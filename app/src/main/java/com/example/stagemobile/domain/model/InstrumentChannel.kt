package com.example.stagemobile.domain.model

data class InstrumentChannel(
    val id: Int,
    val name: String,
    val volume: Float = 0.8f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isArmed: Boolean = false,
    val program: Int = 0,
    val bank: Int = 0,
    val sfId: Int = -1,
    val soundFont: String? = null,
    val minNote: Int = 0,
    val maxNote: Int = 127,
    val midiChannel: Int = -1, // -1 for ALL, 0..15 for specific channels
    val midiDeviceName: String? = null, // null means "Listen to ALL active keyboards"
    val velocityCurve: Int = -1, // -1 = use global, 0..6 = per-channel curve
    val octaveShift: Int = 0, // -4..+4, each unit = 12 semitones
    val transposeShift: Int = 0, // -12..+12, each unit = 1 semitone
    
    // Configurações e Filtros MIDI Avançados
    val sustainEnabled: Boolean = true, // CC 64
    val modulationEnabled: Boolean = true, // CC 1
    val expressionEnabled: Boolean = false, // CC 11
    val pitchBendEnabled: Boolean = true, // Pitch Bend
    val footControllerEnabled: Boolean = false, // CC 4
    val color: Long? = null, // null uses default surface color
    val dspEffects: List<DSPEffectInstance> = emptyList()
)
