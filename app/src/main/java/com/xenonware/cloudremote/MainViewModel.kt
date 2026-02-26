package com.xenonware.cloudremote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.data.GoogleCloudRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GoogleCloudRepository()
    private val auth = FirebaseAuth.getInstance()
    // LocalDeviceManager is now handled by CloudRemoteService for background sync.
    // We only keep it here if we needed direct control, but for now we rely on the Service.

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    var localDeviceId: String = ""
    var localDeviceName: String = ""

    init {
        // Observe Firestore for changes (Remote -> UI)
        viewModelScope.launch {
            repository.getDevicesFlow()
                .catch { e -> e.printStackTrace() }
                .collect { deviceList ->
                    _devices.value = deviceList
                }
        }
    }

    fun toggleCurrentDevice() {
        val currentDevices = _devices.value
        val isAdded = currentDevices.any { it.id == localDeviceId }
        
        if (isAdded) {
            repository.deleteDevice(localDeviceId)
        } else {
            // Initial Add
            val newDevice = Device(
                id = localDeviceId,
                name = localDeviceName,
                // Defaults, Service will update these shortly
                batteryLevel = 0, 
                mediaVolume = 0,
                isDeviceOn = true,
                isScreenOn = true,
                isCurtainOn = false
            )
            repository.updateDevice(newDevice)
        }
    }

    fun updateDevice(device: Device) {
        repository.updateDevice(device)
    }

    fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                _currentUser.value = result.user
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }
}