# Session-Stand — 2026-09-26 (Compact-Nachbau-Review + Agent-Tool-Filter)

## Branch
- **`story/compact-input-budget`** — alles drauf (Merge `a7c533f` mit `story/agent-tool-filter`, gepusht, in Sync mit origin). Merge → main = **User-Entscheidung**.

## Abgeschlossen (alle gepusht, reviewed, ✅ geflippt)
- **Architektur-Doc `docs/compact-architektur.md`** (Paul-Anfrage 14:48): Ownership „Agent entscheidet & setzt zurück · Service führt aus (monitor-frei) · Component kürzt · Result spricht", Mermaid-Sequence, Schnittstellen-Tabelle — in index.md verlinkt. Paul-Report mit Review-Fokus-Stellen ausgeliefert.
- **Agent-Tool-Filter** (R-TF-1…4, `docs/agent-tool-filter.md`): Da Dok hat Shell (instanceof-Whitelist), SearchAgent ohne plan*-Tools, ShellTool-Namen als Konstanten, Tool-Matrix-Tests. Option A: UC-TF-2 nur RAM-Sklave.
- **Compact-Nachbau-Review** (R-CC-11…14, `docs/compact.md`): CompactService/ContextTrimComponent/model-Package, requestTokens (Provider-only B′) in resultLine, Token-Mathe zentral in ChatMessageUtil, monitor-freier Service (Emission durch Caller, Compressor-Events log-only).
- **R-CC-10** Diagnose-Dreiklang (`memory=… model=… estimate=…` bei jedem Compact-Ereignis) — Paul kann die 289k/307k-Phantom-Zahlen jetzt messen.
- **Queued-Marker**: Pauls Format `(HH:mm)` ist SOLL — Tests + Regel 9 + Homepage angepasst (`48e0332`). onTool-Zeile bleibt `(queued HH:mm)` (bewusst, per Doc getrennt).

## Offen
1. **⏳ Option A** (Paul-Rückversicherung): Standalone-Peon-Review behält memory*/askUser — open-points.md.
2. **Merge → main**: Code FF/merge-fähig, Working Tree sauber — User-Entscheidung.
3. **Lint-Befunde in den Compact-Docs** (Pauls Restrukturierung): 6× `DOPPELT_DEFINIERT` (R-CC-1…6 in compact-context-counter.md UND compact.md) + 6× `PRAEFIX_FREMD` (R-CIB-1…6 im CC-Doc) — vor dem Merge aufräumen (Paul/ Jon).
4. **Memory-Summen-Verdacht** (289k/307k > Modell-Limit): Messwerte aus R-CC-10 abwarten → dann Fix (Kandidat: gecachte Prefix-Tokens in jedem Response-Input) als eigene Story.
5. **R-CC-7** (Retry/Fehlerklassen) 🚧 — Bug-Fix-Backlog, mit ApiRetry-Tabelle.
6. Javadoc `AbstractAgent.java:257` sagt noch `(queued HH:mm)` — stale, Mini-Fix bei nächster Berührung.

## Nächste Schritte (Vorschlag)
- **Paul**: `docs/compact-architektur.md` reviewen (Mermaid + Ownership) → dann Compact-Code-Review auf dem Remote-Branch (Fokus-Stellen stehen im Report). Danach Merge-Frage.
- Nach Merge: R-CC-7 oder Memory-Konsolidierung (Punkt 4).
