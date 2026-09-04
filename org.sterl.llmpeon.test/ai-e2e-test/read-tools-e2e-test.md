# E2E-Test: Read-Tools (Datei · Suche · Grep · Console)

**Für den Agenten, der diesen Test ausführt.** Du prüfst die Tools **aus der Sicht eines
Nutzers**, nicht den Quellcode. Lies keinen Produktcode, um dir eine Erwartung zu bilden — die
Erwartung steht hier.

## Regeln

1. **Führe die Schritte der Reihe nach aus** und notiere zu jedem: Aufruf, Ergebnis (gekürzt),
   erwartet ja/nein.
2. **Erstelle eine `issue.md` NUR, wenn du einen Fehler findest.** Kein Fehler = keine Datei,
   kein Report-Dokument, nur eine kurze Antwort im Chat: „alle N Schritte wie erwartet".
3. **Weiche nie aus.** Liefert ein `eclipse*`-Tool ein unerwartetes Ergebnis, wechsle **nicht**
   still auf `disk*` — genau das ist der zu findende Fehler. Notiere ihn.
4. **Rate nicht.** Ist eine Erwartung hier unklar formuliert, ist das selbst ein Befund.
5. Räume angelegte Dateien am Ende wieder weg — **nach** der letzten Prüfung, nie davor.

## Vorbedingungen

- Gewähltes Projekt: `llmpeon-parent`.
- Fixture-Projekt `test_project` ist im Workspace offen (enthält u.a. `Dockerfile`,
  `data/lines-120.txt`, `data/notes.peonx`, `src/org/sterl/fixture/*.java`).
- Mehrere Projekte offen — mindestens eines, das **alphabetisch vor** `llmpeon-parent` steht
  (z.B. `langchain4j`). Ohne das ist Schritt 4 nicht aussagekräftig.

---

## 1 — Lesen: Bereich wird geklemmt, nie erweitert

| # | Aufruf | Erwartet |
|---|---|---|
| 1.1 | `eclipseReadFile(data/lines-120.txt, startLine=100, endLine=900)` | Zeilen **100–120** mit Zeilennummern. **Nicht** die ganze Datei. |
| 1.2 | `eclipseReadFile(…, startLine=800)` | Leeres Ergebnis + Hinweis in der Art `file has 120 lines, requested start 800`. **Nicht** die ganze Datei. |
| 1.3 | `eclipseReadFile(…, startLine=900, endLine=100)` | Wie 1.1 — vertauschte Grenzen werden getauscht, dann geklemmt. |
| 1.4 | `eclipseReadFile(…)` ohne Zeilen | Ganze Datei, **ohne** Zeilennummern. |
| 1.5 | Dieselben vier Aufrufe mit `diskReadFile` | **Identische** Ausgaben wie 1.1–1.4. Jede Abweichung zwischen den Tool-Familien ist ein Fehler. |

## 2 — Grep: Modus wird immer benannt

| # | Aufruf | Erwartet |
|---|---|---|
| 2.1 | `eclipseGrepFiles("Model.*Widget")` | Treffer; Ergebnis nennt den Modus **`regex search`**. |
| 2.2 | `eclipseGrepFiles("foo(bar")` | **Kein Fehler.** Literal gesucht; Ergebnis nennt `literal search — query is not a valid regex`. |
| 2.3 | `eclipseGrepFiles("C++")` | Gültiges Regex → Modus `regex search`. Überraschende Treffer sind hier **korrekt**, solange der Modus dasteht. |
| 2.4 | `eclipseGrepFiles("zzz-gibt-es-nicht-xyz")` | `no matches` + Scope + Pattern. Keine Exception. |
| 2.5 | 2.1–2.4 mit `diskGrepFiles` | Gleiche Semantik, gleiche Modus-Meldung. |

## 3 — Grep: Dateitypen

| # | Aufruf | Erwartet |
|---|---|---|
| 3.1 | `eclipseGrepFiles("dockerGrepMe", path=test_project)` | `Dockerfile` **wird gefunden** (Datei ohne Endung). |
| 3.2 | Suche nach einem Begriff, der nur in `data/notes.peonx` steht, ohne `extension` | Leeres Ergebnis **und** die Ausgabe **benennt den Typ-Filter**. Stilles Nichts = Fehler. |
| 3.3 | Datei mit Endung `.bnd` über `diskGrepFiles` ohne `extension` | Wird gefunden — beide Tool-Familien sehen dieselben Dateitypen. |

## 4 — Suche & Grep: kein Falsch-Negativ durch Projektreihenfolge

| # | Aufruf | Erwartet |
|---|---|---|
| 4.1 | `eclipseSearchFiles("*.java")` ohne `projectName`, gewähltes Projekt `llmpeon-parent` | Treffer des **gewählten** Projekts stehen **vorne**, nicht nur Fremdprojekt-Treffer. |
| 4.2 | `eclipseSearchFiles("*.java", limit=10)` | Genau **10** Treffer insgesamt — nicht 10 geteilt durch die Projektanzahl. |
| 4.3 | `eclipseGrepFiles("class")` ohne `path` | Dasselbe Verhalten wie 4.1. |
| 4.4 | `eclipseSearchFiles("Alpha*")` und `eclipseSearchFiles("*lpha.java")` | **Beide** finden `Alpha.java`. |
| 4.5 | Datei mit `diskWriteFile` ins Fixture schreiben (**ohne** Refresh), dann `eclipseSearchFiles` danach | Wird gefunden. |
| 4.6 | `eclipseSearchFiles("zzz-gibt-es-nicht-xyz")` | Meldung nennt **Scope und Pattern**, nicht nur „No files found". |
| 4.7 | `eclipseSearchFiles("*.java", limit=-5)` | Liefert Treffer (Limit wird auf 1 geklemmt), **kein** leeres Ergebnis. |

## 5 — Console: filtern und ehrlich croppen

Erst `eclipseListAvailableConsoles`, dann eine Konsole mit reichlich Inhalt wählen
(z.B. nach einem Maven-Lauf).

| # | Aufruf | Erwartet |
|---|---|---|
| 5.1 | `eclipseReadConsoleLog(name, grep="ERROR")` | Nur Zeilen mit `ERROR`. Header nennt `showing N of M matching lines`, den Konsolennamen und `total`. |
| 5.2 | Wie 5.1 mit `lines=5` | Die **letzten 5** Treffer, nicht die ersten. Header zeigt `showing 5 of M`. |
| 5.3 | Wie 5.1 mit `grep="foo(bar"` | Kein Fehler; Header nennt **`literal search`**. |
| 5.4 | `eclipseReadConsoleLog(name)` ohne `grep` | Letzte 50 Zeilen + Header `showing 50 of M lines`, **kein** Modus-Wort. |
| 5.5 | Eine leere Konsole lesen | `showing 0 of 0 lines (console: X)` — kein magisches „empty". |

---

## Wenn du einen Fehler findest

Lege **eine** `issue.md` neben dieser Datei an (mehrere Befunde = mehrere Abschnitte in
derselben Datei). Pro Befund:

```markdown
## <kurzer Titel>

**Schritt:** <z.B. 3.2>
**Aufruf:** <exakter Tool-Call mit allen Parametern>
**Erwartet:** <Satz aus dieser Anleitung>
**Tatsächlich:** <Ergebnis, gekürzt aber wörtlich>
**Reproduzierbar:** ja / nein (wie oft versucht)
**Schwere:** Falsch-Negativ (Tool sagt „nichts da", obwohl es existiert) ·
             Falsche Ausgabe · Fehlender Hinweis · Kosmetik
```

**Ein Falsch-Negativ ist immer der schwerste Befund** — der Agent glaubt dann, etwas existiere
nicht, und trifft daraufhin falsche Entscheidungen. Melde es zuerst.

Keine Vermutungen zur Ursache und **keine Code-Änderungen** — du testest, du reparierst nicht.

---

**SOLL-Quelle:** `docs/eclipse-read-tools.md` (R1–R7). Weicht diese Anleitung vom SOLL ab, gilt
das SOLL — und die Abweichung ist selbst ein Befund.
