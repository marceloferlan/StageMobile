# Audio Performance Tuning — Motor Nativo

Este documento registra as otimizações aplicadas ao motor de áudio nativo para eliminar clicks, estalos e micro-interrupções durante a execução de instrumentos SF2, especialmente em SoCs Exynos (Samsung Galaxy Tab S9 FE).

Estado atual: branch `feature/Stage-Mobile-DSP-Superpowered`.

---

## 1. Problema original

Clicks e estalos audíveis durante playback de SF2 grandes (500MB+), mais severos no Tab S9 FE (Exynos 1380) do que no S24 Ultra (Snapdragon 8 Gen 3). O APM HUD inicialmente reportava apenas o tempo total de render, sem visibilidade de qual fase do pipeline estava causando os spikes.

**Causas raiz identificadas via instrumentação per-phase:**
1. Carregamento lazy de samples SF2 causando page faults no hot path
2. Thread de render migrando entre big/little cores do scheduler
3. Prioridade padrão (SCHED_OTHER) permitindo preempção
4. Configurações do FluidSynth sem otimizações mobile
5. Bug no load de Stage Set: nome de exibição vs nome base do SF2 no cache lookup

---

## 2. Instrumentação Per-Phase (APM HUD)

### 2.1 Métricas disponíveis

O `AudioStats` agora retorna **14 floats** do nativo (antes: 6):

| Índice | Métrica | Significado |
|---|---|---|
| 0 | underruns | Callbacks do Superpowered/Oboe sem frames suficientes no ring buffer |
| 1 | mutexMisses | `try_to_lock` falhou no `engine_mutex` (indica contenção entre threads) |
| 2 | clips | Peak > 1.0 no master |
| 3 | avgCallbackUs | Tempo médio do `renderAudioEngine` completo |
| 4 | maxCallbackUs | Pico do `renderAudioEngine` completo |
| 5 | activeVoices | Vozes ativas no FluidSynth (`fluid_synth_get_active_voice_count`) |
| 6-7 | avgPhaseFluidUs / maxPhaseFluidUs | Tempo do `fluid_synth_nwrite_float` |
| 8-9 | avgPhaseDspChanUs / maxPhaseDspChanUs | Loop de 16 canais DSP |
| 10-11 | avgPhaseDspMasterUs / maxPhaseDspMasterUs | Master rack DSP |
| 12-13 | avgPhaseMixUs / maxPhaseMixUs | Mix/interleave/clip detect |

### 2.2 Orçamento de tempo

Budget por chunk @ 48kHz/128 frames = **2666µs**. Se qualquer fase individual chegar perto disso (>2000µs), é um sinal de problema.

### 2.3 Macro C++ `MEASURE_PHASE`

Definida em `fluidsynth_bridge.cpp` próximo aos contadores APM. Wrappa uma expressão com:
- `clock_gettime` antes/depois
- `ATrace_beginSection/endSection` (para traces Perfetto)
- CAS loop para atualizar o máximo atomic

Uso:
```cpp
MEASURE_PHASE(apmPhaseFluidNs, apmMaxPhaseFluidNs, "SM.Fluid", {
    fluid_synth_nwrite_float(synth, numFrames, spLeftPtrs, spRightPtrs, nullptr, nullptr);
});
```

### 2.4 Captura de trace Perfetto

Os marcadores `SM.Fluid`, `SM.DspChan`, `SM.DspMaster`, `SM.Mix`, `SM.RenderTotal` ficam visíveis em traces Perfetto quando capturados com `atrace_apps: "com.marceloferlan.stagemobile"`.

Comando de captura (Bash):
```bash
adb shell "perfetto -o /data/misc/perfetto-traces/stage.pftrace -t 20s \
  sched freq idle power audio atrace_apps:com.marceloferlan.stagemobile"
adb pull /data/misc/perfetto-traces/stage.pftrace ~/Desktop/
```

Abrir em [ui.perfetto.dev](https://ui.perfetto.dev) e procurar pela thread `synthRenderLoop`.

---

## 3. Thread Affinity + Realtime Priority

### 3.1 Runtime CPU detection

O `synthRenderLoop` detecta em runtime quais cores são as mais rápidas (big cluster) lendo `/sys/devices/system/cpu/cpuN/cpufreq/cpuinfo_max_freq`. Todas as cores com a frequência máxima são incluídas no `cpu_set_t` e a thread é pinned a esse conjunto.

Exemplo de log no startup:
```
synthRenderLoop: pinned to 4 fastest core(s) [4,5,6,7] @ 2400000 kHz (of 8 total)
```

No Exynos 1380 (Tab S9 FE): 4× Cortex-A78 @ 2.4GHz (big) nas posições 4-7, 4× Cortex-A55 @ 2.0GHz (little) nas 0-3.

No Snapdragon 8 Gen 3 (S24 Ultra): geralmente 1× Cortex-X4 @ 3.3GHz (prime) nas posições 7-8, outros cores em velocidades menores. O detector vai pinar apenas no prime core.

### 3.2 Realtime scheduling

Tenta `SCHED_FIFO` primeiro (prioridade realtime do Linux). Android normalmente nega (`errno=1, EPERM`) para apps de usuário, então há fallback automático para `nice=-19` (máxima prioridade normal).

```
synthRenderLoop: SCHED_FIFO denied (errno=1), using nice=-19 fallback
```

O fallback `nice=-19` ainda é bem efetivo — é muito acima da prioridade default (`nice=0`) e garante que a thread de render ganha preferência sobre threads normais do app e do sistema.

### 3.3 Por que isso importa

Sem pinning, o scheduler do Android (especialmente o HMP do Exynos) pode migrar a thread de render entre big e little cores em resposta a variações de carga. Cada migração causa:
- Cache misses (L1/L2 flush)
- Reinício do DVFS governor
- Possível downclock se cair em little core

Resultado: spikes intermitentes de tempo de render sem causa algorítmica. Com pinning ao big cluster, o kernel pode migrar dentro do cluster (A78→A78) sem custo significativo, mas nunca migra pra A55.

---

## 4. Configurações Críticas do FluidSynth

Todas aplicadas em `nativeInit` **antes** de `new_fluid_synth(settings)`:

```cpp
// ====== CRITICAL SETTINGS FOR MOBILE LOW-LATENCY ======

// Preload de samples — evita page faults/disk I/O durante render
fluid_settings_setint(settings, "synth.dynamic-sample-loading", 0);

// Single-threaded: evita wakeup latency de worker threads internas
fluid_settings_setint(settings, "synth.cpu-cores", 1);

// Desabilitar reverb/chorus interno ANTES da criação do synth
fluid_settings_setint(settings, "synth.reverb.active", 0);
fluid_settings_setint(settings, "synth.chorus.active", 0);

// Hard-kill chorus: zera parâmetros residuais
fluid_settings_setnum(settings, "synth.chorus.depth", 0.0);
fluid_settings_setnum(settings, "synth.chorus.level", 0.0);
fluid_settings_setint(settings, "synth.chorus.nr", 0);

// Voice overflow scoring — voz com MENOR score é roubada primeiro
// IMPORTANTE: overflow.* são NUMERIC (double) no FluidSynth, NÃO int.
// Usar fluid_settings_setint resulta em "Unknown integer parameter" e é ignorado.
fluid_settings_setnum(settings, "synth.overflow.percussion", 4000.0); // protege drums
fluid_settings_setnum(settings, "synth.overflow.released", -2000.0);  // rouba released primeiro (default FluidSynth)
fluid_settings_setnum(settings, "synth.overflow.sustained", -1000.0); // depois sustained
fluid_settings_setnum(settings, "synth.overflow.age", 1000.0);
fluid_settings_setnum(settings, "synth.overflow.volume", 500.0);
```

### 4.1 Por que cada uma importa

**`dynamic-sample-loading=0`** — Crítico para SF2 grandes (>100MB). Por padrão FluidSynth faz mmap do arquivo SF2 e carrega samples sob demanda; a primeira vez que uma nota toca um sample novo, acontece um page fault que força o kernel a ler do storage (1-10ms de latência). Com `0`, todos os samples são copiados pra RAM no load do SF2. Custo: ~500MB de RAM pra SF2 de 500MB. Benefício: zero disk I/O no hot path.

**`cpu-cores=1`** — FluidSynth por padrão cria worker threads internas para paralelizar a síntese. No Android, essas threads acordam via `pthread_cond_signal` de dentro do callback de áudio, causando spikes de 1-5ms de wakeup latency. Single-threaded é mais previsível.

**`reverb/chorus.active=0` nos settings** — Fazer via `fluid_synth_set_reverb_on(0)` APÓS a criação do synth é menos eficaz — os DSPs internos já foram inicializados. Settar via settings ANTES da criação evita a alocação inteira.

**Voice overflow** — Quando a polifonia satura (ex: polyphony=80, usuário toca acorde de 10 notas enquanto 80 vozes estão ativas), o FluidSynth precisa roubar vozes para alocar as novas. O scoring determina qual voz morre. Os valores acima são os defaults recomendados do FluidSynth para performance musical — protegem drums e vozes altas/sustentadas, preferem matar vozes em release (que já estão fading).

---

## 5. Bug fix: Stage Set load

### 5.1 Sintoma

Ao carregar um Stage Set, os canais exibiam o nome correto do SF2 mas **não tocavam som**. O usuário precisava limpar o canal e recarregar o SF2 manualmente pra ouvir o som.

### 5.2 Causa raiz

O campo `soundFont` do `InstrumentChannel` guarda o **nome de exibição** que inclui o preset selecionado entre colchetes (ex: `"piano1.sf2 [Grand Piano]"`). Isso é usado pela UI para mostrar qual preset está ativo.

Porém:
- `loadedSf2Cache` é chaveado pelo **nome base** (ex: `"piano1.sf2"`) — ver `loadSoundFontFromInternal:719`
- `SoundFontRepository.getFilePath(name)` também espera o nome base

No antigo `loadSetStage`, o lookup usava `ch.soundFont` direto (com colchetes):
```kotlin
val currentSfId = loadedSf2Cache[sf2Name]                // sempre miss
val internalPath = soundFontRepo?.getFilePath(sf2Name)   // arquivo não existe
```

Resultado: cache miss → fallback para arquivo → `internalFile.exists()==false` → fallback para URI → também vazio → **nada acontece silenciosamente**.

### 5.3 Fix aplicado

Em `MixerViewModel.loadSetStage`:

```kotlin
val sf2FullName = ch.soundFont   // "piano1.sf2 [Grand Piano]"
val sf2BaseName = sf2FullName.substringBefore(" [")   // "piano1.sf2"

val cachedSfId = loadedSf2Cache[sf2BaseName]
// ... usa sf2BaseName em todos os lookups ...
```

Padrão já usado em `loadSoundFontFromInternal:940`, `performSoundFontRemoval:1975`, e `reinitAudioEngine:2220`. Foi esquecido apenas no `loadSetStage`.

### 5.4 Correções adicionais no mesmo fluxo

- **Normalização de `sfId=-1` ao aplicar o stage**: antes, `_channels.value = stage.channels` preservava o `sfId` salvo (stale entre sessões, porque o FluidSynth gera novos sfIds a cada init). Agora usa `updateChannels(stage.channels.map { it.copy(sfId = -1) })` para garantir que sfIds inválidos não confundem o `armedChannelsCache`.

- **`updateChannels` em vez de `_channels.value = ...` direto**: o helper chama `rebuildArmedChannelsCache()`, garantindo consistência imediata do cache.

- **Load síncrono em vez de fire-and-forget**: antes, para cache miss, o código fazia `scope.launch(Dispatchers.IO) { load... }` dentro do forEach, sem aguardar. Agora carrega sincronamente no mesmo coroutine Default, garantindo que o `rebuildArmedChannelsCache` final rode com todos os sfIds corretos.

---

## 6. Fluxo de diagnóstico (quando aparecem clicks)

### 6.1 Ativar o APM HUD
Menu de desenvolvedor → APM HUD visível.

### 6.2 Reproduzir o cenário
Tocar o padrão que causa o problema por 15-60 segundos.

### 6.3 Exportar CSV
Botão "Exportar CSV (Clipboard)" no HUD. O CSV tem header:
```
Timestamp,CPU,AvgCb,MaxCb,Underruns,MutexMiss,Clips,Voices,AvgFluid,MaxFluid,AvgDspCh,MaxDspCh,AvgMaster,MaxMaster,AvgMix,MaxMix
```

### 6.4 Análise do CSV

**Spikes concentrados em `MaxFluid`** → problema na síntese do FluidSynth. Causas típicas:
- Disk I/O (page faults) → confirmar que `synth.dynamic-sample-loading=0`
- Voice allocation → verificar voice overflow settings
- Samples grandes descompactando → geralmente resolvido com preload

**Spikes em `MaxDspCh`** → algum efeito DSP por canal explodindo. Procurar por:
- Compressor com attack muito curto
- Reverb com tail longo
- Filtros com ressonância alta

**Spikes em `MaxMaster`** → master rack DSP. Raro; geralmente EQ/limiter.

**Spikes em `MaxMix`** → loop scalar de interleave/peak. Só deveria acontecer com preempção externa (scheduler, DVFS).

**Spikes distribuídos em TODAS as fases simultaneamente** → preempção externa. Sinal de:
- Scheduler migrando a thread (verificar que pinning funcionou no logcat)
- DVFS downclock (CPU governor)
- Samsung GOS / Game Booster interferindo
- Outra thread pesada do app (Compose, Firebase) roubando CPU

**`MutexMiss` aumentando durante playback** → contenção no `engine_mutex` entre `synthRenderLoop` e `midiProcessingLoop`. Normal em rajadas de MIDI; problemático se constante.

**`Underruns` aumentando sem nada mais mudando** → ring buffer drenando mais rápido do que `synthRenderLoop` consegue alimentar. Verificar se há operação de SF2 load em progresso (bloqueia `engine_mutex` por segundos).

### 6.5 Capturar logcat paralelo

```bash
adb logcat -s StageAudioEngine:* FluidSynthEngine:* MixerViewModel:* SuperpoweredUSB:*
```

Procurar por:
- `Mutex miss` — contenção detectada
- `Containment [Stage 1/3]` — CPU > 65% ou buffer crítico, sistema reduziu qualidade
- `synthRenderLoop: pinned to ...` — confirma affinity aplicada

---

## 7. Arquivos relevantes

| Arquivo | Conteúdo |
|---|---|
| `app/src/main/cpp/fluidsynth_bridge.cpp` | `nativeInit` com settings FluidSynth, `synthRenderLoop` com affinity/priority, `renderAudioEngine` com MEASURE_PHASE, `nativeGetAudioStats` retornando 14 floats |
| `app/src/main/java/.../domain/model/AudioStats.kt` | Data class com os 14 campos (avg + max por fase) |
| `app/src/main/java/.../viewmodel/MixerViewModel.kt` | Parsing do FloatArray[14] em `startResourceMonitor`, `loadSetStage` com fix do nome base |
| `app/src/main/java/.../ui/components/APMHudDialog.kt` | UI do HUD com seção "Breakdown por Fase" e exporter CSV |

---

## 8. Testes pendentes (a validar com novas medições)

- [ ] Stage Set load tocando imediatamente após o fix
- [ ] Baseline pós-affinity (AvgFluid volta pra ~110µs @ 64 voices?)
- [ ] Spikes residuais reduzidos após runtime core detection
- [ ] Disaster window no switch piano 1 → piano 2 eliminada
- [ ] Underruns permanecem em 0 durante playback prolongado
