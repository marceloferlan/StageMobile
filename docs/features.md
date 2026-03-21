# Funcionalidades e Requisitos: StageMobile

Este documento descreve as capacidades funcionais do StageMobile e os requisitos não funcionais que sustentam a aplicação.

## 1. Principais Funcionalidades

### 1.1 Mixer de 16 Canais (Mixer Multicamadas)
- **Operação:** Até 16 instrumentos simultâneos.
- **Controles Individuais:** Fader de Volume, Pan, Mute, Solo e Armamento (Active MIDI Channel).
- **Indicadores de Nível:** Metering de Peak e RMS integrados com decaimento visual suave.

### 1.2 Gerenciamento de SoundFonts (SF2)
- **Navegação:** Interface intuitiva para busca de Bancos e Programas.
- **Presets Dinâmicos:** Possibilidade de rotear múltiplos canais para o mesmo patch ou patches diferentes.
- **Otimização de Carregamento:** O sistema utiliza um cache de `sfId` para que múltiplos canais que usam o mesmo arquivo SoundFont não dupliquem o uso de memória RAM.

### 1.3 Rack de Efeitos DSP e Cadeia de Sinal
O processamento de áudio segue uma ordem de sinal fixa ("Signal Chain") inalterável para manter a consistência sonora:
1. **HPF/LPF** (Filtros de corte) -> 2. **Compressor** -> 3. **EQ Paramétrico** -> 4. **Chorus** -> 5. **Tremolo** -> 6. **Delay** -> 7. **Reverb** -> 8. **Limiter**.

### 1.4 MIDI Learn (Mapeamento de Hardware)
- **Funcionalidade:** Permite vincular qualquer parâmetro visual (Knobs de EQ, Volume, etc.) a um controlador físico externo.
- **Processo:** O usuário entra no modo de aprendizado (Long-press), move o controle no teclado externo, e o app armazena o `Control Change` (CC) correspondente através do `MidiConnectionManager`.
- **Persistência:** Todos os mapeamentos são salvos automaticamente no `SettingsRepository`.

### 1.5 Monitoramento de Recursos e Telemetria
- **Performance:** Painel informativo que exibe em tempo real o uso de CPU e RAM.
- **Cálculo Híbrido:** O `SystemResourceMonitor` detecta instantaneamente o carregamento de novas SF2 através da medição de Deltas no Native Heap, proporcionando feedback visual imediato para o músico.

### 1.6 Teclado Virtual (Keybed Scalable)
- **Design:** Touch-sensitive com suporte a deslize entre notas (`glissando`) e transposição instantânea (+/- 2 Oitavas / 12 Semitons).

## 2. Fluxos e Regras de Negócio
- **Armed Channels:** Somente os canais marcados como "Armed" (Luz ligada) respondem prioritariamente aos eventos de Note On.
- **Global Settings:** O usuário pode configurar globalmente a Polifonia Máxima (até 256 vozes) e o método de Interpolação do FluidSynth para equilibrar fidelidade sonora e consumo de CPU.
- **Sustain Inversion:** Regra específica para compatibilidade com pedais de polaridade invertida (Yamaha/NC).
- **Latency First:** O sistema prioriza a renderização de áudio em detrimento de animações da interface se houver sobrecarga de CPU.
- **Silence DSP:** O app possui um Master Bypass que silencia todo o processamento nativo para diagnóstico.

## 3. Requisitos Não Funcionais (NFR)
- **Resiliência:** O áudio continua soando mesmo se a atividade for para segundo plano (Audio-foreground service).
- **Musicalidade:** Ativação/desativação de efeitos utiliza cross-fades ou filtros de estabilização estruturais para evitar "pops".
- **Acessibilidade de Palco:** Todos os elementos visuais são dimensionados para manipulação com luvas ou dedos suados/úmidos em contextos de show.
