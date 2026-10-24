---
idPrefix: WEB
---

# Web-Tools — Fetch (Kontext, paginiert) + Download (Disk)

> **Status:** ✅ done (2026-09-20, Paul specified 2026-09-19). WebFetchTool paginiert (Cache + 500-Zeilen-Fenster),
> `webGet` für Datei-Downloads ohne Limit. **2026-10-18 (✅ done, Paul):** Cache-Größe 5 → **16**
> (R-WEB-9) — die Navigation in der Seite braucht mehr stabile Snapshots. Paul-Smoke offen.
> **2026-10-23 (✅ done, Nachtzyklus):** R-WEB-10…12 — `webSearch` (DuckDuckGo, alle Agenten).

## Ziel

Zwei Web-Tools mit klarer Trennung: `webFetchAsMarkdown` bringt **kleine** Ausschnitte in den Kontext
(paginiert über einen Mini-Cache), `webGet` lädt **Dateien auf Disk** — der Inhalt geht nie in den Kontext,
der Kontext sieht nur Metadaten.

## Regeln

### R-WEB-1 — paginierte `webFetchAsMarkdown` ✅

`webFetchAsMarkdown(url, startLine?, endLine?)`: bei Cache-Miss fetch → Markdown im
**Mini-Cache (LRU; Größe: R-WEB-9)**; Return = Zeilenfenster, **max 500 Zeilen** je Call (Start default 1;
`endLine 0/null` = **Fenster-Default `start+499`, NICHT Dateiende** — bewusste Abweichung von den Read-Tools,
Doku-Korrektur aus dem Review 2026-09-20), Disclosure **„lines X–Y of N (<Pfad-Label>) — read on
with startLine"** (Pfad-Label: R-WEB-13).

#### UC-WEB-1 — webFetchFirstCallReturnsFirstWindow ✅
- GIVEN URL erstmals gefetcht, Markdown mit 3000 Zeilen WHEN `webFetchAsMarkdown(url)` ohne Range
  THEN Zeilen 1–500, Disclosure „lines 1–500 of 3000 (converted from HTML) — read on with startLine=501".

### R-WEB-2 — Cache-Hit ohne Refetch ✅

Cache-Hit = **kein Refetch**: das Fenster kommt aus dem gecachten Snapshot — Paginierung
ist stabil (kein anderer Inhalt zwischen zwei Calls derselben URL). Cache-Verdrängung (die (Kapazität+1)-te URL) = beim
nächsten Call Refetch.

#### UC-WEB-2 — webFetchCacheHitPaginatesWithoutRefetch ✅
- GIVEN URL im Cache WHEN `webFetchAsMarkdown(url, startLine=501)` THEN Zeilen 501–1000 aus dem
  gecachten Snapshot, **kein** zweiter HTTP-Call.

#### UC-WEB-3 — webFetchCacheEvictionRefetches ✅
- GIVEN die (Kapazität+1)-te URL verdrängt die älteste WHEN die verdrängte URL erneut aufgerufen
  wird THEN Refetch, Fenster wieder 1–500.

### R-WEB-3 — Fehlerpfad HTTP ≥ 400 ✅

Fehlerpfad HTTP ≥ 400 → Status + **Snippet** (Anfang des Seiteninhalts — konvertiert oder raw, je
nach Pfad-Label R-WEB-13), nie der volle Body.

#### UC-WEB-4 — webFetchHttpErrorReturnsStatusAndSnippet ✅
- GIVEN HTTP 500 WHEN `webFetchAsMarkdown(url)` THEN Status + Snippet (kein voller Body im Kontext).

### R-WEB-4 — `webGet`: Download auf Disk ✅

`webGet(url, path)`: eigenes Tool, lädt die Datei auf Disk, **kein Size-Limit** (Datei
gehört auf Disk, nicht in den Kontext). Rückgabe = **HTTP-Status + Dateigröße + Disk-Pfad**, nie der
Dateiinhalt.

#### UC-WEB-5 — webGetDownloadsAndReturnsMetadata ✅
- GIVEN gültige URL + absoluten Disk-Pfad WHEN `webGet(url, path)` THEN Datei auf Disk, Return nennt
  HTTP-Status + Dateigröße + Disk-Pfad, **nicht** den Inhalt.

### R-WEB-5 — `webGet`-Guards ✅

`webGet` schreibt über **QualifiedPathValidator (absoluter Disk-Pfad) + WriteValidator**
— dieselben Guards wie `diskWriteFile`.

#### UC-WEB-6 — webGetEnforcesWriteGuards ✅
- GIVEN Pfad unter WriteValidator-Verbot WHEN `webGet(url, path)` THEN Denial mit erlaubten Pfaden
  (wie `diskWriteFile`); Pfad nicht absolut → ehrlicher Fehler.

### R-WEB-6 — `webGet` ist `isEditTool` ✅

`webGet` ist `isEditTool=true` → Sub-Agents/SearchAgent erhalten es nicht (bestehender
Filter greift automatisch).

#### UC-WEB-8 — webGetIsEditToolFiltered ✅
- GIVEN SearchAgent-Tool-Filter WHEN Tool-Matrix gebaut THEN `webGet` nicht verfügbar (isEditTool).

### R-WEB-7 — Ehrliche Fehler ✅

Ehrliche Fehler: HTTP-Fehler → Status-Code; Timeout/unbekannter Host → Fehlermeldung,
keine Teilergebnisse, keine stillen „0 bytes"-Erfolge.

#### UC-WEB-7 — webGetHonestZeroBytes ✅
- GIVEN Server liefert 0 bytes WHEN `webGet(url, path)` THEN ehrliche Meldung (Status + 0 bytes),
  kein stiller Erfolg.

### R-WEB-8 — `webGet` hinter dem Disk-Tools-Toggle ✅

`webGet` hängt hinter das `diskToolsEnabled`-Toggle (default **OFF**) — dieselbe Risikoklasse
wie die Disk-Write-Tools (schreibt auf absolute Pfade); ungated würde der Toggle still umgangen
(Paul 2026-09-19). Homepage-Doku des Toggles nennt `webGet` mit (gleiche Increment-Pflicht).

### R-WEB-9 — Snapshot-Cache hält die letzten 16 Seiten ✅ done (2026-10-18, Paul)

Der `webFetchAsMarkdown`-Snapshot-Cache hält **16** Seiten (gebaut ist 5). Grund: die Paginierung
lebt von stabilen Snapshots — bei 5 Seiten verdrängt eine mehrseitige Recherche ihre eigenen Seiten
und jeder Rücksprung refetcht. Eviction bleibt **LRU** (R-BC-1), die 16 ist eine benannte Konstante
am Tool — **keine** User-Config (unverändert zu R-BC-4, Paul 2026-09-17).

#### UC-WEB-9 — webFetchCacheHolds16Pages ✅
- GIVEN 17 verschiedene URLs nacheinander gefetcht WHEN danach URL 1 erneut aufgerufen wird THEN
  Refetch (URL 1 war die älteste von 17) — aber nach nur 16 URLs ist der erneute Aufruf von URL 1
  ein **Cache-Hit** (kein zweiter HTTP-Call).
→ `webFetchCacheHolds16Pages` (ersetzt den Cap-5-Nachweis in `webFetchCacheUsesBoundedLru`)

### R-WEB-10 — `webSearch`: Web-Suche als Tool (DuckDuckGo) ✅ done (2026-10-23, Nachtzyklus)

`webSearch(query, maxResults?)`: neue Tool-Klasse `WebSearchTool` (core) — sucht via
**DuckDuckGo** (HTTP, kostenlos, kein API-Key) mit einem dünnen eigenen Client, der das
langchain4j `WebSearchEngine`-Interface implementiert (kein Community-Modul — ADR-0074).
Return = **nummerierte
Trefferliste in den Kontext** (Titel, URL, Snippet), max **8 Treffer** je Call (`maxResults`
überschreibbar, hartes Maximum 10); bei mehr verfügbaren Treffern ehrliche Disclosure
(„showing N of M"). **Seiteninhalte gehen nie in den Kontext** — Nachlesen läuft über das
bestehende `webFetchAsMarkdown` (R-WEB-1…3, Snapshots/Paginierung). Kein eigener Cache (die Suche
ist nicht paginiert, jede Call frisch). Snippets werden auf **200 Zeichen** gekürzt (`…`);
  Treffer ohne Titel oder ohne absolute http(s)-URL werden übersprungen (die Suche wirft dafür
  keinen Fehler).

- GIVEN eine Query mit 20 Treffern WHEN `webSearch(query)` THEN 8 Treffer (Titel + URL + Snippet)
  mit Disclosure „showing 8 of 20". *(UC-WEB-10)*
- GIVEN eine Query mit 3 Treffern WHEN `webSearch(query)` THEN genau 3 Treffer, keine erfundenen
  Auffüllung. *(UC-WEB-10)*

#### UC-WEB-10 — webSearchReturnsCappedResultList ✅
- GIVEN eine Query WHEN `webSearch` läuft THEN nummerierte Trefferliste (Titel/URL/Snippet),
  max 8, ehrliche Disclosure bei Kappung, nie mehr als das Maximum.

### R-WEB-11 — `webSearch` für alle Agenten ✅ done (2026-10-23, Nachtzyklus)

`webSearch` ist **nicht** `isEditTool` → er wird allen Agenten gegeben (Paul: „allen geben"),
inklusive Search-Sub-Agenten — dieselbe Verteilung wie `webFetchAsMarkdown` (wörtlich: der
Peon-PO hat `webFetchAsMarkdown` nicht und bekommt `webSearch` ebenfalls nicht). Kein Toggle: die
Risikoklasse ist Lesen im Web (wie webFetch), kein Schreiben.

#### UC-WEB-11 — webSearchAvailableToAllAgents ✅
- GIVEN SearchAgent-Tool-Filter WHEN die Tool-Matrix gebaut wird THEN `webSearch` verfügbar
  (anders als `webGet`, UC-WEB-8).

### R-WEB-12 — `webSearch`: ehrliche Fehler und Leerergebnisse ✅ done (2026-10-23, Nachtzyklus)

Ehrlich in beiden Richtungen: **0 Treffer ist eine Meldung** („0 results for …"), kein stiller
Erfolg mit leerem Body; Rate-Limit/Timeout/Blockierung des DDG-Endpunkts → Fehlermeldung mit
Ursache, keine Teilergebnisse, keine erfundenen Treffer.

#### UC-WEB-12 — webSearchHonestEmptyAndErrors ✅
- GIVEN DDG liefert 0 Treffer WHEN `webSearch(query)` THEN ehrliche „0 results"-Meldung.
- GIVEN DDG antwortet mit Rate-Limit WHEN `webSearch(query)` THEN Fehlermeldung mit Ursache.

### R-WEB-13 — `webFetchAsMarkdown`: Nur HTML wird konvertiert ✅ done (2026-10-24, Bug-Triage T-2, Commits 8964fcc8/520ab5fd/f90406f5)

Der HTML→Markdown-Konverter läuft **nur** bei Media-Type `text/html` oder `application/xhtml+xml`
(und XML-Abkömmlingen `+xml`); jeder andere Inhalt (text/plain, application/json, …) wird als
**roher Text** übergeben — kein Newline-Collapse, kein Markdown-Escaping, kein Footer-Traum.
Der Footer benennt den Pfad („converted from HTML" / „raw text"), damit der Agent weiß, was er
vor sich hat (R-WEB-2: kein Werkzeug lügt über eine Einschränkung). Die @Tool-Beschreibung erwähnt
das Verhalten. Der Cache hält Pfad-Label und Inhalt zusammen — ein Cache-Hit meldet denselben
Pfad wie der erste Abruf.

#### UC-WEB-13 — webFetchRawForNonHtml ✅
- GIVEN der Server antwortet mit `text/plain` WHEN `webFetchAsMarkdown(url)` THEN der Body wird
  unverändert (raw) als Text übergeben und der Footer benennt „raw text".
- GIVEN der Server antwortet mit `text/html` WHEN `webFetchAsMarkdown(url)` THEN der Body wird
  zu Markdown konvertiert und der Footer benennt „converted from HTML".
- GIVEN ein `text/plain`-Ergebnis liegt im Cache WHEN der gleiche URL erneut abgerufen wird THEN
  kommt der gecachte Raw-Text mit „raw text"-Label (kein nachträglicher Konverter-Lauf).

