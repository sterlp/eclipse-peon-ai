# Session-Stand — 2026-09-26 (Compact-Nachbau-Review + Agent-Tool-Filter + Docs-Cleanup)

## Branch
- **`story/compact-input-budget`** — alles drauf (Merge `a7c533f` mit `story/agent-tool-filter`, gepusht, in Sync mit origin). Merge → main = **User-Entscheidung**.

## Abgeschlossen (alle gepusht, reviewed, ✅ geflippt)
- **Architektur-Doc `docs/compact-architektur.md`** (Paul-Anfrage 14:48): Ownership „Agent entscheidet & setzt zurück · Service führt aus (monitor-frei) · Component kürzt · Result spricht", Mermaid-Sequence, Schnittstellen-Tabelle — in index.md verlinkt. Paul-Report mit Review-Fokus-Stellen ausgeliefert.
- **Agent-Tool-Filter** (R-TF-1…4, `docs/agent-tool-filter.md`): Da Dok hat Shell (instanceof-Whitelist), SearchAgent ohne plan*-Tools, ShellTool-Namen als Konstanten, Tool-Matrix-Tests. Option A: UC-TF-2 nur RAM-Sklave.
- **Compact-Nachbau-Review** (R-CC-11…14, `docs/compact.md`): CompactService/ContextTrimComponent/model-Package, requestTokens (Provider-only B′) in resultLine, Token-Mathe zentral in ChatMessageUtil, monitor-freier Service (Emission durch Caller, Compressor-Events log-only).
- **R-CC-10** Diagnose-Dreiklang (`memory=… model=… estimate=…` bei jedem Compact-Ereignis) — Paul kann die 289k/307k-Phantom-Zahlen jetzt messen.
- **Queued-Marker**: Pauls Format `(HH:mm)` ist SOLL — Tests + Regel 9 + Homepage angepasst (`48e0332`). onTool-Zeile bleibt `(queued HH:mm)` (bewusst, per Doc getrennt).
- **Docs-Cleanup (Paul 17:35, `8b48048`):** Lint-Befunde weg — `docs/compact-input-budget.md` (Präfix CIB) aus compact.md ausgegliedert, lintDocs = 0 Befunde. Signature-Cleanup (Paul 17:00): `compact(...)` ohne `budgetTokens`/`requestIsEstimate` (ableitbare Parameter raus), stale Javadoc :257 gefixt. open-points ausgemistet (🔒/erledigt → resolved-points/gelöscht), 4 alte Plan-Archive + 2 Task-Pläne gelöscht. Surefire 1023/0, Plugin-Build clean.

## Offen
1. **⏳ Option A** (Paul-Rückversicherung): Standalone-Peon-Review behält memory*/askUser — open-points.md.
2. **⏳ Doc-Split CIB** (Paul-Rückversicherung): compact-input-budget.md ausgegliedert statt Umnummerierung — open-points.md.
3. **Merge → main**: FF/merge-fähig, Working Tree sauber (nur untracked `peon-plan/overview-done-2026-09-26-17-24.md`, mit nächstem Commit rein) — User-Entscheidung.
4. **Memory-Summen-Verdacht** (289k/307k > Modell-Limit): Messwerte aus R-CC-10 abwarten → dann Fix (Kandidat: gecachte Prefix-Tokens in jedem Response-Input) als eigene Story.
5. **R-CC-7** (Retry/Fehlerklassen) 🚧 — Bug-Fix-Backlog, mit ApiRetry-Tabelle.
6. **⏳ Docs-SOLL-Hygiene-Sweep**: Scope von Paul bestätigen lassen (open-points.md).

## Nächste Schritte (Vorschlag)
- Paul: Compact-Code-Review (Fokus-Stellen im Report) + ⏳-Rückversicherungen (Option A, Doc-Split) → dann Merge-Frage.
- Nach Merge: R-CC-7 oder Memory-Konsolidierung (Punkt 4).
