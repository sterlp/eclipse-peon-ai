# Docs — design & dev spec (the HOW / system reference)

The `docs/` tree is our shared memory: one story per feature (business rules + BDD). Your technical
notes live in [adr/](adr/index.md). Not published — user-facing docs are in `homepage/`.

## Stories

* [Advanced Configuration](advanced-configuration.md) - the two-page preference split and per-agent model resolution via `ChatRequest.modelName()`.
* [Custom Agents](custom-agents-design.md) - user-defined `AGENT.md` agents with tool allowlists, read-only mode and per-agent model.
* [Interaction Design](interaction-design.md) - the chat view layout: history, input block, action bar and status line.
* [Plan & Dev Agent](plan-dev-agent-design.md) - the two-phase plan→dev handoff model and its planned pipeline features.
* [Session Token Usage](token-usage.md) - cumulative ↑/↓ token spend in the header, fed from the StreamingBridge choke point.
* [Standing Orders](standing-orders-design.md) - context lines (project, AGENTS.md, active command/skill) that survive mid-loop compaction.
* [SWT Integrated Input Buttons](swt-integrated-input-buttons.md) - flat icon buttons beside a `StyledText` that read as one white field on macOS + Windows.
* [Ask User Tool](user-question-tool-design.md) - the LLM pausing mid-task to ask a clarifying question inline in the chat.

## Notes

* [ADRs](adr/index.md) - technical decision records (the agent's long-term memory).
