package com.xenonware.cloudremote.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.xenonware.cloudremote.data.Device
import com.xenonware.cloudremote.helper.LocalDeviceManager
import com.xenonware.cloudremote.helper.MediaNotificationListener
import com.xenonware.cloudremote.sign_in.GoogleCloudRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    enum class NetworkState { ONLINE, OFFLINE, BAD_CONNECTION }

    private val repository = GoogleCloudRepository()
    private val auth = FirebaseAuth.getInstance()
    private val localDeviceManager = LocalDeviceManager(application)

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _localDeviceName = MutableStateFlow("")
    val localDeviceName: StateFlow<String> = _localDeviceName

    private val _networkState = MutableStateFlow(NetworkState.ONLINE)
    val networkState: StateFlow<NetworkState> = _networkState

    val localDeviceState: StateFlow<LocalDeviceManager.DeviceState> = localDeviceManager.observeDeviceState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = localDeviceManager.getCurrentStateSnapshot()
        )

    var localDeviceId: String = ""

    init {
        setupNetworkObserver(application)
        
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

    private fun setupNetworkObserver(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Initial check
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isInitiallyConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        _networkState.value = if (isInitiallyConnected) NetworkState.ONLINE else NetworkState.OFFLINE

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Wait for onCapabilitiesChanged to evaluate the true state.
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val isConnected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (!isConnected) {
                    _networkState.value = NetworkState.OFFLINE
                } else {
                    val bandwidth = networkCapabilities.linkDownstreamBandwidthKbps
                    // If reported bandwidth is extremely low, it's a bad connection.
                    // (0 usually implies it's unknown or unmetered in some emulators, so we check > 0)
                    if (bandwidth in 1..500) {
                        _networkState.value = NetworkState.BAD_CONNECTION
                    } else {
                        _networkState.value = NetworkState.ONLINE
                    }
                }
            }

            override fun onLost(network: Network) {
                _networkState.value = NetworkState.OFFLINE
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
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
        localDeviceManager.forceUpdateMediaVolume()
    }

    fun updateDevice(device: Device) {
        if (device.id == localDeviceId) {
            // Optimistic update for local device: apply changes locally immediately
            val currentLocalState = localDeviceState.value
            
            if (device.mediaVolume != currentLocalState.mediaVolume) {
                localDeviceManager.setVolume(device.mediaVolume)
            }
            if (device.ringerMode != currentLocalState.ringerMode) {
                localDeviceManager.setRingerMode(device.ringerMode)
            }
            if (device.isDndActive != currentLocalState.isDndActive) {
                localDeviceManager.setDnd(device.isDndActive)
            }
            if (device.isCurtainOn != currentLocalState.isCurtainOn) {
                localDeviceManager.setCloudCurtain(device.isCurtainOn)
            }
            if (device.mediaAction.isNotBlank()) {
                handleLocalMediaAction(device.mediaAction)
                // Clear media action before sending to cloud to avoid re-triggering
                repository.updateDevice(device.copy(mediaAction = ""))
                return
            }
            if (device.pendingAction == "lock") {
                localDeviceManager.lockDevice()
                // Clear pending action before sending to cloud to avoid re-locking
                repository.updateDevice(device.copy(pendingAction = ""))
                return
            }
        }
        repository.updateDevice(device)
    }

    fun removeDevice(device: Device) {
        repository.deleteDevice(device.id)
    }

    fun updateDeviceFields(deviceId: String, fields: Map<String, Any>) {
        viewModelScope.launch {
            repository.updateDeviceFields(deviceId, fields)
        }
    }

    private fun handleLocalMediaAction(action: String) {
        val componentName = ComponentName(getApplication(), MediaNotificationListener::class.java)
        val mediaSessionManager = getApplication<Application>().getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val controllers = try {
            mediaSessionManager.getActiveSessions(componentName)
        } catch (e: SecurityException) {
            emptyList()
        }
        val mediaController = controllers.firstOrNull()

        when (action) {
            "play" -> mediaController?.transportControls?.play()
            "pause" -> mediaController?.transportControls?.pause()
            "next" -> mediaController?.transportControls?.skipToNext()
            "previous" -> mediaController?.transportControls?.skipToPrevious()
            "custom1" -> {
                val currentDevice = _devices.value.find { it.id == localDeviceId }
                currentDevice?.mediaCustomAction1Action?.let {
                    if (it.isNotBlank()) mediaController?.transportControls?.sendCustomAction(it, null)
                }
            }
            "custom2" -> {
                val currentDevice = _devices.value.find { it.id == localDeviceId }
                currentDevice?.mediaCustomAction2Action?.let {
                    if (it.isNotBlank()) mediaController?.transportControls?.sendCustomAction(it, null)
                }
            }
        }
    }
}
