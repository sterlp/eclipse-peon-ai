# Changelog

All notable changes to Peon AI are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/); versions follow the p2 update-site tags.

Releases before `2.12.4` are documented via [GitHub Releases](https://github.com/sterlp/eclipse-peon-ai/releases).

## [2.13.0] — 2026-10-16

### Added

- **Per-agent tool allowlists.** Every agent (built-in and custom) can be restricted to a set of
  tools — via the preference UI for built-in agents (`llm.agent.<id>.tools`, Basic page for Dev)
  and via a `tools:` frontmatter key for custom `AGENT.md` agents. Supports `!` negation
  (allow-then-deny; a deny-only list is rejected in the UI), suffix wildcards (`package*` matches
  `packageSearch`), and a preview button showing the effective tool set per agent.
- **Dedicated REVIEW slot.** The review agent gets its own model slot instead of sharing the
  Plan slot.
- **Tool output symbols.** Tool progress lines now start with a role symbol — 🔍 search/grep,
  📜 read, 🗜 compact — so a chat log can be scanned at a glance.
- **Smarter path resolution.** The Eclipse file tools resolve paths against the selected project
  first, then the workspace root, then all open projects (with a `src/` fallback) — fewer
  "file not found" misses for guessed paths.

### Fixed

- `diskGrepFiles` on a single file target: honest error message and correct extension handling.
- Header status widget no longer breaks when no theme is active (falls back to "light").


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