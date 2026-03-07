package com.example.stagemobile.ui.mixer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
            .padding(horizontal = 2.5.dp, vertical = 4.dp)
            .fillMaxHeight()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)
    ) {

        // Channel Name — clickable to load SF2
        val displayName = channel.soundFont
            ?.substringAfterLast('/')
            ?: channel.name
        val hasSf2 = channel.soundFont != null

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val heightPx = constraints.maxHeight.toFloat()
            val density = LocalDensity.current.density
            val isLargeScreen = heightPx >= 400f * density

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLargeScreen) 62.dp else 31.dp)
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
                        style = if (isLargeScreen) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasSf2) Color(0xFF81C784) else Color(0xFFB0BEC5),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        // ARM & Advanced Options
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            // ARM Button (Donut Style)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.6.dp),
                contentAlignment = Alignment.Center
            ) {
                val isArmed = channel.isArmed
                val vividAmber = Color(0xFFFFC107) // Amarelo Vivo / Amber
                val glowColor = if (isArmed) vividAmber else Color.Gray

                // External Glow Layer (only when active/armed)
                if (isArmed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(radius = 4.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                            .background(vividAmber.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onArmToggle() }
                        ),
                    color = Color(0xFF2C2C2C),
                    tonalElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            if (isArmed) {
                                // Internal Glow
                                drawCircle(
                                    color = vividAmber.copy(alpha = 0.4f),
                                    radius = size.minDimension / 1.8f,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                                )
                            }

                            drawCircle(
                                color = glowColor,
                                radius = size.minDimension / 2.2f,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

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
                        imageVector = Icons.Outlined.Construction,
                        contentDescription = "Configurações Avançadas",
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
            BoxWithConstraints(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .padding(top = 6.dp)
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp)
            ) {
                val heightPx = constraints.maxHeight.toFloat()
                val density = LocalDensity.current.density
                
                // 60 LEDs for large screens (tablets), 30 for small screens
                // Using pixels directly to avoid Dp comparison compiler issues
                val ledCount = if (heightPx >= 400f * density) 60 else 30
                
                // Convert channel.level (0f..1f) into number of lit LEDs
                val activeLeds = (channel.level * ledCount).toInt().coerceIn(0, ledCount)

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Loop from top (i = 0) to bottom
                    for (i in 0 until ledCount) {
                        val ledIndexFromBottom = ledCount - 1 - i
                        val isLit = ledIndexFromBottom < activeLeds

                        // Proportional color zones: 
                        // Top 13% Red, next 20% Yellow, rest Green
                        val redLimit = (ledCount * 0.13f).toInt()
                        val yellowLimit = (ledCount * 0.33f).toInt()

                        val ledColor = when {
                            !isLit -> Color(0xFF2C2C2C)
                            i < redLimit -> Color(0xFFFF3B30) // Red zone
                            i < yellowLimit -> Color(0xFFFFCC00) // Yellow zone
                            else -> Color(0xFF4CAF50) // Green zone
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
        }

        Spacer(modifier = Modifier.height(12.dp))

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

        // MIDI STATUS PANEL - TABLET ONLY
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val heightPx = constraints.maxHeight.toFloat()
            val density = LocalDensity.current.density
            val isLargeScreen = heightPx >= 400f * density

            if (isLargeScreen) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center) // Perfeita centralização
                        .padding(top = 8.dp)
                        .fillMaxWidth(0.95f)
                        .background(Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    val deviceValue = channel.midiDeviceName ?: "Todos"
                    val channelValue = if (channel.midiChannel == -1) "OMNI" else "${channel.midiChannel + 1}"

                    Text(
                        text = "MIDI DEVICES: $deviceValue",
                        color = Color(0xFF81C784),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "CANAL MIDI: $channelValue",
                        color = Color(0xFFAAAAAA),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // BOTTOM SPACER - further reduced
        Spacer(modifier = Modifier.height(4.dp))
    }
}

fun getMidiNoteName(note: Int): String {
    val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val name = noteNames[note % 12]
    val octave = (note / 12) - 1 // C4 = 60 standard
    return "$name$octave"
}

