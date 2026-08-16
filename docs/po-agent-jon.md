# Peon-PO (Jon)

## Goal

A docs-owning **business-owner agent** — identity **"Jon"**, shown as **`Peon-PO`** in the agent
dropdown. Jon designs features together with the user directly in `docs/`, then drives their
implementation by orchestrating his **own** Peon-Plan and Peon-Dev instances through `jon*` tools.
He owns the WHAT (the docs); the plan and the code are delegated work he never touches himself.

Jon is a **skeptical, critical guardian of the docs**: he keeps them coherent ("round"), always
representing the **SOLL** and clearly separated from the **IST**, and uses Plan/Dev for the heavy
lifting. A question he cannot answer from the docs he **escalates to the user** rather than guessing
(R13).

This story is the design; rules are marked **✅** where built, **❌** where still backlog.

**100 % additive.** Peon-PO only *adds* — a new agent, the `jon*` tools, the slave-side completion
signals (`planComplete` / `planImplemented`) and the write-allowlist decorator. It changes **nothing** in the standalone Peon-Plan / Peon-Dev /
Peon-Scaffold agents or in today's button handoff. Jon lives in the **`core`** module and is therefore
**fully testable in core** with the headless disk tools; the Eclipse plugin only **injects the
Eclipse-workspace tools** (behind the same write wrapper). Jon **never gets a shell** — in any layer.

**Status legend — every rule carries exactly one marker** (mirrored in Jon's system prompt):
**🚧 in design** (being discussed, not yet fully specified) · **❌ specified** (agreed and written
down, but not yet implemented — the backlog) · **✅ done** (implemented by the dev agent with a green
BDD test). Jon captures a rule 🚧, sharpens it to ❌ once agreed, and flips it to ✅ when the dev
agent ships it green (R5).

## Built behaviour (shipped, with tests)

The `jon*` orchestration below (R1–R14) is still the design; these concrete pieces are **built and
green** (`org.sterl.llmpeon.core`/`.test`):

- **First in the dropdown, but not the active default.** `AgentService.getAgents()` returns Peon-PO
  first, then the rest by name (test `PeonAiServiceTest.test_po_is_first_agent`) — a cheap comparator,
  cosmetic only. The **active** default stays Peon-Dev (decision R1): the combo does **not** blindly
  select index 0 — `ActionsBarWidget.updateModeUI(getActiveAgent())` selects the *active* agent's row
  on every config apply, so it pre-selects the **Dev row** while Jon sits at the top of the list. Jon,
  like Peon-Scaffold, is registered in the **plugin** via `PeonAiService.addPersistentAgent` (he needs
  a plugin-assembled, docs-only `ToolService`), not in the core `withDefaultAgent` block that seeds
  `activeAgent = devAgent`.
- **Own model on the plan slot.** Jon reads/writes his model through the **plan model slot**
  (`planModel`) and **defaults to the dev/main model** when it is unset; the pick persists to
  `PREF_PLAN_MODEL`. This fixes the *"No model configured"* seen when Jon was opened first — root
  cause: `AiPoAgent` used to inherit the no-op `setAgentModelName` default, so the auto-selected
  model was dropped (test `test_po_model_uses_plan_slot_and_defaults_to_dev_model`,
  [ADR-0023](adr/0023-po-model-plan-slot.md)).
- **docs/index.md via History (SOLL 2026-08-16, ✅ 2026-08-16).** ~~First-message-Seeding~~ wird ersetzt:
  `docs/index.md` (und `docs/memory.md`) kommen als **Dynamic Context** in Jons Chat History via
  `turnContextSupplier` — einmal pro vollem Pfad, als eigene Message **vor** der User-Message, neu
  nach Compact / Projektwechsel, nie bei Datei-Änderung. `docsIndexSeedForFirstMessage` fällt weg
  (redundant) → [ADR-0029](adr/0029-file-context-in-history.md).
- **Prompt derived from the AGENTS conventions.** `po.txt` makes Jon the guardian of the docs and
  encodes the plan-phase working style (docs-first, IST/SOLL/WEIL, story = goal + rules + BDD
  GIVEN/WHEN/THEN, ADR = memory, `index.md` registries, one-question interview) plus the three status
  markers above (test `AiPoAgentTest.systemPrompt_carriesTheMethodology`).

- **ContextItem + Auto-Load (memory.md, docs/index.md, AGENTS.md) — SOLL 2026-08-16 (✅ 2026-08-16): History
  statt System-Prompt.** `ContextItem` Interface (core) + `EclipseFileContextItem` (plugin) bleiben;
  die Datei-Items wandern in den `turnContextSupplier`: fehlende Datei → `render() = null` →
  übersprungen (kein Crash, kein Status-Eintrag), Dedup nach **vollem Workspace-Pfad** (nie nach
  Content — Datei-Änderungen kommen als Tool-Messages in die History), Header = voller Pfad,
  `lastModified`-Cache weg. **AGENTS.md** (+ AGENTS-\<agent\>.md) folgt allen Agenten inkl. Slaven —
  der System-Prompt bleibt komplett statisch. Siehe [context-message-concept.md](context-message-concept.md)
  und [ADR-0029](adr/0029-file-context-in-history.md).

**BDD:**
```
GIVEN ein Projekt mit docs/memory.md
WHEN Jon eine neue Session startet (erster Turn)
THEN wird memory.md als UserMessage in die Chat History injiziert (vor der User-Message) mit vollem Pfad im Header

GIVEN memory.md existiert nicht
WHEN Jon eine neue Session startet
THEN passiert nichts (kein Error, kein Status-Eintrag)

GIVEN Jon compactSession aufruft
THEN werden die File-Items (memory.md, docs/index.md, AGENTS.md) nach dem Memory-Clear neu injiziert (Pfad-Dedup schlägt fehl)

GIVEN memory.md ist bereits in der History
WHEN Jon die memory.md ändert
THEN wird sie NICHT erneut injiziert (Dedup nach Pfad, nicht nach Content)

GIVEN memory.md wird automatisch geladen
WHEN der Plan-Agent (Da Thinka) startet
THEN bekommt er memory.md NICHT automatisch injiziert
```

## Increment 2 — chat-based delegation (happy path) ✅

**Built & green** (core `JonDelegateToolTest` + `AiPoAgentTest`; the plugin wiring asserts in
`PeonAiServiceTest` run only inside a live Eclipse workspace, like every other `PeonAiServiceTest`).

R1–R14 above are the **full vision** (completion signals, non-blocking queue, header status, error
plumbing). This increment ships the **smallest useful slice**: Jon can actually delegate the *unloved*
plan/dev work to **his own** Plan and Da Mek (Peon-Dev) and get their answers back — **chat/prompt-based, no
completion signals, happy-path only**. It deliberately **defers** R8 (signals), R10 (compaction),
R11 (queue/non-block), R12 (header) and R14 (error handling) to a later Increment 3.

The `jon*` tool **substrate is already in place**: Jon has his docs-only `ToolService` and his writes
are gated (`WriteValidator.DOCS`, built). What this increment adds are the two **delegate tools** plus
Da Thinka and Da Mek behind them.

### I2.1: Four delegate tools + `searchAgent` ✅
Da Thinka and Da Mek were named by **intent** rather than by role: a *talk* verb and a *do* verb per slave,
because "ask a question" and "produce the artefact" want different standing orders. `JonDelegateTool`
exposes four (renamed from the original `jonAskPlan`/`jonAskDev` cut); each drives **one** of Jon's
slaves for **one turn** via `slave.call(prompt, monitor)` (modeled on `SearchAgentTool`, but against a
**persistent** slave so its RAM memory is reused across calls). The slave's reply is the **tool
result** — verbatim back to Jon.

- `talkPlan(prompt)` → ask Da Thinka (Peon-Plan) a question / discuss an approach; **no plan is
  written**.
- `planWithPlanAgent(prompt)` → have Da Thinka (Peon-Plan) **write/refine** the plan into
  `peon-plan/overview.md`. Injects the `plan-write-loop.txt` one-shot standing order (own the plan file,
  slice into small green increments, plan continuously, ask one question when unclear — never guess).
- `askDev(prompt)` → ask Da Mek (Peon-Dev) a question about the code / its progress; **nothing is
  built**.
- `buildWithDev(prompt, planPath?)` → have Da Mek (Peon-Dev) **implement** the released plan. `planPath`
  is **optional**: when given, the path is set as a Da Mek **standing order** so it **survives the
  slave's own compaction** (see I2.6), and the `dev-build-loop.txt` standing order is injected.

Jon additionally gets `searchAgent` (the existing stateless `SearchAgentTool`, "Da Sniffa") — a
throw-away research sub-agent for multi-step lookups, so discovery does not burn his own context.

**No completion signals on the delegate tools.** There is no `planComplete` / `planImplemented` on
`JonDelegateTool`. Control returns to Jon on every team member turn anyway (natural stop), and Jon decides
*"done vs. still working"* **from the reply text** himself (I2.3); Da Mek (Peon-Dev) owns
`planImplemented` and archives only after Jon's post-build review passes.

The slave's **done** progress line carries how long the slave worked, e.g. `Da Thinka done. (3s)` (the
planner; Da Mek (Peon-Dev) shows as `Da Mek`) — the shared sub-agent timing rule (see
[sub-agent-timing.md](sub-agent-timing.md)), which also covers `searchAgent` and `compactSession`.
These display names are UI-only; the tool names and model-facing text stay functional.

**BDD:**
```
GIVEN the user switches to Peon-PO
THEN Jon's ToolService has talkPlan, planWithPlanAgent, askDev, buildWithDev and searchAgent

GIVEN Jon calls talkPlan / askDev with a prompt
THEN the slave runs one turn and its reply text is returned to Jon, and no plan is written / no build runs

GIVEN Jon calls planWithPlanAgent
THEN the plan-write standing order is injected and the Plan slave writes/refines peon-plan/overview.md

GIVEN Jon calls buildWithDev
THEN the Dev slave runs one turn against the released plan and its reply text is returned to Jon
```

### I2.2: Slaves are RAM-only, Jon is durable ✅
**Critical constraint.** Jon keeps his **persisted** state (`FileAgentHistoryStore`, already built).
His slaves do **not** persist — their memory is **RAM-only**: built with the **2-arg**
`AiPlanAgent` / `AiDevAgent` constructor (plain `ThreadSafeMemory`, **no** `FileAgentHistoryStore`, no
JSONL, no JSON of any kind). Same tool set as the standalone agents (`sharedToolService`), but distinct
**instances** and **transient** memory. *(“First step” — later a persistent variant may be
reconsidered.)*

Lifecycle: **one** Plan and **one** Dev slave per Jon session, created **eagerly** on Jon activation and kept alive **in RAM** so their context carries across calls. On app
restart Da Thinka and Da Mek reset — which is fine, because the **durable handoff is the plan file**
(`peon-plan/overview.md`), plus Jon's own persisted state; the slave's memory only holds transient
reasoning. This supersedes R9's earlier “distinct history files” idea: **no history file at all** for
slaves, so the shared-`NAME` clobber concern disappears by construction.

Wiring stays layer-injected via a **slave factory** (R9): core tests inject disk-tool slaves, the
plugin injects Eclipse-workspace-tool slaves — Jon-in-core stays testable without an Eclipse runtime.
See [ADR-0024](adr/0024-po-slaves-ram-only.md).

**BDD:**
```
GIVEN Jon creates his Plan and Dev slaves
THEN each slave uses a RAM-only ThreadSafeMemory and writes NO history/JSON file, while Jon keeps his FileAgentHistoryStore

GIVEN Jon calls the same slave twice in one session
THEN the second call sees the context of the first (persistent-in-RAM singleton)

GIVEN the slave memory holds transient reasoning
THEN the durable plan handoff is the file peon-plan/overview.md, not the slave's memory
```

### I2.3: Delegation guidance — a temporary appended system block ✅
Because there is no structured done-signal, Jon needs steering to run the loop from the **reply text**.
A dedicated **delegation-guidance block** is **appended** to Jon's system messages (kept **out** of the
static `po.txt` identity/methodology prompt) and tells Jon to:

- instruct each slave, in the dispatch prompt, to **state in its reply when it is finished** with the
  task;
- if the reply is **unclear** about done-ness → **re-ask** the same slave (its state is preserved in
  RAM, so it continues where it left off) rather than guessing;
- otherwise treat the reply as an interim question/status and continue.

This is also where Jon is told to **delegate the unloved plan/dev work** rather than doing it himself.

**BDD:**
```
GIVEN Jon delegates to a slave
THEN his appended delegation-guidance block instructs him to make the slave report when it is done

GIVEN a slave reply is unclear about whether it is finished
THEN Jon re-asks the same (state-preserving) slave instead of assuming done or restarting it
```

### I2.4: Plan → review → path handoff to Dev ✅
Happy-path flow, chat-driven end to end:

1. Jon asks Da Thinka (Peon-Plan) for a plan via `planWithPlanAgent`; Da Thinka (Peon-Plan) writes it to
   `peon-plan/overview.md` through its own `PlanTool` (unchanged).
2. Jon **reads and reviews** the plan (via his read tools) and, if unhappy, sends change requests back
   via `planWithPlanAgent` (or clarifies via `talkPlan`).
3. Once satisfied — his **sign-off** — Jon hands the **plan path** (`peon-plan/overview.md`) to Da Mek (Peon-Dev)
   via `buildWithDev` — the durable artefact is the file, so the path is all Dev needs.

Flow order is **plan → sign-off → build → review → retro** (see the appended delegation playbook, I2.3).
Three refinements over the earlier sketch:
- **Jon is not force-compacted** ([ADR-0021](adr/0021-po-slave-lifecycle-jit-compaction.md)): there is no
  "compact after sign-off" step for him. He keeps the turn where the soft 95 % hint lands, updates
  `memory.md`, then self-compacts on his own terms. The per-task `compactSession` discipline still rides
  with the plan to Da Mek (Peon-Dev) (`dev-build-loop.txt`).
- **Review is exactly one mandatory pass, then at Jon's discretion** — no "review loop of death". One
  control pass on a built plan is compulsory; whether Jon re-reviews after a delta plan is his call, never
  a forced loop.
- **Cycle-close retro is at Jon's discretion** (not mandatory, kept short, no second loop): when it is
  worth it, Jon closes a plan/dev cycle by capturing what was learned and what he, the Plan and the Dev
  slave could do better next time. The routing is by *purpose*:
  - **`memory*` tools = "we must forever do it differently"** — the durable behaviour lever. Its content is
    injected into every agent, so it is effectively extra prompt for them; recurring mistakes and what the
    Da Thinka and Da Mek permanently need land here. It is the only thing that changes their behaviour next time (Da Thinka and Da Mek
    are RAM-only and otherwise forget everything).
  - **ADR = Jon's notes / his memory** — what was forgotten or overlooked, what was decided (or built)
    differently than planned. The decision + the *why*, so it is not re-opened; never duplicate a rule/BDD
    into it. The retro is a normal source of ADRs, not a rare exception.
  - **`memory.md` = loose ends and deferred questions** of this cycle (project-local).

No queue, no header, no error plumbing in this increment (deferred).

**BDD:**
```
GIVEN Jon has an approved feature and calls planWithPlanAgent
THEN the Plan slave produces a plan at peon-plan/overview.md and reports back to Jon

GIVEN Jon has reviewed and released the plan
WHEN Jon calls buildWithDev
THEN he passes the plan path peon-plan/overview.md and the Dev slave implements against it
```

### I2.5: Shared memory — Jon writes, slaves only read ✅
Memory is **shared by every agent** in the workspace (`WorkspaceMemoryTool`, one Eclipse-preference
store). Jon knows this, so his memory becomes his **lever on the whole workspace**: a guideline he records is
read by Da Thinka and Da Mek *and* by the user's Peon-Plan/Peon-Dev/Peon-Scaffold.

- **Jon has memory *write*** (`memoryAdd` / `memoryReplace` / `memoryRemove`) in his `ToolService` —
  he curates the shared memory. (Disk tools stay out — Jon does not need them.)
- **Da Thinka and Da Mek are read-only.** They never get the memory-write tool: it is **filtered out** of their
  effective tool set (`getToolFilter` drops `WorkspaceMemoryTool`). Instead the current memory **content
  is injected** into each slave's **standing orders** before every dispatch — they *read* it, they
  cannot *change* it. Only Jon curates shared memory.

Reading via injection (not a read tool) matches how the chat view already surfaces memory as a standing
order; writing is exclusive to Jon by construction (tool filter), not by prompt discipline.

**BDD:**
```
GIVEN the user switches to Peon-PO
THEN Jon's ToolService has the memory-write tool (memoryAdd) plus read-only plan tools, but no planSave

GIVEN Jon dispatches to his Plan or Dev slave
THEN the shared memory content is injected into that slave's standing orders (read)
AND the slave's effective tool set does NOT include the memory-write tool (no write)
```

### I2.6: Jon reads plans (read-only) and hands the path to Dev ✅
Jon **reviews** plans but never **writes** them — planning is delegated. He gets a **read-only** view
onto `PlanTool`:

- `hasPlan()` → returns the **path** (`peon-plan/overview.md`) if a saved plan exists, else states none
  exists — so Jon can check for existing work before delegating.
- `planRead()` → returns the current plan's content.
- He does **not** get `planSave` / `planUpdate` / `planImplemented` — those stay with Da Thinka and Da Mek.

The path from `hasPlan` is exactly what Jon feeds to `buildWithDev(prompt, planPath)` (I2.1): it is set as
a Da Mek standing order and is **sticky**, so it is re-injected on every later dispatch and survives
Da Mek's compaction — the plan path is never lost mid-build.

**BDD:**
```
GIVEN a saved plan exists
WHEN Jon calls hasPlan
THEN it returns the path peon-plan/overview.md (and planRead returns its content)

GIVEN Jon calls buildWithDev with a planPath
THEN the plan path is set as the Dev slave's standing order
AND a later prompt-only buildWithDev call still carries that plan path (sticky, survives compaction)
```

## Business Rules

### R1: Registration & Naming ✅
Built-in agent alongside Peon-Dev, Peon-Plan and Peon-Scaffold. **The default entry agent stays
Peon-Dev** — Peon-PO is opt-in.

- Dropdown / selection name: `Peon-PO`
- Identity in the system prompt **and** in the docs: `Jon`
- Lives in the **`core`** module: package `org.sterl.llmpeon.po`, class `AiPoAgent extends
  AbstractAgent` — fully unit-testable in core (headless disk tools, no Eclipse runtime)
- Registered via `addPersistentAgent()` — survives `clearAgents()` on reload (same as Peon-Scaffold)
- Auto-loads **`AGENTS-PO.md`** (if present) via the **existing generic** agent-specific-AGENTS.md
  mechanism ([agent-specific-agentsmd.md](agent-specific-agentsmd.md)) — Jon needs **no new feature**,
  he just slots in with key **`Peon-PO → PO`**, exactly like `Peon-Dev → DEV` / `Peon-Plan → PLAN`.
  As that mechanism lives in the Eclipse `AgentsMdService`, the load happens in the **plugin** layer
  (headless core simply has no `AGENTS-*.md`, same as every other agent — so core tests are unaffected).
  These are **Jon's own** standing orders — he does **not** pass them down to Da Thinka and Da Mek (R7).

**BDD:**
```
GIVEN AgentService is constructed with the default agents
WHEN getAgents() is called
THEN Peon-PO is in the list alongside Peon-Dev, Peon-Plan and Peon-Scaffold
AND the default active agent is still Peon-Dev
```

### R2: ToolService — reuse existing file tools behind a write-allowlist ✅
Jon has his own `ToolService(false)` (no default-tool leakage, like Peon-Scaffold) holding:
(Tool-Names heute: `talkPlan` / `planWithPlanAgent`, `askDev` / `buildWithDev`, `searchAgent` — I2.1.)

- `jonCreateDevPlan`, `jonAskQuestion` — both drive the **same** persistent Da Thinka (Peon-Plan) (R9),
  distinct tool names for distinct intent: `jonCreateDevPlan` runs the full planning workflow (the plan
  Dev will implement, ends when Da Thinka (Peon-Plan) calls `planComplete()`); `jonAskQuestion` sends a
  **direct question** (`Question: <text>. Just directly respond.`) and returns the answer with **no**
  completion signal.
- `jonAskDev` — drive Da Mek (Peon-Dev) for one turn; the reply is the tool result.
- `SearchAgentTool` — one-shot, **stateless** discovery (unchanged). Complements `jonAskQuestion`,
  which reuses Da Thinka's **warm** project context instead of starting cold.
- **Injected** file tools — Jon gets **no bespoke docs tools**: the **disk** read/write tools in core
  (and tests), the **Eclipse-workspace** read/write tools in the plugin. Each **write** tool is wrapped
  in the **write-path-allowlist decorator** (R3); reads pass through.

No plan* tools, no compact tool on Jon himself, and **no shell in any layer**; writing is bounded by
the allowlist, not by a custom tool.

**BDD:**
```
GIVEN the user switches to Peon-PO
THEN the ToolService has jonCreateDevPlan, jonAskQuestion, jonAskDev, SearchAgentTool and the standard write tools wrapped in the write-allowlist decorator
AND no plan*, shell or compact tools are available
```

### R3: Docs ownership via a write-path allowlist ✅
Jon writes **only** where a configurable allowlist permits — his docs are his single source of truth,
kept coherent and always expressing the SOLL vs. the IST. He treats plan/task artefacts as delegated
work he does not own. **Reading is not gated** — he must see the IST (code included) to keep the docs
honest.

**Write-allowlist decorator + config** (see [ADR-0022](adr/0022-write-path-allowlist-decorator.md)):
the existing Eclipse-workspace and disk write tools are wrapped in a decorator that matches every
target path against a **comma-separated glob list** from config. The list is a **user-editable config
field** (visible and changeable in the settings UI), **preloaded with `*/docs/*`**. Patterns combine as
OR; a write matching none is rejected. The underlying write tool still auto-creates missing sub-paths,
so `docs/` appears on the first write. Semantics:

- `*/docs/*` (default) — a `docs/` folder at **any depth**. In Eclipse the glob is matched against the
  **project-root-relative** path (`<project-name>/docs/...`, the workspace VFS), not a filesystem root;
  for disk tools against the working-dir / given path.
- `docs/*` — only a `docs/` folder **at the (project) root**; the leading position is the sole
  difference from `*/docs/*`.
- `*.md` — any Markdown file, anywhere.

**BDD:**
```
GIVEN the default allowlist */docs/* and a project without a docs/ directory
WHEN Jon writes his first story
THEN the write is allowed and docs/ is created with the file inside it

GIVEN the allowlist */docs/*
WHEN Jon attempts to write a path outside any docs/ folder
THEN the decorator rejects the write

GIVEN the allowlist docs/* (root-only)
WHEN Jon attempts to write <project>/sub/docs/x.md
THEN the decorator rejects it, because docs/ is not at the project root
```

### R4: Onboarding tutorial ❌
On the first activation in a session (`memory.size == 0`) Jon shows a short tutorial message (like
Peon-Scaffold). Later activations in the same session only refresh standing orders.

**BDD:**
```
GIVEN Peon-PO is activated for the first time in a session (memory.size == 0)
THEN a short tutorial message appears in the chat history

GIVEN Peon-PO already has chat history
WHEN the user switches away and back
THEN no tutorial is shown again
```

### R5: Design → approval gate, incremental status ❌
Jon designs and discusses the feature **in the docs** first. He captures a new feature as a story
(goal + business rules + BDD), marks every not-yet-built rule **❌ (WIP)**, and only **after the user
is satisfied** asks *"Shall I implement this?"* before delegating a build to Plan or Dev. (Asking the
Plan agent a **question** via `jonAskQuestion` is design work, not a build — it needs no gate.) When an
implemented slice is confirmed green, Jon flips its rules **❌ → ✅**.

**BDD:**
```
GIVEN the user and Jon are still designing a feature
WHEN Jon has open design questions
THEN Jon keeps refining the story in docs and does NOT delegate to Plan or Dev

GIVEN the user confirms the design is good
THEN Jon asks whether to implement it before calling jonCreateDevPlan

GIVEN a delegated slice returns green and is accepted
THEN Jon flips the affected rules from ❌ to ✅ in the story
```

### R6: Delegate to Plan — `planWithPlanAgent` (build) & `talkPlan` (Q&A) ✅
(Tool-Names I2.1: `jonCreateDevPlan` → `planWithPlanAgent`, `jonAskQuestion` → `talkPlan`.)
Both tools drive the **same** persistent Da Thinka (Peon-Plan) (R9), lazily created on first use and run for
**one turn** via `slave.call(prompt, monitor)` (modeled on `SearchAgentTool`, but against the
persistent Da Thinka — so its memory, auto-compact and standing orders are reused). They differ only in
intent and framing:

- **`jonCreateDevPlan`** — the full planning workflow. The Plan agent interviews **Jon** in place of
  the user: each Plan question comes back as the tool result, Jon answers with the next
  `jonCreateDevPlan` call (a question Jon can't decide → escalate, R13). When the plan is ready Da Thinka (Peon-Plan)
  calls **`planComplete()`** (R8) — Jon's done-marker, carrying the plan link — and Jon **reviews**
  it, then either releases it or sends change requests back via `jonCreateDevPlan`.
- **`jonAskQuestion`** — a direct question to the Plan agent, wrapped as
  `Question: <text>. Just directly respond.` The agent answers **directly** — no interview, no
  completion signal, no handover — and the answer is the tool result. Same warm context as the planning
  turns, so Jon can sanity-check SOLL-vs-IST without kicking off a build.

**BDD:**
```
GIVEN Jon calls jonCreateDevPlan for an approved feature
WHEN the Plan agent asks a clarifying question
THEN the question is returned to Jon as the tool result and Jon answers with the next jonCreateDevPlan call

GIVEN the Plan agent calls planComplete()
THEN Jon gets the done-marker with the plan link and reviews the plan
AND Jon either releases it or returns change requests via jonCreateDevPlan

GIVEN Jon calls jonAskQuestion with "Question: <text>. Just directly respond."
THEN the Plan agent answers directly, does NOT call planComplete(), and the answer is the tool result
```

### R7: Delegate to Dev via `askDev` / `buildWithDev` ✅
(Tool-Names I2.1: `jonAskDev` → `askDev` (Beratung) + `buildWithDev` (Vollzug).)
After releasing the plan Jon calls `buildWithDev`.

**Jon owns his own standing-order logic.** With Jon in **core**, the Eclipse-only `onHandoff` /
`_handoffLine` path (it uses `JdtUtil.pathOf(IFile)`) is unavailable — so Jon does **not** reuse it.
Instead, on every dispatch Jon feeds Da Thinka and Da Mek context as **standing orders** through the
`JonDelegateTool` additional-context supplier (`setAdditionalContext`), while his actual prompt rides
in as a normal **chat
message**. The key standing order is the **plan link**, once a plan exists. **Da Thinka and Da Mek** are handled
the same way: `plan link` (standing order) **+** Jon's question (chat message).

Crucially, Jon does **not** forward his *own* standing orders (his `AGENTS-PO.md`, R1) to Da Thinka and Da Mek —
those are Jon's identity. Jon sets **separate** standing orders **for** each slave. (A richer
cross-agent shared context Jon curates via a tool is a **future** extension, not the MVP.)

Da Mek additionally gets the instruction to call **`planImplemented()`** when finished (R8). On
`planImplemented()` Jon has his done-marker (plan flipped to *done*) and reviews the result, then either
accepts it (flip ❌ → ✅, R5) or returns change requests via `jonAskDev`.

**Post-dev review of larger plans is delegated, not done by Jon himself:** after a completed dev cycle
Jon charges the **Plan agent** with a review + **gap analysis** (built code vs. plan & docs) — via
`jonAskQuestion` for a pure gap analysis, or `jonCreateDevPlan` if the plan itself must change. For
small changes Jon reviews inline; the dedicated Reviewer agent stays a future extension.

**BDD:**
```
GIVEN Jon calls jonAskDev with a released plan
THEN Jon sets a standing order (via the JonDelegateTool additional-context supplier) carrying the plan link plus the instruction to call planImplemented() when done, and his prompt arrives as a chat message
AND Jon's own AGENTS-PO.md standing orders are NOT forwarded to the Dev slave

GIVEN the Dev agent calls planImplemented()
THEN the plan is flipped to done, Jon gets the done-marker and reviews the result
AND Jon accepts (flipping ❌ → ✅) or returns change requests via jonAskDev
```

### R8: Atomic completion signals — `planComplete` / `planImplemented` (core) ❌
The tool-call loop ends by **natural stop** — a slave's turn is over when it emits plain text with no
tool call (`ToolService.executeLoop`, no terminal tool exists). So control returns to Jon on **every**
slave turn — it *is* a tool call. The signal solves a different problem: *did the slave just ask a
clarifying question / give an interim answer, or is the whole job done?*

Two **atomic completion signals**, authored **fresh in core**. The Eclipse `PlanTool` (IFile-bound) is
**not** moved or touched; it stays as standalone legacy.
- **`planComplete()`** — **Da Thinka**: the plan is ready; a **pure** signal (no file I/O) that
  sets the done-latch carrying the **link to the plan**. The plan file stays — Dev needs it.
- **`planImplemented()`** — **Da Mek**: implementation is done. **Atomic:** in one step it
  archives/renames the plan to a *done* name **and** sets the done-latch (link → the archived plan). The
  archive runs through an injected **`PlanArchiver` port** (core interface): a **disk** impl in
  core/tests, an **IFile** impl in the Eclipse plugin. Making the rename **deterministic code** — not a
  second, LLM-discretionary tool call — is what guarantees the active-plan slot is freed for the next
  session; it stays fully core-testable via the disk impl.

They are **markers, not loop-enders**. Jon asks the slave *"are you done?"* and passes the **tool name +
"call it when you are finished"** in the dispatch prompt / standing order.

**Consume-once latch.** A signal only *sets* a small piece of state on the slave — an
`Optional<CompletionInfo>` (done + plan link). After `slave.call(...)` returns, the `jonAsk*` tool
**reads the latch**: if present, it surfaces "done + plan link" to Jon (the tool result **plus** an
OK/done chat message into Jon's memory) and **clears it back to `Optional.empty()`**, so a stale "done"
is never re-consumed on the next turn. If the latch is empty, Jon treats the reply as a clarifying
question / interim status (→ answer or escalate, R13).

**Per-agent tool filtering (static per instance, KV-cache safe):**

| Agent | `planComplete` | `planImplemented` |
| --- | --- | --- |
| Da Thinka | ✅ | — |
| Da Mek | — | ✅ |
| Jon | — | — |
| standalone Peon-Plan / Peon-Dev | — | — |

Da Mek never sees `planComplete`, Da Thinka never sees `planImplemented`, and **Jon has no
plan tools at all**. The signals live **only** on Jon's dedicated slave instances — they never leak into
the user-selectable standalone Plan/Dev. Filters stay **static per agent instance** (a per-turn tool-set
change would kill the KV-cache); `ToolService.addTool` also **throws on a duplicate name**.

**BDD:**
```
GIVEN Jon dispatches the Plan slave with "call planComplete() when the plan is ready"
WHEN the Plan slave calls planComplete()
THEN Jon gets the plan link plus an OK/done chat message marking the plan as done
AND the Dev slave was never offered planComplete

GIVEN Jon dispatches the Dev slave with "call planImplemented() when done"
WHEN the Dev slave calls planImplemented()
THEN planImplemented atomically archives the plan to a done name via the PlanArchiver port and the latch signals Jon completion
AND the Plan slave was never offered planImplemented

GIVEN a slave replies without calling its completion signal
THEN the latch is empty and Jon treats the reply as a clarifying question or interim status, not as done

GIVEN a completion latch was surfaced to Jon
WHEN Jon calls the same slave again
THEN the latch reads Optional.empty() and the stale "done" is not re-consumed
```

### R9: Slave lifecycle — persistent singletons ✅
Per Jon session there is **exactly one** Peon-Plan and **one** Peon-Dev instance. Each is created
**lazily on first delegation** and **kept alive**, holding its context across calls.
Peon-Search stays a stateless **one-shot** agent.

These are **dedicated, Jon-owned instances** — **not** the user-selectable Peon-Plan/Peon-Dev from
`AgentService`, and are **RAM-only — no history files** ([ADR-0024](adr/0024-po-slaves-ram-only.md)). Sharing the *instance* would make the `ask*`/`buildWithDev` tools mutate
the very memory/history the user sees, and the per-agent `working` guard would silently **queue** the
nested `call()` (returning `null`) — so Da Thinka and Da Mek must be distinct instances.

**Tool wiring per layer (Da Thinka and Da Mek are not empty shells).** A dedicated instance still needs a
fully-wired `ToolService`. In **core** it is built with the headless disk tools; in the **Eclipse
plugin** Da Thinka and Da Mek must receive the **Eclipse-workspace tools**, which are assembled only in the
plugin (`PeonAiService`, `sharedToolService = new ToolService()` + `EclipseWorkspaceWriteFileTool` …).

**Shell-Tool-Regel:** Jon (Peon-PO) und Da Thinka (Peon-Plan) bekommen **keine Shell-Tools**.
Da Mek (Peon-Dev) **bekommt Shell-Tools** — er ist der Builder, vergleichbar mit standalone Peon-Dev.
Die Shell-Bestätigungs-Konfiguration (`PREF_SHELL_CONFIRMATION_ENABLED`) hat drei Modi: `always`,
`never`, `not-autonomous`. **Da Mek gilt als autonom** (er arbeitet im Hintergrund für Jon, kein
direktes User-Interface) → `not-autonomous` unterdrückt die Bestätigung für Da Mek. Standalone
Peon-Dev ist *nicht* autonom → bei `not-autonomous` wird der User gefragt.

Two ways, both keeping core clean: **share the same plugin-built `ToolService`** the standalone
Plan/Dev already use (distinct *agent instance*, shared *tool set*), or **participate in the same
tool-`add` step** in `PeonAiService` that builds it. Either way the wiring is **injected per layer via a
slave factory** — core supplies disk-tool slaves for tests, the plugin supplies Eclipse-tool slaves —
so Jon-in-core stays testable without an Eclipse runtime.

**Sklaven-Tool-Filterung (`getToolFilter`):** Strippt `WorkspaceMemoryTool` (nur Jon curates Memory)
und `AskUserTool` (Jon ist das UI, nicht die Sklaven). **ShellTool bleibt erhalten** — Da Mek braucht
es als Builder. Die Shell-Bestätigung wird über den `autonomous`-Flag gesteuert, nicht über den
Tool-Filter.

**BDD:**
```
GIVEN no plan slave exists yet
WHEN Jon makes his first jonCreateDevPlan or jonAskQuestion call
THEN a single Peon-Plan instance is created and reused for every later Plan-side call in the session

GIVEN Jon uses SearchAgentTool
THEN it runs one-shot and holds no context between calls

GIVEN Jon's Plan slave (Da Thinka) is created
THEN Da Thinka's effective tool set does NOT include ShellTool

GIVEN Jon's Dev slave (Da Mek) is created
THEN Da Mek's effective tool set includes ShellTool
AND Da Mek gilt als autonom für die Shell-Bestätigung (`not-autonomous`-Modus unterdrückt die Frage)

AND the standalone Peon-Dev (user-selected) still has ShellTool and is NOT autonomous

Status: 🚧 `PeonAiServiceTest.test_po_slaves_shell_tool_policy` (neuer Test needed)
```

### R10: Just-in-time compaction of the slaves ❌
A slave is compacted **only just before Jon sends it the next message**, and only when its context
exceeds a threshold — a single constant, **default 60 %** (to be fine-tuned later). Implemented by
calling `slave.compressContext(monitor)` before dispatch. Jon's message is delivered as a **standing
order** (like a `/` command) so it **survives the compaction** and is placed **before** the compact
result (`ToolLoopRequest.clearMemory()` re-injects standing orders after clearing) — Da Thinka and Da Mek never
eat their own context away.

The threshold needs its **own explicit constant/basis**: `AbstractAgent.tokenContextUsedInPercent()`
caps its denominator at `min(autoCompactAfter, 4000)`, so "60 %" must be defined against a clear base,
not that fuzzy value.

**BDD:**
```
GIVEN a slave's context is below the threshold
WHEN Jon sends it a message
THEN no compaction happens

GIVEN a slave's context exceeds the threshold (default 60%)
WHEN Jon sends it a message
THEN the slave is compacted first, and Jon's message (as a standing order) survives and precedes the compact result
```

### R11: Non-blocking work — reuse the message queue (no screen lock) ❌
**No input lock.** While a `jon*` loop runs, **Send/Mic stay active** — the user can keep typing. Jon
simply **reuses the existing message queue** ([queued-user-messages.md](queued-user-messages.md)): a
message sent while Jon is busy is acknowledged and parked in Jon's own `AbstractAgent.messageQueue`
(batched, compaction-safe), **not** injected into the running slave. When Jon's current turn finishes,
the queue is **consumed FIFO** as Jon's next prompts — nothing new to build, it comes for free from
`AbstractAgent`. The chaining granularity is Jon's **turn**: a message queued mid-slave waits until the
running `jon*` tool loop has returned, so it never bleeds into an in-flight slave call.

**Stop** stays the abort control ([ADR-0018](adr/0018-abort-path-parity.md)): it cancels via the shared
monitor (below) and the queue **drains to memory** without auto-firing (queue Rule 4), so no intent is
lost and no quota is burned.

**One monitor, one cancel chain (MVP).** Jon passes **his own** `monitor` — the one threaded into the
tool from `ToolLoopRequest` (as `CompactSessionTool` already receives it) — **straight into**
`slave.call(prompt, monitor)` (R6). No separate sub-monitor, no cancel-token translation: a single
**Stop** therefore cancels the whole chain — Jon's turn **and** the running slave. The accepted
trade-off is that, because it is the same monitor / `StreamingBridge`, the slave's messages surface
**live in Jon's chat** (transparency — you see what Plan/Dev are doing) and the slave's tokens flow
through the same choke point into the session total ([ADR-0004](adr/0004-session-token-accounting.md)),
which is correct — it *is* real usage.

**Abort mid-tool caveat:** ADR-0018's "no tool result on abort" holds at model-turn granularity, not
for a sub-agent nested inside a tool. So `jonAsk*` must itself check `monitor.isCanceled()` **after**
the slave returns and drop the tool result on Stop, instead of feeding a half-finished slave reply back
into Jon's memory.

**BDD:**
```
GIVEN Jon is running a jon* loop and the user types a message
THEN Send/Mic stay active, the message is acknowledged and queued in Jon's messageQueue, and it is NOT injected into the running slave

GIVEN Jon's jon* turn finishes successfully with queued messages waiting
THEN they are consumed FIFO as Jon's next prompts

GIVEN the user presses Stop mid-loop
THEN the running sub-agent is aborted via the shared monitor, no tool result is produced, and the queue drains to memory without auto-firing
```

### R12: Header status ❌
While Peon-PO is active, the header shows — next to the usual sent/received token counts —
`peon-dev(context-size)` and `peon-plan(context-size)`, each with a status ball (active / waiting /
idle). Exact ball colours are deferred.

**BDD:**
```
GIVEN Peon-PO is active with a live plan and dev slave
THEN the header shows peon-plan(context-size) and peon-dev(context-size) with their status balls
```

### R13: Escalation to the user (anti-deadlock) ❌
Jon answers Da Thinka and Da Mek's questions himself as long as he can decide from the docs. A question he
**cannot** answer (a genuine user decision) he does **not** guess — he **ends his own turn** with the
question to the user. Jon is then no longer *working*, so the user's answer is submitted directly (or,
if it was typed while Jon was still finishing, it was queued and chains next — R11). Because Da Thinka and Da Mek
are persistent singletons (R9), Jon **resumes** the paused slave with the answer via the next
`jonCreateDevPlan` / `jonAskDev` call.

This is the rule that prevents the hang: Jon **never blocks inside the tool-call loop waiting for the
user** — escalation is always "end turn, resume later", which the persistent slaves make possible.

**BDD:**
```
GIVEN a slave asks Jon a question that requires a user decision
WHEN Jon cannot answer it from the docs
THEN Jon ends his turn with the question; the chat input was never locked (R11 — we rely entirely on the queue feature), the user answers, and no slave is aborted

GIVEN the user answered an escalated question
WHEN Jon continues
THEN he resumes the same persistent slave via jonCreateDevPlan / jonAskDev carrying the answer
```

### R14: Slave failures come back as an error tool-result; Jon reports them (MVP) ❌
Two failure origins must be told apart — and the existing framework already handles them, so the MVP
adds **no** error plumbing, only a **guardrail standing order**:

**(a) Slave technical failure — `jon*` tools are "agent tools" and deviate from the normal tool error
contract.**

*The normal contract, which we do NOT touch.* A standard tool signals a **typical AI error** by
throwing `IllegalArgumentException`; the framework's `SmartToolExecutor` catches it, reports it via
`monitor.onProblem(...)` and **returns the message as the tool result**, so the **tool loop is not
broken**. An **unexpected system error** is *not* handled there and escapes toward the **UI**. We change
**none** of this — the `ToolService` keeps its own error handling. (There is no dedicated tools doc yet;
the contract lives in `SmartToolExecutor` — a small `tools.md` could capture it later.)

*Why Jon's tools differ.* For a normal tool the ultimate sink of an unexpected error is the **human
UI**. But when Jon drives a slave, **Jon _is_ the UI** — a slave failure must land at **Jon**, not
escape past him into the ToolService default handler and on to the real UI. So the `jon*` "agent tools"
**catch every exception themselves** and:

- **report it via `monitor.onProblem(...)`** — the user sees it live; and
- **return it to Jon as the tool result**, carrying: the **caught exception's message**, the **root
  cause** (unwrapped down `getCause`: message + **first ~5 stack lines**, a truncated
`StringUtil.getStackTrace`, not the full trace), and an explicit note that **the slave ran into an
  error and its state may have changed**.

One string, two sinks — `onProblem` (UI, immediate) and the tool result (Jon's context): **user and Jon
see exactly the same**; the full stacktrace stays in the log only. e.g. *"Da Thinka ran into
an error — its state may have changed. Caught: `<msg>`. Root cause: `<root msg>` / `<first ~5 stack
lines>`. Inform the user; do not retry."*

Because a `jon*` tool **always returns** (it never throws), its `tool_use` always gets a matching
`tool_result` — **no dangling `tool_use` by construction** — and the `ToolService`'s generic error
handling (the `SmartToolExecutor` `IllegalArgumentException` path) **never comes into play** for these
tools; we handle the error *inside* the tool rather than handing it to the default handler. Per his
**guardrail standing order** Jon then **summarises the failure to the user and stops — he does not
silently retry** (a technical failure is nothing the LLM can reason its way out of; blind retries burn
quota). Mechanic: a `tool_result` always triggers exactly one follow-up turn, so *"know but don't
retry"* is enforced by that standing order, **not** by a memory trick.

**Did the slave make progress? (deferred.)** Telling Jon whether the slave's history *grew* before it
failed — partial progress → resume, vs. nothing → restart — only matters once Jon **acts** on the error,
which is the deferred retry/resume feature. The MVP carries **no** such progress flag; it is added
together with Jon-level retry (see Future Extensions).

**(b) Jon's own model-call failure** — e.g. a rate limit on *Jon's* request itself (not a tool). This is
**not** a `jon*` tool error: it propagates out of Jon's `executeLoop` → `AbstractAgent.call` →
`handleAbortAndDrain` (drains Jon's queued messages to memory) → rethrow, i.e. the **existing abort/error
path** ([ADR-0018](adr/0018-abort-path-parity.md)) surfaces it to the **real UI** — which is correct,
since here the failure is Jon's, not a slave's.

**Known nuance (future):** Da Thinka and Da Mek are persistent (R9), so a slave turn that fails *after* its prompt was
added leaves that prompt reply-less in the **slave's** memory; on the user-driven retry Jon re-dispatches
and the slave continues from there — fine for MVP. Clean rollback and **Jon-level recoverable-error
handling** (rate-limit backoff, or a *summarised* retry so Jon can adapt) are deferred.

**BDD:**
```
GIVEN Jon called jonCreateDevPlan and the Plan slave throws a timeout mid-plan
THEN the jon* tool CATCHES it itself (the ToolService default error handler never fires) and builds a concise message (caught message + root-cause message + first ~5 stack lines + "slave ran into an error, state may have changed")
AND it returns that message as the tool_result AND pushes the SAME string via monitor.onProblem so the user sees exactly what Jon sees
AND because the tool returns (never throws), the tool_use/tool_result pair is well-formed (no dangling tool_use)
AND Jon summarises the failure to the user and does not silently retry

GIVEN Jon's OWN model call fails (e.g. a rate limit on Jon's request)
THEN it propagates via AbstractAgent.call → handleAbortAndDrain → rethrow (ADR-0018), draining Jon's queue to memory, and no partial turn dangles

GIVEN a slave returns plain text (a clarifying question), not an exception
THEN it is a normal tool result (R8), not treated as an error
```

## Future Extensions (not MVP)

- **Reviewer** — a dedicated agent Jon dispatches to review a plan or the changed code against the
  docs when he is unhappy with Plan/Dev output. In the MVP Jon does this review himself (R6/R7).
- **Self-improvement via Skills (`jonAskScaffold`)** — a `jon*` tool driving a Peon-Scaffold team member so
  Jon can have Skills created/edited and thereby **improve himself over time**. Left out of the MVP
  (it needs its own team member in the lifecycle/header/error rules). This becomes its **own story / feature
  `.md`** — explicitly out of scope here, only flagged as the direction.
- **Cross-agent shared memory** — a tool with which Jon curates **extra** context shared across his
  Da Thinka and Da Mek, beyond the per-dispatch plan link of R7. The MVP passes only the plan link as a standing
  order; this would let Jon set/clear richer cross-agent context deliberately.
- **Recoverable-error handling / retries** — beyond the MVP's report-and-stop (R14): Jon-level
  rate-limit backoff+retry, rolling back a failed team member turn's reply-less prompt, or letting Jon act on
  the error tool-result (adapt/retry) instead of only summarising it to the user.
- **Finer-grained async resolution** — the MVP chains queued user messages at Jon's **turn** boundary
  (R11). A future step could surface/resolve them at the next **team member tool-result** boundary instead,
  for tighter interleaving while a long team member call runs.
- **Generalised handover artefact** — fold the two completion signals (R8) and today's UI-button
  `handoverTo()` / `onHandoff` into one model-callable mechanism where any agent writes a
  `<agent-name>-handover.md` and signals `handoverDone`. Deferred as scope creep: it would rework the
  existing standalone Plan→Dev handoff (breaking the "100 % additive" stance) and conflates "report done
  to my orchestrator" with "hand control to another agent". The MVP signals are shaped to carry a
  link/artefact (the Dev flow renames the plan to a done artefact), so a later merge stays cheap.

## ADRs

- [ADR-0020](adr/0020-po-agent-orchestration.md) — Jon orchestrates Plan/Dev as sub-agents via `jon*`
  tools with `planComplete` / `planImplemented` completion signals (vs. the one-shot button handoff).
- [ADR-0021](adr/0021-po-slave-lifecycle-jit-compaction.md) — team member lifecycle (eager persistent
  singletons) & just-in-time compaction with standing-order survival.
- [ADR-0022](adr/0022-write-path-allowlist-decorator.md) — no bespoke Jon tools; the existing write
  tools are wrapped in a write-path-allowlist decorator driven by a comma-separated glob config
  (default `*/docs/*`).

Related: [Plan & Dev Agent](plan-dev-agent-design.md) — the standalone plan→dev handoff Jon builds on;
[Scaffold Agent](scaffold-agent.md) — the built-in-agent-with-own-ToolService pattern Jon reuses.
