# Change-Review — geprüfte Agent-Änderungen (Keep/Undo/Diff)

> **Status:** 🚧 in design (Entwurf aus dem Tool-Evolutions-Run 2026-09-19, wartet auf PO-Freigabe).
> Ursprungszuordnung (temporär): [tool-evolution.md](tool-evolution.md) — CR-2 (UI-Teil) + CR-7.

## Ziel

Agent-Dateiänderungen (alle Write/Edit-Tools) werden nicht mehr sofort endgültig geschrieben, sondern als
**prüfbare Änderung** bereitgestellt: der User sieht jede Änderung als Diff und kann sie annehmen (Keep),
verwerfen (Undo) oder ansehen — auch mehrere Runden hintereinander. Der Agent darf dadurch „mutig" editieren,
ohne dass der User unkontrolliert Risiko trägt.

## Vorgeschlagenes Verhalten (Entwurf)

- **R-CR7-1 🚧** Original-Inhalt einer Datei wird **einmalig** gecacht (bei der ersten Agent-Änderung der
  Session), nicht je Änderung — Undo stellt damit auch nach mehreren Edit-Runden den Ausgangszustand her.
- **R-CR7-2 🚧** Der User sieht eine gebündelte Änderungsübersicht (pro geänderter Datei: **Keep / Undo /
  View-Diff**). Keep macht die Änderung final; Undo stellt den gecachten Originalzustand wieder her.
- **R-CR7-3 🚧** View-Diff öffnet einen **Compare-Editor** (eclipse.compare) mit editierbarer
  „vorgeschlagene Änderung"-Seite — der User kann vor dem Keep manuell nachbessern.
- **R-CR7-4 🚧** Alle Write/Edit-Tools (Disk + Eclipse-Familie) laufen durch denselben Review-Pfad —
  kein Tool umgeht die Übersicht.
- **R-CR7-5 🚧** WriteValidator-/Edit-Guard-Logik bleibt unverändert vorgeschaltet; Change-Review ist die
  User-Ebene darüber, kein Ersatz für die Tool-Guards.

## BDD (Entwurf, hart erst bei ❌)

- GIVEN Agent editiert Datei A zweimal WHEN User wählt Undo THEN Datei A = Originalzustand (beide Runden weg).
- GIVEN Agent ändert A und B WHEN User wählt Keep nur für A THEN B bleibt in der Übersicht revertierbar.
- GIVEN User öffnet View-Diff und editiert die Vorschauseite WHEN er Keep wählt THEN der nachgebesserte Inhalt wird final.

## Offen (PO-Run)

- Keep pro Datei oder pro Turn? Verhalten im autonomen Modus (kein User da)?
- Interaktion mit `eclipseUpdateOpenFile` (Editor geöffnet, ungesicherte User-Edits)?
- Aufwand: **L** (Write-Pfad + UI + Multi-Round-State).
