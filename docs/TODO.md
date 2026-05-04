# StageMobile — TODO (Backlog Técnico)

*Última atualização: 2026-05-03*

Itens pendentes organizados por prioridade. Marcar com `[x]` ao completar.

---

## Urgente (prazo externo)

- [x] **Firebase Firestore Security Rules** — Regras atualizadas com `request.auth != null`. Publicadas em abril/2026.

- [ ] **Rotacionar chave Firebase Admin SDK** — Credencial `stagemobileapp-31ad1-firebase-adminsdk-*.json` foi exposta no histórico git (já removida com git-filter-repo). Ir em Firebase Console → Project Settings → Service Accounts → Generate New Private Key. Salvar fora do repo.

---

## Alta prioridade (auth / segurança)

- [x] **Firebase Auth (primeira instalação)** — Login com E-mail/Senha e Google Sign-In implementado em abril/2026. `AuthRepository.kt` + `LoginScreen.kt` + roteamento em `MainActivity.kt`.

---

## Alta prioridade (performance / qualidade de áudio)

- [x] **Backup de Configurações** — Salvar/restaurar settings, Set Stages, MIDI Learn e metadados SF2 via Firestore. Funcional desde maio/2026. Firestore network toggle (`enableNetwork`/`disableNetwork`) para contornar o `disableNetwork()` do SoundFontRepository.

- [x] **Backup Completo (SF2)** — Upload/download de SF2 via Cloudflare R2 com multipart upload (partes de 50MB) para arquivos >90MB. Worker deployado em `stagemobile-backup-worker.ferlan.workers.dev`. Funcional desde maio/2026.

- [ ] **Validar novo Punch (Master Limiter)** — Redesenhado com saturação tanh + lookahead 64 samples + attack suavizado + auto makeup gain. Testar que som fica "gordo" sem clicks/artefatos.

- [ ] **Validar PAD sustain fix** — Tracking de estado do pedal (`sustainPedalState`) adicionado no ViewModel. Testar que vozes não ficam presas após soltar o pedal.

- [ ] **Restaurar MIN_INTERVAL_MS para backup** — Atualmente `0L` para testes. Restaurar para `3600_000L` (1 hora) antes do release.

---

## Média prioridade (segurança / infraestrutura)

- [x] **Firebase Anonymous Auth** — Substituído por autenticação real (E-mail/Senha + Google). Concluído abril/2026.

---

## Alta prioridade (nova branch `Stage-Mobile-DSP-USBDriver`)

- [ ] **Driver USB próprio com libusb** — Substituir Superpowered por driver open-source (LGPL-2.1). Transferências isócronas diretas via libusb, sem depender do Android HAL. Plano detalhado em `docs/plan_usb_driver_and_neon_dsp.md`.

- [ ] **NEON DSP: Delay** — Vetorizar delay line + feedback com NEON intrinsics (4 samples/iteração).

- [ ] **NEON DSP: Reverb (FDN)** — Substituir STK FreeVerb por Feedback Delay Network vetorizado. Melhor qualidade + ~2-3× mais rápido.

- [ ] **NEON DSP: Compressor gain stage** — Vetorizar a fase de aplicação de ganho do compressor.

- [ ] **Remover módulo `:superpowered-usb`** — Após driver USB próprio funcional, remover Superpowered inteiro (lib, bridge, módulo Gradle).

---

## Baixa prioridade (otimizações futuras)

- [ ] **Add-on: Seletor de Driver de Áudio (IAP)** — Tornar o driver USB premium exclusivo via Google Play In-App Purchase. Ver `docs/features.md` seção "Add-ons e Monetização".

- [ ] **Reduzir `synth.audio-channels` de 16 para 8** — FluidSynth itera todos os groups mesmo sem som. ~40% menos overhead interno.

- [ ] **Pre-fill ring buffer antes de SF2 load** — Temporariamente subir o target fill pra capacidade máxima antes do load.

- [ ] **SIMD no LimiterEffect (Punch)** — Vetorizar saturação + envelope + gain com NEON.

- [ ] **Contenção de mutex durante SF2 load** — Avaliar carga em 2 fases ou double-buffer com swap atômico.

- [x] ~~**Benchmark DSP: STK vs Superpowered**~~ — Cancelado. Superpowered descartado por custo de licença. Substituído pelo plano de NEON próprio.

---

## Concluídos (referência)

- [x] **Delay SYNC (subdivisão rítmica)** — Seletor de subdivisão (1/1 a 1/16T) no card do Delay no rack DSP. BPM via TAP global, delay_ms = (60000/BPM) × multiplicador. Grid 2×5 chips. Concluído maio/2026.
- [x] **TAP BPM display na toolbar** — BPM médio dos toques exibido ao lado do botão TAP (bolinha). Concluído maio/2026.
- [x] **Limite de 8 canais** — Máximo de 8 instrument channels na mixer. Botão "+" desabilitado ao atingir limite. Concluído maio/2026.
- [x] **Reorganização do Drawer** — Removido "Downloads". "Configurações" movido para top bar. "Mostrar Teclado" movido para toolbar (botão piano). "Suporte/Feedback" como último item. Concluído maio/2026.
- [x] **Ícones SF2 Maintenance** — Emoji 📁 substituído por `Icons.Default.FolderOpen`, `Icons.Default.Add` por `Icons.Default.NoteAdd`. Concluído maio/2026.
- [x] **FilterChip categorias padronizados** — Largura mínima 72dp, texto centralizado. "Drums/Percussion" → "Drums". Concluído maio/2026.
- [x] **BackupScreen info tabular** — Layout de info do backup em colunas (tablet: 1 row, phone: 2 rows de 2). Mensagem dinâmica backup vs restauração. Concluído maio/2026.
- [x] **Backup Firestore offline fix** — Root cause: `SoundFontRepository.disableNetwork()` bloqueava todos os repositórios. Fix: `withFirestoreNetwork()` habilita rede temporariamente. Bug `Task<Void>.await()` retornando null corrigido. Toast crash no Dispatchers.IO corrigido. Concluído maio/2026.
- [x] **Multipart upload R2** — SF2 >90MB enviados em partes de 50MB via endpoints `/multipart/*` no Cloudflare Worker. Concluído maio/2026.
- [x] **Indicadores DSP nos channel strips** — Quadrinhos (HP, LP, CP, EQ, CH, TR, DL, RV, LM) à direita do peak meter. Concluído abril/2026.
- [x] **SF2 import dialog fix** — Dialog redimensionado + verificação de duplicata via Firestore. Concluído abril/2026.
- [x] **isTablet fix global** — `smallestScreenWidthDp >= 600`. Concluído abril/2026.
- [x] **TopBar compacta phone** — Botões 26dp, paddings reduzidos. Concluído abril/2026.
- [x] **LoginScreen layout horizontal phone** — Row layout para landscape. Concluído abril/2026.
- [x] **Seletor de Driver de Áudio** — "Android Nativo" vs "Otimizado (USB)". Concluído abril/2026.
- [x] **Channel culling** — Canais silenciosos removidos do mix. Concluído abril/2026.
- [x] **SF2 unload fix** — `update=0` + programSelect reaffirm. Concluído abril/2026.
- [x] **MIDI drain inline** — MutexMiss→0. Concluído abril/2026.
- [x] **SIMD NEON** — Channel mix + peak vetorizados. Concluído abril/2026.
- [x] **Punch redesenhado** — tanh saturation + lookahead. Concluído abril/2026.
- [x] **APM HUD reset button** — Concluído abril/2026.
- [x] **Per-phase APM instrumentation** — Concluído abril/2026.
- [x] **FluidSynth critical settings** — Concluído abril/2026.
- [x] **Runtime CPU detection + thread affinity** — Concluído abril/2026.
- [x] **Stage Set load bug fix** — Concluído abril/2026.
