# Qwen3.6 27B on AMD RX 7900 XT — BIOS & LM Studio Optimization Guide

Getting from 3 tok/s to 35 tok/s on a 20 GB AMD GPU requires fixing three bottlenecks in order: 
1. BIOS configuration, 
2. GPU layer offloading 
3. KV cache management.

---

## BIOS Settings

| Setting | Value | Notes |
|---|---|---|
| **Above 4G Decoding** | Enabled | Prerequisite — must be on before Resize BAR appears |
| **Resizable BAR / SAM** | Enabled | Without this, model weights silently load into shared system RAM |
| **CSM** | Disabled | Blocks Resize BAR from activating even when "enabled" |
| **PCIe Slot Mode** | PCIe 4.0 x16 | Some boards default to 3.0, halving bandwidth |
| **EXPO / XMP** | Enabled | RAM at rated speed improves CPU <-> GPU transfer |

**Verify in GPU-Z:** `Resizable BAR` -> Yes, `Bus Interface` → PCIe x16 4.0 @ x16 4.0, dedicated VRAM usage ~17–18 GB when model is loaded.

---

## LM Studio Settings

### GPU Offload — Set to 64 (all layers)

Leaving even a few layers on CPU forces every token to cross the PCIe bus. Always offload all layers. (~32 GB/s) instead of staying in VRAM (~640 GB/s).

#### ⚠️ The Layer Trade-Off

With a large context window, all 64 layers on GPU can cause **very slow prompt processing** because the KV cache overflows VRAM and spills into system RAM. The fix is **not** to move layers back to CPU — that just trades one problem for another. Fix the KV cache instead (see below).

### K/V Cache Quantization — The Key Fix

Set both **K Cache** and **V Cache Quantization Type** to **Q8_0**.

This halves KV cache VRAM usage with some quality loss (KL divergence < 0.04 on Qwen3.6). It resolves KV cache overflow, restoring fast prompt processing while keeping all 64 layers on GPU. Avoid Q4_0 — it noticeably degrades tool calling and long-context quality on Qwen models.

### Other Settings

| Setting | Value |
|---|---|
| Context Length | 50000 (100K causes KV cache overflow) |
| Flash Attention | Enabled |
| Offload KV Cache to GPU Memory | Enabled |
| Unified KV Cache | Enabled |
| Keep Model in Memory | Enabled |
| Evaluation Batch Size | 512 |

---

## Results

| Fix Applied | Tok/s |
|---|---|
| Baseline (no Resize BAR) | ~3 tok/s |
| After BIOS fix | ~7 tok/s |
| After 64 GPU layers | ~18 tok/s |
| After K/V Cache Q8_0 + 50K context | **~35 tok/s** |

---

## Final Working Configuration

```
Model:                  Qwen3.6-27B Q4_K_M
GPU:                    AMD RX 7900 XT (20 GB VRAM)
Context Length:         50000
GPU Offload:            64
Flash Attention:        ON
K Cache Quantization:   Q8_0
V Cache Quantization:   Q8_0
Evaluation Batch Size:  512
```
