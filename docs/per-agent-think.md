---
idPrefix: THINK
---
# Per-Agent Think Support

## Goal

Each agent has **one** per-agent think value (a String) and resolves its own effective request
value from it. `AiProvider` maps the resolved string to provider-specific request parameters.
Es gibt **keinen** separaten "supports"-Boolean — `think_supported` fliegt komplett raus,
abgeleitet wird ausschließlich aus dem Think-Level ([ADR-0059](adr/0059-think-dropdown-empty-unset.md),
Issue #149). Einige Provider haben statt `none`/`medium`/`high`/`xhigh` nur `true`/`false`/`""`.

**Ab R-THINK-10 (✅ done, 2026-09-28) gilt die Fallback-Kette Agent→Base:** ein leerer
per-agent Think erbt den **Base-Think** (Dev-Record) — die einfache Config definiert den Default,
die Advanced-Page/Frontmatter nur noch Ausnahmen. „Empty = nichts senden" bleibt auf den Stufen
gültig, wo auch die Basis leer ist; ein Agent, der trotz Base-Default **nichts** senden will,
setzt explizites off.

## Business Rules

### R-THINK-1: Ein Think-Wert je Agent — leer = unset ✅ done (2026-09-27)

#### UC-THINK-1 — emptyThinkOmitted
Der per-agent Think-String ist die einzige Quelle. `null`/`""` = unset → es wird **kein**
think/reasoning-Parameter gesendet — für **alle** Provider einheitlich („Empty means unset",
AGENTS.md).

- **GIVEN** per-agent Think ist leer WHEN ein Request gebaut wird **THEN** kein think/reasoning-Parameter im Request (Ollama wie OpenAI)
- **GIVEN** per-agent Think = `"false"` **WHEN** ein Ollama-Request gebaut wird **THEN** `"think": false`
- **GIVEN** per-agent Think = `"false"` **WHEN** ein OpenAI-Request gebaut wird **THEN** `reasoning_effort:"false"` (verbatim — ab R-THINK-11, [ADR-0064](adr/0064-verbatim-think-values.md); vorher: Parameter fehlte, „OpenAI kennt kein off")

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
- **GIVEN** think = `"false"` **WHEN** LM-Studio-Request **THEN** `reasoning:"false"` (verbatim — ab R-THINK-11, ADR-0064; vorher: Mapping zu `reasoning=off`)

### R-THINK-4: Think-Dropdown statt Checkbox ✅ done (2026-09-27, Paul-Smoke ✅ 2026-09-30)

#### UC-THINK-4 — thinkToggleComboNotCheckbox
`ThinkSupport.Boolean` (die per-agent Checkbox, nur Ollama) entfällt ersatzlos — jeder Provider
mit per-request Think bekommt ein editierbares Dropdown.

- **GIVEN** ein Provider mit per-request Think (auch Ollama) **WHEN** die Config-Seite rendert **THEN** editierbares Think-Dropdown, keine Checkbox
- **GIVEN** Ollama **WHEN** Dropdown-Items initialisiert **THEN** `""`, `"true"`, `"false"`
- **GIVEN** Ollama-Dropdown leer **WHEN** gespeichert **THEN** nichts persistiert (unset)
- Clean Break: `ThinkSupport.Boolean` + `booleanValue`/`booleanOn` verschwinden, keine Migration

### R-THINK-5: Basis-Checkbox „Default model supports thinking" raus ✅ done (2026-09-27, Paul-Smoke ✅ 2026-09-30)

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
- **GIVEN** legacy `think_supported: false` **WHEN** E2E (eigener Stub, B6) **THEN** `"false"` geht verbatim als `reasoning_effort:"false"` durch (ab R-THINK-11, ADR-0064 — kein stiller Omission; Garbage sichtbar im Request-Log)
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

- **AB R-THINK-11 (ADR-0064):** das On-Mapping entfällt für alle String-Provider — `/thinking/OPEN_AI` gelöscht, `true` geht verbatim in den Think-Kanal. Das Ressourcen-Mapping lebt nur noch für ANTHROPIC (frozen): **GIVEN** ein Anthropic-Model matcht ein Pattern **WHEN** aufgelöst **THEN** gemappter Wert — **GIVEN** ein unknown Gateway-Model ohne Mapping **WHEN** aufgelöst **THEN** reasoning-Attribut entfällt.

### R-THINK-9: send-thinking transport bleibt global ✅

#### UC-THINK-9 — sendThinkingTransportGlobal
`sendThinking` / `returnThinking` sind langchain4j build-time switches — eine globale Preference,
unabhängig vom per-agent Think-Wert.

- **GIVEN** think unset **AND** globales send-thinking enabled **WHEN** Config geprüft **THEN** Thinking wird weiterhin zurückgesendet

### R-THINK-10: Base-Think-Default — leerer Agent-Think erbt den Base-Think ✅ done (2026-09-28, `c90debfb`, Surefire core 1040/0)

**WEIL (Paul):** „Offline local default — ich will mit der einfachen Config durchkommen; nur für
Compact und Search definiere ich ein anderes Think." Die einfache Config (Basic-Page) ist der
Default-Editor, die Advanced-Page/Frontmatter definieren Ausnahmen. Der „verlorene" Default aus
der Zeit vor der Per-Agent-Trennung kommt zurück — als echtes SOLL-Fallback, nicht als
Capability-Flag.

**Resolution-Kette (alle Agenten einheitlich — PO, Plan, Search, Compact, Custom):**
1. per-agent Think **gesetzt** (auch explizites off) → verbatim (R-THINK-1-Semantik unverändert).
2. per-agent Think **leer** → Base-Think (Dev-Record, `llm.agent.dev.think` — dass der Dev-Slot
   die Base-Semantik trägt, ist IST: `LlmConfig.devAgentConfig()`, `LlmConfigSaver`).
3. Base auch leer → unset → **nichts senden** („Empty means unset" auf der untersten Stufe).

Der Dev-Agent liest weiterhin seinen Record verbatim (er *ist* die Basis, kein Selbst-Fallback).
Explizites off gewinnt **immer** — trotz Base-Default kann ein Agent Thinking ausstellen
(Off-Tokens R-THINK-7; für Provider ohne off-Konzept entfällt der Parameter).

#### UC-THINK-10 — emptyAgentThinkInheritsBaseThink
- **GIVEN** Base-Think = `true`, Search-Think = leer **WHEN** ein Search-Request gebaut wird **THEN** der Base-on-Wert wird gesendet (gemappt wie R-THINK-8)
- **GIVEN** Base-Think = `true`, Search-Think = `"false"` **WHEN** Search-Request **THEN** explizites off — **kein** Fallback
- **GIVEN** Base-Think = leer, Agent-Think = leer **WHEN** Request **THEN** nichts gesendet (R-THINK-1 unverändert)
- **GIVEN** Custom-Agent-Frontmatter ohne `think`, Base-Think = `true` **WHEN** Custom-Request **THEN** Base-Wert geerbt (ein Verhalten, eine Implementierung — auch für Custom)
- **GIVEN** Dev-Think = leer **WHEN** Dev-Request **THEN** nichts gesendet (Dev erbt nicht von sich selbst)
→ `LlmConfigTest` (Slot-Parität + off-gewinnt + Dev-verbatim) + `CustomAgentServiceTest` (Frontmatter erbt Base) — Surefire core 1040/0

---

## Rework 2026-09-29 (Paul — „Verbatim Think": String-Provider senden exakt was gesetzt ist)

**WARUM:** Paul-Smoke 2026-09-29 — `think=none` auf OPEN_AI (LM-Studio-Server) kam nicht im
Request an; das Modell dachte trotzdem weiter (Config „aus", Verhalten „an"). Da Mek/Da Sniffa
belegten: kein Transport-Bug, sondern SOLL (Off-Omission für die OpenAI-Familie, UC-THINK-1) —
aber die Prämisse „OpenAI kennt kein off" widerspricht dem SDK (`ReasoningEffort.Known` enthält
`none`), und der Off-Set/On-Mapping-Resolver war Checkbox-Erbe (ADR-0059 hat die Checkbox schon
entfernt). → [ADR-0064](adr/0064-verbatim-think-values.md).

### R-THINK-11 — String-Provider: verbatim, kein Resolver ✅ done (2026-09-29, `3b7d5df4`…`b7f175e6`, Surefire core 1051/0, PDE 313/0)

#### UC-THINK-11 — verbatimThinkStringProviders

GIVEN ein String-Provider (ThinkSupport ≠ Toggle: OPEN_AI, OPEN_AI_OFFICIAL, GITHUB_COPILOT,
GITHUB_MODELS, LM_STUDIO — **nicht** ANTHROPIC/GEMINI/MISTRAL, die behalten ihre provider-eigene
Übersetzungsschicht, ADR-0064 Decision 3) WHEN ein Think-Wert gesetzt ist THEN geht er **unverändert**
in den provider-eigenen Think-Kanal (`reasoning_effort` / `reasoning` / Provider-Params) — auch
`"true"`, `"none"`, `"off"` wörtlich. **Leer = unset = nichts senden** (unverändert). Der
Off-Omission-Pfad und das On-Mapping (`ThinkModelMapping.resolveOn`, `/thinking/OPEN_AI`) entfallen
für String-Provider; die `Off/Auto`-Pseudo-Entries fliegen aus den Combos.

- **GIVEN** OPEN_AI, think = `"none"` **WHEN** Request gebaut **THEN** `reasoning_effort:"none"` (vorher: Parameter fehlte — der gemeldete Bug)
- **GIVEN** OPEN_AI, think = `"xhigh"` **WHEN** Request **THEN** `reasoning_effort:"xhigh"` (verbatim — Test-Lücke geschlossen)
- **GIVEN** OPEN_AI, think = `"true"` **WHEN** Request **THEN** `reasoning_effort:"true"` (as-is, kein resolveOn)
- **GIVEN** LM_STUDIO, think = `"off"` **WHEN** Request **THEN** `reasoning:"off"` (E2E-Body-Capture — bisher nur Parameter-Ebene getestet)
- **GIVEN** LM_STUDIO, think = `"true"` **WHEN** Request **THEN** `reasoning:"true"` (**Verhaltensänderung:** vorher `"on"`)
- **GIVEN** think = leer **WHEN** Request **THEN** nichts gesendet (R-THINK-1 unverändert)
- **GIVEN** Custom-Agent-Frontmatter `think: false` **WHEN** String-Provider-Request **THEN** `"false"` wörtlich gesendet (Garbage in, sichtbar im Request-Log, garbage out — Dropdowns sind der Guard)
- Tests: `AiProviderRequestParametersTest` (OPEN_AI none/xhigh/true verbatim, LM_STUDIO verbatim, Ollama unverändert) + E2E Body-Capture (`CustomAgentConnectionE2ETest`/`PerAgentConnectionE2ETest`) — Memory-Regel 30: Payload capturen, nicht nur „ein Call kam"

### R-THINK-12 — Toggle-Provider (OLLAMA): Boolean-Interpretation bleibt ✅ done (2026-09-29, `3b7d5df4`…`b7f175e6`)

#### UC-THINK-12 — toggleBooleanInterpretation

GIVEN ein Toggle-Provider (Booleans-API, hier OLLAMA) WHEN ein String ins Dropdown eingegeben wird
THEN wird er zu `true`/`false` interpretiert: **on** = `true/on/yes/ja`, **off** =
`false/off/no/nein/none` (case-insensitive); nicht-off und nicht-leer = `true` (bestehende Regel,
um ja/nein erweitert). `null` = Feld weg — bleibt vom expliziten `think:false` unterschieden.

- **GIVEN** Ollama, think = `"false"`/`"FALSE"`/`"off"`/`"nein"` **WHEN** Request **THEN** `think:false`
- **GIVEN** Ollama, think = `"true"`/`"ja"` **WHEN** Request **THEN** `think:true`
- **GIVEN** Ollama, think = `"banana"` **WHEN** Request **THEN** `think:true` (nicht-off, nicht-leer — wie heute)
- **GIVEN** Ollama, think = `null` **WHEN** Request **THEN** Feld weg (unset, UC-THINK-1)
- **GIVEN** Ollama, think = `"false"` **WHEN** E2E **THEN** `think:false` im Body (`.log:4078`-Verhalten bleibt)

## Provider Semantics

> ⚠️ **Ab Rework 2026-09-29 (R-THINK-11/12, ADR-0064) gültig:** String-Provider senden verbatim;
> Bullets 2–3 unten (Off-Tokens, On/generic) sind **historisch** für die String-Provider
> (OPEN_AI/LM_STUDIO/…) — Ollama (Bullet 4) bleibt gültig (R-THINK-12), ANTHROPIC behält seine
> provider-eigene Übersetzungsschicht (frozen).

- **Leer/unset (`null`, `""`):** nichts gesendet — das Modell entscheidet (Denkmodelle denken default). Alle Provider gleich.
- **Off-Tokens (`false`/`FALSE`/`none`/`no`/`off`, case-insensitive):** explizites off, wo der Provider es kennt — Ollama: `think:false`; LM Studio: `reasoning=off`; OpenAI/Anthropic: reasoning entfällt (kein off-Konzept), es sei denn ein Provider-Mapping definiert ein off.
- **On/generic `true`:** Provider/Model-Mapping (Ressourcen-Dateien), sonst generisch.
- Ollama unterscheidet unset (`null` → Feld weg) vom expliziten off (`think:false`).

## ADRs
- [ADR-0001](adr/0001-per-agent-think-string.md) — Think resolved to per-agent String
- [ADR-0002](adr/0002-model-mapping-resource-files.md) — Mapping in resource files
- [ADR-0003](adr/0003-send-thinking-independent.md) — send-thinking independent of support
- [ADR-0059](adr/0059-think-dropdown-empty-unset.md) — Checkbox raus, leer = unset (Issue #149) — Off-Token-Semantik für String-Provider teils superseded durch [ADR-0064](adr/0064-verbatim-think-values.md)
- [ADR-0064](adr/0064-verbatim-think-values.md) — Verbatim-Think: String-Provider senden as-is, nur Toggle-Provider interpretieren

## Non-goals
- Per-agent send-thinking transport (blocked by langchain4j build-time limitation)
- Separate persisted runtime on/off state beyond the think string