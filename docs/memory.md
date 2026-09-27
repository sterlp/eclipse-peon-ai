# Session-Stand — 2026-09-27 (R-CC-15 Nested-Usage-Leak; Smoke-Test Compact-Hint)

> Achtung: docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts
> im Chat, Jon schreibt. (Da Mek hatte 2026-09-27 memory.md überschrieben — nicht wiederholen.)

## Branch
- **`story/compact-input-budget`** — Paul: **kein Merge**, weiter arbeiten. Branch ahead 3:
  `aaea9e5` (TODO-REMOVE-Diagnostik), `0b38d17`, `8cfa46a` (docs). Merge → main = Paul.

## Smoke-Test Compact-Hint (2026-09-27, alle 4 grün, Paul-Order)
- Mek/Thinka/Dok: Compact-Tool vorhanden, Hint → sofort kompaktiert, Codes überlebt.
- **Da Sniffa** (kein Compact-Tool): echter `CONTEXT LIMIT WARNING` (19150/20000) während der
  Lektüre, kompaktierte selbst, antwortete korrekt mit Code SUCH-1106.
- **Befund → R-CC-15 🚧** (docs/compact.md): nach `Da Sniffa done` PO-Hint
  `memory=31002(estimate=false) model=31002 estimate=1902` — Sub-Agent-Usage sprang ins
  Parent-Memory. Da Dok verifiziert code-seitig, Da Mek baut roten Test (Estimates im Test).
- Smoke-Befunde 2026-09-26: Compact lief sauber (Skip = LLM-Doppel-Aufruf, Guard size<3 by design);
  memory==model → kein Wildwuchs; Fixed-Overhead ≈ 7,3k → 10k-Limit praktisch sinnlos (30–40k testen).

## In Arbeit
1. **R-CC-15 ✅** (`4cb2783` + **Paul-Hotfix**: `agent == null || !hasCompactTool` → Fallback-Hint,
   ToolService:216 — „Compact geht nur mit Agent"). Surefire 1026/0/0/0; lint 0. Paul smoke-testet.
2. **Neue Befunde (Jon, in open-points):** **Bug A** Fallback-Hint ohne Dedup-Guard → Spam je
   Iteration (aktiv!) · **Bug B ⏳** CompactSessionTool kompaktiert agent.getMemory() statt
   req.getMemory() (latent, echten Suchagenten) · **Kleinigkeit** compactSession trotz agent==null
   exponiert · StreamingBridge-Race (ADR-0058-Nebenbefund).
3. **R-CC-16 ❌** — Da Scribe schreibt Compressor-LLM-Context-Größe (ohne Static) ins onTool,
   Estimate daneben — wartet auf Umsetzung.
4. **❓ Header 0k estimate (Jon)** — IST: Roster-Refresh nur event-getrieben (onTokenUsage);
   SOLL-Frage an Paul: Estimate-Pfad in den Refresh — open-points.md.
5. **TODO-REMOVE-Diagnostik** (`aaea9e5`) — nach Pauls Re-Smoke entfernen (grep `TODO-REMOVE`).

## Offen (Paul)
1. **❓ Fixture-Drift**: pom.xml/Dockerfile im test_project zulässig oder aufräumen + README-Zeile —
   open-points.md (Da-Mek-Empfehlung: behalten + Doc anpassen).
2. **⏳ Option A** — Standalone-Peon-Review behält memory*/askUser.
3. **⏳ Doc-Split CIB** — compact-input-budget.md ausgegliedert statt Umnummerierung.
4. **⏳ Docs-SOLL-Hygiene-Sweep** — Scope bestätigen.
5. **Merge → main** — zurückgestellt. 6. **R-CC-7** 🚧 Retry/Fehlerklassen-Backlog.
