package com.marceloferlan.stagemobile.data.backup

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Implementação do BackupStorageProvider usando Firebase Cloud Storage.
 * Backend inicial — pode ser substituído por S3, servidor próprio, etc.
 */
class FirebaseStorageProvider : BackupStorageProvider {

    companion object {
        private const val TAG = "FirebaseStorage"
    }

    private val storage = FirebaseStorage.getInstance()

    override suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        onProgress: ((Float) -> Unit)?
    ): Result<String> = try {
        val ref = storage.reference.child(remotePath)
        val uploadTask = ref.putFile(Uri.fromFile(localFile))

        // Progress tracking
        uploadTask.addOnProgressListener { snapshot ->
            val progress = snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount.toFloat()
            onProgress?.invoke(progress)
        }

        uploadTask.await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Log.i(TAG, "Uploaded ${localFile.name} → $remotePath (${localFile.length()} bytes)")
        Result.success(downloadUrl)
    } catch (e: Exception) {
        Log.e(TAG, "Upload failed for ${localFile.name}: ${e.message}")
        Result.failure(e)
    }

    override suspend fun downloadFile(
        remotePath: String,
        localDestination: File,
        onProgress: ((Float) -> Unit)?
    ): Result<Unit> = try {
        val ref = storage.reference.child(remotePath)
        val downloadTask = ref.getFile(localDestination)

        downloadTask.addOnProgressListener { snapshot ->
            val progress = snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount.toFloat()
            onProgress?.invoke(progress)
        }

        downloadTask.await()
        Log.i(TAG, "Downloaded $remotePath → ${localDestination.name} (${localDestination.length()} bytes)")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Download failed for $remotePath: ${e.message}")
        Result.failure(e)
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> = try {
        storage.reference.child(remotePath).delete().await()
        Log.i(TAG, "Deleted $remotePath")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Delete failed for $remotePath: ${e.message}")
        Result.failure(e)
    }

    override suspend fun listFiles(remotePath: String): Result<List<String>> = try {
        val result = storage.reference.child(remotePath).listAll().await()
        val names = result.items.map { it.name }
        Log.i(TAG, "Listed $remotePath: ${names.size} files")
        Result.success(names)
    } catch (e: Exception) {
        Log.e(TAG, "List failed for $remotePath: ${e.message}")
        Result.failure(e)
    }
}
