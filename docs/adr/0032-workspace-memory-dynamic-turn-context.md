# ADR-0032 — Workspace-Memory: dynamisch im Turn-Context (ersetzt den statischen Snapshot)

**Status:** Implemented (2026-08-23) — gebaut, Review bestanden; NICHT committed
**Revision (2026-08-23, User):** der statische Memory-Snapshot aus [ADR-0031](0031-static-context-env-plus-memory.md)
wird **komplett entfernt** — Memory lebt nur noch dynamisch (dieses ADR). Static Context = nur noch Env.
Begründung: seit dem dynamischen Item ist der Snapshot reine Duplikation (jeder Eintrag doppelt im
Kontext), und das KV-Cache-Argument ist entwertet — bei jeder Memory-Änderung bricht der Turn-Anteil
den Cache ohnehin. Umsetzung: Code durch den User.

## Context

Das Workspace-Memory liegt als Snapshot im statischen System-Prompt ([ADR-0031](0031-static-context-env-plus-memory.md)).
Mid-session-Änderungen (auch durch Jon selbst) wirken erst beim nächsten Re-Bake
(clear/compact/updateConfig/Reload) — und Jons RAM-Sklaven (Da Thinka/Da Mek) sehen neue Einträge
gar nicht, ohne dass `initStaticContext()` erneut läuft. Der Reload-Callback-Hack
(PeonAiService.java:170, „TODO this is a bug") existiert genau deshalb. User-Befund: „das Memory
wird nicht dynamisch gebaut".

## Decision

AGENTS.md-Muster auch fürs Memory: der Snapshot im System-Prompt **bleibt** (KV-Cache-Stabilität
für den Grundstock), aber `WorkspaceMemoryTool` wird zusätzlich zum `ContextItem`, das pro Turn
über den `turnContextSupplier` (aktiver Agent) bzw. den Orders-Supplier des Delegate-Tools
(`PoDelegateTool`; Sklaven, drittes Item nach Plan + AGENTS.md) in die nächste Nachricht gerendert
wird. Dedup über festen Key, geänderter Inhalt → neuer Snapshot; alte Snapshots bleiben bis zum Compact.

Regeln + BDD: [context-architecture.md](../../org.sterl.llmpeon.core/docs/context-architecture.md) (Abschnitt „Workspace-Memory
zusätzlich dynamisch").

## Consequences

+ Neue Memory-Einträge erreichen aktive Agenten **und** Sub-Agenten sofort — kein Re-Bake, kein
  Config-Change, kein Callback-Hack als Frische-Quelle.
+ Konsistent mit ADR-0029 (AGENTS.md): ein Mechanismus, ein Muster.
− Mehr Context: Snapshots sammeln sich bis zum Compact (bewusst akzeptiert, User 2026-08-23).
− KV-Cache bricht bei Memory-Änderung für den Turn-Teil — dort ohnehin append-only.
