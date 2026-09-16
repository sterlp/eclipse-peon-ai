# Open Points

Status je Punkt: ❓ offen · ⏳ selbst entschieden (Rückversicherung mit User steht aus) · 🔒 geklärt.

## ⏳ `nextIds` reserviert nicht — Zustandslosigkeit ist Feature, der Ablauf ist die Pflicht (2026-09-15)

**Pauls Frage:** „Wenn `nextIds` eine ID zieht — wie stellen wir sicher, dass es immer eine NEUE
liefert? Sucht es nach einem Eclipse-Neustart die letzte ID in den Docs?"

**Antwort:** Ja — und der Neustart ist der harmlose Teil. `DocsLinter.nextIds` hält nachweislich
keinen Zustand (kein Feld, kein Cache, kein Zähler; `DocsLinter.java:35-110` baut die
`prefixStats`-Map bei jedem Aufruf neu aus dem frisch gelesenen Doc-Baum). `DocsLinterTool` hält nur
das `workingDir`. Neustart, neuer Rechner, frischer Clone: identisches Ergebnis, weil es nur an den
Dateien hängt. **Ein persistenter Zähler wäre schlechter** — er könnte still von den Docs abweichen.

**Die echte Lücke, die Paul instinktiv getroffen hat, liegt woanders:** zwischen *Ziehen* und
*Speichern*. `nextIds` **reserviert nichts**. Ziehe ich `UC-DL-56` und schreibe sie nicht sofort
ins Doc, liefert der nächste Aufruf dieselbe Nummer → `DOPPELT_DEFINIERT`, also genau der Befund,
gegen den das Tool gebaut wurde. Dasselbe, wenn die Doc-Datei nur im Editor-Buffer geändert ist
(R-DL-12: Disk-Read).

**Selbst entschieden und gebaut (2026-09-15, `934ea7c`, Review CONCERNS → abgenommen):**
Zustandslosigkeit bleibt, keine Registry-Datei, kein Zähler. Stattdessen als **R-DL-16 +
UC-DL-56/57** spezifiziert, gebaut und in `po.md` als Arbeitsablauf verankert (ziehen → sofort
schreiben → speichern → erst dann die nächste). Zusätzlich legt `nextIds` jetzt — wie die
Lint-Methoden (R-DL-12/UC-DL-4) — Lesequelle und Doc-Dateizahl offen; bei `nextIds` ist das
wertvoller als bei den Lint-Methoden, weil ein veralteter Stand dort keinen sichtbaren Falschbefund
erzeugt, sondern eine **unsichtbare doppelte ID**. Core Surefire 859 → 861, 0 failures.

**🔒 Nachtrag 2026-09-16:** Paul hat den Ablauf bestätigt und dabei zurecht gefragt, ob `nextIds`
überhaupt Mehrwert hat. Antwort: erst mit **R-DL-17** (ein Präfix spannt ein Feature über n Dateien
auf) — vorher lebte ein Präfix per `PRAEFIX_DOPPELT` in genau einer Datei und die nächste Nummer war
aus dem offenen Doc ablesbar. Beides gebaut (`934ea7c`, `ab71c53`), Punkt geschlossen.


## ⏳ Eclipse-Installationsfehler eines Users — NICHT unser Bug, kein Target-Rollback (2026-09-15)

**Auslöser:** Paul reichte `ins_err.log` + `failing bundles.log` eines Users herein mit der Frage,
ob der Target-Sprung 2026-03 → 2026-09 ([ADR-0044](adr/0044-target-2026-09-dependency-update.md))
die Installierbarkeit gebrochen hat, und ob wir zurückrollen müssen. Vorgabe: „keine Aktion, wenn
kein Problem".

**Befund (aus `ins_err.log`, Zeilen 1–120):** Die Meldung `Could not resolve module:
org.sterl.llmpeon [1032] → Unresolved requirement: Require-Bundle: org.eclipse.jface` ist nur das
letzte Glied. Die Kette dahinter:

```
org.eclipse.jface → Require-Capability: eclipse.swt; filter:="(image.format=svg)"
  → org.eclipse.swt.svg → com.github.weisj.jsvg
    → org.apache.aries.spifly.dynamic.bundle 1.3.7  ← hier bricht es
```

`spifly` scheitert an einer **uses-constraint violation**: es sieht `org.objectweb.asm` in **zwei
Versionen gleichzeitig** — `9.10.1` direkt und `9.9.1` transitiv über `org.objectweb.asm.commons
9.9.1`, das `asm [9.9.1,9.10.0)` fordert. Zwei inkonsistente asm-Versionen in **einer**
Installation.

**Warum es nicht an uns liegt — drei unabhängige Belege:**

1. **Wir fordern dort gar nichts.** `org.sterl.llmpeon/META-INF/MANIFEST.MF` listet
   `org.eclipse.swt`, `org.eclipse.jface`, `org.eclipse.ui` **ohne jede `bundle-version`**. Kein
   Constraint von uns kann diese Auflösung erzwingen oder verhindern.
2. **Wir liefern kein asm mit.** Der `Bundle-ClassPath` (60 `lib/*.jar`) enthält **kein**
   `org.objectweb.asm`, kein `jsvg`, kein `spifly` — geprüft, Volltext gelesen. Wir tragen zur
   Versionskollision nichts bei.
3. **Nur Plattform-Bundles scheitern.** `failing bundles.log` (690 Zeilen) nennt genau zwei
   ungelöste Bundles: `org.eclipse.epp.package.common` und `org.eclipse.epp.package.rcp`
   (4.41.0.20260903-0719) — die EPP-Paketierungs-Bundles von Eclipse selbst. `org.sterl.llmpeon
   2.8.1.qualifier` steht dort als `[RESOLVED]`.

Jedes beliebige Plugin mit JFace-Abhängigkeit würde in dieser Installation scheitern; unseres ist
nur das erste, das darüber stolpert.

**Entscheidung (selbst getroffen, Rückversicherung offen): KEIN Rollback auf 2026-03.** Ein
Rollback würde die asm-Kollision in der User-Installation nicht beheben — sie sitzt in dessen
SVG-Support-Stack, nicht in unserem Target — und kostet den 2026-09-Stand samt ADR-0044. Empfehlung
an den betroffenen User: frische Eclipse-2026-09-Installation bzw. Start mit `-clean`; eine
gemischte Installation (z.B. über ein altes Update-Site-Profil aktualisiert) ist die wahrscheinliche
Ursache.

**Nachzuholen, wenn Paul zustimmt:** Mindest-Eclipse-Version auf der Homepage nennen (heute steht
sie nirgends) — das ist die einzige Aktion, die aus dem Vorfall überhaupt folgt.


## 🔒 Docs-Linter liest Überschriften in Code-Blöcken als Definitionen (2026-09-15, Dogfooding-Fund) — GELÖST

**IST:** Beim ersten Lauf gegen das eigene Repo meldet der Linter drei Falschbefunde in
`docs/docs-linter.md`: `DOPPELT_DEFINIERT R-READTOOLS-4:41` und `PRAEFIX_FREMD R-READTOOLS-4:41`
sowie `:73`. Beide Zeilen stehen in ` ```markdown `-**Code-Blöcken** — Beispiele, die das
Doc-Format illustrieren (Pattern-Beispiel und Doc-Template).

**SOLL-Lücke:** R-DL-2 legt fest „nur ein ID-Vorkommen in einer Markdown-**Überschrift** ist eine
Definition; jedes Vorkommen im Fließtext ist eine Referenz". Ein Code-Block ist weder das eine noch
das andere — er ist zitierter Text. Die Regel nennt den Fall nicht, der Parser kennt ihn nicht.

**Warum das zählt:** Betroffen ist jedes Doc, das Doc-Struktur erklärt — also ausgerechnet Template-
und Meta-Docs. Es ist ein False **Positive** (der Linter meldet einen Fehler, der keiner ist), nicht
die teure False-Negative-Klasse. Aber es erzeugt Rauschen genau dort, wo das Werkzeug Vertrauen
aufbauen muss, und drei Rauschbefunde in 41 Zeilen Report sind zu viel.

**Gelöst (2026-09-15):** als **R-DL-13** + UC-DL-42/43 spezifiziert und gebaut (`797670c`, `43d4ff8`),
Review bestanden. Fenced Code Blocks werden beim Doc-Parsing übersprungen — ein gemeinsamer
Fence-Zustand schützt Überschriften **und** `FORM_ABWEICHEND`. Dogfooding danach: die drei
Falschbefunde sind weg, `UC definitions: 43 / 43` (vorher `43 / 42`), findings 41 → 37.

**Abhängig davon:** Das Nachtragen der ID-Kommentare an die DL-Tests (nächster Punkt) ist jetzt
unblockiert — der Report ist rauschfrei.

## ❓ Code-Block-Regel für Testquellen? (2026-09-15, aus R-DL-13 ausgeklammert)

**Frage:** R-DL-13 lässt Code-Blöcke in **Docs** nicht mehr als Definition zählen. Der Spiegelfall
in **Testquellen** ist offen: Ein ID-Kommentar in einem Java-Textblock (`"""…"""`), einem
Python-Docstring oder einem eingebetteten Beispiel-Snippet würde heute als echter Beleg zählen — ein
Beispiel könnte einen Use-Case fälschlich als belegt ausweisen. Das wäre die teure
False-**Negative**-Richtung: ein falsches ✅, das nie wieder jemand prüft.

**Warum NICHT sofort gebaut:** Zitierten Text in beliebigen Sprachen zu erkennen erfordert
Sprach-Parsing — exakt die Rateübung, die wir in Q8 bewusst verworfen haben (jeder Rateversuch auf
fremde Grammatik erzeugt stille Lücken). Eine Regel schreiben, deren Umsetzung raten muss, wäre
derselbe Fehler nochmal.

**Wie wahrscheinlich ist der Fall?** Ein Testfile, das einen ID-Kommentar als Beispiel-String
enthält, ist selten — aber dieses Repo ist selbst ein Kandidat, sobald die Linter-Tests eigene
Fixtures mit ID-Zeilen bauen (tun sie bereits, allerdings in `@TempDir`-Dateien, nicht als
String-Literale).

**Optionen:** (a) offen lassen bis es real auftritt; (b) nur die eine triviale Heuristik „ID-Zeile
innerhalb eines Java-Textblocks" abdecken; (c) Report nennt Belege aus verdächtigen Kontexten
gesondert, statt sie zu unterdrücken. **Meine Empfehlung: (a)** — der Linter meldet lieber zu viel
als zu wenig, und ein konkreter Fall ist die bessere Spezifikationsgrundlage als eine Vermutung.

## 🔒 Docs-Linter: ID-Kommentare an den eigenen Tests (2026-09-15) — TEILWEISE GELÖST

**Gelöst für den Nachzyklus (2026-09-15):** Die 11 UCs des Read-only/Split-Zyklus
(UC-DL-4/45/46/47/48/49/50/51/52/53/55) tragen jetzt reine `// UC-DL-<n>`-Belegzeilen — **erzwungen
durch Da Doks Review**, der den Flip auf ✅ blockierte, solange die Tests für den Linter unsichtbar
waren. Das Werkzeug hat damit seinen eigenen ersten echten Fang gemacht: Wir hätten sonst genau das
falsche `✅` gesetzt, gegen das es gebaut wurde.

**Lektion daraus, die über diesen Fall hinausgeht:** Ein Test, der eine strukturelle Unmöglichkeit
prüft, ist eine **Regressionsschranke, kein Beleg** — er darf keine UC-ID tragen. Konkreter Fall:
`PeonAiServiceTest.diskTogglePreservesJonsDocsFacades` ist trivial grün (Jons `ToolService` wird
einmalig gebaut und nie mutiert), trägt deshalb bewusst **keine** ID und sagt im Kommentar, was er
*nicht* beweist. Die ID sitzt am shared-Test, wo die Mutation greift.

**Weiter offen (Bestand):** Die ~37 älteren DL-Tests aus dem Erstzyklus tragen weiterhin keine
ID-Kommentare. **Empfehlung unverändert:** in einem eigenen kleinen Zyklus nachziehen, nicht als
Teil eines Feature-Zyklus — und den repo-weiten Sweep davon getrennt halten.


**IST:** `docs/docs-linter.md` steht seit heute auf ✅ done (gebaut, Review bestanden, Surefire
860/0, alle Mutationsnachweise erbracht). Der erste Dogfooding-Lauf meldet aber **37×
`UNBELEGT_ERLEDIGT`** — die Tests existieren und sind grün, tragen aber die
`// UC-DL-<n>`-Kommentarzeilen noch nicht, mit denen der Linter sie zuordnet.

**Selbst entschieden:** Das ✅ bleibt stehen. Die Regeln sind nachweislich implementiert und
falsifizierbar getestet; es fehlt nur die maschinenlesbare Zuordnung. Das Werkzeug meldet hier also
korrekt, was es melden soll — der Bestand ist noch nicht opt-in-fähig annotiert.

**Offen für den User:** Sollen die ID-Kommentare an den DL-Tests nachgetragen werden (dann ist der
Docs-Linter sein eigener erster sauberer Beleg), oder lassen wir das für einen Sweep, der den
gesamten Bestand annotiert? Meine Empfehlung: nur die DL-Tests jetzt, Rest separat — sonst wird aus
dem Abschluss ein Großprojekt.


## ❓ `PeonAiServiceTest`: 8 rote Compact-/TurnContext-Tests (2026-09-15, MITTEL)

**IST:** `PeonAiServiceTest` läuft mit **8 Fehlern** (48 Tests). Betroffen sind
`test_compactDelegatesToPoAgent`, `test_compactMixedRestore_survives`,
`test_turnContext_providesJonFiles`, `test_turnContextSupplier_providesProjectInfoAndAgentsMd`,
`test_compactViaTool_delegatesToAgent` + 3 weitere.

**Fehlerbild:** alle derselbe Typ — `AssertionError` in `AbstractUnitTest.assertHasUserMessageWith`.
Der TurnContext wird nach `compact()` **nicht restored**; erwartete UserMessages fehlen
(`# Test Specifics`, `docs/memory.md`, `order1: be concise`). Setup/Fixture läuft durch, die Tests
scheitern erst an der Assertion.

**Nicht vom Docs-Linter verursacht** (Da Mek, historisch reproduziert): identische 8 Fehler bei
`111f4c3` (vor Inc 1), `5f0a518` (Inc 3), `3b13f02` (Inc 4) und HEAD. Kein Commit dieses Zyklus hat
sie eingeführt; zwischen Inc 3 und Inc 4 gibt es **keinen** Diff in `org.sterl.llmpeon/src` oder
`org.sterl.llmpeon.test/src`. Kein Zusammenhang mit `WriteValidator.DENY_ALL`/`AiReviewAgent`.

**WARUM offen:** Meine Notiz sagt für den Zyklus `story/po-compact-2026-09-13` **Plugin-Suite
203/0 grün**. Entweder ist diese Zahl falsch/veraltet, oder zwischen Zyklusende und heute ist eine
Regression eingelaufen — betroffen wäre ausgerechnet der Compact-Flow, den dieser Zyklus gebaut hat.
Solange das ungeklärt ist, ist der grüne Stand jenes Zyklus nicht belegt.

**Nächster Schritt:** eigener Bug-Zyklus (nicht im Docs-Linter). Erst klären, ob 203/0 je stimmte
(`git bisect` auf `PeonAiServiceTest`), dann Ursache im TurnContext-Restore nach `compact()`.


## ❓ `eclipseReplaceLines`/`diskReplaceLines`: Replace verhält sich sporadisch wie Insert (2026-09-15, HOCH)

**Evidence (Da Mek, Docs-Linter Inc 3):** Aufruf `eclipseReplaceLines(file, line=118, newContent=…)`
→ **alte Zeile 118 blieb stehen**, neuer Content landete ab Zeile 119. Effektiv Insert statt
Replace. Gleiches Verhalten bei `diskReplaceLines` (`TestParser.java`). **Nicht bei jedem Aufruf**
reproduzierbar — bei anderen Dateien im selben Lauf korrekt. Kein Muster erkennbar
(Mehrzeiligkeit? Sonderzeichen? Position?).

**Folge:** Java-Quellen wurden schrittweise korrumpiert („Duplicate local variable"), bis Da Mek
den Überblick verlor; der ganze Inc-3-Stand musste auf `6520377` zurückgesetzt werden
(~30 min Arbeit verloren). Erkannt wurde es spät, weil nach einem Replace nicht zurückgelesen wird —
berechtigtes Vertrauen ins eigene Tool.

**Warum hoch:** Das ist die teuerste Fehlerklasse dieses Codebases — ein Tool, das etwas anderes
tut als es meldet, ohne Fehlermeldung. Verwandt mit der AGENTS-Regel „a tool must never lie".

**Nächster Schritt:** kontrollierte Einzeltests (ein-/mehrzeilig, LF/CRLF, letzte Zeile, Datei mit/
ohne Schluss-Newline, Datei im Editor offen vs. geschlossen). **Verdachtsmoment:** ungespeicherter
Editor-Buffer vs. Datei auf Platte — Da Mek arbeitete an Dateien, die ich parallel offen hatte.

## ❓ ApiRetry: Cancellation-Evidenz-Sammlung (Priorität: hoch, 3. Evidence 2026-09-11)

**Befund-Klassen (alle derselbe Shape: Call bricht, statt dass ApiRetry sichtbar retryt):**
1. **Netzwerk-Failures ohne Retry** (2026-09-10, warning-cleanup): `HttpTimeoutException` +
   `ConnectException` brachen den Call ohne sichtbaren Retry ab — im Gegensatz zu
   HTTP-Status-Fehlern.
2. **Connection reset mitten im SSE-Stream** (2026-09-10, mcp-fixes): `reviewPlanAgent` starb mit
   `ToolExecutionException: closed` ← SSE `Connection reset` (`IOException: chunked transfer
   encoding, state: READING_LENGTH` → `SocketException`).
3. **„AI call canceled while waiting to retry"** (2026-09-02 memory #21; erneut 2026-09-11:
   Da-Mek-Lauf (Branch-Konsolidierung) + Compact-Session starb mittendrin am selben String) —
   Retry-Thread stirbt im Backoff, Meldung endet wie gecancelt.
4. **`IOException: header parser received no bytes`** (2026-09-06): „attempt 1, retrying in 10s"
   gefolgt von Cancel — Verdacht: Null-Byte-IOException wird fälschlich als Cancel klassifiziert
   statt als retry-würdiger API-Fehler.

**Investigations-Fragen:** Retry-Klassifikation für empty-response/Network-Level (Connect/Timeout/
Reset) prüfen — Cancel-Misclassification? Kann ein API-Call den Backoff abbrechen ohne echtes
Cancel? Companion-Story: **Live-Status im Retry-Fenster** (unten).

## ❓ Live-Status im Retry-Backoff-Fenster (2026-09-10)

**IST:** Nach Connection-Abbruch versteckt `StreamingBridge.onError` (END-Chunk) die
Live-Statuszeile, PROBLEM-Nachrichten ebenso → während des ApiRetry-Backoffs (10s…5min):
Funkstille — keine Tokens, kein „working since", User liest es als „hängt".

**SOLL-Idee:** Statuszeile zeigt im Backoff-Fenster den Retry-Zustand („retrying in Xs").
Eigene Mini-Story, nicht Teil von [chat-job-lifecycle.md](chat-job-lifecycle.md).

## ❓ Shell-Tool für Plan-/Review-Agent — Whitelist-Capability? (2026-09-10, User)

Plan-/Review-Agent sollen ggf. `git`/`mvn`/`npm` nutzen — heute ohne Shell-Tool. Optionen: volles
ShellTool, reduziert, oder Whitelist-Capability im ShellTool selbst (analog Write-Validator):
Pattern-Liste per Agent konfigurierbar. **Status: nur Ticket** — „erst fertig werden, dann was
Neues." Offene Fragen: Whitelist pro Agent oder global? Default-Set? Read-only-Filter (push?)?
Verwandt: ADR-0015 (sandbox), ADR-0022 (Write-Path-Allowlist, Proposed). **Evidence 2026-09-13
(User: „sollte er haben"):** Da Dok konnte im Release-Scan die User-Compact-Commits nicht
isolieren (kein Git) — Review-Material musste erst Da Mek aufbereiten. Konkreter Use-Case für
read-only Git beim Review-Agent.

## ⏳ Jackson 2 → 3: beobachten, Migration erst bei voller Entfernbarkeit (User 2026-09-10)

langchain4j 1.20.0 macht Jackson 3 **opt-in** (`langchain4j-jackson3`; Default bleibt Jackson 2).
Nicht heute migrierbar: (1) openai-java pinnt Jackson 2 (nicht unter unserer Kontrolle),
(2) eigene Core-Nutzung in 5 Dateien müsste mit, (3) Error-Path-Änderung
(`JsonReadException` statt Jackson-Exceptions) → ApiRetry-Klassifikation prüfen.
**Entscheidung:** Beobachten — Migration erst, wenn Jackson 2 vollständig entfernbar (auch aus
openai). Revisit-Trigger: langchain4j 1.21+ (Aggregator schon auf 1.21.0-beta31) oder openai-java
Jackson-3-Support. Dann eigene Story mit ADR (Major-Sprung, OSGi-Bundle-ClassPath, Error-Path).

## ❓ buildWithDev sollte Da Mek vorher compacten (2026-09-03)

Vor `buildWithDev` automatisch `compactDev` bei nennenswertem Kontext — die Plan-Datei ist die
Übergabe, nicht der Restkontext. User: „da brauchen wir kein Test". **PO-Empfehlung:** Compact
statt Reset, Schwelle ~50 % Fenster, nur beim Start eines neuen Plans (nicht bei Delta/Nacharbeit
— dort ist der Restkontext die Ersparnis). Eigene kleine Story.

## ⏳ Unbegrenzte Query-Caches (PO-Entscheidung, Rückversicherung offen)

`SearchQuery.CACHE` + `RegexUtils.GLOB_CACHE` sind unbegrenzte `ConcurrentHashMap`s.
**PO-Entscheidung (2026-09-03):** vorerst belassen — Einträge winzig, Session erzeugt Dutzende.
Bei Bedarf: LRU mit Obergrenze (z. B. 500). Rückversicherung mit User steht aus.

## ⏳ Streaming-Timing: Präzisierungen (2026-09-05, streaming-display.md R19)

1. **Total-Timer Stop:** Bridge kennt kein Turn-Ende — Total startet im Konstruktor, läuft durch
   die Bridge-Lebenszeit; Anzeige nutzt `startedAt` aus dem Chunk.
2. **TOOL-Chunk-Value:** R21 zählt gestreamten Text — für TOOL heißt das `partialArguments()`
   (Delta-Slice); der Tool-Name wird nicht gezählt.

## ⏳ Edit-Tools: Naming-Uniformität + gemeinsame Doku (geparkt 2026-09-05)

- Rename auf **"Edit"**: `eclipseUpdateOpenFile` → `eclipseEditOpenFile`, `planUpdate` →
  `planEdit` (Verb-Familie konsistent; "Update" kollidiert mit Write/overwrite).
- Gemeinsame Doku für die 4 Edit-Tools (`FileUtils.applyEdit` = Replace-All + Count + 0 = Fehler):
  neue `edit-tools.md` (PO-Empfehlung) vs kanonisch in disk-file-write-tool.md.
- `planUpdate`/`planEdit` meldet noch **keine Count** — nachziehen.
- **Konflikt offen:** eclipse-workspace-write-file-tool.md sagt noch „Errors if 0 or >1 matches"
  — widerspricht Bug-Hunt #1 (Replace-All + Count). Muss mitfixt werden.
- Nebenbefund: `planUpdate` überreicht dem Monitor Parameter statt Content — Editor-Diff falsch.
- **Line-Ending-Normalisierung (User, 2026-09-05, `file-edit-tools.txt`):** falsches Ending im
  oldString → Tool normalisiert **beide** Strings; anderes Ending im newString → wörtlich
  übernommen, kein Fehler. Ersetzt frühere E3-Entscheidung. E2E-Spec:
  `org.sterl.llmpeon.test/ai-e2e-test/file-edit-tools.txt`.

## ❓ Deferred Smoke-Test-Kosmetik (User: „Kosmetik ist mir erstmal egal")

- Zwei horizontale Striche zwischen Selected-File und Skill-Liste — einer raus.
- Scrollverhalten Advanced Config wirkt komisch.
- Dropdown-Umbau descoped (2026-09-03), Klassen gelöscht (`a1d8d35`, Git-Historie) —
  Wiederaufnahme = eigene Story.

- **⏳ 2026-09-12 — Docs-SOLL-Hygiene-Sweep:** User-Direktive: Docs = reines SOLL, keine
  implementierten Bug-/„war:"-Narrativen (der Plan trägt den Diff SOLL/IST). Umgesetzt für
  advanced-configuration.md + model-loading.md (UI-Zyklus). Offen: Sweep über die übrigen
  Feature-Docs (weitere „war:"-Blöcke, alte IST-Abschnitte) — als eigener Aufwasch, Scope vom
  User bestätigen lassen.

- **⏳ 2026-09-13 — Compressor-Dedup-Subtext-Edge (Da-Dok Release-Scan):** `AiCompressorAgent`-Dedup (`msg.indexOf(txt) < 0`) kann eine Einzel-Nachricht unterschlagen, deren (≤3000-Zeichen-truncated) Text Teiltext einer früheren Nachricht ist. Compact ist ohnehin lossy — bewusst akzeptiert, kein Release-Blocker; Revisit nur bei Kompaktier-Qualitäts-Beschwerden.
- **⏳ 2026-09-13 — ThreadSafeMemory: RAM-only nach Persist-IOException (Da-Dok Release-Scan):** nach Persist-Fehler `store = null` + throw → Session läuft ohne Persistenz weiter (stille Loss NACH dem Fehler; präexistierendes Muster aller append/persist/clear-Pfade). Kein Blocker; Revisit nur bei Datenverlust-Meldungen.

## Compact Input Budget (docs/compact-input-budget.md)

- ❓ Context-Noise zuerst raus (User-Idee, 2026-09-13): Context-Item-Messages (selektierte Files,
  Standing Orders, AGENTS.md in der History) nehmen, bevor gekürzt wird — die echte User-Message
  steht (Text-Content) zuletzt. Wird zusammen mit der **Light-Version** des Budgets ausgearbeitet
  (User: „#2 und #5 gehören zusammen", vor dem Bau nochmal gemeinsam drüber).
- 🔒 R1–R5 SOLL festgelegt, Story ❌ specified (2026-09-13). Reihenfolge: erst Compact-Slot-Bug
  (eigener Zyklus, eigenes Release), dann Budget-Light + Noise.


## Compact-Input: Context-Noise zuerst raus (User-Idee, 2026-09-13 — unterbrochen, nicht ausdiskutiert)

- ❓ Vor dem Kürzen (R3) zuerst Context-Item-Messages aus dem Compact-Input nehmen: selektierte
  Files, Standing Orders, AGENTS.md in der History — „Rauschen", das Compact nicht braucht.
  Die echte User-Message steht (Text-Content) zuletzt. Ausarbeitung + Einordnung in
  compact-input-budget.md (eigene Stufe vor der sanften Kürzung?) offen.


## Neu (2026-09-14, Zyklus story/po-compact-2026-09-13)

| Punkt | Status | Notiz |
|---|---|---|
| `homepage/src/setup/peon-po.md` listet das Team ohne Da Dok | ❓ offen | leicht veraltet, Da-Dok-Fund; Korrektur im nächsten Homepage-Kontakt |
| User-Smoke Header-Compact-Buttons (BDD in agenten-status-im-header.md) | ⏳ teils erledigt | Optik ✅ (User 2026-09-15: „optisch sauber"); ausstehend: Re-Compact-Noop (2 Messages → `Nothing to compact`), Disabled-States, Tooltip — nach Merge |
