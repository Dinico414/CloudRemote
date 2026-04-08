package com.xenonware.cloudremote.data

import androidx.annotation.Keep

@Keep
enum class BTDeviceType {
    MOUSE, KEYBOARD, HEADSET, EARBUDS, WATCH, PEN, CONTROLLER, PHONE, COMPUTER, LAPTOP, TV, SPEAKER, IMAGING, PRINTER, NETWORKING, HEARING_AID, OTHER, PERIPHERAL
}

@Keep
data class ConnectedDevice(
    val name: String = "",
    val type: BTDeviceType = BTDeviceType.OTHER,
    val batteryLevel: Int = -1
)
