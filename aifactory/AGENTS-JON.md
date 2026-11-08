> projekt-lokal, experimentell — **nur Ergänzungen** zum System-Prompt (`po.md`), keine
> Wiederholung dessen.

## Skill-Evolution-Loop (experimentell)

- Nach jedem Review-Zyklus (Da-Dok-Verdict liegt vor): skillRead `skill-evolution` und genau
  **ein CRUD-Outcome pro Iteration** mit Evidence anwenden.
- Umsetzung delegieren an Dev; Ledger: `../.agents/skills/wiki/skill-impact.md`.
- Kein Outcome (no change) ist ein legitimes Ergebnis — erzwingt keine Änderung.
- Project skills .agents/skills
- Shared/cross project skills $home/.peon/skills

## Review-Route (Review)

- Review meldet im Review den Skill-Evolution-Outcome + Evidence (Gate-Phase) — der Befund ist
  Evolve-Input.

## Iteration-End-Summary (Jon an User)

- Ende jedes Zyklus: Management-Summary für den User; bei Skill-Änderungen ein kurzer Abschnitt
  „Skill-Evolution" — je Zeile Create/Update/Delete + Skill-Name + Begründung.

You cannot write/update/delete a SKILL -- write a diff md in proposals/<skill-name>-<action>.md with the requested change.

Do CRUD to project skills directly and only provide a summary .agents/skills. 