---
idPrefix: PT
---

# PlanTool — Plan-Dateien im Eclipse-Workspace

> **Status:** ✅ done (2026-10-24, Bug-Triage T-8/T-8b; R-PT-2a/3/4 2026-10-24 aus externem Review)   **Code:** `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/PlanTool.java`

## Ziel

`PlanTool` besitzt die Plan-Dateien des Workspace (`peon-plan/overview.md`): Lesen, Schreiben,
Aktualisieren und Archivieren (`planImplemented` → `peon-plan/overview-done-<stamp>.md`). Es arbeitet
mit Eclipse-`IResource`-APIs, damit Workspace-History und Refresh-Benachrichtigungen greifen —
Plan-Dateien können aber auch über die `disk*`-Tools geschrieben werden, die am Workspace-Baum
vorbei auf Disk schreiben.

## Regeln

### R-PT-1 — Archivierung toleriert disk-geschriebene Pläne ✅ done (2026-10-24, T-8/T-8b)

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

### R-PT-2 — `planRead`/`hasPlan`/`planUpdate` sehen disk-geschriebene Pläne ✅ done (2026-10-24, T-8b)

Auch die Lese-Pfade aktualisieren den Workspace-Baum, bevor sie `exists()` prüfen oder lesen —
ein per Disk-Tool geschriebener Plan darf nie als „No plan exists yet" erscheinen (False-Negative:
das teuerste Ergebnis dieser Codebasis — der Agent handelt auf der Annahme, der Plan existiere
nicht). Der tote `plan == null`-Guard entfällt (`IProject.getFile` liefert nie null).

#### UC-PT-2 — planReadSeesDiskWrittenPlan ✅
- GIVEN `peon-plan/overview.md` existiert auf Disk, ist dem Workspace-Tree aber unbekannt WHEN
  `planRead()`/`hasPlan()` THEN der Inhalt wird gelesen bzw. `true` gemeldet — niemals „No plan
  exists yet" für eine vorhandene Datei.
- GIVEN `planUpdate` schreibt WHEN danach THEN der Workspace-Tree ist synchron ohne zusätzlichen
  Refresh — `IoUtils.writeFile` ist eine Workspace-Operation (`IFile.setContents`/`create`,
  Resource-Events feuern), kein Stale-State bleibt zurück (Klarstellung 2026-10-24, externes
  Review Punkt 3 — verifiziert, kein Fix nötig).

**Nachschärfung ✅ done (2026-10-24, externes Review, Punkt 1):** die Query-Pfade
(`planRead`/`hasPlan`) refreshen **nur die Plan-Datei mit DEPTH_ZERO** — nicht den `peon-plan`-Ordner
rekursiv (das bleibt `planImplemented`, R-PT-1, vorbehalten: seltene Operation, die den ganzen
Ordner braucht). Ein Refresh-Fehler ist **kein Turn-Fehler**: die Query-Pfade antworten ehrlich
(`hasPlan` → `false` mit `onProblem`, `planRead` → „No plan exists"-Antwort mit `onProblem` mit Pfad
+ Ursache); hart werfen bleibt `planImplemented` (R-PT-1). IST-Befund: `hasPlan()` läuft nur beim
Agent-Wechsel (`preloadPlanIfNeeded`) und als LLM-Tool — kein Turn-Pfad; trotzdem wirft der
Query-Pfad heute den Refresh-Fehler hart und bricht `onAgentChange` nach vollzogenem
Agent-Wechsel (halb aktivierter Zustand).

#### UC-PT-2a — queryPathsFailHonestOnRefreshError ✅
- GIVEN `refreshLocal` wirft `CoreException` WHEN `hasPlan()` THEN `false` + `onProblem` mit Pfad +
  Ursache — kein Throw, kein abgebrochener Agent-Wechsel. (`planRead` analog mit ehrlicher
  „No plan exists"-Antwort + `onProblem`.)

### R-PT-3 — Per-Turn-Plan-Sicht nutzt denselben refreshten Stand ✅ done (2026-10-24, externes Review, Punkt 2)

Der reguläre Turn-Pfad hängt den Plan über `AgentContextComponent.appendPlanReference` an — der
`exists()`-Check dort prüft ohne Refresh, ein per Disk-Tool geschriebener Plan erscheint also
jeden Turn nicht als „Plan reference"-ContextItem (dieselbe False-Negative-Klasse wie R-PT-2, nur
im per-Turn-Pfad). **SOLL:** der per-Turn-Pfad nutzt denselben refreshten/exakten Stand wie die
Query-Pfade (R-PT-2/Nachschärfung) — ein vorhandener Plan ist als Plan-Reference sichtbar,
unabhängig davon, ob er per Workspace- oder Disk-Tool geschrieben wurde. `AgentContextComponent.
readPlan` bleibt ohne eigenen Refresh, weil ohne `setPlan` gar nicht gelesen wird (Call-Site
verifiziert 2026-10-24).

#### UC-PT-3 — diskWrittenPlanVisibleInTurnContext ✅
- GIVEN `peon-plan/overview.md` existiert nur auf Disk WHEN ein Turn startet THEN die
  Plan-Reference erscheint als ContextItem (kein stilles Fehlen).

### R-PT-4 — Archivierung gibt die Plan-Referenz frei ✅ done (2026-10-24, externes Review, Punkt 2 — IST-Befund verschärft)

`planImplemented` verschiebt die Plan-Datei, **nichts** gibt die in-memory Plan-Referenz frei:
der nächste `onHandoff` sieht `plan != null`, `exists() == false` → `readPlan()` liefert `""`
→ das Handoff-Konstrukt behandelt das als „Plan vorhanden", **löscht die Dev-Memory** und meldet
Erfolg — ohne Plan-Inhalt (False-Positive, verifiziert 2026-10-24). **SOLL:** nach gelungener
Archivierung wird die Plan-Referenz freigegeben (dieselbe Stelle/Prinzip wie `clearPlan`); ein
Handoff auf eine nicht mehr existierende Plan-Datei läuft wie „kein Plan" — nie clear + Erfolg
mit leerem Plan.

#### UC-PT-4 — archivedPlanReleasesReferenceNoGhostHandoff ✅
- GIVEN ein Plan wurde per `planImplemented` archiviert WHEN der nächste Handoff läuft THEN die
  Dev-Memory wird NICHT gelöscht und der Handoff meldet keinen Erfolg auf Grundlage eines
  leeren Plans.

**Härtung (Jon 2026-10-24, gleiche Klasse):** der Fall „Plan-Datei ohne `planImplemented`
gelöscht" ist derselbe Ghost (`planRef != null`, `readPlan()` = `""` → clear + Erfolg). `onHandoff`
behandelt deshalb **leeren Plan-Text wie „kein Plan"** — nie clear + Erfolg mit leerem Plan.
- GIVEN Plan-Referenz lebt, die Datei wurde gelöscht WHEN `onHandoff` THEN die Dev-Memory bleibt,
  der Handoff meldet keinen Erfolg (kein Ghost-Handoff).
- GIVEN leere `overview.md` WHEN `onHandoff` THEN wie „kein Plan" — der Guard prüft den **rohen
  Dateiinhalt**, nicht die `readPlan()`-Ausgabe (die trägt je Header `Plan: <pfad>` und wäre damit
  nie blank — Da-Dok F2, 2026-10-24). Das Rendering bleibt auf `readPlan()`, nur die
  Handoff-**Entscheidung** liest den Rohtext.
- Der Chat-Fallback-Zweig (leere letzte AiMessage) wird vom „leer = kein Plan"-Guard **ebenfalls**
  erfasst — einheitliche Semantik (Da-Dok F3, 2026-10-24).

## Offene Punkte

- ⏳ ~~`AgentContextComponent.readPlan`-Stale-Risiko~~ — im externen Review (2026-10-24) verifiziert:
  ohne `setPlan` wird gar nicht gelesen; das echte Risiko ist der Folgeschaden im Handoff
  (→ jetzt **R-PT-4**) und der unbeleafte per-Turn-`exists()`-Check (→ jetzt **R-PT-3**).
