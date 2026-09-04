# ADR-0023: Jon (Peon-PO) reuses the plan model slot, defaulting to the dev/main model

**Status** · **Superseded by [ADR-0036](0036-po-own-model-slot.md)** (2026-09-03, umgesetzt in
Zyklus 3a). Jon besitzt seit 3a einen eigenen `po`-Slot; der Fallback bei leerem Slot ist die
**Base-Config**, nicht der Plan-Slot. Die unten als „deliberate follow-up" benannte Divergenz ist
damit eingetreten. Der Regressionstest heißt jetzt
`PeonAiServiceTest.test_po_model_uses_po_slot_and_defaults_to_base_model`.
Die folgende Beschreibung ist **historisch**.

## Context
Jon (Peon-PO) is a distinct agent in the dropdown, but he must not force the user to configure a
*third* model preference next to Dev (`PREF_MODEL`) and Plan (`PREF_PLAN_MODEL`). Opening Jon **first**
used to yield "No model configured": `AiPoAgent` inherited the `AiAgent` defaults
(`getAgentModelName()` → `null`, `setAgentModelName()` → `false` no-op), so the model auto-selected by
`AIChatView.loadModelsInBackground()` was silently dropped and `PeonAiService.getActiveModel()` fell
through to an empty value. It only "worked" for Jon if the Dev agent had been activated first and thus
seeded `PREF_MODEL`.

## Decision
Jon **shares the plan model slot** rather than owning one. `AiPoAgent` overrides the model accessors to
read/write `LlmConfig.planModel`:

- `getAgentModelName()` returns `planModel` when set, otherwise the dev/main `model` — so Jon is **never
  model-less**; with no plan model configured he simply runs on the dev/default model.
- `setAgentModelName(name)` writes the plan slot (`planModel`), mirroring `AiPlanAgent`; `null` clears it.
- `getConfig()` returns `planAgentConfig()`, falling back to the main `model` when `planModel` is unset.

The persistence side matches: `LlmPreferenceInitializer.saveModel` / `saveThinkSupported` treat
`AiPoAgent` on the same branch as `AiPlanAgent` (`PREF_PLAN_MODEL` / `PREF_PLAN_THINK_SUPPORTED`).

## Consequences
- Opening Jon first can never produce "No model configured" — he defaults to the dev/main model.
- Jon and the Plan agent share one model preference by design: configuring a plan model applies to both,
  which is the intended "planning-tier model" grouping. If they must diverge later, Jon needs his own
  `PREF_PO_MODEL` slot — a deliberate follow-up, not an accident.
- No third model preference is introduced; the config surface stays Dev + Plan.
- Regression covered by `PeonAiServiceTest.test_po_model_uses_plan_slot_and_defaults_to_dev_model`.
- Realised in [Jon — Peon-PO](../po-agent-jon.md) (feature doc with the BDD rules).
