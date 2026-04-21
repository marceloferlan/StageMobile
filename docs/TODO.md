# StageMobile — TODO (Backlog Técnico)

*Última atualização: 2026-04-21*

Itens pendentes organizados por prioridade. Marcar com `[x]` ao completar.

---

## Urgente (prazo externo)

- [ ] **Firebase Firestore Security Rules** — Regras de Test Mode expiram em ~2 dias. Colar regras com validação de estrutura no Firebase Console → Firestore → Rules. Regras prontas documentadas na conversa Claude (abril/2026).

- [ ] **Rotacionar chave Firebase Admin SDK** — Credencial `stagemobileapp-31ad1-firebase-adminsdk-*.json` foi exposta no histórico git (já removida com git-filter-repo). Ir em Firebase Console → Project Settings → Service Accounts → Generate New Private Key. Salvar fora do repo.

---

## Alta prioridade (performance / qualidade de áudio)

- [ ] **Validar novo Punch (Master Limiter)** — Redesenhado com saturação tanh + lookahead 64 samples + attack suavizado + auto makeup gain. Testar que som fica "gordo" sem clicks/artefatos.

- [ ] **Validar PAD sustain fix** — Tracking de estado do pedal (`sustainPedalState`) adicionado no ViewModel. Testar que vozes não ficam presas após soltar o pedal.

---

## Média prioridade (segurança / infraestrutura)

- [ ] **Firebase Anonymous Auth** — Adicionar `Firebase.auth.signInAnonymously()` no startup + regras `if request.auth != null`. Mais seguro que validação de dados apenas.

---

## Baixa prioridade (otimizações futuras)

- [ ] **Reduzir `synth.audio-channels` de 16 para 8** — FluidSynth itera todos os groups mesmo sem som. 8 grupos estéreo = 16 canais mono, suficiente, com ~40% menos overhead interno.

- [ ] **Pre-fill ring buffer antes de SF2 load** — Quando `nativeLoadSf2` segura `engine_mutex` por segundos, o ring buffer drena e causa underruns.

- [ ] **SIMD no LimiterEffect (Punch)** — O loop de saturação + envelope + gain é escalar. Pode ser vetorizado com NEON pra ~2× speedup.

- [ ] **Contenção de mutex durante SF2 load** — `nativeLoadSf2` segura `engine_mutex` durante todo o parsing. Para SF2 grandes (500MB+), isso causa underruns.

---

## Concluídos (referência)

- [x] **Seletor de Driver de Áudio** — "Android Nativo" vs "Otimizado (USB)" em Parâmetros Globais. Persiste em SharedPreferences. Superpowered só inicia se modo=1. Concluído abril/2026.
- [x] **Channel culling** — Canais silenciosos (peak<0.001 + 0 notas) são removidos do `activeChannelsMask`. Economia de ~700µs/render idle. Concluído abril/2026.
- [x] **SF2 unload fix (update=0 + programSelect reaffirm)** — `fluid_synth_sfunload` com update=0 + reafirmação de programSelect nos canais ativos. Concluído abril/2026.
- [x] **MIDI drain inline** — `midiProcessingLoop` removida, `drainMidiQueue()` dentro de `renderAudioEngine`. MutexMiss→0. Concluído abril/2026.
- [x] **SIMD NEON** — Channel mix + peak e Master interleave + peak vetorizados. Concluído abril/2026.
- [x] **Punch redesenhado** — LimiterEffect com tanh saturation + lookahead 64 samples + auto makeup gain. Concluído abril/2026.
- [x] **APM HUD reset button** — Botão "Zerar Contadores" no APM HUD. Concluído abril/2026.
- [x] **CC MIDI label fix** — "Filtros MIDI" → "CC MIDI Habilitados". Concluído abril/2026.
- [x] **Per-phase APM instrumentation** — `MEASURE_PHASE` macro + ATrace markers + FloatArray[14]. Concluído abril/2026.
- [x] **FluidSynth critical settings** — preload, cpu-cores=1, reverb/chorus disabled, overflow tuning com `setnum`. Concluído abril/2026.
- [x] **Runtime CPU detection + thread affinity** — sysfs `cpuinfo_max_freq` + pin ao big cluster + SCHED_FIFO fallback nice=-19. Concluído abril/2026.
- [x] **Stage Set load bug fix** — SF2 name normalization, sfId=-1 normalization, armed cache rebuild, load síncrono. Concluído abril/2026.
- [x] **Voice overflow params** — Corrigido `setint` → `setnum` para `synth.overflow.*`. Concluído abril/2026.
- [x] **Superpowered USB bridge** — Módulo Gradle isolado `:superpowered-usb` com c++_static, ponte C pura. Concluído abril/2026.
- [x] **Agent memory versionada** — Movida para `docs/.agent-memory/` com symlink. Concluído abril/2026.
