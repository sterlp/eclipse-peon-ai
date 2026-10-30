# Jon

## Goal

A docs-owning **business-owner agent** — identity **"Jon"**, shown as **`Jon`** in the agent
dropdown. Jon designs features together with the user directly in `docs/`, then drives their
implementation by orchestrating his **own** Plan and Dev instances through `poagent` tools
(`talkPlan`/`planWithPlanAgent`/`askDev`/`buildWithDev` — früher `jon*` tools genannt).
He owns the WHAT (the docs); the plan and the code are delegated work he never touches himself.

Jon is a **skeptical, critical guardian of the docs**: he keeps them coherent ("round"), always
representing the **SOLL** and clearly separated from the **IST**, and uses Plan/Dev for the heavy
lifting. A question he cannot answer from the docs he **escalates to the user** rather than guessing
(R13).

This story is the design; rules are marked **✅** where built, **❌** where still backlog.

**100 % additive.** Jon only *adds* — a new agent, the `poagent` delegate tools, the slave-side
completion signals (`planComplete` / `planImplemented`) and the write-allowlist decorator. It changes
**nothing** in the standalone Plan / Dev /
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

- **First in the dropdown, but not the active default.** `AgentService.getAgents()` returns Jon
  first, then the rest by name (test `PeonAiServiceTest.test_po_is_first_agent`) — a cheap comparator,
  cosmetic only. The **active** default stays Dev (decision R1): the combo does **not** blindly
  select index 0 — `ActionsBarWidget.updateModeUI(getActiveAgent())` selects the *active* agent's row
  on every config apply, so it pre-selects the **Dev row** while Jon sits at the top of the list. Jon,
  like Peon-Scaffold, is registered in the **plugin** via `PeonAiService.addPersistentAgent` (he needs
  a plugin-assembled, docs-only `ToolService`), not in the core `withDefaultAgent` block that seeds
  `activeAgent = devAgent`.
- **Own model slot (✅ 3a, 2026-09-03).** Jon liest/schreibt sein Modell über den **eigenen
  `po`-Slot** (`llm.agent.po.*`) und fällt bei leerem Slot auf das **Base-Modell** zurück —
  „No model configured" kann damit weiterhin nicht auftreten. **Kein** Rückfall auf den
  Plan-Slot (Test `test_po_model_uses_po_slot_and_defaults_to_base_model`,
  [ADR-0036](../../docs/adr/0036-po-own-model-slot.md), SOLL in
  [advanced-configuration.md](../../org.sterl.llmpeon/docs/advanced-configuration.md) R-PO1…R-PO4).
  *Historisch (bis 3a): Jon teilte sich den Plan-Slot — [ADR-0023](../../docs/adr/0023-po-model-plan-slot.md),
  superseded.*
  Offen bis 3b: die **Temperature** ist noch ein Base-Level-Key (`getPlanTemperature()`), kein
  Slot-Feld.
- **docs/index.md via History (SOLL 2026-08-16, ✅ 2026-08-16).** ~~First-message-Seeding~~ wird ersetzt:
  `docs/index.md` (und `docs/memory.md`) kommen als **Dynamic Context** in Jons Chat History via
  `turnContextSupplier` — einmal pro vollem Pfad, als eigene Message **vor** der User-Message, neu
  nach Compact / Projektwechsel, nie bei Datei-Änderung. `docsIndexSeedForFirstMessage` fällt weg
  (redundant) → [ADR-0029](../../docs/adr/0029-file-context-in-history.md).
- **Prompt derived from the AGENTS conventions.** `po.txt` makes Jon the guardian of the docs and
  encodes the plan-phase working style (docs-first, IST/SOLL/WEIL, story = goal + rules + BDD
  GIVEN/WHEN/THEN, ADR = memory, `index.md` registries, one-question interview) plus the three status
  markers above — assembly: `default.txt` + `po.txt` + white-list line + `po-delegation.txt`, see
  Prompts; test `AiPoAgentTest.systemPrompt_carriesTheMethodology` asserts the method section is present).

- **ContextItem + Auto-Load (memory.md, docs/index.md, AGENTS.md) — SOLL 2026-08-16 (✅ 2026-08-16): History
  statt System-Prompt.** `ContextItem` Interface (core) + `EclipseFileContextItem` (plugin) bleiben;
  die Datei-Items wandern in den `turnContextSupplier`: fehlende Datei → `render() = null` →
  übersprungen (kein Crash, kein Status-Eintrag), Dedup nach **vollem Workspace-Pfad** (nie nach
  Content — Datei-Änderungen kommen als Tool-Messages in die History), Header = voller Pfad,
  `lastModified`-Cache weg. **AGENTS.md** (+ AGENTS-\<agent\>.md) folgt allen Agenten inkl. Slaven —
  der System-Prompt bleibt komplett statisch. Siehe [context-message-concept.md](context-message-concept.md)
  und [ADR-0029](../../docs/adr/0029-file-context-in-history.md).

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
WHEN der Plan-Agent (Plan) startet
THEN bekommt er memory.md NICHT automatisch injiziert
```

## Prompts

Jon's system prompt is assembled from built-in prompt files. **Policy:** the *content* of prompt
files lives in the repo — that is the source of truth. The docs hold only the **wiring**: which
file, who loads it, when, why the split. Prompt-driven behaviour rules below carry a **reference**
to the prompt file instead of a BDD. Full 12-file inventory + loader mechanics:
[prompts.md](prompts.md).

| File | Loaded by | When | Why it exists |
|---|---|---|---|
| `default.md` | `PromptLoader` (static base) | always — prepended via `loadWithDefault` | shared base behaviour for all built-in agents (and custom-agent bodies) |
| `doc-method.md` ❌ | `AiPoAgent` + Paco (static) | always — right after Jon's/Paco's own prompt (`default` + `po`/`paco` + `doc-method`, both say "Doc-Methode unten"); `${docs}` resolved like `po.md` | the shared docs-first method (SOLL/IST/WEIL, GIVEN/WHEN/THEN, symbols/status markers, conflict check, one-question interviews) — one source so Jon and Paco never drift ([aifactory R-PC-7](../../aifactory/docs/paco.md)) |
| `po.md` | `AiPoAgent` (static) | always — Jon's system prompt | Jon's `## Charakter` + build role (IDs/`nextIds`, ADRs, architecture docs, status flips, team) |
| `po-delegation.md` | `AiPoAgent` (static) | always — appended after the path-white-list line | the delegation playbook (plan → sign-off → build → one review → retro, cycle/branch discipline) — split out so the identity stays clean and the playbook evolves on its own |
| `po-tutorial.md` | plugin `PeonAiService` | first activation in a session (R4) | onboarding message, like Peon-Scaffold |
| `plan-write-loop.md` | `PoDelegateTool.planWithPlanAgent` | one-shot standing order per plan dispatch | plan-writing discipline — only when Plan actually writes the plan, not on `talkPlan` |
| `dev-build-loop.md` | `PoDelegateTool.buildWithDev` | per build dispatch with a (sticky) planPath | build discipline (vertical slices, green gate, git commits, compactSession) — only when Dev builds from a released plan, not on `askDev` |

**Split rationale:** identity (static, small) · playbook (operational, changes more often) · loop
disciplines (one-shot injections bound to the work verbs — talk/ask turns stay lean, and the
discipline survives the slave's own compaction as a standing order) · tutorial (UI-level onboarding,
plugin only).

## Increment 2 — chat-based delegation (happy path) ✅

**Built & green** (core `PoDelegateToolTest`, früher `JonDelegateToolTest` + `AiPoAgentTest`; the plugin wiring asserts in
`PeonAiServiceTest` run only inside a live Eclipse workspace, like every other `PeonAiServiceTest`).

R1–R14 above are the **full vision** (completion signals, non-blocking queue, header status, error
plumbing). This increment ships the **smallest useful slice**: Jon can actually delegate the *unloved*
plan/dev work to **his own** Plan and Dev and get their answers back — **chat/prompt-based, no
completion signals, happy-path only**. It deliberately **defers** R8 (signals), R10 (compaction),
R11 (queue/non-block), R12 (header) and R14 (error handling) to a later Increment 3.

The `poagent` tool **substrate is already in place**: Jon has his docs-only `ToolService` and his writes
are gated (`WriteValidator.DOCS`, built). What this increment adds are the two **delegate tools** plus
Plan and Dev behind them.

### I2.1: Four delegate tools + `searchAgent` ✅
Plan and Dev were named by **intent** rather than by role: a *talk* verb and a *do* verb per slave,
because "ask a question" and "produce the artefact" want different standing orders. `PoDelegateTool`
(früher `JonDelegateTool`, Package `org.sterl.llmpeon.poagent`)
exposes four (renamed from the original `jonAskPlan`/`jonAskDev` cut); each drives **one** of Jon's
slaves for **one turn** via `slave.call(prompt, monitor)` (modeled on `SearchAgentTool`, but against a
**persistent** slave so its RAM memory is reused across calls). The slave's reply is the **tool
result** — verbatim back to Jon.

- `talkPlan(prompt)` → ask Plan a question / discuss an approach; **no plan is
  written**.
- `planWithPlanAgent(prompt)` → have Plan **write/refine** the plan into
  `peon-plan/overview.md`. Injects the `plan-write-loop.txt` one-shot standing order (own the plan file,
  slice into small green increments, plan continuously, ask one question when unclear — never guess).
- `askDev(prompt)` → ask Dev a question about the code / its progress; **nothing is
  built**.
- `buildWithDev(prompt, planPath?)` → have Dev **implement** the released plan. `planPath`
  is **optional**: when given, the path is set as a Dev **standing order** so it **survives the
  slave's own compaction** (see I2.6), and the `dev-build-loop.txt` standing order is injected.

Jon additionally gets `searchAgent` (the existing stateless `SearchAgentTool`, "Search") — a
throw-away research sub-agent for multi-step lookups, so discovery does not burn his own context.

**No completion signals on the delegate tools.** There is no `planComplete` / `planImplemented` on
`PoDelegateTool`. Control returns to Jon on every team member turn anyway (natural stop), and Jon decides
*"done vs. still working"* **from the reply text** himself (I2.3); Dev owns
`planImplemented` and archives only after Jon's post-build review passes.

The slave's **done** progress line carries how long the slave worked, e.g. `Plan done. (3s)` (the
planner; Dev shows as `Dev`) — the shared sub-agent timing rule (see
[sub-agent-timing.md](sub-agent-timing.md)), which also covers `searchAgent` and `compactSession`.
These display names are UI-only; the tool names and model-facing text stay functional.

**BDD:**
```
GIVEN the user switches to Jon
THEN Jon's ToolService has talkPlan, planWithPlanAgent, askDev, buildWithDev and searchAgent

GIVEN Jon calls talkPlan / askDev with a prompt
THEN the slave runs one turn and its reply text is returned to Jon, and no plan is written / no build runs
```

The standing-order injections (`plan-write-loop.txt` on `planWithPlanAgent`, `dev-build-loop.txt` on
`buildWithDev`) are **prompt wiring** — see Prompts; no prompt BDD here.

### I2.2: Slaves are RAM-only, Jon is durable ✅
**Critical constraint.** Jon keeps his **persisted** state (`FileAgentHistoryStore`, already built).
His slaves do **not** persist — their memory is **RAM-only**: built with the **2-arg**
`AiPlanAgent` / `AiDevAgent` constructor (plain `ThreadSafeMemory`, **no** `FileAgentHistoryStore`, no
JSONL, no JSON of any kind). Same tool set as the standalone agents (`sharedToolService`), but distinct
**instances** and **transient** memory. *(“First step” — later a persistent variant may be
reconsidered.)*

Lifecycle: **one** Plan and **one** Dev slave per Jon session, created **eagerly** on Jon activation and kept alive **in RAM** so their context carries across calls. On app
restart Plan and Dev reset — which is fine, because the **durable handoff is the plan file**
(`peon-plan/overview.md`), plus Jon's own persisted state; the slave's memory only holds transient
reasoning. This supersedes R9's earlier “distinct history files” idea: **no history file at all** for
slaves, so the shared-`NAME` clobber concern disappears by construction.

Wiring stays layer-injected via a **slave factory** (R9): core tests inject disk-tool slaves, the
plugin injects Eclipse-workspace-tool slaves — Jon-in-core stays testable without an Eclipse runtime.
See [ADR-0024](../../docs/adr/0024-po-slaves-ram-only.md).

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
A dedicated **delegation-guidance block** (the `po-delegation.txt` file — see Prompts) is
**appended** to Jon's system messages (kept **out** of the static `po.txt` identity/methodology
prompt) and tells Jon to:

- instruct each slave, in the dispatch prompt, to **state in its reply when it is finished** with the
  task;
- if the reply is **unclear** about done-ness → **re-ask** the same slave (its state is preserved in
  RAM, so it continues where it left off) rather than guessing;
- otherwise treat the reply as an interim question/status and continue.

This is also where Jon is told to **delegate the unloved plan/dev work** rather than doing it himself.

**Prompt-Behaviour — kein BDD hier:** die Done-/Re-Ask-Disziplin steuert `po-delegation.txt`
(Content im Repo, siehe Prompts).

### I2.4: Plan → review → path handoff to Dev ✅
Happy-path flow, chat-driven end to end:

1. Jon asks Plan for a plan via `planWithPlanAgent`; Plan writes it to
   `peon-plan/overview.md` through its own `PlanTool` (unchanged).
2. Jon **reads and reviews** the plan (via his read tools) and, if unhappy, sends change requests back
   via `planWithPlanAgent` (or clarifies via `talkPlan`).
3. Once satisfied — his **sign-off** — Jon hands the **plan path** (`peon-plan/overview.md`) to Dev
   via `buildWithDev` — the durable artefact is the file, so the path is all Dev needs.

Flow order is **plan → sign-off → build → review → retro** (see the appended delegation playbook, I2.3).
Three refinements over the earlier sketch:
- **Jon is not force-compacted** ([ADR-0021](../../docs/adr/0021-po-slave-lifecycle-jit-compaction.md)): there is no
  "compact after sign-off" step for him. He keeps the turn where the soft 95 % hint lands, updates
  `memory.md`, then self-compacts on his own terms. The per-task `compactSession` discipline still rides
  with the plan to Dev (`dev-build-loop.txt`).
- **Review is exactly one mandatory pass, then at Jon's discretion** — no "review loop of death". One
  control pass on a built plan is compulsory; whether Jon re-reviews after a delta plan is his call, never
  a forced loop.
- **Cycle-close retro is at Jon's discretion** (not mandatory, kept short, no second loop): when it is
  worth it, Jon closes a plan/dev cycle by capturing what was learned and what he, the Plan and the Dev
  slave could do better next time. The routing is by *purpose*:
  - **`memory*` tools = "we must forever do it differently"** — the durable behaviour lever. Its content is
    injected into every agent, so it is effectively extra prompt for them; recurring mistakes and what the
    Plan and Dev permanently need land here. It is the only thing that changes their behaviour next time (Plan and Dev
    are RAM-only and otherwise forget everything).
  - **ADR = Jon's notes / his memory** — what was forgotten or overlooked, what was decided (or built)
    differently than planned. The decision + the *why*, so it is not re-opened; never duplicate a rule/BDD
    into it. The retro is a normal source of ADRs, not a rare exception.
  - **`memory.md` = loose ends and deferred questions** of this cycle (project-local).

No queue, no header, no error plumbing in this increment (deferred).

**Prompt-Behaviour — kein BDD hier:** die Flow-Ordnung (plan → sign-off → build → review → retro)
steuert `po-delegation.txt` (Content im Repo, siehe Prompts); die Tool-Mechanik selbst ist I2.1/I2.6.

### I2.5: Shared memory — Jon writes, slaves only read ✅
Memory is **shared by every agent** in the workspace (`WorkspaceMemoryTool`, one Eclipse-preference
store). Jon knows this, so his memory becomes his **lever on the whole workspace**: a guideline he records is
read by Plan and Dev *and* by the user's Plan/Dev/Peon-Scaffold.

- **Jon has memory *write*** (`memoryAdd` / `memoryReplace` / `memoryRemove`) in his `ToolService` —
  he curates the shared memory. (Disk tools stay out — Jon does not need them.)
- **Plan and Dev are read-only.** They never get the memory-write tool: it is **filtered out** of their
  effective tool set (`getToolFilter` drops `WorkspaceMemoryTool`). Instead the current memory **content
  is injected** into each slave's **standing orders** before every dispatch — they *read* it, they
  cannot *change* it. Only Jon curates shared memory.

Reading via injection (not a read tool) matches how the chat view already surfaces memory as a standing
order; writing is exclusive to Jon by construction (tool filter), not by prompt discipline.

**BDD:**
```
GIVEN the user switches to Jon
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
- He does **not** get `planSave` / `planUpdate` / `planImplemented` — those stay with Plan and Dev.

The path from `hasPlan` is exactly what Jon feeds to `buildWithDev(prompt, planPath)` (I2.1): it is set as
a Dev standing order and is **sticky**, so it is re-injected on every later dispatch and survives
Dev's compaction — the plan path is never lost mid-build.

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
Built-in agent alongside Dev, Plan and Peon-Scaffold. **The default entry agent stays
Dev** — Jon is opt-in.

- Dropdown / selection name: `Jon`
- Identity in the system prompt **and** in the docs: `Jon`
- Lives in the **`core`** module: package `org.sterl.llmpeon.po`, class `AiPoAgent extends
  AbstractAgent` — fully unit-testable in core (headless disk tools, no Eclipse runtime)
- Registered via `addPersistentAgent()` — survives `clearAgents()` on reload (same as Peon-Scaffold)
- Auto-loads **`AGENTS-JON.md`** (if present) via the **existing generic** agent-specific-AGENTS.md
  mechanism ([agent-specific-agentsmd.md](agent-specific-agentsmd.md)) — Jon needs **no new feature**,
  he just slots in with key **`Jon`** (candidates `AGENTS-Jon.md` / `agents-jon.md` / `AGENTS-JON.md`),
  like `Dev → DEV` / `Plan → PLAN`.
  Clean break: an old `AGENTS-PO.md` is **no longer** loaded for Jon — rename it (release note 2026-10-30).
  As that mechanism lives in the Eclipse `AgentsMdService`, the load happens in the **plugin** layer
  (headless core simply has no `AGENTS-*.md`, same as every other agent — so core tests are unaffected).
  These are **Jon's own** standing orders — he does **not** pass them down to Plan and Dev (R7).

**BDD:**
```
GIVEN AgentService is constructed with the default agents
WHEN getAgents() is called
THEN Jon is in the list alongside Dev, Plan and Peon-Scaffold
AND the default active agent is still Dev
```

### R2: ToolService — reuse existing file tools behind a write-allowlist ✅
Jon has his own `ToolService(false)` (no default-tool leakage, like Peon-Scaffold) holding:
(Tool-Names heute: `talkPlan` / `planWithPlanAgent`, `askDev` / `buildWithDev`, `searchAgent` — I2.1.
Historische Namen `jonCreateDevPlan`/`jonAskQuestion`/`jonAskDev` → heute
`planWithPlanAgent`/`talkPlan`/`askDev`.)

- `jonCreateDevPlan`, `jonAskQuestion` — both drive the **same** persistent Plan (R9),
  distinct tool names for distinct intent: `jonCreateDevPlan` runs the full planning workflow (the plan
  Dev will implement, ends when Plan calls `planComplete()`); `jonAskQuestion` sends a
  **direct question** (`Question: <text>. Just directly respond.`) and returns the answer with **no**
  completion signal.
- `jonAskDev` — drive Dev for one turn; the reply is the tool result.
- `SearchAgentTool` — one-shot, **stateless** discovery (unchanged). Complements `jonAskQuestion`,
  which reuses Plan's **warm** project context instead of starting cold.
- **Injected** file tools — Jon gets **no bespoke docs tools**: the **disk** read/write tools in core
  (and tests), the **Eclipse-workspace** read/write tools in the plugin. Each **write** tool is wrapped
  in the **write-path-allowlist decorator** (R3); reads pass through.

No plan* tools, no compact tool on Jon himself, and **no shell in any layer**; writing is bounded by
the allowlist, not by a custom tool.

**BDD:**
```
GIVEN the user switches to Jon
THEN the ToolService has jonCreateDevPlan, jonAskQuestion, jonAskDev, SearchAgentTool and the standard write tools wrapped in the write-allowlist decorator
AND no plan*, shell or compact tools are available
```

### R3: Docs ownership via a write-path allowlist ✅
Jon writes **only** where a configurable allowlist permits — his docs are his single source of truth,
kept coherent and always expressing the SOLL vs. the IST. He treats plan/task artefacts as delegated
work he does not own. **Reading is not gated** — he must see the IST (code included) to keep the docs
honest.

**Write-allowlist decorator + config** (see [ADR-0022](../../docs/adr/0022-write-path-allowlist-decorator.md)):
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

### R16: Compact-Guard — < 3 Messages → Skip, kein LLM-Call ✅ done (2026-09-15; 2026-09-05, User; SOLL-Korrektur 2026-09-13: Skip statt Reset)

`AbstractAgent.compact()` bricht, wenn das Memory **weniger als 3 Nachrichten** enthält.

**WEIL (< 2 → < 3):** Ein Compact hinterlässt **genau 1 Message** (deterministisch, alle Agenten:
eine UserMessage mit den Turn-Context-Blöcken, deren **letzter** Content der Marker-Block
`Session compacted:` + Summary ist — R-CC-9). Bei Guard `< 2` war ein Re-Compact direkt nach jedem
Compact ein echter LLM-Call auf die Reseed-Historie (User-Smoke 2026-09-15: „Compressing
conversation 2 messages 124 tokens") — der Guard war strukturell tot. `< 3` macht **jeden Compact
direkt nach einem Compact zum Noop** (1 Message < 3).

**SOLL:** `memory.size() < 3` → **Skip** (`false` zurückgeben) — kein LLM-Call, **kein** Clear
(Memory bleibt unverändert). Gilt für **alle** Agenten inkl. Jon und seine Sklaven (Plan,
Dev, Review), **zentral in `AbstractAgent`** — damit alle 4 Eintrittspfade gedeckt sind
(Action-Bar-Hammer, Sklaven-Header-Button, `compactSession`, `compactPlan/Review/Dev`).
Der bestehende UI-Guard in `AIChatView.doCompressContext` (`size < 3`) bleibt als billiger
Short-Circuit bestehen.

**Ehrliche Skip-Meldung (kein Misreport):**
- `CompactSessionTool` (Jon): `"Not needed only N message in context"` — bestehendes Pattern, an `< 3` angepasst.
- `PoDelegateTool.compactPlan/compactReview/compactDev` (Sklaven): liest den **Boolean-Return** —
  Skip → `"Nothing to compact (N messages)"`, niemals `"X compacted."` (löst den Open-Point
  PoDelegateTool-Misreport, PoDelegateTool.java:191-195).
- UI Sklaven-Button ([agenten-status-im-header.md](../../org.sterl.llmpeon/docs/agenten-status-im-header.md) Regel 4):
  `compactResult(false)` → Statuszeile `Nothing to compact`.
  *(Historisch: R16 sagte ursprünglich „Reset statt LLM-Call" — der Code skipt korrekt ohne Clear;
  2026-09-13, User: „der skip ist richtig, die docs sind da falsch".)*

- **GIVEN** Jon hat 0, 1 oder 2 Nachrichten im Memory **WHEN** `compact()` aufgerufen **THEN** kein LLM-Call, Memory bleibt unverändert (`false`)
- **GIVEN** ein Agent wurde gerade komprimiert (Memory = exakt 2 Messages) **WHEN** `compact()` erneut aufgerufen **THEN** Noop — kein LLM-Call, `false`
- **GIVEN** Dev hat 2 Messages im Memory **WHEN** `compactDev()` aufgerufen **THEN** Skip und Tool-Result ist `"Nothing to compact (2 messages)"`, nicht `"Dev compacted."`
- **GIVEN** Jon hat ≥ 3 Nachrichten **WHEN** `compact()` aufgerufen **THEN** normaler Compact-Flow
- **Tag:** unit (verify no LLM call when < 3 messages; verify memory NOT cleared; verify PoDelegateTool surfaces the skip verbatim)

**Historischer IST-Bug (2026-09-05):** `compact()` rief `super.compact(monitor)` ohne Guard —
brach bei < 2 Messages. Slaven-Compacts (`compactDev`/`compactPlan`) hatten denselben Mangel.

### R4: Onboarding tutorial ❌
On the first activation in a session (`memory.size == 0`) Jon shows a short tutorial message (like
Peon-Scaffold). Later activations in the same session only refresh standing orders.

**BDD:**
```
GIVEN Jon is activated for the first time in a session (memory.size == 0)
THEN a short tutorial message appears in the chat history

GIVEN Jon already has chat history
WHEN the user switches away and back
THEN no tutorial is shown again
```

### R5: Design → approval gate, incremental status ❌
Jon designs and discusses the feature **in the docs** first. He captures a new feature as a story
(goal + business rules + BDD), marks every not-yet-built rule **❌ (WIP)**, and only **after the user
is satisfied** asks *"Shall I implement this?"* before delegating a build to Plan or Dev. (Asking the
Plan agent a **question** via `jonAskQuestion` is design work, not a build — it needs no gate.) When an
implemented slice is confirmed green, Jon flips its rules **❌ → ✅**.

**Prompt-Behaviour — kein BDD hier:** das Approval-Gate („Shall I implement this?“) und der
❌ → ✅-Flip werden von `po.txt` + `po-delegation.txt` gesteuert (Content im Repo, siehe Prompts).

### R6: Delegate to Plan — `planWithPlanAgent` (build) & `talkPlan` (Q&A) ✅
(Tool-Names I2.1: `jonCreateDevPlan` → `planWithPlanAgent`, `jonAskQuestion` → `talkPlan`.)
Both tools drive the **same** persistent Plan (R9), lazily created on first use and run for
**one turn** via `slave.call(prompt, monitor)` (modeled on `SearchAgentTool`, but against the
persistent Plan — so its memory, auto-compact and standing orders are reused). They differ only in
intent and framing:

- **`jonCreateDevPlan`** — the full planning workflow. The Plan agent interviews **Jon** in place of
  the user: each Plan question comes back as the tool result, Jon answers with the next
  `jonCreateDevPlan` call (a question Jon can't decide → escalate, R13). When the plan is ready Plan
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
Instead, on every dispatch Jon feeds Plan and Dev context as **standing orders** through the
`PoDelegateTool` additional-context supplier (`setAdditionalContext`), while his actual prompt rides
in as a normal **chat
message**. The key standing order is the **plan link**, once a plan exists. **Plan and Dev** are handled
the same way: `plan link` (standing order) **+** Jon's question (chat message).

Crucially, Jon does **not** forward his *own* standing orders (his `AGENTS-PO.md`, R1) to Plan and Dev —
those are Jon's identity. Jon sets **separate** standing orders **for** each slave. (A richer
cross-agent shared context Jon curates via a tool is a **future** extension, not the MVP.)

Dev additionally gets the instruction to call **`planImplemented()`** when finished (R8). On
`planImplemented()` Jon has his done-marker (plan flipped to *done*) and reviews the result, then either
accepts it (flip ❌ → ✅, R5) or returns change requests via `jonAskDev`.

**Post-dev review of larger plans is delegated, not done by Jon himself:** after a completed dev cycle
Jon charges the **Plan agent** with a review + **gap analysis** (built code vs. plan & docs) — via
`jonAskQuestion` for a pure gap analysis, or `jonCreateDevPlan` if the plan itself must change. For
small changes Jon reviews inline; the dedicated Reviewer agent stays a future extension.

**BDD:**
```
GIVEN Jon calls jonAskDev with a released plan
THEN Jon sets a standing order (via the PoDelegateTool additional-context supplier) carrying the plan link plus the instruction to call planImplemented() when done, and his prompt arrives as a chat message
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
- **`planComplete()`** — **Plan**: the plan is ready; a **pure** signal (no file I/O) that
  sets the done-latch carrying the **link to the plan**. The plan file stays — Dev needs it.
- **`planImplemented()`** — **Dev**: implementation is done. **Atomic:** in one step it
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
| Plan | ✅ | — |
| Dev | — | ✅ |
| Jon | — | — |
| standalone Plan / Dev | — | — |

Dev never sees `planComplete`, Plan never sees `planImplemented`, and **Jon has no
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
Per Jon session there is **exactly one** Plan and **one** Dev instance. Each is created
**lazily on first delegation** and **kept alive**, holding its context across calls.
Peon-Search stays a stateless **one-shot** agent.

These are **dedicated, Jon-owned instances** — **not** the user-selectable Plan/Dev from
`AgentService`, and are **RAM-only — no history files** ([ADR-0024](../../docs/adr/0024-po-slaves-ram-only.md)). Sharing the *instance* would make the `ask*`/`buildWithDev` tools mutate
the very memory/history the user sees, and the per-agent `working` guard would silently **queue** the
nested `call()` (returning `null`) — so Plan and Dev must be distinct instances.

**Tool wiring per layer (Plan and Dev are not empty shells).** A dedicated instance still needs a
fully-wired `ToolService`. In **core** it is built with the headless disk tools; in the **Eclipse
plugin** Plan and Dev must receive the **Eclipse-workspace tools**, which are assembled only in the
plugin (`PeonAiService`, `sharedToolService = new ToolService()` + `EclipseWorkspaceWriteFileTool` …).

**Shell-Tool-Regel:** Jon und Plan bekommen **keine Shell-Tools**.
Dev **bekommt Shell-Tools** — er ist der Builder, vergleichbar mit standalone Dev.
Die Shell-Bestätigungs-Konfiguration (`PREF_SHELL_CONFIRMATION_ENABLED`) hat drei Modi: `always`,
`never`, `not-autonomous`. **Dev gilt als autonom** (er arbeitet im Hintergrund für Jon, kein
direktes User-Interface) → `not-autonomous` unterdrückt die Bestätigung für Dev. Standalone
Dev ist *nicht* autonom → bei `not-autonomous` wird der User gefragt.

Two ways, both keeping core clean: **share the same plugin-built `ToolService`** the standalone
Plan/Dev already use (distinct *agent instance*, shared *tool set*), or **participate in the same
tool-`add` step** in `PeonAiService` that builds it. Either way the wiring is **injected per layer via a
slave factory** — core supplies disk-tool slaves for tests, the plugin supplies Eclipse-tool slaves —
so Jon-in-core stays testable without an Eclipse runtime.

**Sklaven-Tool-Filterung (`getToolFilter`):** Strippt `WorkspaceMemoryTool` (nur Jon curates Memory)
und `AskUserTool` (Jon ist das UI, nicht die Sklaven). **ShellTool bleibt erhalten** — Dev braucht
es als Builder. Die Shell-Bestätigung wird über den `autonomous`-Flag gesteuert, nicht über den
Tool-Filter.

**BDD:**
```
GIVEN no plan slave exists yet
WHEN Jon makes his first jonCreateDevPlan or jonAskQuestion call
THEN a single Plan instance is created and reused for every later Plan-side call in the session

GIVEN Jon uses SearchAgentTool
THEN it runs one-shot and holds no context between calls

GIVEN Jon's Plan slave (Plan) is created
THEN Plan's effective tool set does NOT include ShellTool

GIVEN Jon's Dev slave (Dev) is created
THEN Dev's effective tool set includes ShellTool
AND Dev gilt als autonom für die Shell-Bestätigung (`not-autonomous`-Modus unterdrückt die Frage)

AND the standalone Dev (user-selected) still has ShellTool and is NOT autonomous

Status: 🚧 `PeonAiServiceTest.test_po_slaves_shell_tool_policy` (neuer Test needed)
```

### R10: Just-in-time compaction of the slaves ❌
A slave is compacted **only just before Jon sends it the next message**, and only when its context
exceeds a threshold — a single constant, **default 60 %** (to be fine-tuned later). Implemented by
calling `slave.compressContext(monitor)` before dispatch. Jon's message is delivered as a **standing
order** (like a `/` command) so it **survives the compaction** and is placed **before** the compact
result (`ToolLoopRequest.clearMemory()` re-injects standing orders after clearing) — Plan and Dev never
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

**Stop** stays the abort control ([ADR-0018](../../docs/adr/0018-abort-path-parity.md)): it cancels via the shared
monitor (below) and the queue **drains to memory** without auto-firing (queue Rule 4), so no intent is
lost and no quota is burned.

**One monitor, one cancel chain (MVP).** Jon passes **his own** `monitor` — the one threaded into the
tool from `ToolLoopRequest` (as `CompactSessionTool` already receives it) — **straight into**
`slave.call(prompt, monitor)` (R6). No separate sub-monitor, no cancel-token translation: a single
**Stop** therefore cancels the whole chain — Jon's turn **and** the running slave. The accepted
trade-off is that, because it is the same monitor / `StreamingBridge`, the slave's messages surface
**live in Jon's chat** (transparency — you see what Plan/Dev are doing) and the slave's tokens flow
through the same choke point into the session total ([ADR-0004](../../docs/adr/0004-session-token-accounting.md)),
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
While Jon is active, the header shows — next to the usual sent/received token counts —
`dev(context-size)` and `plan(context-size)`, each with a status ball (active / waiting /
idle). Exact ball colours are deferred.

**BDD:**
```
GIVEN Jon is active with a live plan and dev slave
THEN the header shows plan(context-size) and dev(context-size) with their status balls
```

### R13: Escalation to the user (anti-deadlock) ❌
Jon answers Plan and Dev's questions himself as long as he can decide from the docs. A question he
**cannot** answer (a genuine user decision) he does **not** guess — he **ends his own turn** with the
question to the user. Jon is then no longer *working*, so the user's answer is submitted directly (or,
if it was typed while Jon was still finishing, it was queued and chains next — R11). Because Plan and Dev
are persistent singletons (R9), Jon **resumes** the paused slave with the answer via the next
`jonCreateDevPlan` / `jonAskDev` call.

This is the rule that prevents the hang: Jon **never blocks inside the tool-call loop waiting for the
user** — escalation is always "end turn, resume later", which the persistent slaves make possible.

**Prompt-Behaviour — kein BDD hier:** die Anti-Deadlock-Disziplin (Turn enden, eskalieren, denselben
persistenten Slave mit der Antwort resümieren) steuert `po-delegation.txt` (Content im Repo, siehe
Prompts); die Queue-Mechanik dahinter ist R11.

### R14: Slave failures come back as an error tool-result; Jon reports them (MVP) ❌
Two failure origins must be told apart — and the existing framework already handles them, so the MVP
adds **no** error plumbing, only a **guardrail standing order**:

**(a) Slave technical failure — `poagent` delegate tools are "agent tools" and deviate from the normal tool error
contract.** *(Tool-Ergebnisse: `jon*`-Namen in den BDDs unten sind historisch — gemeint sind die
Delegate-Tools.)*

*The normal contract, which we do NOT touch.* A standard tool signals a **typical AI error** by
throwing `IllegalArgumentException`; the framework's `SmartToolExecutor` catches it, reports it via
`monitor.onProblem(...)` and **returns the message as the tool result**, so the **tool loop is not
broken**. An **unexpected system error** is *not* handled there and escapes toward the **UI**. We change
**none** of this — the `ToolService` keeps its own error handling. (There is no dedicated tools doc yet;
the contract lives in `SmartToolExecutor` — a small `tools.md` could capture it later.)

*Why Jon's tools differ.* For a normal tool the ultimate sink of an unexpected error is the **human
UI**. But when Jon drives a slave, **Jon _is_ the UI** — a slave failure must land at **Jon**, not
escape past him into the ToolService default handler and on to the real UI. So the `poagent` delegate
tools **catch every exception themselves** and:

- **report it via `monitor.onProblem(...)`** — the user sees it live; and
- **return it to Jon as the tool result**, carrying: the **caught exception's message**, the **root
  cause** (unwrapped down `getCause`: message + **first ~5 stack lines**, a truncated
`StringUtil.getStackTrace`, not the full trace), and an explicit note that **the slave ran into an
  error and its state may have changed**.

One string, two sinks — `onProblem` (UI, immediate) and the tool result (Jon's context): **user and Jon
see exactly the same**; the full stacktrace stays in the log only. e.g. *"Plan ran into
an error — its state may have changed. Caught: `<msg>`. Root cause: `<root msg>` / `<first ~5 stack
lines>`. Inform the user; do not retry."*

Because a delegate tool **always returns** (it never throws), its `tool_use` always gets a matching
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
path** ([ADR-0018](../../docs/adr/0018-abort-path-parity.md)) surfaces it to the **real UI** — which is correct,
since here the failure is Jon's, not a slave's.

**Known nuance (future):** Plan and Dev are persistent (R9), so a slave turn that fails *after* its prompt was
added leaves that prompt reply-less in the **slave's** memory; on the user-driven retry Jon re-dispatches
and the slave continues from there — fine for MVP. Clean rollback and **Jon-level recoverable-error
handling** (rate-limit backoff, or a *summarised* retry so Jon can adapt) are deferred.

**BDD:**
```
GIVEN Jon called planWithPlanAgent and the Plan slave throws a timeout mid-plan
THEN the delegate tool CATCHES it itself (the ToolService default error handler never fires) and builds a concise message (caught message + root-cause message + first ~5 stack lines + "slave ran into an error, state may have changed")
AND it returns that message as the tool_result AND pushes the SAME string via monitor.onProblem so the user sees exactly what Jon sees
AND because the tool returns (never throws), the tool_use/tool_result pair is well-formed (no dangling tool_use)
AND Jon summarises the failure to the user and does not silently retry

GIVEN Jon's OWN model call fails (e.g. a rate limit on Jon's request)
THEN it propagates via AbstractAgent.call → handleAbortAndDrain → rethrow (ADR-0018), draining Jon's queue to memory, and no partial turn dangles

GIVEN a slave returns plain text (a clarifying question), not an exception
THEN it is a normal tool result (R8), not treated as an error
```

### R15: Build-Zyklus auf Git-Branch — Commit + Push je Iteration, Squash-Merge als Default ✅ (Commit 2026-08-28; Push/Message/Merge + nur im Jon-Modus 2026-10-28; prompt-basiert, kein BDD)
Ein Build-Zyklus (ein Plan/Feature) läuft auf einem **dedizierten Git-Branch** — nur wenn Git
im Workspace verfügbar und wir tatsächlich auf einem Branch (sonst: **kein** Auto-Commit,
Jon fragt den User). **Zu Zyklus-Start** prüft Jon den aktuellen Branch und ist bei
main/master **zuerst** auf einem Feature-Branch (anlegen/wechseln, sonst User fragen), bevor
er Dev los schickt. Der **Dev-Agent** committet nach **jeder erfolgreichen (grünen) Iteration**,
Scope = die Dateien dieser Iteration — jeder Schritt bleibt revertierbar (`git revert`), die
Base-Branch bleibt während des Zyklus unangetastet.

**Auto-Commit nur im Jon-Modus (2026-10-28, Paul).** Automatisch committet der Dev-Agent nur in
einem Build-Zyklus, den Jon steuert (`buildWithDev`). Bei `askDev` committet er nur, wenn Jon es im
Auftrag sagt. Arbeitet der User direkt mit dem Dev-Agenten, committet, pusht und merged dieser **nie**
von sich aus, nur auf ausdrückliche Bitte. **WEIL** der User im Direktbetrieb selbst über seine History
entscheidet; Jon steuert seinen Zyklus.

**Commit, Push, Merge (2026-10-28, Paul — Plugin und Web).** Defaults — Jon oder der User können
jederzeit anders entscheiden, und Jon denkt mit, wann ein Default nicht passt:
- **Message = kurze Zusammenfassung des fachlichen Effekts + IDs**, für LLM und Mensch beim Blick in die
  History: `inc-N: <Effekt> (R-…, UC-…)`, Body höchstens 2 Zeilen (Abweichung/Grund). Keine Dateiliste.
- **Nacharbeit = eigener Commit** (Review-Delta, Fix nach Rückfrage) mit dem, was nachgearbeitet wurde —
  kein amend/squash im Zyklus.
- **Push direkt nach jedem Commit** (auch nach dem Archiv-Commit), nicht erst nach Jons Abnahme — der
  Push sichert, freigegeben wird mit dem Merge. Kein Remote / Push schlägt fehl ⇒ Dev meldet es, der
  Zyklus läuft weiter, Jon gibt es an den User weiter.
- **Merge auf main nur im Auftrag des Users** (meist über Jon). **Default Squash-Merge**; sein Text ist
  allein aus den Commit-Messages des Branches ableitbar — niemand muss dafür Diffs lesen. **Kein Squash**,
  wenn Commits einzeln gebraucht werden (Cherry-Picks, mehrere Branches): dann normaler Merge und Branch
  gleich löschen. Nach einem Squash fragt Jon, ob der Branch gelöscht wird, wenn der User nichts gesagt
  hat — mit eigener Empfehlung.
- `CHANGELOG.md` ist nur das Release-Log für den User, keine Zweitschrift der Commits.

**WEIL** auf einem frischen Fabrik-Node alles Ungepushte weg ist
([aifactory R-CR-8](../../aifactory/docs/config-repo.md)) und die History ohne Diffs lesbar sein soll.

**Kein Prompt-BDD:** operativ in `dev-build-loop.md` + `developer.md` (Dev: Commit/Push) und
`po-delegation.md` (Jon: Branch, Merge) — gleicher Text für Plugin und Fabrik.

### R17: Jon gets `askUser` — matched user answers ✅ (2026-09-05, User)

Jon bekommt das bestehende `AskUserTool` ([user-question-tool-design.md](../../org.sterl.llmpeon/docs/user-question-tool-design.md))
in seine kuratierte `poToolService`. Seine Fragen (Approval-Gates, 🔒-Bestätigungen, echte SOLL-
Entscheidungen) zeigen das Question-Widget; die User-Antwort ist **matcht** zu genau dieser Frage
(queue-safe — Input-Bereich ist während des Wartens ersetzt), keine beliebige queued Message.
Cancel via Stop → Cancel-Error (cancel-safe, kein Deadlock).

**Unverändert:** Slaven (Plan/Dev) + Search-Agent behalten den bestehenden `AskUserTool`-
Filter (R9: „Jon ist das UI, nicht die Sklaven").

**R13-Klärung:** R13s „never blocks inside the tool-call loop" bezieht sich auf die **Slave-Fragen-
Eskalation** (Turn enden, später resümieren). Direkte User-Entscheidungen dürfen via `askUser`
blocken (diese Regel) — cancel-safe via Stop.

- **GIVEN** der User schaltet auf Jon **WHEN** Jons ToolService gebaut wird **THEN** sie enthält `askUser` zusätzlich zu den bestehenden kuratierten Tools
- **GIVEN** Jon hat eine echte User-Entscheidung (Business-Choice, unklarer Use-Case, Zielkonflikt) **WHEN** er `askUser` aufruft **THEN** das Question-Widget erscheint und die eingereichte User-Antwort ist das Tool-Result
- **GIVEN** Jon ruft `askUser` mit Predefined Answers auf **WHEN** der User eine auswählt (per Free Text verfeinerbar) **THEN** der ausgewählte Text wird zurückgegeben
- **GIVEN** der User drückt Stop während eine Frage wartet **WHEN** der Latch released wird **THEN** das Tool liefert einen Cancel-Error und Jons Loop läuft weiter (kein Deadlock)
- **GIVEN** Plan/Dev werden erstellt **WHEN** ihr effektiver Tool-Set berechnet wird **THEN** `askUser` wird weiterhin gefiltert (R9 unverändert)
- **Tag:** unit (verify poToolService contains AskUserTool; verify slaves still filter it)

### R18: Compact = nur der Agent selbst — kein Cascade ✅ done (2026-09-14, Zyklus story/po-compact-2026-09-13; Decision 2026-09-13, User)

`AiPoAgent.compact()` komprimiert **ausschließlich Jon selbst**. Der implizite Slave-Cascade
(sequenziell `compactPlan` → `compactReview` → `compactDev` über `PoDelegateTool`) wird **entfernt**.

**WEIL:** Die Slave-Memories sind unabhängig und managen sich ohnehin selbst — Jons Delegation
compactet einen Sklaven **vor** jeder neuen Aufgabe, sobald sein Kontext über 70 % des Budgets liegt
(`PoDelegateTool.compactSlaveBeforeDelegation`, `SLAVE_COMPACT_FACTOR = 0.7`, [ADR-0071](../../docs/adr/0071-compact-policy-owned-by-delegation.md)).
Der Cascade war 3 sequenzielle LLM-Calls ohne Nutzen, unsichtbar im
UI („Hänger"-Gefühl, Dauer ∝ Kontextgröße) — vom User als ungewollter Seiteneffekt von Jons
`compactSession`-Tool eingestuft.

- Der UI-Compact-Button (Action Bar) auf Jon komprimiert nur Jon; Jons `compactSession`-Tool ebenso.
- Die **expliziten** Slave-Compact-Tools (`compactPlan`/`compactReview`/`compactDev`) **bleiben** —
  Jon (LLM) kann gezielt einen Slave komprimieren; neu dazu der **Header-Button pro Sklave**
  ([agenten-status-im-header.md](../../org.sterl.llmpeon/docs/agenten-status-im-header.md), ❌ specified).

**BDD:**
```
GIVEN Jon wird komprimiert (Action-Bar-Button oder compactSession-Tool)
WHEN compact() läuft
THEN wird NUR Jons Memory komprimiert — Plan/Dev/Review bleiben unverändert
AND es gibt keinen weiteren LLM-Call (kein Slave-Traffic, kein „Hänger")

GIVEN ein Slave hat über 70 % des Budgets im Kontext
WHEN Jon delegiert ihm die nächste Aufgabe (askDev/buildWithDev/talkPlan/reviewPlanAgent)
THEN compactet `PoDelegateTool` den Sklaven VOR dem Request (ADR-0071)
AND unter 70 % passiert nichts; `autoCompactAfter <= 0` schaltet den Delegations-Compact aus

GIVEN Jon fragt einen Sklaven ohne neue Aufgabe (nur ein Follow-up im selben Turn)
WHEN dessen Kontext über 95 % steigt
THEN kommt der In-Loop-Hint (R-CC-18) — ein Pre-Turn-Compact existiert nicht mehr
```

### R19: Clear-Cascade bleibt ✅ (IST bestätigt als SOLL, 2026-09-13, User)

`AiPoAgent.clear()` cleart **Jon UND alle drei Sklaven** (`clearPlan`/`clearReview`/`clearDev` über
`PoDelegateTool`) — im Jon-Modus räumt ein Clear alles weg (UI: `AIChatView.onClear` →
`PeonAiService.clear()`). Bewusst **asymmetrisch zu Compact** (R18): Clear ist die harte „Neu-
anfangen"-Aktion des Users, Compact ist Kontextpflege.

- **GIVEN** Jon ist aktiv **WHEN** der User Clear drückt **THEN** Jon- und alle Slave-Memories sind geleert (IST `AiPoAgent.clear()`, unverändert)
- **Homepage:** das Verhalten ist user-facing und wird dort dokumentiert (**✅ done** —
  `homepage/src/usage/agents.md` inkl. Sidebar-Eintrag, Zyklus story/po-compact-2026-09-13).

### R20: Jon nimmt an den dynamischen Tool-Familien teil — disk-Gate + MCP ✅ done (2026-10-15, specified 2026-10-15, Paul)

**IST (vermessen 2026-10-15):** Jon hat seine eigene kuratierte Registry (`BuildPoAgentComponent:
67`, `new ToolService(false)`, 12 `addTool`) — und genau die wird von beiden dynamischen Familien
**umgangen**: `McpConnectionService.connect` verbindet nur den `sharedToolService`
(PeonAiService.java:200 → ToolService.connectMcp:291-298 — *historisch: `connectMcp` existiert seit
[ADR-0069](../../docs/adr/0069-shared-mcp-connection-per-server.md) nicht mehr, die Registries adoptieren den
vom Connection-Service gehaltenen `McpService`*), `SharedToolsComponent.updateActiveDiskTools`
(:82-98) touched ebenfalls nur den `sharedToolService`. Folge: Jon sieht **niemanden** seiner
MCP-Server und **keine** disk-Tools — obwohl R2 ihm die disk read/write tools zuspricht und Pauls
SOLL lautet: **alle** Agenten bekommen MCP-Tools. (Indirekt sieht Jon MCP/disk nur über Search.)

**SOLL:** Beide dynamischen Familien adressieren **alle** agent-facing ToolService-Registries —
shared **und** po:

1. **MCP:** `McpConnectionService` verbindet/disconnectet MCP-Server an beiden Registries; Jons
   `mcpToolSpecs` füllt/leert sich wie beim shared-Service. Kein Filter-Change — die abwesende
   Allowlist heißt „alle" und `getToolNameFilter` deckt MCP-Namen ab.
2. **disk-Gate:** `updateActiveDiskTools` togglet die disk-Familie an **beiden** Registries
   (gleiche Konfiguration `diskToolsEnabled`, Clean Break, kein zweiter Schalter). Jon's
   Write-Validator (R3, `*/docs/*` + `*.md`) gilt am Choke-Point unverändert — auch für disk-Writes.

**WEIL:** Ein Agent, dessen Registry die User-Konfiguration (MCP-Server, disk-Gate) still ignoriert,
handelt auf der Überzeugung „Tool existiert nicht" — dieselbe teuerste Fehlerklasse wie ein
False-Negative der Read-Tools. Die Kuratierte-Registry-Entscheidung (R2) bleibt: Jon wählt seine
Basis-Tools, die **dynamischen** Familien kommen konfigurationsgetrieben hinzu.

```
GIVEN ein MCP-Server ist verbunden
WHEN Jons toolSpecifications gebaut werden
THEN sie enthalten die MCP-Tool-Specs des Servers
AND WHEN der Server getrennt wird, THEN verschwinden sie aus Jons Specs

GIVEN diskToolsEnabled = false
WHEN Jons toolSpecifications gebaut werden
THEN keine disk*-Tools
AND WHEN diskToolsEnabled = true gesetzt wird, THEN enthält Jons Set die disk*-Familie
AND ein disk-Write außerhalb von */docs/* + *.md wird weiterhin vom Write-Validator rejected
```
→ Tests je Richtung (Connect/Disconnect, Toggle on/off) gegen die po-Registry.

## Future Extensions (not MVP)

- **Reviewer** — a dedicated agent Jon dispatches to review a plan or the changed code against the
  docs when he is unhappy with Plan/Dev output. In the MVP Jon does this review himself (R6/R7).
- **Self-improvement via Skills (`jonAskScaffold`)** — a `jon*` tool driving a Peon-Scaffold team member so
  Jon can have Skills created/edited and thereby **improve himself over time**. Left out of the MVP
  (it needs its own team member in the lifecycle/header/error rules). This becomes its **own story / feature
  `.md`** — explicitly out of scope here, only flagged as the direction.
- **Cross-agent shared memory** — a tool with which Jon curates **extra** context shared across his
  Plan and Dev, beyond the per-dispatch plan link of R7. The MVP passes only the plan link as a standing
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

- [ADR-0020](../../docs/adr/0020-po-agent-orchestration.md) — Jon orchestrates Plan/Dev as sub-agents via the
  poagent delegate tools with `planComplete` / `planImplemented` completion signals (vs. the one-shot button handoff).
- [ADR-0021](../../docs/adr/0021-po-slave-lifecycle-jit-compaction.md) — team member lifecycle (eager persistent
  singletons) & just-in-time compaction with standing-order survival.
- [ADR-0022](../../docs/adr/0022-write-path-allowlist-decorator.md) — no bespoke Jon tools; the existing write
  tools are wrapped in a write-path-allowlist decorator driven by a comma-separated glob config
  (default `*/docs/*`).

Related: [Plan & Dev Agent](plan-dev-agent-design.md) — the standalone plan→dev handoff Jon builds on;
[Scaffold Agent](scaffold-agent.md) — the built-in-agent-with-own-ToolService pattern Jon reuses.
