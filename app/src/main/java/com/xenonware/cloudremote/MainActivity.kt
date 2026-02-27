package com.xenonware.cloudremote

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoDisturbOn
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceId = androidId ?: UUID.randomUUID().toString()
        viewModel.localDeviceId = deviceId
        viewModel.localDeviceName = Build.MODEL

        checkOverlayPermission()
        checkDoNotDisturbPermission()
        checkDeviceAdminPermission()
        checkNotificationListenerPermission()

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
                        val devices by viewModel.devices.collectAsState()
                        val isLocalDeviceAdded = devices.any { it.id == viewModel.localDeviceId }
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.signOut() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Sign Out")
                                }
                                Button(
                                    onClick = { viewModel.toggleCurrentDevice() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isLocalDeviceAdded) "Remove device" else "Add device")
                                }
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

    private fun checkDeviceAdminPermission() {
        val devicePolicyManager =
            getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val componentName = ComponentName(this, AdminReceiver::class.java)

        if (!devicePolicyManager.isAdminActive(componentName)) {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(
                android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Cloud Remote needs this permission to lock the screen remotely."
            )
            startActivity(intent)
            Toast.makeText(
                this, "Please grant Device Admin permission for Remote Lock", Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkNotificationListenerPermission() {
        val enabledListeners =
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val myListener =
            ComponentName(this, MediaNotificationListener::class.java).flattenToString()
        if (enabledListeners == null || !enabledListeners.contains(myListener)) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivity(intent)
            Toast.makeText(
                this,
                "Please grant Notification Listener permission for Media Control",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@SuppressLint("LocalContextResourcesRead")
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
    val localDevice = devices.find { it.id == viewModel.localDeviceId }

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            currentTime = System.currentTimeMillis()
        }
    }

    val cloudDevices = devices.filter { it.id != viewModel.localDeviceId }
    val (onlineCloudDevices, offlineCloudDevices) = cloudDevices.partition {
        (currentTime - it.lastUpdated) < 60_000 // 1 minute threshold
    }

    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        localDevice?.let {
            item {
                DeviceItem(
                    device = it,
                    isLocalDevice = true,
                    isOnline = true, // Local device is always considered online for UI purposes
                    onUpdateDevice = { updatedDevice -> viewModel.updateDevice(updatedDevice) }
                )
            }
        }

        if (onlineCloudDevices.isNotEmpty()) {
            item {
                Text(
                    text = "Cloud Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            items(onlineCloudDevices) { device ->
                DeviceItem(
                    device = device,
                    isLocalDevice = false,
                    isOnline = true,
                    onUpdateDevice = { updatedDevice -> viewModel.updateDevice(updatedDevice) }
                )
            }
        }

        if (offlineCloudDevices.isNotEmpty()) {
            item {
                Text(
                    text = "Offline Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            items(offlineCloudDevices) { device ->
                DeviceItem(
                    device = device,
                    isLocalDevice = false,
                    isOnline = false,
                    onUpdateDevice = { updatedDevice -> viewModel.updateDevice(updatedDevice) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceItem(device: Device, isLocalDevice: Boolean, isOnline: Boolean, onUpdateDevice: (Device) -> Unit) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .graphicsLayer(alpha = if (isOnline) 1f else 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name.ifBlank { "Unknown Device" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isOnline) {
                Spacer(modifier = Modifier.height(8.dp))

                // Media Player
                if (device.mediaTitle.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val imageBytes = try {
                            Base64.decode(device.mediaAlbumArt, Base64.DEFAULT)
                        } catch (_: Exception) {
                            null
                        }
                        val bitmap =
                            imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

                        val isSquare = bitmap != null && (bitmap.width.toFloat() / bitmap.height.toFloat() == 1f)

                        Box(
                            modifier = if (isSquare) {
                                Modifier
                                    .width(96.dp)
                                    .height(96.dp)
                            } else {
                                Modifier
                                    .weight(1f)
                                    .height(96.dp)
                            }
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Album Art",
                                    modifier = Modifier
                                        .widthIn(max = 96.dp)
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 96.dp)
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            RoundedCornerShape(8.dp)
                                        )
                                )
                            }
                        }


                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = if (isSquare) Modifier.weight(1f) else Modifier.width(IntrinsicSize.Min)) {
                            Text(
                                text = device.mediaTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                            Text(
                                text = device.mediaArtist,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp)
                            )

                            val cornerRadius by animateDpAsState(
                                targetValue = if (device.isPlaying) 12.dp else 24.dp,
                                animationSpec = tween(300),
                                label = "playPauseShape"
                            )
                            Row(
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onUpdateDevice(device.copy(mediaAction = "previous")) },
                                    enabled = true
                                ) {
                                    Icon(
                                        Icons.Rounded.SkipPrevious,
                                        contentDescription = "Previous"
                                    )
                                }
                                IconButton(
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    onClick = { onUpdateDevice(device.copy(mediaAction = if (device.isPlaying) "pause" else "play")) },
                                    enabled = true
                                ) {
                                    Crossfade(
                                        targetState = device.isPlaying,
                                        animationSpec = tween(300),
                                        label = "playPauseIcon"
                                    ) { isPlaying ->
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play"
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onUpdateDevice(device.copy(mediaAction = "next")) },
                                    enabled = true
                                ) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                                }

                                CustomMediaActionButton(
                                    actionTitle = device.mediaCustomAction1Title,
                                    defaultIcon = Icons.Rounded.Add,
                                    onClick = { onUpdateDevice(device.copy(mediaAction = "custom1")) },
                                    enabled = true
                                )

                                CustomMediaActionButton(
                                    actionTitle = device.mediaCustomAction2Title,
                                    defaultIcon = Icons.Rounded.Remove,
                                    onClick = { onUpdateDevice(device.copy(mediaAction = "custom2")) },
                                    enabled = true
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (device.mediaCustomAction1Title.isNotBlank()) {
                        TextButton(onClick = { onUpdateDevice(device.copy(mediaAction = "custom1")) }) {
                            Text(device.mediaCustomAction1Title)
                        }
                    }

                    if (device.mediaCustomAction2Title.isNotBlank()) {
                        TextButton(onClick = { onUpdateDevice(device.copy(mediaAction = "custom2")) }) {
                            Text(device.mediaCustomAction2Title)
                        }
                    }
                }


                if (!isLocalDevice) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (!device.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                            .then(
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { if (!device.isLocked) onUpdateDevice(device.copy(isLocked = true)) }
                                )
                            )
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Icon(
                            tint = if (!device.isLocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                            imageVector = if (device.isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = if (device.isLocked) "Locked" else "Unlocked",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (device.isLocked) "Locked" else "Unlocked",
                            color = if (!device.isLocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
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

                val infiniteTransition = rememberInfiniteTransition(label = "batteryBlink")
                val blinkColor by infiniteTransition.animateColor(
                    initialValue = MaterialTheme.colorScheme.error,
                    targetValue = MaterialTheme.colorScheme.errorContainer,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500), repeatMode = RepeatMode.Reverse
                    ),
                    label = "blink"
                )

                val progressColor = if (device.isCharging) {
                    Color.Green
                } else if (device.batteryLevel <= 5) {
                    blinkColor
                } else if (device.batteryLevel <= 20) {
                    MaterialTheme.colorScheme.error
                } else if (device.batteryLevel >= 100) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                }

                val trackColor = if (!device.isCharging && device.batteryLevel <= 20) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    ProgressIndicatorDefaults.linearTrackColor
                }

                LinearProgressIndicator(
                    progress = { device.batteryLevel / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(vertical = 8.dp),
                    color = progressColor,
                    trackColor = trackColor,
                )

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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // DND toggle — independent of ringerMode
                    SoundModeIconButton(
                        icon = Icons.Rounded.DoDisturbOn,
                        isActive = device.isDndActive,
                        activeColor = MaterialTheme.colorScheme.error,
                        onClick = { onUpdateDevice(device.copy(isDndActive = !device.isDndActive)) },
                        modifier = Modifier.weight(1f),
                        enabled = true
                    )
                    // Silent: ringerMode = 0
                    SoundModeIconButton(
                        icon = Icons.AutoMirrored.Rounded.VolumeOff,
                        isActive = device.ringerMode == 0,
                        activeColor = MaterialTheme.colorScheme.primary,
                        onClick = { onUpdateDevice(device.copy(ringerMode = 0)) },
                        modifier = Modifier.weight(1f),
                        enabled = true
                    )
                    // Vibrate: ringerMode = 1
                    SoundModeIconButton(
                        icon = Icons.Rounded.Vibration,
                        isActive = device.ringerMode == 1,
                        activeColor = MaterialTheme.colorScheme.primary,
                        onClick = { onUpdateDevice(device.copy(ringerMode = 1)) },
                        modifier = Modifier.weight(1f),
                        enabled = true
                    )
                    // Sound: ringerMode = 2
                    SoundModeIconButton(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        isActive = device.ringerMode == 2,
                        activeColor = MaterialTheme.colorScheme.primary,
                        onClick = { onUpdateDevice(device.copy(ringerMode = 2)) },
                        modifier = Modifier.weight(1f),
                        enabled = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Media Volume
                Text("Media Volume: ${device.mediaVolume}")
                Slider(
                    value = device.mediaVolume.toFloat(),
                    onValueChange = { onUpdateDevice(device.copy(mediaVolume = it.toInt())) },
                    valueRange = 0f..device.maxMediaVolume.toFloat(),
                    steps = if (device.maxMediaVolume > 0) device.maxMediaVolume - 1 else 0,
                    enabled = true
                )
                if (!isLocalDevice) {
                    // Curtain toggle
                    Button(
                        onClick = { onUpdateDevice(device.copy(isCurtainOn = !device.isCurtainOn)) },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (device.isCurtainOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        enabled = true
                    ) {
                        Text(if (device.isCurtainOn) "Turn Curtain Off" else "Turn Curtain On")
                    }
                }
            } else {
                Text(
                    text = "device unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CustomMediaActionButton(
    actionTitle: String,
    defaultIcon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean
) {
    if (actionTitle.isNotBlank()) {
        IconButton(onClick = onClick, enabled = enabled) {
            val icon = when (actionTitle) {
                "Remove from collection" -> Icons.Rounded.CheckCircle
                "Add to collection" -> Icons.Rounded.AddCircleOutline
                "Start radio" -> Icons.Rounded.Podcasts
                "Toggle shuffle" -> Icons.Rounded.Shuffle
                else -> defaultIcon
            }
            Icon(icon, contentDescription = actionTitle)
        }
    }
}

@Composable
fun SoundModeIconButton(
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean
) {
    Column(
        modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isActive) activeColor else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
            ),
            enabled = enabled
        ) {
            Icon(
                imageVector = icon, contentDescription = "", modifier = Modifier.size(24.dp)
            )
        }
    }
}
