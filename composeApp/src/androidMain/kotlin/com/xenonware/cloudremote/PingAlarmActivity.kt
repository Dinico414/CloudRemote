package com.xenonware.cloudremote

import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xenon.mylibrary.theme.QuicksandTitleVariable
import com.xenonware.cloudremote.data.SharedPreferenceManager
import com.xenonware.cloudremote.helper.LocalDeviceManager
import com.xenonware.cloudremote.ui.res.AnimatedGradientBackground
import com.xenonware.cloudremote.ui.theme.XenonTheme
import com.xenonware.cloudremote.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class PingAlarmActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var flashJob: Job? = null

    private var originalVolume: Int = 0
    private var originalAlarmVolume: Int = 0
    private var originalDnd: Boolean = false
    private var originalRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var isStateSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPreferenceManager = SharedPreferenceManager(applicationContext)
        viewModel.localDeviceId = sharedPreferenceManager.localDeviceId

        // Show over lock screen
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        handleIntent(intent)
        setupUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("STOP_PING", false) == true) {
            dismissAlarm()
        } else {
            if (ringtone == null || !ringtone!!.isPlaying) {
                initAlarmState()
                startAlarm()
            }
        }
    }

    private fun initAlarmState() {
        val localDeviceManager = LocalDeviceManager(this)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        if (!isStateSaved) {
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            originalRingerMode = audioManager.ringerMode
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            originalDnd = notificationManager.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            isStateSaved = true
        }

        localDeviceManager.setDnd(false)
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        
        val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        
        audioManager.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
    }

    private fun setupUI() {
        setContent {
            XenonTheme(darkTheme = isSystemInDarkTheme()) {
                AnimatedGradientBackground {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        PingAlarmScreen(
                            onDismiss = {
                                dismissAlarm()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun startAlarm() {
        val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, notification)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone?.audioAttributes = attributes

        ringtone?.isLooping = true
        ringtone?.play()

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))

        startFlashing()
    }

    private fun startFlashing() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraId = try {
            cameraManager.cameraIdList.firstOrNull()
        } catch (_: Exception) {
            null
        } ?: return

        flashJob = CoroutineScope(Dispatchers.Default).launch {
            var isOn = false
            try {
                while (isActive) {
                    try {
                        cameraManager.setTorchMode(cameraId, !isOn)
                        isOn = !isOn
                    } catch (_: Exception) {
                        break
                    }
                    delay(300.milliseconds)
                }
            } finally {
                withContext(NonCancellable) {
                    try {
                        cameraManager.setTorchMode(cameraId, false)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun dismissAlarm() {
        ringtone?.stop()
        vibrator?.cancel()
        flashJob?.cancel()

        if (isStateSaved) {
            val localDeviceManager = LocalDeviceManager(this)
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            
            localDeviceManager.setDnd(originalDnd)
            audioManager.ringerMode = originalRingerMode
            audioManager.setStreamVolume(AudioManager.STREAM_RING, originalVolume, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
        }

        if (viewModel.localDeviceId.isNotBlank()) {
            viewModel.updateDeviceFields(viewModel.localDeviceId, mapOf("isPinged" to false))
        }

        CoroutineScope(Dispatchers.Main).launch {
            delay(1000.milliseconds)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtone?.stop()
        vibrator?.cancel()
        flashJob?.cancel()
    }
}

@Composable
fun PingAlarmScreen(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MyLocation,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.device_pinged),
                style = MaterialTheme.typography.headlineLarge.copy(
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.25f),
                        offset = Offset(x = 2f, y = 4f),
                        blurRadius = 8f
                    )
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontFamily = QuicksandTitleVariable,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.device_pinged_description),
                style = MaterialTheme.typography.bodyLarge.copy(
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = Offset(x = 1f, y = 2f),
                        blurRadius = 2f
                    )
                ), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ), modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(96.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dismiss_alarm),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = QuicksandTitleVariable,
            )
        }
    }
}
