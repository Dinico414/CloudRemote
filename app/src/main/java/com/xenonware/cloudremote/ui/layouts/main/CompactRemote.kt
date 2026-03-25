package com.xenonware.cloudremote.ui.layouts.main

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Curtains
import androidx.compose.material.icons.rounded.CurtainsClosed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.identity.Identity
import com.xenon.mylibrary.ActivityScreen
import com.xenon.mylibrary.res.FloatingToolbarContent
import com.xenon.mylibrary.res.GoogleProfilBorder
import com.xenon.mylibrary.res.GoogleProfilePicture
import com.xenon.mylibrary.res.SpannedModeFAB
import com.xenon.mylibrary.theme.DeviceConfigProvider
import com.xenon.mylibrary.theme.LocalDeviceConfig
import com.xenon.mylibrary.values.ExtraLargePadding
import com.xenon.mylibrary.values.LargePadding
import com.xenon.mylibrary.values.MediumPadding
import com.xenon.mylibrary.values.NoSpacing
import com.xenon.mylibrary.values.SmallPadding
import com.xenonware.cloudremote.R
import com.xenonware.cloudremote.SwipeableCurtainActivity
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.data.SharedPreferenceManager
import com.xenonware.cloudremote.presentation.sign_in.GoogleAuthUiClient
import com.xenonware.cloudremote.presentation.sign_in.SignInViewModel
import com.xenonware.cloudremote.service.CurtainTileService
import com.xenonware.cloudremote.ui.res.DeviceItem
import com.xenonware.cloudremote.ui.res.LoginScreen
import com.xenonware.cloudremote.viewmodel.LayoutType
import com.xenonware.cloudremote.viewmodel.MainViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactRemote(
    viewModel: MainViewModel,
    appSize: IntSize,
    layoutType: LayoutType,
    isLandscape: Boolean,
    onOpenSettings: () -> Unit,
    onSignInClick: () -> Unit,
) {
    DeviceConfigProvider(appSize = appSize) {

        // ============================================================================
        // 1. Device, Screen & Layout Configuration
        // ============================================================================
        val deviceConfig = LocalDeviceConfig.current
        var backProgress by remember { mutableFloatStateOf(0f) }
        val context = LocalContext.current
        val sharedPreferenceManager = remember { SharedPreferenceManager(context) }

        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val appHeight = configuration.screenHeightDp.dp
        val screenWidthDp = with(density) { appSize.width.toDp() }.value.toInt()

        val isAppBarExpandable = when (layoutType) {
            LayoutType.COVER -> false
            LayoutType.SMALL -> false
            LayoutType.COMPACT -> !isLandscape && appHeight >= 460.dp
            LayoutType.MEDIUM -> true
            LayoutType.EXPANDED -> true
        }

        // ============================================================================
        // 2. UI / Navigation / Interaction State
        // ============================================================================
        val hazeState = rememberHazeState()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        val lazyListState = rememberLazyListState()

        var isSearchActive by rememberSaveable { mutableStateOf(false) }


        // ============================================================================
        // 3. Authentication & Preferences
        // ============================================================================
        val googleAuthUiClient = remember {
            GoogleAuthUiClient(
                context = context.applicationContext,
                oneTapClient = Identity.getSignInClient(context.applicationContext)
            )
        }
        val signInViewModel: SignInViewModel = viewModel()
        val state by signInViewModel.state.collectAsStateWithLifecycle()
        val userData = googleAuthUiClient.getSignedInUser()

        // ============================================================================
        // 4. Small Utility Functions & Effects
        // ============================================================================

        LaunchedEffect(state.isSignInSuccessful) {
            if (state.isSignInSuccessful) {
                viewModel.onSignedIn()
            }
        }

        val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
        val devices by viewModel.devices.collectAsState()
        val localDevice = devices.find { it.id == viewModel.localDeviceId }
        val localDeviceName by viewModel.localDeviceName.collectAsStateWithLifecycle()


        Scaffold(snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }, bottomBar = {
            val bottomPaddingNavigationBar =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val imePaddingValues = WindowInsets.ime.asPaddingValues()
            val imeHeight = imePaddingValues.calculateBottomPadding()

            val targetBottomPadding =
                remember(imeHeight, bottomPaddingNavigationBar, imePaddingValues) {
                    val calculatedPadding = if (imeHeight > bottomPaddingNavigationBar) {
                        imeHeight + LargePadding
                    } else {
                        max(
                            bottomPaddingNavigationBar, imePaddingValues.calculateTopPadding()
                        ) + LargePadding
                    }
                    max(calculatedPadding, 0.dp)
                }

            val animatedBottomPadding by animateDpAsState(
                targetValue = targetBottomPadding, animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow
                ), label = "bottomPaddingAnimation"
            )

            FloatingToolbarContent(
                hazeState = hazeState,
                onSearchQueryChanged = { },
                currentSearchQuery = "",
                lazyListState = lazyListState,
                allowToolbarScrollBehavior = !isAppBarExpandable,
                isSelectedColor = MaterialTheme.colorScheme.background,
                selectedNoteIds = emptyList(),
                onClearSelection = { },
                isAddModeActive = false,
                onAddModeToggle = { },
                isSearchActive = isSearchActive,
                onIsSearchActiveChange = { isSearchActive = it },
                defaultContent = { iconsAlphaDuration, showActionIconsExceptSearch ->
                    Row {
                        val iconAlphaTarget = if (isSearchActive) 0f else 1f

                        val curtainIconAlpha by animateFloatAsState(
                            targetValue = iconAlphaTarget, animationSpec = tween(
                                durationMillis = iconsAlphaDuration,
                                delayMillis = if (isSearchActive) 0 else 0
                            ), label = "CurtainIconAlpha"
                        )

                        // Curtain button
                        IconButton(
                            onClick = {
                                if (CurtainTileService.isCurtainActive) {
                                    val closeIntent =
                                        Intent(SwipeableCurtainActivity.ACTION_CLOSE_CURTAIN)
                                    context.sendBroadcast(closeIntent)
                                } else {
                                    val intent =
                                        Intent(context, SwipeableCurtainActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                                CurtainTileService.isCurtainActive =
                                    !CurtainTileService.isCurtainActive
                                TileService.requestListeningState(
                                    context, ComponentName(context, CurtainTileService::class.java)
                                )
                            },
                            modifier = Modifier.alpha(curtainIconAlpha),
                            enabled = !isSearchActive && showActionIconsExceptSearch
                        ) {
                            val icon =
                                if (localDevice?.isCurtainOn == true) Icons.Rounded.CurtainsClosed else Icons.Rounded.Curtains
                            Icon(icon, contentDescription = "Curtain Trigger")
                        }

                        val settingsIconAlpha by animateFloatAsState(
                            targetValue = iconAlphaTarget, animationSpec = tween(
                                durationMillis = iconsAlphaDuration,
                                delayMillis = if (isSearchActive) 100 else 0
                            ), label = "SettingsIconAlpha"
                        )

                        // Settings button
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.alpha(settingsIconAlpha),
                            enabled = !isSearchActive && showActionIconsExceptSearch
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                },
                fabOverride = null,
                isFabEnabled = false,
                isSpannedMode = deviceConfig.isSpannedMode,
                fabOnLeftInSpannedMode = deviceConfig.fabOnLeft,
                spannedModeHingeGap = deviceConfig.hingeGapDp,
                spannedModeFab = {
                    SpannedModeFAB(
                        hazeState = hazeState,
                        onClick = deviceConfig.toggleFabSide,
                        modifier = Modifier.padding(bottom = animatedBottomPadding)
                    )
                })

        }) { scaffoldPadding ->
            ActivityScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                titleText = stringResource(id = R.string.app_name),
                expandable = isAppBarExpandable,
                navigationIconStartPadding = if (state.isSignInSuccessful) SmallPadding else 0.dp,
                navigationIconPadding = if (state.isSignInSuccessful) SmallPadding else 0.dp,
                navigationIconSpacing = NoSpacing,
                hasNavigationIconExtraContent = state.isSignInSuccessful,
                navigationIconExtraContent = {
                    if (state.isSignInSuccessful) {
                        Box(contentAlignment = Alignment.Center) {
                            GoogleProfilBorder(
                                isSignedIn = true,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.5.dp
                            )

                            GoogleProfilePicture(
                                noAccIcon = painterResource(id = R.drawable.default_icon),
                                profilePictureUrl = userData?.profilePictureUrl,
                                contentDescription = stringResource(R.string.profile_picture),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                },
                navigationIcon = {},
                actions = {},
                content = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (currentUser == null) {
                            LoginScreen(onSignInClick = onSignInClick)
                        } else {
                            var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

                            LaunchedEffect(Unit) {
                                while (true) {
                                    delay(30000)
                                    currentTime = System.currentTimeMillis()
                                }
                            }

                            val cloudDevices = devices.filter { it.id != viewModel.localDeviceId }
                            val (onlineCloudDevices, offlineCloudDevices) = cloudDevices.partition {
                                (currentTime - it.lastUpdated) < 180_000 // 3 minute threshold
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = ExtraLargePadding,
                                    bottom = scaffoldPadding.calculateBottomPadding() + MediumPadding,
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    val isSharing = localDevice != null
                                    val deviceToDisplay = localDevice ?: Device(
                                        id = viewModel.localDeviceId,
                                        name = localDeviceName.ifBlank {
                                            currentUser?.displayName ?: "This Device"
                                        })

                                    DeviceItem(
                                        device = deviceToDisplay,
                                        isLocalDevice = true,
                                        isOnline = true,
                                        isSharing = isSharing,
                                        onUpdateDevice = { updatedDevice ->
                                            viewModel.updateDevice(updatedDevice)
                                        },
                                        onToggleShare = { name, icon ->
                                            viewModel.toggleCurrentDevice(
                                                name, icon
                                            )
                                        },
                                        onRemoveDevice = { removedDevice ->
                                            viewModel.removeDevice(removedDevice)
                                        })
                                }

                                if (onlineCloudDevices.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Cloud Devices",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(
                                                top = 8.dp,
                                            )
                                        )
                                    }

                                    items(onlineCloudDevices) { device ->
                                        DeviceItem(
                                            device = device,
                                            isLocalDevice = false,
                                            isOnline = true,
                                            isSharing = false,
                                            onUpdateDevice = { updatedDevice ->
                                                viewModel.updateDevice(
                                                    updatedDevice
                                                )
                                            },
                                            onToggleShare = { _, _ -> },
                                            onRemoveDevice = { removedDevice ->
                                                viewModel.removeDevice(removedDevice)
                                            })
                                    }
                                }

                                if (offlineCloudDevices.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Offline Devices",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(
                                                top = 8.dp,
                                            )
                                        )
                                    }

                                    items(offlineCloudDevices) { device ->
                                        DeviceItem(
                                            device = device,
                                            isLocalDevice = false,
                                            isOnline = false,
                                            isSharing = false,
                                            onUpdateDevice = { updatedDevice ->
                                                viewModel.updateDevice(
                                                    updatedDevice
                                                )
                                            },
                                            onToggleShare = { _, _ -> },
                                            onRemoveDevice = { removedDevice ->
                                                viewModel.removeDevice(removedDevice)
                                            })
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}