package com.xenonware.cloudremote

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.rounded.DoDisturbOn
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.ui.theme.XenonTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceId = androidId ?: UUID.randomUUID().toString()
        viewModel.localDeviceId = deviceId
        viewModel.localDeviceName = Build.MODEL

        checkOverlayPermission()
        checkDoNotDisturbPermission()

        startCloudRemoteService(deviceId)

        setContent {
            XenonTheme(darkTheme = isSystemInDarkTheme()) {
                val currentUser by viewModel.currentUser.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (currentUser == null) {
                        LoginScreen(
                            modifier = Modifier.padding(innerPadding),
                            onTokenReceived = { token -> viewModel.signInWithGoogle(token) })
                    } else {
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                        ) {
                            Button(
                                onClick = { viewModel.signOut() },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Sign Out (${currentUser?.email})")
                            }
                            DeviceControlScreen(
                                viewModel = viewModel, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startCloudRemoteService(deviceId: String) {
        val intent = Intent(this, CloudRemoteService::class.java)
        intent.putExtra(CloudRemoteService.EXTRA_DEVICE_ID, deviceId)
        startForegroundService(intent)
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()
            )
            startActivity(intent)
            Toast.makeText(
                this, "Please grant Overlay permission for Curtain feature", Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkDoNotDisturbPermission() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Please grant Do Not Disturb permission for Ringer Mode control",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
fun LoginScreen(modifier: Modifier = Modifier, onTokenReceived: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val webClientId = remember(context) {
        val resId =
            context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) context.resources.getString(resId) else null
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = {
            if (webClientId == null) {
                Toast.makeText(
                    context,
                    "Web Client ID not found. Ensure google-services.json is valid.",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("Auth", "default_web_client_id is missing.")
                return@Button
            }

            coroutineScope.launch {
                try {
                    val credentialManager = CredentialManager.create(context)

                    val googleIdOption =
                        GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false)
                            .setServerClientId(webClientId).setAutoSelectEnabled(true).build()

                    val request =
                        GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

                    val result = credentialManager.getCredential(context, request)
                    val credential = result.credential

                    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential =
                            GoogleIdTokenCredential.createFrom(credential.data)
                        onTokenReceived(googleIdTokenCredential.idToken)
                    } else {
                        Log.e("Auth", "Unexpected credential type: ${credential.type}")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }) {
            Text("Sign in with Google")
        }
    }
}

@Composable
fun DeviceControlScreen(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    val devices by viewModel.devices.collectAsState()
    val isLocalDeviceAdded = devices.any { it.id == viewModel.localDeviceId }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {

        Button(
            onClick = { viewModel.toggleCurrentDevice() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(if (isLocalDeviceAdded) "Remove this device from sync" else "Add this device to sync")
        }

        Text(
            text = "Connected Devices:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()
        ) {
            items(devices) { device ->
                DeviceItem(
                    device = device,
                    isLocalDevice = device.id == viewModel.localDeviceId,
                    onUpdateDevice = { updatedDevice -> viewModel.updateDevice(updatedDevice) })
            }
        }
    }
}

@Composable
fun DeviceItem(device: Device, isLocalDevice: Boolean, onUpdateDevice: (Device) -> Unit) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            currentTime = System.currentTimeMillis()
        }
    }

    val isOnline = (currentTime - device.lastUpdated) < 60_000 // 1 minute threshold

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name.ifBlank { "Unknown Device" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = if (isOnline) "Online" else "Offline",
                        tint = if (isOnline) Color.Green else Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                }

                if (isLocalDevice) {
                    Text(
                        text = "(This Device)",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status: ${if (isOnline) "Online" else "Offline"}")
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = if (device.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (device.isLocked) "Locked" else "Unlocked",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (device.isLocked) "Locked" else "Unlocked")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Battery: ${device.batteryLevel}%")
                if (device.isCharging) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.FlashOn,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = ""
                    )
                }
            }
            Text("Screen: ${if (device.isScreenOn) "On" else "Off"}")

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Sound Mode:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // DND toggle — independent of ringerMode
                SoundModeIconButton(
                    icon = Icons.Rounded.DoDisturbOn,
                    label = "DND",
                    isActive = device.isDndActive,
                    activeColor = MaterialTheme.colorScheme.error,
                    onClick = { onUpdateDevice(device.copy(isDndActive = !device.isDndActive)) })
                // Silent: ringerMode = 0
                SoundModeIconButton(
                    icon = Icons.AutoMirrored.Rounded.VolumeOff,
                    label = "Silent",
                    isActive = device.ringerMode == 0,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = { onUpdateDevice(device.copy(ringerMode = 0)) })
                // Vibrate: ringerMode = 1
                SoundModeIconButton(
                    icon = Icons.Default.Vibration,
                    label = "Vibrate",
                    isActive = device.ringerMode == 1,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = { onUpdateDevice(device.copy(ringerMode = 1)) })
                // Sound: ringerMode = 2
                SoundModeIconButton(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    label = "Sound",
                    isActive = device.ringerMode == 2,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = { onUpdateDevice(device.copy(ringerMode = 2)) })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Media Volume
            Text("Media Volume: ${device.mediaVolume}")
            Slider(
                value = device.mediaVolume.toFloat(),
                onValueChange = { onUpdateDevice(device.copy(mediaVolume = it.toInt())) },
                valueRange = 0f..device.maxMediaVolume.toFloat(),
                steps = if (device.maxMediaVolume > 0) device.maxMediaVolume - 1 else 0
            )

            // Curtain toggle
            Button(
                onClick = { onUpdateDevice(device.copy(isCurtainOn = !device.isCurtainOn)) },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (device.isCurtainOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (device.isCurtainOn) "Turn Curtain Off" else "Turn Curtain On")
            }
        }
    }
}

@Composable
fun SoundModeIconButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isActive) activeColor else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = icon, contentDescription = label, modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}