package com.example.stagemobile.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.stagemobile.domain.model.InstrumentChannel

@Composable
fun AdvancedParamsDialog(
    channel: InstrumentChannel,
    activeMidiDevices: Set<String>,
    onDismiss: () -> Unit,
    onMidiDeviceSelected: (String?) -> Unit,
    onMidiChannelSelected: (Int) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .border(0.5.dp, Color(0xFF424242), RoundedCornerShape(12.dp)),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                        .height(345.dp)
                ) {
                    // LEFT COLUMN: MIDI ROUTING (65%)
                    Column(
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Selecione o MIDI device",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val deviceOptions = mutableListOf<String?>()
                        deviceOptions.add(null) // "Todos Ativos"
                        deviceOptions.addAll(activeMidiDevices)

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
                            
                            if (deviceOptions.size == 1) {
                                item {
                                    Text(
                                        text = "Nenhum teclado habilitado em Configurações.",
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

                    // RIGHT COLUMN: MIDI CHANNEL (35%)
                    Column(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
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
                            columns = GridCells.Fixed(3),
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
            }
        }
    }
}
