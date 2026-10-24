# ADR-0041 — `.peon` ist shared-config-only; Agent-History ist Runtime State

**Status:** Accepted (SOLL; Umsetzung offen)

**Context:** Agent-History liegt als `~/.peon/state/<AgentName>-history.jsonl` im geteilten
Config-Verzeichnis — pro Agent, aber weder pro Workspace noch pro Projekt. Zwei Eclipse-Instanzen
(kollegiales Parallelopen desselben Projekts) kollidieren: `append()` interleaved, `persist()`
(tmp+atomic-move) = Last-Writer-Wins, `clear()` löscht unter der laufenden Instanz weg, korrupte
Zeile → `load()` deletet die Datei. User-Szenario „zweite Eclipse unabhängig vom ersten" ist damit
unbenutzbar (Totalschaden).

**Decision:** `configDir` (`~/.peon`) ist **shared-config-only** — Agent-Definitionen, Skills,
Commands, alles was der Scaffold-Agent verwaltet. Runtime State (Agent-History) verlässt `.peon`
und ist **workspace-scoped**: `.metadata/.plugins/org.sterl.llmpeon/state/<agent>-history.jsonl`
via `Platform.getStateLocation()` (Plugin-Layer; Core headless behält `configDir/state` als
Default, Layer-Injektion). Kein State im Projekt-Verzeichnis als Ausweg — dasselbe Projekt in
zwei Instanzen kollidiert erneut. **Keine Migration:** der zwischenzeitlich gebaute One-Shot-Move
aus `~/.peon/state` ist 2026-10-19 ersatzlos entfernt worden (Clean Break, Paul) — eine
Vor-2.x-History wird nicht übernommen, der Agent startet leer.

**Consequences:**
- `FileAgentHistoryStore` bekommt den State-Pfad injiziert statt ihn aus `configDir` abzuleiten.
- `~/.peon` ist wirklich config-only; ein zurückgebliebenes `state/` aus einer Vor-2.x-Installation
  wird ignoriert und darf vom User gelöscht werden.
- Skills/Commands/AGENT.md bleiben geteilt — das ist gewollt (eine Pflege, alle Instanzen).
- Zwei Eclipse-Instanzen (verschiedene Workspaces) sind wieder voll unabhängig.
- 2026-10-23: Der Skills-Default zieht auf `~/.agents/skills` um (APM-Kompatibilität) —
  `.peon/skills` bleibt deprecated **gelesen und schreibbar** (Support), Empfehlung (Homepage-Warnung
  + Scaffold) ist `.agents`: User-Root oder Projekt ([project-skills.md](../../org.sterl.llmpeon.core/docs/project-skills.md) R17/R10).
  Commands/Agenten bleiben vorerst in `.peon`.
- Homepage nennt den History-Pfad; einen Migrations-Hinweis gibt es seit dem Entfall nicht mehr.
