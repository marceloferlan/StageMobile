package com.marceloferlan.stagemobile.data.backup

import com.google.firebase.Timestamp

enum class BackupType { CONFIG, FULL }
enum class BackupStatus { PENDING, UPLOADING, COMPLETE, FAILED }

data class BackupMetadata(
    val id: String = "",
    val userId: String = "",
    val type: BackupType = BackupType.CONFIG,
    val createdAt: Timestamp = Timestamp.now(),
    val deviceName: String = "",
    val appVersion: String = "",
    val configSizeBytes: Long = 0,
    val sf2SizeBytes: Long = 0,
    val sf2FileCount: Int = 0,
    val status: BackupStatus = BackupStatus.PENDING
) {
    // Firestore requires a no-arg constructor for deserialization
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "type" to type.name,
        "createdAt" to createdAt,
        "deviceName" to deviceName,
        "appVersion" to appVersion,
        "configSizeBytes" to configSizeBytes,
        "sf2SizeBytes" to sf2SizeBytes,
        "sf2FileCount" to sf2FileCount,
        "status" to status.name
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): BackupMetadata = BackupMetadata(
            id = map["id"] as? String ?: "",
            userId = map["userId"] as? String ?: "",
            type = try { BackupType.valueOf(map["type"] as? String ?: "CONFIG") } catch (_: Exception) { BackupType.CONFIG },
            createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now(),
            deviceName = map["deviceName"] as? String ?: "",
            appVersion = map["appVersion"] as? String ?: "",
            configSizeBytes = (map["configSizeBytes"] as? Number)?.toLong() ?: 0,
            sf2SizeBytes = (map["sf2SizeBytes"] as? Number)?.toLong() ?: 0,
            sf2FileCount = (map["sf2FileCount"] as? Number)?.toInt() ?: 0,
            status = try { BackupStatus.valueOf(map["status"] as? String ?: "COMPLETE") } catch (_: Exception) { BackupStatus.COMPLETE }
        )
    }
}
