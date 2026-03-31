package com.marceloferlan.stagemobile.audio.engine

import android.util.Log
import com.marceloferlan.stagemobile.domain.model.Sf2Preset

class DummyAudioEngine : AudioEngine {
    override val isInitialized: Boolean = true
    override fun initialize(sampleRate: Int, bufferSize: Int, deviceId: Int) {
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

    override fun setVolume(channel: Int, volumeDb: Float) {
        Log.d("DummyEngine", "Channel $channel Volume (dB): $volumeDb")
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

    override fun programSelect(channelId: Int, sfId: Int, bank: Int, program: Int) {
        Log.d("DummyEngine", "programSelect(ch=$channelId, sfId=$sfId, bank=$bank, prog=$program)")
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

    override fun addEffect(channel: Int, type: Int) {
        Log.d("DummyEngine", "addEffect(ch=$channel, type=$type)")
    }

    override fun clearEffects(channel: Int) {
        Log.d("DummyEngine", "clearEffects(ch=$channel)")
    }

    override fun removeEffect(channel: Int, index: Int) {
        Log.d("DummyEngine", "removeEffect(ch=$channel, idx=$index)")
    }

    override fun setEffectParam(channel: Int, index: Int, paramId: Int, value: Float) {
        Log.d("DummyEngine", "setEffectParam(ch=$channel, idx=$index, pid=$paramId, val=$value)")
    }

    override fun setEffectEnabled(channel: Int, index: Int, enabled: Boolean) {
        Log.d("DummyEngine", "setEffectEnabled(ch=$channel, idx=$index, enabled=$enabled)")
    }

    override fun setDspMasterBypass(enabled: Boolean) {
        Log.d("DummyEngine", "setDspMasterBypass(enabled=$enabled)")
    }

    override fun getEffectMeters(channel: Int, index: Int, output: FloatArray): Boolean {
        return false
    }

    override fun destroy() {
        Log.d("DummyEngine", "Audio Engine Destroyed")
    }
}