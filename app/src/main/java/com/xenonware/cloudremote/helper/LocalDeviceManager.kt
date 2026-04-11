package com.xenonware.cloudremote.helper

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.RequiresPermission
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.xenonware.cloudremote.R
import com.xenonware.cloudremote.broadcastReceiver.AdminReceiver
import com.xenonware.cloudremote.data.BTDeviceType
import com.xenonware.cloudremote.data.ConnectedDevice
import com.xenonware.cloudremote.ui.res.PixelWatchFace
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs
import kotlin.math.pow

class LocalDeviceManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adminComponent = ComponentName(context, AdminReceiver::class.java)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var curtainView: View? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var isCurtainVisible = false
    private var lastKnownRingVolume = -1
    private var cachedConnectedDevices: List<ConnectedDevice> = emptyList()
    private var onCurtainStateChanged: (() -> Unit)? = null
    private var lastBatteryLevel = -1
    private var lastIsCharging = false

    companion object {
        private const val TAG = "LocalDeviceManager"
    }

    data class DeviceState(
        val batteryLevel: Int = 0,
        val isCharging: Boolean = false,
        val mediaVolume: Int = 0,
        val maxMediaVolume: Int = 0,
        val ringerMode: Int = 2,
        val isDndActive: Boolean = false,
        val isScreenOn: Boolean = true,
        val isCurtainOn: Boolean = false,
        val isLocked: Boolean = false,
        val connectedDevices: List<ConnectedDevice> = emptyList()
    )

    fun getBatteryLevel(): Int {
        if (lastBatteryLevel != -1) return lastBatteryLevel
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return parseBatteryLevel(batteryIntent)
    }

    private fun parseBatteryLevel(intent: Intent?): Int {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val result = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
        lastBatteryLevel = result
        return result
    }

    fun isCharging(): Boolean {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return parseIsCharging(batteryIntent)
    }

    private fun parseIsCharging(intent: Intent?): Boolean {
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val result = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0
        lastIsCharging = result
        return result
    }

    fun observeDeviceState(): Flow<DeviceState> = callbackFlow {
        // Initial fetch of Bluetooth devices
        cachedConnectedDevices = getConnectedBluetoothDevices()
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                val action = intent?.action
                
                if (action == Intent.ACTION_BATTERY_CHANGED) {
                    parseBatteryLevel(intent)
                    parseIsCharging(intent)
                }
                
                // Only refresh Bluetooth devices on actual Bluetooth events
                if (action == BluetoothDevice.ACTION_ACL_CONNECTED ||
                    action == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED ||
                    action == BluetoothDevice.ACTION_ACL_DISCONNECTED
                ) {
                    cachedConnectedDevices = getConnectedBluetoothDevices()
                    
                    // Bluetooth state often takes a few seconds to update after the ACL broadcast.
                    // We schedule follow-up checks to ensure the connected devices list is accurate.
                    mainHandler.postDelayed({ 
                        cachedConnectedDevices = getConnectedBluetoothDevices()
                        trySend(getCurrentState()) 
                    }, 2000)
                    mainHandler.postDelayed({ 
                        cachedConnectedDevices = getConnectedBluetoothDevices()
                        trySend(getCurrentState()) 
                    }, 5000)
                }
                
                trySend(getCurrentState())
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onCurtainStateChanged = { trySend(getCurrentState()) }
        trySend(getCurrentState())
        awaitClose {
            context.unregisterReceiver(receiver)
            onCurtainStateChanged = null
        }
    }

    fun getCurrentStateSnapshot(): DeviceState = getCurrentState()

    private fun getCurrentState(): DeviceState {
        return try {
            val batteryLevel = getBatteryLevel()
            val isCharging = isCharging()

            val mediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxMediaVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            val ringerMode = when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> 0
                AudioManager.RINGER_MODE_VIBRATE -> 1
                else -> 2
            }

            val isDndActive = if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            } else false

            val isLocked = keyguardManager.isKeyguardLocked

            DeviceState(
                batteryLevel,
                isCharging,
                mediaVolume,
                maxMediaVolume,
                ringerMode,
                isDndActive,
                powerManager.isInteractive,
                isCurtainVisible,
                isLocked,
                cachedConnectedDevices
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device state", e)
            DeviceState()
        }
    }

    private fun getConnectedBluetoothDevices(): List<ConnectedDevice> {
        val devices = mutableListOf<ConnectedDevice>()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return devices

        try {
            val bondedDevices = bluetoothAdapter.bondedDevices
            for (device in bondedDevices) {
                try {
                    if (!isDeviceConnected(device)) continue

                    val batteryLevel = getBluetoothDeviceBatteryLevel(device)

                    val name = device.name ?: "Unknown"
                    devices.add(
                        ConnectedDevice(
                            name = name,
                            type = mapBluetoothClassToDeviceType(device, name),
                            batteryLevel = batteryLevel
                        )
                    )
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException for device ${device.address}", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing device ${device.address}", e)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission missing", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bluetooth devices", e)
        }
        return devices
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    private fun getBluetoothDeviceBatteryLevel(device: BluetoothDevice): Int {
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            method.invoke(device) as Int
        } catch (e: Exception) {
            -1
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun mapBluetoothClassToDeviceType(device: BluetoothDevice, name: String): BTDeviceType {
        val btClass = device.bluetoothClass ?: return BTDeviceType.OTHER
        val name = device.name ?: ""

        if (name.contains("Pen", ignoreCase = true) || name.contains("Stylus", ignoreCase = true)) {
            return BTDeviceType.PEN
        }

        return when (btClass.deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED -> BTDeviceType.SPEAKER
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> {
                if (name.contains("Hearing Aid", ignoreCase = true)) BTDeviceType.HEARING_AID
                else if (name.contains("Earbuds", ignoreCase = true) ||
                         name.contains("Buds", ignoreCase = true) ||
                         name.contains("Pods", ignoreCase = true)) BTDeviceType.EARBUDS
                else BTDeviceType.HEADSET
            }
            BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE -> BTDeviceType.MICROPHONE
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> BTDeviceType.SPEAKER
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> BTDeviceType.SPEAKER
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> BTDeviceType.SPEAKER
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CAMERA -> BTDeviceType.IMAGING
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER -> BTDeviceType.TV
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> BTDeviceType.CAR
            BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX -> BTDeviceType.SPEAKER
            BluetoothClass.Device.AUDIO_VIDEO_VCR -> BTDeviceType.SPEAKER
            BluetoothClass.Device.AUDIO_VIDEO_CAMCORDER -> BTDeviceType.IMAGING
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR -> BTDeviceType.COMPUTER
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CONFERENCING -> BTDeviceType.IMAGING
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_GAMING_TOY -> BTDeviceType.CONTROLLER

            BluetoothClass.Device.PERIPHERAL_NON_KEYBOARD_NON_POINTING -> BTDeviceType.KEYBOARD
            BluetoothClass.Device.PERIPHERAL_KEYBOARD -> BTDeviceType.KEYBOARD
            BluetoothClass.Device.PERIPHERAL_POINTING -> BTDeviceType.MOUSE
            BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING -> BTDeviceType.KEYBOARD
            0x0514 -> BTDeviceType.PEN // PERIPHERAL_DIGITIZER_TABLET
            0x051C -> BTDeviceType.PEN // PERIPHERAL_DIGITAL_PEN

            BluetoothClass.Device.WEARABLE_UNCATEGORIZED -> BTDeviceType.WATCH
            BluetoothClass.Device.WEARABLE_WRIST_WATCH -> BTDeviceType.WATCH
            BluetoothClass.Device.WEARABLE_PAGER -> BTDeviceType.OTHER
            BluetoothClass.Device.WEARABLE_JACKET -> BTDeviceType.OTHER
            BluetoothClass.Device.WEARABLE_HELMET -> BTDeviceType.OTHER
            BluetoothClass.Device.WEARABLE_GLASSES -> BTDeviceType.GLASSES


            BluetoothClass.Device.TOY_UNCATEGORIZED -> BTDeviceType.CONTROLLER
            BluetoothClass.Device.TOY_ROBOT -> BTDeviceType.CONTROLLER
            BluetoothClass.Device.TOY_VEHICLE -> BTDeviceType.CONTROLLER
            BluetoothClass.Device.TOY_CONTROLLER -> BTDeviceType.CONTROLLER
            BluetoothClass.Device.TOY_DOLL_ACTION_FIGURE -> BTDeviceType.CONTROLLER
            BluetoothClass.Device.TOY_GAME -> BTDeviceType.CONTROLLER

            0x0508 -> BTDeviceType.CONTROLLER // Joypad
            0x0504 -> BTDeviceType.CONTROLLER // Gamepad

            BluetoothClass.Device.COMPUTER_UNCATEGORIZED -> BTDeviceType.OTHER
            BluetoothClass.Device.COMPUTER_DESKTOP -> BTDeviceType.COMPUTER
            BluetoothClass.Device.COMPUTER_SERVER -> BTDeviceType.COMPUTER
            BluetoothClass.Device.COMPUTER_LAPTOP -> BTDeviceType.LAPTOP
            BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA -> BTDeviceType.CONTROLLER
            BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA -> BTDeviceType.LAPTOP
            BluetoothClass.Device.COMPUTER_WEARABLE -> BTDeviceType.WATCH


            BluetoothClass.Device.PHONE_UNCATEGORIZED -> BTDeviceType.PHONE
            BluetoothClass.Device.PHONE_CELLULAR -> BTDeviceType.PHONE
            BluetoothClass.Device.PHONE_CORDLESS -> BTDeviceType.PHONE
            BluetoothClass.Device.PHONE_SMART -> BTDeviceType.PHONE
            BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY -> BTDeviceType.NETWORKING
            BluetoothClass.Device.PHONE_ISDN -> BTDeviceType.PHONE

            BluetoothClass.Device.HEALTH_UNCATEGORIZED -> BTDeviceType.HEARING_AID
            BluetoothClass.Device.HEALTH_BLOOD_PRESSURE -> BTDeviceType.HEALTH
            BluetoothClass.Device.HEALTH_GLUCOSE -> BTDeviceType.HEALTH
            BluetoothClass.Device.HEALTH_PULSE_OXIMETER -> BTDeviceType.HEALTH
            BluetoothClass.Device.HEALTH_PULSE_RATE -> BTDeviceType.HEALTH
            BluetoothClass.Device.HEALTH_THERMOMETER -> BTDeviceType.HEALTH
            BluetoothClass.Device.HEALTH_WEIGHING -> BTDeviceType.HEALTH
            BluetoothClass.Device.HEALTH_DATA_DISPLAY -> BTDeviceType.HEALTH


            else -> {
                when (btClass.majorDeviceClass) {
                    BluetoothClass.Device.Major.COMPUTER -> BTDeviceType.COMPUTER
                    BluetoothClass.Device.Major.PHONE -> BTDeviceType.PHONE
                    BluetoothClass.Device.Major.NETWORKING -> BTDeviceType.NETWORKING
                    BluetoothClass.Device.Major.AUDIO_VIDEO -> BTDeviceType.HEADSET
                    BluetoothClass.Device.Major.PERIPHERAL -> BTDeviceType.PERIPHERAL
                    BluetoothClass.Device.Major.IMAGING -> BTDeviceType.IMAGING
                    BluetoothClass.Device.Major.WEARABLE -> BTDeviceType.WATCH
                    BluetoothClass.Device.Major.TOY -> BTDeviceType.CONTROLLER
                    BluetoothClass.Device.Major.HEALTH -> BTDeviceType.HEALTH
                    BluetoothClass.Device.Major.UNCATEGORIZED -> BTDeviceType.OTHER
                    else -> BTDeviceType.OTHER
                }
            }
        }
    }
    
    fun forceUpdateMediaVolume() {
        try {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            if (currentVolume < maxVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume + 1, 0)
                mainHandler.postDelayed({
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                }, 100)
            } else {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume - 1, 0)
                mainHandler.postDelayed({
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                }, 100)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force update device values", e)
        }
    }

    fun setVolume(volume: Int) {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume.coerceIn(0, max), 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }

    fun setRingerMode(mode: Int) {
        try {
            val hasPerm = notificationManager.isNotificationPolicyAccessGranted

            if (!hasPerm) {
                Log.w(TAG, "setRingerMode: DND permission not granted")
                return
            }

            when (mode) {
                0 -> {
                    val vol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                    if (vol > 0) lastKnownRingVolume = vol
                    
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                1 -> {
                    if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                        val vol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                        if (vol > 0) lastKnownRingVolume = vol
                    }
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                2 -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                    val targetVol = if (lastKnownRingVolume > 0) lastKnownRingVolume
                    else (maxVol / 2).coerceAtLeast(1)
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, targetVol, 0)
                    audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, targetVol, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting ringer mode", e)
        }
    }

    fun setDnd(active: Boolean) {
        try {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                Log.w(TAG, "setDnd: permission not granted")
                return
            }
            val target = if (active) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
            notificationManager.setInterruptionFilter(target)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting DND", e)
        }
    }

    fun setCloudCurtain(enabled: Boolean) {
        mainHandler.post {
            Log.d(TAG, "setCloudCurtain: $enabled")
            if (enabled) showCloudCurtain() else hideCurtain()
        }
    }

    private fun showCloudCurtain() {
        if (isCurtainVisible) return
        if (!Settings.canDrawOverlays(context)) return

        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )

            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

            val layout = object : FrameLayout(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
                    return super.dispatchKeyEvent(event)
                }
            }
            layout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            layout.isClickable = true
            layout.isFocusable = true
            layout.isFocusableInTouchMode = true
            
            @Suppress("DEPRECATION")
            layout.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN

            overlayLifecycleOwner = OverlayLifecycleOwner()
            overlayLifecycleOwner?.performRestore(null)
            overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

            layout.setViewTreeLifecycleOwner(overlayLifecycleOwner)
            layout.setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            layout.setViewTreeViewModelStoreOwner(overlayLifecycleOwner)

            val composeView = ComposeView(context).apply {
                setContent {
                    var isActive by remember { mutableStateOf(true) }
                    var targetOffsetY by remember { mutableFloatStateOf(0f) }
                    var isDragging by remember { mutableStateOf(false) }
                    var screenHeight by remember { mutableFloatStateOf(1f) }

                    val offsetY by animateFloatAsState(
                        targetValue = targetOffsetY,
                        label = "offsetY",
                        animationSpec = if (isDragging) snap() else spring(),
                        finishedListener = {
                            if (it <= -screenHeight + 1f && !isDragging && targetOffsetY < 0) {
                                hideCurtain()
                            }
                        }
                    )

                    val animatedTextAlpha by animateFloatAsState(
                        targetValue = if (isActive) 0.5f else 0f,
                        label = "textAlpha",
                        animationSpec = tween(durationMillis = 500)
                    )

                    LaunchedEffect(isActive) {
                        if (isActive) {
                            delay(10000)
                            isActive = false
                        }
                    }

                    val progress = (abs(offsetY) / screenHeight).coerceIn(0f, 1f)
                    
                    // Deadzone: stay fully opaque for the first 30% of the swipe
                    val fadeStart = 0.3f
                    val adjustedProgress = if (progress < fadeStart) 0f else (progress - fadeStart) / (1f - fadeStart)
                    
                    // Non-linear alpha: transparency grows faster (exponentially) as we swipe further
                    val curtainAlpha = (1f - adjustedProgress.pow(8f)).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { screenHeight = it.height.toFloat().coerceAtLeast(1f) }
                            .drawBehind {
                                drawRect(color = Black, alpha = curtainAlpha)
                            }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = {
                                        isDragging = false
                                        targetOffsetY = if (progress > 0.4f) {
                                            -screenHeight // Animate away
                                        } else {
                                            0f
                                        }
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        targetOffsetY = 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        // Swipe up to reveal
                                        targetOffsetY = (targetOffsetY + dragAmount).coerceAtMost(0f)
                                        isActive = true
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = {
                                    isActive = true
                                    tryAwaitRelease()
                                })
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationY = offsetY
                                    alpha = curtainAlpha
                                }
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            PixelWatchFace(isActive = isActive)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = stringResource(R.string.locked_extern), color = White.copy(alpha = animatedTextAlpha))
                            Spacer(modifier = Modifier.weight(0.2f))
                        }
                    }
                }
            }

            layout.addView(composeView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_START)
            overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            windowManager.addView(layout, params)

            curtainView = layout
            isCurtainVisible = true
            onCurtainStateChanged?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing curtain", e)
        }
    }

    private fun hideCurtain() {
        if (curtainView == null && !isCurtainVisible) return

        curtainView?.let { view ->
            try {
                overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing curtain view", e)
            }
        }
        curtainView = null
        overlayLifecycleOwner = null
        isCurtainVisible = false
        onCurtainStateChanged?.invoke()
    }

    fun lockDevice() {
        try {
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                devicePolicyManager.lockNow()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error locking device", e)
        }
    }

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner,
        SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }

        fun performRestore(savedState: Bundle?) {
            savedStateRegistryController.performRestore(savedState)
        }
    }
}
