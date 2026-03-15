package com.example.stagemobile.audio.engine

import android.util.Log
import com.example.stagemobile.domain.model.Sf2Preset

class FluidSynthEngine : AudioEngine {

    companion object {
        private const val TAG = "FluidSynthEngine"
        var isAvailable = false
            private set

        init {
            Log.d(TAG, "FluidSynthEngine static initializer starting")
            try {
                // Android does NOT auto-load transitive .so dependencies
                // Must load in dependency order: base libs → FluidSynth → our bridge
                val optionalLibs = listOf(
                    "pcre",
                    "pcreposix",
                    "glib-2.0",
                    "gthread-2.0",
                    "gmodule-2.0",
                    "gobject-2.0",
                    "gio-2.0",
                    "ogg",
                    "vorbis",
                    "vorbisenc",
                    "vorbisfile",
                    "FLAC",
                    "opus",
                    "sndfile",
                    "instpatch-1.0",
                    "oboe",
                    "fluidsynth"
                )

                for (lib in optionalLibs) {
                    try {
                        System.loadLibrary(lib)
                        Log.v(TAG, "✓ Loaded: lib$lib.so")
                    } catch (e: UnsatisfiedLinkError) {
                        Log.e(TAG, "✗ Optional lib failed: lib$lib.so → ${e.message}")
                    }
                }

                System.loadLibrary("synthmodule")
                isAvailable = true
                Log.i(TAG, "★ FluidSynth native bridge ready")

            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "✗✗ CRITICAL: synthmodule failed to load: ${e.message}", e)
                isAvailable = false
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error: ${e.message}", e)
                isAvailable = false
            }
        }
    }

    private var isInitialized = false

    // Native JNI methods
    private external fun nativeInit(sampleRate: Int, bufferSize: Int, deviceId: Int): Boolean
    private external fun nativeLoadSf2(path: String): Int
    private external fun nativeUnloadSf2(sfId: Int): Boolean
    private external fun nativeNoteOn(channel: Int, key: Int, velocity: Int)
    private external fun nativeNoteOff(channel: Int, key: Int)
    private external fun nativeSetVolume(channel: Int, volume: Float)
    private external fun nativeProgramChange(channel: Int, bank: Int, program: Int)
    private external fun nativeProgramSelect(channel: Int, sfId: Int, bank: Int, program: Int): Boolean
    private external fun nativeWarmUpChannel(channel: Int)
    private external fun nativeControlChange(channel: Int, controller: Int, value: Int)
    private external fun nativeGetChannelLevels(output: FloatArray)
    private external fun nativeGetPresets(sfId: Int): Array<String>
    private external fun nativeSetInterpolation(method: Int)
    private external fun nativeSetPolyphony(maxVoices: Int)
    private external fun nativeSetMasterLimiter(enabled: Boolean)
    private external fun nativePitchBend(channel: Int, value: Int)
    private external fun nativePanic()
    private external fun nativeDestroy()

    override fun init(sampleRate: Int, bufferSize: Int, deviceId: Int) {
        if (!isInitialized && isAvailable) {
            try {
                isInitialized = nativeInit(sampleRate, bufferSize, deviceId)
                Log.i(TAG, "FluidSynth init result: $isInitialized (sampleRate=$sampleRate, buf=$bufferSize, device=$deviceId)")
            } catch (e: Exception) {
                Log.e(TAG, "nativeInit crashed: ${e.message}", e)
                isInitialized = false
            }
        }
    }

    override fun loadSoundFont(path: String): Int {
        if (!isInitialized) {
            Log.w(TAG, "Cannot load SF2: engine not initialized")
            return -1
        }
        return try {
            val sfId = nativeLoadSf2(path)
            Log.i(TAG, "SoundFont load: path=$path → sfId=$sfId (${if (sfId >= 0) "OK" else "FAILED"})")
            sfId
        } catch (e: Exception) {
            Log.e(TAG, "nativeLoadSf2 crashed: ${e.message}", e)
            -1
        }
    }

    override fun unloadSoundFont(sfId: Int): Boolean {
        if (!isInitialized || sfId < 0) return false
        return try {
            val result = nativeUnloadSf2(sfId)
            Log.i(TAG, "SoundFont unload: sfId=$sfId → result=$result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "nativeUnloadSf2 crashed: ${e.message}", e)
            false
        }
    }

    override fun noteOn(channel: Int, key: Int, velocity: Int) {
        Log.d(TAG, "noteOn(ch=$channel, key=$key, vel=$velocity) init=$isInitialized")
        if (isInitialized) {
            try { nativeNoteOn(channel, key, velocity) }
            catch (e: Exception) { Log.e(TAG, "noteOn error: ${e.message}") }
        }
    }

    override fun noteOff(channel: Int, key: Int) {
        if (isInitialized) {
            try { nativeNoteOff(channel, key) }
            catch (e: Exception) { Log.e(TAG, "noteOff error: ${e.message}") }
        }
    }

    override fun setChannelVolume(channelId: Int, volume: Float) {
        if (isInitialized) {
            try { nativeSetVolume(channelId, volume) }
            catch (e: Exception) { Log.e(TAG, "setVolume error: ${e.message}") }
        }
    }

    override fun programChange(channel: Int, bank: Int, program: Int) {
        Log.d(TAG, "programChange(ch=$channel, bank=$bank, prog=$program)")
        if (isInitialized) {
            try { nativeProgramChange(channel, bank, program) }
            catch (e: Exception) { Log.e(TAG, "programChange error: ${e.message}") }
        }
    }

    override fun controlChange(channel: Int, controller: Int, value: Int) {
        if (isInitialized) {
            try { nativeControlChange(channel, controller, value) }
            catch (e: Exception) { Log.e(TAG, "controlChange error: ${e.message}") }
        }
    }

    override fun pitchBend(channel: Int, value: Int) {
        if (isInitialized) {
            try { nativePitchBend(channel, value) }
            catch (e: Exception) { Log.e(TAG, "pitchBend error: ${e.message}") }
        }
    }

    override fun panic() {
        if (isInitialized) {
            try { nativePanic() }
            catch (e: Exception) { Log.e(TAG, "panic error: ${e.message}") }
        }
    }

    fun warmUpChannel(channel: Int) {
        if (isInitialized) {
            try { 
                nativeWarmUpChannel(channel)
                Log.d(TAG, "Warm-up triggered for channel $channel")
            }
            catch (e: Exception) { Log.e(TAG, "warmUpChannel error: ${e.message}") }
        }
    }

    override fun programSelect(channel: Int, sfId: Int, bank: Int, program: Int) {
        Log.d(TAG, "programSelect(ch=$channel, sfId=$sfId, bank=$bank, prog=$program)")
        if (isInitialized) {
            try {
                val result = nativeProgramSelect(channel, sfId, bank, program)
                Log.i(TAG, "programSelect result: $result")
            } catch (e: Exception) {
                Log.e(TAG, "programSelect error: ${e.message}")
            }
        }
    }

    override fun getPresets(sfId: Int): List<Sf2Preset> {
        if (!isInitialized) {
            Log.w(TAG, "Cannot get presets: engine not initialized")
            return emptyList()
        }
        return try {
            val rawPresets = nativeGetPresets(sfId)
            rawPresets.mapNotNull { raw ->
                val parts = raw.split("|", limit = 3)
                if (parts.size == 3) {
                    Sf2Preset(
                        bank = parts[0].toIntOrNull() ?: 0,
                        program = parts[1].toIntOrNull() ?: 0,
                        name = parts[2]
                    )
                } else null
            }.also { Log.i(TAG, "Parsed ${it.size} presets for sfId=$sfId") }
        } catch (e: Exception) {
            Log.e(TAG, "getPresets error: ${e.message}", e)
            emptyList()
        }
    }

    override fun getChannelLevels(output: FloatArray) {
        if (isInitialized) {
            try { nativeGetChannelLevels(output) }
            catch (e: Exception) { /* Silent fail to avoid log spam in render loop */ }
        }
    }

    override fun setInterpolation(method: Int) {
        if (isInitialized) {
            try {
                nativeSetInterpolation(method)
                Log.i(TAG, "Interpolation set to method $method")
            } catch (e: Exception) { Log.e(TAG, "setInterpolation error: ${e.message}") }
        }
    }

    override fun setPolyphony(maxVoices: Int) {
        if (isInitialized) {
            try {
                nativeSetPolyphony(maxVoices)
                Log.i(TAG, "Polyphony set to $maxVoices voices")
            } catch (e: Exception) { Log.e(TAG, "setPolyphony error: ${e.message}") }
        }
    }

    override fun setMasterLimiter(enabled: Boolean) {
        if (isInitialized) {
            try {
                nativeSetMasterLimiter(enabled)
                Log.i(TAG, "Master Limiter enabled: $enabled")
            } catch (e: Exception) { Log.e(TAG, "setMasterLimiter error: ${e.message}") }
        }
    }

    override fun destroy() {
        if (isInitialized) {
            try {
                nativeDestroy()
                Log.i(TAG, "FluidSynth destroyed")
            } catch (e: Exception) {
                Log.e(TAG, "destroy error: ${e.message}")
            }
            isInitialized = false
        }
    }
}