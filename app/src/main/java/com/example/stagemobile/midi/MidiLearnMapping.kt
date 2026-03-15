package com.example.stagemobile.midi

/**
 * Represents the type of UI control that can be mapped via MIDI Learn.
 */
enum class MidiLearnTarget {
    FADER,
    ARM,
    OCTAVE_UP,
    OCTAVE_DOWN,
    TRANSPOSE_UP,
    TRANSPOSE_DOWN
}

/**
 * Identifies a specific UI control awaiting MIDI Learn assignment.
 */
data class MidiLearnTargetInfo(
    val target: MidiLearnTarget,
    val channelId: Int
)

/**
 * A persisted MIDI CC → UI control mapping.
 */
data class MidiLearnMapping(
    val target: MidiLearnTarget,
    val channelId: Int,
    val ccNumber: Int,
    val midiChannel: Int, // MIDI channel the CC was received on
    val deviceName: String? = null // Optional device filter
)
