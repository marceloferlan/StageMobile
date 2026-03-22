package com.example.stagemobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.stagemobile.domain.model.SetStage
import com.example.stagemobile.viewmodel.MixerViewModel

@Composable
fun SetStageQuickSelectorDialog(
    onDismissRequest: () -> Unit,
    viewModel: MixerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentBank by remember { mutableStateOf(1) }
    var loadedSets by remember { mutableStateOf<Map<Int, SetStage>>(emptyMap()) }
    val activeSetStageId by viewModel.activeSetStageId.collectAsState()

    LaunchedEffect(currentBank) {
        loadedSets = viewModel.setStageRepo?.getSetStagesForBank(currentBank) ?: emptyMap()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color(0xFF1A1A1A),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // HEADER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Alterar Set Stage",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(28.dp)
                            .clickable { onDismissRequest() },
                        color = Color(0xFF444444),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Close,
                                contentDescription = "Fechar",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                BankNavigationPanel(
                    currentBank = currentBank,
                    onPrevious = { if (currentBank > 1) currentBank-- },
                    onNext = { if (currentBank < 10) currentBank++ },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                SetStageGrid(
                    currentBank = currentBank,
                    loadedSets = loadedSets,
                    activeSetStageId = activeSetStageId,
                    onSetStageClicked = { slotId, _ ->
                        viewModel.loadSetStage(context, currentBank, slotId)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
