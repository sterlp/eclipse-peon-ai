---
idPrefix: OD
---

# Tool-Output-Disclosure — Caps ehrlich machen

> **Status:** ✅ done (2026-09-20, Paul specified 2026-09-19; R-OD-7 2026-10-17, R-OD-5-Rework 2026-10-17, R-OD-8 2026-10-24).

## Ziel

AGENTS.md: „A tool must never lie about a limit." Grep-Tools disclose ihre Caps im Return — drei weitere
Tools kappen still oder gar nicht. Nachziehen, gleiche Disclosure-Logik wie `grepComplete`.

## Business Rules

### R-OD-1 — `eclipseSearchFiles` ✅

wird das Limit erreicht (Default 100, **Cap 500** — Paul 2026-09-19,
war 1000), nennt der Return die Kappung („capped at N — narrow your search") — vorher: stille Kappung.

#### UC-OD-1 — eclipseSearchFilesCapsWithDisclosure ✅
- GIVEN Workspace mit 600 Match-Dateien WHEN `eclipseSearchFiles(query, limit=500)` THEN Return listet
  500 Treffer **und** nennt die Kappung („capped at 500 — narrow your search").

### R-OD-2 — `diskSearchFiles` ✅

gleiche Regel — nur falls `limit > 0` und erreicht.

#### UC-OD-2 — diskSearchFilesDisclosesCap ✅
- GIVEN Verzeichnis mit 80 Match-Dateien WHEN `diskSearchFiles(query)` (Default-Limit 50) THEN Return
  listet 50 Treffer **und** nennt die Kappung.

#### UC-OD-3 — diskSearchFilesUnlimitedNoDisclosure ✅
- GIVEN Verzeichnis mit 30 Match-Dateien WHEN `diskSearchFiles(query, limit=0)` THEN alle 30 Treffer,
  **keine** Disclosure-Zeile (nichts wurde gekappt).

### R-OD-3 — `webFetchAsMarkdown` ✅

statt byte-Cap → **paginiert** (Snapshot-Cache letzte 16 Seiten — [web-tools.md](web-tools.md) R-WEB-9 ✅,
500-Zeilen-Fenster je Call, disclosed) — Details in [web-tools.md](web-tools.md); Fehlerpfad
(HTTP ≥ 400) gibt Status + **Snippet** statt des vollständigen Body zurück.

### R-OD-4 — Disclosure-Muster ✅

Disclosure-Zeilen folgen dem bestehenden `AiReponseBuilder`-Muster (eine Stelle, beide
Such-Tools konsistent).

### R-OD-5 — `eclipseBuildProject` ✅ done (2026-09-24, Paul; Review Da Dok ACCEPTED; **Test-Rework 2026-10-17**)

Marker-Output wird auf **100** gecappt (Errors zuerst, dann Warnings) — vorher: unlimitiert
(`EclipseBuildTool.java:241-254` druckt alle ERROR+WARNING-Marker). Wird der Cap erreicht, nennt
der Return die Kappung im bestehenden Disclosure-Muster („showing 100 of N markers"). INFO-Marker
bleiben gedroppt (unverändert).

**Rework 2026-10-17 (Paul-TODO):** die Cap-Logik lebt im **Core** (`AiReponseBuilder`) und wird
dort mit **Fake-Problems** getestet — ein geseedeter Fake-Marker überlebt `eclipseBuildProject`
nicht (der Tool-Call löscht als ersten Schritt alle PROBLEM-Marker, `EclipseBuildTool.java:209`),
ein Fake-IMarker-E2E ist daher infeasibel. Der Plugin-Test bleibt ein kleiner **E2E, der zeigt,
dass Projekt-Errors sichtbar sind** (kurzer Timeout statt 180s).

#### UC-OD-5 — buildMarkersCappedWithDisclosure ✅ (Core: `AiReponseBuilderTest`, Fake-Problems)
- GIVEN ≥ 100 Marker im Input WHEN der Cap-Pfad läuft THEN 100 Marker (alle Errors zuerst, dann
  Warnings) **und** die Kappung ist disclosed („showing 100 of N markers").

#### UC-OD-6 — buildReportShowsProjectErrors ✅ (Plugin-E2E, `timeout = 30000`)
- GIVEN Fixture-File mit 2 echten JDT-Fehlern WHEN `eclipseBuildProject` THEN die Errors erscheinen
  im Report **und** unter Cap keine Disclosure-Zeile (nichts wurde gekappt).

### R-OD-6 — Grep: Zeilenlängen-Cap gegen Minified-Overflow ✅ done (2026-09-30; **2026-10-17 Cap 2000 → 500 via OutputTrim**)

Grep liefert pro Treffer Pfad + Zeilennummer + **die komplette Zeile** — der 100-Zeilen-Cap
(`MAX_GREP_LINES`) ist ein **Zeilenzähler ohne Längenlimit pro Zeile**. Eine minifizierte
`.js`/Bundle-Datei (eine einzige Megabyte-Zeile) schiebt damit faktisch die ganze Datei in den
LLM-Kontext — Hauptverdachtiger für den Context-Overflow (Thinka, 2026-09-30, Grep `replace` workspace-weit).

**SOLL (2026-10-17, Output-Trim-Service Slice 1):** der Zeilen-Cap liegt bei **500 Zeichen**
(Paul: „500 überall", ersetzt den R-OD-6-Wert 2000) und wird von der geteilten Komponente
[`OutputTrim`](output-trim-service.md) ausgeführt (`capLine`, Marker `…(+N chars)`) — Cap + Kürzung +
Disclosure an EINER Stelle, beide Grep-Familien konsistent („one behaviour, one implementation").
Historie: 2026-09-30 bis 2026-10-16 war der Wert 2000 mit Muster „N line(s) truncated at 2000 chars"
— Clean Break, kein Alias. Der 0-Treffer-Fall bleibt unverändert (klein).

#### UC-OD-7 — grepClampsMinifiedLine ✅
- GIVEN minifizierte Datei mit einer 2-MB-Zeile, die matcht WHEN grep THEN die Zeile kommt auf
  500 Zeichen gekappt zurück (Marker `…(+N chars)`) **und** die Disclosure nennt die Truncation
  („truncated: N chars total, M shown").

#### UC-OD-8 — grepNormalLinesUntouched ✅
- GIVEN normale Zeilen (< Cap) WHEN grep THEN unverändert, keine Truncation-Disclosure.

#### UC-OD-9 — grepNoMatchesUnchanged ✅
- GIVEN Suche ohne Treffer WHEN grep THEN das kleine ehrliche „no matches"-Payload (Guard,
  Regression gegen Regression-Behandlung des Leerfalls).

### R-OD-7 — Disclosure nennt nur angewandte Filter (Grep-Datei-Target) ✅ done (2026-10-17)

`eclipseGrepFiles` mit **Datei-Target** (`instanceof IFile`) wendet den `extension`-Filter
bewusst **nicht** an (die Datei ist selbst der Filter — Pendant: Disk-Seite C-EXT-Muster,
`DiskGrepToolTest.fileTargetBeatsExtensionFilter`) — aber `grepComplete`/`onTool` disclose
die rohe Extension, als wäre gefiltert worden: die Text/Inhalt-Diskrepanz, die Inc-C-EXT auf
der Disk-Seite behoben hat (AGENTS: „one behaviour, one implementation", Da-Dok-CONCERN
2026-10-16, unblocked).

**SOLL (C-EXT-Muster auf den IFile-Zweig):** `effectiveExtension = (Target ist Verzeichnis/
Container) ? extension : null` — `grepComplete` + `onTool` disclose `effectiveExtension`, nie
die rohe Extension.

#### UC-OD-10 — fileTargetBeatsExtensionFilterInDisclosure ✅
- GIVEN `eclipseGrepFiles` mit einem konkreten `.java`-File als Target und `extension=".xml"`
  WHEN grep THEN alle Matches der Datei kommen zurück (kein Extension-Filter) **und** die
  Disclosure nennt KEINE Extension (nichts wurde gefiltert).
- GIVEN Ordner-Target mit `extension=".java"` WHEN grep THEN Disclosure nennt die Extension
  wie heute (unverändert).

→ `fileTargetBeatsExtensionFilterInDisclosure` (Plugin-Test-Modul, Pendant zu
`DiskGrepToolTest.fileTargetBeatsExtensionFilter`)

### R-OD-8 — Tool-Fehler-Meldung nennt Tool, Zeit, Exception-Klasse + Cause-Chain ✅ done (2026-10-24, Paul; Commits `7f0435d2`+`3b56ac2b`, Da-Dok CONCERNS aufgelöst F1/F2/F5)

Fängt der Tool-Loop eine Tool-Exception (`ToolService.runAllTools`), bekommt `onProblem` heute
nur `"<Tool> failed: <e.getMessage()> details logged."` — oft wäre das `buildWithDev failed:
closed details logged.`: nicht triagierbar, ohne ins Error Log zu gehen. Der volle Stack geht
bereits an Log + Tool-Ergebnis (LLM), nur der Mensch sieht die Details nicht.

**SOLL:** die `onProblem`-Zeile nennt **Tool-Name, Uhrzeit (`HH:mm` lokal, Muster
[R-TD-1](tool-time-disclosure.md)), Exception-Klasse + Message und die komplette Cause-Chain**
(je Kettenglied `Klasse: message`, verbunden mit ` ← `):

    buildWithDev failed (14:32): IllegalStateException: closed ← IOException: Connection reset — details logged.

- Message `null`/leer → `(no message)` statt `null`.
- **Wrapper-Glieder entfallen:** ein Kettenglied, das nur seine Cause wiederkäut (Message ist
  die Cause-Message oder das Cause-`toString()` — z. B. der `ToolExecutionException`-Wrapper
  des Tool-Executors), wird nicht als eigenes Glied gerendert — es trägt keine Information
  (Präzisierung 2026-10-24, Da-Mek-IST-Befund; die Beispielzeile oben gilt damit auch real).
- **Mehrzeilige Messages:** Whitespace wird normalisiert (einzeilig) und Zeilen im
  Stack-Frame-Muster (`^\s*at …`) werden aus der Message entfernt — der übrige Inhalt bleibt
  vollständig, der Stack flutet den Chat nicht (Präzisierung 2026-10-24).
- **Kein Stack in der Zeile** — der Stack bleibt im Log (`log.error`) und im Tool-Ergebnis
  für den LLM; der Chat flutet nicht.
- **LLM-Sink (Entscheidung Jon, Paul überlassen):** das Tool-Ergebnis trägt den vollen Stack
  bereits heute („Tool failed - check why and inform user" + Stack) — das deckt „mindestens
  das Gleiche wie der User" ab und der Stack hat für die Diagnose des Agenten Mehrwert →
  Tool-Ergebnis bleibt unverändert.
- Format-Hilfsmittel lebt im Core, eine Implementation (AGENTS „one behaviour, one
  implementation") — inkl. Uhrzeit-Muster wie `CallStats` (HH:mm), Clock testbar.

#### UC-OD-11 — toolFailureReportsFullException ✅

- GIVEN ein Tool wirft `Exception` mit Message und Cause-Chain WHEN `onProblem` feuert THEN
  die Zeile enthält Tool-Name, `HH:mm`, `Klasse: message` je Kettenglied (verbunden mit ` ← `)
  und endet mit „details logged.".
- GIVEN Message ist `null` WHEN `onProblem` THEN `(no message)` statt `null`.
- GIVEN der Stack WHEN Reporting THEN Stack nur in Log + Tool-Ergebnis, nie in der
  `onProblem`-Zeile.
