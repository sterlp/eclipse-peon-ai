# Bug-Hunt-Fix-Zyklus — Branch `bug-hunt-2026-09-04` (2026-09-04)

## 1. Context

Bug-Hunt-Zyklus: 6 bestätigte Bugs (+ 1 neues Filter-Feature im ShellTool) fixen, jeweils
**rot vor grün**. SOLL-Quellen (alle bereits auf SOLL-Stand, **docs/ NIEMALS anfassen**):
- `docs/memory.md` — Triage-Liste + Scope (#1, #2+filter, #3, #4, #9, #13 jetzt; Rest später)
- `docs/shell-tool.md` (R1 tail, R2 filter) · `docs/disk-file-write-tool.md` (diskEditFile)
- `docs/custom-agents-design.md` (null-Allowlist) · `docs/write-path-validator.md` (R1 Traversal)
- `docs/streaming-display.md` (R17)

**Prozess-Harte Regeln (User):**
1. **Rot vor Grün:** jedes Increment startet mit einem Test, der das IST-Bug beweist und VOR dem
   Fix rot ist. Da Mek verifiziert das Rot per Testlauf, erst dann Fix, dann Grün.
   Ein Test, der auch ohne Fix grün wäre, ist kein Test.
2. Kleine vertikale Inkremente, je einzeln grün. **Maven Surefire = Ground Truth** für Test-Zahlen (core).
3. `docs/` nie anfassen.
4. Homepage pro Increment prüfen: geändertes Verhalten dort dokumentiert? → Update im selben Increment.
5. Commit nach jedem grünen Increment: `inc-N: <summary>` + Trailer `Assisted-by: Peon AI (<ModelName>)`,
   nur Increment-Dateien.
6. **planImplemented NICHT aufrufen** — PO-Review erst, Archivierung macht Da Mek nach Review.

## 2. Design-Entscheidungen (gesamt)

| # | Entscheidung | Warum |
|---|---|---|
| D1 | `FileUtils.applyEdit` liefert neuen Record `FileUtils.EditResult(String content, int count)` statt `String` | Count muss aus derselben Operation kommen (nach Line-Ending-Normalisierung) — separates Zählen wäre zweite Implementierung. Clean Break (Repo-Regel), keine Aliase. |
| D2 | Edit-Tools geben die Count-Meldung als **Rückgabewert** (String) zurück, nicht nur `onTool` | LLM sieht bei void-Tools nur "Success" (langchain4j `DefaultToolExecutor.toText`) — Count wäre für das LLM unsichtbar. SOLL: "Tool-Output meldet Anzahl". |
| D3 | `diskEditFile.newString` wird `required = false` (null → "") | SOLL `disk-file-write-tool.md`: "newString = null deletes the match" — heute `requireNonNull`. Code an SOLL alignen. |
| D4 | ShellTool-Filter wiederverwendet `SearchQuery` (core, regex-first/literal-fallback, case-insensitive, `matches(line)`, `literal()`) | "Ein Verhalten, eine Implementierung" (ADR-0035-Muster, wie Grep/Console-Tools). `LogExcerpt` NICHT nehmen: dessen Header ist console-spezifisch ("(console: …)"), SOLL-Format ist `filter: <pattern> (regex\|literal, showing N of M lines)`. |
| D5 | `StreamingBridge` bekommt package-private Konstruktor `StreamingBridge(Clock)`; Feld `startedAt = Instant.now(clock)` (Default `Clock.systemUTC()`) | Deterministischer Rot-Test ohne Sleep: ohne Clock-Injektion könnten zwei schnelle `Instant.now()` auf groben Uhren zufällig gleich sein (falsches Grün). |
| D6 | Path-Normalisierung für `AllowlistWriteValidator` = **string-segment-basiert** (`.`/`..` auflösen), kein `Path.normalize()` | `Path.normalize().toString()` liefert auf Windows `\`-Separatoren → Glob-Match bricht. Validierung ist projekt-agnostisch auf String-Ebene (SOLL), keine Dateisystem-Zugriffe, plattformunabhängig. |
| D7 | Count-Meldungsformat: `replaced N occurrence(s) in <path>` / `deleted N occurrence(s) in <path>` (deleted = newString null/leer) | SOLL-Phrase `replaced N occurrence(s)` als Substring enthalten; Pfad ergänzt (Kontext, wie bei `diskWriteFile` "Updated file: …"). |

## 3. Affected files (vollständig — Pfade für Da Mek)

**core** (`org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/`):
- `shared/FileUtils.java` — D1 (inc-1), D6-Helfer `normalizeSegments` (inc-5)
- `tool/tools/DiskFileWriteTool.java` — inc-1
- `tool/tools/ShellTool.java` — inc-2
- `agent/CustomAgent.java` — inc-3
- `streaming/StreamingBridge.java` — inc-4
- `tool/AllowlistWriteValidator.java` — inc-5
- `ai/LlmConfigLoader.java`, `ai/LlmConfig.java` — inc-6

**plugin** (`org.sterl.llmpeon/src/org/sterl/llmpeon/parts/`):
- `tools/EclipseWorkspaceWriteFileTool.java` — inc-1 (`eclipseEditFile`, `eclipseUpdateOpenFile`)
- `tools/PlanTool.java` — inc-1 (mechanisch: `planUpdate` nutzt `applyEdit`)
- `shared/EclipseUtil.java` — inc-1 (mechanisch: `editInEditor`, **Dead Code** — keine Referenzen)
- `config/LlmPreferenceInitializer.java` — inc-6 (nur Verifizieren: Default ist bereits `true`)

**core tests** (`org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/`):
- `shared/FileUtilsTest.java` — 7 `applyEdit`-Aufrufstellen auf `.content()` adaptieren + Count-Assertions
- `tool/DiskFileWriteToolTest.java` — inc-1 Rot-Test + Count-Tests
- `tool/ShellToolTest.java` — inc-2 (alle Aufrufe 5. Param `null`; neue R1/R2-Tests)
- `agent/CustomAgentServiceTest.java` — inc-3
- `streaming/StreamingBridgeTest.java` — **neu** (inc-4)
- `tool/AllowlistWriteValidatorTest.java` — inc-5
- `ai/LlmConfigLoaderTest.java` — inc-6 (Fixture: `ai/MapLlmConfigStore.java`, existiert)

**plugin tests** (`org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/`):
- `EclipseWorkspaceWriteFileToolTest.java` — inc-1 (Count im Workspace-Tool-Output, JUnit 4/OSGi)

**Homepage** (`homepage/src/setup/custom-agents.md`) — **nur inc-3** (siehe dort).

**Nicht anfassen:** `ToolPolicy.java` (bleibt strikt), `SearchQuery.java`, `LogExcerpt.java`,
`WriteValidator.java` (DOCS-Konstante), `ArgsUtil.java`, `PromptYmlParser.java`, `docs/*`.

## 4. Inkremente

### inc-0 — Docs-Commit
> ✅ done (29437af): 7 SOLL-Docs committet.
Bereits geänderte/neue SOLL-Docs committen (keine eigenen Edits!):
`docs/custom-agents-design.md`, `docs/disk-file-write-tool.md`, `docs/index.md`, `docs/memory.md`,
`docs/streaming-display.md`, `docs/write-path-validator.md`, `docs/shell-tool.md` (untracked).
Commit: `docs: Bug-Hunt SOLL-Updates` (+ Assisted-by-Trailer).

### inc-1 — Bug #1: applyEdit Count + ehrliche Beschreibungen
> ✅ done (3bd3acb): EditResult + Count-Meldung; Core 568/568, OSGi 177/177.
**SOLL:** `docs/disk-file-write-tool.md` (diskEditFile-Abschnitt). Replace-All bleibt;
Tool meldet Anzahl; 0 Matches = Fehler (bleibt); Count auch bei Delete; Javadoc + @Tool-Beschreibungen
beider Familien sagen heute "first occurrence" → korrigieren.

**ROT-Test (core, `DiskFileWriteToolTest`):** `diskEditFile_reportsReplacementCount`
— Temp-File mit 2 Vorkommen von `x`; Aufruf **über `ToolService.execute`** (eigene
`ToolService(false)` + `ts.addTool(tool)` + `ToolExecutionRequest` für `diskEditFile`,
LoopRequest wie im bestehenden `docsRequest()`-Pattern). Assert: Result enthält
`replaced 2 occurrence(s)`. **Heute rot:** void-Tool → LLM sieht "Success".
(Ein direkter Methodenaufruf wäre kein Rot-Test — void lässt sich nicht asserten; der
ToolService-Pfad ist der ehrliche LLM-sichtbare Vertrag.)

**Fix:**
1. `FileUtils.applyEdit` → `EditResult` (D1). Count = Nicht-überlappende Vorkommen des
   **normalisierten** oldStr (indexOf-Loop; Guard: leeres oldStr → Count 0, Replace-Verhalten bleibt).
   Javadoc: "Replaces **all** occurrences … returns new content + count; throws on zero matches".
2. `DiskFileWriteTool.diskEditFile` → `String` (D2/D3/D7): `@P(newString, required = false)`,
   null→"", Identical-Check nach Normalisierung; Rückgabe `replaced/deleted N occurrence(s) in <relPath>`.
   @Tool: "Replace **all** occurrences of an exact string; reports how many were replaced.
   newString=null/empty deletes the matches. Error if not found or identical."
3. `EclipseWorkspaceWriteFileTool.eclipseEditFile` → `String`, gleiches Format mit Workspace-Pfad.
   @Tool: "Replace **all** occurrences …; reports how many were replaced. newString=null deletes the matches."
4. `EclipseWorkspaceWriteFileTool.eclipseUpdateOpenFile` (nutzt ebenfalls `applyEdit`) → Count in
   die bestehende Rückgabe-Meldung ergänzen (z.B. `Saved! of <path> — replaced 2 occurrence(s)`).
5. `PlanTool.planUpdate` + `EclipseUtil.editInEditor` (Dead Code) → nur auf `EditResult` adaptieren
   (`.content()`), Meldungen unverändert.
6. Tests: `FileUtilsTest` 7 Stellen `.content()`; `applyEdit_replacesMultipleOccurrences` bleibt grün
   **+** `count() == 2` asserten; neuer Delete-Count-Test (newString "" → `deleted N`);
   Plugin-Test `EclipseWorkspaceWriteFileToolTest.test_editWorkspaceFile_reportsCount`
   (2 Vorkommen → Ergebnis enthält `replaced 2 occurrence(s)`).

**Homepage:** kein Edit-Tool-Dokument → keine Änderung.
**Commit:** `inc-1: applyEdit reports replacement count; fix lying 'first occurrence' descriptions`.

### inc-2 — Bug #2: ShellTool tail-Semantik + neues `filter`-Feature
> ✅ done (72eec78): tail Default 60, 0/-1=all (Hard-Cap 3000), filter (regex-first/literal-fallback) + Disclosure; Core 575/575.
**SOLL:** `docs/shell-tool.md` R1 + R2.

**ROT-Test R1 (core, `ShellToolTest`):** `runOsCommand_tailLinesMinusOneReturnsAll`
— Command mit 100 Zeilen (`for i in $(seq 1 100); do echo line $i; done`), `tailLines=-1` →
Assert `contains("line 1")`. **Heute rot:** `ArgsUtil.getOrDefault` mappt `-1` → Default 50,
nur letzte 50 Zeilen.

**Fix R1:**
- `DEFAULT_TAIL_LINES` 50 → **60**.
- `shellRunCommand`: `if (tailLines == null) tailLines = DEFAULT_TAIL_LINES;`
  (`ArgsUtil.getOrDefault`-Zeile entfernen — die mappt `<=0` auf Default und macht "all" unerreichbar).
- Interne `tailLines(List, Integer)`: `maxLines == null || maxLines <= 0` → `maxLines = MAX_OUTPUT_LENGTH`
  (3000) — **Hard-Cap gilt jetzt auch für "all"** + bestehende `... (N lines skipped)`-Disclosure.
- `@P`-Beschreibung tailLines: "max tail lines, default=60; 0 or -1 = all (hard cap 3000);
  use this instead of `| tail -50`".
- `onTool`-Meldung Success-Pfad: `Math.min(lines.size(), tailLines)` zeigt heute bei -1 "reading -1 lines"
  → effektives Limit + gefilterte Größe verwenden.

**Fix R2 (filter):**
- Neuer optionaler Param: `@P(description = "filter output lines (regex, literal fallback) — like `| grep`",
  name = "filter", required = false) String filter`.
- Nach dem Sammeln von `lines`: `var q = (filter == null || filter.isBlank()) ? null : SearchQuery.of(filter);`
  `var shown = q == null ? lines : lines.stream().filter(q::matches).toList();`
  — `shown` ersetzt `lines` in **allen** `tailLines(...)`-Aufrufen (Erfolg, Timeout, Error, Interrupt).
- Disclosure-Zeile, **erste Zeile** des Outputs, nur wenn Filter aktiv:
  `filter: <pattern> (<regex|literal>, showing <N> of <M> lines)` — N = tatsächlich gezeigte Zeilen
  (nach tail), M = Gesamtzeilen. 0 Matches → `showing 0 of M lines` (kein stilles Leere).
  Modus: `q.literal() ? "literal" : "regex"`.
- Timeout-Sonderfall `lines.isEmpty() && commandUsesShellTail` bleibt unverändert (Hint).
- `commandUsesShellTail`-Hint: NICHT ändern (SOLL-Note sagt nur "kann").

**Tests (alle core, `ShellToolTest`; bestehende Aufrufe um 5. Param `null` ergänzen):**
- R1: Default 60 (100 Zeilen, kein Param → `line 41` + `line 100` vorhanden, `line 40` nicht,
  `40 lines skipped`); `tailLines=20` → letzte 20; `-1` → alle 100 (der Rot-Test);
  **4000 Zeilen + `-1` → 3000 Zeilen + `1000 lines skipped`** (Hard-Cap, BDD-Fall 4).
- R2: Match (100 Zeilen, filter `line 4` → 10 Zeilen, Disclosure
  `filter: line 4 (regex, showing 10 of 100 lines)`, `line 5` nicht enthalten);
  Kein Match (filter `KEINMATCH` → `showing 0 of 100 lines`);
  Ungültige Regex (filter `[ungültig` → `literal` im Modus, exakte Zeichenfolge);
  Filter+Tail (filter `line` → 100 Matches, tailLines=20 → `showing 20 of 100 lines` + `80 lines skipped`);
  Filter im Fehlerpfad (exit≠0 → Disclosure + `Exit code:`).

**Homepage:** keine Shell-Tool-Seite (Grep: kein `tailLines`/`shellRunCommand`/`shell` außer
read-only-Zeile) → keine Änderung.
**Commit:** `inc-2: ShellTool tail 60/all-semantics + filter parameter (regex-first, literal fallback)`.

### inc-3 — Bug #3 + Q3: CustomAgent null-Allowlist = all + CSV-Flattening
> ✅ done (8bed72f): `allowed()` → `PromptYmlParser.toolAllowlist` (null=all, CSV-Flatten); rot war 3 Fails (absent→null→blocked, CSV nicht geflattet); Core 579/579; Homepage 3 Stellen.
**SOLL:** `docs/custom-agents-design.md` (Components: `ToolPolicy.enables` strikt, null-Allowlist
wird in `CustomAgent.getToolFilter()` vor der read-only-Regel auf allow-all gemappt;
`tools` accepts a YAML block list **or inline CSV**).

**ROT-Test 1 (core, `CustomAgentServiceTest`):** `absentToolsAllowAll`
— Agent **ohne** `tools:`-Frontmatter (bestehendes `newAgent(file)` ohne `setTools`):
Assert `isToolActive(exec("read_file"))` true, `isToolActive(exec("write_file"))` true,
`getToolNameFilter().test("mcp__docs__search_docs")` true. **Heute rot:** `ToolPolicy.enables(null, …)` = false → alles gesperrt.

**ROT-Test 2 (core, `CustomAgentServiceTest`):** `inlineCsvToolsFlattened`
— Agent mit `tools: grep, read_` (inline CSV im Frontmatter):
Assert `isToolActive(exec("grep"))` true, `isToolActive(exec("read_"))` true.
**Heute rot:** Liste enthält einen Eintrag `"grep, read_"` → matcht weder `grep` noch `read_`.

**Fix (1 Stelle, `CustomAgent.allowed(String)`):**
```java
var allowlist = PromptYmlParser.toolAllowlist(getTools());
return allowlist == null || ToolPolicy.enables(allowlist, toolName);
```
- `PromptYmlParser.toolAllowlist` (existiert, `PromptYmlParser.java:149`): null → null (all-Signal),
  Inline-CSV `grep, read_` → `["grep", "read_"]`, YAML-Block-Liste bleibt unverändert.
- null → `true` (all); leere Liste → `ToolPolicy.enables([], …)` = false (nichts).
- Deckt `getToolFilter()` **und** `getToolNameFilter()` (beide delegieren an `allowed`).
- `ToolPolicy` unverändert.

**Tests:** Rot-Tests 1+2 oben; `absentToolsReadOnlyOnlyReadTools` (read-only + ohne tools → nur read_file);
`emptyToolsAllowNothing` (leere Liste → nichts — Pin, heute schon grün);
`inlineCsvToolsFlattened` (CSV → beide Tools aktiv).

**Homepage (im selben Increment!):** `homepage/src/setup/custom-agents.md`:
1. Feld-Tabelle, Zeile `tools`: "**Omit it and the agent gets _no_ tools** — use `- '*'` to allow all."
   → "**Omit it and the agent gets _all_ tools**; an empty list allows none."
2. Abschnitt "Tool allowlist": "- **field omitted** — **no tools** (use `- '*'` if you want all of them)."
   → "- **field omitted** — **all tools** (an empty list allows none)."
3. Abschnitt "Tool allowlist", letzte Zeile: "Use the YAML block-list form (one `- entry` per line), as in the example above."
   → "Use the YAML block-list form (one `- entry` per line) or inline CSV (`tools: grep, read_`)."

**Commit:** `inc-3: CustomAgent null-Allowlist → all + inline-CSV flattening (+ homepage)`.

### inc-4 — Bug #4: StreamingBridge startedAt pro Turn
> ✅ done (4b3478a): `startedAt` final (Konstruktor + package-private `StreamingBridge(Clock)`), Reset in `call()` entfernt; Core 580/580.
**SOLL:** Javadoc der Klasse ist bereits korrekt (1 Bridge/Turn, `call()` keeps `startedAt`);
UI "working since Xs" darf nicht pro Tool-Call springen.

**ROT-Test (core, NEU `streaming/StreamingBridgeTest`):** `startedAtSpansWholeTurn`
— Bridge mit injiziertem `Clock` (D5), fix bei T0; Mocked `StreamingChatModel`
(Pattern aus `TokenUsageAccumulationTest`: `doAnswer` → `onCompleteResponse`);
Monitor erfasst `OnPartialAiResponse`-Chunks (Feld `startedAt`).
Call 1 → START-Chunk.startedAt == T0. Clock um 1 h vor. Call 2 → START-Chunk.startedAt **== T0**.
**Heute rot:** `call()` setzt `startedAt = Instant.now(clock)` → T0+1h.

**Fix:** `StreamingBridge`:
- `private final Instant startedAt = Instant.now(clock);` + Feld `private final Clock clock = Clock.systemUTC();`
  + package-private `StreamingBridge(Clock clock)` (Default-Konstruktor delegiert).
- Zeile `startedAt = Instant.now();` in `call()` **entfernen**.
- Method-Javadoc "Sets `startedAt` on the first invocation only." → "startedAt is set at
  construction (turn start) and kept across all calls of this turn."
- `ConfiguredChatModel.callBlocking` (`new StreamingBridge()` pro Call) bleibt korrekt — nicht anfassen.

**Commit:** `inc-4: StreamingBridge keeps startedAt across tool-loop calls (one start per turn)`.

### inc-5 — Bug #9: AllowlistWriteValidator Traversal
> ✅ done (fda110d): `FileUtils.normalizeSegments` (string-segment, D6) + Validator normalisiert vor Glob-Match (Fehlermeldung zeigt Original-Pfad); rot war `a/docs/../../secret.txt` matchte `*/docs/*` → erlaubt; Core 587/587.
**SOLL:** `docs/write-path-validator.md` (normalized path, BDD R1 Traversal-Fall).

**BDD-String:** PO-korrigiert in `write-path-validator.md` auf den echten Bypass
`a/docs/../../secret.txt` (roh: matcht `*/docs/*` → heute erlaubt; normalisiert: `secret.txt` → außerhalb).
Der alte String `docs/../../secret.txt` (enthält kein `/docs/`-Substring → matcht `*/docs/*` nicht)
wird ebenfalls abgewiesen — bleibt als Regression-Pin.

**ROT-Test (core, `AllowlistWriteValidatorTest`):** `rejectsTraversalThroughDocs`
— `docs.validate("a/docs/../../secret.txt")` → `assertThrows(IllegalArgumentException)`.
**Heute rot:** rohes Glob-Match erlaubt es (matcht `*/docs/*` → erlaubt; normalisiert → `secret.txt` → außerhalb).
**Regression-Pin (zusätzlich, heute schon grün):** `rejectsBddTraversalExample` —
`docs.validate("docs/../../secret.txt")` → throws (sichert den alten BDD-String; enthält kein
`/docs/`-Substring → matcht `*/docs/*` nicht → wird heute schon abgewiesen).

**Fix:**
- Neuer Helfer `FileUtils.normalizeSegments(String)` (D6): string-segment-basiert auf `/`:
  `.` skip, `..` pop (leerer Stack → `..` behalten, POSIX-artig), leading `/` bleibt,
  Ergebnis mit `/` joined. Keine `\`-Umwandlung, kein Dateisystem.
  Unit-Tests: `docs/../../x` → `../x`; `a/b/../c` → `a/c`; `./x` → `x`;
  `/abs/docs/../x.md` → `/abs/x.md`; `a/docs/../../secret.txt` → `secret.txt`.
- `AllowlistWriteValidator.validate`: `var normalized = FileUtils.normalizeSegments(path);`
  Glob-Match auf `normalized`; **Fehlermeldung zeigt den ORIGINAL-Pfad** (LLM-verständlich).
  Javadoc: "matched against the **normalized** path".

**Tests:** Rot-Test + BDD-Pin + bestehende Positive bleiben grün (`MyProject/docs/feature.md`,
`docs/feature.md`, `README.md`, `proj/docs/img/logo.png`, `src/main/java/Foo.java` rejected).
Plugin-Tests `EclipseWriteValidatorUnitTest` + `ToolLoopRequestWriteValidatorTest` bleiben grün
(normalisiert = identisch für diese Pfade) — im Abschlusslauf verifizieren.

**Homepage:** `peon-po.md` dokumentiert keine Pfad-Details → keine Änderung.
**Commit:** `inc-5: AllowlistWriteValidator normalizes path before glob match (closes traversal bypass)`.

### inc-6 — Bug #13: showRealtimeAiResponse Default = on
> ✅ done (eb36e77): Loader-Fallback + `@Default` false→true; Preference-Default bereits `true` (verifiziert, nicht geändert); rot war `Expecting value to be true but was false`; Core 588/588.
**SOLL:** `docs/streaming-display.md` R17 (Loader-Default **und** Preference-Default = `true`).

**ROT-Test (core, `LlmConfigLoaderTest`):** `loaderDefaultsShowRealtimeAiResponse`
— `LlmConfigLoader.load(new MapLlmConfigStore())` → `assertThat(config.isShowRealtimeAiResponse()).isTrue()`.
**Heute rot:** Loader-Fallback `parseBoolean(null, false)`.

**Fix (2 Stellen, 1 verifizieren):**
- `LlmConfigLoader.load`: `.showRealtimeAiResponse(parseBoolean(…, true))` (false → true).
- `LlmConfig`: `@Default private final boolean showRealtimeAiResponse = true;` (false → true).
- `LlmPreferenceInitializer.initializeDefaultPreferences`: `PREF_SHOW_REALTIME_AI_RESPONSE` = `true`
  — bereits korrekt, **nur verifizieren**, nicht ändern.
- Keine Tests asserten heute auf `false` (Grep verifiziert) → keine Anpassungen.

**Homepage:** keine Erwähnung von "realtime"/"real time" → keine Änderung.
**Commit:** `inc-6: showRealtimeAiResponse defaults to on (loader + builder aligned with preference)`.

## 5. Abschluss (nach inc-6, kein eigener Commit außer Repo-Status)
1. `eclipseBuildProject` über `org.sterl.llmpeon.core` **und** `org.sterl.llmpeon` (stale-Bin-Guard).
2. Core: `mvn test` (Surefire = Ground Truth, Zahlen berichten).
3. Plugin: `eclipseRunTests` über `org.sterl.llmpeon.test` (OSGi; erster Lauf braucht ggf.
   Workspace-Trust-Dialog — ganze Suite starten, Timeout → User informieren, nicht parallel nachstarten).
4. Repo-Status melden (git status + Commit-Liste `inc-0`…`inc-6`). **planImplemented NICHT** — PO-Review erst.

## 6. Regeln & Constraints
- Rot-Verifikation ist ein **eigener Testlauf** vor dem Fix (Da Mek berichtet Rot-Output).
- Test-Zahlen nur aus Maven Surefire (core); PDE-Runner-Zahlen dürfen nicht als Ground Truth zitiert werden.
- `docs/` read-only. Homepage nur in inc-3.
- Secrets nie in Meldungen (nicht betroffen, aber bei neuen Log/Tool-Strings beachten).
- `System.lineSeparator()` in Tool-Output-Strings (ShellTool-Disclosure, Count-Meldungen sind
  Single-Line — kein `\n`-Hardcode in Multi-Line-Outputs).
- Kein Scope-Creep: nur die 6 Bugs + filter-Feature. Gefundene Nebenbugs → Q3/Q4, nicht fixen.
- Commits nur auf Branch `bug-hunt-2026-09-04` (aktiv laut User), nur Increment-Dateien.

## 7. Open Questions / Befunde für den PO
- **Q1 (Bug #9 BDD-Beispiel):** ✅ Gelöst — PO hat `write-path-validator.md` auf den echten Bypass
  `a/docs/../../secret.txt` korrigiert. Plan nutzt ihn für den Rot-Test; der alte String
  `docs/../../secret.txt` bleibt als Regression-Pin (beide müssen throwen).
- **Q2 (Bug #1 SOLL-Lücke):** SOLL-Doc verspricht für `diskEditFile` "newString = null deletes",
  Code verlangte non-null. Plan macht `newString` optional (D3) — Code an SOLL alignen.
  Falls PO das anders will (null bleibt Fehler im Disk-Tool), fehlt die `deleted N`-Variante
  dort — Eclipse-Tool deckt sie trotzdem.
- **Q3 (Nebenbug → inc-3):** ✅ In Scope genommen — Inline-CSV-Allowlist `tools: grep, read_`
  wird in inc-3 gefixt (Flattening via `PromptYmlParser.toolAllowlist` in `CustomAgent.allowed`).
- **Q4 (Nebenbefund):** `EclipseUtil.editInEditor` ist Dead Code (0 Referenzen) — wird in inc-1
  nur mechanisch adaptiert; Löschung wäre späterer Cleanup.
- **Q5 (Skill-Hint):** falls der Plugin-Testlauf (OSGi/PDE) ein neues Launch-Problem zeigt, das
  nicht in `skills/eclipse-dpe` steht → SKILL um das Know-how ergänzen (AGENTS.md §Reference).
