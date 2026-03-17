package com.xenonware.cloudremote.presentation.sign_in

import com.google.firebase.firestore.FirebaseFirestore
import com.xenonware.cloudremote.data.Device
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GoogleCloudRepository {

    private val db = FirebaseFirestore.getInstance()

    fun getDevicesFlow(): Flow<List<Device>> = callbackFlow {
        val listener = db.collection("devices").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val devices = snapshot.documents.mapNotNull { it.toObject(Device::class.java) }
                trySend(devices)
            }
        }
        awaitClose { listener.remove() }
    }

    fun updateDevice(device: Device) {
        if (device.id.isBlank()) return
        db.collection("devices").document(device.id).set(device)
    }

    fun updateDeviceFields(deviceId: String, fields: Map<String, Any>) {
        if (deviceId.isBlank() || fields.isEmpty()) return
        db.collection("devices").document(deviceId).update(fields)
    }

    fun deleteDevice(deviceId: String) {
        if (deviceId.isBlank()) return
        db.collection("devices").document(deviceId).delete()
    }
}