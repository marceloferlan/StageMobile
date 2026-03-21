# Padrões e Requisitos de Interface: StageMobile (UI/UX)

Este documento define as diretrizes de design, padrões de componentes e heurísticas de usabilidade aplicadas ao StageMobile.

## 1. Conceito de Design: "Stage Ready"
O design foi concebido para uso em ambientes de palco (baixa luminosidade, necessidade de resposta rápida).
- **Tema:** Dark Mode (0xFF121212) como padrão estrito.
- **Estética:** Industrial/Premium com texturas de "Brushed Metal" e "Glassmorphism".
- **Contraste:** Uso de cores vibrantes para sinalizar estados ativos (LEDs, Meterings).

## 2. Paleta de Cores e Semântica DSP
Para facilitar o reconhecimento rápido na barra de rack de efeitos, utilizamos cores semânticas para cada categoria:

| Categoria | Tipo de Efeito | Cor Hexadecimal |
| :--- | :--- | :--- |
| **Spectral** | EQ Paramétrico / HPF / LPF | #4FC3F7 (Cyan) / #4DD0E1 / #4DB6AC |
| **Dynamics** | Compressor / Limiter | #E57373 (Red) / #FFD54F (Amber) |
| **Modulation** | Chorus / Tremolo | #81C784 (Green) / #AED581 |
| **Time/Space** | Delay / Reverb | #BA68C8 (Purple) / #9575CD |
| **Logic** | Reverb Send | #FFB74D (Orange) |

## 3. Componentes de Interface Customizados (Custom Controls)
- **`DSPCircularKnob`:**
    - **Operação:** Arraste vertical (Slide) para ajuste.
    - **Precisão:** Exibição dinâmica de 2 casas decimais para frequências baixas.
    - **Visual:** Feedback de arco luminoso ao redor do knob.
- **`DSPVerticalFader`:** 
    - Representação clássica de mesa de som.
    - Área de toque ampliada para evitar "miss-clicks" durante a performance.
- **`DSPMeter`:**
    - Representação Peak/RMS com gradiente de cor (Verde -> Amarelo -> Vermelho para Clipping).

## 4. Diretrizes de Usabilidade (UX)
- **Latência Sensível:** Qualquer animação de transição deve ser instantânea ou curta (menos de 150ms) para não atrasar a sensação de controle.
- **Feedback Hápico e Visual:** Todo botão ou LED de estado (isEnabled) deve mudar drasticamente de brilho ou cor ao ser acionado.
- **Layout Responsivo (Cálculo `isTablet`):**
    - **Celular:** Foco em profundidade (Mixer vertical / Canais selecionáveis).
    - **Tablet:** Foco em largura (Visão de múltiplos Strips de canais simultaneamente).

## 5. Tipografia
- **Títulos:** FontWeight.Bold para rápida leitura de nomes de presets e parâmetros.
- **Small Labels:** Fontes compactas (8sp a 10sp) em Knobs para poupar espaço em telas menores sem perder legibilidade.
