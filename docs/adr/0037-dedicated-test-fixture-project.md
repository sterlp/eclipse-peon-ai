# ADR-0037 — Dediziertes Test-Fixture-Projekt + unattended PDE-Launch

**Status:** Accepted (2026-09-03)

## Context

`AbstractIntegrationTest` importiert bisher `new File("./")` — also das laufende Testbundle
`org.sterl.llmpeon.test` — als Workspace-Projekt (`AbstractIntegrationTest.java:63`, TODO im
Code: „create minimal test project"). Folgen:

- Der Import kollidiert mit dem Maven/Tycho-Workspace; 8 Integrationstests fielen mit
  „ungültige Projektbeschreibung" aus (`AbstractIntegrationTest.java:93`).
- Der Pfad hängt vom Arbeitsverzeichnis des Prozesses ab (IDE vs. Maven vs. CI).
- Die Tests greifen auf echten Produktcode zu — Treffer in `bin/`/Binaries verfälschen
  Navigations- und Grep-Assertions.

Zusätzlich blockiert der PDE-Launch unattended Läufe: die programmatisch gebaute Launch-Config
(`EclipseRunTestTool.java:132-136`) setzt weder den **Runtime-Workspace-Ort** noch
`IPDELauncherConstants.ASKCLEAR`. Eclipse braucht für den Testlauf zwingend einen Workspace;
ohne gesetzten Ort + gesetzte Clear-Policy fragt es („Clear workspace data before launching?")
und wartet auf einen Menschen.

Referenz der Schlüssel:
[`IPDELauncherConstants`](https://raw.githubusercontent.com/eclipse-pde/eclipse.pde/master/ui/org.eclipse.pde.launching/src/org/eclipse/pde/launching/IPDELauncherConstants.java)
— `LOCATION` (`"location"`), `DOCLEAR` (`"clearws"`), `ASKCLEAR` (`"askclear"`).

## Decision

1. **Dediziertes Fixture:** `test_project` neben dem Repo-Checkout
   (`<repo-parent>/test_project`) mit `.project` (Name `test_project`, JDT-Builder +
   `org.eclipse.jdt.core.javanature`), `.classpath` und `src/` wird importiert — nicht mehr
   das Testbundle selbst.
2. **Pfad-Auflösung robust statt relativ:** System-Property `peon.test.project` gewinnt;
   sonst wird vom Bundle-Location aus aufgelöst. Fehlt das Fixture, wird der Test mit einer
   klaren Meldung **failed**, nicht mehr still per `assumeTrue(false)` übersprungen — ein
   fehlendes Fixture ist ein Setup-Bug, kein „nicht anwendbar".
3. **Unattended + stabiler Runtime-Workspace:** die Launch-Config setzt alle drei Schlüssel
   explizit — `LOCATION` = **fester** Pfad (`<workspace>/.metadata/peon-test-ws` bzw. via
   `peon.test.ws` überschreibbar), `ASKCLEAR = false` (nie fragen),
   `DOCLEAR = false` (Workspace **bleibt bestehen**).

   Bewusst **kein** Clear pro Lauf: ein frischer Workspace bedeutet jedes Mal Full-Build und
   Plugin-Registry-Aufbau — das kostet Minuten pro Zyklus. Der Workspace ist ein Cache; das
   Fixture wird ohnehin bei jedem Lauf neu importiert (`project.delete` + `create`), also ist
   der relevante Zustand pro Lauf sauber. Wer wirklich frisch starten will, löscht das
   Verzeichnis.

## Consequences

- Integrationstests laufen deterministisch und ohne Interaktion — Voraussetzung für
  Night-Cycles und CI.
- Das Fixture muss versioniert und im Build verfügbar sein; liegt es außerhalb des
  Repos, braucht CI die Property `peon.test.project`.
- Assertions gegen Fixture-Inhalte sind stabil (keine Produktcode-Drift mehr).
- Silent-Skips verschwinden: die Suite meldet ehrlich, wenn das Setup fehlt.
- Regeln + BDD: [test-setup.md](../test-setup.md).
