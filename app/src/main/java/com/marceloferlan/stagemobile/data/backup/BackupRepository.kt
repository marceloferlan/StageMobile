package com.marceloferlan.stagemobile.data.backup

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

/**
 * BackupRepository — Orquestra criação, restauração e gerenciamento de backups.
 *
 * Estrutura no Firestore:
 *   backups/{userId}/config  → ConfigSnapshot (JSON serializado)
 *   backups/{userId}/full    → ConfigSnapshot + metadata dos SF2
 *   backups/{userId}/metadata/config → BackupMetadata
 *   backups/{userId}/metadata/full   → BackupMetadata
 *
 * Estrutura no Cloud Storage (backup full):
 *   backups/{userId}/full/soundfonts/{fileName}.sf2
 */
class BackupRepository(
    private val context: Context,
    private val storageProvider: BackupStorageProvider = CloudflareR2StorageProvider()
) {
    companion object {
        private const val TAG = "BackupRepository"
        private const val MIN_INTERVAL_MS = 3600_000L // 1 hora entre backups
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

    /**
     * Cria um backup de configurações (sobrescreve o anterior se existir).
     */
    suspend fun createConfigBackup(onProgress: ((String) -> Unit)? = null): Result<BackupMetadata> = try {
        // Verifica intervalo mínimo
        checkMinInterval(BackupType.CONFIG)?.let { return Result.failure(it) }

        onProgress?.invoke("Capturando configurações...")
        val snapshot = ConfigSnapshot.capture(context, appVersion)
        val snapshotMap = ConfigSnapshot.toMap(snapshot)
        val configJson = com.google.gson.Gson().toJson(snapshotMap)

        onProgress?.invoke("Salvando na nuvem...")
        // Salva o snapshot como documento único (sobrescreve)
        db.collection("backups").document(userId)
            .collection("data").document("config")
            .set(snapshotMap)
            .await()

        // Salva metadata
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

        db.collection("backups").document(userId)
            .collection("metadata").document("config")
            .set(metadata.toMap())
            .await()

        Log.i(TAG, "Config backup created: ${metadata.configSizeBytes} bytes")
        Result.success(metadata)
    } catch (e: Exception) {
        Log.e(TAG, "Config backup failed: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Restaura um backup de configurações.
     */
    suspend fun restoreConfigBackup(onProgress: ((String) -> Unit)? = null): Result<Unit> {
        return try {
        onProgress?.invoke("Baixando configurações...")
        val doc = db.collection("backups").document(userId)
            .collection("data").document("config")
            .get()
            .await()

        if (!doc.exists()) {
            return Result.failure(Exception("Nenhum backup de configurações encontrado."))
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

    // ════════════════════════════════════════════════════════════════════
    // FULL BACKUP
    // ════════════════════════════════════════════════════════════════════

    /**
     * Cria um backup completo (config + arquivos SF2).
     */
    suspend fun createFullBackup(onProgress: ((String, Float) -> Unit)? = null): Result<BackupMetadata> = try {
        checkMinInterval(BackupType.FULL)?.let { return Result.failure(it) }

        // 1. Config snapshot
        onProgress?.invoke("Capturando configurações...", 0f)
        val snapshot = ConfigSnapshot.capture(context, appVersion)
        val snapshotMap = ConfigSnapshot.toMap(snapshot)
        val configJson = com.google.gson.Gson().toJson(snapshotMap)

        db.collection("backups").document(userId)
            .collection("data").document("full")
            .set(snapshotMap)
            .await()

        // 2. Upload SF2 files
        val soundfontsDir = File(context.filesDir, "soundfonts")
        val sf2Files = soundfontsDir.listFiles()?.filter { it.extension.equals("sf2", ignoreCase = true) } ?: emptyList()
        var totalSf2Bytes = 0L
        val remotePath = "backups/$userId/full/soundfonts"

        // Limpa SF2s antigos do storage antes de sobrescrever
        onProgress?.invoke("Preparando upload...", 0.05f)
        try {
            val existingFiles = storageProvider.listFiles(remotePath).getOrDefault(emptyList())
            for (file in existingFiles) {
                storageProvider.deleteFile("$remotePath/$file")
            }
        } catch (_: Exception) { /* ignore cleanup errors */ }

        // Upload cada SF2
        for ((index, file) in sf2Files.withIndex()) {
            val fileProgress = index.toFloat() / sf2Files.size.coerceAtLeast(1)
            onProgress?.invoke("Enviando ${file.name}...", 0.1f + fileProgress * 0.85f)

            val result = storageProvider.uploadFile(
                localFile = file,
                remotePath = "$remotePath/${file.name}"
            )

            if (result.isFailure) {
                Log.e(TAG, "Failed to upload ${file.name}: ${result.exceptionOrNull()?.message}")
                // Continua com os demais — não aborta o backup inteiro
            } else {
                totalSf2Bytes += file.length()
            }
        }

        // 3. Salva metadata
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

        db.collection("backups").document(userId)
            .collection("metadata").document("full")
            .set(metadata.toMap())
            .await()

        onProgress?.invoke("Backup completo!", 1f)
        Log.i(TAG, "Full backup created: config=${metadata.configSizeBytes}B, sf2=${totalSf2Bytes}B (${sf2Files.size} files)")
        Result.success(metadata)
    } catch (e: Exception) {
        Log.e(TAG, "Full backup failed: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Restaura um backup completo (config + arquivos SF2).
     */
    suspend fun restoreFullBackup(onProgress: ((String, Float) -> Unit)? = null): Result<Unit> {
        return try {
        onProgress?.invoke("Baixando configurações...", 0f)
        val doc = db.collection("backups").document(userId)
            .collection("data").document("full")
            .get()
            .await()

        if (!doc.exists()) {
            return Result.failure(Exception("Nenhum backup completo encontrado."))
        }

        val snapshot = ConfigSnapshot.fromMap(doc.data ?: emptyMap())
        onProgress?.invoke("Restaurando configurações...", 0.05f)
        ConfigSnapshot.restore(context, snapshot)

        // 2. Download SF2 files
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

    // ════════════════════════════════════════════════════════════════════
    // METADATA & INFO
    // ════════════════════════════════════════════════════════════════════

    /**
     * Retorna o metadata do backup de configurações (se existir).
     */
    suspend fun getConfigBackupInfo(): BackupMetadata? = try {
        val doc = db.collection("backups").document(userId)
            .collection("metadata").document("config")
            .get().await()
        if (doc.exists()) BackupMetadata.fromMap(doc.data ?: emptyMap()) else null
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get config backup info: ${e.message}")
        null
    }

    /**
     * Retorna o metadata do backup completo (se existir).
     */
    suspend fun getFullBackupInfo(): BackupMetadata? = try {
        val doc = db.collection("backups").document(userId)
            .collection("metadata").document("full")
            .get().await()
        if (doc.exists()) BackupMetadata.fromMap(doc.data ?: emptyMap()) else null
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get full backup info: ${e.message}")
        null
    }

    /**
     * Deleta um backup (config ou full).
     */
    suspend fun deleteBackup(type: BackupType): Result<Unit> = try {
        db.collection("backups").document(userId)
            .collection("data").document(type.name.lowercase())
            .delete().await()

        db.collection("backups").document(userId)
            .collection("metadata").document(type.name.lowercase())
            .delete().await()

        if (type == BackupType.FULL) {
            // Limpa SF2s do Cloud Storage
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

    // ════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Verifica se o intervalo mínimo entre backups foi respeitado.
     * Retorna Exception se violado, null se OK.
     */
    private suspend fun checkMinInterval(type: BackupType): Exception? {
        val info = when (type) {
            BackupType.CONFIG -> getConfigBackupInfo()
            BackupType.FULL -> getFullBackupInfo()
        }
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
