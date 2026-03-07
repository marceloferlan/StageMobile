package com.example.stagemobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.stagemobile.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.stagemobile.viewmodel.MixerViewModel
import com.example.stagemobile.ui.mixer.ChannelStrip
import com.example.stagemobile.ui.mixer.MasterChannelStrip
import com.example.stagemobile.ui.mixer.VirtualKeyboard
import com.example.stagemobile.ui.mixer.AdvancedParamsDialog
import com.example.stagemobile.ui.components.InfoPanel
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MixerScreen(
    viewModel: MixerViewModel = viewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSets: () -> Unit = {},
    onNavigateToDrumpads: () -> Unit = {},
    onNavigateToContinuousPads: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val channels by viewModel.channels.collectAsState()
    val midiConnected by viewModel.midiDeviceConnected.collectAsState()
    val midiDeviceName by viewModel.midiDeviceName.collectAsState()
    val sf2Loaded by viewModel.sf2Loaded.collectAsState()
    val sf2Name by viewModel.sf2Name.collectAsState()
    val ramUsageMb by viewModel.ramUsageMb.collectAsState()
    val cpuUsagePercent by viewModel.cpuUsagePercent.collectAsState()
    val bufferSize by viewModel.bufferSize.collectAsState()
    val sampleRate by viewModel.sampleRate.collectAsState()

    val isMasterVisible by viewModel.isMasterVisible.collectAsState()
    val masterVolume by viewModel.masterVolume.collectAsState()
    val masterLevel by viewModel.masterLevel.collectAsState()

    var pendingChannelId by remember { mutableIntStateOf(-1) }
    var showKeyboard by remember { mutableStateOf(false) }
    var channelToRemoveSf2 by remember { mutableIntStateOf(-1) }
    var showRangeDialogForChannel by remember { mutableIntStateOf(-1) }
    var rangeDialogSettingMin by remember { mutableStateOf(true) }
    var showAdvancedParamsForChannel by remember { mutableStateOf<Int?>(null) }
    var showInfoPanel by remember { mutableStateOf(false) }
    
    // Obter canais e informações extras do ViewModel
    val activeMidiDevices by viewModel.activeMidiDevices.collectAsState()
    var keyboardOffsetX by remember { mutableFloatStateOf(0f) }
    var keyboardOffsetY by remember { mutableFloatStateOf(200f) }

    val sf2Picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && pendingChannelId >= 0) {
            viewModel.loadSoundFontForChannel(context, pendingChannelId, uri)
        }
        pendingChannelId = -1
    }

    LaunchedEffect(Unit) {
        viewModel.initMidi(context, isTablet)
    }

    // === Remove SF2 Confirmation Dialog ===
    if (channelToRemoveSf2 >= 0) {
        val ch = channels.firstOrNull { it.id == channelToRemoveSf2 }
        if (ch != null) {
            Dialog(onDismissRequest = { channelToRemoveSf2 = -1 }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2C2C2C),
                    tonalElevation = 6.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Remover instrumento SF2?",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            color = Color.White
                        )
                        HorizontalDivider(thickness = 1.dp, color = Color(0xFF424242))
                        Text(
                            text = "Deseja remover \"${ch.soundFont ?: ""}\" do canal ${channelToRemoveSf2 + 1}?",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            textAlign = TextAlign.Center,
                            color = Color(0xFFAAAAAA)
                        )
                        HorizontalDivider(thickness = 1.dp, color = Color(0xFF424242))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { channelToRemoveSf2 = -1 }) {
                                Text("Cancelar", color = Color.White)
                            }
                            TextButton(onClick = {
                                viewModel.removeSoundFont(channelToRemoveSf2)
                                channelToRemoveSf2 = -1
                            }) {
                                Text("Remover", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // === Range Selector Form Dialog ===
    if (showRangeDialogForChannel >= 0) {
        val ch = channels.firstOrNull { it.id == showRangeDialogForChannel }
        if (ch != null) {
            Dialog(onDismissRequest = { showRangeDialogForChannel = -1 }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.6f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2C2C2C),
                    tonalElevation = 6.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (rangeDialogSettingMin) "Selecionar Nota Inicial" else "Selecionar Extensão",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            color = Color.White
                        )
                        HorizontalDivider(thickness = 1.dp, color = Color(0xFF424242))
                        LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                            items(128) { midiNote ->
                                val isInvalid = if (rangeDialogSettingMin) {
                                    midiNote > ch.maxNote
                                } else {
                                    midiNote < ch.minNote
                                }
                                TextButton(
                                    onClick = {
                                        if (!isInvalid) {
                                            if (rangeDialogSettingMin) {
                                                viewModel.updateChannelKeyRange(ch.id, midiNote, ch.maxNote)
                                            } else {
                                                viewModel.updateChannelKeyRange(ch.id, ch.minNote, midiNote)
                                            }
                                            showRangeDialogForChannel = -1
                                        }
                                    },
                                    enabled = !isInvalid,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = com.example.stagemobile.ui.mixer.getMidiNoteName(midiNote) + " ($midiNote)", 
                                        color = if (isInvalid) Color(0xFF555555) else Color(0xFF81C784)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = Color(0xFF424242))
                        TextButton(
                            onClick = { showRangeDialogForChannel = -1 },
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text("Cancelar", color = Color(0xFFEF5350))
                        }
                    }
                }
            }
        }
    }

    // === MIDI Channel Selector Dialog (Moved to Advanced Options Modal) ===


    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {
            // === Top Bar ===
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF111111),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Menu Icons (Weight 1, Aligned Start)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Settings Toggle (Engrenagem)
                            Button(
                                onClick = onNavigateToSettings,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF424242),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Configurações", modifier = Modifier.size(20.dp))
                            }

                            Button(
                                onClick = onNavigateToSets,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242), contentColor = Color.White),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Sets", modifier = Modifier.size(20.dp))
                            }
                            
                            Button(
                                onClick = onNavigateToDrumpads,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242), contentColor = Color.White),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Outlined.Apps, contentDescription = "Drumpads", modifier = Modifier.size(20.dp))
                            }
                            
                            Button(
                                onClick = onNavigateToContinuousPads,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242), contentColor = Color.White),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Outlined.Loop, contentDescription = "Pads Contínuos", modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Center Menu (Weight 1, Aligned Center)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_stage_mobile),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Stage Mobile ®",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Right Menu Icons (Weight 1, Aligned End)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Downloads Screen
                            Button(
                                onClick = onNavigateToDownloads,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242), contentColor = Color.White),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = "Downloads", modifier = Modifier.size(20.dp))
                            }
                            
                            // MIDI Learn (Placeholder)
                            Button(
                                onClick = { /* TODO: Implementar MIDI Learn futuramente */ },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF424242),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "MIDI Learn", modifier = Modifier.size(20.dp))
                            }

                            // Add Channel (+ CH)
                            Button(
                                onClick = {
                                    val nextNum = channels.size + 1
                                    val formattedNum = nextNum.toString().padStart(2, '0')
                                    viewModel.addChannel("Instrumento $formattedNum")
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF424242),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("+ CH", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Keyboard Toggle
                            Button(
                                onClick = { showKeyboard = !showKeyboard },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (showKeyboard) Color(0xFF81C784) else Color(0xFF424242),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Outlined.Piano, contentDescription = "Teclado", modifier = Modifier.size(20.dp))
                            }

                            // Master Toggle Icon
                            Button(
                                onClick = { viewModel.toggleMasterVisibility() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMasterVisible) Color(0xFF81C784) else Color(0xFF424242),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Outlined.GraphicEq, contentDescription = "Master", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // === Info Panel (Tablet & Phone) ===
            InfoPanel(
                ramUsage = ramUsageMb,
                cpuUsage = cpuUsagePercent.toInt(),
                activeSet = "LIVE PERFORMANCE ALPHA",
                midiStatus = if (midiConnected) midiDeviceName ?: "CONECTADO" else "NENHUM DISPOSITIVO",
                lastEvent = "ENGINE READY - BUFFER: $bufferSize",
                sampleRate = sampleRate,
                isTablet = isTablet
            )

            // === Mixer Area ===
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1F1F1F))
            ) {
                // To fit exactly 7 (phone) or 8 (tablet) channels
                val channelWidth = if (isTablet) maxWidth / 8 else maxWidth / 7
                val heightPx = constraints.maxHeight.toFloat()
                val density = LocalDensity.current.density
                val isLargeScreen = heightPx >= 400f * density
                
                // Master width is 122dp on phone, 183dp on tablet. 
                // We add a bit of buffer for the master's left padding (8dp)
                val masterPadding = if (isLargeScreen) 166.5.dp else 130.dp

                // Dynamic Channels Horizontal Scroll Area
                val scrollState = rememberScrollState()
                
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState)
                        .padding(end = if (isMasterVisible) masterPadding else 0.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    channels.forEach { channel ->
                        ChannelStrip(
                            channel = channel,
                            modifier = Modifier.width(channelWidth),
                            onVolumeChange = { viewModel.updateVolume(channel.id, it) },
                            onMinNoteClick = { 
                                showRangeDialogForChannel = channel.id
                                rangeDialogSettingMin = true
                            },
                            onMaxNoteClick = { 
                                showRangeDialogForChannel = channel.id
                                rangeDialogSettingMin = false
                            },
                            onArmToggle = { viewModel.toggleArm(channel.id) },
                            onNameClick = {
                                pendingChannelId = channel.id
                                sf2Picker.launch(arrayOf("*/*"))
                            },
                            onNameLongClick = {
                                channelToRemoveSf2 = channel.id
                            },
                            onMidiChannelClick = {
                                // Moved to Advanced Options
                            },
                            onAdvancedOptionsClick = {
                                showAdvancedParamsForChannel = channel.id
                            }
                        )
                    }
                }

                // === Master Channel Overlay (instant, no animation) ===
                if (isMasterVisible) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MasterChannelStrip(
                            volume = masterVolume,
                            level = masterLevel,
                            onVolumeChange = { viewModel.updateMasterVolume(it) }
                        )
                    }
                }
            }

                // === Advanced Params Dialog ===
                if (showAdvancedParamsForChannel != null) {
                    val targetCh = channels.firstOrNull { it.id == showAdvancedParamsForChannel }
                    if (targetCh != null) {
                        AdvancedParamsDialog(
                            channel = targetCh,
                            activeMidiDevices = activeMidiDevices,
                            onDismiss = { showAdvancedParamsForChannel = null },
                            onMidiDeviceSelected = { devName ->
                                viewModel.updateChannelMidiDevice(targetCh.id, devName)
                            },
                            onMidiChannelSelected = { midiCh ->
                                viewModel.updateChannelMidiChannel(targetCh.id, midiCh)
                            }
                        )
                    } else {
                        showAdvancedParamsForChannel = null
                    }
                }

        }

        // === Floating Draggable Virtual Keyboard ===
        if (showKeyboard) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(keyboardOffsetX.roundToInt(), keyboardOffsetY.roundToInt()) }
                    .width(IntrinsicSize.Min)
                    .background(Color(0xDD1A1A1A), RoundedCornerShape(12.dp))
                    .padding(2.dp)
            ) {
                Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                    // Drag handle / title bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF333333), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    keyboardOffsetX += dragAmount.x
                                    keyboardOffsetY += dragAmount.y
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(Icons.Outlined.Piano, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Teclado Virtual",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(24.dp)
                                .clickable { showKeyboard = false },
                            color = Color(0xFF555555),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // Keyboard itself
                    VirtualKeyboard(
                        onNoteOn = { viewModel.noteOn(it) },
                        onNoteOff = { viewModel.noteOff(it) }
                    )
                }
            }
        }

        // Advanced Parameters Dialog
        showAdvancedParamsForChannel?.let { channelId ->
            val channel = channels.find { it.id == channelId }
            if (channel != null) {
                AdvancedParamsDialog(
                    channel = channel,
                    activeMidiDevices = activeMidiDevices,
                    onDismiss = { showAdvancedParamsForChannel = null },
                    onMidiDeviceSelected = { deviceName -> 
                        viewModel.updateChannelMidiDevice(channelId, deviceName)
                    },
                    onMidiChannelSelected = { midiCh ->
                        viewModel.updateChannelMidiChannel(channelId, midiCh)
                    }
                )
            }
        }
    }
}