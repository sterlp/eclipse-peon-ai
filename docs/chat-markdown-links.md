# Chat Markdown Links — Klickbare Dateiverweise

## Business Requirements

- Klickbare Dateiverweise im Chat öffnen die Datei im Eclipse Editor — ohne den Agenten dazu zu bringen, ein Tool aufzurufen.
- Externe Links (`https://...`) öffnen im Browser (Standard-Verhalten).

## Business Rules

### R1 — Workspace-Absolute Pfade ✅
Ein Link der mit `/` beginnt und eine Workspace-Datei auflöst, öffnet im Editor.

- **GIVEN** `[code](/llmpeon-core/src/.../EclipseWorkspaceReadFileTool.java)` **WHEN** der User darauf klickt
  **THEN** die Datei öffnet sich im Eclipse Editor → `workspaceAbsoluteLinkOpensEditor`

### R2 — Relative Doc-Links ✅
Ein relativer Link (z.B. `adr/0026-...` oder `../user-question-tool-design.md`) wird als Workspace-Pfad behandelt.

- **GIVEN** `[ADR-0026](adr/0026-extract-question-shell-approval.md)` in `/llmpeon-parent/docs/user-question-tool-design.md`
  **WHEN** der User darauf klickt **THEN** `/llmpeon-parent/docs/adr/0026-extract-question-shell-approval.md` öffnet sich im Editor
  → `relativeDocLinkOpensEditor`

### R3 — Fallback-Suche bei Nicht-Fund ✅
Wenn `EclipseUtil.resolveInEclipse()` nichts findet, wird eine Workspace-Suche nach dem Dateinamen gestartet.
Das erste Treffer-File wird geöffnet.

- **GIVEN** ein Pfad wie `org/sterl/llmpeon/parts/tools/EclipseWorkspaceReadFileTool.java` (ohne leading `/project/`)
  **WHEN** der direkte Resolve fehlschlägt **THEN** eine Suche nach `*EclipseWorkspaceReadFileTool.java` wird gestartet
  **THEN** das erste Ergebnis öffnet sich im Editor
  → `fallbackSearchOpensFirstHit`
- **GIVEN** kein Workspace-File passt **WHEN** die Suche leer bleibt **THEN** eine Problem-Nachricht erscheint
  → `notFoundShowsProblem`

### R4 — Externe URLs ✅
`https://...`, `http://...`, `mailto:` bleiben Standard-Links — öffnen im Browser.

- **GIVEN** `[LangChain4j](https://docs.langchain4j.dev/)` **WHEN** der User darauf klickt
  **THEN** der Browser öffnet die URL → `externalUrlOpensBrowser`

## Technical Approach

### JavaScript — markdown-it Link-Renderer
```js
md.linkify.set({ fuzzyLink: false });
md.renderer.rules.link_open = function (tokens, idx) {
    const href = tokens[idx].attrs.find(a => a[0] === 'href')[1];
    if (isWorkspaceLink(href)) {
        tokens[idx].attrs[0][1] = 'open-in-editor:' + encodeURIComponent(href);
        tokens[idx].attrs.push(['data-workspace-link', 'true']);
    }
    return defaultLinkOpen(tokens, idx, options);
};
```

`isWorkspaceLink(href)`: True wenn href mit `/` beginnt, `.md`/`.java`/`.xml` etc. enthält,
oder ein relativer Pfad ist (enthält `/` aber kein `://`).

### Java — LocationListener (ergänzung)
Existiert bereits in `ChatMarkdownWidget` (Lines 68-82). Ergänzung:
- Fallback-Suche via `EclipseUtil.searchWorkspaceFiles()` wenn `resolveInEclipse()` leer
- `onProblem`-Status bei keinem Treffer

### Scope der Änderung
- `chat.html` — markdown-it Link-Renderer
- `ChatMarkdownWidget.java` — LocationListener Erweiterung (Fallback + Problem)
- Keine neue UI-Komponente — bestehende Infrastruktur wiederverwenden

## Non-Goals

- **Line-Number-Jump** (`file.java#L42`) — später, wenn Bedarf
- **Projekt-spezifische Base-URL** — nicht nötig, Workspace ist die Root
