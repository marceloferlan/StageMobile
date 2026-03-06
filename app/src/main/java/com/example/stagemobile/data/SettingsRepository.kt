package com.example.stagemobile.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("stage_mobile_settings", Context.MODE_PRIVATE)

    var bufferSize: Int
        get() = prefs.getInt(KEY_BUFFER_SIZE, 64)
        set(value) = prefs.edit().putInt(KEY_BUFFER_SIZE, value).apply()

    var sampleRate: Int
        get() = prefs.getInt(KEY_SAMPLE_RATE, 48000)
        set(value) = prefs.edit().putInt(KEY_SAMPLE_RATE, value).apply()

    // 0 means "All Channels"
    var midiChannel: Int
        get() = prefs.getInt(KEY_MIDI_CHANNEL, 0)
        set(value) = prefs.edit().putInt(KEY_MIDI_CHANNEL, value).apply()

    var showMaster: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MASTER, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_MASTER, value).apply()

    var selectedMidiDeviceId: Int
        get() = prefs.getInt(KEY_MIDI_DEVICE_ID, -1)
        set(value) = prefs.edit().putInt(KEY_MIDI_DEVICE_ID, value).apply()

    var activeMidiDevices: Set<String>
        get() = prefs.getStringSet(KEY_ACTIVE_MIDI_DEVICES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_ACTIVE_MIDI_DEVICES, value).apply()

    var selectedAudioDeviceId: Int
        get() = prefs.getInt(KEY_AUDIO_DEVICE_ID, -1)
        set(value) = prefs.edit().putInt(KEY_AUDIO_DEVICE_ID, value).apply()

    companion object {
        private const val KEY_BUFFER_SIZE = "buffer_size"
        private const val KEY_SAMPLE_RATE = "sample_rate"
        private const val KEY_MIDI_CHANNEL = "midi_channel"
        private const val KEY_SHOW_MASTER = "show_master"
        private const val KEY_MIDI_DEVICE_ID = "midi_device_id"
        private const val KEY_AUDIO_DEVICE_ID = "audio_device_id"
        private const val KEY_ACTIVE_MIDI_DEVICES = "active_midi_devices"
    }
}
