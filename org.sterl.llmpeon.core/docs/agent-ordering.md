# Agent Ordering — `agent-order.txt`

**Status: ✅ done (2026-09-06, Feature #128, gebaut vom User, gemerged).**
Eine Java-Regex pro Zeile steuert die Reihenfolge **aller** Agenten (Custom + Built-ins) im
Chat-Dropdown. Implementierung: `agentorder.AgentOrder` (core), geladen in
`AgentService.reloadAgents()`, angewandt in `AgentService.getAgents()`.
Nutzung dokumentiert für End-User in [Homepage „Ordering the agent list in UI"](../../homepage/src/setup/custom-agents.md) und README; der Scaffold-Agent bietet Order-Updates an
([scaffold-agent.md](../src/main/resources/org/sterl/llmpeon/prompts/scaffold-agent.md)).

**WEIL:** Die Dropdown-Reihenfolge ist Präsentation, keine Korrektheit — Jon zuerst,
spezifische Agenten gruppiert, ohne dass der User Code anfassen muss. Eine Datei im
Config-Dir ist die schlankste Form: config-only (ADR-0041-konform), versionierbar,
vom Scaffold-Agent verwaltbar.

## R1 — Datei & Auto-Create

Pfad: `<configDir>/agents/agent-order.txt` — **in** den Agenten-Verzeichnissen
(`AgentService.reloadAgents()` lädt sie aus `agentsDirectory`).

- GIVEN kein agent-order.txt im agents-Verzeichnis, WHEN Agenten laden, THEN wird die Datei
  mit Default-Inhalt `^Jon$` erzeugt und das Laden läuft weiter
  → `AgentOrderTest.loadCreatesDefaultFileWhenAbsent`, `AgentServiceTest.defaultFileCreated`
- GIVEN vorhandene agent-order.txt, WHEN Agenten laden, THEN wird sie **nicht** überschrieben
  → `AgentOrderTest.loadDoesNotOverwriteAnExistingFile`
- GIVEN nur Kommentare (`#`) und Leerzeilen, WHEN geparst, THEN gelten die Default-Patterns
  (`^Jon$`)
  → `AgentOrderTest.sortFallsBackToDefaultPatternsWhenOrderFileHasOnlyComments`

## R2 — Pattern-Matching & Gruppierung

Jede Zeile = Java-Regex, geprüft mit **Full-Match** (`Pattern.matcher(name).matches()`) —
`^`/`$` sind dadurch redundant; `Dev` matcht **nicht** `Dev-2`, Prefixe brauchen `Dev.*`.
Top-down: erster Match bestimmt die Gruppe; innerhalb einer Gruppe alphabetisch; ein Agent
erscheint nur einmal (Gruppe des ersten Matches). Reihenfolge gilt für die Vereinigung aus
Custom- und Built-in-Agenten.

- GIVEN Patterns `^Jon$` dann `.*`, WHEN sortiert, THEN Jon vor allen anderen
  Agenten, Rest alphabetisch → `AgentOrderTest.sortGroupsByPatternThenAlphabeticalWithinGroup`
- GIVEN ein Agent matcht mehrere Patterns, WHEN sortiert, THEN er erscheint nur einmal
  (Gruppe des ersten Matches) → `AgentOrderTest.sortDoesNotDuplicateAnAgentMatchedByMultiplePatterns`
- GIVEN `.*` als erste Zeile, WHEN sortiert, THEN schluckt sie alle Agenten — spätere
  Patterns sind wirkungslos („spezifische Patterns zuerst")
  → `AgentOrderTest.sortWithCatchAllPatternKeepsAlphabeticalOrder` (Gegenprobe zu R5)
- GIVEN Built-in `^Dev$` vor Custom-Agent-Patterns, WHEN sortiert, THEN Built-ins
  erscheinen an patternbestimmter Position, nicht als Block
  → `AgentServiceTest.getAgentsWithOrdering`

## R3 — Unmatched & Invalid

- GIVEN Agenten, die kein Pattern matchen, WHEN sortiert, THEN sie werden alphabetisch
  **ans Ende** angehängt → `AgentOrderTest.sortAppendsUnmatchedAgentsAlphabeticallyAtTheEnd`
- GIVEN eine Zeile ist kein gültiger Regex, WHEN geparst, THEN Zeile übersprungen mit
  log.warn, Rest bleibt wirksam → `AgentOrderTest.sortSkipsInvalidRegexInTheOrderFile`

## R4 — Ein Agent, ein Dropdown-Eintrag + Warnung **✅ done (2026-09-12 — rot-first bewiesen, User-SOLL)

User 2026-09-12: „Jeder Agent darf nur einmal im Dropdown sein. Das Feature erlaubt **nur** die
Sortierung anzupassen — sonst nichts." Das seen-Set ist **namensbasiert** (das Dropdown ist
namensbasiert — zwei Instanzen gleichen Namens wären im UI ununterscheidbar). Dedup war schon immer
gewollt (R2); der Bug war ausschließlich die Stille (Triage #5). Beide Duplikat-Fälle bleiben
sichtbar geloggt.

- GIVEN ein Agent matcht mehrere Patterns (z. B. `^Jon$` und `.*`), WHEN sortiert, THEN
  erscheint der Agent nur einmal (Gruppe der **ersten** Zeile, R2) UND ein `log.warn` nennt die
  übergehende Zeile + Agent
  → `AgentOrderTest.sortWarnsWhenAnAgentMatchesMultiplePatterns`
- GIVEN zwei verschiedene Agent-Instanzen mit gleichem Namen, WHEN sortiert, THEN erscheint nur
  **eine** (die Erste) UND ein `log.warn` nennt die Namens-Kollision — kein stiller Drop
  → `AgentOrderTest.sortWarnsWhenTwoDistinctAgentsShareAName` (`hasSize(1)` + warn; ersetzt den
  alten `bug_sortSilentlyDropsAgentsWithDuplicateNames`, der `hasSize(2)` bewies)

## R5 — Fallback auf die Default-Ordnung, wenn nichts matcht ✅ done (2026-10-31, Issue 2)

**WEIL:** Ein Muster, das auf **keinen** Agenten passt, trifft keine Aussage über die Reihenfolge —
es ist ein Relikt (umbenannter Agent, übernommene Datei). Alle Agenten in den alphabetischen Schwanz
zu schicken, ist dann die teure Antwort: Jon verliert Platz 1 im Dropdown.

- GIVEN eine `agent-order.txt` mit gültigen Patterns, WHEN **kein** geladener Agent ein Pattern
  matcht, THEN gilt die Default-Ordnung (`^Jon$` → Jon zuerst, Rest alphabetisch) und der Fallback
  wird per `log.warn` gemeldet; Teiltreffer ändern nichts (R2/R3)
  → `AgentOrderTest.sortFallsBackToDefaultOrderWhenNoAgentMatchesAnyPattern`
- GIVEN `.*` (oder ein beliebiges Muster, das mindestens einen Agenten trifft), WHEN sortiert,
  THEN bleibt die Datei maßgeblich — die absichtliche Alphabet-Ordnung überlebt den Fallback
  → `AgentOrderTest.sortWithCatchAllPatternKeepsAlphabeticalOrder`

## Known Edge (Doku, kein Fix)

- Schlägt das Auto-Create fehl (read-only Config-Dir), wirft `AgentOrder.load()` und der
  ganze Agent-Reload bricht — **keine** Agenten laden. Ordering ist kosmetisch; statt zu
  töten wäre „weiter ohne Ordering" das robustere Verhalten. Kandidat für den Bug-Fix-Zyklus.
- `peek(seen::add)` = Side-Effect im Stream (funktioniert, aber unidiomatisch) — ersetzt durch **R4** (2026-09-11).

## 🔒 Issue 2 — Default-Sortierung im Dropdown defekt (Paul 2026-10-31, gebaut auf `web-factory-goon`)

**SOLL** (Default, ohne `agent-order.txt` bzw. nur `^Jon$`):

```
Jon
Dev (default)
Plan
Review
Scaffold
```

**Verdict:** der Defekt lag **doch** im Core-Sort — nicht an den Default-Patterns, sondern an der
Fallback-**Bedingung**: `sort` fiel nur zurück, wenn die Datei gar keine Patterns lieferte
(`patterns.isEmpty()`). Eine bestehende Datei mit gültigen, aber zu nichts passenden Mustern
(Pauls `^Peon-PO$` nach den Renames) ließ jeden Agenten als „unmatched" ans Ende laufen → Jon verlor
Platz 1. Der Vor-Check hatte allein den Fall „nur `^Jon$`" gesehen.

**Fix:** **R5** — matcht kein Agent irgendein Pattern, gilt die Default-Ordnung (Jon zuerst) samt
`log.warn`. Pauls `agent-order.txt` wird nicht migriert (Clean Break): solange nichts passt, ordnet
der Default; zieht er die Muster auf die aktuellen Namen, übernimmt wieder die Datei.