# Architecture Decision Records (ADR)

Short record of every technical decision. Format per ADR: **Status · Context · Decision ·
Consequences**. One decision per file, captured once.

Behavioural decisions live as rules + BDD in the story docs, not here — an ADR is only added when it
isn't clear from a rule/BDD.

| ADR | Decision | Status |
|-----|----------|--------|
| [0001](0001-per-agent-think-string.md) | Think is resolved to a per-agent String, not a global enum | Accepted |
| [0002](0002-model-mapping-resource-files.md) | The provider/model think mapping lives in resource files, not code | Accepted |
| [0003](0003-send-thinking-independent.md) | send-thinking transport is independent of think support (and stays global, build-time) | Accepted |
| [0004](0004-session-token-accounting.md) | Session token totals accumulate at the StreamingBridge choke point, real-usage-only, never reset | Accepted |
| [0005](0005-widget-owns-state-view-routes.md) | UI widgets own their state/logic (HeaderBarWidget + TokenHeaderWidget); AIChatView only routes monitor events | Accepted |
| [0006](0006-swt-reflow-parent-on-size-change.md) | SWT: when a control's content changes size, re-layout the parent chain, not just the control | Accepted |
| [0007](0007-scaffold-agent-built-in.md) | Scaffold agent as built-in Java class with own ToolService | Accepted |
| [0014](0014-system-line-separator-in-llm-strings.md) | Use System.lineSeparator() in strings sent to LLM to match host OS line endings | Accepted |
| [0008](0008-aiagent-gettoolservice-routing.md) | AiAgent.getToolService() routing for per-agent tool services | Accepted |
| [0009](0009-reloadtool-dedicated.md) | ReloadTool as dedicated service tool for scaffold agent | Accepted |
| [0010](0010-standing-orders-setactiveagent-hook.md) | Standing orders via PeonAiService setActiveAgent hook | Accepted |
| [0011](0011-agent-template-system-prompt.md) | Agent template as system prompt resource for scaffold | Accepted |
| [0015](0015-eclipse-sandbox-boundary.md) | Eclipse VFS as AI sandbox boundary — disk tools are opt-in override | Accepted |
| [0016](0016-async-state-safety.md) | Always capture state before resetting references in async callbacks | Accepted |
| [0017](0017-atomic-ui-chaining.md) | Move queue ownership and chaining into core agent — eliminates UI flicker and race windows | Accepted |
| [0018](0018-abort-path-parity.md) | Explicitly distinguish success from abort; drain queues safely to memory on failure | Accepted |
| [0026](0026-extract-question-shell-approval.md) | QuestionOrchestrator + ShellApprovalService aus AIChatView extrahieren — ~70 Zeilen weniger, testbar ohne SWT | Accepted |
| [0027](0027-osgi-plugin-test-constraints.md) | OSGi Plugin-Tests nutzen JUnit 4 (Eclipse-Runtime); AssertJ bleibt im Maven-core — OSGi-Klassenpfad & Workbench-Startup beachten | Accepted |
| [0020](0020-po-agent-orchestration.md) | Peon-PO orchestrates Plan/Dev as sub-agents via jon* tools with planComplete/planImplemented completion signals | Proposed |
| [0021](0021-po-slave-lifecycle-jit-compaction.md) | Peon-PO slave lifecycle (lazy persistent singletons) & just-in-time compaction | Proposed |
| [0022](0022-write-path-allowlist-decorator.md) | Scope an agent's writes via a write-path-allowlist decorator (comma-separated glob config) | Proposed |
| [0023](0023-po-model-plan-slot.md) | Jon (Peon-PO) reuses the plan model slot, defaulting to the dev/main model | Accepted |
| [0024](0024-po-slaves-ram-only.md) | Peon-PO slaves are RAM-only (no JSON); Jon is durable; the durable handoff is the plan file | Accepted |
| [0025](0025-po-status-widget-named-agents.md) | Header PO-status is pulled from `AiPoAgent.getTeam()` (`NamedAgent` list) into `AiAgentStatusWidget`; ork-named team members (Da Thinka/Da Mek) are distinct from the selectable Plan/Dev; one `instanceof` choke-point in `PeonAiService` | Accepted |
| [0026](0026-extract-question-shell-approval.md) | QuestionOrchestrator + ShellApprovalService aus AIChatView extrahieren — ~70 Zeilen weniger, testbar ohne SWT | Accepted |
| [0027](0027-static-content-loader.md) | StaticContentLoader — effizientes Dateiladen mit Duplikat-Prüfung (record statt ChatMessage-Extension, PathResolver SPI, Callback-Hook) | Superseded |
| [0028](0028-context-item-concept.md) | ContextItem-Konzept — OCP, Agent-besitzter Compact-Flow via `ToolLoopRequest.agent()`, Tool-delegation | Accepted |
| [0029](0029-file-context-in-history.md) | File-Context (AGENTS.md, memory.md, index.md) in die Chat History statt System-Prompt; Dedup nach vollem Workspace-Pfad (nie nach Content), fehlende Datei → skip | Accepted |
| [0030](0030-statictext-helper-frozen-chatmessageutil.md) | staticText()-Helper im Plugin statt Core-Fix: ChatMessageUtil.toString() droppt SystemMessages (silent-bug), Core bleibt frozen | Accepted |
