# ADR-0029: File-Context wandert in die Chat History, Dedup nach vollem Pfad

**Status:** Akzeptiert · **Datum:** 2026-08-16 · **Betroffen:** alle Agenten (System-Prompt-Komposition)

## Kontext

ADR-0028 legte Datei-Context (AGENTS.md, memory.md, index.md) in den **System-Prompt**
(`persistentContext`) — für KV-Cache-Stabilität. Zwei Bugs zeigten die Kehrseite:

1. **Crash:** `EclipseFileContextItem.render()` warf `RuntimeException: File not found` bei
   optionalen Dateien (memory.md existiert nicht ohne Session-Notes) → killte die gesamte Request
   (Stack: `AbstractAgent.buildSystemPrompt`).
2. **Stale Projekt:** `systemMessage` wird nur nach Compact / `setPersistentContext` rebuilt — ein
   Projektwechsel ließ den alten AGENTS.md / die alten Docs im Prompt, bis zum nächsten Compact.
   Der `lastModified`-Cache im File-Item (nur Timestamp als Key) konnte bei gleichem Timestamp sogar
   den Inhalt des **falschen Projekts** liefern.

## Entscheidung

- **Datei-Items gehören in die Chat History** (`turnContextSupplier` → `restoreTurnContext`), nicht
  in den System-Prompt. Pro Turn injiziert, wenn nicht schon vorhanden.
- **Dedup nach vollem Workspace-Pfad (Header), nie nach Content** — Content-Änderungen an der Datei
  (z. B. Jon editiert memory.md) injizieren nie neu; die Änderungen stehen ohnehin als
  Tool-Messages in der History. Neu injiziert wird nur bei **anderem Pfad** (Projektwechsel) oder
  **nach Compact** (Memory geleert).
- **Fehlende Datei → `render() = null`** → übersprungen, keine Exception, kein Status-Eintrag.
- **Header = voller Workspace-Pfad** (`<path>:\n---\n<content>`), "Loading"-Status zeigt denselben
  Pfad (Projekt erkennbar).
- **`lastModified`-Cache entfernt** (KISS: ein Read pro Session/Projekt/Compact/Projektwechsel reicht).
- **KV-Cache bleibt gut:** History-Injection ist append-only — der ge-Cachte Prefix wandert nicht,
  `setProject()` braucht keine Invalidation des System-Prompts.

## Konsequenzen

- System-Prompt wird komplett statisch (nur OS/Date-Regeln) — rebuild nur nach Compact / first call.
- `docsIndexSeedForFirstMessage` wird redundant → entfernt (index.md kommt als eigene
  History-Message bei Jons erstem Turn).
- `ThreadSafeMemory.add()` merged consecutive User-Messages (File-Message + User-Message zu einer) —
  der Pfad-Dedup ist ein Substring-Check über alle User-Messages, bleibt dadurch robust.
- Supersedet den "persistentContext für Dateien"-Teil von ADR-0028; das ContextItem-Konzept
  (Interface, Agent-besitzter Compact-Flow) bleibt unverändert gültig.

## Korrektur (2026-08-16)

Der Substring-Check lief auf den **bloßen Pfad** (dedupKey) → **False-Positive**: eine
Compact-Summary erwähnt den Pfad ("loaded /proj/AGENTS.md …") → Datei wurde nach Compact
nicht re-injiziert. Fix: Dedup prüft **`ContextItem.dedupKey()`** = exakter Header
`<pfad>:\n---\n` (mit Trenner, `System.lineSeparator()`). `label()` = bloßer Pfad für den
"Loading 📋"-Status. Drei Methods: `render()` / `label()` / `dedupKey()` (Default null →
Content-Dedup). Eine Summary kann den Pfad erwähnen, aber praktisch nie den exakten Header.

**Umgesetzt ✅ (2026-08-16):** Core (3 Inkremente) + Plugin-Delta (EclipseFileContextItem,
AgentsMdContextItem) — Review OK, alle Tests grün (Core 418/0, Plugin grün).

## Verwandt

- [Context Message Konzept](../context-message-concept.md) — Regeln + BDD
- [Context Architecture](../context-architecture.md) — Static vs. Dynamic-Kategorisierung
- [AGENTS.md Support](../agents-md-support.md) — File-Resolution (welcher Name, Fallback)
