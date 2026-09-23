# ADR-0051: Debugger — automatisierte Live-Session-Tests gestoppt, Smoke statt Flaky

> **Status:** ✅ accepted (2026-09-20/21, Paul) · **Fachdoc:** [java-debugger-tool.md](../java-debugger-tool.md)

## Context

Der Plan (§6) sah eine OSGi-Fixture vor, die im PDE-Test-Workbench eine echte Debug-Session launcht
(suspend=y ist in `StandardVMDebugger` hardgecodet; der Release des Initial-Suspend muss über
`resume()` laufen). Der In-Workbench-Launch an sich wurde bewiesen working — aber die Release-Sequenz
ist ein **Race gegen JDTs eigenen VMStart-Dispatch** (`handleVMStart`), der im PDE-Test-Workbench
unzuverlässig feuert: Iteration 3 grün, Iteration 4 rot. Flaky, nicht deterministisch.

## Decision (Paul vorab freigegeben: „notfalls minimal"; PO-Vollzug 2026-09-20/21)

1. **Fixture-Pfad stoppen** — Hard-Stop war überschritten, jede Iteration ~1 h, und ein flaky Test
   ist schlimmer als kein Test (vergiftet jede zukünftige Suite).
2. **Minimal-Ausprägung:** automatisiert nur UC-JD-1 (noSession über alle 13 Actions) +
   Stub-Tests (`DebugSessionLookupTest`, `DebugSessionThreadsTest`, `DebugPrimaryTypeTest`,
   `DebugJsonUnitTest`) — Proxy-Stubs über `ILaunch[]`/`IJavaDebugTarget`, exakt die diagnose.txt-
   Zustände reproduzierend. **UC-JD-2…6 = manuelle Smoke-Verifikation durch Paul** (à la R-MCP3),
   durchgeführt 2026-09-21 mit `ai-e2e-test/java-debugger-e2e-test.md`.
3. **Kein Test-Weichspülen:** die Stub-Tests fingen echte Bugs (getThreads-vs-getRootThreadGroups,
   toter-VM-Filter, CU-Auflösung, Numeric-Value-Contract) — Mutation-Nachweis für den Session-Gate
   liegt vor (3 rote Pfade bei Gate-Invertierung).

## Consequences

- Die Lint-UNBELEGT-Befunde für UC-JD-2…6 sind **erwarteter Bestand**, kein Mangel — manuelle
  Verifikation ist hier die vereinbarte Beweisform.
- `diagnose_sessions` (TEMP-Diagnose-Tool, `15e50d0`) wurde nach dem Fund entfernt (`2efaaf6`).
- Wer die Fixture später deterministisch machen will (z. B. suspend=n per explizitem VM-Argument,
  VM-Install-Modell `ATTR_VM_INSTALL_NAME/TYPE` statt obsoletem `VM_ATTR_ID`), startet bei
  ADR-0049s Fakten — nicht beim Plan-§6-Stand.
