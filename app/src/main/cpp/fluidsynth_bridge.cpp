#include <jni.h>
#include <android/log.h>
#include <string>
#include <mutex>
#include <deque>
#include <thread>
#include <condition_variable>
#include <atomic>
#include <math.h>

#include <oboe/Oboe.h>
#include "include/fluidsynth.h"
#include "include/fluidsynth/voice.h"
#include "include/fluidsynth/gen.h"
#include <arm_neon.h>
#include "Stk.h"
#include "dsp_chain.h"

#define LOG_TAG "StageAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace stage_audio;

// --- Globals ---
static std::recursive_mutex engine_mutex;
static fluid_settings_t* settings = nullptr;
static fluid_synth_t* synth = nullptr;
static std::unique_ptr<DSPChain> dspChain = nullptr;
static std::atomic<bool> dspMasterBypass{false};

// --- Performance Tracking ---
static std::atomic<uint32_t> activeChannelsMask{0};
static std::atomic<float> channelPeaks[16]; // Zero-cost meters
static std::atomic<int> activeNotesCountPerChannel[16];

// --- MIDI Queue ---
struct MidiEvent {
    enum Type { NoteOn, NoteOff, CC, PitchBend, ProgramChange, BankSelect } type;
    int channel;
    int data1; 
    int data2; 
};
static std::deque<MidiEvent> midiQueue;
static std::mutex queueMutex;
static std::condition_variable queueCv;
static std::atomic<bool> midiThreadRunning{false};
static std::thread midiThread;

static void midiProcessingLoop() {
    while (midiThreadRunning.load()) {
        std::unique_lock<std::mutex> lock(queueMutex);
        queueCv.wait(lock, [] { return !midiQueue.empty() || !midiThreadRunning.load(); });
        while (!midiQueue.empty()) {
            MidiEvent ev = midiQueue.front();
            midiQueue.pop_front();
            lock.unlock();
            {
                std::lock_guard<std::recursive_mutex> synthLock(engine_mutex);
                if (synth) {
                    switch(ev.type) {
                        case MidiEvent::NoteOn: 
                            LOGI("JNI: fluid_synth_noteon ch=%d note=%d vel=%d", ev.channel, ev.data1, ev.data2);
                            fluid_synth_noteon(synth, ev.channel, ev.data1, ev.data2); 
                            if (ev.data2 > 0) {
                                activeNotesCountPerChannel[ev.channel]++;
                                activeChannelsMask.fetch_or(1 << ev.channel);
                            } else {
                                if (--activeNotesCountPerChannel[ev.channel] <= 0) {
                                    activeNotesCountPerChannel[ev.channel] = 0;
                                    // Don't clear mask yet, wait for release trail
                                }
                            }
                            break;
                        case MidiEvent::NoteOff: 
                            fluid_synth_noteoff(synth, ev.channel, ev.data1); 
                            if (--activeNotesCountPerChannel[ev.channel] <= 0) {
                                activeNotesCountPerChannel[ev.channel] = 0;
                            }
                            break;
                        case MidiEvent::CC: fluid_synth_cc(synth, ev.channel, ev.data1, ev.data2); break;
                        case MidiEvent::PitchBend: fluid_synth_pitch_bend(synth, ev.channel, ev.data1); break;
                        case MidiEvent::ProgramChange: fluid_synth_program_change(synth, ev.channel, ev.data2); break;
                        case MidiEvent::BankSelect: fluid_synth_bank_select(synth, ev.channel, ev.data1); break;
                    }
                }
            }
            lock.lock();
        }
    }
}

/**
 * Custom Oboe Audio Engine
 */
class StageAudioEngine : public oboe::AudioStreamCallback {
public:
    StageAudioEngine() {
        for (int i = 0; i < 16; ++i) {
            leftPointers[i] = channelBuffersL[i];
            rightPointers[i] = channelBuffersR[i];
        }
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
        float *outputBuffer = static_cast<float *>(audioData);
        memset(outputBuffer, 0, numFrames * 2 * sizeof(float));

        if (numFrames > 4096) return oboe::DataCallbackResult::Continue;

        // 1. Snapshot de Dados e Lock de Renderização
        std::unique_lock<std::recursive_mutex> lock(engine_mutex, std::try_to_lock);
        if (!lock.owns_lock()) return oboe::DataCallbackResult::Continue;

        if (!synth || !dspChain) return oboe::DataCallbackResult::Continue;

        uint32_t currentMask = activeChannelsMask.load();
        bool hasTails = dspChain->hasActiveTails();

        // Sair cedo apenas se não houver NADA para tocar (Notas E Caudas)
        if (currentMask == 0 && !hasTails) {
            if (fluid_synth_get_active_voice_count(synth) == 0) return oboe::DataCallbackResult::Continue;
        }

        // Limpa acumuladores de frame
        dspChain->prepare(numFrames);

        // Renderização do FluidSynth (Ponto crítico)
        fluid_synth_nwrite_float(synth, numFrames, leftPointers, rightPointers, nullptr, nullptr);

        // Diagnóstico: Verificar se o synth produziu algo no ch=0 (UI Canal 1)
        {
            float synthLogMax = 0.0f;
            for(int k=0; k<numFrames; k++) {
                synthLogMax = std::max(synthLogMax, std::max(std::abs(channelBuffersL[0][k]), std::abs(channelBuffersR[0][k])));
            }
            if (synthLogMax > 0.0001f) {
                static int synthLogCount = 0;
                if (synthLogCount++ % 500 == 0) {
                    LOGI("SYNTH DIAG: ch=0 (Canal 1) produziu áudio! Peak=%.6f", synthLogMax);
                }
            }
        }

        // 2. Processar Canais e Mixar no Barramento Master
        memset(finalL, 0, 4096 * sizeof(float));
        memset(finalR, 0, 4096 * sizeof(float));
        
        uint32_t newMask = currentMask;
        for (int ch = 0; ch < 16; ++ch) {
            bool isBitSet = (currentMask & (1 << ch));
            float* outL = channelBuffersL[ch];
            float* outR = channelBuffersR[ch];
            
            float maxVal = 0.0f;
            // Processa se houver nota ativa OU se o canal ainda tinha sinal no frame anterior
            if (isBitSet || activeNotesCountPerChannel[ch] > 0) {
                if (!dspMasterBypass.load()) {
                    dspChain->processChannel(ch, outL, outR, numFrames);
                }
                
                // Mixagem para o Barramento Master (NEON)
                int i = 0;
                for (; i <= numFrames - 4; i += 4) {
                    float32x4_t chL = vld1q_f32(outL + i);
                    float32x4_t chR = vld1q_f32(outR + i);
                    float32x4_t masterL = vld1q_f32(finalL + i);
                    float32x4_t masterR = vld1q_f32(finalR + i);
                    
                    vst1q_f32(finalL + i, vaddq_f32(masterL, chL));
                    vst1q_f32(finalR + i, vaddq_f32(masterR, chR));
                    
                    float32x4_t maxLR = vmaxq_f32(vabsq_f32(chL), vabsq_f32(chR));
                    float curMax = vmaxvq_f32(maxLR);
                    if (curMax > maxVal) maxVal = curMax;
                }
                for (; i < numFrames; ++i) {
                    finalL[i] += outL[i];
                    finalR[i] += outR[i];
                    float a = std::max(fabsf(outL[i]), fabsf(outR[i]));
                    if (a > maxVal) maxVal = a;
                }

                channelPeaks[ch].store(maxVal);

                if (maxVal < 0.0001f && activeNotesCountPerChannel[ch] <= 0) {
                    newMask &= ~(1 << ch);
                } else {
                    newMask |= (1 << ch);
                }
            } else {
                channelPeaks[ch].store(0.0f);
            }
        }
        activeChannelsMask.store(newMask);
        
        // 3. Processar Master Rack sobre a Mistura Final
        dspChain->processMaster(finalL, finalR, numFrames);
        
        // 4. Intercalar Barramento para o OutputBuffer Oboe (NEON)
        int i = 0;
        for (; i <= numFrames - 4; i += 4) {
            float32x4_t mOutL = vld1q_f32(finalL + i);
            float32x4_t mOutR = vld1q_f32(finalR + i);
            float32x4x2_t interleaved;
            interleaved.val[0] = mOutL;
            interleaved.val[1] = mOutR;
            vst2q_f32(outputBuffer + i * 2, interleaved);
        }
        for (; i < numFrames; ++i) {
            outputBuffer[i*2] = finalL[i];
            outputBuffer[i*2 + 1] = finalR[i];
        }

        return oboe::DataCallbackResult::Continue;
    }

    void start(int sampleRate, int bufferSize, int deviceId) {
        oboe::AudioStreamBuilder builder;
        builder.setFormat(oboe::AudioFormat::Float)
               ->setChannelCount(oboe::ChannelCount::Stereo)
               ->setSampleRate(sampleRate)
               ->setFramesPerCallback(bufferSize)
               ->setSharingMode(oboe::SharingMode::Exclusive)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setCallback(this);
        if (deviceId >= 0) builder.setDeviceId(deviceId);
        oboe::Result result = builder.openStream(stream);
        if (result == oboe::Result::OK) stream->requestStart();
    }

    void stop() {
        if (stream) { stream->requestStop(); stream->close(); stream.reset(); }
    }

private:
    std::shared_ptr<oboe::AudioStream> stream;
    float channelBuffersL[16][4096] = {0.0f};
    float channelBuffersR[16][4096] = {0.0f};
    float finalL[4096] = {0.0f};
    float finalR[4096] = {0.0f};
    float* leftPointers[16];
    float* rightPointers[16];
};

static std::unique_ptr<StageAudioEngine> audioEngine = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeInit(
        JNIEnv* env, jobject thiz, jint sampleRate, jint bufferSize, jint deviceId) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    ::stk::Stk::setSampleRate((float)sampleRate);
    if (synth) { if (audioEngine) audioEngine->stop(); delete_fluid_synth(synth); delete_fluid_settings(settings); }
    settings = new_fluid_settings();
    fluid_settings_setnum(settings, "synth.sample-rate", (double)sampleRate);
    fluid_settings_setint(settings, "synth.polyphony", 128);
    fluid_settings_setint(settings, "synth.midi-channels", 16);
    fluid_settings_setint(settings, "synth.audio-channels", 16);
    fluid_settings_setint(settings, "synth.audio-groups", 16);
    fluid_settings_setnum(settings, "synth.gain", 0.6);
    synth = new_fluid_synth(settings);
    dspChain = std::make_unique<DSPChain>();
    dspChain->prepare(bufferSize, (float)sampleRate); // Define o sample rate inicial
    audioEngine = std::make_unique<StageAudioEngine>();
    audioEngine->start(sampleRate, bufferSize, deviceId);
    if (!midiThreadRunning.load()) { midiThreadRunning.store(true); midiThread = std::thread(midiProcessingLoop); }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeNoteOn(JNIEnv* env, jobject thiz, jint ch, jint key, jint vel) {
    std::lock_guard<std::mutex> lock(queueMutex);
    midiQueue.push_back({MidiEvent::NoteOn, (int)ch, (int)key, (int)vel});
    queueCv.notify_one();
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeNoteOff(JNIEnv* env, jobject thiz, jint ch, jint key) {
    std::lock_guard<std::mutex> lock(queueMutex);
    midiQueue.push_back({MidiEvent::NoteOff, (int)ch, (int)key, 0});
    queueCv.notify_one();
}

JNIEXPORT jboolean JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeUnloadSf2(JNIEnv* env, jobject thiz, jint sfId) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) return JNI_FALSE;
    return (fluid_synth_sfunload(synth, sfId, 1) == FLUID_OK) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeLoadSf2(JNIEnv* env, jobject thiz, jstring path) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) return -1;
    const char* sf2Path = env->GetStringUTFChars(path, nullptr);
    int sfId = fluid_synth_sfload(synth, sf2Path, 0);
    env->ReleaseStringUTFChars(path, sf2Path);
    return sfId;
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeGetChannelLevels(JNIEnv* env, jobject thiz, jfloatArray output) {
    float local_levels[16] = {0.0f};
    for (int i = 0; i < 16; i++) {
        local_levels[i] = channelPeaks[i].load();
    }
    env->SetFloatArrayRegion(output, 0, 16, local_levels);
}

JNIEXPORT jobjectArray JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeGetPresets(JNIEnv* env, jobject thiz, jint sfId) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    jclass stringClass = env->FindClass("java/lang/String");
    if (!synth) return env->NewObjectArray(0, stringClass, nullptr);
    fluid_sfont_t* sfont = fluid_synth_get_sfont_by_id(synth, sfId);
    if (!sfont) return env->NewObjectArray(0, stringClass, nullptr);
    int count = 0; fluid_sfont_iteration_start(sfont);
    while (fluid_sfont_iteration_next(sfont)) count++;
    jobjectArray result = env->NewObjectArray(count, stringClass, nullptr);
    fluid_sfont_iteration_start(sfont);
    fluid_preset_t* preset; int index = 0;
    while ((preset = fluid_sfont_iteration_next(sfont)) && index < count) {
        char buffer[256]; snprintf(buffer, sizeof(buffer), "%d|%d|%s", fluid_preset_get_banknum(preset), fluid_preset_get_num(preset), fluid_preset_get_name(preset));
        jstring jstr = env->NewStringUTF(buffer);
        env->SetObjectArrayElement(result, index++, jstr);
        env->DeleteLocalRef(jstr);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativePanic(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(queueMutex);
    LOGI("PANIC: Enqueuing full reset for all channels");
    for (int ch = 0; ch < 16; ch++) {
        // Sounds & Notes Off via CC
        midiQueue.push_back({MidiEvent::CC, ch, 120, 0}); // All Sound Off
        midiQueue.push_back({MidiEvent::CC, ch, 123, 0}); // All Notes Off
        
        // Essential CC Resets
        midiQueue.push_back({MidiEvent::CC, ch, 64, 0});  // Sustain Off
        midiQueue.push_back({MidiEvent::CC, ch, 1, 0});   // Modulation Off
        midiQueue.push_back({MidiEvent::CC, ch, 11, 127}); // Expression Max
        midiQueue.push_back({MidiEvent::CC, ch, 4, 127});  // Foot Ctrl Max
        
        // Pitch Bend Center
        midiQueue.push_back({MidiEvent::PitchBend, ch, 8192, 0});
    }
    queueCv.notify_one();
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeDestroy(JNIEnv* env, jobject thiz) {
    midiThreadRunning.store(false); queueCv.notify_all();
    if (midiThread.joinable()) midiThread.join();
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (audioEngine) audioEngine->stop();
    if (synth) delete_fluid_synth(synth);
    if (settings) delete_fluid_settings(settings);
    audioEngine.reset(); dspChain.reset(); synth = nullptr; settings = nullptr;
}

JNIEXPORT void JNICALL Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetVolume(JNIEnv* env, jobject thiz, jint ch, jfloat volDb) {
    std::lock_guard<std::mutex> lock(queueMutex);
    float normalized = (volDb <= -60.0f) ? 0.0f : (volDb + 60.0f) / 60.0f;
    midiQueue.push_back({MidiEvent::CC, (int)ch, 7, (int)(normalized * 127.0f)});
    queueCv.notify_one();
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeControlChange(JNIEnv* env, jobject thiz, jint ch, jint controller, jint value) {
    std::lock_guard<std::mutex> lock(queueMutex);
    midiQueue.push_back({MidiEvent::CC, (int)ch, (int)controller, (int)value});
    queueCv.notify_one();
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativePitchBend(JNIEnv* env, jobject thiz, jint ch, jint value) {
    std::lock_guard<std::mutex> lock(queueMutex);
    midiQueue.push_back({MidiEvent::PitchBend, (int)ch, (int)value, 0});
    queueCv.notify_one();
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeProgramChange(JNIEnv* env, jobject thiz, jint ch, jint bank, jint prog) {
    std::lock_guard<std::mutex> lock(queueMutex);
    if (bank >= 0) midiQueue.push_back({MidiEvent::BankSelect, (int)ch, (int)bank, 0});
    midiQueue.push_back({MidiEvent::ProgramChange, (int)ch, 0, (int)prog});
    queueCv.notify_one();
}

JNIEXPORT jboolean JNICALL Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeProgramSelect(JNIEnv* env, jobject thiz, jint ch, jint sfId, jint bank, jint prog) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) {
        LOGE("JNI: nativeProgramSelect FAILED (synth null) ch=%d sfId=%d bank=%d prog=%d", ch, sfId, bank, prog);
        return JNI_FALSE;
    }
    int result = fluid_synth_program_select(synth, ch, sfId, bank, prog);
    if (result == FLUID_OK) {
        LOGI("JNI: nativeProgramSelect SUCCESS ch=%d sfId=%d bank=%d prog=%d", ch, sfId, bank, prog);
        return JNI_TRUE;
    } else {
        LOGE("JNI: nativeProgramSelect FAILED (FluidSynth rejected it) ch=%d sfId=%d bank=%d prog=%d", ch, sfId, bank, prog);
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetInterpolation(JNIEnv* env, jobject thiz, jint method) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (synth) for (int ch = 0; ch < 16; ch++) fluid_synth_set_interp_method(synth, ch, method);
}

JNIEXPORT void JNICALL Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetPolyphony(JNIEnv* env, jobject thiz, jint voices) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (synth) fluid_synth_set_polyphony(synth, voices);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetChannelEqCutoff(JNIEnv* env, jobject thiz, jint ch, jfloat cutoff) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (dspChain) dspChain->setChannelEQ(ch, cutoff);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetChannelSendLevel(JNIEnv* env, jobject thiz, jint ch, jfloat level) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (dspChain) dspChain->setChannelSend(ch, level);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeAddEffect(JNIEnv* env, jobject thiz, jint ch, jint type) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (dspChain) dspChain->addEffect(ch, type);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeRemoveEffect(JNIEnv* env, jobject thiz, jint ch, jint index) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (dspChain) dspChain->removeEffect(ch, index);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeClearEffects(JNIEnv* env, jobject thiz, jint ch) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (dspChain) dspChain->clearEffects(ch);
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetEffectParam(JNIEnv* env, jobject thiz, jint ch, jint index, jint paramId, jfloat value) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (dspChain) {
        dspChain->setEffectParam(ch, index, paramId, value);
    }
}

JNIEXPORT void JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetEffectEnabled(JNIEnv* env, jobject thiz, jint ch, jint index, jboolean enabled) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (dspChain) {
        dspChain->setEffectEnabled(ch, index, enabled);
    }
}

JNIEXPORT void JNICALL Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetDspMasterBypass(JNIEnv* env, jobject thiz, jboolean bypass) {
    dspMasterBypass.store(bypass);
}

JNIEXPORT void JNICALL Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetMasterLimiter(JNIEnv* env, jobject thiz, jboolean enabled) {}
JNIEXPORT void JNICALL Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeWarmUpChannel(JNIEnv* env, jobject thiz, jint ch) {
    std::lock_guard<std::mutex> lock(queueMutex);
    LOGI("WARM-UP: Channel %d", (int)ch);
    // Silent note to trigger voice allocation and sample caching
    midiQueue.push_back({MidiEvent::NoteOn, (int)ch, 60, 0});
    midiQueue.push_back({MidiEvent::NoteOff, (int)ch, 60, 0});
    queueCv.notify_one();
}

JNIEXPORT jboolean JNICALL
Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeGetEffectMeters(
        JNIEnv* env, jobject thiz, jint ch, jint index, jfloatArray output) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!dspChain) return JNI_FALSE;
    
    float input = 0, outputVal = 0, gr = 0;
    dspChain->getEffectMeters(ch, index, &input, &outputVal, &gr);
    
    float local[3] = {input, outputVal, gr};
    env->SetFloatArrayRegion(output, 0, 3, local);
    return JNI_TRUE;
}

} // extern "C"
