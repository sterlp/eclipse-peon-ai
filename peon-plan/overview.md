# Plan: Compact-Redesign (R-CIB-1…6 + Komponenten-Extraktion + Render-Modi, ADR-0056)

SOLL-Basis: **docs/compact.md** (konsolidiert 2026-09-24, F1–F8 eingebacken — NOT touch by dev) ·
Architektur: **docs/adr/0056-compact-component-and-render-modes.md** (read-only SOLL) ·
Da-Dok-Review: F1–F10 abgearbeitet, nicht neu erfinden.

## ⛔ STOP-AND-ASK (Paul 2026-09-08 — prominent, gilt für JEDES Inkrement)

Bei Compile-Fehlern ohne Lösung, nicht-grün-bekommenden Tests, IST-Widersprüchen zum Plan oder
Unklarheiten: **aktiv stoppen und im Jon/askDev-Kanal nachfragen** — nie still workarounden,
nie das SOLL (docs/compact.md) ändern. Docs = SOLL, gepflegt von Da Boss; Testnamen im Feature-Doc
NICHT anpassen.

**Blockaden:** keine — Q1/Q2 durch Paul entschieden (2026-09-24). Neu zu beachten: R-CC-8 „> 3"
steht als ⏳ (Pauls Randnotiz „denke ich") — **so bauen, wie das Doc es sagt**; nur bei hartem
Widerspruch am Code (z. B. Guard-Grenze 3 vs. > 3 kollidiert mit bestehenden Test-Seeds) STOP-AND-ASK.

---

## Verifizierte IST-Fakten (am Code, 2026-09-24 — Grep/Read, nicht Doku)

| # | Behauptung | Evidenz |
|---|---|---|
| a | `compact()`-Caller (Prod): `AiAgent.java:28` (Interface), `AbstractAgent.java:267` (Auto-Gate in doCall, Result ignoriert), `AbstractAgent.java:297` (Impl), `AIChatView.java:492` (`result == CompactResult.COMPACTED`), `AIChatView.java:520` (→ `AiAgentStatusModel.compactResult`), `PoDelegateTool.java:190` (switch über Enum), `CompactSessionTool.java:29` (switch über Enum). Tests: `AbstractAgentCompactResultTest` (2×), `AbstractAgentTest` (~10×), `AiDeveloperAgentTest`, `AiPoAgentTest` (2×, davon 1× mit Result-Assertion), `AiCompressorAgentTest:72` (via Agent), `CompactSessionToolTest` (2× anonymous Override **mit Enum-Signatur**), `HeaderRosterStructureTest:104` (**Plugin-Modul**, Enum-Signatur!), `PeonAiServiceTest` (~13× `compact(null)`), `ToolServiceCompactResultTest`, `AiAgentStatusModelTest` (switch über Enum). | ✅ verifiziert |
| b | **Pauls These „erster TextContent" hält NICHT** — `AbstractAgent.doCall:271-274` baut die UserMessage als `renderTurnContext(...)`-Items **zuerst**, dann `userMessages.add(TextContent.from(message))` — der echte User-Text ist der **LETZTE** TextContent. Gleiches Bild: `ThreadSafeMemory.add:73-77` (join hängt neue Inhalte hinten an), Compact-Restore `AbstractAgent:320-326`. **✅ PAUL BESTÄTIGT (2026-09-24):** Regel gilt mit „**letzter** TextContent"; docs/compact.md R-CIB-4 + BDD bereits umgeschrieben; Insert-Ordnung ist als Kontrakt dokumentiert in **docs/context-message-concept.md** (read-only SOLL für den Dev). | ✅ entschieden |
| c | `ChatMessageUtil.toString`-Aufrufstellen (Prod): 3 Overloads — `toString(msg)` (default `true, 6000`), `toString(msg, int)` (nur `ThreadSafeMemory.containsMessage:120`, 90000), `toString(msg, boolean, int)` (nur `AiCompressorAgent:70`, `false, 4000`).Weitere Prod-Nutzer: `ChatMessageUtil` intern (`charCount:63`, `readChatMessage`), Tests (`StreamMock` core+plugin, `AbstractUnitTest`, etliche Assertions). Options-Record muss alle 3 Signaturen default-kompatibel halten. | ✅ verifiziert |
| d | Doppel-Präfix: `AiCompressorAgent.toText:69` hängt `msg.type()+":"` an, `ChatMessageUtil.toString:106` **nochmal** → „AI:\nAI:…" (alle Typen). Fällt mit dem neuen Pfad weg. | ✅ verifiziert |
| e | Dedup IST: `AiCompressorAgent.call:37-41` = `LinkedHashSet<String>` **über den bereits 4000-gecappten** `toText`-Strings. SOLL: Dedup auf **ungecappten** Renders VOR den Caps (R-CIB-2). | ✅ verifiziert |
| f | SystemMessage-Landmine (ADR-0030): `ChatMessageUtil.toString:101` returnt `""` für SYSTEM. Der `staticText()`-Helper aus ADR-0030 **existiert nicht mehr** (Grep: 0 Treffer) — der Workaround ist mit der ContextItem-Refactoring verschwunden; Fix = toString rendert SYSTEM, Compact-Input filtert SystemMessages **auf Listenebene** (Compressor-Javadoc AiCompressorAgent:31-33: bewusst ignoriert — bleibt SOLL). | ✅ verifiziert |
| g | Hint/Marker: `ToolService.COMPACT_HINT` (Konstante, :49, add :217, Check :212), `STUCK_MESSAGE` (:185, UserMessage), Queued-Marker **Inline-Literal** `"[Queued Message] "` in `AbstractAgent.queuedMarker:257` → wird Konstante. | ✅ verifiziert |
| h | Budget-Quelle: `AbstractAgent.compactAfterTokens():167-169` = `getAutoCompactAfter() × compactFactor` (nur Auto-Trigger). R-CIB-1 SOLL: Staging-Budget = **`autoCompactAfter` (Config, roh)** — der Faktor skaliert nur den Trigger. `LlmConfig.getAutoCompactAfter()` ≤ 0 → „off". | ✅ verifiziert |
| i | LLM-Call/Routing: `AiCompressorAgent.call` — COMPRESS_SYSTEM (compressor.md), COMPACT-Slot (`cfg.compactAgentConfig()`), `callBlocking`, `null → throw IllegalStateException`, Response-Streaming via `monitor.onChatMessage/onChatResponse`. Engine übernimmt 1:1. | ✅ verifiziert |

---

## 1. Context

Der Compact ist der Recovery-Pfad für übergroße History. Heute: Input nur per-Message 4000er-Blanko-Cap
(`AiCompressorAgent.toText:70`), kein Gesamt-Budget (Evidenz: 400 `exceed_context_size_error`,
337461 Provider-Tokens vs. 114512 Compact-Schätzung), keine Beobachtbarkeit, Input-Bau in einem
Pseudo-Agenten. SOLL: gestufte, ehrliche Kürzung mit Disclosure (R-CIB-1…6), Compact als eigene
core-Komponente `compact` (ADR-0056), Render-Modi in `ChatMessageUtil` als Options-Record
(ADR-0030 verworfen), `CompactResult` Enum→Record (F9), Stats bis Tool-Result/Statuszeile,
compressor.md-Prompt-Zeile, Homepage-Update.

**Nicht im Scope:** R-CC-7 (Fehlerklassen/Retry — eigener Zyklus), compact-lock.md/UI-Button-Umbauten
(außer wo der Record Signaturen erzwingt), Live-Context-Änderungen, Auto-Compact-Trigger-Logik.

## 2. Design-Entscheidungen

1. **`org.sterl.llmpeon.compact` (core), ~4 Typen, kein Interface-Zoo** (ADR-0056):
   - `CompactStager` — **pure** Funktion: Dedup + Stufen 1/2/Endstufe + Disclosure + Stats.
   - `CompactEngine` — Stager + LLM-Call (COMPACT-Slot-Routing wie IST) + Logging.
   - `CompactResult` — Record (Enum→Record, R-CIB-6/F9) mit `status()` + Stats.
   - `CompactLog` — 3-Methoden-Log-Schnittstelle (debug/warn/error), Default→slf4j; Tests injecten
     einen Capturing-Log. Grund: Entry-Log/Result-Zeile sind BDD („genau EIN debug-Log") und müssen
     ohne Log-Capture-Lib testbar sein.
   - Orchestrierung/Skip-Guards (`memory.size() < 3` → SKIPPED_SMALL, Auto-Trigger, memory
     clear/re-seed mit „Session compacted:") **bleiben in `AbstractAgent.compact()`** — die
     öffentliche API `AiAgent.compact(monitor)` bleibt unverändert (Plugin-Blast-Radius klein).
2. **Render-Modi in `ChatMessageUtil` als Options-Record** (ADR-0056.2, statt wachsender
   Parameterliste `(msg, includeThink, toolMessageSize)`):
   ```java
   /** Bestandsformate bleiben per Default exakt erhalten; Compact-Modi schalten nur Extras. */
   record RenderOptions(boolean includeThink, int toolMessageSize, int thinkCapChars,
                        boolean thinkKeepTail, boolean perMessageTrimmedTag, boolean renderSystemMessage) {
       static RenderOptions defaults() { /* includeThink=true, toolMessageSize=6000, cap=MAX, tag=true, system=true */ }
       static RenderOptions compactStage1() { /* thinkCap=9000, keepTail=true, toolMessageSize=0 (nichts), tag=false, system=false */ }
       static RenderOptions compactStage2() { /* thinkCap=6000, keepTail=true, toolMessageSize=6000, tag=false, system=false */ }
   }
   String toString(ChatMessage msg, RenderOptions options);
   // Bestehende 3 Signaturen bleiben als Delegate auf defaults() — keine Bestandsaufruf-Änderung.
   ```
   - **Think-Front-Cap mit `$`-Anker:** wenn `think.length() > thinkCapChars` → `"Think: …$ " + tail`
     (tail = letzte `thinkCapChars` Zeichen; Konklusion bleibt, Prozessgeraste fliegt vorn weg).
     `…$`-Marker als benannte Konstante, nur wenn gekappt.
   - **SystemMessage-Fix (Landmine, roter Test zuerst):** `renderSystemMessage=true` (Default) rendert
     `SYSTEM:<nl><text>` statt `""`. Der **Compact-Input filtert SystemMessages auf Listenebene**
     (vor Dedup) — das „intentionally ignored" des Compressors bleibt, aber über den Stager, nicht
     über einen stillen Render-Drop.
   - **Stage-2-Think-Cap-Form:** gleicher Front-Cap-Stil wie Stage 1 (Ende=Konklusion bleibt), nur
     mit 6000 — konsistente Semantik, kein zweiter Think-Render-Pfad.
   - **`perMessageTrimmedTag=false`** im Compact-Pfad: R-CIB-5 (User-Korrektur) — kein `(trimmed)`
     je Message mehr; Disclosure einmal am Input-Ende. Bestandsaufrufe behalten den Tag.
3. **Estimator an EINER Stelle (R-CIB-1, F2):** `ChatMessageUtil.estimateTokens` (chars×2/7) — auch
   für den Compact-Input; `join/4` verworfen. Re-Estimate nach dem Join (`System.lineSeparator`)
   über `estimateTokens(String)` auf dem Join-Ergebnis.
4. **Dedup vor den Caps (R-CIB-2):** `LinkedHashSet` keep-first über **ungecappte** Renderings;
   SystemMessages vorher rausgefiltert; leere Renderings verworfen (wie IST). Duplikate zählen in
   die Disclosure („duplicates collapsed: N") — auch wenn Dedup allein unter Budget bringt (F4).
5. **Endstufe (R-CIB-4.4, F5):** `capChars = restTokens × 7 / 2 / n` (n ≥ 1 geclampt),
   `restTokens = budget − estimateTokens(Disclosure-Zeile + n Zeilentrenner)` (clamp ≥ 0), Cap via
   head-keep (`StringUtil.trimToLength`, Bestandskonvention) auf das **fertige Rendering**. Garantiert
   Terminierung: Summe ≤ n·capChars → estimate ≤ restTokens + Overhead = budget. Integer-Division
   floort → nie drüber.
6. **`CompactResult`-Record (Sketch):**
   ```java
   public record CompactResult(Status status, Stats stats, String cause) {
       public enum Status { COMPACTED, SKIPPED_SMALL, FAILED_EMPTY }
       public enum Stage { NONE, THINK_AND_USER, TOOL_RESULTS, PER_MESSAGE }
       public record Stats(int messageCount, int estimateBefore, int estimateAfter, Stage stage,
                           long droppedChars, int resultChars, String model, long millis) {}
       public static CompactResult compacted(Stats stats);
       public static CompactResult skippedSmall();
       public static CompactResult failedEmpty(Stats stats, String cause);
       /** Eine Zeile, zwei Empfänger (R-CIB-6): Log UND Tool-Result. Format:
           "compressed 61 messages ~114k → input ~40k, result 2.3k, stage: tool results 6000" */
       public String resultLine();
   }
   ```
   Caller-Anpassung (minimal, R27: jedes Inkrement kompiliert): `switch(x)` → `switch(x.status())`,
   `result == COMPACTED` → `result.status() == Status.COMPACTED`, Test-Override-Signaturen.
7. **Fail-Verhalten unverändert (R-CC-3):** Engine liefert FAILED_EMPTY; `monitor.onProblem` bleibt
   in `AbstractAgent.compact()` (Bestandstests decken es). `callBlocking == null` → weiter
   `IllegalStateException` (Log OR throw: throw im Call-Pfad, Result-Zeile ist kein Exception-Ersatz).
8. **Budget ≤ 0 (R-CIB-1):** Stager: nie kürzen, nur Entry-Log (einziger Log). Disclosure nur bei
   Kürzung.
9. **R-CC-8 — `MIN_COMPACT_MESSAGES` an EINER Stelle (core, Plugin-frei):** neue Konstanten-Klasse
   `org.sterl.llmpeon.compact.CompactConstants` mit `MIN_COMPACT_MESSAGES = 3`. Referenziert von
   allen drei Gates, keine verstreuten Literale:
   - **SKIPPED_SMALL-Guard** `AbstractAgent.compact`: `size() < MIN_COMPACT_MESSAGES` — Verhalten
     **unverändert** (nur Konstanten-Referenz, R-CC-8-Text).
   - **Hint-Gate** `ToolService.addCompactHintIfNeeded:199`: ersetzt `memory.size() < 10` durch
     `size() <= MIN_COMPACT_MESSAGES` → kein Hint. **Das ist eine SOLL-Verhaltensänderung**
     (Hint war bisher ab 10 Messages blockiert, SOLL: ab > 3 scharf — Doc-BDD). Inventar-Lektion
     R16: `ToolServiceCompactHintTest`-Seeds prüfen/anpassen; kollidiert der Wert mit bestehenden
     Seeds hart → STOP-AND-ASK.
   - **Auto-Gate** `AbstractAgent.doCall:265`: zusätzlich zur Token-Bedingung
     `memory.size() > MIN_COMPACT_MESSAGES` (hat heute **keinen** Messages-Check).
   - Wert-Übergang: bei genau 3 Messages skippt der Compact nicht (Guard `< 3`), aber Auto-Gate/Hint
     feuern nicht (`> 3`) — konsistent mit Doc.
10. **R-CC-9 — Compact-Reinsert-Marker (keine Rekursion):** neue Konstante
    `CompactConstants.REINSERT_MARKER = "Session compacted:"` — ersetzt das Inline-Literal in
    `AbstractAgent:324` (Re-Seed). Semantik:
    (a) **erkennbar** über die Konstante (wie COMPACT_HINT, ADR-0056.4);
    (b) **kein Re-Trigger:** nach Compact setzt R-CC-2-Reevaluate den Zähler auf die kleinen
    Reinsert-Messages + R-CC-8-Messages-Gate — kein Auto-Gate/Hint auf das frische Ergebnis (BDD-Test);
    (c) **keine Rekursiv-Komprimierung:** der `CompactStager` behandelt Reinsert-Messages
    (UserMessage mit REINSERT_MARKER **und** die Summary-AiMessage) als **State** — nie als
    „echter User-Text" (Ausschluss-Set um COMPACT_HINT, Queued-Marker, REINSERT_MARKER erweitern).
    Insert-Ordnung = Kontrakt in context-message-concept.md (SOLL, read-only).

## 3. Architektur

```mermaid
componentDiagram
    direction LR
    subgraph core
        AbstractAgent -->|compact(snapshot, budget, monitor)| CompactEngine
        CompactEngine -->|stage(messages, budget)| CompactStager
        CompactStager -->|render per Stufe| ChatMessageUtil
        CompactStager -->|estimateTokens ×2/7| ChatMessageUtil
        CompactEngine -->|callBlocking, COMPACT-Slot| ConfiguredChatModel
        CompactEngine --> CompactLog
    end
    subgraph plugin
        CompactSessionTool -->|agent.compact(monitor)| AbstractAgent
        AIChatView -->|agent.compact(monitor)| AbstractAgent
        PoDelegateTool -->|slave.compact(monitor)| AbstractAgent
        AiAgentStatusModel -->|status + Zahlen| CompactResult
    end
```

```mermaid
sequenceDiagram
    participant A as AbstractAgent.compact()
    participant E as CompactEngine
    participant S as CompactStager
    participant C as ConfiguredChatModel (COMPACT-Slot)
    A->>A: Guard memory.size() < 3 → SKIPPED_SMALL
    A->>E: compact(memory.getCopy(), budget, monitor)
    E->>E: Entry-Debug-Log (einmal: agent, messageCount, estimate, budget, thinkingEnabled)
    E->>S: stage(messages, budget)
    S->>S: Filter SystemMessages → Render (ungecappt) → Dedup (keep-first)
    S->>S: estimate > budget? → Stufe 1 (Think 9000 front, letzte echte User-Message) → Re-Estimate
    S->>S: noch drüber? → Stufe 2 (Tool-Results+Args+Think 6000) → Re-Estimate (warn)
    S->>S: noch drüber? → Endstufe (capChars = restTokens×7/2/n, head-keep) (error)
    S-->>E: (input, disclosure, stats)
    E->>C: ChatRequest(COMPRESS_SYSTEM + input)
    C-->>E: ChatResponse (null → throw; leerer Text → FAILED_EMPTY)
    E->>E: Result-Zeile: log (info/warn/error je Stufe) + CompactResult(stats)
    E-->>A: CompactResult
    A->>A: nur COMPACTED: memory.clear + re-seed ("Session compacted:" + Summary); FAILED_EMPTY → onProblem
```

Information Hiding: `AbstractAgent` kennt nur `CompactEngine.compact(...)` und das Record; der Stager
sieht keine Agent/Memory/Config-Typen (pure Liste + int). Der Plugin-Layer sieht nur
`AiAgent.compact()` + `CompactResult` — keine Stufen-Details.

## 4. Inkremente (klein, vertikal, je für sich grün; Surefire = Ground Truth)

| # | Deliverables | Tests (konkrete Namen) | Polarität |
|---|---|---|---|
| **2a** | `ChatMessageUtil`: `RenderOptions`-Record + `toString(msg, options)` + Think-Front-Cap (`…$`-Anker) + `perMessageTrimmedTag`-Flag + **SystemMessage-Render-Fix** (roter Test zuerst!) + `queuedMarker`-Präfix → Konstante in `UserMessageQueue` (oder AbstractAgent) für R-CIB-4-Ausschluss | `ChatMessageUtilTest`: `systemMessageIsRenderedNotDropped` (ZUERST rot), `defaultOverloadsUnchanged` (Charakterisierung aller 3 Signaturen), `thinkFrontCapKeepsTailWithAnchor`, `thinkFullByDefault`, `trimmedTagSuppressedInCompactMode`, `toolArgumentsCappedByToolMessageSize` | nur-hinzufügen (System-Fix ist benannter Bugfix) |
| **2b** | core-Package `org.sterl.llmpeon.compact`: `CompactStager`, `CompactEngine`, `CompactResult`-Record, `CompactLog`. Record-Compile-Fixes über Module (Prod: `AIChatView:493`, `AiAgentStatusModel.compactResult:70-77`, `PoDelegateTool:190`, `CompactSessionTool:29`; Tests: `CompactSessionToolTest` (2 Overrides), `HeaderRosterStructureTest` (Plugin!), `AbstractAgentCompactResultTest`, `AbstractAgentTest`, `AiPoAgentTest`, `AiAgentStatusModelTest` — minimal auf `status()` umbiegen). `AbstractAgent.compact` intern auf Record (Caller bleibt AiCompressorAgent) | **`CompactStagerTest`** (JUnit 5 + AssertJ, GIVEN/WHEN/THEN): `zeroBudgetMeansNoCap` (Doc-Name!), `underBudget_nothingIsTruncated`, `underBudget_noDisclosure`, `stage1_capsThinkingTo9000KeepingTheEnd`, `stage1_keepsOnlyLastRealUserMessageFull`, `stage1_earlierUserMessagesRenderStateOnly`, `stage1_hintIsNeverTreatedAsRealUserText`, `stage1_noUserMessageWithRealText_noUserReduction`, `stage2_capsToolResultsToolArgumentsAndThinkingTo6000`, `finalStage_capsEachMessageToRestTokensTimes7Over2OverN`, `finalStage_estimateStaysWithinBudget_guaranteed` (**Mutation: ×7/2/n oder ×2/7 mutieren → rot**), `finalStage_singleMessageLargerThanBudget_stillCompacts`, `dedup_keepsFirstOfIdenticalMessages`, `dedup_doesNotCollapseDifferentMessagesWithEqualHead` (**Mutation: Set→Substring → rot**), `dedupOnly_disclosesDuplicatesCollapsed`, `disclosure_appendedOnceAtEnd_whenCapped`, `reinsertedSummaryAndMarkerAreStateNeverRealUserText` (R-CC-9c: Reinsert im Ausschluss-Set). **`CompactEngineTest`** (MockLlmServer/StreamMock wie `AiCompressorAgentTest`): `entryDebugLogExactlyOnceWithInitialValues`, `zeroBudget_onlyEntryLog`, `resultLogLevelMatchesStage`, `emptyResponse_failedEmptyWithCauseAndNumbers`, `sendsSystemPromptAndDedupedInput` (Payload-Capture, AGENTS-DEV-Regel: beide Richtungen!) + migrierte Slot-Tests (`compactSlotRoutesCallToCompactConnection`, `emptyCompactSlotFallsBackToBaseConnection`, `compactSlotThinkReachesTheWire`, `compactSlotThinkAnthropicSendsThinkingBlock`, `compactSlotExtraBodyMergesUserWinsAndStripsReserved`) | add (+ modify Compile-Fixes) |
| **3** | Wiring: `AbstractAgent.compact()` → `CompactEngine` (Guard `< 3`, memory clear/re-seed, onProblem bleiben); Budget-Übergabe `getAutoCompactAfter()` (roh, R-CIB-1); ≤0-Guard; Entry-Log kommt live; Hint/Trigger unverändert. **R-CC-8:** `CompactConstants.MIN_COMPACT_MESSAGES` (core) + alle 3 Gate-Referenzen (SKIPPED_SMALL unverändert im Verhalten, Hint-Gate `<10`→`<= MIN` (SOLL-Verhaltensänderung, s. Entscheidung 9), Auto-Gate bekommt Messages-Check). **R-CC-9:** `REINSERT_MARKER`-Konstante ersetzt Inline-Literal `AbstractAgent:324`. **Q2-Verifikationsauftrag (Paul):** `LlmConfig.getAutoCompactAfter()`-Default + Gate-Verhalten bei ≤ 0 verifizieren; feuert der Auto-Gate bei ≤ 0 jeden Turn → STOP-AND-ASK an Paul, kein stiller Fix | `AbstractAgentCompactResultTest` + `AbstractAgentTest` grün (Bestand), neu: `compact_delegatesToEngineAndReseeds`, `compact_failedEmpty_onProblemKept`, `autoGateNeedsMoreThanMinCompactMessages` (R-CC-8), `reinsertedMessagesDoNotTriggerImmediateReCompact` (R-CC-9b), `compactWithZeroBudget_neverCaps` (Q2-Beweis falls Gate off); `ToolServiceCompactHintTest`: `hintNotBelowMinCompactMessages` (≤ 3 → kein Hint) + Seed-Inventar (Lektion R16: alte `<10`-Seeds); `AiCompressorAgentTest#test_compressContext` wandert als Agent-Integrationstest mit; `ToolServiceCompactResultTest` unverändert grün | modify (Alt-Pfad AiCompressorAgent bleibt parallel stehen, wird NICHT angefasst) |
| **4** | R6 + UI: `CompactSessionTool` COMPACTED-Zweig: `resultLine()` + preserve-Text ins Tool-Ergebnis (onTool-Zeile bleibt); `AiAgentStatusModel.compactResult` mit Zahlen (Statuszeile); `compressor.md` (core `src/main/resources/org/sterl/llmpeon/prompts/`): Disclosure-Zeile (`session truncated` + Cap-Zeile) erklärt; **Homepage-Update** (user-visible, AGENTS.md-Pflicht) | `CompactSessionToolTest`: `compactedToolResultCarriesStats`, `preserveTextKept` (Bestand, angepasst); `AiAgentStatusModelTest`: `compactResultCarriesNumbers`; Plugin-Test-Modul (JUnit 4, keine externen Assertion-Libs): `HeaderRosterStructureTest`, `PeonAiServiceTest` grün | add/modify |
| **5** | Cleanup (nur-löschen): `AiCompressorAgent` löschen (toText/Call — Doppel-Präfix-Bug stirbt mit), toten `toString(msg,false,4000)`-Pfad prüfen, `docs/adr/0030-statictext-helper-frozen-chatmessageutil.md` löschen (Landmine gefixt, ADR-0056 folgt), Grep-Check „AiCompressorAgent" = 0 Treffer | Bestandstests grün (Surefire-Kernzahlen), `AiCompressorAgentTest`-Rest (Slots) lebt in `CompactEngineTest` | nur-löschen |

**Slicing-Refinement (2a/2b statt ein Inkrement 2):** Der Stager (2b) braucht die Render-Modi (2a) —
ohne sie kann er keinen Stage-1-Input bauen. Beide sind core-only, je grün; 2a bleibt dabei im
„Core-Extraktion"-Umfang, nur in testbarer Reihenfolge.

## 5. Regeln & Constraints

- **AGENTS.md:** Log OR throw; Tool-Limits disclosed (hier: Disclosure-Zeile + Entry-Log); ein
  Verhalten eine Implementierung (Estimator ×2/7; Result-Zeile via `CompactResult.resultLine()` für
  Log UND Tool-Result); Clean Break (kein Migrationsschleife); Surefire = Test-Ground-Truth.
- **Test honesty (AGENTS-DEV):** Engine-Tests capturen den gesendeten Request-Payload (Dedup,
  Disclosure, Stufen) UND was beim Monitor ankommt — nie nur „ein Call kam".
- **Line-based edits:** nach jedem `*ReplaceLines`-Edit zurücklesen; größere Umbauten als Ganzfile-Write.
- **Namen/Konstanten:** `…$`-Marker, `COMPACT_HINT`, Queued-Marker, Disclosure-Zeilen-Format,
  `session truncated` — als benannte Konstanten, keine verstreuten Literale (R-CIB-4/ADR-0056.4).
- **Keine Änderung an:** Auto-Compact-Trigger (`doCall:265`, Hint `addCompactHintIfNeeded`),
  Live-Context-Render, ThreadSafeMemory-Logik (nur Reads), `compact-lock`-UI.
- **Thread-Safety:** `compact()` läuft im Job; Engine zustandsfrei je Call (wie IST) — kein
  Single-Thread-Annahme.
- Build auf dediziertem Branch, Commit nach jeder grünen Iteration inkl. Prompts/Homepage.
- **Git (Paul, 2026-09-24):** Workspace ist auf **main** — Da Mek legt den Zyklus-Branch
  **`story/compact-input-budget`** an. JEDE grüne Iteration wird committed **inkl. docs/** — die
  Docs-Änderungen von heute (compact.md, adr/0056, index.md, open-points, context-message-concept.md,
  memory.md) sind **uncommitted** und wandern mit dem ersten Inkrement-Commit. Lokalen Branch
  `fix/nextids-bug-report` löschen (remote in main, gelöscht). Finaler Merge/Squash = User-Entscheidung.

## 6. BDD-Abdeckung (docs/compact.md → Tests)

Alle 9 BDD-Blöcke aus docs/compact.md §BDD sind 1:1 auf `CompactStagerTest`/`CompactEngineTest`-
Namen in Inkrement 2b gemappt (siehe Tabelle); R-CIB-3 (Entry-Log) + Result-Zeile in
`CompactEngineTest`; `zeroBudgetMeansNoCap` trägt den Doc-Namen. **R-CC-8-BDD** (≤ 3 → kein Hint,
> 3 → scharf) → `ToolServiceCompactHintTest` + Auto-Gate-Test in Inkrement 3. **R-CC-9-BDD**
(Reinsert → kein Re-Trigger; Reinsert als State) → `reinsertedMessagesDoNotTriggerImmediateReCompact`
(Inkrement 3) + `reinsertedSummaryAndMarkerAreStateNeverRealUserText` (Inkrement 2b).
R-CC-1…6-Bestandstests
(`ThreadSafeMemoryInputTokenCountTest`, `ToolServiceCompactResultTest`, `ToolServiceCompactHintTest`,
`AbstractAgentCompactResultTest`, `ContextCounterDisplayTest`) bleiben unangetastet grün — nur
Record-Compile-Fixes wo Signaturen betroffen sind.

## 7. Schwellenwert-/Bestands-Testinventar (Lektion R16/R33)

- **4000er-Cap-Nutzer:** nur `AiCompressorAgent.toText:70` (Prod) — stirbt in Inkrement 5; Tests mit
  4000-Seeds: keine gefunden (Grep `4000` in core-tests: nur `ChatMessageUtilTest`-Schätzwerte ohne Cap-Bezug).
- **90000:** `ThreadSafeMemory.containsMessage:120` — bleibt unverändert.
- **60000/6000-Default:** `ChatMessageUtil.toString`-Default — Bestandstests
  (`ThreadSafeMemoryTest`, `FileAgentHistoryStoreTest`, `StreamMock`, `AbstractUnitTest`,
  `AbstractAgentTest`, `AiDeveloperAgentTest`, `PeonAiServiceTest`) asserten nur contains — 2a
  charakterisiert die Defaults per Test (`defaultOverloadsUnchanged`), um Regressionen sofort zu sehen.
- **Enum→Record-Signaturen:** `CompactSessionToolTest` (2 Overrides), `HeaderRosterStructureTest`
  (Plugin-Modul!), `AiAgentStatusModelTest`, `AbstractAgentCompactResultTest`, `AbstractAgentTest`,
  `AiPoAgentTest:232`, `PeonAiServiceTest` — alle in 2b mit min. Compile-Fix genannt.

## 8. Offene Fragen

- keine blockierenden — Q1 („letzter TextContent") und Q2 (Verifikationsauftrag ≤ 0) von Paul
  entschieden (2026-09-24).
- **R-CC-8 ⏳ (Pauls Randnotiz „denke ich"):** so bauen wie im Doc (> 3); nur bei hartem
  Widerspruch am Code (Guard-Grenze kollidiert mit bestehenden Test-Seeds, insb.
  `ToolServiceCompactHintTest`-Seeds und der `< 3`-SKIPPED_SMALL-Guard bei genau 3 Messages)
  STOP-AND-ASK an Paul.
- ADR-0056-Consequence „staticText()-Helper in PeonAiService migrieren" ist gegenstandslos
  (Helper existiert nicht mehr, Evidenz f) — kein Arbeitsschritt.

## 9. Status

Plan **freigegeben** (Da Boss, 2026-09-24, nach Q1/Q2-Entscheid + R-CC-8/9 + Git-Regeln).
Start: Branch `story/compact-input-budget` → Inkrement 2a (Render-Modi, SystemMessage-Fix roter
Test zuerst), dann 2b, 3 (inkl. R-CC-8/9 + Q2-Verifikation), 4, 5.
