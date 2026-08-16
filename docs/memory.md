# Open Ends (2026-08-16)

- **Core-Fix-Kampagne:**
  - `ChatMessageUtil.toString()` droppt SystemMessages — Workaround `staticText()` lebt im
    Plugin ([ADR-0030](adr/0030-statictext-helper-frozen-chatmessageutil.md)).
  - Erledigt 2026-08-16: `ThreadSafeMemory`-Load-Pfad doppelte Division gestrichen
    (chars/9 → chars/3, konsistent mit `estimateTokens`).
  Nicht jetzt — erst wenn wir Core anfassen.
- **Beobachten:** R2(a)-Rest-Race — nur relevant, falls der Live-Status nach Compact
  doch noch mal klebt (spät gelieferter Monitor-Callback, vgl. context-architecture.md R2).

# Zyklus ADR-0029 abgeschlossen (2026-08-16)

Alle Items gebaut + Review OK + Smoke Test grün: po-agent-jon.md Marker ✅,
EclipseFileContextItem + AgentsMdContextItem → Header-dedupKey, `itemsFor()` mit
2 Items (R1+R2), Core-Delta `StandingOrdersBuilder.buildItems()` → `List<ContextItem>`,
R2(a) Live-Status-Hide nach Replay in `doCompressContext`.
