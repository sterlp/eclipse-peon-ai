# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Stand 2026-10-23 — Docs-Slim-Zyklus ABGESCHLOSSEN

- Branch `story/docs-restructure-2026-10-22` @ `cb458f0f` — **einziger Docs-Branch** (slim per FF
  hineingemerged + gelöscht, Paul-Wunsch: nur EIN Merge nach main). Alle Gates grün (Core-Suite +
  Plugin-Fragment, PDE-Bau), Da-Dok-Review bestanden — **Merge nach main FREI** (Paul).
  Danach: `releng/llmpeon-target` wieder installen, Branch löschen.
- Branch trägt auch **Pauls Code-WIP** (`fb66b8b4`: AgentsTool/AsyncStreamingBridge/AsyncMonitor +
  AbstractAgent/AiAgent/Timer/ToolLoopRequest; `573eb601`: `org.sterl.llmpeon/pom.xml` −1 Zeile) —
  für diese Teile lief kein Core-Suite-/Plugin-Build.
- **Skill-Evolution:** `peon-build-test` gepatcht (Core-Single-Class-Gate offline OHNE `-am`/Reactor,
  `clean` zwingend). Ledger `.agents/skills/wiki/skill-impact.md` = Accepted.
- **Neu im Zyklus:** OP-STREAM-1 (Stream-Hänger bei Verbindungsverlust/Notebook-Sleep, keine
  Aliveness-Erkennung → streaming-display.md) · ADR-0073 (slf4j-api bleibt gebündelt, OSGi-Resolve
  negativ bewiesen) · SwtUi-Guard (Tests skippen headless statt NPE).
- **README-Befund (Paul fragen, nicht selbst ändern):** Update-Site-Konflikt — README L48 +
  open-points sagen `https://sterlp.github.io/eclipse-peon-ai/` (2026-10-21), memory-Tool #46 sagt
  `https://peon-ai-4e.sterl.org/` (ebenfalls 2026-10-21). Beide datieren denselben Tag — Konflikt,
  hängt am offenen ⏳ p2-Pfad-Verifikation. Plus Proposal-only: doppelte „## Links"-Sektionen,
  Emoji-Legende, „very old stuff" in README.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ **Merge/Squash** Docs-Branch → main + Smoke (Docs-Linter-Report, Root-Index auto-load). Danach Branch löschen.
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
6. ❓ **Testnamen-im-Fließtext-Slice** (Paul-Entscheid 2026-10-19, noch nicht gefahren) ·
   ❓ Linter-Guard gegen Evidenz-Drift (positiv nachweisen) · vorbestehende R-CC-18-/R-DEF-Doppedefinitionen.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio** (wichtiger als async Agents). Danach async Agents (Top-3 in
  [async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)).
- **TempFileTool** — Slice 2 Output-Trim 🚧 in design.
- Lint-Baseline-Befunde in einen Docs-Zyklus aufnehmen.
- R-CG-14 (Generation + `disposed` statt Lock) · R-CIB-4 Stufe 2 · R-CC-7 Fehlerklassen-Tabelle
  ApiRetry (❓) · Header-State-Leak (❓) · Context-Overflow-Zyklus (❓) · McpService
  `connect()`-Rollback-Test (ADR-0069).

## Notizen

- **System-Prompts generisch halten** (Paul 2026-10-20): nichts Repo-Spezifisches, minimale
  Änderungen, keine Docs-Inhalte — nur Verweise; Best Practice für alle Projekte.
- **Commit-Regel:** immer EXPLIZITE Pfadliste (`git add -- docs/ ...`), nie `git add -u`;
  Pauls WIP nie committen; Surefire-Ground-Truth + Commit-Hash aktiv einfordern, DONE-Claims
  gegen den IST prüfen.
- **Tool-Trap:** `diskRenameResource` kann Erfolg melden ohne zu verschieben — nach Renames immer
  verifizieren. Edit-Fallen (replaceLines-Insert, Edit-Deletion-Teil-Match): nach JEDEM Edit
  Zielbereich zurücklesen + Dubletten-Check; Ganzdatei-Rewrites via `diskWriteFile` bevorzugen.
- **Windows-Falle im Renderer:** in `lineSeparator()`-Strings immer `indexOf('\n')` suchen.
- **UserMessage-Join in `ThreadSafeMemory.add`:** Seed-Fixtures ggf. `AiMessage` als Trenner.
- **Docs-Restrukturierung (2026-10-22):** Feature-Doc-Pfade sind modul-qualifiziert
  (`org.sterl.llmpeon.core/docs/x.md`, `org.sterl.llmpeon/docs/x.md`); ADRs + Root-Docs
  (Glossar, memory, open-/resolved-points, Notes) bleiben in `docs/`. Linter-Discovery findet
  Modul-Roots automatisch.
- **Issues/**: asm-Versions-Tabellen (issue-142) sind Fachinhalt, keine Evidenz — bewusst behalten.
