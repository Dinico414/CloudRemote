package com.xenonware.cloudremote

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.xenonware.cloudremote.data.Device

class BatteryWidgetRemoteViewsFactory(
    private val context: Context,
    intent: Intent,
    private val layoutId: Int
) : RemoteViewsService.RemoteViewsFactory {

    companion object {
        var devices = emptyList<Device>()
    }

    override fun onCreate() {
        // Data is now stored in a static variable
    }

    override fun onDataSetChanged() {
        // Data is updated externally
    }

    override fun onDestroy() {
        // Static data will persist
    }

    override fun getCount(): Int {
        return devices.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        val device = devices[position]
        val views = RemoteViews(context.packageName, layoutId)
        views.setTextViewText(R.id.device_name, device.name)
        views.setProgressBar(R.id.battery_progress, 100, device.batteryLevel, false)
        views.setTextViewText(R.id.battery_level_text, "${device.batteryLevel}%${if (device.isCharging) " ⚡" else ""}")
        return views
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 2 // We now have two possible layouts
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }
}
