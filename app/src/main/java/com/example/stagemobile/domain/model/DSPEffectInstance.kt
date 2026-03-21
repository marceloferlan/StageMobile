package com.example.stagemobile.domain.model

enum class DSPParamType(val label: String, val unit: String) {
    // EQ
    LOW_GAIN("LOW-GAIN", "dB"),
    LOW_FREQ("LOW-FREQ", "Hz"),
    MID_GAIN("MID-GAIN", "dB"),
    MID_FREQ("MID-FREQ", "Hz"),
    MID_Q("MID-Q", "Q"),
    HIGH_GAIN("HIGH-GAIN", "dB"),
    HIGH_FREQ("HIGH-FREQ", "Hz"),
    OUTPUT_GAIN("OUT", "dB"),

    // Filters
    CUTOFF_FREQ("FREQ", "Hz"),
    RESONANCE("Q", "Q"),

    // Delay
    DELAY_TIME("TIME", "ms"),
    DELAY_FEEDBACK("FEEDBACK", "%"),
    DELAY_MIX("MIX", "%"),

    // Reverb
    REVERB_ROOM("ROOM", "%"),
    REVERB_DAMP("DAMPING", "%"),
    REVERB_MIX("MIX", "%"),

    // Modulation (Chorus/Tremolo)
    MOD_RATE("RATE", "Hz"),
    MOD_DEPTH("DEPTH", "%"),
    MOD_MIX("MIX", "%"),

    // Dynamics
    THRESHOLD("THRESHOLD", "dB"),
    RATIO("RATIO", "Ratio"),
    ATTACK("ATTACK", "ms"),
    RELEASE("RELEASE", "ms"),
    MAKEUP_GAIN("MAKE-UP", "dB"),
    KNEE("KNEE", "dB"),
    MIX("MIX", "%"),

    // Utils
    SEND_LEVEL("SEND LEVEL", "%")
}

enum class DSPEffectType(val id: Int, val label: String, val params: List<DSPParamType>, val priority: Int) {
    EQ_PARAMETRIC(0, "Equalizador", listOf(
        DSPParamType.LOW_GAIN, DSPParamType.LOW_FREQ,
        DSPParamType.MID_GAIN, DSPParamType.MID_FREQ, DSPParamType.MID_Q,
        DSPParamType.HIGH_GAIN, DSPParamType.HIGH_FREQ,
        DSPParamType.OUTPUT_GAIN
    ), 4),
    HPF(1, "High Pass", listOf(DSPParamType.CUTOFF_FREQ, DSPParamType.RESONANCE), 1),
    LPF(2, "Low Pass", listOf(DSPParamType.CUTOFF_FREQ, DSPParamType.RESONANCE), 2),
    DELAY(3, "Delay", listOf(DSPParamType.DELAY_TIME, DSPParamType.DELAY_FEEDBACK, DSPParamType.DELAY_MIX), 7),
    REVERB(4, "Reverb", listOf(DSPParamType.REVERB_ROOM, DSPParamType.REVERB_DAMP, DSPParamType.REVERB_MIX), 8),
    CHORUS(5, "Chorus", listOf(DSPParamType.MOD_RATE, DSPParamType.MOD_DEPTH, DSPParamType.MOD_MIX), 5),
    TREMOLO(6, "Tremolo", listOf(DSPParamType.MOD_RATE, DSPParamType.MOD_DEPTH, DSPParamType.MOD_MIX), 6),
    COMPRESSOR(7, "Compressor", listOf(
        DSPParamType.THRESHOLD, DSPParamType.RATIO, 
        DSPParamType.ATTACK, DSPParamType.RELEASE, 
        DSPParamType.MAKEUP_GAIN, DSPParamType.KNEE, 
        DSPParamType.MIX
    ), 3),
    LIMITER(8, "Limiter", listOf(DSPParamType.THRESHOLD, DSPParamType.RELEASE), 10),
    REVERB_SEND(9, "Envio Reverb", listOf(DSPParamType.SEND_LEVEL), 9)
}

data class DSPEffectInstance(
    val id: String, // Unique UUID for UI tracking
    val type: DSPEffectType,
    val isEnabled: Boolean = true,
    val params: Map<Int, Float> = emptyMap() // Map of paramId to value
)
