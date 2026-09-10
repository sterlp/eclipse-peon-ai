# MCP Servers — docs/mcp.md

Zweck: Anbindung externer MCP-Server (STDIO-Prozess oder Streamable-HTTP): Konfiguration
(`McpServerConfig`, Preferences), Verbindung (`McpService`), Protocol-Version-Semantik,
Live-Apply bei Config-Änderung.
Code: core `org.sterl.llmpeon.mcp` (`McpService`, `McpServerConfig`), Plugin
`parts.config.Mcp*` (UI/Preferences/Live-Apply).

**Status: ❌ specified (2026-09-10)** — R-MCP1–R-MCP3 designed während des Smoke-Tests des
lib-update-Zyklus (langchain4j 1.20.0), nicht gebaut. Auslöser: duckduckgo-mcp (`uvx`, stdio)
stirbt mit JSON-RPC `-32022 UNSUPPORTED_PROTOCOL_VERSION` — Analyse in
[ADR-0045](adr/0045-mcp-protocol-version-auto-detect.md).

## R-MCP1 — MCP-Config wird live angewandt ❌

Jede Änderung der MCP-Konfiguration (Server-Liste, protocolVersion, enabled) wirkt ohne
Eclipse-Neustart und ohne manuellen MCP-Toggle — wie die LLM-Config auch.

**WEIL (Bug, 2026-09-10):** `AIChatView.applyConfig():376` bricht bei unverändertem `LlmConfig`
früh ab; `applyMcpConfig():382` läuft dann nie. Reine MCP-Änderungen (das MCP-Preference-Page-Save)
wurden dadurch nie verbunden — der User testete protocolVersionen gegen eine tote Verbindung.

Der Compare lebt künftig in `McpConnectionService` (letzte angewandte Server-Liste + enabled),
nicht im LlmConfig-Gate.

- **BDD R-MCP1a** GIVEN MCP verbunden mit Config C1 WHEN die gespeicherte MCP-Config ändert sich
  zu C2 (LlmConfig unverändert) THEN die Clients werden mit C2 neu verbunden.
  Test: `McpConnectionServiceTest.givenChangedMcpConfig_whenApplied_thenReconnects`
- **BDD R-MCP1b** GIVEN MCP-Config unverändert WHEN applyConfig läuft THEN kein Reconnect
  (kein Spawn von STDIO-Prozessen pro Preference-Event).
  Test: `McpConnectionServiceTest.givenUnchangedMcpConfig_whenApplied_thenNoReconnect`
- **BDD R-MCP1c** GIVEN toggle() hat den Zustand angewandt und lastApplied aktualisiert WHEN das
  Preference-Event des Toggles applyConfig triggert THEN kein erneutes connect/disconnect.
  Test: `McpConnectionServiceTest.givenToggledState_whenApplied_thenNoReconnect` (Da-Dok-Finding
  C1: einzige konsistenzkritische Stelle ohne roten Test; zugleich Mutations-Nachweis).

## R-MCP2 — Protocol-Version-Semantik ❌

Der Wert geht unverändert an `DefaultMcpClient.Builder.protocolVersion(...)` (`McpService.java:81`);
langchain4j 1.20 interpretiert (`DefaultMcpClient.java:262–275`):

- **leer = Auto-Detect** (langchain4j-Default): modern-Probe `server/discover` (`2026-07-28`) zuerst,
  Legacy-Initialize (`2025-11-25`) als Fallback auf derselben Verbindung.
- `2026-07-28` = force modern; `2025-11-25`, `2024-11-05` = force legacy (kein Probe).
- **jede andere Version** löst den Detect-Flow mit dieser Version aus — gegen Dual-Era-Server
  (Python mcp SDK 2.x) kippt das in `-32022` (Era-Lock, ADR-0045).

**Decision (User, 2026-09-10): Default = leer (Auto-Detect), kein stiller Default mehr** —
`DEFAULT_PROTOCOL_VERSION` (`2025-06-18`) entfällt (Clean Break).

- **BDD R-MCP2a** GIVEN protocolVersion leer WHEN `McpServerConfig` gebaut THEN der Wert bleibt
  leer (kein Default-Ersatz) → Client läuft in Auto-Detect.
  Test: `McpServerConfigTest.givenEmptyProtocolVersion_whenConstructed_thenStaysEmpty`

⚠️ Clean Break (AGENTS.md „Clean break over migration"): gespeicherte `2025-06-18`-Werte werden
**nicht** migriert oder aliasiert — sie bleiben wirksam (Detect-Flow), bis der User das Feld leert
oder setzt. Kein Sonderfall für den alten Default.

## R-MCP3 — Dialog: editierbare Combo ❌

Das Protocol-Version-Textfeld im MCP-Server-Dialog wird eine **editierbare Combo** (SWT Combo
ohne `READ_ONLY`):

- Dropdown-Vorschläge: `Auto`, `2025-11-25`, `2026-07-28`; Freitext erlaubt (z. B. `2024-11-05`).
- `Auto` speichert leer (leer = Auto-Detect).
- Hinweistext: „leer = Auto-Detect; `2025-11-25`/`2024-11-05` = Legacy, `2026-07-28` = modern;
  andere Werte lösen die Versionserkennung aus (kann bei Dual-Era-Servern -32022 liefern)."
- Server-Tabelle der Übersicht zeigt `Auto` für leere protocolVersion.
- Verifikation manuell (kein SWT-Test-Harness; Präzedenz R-UI1, user-question-tool-design.md).