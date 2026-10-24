# Proposal: `agent_artifact` — Agentengebundene Artefakte (Plan, Task, Handover)

**Status:** 🚧 design  
**Ziel:** Eine eigene Komponente `agent_artifact` im core, die strukturierte, agentengebundene Markdown-Dateien verwaltet: Pläne, Tasks und Handovers. Baut auf `PeonFile` auf, nutzt dieselben Restrictions und ist Jimfs-testbar.

---

## 1. Motivation

Heute ist der Plan (`peon-plan/overview.md`) ein einzelner Anker-Dokument. Bei größeren Features wird er lang, der Kontext der einzelnen Agenten (Dev, Review) wächst mit, und Handovers passieren implizit über Chat.

**Neue Idee:**
- **Plan** (`overview.md`) bleibt der allgemeine Anker: Architektur, Kontext, globale Regeln.
- **Tasks** sind einzelne, kleine Dateien pro Arbeitsschritt — der Dev-Agent arbeitet nur mit dem relevanten Task, nicht mit dem ganzen Plan. Das hält den Kontext klein.
- **Handovers** sind strukturierte Übergabe-Dateien zwischen Agenten — kein implizites Chat-Gedächtnis mehr.

**Vorteil:** Der Plan-Agent darf Tasks erstellen (wie heute schon `overview.md` als Anker gedacht war), der Dev-Agent arbeitet task-fokussiert, Jon behält die Kontrolle über alle Artefakte.

---

## 2. Konzept: `AgentArtifact`

### 2.1 Typen

| Typ | Zweck | Ersteller | typische Leser |
|---|---|---|---|
| `PLAN` | Allgemeiner Anker: Architektur, Kontext, globale Regeln | Jon / Plan-Agent | alle |
| `TASK` | Einzelner, abgeschlossener Arbeitsschritt | Plan-Agent (oder Jon) | Dev, Review |
| `HANDOVER` | Strukturierter Übergabe zwischen zwei Agenten | beliebiger Agent | Ziel-Agent, Jon |

### 2.2 Identifikation

```java
public enum ArtifactType { PLAN, TASK, HANDOVER }

public record ArtifactId(ArtifactType type, String agentName, String slug) {
    // Dateiname: <TYPE>__<agent>__<slug>.md
    // Beispiel: TASK__dev__fix-login.md
}
```

- **`agentName`** = der Agent, der das Artefakt **besitzt** (Ersteller bzw. aktuell Verantwortlicher).
- **`slug`** = kurzer, sprechender Name (kein Datum, keine UUID — Lesbarkeit schlägt Eindeutigkeit hier).
- **Kollision:** `ArchiveName.firstFreeName` wie heute — bei Namenskollision wird ein Suffix angehängt.

### 2.3 Pflicht-Frontmatter

Jedes Artefakt hat einen maschinenlesbaren Header:

```markdown
---
type: TASK
agent: dev
status: in-progress
created: 2026-10-24T15:30
---
```

- **`type`** = `PLAN | TASK | HANDOVER` (Enum, validiert)
- **`agent`** = besitzender Agent (validiert gegen bekannte Agent-Namen)
- **`status`** = Typ-spezifisch (siehe unten)
- **`created`** = ISO-Timestamp

**Regel:** Frontmatter ist die maschinenlesbare Wahrheit, der Dateiname nur der schnelle Überblick. Der Store validiert Konsistenz (Dateiname ↔ Frontmatter).

---

## 3. Status-Maschine (einheitlich, mit euren Icons)

Alle drei Typen nutzen **dieselbe** Status-Maschine — konsistent mit euren bestehenden Docs-Icons:

| Status | Icon | Bedeutung |
|---|---|---|
| `design` | 🚧 | Noch in Diskussion, nicht voll spezifiziert |
| `specified` | ❌ | Design vereinbart und aufgeschrieben, noch nicht umgesetzt (Backlog) |
| `done` | ✅ | Vom Dev-Agenten umgesetzt, mit grünem BDD-Test |

**Warum einheitlich?** Kein zweites Vokabular für Tasks vs. Feature-Docs — ein Agent (oder Jon) sieht am Icon sofort, wo er steht, ohne die Status-Semantik neu lernen zu müssen.

**Übergänge:**
- `design → specified`: Plan/Task ist ausdiskutiert, wartet auf Umsetzung.
- `specified → done`: Dev hat umgesetzt + BDD-Test grün.
- `done → archived`: Datei wird umbenannt (`-done-<stamp>`), aus dem aktiven Bestand raus.

**Für HANDOVER** gilt dieselbe Maschine: `design` = Entwurf, `specified` = bereit zur Übergabe, `done` = vom Ziel-Agenten übernommen und archiviert.

---

## 4. Handover — sicher über Tooling

### 4.1 Tool-Operation

```java
@Tool("Hand over work to another agent with a structured summary. Creates a HANDOVER file — the actual agent switch happens via the UI/delegation mechanism.")
public void handover(
    @P("target agent name") String targetAgent,
    @P("structured summary: completed, open issues, next steps, files touched, traps") String summary) {
    var id = new ArtifactId(HANDOVER, currentAgent, slug(targetAgent));
    store.create(id, handoverTemplate(currentAgent, targetAgent, summary));
    // KEIN automatischer Agent-Wechsel — das übernimmt das bestehende "handover"-Feature im UI
}
```

**Entscheidung (deine Vorgabe):** Das Tool legt **nur die Datei** an. Der tatsächliche Agent-Wechsel läuft über das bestehende UI-„Handover“-Feature — im Agent-Mode geht alles über Jon, der entscheidet.

### 4.2 Pflicht-Template

```markdown
## Completed
- …

## Open Issues
- …

## Next Steps
- …

## Files Touched
- …

## Traps / Gotchas
- …
```

**Warum Pflicht?** Claude-Code-Best-Practices betonen: „name the files changed, checks run, known limits, and follow-ups“ — ein Handover ohne diese Struktur ist ein Anti-Pattern [web:24]. Das Template erzwingt die Vollständigkeit.

### 4.3 Wer darf Handovers schreiben?

**KISS-Entscheidung (deine Vorgabe):** Jeder Agent darf Handovers erstellen (für sich selbst → an einen anderen). Jon darf zusätzlich alle lesen, archivieren und (bei Bedarf) überschreiben. Keine feingranulierten Rollen — das wäre Overengineering.

---

## 5. Plan ↔ Task-Beziehung

**Der Plan bleibt der Anker, Tasks sind die Ausführung:**

```
peon-plan/
├── PLAN__jon__auth-refactor.md          ← allgemeiner Anker (Architektur, Regeln)
├── TASK__dev__implement-login.md        ← konkreter Schritt 1
├── TASK__dev__implement-logout.md       ← konkreter Schritt 2
├── TASK__review__check-auth-flow.md     ← Review-Aufgabe
└── HANDOVER__dev__review-handoff.md     ← Übergabe Dev → Review
```

**Regel:**
- **Plan-Agent erstellt Tasks** (wie heute `overview.md` als Anker gedacht) — er zerlegt den Plan in ausführbare Einheiten.
- **Dev-Agent arbeitet nur mit dem relevanten Task** (`planRead` liest nur den Task + bei Bedarf den Plan-Anker) — Kontext bleibt klein.
- **Jon sieht alle Tasks** via `listByType(TASK)` und behält die Kontrolle.

**`planImplemented` prüft offene Tasks:**

```java
@Tool("Archive the plan. Warns if open TASKs exist.")
public void planImplemented() {
    var openTasks = store.listByType(TASK).stream()
        .filter(t -> t.status() != Status.DONE)
        .toList();
    if (!openTasks.isEmpty()) {
        onProblem("Warning: " + openTasks.size() + " open TASKs remain — " + openTasks);
        // bewusst NICHT blockieren — Jon entscheidet
    }
    // … Archivierung wie heute
}
```

**Entscheidung (deine Vorgabe):** Ja, prüfen + warnen, aber nicht blockieren — Jon behält die Entscheidung.

---

## 6. Vergleich: Wie andere Tools das lösen

### Claude Code

- **Plan Mode:** Trennt Recherche/Planung von Implementierung — Plan wird als Datei persistiert, überlebt Compaction/Session-Restarts [web:17][web:29].
- **Subagent-Handoffs:** Best Practice ist „clear goal, input, output, handoff rule“ pro Subagent — strukturierte Übergabe statt Chat-Gedächtnis [web:22].
- **Community-Muster:** Es gibt Skills wie `task-handoff`, die genau das machen: ein Agent plant, ein anderer führt aus, Übergabe erfolgt über Dateien, nie direkt [web:25].
- **Status-Tracking:** Claude-Code-Workflows nutzen explizite Plan-Dateien mit Status, die über Sessions hinweg bestehen [web:29].

**Parallele zu uns:** Dein PLAN/TASK/HANDOVER-Split bildet genau dieses Muster ab — Plan = Anker, Tasks = Ausführungseinheiten, Handover = strukturierte Übergabe.

### OpenCode

- **Permissions:** `allow` / `ask` / `deny` pro Tool + Pfad-Pattern — Restriktionen sind deklarativ, nicht hardcoded [web:3][web:7].
- **Tool-Hooks:** `tool.execute.before` / `after` um die eigentliche Ausführung — Audit, Undo, Sandbox-Checks hängen sich ein [web:10].
- **Kein Sandbox per Default:** OpenCode verzichtet bewusst auf OS-Sandboxing, verlässt sich auf Permission-Regeln [web:5][web:14].

**Parallele zu uns:** Dein `PeonFileRestriction`-Konzept (ALLOW/ASK/DENY) ist direkter verwandt mit OpenCodes Permission-Modell als mit Claude Codes implizitem Trust-Modell.

### Andere Tools (Cursor, Aider, etc.)

- **Aider:** Nutzt `.aider*`-Dateien für Konventionen/Handoffs, aber kein strukturiertes Task-System.
- **Cursor:** `.cursorrules` für Konventionen, aber keine expliziten Handover-Dateien.
- **Devin/Cognition:** Nutzt strukturierte Plan-/Task-Dateien ähnlich deinem Vorschlag — Plan als Anker, Tasks als Ausführungseinheiten.

**Fazit:** Dein Ansatz (flache Dateien, Präfix-basierte Typen, Pflicht-Frontmatter, einheitliche Status-Maschine) ist **konservativer und robuster** als viele der genannten Tools — kein Verzeichnis-Drift, kein zweites Vokabular, Jimfs-testbar.

---

## 7. Paketstruktur

```
org.sterl.llmpeon.core/
└── src/main/java/org/sterl/llmpeon/agent_artifact/
    ├── ArtifactType.java          // Enum: PLAN, TASK, HANDOVER
    ├── ArtifactId.java            // Record: type + agent + slug
    ├── ArtifactStatus.java        // Enum: DESIGN, SPECIFIED, DONE
    ├── AgentArtifact.java         // Model: id + frontmatter + content
    ├── AgentArtifactStore.java    // Interface: CRUD + list + archive
    ├── DiskAgentArtifactStore.java    // Default: auf PeonFile/Disk
    ├── InMemoryAgentArtifactStore.java // Test: Map-basiert
    └── AgentArtifactTool.java     // @Tool-Methoden (planRead/Save/Update, taskCreate, handover, …)

org.sterl.llmpeon/  (Plugin)
└── src/org/sterl/llmpeon/parts/agent_artifact/
    └── EclipseAgentArtifactStore.java  // Adapter: PeonFile → IFile/IFolder + refreshLocal
```

**Abhängigkeit:** `AgentArtifactStore` nutzt `PeonFile` (aus dem `file`-Package) — keine eigene I/O-Logik, Restrictions kommen von `PeonFileRestriction`.

---

## 8. Blast Radius

### Neu (core)

| Datei | Zweck |
|---|---|
| `agent_artifact/ArtifactType.java` | Enum |
| `agent_artifact/ArtifactId.java` | Record |
| `agent_artifact/ArtifactStatus.java` | Enum (design/specified/done) |
| `agent_artifact/AgentArtifact.java` | Model |
| `agent_artifact/AgentArtifactStore.java` | Interface |
| `agent_artifact/DiskAgentArtifactStore.java` | Default-Impl |
| `agent_artifact/InMemoryAgentArtifactStore.java` | Test-Impl |
| `agent_artifact/AgentArtifactTool.java` | @Tool-Methoden |

### Neu (core tests, Jimfs)

| Datei | Zweck |
|---|---|
| `agent_artifact/AgentArtifactStoreTest.java` | CRUD, list, archive, Frontmatter-Validierung |
| `agent_artifact/AgentArtifactToolTest.java` | Tool-Verhalten (planRead/Save, taskCreate, handover) |

### Neu (Plugin)

| Datei | Zweck |
|---|---|
| `parts/agent_artifact/EclipseAgentArtifactStore.java` | Adapter |
| `test/EclipseAgentArtifactStoreTest.java` | Adapter-Test (minimal) |

### Geändert

| Datei | Änderung |
|---|---|
| `parts/tools/PlanTool.java` | Ersetzt durch `AgentArtifactTool` (oder delegiert daran) |
| `parts/ai/PeonAiService.java` | Wiring: `AgentArtifactStore` + Restrictions |
| `docs/plan-tool.md` | Ersetzt/erweitert durch `agent-artifact.md` |
| `org.sterl.llmpeon.core/docs/index.md` | Neuer Eintrag |
| `docs/adr/` | Neuer ADR: „AgentArtifact — agentengebundene Artefakte“ |

### Unverändert

- `disk*`/`eclipse*`-Tools — bleiben.
- `SkillService`, `CommandService`, `AgentService` — keine Berührung.
- Bestehende `overview.md`-Dateien — Migration optional (Backlog), nicht Teil dieses Inkrements.

---

## 9. Offene Fragen

1. **Q1:** Soll `planRead` für den Dev-Agenten nur den Task lesen, oder Task + Plan-Anker? *(Empfehlung: Task primär, Plan bei Bedarf via explizitem zweiten Aufruf.)*
2. **Q2:** Sollen Tasks eine `assignee`-Erweiterung bekommen (abweichend vom Besitzer-Agenten)? *(Empfehlung: erst nicht — KISS, Besitzer = Verantwortlicher.)*
3. **Q3:** Soll es ein `listByStatus` geben (z. B. „alle offenen Tasks“), oder reicht `listByType` + Filter im Agent? *(Empfehlung: `listByStatus` als Convenience — Jon fragt oft danach.)*
4. **Q4:** Sollen bestehende `overview.md`-Dateien automatisch migriert werden? *(Empfehlung: nein — Backlog, manuelle Migration bei Bedarf.)*

---

## 10. Nächste Schritte

1. **Feedback zu diesem Proposal** einholen (insbesondere Q1–Q4).
2. **`PeonFile`-Komponente** zuerst bauen (siehe separates Proposal) — `agent_artifact` baut darauf auf.
3. **`agent_artifact` als eigenes Inkrement** danach — nicht mit `PeonFile` mischen.
