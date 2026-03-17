package com.xenonware.cloudremote.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.google.gson.Gson
import com.xenonware.cloudremote.R
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.service.WidgetService
import com.xenonware.cloudremote.viewmodel.BatteryWidgetRemoteViewsFactory

class BatteryWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.xenonware.cloudremote.ACTION_UPDATE_WIDGET"
        const val EXTRA_DEVICES_JSON = "EXTRA_DEVICES_JSON"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)

        val layoutId = if (minWidth < 200) {
            R.layout.battery_widget_list_item_small
        } else {
            R.layout.battery_widget_list_item
        }

        val intent = Intent(context, WidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("layout_id", layoutId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }

        val views = RemoteViews(context.packageName, R.layout.battery_widget_layout).apply {
            setRemoteAdapter(R.id.devices_list, intent)
            setEmptyView(R.id.devices_list, R.id.widget_title)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val devicesJson = intent.getStringExtra(EXTRA_DEVICES_JSON)
            if (devicesJson != null) {
                val devices = Gson().fromJson(devicesJson, Array<Device>::class.java).toList()
                BatteryWidgetRemoteViewsFactory.Companion.devices = devices
            }

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BatteryWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.devices_list)
        }
        super.onReceive(context, intent)
    }
}