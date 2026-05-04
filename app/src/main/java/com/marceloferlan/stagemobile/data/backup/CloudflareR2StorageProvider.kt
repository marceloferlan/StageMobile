package com.marceloferlan.stagemobile.data.backup

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * BackupStorageProvider que usa Cloudflare R2 via Worker.
 *
 * O Worker faz o bridge entre o app e o R2 bucket:
 * - App envia Firebase ID Token no header Authorization
 * - Worker valida o token e acessa o R2 via binding (sem credenciais no app)
 * - Arquivos <90MB: upload direto (PUT /upload)
 * - Arquivos >=90MB: multipart upload (POST /multipart/create → PUT /multipart/part × N → POST /multipart/complete)
 */
class CloudflareR2StorageProvider(
    private val workerUrl: String = WORKER_URL
) : BackupStorageProvider {

    companion object {
        private const val TAG = "CloudflareR2"

        const val WORKER_URL = "https://stagemobile-backup-worker.ferlan.workers.dev"

        private const val CONNECT_TIMEOUT = 30_000   // 30s
        private const val READ_TIMEOUT = 300_000      // 5min (SF2 grandes)

        // Limite seguro para upload direto (Cloudflare Workers aceita ~100MB)
        private const val DIRECT_UPLOAD_LIMIT = 90L * 1024 * 1024  // 90MB

        // Tamanho de cada parte no multipart (R2 exige mínimo 5MB, exceto última parte)
        private const val PART_SIZE = 50L * 1024 * 1024  // 50MB
    }

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
            if (localFile.length() >= DIRECT_UPLOAD_LIMIT) {
                uploadFileMultipart(localFile, remotePath, onProgress)
            } else {
                uploadFileDirect(localFile, remotePath, onProgress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error for ${localFile.name}: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Upload direto para arquivos <90MB.
     */
    private suspend fun uploadFileDirect(
        localFile: File,
        remotePath: String,
        onProgress: ((Float) -> Unit)?
    ): Result<String> {
        val token = getAuthToken()
        val encodedKey = URLEncoder.encode(remotePath, "UTF-8")
        val url = URL("$workerUrl/upload?key=$encodedKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("Content-Length", localFile.length().toString())
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setFixedLengthStreamingMode(localFile.length())
        }

        val totalBytes = localFile.length()
        var uploadedBytes = 0L
        val buffer = ByteArray(65536)

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
        return if (responseCode in 200..299) {
            Log.i(TAG, "Uploaded ${localFile.name} → $remotePath (${localFile.length()} bytes)")
            Result.success(remotePath)
        } else {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
            Log.e(TAG, "Upload failed: $error")
            Result.failure(Exception("Upload failed: $error"))
        }
    }

    /**
     * Multipart upload para arquivos >=90MB.
     * Divide em partes de 50MB e envia cada uma separadamente.
     */
    private suspend fun uploadFileMultipart(
        localFile: File,
        remotePath: String,
        onProgress: ((Float) -> Unit)?
    ): Result<String> {
        val token = getAuthToken()
        val encodedKey = URLEncoder.encode(remotePath, "UTF-8")
        val totalBytes = localFile.length()
        val totalParts = ((totalBytes + PART_SIZE - 1) / PART_SIZE).toInt()

        Log.i(TAG, "Multipart upload: ${localFile.name} (${totalBytes / 1_048_576}MB, $totalParts parts)")

        // 1. Criar multipart upload
        val createUrl = URL("$workerUrl/multipart/create?key=$encodedKey")
        val createConn = (createUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
        }

        val createCode = createConn.responseCode
        if (createCode !in 200..299) {
            val error = createConn.errorStream?.bufferedReader()?.readText() ?: "HTTP $createCode"
            return Result.failure(Exception("Multipart create failed: $error"))
        }
        val createResponse = createConn.inputStream.bufferedReader().readText()
        val uploadId = JSONObject(createResponse).getString("uploadId")
        Log.d(TAG, "Multipart created: uploadId=$uploadId")

        // 2. Upload de cada parte
        val uploadedParts = JSONArray()
        var uploadedTotal = 0L

        FileInputStream(localFile).use { input ->
            for (partNumber in 1..totalParts) {
                val remaining = totalBytes - uploadedTotal
                val partSize = minOf(PART_SIZE, remaining)
                val partBytes = ByteArray(partSize.toInt())

                // Ler a parte inteira do arquivo
                var offset = 0
                while (offset < partSize) {
                    val read = input.read(partBytes, offset, (partSize - offset).toInt())
                    if (read == -1) break
                    offset += read
                }

                // Enviar a parte
                val partUrl = URL("$workerUrl/multipart/part?key=$encodedKey&uploadId=$uploadId&partNumber=$partNumber")
                val partConn = (partUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setRequestProperty("Content-Length", offset.toString())
                    doOutput = true
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    setFixedLengthStreamingMode(offset.toLong())
                }

                partConn.outputStream.use { output ->
                    output.write(partBytes, 0, offset)
                    output.flush()
                }

                val partResponseCode = partConn.responseCode
                if (partResponseCode !in 200..299) {
                    val error = partConn.errorStream?.bufferedReader()?.readText() ?: "HTTP $partResponseCode"
                    // Abortar upload
                    abortMultipart(token, encodedKey, uploadId)
                    return Result.failure(Exception("Part $partNumber upload failed: $error"))
                }

                val partResponse = partConn.inputStream.bufferedReader().readText()
                val etag = JSONObject(partResponse).getString("etag")

                uploadedParts.put(JSONObject().apply {
                    put("partNumber", partNumber)
                    put("etag", etag)
                })

                uploadedTotal += offset
                onProgress?.invoke(uploadedTotal.toFloat() / totalBytes)
                Log.d(TAG, "Part $partNumber/$totalParts uploaded (${offset / 1024}KB)")
            }
        }

        // 3. Completar multipart upload
        val completeUrl = URL("$workerUrl/multipart/complete?key=$encodedKey&uploadId=$uploadId")
        val completeConn = (completeUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
        }

        completeConn.outputStream.use { output ->
            output.write(uploadedParts.toString().toByteArray())
            output.flush()
        }

        val completeCode = completeConn.responseCode
        return if (completeCode in 200..299) {
            Log.i(TAG, "Multipart upload complete: ${localFile.name} (${totalBytes / 1_048_576}MB, $totalParts parts)")
            Result.success(remotePath)
        } else {
            val error = completeConn.errorStream?.bufferedReader()?.readText() ?: "HTTP $completeCode"
            Log.e(TAG, "Multipart complete failed: $error")
            Result.failure(Exception("Multipart complete failed: $error"))
        }
    }

    private fun abortMultipart(token: String, encodedKey: String, uploadId: String) {
        try {
            val abortUrl = URL("$workerUrl/multipart/abort?key=$encodedKey&uploadId=$uploadId")
            val conn = (abortUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = CONNECT_TIMEOUT
            }
            conn.responseCode // trigger request
            Log.d(TAG, "Multipart upload aborted")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abort multipart: ${e.message}")
        }
    }

    override suspend fun downloadFile(
        remotePath: String,
        localDestination: File,
        onProgress: ((Float) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken()
            val encodedKey = URLEncoder.encode(remotePath, "UTF-8")
            val url = URL("$workerUrl/download?key=$encodedKey")
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
            val encodedKey = URLEncoder.encode(remotePath, "UTF-8")
            val url = URL("$workerUrl/delete?key=$encodedKey")
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
            val prefix = if (remotePath.endsWith("/")) remotePath else "$remotePath/"
            val encodedPrefix = URLEncoder.encode(prefix, "UTF-8")
            val url = URL("$workerUrl/list?prefix=$encodedPrefix")
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
