# F8-Survey — Config-Umfang Plugin/Core vs. Fabrik (Such-Agent, 2026-11-09, Jon)

Ziel: F8 „Config-Umfang: alles was das Plugin hat" (open-points Runde 3) — Delta-Bestimmung vor den
UCs. Alle Pfade/Zeilen vom Agent verifiziert; Belege hier, Regeln kommen in `docs/config.md`.

## Pfade
- Core: `org.sterl.llmpeon.core` (Eclipse /llmpeon-core) — `LlmConfig.java`, `AgentModelConfig.java`,
  `AgentConfig.java`, `LlmConfigLoader/Saver`, `LlmConfigKeys`.
- Plugin: `org.sterl.llmpeon` (nur Disk, kein Eclipse-Projekt) — Config ist SWT/JFace,
  **kein `chat.html`** im Bestand (Plugin-Config = Preference-Views).
- Fabrik: `factory-ui/src/config/` + `factory-backend/…/config/`.

## 1. Core `LlmConfig`-Felder (LlmConfig.java)
| Zeile | Feld | Bedeutung |
|---|---|---|
| :47 | `providerType : AiProvider` (Default OLLAMA) | Basis-Provider |
| :49 | `model` | Basis-Modell = Dev-Modell (:52-53) |
| :56 | `modelConfigs : Map<String,AgentModelConfig>` | Slots `dev/po/plan/review/search/compact` |
| :63 | `agentTools : Map<String,String>` | rohe CSV-Tool-Allowlists |
| :66 | `timeout : Duration` (3 min) | Request-Timeout |
| :68 | `url` | Basis-URL |
| :70 | `autoCompactAfter = 80000` | Key `llm.tokenWindow` (Loader:35) |
| :77 | `maxTokens = 0` | Max Output-Tokens, 0 = Provider-Default |
| :80 | `sendThinkingEnabled = true` | Thinking zurückschicken (Build-Time) |
| :82 | `apiKey` | Basis-Key |
| :90 | `extraBody` | Extra-JSON-Body |
| :92 | `configDir = ~/.peon` | Fabrik-pinnt es (R-WP-9) |
| :95 | `diskToolsEnabled` | Fabrik-pinnt an (R-WP-12) |
| :97 | `debugMode` | Key `llm.logResponse` |
| :99 | `showRealtimeAiResponse` | Live-Response im Chat |
| :101 | `queryParams : Map` | an jede Request-URL (OpenAiProvider:50, LmStudio:49) |
| :103 | `headerParams : Map` | Custom-Header je Provider (OpenAi:49, Anthropic:45/73, Copilot:55/93) |

`SHELL_CONFIRMATION_ENABLED` (Keys:25) hat KEIN Core-Feld, Loader liest ihn nie — plugin-seitig
(`ShellConfirmationMode`). MCP/Voice (PeonConstants:28-37) = eigene Plugin-Features, nicht LlmConfig.

## 2. Agent-Level
`AgentModelConfig.java:12-13`: `url, apiKey, model, think, extraBody, temperature`; Slots :15-22.
`AgentConfig.java:36-53`: effektiv `provider, id, url, apiKey, model, think, temperature, extraBody`;
think → `reasoning_effort` (ADR-0059/0064).
Vererbung: `LlmConfig.java:166-173` (leeres url/key/extraBody erbt Basis), :257-270 (compact/search),
:279-283 (custom); Dev-Model = Basis (Saver:20-24), Dev-url/-key beim Laden verworfen (Loader:69-70).
Tools je Slot: `LlmConfig.toolAllowlist(agentId)` :184-186, Save Saver:37-39.
Custom-Agent-Frontmatter (`CustomAgent.java:34-60`): model, url, api_key, extra_body, think,
include-default, temperature, tools, read-only, handover (+ deprecated think_*).

## 3. Plugin-Oberfläche
Basis `AiConfigPreferenceView.java`: `ModelConfigWidget` (:51-58) = Provider · URL · API-Key · Modell ·
**Think · Temperatur** · Extra-Body (:92-101, :158-181), Ping :199-202; Dev-Tools-CSV + Preview :62-67,
:172-189; **Auto compact after** = `PREF_TOKEN_WINDOW` :69; **Resend model thinking** :71-72;
Copilot-Device-Flow :191-205; Disk Tools :76-77; Shell-Confirmation (3 Werte) :79-85; Config-Dir :87-88.
Advanced `AiAdvancedPreferenceView.java`: **Timeout** :51-52; 5 Agent-Sektionen :34-39/:56-58;
**Max output tokens** :62; **Query Params (CSV k=v,…)** :64-67; **Header Params (CSV: k=v,k2=v2)** :69-72
(= „Header-Einstellungen"); Debug mode :74; Show real-time AI response :75.
Je Agent `AgentModelConfigSection.java:73-80`: URL, API-Key, Modell (`ModelComboWidget`), Think
(Form je Provider :66-68/:172-181), Temperatur, Extra-Body, Tools-CSV + Preview (Compact ohne Tools
:81-84).

**reasoning_effort = der Think-Wert** (kein eigenes Feld): Formen `ThinkSupport.java:11-36`
(Toggle/Values/FreeString/None/Unknown); OpenAI-Familie `ProviderRequestSupport.java:90-94` — Values
aus `ReasoningEffort.Known`, Versand verbatim :82-84; Ollama Toggle (OllamaProvider:81), Anthropic
Values(adaptive, enabled) (:89), LM Studio FreeString (:85), Gemini/Mistral None (GoogleGemini:70,
Mistral:53). Widget-Formen `ThinkValueSupport.java:14-31`, Live-Wechsel `ModelConfigWidget:245-252`.

## 4. Fabrik heute
UI `config.page.tsx`: Draft :37-45 / Patch :61-73 — Provider (Select :177-183), **Modell = Freitext-Input**
(:196-200), URL :206-210, Token-Fenster :221-226, Timeout :232-237, **Think senden = yes/no-Select**
(:244-254), Key (:263-283); Provider-Dynamik :119-131.
`config.agents.tsx`: Override :223, Modell :228-233, URL :234-239, Think (Form des Basis-Providers,
Dev = „Think (Default)") :241-247 + `ThinkField :92-136`, Temperatur :248-254, Key :255-281, Save :285-291.
`config.client.ts`: Settings :10-19, Provider/ThinkInput :30-48, SettingsPatch :88-93, GET/PUT
/api/settings :59-100, GET /api/settings/providers :64-66. `config.agents.client.ts`: AgentSettings
:9-18, AgentPatch :26-30, GET /api/settings/agents, PUT /api/settings/agents/{id} :32-42.
Backend: `SettingsController.java:21,30-49`; `SettingsSnapshot.java:9-10` = provider, model, url,
tokenWindow, timeoutSeconds, sendThinking, keySet, keySource; `SettingsUpdate.java:25-26`; Keys
`FactoryConfigService.java:165-171`; Config-Dir/Disk-Tools gepinnt (:277-282, SettingsUpdate:21-23).
Provider-Antwort `FactoryConfigService.providerDefaults():121-131` = Name, defaultBaseUrl,
thinkSupported, ThinkForm (:13-35), thinkValues (bei Values u. a. Reasoning-Efforts).
Agent: `AgentSettingsController.java:24,33-43`; `AgentSettingsDto.java:11-12` (id,name,overridden,
model,url,think,temperature,keySet); Keys `AgentConfigFile.java:50-52` = model,url,api_key,think,
temperature — **extra_body bewusst nicht (D-13)**, tools/read-only unberührt :140-141; Slots
`AgentSlots.java:23-29`.

## 5. Delta (Fehlend in der Fabrik)
| Fehlend | Plugin/Core-Beleg |
|---|---|
| **Basis-Think (reasoning_effort) auf Default-Ebene** — Default-Seite kennt nur `sendThinking` (Boolean) | ModelConfigWidget:158-181 (think im Basis-Widget); in der Fabrik nur über Dev-Override erreichbar (config.agents.tsx:241-247) |
| **Modell-Auswahl statt Freitext** — kein Modell-Listen-Endpunkt (grep listAiModels/listModels//models in factory-backend: leer), UI = Input | ModelComboWidget:55-63 (editierbare Combo + Refresh), :79-97 (Fetch/Cache), :124-135 (No-append: Auswahl ODER Freitext); F8-Forderung open-points:248-250 |
| **Header-/Query-Params** (die „Header-Einstellungen") | AiAdvancedPreferenceView:64-72, LlmConfig:101-103; keine Felder in SettingsUpdate/SettingsDto (grep headerParams factory-backend: leer) |
| **maxTokens** | AiAdvancedPreferenceView:62, LlmConfig:77 |
| **extraBody je Agent** | AgentModelConfig:12, ExtraBodyWidget:87-89; Fabrik: FactoryConfigService:312 (null), AgentConfigFile:47-48 |
| **Tools-Allowlist je Agent + Preview** | AgentModelConfigSection:80-83, LlmConfigSaver:37-39; Fabrik schreibt tools nie (AgentConfigFile:140-141) |
| **Debug mode / Show real-time response** | AiAdvancedPreferenceView:74-75, LlmConfig:97,99 |
| **Shell-Command-Confirmation** (3stufig) | AiConfigPreferenceView:79-85; Key LlmConfigKeys:25, kein Core-Feld |
| **Ping/Erreichbarkeit, Copilot-Login** | ModelConfigWidget:199-202; AiConfigPreferenceView:191-205; kein Ping in factory-backend |
| **MCP- und Voice-Einstellungen** | PeonConstants:28-37 |
| Bewusst nicht: Config-Dir, diskToolsEnabled | SettingsUpdate:21-23, FactoryConfigService:277-282, config.md:23-27 |
| Nicht erfunden: globales Temperatur-Feld | config.md:53-55 (Temperatur nur je Agent) |

**Korrektur der F8-Beschreibung:** „reasoning_effort nicht einstellbar" ist teilweise überholt — die
Agent-Think-Felder bauen ihre Werte-Liste bereits aus den Provider-Daten (providerDefaults →
ThinkForm.VALUES + thinkValues, UI config.agents.tsx:113-131). Die Lücke ist die **Default-Ebene**
(nur sendThinking) und das Fehlen von Modell-Liste, Headern, maxTokens, extraBody, Tools.

## Jons Scope-Empfehlung (2026-11-09, Nachtzyklus)
Buildbar im MVP-Umfang (Web-Parität zum Plugin, ohne Eclipse-Eigenes):
1. **Basis-Think** auf der Default-Ebene (Form des Basis-Providers, wie je Agent) — ersetzt/ergänzt
   „Think senden". Klärungsbedarf: Verhältnis `sendThinkingEnabled` (Global-Flag „Think zurückschicken")
   zum Basis-Think-Wert — Plugin hat BEIDE (Widget think + „Resend model thinking" :71-72).
2. **Modell-Feld = AutoComplete** (Auswahl ODER Eingabe, Paul 2026-11-08) — braucht Modell-Listen-Quelle
   (Core-Fähigkeit? ModelComboWidget-Fetch prüfen) oder erst ohne Liste (freie Eingabe).
3. **Header-/Query-Params, maxTokens** auf der Default-Ebene (Advanced).
4. **extraBody + Tools-Allowlist je Agent** (Agent-Ebene; D-13 „extra_body bewusst nicht" wäre zu
   revidieren — Core-Proposal-Regel prüfen: AgentConfigFile ist Fabrik-Code, kein Core).
Eclipse-eigen / später: Copilot-Device-Flow, Shell-Confirmation (plugin-seitiger Key), MCP, Voice,
Ping (Wert im Web fraglich), debugMode/showRealtime (Web streamt immer).
