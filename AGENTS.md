# AGENTS.md — LLM Peon

Peon AI is an Eclipse RCP plugin that runs AI agents (Peon-PO / Peon-Plan / Peon-Dev + custom
agents) inside Eclipse: chat UI, streaming, tool loop, sub-agent orchestration.
If you run in eclipse - your are building/improving yourself. Do a good job or you suffer!

**Working method:** docs-first PO cycle — the method itself lives in the **Jon skill**
(`https://github.com/sterlp/ai-skill-codex/tree/main/skills/jon`).

## Repo layout

| Module | What |
|---|---|
| `org.sterl.llmpeon.core` (artifactId `llmpeon-core`) | Non-Eclipse business logic + tests — plain Maven, JUnit 5, AssertJ, Lombok. Agent prompts in `src/main/resources/org/sterl/llmpeon/prompts/`. |
| `org.sterl.llmpeon` | Eclipse plugin: SWT/JFace UI + wiring (OSGi). |
| `org.sterl.llmpeon.test` | OSGi plugin tests — JUnit 4, no external assertion libs. |
| `releng/` | Tycho feature, target platform, update site. |
| `homepage/` | **User behavior/visible changes** need the update in the same increment. |

Module guides (read when working in one):
- `org.sterl.llmpeon.core/AGENTS.md` — core conventions
- `org.sterl.llmpeon/AGENTS.md` — plugin UI & logic patterns

## Architecture

- **Log OR throw, never both** (except facades where the exception leaves the context).
- Use a clean component architecture with proper information hiding / deep modules
- **Thread safety:** Eclipse plugin - heavy work on a background Thread `Job.create`, UI changes
  on a UI thread `EclipseUtil.runInUiThread` as so plan/build accordingly. 
  No single-threaded assumptions.

## Build cycles & git

- A build cycle runs on a **dedicated branch** named by the PO; only there does the Dev agent
  auto-commit. No git repo / not on a branch → **no auto commits, ask first**.
- After **each green increment**: If git is available and you are on a branch (not main/master) 
  commit automatically — unless stated otherwise — with message 
  `inc-N: <summary>` scoped to that increment's and an `Assisted-by: Peon AI (<ModelName>)` trailer in the body
  — every step stays revertable (`git revert`) without touching the main branch. 
  After `planImplemented` everything (incl. the archived plan) — repo clean for the next cycle.
- Final merge/squash into the base branch is the **user's** decision.

## Dependencies

- External JARs land in `lib/` via `maven-dependency-plugin`; `MANIFEST.MF` `Bundle-ClassPath`,
  `build.properties` `bin.includes` and `.classpath` must list the **same** JARs.
- Whitelist only the needed groupIds via `includeGroupIds`. Platform-provided JARs (jakarta,
  osgi, jna, asm, jetty, felix, …) must **not** be in `lib/` — they come from the target
  platform.

## Docs

Three trees, kept separate:
- `docs/` — the **SOLL**: feature stories (goal, business rules + BDD) and technical design
  docs in `docs/<feature>.md`, story registry `docs/index.md`, technical decisions in
  `docs/adr/` (`docs/adr/index.md` registry), cycle notes in `docs/memory.md`. An ADR is a
  technical decision that doesn't follow from a rule/BDD: `docs/adr/NNNN-<slug>.md`
  (Status · Context · Decision · Consequences) — it cross-links to the story, never repeats
  a rule or BDD. In Peon the docs are owned by the PO + the user — **no other agent writes to
  `docs/`**.
- `homepage/` — published end-user documentation (VitePress); 
- dev phase mechanics in `AGENTS-DEV.md`.

Start at `docs/index.md` for the full map before touching planning a feature.

## Reference and help working with eclipse building a good plugin
Use search agents to search these big repos - do direct reads only.
Save hard won facts / know how in /llmpeon-parent/skills/eclipse-dpe as SKILL use write SKILL
for eclipse know-how you didn't know before. if you are in plan mode, add a hint in the plan to update
the skill if needed.

1. check the SKILL directory if something was already saved here ...
2. use github eclipse plugin AI harness if problems or question arise which cannot be answered 
   by the API itself eclipse: /github-copilot-for-eclipse
3. /langchain4j-aggregator for langchain4j code & docs (*.md), if the API itself is not enough
4. Opencode source - cli AI harness: /opencode -- for generall idea how AI harnesses are build