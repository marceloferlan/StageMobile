# Fluxo de Roteamento de Áudio (Virtual Keyboard -> Interface Oboe)

Este documento mapeia exatamente o caminho que um evento de nota segue desde o toque do usuário na interface gráfica até a geração do som físico na interface de áudio do Android ou falha (som de mola / fallback puro).

## 1. Interação do Usuário (Toque Físico)
- **Arquivo:** `VirtualKeyboard.kt`
- **Componente:** Modifier `pointerInput(Unit)` 
- **O que acontece:** O usuário toca no teclado construído por geometria do `Canvas`. A lógica customizada cruza as coordenadas X/Y do dedo e decide qual índice foi apertado. Retorna um índice cru e soma com oitava inicial. Por exemplo, a tecla do meio emana a nota MIDI C3, de código `48`.
- **Saída:** O lambda `onNoteOn(note: Int)` ou `onNoteOff(note: Int)` é invocado de volta para o parente hierárquico, entregando o número `48`.

## 2. Roteamento na Camada de UI (Repasse)
- **Arquivo:** `MixerScreen.kt`
- **O que acontece:** O Teclado Virtual foi renderizado com os eventos encadeados:
```kotlin
VirtualKeyboard(
    onNoteOn = { viewModel.noteOn(it) }, 
    onNoteOff = { viewModel.noteOff(it) }
)
```
- **Saída:** A Nota `48` entra na regra de negócios centralizada do aplicativo.

## 3. Distribuição e Transposição (Regra de Negócios)
- **Arquivo:** `MixerViewModel.kt`
- **O que acontece:** Função `noteOn(midiNote: Int)`:
    1. Resgata do cache apenas os canais fisicamente aptos para tocar: `val cached = armedChannelsCache`
    2. Lê os metadados do estado de cada canal armado (Se o canal possui split limitando alcance).
    3. Soma os shifts de oitavas/tons: `octaveShift * 12 + transposeShift`.
    4. Chama o motor global: `_audioEngine.noteOn(ch.id, transposedKey, 100)`.  (Velocity é 100).
    5. Atualiza contadores e UI para que o LED do canal pisque com a luz de Velocity.

## 4. Camada de Abstração JNI (Kotlin -> Native)
- **Arquivo:** `FluidSynthEngine.kt`
- **O que acontece:** Encapsula JNI com bloqueios de segurança (`isInitialized`). A chamada cai no abismo C++ do código Kotlin via `nativeNoteOn(channel, key, velocity)`.

## 5. Fila de Eventos MIDI (Concorrência Multithread)
- **Arquivo:** `fluidsynth_bridge.cpp` 
- **O que acontece:** Como a chamada original rodou no *UI Thread* do Kotlin, nós não podemos prender ela ou atacar o sistema de renderização sonoro do `FluidSynth` diretamente (Isso poderia dar engasgos ou quebra de frames). 
- O evento entra numa fila estrita: `midiQueue.push_back({NoteOn, ch, key, vel})`. Dispara um Lock Wake Up (`queueCv.notify_one()`).

## 6. Processamento Rápido Loop-MIDI (Thread Oboe Secundaria)
- **Arquivo:** `fluidsynth_bridge.cpp`
- **O que acontece:** Um loop que roda num núcleo rápido pega o item na frente da fila `midiQueue`. E esvazia o NoteOn injetando a informação real na API interna da linguagem C: `fluid_synth_noteon(synth, channel, key, vel);`.

## 7. O Ponto de Falha e Processamento DSP (Rack Nativo)
- O motor de áudio utiliza um rack de efeitos nativo em C++ (`dsp_chain.h`).
- **Ordem Profissional de Processamento:** O sinal segue a cadeia de estúdio para garantir integridade:
    1. **HPF/LPF:** Limpeza de frequências extremas.
    2. **Dynamics:** Compressor (estabilização) e Limiter (proteção final).
    3. **Tone:** Equalizador Paramétrico.
    4. **Modulation:** Chorus e Tremolo.
    5. **Time/Space:** Delay e Reverb.
- **Sincronização JNI:** Os índices entre Kotlin e C++ são mapeados 1:1 (sen ordenação automática por prioridade), garantindo que o comando enviado na UI atinja o efeito correto no motor.

## 8. Correções Estruturais (Fim da "Mola")
A distorção metálica ("mola") foi resolvida através de três pilares:
1. **Chorus Base Delay:** Reduzido de 136ms (6000 samples) para **20ms** (1000 samples). O valor original do STK era um eco, o que causava ressonância metálica quando modulado.
2. **Double-Tick avoided:** O processamento estéreo do Chorus agora "ticka" o objeto STK apenas uma vez por amostra, evitando instabilidade no LFO.
3. **STK Sample Rate Sync:** A taxa de amostragem global (`stk::Stk::setSampleRate`) é inicializada no `prepare()`, garantindo que os filtros e delays operem na frequência real do hardware (48kHz).

## 9. Escoamento Pro Alto-falante de Dispositivo
- O Motor do `Oboe` da Google corre paralelo exigindo renderizações novas através de `onAudioReady()`.
- O buffer cru gerado pelo Passo 7 é processado pelo Rack DSP (Passo 8).
- O Kernel (`AAudio` ou `OpenSLES`) projeta o sinal final pro alto-falante.
