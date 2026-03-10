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
    fun programChange(channel: Int, bank: Int, program: Int)
    fun programSelect(channel: Int, sfId: Int, bank: Int, program: Int)
    fun getChannelLevels(output: FloatArray)
    fun destroy()
}