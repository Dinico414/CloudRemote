package com.xenonware.cloudremote.data

data class Device(
    val id: String = "",
    val name: String = "",
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val mediaVolume: Int = 0,
    val maxMediaVolume: Int = 15,
    val ringerMode: Int = 2,
    val isDndActive: Boolean = false,
    val isDeviceOn: Boolean = true,
    val isScreenOn: Boolean = true,
    val isCurtainOn: Boolean = false,
)