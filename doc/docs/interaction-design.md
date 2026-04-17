# Peon AI — Interaction Design

## Layout (top → bottom)

```
┌──────────────────────────────────────────────────────┐
│  1. Chat History (ChatMarkdownWidget)                │
│     fills all available vertical space               │
│                                                      │
├─────────────────────────────────────────────────────────┤
│  2+3+4. Input Block (single SWT.BORDER, white bg)       │
│  ┌───────────────────────────────────────────────────┐  │
│  │  2. User Input (ChatWidget)                       │  │
│  │     [📎 file.java ×] [📎 pom.xml ×]  (hidden)     │  │  file chips
│  │     StyledText (auto-grow, max 7 rows)       [🎤] │  │  mic: hidden unless voice configured
│  ├───────────────────────────────────────────────────┤  │
│  │  3. Action Bar (ActionsBarWidget)                 │  │
│  │     [Plan▾] [Model▾] [🧠 Think] [Clear]      [▶/■]│  │  send/stop pinned right (GridLayout 2)
│  │     [Start Impl.] [☐ autonomous]  (conditional)  │  │
│  ├───────────────────────────────────────────────────┤  │
│  │  4. Status Bar (StatusLineWidget)                 │  │
│  │     [📌 ProjectName] [⚡ 2 skills] [AGENTS.md]    │  │
│  │     [file.java] [MCP on/off] [Compact 45K/100K]  │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## Section Details

### 2 — User Input (ChatWidget)

- **StyledText**: auto-grows up to 7 rows, then scrolls. Enter = newline, `Ctrl/Cmd+Enter` = send.
- **File chips bar**: hidden until files are attached. Each chip: file icon + name + `×`. `+` button opens workspace file picker.
- **Mic button**: flat ToolBar/ToolItem on the right side of the text area. Transparent/white background. Turns red while recording. Hidden unless voice is configured.
- **No outer border on the composite** — the StyledText itself provides the focus visual.

### 3 — Action Bar (ActionsBarWidget)

Sections 2, 3 and 4 share the same background color so they form one cohesive input block.

Layout: `GridLayout(2)` — left cell is a wrapping `RowLayout` composite, right cell is `SendOrStopButton` pinned `SWT.RIGHT`.

| Position | Control | Notes |
|----------|---------|-------|
| Left | **Mode selector** (`Plan`, `Dev`, `Agent`) | combo |
| Left | **Model selector** | combo, ~200px |
| Left | **🧠 Think toggle** | on/off for extended thinking; initialised from the "Supports Thinking" preference; toggling is session-only and is not written back to preferences |
| Left | **Clear** | clears conversation history |
| Right (pinned) | **Send / Stop** | always rightmost; icon swaps while working |
| Conditional | **Start Impl.** | visible in Plan/Agent mode when AI has replied |
| Conditional | **☐ autonomous** | checkbox, visible in Agent mode only |

### 4 — Status Bar (StatusLineWidget)

Wrapping `RowLayout` so controls reflow on narrow views.

| Control | Type | Notes |
|---------|------|-------|
| **📌 Project** | Toggle button (pin icon) | pin/unpin project; shows project name; disabled if no project |
| **Selected file** | Label | currently active file in editor |
| **⚡ N skills** | Toggle button | on/off; enables/disables skill loading; shows count |
| **AGENTS.md** | Label/icon | shown only when an AGENTS.md is found |
| **MCP on/off** | Toggle button | enables/disables MCP tool servers |
| **Compact N/M** | Push button | color coding: yellow ≥70%, red ≥88% |

## Visual Unification

Sections 2, 3 and 4 are children of a single `inputBlock` composite (`SWT.BORDER`, `SWT.COLOR_LIST_BACKGROUND`, `INHERIT_FORCE`). This gives the entire input area one outer border and a consistent white background. No internal borders between sections — dividing lines between sections are optional thin separators at most.
