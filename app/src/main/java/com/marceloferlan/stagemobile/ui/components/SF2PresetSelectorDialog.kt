package com.marceloferlan.stagemobile.ui.components
import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalConfiguration
import com.marceloferlan.stagemobile.domain.model.Sf2Preset

@Composable
fun SF2PresetSelectorDialog(
    sf2Name: String,
    presets: List<Sf2Preset>,
    currentBank: Int,
    currentProgram: Int,
    onPresetSelected: (bank: Int, program: Int, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPreset by remember(presets, currentBank, currentProgram) { 
        mutableStateOf(presets.find { it.bank == currentBank && it.program == currentProgram }) 
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        val isTablet = com.marceloferlan.stagemobile.utils.UiUtils.rememberIsTablet()
        val screenConfig = LocalConfiguration.current
        val screenWidth = screenConfig.screenWidthDp.dp
        val screenHeight = screenConfig.screenHeightDp.dp

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f)
                .testTag("sf2_preset_selector_dialog")
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.padding(if (isTablet) 20.dp else 12.dp)) {
                // Header
                Text(
                    text = sf2Name,
                    color = Color(0xFF4CAF50),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${presets.size} presets disponíveis",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = if (isTablet) 12.dp else 6.dp)
                )

                HorizontalDivider(color = Color(0xFF333333))

                // Preset List grouped by Bank
                val groupedPresets = presets.groupBy { it.bank }.toSortedMap()

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    groupedPresets.forEach { (bank, bankPresets) ->
                        // Bank Header
                        item {
                            Text(
                                text = "Banco ${bank.toString().padStart(3, '0')}",
                                color = Color(0xFF4CAF50),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }

                        // Presets in this bank
                        items(bankPresets) { preset ->
                            val isCurrentlyActive = preset.bank == currentBank && preset.program == currentProgram
                            val isSelected = selectedPreset == preset

                            val bgColor = when {
                                isSelected -> Color(0xFF2E7D32) // Bright Green for Active Dialog Selection
                                else -> Color(0xFF252525)      // Standard background
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgColor, RoundedCornerShape(6.dp))
                                    .clickable { selectedPreset = preset }
                                    .padding(horizontal = 12.dp, vertical = if (isTablet) 10.dp else 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Program number
                                Text(
                                    text = preset.program.toString().padStart(3, '0'),
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(36.dp)
                                )
                                // Preset name
                                Text(
                                    text = preset.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                // Active indicator
                                if (isCurrentlyActive) {
                                    Text(
                                        text = "●",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(top = if (isTablet) 12.dp else 6.dp))

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isTablet) 12.dp else 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color(0xFF888888))
                    }
                    Button(
                        onClick = {
                            selectedPreset?.let {
                                onPresetSelected(it.bank, it.program, it.name)
                            }
                        },
                        enabled = selectedPreset != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color(0xFF333333)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Selecionar", color = Color.White)
                    }
                }
            }
        }
    }
}
