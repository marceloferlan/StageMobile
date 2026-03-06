package com.example.stagemobile.ui.mixer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stagemobile.R
import com.example.stagemobile.domain.model.InstrumentChannel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelStrip(
    channel: InstrumentChannel,
    onVolumeChange: (Float) -> Unit,
    onMinNoteClick: () -> Unit,
    onMaxNoteClick: () -> Unit,
    onArmToggle: () -> Unit,
    onNameClick: () -> Unit,
    onNameLongClick: () -> Unit,
    onMidiChannelClick: () -> Unit,
    onAdvancedOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 2.5.dp, vertical = 5.dp)
            .fillMaxHeight()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {

        // Channel Name — clickable to load SF2
        val displayName = channel.soundFont
            ?.substringAfterLast('/')
            ?: channel.name
        val hasSf2 = channel.soundFont != null

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(31.dp)
                .clip(RoundedCornerShape(6.dp))
                .combinedClickable(
                    onClick = { onNameClick() },
                    onLongClick = { if (hasSf2) onNameLongClick() }
                ),
            color = Color(0xFF2C2C2C),
            tonalElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (hasSf2) Color(0xFF81C784) else Color(0xFFB0BEC5),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        // ARM & MODE VERT
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            // ARM Button
            Image(
                painter = painterResource(
                    id = if (channel.isArmed) R.drawable.arm_laranja_ligado else R.drawable.arm_laranja_desligado
                ),
                contentDescription = "ARM Button",
                modifier = Modifier
                    .weight(1f)
                    .height(28.5.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onArmToggle() }
                    ),
                contentScale = ContentScale.FillBounds
            )

            Spacer(modifier = Modifier.width(6.dp))

            // ADVANCED OPTIONS BUTTON
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(30.6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAdvancedOptionsClick() },
                color = Color(0xFF2C2C2C),
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opções do Canal",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // FADER + METER
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center // Centers the fixed-width items
        ) {
            VerticalFader(
                value = channel.volume,
                onValueChange = onVolumeChange,
                // Removed .weight() so it uses its internal 78.dp fixed width and centers correctly
                modifier = Modifier
            )

            Spacer(modifier = Modifier.width(4.dp))

            // VU Meter
            Column(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                val ledCount = 30
                // Convert channel.level (0f..1f) into number of lit LEDs
                val activeLeds = (channel.level * ledCount).toInt().coerceIn(0, ledCount)

                // Loop from top (i = 0) to bottom (i = 29)
                for (i in 0 until ledCount) {
                    val ledIndexFromBottom = ledCount - 1 - i
                    val isLit = ledIndexFromBottom < activeLeds

                    // Determine LED base color depending on its height zone
                    val ledColor = when {
                        !isLit -> Color(0xFF2C2C2C) // Unlit LED / Off State
                        i < 4 -> Color(0xFFFF3B30) // Red zone (top)
                        i < 10 -> Color(0xFFFFCC00) // Yellow zone (middle-top)
                        else -> Color(0xFF4CAF50) // Green zone (bottom)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 2.dp, vertical = 0.5.dp)
                            .background(ledColor, RoundedCornerShape(1.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // KEY RANGE SELECTORS
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            val isMinModified = channel.minNote > 0
            val isMaxModified = channel.maxNote < 127
            val modifiedColor = Color(0xFF81C784)
            val defaultColor = Color.White

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(34.2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMinNoteClick() },
                color = Color(0xFF2C2C2C),
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = getMidiNoteName(channel.minNote),
                        color = if (isMinModified) modifiedColor else defaultColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(34.2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMaxNoteClick() },
                color = Color(0xFF2C2C2C),
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = getMidiNoteName(channel.maxNote),
                        color = if (isMaxModified) modifiedColor else defaultColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // BOTTOM SPACER to replace removed ADVANCED OPTIONS Button
        Spacer(modifier = Modifier.height(36.dp))
    }
}

fun getMidiNoteName(note: Int): String {
    val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val name = noteNames[note % 12]
    val octave = (note / 12) - 1 // C4 = 60 standard
    return "$name$octave"
}

