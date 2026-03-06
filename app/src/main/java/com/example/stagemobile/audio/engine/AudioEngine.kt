package com.example.stagemobile.audio.engine

interface AudioEngine {
    fun init(sampleRate: Int = 48000, bufferSize: Int = 256, deviceId: Int = -1)
    fun loadSoundFont(path: String): Int // returns sfId or -1
    fun unloadSoundFont(sfId: Int): Boolean
    fun noteOn(channel: Int, key: Int, velocity: Int)
    fun noteOff(channel: Int, key: Int)
    fun setChannelVolume(channelId: Int, volume: Float)
    fun programChange(channel: Int, bank: Int, program: Int)
    fun programSelect(channel: Int, sfId: Int, bank: Int, program: Int)
    fun destroy()
}