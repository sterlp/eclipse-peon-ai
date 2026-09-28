---
name: eclipse-preferences
description: Eclipse JFace preference-page patterns — live widget reads instead of store reads, coupled fields (provider → think form), and the parts/config/widgets overview.
---

# Preference pages — read widget state, not the store (verified 2026-09-28, model-config-widget cycle)

## 1. Live read, not store read

- **A listener/action button must read the field's current widget value
  (`editor.getStringValue()`, `combo.getSelectionIndex()`, `text.getText()`), never
  `getPreferenceStore().getString(...)`.** The store only updates on OK/Apply
  (`FieldEditorPreferencePage.performOk` → field editors persist into the store); reading it in a
  click handler silently returns the last *saved* value — a stale-read bug that surfaces as "works
  only after reopening the config UI" or "works only after Apply".
- **Asymmetry check when two buttons look inconsistent:** trace WHERE each gets its value (widget
  vs store) before suspecting caches/suppliers — the "inconsistent behavior" is usually one fresh
  read vs. one store read.
- **Widget owns state, the page routes the store (ADR-0005):** a reusable widget never touches the
  preference store itself. The page loads values into the widget once (at page build) and persists
  them in `performOk`; the widget exposes `load(values)` / `getValues()` / `snapshot()`.
- **`performOk`/`performCancel` timing (JFace):** `performOk()` first stores all field editors,
  then returns its boolean as the page's OK result — do custom persistence *after* `super.performOk()`
  so the field editors' values are already in the store. `performCancel()` is a no-op on the store —
  a live-read widget + untouched store means Cancel discards typed values for free.

## 2. Coupled fields — switch live, don't freeze

- **Provider → Think form (R-MCW-6):** a field whose *form* depends on a sibling (here the selected
  provider's `ThinkSupport`: Toggle / Values / FreeString / None) must be re-derived on a
  **Selection event** of the driving control — not frozen at construction (the advanced page's
  `AgentModelConfigSection` freezes because its provider is fixed per agent; the basic page's
  provider is editable in the same widget). Rebuild atomically on the UI thread: old state + new
  state + one `parent.layout()` in a single runnable — no flicker window.
- **SWT trap (hit 2026-09-28, Inc-2):** dispose + recreate of a control **appends the new control at
  the END of the parent's children** — any order-sensitive layout (binding field order 1–5) silently
  breaks after the first switch. Create all form variants **once**, then toggle via
  `GridData.exclude` + `setVisible` (GridLayout honors only `exclude` — `setVisible(false)` alone
  keeps the row reserved).
- **Value carry-over on a switch:** verbatim where the new form allows free input (editable combo /
  text), cleared for a fixed list without a match (**never a silent replacement value**), dropped for
  "no field". Read the value BEFORE switching forms.
- **Provider/URL/Key → reload identity:** a "Reload" that must act on what the user sees builds its
  identity from the live widget values, overriding the store state's identity fields
  (`base.get().toBuilder().providerType(...).url(...).apiKey(...).build()` — the store state
  still supplies the non-identity transport parameters). Think is NOT part of the connection
  identity. Reload/ping persist nothing (ADR-0060).

## 3. Widgets in `parts/config/widgets`

| Widget | Type | Role |
|---|---|---|
| `ModelConfigWidget` | controller (no own Composite) | Basic-page connection group: provider combo (9 entries, READ_ONLY) · URL · API key · model (`ModelComboWidget`) · think (provider-dependent form) · Ping. Live-read `snapshot()`/`computePing()`, `load`/`getValues` for the page's store routing. |
| `ModelComboWidget` | controller (no own Composite) | Model combo + Refresh button; fetches in a background Job from a `Supplier<FetchSnapshot>`, stale-guard by identity. |
| `AgentModelConfigSection` | section builder | Advanced-page per-agent connection block (provider frozen at construction — think form frozen too). |
| `TitledGroup` / `HorizontalRule` | UI helpers | Titled group, horizontal rule. |

Reference tests: `org.sterl.llmpeon.test` — `ModelConfigWidgetTest` (live read, provider switch),
`AiConfigPreferenceViewTest` (page-level persist/reload).
