---
title: Voice Input
description: Configure speech-to-text voice input for Peon AI
---

# Voice Input

Peon AI supports speech-to-text via any Whisper-compatible transcription endpoint. Recording is started and stopped with the microphone button in the chat toolbar.

## Setup

Open **Window → Preferences → Peon AI → Voice Input**.

![Voice Input preferences](../assets/voice-config.png)

Enable **Enable Voice Input** — the microphone button will appear in the chat toolbar once enabled.

## Fields

| Field | Description | Default |
|---|---|---|
| **STT Model** | Model name sent to the endpoint | `voxtral-mini-latest` |
| **Transcription Endpoint** | Path appended to the base URL | `/v1/audio/transcriptions` |
| **Base URL** | Override the transcription host. Leave empty to reuse the main provider URL. | *(empty)* |
| **API Key** | Override the API key for transcription. Leave empty to reuse the main provider API key. | *(empty)* |
| **Language** | BCP-47 code for better accuracy (e.g. `en`, `de`). Leave empty for auto-detect. | *(empty)* |

::: tip Base URL and API Key are only needed when your voice provider differs from your chat provider
If you use OpenAI for chat and Mistral for transcription, set **Base URL** to `https://api.mistral.ai` and **API Key** to your Mistral key. If both are the same provider, leave both empty.
:::

## Provider Examples

### OpenAI

![Voice Input URL field](../assets/voice-config-url.png)

| Field | Value |
|---|---|
| STT Model | `whisper-1` |
| Base URL | *(leave empty — reuses `https://api.openai.com`)* |

### Mistral AI

Mistral exposes a Whisper-compatible transcription endpoint.

| Field | Value |
|---|---|
| STT Model | `whisper-large-v3` |
| Base URL | `https://api.mistral.ai` |

### LM Studio (local)

Load a Whisper model in LM Studio and point voice input at your local server.

| Field | Value |
|---|---|
| STT Model | `whisper` |
| Base URL | `http://localhost:1234` |

### Ollama (local)

| Field | Value |
|---|---|
| STT Model | `whisper` |
| Base URL | `http://localhost:11434` |

## Usage

Click the microphone button to start recording — the button turns red while active. Click again to stop; the audio is sent to the transcription endpoint and the result is inserted into the chat input.
