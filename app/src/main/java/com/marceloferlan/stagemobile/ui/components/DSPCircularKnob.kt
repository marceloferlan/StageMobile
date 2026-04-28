package com.marceloferlan.stagemobile.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Componente de Knob Circular para controle de parâmetros DSP.
 * Suporta arraste vertical para ajuste de valor e é responsivo.
 */
@Composable
fun DSPCircularKnob(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    size: Dp = 46.dp,
    labelFontSize: TextUnit = 10.sp,
    valueFontSize: TextUnit = 10.sp,
    knobImageResId: Int? = null,
    valueFormatter: (Float) -> String = { String.format("%.1f", it) },
    tickLabels: List<String>? = null, // Novas labels externas para visual vintage
    tickLabelRadiusMultiplier: Float = 0.72f, // Multiplicador para distância das labels
    modifier: Modifier = Modifier,
    knobModifier: Modifier = Modifier,
    verticalSpacing: Dp = 8.dp,
    dragSensitivity: Float = 0.004f,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onValueCommit: ((Float) -> Unit)? = null
) {
    val normalizedValue = (value - range.start) / (range.endInclusive - range.start)
    
    // Estado interno para manter o arraste suave, independente do snapping externo do value
    var dragNormalizedValue by remember { mutableStateOf(normalizedValue) }
    
    // Rastreador bruto do movimento do dedo para evitar travamento no snapping
    var accumulatedDrag by remember { mutableStateOf(normalizedValue) }
    
    // Flag para bloquear o LaunchedEffect durante o arrasto ativo
    var isDragging by remember { mutableStateOf(false) }
    
    // Sincroniza o estado interno quando o valor muda externamente (e NÃO estamos arrastando)
    LaunchedEffect(value) {
        if (!isDragging) {
            dragNormalizedValue = normalizedValue
            accumulatedDrag = normalizedValue
        }
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueCommit by rememberUpdatedState(onValueCommit)
    val currentRange by rememberUpdatedState(range)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            // Aumenta a largura do container para que os labels não sejam clipados pela camada de composição
            .then(if (tickLabels != null) Modifier.padding(horizontal = 28.dp) else Modifier)
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { 
                                isDragging = true
                                accumulatedDrag = dragNormalizedValue
                            },
                            onDragEnd = {
                                isDragging = false
                                // Commit-on-release: envia o valor final
                                val finalValue = currentRange.start + dragNormalizedValue * (currentRange.endInclusive - currentRange.start)
                                currentOnValueCommit?.invoke(finalValue)
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onDrag = { change: PointerInputChange, dragAmount: Offset ->
                                change.consume()
                                val delta = (-dragAmount.y) * dragSensitivity
                                
                                accumulatedDrag = (accumulatedDrag + delta).coerceIn(0f, 1f)
                                
                                if (tickLabels != null) {
                                    val steps = tickLabels.size - 1
                                    val stepSize = 1f / steps
                                    val nearestStep = Math.round(accumulatedDrag / stepSize)
                                    dragNormalizedValue = nearestStep * stepSize
                                } else {
                                    dragNormalizedValue = accumulatedDrag
                                }
                                
                                currentOnValueChange(currentRange.start + dragNormalizedValue * (currentRange.endInclusive - currentRange.start))
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        if (tickLabels == null) {
            Text(
                text = valueFormatter(value),
                color = accentColor,
                fontSize = valueFontSize,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = verticalSpacing),
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        } else {
            // Spacer para manter o alinhamento vertical com knobs que possuem label
            Spacer(modifier = Modifier.height(valueFontSize.value.dp + verticalSpacing))
        }

        Box(
            modifier = Modifier
                .size(size)
                .then(knobModifier),
            contentAlignment = Alignment.Center
        ) {
            // Se houver imagem, renderiza e rotaciona com filtragem de alta qualidade
            if (knobImageResId != null) {
                Image(
                    bitmap = ImageBitmap.imageResource(id = knobImageResId),
                    contentDescription = null,
                    filterQuality = FilterQuality.High,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize(0.85f) // Margem para o arco de valor
                        .graphicsLayer {
                            // PNG pointing UP is at -90 degrees in Compose/Android.
                            // Start angle is 135.
                            // Offset = 135 - (-90) = 225.
                            // Normalized 0 -> 225 (Points at 135)
                            // Normalized 1 -> 225 + 270 = 495 (Points at 135) - matches arc.
                            rotationZ = 225f + 270f * dragNormalizedValue
                        }
                )
            }

            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val strokeWidth = 3.dp.toPx()
                val radius = (size.toPx() - strokeWidth) / 2
                val center = Offset(size.toPx() / 2, size.toPx() / 2)
                
                // Só desenha o círculo de fundo se não houver imagem do usuário
                if (knobImageResId == null) {
                    drawCircle(
                        color = Color(0xFF333333),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )
                }
                
                val startAngle = 135f
                val sweepAngle = 270f * dragNormalizedValue
                
                // Desenha o arco de valor (sempre desenha para feedback visual)
                drawArc(
                    color = accentColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Só desenha o marcador de linha se não houver imagem do usuário
                if (knobImageResId == null) {
                    val markerAngle = Math.toRadians((startAngle + sweepAngle).toDouble())
                    val markerStart = Offset(
                        center.x + (radius * 0.5f * cos(markerAngle)).toFloat(),
                        center.y + (radius * 0.5f * sin(markerAngle)).toFloat()
                    )
                    val markerEnd = Offset(
                        center.x + (radius * 0.9f * cos(markerAngle)).toFloat(),
                        center.y + (radius * 0.9f * sin(markerAngle)).toFloat()
                    )
                    drawLine(
                        color = Color.White,
                        start = markerStart,
                        end = markerEnd,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            
            // Desenha as labels fixas ao redor do knob (estilo Vintage)
            if (tickLabels != null) {
                val textMeasurer = rememberTextMeasurer()
                val textStyle = TextStyle(
                    color = Color(0xFFAAAAAA),
                    fontSize = (labelFontSize.value * 0.9f).sp,
                    fontWeight = FontWeight.Normal
                )
                
                // Pré-calcula os layouts de texto para evitar medições no bloco de desenho
                val measuredLabels = remember(tickLabels, labelFontSize) {
                    tickLabels.map { textMeasurer.measure(text = it, style = textStyle) }
                }

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val center = Offset(size.toPx() / 2, size.toPx() / 2)
                    
                    measuredLabels.forEachIndexed { index, textLayoutResult ->
                        // Inteligência v11.1: Afasta apenas rótulos longos ou críticos indicados pelo Dev
                        val labelText = tickLabels[index]
                        val dynamicRadiusMultiplier = when {
                            labelText == "-60dB" || labelText == "-40dB" || labelText == "100ms" -> tickLabelRadiusMultiplier * 1.10f
                            else -> tickLabelRadiusMultiplier
                        }
                        
                        val radius = (size.toPx()) * dynamicRadiusMultiplier
                        
                        // Distribui as labels entre 135 e 405 graus
                        val angleDeg = 135f + (270f * index / (measuredLabels.size - 1))
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        
                        val x = center.x + (radius * cos(angleRad)).toFloat()
                        val y = center.y + (radius * sin(angleRad)).toFloat()
                        
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x - textLayoutResult.size.width / 2,
                                y - textLayoutResult.size.height / 2
                            )
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(verticalSpacing))
        Text(
            text = label,
            color = Color(0xFFAAAAAA),
            fontSize = labelFontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
