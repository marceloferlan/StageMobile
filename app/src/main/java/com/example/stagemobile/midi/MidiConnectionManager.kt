package com.example.stagemobile.midi

import android.content.Context
import android.media.midi.*
import android.os.Handler
import android.os.Looper
import android.util.Log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MidiDeviceState(val id: Int, val name: String, val isConnected: Boolean)

/**
 * Manages USB MIDI device connections using Android's MidiManager API.
 * Detects connected MIDI devices, opens input ports for ALL of them, and routes messages
 * to a callback tagged with the device name.
 */
class MidiConnectionManager(
    context: Context,
    private val onNoteOn: (deviceName: String, channel: Int, key: Int, velocity: Int) -> Unit,
    private val onNoteOff: (deviceName: String, channel: Int, key: Int) -> Unit,
    private val onControlChange: (deviceName: String, channel: Int, controller: Int, value: Int) -> Unit,
    private val onPitchBend: (deviceName: String, channel: Int, value: Int) -> Unit,
    private val onProgramChange: (deviceName: String, channel: Int, program: Int) -> Unit
) {

    companion object {
        private const val TAG = "MidiConnectionManager"

        // MIDI status bytes
        private const val STATUS_NOTE_OFF = 0x80
        private const val STATUS_NOTE_ON = 0x90
        private const val STATUS_CONTROL_CHANGE = 0xB0
        private const val STATUS_PROGRAM_CHANGE = 0xC0
        private const val STATUS_PITCH_BEND = 0xE0
    }

    private val midiThread = android.os.HandlerThread("MidiProcessingThread", android.os.Process.THREAD_PRIORITY_AUDIO).apply { start() }
    private val midiHandler = Handler(midiThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    
    // Store multiple open devices
    private val openDevices = mutableMapOf<Int, MidiDevice>()
    private val openPorts = mutableMapOf<Int, MidiOutputPort>()

    private val _availableDevices = MutableStateFlow<List<MidiDeviceState>>(emptyList())
    val availableDevices: StateFlow<List<MidiDeviceState>> = _availableDevices.asStateFlow()

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            Log.i(TAG, "MIDI device added: ${getDeviceName(info)}")
            connectToDevice(info)
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            val name = getDeviceName(info)
            Log.i(TAG, "MIDI device removed: $name")
            disconnectDevice(info.id)
            updateAvailableDevices()
        }
    }

    // Dynamic receiver generator per device
    private fun createReceiver(deviceName: String) = object : MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            parseMidiData(deviceName, data, offset, count)
        }
    }

    fun start() {
        if (midiManager == null) {
            Log.e(TAG, "MIDI not supported on this device")
            return
        }

        midiManager.registerDeviceCallback(deviceCallback, mainHandler)

        // Connect to ALL already-connected devices
        val devices = midiManager.devices
        for (info in devices) {
            if (info.outputPortCount > 0) {
                connectToDevice(info)
            }
        }
        updateAvailableDevices()

        Log.i(TAG, "MidiConnectionManager started, ${devices.size} device(s) found")
    }

    fun stop() {
        midiManager?.unregisterDeviceCallback(deviceCallback)
        disconnectAll()
        Log.i(TAG, "MidiConnectionManager stopped")
    }

    private fun connectToDevice(info: MidiDeviceInfo) {
        if (info.outputPortCount == 0 || openDevices.containsKey(info.id)) return

        midiManager?.openDevice(info, { device ->
            if (device == null) {
                Log.e(TAG, "Failed to open MIDI device ${info.id}")
                return@openDevice
            }

            val port = device.openOutputPort(0)
            if (port != null) {
                val name = getDeviceName(info)
                val receiver = createReceiver(name)
                port.connect(receiver)
                
                openDevices[info.id] = device
                openPorts[info.id] = port
                
                Log.i(TAG, "Connected to MIDI device: $name (ID: ${info.id})")
                mainHandler.post { updateAvailableDevices() }
            } else {
                Log.w(TAG, "Could not open output port for device ${info.id}")
                device.close()
            }
        }, mainHandler)
    }

    private fun disconnectDevice(deviceId: Int) {
        try {
            openPorts[deviceId]?.close()
            openDevices[deviceId]?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing MIDI device $deviceId: ${e.message}")
        } finally {
            openPorts.remove(deviceId)
            openDevices.remove(deviceId)
        }
    }

    private fun disconnectAll() {
        openPorts.keys.toList().forEach { disconnectDevice(it) }
    }

    private fun updateAvailableDevices() {
        val currentDevices = midiManager?.devices ?: emptyArray()
        val states = currentDevices.filter { it.outputPortCount > 0 }.map { 
            MidiDeviceState(
                id = it.id,
                name = getDeviceName(it),
                isConnected = openDevices.containsKey(it.id)
            )
        }
        _availableDevices.value = states
    }

    private fun parseMidiData(deviceName: String, data: ByteArray, offset: Int, count: Int) {
        var i = offset
        val end = offset + count

        while (i < end) {
            val status = data[i].toInt() and 0xFF

            if (status < 0x80) {
                i++
                continue
            }

            val messageType = status and 0xF0
            val channel = status and 0x0F

            when (messageType) {
                STATUS_NOTE_ON -> {
                    if (i + 2 < end) {
                        val key = data[i + 1].toInt() and 0x7F
                        val velocity = data[i + 2].toInt() and 0x7F
                        if (velocity > 0) {
                            midiHandler.post { onNoteOn(deviceName, channel, key, velocity) }
                        } else {
                            midiHandler.post { onNoteOff(deviceName, channel, key) }
                        }
                        i += 3
                    } else break
                }
                STATUS_NOTE_OFF -> {
                    if (i + 2 < end) {
                        val key = data[i + 1].toInt() and 0x7F
                        midiHandler.post { onNoteOff(deviceName, channel, key) }
                        i += 3
                    } else break
                }
                STATUS_CONTROL_CHANGE -> {
                    if (i + 2 < end) {
                        val controller = data[i + 1].toInt() and 0x7F
                        val value = data[i + 2].toInt() and 0x7F
                        midiHandler.post { onControlChange(deviceName, channel, controller, value) }
                        i += 3
                    } else break
                }
                STATUS_PROGRAM_CHANGE -> {
                    if (i + 1 < end) {
                        val program = data[i + 1].toInt() and 0x7F
                        midiHandler.post { onProgramChange(deviceName, channel, program) }
                        i += 2
                    } else break
                }
                STATUS_PITCH_BEND -> {
                    if (i + 2 < end) {
                        val lsb = data[i + 1].toInt() and 0x7F
                        val msb = data[i + 2].toInt() and 0x7F
                        val value = (msb shl 7) or lsb
                        midiHandler.post { onPitchBend(deviceName, channel, value) }
                        i += 3
                    } else break
                }
                else -> {
                    i++
                }
            }
        }
    }

    private fun getDeviceName(info: MidiDeviceInfo): String {
        val properties = info.properties
        val product = properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
        val name = properties.getString(MidiDeviceInfo.PROPERTY_NAME)
        val manufacturer = properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
        
        return product ?: name ?: manufacturer ?: "Unknown MIDI Device"
    }
}
