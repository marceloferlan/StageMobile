package com.example.stagemobile.audio.engine

import android.util.Log
import com.example.stagemobile.domain.model.Sf2Preset

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

    override fun controlChange(channel: Int, controller: Int, value: Int) {
        Log.d("DummyEngine", "Control Change: ch=$channel, cc=$controller, value=$value")
    }

    override fun pitchBend(channel: Int, value: Int) {
        Log.d("DummyEngine", "Pitch Bend: ch=$channel, value=$value")
    }

    override fun panic() {
        Log.d("DummyEngine", "PANIC: All sound and notes off, controllers reset")
    }

    override fun programChange(channel: Int, bank: Int, program: Int) {
        Log.d("DummyEngine", "Program Change: ch=$channel, bank=$bank, program=$program")
    }

    override fun programSelect(channel: Int, sfId: Int, bank: Int, program: Int) {
        Log.d("DummyEngine", "programSelect(ch=$channel, sfId=$sfId, bank=$bank, prog=$program)")
    }

    override fun getPresets(sfId: Int): List<Sf2Preset> {
        Log.d("DummyEngine", "getPresets(sfId=$sfId)")
        return listOf(Sf2Preset(0, 0, "Dummy Preset"))
    }

    override fun getChannelLevels(output: FloatArray) {
        // No audio in dummy engine
    }

    override fun setInterpolation(method: Int) {
        Log.d("DummyEngine", "Interpolation set to method $method")
    }

    override fun setPolyphony(maxVoices: Int) {
        Log.d("DummyEngine", "Polyphony set to $maxVoices voices")
    }

    override fun setMasterLimiter(enabled: Boolean) {
        Log.d("DummyEngine", "Master Limiter set to $enabled")
    }

    override fun destroy() {
        Log.d("DummyEngine", "Audio Engine Destroyed")
    }
}