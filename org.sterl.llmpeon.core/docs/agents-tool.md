---
idPrefix: AT
---

# Agents Tool (synchron)

> **Status:** 🚧 in design (Paul 2026-11-13 — nur Doc, Bau später) · Technische Heimat:
> `llmpeon-core`, `org.sterl.llmpeon.agent.tools.AgentsTool` · Asynchrone Stufe:
> [async-agents.md](async-agents.md) · Agent-Datei/`isTool`:
> [builtin-agent-prompt-tracked.md](builtin-agent-prompt-tracked.md) · Search als Agent:
> [search-agent-tool.md](search-agent-tool.md) R-LSR-7

## Ziel

Ein **generisches** Delegations-Werkzeug: ein Agent ruft einen anderen Agenten aus einer **Liste**
auf, statt für jeden Agenten ein eigenes Tool zu haben. Der aufrufende Agent kennt die Liste aus
seinem System-Prompt (Name + Beschreibung). Erste Stufe ist **nur synchron** — der Aufruf blockiert,
bis der gerufene Agent antwortet. `PoDelegateTool` (Jons feste Delegate-Tools) ist die ältere Form
desselben Ziels.

## Business Rules

### R-AT-1 — Eine Liste, ein Aufruf 🚧

Das Tool hält eine Liste registrierter Agenten, eindeutig nach Name. Der aufrufende Agent nennt
Agent-Name und Prompt; das Tool ruft den Agenten synchron auf und liefert dessen Antworttext plus
Stats-Zeile (Kontext-Nutzung, Laufzeit). Ein unbekannter Name liefert einen Fehlertext, der die
bekannten Namen nennt — das Tool tut nie so, als hätte es delegiert.

### R-AT-2 — Nur synchron 🚧

Kein Async-Zweig, keine Task-IDs, keine Poll-Tools (`getTaskStatus`, `waitForTask`). Asynchrone
Delegation bleibt Gegenstand von [async-agents.md](async-agents.md).

### R-AT-3 — Liste im System-Prompt 🚧

Ein Agent, der das Tool hat, bekommt im System-Prompt je erreichbarem Agenten **Name +
Beschreibung**. Ein Agent steht nie in seiner eigenen Liste.

### R-AT-4 — Wer auf die Liste kommt 🚧

Aufrufbar ist ein Agent nur, wenn er als Werkzeug markiert ist (`isTool`, siehe Agent-Datei in
[builtin-agent-prompt-tracked.md](builtin-agent-prompt-tracked.md)); Custom Agents setzen das im
Kopf ihrer `AGENT.md`. Ohne Markierung erscheint er weder in der Liste noch ist er aufrufbar.

### R-AT-5 — Default: jeder Agent erreicht den Search-Agenten 🚧

Per Default erreicht **jeder** Agent — Jon eingeschlossen — den Search-Agenten über das Tool. Der
Search-Agent selbst bekommt das Tool nicht (keine Rekursion, [search-agent-tool.md](search-agent-tool.md) R1).
Search ist dafür ein **echter Agent**; der Hot-Block der letzten Suchen lebt im Search-Agenten
selbst, nicht am Tool ([search-agent-tool.md](search-agent-tool.md) R-LSR-7, Paul 2026-11-13).

### R-AT-6 — Kein Rest-Kontext nach dem Aufruf 🚧

Was ein Aufruf dem gerufenen Agenten als Turn-Kontext mitgibt, gilt nur für diesen Aufruf; ein
späterer Aufruf (auch über einen anderen Weg) sieht ihn nicht mehr.

## Offene Punkte

| Punkt | Status |
|---|---|
| Flag-Name: bestehendes `isTool` (Doc + `AiAgent.isTool()`) wiederverwenden statt `isBot`/`isAgent`? Empfehlung: ja | ❓ |
| `clear`/`compact` je Agent über das Tool (heute `subAgentClear`/`subAgentCompact` im Entwurf, doppelt zu `PoDelegateTool`) | ❓ |
| Löst das Tool `PoDelegateTool` ab, und wann? Empfehlung: eigenes späteres Inkrement | ❓ |
| Reihenfolge: R-LSR-7 (Search als Agent) vor oder mit R-AT-5? Empfehlung: vorher, eigenes Inkrement | ❓ |
