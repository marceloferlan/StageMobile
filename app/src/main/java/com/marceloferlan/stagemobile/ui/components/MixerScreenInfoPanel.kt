package com.marceloferlan.stagemobile.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marceloferlan.stagemobile.utils.UiUtils

@Composable
fun MixerScreenInfoPanel(
    modifier: Modifier = Modifier,
    ramUsage: Int = 0,
    cpuUsage: Int = 0,
    activeSet: String = "STAGE SET ALPHA",
    midiStatus: String = "OMNI / ALL DEVICES",
    lastEvent: String = "",
    audioInterface: String = "Buscando...",
    sampleRate: Int = 44100,
    isError: Boolean = false,
    isTablet: Boolean = UiUtils.rememberIsTablet(),
    midiLearnFeedback: String? = null,
    onActiveSetLongClick: () -> Unit = {}
) {
    val lcdBackground = Brush.verticalGradient(
        colors = listOf(Color(0xFF0A0A0A), Color(0xFF1A1A1A))
    )
    
    val cyanNeon = Color(0xFF00E5FF)
    val errorRed = Color(0xFFFF5252)
    val displayColor = if (isError) errorRed else cyanNeon

    // Adaptative sizes
    // Adaptative sizes - Extreme reduction for Mobile (30dp)
    val panelHeight = if (isTablet) 64.dp else 30.dp
    val labelSize = if (isTablet) 9.sp else 8.sp
    val valueSize1 = if (isTablet) 14.sp else 10.sp
    val valueSize2 = if (isTablet) 13.sp else 9.sp
    val valueSize3 = if (isTablet) 12.sp else 9.sp

    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight),
        color = Color.Black
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Divider (Horizontal Line)
            androidx.compose.material3.HorizontalDivider(color = Color(0xFF333333))
            
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(lcdBackground)
                    .padding(horizontal = 12.dp, vertical = if (isTablet) 2.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
            @OptIn(ExperimentalFoundationApi::class)
            Column(
                modifier = Modifier
                    .weight(if (isTablet) 1.5f else 1.8f)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onActiveSetLongClick
                    ),
                verticalArrangement = if (isTablet) Arrangement.spacedBy(2.dp) else Arrangement.Center
            ) {
                if (isTablet) {
                    Text(
                        text = "SET STAGE ATIVO",
                        color = Color.Gray,
                        fontSize = labelSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    Text(
                        text = activeSet.uppercase(),
                        color = displayColor,
                        fontSize = valueSize1,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "STAGE SET: ",
                            color = Color.Gray,
                            fontSize = labelSize,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Text(
                            text = activeSet.uppercase(),
                            color = displayColor,
                            fontSize = valueSize1,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                }
            }

            if (isTablet) {
                // Seção 2: System Status (Tablet Only)
                // Divisor Visual
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF222222)))
                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1.5f),
                    verticalArrangement = if (isTablet) Arrangement.spacedBy(2.dp) else Arrangement.Center
                ) {
                    Text(
                        text = "AUDIO ENGINE",
                        color = Color.Gray,
                        fontSize = labelSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    Text(
                        text = "${lastEvent.uppercase()} @ ${sampleRate}HZ",
                        color = displayColor.copy(alpha = 0.8f),
                        fontSize = valueSize3,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }

                // Divisor Visual
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF222222)))
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                // Divisor Compacto (Mobile)
                Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color(0xFF333333)))
            }

            // Seção 3: Audio Interface Info / MIDI Learn Feedback
            if (isTablet) {
                Column(
                    modifier = Modifier.weight(2f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "INTERFACE DE ÁUDIO",
                        color = Color.Gray,
                        fontSize = labelSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    if (midiLearnFeedback != null) {
                        Text(
                            text = midiLearnFeedback.uppercase(),
                            color = cyanNeon,
                            fontSize = valueSize2,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    } else {
                        Text(
                            text = audioInterface.uppercase(),
                            color = displayColor.copy(alpha = 0.8f),
                            fontSize = valueSize3,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.weight(2.4f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "INTERFACE DE ÁUDIO: ",
                        color = Color.Gray,
                        fontSize = labelSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    if (midiLearnFeedback != null) {
                        Text(
                            text = midiLearnFeedback.uppercase(),
                            color = cyanNeon,
                            fontSize = valueSize2,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    } else {
                        Text(
                            text = audioInterface.uppercase(),
                            color = displayColor.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                }
            }

            if (isTablet) {
                // Divisor Visual (Only Tablet)
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF222222)))
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Seção 4: Recursos (RAM/CPU) compactos
            Column(
                modifier = Modifier.weight(if (isTablet) 0.8f else 1.2f),
                horizontalAlignment = if (isTablet) Alignment.Start else Alignment.End,
                verticalArrangement = if (isTablet) Arrangement.spacedBy(2.dp) else Arrangement.Center
            ) {
                if (isTablet) {
                    Text(
                        text = "PERFORMANCE",
                        color = Color.Gray,
                        fontSize = labelSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "RAM: ${ramUsage}MB",
                            color = if (ramUsage > 500) errorRed else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Text(
                            text = "CPU: ${cpuUsage}%",
                            color = if (cpuUsage > 80) errorRed else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                } else {
                    Text(
                        text = "RAM: ${ramUsage}M | CPU: ${cpuUsage}%",
                        color = if (ramUsage > 500 || cpuUsage > 80) errorRed else Color.LightGray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }            
            }
            }
            
            // Bottom Divider (Horizontal Line) - Separator to next bar
            androidx.compose.material3.HorizontalDivider(color = Color(0xFF333333))
        }
    }
}
