package com.xenonware.cloudremote.data

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocalDeviceManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var curtainView: View? = null
    private var isCurtainVisible = false
    private var lastKnownRingVolume = -1
    private var onCurtainStateChanged: (() -> Unit)? = null

    data class DeviceState(
        val batteryLevel: Int = 0,
        val isCharging: Boolean = false,
        val mediaVolume: Int = 0,
        val maxMediaVolume: Int = 0,
        val ringerMode: Int = 2,
        val isDndActive: Boolean = false,
        val isScreenOn: Boolean = true,
        val isCurtainOn: Boolean = false,
    )

    fun observeDeviceState(): Flow<DeviceState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                trySend(getCurrentState())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
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
            val batteryIntent =
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryLevel =
                if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val isCharging =
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0

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

            DeviceState(
                batteryLevel,
                isCharging,
                mediaVolume,
                maxMediaVolume,
                ringerMode,
                isDndActive,
                powerManager.isInteractive,
                isCurtainVisible
            )
        } catch (e: Exception) {
            e.printStackTrace()
            DeviceState()
        }
    }

    fun setVolume(volume: Int) {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume.coerceIn(0, max), 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setRingerMode(mode: Int) {
        try {
            val hasPerm = notificationManager.isNotificationPolicyAccessGranted

            if (!hasPerm) {
                Log.w("LocalDeviceManager", "setRingerMode: DND permission not granted")
                return
            }

            when (mode) {
                0 -> { // Silent — no sound, no vibration
                    val vol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                    if (vol > 0) lastKnownRingVolume = vol

                    if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    }
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    Log.d(
                        "LocalDeviceManager", "Ringer → SILENT (actual=${audioManager.ringerMode})"
                    )
                }

                1 -> { // Vibrate — no sound, vibration only
                    if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                        val vol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                        if (vol > 0) lastKnownRingVolume = vol
                    }
                    if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    }
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    Log.d("LocalDeviceManager", "Ringer → VIBRATE")
                }

                2 -> { // Sound — restore ring volume
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                    val targetVol = if (lastKnownRingVolume > 0) lastKnownRingVolume
                    else (maxVol / 2).coerceAtLeast(1)
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, targetVol, 0)
                    audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, targetVol, 0)
                    Log.d("LocalDeviceManager", "Ringer → SOUND (volume=$targetVol)")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setDnd(active: Boolean) {
        try {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                Log.w("LocalDeviceManager", "setDnd: permission not granted")
                return
            }
            val target = if (active) NotificationManager.INTERRUPTION_FILTER_NONE
            else NotificationManager.INTERRUPTION_FILTER_ALL
            notificationManager.setInterruptionFilter(target)
            Log.d(
                "LocalDeviceManager",
                "DND → ${if (active) "ON (FILTER_NONE)" else "OFF (FILTER_ALL)"}"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setCurtain(enabled: Boolean) {
        if (enabled) showCurtain() else hideCurtain()
    }

    private fun showCurtain() {
        if (isCurtainVisible || !Settings.canDrawOverlays(context)) return
        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).also {
                it.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            val layout = object : FrameLayout(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_HOME) return true
                    return super.dispatchKeyEvent(event)
                }
            }
            layout.setBackgroundColor(Color.BLACK)
            @Suppress("DEPRECATION")
            layout.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

            val unlockButton = Button(context).apply {
                text = "Unlock"
                setTextColor(Color.DKGRAY)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { hideCurtain() }
            }
            layout.addView(
                unlockButton, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = 100
                })

            windowManager.addView(layout, params)
            curtainView = layout
            isCurtainVisible = true
            onCurtainStateChanged?.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideCurtain() {
        curtainView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            curtainView = null
            isCurtainVisible = false
            onCurtainStateChanged?.invoke()
        }
    }
}