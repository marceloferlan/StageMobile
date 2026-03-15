#include <jni.h>
#include <android/log.h>
#include <string>
#include <mutex>
#include <deque>
#include <thread>
#include <condition_variable>
#include <atomic>

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

// --- MIDI Ring Buffer (Lock-Free FIFO) ---
struct MidiEvent {
    enum Type { NoteOn, NoteOff } type;
    int channel;
    int key;
    int velocity; // only used for NoteOn
};

static std::deque<MidiEvent> midiQueue;
static std::mutex queueMutex;
static std::condition_variable queueCv;
static std::atomic<bool> midiThreadRunning{false};
static std::thread midiThread;

// Dedicated MIDI processing thread — decouples MIDI input from audio rendering
static void midiProcessingLoop() {
    LOGI("★ MIDI processing thread started");
    while (midiThreadRunning.load()) {
        std::unique_lock<std::mutex> lock(queueMutex);
        queueCv.wait(lock, [] { return !midiQueue.empty() || !midiThreadRunning.load(); });
        while (!midiQueue.empty()) {
            MidiEvent ev = midiQueue.front();
            midiQueue.pop_front();
            lock.unlock(); // release queue lock before touching synth
            {
                std::lock_guard<std::recursive_mutex> synthLock(engine_mutex);
                if (synth) {
                    if (ev.type == MidiEvent::NoteOn) {
                        fluid_synth_noteon(synth, ev.channel, ev.key, ev.velocity);
                    } else {
                        fluid_synth_noteoff(synth, ev.channel, ev.key);
                    }
                }
            }
            lock.lock();
        }
    }
    LOGI("★ MIDI processing thread stopped");
}

// Helper: start the MIDI thread if not running
static void startMidiThread() {
    if (!midiThreadRunning.load()) {
        midiThreadRunning.store(true);
        midiThread = std::thread(midiProcessingLoop);
        LOGI("MIDI thread launched");
    }
}

// Helper: stop the MIDI thread gracefully
static void stopMidiThread() {
    if (midiThreadRunning.load()) {
        midiThreadRunning.store(false);
        queueCv.notify_all();
        if (midiThread.joinable()) {
            midiThread.join();
        }
        // Drain any remaining events
        std::lock_guard<std::mutex> lock(queueMutex);
        midiQueue.clear();
        LOGI("MIDI thread stopped and queue drained");
    }
}

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
    fluid_settings_setnum(settings, "synth.gain", 0.7); // Reduced from 1.0 for gain staging/headroom
    
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
    bool driverCreated = false;
    for (int i = 0; drivers[i] != nullptr; i++) {
        LOGI("Trying to create audio driver: %s", drivers[i]);
        fluid_settings_setstr(settings, "audio.driver", drivers[i]);
        adriver = new_fluid_audio_driver(settings, synth);
        if (adriver) {
            LOGI("★ Audio driver '%s' created successfully!", drivers[i]);
            driverCreated = true;
            break;
        } else {
            LOGW("Failed to create audio driver '%s'", drivers[i]);
        }
    }
 
    if (!driverCreated) {
        LOGE("CRITICAL: No audio driver could be initialized! Silence expected.");
    }

    // Start the dedicated MIDI processing thread
    startMidiThread();
 
    LOGI("=== nativeInit complete ===");
    return driverCreated ? JNI_TRUE : JNI_FALSE;
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

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeWarmUpChannel(
        JNIEnv* env,
        jobject thiz,
        jint channel) {

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) return;

    LOGI("---> JNI: Warming up channel %d", channel);
    
    // Play a silent note to trigger voice allocation and sample caching
    fluid_synth_noteon(synth, channel, 60, 0); 
    fluid_synth_noteoff(synth, channel, 60);
    
    LOGI("---> JNI: Warm-up complete for channel %d", channel);
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

    // Enqueue to MIDI Ring Buffer — never blocks audio thread
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        midiQueue.push_back({MidiEvent::NoteOn, (int)channel, (int)key, (int)velocity});
    }
    queueCv.notify_one();
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeNoteOff(
        JNIEnv* env,
        jobject thiz,
        jint channel,
        jint key) {

    // Enqueue to MIDI Ring Buffer — never blocks audio thread
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        midiQueue.push_back({MidiEvent::NoteOff, (int)channel, (int)key, 0});
    }
    queueCv.notify_one();
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
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeControlChange(
        JNIEnv* env,
        jobject thiz,
        jint channel,
        jint controller,
        jint value) {

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) return;
    fluid_synth_cc(synth, channel, controller, value);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativePitchBend(
        JNIEnv* env,
        jobject thiz,
        jint channel,
        jint value) {

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) return;
    fluid_synth_pitch_bend(synth, channel, value);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativePanic(
        JNIEnv* env,
        jobject thiz) {

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) return;
    
    LOGI("Executing PANIC: All notes off, sounds off, and CC reset");
    for (int ch = 0; ch < 16; ch++) {
        // Encerra imediatamente as notas
        fluid_synth_all_notes_off(synth, ch);
        fluid_synth_all_sounds_off(synth, ch);
        
        // Reseta controladores essenciais
        fluid_synth_cc(synth, ch, 64, 0); // Sustain off
        fluid_synth_cc(synth, ch, 1, 0);  // Modulation off
        fluid_synth_cc(synth, ch, 11, 127); // Expression to max
        fluid_synth_cc(synth, ch, 4, 127); // Foot controller to max
        fluid_synth_pitch_bend(synth, ch, 8192); // Pitch bend centered
    }
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

    // Stop the MIDI thread BEFORE destroying the synth
    stopMidiThread();

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (adriver) { delete_fluid_audio_driver(adriver); adriver = nullptr; }
    if (synth) { delete_fluid_synth(synth); synth = nullptr; }
    if (settings) { delete_fluid_settings(settings); settings = nullptr; }
    LOGI("FluidSynth destroyed (MIDI thread stopped)");
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
        JNIEnv* env, jobject thiz, jfloatArray output) {

    // NÍVEL 3: Non-blocking try_lock. 
    // Se a engine estiver ocupada processando notas, o medidor de pico pula este frame 
    // em vez de atrasar a thread de processamento.
    std::unique_lock<std::recursive_mutex> lock(engine_mutex, std::try_to_lock);
    if (!lock.owns_lock() || !synth || !settings) {
        // Se não conseguiu o lock, o Kotlin receberá o estado anterior ou zeros, 
        // mantendo a fluidez sem jitter.
        return; 
    }

    jsize len = env->GetArrayLength(output);
    if (len > 16) len = 16;
    
    static float local_levels[16];
    fluid_voice_t* voicelist_ptr[128];
    for(int i=0; i<128; i++) voicelist_ptr[i] = nullptr;

    // Reset frame-local levels
    float frame_levels[16];
    for(int i=0; i<16; i++) frame_levels[i] = 0.0f;
    
    int poly = 64;
    fluid_settings_getint(settings, "synth.polyphony", &poly);
    if (poly > 128) poly = 128;

    fluid_synth_get_voicelist(synth, voicelist_ptr, poly, -1);

    for (int i = 0; i < poly; i++) {
        fluid_voice_t* voice = voicelist_ptr[i];
        // Use fluid_voice_is_playing to catch voices in release phase too
        if (voice != nullptr && fluid_voice_is_playing(voice)) {
            int chan = fluid_voice_get_channel(voice);
            if (chan >= 0 && chan < 16) {
                // FALLBACK: Since actual_gain is missing in this FluidSynth binary,
                // we use GEN_ATTENUATION as base and cut to 0 when key is released (is_on == false).
                // This prevents the meter from staying stuck while held or after release.
                float linearVolume = 0.0f;
                if (fluid_voice_is_on(voice)) {
                    float atten_cb = fluid_voice_gen_get(voice, GEN_ATTENUATION);
                    // cB to linear: 10^(-cB/200)
                    linearVolume = powf(10.0f, -atten_cb / 200.0f);
                }
                
                if (linearVolume > frame_levels[chan]) frame_levels[chan] = linearVolume;
            }
        }
    }
    for(int i=0; i<len && i<16; i++) {
        // Return raw peak multiplier from the frame
        // No internal decay or additional gain for peak meter transparency
        local_levels[i] = frame_levels[i];
    }

    env->SetFloatArrayRegion(output, 0, len, local_levels);
}

JNIEXPORT jobjectArray JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeGetPresets(
        JNIEnv* env,
        jobject thiz,
        jint sfId) {

    std::lock_guard<std::recursive_mutex> lock(engine_mutex);

    // Find the String class for creating the return array
    jclass stringClass = env->FindClass("java/lang/String");

    if (!synth) {
        LOGE("Cannot get presets: synth not initialized");
        return env->NewObjectArray(0, stringClass, nullptr);
    }

    fluid_sfont_t* sfont = fluid_synth_get_sfont_by_id(synth, sfId);
    if (!sfont) {
        LOGE("Cannot get presets: SoundFont ID %d not found", sfId);
        return env->NewObjectArray(0, stringClass, nullptr);
    }

    // First pass: count presets
    int count = 0;
    fluid_sfont_iteration_start(sfont);
    while (fluid_sfont_iteration_next(sfont) != nullptr) {
        count++;
    }

    LOGI("SoundFont ID %d has %d presets", sfId, count);

    // Create the output array
    jobjectArray result = env->NewObjectArray(count, stringClass, nullptr);

    // Second pass: populate the array
    fluid_sfont_iteration_start(sfont);
    fluid_preset_t* preset;
    int index = 0;
    while ((preset = fluid_sfont_iteration_next(sfont)) != nullptr && index < count) {
        const char* name = fluid_preset_get_name(preset);
        int bank = fluid_preset_get_banknum(preset);
        int program = fluid_preset_get_num(preset);

        // Format: "bank|program|name"
        char buffer[256];
        snprintf(buffer, sizeof(buffer), "%d|%d|%s", bank, program, name ? name : "Unknown");

        jstring jstr = env->NewStringUTF(buffer);
        env->SetObjectArrayElement(result, index, jstr);
        env->DeleteLocalRef(jstr);
        index++;
    }

    LOGI("Returned %d presets for SoundFont ID %d", index, sfId);
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetInterpolation(
        JNIEnv* env,
        jobject thiz,
        jint method) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) {
        LOGW("setInterpolation called but synth is null");
        return;
    }
    // Apply to all 16 MIDI channels
    for (int ch = 0; ch < 16; ch++) {
        fluid_synth_set_interp_method(synth, ch, method);
    }
    LOGI("Interpolation method set to %d for all channels", method);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetPolyphony(
        JNIEnv* env,
        jobject thiz,
        jint maxVoices) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) {
        LOGW("setPolyphony called but synth is null");
        return;
    }
    fluid_synth_set_polyphony(synth, maxVoices);
    LOGI("Polyphony set to %d voices", maxVoices);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetMasterLimiter(
        JNIEnv* env,
        jobject thiz,
        jboolean enabled) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth || !settings) return;

    /* 
    if (enabled) {
        // PUNCH / STAGE LIMITER Configuration (Not available in current headers)
        fluid_synth_set_compressor_params(synth, -3.0, 4.0, 10.0, 20.0, 0.001, 0.1);
        LOGI("Master Limiter (Stage Punch) ENABLED");
    } else {
        // Transparency / Bypass (Neutral values)
        fluid_synth_set_compressor_params(synth, 0.0, 0.0, 0.0, 1.0, 0.01, 0.1);
        LOGI("Master Limiter (Stage Punch) DISABLED");
    }
    */
    LOGW("Master Limiter toggled, but compressor API is missing in this FluidSynth build.");
}

} // extern "C"
