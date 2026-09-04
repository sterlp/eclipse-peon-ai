# ADR-0035 — Grep: Regex first, Literal-Fallback (keine Eclipse SearchEngine)

**Status:** Accepted (2026-09-03)

## Context

`eclipseGrepFiles` rät heute per Zeichen-Heuristik (`RegexUtils.isRegexPattern`: enthält die
Query `* | + ^ $`?), ob eine Query als Regex oder literal gesucht wird. Die Heuristik liegt in
beide Richtungen falsch: `foo(bar` und `a.b` werden literal gesucht (obwohl Regex gemeint sein
kann), `C++` wird als Regex kompiliert und wirft. Agenten weichen deshalb regelmäßig auf
`diskGrepFiles` aus.

Diskutierte Alternative: die Eclipse-Suche (`org.eclipse.search` `SearchEngine` /
`FileSearchQuery`) benutzen statt eigener Iteration.

## Decision

1. **Regex first, Literal-Fallback statt Heuristik:** Query wird immer erst als
   `Pattern` kompiliert; nur bei `PatternSyntaxException` wird case-insensitive literal
   gesucht. Der gewählte Modus wird im Tool-Ergebnis benannt.
2. **Keine Eclipse-`SearchEngine`:** Die bestehende `IResourceVisitor`-Iteration in
   `EclipseGrepTool` bleibt. Die Search-API ist Query-/Result-Framework-lastig
   (`ISearchQuery`, `NewSearchUI`, Match-Collector), UI-gekoppelt und in OSGi-Tests schlecht
   headless zu fahren — für „zähle Treffer pro Datei" ist sie deutlich zu schwer.
3. Dieselbe Match-Semantik gilt für den neuen `grep`-Parameter von `eclipseReadConsoleLog`
   (zeilenweise angewandt).

## Nachtrag (2026-09-03) — „gültiges Regex" ist weiter gefasst als gedacht

Beim Bau zeigte sich: `C++` kompiliert **erfolgreich** (`C` mit possessivem Quantifizierer `++`)
und matcht jedes `C`. Der Literal-Fallback greift also nur bei echtem
`PatternSyntaxException` (`foo(bar`, `[invalid`) — nicht bei Queries, die bloß „nach Code
aussehen" (`C++`, `a.b`, `foo()`).

Das ist die gewollte Konsequenz der Entscheidung, **nicht** ein Loch darin: jede
„sieht-aus-wie-Code"-Sonderregel wäre die abgeschaffte Zeichen-Heuristik unter anderem Namen.
Der Preis ist Überraschung im Ergebnis — bezahlt wird er durch die **verschärfte R2c**: der
Modus (`regex search` / `literal search — …`) steht **immer** im Ergebnis, nicht nur beim
Fallback. Wer literal will, escaped (`\Q…\E`).

## Consequences

- Deterministisches, erklärbares Verhalten; die Query bestimmt den Modus, nicht ihre Zeichen.
- `RegexUtils.isRegexPattern` entfällt (bzw. wird nur noch intern zur Modus-Meldung genutzt).
- Kompilierung pro Query, nicht pro Datei — Pattern einmal bauen, dann wiederverwenden.
- Kein Eclipse-Search-Bundle in `MANIFEST.MF` nötig; keine UI-Kopplung.
- Regeln + BDD: [eclipse-read-tools.md](../eclipse-read-tools.md) R2/R3.
