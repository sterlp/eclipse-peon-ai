# Open Ends / Memory — Session-Zwischenstand

> Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> **Branch `web-factory-mvp`** (Paul hat `web-factory-goon` offenbar gemerged/neu aufgemacht),
> HEAD `8cc13196`, gepusht. Zyklus 2026-11-05 komplett (inc-10…16, Plan archiviert
> `overview-done-2026-11-05-10-00.md`). **Merge-Entscheidung (Squash-Default) liegt bei Paul.**

## Zyklus 2026-11-05 — zwei Stories, beide ✅ + gepusht

- **① Phantom-Projekt-Crash („Failed to open project with plan file home", 2026-11-03):**
  Externe Datei-Selektion resolved zu Phantom-Handle `/home`; `PlanTool.getProject()` öffnete
  bedingungslos im Query-Pfad. Gebaut: R-PT-6 (Query öffnet nie), R-PT-7 (`exists()` vor
  `open()`, Tool-Pfad), R-SEL-5 (Phantom-Guard als allererste Zeile in `setProject`) — jeweils
  ✅ mit Mutanten-Belegen, Da-Dok-Review des B1-Fixes (planRead-Regression bei geschlossenen
  Projekten) nachgezogen. **R-PT-8 (Query nennt geschlossenes Projekt per `onProblem`) ist mein
  autonom abgeleitetes SOLL — ⏳ Rückversicherung in open-points.md.**
- **② File-Update-Diff (C-4-Proposal, aifactory open-points):** `file-update-diff.md` (R-SD-1…3) ✅:
  Anlegen feuert `onFileUpdate` (voller `+`-Diff, disk + eclipse, auch eclipse-Overwrite),
  partzieller Diff statt Skip (ein Cap 500 auf die Changed-Region, `MAX_LCS_CELLS`/
  `MAX_DIFF_LINES` Clean-Break raus), binärer Inhalt (NUL-Sniff 8 KB) bekommt kein Diff.
  Da-Dok REJECTED → Delta: **Marker nennt die tatsächlich gezeigte Menge, nie die Cap-Zahl**
  (`34f999d6`, `8cc13196`). aifactory chat.md R-WP-36/UC-WP-68 mitgezogen, Proposal-Block aus
  aifactory/open-points.md raus. Homepage: geprüft, keine Aussage wird stale — ob die
  Diff-Anzeige eine eigene Homepage-Zeile verdient, Paul-Prio.
- **Skill-Evolution:** 1 Outcome — wiki pattern „Status flip: sweep the doc, not just the
  marker" (Create, B1/B2-Evidenz) + Ledger-Zeile; `AGENTS-DEV.md` Lint-Regel erweitert
  (nie partielle docRoots/testRoots — 253-Findings-Falle). Lint-Baseline Auto-Discovery: **38**.
- **Gates final:** Core-Surefire **1480/0/0/0** · Tycho-Plugin **466/0/0/53** · aifactory
  **221/0/0/0** · Lint 38 (= Baseline, UC-SD-1…3 + UC-PT-6/7 + UC-SEL-5 belegt).
- **Stale-Flip:** R-SEL-4/UC-SEL-4 ❌→✅ (Tests existieren seit 2026-09-16, Jon, 🔒 in open-points).

## Offen für Paul (je eine Frage / Bestätigung)

1. **⏳ R-PT-8** (gebautes Verhalten meiner Abgeleitet-Entscheidung): Query auf ein geschlossenes,
   echtes Projekt meldet `onProblem` „Project 'X' is closed" statt still „kein Plan" — ok so,
   oder stilles „kein Plan"? (open-points.md, Regel streichen wäre trivial).
2. **⏳ AGENTS-JON.md-Alias** (2026-10-30) — unverändert offen (open-points.md).
3. **Merge `web-factory-mvp`** (Squash-Default) — trägt inc-10…16.
4. **Homepage-Zeile zur Diff-Anzeige?** (siehe oben, optional)
5. ❓ Altbestand aus dem Reparatur-Zyklus: Edit-Tool-Falle (upstream melden?), manuelle Nachweise.

## Backlog (nicht gestartet)

- **Search-Agent als vollwertiger interner Agent** (R-LSR-7 🚧, search-agent-tool.md) ·
  **Rename-Sicherheit über stabile `id()`** (agenten-namen-im-chat.md 🚧).
- **P3:** SSE-Client-Kopien (5×) in Fabrik-Tests extrahieren — eigenes Gate.
- **P1-Variante:** Cancel-/Fehler-Pfad-Release ohne atomaren Queue-Blick — test-first (⏳).
- Async-Shell-Tool (Paul-Prio) → async Agents; Merge-Check „geteilte disk-Tool-Instanzen" vorher.
- Bug-Triage-Reste: totes MCP-Tool in Spec-Liste, `PoDelegateTool.dispatch`-Catch, ApiRetry-
  Fehlerklassen, Header-State-Leak, Auto-Compact bei Context-Overflow, `org.sterl.llmpeon.test`
  Surefire-Basis. Neu: Warnungs-Aufräum-Zyklus (5 `@SuppressWarnings`-Dateien) · „Kein `throw`
  ohne Message" · ApiRetry-Null-Byte-IOException-Verdacht (❓ open-points).
- Klein: `MutableClock` 7× → Fixture · `AbstractAgent.asyncCall` ohne Aufrufer · Lint-Baseline
  Core · historische `*-plan.md` löschen? · `StringUtil.oneLine`-Konsolidierung ·
  `effectiveUrl`-Schnitt · Ork-/Peon-Altbestand ~35 SOLL-Docs (Massen-Rename braucht Paul).

## Notizen

- System-Prompts generisch; Scaffold-Prompts stehen auf `Scaffold` (Jon editiert System-Prompts
  selbst, Übersicht: `core/docs/system-prompts.md`).
- Commit: EXPLIZITE Pfadliste; Pauls WIP nie (`AGENTS*.md` Root, `peon-plan/**`, `README.md`,
  `ai-e2e-test/**`, `bugs/**`, `homepage/**` nur auf Ansage).
- Lint: `docRoots`/`testRoots` weglassen = Auto-Discovery (R-DL-26 + AGENTS-DEV-Präzisierung
  2026-11-05 — teilweise Listen erzeugen Massen-Findings); `findings:`-Zeile melden, nie
  `Scanned:`; `idPattern` **mit** `[a-z]?`.
- JUnit `@Timeout`-Falle: Daemon-Probe-Muster statt `@Timeout` (memory 50).
- Eclipse-Plugin-Tests: `eclipseBuildProject` vor JUnit-Lauf; Einzelklassenlauf einer
  bestehenden Klasse ist trust-frei (~6 s).
