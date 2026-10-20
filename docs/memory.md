# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Stand 2026-10-20 (2) — R-CC-20-Zyklus fertig gebaut + reviewed (Branch `story/workspace-nesting-2026-10-19`, lokal, NICHT gepusht)

- **Vier Slices + Delta-Fix, alle abgenommen (Da Dok CONCERNS → CONCERNS 1 behoben):**
  1. **B — stale Supplier** (`e5fa3c41`): `BuildPoAgentComponent:133` Bound-Ref → Lambda
     `() -> configuredModel.getConfig().getAutoCompactAfter()`; Test
     `delegateCompactThresholdFollowsConfigUpdate` (Live-Update nach Agent-Bau + 0 = off).
  2. **A — R-CC-20 ✅ Pre-Turn-Check** (`98a0a5be` + Ordering-Pin `19340dbf`): `overRawBudget`
     statisch in `HandleToolLoopCompactComponent` (EINE Regel, zwei Zeitpunkte), `preTurnCompact`
     in `AbstractAgent.doCall` vor `renderTurnContext`/frischer Message. `autoCompactRetriesAfterFailure`
     2 → 3 Versuche (Turn-2-Pre-Turn-Versuch ist jetzt SOLL). Ordering-Pin: Request-Payload beweist
     „Compact VOR Provider-Call" (Mutation rot am Payload-Pin).
  3. **C — 3 Tests honest** (`1ba66623`): `autoGateNeedsMoreThanMinCompactMessages` (MIN-Guard-Fall),
     `compactWithZeroBudget_gateOff` (Off-Fall), `systemPromptBuiltOnce…` Javadoc; Seed-Fix wegen
     UserMessage-Join in `ThreadSafeMemory.add` dokumentiert.
  4. **D — Codegraph-Context = eine Zeile** (`58afd828`): `renderBody` ohne Server-Instructions,
     Capture/`State.instructions`/`head()`-Hash bleiben (Dedup unverändert); Tests auf Abwesenheit.
  - **Externes Review #2 (2026-10-20) → R-CC-20.5** (`b34485c6` + Docs `4a2363d6`): wirft der Compressor,
     fängt `preTurnCompact` (RuntimeException, Cancel re-thrown via `ExceptionUtil.isCanceled`), meldet
     `onProblem` und läuft **ohne Compact** weiter — die frische Message geht nicht mehr verloren
     (Rot-vor-grün bewiesen). 🔒 **Kein Hint-Band am Turn-Anfang, für niemanden** (Paul 2026-10-20) —
     der Pre-Turn-Compact läuft vor der frischen Message, Compressor-Summary preserved; festgehalten in
     compact.md R-CC-20.2. ❓ Nachbarloch: In-Loop-Direkt-Compact fängt Exceptions nicht
     (Turn stirbt, nichts verloren) — bewusst nicht mitgefixt.
  - **Docs-Commit:** `20ebbdfb` (R-CC-20 specified, codegraph R-CG-7(c), ADR-0070/0071 angepasst,
    CHANGELOG Removed-StateMigration, open-points bereinigt). Status-Flips (compact.md R-CC-20 ✅,
    index.md) liegen noch **uncommitted** → mit dem Archivierungs-Commit mitkommen.
- **Gates:** Surefire core **1229/0/0**, OSGi **399/0/0**, Da Dok CONCERNS (nur CONCERNS 1 → behoben;
  CONCERNS 2 Fixture-Duplikat → open-points ❓). Lint: R-Form-VERWAIST = by-design-Baseline
  (DocsLinter.java:241-243 indexiert nur UC-Definitionen), kein `UNBELEGT_ERLEDIGT`.
- **Nächster Schritt (Da Mek):** `planImplemented` (archiviert `peon-plan/overview.md`) + Commit der
  offenen Docs-Edits (compact.md-Flip, index.md, memory.md, open-points.md). Danach Session schließen.
- **Homepage auf diesem Branch:** „Which model?" (`317a27a3`) + „Qwen tuning" (`e8fb390c`) entfernt.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ **Merge/Tag 2.14.1** — Branch lokal fertig (Workspace-Nesting + Compact-Policy + R-CC-20 + Codegraph
   eine Zeile + Homepage-Cleanup). Vorher `mvn -B clean verify` einmal neu; Merge/Tag = Pauls Call.
2. ⏳ **Delegations-Compact feuert pro Delegation** (Kosten, ADR-0071) — Faktor anheben oder nur bei
   Themenwechsel? Rückversicherung steht aus.
3. ❓ **Fixture-Duplikat** in ≥3 core-Testklassen (Da-Dok CONCERNS 2) — Micro-Zyklus oder weiterleben?
4. ❓ **R-Regeln haben kein Linter-Gate** (`DocsLinter:241/254`) — wie gehabt; 30 VERWAIST-Baseline.
5. Smokes: `!`-Override M1–M6 · Compact-Lock UC-CT-3/5/6 · Compact 2.14.1 (Dev/Plan/Review gleicher
   Prozent; Delegation compactet Sklave > 70 % vor Auftrag — jetzt zusätzlich: Pre-Turn-Zeile
   „🗜 Pre-turn compact for …" bei über Budget ohne Tool-Runde) · Codegraph-Smoke nach Neustart.
6. ⏳ Reserved-Name `codegraph` (ADR-0068) · ⏳ R-CG-15 Monorepo-State-Refresh · ⏳ R-CC-19-Semantik+Tilde ·
   ⏳ Windows-`cmd /c`-Fallback · 🔒 S3 (🗜 in PoDelegateTool) blockiert auf Pauls WIP-Merge.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio** (wichtiger als async Agents). Danach async Agents (Top-3 in
  [async-agents.md](async-agents.md)).
- **TempFileTool** — Slice 2 Output-Trim 🚧 in design.
- R-CG-14 (Generation + `disposed` statt Lock über den Connect) · R-CIB-4 Stufe 2 · R-CG-13 ·
  R-TR-6 INFO-Text + echte Assertions in `TaggedResultTest` · Reprobe-Entscheidung.
- Bug-Backlog: R-CC-7 Fehlerklassen-Tabelle/ApiRetry (❓) · Header-State-Leak (❓) ·
  Context-Overflow-Zyklus (❓) · McpService `connect()`-Rollback-Test (ADR-0069).

## Notizen

- **Riesige Tool-Args killen den Call** (~28k Zeichen Provider-JSON-Limit): Agenten schreiben lange
  Pläne/Berichte als Datei oder kompakt in mehreren kleinen Calls.
- **Windows-Falle im Renderer:** in `lineSeparator()`-Strings immer `indexOf('\n')` suchen, nie
  `indexOf(System.lineSeparator())`.
- `eclipseEditFile` verschluckt gelegentlich Parameter — Fallback `eclipseReplaceLines`/`diskEditFile`,
  danach Read-back. Nach jedem Line-Edit Read-back (AGENTS.md).
- Ledger `.agents/skills/wiki/skill-impact.md` gehört mit dem Zyklus committed.
- **Untracked, NICHT committen:** Pauls WIP (`org.sterl.llmpeon.core/…/agent/tools/**`,
  `bad-context-codegraph.md`). `peon-plan/overview.md` wird durch `planImplemented` archiviert.
- **UserMessage-Join in `ThreadSafeMemory.add`:** aufeinanderfolgende UserMessages werden gemergt —
  Seed-Fixtures müssen deshalb ggf. eine `AiMessage` als Trenner setzen (Fall Inc 3).
- **Surefire-Ground-Truth + Commit-Hash aktiv einfordern** — Da Meks DONE-Claims gegen den IST prüfen
  (Fall Inc 1: erster Build-Lauf war nur Zusammenfassung, kein Code).