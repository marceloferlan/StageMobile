#ifndef STAGEMOBILE_DSP_CHAIN_H
#define STAGEMOBILE_DSP_CHAIN_H

#include <vector>
#include <memory>
#include <mutex>
#include <atomic>
#include <cstring>
#include <DspFilters/Dsp.h>
#include <DspFilters/Butterworth.h>
#include <DspFilters/Design.h>
#include "Delay.h"
#include "DelayL.h"
#include "Chorus.h"
#include "FreeVerb.h"
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
    
    void processStereo(float* left, float* right, int numSamples) override {
        // Reset meters if bypass fully active (handled by bypassFade < 0.01 optimization in DSPChannel)
        float currentInputPeak = 0.0f;
        float currentOutputPeak = 0.0f;
        float currentMaxGR = 1.0f;
        
        for (int i = 0; i < numSamples; ++i) {
            // Atualizar parâmetros suavizados a cada 16 samples
            float curThresh, curRatio, curMakeup, curKnee, curMix;
            if ((i & 15) == 0) {
                curThresh = threshSmooth.getNext();
                curRatio = ratioSmooth.getNext();
                curMakeup = makeupSmooth.getNext();
                curKnee = kneeSmooth.getNext();
                curMix = mixSmooth.getNext();
                // Cache para os próximos 16 samples
                cachedThresh = curThresh;
                cachedRatio = curRatio;
                cachedMakeup = curMakeup;
                cachedKnee = curKnee;
                cachedMix = curMix;
            } else {
                curThresh = cachedThresh;
                curRatio = cachedRatio;
                curMakeup = cachedMakeup;
                curKnee = cachedKnee;
                curMix = cachedMix;
            }
            
            float absL = std::abs(left[i]);
            float absR = std::abs(right[i]);
            float peak = std::max(absL, absR);
            if (peak > currentInputPeak) currentInputPeak = peak;
            
            float target = (peak > envelope) ? attackCoeff : releaseCoeff;
            envelope = envelope + target * (peak - envelope);
            
            float gainReduction = 1.0f;
            if (envelope > 1e-6f) {
                float envDb = 20.0f * std::log10(envelope);
                float thresholdDb = 20.0f * std::log10(std::max(curThresh, 1e-6f));
                float diffDb = envDb - thresholdDb;
                
                if (curKnee > 0.01f) {
                    float halfKnee = curKnee / 2.0f;
                    if (diffDb > halfKnee) {
                        float redDb = diffDb * (1.0f - 1.0f / curRatio);
                        gainReduction = std::pow(10.0f, -redDb / 20.0f);
                    } else if (diffDb > -halfKnee) {
                        float factor = (1.0f / curRatio - 1.0f);
                        float kneePart = diffDb + halfKnee;
                        float redDb = -factor * (kneePart * kneePart) / (2.0f * curKnee);
                        gainReduction = std::pow(10.0f, -redDb / 20.0f);
                    }
                } else {
                    if (diffDb > 0.0f) {
                        float redDb = diffDb * (1.0f - 1.0f / curRatio);
                        gainReduction = std::pow(10.0f, -redDb / 20.0f);
                    }
                }
            }
            
            if (gainReduction < currentMaxGR) currentMaxGR = gainReduction;
            
            float wetL = left[i] * gainReduction * curMakeup;
            float wetR = right[i] * gainReduction * curMakeup;
            
            float outL = (left[i] * (1.0f - curMix)) + (wetL * curMix);
            float outR = (right[i] * (1.0f - curMix)) + (wetR * curMix);
            
            float outPeak = std::max(std::abs(outL), std::abs(outR));
            if (outPeak > currentOutputPeak) currentOutputPeak = outPeak;
            
            left[i] = outL;
            right[i] = outR;
        }
        
        lastInputPeak = currentInputPeak;
        lastOutputPeak = currentOutputPeak;
        lastGainReductionDb = (currentMaxGR < 0.999f) ? 20.0f * std::log10(currentMaxGR) : 0.0f;
    }
    
    void setParam(int paramId, float value) override {
        switch(paramId) {
            case 0: // Threshold (-60 to 0 dB)
                threshSmooth.setTarget(std::pow(10.0f, std::min(value, 0.0f) / 20.0f)); break;
            case 1: // Ratio (1 to 20)
                ratioSmooth.setTarget(std::max(1.0f, value)); break;
            case 2: // Attack (ms)
                attackCoeff = 1.0f - std::exp(-1.0f / (sampleRate * (std::max(value, 0.1f) / 1000.0f))); break;
            case 3: // Release (ms)
                releaseCoeff = 1.0f - std::exp(-1.0f / (sampleRate * (std::max(value, 5.0f) / 1000.0f))); break;
            case 4: // Makeup Gain (0 to 24 dB)
                makeupSmooth.setTarget(std::pow(10.0f, std::max(value, 0.0f) / 20.0f)); break;
            case 5: // Knee (0 to 12 dB)
                kneeSmooth.setTarget(std::max(0.0f, std::min(value, 12.0f))); break;
            case 6: // Mix (0 to 1.0)
                mixSmooth.setTarget(std::max(0.0f, std::min(value, 1.0f))); break;
        }
    }
    int getType() const override { return 7; }

    void getMeters(float* input, float* output, float* gr) override {
        if (input) *input = lastInputPeak;
        if (output) *output = lastOutputPeak;
        if (gr) *gr = lastGainReductionDb;
    }

    void setSampleRate(float fs) override {
        sampleRate = fs;
    }
    
private:
    SmoothedParam threshSmooth, ratioSmooth, makeupSmooth, kneeSmooth, mixSmooth;
    
    // Cached values for per-sample processing
    float cachedThresh = 0.1f, cachedRatio = 4.0f, cachedMakeup = 1.0f;
    float cachedKnee = 0.0f, cachedMix = 1.0f;
    
    float attackCoeff = 0.01f;
    float releaseCoeff = 0.001f;
    float envelope = 0.0f;
    float sampleRate = 44100.0f;
    
    // Meter states
    float lastInputPeak = 0.0f;
    float lastOutputPeak = 0.0f;
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
 * Limitador de Pico
 */
class LimiterEffect : public DSPEffect {
public:
    LimiterEffect() {
        memset(delayL, 0, sizeof(delayL));
        memset(delayR, 0, sizeof(delayR));
        updateCoeffs();
    }

    void processStereo(float* left, float* right, int numSamples) override {
        for (int i = 0; i < numSamples; ++i) {
            // Stage 1: Soft saturation (tanh waveshaper) — adiciona "gordura"
            // Comprime picos naturalmente e gera harmônicos quentes (odd harmonics).
            // tanhNorm normaliza pra que sinais baixos passem inalterados.
            float satL = fastTanh(left[i] * drive) * tanhNorm;
            float satR = fastTanh(right[i] * drive) * tanhNorm;

            // Stage 2: Envelope detection no sinal ATUAL (pré-delay = lookahead)
            // O envelope "vê" os picos ANTES do áudio passar pelo delay line,
            // permitindo reduzir o ganho ANTES do pico chegar à saída.
            float peak = std::max(std::abs(satL), std::abs(satR));
            if (peak > envelope)
                envelope += attackCoeff * (peak - envelope);  // attack suavizado (~1ms)
            else
                envelope += releaseCoeff * (peak - envelope); // release configurável

            // Stage 3: Gain computation (brick wall no threshold, sem artefatos)
            float gain = (envelope > threshold) ? (threshold / envelope) : 1.0f;

            // Stage 4: Lookahead delay — lê áudio atrasado, grava áudio atual
            float outL = delayL[delayIdx];
            float outR = delayR[delayIdx];
            delayL[delayIdx] = satL;
            delayR[delayIdx] = satR;
            delayIdx = (delayIdx + 1) & (LOOKAHEAD - 1);

            // Stage 5: Aplica ganho ao sinal ATRASADO (que é o que sai pro hardware)
            // makeupGain compensa a redução pra manter loudness percebido.
            left[i] = outL * gain * makeupGain;
            right[i] = outR * gain * makeupGain;
        }
    }

    void setParam(int paramId, float value) override {
        if (paramId == 0) {
            threshold = std::pow(10.0f, value / 20.0f);
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
    static constexpr int LOOKAHEAD = 64; // ~1.3ms @ 48kHz

    float delayL[LOOKAHEAD] = {};
    float delayR[LOOKAHEAD] = {};
    int delayIdx = 0;

    float envelope = 0.0f;
    float attackCoeff = 0.0f;
    float releaseCoeff = 0.0f;

    float threshold = 0.891f;       // -1dB
    float drive = 1.5f;             // saturação: 1.0=limpo, 1.5=warm, 2.0=agressivo
    float tanhNorm = 1.0f / 0.905f; // 1/tanh(1.5) — normalização pra manter nível
    float makeupGain = 1.122f;      // 1/threshold — compensa a redução de picos
    float sampleRate = 48000.0f;
    float lastReleaseMs = 200.0f;   // release mais rápido que antes (500→200ms)

    // Padé approximation — precisa <0.1% pra |x|<3, zero branches
    static inline float fastTanh(float x) {
        float x2 = x * x;
        return x * (27.0f + x2) / (27.0f + 9.0f * x2);
    }

    void updateCoeffs() {
        // Attack ~1ms: suavizado (antes era instantâneo → artefatos)
        attackCoeff = 1.0f - std::exp(-1.0f / (sampleRate * 0.001f));
        // Release configurável (default 200ms)
        releaseCoeff = 1.0f - std::exp(-1.0f / (sampleRate * (lastReleaseMs / 1000.0f)));
    }
};

/**
 * Reverb Global (FreeVerb Return) — Com Smoothing de Parâmetros
 * Parâmetros: 0:RoomSize, 1:Damping, 2:Mix, 3:Width
 */
class ReverbEffect : public DSPEffect {
public:
    ReverbEffect(float* globalInL, float* globalInR) : inL(globalInL), inR(globalInR),
        roomSmooth(0.7f, 0.999f), dampSmooth(0.3f, 0.999f), mixSmooth(1.0f, 0.999f), widthSmooth(0.5f, 0.999f) {
        reverb.setRoomSize(0.7f);
        reverb.setDamping(0.3f);
        reverb.setEffectMix(1.0f);
    }
    
    void processStereo(float* left, float* right, int numSamples) override {
        float* sourceL = inL;
        float* sourceR = inR;
        
        bool isInsertMode = (inL == nullptr || inR == nullptr);
        if (isInsertMode) {
            sourceL = left;
            sourceR = right;
        }

        bool hasInput = false;
        const float inputThreshold = 3e-4f;
        
        for (int i = 0; i < numSamples; ++i) {
            float xL = sourceL[i];
            float xR = sourceR[i];
            
            float outL_dc = xL - l_prevX + 0.995f * l_prevY;
            float outR_dc = xR - r_prevX + 0.995f * r_prevY;
            
            l_prevX = xL; l_prevY = outL_dc;
            r_prevX = xR; r_prevY = outR_dc;
            
            if (std::abs(outL_dc) > inputThreshold || std::abs(outR_dc) > inputThreshold) {
                hasInput = true;
            }
        }

        if (hasInput) {
            wasProcessing = true;
            tailSleepTimer = maxSleepSamples; 
        }

        if (!wasProcessing) return;

        // Atualizar parâmetros do reverb suavizados
        reverb.setRoomSize(roomSmooth.getNext());
        reverb.setDamping(dampSmooth.getNext());
        reverb.setWidth(widthSmooth.getNext());
        float curMix = mixSmooth.getNext();

        bool hasCurrentOutput = false;
        const float outputThreshold = 3e-5f;

        for (int i = 0; i < numSamples; ++i) {
            reverb.tick(sourceL[i], sourceR[i]);
            
            float outL = (float)reverb.lastOut(0) * curMix;
            float outR = (float)reverb.lastOut(1) * curMix;

            left[i] += outL;
            right[i] += outR;

            if (std::abs(outL) > outputThreshold || std::abs(outR) > outputThreshold) {
                hasCurrentOutput = true;
            }
        }

        if (hasInput || hasCurrentOutput) {
            tailSleepTimer = maxSleepSamples;
        } else {
            tailSleepTimer -= numSamples;
            if (tailSleepTimer <= 0) {
                reverb.clear();
                wasProcessing = false;
                tailSleepTimer = 0;
            }
        }
    }

    bool hasActiveTails() const override { return wasProcessing; }

    void setParam(int paramId, float value) override {
        if (paramId == 0) roomSmooth.setTarget(value);
        else if (paramId == 1) dampSmooth.setTarget(value);
        else if (paramId == 2) mixSmooth.setTarget(value);
        else if (paramId == 3) widthSmooth.setTarget(value);
    }
    int getType() const override { return 4; }
private:
    ::stk::FreeVerb reverb;
    float* inL;
    float* inR;
    SmoothedParam roomSmooth, dampSmooth, mixSmooth, widthSmooth;
    bool wasProcessing = false;
    
    float l_prevX = 0, l_prevY = 0;
    float r_prevX = 0, r_prevY = 0;

    int tailSleepTimer = 0;
    const int maxSleepSamples = 44100 * 3;
};

/**
 * Delay Estéreo (STK DelayL) — Com Interpolação e Smoothing
 * Usa stk::DelayL com interpolação linear para transição suave
 * de tempo, eliminando clicks no ajuste de delay time.
 */
class DelayEffect : public DSPEffect {
public:
    DelayEffect() : delayL(22050.0f, 66150), delayR(22050.0f, 66150),
        timeSmooth(22050.0f, 0.999f), fbSmooth(0.3f, 0.997f), mixSmooth(0.5f, 0.997f) {
    }
    void processStereo(float* left, float* right, int numSamples) override {
        // Soft-bypass handled by DSPChannel crossfade
        for (int i = 0; i < numSamples; ++i) {
            // Atualizar parâmetros suavizados
            if ((i & 7) == 0) { // A cada 8 samples
                float t = timeSmooth.getNext();
                delayL.setDelay(t);
                delayR.setDelay(t);
                cachedFb = fbSmooth.getNext();
                cachedMix = mixSmooth.getNext();
            }
            
            // Soft-clip no feedback para evitar auto-oscilação explosiva
            float fbL = lastDelayL * cachedFb;
            float fbR = lastDelayR * cachedFb;
            fbL = std::tanh(fbL); // Soft-clip: limita entre -1 e +1 suavemente
            fbR = std::tanh(fbR);
            
            float inL = left[i] + fbL;
            float inR = right[i] + fbR;
            float outL = delayL.tick(inL);
            float outR = delayR.tick(inR);
            lastDelayL = outL;
            lastDelayR = outR;
            left[i] = left[i] * (1.0f - cachedMix) + outL * cachedMix;
            right[i] = right[i] * (1.0f - cachedMix) + outR * cachedMix;
        }
    }
    void setParam(int paramId, float value) override {
        if (paramId == 0) { // Time (ms) — max 1000ms
            float delaySamples = sampleRate * (value / 1000.0f);
            if (delaySamples > 66150.0f) delaySamples = 66150.0f;
            if (delaySamples < 1.0f) delaySamples = 1.0f;
            timeSmooth.setImmediate(delaySamples); // Salto instantâneo para evitar Pitch Shifting (efeito Laser)
        }
        else if (paramId == 1) fbSmooth.setTarget((value < 0.0f) ? 0.0f : (value > 0.75f) ? 0.75f : value); // Max 0.75
        else if (paramId == 2) mixSmooth.setTarget((value < 0.0f) ? 0.0f : (value > 1.0f) ? 1.0f : value);
    }
    void setSampleRate(float fs) override { sampleRate = fs; }
    int getType() const override { return 3; }
    bool hasActiveTails() const override {
        return std::abs(lastDelayL) > 0.0001f || std::abs(lastDelayR) > 0.0001f;
    }
private:
    ::stk::DelayL delayL, delayR;
    SmoothedParam timeSmooth, fbSmooth, mixSmooth;
    float cachedFb = 0.3f, cachedMix = 0.5f;
    float lastDelayL = 0, lastDelayR = 0;
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
