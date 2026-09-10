# Feature: MCP Servers (R-MCP1/R-MCP2/R-MCP3, docs/mcp.md) — Branch `story/lib-update-2026-09-09` (bereits ausgecheckt, NICHT wechseln)

## ⛔ STOP-AND-ASK (Da Mek, User-Regel 2026-09-08 — gilt immer)
Bei Compile-Fehlern ohne Lösung, nicht-grün-bekommenden Tests, IST-Widersprüchen zum Plan
oder Unklarheiten: AKTIV bei Jon (PO, askDev-Kanal) nachfragen — nie still workarounden,
nie das SOLL (docs/mcp.md) ändern.

## 1. Context
MCP-Anbindung existiert, aber: (a) MCP-Config-Änderungen werden nicht live angewandt —
`AIChatView.applyConfig():376` bricht bei unverändertem `LlmConfig` früh ab, `applyMcpConfig():382`
läuft nie → User testete protocolVersionen gegen tote Verbindung (Bug, 2026-09-10);
(b) stiller Default `DEFAULT_PROTOCOL_VERSION = "2025-06-18"` ist „andere Version" für
langchain4j 1.20 → Detect-Flow → `-32022` gegen Dual-Era-Server (duckduckgo-mcp, ADR-0045).
SOLL = docs/mcp.md (R-MCP1/R-MCP2/R-MCP3 mit BDDs und Testnamen) + ADR-0045. Docs/** gehören
dem PO — NICHT anfassen (nur commiten).

## 2. Design decisions
- **R-MCP2 (core, Clean Break):** `McpServerConfig.DEFAULT_PROTOCOL_VERSION` Konstante ENTFÄLLT.
  Compact-Constructor: leere/null protocolVersion → `""` (Normalisierung bleibt, Default-Füllung
  nicht — `StringUtil.hasNoValue`-Zweig setzt auf `""` statt Default). 3-arg Convenience-Ctor
  übergibt `""`. Keine Migration/Aliase für gespeicherte `2025-06-18` (AGENTS.md Clean Break) —
  Wert bleibt wirksam (Detect-Flow), bis User das Feld leert/setzt. Javadoc `@param protocolVersion`
  anpassen (leer = Auto-Detect).
- **R-MCP1 (plugin):** Der Compare (letzte angewandte Server-Liste + enabled) lebt in
  `McpConnectionService` als privates Value-Objekt, NICHT im LlmConfig-Gate.
  `AIChatView.applyConfig()` ruft `applyMcpConfig()` VOR dem Gate (vor Zeile 376) auf;
  das Gate bleibt ausschließlich für LlmConfig-Arbeit (`lastAppliedConfig`, `aiService.updateConfig`,
  agents/status). StatusLine-Updates in `applyMcpConfig()` bleiben unconditional (UI-Thread).
- **Testbarkeit ohne echte Verbindungen (narrow seam, kein Interface-Spreizung):**
  `McpConnectionServiceTest` (Plugin-Testmodul, JUnit 4, keine externen Assertions) subclassed
  `McpConnectionService` und überschreibt `connect()`/`disconnect()` als Recorder — `ToolService`
  wird als `null` übergeben (nie benutzt, dokumentiert im Test). Compare-Logik wird damit ohne
  echte Verbindungen/OSGi-Verbindungen getestet. Falls sich die Preferences (`McpPreferenceInitializer`,
  InstanceScope) im OSGi-Test nicht sauber bedienen lassen → STOP-AND-ASK, nicht Framework umbauen.

## 3. Architecture
```mermaid
sequenceDiagram
    participant P as Preferences (any change)
    participant V as AIChatView.applyConfig()
    participant S as McpConnectionService
    participant T as ToolService (Job)
    P->>V: prefListener → applyConfig()
    V->>V: LlmConfig-Gate (unverändert → return, NACH Mcp-Apply)
    V->>S: applyMcpConfig() → applyConfig()
    S->>S: desired = McpApplied(servers, enabled); equals(lastApplied)? → return
    S->>T: connect()/disconnect() im Hintergrund-Job (nur bei Änderung)
```
- `McpConnectionService.applyConfig()` neu (Sketch):
```java
private record McpApplied(List<McpServerConfig> servers, boolean enabled) {}
private volatile McpApplied lastApplied; // UI-thread confined; volatile zur Sicherheit

public void applyConfig() {
    var servers = McpPreferenceInitializer.loadServers();
    boolean enabled = !servers.isEmpty() && McpPreferenceInitializer.isMcpEnabled();
    var desired = new McpApplied(servers, enabled);
    if (desired.equals(lastApplied)) return;   // R-MCP1b
    lastApplied = desired;
    if (enabled) connect(); else disconnect();
}
```
- `toggle(boolean)` aktualisiert `lastApplied` konsistent (sonst reconnectet das nächste
  applyConfig unnötig): nach Persistenz-Update `lastApplied = new McpApplied(loadServers(), enabled)`.
  Failure-Konvergenz: `connect()`-Fehler setzt `PREF_MCP_ENABLED=false` → nächstes applyConfig
  berechnet enabled=false → disconnect (kein Endlos-Reconnect).
- Dependency-Richtung bleibt: `McpConnectionService` (plugin) → `ToolService` (core) → `McpService` (core).
  `McpService.java:81` (protocolVersion-Wiring) UNVERÄNDERT — leer geht an
  `DefaultMcpClient.Builder.protocolVersion(...)` = Auto-Detect.

## 4. Affected files
**inc-1:**
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/mcp/McpServerConfig.java` — Konstante weg, Compact-Ctor → `""`, 3-arg-Ctor → `""`, Javadoc.
- `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/mcp/McpServerConfigTest.java` — NEU (JUnit 5 + AssertJ): R-MCP2a + null→""-Normalisierung.
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/McpConnectionService.java` — Compare-Value-Objekt + applyConfig/toggle wie oben.
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java` — `applyMcpConfig()`-Aufruf vor Zeile 376 (aus dem Gate-Block raus); Gate-Block sonst unverändert.
- `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/McpConnectionServiceTest.java` — NEU (JUnit 4): R-MCP1a/b.
- ggf. `McpServiceTest.java` bleibt unberührt (@Disabled Integration, nutzt 3-arg-Ctor → jetzt `""`, ok).

**inc-2:**
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/McpPreferenceView.java` — Zeile 266: Dialog-Default `""` statt Konstante; `txtProtocol` (209, 265-266) → editierbare Combo (`SWT.DROP_DOWN` ohne READ_ONLY; Vorschläge `Auto`, `2025-11-25`, `2026-07-28`; Freitext); `Auto` speichert `""`; Hinweistext laut docs/mcp.md R-MCP3 („leer = Auto-Detect; 2025-11-25/2024-11-05 = Legacy, 2026-07-28 = modern; andere Werte lösen die Versionserkennung aus (kann bei Dual-Era-Servern -32022 liefern)"); `setItemData` (Zeilen 127/131): leere protocolVersion → `Auto` anzeigen.
- `homepage/src/setup/mcp-configuration.md` — sichtbare Änderungen im selben Inkrement: Auto-Detect-Default, Dropdown statt Textfeld, live-apply-Hinweis (R-MCP1). Welche Sektion: Datei prüfen (37 MCP-Treffer), passgenau statt Vollumbau.

## 5. Rules & constraints
- Log OR throw, nie beides. Surefire = Ground-Truth für Core-Testzahlen. `eclipseBuildProject` VOR jedem OSGi-Testlauf (stale bin/ → ClassNotFoundException).
- Kein Git/kein Branch-Wechsel; Commit nach jeder grünen Iteration inkl. docs/** (hier: nur committen, nicht ändern).
- Thread-Safety: `applyConfig`/`applyMcpConfig` laufen im UI-Thread; `connect()` im Job — `lastApplied` volatile,compare+set atomar aus UI-Thread-Sicht.
- Plugin-Tests: JUnit 4, KEINE AssertJ; Preferences-Fixture VOR dem SUT setzen, im @After räumen (kein persistierter Cross-Run-State).
- Warnings-Disziplin (laufender Cleanup-Zyklus): keine neuen Warnings/Suppressions ohne Not.

## 6. BDD acceptance
- **R-MCP2a** GIVEN protocolVersion leer WHEN McpServerConfig gebaut THEN Wert bleibt leer.
  Test: `McpServerConfigTest.givenEmptyProtocolVersion_whenConstructed_thenStaysEmpty` (core, Surefire).
- **R-MCP1a** GIVEN MCP verbunden mit Config C1 WHEN gespeicherte MCP-Config ändert sich zu C2 (LlmConfig unverändert) THEN Clients werden mit C2 neu verbunden.
  Test: `McpConnectionServiceTest.givenChangedMcpConfig_whenApplied_thenReconnects` (Plugin-Modul).
- **R-MCP1b** GIVEN MCP-Config unverändert WHEN applyConfig läuft THEN kein Reconnect (kein Spawn pro Preference-Event).
  Test: `McpConnectionServiceTest.givenUnchangedMcpConfig_whenApplied_thenNoReconnect` (Plugin-Modul).
- **R-MCP3** manuelle Verifikation (kein SWT-Harness, Präzedenz R-UI1): Dropdown, Hint-Text, Tabelle „Auto", Freitext speichert.

## 7. Test strategy
- Core: neue `McpServerConfigTest` (eine Testklasse sammelt die Config-Tests); Surefire-Lauf als Ground-Truth.
- Plugin: `McpConnectionServiceTest` sammelt R-MCP1-Tests; Recorder-Subclass als Seam; `eclipseBuildProject` vor Lauf; erster Lauf braucht ggf. Workspace-Trust-Dialog (User informieren, nicht parallel nachstarten).
- AIChatView-Wiring (Aufruf vor dem Gate) ist UI-Wiring — über Code-Review + manuelle Verifikation (Preference-Page-Save ohne LlmConfig-Änderung verbindet neu), kein separater Test (AIChatView nicht unit-testbar).
- Homepage: reiner Markdown-Text, kein Test.

## 8. Increments (je grün, Commit inkl. docs/**)
1. **inc-1:** R-MCP2a (core + Test) + R-MCP1 (Compare in McpConnectionService + R-MCP1a/b-Tests + AIChatView-Wiring) → core-Surefire + OSGi-Tests grün → commit.
2. **inc-2:** R-MCP3 (Dialog-Combo, Tabellen-„Auto", Hint) + homepage/src/setup/mcp-configuration.md → Build grün, manuelle UI-Verifikation → commit.

## 9. Open questions
- Homepage komplett in inc-2 (statt aufgeteilt) — pragmatisch entschieden, ein Edit statt zwei.
- Falls Recorder-Subclass + Preferences im OSGi-Test nicht funktioniert (InstanceScope im Test-Workspace): STOP-AND-ASK vor Design-Änderung.

## Status (Da Mek, 2026-09-10)
- **inc-1** ✅ commit `cec9ebf` — R-MCP2a (core Surefire 4/4, Ground-Truth) + R-MCP1 (OSGi 196/0/0, R-MCP1a/b grün). `McpPreferenceView:266` Default `""` als kompilierende Ein-Zeilen-Fix mitgezogen (PO genehmigt, 2026-09-10).
- **inc-2** ✅ (dieser Commit) — R-MCP3 Dialog-Combo (Auto/2025-11-25/2026-07-28 + Freitext, `Auto`→`""`, Hint), Tabelle zeigt `Auto`, homepage `mcp-configuration.md` (Auto-Detect-Default, live-apply-Hinweis R-MCP1). OSGi 196/0/0. R-MCP3 = manuelle UI-Verifikation (kein SWT-Harness).

---

## PO-Review (2026-09-10) — Verdict: **CONCERNS**

Drei-Seiten-Prüfung MCP-Feature (cec9ebf + fb86226). Frisch verifiziert in dieser Session:
core 0 Errors, Plugin 0 Errors (nur die 11 known-benign Warnings — keine neuen aus dem
MCP-Code), `McpServerConfigTest` 4/4 grün, `McpConnectionServiceTest` 2/2 grün (frischer Lauf).

### Verified — Plan ↔ Code
1. **Clean Break** ✅ — `McpServerConfig.DEFAULT_PROTOCOL_VERSION` weg (Repo-Grep: 0 Treffer in
   Code, nur Docs-Historie); Compact-Ctor `hasNoValue → ""`; 3-arg-Ctor übergibt `""`; keine
   Migration/Alias. `McpService` (`.protocolVersion(server.protocolVersion())`) unverändert.
2. **Compare-Lokation** ✅ — `McpApplied`-Record + `lastApplied` volatile in
   `McpConnectionService`, nicht im LlmConfig-Gate. `AIChatView.applyConfig():378` ruft
   `applyMcpConfig()` VOR dem Gate (Return in :380); StatusLine-Updates in `applyMcpConfig`
   (:390-395) unconditional. Gate-Block sonst unverändert. Chain komplett:
   prefListener (:103) → applyConfig (UI-Thread) → PeonAiService.applyMcpConfig → service.applyConfig.
3. **toggle()-lastApplied-Konsistenz** ✅ — `toggle()` setzt Persistenz (mit flush) + `lastApplied`
   via `desiredState()` → der prefListener-Event nach dem Toggle fällt auf desired==lastApplied
   durch (kein Doppel-Reconnect). Failure-Konvergenz wie geplant: connect()-Job setzt im finally
   PREF_MCP_ENABLED → nächstes applyConfig disconnectet.
4. **Thread-Safety** ✅ — lastApplied volatile + UI-thread-confined (Listener, Toggle, StatusLine
   callbacks laufen über runInUiThread); connect im Hintergrund-Job. `McpPreferenceInitializer`
   flushed bei saveServers/setMcpEnabled.
5. **R-MCP3** ✅ — Combo ohne READ_ONLY (Freitext), Vorschläge Auto/2025-11-25/2026-07-28,
   `protoVersion()`: "Auto"→"", Freitext-Roundtrip (indexOf/setText), Tabelle zeigt `Auto` in
   beiden Branches (setItemData), Hinweistext inhaltlich = docs/mcp.md R-MCP3 (Englisch — PO
   genehmigt), Dialog-Default `""` (PO genehmigter inc-1-Pullforward).
6. **langchain4j-Semantik** ✅ — `DefaultMcpClient.initialize()` (262-276, gegen lokalen Checkout
   verifiziert): leer→autoDetect(modern, legacy), moderne Version force, 2025-11-25/2024-11-05
   force legacy, andere → autoDetect(v, v). docs/mcp.md R-MCP2 exakt korrekt abgebildet.
7. **Homepage** ✅ für neues Verhalten — Dropdown mit Auto + Freitext, „Auto (empty) is the
   default", -32022-Hinweis, live-apply-Absatz (R-MCP1). McpServiceTest unverändert (@Disabled,
   3-arg-Ctor → compiliert).

### Verified — Docs ↔ Code (BDD)
8. R-MCP1a `givenChangedMcpConfig_whenApplied_thenReconnects` ✅, R-MCP1b
   `givenUnchangedMcpConfig_whenApplied_thenNoReconnect` ✅ (JUnit 4, Recorder-Subclass, null
   ToolService dokumentiert, Preferences-Fixture mit Restore+flush), R-MCP2a
   `givenEmptyProtocolVersion_whenConstructed_thenStaysEmpty` ✅ (+ null-Normalisierung,
   explizite Version bleibt, 3-arg → leer). R-MCP3 manuell laut Doc.

### Verified — Docs ↔ Plan
9. Plan deckt alle R-MCP-Regeln, Testnamen, Inc-Struktur, Homepage-Scope und ADR-0045-Decision
   (Konstante weg, keine Migration, UI-Combo) ab. Kein Plan-Coverage-Gap.

### Findings (nicht-blockierend → CONCERNS)
- **C1 — toggle()-lastApplied ohne Test:** kein BDD/Plan-Test verlangt, aber die einzige
  konsistenzkritische Zeile ohne roten Test. Symptom bei Regression: ein redundanter
  Reconnect nach jedem Toggle (prefListener-Event). → kleiner dritter Recorder-Test
  (toggle → applyConfig → kein Reconnect) schließt die Lücke und ist zugleich der
  Mutation-Nachweis (siehe unten).
- **C2 — Homepage „Description"-Feld existiert nicht im Code:** `mcp-configuration.md`
  Common-fields-Tabelle dokumentiert „Description — Optional hint for the AI", aber
  `McpServerConfig`/Dialog haben kein description-Feld. Vermutlich Alt-Drift (kein Git-Diff
  in dieser Umgebung möglich), nicht R-MCP-Scope — aber in der in inc-2 berührten Datei.
  PO-Entscheid: Zeile entfernen oder Feld planen.
- **C3 — Rule of three „effective enabled":** `!servers.isEmpty() && isMcpEnabled()` 3×
  (`McpConnectionService.desiredState()`, `isEnabled()` static, `AIChatView.applyMcpConfig():393`).
  Lokale Extraktion: in `applyMcpConfig` `McpConnectionService.isEnabled()` statt Nachbau.
- **C4 — connect() lädt Server im Job neu** statt lastApplied-Snapshot zu nutzen: wenn sich
  Prefs zwischen applyConfig und Job-Ausführung ändern, wird einmal zu viel verbunden
  (Selbst-Konvergenz, kein Stick-State; Pre-existing-Muster). Hinweis, kein Rework.

### Mutation-check (C1 ist die eine Stelle)
R-MCP1b selbst ist bereits der Mutations-Beweis für den Compare (Guard entfernen →
connectCount 2 → rot). Der EINE Spot ohne Nachweis: `toggle()` ohne lastApplied-Update —
kein Test würde rot. Empfehlung an PO: Test aus C1 nachziehen; kein separater
Mutation-Proof nötig.

Most likely reason this breaks later: toggle()'s lastApplied-Update ist die einzige
konsistenzkritische Stelle ohne roten Test — ein Refactor von toggle()/prefListener
reintroduziert still das „Reconnect bei jedem Preference-Event"-Bug. Change mit größter
Risikominderung: der dritte Recorder-Test (C1).

Skill/instruction gaps: keine.
