package com.example.stagemobile.ui.mixer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stagemobile.R

/**
 * Fader vertical com escala dB calibrada e adaptativa.
 *
 * Tablet (altura >= 400dp): escala completa (9 marcas)
 * Celular (altura < 400dp): escala reduzida (6 marcas)
 */
@Composable
fun VerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    thumbResourceId: Int = R.drawable.fader_thumb
) {
    // All possible dB marks: position = (dB + 60) / 66
    val fullScale = listOf(
        "+6" to 1.000f,
        "0" to 0.909f,
        "-5" to 0.833f,
        "-10" to 0.758f,
        "-20" to 0.606f,
        "-30" to 0.455f,
        "-40" to 0.303f,
        "-50" to 0.152f,
        "-60" to 0.050f,
        "-∞" to 0.000f
    )

    val compactScale = listOf(
        "+6" to 1.000f,
        "0" to 0.909f,
        "-10" to 0.758f,
        "-30" to 0.455f,
        "-50" to 0.152f,
        "-∞" to 0.000f
    )

    BoxWithConstraints(
        modifier = modifier
            .width(50.dp)
            .fillMaxHeight()
    ) {

        val heightPx = constraints.maxHeight.toFloat()
        val heightDp = with(LocalDensity.current) { heightPx.toDp() }

        // Choose scale and sizes based on available height
        val isLargeScreen = heightDp >= 400.dp
        val scaleMarks = if (isLargeScreen) fullScale else compactScale
        
        val labelFontSize = if (isLargeScreen) 11.sp else 8.sp
        val infinityFontSize = if (isLargeScreen) 13.sp else 10.sp
        
        val thumbHeight = if (isLargeScreen) 50.dp else 40.dp // 25% increase
        val thumbWidth = if (isLargeScreen) 108.dp else 86.dp // 25% increase

        // Inset area so labels at top/bottom are visible
        val labelInset = with(LocalDensity.current) { 14.dp.toPx() }
        val usableHeight = heightPx - (labelInset * 2f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(heightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val newValue = 1f - (offset.y / heightPx)
                            onValueChange(newValue.coerceIn(0f, 1f))
                        },
                        onVerticalDrag = { change, _ ->
                            val newValue = 1f - (change.position.y / heightPx)
                            onValueChange(newValue.coerceIn(0f, 1f))
                        }
                    )
                }
        ) {

            // Scale labels — positioned proportionally within inset area
            scaleMarks.forEach { (label, position) ->
                // Offset to vertically center text on the tick mark
                val isInfinity = label == "-∞"
                val textOffsetDp = when {
                    isInfinity -> (-6).dp   // -∞ is larger, needs less offset
                    position <= 0.05f -> (-14).dp
                    else -> (-12).dp
                }
                val textOffsetPx = with(LocalDensity.current) { textOffsetDp.toPx() }
                
                Text(
                    text = label,
                    fontSize = if (isInfinity) infinityFontSize else labelFontSize,
                    textAlign = TextAlign.End,
                    color = if (label == "0") Color(0xFF4CAF50) else Color(0xFF888888),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(x = (-40).dp)
                        .width(36.dp)
                        .offset {
                            val yPx = labelInset + ((1f - position) * usableHeight)
                            IntOffset(0, (yPx + textOffsetPx).toInt())
                        }
                )
            }

            // Track
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .padding(top = 6.dp, bottom = 0.dp)
                    .width(8.dp) // Largura para fader 3D
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF0A0A0A), // Sombra funda na esquerda
                                Color(0xFF1E1E1E), // Fundo do trilho
                                Color(0xFF555555)  // Luz na borda direita (chanfro)
                            )
                        ),
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFF000000), // Contorno escuro
                        shape = RoundedCornerShape(4.dp)
                    )
            )

            // Scale tick marks on both sides of the track
            scaleMarks.forEach { (label, position) ->
                val isInfinity = label == "-∞"
                val tickOffsetDp = if (isInfinity) 5.dp else 0.dp
                val tickOffsetPx = with(LocalDensity.current) { tickOffsetDp.toPx() }
                
                val tickWidth = if (label == "0") 12.dp else 8.dp
                // Distância do centro: Metade do track (4dp) + margem (1dp) + metade do tick
                val xOffsetDp = 5.dp + (tickWidth / 2)
                val xOffsetPx = with(LocalDensity.current) { xOffsetDp.toPx() }

                // Left tick
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            val yPx = labelInset + ((1f - position) * usableHeight) + tickOffsetPx
                            IntOffset(-xOffsetPx.toInt(), yPx.toInt())
                        }
                        .width(tickWidth)
                        .height(1.dp)
                        .background(
                            if (label == "0") Color(0xFF4CAF50)
                            else Color(0xFF666666)
                        )
                )

                // Right tick
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            val yPx = labelInset + ((1f - position) * usableHeight) + tickOffsetPx
                            IntOffset(xOffsetPx.toInt(), yPx.toInt())
                        }
                        .width(tickWidth)
                        .height(1.dp)
                        .background(
                            if (label == "0") Color(0xFF4CAF50)
                            else Color(0xFF666666)
                        )
                )
            }

            // Thumb with Blur Shadow effect
            val thumbHeightPx = with(LocalDensity.current) { thumbHeight.toPx() }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        val halfThumb = thumbHeightPx / 2f
                        val yCenter = labelInset + (1f - value) * usableHeight
                        val yOffset = (yCenter - halfThumb).coerceIn(0f, heightPx - thumbHeightPx)
                        IntOffset(0, yOffset.toInt())
                    }
                    .requiredWidth(thumbWidth)
                    .height(thumbHeight)
            ) {
                // 1. Shadow Layer: Black-tinted blurred copy of the thumb
                Image(
                    painter = painterResource(id = thumbResourceId),
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = 4.dp) // Slightly more offset for better depth
                        .blur(radius = 10.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                )

                // 2. Main Thumb Layer
                Image(
                    painter = painterResource(id = thumbResourceId),
                    contentDescription = "Fader Thumb",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}


