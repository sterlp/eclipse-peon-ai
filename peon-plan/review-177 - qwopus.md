# PR #179 Review: Model Selector Follows Active Agent (R-ML5)

**Author:** Demmtop  
**Branch:** fix/177-model-select-per-agent  
**Issue:** #177  
**Date:** 2026-11-14

## Problem Solved

The original model selector (PR #173) always showed and saved the **base model** (`llm.model`), regardless of which agent was active. This caused:

- **Wrong display:** Selector showed base model even when PO/Plan/Review/custom agents had their own model configured.
- **Wrong save target:** Selecting a model while PO was active would silently change the base model (affecting Dev and all agents without their own model), while PO remained unchanged if it had its own model.
- **Empty values stored as `""`:** Violated the "empty means unset" convention.
- **Wrong connection for model list:** Always fetched models from the base server, even when the active agent had its own URL/key.

## Architecture Consistency

**✅ Strong alignment:**

- **Core/Plugin separation:** Routing logic (`AgentModelSelection` record) lives in core; Eclipse-specific wiring (`ChatModelComboWidget`, `AIChatView`) in plugin.
- **Service as facade:** `PeonAiService` exposes `getActiveModelSelection()` and `saveActiveAgentModel()`.
- **Widget composition:** `ChatModelComboWidget` composed by `ActionsBarWidget`, following established patterns.
- **Clean break:** Completely replaces old base-model-only behavior.
- **Explicit over implicit:** `modelSlot()` makes agent→slot mapping public.

**⚠️ Minor concern:** `ModelComboWidget` now has two constructors (grid layout vs. caller-provided controls). Reasonable for reuse but verbose contract documentation.

## Tests

Excellent coverage:
- `AgentModelSelectionTest` (core): 9 BDD tests covering all routing cases, with negative assertions
- `ChatModelComboWidgetTest` (PDE/SWT): Widget behavior
- `ChatModelPreferenceTest` (PDE): Real Eclipse preference store
- `PeonAiServiceTest`: Service API path

## Issues Found

1. **Thread safety of `modelSelectable`** in `ActionsBarWidget`: `working` is `volatile`, but `modelSelectable` is not. Potential race if `showModel()` and `lockWhileWorking()` called from different threads. Likely acceptable given SWT's single-threaded UI model.

2. **Error handling in `onModelChange`:** Catch block shows problem message but doesn't log the exception. Per architecture rules ("Log OR throw"), should log as well.

3. **BDD 10 verification:** "Committing pending model before agent switch" only verified via review/smoke (SWT precedent R-UI1). Gap compared to other BDDs but consistent with past practice.

## Documentation

Comprehensive updates:
- `model-loading.md` (new R-ML5 with 10 BDDs)
- `interaction-design.md` (model selector row)
- `index.md` (trail update)
- `homepage/src/setup/advanced-configuration.md` (user-facing)

## Scope

**Appropriate and well-bounded:**
- Focused on one concern (agent-aware model selector)
- Minimal core changes (new public API only)
- Revert of PR #173 included for clean history
- Reuses existing `ModelComboWidget` infrastructure

## Verdict

**✅ APPROVED with minor notes**

The two minor concerns (thread safety — likely fine; error logging in `onModelChange`) are low-risk and can be addressed in a follow-up. The PR solves a real bug with clear user impact, follows Peon AI architecture principles, includes comprehensive tests, and updates all relevant documentation.
