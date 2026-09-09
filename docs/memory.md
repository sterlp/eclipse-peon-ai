# Session-Stand (2026-09-09)

## Git-Stand

- **`story/133` @ `ac11023`** — Project-Skills-Zyklus **komplett** (inc-1 `f85c6f0` · inc-2
  `d087eb0` · inc-3 `910b8bd` · Delta inc-4 `7e2320a` · inc-6 `94a6499` · Plan-Archiv `ac11023`).
  **Merge/Squash in main + Push = User-Entscheidung.**
- Core **688/0** · Plugin **193/0**. Plan archiviert: overview-done-2026-09-09-09-23.md.
- ⚠️ Nicht committet (User-WIP, kompiliert NICHT): Review-Agent-Refactor (`PoDelegateTool` 3-
  Agent-Ctor + Context-Fn; `PoDelegateToolTest.java:45` nutzt alten 2-Agent-Ctor → testCompile rot)
  — User fragen, ob er fertigstellt oder Da Mek (`AiReviewAgent`, `BuildPoAgentComponent` berühren).
- test_project-Rename-Test-Leftovers (renameRootDst_*, sub/) untracked — Rename-Test-Cleanup
  verpasst vermutlich; mit Bug-Fix-Zyklus anschauen.

## Zyklus `story/133` — Project Skills — ABGESCHLOSSEN ✅ (2026-09-09)

- Design: docs/project-skills.md R1–R13 (alle ✅) + ADR-0042 Rev 2 (Slot-Components, disunkte
  Maps, Override nur beim Lesen) + ADR-0043 (DynamicRootsWriteValidator, configDir + .agents/skills
  aller offenen Projekte, kein Instanz-Merge). Branch-Namens-Abweichung: User-Wunsch `story/133`
  statt project-skills-2026-09-08.
- Review (Da Thinka, 3-Seiten): 1 Abweichung (Test-Name, Docs korrigiert) · 1 Risiko→inc-4
  (Tag-Strip-Guard in get() gegen False-Negative beim Echo) · 1 PO-Lücke→inc-6 (Homepage) ·
  Mutation-Proof bestanden (Merge-Order-Flip → 2 Tests rot → revert, beide Fänger wie vorhergesagt).
- **UI manuell prüfen (SWT, Da Mek-Checkliste):** Zähler „N skills (M project)" · Menü-Sektion
  „Project skills — <name>" · Slash-Autocomplete `[project]`-Suffix · Namen im Dialog unsuffigiert.

## Nächste Schritte

1. **User:** Review-Agent-WIP entscheiden (selbst fertigstellen vs. Da Mek) — Blocker für
   testCompile auf story/133.
2. **User:** Merge story/133 → main (nach WIP-Entscheidung, da WIP auf gleichem Branch liegt).
3. UI-Checkliste (oben) manuell durchklicken; Homepage `homepage/src/setup/agents-and-skills.md`
   überfliegen (inc-6, User-Edit des yaml-Blocks möglich).
4. Bug-Fix-Zyklus: Triage #5–#15 + ApiRetry-Verdacht (❓ open-points.md, Memory #21) + Plugin-Hunt
   + AgentOrder Auto-Create-Edge (agent-ordering.md) + Rename-Test-Leftovers.
5. Offene ❓: Glossar eager · buildWithDev-Compact — Empfehlungen in open-points.md.
6. Ideen-Backlog: Jon×Scaffold (Scaffold als Jon-Sub-Agent, out-of-scope project-skills) ·
   builtin-agent-prompt-override (🚧) · eclipseJavaMoveType (🚧).

## Geparkt / Wissenswert

- E2E-Tool-Namens-Falle: `diskRenameResource`/`eclipseRenameResource` (nicht *RenameFile).
- copy-tools-e2e-test.md `diskRenameResource`-Korrektur — kosmetisch, steht aus.
- ⏳ Query-Caches · ⏳ Streaming-Präzisierungen · ⏳ Edit-Tools (open-points.md) — unverändert.
- Da Mek STOP-AND-ASK-Regel (memory-Tool #29): bewährt — hat Branch-Abweichung (story/133) und
  WIP-Konflikt sauber eskaliert statt still zu workarounden.
