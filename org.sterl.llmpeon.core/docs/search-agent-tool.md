# Search Agent Tool (Da Sniffa)

**Goal:** Stateless one-shot research sub-agent for complex multi-step lookups, keeping the parent agent's context lean.

## Business Rules

### R1 — Read-only tool scope ✅

Da Sniffa gets only read tools — no writes, no shell, no user interaction, no memory curation.

- **GIVEN** `searchAgent` is invoked **WHEN** the tool loop starts **THEN** the agent's tool filter excludes: all edit tools, `SearchAgentTool` (no recursion), `ShellTool`, `AskUserTool`, `WorkspaceMemoryTool`
- **GIVEN** a read tool is available **WHEN** the agent needs it **THEN** it can call it (e.g. `diskReadFile`, `diskGrep`, `eclipseReadFile`, `eclipseGrep`)
- **Tag:** unit (verify `SearchAgentTool.searchAgent` toolFilter predicates)

### R2 — Dedicated search model, no thinking ✅

Da Sniffa uses a dedicated search model (`searchModel` in `LlmConfig`) with thinking disabled — research should be fast and cheap.

- **GIVEN** `LlmConfig` has a `searchModel` set **WHEN** `searchAgent` runs **THEN** it uses `searchModel` (not the parent agent's model)
- **GIVEN** `searchAgent` is configured **WHEN** it calls the provider **THEN** think/reasoning is disabled (no `reasoning.effort` sent)
- **GIVEN** `searchModel` is unset **WHEN** `searchAgent` runs **THEN** it falls back to the default model with thinking disabled
- **Tag:** unit (verify `AgentConfig.searchAgentConfig()` disables think; verify model resolution)

### R3 — Stateless one-shot execution ✅

Da Sniffa is created fresh per call — no persistent memory, no context carry-over between invocations.

- **GIVEN** `searchAgent` is called twice with different prompts **WHEN** the second call starts **THEN** it has an empty `ThreadSafeMemory` (no history from the first call)
- **GIVEN** a search completes **WHEN** the tool returns **THEN** the parent agent's memory is untouched (Da Sniffa's memory is discarded)
- **Tag:** unit (verify `new ThreadSafeMemory()` per call in `SearchAgentTool.searchAgent`)

### R4 — Timing & header status ✅

The search agent reports wall-clock timing and lights a transient header chip while running.

- **GIVEN** `searchAgent` starts **WHEN** the tool runs **THEN** `monitor.onSubAgent("Search", true)` fires (header chip lit)
- **GIVEN** `searchAgent` finishes **WHEN** the tool returns **THEN** `monitor.onSubAgent("Search", false)` fires and the result includes elapsed time (e.g. `done. (3s)`)
- **Tag:** unit (verify `onSubAgent` calls; verify `StringUtil.humanElapsed` in result)

## Idee — stateful Search Agent (💡 2026-10-18, Paul — bewertet, **nicht** beschlossen)

**Vorschlag:** Am Ende jedes Such-Laufs legt Da Sniffa ein Tupel (User-Message = Request, finale
AI-Antwort = das an den Aufrufer weitergegebene Ergebnis) in sein Memory — das Tupel wird bei jeder
Suche überschrieben, **nur RAM, kein File-Appender**. Begründung: Suchen einer Session betreffen
meist ähnliche Dateien; das letzte Ergebnis erspart dem Suchagenten die Wiederholung.

**Bewertung (Jon) — nachvollziehbarer Nutzen, aber nicht in dieser Form:** Das Tupel widerspricht
R3 (*stateless one-shot*) und macht die Suche von der Vorgeschichte abhängig — bei thematisch
unverwandten Suchen ist das alte Ergebnis reines Rauschen im ohnehin kleinen Such-Kontext, und ein
veraltetes Ergebnis (Datei hat sich geändert) wird stillschweigend geglaubt. Zudem trägt die
weitergeleitete Antwort nur die *Zusammenfassung*: das billige Wiederaufsetzen käme aus den
Fundstellen (Pfade, Zeilen), die der erste Lauf weggeworfen hat. Empfehlung, falls wir es
verfolgen: nicht als Agent-Memory, sondern als **explizit gelabelter Continuity-Block**
(`previous search: <request>` + Ergebnis, gedeckelt, mit Schalter) — dann bleibt R3 als
Default-Semantik gültig, der Aufrufer sieht, dass Vorgeschichte im Spiel ist, und die Ablehnung
eines stale Ergebnisses ist eine Regel statt einer Annahme. Priorität: **niedrig** — der
Aufrufer hat das vorherige Ergebnis ohnehin in seinem eigenen Memory und kann seinen Prompt
darauf aufsetzen.

## Components
- **`SearchAgentTool`** (`llmpeon-core`): tool wrapping the one-shot agent loop.
- **`search-agent.txt`** (`llmpeon-core`): system prompt for Da Sniffa (**no** `default.txt` prepended — `default.txt` is for full agents, not stateless tools; Da Sniffa doesn't need askUser/queue rules).
- **`AgentConfig.searchAgentConfig()`** (`llmpeon-core`): per-agent config with search model + think disabled.

## ADRs
— (none yet; design is straightforward, rules above cover it)
