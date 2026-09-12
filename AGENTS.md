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
- **A tool must never lie about a limit.** Every truncation, filter or scope restriction is named
  in its own output (`showing N of M`, `regex search` / `literal search`, "filtered by file type").
  A false negative — the tool says "not found" for something that exists — is the most expensive
  bug in this codebase: the agent then acts on the belief that the thing does not exist. An error
  is harmless by comparison. See `docs/eclipse-read-tools.md`.
- **One behaviour, one implementation.** The `eclipse*` and `disk*` tool families must not
  diverge; shared logic lives in core (`FileLines`, `SearchQuery`, `TextFileTypes`, `LogExcerpt`,
  `AiReponseBuilder`). A silently differing whitelist is a false negative waiting to happen.
- **Clean break over migration.** Config is rebuilt from stored values + defaults on every load;
  removed keys are ignored, never migrated or aliased. No migration chains, no stale state.
- **Empty means unset.** An empty config value sends *no* parameter — never a default. (Reason:
  GPT-5/o reject any `temperature != 1`; a "harmless default" breaks exactly those models.)
- Use a clean component architecture with proper information hiding / deep modules
- **Thread safety:** Eclipse plugin - heavy work on a background Thread `Job.create`, UI changes
  on a UI thread `EclipseUtil.runInUiThread` as so plan/build accordingly. 
  No single-threaded assumptions.
- Code structure komponenten-architektur SKILL

## Working agreements that cost us the most to learn

- **Maven Surefire is the ground truth for test numbers**, not `eclipseRunTests` — the Eclipse
  runner counts higher (parameterized artefacts) and has repeatedly produced wrong report numbers.
- **Read the API contract in the source, never guess it.** Dead guards survived here for months
  because they *looked* defensive (`PlatformUI.getWorkbench() == null`,
  `IProject instanceof IJavaProject`). A guard that never fires is worse than none.
- **A test that would also be green without the feature is not a test.** Details and the recurring
  shapes: `AGENTS-DEV.md` → "Test honesty".
- **Report, don't route around.** If a tool gives a surprising result, report query, scope and the
  expected file — never silently switch from `eclipse*` to `disk*`. That switch hides exactly the
  bug worth finding.
- Shell is for read-only diagnosis (`xxd`, `file`, `wc`), never for file I/O.
