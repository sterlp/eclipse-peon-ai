# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Stand 2026-10-23 — Zyklus: Docs-Slim ABGESCHLOSSEN (Task „Docs-Cleanup & schlanker Index")

Branch `story/docs-restructure-2026-10-22` @ `12ae029b` — **einziger Docs-Branch** (slim wurde
per FF hineingemerged + gelöscht, Paul-Wunsch 2026-10-23: nur EIN Merge nach main).
**Zyklus fertig — Review bestanden, 16 Commits auf dem Branch. Warten auf Pauls Merge nach main.**

⚠️ Der Branch trägt jetzt auch **Pauls Code** (`fb66b8b4`: AgentsTool/AsyncStreamingBridge/
AsyncMonitor + AbstractAgent/AiAgent/Timer/ToolLoopRequest; `573eb601`: `org.sterl.llmpeon/pom.xml`
−1 Zeile) — dafür lief in diesem Zyklus **kein** Core-Suite-/Plugin-Build. Vor dem main-Merge:
`mvn -o -B clean test -pl org.sterl.llmpeon.core` + Plugin-Bau als Mindest-Evidenz.

- **Commits:** `01d538ae` INC-A (Root-Index = Link-Doc ~3.2k Zeichen + AGENTS-DEV Gate-Evidenz-Regel) ·
  `b6d27472` INC-B Evidence-Sweep (Hashes/Surefire-Zahlen/Messwerte aus `docs/**` entfernt) ·
  `89273a15` 2 Rest-Hashes · `916c8311` Da-Dok-Review-Fixes F1–F5 · `67113bcc` Skill-Evolution
  (`peon-build-test` patched + Ledger Accepted) · `fb66b8b4`–`12ae029b` Pauls Code-WIP (AgentsTool/
  AsyncStreamingBridge/AsyncMonitor) · `2ab1a869` SwtUi-UI-Guard (CI-Failure behoben, Test skippt
  headless) · `88c499fd` OP-STREAM-1 (Stream-Hänger bei Verbindungsverlust, streaming-display.md) ·
  `4fe02f02` slf4j-simple-Stale-Ref (.classpath) · `b8a91f4f` ADR-0073 slf4j-api bleibt gebündelt.
- **Merge-Status:** ALLE Gates grün (Core-Suite + Plugin-Fragment, PDE-Bau repariert, slf4j
  ex-vorher-Stand via ADR-0073) — **Merge nach main frei** (Paul, 1 Merge, --no-ff optional).
  Danach: `releng/llmpeon-target` wieder installen (Datei/`~/.m2` gleichziehen), Branch löschen.
- **Da-Dok-Review: CONCERNS → alle Findings aufgelöst** (F1 memory.md-Stand, F2 java-debugger
  Satzreste, F3 Sub-Index-Köpfe/Legende, F4 Evidenz-Reste 2. Welle, F5 UC-DL-74…82 ❌→✅ —
  Marker+Tests verifiziert, 27/0 grün). Lint-Baseline unverändert (9).
- **Skill-Evolution:** `peon-build-test` gepatcht — Core-Single-Class-Gate läuft offline OHNE
  `-am`/Reactor (`mvn -o -B clean test -pl org.sterl.llmpeon.core -Dtest='A,B'`); `clean` zwingend
  (m2e/Lombok-Stale-Falle). Evidence 27/0. Ledger `.agents/skills/wiki/skill-impact.md` = Accepted.
- **Offen aus dem Review (open-points.md):** Testnamen-im-Fließtext-Slice (Paul-Entscheid
  2026-10-19, noch nicht gefahren) · Linter-Guard gegen Evidenz-Drift (❓, positiv nachweisen) ·
  R-CC-18-/R-DEF-Doppedefinitionen (vorbestehend).

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ **Merge/Squash** beider Docs-Branches (restructure + slim) → main + Smoke (Docs-Linter-Report,
   Root-Index auto-load). Danach Branches löschen.
2. ❓ **Merge/Tag 2.14.1** — `story/workspace-nesting-2026-10-19` lokal fertig; vorher
   `mvn -B clean verify`. Smokes: Dev/Plan/Review gleicher Prozentsatz ·
   Delegation-Compact > 70 % vor Auftrag · Monorepo · Codegraph nach Neustart.
3. ⏳ **Delegations-Compact feuert pro Delegation** (Kosten, ADR-0071) — Faktor anheben oder nur
   bei Themenwechsel? Rückversicherung steht aus.
4. ⏳ **Compact-Briefing-Schema** für `compressor.md`: 3 Ergänzungen vorgeschlagen (STANDING-RULES-Slot,
   preserve-Pfade erweitern, memory.md = complement-not-replace) — Freigabe steht aus.
5. ❓ Fixture-Duplikat in ≥3 core-Testklassen · ❓ R-Regeln ohne Linter-Gate (`DocsLinter:241/254`)
   · ❓ In-Loop-Direkt-Compact fängt Exceptions nicht (R-CC-20.5-Nachbarloch) · ⏳ Reserved-Name
   `codegraph` · ⏳ R-CG-15 Monorepo-State-Refresh · ⏳ R-CC-19-Semantik+Tilde · ⏳ Windows-
   `cmd /c`-Fallback · 🔒 S3 (🗜 in PoDelegateTool) blockiert auf Pauls WIP-Merge.

## Task (Paul 2026-10-22) — Status

1. **Root-Index verschlanken** — ✅ fertig (INC-A), Abnahme „ohne Register arbeiten" bestanden.
2. **Docs inhaltlich cleanen (Variante B)** — ✅ fertig im Working Tree (INC-B), Commit + Review offen.
3. **Paul arbeitet parallel am Code** — seine Edits ignorieren, nie committen, nicht wundern.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio** (wichtiger als async Agents). Danach async Agents (Top-3 in
  [async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)).
- **TempFileTool** — Slice 2 Output-Trim 🚧 in design.
- Lint-Baseline-Befunde (siehe Stand oben) in einen Docs-Zyklus aufnehmen.
- R-CG-14 (Generation + `disposed` statt Lock) · R-CIB-4 Stufe 2 · R-CC-7 Fehlerklassen-Tabelle
  ApiRetry (❓) · Header-State-Leak (❓) · Context-Overflow-Zyklus (❓) · McpService
  `connect()`-Rollback-Test (ADR-0069).

## Notizen

- **System-Prompts generisch halten** (Paul 2026-10-20): nichts Repo-Spezifisches, minimale
  Änderungen, keine Docs-Inhalte — nur Verweise; Best Practice für alle Projekte (ABAB, C++, Java …).
- **Untracked, NICHT committen:** Pauls WIP (`org.sterl.llmpeon.core/…/agent/tools/**` —
  AgentsTool.java, AsyncMonitor.java, StreamingBridgeNew.java; getrackt modifiziert: Timer.java);
  `peon-plan/`; `non-peon-ai-agents-md/` veraltet — Paul löscht selbst. Commit immer mit
  EXPLIZITER Pfadliste (`git add -- docs/ org.sterl.llmpeon.core/docs/ org.sterl.llmpeon/docs/ AGENTS-DEV.md`), nie `git add -u`.
- **Tool-Trap:** `diskRenameResource` kann Erfolg melden ohne zu verschieben — nach Renames immer
  mit `diskListDirectory`/`diskSearchFiles` verifizieren.
- **Windows-Falle im Renderer:** in `lineSeparator()`-Strings immer `indexOf('\n')` suchen.
- **UserMessage-Join in `ThreadSafeMemory.add`:** Seed-Fixtures ggf. `AiMessage` als Trenner.
- **Surefire-Ground-Truth + Commit-Hash aktiv einfordern** — DONE-Claims gegen den IST prüfen.
- **Docs-Restrukturierung (2026-10-22):** Feature-Doc-Pfade sind modul-qualifiziert
  (`org.sterl.llmpeon.core/docs/x.md`, `org.sterl.llmpeon/docs/x.md`); ADRs + Root-Docs
  (Glossar, memory, open-/resolved-points, Notes) bleiben in `docs/`. Code-Referenzen folgen
  AGENTS-DEV.md-Regel. Linter-Discovery findet Modul-Roots automatisch.
- **Issues/**: asm-Versions-Tabellen (issue-142) sind Fachinhalt, keine Evidenz — bewusst behalten.
