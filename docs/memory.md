# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Stand 2026-10-22 — Docs-Restrukturierung (`story/docs-restructure-2026-10-22`)

- **INC-1 Linter-Discovery** (`942390f6`) · **INC-2 Doc-Move + Indexe** (`a2616fc9`) ·
  **INC-3 Referenz-Hygiene** (`ba1b2712` + follow-up `e82f8d8d`) — Gates grün:
  Core Surefire 1252/0/0/0, Lint 9 Findings = Baseline, Jar-Check 0 `docs/`-Einträge,
  79 Renames + dokumentierte Ausnahme (eclipse-read-tools-Split).
- **Neue Struktur:** Root `docs/index.md` = Register (nur Titel + Statusmarker, 7.272 Zeichen,
  Budget ≤ 8.000) · Sub-Indexe `org.sterl.llmpeon.core/docs/index.md` (58 Docs) und
  `org.sterl.llmpeon/docs/index.md` (25 Docs) tragen die vollständigen Einträge ·
  ADRs global am Root (0001–0072). Linter auto-discovered (INC-1), Disclosure im Report.
- **INC-4 (meine Edits, ~uncommitted):** ADR-Pfadstrings repointet (0045/0054×2/0056/0058),
  `AGENTS.md:42` Skill-Name → `component-architecture`, AGENTS-DEV.md-Regel
  „Doc-Referenzen in Code tragen Modul-Pfad, in Kommentaren nie Test-String-Literalen",
  toter `docs/handovers-and-plans.md`-Block aus context-architecture.md entfernt.
  Prompts (po.md/dev-build-loop.md/po-tutorial.md): grep-clean, keine Anpassung nötig.
- **Nächste Schritte:** Da-Dok-Review des Gesamtumbaus → bei ACCEPTED `planImplemented`
  (archiviert Plan) → Commit meiner Docs-Edits → Pauls Merge/Smoke.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ **Merge/Tag 2.14.1** — `story/workspace-nesting-2026-10-19` lokal fertig (Core 1231/0,
   OSGi 399/0); vorher `mvn -B clean verify`. Smokes: Dev/Plan/Review gleicher Prozentsatz ·
   Delegation-Compact > 70 % vor Auftrag · Monorepo · Codegraph nach Neustart.
2. ⏳ **Delegations-Compact feuert pro Delegation** (Kosten, ADR-0071) — Faktor anheben oder nur
   bei Themenwechsel? Rückversicherung steht aus.
3. ⏳ **Compact-Briefing-Schema** für `compressor.md`: 3 Ergänzungen vorgeschlagen (STANDING-RULES-Slot,
   preserve-Pfade erweitern, memory.md = complement-not-replace) — Freigabe steht aus.
4. ❓ Fixture-Duplikat in ≥3 core-Testklassen · ❓ R-Regeln ohne Linter-Gate (`DocsLinter:241/254`)
   · ❓ In-Loop-Direkt-Compact fängt Exceptions nicht (R-CC-20.5-Nachbarloch) · ⏳ Reserved-Name
   `codegraph` · ⏳ R-CG-15 Monorepo-State-Refresh · ⏳ R-CC-19-Semantik+Tilde · ⏳ Windows-
   `cmd /c`-Fallback · 🔒 S3 (🗜 in PoDelegateTool) blockiert auf Pauls WIP-Merge.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio** (wichtiger als async Agents). Danach async Agents (Top-3 in
  [async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)).
- **TempFileTool** — Slice 2 Output-Trim 🚧 in design.
- R-CG-14 (Generation + `disposed` statt Lock) · R-CIB-4 Stufe 2 · R-CC-7 Fehlerklassen-Tabelle
  ApiRetry (❓) · Header-State-Leak (❓) · Context-Overflow-Zyklus (❓) · McpService
  `connect()`-Rollback-Test (ADR-0069).

## Notizen

- **System-Prompts generisch halten** (Paul 2026-10-20): nichts Repo-Spezifisches, minimale
  Änderungen, keine Docs-Inhalte — nur Verweise; Best Practice für alle Projekte (ABAB, C++, Java …).
- **Untracked, NICHT committen:** Pauls WIP (`org.sterl.llmpeon.core/…/agent/tools/**`,
  `bad-context-codegraph.md`, `peon-plan/`); `non-peon-ai-agents-md/` veraltet — Paul löscht selbst.
- **Tool-Trap:** `diskRenameResource` kann Erfolg melden ohne zu verschieben — nach Renames immer
  mit `diskListDirectory`/`diskSearchFiles` verifizieren.
- **Windows-Falle im Renderer:** in `lineSeparator()`-Strings immer `indexOf('\n')` suchen.
- **UserMessage-Join in `ThreadSafeMemory.add`:** Seed-Fixtures ggf. `AiMessage` als Trenner.
- **Surefire-Ground-Truth + Commit-Hash aktiv einfordern** — DONE-Claims gegen den IST prüfen.
- **Docs-Restrukturierung (2026-10-22):** Feature-Doc-Pfade sind modul-qualifiziert
  (`org.sterl.llmpeon.core/docs/x.md`, `org.sterl.llmpeon/docs/x.md`); ADRs + Root-Docs
  (Glossar, memory, open-/resolved-points, Notes) bleiben in `docs/`. Code-Referenzen folgen
  AGENTS-DEV.md-Regel. Linter-Discovery findet Modul-Roots automatisch.
