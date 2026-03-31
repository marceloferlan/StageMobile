package com.marceloferlan.stagemobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.composed

/**
 * Encapsulates the MIDI Learn state for a single learnable component.
 */
data class MidiLearnState(
    val isActive: Boolean = false,
    val isTarget: Boolean = false,
    val hasMapping: Boolean = false
)

/**
 * Centralized defaults for MIDI Learn visual styling.
 * Change values here to update ALL learnable components at once.
 */
object MidiLearnDefaults {
    val learnColor = Color(0xFFFFEE3B)  // Amarelo Puro Neon — unmapped, ready to learn
    val mappedColor = Color(0xFF39FF14) // Verde neon — already has mapping
    const val idleAlpha = 0.3f          // Alpha quando o modo learn está ativo mas o componente não é o alvo
    const val pulseMin = 0.4f           // Limite inferior da pulsação (aumenta o contraste do efeito "acender")
    const val pulseMax = 1.0f           // Pulse animation upper bound
    const val pulseDurationMs = 600     // Pulse animation duration
    val defaultBorderWidth = 3.dp       // Largura da borda nos knobs (aumentada para pop extra)
}

/**
 * Remember the pulsing animation state for MIDI Learn.
 * Call once per composable scope and pass the result to midiLearnHalo().
 */
@Composable
fun rememberMidiLearnPulse(isActive: Boolean): State<Float>? {
    return if (isActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "midiLearn")
        infiniteTransition.animateFloat(
            initialValue = MidiLearnDefaults.pulseMin,
            targetValue = MidiLearnDefaults.pulseMax,
            animationSpec = infiniteRepeatable(
                animation = tween(MidiLearnDefaults.pulseDurationMs),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
    } else null
}

/**
 * Applies the MIDI Learn halo border to a component.
 *
 * - When learn is active but this is NOT the target: dim static border (idleAlpha)
 * - When learn is active and this IS the target: pulsing border
 * - When learn is NOT active: no border added
 *
 * @param state The MIDI Learn state for this component
 * @param shape The shape of the border (must match the component's shape)
 * @param borderWidth The width of the halo border
 * @param pulseAlpha The pulsing animation state (from rememberMidiLearnPulse)
 */
fun Modifier.midiLearnHalo(
    state: MidiLearnState,
    shape: Shape,
    borderWidth: Dp = MidiLearnDefaults.defaultBorderWidth,
    pulseAlpha: State<Float>? = null
): Modifier {
    if (!state.isActive) return this

    val haloColor = if (state.hasMapping) MidiLearnDefaults.mappedColor else MidiLearnDefaults.learnColor
    val alpha = if (state.isTarget) {
        pulseAlpha?.value ?: MidiLearnDefaults.pulseMax
    } else {
        MidiLearnDefaults.idleAlpha
    }

    return this.border(borderWidth, haloColor.copy(alpha = alpha), shape)
}

/**
 * Applies MIDI Learn-aware click routing to a component.
 *
 * - When learn is active: onClick → onLearnClick, onLongClick → onLearnLongClick (if hasMapping)
 * - When learn is NOT active: onClick → normalClick, onLongClick → normalLongClick
 *
 * @param state The MIDI Learn state for this component
 * @param onLearnClick Callback when tapped during MIDI Learn
 * @param onLearnLongClick Callback when long-pressed during MIDI Learn (to unmap)
 * @param onClick Normal click action
 * @param onLongClick Normal long-click action (optional)
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.midiLearnClickable(
    state: MidiLearnState,
    onLearnClick: () -> Unit,
    onLearnLongClick: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    this.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {
            if (state.isActive) onLearnClick() else onClick()
        },
        onLongClick = {
            if (state.isActive && state.hasMapping) onLearnLongClick()
            else onLongClick?.invoke()
        }
    )
}
