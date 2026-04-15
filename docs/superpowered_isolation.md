# Isolamento do Superpowered SDK: Arquitetura e Solução

Este documento registra a arquitetura implementada para resolver os crashes e travamentos estruturais causados pela integração do **Superpowered SDK** para Áudio USB no StageMobile.

## 1. O Problema (Conflito de STL API)
Ao integrar a biblioteca estática do Superpowered SDK, o aplicativo sofria crash (SIGSEGV) em `Initialize` ou travava indefinidamente (ANR) em `DynamicInitialize`. O diagnóstico revelou uma incompatibilidade fatal de **C++ ABI (Application Binary Interface)**:
- O **Oboe** e a base do NDK do projeto `app` requerem a compilação padrão com `c++_shared`.
- O **Superpowered SDK** (fornecido apenas como lib estática `.a`) foi pré-compilado internamente exigindo `c++_static`.
- Quando ambos são ligados na mesma biblioteca compartilhada (`libsynthmodule.so`), as estruturas da STD Library, tabelas de métodos virtuais (vtables) e representações de `std::mutex` da versão _shared_ entram em colapso contra as compilações embutidas do SDK _static_, destruindo a integridade da memória.

## 2. Solução Implementada: Desacoplamento via Módulo Gradle Isolado
Para manter ambos funcionais no mesmo processo e sem reescrever o motor do Oboe, nós implementamos um **Bridge Architecture**, alocando toda a responsividade do USB para um **Módulo Gradle Totalmente Isolado**.

### 2.1. O Módulo `:superpowered-usb`
A solução isola o Superpowered em seu próprio quarto de contexto:
- Novo módulo sem dependências cruzadas (com exceção do `core-ktx`).
- Diretiva CMake explícita: `arguments += "-DANDROID_STL=c++_static"`.
- O módulo compila o `libspbridge.so`, engolindo a SDK estática do Superpowered. Dessa forma, as definições C++ são autossuficientes.

### 2.2. A Ponte ("The C Bridge")
Como as abstrações em C++ não podem cruzar de forma segura entre uma biblioteca _shared_ (`synthmodule`) e _static_ (`spbridge`), criamos um elo fraco em **C puro** (`extern "C"`). Todo o tráfego audio-real-time é feito por function pointers nativos, sem vtable e STL containers.

#### Header Compartilhado: `sp_bridge.h`
```cpp
#ifdef __cplusplus
extern "C" {
#endif

typedef bool (*SpRenderCallback)(float* audioIO, int numFrames, int numChannels);
void sp_register_render_callback(SpRenderCallback callback);

#ifdef __cplusplus
}
#endif
```

## 3. Fluxo de Vida Intelectual (O App rodando)

### Startup do Kotlin
O Kotlin, ao iniciar o StageMobile, sobe o `SuperpoweredUSBAudioManager` que, imediatamente, pede carregamento do bridge:
```kotlin
System.loadLibrary("spbridge") // Sobe a lib c++_static sem chutar o Oboe
```

O `MixerViewModel` sobe depois, instanciando o `FluidSynthEngine` (Motor que interage com Oboe através de `libsynthmodule.so`). 
Ao ter sucesso na inicialização nativa, chamamos a nova JNI `nativeRegisterSpBridge()`.

### JNI `dlopen` do Render Callback (Engine -> USB)
O FluidSynthEngine (Escrito usando `c++_shared`) se comunica com a biblioteca ponte em tempo de execução:

1. `dlopen("libspbridge.so", RTLD_NOW)`
2. `dlsym` rastreia o endereço de memória nua da função `sp_register_render_callback`.
3. Assina nela a função mestra de áudio (o "Render Callback" interno no `fluidsynth_bridge.cpp`).

### O Loop do Audio Hardware USB
Quando o Superpowered assume controle sobre o File Descriptor (`fd`) USB da interface sonora (ex: AudioBox USB):
1. O Android USB Engine solicita um Buffer PCM (na thread Superpowered).
2. O Bridge atende o Buffer e pergunta à função inscrita (`gRenderCallback`).
3. O Oboe (fluidsynth engine) bloqueia o motor midi momentaneamente (`engine_mutex`), gera o batch de áudio PCM final (DSP e Synth) e interpola o retorno na mesma folha de memória.

## 4. Correção Global de Permissões USB no Android 12+
Junto disso, resolvemos duas falhas das Intents globais do Android 12 em Broadcasters:
* Evitou-se o preenchimento de devices e resultamentos nulos nos intents modificando o preenchimento da permissão para `PendingIntent.FLAG_MUTABLE`.
* Foi adotado o registro da broadcast `ContextCompat.RECEIVER_EXPORTED` permitindo o app receber intents externos de Attach (Engate Físico) expedidos em hardware pelo Android USB System.
