package com.marceloferlan.stagemobile.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * Virtual piano keyboard — 2 octaves (C3-B4)
 */
@Composable
fun VirtualKeyboard(
    onNoteOn: (midiNote: Int) -> Unit,
    onNoteOff: (midiNote: Int) -> Unit,
    modifier: Modifier = Modifier,
    startOctave: Int = 3,
    octaves: Int = 2
) {
    val whiteKeyWidth = 44.dp
    val whiteKeyHeight = 120.dp
    val blackKeyWidth = 28.dp
    val blackKeyHeight = 75.dp

    // White key pattern per octave: C, D, E, F, G, A, B
    // Black key offsets from each white key (semitone positions)
    data class KeyInfo(val note: Int, val isBlack: Boolean, val name: String)

    val allKeys = mutableListOf<KeyInfo>()
    val whiteKeys = mutableListOf<KeyInfo>()
    val blackKeys = mutableListOf<KeyInfo>()

    for (oct in 0 until octaves) {
        val baseNote = (startOctave + oct) * 12 + 12 // MIDI: C3 = 48
        // White keys
        whiteKeys.add(KeyInfo(baseNote + 0, false, "C${startOctave + oct}"))   // C
        whiteKeys.add(KeyInfo(baseNote + 2, false, "D"))   // D
        whiteKeys.add(KeyInfo(baseNote + 4, false, "E"))   // E
        whiteKeys.add(KeyInfo(baseNote + 5, false, "F"))   // F
        whiteKeys.add(KeyInfo(baseNote + 7, false, "G"))   // G
        whiteKeys.add(KeyInfo(baseNote + 9, false, "A"))   // A
        whiteKeys.add(KeyInfo(baseNote + 11, false, "B"))  // B
        // Black keys
        blackKeys.add(KeyInfo(baseNote + 1, true, "C#"))   // C#
        blackKeys.add(KeyInfo(baseNote + 3, true, "D#"))   // D#
        blackKeys.add(KeyInfo(baseNote + 6, true, "F#"))   // F#
        blackKeys.add(KeyInfo(baseNote + 8, true, "G#"))   // G#
        blackKeys.add(KeyInfo(baseNote + 10, true, "A#"))  // A#
    }

    // Black key positions relative to white keys
    // In each octave, black keys sit between: C-D, D-E, F-G, G-A, A-B
    val blackKeyPositions = mutableListOf<Int>() // index of white key to the LEFT
    for (oct in 0 until octaves) {
        val offset = oct * 7
        blackKeyPositions.add(offset + 0) // C# between C(0) and D(1)
        blackKeyPositions.add(offset + 1) // D# between D(1) and E(2)
        blackKeyPositions.add(offset + 3) // F# between F(3) and G(4)
        blackKeyPositions.add(offset + 4) // G# between G(4) and A(5)
        blackKeyPositions.add(offset + 5) // A# between A(5) and B(6)
    }

    // Total width based on actual keys: 14 white keys × 44dp
    val totalWhiteKeys = octaves * 7
    val totalKeyboardWidth = (whiteKeyWidth * totalWhiteKeys) + 8.dp

    val density = LocalDensity.current
    val whiteKeyWidthPx = with(density) { whiteKeyWidth.toPx() }
    val blackKeyWidthPx = with(density) { blackKeyWidth.toPx() }
    val blackKeyHeightPx = with(density) { blackKeyHeight.toPx() }

    // State to hold currently pressed notes
    val pressedNotes = remember { mutableStateListOf<Int>() }
    
    // Helper to find note at (x, y) relative to the content area
    val getNoteAt = { x: Float, y: Float ->
        var foundNote: Int? = null
        
        // Bounds check first
        val totalWidthPx = with(density) { (whiteKeyWidth * totalWhiteKeys).toPx() }
        val totalHeightPx = with(density) { whiteKeyHeight.toPx() }
        if (x in 0f..totalWidthPx && y in 0f..totalHeightPx) {
            // Check black keys first (they overlap white keys)
            if (y <= blackKeyHeightPx) {
                blackKeyPositions.forEachIndexed { index, whiteIndex ->
                    if (index < blackKeys.size) {
                        val xOffsetPx = (whiteKeyWidthPx * (whiteIndex + 1)) - (blackKeyWidthPx / 2f)
                        if (x >= xOffsetPx && x <= xOffsetPx + blackKeyWidthPx) {
                            foundNote = blackKeys[index].note
                        }
                    }
                }
            }
            
            // If not on a black key, check white keys
            if (foundNote == null) {
                val whiteIndex = (x / whiteKeyWidthPx).toInt()
                if (whiteIndex in whiteKeys.indices) {
                    foundNote = whiteKeys[whiteIndex].note
                }
            }
        }
        foundNote
    }

    Box(
        modifier = modifier
            .requiredWidth(totalKeyboardWidth)
            .height(whiteKeyHeight + 8.dp)
            .background(Color(0xFF0D0D0D))
            .padding(4.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    val activePointers = mutableMapOf<PointerId, Int>() // pointerId -> midiNote
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        
                        for (change in event.changes) {
                            if (change.pressed) {
                                val pos = change.position
                                val note = getNoteAt(pos.x, pos.y)
                                
                                val prevNote = activePointers[change.id]
                                
                                if (note != prevNote) {
                                    if (prevNote != null) {
                                        activePointers.remove(change.id)
                                        if (!activePointers.values.contains(prevNote)) {
                                            pressedNotes.remove(prevNote)
                                            onNoteOff(prevNote)
                                        }
                                    }
                                    
                                    if (note != null) {
                                        activePointers[change.id] = note
                                        if (!pressedNotes.contains(note)) {
                                            pressedNotes.add(note)
                                            onNoteOn(note)
                                        }
                                    }
                                }
                                change.consume()
                            } else {
                                // Pointer went up
                                val prevNote = activePointers.remove(change.id)
                                if (prevNote != null) {
                                    if (!activePointers.values.contains(prevNote)) {
                                        pressedNotes.remove(prevNote)
                                        onNoteOff(prevNote)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // White keys
        Row {
            whiteKeys.forEachIndexed { index, key ->
                val pressed = pressedNotes.contains(key.note)

                Box(
                    modifier = Modifier
                        .width(whiteKeyWidth)
                        .height(whiteKeyHeight)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(if (pressed) Color(0xFFBBDEFB) else Color.White),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (key.name.startsWith("C")) {
                        Text(
                            text = key.name,
                            fontSize = 9.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }

        // Black keys overlay
        blackKeyPositions.forEachIndexed { index, whiteIndex ->
            if (index < blackKeys.size) {
                val key = blackKeys[index]
                val pressed = pressedNotes.contains(key.note)

                // Position exactly at the boundary between current white key and the next
                val xOffset = (whiteKeyWidth * (whiteIndex + 1)) - (blackKeyWidth / 2)

                Box(
                    modifier = Modifier
                        .offset(x = xOffset)
                        .width(blackKeyWidth)
                        .height(blackKeyHeight)
                        .zIndex(1f)
                        .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(if (pressed) Color(0xFF424242) else Color(0xFF1A1A1A))
                )
            }
        }
    }
}
