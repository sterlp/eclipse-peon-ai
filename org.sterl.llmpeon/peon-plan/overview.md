# Feature-Batch: `.peon` State-Umzug + Migration + E5 + File Copy Tool (+ Aufräumen)

**Branch:** `state-config-2026-09-06` (von `main` — HEAD ist aktuell `main`, User freigegeben).
**Zielbild:** `.peon` = shared-config-only (ADR-0041), History → Workspace-Metadata, One-Shot-Migration, Disk-Tool-Pfade absolut, Copy-Tool in beiden Familien.
**SOLL-Docs (lesen, nicht ändern):** `docs/peon-config-directory.md` (R1+R2+R3 + ADR-0041) · `docs/disk-file-write-tool.md` (E5) · `docs/file-copy-tool.md` (R1–R4) · `docs/configuration.md` (Landkarte, kein Bau) · `docs/index.md`.

---

## inc-1 — Aufräumen & Commit-Basis (klein, grün = compile + core-suite unverändert grün) ✅ DONE (core 632/632 grün, Plugin+Test-Compile ok; Branch `state-config-2026-09-06` angelegt, smoke-fixes + Docs + Prompt-Regeln committet) ✅ DONE (core 632/632 grün, Plugin+Test-Compile ok; Branch `state-config-2026-09-06` angelegt, smoke-fixes + Docs + Prompt-Regeln committet) ✅ DONE (core 632/632 grün, Plugin+Test-Compile ok; Branch `state-config-2026-09-06` angelegt, smoke-fixes + Docs + Prompt-Regeln committet)

1. `git status` / `git log --oneline -5` prüfen (Branch wechseln/anlegen: `state-config-2026-09-06` von `main`).
2. Smoke-Test-Fixes **verifiziert — beide stehen im Code**:
   - (a) `headerBar.refreshRoster()` in `onClear()` → `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java:186` ✅ da.
   - (b) `AGENT_SECTIONS` PO→Plan→Dev→Search→Compact → `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/AiAdvancedPreferenceView.java:35` + `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/AdvancedPreferenceSectionsTest.java` ✅ da.
   - Falls `git status` sie als committed zeigt (Merge schon gelaufen) → nur (3)–(5). Sonst wandern sie mit in den inc-1-Commit.
3. **NEU Prompt-Regeln (Docs-Buchhaltung im Commit):**
   - `org.sterl.llmpeon.core/src/main/resources/org/sterl/llmpeon/prompts/po-delegation.txt`: Jon-Buchhaltung — seine Docs-Änderungen (`docs/**`) wandern mit dem nächsten Increment-Commit bzw. werden sofort committet (er besitzt den Content, der Dev committet ihn).
   - `org.sterl.llmpeon.core/src/main/resources/org/sterl/llmpeon/prompts/dev-build-loop.txt`: im Commit-Bullet ergänzen — der Increment-Commit schließt die Docs (`docs/**`) der Story ein.
   - `AGENTS.md` (Repo-Root, „Build cycles & git"-Absatz): Increment-Commits umfassen die Story-Docs (`docs/**`) — Dev committet, PO besitzt.
4. **Docs-Buchhaltung (PO hat die Verschiebung bereits erledigt, 2026-09-06):**
   - Die vier SOLL-Docs + ADR-0041 liegen jetzt **korrekt am Repo-Root**: `docs/peon-config-directory.md`, `docs/file-copy-tool.md`, `docs/configuration.md`, `docs/adr/0041-peon-shared-config-only.md`. `docs/index.md` ist von PO aktualisiert (configuration.md verlinkt, peon-config-directory-Status R1+R2+R3, jon-askuser-Merge vermerkt) → **nur committen, kein Docs-Edit**.
   - `org.sterl.llmpeon/docs/` enthält nur noch den leeren `adr/`-Ordner-Rest → mit inc-1 **löschen** (leere Ordner sind nicht git-getrackt, einfach entfernen).
   - `peon-plan/overview-done-2026-09-04-17-45.md` (archivierter Plan) → committen.
5. Commit: `inc-1: housekeeping — smoke-test fixes, docs relocation, prompt commit rules`.

## inc-2 — State-Umzug (SOLL: peon-config-directory.md R2 + ADR-0041) ✅ DONE (core 636/636 + Plugin 180/0 grün; stateDir via `Platform.getBundle(PeonConstants.PLUGIN_ID)` — `FrameworkUtil` existiert in der Target-Platform NICHT; `BuildPoAgentComponent`: dead `config`-Feld entfernt; `lib/llmpeon-core.jar` regeneriert, da PDE-Build dagegen kompiliert)

**Prinzip:** `FileAgentHistoryStore` bekommt weiterhin den vollen Datei-Pfad; die **Ableitung** `configDir → configDir/state/<agent>-history.jsonl` wandert aus dem Core-Agenten zu den Aufrufern. Der injizierte Parameter wird zum **State-Verzeichnis** (enthält die `<agent>-history.jsonl` direkt, ohne `state`-Segment). Keine Eclipse-Dependency im Core.

### Core (`org.sterl.llmpeon.core`)
- `agent/AbstractAgent.java`: `historyFile(Path stateDir, String agentName)` → `stateDir.resolve(safeAgentName(agentName) + "-history.jsonl")` (**kein** `.resolve("state")` mehr). Javadoc: stateDir = Verzeichnis, das die History-Dateien direkt enthält.
- `agent/AiDevAgent.java`, `agent/AiPlanAgent.java`, `agent/CustomAgent.java`, `poagent/AiPoAgent.java`: Konstruktor-Parameter `Path historyConfigDir` → `Path historyStateDir` (Semantik wie oben); Store-Bau unverändert `historyStateDir == null ? RAM : new FileAgentHistoryStore(historyFile(historyStateDir, NAME))`.
- `ai/LlmConfig.java`: neue Methode `public Path stateDirectory() { return configDir.resolve("state"); }` — der **benannte Core-Default** (headless).
- `AgentService.java`: Parameter `historyConfigDir` → `historyStateDir` in allen 4 Konstruktoren (Durchreichung). Aufrufer anpassen: `AgentServiceTest` (7), `ReloadConfigToolTest` (3, übersetzen `configDir → config.resolve("state")` bzw. `LlmConfig#stateDirectory()`), Plugin `PeonAiService` (1).
- `memory/ThreadSafeMemory.java`: Accessor `public Optional<Path> historyFile()` → `store != null ? Optional.of(store.historyFile()) : Optional.empty()` (dient den Tests, null-RAM transparent).
- Tests core:
  - Pfad-Ableitung: GIVEN stateDir WHEN `historyFile(...)` THEN `stateDir/<Agent>-history.jsonl` (inkl. safeAgentName-Fall mit Sonderzeichen).
  - `LlmConfigTest`: `stateDirectory()` == `configDir/state` (Core-Default R2 „headless bleibt configDir/state").
  - `AgentServiceTest`: dev/plan/custom-Agenten tragen Store unter dem **injizierten** stateDir (via `ThreadSafeMemory.historyFile()`).
  - `AbstractAgentTest`/`ThreadSafeMemoryTest`: bestehende Pfad-Erwartungen auf neue Semantik umstellen.

### Plugin (`org.sterl.llmpeon`)
- `parts/ai/PeonAiService.java` (Konstruktor): State-Dir der Plugin-Schicht bestimmen — `FrameworkUtil.getBundle(PeonAiService.class)` → `Platform.getStateLocation(bundle)` (liefert `<ws>/.metadata/.plugins/org.sterl.llmpeon`) → `.append("state").toFile().toPath()`. Kein neuer Activator nötig (kein `Bundle-Activator` im MANIFEST, automatic-Start reicht).
- Weiterreichen: `new AgentService(true, agentsDir, sharedToolService, configuredModel, stateDir)` und `BuildPoAgentComponent(..., stateDir)` → `new AiPoAgent(configuredModel, poToolService, stateDir, slaves)`.
- `parts/ai/component/BuildPoAgentComponent.java`: Konstruktor + `build()` um `Path historyStateDir` erweitern.
- Jon (PoAgent), Dev, Plan, **alle Custom Agents** (via AgentService) nutzen denselben Metadata-State; RAM-Sklaven (Da Thinka/Da Mek, 3-arg-CTor mit `compactFactor`) bleiben ohne Store (ADR-0024).
- Test plugin (nur patterns-erprobtes, `PeonAiServiceTest`): `persistentAgentsLiveInWorkspaceMetadataState` — GIVEN gebauter Service WHEN `historyFile()` von Dev/Plan/PO gelesen THEN beginnt mit `Platform.getStateLocation(bundle)/state`; `CustomAgent`-Pfad folgt derselben Regel (dieselbe Injektion, eine Zeile im Test genügt falls Fixture-Agent fehlt).

### Homepage (AGENTS-Pflicht, user-facing)
- `homepage/src/peon-memory.md`, Abschnitt „Chat history persistence": neuer Ablageort `<workspace>/.metadata/.plugins/org.sterl.llmpeon/state/<agent>-history.jsonl`, workspace-scoped; `~/.peon` hält künftig nur Config (Agents/Skills/Commands); automatische Einmal-Migration folgt (inc-3).

Commit: `inc-2: agent history → workspace metadata state (ADR-0041 R2)`.

## inc-3 — One-Shot-Migration (SOLL: R3) ✅ DONE (core 642/642 + Plugin 180/0 grün; `StateMigration` pure NIO + injizierbarer `Mover`, Skip-bei-Ziel-Existenz, per-Entry-Errors gefangen, leeres Source-Dir entfernt, idempotent; Wiring `PeonAiService`-CTor vor `AgentService`; Homepage Skip+Log-Verhalten)

**Neu:** `org.sterl.llmpeon.core/.../memory/StateMigration.java` (pure NIO, kein Eclipse).
API: `static MigrationResult migrate(Path sourceDir, Path targetDir)` + Kern mit injizierbarem Mover (`UnaryOperator<Path>`/`BiFunction<Path,Path,Path>`) für den Fehler-Test.

Semantik (1:1 R3):
- `sourceDir` existiert nicht / ist leer → stiller No-Op (Result `moved=0`).
- Pro Eintrag (reguläre Dateien, flach; Unterverzeichnisse: `Files.move` rekursiv gleiche Volume — Fehler → gefangen):
  - Ziel existiert bereits → **Skip**: Quelle unverändert, Zähler `skipped`, Meldung `Skipped migration of <name> — target already exists` (kein Überschreiben, kein stiller Verlust).
  - Sonst `Files.move(source, target)` **ohne** `ATOMIC_MOVE` — Cross-Volume-Fallback (copy+delete) liefert das JDK definiert selbst (PO-Entscheidung 2026-09-06: **KEIN eigener Fallback-Code bauen** — ADR-0041-Wortlaut „Cross-Volume-Fallback copy+delete" ist damit semantisch erfüllt).
  - Per-Entry-Fehler → gefangen, Zähler `failed` + Log-Warn; **Start läuft weiter** (Migration blockiert nie, wirft nie).
- Nach vollständiger Migration: leeres `sourceDir` löschen (`Files.deleteIfExists` nur wenn leer; `~/.peon` selbst bleibt).
- Log: eine Zusammenfassungs-Zeile (moved/skipped/failed + Pfade). **Nur Log, keine UI-Statuszeile** (kein `PeonConstants.status`) — kein Dialog (PO 2026-09-06: Migration ist One-Shot-Startpfad; der User sieht das Ergebnis — leere `~/.peon/state` — nicht den Vorgang).

**Wiring:** `PeonAiService`-Konstruktor **vor** `new AgentService(...)` (Histories laden im CTor!): `StateMigration.migrate(config.getConfigDir().resolve("state"), pluginStateDir)`. Headless-Core ruft sie nie (Default = Quell-Ort → No-Op).

Tests core (`memory/StateMigrationTest`, GIVEN/WHEN/THEN, Jimfs via `AbstractMemoryFileTest`):
- `movesHistoryFileToTargetState` — Datei landet im Ziel, Quelle weg.
- `secondRunIsNoOp` — idempotent (Quelle leer/verschollen → `moved=0`).
- `targetExistsSkipsAndKeepsSource` — Ziel bleibt, Quelle unverändert, `skipped=1`, Meldung enthält Dateinamen.
- `missingSourceDirIsSilentNoOp`.
- `emptySourceDirRemovedAfterMigration`.
- `failingMoveIsLoggedNotThrown` — injizierter Mover wirft; `failed=1`, Rest migriert, keine Exception.
Test plugin (optional, nur wenn OSGi-Lauf unkritisch): `serviceStartMigratesLegacyState` — UUID-Datei in tmp-`configDir/state`, Service bauen, Datei liegt im Metadata-State; `finally` Metadata-Datei löschen (kein persistenter Test-State, Memory-Regel 12).

Homepage: in `peon-memory.md` den Migrationssatz ergänzen (einmalig, automatisch; Konflikt → Skip + Log, Quelle bleibt).

Commit: `inc-3: one-shot migration ~/.peon/state → workspace metadata (ADR-0041 R3)`.

## inc-4 — E5: Disk-Tool-Success-Messages = absoluter Pfad (SOLL: disk-file-write-tool.md) ✅ DONE (core 646/646 + Plugin 180/0; DiskFileWriteTool write/delete/edit/rename Success-Messages = absoluter Pfad (resolved), AiFileUpdate-Diff-Header bleibt workingDir-relativ (PO); +4 Tests via CapturingMonitor, Homepage-Tip in setup/custom-agents.md)

**IST:** `tool/tools/DiskFileWriteTool.java` meldet `workingDir.relativize(resolved)`.
**SOLL:** Jede Disk-Tool-Success-Message mit Pfad meldet den **absoluten** Pfad (workingDir aufgelöst) — im Sync mit der Eclipse-Familie (diese meldet bereits `JdtUtil.pathOf(...)`/Workspace-Pfad; **nichts zu ändern**, nur Verifikation).

Änderungen `DiskFileWriteTool`:
- `diskWriteFile` → `Created file: <abs>` / `Updated file: <abs>`
- `diskDeleteFile` → `Deleted: <abs>`
- `diskEditFile` (return) → `replaced/deleted N occurrence(s) in <abs>`
- `diskRenameResource` → `Renamed <abs> -> <abs>`
- `diskReplaceLines`/`diskInsertLines` geben keine Pfad-Message → nur `AiFileUpdate` — bewusst **außerhalb** des E5-Scopes. PO-Entscheidung 2026-09-06: `AiFileUpdate` (Diff-Header) **bleibt workingDir-relativ** — Diff-Header ist Monitoring, keine Success-Message; **keine Code-Änderung**.

Tests core (`DiskFileWriteToolTest`): je Test Message via ToolLoopRequest+Monitor-Capture — `writeMessageCarriesAbsolutePath` (Created + Updated), `deleteMessageCarriesAbsolutePath`, `editResultCarriesAbsolutePath`, `renameMessageCarriesAbsolutePaths` ( GIVEN workingDir `tempDir` WHEN … THEN Message enthält `tempDir.resolve(...)`-Pfad).

Homepage: `homepage/src` dokumentiert Disk-Tool-Messages bisher nirgends strukturiert → kurzer Satz „Disk file tools report absolute paths" bei der Stelle, wo Disk-Tools erwähnt sind (`setup/custom-agents.md` Tool-Kontext) bzw. in die inc-5 „File tools"-Notiz (zusammenlegen erlaubt).

Commit: `inc-4: disk tool success messages report absolute paths (E5)`.

## inc-5 — File Copy Tool (SOLL: file-copy-tool.md R1–R4)

**Geteilter Kern** („one behaviour, one implementation"): `shared/FileUtils.copy(Path source, Path target)` — validiert: Quelle fehlt → `IllegalArgumentException("Not found: …")`, Quelle ist Verzeichnis → `"Not a file: …"`, Ziel existiert → `"Target already exists: …"` (R3, **kein Overwrite-Flag**); `Files.createDirectories(target.getParent())` (R1, wie Rename); `Files.copy(source, target)` ohne `REPLACE_EXISTING`. Original bleibt.

- **Core:** `tool/tools/DiskFileWriteTool.diskCopyFile(@P sourcePath, @P targetPath)` — Resolution wie Rename (über `resolve()` → `validateWrite` greift automatisch), Message `Copied <abs> -> <abs>` — ASCII-Pfeil `->` (konsistent mit Rename; PO-Entscheidung 2026-09-06; Doc-SOLL `→` bleibt Semantik-Beschreibung).
- **Plugin:** `parts/tools/EclipseWorkspaceWriteFileTool.eclipseCopyFile(sourcePath, targetPath)` — `validateWrite` auf beide Pfade, `EclipseUtil.resolveInEclipse` für Quelle + Existenz-Check Ziel, Parent-Folders via `IoUtils.ensureFolders`, `resource.copy(destPath, IResource.KEEP_HISTORY, getProgressMonitor())`, Message `Copied <sourcePath> -> <workspacePath>` (familien-übliche Pfade).
- **R4:** `diskRenameResource`/`eclipseRenameResource` bleiben unangetastet (atomar, kein Copy+Delete-Ersatz).

Tests:
- core `DiskFileWriteToolTest`: `copyCreatesTargetAndKeepsSource` · `copyCreatesParentDirectories` · `copyFailsWhenTargetExists` (Quelle+Ziel unverändert) · `copyFailsWhenSourceMissing` · `copyFailsWhenSourceIsDirectory`.
- plugin `EclipseWorkspaceWriteFileToolTest` (Parität, gleiche Regel-Liste gegen `test_project`-Fixture): `test_copyWorkspaceFile` (+Parents, Original bleibt) · `test_copyWorkspaceFile_failsWhenTargetExists` · `test_copyWorkspaceFile_failsWhenSourceMissing`.
- R2-Nachweis: identische Message-Form `Copied <s> -> <t>` in beiden Familien — **testbar per String-Vergleich auf `"->"`** (core: AssertJ `contains(" -> ")`, plugin JUnit 4: `assertTrue(msg.contains(" -> "))`), je eine Assertion in den obigen Tests.

Homepage: `diskCopyFile`/`eclipseCopyFile` dokumentieren (neuer kurzer Abschnitt „File tools" im Setup-/Usage-Bereich: Copy-Verhalten, kein Overwrite, Rename bleibt atomar — incl. E5-Satz aus inc-4 falls dort noch offen).

Commit: `inc-5: file copy tool for disk and eclipse families (R1–R4)`.

---

## Rules & Constraints

- **Commits:** je Inkrement `inc-N: <summary>` + `Assisted-by: Peon AI (<ModelName>)`-Trailer; nur auf `state-config-2026-09-06`, nie auf main. Docs (`docs/**`) werden mit-committed, aber **inhaltlich nie vom Dev geändert**. Homepage-Änderungen gehören ins jeweilige Inkrement.
- **Ground truth:** Maven-Surefire (core) für Testzahlen; vor Plugin-Testlauf `eclipseBuildProject` über geänderte Projekte (stale `bin/` → ClassNotFoundException). PDE-Läufe: ganze Suite, erste Trust-Bestätigung abwarten, nicht parallel starten.
- **Test-Stil:** GIVEN/WHEN/THEN-Kommentare; core = JUnit 5 + AssertJ; plugin = JUnit 4, keine externen Assertion-Libs (ADR-0027).
- **Secrets:** nichts Neues sensibles; `LlmConfig.toString`-Ausschlüsse unangetastet lassen.
- **Kein Eclipse-Import im Core:** State-Dir-Injektion strikt über Parameter; `Platform`/`FrameworkUtil` nur im Plugin.
- **Clean Break:** keine Preferenz-Migration, keine Alias-Pfade — nur die One-Shot-Datei-Migration (R3).
- Falls neuer Eclipse-API-Basisschatz entsteht (getStateLocation-Handling ohne Activator): als Hint in `skills/eclipse-dpe` sichern (AGENTS-Auftrag an Dev/Plan).

## Test-Strategie (Sammelort)

- core: `AbstractAgentTest` (Pfad-Ableitung), `AgentServiceTest` (Injektion Dev/Plan/Custom), `LlmConfigTest` (stateDirectory-Default), `FileAgentHistoryStoreTest` (unverändert, volle Pfad-Injektion), `StateMigrationTest` (alle R3-Fälle, injizierbarer Mover statt IO-Fehler-Simulation), `DiskFileWriteToolTest` (E5 + Copy).
- plugin: `PeonAiServiceTest` (Metadata-Pfad + optionale Migration), `EclipseWorkspaceWriteFileToolTest` (Copy-Parität).
- Keine Timeout/Retry-Flächen beteiligt; Migration ist synchron im Service-CTor (Start-Impact: eine Directory-Liste — vernachlässigbar).

## Offene Punkte — ALLE BEANTWORTET (PO 2026-09-06)

1. Skip-Meldung: **nur Log**, keine UI-Statuszeile → in inc-3 eingetragen.
2. `AiFileUpdate`: **bleibt workingDir-relativ**, keine Code-Änderung → in inc-4 vermerkt.
3. `docs/index.md`: **von PO erledigt** (configuration.md verlinkt, peon-config-directory-Status R1+R2+R3, jon-askuser-Merge vermerkt) → inc-1 committet Docs wie sie liegen.
4. Pfeil: **`->` im Code** (ASCII, konsistent mit Rename, testbar per String-Vergleich), Doc-SOLL `→` bleibt Semantik-Beschreibung → inc-5.
5. Cross-Volume: **kein eigener Fallback-Code** — JDK `Files.move` ohne `ATOMIC_MOVE` deckt copy+delete ab → in inc-3 eingetragen.

STATUS: COMPLETE