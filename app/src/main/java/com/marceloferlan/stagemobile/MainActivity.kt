package com.marceloferlan.stagemobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import com.marceloferlan.stagemobile.viewmodel.MixerViewModel
import com.marceloferlan.stagemobile.ui.screens.*
import com.marceloferlan.stagemobile.ui.theme.StageMobileTheme
import com.marceloferlan.stagemobile.ui.components.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.marceloferlan.stagemobile.ui.mixer.InstrumentChannelSettingsPanel
import kotlinx.coroutines.delay
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.testTag
import com.marceloferlan.stagemobile.domain.model.InstrumentChannel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.marceloferlan.stagemobile.midi.MidiLearnTarget
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.marceloferlan.stagemobile.data.AuthRepository

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Permissions granted
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        permissions.add(Manifest.permission.RECORD_AUDIO)

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val windowInsetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            checkAndRequestPermissions()
        } catch (e: Exception) {
        }

        setContent {
            StageMobileTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF131313)
                ) {
                    val context = LocalContext.current
                    val viewModel: MixerViewModel = viewModel()
                    val isReady by viewModel.isReady.collectAsState()
                    var showSplashScreen by remember { mutableStateOf(true) }
                    var currentScreen by remember { mutableStateOf("mixer") }
                    val scope = rememberCoroutineScope()

                    // ── Roteamento de Autenticação ──────────────────────────
                    val authRepository = remember { AuthRepository() }
                    // currentUser é null se não logado ou se o usuário limpou os dados do app
                    var isAuthenticated by remember { 
                        mutableStateOf(Firebase.auth.currentUser != null && Firebase.auth.currentUser?.isEmailVerified == true) 
                    }

                    DisposableEffect(Unit) {
                        val authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
                            val user = auth.currentUser
                            if (user == null) {
                                isAuthenticated = false
                            } else if (user.isEmailVerified) {
                                isAuthenticated = true
                            }
                            // Se o user existir mas isEmailVerified for false (como logo após um cadastro automático), 
                            // a autenticação não é forçada para true. 
                            // O LoginScreen ficará responsável por mostrar a Dialog e ditar onAuthSuccess().
                        }
                        Firebase.auth.addAuthStateListener(authListener)
                        onDispose { Firebase.auth.removeAuthStateListener(authListener) }
                    }

                    if (!isAuthenticated) {
                        LoginScreen(
                            authRepository = authRepository,
                            onAuthSuccess = { isAuthenticated = true }
                        )
                        return@Surface
                    }
                    // ────────────────────────────────────────────────────────

                    LaunchedEffect(Unit) {
                        viewModel.initMidi(context)
                    }

                    LaunchedEffect(isReady) {
                        if (isReady) {
                            delay(2000)
                            showSplashScreen = false
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (showSplashScreen) {
                                SplashScreen()
                            } else {
                                when (currentScreen) {
                                    "mixer" -> {
                                        MixerScreen(
                                            viewModel = viewModel,
                                            onNavigateToSettings = { currentScreen = "settings" },
                                            onNavigateToSets = { currentScreen = "sets" },
                                            onNavigateToDrumpads = { currentScreen = "drumpads" },
                                            onNavigateToContinuousPads = { currentScreen = "continuous_pads" },
                                            onNavigateToDownloads = { currentScreen = "downloads" },
                                            onNavigateToSf2Maintenance = { currentScreen = "sf2_maintenance" }
                                        )
                                    }
                                    "sf2_maintenance" -> {
                                        SoundFontMaintenanceScreen(
                                            viewModel = viewModel,
                                            onNavigateBack = { currentScreen = "mixer" }
                                        )
                                    }
                                    "settings" -> {
                                        SystemGlobalSettings(
                                            viewModel = viewModel,
                                            onNavigateBack = { currentScreen = "mixer" }
                                        )
                                    }
                                    "sets" -> SetsScreen(onNavigateBack = { currentScreen = "mixer" }, viewModel = viewModel)
                                    "drumpads" -> DrumpadsScreen(onNavigateBack = { currentScreen = "mixer" })
                                    "continuous_pads" -> ContinuousPadsScreen(onNavigateBack = { currentScreen = "mixer" })
                                    "downloads" -> DownloadsScreen(onNavigateBack = { currentScreen = "mixer" })
                                }
                            }
                        }

                        // Top Level Overlays
                        val showSoundFontSelectorForChannel by viewModel.showSoundFontSelectorForChannel.collectAsState()
                        val showQuickSelector by viewModel.showQuickSelector.collectAsState()
                        val showDSPEffectsRackForChannel by viewModel.showDSPEffectsRackForChannel.collectAsState()
                        val pendingPresetSelection by viewModel.pendingPresetSelection.collectAsState()
                        val availableSF2 by viewModel.availableSoundFonts.collectAsState()

                        if (showSoundFontSelectorForChannel != null) {
                            val chId = showSoundFontSelectorForChannel!!
                            SoundFontSelectorDialog(
                                availableSoundFonts = availableSF2,
                                onSoundFontSelected = { metadata ->
                                    viewModel.loadSoundFontFromInternal(chId, metadata)
                                    viewModel.dismissSoundFontSelector()
                                },
                                onNavigateToMaintenance = {
                                    viewModel.dismissSoundFontSelector()
                                    currentScreen = "sf2_maintenance"
                                },
                                onDismiss = { viewModel.dismissSoundFontSelector() }
                            )
                        }

                        pendingPresetSelection?.let { selection ->
                            val channels by viewModel.channels.collectAsState()
                            val currentChannel = channels.find { it.id == selection.channelId }
                            SF2PresetSelectorDialog(
                                sf2Name = selection.sf2Name,
                                presets = selection.presets,
                                currentBank = currentChannel?.bank ?: 0,
                                currentProgram = currentChannel?.program ?: 0,
                                onPresetSelected = { bank, program, name ->
                                    viewModel.selectPresetForChannel(selection.channelId, bank, program, name)
                                },
                                onDismiss = { viewModel.dismissPresetSelector() }
                            )
                        }

                        if (showQuickSelector) {
                            SetStageQuickSelectorDialog(
                                viewModel = viewModel,
                                onDismissRequest = { viewModel.dismissQuickSelector() }
                            )
                        }

                        showDSPEffectsRackForChannel?.let { chId ->
                            val channels by viewModel.channels.collectAsState()
                            val ch = if (chId == MixerViewModel.MASTER_CHANNEL_ID) null else channels.find { it.id == chId }
                            val channelIndex = if (chId == MixerViewModel.MASTER_CHANNEL_ID) -1 else channels.indexOfFirst { it.id == chId } + 1
                            val displayTitle = if (chId == MixerViewModel.MASTER_CHANNEL_ID) "MASTER" else "CANAL $channelIndex"
                            val subtitle = ch?.name ?: ""
                            
                            val effects = if (chId == MixerViewModel.MASTER_CHANNEL_ID)
                                viewModel.masterDspEffects.collectAsState().value
                            else
                                ch?.dspEffects ?: emptyList()

                            DSPEffectsRackDialog(
                                title = displayTitle,
                                subtitle = subtitle,
                                effects = effects,
                                onDismiss = { viewModel.dismissEffectsRack() },
                                onUpdateParam = { effectId, paramId, value -> viewModel.updateEffectParam(chId, effectId, paramId, value) },
                                onToggleEffect = { effectId, enabled -> viewModel.toggleEffect(chId, effectId, enabled) },
                                onAddEffect = { type -> viewModel.addEffectToChannel(chId, type) },
                                onRemoveEffect = { effectId -> viewModel.removeEffectFromChannel(chId, effectId) },
                                onTapDelay = { effectId -> viewModel.tapDelayTime(chId, effectId) },
                                onResetEffect = { effectId -> viewModel.resetEffectParams(chId, effectId) },
                                isMidiLearnActive = viewModel.isMidiLearnActive.collectAsState().value,
                                onToggleMidiLearn = { viewModel.toggleMidiLearn() },
                                onSelectMidiLearnTarget = { effectId, paramId -> viewModel.selectDspLearnTarget(chId, effectId, paramId) },
                                midiLearnMappings = viewModel.midiLearnMappings.collectAsState().value,
                                midiLearnTarget = viewModel.midiLearnTarget.collectAsState().value,
                                midiLearnFeedback = viewModel.midiLearnFeedback.collectAsState().value,
                                channelId = chId,
                                onTestNoteOn = { cId, note, vel -> viewModel.playTestNoteOn(cId, note, vel) },
                                onTestNoteOff = { cId, note -> viewModel.playTestNoteOff(cId, note) },
                                viewModel = viewModel,
                                engine = viewModel.audioEngine
                            )
                        }

                        val showAdvancedParamsForChannel by viewModel.showAdvancedParamsForChannel.collectAsState()
                        showAdvancedParamsForChannel?.let { chId ->
                            val channels by viewModel.channels.collectAsState()
                            val targetCh = channels.find { it.id == chId }
                            targetCh?.let { ch ->
                                InstrumentChannelSettingsPanel(
                                    channel = ch,
                                    activeMidiDevices = viewModel.activeMidiDevices.collectAsState().value,
                                    availableMidiDevices = viewModel.availableMidiDevices.collectAsState().value,
                                    onDismiss = { viewModel.dismissChannelAdvancedSettings() },
                                    onMidiDeviceSelected = { device -> viewModel.updateChannelMidiDevice(ch.id, device) },
                                    onMidiChannelSelected = { chan -> viewModel.updateChannelMidiChannel(ch.id, chan) },
                                    onVelocityCurveSelected = { curve -> viewModel.updateChannelVelocityCurve(ch.id, curve) },
                                    onMinNoteClick = { },
                                    onMaxNoteClick = { },
                                    onMidiFilterToggled = { key, value -> viewModel.toggleChannelMidiFilter(ch.id, key, value) }
                                )
                            }
                        }

                        val showOptionsForChannel by viewModel.showOptionsForChannel.collectAsState()
                        showOptionsForChannel?.let { chId ->
                            val channels by viewModel.channels.collectAsState()
                            val targetCh = channels.find { it.id == chId }
                            targetCh?.let { ch ->
                                InstrumentChannelOptionsMenu(
                                    channel = ch,
                                    channelIndex = channels.indexOfFirst { it.id == ch.id } + 1,
                                    onDismiss = { viewModel.dismissChannelOptions() },
                                    onColorChange = { color -> viewModel.updateChannelColor(ch.id, color) },
                                    onRemoveClick = { 
                                        viewModel.dismissChannelOptions()
                                        viewModel.removeChannel(ch.id) 
                                    },
                                    onAdvancedOptionsClick = { 
                                        viewModel.dismissChannelOptions()
                                        viewModel.showChannelAdvancedSettings(ch.id) 
                                    },
                                    onDSPEffectsClick = { 
                                        viewModel.dismissChannelOptions()
                                        viewModel.showEffectsRack(ch.id) 
                                    }
                                )
                            }
                        }

                        val showUnloadConfirmationForChannel by viewModel.showUnloadConfirmationForChannel.collectAsState()
                        showUnloadConfirmationForChannel?.let { chId ->
                            val isTablet = com.marceloferlan.stagemobile.utils.UiUtils.rememberIsTablet()
                            val channels by viewModel.channels.collectAsState()
                            val targetCh = channels.find { it.id == chId }
                            targetCh?.let { ch ->
                                val sf2Name = ch.soundFont?.substringAfterLast('/') ?: "Instrumento"
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { viewModel.dismissUnloadConfirmation() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth(if (isTablet) 0.2925f else 0.45f)
                                            .fillMaxHeight(if (isTablet) 0.163f else 0.36f)
                                            .testTag("unload_confirmation_overlay")
                                            .clickable(enabled=false){},
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xFF2C2C2C)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                            val channelIndex = channels.indexOfFirst { it.id == ch.id } + 1
                                            Text(
                                                text = "LIMPAR DO CANAL $channelIndex?", 
                                                color = Color.White, 
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Text(
                                                text = sf2Name.uppercase(), 
                                                color = Color(0xFF81C784),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                                                TextButton(onClick = { viewModel.dismissUnloadConfirmation() }) { Text("CANCELAR", color = Color.Gray) }
                                                TextButton(onClick = { 
                                                    viewModel.removeSoundFont(chId)
                                                    viewModel.dismissUnloadConfirmation()
                                                }) { Text("LIMPAR", color = Color.Red, fontWeight = FontWeight.Bold) }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val pendingUnmap by viewModel.pendingUnmap.collectAsState()
                        pendingUnmap?.let { targetInfo ->
                            val targetMappings = targetInfo.mappings
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { viewModel.dismissUnmap() },
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f).testTag("unmap_dialog").clickable(enabled=false){},
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF2C2C2C)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                        Text("Remover vinculação MIDI", color = Color.White, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        targetMappings.forEach { m ->
                                            TextButton(onClick = { viewModel.confirmUnmap(m) }, modifier = Modifier.fillMaxWidth()) {
                                                Text("CC ${m.ccNumber} (CH ${m.midiChannel + 1})", color = Color(0xFFEF5350))
                                            }
                                        }
                                        TextButton(onClick = { viewModel.unmapAll(targetInfo.target, targetInfo.channelId) }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Remover Todos", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        TextButton(onClick = { viewModel.dismissUnmap() }) { Text("CANCELAR", color = Color.Gray) }
                                    }
                                }
                            }
                        }

                        val showSf2Import by viewModel.showSf2ImportTagSelector.collectAsState()
                        showSf2Import?.let { uri ->
                            val categories = listOf("Piano", "EP/FM", "Pad", "Synth", "Lead", "Bass", "Brass", "Strings", "Organ", "Bells", "Guitar", "Drums/Percussion", "FX", "Outros")
                            TagSelectionOverlay(
                                uri = uri,
                                categories = categories,
                                onConfirm = { fileName, tags ->
                                    viewModel.importSoundFontToLibrary(uri, fileName, tags)
                                },
                                onDismiss = { viewModel.dismissSf2Import() },
                                exists = { name -> viewModel.isSoundFontInLibrary(name) }
                            )
                        }

                        val importProgress by viewModel.importProgress.collectAsState()
                        if (importProgress != null) {
                            androidx.compose.ui.window.Dialog(onDismissRequest = {}) { // Impede fechar a modal clicando fora
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF2C2C2C),
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(0.9f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Importando SoundFont...", color = Color.White, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = importProgress ?: 0f,
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                            color = Color(0xFF26C6DA), // Usando ciano da nova identidade visual
                                            trackColor = Color(0xFF424242)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("${((importProgress ?: 0f) * 100).toInt()}%", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        val showSf2Rename by viewModel.showSf2RenameDialog.collectAsState()
                        showSf2Rename?.let { sf2 ->
                            RenameSF2Overlay(
                                sf2 = sf2,
                                onConfirm = { newName ->
                                    viewModel.renameSoundFont(sf2, newName)
                                    viewModel.dismissSf2Rename()
                                },
                                onDismiss = { viewModel.dismissSf2Rename() }
                            )
                        }

                        val showSf2Delete by viewModel.showSf2DeleteConfirmation.collectAsState()
                        showSf2Delete?.let { sf2 ->
                            val isTablet = com.marceloferlan.stagemobile.utils.UiUtils.rememberIsTablet()
                            val isInUse = viewModel.isSoundFontInUse(sf2.fileName)
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { viewModel.dismissSf2Delete() },
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth(if (isTablet) 0.36f else 0.45f)
                                        .fillMaxHeight(if (isTablet) 0.20f else 0.40f)
                                        .testTag("delete_confirmation")
                                        .clickable(enabled=false){},
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF2C2C2C)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(if (isInUse) "Arquivo em Uso!" else "Excluir SoundFont?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // Highlighted FileName with Truncate
                                        Text(
                                            text = sf2.fileName,
                                            color = Color(0xFF81C784),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))
                                        
                                        Text(
                                            text = if (isInUse) "Arquivo vinculado a Set Stages. Continuar?" 
                                                   else "Confirmar a remoção permanente do arquivo?",
                                            color = Color.Gray, 
                                            textAlign = TextAlign.Center, 
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                                            TextButton(onClick = { viewModel.dismissSf2Delete() }) { Text("CANCELAR", color = Color.Gray) }
                                            TextButton(onClick = { 
                                                scope.launch { viewModel.soundFontRepo?.deleteSoundFont(sf2) }
                                                viewModel.dismissSf2Delete()
                                            }) { Text("EXCLUIR", color = Color.Red, fontWeight = FontWeight.Bold) }
                                        }
                                    }
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
fun TagSelectionOverlay(
    uri: android.net.Uri,
    categories: List<String>,
    onConfirm: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
    exists: (String) -> Boolean
) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<String>() }
    var showConflictDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        val isTablet = com.marceloferlan.stagemobile.utils.UiUtils.rememberIsTablet()
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(if (isTablet) 0.52f else 0.9f)
                .testTag("tag_selection_overlay")
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(modifier = Modifier.padding(start = if (isTablet) 20.dp else 12.dp, end = if (isTablet) 20.dp else 12.dp, top = if (isTablet) 20.dp else 12.dp, bottom = if (isTablet) 20.dp else 8.dp).fillMaxWidth()) {
                Text("Configurar SoundFont", fontSize = if (isTablet) 20.sp else 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                
                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                androidx.compose.foundation.text.BasicTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    modifier = Modifier.fillMaxWidth().height(if (isTablet) 56.dp else 46.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = if (isTablet) 16.sp else 14.sp,
                        lineHeight = if (isTablet) 20.sp else 16.sp
                    ),
                    singleLine = true,
                    interactionSource = interactionSource,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF4CAF50))
                ) { innerTextField ->
                    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                    androidx.compose.material3.OutlinedTextFieldDefaults.DecorationBox(
                        value = fileName,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                        interactionSource = interactionSource,
                        label = { Text("Nome do Arquivo") },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, 
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF4CAF50),
                            unfocusedLabelColor = Color.Gray
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 4.dp, top = 0.dp, bottom = 0.dp),
                        container = {
                            androidx.compose.material3.OutlinedTextFieldDefaults.Container(
                                enabled = true,
                                isError = false,
                                interactionSource = interactionSource,
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                shape = RoundedCornerShape(8.dp),
                                focusedBorderThickness = 2.dp,
                                unfocusedBorderThickness = 1.dp
                            )
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Selecione as Categorias (Mínimo 1)", color = if (selectedTags.isEmpty()) Color(0xFFE57373) else Color.Gray, fontSize = 12.sp)
                
                // Usando LazyVerticalGrid em vez de LazyColumn para 3 colunas proporcionalmente distribuídas
                Box(modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 240.dp)
                    .padding(top = 8.dp)
                ) {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isTablet) 8.dp else 2.dp)
                    ) {
                        items(categories.size) { index ->
                            val category = categories[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        if (selectedTags.contains(category)) selectedTags.remove(category) else selectedTags.add(category)
                                    }
                                    .background(if (selectedTags.contains(category)) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color.Transparent)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = selectedTags.contains(category),
                                    onCheckedChange = null,
                                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50)),
                                    modifier = Modifier.scale(0.8f) // Reduzindo levemente o checkbox para caber melhor no layout de colunas
                                )
                                Text(
                                    category, 
                                    color = if (selectedTags.contains(category)) Color(0xFF4CAF50) else Color.White, 
                                    fontSize = if (isTablet) 14.sp else 12.sp, 
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(if (isTablet) 24.dp else 8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { 
                        Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold) 
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (exists(fileName)) showConflictDialog = true else onConfirm(fileName, selectedTags.toList())
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
                        ),
                        enabled = fileName.isNotBlank() && selectedTags.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp)
                    ) { 
                        Text("IMPORTAR AGORA", fontWeight = FontWeight.ExtraBold) 
                    }
                }
            }
        }
    }

    if (showConflictDialog) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showConflictDialog = false },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (com.marceloferlan.stagemobile.utils.UiUtils.isTablet(LocalContext.current)) 0.35f else 0.55f)
                    .wrapContentHeight()
                    .testTag("import_conflict_dialog")
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF2C2C2C)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Text("Arquivo Já Existe", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "O arquivo '$fileName' já está na biblioteca. Deseja substituir?",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showConflictDialog = false }) { Text("Cancelar") }
                        TextButton(onClick = { onConfirm(fileName, selectedTags.toList()); showConflictDialog = false }) { Text("Substituir", color = Color.Red) }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameSF2Overlay(
    sf2: com.marceloferlan.stagemobile.domain.model.SoundFontMetadata,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(sf2.fileName) }
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f).testTag("rename_sf2_overlay").clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Renomear SoundFont", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Novo nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color(0xFF4CAF50), unfocusedLabelColor = Color.Gray)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFF888888)) }
                    Button(
                        onClick = { onConfirm(newName) },
                        enabled = newName.isNotBlank() && newName != sf2.fileName,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text("Renomear") }
                }
            }
        }
    }
}