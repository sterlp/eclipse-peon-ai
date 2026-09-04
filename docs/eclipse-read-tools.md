# Read Tools (Datei · Grep · Console) — Eclipse **und** Disk

**Ziel:** Die lesenden Eclipse-Tools liefern **vorhersagbar begrenzte** Ausgaben. Ein Agent darf
nie überrascht werden — weder durch eine ganze Datei statt eines Ausschnitts noch durch eine
komplette Konsole statt der gesuchten Zeilen. Jede Begrenzung wird im Ergebnis **benannt**.

Quelle der Befunde: [issues/fact-issues.md](../issues/fact-issues.md) (User-Scratch),
User-Entscheidungen vom 2026-09-03.

**Geltungsbereich (User, 2026-09-03):** Die Regeln gelten für **beide** Tool-Familien —
`eclipseReadFile`/`eclipseGrepFiles` **und** `diskReadFile`/`diskGrepFiles`. Ein Agent darf
nicht wissen müssen, welche Familie er gerade benutzt, um zu verstehen, was ein
Zeilenbereich bedeutet. Wo die Logik geteilt werden kann (`FileLines`, `RegexUtils` im core),
wird sie geteilt — ein Verhalten, eine Implementierung, ein Testsatz.

## R1 ✅ done (2026-09-03) — `eclipseReadFile` **und** `diskReadFile`: Bereich clampen statt Ganzdatei

**IST:** `FileLines.extract` gibt bei `startLine > total` **oder** `endLine > total` die
**komplette** Datei mit Zeilennummern zurück (`FileLines.java:56-58`). Ein Agent, der
`startLine=800, endLine=900` einer 120-Zeilen-Datei liest, bekommt alle 120 Zeilen.

**SOLL:** Über das Dateiende hinausschießende Bereiche werden **geklemmt**, nie erweitert.

**WEIL:** Partial-Read ist ein Kontext-Sparmechanismus; still zur Ganzdatei zu eskalieren
verkehrt ihn ins Gegenteil und ist der Hauptgrund, warum Agenten auf `diskReadFile` ausweichen.

- **R1a ✅ done — `endLine` über dem Ende wird auf die letzte Zeile geklemmt.**
  - GIVEN Datei mit 120 Zeilen, WHEN `eclipseReadFile(startLine=100, endLine=900)`,
    THEN werden die Zeilen 100–120 (mit Zeilennummern) zurückgegeben
    → `FileLinesTest.clampsEndLineToFileEnd`
- **R1b ✅ done — `startLine` hinter dem Ende liefert eine leere, erklärende Antwort.**
  - GIVEN Datei mit 120 Zeilen, WHEN `eclipseReadFile(startLine=800)`,
    THEN ist das Ergebnis leer bis auf einen Hinweis der Form
    `file has 120 lines, requested start 800` — **nicht** die ganze Datei
    → `FileLinesTest.startBeyondEndReturnsHint`
- **R1c ✅ done — bestehendes Verhalten bleibt.** `startLine<=0 && endLine<=0` → ganze
  Datei ohne Zeilennummern; `start>end` → getauscht; gültige Bereiche unverändert.
  **Reihenfolge ist Teil der Regel** (Review-Befund L1): erst tauschen, dann klemmen, dann
  Bounds-Check — sonst liefert `(900, 100)` den R1b-Hinweis statt der Zeilen 100–120.
  `endLine = 0` bleibt Sentinel („bis Dateiende") und wird nicht getauscht.
  → `FileLinesTest.existingBehaviourUnchanged`
- **R1e ✅ done — `diskReadFile` verhält sich identisch.** Dieselbe Clamp-Semantik
  (R1a/R1b/R1c/R1d) gilt für das Disk-Tool; beide Tools benutzen **denselben** `FileLines`-Pfad
  im core. Divergierendes Verhalten zwischen den Familien ist ein Bug.
  - GIVEN Datei mit 120 Zeilen, WHEN `diskReadFile(startLine=100, endLine=900)`, THEN werden
    die Zeilen 100–120 geliefert → `DiskReadFileToolTest.clampsEndLineToFileEnd`
  - GIVEN dieselbe Datei, WHEN sie einmal über `eclipseReadFile` und einmal über
    `diskReadFile` mit gleichem Bereich gelesen wird, THEN ist die Ausgabe identisch
    → `DiskReadFileToolTest.matchesEclipseReadFile`

- **R1d 🔒 zurückgezogen (2026-09-03) — es gibt keine stille Kürzung.**
  Der Dev-Befund „`eclipseReadFile` kürzt lange Ausgaben ohne Hinweis" hat sich bei der
  Analyse des vollen Pfads (`FileLines` → `DefaultToolExecutor` → `SmartToolExecutor` →
  `ToolService.execute` → `ThreadSafeMemory.addResult` → `ToolLoopRequest.call` →
  `StreamingBridge`) **nicht bestätigt**: Das Tool-Ergebnis wird nirgends gekappt. Die Limits
  in `ChatMessageUtil` (`6000`/`90000`/`900000`) dienen Logging, Dedup-Suche und
  Token-Schätzung und verändern die gespeicherte Nachricht nicht;
  `SmartToolExecutor.java:60-62` kürzt nur den Anzeigetext für `monitor.onProblem`.
  Das beobachtete Verhalten war R1a/R1b (Bereich ignoriert → Ganzdatei) bzw. die UI-Anzeige.
  Ein expliziter Zeilen-Cap wird **nicht** eingeführt — ein solcher Cap wäre eine neue,
  willkürliche Verhaltensänderung; die Ehrlichkeitsregel greift bereits über R1a/R1b/R2c/R3c.

## R2 ✅ done (2026-09-03) — `eclipseGrepFiles`: Regex first, Literal als Fallback

**IST:** `RegexUtils.countOccurrences` rät anhand der Zeichen `* | + ^ $`, ob die Query ein
Regex ist (`isRegexPattern`). Heuristik-Fehltreffer in beide Richtungen: `foo(bar` wird als
Literal gesucht, `a.b` ebenso, während `C++` fälschlich als Regex kompiliert wird und knallt.

**SOLL:** Kein Raten mehr — **erst kompilieren, bei `PatternSyntaxException` literal suchen.**

**WEIL:** Deterministisch und für den Agenten erklärbar; deckt beide User-Fälle ab (echte
Regex funktioniert, kaputte Regex liefert Treffer statt Fehler).

Umgesetzt als `SearchQuery`-Record im core (Query + Pattern + Modus, `ConcurrentHashMap`-Cache,
**einmal pro Query** kompiliert statt pro Datei). `RegexUtils.isRegexPattern` entfernt.

- **R2a ✅ done — gültiges Pattern → Regex-Suche** (case-insensitive, wie bisher).
  - GIVEN Query `Model.*Widget`, WHEN gegrept wird, THEN matcht sie als Regex
    → `RegexUtilsTest.validPatternUsesRegex`
- **R2b ✅ done — ungültiges Pattern → Literal-Suche, kein Fehler.**
  - GIVEN Query `foo(bar` (`PatternSyntaxException`), WHEN gegrept wird, THEN wird literal
    (case-insensitive `contains`) gesucht und Treffer geliefert
    → `RegexUtilsTest.invalidPatternFallsBackToLiteral`
- **R2c ✅ done — Der Modus wird IMMER benannt.** Nicht nur beim Fallback:
  `literal search — query is not a valid regex` bzw. `regex search`. Ein Wort im Ergebnis.
  → `EclipseGrepToolTest.reportsLiteralFallback`, `.reportsRegexMode`

  **WEIL (Befund 2026-09-03, Dev):** `C++` ist ein **gültiges** Regex (`C` mit possessivem
  Quantifizierer) und matcht jedes `C` — es fällt also **nicht** auf Literal zurück. Ebenso
  `a.b` (matcht `axb`). Ohne Modus-Angabe kann ein Agent ein überraschendes Ergebnis nicht von
  einem korrekten unterscheiden und weiß nicht, dass er `\Q…\E` bzw. Escaping braucht.
  Den Modus nur im Ausnahmefall zu melden, verschiebt das Raten lediglich in den Normalfall.

  **Bewusst KEIN Sonderfall für `C++` & Co.** Eine Heuristik „sieht aus wie Code, also literal"
  wäre genau die Zeichen-Raterei, die ADR-0035 abgeschafft hat. Die Query bestimmt den Modus,
  das Ergebnis sagt welchen.
- **R2d ✅ done — kein Treffer ist kein Fehler.** Leeres Ergebnis → Meldung
  `no matches`; keine Exception. Das `MAX_FILES = 100`-Cap und sein Hinweis-Suffix bleiben.
  → `EclipseGrepToolTest.noMatchesReportsEmpty`

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
  genau dann ein Falsch-Negativ droht. → [ADR-0038](adr/0038-refresh-on-empty-search.md)
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
   ([ADR-0038](adr/0038-refresh-on-empty-search.md) gilt bisher nur für die Suche).
3. `isTextFile:93-99` überspringt **still** Dateien ohne Punkt oder mit unbekannter Endung
   (`.gitignore`, `Dockerfile`, `Makefile`, `.env`) — Falsch-Negativ nach Dateityp.

**SOLL:** Punkt 1 und 2 übernehmen das bereits abgenommene Muster unverändert; Punkt 3 wird
zusammen mit R6 gelöst.

**WEIL:** Eine Tool-Familie, in der die Dateisuche ehrlich und der Grep still lückenhaft ist,
ist schlimmer als eine, in der beide gleich schlecht sind — der Agent kann nicht wissen,
welchem Ergebnis er trauen darf.

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

## R6 ✅ done (2b-2 + R6b aus dem E2E-Test) — Beide Grep-Familien sehen dieselben Dateitypen

**IST:** `EclipseGrepTool` und `DiskGrepTool` pflegen **je eigene** `TEXT_EXTENSIONS`-Listen,
die auseinanderlaufen. Dieselbe Query liefert je nach Tool-Familie andere Treffer — ohne dass
der Agent den Grund sehen kann.

**SOLL:** Eine gemeinsame Endungs-Whitelist im core; beide Tools benutzen sie.
**Umgesetzt:** `org.sterl.llmpeon.shared.TextFileTypes` (core) — `EXTENSIONS` = **Vereinigung**
beider Altlisten (34 Disk-Endungen + 7 Eclipse-Extras `mf`, `prefs`, `product`, `target`,
`project`, `classpath`, `bnd`, kein Endungsverlust) + `FILE_NAMES` für punktlose Namen.
Die lokalen `TEXT_EXTENSIONS`/`isTextFile` in `EclipseGrepTool` und `DiskGrepTool` sind entfernt.

**WEIL:** Gleiches Prinzip wie R1e/R5 — ein Verhalten, eine Implementierung. Eine unsichtbar
abweichende Whitelist ist ein Falsch-Negativ mit Ansage (vgl. R4).

- GIVEN eine Datei mit Endung X, WHEN sie über `eclipseGrepFiles` **und** `diskGrepFiles`
  gesucht wird, THEN ist sie für beide entweder sichtbar oder für beide unsichtbar
  → `TextFileTypesTest.textExtensionsAreShared` (die Whitelist lebt in der neuen core-Klasse
  `TextFileTypes`, nicht in `SearchQuery` — Test gehört in die Klasse, die die Regel trägt)
- **R6a ✅ done — punktlose und unbekannte Dateien sind kein stilles Nichts.**
  Dateien ohne Endung (`Dockerfile`, `Makefile`) bzw. mit unbekannter Endung werden entweder
  durchsucht oder die Ausgabe nennt, dass nach Dateityp gefiltert wurde — nie beides
  verschweigen.
  **Entscheidung 2b-2 (PO, 2026-09-03): beides.** Bekannte punktlose Namen (`Dockerfile`,
  `Makefile`, `Jenkinsfile`, …) kommen als `FILE_NAMES` in `TextFileTypes` und werden
  **wirklich durchsucht**; bleibt das Ergebnis **leer** und war keine `extension` gesetzt,
  nennt die Ausgabe zusätzlich den Typ-Filter. Bei Treffern **kein** Hinweis (dort verschweigt
  nichts, und jede Zusatzzeile kostet in jedem Tool-Call Tokens).
  **WEIL:** Nur-Hinweis wäre eine Assertion auf eine Konstante — ein Vakuum-Test, der auch
  grün bliebe, wenn der Filter weiterhin still zuschlägt.
  - GIVEN `Dockerfile` mit dem Suchbegriff, WHEN ohne `extension` gegrept wird, THEN wird sie
    gefunden **oder** die Ausgabe nennt den Typ-Filter
    → `EclipseGrepToolTest.reportsExtensionFilter` (Dockerfile gefunden, `notes.peonx` nicht)
  - GIVEN ein Begriff, der nur in einer Datei mit unbekannter Endung steht, WHEN ohne
    `extension` gegrept wird, THEN ist das Ergebnis leer **und** nennt den Typ-Filter
    → `EclipseGrepToolTest.namesTypeFilterOnEmptyResult`
  - **R6b ✅ done (E2E-Befund 2026-09-03) — `diskGrepFiles` nennt den Typ-Filter genauso.**
    Der Hinweis war nur im Eclipse-Tool umgesetzt; `diskGrepFiles` meldete bei demselben Fall
    nur `no matches / regex search`. Das ist exakt die Divergenz, die R6 verhindern soll —
    der Agent kann nicht wissen, welchem leeren Ergebnis er trauen darf.
    - GIVEN ein Begriff, der nur in einer Datei mit unbekannter Endung steht, WHEN
      `diskGrepFiles` ohne `extension` läuft, THEN ist das Ergebnis leer **und** nennt den
      Typ-Filter — wortgleich zum Eclipse-Tool
      → `DiskGrepToolTest.namesTypeFilterOnEmptyResult`
    - GIVEN eine gesetzte `extension`, WHEN das Ergebnis leer ist, THEN **kein** Typ-Filter-
      Hinweis (der Filter war ja gewollt)
      → `DiskGrepToolTest.noMatchesReportsEmpty`, `EclipseGrepToolTest.noMatchesReportsEmpty`

    **Umsetzung:** die Hinweislogik lebt jetzt **einmal** in `AiReponseBuilder.grepComplete`
    (4-arg, nimmt `extension`); beide Tools delegieren, die doppelte Eclipse-Logik ist entfernt,
    ebenso die tot gewordene 3-arg-Überladung. Genau das Muster von R6/R1e: ein Verhalten, eine
    Implementierung, ein Testsatz.

Fixture-Ergänzungen: `test_project/Dockerfile` und `test_project/data/notes.peonx`
(Suchbegriff `dockerGrepMe`), verankert in `TestFixtureIntegrityTest.grepTypeFixturesExist`.

## R5 ✅ done (2026-09-03) — `diskGrepFiles`: gleiche Match-Semantik wie `eclipseGrepFiles`

Regex-first mit Literal-Fallback (R2a/R2b), Modus-Meldung (R2c), „no matches" statt Fehler
(R2d) — über den geteilten `SearchQuery`-Pfad. Ergebnis-Formatierung und `MAX_GREP_FILES`
liegen zentral in `AiReponseBuilder`; beide Tools delegieren (Review-Befund L3 — das Duplikat
war die Quelle des Formatierungs-Bugs).
- GIVEN Query `foo(bar`, WHEN `diskGrepFiles` läuft, THEN literal gesucht, Modus benannt
  → `DiskGrepToolTest.invalidPatternFallsBackToLiteral`

## Nicht im Scope
- Keine neue Such-Engine als **Vorgabe**: Default bleibt der `IResourceVisitor`-Weg (einfach,
  testbar, headless). Zeigt R4a jedoch, dass das Falsch-Negativ strukturell am eigenen
  Traversal hängt (Scope/Sync/Wildcard), darf der Dev-Agent die Eclipse-Suche als Alternative
  vorschlagen — mit Begründung, dann entscheidet der PO und ADR-0035 wird ergänzt.
  → siehe [ADR-0035](adr/0035-grep-regex-first-literal-fallback.md)
