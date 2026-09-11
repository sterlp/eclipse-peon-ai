# Glossar — Ubiquitous Language

**Ziel:** Ein Begriff, eine Bedeutung — in Docs, im Code, in den Prompts und im Chat.

**WEIL (User, 2026-09-03):** Synonyme sind für Menschen harmlos und für LLMs teuer. Wo ein
Mensch „Slot", „Feld" und „Setting" mühelos als dasselbe liest, erzeugt ein Agent drei
Konzepte, sucht nach dem falschen, findet nichts und rät. Jeder Begriffsdrift kostet einen
Tool-Call und manchmal eine falsche Entscheidung.

**Regeln:**
1. Steht ein Begriff hier, wird **genau dieser** benutzt — auch als Klassen-/Feld-/Package-Name.
2. Neuer fachlicher Begriff → **erst** hier eintragen, dann im Code verwenden.
3. Ein Feature-Doc heißt wie sein Package heißt wie sein Ordner (bestehende Doc-Regel).
4. „Verboten" heißt: nicht in neuem Code/Doc verwenden. Altbestand wird bei Berührung angeglichen.

## Domäne — Agenten

| Begriff | Bedeutung | Nicht verwenden |
|---|---|---|
| **Peon-PO** / **Jon** | Der Business-Owner-Agent; besitzt `docs/` (das SOLL), orchestriert Plan + Dev | PO-Agent, Owner, Boss |
| **Peon-Plan** / **Da Thinka** | Plan-Agent; schreibt ausschließlich `peon-plan/overview.md`, berät verbal | Planner, Architekt |
| **Peon-Dev** / **Da Mek** | Dev-Agent; schreibt ausschließlich Code | Developer, Coder, Sklave (nur informell) |
| **Da Sniffa** | Such-Agent (`searchAgent`), read-only, zustandslos, einmalig | Research-Agent, Scout |
| **Da Dok** | Review-Agent (`reviewPlanAgent`), dritter PO-RAM-Sklave (`Peon-Review`), read-only im Code, Verdict ACCEPTED/CONCERNS/REJECTED | Reviewer, QA-Agent, Peon-Review (nur als Agent-ID) |
| **Da Scribe** | Compact-Agent (`compactSession`) | Compressor, Summarizer |
| **Custom Agent** | Nutzerdefinierter Agent aus `AGENT.md` mit Frontmatter | User-Agent, eigener Agent |

## Domäne — Arbeitsweise

| Begriff | Bedeutung | Nicht verwenden |
|---|---|---|
| **SOLL** | Der Zielzustand — lebt **ausschließlich** in `docs/` | Spec, Requirement, To-Be |
| **IST** | Der gebaute Zustand — lebt im Code | As-Is, Status quo |
| **Story** | Ein Feature-Doc `docs/<feature>.md` mit Zielsatz + Business Rules | Ticket, Issue, Epic |
| **Business Rule** | Nummerierte Regel (`R1`, `R-PO2`) mit genau einem Status-Marker | Anforderung, Constraint |
| **BDD** | GIVEN/WHEN/THEN, je auf **einen** Testnamen abgebildet | Akzeptanzkriterium, Szenario |
| **ADR** | Technische Entscheidung in `docs/adr/NNNN-<slug>.md` (Status · Context · Decision · Consequences) | Design-Doc, RFC |
| **Inkrement** | Kleinste vertikale Einheit inkl. Tests, für sich grün, `inc-N` | Task, Step, Etappe |
| **Zyklus** | Ein kompletter Durchlauf Plan → Abnahme → Build → Review → Retro | Sprint, Iteration |
| **Plan** | Genau eine Datei: `peon-plan/overview.md` — die dauerhafte Übergabe an Da Mek | Planning-Doc, Konzept |
| **Fixture** | Das versionierte Testprojekt `test_project` | Testdaten, Sample-Projekt |

**Status-Marker** (genau einer pro Regel und pro Feature):
🚧 in design · ❌ specified · ✅ done. Für offene Punkte zusätzlich: ❓ offen · ⏳ selbst
entschieden · 🔒 geklärt.

## Domäne — Konfiguration & Verbindung

| Begriff | Bedeutung | Nicht verwenden |
|---|---|---|
| **Base-Config** | Die Grundeinstellung auf der Basic-Preference-Page | Default-Config, globale Config, Haupt-Config |
| **Agent-Config** | Der Satz Einstellungen **eines** Agenten (`llm.agent.<id>.*`) auf der Preference-Page | Agent-Slot, Agent-Settings, Profil, Sektion |
| **Model-Selection** | Die pro Agent/Verwendung **gewählte** Model-Config — im Code `AgentModelConfig` (DEV/PLAN/COMPACT/PO, `CORE_IDS`); Auflösung: Agent-Config, sonst Base ([ADR-0036](adr/0036-po-own-model-slot.md), Dateiname historisch) | Model-Slot, Slot, Agent-Slot |
| **Model Config** | Der Wertetyp `AgentModelConfig` (model · url · apiKey · think · extraBody · temperature) | Modell-Einstellungen |
| **Effective Connection** | Die aufgelöste Verbindung eines Agenten (Agent-Config, sonst Base) | resolved config, aktive Verbindung |
| **Connection Identity** | Der Cache-Schlüssel Provider + URL + Key (+ Body nur build-time) | Connection-Hash, Verbindungs-Key |
| **extra body** | Das per-Agent-JSON, das in den Request gemerged wird (User gewinnt) | JSON-Body, Zusatz-Parameter, custom body |
| **Provider** | Eine `AiProvider`-Implementierung (je Provider eine Klasse) | Backend, LLM-Anbieter |
| **unset** | Leerer Wert = Parameter wird **nicht** gesendet (kein Default) | null, leer, deaktiviert |
| **Auto-Detect (MCP)** | Leere MCP-Protocol-Version: langchain4j sondiert — modern (`2026-07-28`) zuerst, Legacy-Fallback (`2025-11-25`) auf derselben Verbindung | Detect-Tanz, Auto-Erkennung |
| **Dual-Era-Server** | MCP-Server (Python mcp SDK 2.x), dessen Ära (modern/legacy) der **erste** Request entscheidet; missglückte Probe + Legacy-Fallback auf derselben Verbindung = Era-Lock → `-32022` | Versions-Konflikt |

## Domäne — Kontext

| Begriff | Bedeutung | Nicht verwenden |
|---|---|---|
| **Static Context** | Der System-Prompt-Anteil: Env (Datum, OS, Datei-Regeln). **Kein** Projekt, kein Memory | System-Kontext, Preamble |
| **Turn Context** | Pro Turn in die History gehängt: gewähltes Projekt, Workspace-Memory, Dateien | dynamischer Kontext, Laufzeit-Kontext |
| **ContextItem** | Der Typ, der beides trägt (`render()`, dedupKey) | Kontext-Block, Snippet |
| **Compact** | Verdichten der History durch Da Scribe | Compress, Zusammenfassen, Summarize |
| **Workspace Memory** | Der projektlokale Merkspeicher (memory*-Tools) | Guidelines, Notizen |

## Domäne — Tools

| Begriff | Bedeutung | Nicht verwenden |
|---|---|---|
| **Tool-Familie** | `eclipse*` (Workspace/VFS) vs. `disk*` (echtes Dateisystem) | Tool-Gruppe, Toolset |
| **Clamp** | Einen Bereich auf gültige Grenzen begrenzen statt zu eskalieren | kappen, abschneiden, truncate |
| **Literal-Fallback** | Suche als Text, wenn die Query kein gültiges Regex ist | Plain-Suche, Text-Suche |
| **Falsch-Negativ** | Tool meldet „nicht gefunden", obwohl es existiert — der teuerste Tool-Fehler | leeres Ergebnis |
| **Log-Auszug** (`LogExcerpt`) | Gefilterter und geklemmter Ausschnitt eines Logs samt Header, der gezeigt/gesamt benennt | Log-Snippet, Tail, Ausschnitt |
| **Refresh-Ziel** | Der Container, der bei leerem Ergebnis synchronisiert wird — **nicht** dasselbe wie der Such-Scope | Refresh-Scope, Sync-Bereich |
| **Config-Skills** | Skills aus dem geteilten Config-Dir (`~/.peon/skills`) — Basis, in jedem Projekt sichtbar | globale Skills, Basis-Skills |
| **Projekt-Skills** | Skills aus `<Projekt>/.agents/skills` (Disk-Pfad) — nur bei gewähltem Projekt sichtbar, **Override** bei Namenskollision | lokale Skills, Project-Skills |
| **Skill-Component** | Eine vom `SkillService` gehaltene Component mit **immer einem Pfad** (Config- oder Projekt-Skills-Dir); lädt Discover+Parse+Swap selbst — Service tauscht Projekt-Component als Instanz, kein `@Nullable` dir | Skill-Slot (alt), Skill-Dir, Skill-Root |

## Offen

- Die Tabellen sind bewusst unvollständig. Neue Begriffe kommen bei Berührung dazu, nicht auf
  Vorrat.
- **Eager Load 🔒 (2026-09-11):** (a) nur Jon — ADR: [resolved-points.md](resolved-points.md).
