package com.xenonware.cloudremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
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
import kotlinx.coroutines.launch

class CloudRemoteService : Service() {

    private val job   = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var repository:         GoogleCloudRepository
    private lateinit var localDeviceManager: LocalDeviceManager

    private var currentRemoteDevice: Device? = null
    private var localDeviceId: String?       = null

    /**
     * Timestamp of the last time we applied a remote command to this device.
     * Local→remote uploads are suppressed for COOLDOWN_MS afterwards to prevent
     * OS side-effects from racing with and overwriting the command we just applied.
     */
    @Volatile
    private var lastCommandAppliedAt: Long = 0L

    companion object {
        const val ACTION_START    = "ACTION_START"
        const val ACTION_STOP     = "ACTION_STOP"
        const val EXTRA_DEVICE_ID = "EXTRA_DEVICE_ID"
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID      = "CloudRemoteServiceChannel"

        /** Suppress local→remote uploads for this long after any remote command. */
        private const val COOLDOWN_MS = 3_000L

        /**
         * Delay between setRingerMode() and setDnd().
         *
         * Setting RINGER_MODE_SILENT triggers Android's interruption-filter change
         * asynchronously — the OS fires it a few hundred ms after the call returns.
         * If we call setDnd() immediately after, Android's auto-change lands later
         * and silently overwrites our value. Waiting here lets the side effect settle
         * so our explicit setDnd() always wins.
         */
        private const val RINGER_SETTLE_MS = 400L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository         = GoogleCloudRepository()
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

        // ── 1. Remote → Local ────────────────────────────────────────────────────
        scope.launch {
            repository.getDevicesFlow().collect { devices ->
                val myDevice = devices.find { it.id == deviceId } ?: return@collect
                val prev     = currentRemoteDevice

                // Always update currentRemoteDevice immediately so the local→remote
                // coroutine has the latest reference even during the settle delay below.
                currentRemoteDevice = myDevice

                var commandApplied = false

                if (prev == null || prev.mediaVolume != myDevice.mediaVolume) {
                    localDeviceManager.setVolume(myDevice.mediaVolume)
                    commandApplied = true
                }

                val ringerChanged = prev == null || prev.ringerMode  != myDevice.ringerMode
                val dndChanged    = prev == null || prev.isDndActive != myDevice.isDndActive

                if (ringerChanged || dndChanged) {
                    commandApplied = true

                    if (ringerChanged) {
                        localDeviceManager.setRingerMode(myDevice.ringerMode)

                        // Wait for Android's async side-effect on the interruption filter
                        // to settle before we apply the desired DND state. Without this
                        // delay, RINGER_MODE_SILENT's auto-filter-change fires after our
                        // setDnd() call and silently overwrites it.
                        delay(RINGER_SETTLE_MS)
                    }

                    // Always apply DND explicitly after ringer mode has settled.
                    // This correctly handles:
                    //   - Silent + DND off  → clears the filter Silent auto-set
                    //   - Silent + DND on   → leaves (or sets) DND active
                    //   - Sound/Vibrate + DND toggle → normal case, no side effects
                    localDeviceManager.setDnd(myDevice.isDndActive)
                }

                if (prev == null || prev.isCurtainOn != myDevice.isCurtainOn) {
                    localDeviceManager.setCurtain(myDevice.isCurtainOn)
                    commandApplied = true
                }

                if (commandApplied) {
                    lastCommandAppliedAt = System.currentTimeMillis()
                }
            }
        }

        // ── 2. Local → Remote ────────────────────────────────────────────────────
        // Upload sensor/status data only. ringerMode and isDndActive are intentionally
        // excluded — they are control fields owned by the remote controller. Echoing
        // them back would cause a feedback loop (Silent auto-sets DND → upload →
        // setDnd fires again → loop).
        scope.launch {
            localDeviceManager.observeDeviceState().collectLatest { state ->
                // Skip upload during cooldown so OS side-effects from a recent command
                // cannot overwrite the commanded state before it has fully settled.
                val msSinceCommand = System.currentTimeMillis() - lastCommandAppliedAt
                if (msSinceCommand < COOLDOWN_MS) return@collectLatest

                val cachedDevice = currentRemoteDevice ?: return@collectLatest

                val needsUpdate =
                    cachedDevice.batteryLevel   != state.batteryLevel   ||
                            cachedDevice.isCharging     != state.isCharging     ||
                            cachedDevice.mediaVolume    != state.mediaVolume    ||
                            cachedDevice.maxMediaVolume != state.maxMediaVolume ||
                            cachedDevice.isScreenOn     != state.isScreenOn     ||
                            cachedDevice.isCurtainOn    != state.isCurtainOn

                if (needsUpdate) {
                    val updatedDevice = cachedDevice.copy(
                        batteryLevel   = state.batteryLevel,
                        isCharging     = state.isCharging,
                        mediaVolume    = state.mediaVolume,
                        maxMediaVolume = state.maxMediaVolume,
                        isScreenOn     = state.isScreenOn,
                        isCurtainOn    = state.isCurtainOn
                        // ringerMode and isDndActive intentionally omitted
                    )
                    currentRemoteDevice = updatedDevice
                    repository.updateDevice(updatedDevice)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cloud Remote Sync Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cloud Remote Active")
            .setContentText("Syncing device state...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        scope.cancel()
    }
}