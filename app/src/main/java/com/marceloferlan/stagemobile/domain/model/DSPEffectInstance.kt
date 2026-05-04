package com.marceloferlan.stagemobile.domain.model

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

/**
 * Subdivisões rítmicas para sync do Delay com o TAP BPM.
 * O multiplicador é aplicado sobre a duração de 1 tempo (semínima):
 *   delay_ms = (60000 / BPM) × multiplicador
 */
enum class DelaySubdivision(val label: String, val multiplier: Float) {
    WHOLE("1/1", 4.0f),           // Semibreve (4 tempos)
    HALF_DOT("1/2.", 3.0f),       // Mínima pontuada
    HALF("1/2", 2.0f),            // Mínima (2 tempos)
    QUARTER_DOT("1/4.", 1.5f),    // Semínima pontuada
    QUARTER("1/4", 1.0f),         // Semínima (1 tempo) — padrão
    EIGHTH_DOT("1/8.", 0.75f),    // Colcheia pontuada
    EIGHTH("1/8", 0.5f),          // Colcheia
    EIGHTH_TRIP("1/8T", 0.333f),  // Tercina de colcheia
    SIXTEENTH("1/16", 0.25f),     // Semicolcheia
    SIXTEENTH_TRIP("1/16T", 0.167f) // Tercina de semicolcheia
}

data class DSPEffectInstance(
    val id: String, // Unique UUID for UI tracking
    val type: DSPEffectType,
    val isEnabled: Boolean = true,
    val params: Map<Int, Float> = emptyMap(), // Map of paramId to value
    val delaySubdivision: DelaySubdivision = DelaySubdivision.QUARTER // Só usado para DELAY
)
