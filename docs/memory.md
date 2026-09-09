# Session-Stand (2026-09-09)

## Git-Stand

- **`story/133`** — Project Skills + Review-Agent komplett, zusätzlich heute:
  - Team-Header-Order-Fix `ec2d754` + Docs-Followup `d5ca69a` + Rename-Test-Fix `7f89af5`
    (dst-Cleanup, Fixture-Leichen weg, 17/17 grün).
  - Core 695/0 · Plugin 193/0. **Merge/Squash → main + Push = User-Entscheidung.**
- Nicht committet: User-Assets chat/ (bewusst).

## Skill-Feature — NEU (2026-09-09, User-Entscheid)

- **3 Stories, Reihenfolge A→B→C** (User bestätigt):
  - **A: SkillComponent-Refactor** — `project-skills.md` R14–R16 ❌: Rename SkillSlot→SkillComponent,
    kein @Nullable dir (Service tauscht leere Component, 2 Felder bleiben, KEIN Cache pro Pfad),
    Scaffold-Write→refreshAll() deterministisch, Jon bekommt geteilten SkillTool (Wiring-Lücke:
    poToolService vergaß ihn, Sklaven haben ihn alle).
  - **B: Skills-Move (Jon selbst, kein Dev)** — User-Details (2026-09-09): **alle** Skills zusammen
    nach `.agents/skills` (Repo-Root); danach erreichen Agenten sie via Skill-Tools → **AGENTS.md:61-63
    Skill-Directory-Hinweis ENTFERNEN** (nicht umschreiben), AGENTS-DEV.md:97-98 minimal halten
    (nur skill-evolution-Bezug). `test-skill.md` löschen (User-OK). `wiki/` bleibt Ledger-Dir
    (kein SKILL.md → Scan ignoriert). Smoketest bestätigt: Skills in .agents/skills werden sichtbar.
  - **C: Skill-Evolution-Loop** — `skill-evolution-loop.md` R1–R6 ❌: Usefulness-Footer in
    skillRead (~2 Zeilen), Jon konsolidiert pro Review (no change/patched/created/obsolete→delete,
    Umsetzung via **Da Mek** — User bestätigt; Scaffold nur für spätere Global-Skills), Da Dok
    meldet Outcome. **Experimentell → AGENTS-PO.md/AGENTS-DEV.md (projekt-lokal), KEINE
    Built-in-Prompts (po.txt/review-agent.txt)** — Wanderung später = eigener User-Schritt.
- Glossar: „Skill-Slot" → „Skill-Component" umbenannt. test_project/.agents/test/SKILL.md des
  Users lag falsch (fehlt `skills`-Level) → `.agents/skills/test/SKILL.md`.

## Nächste Schritte

1. **User:** Merge story/133 → main + Push.
2. **Story A planen lassen** (planWithPlanAgent) — erster Zyklus des neuen Features.
3. Danach B (mache ich selbst), dann C.
4. Memory-Leak-Hunt (User 2026-09-09, eigener Zyklus, frischer Context).
5. Bug-Fix-Zyklus: Triage #5–#15 + ApiRetry-Verdacht + AgentOrder-Edge.
6. Offene ❓: Glossar eager · buildWithDev-Compact (open-points.md).

## Geparkt / Wissenswert

- E2E-Tool-Namens-Falle: `diskRenameResource`/`eclipseRenameResource`.
- copy-tools-e2e-test.md `diskRenameResource`-Korrektur — kosmetisch, steht aus.
- ⏳ Query-Caches · ⏳ Streaming-Präzisierungen · ⏳ Edit-Tools (open-points.md).
- Leere Test-Fixture-Dirs (sub/, copyDirSrc/) überleben Cleanup — unsichtbar für git, bewusst
  kein Sweep (Da Mek-Entscheid, User kann anders entscheiden).
- Review-Agent Bewährung: F1+F2 wären sonst in den Merge gegangen.
