package com.xenonware.cloudremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.UUID

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val deviceId = androidId ?: UUID.randomUUID().toString()

            val serviceIntent = Intent(context, CloudRemoteService::class.java).apply {
                putExtra(CloudRemoteService.EXTRA_DEVICE_ID, deviceId)
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
