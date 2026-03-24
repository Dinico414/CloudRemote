@file:Suppress("AssignedValueIsNeverRead")

package com.xenonware.cloudremote.ui.res

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Curtains
import androidx.compose.material.icons.rounded.CurtainsClosed
import androidx.compose.material.icons.rounded.DoDisturbOn
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.xenon.mylibrary.theme.QuicksandTitleVariable
import com.xenonware.cloudremote.BuildConfig
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.ui.theme.LocalGreenMaterialColorScheme
import com.xenonware.cloudremote.ui.theme.LocalRedMaterialColorScheme
import kotlinx.coroutines.delay

fun getDeviceIconPrefix(name: String): String? {
    return when (name) {
        "Old Phone", "" -> "op"
        "Surface Duo" -> "sd"
        "New Phone (BigBezel)" -> "npbb"
        "New Phone (NoBezel)" -> "npnb"
        "New Phone (SmallNotch)" -> "npsn"
        "New Phone (BigNotch)" -> "npbn"
        "New Phone (PillCutOut)" -> "nppc"
        "New Phone (RoundCutOutCenter)" -> "nprcc"
        "New Phone (RoundCutOutLeft)" -> "nprcl"
        "New Phone (RoundCutOutRight)" -> "nprcr"
        "Flip Phone" -> "fp"
        "Fold (inwards)" -> "fi"
        "Fold (outwards)" -> "fo"
        "LG Wing" -> "lg"
        "iKKO Mind One" -> "ikko"
        "Clicks Communicator" -> "cc"
        "Keyboard Phone" -> "kp"
        "Tablet" -> "t"
        "Tablet (Notch)" -> "tn"
        "Tablet (RoundCutOut)" -> "trc"
        else -> "op"
    }
}

val deviceIconCategories = listOf(
    "Default" to listOf(
        "Old Phone",
        "New Phone (BigBezel)",
        "Keyboard Phone",
        "New Phone (SmallNotch)",
        "New Phone (BigNotch)",
        "New Phone (RoundCutOutLeft)",
        "New Phone (RoundCutOutCenter)",
        "New Phone (RoundCutOutRight)",
        "New Phone (PillCutOut)",
        "New Phone (NoBezel)"
    ), "Foldables" to listOf(
        "Flip Phone", "Fold (inwards)", "Fold (outwards)"
    ), "Tablet" to listOf(
        "Tablet", "Tablet (Notch)", "Tablet (RoundCutOut)"
    ), "Specific" to listOf(
        "LG Wing", "Surface Duo", "iKKO Mind One", "Clicks Communicator"
    )
)

@Suppress("SimplifyBooleanWithConstants")
@SuppressLint("LocalContextResourcesRead", "DiscouragedApi")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeviceItem(
    device: Device,
    isLocalDevice: Boolean,
    isOnline: Boolean,
    isSharing: Boolean,
    onUpdateDevice: (Device) -> Unit,
    onToggleShare: (String, String) -> Unit,
) {
    var showShareDialog by remember { mutableStateOf(false) }
    var shareName by remember { mutableStateOf("") }
    var shareIcon by remember { mutableStateOf("old phone") }
    var isCollapsed by remember { mutableStateOf(!isLocalDevice) }
    var lastRememberedVolume by remember { mutableIntStateOf(device.maxMediaVolume / 2) }

    val progressBackgroundColor by animateColorAsState(
        targetValue = if (device.isCharging) {
            LocalGreenMaterialColorScheme.current.secondaryContainer
        } else if (device.batteryLevel <= 5) {
            LocalRedMaterialColorScheme.current.secondaryContainer
        } else if (device.batteryLevel <= 20) {
            LocalRedMaterialColorScheme.current.secondaryContainer
        } else if (device.batteryLevel >= 100) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = tween(durationMillis = 500),
        label = "progressBackgroundColor"
    )

    val rowBackgroundColor by animateColorAsState(
        targetValue = if (isCollapsed && isOnline) progressBackgroundColor else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = tween(durationMillis = 500),
        label = "rowBackgroundColor"
    )

    var previewFrame by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(750L)
            for (f in 2..5) {
                previewFrame = f
                delay(100L)
            }
            delay(750L)
            for (f in 4 downTo 1) {
                previewFrame = f
                delay(100L)
            }
        }
    }

    val context = LocalContext.current

    if (showShareDialog) {
        AlertDialog(onDismissRequest = { }, title = { Text("Share Device") }, text = {
            Column {
                OutlinedTextField(
                    value = shareName,
                    onValueChange = { shareName = it },
                    placeholder = { Text(device.name.ifBlank { "Unknown Device" }) },
                    label = { Text("Device Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                deviceIconCategories.forEach { (categoryName, iconNames) ->
                    val scrollState = rememberScrollState()

                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    HorizontalScrollWithIndicator(
                        scrollState = scrollState,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        iconNames.forEach { name ->
                            val prefix = getDeviceIconPrefix(name)
                            if (prefix != null) {
                                val resId = remember(prefix, previewFrame) {
                                    context.resources.getIdentifier(
                                        prefix + previewFrame, "drawable", context.packageName
                                    )
                                }
                                if (resId != 0) {
                                    IconButton(
                                        onClick = { shareIcon = name },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (shareIcon == name) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                    ) {
                                        Image(
                                            painter = painterResource(id = resId),
                                            contentDescription = name,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }, confirmButton = {
            TextButton(onClick = {
                showShareDialog = false
                onToggleShare(
                    shareName.ifBlank { device.name.ifBlank { "Unknown Device" } }, shareIcon
                )
            }) {
                Text("Share")
            }
        }, dismissButton = {
            TextButton(onClick = {
                showShareDialog = false
            }) {
                Text("Cancel")
            }
        })
    }
    val animatedRadius = animateDpAsState(
        targetValue = if (isCollapsed) 40.dp else 30.dp,
        animationSpec = tween(durationMillis = 100),
        label = "cardRadius"
    )

    Card(
        shape = RoundedCornerShape(animatedRadius.value),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(
                    start = 16.dp, end = 16.dp, bottom = 16.dp, top = 16.dp
                )
                .graphicsLayer(alpha = if (isOnline) 1f else 0.5f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            val animatedBottomPadding by animateDpAsState(
                targetValue = if (!isCollapsed && isOnline && (!isLocalDevice || isSharing)) 4.dp else 0.dp,
                animationSpec = tween(durationMillis = 300),
                label = "bottomPadding"
            )

            // Device Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = animatedBottomPadding
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isMediaIndicatorVisible = isCollapsed && device.mediaTitle.isNotBlank()
                val transition =
                    updateTransition(targetState = isMediaIndicatorVisible, label = "mediaIndicatorTransition")

                val spacerWidth by transition.animateDp(
                    transitionSpec = {
                        if (targetState) { // Enter
                            tween(durationMillis = 150)
                        } else { // Exit
                            tween(durationMillis = 450)
                        }
                    }, label = "spacerWidth"
                ) { isVisible ->
                    if (isVisible) 8.dp else 0.dp
                }

                val boxWidth by transition.animateDp(
                    transitionSpec = {
                        if (targetState) { // entering
                            tween(durationMillis = 300)
                        } else { // exiting
                            tween(durationMillis = 300)
                        }
                    }, label = "boxWidth"
                ) { isVisible ->
                    if (isVisible) 40.dp else 0.dp
                }
                val iconAlpha by transition.animateFloat(
                    transitionSpec = {
                        if (targetState) { // entering
                            tween(durationMillis = 150, delayMillis = 150)
                        } else { // exiting
                            tween(durationMillis = 150)
                        }
                    }, label = "iconAlpha"
                ) { isVisible ->
                    if (isVisible) 1f else 0f
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .width(boxWidth)
                            .clip(RoundedCornerShape(100f))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = "Media active",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = iconAlpha),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(spacerWidth))
                }
                val prefix = getDeviceIconPrefix(device.icon) ?: "op"

                val targetFrame by animateIntAsState(
                    targetValue = if (device.isScreenOn) 5 else 1,
                    animationSpec = tween(durationMillis = 500),
                    label = "screenOnAnimation"
                )

                val resId = remember(prefix, targetFrame) {
                    context.resources.getIdentifier(
                        prefix + targetFrame, "drawable", context.packageName
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100f))
                        .background(rowBackgroundColor)
                        .padding(4.dp)
                ) {
                    if (resId != 0) {
                        Image(
                            modifier = Modifier.size(40.dp),
                            painter = painterResource(id = resId),
                            contentDescription = "Device Icon",
                        )
                    }

                    Text(
                        text = device.name.ifBlank { "Unknown Device" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                when {
                    isLocalDevice -> {
                        FilledTonalIconButton(onClick = {
                            if (isSharing) {
                                onToggleShare(device.name, device.icon)
                            } else {
                                shareName = ""
                                shareIcon = "old phone"
                                showShareDialog = true
                            }
                        }) {
                            Icon(
                                imageVector = if (isSharing) Icons.Rounded.LinkOff else Icons.Rounded.Link,
                                contentDescription = if (isSharing) "Stop Sharing" else "Share"
                            )
                        }
                    }

                    isOnline -> {
                        FilledTonalIconButton(onClick = {
                            isCollapsed = !isCollapsed
                        }) {
                            Icon(
                                imageVector = if (isCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                contentDescription = if (isCollapsed) "Expand" else "Collapse"
                            )
                        }
                    }

                    else -> {
                        FilledTonalIconButton(onClick = {
                            onToggleShare(
                                device.name, device.icon
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close, contentDescription = "Remove"
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = !isCollapsed && isOnline && (!isLocalDevice || isSharing),
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Media Player
                    if (device.mediaTitle.isNotBlank()) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                        ) {
                            val availableWidth = maxWidth
                            val buttonsWidth = 240.dp
                            val spacerWidth = 12.dp
                            val desiredAlbumSize = 96.dp

                            val albumWidth = min(
                                desiredAlbumSize,
                                max(0.dp, availableWidth - buttonsWidth - spacerWidth)
                            )

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val imageBytes = try {
                                    Base64.decode(device.mediaAlbumArt, Base64.DEFAULT)
                                } catch (_: Exception) {
                                    null
                                }
                                val bitmap = imageBytes?.let {
                                    BitmapFactory.decodeByteArray(
                                        it, 0, it.size
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(albumWidth)
                                        .height(96.dp)
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Album Art",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    MaterialTheme.colorScheme.secondaryContainer,
                                                    RoundedCornerShape(8.dp)
                                                )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
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
                                        val button1gone =
                                            if (device.mediaCustomAction1Title == "null") 24.dp else 0.dp
                                        val button2gone =
                                            if (device.mediaCustomAction2Title == "null") 24.dp else 0.dp

                                        Spacer(modifier = Modifier.width(button1gone + button2gone))
                                        IconButton(
                                            onClick = {
                                                onUpdateDevice(
                                                    device.copy(
                                                        mediaAction = "previous"
                                                    )
                                                )
                                            }, enabled = true
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
                                            onClick = {
                                                onUpdateDevice(
                                                    device.copy(
                                                        mediaAction = if (device.isPlaying) "pause" else "play"
                                                    )
                                                )
                                            },
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
                                            onClick = {
                                                onUpdateDevice(
                                                    device.copy(
                                                        mediaAction = "next"
                                                    )
                                                )
                                            }, enabled = true
                                        ) {
                                            Icon(
                                                Icons.Rounded.SkipNext,
                                                contentDescription = "Next"
                                            )
                                        }

                                        CustomMediaActionButton(
                                            actionTitle = device.mediaCustomAction1Title,
                                            defaultIcon = Icons.Rounded.Add,
                                            onClick = {
                                                onUpdateDevice(
                                                    device.copy(
                                                        mediaAction = "custom1"
                                                    )
                                                )
                                            },
                                            enabled = true
                                        )

                                        CustomMediaActionButton(
                                            actionTitle = device.mediaCustomAction2Title,
                                            defaultIcon = Icons.Rounded.Remove,
                                            onClick = {
                                                onUpdateDevice(
                                                    device.copy(
                                                        mediaAction = "custom2"
                                                    )
                                                )
                                            },
                                            enabled = true
                                        )
                                        Spacer(modifier = Modifier.width(button1gone + button2gone))
                                    }
                                }
                            }
                        }
                    }

                    @Suppress("KotlinConstantConditions") if (BuildConfig.BUILD_TYPE == "debug" && device.mediaCustomAction1Title.isNotBlank() && device.mediaCustomAction2Title.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (device.mediaCustomAction1Title.isNotBlank()) {
                                TextButton(onClick = {
                                    onUpdateDevice(
                                        device.copy(
                                            mediaAction = "custom1"
                                        )
                                    )
                                }) {
                                    Text(device.mediaCustomAction1Title)
                                }
                            }

                            if (device.mediaCustomAction2Title.isNotBlank()) {
                                TextButton(onClick = {
                                    onUpdateDevice(
                                        device.copy(
                                            mediaAction = "custom2"
                                        )
                                    )
                                }) {
                                    Text(device.mediaCustomAction2Title)
                                }
                            }
                        }
                    }

                    // Control Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!isLocalDevice) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .animateContentSize()
                                    .clip(RoundedCornerShape(30.dp))
                                    .background(if (!device.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                                    .then(
                                        Modifier.combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                if (!device.isLocked) onUpdateDevice(
                                                    device.copy(
                                                        pendingAction = "lock"
                                                    )
                                                )
                                            })
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
                        BatteryIndicator(device.batteryLevel, device.isCharging)

                    }

                    // Sound Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // DND toggle — independent of ringerMode
                        IconToggleButton(
                            icon = Icons.Rounded.DoDisturbOn,
                            isActive = device.isDndActive,
                            activeColor = MaterialTheme.colorScheme.error,
                            onCheckedChange = { onUpdateDevice(device.copy(isDndActive = it)) },
                            modifier = Modifier.weight(1f),
                            enabled = true
                        )
                        XenonSingleChoiceButtonGroup(
                            options = listOf(0, 1, 2),
                            selectedOption = device.ringerMode,
                            connected = false,
                            onOptionSelect = { onUpdateDevice(device.copy(ringerMode = it)) },
                            label = { "" },
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(3f),
                            height = 48.dp,
                            icon = { option, _ ->
                                Icon(
                                    imageVector = when (option) {
                                        0 -> Icons.AutoMirrored.Rounded.VolumeOff
                                        1 -> Icons.Rounded.Vibration
                                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                                    }, contentDescription = "Ringer Mode"
                                )
                            })
                    }

                    // Media Volume Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LaunchedEffect(device.mediaVolume) {
                            if (device.mediaVolume != 0) {
                                lastRememberedVolume = device.mediaVolume
                            }
                        }
                        val isMuted = device.mediaVolume == 0
                        ToggleButton(
                            checked = isMuted,
                            onCheckedChange = { isChecked ->
                                val newVolume = if (isChecked) {
                                    0
                                } else {
                                    if (lastRememberedVolume == 0) (device.maxMediaVolume * 0.3).toInt() else lastRememberedVolume
                                }
                                onUpdateDevice(device.copy(mediaVolume = newVolume))
                            },
                            modifier = Modifier.height(48.dp),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                val mediaVolumeIcon =
                                    when ((device.mediaVolume.toFloat() / device.maxMediaVolume.toFloat() * 100).toInt()) {
                                        in 68..100 -> Icons.AutoMirrored.Rounded.VolumeUp
                                        in 35..67 -> Icons.AutoMirrored.Rounded.VolumeDown
                                        in 1..34 -> Icons.AutoMirrored.Rounded.VolumeMute
                                        else -> Icons.AutoMirrored.Rounded.VolumeOff
                                    }
                                Icon(
                                    imageVector = mediaVolumeIcon,
                                    contentDescription = "Media Volume",
                                    modifier = Modifier.size(24.dp)
                                )
                                Box(
                                    modifier = Modifier.width(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${device.mediaVolume}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = QuicksandTitleVariable
                                    )
                                }
                            }
                        }
                        Slider(
                            value = device.mediaVolume.toFloat(),
                            onValueChange = { onUpdateDevice(device.copy(mediaVolume = it.toInt())) },
                            valueRange = 0f..device.maxMediaVolume.toFloat(),
                            steps = if (device.maxMediaVolume > 0) device.maxMediaVolume - 1 else 0,
                            enabled = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!isLocalDevice) {
                        // Curtain toggle
                        Button(
                            onClick = { onUpdateDevice(device.copy(isCurtainOn = !device.isCurtainOn)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (device.isCurtainOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            enabled = true
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (device.isCurtainOn) Icons.Rounded.CurtainsClosed else Icons.Rounded.Curtains,
                                    contentDescription = ""
                                )
                                Text(if (device.isCurtainOn) "Curtain Off" else "Curtain On")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomMediaActionButton(
    actionTitle: String,
    defaultIcon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    if (actionTitle != "null") {
        IconButton(onClick = onClick, enabled = enabled) {
            val icon = when (actionTitle) {
                // Add / Checked
                "Remove from collection", "Aus Sammlung entfernen" -> Icons.Rounded.CheckCircle
                "Add to collection", "Zu Sammlung hinzufügen" -> Icons.Rounded.AddCircleOutline
                // Thumbs up
                "Mag ich", "Like" -> Icons.Outlined.ThumbUp
                "Like rückgängig machen", "Undo like" -> Icons.Filled.ThumbUp
                // Star
                "Als Favorit markieren", "Favorite" -> Icons.Rounded.StarBorder
                "Undo Favorite", "„Favorit“ widerrufen" -> Icons.Rounded.Star
                // Heart
                "LikeRatingAction", "Aus Meine Musik entfernen", "Favorit", "Favourite", "Remove from My Collection" -> Icons.Rounded.Favorite
                "UnLikeRatingAction" -> Icons.Rounded.FavoriteBorder
                // Radio
                "Start radio", "Radio starten" -> Icons.Rounded.Podcasts
                // Jump
                "Rücklauf" -> Icons.Rounded.Replay
                "30 Sek. zurückspulen", "Rewind 30 Sec" -> Icons.Rounded.Replay30
                "30 Sek. vorspulen", "Forward 30 Sec", "Schnellvorlauf" -> Icons.Rounded.Forward30
                // Repeat
                "Alle Songs wiederholen" -> Icons.Rounded.Repeat
                "Song wiederholen" -> Icons.Rounded.RepeatOn
                "Wiederholung aus" -> Icons.Rounded.RepeatOneOn
                // Shuffle
                "Toggle shuffle", "Zufallsmix aus", "Zufallsmix ein", "Shuffle off", "Shuffle on", "Shuffle", "Zufällig", "Zufallswiedergabe ein", "zufallswiedergabe aus", "Shuffle aktivieren/deaktivieren" -> Icons.Rounded.Shuffle
                // Stop
                "Stopp" -> Icons.Rounded.Stop
                // Infinity
                "Autoplay", "Automatisch wiedergeben" -> Icons.Rounded.AllInclusive
                else -> defaultIcon
            }
            Icon(icon, contentDescription = actionTitle)
        }
    }
}

@SuppressLint("FrequentlyChangingValue")
@Composable
fun HorizontalScrollWithIndicator(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            content = content
        )
        if (scrollState.maxValue > 0) {
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                val fraction = scrollState.value.toFloat() / scrollState.maxValue
                val thumbWidth = maxWidth * 0.25f
                Box(
                    modifier = Modifier
                        .width(thumbWidth)
                        .fillMaxHeight()
                        .offset(x = (maxWidth - thumbWidth) * fraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun BatteryIndicator(
    batteryLevel: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "colorBlink")
    val batteryWarningColor by infiniteTransition.animateColor(
        initialValue = LocalRedMaterialColorScheme.current.primary,
        targetValue = LocalRedMaterialColorScheme.current.secondaryContainer,
        animationSpec = infiniteRepeatable(
            animation = tween(500), repeatMode = RepeatMode.Reverse
        ),
        label = "lowBattery"
    )
    val batteryWarningTextColor by infiniteTransition.animateColor(
        initialValue = LocalRedMaterialColorScheme.current.onPrimary,
        targetValue = LocalRedMaterialColorScheme.current.onSecondaryContainer,
        animationSpec = infiniteRepeatable(
            animation = tween(500), repeatMode = RepeatMode.Reverse
        ),
        label = "lowBatteryText"
    )

    val batteryChargingColor by infiniteTransition.animateColor(
        initialValue = LocalGreenMaterialColorScheme.current.primary,
        targetValue = LocalGreenMaterialColorScheme.current.secondary,
        animationSpec = infiniteRepeatable(
            animation = tween(500), repeatMode = RepeatMode.Reverse
        ),
        label = "charging"
    )
    val batteryChargingTextColor by infiniteTransition.animateColor(
        initialValue = LocalGreenMaterialColorScheme.current.onPrimary,
        targetValue = LocalGreenMaterialColorScheme.current.onSecondary,
        animationSpec = infiniteRepeatable(
            animation = tween(500), repeatMode = RepeatMode.Reverse
        ),
        label = "chargingText"
    )


    val progressColor by animateColorAsState(
        targetValue = if (isCharging) {
            batteryChargingColor
        } else if (batteryLevel <= 5) {
            batteryWarningColor
        } else if (batteryLevel <= 20) {
            LocalRedMaterialColorScheme.current.primary
        } else if (batteryLevel >= 100) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        }, animationSpec = tween(durationMillis = 500), label = "progressColor"
    )

    val progressTextColor by animateColorAsState(
        targetValue = if (isCharging) {
            batteryChargingTextColor
        } else if (batteryLevel <= 5) {
            batteryWarningTextColor
        } else if (batteryLevel <= 20) {
            LocalRedMaterialColorScheme.current.onPrimary
        } else if (batteryLevel >= 100) {
            MaterialTheme.colorScheme.onTertiary
        } else {
            MaterialTheme.colorScheme.onPrimary
        }, animationSpec = tween(durationMillis = 500), label = "progressTextColor"
    )

    val progressBackgroundColor by animateColorAsState(
        targetValue = if (isCharging) {
            LocalGreenMaterialColorScheme.current.secondaryContainer
        } else if (batteryLevel <= 5) {
            LocalRedMaterialColorScheme.current.secondaryContainer
        } else if (batteryLevel <= 20) {
            LocalRedMaterialColorScheme.current.secondaryContainer
        } else if (batteryLevel >= 100) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }, animationSpec = tween(durationMillis = 500), label = "progressBackgroundColor"
    )

    Box(
        modifier = modifier
            .height(40.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(progressBackgroundColor)
    ) {
        //IndicatorBox
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(4.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        ) {
            val minWidth = 32.dp
            val maxWidth = this.maxWidth
            val fraction = (batteryLevel.coerceIn(0, 100) / 100f)
            val indicatorWidth = minWidth + (maxWidth - minWidth) * fraction

            Box(
                modifier = modifier
                    .height(32.dp)
                    .width(indicatorWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .background(progressColor)
            )
        }

        //TextPositionBox
        Box(
            modifier
                .align(Alignment.CenterStart)
                .height(40.dp)
                .width(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Transparent)
        ) {
            Text(
                text = "$batteryLevel",
                modifier.align(Alignment.Center),
                color = progressTextColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = QuicksandTitleVariable
            )
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IconToggleButton(
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ToggleButton(
            checked = isActive,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                checkedContainerColor = activeColor,
                checkedContentColor = if (activeColor == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            ),
            enabled = enabled,
            interactionSource = interactionSource
        ) {
            Icon(
                imageVector = icon, contentDescription = "", modifier = Modifier.size(24.dp)
            )
        }
    }
}
