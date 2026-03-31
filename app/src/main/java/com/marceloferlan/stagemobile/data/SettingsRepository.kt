package com.marceloferlan.stagemobile.data

import android.content.Context
import android.content.SharedPreferences

import com.marceloferlan.stagemobile.midi.MidiLearnMapping
import com.marceloferlan.stagemobile.midi.MidiLearnTarget
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("stage_mobile_settings", Context.MODE_PRIVATE)

    var bufferSize: Int
        get() = prefs.getInt(KEY_BUFFER_SIZE, 256)
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

    var activeMidiDevices: Set<String>?
        get() = if (prefs.contains(KEY_ACTIVE_MIDI_DEVICES)) prefs.getStringSet(KEY_ACTIVE_MIDI_DEVICES, emptySet()) else null
        set(value) = prefs.edit().putStringSet(KEY_ACTIVE_MIDI_DEVICES, value).apply()

    var selectedAudioDeviceName: String?
        get() = prefs.getString(KEY_AUDIO_DEVICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_AUDIO_DEVICE_NAME, value).apply()

    var interpolationMethod: Int
        get() = prefs.getInt(KEY_INTERPOLATION, 4) // 4th Order default
        set(value) = prefs.edit().putInt(KEY_INTERPOLATION, value).apply()

    var maxPolyphony: Int
        get() = prefs.getInt(KEY_MAX_POLYPHONY, 64)
        set(value) = prefs.edit().putInt(KEY_MAX_POLYPHONY, value).apply()

    var velocityCurve: Int
        get() = prefs.getInt(KEY_VELOCITY_CURVE, 0) // 0 = Linear
        set(value) = prefs.edit().putInt(KEY_VELOCITY_CURVE, value).apply()

    var isSustainInverted: Boolean
        get() = prefs.getBoolean(KEY_SUSTAIN_INVERTED, false)
        set(value) = prefs.edit().putBoolean(KEY_SUSTAIN_INVERTED, value).apply()

    var masterLimiterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER_LIMITER, false)
        set(value) = prefs.edit().putBoolean(KEY_MASTER_LIMITER, value).apply()

    // --- MIDI Learn Mappings ---
    
    fun saveMidiMappings(mappings: List<MidiLearnMapping>) {
        val jsonArray = JSONArray()
        mappings.forEach { m ->
            jsonArray.put(JSONObject().apply {
                put("target", m.target.name)
                put("channelId", m.channelId)
                put("ccNumber", m.ccNumber)
                put("midiChannel", m.midiChannel)
            })
        }
        prefs.edit().putString(KEY_MIDI_LEARN_MAPPINGS, jsonArray.toString()).apply()
    }

    fun loadMidiMappings(): List<MidiLearnMapping> {
        val json = prefs.getString(KEY_MIDI_LEARN_MAPPINGS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                MidiLearnMapping(
                    target = MidiLearnTarget.valueOf(obj.getString("target")),
                    channelId = obj.getInt("channelId"),
                    ccNumber = obj.getInt("ccNumber"),
                    midiChannel = obj.getInt("midiChannel")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_BUFFER_SIZE = "buffer_size"
        private const val KEY_SAMPLE_RATE = "sample_rate"
        private const val KEY_MIDI_CHANNEL = "midi_channel"
        private const val KEY_SHOW_MASTER = "show_master"
        private const val KEY_MIDI_DEVICE_ID = "midi_device_id"
        private const val KEY_AUDIO_DEVICE_NAME = "audio_device_name"
        private const val KEY_ACTIVE_MIDI_DEVICES = "active_midi_devices"
        private const val KEY_MIDI_LEARN_MAPPINGS = "midi_learn_mappings"
        private const val KEY_INTERPOLATION = "interpolation_method"
        private const val KEY_MAX_POLYPHONY = "max_polyphony"
        private const val KEY_VELOCITY_CURVE = "velocity_curve"
        private const val KEY_SUSTAIN_INVERTED = "sustain_inverted"
        private const val KEY_MASTER_LIMITER = "master_limiter_enabled"
    }
}
