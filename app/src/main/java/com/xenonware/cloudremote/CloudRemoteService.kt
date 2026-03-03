package com.xenonware.cloudremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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

    @Volatile
    private var lastCommandAppliedAt: Long = 0L

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                Log.d(TAG, "User unlocked device, resetting lock state.")
                currentRemoteDevice?.let {
                    if (it.isLocked) {
                        val unlockedDevice = it.copy(isLocked = false)
                        repository.updateDevice(unlockedDevice)
                        currentRemoteDevice = unlockedDevice
                    }
                }
            }
        }
    }

    private val mediaUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val title = it.getStringExtra(MediaNotificationListener.EXTRA_TITLE) ?: ""
                val artist = it.getStringExtra(MediaNotificationListener.EXTRA_ARTIST) ?: ""
                val albumArt = it.getStringExtra(MediaNotificationListener.EXTRA_ALBUM_ART) ?: ""
                val isPlaying = it.getBooleanExtra(MediaNotificationListener.EXTRA_IS_PLAYING, false)
                val customAction1Title = it.getStringExtra(MediaNotificationListener.EXTRA_CUSTOM_ACTION_1_TITLE) ?: ""
                val customAction1Action = it.getStringExtra(MediaNotificationListener.EXTRA_CUSTOM_ACTION_1_ACTION) ?: ""
                val customAction2Title = it.getStringExtra(MediaNotificationListener.EXTRA_CUSTOM_ACTION_2_TITLE) ?: ""
                val customAction2Action = it.getStringExtra(MediaNotificationListener.EXTRA_CUSTOM_ACTION_2_ACTION) ?: ""
                updateMediaState(title, artist, albumArt, isPlaying, customAction1Title, customAction1Action, customAction2Title, customAction2Action)
            }
        }
    }

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
        LocalBroadcastManager.getInstance(this).registerReceiver(
            mediaUpdateReceiver, IntentFilter(MediaNotificationListener.ACTION_MEDIA_UPDATE)
        )
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
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
            while (auth.currentUser == null) {
                delay(2000)
            }

            repository.getDevicesFlow()
                .catch { e -> Log.e(TAG, "Error syncing devices", e) }
                .collect { devices ->
                    val myDevice = devices.find { it.id == deviceId }
                    if (myDevice == null) {
                        currentRemoteDevice = null
                        return@collect
                    }
                    val prev = currentRemoteDevice
                    currentRemoteDevice = myDevice
                    var commandApplied = false

                    if (prev?.mediaVolume != myDevice.mediaVolume) {
                        localDeviceManager.setVolume(myDevice.mediaVolume)
                        commandApplied = true
                    }
                    if (prev?.ringerMode != myDevice.ringerMode) {
                        localDeviceManager.setRingerMode(myDevice.ringerMode)
                        commandApplied = true
                    }
                    if (prev?.isDndActive != myDevice.isDndActive) {
                        localDeviceManager.setDnd(myDevice.isDndActive)
                        commandApplied = true
                    }
                    if (prev?.isCurtainOn != myDevice.isCurtainOn) {
                        localDeviceManager.setCurtain(myDevice.isCurtainOn)
                        commandApplied = true
                    }
                    if (prev?.isLocked != myDevice.isLocked && myDevice.isLocked) {
                        localDeviceManager.lockDevice()
                        commandApplied = true
                    }
                    // Handle Media Actions
                    if (prev?.mediaAction != myDevice.mediaAction && myDevice.mediaAction.isNotBlank()) {
                        handleMediaAction(myDevice.mediaAction)
                        // Reset action after handling
                        repository.updateDevice(myDevice.copy(mediaAction = ""))
                        commandApplied = true
                    }

                    if (commandApplied) {
                        lastCommandAppliedAt = System.currentTimeMillis()
                    }
                }
        }

        scope.launch {
            while (isActive) {
                if (auth.currentUser != null) {
                    currentRemoteDevice?.let { device ->
                        lastLocalState?.let { state ->
                            syncLocalStateToCloud(device, state, force = true)
                        }
                    }
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }

        scope.launch {
            localDeviceManager.observeDeviceState().collectLatest { state ->
                lastLocalState = state
                currentRemoteDevice?.let {
                    if (auth.currentUser != null) {
                        syncLocalStateToCloud(it, state, force = false)
                    }
                }
            }
        }
    }

    private fun handleMediaAction(action: String) {
        val componentName = ComponentName(this, MediaNotificationListener::class.java)
        val mediaController =
            (getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager)
                .getActiveSessions(componentName).firstOrNull()

        when (action) {
            "play" -> mediaController?.transportControls?.play()
            "pause" -> mediaController?.transportControls?.pause()
            "next" -> mediaController?.transportControls?.skipToNext()
            "previous" -> mediaController?.transportControls?.skipToPrevious()
            "custom1" -> currentRemoteDevice?.mediaCustomAction1Action?.let {
                mediaController?.transportControls?.sendCustomAction(it, null)
            }
            "custom2" -> currentRemoteDevice?.mediaCustomAction2Action?.let {
                mediaController?.transportControls?.sendCustomAction(it, null)
            }
        }
    }

    private fun updateMediaState(
        title: String,
        artist: String,
        albumArt: String,
        isPlaying: Boolean,
        customAction1Title: String,
        customAction1Action: String,
        customAction2Title: String,
        customAction2Action: String
    ) {
        currentRemoteDevice?.let {
            val changed = it.mediaTitle != title ||
                    it.mediaArtist != artist ||
                    it.mediaAlbumArt != albumArt ||
                    it.isPlaying != isPlaying ||
                    it.mediaCustomAction1Title != customAction1Title ||
                    it.mediaCustomAction2Title != customAction2Title

            if (changed) {
                val updatedDevice = it.copy(
                    mediaTitle = title,
                    mediaArtist = artist,
                    mediaAlbumArt = albumArt,
                    isPlaying = isPlaying,
                    mediaCustomAction1Title = customAction1Title,
                    mediaCustomAction1Action = customAction1Action,
                    mediaCustomAction2Title = customAction2Title,
                    mediaCustomAction2Action = customAction2Action,
                    lastUpdated = System.currentTimeMillis()
                )
                currentRemoteDevice = updatedDevice
                repository.updateDevice(updatedDevice)
            }
        }
    }

    private fun syncLocalStateToCloud(
        cachedDevice: Device,
        state: LocalDeviceManager.DeviceState,
        force: Boolean
    ) {
        val msSinceCommand = System.currentTimeMillis() - lastCommandAppliedAt
        if (!force && msSinceCommand < COOLDOWN_MS) return

        val changed = force ||
                cachedDevice.batteryLevel != state.batteryLevel ||
                cachedDevice.isCharging != state.isCharging ||
                cachedDevice.mediaVolume != state.mediaVolume ||
                cachedDevice.maxMediaVolume != state.maxMediaVolume ||
                cachedDevice.ringerMode != state.ringerMode ||
                cachedDevice.isDndActive != state.isDndActive ||
                cachedDevice.isScreenOn != state.isScreenOn ||
                cachedDevice.isCurtainOn != state.isCurtainOn ||
                cachedDevice.isLocked != state.isLocked

        if (changed) {
            val updatedDevice = cachedDevice.copy(
                batteryLevel = state.batteryLevel,
                isCharging = state.isCharging,
                mediaVolume = state.mediaVolume,
                maxMediaVolume = state.maxMediaVolume,
                ringerMode = state.ringerMode,
                isDndActive = state.isDndActive,
                isScreenOn = state.isScreenOn,
                isCurtainOn = state.isCurtainOn,
                isLocked = state.isLocked,
                lastUpdated = System.currentTimeMillis()
            )
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cloud Remote Active")
            .setContentText("Syncing device state...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mediaUpdateReceiver)
        unregisterReceiver(userPresentReceiver)
        job.cancel()
        scope.cancel()
    }
}
