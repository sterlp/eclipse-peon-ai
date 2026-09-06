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
zwei Instanzen kollidiert erneut. Migration: One-Shot-Move von `~/.peon/state/**` beim Start
(idempotent, Cross-Volume-Fallback copy+delete, Ziel-existiert → Quelle bleibt + Skip-Meldung,
Fehler blockiert den Start nie). Kein Migration-Chain-Gedöns — ein Zielort, ein Move.

**Consequences:**
- `FileAgentHistoryStore` bekommt den State-Pfad injiziert statt ihn aus `configDir` abzuleiten.
- `~/.peon/state/` wird nach erfolgreicher Migration leer; der Ordner bleibt nicht zurück,
  `.peon` ist danach wirklich config-only.
- Skills/Commands/AGENT.md bleiben geteilt — das ist gewollt (eine Pflege, alle Instanzen).
- Zwei Eclipse-Instanzen (verschiedene Workspaces) sind wieder voll unabhängig.
- Homepage-Anpassung (History-Pfad + Migrations-Hinweis) ist Teil des Inkrements.
