# ADR-0067: Zwei-Registry-Wiring für dynamische Tool-Familien (MCP + disk)

**Status:** Accepted (2026-10-15, Paul-Order Night-Cycle 4)

## Context

Jon (Peon-PO) hält seit R2 eine **eigene kuratierte** `ToolService`-Instanz
(`BuildPoAgentComponent:67`, `new ToolService(false)`), damit seine Basistools bewusst gewählt
bleiben. Die dynamischen Tool-Familien sind daran vorbeigebaut worden:

- `McpConnectionService.connect` verbindet MCP-Server ausschließlich an den `sharedToolService`
  (PeonAiService.java:200) → Jons `mcpToolSpecs` bleibt leer.
- `SharedToolsComponent.updateActiveDiskTools` (:82-98) togglet die disk*-Familie nur am
  `sharedToolService` → Jon sieht keine disk-Tools, obwohl po-agent-jon.md R2 sie ihm zuspricht.

Ein Agent, dessen Registry User-Konfiguration still ignoriert, handelt auf „Tool existiert nicht" —
die teuerste Fehlerklasse dieses Codebases (False-Negative-Prinzip, AGENTS.md).

## Decision

Beide dynamischen Familien adressieren **alle** agent-facing ToolService-Registries — shared **und**
po (Jon):

1. **MCP:** Connect/Disconnect läuft gegen beide Registries. Kein Filter-Change (abwesende
   Allowlist = alle; `getToolNameFilter` deckt MCP-Namen ab).
2. **disk-Gate:** `updateActiveDiskTools` togglet beide Registries mit derselben Konfiguration
   (`diskToolsEnabled`). Kein zweiter Schalter, keine Inheritance-Diskussion.

Jons Write-Validator (R3: `*/docs/*` + `*.md`) gilt am Choke-Point unverändert — auch für
disk-Writes; damit bleibt die Schreibfläche von Jon auch mit disk-Tools auf Docs begrenzt.

## Consequences

- Die Kuratierte-Registry-Entscheidung (R2) bleibt unangetastet: sie regelt die **Basis**-Tools;
  die dynamischen Familien kommen konfigurationsgetrieben in jede Registry.
- Neue agent-facing Registries müssen sich künftig an beiden Familien anmelden (Implementierungs-
  Hinweis: Sammlung/Enumeration der Registries statt Copy-Paste-Zweiter Aufruf).
- Slaves (Da Thinka/Da Mek/Da Dok) sitzen auf dem shared-Service und sind von beiden Fixes
  unberührt — sie sahen MCP/disk bereits direkt.
- Nachweis: je Richtung ein Test gegen die po-Registry (Connect/Disconnect, Toggle on/off).
