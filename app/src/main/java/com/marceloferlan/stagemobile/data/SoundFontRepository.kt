package com.marceloferlan.stagemobile.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.marceloferlan.stagemobile.domain.model.SoundFontMetadata
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import com.google.firebase.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.io.FileOutputStream

class SoundFontRepository(private val context: Context) {
    private val db = Firebase.firestore
    private val soundFontsCollection = db.collection("soundfonts")
    private val soundfontsDir = File(context.filesDir, "soundfonts").apply {
        if (!exists()) mkdirs()
    }

    init {
        // Habilita persistência offline (padrão no Android, mas garantindo)
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        db.firestoreSettings = settings
        
        // Desabilita a rede por padrão para evitar Death-Loop do GMS em tablets de palco.
        // O Firestore funcionará 100% em cache local (sem latência ou travamentos).
        db.disableNetwork()
    }

    /**
     * Retorna a lista de SoundFonts cadastrados no Firestore (com suporte offline).
     */
    fun getSoundFonts(): Flow<List<SoundFontMetadata>> = callbackFlow {
        val listener = soundFontsCollection
            .orderBy("addedDate")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SoundFontRepository", "Error listening to soundfonts", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.toObjects<SoundFontMetadata>()
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Importa um arquivo SF2 do sistema de arquivos para o diretório interno e salva metadados no Firestore.
     */
    suspend fun importSoundFont(uri: Uri, fileName: String, tags: List<String>, onProgress: ((Float) -> Unit)? = null): Result<Unit> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val destinationFile = File(soundfontsDir, fileName)
                
                var totalSize = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex != -1) totalSize = cursor.getLong(sizeIndex)
                    }
                }

                // Copia o arquivo para o armazenamento interno reportando progresso
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(32 * 1024) // Buffer ajustado para 32KB para performance em arquivos grandes
                        var bytesCopied = 0L
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            if (totalSize > 0) {
                                onProgress?.invoke(bytesCopied.toFloat() / totalSize.toFloat())
                            }
                            bytes = input.read(buffer)
                        }
                    }
                } ?: throw Exception("Não foi possível abrir o arquivo original.")

            // Salva metadados no Firestore
            val metadata = SoundFontMetadata(
                fileName = fileName,
                tags = tags,
                addedDate = System.currentTimeMillis()
            )
            
            // Usamos o próprio fileName (ou um hash dele) como DocumentID para garantir UNICIDADE.
            // Isso previne que double-clicks na interface gerem documentos duplicados no banco local offline.
            soundFontsCollection.document(fileName).set(metadata)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SoundFontRepository", "Erro ao importar SF2: ${e.message}")
            Result.failure(e)
        }
        } // Fim de withContext
    }

    /**
     * Remove o SoundFont do Firestore e deleta o arquivo físico.
     */
    suspend fun deleteSoundFont(metadata: SoundFontMetadata): Result<Unit> {
        return try {
            // Remove do Firestore
            soundFontsCollection.document(metadata.id).delete()
            
            // Deleta o arquivo físico
            val file = File(soundfontsDir, metadata.fileName)
            if (file.exists()) {
                file.delete()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SoundFontRepository", "Erro ao deletar SF2: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Verifica se um arquivo com o mesmo nome já existe no diretório interno.
     */
    fun exists(fileName: String): Boolean {
        return File(soundfontsDir, fileName).exists()
    }

    /**
     * Renomeia o arquivo físico e atualiza o nome no Firestore.
     */
    suspend fun renameSoundFont(metadata: SoundFontMetadata, newName: String): Result<Unit> {
        return try {
            val oldFile = File(soundfontsDir, metadata.fileName)
            val newFile = File(soundfontsDir, newName)
            
            if (newFile.exists()) throw Exception("Arquivo com este nome já existe.")
            
            if (oldFile.renameTo(newFile)) {
                // Como o ID do Firestore agora é o fileName, devemos migrar o documento para a nova Chave (ID)
                val newMetadata = metadata.copy(fileName = newName)
                soundFontsCollection.document(newName).set(newMetadata) // Cria com o novo ID
                soundFontsCollection.document(metadata.id).delete() // Remove o ID antigo
                
                Result.success(Unit)
            } else {
                throw Exception("Falha ao renomear arquivo físico.")
            }
        } catch (e: Exception) {
            Log.e("SoundFontRepository", "Erro ao renomear SF2: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Retorna o path absoluto do arquivo para ser usado pelo motor de áudio.
     */
    fun getFilePath(fileName: String): String {
        return File(soundfontsDir, fileName).absolutePath
    }
}
