# AGENTS.md — LLM Peon

LLM Peon is an Eclipse RCP plugin that runs AI agents (Peon-PO / Peon-Plan / Peon-Dev + custom
agents) inside Eclipse: chat UI, streaming, tool loop, sub-agent orchestration.

**Working method:** docs-first PO cycle — the method itself lives in the **Jon skill**
(`https://github.com/sterlp/ai-skill-codex/tree/main/skills/jon`).

## Repo layout

| Module | What |
|---|---|
| `org.sterl.llmpeon.core` (artifactId `llmpeon-core`) | Non-Eclipse business logic + tests — plain Maven, JUnit 5, AssertJ, Lombok. Agent prompts in `src/main/resources/org/sterl/llmpeon/prompts/`. |
| `org.sterl.llmpeon` | Eclipse plugin: SWT/JFace UI + wiring (OSGi). |
| `org.sterl.llmpeon.test` | OSGi plugin tests — JUnit 4, no external assertion libs. |
| `releng/` | Tycho feature, target platform, update site. |

Module guides (read when working in one):
- `org.sterl.llmpeon.core/AGENTS.md` — core conventions
- `org.sterl.llmpeon/AGENTS.md` — plugin UI & logic patterns

## Build & test

- Full build: `mvn clean verify` at the repo root (`llmpeon-parent`) — an Eclipse refresh +
  clean build afterwards is needed.
- **Core changes need `mvn clean verify` in `org.sterl.llmpeon.core`** so the plugin/test
  bundles pick up the new core — Tycho resolves core from the target platform, not the reactor.
- Core tests: `mvn -pl org.sterl.llmpeon.core test` (JUnit 5). `@Tag("integration")` tests are
  excluded by default — run with `-Pintegration`.
- Plugin tests: `org.sterl.llmpeon.test` via the Eclipse test runner (OSGi, JUnit 4).
  - Before EVERY test run call `eclipseBuildProject` (all changed projects) — stale bundle
    classes in `bin/` cause `ClassNotFoundException` / unresolved-compilation failures, and
    stale Surefire reports under `target/` mislead result reading.
  - A new test class needs manual workspace approval by the user and may time out if he is not
    watching — prefer the already approved suite.
- Compile-checking the plugin against local core changes: `mvn -o -pl
  org.sterl.llmpeon,releng/llmpeon-target -am package` — `releng/llmpeon-target` must be in the
  `-pl` list (offline the target-platform artifact is not in `~/.m2`; `verify` does not install
  it). Without `-am` Tycho resolves core from a stale target-platform copy and reports phantom
  "cannot be resolved" errors for brand-new core symbols.

## Build cycles & git

- A build cycle (one feature all plans / dev increments) runs on a **dedicated branch** — 
  and only then does the Dev agent auto-commit. 
  No git repo / not on a branch → **no auto commits, ask first**.
- After **each successful (green) increment**, the Dev agent commits with a short message
  (`inc-N: <one-line summary>`), scoped to that increment's files — every step stays
  revertable (`git revert`) without touching the main branch.
- Final takeover into the base branch (optionally squash-merged into one entry) is the
  **user's** decision.
- after a `planImplemented` all files and the archived plan should be committed. 
  Repo should be clean for the next increment.

## Dependencies

- External JARs land in `lib/` via `maven-dependency-plugin`; `MANIFEST.MF` `Bundle-ClassPath`,
  `build.properties` `bin.includes` and `.classpath` must list the **same** JARs.
- Whitelist only the needed groupIds via `includeGroupIds`. Platform-provided JARs (jakarta,
  osgi, jna, asm, jetty, felix, …) must **not** be in `lib/` — they come from the target
  platform.

## Code invariants

- **Thread safety:** all code must be thread-safe (`volatile` / `Atomic*` / `ReentrantLock`) —
  SWT jobs, streaming callbacks and the tool loop run concurrently. No single-threaded
  assumptions.
- Elegant, expressive modern Java (records, pattern matching, switch expressions, Lombok).
- **Log OR throw, never both** (except facades where the exception leaves the context).

## Docs

Three trees, kept separate:
- `docs/` — the **SOLL**: feature stories (goal, business rules + BDD) and technical design
  docs in `docs/<feature>.md`, story registry `docs/index.md`, technical decisions in
  `docs/adr/` (`docs/adr/index.md` registry), cycle notes in `docs/memory.md`. An ADR is a
  technical decision that doesn't follow from a rule/BDD: `docs/adr/NNNN-<slug>.md`
  (Status · Context · Decision · Consequences) — it cross-links to the story, never repeats
  a rule or BDD. In Peon the docs are owned by the PO + the user — **no other agent writes to
  `docs/`**.
- `homepage/` — published end-user documentation (VitePress); mechanics in `AGENTS-DEV.md`.
- `skills/` — agent skills (YAML frontmatter `description` + markdown body).

Start at `docs/index.md` for the full map before touching a feature.

## Phase files

- `AGENTS-PLAN.md` (planning), `AGENTS-DEV.md` (implementing) — project-specific additions on
  top of the Jon skill's method. The Peon plugin auto-loads `AGENTS-<agent>.md` for the
  **active** Peon-PO/Plan/Dev (key = name after "Peon-", uppercased); Jon's slave agents
  currently receive this base file only. Any other tool opens them manually and follows the
  Jon skill.

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