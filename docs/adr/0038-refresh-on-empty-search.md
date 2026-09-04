# ADR-0038 — Workspace-Refresh nur bei leerem Suchergebnis

**Status:** Accepted (2026-09-03)

## Context

Der Eclipse-Workspace ist ein Cache über dem Dateisystem. Wer außerhalb von Eclipse schreibt
(Shell, anderer Editor, ein `disk*`-Tool), erzeugt Dateien, die der Workspace erst nach einem
`refreshLocal` sieht. Für ein Such-Tool heißt das: ein **Falsch-Negativ** — die Datei
existiert, das Tool meldet „nicht gefunden". Das ist der teuerste Tool-Fehler, weil der Agent
daraufhin falsche Schlüsse zieht (legt Dateien doppelt an, hält Code für tot).

Der naheliegende Fix — vor jeder Suche refreshen — kostet bei großen Workspaces (hier:
`langchain4j`, `opencode`, `copilot-for-eclipse` parallel offen) spürbar Zeit, und zwar bei
**jeder** Suche für einen Fall, der selten eintritt.

## Decision

`eclipseSearchFiles` refresht **nur dann**, wenn die Suche leer ausging — und dann genau
einmal, gefolgt von einem zweiten Suchdurchlauf. Bei Treffern wird nicht refresht.

**Nachtrag (2026-09-03, nach Review 2b-2):** Der Refresh trifft **nur das gewählte Projekt**,
wenn kein expliziter Scope angegeben ist — nicht alle offenen Projekte. Mit explizitem Scope
genau diesen; ohne gewähltes Projekt gar nicht. Die ursprüngliche Entscheidung hat den Refresh
zeitlich begrenzt (nur im Leerfall), aber räumlich offen gelassen; das führte zu
`DEPTH_INFINITE` über fremde Großprojekte. Gilt für `eclipseSearchFiles` **und**
`eclipseGrepFiles`. → [eclipse-read-tools.md](../eclipse-read-tools.md) R7d

## Consequences

- Der häufige Pfad (Treffer) bleibt so schnell wie heute.
- Der teure Pfad (leer) zahlt einmalig für die Gewissheit, dass „nicht gefunden" auch
  wirklich „existiert nicht" heißt — passend zur Ehrlichkeitsregel der Story.
- Worst Case ist eine wirklich nicht existierende Datei: dort kostet jede Suche zusätzlich
  einen Refresh. Akzeptiert, weil ein Agent auf ein leeres Ergebnis ohnehin mit weiteren
  Tool-Calls reagiert — die teurer sind als der Refresh.
- Der Refresh ist ein Seiteneffekt in einem lesenden Tool. Bewusst in Kauf genommen: er
  synchronisiert nur den Workspace mit der Disk, er ändert keine Inhalte.
- Regeln + BDD: [eclipse-read-tools.md](../eclipse-read-tools.md) R4b (Suche), R7b/R7c (Grep),
  R7d (Refresh-Scope).
