package com.xenonware.cloudremote.ui.layouts

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize
import com.xenonware.cloudremote.ui.layouts.main.CompactRemote
import com.xenonware.cloudremote.viewmodel.MainViewModel

@Composable
fun MainLayout(
    viewModel: MainViewModel,
    appSize: IntSize,
    onSignInClick: () -> Unit,
    ) {
    CompactRemote(
        viewModel = viewModel,
        appSize = appSize,
        onSignInClick = onSignInClick,
    )
}
