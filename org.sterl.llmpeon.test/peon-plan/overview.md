# Mini-Zyklus: Debugger F2 + get_exception + Docs-Linter (Textblock/Manuell/idPattern)

## ⛔ STOP-AND-ASK (zuerst lesen)
Bei **Compile-Fehlern ohne Lösung**, **nicht-grün-bekommenden Tests** oder **IST-Widersprüchen zu
diesem Plan**: STOPP und Rückfrage über den Jon-Kanal — nie still workarounden, nie SOLL ändern.
- UC-Status bleibt in allen Docs **❌** — Flippen ❌→✅ macht **ausschließlich Jon** nach Review.
  Niemand in diesem Zyklus rührt die Feature-Docs an (nur die Plan-Datei + Code + Tests).
- Git: auf dediziertem Branch bauen (Rule 19): `story/f2-debug-linter-2026-09-21`,
  **abgezwungen von `analysis/tool-evolution`** (Debugger-Code/Story C lebt dort, noch nicht
  gemerged — nicht von main). Vorbereitung: Abschnitt „Vorbereitungen" vor I1.

## 1. Kontext
Zwei Feature-Docs tragen ❌-Regeln, die in einen Mini-Zyklus zusammengebaut werden:
- `docs/java-debugger-tool.md`: **R-JD-9** (UC-JD-10 statics, UC-JD-11 evaluate-Objektfelder),
  **R-JD-10** (UC-JD-12 `get_exception`).
- `docs/docs-linter.md`: **R-DL-21** (UC-DL-64 Java-Textblock-Belege zählen nicht),
  **R-DL-22** (UC-DL-65 manuell-Marker exemprt, UC-DL-66 ohne Marker bleibt UNBELEGT_ERLEDIGT),
  plus Doku-Hinweis: `idPattern` **FULL-matcht** die UC-ID → in die @P-Description
  (keine neue UC — reiner Beschreibungsfix).
Begründungen (WEIL-Blöcke): `docs/open-points.md` (🔒 F2 / Exception / Manuell-Marker / Code-Block-Regel),
WEILs in den Feature-Docs selbst. IST-Fakten unten per File:Zeile verifiziert (2026-09-21).

## Status
- **I1 ✅ DONE** (Commit `7e8fb41`): Core-Linter grün (923/0/19), Dogfood verifiziert.
- **I2 ✅ DONE**: `DebugJson.variables` → `{locals, statics}`, `evaluated` → Tiefe 2, Descriptions, 4 neue/angepasste Tests; `DebugJsonUnitTest` 9/9, Voll-Suite 258 grün (JavaDebugToolTest inkl.).
- **G1 ✅ DONE** (Commit `348d521`, PO-Review-Fix): Heuristik behalten; get_exception-Description nennt die Name-Grenze (Exception/Error-Suffix, unübliche Namen → get_variables) + Pin-Test `exceptionNotRecognizedByUnusualName` (UC-JD-12); Suite 261 grün. R-JD-10-Doku-Text = Jon (vor UC-JD-12-Flip).
- **I3 ✅ DONE** (Commit `75eb9ac`): `DebugJson.exception` + `isThrowable` + `messageOf` (getMessage via sendMessage, null bei Fehler), `JavaDebugTool.getException` (14. Action), 2 neue Stub-Tests (UC-JD-12) + `getException(null)` in `noSessionFailsHonest`; Voll-Suite 260 grün.

## 2. Slicing — 3 vertikale Inkremente, je für sich kompilierend + grün

### Vorbereitungen (vor I1 — Git, einmalig)
- Branch `story/f2-debug-linter-2026-09-21` **von `analysis/tool-evolution`** anlegen
  (Paul: Debugger-Code/Story C lebt auf diesem Branch, noch nicht gemerged — **nicht** von main;
  Merge/Squash = später Pauls Entscheidung).
- Vorher Git-Zustand selbst prüfen: aktueller Branch (`analysis/tool-evolution` erwartet),
  Working Tree sauber? Uncommitted-Docs von Jon/Paul liegen an — die geänderten Dateien:
  `docs/java-debugger-tool.md`, `docs/docs-linter.md` (u. a. R-DL-22-Präzisierung),
  `docs/open-points.md`.
- Diese ausstehenden Docs-Änderungen werden **mit dem ersten Inkrement-Commit (I1)**
  committet — nie separat, nie vergessen (Rule 19).
- Ist-Zustand weicht ab (schmutziger Tree mit fremden Änderungen, falscher Branch,
  Branch existiert schon): STOP-AND-ASK.

### Inkrement 1 — Linter (Core, `org.sterl.llmpeon.docslinter`)
**UC-IDs: UC-DL-64, UC-DL-65, UC-DL-66** (+ idPattern-@P-Fix ohne eigene UC)
Besteht aus (a)+(b)+(c), am Ende des Inkrements ein grüner Core-Testlauf.

**(a) idPattern-@P-Description (Full-Match)**
- IST: `idPattern`-Parameter existiert in **zwei** @Tool-Methoden — `DocsLinterTool.lintDocs`
  (:52) und `lintDocsAndTests` (:79). Die dritte Methode `nextIds` (:105) ist package-private,
  kein Tool, und hat `prefix` statt `idPattern` → keine @P-Stelle dort. ("Alle 3 Methoden" =
  2 annotierte Stellen; per Grep verifiziert.)
- ÄNDERUNG: neue package-private Konstante in `DocsLinterTool`
  `ID_PATTERN_DESCRIPTION = "regex that FULL-matches UC ids (e.g. UC-PP-\\d+); full-match, not a prefix"`
  und `@P(required = false, name = "idPattern", description = ID_PATTERN_DESCRIPTION)` an beiden
  Stellen. Zentrale Stelle reicht (eine Konstante, zwei Referenzen).
- TEST (in `DocsLinterToolTest`, Muster des UC-DL-46-Tests `exposesOnlyExpectedReadParameters` :91):
  über `ToolService.toolSpecifications()` die `JsonSchema.Property` von `idPattern` für
  `lintDocs` und `lintDocsAndTests` holen und `description()` contains "FULL-match" (bzw.
  "full-match, not a prefix") asserten.

**(b) R-DL-21 — Textblock-Erkennung im Test-Quell-Scan**
- IST: `TestParser.parse` (:25) scannt zeilenbasiert ohne Code-Zustand; `extractCommentIds` (:80)
  akzeptiert `//`, `#`, `--`. Eine ID-Zeile in einem Java-Textblock (`"""`…`"""`) wird deshalb
  fälschlich als Beleg gezählt → Dogfood-Befund `VERWAIST UC-DL-99 DocsLinterToolTest.java:78`.
- ÄNDERUNG in `TestParser.parse` (nur `.java`-Dateien, Extension-Check an der parse-Einstieg):
  - `boolean inTextBlock = false;` pro Datei.
  - Pro Zeile: Anzahl der Vorkommen der 3er-Sequenz `"""` zählen
    (`line.indexOf("\"\"\"")`-Loop oder split); **ungerade** → Zustand kippen.
    **Kein Escaping-Handling** (bewusst einfach, SOLL + R-DL-4).
  - Während `inTextBlock`: `extractCommentIds` wird **nicht** aufgerufen (Zeile liefert keinen
    Beleg) — aber der Zustand wird trotzdem um die `"""` der Zeile fortgeschrieben.
  - Nicht-`.java`-Dateien: Verhalten unverändert (keine Toggle-Logik).
- TEST UC-DL-64 (in `TestParserTest`, temp-file-Muster der Klasse):
  `.java`-Datei mit `// UC-XY-1` **innerhalb** eines `"""`-Blocks → kein Beleg;
  dieselbe Zeile **außerhalb** → Beleg. ZUSATZ: `.md`/andere Extension bleibt unverändert (ID
  in `"""`-ähnlichem Kontext zählt dort weiter).
- KONSEQUENZ (erwartet, nicht defekt): Der Dogfood-Befund `VERWAIST UC-DL-99` verschwindet —
  die Textblock-Zeilen in `DocsLinterToolTest.java:76-80` werden nicht mehr als Beleg gelesen.
  Der Test `returnsEveryFindingWithoutTruncation` (UC-DL-45, :63-87) bleibt **UNVERÄNDERT** grün:
  sein `VERWAIST UC-DL-99 src/test/java/Test.java:2` kommt aus der **geschriebenen** Temp-Datei
  (ohne `"""` in der Datei), nicht aus dem Quelltextblock. Fixture `Test.java`-Inhalt bleibt rein.
- REGRad-SCHUTZ: keine existierende Test-Fixture enthält `"""` in **geschriebenen** Inhalte →
  kein anderer Lauf kippt. (Vor dem Inkrement-Start per Grep verifizieren:
  `"""` in `llmpeon-core/src/test/resources/**` und in Test-Quellen, die als Fixture gelesen werden.)

**(c) R-DL-22 — `manuell`-Marker exemprt vom UNBELEGT-Check, neuer Finding-Typ MANUELL**
- IST: `DocParser` :75-105 — Heading-Block; `STATUS_EMOJI_PATTERN` (:16) liefert `textAfter`
  (:85) = alles nach dem Emoji. `DocDefinition` = 7-Komponenten-Record, **einzige**
  Konstruktor-Stellen: `DocParser:191` (`makeDefinition`) und `DocParser:234-235`
  (Re-slot in `validateStructure`). `DocsLinter` :244-256: ✅ ohne Testevidenz →
  `UNBELEGT_ERLEDIGT` (:250). `findingPriority` (:186-194) ist ein **exhaustive switch** über
  `FindingType` → Compile-Kanari für neue Enum-Values (deshalb ÄNDERUNG in EINEM Inkrement,
  Slicing-Regel). `DocsLintReportRenderer` ist typgenerisch (filtert :68 nur
  `UNBELEGT_ERLEDIGT` für die Statuszeile) → **unverändert**.
- ÄNDERUNGEN:
  1. `FindingType`: neuer Value `MANUELL` (info, **kein** Blocker).
  2. `DocDefinition`: 8. Komponente `boolean manuell` anhängen; `DocParser:191` setzt sie,
     `DocParser:234-235` kopiert `def.manuell()` beim Re-slot.
  3. `DocParser`: im Heading-Block nach `textAfter` berechnen:
     `boolean manuell = isManuellMarker(textAfter);` — nur relevant bei ✅-Status.
     Marker-Prädikat (loose — von Paul bestätigt, SOLL in `docs-linter.md` entsprechend präzisiert):
     `textAfter` (getrimmt) besteht aus optionaler `*`, genau einem `(`…`)`-Klammernpaar, das
     den Substring **`manuell`** enthält, und optionaler abschließender `*` — sonst false.
     (Regex-Vorschlag: `^\*?\s*\([^()]*manuell[^()]*\)\s*\*?$` — bindet am Ende, weil
     `manuell` sonst z. B. im Titeltext vor dem Status matchen würde. Substring-Match für
     `manuell` → deckt `manuelle` ab.)
  4. `DocsLinter` :249-251 (Entscheidungspunkt in `lintWithTests`):
     ```
     if (status.startsWith("✅")) {
         if (def.manuell()) → LintFinding(MANUELL, id, file, line)
         else               → LintFinding(UNBELEGT_ERLEDIGT, …)   [bestehend]
     } else UNBELEGT [bestehend]
     ```
     `lint()` (docs-only) erzeugt **keine** MANUELL-Befunde (Exemption betrifft nur den
     UNBELEGT-Check mit Tests) — keine Änderung dort.
  5. `findingPriority`: `case MANUELL -> 5;` (nach UNBELEGT=4; info am Ende der Sortierung).
- TESTS UC-DL-65/66 (in `DocsLinterMatchingTest`, temp-fixture-Muster der Klasse):
  - UC-DL-65: Doc mit `#### UC-XX-1 … ✅ *(manuell verifiziert 2026-09-21, ADR-0051)*`,
    kein Test mit der ID → `lintWithTests` ⇒ **kein** `UNBELEGT_ERLEDIGT`, **aber** Befund
    `MANUELL` mit id + `datei:zeile` im Report-Output.
  - UC-DL-66: Doc mit `#### UC-XX-2 … ✅` (kein manuell-Anhang), kein Test ⇒
    `UNBELEGT_ERLEDIGT` — Regressionsschranke.
  - **Realform-Schutz**: zusätzlicher Fall
    `✅ (manuelle Verifikation 2026-09-21, ADR-0051)` **ohne** Asterisk ⇒ MANUELL —
    das ist die reale Form der UC-JD-2…6-Headings (die eigentliche Motivation, WEIL R-DL-22).
- FIXTURE: `DocsLinterFindingsFixtureTest` — ein neues Fixture-Doc unter
  `org.sterl.llmpeon.core/src/test/resources/docs-linter/findings/docs/` mit einem ✅-UC
  (eigene freie ID, z. B. `UC-DL-16`) + manuell-Marker und **ohne** Test → Assertion
  `anyMatch(f -> f.type() == FindingType.MANUELL && f.id().equals("UC-DL-16"))` und
  `noneMatch(UNBELEGT_ERLEDIGT für dieselbe ID)` ergänzen (eine Befundart pro Fixture-Fall,
  Nicht-funktional-Tabelle). Bestehende Fixture-Befunde (UC-DL-8…15) bleiben unverändert.
- **Kein** Renderer-Änderung: MANUELL rendert über den generischen Befund-Pfad als
  `MANUELL <id> <file>:<line>`; Statuszeile/Kappa zählt weiterhin nur UNBELEGT_ERLEDIGT.

GATE I1: `eclipseRunTests` Project `llmpeon-core` (JUnit 5/Surefire, AssertJ) — alle
`org.sterl.llmpeon.docslinter.*` grün; danach optional Dogfood-`lintDocsAndTests` als Sichtbarkeits-
Check (VERWAIST UC-DL-99 weg, MANUELL-Zeilen für UC-JD-2…6).

### Inkrement 2 — Debugger F2 (Plugin `org.sterl.llmpeon`)
**UC-IDs: UC-JD-10, UC-JD-11**
- IST (verifiziert): `JavaDebugTool.getVariables` :72-82 (Description "…(statics not included)"),
  `DebugJson.variables(IJavaStackFrame, String namePath, int depth)` :112-150 — ohne namePath
  liefert ein **bloßes Array** von `variableNode`s, depth `d = clamp(1..5)`;
  `DebugJson.evaluated(IJavaValue)` :216-235 — Objekt-Endzweig `node.put("value", valueString(value))`
  (:233) = bloße Referenz "(id=N)"; Walker `applyValue(node, value, depth)` :296-320
  (`depth >= 2 && hasVariables(value)` → `fields` mit `depth-1`).
- API-Fakten (readTypeSource 2026-09): `IJavaStackFrame.getDeclaringType()` ist **deprecated** →
  `IJavaStackFrame.getReferenceType()` (`IJavaReferenceType`) verwenden.
  `IJavaReferenceType.getAllFieldNames()` + `getField(name)` → `IJavaFieldVariable`
  (nicht local); `IJavaVariable.isStatic()` filtert statisch.
- ÄNDERUNGEN:
  1. `DebugJson.variables` (nur der namePath-leere Zweig, :120-126):
     - `locals`: bestehendes Rendering (Array von `variableNode(variable, d)`).
     - `statics`: `IJavaReferenceType rt = frame.getReferenceType();`
       (DebugException → `DebugSupport.fail` wie bei getLocalVariables) →
       `getAllFieldNames()`; für jeden Namen `rt.getField(name)` (null → skip),
       `field.isStatic()` (DebugException → fail) → nur statische Felder als
       `variableNode(field, d)` (dieselbe Verschachtelung/Tiefe wie locals).
       Keine Felder → leeres Array `[]` (immer beide Keys vorhanden).
     - Return-Shape: `{ "locals": [ … ], "statics": [ … ] }` statt bloßes Array.
     - **namePath-Drill-down-Zweig (:127-149) bleibt unverändert** (Single-Variable-Output).
  2. `DebugJson.evaluated` (:233, Objekt-Endzweig): statt `valueString` →
     `applyValue(node, value, 2)` — derselbe Walker, **fixe Tiefe 2** (identisches Verhalten
     wie `get_variables` mit `depth=2`: Felder eine Ebene, deren Felder als Wert).
     null/Primitive/String unverändert; `IJavaArray`-Zweig unverändert.
  3. `JavaDebugTool` @Tool-Descriptions:
     - `get_variables` (:72): "(statics not included)" ersatzlos weg; neu: "… as JSON with
       frame locals plus the static fields of the frame's declaring type in a separate
       `statics` block (empty when none). Optional name path (a.b.c) and depth (default 1, max 5)."
     - `evaluate_expression` (:84): "Object results come back as a reference id (… (id=N))."
       → "Object results render their fields to depth 2; primitives, String and null come back as values."
- TESTS (in `DebugJsonUnitTest` — Proxy-Stub-Muster :32, keine Session nötig):
  - UC-JD-10 (zwei Tests): Stub `IJavaStackFrame` mit `getLocalVariables` (1-2 Lokale) +
    `getReferenceType` → Stub `IJavaReferenceType` (`getAllFieldNames` z. B. `["counter", "cache"]`,
    `getField("counter")` → `IJavaVariable`-Stub `isStatic=true` + int-Wert, `getField("cache")` →
    `isStatic=false` (darf NICHT erscheinen)).
    THEN JSON enthält `"statics"` mit `counter` (Name+Typ+Wert) und `"locals"` unverändert;
    non-statische `cache` fehlt. Zweiter Test: `getAllFieldNames() = []` → `"statics": []`.
  - UC-JD-11: `DebugJson.evaluated(object("Foo", "Foo@1", field("name", String "a"), field("n", int 3)))`
    → Output enthält `fields` mit `name`/`n` (Werte) und **keine** bloße " (id=N)"-Referenz;
    verschachteltes Feldobjekt zeigt auf Tiefe 2 **keine** eigenen `fields` mehr (Tiefenlimit),
    sondern den Wert. Primitive/`nullValue`/String bleiben als Wert gerendert (Regression:
    bestehende evaluated-Tests dürfen unverändert grün bleiben — Shape der value-/primitive-Zweige
    ist unverändert).
  - **Bestehende `variables`-Tests anpassen**: alle Assertionen auf die neue
    `{locals, statics}`-Shape (z. B. Pfade `$.locals[0]…` statt `$.[0]…`, oder JSON-parse +
    Liste-Extraktion). Das gehört in dies Inkrement (gleiche Datei, gleicher Befund).
GATE I2: `eclipseBuildProject` über `org.sterl.llmpeon` **und** `org.sterl.llmpeon.test`
(stale bin/-Klassen!), dann `eclipseRunTests` Project `org.sterl.llmpeon.test`
(PDE/OSGi, JUnit 4) — `DebugJsonUnitTest` + `JavaDebugToolTest` grün. Erster Lauf braucht
einmalig Workspace-Trust (Rule 13) — nicht parallel nachstarten.

### Inkrement 3 — `get_exception` (Plugin)
**UC-IDs: UC-JD-12**
- IST: Thread-Auflösung besteht: `DebugSession.findActive()` + `session.resolveThread(thread)`
  (Muster `getStackTrace` :63-70). `noSessionFailsHonest` listet 13 Actions
  (`JavaDebugToolTest` :35-49).
- ÄNDERUNGEN:
  1. `DebugJson`: neue `static String exception(IJavaThread thread)`:
     - Top-Frame via `thread.getTopStackFrame()` (DebugException → `DebugSupport.fail`);
       null-Frame → ehrlicher IAE ("no top frame — thread has no stack").
     - `frame.getLocalVariables()` scannen (DebugException → fail).
     - Treffer-Bedingung pro Variable: `value = variable.getValue()` (DebugException → fail);
       `value instanceof IJavaObject obj` && `!obj.isNull()` && `isThrowable(obj.getJavaType())`.
     - `isThrowable(IJavaType t)` (private, namebasiert — Design-Entscheid, see §Design):
       `String n = t.getName()`; true wenn `"java.lang.Throwable".equals(n)` oder der
       **Simple-Name** (Teil nach letztem `.`) mit `Exception` oder `Error` endet.
       (Deckt alle JDK- + typischen User-Ausnahmen ab; dokumentierte Grenze: direkte
       `Throwable`-Subtypen ohne *-Exception/*Error-Namen entgehen — `get_variables` bleibt
       der Weg.)
     - Message: `obj.sendMessage("getMessage", "()Ljava/lang/String;", null, thread, false)`
       (readTypeSource-verifizierte Overload); Ergebnis `IJavaValue` → null/nullValue →
       `message = null`, sonst `getValueString()`/`IJavaPrimitiveValue.getStringValue()`;
       `DebugException` → `message = null` (ehrlich, kein Fallback-Raten).
     - Response: `{ "varName": <name>, "type": <getReferenceTypeName>, "message": <…|null> }`
       (pretty, LinkedHashMap — Reihenfolge wie oben).
     - **Kein Treffer → `IllegalArgumentException("no exception variable in top frame")`**
       (ehrlich, kein erfundener Typ — UC-JD-12).
     - Stateless: kein Event-Listening, kein Cache (R-JD-3).
  2. `JavaDebugTool`: neue Action
     ```java
     @Tool(name = "get_exception", value = "Find the exception at the current suspend: scans the "
         + "top frame's local variables (incl. catch parameter) for a java.lang.Throwable or "
         + "subtype and returns its type, message and variable name. Limit: an uncaught "
         + "throw new X(...) at the throw site has no named variable and is not found there.")
     public String getException(@P(name = "thread", description = "Thread name; empty = first "
         + "suspended thread, else first non-system thread.", required = false) String thread) {
         var session = DebugSession.findActive();
         if (session == null) return noSession("get_exception");
         return DebugJson.exception(session.resolveThread(thread));
     }
     ```
     (14. Action; @P-Text wortgleich mit den bestehenden Thread-Parametern.)
- TESTS:
  - UC-JD-12 (in `DebugJsonUnitTest`, Stubs):
    - Fund-Fall: Thread-Stub → `getTopStackFrame` → Frame-Stub mit `getLocalVariables` =
      { `int count` (Prim-Stub), `e` → `IJavaObject`-Stub: `getJavaType` =
      `javaType("java.lang.IllegalStateException")`, `isNull=false`,
      `sendMessage` → String-Prim-Stub "boom" } → JSON enthält `"varName":"e"`,
      `"type":"java.lang.IllegalStateException"`, `"message":"boom"`; `count` wird nicht
      gemeldet (Beidseitigkeit: Scan-Input und Output).
    - Kein-Treffer-Fall: Frame nur mit Prim-Stub → `assertThrows(IllegalArgumentException,
      msg contains "no exception variable in top frame")`.
  - `JavaDebugToolTest.noSessionFailsHonest`: `tool.getException(null)` zur Listeners-Liste
    hinzufügen (→ 14 Actions; Javadoc-Kommentar "alle 13 Actions" der Testklasse/UC-JD-1-
    Anmerkung anpassen, falls vorhanden).
GATE I3: wie I2 (Build beider Projekte, dann Suite). Erster Trust-Dialog des Zyklus ist mit
I2 abgehakt.

## 3. Design-Entscheidungen (bereits gefallen)
1. **idPattern-Description**: eine gemeinsame Konstante in `DocsLinterTool`, Referenz in
   beiden @P (lintDocs, lintDocsAndTests). `nextIds` bleibt unverändert (kein Tool-Param).
2. **Textblock-Toggle**: reiner Zeilen-Zähler über `"""` in `TestParser`, nur `.java`,
   kein Escaping, keine Fence-/Blockkommentar-Logik (SOLL: "bewusst einfach", R-DL-4).
   `DocParser` ist **nicht** betroffen (Markdown hat keine Java-Textblocks).
3. **manuell-Marker**: Flag auf `DocDefinition` (Parse-Ebene) + Entscheidung in `DocsLinter`
   (Match-Ebene) — Kapselung: `DocParser` weiß nur "Ist das ein Marker?", `DocsLinter` weiß
   "Was bedeutet das für Befunde?". Marker-Matcher als private Methode in `DocParser`
   (kein eigener Class — eine Regex + ein Prädikat).
4. **MANUELL-Priorität 5** (nach UNBELEGT): info-artige Zeile, sortiert am Ende;
   `findingPriority`-Switch = Compile-Kanari, deshalb im selben Inkrement wie der Enum-Value.
5. **statics-Shape**: `{locals, statics}`-Objekt nur im namePath-leeren Zweig; Drill-down
   bleibt Single-Node. Keine neue Parameter (SOLL: "kein zusätzlicher Parameter").
   `getReferenceType()` (nicht deprecatedes `getDeclaringType()`).
6. **evaluated-Tiefe**: `applyValue(node, value, 2)` — derselbe Walker wie `get_variables`,
   keine zweite Rendering-Logik. Fixe 2 = bewusst (SOLL).
7. **get_exception-Scan**: namebasiert (`java.lang.Throwable` exakt, sonst Simple-Name
   `*Exception`/`*Error`). Alternative (strikte `IType.isAssignableFrom` mit
   `JavaCore.findType`) verworfen: bindet `DebugJson` an Workspace-Static und macht den
   Session-freien Stub-Test kaputt; Name-Heuristik deckt alle realen Fälle und die
   Rest-Lücke ist in Tool-Description + Plan dokumentiert. Message via `sendMessage`
   (bestehende JDT-Möglichkeit, kein eigener Parser).
8. **Fehler-Konvention**: wie bestehendes Tool — ehrliche `IllegalArgumentException`- /
   `DebugSupport.fail`-Meldungen; kein Auto-Start (R-JD-1).

## 4. Betroffene Dateien (vollständig)
**Core `llmpeon-core`** (Workspace-Pfad `/llmpeon-core/`, Disk
`/Users/sterlp/dev/workset/peon-ai/org.sterl.llmpeon.core/`) — alle unter
`src/main/java/org/sterl/llmpeon/docslinter/`, außer Tests:
| Datei | Inkrement | Art |
|---|---|---|
| `DocsLinterTool.java` | I1a | ändern (Konstante + 2 @P-Zeilen :52, :79) |
| `TestParser.java` | I1b | ändern (Textblock-Toggle in `parse`) |
| `DocParser.java` | I1c | ändern (manuell-Flag, 2 Konstruktor-Stellen :191/:234, Marker-Prädikat) |
| `DocDefinition.java` | I1c | ändern (8. Komponente `boolean manuell`) |
| `FindingType.java` | I1c | ändern (`MANUELL`) |
| `DocsLinter.java` | I1c | ändern (Entscheidungspunkt :249-251, `findingPriority` case) |
| `DocsLintReportRenderer.java` | — | **unverändert** (typgenerisch, :68 filtert nur UNBELEGT_ERLEDIGT) |
Tests (JUnit 5 + AssertJ):
| Datei | Inkrement | Art |
|---|---|---|
| `src/test/java/org/sterl/llmpeon/docslinter/TestParserTest.java` | I1b | UC-DL-64-Tests |
| `src/test/java/org/sterl/llmpeon/docslinter/DocsLinterMatchingTest.java` | I1c | UC-DL-65/66 (+Realform-Fall) |
| `src/test/java/org/sterl/llmpeon/docslinter/DocsLinterFindingsFixtureTest.java` + `src/test/resources/docs-linter/findings/docs/*.md` (neue Datei) | I1c | MANUELL-Fixture + Assertions |
| `src/test/java/org/sterl/llmpeon/docslinter/DocsLinterToolTest.java` | I1a | Description-Assertion (Muster :91-109); UC-DL-45-Test :63-87 **unverändert** |

**Plugin `org.sterl.llmpeon`** (Workspace `/llmpeon-parent/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/debug/`,
Disk `/Users/sterlp/dev/workset/peon-ai/org.sterl.llmpeon/...`):
| Datei | Inkrement | Art |
|---|---|---|
| `DebugJson.java` | I2 | ändern (`variables` Shape :120-126, `evaluated` :233) |
| `DebugJson.java` | I3 | ändern (neu `static exception(IJavaThread)`, `isThrowable`) |
| `JavaDebugTool.java` | I2 | ändern (2 @Tool-Descriptions :72, :84) |
| `JavaDebugTool.java` | I3 | ändern (neu `getException`, 14. Action) |
| `DebugSession.java` | — | **unverändert** (`findActive`/`resolveThread` werden nur mitgenutzt) |

**Test-Modul `org.sterl.llmpeon.test`** (Eclipse JUnit 4, OSGi;
`/org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/`):
| Datei | Inkrement | Art |
|---|---|---|
| `DebugJsonUnitTest.java` | I2 | UC-JD-10/11-Stub-Tests + bestehende `variables`-Assertions auf neue Shape |
| `DebugJsonUnitTest.java` | I3 | UC-JD-12-Stub-Tests (Fund + ehrlicher Fehler) |
| `JavaDebugToolTest.java` | I3 | `getException(null)` in `noSessionFailsHonest` (14 Actions, :35-49) |

**Nicht anfassen**: `docs/**` (Status-Flips = Jon), `DocsIdTool`, `DebugSupport`,
`DocsLintReportRenderer`, `TestParser`-Aufruf in `DocsLinter` (:222 — Signatur bleibt).

## 5. Regeln & Constraints
- **Test-Honesty** (AGENTS-DEV, Rule 30): Stub-Tests asserten beide Richtungen — der
  gestubte Input (welche Variablen/Werte der Scan sieht) UND das gerenderte Output (Shape +
  Werte + dass Nicht-Treffer NICHT erscheinen). Nicht nur "ein Call kam".
- Test-Kommentar `// UC-X-n` (reine ID-Zeile) direkt über jeden Beleg-Test, ID aus dem
  SOLL-Doc kopiert.
- Core: JUnit 5 + AssertJ. Plugin-Test: JUnit 4, **keine** externen Assert-Libs (OSGi).
- Vor jedem Plugin-JUnit-Lauf: `eclipseBuildProject` über `org.sterl.llmpeon` **und**
  `org.sterl.llmpeon.test` (Rule 16: stale bin/ → ClassNotFoundException). Surefire-Reports
  in `target/` sind stale — nicht als Evidenz werten.
- PDE-Test erster Lauf: Workspace-Trust-Dialog manuell bestätigen (Rule 13); bei Timeout
  nicht parallel nachstarten.
- Keine neuen Dependencies, keine API-Änderungen nach außen (Tool-Verträge: `get_variables`
  ändert die JSON-Shape bewusst — SOLL R-JD-9; `evaluate_expression`-Shape bei
  Primitive/String/null unverändert).
- Secrets/PII-Regel (Rule 20) nicht betroffen, aber Exception-Messages dürfen keine
  Stacktraces/Inhalte aus der VM unkontrolliert hereinziehen — nur `getValueString`/
  `sendMessage`-Ergebnis.
- Dev committet nach jeder grünen Iteration (Code **und** — falls angefasst — Prompts/Doku;
  hier: nur Code+Tests) auf dem abgestimmten Branch (Rule 19).
- DONE-Claims werden vom PO gegen IST verifiziert (Rule 28): je Inkrement konkrete Evidenz
  (Status-Strings, Test-Namen, Grep-Counts), nicht "sollte grün sein".

## 6. BDD-Akzeptanz (aus SOLL-Docs, Status bleibt ❌)
- **UC-DL-64** (R-DL-21) — GIVEN `.java`-Testdatei mit `// UC-XY-1` in `"""`-Block WHEN
  gescannt THEN kein Beleg; GIVEN dieselbe Zeile außerhalb THEN Beleg.
  → `TestParserTest` (zwei Tests: inTextBlockNoEvidence / outsideTextBlockEvidence).
- **UC-DL-65** (R-DL-22) — GIVEN UC ✅ mit `*(manuell verifiziert 2026-09-21, ADR-0051)*`,
  kein Test mit der ID WHEN `lintDocsAndTests` THEN kein `UNBELEGT_ERLEDIGT`, aber
  Info-Zeile `MANUELL` mit `datei:zeile`. → `DocsLinterMatchingTest` (+ Fixture-Test).
- **UC-DL-66** (R-DL-22) — GIVEN UC ✅ ohne manuell-Anhang, ohne Test THEN
  `UNBELEGT_ERLEDIGT` (Regressionsschranke). → `DocsLinterMatchingTest`.
  (idPattern-Description: keine UC — Verifikation per Assertion in `DocsLinterToolTest`.)
- **UC-JD-10** (R-JD-9) — GIVEN Frame in Klasse mit statischen Feldern WHEN `get_variables`
  THEN `statics` mit Feldern (Name, Typ, Wert), `locals` unverändert; GIVEN keine statischen
  Felder THEN leeres `statics`-Array. → `DebugJsonUnitTest.staticsInGetVariables`,
  `DebugJsonUnitTest.staticsEmptyWhenNone`.
- **UC-JD-11** (R-JD-9) — GIVEN `evaluate_expression("p")` liefert Objekt THEN Felder bis
  Tiefe 2 im Output, keine bloße " (id=N)"; Primitive/String/null als Wert.
  → `DebugJsonUnitTest.evaluateRendersObjectFieldsToDepth2` (+ Primitive/null-Regression).
- **UC-JD-12** (R-JD-10) — GIVEN Thread an Exception-Suspend mit Catch-Variable `e` WHEN
  `get_exception` THEN Typ + Message + Variablenname; GIVEN kein Throwable im Top-Frame
  THEN ehrlicher Fehler, kein erfundener Typ. → `DebugJsonUnitTest.exceptionFindsThrowableInTopFrame`,
  `DebugJsonUnitTest.exceptionHonestErrorWhenNone` (+ `noSessionFailsHonest` mit 14 Actions).
- Dogfood-Evidenz (kein Beleg, Sichtbarkeitscheck nach I1): `VERWAIST UC-DL-99
  DocsLinterToolTest.java:78` aus dem Linter-Report verschwunden.

## 7. Test-Strategie
- Core: `eclipseRunTests` Project `llmpeon-core` — Paket `org.sterl.llmpeon.docslinter`
  komplett (kein einzelner Test: Regressionsschutz über die ganze Linter-Suite, v. a.
  `DocsLinterDocsTest`, `DocParserTest`, `DocsLintReportRendererTest`, die an
  `DocDefinition`/Findings angrenzen).
- Plugin: `eclipseRunTests` Project `org.sterl.llmpeon.test` (PDE, OSGi) — mindestens
  `DebugJsonUnitTest` + `JavaDebugToolTest` + übrige Debug-*Tests der Suite.
- Zeitlimits: Debug-Stub-Tests sind Session-frei und millisekunden-schnell; keine
  Retry/Timeout-Logik nötig. `evaluate_expression`-Timeout-Logik wird nicht berührt.
- Reihenfolge: I1 (Core, schnell) → I2 → I3 (Plugin, PDE-Workbench einmalig trauen).
- Nach I1: optionaler Dogfood-Lauf `lintDocsAndTests` über das Peon-Repo als
  Sichtbarkeits-Check (kein Gate, kein Commit-Artefakt).

## 8. Offene Fragen
**Keine.** Q1 (Marker-Strenge) = **loose** bestätigt (Paul 2026-09-21; SOLL in
`docs-linter.md` präzisiert: Klammer-Anhang `(…)`, Asterisk optional, `manuelle` matcht —
UC-JD-2…6 damit automatisch gedeckt). Q2 (Branch) = **`story/f2-debug-linter-2026-09-21`
abgezwungen von `analysis/tool-evolution`**; Git-Vorbereitung = Abschnitt „Vorbereitungen"
(Docs von Jon/Paul mit dem I1-Commit).
