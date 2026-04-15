package com.marceloferlan.stagemobile.audio.engine

import android.content.Context
import android.util.Log
import com.marceloferlan.stagemobile.domain.model.Sf2Preset
import java.io.File

class FluidSynthEngine(private val context: Context) : AudioEngine {
    private val TAG = "FluidSynthEngine"
    override var isInitialized = false
        private set

    companion object {
        init {
            try {
                System.loadLibrary("synthmodule")
                Log.i("FluidSynthEngine", "Native library loaded successfully")
            } catch (e: Exception) {
                Log.e("FluidSynthEngine", "Failed to load native library: ${e.message}")
            }
        }
    }

    private external fun nativeInit(sampleRate: Int, bufferSize: Int, deviceId: Int): Int
    private external fun nativeRegisterSpBridge(): Boolean
    private external fun nativeLoadSf2(path: String): Int
    private external fun nativeUnloadSf2(sfId: Int): Boolean
    private external fun nativeNoteOn(channel: Int, key: Int, velocity: Int)
    private external fun nativeNoteOff(channel: Int, key: Int)
    private external fun nativeSetVolume(channel: Int, volumeDb: Float)
    private external fun nativeControlChange(channel: Int, controller: Int, value: Int)
    private external fun nativePitchBend(channel: Int, value: Int)
    private external fun nativeProgramChange(channel: Int, bank: Int, program: Int)
    private external fun nativeProgramSelect(channel: Int, sfId: Int, bank: Int, program: Int): Boolean
    private external fun nativeDestroy()
    private external fun nativeGetChannelLevels(output: FloatArray)
    private external fun nativeGetPresets(sfId: Int): Array<String>?
    private external fun nativeSetInterpolation(method: Int)
    private external fun nativeSetPolyphony(maxVoices: Int)
    private external fun nativeWarmUpChannel(channel: Int)
    private external fun nativeGetAudioStats(): FloatArray?

    // --- DSP Effects ---
    private external fun nativeSetChannelEqCutoff(channel: Int, cutoff: Float)
    private external fun nativeSetChannelSendLevel(channel: Int, level: Float)
    private external fun nativeAddEffect(channel: Int, type: Int)
    private external fun nativeRemoveEffect(channel: Int, index: Int)
    private external fun nativeClearEffects(channel: Int)
    private external fun nativeSetEffectParam(channel: Int, index: Int, paramId: Int, value: Float)
    private external fun nativeSetEffectEnabled(channel: Int, index: Int, enabled: Boolean)
    private external fun nativeGetEffectMeters(channel: Int, index: Int, output: FloatArray): Boolean
    private external fun nativeSetMasterLimiter(enabled: Boolean)
    private external fun nativeSetDspMasterBypass(enabled: Boolean)
    private external fun nativeIsStreamDead(): Boolean
    private external fun nativeResetStreamDead()
    private external fun nativePanic()

    fun setChannelEqCutoff(channel: Int, cutoff: Float) {
        if (isInitialized) nativeSetChannelEqCutoff(channel, cutoff)
    }

    fun setChannelSendLevel(channel: Int, level: Float) {
        if (isInitialized) nativeSetChannelSendLevel(channel, level)
    }

    override fun addEffect(channel: Int, type: Int) {
        if (isInitialized) nativeAddEffect(channel, type)
    }

    override fun removeEffect(channel: Int, index: Int) {
        if (isInitialized) nativeRemoveEffect(channel, index)
    }

    override fun clearEffects(channel: Int) {
        if (isInitialized) nativeClearEffects(channel)
    }

    override fun setEffectParam(channel: Int, index: Int, paramId: Int, value: Float) {
        if (isInitialized) nativeSetEffectParam(channel, index, paramId, value)
    }

    override fun setEffectEnabled(channel: Int, index: Int, enabled: Boolean) {
        if (isInitialized) nativeSetEffectEnabled(channel, index, enabled)
    }

    override fun getEffectMeters(channel: Int, index: Int, output: FloatArray): Boolean {
        return if (isInitialized) nativeGetEffectMeters(channel, index, output) else false
    }


    override fun initialize(sampleRate: Int, bufferSize: Int, deviceId: Int) {
        if (!isInitialized) {
            val result = nativeInit(sampleRate, bufferSize, deviceId)
            isInitialized = (result == 0) // 0 is oboe::Result::OK
            if (isInitialized) {
                Log.i(TAG, "FluidSynth initialized @ $sampleRate Hz (Oboe OK)")
                val bridgeRegistered = nativeRegisterSpBridge()
                Log.i(TAG, "Superpowered Bridge registered: $bridgeRegistered")
            } else {
                Log.e(TAG, "FluidSynth initialization FAILED with Oboe result: $result")
            }
        }
    }

    fun forceInitializedState() {
        if (!isInitialized) {
            isInitialized = true
            Log.i(TAG, "FluidSynthEngine forced to initialized state (Superpowered Bypass Mode)")
        }
    }

    override fun loadSoundFont(path: String): Int {
        return if (isInitialized) nativeLoadSf2(path) else -1
    }

    override fun unloadSoundFont(sfId: Int): Boolean {
        return if (isInitialized) nativeUnloadSf2(sfId) else false
    }

    override fun noteOn(channel: Int, key: Int, velocity: Int) {
        if (isInitialized) nativeNoteOn(channel, key, velocity)
    }

    override fun noteOff(channel: Int, key: Int) {
        if (isInitialized) nativeNoteOff(channel, key)
    }

    override fun setVolume(channel: Int, volumeDb: Float) {
        if (isInitialized) nativeSetVolume(channel, volumeDb)
    }

    override fun controlChange(channel: Int, controller: Int, value: Int) {
        if (isInitialized) nativeControlChange(channel, controller, value)
    }

    override fun pitchBend(channel: Int, value: Int) {
        if (isInitialized) nativePitchBend(channel, value)
    }

    override fun programChange(channel: Int, bank: Int, program: Int) {
        if (isInitialized) nativeProgramChange(channel, bank, program)
    }

    override fun programSelect(channelId: Int, sfId: Int, bank: Int, program: Int) {
        if (isInitialized) nativeProgramSelect(channelId, sfId, bank, program)
    }

    override fun getChannelLevels(output: FloatArray) {
        if (isInitialized) nativeGetChannelLevels(output)
    }

    override fun getPresets(sfId: Int): List<Sf2Preset> {
        val nativePresets = if (isInitialized) nativeGetPresets(sfId) else null
        return nativePresets?.mapNotNull { presetStr ->
            // Format: "bank|program|name"
            val parts = presetStr.split("|")
            if (parts.size >= 3) {
                val bank = parts[0].toIntOrNull() ?: 0
                val prog = parts[1].toIntOrNull() ?: 0
                val name = parts[2]
                Sf2Preset(bank, prog, name)
            } else if (parts.size == 2) {
                val bank = parts[0].toIntOrNull() ?: 0
                val prog = parts[1].toIntOrNull() ?: 0
                Sf2Preset(bank, prog, "Preset $prog")
            } else null
        } ?: emptyList()
    }

    override fun setInterpolation(method: Int) {
        if (isInitialized) nativeSetInterpolation(method)
    }

    override fun setPolyphony(maxVoices: Int) {
        if (isInitialized) nativeSetPolyphony(maxVoices)
    }

    override fun setMasterLimiter(enabled: Boolean) {
        if (isInitialized) nativeSetMasterLimiter(enabled)
    }

    override fun setDspMasterBypass(enabled: Boolean) {
        if (isInitialized) nativeSetDspMasterBypass(enabled)
    }

    override fun panic() {
        if (isInitialized) nativePanic()
    }

    fun isStreamDead(): Boolean {
        return if (isInitialized) nativeIsStreamDead() else false
    }

    fun resetStreamDead() {
        if (isInitialized) nativeResetStreamDead()
    }

    fun warmUpChannel(channel: Int) {
        if (isInitialized) nativeWarmUpChannel(channel)
    }

    override fun destroy() {
        if (isInitialized) {
            nativeDestroy()
            isInitialized = false
            Log.i(TAG, "FluidSynth destroyed (Oboe stopped)")
        }
    }

    override fun getAudioStats(): FloatArray? {
        return if (isInitialized) nativeGetAudioStats() else null
    }
}