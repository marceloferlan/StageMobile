package com.example.stagemobile.domain.model

data class InstrumentChannel(
    val id: Int,
    val name: String,
    val volume: Float = 0.8f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isArmed: Boolean = false,
    val level: Float = 0f,
    val program: Int = 0,
    val bank: Int = 0,
    val sfId: Int = -1,
    val soundFont: String? = null,
    val minNote: Int = 0,
    val maxNote: Int = 127,
    val midiChannel: Int = -1, // -1 for ALL, 0..15 for specific channels
    val midiDeviceName: String? = null // null means "Listen to ALL active keyboards"
)
