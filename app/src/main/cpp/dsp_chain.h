#ifndef STAGEMOBILE_DSP_CHAIN_H
#define STAGEMOBILE_DSP_CHAIN_H

#include <vector>
#include <memory>
#include <mutex>
#include <cstring>
#include <DspFilters/Dsp.h>
#include <DspFilters/Butterworth.h>
#include <DspFilters/Design.h>
#include "Delay.h"
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

namespace stage_audio {

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
    bool enabled = true;
};

/**
 * Efeito de Send para o Bus Global (Reverb)
 */
class SendBusEffect : public DSPEffect {
public:
    SendBusEffect(float* globalL, float* globalR) : busL(globalL), busR(globalR) {}

    void processStereo(float* left, float* right, int numSamples) override {
        if (enabled && level > 0.0f) {
            for (int i = 0; i < numSamples; ++i) {
                busL[i] += left[i] * level;
                busR[i] += right[i] * level;
            }
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
 * EQ Paramétrico (3 Bandas)
 */
class ParametricEQEffect : public DSPEffect {
private:
    float lFreq = 200.0f, lGain = 0.0f;
    float mFreq = 1000.0f, mBW = 500.0f, mGain = 0.0f;
    float hFreq = 5000.0f, hGain = 0.0f;
    float outGain = 1.0f;
    float sampleRate = 44100.0f;
    ::Dsp::SimpleFilter<::Dsp::Butterworth::LowShelf<2>, 1> lowShelf[2];
    ::Dsp::SimpleFilter<::Dsp::Butterworth::BandShelf<2>, 1> midPeak[2]; 
    ::Dsp::SimpleFilter<::Dsp::Butterworth::HighShelf<2>, 1> highShelf[2];

    float clamp(float v, float min, float max) {
        return (v < min) ? min : (v > max) ? max : v;
    }

public:
    ParametricEQEffect() {
        updateFilters();
    }
    
    void setSampleRate(float fs) {
        sampleRate = fs;
        updateFilters();
    }
    
    void setParam(int paramId, float value) override {
        bool changed = false;
        switch(paramId) {
            case 0: // Low Gain
                lGain = clamp(value, -24.0f, 24.0f); changed = true; break;
            case 1: // Low Freq
                lFreq = clamp(value, 20.0f, 1000.0f); changed = true; break;
            case 2: // Mid Gain
                mGain = clamp(value, -24.0f, 24.0f); changed = true; break;
            case 3: // Mid Freq
                mFreq = clamp(value, 20.0f, 8000.0f); changed = true; break;
            case 4: // Mid BW (Q/Width)
                mBW = clamp(value, 10.0f, 1000.0f); changed = true; break;
            case 5: // High Gain
                hGain = clamp(value, -24.0f, 24.0f); changed = true; break;
            case 6: // High Freq
                hFreq = clamp(value, 2000.0f, 20000.0f); changed = true; break;
            case 7: // Master Out
                outGain = std::pow(10.0f, clamp(value, -24.0f, 24.0f) / 20.0f); break;
        }
        
        if (changed) {
            updateFilters();
        }
    }

    void updateFilters() {
        for (int i = 0; i < 2; ++i) {
            // Converter Q para Bandwidth se necessário. O DspFilters BandShelf espera largura de banda em Hz.
            // Se mBW for tratado como Q no Kotlin (0.5 a 4.0), convertemos aqui.
            float finalBW = (mBW < 20.0f) ? (mFreq / std::max(0.1f, mBW)) : mBW;
            
            lowShelf[i].setup(1, sampleRate, lFreq, lGain);
            midPeak[i].setup(1, sampleRate, mFreq, finalBW, mGain);
            highShelf[i].setup(1, sampleRate, hFreq, hGain);
        }
    }

    int getType() const override { return 0; }

    void processStereo(float* left, float* right, int numSamples) override {
        if (!enabled) return;
        float* chs[2] = {left, right};
        for (int c = 0; c < 2; ++c) {
            lowShelf[c].process(numSamples, &chs[c]);
            midPeak[c].process(numSamples, &chs[c]);
            highShelf[c].process(numSamples, &chs[c]);
        }
        
        // Apply Master Out once for both channels
        if (outGain != 1.0f) {
            for (int i = 0; i < numSamples; ++i) {
                left[i] *= outGain;
                right[i] *= outGain;
            }
        }

        static int eqLogCounter = 0;
        if (eqLogCounter++ % 500 == 0) {
            LOG_DSP("EQ Process: GainL=%.2f, GainM=%.2f, GainH=%.2f, Out=%.2f", lGain, mGain, hGain, outGain);
        }
    }
};

/**
 * HPF/LPF Butterworth
 */
class FilterEffect : public DSPEffect {
public:
    FilterEffect(bool isHPF) : hpf(isHPF) {
        setup(1000.0f);
    }
    void processStereo(float* left, float* right, int numSamples) override {
        if (!enabled) return;
        float* chs[2] = {left, right};
        if (hpf) {
            filterHP_L.process(numSamples, &chs[0]);
            filterHP_R.process(numSamples, &chs[1]);
        } else {
            filterLP_L.process(numSamples, &chs[0]);
            filterLP_R.process(numSamples, &chs[1]);
        }
    }
    void setParam(int paramId, float value) override {
        if (paramId == 0) {
            lastCutoff = value;
            setup(value);
        }
    }
    void setSampleRate(float fs) {
        sampleRate = fs;
        setup(lastCutoff);
    }
    int getType() const override { return hpf ? 1 : 2; }
private:
    void setup(float cutoff) {
        // Clamp fundamental para evitar NaN/Assert em frequências extremas
        float safeCutoff = (cutoff < 20.0f) ? 20.0f : (cutoff > (sampleRate * 0.45f)) ? (sampleRate * 0.45f) : cutoff;
        if (hpf) {
            filterHP_L.setup(1, sampleRate, safeCutoff);
            filterHP_R.setup(1, sampleRate, safeCutoff);
        } else {
            filterLP_L.setup(1, sampleRate, safeCutoff);
            filterLP_R.setup(1, sampleRate, safeCutoff);
        }
    }
    bool hpf;
    float sampleRate = 44100.0f;
    float lastCutoff = 1000.0f;
    ::Dsp::SimpleFilter<::Dsp::Butterworth::HighPass<2>, 1> filterHP_L, filterHP_R;
    ::Dsp::SimpleFilter<::Dsp::Butterworth::LowPass<2>, 1> filterLP_L, filterLP_R;
};

/**
 * Chorus Estéreo (STK)
 */
class ChorusEffect : public DSPEffect {
public:
    ChorusEffect() : chorus(1000) {
        chorus.setModFrequency(0.5f);
        chorus.setModDepth(0.2f);
        chorus.setEffectMix(0.5f);
    }
    void processStereo(float* left, float* right, int numSamples) override {
        if (!enabled) return;
        for (int i = 0; i < numSamples; ++i) {
            float monoIn = (left[i] + right[i]) * 0.5f;
            // IMPORTANTE: tick(input, 0) processa internamente e retorna ch 0.
            // Chamar tick(input, 1) DEPOIS processaria NOVAMENTE (double LFO update).
            // O correto é pegar o lastOut(1).
            left[i] = chorus.tick(monoIn, 0);
            right[i] = chorus.lastOut(1);
        }
    }
    void setParam(int paramId, float value) override {
        if (paramId == 0) chorus.setModFrequency(value); // Rate 0.1-10Hz
        else if (paramId == 1) chorus.setModDepth(value); // Depth 0.0-1.0
        else if (paramId == 2) chorus.setEffectMix(value); // Mix 0.0-1.0
    }
    int getType() const override { return 5; }
private:
    ::stk::Chorus chorus;
};

/**
 * Compressor Feed-Forward Custom
 */
class CompressorEffect : public DSPEffect {
public:
    CompressorEffect() {}
    
    void processStereo(float* left, float* right, int numSamples) override {
        if (!enabled) {
            lastInputPeak = 0.0f;
            lastOutputPeak = 0.0f;
            lastGainReductionDb = 0.0f;
            return;
        }
        
        float currentInputPeak = 0.0f;
        float currentOutputPeak = 0.0f;
        float currentMaxGR = 1.0f; // Linear gain reduction (1.0 = no reduction)
        
        for (int i = 0; i < numSamples; ++i) {
            float absL = std::abs(left[i]);
            float absR = std::abs(right[i]);
            float peak = std::max(absL, absR);
            if (peak > currentInputPeak) currentInputPeak = peak;
            
            // Envelope detection (RMS-ish simple envelope)
            float target = (peak > envelope) ? attackCoeff : releaseCoeff;
            envelope = envelope + target * (peak - envelope);
            
            float gainReduction = 1.0f;
            if (envelope > 1e-6f) {
                float envDb = 20.0f * std::log10(envelope);
                float thresholdDb = 20.0f * std::log10(threshold);
                float diffDb = envDb - thresholdDb;
                
                if (kneeDb > 0.01f) {
                    float halfKnee = kneeDb / 2.0f;
                    if (diffDb > halfKnee) {
                        float redDb = diffDb * (1.0f - 1.0f / ratio);
                        gainReduction = std::pow(10.0f, -redDb / 20.0f);
                    } else if (diffDb > -halfKnee) {
                        float factor = (1.0f / ratio - 1.0f);
                        float kneePart = diffDb + halfKnee;
                        float redDb = -factor * (kneePart * kneePart) / (2.0f * kneeDb);
                        gainReduction = std::pow(10.0f, -redDb / 20.0f);
                    }
                } else {
                    if (diffDb > 0.0f) {
                        float redDb = diffDb * (1.0f - 1.0f / ratio);
                        gainReduction = std::pow(10.0f, -redDb / 20.0f);
                    }
                }
            }
            
            if (gainReduction < currentMaxGR) currentMaxGR = gainReduction;
            
            // Wet signal: Apply gain reduction AND Makeup Gain
            float wetL = left[i] * gainReduction * makeupGain;
            float wetR = right[i] * gainReduction * makeupGain;
            
            float outL = (left[i] * (1.0f - mix)) + (wetL * mix);
            float outR = (right[i] * (1.0f - mix)) + (wetR * mix);
            
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
                threshold = std::pow(10.0f, std::min(value, 0.0f) / 20.0f); break;
            case 1: // Ratio (1 to 20)
                ratio = std::max(1.0f, value); break;
            case 2: // Attack (ms)
                attackCoeff = 1.0f - std::exp(-1.0f / (sampleRate * (std::max(value, 0.1f) / 1000.0f))); break;
            case 3: // Release (ms)
                releaseCoeff = 1.0f - std::exp(-1.0f / (sampleRate * (std::max(value, 5.0f) / 1000.0f))); break;
            case 4: // Makeup Gain (0 to 24 dB)
                makeupGain = std::pow(10.0f, std::max(value, 0.0f) / 20.0f); break;
            case 5: // Knee (0 to 12 dB)
                kneeDb = std::max(0.0f, std::min(value, 12.0f)); break;
            case 6: // Mix (0 to 1.0)
                mix = std::max(0.0f, std::min(value, 1.0f)); break;
        }
    }
    int getType() const override { return 7; }

    void getMeters(float* input, float* output, float* gr) override {
        if (input) *input = lastInputPeak;
        if (output) *output = lastOutputPeak;
        if (gr) *gr = lastGainReductionDb;
    }

    void setSampleRate(float fs) {
        sampleRate = fs;
    }
    
private:
    float threshold = 0.1f; // -20dB default
    float ratio = 4.0f;
    float attackCoeff = 0.01f;
    float releaseCoeff = 0.001f;
    float envelope = 0.0f;
    float makeupGain = 1.0f;
    float kneeDb = 0.0f;
    float mix = 1.0f;
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
    TremoloEffect() {}
    
    void processStereo(float* left, float* right, int numSamples) override {
        if (!enabled) return;
        for (int i = 0; i < numSamples; ++i) {
            float lfo = 1.0f - (depth * 0.5f * (1.0f + std::sin(phase)));
            
            // Wet signal
            float wetL = left[i] * lfo;
            float wetR = right[i] * lfo;
            
            // Mix Dry/Wet
            left[i] = (left[i] * (1.0f - mix)) + (wetL * mix);
            right[i] = (right[i] * (1.0f - mix)) + (wetR * mix);
            
            phase += phaseIncrement;
            if (phase > 2.0f * M_PI) phase -= 2.0f * M_PI;
        }
    }
    
    void setParam(int paramId, float value) override {
        if (paramId == 0) { // Rate (0.1 to 20 Hz)
            lastRate = value;
            updateLFO();
        } else if (paramId == 1) { // Depth (0.0 to 1.0)
            depth = value;
        } else if (paramId == 2) { // Mix (0.0 to 1.0)
            mix = value;
        }
    }
    
    void setSampleRate(float fs) {
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
    float depth = 0.5f;
    float mix = 1.0f;
    float sampleRate = 44100.0f;
};

/**
 * Limitador de Pico
 */
class LimiterEffect : public DSPEffect {
public:
    LimiterEffect() {}
    
    void processStereo(float* left, float* right, int numSamples) override {
        if (!enabled) return;
        for (int i = 0; i < numSamples; ++i) {
            float peak = std::max(std::abs(left[i]), std::abs(right[i]));
            
            // Fast attack, slow release envelope
            if (peak > envelope) envelope = peak;
            else envelope = envelope * releaseCoeff;
            
            float gain = 1.0f;
            if (envelope > threshold) {
                gain = threshold / envelope;
            }
            
            left[i] *= gain;
            right[i] *= gain;
        }
    }
    
    void setParam(int paramId, float value) override {
        if (paramId == 0) threshold = std::pow(10.0f, value / 20.0f);
        else if (paramId == 1) {
            lastReleaseMs = value;
            updateCoeff();
        }
    }
    
    void setSampleRate(float fs) {
        sampleRate = fs;
        updateCoeff();
    }
    
    int getType() const override { return 8; }
private:
    void updateCoeff() {
        releaseCoeff = std::exp(-1.0f / (sampleRate * (lastReleaseMs / 1000.0f)));
    }
    float threshold = 0.95f; // ~ -0.5dB
    float envelope = 0.0f;
    float releaseCoeff = 0.999f;
    float sampleRate = 44100.0f;
    float lastReleaseMs = 500.0f;
};

/**
 * Reverb Global (FreeVerb Return)
 * Parâmetros: 0:RoomSize, 1:Damping, 2:Mix, 3:Width
 */
class ReverbEffect : public DSPEffect {
public:
    ReverbEffect(float* globalInL, float* globalInR) : inL(globalInL), inR(globalInR) {
        reverb.setRoomSize(0.7f);
        reverb.setDamping(0.3f);
        reverb.setEffectMix(1.0f);
    }
    
    void processStereo(float* left, float* right, int numSamples) override {
        if (!enabled) return;

        // 1. Determinar entrada (Bus Global ou Inserção Local)
        float* sourceL = inL;
        float* sourceR = inR;
        
        bool isInsertMode = (inL == nullptr || inR == nullptr);
        if (isInsertMode) {
            sourceL = left;
            sourceR = right;
        }

        // 2. Detector de entrada e DC Blocker (Filtro Anti-Sustain-Loop)
        // O DC Blocker remove offset infra-sônico que poderia "prender" o gate
        bool hasInput = false;
        const float inputThreshold = 3e-4f; // ~ -70dB (sensível a velocity baixo)
        
        for (int i = 0; i < numSamples; ++i) {
            // DC Blocker (Filtro de polo único em ~20Hz)
            float xL = sourceL[i];
            float xR = sourceR[i];
            
            float outL_dc = xL - l_prevX + 0.995f * l_prevY;
            float outR_dc = xR - r_prevX + 0.995f * r_prevY;
            
            l_prevX = xL; l_prevY = outL_dc;
            r_prevX = xR; r_prevY = outR_dc;
            
            if (std::abs(outL_dc) > inputThreshold || std::abs(outR_dc) > inputThreshold) {
                hasInput = true;
            }
            
            // Re-injeta áudio limpo no processamento (opcional, aqui usamos apenas para detecção)
            // No FreeVerb, ruído DC na entrada pode causar instabilidade
        }

        // 3. Lógica de Ativação e Histerese
        if (hasInput) {
            wasProcessing = true;
            tailSleepTimer = maxSleepSamples; 
        }

        // Se estiver dormindo e não houver entrada, pula
        if (!wasProcessing) return;

        // 4. Processamento Real do Reverb (STK FreeVerb)
        bool hasCurrentOutput = false;
        const float outputThreshold = 3e-5f; // ~ -90dB (cauda profunda)

        for (int i = 0; i < numSamples; ++i) {
            reverb.tick(sourceL[i], sourceR[i]);
            
            float outL = (float)reverb.lastOut(0) * mix;
            float outR = (float)reverb.lastOut(1) * mix;

            left[i] += outL;
            right[i] += outR;

            if (std::abs(outL) > outputThreshold || std::abs(outR) > outputThreshold) {
                hasCurrentOutput = true;
            }
        }

        // 5. Gestão de Repouso Inteligente
        if (hasInput || hasCurrentOutput) {
            tailSleepTimer = maxSleepSamples; // Mantém acordado
        } else {
            tailSleepTimer -= numSamples;
            if (tailSleepTimer <= 0) {
                reverb.clear(); // Limpa buffers 100% (Zero Ghost Notes)
                wasProcessing = false;
                tailSleepTimer = 0;
            }
        }
    }

    bool hasActiveTails() const override { return wasProcessing; }

    void setParam(int paramId, float value) override {
        if (paramId == 0) reverb.setRoomSize(value);
        else if (paramId == 1) reverb.setDamping(value);
        else if (paramId == 2) mix = value;
        else if (paramId == 3) reverb.setWidth(value);
    }
    int getType() const override { return 4; }
private:
    ::stk::FreeVerb reverb;
    float* inL;
    float* inR;
    float mix = 1.0f;
    bool wasProcessing = false;
    
    // DC Blocker states
    float l_prevX = 0, l_prevY = 0;
    float r_prevX = 0, r_prevY = 0;

    int tailSleepTimer = 0;
    const int maxSleepSamples = 44100 * 3; // 3 segundos de escoamento natural
};

/**
 * Delay Simples (Aprimorado)
 */
class DelayEffect : public DSPEffect {
public:
    DelayEffect() {
        delayL.setDelay(44100 * 0.5f); // 500ms
        delayR.setDelay(44100 * 0.5f);
    }
    void processStereo(float* left, float* right, int numSamples) override {
        if (!enabled) return;
        for (int i = 0; i < numSamples; ++i) {
            float inL = left[i] + lastL * feedback;
            float inR = right[i] + lastR * feedback;
            float outL = delayL.tick(inL);
            float outR = delayR.tick(inR);
            lastL = outL;
            lastR = outR;
            left[i] = left[i] * (1.0f - mix) + outL * mix;
            right[i] = right[i] * (1.0f - mix) + outR * mix;
        }
    }
    void setParam(int paramId, float value) override {
        if (paramId == 0) { // Time (ms)
            unsigned long delaySamples = (unsigned long)(44.1f * value);
            if (delaySamples > 88200) delaySamples = 88200; // Cap at 2s
            delayL.setDelay(delaySamples);
            delayR.setDelay(delaySamples);
        }
        else if (paramId == 1) feedback = (value < 0.0f) ? 0.0f : (value > 0.95f) ? 0.95f : value;
        else if (paramId == 2) mix = (value < 0.0f) ? 0.0f : (value > 1.0f) ? 1.0f : value;
    }
    int getType() const override { return 3; }
    bool hasActiveTails() const override {
        // Delay has tails if there's significant energy in the feedback loop
        return std::abs(lastL) > 0.0001f || std::abs(lastR) > 0.0001f;
    }
private:
    ::stk::Delay delayL, delayR;
    float feedback = 0.3f, mix = 0.5f, lastL = 0, lastR = 0;
};

/**
 * Canal DSP Modular (Membro do Rack)
 */
class DSPChannel {
public:
    void process(float* left, float* right, int numSamples) {
        for (auto& effect : effects) {
            effect->processStereo(left, right, numSamples);
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
        if (numSamples > 4096) numSamples = 4096;
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
                case 3: target->addEffect(std::unique_ptr<DSPEffect>(new DelayEffect())); break;
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
    float reverbInL[4096] = {0.0f};
    float reverbInR[4096] = {0.0f};
    float sampleRate = 44100.0f;
};

} // namespace stage_audio

#endif
