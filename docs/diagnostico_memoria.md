# Relatório de Diagnóstico: Uso de Memória RAM (StageMobile)

Este documento detalha o consumo de memória RAM do aplicativo StageMobile, analisado em MARÇO/2026, com foco no uso inercial (ao abrir o app) e dinâmico (carregamento de SoundFonts).

## 📊 Sumário Executivo: ~200MB (PSS)
O uso inicial de cerca de 200MB de RAM é o comportamento esperado para uma arquitetura de áudio profissional híbrida (Kotlin + Motor C++ Nativo).

## 🏆 Ranking de Consumo Inercial

### 1. Bibliotecas Nativas (JNI/Shared Objects) - ~70MB a 90MB (PSS)
O app utiliza o motor **FluidSynth 2.3+** completo. Para funcionar no Android, ele exige o carregamento de 18 bibliotecas `.so` simultâneas:
- **Core:** `libfluidsynth.so`, `liboboe.so` (Google Oboe).
- **Dependências:** `libglib-2.0.so`, `libgio-2.0.so`, `libinstpatch-1.0.so` (Gerenciador de patches), `libsndfile.so` (Decodificação).
- **Codec Support:** Ogg, Vorbis, FLAC, Opus, PCRE.
O sistema operacional mapeia esses binários diretamente na RAM (Proportional Set Size), gerando o maior custo inicial de memória.

### 2. Buffers de Áudio e Estruturas de Síntese - ~40MB a 50MB
O motor C++ reserva buffers circulares de baixa latência para garantir que a síntese de 8 canais ocorra sem interrupções (*glitches*).
- **Configuração Atual:** Sample Rate: 48kHz | Buffer: 64 frames.
- **Estruturas:** Tabelas de modulação, geradores de envelope e osciladores pré-alocados para os canais do mixer.

### 3. Java Heap & Jetpack Compose - ~30MB a 40MB
Consumo gerenciado pela Dalvik/ART Virtual Machine:
- **MixerViewModel:** Gerenciamento de dezenas de `StateFlows` reativos (níveis de áudio, VU meters, estados de conexão).
- **UI Tree:** Representação visual de todo o Mixer (faders, botões, modais).
- **Settings Store:** Cache local de configurações e estado persistente.

### 4. Gestão de USB & Recursos - ~10MB
Listeners permanentes para detecção de hardware MIDI USB e o thread do `SystemResourceMonitor`.

---

## ⚡ Comportamento Dinâmico (SoundFonts)
O carregamento de instrumentos segue uma progressão linear:
- **Carregamento Prioritário:** O FluidSynth carrega o arquivo `.sf2` **integralmente** na memória nativa para permitir acesso aleatório às amostras de áudio com latência zero.
- **Fator de Impacto:** Se o usuário carrega um SoundFont de 100MB, o uso total do app saltará de ~200MB para ~300MB.

## 🔍 Conclusão Técnica
O StageMobile prioriza **estabilidade e latência** em vez de baixo uso de memória. O consumo é "frontal" (inercial alto) para garantir que o processamento em tempo real durante uma performance ao vivo não sofra interrupções por alocações dinâmicas ou carregamento de disco tardio.
