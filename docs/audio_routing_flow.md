# Stage Audio Engine: Arquitetura e Fluxo de Processamento

Este documento detalha o funcionamento técnico do motor de áudio do StageMobile, desde a entrada de eventos MIDI até a saída final PCM via Oboe.

## 1. Visão Geral
O motor de áudio é uma implementação híbrida (Kotlin/C++) projetada para performance de baixa latência em palco ("Stage ready"). Ele combina o sintetizador FluidSynth para síntese baseada em SoundFonts (SF2) com um rack DSP customizado baseado na biblioteca STK (Synthesis Toolkit).

## 2. Fluxo de Eventos (Data Flow)

### 2.1 Entrada de Controle (UI / External MIDI)
- **Origem:** Interação do usuário via `VirtualKeyboard.kt` ou dispositivos MIDI externos.
- **Processamento:** O evento MIDI (Note On/Off) é capturado e enviado ao `MixerViewModel`.
- **Lógica de Negócio:** Aplicação de transposição, split de teclado e roteamento de canal armado (`armedChannelsCache`).

### 2.2 Camada de Abstração JNI
- **Interoperabilidade:** O `FluidSynthEngine.kt` envia os comandos para o motor nativo via JNI.
- **Concorrência:** Para evitar travamentos na UI Thread, os eventos são inseridos em uma fila de prioridade (`midiQueue`) no C++.

### 2.3 Síntese e Renderização (Native Synth)
- **Motor de Síntese:** FluidSynth processa a fila MIDI em uma thread de áudio dedicada de alta prioridade.
- **Buffers:** Geração de sinal PCM de 32 bits (float) através da função `fluid_synth_nwrite_float`.

### 2.4 Cadeia de Processamento DSP (Signal Chain)
O sinal sintetizado passa por um rack de efeitos estruturado em `dsp_chain.h` antes de chegar à saída física. A ordem segue padrões profissionais de estúdio para máxima clareza e fidelidade:

1.  **Filtros de Corte (HPF/LPF):** Limpeza de espectro indesejado.
2.  **Dinâmica:** Compressor para estabilização de picos.
3.  **Tonalidade:** Equalizador Paramétrico (Low-Shelf, Mid-Peak, High-Shelf).
4.  **Modulação:** Chorus e Tremolo (Calibrados para musicalidade).
5.  **Espaço/Tempo:** Delay e Reverb (Efeitos de profundidade).
6.  **Proteção:** Limiter final para evitar clipping digital.

## 3. Requisitos Funcionais
- **Múltiplos SoundFonts:** Carregamento simultâneo e isolado de arquivos SF2.
- **Preset Management:** Seleção dinâmica de bancos e programas por canal.
- **Efeitos em Tempo Real:** Controle individual de parâmetros DSP por canal e no canal Master.
- **DSP Bypass:** Possibilidade de desvio total do processamento para economia de CPU.

## 4. Requisitos Não Funcionais (NFR)
- **Baixa Latência:** Uso do Google Oboe (AAudio/OpenSLES) com buffer size otimizado para resposta instantânea ao toque.
- **Fidelidade Sonora:** Processamento em taxa de amostragem fixa (ex: 48kHz) sincronizado com o hardware do dispositivo via `stk::Stk::setSampleRate`.
- **Estabilidade:** Sincronização estrita de memória JNI para evitar inconsistências entre a UI (Kotlin) e o Motor (C++).
- **Eficiência:** Uso de funções matemáticas otimizadas e processamento em bloco (block sizes adequados) para garantir 0% de ocorrência de x-runs (glitches de áudio).

## 5. Estrutura de Arquivos Críticos
- `fluidsynth_bridge.cpp`: Ponto de entrada JNI e gerenciamento de threads nativas.
- `dsp_chain.h`: Implementação do rack de efeitos e algoritmos de processamento.
- `MixerViewModel.kt`: Orquestração de estado e lógica de roteamento em alto nível.
- `FluidSynthEngine.kt`: Driver de software para comunicação com a biblioteca nativa.
