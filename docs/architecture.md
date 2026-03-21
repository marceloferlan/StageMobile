# Arquitetura do Sistema: StageMobile

Este documento descreve a infraestrutura técnica, as decisões de design e a organização dos componentes do projeto StageMobile.

## 1. Stack Tecnológica
- **Linguagem Principal:** Kotlin (Android) e C++ (Motor de Áudio).
- **Interface Gráfica:** Jetpack Compose (Material 3).
- **Motor de Áudio (Nativo):**
    - **FluidSynth:** Síntese de áudio baseada em tabelas de ondas (SoundFonts).
    - **Oboe:** API de áudio C++ da Google para baixa latência (AAudio/OpenSLES).
    - **STK (Synthesis Toolkit):** Algoritmos de efeitos DSP (Chorus, Reverb, EQ, etc.).
    - **SoundTouch:** Processamento de tempo e pitch.
- **Persistência:** `SharedPreferences` (via `SettingsRepository`) para configurações globais e mapeamentos MIDI.

## 2. Componentes e Papéis (Arquitetura)
O projeto segue uma arquitetura separada por camadas:

### Camada de UI (Kotlin/Compose)
- **`MixerViewModel`:** Atua como o "Cérebro" do app. Gerencia o estado de 16 canais, roteamento MIDI, carregamento de SoundFonts e sincronização com o motor nativo.
- **`InstrumentChannelStrip`:** Componente de interface que representa um canal físico.

### Camada de Domínio e Modelo
- **`InstrumentChannel`:** Modelo de dados que contém o estado de um canal (Preset, Mute, Solo, Volume, Pan, Efeitos).
- **`DSPEffectInstance`:** Contém os metadados e parâmetros de um efeito DSP.
- **`Sf2Preset`:** Representação de um patch de SoundFont (Bank/Programm).

### Camada de Motor de Áudio (Native Bridge)
- **`FluidSynthEngine`:** Wrapper JNI que expõe as funções C++ para o Kotlin.
- **`fluidsynth_bridge.cpp`:** Gerenciador do ciclo de vida do sintetizador e das threads de áudio.
- **`dsp_chain.h`:** Rack de efeitos nativo que processa o sinal PCM em tempo real.

## 3. Comunicação e Concorrência
- **Threads:** 
    - **UI Thread (Kotlin):** Interação do usuário.
    - **Synth Thread (Native):** Processamento da fila MIDI.
    - **Render Thread (Oboe):** Thread de tempo real de alta prioridade que gera o áudio final.
- **Sincronização:** Utiliza-se um `midiQueue` (fila de eventos) no C++ com mecanismos de trava mínima para garantir que comandos de UI não interfiram na estabilidade do áudio ("Zero Glitch").

## 4. Modelagem de Dados e Persistência
Não há uso de banco de dados relacional clássico (SQLite/Room) por questões de performance e simplicidade de estado.
- **Configurações:** Salvas em formato JSON/XML via `SharedPreferences`.
- **Mapeamento MIDI:** Salvo em `JSONArray` contendo `CC Number`, `Canal` e `Target`.

## 5. Principais Decisões Arquiteturais
1.  **Fixed Sample Rate:** O app opera prioritariamente em 48kHz para alinhar com o hardware Android moderno, evitando resostragem desnecessária.
2.  **Stateless Native DSP:** O motor C++ é "burro" em termos de lógica de negócio; ele apenas processa comandos enviados pelo Kotlin, que detém a "Fonte da Verdade" do estado.
3.  **SF2 Cache:** Implementação de um `sfId` único para evitar carregamentos redundantes de arquivos SoundFont idênticos na memória RAM.
