package com.example.stagemobile.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.util.DisplayMetrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.stagemobile.viewmodel.MixerViewModel
import com.example.stagemobile.utils.UiUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemGlobalSettings(
    viewModel: MixerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val bufferSize by viewModel.bufferSize.collectAsState()
    val sampleRate by viewModel.sampleRate.collectAsState()

    // State for MIDI
    val availableMidiDevices by viewModel.availableMidiDevices.collectAsState()
    val activeMidiDevices by viewModel.activeMidiDevices.collectAsState()
    val availableAudioDevices by viewModel.availableAudioDevices.collectAsState()
    val selectedAudioDeviceId by viewModel.selectedAudioDeviceId.collectAsState()
    val interpolationMethod by viewModel.interpolationMethod.collectAsState()
    val maxPolyphony by viewModel.maxPolyphony.collectAsState()
    val velocityCurve by viewModel.velocityCurve.collectAsState()
    val isSustainInverted by viewModel.isSustainInverted.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111111)
                )
            )
        },
        containerColor = Color(0xFF1A1A1A)
    ) { paddingValues ->
        val isTablet = UiUtils.rememberIsTablet()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(if (isTablet) 24.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Seção de Informações do Sistema (Cabeçalho Técnico Profissional)
            item {
                SystemInfoSection()
            }

            if (isTablet) {
                // Par 1: MIDI e Áudio (Topos e Bases alinhados)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        MidiSection(
                            availableMidiDevices, 
                            activeMidiDevices, 
                            viewModel,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        AudioSection(
                            availableAudioDevices, 
                            selectedAudioDeviceId, 
                            context, 
                            viewModel,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
                
                // Par 2: Latência e Motor de Áudio
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        LatencySection(
                            bufferSize.toString(), 
                            sampleRate.toString(), 
                            context, 
                            viewModel,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        AudioEngineSection(
                            interpolationMethod = interpolationMethod,
                            maxPolyphony = maxPolyphony,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }

                // Par 3: Curva de Velocity (full width)
                item {
                    VelocityCurveSection(
                        velocityCurve = velocityCurve,
                        viewModel = viewModel
                    )
                }
            } else {
                // Layout Vertical padrão para Celulares
                item { MidiSection(availableMidiDevices, activeMidiDevices, viewModel) }
                item { AudioSection(availableAudioDevices, selectedAudioDeviceId, context, viewModel) }
                item { LatencySection(bufferSize.toString(), sampleRate.toString(), context, viewModel) }
                item { AudioEngineSection(interpolationMethod, maxPolyphony, viewModel) }
                item { VelocityCurveSection(velocityCurve, viewModel) }
            }
        }
    }
}

@Composable
fun MidiSection(
    availableMidiDevices: List<com.example.stagemobile.midi.MidiDeviceState>,
    activeMidiDevices: Set<String>,
    viewModel: MixerViewModel,
    modifier: Modifier = Modifier
) {
    SettingsSection(title = "Controladores MIDI", modifier = modifier) {
        if (availableMidiDevices.isEmpty()) {
            SettingsRow(title = "Status", value = "Nenhum controlador MIDI USB conectado")
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
                    Text(text = device.name, color = Color.White, fontSize = 16.sp)
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF333333))

        val isSustainInverted by viewModel.isSustainInverted.collectAsState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Inverter Pedal de Sustain", color = Color.White, fontSize = 16.sp)
                Text(text = "Útil para pedais Yamaha/NC (Polaridade Inversa)", color = Color.Gray, fontSize = 11.sp)
            }
            Switch(
                checked = isSustainInverted,
                onCheckedChange = { viewModel.updateSustainInversion(it) },
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

@Composable
fun AudioSection(
    availableAudioDevices: List<com.example.stagemobile.audio.AudioDeviceState>,
    selectedAudioDeviceId: Int,
    context: android.content.Context,
    viewModel: MixerViewModel,
    modifier: Modifier = Modifier
) {
    SettingsSection(title = "Saídas de Áudio", modifier = modifier) {
        val audioDeviceOptions = availableAudioDevices.map { it.name }
        val selectedAudioDeviceName = availableAudioDevices.find { it.id == selectedAudioDeviceId }?.name ?: "Buscando Interface..."

        SettingsSelector(
            title = "Interface de Áudio",
            options = audioDeviceOptions.ifEmpty { listOf("Buscando Interface...") },
            selectedOption = selectedAudioDeviceName,
            onOptionSelected = { name -> 
                val id = availableAudioDevices.find { it.name == name }?.id ?: -1
                viewModel.updateAudioDevice(context, id)
            }
        )
    }
}

@Composable
fun LatencySection(
    bufferSize: String,
    sampleRate: String,
    context: android.content.Context,
    viewModel: MixerViewModel,
    modifier: Modifier = Modifier
) {
    SettingsSection(title = "Controle de Latência (Oboe)", modifier = modifier) {
        SettingsSelector(
            title = "Tamanho do Buffer (Frames)",
            options = listOf("64", "128", "256", "384", "512"),
            selectedOption = bufferSize,
            onOptionSelected = { viewModel.updateBufferSize(context, it.toInt()) }
        )

        SettingsSelector(
            title = "Taxa de Amostragem (Hz)",
            options = listOf("44100", "48000"),
            selectedOption = sampleRate,
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


@Composable
fun SystemInfoSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SettingsSection(title = "Informações do Sistema", modifier = modifier) {
        val manufacturer = android.os.Build.MANUFACTURER.uppercase()
        val model = android.os.Build.MODEL
        val androidVersion = android.os.Build.VERSION.RELEASE
        val apiLevel = android.os.Build.VERSION.SDK_INT
        
        // Extração de CPU (Hardware/Chipset) Detalhada
        val cpuHardware = android.os.Build.HARDWARE
        val socModel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.os.Build.SOC_MODEL
        } else ""
        
        // Identificação de Marca (Snapdragon vs Exynos)
        val brand = when {
            socModel.contains("snapdragon", ignoreCase = true) || cpuHardware.contains("qcom", ignoreCase = true) -> "SNAPDRAGON"
            socModel.contains("exynos", ignoreCase = true) || cpuHardware.contains("exynos", ignoreCase = true) -> "EXYNOS"
            else -> ""
        }
        
        val socName = if (socModel.isNotEmpty() && socModel != "unknown") socModel else cpuHardware
        val cpuBrandLabel = if (brand.isNotEmpty() && !socName.contains(brand, ignoreCase = true)) {
            "$brand $socName"
        } else socName

        // Contagem de Cores
        val cores = Runtime.getRuntime().availableProcessors()
        
        // Clock (Frequência Máxima) - Tentativa de leitura via sysfs
        val maxFreqHz = try {
            val scanner = java.util.Scanner(java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"))
            if (scanner.hasNextLong()) scanner.nextLong() else 0L
        } catch (e: Exception) {
            0L
        }
        val clockGhz = if (maxFreqHz > 0) " @ %.1f GHz".format(maxFreqHz / 1000000.0) else ""
        
        val cpuFullDisplay = "$cpuBrandLabel ($cores Cores$clockGhz)".uppercase()

        // Extração de RAM Total
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamGb = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
        val ramDisplay = "%.1f GB".format(totalRamGb)

        // Extração de Display
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        val displayDisplay = "${width}x${height} px ($density DPI)"

        Column() {
            SettingsRow(title = "Fabricante", value = manufacturer)
            HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
            SettingsRow(title = "Modelo", value = model)
            HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
            SettingsRow(title = "Processador", value = cpuFullDisplay)
            HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
            SettingsRow(title = "Memória RAM", value = ramDisplay)
            HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
            SettingsRow(title = "Display", value = displayDisplay)
            HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
            SettingsRow(title = "Android", value = "Versão $androidVersion (API $apiLevel)")
        }
    }
}

@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEngineSection(
    interpolationMethod: Int,
    maxPolyphony: Int,
    viewModel: MixerViewModel,
    modifier: Modifier = Modifier
) {
    val interpOptions = listOf(
        0 to "Desativada",
        1 to "Leve",
        4 to "Balanceada (padrão)",
        7 to "Alta Fidelidade"
    )
    val currentInterpLabel = interpOptions.find { it.first == interpolationMethod }?.second ?: "Balanceada (padrão)"

    SettingsSection(title = "Motor de Áudio", modifier = modifier) {
        // Interpolation selector
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                "Interpolação",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                interpOptions.forEach { (method, label) ->
                    val isSelected = method == interpolationMethod
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) Color(0xFF1B5E20) else Color(0xFF333333),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.updateInterpolation(method) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        // Polyphony slider
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            val performanceLabel = when {
                maxPolyphony <= 64 -> "OTIMIZADO (LEVE)"
                maxPolyphony <= 128 -> "PERFORMANCE ALTA"
                else -> "CARGA PESADA (RISCO DE GLITCH)"
            }
            val performanceColor = when {
                maxPolyphony <= 64 -> Color.Gray
                maxPolyphony <= 128 -> Color(0xFF00E5FF)
                else -> Color(0xFFFFC107)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Polifonia Máxima", 
                    color = Color.White, 
                    fontSize = 14.sp, 
                )
                Text(
                    "$maxPolyphony vozes",
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = maxPolyphony.toFloat(),
                onValueChange = { viewModel.updatePolyphony(it.toInt()) },
                valueRange = 16f..256f,
                steps = 14, // (256-16)/16 - 1 = 14 steps for increments of 16
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E5FF),
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                track = { sliderState ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    ) {
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Gray,
                                    Color(0xFF4CAF50), // Verde (Conforto)
                                    Color(0xFF00E5FF), // Ciano (Performance)
                                    Color(0xFFFFC107)  // Âmbar (Pesado)
                                )
                            ),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("16", color = Color.Gray, fontSize = 10.sp)
                Text(
                    performanceLabel,
                    color = performanceColor.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("256", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun AudioProcessingSection(viewModel: MixerViewModel, modifier: Modifier = Modifier) {
    val masterLimiterEnabled by viewModel.isMasterLimiterEnabled.collectAsState()

    SettingsSection(title = "Processamento Master", modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Master Limiter / Som de Palco",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Protege contra distorção e adiciona densidade (Punch).",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
                
                Switch(
                    checked = masterLimiterEnabled,
                    onCheckedChange = { viewModel.updateMasterLimiter(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

@Composable
fun VelocityCurveSection(
    velocityCurve: Int,
    viewModel: MixerViewModel,
    modifier: Modifier = Modifier
) {
    val curveOptions = listOf(
        0 to "Linear",
        1 to "Semi-Suave",
        2 to "Suave",
        3 to "Extra Suave",
        4 to "Semi-Rígida",
        5 to "Rígida",
        6 to "Logarítmica",
        7 to "Curva-S"
    )

    SettingsSection(title = "Curva de Velocity", modifier = modifier) {
        // Selector buttons - Row 1 (4 options)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            curveOptions.take(4).forEach { (id, label) ->
                val isSelected = id == velocityCurve
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) Color(0xFF1B5E20) else Color(0xFF333333),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.updateVelocityCurve(id) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Selector buttons - Row 2 (4 options)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            curveOptions.drop(4).forEach { (id, label) ->
                val isSelected = id == velocityCurve
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) Color(0xFF1B5E20) else Color(0xFF333333),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.updateVelocityCurve(id) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Canvas graph
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
        ) {
            val w = size.width
            val h = size.height
            val padding = 24f

            val graphW = w - padding * 2
            val graphH = h - padding * 2

            // Grid lines
            val gridColor = Color(0xFF333333)
            for (i in 0..4) {
                val x = padding + graphW * i / 4f
                val y = padding + graphH * i / 4f
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, padding), androidx.compose.ui.geometry.Offset(x, padding + graphH), 0.5f)
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(padding, y), androidx.compose.ui.geometry.Offset(padding + graphW, y), 0.5f)
            }

            // Reference diagonal (linear) - dashed
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            drawLine(
                color = Color(0xFF555555),
                start = androidx.compose.ui.geometry.Offset(padding, padding + graphH),
                end = androidx.compose.ui.geometry.Offset(padding + graphW, padding),
                strokeWidth = 1f,
                pathEffect = dashEffect
            )

            // Draw the curve
            val curvePath = Path()
            val steps = 128
            for (i in 0..steps) {
                val inputVel = i
                val normalized = inputVel / 127.0
                val mapped = when (velocityCurve) {
                    0 -> normalized
                    1 -> Math.pow(normalized, 0.75)
                    2 -> Math.sqrt(normalized)
                    3 -> Math.cbrt(normalized)
                    4 -> Math.pow(normalized, 1.5)
                    5 -> normalized * normalized
                    6 -> {
                        if (normalized <= 0) 0.0
                        else Math.log10(1.0 + 9.0 * normalized)
                    }
                    7 -> {
                        val x = (normalized - 0.5) * 12.0
                        1.0 / (1.0 + Math.exp(-x))
                    }
                    else -> normalized
                }
                val px = padding + (i / 127f) * graphW
                val py = padding + graphH - (mapped.toFloat() * graphH)

                if (i == 0) curvePath.moveTo(px, py)
                else curvePath.lineTo(px, py)
            }

            drawPath(
                path = curvePath,
                color = Color(0xFF00E5FF),
                style = Stroke(width = 3f)
            )

            // Axis labels
            val axisColor = Color(0xFF888888)
            drawLine(axisColor, androidx.compose.ui.geometry.Offset(padding, padding), androidx.compose.ui.geometry.Offset(padding, padding + graphH), 1f)
            drawLine(axisColor, androidx.compose.ui.geometry.Offset(padding, padding + graphH), androidx.compose.ui.geometry.Offset(padding + graphW, padding + graphH), 1f)
        }

        // Axis text labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.padding(start = 24.dp))
            Text("Entrada (Velocity MIDI)", color = Color.Gray, fontSize = 9.sp)
            Text("127", color = Color.Gray, fontSize = 9.sp)
        }
    }
}
