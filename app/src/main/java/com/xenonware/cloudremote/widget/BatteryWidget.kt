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

/**
 * Holds all color variables for the battery widget, allowing a single
 * palette swap between dynamic Material You colors and a static fallback.
 */
data class ColorPalette(
    // Primary (used for local device)
    val primaryDay: Color,
    val primaryNight: Color,
    val primaryContainerDay: Color,
    val primaryContainerNight: Color,
    val primarySurfaceDay: Color,
    val primarySurfaceNight: Color,
    // Tertiary (used for remote devices)
    val tertiaryDay: Color,
    val tertiaryNight: Color,
    val tertiaryContainerDay: Color,
    val tertiaryContainerNight: Color,
    val tertiarySurfaceDay: Color,
    val tertiarySurfaceNight: Color,
    // Low battery
    val lowDay: Color = Color(0xFF67040d),
    val lowNight: Color = Color(0xFFffb3ae),
    val lowContainerDay: Color = Color(0xFFff8983),
    val lowContainerNight: Color = Color(0xFF871f21),
    val lowSurfaceDay: Color = Color(0xFFffdad7),
    val lowSurfaceNight: Color = Color(0xFF410004),
)

/**
 * Returns a [ColorPalette] using dynamic Material You system colors on
 * Android S (12) and above, or a static blue fallback on older devices.
 */
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
        // Static blue fallback for pre-Android 12 devices
        val blue = Color(0xFF2962FF)
        val blueBright = Color(0xFF82B1FF)
        val blueContainer = Color(0xFF5C9CFF)
        val blueContainerDark = Color(0xFF1A56C4)
        val blueSurface = Color(0xFFD6E4FF)
        val blueSurfaceDark = Color(0xFF0D2B6B)

        val teal = Color(0xFF00796B)
        val tealBright = Color(0xFF80CBC4)
        val tealContainer = Color(0xFF4DB6AC)
        val tealContainerDark = Color(0xFF00574B)
        val tealSurface = Color(0xFFB2DFDB)
        val tealSurfaceDark = Color(0xFF003330)

        ColorPalette(
            primaryDay = blue,
            primaryNight = blueBright,
            primaryContainerDay = blueContainer,
            primaryContainerNight = blueContainerDark,
            primarySurfaceDay = blueSurface,
            primarySurfaceNight = blueSurfaceDark,
            tertiaryDay = teal,
            tertiaryNight = tealBright,
            tertiaryContainerDay = tealContainer,
            tertiaryContainerNight = tealContainerDark,
            tertiarySurfaceDay = tealSurface,
            tertiarySurfaceNight = tealSurfaceDark,
        )
    }
}

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
        val devices = parseDevicesJson(devicesJson).filter { (now - it.lastUpdated) < 3_600_000 }
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
                val spacingDp = 2.dp
                val widgetHeight = LocalSize.current.height.value
                val availableHeight = widgetHeight - 16f
                val totalSpacing = spacingDp.value * (devices.size - 1)
                val heightPerItem = (availableHeight - totalSpacing) / devices.size
                val tallLayout = heightPerItem >= 90f
                val needsScroll = heightPerItem < 24f

                if (!needsScroll) {
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        devices.forEachIndexed { index, device ->
                            Box(
                                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                                    .cornerRadius(16.dp)
                            ) {
                                DeviceItem(
                                    context, device, device.id == localDeviceId, tallLayout
                                )
                            }
                            if (index < devices.lastIndex) {
                                Spacer(modifier = GlanceModifier.height(spacingDp))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(devices) { device ->
                            Box(
                                modifier = GlanceModifier.fillMaxWidth().height(44.dp)
                                    .padding(bottom = spacingDp)
                            ) {
                                DeviceItem(context, device, device.id == localDeviceId, false)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DeviceItem(
        context: Context, device: Device, isLocalDevice: Boolean, tallLayout: Boolean,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth().fillMaxHeight().cornerRadius(16.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            BatteryIndicator(
                batteryLevel = device.batteryLevel,
                isCharging = device.isCharging,
                isLocalDevice = isLocalDevice,
                deviceName = device.name,
                deviceIconType = device.icon,
                tallLayout = tallLayout
            )
        }
    }

    @Composable
    private fun BatteryIndicator(
        batteryLevel: Int,
        isCharging: Boolean,
        isLocalDevice: Boolean,
        deviceName: String,
        deviceIconType: String,
        tallLayout: Boolean,
    ) {
        val context = LocalContext.current
        val palette = getPalette(context)

        val bgColor: ColorProvider
        val fgColor: ColorProvider
        val textColor: ColorProvider

        if (batteryLevel <= 20) {
            bgColor = androidx.glance.color.ColorProvider(
                day = palette.lowSurfaceDay, night = palette.lowSurfaceNight
            )
            fgColor = androidx.glance.color.ColorProvider(
                day = palette.lowContainerDay, night = palette.lowContainerNight
            )
            textColor = androidx.glance.color.ColorProvider(
                day = palette.lowDay, night = palette.lowNight
            )
        } else if (isLocalDevice) {
            bgColor = androidx.glance.color.ColorProvider(
                day = palette.primarySurfaceDay, night = palette.primarySurfaceNight
            )
            fgColor = androidx.glance.color.ColorProvider(
                day = palette.primaryContainerDay, night = palette.primaryContainerNight
            )
            textColor = androidx.glance.color.ColorProvider(
                day = palette.primaryDay, night = palette.primaryNight
            )
        } else {
            bgColor = androidx.glance.color.ColorProvider(
                day = palette.tertiarySurfaceDay, night = palette.tertiarySurfaceNight
            )
            fgColor = androidx.glance.color.ColorProvider(
                day = palette.tertiaryContainerDay, night = palette.tertiaryContainerNight
            )
            textColor = androidx.glance.color.ColorProvider(
                day = palette.tertiaryDay, night = palette.tertiaryNight
            )
        }

        val contentColor = if (batteryLevel > 20) textColor else GlanceTheme.colors.onSurface

        val typeIconRes = when {
            deviceName.contains("Surface Duo", ignoreCase = true) || deviceIconType == "Surface Duo" -> R.drawable.rounded_dual_screen_24
            deviceIconType.contains("Flip", ignoreCase = true) -> R.drawable.rounded_devices_flip_24
            deviceIconType.contains("Fold", ignoreCase = true) -> R.drawable.rounded_devices_fold_24
            deviceIconType.contains("Tablet", ignoreCase = true) -> R.drawable.rounded_tablet_24
            deviceName.contains("TV", ignoreCase = true) -> R.drawable.rounded_tv_gen_24
            deviceIconType.contains("Phone", ignoreCase = true) ||
                    deviceIconType == "LG Wing" ||
                    deviceIconType == "iKKO Mind One" ||
                    deviceIconType == "Clicks Communicator" ||
                    deviceIconType == "Keyboard Phone" -> R.drawable.round_phone_android_24
            else -> R.drawable.rounded_monitor_24
        }
        Box(
            modifier = GlanceModifier.fillMaxWidth().fillMaxHeight().cornerRadius(16.dp)
                .background(bgColor)
        ) {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                LinearProgressIndicator(
                    progress = batteryLevel.coerceIn(0, 100) / 100f,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(typeIconRes),
                            contentDescription = null,
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(contentColor)
                        )

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        Text(
                            text = deviceName, style = TextStyle(
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = contentColor
                            ), maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$batteryLevel%", style = TextStyle(
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = contentColor
                            )
                        )
                        if (isCharging) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = GlanceModifier.size(20.dp).background(textColor)
                                    .cornerRadius(14.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.round_bolt_24),
                                    contentDescription = "Charging",
                                    modifier = GlanceModifier.size(14.dp),
                                    colorFilter = ColorFilter.tint(fgColor)
                                )
                            }
                        }
                        if (!isCharging && batteryLevel <= 20) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = GlanceModifier.size(20.dp).background(fgColor)
                                    .cornerRadius(14.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.round_battery_alert_24),
                                    contentDescription = "Low battery",
                                    modifier = GlanceModifier.size(14.dp),
                                    colorFilter = ColorFilter.tint(textColor)
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
                    Image(
                        provider = ImageProvider(typeIconRes),
                        contentDescription = null,
                        modifier = GlanceModifier.size(16.dp),
                        colorFilter = ColorFilter.tint(contentColor)
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))

                    Text(
                        text = deviceName, style = TextStyle(
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = contentColor
                        ), maxLines = 1, modifier = GlanceModifier.defaultWeight()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Text(
                            text = "$batteryLevel%", style = TextStyle(
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = contentColor
                            )
                        )
                        if (isCharging) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = GlanceModifier.size(20.dp).background(textColor)
                                    .cornerRadius(14.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.round_bolt_24),
                                    contentDescription = "Charging",
                                    modifier = GlanceModifier.size(14.dp),
                                    colorFilter = ColorFilter.tint(fgColor)
                                )
                            }
                        }
                        if (!isCharging && batteryLevel <= 20) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = GlanceModifier.size(20.dp).background(fgColor)
                                    .cornerRadius(14.dp)
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.round_battery_alert_24),
                                    contentDescription = "Low battery",
                                    modifier = GlanceModifier.size(14.dp),
                                    colorFilter = ColorFilter.tint(textColor)
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
        context: Context, glanceId: GlanceId, parameters: ActionParameters,
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