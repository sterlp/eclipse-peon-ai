---
idPrefix: SD
---

# File-Update-Diff — Diff-Anzeige bei Datei-Änderungen

**Technische Heimat:** core `org.sterl.llmpeon.shared.SimpleDiff` (Diff-Konstruktion + Caps),
core `org.sterl.llmpeon.tool.tools.DiskFileWriteTool` (Emission `AiMonitor.onFileUpdate`),
plugin `AIChatView.onFileUpdate` (Rendering, unverändert — R14 in
[streaming-display.md](../../org.sterl.llmpeon/docs/streaming-display.md)).

**Status:** ✅ done (2026-11-05, Paul-Freigabe des C-4-Proposals aus
`aifactory/docs/open-points.md`, 2026-11-03)

**Ziel:** Jede Datei-Änderung eines Agents erscheint als **echtes Diff** im Chat — auch das
Anlegen einer neuen Datei — und oberhalb der Grenzen gibt der Diff nicht mehr komplett auf,
sondern wird partziell und ehrlich gekappt ausgegeben.

## Business Rules

### R-SD-1 — Anlegen feuert onFileUpdate („Created" = voller `+`-Diff) ✅

`DiskFileWriteTool.diskWriteFile` feuert `AiMonitor.onFileUpdate` **auch beim Anlegen** einer
Datei (`oldContent = ""`) — die neue Datei erscheint als voller `+`-Diff im Chat, statt nur als
„Created file: …"-Zeile. Heute feuert das Tool nur `if (existed)`
(`DiskFileWriteTool.java:73-81`). Ein Löschen/Umbenennen/Kopieren/Shell bleibt Werkzeug-Zeile
(kein Inhalt, kein Diff). Die `eclipse*`-Familie folgt demselben Vertrag (Parität,
„one behaviour, one implementation") — ein `eclipseWriteFile`-Anlegen feuert genauso.

**Begründung:** Paul sieht bei neu angelegten Dateien heute nur die Werkzeug-Zeile und muss die
Datei manuell öffnen, um zu prüfen, was der Agent da hingelegt hat; der Diff ist genau die
Review-Fläche des Chat.

### R-SD-2 — Partzieller Diff statt Skip: ein Cap auf die Changed-Region ✅

`SimpleDiff` gibt oberhalb der Grenzen den Diff **nicht mehr komplett auf** (heute nur Summary,
`SimpleDiff.java:67-80`). Statt der zwei Guards (`MAX_LCS_CELLS`-Matrix-Limit +
`MAX_DIFF_LINES`-Changed-Region-Limit) gilt **ein Cap**: umfasst die Changed-Region (nach
Prefix/Suffix-Trim) mehr als **500 Zeilen**, diffed der Guard nur die **ersten 500 Zeilen** der
Changed-Region und hängt einen Ende-Marker mit Grund an
(`… diff truncated — showing 500 of N changed lines`). Bei ≤ 500 Zeilen ändert sich nichts.

**Begründung:** Pauls Idee aus dem C-4-Review — „lieber ein partzieller Diff als gar keiner".
Die Vereinfachung ist der eigentliche Punkt: 500 × 500 Zellen ≈ 1 MB LCS-Matrix macht das
separate Matrix-Limit überflüssig; es entfällt mit dem Doppel-Guard (Clean Break, keine
Migration). Der Cap mildert zugleich das Review-Risiko „mehrere MB Diff an alle Browser"
(S1) und begrenzt den Output deterministisch.

### R-SD-3 — Binärer Inhalt bekommt kein Diff ✅

Enthält alter **oder** neuer Inhalt NUL-Bytes (`\0`, Sniff über die ersten 8 KB), erhält der
Update eine Summary-Zeile statt eines Diffs — UTF-8-Müll (z. B. `webGet`-Binärdateien durch
`onFileUpdate`) wird nie als Diff gerendert.

## BDD

#### UC-SD-1 — createdFileShowsAsPlusDiff ✅
- GIVEN `diskWriteFile` auf einen nicht existierenden Pfad WHEN das Tool anlegt THEN
  `onFileUpdate` feuert mit `oldContent = ""` und dem neuen Inhalt; die Chat-Anzeige erhält ein
  `+`-Diff (nicht nur die „Created"-Zeile).
- GIVEN `eclipseWriteFile` auf einen nicht existierenden Workspace-Pfad WHEN angelegt THEN
  dasselbe Verhalten (Parität).

#### UC-SD-2 — oversizedChangedRegionIsPartiallyDiffed ✅
- GIVEN eine Änderung mit > 500 Zeilen Changed-Region WHEN der Diff gebaut wird THEN der Output
  enthält einen partziellen Unified-Diff der ersten 500 Zeilen **plus** Ende-Marker mit der
  abgeschnittenen Restmenge — keine reine Skip-Summary.
- GIVEN eine Änderung mit ≤ 500 Zeilen Changed-Region WHEN der Diff gebaut wird THEN der Diff ist
  vollständig, ohne Marker (Bestandsverhalten unverändert).

#### UC-SD-3 — binaryContentGetsNoDiff ✅
- GIVEN alter oder neuer Inhalt enthält NUL-Bytes WHEN `onFileUpdate` verarbeitet wird THEN keine
  Diff-Ausgabe, sondern eine Summary-Zeile mit Hinweis „binary content".

## Offene Punkte

- ❓ Ausbau der `<input>`-Länge: bleibt der 500-Zeilen-Cap konfigurierbar? Nein — Konstante, keine
  Konfigurationsfläche (Muster der Bounded Caches, Paul).
