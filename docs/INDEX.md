# StageMobile — Índice de Documentação

*Última atualização: 2026-04-21*

---

## O Projeto em 5 Linhas

- App Android de síntese de áudio profissional para músicos de palco
- UI em Jetpack Compose + motor nativo C++17 via JNI
- Render híbrido: Oboe (áudio interno) + Superpowered SDK (áudio USB direto)
- 16 canais MIDI simultâneos, rack DSP por canal, múltiplos SF2 carregados em RAM
- Constraint crítica: latência e estabilidade de áudio — regras de segurança no hot path são invioláveis

---

## Branch atual

`feature/Stage-Mobile-DSP-Superpowered` — integração do Superpowered SDK para contornar o driver USB audio do Android (jitter no Exynos). Ver [superpowered_isolation.md](./superpowered_isolation.md).

---

## Docs por categoria

### Arquitetura e Sistema

| Documento | Cobre | Quando usar |
|---|---|---|
| [architecture.md](./architecture.md) | Stack, threading model, decisões arquiteturais | Entender o "por quê" da estrutura |
| [audio_routing_flow.md](./audio_routing_flow.md) | Fluxo completo MIDI → síntese → DSP → saída | Trabalhar na camada nativa (C++) |
| [component_map.md](./component_map.md) | Tabela arquivo → função Compose por área | Navegar o código rapidamente |
| [superpowered_isolation.md](./superpowered_isolation.md) | Integração Superpowered via módulo Gradle isolado (c++_static vs c++_shared) | Entender/manter a ponte USB |

### Performance e Diagnóstico

| Documento | Cobre | Quando usar |
|---|---|---|
| [audio_performance_tuning.md](./audio_performance_tuning.md) | Per-phase APM, thread affinity, FluidSynth settings, voice overflow, fluxo de diagnóstico de clicks | Investigar glitches, otimizar latência, entender o APM HUD |
| [diagnostico_cpu.md](./diagnostico_cpu.md) | Análise de consumo de CPU por subsistema | Problemas de performance (CPU alta) |

### Backlog e Planejamento

| Documento | Cobre | Quando usar |
|---|---|---|
| [TODO.md](./TODO.md) | Backlog técnico: itens pendentes por prioridade (urgente, alta, média, baixa) + concluídos | Antes de cada sprint/sessão de trabalho |

### Funcionalidades

| Documento | Cobre | Quando usar |
|---|---|---|
| [features.md](./features.md) | Regras de negócio, fluxos UX, requisitos funcionais | Implementar/modificar features |
| [ui_ux_standards.md](./ui_ux_standards.md) | Padrões visuais, gestos, ergonomia de palco | Criar/alterar componentes de UI |

### Guia Operacional

| Documento | Cobre | Quando usar |
|---|---|---|
| [developer_guide.md](./developer_guide.md) | Padrões JNI, troubleshooting, protocolo de integridade de código | Depurar ou expandir o motor nativo |

---

## Constantes Globais Críticas

```kotlin
// MixerViewModel.kt — companion object
const val MASTER_CHANNEL_ID = -1   // Canal master
const val GLOBAL_CHANNEL_ID = -2   // Operações globais sem canal

// InstrumentChannel defaults (domain/model/InstrumentChannel.kt)
sfId = -1               // Canal vazio (nenhum SF2 carregado)
midiChannel = -1        // Escuta todos os canais MIDI
midiDeviceName = null   // Escuta todos os dispositivos ativos
```

```cpp
// fluidsynth_bridge.cpp
CHUNK_SIZE = 128                // Frames por chamada de render
RING_BUFFER_CAPACITY = 4096     // Frames do ring buffer synth → consumer
FILL_LOW_WATERMARK  = 512       // Entrar em contenção (quality down)
FILL_HIGH_WATERMARK = 1024      // Sair de contenção (quality up)
```

---

## Arquivos nativos-chave

| Arquivo | Papel |
|---|---|
| `app/src/main/cpp/fluidsynth_bridge.cpp` | Bridge JNI, `nativeInit`, `synthRenderLoop`, `renderAudioEngine`, `midiProcessingLoop`, APM counters, ATrace markers |
| `app/src/main/cpp/dsp_chain.h` | DSPChain modular, racks por canal + master, efeitos STK |
| `superpowered-usb/src/main/cpp/sp_bridge.cpp` | Bridge C puro para áudio USB via Superpowered SDK |

---

## Convenções de Nomenclatura

- Prefixo `native` em funções JNI Kotlin: `nativeLoadSf2()`, `nativeNoteOn()`, etc.
- Prefixo `_` em StateFlows privados: `_channels`, `_midiDeviceName`, etc.
- Sufixo `Repository` em classes de persistência
- Sufixo `Engine` em interface/implementação de áudio: `AudioEngine`, `FluidSynthEngine`, `DummyAudioEngine`
- Sufixo `Screen` em telas Compose: `MixerScreen`, `SetsScreen`
- Sufixo `Dialog` em diálogos modais: `APMHudDialog`, `DSPEffectsRackDialog`

---

## Dependências Externas

| Biblioteca | Papel | Camada |
|---|---|---|
| FluidSynth | Síntese de áudio via SoundFonts (.sf2) | Nativa (pré-compilada em `jniLibs/arm64-v8a/`) |
| Google Oboe | Áudio de baixa latência (AAudio/OpenSLES) | Nativa (via prefab) |
| Superpowered SDK | Áudio USB direto (contorna driver Android) | Nativa isolada em `:superpowered-usb` com c++_static |
| STK (Synthesis Toolkit) | DSP: reverb, chorus, delay, filtros | Nativa (fonte em `cpp/externals/STK/`) |
| SoundTouch | Pitch shift e time stretch | Nativa |
| DSPFilters | Filtros digitais: Bessel, Butterworth, Chebyshev | Nativa |
| Firebase Firestore | Metadados SF2 (persistência offline) | Kotlin |
| Gson | Serialização de SetStage para SharedPreferences | Kotlin |
| Jetpack Compose | UI declarativa (Material 3) | Kotlin |
