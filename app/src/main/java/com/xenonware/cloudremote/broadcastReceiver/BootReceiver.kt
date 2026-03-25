package com.xenonware.cloudremote.broadcastReceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xenonware.cloudremote.data.SharedPreferenceManager
import com.xenonware.cloudremote.service.CloudRemoteService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            val sharedPrefs = SharedPreferenceManager(context)
            val deviceId = sharedPrefs.localDeviceId

            val serviceIntent = Intent(context, CloudRemoteService::class.java).apply {
                putExtra(CloudRemoteService.EXTRA_DEVICE_ID, deviceId)
            }
            
            try {
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start foreground service", e)
            }
        }
    }
}