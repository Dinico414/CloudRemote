package com.xenonware.cloudremote.helper

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.input.pointer.pointerInput
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
import com.xenonware.cloudremote.ui.res.PixelWatchFace
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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

    private var curtainView: View? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var isCurtainVisible = false
    private var lastKnownRingVolume = -1
    private var onCurtainStateChanged: (() -> Unit)? = null

    // Cached values to avoid redundant system calls
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
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    parseBatteryLevel(intent)
                    parseIsCharging(intent)
                }
                trySend(getCurrentState())
            }
        }
        val filter = IntentFilter().apply {
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

    private fun getCurrentState(): DeviceState {
        return try {
            val batteryLevel = if (lastBatteryLevel != -1) lastBatteryLevel else getBatteryLevel()
            val isCharging = lastIsCharging // Updated by observer or initial call

            val mediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxMediaVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            val ringerMode = when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> 0
                AudioManager.RINGER_MODE_VIBRATE -> 1
                else -> 2
            }

            val isDndActive = if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
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
                isLocked
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device state", e)
            DeviceState()
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

                    if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    }
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                1 -> {
                    if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                        val vol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                        if (vol > 0) lastKnownRingVolume = vol
                    }
                    if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
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
            val target = if (active) NotificationManager.INTERRUPTION_FILTER_NONE
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
            layout.setBackgroundColor(Color.BLACK)
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = {
                                    isActive = true
                                    tryAwaitRelease()
                                })
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