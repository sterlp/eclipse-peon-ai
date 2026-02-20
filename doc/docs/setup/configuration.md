---
title: Configuration
description: Configure Eclipse Peon AI
---

# Configuration

After installation, configure the plugin via **Window > Preferences > Peon AI**.

## Provider Settings

### Ollama (Recommended for local use)

| Setting | Value |
|---------|-------|
| Provider | Ollama |
| Model | `llama3`, `codellama`, or `mistral` |
| Base URL | `http://localhost:11434` |

### OpenAI

| Setting | Value |
|---------|-------|
| Provider | OpenAI |
| Model | `gpt-4o` or `gpt-4o-mini` |
| Base URL | `https://api.openai.com/v1` |
| API Key | Your OpenAI API key |

### Anthropic

| Setting | Value |
|---------|-------|
| Provider | Anthropic |
| Model | `claude-sonnet-4-20250514` |
| Base URL | `https://api.anthropic.com` |
| API Key | Your Anthropic API key |

## Testing the Connection

1. Open the Peon AI chat view
2. Type a test message like "Hello"
3. If configured correctly, you should receive a response
