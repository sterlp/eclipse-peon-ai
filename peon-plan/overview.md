# Mini-Zyklus: Java-Debugger — `list_breakpoints` (R-JD-11) + hitCount-Clamp (R-JD-12)

## ⛔ STOP-AND-ASK (zuerst lesen)
Bei **Compile-Fehlern ohne Lösung**, **nicht-grün-bekommenden Tests** oder **IST-Widersprüchen zu
diesem Plan**: STOPP und Rückfrage über den Jon-Kanal — nie still workarounden, nie SOLL ändern.
- UC-Status in `docs/java-debugger-tool.md` bleibt **❌** — Flippen ❌→✅ macht **ausschließlich
  Jon** nach Review. Niemand in diesem Zyklus rührt die Feature-Docs an (nur Code + Tests).
- **Q1 ist entschieden (Jon):** Projekt-Scope von `list_breakpoints` = **im Chat-View gewähltes
  Projekt** (das bestehende „gewähltes Projekt"-Konzept des Plugins, dieselbe Quelle wie die
  eclipse*-Tools) — **nicht** der aktive Editor. I2 ist damit **entblockt**; Reihenfolge
  I1 → I2 bleibt.

## 1. Kontext
SOLL: `docs/java-debugger-tool.md` **R-JD-11** (UC-JD-13: `list_breakpoints` — Breakpoint-
Landkarte, **bewusst ohne Session** als Ausnahme zu R-JD-1) und **R-JD-12** (UC-JD-14:
`DebugJson.breakpointResponse` zeigt Marker-hitCount `< 0` als `0` — die Beschreibung sagt
„0 = every hit"; Marker-Verhalten unangetastet).
Motivation (SOLL-WEIL, E2E 2026-09-22): zwischen Sessions konnte der Agent Phantom-BPs nicht
sehen und musste über Laufzeitverhalten raten.
Branch: **`analysis/tool-evolution`** (Tool-Time-Disclosure drin; erwartet Tip `081cccd`/`6e16118`).
Repo-Root: Disk `/Users/sterlp/dev/workset/peon-ai` (Workspace-Projekt `llmpeon-parent`).

## Status
- Vorbereitungen: ✅ (2026-09-22) — Branch `analysis/tool-evolution` Tip `6e16118` ✅; Baseline PDE-Suite **264/0/0**.
- I1 (R-JD-12 Display-Clamp): ✅ (2026-09-22) — `displayHitCount` + Clamp in `breakpointResponse`, 2 UC-JD-14-Tests in `DebugJsonUnitTest`; Suite 266/0/0; Commit `b04f0e3`.
- I2 (R-JD-11 list_breakpoints): ✅ (2026-09-22) — 15. Action + `currentProject`-Wiring (SharedToolsComponent/PeonAiService) + `DebugJson.breakpointListResponse`, 5 neue UC-JD-13-Tests (`DebugListBreakpointsTest`) + `listBreakpointsDoesNotFailWithoutSession`; Suite 272/0/0.
  - IST-Abweichung (verifiziert, 2026-09-22): `JDTDebugConstants` existiert auf dieser Target-Platform (jdt.launching 3.24.300 / jdt.debug 3.26.100, 2026-06/07) **nicht** — Marker-Typ-IDs `org.eclipse.jdt.debug.javaLineBreakpointMarker` / `...javaExceptionBreakpointMarker`, Attribut-Keys `org.eclipse.jdt.debug.core.typeName` / `...condition` / `...hitCount`, enabled = `IBreakpoint.ENABLED` (`org.eclipse.debug.core.enabled`). Aus 2026-07-Quell verifiziert; UC-JD-13-Tests self-verifying bestätigt.

## 2. Slicing — 2 vertikale Inkremente, je grün + Commit auf `analysis/tool-evolution`

### Vorbereitungen (vor I1 — einmalig)
1. Git-Zustand prüfen: Branch `analysis/tool-evolution`, Tip `081cccd` oder `6e16118`
   (beide von Paul genannt — einer muss als HEAD stimmen; Weichbeide ab → STOP-AND-ASK).
   Working Tree ohne fremde Änderungen.
2. **Baseline neu messen**: `eclipseBuildProject` über `org.sterl.llmpeon` **und**
   `org.sterl.llmpeon.test` (Rule 16), dann `eclipseRunTests` Project `org.sterl.llmpeon.test`
   — ganze Suite (erwartet ~264/0; erster Lauf: Workspace-Trust manuell bestätigen, Rule 13 —
   nicht parallel nachstarten). Reale Zahl in §Status eintragen; rot/weich ab → STOP-AND-ASK.

### Inkrement 1 — R-JD-12: hitCount-Clamp in `breakpointResponse` (Plugin)
**UC-IDs: UC-JD-14**
- IST (verifiziert): `DebugJson.breakpointResponse` (`org.sterl.llmpeon/.../parts/tools/debug/
  DebugJson.java:233-257`) rendert :250 `node.put("hitCount", breakpoint.getHitCount())` —
  roher Marker-Wert (JDT-Default `-1` „niemals expire"). Aufrufer: nur
  `set_breakpoint` (JavaDebugTool:247) + `set_exception_breakpoint` (:283).
- ÄNDERUNG in `DebugJson`:
  - neuer `static int displayHitCount(int raw) { return Math.max(0, raw); }` (private)
    — die **einzige** Clamp-Stelle (wird in I2 von der List-Response wiederverwendet,
    „one behaviour, one implementation").
  - :250 → `node.put("hitCount", displayHitCount(breakpoint.getHitCount()));`
  - Sonst **nichts** — Shape, Key-Reihenfolge (LinkedHashMap), alle anderen Felder unverändert.
- TESTS (in `DebugJsonUnitTest` — Proxy-Stub-Muster :36, JUnit 4, OSGi; heute **keine**
  Breakpoint-Tests, Grep-verifiziert):
  - `// UC-JD-14` + `breakpointResponseClampsNegativeHitCount`: Stub
    `IJavaLineBreakpoint` (Proxy) mit `getMarker()` → `IMarker`-Stub (`getId()`=42),
    `getHitCount()` → `-1` → Output enthält `"hitCount": 0` und `"id": "42"`.
  - `// UC-JD-14` + `breakpointResponseKeepsPositiveHitCount`: `getHitCount()` → `3`
    → `"hitCount": 3` (Regressionsschranke: ≥ 0 unverändert).
  - Stub-Honesty (Rule 30): beide Richtungen — Input (gesubberter Wert −1/3) und Output
    (exakter gerendert er Wert), nicht nur „Call kam".
- GATE I1: `eclipseBuildProject` beider Plugin-Projekte + `eclipseRunTests`
  `org.sterl.llmpeon.test` — ganze Suite grün (Baseline + 2 neue). Commit.

### Inkrement 2 — R-JD-11: `list_breakpoints` (Plugin)
**UC-IDs: UC-JD-13**
- **Projekt-Scope (Q1, Jon): „gewähltes Projekt" = im Chat-View gewähltes Projekt** —
  dieselbe Quelle, die die anderen eclipse*-Tools nutzen (**nicht** der aktive Editor):
  - **Wiring (etabliertes Muster `EclipseGrepTool:32-36` / `EclipseWorkspaceReadFileTool:40-44`):**
    - `JavaDebugTool`: neu `private IProject currentProject;` +
      `public void setCurrentProject(IProject currentProject)` (wie die Schwestertools,
      plain field — konsistent, nicht volatile-abweichend).
    - `SharedToolsComponent` (:69 heute `new JavaDebugTool()` inline): Instanz in Field
      `javaDebugTool` + Accessor `javaDebugTool()` (Muster `eclipseGrepTool` :38/:70/:111).
    - `PeonAiService.setProject` (:258-260): ergänzen
      `sharedTools.javaDebugTool().setCurrentProject(project);` — eine Zeile, derselbe
      Push wie workspaceRead/Write/grep.
  - @Tool-Methode `listBreakpoints()` **ohne Parameter** → löst `currentProject` auf und
    delegiert an die Seam (s. u.).
  - **Nichts gewählt → ehrlicher Fehler, kein Fallback-Raten**: neue Konstante
    `private static final String NO_PROJECT = "no project selected — select a project to list its breakpoints"`
    + `onProblem(...)`-Ruf, Stil von `noSession` (:552-555).
  - Session-Guard (`DebugSession.findActive()`/`noSession(...)`) greift hier **nicht** —
    bewusste R-JD-1-Ausnahme, im Code als Javadoc-Hinweis an der Methode benannt.
- **Marker-Scan (Workspace-State, sessionfrei):**
  - Line-BPs des Projekts: `project.findMarkers(<JDT-Line-BP-Marker-Typ>, true,
    IResource.DEPTH_INFINITE)`.
  - Exception-BPs: Marker leben auf dem **Workspace-Root** (`createExceptionBreakpoint`
    wird in `set_exception_breakpoint` :273 explizit mit `getRoot()` angelegt) — also:
    `getRoot().findMarkers(<JDT-Exception-BP-Marker-Typ>, false, IResource.DEPTH_ZERO)`.
    Exception-BPs sind workspace-global → erscheinen in **jeder** Projekt-Liste
    (Jon akzeptiert das als JDT-Wahrheit; im Output + Description als workspace-wide benannt).
  - Marker-Typ-IDs + Attribut-Keys aus `JDTDebugConstants` (erwartet: Basis
    `org.eclipse.jdt.debug.model_breakpoint`, line `..._line`? — **exakte Konstanten
    vor dem Coden via readTypeSource/JDT-Quell bestätigen** (AGENTS: nie raten);
    Fallback-Vertrauen: die UC-JD-13-Tests sind self-verifying (echte Marker werden angelegt
    und müssen erscheinen) → falscher Typ/Key = roter Test, kein stiller False-Negative.
  - Attribute pro Marker: `id` = `marker.getId()` (String), `type` = line/exception
    (per Marker-Typ), Line: `file` (projekt-relativ), `line` (`IMarker.LINE_NUMBER`),
    `typeName` (JDT-Attr `...typeName`), `condition` (JDT-Attr `...condition`, `""` wenn
    keiner); Exception: `exceptionType` (JDT-Attr `...exception_type`); beide: `hitCount`
    (JDT-Attr `...hit_count`, Default `-1` → **`displayHitCount`** aus I1) und `enabled`
    (`IMarker.ATTR_ENABLED`).
- **Response** (neue `DebugJson.breakpointListResponse(String projectName, List<...>)`,
  pretty + LinkedHashMap, konsistent mit den DebugJson-Formen):
  ```json
  {
    "project": "test_project",
    "scope": "line breakpoints of the project + workspace exception breakpoints (other projects not listed)",
    "breakpoints": [
      { "id": "123", "type": "line", "file": "src/Foo.java", "line": 42,
        "typeName": "org.example.Foo", "condition": "i > 3", "hitCount": 0, "enabled": true },
      { "id": "124", "type": "exception", "exceptionType": "java.lang.IllegalStateException",
        "hitCount": 0, "enabled": true }
    ]
  }
  ```
  Leere Liste: `"breakpoints": []` + `project`/`scope`-Keys = Scope-Disclosure
  (SOLL: „leere Liste mit Scope-Disclosure"; AGENTS: Scope-Einschränkung im Output benannt).
- **@Tool-Description** (15. Action, Stil der 14 bestehenden): Zweck = Landkarte der
  Breakpoints **vor/zwischen Sessions** inkl. Phantom-BPs (UI gesetzt), die das
  Laufzeitverhalten sonst nicht verrät; Scope = **im Chat gewähltes Projekt** +
  workspace-wide Exception-BPs; hitCount `0 = every hit` (Clamp R-JD-12); entfernen
  bleibt `remove_breakpoint(id)`; **works without a debug session**;
  kein gewähltes Projekt → ehrlicher Fehler.
- **JavaDebugTool-Klassen-Javadoc** :41-49 aktualisieren (heute: „every action answers
  with the honest no-session message" — Ausnahmeklausel für `list_breakpoints` ergänzen:
  läuft ohne Session, braucht dafür aber ein gewähltes Projekt).
- TESTS:
  - **Neue Klasse `DebugListBreakpointsTest`** (JUnit 4, extends `AbstractIntegrationTest` —
    Fixture `test_project`, Muster `EclipseBuildToolTest`/`DebugSessionLookupTest`):
    - `// UC-JD-13` + `lineBreakpointWithConditionAndHitCountAppears`: via
      `JDIDebugModel.createLineBreakpoint(file, typeName, line, -1, -1, 3, true, {})`
      auf einer echten Fixture-CU (z. B. `src/org/sterl/fixture/Alpha.java`, wie
      `EclipseBuildToolTest.FILE_A`) + `setCondition("i > 3")` →
      `JavaDebugTool.listBreakpoints(project)` (Seam, s. u.) enthält id/`"type": "line"`/
      file/line/`"condition": "i > 3"`/`"hitCount": 3`/`"enabled": true`.
      finally: `breakpoint.delete()` + `marker.delete()` (Muster `EclipseBuildToolTest`
      `deleteTestMarkers`).
    - `// UC-JD-13` + `exceptionBreakpointListedWorkspaceWide`:
      `JDIDebugModel.createExceptionBreakpoint(getRoot(), "java.lang.IllegalStateException",
      false, true, false, true, {})` → erscheint mit `"type": "exception"` +
      `exceptionType` (beweist zugleich Root-Scope); cleanup idem.
    - `// UC-JD-13` + `uiStyleBreakpointRendersClampedHitCount`: Line-BP mit
      `hitCount = -1` (UI-Stil) → `"hitCount": 0` in der Liste (R-JD-12 greift auch
      auf Marker-Lese-Pfad — via `displayHitCount`).
    - `// UC-JD-13` + `noMarkersListsEmptyWithScopeDisclosure`: ohne Marker →
      `"breakpoints": []` + Projekt-Name im Output.
    - `// UC-JD-13` + `otherProjectMarkersDoNotAppear`: Line-BP-**Marker** direkt auf
      der Workspace-Root anlegen (`root.createMarker(<line-BP-Typ>, attrs)` ohne
      `getFile(...)`-Ziel im Projekt) → taucht in `listBreakpoints(project)` **nicht**
      auf (Isolation: nur Projekt-Marker + Root-Exception-Marker zählen).
  - **Seam für die Tests**: `public static String listBreakpoints(IProject project)`
    in `JavaDebugTool` (Präzedenz: `primaryTypeName` :524 ist public static als
    Test-Seam, R-JD-7/`DebugPrimaryTypeTest`); @Tool-Methode = gewähltes-Projekt-Resolution
    + Delegation. (Package-private reicht nicht — Test-Package ist `org.sterl.llmpeon.test`.)
  - **`JavaDebugToolTest.noSessionFailsHonest`** (:28-61, heute 14 Actions): Matrix **unverändert**
    lassen; `// UC-JD-1`-Kommentar (:26) um einen Satz ergänzen (Paul: „im Test kommentieren,
    NICHT im Doc"): `list_breakpoints` (R-JD-11) ist die bewusste Session-Ausnahme —
    getestet in `DebugListBreakpointsTest`. Dazu neuer Test
    `listBreakpointsDoesNotFailWithoutSession` in `JavaDebugToolTest`:
    `tool.listBreakpoints()` (Test-Instanz ohne gewähltes Projekt, Headless-PDE) enthält
    „no project selected" (NO_PROJECT, deterministisch) **und kein** „no active debug session".
- GATE I2: wie GATE I1 (Build beider Projekte + ganze Suite). Commit.

## 3. Design-Entscheidungen (alle entschieden — keine offenen)
1. **Clamp-Zentrale**: ein `displayHitCount` in `DebugJson`, genutzt von
   `breakpointResponse` (I1) UND `breakpointListResponse` (I2) — eine Stelle für
   „Marker-Rohwert → ehrliche Anzeige" (SOLL R-JD-12 + AGENTS one-behaviour).
2. **List-Datenquelle = Marker, nicht Breakpoint-Manager**: Marker überleben das
   Target (Phantom-BPs zwischen Sessions = genau der Zweck); der Breakpoint-Manager
   würde nur session-registrierte BP liefern. Projekt-Isolation per `project.findMarkers`.
3. **Exception-BPs = workspace-global im Scope** (Marker leben auf Root — JDT-Wahrheit,
   von Jon akzeptiert; UC-JD-13 verlangt sie in der Liste). Im Output + Description
   benannt („exception breakpoints are workspace-wide"); „anderes Projekt" isoliert
   nur die Line-BPs.
4. **Scope = im Chat-View gewähltes Projekt (Q1, Jon)** — per bestehendem
   `setCurrentProject`-Wiring (SharedToolsComponent → `PeonAiService.setProject`),
   **nicht** aktiver Editor: (1) „gewählt" bedeutet überall im Plugin dasselbe;
   (2) bleibt zwischen Sessions stabil ohne Editor (User-Kombo, Phantom-Hunt-Zweck);
   (3) ehrlicher Fehler statt Fallback-Raten. **Seam `public static
   listBreakpoints(IProject)`** hält die @Tool-Hülle dünn (Projekt-Resolution +
   ehrlicher Fehler) und macht die Logik ohne UI/Session testbar (ADR-0051: keine
   Live-Session-Tests; ADR-0037: `test_project`-Fixture).
5. **Kein Session-Guard, kein Auto-Start** in der neuen Action; `noSession`-Text und
   die 14er-Matrix bleiben byte-identisch.
6. **Keine neue Abhängigkeit, keine @P-Parameter** (Paul: Parameter „keine");
   `remove_breakpoint` unangetastet.

## 4. Betroffene Dateien (vollständig)
| Datei | Inkrement | Art |
|---|---|---|
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/debug/DebugJson.java` | I1 | ändern (:250 Clamp, neu `displayHitCount`) |
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/debug/DebugJson.java` | I2 | ändern (neu `breakpointListResponse`) |
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/debug/JavaDebugTool.java` | I2 | ändern (15. Action `listBreakpoints` + `currentProject`-Field/`setCurrentProject` + `NO_PROJECT`-Konstante + public static Seam, Klassen-Javadoc :41-49) |
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/ai/component/SharedToolsComponent.java` | I2 | ändern (JavaDebugTool in Field + Accessor `javaDebugTool()`, :69) |
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/ai/PeonAiService.java` | I2 | ändern (`setProject` :258-260: Push an `javaDebugTool`) |
| `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/DebugJsonUnitTest.java` | I1 | ändern (2 UC-JD-14-Stub-Tests) |
| `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/DebugListBreakpointsTest.java` | I2 | **neu** (5 UC-JD-13-Tests) |
| `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/JavaDebugToolTest.java` | I2 | ändern (UC-JD-1-Kommentar + `listBreakpointsDoesNotFailWithoutSession`) |
| **unverändert**: `DebugSession`, `DebugSupport`, `UserContext`, `set_breakpoint`/`set_exception_breakpoint`/`remove_breakpoint`-Logik, die anderen Shared-Tools, `docs/**` (Flip = Jon), `docs/java-debugger-tool.md` | — | — |

## 5. Regeln & Constraints
- **Falsche Negative sind der teuerste Bug** (AGENTS): jede Scope-Einschränkung im
  Output benannt (`project`/`scope`-Keys); der self-verifying-Charakter der UC-JD-13-
  Tests (echte Marker müssen erscheinen) darf nicht „erleichtert" werden.
- Test-Kommentar `// UC-JD-n` (reine ID-Zeile) über jeden Beleg-Test; Test-Honesty
  (Rule 30) — Input-Setup und gerendertes Output beidseitig asserten.
- JUnit 4 im Test-Modul, **keine** externen Assert-Libs (OSGi).
- Marker-Cleanup in `finally` (Muster `EclipseBuildToolTest.deleteTestMarkers`) —
  keine cross-run-Leichen (diese würden andere Tests + den User-Workspace verschmutzen).
- Vor jedem Plugin-JUnit-Lauf: `eclipseBuildProject` über `org.sterl.llmpeon` **und**
  `org.sterl.llmpeon.test` (stale bin/); PDE-Trust-Dialog einmalig (Rule 13).
- Kein Docs-Flip, keine SOLL-Änderung, keine neuen Dependencies.
- Commit nach JEDEM grünen Inkrement auf `analysis/tool-evolution` (Code + Tests;
  keine Docs-Änderungen in diesem Zyklus).
- DONE-Claims vom PO gegen IST verifiziert (Rule 28): Suite-Status + Test-Namen.

## 6. BDD-Akzeptanz (Status bleibt ❌)
- **UC-JD-13** (R-JD-11) — GIVEN Marker im gewählten Projekt (Line-BP mit
  condition/hitCount, Exception-BP, UI-gesetzter BP) WHEN `list_breakpoints` (ohne
  Session) THEN jeder Marker mit id/Typ/Ort/condition/hitCount/enabled; GIVEN keine
  Marker THEN leere Liste mit Scope-Disclosure; GIVEN anderes Projekt THEN dessen
  Line-BP-Marker erscheinen nicht (Exception-BPs workspace-wide, disclosed);
  GIVEN kein gewähltes Projekt THEN ehrlicher Fehler, kein Session-Text, kein Raten.
  → `DebugListBreakpointsTest` ×5 + `JavaDebugToolTest.listBreakpointsDoesNotFailWithoutSession`.
- **UC-JD-14** (R-JD-12) — GIVEN Marker hitCount `-1` WHEN Response THEN `hitCount: 0`;
  GIVEN `3` THEN `3`. → `DebugJsonUnitTest.breakpointResponseClampsNegativeHitCount`
  + `breakpointResponseKeepsPositiveHitCount` (+ Clamp-Regression in der List-Test-Klasse).

## 7. Test-Strategie
- Plugin-Gate = **ganze** PDE-Suite `org.sterl.llmpeon.test` (Baseline aus
  Vorbereitungen + neue Tests), vorher Build beider Plugin-Projekte.
- UC-JD-13-Tests sind echte Integration gegen `test_project` (ADR-0037) — keine
  Live-Debug-Session, aber **echte Marker** (self-verifying für Marker-Typ/Attribute).
- Keine Sleeps/Timeouts nötig (Marker-Operationen sind Workspace-synchron);
  `waitForSuspend`-Logik wird nicht berührt.
- Reihenfolge: I1 (klein, liefert `displayHitCount`) → I2 (Wiring + Action).

## 8. Offene Fragen
Keine. Q1 ist von Jon entschieden (gewähltes Projekt der Chat-View, nicht Editor)
und in I2 + §3.4 aufgegangen; Exception-BPs workspace-wide wurde als JDT-Wahrheit
mit Disclosure akzeptiert.
