package com.xenonware.cloudremote.data

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName

@Keep
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
    @get:PropertyName("isLocked")
    val isLocked: Boolean = false,
    val lastUpdated: Long = 0L,

    // Media properties
    val mediaTitle: String = "",
    val mediaArtist: String = "",
    val mediaAlbumArt: String = "",
    @get:PropertyName("isPlaying")
    val isPlaying: Boolean = false,
    val mediaAction: String = "", // "play", "pause", "next", "previous", "custom1", "custom2"

    val mediaCustomAction1Title: String = "",
    val mediaCustomAction2Title: String = "",
    val mediaCustomAction1Action: String = "",
    val mediaCustomAction2Action: String = "",

    val pendingAction: String = "" // "lock"
)