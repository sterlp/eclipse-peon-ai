# Changelog

All notable changes to Peon AI are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/); versions follow the p2 update-site tags.

Releases before `2.12.4` are documented via [GitHub Releases](https://github.com/sterlp/eclipse-peon-ai/releases).

## [2.17.3] - 16.11.2026

### Changed

- **The project info is the one place that orients you over paths, entry files and the plan.** It
  names the Eclipse path (for the `eclipse*` tools), the disk path (for the `disk*` tools and the
  shell's working directory), the entry files that exist in the project (`pom.xml`, `package.json`,
  `AGENTS.md`, `docs/index.md`) and the plan path when there is a plan. The separate "Plan reference"
  context block and the "FILE ACCESS" line are gone — the agent reaches for the right tool family
  instead of guessing. (`org.sterl.llmpeon`,
  [org.sterl.llmpeon/docs/user-context.md](org.sterl.llmpeon/docs/user-context.md) R-SEL-6)
- **The write boundary follows the real location, not the path text.** A write under a dynamic root is
  allowed only when its resolved location (symlinks followed) lies inside a root — a cloned repo can no
  longer bring a link like `x -> ~/.ssh` that lets an agent write outside project and temp folder;
  links inside a root still work. (`llmpeon-core`,
  [org.sterl.llmpeon.core/docs/write-path-validator.md](org.sterl.llmpeon.core/docs/write-path-validator.md) R7)
- **Reading a file now shows 📄 instead of 📜** — 📜 is free for scripts and source files again.
  (`org.sterl.llmpeon`)
- **Better system prompts**: Compact keeps the user's standing behavioral instructions and stops
  copying content of files that are re-injected after the compact anyway; Plan needs a measurable
  proof with a named tool for every acceptance criterion; Review honors an explicitly narrowed order
  and names in one line what it did not check. (`llmpeon-core` prompts `compressor.md`, `planner.md`,
  `review-agent.md`)

### Added

- **"Did you mean" when a file is not found.** `eclipseReadFile` and `diskReadFile` append up to three
  matches for the last path segment, found with the search of their own family, say so when there are
  more matches, and never read a suggested file by themselves. (`llmpeon-core` / `org.sterl.llmpeon`,
  [org.sterl.llmpeon.core/docs/read-tools.md](org.sterl.llmpeon.core/docs/read-tools.md) R-RT-1)
- **Attachments travel with the message queue.** A file or picture sent while the agent is working is
  no longer lost — the follow-up turn, the turn start and an aborted turn all carry text and
  attachment. An input with an attachment never merges into the previous one, and an urgent (`!`)
  input never carries an attachment. (`llmpeon-core`,
  [org.sterl.llmpeon.core/docs/queued-user-messages.md](org.sterl.llmpeon.core/docs/queued-user-messages.md) Regel 13)
- **Every lint run shows start, resolved root and duration**, so a long run is visible in the status
  bar again instead of looking like a hang. (`llmpeon-core`,
  [org.sterl.llmpeon.core/docs/docs-linter.md](org.sterl.llmpeon.core/docs/docs-linter.md) R-DL-29)

### Fixed

- **A silent AI stream now ends by itself.** The provider deadline (default 3 minutes) applies during
  streaming too: if no answer, thinking or tool call arrives within it, the call breaks with
  `AI stream idle for 180 s (timeout)` and is retried like any other timeout, instead of holding the
  turn and the compact hostage until Stop and a restart. Stop still works within a second even when
  nothing arrives. (`llmpeon-core`,
  [org.sterl.llmpeon.core/docs/agent-error-memory.md](org.sterl.llmpeon.core/docs/agent-error-memory.md) R-AEM-4)
- **Queued messages survive an abort.** A queued input counts as delivered only once it is in the
  agent's working memory — aborting between take and write keeps it (with its attachments), while a
  failing history file alone no longer causes a second delivery. (`llmpeon-core`,
  [org.sterl.llmpeon.core/docs/queued-user-messages.md](org.sterl.llmpeon.core/docs/queued-user-messages.md) Regel 13)
- **Compact no longer wastes tokens on filler.** AI answers that say exactly "ok" and carry no tool
  call and no thinking are dropped from the compact input; the conversation itself keeps them, as the
  provider requires that role sequence. (`llmpeon-core`,
  [org.sterl.llmpeon.core/docs/compact.md](org.sterl.llmpeon.core/docs/compact.md) R-CC-21)
- **The docs linter no longer walks foreign trees.** Under every test root it skips `node_modules`,
  `target`, `bin`, `build`, `out`, `.git`, `.settings` and `.gradle`: a run on the repository root
  dropped from about three minutes to a fraction of a second. (`llmpeon-core`,
  [org.sterl.llmpeon.core/docs/docs-linter.md](org.sterl.llmpeon.core/docs/docs-linter.md) R-DL-28)

## [2.17.2] - 16.11.2026

### Added

- **The model selector in the chat follows the active agent.** The action bar shows the model that
  agent really uses; a typed model is saved for that agent only and only when it truly differs from
  the shown one. The list is cached per connection (provider, URL, key), so switching agents never
  refetches it — the refresh button re-lists on demand and keeps the old list if that fails.
  (`org.sterl.llmpeon`, [org.sterl.llmpeon/docs/model-loading.md](org.sterl.llmpeon/docs/model-loading.md)
  R-ML4/R-ML5, PR #179 fixing #177)

### Removed

- **The experimental web AI factory (`aifactory/`) is gone.** The Eclipse plugin is the only user
  interface again; no plugin code depended on it.

### Fixed

- Saving UI state (the remembered active agent) no longer logs a misleading "Set new config", and the
  model refresh button uses the shared refresh icon instead of a text glyph. (`org.sterl.llmpeon`)

## [2.17.1] - 12.11.2026

### Fixed

- **The chat model selector of 2.17.0 is reverted** — it showed one model for every agent, so a change
  landed at the wrong agent. The configuration pages were never affected; the selector came back in
  2.17.2, bound to the active agent (#179). (`org.sterl.llmpeon`)

## [2.17.0] - 10.11.2026

### Added

- **Model dropdown in the chat dialog.** Next to the agent combo: the model list of the current
  connection, fetched once and cached, with a refresh button; text you type stays in the field and is
  never switched automatically. Reverted in 2.17.1 and replaced by the per-agent selector in 2.17.2.
  (`org.sterl.llmpeon`, `ChatModelComboWidget`)

## [2.16.6] - 10.11.2026

### Changed

- **Feature docs follow one BDD shape.** The doc method now prescribes the scenario format — one
  `gherkin` block per use case, state under `Given` and only the action under `When`, one statement per
  `And` line, values in quotes, no markup inside the block — so rules and scenarios read and render the
  same everywhere. Prompt only, no plugin change. (`llmpeon-core` prompt `doc-method.md`)

## [2.16.5] - 10.11.2026

### Changed

- **Skills reload when one cannot be found.** `skillList` reloads from disk, and `skillRead` /
  `skillReadFile` retry once after a reload (within a ten-minute quiet period) instead of answering
  "no skill with that name" — a skill you just created works without a restart, and a failing reload is
  named in the output instead of silently returning an old list. (`llmpeon-core`,
  [org.sterl.llmpeon.core/docs/project-skills.md](org.sterl.llmpeon.core/docs/project-skills.md))
- **A canceled agent task reads as canceled, not failed** — the caller gets `Agent canceled by user`
  plus what the agent produced so far instead of a failure, and a canceled AI call is recognized as a
  cancellation of its own — the basis for the retry fix below. (`llmpeon-core`)

### Fixed

- **A canceled AI call is no longer retried.** The retry loop stops on a cancellation, which ends the
  recurring "AI call canceled while waiting to retry" that swallowed whole agent runs.
  (`llmpeon-core`, `ApiRetry`)
- **Inserting at the end of a file lands on the last content line.** An `insertLines` call without
  `afterLine`, or with a line number past the end, no longer inserts behind the file's trailing empty
  line. (`llmpeon-core`, `FileLines`)

### Notes

- Removed the unused second streaming bridge; Dev, Review, Compact and delegation prompts improved.
  Most of this cycle went into the experimental web factory, which was removed again in 2.17.2.

## [2.16.4] - 08.11.2026

### Changed

- **Plan mode works from an explicit protocol.** The planner now runs a stated sequence — intake,
  exploration before any design, an interview protocol that resolves open questions with you first,
  architecture and design lenses, a plan contract and a quality gate before the plan is handed over —
  and keeps the saved plan current while the work proceeds. Plans look the same from agent to agent and
  the questions come before the design, not after it. (`llmpeon-core` prompt `planner.md`)

### Notes

- Repository-internal: the development skills were consolidated (two new Eclipse skills for `IFile`
  paths and embedded jars, several retired); nothing that ships to users changed.

## [2.16.3] - 08.11.2026

### Changed

- improved system prompts
- allow self improvement in Agents / Jon mode

## [2.16.2] - 07.11.2026

### Changed

- fixed compact
- rescue tool calls v1
- better info for LLM concerning eclipse vs. disk tools

## [2.16.0] - 01.11.2026

### Changed

- **Queued messages while the AI works are delivered mid-turn.** Messages sent while an agent is
  working — and messages prefixed with `!` in particular — ride along with the next tool result as
  a visible `queued_user_message` block (including the time each message was queued), instead of
  being injected into the running context. The answer you type for a question (`askUser`) carries
  all queued messages along with it. If nothing consumes them in the current turn, they are asked
  as follow-up turns as before.
- **Built-in agents are named Jon, Plan, Dev, Review** (sub-loops `Search`/`Compact`) — display
  name = agent id everywhere (header, roster, history files, prompts); the Ork names are gone.
  Config keys (`llm.agent.*`, model slots) are unchanged ([ADR-0079](docs/adr/0079-display-names-without-ork-names.md))
- **Agent histories start fresh** — history files follow the display name (`Dev-history.jsonl`
  instead of `Peon-Dev-history.jsonl`); old `Peon-*-history.jsonl` files are not read, rename them
  manually to keep a conversation. `agent-order.txt` default is now `^Jon$`; an existing file with
  `^Peon-PO$` no longer matches — such a file falls back to the default order (**Jon** first, rest
  alphabetical), so the dropdown stays usable until the file is updated.
  Same clean break for the agent-specific standing orders: Jon now loads `AGENTS-JON.md` — an
  existing `AGENTS-PO.md` is no longer read, rename it.
- **Scaffold is now `Scaffold`** — the config-artifact agent (agents, skills, commands) drops its
  `Peon-` prefix like the other roles: dropdown, chat header and the sender stamp on its chat lines
  all use that name. Clean break, no alias — an `agent-order.txt` pattern on
  `^Peon-Scaffold$` or `Peon-.*` no longer matches.

### Added

- **Every AI chat line names its speaker.** The core stamps each line with the speaking agent's
  id (`origin`, ADR-0078): the Eclipse plugin header shows who is talking instead of a generic
  "Peon" (lines without an agent carry no header; groups merge only when role and sender match),
  and the web factory attributes final team answers by the stamped sender instead of the tool name.


### New Features

- **Your agent choice survives a restart.** The agent you picked in the chat dropdown is remembered for this Eclipse workspace and restored on start — after a restart or a workspace switch you continue with that agent instead of **Dev**. Only a real change of agent is remembered (a handoff counts, re-picking the active one does not), and an agent that has since been removed or renamed leaves the default in place, quietly. (`org.sterl.llmpeon`, [org.sterl.llmpeon/docs/active-agent-persistence.md](org.sterl.llmpeon/docs/active-agent-persistence.md))

### Fixed

- **The live status bar no longer vanishes on chat events.** Sending a message while the AI works
  (e.g. a `!`-message) or answering a question used to hide the "waiting for AI…" bar until the
  next response chunk arrived. Appended messages no longer hide it — the bar closes only when
  streaming ends or the turn finishes.

- **Jon files docs by topic, not by code.** Topic folders (`docs/<topic>/` with their own index.md) are opt-in — Jon proposes one when a domain owner reaches about three feature docs and asks first; each feature/architecture doc names its technical home (module, package) in its header. (`org.sterl.llmpeon.core` prompts `doc-method.md`, `po.md`)
- **Doc moves keep the links.** When Jon moves or renames a doc, all references — docs, code comments, tests — are updated in the same cycle. (`org.sterl.llmpeon.core` prompt `po.md`)

### Bug Fixes

- **An `agent-order.txt` that matches no agent no longer scrambles the dropdown.** Valid patterns that
  fit nobody — a renamed agent, a copied config — sent every agent to the alphabetical tail, so Jon lost
  first place; now such a file falls back to the default order (`^Jon$`, Jon first) with a warning in the
  log. A partial match behaves as before, and `.*` still gives a deliberately alphabetical list. (R5, `org.sterl.llmpeon.core`, [org.sterl.llmpeon.core/docs/agent-ordering.md](org.sterl.llmpeon.core/docs/agent-ordering.md))
- **A failed AI call now names the endpoint it tried.** Errors that said nothing — a blank message, or the JDK wrapper that only repeats its cause's class name — are reported as `AI call to <url> failed: <Exception> — <cause>`, so a dead model URL is tellable from an MCP one; cancelling a call and errors that already explain themselves stay untouched (R-AEM-3, `org.sterl.llmpeon.core`, [org.sterl.llmpeon.core/docs/agent-error-memory.md](org.sterl.llmpeon.core/docs/agent-error-memory.md), PR #168)

## [2.15.2]

### New Features

- **Jon files docs by topic, not by code.** Topic folders (`docs/<topic>/` with their own index.md) are opt-in — Jon proposes one when a domain owner reaches about three feature docs and asks first; each feature/architecture doc names its technical home (module, package) in its header. (`org.sterl.llmpeon.core` prompts `doc-method.md`, `po.md`)
- **Doc moves keep the links.** When Jon moves or renames a doc, all references — docs, code comments, tests — are updated in the same cycle. (`org.sterl.llmpeon.core` prompt `po.md`)

### Bug Fixes

- **A failed AI call now names the endpoint it tried.** Errors that said nothing — a blank message, or the JDK wrapper that only repeats its cause's class name — are reported as `AI call to <url> failed: <Exception> — <cause>`, so a dead model URL is tellable from an MCP one; cancelling a call and errors that already explain themselves stay untouched (R-AEM-3, `org.sterl.llmpeon.core`, [org.sterl.llmpeon.core/docs/agent-error-memory.md](org.sterl.llmpeon.core/docs/agent-error-memory.md), PR #168)


## [2.15.1]

### Added

- **Docs linter flags oversized docs.** Docs above 1000 lines are reported as a `ZU_LANG` hint
  (file + line count) — a hint, not a finding, so an outgrown doc gets split instead of growing
  silently.

### Changed

- **Search agent starts local, not Eclipse-first.** Tool priority is now "whatever this session
  offers for files and code" (workspace *or* disk), then MCP, then web — the search sub-agent
  behaves the same outside an Eclipse workspace.
- **Jon applies the docs method from its own prompt.** Doc structure, splitting and the
  one-product-vision rule live in a dedicated prompt module (`doc-method.md`); Jon asks before
  splitting a doc the linter reports as `ZU_LANG`.
- **The prompt's path scope comes from the write validator.** Where an agent is told it may write
  is derived from the validator actually in force, so the prompt can no longer promise a scope the
  validator denies.
- **Commits follow the build cycle — and only Jon's.** In a Jon build cycle the dev agent commits
  and pushes after every green increment (message = functional effect + rule/UC IDs, rework as its
  own commit). Merging stays your call (default squash); Peon-Dev used directly never commits,
  pushes or merges unless you ask.

### Fixed

- **Chat no longer clears when a team member starts working.** After a compact, the chat view is
  rebuilt only when the agent shown in the chat finished compacting — never when a delegated
  member (or the search agent) starts its own turn. The fresh question stays visible after a
  pre-turn compact.
- **"Started HH:MM" stays put.** The status bar shows the turn start of the agent that is working;
  a compact or cancel inside the turn no longer resets it. A delegated member shows its own start
  time, and the delegating agent gets its original time back afterwards.
- **A second context overflow in the same turn is compacted again.** A successful in-loop compact
  no longer blocks a later one in the same turn. A failed one ends the turn with the error instead
  of sending calls over budget, and compressor API errors are retried with the turn's normal retry
  logic.
- **A queued message no longer unlocks the running turn.** Input sent while an agent is busy used
  to clear its busy flag, letting a second turn run in parallel on the same conversation memory;
  the queued message now reliably waits its turn.
- **A dead endpoint now names itself.** An AI-call error whose message says nothing — blank, or a
  `CompletionException` envelope carrying nothing but its cause's class name (the JDK form over a
  message-less `ConnectException`) — is rethrown as `AI call to <url> failed: <class> — <cause>`,
  carrying the agent's effective endpoint: a model-API connect error is now tellable from an MCP
  one. Cancellations and errors that carry real information are left untouched. (R-AEM-3)

## [2.15.0] — 2026-10-25

### Added

- **`webSearch` tool (DuckDuckGo).** All agents can search the web: a numbered hit list
  (title, URL, snippet, max 10) with honest sizes (`showing N of M`), capped snippets and a
  disclosure when a snippet was truncated. Built on a thin dedicated DuckDuckGo client.
- **Search agent remembers its research.** The search sub-agent now carries a rolling
  `last-search-rolling` context block — the prompts and final answers of recent searches in the
  same chat view, trimmed to the last 10,000 characters with a disclosure when trimmed.
  Follow-up searches start informed instead of blind; failed or cancelled searches never
  corrupt the block.
- **Skill roots.** Skills load from three roots with precedence project > `~/.agents` > `~/.peon`.

### Changed

- **Compact is visible again.** Compressing a session streams into the chat and ends with one
  clear "… compacted" line (message/token counts); stopping the agent also cancels the running
  compression.
- **`webFetchAsMarkdown` converts pages by content type.** HTML (and `*+xml`) responses are
  converted to Markdown and labelled `(converted from HTML)`; anything else is delivered as raw
  text `(raw text)`. The label is stable across cache hits.
- **Rate-limited searches are reported honestly.** A DuckDuckGo rate limit / bot check is named
  with guidance (don't retry immediately, use `webFetchAsMarkdown` on known URLs instead) —
  no automatic retry that would extend the block.
- **Tool failures are named.** The problem line now carries tool name, time (HH:mm), exception
  class and the cause chain instead of an anonymous message.
- **Plan tool survives disk-written plans.** Plans saved outside the workspace are refreshed and
  seen (`planRead`/`hasPlan` no longer report a false "No plan exists"); archiving clears the
  plan reference, so a following agent handoff can no longer wipe the dev memory on a stale plan.

### Fixed

- **Nested projects (monorepos) resolve by disk location.** A file that is not reachable through
  the workspace tree is resolved via its disk location and disclosed as such (`resolved via …`)
  instead of a bare "not found"; open-project lookups match exactly, not by substring.

## [2.14.1] — 2026-10-21

### Changed

- **Nested projects (monorepos).** A file visible under several projects is now delivered
  exactly once, under the most specific (deepest) project — before, the scan order decided.
  An explicitly selected project scope stays authoritative. The project context now names
  the nesting — one line per direction.
- **Compaction uses the configured window again.** Dev, Plan and Review no longer compact at
  70 % of the budget — the percent shown and the threshold applied are the same number now
  (direct compact above it, hint above 95 % of it). The PO's 70 % early-compact for its team
  members still exists, but as a delegation rule: the slave is compacted right before a new
  task lands.
- **A turn without tool calls is checked again.** Before a new message is accepted, an over-budget
  context is compacted — before the message lands, so a fresh prompt is never compacted away.
  Compaction still happens only *after* a tool round inside the loop (a huge tool result is seen,
  then compacted).
- **Codegraph context is one line.** The injected block no longer carries the MCP server's own
  instruction text (~6,6k characters of generic tutorial prose) — only the disk→workspace path
  mapping stays. The MCP tool description already teaches the tool.

### Removed

- **The one-shot state migration.** Chat histories are no longer copied from `~/.peon/state` into
  the workspace state; old files are ignored (clean break, no migration). Only affects
  installations that skipped 2.x entirely.

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