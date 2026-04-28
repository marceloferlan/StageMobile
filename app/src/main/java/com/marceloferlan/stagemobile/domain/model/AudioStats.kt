package com.marceloferlan.stagemobile.domain.model

data class AudioStats(
    val underruns: Int = 0,
    val mutexMisses: Int = 0,
    val clips: Int = 0,
    val avgCallbackUs: Float = 0f,
    val maxCallbackUs: Float = 0f,
    val activeVoices: Int = 0,
    // Per-phase timing (avg / max em µs)
    val avgPhaseFluidUs: Float = 0f,
    val maxPhaseFluidUs: Float = 0f,
    val avgPhaseDspChanUs: Float = 0f,
    val maxPhaseDspChanUs: Float = 0f,
    val avgPhaseDspMasterUs: Float = 0f,
    val maxPhaseDspMasterUs: Float = 0f,
    val avgPhaseMixUs: Float = 0f,
    val maxPhaseMixUs: Float = 0f
)
