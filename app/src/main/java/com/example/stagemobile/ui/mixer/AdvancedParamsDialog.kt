package com.example.stagemobile.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.stagemobile.domain.model.InstrumentChannel

@Composable
fun AdvancedParamsDialog(
    channel: InstrumentChannel,
    activeMidiDevices: Set<String>,
    onDismiss: () -> Unit,
    onMidiDeviceSelected: (String?) -> Unit,
    onMidiChannelSelected: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Parâmetros do Canal ${channel.id + 1}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                HorizontalDivider(color = Color(0xFF424242))

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Roteamento de Controlador MIDI",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // List of Active Devices from Settings
                val options = mutableListOf<String?>()
                options.add(null) // "Todos Ativos"
                options.addAll(activeMidiDevices)

                LazyColumn(
                    modifier = Modifier.fillMaxHeight(0.4f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options) { deviceName ->
                        val isSelected = channel.midiDeviceName == deviceName
                        val label = deviceName ?: "Todos os Teclados Ativos"
                        
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF4CAF50) else Color(0xFF2C2C2C),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable {
                                    onMidiDeviceSelected(deviceName)
                                    onDismiss()
                                }
                        ) {
                            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                if (options.size == 1) { // Only "Todos Ativos"
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nenhum teclado habilitado em Configurações.",
                        color = Color(0xFFFFCC00),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFF424242))
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Canal MIDI",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // MIDI Channel Selection Horizontal Scroll
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allChannelsSelection = listOf(-1) + (0..15).toList()
                    allChannelsSelection.forEach { midiCh ->
                        val isSelected = channel.midiChannel == midiCh
                        val label = if (midiCh == -1) "ALL" else "CH ${midiCh + 1}"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF4CAF50) else Color(0xFF2C2C2C),
                            modifier = Modifier
                                .height(48.dp)
                                .clickable {
                                    onMidiChannelSelected(midiCh)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                ) {
                    Text("Fechar")
                }
            }
        }
    }
}
