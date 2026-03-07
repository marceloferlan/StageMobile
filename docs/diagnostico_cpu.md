# Relatório de Diagnóstico: Uso de CPU (StageMobile)

Este documento detalha o consumo de ciclos de processamento (CPU) do aplicativo StageMobile, analisado em MARÇO/2026, com foco na eficiência da síntese de áudio em tempo real e na renderização da interface profissional.

## 📊 Sumário Executivo: Prioridade em Latência Zero
A arquitetura do StageMobile é otimizada para "Performance de Palco". Isso significa que a CPU é instruída a priorizar a continuidade do fluxo de áudio sobre a economia de energia, garantindo que não ocorram *audio glitches* durante o uso de baixa latência.

## 🏆 Ranking de Consumo de CPU

### 1. Motor de Síntese Polifônica (Nativo C++) - ~40% a 70% (Dependendo da Polifonia)
O processamento ocorre quase inteiramente na camada nativa (`libfluidsynth.so`), executando cálculos matemáticos intensivos:
- **Osciladores e Envelopes:** Cada nota pressionada em um canal MIDI dispara geradores de áudio que computam amostras em tempo real.
- **Interpolação de Samples:** O motor redimensiona amostras de áudio para diferentes tons instantaneamente.
- **Efeitos e Filtros:** Processamento de filtros passa-baixas e ressonância nativos.
- **Impacto:** Quanto mais notas tocadas simultaneamente (polifonia), maior o consumo de CPU.

### 2. Pipeline de Áudio de Baixa Latência (Google Oboe) - ~10% a 15%
A biblioteca `liboboe.so` opera em **Modo Exclusivo** no Android:
- **Audio Worklet Threads:** Threads de altíssima prioridade que precisam ser alimentados com dados a cada poucos milissegundos (64 frames de buffer).
- **Consumo Constante:** Mesmo sem tocar nenhuma nota, esse pipeline mantém a CPU "acordada" para evitar latência no primeiro toque.

### 3. Interface Reativa & VU Meters (Jetpack Compose) - ~10% a 20%
A interface do mixer é dinâmica e exige atualizações frequentes:
- **VU Meters (Alta Resolução):** Em tablets, 60 segmentos de LED precisam ser redesenhados a cada ~40ms para garantir fluidez visual.
- **Recomposições de Estado:** A sincronização dos faders e botões com o estado do motor de áudio.
- **Otimização:** O app utiliza o grid de LEDs de forma otimizada para minimizar o custo dessas atualizações visuais.

### 4. Processamento MIDI e Lógica de Negócio - ~5%
Monitoramento e parsing de eventos MIDI:
- **USB MIDI Direct Access:** Comunicação direta via hardware para minimizar o *jitter*.
- **MixerViewModel:** Lógica de decaimento (decay) dos VU meters e cálculos de volume.

---

## ⚡ Variáveis de Impacto na Performance

- **Tamanho do Buffer:** Quanto menor o buffer (ex: 64 frames), mais vezes a CPU é interrompida para processar áudio, aumentando a carga global mas diminuindo drasticamente a latência.
- **Taxa de Amostragem (Sample Rate):** Operar em 48kHz exige mais cálculos por segundo do que em 44.1kHz.
- **Quantidade de Canais Ativos:** O processamento cresce linearmente com o número de instrumentos carregados e ativos no mixer.

## 🔍 Conclusão Técnica
O consumo de CPU do StageMobile é classificado como **"Intensivo Sustentado"**. Ao contrário de apps convencionais que operam em rajadas (*bursts*), este app mantém a CPU em um estado de alto desempenho para assegurar que a experiência do músico seja idêntica a de um hardware dedicado. A taxa de uso reportada de ~50-60% durante um toque intenso é ideal para um dispositivo móvel moderno, deixando margem de segurança para o sistema operacional sem comprometer o áudio.
