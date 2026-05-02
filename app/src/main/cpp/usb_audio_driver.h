/**
 * usb_audio_driver.h — Driver USB Nativo com libusb (Fase 2+3)
 *
 * Fase 2: Parsing de descritores class-specific (UAC1/UAC2)
 *          → detecta nrChannels, subframeSize, bitResolution
 *          → claim da interface + set alternate setting
 *
 * Fase 3: Loop de transferências isócronas OUT (double-buffered)
 *          → double-buffer: 2 transfers sempre pending
 *          → Thread USB com SCHED_FIFO
 *          → Converte float (ring buffer) → USB byte format (16/24/32-bit)
 *          → Async sync: lê feedback endpoint para ajustar framesPerPacket
 */

#pragma once

#include <cstdint>
#include <atomic>
#include <thread>
#include <vector>
#include <cmath>

// ── libusb forward declarations ──────────────────────────────────────────────
// Evita incluir libusb.h no header (o .cpp faz o include completo).
// LIBUSB_CALL é vazio em Android/Linux; __cdecl apenas no Windows.
struct libusb_transfer;
#ifndef LIBUSB_CALL
#  define LIBUSB_CALL
#endif
// ─────────────────────────────────────────────────────────────────────────────

// Callback de preenchimento de áudio (implementado em fluidsynth_bridge.cpp)
// Lê do ring buffer e converte para interleaved float stereo.
using AudioFillCallback = bool(*)(float* buffer, int numFrames, int numChannels);

/**
 * Formato de áudio detectado dos descritores class-specific USB Audio.
 */
struct AudioFormat {
    uint32_t sampleRate    = 48000;
    uint8_t  nrChannels    = 2;       // Ex: 2 = estéreo
    uint8_t  subframeSize  = 2;       // bytes por sample por canal no pacote USB
    uint8_t  bitResolution = 16;      // Ex: 16, 24, 32
    int      packetsPerSec = 1000;    // 1000 = FS USB 1ms | 8000 = HS USB 125µs
    int      framesPerPacket = 48;    // nominal (pode variar ±1 com async sync)
    int      bytesPerPacket  = 192;   // framesPerPacket × nrChannels × subframeSize
};

/**
 * UsbAudioDriver
 *
 * Ciclo de vida:
 *   1. start(fd, sr, ch)   — probe + detecta endpoint isócrono OUT
 *   2. startStreaming(cb)  — claim + set alt setting + inicia transferências ISO
 *   3. stopStreaming()     — cancela transfers, libera interface
 *   4. stop()             — fecha device handle, exit libusb
 */
class UsbAudioDriver {
public:
    UsbAudioDriver();
    ~UsbAudioDriver();

    // ── Fase 1 ──────────────────────────────────────────────────────────────
    bool start(int fd, int sampleRate, int channels);
    void stop();
    bool isActive()     const;
    int  getSampleRate() const;

    // ── Fase 2+3 ────────────────────────────────────────────────────────────
    /**
     * Faz claim da interface, detecta formato de áudio e inicia as
     * isochronous OUT transfers. Deve ser chamado APÓS start().
     *
     * @param cb  Callback que fornece áudio do ring buffer (pode ser nullptr
     *            para teste com sine wave 440Hz).
     * @return true se o streaming foi iniciado com sucesso.
     */
    bool startStreaming(AudioFillCallback cb = nullptr);

    /**
     * Para o streaming, cancela transfers pendentes e libera a interface.
     * Thread-safe. Pode ser chamado de qualquer thread.
     */
    void stopStreaming();

    bool isStreaming() const { return streaming_.load(); }

    const AudioFormat& audioFormat() const { return fmt_; }

private:
    // ── Fase 2: Parsing de descritores ──────────────────────────────────────
    bool parseAudioDescriptors();
    void parseClassSpecificDescriptors();
    bool claimAudioInterface();
    void releaseAudioInterface();

    // ── Fase 3: Isochronous transfers ────────────────────────────────────────
    bool allocateTransfers();
    void freeTransfers();

    // Callback estático libusb → despacha para instância
    static void LIBUSB_CALL isoCallback(struct libusb_transfer* t);
    void handleCompletion(struct libusb_transfer* t);
    void fillAndSubmitTransfer(struct libusb_transfer* t);

    // Preenchimento de áudio e conversão de formato
    bool fillPacketFromAudio(uint8_t* buf, int numFrames);
    static void floatToUsb(float sample, uint8_t* dst, int subframeSize);

    // Loop de eventos USB (roda na usbThread_)
    void usbEventLoop();

    // Leitura de feedback endpoint (async sync Fase 3)
    void parseAsyncFeedback(uint32_t feedback);

    // ── Estado interno ───────────────────────────────────────────────────────
    void* ctx_       = nullptr;  // libusb_context*
    void* devHandle_ = nullptr;  // libusb_device_handle*

    std::atomic<bool> active_    {false};
    std::atomic<bool> streaming_ {false};
    std::atomic<bool> stopLoop_  {false};

    int sampleRate_ = 48000;
    int channels_   = 2;

    // Endpoint detectado na Fase 1
    uint8_t  isoOutEp_    = 0;
    uint8_t  isoFeedEp_   = 0;   // feedback endpoint (0 = não encontrado)
    uint16_t isoMaxPkt_   = 0;
    uint8_t  isoIface_    = 0;
    uint8_t  isoAltSet_   = 0;
    bool     ifaceClaimed_ = false;

    // Extra bytes da interface de saída (descritores class-specific)
    std::vector<uint8_t> ifaceExtra_;

    // Formato detectado
    AudioFormat fmt_;

    // Streaming
    AudioFillCallback renderCb_ = nullptr;

    static constexpr int NUM_TRANSFERS = 8;
    static constexpr int NUM_PACKETS_PER_XFER = 16;
    struct libusb_transfer* xfers_[NUM_TRANSFERS] = {};
    std::vector<uint8_t>    xferBufs_[NUM_TRANSFERS];

    struct libusb_transfer* feedXfers_[2] = {};
    std::vector<uint8_t>    feedBufs_[2];
    static void LIBUSB_CALL feedCallback(struct libusb_transfer* t);

    std::thread             usbThread_;
    std::atomic<int>        pendingXfers_{0};

    // Async sync: framesPerPacket adaptável
    std::atomic<int> adaptFrames_{0}; // DEPRECATED: still here just in case, but using fractionAcc_ now
    std::atomic<float> currentDeviceRate_{0.0f}; // Rate parsed from Feedback Endpoint
    float fractionAcc_{0.0f}; // Accumulates drift per-microframe in handleCompletion
    // Configurações de interface
    uint8_t ifaceIndex_ = 0;
    
    // Sine wave de teste (usado quando renderCb_ == nullptr)
    float sinePhase_ = 0.0f;
    static constexpr float SINE_FREQ = 440.0f;  // A4

    // Gerador de float estéreo (sine test ou ring buffer)
    bool getAudio(float* buf, int numFrames);
};
