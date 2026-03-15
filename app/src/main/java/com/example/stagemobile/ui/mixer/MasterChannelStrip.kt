package com.example.stagemobile.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MasterChannelStrip(
    volume: Float,
    level: Float,
    isMasterLimiterEnabled: Boolean = false,
    onMasterLimiterToggle: (Boolean) -> Unit = {},
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isMidiLearnActive: Boolean = false,
    isLearnTargetFader: Boolean = false,
    hasFaderMapping: Boolean = false,
    onLearnFaderClick: () -> Unit = {},
    onLearnFaderLongClick: () -> Unit = {}
) {
    BoxWithConstraints(
        modifier = modifier
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
            .fillMaxHeight()
    ) {
        val isTablet = com.example.stagemobile.utils.UiUtils.rememberIsTablet()
        val currentWidth = if (isTablet) 158.5.dp else 119.dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(currentWidth)
                .fillMaxHeight()
                .background(
                    color = Color(0xFF161616), 
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                )
                .padding(if (isTablet) 16.dp else 12.dp)
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
        
        Spacer(modifier = Modifier.height(12.dp))

        // PUNCH BUTTON (Master Limiter)
        val punchColor = if (isMasterLimiterEnabled) Color(0xFF4CAF50) else Color(0xFF333333)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onMasterLimiterToggle(!isMasterLimiterEnabled) },
            color = punchColor,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "PUNCH",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isMasterLimiterEnabled) Color.White else Color.Gray,
                    textAlign = TextAlign.Center
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
                    value = volume,
                    onValueChange = if (isMidiLearnActive) { _ -> } else onVolumeChange,
                    modifier = Modifier,
                    thumbResourceId = R.drawable.fader_master_thumb,
                    isMidiLearnActive = isMidiLearnActive,
                    isLearnTarget = isLearnTargetFader,
                    hasMidiMapping = hasFaderMapping,
                    labelXOffset = if (isTablet) (-46).dp else (-40).dp
                )
            }

            Spacer(modifier = Modifier.width(if (isTablet) 16.dp else 4.dp))

            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxHeight()
                    .padding(top = 6.dp)
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                    .border(width = 1.dp, color = Color(0xFF282828), shape = RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp)) // Added clip for rounded ends
            ) {
                // 1. The Mask Layout "Off" segments (Subtle Background)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        color = Color(0xFF222222),
                        size = size
                    )
                }

                // 2. The Dynamic Gradient Layer (The "Light")
                Canvas(modifier = Modifier.fillMaxSize()) {
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
                val maskRes = if (isTablet) R.drawable.meter_mask_master_60 else R.drawable.meter_mask_master_30
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
