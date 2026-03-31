package com.marceloferlan.stagemobile.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marceloferlan.stagemobile.domain.model.SetStage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BankNavigationPanel(
    currentBank: Int,
    bankName: String? = null,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRenameBank: (() -> Unit)? = null,
    isMidiLearnActive: Boolean = false,
    pulseAlpha: State<Float>? = null,
    midiLearnTarget: com.marceloferlan.stagemobile.midi.MidiLearnTargetInfo? = null,
    midiLearnMappings: List<com.marceloferlan.stagemobile.midi.MidiLearnMapping> = emptyList(),
    onLearnPrev: () -> Unit = {},
    onLearnNext: () -> Unit = {},
    onUnmapPrev: () -> Unit = {},
    onUnmapNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isTablet = com.marceloferlan.stagemobile.utils.UiUtils.rememberIsTablet()
    val prevMidiState = MidiLearnState(
        isMidiLearnActive,
        midiLearnTarget?.target == com.marceloferlan.stagemobile.midi.MidiLearnTarget.PREVIOUS_BANK,
        midiLearnMappings.any { it.target == com.marceloferlan.stagemobile.midi.MidiLearnTarget.PREVIOUS_BANK }
    )
    val nextMidiState = MidiLearnState(
        isMidiLearnActive,
        midiLearnTarget?.target == com.marceloferlan.stagemobile.midi.MidiLearnTarget.NEXT_BANK,
        midiLearnMappings.any { it.target == com.marceloferlan.stagemobile.midi.MidiLearnTarget.NEXT_BANK }
    )
    Row(
        modifier = modifier
            .background(Color(0xFF222222), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = if (isTablet) 8.dp else 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = if (isMidiLearnActive) onLearnPrev else onPrevious,
            enabled = if (isMidiLearnActive) true else currentBank > 1,
            modifier = Modifier
                .midiLearnHalo(prevMidiState, RoundedCornerShape(12.dp), pulseAlpha = pulseAlpha)
                .combinedClickable(
                    onClick = { if (isMidiLearnActive) onLearnPrev() else onPrevious() },
                    onLongClick = { if (isMidiLearnActive && prevMidiState.hasMapping) onUnmapPrev() }
                )
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Banco Anterior", tint = if (!isMidiLearnActive && currentBank <= 1) Color.Gray else Color.White)
        }
        
        Spacer(modifier = Modifier.width(if (isTablet) 32.dp else 16.dp))
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .combinedClickable(
                    onClick = { /* Simple click does nothing or can toggle something */ },
                    onLongClick = { onRenameBank?.invoke() }
                )
        ) {
            Text(
                text = "Banco $currentBank",
                style = if (isTablet) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            if (!bankName.isNullOrEmpty()) {
                Text(
                    text = bankName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.width(if (isTablet) 32.dp else 16.dp))
        
        IconButton(
            onClick = if (isMidiLearnActive) onLearnNext else onNext,
            enabled = if (isMidiLearnActive) true else currentBank < 10,
            modifier = Modifier
                .midiLearnHalo(nextMidiState, RoundedCornerShape(12.dp), pulseAlpha = pulseAlpha)
                .combinedClickable(
                    onClick = { if (isMidiLearnActive) onLearnNext() else onNext() },
                    onLongClick = { if (isMidiLearnActive && nextMidiState.hasMapping) onUnmapNext() }
                )
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Próximo Banco", tint = if (!isMidiLearnActive && currentBank >= 10) Color.Gray else Color.White)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetStageGrid(
    currentBank: Int,
    loadedSets: Map<Int, SetStage>,
    activeSetStageId: String? = null,
    clipboardSetStage: SetStage? = null,
    onSetStageClicked: (Int, SetStage?) -> Unit,
    onCopy: ((SetStage) -> Unit)? = null,
    onPaste: ((Int, SetStage) -> Unit)? = null,
    onDelete: ((Int) -> Unit)? = null,
    onRename: ((SetStage) -> Unit)? = null,
    isMidiLearnActive: Boolean = false,
    pulseAlpha: State<Float>? = null,
    midiLearnTarget: com.marceloferlan.stagemobile.midi.MidiLearnTargetInfo? = null,
    midiLearnMappings: List<com.marceloferlan.stagemobile.midi.MidiLearnMapping> = emptyList(),
    onLearnSlot: (Int) -> Unit = {},
    onUnmapSlot: (Int) -> Unit = {},
    spacing: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    val slots = (1..15).toList()

    var showDropdownForSlot by remember { mutableStateOf<Int?>(null) }
    var slotToConfirmDelete by remember { mutableStateOf<Int?>(null) }
    var slotToConfirmPaste by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalSpacingHeight = spacing * 2 // 2 gaps entre 3 linhas
        val availableHeight = maxHeight - totalSpacingHeight
        val itemHeight = availableHeight / 3

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            userScrollEnabled = false
        ) {
            items(slots) { slotId ->
                val setStage = loadedSets[slotId]
                val isEmpty = setStage == null
                val isActive = activeSetStageId == "${currentBank}_$slotId"

                val slotMidiState = MidiLearnState(
                    isMidiLearnActive,
                    midiLearnTarget?.target == com.marceloferlan.stagemobile.midi.MidiLearnTarget.SET_STAGE_SLOT && midiLearnTarget?.slotIndex == slotId,
                    midiLearnMappings.any { it.target == com.marceloferlan.stagemobile.midi.MidiLearnTarget.SET_STAGE_SLOT && it.slotIndex == slotId }
                )

                Column(
                    modifier = Modifier
                        .height(itemHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isEmpty) Color(0xFF1E1E1E)
                            else Color(0xFF2C2C2C)
                        )
                        .midiLearnHalo(slotMidiState, RoundedCornerShape(12.dp), pulseAlpha = pulseAlpha)
                        .midiLearnClickable(
                            slotMidiState,
                            onLearnClick = { onLearnSlot(slotId) },
                            onLearnLongClick = { onUnmapSlot(slotId) },
                            onClick = {
                                if (!isEmpty) {
                                    onSetStageClicked(slotId, setStage)
                                }
                            },
                            onLongClick = {
                                if (onCopy != null || onPaste != null || onDelete != null || onRename != null) {
                                    showDropdownForSlot = slotId
                                }
                            }
                        )
                ) {
                    // ARM BAR (SF2 ARMED STYLE)
                    val barColor = if (isActive) Color(0xFF81C784) else Color(0xFF424242)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(barColor)
                    )

                    // CONTENT
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = setStage?.name ?: "Nenhum",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isEmpty) FontWeight.Normal else FontWeight.Bold,
                            color = if (isEmpty) Color.Gray else Color(0xFF81C784), // Vivid Green if has content
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        if (onCopy != null || onPaste != null || onDelete != null || onRename != null) {
                            DropdownMenu(
                                expanded = showDropdownForSlot == slotId,
                                onDismissRequest = { showDropdownForSlot = null },
                                modifier = Modifier.background(Color(0xFF222222))
                            ) {
                                if (!isEmpty && onRename != null) {
                                    DropdownMenuItem(
                                        text = { Text("Renomear", color = Color.White) },
                                        onClick = {
                                            setStage?.let { onRename(it) }
                                            showDropdownForSlot = null
                                        }
                                    )
                                }

                                if (!isEmpty && onCopy != null) {
                                    DropdownMenuItem(
                                        text = { Text("Copiar", color = Color.White) },
                                        onClick = {
                                            setStage?.let { onCopy(it) }
                                            showDropdownForSlot = null
                                        }
                                    )
                                }
                                
                                if (clipboardSetStage != null && onPaste != null) {
                                    DropdownMenuItem(
                                        text = { Text("Colar", color = Color.White) },
                                        onClick = {
                                            showDropdownForSlot = null
                                            if (!isEmpty) {
                                                slotToConfirmPaste = slotId
                                            } else {
                                                onPaste(slotId, clipboardSetStage)
                                            }
                                        }
                                    )
                                }

                                if (!isEmpty && onDelete != null) {
                                    DropdownMenuItem(
                                        text = { Text("Excluir", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showDropdownForSlot = null
                                            slotToConfirmDelete = slotId
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (slotToConfirmDelete != null && onDelete != null) {
        Dialog(onDismissRequest = { slotToConfirmDelete = null }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E1E),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // HEADER
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Text(
                            text = "Confirmar exclusão",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(24.dp)
                                .clickable { slotToConfirmDelete = null },
                            color = Color(0xFF555555),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Fechar",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Text("Deseja realmente excluir este Set Stage?", color = Color.LightGray)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { slotToConfirmDelete = null }) { 
                            Text("Cancelar", color = Color.Gray) 
                        }
                        TextButton(onClick = {
                            slotToConfirmDelete?.let { onDelete(it) }
                            slotToConfirmDelete = null
                        }) { 
                            Text("Excluir", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) 
                        }
                    }
                }
            }
        }
    }

    if (slotToConfirmPaste != null && onPaste != null && clipboardSetStage != null) {
        Dialog(onDismissRequest = { slotToConfirmPaste = null }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E1E),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // HEADER
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Text(
                            text = "Sobrescrever Set Stage",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(24.dp)
                                .clickable { slotToConfirmPaste = null },
                            color = Color(0xFF555555),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Fechar",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Text("Este slot já possui uma configuração salva. Deseja sobrescrevê-la?", color = Color.LightGray)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { slotToConfirmPaste = null }) { 
                            Text("Cancelar", color = Color.Gray) 
                        }
                        TextButton(onClick = {
                            slotToConfirmPaste?.let { slot ->
                                clipboardSetStage.let { stage -> onPaste(slot, stage) }
                            }
                            slotToConfirmPaste = null
                        }) { 
                            Text("Sobrescrever", color = Color.White, fontWeight = FontWeight.Bold) 
                        }
                    }
                }
            }
        }
    }
}
