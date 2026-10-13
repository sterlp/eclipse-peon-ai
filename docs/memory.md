# Session-Stand — 2026-10-13 (Zyklus 3: Agent-Tool-Config + Da-Dok-Slot)

> Achtung: docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts
> im Chat, Jon schreibt. (Da Mek hatte 2026-09-27 memory.md überschrieben — nicht wiederholen.)

## Zyklus-Ziel (Paul-Order, 2026-10-13)

**Beide Stories auf EINEM eigenen Branch bauen** (Name: `story/agent-tool-config`), Plan → Abnahme
→ Build → Review → Retro, Inkremente so klein wie möglich, **Docs nach jedem Inkrement nachziehen**.
Paul baut PARALLEL sein Async-Interface — falls der Umbau die Agent-Konstruktion anfasst, STOP-AND-ASK.

1. **Agent-Tool-Config** — ❌ specified ([agent-tool-config.md](agent-tool-config.md), [ADR-0065](adr/0065-tool-policy-negation.md)): Tool Allowlist (CSV) pro Agent, ein Matcher (`ToolPolicy`) für Built-ins (UI) + Custom (Frontmatter), Negation `!` (allow-then-deny; **nur Negationen = alle minus Ausschluss**), keine Inheritance (Dev-Filter bleibt Dev), Schnittmenge mit Klassen-Filter, Preview-Button, Scaffold R8.
2. **Da-Dok-Eigen-Slot** — ❌ specified ([review-agent.md](review-agent.md) R7, [ADR-0066](adr/0066-review-agent-own-model-slot.md)): eigener REVIEW-Slot (`llm.agent.review.*`, CORE_IDS + Advanced-UI), Fallback = Base statt Plan, Clean Break, Release-Notes-Erwähnung + Homepage-Doku im selben Inkrement.

## Review-Order (Paul, 2026-10-13)

- Da Dok reviewt jeden Build — **Schwerpunkte: Architektur sauber + Blast Radius passt** (nicht nur Regel-zu-Test).
- Bei Unsicherheit: Da Dok konsultieren (talkPlan-ähnlich über reviewPlanAgent), Entscheidungen gegenprüfen lassen.
- Inkremente minimal, Docs (Jon) nachziehen nach JEDEM Inkrement — nicht erst am Zyklusende.

## Ausstehend aus vorherigen Zyklen (unverändert)

- **Paul-Smoke:** R-AEM (terminale Fehler → 1× History, Abbruch → keine Insertion) + R-TD-5 (`… (4s, 19:54) (1234 chars)`).
- **Merge branch→main** = Pauls Entscheidung (Branch `bug/grep-empty-payload-test` mit Merge `d5c87548` ist committed; Docs-Commits inkl. release-notes-2026-10.md).
- **`planImplemented`** (Archiv) für offene Pläne — nach dem Merge.
- **Danach (Paul-Order): Context-Overflow/ContentProvider Bug-Zyklus** — Thinka 954k, Mek 431005 vs. n_ctx 170240, webFetch 500k. Logs nur mit searchAgent (`.metadata/.log`, GROSS).

## Backlog (Auswahl)

AGENTS-Trim (wartet Paul-GO, inkl. totem „komponenten-architektur"-Link AGENTS.md:43) · TrimService-Story (⏳) · R-CC-7 + ApiRetry-Fehlerklassen-Tabelle (❓) · Header-State-Leak · Think-BDD-Lücken (a)–(g) · per-Agent-Provider-Override (❓) · Linter ☠️-Status (❓) · Homepage think-Werte-Mismatch · Homepage peon-po.md ohne Da Dok · Compact-Button ohne Monitor (❓) · Auto-Compact bei Overflow (❓) · Custom-Agent-UI mit Write-Back (Phase 2, agent-tool-config Out of Scope).

## Nächste Schritte (dieser Zyklus)

1. ✅ SOLL komplett: agent-tool-config.md + review-agent.md R7 + ADR-0065/0066.
2. **clear auf alle Agenten** (Thema ist neu) → Branch `story/agent-tool-config` anlegen (Da Mek).
3. planWithPlanAgent: beide Stories planen (kleine vertikale Inkremente, je grün, Docs-Nachzug je Inkrement vorgesehen).
4. Abnahme (Jon) → buildWithDev Inkrement für Inkrement → Docs nachziehen → Da-Dok-Review je Stand (Architektur + Blast Radius).
