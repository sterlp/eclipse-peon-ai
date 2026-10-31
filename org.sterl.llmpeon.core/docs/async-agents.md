# Async Agents (Erstentwurf)

> **Status:** 🚧 in design (2026-10-16, Nachtzyklus 5 — Jon-Review von Pauls Erstentwurf, **WIP by Paul**,
> Code untracked unter `org.sterl.llmpeon.core/.../agent/tools/` + `agent/model/`) · Verwandt:
> [Async Agent Tools Proposal](async-agent-tools-proposal.md) (Option C, Queue-basiert — empfohlen) ·
> [Tools-Result-Tags (4.1)](#41--toolsresult-tags-huckepack) · [PO-Delegation](po-agent-jon.md)

## Entwurf im Überblick (gelesen 2026-10-16)

- **`AgentsTool`** (agent/tools): `spawn(agentName, prompt)` — synchron sofort, oder asynchron auf
  Virtual Thread, wenn `maxConcurrent > asyncRunning`. Result: `Task { id, agent, prompt, future,
  status, result, e }`. Tools: `getTaskStatus(taskId)`, `waitForTask(taskId, timeout?)`,
  `subAgentClear(agentName)`, `subAgentCompact(agentName)`. Ein großer Kommentarblock (listAgents /
  spawnAgent / snapshots) ist auskommentiert.
- **`AsyncMonitor`** (agent/tools): `AiMonitor`-Wrapper um den Root-Monitor — cached `lastMessage`
  + Zeit, eigener `canceled`-Flag, `isCanceled()` delegiert an Root.
- **`ToolsResult`** (agent/model, siehe 4.1): getaggte Hülle `//-- Start <head> … //-- End` um
  Tool-Result-Bodies.

## Bewertung (Jon)

**Richtung stimmt** — das löst das Kernproblem aus async-agent-tools-proposal (sync Tools blockieren
die Queue) auf Tool-Ebene und passt zur Option-C-Empfehlung. Virtual Threads statt Executor-Pools sind
die richtige Wahl. Als **grober Entwurf** trägt er; fünf Punkte sind m. E. vor einem ersten Build zu
klären:

1. **Dead-Counter:** `asyncRunning` wird **nirgends** inkrementiert/dekrementiert — `runsAsync` ist
   faktisch immer `maxConcurrent > 0`. Entweder Zähler richtig führen (try/finally um dispatch),
   oder bewusst weglassen und `maxConcurrent > 0` als Schalter dokumentieren.
2. **Gleicher Agent + Nebenläufigkeit:** zwei Tasks auf denselben `AiAgent` teilen sein Memory/
   History — interleaved Corruption ist vorprogrammiert. Empfehlung: **Lock pro Agent** (ein laufender
   Task je Agent, Rest queue) statt freie Nebenläufigkeit — das passt auch zum Verhalten der heutigen
   Slaven (RAM-only, seriell).
3. **Turn-Context-Supplier bleibt gesetzt:** `dispatch` setzt `setTurnContextSupplier(() -> orders)`
   mit ewig leerer Liste — der Slave behält diesen Stale-Supplier auch für spätere Sync-Calls.
   Reset nach Task-Ende oder Supplier pro Call.
4. **Kein Cancel, kein Cleanup:** kein `taskCancel`, `tasks` wächst ungebunden, `Task.status` wird
   auch bei Fehler auf `COMPLETED` gesetzt (FAILED ergibt sich nur aus `e != null` — Semantik
   muddelt), und es ist undefiniert, was mit einem laufenden Async-Task passiert, wenn der
   Parent-Turn endet (orphan). Orphan-Semantik muss explizit sein (mein Vorschlag: Task läuft
   weiter, Ergebnis kommt beim nächsten Parent-Turn als getaggte Nachricht „huckepack" — siehe 4.1).
5. **`AsyncMonitor.lastMessage/lastMessageTime` sind tot** (niemand liest sie) und Async-Streaming
   geht verloren: der Parent-Chat sieht nur start/done-Zeilen. Für MVP ok — aber dann Felder raus
   (AGENTS: „a guard that never fires is worse than none").

## Top-3-Empfehlungen (die Fragen an Paul)

1. **Ergebnis-Zustellung:** Nur Poll (`getTaskStatus`/`waitForTask`) für MVP — oder gleich Huckepack
   (abgeschlossene Async-Results als getaggte Nachricht in den nächsten Parent-Turn injizieren,
   braucht Queue-Infrastruktur = Option C)? **Meine Empfehlung:** MVP = Poll + Huckepack in einem
   zweiten Schritt; die Tag-Infrastruktur (4.1) ist dafür die Voraussetzung und lohnt sowieso.
2. **Nebenläufigkeitsmodell:** Ein laufender Task je Agent + Queue (sicher, deckt „Fortschritt
   sichtbar machen" trotzdem ab) vs. freie Virtual-Thread-Nebenläufigkeit mit Agent-Klonen (teuer,
   History-Split). **Meine Empfehlung:** Lock pro Agent.
3. **Verhältnis zu `PoDelegateTool`:** `subAgentClear`/`subAgentCompact` duplizieren die
   PoDelegateTool-Fähigkeiten; langfristig ein Pfad je Verantwortung. Entweder AgentsTool wird die
   generische Basis und PoDelegateTool delegiert drauf, oder die Actions bleiben beim PO-Tool.
   **Meine Empfehlung:** Entscheidung erst bei Huckepack/Option C — MVP nicht doppelt bauen.

## 4.1 — ToolsResult-Tags (Huckepack)

Pauls Idee: getaggte Ergebnisse — ein fester Tag (oder XML-Tags) um Tool-Result-Bodies, damit das
LLMerkennen kann „das ist ein Tool-Result, keine Nutzereingabe"; Compact kann getaggte Spans
behandeln; und Async-Results reisen als getaggte Nachricht „huckepack" mit der nächsten User-/Tool-
Nachricht (System-Prompt-Info nötig).

**Bewertung: ja — mit drei Präzisierungen.**

1. **Format:** Fester Tag ist ok, aber ich empfehle XML-artige Tags mit Attribut-Kopf
   (`<peon-result kind="tool|sub-agent" agent="…" task="…">…</peon-result>` statt `//-- Start`).
   Warum: (a) eindeutig parsebar trotz Code-Inhalten, (b) trägt Metadaten (Agent, Task-ID) — genau
   was Huckepack braucht, (c) beide großen Provider trainieren XML-Tag-Struktur. Die `//--`-Form
   kollidiert leichter mit echtem Code (Shell-Kommentare!). Eine Konstante im core („eine Bedeutung,
   ein Tag") wie `ToolsResult.PREFIX` — aber final und mit Definitionsstelle in EINEM Typ.
2. **Compact-Kooperation:** Ausschließen über Tags als **String-Scan im Compressor ist fragil** —
   besser: Tag wird bei der Nachrichtenkonstruktion gesetzt (ContextItem/RenderOptions-Metadatum)
   und der Trimmer behandelt es typisiert (z. B. Async-Result-Spans „behalten, kürzen auf Kopf+Ende"
   statt full exclude — volles Ausschließen verliert Daten dauerhaft). Der Tag bleibt im Output
   sichtbar, damit Disclosure-Regeln greifen.
3. **System-Prompt:** Zustimmung — die Tag-Semantik gehört in den Static-Context (einmal, kurz),
   nicht in jedes Tool-Description. Erst wenn Tags wirklich „immer" gesetzt werden (Pauls Idee
   „nehmen wir das immer"), gilt: Mehr-Token-Kosten je Nachricht gegen LLM-Klarheit abwägen —
   Messwert-Frage (Token-Delta messen), kein Bauchgefühl.

Kleinigkeiten am `ToolsResult`-Entwurf: `INFO` ist `public static` **non-final** (mutabel) → final
oder weg; `toString()` ohne Null-Guards für body; LN/Naming ok (Trim-Konzept, literal separator
bewusst — memory 36).

## Offen (Paul, morgen)

- Q1–Q3 oben; 4.1-Formatfrage (XML-artig vs. `//--`); MVP-Scope (Poll-only vs. Huckepack im ersten Slice).
- Code bleibt untracked (Paul: „nur die neuen files nicht") bis das SOLL festgezurrt ist.