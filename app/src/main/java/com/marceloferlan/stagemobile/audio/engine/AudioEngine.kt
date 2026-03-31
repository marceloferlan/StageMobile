package com.marceloferlan.stagemobile.audio.engine

/**
 * Interface for the sound engine.
 * Allows switching between FluidSynth and a Dummy engine for UI testing.
 */
interface AudioEngine {
    val isInitialized: Boolean
    fun initialize(sampleRate: Int, bufferSize: Int, deviceId: Int)
    fun loadSoundFont(path: String): Int
    fun unloadSoundFont(sfId: Int): Boolean
    fun noteOn(channel: Int, key: Int, velocity: Int)
    fun noteOff(channel: Int, key: Int)
    fun setVolume(channel: Int, volumeDb: Float)
    fun controlChange(channel: Int, controller: Int, value: Int)
    fun pitchBend(channel: Int, value: Int)
    fun panic()
    fun programChange(channel: Int, bank: Int, program: Int)
    fun programSelect(channelId: Int, sfId: Int, bank: Int, program: Int)
    fun getPresets(sfId: Int): List<com.marceloferlan.stagemobile.domain.model.Sf2Preset>
    fun getChannelLevels(output: FloatArray)
    fun setInterpolation(method: Int)
    fun setPolyphony(maxVoices: Int)
    fun setMasterLimiter(enabled: Boolean)

    // Modular DSP
    fun addEffect(channel: Int, type: Int)
    fun removeEffect(channel: Int, index: Int)
    fun clearEffects(channel: Int)
    fun setEffectParam(channel: Int, index: Int, paramId: Int, value: Float)
    fun setEffectEnabled(channel: Int, index: Int, enabled: Boolean)
    fun setDspMasterBypass(enabled: Boolean)
    fun getEffectMeters(channel: Int, index: Int, output: FloatArray): Boolean
    
    fun destroy()
}