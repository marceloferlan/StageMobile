---
name: Consultar docs/ antes de pesquisar/investigar
description: Antes de fase de research para bugs ou soluções, consultar docs/INDEX.md e abrir só os docs relevantes. Não ler docs/ inteiro — ser seletivo.
type: feedback
---

Antes de iniciar a fase de pesquisa para um bug, feature, ou solução arquitetural, **consultar primeiro as especificações do projeto em `docs/`**. A pasta tem bastante conteúdo — ser otimizado e seletivo, não exaustivo.

**Why:** O projeto StageMobile já acumula doc detalhada em `docs/` (arquitetura, threading, DSP, APM, diagnóstico). Muita coisa que eu precisaria investigar do zero já está documentada. Ignorar isso = desperdício de tempo pesquisando o que já foi decidido/resolvido.

**How to apply:**

1. **Ponto de entrada único**: sempre começar lendo `docs/INDEX.md`. Ele é curado e categorizado (Arquitetura, Performance, Funcionalidades, Operacional). É pequeno e já dá o mapa mental.

2. **Ler APENAS os docs relevantes ao tópico em questão**. Não ler a pasta inteira. Exemplos:
   - Bug de click/estalo de áudio → `audio_performance_tuning.md` (tem causas raiz conhecidas, tabela de diagnóstico por fase) + `architecture.md` seções 3.x
   - Bug no Stage Set load → `audio_performance_tuning.md` seção 5 (tem o padrão de SF2 name normalization) + `developer_guide.md` seção 5
   - Adicionar novo efeito DSP → `developer_guide.md` seção 2 + `architecture.md` seção 4
   - Performance de CPU → `diagnostico_cpu.md`
   - Integração Superpowered → `superpowered_isolation.md`

3. **Grep dentro de docs/** quando o tópico não for óbvio pelo INDEX. Ex: `Grep pattern="overflow" path="docs/"` pra achar referências cruzadas rapidamente.

4. **Preferir doc sobre código quando a doc cobrir**. Se `audio_performance_tuning.md` diz "samples são preloaded via `synth.dynamic-sample-loading=0`", não precisa ir ao `fluidsynth_bridge.cpp` confirmar antes de responder.

5. **Exceção — edições triviais ou totalmente novas**: se a task é trivial (formatação, gitignore, renomeação) ou é feature totalmente nova sem precedente documentado, pular essa etapa está OK.

6. **Complementar com memórias existentes**: antes de qualquer investigação, o MEMORY.md já foi auto-carregado e aponta pras memórias `project_*`/`feedback_*`. Essas são o primeiro filtro — veja se a resposta já está ali.

**Sequência otimizada de investigação:**
```
1. MEMORY.md (já em contexto)
2. Memórias relevantes (project_audio_tuning_state, etc)
3. docs/INDEX.md (mapa dos docs)
4. Docs específicos do tópico (1-3 arquivos no máximo)
5. Grep em docs/ se precisar de texto específico
6. Só então ir ao código-fonte
```

**Sinal de que eu não segui esta regra**: se eu começo a pesquisa lendo arquivos .kt/.cpp diretamente sem antes consultar INDEX.md ou memórias, está errado — parar e recomeçar pelo ponto 1.

Confirmado explicitamente pelo usuário durante a consolidação do sistema de memórias (abril/2026).
