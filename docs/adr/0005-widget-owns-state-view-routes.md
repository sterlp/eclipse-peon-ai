# ADR 0005 — Widgets own their state; AIChatView only routes monitor events

**Status:** Accepted

**Context:** The first cut of [Session Token Usage](../token-usage.md) built the header strip, the
tools-menu popup logic, *and* held the cumulative session `TokenStats` directly inside
`AIChatView.buildHeaderToolbar`/`showToolsMenu`/`updateTokenHeader`. That is a responsibility bleed:
`AIChatView` is the `AiMonitor` sink for a chat session — it should not know how the header is laid
out or own the token-accounting state. The codebase already establishes the right pattern: the other
chat controls (`UserInputWidget`, `ActionsBarWidget`, `StatusLineWidget`, `ChatMarkdownWidget`) are
self-contained `Composite`s under `parts/widget/`, constructed with callbacks/suppliers, to which the
view merely forwards events (`onChatResponse → chatHistory`, `refreshStatusLine → statusLine`).

**Decision:**
- Extract the header into two self-contained widgets under `parts/widget/`:
  - `TokenHeaderWidget` — owns the session `TokenStats` and the readout `Label`; exposes
    `addUsage(TokenUsage)` and encapsulates formatting + hide-while-zero.
  - `HeaderBarWidget` — the white header strip; owns the flat hammer button and the tools-menu
    popup, hosts a `TokenHeaderWidget`, and takes `Supplier<String>` (active agent name) +
    `Supplier<List<ToolStatus>>` for the menu. Exposes `addTokenUsage(TokenUsage)` (delegates).
- `AIChatView` keeps only a `HeaderBarWidget headerBar` reference and, in its `onTokenUsage`
  monitor callback, forwards to `headerBar.addTokenUsage(...)` on the UI thread — the same
  event-routing role it plays for every other widget. It no longer builds the header, owns the menu,
  or holds token state.

**Consequences:** The view stays thin and the header is independently testable/reusable — important
for the future detached-agents work (R5–R7 in the story). The session `TokenStats` now lives in
`TokenHeaderWidget`, whose lifecycle equals the view's, so "never reset until the view closes"
(R3) still holds. General principle, not just this feature: new UI state/logic belongs in a
`parts/widget/*` component, never inlined into the view.
