---
name: Estado da investigação de clicks de áudio
description: Contexto, fixes aplicados e testes pendentes da sessão de tuning do motor nativo (abril/2026)
type: project
---

Investigação de clicks/estalos no Tab S9 FE (Exynos 1380) e, em menor grau, S24 Ultra (Snapdragon 8 Gen 3), usando SF2 de piano grandes (~500MB cada).

## Causas raiz identificadas

1. **Disk I/O em hot path** — FluidSynth com `dynamic-sample-loading=1` (default) causava page faults durante render (spikes de 1-9ms no `MaxFluid`). Resolvido com preload (`synth.dynamic-sample-loading=0`).

2. **Thread migration entre big/little cores** — scheduler Android migrava `synthRenderLoop` entre A78 e A55. Resolvido com `sched_setaffinity` para o big cluster (detecção runtime via sysfs `cpuinfo_max_freq`).

3. **Prioridade default** — thread de render tinha `SCHED_OTHER` nice 0. Android nega `SCHED_FIFO` (`EPERM`), fallback automático para `nice=-19` funciona bem.

4. **FluidSynth overflow settings ignorados** — inicialmente usei `fluid_settings_setint` mas os params `synth.overflow.*` são NUMERIC (double). `fluid_settings_setnum` é o correto. Antes do fix o voice stealing usava defaults.

5. **Bug no Stage Set load** — `loadSetStage` fazia lookup no cache/filesystem usando `ch.soundFont` direto (que inclui suffix `[Preset]`), sempre dando miss. Resolvido com `sf2BaseName = sf2FullName.substringBefore(" [")`.

6. **DSP interno do FluidSynth rodando em paralelo com DSPChain externo** — `synth.reverb.active` e `synth.chorus.active` precisam ser 0 **nos settings** antes de `new_fluid_synth`, não só depois via `fluid_synth_set_reverb_on(0)`.

## Fixes aplicados (ordem)

1. Per-phase APM instrumentation (`MEASURE_PHASE` macro + ATrace markers + FloatArray[14])
2. FluidSynth critical settings (preload, cpu-cores=1, reverb/chorus disabled, overflow tuning)
3. Thread affinity para core 6 (hardcoded) — **DEGRADOU o baseline** (provavelmente migrava pra little core antes e a gente não sabia, ou o kernel tinha liberdade que core 6 não tem)
4. Runtime CPU detection via sysfs — pinna ao conjunto de cores com freq máxima (funciona no Exynos 1380 → 4 cores, no SD8G3 → prime core)
5. Stage Set load fix (substringBefore + normalização de sfId + rebuild do armed cache)
6. Voice overflow params corrigidos de `setint` para `setnum`

## Testes pendentes (esperando próximo CSV do APM HUD)

- [ ] Baseline pós runtime-detect volta a ~110µs AvgFluid @ 64 voices (antes do hardcode core 6 que degradou para 515µs)
- [ ] Logcat confirma `synthRenderLoop: pinned to 4 fastest core(s) [4,5,6,7]` no Tab S9 FE
- [ ] Logcat confirma `SCHED_FIFO denied (errno=1), using nice=-19 fallback`
- [ ] Logcat NÃO mostra mais `Unknown integer parameter 'synth.overflow.*'`
- [ ] Stage Set load toca imediatamente sem precisar limpar+recarregar SF2
- [ ] Disaster window no switch piano 1 → piano 2 continua eliminada

## Estado do usuário

Estava prestes a reiniciar o Antigravity IDE quando solicitou salvar contexto. A sessão havia terminado com:
- Firebase MCP sendo configurado (erro `env: node: No such file or directory` — Claude Code não herda PATH do nvm)
- MCP config em `.mcp.json` project scope precisando de path absoluto para `npx` do nvm
- Node em `/Users/macbookpro/.nvm/versions/node/v20.19.4/bin/`

## Armadilhas conhecidas

- **Parâmetros FluidSynth**: verificar sempre se são INT ou NUMERIC na documentação. `overflow.*` são numeric. `reverb.active`, `chorus.active`, `cpu-cores`, `polyphony` são int. Erro silencioso: FluidSynth loga "Unknown integer parameter" mas não falha.
- **CPU numbering**: Nunca assumir que core 4+ são big. Sempre usar runtime detection via sysfs.
- **SF2 name normalization**: ver `feedback_workflow.md` e `developer_guide.md` seção 5.
- **Logs em hot path**: não adicionar `LOGW` no `renderAudioEngine` nem em qualquer thread de áudio realtime — causa priority inversion com os locks internos do logging.

## Arquivos modificados nesta investigação

- `app/src/main/cpp/fluidsynth_bridge.cpp` (grande parte)
- `app/src/main/java/com/marceloferlan/stagemobile/domain/model/AudioStats.kt` (+8 campos)
- `app/src/main/java/com/marceloferlan/stagemobile/viewmodel/MixerViewModel.kt` (parsing + `loadSetStage` rewrite)
- `app/src/main/java/com/marceloferlan/stagemobile/ui/components/APMHudDialog.kt` (PhaseRow + CSV export)
- `docs/audio_performance_tuning.md` (novo)
- `docs/INDEX.md` (novo)
- `docs/architecture.md` (seções 3.2 e 3.3 adicionadas)
- `docs/developer_guide.md` (regra de sf2 name + FluidSynth types)
