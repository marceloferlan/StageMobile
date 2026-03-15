package com.example.stagemobile.ui.mixer

import com.example.stagemobile.utils.UiUtils
import com.example.stagemobile.utils.getMidiNoteName
import com.example.stagemobile.midi.MidiDeviceState

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.stagemobile.domain.model.InstrumentChannel

@Composable
fun InstrumentChannelSettingsPanel(
    channel: InstrumentChannel,
    activeMidiDevices: Set<String>,
    availableMidiDevices: List<MidiDeviceState>,
    onDismiss: () -> Unit,
    onMidiDeviceSelected: (String?) -> Unit,
    onMidiChannelSelected: (Int) -> Unit,
    onVelocityCurveSelected: (Int) -> Unit,
    onMinNoteClick: () -> Unit,
    onMaxNoteClick: () -> Unit,
    onMidiFilterToggled: (String, Boolean) -> Unit
) {
    val isTablet = UiUtils.rememberIsTablet()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .fillMaxWidth(if (isTablet) 0.6f else 0.85f)
                .padding(vertical = 16.dp)
                .border(0.5.dp, Color(0xFF424242), RoundedCornerShape(12.dp)),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // HEADER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Parâmetros do Canal ${channel.id + 1}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(24.dp)
                            .clickable { onDismiss() },
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

                HorizontalDivider(thickness = 1.dp, color = Color(0xFF424242))

                // MAIN CONTENT - TWO COLUMNS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isTablet) 200.dp else 145.dp)
                ) {
                    // LEFT COLUMN: MIDI ROUTING (50%)
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Selecione o MIDI device",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val connectedActiveDevices = availableMidiDevices
                            .filter { it.isConnected && activeMidiDevices.contains(it.name) }
                            .map { it.name }

                        val deviceOptions = mutableListOf<String?>()
                        if (connectedActiveDevices.size > 1) {
                            deviceOptions.add(null) // "Todos Ativos"
                        }
                        deviceOptions.addAll(connectedActiveDevices)

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(deviceOptions) { deviceName ->
                                val isSelected = channel.midiDeviceName == deviceName
                                val label = deviceName ?: "TODOS OS MIDI DEVICES"
                                
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF4CAF50) else Color(0xFF2C2C2C),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .clickable { onMidiDeviceSelected(deviceName) }
                                ) {
                                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 12.dp)) {
                                        Text(
                                            text = label,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                            
                            if (deviceOptions.isEmpty()) {
                                item {
                                    Text(
                                        text = "Nenhum MIDI device conectado.",
                                        color = Color(0xFFFFCC00),
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // VERTICAL SEPARATOR
                    VerticalDivider(thickness = 1.dp, color = Color(0xFF424242))

                    // RIGHT COLUMN: MIDI CHANNEL (50%)
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Canal MIDI",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val midiChannels = listOf(-1) + (0..15).toList()
                        
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (isTablet) 6 else 4),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(midiChannels) { midiCh ->
                                val isSelected = channel.midiChannel == midiCh
                                val label = if (midiCh == -1) "OMNI" else "${midiCh + 1}"
                                
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF4CAF50) else Color(0xFF2C2C2C),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clickable { onMidiChannelSelected(midiCh) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // KEY RANGE SECTION
                HorizontalDivider(thickness = 1.dp, color = Color(0xFF424242))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Key Range",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isMinModified = channel.minNote > 0
                        val isMaxModified = channel.maxNote < 127
                        val modifiedColor = Color(0xFF81C784)
                        val defaultColor = Color.White

                        // Min Note
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2C2C2C),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clickable { onMinNoteClick() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Min",
                                        color = Color(0xFF888888),
                                        fontSize = 8.sp,
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                    Text(
                                        text = getMidiNoteName(channel.minNote),
                                        color = if (isMinModified) modifiedColor else defaultColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                }
                            }
                        }

                        // Max Note
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2C2C2C),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clickable { onMaxNoteClick() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Max",
                                        color = Color(0xFF888888),
                                        fontSize = 8.sp,
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                    Text(
                                        text = getMidiNoteName(channel.maxNote),
                                        color = if (isMaxModified) modifiedColor else defaultColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                }
                            }
                        }
                    }
                }

                // VELOCITY CURVE SECTION
                HorizontalDivider(thickness = 1.dp, color = Color(0xFF424242))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Curva de Velocity",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val curveOptions = listOf(
                        -1 to "Usar Global",
                        0 to "Linear",
                        1 to "Semi-Suave",
                        2 to "Suave",
                        3 to "Extra Suave",
                        4 to "Semi-Rígida",
                        5 to "Rígida",
                        6 to "Logarítmica",
                        7 to "Curva-S"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // LEFT SIDE: Buttons (Grid-like 4x2)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val numRows = (curveOptions.size + 1) / 2
                            for (i in 0 until numRows) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (j in 0 until 2) {
                                        val index = i * 2 + j
                                        if (index < curveOptions.size) {
                                            val (id, label) = curveOptions[index]
                                            val isSelected = id == channel.velocityCurve
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) Color(0xFF4CAF50) else Color(0xFF2C2C2C),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp)
                                                    .clickable { onVelocityCurveSelected(id) }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = label,
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        // RIGHT SIDE: Graph
                        val displayCurve = channel.velocityCurve
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .height(154.dp) // Height matches 4 buttons (34*4) + 3 spacers (6*3) = 154
                                .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                        ) {
                            val w = size.width
                            val h = size.height
                            val pad = 16f
                            val gW = w - pad * 2
                            val gH = h - pad * 2

                            // Grid
                            val gridColor = Color(0xFF333333)
                            for (i in 0..4) {
                                val x = pad + gW * i / 4f
                                val y = pad + gH * i / 4f
                                drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, pad), androidx.compose.ui.geometry.Offset(x, pad + gH), 0.5f)
                                drawLine(gridColor, androidx.compose.ui.geometry.Offset(pad, y), androidx.compose.ui.geometry.Offset(pad + gW, y), 0.5f)
                            }

                            // Diagonal reference
                            val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            drawLine(Color(0xFF555555), androidx.compose.ui.geometry.Offset(pad, pad + gH), androidx.compose.ui.geometry.Offset(pad + gW, pad), 1f, pathEffect = dash)

                            // Curve
                            val curvePath = Path()
                            val effectiveCurve = if (displayCurve < 0) 0 else displayCurve
                            for (i in 0..127) {
                                val n = i / 127.0
                                val m = when (effectiveCurve) {
                                    0 -> n
                                    1 -> Math.pow(n, 0.75)
                                    2 -> Math.sqrt(n)
                                    3 -> Math.cbrt(n)
                                    4 -> Math.pow(n, 1.5)
                                    5 -> n * n
                                    6 -> {
                                        if (n <= 0) 0.0
                                        else Math.log10(1.0 + 9.0 * n)
                                    }
                                    7 -> {
                                        val x = (n - 0.5) * 12.0
                                        1.0 / (1.0 + Math.exp(-x))
                                    }
                                    else -> n
                                }
                                val px = pad + (i / 127f) * gW
                                val py = pad + gH - (m.toFloat() * gH)
                                if (i == 0) curvePath.moveTo(px, py) else curvePath.lineTo(px, py)
                            }
                            drawPath(curvePath, Color(0xFF00E5FF), style = Stroke(width = 2.5f))

                            // Axes
                            val axisColor = Color(0xFF888888)
                            drawLine(axisColor, androidx.compose.ui.geometry.Offset(pad, pad), androidx.compose.ui.geometry.Offset(pad, pad + gH), 1f)
                            drawLine(axisColor, androidx.compose.ui.geometry.Offset(pad, pad + gH), androidx.compose.ui.geometry.Offset(pad + gW, pad + gH), 1f)
                        }
                    }
                }

                // ADVANCED MIDI FILTERS SECTION
                HorizontalDivider(thickness = 1.dp, color = Color(0xFF424242))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Filtros MIDI & Avançado",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    val filters = listOf(
                        Triple("Sustain (CC 64)", channel.sustainEnabled, "sustainEnabled"),
                        Triple("Modulation (CC 1)", channel.modulationEnabled, "modulationEnabled"),
                        Triple("Expression (CC 11)", channel.expressionEnabled, "expressionEnabled"),
                        Triple("Pitch Bend", channel.pitchBendEnabled, "pitchBendEnabled"),
                        Triple("Foot Ctrl (CC 4)", channel.footControllerEnabled, "footControllerEnabled")
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isTablet) 2 else 1),
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filters.size) { index ->
                            val filter = filters[index]
                            Row(
                                modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = filter.first,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                Switch(
                                    checked = filter.second,
                                    onCheckedChange = { isChecked ->
                                        onMidiFilterToggled(filter.third, isChecked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF4CAF50),
                                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color(0xFFAAAAAA),
                                        uncheckedTrackColor = Color(0xFF424242)
                                    ),
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
