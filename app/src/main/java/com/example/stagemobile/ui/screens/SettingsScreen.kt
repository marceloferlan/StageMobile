package com.example.stagemobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stagemobile.viewmodel.MixerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MixerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val bufferSize by viewModel.bufferSize.collectAsState()
    val sampleRate by viewModel.sampleRate.collectAsState()
    val isMasterVisible by viewModel.isMasterVisible.collectAsState()

    // State for MIDI
    val availableMidiDevices by viewModel.availableMidiDevices.collectAsState()
    val activeMidiDevices by viewModel.activeMidiDevices.collectAsState()
    val availableAudioDevices by viewModel.availableAudioDevices.collectAsState()
    val selectedAudioDeviceId by viewModel.selectedAudioDeviceId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111111)
                )
            )
        },
        containerColor = Color(0xFF1A1A1A)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section: Conectividade MIDI
            item {
                SettingsSection(title = "Controladores MIDI") {
                    if (availableMidiDevices.isEmpty()) {
                        SettingsRow(title = "Status", value = "Nenhum teclado USB conectado")
                    } else {
                        availableMidiDevices.forEach { device ->
                            val isActive = activeMidiDevices.contains(device.name)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = device.name,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                                Switch(
                                    checked = isActive,
                                    onCheckedChange = { checked ->
                                        viewModel.toggleActiveMidiDevice(device.name, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF4CAF50),
                                        uncheckedThumbColor = Color.LightGray,
                                        uncheckedTrackColor = Color(0xFF424242)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Section: Saídas de Áudio
            item {
                SettingsSection(title = "Saídas de Áudio") {
                    val audioDeviceOptions = availableAudioDevices.map { it.name }
                    val selectedAudioDeviceName = availableAudioDevices.find { it.id == selectedAudioDeviceId }?.name ?: "Auto (Padrão do Sistema)"

                    SettingsSelector(
                        title = "Interface de Áudio",
                        options = audioDeviceOptions.ifEmpty { listOf("Auto (Padrão do Sistema)") },
                        selectedOption = selectedAudioDeviceName,
                        onOptionSelected = { name -> 
                            val id = availableAudioDevices.find { it.name == name }?.id ?: -1
                            viewModel.updateAudioDevice(context, id)
                        }
                    )
                }
            }

            // Section: Controle de Latência (FastAudio)
            item {
                SettingsSection(title = "Controle de Latência (Oboe)") {
                    SettingsSelector(
                        title = "Tamanho do Buffer (Frames)",
                        options = listOf("64", "128", "256", "512"),
                        selectedOption = bufferSize.toString(),
                        onOptionSelected = { viewModel.updateBufferSize(context, it.toInt()) }
                    )

                    SettingsSelector(
                        title = "Taxa de Amostragem (Hz)",
                        options = listOf("44100", "48000"),
                        selectedOption = sampleRate.toString(),
                        onOptionSelected = { viewModel.updateSampleRate(context, it.toInt()) }
                    )
                    Text(
                        text = "Atenção: Modificar o tamanho do Buffer recarregará o motor de áudio. Valores mais baixos = menor delay (exige mais CPU).",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Section: Exibição de Telas
            item {
                SettingsSection(title = "Exibição de Controles") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Painel do Master Channel", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = isMasterVisible,
                            onCheckedChange = { viewModel.toggleMasterVisibility() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF1B5E20)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF222222), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF4CAF50),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        content()
    }
}

@Composable
fun SettingsRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 14.sp)
        Text(value, color = Color.LightGray, fontSize = 14.sp)
    }
}

@Composable
fun SettingsSelector(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) Color(0xFF1B5E20) else Color(0xFF333333),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onOptionSelected(option) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.White else Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
