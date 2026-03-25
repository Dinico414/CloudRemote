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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xenonware.cloudremote.broadcastReceiver.AdminReceiver
import com.xenonware.cloudremote.data.SharedPreferenceManager
import com.xenonware.cloudremote.ui.theme.XenonTheme

class PermissionActivity : ComponentActivity() {

    private lateinit var sharedPreferenceManager: SharedPreferenceManager
    private val requiredPermissions = listOf(
        Permission(
            name = "Display over other apps",
            description = "This permission is required for the curtain feature to work. It allows the app to draw over other apps.",
            isGranted = { Settings.canDrawOverlays(this) },
            request = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                startActivity(intent)
            }
        ),
        Permission(
            name = "Do Not Disturb access",
            description = "This permission is needed to control the Do Not Disturb mode on your device.",
            isGranted = {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.isNotificationPolicyAccessGranted
            },
            request = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivity(intent)
            }
        ),
        Permission(
            name = "Notification access",
            description = "This permission is required to read media notifications and display them in the app.",
            isGranted = {
                val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                enabledListeners?.contains(packageName) == true
            },
            request = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
            }
        ),
        Permission(
            name = "Device Admin",
            description = "Cloud Remote needs this permission to lock the screen remotely.",
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
            }
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedPreferenceManager = SharedPreferenceManager(this)

        setContent {
            XenonTheme(darkTheme = isSystemInDarkTheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionScreen(
                        permissions = requiredPermissions,
                        isFirstLaunch = sharedPreferenceManager.isFirstLaunch,
                        onFinish = {
                            if (sharedPreferenceManager.isFirstLaunch) {
                                startActivity(Intent(this, FirstLaunchActivity::class.java))
                            } else {
                                startActivity(Intent(this, MainActivity::class.java))
                            }
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(permissions: List<Permission>, isFirstLaunch: Boolean, onFinish: () -> Unit) {
    var currentPermissionIndex by remember { mutableStateOf(0) }
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
            }
        }
        (context as ComponentActivity).lifecycle.addObserver(lifecycleObserver)
        onDispose {
            (context as ComponentActivity).lifecycle.removeObserver(lifecycleObserver)
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = currentPermission.name,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = currentPermission.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                if (isPermissionGranted) {
                    val nextPermissionIndex = permissions.indexOfFirst { !it.isGranted(context) }
                    if (nextPermissionIndex != -1) {
                        currentPermissionIndex = nextPermissionIndex
                    } else {
                        onFinish()
                    }
                } else {
                    currentPermission.request(context)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            val allPermissionsGranted = permissions.all { it.isGranted(context) }
            Text(
                text = when {
                    isPermissionGranted && !allPermissionsGranted -> "Next"
                    allPermissionsGranted && isFirstLaunch -> "Next"
                    allPermissionsGranted && !isFirstLaunch -> "Finish"
                    else -> "Grant Permission"
                }
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
