# Plano: Avaliação de Performance DSP — Dados e Próximos Passos

## Análise dos dados (6 instrumentos × 4 efeitos: Compressor + EQ + Delay + Reverb)

### Comparativo SEM vs COM efeitos

| Métrica | SEM efeitos (CSV anterior) | COM efeitos (este CSV) | Delta |
|---|---|---|---|
| **AvgDspCh** | 30-94µs | **630-760µs** | **+600-670µs (+700%)** |
| **MaxDspCh** | ~200µs | **1000-1600µs** (picos até **3335µs**) | Excede budget em picos |
| **AvgCb total** | 200-350µs | **900-1070µs** | 2.5-3× mais lento |
| **% do budget (2666µs)** | 8-13% | **34-40%** | DSP consome 1/3 do budget |
| **Underruns** | 0 | **0** | Ring buffer absorveu |
| **MutexMiss** | 0 | 3 (estáveis) | Sem impacto |

### Breakdown estimado por efeito (24 instâncias = 4 efeitos × 6 canais)

Custo médio por instância: ~670µs / 24 = **~28µs por efeito por canal**.

| Efeito | Custo estimado/canal | Razão | Complexidade NEON |
|---|---|---|---|
| **Reverb (FreeVerb)** | ~12-15µs (40-50%) | 8 comb + 4 allpass filters, 100% escalar | Alta — precisa reestruturar data layout |
| **Delay** | ~7-8µs (25%) | Delay line + feedback loop, escalar | Média — vetorizável com buffer alignment |
| **Compressor** | ~5-6µs (18%) | Envelope + gain per-sample | Baixa — envelope é sequencial por natureza |
| **EQ** | ~3-4µs (12%) | Butterworth IIR (DSPFilters lib) | Baixa — IIR é inerentemente sequencial |

### O problema real: **MaxDspCh acima do budget**

Os picos de **2700-3335µs** no MaxDspCh excedem os 2666µs do budget. Somando FluidSynth (~220µs), o total passa de 3000µs. Mesmo com ring buffer amortecendo, esses picos são **candidatos a causar artefatos audíveis** em dispositivos mais lentos.

### Veredicto

**AvgDspCh de 670µs (25% do budget) é aceitável mas não confortável.** Não é urgente, mas:
- Pouco headroom pra adicionar MAIS efeitos (ex: segundo reverb, phaser, auto-wah)
- Picos de MaxDspCh acima do budget são risco real
- Em Exynos com DVFS agressivo, 25% pode virar 40% temporariamente

**VALE A PENA OTIMIZAR.** A questão é o caminho.

---

## Dois caminhos possíveis

### Caminho A — NEON-otimizar nossos próprios efeitos (STK/Custom)

**Prós:**
- Zero dependência extra de licença (STK é open-source)
- Sem problema de ABI bridge (tudo dentro de `libsynthmodule.so`)
- Controle total sobre o algoritmo
- Não aumenta custo de licença Superpowered

**Contras:**
- Alto esforço de engenharia: NEON-otimizar FreeVerb requer reestruturar o data layout inteiro (SoA vs AoS) para processar 4 samples em paralelo pelos 12 filtros
- STK FreeVerb é código acadêmico com muitas dependências internas — difícil de vetorizar sem reescrever
- Delay e Compressor são mais fáceis, mas EQ/IIR é inerentemente sequencial (cada sample depende do anterior)

**Ganho estimado:** 2-3× no Reverb e Delay (se bem feito). Compressor e EQ pouco ganho com SIMD.

### Caminho B — Benchmark com Superpowered Effects via C Bridge

**Prós:**
- Efeitos já são NEON-optimized (trabalho feito pelo Superpowered)
- SDK já está integrado no projeto (módulo `:superpowered-usb` com c++_static)
- Bridge C puro já existe e funciona (sp_bridge.h)
- Resultado imediato sem esforço de otimização manual
- Benchmark rápido: teste de 1 efeito (Reverb) pra validar antes de migrar os demais

**Contras:**
- Aumenta dependência na licença Superpowered (mais features usadas = mais argumento pra preço mais alto)
- Cada efeito precisa de wrapper no C bridge (create/destroy/process/setParam)
- Efeitos Superpowered têm API própria (parâmetros diferentes dos nossos) — precisa de mapeamento
- Não temos `ThreeBandEQ` equivalente exato ao nosso ParametricEQ (3 bandas vs paramétrico)

**Ganho estimado:** 2-4× no Reverb, 2-3× em Delay e Compressor. Possível ganho menor em EQ.

---

## Recomendação: Caminho B (benchmark Superpowered) primeiro

**Razão:** é mais rápido obter dados reais do que otimizar manualmente. Um benchmark do Reverb (o efeito mais caro) nos dá a resposta definitiva em ~2 horas de implementação vs ~2 dias de NEON optimization manual.

### Implementação do benchmark

**Fase 1 — Bridge pra Superpowered Reverb (~1 hora):**

1. Em `sp_bridge.h` + `sp_bridge.cpp` (módulo `:superpowered-usb`), adicionar:
```cpp
void* sp_reverb_create(int sampleRate);
void sp_reverb_process(void* reverb, float* left, float* right, int numFrames);
void sp_reverb_set_param(void* reverb, int paramId, float value);
void sp_reverb_destroy(void* reverb);
```

2. Em `sp_bridge.cpp`, implementar usando `Superpowered::Reverb`:
```cpp
void* sp_reverb_create(int sampleRate) {
    auto* rev = new Superpowered::Reverb((unsigned int)sampleRate);
    rev->enabled = true;
    return rev;
}
void sp_reverb_process(void* reverb, float* left, float* right, int numFrames) {
    ((Superpowered::Reverb*)reverb)->process(left, right, left, right, numFrames);
}
```

3. Em `fluidsynth_bridge.cpp`, carregar via `dlsym` (mesmo pattern do render callback).

**Fase 2 — Wrapper DSPEffect (~30 min):**

Em `dsp_chain.h`, criar `SuperpoweredReverbEffect : public DSPEffect` que chama as funções do bridge via function pointers.

**Fase 3 — Benchmark A/B (~30 min):**

Adicionar flag no `renderAudioEngine` que alterna entre `ReverbEffect` (STK) e `SuperpoweredReverbEffect` (bridge). Medir AvgDspCh em ambos cenários com o APM HUD.

Se Superpowered Reverb for **2×+ mais rápido** → migrar Reverb (e potencialmente Delay, Compressor).
Se for **<1.5× mais rápido** → NEON-otimizar manualmente (o overhead do bridge anula o ganho).

### Abordagem incremental pós-benchmark

Se o benchmark validar, migrar efeito por efeito (não tudo de uma vez):
1. Reverb (maior ganho, ~50% do custo de DSP)
2. Delay (segundo maior)
3. Compressor (se houver ganho significativo)
4. EQ — manter Butterworth nosso (IIR é inerentemente sequencial, pouco ganho com SIMD)

---

## Arquivos a modificar (apenas pra o benchmark)

| Arquivo | Mudança |
|---|---|
| `superpowered-usb/src/main/cpp/sp_bridge.h` | Declarações C das 4 funções de reverb |
| `superpowered-usb/src/main/cpp/sp_bridge.cpp` | Implementação com `Superpowered::Reverb` |
| `app/src/main/cpp/dsp_chain.h` | `SuperpoweredReverbEffect : public DSPEffect` (wrapper via dlsym) |
| `app/src/main/cpp/fluidsynth_bridge.cpp` | `dlsym` das funções de reverb no `nativeRegisterSpBridge` |

---

## Verificação

1. Build limpo dos dois módulos (`:app` e `:superpowered-usb`)
2. Logcat confirma que as funções de reverb foram carregadas via dlsym
3. Teste A: 6 canais × STK FreeVerb → capturar AvgDspCh
4. Teste B: 6 canais × Superpowered Reverb (via bridge) → capturar AvgDspCh
5. Comparar: se Superpowered for ≥2× mais rápido → migrar; se <1.5× → otimizar manualmente

## Nota sobre a call com Superpowered

Se decidirmos migrar efeitos pro Superpowered, isso **muda o escopo da licença** — passamos de "USB Audio only" pra "USB Audio + Effects". Importante **não mencionar isso na call** até termos os dados do benchmark. Se mencionarmos que pretendemos usar effects, o preço pode subir antes de sabermos se vale a pena.
