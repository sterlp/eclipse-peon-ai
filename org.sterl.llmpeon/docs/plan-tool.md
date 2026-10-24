---
idPrefix: PT
---

# PlanTool — Plan-Dateien im Eclipse-Workspace

> **Status:** ✅ done (2026-10-24, Bug-Triage T-8/T-8b)   **Code:** `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/PlanTool.java`

## Ziel

`PlanTool` besitzt die Plan-Dateien des Workspace (`peon-plan/overview.md`): Lesen, Schreiben,
Aktualisieren und Archivieren (`planImplemented` → `peon-plan/overview-done-<stamp>.md`). Es arbeitet
mit Eclipse-`IResource`-APIs, damit Workspace-History und Refresh-Benachrichtigungen greifen —
Plan-Dateien können aber auch über die `disk*`-Tools geschrieben werden, die am Workspace-Baum
vorbei auf Disk schreiben.

## Regeln

### R-PT-1 — Archivierung toleriert disk-geschriebene Pläne ✅ done (2026-10-24, T-8/T-8b, Commits 28ae9fed/4e563e11/411bc3b6)

`planImplemented` aktualisiert den Workspace-Baum (`refreshLocal` auf dem `peon-plan`-Ordner,
DEPTH_INFINITE), BEVOR es `exists()` prüft und `move` aufruft. Damit sind beide Fallen abgedeckt:
ein dem Baum unbekannter Plan (stiller Return ohne Archiv — die teurere) und eine dem Baum
bekannte, auf Disk geänderte Plan-Datei (`ResourceException` am Move — nur dieser Fall ist
synchronlos). Ein fehlgeschlagener Archiv-Versuch wirft nie still — der Fehler wird mit Pfad und
Ursache geworfen. User-Abbruch (Stop) ist **kein Fehler**: `OperationCanceledException` läuft
ungefangen durch (Job-Framework behandelt ihn, kein Pfad-Wrap).

#### UC-PT-1 — planImplementedArchivesDiskWrittenPlan ✅
- GIVEN `peon-plan/overview.md` wurde per Disk-Tool geschrieben (Workspace-Tree kennt den Inhalt
  nicht) WHEN `planImplemented()` THEN die Datei wird unter `overview-done-<stamp>.md` archiviert
  (Move erfolgreich, keine `ResourceException`).
- GIVEN der Move schlägt dennoch fehl WHEN `planImplemented()` THEN ein Fehler mit Pfad und Ursache
  wird geworfen (kein stiller Abbruch).

### R-PT-2 — `planRead`/`hasPlan`/`planUpdate` sehen disk-geschriebene Pläne ✅ done (2026-10-24, T-8b, Commit 4e563e11)

Auch die Lese-Pfade aktualisieren den Workspace-Baum, bevor sie `exists()` prüfen oder lesen —
ein per Disk-Tool geschriebener Plan darf nie als „No plan exists yet" erscheinen (False-Negative:
das teuerste Ergebnis dieser Codebasis — der Agent handelt auf der Annahme, der Plan existiere
nicht). Der tote `plan == null`-Guard entfällt (`IProject.getFile` liefert nie null).

#### UC-PT-2 — planReadSeesDiskWrittenPlan ✅
- GIVEN `peon-plan/overview.md` existiert auf Disk, ist dem Workspace-Tree aber unbekannt WHEN
  `planRead()`/`hasPlan()` THEN der Inhalt wird gelesen bzw. `true` gemeldet — niemals „No plan
  exists yet" für eine vorhandene Datei.

## Offene Punkte

- ⏳ `AgentContextComponent.readPlan():110` prüft `plan.exists()` auf dem per `setPlan()` übergebenen
  `IFile` — der reguläre Pfad läuft über `preloadPlanIfNeeded`→`hasPlan()` (seit R-PT-2 refresht),
  ein nur disk-geschriebener Plan bliebe dort aber leer, falls `setPlan` nie gerufen wurde
  (Injection-Klasse, nicht beobachtet). Erst bei Befund als R-PT-3 prüfen.
