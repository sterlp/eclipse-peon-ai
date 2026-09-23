# ADR-0054 — Tool-Naming: camelCase mit Familien-Prefix

> **Status:** Accepted (2026-09-23, Paul)

## Context

Die Java-Debug-Tools waren die einzige snake_case-Familie (`get_state`, `set_breakpoint`, …) und
trugen kein Präfix — alle anderen Tool-Familien folgen de-facto **camelCase + Familien-Prefix**
(`eclipseReadFile`, `diskGrepFiles`, `webGet`, `memoryAdd`, `planRead`, `skillList`,
`shellRunCommand`). Paul kritisierte am 2026-09-23 die Inkonsistenz („Tool-Namen folgen nicht
unserer Konvention"). Zudem dokumentierte `docs/tool-descriptions-inventory.md` eine nie gebaute
„`workspace-`-Prefix"-Konvention (veraltet, korrigiert).

## Decision

Tool-Namen = **camelCase mit Familien-/Klassen-Prefix**, als Systemkonvention.

Java-Debug-Familie: `debugJava*` — `debugJavaGetState`, `debugJavaGetVariables`,
`debugJavaGetStackTrace`, `debugJavaGetException`, `debugJavaEvaluateExpression`,
`debugJavaSetVariable`, `debugJavaSetBreakpoint`, `debugJavaSetExceptionBreakpoint`,
`debugJavaRemoveBreakpoint`, `debugJavaListBreakpoints`, `debugJavaStepOver`, `debugJavaStepIn`,
`debugJavaStepOut`, `debugJavaContinue`, `debugJavaSuspend`. Prefix `debug` + `Java` (JDT =
Java-only; zukunftssicher für z. B. `debugPython*`). Allowlist-Präfix für Custom Agents: `debugJava`.

## Consequences

- **Clean Break:** alte snake_case-Namen werden nicht migriert und nicht gealiast (AGENTS:
  „Clean break over migration") — Feature ist 2 Tage alt, externe Allowlisten existieren noch nicht.
- `continue` ist Java-Keyword — der Prefix löst auch das.
- Umgestellt im selben Inkrement: `@Tool`-Namen, Tests, `docs/java-debugger-tool.md`,
  E2E-Doc, Prompts (falls referenzierend), `tool-descriptions-inventory.md`, Homepage-Tabellen.