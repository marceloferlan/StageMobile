#include <jni.h>
#include <android/log.h>
#include <fluidsynth.h>
#include <string>

#define LOG_TAG "FluidSynthBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global FluidSynth instances
static fluid_settings_t* settings = nullptr;
static fluid_synth_t* synth = nullptr;
static fluid_audio_driver_t* adriver = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeInit(
        JNIEnv* env,
        jobject thiz,
        jint sampleRate,
        jint bufferSize,
        jint deviceId) {

    LOGI("=== nativeInit called (sampleRate=%d, bufferSize=%d, deviceId=%d) ===", sampleRate, bufferSize, deviceId);

    if (synth != nullptr) {
        LOGW("FluidSynth already initialized, destroying first");
        if (adriver) { delete_fluid_audio_driver(adriver); adriver = nullptr; }
        if (synth) { delete_fluid_synth(synth); synth = nullptr; }
        if (settings) { delete_fluid_settings(settings); settings = nullptr; }
    }

    // Create settings
    settings = new_fluid_settings();
    if (!settings) {
        LOGE("Failed to create FluidSynth settings");
        return JNI_FALSE;
    }
    LOGI("Settings created OK");

    // Configure settings for Android audio (FastAudio / Low Latency Path)
    fluid_settings_setnum(settings, "synth.sample-rate", (double)sampleRate);
    fluid_settings_setint(settings, "synth.polyphony", 64);
    fluid_settings_setint(settings, "synth.midi-channels", 16);
    fluid_settings_setnum(settings, "synth.gain", 1.0);
    
    // Dynamic buffer size from App Settings (< 10ms target for 64)
    fluid_settings_setint(settings, "audio.periods", 2);
    fluid_settings_setint(settings, "audio.period-size", bufferSize);

    // Force Google Oboe Native driver properties
    fluid_settings_setstr(settings, "audio.oboe.performance-mode", "LowLatency");
    fluid_settings_setstr(settings, "audio.oboe.sharing-mode", "Exclusive");
    if (deviceId >= 0) {
        fluid_settings_setint(settings, "audio.oboe.id", deviceId);
        LOGI("Oboe Audio ID bound to Hardware Interface %d", deviceId);
    }

    // Create synth
    synth = new_fluid_synth(settings);
    if (!synth) {
        LOGE("Failed to create FluidSynth synthesizer");
        delete_fluid_settings(settings);
        settings = nullptr;
        return JNI_FALSE;
    }
    LOGI("Synth created OK");

    // Try multiple audio drivers in order of preference
    const char* drivers[] = {"oboe", "opensles", "pulseaudio", nullptr};
    for (int i = 0; drivers[i] != nullptr; i++) {
        LOGI("Trying audio driver: %s", drivers[i]);
        fluid_settings_setstr(settings, "audio.driver", drivers[i]);
        adriver = new_fluid_audio_driver(settings, synth);
        if (adriver) {
            LOGI("★ Audio driver '%s' created successfully!", drivers[i]);
            break;
        }
        LOGW("Audio driver '%s' failed, trying next...", drivers[i]);
    }

    if (!adriver) {
        LOGW("No audio driver available — synth created but no sound output");
    }

    LOGI("=== FluidSynth fully initialized ===");
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeLoadSf2(
        JNIEnv* env,
        jobject thiz,
        jstring path) {

    if (!synth) {
        LOGE("Cannot load SF2: synth not initialized");
        return -1;
    }

    const char* sf2Path = env->GetStringUTFChars(path, nullptr);
    // reset=0: do NOT reassign channels. We use programSelect to bind per-channel.
    int sfId = fluid_synth_sfload(synth, sf2Path, 0);

    if (sfId < 0) {
        LOGE("Failed to load SoundFont: %s", sf2Path);
    } else {
        LOGI("SoundFont loaded: %s (id=%d)", sf2Path, sfId);
    }

    env->ReleaseStringUTFChars(path, sf2Path);
    return sfId;
}

JNIEXPORT jboolean JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeUnloadSf2(
        JNIEnv* env,
        jobject thiz,
        jint sfId) {

    if (!synth) return JNI_FALSE;

    int result = fluid_synth_sfunload(synth, sfId, 1); // 1 = reset presets
    if (result == FLUID_OK) {
        LOGI("SoundFont unloaded successfully (id=%d)", sfId);
        return JNI_TRUE;
    } else {
        LOGE("Failed to unload SoundFont (id=%d)", sfId);
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeNoteOn(
        JNIEnv* env,
        jobject thiz,
        jint channel,
        jint key,
        jint velocity) {

    if (synth) {
        fluid_synth_noteon(synth, channel, key, velocity);
    }
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeNoteOff(
        JNIEnv* env,
        jobject thiz,
        jint channel,
        jint key) {

    if (synth) {
        fluid_synth_noteoff(synth, channel, key);
    }
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetVolume(
        JNIEnv* env,
        jobject thiz,
        jint channel,
        jfloat volumeDb) {

    if (!synth) return;

    // Convert dB to MIDI CC value (0-127)
    // Range: -60dB → CC 0, 0dB → CC 116, +6dB → CC 127
    float normalized;
    if (volumeDb <= -60.0f) {
        normalized = 0.0f;
    } else if (volumeDb >= 6.0f) {
        normalized = 1.0f;
    } else {
        normalized = (volumeDb + 60.0f) / 66.0f;
    }

    int ccValue = (int)(normalized * 127.0f);
    LOGI("Volume: %.1f dB → CC %d (ch=%d)", volumeDb, ccValue, channel);
    fluid_synth_cc(synth, channel, 7, ccValue); // CC 7 = Volume
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeProgramChange(
        JNIEnv* env,
        jobject thiz,
        jint channel,
        jint bank,
        jint program) {

    if (!synth) return;

    fluid_synth_bank_select(synth, channel, bank);
    fluid_synth_program_change(synth, channel, program);
    LOGI("Program change: ch=%d, bank=%d, program=%d", channel, bank, program);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeDestroy(
        JNIEnv* env,
        jobject thiz) {

    if (adriver) {
        delete_fluid_audio_driver(adriver);
        adriver = nullptr;
    }
    if (synth) {
        delete_fluid_synth(synth);
        synth = nullptr;
    }
    if (settings) {
        delete_fluid_settings(settings);
        settings = nullptr;
    }

    LOGI("FluidSynth destroyed");
}

JNIEXPORT jboolean JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeProgramSelect(
        JNIEnv* env,
        jobject thiz,
        jint channel,
        jint sfId,
        jint bank,
        jint program) {

    if (!synth) {
        LOGE("Cannot programSelect: synth not initialized");
        return JNI_FALSE;
    }

    int result = fluid_synth_program_select(synth, channel, sfId, bank, program);
    if (result == FLUID_OK) {
        LOGI("programSelect OK: ch=%d, sfId=%d, bank=%d, prog=%d", channel, sfId, bank, program);
        return JNI_TRUE;
    } else {
        LOGE("programSelect FAILED: ch=%d, sfId=%d, bank=%d, prog=%d", channel, sfId, bank, program);
        return JNI_FALSE;
    }
}

} // extern "C"
