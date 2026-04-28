package com.marceloferlan.stagemobile.audio.usb

/**
 * Interface de callbacks para eventos de dispositivos USB de áudio.
 * Implementada pelo ViewModel ou Activity para reagir a conexões/desconexões.
 */
interface SuperpoweredUSBAudioHandler {
    fun onUSBAudioDeviceAttached(deviceID: Int)
    fun onUSBMIDIDeviceAttached(deviceID: Int)
    fun onUSBDeviceDetached(deviceID: Int)
}
