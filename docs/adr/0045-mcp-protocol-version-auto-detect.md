# ADR-0045 — MCP Protocol Version: leer = Auto-Detect

**Status:** Accepted (2026-09-10)

**Context:**
lib-update auf langchain4j 1.20.0 (McpService → Streamable-HTTP, ADR-0044-Nachtrag): duckduckgo-mcp
(`uvx`, stdio) verbindet nicht mehr — JSON-RPC `-32022 UNSUPPORTED_PROTOCOL_VERSION`.

Recherche (Da Sniffa, aus Compact-500-Fehlertext gerettet) + `DefaultMcpClient.java:262–320`
(1.20.0, verifiziert gegen lokalen Checkout `/langchain4j-aggregator`):

- leer = Auto-Detect: modern-Probe `server/discover` (`2026-07-28`) zuerst; bei jedem Scheitern
  Legacy-Initialize (`2025-11-25`) auf **derselben Verbindung**.
- `2026-07-28` = force modern; `2025-11-25` / `2024-11-05` = force legacy.
- **jede andere Version** → `autoDetect(version, version)` — Detect-Flow mit diesem String.

duckduckgo-mcp 0.7.0 steht auf Python mcp SDK 2.x = **Dual-Era-Server**: der erste Request
entscheidet die Ära. Unser Probe-Envelope lockt MODERN, die Probe scheitert (`server/discover`
fehlt), der Legacy-Fallback auf derselben Verbindung bekommt `-32022`. Also nicht „Client zu neu".
Unser alter Default `2025-06-18` war „andere Version" und damit exakt der Auslöser; der User-Test
mit `2024-11-05` (force-legacy, wäre korrekt) kam nie an, weil MCP-Config-Änderungen nicht live
angewandt wurden (AIChatView-Gate → R-MCP1, docs/mcp.md).

**Decision:**
1. Default = **leer** = Auto-Detect (langchain4j-Verhalten). Kein stiller Default:
   `McpServerConfig.DEFAULT_PROTOCOL_VERSION` entfällt, der Compact-Constructor füllt leere Werte
   nicht mehr.
2. Keine Migration/Aliase für gespeicherte `2025-06-18` (Clean Break, AGENTS.md).
3. UI: editierbare Combo (Auto / 2025-11-25 / 2026-07-28 + Freitext), Hint nennt die Semantik
   (R-MCP3).

**Consequences:**
- Dual-Era-Server mit fehlendem `server/discover` (duckduckgo) scheitern unter Auto-Detect
  weiterhin — User setzt dann `2025-11-25` manuell (Legacy-Pfad geht gegen mcp 2.x immer).
  Dokumentiert im Dialog-Hint, nicht im Code gefixt.
- Manuelle Freitext-Werte außerhalb der drei bekannten laufen bewusst in den Detect-Flow —
  warnt der Hint.
- Gewinnt langchain4j später Server-seitige Erkenntnis (Probe-Erkennung), bleibt unser Contract
  unverändert: leer = Bibliothek entscheidet.