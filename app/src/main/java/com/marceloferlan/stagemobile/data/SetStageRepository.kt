package com.marceloferlan.stagemobile.data

import android.content.Context
import android.content.SharedPreferences
import com.marceloferlan.stagemobile.domain.model.SetStage
import com.google.gson.Gson

class SetStageRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("stage_mobile_sets", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveSetStage(setStage: SetStage) {
        val key = getKey(setStage.bankId, setStage.slotId)
        val json = gson.toJson(setStage)
        prefs.edit().putString(key, json).apply()
    }

    fun loadSetStage(bankId: Int, slotId: Int): SetStage? {
        val key = getKey(bankId, slotId)
        val json = prefs.getString(key, null) ?: return null
        return try {
            gson.fromJson(json, SetStage::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteSetStage(bankId: Int, slotId: Int) {
        val key = getKey(bankId, slotId)
        prefs.edit().remove(key).apply()
    }

    fun getSetStagesForBank(bankId: Int): Map<Int, SetStage> {
        val map = mutableMapOf<Int, SetStage>()
        for (slotId in 1..15) {
            val setStage = loadSetStage(bankId, slotId)
            if (setStage != null) {
                map[slotId] = setStage
            }
        }
        return map
    }

    fun saveBankName(bankId: Int, name: String) {
        prefs.edit().putString("bank_name_$bankId", name).apply()
    }

    fun getBankName(bankId: Int): String? {
        return prefs.getString("bank_name_$bankId", null)
    }

    fun findNextAvailableSlot(): Pair<Int, Int>? {
        for (bankId in 1..10) {
            for (slotId in 1..15) {
                val key = getKey(bankId, slotId)
                if (!prefs.contains(key)) {
                    return Pair(bankId, slotId)
                }
            }
        }
        return null
    }

    private fun getKey(bankId: Int, slotId: Int): String {
        return "set_stage_${bankId}_${slotId}"
    }
}
