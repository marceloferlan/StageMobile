#ifndef STAGEMOBILE_DSP_CHAIN_H
#define STAGEMOBILE_DSP_CHAIN_H

#include <vector>
#include <memory>
#include <mutex>
#include <atomic>
#include <cstring>
#include <cstdlib>   // posix_memalign / free
#include <DspFilters/Dsp.h>
#include <DspFilters/Butterworth.h>
#include <DspFilters/Design.h>
#include "Delay.h"
#include "DelayL.h"
#include "Chorus.h"
#include "FreeVerb.h"
#ifdef __ARM_NEON
#include <arm_neon.h>
#endif
#ifndef _USE_MATH_DEFINES
#define _USE_MATH_DEFINES
#endif
#include <cmath>
#include <algorithm>
#include <android/log.h>

#define DSP_LOG_TAG "StageDSP"
#define LOG_DSP(...) __android_log_print(ANDROID_LOG_INFO, DSP_LOG_TAG, __VA_ARGS__)

#define MAX_AUDIO_FRAME_SIZE 16384

namespace stage_audio {

/**
 * Utilitário para interpolação suave de parâmetros em tempo real.
 * Evita clicks/pops causados por mudanças abruptas de valor.
 * Uso: chamar setTarget() da UI thread, getNext() sample-a-sample no audio callback.
 */
class SmoothedParam {
public:
    SmoothedParam(float initial = 0.0f, float smoothCoeff = 0.995f)
        : current(initial), target(initial), coeff(smoothCoeff) {}

    void setTarget(float v) { target = v; }
    void setImmediate(float v) { target = v; current = v; }
    void setSmoothCoeff(float c) { coeff = c; }

    float getNext() {
        current = current + (1.0f - coeff) * (target - current);
        return current;
    }

    float getCurrent() const { return current; }
    float getTarget() const { return target; }

    bool isSmoothing() const {
        return std::abs(target - current) > 1e-6f;
    }

private:
    float current;
    float target;
    float coeff; // 0.99 = fast (~5ms), 0.999 = slow (~50ms)
};

/**
 * Interface Base para Efeitos DSP
 */
class DSPEffect {
public:
    virtual ~DSPEffect() = default;
    virtual void processStereo(float* left, float* right, int numSamples) = 0;
    virtual void setParam(int paramId, float value) = 0;
    virtual int getType() const = 0;
    virtual bool hasActiveTails() const { return false; }
    virtual void getMeters(float* input, float* output, float* gr) {
        if (input) *input = 0.0f;
        if (output) *output = 0.0f;
        if (gr) *gr = 0.0f;
    }
    virtual void setSampleRate(float fs) {}
    
    // Soft Bypass System
    bool enabled = true;
    SmoothedParam bypassFade{1.0f, 0.95f}; // 1.0 = On, 0.0 = Off
};

/**
 * Efeito de Send para o Bus Global (Reverb)
 */
class SendBusEffect : public DSPEffect {
public:
    SendBusEffect(float* globalL, float* globalR) : busL(globalL), busR(globalR) {}

    void processStereo(float* left, float* right, int numSamples) override {
        // We use bypassFade to scale the send level smoothly
        float currentTarget = bypassFade.getTarget();
        bool isSmoothing = bypassFade.isSmoothing();
        if (currentTarget < 0.01f && !isSmoothing) return;
        
        for (int i = 0; i < numSamples; ++i) {
            float fade = isSmoothing ? bypassFade.getNext() : 1.0f;
            busL[i] += left[i] * level * fade;
            busR[i] += right[i] * level * fade;
        }
    }

    void setParam(int paramId, float value) override {
        if (paramId == 0) level = value; // 0 = Send Level
    }

    int getType() const override { return 9; }

private:
    float level = 0.0f;
    float* busL;
    float* busR;
};

/**
 * EQ Paramétrico (3 Bandas) — Com Crossfade Anti-Click
 * Usa double-buffer de filtros: ao alterar parâmetros, o novo filtro
 * processa em paralelo enquanto um crossfade suave (64 samples) faz
 * a transição do sinal antigo para o novo, eliminando descontinuidades.
 */
class ParametricEQEffect : public DSPEffect {
public:
    ParametricEQEffect() {
        setupBank(0, 200.0f, 0.0f, 1000.0f, 500.0f, 0.0f, 5000.0f, 0.0f);
        setupBank(1, 200.0f, 0.0f, 1000.0f, 500.0f, 0.0f, 5000.0f, 0.0f);
    }
    
    void setSampleRate(float fs) override {
        sampleRate = fs;
        float fL = lFreq.load(), gL = lGain.load();
        float fM = mFreq.load(), bw = mBW.load(), gM = mGain.load();
        float fH = hFreq.load(), gH = hGain.load();
        setupBank(0, fL, gL, fM, bw, gM, fH, gH);
        setupBank(1, fL, gL, fM, bw, gM, fH, gH);
        appliedLFreq = fL; appliedLGain = gL;
        appliedMFreq = fM; appliedMBW = bw; appliedMGain = gM;
        appliedHFreq = fH; appliedHGain = gH;
    }
    
    void setParam(int paramId, float value) override {
        switch(paramId) {
            case 0: lGain.store(clamp(value, -24.0f, 24.0f)); break;
            case 1: lFreq.store(clamp(value, 20.0f, 1000.0f)); break;
            case 2: mGain.store(clamp(value, -24.0f, 24.0f)); break;
            case 3: mFreq.store(clamp(value, 20.0f, 8000.0f)); break;
            case 4: mBW.store(clamp(value, 10.0f, 1000.0f)); break;
            case 5: hGain.store(clamp(value, -24.0f, 24.0f)); break;
            case 6: hFreq.store(clamp(value, 2000.0f, 20000.0f)); break;
            case 7: outGainSmooth.setTarget(std::pow(10.0f, clamp(value, -24.0f, 24.0f) / 20.0f)); break;
        }
    }

    int getType() const override { return 0; }

    void processStereo(float* left, float* right, int numSamples) override {
        // Soft-bypass handled by DSPChannel crossfade
        float curLF = lFreq.load(), curLG = lGain.load();
        float curMF = mFreq.load(), curMBW = mBW.load(), curMG = mGain.load();
        float curHF = hFreq.load(), curHG = hGain.load();
        
        bool paramsChanged = (curLF != appliedLFreq || curLG != appliedLGain ||
                              curMF != appliedMFreq || curMBW != appliedMBW || curMG != appliedMGain ||
                              curHF != appliedHFreq || curHG != appliedHGain);
        
        if (paramsChanged && !crossfading) {
            int newBank = 1 - activeBank;
            setupBank(newBank, curLF, curLG, curMF, curMBW, curMG, curHF, curHG);
            crossfading = true;
            crossfadePos = 0;
            appliedLFreq = curLF; appliedLGain = curLG;
            appliedMFreq = curMF; appliedMBW = curMBW; appliedMGain = curMG;
            appliedHFreq = curHF; appliedHGain = curHG;
        }
        
        if (crossfading) {
            int oldB = activeBank, newB = 1 - activeBank;
            memcpy(tmpL_old, left, numSamples * sizeof(float));
            memcpy(tmpR_old, right, numSamples * sizeof(float));
            memcpy(tmpL_new, left, numSamples * sizeof(float));
            memcpy(tmpR_new, right, numSamples * sizeof(float));
            
            float* chsOld[2] = {tmpL_old, tmpR_old};
            float* chsNew[2] = {tmpL_new, tmpR_new};
            for (int c = 0; c < 2; ++c) {
                lowShelf[oldB][c].process(numSamples, &chsOld[c]);
                midPeak[oldB][c].process(numSamples, &chsOld[c]);
                highShelf[oldB][c].process(numSamples, &chsOld[c]);
                lowShelf[newB][c].process(numSamples, &chsNew[c]);
                midPeak[newB][c].process(numSamples, &chsNew[c]);
                highShelf[newB][c].process(numSamples, &chsNew[c]);
            }
            
            for (int i = 0; i < numSamples; ++i) {
                float t = (crossfadePos < CROSSFADE_LEN) ? (float)crossfadePos / (float)CROSSFADE_LEN : 1.0f;
                left[i]  = tmpL_old[i] * (1.0f - t) + tmpL_new[i] * t;
                right[i] = tmpR_old[i] * (1.0f - t) + tmpR_new[i] * t;
                if (crossfadePos < CROSSFADE_LEN) crossfadePos++;
            }
            if (crossfadePos >= CROSSFADE_LEN) {
                activeBank = newB;
                crossfading = false;
            }
        } else {
            float* chs[2] = {left, right};
            for (int c = 0; c < 2; ++c) {
                lowShelf[activeBank][c].process(numSamples, &chs[c]);
                midPeak[activeBank][c].process(numSamples, &chs[c]);
                highShelf[activeBank][c].process(numSamples, &chs[c]);
            }
        }
        
        for (int i = 0; i < numSamples; ++i) {
            float g = outGainSmooth.getNext();
            left[i] *= g;
            right[i] *= g;
        }
    }

private:
    float clamp(float v, float lo, float hi) { return (v < lo) ? lo : (v > hi) ? hi : v; }
    
    void setupBank(int bank, float fL, float gL, float fM, float bw, float gM, float fH, float gH) {
        float nyquist = sampleRate * 0.5f;
        fL = clamp(fL, 20.0f, nyquist * 0.9f);
        fH = clamp(fH, 20.0f, nyquist * 0.9f);
        fM = clamp(fM, 20.0f, nyquist * 0.9f);
        float finalBW = (bw < 20.0f) ? (fM / std::max(0.1f, bw)) : bw;
        float maxBW = 2.0f * (nyquist * 0.95f - fM);
        if (finalBW > maxBW) finalBW = maxBW;
        if (finalBW < 5.0f) finalBW = 5.0f;
        for (int c = 0; c < 2; ++c) {
            lowShelf[bank][c].setup(2, sampleRate, fL, gL);
            midPeak[bank][c].setup(2, sampleRate, fM, finalBW, gM);
            highShelf[bank][c].setup(2, sampleRate, fH, gH);
        }
    }

    std::atomic<float> lFreq{200.0f}, lGain{0.0f};
    std::atomic<float> mFreq{1000.0f}, mBW{500.0f}, mGain{0.0f};
    std::atomic<float> hFreq{5000.0f}, hGain{0.0f};
    
    float appliedLFreq = 200.0f, appliedLGain = 0.0f;
    float appliedMFreq = 1000.0f, appliedMBW = 500.0f, appliedMGain = 0.0f;
    float appliedHFreq = 5000.0f, appliedHGain = 0.0f;

    float tmpL_old[MAX_AUDIO_FRAME_SIZE], tmpR_old[MAX_AUDIO_FRAME_SIZE];
    float tmpL_new[MAX_AUDIO_FRAME_SIZE], tmpR_new[MAX_AUDIO_FRAME_SIZE];

    ::Dsp::SimpleFilter<::Dsp::Butterworth::LowShelf<2>, 1> lowShelf[2][2];
    ::Dsp::SimpleFilter<::Dsp::Butterworth::BandShelf<2>, 1> midPeak[2][2];
    ::Dsp::SimpleFilter<::Dsp::Butterworth::HighShelf<2>, 1> highShelf[2][2];

    int activeBank = 0;
    bool crossfading = false;
    int crossfadePos = 0;
    static constexpr int CROSSFADE_LEN = 256;
    float sampleRate = 44100.0f;
    SmoothedParam outGainSmooth{1.0f, 0.995f};
};

class FilterEffect : public DSPEffect {
public:
    FilterEffect(bool isHPF) : hpf(isHPF) {
        setupBank(0, 1000.0f);
        setupBank(1, 1000.0f);
    }
    
    void setSampleRate(float fs) override {
        sampleRate = fs;
        float c = targetCutoff.load();
        setupBank(0, c);
        setupBank(1, c);
        appliedCutoff = c;
    }

    void setParam(int paramId, float value) override {
        if (paramId == 0) targetCutoff.store(value);
    }

    int getType() const override { return hpf ? 1 : 2; }

    void processStereo(float* left, float* right, int numSamples) override {
        // Soft-bypass handled by DSPChannel crossfade
        float target = targetCutoff.load();
        if (target != appliedCutoff && !crossfading) {
            int newBank = 1 - activeBank;
            setupBank(newBank, target);
            crossfading = true;
            crossfadePos = 0;
            appliedCutoff = target;
        }
        
        if (crossfading) {
            int oldB = activeBank, newB = 1 - activeBank;
            memcpy(tmpL_old, left, numSamples * sizeof(float));
            memcpy(tmpR_old, right, numSamples * sizeof(float));
            memcpy(tmpL_new, left, numSamples * sizeof(float));
            memcpy(tmpR_new, right, numSamples * sizeof(float));
            
            float* chOld[2] = {tmpL_old, tmpR_old};
            float* chNew[2] = {tmpL_new, tmpR_new};
            processBank(oldB, chOld, numSamples);
            processBank(newB, chNew, numSamples);
            
            for (int i = 0; i < numSamples; ++i) {
                float t = (crossfadePos < CROSSFADE_LEN) ? (float)crossfadePos / (float)CROSSFADE_LEN : 1.0f;
                left[i]  = tmpL_old[i] * (1.0f - t) + tmpL_new[i] * t;
                right[i] = tmpR_old[i] * (1.0f - t) + tmpR_new[i] * t;
                if (crossfadePos < CROSSFADE_LEN) crossfadePos++;
            }
            if (crossfadePos >= CROSSFADE_LEN) {
                activeBank = newB;
                crossfading = false;
            }
        } else {
            float* chs[2] = {left, right};
            processBank(activeBank, chs, numSamples);
        }
    }

private:
    void setupBank(int bank, float cutoff) {
        float safeCutoff = (cutoff < 20.0f) ? 20.0f : (cutoff > (sampleRate * 0.45f)) ? (sampleRate * 0.45f) : cutoff;
        if (hpf) {
            filterHP[bank][0].setup(1, sampleRate, safeCutoff);
            filterHP[bank][1].setup(1, sampleRate, safeCutoff);
        } else {
            filterLP[bank][0].setup(1, sampleRate, safeCutoff);
            filterLP[bank][1].setup(1, sampleRate, safeCutoff);
        }
    }
    
    void processBank(int bank, float* chs[2], int numSamples) {
        if (hpf) {
            filterHP[bank][0].process(numSamples, &chs[0]);
            filterHP[bank][1].process(numSamples, &chs[1]);
        } else {
            filterLP[bank][0].process(numSamples, &chs[0]);
            filterLP[bank][1].process(numSamples, &chs[1]);
        }
    }
    
    bool hpf;
    float sampleRate = 44100.0f;
    std::atomic<float> targetCutoff{1000.0f};
    float appliedCutoff = 1000.0f;
    int activeBank = 0;
    bool crossfading = false;
    int crossfadePos = 0;
    static constexpr int CROSSFADE_LEN = 256;
    float tmpL_old[MAX_AUDIO_FRAME_SIZE], tmpR_old[MAX_AUDIO_FRAME_SIZE];
    float tmpL_new[MAX_AUDIO_FRAME_SIZE], tmpR_new[MAX_AUDIO_FRAME_SIZE];
    ::Dsp::SimpleFilter<::Dsp::Butterworth::HighPass<2>, 1> filterHP[2][2];
    ::Dsp::SimpleFilter<::Dsp::Butterworth::LowPass<2>, 1> filterLP[2][2];
};

/**
 * Chorus Estéreo (STK) — Com Smoothing de Parâmetros
 */
class ChorusEffect : public DSPEffect {
public:
    ChorusEffect() : chorus(1000), rateSmooth(0.5f, 0.997f), depthSmooth(0.2f, 0.997f), mixSmooth(0.5f, 0.997f) {
        chorus.setModFrequency(0.5f);
        chorus.setModDepth(0.2f);
        chorus.setEffectMix(0.5f);
    }
    void processStereo(float* left, float* right, int numSamples) override {
        // Soft-bypass handled by DSPChannel crossfade
        for (int i = 0; i < numSamples; ++i) {
            // Atualizar parâmetros suavizados a cada N samples
            if ((i & 15) == 0) { // A cada 16 samples (~0.36ms)
                chorus.setModFrequency(rateSmooth.getNext());
                chorus.setModDepth(depthSmooth.getNext());
                chorus.setEffectMix(mixSmooth.getNext());
            }
            float monoIn = (left[i] + right[i]) * 0.5f;
            left[i] = chorus.tick(monoIn, 0);
            right[i] = chorus.lastOut(1);
        }
    }
    void setParam(int paramId, float value) override {
        if (paramId == 0) rateSmooth.setTarget(value); // Rate 0.1-10Hz
        else if (paramId == 1) depthSmooth.setTarget(value); // Depth 0.0-1.0
        else if (paramId == 2) mixSmooth.setTarget(value); // Mix 0.0-1.0
    }
    int getType() const override { return 5; }
private:
    ::stk::Chorus chorus;
    SmoothedParam rateSmooth, depthSmooth, mixSmooth;
};

/**
 * Compressor Feed-Forward Custom — Com Smoothing Anti-Click
 * Threshold, ratio, makeupGain, kneeDb e mix são suavizados para
 * evitar saltos bruscos de gain reduction que causam estalos.
 */
class CompressorEffect : public DSPEffect {
public:
    CompressorEffect() : threshSmooth(0.1f, 0.997f), ratioSmooth(4.0f, 0.997f),
        makeupSmooth(1.0f, 0.997f), kneeSmooth(0.0f, 0.997f), mixSmooth(1.0f, 0.997f) {}

    /**
     * processStereo — Block-processing NEON (N=16 samples/bloco)
     *
     * O envelope follower é inerentemente sequencial (cada sample depende do anterior),
     * portanto permanece scalar. O ganho é calculado uma vez por bloco de 16 samples
     * (imperceptível a 48kHz: ~0.33ms de granularidade).
     *
     * NEON cobre:
     *  1. Peak detection:  vabsq_f32 + vmaxq_f32 + vpmax_f32 (4 samples/iter)
     *  2. Gain application: vmulq_f32 com combinedGain escalar (1 multiply/4 samples)
     *     — onde combinedGain = (1-mix) + gainReduction × makeup × mix
     *       elimina o cálculo dry/wet separado e mapeia num único vmulq por 4 amostras
     */
    void processStereo(float* __restrict__ left, float* __restrict__ right, int numSamples) override {
        float currentInputPeak  = 0.0f;
        float currentOutputPeak = 0.0f;
        float currentMaxGR      = 1.0f;

        static constexpr int BLOCK = 16;
        int i = 0;

        while (i < numSamples) {
            const int blockSize = std::min(BLOCK, numSamples - i);
            const int blockEnd  = i + blockSize;

            // Atualizar parâmetros suavizados (uma vez por bloco)
            const float curThresh  = threshSmooth.getNext();
            const float curRatio   = ratioSmooth.getNext();
            const float curMakeup  = makeupSmooth.getNext();
            const float curKnee    = kneeSmooth.getNext();
            const float curMix     = mixSmooth.getNext();

            // ── 1. Peak detection (NEON) ──────────────────────────────────────────
            float blockPeak = 0.0f;
#ifdef __ARM_NEON
            float32x4_t vPeak = vdupq_n_f32(0.0f);
            int j = i;
            for (; j <= blockEnd - 4; j += 4) {
                vPeak = vmaxq_f32(vPeak, vmaxq_f32(
                    vabsq_f32(vld1q_f32(left  + j)),
                    vabsq_f32(vld1q_f32(right + j))));
            }
            // Redução horizontal: max dos 4 lanes
            float32x2_t vP2 = vpmax_f32(vget_low_f32(vPeak), vget_high_f32(vPeak));
            vP2 = vpmax_f32(vP2, vP2);
            blockPeak = vget_lane_f32(vP2, 0);
            // Scalar tail (blocos < 4 samples, raro)
            for (; j < blockEnd; j++)
                blockPeak = std::max(blockPeak, std::max(std::abs(left[j]), std::abs(right[j])));
#else
            for (int j = i; j < blockEnd; j++)
                blockPeak = std::max(blockPeak, std::max(std::abs(left[j]), std::abs(right[j])));
#endif
            if (blockPeak > currentInputPeak) currentInputPeak = blockPeak;

            // ── 2. Envelope follower (scalar — dependência sequencial) ────────────
            const float target = (blockPeak > envelope) ? attackCoeff : releaseCoeff;
            envelope = envelope + target * (blockPeak - envelope);

            // ── 3. Gain computation (scalar — envolve log10 + pow) ────────────────
            float gainReduction = 1.0f;
            if (envelope > 1e-6f) {
                const float envDb   = 20.0f * std::log10(envelope);
                const float thrDb   = 20.0f * std::log10(std::max(curThresh, 1e-6f));
                const float diffDb  = envDb - thrDb;

                if (curKnee > 0.01f) {
                    const float halfKnee = curKnee * 0.5f;
                    if (diffDb > halfKnee) {
                        gainReduction = std::pow(10.0f, -diffDb * (1.0f - 1.0f / curRatio) / 20.0f);
                    } else if (diffDb > -halfKnee) {
                        const float kp = (diffDb + halfKnee) / curKnee;
                        gainReduction = std::pow(10.0f, -(1.0f - 1.0f / curRatio) * kp * kp * curKnee * 0.5f / 20.0f);
                    }
                } else if (diffDb > 0.0f) {
                    gainReduction = std::pow(10.0f, -diffDb * (1.0f - 1.0f / curRatio) / 20.0f);
                }
            }
            if (gainReduction < currentMaxGR) currentMaxGR = gainReduction;

            // ── 4. Aplicar ganho + dry/wet (NEON) ─────────────────────────────────
            // Fusão de dry/wet num único multiplicador:
            //   out = in*(1-mix) + in*gainReduction*makeup*mix
            //       = in * [(1-mix) + gainReduction*makeup*mix]
            //       = in * combinedGain
            // → 1 vmulq_f32 por 4 amostras, sem segunda leitura de in[]
            const float combinedGain = (1.0f - curMix) + gainReduction * curMakeup * curMix;
            const float estOutPeak   = blockPeak * combinedGain;
            if (estOutPeak > currentOutputPeak) currentOutputPeak = estOutPeak;

#ifdef __ARM_NEON
            const float32x4_t vGain = vdupq_n_f32(combinedGain);
            j = i;
            for (; j <= blockEnd - 4; j += 4) {
                vst1q_f32(left  + j, vmulq_f32(vld1q_f32(left  + j), vGain));
                vst1q_f32(right + j, vmulq_f32(vld1q_f32(right + j), vGain));
            }
            for (; j < blockEnd; j++) { left[j] *= combinedGain; right[j] *= combinedGain; }
#else
            for (int j = i; j < blockEnd; j++) {
                left[j]  *= combinedGain;
                right[j] *= combinedGain;
            }
#endif
            i = blockEnd;
        }

        lastInputPeak        = currentInputPeak;
        lastOutputPeak       = currentOutputPeak;
        lastGainReductionDb  = (currentMaxGR < 0.999f) ? 20.0f * std::log10(currentMaxGR) : 0.0f;
    }

    void setParam(int paramId, float value) override {
        switch(paramId) {
            case 0: threshSmooth.setTarget(std::pow(10.0f, std::min(value, 0.0f) / 20.0f)); break;
            case 1: ratioSmooth.setTarget(std::max(1.0f, value)); break;
            case 2: attackCoeff  = 1.0f - std::exp(-1.0f / (sampleRate * (std::max(value, 0.1f) / 1000.0f))); break;
            case 3: releaseCoeff = 1.0f - std::exp(-1.0f / (sampleRate * (std::max(value, 5.0f)  / 1000.0f))); break;
            case 4: makeupSmooth.setTarget(std::pow(10.0f, std::max(value, 0.0f) / 20.0f)); break;
            case 5: kneeSmooth.setTarget(std::max(0.0f, std::min(value, 12.0f))); break;
            case 6: mixSmooth.setTarget(std::max(0.0f, std::min(value, 1.0f))); break;
        }
    }

    int getType() const override { return 7; }

    void getMeters(float* input, float* output, float* gr) override {
        if (input)  *input  = lastInputPeak;
        if (output) *output = lastOutputPeak;
        if (gr)     *gr     = lastGainReductionDb;
    }

    void setSampleRate(float fs) override { sampleRate = fs; }

private:
    SmoothedParam threshSmooth, ratioSmooth, makeupSmooth, kneeSmooth, mixSmooth;

    float attackCoeff  = 0.01f;
    float releaseCoeff = 0.001f;
    float envelope     = 0.0f;
    float sampleRate   = 44100.0f;

    // Meter states
    float lastInputPeak       = 0.0f;
    float lastOutputPeak      = 0.0f;
    float lastGainReductionDb = 0.0f;
};

/**
 * Tremolo (LFO Senoidal)
 */
class TremoloEffect : public DSPEffect {
public:
    TremoloEffect() : depthSmooth(0.5f, 0.997f), mixSmooth(1.0f, 0.997f) {}
    
    void processStereo(float* left, float* right, int numSamples) override {
        // Soft-bypass handled by DSPChannel crossfade
        for (int i = 0; i < numSamples; ++i) {
            // Atualizar parâmetros suavizados a cada 16 samples
            if ((i & 15) == 0) {
                cachedDepth = depthSmooth.getNext();
                cachedMix = mixSmooth.getNext();
            }
            
            float lfo = 1.0f - (cachedDepth * 0.5f * (1.0f + std::sin(phase)));
            
            // Wet signal
            float wetL = left[i] * lfo;
            float wetR = right[i] * lfo;
            
            // Mix Dry/Wet
            left[i] = (left[i] * (1.0f - cachedMix)) + (wetL * cachedMix);
            right[i] = (right[i] * (1.0f - cachedMix)) + (wetR * cachedMix);
            
            phase += phaseIncrement;
            if (phase > 2.0f * M_PI) phase -= 2.0f * M_PI;
        }
    }
    
    void setParam(int paramId, float value) override {
        if (paramId == 0) { // Rate (0.1 to 10 Hz)
            lastRate = value;
            updateLFO();
        } else if (paramId == 1) { // Depth (0.0 to 1.0)
            depthSmooth.setTarget(value);
        } else if (paramId == 2) { // Mix (0.0 to 1.0)
            mixSmooth.setTarget(value);
        }
    }
    
    void setSampleRate(float fs) override {
        sampleRate = fs;
        updateLFO();
    }
    
    int getType() const override { return 6; }
private:
    void updateLFO() {
        phaseIncrement = (2.0f * M_PI * lastRate) / sampleRate;
    }
    float phase = 0.0f;
    float phaseIncrement = (2.0f * M_PI * 1.0f) / 44100.0f;
    float lastRate = 1.0f;
    SmoothedParam depthSmooth, mixSmooth;
    float cachedDepth = 0.5f, cachedMix = 1.0f;
    float sampleRate = 44100.0f;
};

/**
 * LimiterEffect NEON (Punch — Lookahead Peak Limiter com Tanh Saturation)
 *
 * Pipeline por bloco de 4 samples:
 *
 *  Stage 1 — NEON: fastTanh(in × drive) × tanhNorm
 *    Padé racional [3/2]: sat = x*(27+x²)/(27+9x²)
 *    Vetorizável porque é aritmética pura (sem transcendentais).
 *    Reciprocal via vrecpeq_f32 + 1 Newton-Raphson step (precisão <0.04%).
 *
 *  Stages 2-3 — Scalar: envelope follow + gain (dependência serial obrigatória)
 *    Envelope: y[n] = y[n-1] + coeff*(peak[n] - y[n-1]) — não paralelizável.
 *    Gain: brick-wall threshold / envelope.
 *
 *  Stages 4-5 — NEON (hot path, sem wrap):
 *    Delay: vld1q_f32 + vst1q_f32 direto no buffer lookahead (alinhado 16 bytes).
 *    Output: vmulq_f32(delayed, gain × makeup) — 1 instrução/4 samples.
 *    Wrap (~1 em 16 blocos): fallback scalar.
 */
class LimiterEffect : public DSPEffect {
public:
    LimiterEffect() {
        memset(delayL, 0, sizeof(delayL));
        memset(delayR, 0, sizeof(delayR));
        updateCoeffs();
    }

    void processStereo(float* __restrict__ left, float* __restrict__ right, int numSamples) override {
        int i = 0;

#ifdef __ARM_NEON
        const float32x4_t vDrive    = vdupq_n_f32(drive);
        const float32x4_t vTanhNorm = vdupq_n_f32(tanhNorm);
        const float32x4_t vMakeup   = vdupq_n_f32(makeupGain);
        const float32x4_t v27       = vdupq_n_f32(27.0f);
        const float32x4_t v9        = vdupq_n_f32(9.0f);

        for (; i <= numSamples - 4; i += 4) {
            // ── Stage 1 NEON: fastTanh Padé para 4 samples (L e R) ───────────────
            // fastTanh(x) = x*(27+x²)/(27+9x²)  — Padé [3/2] racional
            // Recíproco: vrecpeq (estimativa) + 1× vrecpsq (Newton-Raphson)
            auto neonTanh4 = [&](float32x4_t x) -> float32x4_t {
                const float32x4_t x2  = vmulq_f32(x, x);
                const float32x4_t num = vmulq_f32(x, vaddq_f32(v27, x2)); // x*(27+x²)
                const float32x4_t den = vmlaq_f32(v27, v9, x2);            // 27+9x²
                // 1-step Newton-Raphson: rden ≈ 1/den, precisão ~23 bits (suficiente para áudio)
                float32x4_t rden = vrecpeq_f32(den);
                rden = vmulq_f32(vrecpsq_f32(den, rden), rden);
                return vmulq_f32(vmulq_f32(num, rden), vTanhNorm);
            };

            const float32x4_t satL4 = neonTanh4(vmulq_f32(vld1q_f32(left  + i), vDrive));
            const float32x4_t satR4 = neonTanh4(vmulq_f32(vld1q_f32(right + i), vDrive));

            // ── Stages 2-3: envelope + gain (scalar — dependência serial) ─────────
            alignas(16) float satL_a[4], satR_a[4], gain_a[4];
            vst1q_f32(satL_a, satL4);
            vst1q_f32(satR_a, satR4);

            for (int j = 0; j < 4; j++) {
                const float absL = satL_a[j] < 0.0f ? -satL_a[j] : satL_a[j];
                const float absR = satR_a[j] < 0.0f ? -satR_a[j] : satR_a[j];
                const float pk   = absL > absR ? absL : absR;

                // Envelope: attack suavizado (~1ms) + release configurável
                envelope += (pk > envelope ? attackCoeff : releaseCoeff) * (pk - envelope);

                // Gain: brick wall — sem overshoot graças ao lookahead
                gain_a[j] = (envelope > threshold) ? (threshold / envelope) : 1.0f;
            }

            // ── Stages 4-5: delay lookahead + output (NEON / scalar fallback) ─────
            const int d = delayIdx;

            if (__builtin_expect(d + 4 <= LOOKAHEAD, 1)) {
                // Hot path: sem wrap no delay buffer — NEON puro
                const float32x4_t dL4 = vld1q_f32(&delayL[d]);
                const float32x4_t dR4 = vld1q_f32(&delayR[d]);
                vst1q_f32(&delayL[d], satL4);       // grava saturado no lookahead
                vst1q_f32(&delayR[d], satR4);
                delayIdx = (d + 4) & (LOOKAHEAD - 1);

                // out = delayed * gain * makeup — 1 vmulq por canal
                const float32x4_t vGMup = vmulq_f32(vld1q_f32(gain_a), vMakeup);
                vst1q_f32(left  + i, vmulq_f32(dL4, vGMup));
                vst1q_f32(right + i, vmulq_f32(dR4, vGMup));
            } else {
                // Wrap-around: scalar (ocorre 1× a cada 16 blocos NEON = cada 64 samples)
                for (int j = 0; j < 4; j++) {
                    const float outL = delayL[delayIdx];
                    const float outR = delayR[delayIdx];
                    delayL[delayIdx] = satL_a[j];
                    delayR[delayIdx] = satR_a[j];
                    delayIdx = (delayIdx + 1) & (LOOKAHEAD - 1);
                    left[i + j]  = outL * gain_a[j] * makeupGain;
                    right[i + j] = outR * gain_a[j] * makeupGain;
                }
            }
        }
#endif // __ARM_NEON

        // Scalar tail — amostras restantes (< 4) ou build non-NEON
        for (; i < numSamples; ++i) {
            // Stage 1: tanh saturation
            const float satL = fastTanh(left[i]  * drive) * tanhNorm;
            const float satR = fastTanh(right[i] * drive) * tanhNorm;

            // Stage 2: envelope
            const float peak = std::max(std::abs(satL), std::abs(satR));
            if (peak > envelope)
                envelope += attackCoeff  * (peak - envelope);
            else
                envelope += releaseCoeff * (peak - envelope);

            // Stage 3: gain
            const float gain = (envelope > threshold) ? (threshold / envelope) : 1.0f;

            // Stage 4: lookahead delay
            const float outL = delayL[delayIdx];
            const float outR = delayR[delayIdx];
            delayL[delayIdx] = satL;
            delayR[delayIdx] = satR;
            delayIdx = (delayIdx + 1) & (LOOKAHEAD - 1);

            // Stage 5: output
            left[i]  = outL * gain * makeupGain;
            right[i] = outR * gain * makeupGain;
        }
    }

    void setParam(int paramId, float value) override {
        if (paramId == 0) {
            threshold  = std::pow(10.0f, value / 20.0f);
            makeupGain = 1.0f / std::max(threshold, 0.1f);
        } else if (paramId == 1) {
            lastReleaseMs = value;
            updateCoeffs();
        }
    }

    void setSampleRate(float fs) override {
        sampleRate = fs;
        updateCoeffs();
    }

    int getType() const override { return 8; }

private:
    static constexpr int LOOKAHEAD = 64;   // ~1.3ms @ 48kHz (power-of-2 para mask)

    // Buffers alinhados 16 bytes — permite vld1q_f32/vst1q_f32 direto
    alignas(16) float delayL[LOOKAHEAD] = {};
    alignas(16) float delayR[LOOKAHEAD] = {};
    int delayIdx = 0;

    float envelope    = 0.0f;
    float attackCoeff = 0.0f;
    float releaseCoeff = 0.0f;

    float threshold  = 0.891f;        // -1dB
    float drive      = 1.5f;          // 1.0=limpo, 1.5=warm, 2.0=agressivo
    float tanhNorm   = 1.0f / 0.905f; // 1/tanh(1.5) — normalização de amplitude
    float makeupGain = 1.122f;        // compensação de loudness
    float sampleRate  = 48000.0f;
    float lastReleaseMs = 200.0f;

    // Padé [3/2] scalar — usado no tail e fallback
    static inline float fastTanh(float x) {
        const float x2 = x * x;
        return x * (27.0f + x2) / (27.0f + 9.0f * x2);
    }

    void updateCoeffs() {
        attackCoeff  = 1.0f - std::exp(-1.0f / (sampleRate * 0.001f));
        releaseCoeff = 1.0f - std::exp(-1.0f / (sampleRate * (lastReleaseMs / 1000.0f)));
    }
};

/**
 * FDN Reverb Estéreo (Feedback Delay Network) — NEON
 *
 * Substitui STK FreeVerb por FDN com N=8 delay lines.
 * Arquitetura:
 *   input → N delay lines → Hadamard H8 (feedback matrix) → output
 *                 ↑_________________damping filter___________|
 *
 * Vantagens vs FreeVerb:
 *  - Decay mais difuso e sem coloração metálica (sem comb filter artifacts)
 *  - Naturalmente vetorizável: damping e scaling em float32x4_t × 2
 *  - ~2-3× menor custo de DSP (benchmark estimado: 12-15µs → 4-6µs/canal)
 *
 * NEON:
 *  - Damping: 8 filtros one-pole → 2 vmlaq_f32 (em vez de 8 scalar)
 *  - Scaling: 8 valores × gain → 2 vmulq_f32
 *  - Output sum estéreo: vpadd horizontal de 4+4 linhas
 *
 * Parâmetros: 0:RoomSize (0.1–1.0), 1:Damping (0–0.99), 2:Mix (0–1), 3:Width (0–1)
 */
class ReverbEffect : public DSPEffect {
public:
    static constexpr int   NUM_LINES = 8;
    static constexpr float H8_NORM   = 0.35355f;  // 1/sqrt(8) — normalização Hadamard
    static constexpr int   MAX_LINE  = 3600;       // samples/linha (≥ base_max × 1.5)

    // Delay lengths: primos para mínima correlação entre linhas @ 44-48kHz
    static constexpr int BASE_DELAY[NUM_LINES] = {
        1559, 1637, 1789, 1847,
        1931, 2039, 2179, 2243
    };

    explicit ReverbEffect(float* globalInL, float* globalInR)
        : inL(globalInL), inR(globalInR),
          roomSmooth(0.7f, 0.999f), dampSmooth(0.3f, 0.999f),
          mixSmooth(1.0f, 0.999f),  widthSmooth(0.5f, 0.999f)
    {
        for (int i = 0; i < NUM_LINES; i++) {
            if (posix_memalign((void**)&lineBuf[i], 16, MAX_LINE * sizeof(float)) != 0)
                lineBuf[i] = new float[MAX_LINE];
            memset(lineBuf[i], 0, MAX_LINE * sizeof(float));
            wPos[i] = 0;
        }
        memset(dampState, 0, sizeof(dampState));
        applyRoom(0.7f, 0.3f, 0.5f);
    }

    ~ReverbEffect() override {
        for (int i = 0; i < NUM_LINES; i++) free(lineBuf[i]);
    }

    void processStereo(float* left, float* right, int numSamples) override {
        const float* srcL = inL ? inL : left;
        const float* srcR = inR ? inR : right;

        // Detecção de sinal ativo (evita processar silêncio)
        bool hasInput = false;
        for (int n = 0; n < numSamples; n++) {
            if (fabsf(srcL[n]) + fabsf(srcR[n]) > 3e-4f) { hasInput = true; break; }
        }
        if (hasInput) { wasProcessing = true; tailTimer = tailMax; }
        if (!wasProcessing) return;

        // Atualizar parâmetros (uma vez por bloco — econômico)
        applyRoom(roomSmooth.getNext(), dampSmooth.getNext(), widthSmooth.getNext());
        const float curMix = mixSmooth.getNext();

        // Estados locais do DC filter (reduz acesso à memória no loop)
        float lX = l_prevX, lY = l_prevY, rX = r_prevX, rY = r_prevY;

#ifdef __ARM_NEON
        const float32x4_t v1mDamp = vdupq_n_f32(1.0f - curDamp);
        const float32x4_t vDamp   = vdupq_n_f32(curDamp);
        const float32x4_t vScale  = vdupq_n_f32(H8_NORM * curFeedback);
        // Carregar estado do damping filter nos registradores NEON
        float32x4_t vDS0 = vld1q_f32(dampState);
        float32x4_t vDS1 = vld1q_f32(dampState + 4);
#endif

        bool hasOutput = false;

        for (int n = 0; n < numSamples; n++) {
            // DC blocking na entrada
            const float xL = srcL[n], xR = srcR[n];
            const float dcL = xL - lX + 0.995f * lY;
            const float dcR = xR - rX + 0.995f * rY;
            lX = xL; lY = dcL;
            rX = xR; rY = dcR;
            const float monoIn = (dcL + dcR) * 0.5f;

            // Leitura das 8 delay lines (scalar — offsets distintos)
            float v[8];
            for (int k = 0; k < NUM_LINES; k++) {
                int rIdx = wPos[k] - curDelay[k];
                if (rIdx < 0) rIdx += MAX_LINE;
                v[k] = lineBuf[k][rIdx];
            }

#ifdef __ARM_NEON
            // One-pole damping filter: state = v*(1-d) + state*d
            float32x4_t vv0 = vld1q_f32(v);
            float32x4_t vv1 = vld1q_f32(v + 4);
            vDS0 = vmlaq_f32(vmulq_f32(vv0, v1mDamp), vDS0, vDamp);
            vDS1 = vmlaq_f32(vmulq_f32(vv1, v1mDamp), vDS1, vDamp);

            // Extrair para array local para o butterfly Hadamard
            float u[8];
            vst1q_f32(u,     vDS0);
            vst1q_f32(u + 4, vDS1);
#else
            for (int k = 0; k < NUM_LINES; k++)
                dampState[k] = v[k] * (1.0f - curDamp) + dampState[k] * curDamp;
            float u[8];
            memcpy(u, dampState, sizeof(u));
#endif

            // Hadamard H8 in-place — 12 adições (custo fixo, sem branches)
            // Stage 1: pares (N/2=4 butterflies)
            { float a=u[0],b=u[1]; u[0]=a+b; u[1]=a-b; }
            { float a=u[2],b=u[3]; u[2]=a+b; u[3]=a-b; }
            { float a=u[4],b=u[5]; u[4]=a+b; u[5]=a-b; }
            { float a=u[6],b=u[7]; u[6]=a+b; u[7]=a-b; }
            // Stage 2: quads
            { float a=u[0],b=u[2]; u[0]=a+b; u[2]=a-b; }
            { float a=u[1],b=u[3]; u[1]=a+b; u[3]=a-b; }
            { float a=u[4],b=u[6]; u[4]=a+b; u[6]=a-b; }
            { float a=u[5],b=u[7]; u[5]=a+b; u[7]=a-b; }
            // Stage 3: cruzamento entre os dois grupos
            { float a=u[0],b=u[4]; u[0]=a+b; u[4]=a-b; }
            { float a=u[1],b=u[5]; u[1]=a+b; u[5]=a-b; }
            { float a=u[2],b=u[6]; u[2]=a+b; u[6]=a-b; }
            { float a=u[3],b=u[7]; u[3]=a+b; u[7]=a-b; }

#ifdef __ARM_NEON
            // Escalar o resultado Hadamard por (H8_NORM × feedbackGain)
            vst1q_f32(u,     vmulq_f32(vld1q_f32(u),     vScale));
            vst1q_f32(u + 4, vmulq_f32(vld1q_f32(u + 4), vScale));
#else
            const float sc = H8_NORM * curFeedback;
            for (int k = 0; k < NUM_LINES; k++) u[k] *= sc;
#endif

            // Escrita de volta nas delay lines
            for (int k = 0; k < NUM_LINES; k++) {
                lineBuf[k][wPos[k]] = monoIn + u[k];
                if (++wPos[k] >= MAX_LINE) wPos[k] = 0;
            }

#ifdef __ARM_NEON
            // Soma estéreo via horizontal add NEON
            // L = soma das 4 primeiras linhas, R = soma das 4 últimas
            const float32x2_t sumL2 = vpadd_f32(
                vpadd_f32(vget_low_f32(vDS0), vget_high_f32(vDS0)),
                vdup_n_f32(0.0f));
            const float32x2_t sumR2 = vpadd_f32(
                vpadd_f32(vget_low_f32(vDS1), vget_high_f32(vDS1)),
                vdup_n_f32(0.0f));
            float outL = vget_lane_f32(sumL2, 0) * 0.25f * curMix;
            float outR = vget_lane_f32(sumR2, 0) * 0.25f * curMix;
#else
            float outL = (dampState[0]+dampState[1]+dampState[2]+dampState[3]) * 0.25f * curMix;
            float outR = (dampState[4]+dampState[5]+dampState[6]+dampState[7]) * 0.25f * curMix;
#endif

            // Controle de largura estéreo (M/S)
            const float mid  = outL + outR;
            const float side = (outL - outR) * curWidth;
            outL = mid + side;
            outR = mid - side;

            if (fabsf(outL) + fabsf(outR) > 3e-5f) hasOutput = true;
            left[n]  += outL;
            right[n] += outR;
        }

#ifdef __ARM_NEON
        // Persistir estado do damping filter de volta à memória
        vst1q_f32(dampState,     vDS0);
        vst1q_f32(dampState + 4, vDS1);
#endif
        l_prevX=lX; l_prevY=lY; r_prevX=rX; r_prevY=rY;

        if (hasInput || hasOutput) {
            tailTimer = tailMax;
        } else {
            tailTimer -= numSamples;
            if (tailTimer <= 0) {
                for (int i = 0; i < NUM_LINES; i++)
                    if (lineBuf[i]) memset(lineBuf[i], 0, MAX_LINE * sizeof(float));
                memset(dampState, 0, sizeof(dampState));
                wasProcessing = false;
                tailTimer = 0;
            }
        }
    }

    bool hasActiveTails() const override { return wasProcessing; }

    void setParam(int paramId, float value) override {
        if      (paramId == 0) roomSmooth.setTarget(std::max(0.1f, std::min(value, 1.0f)));
        else if (paramId == 1) dampSmooth.setTarget(std::max(0.0f, std::min(value, 0.99f)));
        else if (paramId == 2) mixSmooth.setTarget(std::max(0.0f, std::min(value, 1.0f)));
        else if (paramId == 3) widthSmooth.setTarget(std::max(0.0f, std::min(value, 1.0f)));
    }

    int getType() const override { return 4; }

private:
    void applyRoom(float roomSize, float damp, float width) {
        curDamp     = damp;
        curWidth    = width;
        curFeedback = 0.5f + roomSize * 0.45f;   // 0.55 – 0.95
        const float scale = 0.5f + roomSize;      // 0.6 – 1.5 × base delays
        for (int i = 0; i < NUM_LINES; i++)
            curDelay[i] = std::max(1, std::min((int)(BASE_DELAY[i] * scale), MAX_LINE - 1));
    }

    float* inL = nullptr;
    float* inR = nullptr;

    float* lineBuf[NUM_LINES] = {};      // heap-alloc, 16-byte aligned
    int    wPos[NUM_LINES]    = {};

    alignas(16) float dampState[NUM_LINES] = {};  // estado do filtro damping (8 × float)

    int   curDelay[NUM_LINES] = {};
    float curDamp     = 0.3f;
    float curFeedback = 0.8f;
    float curWidth    = 0.5f;

    SmoothedParam roomSmooth, dampSmooth, mixSmooth, widthSmooth;

    bool wasProcessing = false;
    int  tailTimer     = 0;
    const int tailMax  = 48000 * 3;  // 3s de cauda

    float l_prevX = 0, l_prevY = 0;
    float r_prevX = 0, r_prevY = 0;
};

/**
 * Delay Estéreo NEON — Ring Buffer customizado, vetorizado com ARM NEON
 *
 * Mudanças vs versão anterior:
 *  - Ring buffer heap-alocado (65536 samples, power-of-2) substitui stk::DelayL
 *  - ARM NEON processa 4 samples/iteração (vld1q_f32 / vst1q_f32 / vmlaq_f32)
 *  - Feedback soft-clip: clip linear [-1,1] via vminq/vmaxq (seguro com fb ≤ 0.75)
 *  - Índice circular via máscara binária (&DELAY_MASK) — zero divisões
 *  - Fallback scalar para bordas do buffer (wrap-around ~1 em 16384 blocos)
 */
class DelayEffect : public DSPEffect {
public:
    static constexpr int MAX_DELAY = 65536;     // power-of-2 para máscara
    static constexpr int DELAY_MASK = MAX_DELAY - 1;

    DelayEffect() : timeSmooth(22050.0f, 0.999f), fbSmooth(0.3f, 0.997f), mixSmooth(0.5f, 0.997f) {
        // Aloca buffers NEON-alinhados (16 bytes) no heap
        if (posix_memalign((void**)&bufL, 16, MAX_DELAY * sizeof(float)) != 0) bufL = new float[MAX_DELAY];
        if (posix_memalign((void**)&bufR, 16, MAX_DELAY * sizeof(float)) != 0) bufR = new float[MAX_DELAY];
        memset(bufL, 0, MAX_DELAY * sizeof(float));
        memset(bufR, 0, MAX_DELAY * sizeof(float));
    }

    ~DelayEffect() override {
        free(bufL);
        free(bufR);
    }

    void processStereo(float* __restrict__ left, float* __restrict__ right, int numSamples) override {
        int i = 0;

#ifdef __ARM_NEON
        const float32x4_t v_mix  = vdupq_n_f32(cachedMix);
        const float32x4_t v_dry  = vdupq_n_f32(1.0f - cachedMix);
        const float32x4_t v_fb   = vdupq_n_f32(cachedFb);
        const float32x4_t v_pos1 = vdupq_n_f32(1.0f);
        const float32x4_t v_neg1 = vdupq_n_f32(-1.0f);

        for (; i <= numSamples - 4; i += 4) {
            // Atualizar parâmetros suavizados a cada 8 samples
            if ((i & 7) == 0) {
                float t = timeSmooth.getNext();
                delaySamples = std::max(1, std::min((int)t, MAX_DELAY - 1));
                cachedFb  = fbSmooth.getNext();
                cachedMix = mixSmooth.getNext();
            }

            const int wIdx = (writeIdx + i) & DELAY_MASK;
            const int rIdx = (writeIdx + i - delaySamples + MAX_DELAY * 2) & DELAY_MASK;

            // Verificar se há wrap-around neste chunk de 4 samples
            if (__builtin_expect((wIdx + 4 > MAX_DELAY) | (rIdx + 4 > MAX_DELAY), 0)) {
                // Scalar fallback — raro (~1 vez a cada 16384 blocos)
                for (int j = 0; j < 4; j++) {
                    const int wi = (writeIdx + i + j) & DELAY_MASK;
                    const int ri = (writeIdx + i + j - delaySamples + MAX_DELAY * 2) & DELAY_MASK;
                    const float dL = bufL[ri], dR = bufR[ri];
                    const float fbL = std::max(-1.0f, std::min(dL * cachedFb, 1.0f));
                    const float fbR = std::max(-1.0f, std::min(dR * cachedFb, 1.0f));
                    bufL[wi] = left[i + j] + fbL;
                    bufR[wi] = right[i + j] + fbR;
                    left[i + j]  = left[i + j]  * (1.0f - cachedMix) + dL * cachedMix;
                    right[i + j] = right[i + j] * (1.0f - cachedMix) + dR * cachedMix;
                }
                continue;
            }

            // Hot path NEON
            const float32x4_t delL = vld1q_f32(&bufL[rIdx]);
            const float32x4_t delR = vld1q_f32(&bufR[rIdx]);

            // Feedback: clip linear em vez de tanh (seguro com fb ≤ 0.75)
            const float32x4_t fbL4 = vminq_f32(vmaxq_f32(vmulq_f32(delL, v_fb), v_neg1), v_pos1);
            const float32x4_t fbR4 = vminq_f32(vmaxq_f32(vmulq_f32(delR, v_fb), v_neg1), v_pos1);

            const float32x4_t inL4 = vld1q_f32(left  + i);
            const float32x4_t inR4 = vld1q_f32(right + i);

            // Escrever no delay buffer: input + feedback
            vst1q_f32(&bufL[wIdx], vaddq_f32(inL4, fbL4));
            vst1q_f32(&bufR[wIdx], vaddq_f32(inR4, fbR4));

            // Saída: dry + wet
            // out = in * (1-mix) + del * mix  →  vmlaq_f32(in*dry, del, mix)
            const float32x4_t outL4 = vmlaq_f32(vmulq_f32(inL4, v_dry), delL, v_mix);
            const float32x4_t outR4 = vmlaq_f32(vmulq_f32(inR4, v_dry), delR, v_mix);

            vst1q_f32(left  + i, outL4);
            vst1q_f32(right + i, outR4);
        }
#endif // __ARM_NEON

        // Scalar tail (remainder após chunks NEON, ou compilação non-NEON)
        for (; i < numSamples; i++) {
            if ((i & 7) == 0) {
                float t = timeSmooth.getNext();
                delaySamples = std::max(1, std::min((int)t, MAX_DELAY - 1));
                cachedFb  = fbSmooth.getNext();
                cachedMix = mixSmooth.getNext();
            }
            const int wi = (writeIdx + i) & DELAY_MASK;
            const int ri = (writeIdx + i - delaySamples + MAX_DELAY * 2) & DELAY_MASK;
            const float dL = bufL[ri], dR = bufR[ri];
            const float fbL = std::tanh(dL * cachedFb);
            const float fbR = std::tanh(dR * cachedFb);
            bufL[wi] = left[i]  + fbL;
            bufR[wi] = right[i] + fbR;
            left[i]  = left[i]  * (1.0f - cachedMix) + dL * cachedMix;
            right[i] = right[i] * (1.0f - cachedMix) + dR * cachedMix;
        }

        writeIdx = (writeIdx + numSamples) & DELAY_MASK;
        // Atualizar tails para hasActiveTails()
        const int lastRi = (writeIdx - delaySamples - 1 + MAX_DELAY * 2) & DELAY_MASK;
        lastDelayL = bufL[lastRi];
        lastDelayR = bufR[lastRi];
    }

    void setParam(int paramId, float value) override {
        if (paramId == 0) { // Time (ms) — max ~1485ms @ 44kHz
            float delaySamplesF = sampleRate * (value / 1000.0f);
            delaySamplesF = std::max(1.0f, std::min(delaySamplesF, (float)(MAX_DELAY - 1)));
            timeSmooth.setImmediate(delaySamplesF); // Salto instantâneo: evita Pitch Shifting
        } else if (paramId == 1) {
            fbSmooth.setTarget((value < 0.0f) ? 0.0f : (value > 0.75f) ? 0.75f : value);
        } else if (paramId == 2) {
            mixSmooth.setTarget((value < 0.0f) ? 0.0f : (value > 1.0f) ? 1.0f : value);
        }
    }

    void setSampleRate(float fs) override { sampleRate = fs; }
    int getType() const override { return 3; }

    bool hasActiveTails() const override {
        return std::abs(lastDelayL) > 0.0001f || std::abs(lastDelayR) > 0.0001f;
    }

private:
    float* bufL = nullptr;          // Ring buffer L (heap, 16-byte aligned)
    float* bufR = nullptr;          // Ring buffer R (heap, 16-byte aligned)
    int    writeIdx    = 0;
    int    delaySamples = 22050;    // Delay corrente em samples (inteiro)
    SmoothedParam timeSmooth, fbSmooth, mixSmooth;
    float cachedFb  = 0.3f;
    float cachedMix = 0.5f;
    float lastDelayL = 0.0f;
    float lastDelayR = 0.0f;
    float sampleRate = 44100.0f;
};

/**
 * Canal DSP Modular (Membro do Rack)
 */
class DSPChannel {
public:
    void process(float* left, float* right, int numSamples) {
        if (numSamples > MAX_AUDIO_FRAME_SIZE) return;
        static thread_local float dryL[MAX_AUDIO_FRAME_SIZE];
        static thread_local float dryR[MAX_AUDIO_FRAME_SIZE];
        
        for (auto& effect : effects) {
            bool isSmoothing = effect->bypassFade.isSmoothing();
            float currentTarget = effect->bypassFade.getTarget();
            
            // Optimization: skip if disabled and not fading
            if (currentTarget < 0.01f && !isSmoothing) continue;
            
            if (isSmoothing) {
                // Pre-Effect Backup for Crossfade
                std::copy(left, left + numSamples, dryL);
                std::copy(right, right + numSamples, dryR);
                
                effect->processStereo(left, right, numSamples);
                
                // Fine Crossfade Mix
                for (int i = 0; i < numSamples; ++i) {
                    float fade = effect->bypassFade.getNext();
                    left[i] = dryL[i] * (1.0f - fade) + left[i] * fade;
                    right[i] = dryR[i] * (1.0f - fade) + right[i] * fade;
                }
            } else {
                // Fully On - Normal Process
                effect->processStereo(left, right, numSamples);
            }
        }
    }

    bool hasActiveTails() const {
        for (auto& fx : effects) {
            if (fx->enabled && fx->hasActiveTails()) return true;
        }
        return false;
    }

    void addEffect(std::unique_ptr<DSPEffect> effect) {
        effects.push_back(std::move(effect));
        
        // Sorting disabled to maintain index synchronization with Kotlin
        /*
        std::stable_sort(effects.begin(), effects.end(), [](const std::unique_ptr<DSPEffect>& a, const std::unique_ptr<DSPEffect>& b) {
            return getPriority(a->getType()) < getPriority(b->getType());
        });
        */
    }

    static int getPriority(int typeId) {
        switch(typeId) {
            case 1: return 1;  // HPF
            case 2: return 2;  // LPF
            case 7: return 3;  // COMPRESSOR
            case 0: return 4;  // EQ_PARAMETRIC
            case 5: return 5;  // CHORUS
            case 6: return 6;  // TREMOLO
            case 3: return 7;  // DELAY
            case 4: return 8;  // REVERB
            case 9: return 9;  // REVERB_SEND
            case 8: return 10; // LIMITER
            default: return 99;
        }
    }

    void removeEffect(int index) {
        if (index >= 0 && index < (int)effects.size()) {
            effects.erase(effects.begin() + index);
        }
    }

    void clearEffects() {
        effects.clear();
    }

    std::vector<std::unique_ptr<DSPEffect> >& getEffects() { return effects; }

private:
    std::vector<std::unique_ptr<DSPEffect>> effects;
};

/**
 * Gerenciador da Cadeia de Áudio (Mixer Nativo)
 */
class DSPChain {
public:
    DSPChain() {
        for (int i = 0; i < 16; ++i) {
            std::unique_ptr<DSPChannel> channel(new DSPChannel());
            channels.push_back(std::move(channel));
        }
        
        // Master Rack Modular (ch = -1) starts empty
        masterChannel.reset(new DSPChannel());
    }

    DSPChannel* getChannel(int ch) {
        if (ch >= 0 && ch < (int)channels.size()) return channels[ch].get();
        return nullptr;
    }

    DSPChannel* getMasterChannel() {
        return masterChannel.get();
    }

    void prepare(int numSamples, float sr = -1.0f) {
        if (sr > 0) {
            if (std::abs(sampleRate - sr) > 0.1f) {
                LOG_DSP("SAMPLE RATE CHANGE: %.1f -> %.1f", sampleRate, sr);
                stk::Stk::setSampleRate(sr);
            }
            sampleRate = sr;
        }
        if (numSamples > MAX_AUDIO_FRAME_SIZE) numSamples = MAX_AUDIO_FRAME_SIZE;
        memset(reverbInL, 0, numSamples * sizeof(float));
        memset(reverbInR, 0, numSamples * sizeof(float));
    }

    void processChannel(int ch, float* left, float* right, int numSamples) {
        if (ch >= 0 && ch < 16) {
            channels[ch]->process(left, right, numSamples);
        }
    }

    void processMaster(float* outL, float* outR, int numSamples) {
        if (masterChannel) {
            masterChannel->process(outL, outR, numSamples);
        }
    }

    bool hasActiveTails() const {
        if (masterChannel && masterChannel->hasActiveTails()) return true;
        for (auto& ch : channels) {
            if (ch->hasActiveTails()) return true;
        }
        return false;
    }

    void addEffect(int ch, int type) {
        DSPChannel* target = nullptr;
        if (ch == -1) target = masterChannel.get();
        else if (ch >= 0 && ch < 16) target = channels[ch].get();

        if (target) {
            switch(type) {
                case 0: {
                    auto eq = new ParametricEQEffect();
                    eq->setSampleRate(sampleRate);
                    target->addEffect(std::unique_ptr<DSPEffect>(eq)); 
                    break;
                }
                case 1: {
                    auto f = new FilterEffect(true);
                    f->setSampleRate(sampleRate);
                    target->addEffect(std::unique_ptr<DSPEffect>(f));
                    break;
                }
                case 2: {
                    auto f = new FilterEffect(false);
                    f->setSampleRate(sampleRate);
                    target->addEffect(std::unique_ptr<DSPEffect>(f));
                    break;
                }
                case 3: {
                    auto d = new DelayEffect();
                    d->setSampleRate(sampleRate);
                    target->addEffect(std::unique_ptr<DSPEffect>(d));
                    break;
                }
                case 4: target->addEffect(std::unique_ptr<DSPEffect>(new ReverbEffect(reverbInL, reverbInR))); break;
                case 5: target->addEffect(std::unique_ptr<DSPEffect>(new ChorusEffect())); break;
                case 6: {
                    auto t = new TremoloEffect();
                    t->setSampleRate(sampleRate);
                    target->addEffect(std::unique_ptr<DSPEffect>(t));
                    break;
                }
                case 7: {
                    auto c = new CompressorEffect();
                    c->setSampleRate(sampleRate);
                    target->addEffect(std::unique_ptr<DSPEffect>(c));
                    break;
                }
                case 8: {
                    auto l = new LimiterEffect();
                    l->setSampleRate(sampleRate);
                    target->addEffect(std::unique_ptr<DSPEffect>(l));
                    break;
                }
                case 9: target->addEffect(std::unique_ptr<DSPEffect>(new SendBusEffect(reverbInL, reverbInR))); break;
            }
        }
    }

    void removeEffect(int ch, int index) {
        if (ch == -1 && masterChannel) {
            masterChannel-> removeEffect(index);
        } else if (ch >= 0 && ch < 16) {
            channels[ch]->removeEffect(index);
        }
    }

    void clearEffects(int ch) {
        if (ch == -1 && masterChannel) {
            masterChannel->clearEffects();
        } else if (ch >= 0 && ch < 16) {
            channels[ch]->clearEffects();
        }
    }

    void setEffectParam(int ch, int effectIdx, int paramId, float value) {
        DSPChannel* target = nullptr;
        if (ch == -1) target = masterChannel.get();
        else if (ch >= 0 && ch < 16) target = channels[ch].get();

        if (target) {
            auto& effects = target->getEffects();
            if (effectIdx >= 0 && effectIdx < (int)effects.size()) {
                effects[effectIdx]->setParam(paramId, value);
            } else {
                LOG_DSP("ERROR: setEffectParam invalid index! ch=%d, idx=%d, size=%zu", ch, effectIdx, effects.size());
            }
        }
    }

    void setEffectEnabled(int ch, int effectIdx, bool enabled) {
        DSPChannel* target = nullptr;
        if (ch == -1) target = masterChannel.get();
        else if (ch >= 0 && ch < 16) target = channels[ch].get();

        if (target) {
            auto& effects = target->getEffects();
            if (effectIdx >= 0 && effectIdx < (int)effects.size()) {
                effects[effectIdx]->enabled = enabled;
                effects[effectIdx]->bypassFade.setTarget(enabled ? 1.0f : 0.0f);
            } else {
                LOG_DSP("ERROR: setEffectEnabled invalid index! ch=%d, idx=%d, size=%zu", ch, effectIdx, effects.size());
            }
        }
    }

    void getEffectMeters(int ch, int effectIdx, float* input, float* output, float* gr) {
        DSPChannel* target = nullptr;
        if (ch == -1) target = masterChannel.get();
        else if (ch >= 0 && ch < 16) target = channels[ch].get();

        if (target) {
            auto& effects = target->getEffects();
            if (effectIdx >= 0 && effectIdx < (int)effects.size()) {
                effects[effectIdx]->getMeters(input, output, gr);
            }
        }
    }

    void setChannelSend(int ch, float level) { setEffectParam(ch, 1, 0, level); }
    void setChannelEQ(int ch, float cutoff) { setEffectParam(ch, 0, 0, cutoff); }

private:
    std::vector<std::unique_ptr<DSPChannel> > channels;
    std::unique_ptr<DSPChannel> masterChannel;
    float reverbInL[MAX_AUDIO_FRAME_SIZE] = {0.0f};
    float reverbInR[MAX_AUDIO_FRAME_SIZE] = {0.0f};
    float sampleRate = 44100.0f;
};

} // namespace stage_audio

#endif
