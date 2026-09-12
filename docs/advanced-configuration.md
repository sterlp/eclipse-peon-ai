# Advanced Configuration

## Preference Page Split

AI Peon configuration is split into two preference pages:

| Page | Purpose |
|------|--------|
| **AI Peon Configuration** | Basic provider, model, URL settings for everyday use |
| **AI Peon Advanced** | Per-agent models, temperatures, debug mode, query/header parameters |

This separation keeps the default configuration simple while providing power users access to fine-grained controls.

## Per-Agent Model Resolution via ChatRequest

### Model Resolution (SOLL)

Jeder Agent resolviert **seinen eigenen** Modell-/Verbindungs-Slot aus `LlmConfig` (Slots und
Fallbacks: siehe „Agent-Specific Config Umbau" unten) und setzt den resolvierten Modellnamen auf
`ChatRequest.modelName()`, bevor der Tool-Loop läuft — langchain4j wendet den Override beim
Request-Bau an.

- **Dev-Agent**: nutzt die Base-Connection; `llm.agent.dev.*` bleibt optional für Overrides
- **Alle anderen Slots** (plan/search/compact/po + Custom Agents): eigene Werte, wenn gesetzt;
  leere Felder erben die **Base-Connection** (URL, Key, Modell)
- **Kein Slot fällt auf einen anderen Agent-Slot zurück** (R-PO2: PO-Fallback = Base, nie Plan)
- **Warum `ChatRequest.modelName()` statt separates Modell-Objekt:** ein `StreamingChatModel`
  pro Verbindung (KV-Cache bleibt über Agenten-Wechsel erhalten), keine Synchronisierung
  mehrerer Instanzen, nativer per-Request-Override, geringerer Overhead

## Agent-Specific Config Umbau (SOLL, 2026-08-21) — ✅ komplett gebaut: Core (2a) + Config-UI (2b) + Cache-Clean-Break/Beispiele/Usage/Custom-Agent-Frontmatter/Homepage (2c, 2026-09-01)

**SOLL:** Alles konfigurierbar wird **agent-spezifisch** — jeder Agent mit Modell-Slot
(base/plan/search/compact/PO + Custom Agents) trägt seine eigenen Einstellungen: Modell
(besteht, Issue #82), Temperature, Think, **JSON extra body** (→ [Prompt Caching](caching.md) —
Caching läuft als per-agent-JSON, nicht als Provider-Hardcode).

Dazu gehören:
- Advanced Settings-Eingabe **pro Agent** (inkl. Custom Agents) mit UI-Beispielen unter dem
  Input (GPT-/Claude-Cache-Snippets).
- Geltung für jeden Agenten, bei dem ein Modell eingestellt werden kann.
- Zusammen mit dem Caching-SOLL dokumentiert ([caching.md](caching.md)) — **✅ umgesetzt (2a/2b/2c)**.

**SOLL-Ergänzung (2026-08-28, User) — ✅ komplett gebaut (2b 2026-08-30: Config-Seite per-agent, Modell-Liste pro Identität, Chat-UI-Räumung; 2c 2026-09-01: Custom-Agent-Frontmatter, Homepage-Doku, Caching-Beispiele + Clean-Break).
Mechanik: [ADR-0034](adr/0034-connection-cache-by-identity.md).**
- **Model-Config pro Agent:** ein „configured model" = URL, Key, Modell, Think, extra JSON body
  (KV-Cache-ID & weitere Body-Params). Verbindungen (Model-Instanzen) werden per **Hash der
  Verbindungs-Identität** (Provider + URL + Key [+ body, wenn nur Build-time setzbar]) gecacht;
  gleiche Identität → wiederverwendete Verbindung (Default-Verbindung, solange URL/Key
  gleich/nicht gesetzt). Temp/Think/Body laufen per Request.
- **JSON-Widget in der Config-Seite:** pro Agent ein Widget, das die Config editiert;
  Speicherung mit **Agent-Prefix** (Key-Prefix je Agent) — keine zentrale Modell-Registry.
- **Modell-Dropdown + Think: aus der Chat-UI raus** (2026-08-28, User: „nur wenn es einfacher
  wird" — einfacher: Config-Seite = einzige Quelle, kein Dual-Edit, weniger Chat-UI-State).
  Die Config-Seite trägt pro Agent: Modell-Dropdown, Think, JSON-Body-Widget.
- **Modell-Liste pro Agent (2026-08-28, User):** das Dropdown eines Agenten bezieht seine
  Liste aus dessen **effektiver** Verbindung (eigene Provider+URL+Key, sonst Base-Config) —
  Umschalten auf einen Agenten mit anderer URL zieht also eine andere Liste. Liste **einmalig**
  pro Identität (gleicher Hash wie Connection-Cache): **Cache on success**; Fetch-Fehler →
  konfiguriertes Modell bleibt gesetzt; **kein Refetch** beim
  Zurückwechseln. **Refresh-Button** (2026-08-28, User): manueller Refetch der
  Liste der aktuellen Identität (Fehler → alter Cache bleibt); Placement seit R-ML3
  **unter dem Combo**. Identitätswechsel
  (URL/Key/Provider) → neuer Fetch. Konfiguriertes Modell
  nicht in der Liste → **bleibt gesetzt** (bewusst kein Auto-Switch auf ein Listen-Modell).
- **Core-Fundament ✅ (Zyklus 2a, 2026-08-28):** `AgentConfig`/`LlmConfig` +`extraBody`;
  `EffectiveConnection` (Agent-URL/Key, sonst Base; Provider bleibt Base-Ebene);
  Connection-Cache in `ConfiguredChatModel` pro `ConnectionIdentity` (Provider+URL+Key, +Body
  nur Build-time-Provider); per-request Body-Merge (provider-Entries zuerst, **User-Body
  gewinnt**, Reserved-Keys `model`/`messages`/`tools` gestrichen); invalides JSON → warn +
  ignoriert. Core 483/483 grün, Branch `new-config` (Commits `inc-1`…`inc-8`, NICHT gemerged).
- **Core/UI-Trennung (2026-08-28, User):** die Konfig-Domäne (Model-Config-Record, Speicherung,
  effektive Auflösung, Verbindungs-Identität) lebt in **core**; die Eclipse-Config-Seite ist
  reine View/Editor darüber — „sauber trennen, nah halten“ (das Plugin trägt keine
  Config-Logik).
- **Sauberer Rebuild bei Update (2026-08-28, User):** die effektive Config wird bei jedem Load
  **komplett** aus gespeicherten Werten + Defaults neu aufgebaut; unbekannte/entfernte Keys
  werden ignoriert (keine Migrations-Kette, kein Stale-State).
- **Custom Agents: komplette Config via AGENT.md-Frontmatter (yml) (2026-08-28, User) — ✅ gebaut (2c 2026-09-01):** alles
  Einstellbare (URL, API Key, Modell, Think, JSON extra body) geht auch pro Custom Agent im
  Frontmatter — gleicher Record, gleiche Auflösung; Keys `url`/`api_key`/`extra_body`,
  `PromptYmlParser` im core, Auflösung wie die 4 Core-Agents (0 Plugin-Änderungen);
  **Homepage-Doku** ✅ (2c, `custom-agents.md`).
- **URL-Wechsel in der Base-Config ✅ (2b, 2026-08-30):** GIVEN die Base-URL/-Key ändert sich,
  WHEN die Modell-Auswahl eines beliebigen Agenten geladen/angezeigt wird, THEN folgt sie der
  neuen effektiven Verbindung (kompletter Config-Rebuild bei jedem Load).
- **Modell-Listen-Fetch UI-Thread-safe ✅ (2b, inc-17 `60b494e`):**
  GIVEN Config-Seite geöffnet, Modell-Dropdown eines Agents lädt die Liste
  WHEN der Fetch-Job die aktuelle Config liest
  THEN alle SWT-Zugriffe laufen auf dem UI-Thread — der Job arbeitet ausschließlich mit dem
  vor dem Start gecapturten, SWT-freien `FetchSnapshot(identity, buildConfig)`
  (`fetchList(FetchSnapshot)` ist static und widget-frei)
  → `AgentModelConfigFetchTest.fetchListUsesCapturedSnapshotWithoutWidgets`

## PO-Agent bekommt einen eigenen Model-Slot (SOLL, 2026-09-03 — ✅ 3a: R-PO1–R-PO4)

**SOLL:** Fünfter Slot `po` — vollwertig wie plan/search/compact (URL, API-Key, Modell +
Refresh, Think, JSON extra body, gegated über `supportsExtraBody()`).

**WEIL:** PO und Plan sind unterschiedliche Rollen mit unterschiedlichem Modellprofil
(User-Setup: PO = Claude, Plan = GPT-5). Der geteilte Slot macht diese Kombination
technisch unmöglich. → [ADR-0023](adr/0023-po-model-plan-slot.md) wird **superseded**.

### Regeln

- **R-PO1 ✅ done (3a, 2026-09-03) — eigener Slot.**
  Der PO-Agent besitzt einen eigenen `AgentModelConfig.PO = "po"`-Slot mit allen Feldern
  (`model`/`url`/`apiKey`/`think`/`extraBody`), gespeichert unter `llm.agent.po.<field>`.
  - GIVEN Advanced-Config geöffnet, WHEN die Seite rendert, THEN erscheinen fünf Sections in
    der Reihenfolge PO, Plan, Dev, Search, Compact → `AdvancedPreferenceSectionsTest.showsPoSection`
    — **Einschränkung (Review 3a):** der Test prüft die **Deklaration** `AGENT_SECTIONS`, nicht
    das Rendern. Dreht jemand `createFieldEditors()` auf Einzelaufrufe zurück, bleibt er grün.
    Ein echter Render-Test bräuchte ein Display und würde skippen — bewusst nicht gebaut, das
    Rendern nimmt der **Smoke-Test** ab. Titel (IST): `PO agent (Jon)`,
    `Dev agent (uses base model)`, `Plan agent`, `Search agent`, `Compact agent`.
  - GIVEN PO-Modell `claude-x` gesetzt und Plan-Modell `gpt-5`, WHEN beide Agenten aufgerufen
    werden, THEN nutzt jeder sein eigenes Modell → `AgentModelResolutionTest.poIndependentOfPlan`

- **R-PO2 ✅ done (3a, 2026-09-03) — Fallback = Base-Config, nicht Plan.**
  Ist der PO-Slot leer, gilt die Base-Config (wie beim Dev-Agenten) — **kein** Rückfall auf
  den Plan-Slot.
  - GIVEN leerer PO-Slot, WHEN der PO-Agent ruft, THEN werden Base-URL/-Key/-Modell benutzt
    → `AgentModelResolutionTest.poFallsBackToBase`
  - GIVEN leerer PO-Slot und gesetztes Plan-Modell, WHEN der PO-Agent ruft, THEN wird das
    Plan-Modell **nicht** verwendet → `AgentModelResolutionTest.poIgnoresPlanSlot`

- **R-PO3 ✅ done (3a, 2026-09-03) — Clean Break, keine Migration.**
  Bestehende Installationen bekommen einen leeren PO-Slot (= Base). Der bisher implizite
  Plan-Slot-Bezug wird nicht übernommen (konsistent zum Rebuild-Prinzip „keine
  Migrations-Kette").
  - GIVEN gespeicherte Config ohne `llm.agent.po.*`, WHEN geladen wird, THEN ist der PO-Slot
    leer und die Auflösung fällt auf Base → `LlmConfigLoaderTest.missingPoKeysYieldEmptySlot`

**Umsetzung 3a (IST):** `AgentModelConfig.PO` + `CORE_IDS` als **einzige** Slot-Aufzählung
(vorher doppelt in `LlmConfigLoader` und UI); `LlmConfig.poAgentConfig()` (Modell-Fallback auf
Base wie beim Dev-Slot); `LlmConfigLoader` iteriert `CORE_IDS`; `AiPoAgent` liest/schreibt nur
noch den PO-Slot (6 harte PLAN-Bezüge entfernt, Grep-verifiziert); UI-Sections als SWT-freier
Deskriptor `AGENT_SECTIONS`. `LlmConfigSaver` und `LlmPreferenceInitializer` blieben unberührt
(bereits generisch). Der gpt-5-Default-Cache-Key wird automatisch `peon-ai-po`.

**Nachgezogen in 3b (2026-09-03):** die Base-Level-Temperature ist ersatzlos entfallen; Jon hat
seither wie jeder andere Agent ein eigenes `llm.agent.po.temperature`. Die Marker in `AiPoAgent`
und `LlmConfig` sind mitsamt den toten `getTemperature()`-Overrides gelöscht.

## Temperature pro Agent (SOLL, 2026-09-03 — ✅ 3b: R-T1–R-T4)

**SOLL:** Optionales Temperature-**Eingabefeld** pro Agent (alle fünf Core-Slots + Custom
Agents via Frontmatter `temperature`).

**WEIL:** Temperature ist der eine Parameter, den praktisch jedes Modell versteht — anders als
der extra body, den nicht jeder Provider unterstützt (`supportsExtraBody()`-Gate). Ein eigenes
Feld macht ihn ohne JSON-Bastelei erreichbar.

**Kein Slider** (User + PO, 2026-09-03): ein Slider hat immer einen Wert, wir würden
`temperature` also immer senden. GPT-5/o-Modelle lehnen jedes `temperature != 1` mit einem
API-Fehler ab — das würde exakt die Modelle brechen, die im Plan-/PO-Slot laufen.

- **R-T1 ✅ done (3b, 2026-09-03) — leeres Feld = nicht senden.**
  Temperature ist ein optionaler Text-/Zahlen-Input. Leer oder `null` → der Parameter wird
  **gar nicht** in den Request geschrieben (kein Default, keine 1.0).
  - GIVEN leeres Temperature-Feld, WHEN ein Request gebaut wird, THEN enthält er keinen
    `temperature`-Parameter → `AgentTemperatureTest.emptyMeansUnset`
  - GIVEN `0.2`, WHEN ein Request gebaut wird, THEN enthält er `temperature=0.2`
    → `AgentTemperatureTest.setsConfiguredValue`
- **R-T2 ✅ done (3b, 2026-09-03) — invalide Eingabe → warn + ignorieren.**
  Nicht-numerische Eingabe wird wie invalides extra-body-JSON behandelt: Warnung ins Log, Wert
  ignoriert, Request ohne Temperature. Kein Absturz, keine Exception zum User.
  - GIVEN Eingabe `abc`, WHEN geladen wird, THEN wird gewarnt und der Slot bleibt unset
    → `AgentTemperatureTest.invalidValueWarnsAndIgnores` (Fälle `abc`, `1,5`, `" "`, `null`)
  - GIVEN ein Custom Agent mit `temperature: abc` im Frontmatter, WHEN `getConfig()` gerufen
    wird, THEN kommt `null` heraus statt `NumberFormatException`
    → `CustomAgentServiceTest.frontmatterInvalidTemperature_isIgnored`
  - **`0` und `0.0` sind gültige Werte**, kein unset — nur leer/blank/unparsebar ist unset.
- **R-T3 ✅ done (3b, 2026-09-03) — extra body gewinnt.**
  Setzt der User `temperature` **auch** im extra body, gewinnt der Body (konsistent zur
  bestehenden Merge-Regel „User-Body gewinnt"), und der Feld-Wert wird verworfen.
  - GIVEN Feld `0.2` und Body `{"temperature": 0.9}`, WHEN gemerged wird, THEN geht `0.9`
    raus → `AgentTemperatureTest.bodyWinsOverField`
  - GIVEN dieselbe Kombination, WHEN ein echter Request rausgeht, THEN steht `temperature`
    **genau einmal** mit `0.9` im JSON-Body (kein Doppelkey)
    → `AgentTemperatureTest.bodyTemperatureWinsOnTheWire`
  - Das Streichen greift **nur** bei `supportsExtraBody()`; bei Ollama (`ExtraBodyMode.NONE`)
    bleibt das typisierte Feld erhalten, sonst fiele beides weg
    → [ADR-0039](adr/0039-temperature-body-precedence.md)
- **R-T4 ✅ done (3b, 2026-09-03) — Clean Break der Alt-Keys.**
  `llm.planTemperature` und `llm.devTemperature` werden **gelöscht** (Konstanten, Aliase,
  Loader/Saver). Neue Keys: `llm.agent.<id>.temperature`. Keine Migration.
  - GIVEN gespeicherte Alt-Keys, WHEN die Config geladen wird, THEN werden sie ignoriert und
    der Temperature-Slot ist leer → `LlmConfigLoaderTest.legacyTemperatureKeysIgnored`
  - GIVEN das Plugin initialisiert seine Defaults, WHEN der DefaultScope gelesen wird, THEN
    existieren die Alt-Keys nicht mehr
    → `AdvancedPreferenceSectionsTest.legacyTemperaturePreferencesRemoved`
  - **Mitentfernt (Leichen-Beseitigung):** die toten `getTemperature()`-Overrides in `AiAgent`,
    `AbstractAgent`, `AiPlanAgent`, `AiDevAgent`, `AiScaffoldAgent`, `AiPoAgent`, `CustomAgent`
    (nur `CustomAgent` konsumierte den Wert überhaupt), `SimplePromptFile.firstOrDefaultNumber`,
    `LlmConfigLoader.parseDouble` und der `DoubleSliderFieldEditor`.
- **R-T5 ❌ specified — Position im UI.** Das Feld steht in jeder `AgentModelConfigSection`
  zwischen Think und extra body; **kein** Provider-Gate (anders als extra body) — Temperature
  gilt als universell, und der Unset-Default schützt die Modelle, die sie ablehnen.
  **Kein automatisierter Test** (PO-Entscheidung 2026-09-03): ein Render-Test würde ohne
  Display skippen, ein Deklarations-Test wäre exakt der Befund B-1 aus Review 3a. **Abnahme
  per Smoke-Test.**

### Konsequenzen des Clean Break (PO, 2026-09-03)

- **Search und Compact haben keine impliziten Defaults (`0.3` / `0.2` entfallen, 3b).** Ohne
  konfigurierten Wert senden sie **nichts** (R-T1). Wer die alten Werte will, trägt sie einmalig
  ins Feld ein. → Homepage benennt das.
- **`temperature` hat einen einzigen Parse-Pfad für alle Agent-Arten.** Custom Agents legen
  ihren Frontmatter-Wert als **rohen String** in dasselbe `AgentModelConfig`-Record wie die
  fünf Core-Slots; geparst wird an genau einer Stelle (`AgentTemperature`, R-T2: invalide
  Eingabe → warn + ignorieren).
- **R-T3 ist explizit implementiert, nicht der Serialisierung überlassen.** langchain4j merged
  `customParameters` per `@JsonAnyGetter` **neben** das typisierte Feld — ein `temperature` im
  extra body würde sonst einen **doppelten JSON-Key** erzeugen, und wer gewinnt, entscheidet die
  Gegenseite. Deshalb streicht der Merge das typisierte Feld aktiv, wenn der Body den Wert
  trägt.
- **`DoubleSliderFieldEditor` wurde mit dem Umbau gelöscht (Clean Break).** Toter Code, keine
  Tests, und das SOLL schließt einen Slider dauerhaft aus.

- **R-PO4 ✅ done (3a, 2026-09-03) — Custom-Agent-Parität.** Der PO-Slot verhält sich beim
  Frontmatter-/Extra-Body-Merge exakt wie die anderen Core-Agents (User-Body gewinnt,
  Reserved-Keys gestrichen) — keine Sonderlogik. **Nicht-Change:** der Merge-Pfad
  (`ProviderRequestSupport.mergeCustomParameters`, `ExtraBody.parse`) ist agentneutral;
  abgesichert durch den als **Charakterisierungstest** deklarierten
  `AgentModelResolutionTest.poExtraBodyMergesLikeOtherAgents`.

## First-Launch Directory Resolution

On first launch, AI Peon resolves skills and commands directories:

1. Check if `~/.claude/skills` exists → use it (Claude Desktop compatibility)
2. Otherwise create and use `~/.llmpeon/skills`

Same logic applies to commands directory (`~/.claude/commands` → `~/.llmpeon/commands`).

This one-time resolution ensures deterministic behavior without filesystem I/O on every config load.

## Basis-URL vs. per-Agent Override — Klartext (2026-09-12, User-Rückfrage)

Die URL der **Basic-Page** (`llm.url`) ist die Basis für **alle** Agenten ohne eigenen Override —
der Dev-Agent trägt standardmäßig **keinen eigenen** URL (`llm.agent.dev.url` existiert dann nicht)
und erbt die Base-URL. Das URL-Feld der Advanced-View zeigt nur den **eigenen** Override
(„empty = inherit base"), nicht die effektive Verbindung — deshalb steht dort für Dev nichts,
obwohl dev faktisch die Base-URL nutzt (`LlmConfig.java:165`, `EffectiveConnection.java:32`).
Modell-Listen-Refresh nutzt den **gespeicherten** Stand: erst **Apply**, dann Refresh →
R-ML2 in [model-loading.md](model-loading.md).
## Config-Page UI — R-A1/R-A2/R-A3 — ❌ specified (2026-09-12, User-Smoke „Seite sieht altbacken aus")

**R-A1 — Abstand unter den Examples:** GIVEN die Advanced-Page zeigt die 3 Example-Buttons
unter dem Extra-Body-Feld, WHEN gerendert, THEN ist der Abstand **unterhalb** der Gruppe gleich
klein wie oberhalb — keine Leerraum-Zeile, kein Extra-Fußraum (Layout-Gruppe, kein Extra-Space).

**R-A2 — Native Combo statt Custom-Dropdown (beide Pages):** Das Model-Feld nutzt auf **Basic**
und **Advanced** (geteilte Logik) das **native SWT-Combo** wie Provider/Shell-Command — gleicher
Dropdown-Button. Label und Combo erscheinen exakt wie die übrigen Label/Feld-Paare derselben
Page: Label in der Label-Spalte der Page (gleiche Ausrichtung wie die Sibling-Labels), Combo in
der Feld-Spalte. Der Refresh-Button sitzt **unter** dem Combo
(Placement/Style wie „Check Host and Port" beim URL-Feld). **Verhalten unverändert:** lazy fetch
einmal pro Verbindungs-Identität, Refresh holt neu, manuelle Modelleingabe erlaubt, konfiguriertes
Modell bleibt selektiert auch wenn es nicht in der Liste steht, Single-Flight + Secret-Masking
bleiben (ADR-0040) → R-ML3 in [model-loading.md](model-loading.md).

**R-A3 — Design-Studie github-copilot-for-eclipse (separat, NACH R-A1/R-A2):** Gruppen-/Label-Gestaltung
im Copilot-Plugin abschauen (alle Labels bündig, auch Extra Body) — Inspiration, kein Copy; nur
anschauen, wenn die 2 einfachen Fixes drin sind.