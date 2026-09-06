# `.peon` Config Directory — Shared Config vs. Runtime State

## Goal

`~/.peon` (`configDir`, User-Home; übersteuerbar per `llm.configDirectory`) hält **ausschließlich
shared config** — Inhalte, die sich alle Eclipse-Instanzen desselben Users teilen dürfen. Runtime
State hat dort **nichts verloren**.

**WEIL (User, 2026-09-06):** Mit State in der geteilten Config kann keine zweite Eclipse-Instanz
unabhängig laufen — Totalschaden-Szenario ( interleaved Writes, Last-Writer-Wins, Delete unter der
laufenden Instanz).

## IST (verifiziert 2026-09-06, Recherche)

| Artifact | Ort | Klassifikation | Bewertung |
|---|---|---|---|
| Skills | `~/.peon/skills/**` | Shared Config | ✅ ok |
| Commands | `~/.peon/commands/**` | Shared Config | ✅ ok |
| Custom Agents | `~/.peon/agents/<name>/AGENT.md` | Shared Config | ✅ ok |
| **Agent-History** | `~/.peon/state/<AgentName>-history.jsonl` (`FileAgentHistoryStore`: `append`/`persist`/`clear`) | **Runtime State** | ❌ Verstoß gegen R1 |

History ist **pro Agent**, aber weder pro Workspace noch pro Projekt. Zwei Eclipse-Instanzen
kollidieren direkt in dieser Datei: `append()` interleaved/verstümmelt Zeilen, `persist()`
(tmp + atomic move) = Last-Writer-Wins, `clear()` löscht unter der anderen Instanz weg, korrupte
Zeile → `load()` deletet die ganze Datei (Datenverlust beider Instanzen).

Nicht in `.peon`: LLM-Config + per-Agent-Slots + Workspace-Memory (Eclipse Instance-Preferences),
Token-Counter + Model-Cache (RAM), Plan (`<project>/peon-plan/`), Docs (`<project>/docs/`).

## SOLL

### R1: `.peon` = shared config only ✅

In `configDir` (`~/.peon`) darf **nur shared config** liegen: Agent-Definitionen (`AGENT.md`),
Skills, Commands — alles, was der Scaffold-Agent verwaltet. **Kein** Runtime State (History,
Session-State, Counter) darf dorthin geschrieben werden.

- **GIVEN** Peon speichert Agent-History **WHEN** der Ablageort bestimmt wird **THEN** liegt sie
  NICHT unter `configDir`
- **GIVEN** eine zweite Eclipse-Instanz läuft parallel **WHEN** beide Instanzen Agenten nutzen
  **THEN** stören sie sich nicht gegenseitig (kein geteilter State-Dateipfad)

### R2: Agent-History = Runtime State, workspace-scoped ✅

Die History ist Laufzeit-**State des Eclipse-Workspace** (Eclipse garantiert per Workspace-Lock
genau eine Instanz → Totalschaden-Szenario konstruktiv weg). Zielort:

```
.metadata/.plugins/org.sterl.llmpeon/state/<agent>-history.jsonl
```

- **Pfad-Quelle:** `Platform.getStateLocation(bundle)` — der Bundle-Metadata-Container, kein
  handgebauter Pfad. Steht nur im **Plugin-Layer** zur Verfügung; Core bleibt headless und
  behält `configDir/state` als Default (Layer-Injektion wie beim Slave-Factory-Muster).
- **GIVEN** zwei Projekte in einem Workspace **WHEN** beide Agenten-History schreiben **THEN**
  teilen sie eine History (workspace-scoped, nicht projekt-scoped — bewusst)
- **GIVEN** Plugin-Start **WHEN** `~/.peon/state/` existiert mit State-Dateien **THEN**
  Migration (R3) läuft, Agent-Histories laden danach aus dem Metadata-State
- **GIVEN** headless Core (Tests/CLI) **WHEN** Store gebaut wird **THEN** weiter
  `configDir/state` (kein Eclipse-Dependency im Core)

### R3: One-Shot-Migration `.peon/state` → Workspace-Metadata ✅

Beim Plugin-/Service-Start verschiebt Peon eventuell vorhandene `~/.peon/state/**`-Dateien
(alle: Built-in- **und** Custom-Agent-Histories) per **File-I/O** in den Metadata-State.

- **GIVEN** `~/.peon/state/Dev-history.jsonl` existiert, Metadata-State hat keine Datei **WHEN**
  Peon startet **THEN** die Datei wird dorthin **verschoben** (move; Cross-Volume-Fallback:
  copy + delete), Original ist weg → Migration ist idempotent (nächster Start: Quelle leer, no-op)
- **GIVEN** Ziel-Datei existiert bereits (Partiel-Migration/Konflikt) **WHEN** Migration läuft
  **THEN** Quelle bleibt unangetastet, Migration meldet den Skip (kein stiller Datenverlust)
  — ⏳ Rückversicherung User: Ziel gewinnt
- **GIVEN** `~/.peon/state/` existiert nicht **WHEN** Peon startet **THEN** kein no-op-Fehler,
  Migration übersprungen
- **GIVEN** Migration scheitert (Rechte/IO) **WHEN** Peon startet **THEN** Log-Warnung, Start
  läuft mit leerem State weiter (Migration blockiert nie den Start)

**Homepage:** Anpassung ist **Inkrement-Pflicht** (Shared-Config-Sektion + History-Pfad +
Migrations-Hinweis) — gehört ins gleiche Inkrement wie der Store-Umzug.

## Side Quest (bestätigt, eigene Story später)

Jon bekommt den Scaffold-Agenten als Sklaven/Delegate → er kann nach jedem Zyklus Skill-/Config-
Anpassungen beauftragen. Richtung steht in [po-agent-jon.md](po-agent-jon.md) Future Extensions
(`jonAskScaffold`).
