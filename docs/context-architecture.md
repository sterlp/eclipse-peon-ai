# Context Architecture — Static vs Dynamic

**Status:** ❌ specified · **Datum:** 2026-08-15

## Purpose

Klare Abgrenzung wie Context in die Agenten geladen wird. Zwei Kategorien, zwei Ziele,
ein einheitlicher Mechanismus: `ContextItem.render()`.

## Two Categories

| | **Static Context** | **Dynamic Context** |
|---|---|---|
| **Wo** | System-Prompt | Chat History (UserMessage) |
| **Wann** | Lazy — beim 1. Turn nach clear/first-call | Lazy — wenn nicht bereits vorhanden (contains-Check) |
| **Ändert sich?** | Nein (außer Datei auf Disk) | Ja (Command, Skill, Selektion pro Turn) |
| **KV-Cache** | Prefix stabil → Cache-freundlich | Breakt Cache wenn neu |
| **Überlebt Compact?** | Ja (System-Prompt wird rebuild) | Ja (contains-Check re-injiziert) |

## Static Context — System-Prompt

Geladen als `persistentContext: List<ContextItem>` → gerendert in `buildSystemPrompt()`.

| Item | Wer | Quelle |
|------|-----|--------|
| OS/Date-Regeln (Datum, OS, File-Access) | Alle | `PeonAiService.setStaticContext()` |
| AGENTS.md | Alle | `AgentsMdContextItem(agentName)` |
| AGENTS-\<agent\>.md | Alle (falls existiert) | `AgentsMdContextItem(agentName)` |
| docs/memory.md | Jon | `EclipseFileContextItem("docs/memory.md")` |
| docs/index.md | Jon | `EclipseFileContextItem("docs/index.md")` |

**Lazy-Verhalten:** `systemMessage = null` → nächster `call()` baut System-Prompt neu.
Nach `compressContext()` wird `systemMessage = null` gesetzt → rebuild beim nächsten Turn.

## Dynamic Context — Chat History

Geladen als `turnContextSupplier: Supplier<List<ContextItem>>` → injiziert via `restoreTurnContext()`
mit contains-Check (`memory.containsUserMessage(rendered)`).

| Item | Wer | Quelle |
|------|-----|--------|
| Project-Info (Name, Pfad, Natures) | Alle | `turnContextSupplier` |
| Selektierte Datei | Aktiver Agent | `turnContextSupplier` |
| Active Command (`/command`) | Aktiver Agent | One-time via `addOneTimeOrder()` |
| Active Skill | Aktiver Agent | One-time via `addOneTimeOrder()` |
| Shared Memory (memory.md Content) | Slaves | `JonDelegateTool` supplier |
| Plan-Path (sticky) | Dev Slave | `JonDelegateTool` |
| Handoff-Line (einmalig) | Dev Agent | `PeonAiService.get()` |
| Docs-Index-Seed (einmalig) | Jon | `docsIndexSeedForFirstMessage()` |

**Lazy-Verhalten:** `restoreTurnContext()` läuft bei jedem `doCall()` — contains-Check verhindert
Duplikate. Nur bei leerer Memory (first call / nach compact) wird tatsächlich injiziert.

## UI Reporting (SOLL)

Der Status-Line/Token-Header zeigt, was geladen ist:

```
📋 AGENTS.md ✅ · memory.md ✅ · index.md ✅ · Project: llmpeon-parent
```

Gestrichen/fehlt wenn nicht vorhanden. Token-Zahl neben dem Item für Transparenz.

## Data Flow

```
call()
  → doCall()
      → if (systemMessage == null) buildSystemPrompt()     // Static: lazy rebuild
      → restoreTurnContext()                                // Dynamic: contains-check
      → toolService.executeLoop(...)

compressContext()
  → memory.clear()
  → systemMessage = null                                    // Force static rebuild
  → restoreTurnContext()                                    // Dynamic re-injection
  → memory.add(summary)
```

## BDD

```
GIVEN ein Agent mit persistentContext [AGENTS.md, OS-Regeln]
AND systemMessage ist null (first call oder nach compact)
WHEN call() aufgerufen
THEN buildSystemPrompt() rendert alle persistentContext Items in den System-Prompt
AND systemMessage wird gecacht (kein erneuter Build im selben Turn)

GIVEN turnContextSupplier liefert [Project-Info]
AND Memory ist leer (first call)
WHEN restoreTurnContext() aufgerufen
THEN Project-Info wird als UserMessage in Memory injiziert

GIVEN turnContextSupplier liefert [Project-Info]
AND Memory enthält bereits Project-Info (contains-Check true)
WHEN restoreTurnContext() aufgerufen
THEN Project-Info wird NICHT erneut injiziert

GIVEN ein Agent mit persistentContext [AGENTS.md]
WHEN compressContext() aufgerufen
THEN systemMessage wird null gesetzt
AND beim nächsten call() wird System-Prompt neu gebaut (AGENTS.md frisch geladen)
```

## Relationship to Other Docs

- [context-message-concept.md](context-message-concept.md) — technische Implementierung (ContextItem, AbstractAgent)
- [agents-md-support.md](agents-md-support.md) — AGENTS.md File-Resolution (Welcher Name, Fallback)
- [standing-orders-design.md](standing-orders-design.md) — historischer Design (→ wird hier abgelöst für Dynamic Context)

## Offene Punkte

> **Slaves & AGENTS-\<agent\>.md (Namens-Bug):** Solange der `NamedAgent`-Wrapper existiert
> (UI-Name "Da Thinka" ≠ Agent-Name "Peon-Plan"), bekommen Slaves nur die base `AGENTS.md`.
> `AGENTS-da-thinka.md` / `AGENTS-da-mek.md` funktionieren erst wenn die Slaves direkt benannt
> sind (Side Quest, Backlog).

## Potenzial zum Aufräumen

> **Notiz 2026-08-15:** Die aktuelle Implementierung hat Redundanzen:
> - `PeonAiService`-Konstruktor (~200 Zeilen) verdrahtet alles in einem Rutsch
> - `PeonAiService.get()` (ContextItemProvider) mischt Scaffold-Spezialfall + Default
> - `standing-orders-design.md` beschreibt noch den alten ToolLoopRequest-Flow
> - `setStaticContext()` + `turnContextSupplier` + `JonDelegateTool` supplier überschneiden sich
>
> **Nicht jetzt aufräumen** — nur wenn wir drin sind (siehe [architecture.md](architecture.md),
> Extrahieren-Regel >10 lines + testbar).
