package com.marceloferlan.stagemobile.ui.components
import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import com.marceloferlan.stagemobile.domain.model.SetStage
import com.marceloferlan.stagemobile.viewmodel.MixerViewModel
import com.marceloferlan.stagemobile.ui.components.BankNavigationPanel
import com.marceloferlan.stagemobile.ui.components.SetStageGrid

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

    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = true, onClick = onDismissRequest),
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
                .testTag("set_stage_quick_selector_dialog")
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // HEADER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isTablet) 16.dp else 8.dp)
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

                Spacer(modifier = Modifier.height(if (isTablet) 16.dp else 4.dp))

                BankNavigationPanel(
                    currentBank = currentBank,
                    onPrevious = { if (currentBank > 1) currentBank-- },
                    onNext = { if (currentBank < 10) currentBank++ },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(if (isTablet) 24.dp else 8.dp))

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
