# Padrões e Requisitos de Interface: StageMobile (UI/UX)

Este documento define as diretrizes de design, padrões de componentes e heurísticas de usabilidade aplicadas ao StageMobile.

## 1. Conceito de Design: "Stage Ready" (Palco)
O design é "High-Contrast / Dark-Mode" para garantir legibilidade sob luzes de show intensas ou ambientes escuros.
- **Tema:** Dark Mode profundo (#121212) com acentos Vibrantes.
- **Superfícies:** Uso de texturas de metal escovado e gradientes sutis para simular hardware real (Analog-Digital Hybrid).

## 2. Paleta de Cores Semântica (Rack DSP)
Cada categoria de processamento possui uma cor fixa para facilitar a identificação periférica rápida:
- **Equalizadores/Filtros (Spectral):** Tons de Cianos e Verdes-Água (#4FC3F7, #4DD0E1).
- **Dinâmica (Compressão):** Tons de Vermelhos e Amarelos (#E57373, #FFD54F).
- **Modulação (Chorus/Tremolo):** Tons de Verdes Natureza (#81C784, #AED581).
- **Tempo/Espaço (Delay/Reverb):** Tons de Púrpuras e Violetas (#BA68C8, #9575CD).

## 3. Catálogo de Componentes Customizados

### 3.1 `DSPCircularKnob`
- **Interação:** Clique e arraste vertical (para cima aumenta, para baixo diminui).
- **Precisão Visual:** O display de valor acima do knob exibe **duas casas decimais** para frequências baixas (ex: 0.55 Hz), permitindo ajustes milimétricos.
- **Regra de LED:** O arco luminoso ao redor do knob reflete o valor normalizado do parâmetro no range definido.

### 3.2 `DSPVerticalFader`
- **Fader Profissional:** Possui área de toque expandida ("Grip area") para manipulação segura.
- **Logarithmic Display:** O movimento visual é linear, mas o mapeamento de ganho segue curvas logarítmicas quando aplicável (dB).

### 3.3 `DSPMeter` (Indicadores de Nível)
- **Funcionalidade:** Exibe Peak (Nível de pico instantâneo) e RMS (Energia média) simultaneamente.
- **Degradê de Segurança:** Verde (-inf a -12dB), Amarelo (-12 a -3dB) e Vermelho (Acima de -3dB / Clipping Imimente).

### 3.4 `SF2PresetSelectorDialog`
- **Organização:** Separação visual clara entre Bancos e Programas.
- **UX de Palco:** Seleção instantânea sem necessidade de confirmação extra ("Single-tap load"), permitindo trocas de timbre no meio de uma música.

### 3.5 `InstrumentChannelOptionsMenu`
- **Função:** Acesso rápido a funções destrutivas (Delete) e utilitárias (Duplicate).
- **MIDI Learn Trigger:** Ponto de entrada secundário para o modo de aprendizado MIDI.

## 4. Adaptabilidade e Responsividade (`isTablet`)
O sistema utiliza a classe `UiUtils.isTablet` (Baseada em `smallestScreenWidthDp >= 600`) para decidir o modo de visualização:
- **Modo Mobile:** Foco em profundidade. O Mixer é horizontalmente rolável, exibindo 1 ou 2 canais por vez com controles maiores. O teclado virtual é otimizado para o polegar.
- **Modo Tablet:** Foco em visão panorâmica. Exibe o console completo (mixers lado a lado) simulando uma mesa de hardware estúdio. O teclado virtual ganha mais extensão de oitavas visíveis.

## 5. Heurísticas de Controle e MIDI Learn
- **Long-Press:** Pressionar longa sobre qualquer Componente Customizado (`Knob` ou `Fader`) aciona o modo **MIDI Learn**. O componente exibe um overlay visual indicando que está aguardando sinal.
- **Visual Feedback:** Uso de indicadores LED (Verde no "Arm", Vermelho no "Mute", Amarelo no "Solo") para status crítico de canal.
- **Double-Tap (Reset):** Dois toques rápidos em um knob restauram o valor padrão (ex: 0dB no ganho ou 440Hz no pitch).

## 6. Info Panel (Monitoramento Global)
- **Status Bar Inferior:** Exibe o consumo de memória híbrida e CPU. O design utiliza tons sóbrios (Cinza/Cinza-claro) para não distrair do mixer, mas muda para Laranja/Vermelho se os recursos atingirem níveis críticos (80%+ de CPU).
