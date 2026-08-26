package com.xenonware.cloudremote

import android.Manifest
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.xenon.mylibrary.activity.BasePermissionActivity
import com.xenon.mylibrary.utils.PermissionItem
import com.xenonware.cloudremote.broadcastReceiver.AdminReceiver
import com.xenonware.cloudremote.data.SharedPreferenceManager

class PermissionActivity : BasePermissionActivity() {

    private val sharedPreferenceManager by lazy { SharedPreferenceManager(this) }

    override fun isFirstLaunch(): Boolean = sharedPreferenceManager.isFirstLaunch

    override fun getPermissions(): List<PermissionItem> = buildList {
        add(PermissionItem(
            name = getString(R.string.display_over_other_apps),
            description = getString(R.string.display_over_other_apps_description),
            isGranted = { Settings.canDrawOverlays(it) },
            request = {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
            }
        ))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(PermissionItem(
                name = getString(R.string.bluetooth_access),
                description = getString(R.string.bluetooth_access_description),
                isGranted = { ContextCompat.checkSelfPermission(it, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED },
                request = { requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101) }
            ))
        }

        add(PermissionItem(
            name = getString(R.string.do_not_disturb_access),
            description = getString(R.string.do_not_disturb_access_description),
            isGranted = { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted },
            request = { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        ))

        add(PermissionItem(
            name = getString(R.string.notification_access),
            description = getString(R.string.notification_access_description),
            isGranted = {
                val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                enabled?.contains(packageName) == true
            },
            request = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        ))

        add(PermissionItem(
            name = getString(R.string.device_admin),
            description = getString(R.string.device_admin_description),
            isGranted = {
                val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.isAdminActive(ComponentName(it, AdminReceiver::class.java))
            },
            request = {
                val component = ComponentName(it, AdminReceiver::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Cloud Remote needs this permission to lock the screen remotely.")
                }
                startActivity(intent)
            }
        ))
    }

    override fun onPermissionsFinished() {
        if (sharedPreferenceManager.isFirstLaunch) {
            startActivity(Intent(this, WelcomeActivity::class.java))
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
