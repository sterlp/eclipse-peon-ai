# Session-Stand — 2026-09-19 (Tag: Tool-Evolution PO-Run + Build-Start)

## Wo wir stehen

**Branch `analysis/tool-evolution`** — Story A vollständig gebaut (2026-09-20, inc 1…3, alle Docs committed).

**PO-Run abgeschlossen (Paul):** alle CR-Items entschieden. Verdicts + Begründungen: [resolved-points.md](resolved-points.md)
(„Tool-Evolution-Run"). Temp-Docs (tool-evolution.md, feature-change-request-copilot.md, change-review.md) gelöscht.

**❌ specified, wartet auf Build-Zyklen (Reihenfolge-Vorschlag):**
1. ✅ **Hygiene** (S): [tool-output-disclosure.md](tool-output-disclosure.md) — done 2026-09-20 (Story A, inc 1 + inc 3).
2. ✅ **Web-Tools** (S/M): [web-tools.md](web-tools.md) — done 2026-09-20 (Story A, inc 2 + inc 3).
3. **CR-3 Filter** (S): [project-problems-tool.md](project-problems-tool.md).
4. **Debugger** (L, eigener Zyklus): [java-debugger-tool.md](java-debugger-tool.md) — User-managte Session, alle Actions, keine Confirmations, für Da Mek.

**⏳ geparkt:** terminal-session-tool.md (Revisit async-agent-tools-proposal Option C) · tool-confirmation.md.
**Decompiler Language-Server: Nein** (nativ, IP, wir haben das Verhaltenswichtige schon).

## Nächste Schritte

1. **Story A DONE (2026-09-20)** — Inc 1 Caps/Disclosures (UC-OD-1…3), Inc 2 webGet + Toggle-Gate + Homepage-Zeile (UC-WEB-5…8), Inc 3 webFetch-Pagination (UC-WEB-1…4); wartet auf PO-Review (Da Dok). Danach Story B: CR-3-Filter ([project-problems-tool.md](project-problems-tool.md), PP-IDs ziehen), Story C: Debugger ([java-debugger-tool.md](java-debugger-tool.md), JD-IDs ziehen). Review (Da Dok) je Story, Flip ✅ + lint vor Flip.
2. ApiRetry-Evidence: 3 Connect/no-bytes-Abbrüche 2026-09-20 im buildWithDev — in open-points ergänzen.
3. BDDs in ❌-Docs sind gehärtet (OD/WEB mit UC-IDs); PP/JD beim jeweiligen Plan-Zyklus härten.

## Was nicht neu aufgemacht wird

CR-1/2/7 bleiben abgelehnt (Revisit nur bei Nicht-Git-Workspaces) · CR-5/CR-6 geparkt, nicht verworfen · Copilot-Server nicht decompilieren · keine Confirmations für Debugger.

## Lektionen

1. Eigene frühere Analysen sind IST-Verdacht (Issue #142: p2-Site-Content ≠ Bundle-ClassPath).
2. PO-Run-Dokumentation: Verdicts sofort in die CR-Tabelle, Auflösung mit resolved-points-Ablage — keine Leichen.
