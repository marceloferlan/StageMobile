package com.marceloferlan.stagemobile.data.backup

import java.io.File

/**
 * Interface abstrata para o storage de arquivos de backup (SF2).
 * Permite trocar o backend (Firebase Cloud Storage, S3, servidor próprio)
 * sem alterar a lógica do BackupRepository.
 */
interface BackupStorageProvider {

    /**
     * Upload de um arquivo para o storage remoto.
     * @param localFile arquivo local a enviar
     * @param remotePath caminho remoto (ex: "backups/{userId}/full/soundfonts/Piano.sf2")
     * @param onProgress callback com progresso 0.0 a 1.0
     * @return URL ou path remoto do arquivo salvo
     */
    suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        onProgress: ((Float) -> Unit)? = null
    ): Result<String>

    /**
     * Download de um arquivo do storage remoto.
     * @param remotePath caminho remoto do arquivo
     * @param localDestination arquivo local de destino
     * @param onProgress callback com progresso 0.0 a 1.0
     */
    suspend fun downloadFile(
        remotePath: String,
        localDestination: File,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Unit>

    /**
     * Deleta um arquivo ou diretório no storage remoto.
     * @param remotePath caminho remoto a deletar
     */
    suspend fun deleteFile(remotePath: String): Result<Unit>

    /**
     * Lista arquivos em um diretório remoto.
     * @param remotePath diretório remoto
     * @return lista de nomes de arquivos
     */
    suspend fun listFiles(remotePath: String): Result<List<String>>
}
