# Peon AI — Interaction Design

## Layout (top → bottom)

```
┌──────────────────────────────────────────────────────┐
│  1. Chat History (ChatMarkdownWidget)                │
│     fills all available vertical space               │
│                                                      │
├──────────────────────────────────────────────────────┤
│  2. User Input (UserInputWidget)                     │
│  ┌─────────────────────────────────────────────────┐ │
│  │ [📎 file.java ×] [📎 pom.xml ×]  (hidden)       │ │
│  ├─────────────────────────────────────────────────┤ │
│  │ Text (auto-grow, min 2, max 7 rows)       [🎤] │ │
│  │                                           [▶/■] │ │
│  └─────────────────────────────────────────────────┘ │
│                                                      │
├──────────────────────────────────────────────────────┤
│  3. Action Bar (ActionsBarWidget)                    │
│    [Plan▾]  [Model Name▾]  [🧠 Think]  [Clear]       │
│    [Start Impl.]  [☐ autonomous]  (conditional)      │
│                                                      │
├──────────────────────────────────────────────────────┤
│  4. Status Bar (StatusLineWidget)                    │
│    [📌 ProjectName]  [file.java]  [⚡ 2 skills]       │
│    [AGENTS.md]  [MCP on/off]  [Compact 45K/100K]     │
└──────────────────────────────────────────────────────┘
```

## Section Details

### 2 — User Input (UserInputWidget)

- **File chips bar**: hidden until files are attached. Each chip: file icon + name + `×`. `+` button opens workspace file picker.
- **StyledText**: auto-grows, minimum 2 rows, maximum 7 rows (then scrolls). `Enter` = newline, `Ctrl/Cmd+Enter` = send.
- **Right column**: fixed-width column to the right of the StyledText, vertically filling the row.
  - **Mic button** `[🎤]` at top: flat ToolItem. Turns red while recording. Hidden unless voice is configured.
  - **Send/Stop button** `[▶/■]` at bottom: always visible. Shows send icon when idle, stop icon while a request is in flight.
- **Layout**: `GridLayout(2)` in the text row — StyledText fills, right column holds mic (top) and send/stop (bottom).

### 3 — Action Bar (ActionsBarWidget)

`RowLayout` (wrapping). Shares background with section 2 so both read as one unified input block.

| Control | Notes |
|---------|-------|
| **Mode selector** (`Plan`, `Dev`, `Agent`) | combo |
| **Model selector** | combo |
| **🧠 Think toggle** | on/off for extended thinking; default from "Supports Thinking" preference; session-only, not written back |
| **Clear** | clears conversation history |
| **Start Impl.** *(conditional)* | visible in Plan/Agent mode when AI has replied |
| **☐ autonomous** *(conditional)* | checkbox, visible in Agent mode only |

### 4 — Status Bar (StatusLineWidget)

`RowLayout` (wrapping). Shares background with sections 2 and 3.

| Control | Type | Notes |
|---------|------|-------|
| **📌 Project** | Toggle button | pin/unpin project; shows project name; hidden if no project |
| **Selected file** | Label | currently active file in editor |
| **⚡ N skills** | Toggle button | on/off; enables/disables skill loading; shows count |
| **AGENTS.md** | Label/icon | shown only when an AGENTS.md is found |
| **MCP on/off** | Toggle button | enables/disables MCP tool servers |
| **Compact N/M** | Push button | color coding: yellow ≥ 70 %, red ≥ 88 % |

## Visual Unification

Sections 2, 3 and 4 are children of a single `inputBlock` composite (`SWT.BORDER`). This gives the entire input area one outer border. No internal borders between sections.
