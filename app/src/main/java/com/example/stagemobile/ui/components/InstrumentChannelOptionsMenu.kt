package com.example.stagemobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.stagemobile.domain.model.InstrumentChannel

private enum class ChannelDialogState { MAIN, COLOR_PICKER, REMOVE_CONFIRM }

@Composable
fun InstrumentChannelOptionsMenu(
    channel: InstrumentChannel,
    onDismiss: () -> Unit,
    onColorChange: (Long?) -> Unit,
    onRemoveClick: () -> Unit,
    onAdvancedOptionsClick: () -> Unit = {},
    onDSPEffectsClick: () -> Unit = {}
) {
    var currentState by remember { mutableStateOf(ChannelDialogState.MAIN) }

    val premiumColors = listOf(
        null,       // Padrão (Dark)
        0xFF2196F3, // Vivid Blue
        0xFF4CAF50, // Vivid Green
        0xFFF44336, // Vivid Red
        0xFFFF9800, // Vivid Orange
        0xFF9C27B0, // Vivid Purple
        0xFF00BCD4, // Vivid Cyan
        0xFFFFC107, // Vivid Amber
        0xFFCDDC39, // Vivid Lime
        0xFFE91E63, // Vivid Pink
        0xFF3F51B5, // Vivid Indigo
        0xFF009688, // Teal
        0xFF795548, // Brown
        0xFF607D8B, // Blue Grey
        0xFF673AB7, // Deep Purple
        0xFFFF5722  // Deep Orange
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val isTablet = com.example.stagemobile.utils.UiUtils.rememberIsTablet()
        val dialogWidth = if (isTablet) 0.24f else 0.35f

        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth(dialogWidth)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1E1E1E),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header (Always same or contextual?)
                val headerText = when(currentState) {
                    ChannelDialogState.MAIN -> "OPÇÕES" // Encurtado para largura slim
                    ChannelDialogState.COLOR_PICKER -> "CORES"
                    ChannelDialogState.REMOVE_CONFIRM -> "REMOVER"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (currentState != ChannelDialogState.MAIN) {
                        IconButton(
                            onClick = { currentState = ChannelDialogState.MAIN },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.labelMedium, // Reduzido para largura slim
                        color = Color.Gray,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    if (currentState != ChannelDialogState.MAIN) {
                        Spacer(modifier = Modifier.size(28.dp)) // Balance
                    }
                }
                
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(0.5.dp).fillMaxWidth().background(Color(0xFF333333)))

                when (currentState) {
                    ChannelDialogState.MAIN -> {
                        // Main Menu Selection
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            OptionItem(
                                icon = Icons.Outlined.Palette,
                                label = "Colorizar", // Encurtado
                                onClick = { currentState = ChannelDialogState.COLOR_PICKER }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            OptionItem(
                                icon = Icons.Outlined.Construction,
                                label = "Parâmetros do canal",
                                onClick = {
                                    onDismiss()
                                    onAdvancedOptionsClick()
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OptionItem(
                                icon = Icons.Default.Settings,
                                label = "Rack de Efeitos",
                                onClick = {
                                    onDismiss()
                                    onDSPEffectsClick()
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OptionItem(
                                icon = Icons.Default.Delete,
                                label = "Remover canal", // Encurtado
                                color = Color(0xFFEF5350),
                                onClick = { currentState = ChannelDialogState.REMOVE_CONFIRM }
                            )
                        }
                    }
                    
                    ChannelDialogState.COLOR_PICKER -> {
                        // Color Grid Selection
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            items(premiumColors) { colorVal ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (colorVal != null) Color(colorVal) else Color(0xFF424242))
                                            .border(
                                                width = if (channel.color == colorVal) 1.5.dp else 0.dp,
                                                color = Color.White,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                onColorChange(colorVal)
                                                onDismiss()
                                            }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    ChannelDialogState.REMOVE_CONFIRM -> {
                        // Removal Confirmation
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Deseja remover este canal?",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Instrumentos e configurações serão perdidos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { currentState = ChannelDialogState.MAIN },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                                ) {
                                    Text("CANCEL", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onRemoveClick()
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                                ) {
                                    Text("REMOVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (currentState == ChannelDialogState.MAIN || currentState == ChannelDialogState.COLOR_PICKER) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(modifier = Modifier.height(0.5.dp).fillMaxWidth().background(Color(0xFF333333)))
                    TextButton(
                        onClick = {
                            if (currentState == ChannelDialogState.MAIN) onDismiss()
                            else currentState = ChannelDialogState.MAIN
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    ) {
                        Text(
                            text = if (currentState == ChannelDialogState.MAIN) "FECHAR" else "CANCELAR",
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color = Color.LightGray,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
