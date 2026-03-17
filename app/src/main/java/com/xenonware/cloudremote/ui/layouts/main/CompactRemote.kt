package com.xenonware.cloudremote.ui.layouts.main

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.identity.Identity
import com.xenonware.cloudremote.presentation.sign_in.GoogleAuthUiClient
import com.xenonware.cloudremote.presentation.sign_in.SignInViewModel
import com.xenonware.cloudremote.ui.res.DeviceControlScreen
import com.xenonware.cloudremote.ui.res.LoginScreen
import com.xenonware.cloudremote.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun CompactRemote(
    viewModel: MainViewModel,
    appSize: IntSize,
    onSignInClick: () -> Unit,
    ) {
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

    if (currentUser == null) {
        LoginScreen(onSignInClick = onSignInClick)
    } else {
        DeviceControlScreen(viewModel = viewModel)
    }
}
