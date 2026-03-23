package com.xenonware.cloudremote.service

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
import android.media.session.MediaSessionManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.xenonware.cloudremote.widget.BatteryWidgetProvider
import com.xenonware.cloudremote.MainActivity
import com.xenonware.cloudremote.helper.MediaNotificationListener
import com.xenonware.cloudremote.R
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.presentation.sign_in.GoogleCloudRepository
import com.xenonware.cloudremote.helper.LocalDeviceManager
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

    @Volatile
    private var currentRemoteDevice: Device? = null
    private var localDeviceId: String? = null

    @Volatile
    private var lastCommandAppliedAt: Long = 0L

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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
            mediaUpdateReceiver,
            IntentFilter(MediaNotificationListener.ACTION_MEDIA_UPDATE)
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
            while (isActive) {
                if (auth.currentUser == null) {
                    delay(2000)
                    continue
                }

                try {
                    repository.getDevicesFlow()
                        .collect { devices ->
                            val myDevice = devices.find { it.id == deviceId }
                            if (myDevice == null) {
                                currentRemoteDevice = null
                                return@collect
                            }

                            val prev = currentRemoteDevice
                            currentRemoteDevice = myDevice
                            var commandApplied = false

                            if (prev != null) {
                                if (prev.mediaVolume != myDevice.mediaVolume) {
                                    localDeviceManager.setVolume(myDevice.mediaVolume)
                                    commandApplied = true
                                }
                                if (prev.ringerMode != myDevice.ringerMode) {
                                    localDeviceManager.setRingerMode(myDevice.ringerMode)
                                    commandApplied = true
                                }
                                if (prev.isDndActive != myDevice.isDndActive) {
                                    localDeviceManager.setDnd(myDevice.isDndActive)
                                    commandApplied = true
                                }
                                if (prev.isCurtainOn != myDevice.isCurtainOn) {
                                    localDeviceManager.setCurtain(myDevice.isCurtainOn)
                                    commandApplied = true
                                }
                                if (myDevice.mediaAction.isNotBlank()) {
                                    handleMediaAction(myDevice.mediaAction)
                                    repository.updateDeviceFields(deviceId, mapOf("mediaAction" to ""))
                                    commandApplied = true
                                }
                                if (myDevice.pendingAction == "lock") {
                                    localDeviceManager.lockDevice()
                                    val updates = mapOf("pendingAction" to "", "isLocked" to true)
                                    repository.updateDeviceFields(deviceId, updates)
                                    commandApplied = true
                                }
                            }

                            if (commandApplied) {
                                lastCommandAppliedAt = System.currentTimeMillis()
                            }

                            val onlineDevices = devices.filter { (System.currentTimeMillis() - it.lastUpdated) < 60_000 }
                            broadcastWidgetUpdate(onlineDevices)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing devices", e)
                }

                delay(2000)
            }
        }

        scope.launch {
            localDeviceManager.observeDeviceState().collectLatest { state ->
                currentRemoteDevice?.let {
                    if (auth.currentUser != null) {
                        syncLocalStateToCloud(it, state)
                    }
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (auth.currentUser != null) {
                    val fields = mapOf("lastUpdated" to System.currentTimeMillis())
                    repository.updateDeviceFields(deviceId, fields)
                }
            }
        }
    }

    private fun broadcastWidgetUpdate(devices: List<Device>) {
        val intent = Intent(this, BatteryWidgetProvider::class.java).apply {
            action = BatteryWidgetProvider.ACTION_UPDATE_WIDGET
            putExtra(BatteryWidgetProvider.EXTRA_DEVICES_JSON, Gson().toJson(devices))
        }
        sendBroadcast(intent)
    }

    private fun handleMediaAction(action: String) {
        val componentName = ComponentName(this, MediaNotificationListener::class.java)
        val mediaController =
            (getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager)
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
        title: String, artist: String, albumArt: String, isPlaying: Boolean,
        customAction1Title: String, customAction1Action: String,
        customAction2Title: String, customAction2Action: String
    ) {
        val device = currentRemoteDevice ?: return
        val deviceId = localDeviceId ?: return
        if (auth.currentUser == null) return

        val updates = mutableMapOf<String, Any>()
        if (device.mediaTitle != title) updates["mediaTitle"] = title
        if (device.mediaArtist != artist) updates["mediaArtist"] = artist
        if (device.mediaAlbumArt != albumArt) updates["mediaAlbumArt"] = albumArt
        if (device.isPlaying != isPlaying) updates["isPlaying"] = isPlaying
        if (device.mediaCustomAction1Title != customAction1Title) updates["mediaCustomAction1Title"] = customAction1Title
        if (device.mediaCustomAction1Action != customAction1Action) updates["mediaCustomAction1Action"] = customAction1Action
        if (device.mediaCustomAction2Title != customAction2Title) updates["mediaCustomAction2Title"] = customAction2Title
        if (device.mediaCustomAction2Action != customAction2Action) updates["mediaCustomAction2Action"] = customAction2Action

        if (updates.isNotEmpty()) {
            updates["lastUpdated"] = System.currentTimeMillis()
            repository.updateDeviceFields(deviceId, updates)
            currentRemoteDevice = device.copy(
                mediaTitle = title, mediaArtist = artist, mediaAlbumArt = albumArt,
                isPlaying = isPlaying, mediaCustomAction1Title = customAction1Title,
                mediaCustomAction1Action = customAction1Action,
                mediaCustomAction2Title = customAction2Title,
                mediaCustomAction2Action = customAction2Action
            )
        }
    }

    private fun syncLocalStateToCloud(cachedDevice: Device, state: LocalDeviceManager.DeviceState) {
        val deviceId = localDeviceId ?: return
        if (auth.currentUser == null) return
        val msSinceCommand = System.currentTimeMillis() - lastCommandAppliedAt
        if (msSinceCommand < COOLDOWN_MS) return

        val updates = mutableMapOf<String, Any>()
        if (cachedDevice.batteryLevel != state.batteryLevel) updates["batteryLevel"] = state.batteryLevel
        if (cachedDevice.isCharging != state.isCharging) updates["isCharging"] = state.isCharging
        if (cachedDevice.mediaVolume != state.mediaVolume) updates["mediaVolume"] = state.mediaVolume
        if (cachedDevice.maxMediaVolume != state.maxMediaVolume) updates["maxMediaVolume"] = state.maxMediaVolume
        if (cachedDevice.ringerMode != state.ringerMode) updates["ringerMode"] = state.ringerMode
        if (cachedDevice.isDndActive != state.isDndActive) updates["isDndActive"] = state.isDndActive
        if (cachedDevice.isScreenOn != state.isScreenOn) updates["isScreenOn"] = state.isScreenOn
        if (cachedDevice.isCurtainOn != state.isCurtainOn) updates["isCurtainOn"] = state.isCurtainOn
        if (cachedDevice.isLocked != state.isLocked) updates["isLocked"] = state.isLocked

        if (updates.isNotEmpty()) {
            updates["lastUpdated"] = System.currentTimeMillis()
            repository.updateDeviceFields(deviceId, updates)
            currentRemoteDevice = cachedDevice.copy(
                batteryLevel = state.batteryLevel, isCharging = state.isCharging,
                mediaVolume = state.mediaVolume, maxMediaVolume = state.maxMediaVolume,
                ringerMode = state.ringerMode, isDndActive = state.isDndActive,
                isScreenOn = state.isScreenOn, isCurtainOn = state.isCurtainOn,
                isLocked = state.isLocked
            )
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Cloud Remote Sync Service", NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
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