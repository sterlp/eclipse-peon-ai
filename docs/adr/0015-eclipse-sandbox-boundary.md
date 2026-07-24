# Eclipse VFS as AI Sandbox Boundary

**Status** · Accepted

**Context** · The AI agent has two sets of file tools: disk tools (real filesystem, configurable workingDir) and Eclipse workspace tools (Eclipse virtual file system, project-scoped). Disk tools can be enabled/disabled in config, but the Eclipse tools are always available.

**Decision** · The Eclipse workspace tools serve as the safe default sandbox — they restrict the AI agent to files within open Eclipse projects via the Eclipse VFS. Disk tools are an opt-in override for scenarios where real filesystem access is needed (e.g. scaffold agent working on `.peon` config directory).

**Consequences** ·
- When disk tools are disabled, the agent is confined to Eclipse workspace files only — no risk of writing outside projects.
- The scaffold agent uses config-scoped disk tools as an exception — it needs to write to the `.peon` config directory which is outside the Eclipse workspace.
- Both tool sets should have symmetric behavior (same operations, same semantics) so the agent doesn't need to reason about which tool to use based on behavioral differences.

