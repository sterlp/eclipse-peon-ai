# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Stand 2026-10-23 — Zyklus: Docs-Slim (Task „Docs-Cleanup & schlanker Index")

Branch `story/docs-slim-2026-10-22` (Basis = INC-A auf `story/docs-restructure-2026-10-22`).

- **INC-A committed:** Root-Index = Link-Dokument (~3.2k Zeichen, Register raus) + AGENTS-DEV
  Gate-Evidenz-Regel. A8-Paritäts-Gate existiert NICHT im Code (Da-Thinka-VOLL-Grep: 0 Treffer).
- **INC-B (Evidence-Sweep) fertig im Working Tree, UNCOMMITTED:** alle Commit-Hashes,
  Surefire-/Test-Zahlen und Messwerte aus `docs/**` entfernt (Root-Docs, beide Sub-Indexe,
  ~40 Feature-Docs, 8 ADRs + Index, AGENTS-DEV-Präzision „nur tatsächlich gefahrene Gates").
  Struktur-Entscheidungen (D1–D6) siehe Compact-Zusammenfassung: Root-Index = Link-Doc,
  `nachweise/` verworfen (Paul 2026-10-22), Variante B prospektiv, Branch = slim auf restructure.
- Spezial-Fixes im Sweep: compact-lock Beleg-Tabelle raus · test-setup Zahlen-Tabelle raus
  (by-design-Skip-Liste bleibt, ohne Stückzahlen) · R-DL-26 ❌→✅ (DocRootDiscovery, gebaut im
  Restrukturierungs-Zyklus) · icons.md Inc-Hash-Ketten raus, S1/S2-Labels bleiben.
- **Lint nach Flip:** keine DL-26-Findings; 14 UNBELEGT_ERLEDIGT + 9 VERWAIST = Baseline-Bestand
  unberührter Docs (agent-tool-config, agent-tool-filter, docs-linter:778+, tool-output-disclosure,
  tool-time-disclosure, UC-DEF-Test-Marker) — Vorab-Kandidaten für den nächsten Docs-Zyklus.
- **Nächster Schritt:** INC-B committen (Da Mek, explizite Pfadliste, Pauls WIP-Dateien NICHT),
  danach Da-Dok-Review des Slim-Zyklus, dann Sub-Index-Abnahme bei Paul.

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
