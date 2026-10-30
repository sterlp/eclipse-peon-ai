# ADR-0078 — Origin im Core: `OwnerMonitor` am Turn-Besitzer, `ToolLoopRequest` → `LoopOwner`

**Status:** Accepted (2026-10-29, Paul; 2026-10-30 an den gebauten Mechanismus angepasst — Da-Dok-Review) — löst Teile von [AF-ADR-002](../../aifactory/docs/adr/002-origin-in-core-display-in-factory.md)
ab (dort: „Core-Weg bleibt Plugin-Thema" — jetzt Core-Wege für beide Consumer) und setzt
[agent-origin-proposal](../../proposals/agent-origin-proposal.md) §7a um.

## Context

Der Web-Chat darf nur Jon und die finalen Antworten seines Teams zeigen (R-WP-4, UC-WP-39/40);
das Plugin soll künftig vor jeder Nachricht den sprechenden Agenten zeigen
([agenten-namen-im-chat](../../org.sterl.llmpeon/docs/agenten-namen-im-chat.md)). Gemessen (2026-10-29):
die Events eines delegierten Mitglieds laufen anonym über den Monitor des Delegierenden —
`SimpleMessage.origin`/`OnPartialAiResponse.origin` existieren, haben aber **keinen** Emitter im Core.
Konsequenz heute: Mitglieder-Denken, -Teilantworten und -Werkzeugzeilen erscheinen im Web als Jons
Zeilen. Die Fabrik könnte sie nur durch Parsen von Core-Text unterscheiden — das verbietet AF-ADR-002.

Alternativen verworfen: Decorator nur in `PoDelegateTool` (deckt `AgentsTool`, `SearchAgentTool`-Subloop,
Compact-Kompressor und Custom Agents nicht — R-WP-4-Loch); Stempeln an 71 Emit-Stellen
(`onStreamingChunk` bliebe ungelöst); `getAgent()`/`getActiveAgent()` am `AiMonitor`
(agenten-namen-im-chat.md:33-37 — der aktive Agent bleibt während der Delegation Peon-PO; der Monitor
ist ein ausgehender Beobachter, kein Service-Locator; `NULL_MONITOR` müsste einen Agenten erfinden).

## Decision

1. **`origin` = Core-Id des sprechenden Agenten (`Jon`, `Dev`, … — [ADR-0079](0079-display-names-without-ork-names.md)), immer gestempelt;
   `null` = außerhalb eines Agent-Turns.** Kein UI-Name im Core (`AgentNames`-JavaDoc), kein Policy-Missbrauch
   von `null` („null = Jon" wäre Fabrik-Policy, nicht Core-Tatsache).
2. **Stempeln im Decorator am Turn-Besitzer:** neuer zustandsloser `OwnerMonitor(origin, delegate)`
   (Core `shared/`, ~30 Zeilen, fill-if-null — bereits gestempelt bleibt, `isCanceled()` transparent),
   gebunden in `AbstractAgent` je **Emitter** (`call`, `compact`, `buildStaticMessages`) — analog
   `turnStart` (ADR-0071-Linie: der Agent besitzt den Turn). Statt eines Reflexions-Architekturtests
   pinnt ein Guard-Test je Emitter die Bindestelle; Mutationstest: Jon-Decorator zuerst ⇒ Mitglieder-Events
   fälschlich als Jon ⇒ muss rot werden.
3. **Sub-Loops ohne Agenten benennen sich selbst:** `SearchAgentTool` und `CompactService` wickeln mit
   `LoopOwner.named(...)` bzw. eigenem Label (`Search`, `Compact` — [ADR-0079](0079-display-names-without-ork-names.md)).
4. **`ToolLoopRequest`: `agent` + `agentConfig` → ein Feld `LoopOwner`** (`name()`, `@Nullable agent()`
   für Rückdelegation, `config(base)`; Default `LoopOwner.NONE = named(null, dev)` — `null` stempelt
   nicht, kein stiller Kopf). Zwei Wahrheiten werden eine, der
   R-CC-15-Kommentarwächter wird Typ-Invariante. `config()` liest live aus `chatModel.getConfig()`
   (Präferenzwechsel wirkt in der nächsten Runde, UC-WP-24 GIVEN 3 — Testpflicht).
5. **Kein zweiter Abschluss-Kanal, kein Delegations-Antwort-Event** (Leitplanken AF-ADR-002 bleiben):
   `onDelegatedAnswer`/`onAgentCallCompleted`/`ToolResultAttributes`/`ChatEvent.Delegated` kommen **nicht**
   zurück (D6). Turn-Ende = Rückkehr von `call()`; die finale Team-Antwort ist das Ergebnis des
   Delegations-Werkzeugs im Verlauf, der Urheber folgt aus dem Werkzeugnamen.
6. **Anzeige-Policy bleibt beim Consumer:** Web-Filter wird `isJon(origin)` statt `null == Jon`; der
   `null`-Zweig bleibt ehrlicher Fallback. TOOL/PROBLEM-Zeilen innerhalb eines Mitglied-Turns tragen
   über dessen `OwnerMonitor` bereits die Mitglieds-Origin — das Web zeigt Zeilen mit Mitglieds-Origin
   grundsätzlich nicht, es braucht **keine** Id-Buchführung für offene Delegationen.

## Consequences

* Plugin: `msg.origin` trägt der Record-JSON automatisch (`ChatMarkdownWidget` serialisiert); Merge-Regel
  im Chat muss nach `role` **und** `origin` mergen; Persistenz-/Chat-Rebuild-Pfade prüfen; Restore ohne
  Namen bleibt gleichgültig (agenten-namen-im-chat.md:18-20).
* Fabrik: die Inc-3f-Abhängigkeiten (gestrichene Kanäle) werden zuerst entfernt (Fabrik-Entgiftung gegen
  das gelieferte Core-Artefakt), danach folgt die Live-Trennung auf dem gestempelten `origin`
  (`isJon(origin)` + Null-Fallback, keine Id-Buchführung).
* Grenze: agentenlose Loops stempeln nur, wenn sie sich benennen — ein neuer Einstiegspfad ohne Bindung
  bleibt ungestempelt (`null` = ehrlicher Fallback, kein stiller Jon). Die Emitter-Guards fangen das.
* PR-3-Ausnahme bewusst freigegeben (Paul): Signatur-Entfernung in `ToolLoopRequest` (kein Plugin-Leser).
* Plan-Abweichung (Inc 2): der geplante `OriginStampTest#subLoopsCarryTheirOwnName` entfiel — die
  Sub-Loop-Namen sind stattdessen durch `SearchAgentToolTest` (`owner().name() == "Search"`, Zeile
  trägt `Search`) und `CompactServiceTest#compressorChunksCarryTheCompactLoopName` gepinnt.
