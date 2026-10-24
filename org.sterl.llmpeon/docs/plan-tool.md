---
idPrefix: PT
---

# PlanTool — Plan-Dateien im Eclipse-Workspace

> **Status:** ❌ specified (2026-10-24, Bug-Triage T-8)   **Code:** `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/PlanTool.java`

## Ziel

`PlanTool` besitzt die Plan-Dateien des Workspace (`peon-plan/overview.md`): Lesen, Schreiben,
Aktualisieren und Archivieren (`planImplemented` → `peon-plan/overview-done-<stamp>.md`). Es arbeitet
mit Eclipse-`IResource`-APIs, damit Workspace-History und Refresh-Benachrichtigungen greifen —
Plan-Dateien können aber auch über die `disk*`-Tools geschrieben werden, die am Workspace-Baum
vorbei auf Disk schreiben.

## Regeln

### R-PT-1 — Archivierung toleriert disk-geschriebene Pläne ❌

`planImplemented` aktualisiert den Workspace-Baum (`refreshLocal` auf der Plan-Datei bzw. ihrem
Ordner), BEVOR es `move` aufruft. Ein Plan, der zuletzt per Disk-Tool geschrieben wurde (stale
Workspace-Tree), wird zuverlässig archiviert statt mit `ResourceException` zu scheitern. Ein
fehlgeschlagener Archiv-Versuch wirft nie still — der Fehler wird mit Pfad benannt.

#### UC-PT-1 — planImplementedArchivesDiskWrittenPlan ❌
- GIVEN `peon-plan/overview.md` wurde per Disk-Tool geschrieben (Workspace-Tree kennt den Inhalt
  nicht) WHEN `planImplemented()` THEN die Datei wird unter `overview-done-<stamp>.md` archiviert
  (Move erfolgreich, keine `ResourceException`).
- GIVEN der Move schlägt dennoch fehl WHEN `planImplemented()` THEN ein Fehler mit Pfad und Ursache
  wird geworfen (kein stiller Abbruch).

## Offene Punkte

- ⏳ Ob auch `planRead`/`hasPlan` bei disk-geschriebenen Plänen stale sind (False-Negative-Klasse:
  „No plan exists" trotz Datei) — nicht beobachtet, erst bei Befund prüfen.
