# Bugfix Plan — Agent Reload UI Wiring + Documentation Fixes

## Goal
Wire the agent reload flow so the UI updates after new agents are created, and fix documentation issues in the scaffold agent prompt.

---

## Bug 1: Agent reload doesn't update the UI (wiring missing)

### Problem
`ReloadConfigTool.reloadConfig()` calls `agentService.reloadAgents()` which updates the internal agents list, but `AIChatView` never gets notified. The agent combo box in `ActionsBarWidget` doesn't reflect new/removed agents.

**Flow:**
1. Scaffold agent creates a new agent (e.g., writes `agents/CodeReviewer/AGENT.md`)
2. Calls `reloadConfig()` → `agentService.reloadAgents()` → new agent is in `agentService.getAgents()`
3. ❌ `AIChatView.applyConfig()` is never called → `actionsBar.setAgents()` never runs → combo box unchanged

**Impact:** New agents invisible in UI until user restarts or triggers a config change (e.g. preferences).

### Design
Add an optional callback to `AgentService.reloadAgents()` that notifies on reload. Chain it through `PeonAiService` → `AIChatView` using the same callback pattern as `sendTrigger` and `mcpStateChange`.

### Affected Files
- `/org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/AgentService.java` — add optional `Consumer<Boolean>` reload callback to `reloadAgents()`
- `/org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/scaffold/ReloadConfigTool.java` — invoke callback after reload
- `/org.sterl.llmpeon/src/main/java/org/sterl/llmpeon/parts/PeonAiService.java` — wire `Runnable` callback from AIChatView into reload chain
- `/org.sterl.llmpeon/src/main/java/org/sterl/llmpeon/parts/AIChatView.java` — pass reload callback to `PeonAiService`, update UI in callback

### Implementation

**1. AgentService — add callback parameter to reloadAgents:**

In `AgentService.java`, change:
```java
public boolean reloadAgents() {
```
to:
```java
public boolean reloadAgents() {
    return reloadAgents(null);
}

public boolean reloadAgents(Runnable onReload) {
```

At the end of the method, before `return true;`:
```java
if (onReload != null) {
    try { onReload.run(); } catch (Exception e) { /* swallow — reload succeeded, callback failure is non-critical */ }
}
return true;
```

**2. ReloadConfigTool — invoke callback after reload:**

In `ReloadConfigTool.java`, add a field:
```java
private volatile Runnable onReload;

public void setOnReload(Runnable onReload) {
    this.onReload = onReload;
}
```

In `reloadConfig()`, after `agentService.reloadAgents();`:
```java
agentService.reloadAgents(onReload);
```

**3. PeonAiService — wire the callback:**

In `PeonAiService.java`, add field:
```java
private final Runnable onAgentReload;
```

In constructor, add parameter:
```java
public PeonAiService(Runnable sendTrigger,
                     Consumer<IFile> openInEditorCallback,
                     Consumer<Boolean> mcpStateChange,
                     Runnable onAgentReload) {
    // ...
    this.onAgentReload = onAgentReload;
    // ...
    reloadConfigTool = new ReloadConfigTool(agentService, skillService, commandService, config);
    reloadConfigTool.setOnReload(onAgentReload);
    // ...
}
```

**4. AIChatView — pass the reload callback:**

In `AIChatView.java`, change the `aiService` field:
```java
private final PeonAiService aiService = new PeonAiService(
    this::doSendMessage,
    file -> EclipseUtil.runInUiThread(parent, () -> EclipseUtil.openInEditor(file)),
    enabled -> EclipseUtil.runInUiThread(parent, () -> statusLine.setMcpEnabled(enabled)),
    () -> EclipseUtil.runInUiThread(parent, this::refreshAgentUI)
);
```

Add the refresh method:
```java
/** Refresh agent combo and status after a config reload. */
private void refreshAgentUI() {
    actionsBar.setAgents(aiService.getAgents());
    actionsBar.updateModeUI(aiService.getActiveAgent());
    actionsBar.setThinkEnabled(aiService.getActiveAgent().isThinkEnabled());
    refreshStatusLine();
}
```

### Verification
- Build + run. Use Scaffold agent to create a new agent → reloadConfig() fires → new agent appears in combo box immediately.

---

## Bug 2: Doc — custom-agents-design.md references dead code (PeonMode, CustomAgentService)

### Problem
`/llmpeon-parent/docs/custom-agents-design.md` contains references to concepts that no longer exist in the codebase:

1. **`PeonMode`** (4 mentions) — the old enum-based mode system has been replaced by `AgentService` / `AiAgent`. The doc says: "`ActionsBarWidget` builds the agent combo from `PeonMode` labels **plus** custom-agent names. Selecting index `< PeonMode.values().length` fires the mode callback" — this is completely wrong now.
2. **`CustomAgentService`** — the doc describes a separate `CustomAgentService extends AbstractChatService` class. This doesn't exist. Custom agents are `CustomAgent extends AbstractAgent`, managed by `AgentService`.
3. **`PeonAiService.setPeonMode` / `setActiveCustomAgent`** — these methods don't exist. The actual code uses `AgentService.getActiveAgent()` / `setActiveAgent()`.
4. **`AgentPromptFile`** — the doc references `AgentPromptFile` as if it's a separate class from `SimplePromptFile`. It's the same thing.
5. **`resolveDefaultDir` / `.claude` compat for agents** — the doc mentions "default resolved by `LlmPreferenceInitializer.resolveDefaultDir(\"agents\")`" and Claude compatibility (`~/.claude/agents`). The actual code uses `config.getConfigDir().resolve(AGENT_DIRECTORY)` with no Claude fallback for agents.

**Impact:** Future developers reading this doc will be confused by references to dead classes/methods and may try to implement things that already exist differently.

### Affected Files
- `/llmpeon-parent/docs/custom-agents-design.md`

### Implementation
Rewrite the doc to reflect the current architecture:
- Replace `PeonMode` references with `AgentService` + `AiAgent`
- Remove `CustomAgentService` — describe `CustomAgent extends AbstractAgent` instead
- Update the UI & wiring section to describe `AgentService.getAgents()` / `setActiveAgent()` / `actionsBar.setAgents()`
- Replace `AgentPromptFile` with `SimplePromptFile`
- Remove Claude-compat references for agents (only skills/commands have that)
- Update the AGENT.md format example to use `read-only` (not `readOnly`) — the canonical key is `read-only` (see `CustomAgent.READ_ONLY = "read-only"`)

### Verification
- Read the rewritten doc — no references to non-existent classes/methods.

---

## Bug 3: Typo in scaffold-agent.txt documentation

### Problem
`/org.sterl.llmpeon.core/src/main/resources/org/sterl/llmpeon/prompts/scaffold-agent.txt` line 33:
```
- `temperature` (optional) - temperate to use e.g. 1.0, 0.9 etc.
```
"temperate" should be "temperature".

### Affected Files
- `/org.sterl.llmpeon.core/src/main/resources/org/sterl/llmpeon/prompts/scaffold-agent.txt`

### Implementation
Replace "temperate to use" with "temperature to use" on the line for the `temperature` frontmatter field.

### Verification
- Read scaffold-agent.txt — no more typo.

---

## Bug 5: Remove autonomous feature from docs + homepage

### Problem
The autonomous mode checkbox is disabled in the UI (`AIChatView.applyShellCommandConfirmation()` has `var autonomous = false; // TODO restore autonomus mode`). The feature is not in use but is documented in both the dev docs and the user-facing homepage, which will confuse users.

**Locations:**
- `docs/interaction-design.md` — mentions "☐ autonomous" checkbox in the action bar
- `docs/plan-dev-agent-design.md` — describes autonomous mode as a planned feature
- `homepage/src/setup/agent-mode.md` — entire "Autonomous mode (planned / WIP)" section
- `homepage/src/setup/custom-agents.md` — mentions autonomous mode in the handoff tip

### Affected Files
- `/llmpeon-parent/docs/interaction-design.md`
- `/llmpeon-parent/docs/plan-dev-agent-design.md`
- `/llmpeon-parent/homepage/src/setup/agent-mode.md`
- `/llmpeon-parent/homepage/src/setup/custom-agents.md`

### Implementation
- **interaction-design.md**: Remove the "☐ autonomous" row from the action bar table
- **plan-dev-agent-design.md**: Remove the "Autonomous mode (checkbox in UI, currently disabled)" line from the handoff decision section
- **agent-mode.md**: Remove the entire "## Autonomous mode (planned / WIP)" section
- **custom-agents.md**: Remove the "autonomous variant" mention in the handoff tip blockquote

### Verification
- Read all four files — no autonomous references remain.

---

## Bug 4: PromptYmlParser.getValue — unidirectional key fallback (hyphens only)

### Problem
`PromptYmlParser.getValue(frontmatter, key)` only strips hyphens for fallback:
```java
var values = frontmatter.get(key);
if (values == null) values = frontmatter.get(key.replace("-", ""));
```

If a user writes `read_only` (with underscore) in their AGENT.md, the parser stores it as `read_only`. When code looks up `read-only`, it checks `read-only` → not found → `readonly` (stripped hyphens) → not found → returns null.

The fallback needs to also try converting underscores to hyphens.

### Affected Files
- `/org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/prompt/PromptYmlParser.java`

### Implementation
In `PromptYmlParser.getValue()`, change to:
```java
public static List<String> getValue(
        @NonNull Map<String, List<String>> frontmatter, @NonNull String key) {
    var values = frontmatter.get(key);
    if (values == null) values = frontmatter.get(key.replace("-", ""));
    if (values == null) values = frontmatter.get(key.replace("_", "-"));
    if (values == null) values = frontmatter.get(key.replace("_", ""));
    return values;
}
```

This covers all combinations: `read-only` ↔ `read_only` ↔ `readonly` ↔ `read-only`.

### Verification
- Add test: parse YAML with `read_only: true`, look up `read-only` → should return `["true"]`.

---

## Model Selection Verification (user-requested)

**Scenario:** GIVEN we are in the DEV agent WHEN no model is selected for the PLAN agent THEN we select the PLAN agent, the model stays the same as the DEV agent's default.

**Result:** ✅ Already works correctly.

**Trace:**
1. `onAgentChange(planAgent)` → `aiService.setActiveAgent(planAgent)`
2. `aiService.getActiveModel()` → `getActiveAgent().getAgentModelName()` → `AiPlanAgent.getAgentModelName()` → `config.getPlanModel()` = `null` → falls back to `getConfig().getModel()` = the default model
3. `actionsBar.containsModelId(defaultModel)` → true → `selectModel(defaultModel)` — combo stays on the same model

Same fallback applies to any agent whose `getAgentModelName()` returns `null` — `PeonAiService.getActiveModel()` always falls back to `getConfig().getModel()`.

---

## Bug 6: Agent model save asymmetry — setAgentModelName writes wrong model

### Problem

`AbstractAgent.setAgentModelName(String)` calls `configuredModel.withModel()` — changes the **global** model. Only `AiDevAgent` should do this. `AiPlanAgent` and `AiScaffoldAgent` inherit this behavior, so:

- **AiPlanAgent**: `getAgentModelName()` reads `planModel`, but `setAgentModelName()` writes the **global** model. Selecting a model for Plan temporarily changes Dev's model too (corrected by prefListener after `prefs.flush()`, but the intermediate state is wrong and fragile).
- **AiScaffoldAgent**: `getAgentModelName()` returns `config.getModel()` (the global), and `setAgentModelName()` writes the global. Model changes on Scaffold silently change Dev's model.
- `AiPlanAgent` already has a dead `setModelName(AiModel)` method that does the right thing (updates planModel) — it's just not wired to the interface.
- `AiDevAgent` has a dead `setModelName(String)` method that duplicates the inherited behavior.

**Design principle (from user):** Each agent returns its own model or `null` (use default). Each agent saves its own model. Only Dev saves the "default".

### Affected Files
- `/org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/AbstractAgent.java` — remove `getAgentModelName()` and `setAgentModelName()` overrides (let interface defaults apply: null / no-op)
- `/org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/AiDevAgent.java` — add explicit `setAgentModelName` override, remove dead `setModelName` method
- `/org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/AiPlanAgent.java` — `setAgentModelName` already fixed, remove dead `setModelName(AiModel)` method
- `/org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/scaffold/AiScaffoldAgent.java` — remove `getAgentModelName()` override (inherits `null`)
- `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/LlmPreferenceInitializer.java` — add `AiScaffoldAgent` to the Dev case in `saveModel`

### Implementation

**1. AbstractAgent — remove overrides, let interface defaults apply:**

The `AiAgent` interface already declares safe defaults: `getAgentModelName() → null` and `setAgentModelName() → false`. Remove both overrides from `AbstractAgent` so these defaults apply to any subclass that doesn't explicitly override.

Remove from `AbstractAgent.java`:
```java
// REMOVE both methods entirely:
@Override
public String getAgentModelName() {
    return configuredModel.getConfig().getModel();
}
@Override
public boolean setAgentModelName(String modelName) {
    return this.configuredModel.withModel(modelName);
}
```

**2. AiDevAgent — add explicit override (was inherited from AbstractAgent):**

Remove the dead `setModelName(String)` method. Add the interface override:
```java
// REMOVE: public boolean setModelName(String modelName) { ... }
// ADD:
@Override
public boolean setAgentModelName(String modelName) {
    return this.configuredModel.withModel(modelName);
}
```
`getAgentModelName()` already returns `config.getModel()` — no change.

**3. AiPlanAgent — override already done, remove dead method:**

`setAgentModelName(String)` is already overridden correctly (your fix). Remove the dead `setModelName(AiModel)` wrapper:
```java
// REMOVE:
public boolean setModelName(AiModel modelName) {
    return setAgentModelName(modelName == null ? null : modelName.getId());
}
```
`getAgentModelName()` already returns `config.getPlanModel()` — no change.

**4. AiScaffoldAgent — use default:**

Remove the `getAgentModelName()` override (inherits `null` from `AbstractAgent`). No `setAgentModelName` override needed (inherits no-op).

**5. LlmPreferenceInitializer.saveModel — handle Scaffold:**

```java
// FROM:
if (agent instanceof AiDevAgent) {
// TO:
if (agent instanceof AiDevAgent || agent instanceof AiScaffoldAgent) {
```
Add `import org.sterl.llmpeon.scaffold.AiScaffoldAgent;`.

### Verification
- Unit test: `AiPlanAgent.setAgentModelName("gpt-4")` updates `planModel`, not global `model`.
- Unit test: `AiDevAgent.setAgentModelName("gpt-4")` updates global `model`.
- Unit test: `AiScaffoldAgent.getAgentModelName()` returns `null`.
- Build + run: switch to Plan, change model → Dev model unchanged.

---

## BDD Use Cases

### Bug 1 — Agent Reload UI Wiring

**UC-1: New agent visible after scaffold creates it**
- GIVEN the user is on Peon-Dev agent with 2 agents in the combo
- WHEN the scaffold agent creates a new agent and calls reloadConfig()
- THEN the agent combo box shows 3 agents including the new one
- AND the active agent selection is preserved if it still exists
- Type: integration (requires UI thread)

**UC-2: Reload preserves active agent selection**
- GIVEN the user has selected a custom agent "MyAgent"
- WHEN reloadConfig() is called (no agents added/removed)
- THEN "MyAgent" remains selected in the combo
- Type: integration

**UC-3: Reload selects fallback if active agent removed**
- GIVEN the user has selected a custom agent "MyAgent"
- WHEN "MyAgent" is deleted from disk and reloadConfig() is called
- THEN the combo shows remaining agents and selects the first one
- Type: integration

### Bug 2 — Doc Rewrite
- No BDD needed — documentation fix.

### Bug 3 — Typo Fix
- No BDD needed — documentation fix.

### Bug 4 — Bidirectional Key Fallback

**UC-4: Underscore key resolved by hyphen lookup**
- GIVEN an AGENT.md with `read_only: true` in frontmatter
- WHEN code reads the key via `firstOrDefault("read-only", null)`
- THEN the value is `"true"`
- Type: unit test on `PromptYmlParserTest`

**UC-5: Hyphen key resolved by underscore lookup**
- GIVEN an AGENT.md with `read-only: true` in frontmatter
- WHEN code reads the key via `firstOrDefault("read_only", null)`
- THEN the value is `"true"`
- Type: unit test on `PromptYmlParserTest`

### Bug 6 — Agent Model Save Asymmetry

**UC-6: Plan agent model change doesn't affect Dev agent**
- GIVEN Dev agent uses model "qwen3" (the default)
- WHEN the user switches to Plan and calls `setAgentModelName("gpt-4")`
- THEN `configuredModel.getConfig().getPlanModel()` is `"gpt-4"`
- AND `configuredModel.getConfig().getModel()` is still `"qwen3"`
- Type: unit test

**UC-7: Scaffold agent uses default model**
- GIVEN an AiScaffoldAgent instance
- WHEN `getAgentModelName()` is called
- THEN it returns `null` (falls back to default in `PeonAiService.getActiveModel()`)
- Type: unit test

**UC-8: AbstractAgent defaults to null model**
- GIVEN an AbstractAgent subclass that doesn't override `getAgentModelName()`
- WHEN `getAgentModelName()` is called
- THEN it returns `null`
- AND `setAgentModelName("any")` returns `false` (no-op)
- Type: unit test

---

## Open Questions
1. Should the reload callback also refresh the model combo when a new agent has a different model? — Currently `refreshAgentUI()` does `updateModeUI` which calls `applyImplAutonomousVisibility`. Model handling happens in `onAgentChange`, not in reload. If the user switches to the new agent after reload, `onAgentChange` will handle model appending. No action needed unless we want proactive model addition on reload.

2. Bug 6 — `AiScaffoldAgent.getConfig()` currently returns `devAgentConfig()` (uses global model). Since `getAgentModelName()` will now return `null`, `PeonAiService.getActiveModel()` falls back to `getConfig().getModel()` for display. But `AiScaffoldAgent.getConfig()` builds its `AgentConfig` with `devAgentConfig().model(model)` — the global model. This is correct: Scaffold uses the default model at runtime. No change needed to `getConfig()`.

---

## Steps
1. Bug 1: Wire reload callback (AgentService → ReloadConfigTool → PeonAiService → AIChatView)
2. Bug 2: Rewrite custom-agents-design.md to reflect current architecture
3. Bug 3: Fix typo in scaffold-agent.txt
4. Bug 4: Add bidirectional key fallback in PromptYmlParser
5. Bug 5: Remove autonomous feature from docs + homepage
6. Bug 6: Fix agent model save asymmetry (AbstractAgent defaults, AiDevAgent/AiPlanAgent/AiScaffoldAgent overrides, LlmPreferenceInitializer)
7. Add unit tests for Bug 4 + Bug 6
8. Build + integration test Bug 1 (create agent via scaffold, verify UI update)
