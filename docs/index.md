# Docs — design & dev spec (the HOW / system reference)

The `docs/` tree is our shared memory: one story per feature (business rules + BDD). Your technical
notes live in [adr/](adr/index.md). Not published — user-facing docs are in `homepage/`.

## Stories

* [Architecture — Component Rules](architecture.md) - Service/Component/View/Widget layering, extraction rules, base packages. **✅ 2026-08-23:** PeonAiService = Fassade über SharedTools-/AgentContext-/BuildPoAgentComponent.
* [Context Architecture](context-architecture.md) - static (System-Prompt) vs dynamic (Chat History) — wer bekommt was, lazy-loading, UI reporting. **2026-08-21:** Static trägt Env+Memory — [ADR-0031](adr/0031-static-context-env-plus-memory.md). **2026-08-23 (✅ done):** Workspace-Memory dynamisch pro Turn (auch Sklaven via Delegate-Orders); statischer Snapshot entfernt → Static = nur Env ([ADR-0032](adr/0032-workspace-memory-dynamic-turn-context.md), Code-Umsetzung durch User).
* [Disk File Write Tool](disk-file-write-tool.md) - real filesystem write/edit tools, configurable workingDir, disabled by default.
* [Eclipse Workspace Write Tool](eclipse-workspace-write-file-tool.md) - Eclipse VFS write/edit tools, project-scoped sandbox, always available.
* [Write-Path Validator](write-path-validator.md) - per-agent `WriteValidator` (agent-provided like the tool filter) that vets the raw write path at the tool choke-point; scopes Jon to `*/docs/*` + `*.md`.
* [Advanced Configuration](advanced-configuration.md) - the two-page preference split and per-agent model resolution via `ChatRequest.modelName()`. **2026-08-21:** SOLL agent-spezifischer Config-Umbau (alles pro Agent, inkl. JSON extra body) — [caching.md](caching.md). **2026-08-30 (✅ 2b):** per-agent Model-Config (URL/Key/Modell/Think/JSON-Body) + Connection-Cache nach Identität — [ADR-0034](adr/0034-connection-cache-by-identity.md); Modell-Dropdown + Think aus Chat-UI raus → Config-Seite (+ Refresh-Button); Core/UI-Trennung, kompletter Config-Rebuild bei Update. **2c (❌ offen):** Custom Agents via AGENT.md-yml + Homepage-Doku + Cache-Beispiele/Hardcode-Entfernung.
* [Custom Agents](custom-agents-design.md) - user-defined `AGENT.md` agents with tool allowlists, read-only mode and per-agent model. **2026-08-28:** komplette Model-Config (URL/Key/Modell/Think/JSON-Body) auch via AGENT.md-Frontmatter (yml) — SOLL in [advanced-configuration.md](advanced-configuration.md), Homepage-Doku folgt.
* [Interaction Design](interaction-design.md) - the chat view layout: history, input block, action bar and status line.
* [Plan & Dev Agent](plan-dev-agent-design.md) - the two-phase plan→dev handoff model and its planned pipeline features.
* [Model Loading](model-loading.md) - model list lifecycle: lazy fetch, persistence across agent switches, fallback on failure. **2026-08-30 (✅ 2b):** Dropdown in der Config-Seite; Liste einmalig pro Verbindungs-Identität gecacht, Cache on success, Refresh-Button, kein Auto-Switch (ADR-0034).
* [Per-Agent Think Support](per-agent-think.md) - per-agent thinking support and request-value resolution via provider mapping files and AGENT.md frontmatter.
* [Queued User Messages](queued-user-messages.md) - input queue with batching, FIFO consumption, drain-to-memory on abort.
* [Session Token Usage](token-usage.md) - cumulative ↑/↓ token spend in the header, fed from the StreamingBridge choke point.
* [Scaffold Agent](scaffold-agent.md) - built-in agent for creating/editing Peon config artifacts (agents, skills, commands) with config-scoped disk tools.
* [Standing Orders](standing-orders-design.md) - **historisch (superseded 2026-08-21).** `StandingOrdersBuilder` gelöscht; aktueller Flow: [Context Architecture](context-architecture.md) + [ADR-0031](adr/0031-static-context-env-plus-memory.md).
* [AGENTS.md Support](agents-md-support.md) - base AGENTS.md loading: purpose, file name resolution, toggle. **✅ 2026-08-16:** History statt System-Prompt (Pfad-Dedup) — [ADR-0029](adr/0029-file-context-in-history.md).
* [Agent-Specific AGENTS-<agent>.md](agent-specific-agentsmd.md) - AGENTS-<agent>.md: agent name resolution, case-insensitive fallback, deduplication.
* [SWT Integrated Input Buttons](swt-integrated-input-buttons.md) - flat icon buttons beside a `StyledText` that read as one white field on macOS + Windows.
* [Ask User Tool](user-question-tool-design.md) - the LLM pausing mid-task to ask a clarifying question inline in the chat.
* [Persistent Agent History](persistent-agent-history.md) - JSONL chat history persistence for Dev, Plan and custom agents.
* [Streaming Response Display](streaming-display.md) - status-bar overlay with bounded live preview, single DOM insert on completion, no incremental chat rendering.
* [Sub-agent tool timing](sub-agent-timing.md) - the sub-agent tools (talkPlan/planWithPlanAgent/askDev/buildWithDev, searchAgent, compactSession) append the nested agent's wall-clock to their done line, e.g. `done. (3s)`.
* [Search Agent Tool (Da Sniffa)](search-agent-tool.md) - stateless one-shot research sub-agent: read-only tools, dedicated search model, thinking disabled, no shell/ask/memory.
* [Agenten-Namen im Chat-Header](agenten-namen-im-chat.md) - **WIP-Design.** AI-Header zeigt den sprechenden Agenten (Peon-PO/-Plan/-Dev, Custom-Agents, Da Sniffa/Da Scribe) statt „Peon"; Name reist auf dem `ToolLoopRequest` mit, kein Monitor-Umbau.
* [Agenten-Status im Header](agenten-status-im-header.md) - **Wird neu gebaut** (siehe MVP-Plan). Alte Umsetzung (active-scoped Roster + `onSubAgent`-Chips) zeigte Agenten doppelt.
* [Agenten-Status Header — MVP-Neubau (Plan)](agenten-status-im-header-mvp-plan.md) - **WIP-Plan.** Pull/MVC statt Observer ([ADR-0025](adr/0025-po-status-widget-named-agents.md)): `AiPoAgent.getTeam()` → `List<NamedAgent>` (Ork-Sklaven Da Thinka/Da Mek) → `AiAgentStatusWidget`; ein `instanceof`-Choke-Point in `PeonAiService`; `getRoster`/`onSubAgent`/`workingSubAgents` fallen weg. Inkremente einzeln baubar.
* [Agent-API-Retry (Mini)](agents-retry.md) - **✅ done.** Eigene Klasse `streaming.ApiRetry` am Choke-Point `ToolLoopRequest.call` für **alle Agenten & Kontexte**: verdiente Geduld (`credit` +1/Erfolg, max 10) + `retryCount` (Reset/Erfolg) → linearer Backoff `min(retryCount·10s, maxWait)`, Sleep in `min(1s, restWait)`-Häppchen, Cancel nie retryt (Klassifikation), Root im Wrapper geloggt, englische `onProblem`-Meldung.
* [Peon-PO (Jon)](po-agent-jon.md) - docs-owning business-owner agent that designs features and orchestrates its own Peon-Plan/Peon-Dev via talkPlan/planWithPlanAgent/askDev/buildWithDev (+ searchAgent) with planComplete/planImplemented completion signals. **2026-08-29:** Prompt-BDDs durch Prompt-Referenzen ersetzt (neue Prompts-Sektion mit Wiring-Tabelle) — Content lebt im Repo, Docs halten das Wiring; R15 ✅ (prompt-basiert).
* [Prompt Inventory & Wiring](prompts.md) - **✅ done (2026-08-29).** Die 12 Built-in-Prompt-Dateien: welche Datei, wer lädt sie, wann, warum der Split — nur Wiring, kein Content (Repo = SOT); keine Prompt-BDDs in den Feature-Docs. Loader `PromptLoader`/`PeonPaths`.
* [Sklaven-Kontext](sklaven-kontext-plan.md) - Jons RAM-Sklaven (Da Thinka/Da Mek) bekommen denselben relevanten Kontext wie der aktive Agent: gewähltes Projekt + `AGENTS.md`-Basis (`getBaseAgentsMd`) + Static-Context Datum/OS/File-Regeln (`setStaticContext` auch auf die Sklaven) — **alle ✅ gebaut, grün, NICHT committed**; Editor-Selektion bewusst nicht, `AGENTS-DEV/PLAN.md` per Sklave = Backlog (Inc 3).
* [Async Agent Tools](async-agent-tools-proposal.md) - **Proposal.** Sync Agent-Tools blockieren Queue. 3 Optionen, Option C (Queue-basiert) empfohlen.
* [Prompt Caching](caching.md) - **❌ specified (2026-08-21/28).** Caching als **per-agent JSON extra body** (Advanced Settings, auch Custom Agents) mit GPT-/Claude-Beispielen in der UI; gilt für jeden Agenten mit Modell-Slot; llama.cpp: kein Snippet = kein Slot belegt; Abgleich über Usage-Cache-Tokens. Ist-Hardcode (Anthropic/OpenAI) durch SOLL ersetzt. R1a: Body per-request wo möglich (verifiziert: OpenAI-Familie; Anthropic Build-time), sonst UI-Gate.
* [Provider (AiProvider)](provider.md) - **✅ done (2026-08-28).** Eigene Komponente: `AiProvider`-Enum → Interface + je Provider eine Klasse (core-Package, kein neues Bundle); Extra-Body-Fähigkeit (`supportsExtraBody()` — **nur dann UI-Eingabe**, caching.md R2) + Think-Support-Fähigkeit (Boolean/Values/FreeString/None/Unknown) je Klasse; verhaltenstreu.

* [Context Message Konzept](context-message-concept.md) - **✅ done.** ContextItem, Agent-besitzter Compact-Flow, Tool-Delegation, Mixed Restore, Standing Orders Migration. + **✅ done 2026-08-16.** File-Context in der History (Dedup nach vollem Pfad, fehlende Datei → skip, AGENTS.md raus aus dem System-Prompt) — [ADR-0029](adr/0029-file-context-in-history.md), Bugfix-ADR [0030](adr/0030-statictext-helper-frozen-chatmessageutil.md).
* [Tool Descriptions Inventory](tool-descriptions-inventory.md) - **✅ done.** 21/55 `@Tool`-Descriptions optimiert (Konsistenz, Token-Effizienz, LLM-Tool-Auswahl).
* [Chat Markdown Links](chat-markdown-links.md) - **✅ done.** Klickbare Dateiverweise im Chat öffnen im Eclipse Editor (Workspace-Pfade, relative Doc-Links, Fallback-Suche, externe URLs im Browser).

## Notes

* [Verbesserungen aus opencode](verbesserungen.md) - **📝 Studie (2026-08-23).** Top 5 Übernahme-Kandidaten aus opencode: Ox-Alpha-Free-Provider, Modell-Katalog, Layered Activation/Onboarding, Secure Credentials + deklarative Auth, Permissions als Daten.
* [Open to Discuss](open-to-discuss.md) - ambiguous items not clear as bugs or features yet; reviewed end-of-cycle.
* [Open Ends / Memory](memory.md) - offene Enden des aktuellen Zyklus; nach jedem Plan-Zyklus aufräumen.
* [ADRs](adr/index.md) - technical decision records (the agent's long-term memory).
