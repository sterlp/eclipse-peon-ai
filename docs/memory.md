# Session-Stand (2026-09-11 Abend — core-cleanup-2026-09-11 @ `f85156a`, gebaut + Review bestanden)

## Zyklus core-cleanup-2026-09-11 — STATUS: ✅ Review bestanden (Da Dok CONCERNS→behoben), Archivierung läuft

Branch-Chain: `299c26b` (5 rote Tests) → `0224066`/`85de46d` (Docs) → `9adcab6` (Merge
compressor-Fix aus `fix/compressor-empty-compact-input` — **main hat den Fix NICHT**, Merge main =
User-Entscheidung) → `8b2431e` (Inc-1) → `b7f67e0` (Inc-2) → `44b1846` (Inc-3) → `e214997`
(Javadoc-Refresh) → `f85156a` (Traversal-Guard-Fix SOLL-4 + ADR-0046).

- **Inc-1 ✅** 5 Bug-Fixes + 2 neue R4-Warn-Tests (Core Surefire **727/0**): StreamingBridge
  `compareAndSet` (Cancel gewinnt); SkillPromptFile skill-qualifizierter Pfad + parentless-IAE;
  ToolService agent-null-Guard; AgentOrder `IdentityHashMap` (pattern first-wins → 1×, Namens-
  Kollision → beide + je `log.warn`).
- **Inc-2 ✅** Moves Plugin→core: SimpleDiff, WorkspaceGuideline, ThinkValueSupport,
  AiAgentStatusModel — core import-rein (0 `org.eclipse`), JUnit5+AssertJ für die 2 mit Tests;
  SimpleDiff/WorkspaceGuideline testlos = Altschuld (ADR-0046, kein Scope-Teil).
- **Inc-3 ✅** `deleteOwnSkillArtifacts`-Helper (0 `.getParent()`-Deletes); OSGi-Run = User-Smoke,
  offen.
- **Da Dok:** CONCERNS — (1) Traversal-Guard war tot (`makeReltive` strippte `../` vor dem Guard,
  False-Negative-Klasse) → Option-B-Fix `f85156a`: Guard sieht Roh-Pfad (führendes `/` = relativ,
  makeReltive-Kontrakt), alter `:103`-Guard entfernt (verifiziert dead), Test
  `readRelativeFileRejectsPathTraversal`, valid red-proof. (2) Mutations-Nachweis StreamingBridge
  (`compareAndSet`→`set` = rot) ✅. (3) Skill-Evolution: **No change** (Evidence im Review).
- **Docs:** ADR-0046 core-portability-review (Nordstern: Composition-Root-Gap, B-Moves AskUserTool/
  WorkspaceMemoryTool, UiCommand BEHALTEN, Gaps) + index ✅ · R4 in agent-ordering.md ✅ geflippt ·
  project-skills.md Skill-Lesepfad-BDDs ergänzt · glossary/resolved vom Vormittag drin.

## Nächste Schritte
1. `planImplemented` (Da Mek, Archiv + finaler Commit inkl. Docs) — direkt als Nächstes.
2. Final-Report an User: gebaut ✅, Skill-Evolution No-change, ⏳-Bestätigungen (AgentOrder
   identity-keyed), main-Merge = User, User-Smokes (OSGi + bestehende Liste).
3. Retro kurz: keine neuen memory*-Tools nötig (dead-guard Lesson lebt in AGENTS.md, Test-honesty
   in AGENTS-DEV).

## Offene User-Handlungen (Stand 2026-09-12)
- (a) main-Merge des Branches `core-cleanup-2026-09-11` (Compressor-Fix reist mit) — User.
- **AgentOrder R4 (🔒 2026-09-12):** User-Entscheid — name-keyed, jeder Agent einmal im Dropdown,
  Drop nur mit warn. Umsetzung offen (❌ in agent-ordering.md).
- **Smoke-Verdicts 2026-09-12:** OSGi-Fixture-Smoke ✅ grün · MCP live ✅ · Stop ✅ ·
  Compact-Button ✅ (Stop-idle-Verdacht damit **entkräftet**) · R-ST4: System-Prompt genau 1× im
  Request (Rebuild = Replace, User bestätigt über Jon-History) · **R-ML1a → kein Bug**: Refresh
  nutzt gespeicherten Stand, erst Apply (R-ML2 ✅ dokumentiert in model-loading.md + HP +
  configuration.md).
- ❓ User kommt zurück auf: `Loading 📋 Static env info` nach Such-Agent (Hypothese: frischer
  Stateless-Agent bäkt eigenen Static bei Turn-Start, `AbstractAgent.java:350` — kein Reload des
  Eltern-Agenten).
- ❓ weiter offen (open-points.md): ApiRetry-Evidence, Live-Status im Retry-Fenster, Shell-Tool für
  Plan/Review, Jackson 2→3, buildWithDev-compact.
- Homepage-Release-Notes prüfen (User) — offen.
