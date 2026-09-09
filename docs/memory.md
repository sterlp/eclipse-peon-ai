# Session-Stand (2026-09-09)

## Git-Stand

- **`story/133`** — Project Skills + Review-Agent komplett, zusätzlich heute:
  - Team-Header-Order-Fix `ec2d754` + Docs-Followup `d5ca69a` + Rename-Test-Fix `7f89af5`
    (dst-Cleanup, Fixture-Leichen weg, 17/17 grün).
  - Core 695/0 · Plugin 193/0. **Merge/Squash → main + Push = User-Entscheidung.**
- Nicht committet: User-Assets chat/ (bewusst).

## Skill-Feature (2026-09-09, User-Entscheid)

- **3 Stories, Reihenfolge A→B→C** (User bestätigt):
  - **A: SkillComponent-Refactor — ✅ KOMPLETT (2026-09-09):** inc-1 `23fbaa3` (Rename) ·
    inc-2 `024d151` (R14: kein @Nullable, EMPTY_PATH, Instanz-Tausch, kein Cache) ·
    inc-3 `c19ecd7` (R15: DiskFileWriteTool.afterWrite → refreshAll, 7/7 Methoden) ·
    inc-4 `5eff54f` (R16: Jon bekommt geteilten SkillTool) · Plan-Archiv + Docs-Flip `3d6c2d5`.
    Core 698/0 · Plugin 194/0. Review Da Dok: ACCEPTED. M1 (R15 AND-step nicht falsifizierbar —
    characterization ok) + M2 (cosmetic „slot"-Namensdrift in alten Tests, Jon macht Docs-Seite).
    User bestätigt: erster Scaffold-Write in fehlendes Dir → Dirs angelegt, sofort sichtbar
    (gewähltes Projekt + Config); nicht-gewählte Projekte erst bei Wechsel (R2c) — final ok.
  - **B: Skills-Move (Jon selbst, kein Dev)** — User-Details (2026-09-09): **alle** Skills zusammen
    nach `.agents/skills` (Repo-Root); danach erreichen Agenten sie via Skill-Tools → **AGENTS.md:61-63
    Skill-Directory-Hinweis ENTFERNEN** (nicht umschreiben), AGENTS-DEV.md:97-98 minimal halten
    (nur skill-evolution-Bezug). `test-skill.md` löschen (User-OK). `wiki/` bleibt Ledger-Dir
    (kein SKILL.md → Scan ignoriert). Smoketest bestätigt: Skills in .agents/skills werden sichtbar.
    **Story A macht AGENTS.md-Hinweis überflüssig — Jon hat jetzt SkillTool selbst.**
  - **C: Skill-Evolution-Loop** — `skill-evolution-loop.md` R1–R6 ❌: Usefulness-Footer in
    skillRead (~2 Zeilen), Jon konsolidiert pro Review (no change/patched/created/obsolete→delete,
    Umsetzung via **Da Mek** — User bestätigt; Scaffold nur für spätere Global-Skills), Da Dok
    meldet Outcome. **Experimentell → AGENTS-PO.md/AGENTS-DEV.md (projekt-lokal), KEINE
    Built-in-Prompts (po.txt/review-agent.txt)** — Wanderung später = eigener User-Schritt.
- Glossar: „Skill-Slot" → „Skill-Component" umbenannt. test_project/.agents/test/SKILL.md des
  Users lag falsch (fehlt `skills`-Level) → `.agents/skills/test/SKILL.md`.

## Offen: 2 staged Änderungen (User-Rückfrage gestellt, unbeantwortet)

- **po.txt staged:** „Da Dok" → „Da Doc" in Zeile 33 — vermutlich Typos-Drift, widerspricht
  Glossar (Da Dok). Empfehlung: revert. User-Bestätigung ausstehend.
- **test_project/.agents/skills/test/SKILL.md staged deletion:** Test-Skill am falschen Pfad.
  Ok wenn nicht mehr gebraucht; sonst korrekt platzieren.

## Nächste Schritte

1. **User:** Merge story/133 → main + Push (Project Skills + Review Agent + Story A komplett grün).
2. **Staged po.txt/test-SKILL.md klären** (siehe oben) — dann committen oder verwerfen.
3. **Story B: Skills-Move** (mache ich selbst): `skills/` → `.agents/skills`, AGENTS.md:61-63
   Hinweis entfernen, AGENTS-DEV.md:97-98 minimal, test-skill.md löschen, wiki/ bleibt.
4. **Story C: Learning-Loop** planen (planWithPlanAgent) — R1 Java-Footer, Rest AGENTS-*-Text.
5. Memory-Leak-Hunt (eigener Zyklus, frischer Context).
6. Bug-Fix-Zyklus: Triage #5–#15 + ApiRetry-Verdacht + AgentOrder-Edge.
7. Offene ❓: Glossar eager · buildWithDev-Compact (open-points.md).

## Geparkt / Wissenswert

- E2E-Tool-Namens-Falle: `diskRenameResource`/`eclipseRenameResource`.
- copy-tools-e2e-test.md `diskRenameResource`-Korrektur — kosmetisch, steht aus.
- ⏳ Query-Caches · ⏳ Streaming-Präzisierungen · ⏳ Edit-Tools (open-points.md).
- Leere Test-Fixture-Dirs (sub/, copyDirSrc/) überleben Cleanup — unsichtbar für git, bewusst
  kein Sweep (Da Mek-Entscheid, User kann anders entscheiden).
- Review-Agent Bewährung: F1+F2 wären sonst in den Merge gegangen.
