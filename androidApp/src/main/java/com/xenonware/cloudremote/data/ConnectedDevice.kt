package com.xenonware.cloudremote.data

import androidx.annotation.Keep

@Keep
enum class BTDeviceType {
    MOUSE, KEYBOARD, HEADSET, EARBUDS, WATCH, PEN, CAR, CONTROLLER, PHONE, HEALTH, MICROPHONE, COMPUTER, LAPTOP, TV, SPEAKER, IMAGING, PRINTER, NETWORKING, HEARING_AID, OTHER, PERIPHERAL, GLASSES
}

@Keep
data class ConnectedDevice(
    val name: String = "",
    val type: BTDeviceType = BTDeviceType.OTHER,
    val batteryLevel: Int = -1
) {
    fun toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "type" to type.name,
        "batteryLevel" to batteryLevel
    )
}
