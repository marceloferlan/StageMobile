---
name: StageMobile — Visão Geral do Projeto
description: Contexto, arquitetura e estado atual do projeto Android StageMobile (sintetizador para palco)
type: project
---

StageMobile é um app Android de síntese de áudio profissional para músicos de palco. Combina Jetpack Compose (Kotlin) com motor de áudio nativo em C++17 via JNI.

- **Package:** `com.marceloferlan.stagemobile`
- **Localização:** `/Users/macbookpro/AndroidStudioProjects/StageMobile/`
- **Branch ativa:** `feature/Stage-Mobile-DSP-Superpowered`
- **Stack:** Kotlin + C++17 | FluidSynth (SF2) | Google Oboe (interno) | Superpowered SDK (USB isolado) | STK + SoundTouch (DSP) | Firebase Firestore | Jetpack Compose Material 3
- **Arquitetura:** arm64-v8a apenas | minSdk 26 (Android 8.0) | 16 canais MIDI simultâneos

**Fluxo principal:** MIDI USB → MidiConnectionManager → MixerViewModel → FluidSynthEngine (JNI) → fluidsynth_bridge.cpp → DSPChain → Oboe (interno) OU Superpowered USB bridge → hardware

**Render dual-path:**
- **Oboe path:** `onAudioReady` → lê do `synthRingBuffer` (SPSC lock-free)
- **Superpowered path:** `sp_render_audio` (registrado via `sp_register_render_callback` no `libspbridge.so`) → lê do mesmo ring buffer
- **Produtor:** `synthRenderLoop` (thread dedicada) → chama `renderAudioEngine` → acquire `engine_mutex` → `fluid_synth_nwrite_float` → DSP → escreve no ring buffer
- Quando Superpowered está ativo, `onAudioReady` do Oboe retorna silêncio (detectado via `spIsActiveFn`)

**Constantes críticas:** `MASTER_CHANNEL_ID = -1`, `GLOBAL_CHANNEL_ID = -2` (em MixerViewModel companion), `CHUNK_SIZE = 128`, `RING_BUFFER_CAPACITY = 4096`

**Persistência:** SharedPreferences "stage_mobile_settings" (config) + SharedPreferences "stage_mobile_sets" + Gson (SetStages, 150 slots) + Firebase Firestore "soundfonts" (metadados SF2) + filesDir/soundfonts/ (arquivos físicos)

**Por que:** App para performance ao vivo — latência e estabilidade são inegociáveis. Toda decisão arquitetural prioriza "zero glitches" no palco.

**How to apply:** Ao sugerir mudanças, sempre verificar impacto no loop de áudio nativo. Regras: nunca alocar memória no hot path, nunca usar `Log.d`/`__android_log_print` em `renderAudioEngine`/`synthRenderLoop`/`onAudioReady`, nunca fazer JNI do callback de áudio.

**Thread affinity:** `synthRenderLoop` é pinned em runtime às cores com freq máxima (big cluster) via leitura de `/sys/devices/system/cpu/cpuN/cpufreq/cpuinfo_max_freq`. Prioridade `SCHED_FIFO` é negada pelo Android (`EPERM`), fallback automático para `nice=-19`.

**FluidSynth settings críticas (em `nativeInit` ANTES de `new_fluid_synth`):**
- `synth.dynamic-sample-loading=0` — preload RAM, evita page faults no hot path (principal causa dos spikes)
- `synth.cpu-cores=1` — evita worker threads internas com wakeup latency
- `synth.reverb.active=0`, `synth.chorus.active=0` — DSP interno desabilitado, usamos DSPChain externo
- `synth.overflow.*` — NUMERIC (double), não int. Usar `setnum` não `setint` — senão FluidSynth loga "Unknown integer parameter" e ignora

**Documentação:** `docs/INDEX.md` é o ponto de entrada; `docs/audio_performance_tuning.md` cobre todas as otimizações e o fluxo de diagnóstico via APM HUD.
