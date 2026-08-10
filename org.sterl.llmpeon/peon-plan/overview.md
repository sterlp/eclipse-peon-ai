# Delta-Plan: Add Missing JS `link_open` Renderer to chat.html ✅ Done

## 1. Context

The Chat Markdown Links feature is fully implemented. JS link renderer + Java fallback search + problem display all verified. Build clean.

## 2. Affected File

| File | Location | Change |
|------|----------|--------|
| `/org.sterl.llmpeon/resources/chat/chat.html` | After line 238 (after `md` initialization closing `});`) | Insert `isWorkspaceLink` function + `link_open` rule (~10 lines) |

**No other files changed.** Java side is complete and verified.

## 3. Code to Insert

Insert after line 238 (`});` closing `md` initialization):

```javascript

        // Detect workspace links — false for URLs with schemes (https://, mailto:, etc.)
        function isWorkspaceLink(href) {
            return !href.includes('://');
        }

        // Convert workspace file links to open-in-editor: protocol
        md.renderer.rules.link_open = function(tokens, idx, options, env, self) {
            const token = tokens[idx];
            const hrefAttr = token.attrs.find(a => a[0] === 'href');
            if (hrefAttr && isWorkspaceLink(hrefAttr[1])) {
                hrefAttr[1] = 'open-in-editor:' + encodeURIComponent(hrefAttr[1]);
            }
            return self.renderToken(tokens, idx, options);
        };
```

## 4. What This Does

- **`isWorkspaceLink(href)`**: Returns `false` for any href containing `://` — covers `https://`, `http://`, `mailto:`, `tel:`, etc. All other links return `true` (workspace paths, relative paths, filenames).
- **`link_open` rule**: Intercepts every `<a>` tag rendered by markdown-it. Workspace links get converted to `open-in-editor:encoded-path`. External URLs pass through as standard `<a>` elements.
- **Delegates to default renderer** via `self.renderToken` — no custom HTML generation needed.

## 5. BDD Coverage (verified by existing Java implementation)

| Scenario | JS Behavior | Java Behavior |
|----------|-------------|---------------|
| R1: `/project/src/File.java` | → `open-in-editor:%2Fproject%2Fsrc%2FFile.java` | `resolveInEclipse()` opens editor |
| R2: `../docs/adr.md` | → `open-in-editor:%2E%2Edocs%2Fadr.md` | `resolveInEclipse()` opens editor |
| R3: `EclipseWorkspaceReadFileTool.java` | → `open-in-editor:EclipseWorkspaceReadFileTool.java` | Fallback search opens first hit |
| R4: `https://docs.example.com` | → `<a href="https://docs.example.com">` | No intercept, browser handles it |

## 6. Test Strategy

Manual verification only — load Eclipse, send a chat message with mixed links:
- `[workspace file](/org.sterl.llmpeon/src/.../EclipseUtil.java)` → should open editor
- `[external](https://www.google.com)` → should open browser
- `[relative](../README.md)` → should attempt editor open

## 7. Rules & Constraints

- **Do not touch** anything else in chat.html — diff click handler, copy-btn, appendMessage, etc. are untouched.
- **Keep it minimal** — 10 lines of JS, no new dependencies.
- **No changes** to `open-in-editor:` protocol format or Java-side handling.

## 8. Open Questions

None.
