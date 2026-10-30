# Review Agent

**Ziel:** Reviews des PO-Zyklus laufen nicht mehr durch den Plan-Agenten, sondern durch einen
dritten RAM-Sklaven des Jon: **Review** (Agent-ID `Review`) — strukturell wie der Plan-
Agent (gleiche Ctor/Config/Filter-Familie), nur mit eigenem System-Prompt für saubere Reviews:
frischer Context, keine Memory der Implementierung, Verdict-basiert (ACCEPTED / CONCERNS /
REJECTED). Basis: ADR-0020 (PO-Orchestrierung, superseded-Status durch diesen Agenten erweitert),
Delegation über [PoDelegateTool](../../docs/adr/0020-po-agent-orchestration.md).

**Begriffe:** Review · Review-Agent — siehe [Glossar](../../docs/glossary.md).

## Business Rules

### Agent & Wiring

- **R1 ✅** `AiReviewAgent` ist ein built-in persistent Agent (`Review`), registriert in
  `AgentService` neben Dev/Plan/Scaffold — strukturell 1:1-Familie mit `AiPlanAgent`
  (gleiche Ctor-Varianten inkl. RAM-only + JSONL-History, read-only Tool-Filter `!isEditTool`,
  Config/Model-Slot = PLAN, Handover → Dev).
  - GIVEN AgentService mit Default-Agenten WHEN getAgents() THEN Review ist persistent
    enthalten (überlebt reloadAgents)
- **R2 ✅** Der PO hält Review als dritten RAM-Sklaven: `PoDelegateTool`-Ctor
  `(plan, review, dev, ordersFor)`, `getTeam()` = `List.of(thinka, mek, doc)` — Header-Reihenfolge
  Jon · Plan · Dev · Review (User-Entscheid 2026-09-09, Lifecycle plan→build→review).
  - GIVEN PeonAiService gebaut WHEN getTeam() THEN drei NamedAgents: Plan, Dev, Review
- **R3 ✅** Delegate-Tools: `reviewPlanAgent(prompt, planPath?)` (frischer Context, stickiger
  Plan-Pfad geteilt mit buildWithDev), `clearReview()`, `compactReview()` — Spiegel von
  clear/compactPlan/Dev; `AiPoAgent.clear()/compact()` cascadieren alle drei Sklaven.
  - GIVEN Jon ruft clear() WHEN Cascade läuft THEN auch Reviews Memory wird geleert

### Review-Verhalten (Prompt getrieben)

- **R4 ✅** Der Review läuft über `reviewPlanAgent` an Review — **nicht** mehr über `talkPlan` an
  Plan (`po-delegation.md` Schritt 4). Er meldet Abweichungen und **repariert nichts**.
  Verdict ACCEPTED/CONCERNS/REJECTED, Chat-Summary zuletzt.
- **R8 ✅ (2026-10-28, Paul; prompt-basiert, kein BDD) — Maßstab: leicht wartbar, leicht änderbar, keine Überraschungen.**
  Der Prompt ist rollen-neutral (keine Team-Namen — Review läuft auch allein) und arbeitet auch
  **ohne Plan oder ohne Docs**. Prüft: Bugs · Docs↔Code (wenn Docs da) · Plan↔Code (wenn Plan da) ·
  Docs↔Plan-Konflikt (Docs sind Maßstab) · überflüssige Kommentare statt Doc-Verweis · schlechte
  Testbarkeit · schlechte Kapselung, Wiederholung (≥3×), Modul-Zyklen · verschmierte Struktur
  (Änderung braucht viele Module). Diese sind **BUG** (→ REJECTED); Überkomplexes ist **LOW** mit
  einfacherer Lösung; Nitpicks/Stil werden ignoriert. Ein Durchgang meldet **alle** BUGs (kein
  Abbruch beim ersten); BUG nur für geänderten/neuen Code, Altlast als nicht-blockierende Liste
  „Pre-existing"; Verhaltens-BUG nur mit Beleg (sonst „Verdacht"), Tests des Moduls laufen mit
  Befehl + Zahlen; BUGs nach Wirkung sortiert (Verhalten/Daten vor Struktur). Inhalt: Prompt
  `review-agent.md`.
  - GIVEN Dev meldet fertig WHEN Jon reviewPlanAgent aufruft THEN Review liefert Verdict +
    Chat-Summary, ohne Code zu ändern
- **R5 ✅** Review ist **read-only im Code**: Write-Tools ausgenommen, nur plan*-Tools zum Schreiben
  (Plan-Datei korrigieren bei legitimen Abweichungen), nie Application Code, nie Docs. **Ausnahme
  Shell** ([agent-tool-filter.md](agent-tool-filter.md) R-TF-1): Diagnose/git/Build/Test erlaubt —
  Code-Änderungen via Shell bleiben Regelbruch (soft rule).
- **R6 ✅** Kontext wie alle Sklaven: Turn-Context-Supplier (gewähltes Projekt + AgentsMd-Basis —
  Agent-File-Konvention `AGENTS-REVIEW.md`, `Peon-`-Präfix wird gestrippt) + Workspace-Memory-Snapshot
  über die generische `ordersFor`-Lambda.

### Config

- **R7 ✅ (2026-10-14) — Review bekommt einen eigenen Config-Bereich (Model-Selection-Slot):**
  statt geteilter PLAN-Slot (`Config/Model-Slot = PLAN`, R1) erhält Review einen eigenen Slot
  `REVIEW` (Store `llm.agent.review.*`, Aufnahme in `CORE_IDS`), UI-Sektion auf der
  Advanced-Seite. Fallback = **Base** (wie PO nach [ADR-0036](../../docs/adr/0036-po-own-model-slot.md)),
  nicht mehr Plan.
  - **Migration-Hinweis (User):** standen die Review-Werte bisher im PLAN-Slot, müssen sie nach
    dem Update einmal in die REVIEW-Sektion kopiert werden — ein leerer REVIEW-Slot erbt Base,
    nicht mehr Plan (kein stiller Alias).
  - GIVEN REVIEW-Slot leer WHEN Da-Dok-Model aufgelöst THEN Base-Config (nicht PLAN)
  - GIVEN REVIEW-Slot gefüllt WHEN Da-Dok-Model aufgelöst THEN REVIEW-Werte gewinnen
  - GIVEN PLAN-Slot gefüllt WHEN REVIEW leer THEN Plan-Werte fließen NICHT ein (Clean Break)


## Out of Scope / Hinweise

- AGENTS-REVIEW.md ist User-Konfiguration (Projekt), kein Feature-Doc-Gegenstand.
- ADR-0020 bleibt inhaltlich (Orchestrierung-Muster), der Drei-Sklaven-Stand ist hier dokumentiert.

## BDD-Test-Mapping

| Regel | Test |
|---|---|
| R1 | `AgentServiceTest.hasDefaultAgent` (named: enthält Review) |
| R2 | `PoDelegateToolTest` (3 NamedAgents im Ctor) · `PeonAiServiceTest.test_status_agents_are_jons_team_when_po_active` (Header: Jon, Plan, Dev, Review) |
| R3 | `PoDelegateToolTest.clearReview_wipesReviewSlaveMemoryOnly` / `compactReview_compactsReviewSlaveMemory` · `AiPoAgentTest.clear_cascadesToAllThreeSlaves` / `compact_cascadesToAllThreeSlaves` · Sticky-Path: `reviewPlanAgent_reusesStickyPlanPath` |
| R4–R6, R8 | Prompt/Verhalten — manuelle Verifikation (kein Unit-Test, wie Prompts-Konvention) |
