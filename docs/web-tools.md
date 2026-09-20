---
idPrefix: WEB
---

# Web-Tools — Fetch (Kontext, paginiert) + Download (Disk)

> **Status:** ✅ done (2026-09-20, Paul specified 2026-09-19). WebFetchTool paginiert (Cache + 500-Zeilen-Fenster),
> `webGet` für Datei-Downloads ohne Limit.

## Ziel

Zwei Web-Tools mit klarer Trennung: `webFetchAsMarkdown` bringt **kleine** Ausschnitte in den Kontext
(paginiert über einen Mini-Cache), `webGet` lädt **Dateien auf Disk** — der Inhalt geht nie in den Kontext,
der Kontext sieht nur Metadaten.

## Regeln

### R-WEB-1 — paginierte `webFetchAsMarkdown` ✅

`webFetchAsMarkdown(url, startLine?, endLine?)`: bei Cache-Miss fetch → Markdown im
**Mini-Cache (letzte 5 URLs, LRU)**; Return = Zeilenfenster, **max 500 Zeilen** je Call (Start default 1;
`endLine 0/null` = **Fenster-Default `start+499`, NICHT Dateiende** — bewusste Abweichung von den Read-Tools,
Doku-Korrektur aus dem Review 2026-09-20), Disclosure **„lines X–Y of N — read on with startLine"**.

#### UC-WEB-1 — webFetchFirstCallReturnsFirstWindow ✅
- GIVEN URL erstmals gefetcht, Markdown mit 3000 Zeilen WHEN `webFetchAsMarkdown(url)` ohne Range
  THEN Zeilen 1–500, Disclosure „lines 1–500 of 3000 — read on with startLine=501".

### R-WEB-2 — Cache-Hit ohne Refetch ✅

Cache-Hit = **kein Refetch**: das Fenster kommt aus dem gecachten Snapshot — Paginierung
ist stabil (kein anderer Inhalt zwischen zwei Calls derselben URL). Cache-Verdrängung (6. URL) = beim
nächsten Call Refetch.

#### UC-WEB-2 — webFetchCacheHitPaginatesWithoutRefetch ✅
- GIVEN URL im Cache WHEN `webFetchAsMarkdown(url, startLine=501)` THEN Zeilen 501–1000 aus dem
  gecachten Snapshot, **kein** zweiter HTTP-Call.

#### UC-WEB-3 — webFetchCacheEvictionRefetches ✅
- GIVEN 6. URL verdrängt die älteste WHEN die verdrängte URL erneut aufgerufen wird THEN Refetch,
  Fenster wieder 1–500.

### R-WEB-3 — Fehlerpfad HTTP ≥ 400 ✅

Fehlerpfad HTTP ≥ 400 → Status + **Snippet** (Anfang des Mark downs), nie der volle Body.

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
