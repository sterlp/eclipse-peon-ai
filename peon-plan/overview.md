# PO-Review: Review Agent "Da Dok" (story/133, b55e477) — FINDBERICHT, KEINE FIXES

## Kontext
Review des fertigen Review-Agent-Features (docs/review-agent.md R1–R6) auf drei Seiten:
Docs↔Code, Test-Honesty, Gaps. Code gelesen (nicht nur gegreppt): AiReviewAgent, AgentService,
PoDelegateTool, AiPoAgent, BuildPoAgentComponent, AgentsMdContextItem, PeonAiService,
review-agent.txt, po-delegation.txt, dev-build-loop.txt + alle relevanten Tests.
**Ergebnis: R1, R2, R5, R6 code-verifiziert ✅. Zwei echte Befunde (F1 hoch, F2 hoch),
ein Docs/Test-Mapping-Regression (F3), Rest kleine Parity-Lücken.**

## Verifiziert ✅ (Docs == Code)
- **R1** AiReviewAgent = AiPlanAgent-Familie 1:1: NAME "Peon-Review" (AiReviewAgent.java:21),
  3 Ctor-Varianten inkl. RAM-only (Faktor) + JSONL-History (stateDir), Tool-Filter
  `super.getToolFilter().and(t -> !t.getTool().isEditTool())`, Slot=PLAN (`planAgentConfig()`,
  `modelConfigFor(PLAN)`), handoverTo → AiDevAgent.NAME, persistent in AgentService-Ctor
  (withDefaultAgent-Zweig: persistentAgents.put(reviewAgent)) — überlebt reloadAgents via
  clearAgents()-Re-Add.
- **R2** PoDelegateTool-Ctor (plan, review, dev, ordersFor); BuildPoAgentComponent baut
  reviewSlave (RAM-only, SLAVE_COMPACT_FACTOR, noPrivilegedTools-Filter), NamedAgent("Da Dok"),
  AiPoAgent(..., List.of(thinka, doc, mek)); Header-Widget: getStatusAgents() → instanceof
  AiPoAgent → getTeam() → Da Boss + 3 Sklaven.
- **R3 (Code-Seite)** reviewPlanAgent/clearReview/compactReview existieren; sticky planPath als
  Feld von PoDelegateTool geteilt zwischen reviewPlanAgent und buildWithDev; AiPoAgent.clear()
  cascadert clearPlan/clearReview/clearDev, compact() analog.
- **R4 (Routing)** po-delegation.txt: Review AUSSCHLIESSLICH über reviewPlanAgent (Schritt 4 +
  Werkzeuge-im-Detail + Kommunikationsliste). **Keine stale talkPlan-Review-Routing mehr** —
  talkPlan ist "Rein beratend: kein Plan geschrieben, kein Code berührt"; dev-build-loop.txt
  ("call planImplemented only once told the review passed") konsistent.
- **R5** read-only: isEditTool-Filter (PlanTool ist KEIN isEditTool → plan*-Write-Tools bleiben
  ✅ gewollt), zusätzlich noPrivilegedTools (WorkspaceMemoryTool/AskUserTool) im Plugin-Wiring;
  review-agent.txt: "Never edit application code".
- **R6** Turn-Context-Supplier generisch über NamedAgent (ordersFor-Lambda: Plan-Ref +
  AgentsMdContextItem.itemsFor(agentName) + WorkspaceMemoryTool). **Strip-Logik verifiziert:**
  AgentsMdContextItem.java:56-61:
  ```java
  private static String resolveAgentKey(String agentName) {
      if (agentName.startsWith("Peon-")) {
          return agentName.substring(5).toUpperCase();   // "Peon-Review" → "REVIEW" → AGENTS-REVIEW.md
      }
      return agentName;
  }
  ```

## Befunde

### F1 (HOCH) — review-agent.txt widerspricht dem Drei-Seiten-SOLL
- review-agent.txt: "The plan file and the code are your only inputs. This is deliberate." +
  Goal "compare the implemented code to the persisted plan" + Intake (Plan + Diff).
- SOLL (po-delegation.txt Schritt 4 + review-agent.md R4): DREI Seiten — Plan↔Code,
  **Docs↔Code, Docs↔Plan**; Jon soll ihm "die Feature-Docs-Pfade" nennen.
- prompt kennt KEINE Feature-Docs (Intake, Checklist 1–6 ohne Docs-Gegencheck).
- prompt kennt den **Mutations-Check-Empfehlung**-Auftrag (po-delegation.txt: "lass dir von
  Da Dok im Review sagen, welche EINE Stelle einen Nachweis verdient") NICHT.
- review-agent.md R4-Prosa behauptet beides („DREI Seiten … + Mutations-Check-Empfehlung"),
  die Prompt-Aufzählung in derselben Regel listet sie nicht → Prompt kann das SOLL nicht
  erfüllen. R4-BDD "liefert Verdict + Chat-Summary, ohne Code zu ändern" wäre grün, der
  Kernzweck des Reviews (SOLL==IST über drei Seiten) aber im Prompt nicht abgebildet.

### F2 (HOCH) — PeonAiServiceTest-Status-Team-Test ist stale/rot
- test_status_agents_are_jons_team_when_po_active:
  `assertEquals(List.of("Da Boss", "Da Thinka", "Da Mek"), uiNames)` — getTeam() liefert jetzt
  4 Einträge (Da Dok) → Assertion MUSS rot sein (Test wurde nach Feature nicht aktualisiert
  oder nie gelaufen). Widerspricht R2 ("Da Dok erscheint im Header-Status-Widget").
- poAgentSlavesStillFilterAskUser: nur List.of("Da Thinka", "Da Mek") — Da Dok ungedeckt
  (Code filtert ihn generisch ✅, aber Test-Parity fehlt).
- Plugin-Tests laufen lassen zur Bestätigung (erster Lauf braucht Workspace-Trust, Regel 13).

### F3 (MITTEL) — R3-Test-Mapping in review-agent.md ist falsch
- Mapping-Zeile R3: "PoDelegateToolTest (sticky-path + clear/compact-Tests)".
- Realität: die 2 sticky-path-Tests existieren ✅ (reviewPlanAgent_reusesStickyPlanPath,
  reviewPlanAgent_withoutStickyPath_injectsNothing); **clear/compact-Tests existieren NICHT**
  (Grep clearPlan/compactPlan/clearReview/compactReview über alle Core-Tests: 0 Treffer).
- R3-BDD "GIVEN Jon ruft clear() … THEN auch Da Doks Memory wird geleert" ist ungetestet
  (AiPoAgentTest hat keinen Cascade-Test; PoDelegateTool hat keinen getReviewSlave-Accessor).

### F4 (NIEDRIG) — R1-Persistenz nur indirekt (Counts) getestet
- AgentServiceTest.hasDefaultAgent (3) / loadsAgentsAutomatically (4) +
  ReloadConfigToolTest "Agents: 3 loaded" — Updates 2→3/3→4 sind **legitim** (echtes
  Verhaltens-/Zähl-Änderung, dritter persistenter Agent) und wären ohne Feature ROT ✅.
  Aber: keine Assertion nennt "Peon-Review" beim Namen (Grep: 0 Treffer in Tests) —
  `extracting(AiAgent::getName).contains("Peon-Review")` wäre die ehrliche Form.
- enablesHistoryForPlanDevAndCustomOnly: Name/Contract "PlanDevAndCustomOnly" stimmt nicht
  mehr ganz — Review persistiert (stateDir-Ctor) ebenfalls, wird im Test nur nicht berührt.
  Kein Lügner, aber Namens-Drift.

### F5 (NIEDRIG) — Da-Dok-Parity in Integration-Tests fehlt
- test_static_context_reaches_jons_slaves, test_slaves_getAgentSpecificMdInTurnContext,
  test_po_slaves_cannot_write_memory, test_staticContext_isEnvOnly (assertSame-Block):
  prüfen nur Plan-/Dev-Sklaven. AGENTS-REVIEW.md-Auflösung ist code-verifiziert, aber
  ungetestet (AgentsMdContextItemTest: nur Peon-Plan/Peon-Dev).
- Kosmetik: PoDelegateTool-Javadoc sagt noch "The two slaves" (drei sind es).

## Test-Honesty — direkte Antwort
Tests, die ohne Feature rot wären: AgentServiceTest.hasDefaultAgent/loadsAgentsAutomatically
(Counts), ReloadConfigToolTest.reloadConfigReportsCounts, PoDelegateToolTest (kompiliert nicht
ohne 4-arg Ctor; die 2 Review-Sticky-Tests wären rot). Assertion-Massage: NEIN — die
Count-Updates sind legitime Verhaltensänderung. F2 ist das Gegenstück: ein Bestandstest, der
durch das Feature rot wurde und nicht nachgezogen wurde.

## Empfehlungen (Triage durch User, Umsetzung durch Da Mek — NICHT Teil dieses Reports)
1. review-agent.txt: Feature-Docs-Intake + Drei-Seiten-Checkliste + Mutations-Check-Empfehlung
   ergänzen (an po-delegation.txt Schritt 4 angleichen).
2. PeonAiServiceTest: Status-Team auf ["Da Boss","Da Thinka","Da Dok","Da Mek"] korrigieren;
   Filter-Test um Da Dok erweitern.
3. clear/compact-Tests ergänzen (PoDelegateTool: clearReview/compactReview + Cascade via
   AiPoAgent) ODER review-agent.md-Mapping korrigieren — docs-first: Mapping lügt heute.
4. Optional: contains("Peon-Review")-Assertion; Da-Dok-Parity in den Kontext-Tests.

## Offene Fragen
- Keine — F1/F2 sind IST≠SOLL, F3 ist ein falsches Docs-Mapping; alles andere optional.
