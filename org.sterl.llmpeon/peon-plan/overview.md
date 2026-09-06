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

## inc-5 — File Copy Tool (SOLL: file-copy-tool.md R1–R4) ✅ DONE (core 651/651 + Plugin 183/0; `FileUtils.copy` shared core (Not found/Not a file/Target already exists, no overwrite), `diskCopyFile` + `eclipseCopyFile` in both families (R1–R4), R2 message parity `Copied <s> -> <t>` asserted in both, Homepage „File tools" section in setup/custom-agents.md)

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

## inc-6 — Review-Fixes zum Build `state-config-2026-09-06` (Delta-Plan) ✅ DONE (core 651/651 unverändert — keine Core-Änderung; Plugin 185/0, +2 Tests. **R1-Parität:** `IFile`-Guard in `eclipseCopyFile` (Reihenfolge Not found → **Not a file** → Target already exists), Tool-Beschreibung auf „file" zurechtgestutzt, Paritätstest `test_copyWorkspaceFile_failsWhenSourceIsDirectory`; rename unangetastet (R4). **Mutations-Nachweis:** `serviceStartMigratesLegacyState` (Legacy-Fixture VOR Service-Bau, THEN1 History im Metadata-State, THEN2 geladene Memory enthält Legacy-Marker — killt die Reihenfolge-Mutation; finally räumt Legacy+Metadata-Datei).)

Review-Befunde (drei Seiten-Abgleich Plan↔Code, Docs↔Code, Docs↔Plan) — zwei Fixes, keine Core-Änderung (Core-Suite bleibt 651/651, nichts anzufassen).

### 1. R1-Parität Copy-Quelle: Verzeichnis-Quelle ablehnen (Plugin-Familie)

**Befund:** `EclipseWorkspaceWriteFileTool.eclipseCopyFile` (src/org/sterl/llmpeon/parts/tools/EclipseWorkspaceWriteFileTool.java:250-285) akzeptiert Verzeichnisse als Quelle — Tool-Beschreibung sagt „file or directory" (:250), `resource.copy` kopiert Ordner rekursiv (:280). SOLL docs/file-copy-tool.md R1: Quelle = Verzeichnis → Fehler; Core macht es richtig via `FileUtils.copy` „Not a file"-Guard (shared/FileUtils.java, Test `DiskFileWriteToolTest.copyFailsWhenSourceIsDirectory`, llmpeon-parent/…/tool/DiskFileWriteToolTest.java:339).

Änderungen `EclipseWorkspaceWriteFileTool`:
- Guard nach `var resource = source.get();` (:263), **vor** dem Target-Exists-Check (:264) — Reihenfolge exakt wie `FileUtils.copy`: Not found → **Not a file** → Target already exists:
  ```java
  if (!(resource instanceof IFile)) throw new IllegalArgumentException("Not a file: " + sourcePath);
  ```
  (`IFile` ist importiert, :6; `sourcePath` wie bei „Not found: " (:261) — familien-üblicher Workspace-Pfad; Testbarkeit per `contains("Not a file")`.)
- Tool-Beschreibung (:250): „Copy a workspace file **or directory** to a new location." → „Copy a workspace **file** to a new location."

Test plugin (`EclipseWorkspaceWriteFileToolTest.java`, nach `test_copyWorkspaceFile_failsWhenSourceMissing`, JUnit 4, try/fail/catch-Stil wie die Nachbar-Tests):
- `test_copyWorkspaceFile_failsWhenSourceIsDirectory` — GIVEN Ordner via `eclipseWriteFile("/test_project/copyDirSrc/inner.txt", "x")` WHEN `eclipseCopyFile("/test_project/copyDirSrc", "/test_project/copyDirDst.txt")` THEN IllegalArgumentException mit „Not a file", Ziel NICHT erstellt (readTool → „No eclipse file found"), Quelle unverändert. **Kein manuelles Cleanup** — `eclipseWriteFile` legt copyDirSrc in `toDelete`, `AbstractIntegrationTest.after()` löscht rekursiv (verifiziert: `test_deleteResource_recursiveDirectory`); Ziel entsteht nie.
- R3 „Target already exists" ist in der Plugin-Familie bereits gedeckt (Test `test_copyWorkspaceFile_failsWhenTargetExists`) — kein Fix.

**Verifiziert, keine Änderung:** Homepage `/llmpeon-parent/homepage/src/setup/custom-agents.md:178` sagt bereits „**Copy** duplicates a file" ✅. `docs/file-copy-tool.md` R1 ist korrekt — Docs werden nie vom Dev geändert.

### 2. Mutations-Nachweis Migration-Wiring: `serviceStartMigratesLegacyState`

**Befund:** `PeonAiService`-Konstruktor (src/org/sterl/llmpeon/parts/ai/PeonAiService.java:133-138) ruft `StateMigration.migrate(config.stateDirectory(), stateDir)` **vor** `new AgentService(...)` (:140) — die Reihenfolge wird von keinem Test getötet (Mutation „Migration nach Store-Bau" überlebt; Folge: leere History im UI trotz migrierter Datei). Der optionale Plugin-Test aus inc-3 wurde nie gebaut.

Test `PeonAiServiceTest` (nach `persistentAgentsLiveInWorkspaceMetadataState`, :533; JUnit 4; eigenes Service-Setup, NICHT das `@Before`-aiService — Fixture muss **vor** Konstruktion stehen, Memory-Regel 12):

```java
@Test public void serviceStartMigratesLegacyState() {
    assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
    // GIVEN legacy ~/.peon/state mit Dev-History (Marker-Content) — VOR Service-Bau
    var legacyRoot = Path.of(System.getProperty("java.io.tmpdir"), ".peon-migration-test-" + System.nanoTime());
    var legacyState = legacyRoot.resolve("state");
    var legacyFile = legacyState.resolve(AiDevAgent.NAME + "-history.jsonl"); // "Peon-Dev" — safeAgentName-identisch
    new FileAgentHistoryStore(legacyFile).append(UserMessage.from("legacy-migration-marker-" + System.nanoTime()));
    var marker = /* denselben Marker-String in eine Variable, s.u. */;
    try {
        var ccm = new ConfiguredChatModel(LlmConfig.builder()
                .model("test").url("http://localhost:0")
                .configDir(legacyRoot) // → config.stateDirectory() = legacyRoot/state
                .build());
        // WHEN Service bauen (Konstruktor: migrate VOR AgentService)
        var service = new PeonAiService(() -> {}, null, null, null, ccm);
        var expectedStateDir = Platform.getStateLocation(Platform.getBundle(PeonConstants.PLUGIN_ID))
                .append("state").toFile().toPath();
        var dev = service.getAgent(AiDevAgent.NAME).orElseThrow();
        // THEN 1: History-Datei liegt im Metadata-State (R2)
        assertTrue(dev.getMemory().historyFile().orElseThrow().startsWith(expectedStateDir));
        // THEN 2 (Mutations-Killer): geladene Memory enthält den Legacy-Content —
        // nur wahr, wenn migrate VOR dem Store-Bau lief
        assertTrue(dev.getMemory().containsMessage(marker));
    } finally {
        // Memory-Regel 12: kein persistenter State hinterlassen
        // 1) legacyRoot rekursiv löschen (Files.walk sorted reverse) — migration hat legacyState evtl. schon entfernt
        // 2) Safety: Files.deleteIfExists(expectedStateDir.resolve("Peon-Dev-history.jsonl"))
        //    (expectedStateDir nur einmal berechnen, im finally neu oder in Variable vor try)
    }
}
```
Details (verifiziert): `AiDevAgent.NAME` = „Peon-Dev" → Dateiname `Peon-Dev-history.jsonl` (safeAgentName lässt `[A-Za-z0-9._-]` unangetastet, `AbstractAgent.java:90-94`). `FileAgentHistoryStore.append` schreibt korrektes JSONL (Serializer, `FileAgentHistoryStore.java:42-50`); ein store-gebautes `UserMessage` lädt sauber zurück (`load()`, :26-40). `containsMessage(String)` existiert an `ThreadSafeMemory.java` (filtert User+Tool-Messages, toString-basiert) — Marker daher als Variable VOR dem try bauen und in beiden Stellen (append + Assertion) verwenden. Konstruktor-Pattern wie `newServiceWithPresenter` (:151-161) bzw. `beforeEach` (:66-72) — 5-arg-CTor, kein Presenter nötig. Kein `clearAll()` nötig (Service ist test-lokal); Datei-Delete im finally reicht.
Imports: `dev.langchain4j.data.message.UserMessage`, `org.sterl.llmpeon.memory.FileAgentHistoryStore` (Core-Dependency, wie inc-2/3), `java.nio.file.Files`. Keine UI-Threads nötig (Konstruktor only, kein send).

**Wiring-Referenz (unverändert, nur getestet):** migrate(:138) → AgentService(:140); `stateDir` = `Platform.getStateLocation(Platform.getBundle(PeonConstants.PLUGIN_ID)).append("state")` (:133-134).

Commit: `inc-6: copy directory-guard parity + migration wiring test (review findings)`.

Verifikation: core-Suite unverändert grün (651/651 — keine Core-Änderung); `eclipseBuildProject` über `org.sterl.llmpeon` + `org.sterl.llmpeon.test` VOR dem Plugin-Lauf; PDE-Suite komplett, erste Trust-Bestätigung abwarten, nicht parallel.

STATUS: COMPLETE — alle Inkremente (inc-1 bis inc-6) gebaut & committed (Branch `state-config-2026-09-06`). Wartet auf PO-Review; `planImplemented` erst nach Review-Freigabe.