# Guia do Desenvolvedor: Manutenção e Expansão

Este guia é destinado a desenvolvedores e IAs que atuarão na manutenção, depuração e expansão do StageMobile. Ele foca em padrões recorrentes, protocolos de integração e resolução de problemas técnicos.

## 1. Padrões de Integração JNI (Kotlin <-> C++)

Para adicionar novas funcionalidades nativas, siga o padrão de assinatura rigoroso:

### 1.1 Assinaturas de Métodos
- **Kotlin:** `private native fun nativeSetAlgo(value: Float)`
- **C++:** `JNIEXPORT void JNICALL Java_com_example_stagemobile_audio_engine_FluidSynthEngine_nativeSetAlgo(JNIEnv *env, jobject thiz, jfloat value)`
- **Atenção:** Qualquer erro na assinatura resultará em `UnsatisfiedLinkError`. Sempre verifique o pacote completo no nome da função C++.

### 1.2 O Ciclo MIDI
Para adicionar um novo tipo de mensagem MIDI:
1. Adicione a constante em `MidiConnectionManager.kt`.
2. Implemente o parser no método `parseMidiData`.
3. Adicione o callback correspondente no `MixerViewModel`.

## 2. Protocolo de Expansão DSP (Novo Efeito)

Para inserir um novo efeito no Rack Nativo:
1. **C++ (`dsp_chain.h`):**
    - Crie uma classe herdando de `DSPEffect` (ou use uma primitiva do STK).
    - Implemente `processStereo(float* L, float* R)`.
    - Adicione o efeito no `DSPChannel::addEffect` (respeitando a ordem do sinal).
2. **Kotlin (`DSPEffectType.kt`):**
    - Registre o novo `ENUM` com seu rótulo e ícone.
3. **ViewModel (`MixerViewModel.kt`):**
    - Adicione o mapeamento em `getDefaultParamsFor`.
    - Crie a função de atualização `updateEffectXParam`.

## 3. Guia de Troubleshooting (Depuração Sistemática)

| Sintoma | Causa Provável | Ação Recomendada |
| :--- | :--- | :--- |
| **Som de Mola / Digital Ping** | Index mismatch no JNI ou STK Double-ticking | Verifique `fluidsynth_bridge.cpp` e logs de índice. |
| **X-Runs / Estalos no Áudio** | Buffer muito pequeno ou Bloqueio na Render Thread | Aumente o Buffer em Settings; remova `LOGD` da thread de renderização. |
| **Atraso Perceptível (Input Lag)** | Oboe usando OpenSLES em vez de AAudio | Verifique o `AudioDeviceName` e force AAudio se o Android for 8.0+. |
- **App Crash no SF2 Load:** Estouro de memória RAM (Heap Nativo). Use `SystemResourceMonitor` para checar se o arquivo excede a RAM livre.
  - *Veja:* [diagnostico_memoria.md](file:///Users/macbookpro/AndroidStudioProjects/StageMobile/docs/diagnostico_memoria.md) para entender o baseline de 200MB.

## 4. Comandos Essenciais de Terminal (ADB)

- **Filtrar Logs de Áudio:**
  `adb logcat -s FluidSynthEngine fluidsynth_bridge ResourceMonitor`
- **Checar Threads de Áudio:**
  `adb shell top -H -p $(pidof com.example.stagemobile)` (Procure por `MPT` ou `AAudio`)
- **Limpar Cache de SF2 via ADB:**
  `adb shell rm -rf /data/data/com.example.stagemobile/cache/*`

## 5. Regras de Estabilidade "Stage-Ready"
- **NUNCA** use `println` ou `Log.d` dentro do loop de áudio nativo (`onAudioReady`).
- **NUNCA** aloque memória (`new` / `malloc`) dentro do loop de áudio. Use pré-alocação no `prepare()`.
- **SEMPRE** use `lock-free queues` para comunicação entre a UI e o Motor de Áudio.
- **SEMPRE** valide a integridade estrutural (chaves e imports) após refatorações.

## 6. Protocolo de Integridade de Código (Prevenção de Erros)

Para evitar erros recorrentes de compilação, siga este checklist antes de concluir uma refatoração:

1.  **Verificação de Escopo:** Após editar blocos aninhados (especialmente Lambdas do Compose), verifique o alinhamento das chaves (`}`). Um erro comum é deixar um `Dialog` ou `Box` aberto, o que corrompe o escopo de todas as funções subsequentes no arquivo.
2.  **Auditoria de Imports:** Ao componentizar ou mover elementos entre pacotes (ex: de `ui.screens` para `ui.components`), certifique-se de que todos os arquivos afetados atualizaram seus `import`.
3.  **Build de Verificação:** Realize um build local (`./gradlew assembleDebug`) após qualquer alteração que modifique assinaturas de funções ou a estrutura de componentes compartilhados.
4.  **Substituições Seguras:** Ao usar ferramentas de edição de texto em arquivos grandes (> 500 linhas), prefira edições pequenas e localizadas para evitar remoções acidentais de delimitadores de escopo.
