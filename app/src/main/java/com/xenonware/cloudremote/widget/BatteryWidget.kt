package com.xenonware.cloudremote.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
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
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xenonware.cloudremote.MainActivity
import com.xenonware.cloudremote.R
import com.xenonware.cloudremote.data.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BatteryWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(56.dp, 40.dp),
            DpSize(56.dp, 60.dp),
            DpSize(56.dp, 80.dp),
            DpSize(56.dp, 100.dp),
            DpSize(56.dp, 120.dp),
            DpSize(56.dp, 150.dp),
            DpSize(56.dp, 180.dp),
            DpSize(56.dp, 210.dp),
            DpSize(56.dp, 250.dp),
            DpSize(56.dp, 300.dp),
            DpSize(56.dp, 350.dp),
            DpSize(56.dp, 400.dp),
            DpSize(56.dp, 500.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var devicesJson = prefs.getString(KEY_DEVICES, null)

        if (devicesJson == null || devicesJson == "[]") {
            val fetched = fetchDevicesFromFirestore()
            if (fetched != null) {
                devicesJson = devicesToLightJson(fetched)
                prefs.edit { putString(KEY_DEVICES, devicesJson) }
            } else {
                devicesJson = "[]"
            }
        }

        val localDeviceId =
            com.xenonware.cloudremote.data.SharedPreferenceManager(context).localDeviceId
        val now = System.currentTimeMillis()
        val devices = parseDevicesJson(devicesJson)
            .filter { (now - it.lastUpdated) < 3_600_000 }
            .sortedByDescending { it.id == localDeviceId }

        provideContent {
            GlanceTheme {
                WidgetContent(context, devices, localDeviceId)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, devices: List<Device>, localDeviceId: String) {
        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp)
                .background(GlanceTheme.colors.widgetBackground).padding(8.dp)
        ) {
            if (devices.isEmpty()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = "Cloud Remote", style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = GlanceTheme.colors.onSurface
                            )
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(4.dp))
                Box(
                    modifier = GlanceModifier.fillMaxSize()
                        .clickable(actionRunCallback<RefreshAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No devices online.\nTap to refresh.", style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    )
                }
            } else {
                val spacingDp = 4.dp
                val widgetHeight = LocalSize.current.height.value
                val availableHeight = widgetHeight - 16f
                val totalSpacing = spacingDp.value * (devices.size - 1)
                val heightPerItem = (availableHeight - totalSpacing) / devices.size
                val tallLayout = heightPerItem >= 100f
                // Only scroll when items would be unreasonably tiny
                val needsScroll = heightPerItem < 24f

                if (!needsScroll) {
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        devices.forEachIndexed { index, device ->
                            Box(
                                modifier = GlanceModifier.fillMaxWidth()
                                    .defaultWeight()
                            ) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    DeviceItem(
                                        context, device, device.id == localDeviceId, tallLayout
                                    )
                                }
                            }
                            if (index < devices.lastIndex) {
                                Spacer(modifier = GlanceModifier.height(spacingDp))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(devices) { device ->
                            Box(
                                modifier = GlanceModifier.fillMaxWidth()
                                    .height(38.dp)
                                    .padding(bottom = spacingDp)
                            ) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    DeviceItem(context, device, device.id == localDeviceId, false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Composable
    private fun DeviceItem(
        context: Context, device: Device, isLocalDevice: Boolean, tallLayout: Boolean
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth().fillMaxHeight()
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            BatteryIndicator(
                batteryLevel = device.batteryLevel,
                isCharging = device.isCharging,
                isLocalDevice = isLocalDevice,
                deviceName = device.name,
                tallLayout = tallLayout
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("RestrictedApi")
    @Composable
    private fun BatteryIndicator(
        batteryLevel: Int,
        isCharging: Boolean,
        isLocalDevice: Boolean,
        deviceName: String,
        tallLayout: Boolean
    ) {
        val context = LocalContext.current

        val primaryDay = Color(context.getColor(android.R.color.system_accent1_800))
        val primaryNight = Color(context.getColor(android.R.color.system_accent1_200))
        val primaryContainerDay = Color(context.getColor(android.R.color.system_accent1_300))
        val primaryContainerNight = Color(context.getColor(android.R.color.system_accent1_700))
        val primarySurfaceDay = Color(context.getColor(android.R.color.system_accent1_100))
        val primarySurfaceNight = Color(context.getColor(android.R.color.system_accent1_900))
        val tertiaryDay = Color(context.getColor(android.R.color.system_accent3_800))
        val tertiaryNight = Color(context.getColor(android.R.color.system_accent3_200))
        val tertiaryContainerDay = Color(context.getColor(android.R.color.system_accent3_300))
        val tertiaryContainerNight = Color(context.getColor(android.R.color.system_accent3_700))
        val tertiarySurfaceDay = Color(context.getColor(android.R.color.system_accent3_100))
        val tertiarySurfaceNight = Color(context.getColor(android.R.color.system_accent3_900))

        val bgColor: ColorProvider
        val fgColor: ColorProvider
        val textColor: ColorProvider

        when {
            isLocalDevice -> {
                bgColor = androidx.glance.color.ColorProvider(
                    day = primarySurfaceDay, night = primarySurfaceNight
                )
                fgColor = androidx.glance.color.ColorProvider(
                    day = primaryContainerDay, night = primaryContainerNight
                )
                textColor = androidx.glance.color.ColorProvider(
                    day = primaryDay, night = primaryNight
                )
            }

            batteryLevel <= 20 -> {
                bgColor = GlanceTheme.colors.surface
                fgColor = GlanceTheme.colors.errorContainer
                textColor = GlanceTheme.colors.error
            }

            else -> {
                bgColor = androidx.glance.color.ColorProvider(
                    day = tertiarySurfaceDay, night = tertiarySurfaceNight
                )
                fgColor = androidx.glance.color.ColorProvider(
                    day = tertiaryContainerDay, night = tertiaryContainerNight
                )
                textColor = androidx.glance.color.ColorProvider(
                    day = tertiaryDay, night = tertiaryNight
                )
            }
        }

        val contentColor = if (batteryLevel > 20) textColor else GlanceTheme.colors.onSurface

        Box(
            modifier = GlanceModifier.fillMaxWidth().fillMaxHeight().cornerRadius(19.dp)
                .background(bgColor)
        ) {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                LinearProgressIndicator(
                    progress = batteryLevel.coerceIn(0, 100) / 100f,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(15.dp),
                    color = fgColor,
                    backgroundColor = bgColor
                )
            }

            if (tallLayout) {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.Vertical.Top
                ) {
                    Text(
                        text = deviceName,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        ),
                        maxLines = 1
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$batteryLevel%",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        )
                        if (isCharging) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = GlanceModifier.size(20.dp)
                                    .background(textColor).cornerRadius(14.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.round_bolt_24),
                                    contentDescription = "Charging",
                                    modifier = GlanceModifier.size(14.dp),
                                    colorFilter = ColorFilter.tint(fgColor)
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = deviceName,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$batteryLevel%",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        )
                        if (isCharging) {
                            Spacer(modifier = GlanceModifier.width(12.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = GlanceModifier.size(20.dp)
                                    .background(textColor).cornerRadius(14.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.round_bolt_24),
                                    contentDescription = "Charging",
                                    modifier = GlanceModifier.size(14.dp),
                                    colorFilter = ColorFilter.tint(fgColor)
                                )
                            }
                        }
                        Spacer(modifier = GlanceModifier.width(4.dp))
                    }
                }
            }
        }
    }

    companion object {
        const val PREFS_NAME = "battery_widget_prefs"
        const val KEY_DEVICES = "devices_json"
        const val KEY_LAST_UPDATE = "last_update"

        fun devicesToLightJson(devices: List<Device>): String {
            val light = devices.map { it.copy(mediaAlbumArt = "") }
            return Gson().toJson(light)
        }

        fun parseDevicesJson(json: String): List<Device> {
            return try {
                val type = object : TypeToken<List<Device>>() {}.type
                Gson().fromJson(json, type)
            } catch (_: Exception) {
                emptyList()
            }
        }

        private suspend fun fetchDevicesFromFirestore(): List<Device>? {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
            return try {
                FirebaseFirestore.getInstance().collection("users").document(userId)
                    .collection("devices").get().await().toObjects(Device::class.java)
            } catch (_: Exception) {
                null
            }
        }

        fun updateCache(context: Context, devicesJson: String) {
            val lightJson = try {
                val type = object : TypeToken<List<Device>>() {}.type
                val devices: List<Device> = Gson().fromJson(devicesJson, type)
                devicesToLightJson(devices)
            } catch (_: Exception) {
                return
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
                putString(KEY_DEVICES, lightJson).putLong(
                    KEY_LAST_UPDATE, System.currentTimeMillis()
                )
            }
        }

        suspend fun refreshFromFirestore(context: Context) {
            val devices = fetchDevicesFromFirestore() ?: return
            val lightJson = devicesToLightJson(devices)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
                putString(KEY_DEVICES, lightJson).putLong(
                    KEY_LAST_UPDATE, System.currentTimeMillis()
                )
            }
            BatteryWidget().updateAll(context)
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context, glanceId: GlanceId, parameters: ActionParameters
    ) {
        try {
            BatteryWidget.refreshFromFirestore(context)
        } catch (e: Exception) {
            Log.e("BatteryWidget", "RefreshAction FAILED", e)
        }
    }
}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.xenonware.cloudremote.ACTION_UPDATE_WIDGET"
        const val EXTRA_DEVICES_JSON = "EXTRA_DEVICES_JSON"
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CoroutineScope(Dispatchers.IO).launch {
            BatteryWidget.refreshFromFirestore(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_UPDATE_WIDGET -> {
                val rawJson = intent.getStringExtra(EXTRA_DEVICES_JSON) ?: return
                BatteryWidget.updateCache(context, rawJson)
                CoroutineScope(Dispatchers.IO).launch {
                    BatteryWidget().updateAll(context)
                }
            }

            "android.appwidget.action.APPWIDGET_UPDATE" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    BatteryWidget.refreshFromFirestore(context)
                }
            }
        }
    }
}