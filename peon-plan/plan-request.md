# Plan-Request: Model-Dropdown im Chat-Dialog

**PO:** cskinner · **Datum:** 2026-11-01 · **Status:** offen

## Ziel

Model-Auswahl direkt im Chat-Dialog: Dropdown, mit dem der User das Model jederzeit
wechseln kann — heute geht das nur in den Settings (ein Model pro Server, Basic-Page +
pro Agent in der Advanced-Page).

## Kontext / was gefunden wurde

### Heute (Model-Fluss)
- Model-Auswahl nur in Settings: `AiConfigPreferenceView` (Basic) + `AiAdvancedPreferenceView`
  (pro Agent), geteilte Logik in
  `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/widgets/ModelComboWidget.java`
  (editierbares SWT-Combo + Refresh-Button; R-ML4: Liste = nur Server-Einträge, getippter
  Text bleibt Feld-Text, kein Auto-Switch).
- Modell-Liste: `LlmProviders.of(provider).listAiModels(config)` (Provider-`/models`),
  gecacht pro `ConnectionIdentity` in
  `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/ai/ModelListCache.java`
  (Singleton, single-flight, cache-on-success, Fehler/leer → nicht gecacht).
- Base-Model: Präferenz `PREF_MODEL` (`PeonConstants.java:40`) → `LlmConfig.model`.
  Pro-Agent-Overrides: `LlmConfig.modelConfigs` (dev = Base, die anderen Slots erben
  Base bei leerem Wert, `LlmConfig.resolveModel`).
- Chat-Dialog: `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java`
  - `HeaderBarWidget` (Tokens | · | Agent-Roster | Codegraph | Hammer)
  - `ActionsBarWidget` unter der Eingabe (Agent-Combo, Compact, Clear, Handoff)
- `PeonAiService.getActiveModel()` (`PeonAiService.java:295`) = Model des aktiven
  Agents, sonst Base-Model.
- `AIChatView.doSendMessage()` (`AIChatView.java:561`) blockt heute mit
  "No model configured — open Window > Preferences > Peon AI".

### Bereits vorhanden (Wiederverwendung!)
- `ConfiguredChatModel.withModel(String)`
  (`org.sterl.llmpeon.core/.../ai/ConfiguredChatModel.java:88`): switcht Base-Model,
  clearlt gecachte Agent-Model-Instanzen.
- `AIChatView.prefListener` → `applyConfig()` (`AIChatView.java:433`): reagiert auf jede
  Präferenz-Änderung, lädt `LlmPreferenceInitializer.buildWithDefaults()`,
  `aiService.updateConfig(config)` → Model-Neubau. **Eine Schreibaktion auf `PREF_MODEL`
  aus dem Chat-Dropdown läuft also durch den bestehenden Pfad.**
- `ModelComboWidget.baseSnapshot(LlmConfig)` baut die Fetch-Snapshot für die Base-Verbindung.

## Vorschlag (Dev)

- Neues Model-Dropdown im Chat-Dialog, zeigt/wechselt das **Base-Model** ("ein Model pro
  Server"), editierbar (R-ML4-Konsistenz), Liste aus `ModelListCache` (Base-Identität),
  Fetch beim View-Open (oder lazy beim ersten Öffnen) + Refresh-Affordance.
- Auswahl schreibt `PREF_MODEL` → bestehender `applyConfig`-Pfad → Model-Neubau.
  Settings und Chat bleiben dadurch automatisch sync.
- Pro-Agent-Overrides aus der Advanced-Page bleiben wirksam (Tooltip + Doc).

## Entscheidungen (PO, 2026-11-01)

1. **Platzierung:** ✅ **ActionsBar neben dem Agent-Combo** (Agent + Model sind beide
   Konversations-Selektoren).
2. **Scope:** ✅ **nur Base-Model** ("ein Model pro Server"); Pro-Agent-Overrides aus
   der Advanced-Page bleiben wirksam (Tooltip + Doc).
3. **Persistenz:** ✅ **Präferenz `PREF_MODEL`** schreiben → bestehender
   `applyConfig`-Pfad; Settings und Chat bleiben sync, überlebt Restart.

## Betroffene Flächen (vorab)

- `org.sterl.llmpeon`: `AIChatView`, neuer/angepasster Widget (vermutlich
  `parts/widget/`), ggf. `PeonAiService`.
- `org.sterl.llmpeon.core`: vermutlich keine Änderung nötig (Mechanik vorhanden) —
  Plan soll das verifizieren.
- Tests: `org.sterl.llmpeon.test` (PDE/SWT-Präzedenz: `ModelComboWidgetTest`,
  `AiConfigPreferenceViewTest`, `PeonAiServiceTest`).
- Docs: `org.sterl.llmpeon/docs/model-loading.md` (neue Oberfläche), ggf.
  `interaction-design.md` / `advanced-configuration.md`.
- Homepage: User-visible Change → Update im selben Inkrement (AGENTS.md).

## BDD-Grobentwurf (für den Plan zu präzisieren)

```
GIVEN der Chat-View ist offen und die Modell-Liste der Base-Verbindung ist geladen
WHEN der User ein anderes Model im Chat-Dropdown wählt
THEN das Base-Model wird gewechselt (nächster Turn nutzt das neue Model)
AND die Settings-Präferenz PREF_MODEL zeigt das gewählte Model

GIVEN der List-Fetch schlägt fehl (Netzwerk/leere Liste)
WHEN der User den Chat-Dropdown öffnet
THEN das aktuell konfigurierte Modell bleibt gesetzt (kein Fehler, kein Auto-Switch)

GIVEN ein Agent hat in den Settings ein eigenes Model-Override
WHEN der User das Base-Model im Chat-Dropdown wechselt
THEN das Agent-Override bleibt wirksam für diesen Agent (Tooltip/Doc machen das sichtbar)
```
