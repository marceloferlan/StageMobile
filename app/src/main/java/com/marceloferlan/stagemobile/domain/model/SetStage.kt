package com.marceloferlan.stagemobile.domain.model

data class SetStage(
    val id: String = "",
    val bankId: Int = 1,
    val slotId: Int = 1,
    val name: String = "",
    val channels: List<InstrumentChannel> = emptyList(),
    val masterVolume: Float = 0.8f,
    val globalOctaveShift: Int = 0,
    val globalTransposeShift: Int = 0,
    val isMasterLimiterEnabled: Boolean = false,
    val isDspMasterBypass: Boolean = false
)
