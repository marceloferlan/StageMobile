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
    val isTablet = UiUtils.rememberIsTablet()
    val backupRepo = remember { BackupRepository(context) }

    // State
    var configBackupInfo by remember { mutableStateOf<BackupMetadata?>(null) }
    var fullBackupInfo by remember { mutableStateOf<BackupMetadata?>(null) }
    var isLoading by remember { mutableStateOf(false) }
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
                        performConfigBackup(scope, backupRepo, context, onEngineReinit,
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
                        performFullBackup(scope, backupRepo, context, onEngineReinit,
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
                        Text(progressMessage ?: "", color = Color.White, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (progressValue > 0f) {
                            LinearProgressIndicator(
                                progress = { progressValue },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF26C6DA),
                                trackColor = Color(0xFF333333)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF26C6DA),
                                trackColor = Color(0xFF333333)
                            )
                        }
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
                            performConfigBackup(scope, backupRepo, context, onEngineReinit,
                                onStart = { isLoading = true; progressMessage = it },
                                onDone = { meta, err ->
                                    isLoading = false; progressMessage = null
                                    if (meta != null) configBackupInfo = meta
                                    if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                })
                        } else {
                            performFullBackup(scope, backupRepo, context, onEngineReinit,
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
                        scope.launch {
                            isLoading = true
                            val result = if (type == BackupType.CONFIG) {
                                progressMessage = "Restaurando configurações..."
                                backupRepo.restoreConfigBackup { progressMessage = it }
                            } else {
                                backupRepo.restoreFullBackup { msg, prog ->
                                    progressMessage = msg; progressValue = prog
                                }
                            }
                            isLoading = false; progressMessage = null; progressValue = 0f
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

            // Backup info (se existir)
            if (backupInfo != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1A1A1A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        val date = dateFormat.format(backupInfo.createdAt.toDate())
                        Text("Último backup: $date", color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Dispositivo: ${backupInfo.deviceName}", color = Color.Gray, fontSize = 11.sp)
                        Text("Versão: ${backupInfo.appVersion}", color = Color.Gray, fontSize = 11.sp)
                        if (backupInfo.sf2FileCount > 0) {
                            val sizeMb = String.format(Locale.US, "%.1f", backupInfo.sf2SizeBytes / 1_048_576.0)
                            Text("SoundFonts: ${backupInfo.sf2FileCount} arquivos ($sizeMb MB)", color = Color.Gray, fontSize = 11.sp)
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
        result.fold(
            onSuccess = {
                onDone(it, null)
                Toast.makeText(context, "Backup de configurações salvo!", Toast.LENGTH_SHORT).show()
            },
            onFailure = { onDone(null, it.message) }
        )
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
        result.fold(
            onSuccess = {
                onDone(it, null)
                Toast.makeText(context, "Backup completo salvo!", Toast.LENGTH_SHORT).show()
            },
            onFailure = { onDone(null, it.message) }
        )
    }
}
