package com.xenonware.cloudremote.data

import com.google.firebase.firestore.FirebaseFirestore
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

    fun getDevice(deviceId: String, onComplete: (Device?) -> Unit) {
        if (deviceId.isBlank()) {
            onComplete(null)
            return
        }
        
        db.collection("devices").document(deviceId).get()
            .addOnSuccessListener { document ->
                val device = document.toObject(Device::class.java)
                onComplete(device)
            }
            .addOnFailureListener {
                onComplete(null)
            }
    }

    fun updateDevice(device: Device) {
        if (device.id.isBlank()) return
        
        db.collection("devices").document(device.id).set(device)
    }

    fun deleteDevice(deviceId: String) {
        if (deviceId.isBlank()) return
        
        db.collection("devices").document(deviceId).delete()
    }
}