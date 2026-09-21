# Build-Plan: Story C — Java-Debugger-Tool (CR-4)

**Branch:** `analysis/tool-evolution` (dediziert, schon vorhanden — **nicht** neu anlegen/wechseln)
**Modul:** `org.sterl.llmpeon` (Plugin) + `org.sterl.llmpeon.test` (OSGi JUnit 4, keine externen Assertion-Libs) + `docs/`
**SOLL:** `docs/java-debugger-tool.md` — R-JD-1…5, UC-JD-1…6 (alle ❌). IDs sind VORGEGEBEN: nicht weiterzählen, keine neuen.
**Ziel:** Da Mek kann eine **vom User gestartete** Debug-Session lesen/ändern (JDT-Debug-Model, keine DAP, keine Confirmations). Synchron, stateless, JSON pretty.

---

## ⚠️ STOP-AND-ASK (Memory #26) — zuerst lesen

Bei **Compile-Fehlern ohne Lösung, nicht-grünen Tests, IST-Widersprüchen zu diesem Plan oder Unklarheiten**: aktiv bei Jon (askDev-Kanal) nachfragen — **nie still workarounden oder SOLL ändern**. Konkrete Trigger für diese Story:
- JDT-Debug-API weicht ab (diese Generation 2026-07 hat umbenannte Typen — s. §3; falls Da Mek auf einen **weiteren** Umbau stößt: fragen).
- `evaluate_expression`: Probe-Ergebnis uneindeutig (s. D6) → fragen, nicht still internal-API-Tuning.
- OSGi-Debug-Fixture startet keine Session (JRE/JDWP-Umgebung) → **nicht** Test schwächen; fragen (Optionen: manuelle Smoke-Verifikation à la R-MCP3, oder Umgebungs-Fix) — s. §6.
- Kein Git / Branch `analysis/tool-evolution` nicht aktiv → erst fragen.

---

## 1. Kontext

Da Mek debuggt heute manuell im UI. Story C gibt dem Dev-Agenten ein Tool, das eine **User-gestartete** Debug-Session liest (State, Variablen, Stack) und ändert (Variablen, Breakpoints, Steps, Ausdrücke) — ohne Confirmations, weil die Session User-Property ist (Paul 2026-09-19). Basis = Eclipse-Debug-Model (JDT), keine DAP (R-JD-4). Auslieferung in 5 vertikalen Inkrementen, je grün, je eine Polarität (alle nur-hinzufügen).

## 2. Design-Entscheidungen (fest)

### D1 — Ort & Klasse
`org.sterl.llmpeon.parts.tools.debug.JavaDebugTool` (Plugin, neues Subpackage `debug` — nicht exportiert, Export-Package bleibt `org.sterl.llmpeon.parts`). Erbt `AbstractTool` (wie `WebGetTool`/`EclipseBuildTool`), **`isEditTool() = true`** (R-JD-5). Stateless: kein Feld-State, Session-State lebt im Eclipse-Debug-Model (R-JD-3).

### D2 — R-JD-5 Wiring (im IST verifiziert 2026-09-20 — keine Filter-Änderungen nötig)
- `AbstractAgent.getToolFilter()` = `p -> true` (Default: Dev/PO erhalten alles).
- `AiPlanAgent:70-71`, `AiReviewAgent:74-75`, `SearchAgentTool.java:26` (core) filtern `isEditTool` automatisch raus.
- `CustomAgent.java:187`: read-only Custom Agents erhalten es nicht.
- Registrierung: `SharedToolsComponent`-Constructor (wie `new EclipseBuildTool()`, ~Zeile 67): `sharedToolService.addTool(new JavaDebugTool())` — **kein** disk-tools-Gate (Eclipse-Familie).
- Erwartete Matrix (Test-Evidenz I1): Da Mek ✓, Plan ✗, Review ✗, SearchAgent ✗, readOnly-Custom ✗.

### D3 — Actions (13 @Tool-Methoden, snake_case-Namen exakt wie SOLL)
| @Tool(name) | Methode | Parameter (alle `@P`, optionale = leere String/0 = unset) |
|---|---|---|
| `get_state` | `getState` | — |
| `get_stack_trace` | `getStackTrace` | `thread` (Name, optional) |
| `get_variables` | `getVariables` | `thread`, `frame` (Index, 0 = top), `name` (Pfad `a.b.c` via `getVisibleChildren`-Lookup, optional), `depth` (default 1, max 5) |
| `evaluate_expression` | `evaluateExpression` | `thread`, `frame`, `expression`, `timeoutMs` (default 10000) |
| `set_variable` | `setVariable` | `thread`, `frame`, `name`, `value` (String) |
| `set_breakpoint` | `setBreakpoint` | `file` (projekt-relativ oder `/proj/…`, rel. = Projekt der Session), `line`, `condition` (optional), `hitCount` (0 = aus), `suspendPolicy` (`THREAD` default/`VM`) |
| `set_exception_breakpoint` | `setExceptionBreakpoint` | `exceptionType` (FQN), `suspendPolicy` (default `THREAD`), `catchUncaught` (default true), `catchCaught` (default false), `subTypes` (default true) |
| `remove_breakpoint` | `removeBreakpoint` | `id` (Marker-ID aus create-Response) — **Zusatz über SOLL** (Hygiene: Agent-Breakpoints müssen wieder raus; SOLL „voll" = erlaubt) |
| `step_over` / `step_in` / `step_out` | `stepOver`/`stepIn`/`stepOut` | `thread`, `waitMs` (default 15000) |
| `continue` | `resume` | `thread`, `waitMs` (default 30000) |
| `suspend` | `suspend` | — |

Tool-Descriptions: je 10–25 Wörter, imperativ (WebGetTool-Stil) → landen in `docs/tool-descriptions-inventory.md` (je Inkrement die Zeilen der darin gelieferten Actions).

### D4 — Session-Lookup (pro Call, stateless)
`DebugPlugin.getDefault().getLaunchManager().getLaunches()` → `launch.getDebugTargets(IJavaDebugTarget.class)` → aktiv = `!isTerminated()`.
- **0 aktiv** → ehrlicher Fehler: `no active debug session — start debugging in the Debug view first` (R-JD-1/2; **kein** Auto-Start, **kein** Auto-Disconnect).
- **>1 aktiv** → ehrlicher Fehler, listet alle (`launch name — vm name`), fordert eine Session.
- **Thread-Auflösung:** `thread`-Name; Default = erster **suspended**, sonst erster **nicht-System** Thread.
- Projekt für `file`-Relative: Launch-Config-Attribut `IClasspathAttribute.ATTR_PROJECT_NAME` der Session.

### D5 — JSON (R-JD-3)
Jackson `ObjectMapper().writerWithDefaultPrettyPrinter()` (jackson-databind liegt im Plugin-Bundle-ClassPath → auch im Test-Fragment sichtbar). Shapes:
- `get_state`: `{ "vm": {name, version, state: "suspended"|"running", outOfSynch}, "threads": [{name, state, system, topFrame: {method, type, line}}] }`
- `get_stack_trace`: `[{index, method, type, line, methodEntry}]`
- `get_variables`: `[{name, type, value}]`, Objekt → `fields: […recursiv bis depth…]`, Array → `elements: [erste 20, „…N more"]` (Limiter **im Output benannt** — AGENTS.md: Tool lügt nicht).
- Alle Control-Responses: resultierender `topFrame` + `state`; Zeitüberschreitung: `„resumed; still running after N ms — no breakpoint hit?"` (ehrlich, kein Timeout-Lügen).

### D6 — `evaluate_expression` (höchste Risiko-Komponente — Probe-first)
**Verifizierter IST (2026-07, jdt.debug 3.26.100.v20260714-1432):** **kein** öffentliches `evaluateExpression(String, EvaluateContext)` mehr auf Frame/Thread (altes `org.eclipse.jdt.debug.model`-Paket existiert nicht mehr). Reihenfolge in Inkrement I4, **Bevor Code** (read-only Shell-Diagnose erlaubt, z. B. `javap -cp <jdt.debug-jar>`):
1. **Probe:** öffentliches Eval-Entry in der aktuellen Generation suchen (`javap` auf `IJavaStackFrame`/`IJavaReferenceType`/`IJavaThread`; jar aus `~/.m2/repository/p2/osgi/bundle/org.eclipse.jdt.debug/3.26.100.v20260714-1432/`). Fund → das nutzen, in Evidenz nennen.
2. **Fallback (erwartet):** `IJavaThread.runEvaluation(IEvaluationRunnable, monitor, DebugEvent.EVALUATION, false)` (öffentlich, verifiziert) + internes, aber **exportiertes** AST-Engine `org.eclipse.jdt.internal.debug.core.eval.ast.engine.ASTEvaluationEngine` (im Target vorhanden — content.xml „provided" Packages bestätigt). Hard-Timeout: Worker-Thread + `Future.get(timeoutMs)`; Timeout → ehrliche Meldung („evaluation timed out after N ms — check the Debug view; the VM evaluation may have completed").
3. **Falls weder 1 noch 2 kompiliert/funktioniert → STOP-AND-ASK** (Action nicht still streichen — SOLL verlangt sie).

### D7 — `set_variable` (UC-JD-4)
- Ziel-Gate **vor** dem Set: `frame.findVariable(name)` → `null` = ehrlich „variable not visible in frame". Typ-Check über `var.getReferenceTypeName()` (oder `getJavaType()`): erlaubt = **Primitiven** (boolean/byte/short/int/long/float/double/char) + `java.lang.String`; `null`-Wert nur bei `java.lang.String`-Deklaration. Sonst ehrlicher Fehler: „set_variable targets primitives, String or null only (declared: <type>)".
- Set: `IJavaValue v` via `target.newValue(…)`/`nullValue()` (verifiziert) → `var.setValue(v)` (`IVariable extends IValueModification`, verifiziert). Wert-Parser für `value`-String: `null`/`true`/`false` → boolean; ganzzahlig → int (widening) oder long bei Overflow; float/double; 1 Char; sonst String. Response: `{"name","type","value"}` (neuer Wert).

### D8 — Breakpoints (UC-JD-5)
- **Line-BP:** `IFile.createMarker(IBreakpoint.P_TYPE)` → `IBreakpointUtils.setBreakpointType(marker, „org.eclipse.jdt.debug.lineBreakpoint")` (Konstante/ID per Compile-Verify — bei Abweichung STOP-AND-ASK) → `IBreakpointUtils.setLineBreakpointAttributes(marker, line)` → via `org.eclipse.debug.internal.core.WorkspaceResourceManager` (intern, aber Standardweg — Target-Check + Compile-Verify, sonst STOP-AND-ASK) `setBreakpointMarker(marker, null)` → `IBreakpoint bp = IBreakpointManager.getBreakpoint(marker)` cast `IJavaBreakpoint` → `bp.setCondition(cond)`, `bp.setHitCount(n)`, `bp.setSuspendPolicy(IJavaBreakpoint.SUSPEND_THREAD|SUSPEND_VM)` (alle 3 verifiziert in dieser Generation, `RESUME_ON_HIT=3` existiert auch — wird **nicht** exposed).
- **Exception-BP:** Marker-Typ `org.eclipse.jdt.debug.exceptionBreakpoint` + Attribute `IJavaDebugConstantAttributes.ATTR_TYPE` (FQN), `ATTR_CATCH_UNCAUGHT`, `ATTR_CATCH_CAUGHT`, `ATTR_SUB_TYPES`, `ITriggerPoint.ATTR_SUSPEND_POLICY` → `rm.setBreakpointMarker(marker, new IDebugTarget[]{target})` (VM-scope). Compile-Verify; Abweichung → STOP-AND-ASK.
- **Response (beide):** `{id (Marker-ID), type, file?, line?, exceptionType?, condition?, hitCount, suspendPolicy, installed}`. `installed=false` ehrlich melden (Breakpoint „greift" erst bei VM-Install).
- `remove_breakpoint`: `IBreakpointManager.getBreakpoint(marker)` → `delete()` + Marker löschen; unbekanntes `id` = ehrlicher Fehler.

### D9 — Control-Actions (UC-JD-6)
`step_*`/`continue` sind JDI-seitig **non-blocking** → Tool wartet selbst auf das nächste Suspend (Poll `ISuspendResumeTarget.isSuspended()`, 100 ms Tick, Deadline `waitMs`), dann gibt es das **neue** `topFrame` zurück (synchron = „sofort zurück, was es sieht", R-JD-3). Edge-Handling, alles ehrlich:
- Timeout → „still running after N ms".
- VM terminated während Wartung → „terminated while waiting — check the Debug view" (kein Auto-Neustart, R-JD-1).
- `suspend` → `target.suspend()` → sofort suspended → JSON der suspended Threads.
- `step_out` auf Top-Frame (main) → ehrlich: „already at top frame — use continue".
- Threading: JDT-Calls blocken auf dem Agent-Background-Thread (Tools laufen nie auf UI-Thread) — **kein** `Job.create` nötig, **kein** UI-Zugriff (Plugin-AGENTS.md Threading-Regel eingehalten).

### D10 — Fehler-Konvention
Alle Aktionen werfen bei Fehlern `IllegalArgumentException` mit ehrlichem Kontext (Session/Thread/Frame/Pfad) — Tool-Loop rendert das als Tool-Fehler für das LLM (WebGetTool-Muster). **Log OR throw, nie beide.** Keine False Negatives: „not found" nur nach explizitem, benanntem Suchbereich.

## 3. VERIFIZIERTE API-Fakten — Eclipse 2026-07 / jdt.debug 3.26.100 (Da Mek: NICHT raten, hier drauf bauen)

| Fakt | Quelle (gelesen 2026-09-20) |
|---|---|
| Paket `org.eclipse.jdt.debug.model` **existiert nicht mehr**; alle Model-Typen in `org.eclipse.jdt.debug.core` | content.xml (provided Packages: nur `…debug.core`, `…debug.eval`, jdi, internal-*) |
| `IJavaFrame` → **`IJavaStackFrame`** (`org.eclipse.jdt.debug.core`) | findJavaType + Source |
| `IJavaStackFrame`: `getLocalVariables()` (inkl. Args), `findVariable(name)`, `getLineNumber(stratum\|null)`, `getMethodName()`, `getDeclaringTypeName()`, `getThis()→IJavaObject`, `forceReturn(value)` | Source gelesen |
| **`IJavaProcess` existiert nicht** → Threads via `IJavaDebugTarget.getRootThreadGroups()` → `IJavaThreadGroup.getThreads()` | findJavaType + Source |
| `IJavaDebugTarget`: `newValue(…)` (alle Primitiven/String), `nullValue()`, `supportsRequestTimeout()/setRequestTimeout(int)`, `refreshState()`, `getVMName()/getVersion()` | Source gelesen |
| `IJavaThread` (extends `IThread`,`IFilteredStep`): `suspend/resume/stepOver/stepInto/stepReturn`, `getStackFrames()`, `isSuspended()`, `findVariable`, `isSystemThread()`, `stop(IJavaObject)`, `runEvaluation(IEvaluationRunnable, monitor, detail, hitBreakpoints)` | Source gelesen |
| `IJavaVariable` (extends `IVariable→IValueModification`): `getName()`, `getValue()`, `getVisibleChildren()` (IVariable), `setValue(IValue)`, `setVariable(name, Object)`, `getReferenceTypeName()`, neu: `getJavaType()`, `isLocal()` | Source gelesen |
| `IJavaObject`: `getField(name, superField)→IJavaFieldVariable`, `getUniqueId()`; Arrays: **`IJavaArray`** (implements `IJavaObject`, `IIndexedValue` — `getArrayValue(i)` o. ä. per Compile-Verify) | Source/Metadata |
| `IJavaBreakpoint`: `setHitCount/getHitCount`, `setSuspendPolicy` (`SUSPEND_VM=1`,`SUSPEND_THREAD=2`,`RESUME_ON_HIT=3`), `isInstalled()`, `isDisableOnHit()`; Condition via `ITriggerPoint` (debug core: `setCondition/getCondition`) | Source gelesen |
| `IJavaExceptionBreakpoint extends IJavaBreakpoint` — klassische Setter (`setType/setCatchUncaught/setCatchCaught/setSubTypes/setExcludeSubclasses`) **Compile-Verify** | Metadata |
| Plugin-MANIFEST: `org.eclipse.debug.core`, `org.eclipse.debug.ui`, `org.eclipse.jdt.launching` **bereits** in Require-Bundle; **`org.eclipse.jdt.debug` fehlt → hinzufügen** (MANIFEST-Edit in I1) | MANIFEST.MF gelesen |
| `org.eclipse.jdt.debug` im Target vorhanden (3.26.100) → Feature/Target-Platform **nicht** ändern nötig | content.xml |

## 4. Betroffene Dateien

| Datei | Änderung |
|---|---|
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/debug/JavaDebugTool.java` | **Neu** — Facade, 13 @Tool-Methoden, `isEditTool()=true`, Argument-Guards (D3, D7, D10) |
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/debug/DebugSession.java` | **Neu** — package-private: Session-Lookup (D4), Thread-/Frame-/Projekt-Auflösung |
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/debug/DebugJson.java` | **Neu** — reine Funktionen: Debug-Model → pretty JSON (D5), Variablen-Walker (depth, 20-Element-Limit) |
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/ai/component/SharedToolsComponent.java` | I1: `sharedToolService.addTool(new JavaDebugTool())` (Constructor, ~Zeile 67) |
| `org.sterl.llmpeon/META-INF/MANIFEST.MF` | I1: `Require-Bundle` += `org.eclipse.jdt.debug` |
| `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/DebugSessionFixture.java` | **Neu** (I2) — Launch/Warten/Cleanup, s. §6 |
| `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/JavaDebugToolTest.java` | **Neu** (I1+I2…) — Tests, s. §7 |
| `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/SharedToolsComponentTest.java` | I1: +1 Assertion — `JavaDebugTool` registriert + `isEditTool`-Filter-Matrix (PO-WEB-8-Vorbild, Zeile 174) |
| `docs/tool-descriptions-inventory.md` | je Inkrement: Zeilen für die gelieferten Actions (Name, Beschreibung, isEditTool=✔) |

**Unberührt:** Core-Modul (kein Code), Releng/Feature (jdt.debug im Target), Prompts (keine Prompt-Referenzen an `eclipseRead…`/Debug — Grep-verifiziert, falls Da Mek doch welche findet: **report-only**, ändern nur auf Anweisung).

## 5. Inkremente (je grün, je nur-hinzufügen, je Commit inkl. `docs/**`)

**Gate je Inkrement:** (1) Git: Branch `analysis/tool-evolution` aktiv · (2) `eclipseBuildProject` `org.sterl.llmpeon` **und** `org.sterl.llmpeon.test` (Memory #16 — stale bin/ bricht JUnit) · (3) `eclipseRunTests` `org.sterl.llmpeon.test` — **ganze Suite** (erster Lauf: Workspace-Trust-Dialog manuell bestätigen, Memory #13; bei Timeout nie parallel nachstarten) · (4) `lintDocsAndTests` idPrefix `JD` → 0 offene Befunde · (5) Evidenzliste (Memory #28): je Deliverable file:line/Status-Zitat · (6) Commit.

- **I1 — Gerüst + noSession + Wiring** (UC-JD-1)
  MANIFEST += jdt.debug; `JavaDebugTool` mit allen 13 Methoden: Session-Lookup komplett; ohne Session → D4-Fehler (echt); mit Session + noch unimplementierte Action → ehrliches IAE `„<action> not yet available in this build"`; `DebugSession` + `DebugJson`-Grundgerüst; Registrierung; `SharedToolsComponentTest`-Matrix; `// UC-JD-1`-Test.
- **I2 — Read-Actions + JSON** (UC-JD-3)
  `get_state`, `get_stack_trace`, `get_variables` (nested depth, Array-Limit); `DebugSessionFixture`; Read-Tests.
- **I3 — set_variable + Breakpoints** (UC-JD-4, UC-JD-5)
  `set_variable` (D7), `set_breakpoint` (conditional+hitCount), `set_exception_breakpoint`, `remove_breakpoint`; Tests.
- **I4 — Control + evaluate** (UC-JD-6)
  `step_over/in/out`, `continue`, `suspend` (D9), `evaluate_expression` (**D6-Probe zuerst**); Tests.
- **I5 — User-Terminierung + Abschluss** (UC-JD-2)
  Test `userTerminatesSessionBetweenCalls` (Fixture terminiert, nächster Call = ehrlicher Fehler, kein Auto-Reconnect); finale Gesamt-Gates: **Surefire core + plugin grün** (core = Ground-Truth-Zahlen, Memory #21 — core wird nicht geändert, Zahlen dienen als Regression-Beleg) + `lintDocsAndTests` `UC-JD-\d+` → 0 Befunde (bekannte UC-DL-Befunde sind out-of-scope, nicht fixen).

## 6. Fixture-Strategie (explizit, I2) — echte Debug-Session im OSGi-Test

`DebugSessionFixture` (Test-Modul, kein Tool-Code) kapselt den kompletten Lifecycle — **alle** JD-Tests laufen dagegen, Cleanup ist zentral:

1. **Main-Klasse:** Test schreibt via `eclipseWriteFile("src/peontest/DebugFix.java", …)` (räumt `AbstractIntegrationTest.after()` schon ab). Inhalt: `int counter`-Loop; Breakpoint-Zeile = `tick(counter)`-Call; Helper-Methode `tick(int)` (für step_in/out); `if (args.length > 0 && counter == 3) throw new IllegalArgumentException("boom");` (Exception-BP-Test startet mit Program-Arg `"throw"`); Feld `static Point p` (nested-JSON depth-2-Test: `p.x`/`p.y`).
2. **Launch:** Launch-Config-Working-Copy `org.eclipse.jdt.launching.java` (Name `peon-debug-fixture`): `ATTR_PROJECT_NAME=test_project`, `ATTR_MAIN_TYPE_NAME=peontest.DebugFix`, `ATTR_DEFAULT_VM_ARGUMENTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:0`, bei Bedarf `ATTR_PROGRAM_ARGUMENTS`; `doLaunch(IDebugLaunchConfiguration.DEBUG_MODE)`. (Fixture-Projekt `test_project`: JDT-Maven-Projekt, `.classpath` = JRE-System-Container → löst auf die Test-JVM; JRE-System-Container + JDK = JDWP vorhanden — **Risiko, s. unten**.)
3. **Warten (alle Polls mit harten Deadlines, 100 ms Tick, kein `@Test(timeout)` — JUnit4-Timeout tötet den Test-Thread und verwaist die Launch):** Target sichtbar (10 s) → Breakpoint-Marker setzen (`IBreakpointUtils` + `WorkspaceResourceManager`, wie D8) → `isSuspended()` (15 s) → Main-Thread (nicht System, suspended) → `close()`.
4. **Cleanup (try/finally im Testkörper + Fixture.close in @After — Memory #6/12):** `launch.terminate()` + Warten auf Terminierung (15 s) → Breakpoint-Marker löschen → Working-Copy dispose (wird nicht gesichert) → Datei via `toDelete`. Kein persistenter State über Runs (frischer Marker/Datei je Run).
5. **RISIKO (realistisch benannt):** In-Workbench-Java-Launch im PDE-JUnit-Workbench ist in diesem Codebase **nicht verifiziert**. Fällt I2s Fixture auf (kein JDWP-Agent, JRE-Container ungelöst, Launch stirbt): → **STOP-AND-ASK**, Optionen mit Paul verhandeln: (a) manuelle Smoke-Verifikation der Session-Actions (à la R-MCP3/model-loading), Test deckt dann nur Lookup/JSON/Errors gegen echte User-Sessions via Fixture-Substitut, (b) Umgebungs-Fix (z. B. `VM_ATTR_ID` explizit setzen). **Kein** Test-Weichspülen (Test-Honesty, Memory #30).

## 7. Tests — `JavaDebugToolTest` (OSGi JUnit 4, extends `AbstractIntegrationTest`)

JUnit 4, **keine** externen Assertion-Libs (`assertEquals`/`assertTrue`/`assertThat`-frei: `org.junit.Assert`). `// UC-JD-x`-Kommentar an **jedem** gemappten Test (Linter-Vertrag).

| Test | UC | Setup → Erwartung |
|---|---|---|
| `noSessionFailsHonest` | `// UC-JD-1` | Keine Session. `getState()`, `setVariable(…)`, `setBreakpoint(…)`, `stepOver()`, `suspend()` → alle enthalten „no active debug session" + Start-Hinweis; danach: `getLaunches()` zeigt **keine** neue Launch (kein Auto-Start) |
| `readActionsReturnNestedJson` | `// UC-JD-3` | Fixture suspended am BP. `getState()` → pretty-JSON: vm.state=suspended, Main-Thread, topFrame.line == BP-Zeile; `getStackTrace()` → Frame mit `DebugFix.main`; `getVariables(depth=2)` → `counter` (int) + `p.fields[0..1]` (x=1,y=2) |
| `setVariableImmediateNoConfirmation` | `// UC-JD-4` | Fixture suspended. `setVariable(counter, "42")` → Response value=42; `getVariables()` bestätigt 42 — **ein** Call, keine Confirmations. Teil 2: `setVariable(p, "…")` → ehrlicher Fehler (Primitives/String/null only, declared type genannt) |
| `conditionalBreakpointWithHitCount` | `// UC-JD-5` | Fixture suspended. `setBreakpoint(BP-Zeile, condition="counter == 5", hitCount=3)` → Response echo; `resume()` → Session suspendet **genau** wenn counter==5 (Variablen-Check); vorherige Hits ohne Suspend (via hitCount/Condition belegt: counter==5 im Suspend-Zustand) |
| `exceptionBreakpointSuspendsOnType` | `// UC-JD-5` | Fixture mit Program-Arg `"throw"`. `setExceptionBreakpoint("java.lang.IllegalArgumentException")` → `resume()` → Suspend mit dieser Exception (Stack/State-Check) |
| `stepAndControlActions` | `// UC-JD-6` | Fixture suspended. `stepOver()` → topFrame.line ändert sich (nächste Zeile), suspended; `stepIn()` in `tick` → topFrame.method == `tick`; `stepOut()` → zurück in `main`; `evaluateExpression("counter + 1")` → JSON-Value == counter+1; `suspend()` → suspended; `resume()` → läuft weiter (nächster Suspend am BP oder ehrliche Running-Meldung) |
| `userTerminatesSessionBetweenCalls` | `// UC-JD-2` | Fixture gestartet + `getState()` ok → `launch.terminate()` + Warten → `getState()` → „no active debug session" (kein Auto-Reconnect, keine neue Launch) |

Zusätzlich I1 (ohne UC-Kommentar): `JavaDebugTool`-Registrierung + `isEditTool()==true` + Filter-Matrix in `SharedToolsComponentTest` (Vorbild `webGetFilteredFromSearchAgent`, Zeile 174).

## 8. Evidenz-Liste (Memory #28 — je Inkrement gegen IST verifizieren, nicht glauben)

Je Inkrement liefert Da Mek: (1) Suite-Zahlen (Tests/Errors/Failures), (2) je UC: grüner Testname, (3) je Deliverable: file:line-Beleg (z. B. `JavaDebugTool#isEditTool` Zeile X `true`; MANIFEST `Require-Bundle` enthält `org.eclipse.jdt.debug`; `SharedToolsComponent` Zeile Y `addTool(new JavaDebugTool())`; I4: D6-Probe-Ergebnis mit `javap`-Output-Zitat), (4) lint-Report (JD: 0 Befunde), (5) Commit-Hash. PO/Da-Dok verifizieren, bevor ein Inkrement „DONE" heißt.

## 9. Docs — PO-only (Da Mek nennt, PO zieht nach)

**Im Inkrement (Da Mek, da tool-technisch):** `docs/tool-descriptions-inventory.md` — 13 Zeilen (`get_state`…`suspend`), je mit finaler Beschreibung + `isEditTool=✔`.
**Post-Review (PO, erst nach bestandenem Review — NICHT im Inkrement, Memory #10):**
1. `docs/java-debugger-tool.md`: R-JD-1…5 + UC-JD-1…6 → `✅ done` + Status-Zeile → `✅ done (2026-…, commit)`.
2. `docs/index.md:72` → `✅`.
3. Commits (alle Inkremente) enthalten die committ-fähigen `docs/java-debugger-tool.md` + `docs/memory.md` (Pauls Regel 7).
**ADR-Motive → PO-ToDo (Paul Regel 8):** (a) **JDT-Debug-Model statt DAP** (R-JD-4-Entscheidung + Begründung User-Property/keine Confirmations); (b) **Eclipse-2026-07 API-Drift** im JDT-Debug-Model (`model`-Paket weg, `IJavaStackFrame`-Rename, kein `IJavaProcess`, öffentliches Eval-API fehlt → internal AST-Engine) — beides gehört in ADRs, nicht in dieses Inkrement.

## 10. Offene Fragen

**None.** Alle Decisions sind oben fixiert; alle Ist-Risiken haben STOP-AND-ASK-Guardrails statt stiller Workarounds.

## 11. IST-Abweichungen — I2-Aufklärung (Da Mek, 2026-09-20, STOP-AND-ASK aktiviert)

**Generations-Korrektur:** Target ist Eclipse **2026-09** (`releng/llmpeon-target/llmpeon.target` → releases/2026-09), nicht 2026-07. Autoritative Versionen: debug.core **3.24.0.v20260714**, jdt.debug 3.26.100.v20260714 (wie im Plan), jdt.launching 3.24.100.v20260218, jdt.debug.ui 3.15.500.v20260722. Alle Fakten unten per `javap` gegen `~/.m2/repository/p2/osgi/bundle/` verifiziert (jdt.debug = Wrapper-Jar, Klassen in `jdimodel.jar`). I1s 5 Drift-Fakten gelten unverändert (Code kompiliert + Suite grün gegen 3.24).

**A. Launch/Fixture (§6.2) — Mechanik umgebaut, SOLL unverändert:**
1. `IWorkspaceLaunchConfiguration` **weg** → `ILaunchConfigurationType.newInstance(IContainer, name)` → `ILaunchConfigurationWorkingCopy`; Start via `ILaunchConfiguration.launch(ILaunchManager.DEBUG_MODE, monitor)` → liefert `ILaunch` direkt (synchron, mit Monitor).
2. `DEBUG_MODE`-Konstante jetzt auf `ILaunchManager` (`IDebugLaunchConfiguration` weg).
3. `ILaunchConfigurationTypeManager` weg → `DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurationType("org.eclipse.jdt.launching.java")`.
4. `ATTR_DEFAULT_VM_ARGUMENTS` weg → `ATTR_VM_ARGUMENTS`.
5. `VM_ATTR_ID` weg → neues VM-Install-Modell `ATTR_VM_INSTALL_NAME`/`ATTR_VM_INSTALL_TYPE` (Plan-Option (b) "VM_ATTR_ID explizit" damit obsolet).
6. **Java-Launch-Config-Typ-ID umbenannt:** `org.eclipse.jdt.launching.java` **existiert nicht mehr** → `org.eclipse.jdt.launching.localJavaApplication` (plugin.xml jdt.launching 3.24.100; Delegate jetzt `org.eclipse.jdt.launching.sourcelookup.advanced.AdvancedJavaLaunchDelegate`, Migration-Delegate für alte Configs vorhanden). Runtime-Proof: `getLaunchConfigurationType("…java")` lieferte im PDE-JUnit-Workbench null, `…localJavaApplication` ist der einzige Java-App-Typ.

**B. Breakpoints (Fixture §6.3 + D8) — `IBreakpointUtils` UND `WorkspaceResourceManager` existieren in dieser Generation nicht (mehr):**
6. **Neue öffentliche Factory `org.eclipse.jdt.debug.core.JDIDebugModel`** (javap 3.26.100 verifiziert; JDT-UI 3.15.500 `ToggleBreakpointAdapter` ruft genau diese an — Bytecode-Beleg, d. h. aktueller UI-Weg):
   - `createLineBreakpoint(IResource, String stratum, int line, int charStart, int charEnd, int suspendPolicy, boolean suspendVM, Map<String,Object> attrs) → IJavaLineBreakpoint`
   - `createExceptionBreakpoint(IResource, String typeName, boolean, boolean, boolean, boolean, Map) → IJavaExceptionBreakpoint` (Booles-Reihenfolge = Runtime-Proof via I3-Test)
   - `lineBreakpointExists(IResource, stratum, line) → IJavaLineBreakpoint`
   - Stratum für Java = `"java"`.
7. `IBreakpointManager` umgezogen: `org.eclipse.debug.core.IBreakpointManager` (via `DebugPlugin.getBreakpointManager()`); `IBreakpoint` bleibt in `…core.model`.
8. `BreakpointManager` (intern) reagiert selbst auf Marker-Änderungen (IResourceDelta ADDED → `handleAddBreakpoint` → register/install) — kein manueller Install-Call nötig. `installed`-Flag weiter via `IJavaBreakpoint.isInstalled()`.

**C. Werte-Modell (I2-JSON, D5/D7) — Umbau:**
9. `IVariable.getValue()` liefert jetzt `IValue` (vorher `Object`); `IValue.getValue()` (Object) **weg** → String via `IValue.getValueString()`, Null via `IJavaValue.isNull()`, Kinder via `IValue.getVariables()`/`hasVariables()`.
10. `IJavaPrimitiveValue` mit Typ-gettern: `getIntValue()`, `getLongValue()`, `getBooleanValue()`, `getCharValue()`, `getFloatValue()`, `getDoubleValue()`, `getByteValue()`, `getShortValue()`. **`IJavaString` existiert nicht** → Strings über `getValueString()`.
11. `getVisibleChildren()` existiert in debug.core 3.22/3.24 **nicht** (Plan-§3-Fakt war auf älterer Generation — korrigiert): Kinder = `IValue.getVariables()`. Arrays: `IJavaArray.getLength()`/`getValue(int)` (oder `IIndexedValue.getSize()`).
12. `IThread.getTopStackFrame()` vorhanden (topFrame-Short-Cut). `IJavaBreakpoint.setSuspendPolicy(int)` mit `SUSPEND_VM`/`SUSPEND_THREAD`-Konstanten (javap ok).

**Status: STOP-AND-ASK bei Jon gestellt** — Ersetzungsmechanik (A–C) ist öffentlich, javap-belegt, SOLL-neutral; kein Code in den betroffenen Pfaden, bis Freigabe liegt. I2-Read-Actions (get_state/get_stack_trace/get_variables) sind davon nicht betroffen, außer der Fixture-Breakpoint (B).

---

## 12. Review Story C — Da Dok (2026-09-20, frischer Kontext)

**VERDICT: CONCERNS** — kein Blocking-Gap. SOLL == IST für R-JD-1…5; UC-JD-2…6 sind nur manuell verifiziert (Paul-Smoke **pending**) — bewusste PO-Abweichung (flaky JDT-Auto-Resume-Race, §6 hinfällig), nicht als Mangel gewertet. Lint: 5× UNBELEGT UC-JD-2…6 = erwarteter Stand; UC-JD-1 belegt (`JavaDebugToolTest.noSessionFailsHonest`).

**Evidenz (selbst gelesen/verifiziert, 2026-09-20):**
- Deliverables komplett: `JavaDebugTool.java` (13 @Tool-Methoden, exakte D3-Namen, `isEditTool` :52), `DebugSession.java`, `DebugJson.java` (D5-Shapes exakt; 20-Element-Limit im Output benannt :304; depth-Cap 5 :91), `SharedToolsComponent.java:69`, `MANIFEST.MF:29` (`org.eclipse.jdt.debug`), `DebugJsonUnitTest` (4 Tests), `JavaDebugToolTest.noSessionFailsHonest` (alle 13 Actions + `launchCount()==0` vor/nach → kein Auto-Start), `SharedToolsComponentTest:183` (Filter-Matrix), `tool-descriptions-inventory.md:184-202` (13 Zeilen).
- **Compile-Verifiziert:** `eclipseBuildProject` `org.sterl.llmpeon` (nur Bestands-Warnings) und `org.sterl.llmpeon.test` (0 Fehler/0 Warnings). OSGi-Suite nicht neu gestartet (Trust-Dialog, Memory #13); Commit-Hashes nicht unabhängig prüfbar (kein Git-Zugriff).
- **API gegen 2026-09-Quelltext verifiziert (nicht geglaubt):**
  - `JDIDebugModel.createLineBreakpoint(resource, typeName, line, charStart, charEnd, hitCount, register, attrs)` — Code korrekt (`JavaDebugTool.java:222`). **Plan-§11-A6-Drift:** Plan nannte 2. Parameter „stratum" und Slots „suspendPolicy/suspendVM" — ist `typeName`/`hitCount`/`register`. Code stimmt mit der echten API überein, der Plan war ungenau. (Analog `lineBreakpointExists`.)
  - `createExceptionBreakpoint(resource, name, caught, uncaught, checked, register, attrs)` — Code `JavaDebugTool.java:261` überträgt `(caught, uncaught, false, true)` in javadoc-korrekter Reihenfolge. Booles-Reihenfolge nur javadoc-geprüft (I3-Runtime-Proof fehlt mit dem Fixture-Stop) → Paul-Smoke muss sie decken.
  - **D6-Probe-Outcome bestätigt:** `EvaluationManager.newAstEvaluationEngine(IJavaProject, IJavaDebugTarget)` + `IEvaluationEngine.evaluate(String, IJavaStackFrame, IEvaluationListener, int, boolean)` sind **öffentlich** (Source gelesen) → D6-Schritt 1, kein internes API nötig. `engine.dispose()` im finally (`JavaDebugTool.java:136`) — javadoc-Pflicht, eingehalten.
- **R-JD-Abgleich (Docs↔Code):** R-JD-1 ehrlicher no-Session-Fehler auf alle 13 Actions, kein Auto-Start (Test), kein Auto-Disconnect (kein `terminate`/`disconnect` auf Session in Tool-Code — nur `thread.terminateEvaluation()` beim Eval-Timeout); R-JD-3 pretty/synchron/stateless (einziger statischer State = geteilter `ObjectMapper`); R-JD-4 JDT/keine Confirmations; R-JD-5 `isEditTool` + Matrix-Test. `set_variable`-Gate (D7) exakt: Primitiven + String, null nur bei String-Deklaration, deklarierte Typen im Fehler genannt (`JavaDebugTool.java:412-442`). Control-Actions: Poll 100 ms + ehrliche Edges (still suspended / VM terminated / still running, `JavaDebugTool.java:538-557`).

**Abweichungen (klassifiziert, alle freigegeben/legitim):**
1. Live-Session-Tests gestoppt (PO) → UC-JD-2…6 manuell. **Legitim (Paul-freigegeben)** — ist aber Plan-Stale, s. u.
2. `continue` = thread-scope (`JavaDebugTool.java:373`) statt VM-scope. **Legitim (PO-Entscheid)**; konsistent mit D3 (`thread`-Parameter) und R-JD-2 (User bleibt Herr der Session).
3. `subTypes=false` ehrlich abgelehnt (`JavaDebugTool.java:256-258`). **Legitim** — `createExceptionBreakpoint` hat gar keinen Subtype-Flag; JDI matcht immer Subtypes. Message lügt nicht.
4. no-session = `onProblem` + ehrliche **Rückgabe** statt IAE-Throw (D10-Satz „alle werfen IAE" vs. §7 „alle enthalten" — Plan selbstaufällig widersprüchlich). Code folgt §7 + House-Konvention (`EclipseBuildTool:47`, `EclipseWorkspaceReadFileTool:56` machen exakt dasselbe). **Legitime Verbesserung**; D10 im Plan als „Fehler *bei Ausführung*" lesen.
5. `set_variable`: strikter Parse pro deklariertem Typ (`parseBounded`) statt Plan-„widening" int→long. **Legitime Verbesserung** (Gate kennt den Typ, Widening wäre falsch).
6. `remove_breakpoint`: workspace-weiter Marker-Scan (`JavaDebugTool.java:483-492`, `Markers.findMarkerById` weg). **Legitim**, ehrlich; Perf-Edge bei riesigen Workspaces, unkritisch.

**CONCERNS (nicht blockierend, für späteres Delta-Plan/PO):**
1. **Duplikation (Rule of Three):** `threadName(IJavaThread)` in `JavaDebugTool.java:568` + `DebugSession.java:175` + `DebugJson.java:467`; `fail(context, DebugException)` in `JavaDebugTool.java:524` + `DebugSession.java:191` + `DebugJson.java:499`; `isSystem` in `DebugSession.java:167` + `DebugJson.java:483`. Extraktion vorschlagen: ein package-private Helper (oder Names/fail in `DebugSession` konsolidieren).
2. **`evaluate_expression` NPE-Edge:** `result.get()` (`JavaDebugTool.java:127`) null → NPE bei `:128` statt ehrlichem Fehler. Engine-Vertrag macht null praktisch unmöglich; eine Null-Guard-Zeile wäre die ehrliche Variante. Rand-Edge: Timeout-Pfad re-ruft `resolveThread(thread)` (`:120`) auf — mit Default-Thread und zwei suspended Threads könnte das eine andere Thread auflösen.
3. **Manuelle-Smoke-Abhängigkeit:** die gesamte mutierende Hälfte (set_variable, Breakpoints, Steps, Eval) hat **keine** automatisierte Abdeckung; Exception-BP-Booles-Reihenfolge nur javadoc-belegt. **Paul-Smoke (pending) ist Vorbedingung für den ❌→✅-Flip der Docs** — sonst flippen wir auf Basis von Compile-Proof.
4. Nit: `remove_breakpoint` gateet auf aktive Session, obwohl Marker-Lösung workspace-level ist — Plan so definiert (13er no-session-Matrix), nur notiert.

**Plan-coverage-gap (Dok↔Plan — NICHT Rework gegen Da Mek):**
- Der Plan ist **stale gegenüber dem finalen IST**: Die PO-Entscheidungen während des Baus (Fixture-Stop → Smoke, Minimal-Ausprägung, `continue` thread-scope, `subTypes=false`-Ablehnung) stehen nirgends im Plan — §5 (I2–I5), §6 (Fixture) und §7 (7 Live-Tests) versprechen weiterhin automatisierte Session-Tests, die es bewusst nicht gibt. Ohne Eintrag wird der nächste Zyklus die 5× UNBELEGT als Mangel lesen. → Da Thinka/PO: Plan um eine „§12.1 PO-Korrektur 2026-09-20" ergänzen (Entscheid + Begründung + verbleibende Smoke-Vorbedingung).
- §11-A6/B-Fakten teils ungenau gegenüber der echten 2026-09-API (oben zitiert) — Code war korrekt; nur Dokumentations-Drift.
- Doc-Flips (R-JD-1…5, UC-JD-1…6, `index.md`) sind PO-Aufgabe **nach** diesem Verdict + Smoke-Abnahme — nicht als fehlend reportet (Paul-Anweisung).

**Mutation-Check-Empfehlung (an PO/Da Thinka):** DIE eine Stelle = `!target.isTerminated()` in `DebugSession.java:46` (R-JD-1/UC-JD-2-Kern, durchlaufen von allen 13 Actions). Mutation (Negation) → terminierte Session wird als aktiv gemeldet. Der einzige automatische Test (`noSessionFailsHonest`) würde **nicht rot**: er assertet `launchCount()==0`, d. h. der negierte Filter hat nichts zu filtern. Test-Nachweis: Session mit terminiertem Target (Stub/Stub-Launch) → `findActive()` muss null liefern. (Kandidat 2, `set_variable`-Default-Branch `JavaDebugTool.java:440`, ist einfachere Switch-Logik mit geringerem Impact.)

**Skill/Instruct-Gap:** keiner von mir gesehen — Da Meks API-Befunde sind bereits im Skill `eclipse-dpe` (`2c1f2de`) vermerkt.

## 12.1 PO-Korrektur (2026-09-20)

Plan ist an diesen Stellen **stale gegenüber dem finalen IST** — dies hier ist der gültige Stand (Protokoll, keine neuen Designs):

1. **Fixture-Stop → §6 hinfällig:** Die Debug-Session-Fixture im OSGi-Test ist hinfällig — flaky JDT-Auto-Resume-Race im PDE-Test-Workbench (`suspend=y` hardgecodet in `StandardVMDebugger`), Hard-Stop überschritten. **PO-Entscheid (Paul vorab freigegeben):** Minimal-Ausprägung — UC-JD-1 automatisiert (`noSessionFailsHonest`) + 4 `DebugJsonUnitTest`-Stub-Tests; **UC-JD-2…6 = manuelle Smoke-Verifikation durch Paul** (à la R-MCP3). Die Smoke ist **Vorbedingung für die Doc-Flips** (§9.1/§9.2).
2. **§7-Tests-Korrektur:** Von den 7 geplanten Live-Session-Tests existieren bewusst nur `noSessionFailsHonest` + `DebugJsonUnitTest`. Conditional-Breakpoint-Semantik korrigiert: `hitCount=3` feuert am 3. Hit, Condition wird danach ausgewertet → korrekte Test-Werte `condition="counter == 2"` (nicht „counter == 5“).
3. **D9-Abweichung:** `continue` resumed den aufgelösten **Thread** (`thread.resume()`), nicht die ganze VM — konsistent mit dem `thread`-Parameter (PO-Entscheid, R-JD-2-konform).
4. **D8-Zusatz:** `subTypes=false` wird ehrlich abgelehnt (JDI-Exception-BP matcht immer Typ + Subtypes). §11-A6-Korrektur: 2. Parameter von `JDIDebugModel.createLineBreakpoint` ist der **Typ-Name** (nicht „stratum = java“).
5. **D6-Ergebnis:** Probe fand **öffentliches** `EvaluationManager.newAstEvaluationEngine` + `IEvaluationEngine.evaluate` → D6-Schritt 1 erfüllt, Fallback (interne AST-Engine) **nicht** benötigt.
6. **Review-Nachinkrement (Da Mek, läuft):** Rule-of-Three-Extraktion (`threadName`/`fail`/`isSystem`), NPE-Guard in `evaluate_expression` (`result.get()`), Mutation-Nachweis für `!target.isTerminated()` (§12 Mutation-Check) via testbares `findActive(ILaunch[])` + Stub-Tests.

---

## DIAGNOSTIC-RUNDE 2026-09-21 (Stale-VM, issue.md) — Status: DONE (2026-09-21, Ergebnis + Fix s. Fixrunde unten)

**Auftrag (Paul):** Diagnose + Messwerte, KEIN Fix vor Freigabe.

**IST (verifiziert):**
- E2E-Session (DebugFix, pid 75791) ist GEGANGEN: Workbench wurde 08:08:57 neu gestartet (alte Konsole "plugin_debug_configuration" pid 75739 terminated). Live-Messung erfordert Re-Trigger.
- Mess-Instrument gebaut (Commit `15e50d0`): temporäres @Tool `diagnose_sessions` in `JavaDebugTool` (klar als TEMPORARY markiert, REMOVE after diagnosis). Dump je Launch: Name, terminated; je Target: Klasse, terminated, suspended, hasThreads, **pid (via IProcess.ATTR_PROCESS_ID)**; je IJavaDebugTarget: vmName, isOutOfSynch, Threads **beide Wege** (direkt `getThreads()` vs `getRootThreadGroups()`) mit suspended/system/topFrame. Output: Tool-Response + System.err + **Datei `/org.sterl.llmpeon.test/diagnosis-sessions.txt`** (von Da Mek per eclipseReadFile auslesbar).
- Bin/Klasse kompiliert (JavaDebugTool.class 08:25, diagnoseSessions vorhanden). **Lädt erst nach Workbench-Restart** (OSGi).
- **IST-Befund 1 (vorbestehend, nicht von mir):** `eclipseBuildProject(org.sterl.llmpeon)` meldet Failure — PDE-Warnung build.properties "class folder 'resources/' not associated to any output library entry". Java-Kompilation läuft (frische Klassen in bin/). Vorbestehend (build.properties letzter Commit d1c2191, Story #136) — separat melden.
- **IST-Befund 2 (API-Generation 2026-09/06):** `IJavaProcess`/`IJavaLaunch` etc. sind aus `org.eclipse.jdt.launching` 3.24.300 **entfernt** (nur noch IJavaLaunchConfigurationConstants + IVM*/IRuntime*); jdt.debug 3.26.100 ist jetzt Fragment-Layout (jdimodel.jar). Prozess-ID künftig nur über `IProcess.ATTR_PROCESS_ID` (debug.core ≥3.19).
- Code-Lesart (vor Messung): `vm.state` = `IJavaDebugTarget.isSuspended()` (DebugJson.java:471, Semantik IDebugTarget: suspended = ALLE Threads suspended). Initial-Suspend müsste also "suspended" ergeben — beobachtetes "running" + 5 System-Threads ohne main ⇒ adressiertes Target ist nicht im Initial-Suspend; Probe klärt, welches Target (pid!) es ist.

**Offene Fragen an Paul (s. Chat-Nachricht):** Restart + Re-Trigger + diagnose_sessions aufrufen.

**Danach:** Messwerte auswerten → Fix-Vorschlag (inkl. Stub-Test-Sicherung à la DebugSessionLookupTest + SOLL-Frage Session-Kennung im Response) → erst nach Freigabe fixen → `diagnose_sessions` entfernen.

---

## ISSUE2-FIXRUNDE 2026-09-21 (Befund 3 + 4, Paul-Anweisung) — DONE, wartet auf Post-Reinstall-Sanity

**Auftrag:** (1) Befund 3: `get_variables.frame` description=optional vs. Required-Fehler — Prüfung aller 13 Actions; (2) Befund 4: `diagnose_sessions` Crash — boring java.io + Klärung real vs. IDE-Artefakt. Gates (OSGi-Suite + Core-Surefire) erst NACH Pauls IDE-Neuinstall.

**Befund 3 — Root Cause (in langchain4j-1.20.0-Source verifiziert, nicht geraten):**
- `ToolSpecifications.parametersFrom` respektiert `required=false` korrekt → Schema war schon ok.
- Der Fehler kommt aus `DefaultToolExecutor` (langchain4j 1.20.0, `langchain4j/service/tool/DefaultToolExecutor.java:~360`): bei weggelassenem Argument wirft der Executor für **primitive Parameter** ohne `@P(defaultValue)` hart `Required parameter "X" of tool "Y" is missing` — `required=false` wirkt nur schema-seitig, nicht executor-seitig.
- **Fix (House-Konvention, wie `EclipseWorkspaceReadFileTool`/`EclipseConsoleLogTool`/`EclipseRunTestTool`):** optionale primitive `int`-Parameter → `Integer` (weggelassen = null = 0 = Default). Betroffen: `frame` ×3 (get_variables/evaluate_expression/set_variable), `depth`, `timeoutMs`, `hitCount`, `waitMs` ×4. `line` (set_breakpoint) bleibt `int` — genuinely required. Null-Normalisierung an den Call-Sites (`javaFrame`-Helper, `timeout`/`hits`/`waitMs`-Defaults).
- **Audit aller 13 Actions:** alle optional deklarierten Parameter sind jetzt String/Boolean/Integer + `required=false`; alle Required (`file`, `line`, `expression`, `name`, `value`, `exceptionType`, `id`) haben keine Optional-Hints in der Description. Keine weiteren Lügen.
- **NEU GEFUNDEN → BEHOBEN (PO-Entscheidung (a), Commit `4d671e9`):** `set_exception_breakpoint.catchCaught` — Description „empty = false", Code defaultete auf **true** → dokumentiertes Default (nur uncaught) per Omission unerreichbar. Fix: `boolean caught = catchCaught != null && catchCaught;` (`JavaDebugTool.java:352`) — deckungsgleich mit Plan D3 + Beschreibung; damit ist auch der `!uncaught && !caught`-Guard wieder erreichbar (explizit false+false).

**Befund 4 — Klärung (b):** Compile-Fehler ist **REAL, kein IDE-Artefakt** — in meiner Workbench exakt reproduziert (3 Errors, gleiche Meldungen). Ursache: `java.nio.channels.Channels.newFileChannel(Path, OpenOption…)` **existiert in keinem JDK** — die Methode ist `java.nio.file.Files.newFileChannel` (API-Vertuschung bei Authoring). `readTypeSource` auf `java.nio.channels.Channels` bestätigt: Klasse endet bei `newWriter` (komplette moderne Signatur, kein `newFileChannel`). Pauls „kaputte IDE" hat nur den Stub gebaut, statt zur Compile-Zeit zu brechen (JDT-Standardverhalten).
**Fix (a):** Diagnose-Datei-Write auf `java.nio.file.Files.writeString(Path, String)` (UTF-8 Default) — ein Block statt try-with-resources, keine Channels/StandardOpenOption mehr.

**Gates (vor Reinstall, bewusst reduziert):** `eclipseBuildProject` `org.sterl.llmpeon` grün (nur Bestands-Warnings aus AGENTS-DEV „known-benign"-Liste; die 3 JavaDebugTool-Errors sind weg) + `org.sterl.llmpeon.test` 0 Fehler/0 Warnings. OSGi-Suite + Core-Surefire = Post-Reinstall-Sanity (Paul-Anweisung). `diagnose_sessions` bleibt bis zur Stale-VM-Diagnose (Befund 1/2) — jetzt kompiliert.

**POST-REINSTALL-SANITY (2026-09-21, frische IDE 2026-06 + Target 2026-09, test_project importiert) — ALLE GRÜN:**
1. `eclipseBuildProject`: `org.sterl.llmpeon` grün (nur die 9 known-benign Warnings), `org.sterl.llmpeon.test` 0 Fehler/0 Warnings.
2. OSGi-Suite `org.sterl.llmpeon.test`: **245 Tests / 0 Failures / 0 Skipped** (erste Runde in frischer IDE, Trust-Dialog kein Problem).
3. Core-Surefire (`mvn -o -pl org.sterl.llmpeon.core clean test`): **897 Tests / 0 Failures / 0 Errors / 0 Skipped, BUILD SUCCESS** — core unangetastet, Regression-Beleg.
Keine Code-Änderung nötig. Nächstes: Diagnose-Lauf Befund 1/2 (Paul startet Session an Zeile 11, `diagnose_sessions` aufrufen, `diagnosis-sessions.txt` lesen).

**Commit:** (Hash unten) — nur `JavaDebugTool.java` geändert, keine Docs (keine Description-Änderungen).


---

## STALE-VM-FIXRUNDE 2026-09-21 (issue2 Befund 1/2, Paul-Anweisung) — DONE, wartet auf Pauls E2E-Smoke

**Diagnose-Ergebnis (`/org.sterl.llmpeon.test/diagnose.txt`):** KEINE stale VM — das adressierte Target IST die UI-Session (main suspended, topFrame main:11 via `getThreads()`). Root Cause = Thread-Enumerierung über `getRootThreadGroups()`: dort nur 5 System-Threads, **main fehlt** (JDI: main hängt am Breakpoint in keiner Root-Group) → Tool sah "running" + keine nutzbare Thread.

**Fixes (PO-freigegeben; SOLL-Präzisierung, keine SOLL-Änderung):**
1. Thread-Enumerierung + Auflösung konsequent über `target.getThreads()` — einziger Weg, `getRootThreadGroups()` aus dem Produktions-Code entfernt (`DebugSession.threads()` :123-137, Javadoc dokumentiert den Grund).
2. `DebugJson.state()` :57-77: `vm.state` = "suspended" wenn `target.isSuspended()` **ODER** ≥1 non-system Thread suspended, sonst "running"; neu: `vm.suspendedThreads` (int, zählt suspended non-system Threads — mixed State ehrlich benannt).
3. Diagnose-Datei-Write: `getRawLocation()` + Workspace-Root-Fallback statt `getLocation()` (NPE-Beweis diagnose.txt:20) — `JavaDebugTool.diagnoseSessions()` :102-105.
4. `get_state` + `diagnose_sessions` tragen `session` = Launch-Config-Name + pid. **API-Fakt 2026-09 (verifiziert, Source gelesen):** `JDIDebugTarget` implementiert `IProcess` **nicht** (private `fProcess` — `instanceof IProcess` auf dem Target ist immer false); pid nur via `target.getProcess().getAttribute(IProcess.ATTR_PROCESS_ID)`; `IProcess.getAttribute` liefert **String** (parse nötig). Umsetzung: `DebugSession.processId()` :139-158, `DebugJson.sessionLabel()` :80-83.

**Stub-Tests (neu: `DebugSessionThreadsTest`, 3 Tests, DebugJsonUnitTest-Proxy-Muster):** reproduzieren exakt den diagnose.txt-Zustand — getThreads: main suspended + 5 System-Threads; getRootThreadGroups: nur System-Threads (Fallen-Set für Group-basierte Implementierungen); target.isSuspended()=false; process als separates Objekt via `getProcess()` mit String-Attribut "78703":
- `stateShowsMainThreadAndSuspendedVm` — main sichtbar (topFrame main/peontest.DebugFix:11), `vm.state=suspended` trotz target-isSuspended=false, `suspendedThreads=1`, `session="DebugFix (pid 78703)"`.
- `stateIsRunningWhenNothingIsSuspended` — `vm.state=running`, `suspendedThreads=0`.
- `defaultThreadResolutionPicksMainThread` — Default-Auflösung wählt main (alter Group-Pfad hätte "no usable thread" geworfen).

**Gates (2026-09-21):** `eclipseBuildProject` `org.sterl.llmpeon` grün (nur known-benign Warnings) + `org.sterl.llmpeon.test` 0 Fehler/0 Warnings; OSGi-Suite **248 Tests / 0 Failures / 0 Skipped** (245 + 3 neu). Commit: `cd85bf2`.

**Offen:** Pauls voller E2E-Smoke (Phase 1–5) → danach `diagnose_sessions`-TEMP-Tool entfernen + PO-Doc-Flips (R-JD-1…5, UC-JD-1…6, `index.md:72`).
