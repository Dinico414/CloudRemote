package com.xenonware.cloudremote.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.gson.Gson
import com.xenonware.cloudremote.MainActivity
import com.xenonware.cloudremote.R
import com.xenonware.cloudremote.data.BTDeviceType
import com.xenonware.cloudremote.data.ConnectedDevice
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.data.SharedPreferenceManager
import com.xenonware.cloudremote.helper.LocalDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConnectedDevicesWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(56.dp, 40.dp), DpSize(56.dp, 60.dp), DpSize(56.dp, 80.dp),
            DpSize(56.dp, 100.dp), DpSize(56.dp, 120.dp), DpSize(56.dp, 150.dp),
            DpSize(56.dp, 180.dp), DpSize(56.dp, 210.dp), DpSize(56.dp, 250.dp),
            DpSize(56.dp, 300.dp), DpSize(56.dp, 350.dp), DpSize(56.dp, 400.dp),
            DpSize(56.dp, 500.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DATA, null)
        val data = if (json != null) parseData(json) else null

        val localDeviceManager = LocalDeviceManager(context)
        val deviceName = Build.MODEL
        val batteryLevel = localDeviceManager.getBatteryLevel()
        val isCharging = localDeviceManager.isCharging()

        val connectedDevices = try {
            val state = localDeviceManager.getCurrentStateSnapshot()
            state.connectedDevices
        } catch (_: Exception) {
            data?.connectedDevices ?: emptyList()
        }

        // Also fetch cloud devices from the battery widget's cache to stay in sync
        val batteryPrefs = context.getSharedPreferences(BatteryWidget.PREFS_NAME, Context.MODE_PRIVATE)
        val cloudDevicesJson = batteryPrefs.getString(BatteryWidget.KEY_DEVICES, null)
        val cloudDevices = if (cloudDevicesJson != null) {
            BatteryWidget.parseDevicesJson(cloudDevicesJson)
        } else {
            emptyList()
        }

        val localDeviceId = SharedPreferenceManager(context).localDeviceId
        val filteredCloudDevices = cloudDevices.filter { it.id != localDeviceId }

        val widgetData = WidgetData(
            localName = deviceName,
            localBattery = batteryLevel,
            localCharging = isCharging,
            connectedDevices = connectedDevices,
            cloudDevices = filteredCloudDevices
        )

        prefs.edit(commit = true) {
            putString(KEY_DATA, Gson().toJson(widgetData))
        }

        provideContent {
            GlanceTheme {
                WidgetContent(context, widgetData)
            }
        }
    }

    data class WidgetData(
        val localName: String = "",
        val localBattery: Int = 0,
        val localCharging: Boolean = false,
        val connectedDevices: List<ConnectedDevice> = emptyList(),
        val cloudDevices: List<Device> = emptyList()
    )

    data class BatteryItem(
        val name: String,
        val battery: Int,
        val isCharging: Boolean,
        val isLocal: Boolean,
        val type: BTDeviceType?,
        val isCloud: Boolean = false,
        val icon: String? = null
    )

    @Composable
    private fun WidgetContent(context: Context, data: WidgetData) {
        val allItems = buildList {
            if (data.localBattery != -1) {
                add(BatteryItem(data.localName, data.localBattery, data.localCharging, isLocal = true, type = null))
            }
            data.connectedDevices.forEach {
                if (it.batteryLevel != -1) {
                    add(BatteryItem(it.name, it.batteryLevel, isCharging = false, isLocal = false, type = it.type))
                }
            }
        }

        // The spacer here breaks the identical UI matching bug in Glance, preventing layout swapping
        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp)
                .background(GlanceTheme.colors.widgetBackground).padding(8.dp)
        ) {
            Spacer(modifier = GlanceModifier.width(1.dp).height(0.dp))

            val spacingDp = 2.dp
            val widgetHeight = LocalSize.current.height.value
            val availableHeight = widgetHeight - 16f
            val totalSpacing = spacingDp.value * (allItems.size - 1)
            val heightPerItem = (availableHeight - totalSpacing) / allItems.size
            val tallLayout = heightPerItem >= 95f
            val needsScroll = heightPerItem < 24f

            if (!needsScroll) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    allItems.forEachIndexed { index, item ->
                        Box(
                            modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                                .cornerRadius(16.dp)
                                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                        ) {
                            DeviceBatteryBar(context, item, tallLayout)
                        }
                        if (index < allItems.lastIndex) {
                            Spacer(modifier = GlanceModifier.height(spacingDp))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(allItems) { item ->
                        Box(
                            modifier = GlanceModifier.fillMaxWidth().height(44.dp)
                                .padding(bottom = spacingDp)
                                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                        ) {
                            DeviceBatteryBar(context, item, false)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DeviceBatteryBar(context: Context, item: BatteryItem, tallLayout: Boolean) {
        val palette = getPalette(context)
        val bgColor: ColorProvider
        val fgColor: ColorProvider
        val textColor: ColorProvider

        if (item.battery <= 20) {
            bgColor = ColorProvider(day = palette.lowSurfaceDay, night = palette.lowSurfaceNight)
            fgColor = ColorProvider(day = palette.lowContainerDay, night = palette.lowContainerNight)
            textColor = ColorProvider(day = palette.lowDay, night = palette.lowNight)
        } else if (item.isLocal) {
            bgColor = ColorProvider(day = palette.primarySurfaceDay, night = palette.primarySurfaceNight)
            fgColor = ColorProvider(day = palette.primaryContainerDay, night = palette.primaryContainerNight)
            textColor = ColorProvider(day = palette.primaryDay, night = palette.primaryNight)
        } else {
            bgColor = ColorProvider(day = palette.tertiarySurfaceDay, night = palette.tertiarySurfaceNight)
            fgColor = ColorProvider(day = palette.tertiaryContainerDay, night = palette.tertiaryContainerNight)
            textColor = ColorProvider(day = palette.tertiaryDay, night = palette.tertiaryNight)
        }

        val contentColor = if (item.battery > 20) textColor else GlanceTheme.colors.onSurface

        val typeIconRes = when {
            item.isLocal -> {
                val name = item.name
                when {
                    name.contains("Surface Duo", ignoreCase = true) -> R.drawable.rounded_dual_screen_24
                    name.contains("Flip", ignoreCase = true) -> R.drawable.rounded_devices_flip_24
                    name.contains("Fold", ignoreCase = true) -> R.drawable.rounded_devices_fold_24
                    name.contains("Tablet", ignoreCase = true) -> R.drawable.rounded_tablet_24
                    name.contains("TV", ignoreCase = true) -> R.drawable.rounded_tv_gen_24
                    name.contains("LG Wing", ignoreCase = true) || name.contains("iKKO Mind One", ignoreCase = true) ||
                            name.contains("Clicks Communicator", ignoreCase = true) || name.contains("Keyboard Phone", ignoreCase = true) -> R.drawable.round_phone_android_24
                    else -> R.drawable.round_phone_android_24
                }
            }
            item.type != null -> {
                when (item.type) {
                    BTDeviceType.HEADSET, BTDeviceType.EARBUDS -> R.drawable.round_headphones_24
                    BTDeviceType.WATCH -> R.drawable.round_watch_24
                    BTDeviceType.KEYBOARD -> R.drawable.round_keyboard_24
                    BTDeviceType.MOUSE -> R.drawable.round_mouse_24
                    BTDeviceType.SPEAKER -> R.drawable.round_speaker_24
                    BTDeviceType.CONTROLLER -> R.drawable.round_gamepad_24
                    BTDeviceType.PEN -> R.drawable.round_edit_24
                    BTDeviceType.HEARING_AID -> R.drawable.round_hearing_24
                    BTDeviceType.PHONE -> R.drawable.round_phone_android_24
                    BTDeviceType.COMPUTER, BTDeviceType.LAPTOP -> R.drawable.round_computer_24
                    BTDeviceType.TV -> R.drawable.round_tv_24
                    else -> R.drawable.round_bluetooth_24
                }
            }
            else -> R.drawable.round_bluetooth_24
        }

        Box(
            modifier = GlanceModifier.fillMaxWidth().fillMaxHeight().cornerRadius(16.dp).background(bgColor)
        ) {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.CenterStart
            ) {
                LinearProgressIndicator(
                    progress = item.battery.coerceIn(0, 100) / 100f,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp),
                    color = fgColor, backgroundColor = bgColor
                )
            }

            if (tallLayout) {
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.Vertical.Top
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(typeIconRes), contentDescription = null,
                            modifier = GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(contentColor)
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = item.name, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = contentColor), maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Row(
                        modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "${item.battery}%", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = contentColor))
                        if (item.isCharging) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center, modifier = GlanceModifier.size(20.dp).background(textColor).cornerRadius(14.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.round_bolt_24), contentDescription = "Charging",
                                    modifier = GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(fgColor)
                                )
                            }
                        }
                        if (!item.isCharging && item.battery <= 20) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center, modifier = GlanceModifier.size(20.dp).background(fgColor).cornerRadius(14.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.round_battery_alert_24), contentDescription = "Low battery",
                                    modifier = GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(textColor)
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(typeIconRes), contentDescription = null,
                        modifier = GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(contentColor)
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = item.name, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = contentColor),
                        maxLines = 1, modifier = GlanceModifier.defaultWeight()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "${item.battery}%", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = contentColor))
                        if (item.isCharging) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center, modifier = GlanceModifier.size(20.dp).background(textColor).cornerRadius(14.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.round_bolt_24), contentDescription = "Charging",
                                    modifier = GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(fgColor)
                                )
                            }
                        }
                        if (!item.isCharging && item.battery <= 20) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center, modifier = GlanceModifier.size(20.dp).background(fgColor).cornerRadius(14.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.round_battery_alert_24), contentDescription = "Low battery",
                                    modifier = GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(textColor)
                                )
                            }
                        }
                        Spacer(modifier = GlanceModifier.width(4.dp))
                    }
                }
            }
        }
    }

    private fun getPalette(context: Context): ColorPalette {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ColorPalette(
                primaryDay = Color(context.getColor(android.R.color.system_accent1_800)),
                primaryNight = Color(context.getColor(android.R.color.system_accent1_200)),
                primaryContainerDay = Color(context.getColor(android.R.color.system_accent1_300)),
                primaryContainerNight = Color(context.getColor(android.R.color.system_accent1_700)),
                primarySurfaceDay = Color(context.getColor(android.R.color.system_accent1_100)),
                primarySurfaceNight = Color(context.getColor(android.R.color.system_accent1_900)),
                tertiaryDay = Color(context.getColor(android.R.color.system_accent3_800)),
                tertiaryNight = Color(context.getColor(android.R.color.system_accent3_200)),
                tertiaryContainerDay = Color(context.getColor(android.R.color.system_accent3_300)),
                tertiaryContainerNight = Color(context.getColor(android.R.color.system_accent3_700)),
                tertiarySurfaceDay = Color(context.getColor(android.R.color.system_accent3_100)),
                tertiarySurfaceNight = Color(context.getColor(android.R.color.system_accent3_900)),
            )
        } else {
            ColorPalette(
                primaryDay = Color(0xff1b2f59),
                primaryNight = Color(0xffb3c6f9),
                primaryContainerDay = Color(0xff98aadc),
                primaryContainerNight = Color(0xff32456f),
                primarySurfaceDay = Color(0xffd9e2ff),
                primarySurfaceNight = Color(0xff021943),
                tertiaryDay = Color(0xff3a284e),
                tertiaryNight = Color(0xffd5bdec),
                tertiaryContainerDay = Color(0xffb9a2d0),
                tertiaryContainerNight = Color(0xff513f66),
                tertiarySurfaceDay = Color(0xffefdbff),
                tertiarySurfaceNight = Color(0xff241338),
            )
        }
    }

    companion object {
        const val PREFS_NAME = "connected_devices_widget_prefs"
        const val KEY_DATA = "widget_data"

        fun parseData(json: String): WidgetData? {
            return try {
                Gson().fromJson(json, WidgetData::class.java)
            } catch (_: Exception) {
                null
            }
        }
    }
}

class ConnectedDevicesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ConnectedDevicesWidget()

    companion object {
        const val ACTION_UPDATE = "com.xenonware.cloudremote.ACTION_UPDATE_CONNECTED_WIDGET"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE || intent.action == "android.appwidget.action.APPWIDGET_UPDATE") {
            val manager = GlanceAppWidgetManager(context)
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    manager.getGlanceIds(ConnectedDevicesWidget::class.java).forEach { id ->
                        ConnectedDevicesWidget().update(context, id)
                    }
                } catch (e: Exception) {
                    Log.e("ConnectedDevicesWidget", "Update failed", e)
                }
            }
        }
    }
}