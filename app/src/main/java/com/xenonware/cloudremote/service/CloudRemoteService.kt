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
import com.xenonware.cloudremote.widget.BatteryWidgetProvider
import com.xenonware.cloudremote.MainActivity
import com.xenonware.cloudremote.helper.MediaNotificationListener
import com.xenonware.cloudremote.R
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.data.SharedPreferenceManager
import com.xenonware.cloudremote.presentation.sign_in.GoogleCloudRepository
import com.xenonware.cloudremote.helper.LocalDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CloudRemoteService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var repository: GoogleCloudRepository
    private lateinit var localDeviceManager: LocalDeviceManager
    private lateinit var sharedPreferenceManager: SharedPreferenceManager
    private val auth = FirebaseAuth.getInstance()

    @Volatile
    private var currentRemoteDevice: Device? = null
    private var localDeviceId: String? = null

    @Volatile
    private var lastCommandAppliedAt: Long = 0L

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
        override fun onReceive(context: Context?, intent: Intent?) {
        }
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
        sharedPreferenceManager = SharedPreferenceManager(this)
        createNotificationChannel()
        setupNetworkCallback()
        
        LocalBroadcastManager.getInstance(this).registerReceiver(
            mediaUpdateReceiver,
            IntentFilter(MediaNotificationListener.ACTION_MEDIA_UPDATE)
        )
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))

        // Debounced media updates with dynamic delay
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
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Initial state
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        updateNetworkState(caps)
        isNetworkMetered = connectivityManager.isActiveNetworkMetered

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
            
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
        currentConnectionQuality = if (bandwidth in 1..800) {
            ConnectionQuality.BAD
        } else {
            ConnectionQuality.GOOD
        }
    }

    private fun getSyncDebounceMs(): Long {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return when {
            currentConnectionQuality == ConnectionQuality.NONE -> 60_000L
            powerManager.isPowerSaveMode -> 20_000L
            currentConnectionQuality == ConnectionQuality.BAD -> 15_000L
            !powerManager.isInteractive -> 10_000L
            else -> 3_000L
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
        }
        
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Log.w(TAG, "Foreground service timed out for fgsType: $fgsType")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun isNetworkAvailable(): Boolean = currentConnectionQuality != ConnectionQuality.NONE

    private fun startSync() {
        if (!sharedPreferenceManager.inputReceiverEnabled) {
            Log.d(TAG, "Input receiver is disabled. Skipping sync.")
            return
        }

        val deviceId = localDeviceId ?: return

        // Remote -> Local sync
        scope.launch {
            var retryDelay = 2000L
            while (isActive) {
                if (auth.currentUser == null) {
                    delay(5000)
                    continue
                }

                if (!isNetworkAvailable()) {
                    delay(15000)
                    continue
                }

                try {
                    repository.getDevicesFlow()
                        .collect { devices ->
                            retryDelay = 2000L 
                            
                            if (!isNetworkAvailable()) {
                                throw Exception("Network lost during collection")
                            }

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
                                    val updates = mapOf("pendingAction" to "", "isLocked" to true)
                                    repository.updateDeviceFields(deviceId, updates)
                                    commandApplied = true
                                }
                            }

                            if (commandApplied) {
                                lastCommandAppliedAt = System.currentTimeMillis()
                            }

                            // Only broadcast widget update if online devices changed significantly
                            val onlineDevices = devices.filter { (System.currentTimeMillis() - it.lastUpdated) < 60_000 }
                            broadcastWidgetUpdate(onlineDevices)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing devices: ${e.message}")
                }

                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(120_000L) // Increase max retry delay to 2 min
            }
        }

        // Local -> Remote sync with dynamic debouncing
        scope.launch {
            localDeviceManager.observeDeviceState()
                .debounce { getSyncDebounceMs() }
                .collectLatest { state ->
                    currentRemoteDevice?.let {
                        if (auth.currentUser != null) {
                            syncLocalStateToCloud(it, state)
                        }
                    }
                }
        }

        // Dynamic Heartbeat with aggressive battery saving
        scope.launch {
            while (isActive) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                val batteryLevel = localDeviceManager.getBatteryLevel()
                val isLowBattery = batteryLevel < 20 && !localDeviceManager.isCharging()
                
                val interval = when {
                    currentConnectionQuality == ConnectionQuality.NONE -> 900_000L // 15 min
                    currentConnectionQuality == ConnectionQuality.BAD -> 600_000L // 10 min
                    powerManager.isPowerSaveMode -> 600_000L // 10 min
                    isLowBattery -> 420_000L // 7 min
                    !powerManager.isInteractive -> 300_000L // 5 min
                    else -> HEARTBEAT_INTERVAL_MS // 30s
                }
                
                delay(interval)
                
                if (auth.currentUser != null && currentRemoteDevice != null && isNetworkAvailable()) {
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
        if (auth.currentUser == null || !isNetworkAvailable()) return

        val updates = mutableMapOf<String, Any>()
        if (device.mediaTitle != title) updates["mediaTitle"] = title
        if (device.mediaArtist != artist) updates["mediaArtist"] = artist
        
        // Disable album art completely on bad/metered connection to save battery/data
        val shouldSkipArt = isNetworkMetered || currentConnectionQuality == ConnectionQuality.BAD
        if (device.mediaAlbumArt != albumArt) {
            if (!shouldSkipArt || albumArt.isEmpty()) {
                updates["mediaAlbumArt"] = albumArt
            }
        }
        
        if (device.isPlaying != isPlaying) updates["isPlaying"] = isPlaying
        if (device.mediaCustomAction1Title != customAction1Title) updates["mediaCustomAction1Title"] = customAction1Title
        if (device.mediaCustomAction1Action != customAction1Action) updates["mediaCustomAction1Action"] = customAction1Action
        if (device.mediaCustomAction2Title != customAction2Title) updates["mediaCustomAction2Title"] = customAction2Title
        if (device.mediaCustomAction2Action != customAction2Action) updates["mediaCustomAction2Action"] = customAction2Action

        if (updates.isNotEmpty()) {
            updates["lastUpdated"] = System.currentTimeMillis()
            repository.updateDeviceFields(deviceId, updates)
            
            currentRemoteDevice = device.copy(
                mediaTitle = title, mediaArtist = artist, 
                mediaAlbumArt = if (updates.containsKey("mediaAlbumArt")) albumArt else device.mediaAlbumArt,
                isPlaying = isPlaying, mediaCustomAction1Title = customAction1Title,
                mediaCustomAction1Action = customAction1Action,
                mediaCustomAction2Title = customAction2Title,
                mediaCustomAction2Action = customAction2Action
            )
        }
    }

    private fun syncLocalStateToCloud(cachedDevice: Device, state: LocalDeviceManager.DeviceState) {
        val deviceId = localDeviceId ?: return
        if (auth.currentUser == null || !isNetworkAvailable()) return
        val msSinceCommand = System.currentTimeMillis() - lastCommandAppliedAt
        if (msSinceCommand < COOLDOWN_MS) return

        val updates = mutableMapOf<String, Any>()
        
        // Increase battery change threshold to 5% if connection is bad or power save is on
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val threshold = if (currentConnectionQuality == ConnectionQuality.BAD || powerManager.isPowerSaveMode) 5 else 2
        
        val batteryDiff = Math.abs(cachedDevice.batteryLevel - state.batteryLevel)
        if (batteryDiff >= threshold || cachedDevice.isCharging != state.isCharging) {
            updates["batteryLevel"] = state.batteryLevel
            updates["isCharging"] = state.isCharging
        }

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