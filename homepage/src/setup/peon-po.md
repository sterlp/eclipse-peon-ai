---
title: Jon & Team
description: Autonomous feature development with the Jon orchestrator
---

# Jon & Team

**Jon** is an autonomous orchestrator that designs features in your project's `docs/`, plans the implementation, and drives the build — all without you babysitting the process.

## How it works

1. **Describe a feature** — tell Jon what you want. He designs it directly in your project's `docs/` folder (business rules, BDD scenarios, ADRs).
2. **Review the design** — Jon acts as a skeptical guardian of the docs, keeping them coherent and always representing the desired state (SOLL) vs current state (IST).
3. **Plan & Build** — once you're happy with the design, Jon orchestrates his team:
   - **Plan** explores your project and writes a structured implementation plan.
   - **Dev** reads the plan and implements the changes.
   - **Review** checks the result independently — plan, code and docs against each other — and reports deviations without fixing them.
4. **Review & Learn** — Jon reviews the built code against the plan and docs, then captures learnings for next time.

```
User: "Add MCP support for web search"
  → Jon designs it in docs/mcp-web-search.md
  → Jon plans the implementation
  → Jon implements via his team
  → Jon reviews and closes the cycle
```

## Key principles

- **Docs-first:** Features are designed in `docs/` before any code is written.
- **Autonomous loop:** Plan → Review → Build → Retro — Jon drives the cycle with his team.
- **Skeptical guardian:** Jon keeps the docs honest, always distinguishing SOLL from IST.
- **Non-blocking:** Chat stays active while Jon works. Messages are queued and processed in order.

::: tip
Jon is opt-in. Dev remains the default agent for quick, ad-hoc tasks.
:::

## Status indicators

When Jon is active, the header shows the status of his team members:
- **Jon** — the orchestrator
- **Plan** — the planner
- **Dev** — the implementer
- **Review** — the independent reviewer

Each shows a status indicator (idle / working / done) so you can see who's doing what.
