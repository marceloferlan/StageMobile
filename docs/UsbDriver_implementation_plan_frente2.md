# Frente 2 — Driver USB Próprio com libusb

## Contexto

O módulo `:superpowered-usb` hoje faz uma coisa: recebe o `fd` USB do Android e executa transferências
isócronas diretas via `Superpowered::AndroidUSBAudio::easyIO`. Essa função contorna o HAL do Android,
reduzindo jitter em dispositivos Exynos.

O objetivo é reproduzir exatamente esse comportamento usando **libusb (LGPL-2.1)** open-source,
eliminando a dependência do Superpowered SDK (licença inviável).

---

## Arquitetura Atual vs. Nova

### Atual (Superpowered)
```
Kotlin: SuperpoweredUSBAudioManager.kt  (módulo :superpowered-usb)
  └─ JNI → libspbridge.so (c++_static)
       └─ Superpowered::AndroidUSBAudio::easyIO(fd, ...)
            └─ callback → gRenderCallback → synthmodule.so
```

Problemas:
- 2 `.so` separados com STLs incompatíveis (c++_static vs c++_shared)
- Cross-lib callback via ponteiro C `SpRenderCallback`
- Superpowered SDK proprietário com licença inviável

### Nova (libusb)
```
Kotlin: UsbAudioManager.kt  (dentro do módulo :app)
  └─ JNI → libsynthmodule.so (c++_shared) — UNO só .so
       └─ UsbAudioDriver::start(fd, sampleRate, channels)
            └─ libusb_wrap_sys_device(fd) → USB isochronous transfers
            └─ lê do RingBuffer compartilhado com synthRenderLoop
```

Vantagens:
- Tudo em `libsynthmodule.so` — sem dlopen, sem ponte C
- STL única (c++_shared)
- Open-source LGPL-2.1

---

## Fases de Implementação

### Fase 1 — libusb setup + CMakeLists + smoke test
**Escopo:**
- Clonar libusb em `app/src/main/cpp/externals/libusb/`
- Adicionar sources ao `CMakeLists.txt` como parte de `synthmodule`
- Criar `usb_audio_driver.h/.cpp` com `libusb_init` e `libusb_exit` apenas
- JNI: `nativeUsbProbe(fd): Boolean` — abre device e loga descritores
- Kotlin: `UsbAudioManager.kt` stub com hotplug registration

**Critério de sucesso:** `assembleDebug` compila sem erro com libusb linkado.

---

### Fase 2 — Descriptor parsing + endpoint detection
**Escopo:**
- Chamar `libusb_wrap_sys_device(ctx, fd, &devHandle)`
- `parseAudioDescriptors()` — encontrar interface AudioClass + endpoints isócronos OUT
- Selecionar alternate setting para 48kHz/24bit/stereo
- Logar: sample rate, bit depth, max_packet_size, polling interval

**Critério de sucesso:** Com PreSonus connectada, logs mostram os endpoints corretos.

---

### Fase 3 — Isochronous OUT transfers (teste com sine wave)
**Escopo:**
- `startStreaming()` — thread USB dedicada com prioridade SCHED_FIFO
- Isochronous transfer loop: `libusb_submit_transfer` + `libusb_handle_events`
- Preencher buffers com sine wave 440Hz (sem conectar ao FluidSynth ainda)
- Clock sync: adaptive (lê SOF frames, ajusta fill)

**Critério de sucesso:** PreSonus AudioBox toca o sine wave sem cliques.

---

### Fase 4 — Integração com ring buffer do synthRenderLoop
**Escopo:**
- Substituir sine wave pelo áudio real do RingBuffer da engine
- O USB thread lê do mesmo ring buffer que o Oboe preenche
- Remover Oboe do path quando USB driver está ativo
- `MixerViewModel`: switch Nativo ↔ Otimizado usa `UsbAudioManager` novo

**Critério de sucesso:** FluidSynth soa pela interface USB sem Oboe ativo.

---

### Fase 5 — Testes multi-device + edge cases
**Escopo:**
- Testar com PreSonus AudioBox USB 96 (já disponível)
- Hot-unplug: cleanup limpo, fallback para Oboe automático
- Sample rate mismatch: detectar e logar aviso
- Test Galaxy Tab S9 FE (Exynos) + Galaxy S24 Ultra (Snapdragon)

---

### Fase 6 — Remover módulo :superpowered-usb
**Escopo:**
- Deletar `superpowered-usb/` inteiro
- Remover do `settings.gradle.kts`
- Remover `SuperpoweredUSBAudioManager` imports do `MixerViewModel`
- Limpar build.gradle.kts do app (remover dependência do módulo)

---

## Novos Arquivos

### [NEW] `app/src/main/cpp/externals/libusb/` (git clone)
Fontes da libusb LGPL-2.1.

### [NEW] `app/src/main/cpp/usb_audio_driver.h`
Interface C++ da `UsbAudioDriver` class.

### [NEW] `app/src/main/cpp/usb_audio_driver.cpp`
Implementação: init, descriptor parsing, isochronous transfers, ring buffer read.

### [MODIFY] `app/src/main/cpp/CMakeLists.txt`
Adicionar libusb sources + `usb_audio_driver.cpp` + flag `-DPLATFORM_POSIX`.

### [NEW] `app/src/main/java/.../usb/UsbAudioManager.kt`
Kotlin: USB hotplug (BroadcastReceiver), permission request, JNI bridge.

### [MODIFY] `app/src/main/java/.../viewmodel/MixerViewModel.kt`
Trocar `SuperpoweredUSBAudioManager` por `UsbAudioManager`.

### [MODIFY] `app/src/main/cpp/fluidsynth_bridge.cpp`
Adicionar JNI methods: `nativeUsbStart`, `nativeUsbStop`, `nativeUsbIsActive`.

### [DELETE] `superpowered-usb/` (Fase 6)
Módulo inteiro removido.
