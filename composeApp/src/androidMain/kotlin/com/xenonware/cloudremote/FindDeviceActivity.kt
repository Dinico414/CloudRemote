package com.xenonware.cloudremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.xenonware.cloudremote.viewmodel.MainViewModel
import com.xenonware.cloudremote.ui.theme.ScreenEnvironment
import com.xenonware.cloudremote.ui.layouts.CompactPingDevice

class FindDeviceActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPreferenceManager = com.xenonware.cloudremote.data.SharedPreferenceManager(applicationContext)
        viewModel.localDeviceId = sharedPreferenceManager.localDeviceId

        val themePref = sharedPreferenceManager.theme
        val blackedOutMode = sharedPreferenceManager.blackedOutModeEnabled

        setContent {
            ScreenEnvironment(
                themePreference = themePref,
                coverTheme = false,
                blackedOutModeEnabled = blackedOutMode
            ) { layoutType, _ ->
                CompactPingDevice(
                    viewModel = viewModel,
                    layoutType = layoutType,
                    onBack = { finish() }
                )
            }
        }
    }
}
