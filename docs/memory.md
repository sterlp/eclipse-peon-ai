# Session-Stand — 2026-10-14 (Zyklus 3: Agent-Tool-Config + Da-Dok-Slot) — BUILD ABGESCHLOSSEN

> Achtung: docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts
> im Chat, Jon schreibt. (Da Mek hatte 2026-09-27 memory.md überschrieben — nicht wiederholen.)

## Zyklus-Zustand (Paul-Order, 2026-10-13) — Stories ✅ gebaut & committet

**Branch `story/agent-tool-config`, alle 6 Inkremente done & committed:**

- inc1 `2100bdc8` — ToolPolicy `!`-Negation (allow-then-deny, nur-Negationen = alle minus Ausschluss, bare-`!` no-op, blank-Filter vor nur-Negationen)
- inc2 `d6dc28ac` — REVIEW-Slot (CORE_IDS, Fallback Base, AiReviewAgent 4× PLAN→REVIEW, Advanced-Section)
- inc3 `0625fd66` — Store+Wiring (`llm.agent.<id>.tools` raw, per-Call-Read, Slot-Overrides, SearchAgentTool belegt beide Filter explizit; Abweichungen a) Wildcard-Tests verhärtet b) MapLlmConfigStore→public — Da-Dok-legitim)
- inc4 `d918b34a` — UI-Felder (Basic+Advanced, Compact bewusst KEIN Feld — Q1 🔒 Paul 2026-10-14) + R-ATC-9-Validierung (performOk verweigert, Store unverändert)
- inc5 `0c1059e8` — Preview-Button (getippter Wert, filterFor-Equivalenz Preview↔Runtime bewiesen)
- inc6 `544c6d1f` — Scaffold-Prompt `!`-Semantik (Full-Example-Variante optional NICHT ergänzt — „nur wenn Paul will")
- `69d907f4` — Homepage-Tools-Doku (configuration.md `### Tools` + advanced-configuration.md `## Tool allowlist`; schließt Da-Dok-Gap)

**Tests:** Surefire core **1110/0/0**, OSGi **339/0/0**. **Lint:** UC-ATC 7/7, findings 0.
**Da-Dok-Zyklus-Review:** CONCERNS (nur Homepage-Gap, inzwischen geschlossen); Abweichungen 1–8 alle legitim dokumentiert (Plan §12); Mutation-Canaries 4/4 vorhanden; Architektur (EIN Matcher, per-Call-Read, Schnittmenge) + Blast Radius verifiziert.

**Docs-Flips (2026-10-14, Jon):** agent-tool-config.md Status + R-ATC-1…9 → ✅ · review-agent.md R7 → ✅ · scaffold-agent.md R8 → ✅ · index.md aktualisiert. Q1 (Compact-Kein-Feld) 🔒 im Doc.

## Offen (bis Merge)

1. **Commit Docs-Flips** — Jon committet docs/** (agent-tool-config.md, review-agent.md, scaffold-agent.md, index.md, memory.md) selbst auf dem Branch.
2. **planImplemented** (Da Mek) — Plan archivieren + Archive-Commit, NACH dem Docs-Commit.
3. **Paul-Smoke (neu):** R-ATC-8 Preview-Dialog-Inhalt (→ [open-points.md](open-points.md)).
4. **Merge branch→main** = Pauls Entscheidung; neue Release-Notes schreibt Jon beim Merge (alte Datei gelöscht, Paul-Freigabe).
5. **Deviation-Report an Paul:** Da Mek baute 6 Inkremente in 2 Build-Calls (inc1–2, inc3–6) statt 6 Einzel-Calls — Vereinbarung war „so klein wie möglich"; Da Dok alle Abweichungen als legitim verifiziert.

## Ausstehend aus vorherigen Zyklen (unverändert)

- **Paul-Smoke:** R-AEM (terminale Fehler → 1× History, Abbruch → keine Insertion) + R-TD-5 (`… (4s, 19:54) (1234 chars)`).
- **Merge branch→main** = Pauls Entscheidung (Branch `bug/grep-empty-payload-test` mit Merge `d5c87548`).
- **Danach (Paul-Order): Context-Overflow/ContentProvider Bug-Zyklus** — Thinka 954k, Mek 431005 vs. n_ctx 170240, webFetch 500k. Logs nur mit searchAgent (`.metadata/.log`, GROSS).
- Paul baut parallel Async-Interface — Merge-Zonen (AbstractAgent.doCall, BuildPoAgentComponent, UI-Pages) beim Merge gegenprüfen.

## Backlog (Auswahl)

AGENTS-Trim (wartet Paul-GO, inkl. totem „komponenten-architektur"-Link AGENTS.md:43) · TrimService-Story (⏳) · R-CC-7 + ApiRetry-Fehlerklassen-Tabelle (❓) · Header-State-Leak · Think-BDD-Lücken (a)–(g) · per-Agent-Provider-Override (❓) · Linter ☠️-Status (❓) · Homepage think-Werte-Mismatch · Homepage peon-po.md ohne Da Dok · Compact-Button ohne Monitor (❓) · Auto-Compact bei Overflow (❓) · Custom-Agent-UI mit Write-Back (Phase 2, Out of Scope) · AGENTS.md-Ambiguität „Homepage-Doku: Dev-Inkrement vs. PO-Docs-Zyklus" → Merge-Gate präzisieren (Da-Dok-Empfehlung, Plan §12).
