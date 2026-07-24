# Release Notes — 2026-07-21

## Breaking Changes
- **Config subfolder names pluralized** (`LlmPreferenceInitializer`) — Config directories renamed from singular to plural: `skill` → `skills`, `command` → `commands`. Migrate existing folders manually if you want to keep your old configs/commands/skills.

## New Features
- **Per-model/provider thinking configuration** (`ThinkModelMapping`, `ThinkResolver`) — Set custom reasoning/thinking values per AI provider and model. Built-in mappings for OpenAI (effort levels: none, minimal, low, medium, high, xhigh) and Anthropic (enabled, adaptive). UI exposes a reasoning value picker in Advanced settings with auto-detection of valid values per provider (#100).
- **Token usage statistics** (`TokenStats`, `TokenHeaderWidget`) — View input/output/token counts directly in the chat header bar for each AI response.

## Bug Fixes
- Fixed NPE when using custom agents with certain model configurations (#99)
- Updated icons for think, hammer, and clear actions
