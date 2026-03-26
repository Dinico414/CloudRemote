package com.xenonware.cloudremote

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xenon.mylibrary.theme.QuicksandTitleVariable
import com.xenonware.cloudremote.broadcastReceiver.AdminReceiver
import com.xenonware.cloudremote.data.SharedPreferenceManager
import com.xenonware.cloudremote.ui.res.AnimatedGradientBackground
import com.xenonware.cloudremote.ui.theme.XenonTheme

class PermissionActivity : ComponentActivity() {

    private lateinit var sharedPreferenceManager: SharedPreferenceManager
    private lateinit var requiredPermissions: List<Permission>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requiredPermissions = listOf(
            Permission(
                name = getString(R.string.display_over_other_apps),
                description = getString(R.string.display_over_other_apps_description),
                isGranted = { Settings.canDrawOverlays(this) },
                request = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()
                    )
                    startActivity(intent)
                }), Permission(
                name = getString(R.string.do_not_disturb_access),
                description = getString(R.string.do_not_disturb_access_description),
                isGranted = {
                    val notificationManager =
                        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.isNotificationPolicyAccessGranted
                },
                request = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    startActivity(intent)
                }), Permission(
                name = getString(R.string.notification_access),
                description = getString(R.string.notification_access_description),
                isGranted = {
                    val enabledListeners =
                        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                    enabledListeners?.contains(packageName) == true
                },
                request = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    startActivity(intent)
                }), Permission(
                name = getString(R.string.device_admin),
                description = getString(R.string.device_admin_description),
                isGranted = {
                    val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val componentName = ComponentName(this, AdminReceiver::class.java)
                    dpm.isAdminActive(componentName)
                },
                request = {
                    val componentName = ComponentName(this, AdminReceiver::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Cloud Remote needs this permission to lock the screen remotely."
                        )
                    }
                    startActivity(intent)
                })
        )

        enableEdgeToEdge()
        sharedPreferenceManager = SharedPreferenceManager(this)

        setContent {
            XenonTheme(darkTheme = isSystemInDarkTheme()) {
                AnimatedGradientBackground {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        PermissionScreen(
                            permissions = requiredPermissions,
                            isFirstLaunch = sharedPreferenceManager.isFirstLaunch,
                            onFinish = {
                                if (sharedPreferenceManager.isFirstLaunch) {
                                    startActivity(Intent(this, WelcomeActivity::class.java))
                                } else {
                                    startActivity(Intent(this, MainActivity::class.java))
                                }
                                finish()
                            })
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(permissions: List<Permission>, isFirstLaunch: Boolean, onFinish: () -> Unit) {
    var currentPermissionIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    // Find the first permission that is not granted
    LaunchedEffect(Unit) {
        val firstUngranted = permissions.indexOfFirst { !it.isGranted(context) }
        if (firstUngranted != -1) {
            currentPermissionIndex = firstUngranted
        } else {
            onFinish()
        }
    }

    val currentPermission = permissions.getOrNull(currentPermissionIndex)

    if (currentPermission == null) {
        LaunchedEffect(Unit) {
            onFinish()
        }
        return
    }

    var isPermissionGranted by remember(currentPermissionIndex) {
        mutableStateOf(currentPermission.isGranted(context))
    }

    // Observe lifecycle events to re-check permission status when returning to the app
    DisposableEffect(currentPermissionIndex) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = currentPermission.isGranted(context)
                
                // Automatically move to the next screen if the permission was granted via system settings
                if (isPermissionGranted) {
                    val nextPermissionIndex = permissions.indexOfFirst { !it.isGranted(context) }
                    when {
                        nextPermissionIndex != -1 -> {
                            currentPermissionIndex = nextPermissionIndex
                        }
                        else -> {
                            onFinish()
                        }
                    }
                }
            }
        }
        (context as ComponentActivity).lifecycle.addObserver(lifecycleObserver)
        onDispose {
            context.lifecycle.removeObserver(lifecycleObserver)
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentPermission.name,
                style = MaterialTheme.typography.headlineLarge.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.25f),
                        offset = Offset(x = 2f, y = 4f),
                        blurRadius = 8f
                    )
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontFamily = QuicksandTitleVariable,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = currentPermission.description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = Offset(x = 1f, y = 2f),
                        blurRadius = 2f
                    )
                ), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = {
                if (isPermissionGranted) {
                    val nextPermissionIndex = permissions.indexOfFirst { !it.isGranted(context) }
                    when {
                        nextPermissionIndex != -1 -> {
                            currentPermissionIndex = nextPermissionIndex
                        }
                        else -> {
                            onFinish()
                        }
                    }
                } else {
                    currentPermission.request(context)
                }
            }, colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ), modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(96.dp)
        ) {
            val allPermissionsGranted = permissions.all { it.isGranted(context) }
            Text(
                text = when {
                    allPermissionsGranted && !isFirstLaunch -> stringResource(R.string.finish)
                    isPermissionGranted && !allPermissionsGranted -> "Next"
                    else -> stringResource(R.string.grant_permission)
                },
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = QuicksandTitleVariable,
            )
        }
    }
}

data class Permission(
    val name: String,
    val description: String,
    val isGranted: (Context) -> Boolean,
    val request: (Context) -> Unit
)
