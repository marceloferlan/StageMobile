/**
 * sp_bridge.cpp — Superpowered USB Audio Bridge (isolado com c++_static)
 * 
 * Este módulo roda numa lib separada (libspbridge.so) com c++_static,
 * isolado do synthmodule.so que usa c++_shared (Oboe).
 * 
 * Toda comunicação com o motor de áudio acontece via um callback C puro
 * registrado pelo synthmodule, evitando qualquer conflito de ABI.
 */

#include <jni.h>
#include <android/log.h>
#include <atomic>

#include <SuperpoweredAndroidUSB.h>
#include <SuperpoweredCPU.h>
#include <Superpowered.h>

#include "sp_bridge.h"

#define SP_TAG "SuperpoweredUSB"
#define SP_LOGI(...) __android_log_print(ANDROID_LOG_INFO, SP_TAG, __VA_ARGS__)
#define SP_LOGW(...) __android_log_print(ANDROID_LOG_WARN, SP_TAG, __VA_ARGS__)
#define SP_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, SP_TAG, __VA_ARGS__)

// =====================================================================
// Estado global
// =====================================================================
static std::atomic<bool> spInitialized{false};
static std::atomic<bool> spUsbActive{false};
static std::atomic<int>  spUsbDeviceId{-1};
static std::atomic<int>  spUsbSampleRate{48000};

// Callback registrado pelo synthmodule para renderização de áudio
static SpRenderCallback gRenderCallback = nullptr;

// =====================================================================
// C API — comunicação cross-library
// =====================================================================
extern "C" {

void sp_register_render_callback(SpRenderCallback callback) {
    gRenderCallback = callback;
    SP_LOGI("Render callback registered from synthmodule");
}

int sp_is_active(void) {
    return spUsbActive.load() ? 1 : 0;
}

int sp_get_sample_rate(void) {
    return spUsbSampleRate.load();
}

} // extern "C"

// =====================================================================
// Callback de áudio Superpowered — chamado pela interface USB
// =====================================================================
static bool superpoweredAudioProcessing(
    void * __unused clientdata,
    int __unused deviceID,
    float *audioIO,
    int numberOfFrames,
    int samplerate,
    int __unused numInputChannels,
    int numOutputChannels
) {
    // Cleanup callback (IO closing)
    if (!audioIO) return true;
    
    // Atualizar sample rate
    spUsbSampleRate.store(samplerate);
    
    // Chamar o render callback registrado pelo synthmodule
    if (gRenderCallback) {
        bool rendered = gRenderCallback(audioIO, numberOfFrames, numOutputChannels);
        if (!rendered) {
            // Render retornou false → silêncio
            memset(audioIO, 0, numberOfFrames * numOutputChannels * sizeof(float));
        }
    } else {
        // Sem callback registrado → silêncio
        memset(audioIO, 0, numberOfFrames * numOutputChannels * sizeof(float));
    }
    
    return true;
}

// =====================================================================
// Inicialização lazy do Superpowered
// =====================================================================
static void ensureSuperpoweredInitialized() {
    if (spInitialized.load()) return;
    
    SP_LOGI("Initializing Superpowered SDK (DynamicInitialize, c++_static lib)...");
    Superpowered::DynamicInitialize("ExampleLicenseKey-WillExpire-OnNextUpdate");
    Superpowered::AndroidUSB::initialize(nullptr, nullptr, nullptr, nullptr, nullptr);
    spInitialized.store(true);
    SP_LOGI("Superpowered SDK + USB initialized successfully");
}

// =====================================================================
// JNI Bridge — Chamado pelo SuperpoweredUSBAudioManager.kt
// =====================================================================
extern "C" {

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_superpowered_SuperpoweredUSBAudioManager_nativeInitialize(
    JNIEnv* env, jobject thiz, jstring licenseKey
) {
    SP_LOGI("Superpowered USB Manager registered — init on first USB connect");
}

JNIEXPORT jint JNICALL
Java_com_marceloferlan_stagemobile_superpowered_SuperpoweredUSBAudioManager_nativeOnConnect(
    JNIEnv *env, jobject thiz, jint deviceID, jint fd, jbyteArray rawDescriptor
) {
    ensureSuperpoweredInitialized();
    
    jbyte *rd = env->GetByteArrayElements(rawDescriptor, nullptr);
    int dataBytes = env->GetArrayLength(rawDescriptor);
    int r = Superpowered::AndroidUSB::onConnect(deviceID, fd, (unsigned char *)rd, dataBytes);
    env->ReleaseByteArrayElements(rawDescriptor, rd, JNI_ABORT);
    
    SP_LOGI("USB onConnect: deviceID=%d, result=%d (0=Audio+MIDI, 1=Audio, 2=MIDI, 3=None)", deviceID, r);
    
    // OnConnectReturn: 0=AudioAndMIDI, 1=AudioOnly, 2=MIDIOnly, 3=NotRecognized
    bool hasAudio = (r == 0 || r == 1);
    
    if (hasAudio) {
        Superpowered::CPU::setSustainedPerformanceMode(true);
        
        bool started = Superpowered::AndroidUSBAudio::easyIO(
            deviceID,
            48000,                                              // sample rate
            24,                                                 // bits per sample
            0,                                                  // input channels
            2,                                                  // output channels (stereo)
            Superpowered::AndroidUSBAudioBufferSize_Low,        // 256 frames
            nullptr,                                            // clientdata
            superpoweredAudioProcessing                         // callback
        );
        
        if (started) {
            spUsbActive.store(true);
            spUsbDeviceId.store(deviceID);
            SP_LOGI("Superpowered USB Audio STARTED on device %d (48kHz/24bit/Stereo/256 frames)", deviceID);
        } else {
            SP_LOGE("Superpowered USB Audio FAILED to start on device %d", deviceID);
        }
    }
    
    bool hasMidi = (r == 0 || r == 2);
    if (hasMidi) {
        SP_LOGI("USB MIDI device detected on deviceID=%d (not started — using Android MidiManager)", deviceID);
    }
    
    return r;
}

JNIEXPORT void JNICALL
Java_com_marceloferlan_stagemobile_superpowered_SuperpoweredUSBAudioManager_nativeOnDisconnect(
    JNIEnv *env, jobject thiz, jint deviceID
) {
    SP_LOGI("USB onDisconnect: deviceID=%d", deviceID);
    
    if (spUsbDeviceId.load() == deviceID) {
        Superpowered::AndroidUSBAudio::stopIO(deviceID);
        spUsbActive.store(false);
        spUsbDeviceId.store(-1);
        Superpowered::CPU::setSustainedPerformanceMode(false);
        SP_LOGI("Superpowered USB Audio STOPPED");
    }
    
    Superpowered::AndroidUSB::onDisconnect(deviceID);
}

JNIEXPORT jboolean JNICALL
Java_com_marceloferlan_stagemobile_superpowered_SuperpoweredUSBAudioManager_nativeIsActive(
    JNIEnv *env, jobject thiz
) {
    return spUsbActive.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_marceloferlan_stagemobile_superpowered_SuperpoweredUSBAudioManager_nativeGetSampleRate(
    JNIEnv *env, jobject thiz
) {
    return spUsbSampleRate.load();
}

} // extern "C"
