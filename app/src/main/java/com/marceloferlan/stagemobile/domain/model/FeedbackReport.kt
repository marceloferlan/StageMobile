package com.marceloferlan.stagemobile.domain.model

data class FeedbackReport(
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userPhone: String = "",
    val type: String = "", // BUG, IMPROVEMENT, NEW_FEATURE
    val featureTag: String = "", // Audio Engine, UI, MIDI, etc.
    val details: String = "",
    val telemetry: TelemetryData = TelemetryData(),
    val timestamp: Long = System.currentTimeMillis()
)

data class TelemetryData(
    val appVersion: String = "",
    val androidVersion: String = "",
    val deviceModel: String = "",
    val freeMemoryInMb: Long = 0L,
    val audioDriverMode: String = "",
    val connectedMidiDevices: List<String> = emptyList(),
    val logcatSnippet: String = ""
)
