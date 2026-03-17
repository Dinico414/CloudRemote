package com.xenonware.cloudremote.ui.layouts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xenon.mylibrary.theme.LayoutType
import com.xenonware.cloudremote.presentation.sign_in.GoogleAuthUiClient
import com.xenonware.cloudremote.presentation.sign_in.SignInState
import com.xenonware.cloudremote.ui.layouts.settings.CoverSettings
import com.xenonware.cloudremote.ui.layouts.settings.DefaultSettings
import com.xenonware.cloudremote.viewmodel.SettingsViewModel

@Composable
fun SettingsLayout(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel,
    isLandscape: Boolean,
    layoutType: LayoutType,
    onNavigateToDeveloperOptions: () -> Unit,
    modifier: Modifier = Modifier,
    state: SignInState,
    googleAuthUiClient: GoogleAuthUiClient,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onConfirmSignOut: () -> Unit,
) {
    when (layoutType) {
        LayoutType.COVER -> {
            CoverSettings(
                onNavigateBack = onNavigateBack,
                viewModel = viewModel,
                onNavigateToDeveloperOptions = onNavigateToDeveloperOptions,
                state = state,
                googleAuthUiClient = googleAuthUiClient,
                onSignInClick = onSignInClick,
                onSignOutClick = onSignOutClick,
                onConfirmSignOut = onConfirmSignOut
            )
        }

        LayoutType.SMALL, LayoutType.COMPACT, LayoutType.MEDIUM, LayoutType.EXPANDED -> {
            DefaultSettings(
                onNavigateBack = onNavigateBack,
                viewModel = viewModel,
                layoutType = layoutType,
                isLandscape = isLandscape,
                onNavigateToDeveloperOptions = onNavigateToDeveloperOptions,
                state = state,
                googleAuthUiClient = googleAuthUiClient,
                onSignInClick = onSignInClick,
                onSignOutClick = onSignOutClick,
                onConfirmSignOut = onConfirmSignOut
            )
        }
    }
}
