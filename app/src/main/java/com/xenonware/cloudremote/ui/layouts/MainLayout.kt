package com.xenonware.cloudremote.ui.layouts

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize
import com.xenonware.cloudremote.viewmodel.LayoutType
import com.xenonware.cloudremote.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainLayout(
    viewModel: MainViewModel,
    layoutType: LayoutType,
    onOpenSettings: () -> Unit,
    appSize: IntSize,
) {

//    when (layoutType) {
//        LayoutType.COVER -> {
//            CoverRemote(
//                viewModel = viewModel,
//                onOpenSettings = onOpenSettings,
//            )
//        }
//
//        LayoutType.SMALL, LayoutType.COMPACT, LayoutType.MEDIUM, LayoutType.EXPANDED -> {
//
//            CompactRemote(
//                viewModel = viewModel,
//                layoutType = layoutType,
//                onOpenSettings = onOpenSettings,
//                appSize = appSize
//            )
//        }
//    }
}
