---
idPrefix: JD
---


# Java-Debugger-Tool — Agent-gesteuertes Debugging (User-managte Session)

> **Status:** ✅ done (2026-09-21, Paul specified 2026-09-19) — Debug-Session **vom User gestartet und
> gemanagt**, das LLM unterstützt; **keine Confirmations**. Verifikation: automatisiert UC-JD-1 + Stub-Tests,
> UC-JD-2…6 manuell (E2E-Smoke 2026-09-21, [ADR-0051](adr/0051-debugger-no-live-session-tests.md)).
> Basis: Eclipse-Debug-Model (JDT), nicht DAP — [ADR-0050](adr/0050-debugger-jdt-not-dap-user-session.md).
> API-Fakten 2026-09: [ADR-0049](adr/0049-jdt-debug-2026-09-api-drift.md).
> Ursprung: CR-4 des Copilot-Vergleichs — [resolved-points.md](resolved-points.md) („Tool-Evolution-Run").
> **2026-09-23 (❌ R-JD-13 + Rename):** Breakpoint-Actions (`debugJavaSetBreakpoint` /
> `debugJavaSetExceptionBreakpoint` / `debugJavaRemoveBreakpoint`) arbeiten jetzt **ohne Session**
> (Marker-Op, JDT installiert in laufende + später gestartete VMs); alle Tool-Namen snake_case →
> `debugJava*` camelCase — [ADR-0054](adr/0054-tool-naming-camelcase-family-prefix.md).

## Ziel

Da Mek kann eine **vom User gestartete** Debug-Session lesen und ändern — Debugging als Dialog zwischen
User (besitzt/managt die Session) und LLM (Hilfe: Zustand interpretieren, Variablen ändern, Breakpoints
setzen, Steps). Weil die Session User-Property ist, brauchen wir **keine Confirmations**.

## Actions (voll, in einer Stufe)

- **Lesend:** `debugJavaGetState`, `debugJavaGetVariables` (nested, depth — Frame-Lokale **plus statische
  Felder des Frame-Typs** als separater `statics`-Block, R-JD-9), `debugJavaGetStackTrace`,
  `debugJavaGetException` (Exception am Suspend, R-JD-10), `debugJavaListBreakpoints` (R-JD-11,
  auch ohne Session — gewähltes Projekt, Exception-BPs workspace-wide).
- **Ändernd, ohne Session (Marker-Op, R-JD-13):** `debugJavaSetBreakpoint` (conditional + hitCount),
  `debugJavaSetExceptionBreakpoint`, `debugJavaRemoveBreakpoint`.
- **Ändernd, Session nötig (R-JD-1):** `debugJavaEvaluateExpression` (Timeout; Primitive/String/null als
  Wert, Objekt-Ergebnisse mit Feldwerten bis Tiefe 2, R-JD-9), `debugJavaSetVariable` (nur
  Primitiven/String/null), `debugJavaStepOver`/`debugJavaStepIn`/`debugJavaStepOut`,
  `debugJavaContinue`, `debugJavaSuspend`.

## Regeln

### R-JD-1 — Keine Session = ehrlicher Fehler ✅

Die **session-gebundenen** Actions (alles außer `debugJavaSetBreakpoint`/`debugJavaSetExceptionBreakpoint`/
`debugJavaRemoveBreakpoint` — R-JD-13 — und `debugJavaListBreakpoints` — R-JD-11) setzen eine **aktive
Debug-Session** voraus (vom User gestartet). Ohne Session: ehrlicher
Fehler („no active debug session — start debugging in the Debug view first"), kein Auto-Start durch den
Agenten, kein Auto-Disconnect.

#### UC-JD-1 — noSessionFailsHonest ✅
- GIVEN keine Debug-Session WHEN irgendeine Action THEN ehrlicher Fehler mit Hinweis, kein Auto-Start
  (Launch-Zähler unverändert). *(Automatisiert: `JavaDebugToolTest.noSessionFailsHonest` über alle
  11 session-gebundenen Actions — debugJavaGetException seit R-JD-10, `348d521`.)*

### R-JD-2 — Session-Lifecycle gehört dem User ✅

Der User startet, fortsetzt und beendet die Session im Debug-UI. Der Agent mutiert nur Zustand
*innerhalb* der laufenden Session; der User bleibt jederzeit Herr der Session.

#### UC-JD-2 — userTerminatesSessionBetweenCalls ✅ (manuelle Verifikation 2026-09-21, ADR-0051)
- GIVEN User beendet Session WHILE Agent zwischen Calls THEN nächster Call = ehrlicher Fehler
  (kein Auto-Start, kein Auto-Reconnect, keine neue Launch).

### R-JD-3 — JSON-Output, synchron, stateless ✅

JSON-Output, pretty; synchron — jedes Tool gibt sofort zurück, was es sieht. Kein Async-Bedarf, kein
Zustands-Gedächtnis im Tool — Session-State lebt im Eclipse-Debug-Model.

#### UC-JD-3 — readActionsReturnNestedJson ✅ (manuelle Verifikation 2026-09-21, ADR-0051)
- GIVEN laufende Debug-Session WHEN `debugJavaGetState` / `debugJavaGetStackTrace` / `debugJavaGetVariables depth=2` THEN
  JSON (pretty), Variablen nested bis Tiefe 2.

### R-JD-4 — Basis Eclipse-Debug-Model, keine Confirmations ✅

Basis: Eclipse-Debug-Model (JDT), nicht DAP ([ADR-0050](adr/0050-debugger-jdt-not-dap-user-session.md)).
Keine Confirmations (bewusst, Session = User-Property — Paul 2026-09-19): jede Action wirkt sofort;
der User sieht jede Änderung unmittelbar im Debug-UI.

#### UC-JD-4 — setVariableImmediateNoConfirmation ✅ (manuelle Verifikation 2026-09-21, ADR-0051)
- GIVEN laufende Session WHEN `debugJavaSetVariable` auf Primitive/String/null THEN Wert sofort geändert, ohne
  Bestätigungsrunde; Debug-UI zeigt ihn. GIVEN Ziel ist kein Primitive/String/null THEN ehrlicher Fehler
  („primitives, String or null only (declared: <type>)").

### R-JD-5 — Tool für den Dev-Agenten ✅

Das Tool gehört Da Meks Toolset (`isEditTool() = true`, Registrierung in `SharedToolsComponent`);
Plan-/Review-/Search-/ReadOnly-Custom-Agents filtern es automatisch raus. Auswirkung nur auf die
debuggte App. Allowlist-Präfix für Custom Agents: `debugJava` (Naming: ADR-0054).

#### UC-JD-5 — conditionalBreakpointWithHitCount ✅ (manuelle Verifikation 2026-09-21, ADR-0051)
- GIVEN laufende Session WHEN Breakpoint conditional + hitCount THEN Suspend genau wie konfiguriert
  (JDI-Semantik: hitCount feuert erst am N-ten Hit, die Condition wird danach geprüft — korrekte
  Testkombination ist `hitCount=3` + `condition="counter == 2"`).

#### UC-JD-6 — stepAndControlActions ✅ (manuelle Verifikation 2026-09-21, ADR-0051)
- GIVEN suspended Thread WHEN `debugJavaStepOver`/`debugJavaStepIn`/`debugJavaStepOut`/`debugJavaContinue`/`debugJavaSuspend`/`debugJavaEvaluateExpression`
  THEN Ausführung bewegt sich wie verlangt; `debugJavaEvaluateExpression` mit Timeout respektiert;
  `debugJavaStepOut` am Top-Frame = ehrlicher „use continue"-Hinweis.

### R-JD-6 — Session-Erkennung schließt tote Targets aus ✅

Session-Lookup filtert Targets, deren Process terminiert ist oder keine Threads mehr hat
(`IProcess.isTerminated()` / `!hasThreads()`) — eine **selbst beendete** VM (Programm lief durch)
zählt als „no active debug session", genau wie eine UI-Terminierung; `debugJavaContinue` pollt dann nicht
in die Deadline. Kollateral aus dem E2E-Smoke (F7, 2026-09-21, Fix `2efaaf6`).

#### UC-JD-7 — deadVMSelfExitFailsHonest ✅
- GIVEN VM endete von selbst (kein UI-Terminate) WHEN irgendeine Action THEN ehrlicher
  „no active debug session"-Fehler, kein 30-s-Poll, keine „running"-Meldung mit leeren Threads.
  *(Automatisiert: `DebugSessionLookupTest` ×3 + `DebugSessionThreadsTest` ×2 — Mutation-Nachweis
  für den Session-Gate liegt separat vor (3 rote Pfade).)*

### R-JD-7 — debugJavaSetBreakpoint verlangt geladene Compilation Unit ✅

Breakpoint-Erstellung löst das File zuerst über JDT auf (`JavaCore.createCompilationUnitFrom` →
`findPrimaryType`) und legt den Marker nur bei geladener CU an; sonst ehrlicher Fehler mit Datei +
Projekt + Grund (E2E F1: rohes `IFile` → „not a Java compilation unit", Fix `2efaaf6`).

#### UC-JD-8 — breakpointRequiresCompilationUnit ✅
- GIVEN Datei im Projekt WHEN `debugJavaSetBreakpoint` THEN CU-Auflösung vor Marker-Factory; Nicht-Source-
  File / fehlender Primär-Typ → ehrlicher Fehler, keine Marker-Leiche. *(Automatisiert:
  `DebugPrimaryTypeTest` ×3.)*

### R-JD-8 — Response-Vertrag: State-Semantik, Session-Kennung, Wert-Typen ✅

- `vm.state` = „suspended", wenn Target suspended ODER ein non-system-Thread suspended ist
  (debugging-relevante Frage „wo steht es?"), sonst „running"; zusätzlich `vm.suspendedThreads`
  (Anzahl suspended non-system Threads) — mixed State ehrlich gezählt.
- `debugJavaGetState` trägt `session` = Launch-Name + pid (`target.getProcess().getAttribute(ATTR_PROCESS_ID)`,
  **String** — `JDIDebugTarget` implementiert `IProcess` nicht, `instanceof` wäre immer false).
- `debugJavaSetVariable`-Response liefert Primitive als JSON-Zahl (nicht String) — konsistent mit
  `debugJavaGetVariables` (E2E F3).

#### UC-JD-9 — stateAndValueContract ✅
- GIVEN main suspended, System-Threads running, Target selbst NICHT suspended THEN `vm.state =
  "suspended"`, `suspendedThreads = 1`, `session` nennt Launch + pid; `debugJavaSetVariable`-Response
  liefert den Wert als JSON-Zahl. *(Automatisiert: `DebugSessionThreadsTest` ×2 +
  `DebugJsonUnitTest.valueResponseRendersPrimitivesAsJsonPrimitives`.)*

### R-JD-9 — Statische Felder + Objekt-Feldwerte (F2) ✅

- `debugJavaGetVariables` liefert zusätzlich zu den Frame-Lokalen die **statischen Felder des Frame-Typs**
  als separates JSON-Feld `"statics"` (leeres Array, wenn keine) — dieselbe Verschachtelung, auch
  unter dem `depth`-Parameter. Getrennt von `locals`, damit der LLM Scope sauber trennt; **kein
  zusätzlicher Parameter** — wer `debugJavaGetVariables` ruft, will den vollen Scope.
- `debugJavaEvaluateExpression` rendert ein Objekt-Ergebnis nicht mehr nur als Referenz-ID „ (id=N)":
  Felder werden mit demselben Walker wie `debugJavaGetVariables` aufgelöst, **fixe Tiefe 2**; Primitive,
  String und null bleiben unverändert Wert. Kein neuer Parameter — die natürlichste Erwartung ist,
  dass `evaluate("p")` die Feldwerte zeigt.

#### UC-JD-10 — staticsInGetVariables ✅
- GIVEN Frame in einer Klasse mit statischen Feldern WHEN `debugJavaGetVariables` THEN Antwort enthält
  `statics` mit den Feldern (Name, Typ, Wert) und `locals` unverändert; GIVEN Klasse ohne statische
  Felder THEN leeres `statics`-Array. *(Automatisiert: `DebugJsonUnitTest.staticsInGetVariables`
  ×2 — Proxy-Stub, keine Session nötig.)*

#### UC-JD-11 — evaluateRendersObjectFields ✅
- GIVEN `debugJavaEvaluateExpression("p")` liefert ein Objekt WHEN das Ergebnis gerendert wird THEN die
  Felder des Objekts stehen bis Tiefe 2 im Output — keine bloße „ (id=N)"-Referenz; Primitive/
  String/null bleiben als Wert gerendert. *(Automatisiert: `DebugJsonUnitTest`
  `evaluateRendersObjectFieldsToDepth2` + Primitive/null-Regression.)*

### R-JD-10 — `debugJavaGetException`: Exception am Suspend, stateless + ehrlich ✅

Neue Action `debugJavaGetException` (Thread-Auflösung wie `debugJavaGetStackTrace`). Sie scannt den Top-Frame
(lokale Variablen inkl. Catch-Parameter) nach einer Variable, deren Wert ein Throwable ist, und
liefert Typ + Message + Variablenname. Keine gefunden → ehrlicher Fehler („no exception variable
in top frame"). **Stateless** (R-JD-3 bleibt): kein Event-Listening, kein Cache.

**Recognition ist name-basiert** (bewusst, kein Workspace-`isAssignableFrom` — das würde den
Session-freien Stub-Test brechen): exakt `java.lang.Throwable` oder Simple-Name endet auf
`Exception`/`Error`. **Grenze:** ein Custom-Throwable mit unüblichem Namen wird nicht erkannt
(ehrlicher Fehler statt Befund) — dann bleibt `debugJavaGetVariables` der Weg; die Tool-Description nennt
die Grenze. Zweite dokumentierte Grenze: am Throw-Site eines ungefangenen `throw new X(…)`
existiert keine benannte Variable — auch dort findet `debugJavaGetException` nichts.

> **WEIL** (E2E-Smoke 2026-09-21): am Exception-Suspend zeigte `debugJavaGetState` nur den Frame, nicht
> welche Exception geworfen hat — der Tester musste sie sich aus Frame+Kontext erschließen. Eine
> echte Event-Abfrage (JDI-ExceptionEvent) würde R-JD-3 (stateless) brechen und mit JDTs eigenem
> Event-Handler konkurrieren; der frame-lokale Scan ist der kleinste ehrliche Weg.

#### UC-JD-12 — getExceptionFindsThrowableInTopFrame ✅
- GIVEN Thread an Exception-Suspend mit Catch-Variable `e` WHEN `debugJavaGetException` THEN Typ + Message +
  Variablenname; GIVEN kein Throwable im Top-Frame THEN ehrlicher Fehler, kein erfundener Typ;
  GIVEN Custom-Throwable `my.company.WeirdThrowable` (unüblicher Name) THEN nicht erkannt,
  ehrlicher Fehler. *(Automatisiert: `DebugJsonUnitTest.exceptionFindsThrowableInTopFrame` /
  `exceptionHonestErrorWhenNone` / `exceptionNotRecognizedByUnusualName` +
  `JavaDebugToolTest.noSessionFailsHonest` über alle 11 session-gebundenen Actions.)*

### R-JD-11 — `debugJavaListBreakpoints`: Breakpoint-Landkarte, auch ohne Session ✅

Neue lesende Action: listet **alle Breakpoint-Marker des gewählten Projekts** — `id`, Typ
(line/exception), Ort (Typ + Zeile bzw. Exception-Klasse), `condition`, `hitCount`, `enabled`.
Bewusst **ohne Session nutzbar** (bewusste Ausnahme zu R-JD-1): Marker sind Workspace-State,
genau die Phantom-BPs, die der Agent nicht sehen kann, existieren zwischen Sessions — dort zu
listen ist der Zweck (E2E 2026-09-22: der Agent musste über Laufzeitverhalten raten; Paul
vermutet darin die Ursache der krummen Hits). Entfernen bleibt über `debugJavaRemoveBreakpoint(id)`.

#### UC-JD-13 — listBreakpointsMapsMarkers ✅
- GIVEN Marker im gewählten Projekt (Line-BP mit condition/hitCount, Exception-BP, UI-gesetzter
  BP) WHEN `debugJavaListBreakpoints` (ohne Session) THEN jeder Marker mit id/Typ/Ort/condition/
  hitCount/enabled; GIVEN keine Marker THEN leere Liste mit Scope-Disclosure; GIVEN anderes
  Projekt THEN dessen Marker erscheinen nicht.

### R-JD-12 — Breakpoint-Response: `hitCount` ohne JDT-Default ✅

`DebugJson.breakpointResponse` zeigt heute den rohen Marker-Wert (`hitCount: -1`, JDT-Default
„niemals expire") — die Tool-Beschreibung sagt `0 = every hit`. Anzeige-Clamp: Marker-Wert `< 0`
wird als `0` gemeldet (Verhalten der Marker bleibt unangetastet, nur die Darstellung ist ehrlich
zur Beschreibung).

#### UC-JD-14 — breakpointResponseClampsNegativeHitCount ✅
- GIVEN Marker mit hitCount `-1` WHEN Response gerendert THEN `hitCount: 0`; GIVEN `hitCount = 3`
  THEN unverändert `3`. *(Automatisiert: `DebugJsonUnitTest`-Stub.)*

### R-JD-13 — Breakpoints ohne Session (Marker-Op) ❌

`debugJavaSetBreakpoint`, `debugJavaSetExceptionBreakpoint` und `debugJavaRemoveBreakpoint` brauchen
**keine** Debug-Session: Breakpoints sind persistente JDT-Marker, und JDT installiert sie automatisch
in laufende **und** später gestartete VMs. Ohne Session legen/entfernen sie den Marker trotzdem; die
Response nennt ehrlich, dass keine Session aktiv ist („no active session — breakpoint stored as
marker, installed when a session starts") und enthält keinen VM-Install-Status. CU-Auflösung
(R-JD-7) und hitCount-Clamp (R-JD-12) gelten unverändert. Session-gebunden bleiben Step/Continue/
Suspend/State/Evaluate/SetVariable (R-JD-1).

> **WEIL** (Paul 2026-09-23): genau der Workflow „BPs vorbereiten vor dem Launch" war die Lücke,
> die die Phantom-BPs im E2E 2026-09-22 offengelegt hat — der User setzt BPs im UI, weil der Agent
> es ohne Session nicht kann. Sichtbarkeit: `debugJavaListBreakpoints` (R-JD-11), Rückweg:
> `debugJavaRemoveBreakpoint`.

#### UC-JD-15 — breakpointsWorkWithoutSession ❌
- GIVEN keine Debug-Session WHEN `debugJavaSetBreakpoint` THEN Marker existiert im gewählten Projekt,
  Response nennt „no active session … stored as marker"; WHEN `debugJavaRemoveBreakpoint(id)` THEN
  Marker entfernt, dito-Response; GIVEN Session aktiv WHEN gleiches Set THEN BP wie bisher installiert
  (Install-Status in der Response). *(Automatisiert: `DebugBreakpointNoSessionTest` ×5 (Marker-Ebene: no-Session-Response, Projekt-Auflösung via currentProject, ehrlicher Fehler ohne Projekt) + `DebugJsonUnitTest` ×2 (no-Session-Response-Rendering) + `JavaDebugToolTest.noSessionFailsHonest` (11 session-gebundene Actions); Install-Pfad manuell.)*

## Thread-Enumeration (technischer Befund, ADR-verlinkt)

Thread-Listing und -Auflösung laufen ausschließlich über **`target.getThreads()`** —
`getRootThreadGroups()` listet `main` schlicht nicht (diagnose.txt-Beweis: 5 vs. 7 Threads,
main suspended in nur einem der beiden Wege). WARUM + Fallstricke: [ADR-0049](adr/0049-jdt-debug-2026-09-api-drift.md).
