package com.example.stagemobile.audio.engine

/**
 * Interface for the sound engine.
 * Allows switching between FluidSynth and a Dummy engine for UI testing.
 */
interface AudioEngine {
    fun init(sampleRate: Int, bufferSize: Int, deviceId: Int)
    fun loadSoundFont(path: String): Int
    fun unloadSoundFont(sfId: Int): Boolean
    fun noteOn(channel: Int, key: Int, velocity: Int)
    fun noteOff(channel: Int, key: Int)
    fun setChannelVolume(channelId: Int, volume: Float)
    fun controlChange(channel: Int, controller: Int, value: Int)
    fun pitchBend(channel: Int, value: Int)
    fun panic()
    fun programChange(channel: Int, bank: Int, program: Int)
    fun programSelect(channel: Int, sfId: Int, bank: Int, program: Int)
    fun getPresets(sfId: Int): List<com.example.stagemobile.domain.model.Sf2Preset>
    fun getChannelLevels(output: FloatArray)
    fun setInterpolation(method: Int)
    fun setPolyphony(maxVoices: Int)
    fun setMasterLimiter(enabled: Boolean)
    fun destroy()
}