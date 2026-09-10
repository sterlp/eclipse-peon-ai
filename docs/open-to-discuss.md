## UI

### Status-Display nach `compactSession`-Tool inkonsistent 🚧 (beobachten — User 2026-09-10)

**Observation:** Nach dem `compactSession`-Tool zeigt der Header „Da Boss" mit grünem Ball
(**Stop aktiv**), aber **kein „Warte auf …"-Working-Hinweis** mehr — obwohl der Agent danach
noch arbeitet (die Queued-Message-Bestätigung des User kam in diesem Zustand).

**Could be:** Der Compact-Tool-Call hängt das Working-Status-Update ab (Sub-Agent-Timing/Status-
Widget zählt den Turn als fertig, während der eigentliche Agent-Run weiterläuft) — passt
thematisch zum MVP-Neubau [agenten-status-im-header-mvp-plan.md](agenten-status-im-header-mvp-plan.md)
(Pull/MVC statt Observer) und zur Stop-Fenster-Erkenntnis (Clobber-Race im finally).

**Verwandt:** „Senden-während-Compress Memory-Race" (Bug-Hunt-Backlog) — andere Facette
(Memory-Race vs. rein optisches Status-Display).

---
# Open to Discuss — ambiguous items, not clear bugs or features yet

Items here are **not committed decisions**. They are observations that could be problems, could be features, or could stay as-is. Reviewed at end of a cycle; discarded or moved to proper docs/ADRs when resolved.

## UI

### Compact Session does not reset the chat view 🚧 (beobachten — User 2026-09-10)

**Observation:** `compactSession` clears the agent's memory and re-injects standing orders, but the UI chat view (`AIChatView`) is **not reset** — old messages remain visible in the DOM.

**Could be a problem:** On very long sessions the browser DOM grows unbounded (old messages never removed). Potential memory/CPU impact in the browser.

**Could be a feature:** The user keeps visual context of what happened before compaction. A "history scroll" is nicer than a hard reset.

**User-Leitidee (2026-09-10, NICHT umgesetzt — beobachten):** „Ich sehe immer, was der
Agent sieht" — wenn der **aktive** Agent compacted, sollte der View geleert werden (Chat
fängt von vorne an); wenn aber ein **Sklave/anderer Agent** compacted, darf der View NICHT
resetet werden (nur der angezeigte Agent zählt). Keine einfache Lösung bekannt (View müsste
aktivem Agenten+dessen Compact-Events folgen) → bewusst nichts tun, Item bleibt als Beobachtung.

**Open questions (für später):**
- Should we limit visible messages to the post-compaction window (virtualize the DOM)?
- Should we keep pre-compaction messages but collapse/archive them visually?
- Or is the current behavior fine (users rarely run sessions long enough to hit browser limits)?

**Context:** `StreamingBridge` + `ToolService.executeLoop` + `AIChatView.onChatResponse`. Compact runs via `CompactSessionTool` → `ToolLoopRequest.clearMemory()` → memory cleared, standing orders re-injected. UI side: `AIChatView` never removes old DOM nodes.
