# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Stand 2026-10-21 (1) — Release-Doku-Auflösung fertig (uncommitted)

- **github-eclipse-repo.md gelöscht** (Repo-Root, Mischdokument) — Prozedur → Skill, Install →
  Homepage, Ideen → open-points. Protokolliert in [ADR-0072](adr/0072-github-eclipse-repo-cleanup.md).
- **Skill `peon-release` verschoben:** `docs/skills/peon-release/` → `.agents/skills/peon-release/`
  (war mein Fehler, falsch platziert — Whitelist hat den Rename blockiert, per Copy+Delete gelöst).
- **ADR-Nummer korrigiert:** `0001-repo-cleanup.md` → `0072-github-eclipse-repo-cleanup.md`
  (nextIds: 0071 war der höchste belegte; ADR-0001 existiert bereits — meins war ein Kollision).
- **system-prompts.md fertig:** `reviewer.md` → `review-agent.md` korrigiert, alle 13 Prompt-Dateien
  mit Wann/Warum ergänzt (compressor, default, dev-build-loop, developer, plan-write-loop, planner,
  po, po-delegation, po-tutorial, review-agent, scaffold-agent, scaffold-tutorial, search-agent).
- **AGENTS-DEV.md:** Trigger-Zeilen ergänzt — Release/Update-Site → Skill `peon-release`,
  System-Prompts → [docs/system-prompts.md](system-prompts.md).
- **open-points.md:** neuer Abschnitt „Release-Doku-Auflösung" — ❓ JAR-Signing, ❓ Auto-Create
  GitHub-Releases, ⏳ p2-Pfad-Verifikation auf peon-ai-4e.sterl.org (404 überall; Pauls These:
  alte gh-pages-URL 404, weil Repo-Root keine `index.html` mehr hat). Stray „Nachtzyklus 5"-Sektion
  (vor dem H1) ans Dateiende verschoben.
- **homepage/installation.md:30:** Update-Site-URL korrigiert (`https://peon-ai-4e.sterl.org/`).
- **Temp-Files gelöscht:** `docs/tmp-peon-4e-root.txt` (Mess-Beleg).
- **Offen:** Commit auf `story/agents-skill-dedup` — Dateien: `.agents/skills/peon-release/`,
  `docs/adr/0072-…` (+ Löschung `0001-repo-cleanup.md`), `docs/system-prompts.md`, `AGENTS-DEV.md`,
  `docs/open-points.md`, `docs/memory.md`, `homepage/src/setup/installation.md`,
  Löschung `github-eclipse-repo.md` + `docs/tmp-peon-4e-root.txt`, Löschung `docs/skills/peon-release/`. Pauls WIP ausgeschlossen.

## Stand 2026-10-20 (3) — Housekeeping-Zyklus `agents-skill-dedup` fertig (Branch `story/agents-skill-dedup`, 2 Commits, lokal)

- **`35c5219f`** — 6 neue Skills unter `.agents/skills/` (`peon-build-test`, `test-honesty`,
  `repo-api-traps`, `architecture-doc`, `plan-exploration`, `story-slicing`) + 6 Ledger-Zeilen
  (`skill-impact.md`); `AGENTS.md` gestrafft (Einzeiler + Skill-Verweise; GPT-5/o-Begründung und
  `FileLines`-Klassenliste bewusst behalten), `AGENTS-DEV.md` 184→21 Zeilen (Core→Plugin-Regel,
  ID-Marker, Scope-Drift bleiben **always-on** — bewusst NICHT in Skills, Silent-Gap-Risiko),
  `AGENTS-PO.md` 85→24 (Architektur-Template → Skill `architecture-doc`).
- **`ce3beb93`** — Prompt-Trims: `po.md` P0 (Änderungsvorschläge strukturiert: IST/SOLL/WEIL für
  Features/Fachliches, GIVEN/WHEN/THEN bei Testbarkeit/Erklärungen/Schärfungen, einzeln
  entscheidbar) · `planner.md` P3 (Exploration → Skill `plan-exploration` + Fallback-Minima) +
  P4 (Large stories → Skill `story-slicing`). Prompts greifen ab der nächsten Agent-Session.
- **Verworben:** P1 (Skill-Verweis in `developer.md` — System-Prompts sind generisch, kein
  repo-Skill-Verweis, Paul). Kein eigener `docs-id-markers`-Skill (Gate-Regel, always-on).
- **P5-Befund:** `po-delegation.md` ist ein eingefügtes Fragment von `po.md`
  (`${plan}`/`${docs}`-Platzhalter), kein Duplikat — keine Änderung nötig.
- **P2-Entscheid (A):** ID-Marker-Regel bleibt zweischichtig — `developer.md` = generische Syntax,
  `AGENTS-DEV.md` = R-Form-Trap + Gate. Kein weiterer Eingriff.
- Keine Docs-/Homepage-Änderung nötig (repo-interne Agent-Infrastruktur).
- `non-peon-ai-agents-md/` ist veraltet — **Paul löscht sie selbst**; die Inhalte leben jetzt in
  den Skills `plan-exploration`/`story-slicing`.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ **Merge/Tag 2.14.1** — `story/workspace-nesting-2026-10-19` lokal fertig (Core 1231/0,
   OSGi 399/0); vorher `mvn -B clean verify`. Smokes: Dev/Plan/Review gleicher Prozentsatz ·
   Delegation-Compact > 70 % vor Auftrag · Monorepo · Codegraph nach Neustart.
2. ⏳ **Delegations-Compact feuert pro Delegation** (Kosten, ADR-0071) — Faktor anheben oder nur
   bei Themenwechsel? Rückversicherung steht aus.
3. ❓ Fixture-Duplikat in ≥3 core-Testklassen · ❓ R-Regeln ohne Linter-Gate (`DocsLinter:241/254`)
   · ❓ In-Loop-Direkt-Compact fängt Exceptions nicht (R-CC-20.5-Nachbarloch) · ⏳ Reserved-Name
   `codegraph` · ⏳ R-CG-15 Monorepo-State-Refresh · ⏳ R-CC-19-Semantik+Tilde · ⏳ Windows-
   `cmd /c`-Fallback · 🔒 S3 (🗜 in PoDelegateTool) blockiert auf Pauls WIP-Merge.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio** (wichtiger als async Agents). Danach async Agents (Top-3 in
  [async-agents.md](async-agents.md)).
- **TempFileTool** — Slice 2 Output-Trim 🚧 in design.
- R-CG-14 (Generation + `disposed` statt Lock) · R-CIB-4 Stufe 2 · R-CC-7 Fehlerklassen-Tabelle
  ApiRetry (❓) · Header-State-Leak (❓) · Context-Overflow-Zyklus (❓) · McpService
  `connect()`-Rollback-Test (ADR-0069).

## Notizen

- **System-Prompts generisch halten** (Paul 2026-10-20): nichts Repo-Spezifisches, minimale
  Änderungen, keine Docs-Inhalte — nur Verweise; Best Practice für alle Projekte (ABAB, C++, Java …).
- **Untracked, NICHT committen:** Pauls WIP (`org.sterl.llmpeon.core/…/agent/tools/**`,
  `bad-context-codegraph.md`).
- **Windows-Falle im Renderer:** in `lineSeparator()`-Strings immer `indexOf('\n')` suchen.
- **UserMessage-Join in `ThreadSafeMemory.add`:** Seed-Fixtures ggf. `AiMessage` als Trenner.
- **Surefire-Ground-Truth + Commit-Hash aktiv einfordern** — DONE-Claims gegen den IST prüfen.
