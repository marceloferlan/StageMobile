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
    
    private val _availableAudioDevices = MutableStateFlow<List<AudioDeviceState>>(listOf(AudioDeviceState(-1, "Auto (Padrão do Sistema)")))
    val availableAudioDevices: StateFlow<List<AudioDeviceState>> = _availableAudioDevices.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            checkOutputDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesRemoved(removedDevices)
            checkOutputDevices()
        }
    }

    init {
        // Run callback on main thread handler
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        checkOutputDevices()
    }

    private fun checkOutputDevices() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val list = mutableListOf(AudioDeviceState(-1, "Auto (Padrão do Sistema)"))
        
        devices.forEach {
            if (it.type == AudioDeviceInfo.TYPE_USB_DEVICE || 
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_AUX_LINE) {
                list.add(AudioDeviceState(it.id, it.productName?.toString() ?: "Saída Externa ${it.id}"))
            }
        }

        _availableAudioDevices.value = list
        Log.i("DeviceAudioManager", "External Outputs updated: $list")
    }
    
    fun release() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }
}
