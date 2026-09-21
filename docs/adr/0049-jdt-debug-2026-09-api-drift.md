# ADR-0049: JDT-Debug-API-Drift 2026-09 — Faktenbestand des Debugger-Tools

> **Status:** ✅ accepted (2026-09-21) · **Kontext:** Story C Java-Debugger-Tool ·
> **Fachdoc:** [java-debugger-tool.md](../java-debugger-tool.md) · **Target:** 2026-09
> (debug.core 3.24.0, jdt.debug 3.26.100, jdt.launching 3.24.300)

## Context

Der Eclipse-Target-Sprung (ADR-0044, 2026-09) hat das JDT-Debug-Model tief umbaut — mehr, als der
Plan §3 ursprünglich verifiziert hatte (die ersten Fakten stammten teils aus der 2026-07-Generation).
Der E2E-Smoke legte zwei weitere Fundamente-Fehler frei, die ohne echte Debug-Session unsichtbar
blieben (diagnose-Sensordaten, `/org.sterl.llmpeon.test/diagnose.txt`).

## Decision — verifizierte Fakten (javap/diagnose-belegt, NICHT raten)

| Fakt | Konsequenz für den Code |
|---|---|
| Paket `org.eclipse.jdt.debug.model` existiert **nicht mehr**; alle Model-Typen in `org.eclipse.jdt.debug.core` | Imports nach `core` |
| `IJavaProcess` (jdt.debug) und `IJavaProcess`/`IJavaLaunch` (jdt.launching) sind **weg** | pid nur über `target.getProcess().getAttribute(ATTR_PROCESS_ID)` — Attribut kommt als **String** zurück |
| `JDIDebugTarget` implementiert `IProcess` **nicht** — `instanceof IProcess` ist immer false | keine instanceof-Abkürzungen |
| **`getRootThreadGroups()` listet `main` nicht** (5 Threads statt 7; `main` suspended nur über `getThreads()` sichtbar) | Thread-Enumerierung/-Auflösung **ausschließlich** über `target.getThreads()` (R-JD-Befund diagnose.txt: 2026-09-21) |
| `IBreakpointUtils`/`WorkspaceResourceManager` existieren nicht mehr | Breakpoint-Erzeugung über öffentliche `JDIDebugModel.createLineBreakpoint/createExceptionBreakpoint` (2. Param = **Typ-Name**, nicht Stratum) |
| Kein Frame-Eval-Entry; `EvaluationManager.newAstEvaluationEngine(IJavaProject, IJavaDebugTarget)` + `IEvaluationEngine.evaluate` sind **öffentlich** | `evaluate_expression` über `EvaluationManager` (D6-Fallback internes AST-Engine nicht nötig) |
| `launch.getDebugTargets()` ohne Argument, `ILaunch` erbt nicht `IDebugElement` (kein `getName()`) | instanceof-Filter + `getLaunchConfiguration().getName()` |
| Optionale `int`-Parameter (`frame`, `depth`, `timeoutMs`, `hitCount`, `waitMs`) müssen Boxed + `required=false` sein — `DefaultToolExecutor` (langchain4j 1.20) verlangt primitive `@P`-Parameter hart, `required=false` wirkt nur schema-seitig | House-Muster der `eclipse*`-Tools: `Integer` + Null-Normalisierung |

## Consequences

- Neue JDT-Debug-Fakten werden hier nachgetragen, nicht in Features neu aufgemacht.
- Da Meks Befunde leben zusätzlich im Skill `eclipse-dpe` (Create, 2026-09-21) — dieses ADR ist
  die projektfeste Quelle, der Skill der Arbeitswissen-Transport.
- **Fallstrick, zweimal gelernt:** API-Contract immer im Source/Bytecode der **tatsächlich im Target
  aufgelösten** Generation verifizieren — Plan-§3-Befunde aus älteren Generationen (2026-07) gingen
  zweimal daneben (debug.core-Drift, getRootThreadGroups).
