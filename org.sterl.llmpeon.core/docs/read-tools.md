# Read Tools (Datei · Grep · Console) — Eclipse **und** Disk

**Ziel:** Die lesenden Eclipse-Tools liefern **vorhersagbar begrenzte** Ausgaben. Ein Agent darf
nie überrascht werden — weder durch eine ganze Datei statt eines Ausschnitts noch durch eine
komplette Konsole statt der gesuchten Zeilen. Jede Begrenzung wird im Ergebnis **benannt**.

Quelle der Befunde: [issues/fact-issues.md](../../issues/fact-issues.md) (User-Scratch),
User-Entscheidungen vom 2026-09-03.

**Geltungsbereich (User, 2026-09-03):** Die Regeln gelten für **beide** Tool-Familien —
`eclipseReadFile`/`eclipseGrepFiles` **und** `diskReadFile`/`diskGrepFiles`. Ein Agent darf
nicht wissen müssen, welche Familie er gerade benutzt, um zu verstehen, was ein
Zeilenbereich bedeutet. Wo die Logik geteilt werden kann (`FileLines`, `RegexUtils` im core),
wird sie geteilt — ein Verhalten, eine Implementierung, ein Testsatz.

Eclipse-spezifischer Scope (Projekt-Priorisierung, Refresh-on-empty, Derived-Filter,
Console-Header, Workspace-Pfad-Dialekt):
[eclipse-read-tools.md](../../org.sterl.llmpeon/docs/eclipse-read-tools.md).

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
  **Supersedet in dieser einen Klausel durch R9 (2026-09-17):** die Ganzdatei-Ausgabe
  (`startLine<=0 && endLine<=0`) trägt künftig ebenfalls Zeilennummern — Rest von R1c
  (Tauschen, Klemmen, Reihenfolge) unverändert.
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

## R8 ✅ done (2026-09-19) — Grep liefert Trefferzeilen mit Zeilennummern

**IST (verifiziert, SearchAgent 2026-09-17):** Beide Grep-Familien (`EclipseGrepTool.java:46,126`,
`DiskGrepTool.java:55,72`, Output via `AiReponseBuilder.grepComplete`) listen **keine** Trefferzeilen —
nur `pfad: N occurrence(s)`. Der Agent muss die Trefferstelle in einem Folge-Read suchen.

**SOLL:** Der Grep liefert die **passenden Zeilen mit 1-basierter Zeilennummer** (gezählt über den
vollen Datei-Inhalt) im Format `pfad:42: text`. Gilt für **beide** Familien — geteilte Logik im core
(`AiReponseBuilder`/`SearchQuery`), eine Implementierung. Die per-File-Occurrence-Zählung entfällt
(die Zeilen tragen die Information); **Caps und Ehrlichkeit bleiben**:

- `MAX_GREP_FILES = 100` bleibt; zusätzlich ein **Zeilen-Cap (Default 100 Zeilen gesamt,
  dateiübergreifend)** mit Disclosure `showing N of M matched lines — narrow your search`
  (gleiche Ehrlichkeitsfamilie wie `showing N of M` bei den Read-Tools).
- `modeHint()` (regex/literal, R2c) und `TextFileTypes.filterHint()` bleiben unverändert.

**WEIL:** Paul: „grep scheint keine Zeilennummern zu liefern" — der IST ist schlimmer: keine Zeilen
überhaupt. `pfad: N occurrences` erzwingt einen zweiten Read pro Treffer; Zeilen + Nummern machen
den Treffer sofort adressierbar (`replaceLines`, `insertLines`, Range-Read). Der Zeilen-Cap schützt
vor der Token-Bombe bei häufigen Begriffen — counts-only war der bisherige, unbenannte Cap.

- GIVEN Datei mit Treffer in Zeile 42, WHEN grep läuft, THEN erscheint `pfad:42: <Zeileninhalt>`
- GIVEN 500 Treffer über 20 Dateien, WHEN grep läuft, THEN werden die ersten 100 Zeilen geliefert
  mit `showing 100 of 500 matched lines`

## R9 ✅ done (2026-09-19) — Zeilennummern immer, auch beim Ganzdatei-Lesen

**SOLL:** Alle Datei-Reads liefern Zeilennummern — **auch** bei `startLine<=0 && endLine<=0`
(Ganzdatei). Die Wegoptimierung „Ganzdatei ohne Zeilennummern" wird zurückgenommen; das supersedet
genau diese Klausel von R1c (Rest von R1c unverändert, siehe Notiz dort).

**WEIL:** Paul: „scheinbar sind die aber immer hilfreich" — beim Ganzdatei-Lesen hatte er sie
wegoptimiert, doch auch danach braucht der Agent Zeilennummern für Folge-Edits. Token-Preis
(~5–7 Zeichen/Zeile) ist kleiner als ein erneuter Range-Read oder eine falsche Edit-Position.

## ℹ️ 2026-10-16 — Grep-Target darf eine einzelne Datei sein (Pauls Fix) — ✅ getestet (DiskGrepToolTest)

- `diskGrepFiles`: ein **regulärer Dateipfad** als `path` ist jetzt ein gültiges Target — das Tool
  greppt genau diese Datei (decompose: `grepDirectory`/`grepFile`-Zweige am resolved Path, gleiche
  Ausgabe-Form). **WEIL:** vorher → `IllegalArgumentException("Directory not found")` → False
  Negative (Agent glaubt, das File existiert nicht) — die teuerste Fehlerklasse dieser Codebasis.
  `eclipseGrepFiles` konnte Datei-Targets schon (IFile-Zweig in `resolveInEclipse`).
- **Semantik Datei-Target:** explizite Datei schlägt den Extension-Filter (Datei wird ungefiltert
  gegreppt); binäre Datei → ehrlicher Fehler (kein „no matches"). Pfade-Arg, das weder Datei noch
  Ordner ist, bleibt ein ehrlicher Fehler.
- GIVEN Datei-Pfad als `path` WHEN grep THEN Trefferzeilen dieser Datei (Line-Cap + Disclosure wie
  bei Ordner-Grep) · GIVEN nicht existierender Pfad THEN Fehler, kein False Negative
