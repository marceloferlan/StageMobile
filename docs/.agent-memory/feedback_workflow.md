---
name: Workflow de colaboração — apresentar análise antes de aplicar
description: Usuário quer ver análise + plano antes de qualquer edição em problemas não-triviais
type: feedback
---

Antes de aplicar qualquer correção ou melhoria em código (especialmente no motor de áudio ou em componentes críticos), apresente primeiro:
1. **Análise do problema** — o que os dados/código mostram, causa raiz identificada
2. **Plano de correção** — quais arquivos, quais mudanças, qual o impacto esperado
3. **Aguardar aprovação explícita** antes de editar

**Why:** O usuário quer avaliar, comentar e possivelmente sugerir alternativas. Já houve casos em que minha hipótese inicial estava parcialmente errada (exemplo: hipótese de "core 6 é little core" era verdade só em parte — era migração de scheduler, não hardcode). Apresentar análise primeiro permite iteração antes de desperdiçar rebuild+teste.

**How to apply:**
- Problemas de performance (clicks, latência, underruns) — SEMPRE análise primeiro
- Fixes de bug em código crítico (audio thread, MixerViewModel, JNI) — análise primeiro
- Mudanças de arquitetura — análise primeiro
- Edições triviais e seguras (1-2 linhas, gitignore, formatação) — pode aplicar direto
- Quando o usuário explicitamente diz "aplique" / "pode prosseguir" / "pode dar andamento", daí sim aplica

**Exceções:** Se o usuário faz uma pergunta exploratória ("o que pode ser X?"), responder com análise é suficiente — não precisa de aprovação porque nada vai ser mudado ainda.

Confirmado explicitamente pelo usuário na sessão de abril/2026 durante investigação de clicks de áudio no Tab S9 FE.
