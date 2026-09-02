# Plan — inc-25: Basic-Config-Page Model-Dropdown + Refresh (shared `ModelComboWidget`)

Branch `new-config` @ 44723b0 · Plugin-only (Core unchanged) · **1 Commit** (PO) · 2 green stages
SOLL: PO-Request 2026-09-02 (Smoke-Test-Follow-up 1) — Basic-Page wie per-agent Sections: Modell-Dropdown (CCCombo) + Refresh.

## Status
- Stage 1 (Extraktion + Section-Update): ✅ done — `ModelComboWidget` neu (D1), `AgentModelConfigSection` Widget-Konsument (D2, kein Behavioral-Change), `AgentModelConfigFetchTest` auf `ModelComboWidget.*` umgestellt. Gate: Build grün + `AgentModelConfigFetchTest` + Suite grün.
- Stage 2 (Basic-Page + Test + Homepage): ✅ done — `AiConfigPreferenceView` mit `ModelComboWidget` + `performOk` (D3), `ModelComboWidgetTest` (4 Tests, BDD 1/2/3/5), Homepage `configuration.md` „Model"-Abschnitt. Gate: Build grün + **vollständige Plugin-Suite 126/126**.
- **Abweichung (geflaggt):** SWT-Test nutzt das **Workbench-Display** (`Display.getDefault()` + alle Widget-Zugriffe UI-Thread via `EclipseUtil.runInUiThread`), nicht `new Display()` — PDE-Launch hat bereits ein Display, SWT erlaubt nur eines pro Prozess (`SWTError: Not implemented [multiple displays]`). Pattern als SKILL gespeichert (`skills/eclipse-dpe/swt-display-test.md`). `JobManager`-Check durch observable-State-Pumps ersetzt (PDE-Fragment-Klassenpfad löst `org.eclipse.core.runtime.jobs` nicht; Manifest-Require half nicht ohne Container-Refresh).

## 1. Context

- `AiConfigPreferenceView` (Basic-Page, "Peon Configuration") hat `StringFieldEditor(PREF_MODEL, "Model:")` — freies Textfeld, kein Dropdown, kein Refresh.
- `AgentModelConfigSection` (Advanced-Page, per-agent) hat die vollständige Logik: CCombo + Refresh-Button,
  `prepareFetch()` (UI-Thread-Snapshot), `Job` → `ModelListCache.getOrFetch` / `refresh` / `cached`,
  `applyModelList` mit Stale-Guard (`identity` changed while fetching → discard), configured-model-keep.
- Ziel: dieselbe Logik DRY auf der Basic-Page. Extraktion in ein Shared-Widget mit
  **Snapshot-Provider** (`Supplier<FetchSnapshot>`): Basic liefert Base-Connection, per-agent liefert Base+Overrides.
  `AgentModelConfigSection` wird Widget-Konsument (kein Behavioral-Change).
- Verifiziert: `FetchSnapshot`/`fetchList` werden nur von `AgentModelConfigSection` +
  `AgentModelConfigFetchTest` referenziert (Grep, 2026-09-02) → Move ohne weitere Aufrufstellen.

## 2. Design decisions

- **D1 — Neues Widget `ModelComboWidget`** (`org.sterl.llmpeon.parts.config.widgets`, Plugin),
  baut CCombo + Refresh und besitzt die komplette Fetch/Apply/Stale-Guard-Logik (heutige
  `AgentModelConfigSection`-Methoden `fetchModels`, `refreshModels`, `currentIdentity`→via Provider,
  `prepareFetch`→via Provider, `fetchList`, `applyModelList`, `buildModel`, `FetchSnapshot`):

  ```java
  public class ModelComboWidget extends Composite {
      // Label "Model:" ist hartkodet (beide Call-Sites identisch)
      public ModelComboWidget(Composite parent, String jobName, Supplier<FetchSnapshot> snapshotProvider)
      public void setModel(String model)          // → CCombo.setText(stripToEmpty)
      public String getModel()                    // → CCombo.getText()
      public void fetchModels()                   // page-open: ModelListCache.getOrFetch (1 HTTP je Identität)
      // Refresh-Button intern → refreshModels()  // immer refetch; Failure → alte Liste bleibt

      public record FetchSnapshot(ConnectionIdentity identity, LlmConfig buildConfig) {}
      /** SWT-free — safe in background Job (heutige Test-Signatur, nur neuer Ort). */
      public static Supplier<List<AiModel>> fetchList(FetchSnapshot snapshot)
      /** SWT-free Base-Snapshot für die Basic-Page (keine per-agent Overrides). */
      public static FetchSnapshot baseSnapshot(LlmConfig base)
  }
  ```
  - `jobName` → `Job.create("Loading models (" + jobName + ")")` (section: agentId, basic: `"base"`) —
    bleibt im Jobs-View unterscheidbar wie heute.
  - Layout: `GridData horizontalSpan=2` (passt in beide Eltern-Grids: section `GridLayout(2,false)`
    und `FieldEditorPreferencePage(GRID)`); intern `GridLayout(3,false)`: [Label "Model:"][CCombo grow][Button Refresh].
    Visuell identisch zu heute (Label + Row mit Combo + Refresh).
  - Stale-Guard in `applyModelList`: `if (!identity.equals(snapshotProvider.get().identity())) return;`
    — Provider wird nur auf dem UI-Thread aufgerufen (`prepareFetch`-Semantik bleibt erhalten:
    Widgets nur UI-Thread lesen; Job-Body liest ausschließlich den Snapshot).
  - `applyModelList`-Items-Logik 1:1 übernehmen (fetched → items; configured nicht in Liste → anhängen;
    configured → select, sonst setText; SWT-Disposal-Guard via `EclipseUtil.runInUiThread(this, …)`).
- **D2 — `AgentModelConfigSection` wird dünner (kein Behavioral-Change):**
  - `modelCombo`-Feld weg → `private final ModelComboWidget modelWidget;`
  - `buildModel()` → `modelWidget = new ModelComboWidget(this, agentId, this::prepareFetch);`
  - `prepareFetch()` bleibt in der Section (liest Section-Widgets + `base`) — wird zum Supplier.
  - `load(record)`: `modelWidget.setModel(record.model())`; `getRecord()`: `StringUtil.stripToNull(modelWidget.getModel())`.
  - `fetchModels()` delegiert an `modelWidget.fetchModels()`.
  - Weg: `refreshModels`, `currentIdentity`, `FetchSnapshot`, `fetchList`, `applyModelList`, `buildModel`-Body.
- **D3 — Basic-Page-Integration (`AiConfigPreferenceView`):**
  - `StringFieldEditor(PREF_MODEL)` wird durch `ModelComboWidget` ersetzt (gleiche Position: direkt nach `providerEditor`).
  - Snapshot-Provider liest **live** die Preferences (UI-Thread):
    ```java
    modelWidget = new ModelComboWidget(getFieldEditorParent(), "base",
        () -> ModelComboWidget.baseSnapshot(LlmPreferenceInitializer.buildWithDefaults()));
    modelWidget.setModel(getPreferenceStore().getString(PeonConstants.PREF_MODEL));
    modelWidget.fetchModels();
    ```
    `buildWithDefaults()` liest InstanceScope live (StringFieldEditors schreiben live ins Store) →
    Identity bleibt aktuell, wenn der User Provider/URL/Key auf derselben Seite ändert (Stale-Guard greift).
  - Persistenz (wie Advanced-Page-Pattern `performOk`):
    ```java
    @Override public boolean performOk() {
        getPreferenceStore().setValue(PeonConstants.PREF_MODEL, StringUtil.stripToNull(modelWidget.getModel()));
        return super.performOk();
    }
    ```
    `PREF_MODEL` wird nirgendwo sonst geschrieben (Grep: nur Initializer-Default + diese View) → kein Race.
- **D4 — `ModelComboWidget.baseSnapshot`** ist der SWT-free Seam für die Basic-Page
  (delegiert an `base.effectiveConnectionFor(AgentModelConfig.empty())` → `FetchSnapshot(identity, buildConfig)`).

## 3. Architecture decisions

- Kein Core-Change: `ModelListCache` (single-flight, inc-24), `ConnectionIdentity`, `LlmConfig`,
  `LlmPreferenceInitializer` bleiben unverändert.
- SWT-Schicht im Plugin (`parts.config.widgets`); SWT-free Seams (`fetchList`, `baseSnapshot`) statisch im
  Widget → ohne Display unit-testbar (erweiterter Test bleibt grün).
- Datenfluss Basic: `store → buildWithDefaults() → effectiveConnectionFor(empty()) → FetchSnapshot →
  Job → ModelListCache → applyModelList (UI) → CCombo → store (performOk)`.
- Kein Preference-Change-Listener nötig (Page wird pro Öffnung neu gebaut; `createFieldEditors` ruft `fetchModels`).

## 4. Affected files (alle Pfade verifiziert)

| File | Änderung |
|---|---|
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/widgets/ModelComboWidget.java` | **neu** — D1 |
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/widgets/AgentModelConfigSection.java` | D2 — model-Logik raus, Widget rein |
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/AiConfigPreferenceView.java` | D3 — StringFieldEditor(PREF_MODEL) → Widget + `performOk` |
| `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/AgentModelConfigFetchTest.java` | Referenz-Update: `AgentModelConfigSection.FetchSnapshot/fetchList` → `ModelComboWidget.*` (Logik bleibt) |
| `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/ModelComboWidgetTest.java` | **neu** — Display-basiert (s. Test strategy) |
| `homepage/src/setup/configuration.md` | Nutzer-doku: Model-Feld = Dropdown + Refresh (AGENTS.md: visible change → gleicher Increment) |

Unberührt (Regression-Gates): `ModelListFetchTest`, `ModelListCache` (Core), alle Provider, `AiAdvancedPreferenceView`, `LlmPreferenceInitializer`.

## 5. Rules & constraints

- **Kein Behavioral-Change** der per-agent Sections (BDD-4 = Regression): Job-Namen, Cache-Semantik
  (getOrFetch/refresh/cached), Stale-Guard, configured-model-keep bleiben identisch.
- SWT nur UI-Thread: Snapshot-Provider/Widgets ausschließlich auf dem UI-Thread lesen
  (heutiges `prepareFetch`-Kommentar bleibt gültig; `EclipseUtil.runInUiThread` für Apply).
- Log OR throw, nie beide (Job-Body: `ModelListCache` loggt selbst — Widget loggt nichts zusätzlich).
- Keine Secrets in Logs/toString (memory #20) — `ConnectionIdentity`-Masking aus inc-24 nicht rückgängig machen.
- Tests: JUnit 4, OSGi, **keine** externen Assertion-Libs (workspace-memory #1); `ModelListCache.instance().clear()`
  pro Test (Setup + Teardown) — kein verlässlicher persistenter Cache-State zwischen Tests (memory #12).
- Vor Plugin-Testläufen: `eclipseBuildProject` über `org.sterl.llmpeon` **und** `org.sterl.llmpeon.test`
  (stale Bundle-Klassen → ClassNotFoundException, memory #16). Erster PDE-Lauf nach Neu-Launch:
  Workspace-Trust im UI bestätigen (memory #13).
- `docs/` (SOLL) wird NICHT vom Dev-Agent angefasst (PO-Ownership).

## 6. BDD acceptance (PO-Tabelle → Tests)

| # | Scenario | Test / Gate |
|---|---|---|
| 1 | Basic-Page lädt → Dropdown + Refresh sichtbar | `ModelComboWidgetTest.dropdownAndRefreshBuilt` + Manual-Smoke |
| 2 | Modell gesetzt → Liste + configured Modell im Dropdown | `ModelComboWidgetTest.fetchShowsListAndKeepsConfiguredModel` |
| 3 | Refresh → neu gefetcht, Dropdown aktualisiert | `ModelComboWidgetTest.refreshRefetchesAndUpdates` |
| 4 | Advanced-Page per-agent → wie vorher (Regression) | `AgentModelConfigFetchTest` (updated) + `ModelListFetchTest` + Manual-Smoke |
| 5 | Fetch-Fehler → alte Liste bleibt | `ModelComboWidgetTest.refreshFailureKeepsPreviousList` |

```
GIVEN Basic-Page-Widget mit MockLlmServer-Connection und gesetztem Modell "gpt-4o"
WHEN fetchModels (page-open)
THEN Dropdown enthält [gpt-4o, mock-model] und "gpt-4o" ist gesetzt

GIVEN erfolgreich gefetchte Liste
WHEN Modell-Liste am Server ändert sich AND Refresh
THEN Dropdown zeigt die neue Liste

GIVEN erfolgreich gefetchte Liste
WHEN Server liefert Fehler (500) AND Refresh
THEN Dropdown zeigt die alte Liste (kein Clear, kein Auto-Switch)
```

## 7. Test strategy

- **`ModelComboWidgetTest` (neu, JUnit 4, `extends AbstractUnitTest`)** — erster SWT-Display-Test des Projekts:
  - `@Before`: `display = new Display()`; **Assume-Guard**: headless → `Assume.assumeNoException(...)` → Skip statt Fail.
    (Tycho-Pom hat bereits macOS-`-XstartOnFirstThread` für SWT; PDE-Lauf = Workbench mit Display.)
  - `@After`: `ModelListCache.instance().clear()` + `display.dispose()` (nur wenn nicht disposed).
  - Widget-Setup: `new ModelComboWidget(shell-Composite, "test", () -> baseSnapshot(mockLlmServer.newConfig("gpt-4o")))`
    + `setModel("gpt-4o")` + `fetchModels()`.
  - **Pump-Loop** (Job-Body läuft im JobManager-Thread; Apply kommt via `asyncExec`):
    `while (!cond && now < deadline) { if (!display.readAndDispatch()) display.sleep(); }` —
    Deadline **5 s**, danach `fail(...)` (expliziter Timeout, kein Hang).
  - B3 ohne Request-Counter (Core unverändert): `mockLlmServer.setModelIds(...)` zwischen fetch und refresh
    ändern → neue IDs im Combo = Refetch bewiesen. B5: `mockLlmServer.enableModelsError()`.
  - Assertions direkt am SWT-Zustand (`getItems()`, `getSelectionIndex()`, `getText()`), JUnit-4-`Assert`.
- **`AgentModelConfigFetchTest`**: nur Import/Referenz auf `ModelComboWidget.FetchSnapshot` + `ModelComboWidget.fetchList`
  (SWT-frei, bleibt grün) — verankert, dass die SWT-free Seam am neuen Ort dieselbe Signatur hat.
- **Regression-Gates** (unverändert laufen): `ModelListFetchTest` (5 Tests, sequenziell, Cache+HTTP-Pfad).
- Manual-Smoke (User): Basic-Page öffnen → Dropdown/Refresh; Advanced-Page öffnen → 4 Sections wie vorher.
- Core: kein Build nötig (unverändert).

## 8. Increments (1 Commit)

- **Stage 1 (green):** `ModelComboWidget` extrahieren (D1) + `AgentModelConfigSection` darauf umstellen (D2) +
  `AgentModelConfigFetchTest`-Referenzen. Gate: `eclipseBuildProject(org.sterl.llmpeon)` +
  `AgentModelConfigFetchTest` + `ModelListFetchTest`.
- **Stage 2 (green):** Basic-Page (D3) + `ModelComboWidgetTest` + Homepage-Update. Gate: `eclipseBuildProject`
  (beide Projekte) + **gesamte** Plugin-Test-Suite (`ModelListFetchTest`, `AgentModelConfigFetchTest`,
  `ModelComboWidgetTest`, Rest) + Manual-Smoke.
- **Commit:** `inc-25: basic-config: model dropdown + refresh (shared ModelComboWidget extraction)`
  + Trailer `Assisted-by: Peon AI (<ModelName>)`.

## 9. Open questions

- **Q1 (nicht blocking, Default gewählt):** Display-basierter Widget-Test (Assume-guarded) — Alternative wäre
  SWT-frei nur + Manual-Smoke. Default: Test bauen (PO-Gate sagt "neue Tests"); wenn der Display-Lauf in der
  Test-Umgebung skippt, bleibt die Suite grün (kein neuer Flaky).
- **Q2 (PO, optional):** SOLL-Doku `docs/model-loading.md` um "Basic-Page-Dropdown" ergänzen — PO/USER-Ownership,
  kein Dev-Schritt (Homepage-Update ist dagegen im Increment enthalten).

## 10. Skill-Hint (AGENTS.md §Reference)

`skills/eclipse-dpe/` ist leer. Wenn der Display-basierte Test (Pattern: `new Display()` + Assume-Guard +
`readAndDispatch`-Pump + `ModelListCache.clear()` + Tycho `-XstartOnFirstThread`/PDE-Launch-Verhalten) im
Dev-Lauf als working Pattern bestätigt wird → als SKILL speichern (erster SWT-UI-Test im Projekt).
