# ADR-0034: Connection-Cache nach Verbindungs-Identität

**Status:** Accepted (2026-08-28)

**Context:** Per-agent-Model-Config (URL, Key, extra JSON body) —
[advanced-configuration.md](../advanced-configuration.md). Heute: ein geteilter
`StreamingChatModel`, per-request `modelName`-Override (Issue #82). Mit per-agent-URL/Key/Body
werden mehrere echte Verbindungen nötig; `StreamingChatModel` pro Request neu zu bauen ist zu
teuer, und der Provider-KV-Cache lebt nur mit stabilen Instanzen.

**Decision:**
- **Verbindungs-Identität = Provider + URL + Key** (+ extra body nur bei Providern, bei denen
  er ausschließlich Build-time setzbar ist — heute: Anthropic; Belege: provider.md „verifiziert
  2026-08-28").
- Model-Instanzen (Verbindungen) werden **pro Identität gecacht**; gleiche Identität →
  dieselbe Instanz. Die Default-Verbindung wird „geerbt", solange ein Agent keine eigene
  URL/Key trägt.
- **Request-Ebene (nie im Hash):** Modell-Name, Think, Temperature, extra body — letzterer
  per-request via `customParameters`, wo der Provider es kann (heute nur OpenAI-Familie).
- **Modell-Listen** werden analog einmalig pro Identität geholt und gecacht; Fetch-Fehler →
  konfiguriertes Modell bleibt (heutiger Fallback); **manueller Refresh** via Button im
  Dropdown (Fehler → alter Cache bleibt).

**Consequences:**
- Modell-Dropdown + Think können aus der Chat-UI — Config-Seite wird Single Source of Truth
  (Simplification, 2026-08-28 User-Entscheidung).
- Base-URL-Wechsel → neue Identität für Agenten ohne eigene URL → neuer (einmaliger)
  List-Fetch; konfiguriertes Modell bleibt gesetzt (kein Auto-Switch — Abweichung von B2 in
  [model-loading.md](../model-loading.md)).
- langchain4j 1.18.1: **keine** per-request Headers bei keinem Provider; Gemini/Mistral/Ollama/
  OpenAiOfficial-Responses haben **keinen** Extra-Body-Escape-Hook →
  `supportsExtraBody() = false` (UI-Gate, caching.md R2).
