# Open Ends / Memory — Session-Zwischenstand

> Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> Branch `web-factory-goon`, HEAD `329f0d2b` — Reparatur-Zyklus 2026-10-30 **komplett** (Inc 1–8 +
> Delta `e68a560e` + Abschluss `329f0d2b`, Da-Dok-Review behandelt, Plan archiviert
> `overview-done-2026-10-31-00-48.md`). **Merge-Entscheidung (Squash-Default) liegt bei Paul.**

## Zyklus-Ergebnis (2026-10-30/31)

- **Inc 1–6 (Bug-Hunt):** Slave erbt nicht mehr Jons Identitätszeile (`ProjectRuntime:98` →
  `List.of()`, Javadoc-Vertrag an `AiPoAgent.setStaticContext`) · tote Plugin-Namens-Schreiber weg
  (`BuildPoAgentComponent`) · `Peon-`-Strip ersatzlos gelöscht (Custom `Peon-Dev` erbt nie
  `AGENTS-DEV.md`) · 3 doppelte Tests gelöscht, 2 Alleinstellungen umgezogen ·
  **Scaffold heißt `Scaffold`** (Prompt-Zeilen von Jon selbst, Clean Break, Glossar-Zeile,
  CHANGELOG/Release-Note) · Ork-Namen in 17 SOLL-Docs + Kommentaren gezogen, Evidenz-Zahlen aus
  docs raus.
- **Inc 7 (AGENTS*.md, alles uncommittet):** `AGENTS-PO.md` → `AGENTS-JON.md` (Jon, `git mv` — lädt
  jetzt wieder) · AGENTS-DEV.md-Linter-Bullet auf R-DL-26 gestellt · Root-AGENTS.md Edit-Tool-Passage
  gekürzt · Plugin-/Core-`AGENTS.md`-Widersprüche behoben (Background-Jobs-Duplikat raus,
  Surefire statt `runTests`). Paul: committet den Working Tree bei Gelegenheit.
- **Inc 8 (P1 Queue-Drain-Race):** bestätigt und gefixt — Claim+Enqueue atomar
  (`claimTurnSlotOrQueue`), Release atomar mit letztem Queue-Blick (`nextQueuedEntryOrReleaseSlot`);
  roter Test `AbstractAgentQueueReleaseRaceTest`; ChatFirstTouch-Flaky deterministisch gefixt.
  Invariante als **Rule 10** in `queued-user-messages.md`.
- **Gates final:** Core **1425/0/0/0** · Fabrik **107/0/0/0** · Tycho-Plugin **445/0/0/53** ·
  Lint 2 = Baseline, UC 285/285.

## Offen für Paul (je eine Frage / Bestätigung)

1. **Merge von `web-factory-goon`** (Squash-Default) + Commit des uncommitteten AGENTS*.md-Working
   Trees (Rename + Säuberung, siehe open-points ⏳).
2. ⏳-Rückversicherungen in [open-points.md](open-points.md) Abschnitt 2026-10-30: P1-Claim-Seite
   (Monitor-Paar-Invariante akzeptiert), Fehler-Ergebnis als Mitglieder-Zeile, Rename-Bestätigung.
3. ❓: **Edit-Tool-Falle** (Tool-Defekt upstream melden oder Bedienungsregel?), manuelle Nachweise
   (Browser 8099, `test-chat.html`), 🔒-Reste ok?
4. Ork-/Peon-Altbestand ~35 SOLL-Docs (Inventar in open-points) — Massen-Rename braucht Paul
   (Glossar-Regel 6).

## Backlog (nicht gestartet)

- **P3:** SSE-Client-Kopien (5×) in Fabrik-Tests extrahieren — nächster Zyklus, eigenes Gate.
- **P1-Variante:** Cancel-/Fehler-Pfad-Release ohne atomaren Queue-Blick — nächster Bug-Zyklus,
  test-first (⏳ open-points).
- Async-Shell-Tool (Paul-Prio) → async Agents; Merge-Check „geteilte disk-Tool-Instanzen" vorher.
- Bug-Triage-Reste: totes MCP-Tool in Spec-Liste, `PoDelegateTool.dispatch`-Catch, ApiRetry-
  Fehlerklassen, Header-State-Leak, Auto-Compact bei Context-Overflow, `org.sterl.llmpeon.test`
  Surefire-Basis.
- Neu aufgenommen (2026-10-29): „Kein `throw` ohne Message" · Warnungs-Aufräum-Zyklus (inkl. 5
  `@SuppressWarnings`-Dateien, uncommitted).
- Klein: `MutableClock` 7× → Fixture · `AbstractAgent.asyncCall` ohne Aufrufer · Lint-Baseline Core ·
  historische `*-plan.md` löschen? · `StringUtil.oneLine`-Konsolidierung · `effectiveUrl`-Schnitt.

## Notizen

- System-Prompts generisch; Scaffold-Prompts stehen auf `Scaffold` (Jon editiert System-Prompts
  selbst, Übersicht: `core/docs/system-prompts.md`).
- Commit: EXPLIZITE Pfadliste; Pauls WIP nie (`AGENTS*.md`, `peon-plan/**`, `README.md`,
  `ai-e2e-test/**`, `pauls-product-vision.md`, `bugs/**`).
- Lint: `docRoots` weglassen = Auto-Discovery (R-DL-26); `findings:`-Zeile melden, nie `Scanned:`.
- Skill `peon-build-test` trägt jetzt: `releng/llmpeon-target` im `-pl` des **Test**-Gates ·
  Core-`static final`-Rename braucht `clean` im Plugin-Bau. Ledger-Zeilen in skill-impact.md.
- Da-Dok-Empfehlungen immer am IST verifizieren — diesmal: seine (b)-Empfehlung wäre ein
  nie-feuernder Guard gewesen; (a) mit dokumentierter Invariante gewählt.
