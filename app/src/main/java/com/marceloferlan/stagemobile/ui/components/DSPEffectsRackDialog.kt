package com.marceloferlan.stagemobile.ui.components
import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import com.marceloferlan.stagemobile.domain.model.DSPEffectInstance
import com.marceloferlan.stagemobile.domain.model.DSPEffectType
import com.marceloferlan.stagemobile.domain.model.DSPParamType
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.marceloferlan.stagemobile.ui.components.MidiLearnState
import com.marceloferlan.stagemobile.ui.components.midiLearnHalo
import com.marceloferlan.stagemobile.ui.components.midiLearnClickable
import com.marceloferlan.stagemobile.ui.components.rememberMidiLearnPulse
import com.marceloferlan.stagemobile.midi.MidiLearnMapping
import com.marceloferlan.stagemobile.midi.MidiLearnTargetInfo
import com.marceloferlan.stagemobile.midi.MidiLearnTarget
import com.marceloferlan.stagemobile.domain.model.InstrumentChannel
import com.marceloferlan.stagemobile.R
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import com.marceloferlan.stagemobile.ui.components.VerticalDSPMeter
import com.marceloferlan.stagemobile.ui.components.MeterType
import com.marceloferlan.stagemobile.viewmodel.MixerViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalLayoutApi::class)
/**
 * Extensão de Modifier para criar um efeito de Alumínio Anodizado Preto (Brushed Metal).
 */
fun Modifier.brushedMetalBackground(): Modifier = this.drawBehind {
    val radius = 12.dp.toPx()
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)

    // 1. Desenha a cor base (Anodized Black) com cantos arredondados
    drawRoundRect(
        color = Color(0xFF202020),
        cornerRadius = cornerRadius
    )

    // 2. Simula as ranhuras (Brushed Lines) - Linhas horizontais sutis
    // Aplicamos o clip opcionalmente ou simplesmente desenhamos no rect
    val lineCount = (size.height / 3f).toInt()
    for (i in 0..lineCount) {
        val y = i * 3f
        drawLine(
            color = Color.White.copy(alpha = 0.02f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
    }

    // 3. Aplica o Gradiente de Iluminação
    val lightingGradient = Brush.linearGradient(
        0.0f to Color.Transparent,
        0.45f to Color.White.copy(alpha = 0.03f),
        0.5f to Color.White.copy(alpha = 0.07f),
        0.55f to Color.White.copy(alpha = 0.03f),
        1.0f to Color.Transparent,
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f)
    )
    
    drawRoundRect(
        brush = lightingGradient,
        cornerRadius = cornerRadius
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DSPEffectsRackDialog(
    title: String,
    effects: List<DSPEffectInstance>,
    onDismiss: () -> Unit,
    onUpdateParam: (String, Int, Float) -> Unit,
    onToggleEffect: (String, Boolean) -> Unit,
    onAddEffect: (DSPEffectType) -> Unit,
    onRemoveEffect: (String) -> Unit,
    onTapDelay: (String) -> Unit = {},
    onResetEffect: (String) -> Unit = {},
    isMidiLearnActive: Boolean = false,
    onToggleMidiLearn: () -> Unit = {},
    onSelectMidiLearnTarget: (String, Int) -> Unit = { _, _ -> },
    midiLearnMappings: List<MidiLearnMapping> = emptyList(),
    midiLearnTarget: MidiLearnTargetInfo? = null,
    midiLearnFeedback: String? = null,
    channelId: Int = -1,
    onTestNoteOn: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onTestNoteOff: (Int, Int) -> Unit = { _, _ -> },
    engine: com.marceloferlan.stagemobile.audio.engine.AudioEngine? = null,
    viewModel: com.marceloferlan.stagemobile.viewmodel.MixerViewModel? = null
) {
    // === Test Player States ===
    var isTestPlayerOpen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var enableBass by remember { mutableStateOf(true) }
    var enableMid by remember { mutableStateOf(true) }
    var enableTreble by remember { mutableStateOf(true) }
    var currentPlayingNote by remember { mutableIntStateOf(-1) }
    
    // Sequência de notas automática
    LaunchedEffect(isPlaying, enableBass, enableMid, enableTreble) {
        if (!isPlaying) {
            currentPlayingNote = -1
            return@LaunchedEffect
        }
        val notes = buildList {
            if (enableBass) addAll(36..47)
            if (enableMid) addAll(60..71)
            if (enableTreble) addAll(84..95)
        }
        if (notes.isEmpty()) {
            isPlaying = false
            return@LaunchedEffect
        }
        while (isPlaying) {
            for (note in notes) {
                if (!isPlaying) break
                currentPlayingNote = note
                onTestNoteOn(channelId, note, 100)
                delay(800)
                onTestNoteOff(channelId, note)
                delay(200)
            }
        }
        currentPlayingNote = -1
    }
    
    // Cleanup ao desmontar
    DisposableEffect(Unit) {
        onDispose {
            isPlaying = false
        }
    }
    
    BackHandler(onBack = { onDismiss() })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = true, onClick = { onDismiss() }),
        contentAlignment = Alignment.Center
    ) {
        val isTablet = com.marceloferlan.stagemobile.utils.UiUtils.rememberIsTablet()
        val screenConfig = LocalConfiguration.current
        val screenWidth = screenConfig.screenWidthDp.dp
        val screenHeight = screenConfig.screenHeightDp.dp

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f)
                .testTag("dsp_effects_rack_dialog")
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header (Following InstrumentChannelOptionsMenu pattern)
                Column(modifier = Modifier.fillMaxWidth().padding(
                    top = if (isTablet) 12.dp else 4.dp,
                    start = if (isTablet) 16.dp else 12.dp,
                    end = if (isTablet) 16.dp else 12.dp,
                    bottom = if (isTablet) 8.dp else 0.dp
                )) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "RACK DE EFEITOS",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val baseTitle = if (title.contains(" [")) title.substringBefore(" [") else title
                            val presetName = if (title.contains(" [")) title.substringAfter(" [", "").removeSuffix("]") else null

                            Text(
                                text = baseTitle.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                modifier = Modifier.padding(top = if (isTablet) 4.dp else 0.dp)
                            )
                            if (presetName != null) {
                                Text(
                                    text = presetName.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Fechar", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(if (isTablet) 8.dp else 4.dp))
                    
                    // Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 12.dp else 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Column - Test Player Button
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (isTestPlayerOpen && isPlaying) {
                                        isPlaying = false
                                    }
                                    isTestPlayerOpen = !isTestPlayerOpen
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.MusicNote,
                                    contentDescription = "Testador de Efeitos",
                                    tint = if (isTestPlayerOpen) Color(0xFF4FC3F7) else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (isPlaying) {
                                val noteLabel = when {
                                    currentPlayingNote in 36..47 -> "♩ Grave"
                                    currentPlayingNote in 60..71 -> "♩ Média"
                                    currentPlayingNote in 84..95 -> "♩ Aguda"
                                    else -> ""
                                }
                                Text(
                                    text = noteLabel,
                                    color = Color(0xFF4FC3F7),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                        
                        // MIDI Learn Button & Status
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isMidiLearnActive) {
                                Text(
                                    text = midiLearnFeedback ?: "Aguardando MIDI CC...", 
                                    color = Color.Yellow, 
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = onToggleMidiLearn,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.AutoFixHigh, 
                                    contentDescription = "MIDI Learn",
                                    tint = if (isMidiLearnActive) Color.Yellow else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // === Painel do Test Player ===
                AnimatedVisibility(
                    visible = isTestPlayerOpen,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2A2A2A))
                            .padding(horizontal = if (isTablet) 16.dp else 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Toggles de faixa
                        val chipColors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF333333),
                            selectedContainerColor = Color(0xFF4FC3F7).copy(alpha = 0.2f),
                            labelColor = Color.Gray,
                            selectedLabelColor = Color(0xFF4FC3F7)
                        )
                        
                        FilterChip(
                            selected = enableBass,
                            onClick = { enableBass = !enableBass },
                            label = { Text("Graves", fontSize = 11.sp) },
                            colors = chipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color(0xFF555555),
                                selectedBorderColor = Color(0xFF4FC3F7),
                                enabled = true,
                                selected = enableBass
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                        FilterChip(
                            selected = enableMid,
                            onClick = { enableMid = !enableMid },
                            label = { Text("Médias", fontSize = 11.sp) },
                            colors = chipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color(0xFF555555),
                                selectedBorderColor = Color(0xFF4FC3F7),
                                enabled = true,
                                selected = enableMid
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                        FilterChip(
                            selected = enableTreble,
                            onClick = { enableTreble = !enableTreble },
                            label = { Text("Agudas", fontSize = 11.sp) },
                            colors = chipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color(0xFF555555),
                                selectedBorderColor = Color(0xFF4FC3F7),
                                enabled = true,
                                selected = enableTreble
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // Play/Stop Button
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = if (isPlaying) Color(0xFFE53935) else Color(0xFF4CAF50),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Parar" else "Tocar",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color(0xFF444444)))
                
                // Effects List (Responsive Grid)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    val visibleEffects = effects.filter { it.type != DSPEffectType.REVERB_SEND }
                    val reverbSendEffect = effects.find { it.type == DSPEffectType.REVERB_SEND }

                    items(visibleEffects.size, key = { index -> visibleEffects[index].id }) { index ->
                        val effect = visibleEffects[index]
                        EffectRackUnit(
                            effect = effect,
                            isTablet = isTablet,
                            onUpdateParam = { paramId, value -> onUpdateParam(effect.id, paramId, value) },
                            onToggle = { onToggleEffect(effect.id, it) },
                            onRemove = { onRemoveEffect(effect.id) },
                            onTapDelay = { onTapDelay(effect.id) },
                            onReset = { onResetEffect(effect.id) },
                            isMidiLearnActive = isMidiLearnActive,
                            onSelectMidiLearnTarget = { paramId -> onSelectMidiLearnTarget(effect.id, paramId) },
                            midiLearnTarget = midiLearnTarget,
                            channelId = channelId,
                            effectIndex = index,
                            engine = engine,
                            reverbSendEffect = if (effect.type == DSPEffectType.REVERB) reverbSendEffect else null,
                            onToggleReverbSend = { sendEnabled ->
                                reverbSendEffect?.let { onToggleEffect(it.id, sendEnabled) }
                            },
                            onUpdateReverbSendParam = { paramId, value ->
                                reverbSendEffect?.let { onUpdateParam(it.id, paramId, value) }
                            }
                        )
                    }
                }
                
                // Footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A))
                        .padding(if (isTablet) 12.dp else 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "STAGE MOBILE AUDIO ENGINE v2.5",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
        
        // --- REUSABLE TOAST SYSTEM ---
        StageToastHost(viewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectRackUnit(
    effect: DSPEffectInstance,
    isTablet: Boolean,
    onUpdateParam: (Int, Float) -> Unit,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onTapDelay: () -> Unit = {},
    onReset: () -> Unit = {},
    isMidiLearnActive: Boolean = false,
    onSelectMidiLearnTarget: (Int) -> Unit = {},
    midiLearnMappings: List<MidiLearnMapping> = emptyList(),
    midiLearnTarget: MidiLearnTargetInfo? = null,
    channelId: Int = -1,
    effectIndex: Int = -1,
    engine: com.marceloferlan.stagemobile.audio.engine.AudioEngine? = null,
    reverbSendEffect: DSPEffectInstance? = null,
    onToggleReverbSend: (Boolean) -> Unit = {},
    onUpdateReverbSendParam: (Int, Float) -> Unit = { _, _ -> }
) {
    val accentColor = when (effect.type) {
        DSPEffectType.EQ_PARAMETRIC -> Color(0xFF4FC3F7)
        DSPEffectType.HPF -> Color(0xFF4DD0E1)
        DSPEffectType.LPF -> Color(0xFF4DB6AC)
        DSPEffectType.DELAY -> Color(0xFFBA68C8)
        DSPEffectType.REVERB -> Color(0xFF9575CD)
        DSPEffectType.CHORUS -> Color(0xFF81C784)
        DSPEffectType.TREMOLO -> Color(0xFFAED581)
        DSPEffectType.COMPRESSOR -> Color(0xFFE57373)
        DSPEffectType.LIMITER -> Color(0xFFFFD54F)
        DSPEffectType.REVERB_SEND -> Color(0xFFFFB74D)
    }
    
    var isExpanded by remember { mutableStateOf<Boolean>(false) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    
    val isEq = effect.type == DSPEffectType.EQ_PARAMETRIC
    val isReverb = effect.type == DSPEffectType.REVERB
    val hasSend = reverbSendEffect != null
    
    // 0 = Canal (Local), 1 = Master (Send)
    var reverbMode by remember(effect.isEnabled, reverbSendEffect?.isEnabled) {
        mutableIntStateOf(if (reverbSendEffect != null && reverbSendEffect.isEnabled && !effect.isEnabled) 1 else 0)
    }
    
    val effectActuallyEnabled = if (isReverb && hasSend && reverbMode == 1) {
        reverbSendEffect?.isEnabled == true
    } else {
        effect.isEnabled
    }
    
    val isCompressor = effect.type == DSPEffectType.COMPRESSOR
    
    val expandedHeight = if (isEq) {
        if (isTablet) 300.dp else 260.dp
    } else if (isReverb) {
        if (isTablet) 315.dp else 285.dp // ~25% reduction for Reverb
    } else if (isCompressor) {
        if (isTablet) 420.dp else 380.dp // Compressor maintains original height
    } else {
        if (isTablet) 273.dp else 247.dp // 35% reduction for HPF, LPF, Chorus, Tremolo, Delay, Limiter
    }
    
    val handleToggle = { enabled: Boolean ->
        if (isReverb && hasSend) {
            if (reverbMode == 0) {
                onToggle(enabled)
                if (enabled) onToggleReverbSend(false)
            } else {
                onToggle(false)
                onToggleReverbSend(enabled)
            }
        } else {
            onToggle(enabled)
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .then(
                if (isExpanded) Modifier.height(expandedHeight) else (Modifier as Modifier)
            )
            .brushedMetalBackground()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { 
                isExpanded = !isExpanded 
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color(0xFF333333))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (effect.isEnabled) accentColor else Color(0xFF424242))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = effect.type.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isExpanded) {
                        TextButton(
                            onClick = { onReset() },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("RESET", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    val switchColors = SwitchDefaults.colors(
                        checkedThumbColor = accentColor,
                        checkedTrackColor = accentColor.copy(alpha = 0.5f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0xFF333333),
                        uncheckedBorderColor = Color.Transparent
                    )
                    Switch(
                        checked = effect.isEnabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.scale(0.7f),
                        colors = switchColors
                    )
                    
                    // Removido o botão de delete: o rack agora é fixo.
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            
            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = Color(0xFF444444))
                
                val formatValue = { value: Float, param: DSPParamType ->
                    when (param.unit) {
                        "dB" -> String.format("%.1f dB", value)
                        "Hz" -> when {
                            value >= 1000f -> String.format("%.1f kHz", value / 1000f)
                            value < 10f -> String.format("%.2f Hz", value)
                            else -> String.format("%.0f Hz", value)
                        }
                        "ms" -> if (param == DSPParamType.ATTACK) String.format("%.1f ms", value) else String.format("%.0f ms", value)
                        "Ratio" -> if (value >= 50f) "inf:1" else String.format("%.1f:1", value)
                        "Q" -> String.format("Q %.1f", value)
                        "%" -> String.format("%.0f%%", value * 100f)
                        else -> String.format("%.1f", value)
                    }
                }

                val getRange = { param: DSPParamType ->
                    when (param) {
                        DSPParamType.LOW_FREQ, DSPParamType.MID_FREQ, DSPParamType.HIGH_FREQ, DSPParamType.CUTOFF_FREQ -> 20f..20000f
                        DSPParamType.LOW_GAIN, DSPParamType.MID_GAIN, DSPParamType.HIGH_GAIN, DSPParamType.OUTPUT_GAIN, DSPParamType.MAKEUP_GAIN -> -15f..15f // Reduced from +/-24dB
                        DSPParamType.THRESHOLD -> if (effect.type == DSPEffectType.LIMITER) -12f..0f else -40f..0f // Calibrated ranges
                        DSPParamType.MID_Q, DSPParamType.RESONANCE -> 0.5f..4.0f // Narrowed for musicality
                        DSPParamType.DELAY_TIME -> 20f..1000f // Real-world stage limit
                        DSPParamType.DELAY_FEEDBACK -> 0f..0.75f // Protection: prevents infinite feedback
                        DSPParamType.MOD_RATE -> if (effect.type == DSPEffectType.CHORUS) 0.1f..3.0f else 0.1f..10.0f // Chorus limit vs Tremolo
                        DSPParamType.ATTACK -> 1f..100f
                        DSPParamType.RELEASE -> if (effect.type == DSPEffectType.LIMITER) 1f..500f else 20f..1000f
                        DSPParamType.RATIO -> 1f..50f // Studio standard, mapped to 50 for inf:1
                        DSPParamType.KNEE -> 0f..12f // 0 to 12 dB soft knee
                        else -> 0f..1f // Default for percentages (Mix, Depth, etc.)
                    }
                }

                val pulseAlpha = rememberMidiLearnPulse(isMidiLearnActive)
                val getLearnState = { paramId: Int ->
                    val isTarget = midiLearnTarget?.target == MidiLearnTarget.DSP_PARAM && 
                                   midiLearnTarget.effectId == effect.id && 
                                   midiLearnTarget.paramId == paramId
                    val hasMapping = midiLearnMappings.any { 
                        it.target == MidiLearnTarget.DSP_PARAM && 
                        it.effectId == effect.id && 
                        it.paramId == paramId 
                    }
                    MidiLearnState(
                        isActive = isMidiLearnActive,
                        isTarget = isTarget,
                        hasMapping = hasMapping
                    )
                }

                val getLearnModifier = { paramId: Int, shape: androidx.compose.ui.graphics.Shape ->
                    val learnState = getLearnState(paramId)
                    Modifier
                        .midiLearnHalo(learnState, shape, 2.dp, pulseAlpha)
                        .midiLearnClickable(
                            state = learnState,
                            onLearnClick = { onSelectMidiLearnTarget(paramId) },
                            onClick = { }
                        )
                }

                val getFaderGlowColor = { paramId: Int ->
                    val state = getLearnState(paramId)
                    if (state.isActive) {
                        val haloColor = if (state.hasMapping) MidiLearnDefaults.mappedColor else MidiLearnDefaults.learnColor
                        val alpha = if (state.isTarget) pulseAlpha?.value ?: MidiLearnDefaults.pulseMax else MidiLearnDefaults.idleAlpha
                        haloColor.copy(alpha = alpha)
                    } else null
                }
                
                val getFaderClickModifier = { paramId: Int ->
                    Modifier.midiLearnClickable(
                        state = getLearnState(paramId),
                        onLearnClick = { onSelectMidiLearnTarget(paramId) },
                        onClick = { }
                    )
                }

                // Configurações Vintage para o Compressor
                val compressorVintageScales = mapOf(
                    DSPParamType.THRESHOLD to listOf(-60f, -40f, -30f, -18f, -9f, 0f),
                    DSPParamType.RATIO to listOf(1f, 2f, 4f, 8f, 16f, 24f),
                    DSPParamType.ATTACK to listOf(1f, 3f, 10f, 30f, 100f, 1000f),
                    DSPParamType.RELEASE to listOf(1f, 3f, 10f, 30f, 100f, 1000f),
                    DSPParamType.MAKEUP_GAIN to listOf(0f, 5f, 10f, 15f, 20f, 24f),
                    DSPParamType.KNEE to listOf(0f, 2f, 4f, 8f, 12f, 18f)
                )

                val getTickLabels = { paramType: DSPParamType ->
                    when (paramType) {
                        DSPParamType.THRESHOLD -> listOf("-60dB", "-40dB", "-30dB", "-18dB", "-9dB", "0dB")
                        DSPParamType.RATIO -> listOf("1:1", "2:1", "4:1", "8:1", "16:1", "24:1")
                        DSPParamType.ATTACK, DSPParamType.RELEASE -> listOf("1ms", "3ms", "10ms", "30ms", "100ms", "1s")
                        DSPParamType.MAKEUP_GAIN -> listOf("0dB", "5dB", "10dB", "15dB", "20dB", "24dB")
                        DSPParamType.KNEE -> listOf("0dB", "2dB", "4dB", "8dB", "12dB", "18dB")
                        else -> null
                    }
                }

                // Effect Parameters - Responsive Dimensions
                val knobSize = if (isTablet) 64.dp else 56.dp
                val knobLabelFontSize = if (isTablet) 11.sp else 9.sp
                val knobValueFontSize = if (isTablet) 11.sp else 10.sp
                
                val faderBoxHeight = if (isTablet) 198.dp else 125.dp
                val sliderRequiredWidth = if (isTablet) faderBoxHeight - 32.dp else 120.dp
                val faderLabelSize = if (isTablet) 9.sp else 7.sp
                val faderValueSize = if (isTablet) 11.sp else 9.sp
                
                when (effect.type) {
                    DSPEffectType.EQ_PARAMETRIC -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val eqKnobSize = knobSize * 1.4f
                            
                            // Coluna 1: Low Gain (0)
                            Box(
                                modifier = Modifier.weight(0.25f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                val i = 0
                                val param = effect.type.params.getOrNull(i)
                                if (param != null) {
                                    DSPCircularKnob(
                                        label = param.label,
                                        value = effect.params[i] ?: 0f,
                                        range = getRange(param),
                                        size = eqKnobSize,
                                        labelFontSize = knobLabelFontSize * 1.1f,
                                        valueFontSize = knobValueFontSize * 1.1f,
                                        accentColor = accentColor,
                                        knobImageResId = R.drawable.knob_custom,
                                        valueFormatter = { formatValue(it, param) },
                                        onValueChange = { onUpdateParam(i, it) },
                                        enabled = effect.isEnabled
                                    )
                                }
                            }

                            // Coluna 2: Mid Gain (2)
                            Box(
                                modifier = Modifier.weight(0.25f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                val i = 2
                                val param = effect.type.params.getOrNull(i)
                                if (param != null) {
                                    DSPCircularKnob(
                                        label = param.label,
                                        value = effect.params[i] ?: 0f,
                                        range = getRange(param),
                                        size = eqKnobSize,
                                        labelFontSize = knobLabelFontSize * 1.1f,
                                        valueFontSize = knobValueFontSize * 1.1f,
                                        accentColor = accentColor,
                                        knobImageResId = R.drawable.knob_custom,
                                        valueFormatter = { formatValue(it, param) },
                                        onValueChange = { onUpdateParam(i, it) },
                                        enabled = effect.isEnabled
                                    )
                                }
                            }

                            // Coluna 3: High Gain (5)
                            Box(
                                modifier = Modifier.weight(0.25f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                val i = 5
                                val param = effect.type.params.getOrNull(i)
                                if (param != null) {
                                    DSPCircularKnob(
                                        label = param.label,
                                        value = effect.params[i] ?: 0f,
                                        range = getRange(param),
                                        size = eqKnobSize,
                                        labelFontSize = knobLabelFontSize * 1.1f,
                                        valueFontSize = knobValueFontSize * 1.1f,
                                        accentColor = accentColor,
                                        knobImageResId = R.drawable.knob_custom,
                                        valueFormatter = { formatValue(it, param) },
                                        onValueChange = { onUpdateParam(i, it) },
                                        enabled = effect.isEnabled
                                    )
                                }
                            }

                            // Coluna 4: Separador e Knob Out (7)
                            Box(
                                modifier = Modifier.weight(0.25f).fillMaxHeight().drawBehind {
                                    drawLine(
                                        color = Color(0xFF444444),
                                        start = Offset(4.dp.toPx(), 20.dp.toPx()),
                                        end = Offset(4.dp.toPx(), size.height - 20.dp.toPx()),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                val outputIdx = 7
                                val param = effect.type.params.getOrNull(outputIdx)
                                if (param != null) {
                                    DSPCircularKnob(
                                        label = param.label,
                                        value = effect.params[outputIdx] ?: 0f,
                                        range = getRange(param),
                                        size = eqKnobSize,
                                        labelFontSize = knobLabelFontSize * 1.1f,
                                        valueFontSize = knobValueFontSize * 1.1f,
                                        accentColor = accentColor,
                                        knobImageResId = R.drawable.knob_custom_golden,
                                        valueFormatter = { formatValue(it, param) },
                                        onValueChange = { onUpdateParam(outputIdx, it) },
                                        enabled = effect.isEnabled
                                    )
                                }
                            }
                        }
                    }
                    DSPEffectType.REVERB -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            if (hasSend && channelId != MixerViewModel.MASTER_CHANNEL_ID) {
                                // Add Switch (Canal vs Master)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("CANAL", fontSize = 12.sp, color = if (reverbMode == 0) accentColor else Color.Gray, fontWeight = if (reverbMode == 0) FontWeight.Bold else FontWeight.Normal)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = reverbMode == 1,
                                        onCheckedChange = { isMaster ->
                                            reverbMode = if (isMaster) 1 else 0
                                            if (isMaster) {
                                                if (effect.isEnabled) {
                                                    onToggle(false)
                                                    onToggleReverbSend(true)
                                                }
                                            } else {
                                                if (reverbSendEffect?.isEnabled == true) {
                                                    onToggleReverbSend(false)
                                                    onToggle(true)
                                                }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = accentColor,
                                            checkedTrackColor = accentColor.copy(alpha = 0.5f),
                                            checkedBorderColor = accentColor.copy(alpha = 0.3f),
                                            uncheckedThumbColor = accentColor,
                                            uncheckedTrackColor = Color.DarkGray,
                                            uncheckedBorderColor = accentColor.copy(alpha = 0.3f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("MASTER", fontSize = 12.sp, color = if (reverbMode == 1) accentColor else Color.Gray, fontWeight = if (reverbMode == 1) FontWeight.Bold else FontWeight.Normal)
                                }
                            }

                            if (!hasSend || reverbMode == 0 || channelId == MixerViewModel.MASTER_CHANNEL_ID) {
                                // Render Local Reverb Knobs
                                val paramsCount = effect.type.params.size
                                val multiplier = if (paramsCount == 2) 2.0f else if (paramsCount == 3) 1.5f else 1.0f
                                val dynamicKnobSize = knobSize * multiplier
                                val dynamicLabelFontSize = knobLabelFontSize * (if (multiplier > 1f) multiplier * 0.9f else 1.0f)
                                val dynamicValueFontSize = knobValueFontSize * (if (multiplier > 1f) multiplier * 0.9f else 1.0f)

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    effect.type.params.forEachIndexed { i, param ->
                                        DSPCircularKnob(
                                            label = param.label,
                                            value = effect.params[i] ?: 0f,
                                            range = getRange(param),
                                            size = dynamicKnobSize,
                                            labelFontSize = dynamicLabelFontSize,
                                            valueFontSize = dynamicValueFontSize,
                                            accentColor = accentColor,
                                            knobImageResId = R.drawable.knob_custom,
                                            valueFormatter = { formatValue(it, param) },
                                            knobModifier = getLearnModifier(i, CircleShape),
                                            onValueChange = { onUpdateParam(i, it) },
                                            enabled = effect.isEnabled
                                        )
                                    }
                                }
                            } else {
                                // Render Master Send Knob
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    reverbSendEffect?.let { sendEffect ->
                                        val param = sendEffect.type.params.getOrNull(0)
                                        if (param != null) {
                                            DSPCircularKnob(
                                                label = param.label,
                                                value = sendEffect.params[0] ?: 0f,
                                                range = getRange(param),
                                                size = knobSize * 1.5f,
                                                labelFontSize = knobLabelFontSize * 1.2f,
                                                valueFontSize = knobValueFontSize * 1.2f,
                                                accentColor = accentColor,
                                                knobImageResId = R.drawable.knob_custom_golden,
                                                valueFormatter = { formatValue(it, param) },
                                                knobModifier = getLearnModifier(0, CircleShape),
                                                onValueChange = { onUpdateReverbSendParam(0, it) },
                                                enabled = sendEffect.isEnabled
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    DSPEffectType.REVERB_SEND -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val param = effect.type.params.firstOrNull()
                            if (param != null) {
                                DSPCircularKnob(
                                    label = param.label,
                                    value = effect.params[0] ?: 0f,
                                    range = getRange(param),
                                    size = knobSize * 2.5f,
                                    labelFontSize = knobLabelFontSize * 2f,
                                    valueFontSize = knobValueFontSize * 2f,
                                    accentColor = accentColor,
                                    knobImageResId = R.drawable.knob_custom,
                                    valueFormatter = { formatValue(it, param) },
                                    onValueChange = { onUpdateParam(0, it) },
                                    enabled = effect.isEnabled
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "O Envio Reverb utiliza os parâmetros de Reverb do canal master. Para adicionar parâmetros específicos neste canal, use (+ Adicionar Efeito > Reverb).",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                    DSPEffectType.COMPRESSOR -> {
                        var activePreset by remember { mutableStateOf("Custom") }
                        var inputPeak by remember { mutableStateOf(0f) }
                        var outputPeak by remember { mutableStateOf(0f) }
                        var grDb by remember { mutableStateOf(0f) }
                        val meterValues = remember { FloatArray(3) }

                        LaunchedEffect(channelId, effectIndex) {
                            while (true) {
                                if (engine != null && engine.isInitialized) {
                                    if (engine.getEffectMeters(channelId, effectIndex, meterValues)) {
                                        inputPeak = meterValues[0]
                                        outputPeak = meterValues[1]
                                        grDb = meterValues[2]
                                    }
                                }
                                delay(50)
                            }
                        }

                        val compressorKnobSize = if (isTablet) 62.dp else 52.dp
                        Row(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Coluna 1: METERS (15%)
                            Column(
                                modifier = Modifier.weight(0.15f).fillMaxHeight().padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Text("METERS", color = Color.White, fontSize = if (isTablet) 10.sp else 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("GR", color = Color(0xFFFFB300), fontSize = if (isTablet) 8.sp else 7.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        VerticalDSPMeter(value = grDb, type = MeterType.REDUCTION, modifier = Modifier.fillMaxHeight().width(if (isTablet) 10.dp else 8.dp))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("IN", color = Color.White, fontSize = if (isTablet) 8.sp else 7.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        VerticalDSPMeter(value = inputPeak, type = MeterType.LEVEL, modifier = Modifier.fillMaxHeight().width(if (isTablet) 10.dp else 8.dp))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("OUT", color = Color.White, fontSize = if (isTablet) 8.sp else 7.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        VerticalDSPMeter(value = outputPeak, type = MeterType.LEVEL, modifier = Modifier.fillMaxHeight().width(if (isTablet) 10.dp else 8.dp))
                                    }
                                }
                            }

                            // Coluna 2: THRESHOLD & ATTACK (21%)
                            Column(
                                modifier = Modifier.weight(0.21f).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                listOf(0, 2).forEach { i ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val param = effect.type.params.getOrNull(i)
                                        if (param != null) {
                                            val vintageScale = compressorVintageScales[param]
                                            val isVintage = vintageScale != null
                                            DSPCircularKnob(
                                                label = param.label,
                                                value = if (isVintage) {
                                                    vintageScale!!.indexOf(effect.params[i] ?: vintageScale[0]).coerceAtLeast(0).toFloat()
                                                } else {
                                                    effect.params[i] ?: 0f
                                                },
                                                range = if (isVintage) 0f..5f else getRange(param),
                                                size = compressorKnobSize,
                                                labelFontSize = knobLabelFontSize,
                                                valueFontSize = knobValueFontSize,
                                                accentColor = accentColor,
                                                knobImageResId = R.drawable.knob_custom,
                                                valueFormatter = { 
                                                    if (isVintage) {
                                                        val realValue = vintageScale!!.getOrElse(it.toInt()) { vintageScale[0] }
                                                        if (param == DSPParamType.ATTACK || param == DSPParamType.RELEASE) if (realValue >= 1000f) "1s" else "${realValue.toInt()}ms"
                                                        else "${realValue.toInt()} dB"
                                                    } else {
                                                        formatValue(it, param) 
                                                    }
                                                },
                                                knobModifier = getLearnModifier(i, CircleShape),
                                                tickLabels = if (isVintage) getTickLabels(param) else null,
                                                tickLabelRadiusMultiplier = 0.70f,
                                                onValueChange = { 
                                                    activePreset = "Custom"
                                                    val actualValue = if (isVintage) {
                                                        vintageScale!!.getOrElse(it.toInt()) { vintageScale[0] }
                                                    } else {
                                                        it
                                                    }
                                                    onUpdateParam(i, actualValue) 
                                                },
                                                enabled = effect.isEnabled
                                            )
                                        }
                                    }
                                }
                            }

                            // Coluna 3: RATIO (21%) - Preset Buttons + Knob
                            Column(
                                modifier = Modifier.weight(0.21f).fillMaxHeight(), 
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val i = 1 // Ratio
                                val param = effect.type.params.getOrNull(i)
                                if (param != null) {
                                    val vintageScale = compressorVintageScales[param]
                                    val isVintage = vintageScale != null
                                    DSPCircularKnob(
                                        label = param.label,
                                        value = if (isVintage) {
                                            vintageScale!!.indexOf(effect.params[i] ?: vintageScale[0]).coerceAtLeast(0).toFloat()
                                        } else {
                                            effect.params[i] ?: 0f
                                        },
                                        range = if (isVintage) 0f..5f else getRange(param),
                                        size = compressorKnobSize * 0.9f,
                                        labelFontSize = knobLabelFontSize,
                                        valueFontSize = knobValueFontSize,
                                        accentColor = accentColor,
                                        knobImageResId = R.drawable.knob_custom,
                                        valueFormatter = { 
                                            if (isVintage) {
                                                val realValue = vintageScale!!.getOrElse(it.toInt()) { vintageScale[0] }
                                                "${realValue.toInt()}:1"
                                            } else {
                                                formatValue(it, param) 
                                            }
                                        },
                                        knobModifier = getLearnModifier(i, CircleShape),
                                        tickLabels = if (isVintage) getTickLabels(param) else null,
                                        tickLabelRadiusMultiplier = 0.70f,
                                        onValueChange = { 
                                            activePreset = "Custom"
                                            val actualValue = if (isVintage) {
                                                vintageScale!!.getOrElse(it.toInt()) { vintageScale[0] }
                                            } else {
                                                it
                                            }
                                            onUpdateParam(i, actualValue) 
                                        },
                                        enabled = effect.isEnabled
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Botões de Preset (Lista Vertical)
                                    Column(
                                        modifier = Modifier.padding(start = 108.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        listOf("Soft", "Balanced", "Hard", "Custom").forEach { presetName ->
                                            val isSelected = activePreset == presetName
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(
                                                        enabled = effect.isEnabled && presetName != "Custom", 
                                                        indication = null, 
                                                        interactionSource = remember { MutableInteractionSource() }
                                                    ) {
                                                        activePreset = presetName
                                                        if (presetName == "Soft") {
                                                            onUpdateParam(0, -9f)
                                                            onUpdateParam(1, 2f)
                                                            onUpdateParam(2, 30f)
                                                            onUpdateParam(3, 100f)
                                                            onUpdateParam(4, 0f)
                                                            onUpdateParam(5, 4f)
                                                            onUpdateParam(6, 1.0f)
                                                        } else if (presetName == "Balanced") {
                                                            onUpdateParam(0, -18f)
                                                            onUpdateParam(1, 4f)
                                                            onUpdateParam(2, 10f)
                                                            onUpdateParam(3, 100f)
                                                            onUpdateParam(4, 5f)
                                                            onUpdateParam(5, 8f)
                                                            onUpdateParam(6, 1.0f)
                                                        } else if (presetName == "Hard") {
                                                            onUpdateParam(0, -30f)
                                                            onUpdateParam(1, 16f)
                                                            onUpdateParam(2, 3f)
                                                            onUpdateParam(3, 30f)
                                                            onUpdateParam(4, 10f)
                                                            onUpdateParam(5, 2f)
                                                            onUpdateParam(6, 1.0f)
                                                        }
                                                    }
                                                    .padding(vertical = 4.dp) // Aumenta área de toque vertical
                                            ) {
                                                // Imagem do Preset (substituindo o Box desenhado)
                                                Image(
                                                    painter = painterResource(
                                                        id = if (isSelected) R.drawable.compressor_preset_button_on else R.drawable.compressor_preset_button_off
                                                    ),
                                                    contentDescription = presetName,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = presetName, 
                                                    color = if (isSelected) Color.White else Color.Gray, 
                                                    fontSize = 11.sp,  // Aumentado levemente para legibilidade
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Coluna 4: KNEE & RELEASE (21%)
                            Column(
                                modifier = Modifier.weight(0.21f).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                listOf(5, 3).forEach { i ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val param = effect.type.params.getOrNull(i)
                                        if (param != null) {
                                            val vintageScale = compressorVintageScales[param]
                                            val isVintage = vintageScale != null
                                            DSPCircularKnob(
                                                label = param.label,
                                                value = if (isVintage) {
                                                    vintageScale!!.indexOf(effect.params[i] ?: vintageScale[0]).coerceAtLeast(0).toFloat()
                                                } else {
                                                    effect.params[i] ?: 0f
                                                },
                                                range = if (isVintage) 0f..5f else getRange(param),
                                                size = compressorKnobSize,
                                                labelFontSize = knobLabelFontSize,
                                                valueFontSize = knobValueFontSize,
                                                accentColor = accentColor,
                                                knobImageResId = R.drawable.knob_custom,
                                                valueFormatter = { 
                                                    if (isVintage) {
                                                        val realValue = vintageScale!!.getOrElse(it.toInt()) { vintageScale[0] }
                                                        if (param == DSPParamType.ATTACK || param == DSPParamType.RELEASE) if (realValue >= 1000f) "1s" else "${realValue.toInt()}ms"
                                                        else "${realValue.toInt()} dB"
                                                    } else {
                                                        formatValue(it, param) 
                                                    }
                                                },
                                                knobModifier = getLearnModifier(i, CircleShape),
                                                tickLabels = if (isVintage) getTickLabels(param) else null,
                                                tickLabelRadiusMultiplier = 0.70f,
                                                onValueChange = { 
                                                    activePreset = "Custom"
                                                    val actualValue = if (isVintage) {
                                                        vintageScale!!.getOrElse(it.toInt()) { vintageScale[0] }
                                                    } else {
                                                        it
                                                    }
                                                    onUpdateParam(i, actualValue) 
                                                },
                                                enabled = effect.isEnabled
                                            )
                                        }
                                    }
                                }
                            }

                            // Coluna 5: MAKEUP & MIX (22%)
                            Column(
                                modifier = Modifier
                                    .weight(0.22f)
                                    .fillMaxHeight()
                                    .drawBehind {
                                        drawLine(
                                            color = Color(0xFF444444),
                                            start = Offset(10.dp.toPx(), 20.dp.toPx()),
                                            end = Offset(10.dp.toPx(), size.height - 20.dp.toPx()),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                listOf(4, 6).forEach { i ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val param = effect.type.params.getOrNull(i)
                                        if (param != null) {
                                            val vintageScale = compressorVintageScales[param]
                                            val isVintage = (i == 4)
                                            DSPCircularKnob(
                                                label = param.label,
                                                value = if (isVintage) {
                                                    vintageScale!!.indexOf(effect.params[i] ?: vintageScale[0]).coerceAtLeast(0).toFloat()
                                                } else {
                                                    effect.params[i] ?: 0f
                                                },
                                                range = if (isVintage) 0f..5f else getRange(param),
                                                size = if (isTablet) compressorKnobSize * 1.0f else compressorKnobSize * 0.95f,
                                                labelFontSize = knobLabelFontSize * 0.9f,
                                                valueFontSize = knobValueFontSize * 0.9f,
                                                accentColor = accentColor,
                                                knobImageResId = R.drawable.knob_custom_golden,
                                                valueFormatter = { 
                                                    if (isVintage) {
                                                        val realValue = vintageScale!!.getOrElse(it.toInt()) { vintageScale[0] }
                                                        "${realValue.toInt()} dB"
                                                    } else {
                                                        formatValue(it, param) 
                                                    }
                                                },
                                                knobModifier = getLearnModifier(i, CircleShape),
                                                tickLabels = if (isVintage) getTickLabels(param) else null,
                                                tickLabelRadiusMultiplier = 0.75f,
                                                verticalSpacing = if (i == 6) 4.dp else 8.dp,
                                                dragSensitivity = if (i == 6) 0.008f else 0.006f,
                                                onValueChange = { 
                                                    activePreset = "Custom"
                                                    val actualValue = if (isVintage) {
                                                        vintageScale!!.getOrElse(it.toInt()) { vintageScale[0] }
                                                    } else {
                                                        it
                                                    }
                                                    onUpdateParam(i, actualValue) 
                                                },
                                                enabled = effect.isEnabled
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            val paramsCount = effect.type.params.size
                            // Reduzido em ~30-35% para acompanhar a altura do card
                            val multiplier = if (paramsCount == 2) 1.4f else if (paramsCount == 3) 1.2f else 1.0f
                            
                            // Ajuste proporcional dos textos para acompanhar o tamanho
                            val dynamicKnobSize = knobSize * multiplier
                            val dynamicLabelFontSize = knobLabelFontSize * (if (multiplier > 1.1f) multiplier * 0.85f else 1.0f)
                            val dynamicValueFontSize = knobValueFontSize * (if (multiplier > 1.1f) multiplier * 0.85f else 1.0f)

                            // Estado local para Delay Time (commit-on-release)
                            val isDelayEffect = effect.type == DSPEffectType.DELAY
                            var localDelayTime by remember(effect.id) { 
                                mutableStateOf(effect.params[0] ?: 500f) 
                            }
                            // Sincroniza com mudanças externas (ex: tap tempo)
                            LaunchedEffect(effect.params[0]) {
                                localDelayTime = effect.params[0] ?: 500f
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                effect.type.params.forEachIndexed { i, param ->
                                    val isDelayTime = isDelayEffect && i == 0
                                    
                                    DSPCircularKnob(
                                        label = param.label,
                                        value = if (isDelayTime) localDelayTime else (effect.params[i] ?: 0f),
                                        range = getRange(param),
                                        size = dynamicKnobSize,
                                        labelFontSize = dynamicLabelFontSize,
                                        valueFontSize = dynamicValueFontSize,
                                        accentColor = accentColor,
                                        knobImageResId = R.drawable.knob_custom,
                                        valueFormatter = { formatValue(it, param) },
                                        knobModifier = getLearnModifier(i, CircleShape),
                                        onValueChange = { newValue ->
                                            if (isDelayTime) {
                                                // Apenas atualiza a UI local, NÃO envia para a engine
                                                localDelayTime = newValue
                                            } else {
                                                onUpdateParam(i, newValue)
                                            }
                                        },
                                        onValueCommit = if (isDelayTime) { finalValue ->
                                            // Envia o valor final para a engine quando o knob é solto
                                            onUpdateParam(0, finalValue)
                                        } else null,
                                        enabled = effect.isEnabled
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun AddEffectButton(
    onAddEffect: (DSPEffectType) -> Unit,
    existingTypes: Set<DSPEffectType>
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF81C784)),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color(0xFF81C784)))
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ADICIONAR EFEITO")
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            val availableEffects = DSPEffectType.values().filter { type ->
                type != DSPEffectType.EQ_PARAMETRIC && 
                type != DSPEffectType.REVERB_SEND &&
                !existingTypes.contains(type)
            }
            
            if (availableEffects.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Todos os efeitos já inclusos", color = Color.Gray, fontSize = 12.sp) },
                    onClick = { expanded = false }
                )
            } else {
                availableEffects.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label, color = Color.White) }, // Use type.label instead of enum name
                        onClick = {
                            onAddEffect(type)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
