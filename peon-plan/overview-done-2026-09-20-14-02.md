# Build-Plan: Project-Problems-Tool — File- & Severity-Filter (CR-3, Story B)

**Branch:** `analysis/tool-evolution` (fortsetzen) — Vor Start: Git-Zustand selbst prüfen (aktueller Branch, nicht umbenennen; memory #23)
**Modul:** `org.sterl.llmpeon` (Plugin, JUnit 4 OSGi, keine externen Assertion-Libs) + `docs/`
**Größe:** 1 Inkrement (nur-hinzufügen, default-Verhalten unverändert)
**SOLL:** `docs/project-problems-tool.md` (idPrefix `PP`) — Struktur bereits linter-konform (`### R-PP-n — Titel ❌`, darunter `#### UC-PP-n — Titel ❌`) → **kein D9-Style-Restrukturieren nötig** (im Gegensatz zu OD/WEB in Story A)

---

## ⚠️ STOP-AND-ASK (memory #26) — zuerst lesen

Bei Compile-Fehlern ohne Lösung, nicht-grün-bekommbaren Tests, IST-Widersprüchen zu diesem Plan oder Unklarheiten: **aktiv bei Jon (askDev) nachfragen — nie still workarounden oder SOLL ändern.**

**Kein Git/kein Branch `analysis/tool-evolution` → erst fragen.**

---

## 1. Kontext

`eclipseReadProjectProblems` gibt heute die **komplette** Problem-Liste eines Projekts (alle Dateien, `DEPTH_INFINITE`, ERROR+WARNING). Bei großen Projekten bekommt der Agent 50+ Probleme aus Dateien, die mit der aktuellen Änderung nichts zu tun haben → Lärm + Token. CR-3 (Story B): optionale **File-Filter** (1+ Pfade, `DEPTH_ZERO`) + **Severity-Filter** (z. B. `ERROR`). Ohne Filter = exakt heutiges Verhalten (R-PP-4).

SOLL-IDs (alle aktuell ❌):

| ID | Titel |
|---|---|
| R-PP-1 | File-Filter auf 1+ Pfade, `DEPTH_ZERO` (keine Sub-Resources) — UC-PP-1, UC-PP-2 |
| R-PP-2 | Severity-Filter (z. B. `ERROR`) |
| R-PP-3 | Ehrliches leeres Ergebnis — „no problems in \<file\>“ inkl. Scope, **kein** project-wide-Fallback — UC-PP-4 |
| R-PP-4 | Default (keine Filter) unverändert — **keine UC**; Beweis = existierende Tests bleiben grün + neuer Default-Test |

## 2. Design-Entscheidungen (fest, nicht verhandelbar)

- **D1 — Signatur:** Gleiche Methode `eclipseReadProjectProblems` um **2 optionale `@P`-String-Parameter** erweitern:
  - `files`: kommagetrennte Pfade (leer = nicht gesetzt). Array/List-`@P`-Präzedenz existiert nicht im Codebase (Grep-verifiziert) → kommagetrennt ist die konsistente Wahl.
  - `severity`: `ERROR` | `WARNING` (case-insensitiv; leer = nicht gesetzt). Ungültiger Wert → `IllegalArgumentException` mit ehrlicher Meldung (welcher Wert, welche erlaubt).
  - Konvention „Empty means unset“ (AGENTS.md): leere Parameter → exakt der heutige Code-Pfad.
- **D2 — Pfad-Resolution:** Jeder Pfad (trimmed, leere Einträge verworfen) via `IProject.findMember(...)` lösen — sowohl project-relativ (`src/org/...`) als auch workspace-absolut (`/MyProject/src/...`). Ergebnis `null` → **ehrlicher Fehler-String** (Nicht gefunden: Pfad, Projektname, Hinweis project-relative Pfade) — **kein** Fallback auf Projektweite. Mehrere Pfade: alle auflösen; jeder Unhit wird im Fehler-String benannt, gültige Pfade werden trotzdem verarbeitet (kein all-or-nothing).
- **D3 — Marker-Lesung bei Filter:** Pro aufgelöstem `IFile`: `findMarkers(IMarker.PROBLEM, false, IResource.DEPTH_ZERO)` — exakt die Datei, keine Sub-Resources (R-PP-1). Default-Pfad (keine `files`) bleibt `project.findMarkers(IMarker.PROBLEM, true, DEPTH_INFINITE)` **byte-identisch** (R-PP-4).
- **D4 — Severity-Filter:** Anwendung auf die ausgewählten Marker (vor der Formatierung): `ERROR` → nur `IMarker.SEVERITY_ERROR`; `WARNING` → nur `IMarker.SEVERITY_WARNING`. INFO war schon heute ausgeschlossen und bleibt es. Ohne `severity` = ERROR+WARNING wie heute.
- **D5 — Output-Wording** (System.lineSeparator(), nie hartkodiertes `"\n"` — memory #7):
  - gefiltert, Treffer: `Problems in <files> (project <p>[, severity <S>]):` + Marker-Zeilen (`message @ line N @ file fullPath` via existierendem `markerToAiString`)
  - gefiltert, leer: `No problems in <files> (scope: project <p>[, severity <S>])` — Scope explizit, **kein** project-wide-Fallback (R-PP-3, Tool-darf-nicht-lügen)
  - nicht-gefundener Pfad: `No problems found for <path> in project <p> (project-relative path expected)`
  - default: unverändert (`Project <p> problems:` / `Project build <p> has no errors or warning.`)
- **D6 — Tool-/Param-Beschreibungen** (`@Tool` + `@P`), je 10–25 Wörter, imperativ:
  - `@Tool`: „List compile errors and warnings for a project. Optional: files (comma-separated) and severity (ERROR, WARNING) filter the result scope.“
  - `files`: „Comma-separated file paths (project-relative or workspace) to limit the problem list; empty = whole project.“
  - `severity`: „Limit to one severity: ERROR or WARNING; empty = errors and warnings.“
- **D7 — Implementationsort:** Alles in `EclipseBuildTool` selbst (Klasse bleibt der einzige Ort der Marker-Auswertung). `Status`-Inner-Klasse (:106) erweitern um das Severity-Fenster (z. B. Predicate/Int), `readProblems(IProject, String files, String severity)` als neue Signatur; alte 1-Arg-Calls (wenn intern existieren) delegieren. **Keine neue Klasse.**

## 3. Betroffene Dateien (IST, Grep-verifiziert 2026-09-20)

| Datei | Änderung |
|---|---|
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/EclipseBuildTool.java` | `:37` `eclipseReadProjectProblems` → +2 `@P`-Params, `@Tool`/`@P`-Dests (D6); `:48` `readProblems`; `:131` `readProjectStatus` + `Status` (`:106`) → Filter-Pfad (D3/D4/D5). Default-Zeilen `:53`/`:55` **unchanged**. |
| `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/EclipseBuildToolTest.java` | **Neu** (siehe §5). |
| `docs/tool-descriptions-inventory.md` | Zeile 136 / Zeile 39 (`eclipseReadProjectProblems`, heute ⚠️ „zu kurz“) → neue Beschreibung aus D6. |

**Unberührt (verifiziert, nur dokumentieren):**
- `SharedToolsComponent.java:67` (Registrierung `new EclipseBuildTool()` — Constructor bleibt no-arg)
- `EclipseWorkspaceReadFileTool.java:217` (`new EclipseBuildTool()` intern — nur `eclipseListAllOpenProjects` genutzt; Grep vor Edit erneut bestätigen, dass keine `eclipseReadProjectProblems`-Callstelle dort entsteht)
- `EclipseWorkspaceReadFileToolTest.java:38` (`testList` → nur `eclipseListAllOpenProjects`, bleibt grün)
- `SharedToolsComponentTest.java:50` (nur `countExecutors >= 1`)
- Core-Modul: **keine** Referenz (Grep leer)
- Prompts (`org.sterl.llmpeon.core/src/main/resources/org/sterl/llmpeon/prompts/`) & AGENTS-DEV: **keine** Referenz an `eclipseReadProjectProblems` / „validate after edit“ (Grep leer) → **kein** Prompt-Change. Homepage-Erwähnungen (`custom-agents.md:132,172` + dist) = Name-only → kein Homepage-Change.
- `docs/project-problems-tool.md` & `docs/index.md` → **NICHT** in diesem Inkrement (s. §7 Post-Review)

## 4. Regeln & Constraints

- Log OR throw, nie beides; Ehrlichkeit: jede Filter-/Scope-Einschränkung im Output benannt (AGENTS.md)
- Plugin-Tests: JUnit 4, **keine** externen Assertion-Libs, `// UC-PP-x`-Kommentar pro UC-Test
- Surefire-Zahlen sind Ground Truth für das **core**-Modul — hier relevant: Eclipse-Runner-Zahlen sind OK, aber **GANZE Suite** laufen (memory #13/#16)
- `System.lineSeparator()` in Output-Strings (memory #7)
- Re-read nach jedem line-basierten Edit (AGENTS.md „Working agreements“)

## 5. Test-Strategie — neue Klasse `EclipseBuildToolTest` (OSGi, `org.sterl.llmpeon.test`)

Pattern wie `EclipseWorkspaceReadFileToolTest`: extends `AbstractIntegrationTest`; `assumeTrue(isWorkspaceAvailable())`; `before()`/`super.after()`; `@After` räumt **alle** selbst gesetzten Marker (`resource.deleteMarkers(...)`) — Fixture **vor** SUT-Nutzung setzen, im `finally`/`@After` räumen (memory #12). Projekt: `PeonTestFixture.PROJECT_NAME`. Marker via `file.createMarker(IMarker.PROBLEM, Map.of(IMarker.MESSAGE, ..., IMarker.SEVERITY, ..., IMarker.LINE_NUMBER, ...), true)`.

| Test (Methode) | UC-Kommentar | Setup → Erwartung |
|---|---|---|
| `problemsForSingleFileReturnsOnlyThatFile` | `// UC-PP-1` | Marker in A **und** B → `files=A` → nur A-Zeilen, Header nennt A + Projekt |
| `filteredPathOutsideProjectFailsHonest` | `// UC-PP-2` | `files=src/not/there.java` → ehrlicher Fehler-String inkl. Projektname; **kein** project-wide-Fallback (andere existierende Marker NICHT enthalten) |
| `severityFilterReturnsOnlyErrors` | `// UC-PP-3` | 1 ERROR + 3 WARNING in einer Datei, `files=Datei, severity=ERROR` → nur die 1 ERROR-Zeile |
| `cleanFileReportsNoProblemsHonest` | `// UC-PP-4` | Marker in C, `files=saubereDatei` → „No problems in … (scope: …)“; C-Marker NICHT enthalten |
| `defaultModeStillProjectWide` | **kein** UC-Kommentar (R-PP-4-Beweis; UC-Kommentar würde beim Lint zu „VERWAIST“ führen, wenn keine passende UC existiert) | Marker in A+B, **keine** Filter → beide im Output, alter Default-Header (exakt heutiges Format) |

Zusätzlich (R-PP-4): existierende Suite bleibt grün — `EclipseWorkspaceReadFileToolTest.testList` (:36), `SharedToolsComponentTest` (:50).

## 6. Gate-Kette (Reihenfolge, jede Stufe grün bevor weiter)

1. Git: Branch `analysis/tool-evolution` aktiv (sonst STOP-AND-ASK)
2. `eclipseBuildProject` → `org.sterl.llmpeon` **und** `org.sterl.llmpeon.test` (memory #16: VOR jedem Testlauf builden — stale `bin/`-Klassen)
3. `eclipseRunTests` `org.sterl.llmpeon.test` — **ganze Suite** (Plugin-Test-Modus). ⚠️ Erster Lauf: Workspace-Trust-Dialog manuell bestätigen (memory #13); bei Timeout NICHT parallel nachstarten — User informieren + PID abwarten
4. `lintDocsAndTests` mit idPrefix `PP` → **0 offene Befunde** (offene ❌-UCs liefern per Definition keine Befunde; neue ✅-UCs brauchen ihre `// UC-PP-x`-Tests — die sind in §5; R-PP-4 ohne UC bleibt ohne Test-Kommentar)
5. **Evidenz pro Deliverable** (memory #28) — DONE-Claim ist erst gegen IST verifiziert, wenn jede Datei mit konkreter Evidenz belegt ist:
   - [ ] `EclipseBuildTool.java`: Signatur-Zeile mit 3 Params + neue `@Tool`-Desc (Zitat)
   - [ ] `EclipseBuildToolTest.java`: 5 Testmethoden vorhanden (Namen zitieren), Grep `// UC-PP-` Count = 4
   - [ ] `docs/tool-descriptions-inventory.md`: Zeile 39 zeigt neue Desc, ⚠️ weg
6. Commit inkl. `docs/**` (memory #19) — z. B. `feat(pp): file/severity filters for eclipseReadProjectProblems (R-PP-1..4)`

## 7. Post-Review (EXPLIZIT getrennt — läuft NUR auf ausdrückliche Anweisung von Paul nach bestandenem PO-Review)

**Nicht Teil des Increments.** Nach Anweisung, per `eclipseEditFile` mit eindeutigem `oldString` (nie per Zeilennummer — memory #31), danach Grep-Count-Verifikation:

1. `docs/project-problems-tool.md`: R-PP-1…R-PP-4 → `✅ done`, UC-PP-1…UC-PP-4 → `✅ done`, Dokumentstatus (Header) → `✅ done`
2. `docs/index.md:73` → `✅`
3. **Danach** `planImplemented` (NUR der Dev-Agent — memory #10; Da Thinka ruft es NIE auf)

## 8. Offene Fragen

**None.** Alle Designentscheidungen sind in §2 fixiert; Naming (`files`, `severity`) und Wording (D5) sind final. Falls sich ein IST-Widerspruch zeigt → STOP-AND-ASK, nicht still ändern.

## 9. PO-Review Da Dok (2026-09-20) — Story B Inc (Commit `0eb746f`)

**Verdict: CONCERNS** — keine blockierenden Lücken. Alle R-PP-1…4 + UC-PP-1…4 vom Code abgedeckt, je UC ein Test; Plan und Docs vollständig übereinstimmend; Default-Pfad strukturell unverändert.

**Verifiziert (IST, diese Session):**
- `EclipseBuildTool.java:41-43` — Signatur mit `files`/`severity` als optionale `@P`-Strings, Descriptionen **wortgleich** D6 (`:40,42,43`).
- D2: `resolveFile` (`:149-154`) — project-relativ via `project.findMember`, workspace-absolut via `Root.findMember`, fremd-Projekt-Dateien → `null` → ehrlicher Fehler-String, nie Fallback; mehrere Pfade: jeder Unhit benannt (`:105-110`), gültige laufen trotzdem.
- D3: `readFileStatus` `DEPTH_ZERO` (`:282`) vs. Default `readProjectStatus` `DEPTH_INFINITE` (`:275`) — Default-Pfad in `readProjectWide` extrahiert, Header **exakt** altes Wording (`:76,78`), alte 1-Arg-Delegation (`:54-56`) für `eclipseBuildProject` (`:208`) erhalten.
- D4: Severity-Filter **vor** Formatierung in `Status.addMarker` (`:238-251`); INFO bleibt ausgeschlossen; ungültiger Wert → `IllegalArgumentException` mit Wert + erlaubten Werten (`:180-181`), case-insensitiv (`:174`).
- D5 Wording exakt: `Problems in … (project …[, severity …]):` (`:130-131`), `No problems in … (scope: …)` (`:135-136`), `No problems found for … (project-relative path expected)` (`:106-108`); `System.lineSeparator()` in allen neuen Output-Zeilen (memory #7); Marker-Zeilen behalten hartkodiertes `"\n"` — bestehend, Default-Pfad bleibt dadurch byte-identisch (bewusste Plan-Entscheidung, kein Befund).
- Tests: `EclipseBuildToolTest` — 5 Methoden exakt wie §5, 4× `// UC-PP-x` (`:53,71,89,108`), Default-Test ohne UC-Kommentar (`:125`); Fixture vor SUT, `@After`-Cleanup (`:31-38`, memory #12); `assumeTrue` vor Marker-Setzung.
- Lint: default-Run **0 PP-Befunde**; mit `idPattern=UC-PP-\d+`: 4/4 UC-Defs + 4/4 Test-IDs, 0 Befunde. (Die 33 repo-weiten Befunde = UC-DL, bekannt out-of-scope.)
- Inventory: `tool-descriptions-inventory.md:136` (Tabellen-Zeile 39) zeigt die neue Desc, ✅ (aktuell, 2026-09-20, Story B), altes ⚠️ weg — Plan hatte beide Zeilenangaben („Zeile 136 / Zeile 39") → **keine Deviation**.
- Docs korrekt **nicht** geflippt (R-PP/UC-PP/Status in `project-problems-tool.md` + `index.md:73` alle noch ❌) — §7 ist post-review, korrekt eingehalten.
- Grep: keine weiteren `eclipseReadProjectProblems`-Callstellen außer Tool + Test; Core-Prompts ohne Referenz (Plan-Claim bestätigt).
- `org.sterl.llmpeon` Build: kompiliert, nur bestehende Null-Safety-Warnings.
- **Nicht verifizierbar (Evidenz-Reserve):** Commit-Hash `0eb746f` (kein Git-Zugriff bei Da Dok) und OSGi-Suite-Lauf (erster Lauf braucht manuelle Trust-Bestätigung, memory #13) — Da Meks Zahlen daher nicht unabhängig bestätigt.

**Gaps (CONCERNS, nicht blockierend):**
1. **Kein Test für ungültigen `severity`-Wert.** D1 verpflichtet auf ehrliche `IllegalArgumentException` — implementiert (`:180-181`), aber §5-Testtabelle enthält keinen Fall, und die Doc-UCs decken es nicht. Mutation `default:` → „ignoriere" würde grün bleiben. Empfehlung: Test `invalidSeverityThrowsHonest` (keine UC-ID — SOLL-Doc trägt sie nicht).
2. **Case-insensitive Severity ungetestet** (D1) — `toLowerCase`-Mutation bliebe grün. Kleiner Anhängsel-Test genügt (kann mit 1. kombiniert werden).
3. **`@After`-Cleanup ohne try/finally:** wirft `deleteMarkers` für die erste Datei, rücken die Marker der restlichen Dateien nicht raus → persistenter Workspace-State (memory #12-Form ist in anderen Tests try/finally). `EclipseBuildToolTest.java:31-38`.
4. Nit: `files=" "` (nur Whitespace) → `splitPaths` leer → stiller Default project-wide (`:161-167`). „Empty means unset" deckt es begründbar — als Nit notiert, kein Handlungsdruck.

**Docs↔Plan (Seite c):** keine Lücke — der Plan deckt alle R-PP-1…4 ab; R-PP-4-ohne-UC ist bewusst so geplant (Beleg = Default-Test + bestehende Suite, beides IST vorhanden).

**Skill-/Instruktions-Gap (Randnotiz 1, empirisch reproduziert):** `idPattern=PP` liefert **5 Scheinbefunde** (`DOPPELT_DEFINIERT PP` + 4× `UNBELEGT PP`), weil (a) `TestParser` das Pattern als **Full-Match** anwendet (`TestParser.java:94`) → Test-IDs 0/0, und (b) `DocParser.extractId` es als Substring-`find` anwendet (`DocParser.java:177-178`) → UC-IDs werden auf `"PP"` gekürzt. `idPattern=UC-PP-\d+` → 4/4 + 0 Befunde. Da Meks Analyse stimmt. **Wurzel-Problem:** der `@P` für `idPattern` in `DocsLinterTool` (`:52,79`) hat **keine Description** — das Model hat null Hinweis, dass ein Full-Match-Regex auf die komplette UC-ID erwartet wird. Empfehlung an PO (keine Story-B-Rework): `@P`-Description ergänzen (z. B. „regex that FULL-matches UC ids, e.g. `UC-PP-\\d+`") + ggf. eine Zeile in `docs-linter.md`; distinct vom 2026-09-16-Open-Point (Root-Fallback, anderer Mechanismus).

**Skill-Evolution-Outcome (AGENTS-PO.md Gate-Phase):** 1 **Update-Kandidat** (keine Erzeugung/Löschung): DocsLinter-`idPattern`-Instruktion lückenhaft — zweiter dokumentierter Treffer (09-16 Root-Problem, 09-20 Pattern-Semantik) spricht für denselben Fix; Evidence oben. Ledger-Eintrag + Umsetzung per Delegation an Da Mek (Mini-Zyklus), Entscheidung: Jon. Positiv: Da Mek hat den Lint-Quirk **reportet statt still workaroundt** (AGENTS „Report, don't route around") und mit dem korrekten Pattern verifiziert — Reporting-Kultur wirkt.
