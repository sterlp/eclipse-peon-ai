# ADR-0068 — Codegraph Built-in: isolierter MCP-Connect-Pfad (Groups)

**Status:** Accepted (2026-10-17)

**Context:** Codegraph ist als Built-in geplant ([codegraph.md](../codegraph.md)): aktives
Projekt mit `.codegraph`-Index → Auto-Connect, unabhängig vom User-MCP-Toggle. `ToolService.connectMcp`
war disconnect-first/all-or-nothing über die gesamte MCP-Connection: ein Built-in-Server darin
hätte User-Server bei Connect-Fehlern mitgerissen (R-CG-3) und der User-Toggle-Off hätte das
Built-in getötet (R-CG-1).

**Decision:** `ToolService` hält generische **MCP-Groups** (named `McpService`-Instanzen,
`connectMcp(group, …)`/`disconnectMcp(group)`). Der User-MCP-Pfad (`McpConnectionService`,
Group `"user"`) und der Codegraph-Pfad (`CodegraphConnectionService`, Group `"codegraph"`)
teilen nur `McpService` als Mechanismus — kein gemeinsamer State, keine Koppelung an den
User-Toggle. Der Server-Name `codegraph` ist im User-Pfad reserviert (Filter in
`McpConnectionService.connectServers` — Built-in gewinnt, R-CG-11).

**Consequences:**
- (+) Failure-Isolation und Toggle-Unabhängigkeit sind strukturell (kein Verhaltenstest nötig,
  der beide Pfade gegen-seitig schützt — Groups können sich nicht berühren).
- (+) `McpService` bleibt generisch; einzige Erweiterungen: `workingDirectory` (Empty =
  unset) + `instructionsOf(serverName)`.
- (−) `ToolService` führt einen zweiten, gruppen-scoped Disconnect — Dispose bedient beide
  Gruppen (`PeonAiService.disconnectMcp()`).
- (−) Reserved-Name `codegraph` ist hart (manueller Eintrag wird im User-Pfad gefiltert,
  Log-Zeile); Jon-Entscheid (a) „unbedingt reserviert", Randwirkung ⏳ in open-points.md.
