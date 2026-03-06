package com.example.stagemobile.audio.engine

import android.util.Log

/**
 * Dummy implementation for testing UI without native libs.
 */
class DummyAudioEngine : AudioEngine {
    private val tag = "DummyAudioEngine"
    private var nextSfId = 0

    override fun init(sampleRate: Int, bufferSize: Int, deviceId: Int) {
        Log.d(tag, "DummyAudioEngine initialized at $sampleRate Hz, Device ID $deviceId")
    }

    override fun loadSoundFont(path: String): Int {
        Log.d(tag, "Dummy loadSoundFont: $path")
        return 1 // Mock sfId
    }

    override fun unloadSoundFont(sfId: Int): Boolean {
        Log.d(tag, "Dummy unloadSoundFont: id=$sfId")
        return true
    }

    override fun noteOn(channel: Int, key: Int, velocity: Int) {
        Log.d(tag, "noteOn(ch=$channel, key=$key, vel=$velocity)")
    }

    override fun noteOff(channel: Int, key: Int) {
        Log.d(tag, "noteOff(ch=$channel, key=$key)")
    }

    override fun setChannelVolume(channelId: Int, volume: Float) {
        Log.d(tag, "setVolume(ch=$channelId, vol=$volume)")
    }

    override fun programChange(channel: Int, bank: Int, program: Int) {
        Log.d(tag, "programChange(ch=$channel, bank=$bank, prog=$program)")
    }

    override fun programSelect(channel: Int, sfId: Int, bank: Int, program: Int) {
        Log.d(tag, "programSelect(ch=$channel, sfId=$sfId, bank=$bank, prog=$program)")
    }

    override fun destroy() {
        Log.d(tag, "destroy()")
    }
}