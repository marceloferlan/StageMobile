---
name: Estado da investigação de clicks de áudio + melhorias
description: Fixes aplicados, resultados validados, features implementadas na branch Superpowered (abril/2026)
type: project
---

Branch: `feature/Stage-Mobile-DSP-Superpowered`

## Resultados validados por APM HUD (21/abril/2026)

- **MutexMiss: 0** — MIDI drain inline eliminou contenção entre threads
- **Underruns: 0** — ring buffer + preload + thread affinity funcionam
- **Channel culling**: canais silenciosos removidos do activeChannelsMask → ~700µs/render economizados em idle

## Fixes aplicados (ordem cronológica)

1. Per-phase APM instrumentation (MEASURE_PHASE + ATrace + FloatArray[14])
2. FluidSynth critical settings (preload, cpu-cores=1, reverb/chorus off, overflow setnum)
3. Runtime CPU detection via sysfs → pin ao big cluster
4. Stage Set load fix (SF2 name normalization + sfId=-1 + armed cache rebuild)
5. MIDI drain inline (midiProcessingLoop removida, drainMidiQueue em renderAudioEngine)
6. SIMD NEON nos loops de mix (channel mix+peak, master interleave+peak)
7. Punch redesenhado (LimiterEffect: tanh saturation + lookahead 64 + auto makeup)
8. SF2 unload fix (update=0 + programSelect reaffirm nos canais ativos)
9. Channel culling (mask bit limpo quando peak<0.001 + 0 notas)
10. APM HUD reset button (nativeResetApmCounters)
11. Seletor de Driver de Áudio ("Android Nativo" vs "Otimizado USB")
12. PAD sustain tracking (sustainPedalState no ViewModel)
13. CC MIDI label fix ("Filtros MIDI" → "CC MIDI Habilitados")
14. Indicadores DSP visuais nos channel strips (HP/LP/CP/EQ/CH/TR/DL/RV/LM) — phone e tablet adaptativo
15. SF2 import dialog fix (tamanho + verificação Firestore em vez de filesystem)
16. isTablet fix global (smallestScreenWidthDp >= 600 em vez de screenWidthDp >= 600)
17. TopBar compacta phone (botões 26dp, logo 36×20dp, título 14sp)
18. LoginScreen layout horizontal pra phone (Row: logo 30% + form 70%)
19. Briefing Superpowered licensing (docs/superpowered_licensing_briefing.md + .html)
20. Plano de benchmark DSP STK vs Superpowered (docs/plan_dsp_benchmark_superpowered.md)

## Features implementadas

- **Seletor de Driver de Áudio** em Parâmetros Globais
- **APM HUD per-phase** com breakdown + CSV export 16 colunas + botão Reset
- **Indicadores DSP visuais** nos channel strips e master — quadrinhos com abreviação 2 letras, verde/escuro
- **LoginScreen responsiva** — layout horizontal pra phone landscape, vertical pra tablet
- **TopBar adaptativa** — dimensões compactas em phone, normais em tablet

## Armadilhas conhecidas

- FluidSynth overflow.* são NUMERIC (setnum), não INT (setint). Erro silencioso.
- CPU numbering: nunca hardcodar core. Usar runtime detection via sysfs cpuinfo_max_freq.
- SF2 name normalization: substringBefore(" [") antes de lookup em cache/filesystem.
- Nunca logar (LOGW/LOGI) em renderAudioEngine/synthRenderLoop — priority inversion.
- fluid_synth_sfunload com update=1 corrompe mapeamentos de canais. Sempre usar update=0 + reaffirm programSelect.

## Arquivos-chave modificados

- `app/src/main/cpp/fluidsynth_bridge.cpp` — render engine, MIDI drain, SIMD, APM, thread affinity
- `app/src/main/cpp/dsp_chain.h` — LimiterEffect redesenhado (Punch)
- `app/src/main/java/.../viewmodel/MixerViewModel.kt` — loadSetStage, driver mode, sustain tracking, APM parsing
- `app/src/main/java/.../data/SettingsRepository.kt` — KEY_AUDIO_DRIVER_MODE
- `app/src/main/java/.../ui/screens/SystemGlobalSettings.kt` — AudioDriverSection
- `app/src/main/java/.../ui/components/APMHudDialog.kt` — per-phase + reset + CSV
- `app/src/main/java/.../ui/mixer/InstrumentChannelSettingsPanel.kt` — CC label fix
- `superpowered-usb/.../SuperpoweredUSBAudioManager.kt` — disconnectAll + device tracking
- `docs/` — TODO.md, INDEX.md, audio_performance_tuning.md, architecture.md, developer_guide.md
