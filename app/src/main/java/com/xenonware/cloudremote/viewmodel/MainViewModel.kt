package com.xenonware.cloudremote.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
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
import kotlinx.coroutines.delay
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

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _localDeviceName = MutableStateFlow("")
    val localDeviceName: StateFlow<String> = _localDeviceName

    var localDeviceId: String = ""

    init {
        viewModelScope.launch {
            _currentUser.flatMapLatest { user ->
                Log.d("MainViewModel", "Current user state changed: ${user?.uid}")
                if (user != null) {
                    repository.getDevicesFlow().catch { e ->
                        Log.e("MainViewModel", "Error fetching devices from repository", e)
                        emit(emptyList())
                    }
                } else {
                    Log.d("MainViewModel", "User is null, emitting empty device list")
                    flowOf(emptyList())
                }
            }.collect { deviceList ->
                Log.d("MainViewModel", "Received updated device list of size: ${deviceList.size}")
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
        Log.d("MainViewModel", "onSignedIn triggered, updating currentUser")
        _currentUser.value = auth.currentUser
    }

    fun updateLocalDeviceName(name: String) {
        _localDeviceName.value = name
    }

    fun toggleCurrentDevice(customName: String? = null, customIcon: String = "") {
        if (localDeviceId.isBlank()) {
            Log.e("MainViewModel", "Cannot toggle current device: localDeviceId is empty")
            return
        }
        val currentDevices = _devices.value
        val isAdded = currentDevices.any { it.id == localDeviceId }

        if (isAdded) {
            Log.d("MainViewModel", "Removing device from cloud: $localDeviceId")
            repository.deleteDevice(localDeviceId)
        } else {
            Log.d("MainViewModel", "Adding device to cloud: $localDeviceId (name: $customName, icon: $customIcon)")
            val deviceName = customName ?: _localDeviceName.value.ifBlank { currentUser.value?.displayName ?: "Unknown Device" }
            val newDevice = Device(
                id = localDeviceId,
                userId = auth.currentUser?.uid ?: "",
                name = deviceName,
                icon = customIcon,
                batteryLevel = 0,
                mediaVolume = 0,
                isDeviceOn = true,
                isScreenOn = true,
                isCurtainOn = false
            )
            repository.updateDevice(newDevice)
            forceUpdateDeviceValues()
        }
    }

    private fun forceUpdateDeviceValues() {
        viewModelScope.launch {
            try {
                val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                if (currentVolume < maxVolume) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume + 1, 0)
                    delay(100)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                } else {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume - 1, 0)
                    delay(100)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to force update device values", e)
            }
        }
    }

    fun updateDevice(device: Device) {
        repository.updateDevice(device)
    }

    fun removeDevice(device: Device) {
        repository.deleteDevice(device.id)
    }
}
