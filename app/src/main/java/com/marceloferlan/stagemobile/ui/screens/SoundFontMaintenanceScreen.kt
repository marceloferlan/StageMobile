package com.marceloferlan.stagemobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marceloferlan.stagemobile.domain.model.SoundFontMetadata
import com.marceloferlan.stagemobile.viewmodel.MixerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SoundFontMaintenanceScreen(
    viewModel: MixerViewModel,
    onNavigateBack: () -> Unit
) {
    val availableSF2 by viewModel.availableSoundFonts.collectAsState()
    val isTablet = com.marceloferlan.stagemobile.utils.UiUtils.rememberIsTablet()
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    
    val categories = listOf(
        "Piano", "EP/FM", "Pad", "Synth", "Lead", "Bass", 
        "Brass", "Strings", "Organ", "Guitar", 
        "Drums/Percussion", "FX", "Outros"
    )

    val filteredSF2 = remember(availableSF2, selectedTags) {
        if (selectedTags.isEmpty()) availableSF2
        else availableSF2.filter { it.metadata.tags.any { tag -> selectedTags.contains(tag) } }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.showSf2Import(it) }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biblioteca de SoundFonts", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    Button(
                        onClick = { 
                            launcher.launch(arrayOf(
                                "application/x-soundfont",
                                "audio/x-soundfont",
                                "audio/soundfont",
                                "application/octet-stream",
                                "audio/*"
                            )) 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Importar SF2", fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Categorias", 
                color = Color(0xFF888888), 
                fontSize = 12.sp, 
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedTags.contains(category)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTags = if (isSelected) selectedTags - category else selectedTags + category
                        },
                        label = { Text(category, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4CAF50),
                            labelColor = Color(0xFFBBBBBB),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (filteredSF2.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum SoundFont encontrado.", color = Color(0xFF555555))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTablet) 12.dp else 8.dp)
                ) {
                    items(filteredSF2) { sf2 ->
                        SoundFontItem(
                            item = sf2,
                            isTablet = isTablet,
                            onDelete = { viewModel.showSf2Delete(sf2.metadata) },
                            onLongClick = { viewModel.showSf2Rename(sf2.metadata) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoundFontItem(
    item: MixerViewModel.SoundFontListItem,
    isTablet: Boolean,
    onDelete: () -> Unit,
    onLongClick: () -> Unit
) {
    val sf2 = item.metadata
    val verticalPadding = if (isTablet) 12.dp else 8.dp
    Surface(
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f), 
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    sf2.fileName, 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
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
            
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.2f)),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Excluir", color = Color(0xFFEF5350), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
