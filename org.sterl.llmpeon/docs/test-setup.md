# Test-Setup — OSGi-Integrationstests

**Status: ✅ done (2026-09-03, inc-1…inc-9 + Review-Nacharbeit L1–L6).**
Fixture `/llmpeon-parent/test_project` versioniert, `AbstractIntegrationTest` importiert es
statt sich selbst, `PeonTestFixture` löst den Pfad auf, fehlendes Fixture failt,
`PdeTestLaunchConfig` macht den PDE-Launch unattended (beide Config-Pfade).
Plugin-Suite 126 → **141 Tests, 0 Fehler**; Core 519/519. Nicht committet (User committet).

**Ziel:** Die Plugin-Suite läuft **unattended** und **deterministisch** — kein Dialog, kein
Skip, kein Zufallsergebnis je nach Arbeitsverzeichnis. Voraussetzung für Night-Cycles und CI.

Mechanik + Begründung: [ADR-0037](../../docs/adr/0037-dedicated-test-fixture-project.md).

## R1 ✅ done — Dediziertes Fixture-Projekt statt Selbst-Import

**IST:** `AbstractIntegrationTest` importiert `new File("./")`, also das Testbundle selbst
(`AbstractIntegrationTest.java:63`). Das kollidiert mit dem Tycho-Workspace (8 rote Tests,
Stand 2026-09-03) und macht Assertions von Produktcode abhängig.

**SOLL:** Importiert wird `test_project` — ein minimales Java-Projekt mit `.project`
(Name `test_project`, JDT-Nature), `.classpath` und `src/`.

**WEIL:** Ein Fixture ist stabil, klein und lügt nicht: Treffer-Zahlen in Grep-/Navigations-
Tests ändern sich nicht, wenn wir Produktcode anfassen.

- GIVEN vorhandenes `test_project`-Fixture, WHEN die Suite startet, THEN ist genau dieses
  Projekt im Workspace offen und `project.getName()` ist `test_project`
  → `AbstractIntegrationTestSetupTest.importsFixtureProject`
- GIVEN ein Test schreibt/löscht Dateien, WHEN er läuft, THEN passiert das ausschließlich im
  Fixture, nie im Produktprojekt → `AbstractIntegrationTestSetupTest.writesOnlyIntoFixture`

## R2 ✅ done — Pfad-Auflösung: Property vor Bundle-Location, nie relativ

Umgesetzt in `PeonTestFixture` (Property `peon.test.project` → Bundle-Location aufwärts bis
`<dir>/test_project/.project`). Tests headless-fähig in `PeonTestFixtureResolveTest`
(bewusst **ohne** `AbstractIntegrationTest`-Ableitung, sonst würden sie am Workspace-Assume
still übersprungen — Review-Befund L6).

**SOLL:** Reihenfolge (1) System-Property `peon.test.project`, (2) Auflösung über die
Bundle-Location des Testbundles (`<repo-parent>/test_project`). Kein `./`.

- GIVEN gesetzte Property `peon.test.project`, WHEN die Suite startet, THEN wird dieser Pfad
  benutzt → `AbstractIntegrationTestSetupTest.propertyWinsOverDefault`
- GIVEN keine Property, WHEN die Suite startet, THEN wird der Pfad aus der Bundle-Location
  abgeleitet und ist unabhängig vom Arbeitsverzeichnis
  → `AbstractIntegrationTestSetupTest.resolvesFromBundleLocation`

## R3 ✅ done — Fehlendes Fixture ist ein Fehler, kein Skip

**IST:** `assumeTrue(false, …)` → die Suite meldet „grün", obwohl 15 Tests nie liefen.

**SOLL:** Fehlt `test_project` oder seine `.project`, **failt** das Setup mit einer Meldung,
die den erwarteten Pfad und die Property nennt.

**WEIL:** Ein still übersprungener Integrationstest ist gefährlicher als ein roter — er
verbirgt genau die Regressionen, für die er existiert.

- GIVEN fehlendes Fixture, WHEN die Suite startet, THEN failt sie mit
  `test fixture not found: <pfad> (set -Dpeon.test.project=…)`
  → `AbstractIntegrationTestSetupTest.missingFixtureFails`
- GIVEN kein Workbench-Display (headless), THEN darf weiterhin **nur dafür** geskippt werden
  → bestehender Assume-Pfad bleibt

## R4 ✅ done — Fester Runtime-Workspace, keine Dialoge im Test-Launch

Umgesetzt in `PdeTestLaunchConfig` (String-Konstanten statt `Require-Bundle:
org.eclipse.pde.launching`), angewandt in **beiden** Pfaden von `EclipseRunTestTool` — auch
auf die **wiederverwendete** Launch-Config, die sonst nie die neuen Attribute bekäme.

**IST:** Die Launch-Config (`EclipseRunTestTool.java:132-136`) setzt weder den
Workspace-Ort noch die Clear-Policy → Eclipse fragt „Clear workspace data before launching?"
und blockiert.

**SOLL:** Alle drei PDE-Schlüssel explizit setzen
([`IPDELauncherConstants`](https://raw.githubusercontent.com/eclipse-pde/eclipse.pde/master/ui/org.eclipse.pde.launching/src/org/eclipse/pde/launching/IPDELauncherConstants.java)):

| Key | Wert | Warum |
|---|---|---|
| `LOCATION` (`location`) | fester Pfad, Default `<workspace>/.metadata/peon-test-ws`, überschreibbar via System-Property `peon.test.ws` | Eclipse **braucht** einen Workspace; wechselt er, wird jeder Lauf zum Kaltstart |
| `ASKCLEAR` (`askclear`) | `false` | kein Dialog, kein Warten auf einen Menschen |
| `DOCLEAR` (`clearws`) | `false` | Workspace bleibt bestehen → schneller Lauf; das Fixture wird pro Lauf ohnehin neu importiert |

**WEIL:** Der Dialog ist nur das Symptom — die Ursache ist ein ungesetzter Workspace-Ort.
Ein fester, wiederverwendeter Workspace macht den Lauf zusätzlich deutlich schneller.

- GIVEN ein per `EclipseRunTestTool` gestarteter Testlauf, WHEN er startet, THEN erscheint
  kein Dialog und der Lauf terminiert ohne Benutzereingabe
  → `EclipseRunTestToolTest.launchConfigDisablesAskClear`
- GIVEN keine Property `peon.test.ws`, WHEN die Launch-Config gebaut wird, THEN steht
  `location` auf dem Default-Pfad und `clearws` auf `false`
  → `EclipseRunTestToolTest.usesStableWorkspaceLocation`
- GIVEN zwei aufeinanderfolgende Läufe, WHEN beide starten, THEN benutzen sie **denselben**
  Runtime-Workspace → `EclipseRunTestToolTest.reusesWorkspaceAcrossRuns`

## R5 ✅ done — Keine stillen Skips als Dauerzustand

Die Suite meldet am Ende `gelaufen / skipped / rot`. Ein Skip ist nur zulässig mit
technischem Grund (kein Display); jeder andere Skip gilt als Bug.
→ Referenz-Zahlen 2026-09-03 nach dem Zyklus: Plugin **141/141** grün.

**Einschränkung (Werkzeug, nicht SOLL, 2026-09-03):** ~~Der PDE-Runner weist Skips nicht
separat aus — „0 skipped" ist derzeit nicht maschinell verifizierbar~~ **aufgelöst
2026-09-06:** `EclipseRunTestTool` meldet jetzt `Skipped: N` (JDT `testCaseFinished` feuert
auch für Ignored mit `Result.IGNORED`; Maven-Semantik: „Tests" = total inkl. skipped).
Sichtbar ab dem ersten Lauf nach Eclipse-Restart (Report-Formatierung im laufenden Bundle).

## R6 ✅ done — Cap-Test ohne 120 Seed-Dateien (2026-10-15, Paul; ECJ-Cap-Fakt im Bau verifiziert)

**IST:** `EclipseBuildToolTest.buildReportCappedAt100WithDisclosure` schreibt 120 kaputte
`.java`-Dateien ins Fixture und triggert einen echten Build, um >100 Marker zu erzeugen. In einem
degradierten Workspace (Recovery-State nach „unsaved changes" der Vornacht) liefert derselbe Build
nur 2 Marker → der Test rotet ohne Produktfehler (Morgen-Run 2026-10-15).

**SOLL:** Der Cap-Test erzeugt seine >100 Problem-Marker **ohne** 120 Seed-Dateien und so, dass das
Ergebnis nicht vom Zustand des Runtime-Workspaces abhängt. **Gebaute Mechanik (2026-10-15):** drei
Seed-Dateien (`CapSeedA/B/C.java`) mit je 40 unabhängigen Fehlern (`int e<i> = missing<i>;` je Zeile
→ ein Marker je Fehlerzeile). **ECJ-Fakt (im Bau verifiziert, CompilerOptions.java:1614):**
`maxProblemsPerUnit = 100` — der Eclipse-Compiler cappt Probleme **pro Compilation-Unit**, deshalb
genau 3 × 40 statt 1 × 120 (eine Datei mit 120 Fehlern lief exakt 100 Marker). Die Behauptung des
Tests ändert sich nicht: >100 Marker → Report cappt bei 100 mit Disclosure (`showing 100 of 120`).

**WEIL:** Der Test prüft die Cap-/Disclosure-Logik des Tools, nicht die Build-Engine — ein echter
Build über 120 Dateien bindet das Ergebnis an einen fremden Zustand, den der Test nicht kontrolliert.

```
GIVEN das Fixture trägt deterministisch >100 Problem-Marker (Seed-Mechanik ohne 120 Dateien)
WHEN der Build-Report gelesen wird
THEN der Report zeigt genau 100 Einträge und nennt die Disclosure („showing 100 of N")
```
→ `EclipseBuildToolTest.buildReportCappedAt100WithDisclosure` (Seed-Mechanik ersetzt, Name bleibt
aussagekräftig; Maven Surefire bleibt die Ground Truth für die Zählung).

## Mock-LLM-Server — verifizierte Wire-Formate (Night-Cycle A, 2026-08-30)

`MockLlmServer` bildet die Provider-Drähte nach; die Formate sind gegen den **echten
langchain4j-1.18.1-Quellcode** verifiziert (nicht aus der Doku geraten):

| Provider | Format | Merkmale |
|---|---|---|
| Anthropic | SSE | `event:`/`data:`-Paare, Reihenfolge `message_start` → `content_block_start` → … → `message_stop` |
| Ollama | NDJSON | zeilenweise JSON, letzte Zeile trägt `done: true` + `done_reason` |

**WEIL:** Ohne diese Formate testet ein Mock nur sich selbst. Wer den Mock erweitert (neuer
Provider, neues Feld), verifiziert erst am langchain4j-Quellcode und dokumentiert das hier —
Provider-Fähigkeiten selbst stehen in [provider.md](../../org.sterl.llmpeon.core/docs/provider.md).

## Bewusste Abdeckungs-Entscheidung: `configEdit` nur für OpenAI

Der Test „kein Stale-Cache nach Config-Änderung" existiert **nur** für OpenAI, nicht für alle
drei Provider. **WEIL:** die Cache-Clear-Mechanik ist provider-unabhängig
([ADR-0034](../../docs/adr/0034-connection-cache-by-identity.md)) — ein Provider genügt als Beweis, drei
kosten Laufzeit und Wartung ohne Erkenntnisgewinn. Wer sich fragt, warum es das Pendant für
Anthropic/Ollama nicht gibt: es fehlt nicht, es wurde entschieden.

## Verwandter Befund (Zyklus 2)

`JdtUtil.java:127-128` — `IProject instanceof IJavaProject` ist **immer** false, der
Projekt-Scope der Type-Suche ist damit wirkungslos und fällt still auf die workspace-weite
Suche durch (korrekt wäre `JavaCore.create(p.get())`). Bestand bereits vor diesem Zyklus
(Historie März/Mai 2026), passt thematisch zu R4a in
[eclipse-read-tools.md](eclipse-read-tools.md) → dort fixen.

## OSGi-Tests via Maven (headless)

Befehl (Repo-Root):
- lokal (macOS, mit Display): `mvn -B clean verify --file pom.xml`
- CI (Linux, ohne Display): `xvfb-run -a mvn -B clean verify --file pom.xml`
  ([pipeline.md](../../docs/pipeline.md); xvfb ist für SWT-Pfade nötig, die ein X-Display anfordern —
  nicht für die Skips unten)

Der Tycho-`verify` baut Plugin + Test-Bundle und startet den OSGi-Test-Run (tycho-surefire,
`useUIHarness=false` → headless OSGi **ohne Workbench**). Das Fixture `test_project` kommt über
die System-Property `peon.test.project` (im Test-Pom gesetzt).

**Kein stiller Skip (by-design geprüft):** kein `skipTests`/`excludes`/`testFailureIgnore`
in irgendeinem pom; die `TEST-*.xml` unter `org.sterl.llmpeon.test/target/surefire-reports`
belegen die Ausführung im echten OSGi-Framework. „Skipped" zählt **in** „Tests run".

**Bekannte Skips (by-design):** die SWT-UI-Klassen extenden `AbstractSwtUiTest`
(`setUpDisplay` fängt `IllegalStateException: Workbench has not been created yet` →
`Assume.assumeNoException`) und laufen **nur im PDE-Runner** (echte Workbench):
AiConfigPreferenceViewTest · AgentModelConfigSectionTest · ModelConfigWidgetTest ·
ModelComboWidgetTest · AiAdvancedPreferenceViewTest · HeaderRosterStructureTest.
Workspace-Assumes (`EclipseBuildToolTest`, `PeonAiServiceTest`) skippen unter Maven **nicht** —
das Fixture wird auch im OSGi-Run importiert.