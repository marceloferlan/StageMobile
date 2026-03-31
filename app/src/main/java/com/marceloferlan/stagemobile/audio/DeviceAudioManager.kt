package com.marceloferlan.stagemobile.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioDeviceState(val id: Int, val name: String)

class DeviceAudioManager(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private val _availableAudioDevices = MutableStateFlow<List<AudioDeviceState>>(emptyList())
    val availableAudioDevices: StateFlow<List<AudioDeviceState>> = _availableAudioDevices.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            checkOutputDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesRemoved(removedDevices)
            removedDevices?.forEach { device ->
                if (device.isSink) {
                    val name = device.productName?.toString() ?: "Desconhecido"
                    Log.w("DeviceAudioManager", "Device REMOVED: $name (Type: ${device.type})")
                }
            }
            checkOutputDevices()
        }
    }

    init {
        // Run callback on main thread handler
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        
        // SCAN 1: Immediate scan
        checkOutputDevices()
        
        // SCAN 2: Delayed scan (1s) to catch slow-initializing USB interfaces on Android
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("DeviceAudioManager", "Running delayed startup scan...")
            checkOutputDevices()
        }, 1000)
    }

    private fun getDevicePriority(type: Int): Int {
        return when (type) {
            AudioDeviceInfo.TYPE_BLE_HEADSET, 
            AudioDeviceInfo.TYPE_BLE_SPEAKER, 
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> 100
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 90
            AudioDeviceInfo.TYPE_USB_DEVICE, 
            AudioDeviceInfo.TYPE_USB_HEADSET -> 80
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, 
            AudioDeviceInfo.TYPE_WIRED_HEADSET, 
            AudioDeviceInfo.TYPE_AUX_LINE -> 70
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 10
            else -> 0
        }
    }

    private fun checkOutputDevices() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val finalSelection = mutableListOf<AudioDeviceState>()
        val deviceMap = mutableMapOf<String, Pair<Int, AudioDeviceState>>() // Key: Name, Value: (Priority, State)
        var internalAdded = false
        
        // 1. Prioridade para Speaker Interno
        devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let {
            val manufacturer = android.os.Build.MANUFACTURER.uppercase()
            val deviceModel = android.os.Build.MODEL
            finalSelection.add(AudioDeviceState(it.id, "Interna (Speaker $manufacturer $deviceModel)"))
            internalAdded = true
        }

        // 2. Processar demais dispositivos com Deduplicação
        devices.forEach { device ->
            val type = device.type
            val isBluetooth = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                             type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                             type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                             type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                             type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                             
            val isExternal = type == AudioDeviceInfo.TYPE_USB_DEVICE || 
                            type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                            type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            type == AudioDeviceInfo.TYPE_AUX_LINE

            if (isBluetooth || isExternal) {
                val prefix = if (isBluetooth) "Bluetooth" else "Externa"
                val productName = try {
                    device.productName?.toString() ?: "Interface ${device.id}"
                } catch (e: SecurityException) {
                    "Dispositivo Protegido (Sem Permissão)"
                }
                
                val displayName = "$prefix ($productName)"
                val priority = getDevicePriority(type)
                
                val existing = deviceMap[displayName]
                if (existing == null || priority > existing.first) {
                    deviceMap[displayName] = priority to AudioDeviceState(device.id, displayName)
                }
            } else if (!internalAdded && type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                val manufacturer = android.os.Build.MANUFACTURER.uppercase()
                val deviceModel = android.os.Build.MODEL
                finalSelection.add(AudioDeviceState(device.id, "Interna (Earpiece $manufacturer $deviceModel)"))
                internalAdded = true
            }
        }

        // Adicionar dispositivos únicos do mapa à lista final
        finalSelection.addAll(deviceMap.values.map { it.second })

        if (_availableAudioDevices.value != finalSelection) {
            _availableAudioDevices.value = finalSelection
            Log.i("DeviceAudioManager", "Outputs updated: $finalSelection")
        }
    }
    
    fun release() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }
}
