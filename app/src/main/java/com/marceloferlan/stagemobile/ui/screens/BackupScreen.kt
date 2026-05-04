package com.marceloferlan.stagemobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marceloferlan.stagemobile.data.backup.BackupMetadata
import com.marceloferlan.stagemobile.data.backup.BackupRepository
import com.marceloferlan.stagemobile.data.backup.BackupType
import com.marceloferlan.stagemobile.utils.UiUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    onEngineReinit: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Scope que sobrevive à navegação (backup não deve ser cancelado se o usuário sair da tela)
    val persistentScope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()) }
    val isTablet = UiUtils.rememberIsTablet()
    val backupRepo = remember { BackupRepository(context) }

    // State
    var configBackupInfo by remember { mutableStateOf<BackupMetadata?>(null) }
    var fullBackupInfo by remember { mutableStateOf<BackupMetadata?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf<String?>(null) }
    var progressValue by remember { mutableStateOf(0f) }
    var showConfirmRestore by remember { mutableStateOf<BackupType?>(null) }
    var showConfirmOverwrite by remember { mutableStateOf<BackupType?>(null) }

    // Load backup info on first composition
    LaunchedEffect(Unit) {
        configBackupInfo = backupRepo.getConfigBackupInfo()
        fullBackupInfo = backupRepo.getFullBackupInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restauração", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111111))
            )
        },
        containerColor = Color(0xFF1A1A1A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = if (isTablet) 32.dp else 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isTablet) 16.dp else 10.dp)
        ) {
            Spacer(modifier = Modifier.height(if (isTablet) 8.dp else 4.dp))

            // ── BACKUP DE CONFIGURAÇÕES ──────────────────────────
            BackupCard(
                title = "Backup de Configurações",
                description = "Salva settings, Set Stages, MIDI Learn e metadados SF2.",
                icon = Icons.Outlined.Settings,
                backupInfo = configBackupInfo,
                isTablet = isTablet,
                onBackup = {
                    if (configBackupInfo != null) {
                        showConfirmOverwrite = BackupType.CONFIG
                    } else {
                        performConfigBackup(persistentScope, backupRepo, context, onEngineReinit,
                            onStart = { isLoading = true; progressMessage = it },
                            onDone = { meta, err ->
                                isLoading = false; progressMessage = null
                                if (meta != null) configBackupInfo = meta
                                if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            })
                    }
                },
                onRestore = { showConfirmRestore = BackupType.CONFIG },
                isLoading = isLoading
            )

            // ── BACKUP COMPLETO ──────────────────────────────────
            BackupCard(
                title = "Backup Completo",
                description = "Salva configurações + todos os arquivos SoundFont (.sf2).",
                icon = Icons.Outlined.Backup,
                backupInfo = fullBackupInfo,
                isTablet = isTablet,
                onBackup = {
                    if (fullBackupInfo != null) {
                        showConfirmOverwrite = BackupType.FULL
                    } else {
                        performFullBackup(persistentScope, backupRepo, context, onEngineReinit,
                            onStart = { msg, prog -> isLoading = true; progressMessage = msg; progressValue = prog },
                            onDone = { meta, err ->
                                isLoading = false; progressMessage = null; progressValue = 0f
                                if (meta != null) fullBackupInfo = meta
                                if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            })
                    }
                },
                onRestore = { showConfirmRestore = BackupType.FULL },
                isLoading = isLoading
            )

            // ── PROGRESS OVERLAY ─────────────────────────────────
            if (isLoading && progressMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2C2C2C),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Separar linhas da mensagem
                        val lines = (progressMessage ?: "").split("\n")
                        // Primeira linha: título (ex: "Enviando 1 de 2")
                        Text(
                            lines.firstOrNull() ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isTablet) 15.sp else 13.sp
                        )
                        // Linhas adicionais: detalhes (nome do arquivo, MB enviados)
                        for (line in lines.drop(1)) {
                            Text(
                                line,
                                color = Color(0xFFAAAAAA),
                                fontSize = if (isTablet) 13.sp else 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (progressValue > 0f) {
                            LinearProgressIndicator(
                                progress = { progressValue },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = Color(0xFF26C6DA),
                                trackColor = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "${(progressValue * 100).toInt()}%",
                                color = Color(0xFF26C6DA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = Color(0xFF26C6DA),
                                trackColor = Color(0xFF333333)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (isRestoring) "Restauração de backup em andamento. Aguarde a conclusão!"
                            else "Backup em andamento. Aguarde a conclusão!",
                            color = Color(0xFF888888),
                            fontSize = if (isTablet) 12.sp else 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isTablet) 16.dp else 8.dp))
        }

        // ── DIALOGS ──────────────────────────────────────────────

        // Confirmação de sobrescrita (novo backup quando já existe)
        showConfirmOverwrite?.let { type ->
            AlertDialog(
                onDismissRequest = { showConfirmOverwrite = null },
                title = { Text("Substituir Backup?", fontWeight = FontWeight.Bold) },
                text = { Text("Isso substituirá o backup anterior de ${if (type == BackupType.CONFIG) "configurações" else "backup completo"}. Deseja continuar?") },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirmOverwrite = null
                        if (type == BackupType.CONFIG) {
                            performConfigBackup(persistentScope, backupRepo, context, onEngineReinit,
                                onStart = { isLoading = true; progressMessage = it },
                                onDone = { meta, err ->
                                    isLoading = false; progressMessage = null
                                    if (meta != null) configBackupInfo = meta
                                    if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                })
                        } else {
                            performFullBackup(persistentScope, backupRepo, context, onEngineReinit,
                                onStart = { msg, prog -> isLoading = true; progressMessage = msg; progressValue = prog },
                                onDone = { meta, err ->
                                    isLoading = false; progressMessage = null; progressValue = 0f
                                    if (meta != null) fullBackupInfo = meta
                                    if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                })
                        }
                    }) { Text("SUBSTITUIR", color = Color(0xFFEF5350)) }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmOverwrite = null }) { Text("CANCELAR") }
                },
                containerColor = Color(0xFF2C2C2C),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray
            )
        }

        // Confirmação de restauração
        showConfirmRestore?.let { type ->
            AlertDialog(
                onDismissRequest = { showConfirmRestore = null },
                title = { Text("Restaurar Backup?", fontWeight = FontWeight.Bold) },
                text = { Text("Isso substituirá TODAS as configurações atuais${if (type == BackupType.FULL) " e os arquivos SoundFont" else ""}. Deseja continuar?") },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirmRestore = null
                        persistentScope.launch {
                            isLoading = true; isRestoring = true
                            val result = if (type == BackupType.CONFIG) {
                                progressMessage = "Restaurando configurações..."
                                backupRepo.restoreConfigBackup { progressMessage = it }
                            } else {
                                backupRepo.restoreFullBackup { msg, prog ->
                                    progressMessage = msg; progressValue = prog
                                }
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                isLoading = false; isRestoring = false; progressMessage = null; progressValue = 0f
                                result.fold(
                                    onSuccess = {
                                        onEngineReinit()
                                        Toast.makeText(context, "Restauração concluída!", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = {
                                        Toast.makeText(context, it.message ?: "Erro na restauração", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    }) { Text("RESTAURAR", color = Color(0xFFEF5350)) }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmRestore = null }) { Text("CANCELAR") }
                },
                containerColor = Color(0xFF2C2C2C),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// COMPOSABLE: Card de backup (Config ou Full)
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun BackupCard(
    title: String,
    description: String,
    icon: ImageVector,
    backupInfo: BackupMetadata?,
    isTablet: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF242424),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(if (isTablet) 20.dp else 14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color(0xFF26C6DA), modifier = Modifier.size(if (isTablet) 28.dp else 22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (isTablet) 18.sp else 15.sp)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(description, color = Color.Gray, fontSize = if (isTablet) 13.sp else 11.sp)

            // Backup info (se existir) — layout tabular compacto
            if (backupInfo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1A1A1A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val dateFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
                    val date = dateFormat.format(backupInfo.createdAt.toDate())
                    val hasSf2 = backupInfo.sf2FileCount > 0
                    val labelSize = if (isTablet) 10.sp else 9.sp
                    val valueSize = if (isTablet) 12.sp else 10.sp

                    if (isTablet) {
                        // Tablet: tudo em uma Row
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            BackupInfoCell("Backup", date, Color(0xFF81C784), labelSize, valueSize, Modifier.weight(1f))
                            BackupInfoCell("Dispositivo", backupInfo.deviceName, Color.White, labelSize, valueSize, Modifier.weight(1f), maxLines = 1)
                            BackupInfoCell("Versão", backupInfo.appVersion, Color.White, labelSize, valueSize, Modifier.weight(0.6f))
                            if (hasSf2) {
                                val sizeMb = String.format(Locale.US, "%.1f", backupInfo.sf2SizeBytes / 1_048_576.0)
                                BackupInfoCell("SoundFonts", "${backupInfo.sf2FileCount} (${sizeMb}MB)", Color.White, labelSize, valueSize, Modifier.weight(1f))
                            }
                        }
                    } else {
                        // Phone: 2 rows de 2 colunas
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                BackupInfoCell("Backup", date, Color(0xFF81C784), labelSize, valueSize, Modifier.weight(1f))
                                BackupInfoCell("Dispositivo", backupInfo.deviceName, Color.White, labelSize, valueSize, Modifier.weight(1f), maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                BackupInfoCell("Versão", backupInfo.appVersion, Color.White, labelSize, valueSize, Modifier.weight(1f))
                                if (hasSf2) {
                                    val sizeMb = String.format(Locale.US, "%.1f", backupInfo.sf2SizeBytes / 1_048_576.0)
                                    BackupInfoCell("SoundFonts", "${backupInfo.sf2FileCount} (${sizeMb}MB)", Color.White, labelSize, valueSize, Modifier.weight(1f))
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botões
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Botão Backup
                Button(
                    onClick = onBackup,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f).height(if (isTablet) 44.dp else 38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2),
                        disabledContainerColor = Color(0xFF333333)
                    )
                ) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (backupInfo != null) "Atualizar" else "Salvar", fontSize = if (isTablet) 14.sp else 12.sp, fontWeight = FontWeight.Bold)
                }

                // Botão Restaurar (só aparece se tem backup)
                if (backupInfo != null) {
                    OutlinedButton(
                        onClick = onRestore,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).height(if (isTablet) 44.dp else 38.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restaurar", fontSize = if (isTablet) 14.sp else 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupInfoCell(
    label: String,
    value: String,
    valueColor: Color,
    labelSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(label, color = Color(0xFF888888), fontSize = labelSize)
        Text(
            value,
            color = valueColor,
            fontSize = valueSize,
            fontWeight = if (valueColor == Color(0xFF81C784)) FontWeight.Bold else FontWeight.Normal,
            maxLines = maxLines,
            overflow = if (maxLines < Int.MAX_VALUE) androidx.compose.ui.text.style.TextOverflow.Ellipsis
                       else androidx.compose.ui.text.style.TextOverflow.Clip
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// HELPERS: executam backup em coroutine
// ═══════════════════════════════════════════════════════════════════════

private fun performConfigBackup(
    scope: kotlinx.coroutines.CoroutineScope,
    repo: BackupRepository,
    context: android.content.Context,
    onEngineReinit: () -> Unit,
    onStart: (String) -> Unit,
    onDone: (BackupMetadata?, String?) -> Unit
) {
    scope.launch {
        val result = repo.createConfigBackup { msg -> onStart(msg) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            result.fold(
                onSuccess = {
                    onDone(it, null)
                    Toast.makeText(context, "Backup de configurações salvo!", Toast.LENGTH_SHORT).show()
                },
                onFailure = { onDone(null, it.message) }
            )
        }
    }
}

private fun performFullBackup(
    scope: kotlinx.coroutines.CoroutineScope,
    repo: BackupRepository,
    context: android.content.Context,
    onEngineReinit: () -> Unit,
    onStart: (String, Float) -> Unit,
    onDone: (BackupMetadata?, String?) -> Unit
) {
    scope.launch {
        val result = repo.createFullBackup { msg, prog -> onStart(msg, prog) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            result.fold(
                onSuccess = {
                    onDone(it, null)
                    Toast.makeText(context, "Backup completo salvo!", Toast.LENGTH_SHORT).show()
                },
                onFailure = { onDone(null, it.message) }
            )
        }
    }
}
