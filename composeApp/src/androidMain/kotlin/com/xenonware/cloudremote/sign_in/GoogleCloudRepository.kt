package com.xenonware.cloudremote.sign_in

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.xenonware.cloudremote.data.Device
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GoogleCloudRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()


    fun getDevicesFlow(): Flow<List<Device>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d("GoogleCloudRepository", "User UID is null, cannot start flow.")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        Log.d("GoogleCloudRepository", "Starting snapshot listener for users/$uid/devices")
        val listener = db.collection("users").document(uid).collection("devices")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("GoogleCloudRepository", "Listen failed for users/$uid/devices.", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val devices = snapshot.documents.mapNotNull { it.toObject(Device::class.java) }
                    Log.d("GoogleCloudRepository", "Snapshot retrieved ${devices.size} devices for users/$uid/devices")
                    trySend(devices)
                }
            }
        awaitClose {
            Log.d("GoogleCloudRepository", "Closing snapshot listener for users/$uid/devices")
            listener.remove()
        }
    }

    fun updateDevice(device: Device) {
        if (device.id.isBlank()) return
        val uid = auth.currentUser?.uid ?: return
        Log.d("GoogleCloudRepository", "Updating device ${device.id} in users/$uid/devices")
        db.collection("users").document(uid).collection("devices").document(device.id).set(device)
            .addOnFailureListener { Log.e("GoogleCloudRepository", "Failed to update device in users/$uid/devices", it) }
    }

    fun updateDeviceFields(deviceId: String, fields: Map<String, Any>) {
        if (deviceId.isBlank() || fields.isEmpty()) return
        val uid = auth.currentUser?.uid ?: return
        Log.d("GoogleCloudRepository", "Updating fields for device $deviceId in users/$uid/devices")
        db.collection("users").document(uid).collection("devices").document(deviceId).update(fields)
            .addOnFailureListener { Log.e("GoogleCloudRepository", "Failed to update device fields in users/$uid/devices", it) }
    }

    fun deleteDevice(deviceId: String) {
        if (deviceId.isBlank()) return
        val uid = auth.currentUser?.uid ?: return
        Log.d("GoogleCloudRepository", "Deleting device $deviceId from users/$uid/devices")
        db.collection("users").document(uid).collection("devices").document(deviceId).delete()
            .addOnFailureListener { Log.e("GoogleCloudRepository", "Failed to delete device from users/$uid/devices", it) }
    }
    suspend fun deleteDeviceAndAwait(deviceId: String) {
        if (deviceId.isBlank()) return
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).collection("devices")
                .document(deviceId).delete().await()
            Log.d("GoogleCloudRepository", "Device $deviceId deleted before sign out")
        } catch (e: Exception) {
            Log.e("GoogleCloudRepository", "Failed to delete device before sign out", e)
        }
    }
}