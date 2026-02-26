package com.xenonware.cloudremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.data.GoogleCloudRepository
import com.xenonware.cloudremote.data.LocalDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CloudRemoteService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var repository: GoogleCloudRepository
    private lateinit var localDeviceManager: LocalDeviceManager
    private val auth = FirebaseAuth.getInstance()

    private var currentRemoteDevice: Device? = null
    private var localDeviceId: String? = null

    @Volatile
    private var lastLocalState: LocalDeviceManager.DeviceState? = null
    
    // Track when the last command was applied from remote to avoid loopback
    @Volatile
    private var lastCommandAppliedAt: Long = 0L

    companion object {
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_DEVICE_ID = "EXTRA_DEVICE_ID"
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "CloudRemoteServiceChannel"
        private const val TAG = "CloudRemoteService"

        private const val COOLDOWN_MS = 2_000L
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = GoogleCloudRepository()
        localDeviceManager = LocalDeviceManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_DEVICE_ID)
        if (id != null) {
            localDeviceId = id
            startSync()
        }
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun startSync() {
        val deviceId = localDeviceId ?: return

        scope.launch {
            // Wait for user to be authenticated
            while (auth.currentUser == null) {
                delay(2000)
            }

            repository.getDevicesFlow()
                .catch { e ->
                     Log.e(TAG, "Error syncing devices", e)
                }
                .collect { devices ->
                val myDevice = devices.find { it.id == deviceId }

                // If device was removed from the list, stop tracking it as current
                if (myDevice == null) {
                    currentRemoteDevice = null
                    return@collect
                }

                val prev = currentRemoteDevice
                currentRemoteDevice = myDevice

                var commandApplied = false

                // 1. Sync Media Volume
                if (prev == null || prev.mediaVolume != myDevice.mediaVolume) {
                     localDeviceManager.setVolume(myDevice.mediaVolume)
                     commandApplied = true
                }

                // 2. Sync Ringer Mode & DND
                // Note: We only apply if changed to avoid overriding local changes immediately
                if (prev == null || prev.ringerMode != myDevice.ringerMode) {
                    localDeviceManager.setRingerMode(myDevice.ringerMode)
                    commandApplied = true
                }

                if (prev == null || prev.isDndActive != myDevice.isDndActive) {
                    localDeviceManager.setDnd(myDevice.isDndActive)
                    commandApplied = true
                }

                // 3. Sync Curtain
                if (prev == null || prev.isCurtainOn != myDevice.isCurtainOn) {
                    localDeviceManager.setCurtain(myDevice.isCurtainOn)
                    commandApplied = true
                }
                
                // 4. Sync Lock State
                if (prev == null || prev.isLocked != myDevice.isLocked) {
                    if (myDevice.isLocked) {
                        localDeviceManager.lockDevice()
                        commandApplied = true
                    }
                }

                if (commandApplied) {
                    lastCommandAppliedAt = System.currentTimeMillis()
                }
            }
        }

        // Heartbeat loop to keep "Online" status active
        scope.launch {
            while (isActive) {
                if (auth.currentUser != null) {
                    val device = currentRemoteDevice
                    val state = lastLocalState
                    if (device != null && state != null) {
                        syncLocalStateToCloud(device, state, force = true)
                    }
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }

        // Observer local changes and push to cloud
        scope.launch {
             localDeviceManager.observeDeviceState().collectLatest { state ->
                lastLocalState = state
                val cachedDevice = currentRemoteDevice ?: return@collectLatest
                if (auth.currentUser != null) {
                     syncLocalStateToCloud(cachedDevice, state, force = false)
                }
            }
        }
    }

    private fun syncLocalStateToCloud(
        cachedDevice: Device,
        state: LocalDeviceManager.DeviceState,
        force: Boolean
    ) {
        // Prevent loop: if we just applied a command from cloud, don't echo back immediately
        // unless it's a periodic heartbeat (force=true).
        val msSinceCommand = System.currentTimeMillis() - lastCommandAppliedAt
        if (!force && msSinceCommand < COOLDOWN_MS) {
            return
        }
        
        // Check what changed locally vs what we think is in the cloud
        val batteryChanged = cachedDevice.batteryLevel != state.batteryLevel || cachedDevice.isCharging != state.isCharging
        val volumeChanged = cachedDevice.mediaVolume != state.mediaVolume || cachedDevice.maxMediaVolume != state.maxMediaVolume
        val ringerChanged = cachedDevice.ringerMode != state.ringerMode || cachedDevice.isDndActive != state.isDndActive
        val screenChanged = cachedDevice.isScreenOn != state.isScreenOn || cachedDevice.isLocked != state.isLocked
        val curtainChanged = cachedDevice.isCurtainOn != state.isCurtainOn

        if (force || batteryChanged || volumeChanged || ringerChanged || screenChanged || curtainChanged) {
            Log.d(TAG, "Updating cloud: battery=${state.batteryLevel}, locked=${state.isLocked}")

            val updatedDevice = cachedDevice.copy(
                batteryLevel = state.batteryLevel,
                isCharging = state.isCharging,
                mediaVolume = state.mediaVolume,
                maxMediaVolume = state.maxMediaVolume,
                ringerMode = state.ringerMode,
                isDndActive = state.isDndActive,
                isScreenOn = state.isScreenOn,
                isCurtainOn = state.isCurtainOn, // update status of curtain
                isLocked = state.isLocked,
                lastUpdated = System.currentTimeMillis()
            )
            // Optimistic update
            currentRemoteDevice = updatedDevice
            repository.updateDevice(updatedDevice)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Cloud Remote Sync Service", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Cloud Remote Active")
            .setContentText("Syncing device state...").setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent).setOngoing(true).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        scope.cancel()
    }
}