---
idPrefix: DL
---

# Docs-Linter — Use-Case-IDs gegen Testbaum abgleichen

> **Status:** ✅ done (2026-09-15) — R-DL-1…16 gebaut, drei Reviews bestanden. Nachzyklen:
> `reportPath` gestrichen (echt read-only), Tool-Split `DocsIdTool`/`DocsLinterTool`, raus aus dem
> Disk-Gate, Tool-Matrix je Agent getestet; 2026-09-19 R-DL-18…20; 2026-09-21 R-DL-21/22
> (Textblock-Belege, manuell-Marker).
> **Ziel:** Ein falsches `✅` maschinell unmöglich machen: jeder als erledigt markierte Use-Case
> muss durch einen Test belegt sein, der die ID des Use-Case trägt.

## Warum (Problem)

Der Statusmarker (`🚧 in design` · `❌ specified` · `✅ done`) ist die zentrale Steuergröße des
PO-Workflows. Belegt wird `✅` heute durch einen Verweis `→ Klasse.methode` im Doc. Dieser Verweis
**driftet**: Rename, Paketumzug, Testklassen-Split brechen ihn lautlos. Ein falsches `✅` ist teurer
als ein `❌` — beim `❌` weiß jeder, dass etwas fehlt; beim falschen `✅` schaut nie wieder jemand hin.

Drei Fehlerarten, zwei davon maschinell findbar:

| # | Fehlerart | maschinell findbar |
|---|---|---|
| 1 | Verweis zeigt auf die falsche Testklasse (Test existiert, aber woanders) | ja |
| 2 | Verweis zeigt auf einen Test, der nie existiert hat | ja |
| 3 | Test existiert und ist grün, belegt die Aussage aber nicht | **nein** — bleibt Handarbeit |

**Verworfen — reiner Skill-Prompt:** Der Kern ist ein Mengenabgleich zweier Listen mit je mehreren
hundert Einträgen. Genau dort übersieht ein LLM Einträge *und merkt es nicht*. Das Werkzeug muss
**deterministischer Code** sein; der Agent ruft es auf und liest den Report.

**Verworfen — Verweis `→ Klasse.methode` greppen:** Klasse+Methode ist ein **Pfad** und bricht bei
jedem legitimen Refactoring → Dauer-Fehlalarm → wird ignoriert. Eine **ID ist ein Name** und
überlebt Rename/Move/Split, solange der Kommentar mitwandert. Der Dev-Agent wird nicht eingeschränkt.

## Das Pattern

**Im Doc** (Definition = Überschrift):

```markdown
### R-READTOOLS-4 — Grep meldet immer den Suchmodus ❌ specified
#### UC-READTOOLS-7 — Gültiges Regex wird als Regex gesucht ✅ done     ← Definition
… wie in UC-READTOOLS-7 beschrieben …                                  ← Referenz (zählt nicht)
```

**ID-Schema (verabschiedet 2026-09-15 mit dem User) — zwei Ebenen, beide flach:**

| Ebene | Format | Beispiel |
|---|---|---|
| Regel | `R-<FEATURE>-<n>` | `R-READTOOLS-4` |
| Use-Case | `UC-<FEATURE>-<n>` | `UC-READTOOLS-7` |

`<FEATURE>` = kurzes Großbuchstaben-Kürzel des Features (`READTOOLS`, `MCP`, `PO`). **Kein
Dateiname in der ID** — sonst bricht sie beim Doc-Split. **Keine Regel-Nummer in der UC-ID** —
sonst bricht sie beim Umhängen eines Use-Case unter eine andere Regel; beides wäre wieder ein Pfad.
Die Regelbindung liest der Linter aus der Doc-Struktur (braucht er für R-DL-3 ohnehin).
Keine Registry-Datei: **die Docs sind die Registry**, `nextIds` liest sie.

Default-Regex: `\bUC-[A-Z]+-\d+(?:-\d+[a-z]?)*\b` — matcht flach (`UC-READTOOLS-7`) **und**
hierarchisch (`UC-FEATURE_NAME-3-3`, Erstanwender). `idPattern` ist damit **Pflicht-Parameter, kein
Nice-to-have**: der Request-Regex mit `+` würde unser flaches Schema still nicht finden — exakt der
Null-Treffer-Fehlerzustand aus R-DL-6. Der Parameter FULL-matcht die ID (`matches()`, kein
Präfix-Match) — `UC-PP-\d+` findet `UC-PP-12`, aber nicht `UC-PP-12-3a`; Werkzeuge, die das
Gegenteil erwarten, erzeugen genau den stillen 0/0 (zweimal passiert: 2026-09-16 + Da-Dok/Da-Mek).

### Doc-Template (verbindlich — der Parser ist nur so gut wie das Template)

```markdown
---
idPrefix: READTOOLS
---

# Read Tools — Titel des Features

### R-READTOOLS-4 — Grep meldet immer den Suchmodus ❌ specified

Freitext: was gilt und warum.

#### UC-READTOOLS-7 — Gültiges Regex wird als Regex gesucht ✅ done
GIVEN eine gültige Regex-Query
WHEN grep läuft
THEN nennt das Ergebnis `regex search`
```

Drei harte Struktur-Regeln, alles andere ist für den Parser unsichtbarer Fließtext:

1. **ID am Anfang** der Überschrift (direkt nach den `#`).
2. **Statusmarker am Ende** derselben Überschriftenzeile.
3. **UC-Überschrift genau eine Ebene tiefer** als ihre Regel — daraus leitet sich die Zugehörigkeit
   und damit die Status-Vererbung (R-DL-3) ab.

> **Autor-Konvention (2026-09-22, Da-Dok-Fund):** Regel-Überschrift `### R-<FEATURE>-<n>`, UC
> `#### UC-<FEATURE>-<n>`. Ein neues Feature-Doc schreibt die Regeln von Anfang an auf `###` —
> ein `##` erzeugt `UC_OHNE_REGEL` (Fall: tool-time-disclosure.md, Erstfassung).

Das **Frontmatter-Feld `idPrefix`** deklariert das Feature-Kürzel **explizit**, statt es aus
vorhandenen IDs abzuleiten. Begründung: ein frisches Doc ohne Regeln hätte sonst kein Kürzel, und
ein Tippfehler (`READTOOL` statt `READTOOLS`) sähe aus wie ein neues Feature — unsichtbar. Mit
Deklaration wird er zum Befund (R-DL-9). Explizit schlägt abgeleitet, genau wie bei der ID selbst.

**Warum Frontmatter statt Blockquote im Text:** Das Präfix ist ein Metadatum *über* das Doc, keine
Aussage *im* Doc — im Fließtext stünde es neben inhaltlichen Blockquotes, mit denen es nichts zu tun
hat. Der Linter braucht dafür **keinen YAML-Parser**: er liest den Block zwischen den ersten beiden
`---`-Zeilen und sucht die Zeile `idPrefix:`. Nebeneffekt: Frontmatter ist ohnehin das Format, das
eine spätere HTML-Veröffentlichung erwartet ([blume-publishing.md](blume-publishing.md)).

**Im Test** (Zeilenkommentar unmittelbar über dem Test, mehrere IDs kommagetrennt):

```java
// UC-READTOOLS-7, UC-READTOOLS-8
@Test
void shouldUseRegexForValidPattern() { … }
```

```ts
// UC-READTOOLS-9
it('falls back to literal search', () => { … });
```

Bewusst **Kommentar statt Annotation**: keine Abhängigkeit, sprachneutral. Erkannte Präfixe: `//`,
`#`, `--`. Der Beleg ist die **ID-Zeile selbst** — es muss kein Funktionskopf erkannt werden
(R-DL-4), sonst entstünden stille Lücken bei jeder Testsyntax, die wir nicht vorhergesehen haben.

**Kardinalität** (verbindlich):

| Richtung | Regel | Verstoß |
|---|---|---|
| UC → Test | 1..n | UC ohne Test = Fehler |
| Test → UC | 0..n | ID am Test ohne UC-Definition = Fehler |

Keine 1:1-Pflicht (sonst müssten parametrisierte Tests künstlich zerschnitten werden). Ein Test
ganz ohne ID ist **kein** Befund — technische/Fixture-/Architekturtests belegen keinen Use-Case.
Das Werkzeug findet Lücken, es erzwingt keine Struktur.

## Methoden (Tool-Oberfläche, Split verabschiedet 2026-09-15)

**Verantwortung trennt die Tools: vergeben darf einer, prüfen dürfen alle.**

| Methode | Braucht Testbaum | Zweck |
|---|---|---|
| `nextIds` | nein | **Vergabe**: je Feature-Kürzel die nächste freie `R-`/`UC-`-Nummer + belegte Kürzel |
| `lintDocs` | **nein** | **Prüfung**: nur Docs — Doppel-IDs, UC ohne Regel, Status-Vererbung; schnell, auch während des Schreibens |
| `lintDocsAndTests` | ja | **Prüfung**: voller Abgleich, alle Befundarten (R-DL-5) |
| `suggestIds` | ja | Migrationshilfe: `→ Klasse.methode` → Vorschlag für den ID-Kommentar — *eigener Zyklus, Q2* |

**Die Logik liegt genau einmal vor**; wer welche Methode sieht, entscheiden drei schlanke Fassaden
darüber (alle im Package `org.sterl.llmpeon.docslinter`):

| Fassade | exponiert | Wer bekommt sie |
|---|---|---|
| `DocsIdTool` | `nextIds` | **nur Jon** |
| `DocsLinterTool` | `lintDocs`, `lintDocsAndTests` | Jon · Da Thinka · Da Mek · Da Dok |

Beide teilen **eine** Kern-Instanz und damit **ein** `workingDir` — zwei Instanzen mit
auseinanderlaufendem Root wären der stille Ausfall aus R-DL-6 („falscher Pfad sieht aus wie sauberer
Bestand"), nur eine Ebene höher. Muster und Begründung wie bei `PlanReadTool` über `PlanTool`
([ADR-0048](adr/0048-docs-linter-read-only-and-tool-split.md)).

> **Warum Da Thinka auch `lintDocsAndTests` bekommt** (Korrektur 2026-09-15): Eine frühere Fassung
> gab ihm nur `lintDocs` über eine dritte Fassade. Das scheiterte an `ToolService`, der je
> Methodenname genau **einen** Executor zulässt (`ToolService.java:47`) — zwei Fassaden mit
> `lintDocs` im selben Service kollidieren. Die Alternative wäre ein Overlay-`ToolService` gewesen:
> ein Architektur-Umbau, um einem Agenten einen Call vorzuenthalten, den er schlicht *nicht
> braucht*. **Die Trennung, die zählt, ist `nextIds`** — „vergeben darf einer, prüfen dürfen alle".
> Prüfen darf Da Thinka.

`nextIds` ist nicht Komfort, sondern das strukturelle Gegenmittel gegen `DOPPELT_DEFINIERT`:
Doppelvergabe entsteht, weil niemand weiß, welche Nummer frei ist.

**Scope dieses Zyklus: Methoden 1–3.** `suggestIds` ist bewusst ausgelagert (Q2) — sie ist die
einzige Methode, die **heuristisch rät** statt Mengen abzugleichen, und braucht deshalb eine eigene
Bewertung der Vorschlagsgüte. Ihre Regeln (R-DL-*) werden erst in ihrem Zyklus geschrieben.

## Business Rules

### R-DL-1 — Ein Root mit Default, alles andere relativ ✅ done

| Parameter | Default | Bedeutung |
|---|---|---|
| `root` | **Disk-Pfad des gewählten Projekts** | Wurzel; deckt im Multi-Modul-Repo alle Module ab |
| `docRoots` | `docs` | n Verzeichnisse **relativ zu `root`**, rekursiv `**/*.md` |
| `testRoots` | `.` | n Verzeichnisse relativ zu `root`, **ohne die `docRoots`** |
| `testGlobs` | Code-Extensions aus `TextFileTypes` | **Liste**, z.B. `["**/*.ts", "**/*.tsx", "**/*.go"]` |
| `idPattern` | s.o. | konfigurierbar — **Pflicht**, weil flach ≠ hierarchisch |

**Nur Dateisystem-Zugriff, keine zweite Tool-Familie.** Der Projekt-Root ist dem Agenten aus der
Projektliste ohnehin bekannt; über den Disk-Pfad des gewählten Projekts ist workspace-relativ und
disk-relativ **dasselbe Verzeichnis**. Ein zusätzliches `eclipse*`-Gegenstück hätte dieselbe Dateien
gelesen — die `eclipse*`/`disk*`-Symmetrie aus [AGENTS.md](../AGENTS.md) greift hier nicht, weil es
keine zwei Verhalten gibt, nur zwei Wege zum selben Byte. Siehe [ADR-0047](adr/0047-docs-linter-disk-only-single-root.md).

#### UC-DL-1 — Default-Roots ohne jede Angabe ✅ done
GIVEN nur `root` gesetzt WHEN der Linter läuft THEN werden `root/docs` als Doc-Quelle und der
gesamte Baum unter `root` — ohne `root/docs` — nach den Default-Code-Extensions als Testquellen
eingelesen.

#### UC-DL-2 — Mehrere Doc- und Test-Wurzeln ✅ done
GIVEN zwei docRoots und zwei testRoots WHEN der Linter läuft THEN werden alle vier Bäume rekursiv
eingelesen und in einem Report zusammengeführt.

#### UC-DL-3 — Fremde Sprache über testGlobs ✅ done
GIVEN `testGlobs=["**/*.ts"]` WHEN der Linter läuft THEN werden nur `.ts`-Dateien als Testquellen
gelesen.

#### UC-DL-37 — Mehrere Sprachen in einem Lauf ✅ done
GIVEN `testGlobs=["**/*.ts", "**/*.tsx", "**/*.go"]` WHEN der Linter läuft THEN werden alle drei
Dateiarten in **einem** Lauf erfasst und dedupliziert.

> **WEIL** Liste statt Brace-Expansion: `RegexUtils.globToPattern` kennt ausschließlich `*` als
> Sonderzeichen — `**/*.{ts,tsx}` würde literal gematcht und still nichts finden. `RegexUtils` steht
> unter „Explizit unverändert" (ADR-0047-Umfeld), also lösen wir es auf der Aufruferseite.

#### UC-DL-38 — Default-Testtypen kommen aus `TextFileTypes` ✅ done
GIVEN kein `testGlobs` WHEN der Linter läuft THEN werden die **Code-Extensions** aus
`shared/TextFileTypes.java` als Testquellen gelesen (nicht nur `.java`) — **ohne** die reinen
Text-/Daten-Typen `md`, `txt`, `json`, `xml`, `csv`, `properties`, `cfg`, `ini`, `toml`, `yaml`,
`yml`.

> **WEIL:** Die Liste existiert bereits und wird von beiden Grep-Familien genutzt — ein Linter mit
> eigener Parallelliste wäre „one behaviour, two implementations". **Markdown muss draußen bleiben:**
> in `.md` ist `#` ein Überschriften-Zeichen, `# UC-DL-1` würde sonst als Test-Beleg gelesen. Aus
> demselben Grund werden die `docRoots` vom Test-Scan ausgeschlossen (UC-DL-39).

#### UC-DL-39 — Docs sind keine Testquellen ✅ done
GIVEN `testRoots=["."]` (Default) und `docRoots=["docs"]` WHEN der Linter läuft THEN werden Dateien
unterhalb der `docRoots` **nicht** als Testquellen gelesen — eine UC-Definition zählt nie als ihr
eigener Beleg.

### R-DL-12 — Dateisystem-Stand wird offengelegt ✅ done

Der Linter liest das **Dateisystem**, nicht die Eclipse-Editor-Buffer. Eine Datei mit ungespeicherten
Änderungen wird im alten Stand gelesen — der Linter meldete dann Befunde, die es nicht mehr gibt,
oder übersähe neue. Das ist kein Grund für ein Eclipse-Tool, sondern für **Offenlegung**, analog zu
`showing N of M` bei den Read-Tools („a tool must never lie about a limit").

> Hieß bis 2026-09-15 `R-DL-1b`. Suffix-Nummern widersprechen dem flachen ID-Schema dieses Docs
> (eine ID ist ein Name, keine Hierarchie) — umbenannt, solange die Regel noch nicht gebaut ist.

#### UC-DL-4 — Ergebnis nennt die Lesequelle ✅ done
GIVEN ein beliebiger Lauf WHEN der Tool-Call zurückkehrt THEN steht **im Rückgabewert**, dass vom
Dateisystem gelesen wurde und ungespeicherte Editor-Änderungen nicht enthalten sind.

> **Achtung beim Bau (Regress-Gefahr):** Dieser Hinweis stand bis 2026-09-15 **nur** im
> Markdown-Report, nicht in der Summary. Mit dem Wegfall des Datei-Reports (R-DL-7) muss er in den
> Rückgabewert wandern, sonst löscht der Clean Break still eine bestehende Regel. Status deshalb
> zurück auf ❌.

### R-DL-2 — Definition ≠ Referenz ✅ done

Nur ein ID-Vorkommen in einer **Markdown-Überschrift** (`#`-Zeile) ist eine Definition; jedes
Vorkommen im Fließtext ist eine Referenz und erzeugt keinen Eintrag.

#### UC-DL-5 — Querverweis im Fließtext ist keine Definition ✅ done
GIVEN eine ID in einer `####`-Überschrift und dreimal im Fließtext WHEN eingelesen wird THEN
existiert genau **eine** Definition und `DOPPELT_DEFINIERT` feuert nicht.

#### UC-DL-6 — Dieselbe ID in zwei Überschriften ✅ done
GIVEN dieselbe ID als Überschrift in zwei verschiedenen Docs WHEN eingelesen wird THEN Befund
`DOPPELT_DEFINIERT` mit **beiden** Fundstellen als `datei:zeile`.

### R-DL-13 — Code-Blöcke sind zitierter Text, keine Definitionen ✅ done

Zeilen innerhalb eines **Fenced Code Block** (` ``` ` oder `~~~`, mit oder ohne Sprachangabe) sind
zitierter Text. Sie erzeugen **weder** eine Definition **noch** eine Präfix-Deklaration — auch dann
nicht, wenn sie syntaktisch wie eine Überschrift aussehen. Der Block endet am schließenden Zaun
gleicher Art; ein unterschiedlicher Zauntyp innerhalb eines Blocks schließt ihn nicht.

> **WEIL** — Dogfooding-Befund vom 2026-09-15, gefunden beim allerersten Lauf gegen das eigene Repo:
> `docs/docs-linter.md` erklärt das Doc-Format und zeigt dazu Beispiel-Überschriften in
> ` ```markdown `-Blöcken. Der Linter las sie als echte Definitionen und meldete
> `DOPPELT_DEFINIERT` + zweimal `PRAEFIX_FREMD` für `R-READTOOLS-4` — drei Falschbefunde aus reinen
> Illustrationen. Betroffen ist jedes Doc, das Doc-Struktur dokumentiert: Templates, Meta-Docs,
> dieses hier. Das ist ein False **Positive**, nicht die teure False-Negative-Klasse — aber es
> erzeugt Rauschen genau dort, wo das Werkzeug Vertrauen aufbauen muss. R-DL-2 trennt bislang nur
> Überschrift von Fließtext; der Code-Block ist die dritte Kategorie, die niemand bedacht hat.

#### UC-DL-42 — Überschrift im Code-Block ist keine Definition ✅ done
GIVEN ein Doc mit `#### UC-XY-1` einmal als echte Überschrift und einmal innerhalb eines
` ```markdown `-Blocks WHEN eingelesen wird THEN existiert genau **eine** Definition und
`DOPPELT_DEFINIERT` feuert nicht.

#### UC-DL-43 — Präfix-Deklaration im Code-Block zählt nicht ✅ done
GIVEN ein Doc, dessen Code-Block eine Überschrift mit fremdem Präfix zeigt WHEN eingelesen wird
THEN entsteht **kein** `PRAEFIX_FREMD` — der Block ist Illustration, keine Deklaration.

**Gilt nur für Docs, nicht für Testquellen.** Der Testbaum wird sprachneutral gelesen; ein
ID-Kommentar in einem Java-Textblock oder Python-Docstring als „zitiert" zu erkennen, erforderte
Sprach-Parsing — genau die Rateübung, die R-DL-4 verworfen hat. Offen als eigener Punkt, siehe
[open-points.md](open-points.md).

### R-DL-3 — Statusmarker erbt von der Regel ✅ done

Zur Definition gehört ihr Marker (`🚧` `❌` `✅`). Steht in der UC-Überschrift keiner, gilt der der
übergeordneten Regel-Überschrift (`### R-…`).

#### UC-DL-7 — Marker der Regel vererbt sich ✅ done
GIVEN `### R-READTOOLS-4 … ✅` mit `#### UC-READTOOLS-7` ohne Marker WHEN eingelesen wird THEN gilt
der Use-Case als `✅`.

#### UC-DL-8 — Eigener Marker schlägt den geerbten ✅ done
GIVEN eine UC-Überschrift mit eigenem `❌` unter einer Regel mit `✅` WHEN eingelesen wird THEN
gewinnt der Marker des Use-Case.

### R-DL-4 — Die ID-Zeile ist der Beleg, nicht die Methode ✅ done

Erfasst wird jede Zeile, die **ausschließlich** aus einem Kommentar mit einer oder mehreren IDs
besteht — mit ID, Datei und Zeile. Die Einschränkung „ausschließlich" verhindert Treffer in
Fließtext-Kommentaren und Javadoc.

Kommentar-Präfixe: **`//`** (Java, TS/JS, Go, C-Familie, Kotlin, Rust, …), **`#`** (Python, Ruby,
Shell, …), **`--`** (SQL, Lua, Haskell).

**Kein Methodenkopf-Zwang (Korrektur 2026-09-15).** Ein gefundener Funktionsname wandert als
*Kontext* in den Report, ist aber **keine Bedingung** für den Beleg.

> **WEIL** — die ursprüngliche Fassung verlangte einen erkannten Funktionskopf nach der ID-Zeile.
> Damit hing die Sprachneutralität an einer Liste geratener Syntaxen, und `it('should …', () => {`
> — die mit Abstand häufigste JS/TS-Testform — fiel durch. Folge: Der Beleg verfiel **still**, der
> Use-Case galt als unbelegt, und ein korrekt getesteter `✅`-UC erschien als
> `UNBELEGT_ERLEDIGT`. Also exakt die Falschmeldung, gegen die dieses Werkzeug gebaut wird, nur in
> die andere Richtung — ein False Negative, der teuerste Bugtyp dieses Codebases
> ([AGENTS.md](../AGENTS.md): „a tool must never lie"). Wir können nicht alle Testsyntaxen der Welt
> raten; jeder Rateversuch erzeugt stille Lücken. `testRoots` + `testGlobs` begrenzen den Scope
> bereits. **Bewusst in Kauf genommen:** eine ID-Zeile über etwas, das kein Test ist, zählt als
> Beleg — im Einklang mit „Das Werkzeug findet Lücken, es erzwingt keine Struktur."

#### UC-DL-9 — Mehrere IDs an einer Zeile ✅ done
GIVEN `// UC-DL-9, UC-DL-10` über einer Testmethode WHEN eingelesen wird THEN sind beide IDs als
Beleg erfasst, mit Datei und Zeile der Kommentarzeile.

#### UC-DL-10 — ID in Fließtext-Kommentar zählt nicht ✅ done
GIVEN `// siehe UC-DL-9 für Details` WHEN eingelesen wird THEN entsteht **kein** Eintrag.

#### UC-DL-11 — Test ohne jede ID ist kein Befund ✅ done
GIVEN eine Testmethode ohne ID-Kommentar WHEN eingelesen wird THEN entsteht kein Befund —
technische, Fixture- und Architekturtests belegen keinen fachlichen Use-Case.

#### UC-DL-34 — Unbekannte Testsyntax belegt trotzdem ✅ done
GIVEN `// UC-DL-34` unmittelbar über `it('should do x', () => {` WHEN eingelesen wird THEN gilt
UC-DL-34 als belegt — der Beleg hängt nicht an der Erkennung des Funktionskopfes.

#### UC-DL-35 — `--` als Kommentar-Präfix ✅ done
GIVEN `-- UC-DL-35` in einer `.sql`-Testdatei WHEN eingelesen wird THEN ist UC-DL-35 belegt.

#### UC-DL-36 — Methodenname ist optionaler Kontext ✅ done
GIVEN eine ID-Zeile über einem erkennbaren Funktionskopf WHEN der Report entsteht THEN nennt er den
Funktionsnamen; GIVEN eine ID-Zeile ohne erkennbaren Funktionskopf THEN bleibt der Beleg gültig und
der Report nennt nur `datei:zeile`.

### R-DL-5 — Vier Befundarten ✅ done

| Befund | Bedeutung | Priorität |
|---|---|---|
| `UNBELEGT_ERLEDIGT` | UC auf `✅`, kein Test | **hoch** — falsches `✅` |
| `UNBELEGT` | UC auf `❌`/`🚧`, kein Test | niedrig — erwartete Bestandsaufnahme |
| `VERWAIST` | ID am Test, in keinem Doc definiert | hoch — Regel gelöscht, Test blieb |
| `DOPPELT_DEFINIERT` | dieselbe ID in zwei Überschriften, auch dateiübergreifend | hoch — real vorgekommen; **einzige** Eindeutigkeits-Schranke seit R-DL-17 |

#### UC-DL-12 — Falsches ✅ wird gefunden ✅ done
GIVEN ein UC auf `✅`, zu dem kein Test die ID trägt WHEN `lintDocsAndTests` läuft THEN Befund
`UNBELEGT_ERLEDIGT`, im Report **zuerst** gelistet.

#### UC-DL-13 — Offener UC ohne Test ist nur Bestandsaufnahme ✅ done
GIVEN ein UC auf `❌` ohne Test WHEN `lintDocsAndTests` läuft THEN Befund `UNBELEGT` mit niedriger
Priorität — kein Blocker.

#### UC-DL-14 — Test überlebt seine gelöschte Regel ✅ done
GIVEN `// UC-DL-99` an einem Test und keine Definition `UC-DL-99` in den Docs WHEN
`lintDocsAndTests` läuft THEN Befund `VERWAIST` mit `datei:zeile` des Tests.

#### UC-DL-15 — Mehrere Tests belegen denselben UC ✅ done
GIVEN zwei Testmethoden, die beide `// UC-DL-9` tragen WHEN `lintDocsAndTests` läuft THEN gilt der
UC als belegt und es entsteht **kein** Befund (Kardinalität 1..n).

### R-DL-9 — Doc-Struktur wird geprüft, nicht vorausgesetzt ✅ done

`lintDocs` (Methode 2) prüft ohne Testbaum die Doc-Seite allein:

#### UC-DL-16 — Fremdes Präfix im Doc ✅ done
GIVEN ein Doc mit `idPrefix: READTOOLS` im Frontmatter und einer Regel `R-MCP-1` WHEN `lintDocs`
läuft THEN Befund `PRAEFIX_FREMD` — macht Tippfehler und Copy-Paste sichtbar.

> **`UC-DL-17` ist verbrannt (2026-09-16).** Sie forderte `PRAEFIX_DOPPELT` bei zwei Docs mit
> demselben `idPrefix` — genau das Gegenteil des heutigen SOLL (R-DL-17). Die ID wird **nicht**
> wiederverwendet und der Befundtyp `PRAEFIX_DOPPELT` ersatzlos gestrichen; der Test, der sie trug,
> entfällt.

#### UC-DL-18 — UCs ohne Präfix-Deklaration sind ein Versehen ✅ done
GIVEN ein Doc mit UC-Definitionen, aber ohne `idPrefix` im Frontmatter WHEN `lintDocs` läuft THEN
Befund `PRAEFIX_FEHLT` (Doc **ohne** UCs und ohne Präfix = Opt-out, siehe R-DL-11).

#### UC-DL-32 — Frontmatter wird ohne YAML-Parser gelesen ✅ done
GIVEN ein Doc, dessen erste Zeile `---` ist WHEN der Linter `idPrefix` sucht THEN liest er nur den
Block bis zur nächsten `---`-Zeile; ein `idPrefix:` **außerhalb** dieses Blocks zählt nicht.

#### UC-DL-19 — UC ohne übergeordnete Regel ✅ done
GIVEN eine UC-Überschrift, über der keine Regel-Überschrift steht WHEN `lintDocs` läuft THEN Befund
`UC_OHNE_REGEL` — Status-Vererbung wäre unmöglich.

#### UC-DL-20 — Kein Marker, nichts zu erben ✅ done
GIVEN eine Regel- oder UC-Überschrift ohne Statusmarker und ohne vererbbaren Marker WHEN `lintDocs`
läuft THEN Befund `STATUS_FEHLT`.

#### UC-DL-21 — Abweichende Definitionsform wird gemeldet ✅ done
GIVEN eine UC-ID am Anfang einer Bullet-Zeile statt in einer Überschrift, in einem teilnehmenden Doc
WHEN `lintDocs` läuft THEN Befund `FORM_ABWEICHEND` mit `datei:zeile` — eine Definitionsform, und
Abweichung ist ein Befund (Q6).

### R-DL-17 — Ein Präfix spannt ein Feature auf, nicht eine Datei ✅ done

Das `idPrefix` bezeichnet das **Feature**, nicht das Dokument. Mehrere Docs dürfen dasselbe Präfix
tragen — das ist die Aussage „diese Dateien gehören zusammen". Der Befundtyp `PRAEFIX_DOPPELT` wird
**ersatzlos gestrichen**.

Die Eindeutigkeit, auf die es ankommt, ist die der **ID**, nicht die der Datei: dieselbe ID in zwei
Überschriften bleibt `DOPPELT_DEFINIERT` (R-DL-2 / UC-DL-6), auch über Dateigrenzen hinweg. Ein
fremdes Präfix in einem Doc bleibt `PRAEFIX_FREMD` (UC-DL-16) — die Tippfehler-Erkennung ist davon
unberührt.

> **WEIL** (User Paul, 2026-09-16) — zwei Gründe, ein Widerspruch:
>
> 1. **Konflikt mit der Split-Regel.** `po.md` verlangt, ein Feature ab ~2 Doc-Seiten in ein
>    Unterverzeichnis mit mehreren Dateien zu splitten. Genau dann braucht ein Feature mehrere
>    Dateien — und `PRAEFIX_DOPPELT` feuerte. Zwei unserer eigenen Regeln widersprachen sich; die
>    Linter-Regel war die jüngere und die falsche.
> 2. **`nextIds` wäre sonst Overkill.** Lebt ein Präfix per Definition in genau einer Datei, ist die
>    nächste Nummer aus dem Doc ablesbar, das man ohnehin offen hat — das Tool aggregiert dann über
>    eine Menge der Größe 1. Erst mit „ein Präfix, n Dateien" wird die Vergabe zu einer Frage, die
>    **keine** einzelne Datei beantworten kann.
>
> **Vereinfachung, nicht Erweiterung:** Es entfällt ein Befundtyp samt Erkennungslogik und Test.
> Der Nutzen wandert dorthin, wo er strukturell abgesichert ist (`DOPPELT_DEFINIERT`), statt an
> einer Datei-Konvention zu hängen.

#### UC-DL-58 — Mehrere Docs teilen ein Präfix ✅ done
GIVEN zwei Docs mit `idPrefix: READTOOLS` und disjunkten IDs WHEN `lintDocs` läuft THEN entsteht
**kein** Befund — beide gelten als teilnehmende Docs desselben Features.

#### UC-DL-59 — `nextIds` zählt über alle Docs eines Präfix ✅ done
GIVEN `UC-READTOOLS-1..4` in Doc A und `UC-READTOOLS-5..9` in Doc B WHEN `nextIds` mit
`prefix=READTOOLS` läuft THEN meldet es `UC-READTOOLS-10` — die höchste Nummer **über alle** Docs
des Präfix, nicht die der zuletzt gelesenen Datei.


### R-DL-11 — Opt-in über die Präfix-Zeile, Skips werden namentlich genannt ✅ done

Ein Doc **ohne** `idPrefix` im Frontmatter **nimmt nicht teil**: es wird nicht geprüft und erzeugt
keinen Befund. Die Teilnahme ist damit Opt-in — man schaltet ein Doc scharf, indem man ihm das
Frontmatter-Feld gibt, typischerweise dann, wenn man es ohnehin anfasst (Einführung Schritt 3).

**Warum Opt-in:** Bei 66 Bestands-Docs ohne IDs wäre der erste Lauf 66 Befunde — Rauschen, kein
Befund, und laut Request §7 der wahrscheinlichste Grund, das Werkzeug nach Lauf 1 zu ignorieren.

**Warum der Skip trotzdem laut ist:** Ein Linter, der leise wird, indem er wegschaut, ist der
stille Ausfall aus R-DL-6 in neuer Form. Der Report nennt deshalb am Ende einen Abschnitt
**„Nicht teilnehmend (kein ID-Präfix)"** mit **jeder** übersprungenen Datei — damit der PO gezielt
opt-in setzen kann, statt eine beruhigende Gesamtzahl zu lesen.

#### UC-DL-22 — Doc ohne Präfix nimmt nicht teil ✅ done
GIVEN ein Doc ohne `idPrefix` und ohne UC-Definitionen WHEN der Linter läuft THEN entsteht kein
Befund, aber ein Eintrag im Abschnitt „Nicht teilnehmend" mit Dateipfad.

#### UC-DL-23 — Übersprungene Docs werden namentlich genannt ✅ done
GIVEN 66 Bestands-Docs ohne Präfix und 2 mit WHEN der Linter läuft THEN nennt der Kopf
`2 docs linted, 66 not participating` und der Fuß listet **alle 66** mit Pfad — damit der PO gezielt
opt-in setzen kann.

### R-DL-10 — `nextIds` verhindert Doppelvergabe strukturell ✅ done

`nextIds` nimmt einen **optionalen Parameter `prefix`**: leer → alle belegten Präfixe mit ihren
nächsten freien Nummern; gesetzt → Auskunft zu genau diesem Kürzel, inklusive „frei" (UC-DL-26).
Ohne Kandidaten-Eingabe wäre „unbenutztes Kürzel ist frei" nicht beantwortbar — die Docs enthalten
naturgemäß nur die *belegten* Kürzel.

#### UC-DL-24 — Nächste freie Nummer je Kürzel ✅ done
GIVEN Docs mit `R-READTOOLS-1..4` und `UC-READTOOLS-1..7` WHEN `nextIds` läuft THEN meldet es je
Kürzel die nächste freie Regel- und UC-Nummer (hier `R-READTOOLS-5`, `UC-READTOOLS-8`).

#### UC-DL-25 — Lücken werden nie wiederverwendet ✅ done
GIVEN eine Lücke in der Nummerierung (`UC-MCP-1`, `UC-MCP-3`) WHEN `nextIds` läuft THEN wird die
Lücke **nicht** wiederverwendet — gelöschte IDs bleiben verbrannt, sonst zeigt ein alter
Testkommentar später auf einen fremden Use-Case.

#### UC-DL-26 — Unbenutztes Kürzel ist frei ✅ done
GIVEN `prefix=FOO` und kein Doc mit diesem Kürzel WHEN `nextIds` läuft THEN wird `FOO` als frei
gemeldet (nächste IDs wären `R-FOO-1` / `UC-FOO-1`).

#### UC-DL-33 — Ohne `prefix` listet `nextIds` alle belegten Kürzel ✅ done
GIVEN kein `prefix`-Parameter WHEN `nextIds` läuft THEN werden alle in den Docs belegten Kürzel mit
ihrer jeweils nächsten freien Regel- und UC-Nummer gemeldet.
### R-DL-23 — `prefix` erlaubt Bindestriche im Präfix ✅ done (2026-09-24, `d477820`)

`prefix` akzeptiert `[A-Z]+(?:-[A-Z0-9]+)*` — Bindestriche und Ziffern **innen**, nicht am Rand.
Bestands-Präfixe (`ORD`, `CUST`, `TS`) verhalten sich unverändert. Das Splitten von `R-`/`UC-`-IDs
in Bereich + Nummer erfolgt am **letzten** Bindestrich vor der Nummer (`R-O-TEST-1` → Bereich
`O-TEST`, nicht `O`). Die Validierung liegt an **einer** Stelle im Core, nicht doppelt
(Tool-Fassade + Linter hatten bislang zwei Kopien derselben Regex).

> **WEIL:**
> In vielen Projekten tragen IDs bewusst einen **Bereichs**-Anteil — `O-TEST-1`, `R-ORD-1`,
> `D-<BEREICH>-<Nr>`. Der Bereich *ist* das Präfix, das die Nummernkreise trennt. Das Tool wies mit
> „prefix must be uppercase letters only" solche Projekte ab, und der
> Anwender fiel zurück auf Handvergabe per Grep — genau das, was das Tool verhindern soll.

#### UC-DL-67 — Bindestrich-Präfix wird akzeptiert ✅ done

GIVEN `prefix=O-TEST` WHEN `nextIds` läuft THEN wird der Aufruf **nicht** abgewiesen — die
Präfix-Suche läuft über `O-TEST` als ganzes Kürzel.

#### UC-DL-68 — Rand-Bindestrich/Kleinbuchstaben werden abgewiesen ✅ done

GIVEN `prefix=OP-`, `prefix=-OP` oder `prefix=op` WHEN `nextIds` läuft THEN klarer Fehler
(„not am Rand" bzw. uppercase only), keine stille Normalisierung.

### R-DL-24 — `nextIds` schreibt die gefundene Form fort; „free" nur bei echtem Nichts ✅ done (2026-09-24, `887d301` + `2235dba`)

Bei gesetztem `prefix` durchsucht `nextIds` zusätzlich zu den Definitionen der Opt-in-Docs den
**rohen Text** aller gescannten MD-Dateien der `docRoots` nach Vorkommen von
`<prefix>-\d+` (Wortgrenze, case-sensitiv). Gilt:

1. **Vorkommen zählen als belegt** — auch reine Erwähnungen in nicht teilnehmenden Docs. Konservativ:
   die höchste gefundene Nummer ist verbrannt, egal in welcher Form sie steht.
2. **Die gefundene Form wird fortgeschrieben, keine Wunschform konstruiert:** höchstes Vorkommen
   flach (`OP-79`) → Vorschlag `OP-80`; nur `R-`/`UC-`-Definitionen gefunden → wie bisher
   `R-<pfx>-N` / `UC-<pfx>-N` (R-DL-10).
3. **Der Vorschlag nennt die Fundstelle** (`<pfx>: highest found OP-79 in docs/offene-punkte.md →
   next: OP-80`) — die Antwort ist prüfbar, nicht glaubwürdig.
4. **„free" nur, wenn wirklich kein Vorkommen in irgendeiner Schreibweise gefunden wurde** — dann
   erst `R-FOO-1` / `UC-FOO-1`.

> **WEIL:**
> `nextIds` las nur Definitionen aus Opt-in-Docs — `OP-70…OP-79` als Bullets in einer
> nicht teilnehmenden Datei waren unsichtbar, und das Tool meldete „free, would start: R-OP-1".
> Ein Paralleluniversum `R-OP-*` neben dem existierenden Register, oder schlimmer: `OP-80` wurde
> zweimal vergeben. **Ein Tool, dessen Zweck Eindeutigkeit ist, darf im Zweifel nicht raten** — und
> es ist exakt das teuerste Fehlermuster dieses Projekts (*„A tool must never lie … a false
> negative is the most expensive bug"*, [AGENTS.md](../AGENTS.md)): Befund 1 und 2 des Reports
> scheiterten laut, dieser lieferte eine plausibel falsche Zahl.
>
> **Abgrenzung zu R-DL-16:** unverändert — vergeben ist, was **gespeichert** im Doc-Bestand steht.
> Rohvorkommen SIND gespeicherter Doc-Bestand; der Unterschied ist nur die Granularität
> (Definition vs. Vorkommen). Erwähnungen brennen Nummern bewusst — lieber eine verbrannte Nummer
> als eine Doppelvergabe, und die Fundstelle macht jeden Fall nachprüfbar.

#### UC-DL-69 — Flaches Register wird erkannt und flach fortgeschrieben ✅ done

GIVEN `OP-70…OP-79` als Bullets in einem Doc **ohne** `idPrefix` WHEN `nextIds` mit `prefix=OP`
läuft THEN meldet es das höchste Vorkommen (`OP-79`) **mit Datei-Fundstelle** und schlägt `OP-80`
vor — nie `R-OP-1`/`UC-OP-1`, nie „free".

#### UC-DL-70 — `R-`/`UC-`-Formen verhalten sich unverändert ✅ done

GIVEN Docs mit `R-READTOOLS-1..4` und `UC-READTOOLS-1..7` (Definitionen) WHEN `nextIds` mit
`prefix=READTOOLS` läuft THEN kommt wie bisher `R-READTOOLS-5`, `UC-READTOOLS-8` (UC-DL-24).

#### UC-DL-71 — Vorkommen zählen auch ohne Definition ✅ done

GIVEN eine Erwähnung `UC-FOO-3` in einem nicht teilnehmenden Doc, keine Definition WHEN `nextIds`
mit `prefix=FOO` läuft THEN ist `UC-FOO-3` belegt — Vorschlag ab `UC-FOO-4` mit Fundstelle, keine
Wiederverwendung der Lücke.

#### UC-DL-72 — „free" nur bei echtem Nichts ✅ done

GIVEN `prefix=FOO` und **kein** Vorkommen von `FOO-<Nr>` in irgendeiner gescannten Datei WHEN
`nextIds` läuft THEN erst dann meldet es `free` mit `R-FOO-1` / `UC-FOO-1` (UC-DL-26 bleibt, mit
schärferer Vorbedingung).

### R-DL-25 — `nextIds` nennt die nicht teilnehmenden Docs namentlich ✅ done (2026-09-24, `7933301`)

Der `nextIds`-Rückgabewert listet — wie der Lint-Report (R-DL-11) — **jede** nicht teilnehmende
Datei („Not participating (no idPrefix)") mit Pfad auf, nicht nur die Zählzeile.

> **WEIL:** Bei einem Vergabe-Tool ist die stumme Blindstelle am
> teuersten — genau in den nicht teilnehmenden Dateien kann die ID längst vergeben sein. R-DL-24
> entschärft das Fachliche (Rohscan sieht die Dateien trotzdem), die Liste macht den Scan
> nachprüfbar und kostet nur Dateinamen.

#### UC-DL-73 — Skipped-Liste im `nextIds`-Output ✅ done

GIVEN ein Lauf mit nicht teilnehmenden Docs WHEN `nextIds` zurückkehrt THEN listet der Output jede
dieser Dateien mit Pfad — analog zum „Nicht teilnehmend"-Abschnitt des Lint-Reports (UC-DL-23).


### R-DL-16 — `nextIds` ist zustandslos; vergeben ist erst, was gespeichert im Doc steht ✅ done

`nextIds` hält **keinen** Zustand: keinen Zähler im Speicher, keine Registry-Datei, keinen Cache.
Jeder Aufruf leitet die nächste freie Nummer **allein aus dem gespeicherten Doc-Bestand** ab
(R-DL-1, R-DL-12). Daraus folgt beides — die Stärke und die einzige Gefahr:

* **Robust über jeden Neustart.** Eclipse-Neustart, neue Workspace-Session, anderer Rechner,
  frisch geklontes Repo: das Ergebnis hängt nur an den Dateien. Es gibt keinen Zustand, der
  verloren gehen, veralten oder mit den Docs auseinanderlaufen könnte. Ein persistenter Zähler
  wäre die *schlechtere* Lösung — er könnte still von den Docs abweichen, und genau diese
  Abweichung wäre unsichtbar.
* **Es gibt keine Reservierung.** Eine gezogene Nummer ist **nicht** belegt. Wird sie nicht
  sofort als Überschrift ins Doc geschrieben *und die Datei gespeichert*, liefert der nächste
  Aufruf **dieselbe** Nummer — und zwei Use-Cases tragen dieselbe ID. Das ist exakt der Befund
  `DOPPELT_DEFINIERT`, gegen den das Tool gebaut wurde.

**Verbindlicher Arbeitsablauf (gilt für Jon, den einzigen Inhaber des Tools):**

1. `nextIds` aufrufen.
2. Die ID **sofort** als Überschrift ins Doc schreiben und die Datei speichern.
3. Erst danach die nächste ID ziehen.

Nie mehrere IDs auf Vorrat ziehen und später verteilen. Werden mehrere Use-Cases in einem Zug
angelegt, wird **einmal** gezogen und von dort hochgezählt — aber **alle** entstehenden
Überschriften werden in **einem** Schreibvorgang gespeichert, bevor `nextIds` wieder befragt wird.

> **WEIL** (User-Frage Paul, 2026-09-15: „wie stellen wir sicher, dass immer eine neue ID kommt —
> sucht es nach einem Eclipse-Neustart die letzte ID in den Docs?"): Die Antwort auf den Neustart
> ist *ja, und das ist unkritisch*. Die eigentliche Lücke liegt nicht zwischen zwei Sessions,
> sondern **innerhalb einer Session zwischen Ziehen und Speichern**. Ein Zähler hätte die harmlose
> Frage gelöst und die gefährliche offen gelassen. Deshalb bleibt es beim zustandslosen Scan — die
> Verpflichtung ist der Ablauf, nicht der Speicher.

#### UC-DL-56 — Kein Aufruf beeinflusst den nächsten ✅ done
GIVEN ein unveränderter Doc-Bestand WHEN `nextIds` zweimal nacheinander aufgerufen wird — einmal
auf einer frischen Instanz, einmal auf einer, die vorher schon gelaufen ist — THEN liefern beide
Aufrufe **dasselbe** Ergebnis; aus einem früheren Aufruf fließt kein Zustand ein.

#### UC-DL-57 — `nextIds` legt seine Lesequelle offen ✅ done
GIVEN ein beliebiger `nextIds`-Aufruf WHEN er zurückkehrt THEN nennt der Rückgabewert — wie bei den
Prüfmethoden (UC-DL-4) — dass vom **Dateisystem** gelesen wurde und ungespeicherte
Editor-Änderungen nicht enthalten sind, sowie **wie viele Doc-Dateien** gescannt wurden.

> **WEIL:** Genau hier ist die Offenlegung am wertvollsten. Bei den Prüfmethoden führt ein
> veralteter Stand zu einem Falschbefund, den man sieht. Bei `nextIds` führt er zu einer
> **wiederverwendeten ID**, die man nicht sieht — der gefährlichere der beiden Fälle.
>
> **Umgesetzt (2026-09-15, `934ea7c`):** `DocsLinter.nextIds` gibt statt `List<NextIds>` einen
> `NextIdsResult` zurück, der die Zahlen **aus der Discovery** (`DocParseData.lintedDocs()` /
> `skippedDocs()`) mitführt — nie aus den Allokationen abgeleitet. Die Wortwahl liegt genau einmal
> vor: `DocsLintReportRenderer.docScanSummary(...)` bedient Lint-Methoden **und** `nextIds`.


### R-DL-6 — Ergebnis nennt Gesamtzahlen, nicht nur Befunde ✅ done

Befunde `UNBELEGT_ERLEDIGT` zuerst, jede Zeile mit `datei:zeile`.

Am Kopf steht ein **Scan-Umfang**, bevor irgendein Befund kommt:

```
Scanned: 3 doc file(s) linted, 44 not participating (of 47 .md found under docs/)
         218 test source file(s) found under ., 41 carrying UC ids
Found:   32 use case definitions (32 unique), 57 id references in tests (29 unique)
```

Dazu die Zählung je Befundart.

> **WEIL** (User 2026-09-15): Die Zahl gefundener **Dateien** — nicht nur gefundener IDs — ist der
> Selbsttest des Agenten, ob er das Verzeichnis überhaupt richtig erwischt hat. „0 findings" bei
> „0 test source files found" heißt *falscher Pfad*, nicht *sauberer Bestand*; ohne die Dateizahl
> sehen beide identisch aus. Das ist der klassische stille Linter-Ausfall und bei einem
> Vertrauenswerkzeug der gefährlichste Fehlerzustand.

**Begründung (nicht Kosmetik):** Ohne Gesamtzahlen ist ein leerer Report nicht von „nichts
eingelesen" unterscheidbar — ein falscher Pfad sähe exakt wie ein grünes Ergebnis aus. Das ist der
klassische stille Linter-Ausfall und bei einem Vertrauenswerkzeug der gefährlichste Fehlerzustand.
Deckt sich mit der Projekt-Architekturregel *„A tool must never lie about a limit"*
([AGENTS.md](../AGENTS.md), [eclipse-read-tools.md](eclipse-read-tools.md)).

#### UC-DL-27 — Leerer Lauf ist von sauberem Bestand unterscheidbar ✅ done
GIVEN `docRoots` zeigt auf ein leeres Verzeichnis WHEN der Linter läuft THEN nennt der Report
`0 use cases` im Kopf — ein falscher Pfad sieht damit nicht mehr wie ein grünes Ergebnis aus.

#### UC-DL-40 — Scan-Umfang nennt gefundene Dateien, nicht nur IDs ✅ done
GIVEN ein beliebiger Lauf WHEN Report und Summary entstehen THEN nennen beide, **wie viele
Doc-Dateien** und **wie viele Test-Quelldateien** gefunden wurden — zusätzlich zur Zahl der
UC-Definitionen und ID-Referenzen.

#### UC-DL-41 — Falscher Testpfad ist von sauberem Bestand unterscheidbar ✅ done
GIVEN `testRoots` zeigt auf ein Verzeichnis ohne Quelldateien WHEN der Linter läuft THEN meldet der
Kopf `0 test source file(s) found` — ein Tippfehler im Pfad sieht damit nicht mehr wie ein
fehlerfreier Lauf aus.

#### UC-DL-28 — Jede Befundzeile ist navigierbar ✅ done
GIVEN ein beliebiger Befund WHEN das Ergebnis entsteht THEN trägt die Zeile `datei:zeile`,
`UNBELEGT_ERLEDIGT` zuerst.

### R-DL-14 — Vergeben darf einer, prüfen dürfen alle ✅ done

Die ID-Vergabe (`nextIds`, Tool `DocsIdTool`) liegt **allein beim PO**. Die Prüfmethoden (`lintDocs`,
`lintDocsAndTests`, Tool `DocsLinterTool`) stehen allen Agenten offen, die sie brauchen.

| Agent | `nextIds` | `lintDocs` | `lintDocsAndTests` | warum |
|---|---|---|---|---|
| **Jon** (PO) | ✅ | ✅ | ✅ | vergibt IDs, prüft vor dem `❌`→`✅`-Flip |
| **Da Thinka** (Plan) | ❌ | ✅ | ✅ | liest die UC-IDs eines Docs, um sie je Inkrement in den Plan zu schreiben (nutzt dafür `lintDocs`) |
| **Da Mek** (Dev) | ❌ | ✅ | ✅ | prüft sich selbst vor der Fertigmeldung; **kopiert** IDs, erfindet nie welche |
| **Da Dok** (Review) | ❌ | ✅ | ✅ | 3-Seiten-Prüfung beim Review-Start |

> **WEIL** — Vergäbe Da Mek IDs selbst, entstünden sie zum **Bauzeitpunkt**, während der PO später
> eine andere ins Doc schreibt. Ergebnis: gleichzeitig `VERWAIST` (seine ID) und
> `UNBELEGT_ERLEDIGT` (die des Docs) — der Linter meldete dann Rauschen statt Befund, also exakt den
> Zustand, gegen den er gebaut wurde. Ein Prompt-Satz („nie erfinden") ist eine Bitte; ein fehlendes
> Tool ist eine Garantie.
>
> **Umgesetzt als Fassaden-Split, nicht als Namens-Filter** — begründet in
> [ADR-0048](adr/0048-docs-linter-read-only-and-tool-split.md): ein Filter auf das String-Literal
> `"nextIds"` bricht bei einem Rename **still**, die Fassade ist compile-gebunden. Präzedenz im
> eigenen Code: `planSave` wird Jon über `PlanReadTool` entzogen, nicht per Filter.

**Die Kette:** Jon vergibt (`nextIds`) → Doc → Da Thinka trägt die IDs je Inkrement in den Plan →
Da Mek kopiert sie an die Tests → Da Dok prüft per `lintDocsAndTests`.

#### UC-DL-48 — Da Mek kann keine ID vergeben ✅ done
GIVEN der Dev-Agent WHEN seine Tool-Liste aufgebaut wird THEN enthält sie `lintDocs` und
`lintDocsAndTests`, aber **nicht** `nextIds`.

#### UC-DL-49 — Jon kann prüfen und vergeben ✅ done
GIVEN der PO-Agent WHEN seine Tool-Liste aufgebaut wird THEN enthält sie `nextIds`, `lintDocs` und
`lintDocsAndTests`.

#### UC-DL-50 — Da Thinka darf prüfen, nicht vergeben ✅ done
GIVEN der Plan-Agent WHEN seine Tool-Liste aufgebaut wird THEN enthält sie `lintDocs` und
`lintDocsAndTests`, aber **nicht** `nextIds`.

#### UC-DL-55 — Ein Root für beide Fassaden ✅ done
GIVEN ein Projektwechsel setzt das `workingDir` WHEN anschließend über `DocsIdTool` oder
`DocsLinterTool` gelintet wird THEN arbeiten beide auf demselben Root — es gibt keine zweite
Kern-Instanz mit eigenem Zustand.

#### UC-DL-51 — Da Dok prüft, vergibt nicht ✅ done
GIVEN der Review-Agent WHEN seine Tool-Liste aufgebaut wird THEN enthält sie `lintDocs` und
`lintDocsAndTests`, aber **nicht** `nextIds`.

### R-DL-15 — Der Linter hängt an keinem Schalter ✅ done

Beide Tools sind **immer** registriert, unabhängig von `diskToolsEnabled`.

> **🐞 WEIL** — Befund 2026-09-15: `DocsLinterTool` wurde nur bei `diskToolsEnabled == true`
> registriert (`SharedToolsComponent.java:67-70`), Default ist `false` (`LlmConfig.java:92`). In der
> Standard-Konfiguration existierte das Tool damit für **niemanden** — auch nicht für Da Dok, dessen
> Prompt es beim Review-Start verlangt. Ein Prompt, der auf ein nicht vorhandenes Tool zeigt, lässt
> den Agenten halluzinieren oder blockieren, und niemand sieht es.
>
> Das Disk-Gate schützt vor **freiem Dateisystem-Schreibzugriff**. Den hat der Linter seit R-DL-7
> nicht mehr: er liest, ist auf einen `root` beschränkt und schreibt nichts. Er gehört deshalb nicht
> hinter dieses Gate. Und kein eigener Schalter — noch ein Config-Key für ein read-only-Tool wäre
> Overhead ohne Schutzwirkung.

#### UC-DL-52 — Linter ist auch bei deaktivierten Disk-Tools da ✅ done
GIVEN `diskToolsEnabled = false` (Default) WHEN die Tools aufgebaut werden THEN sind `DocsIdTool`
und `DocsLinterTool` trotzdem registriert und aufrufbar.

#### UC-DL-53 — Umschalten des Disk-Gates berührt den Linter nicht ✅ done
GIVEN `diskToolsEnabled` wird von `true` auf `false` geschaltet WHEN der **gemeinsame** Tool-Bestand
danach gelesen wird THEN sind die drei Disk-Tools verschwunden und **`DocsLinterTool` ist noch da**.

> **Zwei Besitzverhältnisse, ein Gate — nicht verwechseln:** `DocsLinterTool` liegt im *shared*
> Service (deshalb sehen es Da Thinka, Da Mek und Da Dok). `DocsIdTool` liegt **absichtlich nicht**
> dort, sondern nur in Jons kuratiertem Service — genau dadurch wird es den Sklaven entzogen
> (R-DL-14). Der Gate-Übergang ist folglich **nur am shared Bestand** beobachtbar; dort greift die
> Mutation nachweislich (Gate-false-Zweig entfernt zusätzlich die Linter-Fassade → Test rot).
>
> **Beobachtbarkeitsgrenze bei Jon (ehrlich benannt, 2026-09-15):** Jons `ToolService` wird
> **einmalig** beim Bau gefüllt (`BuildPoAgentComponent.build()`) und danach von keinem Codepfad
> mehr verändert — `updateConfig` mutiert ausschließlich den shared Service. Ein Verlust von
> `nextIds` bei Jon *durch den Übergang* ist damit strukturell unmöglich und deshalb **nicht
> falsifizierbar**: Ein Test darauf blieb selbst dann grün, als der Gate-false-Zweig beide Fassaden
> aus dem shared Service entfernte (per Mutation verifiziert).
>
> **Konsequenz für die Belege:** Die ID `UC-DL-53` trägt ausschließlich der Test am **shared**
> Bestand. Die Prüfung auf Jons Oberfläche bleibt als **Regressionsschranke** erhalten — sie
> schützt davor, dass jemand Jons Verdrahtung künftig ans Gate koppelt (bedingtes `addTool`,
> nachträglicher Umbau seines Service, Bezug der Fassaden über `sharedToolService.getTool(...)`) —
> trägt aber **keine UC-ID**. Ein Test, der eine strukturelle Unmöglichkeit prüft, ist eine
> Schranke, kein Beweis, und darf nicht als Beleg gezählt werden.

### R-DL-7 — Das Ergebnis ist der Rückgabewert, nicht eine Datei ✅ done

Der Tool-Call gibt **vollständig** zurück, was der Agent braucht: Scan-Umfang, Gesamtzahlen und
**jeden** Befund einzeln mit `datei:zeile` — ungekappt. Es gibt **keinen `reportPath`-Parameter**
und keinen Datei-Report.

> **WEIL** (User 2026-09-15, Clean Break): Der Nutzer ist ein Agent, kein Mensch am Terminal. Er
> bekommt den Rückgabewert ohnehin direkt in den Context — der Umweg über eine Datei liefert ihm
> nichts Neues und kostet einen zweiten Read. Gemessen am eigenen Repo: **5.942 Zeichen, 149
> Zeilen, 35 Befunde, ungekappt** — das passt in ein Tool-Result. Der Datei-Report enthielt nur
> Aufbereitung (Tabellen, Gruppierung, Methodennamen), keine zusätzliche Information.
>
> **Der Gewinn ist die Abwesenheit:** Ohne Schreibpfad ist das Tool *echt* read-only. Damit
> entfallen der Traversal-Angriffspfad auf `reportPath` (die Klasse Bug, die
> [write-path-validator.md](write-path-validator.md) real hatte), die `WriteValidator`-Kopplung je
> Agent und die Inkonsistenz von `isEditTool() == false` bei einem schreibenden Tool. Vier Probleme
> durch **Streichen** gelöst statt durch Absichern — dasselbe Muster wie bei Q8.
>
> Braucht jemand den aufbereiteten Report als Arbeitsdatei (z.B. ein Migrationslauf mit 100+
> Befunden), schreibt **Jon** ihn nach `docs/` — er hat dort Schreibrecht, und dort gehört er hin.

#### UC-DL-29 — Ergebnis kommt ohne jeden Dateizugriff ✅ done
GIVEN ein beliebiger Lauf WHEN der Linter läuft THEN wird **keine Datei geschrieben** und der
Tool-Call gibt die vollständigen Zahlen zurück — so nutzt ihn auch ein read-only Agent (Q4).

#### UC-DL-45 — Jeder Befund steht im Rückgabewert, ungekappt ✅ done
GIVEN ein Lauf mit n Befunden WHEN der Tool-Call zurückkehrt THEN enthält der Rückgabewert **alle n**
Befunde einzeln mit `typ`, `id` und `datei:zeile` — keine Auswahl, kein „zeige nur erste N".

#### UC-DL-46 — Kein Schreibparameter mehr vorhanden ✅ done
GIVEN ein Aufruf von `lintDocs` oder `lintDocsAndTests` WHEN die Tool-Signatur gelesen wird THEN
existiert **kein** `reportPath`-Parameter — Clean Break, kein Alias, keine Migration.

### R-DL-8 — Nie eine Schranke, nie ein Schreibzugriff ✅ done

Kein Build-Rot, keine Testausführung, **kein Schreibzugriff überhaupt**. Review-Werkzeug, kein Gate.
Auch später nicht — siehe „Einführung".

#### UC-DL-31 — Befunde brechen nichts ab ✅ done
GIVEN beliebig viele Befunde, auch `UNBELEGT_ERLEDIGT` WHEN der Linter läuft THEN endet er normal
und wirft keinen Fehler.

#### UC-DL-47 — Der Linter ändert keine einzige Datei ✅ done
GIVEN ein beliebiger Lauf über einen Baum WHEN er beendet ist THEN ist keine Datei erzeugt, geändert
oder gelöscht worden.

### R-DL-18 — Root-Fallback: workspace-qualifizierte Pfade werden aufgelöst ❌

Der `root`-Parameter wird tolerant aufgelöst, an **einem** Choke-Point für alle drei Methoden
(`lintDocs`, `lintDocsAndTests`, `nextIds`): gilt der gegebene Pfad als Verzeichnis, wird er benutzt;
sonst probiert das Tool den Pfad **ohne führenden `/`**, aufgelöst gegen das `workingDir` des Tools;
scheitern beide Versuche, kommt der klare Fehler („Root is not a directory") mit **beiden**
probierten Pfaden. Nie eine leise 0/0-Suche — ein falscher Pfad darf nicht wie ein sauberer Bestand
aussehen (R-DL-6, UC-DL-27/40/41).

> **WEIL** (User Paul, 2026-09-16): Der PO ruft die Tools mit workspace-qualifizierten Pfaden
> (`/llmpeon-parent`) auf, wie er sie aus der Eclipse-Welt kennt — und bekam still 0/0 bzw. einen
> rohen Fehler. „Im Tool normalisieren, dass beides geht" schlägt jedes Aufruf-Merkblatt: ein Tool
> für Agenten muss die offensichtliche Pfadform fressen, nicht den Agenten zwingen, zwei Pfaddialekte
> auseinanderzuhalten.

#### UC-DL-60 — Workspace-qualifizierter Root wird aufgelöst ❌

GIVEN `root=/llmpeon-parent` (kein Disk-Verzeichnis), aber `llmpeon-parent` existiert relativ zum
`workingDir` WHEN eine der drei Methoden läuft THEN wird das Verzeichnis gefunden und der Report
zeigt echte Zahlen (Scan-Umfang > 0), kein 0/0.

#### UC-DL-61 — Unauflösbarer Root nennt beide Versuche ❌

GIVEN `root` existiert weder als Verzeichnis noch ohne führenden `/` relativ zum `workingDir` WHEN
eine der drei Methoden läuft THEN Fehlermeldung nennt **beide** probierten Pfade — kein stiller
leerer Report.

### R-DL-19 — Scan-Quellen werden relativ zum Root benannt ✅ done (2026-09-19, `15e3eab`)

Der Report nennt die gescannten Verzeichnisse **relativ zum Root** (`docs`, `src/test/java`, …),
nicht als absolute Disk-Pfade — der User sieht auf einen Blick, welche Wurzeln aktiv waren, ohne
absolute Pfade mental gegen die Parameter auflösen zu müssen.

#### UC-DL-62 — Quellliste relativ ✅ done

GIVEN ein Lauf mit `docRoots=["docs"]`, `testRoots=["src/test/java"]` WHEN der Report entsteht
THEN nennt die Quellliste `docs`, `src/test/java` (relativ zum Root), nicht die absoluten Pfade.

### R-DL-20 — Lint-Läufe sind per `onTool` sichtbar — compact, mit Zahlen ✅ done (2026-09-19, `15e3eab`/`73bb156`)

Jeder Lauf meldet per `onTool` **eine Zeile** an die UI: Tool-Name + Scan-Zahl + Befundzahlen,
z. B. `lintDocs: 3 docs, 2 findings (1 UNBELEGT_ERLEDIGT)`. Der vollständige Report bleibt
ausschließlich im Rückgabewert (R-DL-7) — die Statuszeile bekommt nie den Vollreport.

> **WEIL** (User Paul, 2026-09-17): `onTool` zeigte bisher nur den Methodennamen — der User sah,
> *dass* gelintet wurde, aber nicht *was herauskam*. Eine kompakte Zeile mit Zahlen reicht; mehr
> wäre Rauschen in der Statuszeile.

#### UC-DL-63 — onTool-Zeile nennt Zahlen, nicht nur den Namen ✅ done

GIVEN ein beliebiger Lauf WHEN `onTool` feuert THEN enthält die Zeile Doc-Anzahl und
Befundzahl(en) — nicht nur den Methodennamen.

### R-DL-21 — Java-Textblock-Belege zählen nicht ✅

In `.java`-Testquellen zählt eine ID-Zeile **innerhalb eines Textblocks** (`"""` … `"""`) nicht
als Beleg — sie ist zitierter Fixture-Inhalt, kein Beleg am Test. Zeilenbasierter Zustand: eine
Zeile mit ungerader Anzahl von `"""` kippt den Zustand. Bewusst **nur Java**, bewusst einfach
(kein Escaping-Handling) — die Rateübung fremder Sprach-Grammatiken bleibt verworfen (R-DL-4).

> **WEIL** (realer Befund 2026-09-21): `DocsLinterToolTest.java:78` baut als Fixture eine Datei mit
> `// UC-DL-99` — exakt der Fall, den [open-points.md](open-points.md) als ❓ „Code-Block-Regel für
> Testquellen" zurückstellte („(a) offen bis er real auftritt"). Er ist real: der Dogfood-Lauf
> meldet `VERWAIST UC-DL-99 DocsLinterToolTest.java:78`, weil der Linter die Textblock-Zeile als
> Beleg am echten Test liest. Die Fixture selbst darf nicht verändert werden (der Test braucht die
> reine ID-Zeile als Beleg **in der Fixture**) — die einzige saubere Lösung ist, Textblock-Zeilen
> in `.java` als zitiert zu behandeln. Damit ist die ❓-Frage mit Option (b) entschieden: die eine
> triviale Heuristik, durch einen echten Fall gerechtfertigt.

#### UC-DL-64 — Textblock-ID ist kein Beleg ✅
GIVEN eine `.java`-Testdatei mit `// UC-XY-1` innerhalb eines `"""`-Blocks WHEN gescannt THEN kein
Beleg; GIVEN dieselbe Zeile außerhalb des Blocks THEN Beleg. *(Automatisiert:
`TestParserTest` ×3 — im Block / außerhalb / `.java`-only-Regression.)*

### R-DL-22 — „manuell verifiziert"-Marker exemprt vom UNBELEGT-Check ✅

Enthält die Überschriftenzeile eines UC nach dem Statusmarker einen **Klammer-Anhang** `(…)` mit
dem Wort `manuell` (Asterisk optional, `manuelle` matcht ebenfalls — bewusst loose, kein
Format-Parsing), gilt der UC als **manuell belegt**: kein `UNBELEGT_ERLEDIGT`, stattdessen eine
Info-Zeile `MANUELL` (Typ, id, `datei:zeile`) im Report — info, kein Blocker. Ein UC ✅ **ohne**
solchen Anhang bleibt `UNBELEGT_ERLEDIGT` (hoch).

> **WEIL** (2026-09-21, Paul freigibt): UC-JD-2…6 sind manuell verifiziert
> ([ADR-0051](adr/0051-debugger-no-live-session-tests.md)) und trugen die Annotation nur als
> menschenlesbaren Hinweis — der Linter meldete sie trotzdem als `UNBELEGT_ERLEDIGT` und blockierte
> den Flip-Workflow mit bekanntem Rauschen. Strikt generisches Datum/Beleg-Format zu verlangen wäre
> Parsing-Zeremonie ohne Schutzgewinn.

#### UC-DL-65 — Manueller Marker exemprt ✅
GIVEN UC ✅ mit `*(manuell verifiziert 2026-09-21, ADR-0051)*` und kein Test mit der ID WHEN
`lintDocsAndTests` THEN kein `UNBELEGT_ERLEDIGT`, aber Info-Zeile `MANUELL` mit `datei:zeile`.
*(Automatisiert: `DocsLinterMatchingTest.manuellMarkerExemptsDoneUcFromUnbelegtErledigt` +
Realform-Fall ohne Asterisk + `DocsLinterFindingsFixtureTest` UC-XX-1.)*

#### UC-DL-66 — Ohne Marker bleibt UNBELEGT_ERLEDIGT ✅
GIVEN UC ✅ ohne `manuell`-Anhang und ohne Test THEN Befund `UNBELEGT_ERLEDIGT` — Regressionsschranke.
*(Automatisiert: `DocsLinterMatchingTest.doneWithoutManuellMarkerStaysUnbelegtErledigt`.)*

## Nicht-funktional

| Anforderung | Begründung |
|---|---|
| Deterministisch | Zwei Läufe auf demselben Stand = identischer Report, sonst ist er kein Beleg. |
| Sprachneutral | Pattern ist ein Kommentar (`//`, `#`, `--`); **kein** Funktionskopf-Zwang (R-DL-4), damit keine geratene Syntax-Liste stille Lücken erzeugt. Scope folgt aus `testRoots` + `testGlobs`. |
| Schnell | Läuft bei jedem Review; zwei Dateibäume lesen, keine Kompilierung. |
| Selbst getestet | Fixture mit **je einem Fall pro Befundart**. Ein Linter, der nichts findet, ist von einem sauberen Bestand nicht unterscheidbar — er muss beweisen, dass er finden *kann*. |

## Einführung — der Bestand ist das Risiko

Beim ersten Lauf trägt kein Bestandstest eine ID; jeder Use-Case wäre ein Treffer. Das ist Rauschen
und der wahrscheinlichste Grund, das Werkzeug nach Lauf 1 zu ignorieren. Deshalb:

1. Nie Build-Schranke, auch nicht später — und kein Schreibzugriff (R-DL-8).
2. **In llmpeon wird der Bestand NICHT nachgezogen** (Q1) — Teilnahme ist Opt-in über `idPrefix`
   (R-DL-11). Ein einmaliges Nachziehen ist optional und kostet bei uns den Umbau der BDDs zu Überschriften (Q6).
3. Die ID ist Pflicht bei jedem **angefassten** Use-Case — inkrementell, nie rückwirkend.

**Migrationshilfe `suggestIds` (Request §7) — eigener Zyklus, Q2 🔒:** Lauf 1 erzeugt aus
bestehenden `→ Klasse.methode`-Verweisen Vorschläge (ID → gefundene Testmethode). Laut Request die
wertvollste Zusatzfunktion. Bewusst **nach** dem Grundgerüst, weil sie als einzige Methode rät
statt abzugleichen.

## Prompt-Änderungen (Teil des SOLL, nicht optional)

Die Prompt-Dateien gehören dem PO ([prompts.md](prompts.md)); die Agenten ändern sie nie selbst.

| Rolle | Ergänzung |
|---|---|
| **PO-Agent (Jon)** | Vergibt IDs ausschließlich über `nextIds` — nie von Hand weitergezählt. Vor jedem Flip `❌` → `✅` läuft `lintDocsAndTests`. Ein `✅` ohne belegten Test ist ein Defekt, kein Versehen. |
| **Plan-Agent (Da Thinka)** | Ermittelt die UC-IDs des Feature-Docs per `lintDocs` und schreibt in **jedes Inkrement**, welche UC-IDs es abdeckt. Ein Inkrement ohne genannte UC-ID ist nur zulässig, wenn es nachweislich keinen Use-Case berührt (Wiring, Refactoring). |
| **Dev-Agent (Da Mek)** | Jeder Test, der einen im Plan genannten Use-Case belegt, trägt dessen ID als reine Kommentarzeile unmittelbar über dem Test (`// UC-X-1`, `# UC-X-1`, `-- UC-X-1`). Mehrere IDs kommagetrennt. Rename/Move → Kommentar wandert mit. **IDs werden aus Plan/Doc kopiert, nie erfunden** — er hat `nextIds` nicht. |
| **Review-Agent (Da Dok)** | Jedes Review startet mit `lintDocsAndTests`. `UNBELEGT_ERLEDIGT` und `VERWAIST` sind Blocker, kein Hinweis. Fehlt das Tool oder nimmt das Doc nicht teil: im Report vermerken und weitermachen — nie ein Review verweigern. |

## Offene Punkte (vor dem Bau zu klären)

| # | Frage | Status |
|---|---|---|
| Q3 | Lieferform → **🔒 nur Dateisystem-Zugriff, ein `root`-Parameter mit Default = Disk-Pfad des gewählten Projekts.** Kein `eclipse*`-Gegenstück, kein `FileTreeReader`-Interface, keine Änderung an den bestehenden Read-Tools ([ADR-0047](adr/0047-docs-linter-disk-only-single-root.md)). | 🔒 |
| Q4 | Da Dok ist read-only, Tool schrieb einen Report → **🔒 `reportPath` ersatzlos gestrichen** (User 2026-09-15). Der Rückgabewert enthält bereits alles ungekappt; die Datei war reine Aufbereitung. Damit ist das Tool echt read-only (R-DL-7). | 🔒 |
| Q5 | Tool-Filter je Agent → **🔒 Split in `DocsIdTool` (nur Jon) und `DocsLinterTool` (alle)** (User 2026-09-15). Der Filter wirkt tool-weit, nicht method-weit — der Split macht aus einer Sonderregel eine normale Zuordnung (R-DL-14). | 🔒 |
| Q10 | 🐞 Linter hing am `diskToolsEnabled`-Gate (Default `false`) und fehlte Jon ganz → **🔒 immer registrieren, Jon + Da Thinka bekommen ihn** (User 2026-09-15). R-DL-15, R-DL-14. | 🔒 |
| Q1 | **Dogfooding:** llmpeon-Docs kennen keine UC-IDs (Ist: `R1`/`R2` doc-lokal, ~200 `→ Klasse.methode`-Verweise in 33 Docs). → **🔒 Tool bauen + Prompts anpassen, Bestand NICHT rückwirkend nachziehen**; ID-Pflicht ab jetzt bei jedem angefassten Use-Case (Einführung Schritt 3), Teilnahme per Opt-in (R-DL-11). | 🔒 |
| Q2 | Migrationshilfe `suggestIds` → **🔒 separat, nach dem Grundgerüst** (User 2026-09-15). Begründung: die drei Kernmethoden sind deterministische Mengenabgleiche, `suggestIds` muss heuristisch **raten** — anderer Charakter, andere Testbarkeit. Und da Opt-in bei uns ohnehin ein Doc-Umbau ist (Q6), spart es uns weniger als dem Erstanwender. | 🔒 |
| Q6 | **Definitionsform → 🔒 nur Überschriften** (`#### UC-…`), keine Bullet-Variante. User 2026-09-15: „Konsistenz ist besser als Optionen, das Tool soll Fehler melden wenn wir abweichen." Preis: Opt-in eines Bestands-Docs ist ein Umbau, kein Handgriff — passiert beim ohnehin-Anfassen. | 🔒 |
| Q8 | **Sprachneutralität → 🔒 Methodenkopf-Zwang gestrichen** (User 2026-09-15, nach IST-Befund: `it(…)`/`export const` wurden nicht erkannt → stilles False Negative → falsches `UNBELEGT_ERLEDIGT`). Dazu `testGlobs` als Liste, `--` als drittes Kommentar-Präfix, Default-Testtypen aus `TextFileTypes`, `docRoots` vom Test-Scan ausgeschlossen. Betrifft R-DL-4, R-DL-1; neue UC-DL-34..39. | 🔒 |
| Q9 | **Scan-Umfang im Report → 🔒 gefundene Dateizahlen** (Doc-Dateien und Test-Quelldateien), nicht nur ID-Zahlen (User 2026-09-15: „als Hinweis für das LLM ob es das Verzeichnis richtig erwischt hat"). UC-DL-40/41. | 🔒 |
| Q7 | **Ort des Präfix → 🔒 Frontmatter `idPrefix: DL`**, nicht Blockquote im Fließtext (User 2026-09-15). Metadatum gehört in den Header; Linter liest den Block zwischen den ersten beiden `---` ohne YAML-Parser (UC-DL-32). Vorbereitung für [blume-publishing.md](blume-publishing.md). | 🔒 |

## Herkunft

Entstanden 2026-09-15 aus einem autonomen Nachtlauf: dieser fand 15+ kaputte
Testverweise in Bestands-Docs, darunter ein unbelegtes `✅` und zwei doppelt vergebene Regel-IDs.
Das Werkzeug sichert künftige Zyklen deterministisch ab.
