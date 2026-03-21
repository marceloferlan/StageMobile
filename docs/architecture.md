# Arquitetura do Sistema: StageMobile

Este documento detalha a infraestrutura técnica, as decisões de design e a organização dos componentes do projeto StageMobile.

## 1. Stack Tecnológica
- **Linguagem Principal:** Kotlin (Android) e C++ (Motor de Áudio).
- **Interface Gráfica:** Jetpack Compose (Material 3).
- **Motor de Áudio (Nativo):**
    - **FluidSynth:** Síntese de áudio baseada em SoundFonts (SF2).
    - **Oboe:** API de áudio C++ da Google para baixa latência (AAudio/OpenSLES).
    - **STK (Synthesis Toolkit):** Algoritmos de efeitos DSP (Chorus, Reverb, EQ, etc.).
    - **SoundTouch:** Processamento de tempo e pitch.
- **Gerenciamento MIDI:** Android MidiManager API.
- **Persistência:** `SharedPreferences` (via `SettingsRepository`).

## 2. Componentes e Papéis (Arquitetura)

### 2.1 Camada de UI (Kotlin/Compose)
A interface é organizada para separar lógica de componentes puros:
- **Screens:** Telas de alto nível (`MixerScreen`, `SystemGlobalSettings`, `DownloadsScreen`).
- **Mixer Logic:** Componentes que traduzem estado em áudio (`InstrumentChannelStrip`, `VirtualKeyboard`).
- **Custom Components:** Widgets de alta performance (`DSPCircularKnob`, `DSPVerticalFader`, `DSPMeter`).
- **Theme:** Design System (Cores, Tipografia).

### 2.2 Camada de Domínio e Modelo
- **`InstrumentChannel`:** Objeto central que armazena parâmetros de Mix e a instância do preset SF2.
- **`Sf2Preset`:** Metadados do patch carregado no FluidSynth.
- **`DSPEffectInstance`:** Armazena metadados e parâmetros dos efeitos ativos.

### 2.3 Camada de MIDI (Acesso ao Hardware)
- **`MidiConnectionManager`:** Monitora portas USB/Bluetooth.
    - **Regra:** Auto-conecta a múltiplos dispositivos simultaneamente.
    - **Performance:** Separa o parsing da mensagem MIDI (`MidiReceiver`) em uma `MidiProcessingThread` com prioridade de áudio.
- **`MidiUtils`:** Utilitários para conversão de notas (ex: 60 -> C4).
- **`MidiLearnMapping`:** Persiste o vínculo entre um controlador externo (CC) e um alvo interno (`MidiLearnTarget`).

### 2.4 Camada de Motor de Áudio (Native Bridge)
- **`FluidSynthEngine`:** Interfacia o Kotlin com o motor nativo.
- **`fluidsynth_bridge.cpp`:** Mantém a `midiQueue` (lock-free) e orquestra a síntese via Fluidsynth.
- **`dsp_chain.h`:** Rack linear de efeitos que processa o áudio sintetizado.

### 2.5 Utilitários e Infraestrutura
- **`SystemResourceMonitor`:** Monitoramento de saúde do app.
    - **Funcionamento:** Medição híbrida de memória (PSS + Native Heap Deltas). O sistema utiliza uma "âncora PSS" atualizada a cada 30 segundos para garantir precisão de longo prazo, enquanto os deltas de Native Heap fornecem reatividade instantânea (baixo jitter) ao carregar SoundFonts de 500MB+.
- **`UiUtils`:** Inteligência reativa para layouts Tablets vs Celulares baseada em WindowSizeClasses.

## 3. Comunicação e Concorrência
- **Thread UI (Kotlin):** Recebe inputs e atualiza o estado observável no `MixerViewModel`.
- **MidiProcessingThread (Java):** Thread de prioridade de áudio que despacha eventos MIDI para o C++.
- **Render Thread (C++/Oboe):** Thread de tempo real de altíssima prioridade dedicada exclusivamente à geração de amostras sonoras.

## 4. Principais Decisões Arquiteturais
1.  **Threaded MIDI Dispatch:** Eventos MIDI são movidos para fora da UI Thread no momento em que entram no app.
2.  **Fixed Sample Rate (48kHz):** Todos os cálculos de DSP e Sintetizador são fixados na taxa do hardware para evitar artefatos de aliasing.
3.  **No-Sort DSP Rack:** A ordem dos efeitos em C++ é idêntica à ordem de inserção no Kotlin, garantindo que o índice visual na UI corresponda exatamente ao índice físico no buffer de áudio.
