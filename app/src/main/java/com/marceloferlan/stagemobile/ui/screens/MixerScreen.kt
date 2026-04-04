package com.marceloferlan.stagemobile.ui.screens

import com.marceloferlan.stagemobile.utils.UiUtils
import com.marceloferlan.stagemobile.utils.getMidiNoteName
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.marceloferlan.stagemobile.ui.components.StageToastHost

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import com.marceloferlan.stagemobile.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marceloferlan.stagemobile.viewmodel.MixerViewModel
import com.marceloferlan.stagemobile.ui.mixer.InstrumentChannelStrip
import com.marceloferlan.stagemobile.ui.mixer.MasterChannelStrip
import com.marceloferlan.stagemobile.ui.mixer.VirtualKeyboard
import com.marceloferlan.stagemobile.ui.mixer.InstrumentChannelSettingsPanel
import com.marceloferlan.stagemobile.ui.components.MixerScreenInfoPanel
import com.marceloferlan.stagemobile.ui.components.SF2PresetSelectorDialog
import com.marceloferlan.stagemobile.ui.components.SetStageQuickSelectorDialog
import com.marceloferlan.stagemobile.ui.components.InstrumentChannelOptionsMenu
import com.marceloferlan.stagemobile.ui.components.MidiLearnState
import com.marceloferlan.stagemobile.ui.components.DSPEffectsRackDialog
import com.marceloferlan.stagemobile.ui.components.midiLearnHalo
import com.marceloferlan.stagemobile.ui.components.midiLearnClickable
import com.marceloferlan.stagemobile.ui.components.rememberMidiLearnPulse
import com.marceloferlan.stagemobile.midi.MidiLearnTarget
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MixerScreen(
    viewModel: MixerViewModel = viewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSets: () -> Unit = {},
    onNavigateToDrumpads: () -> Unit = {},
    onNavigateToContinuousPads: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToSf2Maintenance: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTablet = UiUtils.rememberIsTablet()
    val scope = rememberCoroutineScope()
    val channels by viewModel.channels.collectAsState()
    val midiConnected by viewModel.midiDeviceConnected.collectAsState()
    val midiDeviceName by viewModel.midiDeviceName.collectAsState()
    val sf2Loaded by viewModel.sf2Loaded.collectAsState()
    val sf2Name by viewModel.sf2Name.collectAsState()
    val ramUsageMb by viewModel.ramUsageMb.collectAsState()
    val cpuUsagePercent by viewModel.cpuUsagePercent.collectAsState()
    val bufferSize by viewModel.bufferSize.collectAsState()
    val sampleRate by viewModel.sampleRate.collectAsState()
    val audioInterfaceName by viewModel.selectedAudioDeviceName.collectAsState()
    val availableMidiDevices by viewModel.availableMidiDevices.collectAsState()
    // Removed the global array collection here to stop massive Recompositions

    val isMasterVisible by viewModel.isMasterVisible.collectAsState()
    val masterVolume by viewModel.masterVolume.collectAsState()
    val masterLevel by viewModel.masterLevel.collectAsState()
    val masterDspEffects by viewModel.masterDspEffects.collectAsState()
    
    var pendingChannelId by remember { mutableIntStateOf(-1) }
    var showKeyboard by remember { mutableStateOf(false) }
    var showRangeDialogForChannel by remember { mutableIntStateOf(-1) }

    var showInfoPanel by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    
    // Configurações do Set Stage
    var showSaveSetStageOptions by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    // showQuickSelector and other visibility states are now in ViewModel (Phase 5)
    var showSaveCurrentConfirmation by remember { mutableStateOf(false) }
    
    // Obter canais e informações extras do ViewModel
    val activeMidiDevices by viewModel.activeMidiDevices.collectAsState()
    val pendingPresetSelection by viewModel.pendingPresetSelection.collectAsState()
    val isMidiLearnActive by viewModel.isMidiLearnActive.collectAsState()
    val midiLearnTarget by viewModel.midiLearnTarget.collectAsState()
    val midiLearnFeedback by viewModel.midiLearnFeedback.collectAsState()
    val midiLearnMappings by viewModel.midiLearnMappings.collectAsState()
    val pendingUnmap by viewModel.pendingUnmap.collectAsState()
    var keyboardOffsetX by remember { mutableFloatStateOf(0f) }
    var keyboardOffsetY by remember { mutableFloatStateOf(200f) }

    // States for SetStage SaveAs (local to screen is OK)
    val activeSetStageName by viewModel.activeSetStageName.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    var newSetStageName by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val globalOctave by viewModel.globalOctaveShift.collectAsState()
    val globalTranspose by viewModel.globalTransposeShift.collectAsState()

    val sf2Picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && pendingChannelId >= 0) {
            viewModel.loadSoundFontForChannel(context, pendingChannelId, uri)
        }
        pendingChannelId = -1
    }


    // === Remove FULL CHANNEL Confirmation Dialog ===



    // Range selector is now handled in Advanced Settings Overlay or here if needed
    // Assuming for now it's not needed as local Dialog here.

    // === Exit System Confirmation Dialog ===
    if (showExitConfirmation) {
        Dialog(onDismissRequest = { showExitConfirmation = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.8f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2C2C2C),
                tonalElevation = 6.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Encerrar Stage Mobile?",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color(0xFF424242)))
                    Text(
                        text = "Isso desligará todos os motores de áudio, MIDI e processos em execução. Confirmar saída?",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        textAlign = TextAlign.Center,
                        color = Color(0xFFAAAAAA)
                    )
                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color(0xFF424242)))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { showExitConfirmation = false }) {
                            Text("Cancelar", color = Color.White)
                        }
                        TextButton(onClick = {
                            viewModel.exitSystem()
                            (context as? android.app.Activity)?.finishAndRemoveTask()
                            android.os.Process.killProcess(android.os.Process.myPid())
                            java.lang.System.exit(0)
                        }) {
                            Text("SAIR", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // === Save As Dialog ===
    if (showSaveAsDialog) {
        Dialog(onDismissRequest = { showSaveAsDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2C2C2C),
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // HEADER
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Text(
                            text = "Salvar Novo Set Stage",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(24.dp)
                                .clickable { showSaveAsDialog = false },
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
                    
                    OutlinedTextField(
                        value = newSetStageName,
                        onValueChange = { if (it.length <= 35) newSetStageName = it },
                        label = { Text("Nome do Set", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF81C784),
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "O sistema salvará automaticamente no próximo slot livre disponível.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showSaveAsDialog = false }) {
                            Text("Cancelar", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newSetStageName.isNotBlank()) {
                                    viewModel.saveAsNewSetStage(newSetStageName)
                                    showSaveAsDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784), contentColor = Color.Black)
                        ) {
                            Text("Salvar")
                        }
                    }
                }
            }
        }
    }


    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF1A1A1A),
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier
                    .width(if (isTablet) 320.dp else 280.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(if (isTablet) 24.dp else 12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = if (isTablet) 16.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_topbar),
                        contentDescription = null,
                        modifier = Modifier.size(if (isTablet) 44.dp else 36.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Stage Mobile ®",
                        color = Color.White,
                        fontSize = if (isTablet) 24.sp else 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                val drawerItemHeight = if (isTablet) 48.dp else 28.dp
                val drawerItemPadding = if (isTablet) NavigationDrawerItemDefaults.ItemPadding else PaddingValues(horizontal = 12.dp, vertical = 4.dp)

                NavigationDrawerItem(
                    label = { Text("Configurações", fontSize = 16.sp) }, // Removido if redundante
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onNavigateToSettings() 
                    },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(if (isTablet) 24.dp else 20.dp)) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedIconColor = Color.White,
                        unselectedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(drawerItemPadding)
                        .height(drawerItemHeight)
                )

                NavigationDrawerItem(
                    label = { Text("Set Stages", fontSize = 16.sp) },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onNavigateToSets() 
                    },
                    icon = { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = null, modifier = Modifier.size(if (isTablet) 24.dp else 20.dp)) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedIconColor = Color.White,
                        unselectedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(drawerItemPadding)
                        .height(drawerItemHeight)
                )

                NavigationDrawerItem(
                    label = { Text("Drumpads", fontSize = 16.sp) },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onNavigateToDrumpads() 
                    },
                    icon = { Icon(Icons.Outlined.Apps, contentDescription = null, modifier = Modifier.size(if (isTablet) 24.dp else 20.dp)) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedIconColor = Color.White,
                        unselectedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(drawerItemPadding)
                        .height(drawerItemHeight)
                )

                NavigationDrawerItem(
                    label = { Text("Pads Contínuos", fontSize = 16.sp) },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onNavigateToContinuousPads() 
                    },
                    icon = { Icon(Icons.Outlined.Loop, contentDescription = null, modifier = Modifier.size(if (isTablet) 24.dp else 20.dp)) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedIconColor = Color.White,
                        unselectedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(drawerItemPadding)
                        .height(drawerItemHeight)
                )

                // NOVO ITEM: BIBLIOTECA SF2
                NavigationDrawerItem(
                    label = { Text("Biblioteca SF2", fontSize = 16.sp) },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onNavigateToSf2Maintenance()
                    },
                    icon = { Icon(Icons.Outlined.LibraryMusic, contentDescription = null, modifier = Modifier.size(if (isTablet) 24.dp else 20.dp)) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedIconColor = Color.White,
                        unselectedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(drawerItemPadding)
                        .height(drawerItemHeight)
                )

                NavigationDrawerItem(
                    label = { Text("Downloads", fontSize = 16.sp) },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onNavigateToDownloads() 
                    },
                    icon = { Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(if (isTablet) 24.dp else 20.dp)) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedIconColor = Color.White,
                        unselectedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(drawerItemPadding)
                        .height(drawerItemHeight)
                )

                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(horizontal = 16.dp, vertical = if (isTablet) 8.dp else 4.dp))

                NavigationDrawerItem(
                    label = { Text(if (showKeyboard) "Ocultar Teclado" else "Mostrar Teclado", fontSize = 16.sp) },
                    selected = showKeyboard,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        showKeyboard = !showKeyboard 
                    },
                    icon = { Icon(Icons.Outlined.Piano, contentDescription = null, modifier = Modifier.size(if (isTablet) 24.dp else 20.dp)) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color(0xFF81C784).copy(alpha = 0.2f),
                        selectedIconColor = Color(0xFF81C784),
                        selectedTextColor = Color(0xFF81C784),
                        unselectedContainerColor = Color.Transparent,
                        unselectedIconColor = Color.White,
                        unselectedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(drawerItemPadding)
                        .height(drawerItemHeight)
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // === Top Bar ===
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1A1A1A), // Sombreamento/Profundidade (Base)
                    tonalElevation = 4.dp
                ) {
                    // Face da TopBar (Retângulo cinza dos InstrumentChannelStrip)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 0.dp), // Gap de 1dp para aparecer a base escura
                        color = Color(0xFF242424)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = if (isTablet) 6.dp else 4.dp, bottom = if (isTablet) 6.dp else 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                                    }
                                }

                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            painter = painterResource(id = R.drawable.logo_topbar),
                                            contentDescription = null,
                                            modifier = Modifier.width(52.dp).height(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Stage Mobile",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                    Button(
                                        onClick = { showExitConfirmation = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242), contentColor = Color.White),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Outlined.PowerSettingsNew, contentDescription = "Sair", modifier = Modifier.size(18.dp), tint = Color(0xFFEF5350))
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFF555555).copy(alpha = 0.5f), thickness = 1.dp)
                        }
                    }
                }

                MixerScreenInfoPanel(
                    ramUsage = ramUsageMb,
                    cpuUsage = cpuUsagePercent.toInt(),
                    activeSet = if (activeSetStageName != null) activeSetStageName!! + (if (hasUnsavedChanges) " *" else "") else "Nenhum",
                    midiStatus = if (midiConnected) midiDeviceName ?: "CONECTADO" else "NENHUM DISPOSITIVO",
                    lastEvent = "BUFFER: $bufferSize",
                    audioInterface = audioInterfaceName,
                    sampleRate = sampleRate,
                    isTablet = isTablet,
                    midiLearnFeedback = midiLearnFeedback,
                    onActiveSetLongClick = { viewModel.showQuickSelector() }
                )

                MixerScreenToolBar(
                    viewModel = viewModel,
                    globalOctave = globalOctave,
                    globalTranspose = globalTranspose,
                    isMidiLearnActive = isMidiLearnActive,
                    midiLearnTarget = midiLearnTarget,
                    midiLearnMappings = midiLearnMappings,
                    isTablet = isTablet,
                    activeSetStageName = activeSetStageName,
                    hasUnsavedChanges = hasUnsavedChanges,
                    onSaveClick = {
                        if (activeSetStageName == null) {
                            showSaveAsDialog = true
                        } else {
                            showSaveSetStageOptions = true
                        }
                    },
                    showSaveSetStageOptions = showSaveSetStageOptions,
                    onDismissSaveOptions = { showSaveSetStageOptions = false },
                    onSaveCurrent = { showSaveCurrentConfirmation = true },
                    onSaveAs = {
                        newSetStageName = ""
                        showSaveAsDialog = true
                    },
                    channelsCount = channels.size,
                    onAddChannel = {
                        val nextNum = channels.size + 1
                        val formattedNum = nextNum.toString().padStart(2, '0')
                        viewModel.addChannel("Instrumento $formattedNum")
                    },
                    isMasterVisible = isMasterVisible,
                    onToggleMaster = { viewModel.toggleMasterVisibility() }
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF1F1F1F))
                ) {
                    val channelWidth = if (isTablet) maxWidth / 8 else maxWidth / 7
                    val heightPx = constraints.maxHeight.toFloat()
                    val density = LocalDensity.current.density
                    val isLargeScreen = heightPx >= 400f * density
                    val masterPadding = if (isLargeScreen) 166.5.dp else 130.dp
                    val scrollState = rememberScrollState()
                    
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(scrollState)
                            .padding(end = if (isMasterVisible) masterPadding else 0.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        channels.forEachIndexed { index, channel ->
                            InstrumentChannelStrip(
                                channel = channel,
                                channelIndex = index + 1,
                                levelFlow = if (channel.id in viewModel.channelLevels.indices) viewModel.channelLevels[channel.id] else MutableStateFlow(0f),
                                modifier = Modifier.width(channelWidth),
                                onVolumeChange = { viewModel.updateVolume(channel.id, it) },
                                onOctaveDown = { viewModel.updateOctaveShift(channel.id, -1) },
                                onOctaveUp = { viewModel.updateOctaveShift(channel.id, 1) },
                                onArmToggle = { viewModel.toggleArm(channel.id) },
                                onNameClick = {
                                    if (channel.soundFont == null) {
                                        viewModel.showSoundFontSelector(channel.id)
                                    } else {
                                        viewModel.changePresetForChannel(channel.id)
                                    }
                                },
                                onNameLongClick = {
                                     if (channel.soundFont != null) {
                                        viewModel.showUnloadConfirmation(channel.id)
                                    } else {
                                        viewModel.showSoundFontSelector(channel.id)
                                    }
                                },
                                onInstrumentChannelClick = { viewModel.showChannelOptions(channel.id) },
                                onAdvancedOptionsClick = { viewModel.showChannelAdvancedSettings(channel.id) },
                                onDSPEffectsClick = {
                                    viewModel.showEffectsRack(channel.id)
                                },
                                activeMidiDevices = activeMidiDevices,
                                availableMidiDevices = availableMidiDevices,
                                isMidiLearnActive = isMidiLearnActive,
                                isLearnTargetFader = midiLearnTarget?.target == MidiLearnTarget.FADER && midiLearnTarget?.channelId == channel.id,
                                isLearnTargetArm = midiLearnTarget?.target == MidiLearnTarget.ARM && midiLearnTarget?.channelId == channel.id,
                                isLearnTargetOctaveUp = midiLearnTarget?.target == MidiLearnTarget.OCTAVE_UP && midiLearnTarget?.channelId == channel.id,
                                isLearnTargetOctaveDown = midiLearnTarget?.target == MidiLearnTarget.OCTAVE_DOWN && midiLearnTarget?.channelId == channel.id,
                                hasFaderMapping = midiLearnMappings.any { it.target == MidiLearnTarget.FADER && it.channelId == channel.id },
                                hasArmMapping = midiLearnMappings.any { it.target == MidiLearnTarget.ARM && it.channelId == channel.id },
                                hasOctaveUpMapping = midiLearnMappings.any { it.target == MidiLearnTarget.OCTAVE_UP && it.channelId == channel.id },
                                hasOctaveDownMapping = midiLearnMappings.any { it.target == MidiLearnTarget.OCTAVE_DOWN && it.channelId == channel.id },
                                onLearnFaderClick = { viewModel.selectLearnTarget(MidiLearnTarget.FADER, channel.id) },
                                onLearnArmClick = { viewModel.selectLearnTarget(MidiLearnTarget.ARM, channel.id) },
                                onLearnOctaveUpClick = { viewModel.selectLearnTarget(MidiLearnTarget.OCTAVE_UP, channel.id) },
                                onLearnOctaveDownClick = { viewModel.selectLearnTarget(MidiLearnTarget.OCTAVE_DOWN, channel.id) },
                                onLearnFaderLongClick = { viewModel.requestUnmap(MidiLearnTarget.FADER, channel.id) },
                                onLearnArmLongClick = { viewModel.requestUnmap(MidiLearnTarget.ARM, channel.id) },
                                onLearnOctaveUpLongClick = { viewModel.requestUnmap(MidiLearnTarget.OCTAVE_UP, channel.id) },
                                onLearnOctaveDownLongClick = { viewModel.requestUnmap(MidiLearnTarget.OCTAVE_DOWN, channel.id) },
                                onColorChange = { color -> viewModel.updateChannelColor(channel.id, color) },
                            )
                        }
                    }

                    if (isMasterVisible) {
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MasterChannelStrip(
                                volume = masterVolume,
                                level = masterLevel,
                                isMasterLimiterEnabled = viewModel.isMasterLimiterEnabled.collectAsState().value,
                                onMasterLimiterToggle = { viewModel.updateMasterLimiter(it) },
                                onVolumeChange = { viewModel.updateMasterVolume(it) },
                                onFxClick = { viewModel.showEffectsRack(MixerViewModel.MASTER_CHANNEL_ID) },
                                isMidiLearnActive = isMidiLearnActive,
                                isLearnTargetFader = midiLearnTarget?.target == MidiLearnTarget.FADER && midiLearnTarget?.channelId == MixerViewModel.MASTER_CHANNEL_ID,
                                hasFaderMapping = midiLearnMappings.any { it.target == MidiLearnTarget.FADER && it.channelId == MixerViewModel.MASTER_CHANNEL_ID },
                                onLearnFaderClick = { viewModel.selectLearnTarget(MidiLearnTarget.FADER, MixerViewModel.MASTER_CHANNEL_ID) },
                                onLearnFaderLongClick = { viewModel.requestUnmap(MidiLearnTarget.FADER, MixerViewModel.MASTER_CHANNEL_ID) }
                            )
                        }
                    }
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
                            Text(text = "Teclado Virtual", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.CenterEnd).size(24.dp).clickable { showKeyboard = false },
                            color = Color(0xFF555555),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    VirtualKeyboard(onNoteOn = { viewModel.noteOn(it) }, onNoteOff = { viewModel.noteOff(it) })
                }
            }
        }



    // === Save Current Confirmation Dialog ===
    if (showSaveCurrentConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSaveCurrentConfirmation = false },
            title = { Text("Confirmar Salvamento", color = Color.White) },
            text = { 
                Column {
                    Text("Isso irá sobrescrever as configurações do Set Stage '${activeSetStageName}'.", color = Color.LightGray)
                    Text("Deseja continuar?", color = Color.LightGray)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveCurrentConfirmation = false
                        activeSetStageName?.let { viewModel.saveCurrentSetStage(it) }
                    }
                ) {
                    Text("SALVAR", color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveCurrentConfirmation = false }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }


    // --- ELEGANT TOAST NOTIFICATION ---
    // --- REUSABLE TOAST SYSTEM ---
    StageToastHost(viewModel)
}
}

@Composable
fun MixerScreenToolBar(
    viewModel: MixerViewModel,
    globalOctave: Int,
    globalTranspose: Int,
    isMidiLearnActive: Boolean,
    midiLearnTarget: com.marceloferlan.stagemobile.midi.MidiLearnTargetInfo?,
    midiLearnMappings: List<com.marceloferlan.stagemobile.midi.MidiLearnMapping>,
    isTablet: Boolean,
    activeSetStageName: String?,
    hasUnsavedChanges: Boolean,
    onSaveClick: () -> Unit,
    showSaveSetStageOptions: Boolean,
    onDismissSaveOptions: () -> Unit,
    onSaveCurrent: () -> Unit,
    onSaveAs: () -> Unit,
    channelsCount: Int,
    onAddChannel: () -> Unit,
    isMasterVisible: Boolean,
    onToggleMaster: () -> Unit
) {
    val pulseAlpha = rememberMidiLearnPulse(isMidiLearnActive)
    val barHeight = if (isTablet) 38.dp else 34.dp
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight),
        color = Color(0xFF1A1A1A)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            // --- LEFT: SAVE + MIDI LEARN ---
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box {
                    Button(
                        onClick = onSaveClick,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2), contentColor = Color.White),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(height = barHeight - 4.dp, width = (barHeight - 4.dp) * 1.3f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Salvar",
                            modifier = Modifier.size(if (isTablet) 20.dp else 16.dp)
                        )
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = showSaveSetStageOptions,
                        onDismissRequest = onDismissSaveOptions,
                        modifier = Modifier.background(Color(0xFF222222))
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Salvar atual", color = Color.White) },
                            onClick = {
                                onDismissSaveOptions()
                                onSaveCurrent()
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Salvar como...", color = Color.White) },
                            onClick = {
                                onDismissSaveOptions()
                                onSaveAs()
                            }
                        )
                    }
                }

                // MIDI Learn Button
                Button(
                    onClick = { viewModel.toggleMidiLearn() },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMidiLearnActive) Color(0xFFFFEE3B) else Color(0xFF424242),
                        contentColor = if (isMidiLearnActive) Color.Black else Color.White
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(height = barHeight - 4.dp, width = (barHeight - 4.dp) * 1.3f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = "MIDI Learn",
                        modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
                    )
                }
            }

            // --- CENTER: GLOBAL CONTROLS ---
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- GLOBAL OCTAVE ---
                NoteShiftControl(
                    label = "OITAVAS",
                    value = globalOctave,
                    unit = "oct",
                    onDelta = { viewModel.updateGlobalOctaveShift(it) },
                    isMidiLearnActive = isMidiLearnActive,
                    pulseAlpha = pulseAlpha,
                    targetUp = com.marceloferlan.stagemobile.midi.MidiLearnTarget.OCTAVE_UP,
                    targetDown = com.marceloferlan.stagemobile.midi.MidiLearnTarget.OCTAVE_DOWN,
                    midiLearnTarget = midiLearnTarget,
                    midiLearnMappings = midiLearnMappings,
                    viewModel = viewModel,
                    isTablet = isTablet,
                    labelOnLeft = true
                )

                Spacer(modifier = Modifier.width(if (isTablet) 16.dp else 8.dp))
                
                // Vertical Divider
                Box(modifier = Modifier.width(1.dp).height(if (isTablet) 18.dp else 10.dp).background(Color(0xFF444444)))
                
                Spacer(modifier = Modifier.width(if (isTablet) 16.dp else 8.dp))

                // --- GLOBAL TAP DELAY ---
                val tapBtnSize = if (isTablet) 26.dp else 28.dp
                val tapState = com.marceloferlan.stagemobile.ui.components.MidiLearnState(
                    isMidiLearnActive,
                    midiLearnTarget?.target == com.marceloferlan.stagemobile.midi.MidiLearnTarget.TAP_DELAY && midiLearnTarget?.channelId == com.marceloferlan.stagemobile.viewmodel.MixerViewModel.GLOBAL_CHANNEL_ID,
                    midiLearnMappings.any { it.target == com.marceloferlan.stagemobile.midi.MidiLearnTarget.TAP_DELAY && it.channelId == com.marceloferlan.stagemobile.viewmodel.MixerViewModel.GLOBAL_CHANNEL_ID }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TAP", // Shorter label for better fit
                        color = Color.Gray,
                        fontSize = if (isTablet) 8.sp else 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = if (isTablet) 6.dp else 3.dp)
                    )
                    Surface(
                        modifier = Modifier
                            .size(tapBtnSize)
                            .midiLearnHalo(tapState, RoundedCornerShape(4.dp), pulseAlpha = pulseAlpha)
                            .midiLearnClickable(
                                tapState,
                                onLearnClick = { viewModel.selectLearnTarget(com.marceloferlan.stagemobile.midi.MidiLearnTarget.TAP_DELAY, com.marceloferlan.stagemobile.viewmodel.MixerViewModel.GLOBAL_CHANNEL_ID) },
                                onLearnLongClick = { viewModel.requestUnmap(com.marceloferlan.stagemobile.midi.MidiLearnTarget.TAP_DELAY, com.marceloferlan.stagemobile.viewmodel.MixerViewModel.GLOBAL_CHANNEL_ID) },
                                onClick = { viewModel.tapGlobalDelayTime() }
                            ),
                        color = Color(0xFF222222),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF444444))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Indicador geométrico circular para precisão absoluta (evita baseline de fontes)
                            Box(
                                modifier = Modifier
                                    .size(if (isTablet) 9.dp else 8.dp)
                                    .background(Color(0xFFBA68C8), androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(if (isTablet) 16.dp else 8.dp))
                
                // Vertical Divider
                Box(modifier = Modifier.width(1.dp).height(if (isTablet) 18.dp else 10.dp).background(Color(0xFF444444)))
                
                Spacer(modifier = Modifier.width(if (isTablet) 16.dp else 8.dp))

                // --- GLOBAL TRANSPOSE ---
                NoteShiftControl(
                    label = "TRANSPOSE",
                    value = globalTranspose,
                    unit = "st",
                    onDelta = { delta -> viewModel.updateGlobalTransposeShift(delta) },
                    isMidiLearnActive = isMidiLearnActive,
                    pulseAlpha = pulseAlpha,
                    targetUp = com.marceloferlan.stagemobile.midi.MidiLearnTarget.TRANSPOSE_UP,
                    targetDown = com.marceloferlan.stagemobile.midi.MidiLearnTarget.TRANSPOSE_DOWN,
                    midiLearnTarget = midiLearnTarget,
                    midiLearnMappings = midiLearnMappings,
                    viewModel = viewModel,
                    isTablet = isTablet,
                    labelOnLeft = false
                )
            }

            // --- RIGHT: CH + MASTER + PANIC ---
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Button + CH
                Button(
                    onClick = onAddChannel,
                    enabled = channelsCount < 16,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF424242),
                        contentColor = Color(0xFF81C784),
                        disabledContainerColor = Color(0xFF222222),
                        disabledContentColor = Color.DarkGray
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(height = barHeight - 4.dp, width = (barHeight - 4.dp) * 1.3f)
                ) {
                    Text("+CH", fontWeight = FontWeight.Bold, fontSize = if (isTablet) 10.sp else 8.sp)
                }

                // Button Master
                Button(
                    onClick = onToggleMaster,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMasterVisible) Color(0xFF81C784) else Color(0xFF424242),
                        contentColor = if (isMasterVisible) Color.Black else Color.White
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(height = barHeight - 4.dp, width = (barHeight - 4.dp) * 1.3f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = "Master",
                        modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
                    )
                }

                // PANIC Button
                Button(
                    onClick = { viewModel.panic() },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(height = barHeight - 4.dp, width = (barHeight - 4.dp) * 1.3f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = "Panic",
                        modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NoteShiftControl(
    label: String,
    value: Int,
    unit: String,
    onDelta: (Int) -> Unit,
    isMidiLearnActive: Boolean,
    pulseAlpha: State<Float>?,
    targetUp: MidiLearnTarget,
    targetDown: MidiLearnTarget,
    midiLearnTarget: com.marceloferlan.stagemobile.midi.MidiLearnTargetInfo?,
    midiLearnMappings: List<com.marceloferlan.stagemobile.midi.MidiLearnMapping>,
    viewModel: MixerViewModel,
    isTablet: Boolean,
    labelOnLeft: Boolean = true
) {
    val upState = MidiLearnState(
        isMidiLearnActive, 
        midiLearnTarget?.target == targetUp && midiLearnTarget?.channelId == MixerViewModel.GLOBAL_CHANNEL_ID,
        midiLearnMappings.any { it.target == targetUp && it.channelId == MixerViewModel.GLOBAL_CHANNEL_ID }
    )
    val downState = MidiLearnState(
        isMidiLearnActive, 
        midiLearnTarget?.target == targetDown && midiLearnTarget?.channelId == MixerViewModel.GLOBAL_CHANNEL_ID,
        midiLearnMappings.any { it.target == targetDown && it.channelId == MixerViewModel.GLOBAL_CHANNEL_ID }
    )

    val labelSize = if (isTablet) 8.sp else 8.sp
    val valueSize = if (isTablet) 11.sp else 11.sp
    val btnSize = if (isTablet) 28.dp else 28.dp

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (labelOnLeft) {
            Text(
                text = label,
                color = Color.Gray,
                fontSize = labelSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = if (isTablet) 10.dp else 4.dp)
            )
        }

        // Down Button
        Surface(
            modifier = Modifier
                .size(btnSize)
                .clip(RoundedCornerShape(4.dp))
                .midiLearnHalo(downState, RoundedCornerShape(4.dp), pulseAlpha = pulseAlpha)
                .midiLearnClickable(
                    downState,
                    onLearnClick = { viewModel.selectLearnTarget(targetDown, MixerViewModel.GLOBAL_CHANNEL_ID) },
                    onLearnLongClick = { viewModel.requestUnmap(targetDown, MixerViewModel.GLOBAL_CHANNEL_ID) },
                    onClick = { onDelta(-1) }
                ),
            color = Color(0xFF222222)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("−", color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (isTablet) 14.sp else 14.sp)
            }
        }

        // Display
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .width(if (isTablet) 55.dp else 42.dp)
                .height(btnSize)
                .background(Color(0xFF111111), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            val displayValue = if (value >= 0) "+$value" else value.toString()
            Text(
                text = "$displayValue $unit",
                color = if (value != 0) Color(0xFF81C784) else Color.LightGray,
                fontSize = valueSize,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Up Button
        Surface(
            modifier = Modifier
                .size(btnSize)
                .clip(RoundedCornerShape(4.dp))
                .midiLearnHalo(upState, RoundedCornerShape(4.dp), pulseAlpha = pulseAlpha)
                .midiLearnClickable(
                    upState,
                    onLearnClick = { viewModel.selectLearnTarget(targetUp, MixerViewModel.GLOBAL_CHANNEL_ID) },
                    onLearnLongClick = { viewModel.requestUnmap(targetUp, MixerViewModel.GLOBAL_CHANNEL_ID) },
                    onClick = { onDelta(1) }
                ),
            color = Color(0xFF222222)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (isTablet) 14.sp else 14.sp)
            }
        }

        if (!labelOnLeft) {
            Text(
                text = label,
                color = Color.Gray,
                fontSize = labelSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = if (isTablet) 10.dp else 4.dp)
            )
        }
    }
}
