# 0069 — One MCP connection per server config, shared by all registries

> **Status:** Accepted (2026-10-18)
> **Supersedes:** nothing · **Related:** ADR-0067 (two registry families), ADR-0068 (codegraph)

## Context

ADR-0067 introduced two agent-facing tool registries (the shared `ToolService` and Peon-PO's own)
so that Jon takes part in the dynamic tool families. ADR-0068 reuses the same pair for the Codegraph
built-in. Both connection services therefore call `connectMcp(...)` **per registry**:

- `McpConnectionService.connectServers()` (`for (ToolService registry : toolServices)`)
- `CodegraphConnectionService.apply()` (`for (var registry : registries)`)

`ToolService.connectMcp(group, config)` creates its **own** `McpService` per call, and `McpService`
spawns one `McpClient`/transport per configured server. Consequence: one registry = one process
chain. With two registries a single Codegraph server config produced **two** `codegraph serve --mcp`
processes on the same `workDir` (observed 2026-10-18, spawns 0.3 s apart), and a user MCP server
would have been spawned twice as well.

That contradicts our own rule R-CG-4 ("exactly one live `serve --mcp` per workDir") and is wrong
for every stdio server: two processes race on the same state (SQLite/FTS index), double the RAM and
log noise, and for HTTP servers double the connection/handshake attempts.

## Decision

**One `McpService` per server configuration, owned by the connection service, adopted by every
registry.**

- The connection service (`McpConnectionService`, `CodegraphConnectionService`) creates and
  **owns** the `McpService` instance: it calls `connect()` and is the only place that may close it.
- A registry joins via `ToolService.adoptMcpGroup(group, service)`: the group's tool specifications
  become visible in that registry's tool loop, but the registry does **not** own the connection.
- `ToolService.disconnectMcp(group)` on an adopted group only removes the map entry / refreshes the
  specs — closing the connection stays with the owner. The owner tears the connection down last
  (after detaching all registries), which makes the order irrelevant for correctness.
- Registries still execute tool calls through the shared client; a call from either registry reaches
  the one running process.

## Consequences

- Exactly one process per configured server, regardless of how many registries exist — the invariant
  lives in the ownership, not in a call-site loop.
- `ToolService` becomes an **adopt-only** registry (Clean Break): `connectMcp(...)` and
  `mcpInstructions(...)` are gone — a registry cannot create or close an `McpService` at all. It
  holds at most one service per group name; re-adopting replaces the entry without closing it.
- The failure path stays symmetric: nothing is adopted before `connect()` succeeded.
  `CodegraphConnectionService` also closes the failed instance explicitly; on the user path the new
  instance is never assigned (`liveService` stays `null`) and `McpService.connect()` closes its own
  clients (all-or-nothing). No registry ever sees a half-connected group.
- Tests pin the invariant at the seam that produces it: the connection service calls `connect()`
  **once** and adopts into **n** registries (`CodegraphConnectionServiceTest`,
  `McpConnectionServiceTest`), plus `ToolServiceGroupsTest` proves an adopted group's disconnect does
  not stop the shared service.
