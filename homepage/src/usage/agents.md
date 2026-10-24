---
title: Agent Team
description: The Jon team in the header — status, per-slave compact buttons, and what Compact vs Clear does
---

# Agent Team

When **Jon** (Peon-PO) is the active agent, the header shows his team with each member's name and current context size:

```
↑12k ↓8k  ·  Da Boss (12k) · Da Thinka (8k) · Da Mek (45k) · Da Dok (3k)
```

| Member | Role |
|--------|------|
| **Da Boss** | Jon — the orchestrator (the active agent) |
| **Da Thinka** | Peon-Plan — the planner |
| **Da Mek** | Peon-Dev — the implementer |
| **Da Dok** | Peon-Review — the reviewer |

The number in parentheses is the agent's **current context size** in tokens. A **🟢** marks the member that is working right now, a **🟡** the member that is currently **compacting** its context. If Jon delegates to a worker, only that worker glows — Jon stays calm:

```
↑12k ↓8k  ·  Da Boss (12k) · Da Thinka (8k) · 🟢 Da Mek (45k) · Da Dok (3k)
↑12k ↓8k  ·  Da Boss (12k) · Da Thinka (8k) · 🟡 Da Mek (45k) · Da Dok (3k)   ← compacting
```

::: tip
With a non-Jon agent active (e.g. plain Peon-Dev), the team roster is not shown — only the session token readout remains in the header.
:::

## Compacting a single agent

Each of the three worker rows (**Da Thinka**, **Da Mek**, **Da Dok**) carries a small **compact icon button** (tooltip: `Compact Da X`). Clicking it compresses **exactly that one agent's** context:

- While it runs the chat shows the usual **waiting-for-AI** working state and that worker's row shows **🟡**. The compressor now **streams live** into the chat: its real tokens count in the header, and with *show realtime AI response* enabled you watch the summary being written. Exactly one line closes the compact — **`🗜 Peon-Dev compacted: 61 → 2 messages, ~114k → ~2k tokens (4s, 09:40) (1200 chars)`** — the compacting agent, its messages before → after, the estimated tokens before → after, the duration with the wall-clock time, and the summary size. The status line also reports the result with its numbers — **`Compacted Da Mek: compressed 61 messages ~114k → input ~40k, result 2k, stage: tool results 6000, request 80211 (provider)`** — and the roster shows the reduced context size.
- If the agent has fewer than 3 messages (right after a compact, exactly 2 remain), nothing is sent to the model — the status line simply reports **`Nothing to compact`**, and no 🟡 appears.
- The button is **disabled** while that agent is working (🟢) or compacting (🟡), or while any turn is in flight.

**Da Boss has no such button** — Jon is the active agent and uses the regular **Compact** button in the action bar.

## Compact = only that agent

Compact never cascades. Whatever you compact, only that one agent's memory is compressed:

- **Jon's Compact** (action bar button) compresses **Jon alone** — Da Thinka, Da Mek and Da Dok are untouched.
- **A worker's compact button** compresses **that worker alone**.
- The workers also manage their own memory: each one compacts itself automatically when its own context grows large, independent of Jon.

## What the chat shows after a compact

When the agent **you are talking to** finishes a successful compact — its own action-bar **Compact** button or an automatic compact during a turn — the chat rebuilds from the compacted memory: the new summary stays visible, the old transcript leaves the view (condensed, not lost — the agent still works from it).

An automatic compact that runs **inside** a turn waits a moment with that rebuild — until your question is part of the compacted memory. What you just asked stays on screen instead of disappearing with the old transcript.

A **worker's** compact never rebuilds the chat: that memory is not what you are reading. Feedback there is the compressor's live stream, the one `🗜 <agent> compacted: …` line, the status line (`Compacted Da Mek: …`) and the row's **🟡**.

A **failed** compact (the compressor answered with nothing) leaves the chat exactly as it was — nothing is thrown away — and reports a problem line instead.

## Clear removes the whole team

Unlike Compact, **Clear** in Jon mode does cascade: it resets **Jon and all three workers** at once. This is a deliberate asymmetry:

| Action | Scope |
|--------|-------|
| Compact (Jon, action bar) | Jon only |
| Compact (worker button) | That one worker only |
| Clear (Jon mode) | Jon **+** Da Thinka, Da Mek **and** Da Dok |

::: tip
Use **Clear** to start a fresh session; use the **per-worker compact buttons** to free up one worker's context while the team keeps working.
:::

## Verification

1. Activate Jon and watch the header — all four members appear with context sizes.
2. Start a task that delegates to a worker — the worker's row glows 🟢 while working.
3. Hover over a worker's compact icon — the tooltip reads `Compact Da X`.
4. Click it — the compressor's tokens stream into the chat (token counter, and the text with *show realtime AI response*), exactly one `🗜 <agent> compacted: …` line appears, the status line shows `Compacted Da X: compressed N messages ~Xk → input ~Yk, result Zk, stage: …, request 80211 (provider) or request n/a (no provider value)`, and only that worker's context size drops.

Want the full picture of how Jon and his team work together? See [Jon & Team](/setup/peon-po).
