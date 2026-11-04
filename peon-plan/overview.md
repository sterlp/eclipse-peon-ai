# Plan: Model-Dropdown im Chat-Dialog

**PO-Entscheidungen (2026-11-01, plan-request.md):**
1. **Platzierung:** ActionsBar, direkt neben dem Agent-Combo.
2. **Scope:** nur Base-Model (`PREF_MODEL` / `LlmConfig.model`); Pro-Agent-Overrides aus der
   Advanced-Page bleiben wirksam (Tooltip + Doc).
3. **Persistenz:** `PREF_MODEL` schreiben → bestehender `prefListener → applyConfig`-Pfad
   (Settings und Chat bleiben sync, überlebt Restart). **Keine Core-Änderung.**

## Design

- **Neu `ChatModelComboWidget`** (`org.sterl.llmpeon.parts.widget`): editierbares SWT-Combo
  + kleiner Refresh-Button, `RowData`-Layout (RowLayout-tauglich für die ActionsBar).
  Controller wie `ModelComboWidget`:
  - ctor `(Composite parent, Supplier<LlmConfig> liveConfig, Consumer<String> onModelSelected)`
  - `setModel(String)` — programmatisch, mit Guard-Flag (Selection-Callback nicht feuern)
  - `fetchModels()` — Job + `ModelListCache.getOrFetch` +
    `ModelComboWidget.baseSnapshot(liveConfig.get())` (R-ML1: Identity LIVE zur Fetch-Zeit)
  - Refresh-Button — `cache.refresh`, Fehler → `cached(identity)`-Fallback (kein Auto-Switch)
  - Selection → `onModelSelected.accept(combo.getText())`
- **Refactor `ModelComboWidget`** (Repo-Regel "one behaviour, one implementation"): R-ML4-Apply
  als `public static applyModelListTo(Combo, List<AiModel>)` extrahieren; der Stale-Guard
  (Identity-Vergleich) bleibt widget-seitig. Beide Widgets nutzen die eine Implementierung.
- **`ActionsBarWidget`**: ctor +2 Params (`Supplier<LlmConfig> liveConfig`,
  `Consumer<String> onModelChange`); Model-Combo direkt nach Agent-Combo;
  `lockWhileWorking` sperrt das Model-Combo mit.
- **`AIChatView`**: wiring `LlmPreferenceInitializer::buildWithDefaults` +
  `this::onModelChange`; `onModelChange` schreibt `PREF_MODEL` (`StringUtil.stripToEmpty`) +
  `flush()` → prefListener → `applyConfig` (synchron, UI-Thread). Nach `applyConfig` Combo-Text
  idempotent syncen. Fetch im `createPartControl` nach `applyConfig()` (Cache-on-success →
  kein Duplikat-HTTP).
- **Keine Core-Änderung** (Mechanik vorhanden: `ConfiguredChatModel.withModel`,
  `ModelListCache`, `LlmConfig.resolveModel`).

## BDD-Tests (neu `ChatModelComboWidgetTest`, PDE/SWT, `AbstractSwtUiTest`)

```
GIVEN der Chat-View ist offen und die Modell-Liste der Base-Verbindung ist geladen
WHEN   der User ein anderes Model im Chat-Dropdown wählt
THEN   PREF_MODEL zeigt das gewählte Model
AND    die nächste Config (buildWithDefaults) trägt das neue Model

GIVEN der List-Fetch schlägt fehl (enableModelsError)
WHEN   der User den Chat-Dropdown öffnet
THEN   das aktuell konfigurierte Modell bleibt gesetzt (kein Fehler, kein Auto-Switch)

GIVEN die Liste ist geladen und das konfigurierte Model ist case-insensitiv enthalten
WHEN   die Liste angewendet wird
THEN   der serverseitige (canonische) Eintrag ist ausgewählt (R-ML4)
```

## Betroffene Dateien

- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/widgets/ModelComboWidget.java` (Refactor)
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/widget/ChatModelComboWidget.java` (neu)
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/widget/ActionsBarWidget.java`
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java`
- `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/ChatModelComboWidgetTest.java` (neu)
- Docs: `org.sterl.llmpeon/docs/model-loading.md` (neue Sektion Chat-Oberfläche),
  `org.sterl.llmpeon/docs/interaction-design.md` (Section 3 an Implementation anpassen)
- Homepage: User-visible Change → Update im selben Inkrement (AGENTS.md)

## Schritte

1. `ModelComboWidget`: R-ML4-Apply als shared `public static applyModelListTo` extrahieren.
2. `ChatModelComboWidget` schreiben.
3. `ActionsBarWidget`: Model-Combo + Ctor + lockWhileWorking.
4. `AIChatView`: Wiring (liveConfig-Supplier, onModelChange → PREF_MODEL, fetch on open).
5. `ChatModelComboWidgetTest` (BDD oben; PREF_MODEL-Restore in setUp/tearDown).
6. Docs + Homepage.
7. Gates.

## Verifikation / Gates

- `eclipseBuildProject` `org.sterl.llmpeon` UND `org.sterl.llmpeon.test` (Core unchanged →
  kein Core-Surefire, kein `mvn -o -pl ... package` nötig).
- `eclipseRunJavaTests` `org.sterl.llmpeon.test` (usePluginTest=true) — neu:
  `ChatModelComboWidgetTest`, Regression: `ModelComboWidgetTest`, `AiConfigPreferenceViewTest`.
- Kein Commit ohne PO.
