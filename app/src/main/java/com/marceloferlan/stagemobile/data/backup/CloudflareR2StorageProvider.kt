package com.marceloferlan.stagemobile.data.backup

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * BackupStorageProvider que usa Cloudflare R2 via Worker.
 *
 * O Worker faz o bridge entre o app e o R2 bucket:
 * - App envia Firebase ID Token no header Authorization
 * - Worker valida o token e acessa o R2 via binding (sem credenciais no app)
 * - Upload/download feito por streaming HTTP (sem limite de tamanho do SDK)
 *
 * Worker URL deve ser configurado após deploy do cloudflare-worker/.
 */
class CloudflareR2StorageProvider(
    private val workerUrl: String = WORKER_URL
) : BackupStorageProvider {

    companion object {
        private const val TAG = "CloudflareR2"

        // URL do Worker — atualizar após deploy com `npx wrangler deploy`
        // O Worker é deployado em: https://stagemobile-backup-worker.<your-subdomain>.workers.dev
        const val WORKER_URL = "https://stagemobile-backup-worker.marceloferlan.workers.dev"

        private const val CONNECT_TIMEOUT = 30_000  // 30s
        private const val READ_TIMEOUT = 300_000     // 5min (SF2 grandes)
    }

    /**
     * Obtém o Firebase ID Token do usuário autenticado.
     */
    private suspend fun getAuthToken(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("User not authenticated")
        return user.getIdToken(false).await().token
            ?: throw IllegalStateException("Failed to get ID token")
    }

    override suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        onProgress: ((Float) -> Unit)?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken()
            val url = URL("$workerUrl/upload?key=$remotePath")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Content-Length", localFile.length().toString())
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                // Disable chunked — send Content-Length for progress tracking
                setFixedLengthStreamingMode(localFile.length())
            }

            // Stream upload with progress
            val totalBytes = localFile.length()
            var uploadedBytes = 0L
            val buffer = ByteArray(65536) // 64KB chunks

            FileInputStream(localFile).use { input ->
                conn.outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        uploadedBytes += bytesRead
                        onProgress?.invoke(uploadedBytes.toFloat() / totalBytes)
                    }
                    output.flush()
                }
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Log.i(TAG, "Uploaded ${localFile.name} → $remotePath (${localFile.length()} bytes)")
                Result.success(remotePath)
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                Log.e(TAG, "Upload failed: $error")
                Result.failure(Exception("Upload failed: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error for ${localFile.name}: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(
        remotePath: String,
        localDestination: File,
        onProgress: ((Float) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken()
            val url = URL("$workerUrl/download?key=$remotePath")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                return@withContext Result.failure(Exception("Download failed: $error"))
            }

            val totalBytes = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            var downloadedBytes = 0L
            val buffer = ByteArray(65536)

            conn.inputStream.use { input ->
                FileOutputStream(localDestination).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress?.invoke(downloadedBytes.toFloat() / totalBytes)
                        }
                    }
                }
            }

            Log.i(TAG, "Downloaded $remotePath → ${localDestination.name} (${localDestination.length()} bytes)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Download error for $remotePath: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken()
            val url = URL("$workerUrl/delete?key=$remotePath")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = CONNECT_TIMEOUT
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Log.i(TAG, "Deleted $remotePath")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Delete failed: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun listFiles(remotePath: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken()
            // Ensure prefix ends with /
            val prefix = if (remotePath.endsWith("/")) remotePath else "$remotePath/"
            val url = URL("$workerUrl/list?prefix=$prefix")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = CONNECT_TIMEOUT
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("List failed: HTTP $responseCode"))
            }

            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val filesArray = json.getJSONArray("files")
            val names = (0 until filesArray.length()).map { i ->
                filesArray.getJSONObject(i).getString("name")
            }.filter { it.isNotEmpty() }

            Log.i(TAG, "Listed $prefix: ${names.size} files")
            Result.success(names)
        } catch (e: Exception) {
            Log.e(TAG, "List error: ${e.message}")
            Result.failure(e)
        }
    }
}
