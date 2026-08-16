# Context Message Konzept — Typ-basiert, OCP

**Status:** ✅ done · **Datum:** 2026-08-14

> **Siehe auch:** [context-architecture.md](context-architecture.md) — die Abgrenzung
> static (System-Prompt) vs dynamic (Chat History) ist dort dokumentiert.
> Diese Doc beschreibt nur die technische Implementierung (ContextItem, AbstractAgent).

## Problem

Aktuell mischen wir drei Dinge ohne klare Typisierung:
- **Static Context** (`String`) — Datum/OS/Regeln, wird an System-Prompt angehängt
- **Static Content Messages** — Dateien (`docs/memory.md`), werden als UserMessage geladen
- **Standing Orders** — Kontext-Strings, werden an UserMessage angehängt

`compactSession()` (Tool) muss heute verstehen, was "persistent" ist — Responsibility Bleed.
System-Prompt rebuild nach Clear fehlt (KV Cache Invalidate + Rebuild).

## Ziel

1. **Agent besitzt den Compact-Ablauf** — Tool ruft nur `agent.compactContext()` auf. Fertig.
2. **Typ-basiert, OCP** — `ContextItem` Interface; Implementierungen (DiskFile/EclipseFile) austauschbar.
3. **System-Prompt rebuild nach Clear** — `systemMessage = null` Guard; nächste Nachricht baut neu.
4. **Standing Orders contains-Check** — Nur injizieren, wenn noch nicht in History.

---

## Interface (Core)

```java
interface ContextItem {
    /** Renders the full text (header + content). Null = nothing to inject → skip. */
    String render();

    /** "Loading 📋" status label — File-Items: voller Pfad. Default: null (lädt still). */
    default String label() { return null; }

    /**
     * Dedup-Identifier, contains-Check über UserMessages, VOR dem Read.
     * File-Items: exakter Header "<voller Pfad>:\n---\n" (System.lineSeparator()) —
     * eine Summary kann den Pfad erwähnen, aber praktisch nie exakt den Header.
     * Default: null → Dedup über den gerenderten Content.
     */
    default String dedupKey() { return null; }
}
```

**Drei Methods** (2026-08-16): `label()` = Pfad für den "Loading 📋"-Status,
`dedupKey()` = Header (mit Trenner) für die Dedup — zwei Größen, zwei Aufgaben.
Der alte Bug: contains-Check auf den bloßen Pfad → False-Positive gegen
Compact-Summaries.

## Implementierungen

```java
// Core — Disk-basiert (headless-testbar)
class DiskFileContextItem implements ContextItem {
    private final Path path;
    @Override String render() {
        return Files.readString(path);
    }
}

// Plugin — Eclipse-VFS-basiert
class EclipseFileContextItem implements ContextItem {
    private final String relativePath;
    private final IProject project;
    @Override String render() {
        IFile file = project.getFile(relativePath);
        return new String(file.getContents());
    }
}
```

## Agent

```java
class AbstractAgent {
    private volatile String systemMessage = null; // null = noch nicht aufgebaut ODER nach clear
    
    // Persistent: Session-Start einmalig an System-Prompt
    private List<ContextItem> persistentContext;
    
    // Turn-Scoped: Pro Turn (und nach Clear) als UserMessage in Memory
    private Supplier<List<ContextItem>> turnContextSupplier;

    // Session-Start
    void injectPersistentContext() {
        //一次性: persistentContext rendern → systemMessage aufbauen
    }

    ChatResponse call(String userMessage, AiMonitor monitor) {
        if (systemMessage == null) {
            systemMessage = buildSystemPrompt(); // Erster Aufruf ODER nach Clear
        }
        // ... call-Logik
    }

    ChatResponse compactContext(AiMonitor monitor) {
        // 1. Komprimierung (via Compressor)
        var summary = compressor.call(memory.getCopy(), monitor);
        
        // 2. Clear
        memory.clear();
        systemMessage = null; // Force rebuild bei nächste Nachricht
        
        // 3. Standing Orders wiederherstellen (contains-Check)
        restoreTurnContext();
        
        // 4. Summary injizieren
        memory.addResult(summary);
        
        return summary;
    }

    void restoreTurnContext() {
        if (turnContextSupplier == null) return;
        for (var item : turnContextSupplier.get()) {
            String text = item.render();
            if (!memory.containsUserMessage(text)) {
                memory.add(UserMessage.from(text));
            }
        }
    }
}
```

## Compact Tool (Delegiert)

Das Tool kennt den Agenten direkt über den `ToolLoopRequest` und delegiert komplett.

```java
class CompactSessionTool {
    @Tool(name = "compactSession")
    String compactSession() {
        // Request wird von AbstractAgent.doCall() mit dem Agenten befüllt
        AiAgent agent = request.getAgent();
        if (agent != null) return agent.compressContext(monitor);
        
        // Fallback (Legacy/Tests ohne Agent)
        var summary = new AiCompressorAgent(request.getChatModel()).call(request.getMemory().getCopy(), monitor);
        request.clearMemory();
        request.getMemory().add(UserMessage.from(summary));
        return summary;
    }
}
```

## BDD

```
GIVEN ein Agent mit persistenten ContextItems (docs/memory.md, docs/index.md)
     und turn-scoped ContextItems (Project, AGENTS.md)
WHEN compactSession aufgerufen (Tool oder UI-Button)
THEN
  1. Memory wird komprimiert (Summary erzeugt)
  2. Memory.clear() wird aufgerufen
  3. systemMessage auf null gesetzt (Force rebuild)
  4. Turn-scoped ContextItems werden wiederhergestellt (contains-Check)
  5. Summary wird in Memory injiziert

GIVEN systemMessage ist null (nach Clear)
WHEN nächste Benutzer-Nachricht gesendet (call())
THEN System-Prompt wird neu aufgebaut (persistent ContextItems gerendert)

GIVEN turn-scoped ContextItems sind bereits in Memory (contains-Check)
WHEN restoreTurnContext aufgerufen
THEN Items werden NICHT doppelt injiziert

GIVEN ein DiskFileContextItem (core)
WHEN render() aufgerufen
THEN Datei-Inhalt vom Dateisystem gelesen

GIVEN ein EclipseFileContextItem (plugin)
WHEN render() aufgerufen
THEN Datei-Inhalt aus Eclipse VFS gelesen
```

## File-Context in der History (SOLL 2026-08-16, ✅ done)

Ersetzt den "Dateien im System-Prompt"-Teil von ADR-0028 → [ADR-0029](adr/0029-file-context-in-history.md).
Auslöser: Crash `RuntimeException: File not found: docs/memory.md` (optionale Datei killte die
Request) + Stale-Projects-Bug (System-Prompt/`lastModified`-Cache überlebten den Projektwechsel).

### Regeln

1. **Datei fehlt → `null` → übersprungen.** `render()` gibt bei fehlender Datei/Projekt `null`
   zurück; es wird nichts injiziert, kein "Loading"-Eintrag, keine Exception.
2. **Dedup nach `dedupKey()`, nie nach Content — Header-Check vor dem Read.** Der Injektions-Check
   prüft **zuerst** nur `dedupKey()` = Header (`<voller Pfad>:\n---\n`) in der History: Header da →
   übersprungen. Der Check ist exakt auf den **Header-String mit Trenner** — eine Compact-Summary
   kann den Pfad *erwähnen*, aber nicht den exakten Header → kein False-Positive (Bugfix
   2026-08-16, vorher: contains-Check auf den bloßen Pfad).
   Die Datei wird **gar nicht erst gelesen** (kein Payload-Load, kein `render()`). Content-Änderungen
   an der Datei lösen **keine** Neu-Injection aus — die Änderungen stehen ohnehin als Tool-Messages
   in der History. Neu injiziert (und dann erst gelesen) wird nur bei **anderem Pfad**
   (Projektwechsel) oder **nach Compact**.
3. **Header = voller Workspace-Pfad.** File-Items rendern als `<voller Pfad>:\n---\n<content>`
   (wie `AgentsMdContextItem`); der "Loading 📋"-Status zeigt denselben Pfad — so ist im Status
   erkennbar, aus welchem Projekt eine Datei kommt.
4. **Datei-Items leben im `turnContextSupplier`.** AGENTS.md + AGENTS-\<agent\>.md (alle Agenten
   inkl. Slaven), Jons `docs/memory.md` + `docs/index.md`, Plan-Datei. Der System-Prompt hält nur
   noch die statischen OS/Date-Regeln.
5. **`lastModified`-Cache fällt weg.** Ein Datei-Read pro Session/Projekt (first turn / nach
   Compact / Projektwechsel) ist billig — der Cache war Over-Engineering und die Quelle des
   Stale-Projects-Bugs.
6. **`docsIndexSeedForFirstMessage` fällt weg.** Redundant — `docs/index.md` kommt jetzt als eigene
   History-Message bei Jons erstem Turn (vor der User-Message).

### BDD

```
GIVEN wir haben eine memory.md bereits einmal eingefügt in den chat
WHEN jon ändert die memory.md
THEN die memory.md wird nicht einfügt, obwohl diese so in der history noch nicht vorhanden ist

GIVEN wir haben eine memory.md noch nicht geladen im chat
WHEN turn beginnt mit user message
THEN die memory.md wird eingefügt
AND wir sehen die onTool Nachricht das diese eingefügt wird
AND die user message wird danach eingefügt

GIVEN die Datei existiert nicht
WHEN turn beginnt
THEN nichts wird injiziert (kein Error, kein Status-Eintrag)
```

## Migration

| Heute | Ziel |
|-------|------|
| `setStaticContext(String)` | `setPersistentContext(List<ContextItem>)` |
| `setStandingOrders(Supplier<String>)` | `setTurnContextSupplier(Supplier<List<ContextItem>>)` |
| `StaticContentLoader` (eigenständig) | Fällt weg — `ContextItem.render()` macht's |
| `AiCompressorAgent` Callback | Fällt weg — Agent macht `clear() + restore()` selbst |
| `compactSession()` Tool weiß über Persistence | Tool delegiert an `ToolLoopRequest.getAgent().compressContext()` |

## Entscheidungen (abgeschlossen)

- **Header:** Ja — `render()` gibt `"<voller Workspace-Pfad>:\n---\n<content>"` zurück. Dient als
  Pfad-Dedup-Marker und Token-Transparenz (SOLL 2026-08-16: voller Pfad statt "Static loaded file <relativ>").
- **Caching:** Nein — `lastModified`-Cache entfernt (SOLL 2026-08-16); Dedup happens in der History
  nach vollem Pfad, nie nach Content.
- **Standing Orders:** `List<ContextItem>` — gleiche Abstraction. Dedup-Check
  (`memory.containsUserMessage(item.dedupKey())` — Files: exakter Header `<pfad>:\n---\n`,
  sonst gerendeter Content bei `dedupKey() = null`); nur einmal injiziert, nie nachträglich
  angepasst (KV Cache!).
