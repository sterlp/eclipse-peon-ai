---
title: Agents
description: Built-in agents available in Eclipse Peon AI
---

# Agents

Eclipse Peon AI ships with several built-in agents, each designed for a specific role in your workflow.

| Agent | Role | Details |
|-------|------|---------|
| **Jon** | Autonomous orchestrator | [Jon & Team](./peon-po) — designs features in docs, then orchestrates Plan and Dev to implement them. |
| **Dev** | Implementer | Reads plans and modifies code. The default agent for ad-hoc development tasks. |
| **Plan** | Planner | Read-only exploration and plan creation. Writes structured plans to `peon-plan/overview.md`. |
| **Scaffold** | Agent & skill creator | Create and manage custom agents, skills, and commands with natural language — [details](./scaffold-agent). |

## Handoff between agents

Any agent can hand off work to another via the **Handoff** button — see [Agent Handoff](./agent-handoff).

## Custom agents

Define your own agents with custom prompts and tool allowlists — see [Custom Agents](./custom-agents).


## Resilience — Automatic API Retry

All agents automatically retry transient API failures (network blips, timeouts, 5xx errors) without losing the current conversation. The retry budget grows with each successful response — the more work already invested, the more patience the agent has. Cancel (Stop) is always respected immediately and never retried.