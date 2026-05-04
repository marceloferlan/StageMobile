package com.marceloferlan.stagemobile.ui.components
import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import com.marceloferlan.stagemobile.viewmodel.MixerViewModel
import com.marceloferlan.stagemobile.viewmodel.MixerViewModel.SoundFontListItem
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundFontSelectorDialog(
    availableSoundFonts: List<MixerViewModel.SoundFontListItem>,
    onSoundFontSelected: (com.marceloferlan.stagemobile.domain.model.SoundFontMetadata) -> Unit,
    onNavigateToMaintenance: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    
    val categories = listOf(
        "Piano", "EP/FM", "Pad", "Synth", "Lead", "Bass", 
        "Brass", "Strings", "Organ", "Bells", "Guitar", 
        "Drums", "FX", "Outros"
    )

    val filteredSF2: List<MixerViewModel.SoundFontListItem> = remember(availableSoundFonts, selectedTags) {
        val locals = availableSoundFonts.filter { it.isLocal }
        if (selectedTags.isEmpty()) locals
        else locals.filter { it.metadata.tags.any { tag -> selectedTags.contains(tag) } }
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
                .testTag("sound_font_selector_dialog")
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.padding(if (isTablet) 20.dp else 16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Selecionar Instrumento", color = Color.White, fontSize = if (isTablet) 20.sp else 18.sp, fontWeight = FontWeight.Bold)
                        Text("${filteredSF2.size} SoundFonts disponíveis", color = Color(0xFF888888), fontSize = if (isTablet) 12.sp else 11.sp)
                    }
                    
                    Button(
                        onClick = onNavigateToMaintenance,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (isTablet) 8.dp else 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gerenciar SF2", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(if (isTablet) 16.dp else 12.dp))
                HorizontalDivider(color = Color(0xFF333333))
                
                // Categories Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = if (isTablet) 12.dp else 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedTags.contains(category)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTags = if (isSelected) selectedTags - category else selectedTags + category
                            },
                            label = {
                                Text(
                                    category,
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.widthIn(min = 40.dp).fillMaxWidth()
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF4CAF50),
                                labelColor = Color(0xFFBBBBBB),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.widthIn(min = 72.dp)
                        )
                    }
                }

                // List
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredSF2.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nenhum SoundFont nesta categoria.", color = Color(0xFF555555))
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredSF2) { sf2 ->
                                SoundFontSelectorItem(
                                    item = sf2,
                                    onClick = { if (sf2.isLocal) onSoundFontSelected(sf2.metadata) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isTablet) 12.dp else 6.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancelar", color = Color(0xFF888888))
                }
            }
        }
    }
}

@Composable
fun SoundFontSelectorItem(
    item: MixerViewModel.SoundFontListItem,
    onClick: () -> Unit
) {
    val isTablet = com.marceloferlan.stagemobile.utils.UiUtils.rememberIsTablet()
    val sf2 = item.metadata
    Surface(
        color = Color(0xFF252525),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.isLocal) 1f else 0.5f)
            .clickable(enabled = item.isLocal) { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(if (isTablet) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                sf2.fileName, 
                color = Color.White, 
                fontWeight = FontWeight.Bold, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (tag in sf2.tags.take(3)) {
                    Text(
                        text = "#$tag", 
                        color = Color(0xFF4CAF50), 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                
                if (item.isLocal) {
                    Icon(
                        Icons.Default.CheckCircle, 
                        contentDescription = "Local", 
                        tint = Color(0xFF4CAF50), 
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.CloudDownload, 
                        contentDescription = "Cloud Only", 
                        tint = Color(0xFF888888), 
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
