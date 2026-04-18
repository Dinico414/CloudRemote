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
import android.content.pm.ServiceInfo
import android.media.session.MediaSessionManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.xenonware.cloudremote.MainActivity
import com.xenonware.cloudremote.R
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.data.SharedPreferenceManager
import com.xenonware.cloudremote.helper.LocalDeviceManager
import com.xenonware.cloudremote.helper.MediaNotificationListener
import com.xenonware.cloudremote.sign_in.GoogleCloudRepository
import com.xenonware.cloudremote.widget.BatteryWidgetReceiver
import com.xenonware.cloudremote.widget.ConnectedDevicesWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class CloudRemoteService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var repository: GoogleCloudRepository
    private lateinit var localDeviceManager: LocalDeviceManager
    private lateinit var sharedPreferenceManager: SharedPreferenceManager
    private val auth = FirebaseAuth.getInstance()

    private var isSyncing = false

    @Volatile
    private var currentRemoteDevice: Device? = null
    private var localDeviceId: String? = null

    private val lastNotifiedBatteryLevels = mutableMapOf<String, Int>()
    @Volatile
    private var lastCommandAppliedAt: Long = 0L
    private val lastLocalChangeTime = mutableMapOf<String, Long>()

    // NEW: Lock to prevent DND-induced silence from syncing to cloud
    @Volatile
    private var dndTransitionLockUntil = 0L

    private enum class ConnectionQuality { GOOD, BAD, NONE }
    @Volatile
    private var currentConnectionQuality = ConnectionQuality.GOOD
    @Volatile
    private var isNetworkMetered = false

    private data class MediaUpdate(
        val title: String, val artist: String, val albumArt: String, val isPlaying: Boolean,
        val customAction1Title: String, val customAction1Action: String,
        val customAction2Title: String, val customAction2Action: String
    )
    private val mediaUpdateFlow = MutableSharedFlow<MediaUpdate>(extraBufferCapacity = 1)

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {}
    }

    private val mediaUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val update = MediaUpdate(
                    title = it.getStringExtra(MediaNotificationListener.EXTRA_TITLE) ?: "",
                    artist = it.getStringExtra(MediaNotificationListener.EXTRA_ARTIST) ?: "",
                    albumArt = it.getStringExtra(MediaNotificationListener.EXTRA_ALBUM_ART) ?: "",
                    isPlaying = it.getBooleanExtra(MediaNotificationListener.EXTRA_IS_PLAYING, false),
                    customAction1Title = it.getStringExtra(MediaNotificationListener.EXTRA_CUSTOM_ACTION_1_TITLE) ?: "",
                    customAction1Action = it.getStringExtra(MediaNotificationListener.EXTRA_CUSTOM_ACTION_1_ACTION) ?: "",
                    customAction2Title = it.getStringExtra(MediaNotificationListener.EXTRA_CUSTOM_ACTION_2_TITLE) ?: "",
                    customAction2Action = it.getStringExtra(MediaNotificationListener.EXTRA_CUSTOM_ACTION_2_ACTION) ?: ""
                )
                scope.launch { mediaUpdateFlow.emit(update) }
            }
        }
    }

    companion object {
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_DEVICE_ID = "EXTRA_DEVICE_ID"
        const val NOTIFICATION_ID = 101
        const val BATTERY_NOTIFICATION_ID_OFFSET = 1000
        const val CHANNEL_ID = "CloudRemoteServiceChannel"
        const val BATTERY_CHANNEL_ID = "CloudRemoteBatteryChannel"
        private const val TAG = "CloudRemoteService"
        private const val COOLDOWN_MS = 2_000L
        private const val LOCAL_CHANGE_COOLDOWN_MS = 5_000L
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        repository = GoogleCloudRepository()
        localDeviceManager = LocalDeviceManager(this)
        sharedPreferenceManager = SharedPreferenceManager(this)
        createNotificationChannel()
        createBatteryNotificationChannel()
        setupNetworkCallback()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            mediaUpdateReceiver,
            IntentFilter(MediaNotificationListener.ACTION_MEDIA_UPDATE)
        )
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))

        scope.launch {
            mediaUpdateFlow.debounce { getSyncDebounceMs() }.collectLatest { update ->
                updateMediaState(
                    update.title, update.artist, update.albumArt, update.isPlaying,
                    update.customAction1Title, update.customAction1Action,
                    update.customAction2Title, update.customAction2Action
                )
            }
        }
    }

    private fun setupNetworkCallback() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        updateNetworkState(caps)
        isNetworkMetered = connectivityManager.isActiveNetworkMetered

        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateNetworkState(networkCapabilities)
                isNetworkMetered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
            override fun onLost(network: Network) {
                currentConnectionQuality = ConnectionQuality.NONE
            }
        })
    }

    private fun updateNetworkState(caps: NetworkCapabilities?) {
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            currentConnectionQuality = ConnectionQuality.NONE
            return
        }
        val bandwidth = caps.linkDownstreamBandwidthKbps
        currentConnectionQuality = if (bandwidth in 1..800) ConnectionQuality.BAD else ConnectionQuality.GOOD
    }

    private fun getSyncDebounceMs(): Long {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return when {
            currentConnectionQuality == ConnectionQuality.NONE -> 60_000L
            powerManager.isPowerSaveMode -> 20_000L
            currentConnectionQuality == ConnectionQuality.BAD -> 15_000L
            !powerManager.isInteractive -> 10_000L
            else -> 1_000L
        }
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) { Log.e(TAG, "Failed to start foreground", e) }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun isNetworkAvailable(): Boolean = currentConnectionQuality != ConnectionQuality.NONE

    @OptIn(FlowPreview::class)
    private fun startSync() {
        if (isSyncing) return
        if (!sharedPreferenceManager.inputReceiverEnabled) return
        val deviceId = localDeviceId ?: return
        isSyncing = true

        scope.launch {
            var retryDelay = 2000L
            while (isActive) {
                if (auth.currentUser == null) { delay(5000); continue }
                if (!isNetworkAvailable()) { delay(15000); continue }

                try {
                    repository.getDevicesFlow().collect { devices ->
                        retryDelay = 2000L
                        if (!isNetworkAvailable()) throw Exception("Network lost")

                        val myDevice = devices.find { it.id == deviceId }
                        devices.forEach { device ->
                            if (device.id != deviceId && (System.currentTimeMillis() - device.lastUpdated) < 3_600_000) {
                                checkBatteryLevel(device)
                            }
                        }

                        val prev = currentRemoteDevice
                        currentRemoteDevice = myDevice
                        var commandApplied = false

                        if (prev != null && myDevice != null) {
                            val now = System.currentTimeMillis()

                            if (prev.mediaVolume != myDevice.mediaVolume && !isLocalChangeRecent("mediaVolume", now)) {
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

                                // Start the guard timer: ignore local audio changes for 2s
                                dndTransitionLockUntil = System.currentTimeMillis() + 2000L

                                if (!myDevice.isDndActive) {
                                    localDeviceManager.setRingerMode(myDevice.ringerMode)
                                    localDeviceManager.setVolume(myDevice.mediaVolume)
                                }
                            }
                            if (prev.isCurtainOn != myDevice.isCurtainOn && !isLocalChangeRecent("isCurtainOn", now)) {
                                localDeviceManager.setCloudCurtain(myDevice.isCurtainOn)
                                commandApplied = true
                            }
                            if (prev.mediaAction != myDevice.mediaAction && myDevice.mediaAction.isNotBlank()) {
                                handleMediaAction(myDevice.mediaAction)
                                repository.updateDeviceFields(deviceId, mapOf("mediaAction" to ""))
                                commandApplied = true
                            }
                            if (myDevice.pendingAction == "lock") {
                                localDeviceManager.lockDevice()
                                repository.updateDeviceFields(deviceId, mapOf("pendingAction" to "", "isLocked" to true))
                                commandApplied = true
                            }
                        }

                        if (commandApplied) lastCommandAppliedAt = System.currentTimeMillis()
                        broadcastWidgetUpdate(devices)
                    }
                } catch (e: Exception) { Log.e(TAG, "Error syncing: ${e.message}") }
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(120_000L)
            }
        }

        scope.launch {
            localDeviceManager.observeDeviceState().collectLatest { state ->
                val batteryIntent = Intent(this@CloudRemoteService, BatteryWidgetReceiver::class.java).apply {
                    action = BatteryWidgetReceiver.ACTION_REFRESH_LOCAL
                    setPackage(packageName)
                }
                sendBroadcast(batteryIntent)
                val connectedIntent = Intent(this@CloudRemoteService, ConnectedDevicesWidgetReceiver::class.java).apply {
                    action = ConnectedDevicesWidgetReceiver.ACTION_UPDATE
                    setPackage(packageName)
                }
                sendBroadcast(connectedIntent)
            }
        }

        scope.launch {
            localDeviceManager.observeDeviceState()
                .debounce { getSyncDebounceMs() }
                .collectLatest { state ->
                    currentRemoteDevice?.let { if (auth.currentUser != null) syncLocalStateToCloud(it, state) }
                }
        }

        scope.launch {
            while (isActive) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                val batteryLevel = localDeviceManager.getBatteryLevel()
                val isLowBattery = batteryLevel < 20 && !localDeviceManager.isCharging()
                val interval = when {
                    currentConnectionQuality == ConnectionQuality.NONE -> 600_000L
                    currentConnectionQuality == ConnectionQuality.BAD -> 300_000L
                    powerManager.isPowerSaveMode -> 300_000L
                    isLowBattery -> 300_000L
                    !powerManager.isInteractive -> 120_000L
                    else -> HEARTBEAT_INTERVAL_MS
                }
                delay(interval)
                if (auth.currentUser != null && currentRemoteDevice != null && isNetworkAvailable()) {
                    repository.updateDeviceFields(deviceId, mapOf("lastUpdated" to System.currentTimeMillis()))
                }
            }
        }
    }

    private fun broadcastWidgetUpdate(devices: List<Device>) {
        val intent = Intent(this, BatteryWidgetReceiver::class.java).apply {
            action = "com.xenonware.cloudremote.ACTION_UPDATE_WIDGET"
            putExtra("EXTRA_DEVICES_JSON", Gson().toJson(devices))
        }
        sendBroadcast(intent)
    }

    private fun handleMediaAction(action: String) {
        val componentName = ComponentName(this, MediaNotificationListener::class.java)
        val mediaController = (getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager).getActiveSessions(componentName).firstOrNull()
        when (action) {
            "play" -> mediaController?.transportControls?.play()
            "pause" -> mediaController?.transportControls?.pause()
            "next" -> mediaController?.transportControls?.skipToNext()
            "previous" -> mediaController?.transportControls?.skipToPrevious()
            "custom1" -> currentRemoteDevice?.mediaCustomAction1Action?.let { mediaController?.transportControls?.sendCustomAction(it, null) }
            "custom2" -> currentRemoteDevice?.mediaCustomAction2Action?.let { mediaController?.transportControls?.sendCustomAction(it, null) }
        }
    }

    private fun updateMediaState(title: String, artist: String, albumArt: String, isPlaying: Boolean, customAction1Title: String, customAction1Action: String, customAction2Title: String, customAction2Action: String) {
        val device = currentRemoteDevice ?: return
        val deviceId = localDeviceId ?: return
        if (auth.currentUser == null || !isNetworkAvailable()) return
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
            val now = System.currentTimeMillis()
            updates.keys.forEach { lastLocalChangeTime[it] = now }
            repository.updateDeviceFields(deviceId, updates)
            currentRemoteDevice = device.copy(mediaTitle = title, mediaArtist = artist, mediaAlbumArt = if (updates.containsKey("mediaAlbumArt")) albumArt else device.mediaAlbumArt, isPlaying = isPlaying, mediaCustomAction1Title = customAction1Title, mediaCustomAction1Action = customAction1Action, mediaCustomAction2Title = customAction2Title, mediaCustomAction2Action = customAction2Action)
        }
    }

    private fun syncLocalStateToCloud(cachedDevice: Device, state: LocalDeviceManager.DeviceState) {
        val deviceId = localDeviceId ?: return
        if (auth.currentUser == null || !isNetworkAvailable()) return
        val msSinceCommand = System.currentTimeMillis() - lastCommandAppliedAt
        if (msSinceCommand < COOLDOWN_MS) return

        val updates = mutableMapOf<String, Any>()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val threshold = if (currentConnectionQuality == ConnectionQuality.BAD || powerManager.isPowerSaveMode) 5 else 2
        val batteryDiff = abs(cachedDevice.batteryLevel - state.batteryLevel)
        if (batteryDiff >= threshold || cachedDevice.isCharging != state.isCharging) {
            updates["batteryLevel"] = state.batteryLevel
            updates["isCharging"] = state.isCharging
        }

        // THE ULTIMATE GUARD:
        // 1. Don't sync if DND is physically active (OS is forcing silence).
        // 2. Don't sync if we are in the 2s window after a DND toggle.
        val isLocked = state.isDndActive || System.currentTimeMillis() < dndTransitionLockUntil

        if (!isLocked) {
            if (cachedDevice.mediaVolume != state.mediaVolume) updates["mediaVolume"] = state.mediaVolume
            if (cachedDevice.maxMediaVolume != state.maxMediaVolume) updates["maxMediaVolume"] = state.maxMediaVolume
            if (cachedDevice.ringerMode != state.ringerMode) updates["ringerMode"] = state.ringerMode
        }

        if (cachedDevice.isDndActive != state.isDndActive) updates["isDndActive"] = state.isDndActive
        if (cachedDevice.isScreenOn != state.isScreenOn) updates["isScreenOn"] = state.isScreenOn
        if (cachedDevice.isCurtainOn != state.isCurtainOn) updates["isCurtainOn"] = state.isCurtainOn
        if (cachedDevice.isLocked != state.isLocked) updates["isLocked"] = state.isLocked

        val stateConnectedDevices = state.connectedDevices.map { mapOf("name" to it.name, "type" to it.type.name, "batteryLevel" to it.batteryLevel) }
        if (cachedDevice.connectedDevices != stateConnectedDevices) updates["connectedDevices"] = stateConnectedDevices

        if (updates.isNotEmpty()) {
            updates["lastUpdated"] = System.currentTimeMillis()
            val now = System.currentTimeMillis()
            updates.keys.forEach { lastLocalChangeTime[it] = now }
            repository.updateDeviceFields(deviceId, updates)
            currentRemoteDevice = cachedDevice.copy(batteryLevel = state.batteryLevel, isCharging = state.isCharging, mediaVolume = state.mediaVolume, maxMediaVolume = state.maxMediaVolume, ringerMode = state.ringerMode, isDndActive = state.isDndActive, isScreenOn = state.isScreenOn, isCurtainOn = state.isCurtainOn, isLocked = state.isLocked, connectedDevices = stateConnectedDevices)
        }
        val widgetIntent = Intent(this@CloudRemoteService, ConnectedDevicesWidgetReceiver::class.java).apply { action = ConnectedDevicesWidgetReceiver.ACTION_UPDATE }
        sendBroadcast(widgetIntent)
    }

    private fun isLocalChangeRecent(field: String, now: Long): Boolean {
        val lastChange = lastLocalChangeTime[field] ?: return false
        return (now - lastChange) < LOCAL_CHANGE_COOLDOWN_MS
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Cloud Remote Sync Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NOTIFICATION_SERVICE).let { (it as NotificationManager).createNotificationChannel(channel) }
    }

    private fun createBatteryNotificationChannel() {
        val channel = NotificationChannel(BATTERY_CHANNEL_ID, "Battery Alerts", NotificationManager.IMPORTANCE_HIGH).apply { description = "Notifications for low battery levels of cloud devices" }
        getSystemService(NOTIFICATION_SERVICE).let { (it as NotificationManager).createNotificationChannel(channel) }
    }

    private fun checkBatteryLevel(device: Device) {
        val lastLevel = lastNotifiedBatteryLevels[device.id] ?: 100
        val currentLevel = device.batteryLevel
        if (currentLevel <= 5 && lastLevel > 5) { showBatteryNotification(device, true); lastNotifiedBatteryLevels[device.id] = 5 }
        else if (currentLevel <= 20 && lastLevel > 20) { showBatteryNotification(device, false); lastNotifiedBatteryLevels[device.id] = 20 }
        else if (currentLevel > 20 && lastLevel <= 20) { lastNotifiedBatteryLevels[device.id] = 100 }
    }

    private fun showBatteryNotification(device: Device, isCritical: Boolean) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val message = if (isCritical) getString(R.string.battery_very_low_message, device.name, device.batteryLevel) else getString(R.string.battery_low_message, device.name, device.batteryLevel)
        val notification = NotificationCompat.Builder(this, BATTERY_CHANNEL_ID).setSmallIcon(R.drawable.ic_notification).setContentTitle(getString(R.string.low_battery_notification_title)).setContentText(message).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pendingIntent).setAutoCancel(true).build()
        notificationManager.notify(BATTERY_NOTIFICATION_ID_OFFSET + device.id.hashCode(), notification)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Cloud Remote Active").setSmallIcon(R.drawable.ic_notification).setContentIntent(pendingIntent).setSilent(true).setOngoing(true).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mediaUpdateReceiver)
        unregisterReceiver(userPresentReceiver)
        job.cancel()
        scope.cancel()
    }
}
