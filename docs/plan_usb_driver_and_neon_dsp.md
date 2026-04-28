# Plano: Driver USB Próprio (libusb) + Otimização NEON dos Efeitos DSP

*Criado em: 2026-04-27*
*Contexto: Superpowered SDK descartado por custo de licença incompatível com o modelo de negócio.*

---

## Contexto

Após call com o time comercial do Superpowered, o SDK foi descartado — o modelo de licença não diferencia a quantidade de recursos usados e o preço é inviável. Precisamos de duas frentes independentes:

1. **Driver USB próprio** — substituir o Superpowered USB Audio por implementação open-source com libusb
2. **Otimização NEON dos efeitos DSP** — vetorizar os efeitos mais caros (Reverb, Delay, Compressor) com ARM NEON intrinsics

Ambas as frentes são independentes e podem avançar em paralelo.

---

## Frente 1 — Driver USB Próprio com libusb

### O que o Superpowered fazia pra nós

Uma única coisa: tomar o file descriptor USB e fazer transferências isócronas diretamente, contornando o driver USB do Android (ALSA/HAL). Isso reduzia jitter em dispositivos Exynos (Tab S9 FE).

### Pré-requisitos

**1. Biblioteca libusb para Android**
```bash
git clone https://github.com/libusb/libusb.git /Users/macbookpro/Downloads/Dev/libusb
```
- Compilar como biblioteca nativa pra arm64-v8a
- Vive dentro de `libsynthmodule.so` (c++_shared) — sem módulo isolado, sem ponte C, sem dlopen
- Licença: LGPL-2.1 — permite uso em apps comerciais sem abrir o código do app (linkagem dinâmica)

**2. Especificação USB Audio Class**
- [USB Audio Class 1.0](https://www.usb.org/document-library/audio-device-class-spec-10) — cobre a maioria das interfaces class-compliant (PreSonus AudioBox, Focusrite Scarlett, Behringer, etc.)
- USB Audio Class 2.0 — interfaces profissionais de alta amostragem (192kHz+). Pode ser fase 2.
- Pontos-chave a implementar:
  - **Interface descriptor parsing** — encontrar os endpoints isócronos de áudio
  - **Alternate settings** — selecionar formato correto (sample rate, bit depth, channels)
  - **Isochronous OUT transfers** — enviar buffers de áudio pro device com timing preciso
  - **Clock synchronization** — adaptar ao clock do device USB (adaptive vs async)

**3. Referências de código (open-source)**
- **AOSP `usbaudio_hal.c`** — implementação de referência do Google pra USB audio no Android
- **android-usb-audio** (GitHub) — exemplos de acesso direto
- **PipeWire / PulseAudio** — implementações Linux de USB audio com libusb

**4. Hardware pra teste**
- ✅ PreSonus AudioBox USB 96 (já disponível)
- ✅ Galaxy Tab S9 FE — Exynos 1380 (já disponível)
- ✅ Galaxy S24 Ultra — Snapdragon 8 Gen 3 (já disponível)
- Recomendado: segunda interface USB de outro fabricante (Focusrite Scarlett Solo ou Behringer UMC22) pra validar compatibilidade

### Arquitetura proposta

```
┌──────────────────────────────────────────────────────────┐
│  Kotlin (app module)                                      │
│  ├─ UsbAudioManager.kt — hotplug, permissions, fd        │
│  └─ MixerViewModel — seletor de driver, health monitor   │
└────────────┬─────────────────────────────────────────────┘
             │ JNI (fd passado via nativeStartUsb(fd, sampleRate, channels))
             ▼
┌──────────────────────────────────────────────────────────┐
│  C++ (usb_audio_driver.cpp) — dentro de libsynthmodule.so│
│  ├─ UsbAudioDriver class                                 │
│  │   ├─ init(fd) → libusb_wrap_sys_device()              │
│  │   ├─ parseDescriptors() → find audio endpoints        │
│  │   ├─ selectFormat(48kHz, 24bit, stereo)                │
│  │   ├─ startStreaming() → isochronous transfer loop      │
│  │   └─ stop() / cleanup                                  │
│  ├─ Ring buffer (compartilhado com synthRenderLoop)       │
│  └─ Callback: lê do ring buffer → envia via ISO transfer │
│                                                           │
│  libusb.a (linked staticamente)                           │
└──────────────────────────────────────────────────────────┘
```

**Vantagem vs Superpowered:** tudo vive dentro de `libsynthmodule.so` (c++_shared) — sem módulo isolado, sem ponte C, sem dlopen/dlsym. Simplifica enormemente a arquitetura.

### Fases de implementação

| Fase | Escopo | Estimativa |
|---|---|---|
| **Fase 1** | Compilar libusb pra Android arm64, integrar no CMakeLists, validar `libusb_init` | 1 dia |
| **Fase 2** | Receber fd do Kotlin, abrir device via `libusb_wrap_sys_device`, enumerar endpoints | 2 dias |
| **Fase 3** | Implementar isochronous OUT transfers com buffer de áudio real (sine wave test) | 3-4 dias |
| **Fase 4** | Conectar ao ring buffer do `synthRenderLoop`, substituir o path Superpowered | 2 dias |
| **Fase 5** | Testes em múltiplas interfaces, edge cases (hot-unplug, sample rate mismatch) | 2-3 dias |
| **Fase 6** | Remover módulo `:superpowered-usb` inteiro | 1 dia |

**Total estimado: ~2-3 semanas**

### Coleta de informações do hardware (executar antes da Fase 1)

```bash
# Com a PreSonus AudioBox USB 96 conectada ao tablet via ADB:

# 1. Listar placas de som reconhecidas
adb shell "cat /proc/asound/cards"

# 2. Ver streams de áudio (sample rates, formatos)
adb shell "cat /proc/asound/card*/stream*"

# 3. Descriptors USB brutos (endpoints, interfaces)
adb shell "cat /sys/bus/usb/devices/*/descriptors" | xxd | head -200

# 4. Verificar se AAudio MMAP está ativo (referência de comparação)
adb shell "dumpsys media.aaudio" | grep -i mmap
```

---

## Frente 2 — Otimização NEON dos Efeitos DSP

### Dados de referência (APM HUD, 6 instrumentos × 4 efeitos)

| Métrica | SEM efeitos | COM efeitos (Compressor + EQ + Delay + Reverb) |
|---|---|---|
| AvgDspCh | 30-94µs | 630-760µs |
| MaxDspCh | ~200µs | 1000-1600µs (picos até 3335µs) |
| % do budget (2666µs) | 1-3% | 24-28% |

### Prioridade por efeito

| Efeito | Custo atual/canal | % do DSP total | Ganho esperado com NEON | Prioridade |
|---|---|---|---|---|
| **Reverb (FreeVerb)** | ~12-15µs (40-50%) | Maior | 2-3× | **#1** |
| **Delay** | ~7-8µs (25%) | Segundo | 2× | **#2** |
| **Compressor** | ~5-6µs (18%) | Terceiro | 1.3× (envelope sequencial) | #3 |
| **EQ (Butterworth IIR)** | ~3-4µs (12%) | Menor | ~1× (IIR sequencial) | Skip |

### Reverb — Reescrita necessária

O STK FreeVerb processa sample-a-sample por 8 comb + 4 allpass filters. Pra NEON funcionar efetivamente, precisa reestruturar o data layout:

**Opção A — Vetorizar FreeVerb (AoS → SoA):**
- Reestruturar de Array of Structures (1 sample por todos os filtros) para Structure of Arrays (4 samples por 1 filtro)
- Complexo: cada comb filter tem delay line própria com feedback
- Estimativa: 3-4 dias

**Opção B — Substituir por FDN Reverb (recomendado):**
- Feedback Delay Network é um algoritmo moderno, naturalmente vetorizável
- Melhor qualidade que FreeVerb (mais difuso, menos metálico)
- A matriz de feedback pode ser processada 4 delay lines por vez com NEON
- Estimativa: 3-5 dias (inclui tuning)

### Delay — Otimização direta

- Delay line + feedback é um loop simples de leitura/escrita com offset
- Vetorizável processando 4 samples por iteração com `vld1q_f32` / `vst1q_f32`
- Estimativa: ~2 horas

### Compressor — Ganho limitado

- Envelope follower é inerentemente sequencial (cada sample depende do anterior)
- Só a fase de aplicação do ganho (gain × sample) é vetorizável
- Estimativa: ~1 hora, ganho marginal

### Fases de implementação

| Fase | Escopo | Estimativa |
|---|---|---|
| **Fase 1** | Delay NEON (fácil, validação do pattern) | 1 dia |
| **Fase 2** | FreeVerb → FDN Reverb NEON (reescrita) | 3-5 dias |
| **Fase 3** | Compressor gain stage NEON | 1 dia |
| **Fase 4** | Benchmark A/B com APM HUD (antes/depois) | 1 dia |

**Total estimado: ~1-2 semanas**

### Resultado esperado

| Métrica | Antes | Depois (estimado) |
|---|---|---|
| AvgDspCh (6ch × 4 efeitos) | 630-760µs | **250-400µs** |
| MaxDspCh (pior caso) | 1000-3335µs | **500-1200µs** |
| % do budget | 24-28% | **10-15%** |

---

## Sequência recomendada

| Semana | Frente | Atividade |
|---|---|---|
| **1-2** | NEON DSP | Delay NEON → FDN Reverb → Compressor → benchmark |
| **3-4** | USB Driver | libusb setup → descriptor parsing → isochronous transfers |
| **5** | USB Driver | Integração com ring buffer → testes multi-device |
| **6** | Limpeza | Remover Superpowered inteiro → documentação → testes finais |

**Razão da ordem:** NEON DSP primeiro porque (a) é menos risco, (b) ganho imediato mensurável, (c) melhora a experiência com qualquer driver (Oboe ou USB custom), e (d) com DSP otimizado o driver USB terá menos pressão de timing.

---

## Passos imediatos (pra começar)

1. **Clonar libusb:**
   ```bash
   git clone https://github.com/libusb/libusb.git /Users/macbookpro/Downloads/Dev/libusb
   ```

2. **Coletar descriptors da PreSonus AudioBox** (comandos ADB acima)

3. **Baixar USB Audio Class spec:** [usb.org/audio](https://www.usb.org/document-library/audio-device-class-spec-10)

4. **Começar pela Frente 2 (NEON)** enquanto prepara o ambiente da Frente 1

---

## Referências

- [libusb GitHub](https://github.com/libusb/libusb) — LGPL-2.1
- [USB Audio Class 1.0 Specification](https://www.usb.org/document-library/audio-device-class-spec-10)
- [AOSP USB Audio HAL](https://android.googlesource.com/platform/hardware/libhardware/+/master/modules/usbaudio/) — referência
- [ARM NEON Intrinsics Reference](https://developer.arm.com/architectures/instruction-sets/intrinsics/)
- [FDN Reverb Paper (Jot & Chaigne)](https://ccrma.stanford.edu/~jos/pasp/Feedback_Delay_Networks.html) — algoritmo base pra novo reverb