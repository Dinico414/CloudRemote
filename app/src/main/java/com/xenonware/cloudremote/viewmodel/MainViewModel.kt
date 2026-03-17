package com.xenonware.cloudremote.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.xenonware.cloudremote.data.Device
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


    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    var localDeviceId: String = ""
    var localDeviceName: String = ""

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
            }
        }
    }

    fun toggleCurrentDevice() {
        val currentDevices = _devices.value
        val isAdded = currentDevices.any { it.id == localDeviceId }

        if (isAdded) {
            repository.deleteDevice(localDeviceId)
        } else {
            val newDevice = Device(
                id = localDeviceId,
                name = localDeviceName,
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