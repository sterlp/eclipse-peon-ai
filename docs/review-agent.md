# Review Agent (Da Dok / Peon-Review)

**Ziel:** Reviews des PO-Zyklus laufen nicht mehr durch den Plan-Agenten, sondern durch einen
dritten RAM-Sklaven des Peon-PO: **Da Dok** (Agent-ID `Peon-Review`) — strukturell wie der Plan-
Agent (gleiche Ctor/Config/Filter-Familie), nur mit eigenem System-Prompt für saubere Reviews:
frischer Context, keine Memory der Implementierung, Verdict-basiert (ACCEPTED / CONCERNS /
REJECTED). Basis: ADR-0020 (PO-Orchestrierung, superseded-Status durch diesen Agenten erweitert),
Delegation über [PoDelegateTool](adr/0020-po-agent-orchestration.md).

**Begriffe:** Da Dok · Review-Agent · Peon-Review — siehe [Glossar](glossary.md).

## Business Rules

### Agent & Wiring

- **R1 ✅** `AiReviewAgent` ist ein built-in persistent Agent (`Peon-Review`), registriert in
  `AgentService` neben Peon-Dev/Peon-Plan/Scaffold — strukturell 1:1-Familie mit `AiPlanAgent`
  (gleiche Ctor-Varianten inkl. RAM-only + JSONL-History, read-only Tool-Filter `!isEditTool`,
  Config/Model-Slot = PLAN, Handover → Peon-Dev).
  - GIVEN AgentService mit Default-Agenten WHEN getAgents() THEN Peon-Review ist persistent
    enthalten (überlebt reloadAgents)
- **R2 ✅** Der PO hält Da Dok als dritten RAM-Sklaven: `PoDelegateTool`-Ctor
  `(plan, review, dev, ordersFor)`, `getTeam()` = `List.of(thinka, mek, doc)` — Header-Reihenfolge
  Da Boss · Da Thinka · Da Mek · Da Dok (User-Entscheid 2026-09-09, Lifecycle plan→build→review).
  - GIVEN PeonAiService gebaut WHEN getTeam() THEN drei NamedAgents: Da Thinka, Da Mek, Da Dok
- **R3 ✅** Delegate-Tools: `reviewPlanAgent(prompt, planPath?)` (frischer Context, stickiger
  Plan-Pfad geteilt mit buildWithDev), `clearReview()`, `compactReview()` — Spiegel von
  clear/compactPlan/Dev; `AiPoAgent.clear()/compact()` cascadieren alle drei Sklaven.
  - GIVEN Jon ruft clear() WHEN Cascade läuft THEN auch Da Doks Memory wird geleert

### Review-Verhalten (Prompt getrieben)

- **R4 ✅** Der Review läuft über `reviewPlanAgent` an Da Dok — **nicht** mehr über `talkPlan` an
  Da Thinka (`po-delegation.txt` Schritt 4). Er prüft DREI Seiten (Plan↔Code, Docs↔Code, Docs↔Plan)
  + Mutations-Check-Empfehlung, meldet Abweichungen und **repariert nichts**. Prompt
  `review-agent.txt`: Verdict ACCEPTED/CONCERNS/REJECTED, Intake (Plan + Diff vollständig),
  Checklist (Completeness/Correctness/Duplication-Rule-of-Three/Deviations/Tests/Skill-Gaps),
  Plan-Update bei legitimen Abweichungen, Chat-Summary zuletzt.
  - GIVEN Da Mek meldet fertig WHEN Jon reviewPlanAgent aufruft THEN Da Dok liefert Verdict +
    Chat-Summary, ohne Code zu ändern
- **R5 ✅** Da Dok ist **read-only im Code**: nur plan*-Tools zum Schreiben (Plan-Datei korrigieren
  bei legitimen Abweichungen), nie Application Code, nie Docs.
- **R6 ✅** Kontext wie alle Sklaven: Turn-Context-Supplier (gewähltes Projekt + AgentsMd-Basis —
  Agent-File-Konvention `AGENTS-REVIEW.md`, `Peon-`-Präfix wird gestrippt) + Workspace-Memory-Snapshot
  über die generische `ordersFor`-Lambda.

## Out of Scope / Hinweise

- AGENTS-REVIEW.md ist User-Konfiguration (Projekt), kein Feature-Doc-Gegenstand.
- ADR-0020 bleibt inhaltlich (Orchestrierung-Muster), der Drei-Sklaven-Stand ist hier dokumentiert.

## BDD-Test-Mapping

| Regel | Test |
|---|---|
| R1 | `AgentServiceTest.hasDefaultAgent` (named: enthält Peon-Review) |
| R2 | `PoDelegateToolTest` (3 NamedAgents im Ctor) · `PeonAiServiceTest.test_status_agents_are_jons_team_when_po_active` (Header: Da Boss, Da Thinka, Da Mek, Da Dok) |
| R3 | `PoDelegateToolTest.clearReview_wipesReviewSlaveMemoryOnly` / `compactReview_compactsReviewSlaveMemory` · `AiPoAgentTest.clear_cascadesToAllThreeSlaves` / `compact_cascadesToAllThreeSlaves` · Sticky-Path: `reviewPlanAgent_reusesStickyPlanPath` |
| R4–R6 | Prompt/Verhalten — manuelle Verifikation (kein Unit-Test, wie Prompts-Konvention) |
