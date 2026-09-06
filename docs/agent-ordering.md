# Agent Ordering — `agent-order.txt`

**Status: ✅ done (2026-09-06, Feature #128, gebaut vom User, merged mit `18525db`).**
Eine Java-Regex pro Zeile steuert die Reihenfolge **aller** Agenten (Custom + Built-ins) im
Chat-Dropdown. Implementierung: `agentorder.AgentOrder` (core), geladen in
`AgentService.reloadAgents()`, angewandt in `AgentService.getAgents()`.
Nutzung dokumentiert für End-User in [Homepage „Ordering the agent list in UI"](../../homepage/src/setup/custom-agents.md) und README; der Scaffold-Agent bietet Order-Updates an
([scaffold-agent.txt](../llmpeon-core/src/main/resources/org/sterl/llmpeon/prompts/scaffold-agent.txt)).

**WEIL:** Die Dropdown-Reihenfolge ist Präsentation, keine Korrektheit — Peon-PO zuerst,
spezifische Agenten gruppiert, ohne dass der User Code anfassen muss. Eine Datei im
Config-Dir ist die schlankste Form: config-only (ADR-0041-konform), versionierbar,
vom Scaffold-Agent verwaltbar.

## R1 — Datei & Auto-Create

Pfad: `<configDir>/agents/agent-order.txt` — **in** den Agenten-Verzeichnissen
(`AgentService.reloadAgents()` lädt sie aus `agentsDirectory`).

- GIVEN kein agent-order.txt im agents-Verzeichnis, WHEN Agenten laden, THEN wird die Datei
  mit Default-Inhalt `^Peon-PO$` erzeugt und das Laden läuft weiter
  → `AgentOrderTest.loadCreatesDefaultFileWhenAbsent`, `AgentServiceTest.defaultFileCreated`
- GIVEN vorhandene agent-order.txt, WHEN Agenten laden, THEN wird sie **nicht** überschrieben
  → `AgentOrderTest.loadDoesNotOverwriteAnExistingFile`
- GIVEN nur Kommentare (`#`) und Leerzeilen, WHEN geparst, THEN gelten die Default-Patterns
  (`^Peon-PO$`)
  → `AgentOrderTest.sortFallsBackToDefaultPatternsWhenOrderFileHasOnlyComments`

## R2 — Pattern-Matching & Gruppierung

Jede Zeile = Java-Regex, geprüft mit **Full-Match** (`Pattern.matcher(name).matches()`) —
`^`/`$` sind dadurch redundant; `Peon` matcht **nicht** `Peon-Dev`, Prefixe brauchen `Peon.*`.
Top-down: erster Match bestimmt die Gruppe; innerhalb einer Gruppe alphabetisch; ein Agent
erscheint nur einmal (Gruppe des ersten Matches). Reihenfolge gilt für die Vereinigung aus
Custom- und Built-in-Agenten.

- GIVEN Patterns `^Peon-PO$` dann `Peon.*`, WHEN sortiert, THEN Peon-PO vor allen anderen
  Peon-Agenten, Rest alphabetisch → `AgentOrderTest.sortGroupsByPatternThenAlphabeticalWithinGroup`
- GIVEN ein Agent matcht mehrere Patterns, WHEN sortiert, THEN er erscheint nur einmal
  (Gruppe des ersten Matches) → `AgentOrderTest.sortDoesNotDuplicateAnAgentMatchedByMultiplePatterns`
- GIVEN `.*` als erste Zeile, WHEN sortiert, THEN schluckt sie alle Agenten — spätere
  Patterns sind wirkungslos („spezifische Patterns zuerst")
  → `AgentOrderTest.sortIsAlphabeticalWhenPatternsMatchNothing` (Gegenprobe)
- GIVEN Built-in `^Peon-Dev$` vor Custom-Agent-Patterns, WHEN sortiert, THEN Built-ins
  erscheinen an patternbestimmter Position, nicht als Block
  → `AgentServiceTest.getAgentsWithOrdering`

## R3 — Unmatched & Invalid

- GIVEN Agenten, die kein Pattern matchen, WHEN sortiert, THEN sie werden alphabetisch
  **ans Ende** angehängt → `AgentOrderTest.sortAppendsUnmatchedAgentsAlphabeticallyAtTheEnd`
- GIVEN eine Zeile ist kein gültiger Regex, WHEN geparst, THEN Zeile übersprungen mit
  log.warn, Rest bleibt wirksam → `AgentOrderTest.sortSkipsInvalidRegexInTheOrderFile`

## Known Edge (Doku, kein Fix)

- Schlägt das Auto-Create fehl (read-only Config-Dir), wirft `AgentOrder.load()` und der
  ganze Agent-Reload bricht — **keine** Agenten laden. Ordering ist kosmetisch; statt zu
  töten wäre „weiter ohne Ordering" das robustere Verhalten. Kandidat für den Bug-Fix-Zyklus.
- `peek(seen::add)` = Side-Effect im Stream (funktioniert, aber unidiomatisch); Warn-Log
  wiederholt das Pattern zweimal. Kosmetik.
