# Changelog

All notable changes to Peon AI are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/); versions follow the p2 update-site tags.

Releases before `2.12.4` are documented via [GitHub Releases](https://github.com/sterlp/eclipse-peon-ai/releases).

## [2.12.4] — 2026-09-30

### Added

- **Per-agent think support.** Each agent (built-in slots and custom agents) has its own Think
  setting — a dropdown instead of a checkbox. An empty value sends *nothing* (unset), explicit
  off-tokens (`off`, `none`, `false`, `no`) turn thinking off. Custom agents configure it via
  `think` in the `AGENT.md` frontmatter (legacy `think_supported` still reads, but only `think`
  is written).
- **Verbatim think values.** OpenAI-family providers and LM Studio now send the selected think
  value exactly as chosen (`none` → `reasoning_effort: "none"`, `xhigh` verbatim, LM Studio
  `"true"` → `"true"`). Previously `none` never reached the request and the model kept thinking.
  Only toggle-style providers (Ollama) still translate to booleans — including `ja`/`nein`.
- **Model inheritance.** Agents with an empty model slot (Plan, Compact, Search, custom agents)
  inherit the Base model instead of failing with `baseUrl cannot be null`; a wrong Base URL now
  falls back to the provider default with an honest error message (#125 regression).
- **"Default for all agents" on the Basic page.** The Basic preference page is now the single
  owner of the default model config — full slot including extra-body JSON and temperature. The
  Advanced page shows per-agent overrides only and no longer writes Base keys (fixes lost edits
  when both pages were visited in one session). Extra body and Think follow the provider live;
  a hidden field keeps its value instead of being deleted.
- **Model config widget.** Provider, URL, API key, Think and model combo with Ping and Reload
  on the Basic page. Ping/Reload always read the live widget values (never stale store state);
  the Think field's available values switch live when the provider changes.
- **Think default inheritance.** An agent's empty Think setting inherits the Base think value;
  an explicit `off` wins.

### Fixed

- GitHub Copilot reasoning content transport parity with other providers.
- Connection config caches no longer serve stale values after provider changes on the Basic page.