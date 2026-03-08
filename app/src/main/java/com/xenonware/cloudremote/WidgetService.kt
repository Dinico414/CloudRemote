package com.xenonware.cloudremote

import android.content.Intent
import android.widget.RemoteViewsService

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val layoutId = intent.getIntExtra("layout_id", R.layout.battery_widget_list_item)
        return BatteryWidgetRemoteViewsFactory(this.applicationContext, intent, layoutId)
    }
}
