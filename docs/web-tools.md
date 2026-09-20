# Web-Tools — Fetch (Kontext, paginiert) + Download (Disk)

> **Status:** ❌ specified (2026-09-19, Paul). WebFetchTool wird paginiert (Cache + 500-Zeilen-Fenster),
> dazu neues `webGet` für Datei-Downloads ohne Limit.

## Ziel

Zwei Web-Tools mit klarer Trennung: `webFetchAsMarkdown` bringt **kleine** Ausschnitte in den Kontext
(paginiert über einen Mini-Cache), `webGet` lädt **Dateien auf Disk** — der Inhalt geht nie in den Kontext,
der Kontext sieht nur Metadaten.

## Regeln

- **R-W-1 ❌** `webFetchAsMarkdown(url, startLine?, endLine?)`: bei Cache-Miss fetch → Markdown im
  **Mini-Cache (letzte 5 URLs, LRU)**; Return = Zeilenfenster, **max 500 Zeilen** je Call (Start default 1,
  `0` = Dateiende wie bei den Read-Tools), Disclosure **„lines X–Y of N — read on with startLine"**
  (Paul 2026-09-19).
- **R-W-2 ❌** Cache-Hit = **kein Refetch**: das Fenster kommt aus dem gecachten Snapshot — Paginierung
  ist stabil (kein anderer Inhalt zwischen zwei Calls derselben URL). Cache-Verdrängung (6. URL) = beim
  nächsten Call Refetch.
- **R-W-3 ❌** Fehlerpfad HTTP ≥ 400 → Status + **Snippet** (Anfang des Mark downs), nie der volle Body.
- **R-W-4 ❌** `webGet(url, path)`: eigenes Tool, lädt die Datei auf Disk, **kein Size-Limit** (Datei
  gehört auf Disk, nicht in den Kontext). Rückgabe = **HTTP-Status + Dateigröße + Disk-Pfad**, nie der
  Dateiinhalt.
- **R-W-5 ❌** `webGet` schreibt über **QualifiedPathValidator (absoluter Disk-Pfad) + WriteValidator**
  — dieselben Guards wie `diskWriteFile`.
- **R-W-6 ❌** `webGet` ist `isEditTool=true` → Sub-Agents/SearchAgent erhalten es nicht (bestehender
  Filter greift automatisch).
- **R-W-7 ❌** Ehrliche Fehler: HTTP-Fehler → Status-Code; Timeout/unbekannter Host → Fehlermeldung,
  keine Teilergebnisse, keine stillen „0 bytes"-Erfolge.

## BDD (Entwurf, hart beim Plan-Zyklus mit UC-IDs)

- GIVEN URL erstmals gefetcht, 3000 Zeilen WHEN Aufruf ohne Range THEN Zeilen 1–500, Disclosure „lines 1–500 of 3000".
- GIVEN URL im Cache WHEN Aufruf startLine=501 THEN Zeilen 501–1000 aus dem Cache, **kein** zweiter HTTP-Call.
- GIVEN 6. URL im Cache WHEN älteste verdrängt und erneut aufgerufen THEN Refetch, Fenster wieder 1–500.
- GIVEN HTTP 500 WHEN webFetchAsMarkdown THEN Status + Snippet (kein voller Body im Kontext).
- GIVEN gültige URL + absoluten Pfad WHEN webGet THEN Datei auf Disk, Return nennt Status + Größe + Pfad.
- GIVEN Pfad unter WriteValidator-Verbot WHEN webGet THEN Denial mit erlaubten Pfaden (wie diskWriteFile).
- GIVEN Server liefert 0 bytes WHEN webGet THEN ehrliche Meldung (Status + 0 bytes), kein stiller Erfolg.
- GIVEN webGet WHEN Tool-Filter des SearchAgent THEN Tool nicht verfügbar (isEditTool).
