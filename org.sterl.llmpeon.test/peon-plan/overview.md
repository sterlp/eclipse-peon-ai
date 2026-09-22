# Feature Tool-Time-Disclosure: `CallStats`-Helper + Stats-Suffix für Shell/RunTests/Build

## ⛔ STOP-AND-ASK (zuerst lesen)
Bei **Compile-Fehlern ohne Lösung**, **nicht-grün-bekommenden Tests**, **IST-Widersprüchen zu
diesem Plan** oder **flaky/integrierten Build-Tests**: STOPP und Rückfrage über den Jon-Kanal —
nie still workarounden, nie SOLL ändern.
- UC-Status bleibt in allen Docs **❌** (UC-TD-1 ist ✅ *(geplant:_unit)* — bleibt, wie es ist).
  Flippen macht **ausschließlich Jon** nach Review. Niemand in diesem Zyklus rührt die
  Feature-Docs an (nur Code + Tests; die ausstehenden Docs-Änderungen von Jon werden mit I1
  committet, aber NICHT inhaltlich verändert).

## 1. Kontext
SOLL: `docs/tool-time-disclosure.md` (R-TD-1…4, UC-TD-1…4) + `docs/adr/0053-call-stats-shared-helper.md`.
Ziel: Das LLM sieht am Ende von langlaufenden Tool-Aufrufen Dauer + Uhrzeit (`(3s, 14:32)`),
Vorbild ist das private `PoDelegateTool`-Muster. Scope = **Option B (Paul): exakt R-TD-1…4** —
keine weiteren Tools (Out-of-Scope-Liste steht im Feature-Doc; webFetch/memory/continue u. a.
= eigene Stories, hier NICHT anfassen).
- **R-TD-1**: core-Helper `CallStats` (`org.sterl.llmpeon.shared`), Clock injizierbar;
  `PoDelegateTool.dispatchStats` migriert darauf, Output **byte-identisch** (Pin).
- **R-TD-2**: `ShellTool.shellRunCommand` — Suffix nach Exit-code-Teil; Timeout: gemessene Dauer.
- **R-TD-3**: `EclipseRunTestTool.eclipseRunTests` — Report + Suffix; Timeout: gemessene Dauer.
- **R-TD-4**: `EclipseBuildTool.eclipseBuildProject` — Report + Suffix.

Branch: **`analysis/tool-evolution`** (Repo-Root Disk `/Users/sterlp/dev/workset/peon-ai` =
Workspace-Projekt `llmpeon-parent`; f2 + compact-lock gemerged, erwartet Tip `bee7985`).

## Status
- Vorbereitungen: ✅ (2026-09-22) — Git: Branch `analysis/tool-evolution`, Tip `bee7985`, die 5
  Jon-Docs exakt wie erwartet vorhanden (Abweichung gemeldet: die TD-Plan-Datei ersetzt an
  gleicher Stelle das f2-Archiv — Jon). Baseline: Core Surefire **908/0/0**, Plugin **261/0/0**
  (erwartet ~908 / ~261).
- I1: offen (CallStats + PoDelegate-Migration + Pin).
- I2: offen (ShellTool-Suffix).
- I3: offen (eclipseRunTests-Suffix).
- I4: offen (eclipseBuildProject-Suffix).

## 2. Slicing — 4 vertikale Inkremente, je für sich grün
Jedes Inkrement: Gate grün → **Commit auf `analysis/tool-evolution`** (Rule 19, nur geänderte
Code-/Test-Dateien; bei I1 zusätzlich die Jon-Docs, s. Vorbereitungen).

### Vorbereitungen (vor I1 — einmalig)
1. Git-Zustand prüfen: Branch `analysis/tool-evolution`, Tip `bee7985`, Working Tree enthält
   **genau** diese ausstehenden Docs-Änderungen von Jon: `docs/tool-time-disclosure.md`,
   `docs/adr/0053-call-stats-shared-helper.md`, `docs/adr/index.md`, `docs/index.md`,
   `docs/open-points.md`. Weich etwas ab (falscher Branch, fremde Änderungen, fehlende/mehr
   Dateien): STOP-AND-ASK.
2. **Baseline neu messen** (Paul: nach dem Merge neu, nicht glauben): `eclipseRunTests`
   Project `llmpeon-core` (erwartet ~908/0) **und** `eclipseRunTests` Project
   `org.sterl.llmpeon.test` (erwartet ~261/0; erster Lauf: Workspace-Trust-Dialog manuell
   bestätigen, Rule 13 — nicht parallel nachstarten). Reale Zahlen in §Status eintragen.
   Rote oder stark abweichende Baseline: STOP-AND-ASK.

### Inkrement 1 — `CallStats` + PoDelegate-Migration (Core)
**UC-IDs: UC-TD-1** · Polarität: neue Klasse + Tests; PoDelegate-Änderung = replace-in-place
mit identischem Output (bewusst, SOLL R-TD-1).
- **NEU** `llmpeon-core/src/main/java/org/sterl/llmpeon/shared/CallStats.java`:
  ```java
  public final class CallStats {
      private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
      private final Clock clock;
      private final long startedAtMillis; // clock.millis() bei Erstellung

      public static CallStats start()            // Clock.systemDefault() — HH:mm LOkal
      public CallStats(Clock clock)              // Test-Injektion (Vorbild shared.Timer)
      public String duration()                   // StringUtil.humanElapsed(clock.millis() - startedAtMillis)
      public String durationSuffix()             // "(" + duration() + ")"            → "(3s)"
      public String suffix()                     // "(" + duration() + ", "
                                                 //   + LocalTime.now(clock).format(HH_MM) + ")" → "(3s, 14:32)"
  }
  ```
  - `StringUtil.humanElapsed` bleibt die **einzige** Dauer-Formatierung (ADR-0053);
    `LocalTime.now(clock)` — nie `LocalTime.now()` (Injizierbarkeit, ADR-0053 Fallstricke).
  - `start()` nutzt `Clock.systemDefault()` (lokale Zone — Output-Format wie heute HH:mm lokal).
- **ÄNDERUNG** `llmpeon-core/src/main/java/org/sterl/llmpeon/poagent/tools/PoDelegateTool.java`:
  - `dispatch` (:225-254): `long startNanos = System.nanoTime()` (:233) +
    `elapsedMillis` (:240) ersetzen durch `var stats = CallStats.start();` vor `slave.call`.
    Stats-Zeile (:241) wird: `target.uiName() + " done. " + contextUsed(slave) + " " + stats.suffix()`
    → **byte-identisch** zum alten `dispatchStats`-Output
    (`Context: N token - X% used. (1m 5s, 14:32)`); `onTool(stats)` (:246) unverändert nutzen.
  - **Entfernen**: `private String dispatchStats(...)` (:256-259, **einziges** Aufrufstellen-Paar —
    per Grep verifiziert: nur :241) und `COMPLETION_TIME` (:66) + damit ungenutzte
    `LocalTime`/`DateTimeFormatter`-Imports.
- **TESTS**:
  - **NEU** `llmpeon-core/src/test/java/org/sterl/llmpeon/shared/CallStatsTest.java` (JUnit 5 +
    AssertJ, `// UC-TD-1`-Kommentare), test-lokale `MutableClock` (Punkt-for-Punkt-Muster
    `TimerTest.java:21-27`, eigene Instanz; Zone UTC):
    - T0 = `Instant.parse("2026-09-22T14:32:00Z")`; 2400 ms weiter → `durationSuffix()` = `"(2s)"`,
      `suffix()` = `"(2s, 14:32)"` (exakt, deterministic).
    - 65 s weiter → `suffix()` = `"(1m 5s, 14:33)"` (Minute-Form **und** LocalTime wird zum
      `suffix()`-Zeitpunkt berechnet, nicht beim Start).
    - 0 ms → `"(0s, 14:32)"` (Sub-Second = "0s", humanElapsed-Vertrag).
    - `start()` (System-Clock): `suffix()` matcht `\(\d+(?:m \d+)?s, \d{2}:\d{2}\)` (Form-Only).
  - **NEU** Pin-Test in `PoDelegateToolTest` (bestehende Tests :53-82 u. a. **UNVERÄNDERT**):
    ```java
    // UC-TD-1
    @Test void dispatchReplyPinsStatsSuffixFormat() {
        var reply = newTool().askDev("x");
        assertThat(reply).containsPattern(
            "done\\. Context: \\d+ token - \\d+% used\\. \\(\\d+(?:m \\d+)?s, \\d{2}:\\d{2}\\)");
    }
    ```
    Pinnt das volle Stats-Ende — Migration darf daran nichts rütteln.
- **GATE I1**: `eclipseRunTests` Project `llmpeon-core` — **komplette Suite** grün (mindestens
  Baseline-Anzahl + neue Tests). Dann Commit **inkl. der 5 Jon-Docs** aus den Vorbereitungen.

### Inkrement 2 — `shellRunCommand`-Suffix (Core)
**UC-IDs: UC-TD-2** · Polarität: nur-hinzufügen (Suffix-Appends) + eine SOLL-mandatierte
Timeout-Textänderung (konfiguriert → gemessen).
- **ÄNDERUNG** `llmpeon-core/src/main/java/org/sterl/llmpeon/tool/tools/ShellTool.java`
  (`shellRunCommand` :58-176):
  - `var stats = CallStats.start();` direkt vor `var process = pb.start();` (:115).
  - **Success-Pfad** (:152-162): nach dem `Exit code`-Append (:157-159), vor `return resultStr`
    (:162): `resultStr += System.lineSeparator() + stats.suffix();` — Ausnahme: leeres
    `resultStr` (keine Ausgabe, exit 0) → nur `return stats.suffix();` (kein leading Separator).
  - **Timeout-Pfad** (:149): `return "Command timed out after " + stats.duration()
    + ". Partial output:\n" + partial + System.lineSeparator() + stats.suffix();`
    — `timeout` (konfiguriert) wird in der Meldung **durch die gemessene Dauer ersetzt**
    (R-TD-2 bold: „nie die konfigurierte Timeout-Sekundenzahl als Dauer"); Suffix am Ende.
    Die onTool-Zeile (:148) bleibt unverändert.
  - **KEIN Suffix** an: „denied"-Early-Return (:79-86), IOException-Pfad (:164-168),
    InterruptedException-Pfad (:169-174) — dort lief keine gemessene Kommandoausführung.
  - Regressions-Check (erledigt, Plan-Ist): `ShellToolTest` assertet ausschließlich per
    `contains`/`startsWith` (Start = erste Zeile) — kein Test pinnt Timeout-Text oder
    Gesamt-Output; `startsWith("line 1")` (:89) bleibt grün, da Suffix ans **Ende**.
- **TESTS** (in `ShellToolTest`, echte Kurzkommandos wie die restliche Klasse):
  - `// UC-TD-2` + `shellRunCommand_reportsDurationAndTime`: `sleep 2` → letzte Zeile
    (per `linesOf(result)` letzte) matcht `\(\d+s, \d{2}:\d{2}\)` **und** enthält `2s`
    (Dauer gemessen, nicht geraten).
  - `// UC-TD-2` + `shellRunCommand_timeout_reportsMeasuredDurationAndTime`: `sleep 5`
    mit `timeout=1` → `contains("Command timed out after 1s")` (gemessen ≈ 1 s; **nicht**
    mehr die konfigurierte Zahl als Dauer) + letzte Zeile matcht `\(\d+s, \d{2}:\d{2}\)`.
  - `// UC-TD-2` + `shellRunCommand_nonZeroExit_endsWithStats`: `exit 42` →
    `contains("Exit code: 42")` + Suffix-Zeile am Ende (Failure-Pfad, R-TD-2).
- **GATE I2**: komplette Core-Suite grün → Commit.

### Inkrement 3 — `eclipseRunTests`-Suffix (Plugin)
**UC-IDs: UC-TD-3** · Polarität: nur-hinzufügen (Suffix-Appends, neuer statischer
Assembler, Signatur-Erweiterungen an privaten/package-private Methoden).
- **ÄNDERUNG** `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/EclipseRunTestTool.java`:
  - `var stats = CallStats.start();` nach `ArgsUtil`-Validierung, Kopf von `eclipseRunTests`
    (:68) — misst Setup + Launch + Warte (ganzer Aufruf).
  - `runAndCollect` (:192) bekommt `CallStats stats`-Parameter (private; einziger Caller :152).
  - **Success** (:243): `formatResults(sessionName[0], testCount[0], skippedCount[0],
    failures, errorCount, stats)` — `formatResults` (:322-336) wird **package-private static**
    (war private), neuer letzter Parameter `CallStats stats`; am Ende
    `sb.append(System.lineSeparator()).append(stats.suffix());` (Header-Zeilen
    `Test run / Tests / Skipped / Failures` + Failure-Block unverändert).
  - **Timeout** (:236-239): neue **package-private static**
    `String timeoutReport(int testsRan, int failuresSoFar, CallStats stats)`:
    `"Test run timed out after " + stats.duration() + ". " + testsRan + " tests ran, "
    + failuresSoFar + " failures so far."` + `System.lineSeparator()` + `stats.suffix()`.
    Die bisherige Meldung mit `MAX_TEST_DURATION.toMinutes()` (:53, „5 minutes") wird
    **ersetzt durch die gemessene Dauer** (R-TD-3: „steht die gemessene Dauer, nicht die
    konfigurierte Grenze"). `MAX_TEST_DURATION` bleibt der WaitGuard.
  - `formatResults`/`timeoutReport` sind die einzigen Callers bzw. neu — kein anderer Code
    berührt (Regressions-Check: `EclipseRunTestToolTest` testet heute nur
    `PdeTestLaunchConfig`, kein Report-Pin; `SharedToolsComponentTest` zählt nur Executoren).
- **TESTS** (in `EclipseRunTestToolTest`, JUnit 4, OSGi — **kein** echter Test-Launch):
  - `// UC-TD-3` + `formatResultsEndsWithStatsSuffix`: `Clock.fixed(Instant.parse("2026-09-22T14:32:00Z"),
    ZoneOffset.UTC)` → `new CallStats(clock)` → `EclipseRunTestTool.formatResults("All tests in X",
    3, 0, new ArrayList<>(), 5, stats)` → beginnt mit `"Test run: All tests in X"` +
    `"Tests:    3"` (Header-Regression) **und** letzte Zeile exakt `"(0s, 14:32)"`.
  - `// UC-TD-3` + `timeoutReportUsesMeasuredDuration`: test-lokale `MutableClock`
    (Muster `TimerTest:21-27`, Zone UTC, JUnit-4-tauglich), 175 s weiter →
    `EclipseRunTestTool.timeoutReport(10, 2, stats)` → beginnt mit
    `"Test run timed out after 2m 55s."` + enthält `"10 tests ran, 2 failures so far."`
    + letzte Zeile exakt `"(2m 55s, 14:34)"`.
- **GATE I3**: `eclipseBuildProject` über `org.sterl.llmpeon` **und** `org.sterl.llmpeon.test`
  (Rule 16: stale bin/!), dann `eclipseRunTests` Project `org.sterl.llmpeon.test` — **ganze
  Suite** grün (Baseline aus Vorbereitungen + neue Tests). Commit.

### Inkrement 4 — `eclipseBuildProject`-Suffix (Plugin)
**UC-IDs: UC-TD-4** · Polarität: nur-hinzufügen.
- **ÄNDERUNG** `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/EclipseBuildTool.java`
  (`eclipseBuildProject` :190-213):
  - `var stats = CallStats.start();` im `try`, direkt vor `deleteMarkers` (:202) —
    misst Clean + Full Build + Probleme-Lesen.
  - **Einziger** String-Return (:209) wird:
    `return readProblems(projectRef) + System.lineSeparator() + stats.suffix();`
  - **`readProblems` (:54-142) bleibt UNVERÄNDERT** — es ist mit `eclipseReadProjectProblems`
    (:40-52, **Out of Scope**, darf KEIN Suffix bekommen) geteilt; der Suffix gehört auf
    Build-Tool-Ebene.
  - **KEIN Suffix** an: not-found-Early-Return (:194-198, kein Build gelaufen) und
    `RuntimeException`-Pfad (:210-212, kein Output-String). „Failure-Pfad inklusive"
    (UC-TD-4) = Report **mit** Build-Fehlern — läuft durch dieselbe Return-Statement,
    daher strukturell abgedeckt (synthetische Marker sind vor dem Build durch
    `deleteMarkers` weg — kein separater Test dafür, siehe Test-Strategie).
- **TEST** (in `EclipseBuildToolTest` — Integration, wie die UC-PP-Tests der Klasse):
  - `// UC-TD-4` + `@Test(timeout = 120000) buildReportEndsWithStatsSuffix`:
    `assumeTrue(isWorkspaceAvailable())`; `String result = tool.eclipseBuildProject(
    PeonTestFixture.PROJECT_NAME);` → letzte Zeile matcht `\(\d+(?:m \d+)?s, \d{2}:\d{2}\)`
    **und** Ergebnis enthält `"Project "` (Report-Body-Regression, ob mit oder ohne
    Probleme).
  - Falls der Clean+Full-Build des Fixture-Projekts im PDE-Test-Workspace flaky/zu langsam
    ist: **STOP-AND-ASK** — nicht still auf eine Seam-Unit-Test-Variante ausweichen.
- **GATE I4**: wie GATE I3 → Commit.

## 3. Design-Entscheidungen (bereits gefallen)
1. **CallStats-API** (Sketch §I1): Start-Zeitpunkt bei Erstellung (`startedAtMillis =
   clock.millis()`), `start()` = Statik-Factory mit `Clock.systemDefault()`, drei Read-Suffixes
   (`duration`/`durationSuffix`/`suffix`). Bewusst minimal: keine zweite Format-Logik,
   kein Builder, keine Optionen (ADR-0053 „kein Format-Varianten-Wildwuchs"; exakt die zwei
   Formen `(N)` und `(N, HH:mm)` aus dem SOLL). `duration()` ist public, weil die
   Timeout-Meldungen die gemessene Dauer **ohne** Klammern brauchen.
2. **PoDelegate-Migration = replace-in-place in I1** (Paul-Freigabe): identischer Output,
   deshalb in einem Inkrement mit der Helper-Einführung; Pin-Test schützt das Byte-Format.
   `dispatchStats`/`COMPLETION_TIME` werden entfernt (Rule 27: einziges Aufrufstellen-Paar
   :241/:256 per Grep verifiziert, keine anderen Module).
3. **Timeout-Dauer = gemessen, nie konfiguriert** (R-TD-2/R-TD-3 explizit): die bisherige
   Message-Zahl (Shell `timeout`-Int, RunTests `MAX_TEST_DURATION.toMinutes()`) wird in den
   Meldungen durch `stats.duration()` ersetzt; der Suffix trägt ohnehin nur gemessene Werte.
   Bei hartem Timeout ≈ identisch, aber ehrlich gemessen.
4. **Stats-Startpunkte**: Shell vor `pb.start()` (Kommandoausführung); RunTests Kopf der
   `eclipseRunTests`-Methode (Setup+Launch+Warte = ganzer Aufruf); Build vor `deleteMarkers`
   (gesamter Build-Vorgang). Frührückwege (ArgsUtil, not-found, denied) vor dem Start =
   keine Stats, korrekt.
5. **Report/Suffix-Trennung**: immer `Report + System.lineSeparator() + suffix()` (Memory #7:
   lineSeparator in Tool-Output); leeres Shell-Output = nur Suffix, kein leerer erster Eintrag.
6. **Keine neue Kapselungs-Grenze**: `CallStats` lebt in core `org.sterl.llmpeon.shared`
   (neben `Timer`, `StringUtil`), beide Tool-Familien erreichen ihn (Plugin importiert
   core-`shared.*` flächendeckend — verifiziert; Test-Modul ebenso). Keine Abstraktion über
   die drei Tools („Suffix anhängen" ist eine Zeile, kein gemeinsames Interface erfinden).
7. **Testbarkeit Plugin ohne echten Launch**: RunTests = package-private statische
   Assembler (`formatResults`, `timeoutReport`) + `CallStats` mit fixed/mutable Clock
   („Suite-Stub" aus der BDD). Build = echtes Integrationstest gegen `test_project`-Fixture
   (BDD „Automatisiert: Plugin-Test" ohne Stub-Hinweis; passt zum Integration-Charakter der
   bestehenden `EclipseBuildToolTest`).

## 4. Betroffene Dateien (vollständig)
**Core `llmpeon-core`** (Workspace `/llmpeon-core/`, Disk
`/Users/sterlp/dev/workset/peon-ai/org.sterl.llmpeon.core/`):
| Datei | Inkrement | Art |
|---|---|---|
| `src/main/java/org/sterl/llmpeon/shared/CallStats.java` | I1 | **neu** |
| `src/main/java/org/sterl/llmpeon/poagent/tools/PoDelegateTool.java` | I1 | ändern (`dispatch` :233/:241, entfernen :256-259 + :66 + Imports) |
| `src/main/java/org/sterl/llmpeon/tool/tools/ShellTool.java` | I2 | ändern (Stats-Start :115 vor, Success-Return :162, Timeout :149) |
| `src/test/java/org/sterl/llmpeon/shared/CallStatsTest.java` | I1 | **neu** (UC-TD-1) |
| `src/test/java/org/sterl/llmpeon/poagent/tools/PoDelegateToolTest.java` | I1 | ändern (1 neuer Pin-Test; bestehende Tests UNVERÄNDERT) |
| `src/test/java/org/sterl/llmpeon/tool/ShellToolTest.java` | I2 | ändern (3 neue UC-TD-2-Tests) |

**Plugin `org.sterl.llmpeon`** (Workspace `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/`,
Disk `/Users/sterlp/dev/workset/peon-ai/org.sterl.llmpeon/...`):
| Datei | Inkrement | Art |
|---|---|---|
| `EclipseRunTestTool.java` | I3 | ändern (Stats-Start :68, `runAndCollect` :192-247, `formatResults` :322-336, neu `timeoutReport`) |
| `EclipseBuildTool.java` | I4 | ändern (`eclipseBuildProject` :200-209 nur; `readProblems` UNVERÄNDERT) |

**Test-Modul `org.sterl.llmpeon.test`** (JUnit 4, OSGi,
`/org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/`):
| Datei | Inkrement | Art |
|---|---|---|
| `EclipseRunTestToolTest.java` | I3 | ändern (2 neue UC-TD-3-Tests + MutableClock) |
| `EclipseBuildToolTest.java` | I4 | ändern (1 neuer UC-TD-4-Integrationstest) |

**Nicht anfassen**: `docs/**` (Flippen = Jon; I1-Commit nimmt die 5 Jon-Änderungen wie sie
sind mit), `eclipseReadProjectProblems` + `readProblems` (kein Suffix — Out of Scope),
`eclipseRefreshProject`, `StringUtil`, `Timer`, alle Out-of-Scope-Tools des Feature-Docs
(webFetch, memory*, JavaDebugTool, searchAgent, compactSession, lint, Read/Grep/Write-Familie),
`LiveStatus`/`StreamingBridge` (R18-R21-Streaming-Timer laufen getrennt weiter, ADR-0053).

## 5. Regeln & Constraints
- **Test-Honesty** (AGENTS-DEV Rule 30): Suffix-Tests asserten das **gebaute** Ende des
  Outputs (letzte Zeile exakt oder per engem Pattern mit konkreten Erwartungswerten bei
  injizierter Clock) — nicht nur „irgendeine Klammer am Ende". Timeout-Tests asserten beide
  Richtungen: Message-Zahl = gemessen **und** Suffix am Ende.
- Test-Kommentar `// UC-TD-n` (reine ID-Zeile) direkt über jeden Beleg-Test.
- Core: JUnit 5 + AssertJ. Plugin-Test: JUnit 4, **keine** externen Assert-Libs (OSGi) —
  `assertTrue(String, boolean)` etc., Regex via `String.matches`/`Pattern`.
- Vor jedem Plugin-JUnit-Lauf: `eclipseBuildProject` über `org.sterl.llmpeon` **und**
  `org.sterl.llmpeon.test` (stale bin/ → ClassNotFoundException). Surefire-Reports in
  `target/` sind stale.
- PDE-Test erster Lauf: Workspace-Trust manuell bestätigen (Rule 13); bei Timeout nicht
  parallel nachstarten.
- Keine neuen Dependencies, keine @Tool-Description-/Parameter-Änderungen — nur
  **Output-Strings** der 3 Tools ändern sich (bewusst, SOLL) + der PoDelegate-Output bleibt
  byte-identisch.
- Kein `(finished …)`-Wortlaut (Feature-Doc, explizit); Suffix exakt `(N)` bzw. `(N, HH:mm)`.
- Dev committet nach JEDEM grünen Inkrement auf `analysis/tool-evolution` (Rule 19);
  I1-Commit = Code + Tests + die 5 Jon-Docs.
- DONE-Claims vom PO gegen IST verifiziert (Rule 28): je Inkrement konkrete Evidenz
  (Suite-Status-String + neue Test-Namen), nicht „sollte grün sein".

## 6. BDD-Akzeptanz (aus SOLL-Doc, Status bleibt ❌ / UC-TD-1 bleibt ✅ geplant:_unit)
- **UC-TD-1** (R-TD-1) — GIVEN `CallStats` mit injizierter Clock, Lauf 2,4 s WHEN
  `suffix()`/`durationSuffix()` THEN `(2s, 14:32)` bzw. `(2s)` deterministisch; GIVEN
  PoDelegate-Dispatch THEN Output-Format unverändert.
  → `CallStatsTest` (4 Tests) + `PoDelegateToolTest.dispatchReplyPinsStatsSuffixFormat`
  + bestehende PoDelegate-Tests unverändert grün.
- **UC-TD-2** (R-TD-2) — GIVEN Shell-Aufruf (`sleep 2`) THEN letzter Output-Abschnitt enthält
  gemessene Dauer + HH:mm im R-TD-1-Format; GIVEN Timeout THEN Meldung + Suffix tragen die
  **gemessene** Dauer, nicht die konfigurierte.
  → `ShellToolTest.shellRunCommand_reportsDurationAndTime`,
  `shellRunCommand_timeout_reportsMeasuredDurationAndTime`,
  `shellRunCommand_nonZeroExit_endsWithStats`.
- **UC-TD-3** (R-TD-3) — GIVEN Testlauf-Report THEN endet mit `(N, HH:mm)` (Header-Shape
  unverändert); GIVEN Timeout THEN gemessene Dauer in Meldung + Suffix.
  → `EclipseRunTestToolTest.formatResultsEndsWithStatsSuffix`,
  `EclipseRunTestToolTest.timeoutReportUsesMeasuredDuration`.
- **UC-TD-4** (R-TD-4) — GIVEN `eclipseBuildProject`-Aufruf (echter Build des Fixture-
  Projekts) THEN Report endet mit Stats-Suffix, Report-Body unverändert.
  → `EclipseBuildToolTest.buildReportEndsWithStatsSuffix` (Integration, timeout 120 s).

## 7. Test-Strategie
- Core: `eclipseRunTests` Project `llmpeon-core` — **komplette Suite** pro Inkrement
  (Regressionsnetz über PoDelegate/Shell/Nachbarn; kein Einzeltest-Gate).
- Plugin: `eclipseRunTests` Project `org.sterl.llmpeon.test` (PDE) — komplette Suite;
  Vorher Build beider Plugin-Projekte.
- Shell-Tests laufen echte Kurzkommandos (`sleep 2`, `exit 42`) wie die bestehende Klasse —
  deterministisch genug (2 s ± Überlauf < 1 s); kein Sleep-Befehl > 5 s außer dem Timeout-
  Fall (`sleep 5`/timeout 1 → bricht nach ~1 s ab).
- RunTests-Assembler-Tests: fixed/mutable `Clock` → null Flakiness, Millisekunden-schnell;
  **kein** Test-Launch im Test (Launch-im-Launch = PDE-Hölle).
- Build-Integrationstest: 120 s-Timeout (JUnit 4 `@Test(timeout)`); läuft pro Gate-Vorlauf
  einmal; bei Flakiness STOP-AND-ASK statt Retry-Hack.
- Reihenfolge: I1 → I2 (Core, schnell) → I3 → I4 (Plugin, Trust-Dialog einmalig bei I3).

## 8. Offene Fragen
**Keine.** SOLL (Feature-Doc + ADR-0053) ist interpretationsfrei: API-Formen, Suffix-Formate,
Timeout-Dauer (gemessen), Out-of-Scope-Liste und der PoDelegate-Pin stehen alle fest.
Entscheidungen, die ich aus dem SOLL abgeleitet habe (und die ein Reviewer prüfen kann):
(a) `CallStats`-Signatur mit `duration()`/`durationSuffix()`/`suffix()`; (b) Stats-Start-
punkte je Tool; (c) UC-TD-4 als echtes Integrationstest statt Stub (BDD-Hinweis „Plugin-Test"
ohne „Suite-Stub", passt zur bestehenden `EclipseBuildToolTest`); (d) keine Suffixe an
Exception-/denied-/not-found-Pfaden (dort lief kein messbarer Vorgang — BDD deckt nur
Success+Timeout+Failure-Exit-Code ab).
