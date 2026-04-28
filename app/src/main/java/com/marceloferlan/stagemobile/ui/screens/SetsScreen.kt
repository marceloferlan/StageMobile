package com.marceloferlan.stagemobile.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.marceloferlan.stagemobile.ui.components.BankNavigationPanel
import com.marceloferlan.stagemobile.ui.components.SetStageGrid
import com.marceloferlan.stagemobile.ui.components.StageToastHost
import com.marceloferlan.stagemobile.ui.components.midiLearnHalo
import com.marceloferlan.stagemobile.ui.components.midiLearnClickable
import com.marceloferlan.stagemobile.domain.model.SetStage
import com.marceloferlan.stagemobile.viewmodel.MixerViewModel
import com.marceloferlan.stagemobile.utils.UiUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SetsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MixerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentBank by viewModel.currentViewingBank.collectAsState()
    var loadedSets by remember { mutableStateOf<Map<Int, SetStage>>(emptyMap()) }
    var currentBankName by remember { mutableStateOf<String?>(null) }
    val activeSetStageId by viewModel.activeSetStageId.collectAsState()
    
    // MIDI Learn states
    val isMidiLearnActive by viewModel.isMidiLearnActive.collectAsState()
    val midiLearnTarget by viewModel.midiLearnTarget.collectAsState()
    val midiLearnMappings by viewModel.midiLearnMappings.collectAsState()
    val pulseAlpha = com.marceloferlan.stagemobile.ui.components.rememberMidiLearnPulse(isMidiLearnActive)

    var clipboardSetStage by remember { mutableStateOf<SetStage?>(null) }
    var setStageToRename by remember { mutableStateOf<SetStage?>(null) }
    var showRenameBankDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(currentBank) {
        loadedSets = viewModel.setStageRepo?.getSetStagesForBank(currentBank) ?: emptyMap()
        currentBankName = viewModel.setStageRepo?.getBankName(currentBank)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF1A1A1A),
        topBar = {
            TopAppBar(
                title = { Text("Set Stages", fontWeight = FontWeight.ExtraBold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    // FAVORITE_SET learning button
                    if (isMidiLearnActive) {
                        val favMidiState = com.marceloferlan.stagemobile.ui.components.MidiLearnState(
                            isMidiLearnActive,
                            midiLearnTarget?.target == com.marceloferlan.stagemobile.midi.MidiLearnTarget.FAVORITE_SET,
                            midiLearnMappings.any { it.target == com.marceloferlan.stagemobile.midi.MidiLearnTarget.FAVORITE_SET }
                        )
                        IconButton(
                            onClick = { viewModel.selectFavoriteSetLearnTarget() },
                            modifier = Modifier
                                .midiLearnHalo(favMidiState, RoundedCornerShape(12.dp), pulseAlpha = pulseAlpha)
                                .combinedClickable(
                                    onClick = { viewModel.selectFavoriteSetLearnTarget() },
                                    onLongClick = { if (favMidiState.hasMapping) viewModel.requestUnmapFavoriteSet() }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Map Favorite Set",
                                tint = if (favMidiState.hasMapping) com.marceloferlan.stagemobile.ui.components.MidiLearnDefaults.mappedColor else Color.White
                            )
                        }
                    }

                    // MIDI Learn Button in TopBar of SetsScreen too
                    IconButton(
                        onClick = { viewModel.toggleMidiLearn() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isMidiLearnActive) Color(0xFFFFEE3B) else Color.Transparent,
                            contentColor = if (isMidiLearnActive) Color.Black else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoFixHigh, 
                            contentDescription = "Midi Learn",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111111)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BankNavigationPanel(
                currentBank = currentBank,
                bankName = currentBankName,
                onPrevious = { if (currentBank > 1) viewModel.navBank(-1) },
                onNext = { if (currentBank < 10) viewModel.navBank(1) },
                onRenameBank = { showRenameBankDialog = true },
                isMidiLearnActive = isMidiLearnActive,
                pulseAlpha = pulseAlpha,
                midiLearnTarget = midiLearnTarget,
                midiLearnMappings = midiLearnMappings,
                onLearnPrev = { viewModel.selectBankNavLearnTarget(false) },
                onLearnNext = { viewModel.selectBankNavLearnTarget(true) },
                onUnmapPrev = { viewModel.requestUnmapBankNav(false) },
                onUnmapNext = { viewModel.requestUnmapBankNav(true) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            SetStageGrid(
                currentBank = currentBank,
                loadedSets = loadedSets,
                activeSetStageId = activeSetStageId,
                clipboardSetStage = clipboardSetStage,
                onSetStageClicked = { slotId, _ ->
                    viewModel.loadSetStage(context, currentBank, slotId)
                    onNavigateBack()
                },
                onCopy = { stage -> clipboardSetStage = stage },
                onPaste = { slotId, stageToPaste ->
                    val newStage = stageToPaste.copy(
                        id = "${currentBank}_$slotId",
                        bankId = currentBank,
                        slotId = slotId,
                        name = "${stageToPaste.name} (Copy)"
                    )
                    viewModel.setStageRepo?.saveSetStage(newStage)
                    loadedSets = viewModel.setStageRepo?.getSetStagesForBank(currentBank) ?: emptyMap()
                },
                onDelete = { slotId ->
                    viewModel.setStageRepo?.deleteSetStage(currentBank, slotId)
                    loadedSets = viewModel.setStageRepo?.getSetStagesForBank(currentBank) ?: emptyMap()
                },
                onRename = { stage -> setStageToRename = stage },
                isMidiLearnActive = isMidiLearnActive,
                pulseAlpha = pulseAlpha,
                midiLearnTarget = midiLearnTarget,
                midiLearnMappings = midiLearnMappings,
                onLearnSlot = { slotId -> viewModel.selectSetStageLearnTarget(slotId) },
                onUnmapSlot = { slotId -> viewModel.requestUnmapSetStage(slotId) }
            )
        }
    }

    // --- Rename Dialog ---
    if (setStageToRename != null) {
        var textValue by remember { mutableStateOf(TextFieldValue(setStageToRename?.name ?: "")) }
        
        Dialog(onDismissRequest = { setStageToRename = null }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A1A1A),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // HEADER
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Text(
                            text = "Renomear Set Stage",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(24.dp)
                                .clickable { setStageToRename = null },
                            color = Color(0xFF555555),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Outlined.Close,
                                    contentDescription = "Fechar",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Text("Informe o novo nome para este slot:", color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = textValue,
                        onValueChange = { 
                            if (it.text.length <= 35) {
                                textValue = it
                            } else {
                                // Truncate but keep selection safe
                                textValue = it.copy(text = it.text.take(35))
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF333333),
                            unfocusedContainerColor = Color(0xFF222222)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { setStageToRename = null }) {
                            Text("Cancelar", color = Color.Gray)
                        }
                        TextButton(
                            onClick = {
                                setStageToRename?.let { stage ->
                                    val updatedStage = stage.copy(name = textValue.text)
                                    viewModel.setStageRepo?.saveSetStage(updatedStage)
                                    loadedSets = viewModel.setStageRepo?.getSetStagesForBank(currentBank) ?: emptyMap()
                                }
                                setStageToRename = null
                            }
                        ) {
                            Text("Renomear", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // --- Rename Bank Dialog ---
    if (showRenameBankDialog) {
        var bankTextValue by remember { mutableStateOf(TextFieldValue(currentBankName ?: "")) }
        
        Dialog(onDismissRequest = { showRenameBankDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A1A1A),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // HEADER
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Text(
                            text = "Nomear banco",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(24.dp)
                                .clickable { showRenameBankDialog = false },
                            color = Color(0xFF555555),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Outlined.Close,
                                    contentDescription = "Fechar",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Text("Informe o texto complementar para o Banco $currentBank:", color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = bankTextValue,
                        onValueChange = { 
                            if (it.text.length <= 35) {
                                bankTextValue = it
                            } else {
                                bankTextValue = it.copy(text = it.text.take(35))
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF333333),
                            unfocusedContainerColor = Color(0xFF222222)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showRenameBankDialog = false }) {
                            Text("Cancelar", color = Color.Gray)
                        }
                        TextButton(
                            onClick = {
                                viewModel.setStageRepo?.saveBankName(currentBank, bankTextValue.text)
                                currentBankName = bankTextValue.text
                                showRenameBankDialog = false
                            }
                        ) {
                            Text("Nomear", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
        
        StageToastHost(viewModel)
    }
}

