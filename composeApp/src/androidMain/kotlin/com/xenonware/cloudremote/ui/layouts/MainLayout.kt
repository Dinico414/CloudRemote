package com.xenonware.cloudremote.ui.layouts

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize
import com.xenonware.cloudremote.ui.layouts.main.CompactRemote
import com.xenonware.cloudremote.viewmodel.LayoutType
import com.xenonware.cloudremote.viewmodel.MainViewModel

@Composable
fun MainLayout(
    viewModel: MainViewModel,
    isLandscape: Boolean,
    layoutType: LayoutType,
    onOpenSettings: () -> Unit,
    appSize: IntSize,
    onSignInClick: () -> Unit,
    ) {
    CompactRemote(
        viewModel = viewModel,
        isLandscape = isLandscape,
        layoutType = layoutType,
        onOpenSettings = onOpenSettings,
        appSize = appSize,
        onSignInClick = onSignInClick,
    )
}
