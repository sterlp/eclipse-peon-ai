# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Stand 2026-10-22 — Zyklus ABGESCHLOSSEN: Docs-Restrukturierung

`story/docs-restructure-2026-10-22` (Basis `0a54c996`) — Da-Dok-Final-Review: CONCERNS, alle
F1–F9 aufgelöst. Gates grün: Surefire 1252/0/0/0, Lint 9 Findings = Baseline, Jar 0 `docs/`.
Plan archiviert: `peon-plan/overview-done-2026-10-22-17-38.md` (nicht committet).

- `942390f6` INC-1 Linter-Discovery (`DocRootDiscovery`, auto `docs/` + `*/docs/` + `*/*/docs/`)
- `a2616fc9` INC-2 Doc-Move: Root-Index = Register (Titel + Status, ~7.3k Zeichen), Sub-Indexe
  `org.sterl.llmpeon.core/docs/index.md` (58) + `org.sterl.llmpeon/docs/index.md` (25) mit
  Volltext; ADRs global am Root (0001–0072); 79 Renames, einzige Split-Ausnahme
  eclipse-read-tools.
- `ba1b2712` + `e82f8d8d` INC-3 Referenz-Hygiene (nur Kommentarzeilen, 68 Refs in 53 Dateien).
- `8f5e67f5` INC-4 (Jon): ADR-Pfadstrings repointet, AGENTS.md:42 `component-architecture`,
  AGENTS-DEV.md Doc-Pfad-Regel, Handovers-Block verworfen → resolved-points.md, Sub-Index-
  Linktexte, Root-Legende ⏳, Re-read-Regel um diskEditFile-Teil-Löschung erweitert.
- Follow-up (Jon) `406dcb45`: Root-Legende auf 2 Zeilen (Budget 120), AGENTS.md-Blank-Zeilen
  am Ende entfernt; dieser Commit = memory.md cycle-close.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ **Merge/Squash** `story/docs-restructure-2026-10-22` → main + Smoke (Docs-Linter-Report,
   Root-Index auto-load). Danach Branch löschen.
2. ❓ **Merge/Tag 2.14.1** — `story/workspace-nesting-2026-10-19` lokal fertig (Core 1231/0,
   OSGi 399/0); vorher `mvn -B clean verify`. Smokes: Dev/Plan/Review gleicher Prozentsatz ·
   Delegation-Compact > 70 % vor Auftrag · Monorepo · Codegraph nach Neustart.
3. ⏳ **Delegations-Compact feuert pro Delegation** (Kosten, ADR-0071) — Faktor anheben oder nur
   bei Themenwechsel? Rückversicherung steht aus.
4. ⏳ **Compact-Briefing-Schema** für `compressor.md`: 3 Ergänzungen vorgeschlagen (STANDING-RULES-Slot,
   preserve-Pfade erweitern, memory.md = complement-not-replace) — Freigabe steht aus.
5. ❓ Fixture-Duplikat in ≥3 core-Testklassen · ❓ R-Regeln ohne Linter-Gate (`DocsLinter:241/254`)
   · ❓ In-Loop-Direkt-Compact fängt Exceptions nicht (R-CC-20.5-Nachbarloch) · ⏳ Reserved-Name
   `codegraph` · ⏳ R-CG-15 Monorepo-State-Refresh · ⏳ R-CC-19-Semantik+Tilde · ⏳ Windows-
   `cmd /c`-Fallback · 🔒 S3 (🗜 in PoDelegateTool) blockiert auf Pauls WIP-Merge.

## Task (Paul 2026-10-22) — Docs-Cleanup & Schlanker Index

1. **Root-Index verschlanken:** Register raus → je Modul Link zum Sub-Index + Stichwort-Zeile
   je Abschnitts-Gruppe; Repo-Docs/Notes bleiben (Root-eigen). A8-Paritäts-Gate entfällt.
   Abnahme: Jon muss danach ohne Register arbeiten können (Sub-Index lazy je Arbeitsort;
   beide Indexe nur bei Modulgrenzen-Änderungen).
2. **Docs inhaltlich cleanen (Variante B):** Surefire-/Trail-/Beleg-Referenzen aus Docs
   ERSATZLOS löschen — kein Backfill, kein `nachweise/`-Verzeichnis (verworfen, Paul 2026-10-22).
   Evidenz-Kanäle: Commit-Messages (je Inkrement 1–2 Zeilen Surefire-Ergebnis, → AGENTS-DEV.md-
   Regel) + memory-Tools (alle Agenten sehen sie). Docs = fachliches SOLL (Paul + Jon);
   Entscheidungen „anders als naheliegend" → ADR.
3. **Paul arbeitet parallel am Code** — seine Edits ignorieren, nie committen, nicht wundern.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio** (wichtiger als async Agents). Danach async Agents (Top-3 in
  [async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)).
- **TempFileTool** — Slice 2 Output-Trim 🚧 in design.
- R-CG-14 (Generation + `disposed` statt Lock) · R-CIB-4 Stufe 2 · R-CC-7 Fehlerklassen-Tabelle
  ApiRetry (❓) · Header-State-Leak (❓) · Context-Overflow-Zyklus (❓) · McpService
  `connect()`-Rollback-Test (ADR-0069).
- Root-Index Commit-SHAs (Zeilen ~145–170) im nächsten Docs-Zyklus entfernen (Da-Dok F6)
  — überholt: entfernt im Slim-Zyklus (siehe Task 1/2).

## Notizen

- **System-Prompts generisch halten** (Paul 2026-10-20): nichts Repo-Spezifisches, minimale
  Änderungen, keine Docs-Inhalte — nur Verweise; Best Practice für alle Projekte (ABAB, C++, Java …).
- **Untracked, NICHT committen:** Pauls WIP (`org.sterl.llmpeon.core/…/agent/tools/**` —
  AgentsTool.java, AsyncMonitor.java, `peon-plan/`); `non-peon-ai-agents-md/` veraltet — Paul löscht selbst.
- **Tool-Trap:** `diskRenameResource` kann Erfolg melden ohne zu verschieben — nach Renames immer
  mit `diskListDirectory`/`diskSearchFiles` verifizieren.
- **Windows-Falle im Renderer:** in `lineSeparator()`-Strings immer `indexOf('\n')` suchen.
- **UserMessage-Join in `ThreadSafeMemory.add`:** Seed-Fixtures ggf. `AiMessage` als Trenner.
- **Surefire-Ground-Truth + Commit-Hash aktiv einfordern** — DONE-Claims gegen den IST prüfen.
- **Docs-Restrukturierung (2026-10-22):** Feature-Doc-Pfade sind modul-qualifiziert
  (`org.sterl.llmpeon.core/docs/x.md`, `org.sterl.llmpeon/docs/x.md`); ADRs + Root-Docs
  (Glossar, memory, open-/resolved-points, Notes) bleiben in `docs/`. Code-Referenzen folgen
  AGENTS-DEV.md-Regel. Linter-Discovery findet Modul-Roots automatisch.
- **`bad-context-codegraph.md` existiert nicht** (Da-Mek-Check 2026-10-23) — alter Notiz-Eintrag war stale, entfernt.
