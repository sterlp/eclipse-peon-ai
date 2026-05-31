# Plan: Refactor AGENTS.md Injection & UI Toggle

## 1. Context
Refactor `AgentsMdService` to inject project-specific agent instructions similarly to how `WorkspaceMemoryTool` works, but scoped per-project via a markdown file discovered in the workspace root. Add a persistent toggle button in the status bar to enable/disable injection without restarting Eclipse.

## 2. Design Decisions
- **Message Type**: Switch from `SystemMessage` to `AiMessage`. Content is still template-processed (`${...}`) before wrapping.
- **Content Format**: Prepend workspace path and usage instructions, then append processed content:
  ```text
  AGENTS.md: /workspace/my-project/AGENTS.md
  Use this file for critical, non-obvious, always-needed project settings — like workspace memory, but scoped to this project. Edit it directly. Keep it very short, and update or clean up entries as work evolves so only current, relevant rules remain.
  ---
  [processed AGENTS.md content]
  ```
- **AI Interaction**: No new AI tools. The exact file path is provided in the `AiMessage` header so the LLM uses existing `EclipseWorkspaceReadFileTool` / `EclipseWorkspaceWriteFileTool` to edit it.
- **Persistence**: Single global boolean preference `agentsMd.enabled` (default `true`). Survives restarts. Applies immediately on toggle.
- **Discovery Caching**: File discovery remains tied to project changes (`AgentsMdService.load(project)`). Toggling the button only gates injection, not file detection.

## 3. Architecture Decisions
- **Preference Storage**: Add `PREF_AGENTS_MD_ENABLED` to `PeonConstants`. Initialize default in `LlmPreferenceInitializer`.
- **UI Flow**: `StatusLineWidget` toggle fires callback → `AIChatView` writes preference → `StandingOrdersBuilder` reads preference + `AgentsMdService.hasAgentFile()` to decide injection.
- **State Passing**: Add `boolean agentsMdEnabled` parameter to `StandingOrdersBuilder.build()`. Keeps file logic decoupled from UI state.

## 4. Affected Files and Packages
| File | Changes |
|------|---------|
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/PeonConstants.java` | Add `String PREF_AGENTS_MD_ENABLED = "agentsMd.enabled";` |
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/LlmPreferenceInitializer.java` | Add `defaults.putBoolean(PREF_AGENTS_MD_ENABLED, true);` in defaults. Read pref in `buildWithDefaults()` if needed, or keep it simple and read directly via `InstanceScope`. |
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/agentsmd/AgentsMdService.java` | Change `agentMessage(TemplateContext)` to return `Optional<AiMessage>`. Add header/footer formatting. Expose `String getAgentFileName()` for UI label. Keep template processing on the inner content. |
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/widget/StatusLineWidget.java` | Replace passive `agentIcon` + `agentLabel` with a single `Button(btnAgents, SWT.TOGGLE)`. Add `Consumer<Boolean> onAgentsMdToggle` to constructor. In `update()`, set button text to discovered filename, enable/disable based on `hasAgentsMd`. |
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java` | Wire toggle callback: read/write pref via `InstanceScope`. Pass enabled state into `StandingOrdersBuilder.build()`. Sync button selection from preference in `applyConfig()`. |
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/StandingOrdersBuilder.java` | Add `boolean agentsMdEnabled` parameter. Inject message only when `enabled && hasAgentFile()`. Remove old `agentsMdService.agentMessage().ifPresent(orders::add)` conditional duplication. |

## 5. Required New Tests (Business Use Cases)
- **TC1**: Toggle AGENTS.md button ON → trigger chat request → verify `AiMessage` with path header & content is injected into standing orders.
- **TC2**: Toggle AGENTS.md button OFF → trigger chat request → verify NO agent message injected, even if file exists.
- **TC3**: Restart Eclipse → verify toggle state persists (button remains ON/OFF as last set).
- **TC4**: Switch project to one without agents.md → verify status button is disabled/greyed and shows appropriate feedback; no crash on chat request.
- **TC5**: Create `rules.md` in new project → switch selection → verify button updates label to `rules.md` and toggles injection correctly.
