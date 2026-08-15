# ADR-0028: ContextItem-Konzept ersetzt StaticContentLoader

**Status:** Done ✅

**Context:** StaticContentLoader lud Dateien als String und injizierte sie per Callback in den System-Prompt. 
Der Callback-Mechanismus (onCompacted) führte zu Responsibility Bleed (UI/Service mussten wissen, 
dass nach Compact neu geladen werden muss). Gemischte Typen (String/Message/Supplier) verursachten 
Drift. KV-Cache wurde bei jedem Compact invalidiert, weil System-Prompt sofort neu gebaut wurde.

**Decision:** 
- `ContextItem` als functional Interface (`String render()`) — OCP, austauschbare Disk/Eclipse Implementierungen
- `lastModified`-Caching in `DiskFileContextItem` — effizient, ohne Eclipse Resource Listeners (MVP)
- `AbstractAgent.compactContext()` besitzt den gesamten Compact-Ablauf autonom: memory.clear → systemMessage=null → turnContext restore → summary add
- System-Prompt rebuild verzögert bis nächste `call()` (systemMessage==null Guard) — KV-Cache bleibt erhalten
- `persistentContext` (List<ContextItem>) für Session-Start, `turnContextSupplier` für History/Compact
- Header `Static loaded file <path>:\n---\n<content>` für contains-Check in memory (Deduplication ohne History-Modifikation)

**Consequences:**
- StaticContentLoader, StaticContentMessage, Callback-Mechanismus entfernt
- `ToolLoopRequest` erhält optionalen `AiAgent`-Verweis (gesetzt durch `AbstractAgent.doCall()`).
- `CompactSessionTool` delegiert vollständig an `request.getAgent().compressContext()`.
- Kein Memory-Access mehr im Tool; Agent ist Single Source of Truth für Clear/Restore.
- KV-Cache effizient genutzt (System-Prompt rebuild nur nach clear/erste Nachricht)
- Plan/Dev Agenten bekommen memory.md/AGENTS.md nicht automatisch (nur Jon)
- Eclipse Resource Listeners für Echtzeit-Update sind Low-Priority (lastModified ausreichend für MVP)

**Related:** [Context Message Concept](../context-message-concept.md), [po-agent-jon.md](../po-agent-jon.md)
