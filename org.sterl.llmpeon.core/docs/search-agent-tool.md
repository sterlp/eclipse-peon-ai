---
idPrefix: LSR
---

# Search Agent Tool (Da Sniffa)

> Architektur/Abgrenzung: [search-agent-tool-architektur.md](search-agent-tool-architektur.md)

**Goal:** one-shot research sub-agent for complex multi-step lookups, keeping the parent agent's context lean. Since 2026-10-24 with a rolling hot-context block (`last-search-rolling`) — the loop stays one-shot, only the static context carries over (R3, R-LSR-5).

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

Carry-over exists ONLY as the explicitly labelled `last-search-rolling` block in the static
messages (R-LSR-5) — never as memory history.

### R4 — Timing & header status ✅

The search agent reports wall-clock timing and lights a transient header chip while running.

- **GIVEN** `searchAgent` starts **WHEN** the tool runs **THEN** `monitor.onSubAgent("Search", true)` fires (header chip lit)
- **GIVEN** `searchAgent` finishes **WHEN** the tool returns **THEN** `monitor.onSubAgent("Search", false)` fires and the result includes elapsed time (e.g. `done. (3s)`)
- **Tag:** unit (verify `onSubAgent` calls; verify `StringUtil.humanElapsed` in result)

## Rolling Search Context — `last-search-rolling` (✅ done, 2026-10-24 Paul)

Da Sniffa sieht bei jeder Suche den Hot-Block der vorherigen Suchen derselben Chat-View: Prompt +
finale Antwort, append-only, gekappt auf die letzten 10.000 Zeichen. Ein-rollendes Fenster statt
Historie — der Such-Agent muss nicht wiederholen, was er gerade herausgefunden hat. Kein
Config-Schalter (immer an, YAGNI — Paul 2026-10-24).

```
//-- Start last-search-rolling
Frage: Wie konfiguriert man async in Spring Boot?
Antwort: Die wichtigsten Ansätze sind @Async, ThreadPoolTaskExecutor …
Frage: und in Java EE?
Antwort: …
//(older context dropped to fit cap)   ← nur wenn gekappt wurde
//-- End last-search-rolling
```

### R-LSR-1 — Append nur bei vollständigem Ergebnis ✅

Nach jeder **vollständigen** Suche hängt Da Sniffa ein Paar `Frage: <prompt>` + `Antwort: <finale
Antwort>` an den Hot-Block. Gespeichert wird nur der finale Antworttext — kein Thinking, keine
Trefferliste, keine Tool-Rohdaten (die finale Antwort ist die Destillation; ADR-0075 für die
Trim-Entscheidung).

#### UC-LSR-1 — successfulSearchAppendsPair ✅
- GIVEN Hot-Block mit einem Paar WHEN eine weitere Suche vollständig durchläuft THEN der Block
  enthält das neue Paar (Frage + finale Antwort) hinter dem alten, vor der Kappung.

### R-LSR-2 — Fehler/Cancel/leere Antwort ändern nichts ✅

Exception, Cancel oder der Fallback-String „Search completed but returned no result" fügen nichts
an — der Block zeigt weiter den letzten vollständigen Stand. Eine abgebrochene Suche erfindet
keine Antwort („a tool must never lie").

#### UC-LSR-2 — failedSearchLeavesBlockUnchanged ✅
- GIVEN Hot-Block mit Paar WHEN die nächste Suche fehlschlägt (Exception, Cancel oder leerer
  Fallback-String) THEN der Block ist unverändert — kein Eintrag, keine Kappung.

### R-LSR-3 — Cap 10.000 Zeichen, roher Tail-Schnitt ✅

Der Cap gilt auf den **Paar-Inhalt**: der Block wird auf die letzten 10.000 Zeichen gekappt — roher
Char-Schnitt, der Kopf samt evtl. Frage-Header darf weg (Fragmente bewusst akzeptiert, Paul
2026-10-24 gegen Jons Paar-Grenzen-Empfehlung → ADR-0075). Kappung passiert vor dem Setzen von
Header/Disclosure, sodass die Deko-Zeilen die Kappungsgrenze nicht verschieben (gerendert = Paar-Inhalt
≤10.000 + Deko-Zeilen). Ein Paar allein über dem Cap: die Antwort-Schwanzseite überlebt, der Schnitt
ist rein mechanisch.

#### UC-LSR-3 — blockTrimmedToLastTenThousandChars ✅
- GIVEN Blockinhalt von 14.000 Zeichen nach dem Append WHEN der Block aktualisiert wird THEN er
  enthält exakt die letzten 10.000 Zeichen des Inhalts.

### R-LSR-4 — Disclosure nur bei Kappung ✅

Wurde gekappt, trägt der Block die Zeile `(older context dropped to fit cap)`; ungekappt erscheint
sie nicht (AGENTS.md: eine Kappung, die der Agent nicht sieht, ist eine stille Lüge).

#### UC-LSR-4 — disclosureOnlyWhenTrimmed ✅
- GIVEN Block wurde gekappt WHEN der Sub-Loop ihn sieht THEN die Disclosure-Zeile steht im Block.
- GIVEN Block ≤10.000 Zeichen WHEN der Sub-Loop ihn sieht THEN die Disclosure-Zeile fehlt.

### R-LSR-5 — Injektion als gelabelte Static Message ✅

Pro Aufruf wird der Block als **eigene Static Message** mit `//-- Start last-search-rolling`-Header
in den Sub-Loop gegeben — der Sub-Loop hat keine ContextItem-Mechanik (`agent(null)`, frisches
Memory, R3). **Leerer Block → keine Injektion** (exakt das bisherige Verhalten, keine Regression).
`search-agent.md` erklärt den Block in einer Zeile, damit das getaggte Format beim
Suchmodell nicht unerklärt bleibt (Sub-Loops sehen kein TaggedResult-INFO).

#### UC-LSR-5 — subLoopSeesRollingBlockAsStaticMessage ✅
- GIVEN Hot-Block nicht leer WHEN `searchAgent` läuft THEN die System Messages enthalten den
  `last-search-rolling`-Block, und das Sub-Memory enthält nur die UserMessage des Prompts.

### R-LSR-6 — Ein Block pro Chat-View ✅

Der Block lebt an der `SearchAgentTool`-Instanz — eine pro Chat-View — und wird von allen Agents
der View geteilt (Paul 2026-10-24: Einfachheit gewinnt; aktuell gibt es zudem nur einen
Such-Agent). Kein Keying pro Parent-Agent.

#### UC-LSR-6 — agentsShareOneBlockPerView ✅
- GIVEN Agent A derselben View suchte erfolgreich WHEN Agent B derselben View danach sucht THEN B
  sieht A's Paar im `last-search-rolling`-Block.

## Components

- **`SearchAgentTool`** (`llmpeon-core`): tool wrapping the one-shot agent loop; owns the rolling hot block.
- **`search-agent.md`** (`llmpeon-core`, `prompts/`): system prompt for Da Sniffa (**no** `default.txt` prepended — `default.txt` is for full agents, not stateless tools; Da Sniffa doesn't need askUser/queue rules).
- **`AgentConfig.searchAgentConfig()`** (`llmpeon-core`): per-agent config with search model + think disabled.

## ADRs

- [ADR-0075](../../docs/adr/0075-last-search-rolling-rohschnitt.md) — Hot-Block wird roh auf 10k Zeichen gekappt (Fragmente bewusst akzeptiert), kein Schalter.