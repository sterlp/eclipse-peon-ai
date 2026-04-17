# Peon AI — Interaction Design

## Layout (top → bottom)

```
┌──────────────────────────────────────────────────────┐
│  1. Chat History (ChatMarkdownWidget)                │
│     fills all available vertical space               │
│                                                      │
├──────────────────────────────────────────────────────┤
│  2. User Input (UserInputWidget)                     │
│     ┌────────────────────────────────────────────┐   │
│     │ [📎 file.java ×] [📎 pom.xml ×]  (hidden)   │   │  file chips, hidden when empty
│     ├────────────────────────────────────────────┤   │
│     │ StyledText (auto-grow, max 7 rows)    [🎤] │   │  mic: flat, white bg
│     └────────────────────────────────────────────┘   │
│                                                      │
├──────────────────────────────────────────────────────┤
│  3. Action Bar (white bg, visually unified with 2)   │
│                                                      │
│    [Plan▾]  [Model Name▾]  [🧠]  [Clear]    [▶/■]    │  send/stop pinned right
│    [Start Impl.]  [☐ autonomous]  (conditional)      │
│                                                      │
├──────────────────────────────────────────────────────┤
│  4. Status Bar (white bg)                            │
│    [📌 ProjectName]  [file.java]  [⚡ 2 skills]       │
│    [AGENTS.md]  [MCP on/off]  [Compact 45K/100K]     │
└──────────────────────────────────────────────────────┘
```

## Section Details

### 2 — User Input Widget

- **StyledText**: auto-grows up to 7 rows, then scrolls. Enter = newline, `Ctrl/Cmd+Enter` = `send`.
- **File chips bar**: hidden until files are attached. Each chip: file icon + name + `×`. `+` button opens workspace file picker.
- **Mic button**: flat ToolBar/ToolItem on the right side of the text area. Transparent/white background. Turns red while recording. Hidden unless voice is configured.
- **No outer border on the composite** — the StyledText itself provides the focus visual. The whole section (2 + 3) reads as one unified input block.

### 3 — Action Bar

Background matches section 2 (white / system bg) so they read as one block.

| Position | Control | Notes |
|----------|---------|-------|
| Left | **Mode selector** (`Plan`, `Dev`, `Agent`) | combo or segmented button |
| Left | **Model selector** | combo, ~200px |
| Left | **🧠 Think toggle** | on/off for extended thinking; new feature |
| Left | **Clear** | clears conversation history |
| Right (pinned) | **Send / Stop** | always rightmost; icon swaps on lock |
| Conditional | **Start Impl.** | visible in Plan/Agent mode when AI has replied |
| Conditional | **☐ autonomous** | checkbox, visible in Agent mode only |

Layout: `GridLayout(2)` — left cell is a `RowLayout` composite that wraps, right cell is `SendOrStopButton` aligned `SWT.RIGHT`.

### 4 — Status Bar

Separate composite below Action Bar. RowLayout, wrapping. White / system bg.

| Control | Type | Notes |
|---------|------|-------|
| **📌 Project** | Toggle button (pin icon) | pin/unpin project; shows project name; hidden if no project |
| **Selected file** | Label | currently active file in editor |
| **⚡ N skills** | Toggle button | on/off; enables/disables skill loading; shows count |
| **AGENTS.md** | Label/icon | shown only when an AGENTS.md is found |
| **MCP on/off** | Toggle button | moved here from Action Bar |
| **Compact N/M** | Push button | moved here from Action Bar; color coding (yellow 70%, red 88%) |

## Visual Unification

Sections 2, 3 and 4 form one cohesive input block by sharing the same background color (white on light theme / system widget color on dark theme). No visual borders between them — only the StyledText itself has a focused border. Thin horizontal separator lines (1px) may optionally mark section boundaries.

## New Features Required

| Feature | Where | Description |
|---------|-------|-------------|
| **🧠 Think toggle** | Action Bar | Enable Claude extended thinking for the next request. Stored in LlmConfig or per-request flag. |
| **Skills on/off toggle** | Status Bar | `SkillService.setEnabled(boolean)`. Button shows count when on, grayed when off. |

## Open Questions

- **Clear button**: Action Bar (row 3) is the current proposal. Could also live in Status Bar next to Compact if space is tight.
- **Start Impl. / autonomous**: These only appear in Plan/Agent mode. They currently go on a second visual row inside the Action Bar. Could instead be folded into a context menu on the Mode selector.
- **Compact button placement**: Moving to Status Bar makes sense since it reflects state (token usage). Action Bar was pragmatic; Status Bar is more informative.

## Affected Files

| File | Change |
|------|--------|
| `…/parts/widget/ChatWidget.java` | Rename concept to UserInputWidget; remove StatusLineWidget from inside it; keep file chips + mic |
| `…/parts/widget/ActionsBarWidget.java` | New layout (GridLayout 2), white bg, add Think toggle, move MCP+Compact out |
| `…/parts/widget/StatusLineWidget.java` | Add MCP toggle, Compact button, Skills toggle; remove fixed non-wrapping layout |
| `…/parts/widget/SendOrStopButton.java` | No change — already extracted |
| `…/parts/AIChatView.java` | Reorder composites (input before action bar), rewire status line calls |
