# Terminal-Session-Tool — persistente & Hintergrund-Shells

> **Status:** ⏳ geparkt (2026-09-19, Paul: nicht verworfen) — Voraussetzung ist Async-Tool-Infrastruktur
> ([async-agent-tools-proposal.md](async-agent-tools-proposal.md), Option C); Revisit, wenn Kosten/Nutzen passt.
> Ursprungszuordnung (temporär): [tool-evolution.md](tool-evolution.md) — CR-5.

## Ziel

`shellRunCommand` ist One-shot mit 60s-Timeout — Läufe, die länger brauchen oder **Zustand tragen**
(env, cwd, Maven-Schritt-Sequenzen, REPLs), sind damit unmöglich. Vorschlag: persistente Session, optional
Hintergrund-Ausführung mit nachlesbarem Output.

## Vorgeschlagenes Verhalten (Entwurf)

- **R-TS-1 🚧** Persistente **Foreground**-Session: env + cwd bleiben zwischen Aufrufen erhalten
  (Pro-Chat/Agent-Session), erste Stufe statt 60s-One-shot.
- **R-TS-2 🚧** Optional **Background**: Start liefert eine Output-ID; ein zweites Tool liest den Output
  per ID nach (für Long-Running statt Blocken).
- **R-TS-3 🚧** Output-Truncation **disclosed** im Return („showing N of M lines") — unsere Disziplin,
  analog ShellTool/Grep.
- **R-TS-4 🚧** Bestätigung über [tool-confirmation.md](tool-confirmation.md) (Kategorie `terminal`).
- **R-TS-5 🚧** Session-Lifecycle: Session endet mit dem Agent-Chat; keine verwaisten Prozesse (Kill beim
  Chat-Ende/Stop).

## BDD (Entwurf, hart erst bei ❌)

- GIVEN Session mit `cd x && export Y` WHEN nächster Befehl `pwd && echo $Y` THEN cwd/env erhalten.
- GIVEN Background-Start WHEN Output per ID nachgelesen THEN disclosed Truncation, unknown ID → Fehler.
- GIVEN Chat endet mit laufender Session WHEN Cleanup THEN Prozess beendet, keine Orphans.

## Offen (PO-Run)

- Foreground zuerst (Lean) oder Foreground+Background in einem Zug?
- Verhältnis zu `shellRunCommand` (bleibt für One-shot) — zwei Tools oder ein Tool mit Modus?
- Aufwand: **M** (FG) / **M+** (mit Background).
