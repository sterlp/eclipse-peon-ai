# Model Loading & Selection

The model dropdown shows the available models from the current LLM provider, with per-agent
model resolution. The list is fetched lazily and persists across agent switches — it is only
refetched when the provider config changes.

## Use Cases (BDD)

```
GIVEN we have a list of models loaded
WHEN the config is changed (provider, URL, or API key)
AND we successfully reload the models from the provider
AND the currently configured model for the active agent is not found in the list
THEN we select the first model from the loaded list

GIVEN we have no models loaded
WHEN the config is changed
AND no models are successfully loaded (empty list or network failure)
THEN we add the current model to the list and select it

GIVEN we have an agent selected with a model list already loaded
WHEN we select a different agent
AND this agent has a different model in its config
AND this model is not part of the currently loaded list
THEN we add this model to the list and select it
```

## Data Flow

```
AIChatView.createPartControl()
  → applyConfig()
      → reloadModelsIfNeeded()
          → if provider changed or first load → loadModelsInBackground()
              → config.listAiModels() → actionsBar.applyModelList(models, selectedModel)
          → else → actionsBar.selectModel(modelName) (reuse existing list)

AIChatView.onAgentChange(agent)
  → if new agent's model in list → actionsBar.selectModel(modelName)
  → else → actionsBar.addAndSelectModel(modelName)   // append to existing list

Preference change event
  → applyConfig() → reloadModelsIfNeeded()
```

## Components

### `reloadModelsIfNeeded()` in `AIChatView`
Decides whether a full model list reload is needed. Triggers `loadModelsInBackground()` if the
provider type, URL, or API key changed — or if no list exists yet. Otherwise reuses the cached list
and selects the active model from it.

### `loadModelsInBackground()` in `AIChatView`
Fetches models via `config.listAiModels()` on a background job. On success, populates the combo
via `actionsBar.applyModelList()`. On failure or empty list, falls back to showing the configured
model name via `showConfiguredModelFallback()`.

### `onAgentChange(agent)` in `AIChatView`
Switches the active agent. If the new agent's model exists in the current list, selects it.
If not, appends it to the list (preserving previously loaded models) and selects it — the user can
still switch between all known models.

### `ActionsBarWidget.applyModelList(models, selectedId)`
Replaces the full model list. Called only on initial load or config change.

### `ActionsBarWidget.addAndSelectModel(modelId)`
Appends a single model to the existing list if not already present, then selects it. Called on
agent switch when the new agent's model isn't in the current list.

## Notes / constraints

- **List persistence:** the model list is not cleared on agent switch. Once fetched, models stay
  available — switching between agents with different models doesn't require re-fetching.
- **Append on agent switch:** a model unknown to the current list is appended, not replacing the
  list. This gives the user full model choice regardless of agent.
- **Fallback (B1):** if the provider returns no models or the network call fails, the configured
  model name is shown in the combo as a single-item fallback — the user can still type or change
  it later.
- **B2 (unknown model):** if the configured model isn't found after a successful fetch, the first
  model from the provider list is selected automatically.
