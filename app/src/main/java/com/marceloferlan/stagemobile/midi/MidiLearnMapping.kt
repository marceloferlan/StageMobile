package com.marceloferlan.stagemobile.midi

/**
 * Represents the type of UI control that can be mapped via MIDI Learn.
 */
enum class MidiLearnTarget {
    FADER,
    ARM,
    OCTAVE_UP,
    OCTAVE_DOWN,
    TRANSPOSE_UP,
    TRANSPOSE_DOWN,
    DSP_PARAM,
    TAP_DELAY,
    SET_STAGE_SLOT,
    NEXT_BANK,
    PREVIOUS_BANK,
    FAVORITE_SET
}

/**
 * Identifies a specific UI control awaiting MIDI Learn assignment.
 */
data class MidiLearnTargetInfo(
    val target: MidiLearnTarget,
    val channelId: Int,
    val effectId: String? = null,
    val paramId: Int? = null,
    val slotIndex: Int? = null
)

/**
 * A persisted MIDI CC → UI control mapping.
 */
data class MidiLearnMapping(
    val target: MidiLearnTarget,
    val channelId: Int,
    val ccNumber: Int,
    val midiChannel: Int, // MIDI channel the CC was received on
    val deviceName: String? = null, // Optional device filter
    val effectId: String? = null,
    val paramId: Int? = null,
    val slotIndex: Int? = null
)
