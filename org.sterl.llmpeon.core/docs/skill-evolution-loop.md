# Skill-Evolution-Loop

**Ziel:** Skills müssen ihren Token-Wert beweisen — minimaler Extra-Token-Aufwand, CRUD über den
Lebenszyklus (Create/Read/Update/Delete), und der Loop selbst kann reifen. Jedes Projekt
entwickelt seine eigenen Skills unter `.agents/skills` (siehe [project-skills.md](project-skills.md));
generische Passagen aus AGENTS*.md wandern schrittweise dorthin.

**Basis:** Grund-Loop im Built-in-Prompt `po-delegation.md` (R6). Optional der Skill
`skill-evolution` mit Wiki `.agents/skills/wiki/` (Patterns + accepted/rejected History in
`skill-impact.md`); projekt-lokale Zusätze in `AGENTS-JON.md`/`AGENTS-DEV.md`.

## Business Rules

### Read — Nutzung sichtbar machen

- **R1 ✅** `skillRead`/`skillReadFile` liefern einen kurzen Usefulness-Footer (max
  ~2 Zeilen, Token-Disziplin): *Reporte in deiner Antwort — hilfreich? falsch/veraltet/
  unvollständig? obsolet?* Kein neues Tool, reiner Text-Hook am Read-Ergebnis.
  - GIVEN ein Agent liest einen Skill WHEN das Ergebnis gerendert wird THEN endet es mit dem
    Usefulness-Report-Aufruf
- **R2 ✅** Read-only-Agenten (Plan, Review) reporten den Outcome **im Reply**;
  Schreibende (Dev, `ALLOW_ALL`) können zusätzlich in-place fixen — liest Dev via
  `skillRead`, fixt er im selben Turn. *(AGENTS-JON.md/AGENTS-DEV.md 2026-09-09)*
- **R3 ✅** Jon wendet nach jedem Review-Zyklus den skill-evolution-Loop an — genau ein
  CRUD-Outcome pro Iteration, mit Evidence:
  - **Create:** schwierige, gelöste Aufgaben werden zu einem neuen Skill, wenn die Lösung
    wiederverwendbar ist (nicht Project-Spezifisch-dokumentiertes).
  - **Update:** bewährte Skills werden verbessert — **kurz halten** (Prozedur, keine Traces);
    Wächst ein Skill, wird gestrafft statt erweitert.
  - **Delete:** Skills, die mehr schaden als nutzen (falsch, veraltet, missbraucht, nie genutzt
    trotz Chance) werden gelöscht — Löschung braucht dieselbe Evidence-Pflicht wie eine Änderung.
  - Umsetzung (Create/Update/Delete) delegiert Jon an **Dev**; Scaffold bleibt für künftige
    Global-Skills (Config-Dir) reserviert. Ledger-Eintrag (`wiki/skill-impact.md`) je Outcome.
  - GIVEN Review-Feedback zu einem Skill WHEN Jon den Loop anwendet THEN genau ein CRUD-Outcome
    wird festgehalten und ggf. an Dev delegiert
- **R4 ✅** Review meldet im Review den Skill-Evolution-Outcome + Evidence (Gate-Phase
  des skill-evolution-Skills) — ein Checklist-Punkt, kein eigener Loop. *(AGENTS-JON.md)*
- **R5 ✅** Änderungen an Skills (Create/Update/Delete inkl. Begründung in einer Zeile)
  nimmt Jon in das **Iteration-End-Summary** auf — kurzer Abschnitt „Skill-Evolution" als
  Management-Summary für den User nach jedem Zyklus.
  - GIVEN ein Zyklus endet WHEN Jon das Summary schreibt THEN enthält es bei Skill-Änderungen
    den kurzen Abschnitt; ohne Änderungen entfällt er
- **R6 ✅ done (2026-11-09, gebaut mit `88b81a8c`)** Der Grund-Loop (je Zyklus genau ein Skill-Ergebnis; Quellen: Skill-Vorschläge und
  -Probleme der Agenten sowie generische AGENTS*.md-Passagen, die schrittweise in Projekt-Skills
  unter `.agents/skills` wandern) steht im Built-in-Prompt `po-delegation.md` (Retro). Er läuft
  damit in jedem Projekt, auch ohne den Skill `skill-evolution` und ohne Wiki (Paul, 2026-11-09).
  Projekt-lokal in `AGENTS-JON.md`/`AGENTS-DEV.md` bleibt nur, was darüber hinausgeht.
  - GIVEN ein Projekt ohne Skill `skill-evolution` und ohne `.agents/skills/wiki` WHEN Jon die
    Retro eines Zyklus macht THEN hält er genau ein Skill-Ergebnis fest und lässt es von Dev in
    `.agents/skills` schreiben
- **R7 ✅** Retro-Relation („memory vs. Skill"): cross-projekt-dauerhafte
  Verhaltensregeln → memory*-Tools; projekt-lokale, wiederverwendbare Prozeduren → Skill. Keine
  Doppelhaltung desselben Inhalts. *(AGENTS-JON.md)*

## Umsetzungshinweise

- R1 ✅ = `SkillTool`-Footer (inc-1).
- R2–R5, R7 ✅ = AGENTS-JON.md (neu, 2026-09-09) + AGENTS-DEV.md-Ergänzung — kein Java.
- R6 ❌ = Prompt-Text in `po-delegation.md` (Retro), kein Java.
- Ledger-Pfad konventionsgetreu: `<repo>/.agents/skills/wiki/skill-impact.md` (seit Story B
  tatsächlich dort; `wiki/` hat kein SKILL.md → wird vom Scan ignoriert, bleibt als Ledger-Dir).
- Token-Budget: Footer ~2 Zeilen; Loop-Anwendung kostet nur im Review-Schritt — kein per-Turn
  Overhead.

## BDD-Test-Mapping (Plan-Nachweis je Regel)

| Regel | Test (vorgeschlagen) |
|---|---|
| R1 | `SkillToolTest.skillReadAppendsUsefulnessFooter` (+ not-found footer-frei AND-step) |
| R2–R7 | Prompt/Verhalten — manuelle Verifikation (AGENTS-*-Konvention, wie Prompts-Konvention) |
