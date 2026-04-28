package com.marceloferlan.stagemobile.superpowered

/**
 * Interface para callbacks de eventos USB do Superpowered.
 */
interface SuperpoweredUSBAudioHandler {
    fun onUSBAudioDeviceAttached(deviceId: Int) {}
    fun onUSBMIDIDeviceAttached(deviceId: Int) {}
    fun onUSBDeviceDetached(deviceId: Int) {}
}
