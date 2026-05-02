/**
 * usb_audio_driver.cpp — Driver USB Nativo (Fase 2+3)
 *
 * Fase 2: Parsing UAC1/UAC2 class-specific descriptors
 *          → detecta nrChannels, subframeSize, bitResolution
 *          → claim da interface + set alternate setting
 *
 * Fase 3: Isochronous OUT transfers (double-buffered)
 *          → thread USB com SCHED_FIFO prio 1
 *          → async sync: lê feedback endpoint (EP IN implicit)
 *          → converte float [-1,1] → 16/24/32-bit LE
 */

#include "usb_audio_driver.h"

extern "C" {
#include <libusb.h>
}

#include <android/log.h>
#include <cstring>
#include <cstdio>
#include <cmath>
#include <algorithm>
#include <atomic>
#include <sched.h>
#include <sys/resource.h>

// ── Logging ──────────────────────────────────────────────────────────────────
#define USB_TAG  "UsbAudioDriver"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  USB_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  USB_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, USB_TAG, __VA_ARGS__)
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
static constexpr uint8_t USB_CLASS_AUDIO           = 1;
static constexpr uint8_t USB_SUBCLASS_AUDIOSTREAMING = 2;
static constexpr uint8_t CS_INTERFACE              = 0x24;
static constexpr uint8_t AS_GENERAL                = 0x01;
static constexpr uint8_t FORMAT_TYPE               = 0x02;
static constexpr uint8_t FORMAT_TYPE_I             = 0x01;
static constexpr uint8_t PROTO_UAC2               = 0x20;

// ─────────────────────────────────────────────────────────────────────────────
// Ctors / Dtors
// ─────────────────────────────────────────────────────────────────────────────
UsbAudioDriver::UsbAudioDriver()  = default;
UsbAudioDriver::~UsbAudioDriver() { stopStreaming(); stop(); }

// ─────────────────────────────────────────────────────────────────────────────
// start() — Fase 1 (mantida integralmente) + salva extra bytes
// ─────────────────────────────────────────────────────────────────────────────
bool UsbAudioDriver::start(int fd, int sampleRate, int channels) {
    stopStreaming();
    stop();

    sampleRate_ = sampleRate;
    channels_   = channels;

    // ── 1. Init libusb ───────────────────────────────────────────────────────
    libusb_context* context = nullptr;
    int r = libusb_init(&context);
    if (r != LIBUSB_SUCCESS) {
        LOGE("libusb_init failed: %s", libusb_strerror((libusb_error)r));
        return false;
    }
    ctx_ = context;
#ifndef NDEBUG
    libusb_set_option(context, LIBUSB_OPTION_LOG_LEVEL, LIBUSB_LOG_LEVEL_WARNING);
#endif
    LOGI("libusb_init OK — api_version=0x%08x", LIBUSB_API_VERSION);

    // ── 2. Abrir device via fd Android ──────────────────────────────────────
    libusb_device_handle* handle = nullptr;
    r = libusb_wrap_sys_device(context, (intptr_t)fd, &handle);
    if (r != LIBUSB_SUCCESS) {
        LOGE("libusb_wrap_sys_device(fd=%d) failed: %s", fd, libusb_strerror((libusb_error)r));
        libusb_exit(context); ctx_ = nullptr;
        return false;
    }
    devHandle_ = handle;

    struct libusb_device_descriptor desc;
    libusb_device* dev = libusb_get_device(handle);
    if (libusb_get_device_descriptor(dev, &desc) == LIBUSB_SUCCESS) {
        LOGI("USB device opened: bus=%d addr=%d VID=%04x PID=%04x",
             libusb_get_bus_number(dev), libusb_get_device_address(dev),
             desc.idVendor, desc.idProduct);
    }

    // ── 3. Parse descritores de endpoint ─────────────────────────────────────
    bool hasAudio = parseAudioDescriptors();
    if (hasAudio) {
        active_.store(true);
        LOGI("UsbAudioDriver ACTIVE — endpoint=0x%02x maxPkt=%d sr=%dHz ch=%d",
             isoOutEp_, isoMaxPkt_, sampleRate_, channels_);
    } else {
        LOGW("UsbAudioDriver: nenhum endpoint isócrono OUT de áudio encontrado.");
        stop();
    }
    return hasAudio;
}

// ─────────────────────────────────────────────────────────────────────────────
// stop()
// ─────────────────────────────────────────────────────────────────────────────
void UsbAudioDriver::stop() {
    // Guard: evita chamar libusb_close/exit com ponteiro nulo
    // (ocorre na primeira chamada de start() antes de qualquer init bem-sucedido)
    active_.store(false);
    isoOutEp_ = 0; isoFeedEp_ = 0; isoMaxPkt_ = 0;
    isoIface_ = 0; isoAltSet_ = 0;
    ifaceExtra_.clear();

    if (devHandle_) {
        auto* h = static_cast<libusb_device_handle*>(devHandle_);
        devHandle_ = nullptr; // zera ANTES de fechar para evitar double-free em reentrada
        libusb_close(h);
    }
    if (ctx_) {
        auto* c = static_cast<libusb_context*>(ctx_);
        ctx_ = nullptr; // zera ANTES de sair
        libusb_exit(c);
    }
    LOGI("UsbAudioDriver stopped");
}

bool UsbAudioDriver::isActive()     const { return active_.load(); }
int  UsbAudioDriver::getSampleRate() const { return sampleRate_; }

// ─────────────────────────────────────────────────────────────────────────────
// parseAudioDescriptors() — Fase 1 + salva extra bytes
// ─────────────────────────────────────────────────────────────────────────────
bool UsbAudioDriver::parseAudioDescriptors() {
    if (!devHandle_) return false;
    libusb_device* dev = libusb_get_device(static_cast<libusb_device_handle*>(devHandle_));
    libusb_config_descriptor* config = nullptr;
    if (libusb_get_active_config_descriptor(dev, &config) != LIBUSB_SUCCESS) return false;

    LOGI("USB Config: bConfigurationValue=%d numInterfaces=%d",
         config->bConfigurationValue, config->bNumInterfaces);

    bool found = false;
    for (int i = 0; i < (int)config->bNumInterfaces && !found; i++) {
        const libusb_interface& iface = config->interface[i];
        for (int a = 0; a < iface.num_altsetting; a++) {
            const libusb_interface_descriptor& alt = iface.altsetting[a];
            LOGI("  IF[%d] alt[%d]: class=%d sub=%d proto=%d numEP=%d",
                 alt.bInterfaceNumber, alt.bAlternateSetting,
                 alt.bInterfaceClass, alt.bInterfaceSubClass,
                 alt.bInterfaceProtocol, alt.bNumEndpoints);

            if (alt.bInterfaceClass    != USB_CLASS_AUDIO)          continue;
            if (alt.bInterfaceSubClass != USB_SUBCLASS_AUDIOSTREAMING) continue;
            if (alt.bNumEndpoints == 0) continue;

            for (int ep = 0; ep < (int)alt.bNumEndpoints; ep++) {
                const libusb_endpoint_descriptor& e = alt.endpoint[ep];
                bool isOut = (e.bEndpointAddress & LIBUSB_ENDPOINT_DIR_MASK) == LIBUSB_ENDPOINT_OUT;
                bool isIn  = !isOut;
                bool isIso = (e.bmAttributes & LIBUSB_TRANSFER_TYPE_MASK) == LIBUSB_TRANSFER_TYPE_ISOCHRONOUS;
                uint8_t sync = (e.bmAttributes >> 2) & 0x03;
                const char* syncNames[] = {"None","Async","Adaptive","Sync"};

                LOGI("    EP[%d]: addr=0x%02x dir=%s type=%s sync=%s maxPkt=%d interval=%d",
                     ep, e.bEndpointAddress,
                     isOut ? "OUT" : "IN",
                     isIso ? "ISO" : "BULK/INT",
                     syncNames[sync], e.wMaxPacketSize, e.bInterval);

                if (isOut && isIso && !found) {
                    isoOutEp_   = e.bEndpointAddress;
                    isoMaxPkt_  = e.wMaxPacketSize;
                    isoIface_   = alt.bInterfaceNumber;
                    isoAltSet_  = alt.bAlternateSetting;
                    // Protocolo (UAC1=0, UAC2=0x20) para parser de Fase 2
                    fmt_.sampleRate = (uint32_t)sampleRate_;

                    // Salvar extra bytes para parseClassSpecificDescriptors()
                    if (alt.extra && alt.extra_length > 0) {
                        ifaceExtra_.assign(alt.extra, alt.extra + alt.extra_length);
                    }

                    // Detectar velocidade real do device (HS=8000pps, FS=1000pps)
                    // NÃO usar heurístico baseado em maxPkt — pode ser enganoso.
                    {
                        int speed = libusb_get_device_speed(dev);
                        // LIBUSB_SPEED_HIGH=3 (480Mbps): bInterval=1 → 125µs → 8000 pps
                        // LIBUSB_SPEED_FULL=2 (12Mbps):  bInterval=1 → 1ms   → 1000 pps
                        fmt_.packetsPerSec = (speed >= LIBUSB_SPEED_HIGH) ? 8000 : 1000;
                        LOGI("      USB speed=%d → packetsPerSec=%d",
                             speed, fmt_.packetsPerSec);
                    }

                    LOGI("  *** ISOCHRONOUS OUT AUDIO EP FOUND ***");
                    LOGI("      addr=0x%02x  maxPkt=%d  interval=%d  sync=%s",
                         isoOutEp_, isoMaxPkt_, e.bInterval, syncNames[sync]);
                    LOGI("      interface=%d  altSetting=%d  ifaceExtraLen=%d",
                         isoIface_, isoAltSet_, (int)ifaceExtra_.size());
                    found = true;
                }
                // Salvar feedback endpoint (ISO IN no mesmo AltSet que o OUT)
                if (isIn && isIso && found) {
                    isoFeedEp_ = e.bEndpointAddress;
                    LOGI("      feedback EP: 0x%02x", isoFeedEp_);
                }
            }
            if (found) break;
        }
    }
    libusb_free_config_descriptor(config);
    return found;
}

// ─────────────────────────────────────────────────────────────────────────────
// parseClassSpecificDescriptors() — Fase 2
//
// Itera os extra bytes da interface AudioStreaming para encontrar:
//   UAC1 FORMAT_TYPE_I: bNrChannels, bSubframeSize, bBitResolution
//   UAC2 AS_GENERAL:    bNrChannels
//   UAC2 FORMAT_TYPE_I: bSubslotSize, bBitResolution
// ─────────────────────────────────────────────────────────────────────────────
void UsbAudioDriver::parseClassSpecificDescriptors() {
    const uint8_t* p   = ifaceExtra_.data();
    const uint8_t* end = p + ifaceExtra_.size();

    bool isUac2 = false; // detectado via AS_GENERAL subtype check

    while (p + 2 <= end) {
        uint8_t bLength          = p[0];
        uint8_t bDescriptorType  = p[1];
        uint8_t bDescriptorSubtype = (bLength >= 3) ? p[2] : 0;

        if (bLength == 0 || p + bLength > end) break;

        if (bDescriptorType == CS_INTERFACE) {

            // ── AS_GENERAL ────────────────────────────────────────────────
            if (bDescriptorSubtype == AS_GENERAL) {
                // UAC2 AS_GENERAL (16 bytes): bNrChannels em offset 10
                if (bLength >= 16) {
                    isUac2 = true;
                    fmt_.nrChannels = p[10];
                    LOGI("  [UAC2 AS_GENERAL] bNrChannels=%d", fmt_.nrChannels);
                }
                // UAC1 AS_GENERAL (7 bytes): sem bNrChannels aqui
                else if (bLength >= 7) {
                    LOGI("  [UAC1 AS_GENERAL] bFormatTag=0x%02x%02x", p[5], p[6]);
                }
            }

            // ── FORMAT_TYPE ───────────────────────────────────────────────
            else if (bDescriptorSubtype == FORMAT_TYPE && bLength >= 6) {
                uint8_t fmtType = p[3];
                if (fmtType == FORMAT_TYPE_I) {
                    if (isUac2) {
                        // UAC2 FORMAT_TYPE_I (6 bytes):
                        // offset 4 = bSubslotSize, offset 5 = bBitResolution
                        fmt_.subframeSize  = p[4];
                        fmt_.bitResolution = p[5];
                        LOGI("  [UAC2 FORMAT_TYPE_I] subslot=%d bits=%d",
                             fmt_.subframeSize, fmt_.bitResolution);
                    } else {
                        // UAC1 FORMAT_TYPE_I:
                        // offset 4 = bNrChannels, 5 = bSubframeSize, 6 = bBitResolution
                        if (bLength >= 7) {
                            fmt_.nrChannels    = p[4];
                            fmt_.subframeSize  = p[5];
                            fmt_.bitResolution = p[6];
                            LOGI("  [UAC1 FORMAT_TYPE_I] nrCh=%d subframe=%d bits=%d",
                                 fmt_.nrChannels, fmt_.subframeSize, fmt_.bitResolution);
                        }
                    }
                }
            }
        }
        p += bLength;
    }

    // Sanity check + defaults
    if (fmt_.nrChannels  == 0 || fmt_.nrChannels  > 8) fmt_.nrChannels  = (uint8_t)channels_;
    if (fmt_.subframeSize == 0 || fmt_.subframeSize > 4) {
        // Inferir: 104 bytes / 48 frames / nrChannels arredondado
        int guess = isoMaxPkt_ / fmt_.packetsPerSec / fmt_.nrChannels * 1000;
        if (guess < 1 || guess > 4) guess = 2;
        fmt_.subframeSize = (uint8_t)guess;
    }
    if (fmt_.bitResolution == 0) fmt_.bitResolution = fmt_.subframeSize * 8;

    // Calcular parâmetros derivados
    int bytesPerFrame       = fmt_.nrChannels * fmt_.subframeSize;
    fmt_.framesPerPacket    = (fmt_.packetsPerSec > 0)
                               ? (sampleRate_ / fmt_.packetsPerSec)
                               : (isoMaxPkt_ / std::max(bytesPerFrame, 1));
    fmt_.bytesPerPacket     = fmt_.framesPerPacket * bytesPerFrame;

    // Nunca exceder maxPkt
    if (fmt_.bytesPerPacket > isoMaxPkt_) {
        fmt_.framesPerPacket = isoMaxPkt_ / std::max(bytesPerFrame, 1);
        fmt_.bytesPerPacket  = fmt_.framesPerPacket * bytesPerFrame;
    }

    LOGI("AudioFormat final: nrCh=%d subframe=%d bits=%d fps=%d bytesPerPkt=%d pps=%d",
         fmt_.nrChannels, fmt_.subframeSize, fmt_.bitResolution,
         fmt_.framesPerPacket, fmt_.bytesPerPacket, fmt_.packetsPerSec);
}

// ─────────────────────────────────────────────────────────────────────────────
// claimAudioInterface() / releaseAudioInterface()
// ─────────────────────────────────────────────────────────────────────────────
bool UsbAudioDriver::claimAudioInterface() {
    auto* h = static_cast<libusb_device_handle*>(devHandle_);

    // Desanexar kernel driver se necessário (Android geralmente não precisa)
    if (libusb_kernel_driver_active(h, isoIface_) == 1) {
        int r = libusb_detach_kernel_driver(h, isoIface_);
        if (r != LIBUSB_SUCCESS) {
            LOGW("detach kernel driver failed (iface=%d): %s",
                 isoIface_, libusb_strerror((libusb_error)r));
        }
    }

    int r = libusb_claim_interface(h, isoIface_);
    if (r != LIBUSB_SUCCESS) {
        LOGE("libusb_claim_interface(%d) failed: %s", isoIface_, libusb_strerror((libusb_error)r));
        return false;
    }
    LOGI("Interface %d claimed", isoIface_);

    r = libusb_set_interface_alt_setting(h, isoIface_, isoAltSet_);
    if (r != LIBUSB_SUCCESS) {
        LOGE("libusb_set_interface_alt_setting(%d,%d) failed: %s",
             isoIface_, isoAltSet_, libusb_strerror((libusb_error)r));
        libusb_release_interface(h, isoIface_);
        return false;
    }
    LOGI("AltSetting %d activated on interface %d", isoAltSet_, isoIface_);
    ifaceClaimed_ = true;
    return true;
}

void UsbAudioDriver::releaseAudioInterface() {
    if (!ifaceClaimed_ || !devHandle_) return;
    auto* h = static_cast<libusb_device_handle*>(devHandle_);
    libusb_set_interface_alt_setting(h, isoIface_, 0); // volta para alt 0 (bw=0)
    libusb_release_interface(h, isoIface_);
    ifaceClaimed_ = false;
    LOGI("Interface %d released", isoIface_);
}

// ─────────────────────────────────────────────────────────────────────────────
// startStreaming() — Fase 3
// ─────────────────────────────────────────────────────────────────────────────
bool UsbAudioDriver::startStreaming(AudioFillCallback cb) {
    if (!active_.load()) { LOGE("startStreaming: driver not active"); return false; }
    if (streaming_.load()) { LOGW("startStreaming: already streaming"); return true; }

    renderCb_ = cb;
    sinePhase_ = 0.0f;

    // Fase 2: parse descritores e claim interface
    parseClassSpecificDescriptors();
    if (!claimAudioInterface()) return false;

    // Aloca e submete os transfers iniciais
    if (!allocateTransfers()) {
        releaseAudioInterface();
        return false;
    }

    streaming_.store(true);
    stopLoop_.store(false);
    pendingXfers_.store(NUM_TRANSFERS);

    // Iniciar thread de eventos USB com prioridade de tempo real
    usbThread_ = std::thread([this]() {
        // SCHED_FIFO prio 2 (ligeiramente acima do synthRenderLoop prio 1)
        struct sched_param sp;
        sp.sched_priority = 2;
        if (sched_setscheduler(0, SCHED_FIFO, &sp) != 0) {
            setpriority(PRIO_PROCESS, 0, -19);
        }
        LOGI("USB event thread started (cpu=%d)", sched_getcpu());
        usbEventLoop();
        LOGI("USB event thread exited");
    });

    LOGI("Streaming started: %d transfers of %d pkts @ %d bytes each (%dHz %dch %dbit)",
         NUM_TRANSFERS, NUM_PACKETS_PER_XFER, fmt_.bytesPerPacket, sampleRate_,
         fmt_.nrChannels, fmt_.bitResolution);
    return true;
}

// ─────────────────────────────────────────────────────────────────────────────
// stopStreaming()
// ─────────────────────────────────────────────────────────────────────────────
void UsbAudioDriver::stopStreaming() {
    if (!streaming_.load()) return;
    streaming_.store(false);
    stopLoop_.store(true);

    // Cancelar transfers pendentes
    for (int i = 0; i < NUM_TRANSFERS; i++) {
        if (xfers_[i]) libusb_cancel_transfer(xfers_[i]);
    }
    for (int i = 0; i < 2; i++) {
        if (feedXfers_[i]) libusb_cancel_transfer(feedXfers_[i]);
    }

    // Aguardar thread encerrar (timeout 2s)
    if (usbThread_.joinable()) usbThread_.join();

    freeTransfers();
    releaseAudioInterface();
    renderCb_ = nullptr;
    pendingXfers_.store(0);
    LOGI("Streaming stopped");
}

// ─────────────────────────────────────────────────────────────────────────────
// allocateTransfers() / freeTransfers()
// ─────────────────────────────────────────────────────────────────────────────
bool UsbAudioDriver::allocateTransfers() {
    for (int i = 0; i < NUM_TRANSFERS; i++) {
        // MUST allocate based on the absolute maximum packet size the hardware supports,
        // otherwise compensating for clock drift (sending 7 frames instead of 6) will overflow the heap!
        int bytesPerTransfer = isoMaxPkt_ * NUM_PACKETS_PER_XFER;
        xferBufs_[i].resize(bytesPerTransfer, 0);

        xfers_[i] = libusb_alloc_transfer(NUM_PACKETS_PER_XFER);
        if (!xfers_[i]) {
            LOGE("libusb_alloc_transfer failed (i=%d)", i);
            return false;
        }

        libusb_fill_iso_transfer(
            xfers_[i],
            static_cast<libusb_device_handle*>(devHandle_),
            isoOutEp_,
            xferBufs_[i].data(),
            bytesPerTransfer,
            NUM_PACKETS_PER_XFER,
            isoCallback,
            this,
            1000                   // timeout ms
        );

        for (int p = 0; p < NUM_PACKETS_PER_XFER; p++) {
            xfers_[i]->iso_packet_desc[p].length = (unsigned int)fmt_.bytesPerPacket;
            // The initial fill MUST use isoMaxPkt_ for advancing the pointer to ensure
            // the packets are properly spaced in the buffer according to the max size!
            // Wait, libusb packet offsets are calculated automatically by the kernel
            // BUT t->buffer is contiguous. If we write it contiguously, we just advance by the nominal size initially.
            uint8_t* pktData = xferBufs_[i].data() + (p * fmt_.bytesPerPacket);
            fillPacketFromAudio(pktData, fmt_.framesPerPacket);
        }

        int r = libusb_submit_transfer(xfers_[i]);
        if (r != LIBUSB_SUCCESS) {
            LOGE("libusb_submit_transfer[%d] failed: %s", i, libusb_strerror((libusb_error)r));
            return false;
        }
        LOGI("Transfer[%d] submitted: %d bytes", i, fmt_.bytesPerPacket);
    }

    if (isoFeedEp_ != 0) {
        for (int i = 0; i < 2; i++) {
            // Allocate 16 packets per transfer (2ms contiguous polling) to ensure we hit the interval=4 (8 microframes)
            int pkts = 16;
            feedBufs_[i].resize(pkts * 4, 0);
            feedXfers_[i] = libusb_alloc_transfer(pkts);
            libusb_fill_iso_transfer(feedXfers_[i], static_cast<libusb_device_handle*>(devHandle_), 
                                     isoFeedEp_, feedBufs_[i].data(), pkts * 4, pkts, feedCallback, this, 1000);
            for (int p = 0; p < pkts; p++) {
                feedXfers_[i]->iso_packet_desc[p].length = 4;
            }
            libusb_submit_transfer(feedXfers_[i]);
        }
        LOGI("Feedback transfers submitted on EP 0x%02X", isoFeedEp_);
    }
    return true;
}

void UsbAudioDriver::freeTransfers() {
    for (int i = 0; i < NUM_TRANSFERS; i++) {
        if (xfers_[i]) {
            libusb_free_transfer(xfers_[i]);
            xfers_[i] = nullptr;
        }
        xferBufs_[i].clear();
    }
    for (int i = 0; i < 2; i++) {
        if (feedXfers_[i]) {
            libusb_free_transfer(feedXfers_[i]);
            feedXfers_[i] = nullptr;
        }
        feedBufs_[i].clear();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// isoCallback() + handleCompletion() — Fase 3
// ─────────────────────────────────────────────────────────────────────────────
void LIBUSB_CALL UsbAudioDriver::isoCallback(struct libusb_transfer* t) {
    auto* self = static_cast<UsbAudioDriver*>(t->user_data);
    self->handleCompletion(t);
}

void UsbAudioDriver::handleCompletion(struct libusb_transfer* t) {
    if (!streaming_.load() || stopLoop_.load()) {
        pendingXfers_.fetch_sub(1);
        return;
    }

    if (t->status != LIBUSB_TRANSFER_COMPLETED) {
        if (t->status != LIBUSB_TRANSFER_CANCELLED) {
            LOGW("ISO transfer error: status=%d", (int)t->status);
        }
        pendingXfers_.fetch_sub(1);
        return;
    }

    // Determinar quantos frames enviar neste pacote
    // (adaptado pelo feedback async se disponível)
    int bytesPerFrame = fmt_.nrChannels * fmt_.subframeSize;
    int bytesSent = 0;

    for (int p = 0; p < t->num_iso_packets; p++) {
        int targetFrames = fmt_.framesPerPacket;
        
        // --- PROPORTIONAL CLOCK DRIFT ACCUMULATION ---
        // Accumulate fractional drift exactly once per OUT microframe.
        // This ensures the device buffer receives exactly the amount of samples it requested.
        float rate = currentDeviceRate_.load(std::memory_order_relaxed);
        if (rate > 0.0f) {
            fractionAcc_ += (rate - (float)fmt_.framesPerPacket);
            if (fractionAcc_ >= 1.0f) {
                targetFrames += 1;
                fractionAcc_ -= 1.0f;
            } else if (fractionAcc_ <= -1.0f) {
                targetFrames -= 1;
                fractionAcc_ += 1.0f;
            }
        }
        targetFrames = std::max(1, std::min(targetFrames, (int)(isoMaxPkt_ / std::max(bytesPerFrame,1))));
        int pktBytes = targetFrames * bytesPerFrame;

        uint8_t* pktData = t->buffer + bytesSent;
        bool hasAudio = fillPacketFromAudio(pktData, targetFrames);
        t->iso_packet_desc[p].actual_length = 0;
        bytesSent += pktBytes;
    }

    t->length = bytesSent;
    t->status = LIBUSB_TRANSFER_COMPLETED;

    int r = libusb_submit_transfer(t);
    if (r != LIBUSB_SUCCESS) {
        LOGE("resubmit failed: %s", libusb_strerror((libusb_error)r));
        pendingXfers_.fetch_sub(1);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// usbEventLoop() — roda na usbThread_
// ─────────────────────────────────────────────────────────────────────────────
void UsbAudioDriver::usbEventLoop() {
    struct timeval tv;
    tv.tv_sec  = 0;
    tv.tv_usec = 1000; // 1ms timeout

    // Tolerância a falhas ISO isoladas:
    // Uma falha momentânea (ex: USB temporariamente ocupado no reconnect)
    // não deve matar o stream. Só aborta se pendingXfers_ == 0 por 2 polls seguidos.
    int zeroConsecutive = 0;
    constexpr int ZERO_ABORT_THRESHOLD = 2;

    while (!stopLoop_.load(std::memory_order_relaxed)) {
        libusb_handle_events_timeout(static_cast<libusb_context*>(ctx_), &tv);

        if (pendingXfers_.load(std::memory_order_relaxed) == 0) {
            zeroConsecutive++;
            if (zeroConsecutive >= ZERO_ABORT_THRESHOLD) {
                LOGE("FATAL: All ISO transfers dropped for %d consecutive polls. Aborting USB stream.",
                     zeroConsecutive);
                streaming_.store(false);
                active_.store(false);
                break;
            }
        } else {
            zeroConsecutive = 0; // reset ao recuperar
        }
    }

    // Drenar eventos pendentes após stop
    for (int drain = 0; drain < 20 && pendingXfers_.load() > 0; drain++) {
        tv.tv_usec = 5000;
        libusb_handle_events_timeout(static_cast<libusb_context*>(ctx_), &tv);
    }
}

bool UsbAudioDriver::fillPacketFromAudio(uint8_t* buf, int numFrames) {
    if (!buf || numFrames <= 0) return false;

    // Prevenir overflow do array na stack
    if (numFrames > 64) numFrames = 64;

    // Array na stack para evitar alocação (malloc/new) em thread de tempo real
    float audioFloats[128];
    memset(audioFloats, 0, sizeof(audioFloats));

    bool hasAudio = getAudio(audioFloats, numFrames);

    // Conversão float → bytes USB
    uint8_t* dstPtr = buf;
    for (int f = 0; f < numFrames; f++) {
        for (int c = 0; c < (int)fmt_.nrChannels; c++) {
            float samp = (hasAudio && c < 2) ? audioFloats[f * 2 + c] : 0.0f;
            floatToUsb(samp, dstPtr, fmt_.subframeSize);
            dstPtr += fmt_.subframeSize;
        }
    }
    return hasAudio;
}

// ─────────────────────────────────────────────────────────────────────────────
// floatToUsb() — Converte uma amostra float para o formato USB correto
// ─────────────────────────────────────────────────────────────────────────────
void UsbAudioDriver::floatToUsb(float sample, uint8_t* dst, int subframeSize) {
    switch (subframeSize) {
        case 1: {
            // 8-bit unsigned (raro)
            uint8_t v = (uint8_t)((sample + 1.0f) * 127.5f);
            dst[0] = v;
            break;
        }
        case 2: {
            // 16-bit signed LE
            int16_t v = (int16_t)(sample * 32767.0f);
            dst[0] = (uint8_t)(v & 0xFF);
            dst[1] = (uint8_t)((v >> 8) & 0xFF);
            break;
        }
        case 3: {
            // 24-bit signed LE (3 bytes)
            int32_t v = (int32_t)(sample * 8388607.0f);
            dst[0] = (uint8_t)(v & 0xFF);
            dst[1] = (uint8_t)((v >> 8) & 0xFF);
            dst[2] = (uint8_t)((v >> 16) & 0xFF);
            break;
        }
        case 4: {
            // UAC2: 24-bit audio em container de 32-bits, S32_LE (Left-Justified).
            // Em arquiteturas UAC2/ALSA para dispositivos de 24-bit em subslots de 4 bytes,
            // os dados válidos ocupam os bytes mais significativos (MSB) do container de 32-bit.
            // O byte mais significativo (dst[3]) OBRIGATORIAMENTE carrega o bit de sinal.
            // 
            // Hard-clipper obrigatório antes da conversão para evitar overflow extremo.
            // Limitar em 0.9999f impede que 1.0f exato dispare Undefined Behavior (UB)
            // na conversão float -> int32_t, que no ARM pode causar wrap para 0x80000000 (um estalo enorme).
            float s = sample;
            if (s >  0.9999f) s =  0.9999f;
            else if (s < -0.9999f) s = -0.9999f;

            // Escala para 32-bit (2^31 - 1 = 2147483647)
            int32_t v = (int32_t)(s * 2147483647.0f);

            // Zera o LSB para garantir exatamente 24-bits de resolução válida
            v &= 0xFFFFFF00;

            // Alinhado à esquerda (Left-Justified) num container Little-Endian
            dst[0] = (uint8_t)( v        & 0xFF); // Sempre 0
            dst[1] = (uint8_t)((v >>  8) & 0xFF);
            dst[2] = (uint8_t)((v >> 16) & 0xFF);
            dst[3] = (uint8_t)((v >> 24) & 0xFF); // Bit de sinal preservado
            break;
        }
        default:
            memset(dst, 0, (size_t)subframeSize);
            break;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// getAudio() — Obtém áudio do ring buffer ou gera sine wave de teste
// ─────────────────────────────────────────────────────────────────────────────
bool UsbAudioDriver::getAudio(float* buf, int numFrames) {
    if (renderCb_) {
        // Com callback: passa o resultado diretamente (true=áudio, false=underrun)
        // Em underrun, retorna false → fillPacketFromAudio faz memset+return (silêncio)
        // NUNCA cair no sine wave quando há callback — causaria som de guitarra
        // misturado com o instrumento nos gaps de underrun.
        return renderCb_(buf, numFrames, 2);
    }

    // Sine wave de teste 440Hz (A4) — SOMENTE quando sem callback (modo diagnóstico)
    float phaseInc = 2.0f * (float)M_PI * SINE_FREQ / (float)sampleRate_;
    for (int f = 0; f < numFrames; f++) {
        float s = sinf(sinePhase_) * 0.25f; // -12dBFS para não assustar
        sinePhase_ += phaseInc;
        if (sinePhase_ >= 2.0f * (float)M_PI) sinePhase_ -= 2.0f * (float)M_PI;
        buf[f * 2]     = s; // L
        buf[f * 2 + 1] = s; // R
    }
    return true;
}

// ─────────────────────────────────────────────────────────────────────────────
// parseAsyncFeedback() — Processa feedback do device (Fase 3 async sync)
// Formato UAC1/UAC2: 3 bytes, big-endian, Q10.14 (kHz × 16384)
// Convertido para framesPerPacket real.
// ─────────────────────────────────────────────────────────────────────────────
void UsbAudioDriver::parseAsyncFeedback(uint32_t feedback) {
    // UAC2 feedback: 16.16 format (feedback = freq * 65536)
    float rate = (float)feedback / 65536.0f;
    float nominal = (float)fmt_.framesPerPacket;
    
    // Heuristics for broken UAC1/UAC2 feedback implementations
    if (rate > nominal * 1.5f && rate < nominal * 2.5f) {
        // Likely UAC1 10.14 format inside 4 bytes (e.g. rate=12.0 for 48kHz)
        rate /= 2.0f;
    } else if (rate > nominal * 4.0f) {
        // Likely UAC2 16.16 but in Samples/Frame instead of Samples/Microframe
        rate /= 8.0f;
    }
    
    // Store the parsed rate. The actual fractional accumulation MUST happen in handleCompletion()
    // because rate represents drift PER MICROFRAME, but this feedback callback is only called
    // once every `interval` (e.g. 8 microframes). If we accumulate here, we track drift 8x too slowly.
    currentDeviceRate_.store(rate, std::memory_order_relaxed);
}

void LIBUSB_CALL UsbAudioDriver::feedCallback(struct libusb_transfer* t) {
    auto* self = static_cast<UsbAudioDriver*>(t->user_data);
    if (!self->streaming_.load() || self->stopLoop_.load()) return;

    if (t->status == LIBUSB_TRANSFER_COMPLETED) {
        // Iterate through all packets in the contiguous transfer
        for (int p = 0; p < t->num_iso_packets; p++) {
            if (t->iso_packet_desc[p].actual_length > 0) {
                uint32_t feedback = 0;
                int len = t->iso_packet_desc[p].actual_length;
                uint8_t* pktData = libusb_get_iso_packet_buffer_simple(t, p);
                
                if (len == 3) { // UAC1 10.14
                    feedback = (pktData[2] << 16) | (pktData[1] << 8) | pktData[0];
                    feedback <<= 2; // Aproximar para 16.16
                } else if (len >= 4) { // UAC2 16.16
                    feedback = (pktData[3] << 24) | (pktData[2] << 16) | (pktData[1] << 8) | pktData[0];
                }
                if (feedback > 0) self->parseAsyncFeedback(feedback);
            }
        }
    }
    
    if (self->streaming_.load()) {
        libusb_submit_transfer(t);
    }
}
