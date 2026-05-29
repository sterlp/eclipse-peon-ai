# llama.cpp – Local LLM Inference

[llama.cpp](https://github.com/ggml-org/llama.cpp) enables fast, local LLM inference with minimal setup. It can be used directly via CLI, or through frontends like **LM Studio** or **Ollama** — both of which use llama.cpp as their backend. Running llama.cpp directly gives you full control over parameters and avoids GUI overhead.

## Download

Download the latest prebuilt binaries from the [releases page](https://github.com/ggml-org/llama.cpp/releases):

| Platform | Package |
|----------|---------|
| Windows + NVIDIA GPU | `llama-*-bin-win-cuda-cu12.x-x64.zip` |
| Windows + AMD GPU | `llama-*-bin-win-vulkan-x64.zip` |
| Linux + AMD GPU (ROCm) | `llama-*-bin-ubuntu-rocm-x64.zip` |
| CPU only | `llama-*-bin-win-cpu-x64.zip` |

> **AMD on Windows:** Use Vulkan — ROCm on Windows is unstable. **AMD on Linux:** Use ROCm for best performance.

## Commands

The examples below are based on running **Qwen3.6-27B-Uncensored-HauhauCS-Balanced-IQ4_XS** on an **AMD Radeon RX 7900 XT (20 GB VRAM)** with the Vulkan backend on Windows. Adjust `-ngl`, `-c`, and model path for your hardware.

Placeholders used:

- `MODEL` = full path to your `.gguf` file
- `PORT` = desired server port (e.g. `19999`)

---

### Start Server (OpenAI-compatible API)

```bat
llama-server.exe ^
  -m MODEL ^
  -ngl 64 ^
  -c 95000 ^
  -fa on ^
  --cache-type-k q8_0 ^
  --cache-type-v q8_0 ^
  --parallel 1 ^
  --temp 0.6 ^
  --top-p 0.95 ^
  --top-k 20 ^
  --host 0.0.0.0 ^
  --port PORT
```

The server exposes an OpenAI-compatible API at `http://localhost:PORT/v1`. The built-in chat UI is available at `http://localhost:PORT`.

> On the RX 7900 XT with IQ4_XS and `-c 95000`, the model uses ~16.8 GB VRAM leaving ~2.7 GB free headroom.

---

### Memory Check (VRAM usage before server start)

Loads the model, prints memory breakdown, and exits immediately:

```bat
llama-cli.exe ^
  -m MODEL ^
  -ngl 64 ^
  -c 95000 ^
  -fa on ^
  --cache-type-k q8_0 ^
  --cache-type-v q8_0 ^
  -n 10 ^
  -p "test" ^
  -no-cnv ^
  -v 2>&1 | findstr /i "_memory_ load_tensors done_getting_tensors llama_model_loader common"
```

Look for the line:
```
common_params_fit_impl: projected to use XXXXX MiB of device memory vs. XXXXX MiB of free device memory
```

Run this before changing `-c` to verify the new context size still fits in VRAM.

---

### Benchmark (PP + TG at various context depths)

```bat
llama-bench.exe ^
  -m MODEL ^
  -ngl 64 ^
  -fa 1 ^
  -ctk q8_0 ^
  -ctv q8_0 ^
  -t 6 ^
  -b 512 ^
  -ub 512 ^
  -p 1024 ^
  -n 50 ^
  -d 0,16000,32000,48000,65000 ^
  -r 3 ^
  --progress ^
  -o md
```

Outputs a Markdown table with **PP** (prompt processing) and **TG** (token generation) speeds in tokens/s at each context depth.

**Example results — Qwen3.6 27B IQ4_XS on RX 7900 XT (Vulkan):**

| Context depth | PP (t/s) | TG (t/s) |
|--------------|----------|----------|
| d=0 | 611 | 29.9 |
| d=16k | 481 | 30.2 |
| d=32k | 386 | 27.6 |
| d=48k | 325 | 26.7 |
| d=65k | 289 | 26.5 |

---

## Key Parameters

| Parameter | Description |
|-----------|-------------|
| `-ngl 64` | Offload all layers to GPU (set lower if VRAM is insufficient) |
| `-c 95000` | Context size in tokens |
| `-fa on` | Enable Flash Attention (required for KV cache quantization) |
| `--cache-type-k/v q8_0` | Quantize KV cache to Q8 — reduces VRAM significantly |
| `--parallel 1` | Single-user mode — maximizes VRAM for one context slot |
| `--temp / --top-p / --top-k` | Sampling defaults (can be overridden per request via API) |

## Frontends

LM Studio and Ollama are convenient alternatives if you prefer a GUI or want automatic model management — they both run llama.cpp under the hood. Direct llama.cpp gives you the latest builds and full parameter control without any overhead.

| Option | Setup | Control | Overhead |
|--------|-------|---------|----------|
| **llama.cpp direct** | Manual | Full | None |
| **LM Studio** | GUI | Limited | Medium |
| **Ollama** | CLI/GUI | Medium | Low |
