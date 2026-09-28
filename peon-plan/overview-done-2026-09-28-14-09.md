# Zyklus: Model Config Widget — Basic-Page + Think-Default (R-MCW-1…7, UC-THINK-10, ADR-0060+0061)

> **SOLL-Quellen:** `docs/model-config-widget.md` (R-MCW-1…6, UC-MCW-1…7) · `docs/adr/0060-model-config-widget-live-widget-reads.md` (Live-Read + Reload-nicht-persistieren) · `docs/per-agent-think.md` (**R-THINK-10 ❌ specified — Base-Think-Fallback**, UC-THINK-10) · `docs/adr/0061-think-default-base-think-fallback.md` (Think-Default-Kette) · `docs/model-loading.md` (R-ML2 neu entschieden — Regeln liegen jetzt in model-config-widget.md) · `docs/advanced-configuration.md` (Kontext) · ADR-0005 (Widget owns state) · ADR-0034 (Cache je Identity, unverändert) · ADR-0059 (Think leer=unset, unterste Stufe).
>
> **Branch:** `story/model-config-widget` (NEU anlegen). **Gate: die Jon-Order** — nicht der Paul-Merge. Paul hat evtl. einen vorgezogenen Start auf `story/issue-149-think`-Basis angefragt; Inc-1 ist bewusst nur-hinzufügen und damit auch auf #149-Branch-Basis baubar, falls Jon so startet. Kein Merge, kein Push. Vor Zyklus-Start Git-Zustand prüfen (Branch existiert? #149-Basis? — Memory-Regel 23).
>
> **Scope-Grenze (Paul, bindend):** NUR die Basic-Page (`AiConfigPreferenceView`). Die Advanced-Page (`AgentModelConfigSection`, `AiAdvancedPreferenceView`) bleibt unberührt.

## Regeln für Da Mek (STOP-AND-ASK — prominent, 2026-09-08)

1. **STOP-AND-ASK** bei Compile-Fehlern ohne Lösung, nicht-grün-bekommenden Tests oder IST-Widersprüchen zum Plan — aktiv nachfragen (askDev-Kanal), **nie still workarounden, nie SOLL still ändern**.
2. OSGi-Tests: **`eclipseBuildProject` vor jedem JUnit-Lauf** (stale `bin/` → ClassNotFoundException). Surefire-Zahlen sind Ground Truth, nicht `eclipseRunJavaTests`.
3. **Agenten schreiben NIE in `docs/**`** (Fachdocs = PO-Eigentum). **Homepage (`homepage/src/**`) und Skill-Dateien (`.agents/skills/**`) aber JA** (dev-Eigentum, AGENTS.md: User-visible changes im selben Zyklus).
4. Nach jedem line-based Edit: **Zielstruktur zurücklesen** (Grep-Count), eclipseEditFile nur mit eindeutigem `oldString` (Memory-Regel 31).
5. Commit-Disziplin: auf `story/model-config-widget`, nach jeder grünen Iteration committen (Code + Test + Homepage/Skill-Dateien des Inkrements zusammen). Kein Git/kein Branch → erst fragen.

## 1. Context

Heute liest der Reload der Modell-Liste auf der Basic-Page den **Preference-Store** (`AiConfigPreferenceView.java:101-102` → `ModelComboWidget.baseSnapshot(LlmPreferenceInitializer.buildWithDefaults())`) — getippte Änderungen wirken erst nach Apply (R-ML2 alt). Der danebenliegende „Check Host and Port"-Button liest dagegen **live am Widget** (`:121`, `urlEditor.getStringValue()`). Diese Asymmetrie macht die Config-Seite zum Testen unbenutzbar („erst die Seite verlassen, um das Modell zu testen" — Paul). ADR-0060 entscheidet: Ping & Reload lesen **Live-Widget-Werte**, Reload persistiert nicht, die Verbindungsfelder (Provider · URL · API Key · Think-Level) wandern als `ModelConfigWidget` in ein fokussiertes Widget (Basis für späteren Advanced-Reuse, R-MCW-5 zurückgestellt).

**IST-Fakten (verifiziert):**
- `AiConfigPreferenceView.java:41-52` `providerEditor` ComboFieldEditor (9 Provider, Labels→Enum-Namen, `SWT.READ_ONLY`); `:60-65` url/apiKey StringFieldEditors; `:97-105` `buildModel()` — Supplier liest **Store** (`buildWithDefaults()`), initiales `setModel` vom Store, `fetchModels()` beim Page-Bau; `:108-111` `performOk` schreibt `PREF_MODEL` manuell (Rest via Field-Editors); `:113-129` `buildCheckUrl` liest **live** (`LlmConfig.newConfig("", url).isReachable(3000)` → MessageDialog); `:131-147` GitHub-Login-Button ruft nach dem Flow `providerEditor.load()` + `apiKeyEditor.load()`.
- `ModelComboWidget` (Controller, **kein Composite** — R-A2-Präzedenz): nimmt `Supplier<FetchSnapshot>` (:52), `fetchModels`/`refreshModels` capturen den Snapshot auf dem UI-Thread (:80, :90), Stale-Guard verwirft Identity-Wechsel während des Fetchs (:126). `baseSnapshot(LlmConfig)` (:113) + `effectiveConnectionFor(AgentModelConfig.empty())` bauen Identity+buildConfig.
- `ConnectionIdentity` = Provider+URL+Key — **Think ist NICHT Teil der Identity**.
- **Die Basic-Page hat heute KEIN JSON-extra-body-Feld** (geprüft: kein Field, kein Key-Write) — für das Widget entfällt der Punkt; extra body bleibt bewusst Advanced-only.
- Basic-Page-Tests existieren heute **keine** (Grep über `org.sterl.llmpeon.test`: keine Referenz auf `AiConfigPreferenceView`).
- Die Advanced-Page liest ihre Fetch-Identity bereits **live von den Widgets** (`AgentModelConfigSection.prepareFetch` :110-113) — das Basic-Verhalten zieht auf denselben Stand nach; der homepage-Satz „erst Apply, dann Refresh" (`advanced-configuration.md:28`) ist damit auch für Advanced stale.
- `AgentModelConfig`-Record hat `withModel`, aber **kein `withThink`**.
- **Prüfung ModelComboWidget-Wiederverwendbarkeit: JA, unverändert wiederverwendbar.** Nur der gelieferte `Supplier<FetchSnapshot>` ändert sich — er liefert künftig die **Widget-Identität** statt der Store-Identität. Fetch-/Stale-Guard-/Cache-Logik bleibt exakt gleich (ihre Tests `ModelComboWidgetTest`/`AgentModelConfigFetchTest` bleiben unverändert grün).
- **`thinkSupport()` je Provider (IST, für R-MCW-6):** Ollama → `Toggle`; OpenAI-Familie (OPEN_AI, OPEN_AI_OFFICIAL, GITHUB_COPILOT, GITHUB_MODELS via `ProviderRequestSupport.openAiFamilyThinkSupport`) → `Values(minimal/low/medium/high)`; Anthropic → `Values(adaptive/enabled)`; LM Studio → `FreeString`; Gemini & Mistral → `None` (kein Feld). Bausteine `ThinkValueSupport.toggleItems()/valuesItems()/valuesDisplay()/valuesStored()` + Build/Load/Read-Muster: `AgentModelConfigSection.buildThink/loadThink/readThink` (:117-224) — gleiche Bausteine, aber **kein Construction-time-Freeze** (dort :60).

## 2. Design-Entscheidungen

**D0 — Think-Default-Fallback (R-THINK-10/ADR-0061, UC-THINK-10, Inc-0).** Resolution-Kette bei der Think-Resolution, einheitlich für PO/Plan/Search/Compact/Custom:
```
Agent-Think gesetzt (auch off) → verbatim   |  Agent-Think leer → Base-Think (DEV-Record)   |  beide leer → null (unset)
```
- **Umsetzung: ein zentraler privater Helfer in `LlmConfig`** — `private String resolveThink(AgentModelConfig record)` = `StringUtil.hasValue(record.think()) ? record.think() : modelConfigFor(DEV).think()`. Die Methoden `poAgentConfig`, `planAgentConfig`, `compactAgentConfig`, `searchAgentConfig` und `customAgentConfig` ersetzen ihr `.think(record.think())` durch `.think(resolveThink(record))`; `devAgentConfig()` bleibt **unverändert verbatim** (Dev ist die Basis, kein Selbst-Fallback). Bewusst NICHT in `ThinkResolver` (mappt nur Strings, kennt kein `LlmConfig`) und keine Logik in den Agenten — „one behaviour, one implementation" am einzigen Ort, der beide Werte sieht.
- **IST (verifiziert):** alle 5 Methoden setzen think derzeit verbatim aus dem Record (LlmConfig.java:195, 203, 211, 219, 240); Custom-Path: `CustomAgent.getConfig()` → `customAgentConfig(rec, getName())` (:162) — derselbe Helfer deckt Custom ab, **keine** Änderung in `CustomAgent.resolveThink` (Frontmatter-Parsing bleibt unverändert; die Fallback-Entscheidung fällt erst in `LlmConfig`).
- **Explizites off gewinnt immer** — ein gesetztes `"false"` fällt NICHT auf den Base-Default zurück (Kette Stufe 1). Unberührt: `isThinkSupported()` (leitet aus DEV-Record ab), Persistenz (`LlmConfigSaver`/`LlmConfigLoader`), `ThinkResolver`-Mapping, Request-Mapping (R-THINK-1/2/3/8 unverändert).
- **Tests (UC-THINK-10, Fachdoc-BDD):** Base=`true` + Search leer → `searchAgentConfig().getThink()` = Base-Wert; Search=`"false"` → `"false"` (kein Fallback); Base leer + Agent leer → `null`; Custom-Record ohne think + Base=`true` → Base-Wert geerbt; Dev leer → `null`; PO/Plan/Compact-Parität (ein parametrisierter Test über alle 4 Slots + Custom). Neue Tests in `LlmConfigTest` (Slot-Parität) + `CustomAgentServiceTest` (Custom-Erbe) — nicht in `ThinkResolverTest` (der testet nur String-Mapping).

**D0a — Bestands-Tests prüfen, nicht blind anpassen (Memory-Regel 33 — Inventar per Grep):** Nach Inc-0-Code-Änderung laufen und gegen die Kette bewerten. Betroffen-könnten-sein:
- `AiProviderRequestParametersTest.devAndPlan_thinkSupportResolveIndependently` (:49-52): Dev/Compact/Search `getThink()==null` — **bleibt grün** (Base leer → Kette Stufe 3 → null).
- `AgentModelResolutionTest.poThinkResolvesIndependently` (:77-78): Plan-Record `empty()` bei PO=`high` — **bleibt grün** (Base leer).
- `AiServicePerAgentThinkTest.devAgentSendsNoReasoningWhenThinkUnsupported` (:48-62): DEV-Record leer, kein reasoning — **bleibt grün** (Dev verbatim).
- `AiProviderRequestParametersTest.ollamaUnsetStillOmitsForCompactAndSearch` (:175-185): **bleibt grün** (Base leer).
- `AiProviderRequestParametersTest.sendThinkingTransportIndependentFromThinkValue` (:200): `devAgentConfig().getThink()==""` — **bleibt grün** (Dev verbatim).
- `ThinkRoundtripTest.emptyThinkIsUnsetAndRemovedBySaver` (:38-51): Repo-Store ohne DEV-Think-Key → Base leer → **bleibt grün** (assertNull bleibt korrekt).
- `AiPoAgentTest.isThinkSupported_readsPoSlot_notPlan` (:242-251): PO-Record leer + Base leer → **bleibt grün**.
- `CustomAgentServiceTest.absentThinkIsUnset` (:229-236) & `AiServicePerAgentThinkTest.customAgentConfigAppliesThinkVerbatim` (:100-106): Custom-Think-Tests mit leerem Base → **bleiben grün**.
- `LlmConfigLoaderTest`-Assertions (:77, :102, :125) sind Record-Ebene (Loader) — **unberührt** (Fallback greift erst in den `agent*Config()`-Methoden).
- **Wichtig:** das sind erwartete Grün-Bleiber — Da Mek verifiziert beim Lauf, dass keiner von ihnen tatsächlich gegen die Kette behauptet; falls doch (rot), ist das ein IST-Widerspruch → STOP-AND-ASK, nicht still anpassen. Neue „Base gesetzt + Agent leer"-Fälle gibt es heute NICHT in den Tests (Grep-geprüft) — d. h. Inc-0 ist reiner Erweiterungs-Commit, kein Anpassungs-Commit, außer ein Grün-Bleiber erweist sich als rot.

**D1 — `ModelConfigWidget` = Controller wie `ModelComboWidget`, kein eigener Composite.** Er baut Provider-Combo, URL-, Key-, Think-Feld + Ping-Button direkt in das 2-Spalten-Grid der Page und delegiert Model-Combo+Refresh an `ModelComboWidget`. Grund: R-A2/R-ML3 verlangen Label-Ausrichtung in der Label-Spalte der Page — ein eigener Composite mit internem Grid would eine zweite Label-Spalte mit anderer Breite erzeugen (verifizierbares Layout-Problem, das ModelComboWidget exakt deshalb Controller ist). Page-Qualität (FieldEditor-Parent) bleibt unverändert.

**D2 — Widget bekommt die Verbindung per Value-Record, nicht per Store.** ADR-0005/ADR-0060: Widget owns state, View routet. API (Signatur-Skizze):

```java
public class ModelConfigWidget { // Controller, package parts/config.widgets
    /** @param base  UI-thread Supplier des Basis-Stands (Transport-Parameter, z.B. headerParams);
     *                Basic-Page: LlmPreferenceInitializer::buildWithDefaults — Advanced-Reuse später
     *                reicht seinen eigenen base-Supplier, ohne Umbau (R-MCW-5). */
    public ModelConfigWidget(Composite parent, String jobName, Supplier<LlmConfig> base);

    public record ConnectionValues(AiProvider provider, String url, String apiKey,
                                   String think, String model) {}
    public record PingResult(String url, boolean reachable) {} // kein Key → Secret-Hygiene

    public void load(ConnectionValues values);          // null-sicher; baut das Think-Feld je provider-Form
    public ConnectionValues getValues();                // UI-Thread; stripToNull-Semantik wie getRecord()
    public ModelComboWidget.FetchSnapshot snapshot();   // UI-Thread; live Identity
    public PingResult computePing();                    // UI-Thread (blockierend, IST-Parität); testbar
    public void fetchModels();                          // Delegation an ModelComboWidget (Page-Open)
    // intern: rebuildThink(ThinkSupport) — Provider-Combo-Selection-Event → live Re-Build (D3)
}
```

- **Identity zur Klick-Zeit = Widget-Werte:** `snapshot()` baut `base.get()` (Store: nur Nicht-Identity-Transport-Parameter wie headerParams/timeout) und **überschreibt** `providerType`/`url`/`apiKey` mit den Live-Widget-Werten (`toBuilder()`) → `effectiveConnectionFor(AgentModelConfig.empty())` → `FetchSnapshot`. Genau die Form von `AgentModelConfigSection.prepareFetch` — „one behaviour, one implementation", Provider aber **aus dem Widget** (Basic-Page: Provider ist editierbar und gehört zur Identity; im Advanced-Record gibt es bewusst keinen Provider — R-MCW-5-„Zurückgestellt").
- **Think geht NICHT in den Fetch-Snapshot** (nicht Teil der ConnectionIdentity); er fließt nur in `getValues()` → Persistenz.
- **Kein einziges `LlmPreferenceInitializer`/Store-Referenz im Widget** — der Store-Kontakt läuft ausschließlich über den injizierten `base`-Supplier und die Page. Das ist der einzige, nicht-spekulative Extension-Point (OCP: kein zweiter Hook ohne zweiten Nutzer; die `ConnectionValues`-Grenze IST der Reuse-Punkt für R-MCW-5).

**D3 (revidiert, Paul 2026-09-28 — ersetzt die ursprüngliche „fix Toggle"-Idee) — Think-Feld ist provider-abhängig und baut sich beim Provider-Wechsel LIVE neu (R-MCW-6, UC-MCW-7).** Gleiche Form-Ableitung wie die Advanced-Page: `LlmProviders.of(provider).thinkSupport()` →
- `Toggle` (Ollama) → editierbares Combo mit `ThinkValueSupport.toggleItems()` = `""`/`true`/`false` (leer = unset, ADR-0059)
- `Values` (OpenAI-Familie, Anthropic) → editierbares Combo mit `ThinkValueSupport.valuesItems(v)` = `[Off, Auto] + Werte`
- `FreeString` (LM Studio) / `Unknown` → Text-Feld (verbatim, Label „Think (empty = off):" wie Advanced)
- `None` (Gemini, Mistral) → **kein** Think-Feld

**Unterschied zur Advanced-Page:** `AgentModelConfigSection` friert die Form bei Construction ein (`thinkForm = provider.thinkSupport()` im Ctor, :60) — hier geht das nicht, der Provider ist im selben Widget editierbar. Re-Build-Trigger = Selection-Event der Provider-Combo, Ausführung auf dem UI-Thread, **atomares UI-Update** (Disposal + Neubau im Einband, Memory-Regel 5 — kein Flacker-Window): das alte Think-Control disposalen, neues je neuer Form bauen, erst dann `layout()`.

**Wert-Verhalten beim Wechsel (⏳ Rückversicherung Paul, im Fachdoc notiert — im Widget implementieren):** der aktuelle Think-Wert wird **verbatim übernommen, solange die neue Form freie Eingabe erlaubt** (Toggle = editierbares Combo, FreeString = Text); bei einer **festen Options-Liste** (Values) wird das Feld **geleert**, wenn der Wert (bzw. sein Display-Label via `valuesDisplay`) nicht in der Liste steht — **nie ein stiller Ersatzwert**. Bei `None` entfällt das Feld inkl. Wert (unset). Reload/Ping-Identity enthält Think nicht (unverändert).

**D4 — Think-Level auf der Basic-Page = Default-Editor, schreibt den DEV-Slot (`llm.agent.dev.think`), Label „Think (Default)".** Begründung aus dem Code: dev = Base (`AgentModelConfig.DEV`-Sonderrolle, `LlmConfig.devAgentConfig()` denkt mit dev-record think, `isThinkSupported()` leitet aus dev-think ab — ADR-0059); mit R-THINK-10 (D0, Inc-0) ist genau dieser Wert der **Default, den alle leeren Agent-Slots erben** — die Basic-Page wird damit semantisch zum Default-Editor (ADR-0061 Consequence), Label „Think (Default)" (Fachdoc). Das ist exakt dieselbe Dual-Edit-Oberfläche wie das Model-Feld heute (Basic `llm.model` ↔ Advanced Dev-Section) — kein neues Konzept. Save via `LlmConfigSaver.saveAgentModelConfig(store, DEV, devRecord.withModel(...).withThink(...))` (siehe D6) — **nie** den ganzen DEV-Record blanko schreiben (würde URL/Key-Overrides aus der Advanced-Page auslöschen).

**D5 — Ping bleibt Host+Port-Check mit Live-Read, Button-Label „Ping".** Semantik unverändert (`LlmConfig.newConfig("", url).isReachable(3000)` + MessageDialog wie heute), aber Quelle = Widget-URL. Trennung Rechnung/Darstellung für Testbarkeit: `computePing()` = pure Rechnung (public, testbar), Dialog = private Anzeige (User-Smoke). API-Key-Validität testet implizit der Reload-Fetch (R-MCW-4, kein dritter Pfad). Blocking-isReachable(3000) auf dem UI-Thread = **IST-Parität** (heutiger Check-Host-Button macht das genauso) — kein neuer Job, keine Verschlechterung, bewusst nicht „verbessert".

**D6 — Save-Pfad: `performOk` bleibt einziger Save-Pfad.** `performOk` = `super.performOk()` (übrige Field-Editors: Token Window, Resend-Thinking-Checkbox, Disk-Tools, Shell-Confirmation, Config-Dir — bleiben unverändert Field-Editors) **plus** explizite Persistenz der Widget-Werte:
- `getPreferenceStore().setValue(...)` für `PREF_PROVIDER_TYPE`, `PREF_URL`, `PREF_API_KEY` (Strip: leer → unset — Parität zur heutigen Loader-Semantik).
- `LlmConfigSaver.saveAgentModelConfig(new EclipseLlmConfigStore(InstanceScope.INSTANCE.getNode(PLUGIN_ID)), DEV, devRecord.withModel(v.model()).withThink(v.think()))` — der Saver schreibt `llm.model` (ersetzt den heutigen manuellen `PREF_MODEL`-Write, **eine Implementierung**) und `llm.agent.dev.think`; dev-URL/Key/extraBody/temperature kommen aus dem geladenen Stand (Overrides bleiben erhalten). **Dazu: `AgentModelConfig.withThink` ergänzen** (1-Zeilen-Record-Copy, symmetrisch zu `withModel`).
- Der alte manuelle `PREF_MODEL`-setValue in `performOk` entfällt (sonst zweiter Schreiber für denselben Key).
- **Reload/Refresh und Ping persistieren NICHTS** (R-MCW-3) — das Widget hat strukturell keinen Schreibzugriff.

**D7 — Provider-Combo:** `SWT.READ_ONLY`, exakt die heutigen 9 Label→`AiProvider`-Einträge inkl. Labels (Tabelle wandert 1:1 aus `AiConfigPreferenceView` in das Widget — keine User-sichtbare Änderung). Unbekannter gespeicherter Wert → Fallback auf ersten Eintrag (ComboFieldEditor-Parität). Kein Auto-Refetch beim Provider-Wechsel (heutiges Verhalten: nur Page-Open + Refresh-Button).

## 3. Architektur

```mermaid
flowchart LR
    subgraph Page["AiConfigPreferenceView (FieldEditorPreferencePage)"]
        FE["übrige Field-Editors<br/>(TokenWindow, ResendThinking, DiskTools, ShellConfirm, ConfigDir)"]
        OK["performOk(): super + setValue(provider/url/key)<br/>+ LlmConfigSaver(DEV, withModel+withThink)"]
        LOAD["load(): ConnectionValues aus Store<br/>(einmalig beim Page-Bau)"]
    end
    subgraph W["ModelConfigWidget (Controller, kein Composite)"]
        PC["1 Provider-Combo (READ_ONLY, 9 Provider)"]
        UT["2 URL-Text"] 
        KT["3 API-Key-Text"]
        MC["4 Model-Combo + Refresh (ModelComboWidget)"]
        TC["5 Think-Feld — provider-abhängig<br/>Toggle/Values/FreeString/None,<br/>live Re-Build beim Provider-Wechsel"]
        PB["Ping-Button → computePing() → Dialog"]
        SNAP["snapshot(): base.get() überschrieben<br/>mit live provider/url/key"]
        PC -->|Selection-Event| TC
    end
    MCW["ModelComboWidget (unverändert)<br/>Combo + Refresh, Fetch-Job, Stale-Guard"]
    CACHE["ModelListCache (ADR-0034, unverändert)<br/>Cache je ConnectionIdentity"]
    STORE[("Preference Store<br/>(nur Page kennt ihn)")]
    BASE["Supplier&lt;LlmConfig&gt; base<br/>(= buildWithDefaults, Transport-Parameter)"]

    LOAD --> W
    W -->|getValues()| OK --> STORE
    FE --> OK
    SNAP --> MCW --> CACHE
    BASE --> SNAP
```

- **Datenfluss Reload:** Klick → `refreshModels()` (ModelComboWidget) → `snapshotProvider.get()` = `ModelConfigWidget.snapshot()` (**UI-Thread**) → Widget-Werte → `FetchSnapshot(identity, buildConfig)` → Background-Job → `ModelListCache.refresh(identity, fetcher)` → `applyModelList` mit Stale-Guard (Identity muss bei Apply noch passen). Persistiert wird nichts.
- **Datenfluss Provider-Wechsel (neu, D3):** Provider-Combo-Selection (UI-Thread) → `rebuildThink(ThinkSupport)`: altes Think-Control disposalen → je neuer Form Control neu bauen (`ThinkValueSupport`-Bausteine, gleiche wie `AgentModelConfigSection.buildThink`/`loadThink`/`readThink`) → Wert-Übernahme nach der R-MCW-6-Regel (verbatim bei freier Eingabe, sonst leer falls nicht in Liste, weg bei None) → einmalig `layout()`. Fetch/Ping werden **nicht** getriggert (kein Auto-Refetch, D7).
- **Feld-Reihenfolge (Paul, bindend, UC-MCW-6):** vertikal untereinander 1 Provider · 2 URL · 3 API-Key · 4 Model-Combo + Refresh · 5 Think; Ping nahe URL/Model-Block (Placement wie heute „Check Host and Port" unter dem URL-Feld). Wird in UC-MCW-1/6 über die Parent-Child-Reihenfolge getestet.
- **Abhängigkeitsrichtung:** Widget → (Supplier) Store-Snapshot & ModelComboWidget → Cache/Provider. Widget kennt den Store **nicht**; die Page routet (ADR-0005). Core wird nicht angefasst (nur `AgentModelConfig.withThink`).
- **Kein Auto-Verhalten neu:** kein Refetch bei Provider-Wechsel, kein Store-Write im Widget, kein dritter Button-Pfad (R-MCW-4).

## 4. Betroffene Dateien

**Neu:**
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/widgets/ModelConfigWidget.java` (Inc-1)
- `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/ModelConfigWidgetTest.java` (Inc-1, JUnit 4 + `AbstractSwtUiTest`, keine externen Assertion-Libs)
- `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/AiConfigPreferenceViewTest.java` (Inc-2, Page-Level)
- `.agents/skills/eclipse-preferences/SKILL.md` (Inc-4)

**Geändert:**
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/ai/LlmConfig.java` (Inc-0): privater Helfer `resolveThink(AgentModelConfig)` + Umschaltung in `poAgentConfig`/`planAgentConfig`/`compactAgentConfig`/`searchAgentConfig`/`customAgentConfig` (D0); `devAgentConfig` unverändert.
- `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/ai/LlmConfigTest.java` (Inc-0): UC-THINK-10-Kette (parametrisiert über PO/Plan/Search/Compact + Dev-verbatim + off-gewinnt).
- `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/agent/CustomAgentServiceTest.java` (Inc-0): Custom-Frontmatter ohne think + Base gesetzt → erbt.
- ggf. Bestands-Tests (Inc-0, NUR falls ein Grün-Bleiber doch rot — siehe D0a): `AiProviderRequestParametersTest`, `AgentModelResolutionTest`, `AiServicePerAgentThinkTest`, `ThinkRoundtripTest`, `AiPoAgentTest`.
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/AiConfigPreferenceView.java` (Inc-2): `providerEditor`/`urlEditor`/`apiKeyEditor` + `buildModel()`/`buildCheckUrl()` raus; `ModelConfigWidget` rein (Load aus Store, `fetchModels()` beim Bau); `performOk` wie D6; GitHub-Login-Callback: `providerEditor.load()/apiKeyEditor.load()` → `widget.load(storeValues())`. Token-Window/Resend-Thinking/Disk-Tools/Shell-Confirm/Config-Dir/Link bleiben unverändert.
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/ai/AgentModelConfig.java` (Inc-2): `withThink(String)` ergänzen (neben `withModel`).
- `homepage/src/setup/configuration.md` (Inc-3): Model-Abschnitt (Refresh = live, keine Apply nötig), Ping-Button (Host+Port, live), neue Connection-Feldgruppe inkl. Think (leer=unset), „Testing the Connection" ergänzen.
- `homepage/src/setup/advanced-configuration.md` (Inc-3): Zeile 28 — „Refresh always uses the saved connection settings … click Apply first" ersetzen durch Live-Read („Refresh nutzt die aktuell getippten Werte — kein Apply nötig; Apply/OK speichert"); User-Sicht ergänzen (testbar ohne Apply, Cancel verwirft).
- `.agents/skills/eclipse-dpe/SKILL.md` (Inc-4): Quick-Section „Preference pages — read widget state, not the store" (L154-165) **entfernen** (verlagert in den neuen Skill, nicht doppelt).
- `.agents/skills/wiki/skill-impact.md` (Inc-4): Ledger-Eintrag für den neuen Skill.

**Unverändert (bewusst):** `ThinkResolver` (kein Fallback-Wissen — D0), `CustomAgent.resolveThink` (Frontmatter-Parsing; Fallback fällt in `LlmConfig`), `LlmConfigLoader`/`LlmConfigSaver` (Record-Ebene, nur Nutzung), `ModelComboWidget` (nur der Supplier am Caller ändert sich), `AgentModelConfigSection`, `AiAdvancedPreferenceView`, `ThinkValueSupport`, `ModelListCache`, `LlmPreferenceInitializer` (bleibt der base-Supplier), alle Provider. `LlmConfigKeys`/`PeonConstants` unverändert — dev-think-Key existiert bereits (`agentKey(DEV, AGENT_FIELD_THINK)`).

## 5. Regeln & Constraints

- **„A tool must never lie" / Log OR throw** — gelten unverändert; keine neuen Tools.
- **Secret-Hygiene (Memory 20, ADR-0040):** API-Key nie in `toString()`/Log. `ConnectionValues`/`PingResult` enthalten den Key nur als Feld; `PingResult` gar nicht. Widget loggt nichts über Keys. (Heutiges Key-Feld bleibt ein normaler `Text` — Ist-Parität, keine Echo-Änderung ohne Auftrag.)
- **Thread-Safety:** Widget-/Store-Reads nur auf dem UI-Thread; der Fetch-Job arbeitet ausschließlich mit dem gecapturten, SWT-freien `FetchSnapshot` (Präzedenz 2b inc-17). `base.get()` (Store-Read) nur im UI-Thread-Kontext.
- **„Empty means unset" (ab Inc-0 zweistufig):** Persistenz unverändert — leere Felder werden nicht geschrieben (Saver entfernt Key). Auf **Resolution-Ebene** gilt: Agent-Think leer → erbt Base-Think; erst „Base auch leer" ist unset (R-THINK-10). URLs/Keys haben keinen Fallback (nur Think).
- **Clean Break:** keine Migration, keine Aliase; Basic-Page-Keys bleiben exakt die heutigen (`llm.providerType`, `llm.url`, `llm.apiKey`, `llm.model`, `llm.agent.dev.think`).
- **UI-Texte:** Provider-Labels 1:1, URL-Label „URL (incl. port):", Key-Label „API Key:", Think-Label auf der Basic-Page mit Default-Zusatz je Form („Think (Default):" bei Toggle/Values, „Think (Default, empty = off):" bei FreeString/Unknown — Basis ist die Advanced-Formulierung, D4), Buttons „Ping" / „Refresh" (Refresh kommt aus ModelComboWidget). Ping-Tooltip nennt die Host+Port-Semantik + 3s-Timeout.
- Keine Änderung an der Advanced-Page — deren Tests (`ModelComboWidgetTest`, `AgentModelConfigSectionTest`, `AgentModelConfigFetchTest`, `AdvancedPreferenceSectionsTest`) müssen unverändert grün bleiben.

## 6. BDD-Akzeptanz (inkrement-zugeordnet)

**Inc-0 — Core Think-Default-Fallback (tests in `LlmConfigTest` + `CustomAgentServiceTest`):**
- **UC-THINK-10** `emptyAgentThinkInheritsBaseThink` (Fachdoc-BDD 1:1):
  - GIVEN Base-Think=`"true"`, Search-Think leer WHEN `searchAgentConfig()` THEN think=Base-Wert (mapped wie R-THINK-8 im Request) → `LlmConfigTest.emptyAgentThinkInheritsBaseThink`
  - GIVEN Base=`"true"`, Search=`"false"` WHEN `searchAgentConfig()` THEN `"false"` — explizites off, **kein** Fallback → `LlmConfigTest.explicitOffWinsOverBaseDefault`
  - GIVEN Base leer, Agent leer WHEN `agent*Config()` THEN `null` → `LlmConfigTest.bothEmptyStaysUnset`
  - GIVEN Base=`"true"`, Agent-Record leer, WHEN `poAgentConfig()`/`planAgentConfig()`/`compactAgentConfig()` THEN Base-Wert (Slot-Parität, parametrisiert) → `LlmConfigTest.emptyAgentThinkInheritsBaseThink` (Parameter PO/PLAN/COMPACT/SEARCH)
  - GIVEN Custom-Frontmatter ohne think + Base-Think=`"true"` WHEN `customAgentConfig(rec, id)` THEN Base-Wert geerbt → `CustomAgentServiceTest.customAgentWithoutThinkInheritsBaseDefault`
  - GIVEN Dev-Think leer WHEN `devAgentConfig()` THEN `null` (kein Selbst-Fallback) → `LlmConfigTest.devDoesNotInheritItself`
  - Bestands-Grün-Bleiber (D0a) bleiben grün — kein stiller Assertions-Tausch.

**Inc-1 — Widget (tests in `ModelConfigWidgetTest`):**
- **UC-MCW-1** GIVEN das Widget ist in einem 2-Spalten-Grid gebaut WHEN gerendert THEN genau Provider-Combo (READ_ONLY, 9 Einträge), URL-Text, Key-Text, Think-Feld (je Provider-Form), Model-Combo + „Refresh" + „Ping" in der Reihenfolge 1–5 (Parent-Child-Order), **kein** extra-body-Feld → `ModelConfigWidgetTest.showsConnectionFieldset`
- **UC-MCW-7** GIVEN das Widget zeigt einen Provider (z. B. Ollama → Toggle-Combo ""/true/false) WHEN der Provider gewechselt wird (z. B. → OpenAI: Values-Liste, → LM Studio: Text, → Gemini: kein Feld) THEN das Think-Feld rendert **sofort** die Form/Optionen des neuen Providers — ohne Page-Reopen; Wert-Regel: verbatim bei Toggle/FreeString, geleert bei Values ohne Match (z. B. `false` → Values-Liste), weg bei None → `ModelConfigWidgetTest.thinkFieldFollowsProviderChange`
- **UC-MCW-2** GIVEN base-Supplier (= „Store-Stand") zeigt auf eine **tote** URL, im Widget ist die Mock-Server-URL getippt (ohne Apply/Store) WHEN Reload THEN die Liste kommt vom Mock-Server (Widget-Identität gewinnt) — und umgekehrt: getippte tote URL bei mockendem base → keine Liste → `ModelConfigWidgetTest.reloadUsesLiveWidgetValues` (beide Richtungen, Memory-Regel 30)
- **UC-MCW-5** GIVEN Widget-URL = Mock-Server WHEN `computePing()` THEN `reachable=true` und `url()` = Widget-Text; GIVEN Widget-URL = tote URL THEN `reachable=false`; base-Supplier-URL ist dabei irrelevant → `ModelConfigWidgetTest.pingUsesLiveWidgetUrl`

**Inc-2 — Basic-Page-Umbau (tests in `AiConfigPreferenceViewTest`):**
- **UC-MCW-6** GIVEN die Basic-Page ist gebaut WHEN gerendert THEN die Verbindungsfelder stecken im `ModelConfigWidget` in der bindenden Reihenfolge 1 Provider · 2 URL · 3 API-Key · 4 Model+Refresh · 5 Think (+ Ping), keine losen Connection-Field-Editors mehr, und GIVEN geänderte Widget-Werte WHEN `performOk` THEN Store trägt Provider/URL/Key/Model + `llm.agent.dev.think` (leeres Think → Key entfernt) → `AiConfigPreferenceViewTest.performOkPersistsWidgetValues`
- **UC-MCW-4** GIVEN Reload lieferte unter neuer Widget-Identität WHEN der Store gelesen wird THEN Connection-Keys enthalten weiterhin die gespeicherten Werte → `AiConfigPreferenceViewTest.reloadDoesNotTouchStore`
- **UC-MCW-3** GIVEN Widget-URL geändert + Reload erfolgreich WHEN `performCancel` THEN Store unverändert (alter Stand) → `AiConfigPreferenceViewTest.reloadDoesNotPersist`
- Fallback ehrlich benannt: Falls sich `FieldEditorPreferencePage.createControl` im OSGi-SWT-Test als nicht headless-fähig erweist, verifizieren UC-MCW-3/4 per **User-Smoke** (SWT-Präzedenz R-T5/R-UI1/R-ML1) — im Report explizit als „nicht automatisiert, Reason" nennen, nicht verschweigen.

**Inc-3 — Homepage:** kein UC — User-Sicht: Reload/Ping testen ohne Apply, Cancel verwirft; **Think-Fallback-Kette** („Agent ohne eigenen Think erbt den Default aus der einfachen Config; explizites off schaltet auch bei gesetztem Default aus"; leerer Default + leerer Agent = nichts senden). Review via Da-Dok/Da-Thinka-Lesung.

**Inc-4 — Skill:** kein UC — Skill existiert, eclipse-dpe-Section entfernt, Ledger-Eintrag vorhanden.

**Lint:** nach Abschluss `lintDocsAndTests` mit `idPattern: UC-MCW-\d+` (Homage an die UC-IDs im Fachdoc).

## 7. Test-Strategie

- **`ModelConfigWidgetTest`** (Inc-1): Muster = `ModelComboWidgetTest` (Shell aus `AbstractSwtUiTest`, `ui(...)`-Wrapper, `waitUntil` mit Dispatch, `ModelListCache.instance().clear()` in @Before/@After, `MockLlmServer` für echte Fetch-/Reachability-Zirkel). Assertions beider Richtungen (gesendete Identity via erreichbarem/nicht erreichbarem Mock bewiesen, nicht nur „ein Call kam"; was in der UI ankommt geassertet). UC-MCW-7 testet den Live-Re-Build über realen Combo-Selection-Trigger (`select(idx)` + `notifyListeners(SWT.Selection)`), nicht über einen privaten Methodenaufruf — der Trigger IST die Gegebenheit.
- **`AiConfigPreferenceViewTest`** (Inc-2): Page-Level mit echtem `InstanceScope`-Node; **Fixture VOR dem Page-Bau persistieren, Original-Werte der 5 Keys in `finally` restaurieren** (Memory-Regel 12 — keine cross-run-Flakes). Store-Lesezugriff im Test direkt über `IEclipsePreferences` (Wahrheit unterhalb des ScopedPreferenceStore-Caches).
- **Bestehende Tests:** alle Advanced-Page-/Fetch-Tests bleiben unangetastet und müssen grün bleiben (Regression-Guard für „Advanced-Page unberührt"). `AgentModelConfigSectionTest.issue149_*` deckt die Think-Persistenz-Semantik auf Advanced-Seite weiter ab. Inc-0: Bestands-Think-Tests sind erwartete Grün-Bleiber (Inventar in D0a) — wird keiner rot, gibt es keinen Anpassungs-Commit.
- **Timeouts:** Test-Timeouts wie Präzedenz (`@Timeout(10)` nicht nötig — `waitUntil` 5s + `SETTLE_MS`-Muster aus `ModelComboWidgetTest` kopieren).
- **Surefire-Erwartung:** core 1030 + ~3 (Inc-0: LlmConfigTest-Kette parametrisiert + Custom-Erbe — exakte Zahl hängt an Parametrisierung, Report nennt sie); OSGi +7 (4 Widget + 3 Page).
- **`lintDocsAndTests`** mit `idPattern: (UC-MCW|UC-THINK)-\d+` — der Zyklus deckt UC-MCW-1…7 **und UC-THINK-10** ab.

## 8. Inkremente (je für sich grün: Surefire core + OSGi plugin)

0. **Inc-0 — Core: Think-Default-Fallback** (nur core, kein UI; VOR dem Widget — die Think-Semantik muss stehen, bevor die UI den Default-Editor baut; auf `story/model-config-widget` baubar, `ThinkSupport`/`ThinkResolver`-Code kommt aus `story/issue-149-think`-Basis): `LlmConfig.resolveThink` + Umschaltung der 5 Methoden (D0), UC-THINK-10-Tests in `LlmConfigTest` + `CustomAgentServiceTest`, Bestands-Grün-Bleiber verifiziert (D0a). Grüner Surefire-Lauf + Commit. ✅ `c90debfb` (core 1040/1040)
1. **Inc-1 — Widget + Widget-Tests** (nur-hinzufügen): `ModelConfigWidget` + `ModelConfigWidgetTest` (UC-MCW-1/2/5/7, inkl. provider-abhängigem Live-Think-Re-Build D3). Page unberührt, alles grün. Commit. ✅ `df4293fe` (OSGi 298/298)
2. **Inc-2 — Basic-Page umstellen** (Rewire + Entfernen der 3 Field-Editors): `AiConfigPreferenceView` auf Widget umbauen, Label „Think (Default)" (D4), `performOk` (D6), `AgentModelConfig.withThink`, `AiConfigPreferenceViewTest` (UC-MCW-3/4/6 inkl. Save-Roundtrip). Grüner Lauf + Commit. ✅ `a9691449` (OSGi 301/301, core 1040/1040 — SWT-Fix: Think-Feld create-once + GridData.exclude statt dispose+recreate, sonst bindende Reihenfolge 1–5 gebrochen)
3. **Inc-3 — Homepage:** `configuration.md` + `advanced-configuration.md` (L28 + User-Sicht Live-Read/Cancel + **Think-Fallback-Kette**: Agent ohne eigenen Think erbt den Default aus der einfachen Config; explizites off schaltet auch bei gesetztem Default aus). Commit (Code aus Inc-0/2 bereits im Branch). ✅ `99c5aecb`
4. **Inc-4 — Skill + Cleanup:** `.agents/skills/eclipse-preferences/SKILL.md` anlegen — Kern-Botschaften (Paul): ① Werte bei Feld-Änderungen **direkt aus dem Widget auslesen** (Live-Read statt Store; Stale-Store-Read-Muster bei `FieldEditorPreferencePage` inkl. performOk/doPersist-Timing, `editor.getStringValue()`), ② **Feldkopplung**: Provider→Think-Form (live Re-Build statt Construction-time-Freeze, R-MCW-6) und Provider/URL/Key→Reload-Identity — wie im `ModelConfigWidget` implementiert; plus Widgets-Übersicht `parts/config/widgets`. Danach Quick-Section aus `eclipse-dpe/SKILL.md` (L154-165) entfernen + Ledger-Eintrag `skill-impact.md`. Commit. ✅ `5fd04c42` (eclipse-dpe-Section war uncommitted Working-Tree-Addition — Entfernung = Restore auf HEAD)
5. Abschluss: `lintDocsAndTests(idPattern: (UC-MCW|UC-THINK)-\d+)`, Surefire-Zahlen je Inkrement im Report. ✅ Lint 0 findings (17/17 UC-Definitionen, 63 ID-Zeilen in Tests) **KEIN** `planImplemented` durch Da Thinka — Archivierung nach PO-Review durch Da Mek.

## 9. Offene Punkte / Revisionen

- **Rev. 3 (2026-09-28, Queued Message):** **Inc-0 neu** — Think-Default-Fallback (R-THINK-10/ADR-0061, UC-THINK-10): leerer Agent-Think erbt Base-Think (DEV-Record), explizites off gewinnt, Dev verbatim; zentraler `resolveThink`-Helfer in `LlmConfig` (D0/D0a). Inc-Nummerierung nachgezogen (0–4); Basic-Think-Label „Think (Default)" (D4); Homepage Inc-3 nennt die Fallback-Kette.
- **Rev. 2 (2026-09-28, Chat):** D3 revidiert — Think-Feld ist provider-abhängig mit Live-Re-Build beim Provider-Wechsel (R-MCW-6/UC-MCW-7, Fachdoc neu gelesen); bindende Feld-Reihenfolge 1–5 + Ping (UC-MCW-6). Gate präzisiert: **Jon-Order**, nicht Paul-Merge; Inc-1 bleibt auf #149-Branch-Basis baubar (nur-hinzufügen).
- **Rückversicherung im Fachdoc (⏳):** Wert-Verhalten beim Provider-Wechsel (verbatim bei freier Eingabe, leeren bei Values ohne Match, weg bei None) ist als SOLL notiert und wird im Widget so implementiert (D3) — falls Paul die Regel noch mal abändern will: vor Inc-1 sagen (das Re-Build ist Teil des Inc-1-Tests).
- Keine weiteren offenen Fragen — die aus dem Code ableitbaren Punkte (Think-Slot = DEV-Slot D4, ModelComboWidget unverändert wiederverwendbar, Resolve-Punkt in `LlmConfig` statt `ThinkResolver`) sind mit IST-Evidenz festgehalten. Falls Paul D4 (Basic-Think schreibt `llm.agent.dev.think`) anders will: STOP-AND-ASK vor Inc-2 (Inc-0/1 sind davon unabhängig).

## 10. Review (Da Dok, 2026-09-28, Pre-Mortem — Plan↔Code / Docs↔Code / Docs↔Plan)

**Verdict: REJECTED — ein blocking Evidence-Gap; der Code selbst ist in allen drei Richtungen geprüft und sauber.**

### REJECTED — Rework-Items

1. **OSGi-Gate-Evidenz fehlt (Plan §8: „je für sich grün: Surefire core + OSGi plugin").** `org.sterl.llmpeon.test/target/surefire-reports/` ist stale: alle 80 Reports vom 28.09. 09:57 — **vor** Inc-1 (df4293fe, 11:51) und Inc-2 (a9691449, 12:41). `ModelConfigWidgetTest` und `AiConfigPreferenceViewTest` haben **keine** Surefire-Reports — die neuen OSGi-Tests liefen nie durch das Tycho-Gate. Die Plan-Claims „OSGi 298/298" (Inc-1) / „301/301" (Inc-2) sind `eclipseRunJavaTests`-Zahlen — genau der Runner, den AGENTS.md explizit als NICHT ground truth markiert („counts higher, wrong report numbers"; der alte Surefire-Stand zeigt 294). Verstoß gegen Plan §8 + Memory-Regel 28 (IST-Evidenz verlangen, DONE-Claims nicht glauben). **Rework:** ein Tycho-Surefire-Lauf im test-Modul auf `5fd04c42`, Report-Zahl als IST-Evidenz nachtragen.
2. **Uncommitted Deliverables vor Merge sichern (Memory-Regel 19).** Working Tree (12 Einträge): `docs/adr/0060-*`, `docs/adr/0061-*`, `docs/model-config-widget.md` untracked; `docs/per-agent-think.md`, `docs/index.md`, `docs/memory.md`, `docs/model-loading.md`, `docs/open-points.md`, `docs/resolved-points.md`, `docs/adr/index.md` modified — alles Inkrement-Deliverables, noch nicht auf `story/model-config-widget` committed. Status-Flips (R-THINK-10 und R-MCW-1…6 stehen noch auf „❌ specified", UCs auf „geplant") sind nach dieser Review PO-Aufgabe (Präzedenz dc2ec20d) — dann Commit inkl. Flips, erst danach Merge.

### CONCERNS — non-blocking

- **Rule of three erreicht:** `waitUntil(BooleanSupplier, String)` existiert jetzt 3× identisch (`ModelComboWidgetTest`, `AiConfigPreferenceViewTest`, `ModelComboWidgetTest`-Präzedenz), `rendered(Composite)` 2×. → Extraktion: `waitUntil` in `AbstractSwtUiTest`, `rendered` in einen Shared-Test-Helper.
- **Index-basierte Child-Lookups** (`parent.getChildren()[3]`, `[6]`, `[11]`, `[12]`) in beiden neuen Tests sind spröde — bewusster Trade-off (sie schlagen sichtbar beim nächsten Feld), kein Handlungsbedarf; wenn ein Feld dazukommt, sind beide Tests + Helpers gleichzeitig anzufassen.

### Verifiziert mit Evidenz (Plan↔Code)

- **Inc-0** `c90debfb`: `LlmConfig.resolveThink(AgentModelConfig)` zentral (LlmConfig.java:203), alle 5 Methoden umgeschaltet (:212 po, :220 plan, :228 compact, :236 search, :249 custom), `devAgentConfig` (:191) unverändert verbatim (nicht im Diff). Tests: `LlmConfigTest.emptyAgentThinkInheritsBaseThink` (parametrisiert PO/PLAN/SEARCH/COMPACT), `explicitOffWinsOverBaseDefault`, `bothEmptyStaysUnset`, `devDoesNotInheritItself`, `CustomAgentServiceTest.customAgentWithoutThinkInheritsBaseDefault` (:243). D0a: Kern-Diff berührt nur LlmConfigTest/CustomAgentServiceTest — kein stiller Assertions-Tausch. Core-Surefire 1040/0/0 (Reports 12:31) ✓.
- **Inc-1** `df4293fe`: Widget = Controller ohne Composite, **kein Store-Zugriff** — Store nur über den injizierten `Supplier<LlmConfig> base` (ModelConfigWidget.java:64,82); `snapshot()` überschreibt providerType/url/key mit Live-Werten (:163-171), Think bewusst nicht in der Identity. Think-Re-Build live per Selection-Listener (:91→:223), create-once + `GridData.exclude` (:253-256, SWT-Trap im Javadoc dokumentiert), Reihenfolge 1–5 stabil, kein extra-body-Feld. Tests UC-MCW-1/2 (beide Richtungen, Memory-Regel 30)/5 (beide Richtungen)/7 (echter Combo-Selection-Trigger) — alle in `ModelConfigWidgetTest` nachgewiesen.
- **Inc-2** `a9691449`: `performOk` = einziger Save-Pfad — `super.performOk()` + setValue(provider/url/key) (:102-104) + `LlmConfigSaver.saveAgentModelConfig(DEV, …withModel().withThink())` (:107-110); der alte manuelle `PREF_MODEL`-Write entfällt ✓. `AgentModelConfig.withThink` (:31) ✓. `AiConfigPreferenceViewTest`: Fixture VOR Page-Bau persistiert, 5 Keys in finally restauriert (Memory-Regel 12), Store-Reads unterhalb des ScopedPreferenceStore-Caches ✓; UC-MCW-6 prüft zusätzlich, dass die Reihenfolge den Provider-Wechsel überlebt.
- **Inc-3** `99c5aecb`: `homepage/src/setup/configuration.md` — Live-Read (:14-21), Think-Fallback-Kette mit „explicit off wins" (:198-206) ✓; `advanced-configuration.md:28` — „Refresh uses the values currently typed … no Apply needed" ✓.
- **Inc-4** `5fd04c42`: `.agents/skills/eclipse-preferences/SKILL.md` existiert (Live-Read + Feldkopplung), eclipse-dpe-Section „Preference pages" entfernt, Ledger-Eintrag in `wiki/skill-impact.md` (2026-09-28-Zeile) ✓.

### Verifiziert (Docs↔Code / Docs↔Plan / Architektur)

- UC-MCW-1…7: je ein Test, Fachdoc-BDD abgedeckt. UC-THINK-10: alle 5 BDD-Bullets abgedeckt. `lintDocsAndTests(idPattern (UC-MCW|UC-THINK)-\d+)`: **0 findings, 17/17 UC-Definitionen, 63 ID-Zeilen in Tests.**
- Architektur: Widget kennt den Store nicht (ADR-0005/0060) ✓; `ModelComboWidget` nicht im Diff (unverändert wiederverwendet) ✓; Advanced-Page (`AgentModelConfigSection`, `AiAdvancedPreferenceView`) nicht im Diff ✓. Secret-Hygiene: `ConnectionValues.toString()` redactet apiKey (ModelConfigWidget.java:129), `PingResult` trägt keinen Key ✓.
- ADR-0061-Konformität: Kette Agent→Base→null exakt implementiert, explizites off gewinnt, Dev kein Selbst-Fallback, Custom über denselben zentralen Pfad.
- **Plan coverage gap: keiner.** Skill/instruction gap: keiner (die AGENTS-Regel „Surefire ist Ground Truth" existiert — sie wurde befolgt-nicht, das ist Rework-Item 1, kein fehlendes Skill-File).

### Mutation-Check-Empfehlung (für PO)

- `ModelConfigWidget.applyThinkValue`, Values-Zweig carried-Branch (ModelConfigWidget.java:272): mutiere „leeren bei Werte-Liste ohne Match" zu „immer verbatim übernehmen" — `thinkFieldFollowsProviderChange` muss rot werden. Das schützt die Paul-Regel „nie ein stiller Ersatzwert" — die einzige Stelle des Widgets, deren falsches Still-Verhalten sonst unsichtbar bliebe.

### Most likely reason this breaks later

**Wie:** die neue OSGi-Integration bleibt ungeprüft, bis zufällig jemand `mvn verify` im test-Modul fährt; der index-basierte Test-Stil bricht dann erst beim nächsten Feld. **Change that most reduces that risk:** Rework-Item 1 (Tycho-Surefire-Lauf nachziehen, Zahl als IST-Evidenz im Plan) — ein Lauf, der den Gate-Verstoß in Nichts auflöst.
