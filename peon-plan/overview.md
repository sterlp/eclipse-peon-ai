# Rename `think_enabled` → `think_supported`

## Context

`think_enabled` reads like a runtime toggle ("is thinking currently on?") but it's actually a static capability declaration in the AGENT.md frontmatter — "does this agent support thinking?". The brain button in the UI provides the runtime toggle. Renaming to `think_supported` makes the semantic distinction clear: the frontmatter declares *capability*, the UI controls *state*.

## Design Decisions

- **New name:** `think_supported` (capability declaration)
- **Backward compat:** Read both `think_enabled` and `think_supported` — old key takes precedence so existing AGENT.md files work without migration
- **Auto-migrate on write:** Always write `think_supported`; existing files get migrated the next time the agent config is saved (e.g. model change, brain toggle)
- **Homepage docs:** Use only the new name — no mention of the old
- **Internal docs:** Mention the old name as deprecated with a TODO to remove in a future major version

## Affected Files

### Code (org.sterl.llmpeon.core)

1. **`/llmpeon-core/src/main/java/org/sterl/llmpeon/agent/CustomAgent.java`**
   - Add `THINK_SUPPORTED = "think_supported"` constant
   - `isThinkEnabled()`: read `THINK_SUPPORTED` first; fall back to `THINK_ENABLED` for backward compat
   - No change to write path — writes happen via `LlmPreferenceInitializer.saveThinkEnabled()`

2. **`/llmpeon-core/src/main/resources/org/sterl/llmpeon/prompts/scaffold-agent.txt`**
   - Replace `think_enabled` → `think_supported` in the artifact spec and example

3. **`/llmpeon-parent/org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/agent/CustomAgentServiceTest.java`**
   - Add two BDD test methods (see below)

### Plugin (org.sterl.llmpeon)

4. **`/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/LlmPreferenceInitializer.java`**
   - `saveThinkEnabled()` for CustomAgent: write `THINK_SUPPORTED` instead of `THINK_ENABLED`

5. **`/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/AiAdvancedPreferenceView.java`**
   - No change needed — the label "Dev: Supports thinking" is already semantically correct; it references `PREF_THINKING_ENABLED` which is the global preference key, not the frontmatter key

6. **`/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/PeonConstants.java`**
   - No change — `PREF_THINKING_ENABLED` is the Eclipse preference key for the global Dev/Plan think toggle, not the AGENT.md frontmatter key

### Docs

7. **`/llmpeon-parent/homepage/src/setup/custom-agents.md`**
   - Replace `think_enabled` → `think_supported` in the field table and description

8. **`/llmpeon-parent/docs/adr/0003-send-thinking-independent.md`**
   - Replace `think_enabled` → `think_supported` in the Decision section
   - Add TODO: remove `think_enabled` backward compat in a future major version

### Not touched

- `/llmpeon-parent/homepage/.vitepress/dist/` — build artifact, regenerated from source
- `PREF_THINKING_ENABLED` / `PREF_PLAN_THINK_ENABLED` — Eclipse preference keys, not frontmatter keys

## BDD Use Cases

### Test 1: Old name works (backward compat)
**Test:** `CustomAgentServiceTest.oldThinkEnabledNameIsReadCorrectly`
- **GIVEN** an AGENT.md with `think_enabled: true` in frontmatter
- **WHEN** the agent is loaded and `isThinkEnabled()` is called
- **THEN** it returns `true`
- **Type:** unit

### Test 2: Auto-migration on save
**Test:** `CustomAgentServiceTest.oldNameMigratesToNewOnSave`
- **GIVEN** an AGENT.md with `think_enabled: true` in frontmatter
- **WHEN** the agent is loaded, the model is changed via `setAgentModelName()`, and the file is saved
- **THEN** the saved file contains `think_supported: true` (not `think_enabled`)
- **AND** the model change is also persisted
- **Type:** unit (file I/O)

## Rules & Constraints

- The `THINK_ENABLED` constant stays in `CustomAgent.java` for backward compat reads — do not remove
- The `THINK_SUPPORTED` constant is the canonical name going forward
- `SimplePromptFile.setValue()` writes the key it's given — no special handling needed, just pass the right constant
- The `saveThinkEnabled()` in `LlmPreferenceInitializer` is the only code path that writes the think flag for CustomAgent (the brain button in the UI)

## Open Questions

- None — the scope is clear and contained.
