# Model Loading & Selection

The model dropdown shows the available models from the current LLM provider, with per-agent
model resolution. The list is fetched lazily and persists across agent switches — it is only
refetched when the provider config changes.

## SOLL (2026-08-28) — ✅ gebaut (Zyklus 2b, 2026-08-30)

Der Modell-Dropdown wandert aus der Chat-UI in die Config-Seite (Basic-Page + pro Agent, shared `ModelComboWidget` inc-25) —
[advanced-configuration.md](advanced-configuration.md), Mechanik:
[ADR-0034](adr/0034-connection-cache-by-identity.md). Die Liste gilt pro **Verbindungs-
Identität** (Provider+URL+Key): einmalig fetch, **Cache on success**, Fehler → configured
model (heutiger Fallback), kein Refetch beim Agentenwechsel; **Refresh-Button im Dropdown**
= manueller Refetch (Fehler → alter Cache bleibt). Identitätswechsel der effektiven
Verbindung → neuer Fetch. Konfiguriertes Modell nicht in der Liste → **bleibt gesetzt**
(kein Auto-Switch auf erstes Modell — Abweichung von B2); unbekanntes Modell wird der Liste
angehängt (wie heute).

```
GIVEN die Modell-Liste für eine Identität wurde erfolgreich geladen
WHEN ein Agent mit gleicher effektiver Identität aktiviert wird
THEN die gecachte Liste wird genutzt — kein Refetch

GIVEN der List-Fetch für eine Identität schlägt fehl (Netzwerk/leere Liste)
WHEN der Agent aktiviert wird
THEN das konfigurierte Modell bleibt gesetzt (kein Fehler, kein Auto-Switch)

GIVEN die gecachte Liste einer Identität
WHEN der User den Refresh-Button im Dropdown drückt
THEN die Liste wird neu geholt und ersetzt den Cache
AND bei Fetch-Fehler bleibt der alte Cache bestehen
```

Die Ist-Beschreibung unten ist mit dem Umbau (2b, 2026-08-30) überholt — nur noch als historische Referenz.

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
