# Prompt Caching

**Status:** 🚧 in design (2026-08-21) — Designprinzip fest: **per-agent JSON extra body**;
Implementierung Backlog (nach der Issue-Runde), zusammen mit dem agent-spezifischen Config-Umbau
(→ [Advanced Configuration](advanced-configuration.md)).

## Purpose

Provider-Prompt-Caching (KV-Cache: stabiler Präfix = System-Prompt/Tools wird gecacht) wird
**pro Agent vom User konfigurierbar** — statt Provider-spezifischem Hardcode im Code.
Mechanik: ein **JSON extra body** je Agent, der in den LLM-Request-Body gemerged wird —
**alle** Caching-Flags laufen so (Bestands-Hardcode wird zu UI-Beispielen, R1). Gilt für jeden
Agenten, bei dem ein Modell eingestellt werden kann (Built-in + Custom). Der Input erscheint im
UI **nur**, wenn der Provider extra-body-Parameter unterstützt (Capability-Boolean in
`AiProvider`, R2).

## SOLL (2026-08-21, User-Entscheidung)

- **R1 ❌ Per-agent "extra body" (JSON):** In den Advanced Settings kann **jeder Agent — auch
  Custom Agents** — ein JSON-Snippet definieren, das in den Request-Body gemerged wird.
  Caching ist ein Anwendungsfall dieser Mechanik (GPT-/Claude-Snippets), kein Built-in-Flag —
  das Bestands-Hardcode (Anthropic-Flags, OpenAI-Claude `cache_control`) wird entfernt und lebt
  nur noch als UI-Beispiel (entschieden 2026-08-21). **Entfernung erst in Schritt 2**
  zusammen mit der UI (2026-08-28, Option B: kein Caching-Regression-Fenster; der Provider-
  Refactor bleibt verhaltenstreu — provider.md R4).
- **R1a ❌ Body per Agent, möglichst per Request (2026-08-28, User):** der extra body wird
  **per Request** gesetzt (langchain4j `customParameters`), wo der Provider es unterstützt —
  dann geht er NICHT in die Verbindungs-Identität. Wo er nur Build-time geht, ist er Teil des
  Verbindungs-Hashes (→ Connection-Cache in [advanced-configuration.md](advanced-configuration.md)).
  Compact-Agent trägt KEINEN Body → belegt keinen KV-Cache-Slot (R6). Body darf je
  Modell/Agent variieren. Provider-Unterstützung **verifiziert** (→ provider.md R3): per-request
  nur OpenAI-Familie, Anthropic Build-time (Body dann in der Verbindungs-Identität,
  [ADR-0034](adr/0034-connection-cache-by-identity.md)), Rest gar nicht.
- **R2 ❌ Provider-Fähigkeits-Gate:** der JSON-Input erscheint im UI **nur**, wenn der aktive
  Provider extra-body-Parameter unterstützt — nur dort, wo es implementiert ist, wird es
  geboten. Mechanik (Boolean, Merge, Provider-Klassen): [provider.md](provider.md) P2–P3.
- **R3 ❌ Config-UI mit Beispielen:** Unter dem JSON-Input werden die **GPT- und Claude-Beispiele
  fürs Caching** angezeigt (copybar, ohne dass sie gesetzt sind).
- **R4 ❌ Geltungsbereich:** alle Agenten mit Modell-Slot (base/plan/search/compact/PO + Custom).
- **R5 ❌ Abgleich:** verifizieren, dass der Cache greift — Cache-Tokens aus der Usage-Antwort
  (`cache_read`/`cache_creation`) auslesen und im UI melden (an [token-usage](token-usage.md)
  andocken).
- **R6 ❌ Lokal-Provider / "no cache" (llama.cpp):** begrenzter Cache-Slots — ein Agent, der
  **nicht** cachen soll (z. B. Compact-Agent mit langem, statischem Kontext), bekommt einfach
  **kein Caching-Snippet** konfiguriert → belegt keinen Slot. (Absence = no cache.)
- ~~R7 Refactoring: `AiProvider` zerlegen~~ — eigene Doku: [provider.md](provider.md) (P1–P4:
  eigene Komponente, je Provider eine Klasse, Extra-Body je Provider-Klasse, verhaltenstreu).

## Ist (gebaut 2026-08-21, durch SOLL ersetzt)

- `ANTHROPIC`: `cacheSystemMessages(true)` + `cacheTools(true)` beim Build (`AiProvider.buildModel`).
- `OPEN_AI` (Claude über OpenAI-kompatiblen Endpoint): `cache_control: ephemeral` als
  Custom-Parameter, wenn das Modell mit `claude` startet.
- Code-TODOs: per-Agent-`prompt_cache_key` (`OPEN_AI` GPT + `openAiOfficialParameters`) —
  im neuen Design legt der User den Key selbst im JSON extra body fest.

## Open Questions

1. ~~Hardcode-Flatten~~ — entschieden 2026-08-21: **alles** über extra body, Hardcode wird zu
   UI-Beispielen (R1).
2. **Provider-Unterstützung:** welche langchain4j-Provider-Builder können extra-body-Parameter
   (`customParameters` o.ä.) in der aktuellen Version? — pro Provider beim Umsetzen verifizieren
   und in den R2-Boolean eintragen (OpenAI-Familie hat `customParameters` bereits im Ist-Code).
3. **Merge-Semantik:** deep merge mit Schutz für `model`/`messages` (Agent-Snippet darf diese
   Felder nicht überschreiben) oder offener Merge? — Empfehlung: Schutz + Doku.
4. **Abgleich-Kanal:** nur Debug-Log oder permanent im Token-Header (↑/↓ + cache-reads)?

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
- [Session Token Usage](token-usage.md) — Reporting-Anker für R4
- [Per-Agent Think Support](per-agent-think.md) — Muster: per-Agent-Request-Werte über
  `newRequestParameters`
- [ADR-0031](adr/0031-static-context-env-plus-memory.md) — stabiler System-Prompt-Präfix;
  Re-Bake bei Config-Change bricht den Provider-Cache einmal
