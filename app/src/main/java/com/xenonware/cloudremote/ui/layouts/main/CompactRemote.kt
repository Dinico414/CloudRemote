package com.xenonware.cloudremote.ui.layouts.main

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xenon.mylibrary.ActivityScreen
import com.xenon.mylibrary.res.FloatingToolbarContent
import com.xenon.mylibrary.theme.DeviceConfigProvider
import com.xenon.mylibrary.values.ExtraLargePadding
import com.xenon.mylibrary.values.MediumPadding
import com.xenonware.cloudremote.R
import com.xenonware.cloudremote.presentation.sign_in.SignInViewModel
import com.xenonware.cloudremote.ui.res.DeviceItem
import com.xenonware.cloudremote.ui.res.LoginScreen
import com.xenonware.cloudremote.viewmodel.MainViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactRemote(
    viewModel: MainViewModel,
    appSize: IntSize,
    onSignInClick: () -> Unit,
) {
    DeviceConfigProvider(appSize = appSize) {
        val context = LocalContext.current
        val signInViewModel: SignInViewModel = viewModel(
            factory = SignInViewModel.SignInViewModelFactory(context.applicationContext as Application)
        )
        val state by signInViewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(state.isSignInSuccessful) {
            if (state.isSignInSuccessful) {
                viewModel.onSignedIn()
            }
        }

        val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

        val hazeState = rememberHazeState()
        val snackbarHostState = remember { SnackbarHostState() }
        val lazyListState = rememberLazyListState()
        var isSearchActive by rememberSaveable { mutableStateOf(false) }

        Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, bottomBar = {
            FloatingToolbarContent(
                hazeState = hazeState,
                onSearchQueryChanged = { },
                currentSearchQuery = "",
                lazyListState = lazyListState,
                allowToolbarScrollBehavior = true,
                isSelectedColor = MaterialTheme.colorScheme.background,
                selectedNoteIds = emptyList(),
                onClearSelection = { },
                isAddModeActive = false,
                onAddModeToggle = { },
                isSearchActive = isSearchActive,
                onIsSearchActiveChange = { isSearchActive = it },
                defaultContent = { _, _ -> },
            )
        }) { scaffoldPadding ->
            ActivityScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                titleText = stringResource(id = R.string.app_name),
                content = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (currentUser == null) {
                            LoginScreen(onSignInClick = onSignInClick)
                        } else {
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
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = ExtraLargePadding,
                                    bottom = scaffoldPadding.calculateBottomPadding() + MediumPadding,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                localDevice?.let {
                                    item {
                                        DeviceItem(
                                            device = it,
                                            isLocalDevice = true,
                                            isOnline = true, // Local device is always considered online for UI purposes
                                            onUpdateDevice = { updatedDevice ->
                                                viewModel.updateDevice(
                                                    updatedDevice
                                                )
                                            })
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
                                            onUpdateDevice = { updatedDevice ->
                                                viewModel.updateDevice(
                                                    updatedDevice
                                                )
                                            })
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
                                            onUpdateDevice = { updatedDevice ->
                                                viewModel.updateDevice(
                                                    updatedDevice
                                                )
                                            })
                                    }
                                }
                            }
                        }
                    }
                })
        }
    }
}
