package com.marceloferlan.stagemobile.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await

/**
 * ConfigSnapshot — Representa uma "fotografia" completa das configurações do app.
 *
 * Serializa/deserializa:
 * - SharedPreferences "stage_mobile_settings" (config do motor, MIDI learn, etc)
 * - SharedPreferences "stage_mobile_sets" (Set Stages, 150 slots)
 * - Coleção Firestore "soundfonts" (metadados dos SF2)
 */
data class ConfigSnapshot(
    val version: Int = 1,
    val settings: Map<String, Any?> = emptyMap(),
    val setStages: Map<String, Any?> = emptyMap(),
    val soundFontMetadata: List<Map<String, Any?>> = emptyList(),
    val exportedAt: String = "",
    val deviceModel: String = "",
    val appVersion: String = ""
) {
    companion object {
        private const val TAG = "ConfigSnapshot"
        private val gson = Gson()

        /**
         * Captura o estado atual de todas as configurações do app.
         */
        suspend fun capture(context: Context, appVersion: String): ConfigSnapshot {
            val settingsPrefs = context.getSharedPreferences("stage_mobile_settings", Context.MODE_PRIVATE)
            val setsPrefs = context.getSharedPreferences("stage_mobile_sets", Context.MODE_PRIVATE)

            // 1. Serializar SharedPreferences (converte Set<String> pra List pra compatibilidade JSON)
            val settingsMap = serializePrefs(settingsPrefs)
            val setsMap = serializePrefs(setsPrefs)

            // 2. Buscar metadados SF2 do Firestore (cache-first pra funcionar offline)
            val sf2Metadata = try {
                val snapshot = try {
                    FirebaseFirestore.getInstance()
                        .collection("soundfonts")
                        .get(com.google.firebase.firestore.Source.CACHE)
                        .await()
                } catch (_: Exception) {
                    FirebaseFirestore.getInstance()
                        .collection("soundfonts")
                        .get(com.google.firebase.firestore.Source.SERVER)
                        .await()
                }
                snapshot.documents.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["_docId"] = doc.id
                    data
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch SF2 metadata from Firestore: ${e.message}")
                emptyList()
            }

            return ConfigSnapshot(
                version = 1,
                settings = settingsMap,
                setStages = setsMap,
                soundFontMetadata = sf2Metadata,
                exportedAt = java.time.Instant.now().toString(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                appVersion = appVersion
            )
        }

        /**
         * Restaura um snapshot nas SharedPreferences e Firestore.
         */
        suspend fun restore(context: Context, snapshot: ConfigSnapshot) {
            val settingsPrefs = context.getSharedPreferences("stage_mobile_settings", Context.MODE_PRIVATE)
            val setsPrefs = context.getSharedPreferences("stage_mobile_sets", Context.MODE_PRIVATE)

            // 1. Restaurar SharedPreferences settings
            restorePrefs(settingsPrefs, snapshot.settings)
            Log.i(TAG, "Settings restored (${snapshot.settings.size} keys)")

            // 2. Restaurar SharedPreferences sets
            restorePrefs(setsPrefs, snapshot.setStages)
            Log.i(TAG, "Set Stages restored (${snapshot.setStages.size} keys)")

            // 3. Restaurar metadados SF2 no Firestore
            if (snapshot.soundFontMetadata.isNotEmpty()) {
                val db = FirebaseFirestore.getInstance()
                val collection = db.collection("soundfonts")

                // Limpa metadados atuais
                try {
                    val existing = collection.get().await()
                    for (doc in existing.documents) {
                        doc.reference.delete().await()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear existing SF2 metadata: ${e.message}")
                }

                // Insere os do snapshot
                for (meta in snapshot.soundFontMetadata) {
                    val docId = meta["_docId"] as? String ?: continue
                    val data = meta.filterKeys { it != "_docId" }
                    try {
                        collection.document(docId).set(data).await()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore SF2 metadata '$docId': ${e.message}")
                    }
                }
                Log.i(TAG, "SF2 metadata restored (${snapshot.soundFontMetadata.size} entries)")
            }
        }

        /**
         * Serializa um SharedPreferences inteiro para Map<String, Any?>.
         * Converte Set<String> para List<String> pra compatibilidade com Gson/Firestore.
         */
        private fun serializePrefs(prefs: SharedPreferences): Map<String, Any?> {
            val all = prefs.all
            return all.mapValues { (_, value) ->
                when (value) {
                    is Set<*> -> (value as? Set<String>)?.toList() ?: value
                    else -> value
                }
            }
        }

        /**
         * Restaura um Map serializado de volta num SharedPreferences.
         * Detecta o tipo de cada valor e usa o put* correspondente.
         */
        private fun restorePrefs(prefs: SharedPreferences, data: Map<String, Any?>) {
            val editor = prefs.edit()
            editor.clear()

            for ((key, value) in data) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                    is Long -> editor.putLong(key, value)
                    is Int -> editor.putInt(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is Float -> editor.putFloat(key, value)
                    is List<*> -> {
                        // List<String> → Set<String> (SharedPreferences usa StringSet)
                        val stringSet = value.filterIsInstance<String>().toSet()
                        editor.putStringSet(key, stringSet)
                    }
                    is Number -> {
                        // Gson pode deserializar ints como doubles
                        if (value.toDouble() == value.toLong().toDouble()) {
                            editor.putInt(key, value.toInt())
                        } else {
                            editor.putFloat(key, value.toFloat())
                        }
                    }
                    null -> { /* skip nulls */ }
                    else -> Log.w(TAG, "Unknown type for key '$key': ${value::class.simpleName}")
                }
            }

            editor.apply()
        }

        /**
         * Converte o snapshot pra Map pra armazenar no Firestore.
         */
        fun toMap(snapshot: ConfigSnapshot): Map<String, Any> {
            return mapOf(
                "version" to snapshot.version,
                "settings" to gson.toJson(snapshot.settings),
                "setStages" to gson.toJson(snapshot.setStages),
                "soundFontMetadata" to gson.toJson(snapshot.soundFontMetadata),
                "exportedAt" to snapshot.exportedAt,
                "deviceModel" to snapshot.deviceModel,
                "appVersion" to snapshot.appVersion
            )
        }

        /**
         * Reconstrói o snapshot a partir de um Map lido do Firestore.
         */
        fun fromMap(map: Map<String, Any?>): ConfigSnapshot {
            val settingsType = object : TypeToken<Map<String, Any?>>() {}.type
            val sf2Type = object : TypeToken<List<Map<String, Any?>>>() {}.type

            return ConfigSnapshot(
                version = (map["version"] as? Number)?.toInt() ?: 1,
                settings = try { gson.fromJson(map["settings"] as? String ?: "{}", settingsType) } catch (_: Exception) { emptyMap() },
                setStages = try { gson.fromJson(map["setStages"] as? String ?: "{}", settingsType) } catch (_: Exception) { emptyMap() },
                soundFontMetadata = try { gson.fromJson(map["soundFontMetadata"] as? String ?: "[]", sf2Type) } catch (_: Exception) { emptyList() },
                exportedAt = map["exportedAt"] as? String ?: "",
                deviceModel = map["deviceModel"] as? String ?: "",
                appVersion = map["appVersion"] as? String ?: ""
            )
        }
    }
}
