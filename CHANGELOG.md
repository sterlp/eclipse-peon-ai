# Changelog

All notable changes to Peon AI are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/); versions follow the p2 update-site tags.

Releases before `2.12.4` are documented via [GitHub Releases](https://github.com/sterlp/eclipse-peon-ai/releases).

## [2.14.1] — 2026-10-19

### Changed

- **Nested projects (monorepos).** A file visible under several projects is now delivered
  exactly once, under the most specific (deepest) project — before, the scan order decided.
  An explicitly selected project scope stays authoritative. The project context now names
  the nesting — one line per direction.

### Fixed

- **`eclipseGrepFiles` on nested projects.** A doubly visible file was counted twice (hits
  doubled, file cap consumed twice) — the disk file now counts once.

## [2.14.0] — 2026-10-19

### Added

- **Codegraph built-in.** When the active project has a `.codegraph` index, Peon connects the
  local knowledge graph automatically — independent of the MCP toggle, isolated from your configured
  MCP servers. All agents get `codegraph_explore` (one call instead of a search loop). The header
  icon shows ON / OFF / ERROR, the name `codegraph` is reserved.
- **Tagged tool results.** Tool results and harness information are rendered as clearly marked
  blocks (`//-- Start/End …`), so the sender is visible in the text. The compact uses the same tags
  as its only classification key — pure context blocks are dropped with a disclosure
  (`context blocks dropped: K`) instead of eating the budget.
- **`!` override for queued messages.** A message starting with `!` skips the input queue and goes
  directly into the agent's memory while it is working (a `/command` in the rest is still resolved).
  When the agent is idle, the text is sent exactly as typed.
- **MCP tool results report their size**, like the built-in tools: `MCP <name> done. (N chars)`.
- The web-fetch snapshot cache holds 16 pages (paging through large documents stays cheap).

### Changed

- **Compact decides after every tool round.** Above the agent budget it compacts directly (one
  attempt per turn), in the 95 % band it hints — instead of waiting for an explicit compact event.
- **Forward-looking context counter.** After a tool round the counter is the last provider input
  **plus** an estimate of the messages appended since that request, shown with `~`. One counter for
  header, hint and compact gate — so the compact decision sees the tool results that will be in the
  next request instead of running a request that is too large.
- **One trim component for shell and grep.** Shell output is capped by characters (`maxChars`,
  default 10 000, no hidden hard cap) instead of a line count, and grep lines are capped at 500
  chars — every cut is disclosed (`truncated: N chars total`, `showing N of M`).
- **Codegraph: the index gates the start.** The `.codegraph` index is searched upwards to the Git
  root (monorepos work). The binary is only a hint — if it is not found by path, plain `codegraph`
  from your `PATH` is used (Windows: `cmd /c`). A start that fails is reported as an error with the
  reason instead of staying silently off.
- Token figures no longer use thousands separators (`154321`, not `154.321`).

### Fixed

- **Honest shell timeouts.** A timeout kills the whole process tree (children were reparented and
  survived, holding the pipe open), names the time until the timeout actually fired instead of the
  inflated cleanup time, and keeps the partial output — including a final line without a newline.
- Codegraph did not start at all when the binary was not found by the probe, even though the index
  existed.
- Windows: off-by-one line break in the compact's thinking cap.
- `eclipseGrepFiles` on a single file no longer discloses an extension filter that was not applied.
- Only one SLF4J provider on the classpath.

## [2.13.1] — 2026-10-17

- Prompt fixes (Issue #159): PO delegation, developer, plan-write loop.

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