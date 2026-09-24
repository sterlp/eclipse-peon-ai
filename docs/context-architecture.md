# Context Architecture — Static vs Dynamic

**Status:** ✅ done (2026-08-21) · **Datum:** 2026-08-15 · offen: UI-Reporting (❌ specified)

## Purpose

Klare Abgrenzung wie Context in die Agenten geladen wird. Zwei Kategorien, zwei Ziele,
ein einheitlicher Mechanismus: `ContextItem.render()`.

## Two Categories

| | **Static Context** | **Dynamic Context** |
|---|---|---|
| **Wo** | System-Prompt | Chat History (UserMessage) |
| **Wann** | Lazy — beim 1. Turn nach clear/first-call | Lazy — wenn nicht bereits vorhanden (contains-Check) |
| **Ändert sich?** | Nein (nur Env; Rebuild bei clear/compact/config-change, ADR-0031) | Ja (Memory-Snapshot pro Änderung, Command, Skill, Selektion pro Turn) |
| **KV-Cache** | Prefix stabil → Cache-freundlich | Breakt Cache wenn neu |
| **Überlebt Compact?** | Ja (System-Prompt wird rebuild) | Ja (contains-Check re-injiziert) |

## Static Context — System-Prompt

Geladen als `persistentContext: List<ContextItem>` → gerendert in `buildSystemPrompt()`.

| Item | Wer | Quelle |
|------|-----|--------|
| OS/Date-Regeln (Datum, OS, File-Access, User-Language-Fallback) | Alle | `PeonAiService.initStaticContext()` |

**User-Language-Fallback (2026-09-23, Paul):** der Static Context trägt
`user language: <user.language> — respond in the user's language; if unsure or not otherwise
declared, use this one.` Vorrang-Kette: explizite Deklaration (AGENTS.md / AGENTS-\<agent\>.md)
> Sprache der Nutzernachricht > System-Locale. Die Locale ist bewusst **letzte Stufe** (Fallback
für Agenten ohne Userkontakt — Sklaven, Search-Agent — und den ersten Turn), kein Override —
sonst gewinnt die statische Env gegen die eigentliche Chat-Sprache.

**Memory ist NICHT mehr hier** (Revision 2026-08-23, ADR-0032): der statische Snapshot wurde
entfernt — seit dem dynamischen Turn-Item war er reine Duplikation. Static Context = nur noch Env.

**Lazy-Verhalten:** `systemMessage = null` → nächster `call()` baut System-Prompt neu.
Rebuild-Trigger: `clear()`, `compressContext()`, `setStaticContext()`, `updateConfig()` (nach
Agent-Refresh) und der `ReloadConfigTool`-Pfad (ADR-0031).
**Dateien gehören NICHT hierher** (SOLL 2026-08-16, ✅ 2026-08-16): alles Datei-basierte wandert in die
Chat History (Dynamic) — veraltet bei Projektwechsel nicht. Das **Workspace-Memory gehört auch nicht
mehr hierher**: ursprünglich als Snapshot gebacken (SOLL 2026-08-21, ADR-0031), mit ADR-0032 erst
zusätzlich dynamisch, seit der Revision 2026-08-23 **nur noch dynamisch** — siehe Abschnitt unten.
Siehe [ADR-0029](adr/0029-file-context-in-history.md), [ADR-0031](adr/0031-static-context-env-plus-memory.md),
[ADR-0032](adr/0032-workspace-memory-dynamic-turn-context.md).

## Workspace-Memory dynamisch im Turn-Context (Revision 2026-08-23, [ADR-0032](adr/0032-workspace-memory-dynamic-turn-context.md))

Das Memory lebt **ausschließlich** dynamisch — zuerst als zusätzliches Item neben dem statischen
Snapshot (✅ gebaut 2026-08-23), dann per User-Revision **ohne** den Snapshot (nur noch dynamisch;
Code-Umsetzung durch den User). AGENTS.md-Muster: gleicher dedupKey, geänderter Inhalt → neuer
Snapshot in der History, alter bleibt bis zum Compact.

- **R1:** `WorkspaceMemoryTool` wird zum `ContextItem` (rendert den aktuellen Memory-Stand,
  dedupKey = `"workspace-memory#" + hash(entries)` — renderTurnContext-Dedup geht nur auf den Key,
  daher trägt der Hash die Änderungs-Erkennung).
- **R2:** Der `turnContextSupplier` (`PeonAiService.get()`) hängt das Item an jeden Turn des
  aktiven Agenten.
- **R3:** Die Sklaven (Da Thinka/Da Mek) bekommen ihn über den Orders-Supplier des Delegate-Tools
  (`PoDelegateTool`, früher `JonDelegateTool`) als drittes Item (nach Plan + AGENTS.md,
  `BuildPoAgentComponent`) — lazy pro Delegation, das Tool selbst bleibt ihnen entzogen (nur Jon
  schreibt).
- **R4 (Revision 2026-08-23):** ~~`initStaticContext()` bäckt weiterhin Env + initialen Snapshot~~ →
  der statische Snapshot wird **komplett entfernt** (nur noch Env); die Frische kommt ausschließlich
  aus dem Turn-Context. Damit ist der Bug PeonAiService.java:170 adressiert: der Callback-Hack bleibt
  als Re-Bake für neue Custom Agents (Env), verliert aber seine Kritikalität.
- **Nachteil bewusst akzeptiert:** mehr Context (Snapshots sammeln sich bis zum Compact); Vorteil:
  neue Einträge erreichen aktive Agenten UND Sub-Agenten ohne Re-Bake/Config-Change. Seit der
  Revision kein Doppel-Anteil mehr: Memory nur 1× im Kontext (Turn-Message), System-Prompt schlanker.

**BDD:**
```
GIVEN das Workspace-Memory enthält Eintrag E1
WHEN ein Turn beginnt
THEN der aktuelle Memory-Snapshot (inkl. E1) ist als ContextItem in dieser Nachricht

GIVEN ein früherer Turn hat einen Memory-Snapshot injiziert
AND Jon hat seitdem Eintrag E2 hinzugefügt
WHEN der nächste Turn beginnt
THEN ein neuer Snapshot inkl. E2 wird injiziert (gleicher dedupKey)
AND der alte Snapshot bleibt in der History bis zum Compact

GIVEN Jon delegiert an Da Thinka (buildWithDev/talkPlan)
WHEN die Orders-Erstnachricht des Slaven gebaut wird
THEN sie enthält Plan-Referenz + AGENTS.md + den aktuellen Memory-Snapshot

GIVEN das Memory ist leer
WHEN ein Turn beginnt
THEN wird kein leeres Memory-Item injiziert
```

## Dynamic Context — Chat History

Geladen als `turnContextSupplier: Supplier<List<ContextItem>>` → injiziert via `restoreTurnContext()`
mit contains-Check (`memory.containsUserMessage(rendered)`).

| Item | Wer | Quelle |
|------|-----|--------|
| Project-Info (Name, Pfad, Natures) | Alle | `turnContextSupplier` |
| Selektierte Datei | Aktiver Agent | `turnContextSupplier` |
| Active Command (`/command`) | Aktiver Agent | One-time via `addOneTimeOrder()` |
| Active Skill | Aktiver Agent | One-time via `addOneTimeOrder()` |
| AGENTS.md | Alle | `AgentsMdContextItem.itemsFor(agentName, project)` |
| AGENTS-\<agent\>.md | Alle (falls existiert) | `AgentsMdContextItem.itemsFor(agentName, project)` |
| Workspace-Memory (live, dedupKey = `workspace-memory#` + entries-hash) | Alle inkl. Jons Slaven | `PeonAiService.get()` / `JonDelegateTool`-Orders |
| docs/memory.md | Jon | `EclipseFileContextItem("docs/memory.md")` |
| docs/index.md | Jon | `EclipseFileContextItem("docs/index.md")` |
| Plan-Path (sticky) | Dev Slave | `JonDelegateTool` |
| Handoff-Line (einmalig) | Dev Agent | `PeonAiService.get()` |

**Lazy-Verhalten:** `restoreTurnContext()` läuft bei jedem `doCall()` — Dedup-Check verhindert
Duplikate. File-Items werden **einmal pro vollem Pfad** injiziert: nie bei Datei-Änderung
(Änderungen stehen ohnehin als Tool-Messages in der History), nur bei **anderem Pfad**
(Projektwechsel) oder **nach Compact** (Memory geleert). Fehlende Datei → `null` → übersprungen,
keine Exception, kein Status-Eintrag.
**Format (2026-08-21, ADR-0031):** Render mit Linenumbers (`FileLines.format`); Dedup-Key =
exakter Header `<pfad>:` + LineSeparator + ` content with line numbers:` — Präfix der injizierten
Message (Dedup-Prinzip von ADR-0029 unverändert).

## Bugfix: "Loading 📋"-Zeilen (2026-08-16, ✅ done)

**Status 2026-08-16:** Alle Items gebaut + Review OK + alle Tests grün + Smoke Test (Paul) ✅:
Header-Vertrag (dedupKey = exakter Header `<pfad>:\n---\n`) in allen File-Items
(Core: DiskFileContextItem; Plugin: EclipseFileContextItem, AgentsMdContextItem),
AGENTS.md + AGENTS-DEV.md beide im Kontext mit je eigener "Loading 📋"-Zeile,
Live-Status nach Compact sauber ausgeblendet.

**Befund:**
- **Sticky Status nach Compact:** der Compress-Pfad hat das Live-Status nie
  ausgeblendet → das letzte `onTool` ("Loading 📋 …") blieb im Live-Status kleben.
  Fix in R2(a). Die "Loading 📋"-Zeile ist bereits eine TOOL-Message im Chat
  (live-Append via `onChatResponse`) — das ist der eine, persistente Kanal.
- **Keine AGENTS-\<agent\>.md-Zeile:** `AgentsMdContextItem` = **ein** Item (Base +
  Agent-Datei joined) mit **Base-Pfad** als Dedup-Key und Label → die Agent-Datei
  hat nie eigene "Loading 📋"-Zeile; ist die Base schon deduppt, wird das ganze
  Item inkl. Agent-Datei übersprungen.

**SOLL:**
- **R1 ✅:** `AgentsMdContextItem` → **zwei** Items (Base `AGENTS.md` +
  `AGENTS-<agent>.md`), je voller Pfad als Dedup-Key + je eigene "Loading 📋"-Zeile.
  Dedup bleibt Memory-Pflicht, greift je Item (ADR-0029: Dedup nach vollem Pfad).
- **R2 ✅:** Regression: (a) nach Compact ist das Live-Status ausgeblendet und die
  "Loading 📋"-Zeilen im Chat sichtbar — `doCompressContext`: `hideLiveStatus()` steht
  **nach** dem Replay-`forEach` (letztes Command des Compact-Pfads, `AIChatView`);
  das TOOL→Live-Mapping in `onChatResponse` bleibt bewusst erhalten (Feature: hebt das
  letzte Tool hervor, während die AI arbeitet); Rest-Race (spät gelieferter
  Monitor-Callback) nur theoretisch — beobachten. (b) je Item (Base + Agent-Datei)
  eine eigene "Loading 📋"-Zeile.

**Gestrichen (YAGNI):** ToSimpleMessage-Voll-Render + onTool-Entfernung — nicht
nötig, die TOOL-Message ist schon der Kanal.

**BDD:**
```
GIVEN ein neuer Chat mit AGENTS.md + AGENTS-DEV.md
WHEN der erste Turn beginnt
THEN je Datei eine "Loading 📋 <voller Pfad>"-Zeile im Chat (Base und Agent-Datei je eine)

GIVEN ein Compact (clear + Re-Injektion + Summary) ist abgeschlossen
WHEN die Compress-Operation fertig ist
THEN das Live-Status ist ausgeblendet
AND die "Loading 📋"-Zeilen sind im Chat sichtbar

GIVEN die Base AGENTS.md ist bereits in der History (Dedup-Treffer)
AND AGENTS-DEV.md noch nicht
WHEN der nächste Turn beginnt
THEN AGENTS-DEV.md wird injiziert (eigener Dedup-Key)
AND ihre "Loading 📋"-Zeile ist sichtbar
```

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

updateConfig() / ReloadConfigTool
  → agentService.refresh(dir) / reloadAgents()
  → initStaticContext()                                     // Re-Bake Env+Memory (Cache-Invalidation)
```

## BDD

```
GIVEN ein Agent mit persistentContext [Env, Memory-Snapshot]
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
