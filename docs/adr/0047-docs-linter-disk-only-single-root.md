# ADR-0047 — Docs-Linter liest nur das Dateisystem, mit einem `root`-Parameter

**Status:** Accepted (2026-09-15)
**Bezug:** [docs-linter.md](../docs-linter.md) R-DL-1, R-DL-1b

## Context

Der [Docs-Linter](../docs-linter.md) walkt zwei Dateibäume (Docs + Testquellen) rekursiv. Für jede
andere lesende Funktion gilt in diesem Repo die Regel *„One behaviour, one implementation"*: es gibt
ein `eclipse*`- und ein `disk*`-Gegenstück, die sich nie unterscheiden dürfen
([AGENTS.md](../../AGENTS.md), [eclipse-read-tools.md](../eclipse-read-tools.md)).

Die naheliegende Lesart wäre also ein Tool-Paar. Die Analyse des IST (Da Mek, 2026-09-15) ergab:
es existiert **kein** gemeinsames Lese-Interface. `FileLines`, `SearchQuery`, `TextFileTypes`,
`LogExcerpt` sind reine Textprozessoren auf `String`; gewalkt wird doppelt — Eclipse via
`IResourceVisitor` (`EclipseWorkspaceReadFileTool.java:110`), Disk via `Files.walk`
(`DiskFileReadTool.java:70`). Ein Tool-Paar hätte also entweder eine dritte Walk-Implementierung
bedeutet oder ein vorgezogenes Refactoring an stark genutzten Read-Tools.

Zwischenstand der Diskussion war deshalb ein neues `FileTreeReader`-Interface in core plus zwei
Implementierungen — verworfen zugunsten des Folgenden.

## Decision

Der Linter greift **ausschließlich über das Dateisystem** zu und nimmt einen `root`-Parameter,
dessen Default der **Disk-Pfad des gewählten Projekts** ist. `docRoots`/`testRoots` sind relativ
dazu.

Begründung: Über den Disk-Pfad des gewählten Projekts bezeichnen workspace-relativ und disk-relativ
**dasselbe Verzeichnis**. Für den Agenten gibt es keinen Unterschied — er kennt den Projekt-Root
ohnehin aus der Projektliste und arbeitet relativ. Die `eclipse*`/`disk*`-Symmetrie schützt vor
*zwei divergierenden Verhalten*; hier gibt es nur **ein** Verhalten und zwei Wege zum selben Byte.
Die Regel greift also nicht, und ein Gegenstück wäre Zeremonie.

Daraus folgt:

- **Kein** `FileTreeReader`/`VirtualFile`-Interface in core.
- **Keine** Änderung an `EclipseWorkspaceReadFileTool` / `DiskFileReadTool` — kein Refactoring als
  Vorbedingung eines neuen Features.
- Die gesamte Linter-Logik lebt in `llmpeon-core` und ist mit plain JUnit ohne OSGi testbar.
- `root` ist ein **Parameter mit Default**, nicht verdrahtet: Fremd-Repos
  übergeben ihren eigenen Pfad.

## Consequences

**Gut:** Ein Inkrement statt zwei. Kein Eingriff in funktionierenden Code, damit keine
Regressionsfläche. Schnelle Core-Tests. Headless-tauglich ohne Eclipse.

**Preis:** Der Linter sieht **ungespeicherte Editor-Buffer nicht** — eine Datei mit ungespeicherten
Änderungen wird im alten Stand gelesen. Bei einem Werkzeug, dessen Zweck Vertrauen ist, ist eine
stille Falschaussage der teuerste Fehlerzustand. Gegenmittel ist **Offenlegung, nicht Umbau**:
R-DL-1b schreibt einen entsprechenden Hinweis in den Report-Kopf, analog zu `showing N of M`.

**Offen gelassen:** Sollte sich später zeigen, dass der Editor-Stand wirklich gebraucht wird (z.B.
Jon lintet, während er schreibt), ist der Nachzug ein `eclipse*`-Gegenstück auf derselben
Core-Logik — dann aber mit belegtem Bedarf statt auf Verdacht.
