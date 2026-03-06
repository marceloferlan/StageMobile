package com.example.stagemobile.audio.engine

import android.util.Log

class FluidSynthEngine : AudioEngine {

    companion object {
        private const val TAG = "FluidSynthEngine"
        var isAvailable = false
            private set

        init {
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
                        Log.d(TAG, "✓ Loaded: lib$lib.so")
                    } catch (e: UnsatisfiedLinkError) {
                        Log.w(TAG, "✗ Optional lib failed: lib$lib.so → ${e.message}")
                    }
                }

                // This one MUST load — it's our JNI bridge
                System.loadLibrary("synthmodule")
                isAvailable = true
                Log.i(TAG, "★ synthmodule loaded — FluidSynth ready!")

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