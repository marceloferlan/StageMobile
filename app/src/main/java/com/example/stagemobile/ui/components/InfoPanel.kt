package com.example.stagemobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

@Composable
fun InfoPanel(
    modifier: Modifier = Modifier,
    ramUsage: Int = 0,
    cpuUsage: Int = 0,
    activeSet: String = "STAGE SET ALPHA",
    midiStatus: String = "OMNI / ALL DEVICES",
    lastEvent: String = "PRONTO",
    sampleRate: Int = 44100,
    isError: Boolean = false,
    isTablet: Boolean = true
) {
    val lcdBackground = Brush.verticalGradient(
        colors = listOf(Color(0xFF0A0A0A), Color(0xFF1A1A1A))
    )
    
    val cyanNeon = Color(0xFF00E5FF)
    val amberNeon = Color(0xFFFFC107)
    val errorRed = Color(0xFFFF5252)
    val displayColor = if (isError) errorRed else cyanNeon

    // Adaptative sizes
    val panelHeight = if (isTablet) 72.dp else 50.dp
    val labelSize = if (isTablet) 9.sp else 7.sp
    val valueSize1 = if (isTablet) 14.sp else 11.sp
    val valueSize2 = if (isTablet) 13.sp else 10.sp
    val valueSize3 = if (isTablet) 12.sp else 9.sp

    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
            .padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 0.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)),
        color = Color.Black,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(lcdBackground)
                .padding(horizontal = 12.dp, vertical = if (isTablet) 3.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Seção 1: Stage Set & Program
            Column(
                modifier = Modifier.weight(1.5f),
                verticalArrangement = if (isTablet) Arrangement.Top else Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isTablet) "ACTIVE STAGE SET" else "STAGE SET",
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

            // Divisor Visual
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF222222)))
            Spacer(modifier = Modifier.width(if (isTablet) 12.dp else 8.dp))

            // Seção 2: Monitor MIDI
            Column(
                modifier = Modifier.weight(1.5f),
                verticalArrangement = if (isTablet) Arrangement.Top else Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isTablet) "MIDI CHANNEL / DEVICE" else "MIDI DEVICE",
                    color = Color.Gray,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    text = midiStatus,
                    color = amberNeon,
                    fontSize = valueSize2,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }

            // Divisor Visual
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF222222)))
            Spacer(modifier = Modifier.width(if (isTablet) 12.dp else 8.dp))

            // Seção 3: System Status & Events
            Column(
                modifier = Modifier.weight(2f),
                verticalArrangement = if (isTablet) Arrangement.Top else Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isTablet) "SYSTEM LOGS / STATUS" else "SYSTEM LOGS",
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

            // Seção 4: Recursos (RAM/CPU) compactos
            Column(
                modifier = Modifier.weight(0.8f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = if (isTablet) Arrangement.Top else Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "RAM: ${ramUsage}MB",
                    color = if (ramUsage > 500) errorRed else Color.LightGray,
                    fontSize = if (isTablet) 10.sp else 8.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    text = "CPU: ${cpuUsage}%",
                    color = if (cpuUsage > 80) errorRed else Color.LightGray,
                    fontSize = if (isTablet) 10.sp else 8.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
    }
}
