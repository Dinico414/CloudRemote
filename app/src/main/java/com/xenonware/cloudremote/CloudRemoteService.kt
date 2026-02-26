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
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.data.GoogleCloudRepository
import com.xenonware.cloudremote.data.LocalDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CloudRemoteService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var repository: GoogleCloudRepository
    private lateinit var localDeviceManager: LocalDeviceManager

    private var currentRemoteDevice: Device? = null
    private var localDeviceId: String? = null

    @Volatile
    private var lastLocalState: LocalDeviceManager.DeviceState? = null

    @Volatile
    private var lastCommandAppliedAt: Long = 0L

    companion object {
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_DEVICE_ID = "EXTRA_DEVICE_ID"
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "CloudRemoteServiceChannel"
        private const val TAG = "CloudRemoteService"

        private const val COOLDOWN_MS = 3_000L

        private const val RINGER_SETTLE_MS = 400L
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
            repository.getDevicesFlow().collect { devices ->
                val myDevice = devices.find { it.id == deviceId }

                if (myDevice == null) {
                    // Device was removed from sync list, stop syncing
                    currentRemoteDevice = null
                    return@collect
                }

                val prev = currentRemoteDevice
                currentRemoteDevice = myDevice

                var commandApplied = false

                if (prev == null || prev.mediaVolume != myDevice.mediaVolume) {
                    localDeviceManager.setVolume(myDevice.mediaVolume)
                    commandApplied = true
                }

                val ringerChanged = prev == null || prev.ringerMode != myDevice.ringerMode
                val dndChanged = prev == null || prev.isDndActive != myDevice.isDndActive

                if (ringerChanged || dndChanged) {
                    commandApplied = true

                    if (ringerChanged) {
                        localDeviceManager.setRingerMode(myDevice.ringerMode)
                        delay(RINGER_SETTLE_MS)
                    }


                    localDeviceManager.setDnd(myDevice.isDndActive)
                }

                if (prev == null || prev.isCurtainOn != myDevice.isCurtainOn) {
                    localDeviceManager.setCurtain(myDevice.isCurtainOn)
                    commandApplied = true
                }

                if (commandApplied) {
                    lastCommandAppliedAt = System.currentTimeMillis()
                }

                // Try syncing latest local state immediately after applying commands or receiving update
                lastLocalState?.let { state ->
                    syncLocalStateToCloud(myDevice, state, force = false)
                }
            }
        }

        // Heartbeat loop to keep "Online" status active
        scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val cached = currentRemoteDevice
                val state = lastLocalState
                if (cached != null && state != null) {
                    syncLocalStateToCloud(cached, state, force = true)
                }
            }
        }

        scope.launch {
            localDeviceManager.observeDeviceState().collectLatest { state ->
                lastLocalState = state
                val cachedDevice = currentRemoteDevice ?: return@collectLatest
                syncLocalStateToCloud(cachedDevice, state, force = false)
            }
        }
    }

    private fun syncLocalStateToCloud(
        cachedDevice: Device,
        state: LocalDeviceManager.DeviceState,
        force: Boolean
    ) {
        val msSinceCommand = System.currentTimeMillis() - lastCommandAppliedAt
        if (msSinceCommand < COOLDOWN_MS && !force) return

        val needsUpdate = force ||
                cachedDevice.batteryLevel != state.batteryLevel ||
                cachedDevice.isCharging != state.isCharging ||
                cachedDevice.mediaVolume != state.mediaVolume ||
                cachedDevice.maxMediaVolume != state.maxMediaVolume ||
                cachedDevice.isScreenOn != state.isScreenOn ||
                cachedDevice.isCurtainOn != state.isCurtainOn ||
                cachedDevice.isLocked != state.isLocked

        if (needsUpdate) {
            Log.d(TAG, "Updating cloud: locked=${state.isLocked}, battery=${state.batteryLevel}")
            val updatedDevice = cachedDevice.copy(
                batteryLevel = state.batteryLevel,
                isCharging = state.isCharging,
                mediaVolume = state.mediaVolume,
                maxMediaVolume = state.maxMediaVolume,
                isScreenOn = state.isScreenOn,
                isCurtainOn = state.isCurtainOn,
                isLocked = state.isLocked,
                lastUpdated = System.currentTimeMillis()
            )
            // Optimistic update of local cache to prevent redundant updates
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