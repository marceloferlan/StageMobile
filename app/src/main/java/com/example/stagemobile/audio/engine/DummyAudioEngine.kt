package com.example.stagemobile.audio.engine

import android.util.Log

class DummyAudioEngine : AudioEngine {
    override fun init(sampleRate: Int, bufferSize: Int, deviceId: Int) {
        Log.d("DummyEngine", "Audio Engine Initialized (SampleRate: $sampleRate, Buffer: $bufferSize, Device: $deviceId)")
    }

    override fun loadSoundFont(path: String): Int {
        Log.d("DummyEngine", "SoundFont Loaded: $path")
        return 1
    }

    override fun unloadSoundFont(sfId: Int): Boolean {
        Log.d("DummyEngine", "SoundFont Unloaded: $sfId")
        return true
    }

    override fun noteOn(channel: Int, key: Int, velocity: Int) {
        Log.d("DummyEngine", "Note On: $key, Velocity: $velocity (Channel $channel)")
    }

    override fun noteOff(channel: Int, key: Int) {
        Log.d("DummyEngine", "Note Off: $key (Channel)")
    }

    override fun setChannelVolume(channelId: Int, volume: Float) {
        Log.d("DummyEngine", "Channel $channelId Volume: $volume")
    }

    override fun programChange(channel: Int, bank: Int, program: Int) {
        Log.d("DummyEngine", "Program Change: ch=$channel, bank=$bank, program=$program")
    }

    override fun programSelect(channel: Int, sfId: Int, bank: Int, program: Int) {
        Log.d("DummyEngine", "programSelect(ch=$channel, sfId=$sfId, bank=$bank, prog=$program)")
    }

    override fun getChannelLevels(output: FloatArray) {
        // No audio in dummy engine
    }

    override fun destroy() {
        Log.d("DummyEngine", "Audio Engine Destroyed")
    }
}