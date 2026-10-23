# Web-Tools Architektur

> **Status:** ✅ (webSearch 2026-10-23, Nachtzyklus)   **Fachdoc:** [web-tools.md](web-tools.md)   **ADRs:** [0074](../../docs/adr/adr-0074-websearch-eigener-ddg-client.md) (eigener DDG-Client statt Community-Modul)

## Verantwortung & Abgrenzung

- **Zweck:** HTTP-GET-Inhalte agentenfähig machen — `webFetchAsMarkdown` (kleine Ausschnitte in den
  Kontext, paginiert über Mini-Cache), `webGet` (Dateien auf Disk, Metadaten im Kontext) und
  `webSearch` (Web-Suche, Trefferliste in den Kontext).
- **In-Scope:** GET, Markdown-Konvertierung (Flexmark, bestehend), LRU-Cache (16 URLs), Zeilenfenster,
  Guard-geprüfte Disk-Downloads, DuckDuckGo-Suche über das langchain4j `WebSearchEngine`-Interface.
- **Out-of-Scope:** Auth/Custom-Header, POST/PUT, Redirect-Politik über bestehendes Verhalten hinaus,
  beliebiges Pagination-Backend.
- **Keine Überschneidung mit:** Read-Tools (lesesen lokale Dateien — `webFetch` liest nur via HTTP),
  Shell-Tool (keine Pipes), DiskFileWriteTool (webGet ist kein generischer Write — nur URL→Datei).

## Klassen & Abhängigkeiten

| Klasse (core) | Rolle | isEditTool |
|---|---|---|
| `WebFetchTool` (bestehend) | fetch→Markdown→Cache→Zeilenfenster; Cache als Instanz-Feld (LRU 16) | nein |
| `WebGetTool` (bestehend) | URL→Disk-Download; `QualifiedPathValidator.requireQualifiedDisk` + `validateWrite`; Return = Status/Größe/Pfad; **gated hinter `diskToolsEnabled`** (R-WEB-8) | **ja** (Sub-Agent-Filter greift) |
| `WebSearchTool` (R-WEB-10) | Query→DuckDuckGo→Trefferliste (Titel/URL/Snippet, max 8, ehrliche Disclosure) in den Kontext; **besitzt den Such-Vorgang** — der Page-Inhalt bleibt draußen, Nachlesen via WebFetchTool | nein |

| Richtung | Gegenüber | Wofür |
|---|---|---|
| nutzt | `java.net.http.HttpClient` | GET (bestehender Code in WebFetchTool) |
| nutzt | `QualifiedPathValidator`, `AbstractTool.validateWrite` | WebGet-Guards (R-WEB-5) |
| nutzt | `Flexmark` (bestehend) | HTML→Markdown |
| nutzt | eigener dünner DDG-HTTP-Client, implementiert das langchain4j `WebSearchEngine`-Interface | DuckDuckGo-Suche (R-WEB-10) — kein Community-Modul (ADR-0074: ehrliche Fehler/Leerergebnisse dort strukturell nicht abbildbar; jsoup 1.15.4 bereits vorhanden) |
| genutzt von | `ToolService`/`SharedToolsComponent` | Registrierung (WebSearchTool an allen Agenten, R-WEB-11) |

## Architektur-Check
- [x] Keine Komponenten-Zyklen
- [x] Klare Verantwortung (Fetch vs. Download getrennt — „one behaviour, one implementation")
- [x] Enkapsulation: Cache lebt nur im `WebFetchTool`, keine globale Sichtbarkeit
- [x] Composable: beides sind normale `@Tool`-Klassen im core
