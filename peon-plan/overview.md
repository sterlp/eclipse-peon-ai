# Plan — Night-Cycle A: E2E-Beweis per-agent Model-Config (Routing + Config on the Wire)

Status: FINAL (Research abgeschlossen 2026-08-30). Ersetzt den 2b-Plan (abgenommen/archiviert).
Branch: `new-config` @ f600862. Scope: **Core-Projekt** (`llmpeon-core`), keine Plugin-/UI-/Docs-/Homepage-Änderungen.

## 1. Context — Ziel

Beweisen (User: „nur prüfen, dass das sauber funktioniert"), dass die per-agent Model-Config
(Zyklen 2a/2b) Requests **an die konfigurierte URL routet** und die **Config im Request ankommt** —
auch **nach einem Config-Edit** (kein Stale-Cache). LLM-Stub 3× auf dynamischen Ports;
Varianten-/Parameterized-Tests; redundante/simulierte Tests entfernen/ersetzen.

OUT OF SCOPE: SWT-Bug in `AgentModelConfigSection` (❌-Regel in docs/advanced-configuration.md),
2c (Homepage/Icons-Seite), Tool-Bug-Fixes (nur dokumentiert), Plugin-Code-Änderungen.

## 2. Design decisions

### D1 — Stub: `MockLlmServer` ERWEITERN (keine neue Klasse)
`/llmpeon-core/src/main/java/org/sterl/llmpeon/mock/MockLlmServer.java` (JDK-HttpServer, dynamischer
Port, responseQueue, `lastRequestBody`-Capture — alles vorhanden). Hinzufügen:
- `POST /v1/messages` (Anthropic): rohen Body in `lastRequestBody` capture (KEIN `captureMessages`
  — der ist OpenAI-shaped; neuer Handler fängt nur den Raw-Body). Antwort = minimaler
  Anthropic-SSE-Stream (s. D3).
- `POST /api/chat` (Ollama): rohen Body capture. Antwort = NDJSON (s. D3).
- `String rootUrl()` → `http://127.0.0.1:<port>` (ohne `/v1`) — Ollama-Base; bestehende
  `getUrl()` (`…/v1`) bleibt für OpenAI/Anthropic.
- Beide neuen Endpoints streamen IMMER (E2E nutzt nur Streaming); Content kommt aus dem
  bestehenden `responseQueue` (1 String-Antwort je Test genügt). `reset()` unverändert brauchbar.

Begründung gegen neue Klasse: Lifecycle/Capture/Queue sind bereits da; zwei Handler ≈ 60 Zeilen;
eine zweite Stub-Klasse würde `start/stop/isAlive/queue/capture` duplizieren. (Offene Frage 2
aus der Vorgabe → Entscheidung: erweitern.)

### D2 — Ein neuer E2E-Test (Core), bestehender Wiring-Test wird ersetzt
- NEU: `/llmpeon-core/src/test/java/org/sterl/llmpeon/tool/PerAgentConnectionE2ETest.java`
  (Paket `org.sterl.llmpeon.tool` neben `ToolService`). Vehicle:
  `new ToolService(false).executeLoop(ToolLoopRequest.builder()…)` — exakt wie der existierende
  Wiring-Test (echter Tool-Loop → `ToolLoopRequest.call` → `chatModel.modelFor(agentConfig)` →
  `StreamingBridge` → echter HTTP). 1 UserMessage, gequeuedte Antwort, `@Timeout(30)`,
  Stubs per Test start/stop (`@AfterEach`), `reset()`-semantik via frische Instanzen.
- Löschkandidaten (s. §5): `ToolLoopRequestConnectionTest` (wird subsumiert),
  `AiServicePerAgentModelTest` (wird subsumiert, Mapping bleibt in `LlmConfigTest` abgedeckt).

### D3 — Wire-Formate (verifiziert gegen langchain4j 1.18.1, Source `/langchain4j-aggregator`)
**Anthropic** (`DefaultAnthropicClient` + `InternalAnthropicHelper`):
- `POST {baseUrl}/messages` (baseUrl = konfigurierte URL `…/v1`), Header `x-api-key`,
  `anthropic-version: 2023-06-01`.
- Per-request Model: `createAnthropicRequest` setzt `.model(chatRequest.modelName())` —
  per-request Parameter OVERRIDDEN das model-level `modelName` (also Base-Erbe + Agent-Model
  funktioniert: Request an Base-Stub mit Agent-Model im Body).
- Minimale SSE-Antwort (Format `event: <type>\ndata: <json>\n\n`):
  `message_start` (optional, message.model/usage) → `content_block_start` {index:0,
  content_block{type:"text"}} → `content_block_delta` {index:0, delta{type:"text_delta",
  text:"…"}} → `content_block_stop` {index:0} → `message_delta` {delta{stop_reason:"end_turn"}} →
  `message_stop` (triggert onComplete).
**Ollama** (`OllamaClient` + `OllamaServerSentEventParser` + `OllamaChatRequest`):
- `POST {baseUrl}/api/chat` (baseUrl OHNE `/v1` → `rootUrl()`).
- **NDJSON, kein SSE!** (Parser liest zeilenweise; `OllamaServerSentEventParser` Javadoc zeigt
  exakt das Format). Je Line ein JSON-Objekt; letzte Line MUSS `done:true` tragen
  (+ `done_reason:"stop"`, `prompt_eval_count`/`eval_count` für TokenUsage):
  `{"model":"…","message":{"role":"assistant","content":"Hello"},"done":false}`
  `{"model":"…","message":{"role":"assistant","content":""},"done_reason":"stop","done":true,"prompt_eval_count":5,"eval_count":8}`
- Request-Body: `@JsonInclude(NON_NULL)` + `@JsonNaming(SnakeCase)` → `think` fehlt, wenn null;
  `model`/`messages`/`think` wie benannt.
**OpenAI**: unverändert (`/v1/chat/completions`, existierender Handler).

### D4 — Test-Matrix (Parameterized, JUnit 5 `@MethodSource`)
`PerAgentConnectionE2ETest` mit Varianten-Record (je Variante eigene frische Stubs):
```java
record Variant(String name, AiProvider provider, String model, String think, String extraBody,
               Consumer<JsonNode> expectThink, Consumer<JsonNode> expectExtraBody) {}
```
Stub-URL je Provider: `provider == OLLAMA ? stub.rootUrl() : stub.getUrl()`.
- **OpenAI** (`OPEN_AI`, PER_REQUEST): model `"claude-mock"` (triggert Provider-Eintrag
  `cache_control{type:ephemeral}` → erlaubt User-wins-Nachweis), think `"medium"` →
  `reasoning_effort:"medium"`; extraBody (nur Szenario B)
  `{"foo":"bar","cache_control":{"type":"user-wins"},"model":"hacked"}` → Body: `foo=="bar"`,
  `cache_control.type=="user-wins"` (User gewinnt), `model=="claude-mock"` (Reserved-Key gestrippt).
- **Anthropic** (`ANTHROPIC`, BUILD_TIME): model `"claude-mock"`, think `"enabled"` →
  `thinking{type:"enabled", budget_tokens:8000}`; extraBody `{"foo":"bar","model":"hacked"}` →
  `foo=="bar"` baked im Body, `model` gestrippt.
- **Ollama** (`OLLAMA`, NONE): model `"llama-mock"`, think `"true"` → `think:true`;
  extraBody `{"foo":"bar"}` → `foo` NICHT im Body (Mode NONE).

Think-Body-Checks aus Peon-Code verifiziert: `effortFor` (concrete verbatim, off=omitted),
`AnthropicProvider.newRequestParameters` (concrete "enabled" → type+budget 8000),
`ThinkResolver.toOllamaThink` (null=omitted, off=false, sonst true).
ExtraBody-Semantik verifiziert: `ExtraBody.parse` strippt RESERVED_KEYS {model,messages,tools};
`mergeCustomParameters` user-over-provider; BUILD_TIME via `ExtraBody.parse(c.getExtraBody())`
in `AnthropicProvider.buildModel`; NONE ignoriert.

### D5 — Config-Edit-Szenario (kein Stale-Cache)
Verifiziert: `ConfiguredChatModel.updateConfig` setzt `chatModel` null + `agentConnections.clear()`;
`LlmConfig` hat `@EqualsAndHashCode` inkl. `modelConfigs` → `withModelConfig(…)` erzeugt andere
Config → Update greift. Test geht den ECHTEN UI-Pfad: `base.withModelConfig(AgentModelConfig.PLAN, …)`
→ `ccm.updateConfig(newBase)` → `newBase.planAgentConfig()` (Plan-Agent-Mapping bleibt dabei
abgedeckt; Dev-Mapping ist in `LlmConfigTest`).

## 3. Architektur / Datenfluss

`ToolService.executeLoop(ToolLoopRequest)` → `ToolLoopRequest.call(ChatRequest)` (Per-request-Params
aus `AgentConfig.newRequestParameters` via `ToolService`) → `ConfiguredChatModel.modelFor(agent)`:
`EffectiveConnection.of(base, agent)` → `isBase` (url+key gleich base UND kein extraBody; **Model
NICHT in Identity** — per-request angewendet) ? geteiltes Base-Model :
`agentConnections.computeIfAbsent(ConnectionIdentity(provider,url,apiKey,buildTimeBody), …)`.
Kein Code ändert sich — der E2E BEWEIST den Flow gegen echte HTTP-Endpoints.

## 4. Affected Files

| Pfad | Änderung |
|---|---|
| `llmpeon-core/src/main/java/org/sterl/llmpeon/mock/MockLlmServer.java` | +Handler `/v1/messages`, `/api/chat`; +`rootUrl()` |
| `llmpeon-core/src/test/java/org/sterl/llmpeon/mock/MockLlmServerTest.java` | +2 Raw-HTTP-Tests (Anthropic SSE, Ollama NDJSON) |
| `llmpeon-core/src/test/java/org/sterl/llmpeon/tool/PerAgentConnectionE2ETest.java` | NEU (Matrix + Edit) |
| `llmpeon-core/src/test/java/org/sterl/llmpeon/tool/ToolLoopRequestConnectionTest.java` | LÖSCHEN (subsumiert von OpenAI × Agent-own-URL) |
| `llmpeon-core/src/test/java/org/sterl/llmpeon/AiServicePerAgentModelTest.java` | LÖSCHEN (subsumiert; Mapping bleibt in `LlmConfigTest`) |

UNBERÜHRT (bewusst): alle Provider-Klassen, `EffectiveConnection`, `ConfiguredChatModel`,
`LlmConfig`, `ToolService`, `StreamMock` (weiterhin von `AiServicePerAgentThinkTest` + anderen
gebraucht), `ModelConnectionCacheTest` (Unit-Cache-Logik), `AiServicePerAgentThinkTest`
(OPEN_AI_OFFICIAL + `customAgentConfig`-Heuristik — Provider außerhalb der Matrix, NICHT redundant),
`ModelListFetchTest` (Plugin, View-Schicht), `homepage/`, `docs/`, Plugin-Code.

## 5. BDD-Akzeptanz (Szenario → Test)

**S1 Base-Erbe** (je Provider, `PerAgentConnectionE2ETest.inheritsBaseUrl_landsAtBaseStub_withAgentModelAndThink(Variant)`)
GIVEN Base-Config → baseStub (Provider P, Base-Model), Agent-Config OHNE url/key/extraBody,
eigenes Model + think
WHEN Tool-Loop 1 User-Turn
THEN Request AN baseStub (agentStub-Body null); Body `model` == Agent-Model; Think-Mapping im Body
(OpenAI `reasoning_effort`, Anthropic `thinking{}`, Ollama `think`).

**S2 Agent-eigene URL** (je Provider, `PerAgentConnectionE2ETest.agentOwnUrl_landsAtAgentStub_withConfigOnTheWire(Variant)`)
GIVEN Base → baseStub; Agent-Config mit url=agentStub, Model, think, extraBody
WHEN Tool-Loop 1 User-Turn
THEN Request AN agentStub (baseStub-Body null); Body `model` == Agent-Model; Think-Mapping;
extraBody-Semantik: OpenAI merged (User-wins + Reserved gestrippt), Anthropic baked, Ollama absent.

**S3 Config-Edit** (`PerAgentConnectionE2ETest.configEdit_routesToNewUrl_noStaleConnection`, OpenAI)
GIVEN base url=stub1, Plan-Record url=stub2; Request 1 → stub2
WHEN `ccm.updateConfig(base.withModelConfig(PLAN, record(url=stub3)))` + 2 weitere Requests mit
`newBase.planAgentConfig()`
THEN beide Requests AN stub3; stub2 erhält nach dem Edit NICHTS.

**S4 Stub-Protokolle** (Inc 1, `MockLlmServerTest`, `should…`-Naming wie dort)
- `shouldStreamAnthropicSseAndCaptureBody`: POST `/v1/messages` → SSE-Events
  (content_block_start/delta/stop, message_delta `end_turn`, message_stop) + Body captured.
- `shouldStreamOllamaNdjsonAndCaptureBody`: POST `/api/chat` → NDJSON, finale Line `done:true` +
  `done_reason:"stop"` + Body captured.

## 6. Test-Strategie

- JUnit 5 + AssertJ, GIVEN/WHEN/THEN-Kommentare, `@Timeout(30)` (existierendes E2E-Pattern),
  KEINE neuen externen Deps (JDK-HttpServer), keine echten API-Keys.
- Frische Stub-Instanzen je Test (keine Cross-Test-State); `@AfterEach stop()`.
- Body-Assertions über Jackson `JsonNode` (parse von `getLastRequestBody()`).
- Testdelta: Inc1 +2; Inc2 +6 − 1 − 3 (netto +2); Inc3 +1.
- `ModelConnectionCacheTest` bleibt als Unit-Nachweis der Cache-Identity; der E2E S3 beweist
  zusätzlich den Runtime-Effect (kein Stale-Model nach Edit).
- Eclipse: vor Plugin-Testläufen `eclipseBuildProject` (stale-Bundle-Lektion, AGENTS-DEV).
  skills/eclipse-dpe ist leer; keine neuen SKILL-Errungenschaften erwartet — falls doch
  (z. B. langchain4j-Quell-Lese-Tricks), Dev-Agent ergänzt `skills/eclipse-dpe/SKILL.md`.

## 7. Inkremente (jedes einzeln grün)

**Inc 1 — Multi-Provider-Stub (Anthropic + Ollama Wire-Formate)** ✅ DONE
`MockLlmServer`: +`/v1/messages` (Anthropic-SSE), +`/api/chat` (Ollama-NDJSON), +`rootUrl()`.
Tests: `MockLlmServerTest.shouldStreamAnthropicSseAndCaptureBody`,
`MockLlmServerTest.shouldStreamOllamaNdjsonAndCaptureBody`.
Gate: `mvn -pl org.sterl.llmpeon.core test` → 488 grün (+2).

**Inc 2 — E2E-Matrix: Routing + Config on the Wire (3 Provider × 2 Szenarien)** ✅ DONE
NEU `PerAgentConnectionE2ETest`: `inheritsBaseUrl_landsAtBaseStub_withAgentModelAndThink` (×3),
`agentOwnUrl_landsAtAgentStub_withConfigOnTheWire` (×3).
LÖSCHEN: `ToolLoopRequestConnectionTest`, `AiServicePerAgentModelTest`.
Gates: `mvn -pl org.sterl.llmpeon.core test` → `mvn -o -pl org.sterl.llmpeon,releng/llmpeon-target -am package`
(Core-Jar-Änderung fließt ins Plugin ein; Compile-Sanity) → `eclipseBuildProject(llmpeon-core)` (+
`llmpeon`/`llmpeon.test` bei Workspace-Sync).

**Inc 3 — Config-Edit E2E (kein Stale-Cache)** ✅ DONE
`PerAgentConnectionE2ETest.configEdit_routesToNewUrl_noStaleConnection` (3 Stubs, echter
`LlmConfig.withModelConfig` → `updateConfig` → `planAgentConfig`-Pfad; Stale-Nachweis via
`agentStub.reset()` nach dem Edit — keine Main-Code-Änderung nötig).
Gates: Core-Tests (491 grün) → Plugin-Compile (SUCCESS) → `eclipseBuildProject(llmpeon-core)` (clean).

## 8. Regeln & Constraints

- Log OR throw; keine neuen externen Deps; Core-Tests JUnit 5/AssertJ.
- Kein Plugin-/UI-/Docs-/Homepage-Code in diesem Zyklus (Docs sind PO+User-eigene).
- Commit pro grünem Increment: `inc-N: <summary>` + `Assisted-by: Peon AI (<Model>)`-Trailer,
  nur wenn auf dediziertem Branch (sonst erst fragen).
- `MockLlmServer` bleibt im **main**-Scope des Core (Bundling für Plugin-Test-Modul) — additive
  Änderung nur, keine API-Brüche.
- Kein git-Auto-Commit ohne Branch; finaler Merge = User-Entscheidung.

## 9. Abweichungen / Annahmen (PO-kann-überspielen)

1. **`AiServicePerAgentThinkTest` bleibt** (nicht gelöscht): deckt OPEN_AI_OFFICIAL-Think-Mapping
   (Responses-API-Wire, außerhalb der 3-Provider-Matrix) + `customAgentConfig`-Heuristik ab —
   NICHT redundant gegenüber dem neuen E2E. User-Formulierung „redundante Tests entfernen"
   interpretiert als: nur Tests ersetzen, die der E2E nachweislich subsumiert.
2. **S3 nur OpenAI**: Cache-Clear-Mechanik ist provider-unabhängig (`ConnectionIdentity` enthält
   URL/Key/Body); ein Provider genügt als Beweis, die Matrix (S1/S2) deckt die Provider-Diversität.
3. **`MockLlmServer` erweitern statt neue Stub-Klasse** (D1) — Lifecycle/Capture-Deduplizierung.
4. **Szenario-B-Modell für OpenAI = "claude-mock"**: nötig, um den User-wins-Merge auf dem Wire
   nachweisen zu können (nur claude*-Modelle erzeugen einen Provider-Eintrag `cache_control`).
   Mock-Server → Provider-fremder Name harmlos.
5. **`ToolLoopRequestConnectionTest` komplett löschen** (nicht nur der offene Teil): dessen einziger
   Test ist ein strikter Subset von S2/OpenAI (gleiche Vehicle, schwächere Assertions).

## 10. Open Questions

Keine — alle Designfragen durch Code-Verifizierung aufgelöst (langchain4j 1.18.1-Source unter
`/langchain4j-aggregator` direkt gelesen; searchAgent ausfallen lassen nach CancellationException).
Punkte aus §9 sind Annahmen mit Begründung, keine Blocker.
