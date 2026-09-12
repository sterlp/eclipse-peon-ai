# powershell.exe -ExecutionPolicy Bypass -File "C:\Users\pauls\dev\projekte\eclipse-peon-ai\run-qwen.ps1"
cd C:\Users\pauls\dev\projekte\eclipse-peon-ai
llama-server.exe `
  -m "..\..\..\.lmstudio\models\unsloth\Qwen3.8-27B-GGUF\Qwen3.8-27B-UD-Q5_K_XL.gguf" `
  --alias "Qwen3.8-27B" `
  -ngl 999 `
  --prio 3 `
  -c 150000 `
  -fa on `
  --cache-type-k q8_0 `
  --cache-type-v q8_0 `
  -b 2048 `
  -ub 1024 `
  -np -1 `
  --threads-http 2 `
  --cache-reuse 256 `
  --kv-unified `
  --cache-ram 12000 `
  --cache-idle-slots `
  --ctx-checkpoints 64 `
  --temp 0.6 `
  --top-p 0.95 `
  --top-k 20 `
  --min-p 0.0 `
  --reasoning-preserve `
  --chat-template-kwargs '{\"preserve_thinking\": true}' `
  --jinja `
  --chat-template-file qwen-fixed.jinja `
  --host 0.0.0.0 `
  --port 1234 `
  --spec-type draft-mtp `
  --spec-draft-n-max 4 `
  --spec-draft-p-min 0.8

