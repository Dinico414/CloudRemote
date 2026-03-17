package com.xenonware.cloudremote.service

import android.content.Intent
import android.widget.RemoteViewsService
import com.xenonware.cloudremote.viewmodel.BatteryWidgetRemoteViewsFactory
import com.xenonware.cloudremote.R

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val layoutId = intent.getIntExtra("layout_id", R.layout.battery_widget_list_item)
        return BatteryWidgetRemoteViewsFactory(this.applicationContext, intent, layoutId)
    }
}