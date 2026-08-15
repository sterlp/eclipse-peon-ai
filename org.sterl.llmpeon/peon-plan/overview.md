# Plan: Context Message Konzept

**Ziel:** Typ-basierte ContextMessages (`ContextItem` Interface) ersetzen StaticContentLoader + Callback-Mechanismus. Agent besitzt Compact-Ablauf autonom.

**Design:** `/llmpeon-parent/docs/context-message-concept.md`

---

## Increment 1: ContextItem Interface + Implementierungen (core)

**Pfad:** `llmpeon-core`

**Neu:**
- `org/sterl/llmpeon/context/ContextItem.java` — Functional Interface: `String render()`
- `org/sterl/llmpeon/context/DiskFileContextItem.java` — Disk-basiert, mit Header `"Static loaded file <path>:\n---\n<content>"`, lastModified-Cache
- `org/sterl/llmpeon/context/SimpleContextItem.java` — Plain-Text (für Standing Orders)

**Tests:**
- `ContextItemTest` — DiskFileContextItem render() + Cache + lastModified-Invalidation
- `SimpleContextItemTest` — Plain-Text render()

**Grün:** `mvn clean verify -pl llmpeon-core`

---

## Increment 2: AbstractAgent Compact-Ablauf

**Pfad:** `llmpeon-core`

**Änderung:**
- `AbstractAgent.java`:
  - `private volatile String systemMessage = null` — Guard für rebuild
  - `private List<ContextItem> persistentContext` — Persistent Context (Session-Start)
  - `private Supplier<List<ContextItem>> turnContextSupplier` — Turn-scoped Context
  - `setPersistentContext(List<ContextItem>)` — Setter
  - `setTurnContextSupplier(Supplier<List<ContextItem>>)` — Setter
  - `compactContext(AiMonitor)`:
    1. Komprimierung via AiCompressorAgent (wie heute)
    2. `memory.clear()`
    3. `systemMessage = null` (Force rebuild)
    4. `restoreTurnContext()` — contains-Check, nur einmal injizieren
    5. `memory.addResult(summary)`
  - `call()`: `if (systemMessage == null) systemMessage = buildSystemPrompt()`

**Tests:**
- `AbstractAgentTest.test_compactContext_clearsMemoryAndRestoresTurnContext()`
- `AbstractAgentTest.test_call_rebuildsSystemMessageAfterClear()`
- `AbstractAgentTest.test_restoreTurnContext_skipsDuplicates()`

**Grün:** `mvn clean verify -pl llmpeon-core`

---

## Increment 3: Eclipse-Integration + Migration

**Pfad:** `org.sterl.llmpeon` (plugin)

**Neu:**
- `org/sterl/llmpeon/context/EclipseFileContextItem.java` — Eclipse-VFS-basiert

**Änderung:**
- `PeonAiService.java`:
  - Weg: `StaticContentLoader`, `loadStaticContent()`, Callback-Setup
  - Neu: `setPersistentContext(List.of(new EclipseFileContextItem("docs/memory.md"), ...))`
  - Neu: `setTurnContextSupplier()` — AGENTS.md, Project, etc.
- `AIChatView.java`:
  - Weg: Session-Start `loadStaticContent()` Aufruf
  - Neu: Persistent Context wird automatisch am Session-Start via `call()` injected (systemMessage==null)
- `AiPoAgent.java`:
  - Weg: `compressCallbackSupplier`, `setCompressCallbackSupplier()`
  - `getCompressCallback()` — bleibt (für Backward-Kompatibilität, gibt null)

**Tests:**
- `EclipseFileContextItemTest` — plugin-Test (Eclipse VFS)
- `PeonAiServiceTest.test_persistentContext_setOnPoAgent()`

**Grün:** `mvn clean verify` (vollständig, inkl. core)

---

## Increment 4: Cleanup + Verify

**Pfad:** `llmpeon-core` + `org.sterl.llmpeon`

**Löschen:**
- `StaticContentLoader.java`
- `StaticContentMessage.java`
- `StaticContentLoaderTest.java`
- Callback aus `AiCompressorAgent.java` (Konstruktor-Parameter `Runnable onCompacted`, Aufruf in `call()`)
- Callback aus `AbstractAgent.java` (`getCompressCallback()` — nur wenn nicht mehr verwendet)

**Änderung:**
- `docs/po-agent-jon.md` — StaticContentMessage Teil auf ✅ done, ContextItem erwähnt

**Grün:** `mvn clean verify` im `/llmpeon-parent` (vollständig)

**369+ Tests grün** (neue Tests + bestehende Tests intakt)

---

## Risiken

- **AiCompressorAgent Callback:** Wird von anderen Agenten verwendet? (Nur Jon nutzt's heute.) → Safe zu entfernen.
- **getCompressCallback():** Bleibt als deprecated (Backward-Kompatibilität für Custom Agents).
- **System-Prompt Rebuild:** `buildSystemPrompt()` muss persistent Context Items rendern. Heute: `staticContext` (String) wird angehängt. Neu: `persistentContext` (List<ContextItem>) wird gerendert und angehängt.

## Abgrenzung zu po-agent-jon.md

- po-agent-jon.md: Feature-Spezifikation (Business Rules, BDD)
- Dieser Plan: Technische Umsetzung (Code, Tests, Migration)
- nach Build: po-agent-jon.md auf ✅ done aktualisieren
