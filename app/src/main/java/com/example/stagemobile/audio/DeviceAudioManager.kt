package com.example.stagemobile.audio

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

    private fun checkOutputDevices() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val list = mutableListOf<AudioDeviceState>()
        var internalAdded = false
        
        // 1. Prioridade para Speaker Interno (Garante que apareça se disponível)
        devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let {
            val manufacturer = android.os.Build.MANUFACTURER.uppercase()
            val deviceModel = android.os.Build.MODEL
            list.add(AudioDeviceState(it.id, "Interna (Speaker $manufacturer $deviceModel)"))
            internalAdded = true
        }

        // 2. Adicionar demais dispositivos (USB/Wired) e outros internos apenas se o Speaker falhar
        devices.forEach {
            val type = it.type
            val isExternal = type == AudioDeviceInfo.TYPE_USB_DEVICE || 
                             type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                             type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                             type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                             type == AudioDeviceInfo.TYPE_AUX_LINE
            
            if (isExternal) {
                val productName = it.productName?.toString() ?: "Interface USB ${it.id}"
                list.add(AudioDeviceState(it.id, "Externa ($productName)"))
            } else if (!internalAdded && type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                // Fallback para Earpiece apenas se não houver Speaker (raro)
                val manufacturer = android.os.Build.MANUFACTURER.uppercase()
                val deviceModel = android.os.Build.MODEL
                list.add(AudioDeviceState(it.id, "Interna (Earpiece $manufacturer $deviceModel)"))
                internalAdded = true
            }
        }

        if (_availableAudioDevices.value != list) {
            _availableAudioDevices.value = list
            Log.i("DeviceAudioManager", "Outputs updated: $list")
        }
    }
    
    fun release() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }
}
