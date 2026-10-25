# Eclipse Read Tools — Projekt-Scope, Refresh, Console, Pfad-Dialekt

Geteilte Familien-Regeln (Clamping, `SearchQuery` Regex-first, `TextFileTypes`,
Zeilennummern, Familien-Parität): [read-tools.md](../../org.sterl.llmpeon.core/docs/read-tools.md).
Dieses Doc führt den Eclipse-spezifischen Rest fort (R-Nummern teilen mit dem Core-Doc eine
Fortzählung, weil beide aus einer Story entstanden sind).

## R3 ✅ done (2b-3, 2026-09-03) — `eclipseReadConsoleLog`: Grep + ehrliches Croppen

**IST:** `eclipseReadConsoleLog(consoleName, lines)` schneidet per `FileLines.tail` auf die
letzten `lines` (Default 50). Kein Filter — bei einem langen Maven-Log ist der gesuchte Fehler
mit hoher Wahrscheinlichkeit nicht in den letzten 50 Zeilen, also liest der Agent „alles".

**SOLL:** Optionaler `grep`-Parameter; `lines` limitiert **die gefilterten** Zeilen; die
Ausgabe sagt, wieviel sie von wieviel zeigt.

**WEIL:** Konsolen-Logs sind der größte unkontrollierte Token-Frisser im Build-Zyklus.

**Umgesetzt:** Zähl-/Filter-/Croppen-/Header-Logik liegt als **Log-Auszug** `LogExcerpt` im core
(Eclipse-frei, unit-testbar); das Tool findet nur noch die Konsole und liest ihr `IDocument`.
`SearchQuery` wird wiederverwendet (keine zweite Match-Logik), `FileLines.tail` im ungefilterten
Zweig **aufgerufen** statt nachgebaut. `lines` wird nach unten auf 1 geklemmt. Der alte Präfix
`"<name>:\n"` und der `"empty"`-Sonderfall sind entfallen — eine leere Konsole liefert
`showing 0 of 0 lines (console: X)`.

**Bekannte Konsequenz der Zeilentrennung (bewusst):** `total` zählt wie `FileLines.tail` per
`split("\n", -1)`. Ein Konsolen-Dokument endet meist mit `\n`, dadurch ist `total` um 1 höher
als die sichtbaren Zeilen. Konsistent mit dem Read-Tool, deshalb akzeptiert.

- **R3a ✅ done — `grep` filtert zeilenweise.** Neuer optionaler Parameter `grep`;
  gefiltert wird nach denselben Regeln wie R2 (Regex first, Literal-Fallback), pro Zeile.
  - GIVEN Konsole mit 5000 Zeilen, davon 12 mit `ERROR`, WHEN
    `eclipseReadConsoleLog(grep="ERROR")`, THEN werden genau diese 12 Zeilen geliefert
    → `EclipseConsoleLogToolTest.grepFiltersLines`
- **R3b ✅ done — `lines` limitiert die gefilterten Zeilen (Tail).**
  - GIVEN 12 gefilterte Treffer, WHEN `lines=5`, THEN werden die **letzten 5** Treffer
    geliefert → `EclipseConsoleLogToolTest.limitAppliesAfterGrep`
- **R3c ✅ done — die Ausgabe benennt die Begrenzung.** Header-Zeile der Form
  `showing 5 of 12 matching lines (console: <name>, total 5000)`. Ohne `grep` entsprechend
  `showing 50 of 5000 lines`.
  **Bei gesetztem `grep` nennt der Header zusätzlich den Suchmodus** (`SearchQuery.modeHint()`,
  also `regex search` bzw. `literal search — query is not a valid regex`) — R2c gilt hier
  genauso. **WEIL:** ein ungültiges Regex filtert sonst still literal, und der Agent kann ein
  überraschend leeres Konsolen-Ergebnis nicht von einem korrekten unterscheiden. Ohne `grep`
  kein Modus-Wort (es wird nichts gematcht).
  - GIVEN gecroppte Ausgabe, WHEN gelesen wird, THEN steht die Anzahl gezeigt/gesamt im Text
    → `EclipseConsoleLogToolTest.reportsCropCounts`
  - GIVEN `grep` gesetzt, WHEN gelesen wird, THEN nennt der Header den Suchmodus
    → `EclipseConsoleLogToolTest.reportsCropCounts`
- **R3d ✅ done — Rückwärtskompatibel.** Ohne `grep` bleibt das Verhalten Tail-`lines`
  (Default 50), plus Header aus R3c.
  → `EclipseConsoleLogToolTest.withoutGrepBehavesAsTail`

## R4 ✅ done (2b-1, 2026-09-03) — `eclipseSearchFiles`: „No files found" bei existierenden Dateien

**IST (User + Agenten, wiederkehrend):** `eclipseSearchFiles` (und gelegentlich
`eclipseGrepFiles`) meldet „No files found" für **existierende** Dateien; ein zweiter Versuch
mit kürzerem/anderem Wildcard findet sie. Beispiel aus diesem Zyklus: `Compact agent` →
0 Treffer, `Search agent` → 5 Treffer.

**SOLL:** Reproduzierbares Verhalten oder eine ehrliche Meldung — kein stilles Falsch-Negativ.

**WEIL:** Ein Falsch-Negativ ist der teuerste Tool-Fehler überhaupt: der Agent glaubt, etwas
existiere nicht, und trifft daraufhin falsche Entscheidungen (legt Dateien doppelt an, hält
Code für tot). Ein Fehler ist harmlos dagegen.

**URSACHE GEFUNDEN (Plan-Agent, 2026-09-03) — es ist das Limit, nicht das Wildcard:**

- `EclipseWorkspaceReadFileTool.java:133-137` — ohne `projectName` wird über **alle** offenen
  Projekte iteriert und das Limit pro Projekt heruntergerechnet. `langchain4j` steht
  alphabetisch vor `llmpeon*`: `eclipseSearchFiles("*.java")` mit Default-Limit 100 liefert
  **ausschließlich** langchain4j-Treffer — kein einziger Peon-Treffer. Für den Agenten sieht
  das aus wie „existiert nicht". Ein kürzeres Wildcard „hilft" nur, weil es weniger Fremdtreffer
  produziert.
- Verstärker: jede Datei liegt doppelt im Workspace (`/llmpeon-parent/X` **und** `/X`, weil
  `llmpeon-parent` auf dem Repo-Root offen ist) → jeder Treffer zählt doppelt, effektives
  Limit halbiert.
- `EclipseWorkspaceReadFileTool.java:162` — `results.isEmpty() ||` lässt den **ersten** Treffer
  den Derived-/`bin/`-Filter umgehen.
- `EclipseGrepTool.java:24/40` — `MAX_FILES=100` greift ebenfalls in Projekt-Reihenfolge; das
  Cap kann voll sein, bevor das Zielprojekt an der Reihe ist.

- **R4a ✅ done — Priorisierung statt blindem Limit** (User-Entscheidung 2026-09-03,
  Variante **a**). Ohne `projectName` gilt: **gewähltes Projekt zuerst, dann die übrigen**;
  das Limit wird **global** verbraucht, nicht pro Projekt heruntergerechnet. Fremdprojekte
  bleiben erreichbar (bewusst **nicht** auf das gewählte Projekt eingeschränkt) — nur ihre
  Reihenfolge ändert sich.
  - GIVEN Workspace mit `langchain4j` + `llmpeon-parent`, gewähltes Projekt `llmpeon-parent`,
    WHEN `eclipseSearchFiles("*.java")` ohne `projectName`, THEN stehen Treffer des gewählten
    Projekts **vor** allen Fremdtreffern
    → `EclipseSearchFilesToolTest.selectedProjectComesFirst`
  - GIVEN dieselbe Lage und Limit 10, WHEN gesucht wird, THEN werden **10** Treffer geliefert
    (global gezählt), nicht 10 geteilt durch die Projektanzahl
    → `EclipseSearchFilesToolTest.limitIsGlobal`
  - GIVEN eine Datei, die es nur in einem Fremdprojekt gibt, WHEN ohne `projectName` gesucht
    wird, THEN wird sie weiterhin gefunden
    → `EclipseSearchFilesToolTest.foreignProjectsStayReachable`
- **R4a4 ✅ done — `limit` wird auch nach unten geclampt.** Ein negatives `limit` lieferte
  sonst sofort ein leeres Ergebnis (`0 >= -1`) — ein **Falsch-Negativ**, erzeugt vom Fix
  selbst (Review-Befund L3). Untergrenze 1.
  → `EclipseSearchFilesToolTest` (Clamp-Regressionstest)
  - GIVEN Workspace mit `langchain4j` + `llmpeon-parent`, gewähltes Projekt `llmpeon-parent`,
    WHEN `eclipseSearchFiles("*.java")` mit Default-Limit, THEN sind Treffer aus dem gewählten
    Projekt enthalten → `EclipseSearchFilesToolTest.selectedProjectFirst`
  - GIVEN doppelt sichtbare Dateien (verschachtelte Projekte), WHEN gesucht wird, THEN wird
    jeder Disk-Pfad **einmal** gezählt → `EclipseSearchFilesToolTest.dedupesNestedProjectHits`
  - *Welcher der Pfade gewinnt (das spezifischste Projekt), regelt
    [workspace-nesting.md](workspace-nesting.md) R-WN-1.*
- **R4a2 ✅ done — Derived-Filter gilt auch für den ersten Treffer.**
  `results.isEmpty() ||` entfällt; `bin/`-/Derived-Treffer werden nie zurückgegeben.
  → `EclipseSearchFilesToolTest.neverReturnsDerivedHits`
- **R4a3 ✅ done — Projekt-Scope der Type-Suche wirkt.**
  `JdtUtil.java:127-128` prüft `p.get() instanceof IJavaProject` — ein `IProject` ist **nie**
  ein `IJavaProject`, der Zweig ist tot und die Suche fällt still auf workspace-weit durch
  (Bestand seit März/Mai 2026). Korrekt: `JavaCore.create(p.get())`.
  - GIVEN Typ `Alpha` existiert nur im Fixture, WHEN mit `project="test_project"` gesucht wird,
    THEN wird ausschließlich im Fixture gesucht (kein Fremdprojekt-Treffer)
    → `JdtUtilDiskPathTest.projectScopeIsHonoured`

### Muster dahinter: geratene API-Verträge

`JdtUtil.java:128` (`IProject instanceof IJavaProject` — nie wahr) und
`EclipseUtil.java:97/137` (`PlatformUI.getWorkbench() == null` — gibt nie `null` zurück,
**wirft** `IllegalStateException`; in 2a auf `isWorkbenchRunning()` gefixt) sind derselbe
Fehler: der API-Vertrag wurde geraten statt gelesen. Beide Guards sahen defensiv aus und waren
toter Code, der still das falsche Verhalten durchließ.

**Regel für Zyklus 2b und danach:** Bei Eclipse-Guards immer die Quelle/Javadoc prüfen —
`readTypeSource` bzw. das AI-Harness-Repo. Ein Guard, der nie greift, ist schlimmer als keiner.
  - GIVEN Fixture mit `Alpha.java`, WHEN `eclipseSearchFiles("Alpha*")` und
    `eclipseSearchFiles("*lpha.java")`, THEN liefern **beide** die Datei
    → `EclipseSearchFilesToolTest.wildcardVariantsFindSameFile`
- **R4b ✅ done — Workspace-Sync nur bei leerem Ergebnis.** Existiert die Datei auf Disk,
  aber ist im Workspace nicht sichtbar, wird sie gefunden. Der Refresh läuft **nur, wenn die
  Suche leer ausging**, und genau einmal — nicht vor jeder Suche.
  **WEIL:** Ein Refresh vor jeder Suche kostet bei großen Workspaces spürbar Zeit für einen
  Fall, der selten eintritt; bei leerem Ergebnis ist der Preis dagegen gerechtfertigt, weil
  genau dann ein Falsch-Negativ droht. → [ADR-0038](../../docs/adr/0038-refresh-on-empty-search.md)
  - GIVEN eine ohne Eclipse geschriebene Datei im Fixture, WHEN gesucht wird, THEN wird sie
    gefunden → `EclipseSearchFilesToolTest.findsFileWrittenOutsideEclipse`
  - GIVEN eine Suche mit Treffern, WHEN sie läuft, THEN wird **kein** Refresh ausgelöst
    → `EclipseSearchFilesToolTest.noRefreshWhenResultsFound`
- **R4c ✅ done — leeres Ergebnis wird qualifiziert.** Die Meldung nennt, **wo** gesucht
  wurde (Projekt/Scope) und mit welchem übersetzten Muster — statt nur „No files found".
  - GIVEN kein Treffer, WHEN gesucht wird, THEN nennt die Ausgabe Scope und Pattern
    → `EclipseSearchFilesToolTest.emptyResultNamesScopeAndPattern`

## Test-Fixture

Alle Regeln dieser Story werden gegen das versionierte `test_project`-Fixture getestet
(siehe [test-setup.md](test-setup.md)). Dazu gehören eigens angelegte Suchklassen mit
Inhalten, die die Grenzfälle abdecken: Regex-Metazeichen im Text (`C++`, `a.b`, `foo(bar`),
mehrere Treffer pro Datei, Datei mit bekannter Zeilenzahl für die Clamp-Tests (R1),
unterschiedliche Dateiendungen (Text vs. Binär) und ähnliche Dateinamen für die
Wildcard-Tests (R4). Das Fixture ist die **einzige** Quelle für erwartete Treffer-Zahlen —
nie Produktcode.

## R7 ✅ done (2b-2, 2026-09-03) — Das R4-Muster gilt auch für `eclipseGrepFiles`

**IST (Review-Befund 2b-1, 2026-09-03):** Der Grep hat **exakt dieselbe** Falsch-Negativ-Ursache
wie die Dateisuche vor R4 — sie wurde nur nicht mitgefixt, weil 2b-1 auf `eclipseSearchFiles`
geschnitten war:

1. `EclipseGrepTool.java:39` nutzt `EclipseUtil.openProjects()` (alphabetisch) statt
   `openProjectsPreferring` → `langchain4j` füllt das Cap, bevor das gewählte Projekt
   drankommt. **Korrektur (Plan 2b-2, 2026-09-03):** Das Cap `MAX_GREP_FILES = 100` ist
   bereits **global** (ein `matches` über alle Container, `EclipseGrepTool.java:61/77`) —
   falsch ist **nur die Reihenfolge**, nicht die Zählweise. Die Cap-Logik wird nicht angefasst.
2. Kein Refresh — extern geschriebene Dateien sind für den Grep unsichtbar
   ([ADR-0038](../../docs/adr/0038-refresh-on-empty-search.md) gilt bisher nur für die Suche).
3. `isTextFile:93-99` überspringt **still** Dateien ohne Punkt oder mit unbekannter Endung
   (`.gitignore`, `Dockerfile`, `Makefile`, `.env`) — Falsch-Negativ nach Dateityp.

**SOLL:** Punkt 1 und 2 übernehmen das bereits abgenommene Muster unverändert; Punkt 3 wird
zusammen mit R6 gelöst.

**WEIL:** Eine Tool-Familie, in der die Dateisuche ehrlich und der Grep still lückenhaft ist,
ist schlimmer als eine, in der beide gleich schlecht sind — der Agent kann nicht wissen,
welchem Ergebnis er trauen darf.

**Ergänzung (2026-10-19):** Die Zählweise über **Disk**-Pfade bei verschachtelten Projekten regelt
[workspace-nesting.md](workspace-nesting.md) R-WN-2 (2.14.1 gefixt): eine doppelt sichtbare Datei
zählt genau einmal — Treffer einmal, Cap einmal verbraucht.

- **R7a ✅** GIVEN mehrere offene Projekte, gewählt `X`, WHEN ohne `path` gegrept wird, THEN
  wird `X` zuerst durchsucht und das Cap global verbraucht
  → `EclipseGrepToolTest.selectedProjectComesFirst`, Wiring
  → `PeonAiServiceTest.setProjectWiresGrepTool`
- **R7b ✅** GIVEN eine extern geschriebene Datei, WHEN der Grep leer ausgeht, THEN wird
  **einmal** refresht und erneut gesucht (ADR-0038)
  → `EclipseGrepToolTest.findsFileWrittenOutsideEclipse`
- **R7c ✅** GIVEN eine Suche mit Treffern, THEN **kein** Refresh
  → `EclipseGrepToolTest.noRefreshWhenResultsFound`
- **R7d ❌ specified (Review-Befund 2b-2, User-Freigabe 2026-09-03) — der Refresh bei leerem
  Ergebnis trifft nur das gewählte Projekt.**

  **IST:** Der Refresh umfasst **alle** offenen Projekte mit `DEPTH_INFINITE`
  (`EclipseGrepTool.java:69`, ebenso `eclipseSearchFiles` `EclipseWorkspaceReadFileTool.java:143`).
  Ein einziger fehlgeschlagener workspace-weiter Grep refresht `langchain4j`, `opencode` und
  `copilot-for-eclipse` mit.

  **SOLL:** Ohne expliziten `path`/`projectName` wird **nur das gewählte Projekt** refresht.
  Mit explizitem Scope wird genau dieser Scope refresht. Ist kein Projekt gewählt, entfällt
  der Refresh — kein Workspace-weiter Fallback.

  **WEIL:** Der Fall „Datei wurde extern geschrieben" betrifft praktisch immer das Projekt, an
  dem gerade gearbeitet wird. Ein `DEPTH_INFINITE` über fremde Großprojekte kostet Sekunden für
  ein Ergebnis, das niemand erwartet — ADR-0038 hat den Refresh auf den seltenen Leerfall
  begrenzt, nicht auf den halben Workspace. Gilt für **beide** Tools (eine Tool-Familie, ein
  Verhalten).

  - GIVEN mehrere offene Projekte, gewählt `X`, WHEN ein Grep ohne `path` leer ausgeht, THEN
    wird **genau ein** Container refresht, nämlich `X`
    → `EclipseGrepToolTest.refreshesOnlySelectedProject`
  - GIVEN dieselbe Lage, WHEN `eclipseSearchFiles` ohne `projectName` leer ausgeht, THEN
    ebenso → `EclipseSearchFilesToolTest.refreshesOnlySelectedProject`
  - GIVEN expliziter `path`/`projectName`, WHEN leer, THEN wird genau dieser Scope refresht
    → `EclipseGrepToolTest.refreshesExplicitScope` — **Regressionsschutz, kein R7d-Beweis:**
    dieser Fall war schon vor dem Fix korrekt (der explizite Zweig übergab bereits genau einen
    Container). Der Test hält die Grenze fest, an der Inc 1 hätte vorbeischrammen können, ist
    aber bewusst auch pre-fix grün.
  - GIVEN **kein** gewähltes Projekt, WHEN leer, THEN wird **nicht** refresht
    → `EclipseGrepToolTest.noRefreshWithoutSelectedProject`

## ℹ️ Info — Projektname ≠ Disk-Ordnername (kein Bug, bewusst so)

**Realfall 2026-09-16:** Ein Modell rief `diskReadFile("/llmpeon-parent/docs/docs-linter.md")` auf,
während das Projekt `llmpeon-parent` auf Disk unter `…/eclipse-peon-ai` liegt. Ergebnis:

```
File not found: /llmpeon-parent/docs/docs-linter.md also not in /Users/…/eclipse-peon-ai
```

**Warum:** `FileUtils.resolve:78-88` nimmt einen absoluten Pfad nur, wenn er **existiert** — sonst
fällt es auf `workingDir.resolve(...)` zurück und hängt ihn an den Disk-Root
(`…/eclipse-peon-ai/llmpeon-parent/docs/…`). Beide Kandidaten existieren nicht → ehrlicher Fehler
(`DiskFileReadTool.java:54`).

**Entscheidung (User 2026-09-16): kein Code-Fix, keine Auto-Übersetzung.** Empfohlen wird
**Eclipse-Projektname = Disk-Ordnername**; damit verschwindet der Fall. Dokumentiert auf der
Homepage unter *Context Selection*.

Drei Gründe gegen eine Übersetzung `/project/path` → Disk in den `disk*`-Tools:

1. **Echte Mehrdeutigkeit.** Heißt ein Projekt `docs` oder `tmp`, ist `/docs/x.md` gleichzeitig ein
   gültiger Disk- **und** Eclipse-Pfad. Die Übersetzung müsste raten — und ein falscher Treffer
   wäre still, also die teuerste Fehlerklasse dieses Repos.
2. **Der `WriteValidator` arbeitet auf dem normalisierten Pfad**
   ([write-path-validator.md](../../org.sterl.llmpeon.core/docs/write-path-validator.md)). Eine
   Übersetzungsschicht davor verschiebt
   genau die Stelle, die die Schreibgrenze absichert.
3. **Familien-Trennung.** `QualifiedPathValidator` hat bei Copy/Rename gerade festgelegt: Disk =
   absolut, Eclipse = `/project/path`. Eine Übersetzung im Read-Pfad verwischt das wieder.

**Der Agent hat die Information ohnehin:** `projectInfo` nennt in **jedem** Turn Projektname,
Eclipse-Pfad und Disk-Pfad (`EclipseUtil.java:346-358`, via `UserContext`). Im beobachteten Fall hat
das Modell sie ignoriert — ein LLM-Fehler, kein Werkzeugfehler. Der Fehlerpfad ist self-healing: das
Modell sieht den Fehler und korrigiert.


## Nicht im Scope
- Keine neue Such-Engine als **Vorgabe**: Default bleibt der `IResourceVisitor`-Weg (einfach,
  testbar, headless). Zeigt R4a jedoch, dass das Falsch-Negativ strukturell am eigenen
  Traversal hängt (Scope/Sync/Wildcard), darf der Dev-Agent die Eclipse-Suche als Alternative
  vorschlagen — mit Begründung, dann entscheidet der PO und ADR-0035 wird ergänzt.
  → siehe [ADR-0035](../../docs/adr/0035-grep-regex-first-literal-fallback.md)


## ℹ️ 2026-10-16 — Resolve-Reihenfolge für Eclipse-Pfade (Pauls WIP, uncommitted)

- Die eclipse*-Tools lösen Pfade über `EclipseUtil.resolveInEclipse(path, Optional<IProject>)` in
  dieser Reihenfolge auf: **1) selektiertes Projekt** (falls eines aktiv) → **2) Workspace-Root** →
  **3) offene Projekte** (je inkl. `src/`-Fallback) → sonst `Optional.empty()`.
- **Pfad-Modi:** Eclipse-relative Pfade sind **projekt-relativ** oder **workspace-relativ** —
  workspace-relativ heißt `/ProjectName/...` bzw. beginnt mit dem (Modul-)Projekt-Namen
  (z. B. `org.sterl.llmpeon.core/...`).
- **Miss-Fall:** ehrliches `No eclipse file found for <path>` **mit Diagnose-Hint** (Grund benennen:
  Projekt geschlossen / auf Disk nicht im Baum → `eclipseRefreshProject` / unbekanntes Projekt) —
  [workspace-nesting.md](workspace-nesting.md) R-WN-5 (2026-10-24, T-5/I-4). **Supersedes** die
  Paul-Entscheidung 2026-10-16 „bewusst ohne Projekt-Disclosure": die False-Negative-Klasse
  (Agent glaubt „existiert nicht") hat sich als teurer erwiesen als die Extra-Zeile; Pauls
  Triage T-5 forderte die Diagnose ausdrücklich.
- Resolution-Reihenfolge heute (R-WN-4, 2026-10-24): **1) selektiertes Projekt** → **2) Workspace-Root**
  → **3) offene Projekte** (inkl. `src/`-Fallback, ⏳ Entfernung) → **4) Disk-Lokation Last-Resort**
  (`resolveByLocation`, Disclosure `resolved via <pfad>` bei abweichender Auflösung).
- Status: ✅ gebaut (2026-10-24, T-5): R-WN-4/R-WN-5 umgesetzt, `volatile currentProject` =
  Thread-Safety-Fix für Background-Jobs. Die Auflösung semantisch final; der ⏳-Punkt `src/`-Fallback
  bleibt in workspace-nesting.md geführt.

## R10 ✅ done (2026-10-25, E2E-Triage B1) — `eclipseList`: Refresh vor dem Listing

**IST (E2E-Befund, `ai-e2e-test/search-agent-e2e-issues.md` Issue 1):** `eclipseList` mappete den
Workspace-Resource-Tree ohne Refresh — eine frisch auf der Platte erzeugte Datei fehlte
stillschweigend, während `eclipseSearchFiles`/`eclipseGrepFiles`/`eclipseReadFile` sie finden
(Familien-Bruch + False Negative).

**SOLL:** Vor dem `members()`-Mapping wird der gelistete Container mit `refreshLocal(DEPTH_ONE)`
aktualisiert — DEPTH_ONE, weil die Liste nicht-rekursiv ist. Ein Refresh-Fehler ist nie still:
Disclosure-Zeile in der Ausgabe **und** `onProblem` mit Pfad und Ursache. Der Refresh läuft über
einen injizierbaren Seam (Muster `PlanFileView`), Cancellation wird ungefangen durchgereicht.

**WEIL:** „A tool must never lie" — eine veraltete Liste liest sich als „existiert nicht", die
teuerste Fehlerklasse des Repos. ADR-0038 (Refresh nur im Leerfall) gilt für die Such-Tools;
`eclipseList` refresht **immer**, weil sein Ergebnis die Behauptung „das ist der Inhalt" trägt.

- GIVEN eine Datei wurde per java.io in den Projektordner geschrieben (am Workspace-Tree vorbei),
  WHEN `eclipseList` auf den Ordner läuft, THEN ist die Datei gelistet
  → `eclipseListSeesFileWrittenOutsideEclipse` (PDE)
- GIVEN der Refresh schlägt fehl, WHEN `eclipseList` läuft, THEN Disclosure + onProblem, nie eine
  still veraltete Liste → Mutation „stiller Refresh-Fehler" rot (PDE-Suite)
