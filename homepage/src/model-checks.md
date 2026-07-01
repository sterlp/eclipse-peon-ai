Please feel free to contribute your experiance.

# Recommendations

WIP

## Local LLMs

- **Qwen 3.6 27B via LM Studio for AMD** is the recommended choice for complex planning tasks. It is stable and handles code expansion properly, despite the slower generation time (~118s).

## Online LLMs

- Opus
- Sonnet > 4.6
- **devstral-2512** -- has free tokens

# Dev Challenge

## MD Challenge

Select the bottom table and ask the LLM to add a benchmar result to the table --> gemma-4:26b-a4b fails by deleting verify else.

## dev-challenge-MockLlmServer

https://github.com/sterlp/eclipse-peon-ai/releases/tag/dev-challenge-MockLlmServer

## qwen3.6-27b-i1 K/V Q8_0 - on peon-ai v1.6.3

- Used tools in batch
- Properly used "readJavaType" - like Opus or Sonnet
- Discovers existing code base
- Took around 75k token to build
- very close / equal to to Qwen 3.6 27B (required manual compact)

## [Qwen 3.6 27B](https://huggingface.co/Qwen/Qwen3.6-27B) K/V Q8_0 - on peon-ai v1.6.3

- Used tools in batch
- Properly used "readJavaType" - like Opus or Sonnet
- Discovers existing code base
- Took around 75k token to build (required manual compact)


## [devstral-2512](https://docs.mistral.ai/models/model-cards/devstral-2-25-12) - on peon-ai v1.6.3

1. Failed in first attempt `dev-challenge-MockLlmServer` with tool call cycle of death.
2. Second attempt was okay and working - in two cycles
- Ignores existing code base
- Took around 75k token to build

## qwen3.6-35b-a3b - on peon-ai v1.6.1

Thinks forever on a simple development task. Propably an issue of the MoE architecture. Canceled after 40k token of thinking.
Added exit sentence for Qwen: `If you notice yourself repeating the same reasoning step, stop and answer now.` doesn't help.

## [gemma4:e4b](https://ollama.com/library/gemma4:e4b) (9.6GB) - Ollama

Practically useless for complex planning tasks it fails expanding the code base and working alone one tasks

## gemma-4-26b-a4b - LM Studio

Useless for complex planning tasks. Already failing in larger MD files.

## [gemma4:26b / gemma4:26b-a4b-it-q4_K_M](https://ollama.com/library/gemma4:26b-a4b-it-q4_K_M) - Ollama

As good as `gemma-4-26b-a4b - LM Studio`, feels more stable.

## Qwen 3.5 - LM Studio

Currently not working properly due to an LM Studio Bug

https://github.com/lmstudio-ai/lmstudio-bug-tracker/issues/1592


# Benchmarks

Hier geht es um die Nutzbarkeit des LLMs für Coding Aufgaben - nicht um die Geschwindigkeit!
Für Geschwindigkeit schaut ins llama.cpp bzw:
- https://github.com/ggml-org/llama.cpp/blob/master/tools/llama-bench/README.md
- https://github.com/eugr/llama-benchy

## AMD 7900 XT 20 GB on Windows

| Model | Provider | Tokens | Speed | Time | Agent Coding | Status |
|-------|----------|--------|-------|------|--------------|--------|
| gemma-4:26b-a4b-it-q4_K_M | Ollama | 1064 | 29.25 tok/s | 36.38s | ❌ Not usable | ✅ Stable |
| gemma-4-26b-a4b | LM Studio | 2460 | 50.72 tok/s | 48.5s | ❌ Not usable | ✅ Stable |
| gemma-4-26b-a4b-it-claude-opus-distill | LM Studio | 841 | 71.37 tok/s | 11.78s | ❌ Defective (tools not working) | ❌ Defective |
| qwen3.6-35b-a3b | LM Studio | 3974 | 33.56 tok/s | 118.4s | ✅ | ✅ Stable - Sometimes thinks forever |
| qwen3.6-35b-a3b | Ollama | — | — | — | ❌ Timeout | ❌ Timeout (>4 min) |
| qwen3.6-27b | LM Studio |  | 3.6 tok/s |  | ✅ | ✅ Stable - proper tool usage |
| glm-4.7-flash-opus-4.5 | LM Studio | — | — | — | ❌ Deadlock | ❌ Instable (deadlock) |
