---
idPrefix: THINK
---
# Per-Agent Think Support

## Goal

Each agent has **one** per-agent think value (a String) and resolves its own effective request
value from it. **Leer = unset = es wird nichts gesendet.** `AiProvider` maps the resolved string
to provider-specific request parameters. Es gibt **keinen** separaten "supports"-Boolean —
`think_supported` fliegt komplett raus, abgeleitet wird ausschließlich aus dem Think-Level
([ADR-0059](adr/0059-think-dropdown-empty-unset.md), Issue #149). Einige Provider haben statt
`none`/`medium`/`high`/`xhigh` nur `true`/`false`/`""`.

## Business Rules

### R-THINK-1: Ein Think-Wert je Agent — leer = unset ✅ done (2026-09-27)

#### UC-THINK-1 — emptyThinkOmitted
Der per-agent Think-String ist die einzige Quelle. `null`/`""` = unset → es wird **kein**
think/reasoning-Parameter gesendet — für **alle** Provider einheitlich („Empty means unset",
AGENTS.md).

- **GIVEN** per-agent Think ist leer WHEN ein Request gebaut wird **THEN** kein think/reasoning-Parameter im Request (Ollama wie OpenAI)
- **GIVEN** per-agent Think = `"false"` **WHEN** ein Ollama-Request gebaut wird **THEN** `"think": false`
- **GIVEN** per-agent Think = `"false"` **WHEN** ein OpenAI-Request gebaut wird **THEN** kein reasoning-Parameter (OpenAI kennt kein explizites off)

### R-THINK-2: Persistenz-Roundtrip — explizites Off überlebt ✅ done (2026-09-27, Issue #149)

#### UC-THINK-2 — explicitOffSurvivesPersistence
Ein gespeicherter Off-Wert darf auf dem Weg Saver → Store → Loader → `AgentConfig` nie zu `null`
(unset) kollabieren. IST vor dem Fix: Boolean-off → `""` → Saver entfernt den Key
(`LlmConfigSaver`) → Loader `stripToNull` → `null` → Feld weggelassen — `think:false` war über die
UI unerreichbar.

- **GIVEN** per-agent think = `"false"` **WHEN** Saver→Store→Loader→`AgentConfig.think()` **THEN** `"false"` (niemals `null`/`""`)
- **GIVEN** per-agent think = `""` **WHEN** Saver **THEN** Key wird entfernt (unset bleibt unset)
- **GIVEN** think = `"false"` **WHEN** E2E (Agent.call → StreamMock, Ollama) **THEN** Request trägt `think:false`
- **Regression:** der Roundtrip-Test war vor dem Fix rot (Issue-#149-Reproduktion)

### R-THINK-3: Off-Tokens — `false`/`FALSE`/`none`/`no`/`off` ✅ done (2026-09-27)

#### UC-THINK-3 — offTokensCaseInsensitive
Erkennung case-insensitive und getrimmt. Ein nicht-leerer Wert, der kein Off-Token ist, ist on
(`true`, `high`, …).

- **GIVEN** think = `"FALSE"` / `"None"` / `"No"` / `"off"` (beliebige Schreibweise) **WHEN** Ollama-Request **THEN** `think:false`
- **GIVEN** think = `"true"` oder `"high"` **WHEN** Ollama-Request **THEN** `think:true`
- **GIVEN** think = `"false"` **WHEN** LM-Studio-Request **THEN** `reasoning=off` (bisheriges Verhalten unverändert)

### R-THINK-4: Think-Dropdown statt Checkbox ✅ done (2026-09-27, Paul-Smoke steht aus)

#### UC-THINK-4 — thinkToggleComboNotCheckbox
`ThinkSupport.Boolean` (die per-agent Checkbox, nur Ollama) entfällt ersatzlos — jeder Provider
mit per-request Think bekommt ein editierbares Dropdown.

- **GIVEN** ein Provider mit per-request Think (auch Ollama) **WHEN** die Config-Seite rendert **THEN** editierbares Think-Dropdown, keine Checkbox
- **GIVEN** Ollama **WHEN** Dropdown-Items initialisiert **THEN** `""`, `"true"`, `"false"`
- **GIVEN** Ollama-Dropdown leer **WHEN** gespeichert **THEN** nichts persistiert (unset)
- Clean Break: `ThinkSupport.Boolean` + `booleanValue`/`booleanOn` verschwinden, keine Migration

### R-THINK-5: Basis-Checkbox „Default model supports thinking" raus ✅ done (2026-09-27, Paul-Smoke steht aus)

#### UC-THINK-5 — baseCheckboxRemovedDerivedSupport
`PREF_THINK_SUPPORTED` (Basic-Seite) entfällt — sie beeinflusste den Request ohnehin nicht und hat
Issue-#149-Reporter verwirrt. `isThinkSupported()` leitet sich aus dem Think-Wert ab (wie
`AiPlanAgent`/`AiPoAgent` es schon tun).

- **GIVEN** dev think = `"true"` **WHEN** `isThinkSupported()` **THEN** `true`
- **GIVEN** dev think = `""` oder ein Off-Token **WHEN** `isThinkSupported()` **THEN** `false`
- **GIVEN** Preference-Store mit dem alten Key **WHEN** Config geladen **THEN** Key ignoriert (Clean Break)

### R-THINK-6: Custom Agents — `think`-Frontmatter, `think_supported` fliegt raus ✅ done (2026-09-27, Paul-Entscheid)

#### UC-THINK-6 — customAgentThinkFrontmatter
Paul 2026-09-27: `think_supported` (und `think_on_string`/`think_off_string` als Steuerung)
entfallen ersatzlos — abgeleitet wird **komplett** aus dem Think-Level (ein String). Nicht gesetzt
→ nichts senden. Legacy-Keys bleiben **les**kompatibel und wandern beim Write in `think`.

- **GIVEN** AGENT.md `think: false` **WHEN** geladen **THEN** think() = `"false"` → Ollama sendet `think:false`
- **GIVEN** AGENT.md ohne think **WHEN** geladen **THEN** unset → es wird nichts gesendet
- **GIVEN** legacy `think_supported: false` **WHEN** gelesen **THEN** als explizites off (`"false"`) abgeleitet (User-Intention bleibt)
- **GIVEN** legacy `think_on_string: high` **WHEN** gelesen **THEN** think() = `"high"`
- **GIVEN** Write **THEN** Datei enthält nur `think` — `think_supported`/`think_enabled`/`think_on_string`/`think_off_string` entfernt (Migrate-on-write, wie heute)
- **GIVEN** legacy `think_supported: false` **WHEN** E2E (eigener Stub, B6) **THEN** off-Signal korrekt, kein reasoning_effort (OpenAI)
- **GIVEN** `think` = `"high"` **WHEN** returnThinking geprüft **THEN** abgeleitet aus isOn(think) OR globalem send-thinking (ersetzt ADR-0003s think_supported-Bein)

### R-THINK-7: Built-in Agenten unabhängig ✅

#### UC-THINK-7 — builtInAgentsIndependent
Built-in agents resolve independently from the same `LlmConfig`.

- **GIVEN** Dev think = `""` **AND** Plan think = `"high"` **WHEN** beide `AgentConfig` verglichen **THEN** Dev unset, Plan `"high"`
- **GIVEN** Compact oder Search nutzen Ollama mit unset think **WHEN** Request gebaut **THEN** kein think-Parameter

### R-THINK-8: Provider/Model-Mapping in Ressourcen ✅

#### UC-THINK-8 — providerModelMappingFiles
Auto/generic on (`true`) wird über `resources/thinking/<PROVIDER>`-Dateien übersetzt. Format
`pattern | on | off`. First match wins.

- **GIVEN** ein OpenAI-Reasoning-Model matcht ein Pattern **WHEN** aufgelöst **THEN** gemappter Wert (z. B. `high`)
- **GIVEN** ein unknown Gateway-Model ohne Mapping **WHEN** aufgelöst **THEN** reasoning-Attribut entfällt

### R-THINK-9: send-thinking transport bleibt global ✅

#### UC-THINK-9 — sendThinkingTransportGlobal
`sendThinking` / `returnThinking` sind langchain4j build-time switches — eine globale Preference,
unabhängig vom per-agent Think-Wert.

- **GIVEN** think unset **AND** globales send-thinking enabled **WHEN** Config geprüft **THEN** Thinking wird weiterhin zurückgesendet

## Provider Semantics

- **Leer/unset (`null`, `""`):** nichts gesendet — das Modell entscheidet (Denkmodelle denken default). Alle Provider gleich.
- **Off-Tokens (`false`/`FALSE`/`none`/`no`/`off`, case-insensitive):** explizites off, wo der Provider es kennt — Ollama: `think:false`; LM Studio: `reasoning=off`; OpenAI/Anthropic: reasoning entfällt (kein off-Konzept), es sei denn ein Provider-Mapping definiert ein off.
- **On/generic `true`:** Provider/Model-Mapping (Ressourcen-Dateien), sonst generisch.
- Ollama unterscheidet unset (`null` → Feld weg) vom expliziten off (`think:false`).

## ADRs
- [ADR-0001](adr/0001-per-agent-think-string.md) — Think resolved to per-agent String
- [ADR-0002](adr/0002-model-mapping-resource-files.md) — Mapping in resource files
- [ADR-0003](adr/0003-send-thinking-independent.md) — send-thinking independent of support
- [ADR-0059](adr/0059-think-dropdown-empty-unset.md) — Checkbox raus, leer = unset (Issue #149)

## Non-goals
- Per-agent send-thinking transport (blocked by langchain4j build-time limitation)
- Separate persisted runtime on/off state beyond the think string