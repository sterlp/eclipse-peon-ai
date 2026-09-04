# Prompt Caching

**Status:** ✅ done (2c, 2026-09-01, Branch `new-config`) — per-agent JSON extra body:
Mechanik 2a, UI 2b, 2c = Cache-Hardcode-Clean-Break + GPT/Claude-UI-Beispiele + Usage-Abgleich
(Token-Header `↑ ↓ ⇄`) + Custom-Agent-Frontmatter. Bestands-Hardcode entfernt — Caching lebt
nur noch im extra body (PO-Entscheidung: Clean Break, kein stiller Default, keine Migration).

## Purpose

Provider-Prompt-Caching (KV-Cache: stabiler Präfix = System-Prompt/Tools wird gecacht) wird
**pro Agent vom User konfigurierbar** — statt Provider-spezifischem Hardcode im Code.
Mechanik: ein **JSON extra body** je Agent, der in den LLM-Request-Body gemerged wird —
**alle** Caching-Flags laufen so (Bestands-Hardcode wird zu UI-Beispielen, R1). Gilt für jeden
Agenten, bei dem ein Modell eingestellt werden kann (Built-in + Custom). Der Input erscheint im
UI **nur**, wenn der Provider extra-body-Parameter unterstützt (Capability-Boolean in
`AiProvider`, R2).

## SOLL (2026-08-21, User-Entscheidung)

- **R1 ✅ Per-agent "extra body" (JSON) (Mechanik 2a, UI 2b, Clean-Break 2c 2026-09-01):** In den Advanced Settings kann **jeder Agent — auch
  Custom Agents** — ein JSON-Snippet definieren, das in den Request-Body gemerged wird.
  Caching ist ein Anwendungsfall dieser Mechanik (GPT-/Claude-Snippets), kein Built-in-Flag —
  das Bestands-Hardcode (Anthropic-Flags, OpenAI-Claude `cache_control`) wird entfernt und lebt
  nur noch als UI-Beispiel (entschieden 2026-08-21). **Entfernung erst in Schritt 2**
  zusammen mit der UI (2026-08-28, Option B: kein Caching-Regression-Fenster; der Provider-
  Refactor bleibt verhaltenstreu — provider.md R4).
- **R1a ✅ Body per Agent, möglichst per Request (2026-08-28, User):** der extra body wird
  **per Request** gesetzt (langchain4j `customParameters`), wo der Provider es unterstützt —
  dann geht er NICHT in die Verbindungs-Identität. Wo er nur Build-time geht, ist er Teil des
  Verbindungs-Hashes (→ Connection-Cache in [advanced-configuration.md](advanced-configuration.md)).
  Compact-Agent trägt KEINEN Body → belegt keinen KV-Cache-Slot (R6). Body darf je
  Modell/Agent variieren. Provider-Unterstützung **verifiziert** (→ provider.md R3): per-request
  nur OpenAI-Familie, Anthropic Build-time (Body dann in der Verbindungs-Identität,
  [ADR-0034](adr/0034-connection-cache-by-identity.md)), Rest gar nicht. **Core-Mechanik ✅
  (2a, 2026-08-28):** `ExtraBody.parse` (Reserved-Key-Strip, invalid → warn+ignore) +
  `ExtraBodyMode` (PER_REQUEST/BUILD_TIME/NONE) + Merge (User-Body gewinnt) + Connection-Cache;
  **UI ✅ (2b, 2026-08-30)** (JSON-Widget je Agent, Gate per `supportsExtraBody()`) —
  Beispiele + Hardcode-Entfernung ✅ (2c, 2026-09-01).
- **R2 ✅ Provider-Fähigkeits-Gate (Mechanik 2a, UI-Gate 2b, 2026-08-30):** der JSON-Input erscheint im UI **nur**, wenn der aktive
  Provider extra-body-Parameter unterstützt — nur dort, wo es implementiert ist, wird es
  geboten. Mechanik (Boolean, Merge, Provider-Klassen): [provider.md](provider.md) P2–P3.
- **R3 ✅ Config-UI mit Beispielen (2c 2026-09-01, `ExtraBodyExamples` im core; inc-26 kompakt + llama.cpp):**
  Unter dem JSON-Input werden die **Beispiele** in einer kompakten Zeile angezeigt (Buttons mit
  Tooltips, Gate `supportsExtraBody()`, ersetzen + Status-Label; kein stiller Setz). Beispiele:
  GPT (`prompt_cache_key`), Claude (`cache_control`), **llama.cpp** (`chat_template_kwargs.enable_thinking`).
  **Herkunft der Snippet-Inhalte (2c/D1):** nicht frei erfunden, sondern rekonstruiert aus den
  Legacy-TODOs (`OpenAiProvider.java:63`, `ProviderRequestSupport.java:103`) plus dem beim
  Clean Break entfernten Hardcode, gegengeprüft am Wire-Nachweis aus Night-Cycle A
  ([test-setup.md](test-setup.md) — Mock-Wire-Formate). Wer ein Snippet ändert, ändert damit
  eine belegte Provider-Semantik, keinen Beispieltext.

- **R4 ✅ Geltungsbereich (2c 2026-09-01):** alle Agenten mit Modell-Slot (base/plan/search/compact/PO +
  Custom). Custom Agents tragen die Config via **AGENT.md-Frontmatter** (`url`/`api_key`/
  `extra_body`) → gleicher `AgentModelConfig`-Record, `agentBuilder`-Auflösung wie die 4
  Core-Agents (0 Plugin-Änderungen, `PromptYmlParser` im core).
- **R5 ✅ Abgleich (2c 2026-09-01, `TokenStats` + Token-Header `↑ ↓ ⇄`):** Cache-Tokens aus der
  Usage-Antwort (`OpenAI.inputTokensDetails.cachedTokens`, `Anthropic.cacheRead`/
  `cacheCreation`) auslesen und im UI melden — andocken an [token-usage](token-usage.md);
  Cache-WRITE nur im Tooltip.
- **R8 ✅ GPT-Default-Cache-Key (User, 2026-09-01, gebaut inc-23 2026-09-01):** die OpenAI-Familie injiziert bei
  **`gpt-5*`-Modellen** (GPT-5.6+ -Famlien, User-Entscheidung) `prompt_cache_key` als
  **Provider-Entry** mit Default **`peon-ai-<agent-id>`** (Agent-ID seit dem 2a-Umbau in der
  Config verfügbar → stabiler, eigener Cache-Key je Agent). Merge-Semantik unverändert (2a):
  **nicht-leerer** User-Wert im extra body gewinnt; **leerer Body `{}` und
  `prompt_cache_key: ""` zählen als unset → Default bleibt** (leerer Body überschreibt den
  Default NICHT). Anthropic: kein Default (Beispiel-Belegung via R3, Direkt-API profitiert
  nicht). Nicht im Scope: Breakpoints (`prompt_cache_breakpoint`/`prompt_cache_options`) —
  der User kann sie bei Bedarf selbst im JSON extra body setzen. Referenz (Azure, GPT-5.6+):
  https://learn.microsoft.com/en-us/azure/foundry/openai/how-to/prompt-caching
  GIVEN ein OpenAI-Agent (z. B. `plan`) mit einem `gpt-5*`-Modell, ohne extra body
  WHEN der Agent einen Call macht
  THEN der Request trägt `prompt_cache_key = "peon-ai-plan"`
  GIVEN der User hat `prompt_cache_key: "custom"` im extra body gesetzt
  WHEN der Agent einen Call macht
  THEN der Request trägt `prompt_cache_key = "custom"` (User gewinnt)
  GIVEN der extra body ist `{}` (leer) oder `prompt_cache_key: ""`
  WHEN der Agent einen Call macht
  THEN der Request trägt weiterhin den Default `prompt_cache_key`
  GIVEN ein OpenAI-Agent mit einem Modell OHNE `gpt-5*`-Prefix (z. B. `llama` via Gateway)
  WHEN der Agent einen Call macht
  THEN der Request trägt KEIN `prompt_cache_key` (kein fremder Parameter an fremden Endpoints)
- **R6 ✅ Lokal-Provider / "no cache" (llama.cpp):** begrenzter Cache-Slots — ein Agent, der
  **nicht** cachen soll (z. B. Compact-Agent mit langem, statischem Kontext), bekommt einfach
  **kein Caching-Snippet** konfiguriert → belegt keinen Slot. (Absence = no cache.)
- ~~R7 Refactoring: `AiProvider` zerlegen~~ — eigene Doku: [provider.md](provider.md) (P1–P4:
  eigene Komponente, je Provider eine Klasse, Extra-Body je Provider-Klasse, verhaltenstreu).

## Ist (gebaut 2026-08-21, **✅ durch SOLL ersetzt in 2c 2026-09-01**)

Der frühere Ist-Hardcode ist **entfernt** (Clean-Break): Caching kommt fortan ausschließlich aus
dem per-agent extra body (R1/R1a). Was früher da war (historisch):

- `ANTHROPIC`: `cacheSystemMessages(true)` + `cacheTools(true)` beim Build (`AiProvider.buildModel`).
- `OPEN_AI` (Claude über OpenAI-kompatiblen Endpoint): `cache_control: ephemeral` als
  Custom-Parameter, wenn das Modell mit `claude` startet.
- Code-TODOs: per-Agent-`prompt_cache_key` (`OPEN_AI` GPT + `openAiOfficialParameters`) —
  im neuen Design legt der User den Key selbst im JSON extra body fest.

**Konsequenz (PO-Entscheidung 2026-08-30, Clean Break):** kein Cache, solange der User nichts
konfiguriert — keine Migration, kein stiller Default. **Claude-Direkt-API verliert natives
Caching** (`cache_control` gehört in die System-/Tool-Blöcke, nicht in den Top-Level-Body) —
bewusst akzeptiert + in der Homepage-Doku vermerkt.

## Open Questions (alle ✅ geklärt, Stand 2c)

1. ~~Hardcode-Flatten~~ — entschieden 2026-08-21: **alles** über extra body, Hardcode wird zu
   UI-Beispielen (R1); entfernt in 2c (Clean Break).
2. ~~Provider-Unterstützung~~ — ✅ verifiziert beim Umsetzen (2a/2c, → provider.md R3): per-request
   nur OpenAI-Familie, Anthropic Build-time, Rest NONE (`ExtraBodyMode`).
3. ~~Merge-Semantik~~ — ✅ (2a): flacher Merge, **User-Body gewinnt**, Reserved-Keys
   `model`/`messages`/`tools` werden gestrippt (Schutz, nicht deep merge).
4. ~~Abgleich-Kanal~~ — ✅ (PO 2026-08-30, gebaut 2c): **permanent im Token-Header** (`↑ ↓ ⇄`),
   Cache-WRITE nur im Tooltip.

## BDD (Entwürfe)

```
GIVEN ein Agent hat ein extra-body-JSON konfiguriert (Claude-Cache-Beispiel)
WHEN der Agent einen Call macht
THEN das JSON-Snippet ist in den Request-Body gemerged
AND der Request enthält die Cache-Marker

GIVEN ein Lokal-Provider (llama.cpp) mit begrenzten Cache-Slots
AND der Compact-Agent hat KEIN Caching-Snippet konfiguriert
WHEN der Compact-Agent einen Call macht
THEN keine Cache-Flags im Request — kein Slot belegt
AND andere Agenten mit Caching-Snippet bekommen den Slot

GIVEN der Cache greift (Provider meldet cache-reads)
WHEN der Abgleich die Usage-Antwort prüft
THEN die Cache-Tokens werden im UI gemeldet

GIVEN der aktive Provider unterstützt extra-body-Parameter (AiProvider-Boolean)
WHEN der User die Advanced Settings öffnet
THEN der JSON-Input mit GPT/Claude-Beispielen ist sichtbar

GIVEN der aktive Provider unterstützt keine extra-body-Parameter
WHEN der User die Advanced Settings öffnet
THEN es gibt KEINEN JSON-Input
```

## Relationship

- [Advanced Configuration](advanced-configuration.md) — agent-spezifischer Config-Umbau (SOLL)
- [Session Token Usage](token-usage.md) — Reporting-Anker für R5 (Token-Header `↑ ↓ ⇄`)
- [Per-Agent Think Support](per-agent-think.md) — Muster: per-Agent-Request-Werte über
  `newRequestParameters`
- [ADR-0031](adr/0031-static-context-env-plus-memory.md) — stabiler System-Prompt-Präfix;
  Re-Bake bei Config-Change bricht den Provider-Cache einmal
