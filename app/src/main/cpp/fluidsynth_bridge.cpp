#include <jni.h>
#include <android/log.h>
#include <android/trace.h>
#include <sys/types.h>
#include <sys/resource.h>
#include <unistd.h>
#include <time.h>
#include <sched.h>
#include <errno.h>
#include <stdio.h>
#include <string>
#include <mutex>
#include <deque>
#include <thread>
#include <condition_variable>
#include <atomic>
#include <math.h>
#include <dlfcn.h>

#include <oboe/Oboe.h>
#include "include/fluidsynth.h"
#include "include/fluidsynth/voice.h"
#include "include/fluidsynth/gen.h"
#include <arm_neon.h>
#include "Stk.h"
#include "dsp_chain.h"

#define LOG_TAG "StageAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace stage_audio;

// --- Globals (compartilhados com superpowered_usb_bridge.cpp via extern) ---
std::recursive_mutex engine_mutex;
static fluid_settings_t* settings = nullptr;
fluid_synth_t* synth = nullptr;
std::unique_ptr<DSPChain> dspChain = nullptr;
std::atomic<bool> dspMasterBypass{false};

// Ponteiro para verificar se o USB do Superpowered tomou o controle (via libspbridge.so)
typedef int (*SpIsActiveFn)();
static SpIsActiveFn spIsActiveFn = nullptr;

// --- Performance Tracking (compartilhado) ---
std::atomic<uint32_t> activeChannelsMask{0};
std::atomic<float> channelPeaks[16]; // Zero-cost meters
std::atomic<int> activeNotesCountPerChannel[16];

// --- Controle Remoto do APM HUD ---
std::atomic<int> apmUnderruns{0};
std::atomic<int> apmMutexMiss{0};
std::atomic<int> apmClips{0};
std::atomic<uint64_t> apmMaxCallbackNs{0};
std::atomic<uint64_t> apmTotalCallbackNs{0};
std::atomic<int> apmCallbackCount{0};

// --- APM Per-Phase Timing (acumuladores resetados a cada poll) ---
std::atomic<uint64_t> apmPhaseFluidNs{0};
std::atomic<uint64_t> apmPhaseDspChanNs{0};
std::atomic<uint64_t> apmPhaseDspMasterNs{0};
std::atomic<uint64_t> apmPhaseMixNs{0};
std::atomic<uint64_t> apmMaxPhaseFluidNs{0};
std::atomic<uint64_t> apmMaxPhaseDspChanNs{0};
std::atomic<uint64_t> apmMaxPhaseDspMasterNs{0};
std::atomic<uint64_t> apmMaxPhaseMixNs{0};
std::atomic<int> lastLockHolderTid{0};

// Helper para medição de fase: acumula tempo e atualiza máximo com CAS
#define MEASURE_PHASE(accCounter, maxCounter, traceName, code) do { \
    ATrace_beginSection(traceName); \
    struct timespec _ts1, _ts2; \
    clock_gettime(CLOCK_MONOTONIC, &_ts1); \
    code; \
    clock_gettime(CLOCK_MONOTONIC, &_ts2); \
    uint64_t _ns = (uint64_t)(_ts2.tv_sec - _ts1.tv_sec) * 1000000000ULL + \
                   (uint64_t)(_ts2.tv_nsec - _ts1.tv_nsec); \
    accCounter.fetch_add(_ns, std::memory_order_relaxed); \
    uint64_t _cur = maxCounter.load(std::memory_order_relaxed); \
    while (_ns > _cur && !maxCounter.compare_exchange_weak(_cur, _ns)) {} \
    ATrace_endSection(); \
} while(0)

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

// --- Asynchronous Audio Ring Buffer ---
#define RING_BUFFER_SIZE 16384
#define RING_BUFFER_MASK (RING_BUFFER_SIZE - 1)
static float synthRingBuffer[RING_BUFFER_SIZE];
static std::atomic<size_t> rbWriteIndex{0};
static std::atomic<size_t> rbReadIndex{0};
static std::atomic<bool> synthRenderThreadRunning{false};
static std::thread synthRenderThread;

static float* spLeftPtrs[16];
static float* spRightPtrs[16];
static float spChannelBuffersL[16][MAX_AUDIO_FRAME_SIZE];
static float spChannelBuffersR[16][MAX_AUDIO_FRAME_SIZE];
static float spFinalL[MAX_AUDIO_FRAME_SIZE];
static float spFinalR[MAX_AUDIO_FRAME_SIZE];

static void renderAudioEngine(float* outputBuffer, int numFrames) {
    if (numFrames > MAX_AUDIO_FRAME_SIZE) numFrames = MAX_AUDIO_FRAME_SIZE;
    for (int i = 0; i < 16; ++i) {
        spLeftPtrs[i] = spChannelBuffersL[i];
        spRightPtrs[i] = spChannelBuffersR[i];
    }
    
    std::unique_lock<std::recursive_mutex> lock(engine_mutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        apmMutexMiss.fetch_add(1);
        // NÃO logar aqui — este path roda no thread de áudio realtime do Superpowered.
        // __android_log_print tem locks internos e pode causar priority inversion.
        lock.lock();
    }
    lastLockHolderTid.store(gettid(), std::memory_order_relaxed);

    if (!synth || !dspChain) {
        memset(outputBuffer, 0, numFrames * 2 * sizeof(float));
        return;
    }

    MEASURE_PHASE(apmPhaseFluidNs, apmMaxPhaseFluidNs, "SM.Fluid", {
        fluid_synth_nwrite_float(synth, numFrames, spLeftPtrs, spRightPtrs, nullptr, nullptr);
    });

    memset(spFinalL, 0, numFrames * sizeof(float));
    memset(spFinalR, 0, numFrames * sizeof(float));

    MEASURE_PHASE(apmPhaseDspChanNs, apmMaxPhaseDspChanNs, "SM.DspChan", {
        dspChain->prepare(numFrames);
        uint32_t currentMask = activeChannelsMask.load();
        for (int ch = 0; ch < 16; ++ch) {
            bool isBitSet = (currentMask & (1 << ch));
            if (!isBitSet && activeNotesCountPerChannel[ch] <= 0) {
                channelPeaks[ch].store(0.0f);
                continue;
            }

            float* outL = spChannelBuffersL[ch];
            float* outR = spChannelBuffersR[ch];
            if (!dspMasterBypass.load()) {
                dspChain->processChannel(ch, outL, outR, numFrames);
            }

            float maxVal = 0.0f;
            for (int i = 0; i < numFrames; ++i) {
                maxVal = std::max(maxVal, std::max(std::abs(outL[i]), std::abs(outR[i])));
                spFinalL[i] += outL[i];
                spFinalR[i] += outR[i];
            }
            channelPeaks[ch].store(maxVal);
        }
    });

    MEASURE_PHASE(apmPhaseDspMasterNs, apmMaxPhaseDspMasterNs, "SM.DspMaster", {
        if (!dspMasterBypass.load()) {
            dspChain->processMaster(spFinalL, spFinalR, numFrames);
        }
    });

    float spMasterPeak = 0.0f;
    MEASURE_PHASE(apmPhaseMixNs, apmMaxPhaseMixNs, "SM.Mix", {
        for (int i = 0; i < numFrames; ++i) {
            spMasterPeak = std::max(spMasterPeak, std::max(std::abs(spFinalL[i]), std::abs(spFinalR[i])));
            outputBuffer[i*2] = spFinalL[i];
            outputBuffer[i*2 + 1] = spFinalR[i];
        }
    });

    if (spMasterPeak >= 1.0f) {
        apmClips.fetch_add(1);
    }
}

static void synthRenderLoop() {
    // ===== THREAD AFFINITY + REALTIME PRIORITY (runtime-detected big cluster) =====
    // 1. Detectar as cores mais rápidas via sysfs e pinar a todas elas.
    //    Funciona em qualquer SoC (Exynos, Snapdragon, MediaTek) sem hardcode.
    {
        cpu_set_t fastCores;
        CPU_ZERO(&fastCores);

        int cpuFreqs[16] = {0};
        int maxFreq = 0;
        int numCpus = 0;

        for (int cpu = 0; cpu < 16; cpu++) {
            char path[128];
            snprintf(path, sizeof(path),
                     "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", cpu);
            FILE* f = fopen(path, "r");
            if (!f) break;
            int freq = 0;
            if (fscanf(f, "%d", &freq) == 1) {
                cpuFreqs[cpu] = freq;
                if (freq > maxFreq) maxFreq = freq;
            }
            fclose(f);
            numCpus = cpu + 1;
        }

        // Adiciona ao set todas as cores com a freq máxima (big cluster inteiro).
        // Num Exynos 1380: 4× A78 @ 2.4GHz. Num Snapdragon 8 Gen 3: pode ser só 1 (X4 prime).
        int pinnedCount = 0;
        char pinnedList[64] = {0};
        int pinnedOffset = 0;
        for (int cpu = 0; cpu < numCpus; cpu++) {
            if (cpuFreqs[cpu] == maxFreq && maxFreq > 0) {
                CPU_SET(cpu, &fastCores);
                pinnedCount++;
                pinnedOffset += snprintf(pinnedList + pinnedOffset,
                                          sizeof(pinnedList) - pinnedOffset,
                                          "%s%d", (pinnedOffset > 0 ? "," : ""), cpu);
            }
        }

        if (pinnedCount > 0) {
            int r = sched_setaffinity(0, sizeof(cpu_set_t), &fastCores);
            if (r == 0) {
                LOGI("synthRenderLoop: pinned to %d fastest core(s) [%s] @ %d kHz (of %d total)",
                     pinnedCount, pinnedList, maxFreq, numCpus);
            } else {
                LOGW("synthRenderLoop: sched_setaffinity failed (errno=%d), using default", errno);
            }
        } else {
            LOGW("synthRenderLoop: failed to detect CPU frequencies (numCpus=%d), not pinning", numCpus);
        }
    }

    // 2. Tenta SCHED_FIFO (realtime) primeiro — pode falhar em Android sem permissão.
    //    Se falhar, fallback para nice -19 (máxima prioridade normal).
    {
        struct sched_param param;
        param.sched_priority = 1; // Prioridade FIFO mais baixa — ainda acima de SCHED_OTHER
        int r = sched_setscheduler(0, SCHED_FIFO, &param);
        if (r == 0) {
            LOGI("synthRenderLoop: SCHED_FIFO priority 1 acquired (realtime)");
        } else {
            // Fallback: nice mais negativo possível
            int n = setpriority(PRIO_PROCESS, 0, -19);
            if (n == 0) {
                LOGI("synthRenderLoop: SCHED_FIFO denied (errno=%d), using nice=-19 fallback", errno);
            } else {
                LOGW("synthRenderLoop: nice -19 also failed (errno=%d), using default priority", errno);
            }
        }
    }

    // 3. Log uma vez qual core estamos rodando (após o setup)
    LOGI("synthRenderLoop: entering loop on cpu=%d", sched_getcpu());
    // ================================================================================

    float tempBuffer[1024]; // 512 frames * 2 channels (stereo)
    while (synthRenderThreadRunning.load(std::memory_order_relaxed)) {
        if (!synth || !dspChain) {
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
            continue;
        }
        
        size_t w = rbWriteIndex.load(std::memory_order_relaxed);
        size_t r = rbReadIndex.load(std::memory_order_acquire);
        size_t bufferedFloats = w - r;
        
        // Mantém cerca de 2048 floats (1024 frames) pré-renderizados
        if (bufferedFloats < 2048) {
            int framesToRender = 128; // Bloco leve em cálculo constante
            int floatsToRender = framesToRender * 2;
            
            struct timespec start_ts, end_ts;
            clock_gettime(CLOCK_MONOTONIC, &start_ts);

            ATrace_beginSection("SM.RenderTotal");
            renderAudioEngine(tempBuffer, framesToRender);
            ATrace_endSection();

            clock_gettime(CLOCK_MONOTONIC, &end_ts);
            uint64_t ns = (end_ts.tv_sec - start_ts.tv_sec) * 1000000000ULL + (end_ts.tv_nsec - start_ts.tv_nsec);
            apmTotalCallbackNs.fetch_add(ns);
            apmCallbackCount.fetch_add(1);
            uint64_t currentMax = apmMaxCallbackNs.load();
            while (ns > currentMax && !apmMaxCallbackNs.compare_exchange_weak(currentMax, ns)) {}
            
            // Grava no RingBuffer
            for (int i = 0; i < floatsToRender; ++i) {
                synthRingBuffer[(w + i) & RING_BUFFER_MASK] = tempBuffer[i];
            }
            rbWriteIndex.store(w + floatsToRender, std::memory_order_release);
        } else {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }
}

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
    StageAudioEngine() {}

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
        float *outputBuffer = static_cast<float *>(audioData);
        if (spIsActiveFn && spIsActiveFn() == 1) {
            memset(audioData, 0, numFrames * 2 * sizeof(float));
            return oboe::DataCallbackResult::Continue;
        }

        size_t w = rbWriteIndex.load(std::memory_order_acquire);
        size_t r = rbReadIndex.load(std::memory_order_relaxed);
        size_t availableFloats = w - r;
        size_t requestedFloats = numFrames * 2;
        
        if (availableFloats >= requestedFloats) {
            for (size_t i = 0; i < requestedFloats; ++i) {
                outputBuffer[i] = synthRingBuffer[(r + i) & RING_BUFFER_MASK];
            }
            rbReadIndex.store(r + requestedFloats, std::memory_order_release);
        } else {
            apmUnderruns.fetch_add(1);
            memset(outputBuffer, 0, requestedFloats * sizeof(float));
        }
        return oboe::DataCallbackResult::Continue;
    }


    oboe::Result start(int sampleRate, int bufferSize, int deviceId) {
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
        if (result == oboe::Result::OK) {
            result = stream->requestStart();
            if (result != oboe::Result::OK) {
                LOGE("Oboe stream requestStart FAILED: %s", oboe::convertToText(result));
            }
        } else {
            LOGE("Oboe openStream FAILED: %s (SR=%d, Buf=%d, Dev=%d)", 
                 oboe::convertToText(result), sampleRate, bufferSize, deviceId);
        }
        return result;
    }

    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
        LOGE("OBOE ERROR AFTER CLOSE: %s. Stream is now dead.", oboe::convertToText(error));
        streamDead.store(true);
    }
    
    bool isStreamDead() const { return streamDead.load(); }
    void resetStreamDead() { streamDead.store(false); }

    void stop() {
        if (stream) { stream->requestStop(); stream->close(); stream.reset(); }
    }

private:
    std::shared_ptr<oboe::AudioStream> stream;
    std::atomic<bool> streamDead{false};
};

static std::unique_ptr<StageAudioEngine> audioEngine = nullptr;

// =====================================================================
// Render callback para o Superpowered USB bridge (C puro)
// Chamado pelo libspbridge.so quando Superpowered precisa de áudio
static bool sp_render_audio(float* audioIO, int numFrames, int numChannels) {
    if (numChannels != 2) return false;
    
    size_t w = rbWriteIndex.load(std::memory_order_acquire);
    size_t r = rbReadIndex.load(std::memory_order_relaxed);
    size_t availableFloats = w - r;
    size_t requestedFloats = numFrames * 2;
    
    if (availableFloats >= requestedFloats) {
        for (size_t i = 0; i < requestedFloats; ++i) {
            audioIO[i] = synthRingBuffer[(r + i) & RING_BUFFER_MASK];
        }
        rbReadIndex.store(r + requestedFloats, std::memory_order_release);
    } else {
        apmUnderruns.fetch_add(1);
        memset(audioIO, 0, requestedFloats * sizeof(float));
    }
    return true;
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeInit(
        JNIEnv* env, jobject thiz, jint sampleRate, jint bufferSize, jint deviceId) {
    // 1. Pare o Oboe ANTES de travar o Mutex para não dar Deadlock com o onAudioReady!
    if (audioEngine) {
        audioEngine->stop();
    }
    
    // 2. Agora sim trava o motor para reconstruir o FluidSynth e os Pointers
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    ::stk::Stk::setSampleRate((float)sampleRate);
    
    if (synth) { 
        delete_fluid_synth(synth); 
        delete_fluid_settings(settings); 
    }
    settings = new_fluid_settings();
    fluid_settings_setnum(settings, "synth.sample-rate", (double)sampleRate);
    fluid_settings_setint(settings, "synth.polyphony", 80);
    fluid_settings_setint(settings, "synth.midi-channels", 16);
    fluid_settings_setint(settings, "synth.audio-channels", 16);
    fluid_settings_setint(settings, "synth.audio-groups", 16);

    // ========== CRITICAL SETTINGS FOR MOBILE LOW-LATENCY ==========
    // Preload de samples — evita page faults/disk I/O durante render.
    // Sem isso, FluidSynth mmap-eia o SF2 e carrega samples on-demand,
    // causando spikes de 1-9ms no hot path (principal causa dos clicks).
    fluid_settings_setint(settings, "synth.dynamic-sample-loading", 0);

    // Single-threaded: evita wakeup latency de worker threads internas
    // (pthread_cond_signal dentro do callback causa spikes de 1-5ms).
    fluid_settings_setint(settings, "synth.cpu-cores", 1);

    // Desabilitar reverb/chorus interno ANTES da criação do synth.
    // Fazemos nosso próprio reverb/chorus no DSPChain externo.
    fluid_settings_setint(settings, "synth.reverb.active", 0);
    fluid_settings_setint(settings, "synth.chorus.active", 0);

    // Hard-kill chorus: zera parâmetros para evitar qualquer processamento residual
    fluid_settings_setnum(settings, "synth.chorus.depth", 0.0);
    fluid_settings_setnum(settings, "synth.chorus.level", 0.0);
    fluid_settings_setint(settings, "synth.chorus.nr", 0);

    // Voice overflow tuning — priorizar stealing de vozes em release
    // em vez de vozes ativas (stealing menos audível).
    // IMPORTANTE: overflow.* são parâmetros NUMERIC (double) no FluidSynth,
    // NÃO int. Usar setint resulta em "Unknown integer parameter" e é ignorado.
    //
    // Voice overflow scoring: voz com MENOR score é roubada primeiro.
    // Valores NEGATIVOS diminuem o score → voz mais provável de ser roubada.
    // Valores POSITIVOS aumentam o score → voz protegida.
    fluid_settings_setnum(settings, "synth.overflow.percussion", 4000.0); // protege drums (canal 10)
    fluid_settings_setnum(settings, "synth.overflow.released", -2000.0);  // vozes em release → ROUBAR PRIMEIRO (decay natural, menos audível)
    fluid_settings_setnum(settings, "synth.overflow.sustained", -1000.0); // vozes sustentadas → roubar depois das released
    fluid_settings_setnum(settings, "synth.overflow.age", 1000.0);        // vozes antigas (em decay) recebem bônus inverso (default FluidSynth)
    fluid_settings_setnum(settings, "synth.overflow.volume", 500.0);      // vozes com volume alto recebem leve proteção
    // ================================================================

    // CPU SAVERS FOR ULTRA-LOW LATENCY (SUPERPOWERED)
    fluid_settings_setnum(settings, "synth.gain", 0.6);
    synth = new_fluid_synth(settings);

    // Redundância defensiva — já definimos nos settings, mas garantimos aqui
    fluid_synth_set_reverb_on(synth, 0);
    fluid_synth_set_chorus_on(synth, 0);
    fluid_synth_set_interp_method(synth, -1, 4); // Força Interpolação de 4ª Ordem (Rápida/Segura)
    
    dspChain = std::make_unique<DSPChain>();
    dspChain->prepare(bufferSize, (float)sampleRate); 
    audioEngine = std::make_unique<StageAudioEngine>();
    oboe::Result result = audioEngine->start(sampleRate, bufferSize, deviceId);
    
    if (!midiThreadRunning.load()) { 
        midiThreadRunning.store(true); 
        midiThread = std::thread(midiProcessingLoop); 
    }
    
    if (!synthRenderThreadRunning.load()) {
        synthRenderThreadRunning.store(true);
        synthRenderThread = std::thread(synthRenderLoop);
    }
    
    return (jint)result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeGetAudioStats(JNIEnv* env, jobject thiz) {
    jfloatArray result = env->NewFloatArray(14);
    if (result) {
        float stats[14] = {0.0f};

        int count = apmCallbackCount.load();
        uint64_t totalNs = apmTotalCallbackNs.load();
        float divCount = count > 0 ? (float)count : 1.0f;

        stats[0] = (float)apmUnderruns.load();
        stats[1] = (float)apmMutexMiss.load();
        stats[2] = (float)apmClips.load();
        stats[3] = count > 0 ? (float)(totalNs / count / 1000.0) : 0.0f; // Avg Callback Us
        stats[4] = (float)(apmMaxCallbackNs.load() / 1000.0); // Max Callback Us
        stats[5] = synth ? (float)fluid_synth_get_active_voice_count(synth) : 0.0f;

        // Per-phase avg (ns totais / count / 1000 = µs médio)
        stats[6]  = (float)(apmPhaseFluidNs.load()     / divCount / 1000.0f);
        stats[7]  = (float)(apmMaxPhaseFluidNs.load()  / 1000.0);
        stats[8]  = (float)(apmPhaseDspChanNs.load()   / divCount / 1000.0f);
        stats[9]  = (float)(apmMaxPhaseDspChanNs.load()/ 1000.0);
        stats[10] = (float)(apmPhaseDspMasterNs.load() / divCount / 1000.0f);
        stats[11] = (float)(apmMaxPhaseDspMasterNs.load() / 1000.0);
        stats[12] = (float)(apmPhaseMixNs.load()       / divCount / 1000.0f);
        stats[13] = (float)(apmMaxPhaseMixNs.load()    / 1000.0);

        // Reset counters for next interval (Only temporal metrics)
        apmCallbackCount.store(0);
        apmTotalCallbackNs.store(0);
        apmMaxCallbackNs.store(0);
        apmPhaseFluidNs.store(0);
        apmPhaseDspChanNs.store(0);
        apmPhaseDspMasterNs.store(0);
        apmPhaseMixNs.store(0);
        apmMaxPhaseFluidNs.store(0);
        apmMaxPhaseDspChanNs.store(0);
        apmMaxPhaseDspMasterNs.store(0);
        apmMaxPhaseMixNs.store(0);

        env->SetFloatArrayRegion(result, 0, 14, stats);
    }
    return result;
}

// Registra o render callback do synthmodule no libspbridge.so via dlopen
JNIEXPORT jboolean JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeRegisterSpBridge(
    JNIEnv* env, jobject thiz
) {
    // dlopen libspbridge.so (já carregada pelo SuperpoweredUSBAudioManager)
    void* handle = dlopen("libspbridge.so", RTLD_NOW | RTLD_NOLOAD);
    if (!handle) {
        // Não carregada ainda — tentar carregar
        handle = dlopen("libspbridge.so", RTLD_NOW);
    }
    if (!handle) {
        LOGW("sp_bridge: libspbridge.so not available (USB module not loaded): %s", dlerror());
        return JNI_FALSE;
    }
    
    // Buscar a função de registro
    typedef void (*RegisterFn)(bool (*)(float*, int, int));
    RegisterFn registerFn = (RegisterFn)dlsym(handle, "sp_register_render_callback");
    
    // Buscar a função de check ativo
    spIsActiveFn = (SpIsActiveFn)dlsym(handle, "sp_is_active");
    
    if (!registerFn) {
        LOGE("sp_bridge: sp_register_render_callback not found in libspbridge.so: %s", dlerror());
        return JNI_FALSE;
    }
    
    // Registrar nosso render callback
    registerFn(sp_render_audio);
    LOGI("sp_bridge: Render callback registered in libspbridge.so — USB audio ready");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeNoteOn(JNIEnv* env, jobject thiz, jint ch, jint key, jint vel) {
    std::lock_guard<std::mutex> lock(queueMutex);
    midiQueue.push_back({MidiEvent::NoteOn, (int)ch, (int)key, (int)vel});
    queueCv.notify_one();
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeNoteOff(JNIEnv* env, jobject thiz, jint ch, jint key) {
    std::lock_guard<std::mutex> lock(queueMutex);
    midiQueue.push_back({MidiEvent::NoteOff, (int)ch, (int)key, 0});
    queueCv.notify_one();
}

JNIEXPORT jboolean JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeUnloadSf2(JNIEnv* env, jobject thiz, jint sfId) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (!synth) return JNI_FALSE;
    return (fluid_synth_sfunload(synth, sfId, 1) == FLUID_OK) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeLoadSf2(JNIEnv* env, jobject thiz, jstring path) {
    if (!synth) return -1;
    const char* sf2Path = env->GetStringUTFChars(path, nullptr);
    int sfId = fluid_synth_sfload(synth, sf2Path, 0); // Totalmente Thread-Safe segundo a lib
    env->ReleaseStringUTFChars(path, sf2Path);
    return sfId;
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeGetChannelLevels(JNIEnv* env, jobject thiz, jfloatArray output) {
    float local_levels[16] = {0.0f};
    for (int i = 0; i < 16; i++) {
        local_levels[i] = channelPeaks[i].load();
    }
    env->SetFloatArrayRegion(output, 0, 16, local_levels);
}

JNIEXPORT jobjectArray JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeGetPresets(JNIEnv* env, jobject thiz, jint sfId) {
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
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativePanic(JNIEnv* env, jobject thiz) {
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
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeDestroy(JNIEnv* env, jobject thiz) {
    midiThreadRunning.store(false); queueCv.notify_all();
    if (midiThread.joinable()) midiThread.join();
    
    synthRenderThreadRunning.store(false);
    if (synthRenderThread.joinable()) synthRenderThread.join();
    
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (audioEngine) audioEngine->stop();
    if (synth) delete_fluid_synth(synth);
    if (settings) delete_fluid_settings(settings);
    audioEngine.reset(); dspChain.reset(); synth = nullptr; settings = nullptr;
}

JNIEXPORT void JNICALL Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeSetVolume(JNIEnv* env, jobject thiz, jint ch, jfloat volDb) {
    std::lock_guard<std::mutex> lock(queueMutex);
    float normalized = (volDb <= -60.0f) ? 0.0f : (volDb + 60.0f) / 60.0f;
    midiQueue.push_back({MidiEvent::CC, (int)ch, 7, (int)(normalized * 127.0f)});
    queueCv.notify_one();
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeControlChange(JNIEnv* env, jobject thiz, jint ch, jint controller, jint value) {
    std::lock_guard<std::mutex> lock(queueMutex);
    midiQueue.push_back({MidiEvent::CC, (int)ch, (int)controller, (int)value});
    queueCv.notify_one();
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativePitchBend(JNIEnv* env, jobject thiz, jint ch, jint value) {
    std::lock_guard<std::mutex> lock(queueMutex);
    midiQueue.push_back({MidiEvent::PitchBend, (int)ch, (int)value, 0});
    queueCv.notify_one();
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeProgramChange(JNIEnv* env, jobject thiz, jint ch, jint bank, jint prog) {
    std::lock_guard<std::mutex> lock(queueMutex);
    if (bank >= 0) midiQueue.push_back({MidiEvent::BankSelect, (int)ch, (int)bank, 0});
    midiQueue.push_back({MidiEvent::ProgramChange, (int)ch, 0, (int)prog});
    queueCv.notify_one();
}

JNIEXPORT jboolean JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeIsStreamDead(
        JNIEnv* env, jobject thiz) {
    if (audioEngine) {
        bool isDead = audioEngine->isStreamDead();
        if (isDead) {
            LOGW("JNI: nativeIsStreamDead RETURNING TRUE");
        }
        return isDead ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeResetStreamDead(
        JNIEnv* env, jobject thiz) {
    LOGI("JNI: nativeResetStreamDead CALLED");
    if (audioEngine) audioEngine->resetStreamDead();
}

JNIEXPORT jboolean JNICALL Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeProgramSelect(JNIEnv* env, jobject thiz, jint ch, jint sfId, jint bank, jint prog) {
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

JNIEXPORT void JNICALL Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeSetInterpolation(JNIEnv* env, jobject thiz, jint method) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (synth) for (int ch = 0; ch < 16; ch++) fluid_synth_set_interp_method(synth, ch, method);
}

JNIEXPORT void JNICALL Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeSetPolyphony(JNIEnv* env, jobject thiz, jint voices) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex);
    if (synth) fluid_synth_set_polyphony(synth, voices);
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeSetChannelEqCutoff(JNIEnv* env, jobject thiz, jint ch, jfloat cutoff) {
    // Lock-free: setParam() escreve em std::atomic/SmoothedParam
    if (dspChain) dspChain->setChannelEQ(ch, cutoff);
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeSetChannelSendLevel(JNIEnv* env, jobject thiz, jint ch, jfloat level) {
    // Lock-free: setParam() escreve em std::atomic/SmoothedParam
    if (dspChain) dspChain->setChannelSend(ch, level);
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeAddEffect(JNIEnv* env, jobject thiz, jint ch, jint type) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex); // Mantém: modifica estrutura (vector push)
    if (dspChain) dspChain->addEffect(ch, type);
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeRemoveEffect(JNIEnv* env, jobject thiz, jint ch, jint index) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex); // Mantém: modifica estrutura (vector erase)
    if (dspChain) dspChain->removeEffect(ch, index);
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeClearEffects(JNIEnv* env, jobject thiz, jint ch) {
    std::lock_guard<std::recursive_mutex> lock(engine_mutex); // Mantém: modifica estrutura (vector clear)
    if (dspChain) dspChain->clearEffects(ch);
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeSetEffectParam(JNIEnv* env, jobject thiz, jint ch, jint index, jint paramId, jfloat value) {
    // Lock-free: setParam() escreve em std::atomic/SmoothedParam, lido pelo audio thread sem lock
    if (dspChain) {
        dspChain->setEffectParam(ch, index, paramId, value);
    }
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeSetEffectEnabled(JNIEnv* env, jobject thiz, jint ch, jint index, jboolean enabled) {
    // Lock-free: `enabled` é um bool simples, escrito pela UI e lido pelo audio thread
    if (dspChain) {
        dspChain->setEffectEnabled(ch, index, enabled);
    }
}

JNIEXPORT void JNICALL Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeSetDspMasterBypass(JNIEnv* env, jobject thiz, jboolean bypass) {
    dspMasterBypass.store(bypass);
}

JNIEXPORT void JNICALL Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeSetMasterLimiter(JNIEnv* env, jobject thiz, jboolean enabled) {}
JNIEXPORT void JNICALL Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeWarmUpChannel(JNIEnv* env, jobject thiz, jint ch) {
    std::lock_guard<std::mutex> lock(queueMutex);
    LOGI("WARM-UP: Channel %d", (int)ch);
    // Silent note to trigger voice allocation and sample caching
    midiQueue.push_back({MidiEvent::NoteOn, (int)ch, 60, 0});
    midiQueue.push_back({MidiEvent::NoteOff, (int)ch, 60, 0});
    queueCv.notify_one();
}

JNIEXPORT jboolean JNICALL
Java_com_marceloferlan_stagemobile_audio_engine_FluidSynthEngine_nativeGetEffectMeters(
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
