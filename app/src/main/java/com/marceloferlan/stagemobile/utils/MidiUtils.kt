package com.marceloferlan.stagemobile.utils

fun getMidiNoteName(note: Int): String {
    val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val name = noteNames[note % 12]
    val octave = (note / 12) - 1 // C4 = 60
    return "$name$octave"
}
