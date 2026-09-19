# Open Points

Status je Punkt: ❓ offen · ⏳ selbst entschieden (Rückversicherung mit User steht aus) · 🔒 geklärt.

## ❓ `applyEdit` Not-Found-Fehler dumpet das gesamte File (2026-09-19, Jon — offen, keine Lösung)

`FileUtils.applyEdit` hängt bei „not found" den **kompletten Datei-Inhalt** in die
IllegalArgumentException. **Paul (2026-09-19): bewusst so gebaut** — er beobachtete, dass Modelle
ohne den Inhalt sofort ein zweites Read nachschieben (zusätzlicher Tool-Roundtrip, auch teuer);
der Dump spart den Roundtrip genau im Fehlerfall. Nach dem alten String suchen geht nicht — der
Anker liegt ja daneben. **Offen:** beide Seiten sind schlecht (Roundtrip vs. Context-Bombe bei
großen Dateien); eine gute Lösung (z. B. Kontext-Fenster um die ähnlichste Fundstelle, modell-
oder größenabhängig) existiert noch nicht. Bleibt stehen, kein Bau.

## 🔒 Self-Reference-Guard bewusst NICHT übernommen (2026-09-19, Paul bestätigt)

Der Self-Ref-Guard aus `bugfix/edit-tool-insert` (`a3e8ce1`: `newStr.contains(oldStr) &&
content.contains(newStr)` → IAE) war nie gemerged und wurde im `FileUtils`-Umbau bewusst nicht
übernommen. Begründung: Das Selbstwachstum (`abc` → `abcd` → …, jeder Call wächst um
`Treffer × Anhang`) ist **explizite Agenten-Absicht** — jeder Call ist ein korrekt formulierter
Edit mit gültigem Anker; der Tool-Output meldet ehrlich „replaced N occurrence(s)". Der Guard
dagegen blockierte legitime Edits im Normalfall (Anker ist fast immer Präfix des Neuen, z. B.
`foo()` → `foo(); // erledigt`). Der **Edit-Guard** (min. 3 Non-WS-Zeichen, `7800a56`) deckt die
teure Korruptions-Klasse ab. **Falls wir ihn wieder brauchen: er liegt fertig auf
`bugfix/edit-tool-insert` (`FileUtils.applyEdit`, Commit `a3e8ce1`).**

## 🔒 SimpleDiff-Bremse: LCS-Diff kippt bei großen Dateien — GELÖST (2026-09-16)

`eclipseEditFile` → `AIChatView.onFileUpdate:329` → `SimpleDiff.unifiedDiff:21` → `lcsDiff:97` →
`OutOfMemoryError: Java heap space` (gesamter Agenten-Loop gestorben). `lcsDiff` allokiert
`int[m+1][n+1]` ≈ 4·m·n Bytes (`SimpleDiff.java:91-101`), synchron auf dem Tool-Thread — ein OOM
dort kippt den ganzen Eclipse-Prozess. Heap 4 GB → Kipppunkt ≈ 6e8 Zellen. Trigger war eine
**in-memory aufgeblähte** Altdatei (7,3 Mio. Zeilen, siehe ReplaceLines-Evidence unten) × 332
neue Zeilen ≈ 9,7 GB. Ein normales Doc (300×300 ≈ 360 KB) ist sicher.

**SOLL (Vorschlag):** Guard in `SimpleDiff.unifiedDiff` — wenn `m·n` über Schranke (z. B. 5e6
Zellen ≈ 20 MB), kein LCS: summarische Meldung („file updated, N→M lines changed") statt Diff. Kein
Rate-Parsing, kein Verhalten Risiko — nur die Anzeige degeneriert kontrolliert.

**Gelöst (2026-09-16, `2e51a16`):** Guard `MAX_LCS_CELLS = 5_000_000` in `SimpleDiff.unifiedDiff`
(Choke-Point, kein `catch OOM`); darüber human-readable Summary mit beiden Zeilenzahlen + Schranke
(„a tool must never lie"). `AIChatView.onFileUpdate` zeigt Summary als TOOL-Message. Async/Job-Umzug
bewusst descoped (Diff läuft bereits auf dem Tool-Thread; Job-Umzug erzeugt Chat-Reihenfolge-
Probleme). 4 Guard-Tests in `SimpleDiffTest` (unter/an/über Schranke + Crash-Shape als 200k×100).

## 🔒 User-Context-Selection-Regression — GELÖST (2026-09-16, User-Smoke steht aus)

Paul meldete: selektierter Text fehlt im Kontext + Statuszeile (Regression ggü. vorletztem Release).
Diagnose: `0a998ec` — Nicht-Text-Selektions-Events räumen die Editor-Selektion weg
(`AIChatView.java:255` + Clear-Logik `UserContext.java:157-160`). **Live-Beweis (2026-09-16,
Pauls Paste):** Selektion in README.md kommt als Snippet an, aber mit „selected content not in a
file." — die **Ressource** wurde vom Event weggeräumt, der Text überlebte halb. SOLL steht in
[user-context.md](user-context.md) (R-SEL-1 bis R-SEL-3, inkl. Pauls Rendering-Entscheid: Snippet +
Dateipfad statt komplettem Dateiinhalt, Homepage-Text bleibt SOT). Fix-Zyklus: Branch
`bugfix/user-context-selection` (von main), roter Test für die Event-Sequenz (inkl. Ressource),
dann Fix, Review.

**Gelöst (2026-09-16, `5ceb3aa` + Review-Fixes `c958d78`):** R-SEL-1…3 gebaut, Review CONCERNS →
abgenommen (Plugin 216/0, Core 866/0, Linter UC 3/3). Mutation-Note: die Setter-Reihenfolge in
`applyTextSelection` (Resource vor Selektion) fangen Headless-Tests nicht — Abdeckung durch den
User-Smoke (bei falscher Reihenfolge ist die Selektion sofort sichtbar weg). Rest: siehe
[user-context.md](user-context.md).

## ❓ Docs-Linter-Tool: `idPattern`-Parameter verifizieren (2026-09-16)

Da Dok meldete im Review: `lintDocsAndTests` mit explizitem `idPattern UC-SEL-\d+` liefere 0/0.
Jon-Verifikation am Disk-Root (`/Users/sterlp/dev/workset/peon-ai`): dasselbe Pattern liefert
korrekt 3/3 UCs + 5/3 Test-IDs — das Pattern selbst funktioniert.

**Entscheidung (Paul, 2026-09-16):** Die Wurzel ist der Root, nicht das Pattern — workspace-
qualifizierte Pfade (`/llmpeon-parent`) scheitern still. → **R-DL-18** (Root-Fallback: wie gegeben
probiert, dann ohne führenden `/` gegen das `workingDir`, sonst Fehler mit beiden Pfaden), Umsetzung
im Mini-Zyklus `bugfix/linter-root-fallback`. Die 0/0-Evidenz von Da Dok passt zu diesem Mechanismus;
die Verifikation erledigt sich mit dem Fix.

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


## 🔒 `PeonAiServiceTest`: 8 rote Compact-/TurnContext-Tests — GELÖST (2026-09-16): keine Bugs, veraltete Tests

**Auflösung (Da Mek, Worktree-Beweise):** `mvn clean verify` bricht mit 8 Failures in
`PeonAiServiceTest` — **kein Produktionscode-Bug**. Ursache: der R16-Guard (`AbstractAgent.compact`,
`memory.size() < 3`) skippt korrekt, weil genau diese 8 Tests nur **2 Messages** seeden und
`compact()` lediglich als Mechanismus für die Turn-Context-Re-Injection nutzen. Kausalitätsbeweis:
`9ed839b` (vor R16) → Plugin-Suite 202/0 grün; `d94e8e9`+ → dieselben 8 rot. Pauls Docs-Linter-Commit
`d93b1d1` ist unschuldig.

**Die frühere Notiz hier („zyklusfremd, schon vor Inc 1 rot", Da Mek 2026-09-15) war falsch** —
wahrscheinlich stale `bin/`-Klassen ohne `eclipseBuildProject` (Memory-Regel #16). Die realen
Lehren: (1) der R16-Gate lief nur Core-Surefire, nie die Plugin-Testsuite — das Modulgrenzen-Loch
aus Memory-Regel #33, diesmal im anderen Modul; (2) Schwellenwert-Änderungen → Seed-Grep über
**beide** Testmodule.

**Fix:** Da Mek hat die 8 Seeds auf 3 Messages umgestellt (1U+2A — 2×U geht nicht,
`ThreadSafeMemory.add` mergt aufeinanderfolgende User-Messages). Core-Surefire 862/0. Plugin-Suite
läuft noch (Trust-Dialog); Commit nach grünem Lauf.


## 🔒 `eclipseReplaceLines`/`diskReplaceLines`: Replace verhält sich sporadisch wie Insert — GELÖST (2026-09-19)

**Auflösung (Zyklus `bugfix/user-context-selection`, Inc 1):** Die Korruptionsklasse hatte zwei
Mechanismen, beide jetzt geschlossen: (1) **Primär-Ursache** — leerer/blanker `oldString` in
`eclipseEditFile` wurde still zu `""` coalesced (`String.replace("", x)` fügt an jeder Position
ein; „replaced 0 occurrence(s)" log über das echte Verhalten). Pauls Hypothese, code-bestätigt →
**Edit-Guard** gebaut (`7800a56`: oldString Pflicht, `trim().length() >= 3`, up-front in
`FileUtils.applyEdit`, alle 3 Oberflächen). (2) Self-Reference-Wachstum — durch denselben Guard
praktisch ausgeschlossen (Anker ≥ 3 Non-WS-Zeichen). Die Stress-Jagden (1000+10000 Headless-
Iterationen, 8-Zellen-UI-Matrix) fanden **kein** sporadisches Reprodukt in `replaceLines` selbst —
die beobachteten Vorfälle passen zum empty-oldString-Mechanismus. Die falsch getesteten
Last-Loop-/UI-Harness-Tests existieren nicht mehr; Mutation-Nachweise: 5× rot im Guard.
Rest-Risiko: der veraltete `IllegalStateException`-Wrapper war die letzte Ablenkung und ist
umbenannt (`73bb156`).

**Evidence (Da Mek, Docs-Linter Inc 3):** Aufruf `eclipseReplaceLines(file, line=118, newContent=…)`
→ **alte Zeile 118 blieb stehen**, neuer Content landete ab Zeile 119. Effektiv Insert statt
Replace. Gleiches Verhalten bei `diskReplaceLines` (`TestParser.java`). **Nicht bei jedem Aufruf**
reproduzierbar — bei anderen Dateien im selben Lauf korrekt. Kein Muster erkennbar
(Mehrzeiligkeit? Sonderzeichen? Position?).

**Neue Evidence (2026-09-16, OOM-Crash) — Verdacht erhält ein Live-Reprodukt:** Der In-Memory-
Zustand von `docs/open-points.md` in Eclipse wuchs auf **~7,3 Mio. Zeilen** an (21997 duplizierte
Blöcke: Header + Statuszeile + Inhalt wiederholten sich ~22k×, `# Open Points` in derselben Zeile
wie der vorangehende Sektionstitel — Insert statt Replace als Wiederholform), während die **Disk-
Datei unversehrt blieb** (332 Zeilen, 22K, `git` unmodified == HEAD). Eclipse-Reads (File/Docs-Grep)
zeigten die Duplikate, Disk-Grep dasselbe File sauber. Der SimpleDiff-OOM oben ist Folge, nicht
Ursache. Das stützt die „Replace-als-Insert"-Korruptionsklasse: sie lebt im In-Memory-/Editor-Pfad,
nicht auf Disk.

**Folge:** Java-Quellen wurden schrittweise korrumpiert („Duplicate local variable"), bis Da Mek
den Überblick verlor; der ganze Inc-3-Stand musste auf `6520377` zurückgesetzt werden
(~30 min Arbeit verloren). Erkannt wurde es spät, weil nach einem Replace nicht zurückgelesen wird —
berechtigtes Vertrauen ins eigene Tool.

**Warum hoch:** Das ist die teuerste Fehlerklasse dieses Codebases — ein Tool, das etwas anderes
tut als es meldet, ohne Fehlermeldung. Verwandt mit der AGENTS-Regel „a tool must never lie".

**Nächster Schritt:** kontrollierte Einzeltests (ein-/mehrzeilig, LF/CRLF, letzte Zeile, Datei mit/
ohne Schluss-Newline, Datei im Editor offen vs. geschlossen). **Verdachtsmoment:** ungespeicherter
Editor-Buffer vs. Datei auf Platte — Da Mek arbeitete an Dateien, die ich parallel offen hatte.
**Neu (2026-09-16):** Konkurrenzthese — die Korruption entsteht, wenn ein Tool-Write (Write vor
Diff, `EclipseWorkspaceWriteFileTool.java:133-134`) auf ein File trifft, dessen In-Memory-Modell
bereits divergiert; der Crash-Stack belegt Write-then-Diff. Die Repro-Sequenz-Aufklärung gehört in den
Tool-Bug-Zyklus.

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
| Namen im System-Prompt: Scope über Jons Team hinaus? | 🔒 geklärt (2026-09-16, Paul) | **Nur Jons Team** (R-N1). Top-Level-Peon-Agents / Custom Agents / Da Sniffa bleiben außen vor — Wiederaufnahme nur auf expliziten Wunsch. |
| BDD-Test für Compact-Hint-Fallback (Agenten ohne CompactSessionTool) | ❓ offen | Regel gebaut (`29a341b`), dokumentiert in context-message-concept.md. Paul 2026-09-16: „machen wir wann anders" — Backlog. |

## ⏳ R-SEL-4 Umsetzungsdetails (Jon, 2026-09-16 Abend — Rückversicherung steht aus)

Paul hat R-SEL-4 bestätigt („beides": `IClassFile` **und** `IType`; Typ-Event ersetzt File+Text).
Drei Detail-Entscheidungen habe ich selbst getroffen (aus Pauls „es bleibt bis wir wieder was neues
selektieren" abgeleitet, im Plan §2.1/§6 von Da Thinka vorgeschlagen):

1. **Jeder** `setTextSelection`-Aufruf (auch leer/Caret) räumt den Typ — strikte Text/Typ-
   Alternation. Passend zu Pauls Regel (Caret-Klick = neue Selektion) und zur R-SEL-1-Formulierung
   („inkl. leerem/Caret-Event").
2. **Rename** `clazz`/`setClassFile` → `javaType`/`setJavaType` (Feld `IJavaElement`) — `IType` ist
   kein `IClassFile`, technischer Name folgt der Rolle (memory #15).
3. **Typ-Event berührt `currentProject` nicht** (kein `updateSelectedProject`) — Project-State
   bleibt dem Projekt-/Pin-Flow vorbehalten.

Wenn Paul widerspricht: Verhalten zurückändern, Tests (UC-SEL-4) entsprechend anpassen.
