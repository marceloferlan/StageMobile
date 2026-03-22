package com.example.stagemobile.ui.mixer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stagemobile.R
import com.example.stagemobile.utils.UiUtils
import com.example.stagemobile.domain.model.InstrumentChannel
import com.example.stagemobile.midi.MidiDeviceState
import com.example.stagemobile.ui.components.MidiLearnState
import com.example.stagemobile.ui.components.midiLearnHalo
import com.example.stagemobile.ui.components.midiLearnClickable
import com.example.stagemobile.ui.components.rememberMidiLearnPulse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures


import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InstrumentChannelStrip(
    channel: InstrumentChannel,
    levelFlow: StateFlow<Float>,
    onVolumeChange: (Float) -> Unit,
    onOctaveDown: () -> Unit,
    onOctaveUp: () -> Unit,
    onArmToggle: () -> Unit,
    onNameClick: () -> Unit,
    onNameLongClick: () -> Unit,
    onMidiChannelClick: () -> Unit,
    onAdvancedOptionsClick: () -> Unit,
    activeMidiDevices: Set<String> = emptySet(),
    availableMidiDevices: List<MidiDeviceState> = emptyList(),
    isMidiLearnActive: Boolean = false,
    isLearnTargetFader: Boolean = false,
    isLearnTargetArm: Boolean = false,
    isLearnTargetOctaveUp: Boolean = false,
    isLearnTargetOctaveDown: Boolean = false,
    hasFaderMapping: Boolean = false,
    hasArmMapping: Boolean = false,
    hasOctaveUpMapping: Boolean = false,
    hasOctaveDownMapping: Boolean = false,
    onLearnFaderClick: () -> Unit = {},
    onLearnArmClick: () -> Unit = {},
    onLearnOctaveUpClick: () -> Unit = {},
    onLearnOctaveDownClick: () -> Unit = {},
    onLearnFaderLongClick: () -> Unit = {},
    onLearnArmLongClick: () -> Unit = {},
    onLearnOctaveUpLongClick: () -> Unit = {},
    onLearnOctaveDownLongClick: () -> Unit = {},
    onColorChange: (Long?) -> Unit = {},
    onRemoveClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isTablet = UiUtils.rememberIsTablet()

    // MIDI Learn pulse animation (centralized)
    val pulseAlpha = rememberMidiLearnPulse(isMidiLearnActive)

    // MIDI Learn states for each learnable component
    val armLearnState = MidiLearnState(isMidiLearnActive, isLearnTargetArm, hasArmMapping)
    val octaveDownLearnState = MidiLearnState(isMidiLearnActive, isLearnTargetOctaveDown, hasOctaveDownMapping)
    val octaveUpLearnState = MidiLearnState(isMidiLearnActive, isLearnTargetOctaveUp, hasOctaveUpMapping)


    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 2.5.dp, vertical = 4.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.4f)) // Camada "Fumê" (Escurecendo)
            .background(
                color = if (channel.color != null) Color(channel.color).copy(alpha = 0.2f) else Color(0xFF242424)
            )
            .then(
                if (channel.color != null) {
                    Modifier.border(
                        width = 1.dp,
                        color = Color(channel.color).copy(alpha = 0.6f), // HUD Border over solid
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // Desabilitado a pedido do usuário (iluminação indesejada no toque)
                onClick = { /* No action on simple tap */ },
                onLongClick = { onRemoveClick() }
            )
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp)
    ) {
        // Derived panel background color: darkened channel color or default graphite
        val panelBgColor = if (channel.color != null) {
            Color(channel.color).copy(alpha = 0.15f).compositeOver(Color.Black)
        } else {
            Color(0xFF2C2C2C)
        }

        val fullSf2Name = channel.soundFont?.substringAfterLast('/')
        val hasSf2 = fullSf2Name != null
        
        // Split into two parts if possible: "File.sf2" and "[Preset Name]"
        val baseSf2Name = fullSf2Name?.substringBefore(" [") ?: (fullSf2Name ?: channel.name)
        val presetName = fullSf2Name?.substringAfter(" [", "")?.removeSuffix("]")
        val isPresetEmpty = presetName.isNullOrEmpty()

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            // No name surface height adjustment based on isTablet
            val nameHeight = if (isTablet) 62.dp else 40.dp
            val isArmed = channel.isArmed

            Column(modifier = Modifier.fillMaxWidth()) {
                // ARM STRIP — thin stylish bar at top of name panel
                val armStripColor = if (isArmed) Color(0xFF81C784) else Color(0xFF424242)

                val armStripShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(armStripShape)
                        .background(armStripColor)
                        .midiLearnHalo(armLearnState, armStripShape, 1.5.dp, pulseAlpha)
                        .midiLearnClickable(armLearnState, onLearnArmClick, onLearnArmLongClick, onClick = { onArmToggle() })
                )

                // NAME SURFACE (also acts as ARM learn target)
                val namePanelShape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(nameHeight)
                        .clip(namePanelShape)
                        .midiLearnHalo(armLearnState, namePanelShape, pulseAlpha = pulseAlpha)
                        .then(
                            if (!isMidiLearnActive && channel.color != null) {
                                Modifier.border(1.dp, Color(channel.color).copy(alpha = 0.4f), namePanelShape)
                            } else Modifier
                        )
                        .pointerInput(armLearnState, channel.soundFont) {
                            detectTapGestures(
                                onTap = {
                                    if (armLearnState.isActive) onLearnArmClick() else onArmToggle()
                                },
                                onLongPress = {
                                    if (armLearnState.isActive && armLearnState.hasMapping) onLearnArmLongClick()
                                    else onNameLongClick()
                                }
                            )
                        },
                    color = panelBgColor,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Line 1: SF2 File Name
                        Text(
                            text = baseSf2Name,
                            style = if (isTablet) MaterialTheme.typography.labelSmall else TextStyle(fontSize = 9.sp),
                            fontWeight = if (isPresetEmpty) FontWeight.Bold else FontWeight.Normal,
                            color = if (hasSf2) Color(0xFFAAAAAA) else Color(0xFFB0BEC5),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // Line 2: Preset Name (if exists)
                        if (!isPresetEmpty) {
                            Text(
                                text = presetName!!,
                                style = if (isTablet) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784), // Vivid Green for the active sound
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))

        // FADER + METER with MIDI Learn Halo on Fader
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = if (isMidiLearnActive) {
                        Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onLearnFaderClick() },
                            onLongClick = { if (hasFaderMapping) onLearnFaderLongClick() }
                        )
                    } else Modifier
                ) {
                    VerticalFader(
                        value = channel.volume,
                        onValueChange = if (isMidiLearnActive) { _ -> } else onVolumeChange,
                        modifier = Modifier,
                        isMidiLearnActive = isMidiLearnActive,
                        isLearnTarget = isLearnTargetFader,
                        hasMidiMapping = hasFaderMapping,
                        accentColor = if (channel.color != null) Color(channel.color) else null
                    )
                }

            Spacer(modifier = Modifier.width(4.dp))

            // Peak Meter Masked (High Performance)
            BoxWithConstraints(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .padding(top = 6.dp)
                    .border(width = 1.dp, color = Color(0xFF333333), shape = RoundedCornerShape(4.dp))
                    .padding(1.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black)
            ) {
                // Read StateFlow HERE so only this Box recomposes 30fps!
                val level by levelFlow.collectAsState()

                // 1. The Actual Audio Level (Solid Green/Yellow/Red)
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val barHeight = size.height * level.coerceIn(0f, 1f)
                    
                    val brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.0f to Color(0xFFFF3B30),   // Red (Peak/Clip)
                        0.09f to Color(0xFFFF3B30),  // 0dB mark (1.0 - 0.91 = 0.09)
                        0.28f to Color(0xFFFFCC00),  // -12dB mark (1.0 - 0.72 = 0.28)
                        0.60f to Color(0xFF4CAF50),  // Green
                        1.0f to Color(0xFF4CAF50)
                    )

                    drawRect(
                        brush = brush,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(size.width, barHeight)
                    )
                }

                // 2. The Mask Layer (Dynamic 30 or 60 segments)
                val maskRes = if (isTablet) R.drawable.meter_mask_channel_60 else R.drawable.meter_mask_channel_30
                Image(
                    painter = painterResource(id = maskRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
            } // Row
        } // Box (FADER + METER)

        Spacer(modifier = Modifier.height(8.dp))

        // OCTAVE CONTROL
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            val octave = channel.octaveShift
            val isModified = octave != 0
            val modifiedColor = Color(0xFF81C784)
            val defaultColor = Color.White

            // OCTAVE DOWN BUTTON
            val octBtnShape = RoundedCornerShape(8.dp)

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(if (isTablet) 34.2.dp else 29.dp)
                    .clip(octBtnShape)
                    .then(
                        if (channel.color != null) {
                            Modifier.border(1.1.dp, Color(channel.color).copy(alpha = 0.5f), octBtnShape)
                        } else Modifier
                    )
                    .midiLearnHalo(octaveDownLearnState, octBtnShape, pulseAlpha = pulseAlpha)
                    .midiLearnClickable(
                        octaveDownLearnState, onLearnOctaveDownClick, onLearnOctaveDownLongClick,
                        onClick = { onOctaveDown() }
                    ),
                color = panelBgColor,
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "−",
                        color = if (octave < 0) modifiedColor else defaultColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // OCTAVE DISPLAY
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .height(if (isTablet) 34.2.dp else 29.dp)
                    .background(panelBgColor, RoundedCornerShape(8.dp))
                    .then(
                        if (channel.color != null) {
                            Modifier.border(
                                width = 1.dp,
                                color = Color(channel.color).copy(alpha = 0.35f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                val displayText = when {
                    octave > 0 -> "Oct +$octave"
                    octave < 0 -> "Oct $octave"
                    else -> "Oct 0"
                }
                Text(
                    text = displayText,
                    color = if (isModified) modifiedColor else defaultColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTablet) 11.sp else 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Visible
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // OCTAVE UP BUTTON
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(if (isTablet) 34.2.dp else 29.dp)
                    .clip(octBtnShape)
                    .then(
                        if (channel.color != null) {
                            Modifier.border(1.1.dp, Color(channel.color).copy(alpha = 0.5f), octBtnShape)
                        } else Modifier
                    )
                    .midiLearnHalo(octaveUpLearnState, octBtnShape, pulseAlpha = pulseAlpha)
                    .midiLearnClickable(
                        octaveUpLearnState, onLearnOctaveUpClick, onLearnOctaveUpLongClick,
                        onClick = { onOctaveUp() }
                    ),
                color = panelBgColor,
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+",
                        color = if (octave > 0) modifiedColor else defaultColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp)) // Espaçador superior balanceador

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(if (isTablet) 56.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isTablet) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(48.dp)
                        .background(panelBgColor, RoundedCornerShape(8.dp))
                        .then(
                            if (channel.color != null) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = Color(channel.color).copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            } else Modifier
                        )
                        .padding(horizontal = 4.dp)
                ) {
                    val deviceValue = channel.midiDeviceName
                    val isOmni = deviceValue == null
                    
                    // Filter active devices that are also physically connected
                    val connectedActiveDevices = availableMidiDevices
                        .filter { it.isConnected && activeMidiDevices.contains(it.name) }
                        .map { it.name }

                    val isActive = if (isOmni) {
                        connectedActiveDevices.isNotEmpty()
                    } else {
                        // For specific device, must be connected AND active in settings
                        availableMidiDevices.any { it.name == deviceValue && it.isConnected } && activeMidiDevices.contains(deviceValue)
                    }
                    
                    val statusText = when {
                        isOmni -> {
                            when (connectedActiveDevices.size) {
                                0 -> "NENHUM"
                                1 -> connectedActiveDevices.first()
                                else -> "OMNI (${connectedActiveDevices.size})"
                            }
                        }
                        isActive -> deviceValue!!
                        else -> "${deviceValue!!} (OFFLINE)"
                    }
                    
                    val statusColor = if (isActive) Color(0xFF81C784) else Color(0xFFEF5350)
                    val channelValue = if (channel.midiChannel == -1) "OMNI" else "${channel.midiChannel + 1}"

                    Text(
                        text = "Controlador: $statusText",
                        color = statusColor,
                        fontSize = 8.sp,
                        lineHeight = 10.sp, // Sincronizado
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                    Text(
                        text = "CANAL MIDI: $channelValue",
                        color = Color(0xFFAAAAAA),
                        fontSize = 8.sp,
                        lineHeight = 10.sp, // Sincronizado
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }
        }

        // BOTTOM SPACER - further reduced
        // Spacer(modifier = Modifier.height(4.dp))
    }
}
