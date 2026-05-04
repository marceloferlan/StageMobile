package com.marceloferlan.stagemobile.data.backup

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

/**
 * BackupRepository — Orquestra criação, restauração e gerenciamento de backups.
 *
 * IMPORTANTE: O SoundFontRepository chama db.disableNetwork() no init para evitar
 * Death-Loop do GMS em tablets de palco. Por isso, toda operação de backup precisa
 * chamar withFirestoreNetwork {} que habilita a rede temporariamente.
 *
 * Estrutura no Firestore:
 *   backups/{userId}/data/config   → ConfigSnapshot (JSON serializado)
 *   backups/{userId}/data/full     → ConfigSnapshot + metadata dos SF2
 *   backups/{userId}/metadata/config → BackupMetadata
 *   backups/{userId}/metadata/full   → BackupMetadata
 *
 * Estrutura no Cloudflare R2 (backup full):
 *   backups/{userId}/full/soundfonts/{fileName}.sf2
 */
class BackupRepository(
    private val context: Context,
    private val storageProvider: BackupStorageProvider = CloudflareR2StorageProvider()
) {
    companion object {
        private const val TAG = "BackupRepository"
        private const val MIN_INTERVAL_MS = 0L // TODO: restaurar para 3600_000L (1 hora) antes do release
    }

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val userId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    private val appVersion: String
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }

    // ════════════════════════════════════════════════════════════════════
    // CONFIG BACKUP
    // ════════════════════════════════════════════════════════════════════

    suspend fun createConfigBackup(onProgress: ((String) -> Unit)? = null): Result<BackupMetadata> {
        return withFirestoreNetwork {
            try {
                checkMinIntervalOnline(BackupType.CONFIG)?.let {
                    return@withFirestoreNetwork Result.failure(it)
                }

                onProgress?.invoke("Capturando configurações...")
                val snapshot = ConfigSnapshot.capture(context, appVersion)
                val snapshotMap = ConfigSnapshot.toMap(snapshot)
                val configJson = com.google.gson.Gson().toJson(snapshotMap)

                val metadata = BackupMetadata(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    type = BackupType.CONFIG,
                    createdAt = Timestamp.now(),
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    appVersion = appVersion,
                    configSizeBytes = configJson.toByteArray().size.toLong(),
                    status = BackupStatus.COMPLETE
                )

                onProgress?.invoke("Salvando na nuvem...")
                firestoreSet("backups", "data", "config", snapshotMap)
                firestoreSet("backups", "metadata", "config", metadata.toMap())

                Log.i(TAG, "Config backup created: ${metadata.configSizeBytes} bytes")
                Result.success(metadata)
            } catch (e: Exception) {
                Log.e(TAG, "Config backup failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun restoreConfigBackup(onProgress: ((String) -> Unit)? = null): Result<Unit> {
        return withFirestoreNetwork {
            try {
                onProgress?.invoke("Baixando configurações...")

                val doc = db.collection("backups").document(userId)
                    .collection("data").document("config")
                    .get(Source.SERVER)
                    .await()

                if (!doc.exists()) {
                    return@withFirestoreNetwork Result.failure(
                        Exception("Nenhum backup de configurações encontrado.")
                    )
                }

                val snapshot = ConfigSnapshot.fromMap(doc.data ?: emptyMap())

                onProgress?.invoke("Restaurando configurações...")
                ConfigSnapshot.restore(context, snapshot)

                Log.i(TAG, "Config backup restored from ${snapshot.exportedAt}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Config restore failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // FULL BACKUP
    // ════════════════════════════════════════════════════════════════════

    suspend fun createFullBackup(onProgress: ((String, Float) -> Unit)? = null): Result<BackupMetadata> {
        return withFirestoreNetwork {
            try {
                checkMinIntervalOnline(BackupType.FULL)?.let {
                    return@withFirestoreNetwork Result.failure(it)
                }

                // 1. Config snapshot
                onProgress?.invoke("Capturando configurações...", 0f)
                val snapshot = ConfigSnapshot.capture(context, appVersion)
                val snapshotMap = ConfigSnapshot.toMap(snapshot)
                val configJson = com.google.gson.Gson().toJson(snapshotMap)

                // 2. Upload SF2 files (via R2, não usa Firestore)
                val soundfontsDir = File(context.filesDir, "soundfonts")
                val sf2Files = soundfontsDir.listFiles()
                    ?.filter { it.extension.equals("sf2", ignoreCase = true) } ?: emptyList()
                var totalSf2Bytes = 0L
                val remotePath = "backups/$userId/full/soundfonts"

                onProgress?.invoke("Preparando upload...", 0.05f)
                try {
                    val existingFiles = storageProvider.listFiles(remotePath).getOrDefault(emptyList())
                    for (file in existingFiles) {
                        storageProvider.deleteFile("$remotePath/$file")
                    }
                } catch (_: Exception) { /* ignore cleanup errors */ }

                val allSf2TotalBytes = sf2Files.sumOf { it.length() }
                var allSf2UploadedBytes = 0L
                val failedFiles = mutableListOf<String>()

                for ((index, file) in sf2Files.withIndex()) {
                    val fileSizeMb = String.format(java.util.Locale.US, "%.0f", file.length() / 1_048_576.0)
                    val fileLabel = "${file.nameWithoutExtension} (${fileSizeMb}MB)"
                    val fileCounter = "${index + 1} de ${sf2Files.size}"

                    onProgress?.invoke(
                        "Enviando $fileCounter\n$fileLabel",
                        0.1f + (allSf2UploadedBytes.toFloat() / allSf2TotalBytes.coerceAtLeast(1)) * 0.85f
                    )

                    val fileStartBytes = allSf2UploadedBytes
                    val result = storageProvider.uploadFile(
                        localFile = file,
                        remotePath = "$remotePath/${file.name}",
                        onProgress = { fileFraction ->
                            val currentBytes = fileStartBytes + (file.length() * fileFraction).toLong()
                            val totalUploadedMb = String.format(java.util.Locale.US, "%.0f", currentBytes / 1_048_576.0)
                            val totalMb = String.format(java.util.Locale.US, "%.0f", allSf2TotalBytes / 1_048_576.0)
                            val overallFraction = currentBytes.toFloat() / allSf2TotalBytes.coerceAtLeast(1)

                            onProgress?.invoke(
                                "Enviando $fileCounter\n$fileLabel\n${totalUploadedMb}MB / ${totalMb}MB",
                                0.1f + overallFraction * 0.85f
                            )
                        }
                    )

                    if (result.isFailure) {
                        Log.e(TAG, "Failed to upload ${file.name}: ${result.exceptionOrNull()?.message}")
                        failedFiles.add(file.name)
                    } else {
                        totalSf2Bytes += file.length()
                    }
                    allSf2UploadedBytes += file.length()
                }

                // Se TODOS os SF2 falharam, aborta o backup
                if (sf2Files.isNotEmpty() && failedFiles.size == sf2Files.size) {
                    return@withFirestoreNetwork Result.failure(
                        Exception("Falha no upload de todos os SoundFonts. Verifique sua conexão.")
                    )
                }

                // 3. Salva config + metadata no Firestore
                onProgress?.invoke("Finalizando...", 0.98f)
                val metadata = BackupMetadata(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    type = BackupType.FULL,
                    createdAt = Timestamp.now(),
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    appVersion = appVersion,
                    configSizeBytes = configJson.toByteArray().size.toLong(),
                    sf2SizeBytes = totalSf2Bytes,
                    sf2FileCount = sf2Files.size,
                    status = BackupStatus.COMPLETE
                )

                firestoreSet("backups", "data", "full", snapshotMap)
                firestoreSet("backups", "metadata", "full", metadata.toMap())

                onProgress?.invoke("Backup completo!", 1f)
                Log.i(TAG, "Full backup created: config=${metadata.configSizeBytes}B, sf2=${totalSf2Bytes}B (${sf2Files.size} files)")
                Result.success(metadata)
            } catch (e: Exception) {
                Log.e(TAG, "Full backup failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun restoreFullBackup(onProgress: ((String, Float) -> Unit)? = null): Result<Unit> {
        return withFirestoreNetwork {
            try {
                onProgress?.invoke("Baixando configurações...", 0f)

                val doc = db.collection("backups").document(userId)
                    .collection("data").document("full")
                    .get(Source.SERVER)
                    .await()

                if (!doc.exists()) {
                    return@withFirestoreNetwork Result.failure(
                        Exception("Nenhum backup completo encontrado.")
                    )
                }

                val snapshot = ConfigSnapshot.fromMap(doc.data ?: emptyMap())
                onProgress?.invoke("Restaurando configurações...", 0.05f)
                ConfigSnapshot.restore(context, snapshot)

                // 2. Download SF2 files (via R2, não usa Firestore)
                val remotePath = "backups/$userId/full/soundfonts"
                val remoteFiles = storageProvider.listFiles(remotePath).getOrDefault(emptyList())

                val soundfontsDir = File(context.filesDir, "soundfonts").apply {
                    if (!exists()) mkdirs()
                }

                for ((index, fileName) in remoteFiles.withIndex()) {
                    val fileProgress = index.toFloat() / remoteFiles.size.coerceAtLeast(1)
                    onProgress?.invoke("Baixando $fileName...", 0.1f + fileProgress * 0.85f)

                    val localFile = File(soundfontsDir, fileName)
                    val result = storageProvider.downloadFile(
                        remotePath = "$remotePath/$fileName",
                        localDestination = localFile
                    )

                    if (result.isFailure) {
                        Log.e(TAG, "Failed to download $fileName: ${result.exceptionOrNull()?.message}")
                    }
                }

                onProgress?.invoke("Restauração completa!", 1f)
                Log.i(TAG, "Full backup restored: config from ${snapshot.exportedAt}, ${remoteFiles.size} SF2 files")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Full restore failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // METADATA & INFO
    // ════════════════════════════════════════════════════════════════════

    suspend fun getConfigBackupInfo(): BackupMetadata? {
        return withFirestoreNetwork {
            try {
                val doc = db.collection("backups").document(userId)
                    .collection("metadata").document("config")
                    .get(Source.SERVER).await()
                if (doc.exists()) BackupMetadata.fromMap(doc.data ?: emptyMap()) else null
            } catch (e: Exception) {
                Log.d(TAG, "No config backup info yet: ${e.message}")
                null
            }
        }
    }

    suspend fun getFullBackupInfo(): BackupMetadata? {
        return withFirestoreNetwork {
            try {
                val doc = db.collection("backups").document(userId)
                    .collection("metadata").document("full")
                    .get(Source.SERVER).await()
                if (doc.exists()) BackupMetadata.fromMap(doc.data ?: emptyMap()) else null
            } catch (e: Exception) {
                Log.d(TAG, "No full backup info yet: ${e.message}")
                null
            }
        }
    }

    suspend fun deleteBackup(type: BackupType): Result<Unit> {
        return withFirestoreNetwork {
            try {
                db.collection("backups").document(userId)
                    .collection("data").document(type.name.lowercase())
                    .delete().await()

                db.collection("backups").document(userId)
                    .collection("metadata").document(type.name.lowercase())
                    .delete().await()

                if (type == BackupType.FULL) {
                    val remotePath = "backups/$userId/full/soundfonts"
                    val files = storageProvider.listFiles(remotePath).getOrDefault(emptyList())
                    for (file in files) {
                        storageProvider.deleteFile("$remotePath/$file")
                    }
                }

                Log.i(TAG, "Backup ${type.name} deleted")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Delete backup failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Habilita a rede do Firestore temporariamente para toda a operação.
     * O SoundFontRepository desabilita a rede no init (evita Death-Loop do GMS),
     * então precisamos reabilitar antes de operações de backup que precisam do servidor.
     *
     * Uma única chamada enableNetwork/disableNetwork por operação pública — sem toggle
     * rápido entre chamadas internas.
     */
    private suspend fun <T> withFirestoreNetwork(block: suspend () -> T): T {
        db.enableNetwork().await()
        Log.d(TAG, "Firestore network enabled")
        try {
            return block()
        } finally {
            db.disableNetwork().await()
            Log.d(TAG, "Firestore network disabled")
        }
    }

    /**
     * Escreve no Firestore com timeout de 15s.
     * Rede já deve estar habilitada via withFirestoreNetwork().
     */
    private suspend fun firestoreSet(
        collection: String,
        subCollection: String,
        docId: String,
        data: Map<String, Any>
    ) {
        val ref = db.collection(collection).document(userId)
            .collection(subCollection).document(docId)
        // Task<Void>.await() retorna null (Void), então usamos um marcador Boolean
        // para distinguir "completou" de "timeout"
        val completed = withTimeoutOrNull(15_000) {
            ref.set(data).await()
            true
        }
        if (completed == null) {
            throw Exception("Falha ao salvar no servidor (timeout 15s). Verifique sua conexão.")
        }
        Log.d(TAG, "Firestore write OK: $subCollection/$docId")
    }

    /**
     * Verifica intervalo mínimo. Chamado de DENTRO de withFirestoreNetwork,
     * então a rede já está habilitada — faz leitura direta sem toggle.
     */
    private suspend fun checkMinIntervalOnline(type: BackupType): Exception? {
        val info = try {
            val docId = type.name.lowercase()
            val doc = db.collection("backups").document(userId)
                .collection("metadata").document(docId)
                .get(Source.SERVER).await()
            if (doc.exists()) BackupMetadata.fromMap(doc.data ?: emptyMap()) else null
        } catch (_: Exception) { null }

        if (info != null) {
            val elapsed = System.currentTimeMillis() - info.createdAt.toDate().time
            if (elapsed < MIN_INTERVAL_MS) {
                val minutesLeft = ((MIN_INTERVAL_MS - elapsed) / 60000).toInt() + 1
                return Exception("Aguarde $minutesLeft minuto(s) antes do próximo backup.")
            }
        }
        return null
    }
}
