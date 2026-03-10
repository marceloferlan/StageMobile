#include <jni.h>
#include <android/log.h>
#include <string>
#include <mutex>

// Include local FluidSynth headers
#include "include/fluidsynth.h"
#include "include/fluidsynth/voice.h"
#include "include/fluidsynth/gen.h"

#define LOG_TAG "FluidSynthBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global FluidSynth instances protected by mutex
static std::recursive_mutex engine_mutex;
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

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
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

    // Configure settings for Android audio
    fluid_settings_setnum(settings, "synth.sample-rate", (double)sampleRate);
    fluid_settings_setint(settings, "synth.polyphony", 64);
    fluid_settings_setint(settings, "synth.midi-channels", 16);
    fluid_settings_setnum(settings, "synth.gain", 1.0);
    
    fluid_settings_setint(settings, "audio.periods", 2);
    fluid_settings_setint(settings, "audio.period-size", bufferSize);

    // Oboe Native driver properties
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

    // Try audio drivers
    const char* drivers[] = {"oboe", "opensles", nullptr};
    for (int i = 0; drivers[i] != nullptr; i++) {
        LOGI("Trying audio driver: %s", drivers[i]);
        fluid_settings_setstr(settings, "audio.driver", drivers[i]);
        adriver = new_fluid_audio_driver(settings, synth);
        if (adriver) {
            LOGI("★ Audio driver '%s' created successfully!", drivers[i]);
            break;
        }
    }

    if (!adriver) {
        LOGW("No audio driver available — no sound output");
    }

    LOGI("=== FluidSynth fully initialized ===");
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeLoadSf2(
        JNIEnv* env,
        jobject thiz,
        jstring path) {

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) {
        LOGE("Cannot load SF2: synth not initialized");
        return -1;
    }

    const char* sf2Path = env->GetStringUTFChars(path, nullptr);
    LOGI("---> JNI: Attempting to load SF2: %s", sf2Path);

    // Verify file accessibility before FluidSynth takes over
    FILE* testFile = fopen(sf2Path, "rb");
    if (testFile) {
        LOGI("---> JNI: File is reachable by native layer.");
        fclose(testFile);
    } else {
        LOGE("---> JNI: ERROR! File NOT reachable (errno=%d). Possibly sandbox restrictions or path error.", errno);
    }

    // reset=0: do NOT reassign channels
    int sfId = fluid_synth_sfload(synth, sf2Path, 0);

    if (sfId < 0) {
        LOGE("---> JNI: FluidSynth FAILED to load SF2. Error Code: %d", sfId);
    } else {
        LOGI("---> JNI: SoundFont loaded successfully! ID: %d", sfId);
    }

    env->ReleaseStringUTFChars(path, sf2Path);
    return sfId;
}

JNIEXPORT jboolean JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeUnloadSf2(
        JNIEnv* env,
        jobject thiz,
        jint sfId) {

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) return JNI_FALSE;

    int result = fluid_synth_sfunload(synth, sfId, 1);
    if (result == FLUID_OK) {
        LOGI("SoundFont unloaded (id=%d)", sfId);
        return JNI_TRUE;
    } else {
        LOGE("Failed to unload (id=%d)", sfId);
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

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
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

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
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

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) return;

    float normalized;
    if (volumeDb <= -60.0f) normalized = 0.0f;
    else if (volumeDb >= 6.0f) normalized = 1.0f;
    else normalized = (volumeDb + 60.0f) / 66.0f;

    int ccValue = (int)(normalized * 127.0f);
    fluid_synth_cc(synth, channel, 7, ccValue);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeProgramChange(
        JNIEnv* env,
        jobject thiz,
        jint channel,
        jint bank,
        jint program) {

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) return;

    fluid_synth_bank_select(synth, channel, bank);
    fluid_synth_program_change(synth, channel, program);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeDestroy(
        JNIEnv* env,
        jobject thiz) {

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (adriver) { delete_fluid_audio_driver(adriver); adriver = nullptr; }
    if (synth) { delete_fluid_synth(synth); synth = nullptr; }
    if (settings) { delete_fluid_settings(settings); settings = nullptr; }
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

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) return JNI_FALSE;
    int result = fluid_synth_program_select(synth, channel, sfId, bank, program);
    return (result == FLUID_OK) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeGetChannelLevels(
        JNIEnv* env,
        jobject thiz,
        jfloatArray output) {

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth || !settings) return;

    jsize len = env->GetArrayLength(output);
    if (len > 16) len = 16; // Safety cap to match MIDI channels and local buffer
    
    static float local_levels[16];
    static fluid_voice_t* voicelist[128];
    
    for(int i=0; i<16; i++) local_levels[i] = 0.0f;
    for(int i=0; i<128; i++) voicelist[i] = nullptr; 

    int poly = 64;
    fluid_settings_getint(settings, "synth.polyphony", &poly);
    if (poly > 128) poly = 128;

    fluid_synth_get_voicelist(synth, voicelist, poly, -1);

    for (int i = 0; i < poly; i++) {
        fluid_voice_t* voice = voicelist[i];
        if (voice != nullptr) {
            if (fluid_voice_is_on(voice)) {
                int chan = fluid_voice_get_channel(voice);
                if (chan >= 0 && chan < 16 && chan < len) {
                    float atten = fluid_voice_gen_get(voice, GEN_ATTENUATION);
                    float linearVolume = (1000.0f - atten) / 1000.0f;
                    if (linearVolume < 0.0f) linearVolume = 0.0f;
                    if (linearVolume > local_levels[chan]) local_levels[chan] = linearVolume;
                }
            }
        }
    }

    for(int i=0; i<len && i<16; i++) {
        local_levels[i] *= 1.5f; 
        if (local_levels[i] > 1.2f) local_levels[i] = 1.2f; 
    }

    env->SetFloatArrayRegion(output, 0, len, local_levels);
}

} // extern "C"
