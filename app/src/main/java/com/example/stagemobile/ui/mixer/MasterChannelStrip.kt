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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
        val currentWidth = if (isLargeScreen) 158.5.dp else 119.dp

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

            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxHeight()
                    .padding(top = 6.dp)
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp)) // Added clip for rounded ends
            ) {
                // 1. The Dynamic Gradient Layer (The "Light")
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barHeight = size.height * level.coerceIn(0f, 1f)
                    
                    val brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.0f to Color(0xFFFF3B30),
                        0.13f to Color(0xFFFF3B30),
                        0.33f to Color(0xFFFFCC00),
                        0.60f to Color(0xFF4CAF50),
                        1.0f to Color(0xFF4CAF50)
                    )

                    drawRect(
                        brush = brush,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(size.width, barHeight)
                    )
                }

                // 2. The Mask Layer (Dynamic 30 or 60 segments)
                val maskRes = if (isLargeScreen) R.drawable.vu_mask_master_60 else R.drawable.vu_mask_master_30
                Image(
                    painter = painterResource(id = maskRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        }
    }
}
