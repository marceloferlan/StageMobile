package com.example.stagemobile.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.example.stagemobile.R

@Composable
fun MasterChannelStrip(
    volume: Float,
    level: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
            .fillMaxHeight()
    ) {
        val heightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current.density
        val isLargeScreen = heightPx >= 400f * density
        val currentWidth = if (isLargeScreen) 158.5.dp else 116.dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(currentWidth)
                .fillMaxHeight()
                .background(
                    color = Color(0xFF1E1E1E), 
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                )
                .padding(if (isLargeScreen) 16.dp else 12.dp)
        ) {
        // Label
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(31.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = Color(0xFF2C2C2C),
            tonalElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "MASTER",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // FADER + METER
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            VerticalFader(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier,
                thumbResourceId = R.drawable.fader_master_thumb
            )

            Spacer(modifier = Modifier.width(if (isLargeScreen) 12.dp else 4.dp))

            // VU Meter Segmented
            BoxWithConstraints(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxHeight()
                    .padding(top = 6.dp)
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp)
            ) {
                val vuHeightPx = constraints.maxHeight.toFloat()
                val vuDensity = LocalDensity.current.density
                
                // 60 LEDs for large screens (tablets), 30 for small screens
                // Using the exact same threshold as ChannelStrip.kt
                val ledCount = if (vuHeightPx >= 400f * vuDensity) 60 else 30
                val activeLeds = (level * ledCount).toInt().coerceIn(0, ledCount)

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 0 until ledCount) {
                        val ledIndexFromBottom = ledCount - 1 - i
                        val isLit = ledIndexFromBottom < activeLeds

                        val ledColor = when {
                            !isLit -> Color(0xFF2C2C2C)
                            // Proportional color zones: Red (top 13%), Yellow (next 20%), Green (rest)
                            i < (ledCount * 0.13f).toInt() -> Color(0xFFFF3B30) // Red zone
                            i < (ledCount * 0.33f).toInt() -> Color(0xFFFFCC00) // Yellow zone
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
        }
    }
}
