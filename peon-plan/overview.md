# Implementation Plan — Feature "Project Skills" (docs/project-skills.md, ADR-0042, ADR-0043)

> **SOLL = docs/project-skills.md (authoritative, R1–R13) + ADR-0042 + ADR-0043. Read them first.**
> **⚠️ FOR DA MEK — STOP-AND-ASK (prominent):** If you hit a compile error you cannot resolve, a
> test that cannot be made green, an IST fact contradicting this plan, or anything ambiguous —
> **STOP and ASK Jon actively via your askDev channel. Do NOT work around silently, do NOT change
> the SOLL.** Jon explicitly offered help and wants to be asked.

## 0. Branch & commit discipline
- Verify branch first: `git branch --show-current`. If on `release-2026-09-06` or `main`, create/switch
  to **`project-skills-2026-09-08`**; if the branch already exists locally from earlier, continue on it.
- Commit after **every** green increment: message `inc-N: <summary>` + trailer
  `Assisted-by: Peon AI (<ModelName>)` — include docs/** and prompt file changes in the same commit.
- Maven Surefire = ground truth for core test numbers; plugin (OSGi) tests need
  `eclipseBuildProject` on changed projects **before** each run (stale bin/ → ClassNotFoundException).

## 1. Context
Skills live today only in `<configDir>/skills` (`~/.peon/skills`). The feature adds project-local
skills in `<project-disk-path>/.agents/skills` used alongside config skills; name collisions are
won by the project variant (read-time override, ADR-0042). The scaffold agent may create skills in
`.agents/skills` of all open projects; the validator gets dynamic roots (ADR-0043); the UI shows the
source (config vs project) in menu/counter/autocomplete.

## 2. IST — verified facts (do NOT re-research)
- `SkillService` core `org.sterl.llmpeon.skill.SkillService.java`: single `volatile Path skillsDirectory`,
  `ConcurrentHashMap skills`, `refresh(Path)` does `skills.clear()` BEFORE repopulate (R5a bug), map key
  = lowercase name. API: `getSkills()` (enabled-filtered), `getAllLoadedSkills()`, `get(name)`,
  `setSkillEnabled`, `setAllSkillsEnabled`, `setEnabled/isEnabled` (service-level), `skillNames()`,
  `loadedSkillCount()`, `hasSkills()`.
- Enabled-state today lives **on the instance** (`SimplePromptFile.enabled`, volatile boolean) — must move
  to name-keyed state in the service (R1).
- `SkillPromptFile extends SimplePromptFile` (`skill/SkillPromptFile.java`): `volatile Path skillDir`,
  `renderBody()` prints `=== SKILL: name ===` header, `buildShortInfo()`, `readRelativeFile(path)`.
- Consumers (all keep working, no signature changes): `SkillTool` (`tool/tools/SkillTool.java` — uses
  `get/getSkills/skillNames`; output = `buildShortInfo()` join), `SlashCommandResolver.resolve(raw,
  commands, skills)` (`command/SlashCommandResolver.java:26`, uses `skills.get(candidate)` — disabled
  skills still resolvable), `ReloadConfigTool` (`scaffold/ReloadConfigTool.java` L52–53
  `skillService.refresh(configDir.resolve(SKILL_DIRECTORY))`), plugin: `AIChatView` L162–165
  (menu supplier `getAllLoadedSkills`), L174–179 (slash supplier `getSkills`), L332 refreshStatusLine
  (counter = `getSkills().size()`), L657–668 toggles; `PeonAiService` (L116 `new SkillService()`,
  L144 `new SkillTool(skillService)` scaffold, L207–210 `updateConfig` refresh, L245–261 `setProject`
  choke-point); `SharedToolsComponent` ctor `addTool(new SkillTool(skillService))` L40.
- `PeonAiService.setProject` (L245–261): `agentContext.clearPlan()`, workspace tools
  `setCurrentProject`, disk tools `setWorkingDir(JdtUtil.diskPathOf(project))`, then
  `userContext.setCurrentProject(project)`. Pin action does **NOT** call `setProject`
  (`AIChatView.onPinChange` L610–618 only flips the flag and re-setProject on unpin-with-selection).
- `AgentContextComponent.appendScaffoldContext` (plugin `parts/ai/component/AgentContextComponent.java`
  L144–175): today config-dir listings + tool names, **no project/open-projects info** (R2d gap).
  `userContext.get()` is appended at turnContext L140 for all agents.
- Scaffold `AiScaffoldAgent` (core `scaffold/AiScaffoldAgent.java`): own `ToolService(false)`, own
  `DiskFileRead/Write/GrepTool(configDir)`, WebFetchTool; `call()` re-pins workingDir=configDir per
  call. **No `getWriteValidator()` override today** (inherits `AiAgent` default ALLOW_ALL,
  AiAgent L52–54). Only `AiPoAgent` overrides (DOCS, `poagent/AiPoAgent.java` L91–94).
- `WriteValidator` (`tool/WriteValidator.java`): `validate(String)`, `ALLOW_ALL`, `DOCS =
  AllowlistWriteValidator("*/docs/*", "*.md")`. `AllowlistWriteValidator` holds static glob list,
  validates the **normalized raw path** via `FileUtils.normalizeSegments` + `RegexUtils.globToPattern`
  (only `*` special, case-insensitive, anchored; caches per glob — fine for dynamic roots, do NOT
  build globs per root at validate-time from untrusted data). Enforcement choke-point:
  `AbstractTool.validateWrite(path)` (L26–28) called by `DiskFileWriteTool.resolve()` (L217–220) → all
  write methods. `FileUtils.resolve(base, path)` lets absolute paths escape workingDir — the validator
  is the only hard gate.
- Tests today: core `skill/SkillServiceTest` (Jimfs `AbstractMemoryFileTest`, static `subject` +
  shared `tmp`), `tool/SkillToolTest`, `tool/DisabledSkillTest`, `tool/AllowlistWriteValidatorTest`,
  `tool/ToolLoopRequestWriteValidatorTest`, `scaffold/AiScaffoldAgentTest` (2 trivial tests),
  `scaffold/ReloadConfigToolTest` exists. Plugin: `PeonAiServiceTest` (extends
  `AbstractIntegrationTest`, OSGi JUnit4, imports `PeonTestFixture` project, creates real
  `PeonAiService` with tmp `.peon-test` configDir, `aiService.setProject(project)` in @Before).
- Existing plugin utilities for R2d/R10: `EclipseUtil.openProjects()` (`parts/shared/EclipseUtil.java`
  L293–302) and `JdtUtil.diskPathOf(IResource)` (L165–172, null-safe). Both are the reusable pieces.
- `SimplePromptFile` has `@Setter @Getter volatile boolean enabled` — the per-instance enabled flag.

## 3. Design decisions
1. **SkillSlot component (core, new class `skill/SkillSlot.java`)** — deep module; identity = path.
   - Fields: `volatile Path dir`, `volatile Map<String,SkillPromptFile> skills` (immutable map),
     `SkillSource source` (enum CONFIG/PROJECT — lives in SkillPromptFile or own file, dev's choice,
     keep simple). Methods: `path()`, `setPath/setPathAndRefresh` (build new map fully, then swap
     reference — atomic, R5), `refresh()` (full rebuild + swap; on IOException keep previous map and
     rethrow for the caller to report — R5a), `skills()` (current map view), `loadedSkillCount()`.
   - Discover+parse logic (flat `*.md` + `SKILL.md`/`skill.md` dirs, `PromptYmlParser`) moves from
     SkillService into SkillSlot (one behaviour, one implementation).
   - Missing dir / null path = empty map, no error (R2).
2. **SkillService = composition + enabled-state owner (R1) + source marker (R6/R7).**
   - Holds `List<SkillSlot>`: `configSlot` + `projectSlot` (nullable/empty when no project).
   - `setProjectSkillsDir(Path or null)` = project-slot update (R2a): replaces ONLY the project slot
     (new SkillSlot for the new path), config slot untouched (R2b). Then refresh project slot if path
     set.
   - `refreshAll()` (new) refreshes every slot (R3) — used by ReloadConfigTool + PeonAiService.updateConfig.
     Keep old `refresh(Path)` delegating to config-slot replacement + refreshAll-like behavior for
     compat... — **decision: replace `refresh(Path)` semantics**: `refresh(Path)` = set config-slot path
     + refresh config slot ONLY? No — R3 requires reload to refresh both. Public API: keep
     `refresh(Path)` as config-dir setter+refresh (used by tests/PromptYmlParser callers), add
     `refreshAll()` which refreshes every slot. ReloadConfigTool + updateConfig call `refreshAll()`
     (and set config path first via `setConfigSkillsDir(dir)`/existing refresh(dir)).
   - Effective view read-time: iterate slots in order (config first, project last-wins by lowercase
     key) building a LinkedHashMap — project wins on collision (R4). Never mutate config map (R2b).
   - Enabled-state: `Map<String,Boolean> enabledByName` in SkillService (volatile/CCHM). Applied as
     **read-time filter/decoration** onto effective view: new names default `true` (R1), toggles
     survive every refresh/switch, override-aware by name (R1: toggle on name, not instance).
     `setSkillEnabled(name, b)` writes to the map AND to the effective-view instances (UI reads
     `SkillPromptFile.isEnabled()` — keep those reads working by writing state to the live instances
     of the view; see Open Questions — resolved below in §7).
   - Source marker: `SkillPromptFile` gains `SkillSource source` (enum CONFIG/PROJECT) — set by slot
     on parse; `renderBody()` header becomes `=== SKILL: name [project] ===` (R7); `buildShortInfo()`
     appends `\nsource: [project]` (R7); skill names disclosed via `skillNames()`/SkillTool "Use one
     of:" listing get `[project]`/`[config]` suffix? — R13 says: suffix only in Zähler, Menü-Header,
     Autocomplete; `skillRead`/`skillList` output DOES get the source (R7 explicitly names skillList
     output). `skillNames()` output gets suffix too (it feeds SkillTool not-found messages and
     DisabledSkillTest asserts). **Decision: `SkillPromptFile.sourceTag()` → " [project]"/" [config]"
     used in buildShortInfo + renderBody header; skillNames() joins with sourceTag.**
   - Keep `getSkills/getAllLoadedSkills/get/skillNames/loadedSkillCount/hasSkills/setSkillEnabled/
     setAllSkillsEnabled/isEnabled/setEnabled` — all existing consumers keep compiling.
3. **Scaffold write validator (ADR-0043): new `WriteValidator` impl with dynamic roots.**
   - New class `tool/DynamicRootsWriteValidator.java` (core): `Supplier<List<String>> roots` — each
     root as normalized path string; validate(): normalize input once (`normalizeSegments`), allowed
     if input starts with (normalized-root + "/") or equals root, or input is a relative path without
     traversal escaping (i.e. `..` beyond top). Because the model supplies paths relative to configDir
     (workingDir) or absolute — normalized-segment prefix matching on the raw path is NOT enough for
     absolute paths vs relative. **Decision: resolve the raw path against configDir (the scaffold
     workingDir basis) via `FileUtils.resolve(configDir, path)`, then compare against root paths
     (configDir itself, or `<proj>/.agents/skills`) using `Path.startsWith`.** Traversal is killed by
     `normalize()`; absolute paths stay absolute. Denials throw IllegalArgumentException with the
     allowed roots listed (AI-visible via existing onProblem path).
   - Scaffold: `getWriteValidator()` override returns the dynamic validator; its roots supplier reads
     (a) `configuredModel.getConfig().getConfigDir()` and (b) open projects — supplied via a new
     `Supplier<List<Path>> projectSkillsRoots` passed in at construction/`addTool`-time (plugin passes
     `() -> EclipseUtil.openProjects().stream().map(JdtUtil::diskPathOf)... .map(p -> p.resolve(".agents/skills"))`).
     Core stays Eclipse-free: AiScaffoldAgent gets a constructor param / setter
     `setProjectSkillsRootsSupplier(Supplier<List<Path>>)`, default = empty.
   - Jon's `WriteValidator.DOCS` untouched.
4. **R2d scaffold context (plugin)** — `AgentContextComponent.appendScaffoldContext` gains an
   "Open projects" listing: for each `EclipseUtil.openProjects()`: `"- " + diskPath + " — " + name`
   (plus current project marker). New `SimpleContextItem("Open projects", ...)`; format kept simple.
   Current project info already arrives via `userContext.get()` (L140).
5. **UI (R9/R13)** — StatusLineWidget menu: two sections — "Skills" (config) then separator +
   header item (disabled style) "Project skills — <ProjectName>" + project skills; counter text
   `N skills (M project)` in `setSkillCount(int, int)`. Slash autocomplete: `SlashMenuPopup` shows
   ` [project]` suffix for project skills (entry text `/name [project]`, filter on name only).
   Skill names sent to LLM stay unsuffixed (R13) — suffix only display-side.
6. **Prompt update** — `scaffold-agent.txt`: replace "Never create artifacts outside the config
   directory." with: allowed = config dir + `.agents/skills` of open projects (list in context);
   placement question in chat (R11) with recommendation before writing when placement unclear;
   use open-projects list to pick project.

## 4. Architecture / data flow
- Composition at read time: SkillService.getSkills() = merge(configSlot.skills(),
  projectSlot.skills()) → filter by enabledByName → list. Project overrides config (lowercase key).
- Project switch flow: UI selection → AIChatView.updateSelectedProject → PeonAiService.setProject →
  **NEW: skillService.setProjectSkillsDir(diskPath == null ? null : diskPath.resolve(".agents/skills"))**
  (after disk tools update, before/after userContext.setCurrentProject — same call, R2c).
- Reload flow: ReloadConfigTool / updateConfig → skillService.setConfigSkillsDir(configDir/skills)
  (idempotent) + refreshAll() → both slots rebuilt atomically.
- Scaffold write flow: model supplies path → DiskFileWriteTool.resolve → validateWrite →
  DynamicRootsWriteValidator.resolve(configDir, path) vs roots (configDir, each open project's
  .agents/skills) → allow/deny (onProblem).
- Scaffold context flow: turnContext → appendScaffoldContext adds config-dir listings (existing) +
  **new** open-projects item (disk path + name) → scaffold can address other projects' skill dirs.

## 5. Affected files
**Core (org.sterl.llmpeon.core)**
- NEW `src/main/java/org/sterl/llmpeon/skill/SkillSlot.java` — slot component (discover+parse+atomic
  swap, identity=path).
- `skill/SkillService.java` — rewrite to slot list + name-keyed enabled map + composition view +
  `setProjectSkillsDir(Path)`, `setConfigSkillsDir(Path)`/refresh(Path) + `refreshAll()`; keep all
  existing public methods.
- `skill/SkillPromptFile.java` — add `SkillSource source` (+ `sourceTag()`); renderBody header +
  buildShortInfo disclose source (R6/R7). Enum `SkillSource` (new small file, or nested enum — dev
  choice; if separate file: `skill/SkillSource.java`).
- NEW `tool/DynamicRootsWriteValidator.java` + optional small extension to WriteValidator javadoc.
- `scaffold/AiScaffoldAgent.java` — getWriteValidator() override + projectSkillsRoots supplier (ctor
  param or setter, default empty); keep call() workingDir re-pin.
- `src/main/resources/org/sterl/llmpeon/prompts/scaffold-agent.txt` — write-scope rule + R11 placement
  question + open-projects reference.
- Tests (core): `skill/SkillServiceTest` (extend: R1–R7 incl. R2a/R2b/R5/R5a mapping table tests),
  `tool/DynamicRootsWriteValidatorTest` (R12), `scaffold/AiScaffoldAgentTest` (R12
  writeValidatorAllowsConfigAndProjectSkillsOnly), `tool/SkillToolTest` + `tool/DisabledSkillTest`
  adjust where enabled-state semantics/output changed (skillNames now with source suffix —
  DisabledSkillTest contains() assertions still pass since names are prefixes; verify).
**Plugin (org.sterl.llmpeon)**
- `parts/ai/PeonAiService.java` — setProject: add skillService.setProjectSkillsDir(...);
  updateConfig: refresh config slot then refreshAll(); pass project-skills roots supplier into
  AiScaffoldAgent (set on scaffoldAgent after construction; supplier reads EclipseUtil.openProjects
  → diskPathOf → resolve(".agents/skills"), null-safe).
- `parts/ai/component/AgentContextComponent.java` — appendScaffoldContext: add "Open projects" item
  (disk path — name lines, via EclipseUtil.openProjects + JdtUtil.diskPathOf).
- `parts/widget/StatusLineWidget.java` — two-section skills menu (config/project with header) +
  counter `N skills (M project)`; SkillMenuSelection unchanged (name-keyed toggles).
- `parts/widget/SlashMenuPopup.java` — ` [project]` suffix in entry display for project skills
  (R9), filter still by name (R13).
- `parts/AIChatView.java` — refreshStatusLine passes project-skill count (getSkills by source);
  menu supplier unchanged (`getAllLoadedSkills`).
- Plugin tests (`org.sterl.llmpeon.test`): `PeonAiServiceTest` gains
  `setProject_replacesProjectSlotOnly` + `pinnedProject_keepsSkillSlot_pinnedProject` (JUnit4;
  write `.agents/skills` skills into fixture + aaa_other projects, setProject, assert
  skillService view — see §7). UI (StatusLineWidget/SlashMenuPopup) = manual verification (SWT) —
  state in plan.
**Docs (PO-owned — commit but NEVER edit):** docs/project-skills.md, docs/adr/0042*, 0043*,
docs/glossary.md, docs/index.md, docs/configuration.md, docs/scaffold-agent.md,
docs/write-path-validator.md, docs/memory.md.

## 6. Rules & constraints
- Log OR throw, never both; failed refresh (R5a): catch IOException at callers
  (ReloadConfigTool returns error text; updateConfig keeps current throw-into-RuntimeException path
  but must NOT clear state — with atomic swap this is inherent).
- A tool must never lie: failed refresh reports; source disclosure in skillList/skillRead (R7);
  no silent divergence between tool families.
- Thread safety: SkillService used from UI thread (menu/autocomplete) + agent threads (skillList) +
  reload — volatile slot list + immutable maps; no single-threaded assumptions.
- Skill names for LLM remain unsuffixed (R13); suffix `[project]`/`[config]` display-side only
  (menu/counter/autocomplete/skillRead-header/skillList-shortInfo per R7).
- Keep API compat: getSkills/getAllLoadedSkills/get/skillNames signatures unchanged.
- No secrets in logs (N/A here).
- Core = plain Maven + AssertJ; plugin tests = JUnit4, no external assertion libs.
- Core stays Eclipse-free: no IProject imports in core (open-projects list flows in via Supplier).
- AGENTS.md "komponenten-architektur SKILL" — deep modules, information hiding.

## 7. Enabled-state design (resolves R1 cleanly)
- SkillService holds `volatile Map<String,Boolean> enabledByName` (default true on first sight).
- Effective view construction: for each merged skill (lowercase key), set instance enabled flag
  from map before returning (write-through decoration at view build: `s.setEnabled(enabledByName.
  getOrDefault(key, true))`). setSkillEnabled writes both map and live instance.
- Rationale: UI and SkillTool read instance state; decoration at view build keeps them consistent;
  map ensures survival across refresh/project switch (instance dies, name-keyed state persists).
- setAllSkillsEnabled writes all current view instances + marks map for all keys (R1).
- R5 atomic swap guarantee: getSkills() reads each slot's volatile map reference once; merge builds
  new list — never a half-filled map.

## 7a. R11 scaffold placement question — mechanics
- Prompt-only (no tool): scaffold-agent.txt gains the rule; open-projects context (R2d) provides the
  project list; the existing clarify-then-act rule covers waiting for the user's answer.
- No code changes needed beyond context + prompt + validator.

## 8. Increments (each green on its own; commit after each)
### inc-1 (core): SkillSlot + SkillService rewrite + source marker
- Create SkillSlot; rewrite SkillService per §3.2 + §7; add SkillSource + sourceTag disclosure
  (renderBody header + buildShortInfo); skillNames() with source suffix.
- API: keep all existing methods; add `setProjectSkillsDir(Path|null)`, `setConfigSkillsDir(Path)`
  (or reuse refresh(Path) for config set — implementer's choice, document in class javadoc),
  `refreshAll()`.
- Tests (core, Surefire ground truth): new tests in SkillServiceTest mapping R1–R7:
  - `twoComponents_projectSwitchReplacesProjectComponent` (R2/R2a)
  - `configComponentNeverMutated_maskedSkillReturns` (R2b)
  - `projectSkillOverridesConfigSkillByLowercaseName` (R4)
  - `reloadRefreshesEveryComponent` (R3)
  - `swapIsAtomic_noPartialMapVisible` (R5 — hard to test truly concurrent; approximate: refresh
    during getSkills iteration returns full old or full new; or assert map-reference swap semantics;
    implementer's choice, state in test javadoc)
  - `sourceMarkerAndListDisclosure` (R6/R7)
  - `enabledStateSurvivesRefreshAndFollowsName` (R1)
  - `failedRefreshKeepsPreviousState` (R5a)
  - plus keep/adjust existing tests green (Jimfs fixtures).
- Verify: `mvn -pl org.sterl.llmpeon.core test` (or full `mvn test`) green; commit `inc-1: ...`.

### inc-2 (plugin): setProject hook + scaffold open-projects context — ✅ DONE (d087eb0)
- PeonAiService.setProject: add `skillService.setProjectSkillsDir(projectPath == null ? null :
  Path.of(projectPath).resolve(".agents/skills"))` (use diskPathOf; null = empty project slot).
- AgentContextComponent.appendScaffoldContext: add open-projects item (R2d).
- updateConfig: config-slot set + refreshAll (R3).
- Plugin tests in PeonAiServiceTest (OSGi JUnit4, extends AbstractIntegrationTest):
  - `setProject_replacesProjectSlotOnly`: GIVEN config skill + project A skill WHEN switch to
    project B THEN B's project skill present, config skill still there, A's project skill gone.
    Mechanics: write `.agents/skills/<name>/SKILL.md` into PeonTestFixture project dir + `aaa_other`
    (temp dirs via Files in test, cleanup in finally — memory.md rule 12: don't rely on persisted
    state; create dirs under the fixture project's disk path, delete in finally).
  - `pinnedProject_keepsSkillSlot_pinnedProject`: GIVEN pin on A, selection in B (userContext
    setSelectedResource) THEN slot stays A (setProject not called on selection while pinned —
    verifies AIChatView.updateSelectedProject guard via service-level: call setProject(B) is NOT
    what the UI does when pinned; test the UI-driven path at service level: userContext pinned flag
    prevents UI calls — assert at service level that pin flag + no setProject call keeps slot on A;
    if UI-level test too heavy for OSGi, test the service-level contract: setProjectSkillsDir
    replaced ONLY by setProject with new project, and AIChatView guards pinning — state choice).
- Verify: eclipseBuildProject both plugin projects, run `org.sterl.llmpeon.test` suite (first run
  may need workspace-trust confirm; no parallel launches); commit `inc-2: ...`.
- DONE note (d087eb0): pinning test implemented as service-level contract per plan fallback —
  pin-flag half declared characterization (view guard = manual UI territory); Core 686 green,
  plugin suite 193 green (191 baseline + 2 new). R2d "Open projects" item has no dedicated test
  (plan lists none for inc-2) — offered as optional follow-up.

### inc-3 (core+plugin): scaffold validator + prompt + UI — ✅ DONE
- Core: DynamicRootsWriteValidator + AiScaffoldAgent.getWriteValidator() override +
  `setProjectSkillsRootsSupplier(Supplier<List<Path>>)` (default `() -> List.of()`); test
  `writeValidatorAllowsConfigAndProjectSkillsOnly` in AiScaffoldAgentTest (GIVEN scaffold with roots
  configDir + projA/.agents/skills + projB/.agents/skills WHEN write tool validates
  projA/.agents/skills/x.md → ok; projA/src/X.java → IllegalArgumentException; configDir/y.md → ok;
  traversal `../secret` → denied).
- Plugin: wire supplier (EclipseUtil.openProjects → diskPathOf → .agents/skills, null-safe).
- Prompt scaffold-agent.txt: update write-scope rule + R11 chat placement question.
- UI: StatusLineWidget two-section menu + `N skills (M project)` counter; SlashMenuPopup ` [project]`
  suffix; AIChatView refreshStatusLine passes M (project-skill count via getSkills by source).
- Tests: core validator test (above) + AllowlistWriteValidatorTest untouched (DOCS unchanged).
  UI = manual verification (SWT widgets; no SWT bot infra) — stated here per instruction.
- Verify: core Surefire green; plugin build + suite run; commit `inc-3: ...`.
- DONE note: `DynamicRootsWriteValidator` (core, new) resolves the raw path against configDir via
  `FileUtils.resolve` (same base the write tool uses) and allows only paths equal to / under a root
  (configDir + each open project's `.agents/skills`, read at validate time via
  `AiScaffoldAgent.setProjectSkillsRootsSupplier`); denials throw `IllegalArgumentException` listing
  the allowed roots. `AiScaffoldAgent.getWriteValidator()` override returns it fresh per request
  (configDir re-read, so config reloads are picked up). Core test
  `writeValidatorAllowsConfigAndProjectSkillsOnly` (R12) green. Prompt `scaffold-agent.txt`:
  write-scope rule + R11 placement question (chat, recommendation, wait for answer, pick project
  from "Open projects" list) + project-local skill location in the Skills spec. Plugin: supplier
  wired in `PeonAiService` (EclipseUtil.openProjects → JdtUtil.diskPathOf → resolve PROJECT_SKILLS_DIR,
  null-safe). UI (R9): `StatusLineWidget` two-section menu (config skills, then disabled-style
  "Project skills — <name>" header + project skills) + counter `N skills (M project)` (M-part only
  when M > 0); `SlashMenuPopup` ` [project]` suffix (display only — `applyCommandSelection` inserts
  the unsuffixed name, R13); `AIChatView.refreshStatusLine` passes the project count. UI = manual
  verification (SWT). Core Surefire 687 green (686 + 1 new); plugin suite 193 green (unchanged —
  no new plugin tests per plan; `AllowlistWriteValidatorTest` untouched, DOCS validator unchanged).

## 9. Test strategy
- Core tests on Jimfs (AbstractMemoryFileTest) — no disk I/O flake; static subject + tmp shared —
  new tests must be order-independent: use local SkillService instances + local tmp subdirs per test
  (don't mutate the shared static subject in new tests; the existing tests already mutate it — keep
  new tests on local instances).
- Plugin tests: real OSGi workspace via AbstractIntegrationTest (imports PeonTestFixture project);
  create project skills dirs under fixture disk path in try/finally cleanup (memory rule 12).
  eclipseBuildProject before every plugin test run (memory rule 16).
- Test honesty (AGENTS-DEV): each test must fail without the feature; e.g. source disclosure test
  asserts "[project]" in skillList output — fails on old code.
- UI manual verification checklist (inc-3): counter "N skills (M project)", menu two sections with
  project header, slash autocomplete suffix, names in dialog unsuffixed.

## 10. Open questions
- None blocking. Two implementation freedoms (state in commit/PR description): (a) SkillSource enum
  location (SkillPromptFile nested vs own file), (b) setConfigSkillsDir vs reusing refresh(Path).
- Per user instruction: docs/** files are PO-owned — commit them with increments, never edit content.

## 11. Delta (Review-Funde) — branch story/133
> All three items are implementation-level — **SOLL docs unchanged** (docs/project-skills.md is NOT
> touched). Verify branch first: `git branch --show-current` = story/133. STOP-AND-ASK per §0 on any
> blocker. Commit after each green item with usual trailer; docs/** and homepage/** go into the
> commits; NEVER edit docs/** content.

### inc-4 (core): false-negative guard in `SkillService.get(String)` — tagged-name echo — ✅ DONE (7e2320a)
- **Bug:** `skillNames()` now emits `"review [project]"` (R7 disclosure, feeds SkillTool's
  "Use one of:" listing). If the model echoes a tagged name into `skillRead`/`skillReadFile`,
  `get("review [project]")` does a lowercase map lookup → miss = **false negative** (worst bug
  class in this repo, AGENTS.md).
- **Fix:** `skill/SkillService.get(String)` — strip a trailing source tag BEFORE the lowercase
  lookup. New private helper `stripSourceTag(String name)`: compare `name.toLowerCase(Locale.ROOT)`
  against each `SkillSource.values().tag()` literal (`" [project]"`, `" [config]"`); case-insensitive
  `endsWith` → cut that tag length off the ORIGINAL name, then `.strip()` (robustness vs double
  space); strip at most one tag, only if present; unknown tags (`"review [bogus]"`) are NOT
  stripped → lookup misses as before. Proceed unchanged: lowercase → `effectiveView().get(key)` →
  enabled decoration. Update `get()` javadoc: accepts tagged names as echoed from `skillNames()`.
- **Test** (SkillServiceTest, local instance + local tmp dirs per §9):
  `get_acceptsTaggedName_returnsSkill` — GIVEN config skill `"cfg"` + project skill `"review"`
  (reuse the `writeSkill` helper) WHEN `get("review [project]")`, `get("cfg [config]")`,
  `get("REVIEW [PROJECT]")` (case-insensitive) THEN each resolves and `getSource()` is correct;
  AND `get("review")` still resolves AND `get("review [bogus]")` AND unknown name are empty.
  Test honesty: red without the fix (`orElseThrow()` on empty Optional for the tagged lookups).
  R13 unaffected (display-side only), R7 disclosure unchanged.
- Verify: full core Surefire green; commit `inc-4: get() accepts tagged skill names
  (false-negative guard)`.

### inc-5 (core, expect NO surviving change → likely no commit): mutation proof R4 (review-approved) — ✅ DONE (no commit — no surviving change)
- Mutate `SkillService.effectiveView()` (L75–79): flip merge order —
  `new LinkedHashMap<>(projectSlot.skills())` first, `merged.putAll(configSlot.skills())` second.
- Run SkillServiceTest via Maven Surefire. EXPECT RED — primary catcher
  `projectSkillOverridesConfigSkillByLowercaseName` (getSource() = CONFIG ≠ PROJECT);
  `configComponentNeverMutated_maskedSkillReturns` as second catcher (its first assert expects
  PROJECT). Record which actually went red.
- REVERT the mutation exactly → run FULL core suite → green again.
- Report: mutated line, tests that caught it, revert confirmation. Commit ONLY if a file change
  survives (expected: none — if `git status` is clean after revert, skip the commit and report the
  evidence in the final summary instead).
- DONE evidence: mutated `effectiveView()` L84–85 (project slot first, `putAll(configSlot)`
  second). Surefire `SkillServiceTest`: 17 run, **2 FAIL** — `projectSkillOverridesConfigSkillByLowercaseName`
  (expected: PROJECT, but was: CONFIG) + `configComponentNeverMutated_maskedSkillReturns` (same).
  Reverted exactly → full core suite **688 green**. `git status` shows no surviving change to
  `SkillService.java` → no commit, per plan.

### inc-6 (homepage): "Project skills" section — ✅ DONE (94a6499)
- `homepage/src/setup/agents-and-skills.md`: add `## Project skills` right after the existing
  `## Skills` section (file end). User-facing VitePress tone like the rest of the page ("Drop an
  ... into your project" style) — short, written as the user, NOT a changelog. Cover all five
  points: (1) skills can live in `<project>/.agents/skills` (disk path, same `SKILL.md` structure
  as the config skills folder); (2) same name as a config skill → project variant wins (override
  by name); (3) when Peon AI creates a skill it asks in chat where to place it (project vs config)
  and recommends based on scope; skills it writes land in a project's `.agents/skills`;
  (4) status line counter shows `N skills (M project)`; (5) skills menu lists project skills in
  their own section, slash autocomplete shows a ` [project]` suffix (the inserted command stays
  untagged).
- Commit `inc-6: homepage: project skills docs` (include homepage/**).

### Delta verification & final report
- Core Surefire full suite green — expect **688** (687 + 1 new).
- Plugin suite (expect **193**, unchanged — plugin code untouched): `eclipseBuildProject` on core
  + both plugin projects first (memory rule 16, stale bin/), then one full suite run (memory rule
  13: first run may need workspace-trust confirm, never parallel launches).
- Final report: core + plugin test numbers, inc-5 mutation evidence (mutated line, catcher tests,
  revert confirmation), commit hashes.
- ✅ FINAL (2026-09-08): core Surefire **688 green** (687 + 1 new); plugin OSGi suite **193 green**
  (unchanged). Commits: inc-4 `7e2320a`, inc-5 none (no surviving change), inc-6 `94a6499`.
  SOLL docs content unchanged (the `docs/project-skills.md` R2c test-name mapping fix was a PO
  edit, committed with inc-6).