@file:Suppress("SpellCheckingInspection")

package com.xenonware.cloudremote

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.Identity
import com.xenonware.cloudremote.broadcastReceiver.AdminReceiver
import com.xenonware.cloudremote.data.SharedPreferenceManager
import com.xenonware.cloudremote.helper.MediaNotificationListener
import com.xenonware.cloudremote.presentation.sign_in.GoogleAuthUiClient
import com.xenonware.cloudremote.presentation.sign_in.SignInEvent
import com.xenonware.cloudremote.presentation.sign_in.SignInViewModel
import com.xenonware.cloudremote.service.CloudRemoteService
import com.xenonware.cloudremote.ui.layouts.MainLayout
import com.xenonware.cloudremote.ui.theme.ScreenEnvironment
import com.xenonware.cloudremote.viewmodel.LayoutType
import com.xenonware.cloudremote.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val signInViewModel: SignInViewModel by viewModels {
        SignInViewModel.SignInViewModelFactory(application)
    }

    private lateinit var sharedPreferenceManager: SharedPreferenceManager

    private val googleAuthUiClient by lazy {
        GoogleAuthUiClient(
            context = applicationContext,
            oneTapClient = Identity.getSignInClient(applicationContext)
        )
    }

    private var lastAppliedTheme: Int = -1
    private var lastAppliedCoverThemeEnabled: Boolean = false
    private var lastAppliedBlackedOutMode: Boolean = false

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        sharedPreferenceManager = SharedPreferenceManager(applicationContext)

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceId = androidId ?: UUID.randomUUID().toString()
        viewModel.localDeviceId = deviceId
        viewModel.localDeviceName = Build.MODEL

        checkOverlayPermission()
        checkDoNotDisturbPermission()
        checkDeviceAdminPermission()
        checkNotificationListenerPermission()

        startCloudRemoteService(deviceId)

        val initialThemePref = sharedPreferenceManager.theme
        val initialCoverThemeEnabledSetting = sharedPreferenceManager.coverThemeEnabled
        val initialBlackedOutMode = sharedPreferenceManager.blackedOutModeEnabled

        updateAppCompatDelegateTheme(initialThemePref)

        lastAppliedTheme = initialThemePref
        lastAppliedCoverThemeEnabled = initialCoverThemeEnabledSetting
        lastAppliedBlackedOutMode = initialBlackedOutMode

        setContent {
            val currentContext = LocalContext.current
            val currentContainerSize = LocalWindowInfo.current.containerSize
            val applyCoverTheme = sharedPreferenceManager.isCoverThemeApplied(currentContainerSize)

            LaunchedEffect(Unit) {
                signInViewModel.signInEvent.collect { event ->
                    if (event is SignInEvent.SignedInSuccessfully) {
                        viewModel.onSignedIn()
                    }
                }
            }
            ScreenEnvironment(
                themePreference = lastAppliedTheme,
                coverTheme = applyCoverTheme,
                blackedOutModeEnabled = lastAppliedBlackedOutMode
            ) { layoutType, isLandscape ->

                val oneTapLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult(),
                    onResult = { result ->
                        if (result.resultCode == RESULT_OK) {
                            lifecycleScope.launch {
                                val signInResult = googleAuthUiClient.signInWithIntent(
                                    intent = result.data ?: return@launch
                                )
                                signInViewModel.onSignInResult(signInResult)
                            }
                        }
                    }
                )

                val traditionalSignInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                    onResult = { result ->
                        if (result.resultCode == RESULT_OK) {
                            lifecycleScope.launch {
                                val signInResult = googleAuthUiClient.signInWithTraditionalIntent(
                                    intent = result.data ?: return@launch
                                )
                                signInViewModel.onSignInResult(signInResult)
                            }
                        }
                    }
                )

                XenonApp(
                    viewModel = viewModel,
                    layoutType = layoutType,
                    isLandscape = isLandscape,
                    appSize = currentContainerSize,
                    onOpenSettings = {
                        val intent = Intent(currentContext, SettingsActivity::class.java)
                        currentContext.startActivity(intent)
                    },
                    onSignInClick = {
                        lifecycleScope.launch {
                            val signInResult = googleAuthUiClient.signIn()
                            if (signInResult != null) {
                                oneTapLauncher.launch(IntentSenderRequest.Builder(signInResult.pendingIntent.intentSender).build())
                            } else {
                                traditionalSignInLauncher.launch(googleAuthUiClient.getTraditionalSignInIntent())
                            }
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val user = googleAuthUiClient.getSignedInUser()
            val isSignedIn = user != null

            sharedPreferenceManager.isUserLoggedIn = isSignedIn
            signInViewModel.updateSignInState(isSignedIn)

            if (isSignedIn) {
                viewModel.onSignedIn()
            }
        }

        val currentThemePref = sharedPreferenceManager.theme
        val currentCoverThemeEnabledSetting = sharedPreferenceManager.coverThemeEnabled
        val currentBlackedOutMode = sharedPreferenceManager.blackedOutModeEnabled

        if (currentThemePref != lastAppliedTheme ||
            currentCoverThemeEnabledSetting != lastAppliedCoverThemeEnabled ||
            currentBlackedOutMode != lastAppliedBlackedOutMode
        ) {
            if (currentThemePref != lastAppliedTheme) {
                updateAppCompatDelegateTheme(currentThemePref)
            }

            lastAppliedTheme = currentThemePref
            lastAppliedCoverThemeEnabled = currentCoverThemeEnabledSetting
            lastAppliedBlackedOutMode = currentBlackedOutMode

            recreate()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        var context = newBase
        val prefs = SharedPreferenceManager(newBase)
        val savedTag = prefs.languageTag
        if (savedTag.isNotEmpty() && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locale = Locale.forLanguageTag(savedTag)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            context = newBase.createConfigurationContext(config)
        }
        super.attachBaseContext(ContextWrapper(context))
    }

    private fun updateAppCompatDelegateTheme(themePref: Int) {
        if (themePref >= 0 && themePref < sharedPreferenceManager.themeFlag.size) {
            AppCompatDelegate.setDefaultNightMode(sharedPreferenceManager.themeFlag[themePref])
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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

@Composable
fun XenonApp(
    viewModel: MainViewModel,
    isLandscape: Boolean,
    layoutType: LayoutType,
    onOpenSettings: () -> Unit,
    appSize: IntSize,
    onSignInClick: () -> Unit
    ) {
    MainLayout(
        viewModel = viewModel,
//        isLandscape = isLandscape,
//        layoutType = layoutType,
//        onOpenSettings = onOpenSettings,
        appSize = appSize,
        onSignInClick = onSignInClick,
        )
}
