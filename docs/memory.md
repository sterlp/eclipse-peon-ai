# Session-Stand — 2026-09-26 (Docs-Cleanup-Runde, kein Merge)

## Branch
- **`story/compact-input-budget`** — bleibt drauf (Paul: **kein Merge**, weiter aufräumen). Merge → main bleibt User-Entscheidung.

## Abgeschlossen (diese Runde, Docs-only)
- **Docs-Sanity-Sweep** (searchAgent-Scan + eigene Verifikation): 6 tote Links repariert
  (tool-evolution.md ×2 → resolved-points.md, free-provider-ox-alpha.md ×2 → verbesserungen.md-Studie,
  po.md → po-agent-jon.md + `prompts/po.md`, scaffold-agent.txt → Pfad+Name korrekt,
  ADR-0024-Wildcard → 0024-po-slaves-ram-only, index.md builtin-agent-prompt-override → -tracked).
- **ADR-Index aufgeräumt:** 0026-Duplikatzeile entfernt; 0027 doppelt vergeben →
  `0027-static-content-loader` → **`adr/0057-static-content-loader.md`** (Superseded by 0032,
  Status im ADR-File angeglichen, chinesische Zeichencorruption gefixt).
- **open/resolved-Dedup:** ApiRetry-Backlog-Item → Verweis statt Inhaltsdublette;
  Dropdown-Umbau-Zeile aus open-points entfernt (stand schon in resolved-points);
  Slot/Glossar-Doppelzeilen aus resolved-points entfernt (Details-Version verbleibt).
- **index.md:** 10 Story-Zeilen ohne Statusmarker → ✅ done ergänzt; Secure-Credentials + Header-State-Leak nachgetragen (waren ungelinkt); `dropdown-ui-comparison.md` gelöscht (Arbeitsdokument des resolveten Dropdown-Themas, Historie in git).
- lintDocs: 134 Docs gescannt, **0 Befunde** (UC 122/122).

## Offen
1. **⏳ Option A** (Paul-Rückversicherung): Standalone-Peon-Review behält memory*/askUser — open-points.md.
2. **⏳ Doc-Split CIB** (Paul-Rückversicherung): compact-input-budget.md ausgegliedert statt Umnummerierung — open-points.md.
3. **⏳ Docs-SOLL-Hygiene-Sweep**: Scope von Paul bestätigen lassen — open-points.md.
4. **Merge → main**: zurückgestellt bis Paul sagt.
5. **Memory-Summen-Verdacht** (289k/307k): Messwerte aus R-CC-10 abwarten → eigene Story.
6. **R-CC-7** (Retry/Fehlerklassen) 🚧 — Bug-Fix-Backlog, mit ApiRetry-Tabelle.

## Nächste Schritte (Vorschlag)
- Paul: ⏳-Rückversicherungen (Option A, Doc-Split, Sweep-Scope) beantworten → dann R-CC-7 oder Memory-Konsolidierung; Merge später.
