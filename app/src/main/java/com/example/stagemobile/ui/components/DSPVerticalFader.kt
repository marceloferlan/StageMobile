package com.example.stagemobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Componente de Fader Vertical para controle de parâmetros DSP.
 * Utiliza o truque de requiredWidth para contornar restrições de layout do Compose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DSPVerticalFader(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    boxHeight: Dp = 208.dp,
    boxWidth: Dp = 48.dp,
    sliderRequiredWidth: Dp = 198.dp,
    thumbImageResId: Int? = null,
    thumbSize: DpSize = DpSize(28.dp, 50.dp),
    thumbGlowColor: Color? = null,
    labelFontSize: TextUnit = 9.sp,
    valueFontSize: TextUnit = 11.sp,
    valueFormatter: (Float) -> String = { String.format("%.1f", it) },
    modifier: Modifier = Modifier,
    knobModifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxHeight()
            .width(boxWidth)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFAAAAAA),
            fontSize = labelFontSize,
            fontWeight = FontWeight.Bold,
        )  
        Box(
            modifier = Modifier
                .height(boxHeight)
                .width(boxWidth),
            contentAlignment = Alignment.Center
        ) {
            val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.92f) // Reduz levemente o comprimento visual do trilho
                    .width(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight((1f - fraction).coerceAtLeast(0.001f))
                        .fillMaxWidth()
                        .background(Color(0xFF333333))
                )
                Box(
                    modifier = Modifier
                        .weight(fraction.coerceAtLeast(0.001f))
                        .fillMaxWidth()
                        .background(accentColor)
                )
            }
                Slider(
                    enabled = enabled,
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = range,
                    modifier = Modifier
                    .requiredWidth(sliderRequiredWidth)
                    .requiredHeight(boxWidth) // Garante largura total antes da rotação
                    .graphicsLayer {
                        rotationZ = -90f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                    },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                track = { Spacer(modifier = Modifier.fillMaxWidth()) },
                thumb = {
                    if (thumbImageResId != null) {
                        Box(contentAlignment = Alignment.Center) {
                            if (thumbGlowColor != null) {
                                Image(
                                    bitmap = ImageBitmap.imageResource(id = thumbImageResId),
                                    contentDescription = null,
                                    filterQuality = FilterQuality.High,
                                    contentScale = ContentScale.FillBounds,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(thumbGlowColor),
                                    modifier = Modifier
                                        .requiredSize(DpSize(thumbSize.width * 1.18f, thumbSize.height * 1.13f))
                                        .graphicsLayer {
                                            rotationZ = 90f // Compensa a rotação de -90f do Slider
                                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                                        }
                                )
                            }
                            Image(
                                bitmap = ImageBitmap.imageResource(id = thumbImageResId),
                                contentDescription = null,
                                filterQuality = FilterQuality.High,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .requiredSize(thumbSize) // Usa requiredSize para o tamanho real do cap
                                    .then(knobModifier)
                                    .graphicsLayer {
                                        rotationZ = 90f // Compensa a rotação de -90f do Slider
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                                    }
                            )
                        }
                    } else {
                        val interactionSource = remember { MutableInteractionSource() }
                        SliderDefaults.Thumb(
                            interactionSource = interactionSource,
                            colors = SliderDefaults.colors(thumbColor = Color.White),
                            thumbSize = DpSize(16.dp, 16.dp)
                        )
                    }
                }
            )
        }        
        Text(
            text = valueFormatter(value),
            color = accentColor,
            fontSize = valueFontSize,
            fontWeight = FontWeight.Bold,
        )
    }
}
