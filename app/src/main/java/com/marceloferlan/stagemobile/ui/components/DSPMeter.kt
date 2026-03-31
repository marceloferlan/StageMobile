package com.marceloferlan.stagemobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.marceloferlan.stagemobile.R

enum class MeterType {
    LEVEL,      // Upwards: -inf to 0dB (Input/Output)
    REDUCTION   // Downwards: 0 to -30dB (Gain Reduction)
}

@Composable
fun VerticalDSPMeter(
    value: Float, // For LEVEL: 0.0 to 1.0 (linear). For REDUCTION: dB value (e.g. -12f)
    type: MeterType,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .border(width = 1.dp, color = Color(0xFF333333), shape = RoundedCornerShape(4.dp))
            .padding(1.dp)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp))
    ) {
        // 1. Background (Off segments)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Color(0xFF222222),
                size = size
            )
        }

        // 2. Dynamic Light Layer
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (type) {
                MeterType.LEVEL -> {
                    // level is 0.0 to 1.0
                    val barHeight = size.height * value.coerceIn(0f, 1f)
                    val brush = Brush.verticalGradient(
                        0.0f to Color(0xFFFF3B30),   // Red (Peak)
                        0.15f to Color(0xFFFFCC00),  // Yellow
                        0.40f to Color(0xFF4CAF50),  // Green
                        1.0f to Color(0xFF4CAF50)
                    )
                    drawRect(
                        brush = brush,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width, barHeight)
                    )
                }
                MeterType.REDUCTION -> {
                    // value is dB (0.0 to -30.0f)
                    // Map -30..0 to 1.0..0.0 height
                    val grNormalized = (value / -30f).coerceIn(0f, 1f)
                    val barHeight = size.height * grNormalized
                    
                    val brush = Brush.verticalGradient(
                        0.0f to Color(0xFFFFB300),   // Orange/Amber
                        1.0f to Color(0xFFE65100)    // Dark Orange
                    )
                    
                    drawRect(
                        brush = brush,
                        topLeft = Offset(0f, 0f), // Start from top
                        size = Size(size.width, barHeight)
                    )
                }
            }
        }

        // 3. Mask Layer (Segmentos Fakes)
        Image(
            painter = painterResource(id = R.drawable.meter_mask_channel_30),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
    }
}
