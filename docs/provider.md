# Provider (AiProvider)

**Status:** ✅ done (2026-08-28) — Slice 1 des Zwei-Slice-Plans aus
[ADR-0033](adr/0033-ox-alpha-provider-slices.md): dieses Refactoring zuerst (mechanisch,
verhaltenstreu), danach [Free Provider „Ox Alpha"](free-provider-ox-alpha.md) als erste neue
Provider-Klasse. Caching als Feature lebt in [caching.md](caching.md).

## Goal

Die Provider-Domäne (Modell-Build, per-Request-Parameter, Modell-Listen, Extra-Body-Fähigkeit)
ist heute ein Enum (`AiProvider`, 10 Konstanten à 3 Methoden + statische Helpers). Sie wird zu
einer eigenen Komponente mit **je einer Klasse pro Provider** — damit neue Provider (zuerst Ox
Alpha) ohne Enum-Wachstum dazukommen und Provider-Quirks gekapselt sind. Repo-Layout: Feature =
Package in `org.sterl.llmpeon.core`, kein neues Bundle.

## Business Rules

### R1 — Eigene Komponente ✅

Neues Package `org.sterl.llmpeon.provider` in core; Tests in core, UI-Anbindung im Plugin bleibt
unverändert (Aufrufstellen nutzen die Factory).

* `providerPackageResolvesViaFactory` — GIVEN alle 9 bekannten Provider-Namen WHEN über die
  Factory aufgelöst THEN liefert jede einen Provider mit `buildModel`/`newRequestParameters`/
  `listAiModels`.

### R2 — Eine Klasse pro Provider ✅

Gemeinsames Interface (`buildModel`, `newRequestParameters`, `listAiModels`,
`supportsExtraBody()`) + eine Klasse pro Provider (`OllamaProvider`, `OpenAiProvider`,
`AnthropicProvider`, …). `AiProvider` bleibt nur Name-Registry/`parse`; Auflösung über eine
Factory. Die statischen Helpers (`applyBase`, `effortFor`, `anthropicThinkingType`,
`openAiOfficialParameters`, `MODEL_TIMEOUT`) wandeln in gemeinsame/freundliche Klassen um.

* `parseKeepsLegacyNamesStable` — GIVEN bestehende Preference-Werte (Enum-Namen inkl. Fallback
  OLLAMA bei Unbekanntem) WHEN geparst THEN dieselbe Provider-Auflösung wie heute.

### R3 — Extra-Body-Fähigkeit pro Provider ✅

`supportsExtraBody()` am Interface; die Merge-Logik selbst ist Feature von [caching.md](caching.md)
(dort R1 + UI-Gate) und wird hier nur als Fähigkeits-Boolean je Klasse geführt — OpenAI-Familie
bereits nachweisbar `true`.

* `supportsExtraBodyPerClass` — GIVEN die 9 Provider-Klassen WHEN `supportsExtraBody()` THEN
  OpenAI-Familie `true`, Anbieter ohne LC4j-Support `false` (Wertetabelle im Test fixiert).

### R4 — Verhaltenstreu (kein Request-Byte-Stream-Change) ✅

Reines Refactoring: bestehende Tests bleiben grün, **Requests 100% unverändert**. Die
Cache-Hardcodes wandern **unverändert** in die Provider-Klassen und fallen erst in Schritt 2
zusammen mit der JSON-Body-UI ([caching.md](caching.md) R1) — entschieden 2026-08-28, Option B
(kein Caching-Regression-Fenster, Review bleibt trivial). Kein Umbau an `LlmConfig`/`AgentConfig`
(Provider erhalten sie unverändert).

* `requestParametersUnchanged` — GIVEN die Golden-Assertions in `AiProviderRequestParametersTest`
  WHEN vor/nach Refactoring THEN identische Parameter je Provider.
* `modelListingUnchanged` — GIVEN `ModelListingTest`/`ConfiguredModelTest` WHEN nach Refactoring
  THEN grün ohne Anpassung (nur Import-/Fabrik-Pfade).
* `coreChangeVisibleInPlugin` — GIVEN Änderung in core WHEN Plugin/Tests bauen THEN erst nach
  `mvn clean verify` sichtbar (Shell-Build, lt. Root-AGENTS.md).

### R5 — Think-Support-Fähigkeit pro Provider ✅ (2026-08-28, User)

Der Provider macht sichtbar, in welcher **Form** er Think unterstützt: `BOOLEAN` (on/off),
`VALUES(List<String>)` (geschlossene Werteliste, z. B. Reasoning-Effort), `FREE_STRING`,
`NONE` (nicht unterstützt) oder `UNKNOWN` (unbekannt — Default). UI und Validierung leiten
davon ab, welches Think-Input gerendert wird (Checkbox / Dropdown / Freitext / ausgeblendet).

* `thinkSupportPerClass` — GIVEN die 9 Provider-Klassen WHEN `thinkSupport()` THEN die
  Wertetabelle ist im Test fixiert (analog R3).

## Ist

- `org.sterl.llmpeon.ai.AiProvider` (Enum): OLLAMA, OPEN_AI, OPEN_AI_OFFICIAL, LM_STUDIO,
  GOOGLE_GEMINI, MISTRAL, ANTHROPIC, GITHUB_MODELS, GITHUB_COPILOT — je `buildModel` /
  `newRequestParameters` / `listAiModels`; statics: `applyBase`, `effortFor`,
  `anthropicThinkingType`, `openAiOfficialParameters`, `MODEL_TIMEOUT`, `parse`.
- Bestehende Absicherung: `AiProviderRequestParametersTest`, `ModelListingTest`,
  `ConfiguredModelTest`, `LlmConfigTest` (core).
- Extra-body-Fähigkeit heute ad hoc: `customParameters` bei OPEN_AI (Claude `cache_control`),
  LM_STUDIO (`reasoning`); Anthropic-Flags build-time.

## Offene Punkte

- ~~Interface-/Package-Name endgültig fixieren~~ **fixiert (2026-08-28):** Package
  `org.sterl.llmpeon.provider`, Interface `LlmProvider`, Factory `LlmProviders` (+ `all()`),
  Helper `ProviderRequestSupport`, Think-Formen als sealed `ThinkSupport`.
- ~~Provider-Unterstützung Extra-Body pro Provider verifizieren~~ **verifiziert 2026-08-28
  (langchain4j 1.18.1, Da Mek):** per-request `customParameters` **nur OpenAiStreamingChatModel**
  (→ OPEN_AI, LM_STUDIO, GITHUB_COPILOT); **Anthropic nur Build-time** (`customParameters` am
  Builder — Body gehört zur Verbindungs-Identität); **Gemini/Mistral/Ollama/OpenAiOfficial-
  Responses: keine Möglichkeit** (kein Feld, kein Hook) → R3-Wertetabelle: OpenAI-Familie +
  Anthropic `true` (Anthropic: Build-time), Rest `false`. **Headers:** kein Provider per-request
  (nur build-time `customHeaders`, `Supplier`-Variante als einziger dynamischer Hebel).
- ~~Interface-Evolution Richtung `buildRequest(modelConfig)`~~ **✅ umgesetzt (2a, 2026-08-28)**
  — anders als skizziert, aber SOLL-erfüllend: `LlmProvider.extraBodyMode()`
  (PER_REQUEST/BUILD_TIME/NONE; `supportsExtraBody()` daraus abgeleitet), `ExtraBody`-Helper
  (Parse + Reserved-Key-Strip `model`/`messages`/`tools`), per-request Merge in
  `newRequestParameters` (User-Body gewinnt) und Build-time via `LlmConfig.extraBody` bei
  Anthropic. `AgentConfig.extraBody` trägt den Body.

## Relationship

- [Free Provider „Ox Alpha"](free-provider-ox-alpha.md) — Slice 2 baut auf diesem Interface auf;
  Reihenfolge fixiert in [ADR-0033](adr/0033-ox-alpha-provider-slices.md)
- [Prompt Caching](caching.md) — Extra-Body-Feature, das die Provider-Domäne erweitert
- [Advanced Configuration](advanced-configuration.md) — per-agent-Config andockt hier an
- [Per-Agent Think Support](per-agent-think.md) — per-request think läuft durch
  `newRequestParameters` (bleibt im Interface)
