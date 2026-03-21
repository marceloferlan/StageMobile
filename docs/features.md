# Funcionalidades e Requisitos: StageMobile

Este documento descreve as capacidades funcionais do StageMobile e os requisitos não funcionais que sustentam a aplicação.

## 1. Principais Funcionalidades

### 1.1 Mixer de 16 Canais
- **Capacidade:** Suporte para até 16 instrumentos simultâneos.
- **Controles por Canal:** Volume (Fader), Pan, Mute, Solo e Arm (Habilita entrada MIDI).
- **Indicadores Visuais:** Medidores de nível (Peak/RMS) em tempo real.

### 1.2 Gerenciamento de SoundFonts (SF2)
- **Carregamento Flexível:** Possibilidade de carregar diferentes arquivos SF2 em canais diferentes.
- **Navegação de Presets:** Interface para escolha de Bancos e Programas de forma rápida (Stage-ready).
- **Cache Inteligente:** Gerenciamento de memória para evitar duplicidade de SoundFonts idênticos.

### 1.3 Rack de Efeitos DSP Profissional
O sistema possui um rack de efeitos nativo em C++ aplicado individualmente por canal ou no Master:
- **HPF/LPF:** Filtros de higienização de frequência.
- **Compressores/Limitadores:** Controle de dinâmica e proteção contra clipping.
- **EQ Paramétrico:** Ajuste fino de timbre (3 bandas).
- **Modulação:** Chorus e Tremolo calibrados.
- **Espaciais:** Delay e Reverb de alta fidelidade.

### 1.4 MIDI Learn e Integração
- **Mapeamento:** Qualquer controle da interface (Knobs/Faders) pode ser mapeado para um controlador MIDI externo (CC Numbers).
- **Suporte Multidispositivo:** Reconhecimento automático de teclados e controladores USB MIDI.

### 1.5 Teclado Virtual (Keybed)
- **Interatividade:** Teclado polifônico sensível a toque com suporte a glissando.
- **Configuração:** Transposição de oitavas e semitons em tempo real.

## 2. Fluxos e Regras de Negócio
- **Prioridade de Audio:** O áudio nativo tem prioridade absoluta. Comandos de UI não podem bloquear a thread de renderização.
- **Signal Chain:** A ordem dos efeitos é fixa para garantir a integridade do timbre (Filtros -> Dinâmica -> EQ -> Modulação -> Espaciais -> Limiter).
- **Default State:** Ao iniciar, todos os efeitos de canal são desabilitados para preservar CPU e garantir um som "seco" inicial controlado pelo usuário.

## 3. Requisitos Não Funcionais (NFR)
- **Latência:** O tempo entre o toque e o som deve ser inferior a 20ms (prioridade AAudio/Oboe).
- **Performance:** Consumo de CPU otimizado para permitir polyphony de até 128 notas simultâneas.
- **Robustez:** Ausência total de glitches sonoros ("clicks" ou "pops") durante a troca de parâmetros ou carregamento de presets.
- **Responsividade:** Layout adaptável para Celulares e Tablets (variável `isTablet`), otimizando o espaço da tela para manipulação ao vivo.
