# Session-Stand — 2026-10-14 (Zyklus 3: Agent-Tool-Config + Da-Dok-Slot) — ABGESCHLOSSEN

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Zyklus 3 — ✅ abgeschlossen (Branch `story/agent-tool-config`)

Alle 6 Inkremente + Homepage-Doku + Docs-Flips + Plan-Archiv committed (`2100bdc8`…`69d907f4`, Docs-Flips `17755c01`, Plan-Archiv `0b0a9b4d`, Preview-(MCP)-Suffix-Fix `9365e90b`). Tests: Surefire core **1110/0/0**, OSGi **339/0/0**, Lint UC-ATC 7/7, findings 0. Da-Dok-Review: CONCERNS nur Homepage-Gap (geschlossen via `69d907f4`), Abweichungen 1–8 legitim (Plan §12).

**Paul-Smoke R-ATC-8 Preview-Dialog ✅ 2026-10-14.** Rückversicherungen 🔒 (2026-10-14): Standalone-Review behält memory*/askUser · Compact kein Tools-Feld (Q1) · R-SEL-4 alle 3 Details · Compact-Doc-Split (CIB).

Docs: [agent-tool-config.md](agent-tool-config.md) R-ATC-1…9 ✅ · [review-agent.md](review-agent.md) R7 ✅ · [scaffold-agent.md](scaffold-agent.md) R8 ✅ · [ADR-0065](adr/0065-tool-policy-negation.md) · [ADR-0066](adr/0066-review-agent-own-model-slot.md) · index.md aktualisiert.

## Offen

1. **Docs-Hygiene (Paul-Order 2026-10-14):** open-points.md aufgeräumen — gelöste Punkte nach [resolved-points.md](resolved-points.md) (Abschnitt „Aus open-points.md aufgeräumt"), Wertloses gelöscht, Rest komprimiert. ✅ 2026-10-14.
2. **Merge branch→main** = Pauls Entscheidung; Release-Notes schreibt Jon beim Merge (neue Datei, alte gelöscht — Paul-Freigabe).
3. **Paul-Smoke (alt):** R-AEM (terminale Fehler → 1× History, Abbruch → keine Insertion) + R-TD-5 (`… (4s, 19:54) (1234 chars)`).
4. **Danach (Paul-Order): Context-Overflow/ContentProvider Bug-Zyklus** — Thinka 954k, Mek 431005 vs. n_ctx 170240, webFetch 500k. Logs nur mit searchAgent (`.metadata/.log`, GROSS). Passende offene Punkte: Auto-Compact bei Overflow, ApiRetry-Klassifikation, R-CC-7, Context-Pollution-Quellen.

## Notiert

- Paul baut parallel Async-Interface — Merge-Zonen (AbstractAgent.doCall, BuildPoAgentComponent, UI-Pages) beim Merge gegenprüfen.
- AGENTS.md-Ambiguität „Homepage-Doku: Dev-Inkrement vs. PO-Docs-Zyklus" → Merge-Gate präzisieren (Da-Dok-Empfehlung, Plan §12) — Backlog.
- Da-Mek-Deviation (6 Inkremente in 2 Build-Calls statt 6 Einzel-Calls) von Da Dok als legitim verifiziert; künftig Einzel-Dispatch.

## Backlog (Auswahl)

AGENTS-Trim (wartet Paul-GO, inkl. totem „komponenten-architektur"-Link AGENTS.md:43) · TrimService-Story (⏳, nächster Sprint) · Think-BDD-Lücken (a)–(g) · per-Agent-Provider-Override (❓) · Linter ☠️-Status (❓) · Homepage think-Werte-Mismatch · Homepage peon-po.md ohne Da Dok · Vollständige Liste: [open-points.md](open-points.md).
