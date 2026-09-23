# Web-Tools Architektur

> **Status:** ❌   **Fachdoc:** [web-tools.md](web-tools.md)   **ADRs:** — (keine besondere Wahl nötig;
> Cache = schlichte LRU im Tool-Instanz-Feld)

## Verantwortung & Abgrenzung

- **Zweck:** HTTP-GET-Inhalte agentenfähig machen — `webFetchAsMarkdown` (kleine Ausschnitte in den
  Kontext, paginiert über Mini-Cache) und `webGet` (Dateien auf Disk, Metadaten im Kontext).
- **In-Scope:** GET, Markdown-Konvertierung (Flexmark, bestehend), LRU-Cache (5 URLs), Zeilenfenster,
  Guard-geprüfte Disk-Downloads.
- **Out-of-Scope:** Auth/Custom-Header, POST/PUT, Redirect-Politik über bestehendes Verhalten hinaus,
  beliebiges Pagination-Backend.
- **Keine Überschneidung mit:** Read-Tools (lesesen lokale Dateien — `webFetch` liest nur via HTTP),
  Shell-Tool (keine Pipes), DiskFileWriteTool (webGet ist kein generischer Write — nur URL→Datei).

## Klassen & Abhängigkeiten

| Klasse (core) | Rolle | isEditTool |
|---|---|---|
| `WebFetchTool` (bestehend) | fetch→Markdown→Cache→Zeilenfenster; Cache als Instanz-Feld (LRU 5) | nein |
| `WebGetTool` (NEU) | URL→Disk-Download; `QualifiedPathValidator.requireQualifiedDisk` + `validateWrite`; Return = Status/Größe/Pfad; **gated hinter `diskToolsEnabled`** (R-WEB-8) | **ja** (Sub-Agent-Filter greift) |

| Richtung | Gegenüber | Wofür |
|---|---|---|
| nutzt | `java.net.http.HttpClient` | GET (bestehender Code in WebFetchTool) |
| nutzt | `QualifiedPathValidator`, `AbstractTool.validateWrite` | WebGet-Guards (R-WEB-5) |
| nutzt | `Flexmark` (bestehend) | HTML→Markdown |
| genutzt von | `ToolService`/`SharedToolsComponent` | Registrierung (WebGetTool zusätzlich registrieren) |

## Architektur-Check
- [x] Keine Komponenten-Zyklen
- [x] Klare Verantwortung (Fetch vs. Download getrennt — „one behaviour, one implementation")
- [x] Enkapsulation: Cache lebt nur im `WebFetchTool`, keine globale Sichtbarkeit
- [x] Composable: beides sind normale `@Tool`-Klassen im core
