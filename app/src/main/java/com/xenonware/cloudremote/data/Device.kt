package com.xenonware.cloudremote.data

import com.google.firebase.firestore.PropertyName

data class Device(
    val id: String = "",
    val name: String = "",
    val batteryLevel: Int = 0,
    @get:PropertyName("isCharging")
    val isCharging: Boolean = false,
    val mediaVolume: Int = 0,
    val maxMediaVolume: Int = 15,
    val ringerMode: Int = 2,
    @get:PropertyName("isDndActive")
    val isDndActive: Boolean = false,
    @get:PropertyName("isDeviceOn")
    val isDeviceOn: Boolean = true,
    @get:PropertyName("isScreenOn")
    val isScreenOn: Boolean = true,
    @get:PropertyName("isCurtainOn")
    val isCurtainOn: Boolean = false,
)