Feel free to contribute.

## [gemma4:e4b](https://ollama.com/library/gemma4:e4b) (9.6GB) - Ollama

Practically useless for complex planning tasks it fails expanding the code base and working alone one tasks

## gemma-4-26b-a4b - LM Studio

It is working much better - even if it is the same model; but much larger - expanding code and planing properly.

LM Studio is unstable v0.4.10

## [gemma4:26b / gemma4:26b-a4b-it-q4_K_M](https://ollama.com/library/gemma4:26b-a4b-it-q4_K_M) - Ollama

As good as `gemma-4-26b-a4b - LM Studio`, but faster and stable.

## Qwen 3.5 - LM Studio

Currently not working properly due to an LM Studio Bug

https://github.com/lmstudio-ai/lmstudio-bug-tracker/issues/1592


# Benchmarks

Zähle mir die letzten 5 Bundeskanzler der Bundesrepublik Deutschland auf und nenne zudem kurz in einer Tabelle, wie lange diese regiert haben und ihre am meisten gefeierte Leistung während ihrer Regierungszeit.

## AMD 7900 XT 20 GB on Windows

| Model | Provider | Tokens | Speed | Time | Agent Coding | Status |
|-------|----------|--------|-------|------|--------------|--------|
| gemma-4:26b-a4b-it-q4_K_M | Ollama | 1064 | 29.25 tok/s | 36.38s | ❌ Not usable | ✅ Stable |
| gemma-4-26b-a4b | LM Studio | 2460 | 50.72 tok/s | 48.5s | ❌ Not usable | ✅ Stable |
| gemma-4-26b-a4b-it-claude-opus-distill | LM Studio | 841 | 71.37 tok/s | 11.78s | ❌ Defective (tools not working) | ❌ Defective |
| qwen3.6-35b-a3b | LM Studio | 3974 | 33.56 tok/s | 118.4s | ✅ | ✅ Stable |
| qwen3.6-35b-a3b | Ollama | — | — | — | ❌ Timeout | ❌ Timeout (>4 min) |
| glm-4.7-flash-opus-4.5 | LM Studio | — | — | — | ❌ Deadlock | ❌ Instable (deadlock) |


### Recommendations

- **Qwen 3.6 via LM Studio** is the recommended choice for complex planning tasks. It is stable and handles code expansion properly, despite the slower generation time (~118s).