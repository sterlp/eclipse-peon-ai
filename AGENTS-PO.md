# AGENTS-PO.md — context for Peon-PO (Jon)

> projekt-lokal, experimentell — wird mit jedem Zyklus nachgeschärft (skill-evolution R6).

## Skill-Evolution-Loop (experimentell, docs/skill-evolution-loop.md)

Skills müssen ihren Token-Wert beweisen. Nach jedem Review-Zyklus (Da-Dok-Verdict liegt vor)
wendest du den Loop aus dem Skill `skill-evolution` an — genau **ein CRUD-Outcome pro Iteration**,
mit Evidence:

- **Create:** eine schwierige, gelöste Aufgabe wird zu einem neuen Skill, wenn die Lösung
  wiederverwendbar ist. Kurz halten (Prozedur + Anwendbarkeitsbedingung, keine Traces).
- **Update:** bewährte Skills verbessern — **kurz halten**. Wächst ein Skill, wird gestrafft
  statt erweitert. Gilt auch für diesen Text hier (R6: der Loop reift sich selbst).
- **Delete:** Skills, die mehr schaden als nutzen (falsch, veraltet, missbraucht, nie genutzt
  trotz Chance) — Löschung braucht dieselbe Evidence-Pflicht wie eine Änderung.
- **Read-Nutzung:** Agenten reporten nach skillRead (Footer), ob ein Skill hilfreich war —
  dieses Feedback ist dein Evolve-Input (aus den Replies der Sklaven sammeln).
- **Umsetzung:** Delegiere Create/Update/Delete an Da Mek (er hat beide Write-Familien und liest
  via skillRead). Scaffold bleibt künftigen Global-Skills (Config-Dir) vorbehalten.
- **Ledger:** je Outcome ein Eintrag in `.agents/skills/wiki/skill-impact.md` (accepted/rejected
  + Evidence, analog skill-evolution Gate-Phase).
- **Kein Outcome (no change)** ist ein legitimes Ergebnis — erzwingt keine Änderung.

## Review-Route (Da Dok)

- Nach Da Meks Fertigmeldung: `reviewPlanAgent` an Da Dok (3-Seiten-Prüfung, Verdict).
- **Da Dok meldet im Review den Skill-Evolution-Outcome + Evidence** (Gate-Phase) — nimm seinen
  Befund als Evolve-Input in den Loop auf.
- Kompakt bei `compactReview`, Team-Order im Header: Da Boss · Da Thinka · Da Mek · Da Dok.

## Iteration-End-Summary (PO an User)

Ende jedes Zyklus: Management-Summary für den User. Enthält bei Skill-Änderungen einen kurzen
Abschnitt **„Skill-Evolution"** — je Zeile: Create/Update/Delete + Skill-Name + Begründung in
einer Zeile. Ohne Änderungen entfällt der Abschnitt.

## Memory vs. Skill (Retro-Relation)

- Cross-projekt-dauerhafte Verhaltensregeln → memory*-Tools (RAM, immer injiziert).
- Projekt-lokale, wiederverwendbare Prozeduren → Skill in `.agents/skills`.
- Keine Doppelhaltung desselben Inhalts — beim Retro bewusst sortieren.
