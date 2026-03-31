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
    suspend fun importSoundFont(uri: Uri, fileName: String, tags: List<String>): Result<Unit> {
        return try {
            val destinationFile = File(soundfontsDir, fileName)
            
            // Copia o arquivo para o armazenamento interno
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Não foi possível abrir o arquivo original.")

            // Salva metadados no Firestore
            val metadata = SoundFontMetadata(
                fileName = fileName,
                tags = tags,
                addedDate = System.currentTimeMillis()
            )
            
            // Usamos add() para deixar o Firestore gerar um ID único
            soundFontsCollection.add(metadata)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SoundFontRepository", "Erro ao importar SF2: ${e.message}")
            Result.failure(e)
        }
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
                // Atualiza Firestore
                soundFontsCollection.document(metadata.id).update("fileName", newName)
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
