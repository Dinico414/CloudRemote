package com.xenonware.cloudremote.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.helper.LocalDeviceManager
import com.xenonware.cloudremote.presentation.sign_in.GoogleCloudRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GoogleCloudRepository()
    private val auth = FirebaseAuth.getInstance()
    private val localDeviceManager = LocalDeviceManager(application)

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _localDeviceName = MutableStateFlow("")
    val localDeviceName: StateFlow<String> = _localDeviceName

    var localDeviceId: String = ""

    init {
        viewModelScope.launch {
            _currentUser.flatMapLatest { user ->
                if (user != null) {
                    repository.getDevicesFlow().catch { e ->
                        Log.e("MainViewModel", "Error fetching devices", e)
                        emit(emptyList())
                    }
                } else {
                    flowOf(emptyList())
                }
            }.collect { deviceList ->
                _devices.value = deviceList
                deviceList.find { it.id == localDeviceId }?.name?.let {
                    if (it.isNotBlank()) {
                        _localDeviceName.value = it
                    }
                }
            }
        }
    }

    fun onSignedIn() {
        _currentUser.value = auth.currentUser
    }

    fun updateLocalDeviceName(name: String) {
        _localDeviceName.value = name
        // Optionally, you might want to persist this name to SharedPreferences here
    }

    fun toggleCurrentDevice(customName: String? = null, customIcon: String = "") {
        val currentDevices = _devices.value
        val isAdded = currentDevices.any { it.id == localDeviceId }

        if (isAdded) {
            repository.deleteDevice(localDeviceId)
        } else {
            val deviceName = customName ?: _localDeviceName.value.ifBlank { currentUser.value?.displayName ?: "Unknown Device" }
            val newDevice = Device(
                id = localDeviceId,
                name = deviceName,
                icon = customIcon,
                batteryLevel = 0,
                mediaVolume = 0,
                isDeviceOn = true,
                isScreenOn = true,
                isCurtainOn = false
            )
            repository.updateDevice(newDevice)
        }
    }

    fun toggleLocalCurtain() {
        val localDevice = _devices.value.find { it.id == localDeviceId }
        localDevice?.let {
            val isCurtainOn = !it.isCurtainOn
            localDeviceManager.setCurtain(isCurtainOn)
            // Also update the state in Firestore so other devices can see the change
            updateDevice(it.copy(isCurtainOn = isCurtainOn))
        }
    }

    fun updateDevice(device: Device) {
        repository.updateDevice(device)
    }

    fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnSuccessListener { result ->
                _currentUser.value = result.user
            }.addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }
}