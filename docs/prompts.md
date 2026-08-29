# Prompt Inventory & Wiring

**✅ done (2026-08-29).** One map of Peon's built-in prompt files: which file, who loads it, when,
and why the split.

## Policy (R1)

- **Content = repo (SOT).** Prompt files live in
  `org.sterl.llmpeon.core/src/main/resources/org/sterl/llmpeon/prompts/`. The docs never mirror
  prompt content and carry **no BDDs against prompt text** — prompt-driven behaviour rules in the
  feature stories reference the file instead (e.g. [po-agent-jon.md → Prompts](po-agent-jon.md)).
  Any agent (or the PO) reads the prompt directly when it needs the wording.
- **Docs = wiring.** This story records which file exists, who loads it, when, and why the split —
  update it whenever a prompt file is added/removed or its load site moves.

## Loader mechanics

- `PromptLoader.load(filename)` — reads `/org/sterl/llmpeon/prompts/<filename>` (UTF-8); missing
  file → `IllegalStateException`.
- `PromptLoader.loadWithDefault(filename)` / `withDefault(body)` — prepends `default.txt` (the
  shared base) to the given body; used for the built-in agent system prompts and for custom-agent
  markdown bodies (`CustomAgent`).
- `PeonPaths.resolve(text)` — resolves `${docs}` / `${plan}` placeholders so the paths live in one
  constant, not in the prompt text.

## Inventory (12 files, verified against the code call sites, 2026-08-29)

| File | Loaded by | When | `default.txt` prepended | Why it exists (1 line) |
|---|---|---|---|---|
| `default.txt` | `PromptLoader` (static) | always — the shared base | — | common base behaviour for every agent |
| `po.txt` | `AiPoAgent` (static, class-init) | always — Jon's system prompt | yes | Jon's identity + docs-first methodology |
| `po-delegation.txt` | `AiPoAgent` (static) | always — appended after the path-white-list line | no | delegation playbook (plan → sign-off → build → review → retro, cycle/branch discipline) |
| `po-tutorial.txt` | plugin `PeonAiService` | first activation in a session ([po-agent-jon.md](po-agent-jon.md) R4) | — | Jon onboarding message |
| `developer.txt` | `AiDevAgent` (static) | always — Peon-Dev system prompt (standalone **and** Da Mek) | yes | Dev identity + build method |
| `dev-build-loop.txt` | `PoDelegateTool.buildWithDev` | per build dispatch with a (sticky) planPath | no | build discipline: vertical slices, green gate, git commits, compactSession — never on `askDev` |
| `planner.txt` | `AiPlanAgent` (static) | always — Peon-Plan system prompt (standalone **and** Da Thinka) | yes | plan identity + planning method |
| `plan-write-loop.txt` | `PoDelegateTool.planWithPlanAgent` | one-shot standing order per plan dispatch | no | plan-writing discipline (small green increments, plan continuously, ask — never guess) — never on `talkPlan` |
| `compressor.txt` | `AiCompressorAgent` (static) | every compaction run | no | compaction method |
| `search-agent.txt` | `SearchAgentTool` | per `searchAgent` call (stateless one-shot) | no | Da Sniffa: read-only research agent |
| `scaffold-agent.txt` | `AiScaffoldAgent` (static) | always — Peon-Scaffold system prompt | yes | scaffold identity (config artefacts: agents/skills/commands) |
| `scaffold-tutorial.txt` | plugin (`PeonAiService` / `AIChatView`) | first activation of Peon-Scaffold | — | scaffold onboarding message |

## Why the split

1. **Identity** (`po.txt`, `developer.txt`, `planner.txt`, `scaffold-agent.txt`) — static, small,
   per-agent; loaded once at class-init; stays clean because the operational content lives
   elsewhere.
2. **Playbook** (`po-delegation.txt`) — operational, changes more often than identity; appended,
   not merged, so identity and playbook evolve independently.
3. **Loop discipline** (`plan-write-loop.txt`, `dev-build-loop.txt`) — **one-shot standing orders**
   bound to the *work* verbs: injected only when the slave actually writes the plan / builds from a
   released plan, so talk/ask turns stay lean; as standing orders they survive the slave's own
   compaction.
4. **Tutorial** (`po-tutorial.txt`, `scaffold-tutorial.txt`) — UI-level onboarding; wired in the
   **plugin** (chat view), not in core.
5. **Shared base** (`default.txt`) — the common behaviour every agent gets, prepended once by the
   loader (including custom-agent bodies).

Related: [Peon-PO (Jon)](po-agent-jon.md) (the Jon prompt files in detail),
[Sub-agent tool timing](sub-agent-timing.md), [Search Agent Tool](search-agent-tool.md).
