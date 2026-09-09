# Skill-Evolution-Loop

**Ziel:** Skills müssen ihren Token-Wert beweisen — Agenten reporten nach jeder Nutzung, ob ein
Skill hilfreich war, korrekt oder obsolet; Jon konsolidiert das Feedback nach jedem Review-Zyklus
und lässt Änderungen über Da Mek umsetzen. Der Loop ist **experimentell** und lebt deshalb
projekt-lokal in AGENTS-*-Dateien, **nicht** in Built-in-Prompts (User 2026-09-09).

**Basis:** der bestehende Skill `skill-evolution` (Repo, wird mit dem Skills-Move nach
`.agents/skills` wandern — siehe [project-skills.md](project-skills.md) Out-of-Scope/Move) samt
seinem Ledger `wiki/` (Patterns + accepted/rejected History in `skill-impact.md`).

## Business Rules

- **R1 ❌ specified** `skillRead`/`skillReadFile` liefern einen kurzen Usefulness-Footer (~2 Zeilen
  Token): *Reporte in deiner Antwort — war der Skill hilfreich? Falsch, veraltet, unvollständig?
  Obsolet?* Kein neues Tool, reiner Text-Hook am Read-Ergebnis.
  - GIVEN ein Agent liest einen Skill WHEN das Ergebnis gerendert wird THEN endet es mit dem
    Usefulness-Report-Aufruf
- **R2 ❌ specified** Read-only-Agenten (Da Thinka, Da Dok) reporten den Outcome **im Reply**;
  Schreibende (Da Mek, `ALLOW_ALL`) können zusätzlich in-place fixen — mit Story A (R16) liest
  Da Mek via `skillRead` und fixt im selben Turn.
- **R3 ❌ specified** Jon konsolidiert nach jedem Review-Zyklus den Skill-Evolution-Outcome
  gemäß `skill-evolution`: genau ein Ergebnis pro Iteration — `no change` / `patched <skill>` /
  `created <skill>` / `obsolete → delete`. Umsetzung (Patch/Create/Delete) delegiert Jon an
  **Da Mek**; Scaffold bleibt für künftige Global-Skills (Config-Dir) reserviert.
  - GIVEN Review-Feedback zu einem Skill WHEN Jon den Loop anwendet THEN genau ein Outcome wird
    festgehalten (Ledger `wiki/skill-impact.md`) und ggf. an Da Mek delegiert
- **R4 ❌ specified** Da Dok meldet im Review den Skill-Evolution-Outcome + Evidence (Gate-Phase
  des skill-evolution-Skills) — ein Checklist-Punkt, kein eigener Loop.
- **R5 ❌ specified** Kein Built-in-Prompt (po.txt, review-agent.txt) wird geändert — der Loop
  wird per projekt-lokaler `AGENTS-PO.md` und `AGENTS-DEV.md` an die Agenten gegeben, solange er
  in Erprobung ist. Wanderung in die Built-in-Prompts = späterer eigener Schritt (User-Entscheid).
- **R6 ❌ specified** Retro-Relation („memory vs. Skill"): cross-projekt-dauerhafte
  Verhaltensregeln → memory*-Tools; projekt-lokale, wiederverwendbare Prozeduren → Skill. Keine
  Doppelhaltung desselben Inhalts.

## Umsetzungshinweise

- R1 = Änderung an `SkillPromptFile.renderBody()` bzw. `SkillTool`-Rückgabe (keine Prompt-Datei).
- R3/R4/R6 = AGENTS-PO.md / AGENTS-DEV.md Text (projekt-lokal) — kein Java.
- Ledger-Pfad konventionsgetreu: `<repo>/.agents/skills/wiki/skill-impact.md` nach dem Move
  (`wiki/` hat kein SKILL.md → wird vom Scan ignoriert, bleibt als Ledger-Dir erhalten).

## BDD-Test-Mapping (Plan-Nachweis je Regel)

| Regel | Test (vorgeschlagen) |
|---|---|
| R1 | `SkillToolTest.skillReadAppendsUsefulnessFooter` |
| R2–R6 | Prompt/Verhalten — manuelle Verifikation (AGENTS-*-Konvention, wie Prompts-Konvention) |
