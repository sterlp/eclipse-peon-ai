---
idPrefix: SEL
---

# User-Context Selection

> **Status:** ✅ done (2026-09-16, Branch `bugfix/user-context-selection`: `2e51a16`/`5ceb3aa`/`c958d78`, Plugin 216/0 · Core 866/0, Review CONCERNS → abgenommen; User-Smoke steht aus) · **Architektur:** kein eigenes
> Architektur-Doc — Bestandskomponenten `UserContext` + `AIChatView`, Regeln siehe `docs/architecture.md`

## Ziel

Der User markiert Text im aktiven Editor — der markierte Inhalt geht automatisch als Kontext mit
jeder Chat-Nachricht (Snippet mit Zeilennummern + Pfad der Datei). Die Statuszeile zeigt die Selektion.

## Symptom (Paul, 2026-09-16)

Selektierter Text fehlt im gesendeten Kontext **und** in der Statuszeile (README.md, Zeilen 9-10
selektiert) — Regression gegenüber dem vorletzten Release. Diagnose (Da Mek): Verdächtiger ist
Commit `0a998ec` („fixed smaller UI issues due to updates", 13.09.). Jedes Nicht-Text-Selektions-Event
(`AIChatView.onSelection(Object)`, `AIChatView.java:255`: erst `setTextSelection(null)`, dann
`setSelectedResource(...)`) räumt die Editor-Selektion weg, bevor sie gesendet werden kann.
**Live-Beweis (2026-09-16):** Selektion in README.md kommt als Snippet an, aber mit „selected
content not in a file." — die Ressource wurde weggeräumt, der Text überlebte halb.

## Regeln

### R-SEL-1 — Text-Selektion überlebt Nicht-Text-Selektions-Events ✅

**Regel:** Ein Selektions-Event ohne Text-Selektion (null, leer, strukturierte Auswahl, Klick in
Views ohne Texteditor) darf eine bestehende Editor-Text-Selektion **nicht** löschen. Geräumt wird sie
nur durch ein **neues** Text-Selektions-Event (inkl. leerem/Caret-Event des Editors) oder eine
Ressource-Änderung auf eine **andere** Datei. Eine Text-Selektion räumt zudem die Class-File-
Assoziation (`clazz`). Die Clear-Entscheidung lebt als Choke-Point in
`UserContext` (headless testbar), nicht in `AIChatView`.

**Begründung:** Zwischen „Text markieren" und „Nachricht senden" feuern Eclipse-Events
(Outline-Klick, Part-Aktivierung, Explorer-Auswahl) — die Selektion muss diese überleben.

#### UC-SEL-1 — Selektion überlebt Nicht-Text-Events ✅

- GIVEN Text-Selektion in `A.java` WHEN Selektions-Event mit `null`/leer/strukturierter Auswahl (gleiche oder keine Ressource) THEN `UserContext` hält die Text-Selektion → `get()` enthält den Selektions-Kontext.
- GIVEN Text-Selektion in `A.java` WHEN Selektions-Event mit anderer Ressource `B.java` THEN Text-Selektion wird geräumt (Dateiwechsel = Neuanfang).

**Test:** `UserContextTest` (Event-Sequenz headless nachgestellt: `setTextSelection` → Nicht-Text-Event → `get()`).

### R-SEL-2 — Selektion ohne Ressource geht trotzdem an den Agenten ✅

**Regel:** Eine Text-Selektion ohne aufgelöste Ressource/Projekt geht trotzdem als Kontext an den
Agenten (Snippet mit Zeilennummern; offene Datei, wenn bekannt, mitschicken) — kein stiller Drop.
Heute wirft `UserContext.get()` (Early-Return, `UserContext.java:37`) diese Konstellation still weg.
Offene-Datei-Assoziation: beim Text-Selektions-Event (UI-Thread) wird die aktuell offene Datei als
`selectedResource` gesetzt (löst den Statuszeilen-Side-Effect `getSelectedFile()` zur Render-Zeit ab).

**Begründung:** Beim Senden wird diese Regel ausdrücklich nicht vorausgesetzt — der Agent soll die
Selektion trotzdem bekommen; wenn die offene Datei bekannt ist, wird sie mitgeschickt.

#### UC-SEL-2 — Selektion ohne Ressource wird gesendet ✅

- GIVEN reine Text-Selektion, `currentProject == null && selectedResource == null` WHEN `get()` THEN Kontext-Item mit Selektionstext (Zeilennummern), kein stiller Drop.

**Test:** `UserContextTest.test_rSel2_selectionWithoutResourceIsStillSent`

### R-SEL-3 — Selektions-Rendering: Snippet + Dateipfad (Paul 2026-09-16) ✅

**Regel:** Selektierter Text geht als Snippet mit Zeilennummern (`FileLines.format`); ist die
selektierte Datei bekannt (IFile), wird zusätzlich ihr **Pfad** mitgeschickt — **nicht** der gesamte
Dateiinhalt. Ziel: gleiche Semantik wie Homepage `usage/selections.md` („Code snippet with line
numbers") — der IFile-Branch in `addUserSelection` (`UserContext.java:62-70`) schickt heute den ganzen
File-Inhalt (Doc/Code-Drift). Der Homepage-Text stimmt bereits und wird **nicht** geändert.

#### UC-SEL-3 — Snippet plus Dateipfad, kein Volltext ✅ (BDD 2 manuell im User-Smoke — headless nicht testbar)

- GIVEN Selektion in bekannter IFile WHEN `get()` THEN Item = Pfad + „Selected lines X-Y" + Snippet mit Zeilennummern, **kein** gesamter Dateiinhalt.
- GIVEN Selektion ohne IFile WHEN `get()` THEN Item = Snippet mit Zeilennummern (+ offene Datei, wenn bekannt).

**Test:** `UserContextTest.test_rSel3_snippetPlusFilePath_notFullContent`

## Nebenfunde

- 🐞 **`SimpleDiff.lcsDiff` OOM (Crash 2026-09-16):** `eclipseEditFile` → `AIChatView.onFileUpdate:329` → `SimpleDiff.unifiedDiff:21` → `OutOfMemoryError: Java heap space` — **behoben:** Size-Guard in `SimpleDiff.unifiedDiff` (`MAX_LCS_CELLS = 5_000_000`), darüber summarische Meldung statt LCS (`2e51a16`). Details: `open-points.md`.
