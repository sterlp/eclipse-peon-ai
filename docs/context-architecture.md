# Context Architecture — Static vs Dynamic

**Status:** ✅ done (2026-08-16) · **Datum:** 2026-08-15 · offen: UI-Reporting (❌ specified)

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

**Lazy-Verhalten:** `systemMessage = null` → nächster `call()` baut System-Prompt neu.
Nach `compressContext()` wird `systemMessage = null` gesetzt → rebuild beim nächsten Turn.
**Dateien gehören NICHT hierher** (SOLL 2026-08-16, ✅ 2026-08-16): alles Datei-basierte wandert in die
Chat History (Dynamic) — der System-Prompt bleibt komplett statisch (KV-Cache) und veraltet bei
Projektwechsel nicht mehr. Siehe [ADR-0029](adr/0029-file-context-in-history.md).

## Dynamic Context — Chat History

Geladen als `turnContextSupplier: Supplier<List<ContextItem>>` → injiziert via `restoreTurnContext()`
mit contains-Check (`memory.containsUserMessage(rendered)`).

| Item | Wer | Quelle |
|------|-----|--------|
| Project-Info (Name, Pfad, Natures) | Alle | `turnContextSupplier` |
| Selektierte Datei | Aktiver Agent | `turnContextSupplier` |
| Active Command (`/command`) | Aktiver Agent | One-time via `addOneTimeOrder()` |
| Active Skill | Aktiver Agent | One-time via `addOneTimeOrder()` |
| AGENTS.md | Alle | `AgentsMdContextItem(agentName)` |
| AGENTS-\<agent\>.md | Alle (falls existiert) | `AgentsMdContextItem(agentName)` |
| docs/memory.md | Jon | `EclipseFileContextItem("docs/memory.md")` |
| docs/index.md | Jon | `EclipseFileContextItem("docs/index.md")` |
| Shared Memory (memory.md Content) | Slaves | `JonDelegateTool` supplier |
| Plan-Path (sticky) | Dev Slave | `JonDelegateTool` |
| Handoff-Line (einmalig) | Dev Agent | `PeonAiService.get()` |

**Lazy-Verhalten:** `restoreTurnContext()` läuft bei jedem `doCall()` — Dedup-Check verhindert
Duplikate. File-Items werden **einmal pro vollem Pfad** injiziert: nie bei Datei-Änderung
(Änderungen stehen ohnehin als Tool-Messages in der History), nur bei **anderem Pfad**
(Projektwechsel) oder **nach Compact** (Memory geleert). Fehlende Datei → `null` → übersprungen,
keine Exception, kein Status-Eintrag.

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
GIVEN ein Agent mit persistentContext [OS/Date-Regeln]
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

GIVEN AGENTS.md ist bereits im Chat (Pfad-Dedup)
WHEN die Datei geändert wird (z. B. von Jon)
THEN AGENTS.md wird NICHT erneut injiziert

GIVEN das ausgewählte Projekt wechselt (AGENTS.md hat dann einen anderen vollen Pfad)
WHEN der nächste Turn beginnt
THEN die AGENTS.md des neuen Projekts wird injiziert (anderer Pfad → kein Dup)

GIVEN ein File-ContextItem zeigt auf eine nicht existierende Datei
WHEN restoreTurnContext() aufgerufen
THEN nichts wird injiziert und keine Exception / kein Status-Eintrag
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

> **`docs/handovers-and-plans.md` (BETA):** Die Handover-/Plan-Doku ist noch im BETA-Status.
> Sobald sie stabil ist, muss sie in die Context-Architektur integriert werden (als
> `EclipseFileContextItem` im `turnContextSupplier` oder als `persistentContext`-Item).

## Potenzial zum Aufräumen

> **Notiz 2026-08-15:** Die aktuelle Implementierung hat Redundanzen:
> - `PeonAiService`-Konstruktor (~200 Zeilen) verdrahtet alles in einem Rutsch
> - `PeonAiService.get()` (ContextItemProvider) mischt Scaffold-Spezialfall + Default
> - `standing-orders-design.md` beschreibt noch den alten ToolLoopRequest-Flow
> - `setStaticContext()` + `turnContextSupplier` + `JonDelegateTool` supplier überschneiden sich
>
> **Nicht jetzt aufräumen** — nur wenn wir drin sind (siehe [architecture.md](architecture.md),
> Extrahieren-Regel >10 lines + testbar).
