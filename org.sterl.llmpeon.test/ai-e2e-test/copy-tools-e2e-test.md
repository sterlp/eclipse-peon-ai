# E2E-Test: Copy-Tools (diskCopyFile · eclipseCopyFile)

**Für den Agenten, der diesen Test ausführt.** Du prüfst die Tools **aus der Sicht eines
Nutzers**, nicht den Quellcode. Lies keinen Produktcode, um dir eine Erwartung zu bilden —
die Erwartung steht hier.

## Regeln

1. **Führe die Schritte der Reihe nach aus** und notiere zu jedem: Aufruf, Ergebnis (gekürzt),
   erwartet ja/nein.
2. **Erstelle eine `issue.md` NUR, wenn du einen Fehler findest.** Kein Fehler = keine Datei,
   kein Report-Dokument, nur eine kurze Antwort im Chat: „alle N Schritte wie erwartet".
3. **Weiche nie aus.** Liefert ein `eclipse*`-Tool ein unerwartetes Ergebnis, wechsle **nicht**
   still auf `disk*` — genau das ist der zu findende Fehler. Notiere ihn.
4. **Rate nicht.** Ist eine Erwartung hier unklar formuliert, ist das selbst ein Befund.
5. **Räume ALLE angelegten Dateien am Ende weg** — nach der letzten Prüfung, nie davor.
   Danach verifizieren (Search), dass nichts übrig ist. Nur eine ggf. vorhandene `issue.md`
   bleibt liegen.

## Vorbedingungen

- Gewähltes Projekt: `test_project` (Fixture) — alles spielt in einem frischen Unterordner
  `copy-e2e/` darin.
- Fixture ist im Workspace offen; `test_project` ist versioniert — angelegte Dateien sind
  temporär und werden komplett wieder gelöscht.

---

## 0 — Fixture anlegen

| # | Aufruf | Erwartet |
|---|---|---|
| 0.1 | `diskWriteFile(copy-e2e/src-disk.txt)` mit Inhalt: erste Zeile `copy marker 1`, dann `äüß Ö` (Umlaute), dann `copy marker 2` | Erfolgsmeldung. |
| 0.2 | `eclipseWriteFile(copy-e2e/src-ecl.txt)` mit gleichem Inhalt (Marker `ecl` statt `disk`) | Erfolgsmeldung. |
| 0.3 | `diskWriteFile(copy-e2e/sub/target-disk.txt)` — Ziel-Unterverzeichnis **existiert**, Inhalt `occupied` | Erfolgsmeldung. |

## 1 — Happy Path: Byte-Copy, Meldung, Pfadform

| # | Aufruf | Erwartet |
|---|---|---|
| 1.1 | `diskCopyFile(copy-e2e/src-disk.txt → copy-e2e/copy-disk.txt)` | Erfolg. Meldung beginnt `Copied` und nutzt den **ASCII-Pfeil ` -> `** (nicht `→`). Disk-Tool nennt **absolute** Pfade. |
| 1.2 | `diskReadFile(copy-e2e/copy-disk.txt)` | Inhalt **byte-identisch** inkl. Umlaute (`äüß Ö` korrekt, nicht `Ã¤Ã¼Ã`). |
| 1.3 | `eclipseCopyFile(copy-e2e/src-ecl.txt → copy-e2e/copy-ecl.txt)` | Erfolg. Gleiche `Copied <s> -> <t>`-Form; Eclipse-Tool nennt den **workspace-relativen** Pfad. |
| 1.4 | `eclipseReadFile(copy-e2e/copy-ecl.txt)` | Inhalt byte-identisch inkl. Umlaute. |
| 1.5 | Vergleich der beiden Erfolgsmeldungen (1.1 vs 1.3) | Gleiche Formulierung, gleiche Semantik — nur die Pfadform unterscheidet sich (absolut vs. workspace-relativ). Kein anderes Formulierungs-Drift. |

## 2 — Kein Overwrite

| # | Aufruf | Erwartet |
|---|---|---|
| 2.1 | `diskCopyFile(copy-e2e/src-disk.txt → copy-e2e/sub/target-disk.txt)` | **Fehler** („Target already exists"-Semantik). Meldung **nennt den Ziel-Pfad**. Kein „Copied". |
| 2.2 | `diskReadFile(copy-e2e/sub/target-disk.txt)` | Inhalt ist **unverändert** `occupied` — kein Teil-Overwrite, keine Korruption. |
| 2.3 | `eclipseWriteFile(copy-e2e/sub/target-ecl.txt, „occupied-ecl")`, dann `eclipseCopyFile(copy-e2e/src-ecl.txt → copy-e2e/sub/target-ecl.txt)` | Fehler wie 2.1, Ziel-Pfad in der Meldung. |
| 2.4 | `eclipseReadFile(copy-e2e/sub/target-ecl.txt)` | Unverändert `occupied-ecl`. |
| 2.5 | Fehlermeldung 2.1 vs 2.3 | Gleiche Kategorie + gleiche Pfad-Sorgfalt in beiden Familien. |

## 3 — Verzeichnis als Quelle: Copy lehnt ab, Rename nicht (R1/R4-Parität)

| # | Aufruf | Erwartet |
|---|---|---|
| 3.1 | `eclipseCopyFile(copy-e2e/sub → copy-e2e/sub-copy)` | **Fehler** — Quelle ist ein Verzeichnis, Copy ist file-only. Meldung sagt es, kein Crash. |
| 3.2 | `diskCopyFile(copy-e2e/sub → copy-e2e/sub-copy)` | Derselbe Fehler wie 3.1. |
| 3.3 | `diskRenameFile(copy-e2e/sub → copy-e2e/sub-renamed)` | **Erfolg** — Rename ist atomic und akzeptiert Verzeichnisse (R4). |
| 3.4 | `diskRenameFile(copy-e2e/sub-renamed → copy-e2e/sub)` | Erfolg — Zustand zurück. |

## 4 — Fehlende Quelle & neue Ziel-Parents

| # | Aufruf | Erwartet |
|---|---|---|
| 4.1 | `diskCopyFile(copy-e2e/gibts-nicht.txt → copy-e2e/x.txt)` | Fehler („not found"-Semantik), Meldung nennt die fehlende Quelle, **keine** Zieldatei wird angelegt. |
| 4.2 | `eclipseCopyFile(copy-e2e/gibts-nicht.txt → copy-e2e/x.txt)` | Derselbe Fehler. |
| 4.3 | `diskCopyFile(copy-e2e/src-disk.txt → copy-e2e/deep/a/b/c.txt)` | Erfolg — fehlende **Ziel-Parents werden erzeugt**. |
| 4.4 | `diskReadFile(copy-e2e/deep/a/b/c.txt)` | Inhalt identisch zu src-disk. |
| 4.5 | `eclipseCopyFile(copy-e2e/src-ecl.txt → copy-e2e/deep-ecl/a/b/c.txt)` | Erfolg wie 4.3. |
| 4.6 | `eclipseReadFile(copy-e2e/deep-ecl/a/b/c.txt)` | Inhalt identisch zu src-ecl. |

## 5 — Aufräumen & Verifikation

| # | Aufruf | Erwartet |
|---|---|---|
| 5.1 | `eclipseDeleteResource(copy-e2e)` (bzw. je nach Toolfamilie `diskDeleteFile`) | Erfolg. |
| 5.2 | `eclipseSearchFiles("copy-e2e*", project=test_project)` | **Kein Treffer** — nichts übrig. Ggf. Reste einzeln löschen und erneut prüfen. |

---

## Wenn du einen Fehler findest

Lege **eine** `issue.md` neben dieser Datei an (mehrere Befunde = mehrere Abschnitte in
derselben Datei). Pro Befund:

```markdown
## <kurzer Titel>

**Schritt:** <z.B. 2.3>
**Aufruf:** <exakter Tool-Call mit allen Parametern>
**Erwartet:** <Satz aus dieser Anleitung>
**Tatsächlich:** <Ergebnis, gekürzt aber wörtlich>
**Reproduzierbar:** ja / nein (wie oft versucht)
**Schwere:** Falsch-Erfolg (Tool sagt „Copied", hat es aber nicht/korrumpiert) ·
             Falsch-Negativ (erlaubter Vorgang fälschlich abgelehnt) ·
             Falsche Ausgabe · Fehlender Hinweis · Kosmetik
```

**Ein Falsch-Erfolg ist der schwerste Befund** (still über­schriebene/verlorene Daten) —
melde ihn zuerst. Danach Falsch-Negativ.

Keine Vermutungen zur Ursache und **keine Code-Änderungen** — du testest, du reparierst nicht.

---

**SOLL-Quelle:** `docs/file-copy-tool.md` (R1–R4) + E5 in `docs/disk-file-write-tool.md`
(absolute Pfade in Disk-Erfolgsmeldungen). Weicht diese Anleitung vom SOLL ab, gilt das SOLL —
und die Abweichung ist selbst ein Befund.
