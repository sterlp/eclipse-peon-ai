# Skill-Evolution-Loop

**Ziel:** Skills müssen ihren Token-Wert beweisen — minimaler Extra-Token-Aufwand, CRUD über den
Lebenszyklus (Create/Read/Update/Delete), und der Loop selbst kann reifen. Der Loop ist
**experimentell** und lebt deshalb projekt-lokal in AGENTS-*-Dateien, **nicht** in
Built-in-Prompts (User 2026-09-09) — dort kann er billig iterieren, bis er sich bewährt.

**Basis:** der bestehende Skill `skill-evolution` (seit Story B in `.agents/skills` — siehe
[project-skills.md](project-skills.md)) samt Ledger `wiki/`
(Patterns + accepted/rejected History in `skill-impact.md`).

## Business Rules

### Read — Nutzung sichtbar machen

- **R1 ✅** `skillRead`/`skillReadFile` liefern einen kurzen Usefulness-Footer (max
  ~2 Zeilen, Token-Disziplin): *Reporte in deiner Antwort — hilfreich? falsch/veraltet/
  unvollständig? obsolet?* Kein neues Tool, reiner Text-Hook am Read-Ergebnis.
  - GIVEN ein Agent liest einen Skill WHEN das Ergebnis gerendert wird THEN endet es mit dem
    Usefulness-Report-Aufruf
- **R2 ✅** Read-only-Agenten (Da Thinka, Da Dok) reporten den Outcome **im Reply**;
  Schreibende (Da Mek, `ALLOW_ALL`) können zusätzlich in-place fixen — liest Da Mek via
  `skillRead`, fixt er im selben Turn. *(AGENTS-PO.md/AGENTS-DEV.md 2026-09-09)*
- **R3 ✅** Jon wendet nach jedem Review-Zyklus den skill-evolution-Loop an — genau ein
  CRUD-Outcome pro Iteration, mit Evidence:
  - **Create:** schwierige, gelöste Aufgaben werden zu einem neuen Skill, wenn die Lösung
    wiederverwendbar ist (nicht Project-Spezifisch-dokumentiertes).
  - **Update:** bewährte Skills werden verbessert — **kurz halten** (Prozedur, keine Traces);
    Wächst ein Skill, wird gestrafft statt erweitert.
  - **Delete:** Skills, die mehr schaden als nutzen (falsch, veraltet, missbraucht, nie genutzt
    trotz Chance) werden gelöscht — Löschung braucht dieselbe Evidence-Pflicht wie eine Änderung.
  - Umsetzung (Create/Update/Delete) delegiert Jon an **Da Mek**; Scaffold bleibt für künftige
    Global-Skills (Config-Dir) reserviert. Ledger-Eintrag (`wiki/skill-impact.md`) je Outcome.
  - GIVEN Review-Feedback zu einem Skill WHEN Jon den Loop anwendet THEN genau ein CRUD-Outcome
    wird festgehalten und ggf. an Da Mek delegiert
- **R4 ✅** Da Dok meldet im Review den Skill-Evolution-Outcome + Evidence (Gate-Phase
  des skill-evolution-Skills) — ein Checklist-Punkt, kein eigener Loop. *(AGENTS-PO.md)*
- **R5 ✅** Änderungen an Skills (Create/Update/Delete inkl. Begründung in einer Zeile)
  nimmt Jon in das **Iteration-End-Summary** auf — kurzer Abschnitt „Skill-Evolution" als
  Management-Summary für den User nach jedem Zyklus.
  - GIVEN ein Zyklus endet WHEN Jon das Summary schreibt THEN enthält es bei Skill-Änderungen
    den kurzen Abschnitt; ohne Änderungen entfällt er
- **R6 ✅** Kein Built-in-Prompt (po.txt, review-agent.txt) wird geändert — der Loop
  lebt per projekt-lokaler `AGENTS-PO.md` und `AGENTS-DEV.md` an die Agenten, solange er in
  Erprobung ist. **Der Loop-Text selbst darf reifen:** jede Iteration darf die AGENTS-*-Passagen
  nachschärfen (das ist Update nach R3, angewendet auf sich selbst). Wanderung in die
  Built-in-Prompts = späterer eigener User-Schritt. *(AGENTS-PO.md Header-Marker)*
- **R7 ✅** Retro-Relation („memory vs. Skill"): cross-projekt-dauerhafte
  Verhaltensregeln → memory*-Tools; projekt-lokale, wiederverwendbare Prozeduren → Skill. Keine
  Doppelhaltung desselben Inhalts. *(AGENTS-PO.md)*

## Umsetzungshinweise

- R1 ✅ = `SkillTool`-Footer (inc-1, `b4e918d`).
- R2–R7 ✅ = AGENTS-PO.md (neu, 2026-09-09) + AGENTS-DEV.md-Ergänzung — kein Java.
- Ledger-Pfad konventionsgetreu: `<repo>/.agents/skills/wiki/skill-impact.md` (seit Story B
  tatsächlich dort; `wiki/` hat kein SKILL.md → wird vom Scan ignoriert, bleibt als Ledger-Dir).
- Token-Budget: Footer ~2 Zeilen; Loop-Anwendung kostet nur im Review-Schritt — kein per-Turn
  Overhead.

## BDD-Test-Mapping (Plan-Nachweis je Regel)

| Regel | Test (vorgeschlagen) |
|---|---|
| R1 | `SkillToolTest.skillReadAppendsUsefulnessFooter` (+ not-found footer-frei AND-step) |
| R2–R7 | Prompt/Verhalten — manuelle Verifikation (AGENTS-*-Konvention, wie Prompts-Konvention) |
