# Session-Stand — 2026-09-19 (Tag: Tool-Evolution PO-Run + Build-Start)

## Wo wir stehen

**Branch `analysis/tool-evolution`** — Docs der Auflösung müssen noch committed werden (Da Mek).

**PO-Run abgeschlossen (Paul):** alle CR-Items entschieden. Verdicts + Begründungen: [resolved-points.md](resolved-points.md)
(„Tool-Evolution-Run"). Temp-Docs (tool-evolution.md, feature-change-request-copilot.md, change-review.md) gelöscht.

**❌ specified, wartet auf Build-Zyklen (Reihenfolge-Vorschlag):**
1. **Hygiene** (S): [tool-output-disclosure.md](tool-output-disclosure.md) — Search-Cap **500** + Disclosures, webFetch paginiert (Mini-Cache 5 URLs, 500-Zeilen-Fenster), Snippet-Fehlerpfad.
2. **Web-Tools** (S/M): [web-tools.md](web-tools.md) — `webGet(url, path)` (isEditTool, kein Limit) + paginierter webFetch — Story 1+2 können kombiniert werden (webFetch-Pagination greift in beide).
3. **CR-3 Filter** (S): [project-problems-tool.md](project-problems-tool.md).
4. **Debugger** (L, eigener Zyklus): [java-debugger-tool.md](java-debugger-tool.md) — User-managte Session, alle Actions, keine Confirmations, für Da Mek.

**⏳ geparkt:** terminal-session-tool.md (Revisit async-agent-tools-proposal Option C) · tool-confirmation.md.
**Decompiler Language-Server: Nein** (nativ, IP, wir haben das Verhaltenswichtige schon).

## Nächste Schritte

1. Docs-Resolution committen (Da Mek, inkl. resolved-points/index/memory).
2. Build-Zyklen oben, je Story: planWithPlanAgent → Abnahme → buildWithDev → reviewPlanAgent → ❌→✅ (lintDocsAndTests).
3. BDDs in den 4 ❌-Docs sind Entwürfe — beim Plan härten (UC-IDs via nextIds ziehen, idPrefix: DL? OD/WEB/PP/JD je Doc).

## Was nicht neu aufgemacht wird

CR-1/2/7 bleiben abgelehnt (Revisit nur bei Nicht-Git-Workspaces) · CR-5/CR-6 geparkt, nicht verworfen · Copilot-Server nicht decompilieren · keine Confirmations für Debugger.

## Lektionen

1. Eigene frühere Analysen sind IST-Verdacht (Issue #142: p2-Site-Content ≠ Bundle-ClassPath).
2. PO-Run-Dokumentation: Verdicts sofort in die CR-Tabelle, Auflösung mit resolved-points-Ablage — keine Leichen.
