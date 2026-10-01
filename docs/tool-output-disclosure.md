---
idPrefix: OD
---

# Tool-Output-Disclosure — Caps ehrlich machen

> **Status:** ✅ done (2026-09-20, Paul specified 2026-09-19).

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

statt byte-Cap → **paginiert** (Mini-Cache letzte 5 URLs,
500-Zeilen-Fenster je Call, disclosed) — Details in [web-tools.md](web-tools.md); Fehlerpfad
(HTTP ≥ 400) gibt Status + **Snippet** statt des vollständigen Body zurück.

### R-OD-4 — Disclosure-Muster ✅

Disclosure-Zeilen folgen dem bestehenden `AiReponseBuilder`-Muster (eine Stelle, beide
Such-Tools konsistent).

### R-OD-5 — `eclipseBuildProject` ✅ done (2026-09-24, Paul; Review Da Dok ACCEPTED, Commit `5d74aab`)

Marker-Output wird auf **100** gecappt (Errors zuerst, dann Warnings) — vorher: unlimitiert
(`EclipseBuildTool.java:241-254` druckt alle ERROR+WARNING-Marker). Wird der Cap erreicht, nennt
der Return die Kappung im bestehenden Disclosure-Muster („showing 100 of N markers"). INFO-Marker
bleiben gedroppt (unverändert).

#### UC-OD-5 — buildMarkersCappedWithDisclosure ✅
- GIVEN Projekt mit 120 Markern WHEN `eclipseBuildProject` THEN Return listet 100 Marker (alle
  Errors zuerst, dann Warnings) **und** nennt die Kappung („showing 100 of 120 markers").

#### UC-OD-6 — buildMarkersUnderCapNoDisclosure ✅
- GIVEN Projekt mit 30 Markern WHEN `eclipseBuildProject` THEN alle 30 Marker, **keine**
  Disclosure-Zeile (nichts wurde gekappt).


### R-OD-6 — Grep: Zeilenlängen-Cap gegen Minified-Overflow ❌ specified (2026-09-30, Paul)

Grep liefert pro Treffer Pfad + Zeilennummer + **die komplette Zeile** — der 100-Zeilen-Cap
(`MAX_GREP_LINES`) ist ein **Zeilenzähler ohne Längenlimit pro Zeile**. Eine minifizierte
`.js`/Bundle-Datei (eine einzige Megabyte-Zeile) schiebt damit faktisch die ganze Datei in den
LLM-Kontext — Hauptverdachtiger für den 974k-Overflow (Thinka, 2026-09-30, Grep `replace` = 850
Treffer workspace-weit).

**SOLL:** jede ausgegebene Treffer-Zeile wird auf **2000 Zeichen** gekappt (⏳ Wert: Paul bestätigt
beim Bau), mit ehrlicher Disclosure im bestehenden Muster („N line(s) truncated at 2000 chars") —
eine Stelle im geteilten Layer (`AiReponseBuilder`/`LogExcerpt`-Muster), beide Grep-Familien
konsistent („one behaviour, one implementation"). Der 0-Treffer-Fall bleibt unverändert (klein).

#### UC-OD-7 — grepClampsMinifiedLine ❌
- GIVEN minifizierte Datei mit einer 2-MB-Zeile, die matcht WHEN grep THEN die Zeile kommt auf
  2000 Zeichen gekappt zurück **und** die Disclosure nennt die Truncation.

#### UC-OD-8 — grepNormalLinesUntouched ❌
- GIVEN normale Zeilen (< Cap) WHEN grep THEN unverändert, keine Truncation-Disclosure.

#### UC-OD-9 — grepNoMatchesUnchanged ❌
- GIVEN Suche ohne Treffer WHEN grep THEN das kleine ehrliche „no matches"-Payload (Guard,
  Regression gegen Regression-Behandlung des Leerfalls).
