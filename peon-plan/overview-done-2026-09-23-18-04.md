# Mini-Zyklus: Hotfix „Ehrlicher Compact" (2026-09-23, v2 — R-CC-5 gestrichen)

> **⛔ STOP-AND-ASK (prominent, gilt je Inkrement):** Bei Compile-Fehlern ohne Lösung,
> nicht-grün-bekommenden Tests, IST-Widersprüchen zum Plan oder Unklarheiten: **STOPP und aktiv bei
> Jon (askDev-Kanal) nachfragen** — nie still workarounden, nie SOLL ändern. Fachliche Lücke, die
> keine Regel beantwortet → STOP-AND-ASK, nie selbst füllen.
> **Test-First (Paul):** JEDE Regel zuerst ein isolierter roter Core-Test (JUnit 5 + AssertJ,
> GIVEN/WHEN/THEN) — der RED-Beweis wird VOR dem Fix gezeigt. Ein Test, der vor dem Fix schon grün
> ist, ist kein Test — bei grünen Vor-Zuständen: STOP-AND-ASK.

## 1. Kontext

SOLL steht 1:1 in **`docs/compact-context-counter.md`** (Regeln **R-CC-1, R-CC-2, R-CC-3, R-CC-4,
R-CC-6** — R-CC-5 existiert nicht mehr, Nummerierung bleibt mit Lücke, je Regel BDD + Testname) +
**`docs/adr/0055-context-counter-input-not-cost.md`**. Hotfix für Paul — drei beobachtete Fälle,
gemeinsame Wurzel: `totalTokenUsed` trägt zwei inkompatible Werte (Kosten vs. Kontextgröße),
`compact()` meldet zwei grundverschiedene Fälle als dasselbe `false`, der Compact-Hint feuert pro
Tool-Runde neu.

**Plan-Entscheidung (Paul, 2026-09-23):** R-CC-5 (Dedup-Blindstelle 6000/90000) **ersatzlos
gestrichen** — Da Mek hat den Pfad verifiziert: `ChatMessageUtil.toString` trunciert nur
Ai-Tool-Args + ToolResults (ChatMessageUtil.java:120/129), UserMessage-Text NIE;
`containsUserMessage` prüft pro Item volltextlich über die gesamte Historie. Die beobachtete
Aufsummierung ist gecauset durch den Workspace-Memory-Hash-Key (ADR-0032: jede memoryAdd → neue
Vollkopie bis zum Compact) — **by design**, als ❓ in `docs/open-points.md` geparkt, OUT of scope.
Mehrere AGENTS.md-Kopien in EINEM Slave-Memory sind im IST-Code nicht produzierbar — bei
wiederholter Beobachtung: Evidenz von Paul, kein Code-Fix ins Blaue.

**Harte Rahmenbedingungen (Paul):**
- Branch **`analysis/tool-evolution`** (läuft). **Genau EIN Commit am Ende (I5)** — inkl.
  `docs/**` (compact-context-counter.md, adr/0055) und der Plan-Datei. Kein Zwischen-Commit.
- Core-Tests isoliert (JUnit 5 + AssertJ); nur I1 hat Plugin-Bedarf (Interface-Change).
- Gate je Inkrement: **Maven Surefire (artifactId `llmpeon-core`) = Ground Truth** (Zahlen aus
  Surefire, nicht Eclipse-Runner). I1 zusätzlich: `eclipseBuildProject` + Plugin-JUnit.
- **Keine Scope-Erweiterung:** OUT = WHY der Compressor leeren Text liefert (braucht Pauls
  Error-Log), Compressor-Input-Budget (`compact-input-budget.md` R1–R5), Anthropic `cache_read`
  Pitfall (ADR-0055, separater Punkt), Workspace-Memory-Snapshot-Aufsummierung (ADR-0032,
  open-points.md).
- **`planImplemented` nur NACH bestandenem Jon-Review** — und dann vom Dev-Agent (nie Da Thinka).
  I5-Commit erst nach dem Review.
- Baseline vor I1: Surefire-Zahlen (Tests/Failures) notieren — Referenz fürs Abschluss-Gate.
- Regel-Kommentar am Test = reine ID (z. B. `// R-CC-3`).
- Workspace-Hinweis: der Core-Modul liegt unter **zwei** Eclipse-Projekt-Ansichten desselben
  Ordners (`llmpeon-core` und `org.sterl.llmpeon.core`). Editieren über
  `org.sterl.llmpeon.core/src/main/java/…` (kanonisch), Maven-Artefakt = `llmpeon-core`.

## 2. Verifizierte IST-Fakten (2026-09-23, Da Thinka — per Grep/Read)

### `compact()`-Aufrufstellen (komplett inventarisiert — Interface-Change!)

**Produktion:**
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/AiAgent.java:26` —
  `boolean compact(AiMonitor monitor)` (Interface, **abstrakt** — jede Implementierung betroffen).
- `…/agent/AbstractAgent.java:297-332` — Implementierung: CAS `working` :300, `size()<3 → return
  false` :304, `monitor = AiMonitor.nullSafety(monitor)` :306 (nach dem Guard),
  `AiCompressorAgent(configuredModel).call(memory.getCopy(), monitor)` :307-308,
  **leer-Check :310-312 nur `log.warn("Empty compact message received for " + getName())` +
  `return false`**, clear+re-seed :315-326, `return true` :328.
- `…/agent/AbstractAgent.java:265-268` — **stilles Auto-Compact in `doCall`**:
  `if (compactAfterTokens() < memory.getTotalTokenUsed()) { onTool("Auto Compact …"); compact(monitor); }`
  — **Returnwert ignoriert**.
- `…/poagent/tools/PoDelegateTool.java:188-196` — `if (!slave.compact(monitor)) return "Nothing to
  compact (" + size + " messages)"`, sonst `"… compacted. " + contextUsed(slave)`.
- `…/tool/tools/CompactSessionTool.java:29-44` — `var compactDone = agent.compact(monitor)`;
  false → `onTool("Compact called but skipped because of small context …")` +
  `"Not needed only " + size + " message in context"` (bei leerem Compressor = **Lüge**).

**Plugin (nur I1):**
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java:523-524` — ActionBar-Button:
  `var result = active.compact(this); if (result) refreshChat` (Pre-Guard `size()<3` :515 bleibt).
- `AIChatView.java:549-551, 561` — Roster-Slave: `boolean result = false; result = agent.compact(this);`
  (Capture vor `monitorRef`-Reset — Async-State-Safety, bleibt) →
  `AiAgentStatusModel.compactResult(result, slave.uiName())`.
- `AiAgentStatusModel` ist **CORE** (`org.sterl.llmpeon.core/…/agent/AiAgentStatusModel.java`):
  `compactResult(boolean, String)` :65-67 („Compacted X" / „Nothing to compact");
  Roster-Label :54: `uiName + " (" + StringUtil.toK(tokens) + ")"`, Snapshot :40
  `agent.getMemory().getTotalTokenUsed()`.

**Tests (Compile-betroffen, je Call-Site verifiziert):**
- Core `AbstractAgentTest.java`: :436 `assertThat(first).isTrue()`, :440
  `assertThat(second).isFalse()`, :470 `assertThat(compacted).isTrue()` → Enum; Call-Sites
  :404, :429, :433, :495, :590, :644, :673, :708, :1003 ignorieren den Return (kein Impact).
- Core `AiPoAgentTest.java:235` `assertThat(compacted).isFalse()` → Enum; :197 ignoriert.
- Core `AiCompressorAgentTest.java:72` — ignoriert (kein Impact).
- Core `CompactSessionToolTest.java` — **zwei anonyme AiAgent-Implementierungen** mit
  `@Override public boolean compact(AiMonitor)` :122 und :188 (`compactStub` :183-215) →
  Signature + Return `CompactResult.COMPACTED`; :232-268 Result-Text-Tests bleiben grün
  (kompaktierter Pfad unverändert).
- Core `AiAgentStatusModelTest.java:112-115` — `compactResult(true/false, "Da Mek")` → Enum.
- Plugin `org.sterl.llmpeon.test/…/HeaderRosterStructureTest.java:103` —
  `@Override public boolean compact(AiMonitor monitor) { return false; }` →
  `CompactResult compact(…) { return CompactResult.SKIPPED_SMALL; }`.
- Plugin `PeonAiServiceTest.java` — 13× `compact(null)` (Return ignoriert :303, :378, :406, :1067,
  :1100, :1113, :1239, :1282, :1321, :1359, :1538, :1571, :1616) → **kein** Compile-Impact.

### Zähler-Fakten (`ThreadSafeMemory.java`, `…/memory/`)

- Schreibstellen von `totalTokenUsed` (alle!): Konstruktor :42 (Estimate via
  `getTokenCount(null, …)`), `add()` :55 (`+= estimateTokens`), `addResult(…, toolResult)` :163 und
  `addResult(response)` :170 (**Replace** via `getTokenCount(response, memory)`),
  `reevaluateTokens()` :152-154 (Estimate), `clear()` :122 (→ 0), `replaceAll()` :129 (→ 0).
- `ChatMessageUtil.java` (`…/shared/`): `getTokenCount` :20-27 nutzt **`totalTokenCount()`**
  (Fallback Estimate) — **einzige** Call-Sites = ThreadSafeMemory :42, :163, :170 (Grep,
  workspace-weit).
- `TokenUsage` (langchain4j, Source verifiziert): `inputTokenCount()`/`totalTokenCount()` →
  `Integer` (nullable).
- ⚠️ **Dedup-Blindstelle (ehem. R-CC-5) — NICHT vorhanden (gestrichen):** `ChatMessageUtil.toString
  (msg, includeThink, toolMessageSize)` :95-133 trunciert den Parameter **nur** für
  AiMessage-Tool-Args und ToolExecutionResult-Text — UserMessage-Text NIE. `containsUserMessage`
  :92-98 prüft volltextlich über die gesamte Historie. (Verifiziert 2026-09-23, Da Mek.)
- `renderTurnContext` (static, `AbstractAgent.java:407-435`): :420
  `containsUserMessage(key)` (keyed), :423 `containsMessage(rendered)` (fallback).

### Tool-Loop-Fakten (`ToolService.java`, `…/tool/`)

- `executeLoop` :120-187: `runAllTools` :157 **vor** `addResult` :158 → **2× compactSession im
  selben Turn möglich** (zweite sieht post-compact Memory, size<3 → muss ehrlich SKIPPED_SMALL
  sein, kein onProblem). `ranTool(response, CompactSessionTool.NAME)` :161 →
  `reevaluateTokens()` + Static-Rebuild :162-166 **bedingungslos** (auch bei Fehlschlag — der Bug),
  sonst `addCompactHintIfNeeded(req, response, false)` :167. Forced-Hint :181
  (`stuck > MAX_STUCK_ITERATIONS - 2`).
- `addCompactHintIfNeeded` :194-214: Guards size<10 :196, `0.95`-Schwelle :199,
  `COMPACT_HINT` :49-52 (private, startet mit „CONTEXT LIMIT WARNING: …"), ohne Compact-Tool →
  „cannot be compacted"-Message :202-206, sonst onTool „🗜 Compact hint …" +
  `req.addMessage(new UserMessage(COMPACT_HINT + ls + used))` :211-212. **Kein Dedup.**
- `ToolLoopRequest` (`…/tool/ToolLoopRequest.java`): Builder-POJO, **frisch pro Turn**
  (AbstractAgent `doCall` :279-290 baut neu) → kein Leck zwischen Turns.
- `PoDelegateTool.contextUsed` :256-258:
  `"Context: " + getTotalTokenUsed() + " token - " + tokenContextUsedInPercent() + "% used."`
- `AbstractAgent.tokenContextUsedInPercent` :156-160 — **einziger** Production-Caller
  PoDelegateTool:257 (Grep); Roster zeigt `toK(tokens)` (AiAgentStatusModel:54), nicht Prozent.
- `PoDelegateToolTest.java` :64, :81, :91, :236 — Pattern `Context: \d+ token - \d+% used\.`
  (bricht nach I4 → wird bewusst verschärft, s. I4).

## 3. Design-Entscheidungen

- **D1 — `CompactResult`-Enum:** neue Top-Level-Datei
  `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/CompactResult.java` mit
  `COMPACTED / SKIPPED_SMALL / FAILED_EMPTY` (Javadoc je Konstante: was sie heißt, dass
  SKIPPED_SMALL legitim ist, FAILED_EMPTY ein Fehler). Top-Level statt nested: einfachster
  Import an allen Call-Sites.
- **D2 — `onProblem` an EINER Stelle** (One Behaviour, One Implementation): in
  `AbstractAgent.compact()` beim leer-Check:
  `monitor.onProblem("Compact failed: compressor returned no summary for " + getName())`
  (exakter SOLL-Wortlaut) + `log.warn` bleibt (Pauls Root-Cause-Log). Alle drei Stellen
  (Tool-Call, UI-Button, Auto-Compact) rufen `compact(monitor)` mit ihrem jeweils lebenden
  Monitor → alle drei empfangen die Meldung. `monitor = AiMonitor.nullSafety(monitor)` in den
  try-Block-Vorspann ziehen (vor `<3`-Guard) damit der onProblem-Pfad nie null hat.
  → **doCall :265-268 braucht KEINE Änderung**: Auto-Compact ist dadurch nicht mehr still, und
  das Retry (R-CC-3-BDD-3) entsteht strukturell (Memory + Zähler unverändert → Gate feuert
  nächsten Turn neu).
- **D3 — „Compact hat kompaktiert" als sticky Flag auf `ToolLoopRequest`:**
  `private boolean compactedThisTurn;` + `markCompacted()` + `isCompactedThisTurn()`
  (Javadoc: von CompactSessionTool gesetzt bei COMPACTED, von executeLoop für R-CC-2 gelesen;
  frisch pro Turn). Sticky-OR (nicht „letztes Result"): 2× compactSession im selben Turn
  (erstes COMPACTED, zweites SKIPPED_SMALL) → Flag bleibt true → exakt ein re-derive.
  CompactSessionTool setzt es nach `agent.compact(monitor)` bei COMPACTED.
- **D4 — Ein Wortlaut für FAILED_EMPTY:** Tool-Return (CompactSessionTool + PoDelegateTool) =
  derselbe Satz wie onProblem („Compact failed: compressor returned no summary for `<agent>`") —
  keine erfundene zweite Wortwahl. UI-Statusline (AiAgentStatusModel, UI-Detail, nicht im SOLL,
  PO-Review): `FAILED_EMPTY → "Compact failed: no summary for " + uiName`.
- **D5 — R-CC-1:** `ChatMessageUtil.getTokenCount` :20-27 → `inputTokenCount() != null` dann
  Input, sonst Estimate (Fallback bleibt). **Replace-Semantik** von `addResult` bleibt (Counter =
  Größe des letzten echten Prompts; incrementieren würde den Zähler ohne Oberschranke treiben).
  Einzige Call-Sites = ThreadSafeMemory → Change ist lokal. Javadoc: Kontextgröße-Semantik
  (R-CC-1, ADR-0055), `totalTokenCount()` (Kosten) fließt nie herein.
- **D6 — R-CC-6-Flag in `ThreadSafeMemory`:** `private volatile boolean tokenIsEstimate` +
  `public boolean isTokenEstimate()`. Semantik: „aktueller Wert enthält Estimate-Komponente".
  Schreibstellen: Konstruktor(store)→true, `add()`→true, `addResult`→
  (Provider-Input vorhanden ? false : true), `reevaluateTokens()`→true, `clear()`/`replaceAll()`→false
  (0 ist exakt).
- **D7 — Eine Format-Stelle:** `StringUtil.estimateAware(boolean isEstimate, String value)` →
  `isEstimate ? "~" + value + " (estimate)" : value` (SOLL-Format `~N (estimate)`). Verwendet von
  `PoDelegateTool.contextUsed` (roher Wert) und `AiAgentStatusModel` (toK-Wert:
  `Row` + `boolean estimate`, `rows()` liest `isTokenEstimate()`, `build()` rendert
  `uiName + " (" + estimateAware(estimate, toK(tokens)) + ")"`). Prozent (int) bleibt int —
  die Tilde am Zähler ist die Disclosure.
- **D8 — R-CC-4-Dedup an einer Stelle:** in `addCompactHintIfNeeded` im Hint-Add-Branch
  (else, :207) vor `addMessage`: `if (memory.containsMessage(COMPACT_HINT)) return;` —
  covered beide Pfade (normal + forced :181, gleiche Methode), und nach erfolgreichem Compact
  ist die Memory geleert → Hint automatisch wieder scharf (SOLL).
- **D9 — R-CC-5 streichen (Plan-Entscheidung Paul 2026-09-23):** kein Code, kein Test, keine
  Regel. Workspace-Memory-Aufsummierung = ADR-0032 by design → `docs/open-points.md` ❓.

## 4. Inkremente (I1 → I5, je für sich kompilierend + grün)

### I1 — R-CC-3 + R-CC-2 (Core + Plugin-Compile) — ✅ DONE (2026-09-23): RED-Beweise R-CC-2 (`expected 260000 but was 52`) + R-CC-3 (onProblem null / compactCalls 1≠2); Gate: Surefire 924/0/0/0, Plugin-Build grün, Plugin-Suite 279/0/0

**Schritt 1 — roter R-CC-2-Test (kompiliert gegen den IST, noch KEIN Code-Change):**
- NEU `…/src/test/java/org/sterl/llmpeon/tool/ToolServiceCompactResultTest.java`
  `#keepsRealCounterOnFailedCompact` (`// R-CC-2`):
  - GIVEN `AiDevAgent` mit streamMock; Memory ≥ 3 kleine Messages, Zähler vorab via
    `memory.addResult(response with TokenUsage(260000, 0, 260000), List.of())` auf 260000
    (Pre-Compact-Kontext, SOLL-GIVEN); Compact-Model liefert **leeren** AiMessage (→ FAILED_EMPTY).
  - streamMock Haupt-Model (AtomicInteger-Counter):
    - **Call 1:** Tool-Request `compactSession` mit `tokenUsage = TokenUsage(260000, 0, 260000)`
      (input==total: Wert vor UND nach I2 identisch — I2-unkritisch). **Pflicht** (nicht
      optional): ohne Usage würde `addResult` :158 selbst schon auf Estimate fallen → rot aus
      dem falschen Grund (würde `addResult`-Fallback testen statt dem Re-Derive).
    - **Call 2:** flippt das Cancel-Flag (AtomicBoolean) **bevor** die (irgendeine) Text-Response
      geliefert wird → `executeLoop` bricht an :145 (`isCanceled`) → `addResult` für Call 2
      passiert **nie**.
  - WHEN `toolService.executeLoop(req)` (req.agent = Agent, req.monitor = der cancel-fähige
    Monitor);
  - THEN `memory.getTotalTokenUsed() == 260000`.
  - **ROT heute:** Call-1-Runde: `addResult(r1, tR)` :158 schreibt 260000, dann re-derive
    :161-162 **bedingungslos** → Zähler fällt auf den Estimate der kleinen Memory.
  - **Testdesign-Begründung (Abnahme 2026-09-23, SOLL unverändert):**
    1. Der unterscheidbare Pfad ist das **Re-Derive** (:162), nicht `addResult` — beide Pfade
       schreiben via `addResult(r1)` denselben Wert (260000); nur das Re-Derive existiert im BUG.
       Damit der Test exakt das Re-Derive pinnt, darf KEINE spätere `addResult` es überdecken:
       der Loop muss in der Compact-Runde enden. Monitor-Cancel nach Call 1 nutzt den vorhandenen
       Break-Pfad :145 — deterministisch, kein SUT-Code, keine Sleeps. (Ohne Cancel würde die
       `addResult` der finalen Text-Response :171 den Endwert in BEIDEN Pfade identisch
       überdecken → Test grün heute, kein Test.)
    2. `== 260000` statt `isGreaterThan(Schwelle)`: exakter SOLL-Wortlaut („bleibt 260000");
       Replace-Semantik macht den Endwert exakt deterministisch; der Estimate der kleinen Memory
       liegt provabel weit darunter → kein Flake-Fenster. `isGreaterThan` wäre schwächer (jede
       Schreibstelle > Schwelle bliebe grün) und dupliziere die Gate-Folge, die
       `#autoCompactRetriesAfterFailure` bereits testet.
- **RED-Beweis dokumentieren** (Surefire-Auszug), dann:

**Schritt 2 — mechanisches Skelett (API-Change, Verhalten der alten Pfade unverändert):**
- NEU `CompactResult.java` (D1).
- `AiAgent.java:26` → `CompactResult compact(AiMonitor monitor)` + Javadoc (3 Werte, SKIPPED_SMALL
  legitim, FAILED_EMPTY = Fehler + onProblem).
- `AbstractAgent.compact` :304→`return CompactResult.SKIPPED_SMALL;`, :310-312→
  `log.warn` (bleibt) + `return CompactResult.FAILED_EMPTY;` (**noch ohne** onProblem),
  :328→`return CompactResult.COMPACTED;`.
- `PoDelegateTool.compact` :188-196 → switch: COMPACTED→wie heute; SKIPPED_SMALL→
  „Nothing to compact (N messages)" (alt); FAILED_EMPTY→D4-Wortlaut.
- `CompactSessionTool.compactSession` :29-44 → branch: COMPACTED→alt + `request.markCompacted()`;
  SKIPPED_SMALL→„Not needed only N message in context" (alt); FAILED_EMPTY→D4-Wortlaut
  (onProblem kommt in Schritt 4).
- `ToolLoopRequest` → Flag (D3, noch ungelesen).
- Plugin: `AIChatView` :523-524 (`== CompactResult.COMPACTED`), :549-551
  (`CompactResult result = null`), :561; `AiAgentStatusModel.compactResult(CompactResult, String)`
  (switch, D4-FAILED_EMPTY-Text).
- Test-Compile-Fixes (Inventory §2): AbstractAgentTest :436/:440/:470 →
  `isEqualTo(CompactResult.COMPACTED)` / `SKIPPED_SMALL`; AiPoAgentTest :235 → `SKIPPED_SMALL`;
  CompactSessionToolTest :122/:188 → `CompactResult compact(…) { return CompactResult.COMPACTED; }`;
  AiAgentStatusModelTest :114-115 → Enum; HeaderRosterStructureTest :103 → `SKIPPED_SMALL`.
- Gate 2: Surefire grün (Verhalten unverändert) + `eclipseBuildProject` (Plugin + Test-Modul) grün.

**Schritt 3 — rote R-CC-3-Tests:**
- NEU `…/src/test/java/org/sterl/llmpeon/agent/AbstractAgentCompactResultTest.java`
  (Muster: AbstractAgentTest + streamMock):
  1. `#skipsSmallContextHonestly` (`// R-CC-3`) — 2 Messages, capturing Monitor
     (AtomicReference-Lambda) → `SKIPPED_SMALL` **und** onProblem **nicht** gefeuert.
     (Pin — nach dem Skelett grün, darf nicht rot werden.)
  2. `#emptyCompressorIsFailedNotEmptyNeeded` (`// R-CC-3`) — ≥ 3 Messages, Compressor-Mock
     leeren Text → `FAILED_EMPTY` **und** onProblem == „Compact failed: compressor returned no
     summary for `<getName()>`". **ROT** (Skelett: kein onProblem).
  3. `#autoCompactRetriesAfterFailure` (`// R-CC-3`) — `autoCompactAfter` klein setzen,
     Memory über Schwelle (addResult mit Usage), Compressor leer → `doCall`-Turn 1: FAILED_EMPTY
     + onProblem gefeuert; Turn 2: Compressor wird **erneut** aufgerufen
     (`streamMock.getCallCount() == 2`). **ROT** (heute: nach fehlgeschlagenem Compact
     re-deriverter Zähler → Gate zu → kein Retry; wird grün erst mit dem R-CC-2-Fix).
- **RED-Beweis dokumentieren.**

**Schritt 4 — Fix:**
- `AbstractAgent.compact` :310-312 → `monitor.onProblem("Compact failed: compressor returned no
  summary for " + getName())` (D2; nullSafety-Verschiebung).
- `ToolService.executeLoop` :161-167 →
  `if (ranTool(response, CompactSessionTool.NAME)) { if (req.isCompactedThisTurn()) { reevaluate +
  Static-Rebuild } /* SKIPPED_SMALL/FAILED_EMPTY: echter Zählerwert bleibt (R-CC-2) */ }
  else addCompactHintIfNeeded(req, response, false);` (D3)
- Gate I1: Surefire grün + Plugin-Build + Plugin-Suite grün (1. Lauf: Workspace-Trust-Bestätigung
  im UI nötig — Memory; Suite komplett starten, nicht parallel nachstarten).

### I2 — R-CC-1 (nur Core) — ✅ DONE (2026-09-23): RED-Beweis (`expected 1000 but was 6000`); Fix `ChatMessageUtil.getTokenCount` → `inputTokenCount()` (D5, + Javadoc ADR-0055); Pin `fallsBackToEstimate` grün; `ThreadSafeMemoryTest:95` Cost-Pin 9100→9000 (Input-Semantik, erlaubt); Gate: Surefire 926/0/0/0

1. **ROT** — NEU `…/memory/ThreadSafeMemoryInputTokenCountTest.java`:
   - `#countsInputNotTotal` (`// R-CC-1`): frische Memory;
     `addResult(ChatResponse mit TokenUsage(1000, 5000, 6000), List.of())` →
     `totalTokenUsed == 1000` (nicht 6000). **ROT heute** (totalTokenCount).
   - `#fallsBackToEstimate` (`// R-CC-1`): Response **ohne** TokenUsage →
     Estimate chars×2/7 (Pin — heute schon grün).
   - RED-Beweis dokumentieren.
2. **Fix** — `ChatMessageUtil.getTokenCount` :20-27 (D5) + Javadoc.
3. Gate I2: Surefire grün. Bestehende Tests, die über `addResult` mit Usage laufen und den
   Zähler asserten: bei Rot **Testintention prüfen** — nur Assertionen, die die Kosten-Semantik
   einkodiert haben, dürfen angepasst werden (im Zweifel STOP-AND-ASK).

### I3 — R-CC-4 (nur Core) — ✅ DONE (2026-09-23): RED-Beweis `hintIsAddedOnce` (`Expected size: 1 but was: 2` — zwei identische CONTEXT-LIMIT-WARNING-UserMessages); Pin `hintReappearsAfterSuccessfulCompact` grün (Compact via agent.getMemory() — req-Memory muss die Agent-Memory sein, sonst SKIPPED_SMALL); Fix `addCompactHintIfNeeded` :211-212 `containsMessage(COMPACT_HINT)`-Dedup (D8); Gate: Surefire 928/0/0/0

1. **ROT** — NEU `…/tool/ToolServiceCompactHintTest.java`:
   - `#hintIsAddedOnce` (`// R-CC-4`): Memory ≥ 10 Messages, Zähler > 0.95 ×
     `autoCompactAfter` (via `addResult` mit Usage — deterministisch, keine Zeit); streamMock-
     Funktion (AtomicInteger-Counter) liefert **zwei** Tool-Requests (unbekannter Tool-Name,
     z. B. „probe" → ehrliche „unknown tool"-Result-Runde) und dann Text →
     `executeLoop` → UserMessages in Memory, die „CONTEXT LIMIT WARNING" enthalten, == **1**
     (beide Richtungen: Memory-Inhalt + onTool-Zeile „🗜 Compact hint" genau 1×).
     **ROT heute** (Hint pro Runde → 2×).
   - `#hintReappearsAfterSuccessfulCompact` (`// R-CC-4`): nach erfolgreichem Compact
     (Memory gecleart, „Session compacted"-Marker) wieder über Schwelle → Hint erscheint
     erneut (1× in der NEUEN Memory) (Pin).
   - RED-Beweis dokumentieren.
2. **Fix** — `addCompactHintIfNeeded` :207 (else-Branch) vor `addMessage`:
   `if (memory.containsMessage(COMPACT_HINT)) return;` (D8).
3. Gate I3: Surefire grün.

### I4 — R-CC-6 (nur Core) — ✅ DONE (2026-09-23): RED-Beweis = Compile-Rot (7 Errors: `isTokenEstimate()` ×5, `Row`-Konstruktor ×2 — neue API existiert noch nicht, vom Plan akzeptiert); Fix: `ThreadSafeMemory.tokenIsEstimate` + `isTokenEstimate()` + alle 7 Schreibstellen (D6), `StringUtil.estimateAware` (D7), `PoDelegateTool.contextUsed` + `AiAgentStatusModel` (Row+estimate, build via estimateAware); `PoDelegateToolTest` :64/:81/:91/:236 verschärft auf `Context: ~\d+ \(estimate\) token - \d+% used\.` (Wiring verifiziert — Fail-Output zeigte exakt `~11 (estimate)`); Gate: Surefire 929/0/0/0

1. **ROT** — NEU `…/agent/ContextCounterDisplayTest.java` `#estimateIsDisclosed` (`// R-CC-6`)
   (Rot = **Compile-Rot**, neue API existiert noch nicht — als Rot-Beweis akzeptiert):
   - `ThreadSafeMemory`: `add(msg)` → `isTokenEstimate() == true` →
     `StringUtil.estimateAware(true, "34210")` == `~34210 (estimate)`;
     `addResult(Usage input=1234)` → `false` → `1234` (keine Tilde);
     `reevaluateTokens()` → `true` wieder.
   - `AiAgentStatusModel.build`: Row mit estimate → `Da Mek (~15k (estimate))`;
     ohne → `Da Mek (15k)` (Label-Format SOLL: `~N (estimate)`).
2. **Fix** —
   - `ThreadSafeMemory`: Flag + `isTokenEstimate()` + alle 6 Schreibstellen (§2, D6).
   - `StringUtil.estimateAware` (D7).
   - `PoDelegateTool.contextUsed` :256-258 →
     `"Context: " + StringUtil.estimateAware(mem.isTokenEstimate(),
     String.valueOf(mem.getTotalTokenUsed())) + " token - " + agent.tokenContextUsedInPercent()
     + "% used."`
   - `AiAgentStatusModel`: `Row` + `boolean estimate`, `rows()` :40 liest
     `isTokenEstimate()`, `build()` :54 rendert via `estimateAware` (D7).
   - **`PoDelegateToolTest` :64, :81, :91, :236** — Patterns brechen (Memory dort = rein
     Estimate-basiert) → **verschärfen** auf `Context: ~\d+ (estimate) token - \d+% used\.`
     (der verschärfte Assert IS die Wiring-Verifikation).
3. Gate I4: Surefire grün.

### I5 — Commit (erst NACH bestandenem Jon-Review)

- **EIN** Commit auf `analysis/tool-evolution` mit: allen Code-/Test-Dateien **und**
  `docs/compact-context-counter.md` + `docs/adr/0055-…` + `peon-plan/overview.md`.
- Doc-Status `❌ specified` **bleibt** — Flip = PO-Aufgabe (Hauskonvention).
- Abschluss-Evidenz für Jon: Surefire-Zahlen (vorher/hinterher), Plugin-Suite-Status, RED-
  Beweise je Regel. `planImplemented` ruft der **Dev-Agent** erst nach PO-Review auf
  (nie Da Thinka).

## 5. Regeln & Constraints

- STOP-AND-ASK-Banner gilt je Inkrement (oben). SOLL = Docs; nichts erfinden;
  Wortlaut-Änderungen = STOP-AND-ASK.
- Ein Wortlaut je Verhalten (D2/D4/D7); keine Aliase, keine Backwards-Compat
  (`boolean compact` verschwindet vollständig — Clean Break).
- Kein Scope-OUT-Topic anfassen (Compressor-Root-Cause, Input-Budget, cache_read,
  Workspace-Memory-Snapshot/ADR-0032).
- Regeldoc-Nummerung bleibt mit Lücke bei R-CC-6 — **nicht** nachnummerieren (Doc + ADR-0055
  referenzieren die finale Nummerierung).
- Kern-Gate immer **Surefire** (llmpeon-core); Eclipse-Runner-Zahlen keine Referenz.
- `eclipseReplaceLines`/`diskReplaceLines`-Falle: nach jeder zeilenbasierten Edit den Bereich
  re-lesen; größere Umbauten als Ganz-File-Write (AGENTS.md).
- Plugin-Änderungen nur in I1; I2–I4 dürfen kein Plugin kompilieren-müssen
  (`eclipseBuildProject` als Billig-Check ok).
- Kein Git-Zwischen-Commit; Branch wechseln nur auf Ansage.

## 6. BDD-Akzeptanz (SOLL-IDs: R-CC-1 … R-CC-4 + R-CC-6 — das Doc trägt keine UC-IDs)

| SOLL-Regel | BDD (Kurzfassung, Wortlaut im Doc) | Test (Name exakt aus SOLL) | Increment |
|---|---|---|---|
| R-CC-1 | inputTokenCount=1000/total=6000 → Zähler wächst um 1000; ohne Usage → Estimate | `ThreadSafeMemoryInputTokenCountTest#countsInputNotTotal`, `#fallsBackToEstimate` | I2 |
| R-CC-2 | Zähler 260000 + fehlgeschlagener Compact → bleibt 260000, Gate scharf | `ToolServiceCompactResultTest#keepsRealCounterOnFailedCompact` | I1 |
| R-CC-3 | 2 Messages → SKIPPED_SMALL ohne onProblem; leerer Compressor → FAILED_EMPTY + onProblem mit Agenten-Name; nach Fehlschlag rettet das Auto-Compact-Gate den nächsten Turn | `AbstractAgentCompactResultTest#skipsSmallContextHonestly`, `#emptyCompressorIsFailedNotEmptyNeeded`, `#autoCompactRetriesAfterFailure` | I1 |
| R-CC-4 | Hint steht schon → kein zweiter; nach erfolgreichem Compact → Hint wieder scharf | `ToolServiceCompactHintTest#hintIsAddedOnce`, `#hintReappearsAfterSuccessfulCompact` | I3 |
| R-CC-6 | Estimate-Wert → `~34k (estimate)` (PoDelegateTool + Roster); Provider-Wert ohne Tilde | `ContextCounterDisplayTest#estimateIsDisclosed` | I4 |

## 7. Test-Strategie

- JUnit 5 + AssertJ, GIVEN/WHEN/THEN, Regel-ID-Kommentar (`// R-CC-n`).
- Mock-Modelle über das bestehende `streamMock.buildMock(fn)`-Muster (fn zählt Aufrufe via
  AtomicInteger für Mehr-Runden-Szenarien); Capture-Monitore über AtomicReference-Lambdas
  (onProblem/onTool).
- Keine Zeit-/Raten-Logik im Hotfix → Tests deterministisch, keine Sleeps/Latches nötig.
- 5 neue Core-Testklassen (oben); beide Richtungen wo Send-/Monitor-Pfad Logik trägt
  (R-CC-4: Memory-Inhalt UND onTool-Zeile; R-CC-3: Return-Wert UND onProblem UND callCount).
- Bestehende Tests, die den compact-Pfad üben (AiCompressorAgentTest, CompactSessionToolTest,
  PeonAiServiceTest-Delegationstests) bleiben grün — sie sind die Nicht-Regression.
- Testhonesty: R-CC-3-#skipsSmallContextHonestly und die Pin-Tests sind bewusst
  Vor-Grün-Pins; die Behavior-Tests (onProblem, Zähler, Retry, Dedup-Hint) müssen rot starten.

## 8. Open Questions

- Keine. R-CC-5-Entscheidung = (b) streichen (Paul, 2026-09-23) — in §1 + D9 dokumentiert.

---

## 9. PO-Review-Befund (Da Dok, 2026-09-23) — Verdict: **CONCERNS** (nicht-blockierend)

**Evidenz (selbst gelaufen, nicht übernommen):**
- Core-Suite `llmpeon-core`: **948 Tests / 0 Failures / 19 Skipped** (Eclipse-Runner; Surefire-Zahlen liegen niedriger — Green ist die Evidenz, zählt hier als Gate-bestanden).
- Plugin `org.sterl.llmpeon`: Build **grün** (nur Pre-Existing-Null-Safety-Warnings); Test-Modul `org.sterl.llmpeon.test`: Build **grün, 0 Warnings**.
- Plugin-Suite (PDE): **279/0/0** — exakt Da-Mek-Claim.
- Lint (`lintDocsAndTests`): 41 Findings, **alle pre-existing** (UC-DL/UC-JD/UC-CT); `compact-context-counter.md` neu im Lint (CC-IDs), keine neuen Findings.
- Doc-Status `docs/compact-context-counter.md:7` = `❌ specified` — **nicht geflippt** (PO-Aufgabe respektiert) ✓.

**Drei-Seiten-Check — SOLL == IST:**
- R-CC-1: `ChatMessageUtil.getTokenCount:25-32` (inputTokenCount, Estimate-Fallback) + `ThreadSafeMemoryInputTokenCountTest` (2/2 Tests, exakte SOLL-Namen) ✓
- R-CC-2: `ToolService.executeLoop:159-170` (Re-Derive nur bei `isCompactedThisTurn()`) + `ToolServiceCompactResultTest#keepsRealCounterOnFailedCompact` (260000-exakt + compactCalls-Pin) ✓
- R-CC-3: `AbstractAgent.compact:297-334` (SKIPPED_SMALL ohne onProblem, FAILED_EMPTY mit exaktem Wortlaut + Agenten-Name; nullSafety vor Guard) + `AbstractAgentCompactResultTest` (3/3 Tests, beide Richtungen) ✓
- R-CC-4: `ToolService.addCompactHintIfNeeded:212` (`containsMessage(COMPACT_HINT)`-Dedup, deckt normal + forced) + `ToolServiceCompactHintTest` (2/2 Tests, Memory-Inhalt UND onTool-Zeile) ✓
- R-CC-6: `ThreadSafeMemory.isTokenEstimate` + `StringUtil.estimateAware` + `PoDelegateTool.contextUsed:259-263` + `AiAgentStatusModel.build:55` + `ContextCounterDisplayTest#estimateIsDisclosed` ✓
- FAILED_EMPTY-Wortlaut **exakt ein Satz** an allen 4 Stellen (AbstractAgent onProblem, CompactSessionTool yield, PoDelegateTool yield, Test-Pins) — kein Alias ✓
- Clean Break: Grep `boolean compact` im gesamten Repo = 0 Code-Treffer; alle Compile-Fixes aus §2-Inventory vorhanden (AbstractAgentTest:436/440/470, AiPoAgentTest:235, CompactSessionToolTest:123/189, AiAgentStatusModelTest:114-115, HeaderRosterStructureTest:104) ✓

**Deviations (alle 4 von Da Mek — Klassifikation: legitime Verbesserungen, kein Rework):**
1. `ThreadSafeMemory`-Konstruktor: `tokenIsEstimate = !memory.isEmpty()` statt planmäßiger `true` — leerer Restore = 0 = exakt (konsistent mit D6-eigener `clear/replaceAll→false`-Logik); SOLL-Semantik („Wert enthält Estimate-Komponente") erfüllt ✓
2. `AiAgentStatusModel.Row`-Feldreihenfolge: `estimate` als 4. Feld — Plan schrieb nur „Row + boolean estimate", keine Reihenfolge; kosmetisches ✓
3. `PoDelegateToolTest:64/81/91/236`: escaped Pattern `~\\d+ \\(estimate\\)` = Java-Regex-Zwang für das Plan-Pattern; **pinnt die estimateAware-Wiring wirklich** (plain-Wert würde rot) ✓
4. I1: (a) `AiAgentStatusModel.compactResult` null-Guard → „Nothing to compact" (AIChatView:550 null-init, Exception vor compact() → null; Javadoc dokumentiert; verletzt keinen SOLL-Wortlaut) ✓ (b) R-CC-3-Test3: Gate-Überschreitung via Compact-Request-eigener Usage (autoCompactAfter=200000 < 260000) statt vorgesehener addResult — mit addResult würde das doCall-Pre-Turn-Auto-Compact vor Turn 1 feuern und den Retry-Count verschmutzen; Test-Kommentar:73-76 begründet, SOLL-Verhalten („Gate feuert nächsten Turn neu") exakt erhalten ✓

**Nicht-blockierende Lücke (CONCERNS-Grund):**
- D3-Sticky-OR (2× compactSession in EINEM Turn: erst COMPACTED, dann SKIPPED_SMALL → exakt ein Re-Derive) ist dokumentierte Design-Entscheidung, aber **ohne Test**. Mutation `CompactSessionTool.markCompacted()` → no-op bleibt grün (keiner der 9 neuen Tests deckt das 2×-Szenario). Empfehlung an PO/Da Thinka: optionaler Folge-Test `#secondCompactInSameTurnStillDerivesOnce` (Re-Derive genau 1× + Zähler konsistent).

**Mutation-Check (die EINER Stelle):** `ToolService.executeLoop:160` — Gate `req.isCompactedThisTurn()`. Mutation „Re-Derive immer bei ranTool(compactSession)" (Flag ignoriert) → `ToolServiceCompactResultTest#keepsRealCounterOnFailedCompact` wird **rot** (Zähler 260000 → Estimate). Der R-CC-2-Pin hält.

**Plan-Coverage-Gap:** keiner. **Skill/Instruktionen-Gap:** keiner — das §2-Call-Site-/Test-Inventory war vollständig und exakt (eigene Grep-Verifikation fand nichts darüber hinaus).

**Most likely reason this breaks later:** das Sticky-OR-Flag (`ToolLoopRequest.compactedThisTurn`) ist die einzige neue, ungetestete Zustandshaltung — ein zukünftiger Change, der es resetet oder überschreiben lässt, macht das Re-Derive doppelt oder unmöglich → Zähler fällt auf Estimate → Auto-Compact-Gate blind (genau der Bug, den R-CC-2 fixt).
**Change, der das Risiko am meisten senkt:** der oben genannte 2×-compactSession-Test, der exakt ein Re-Derive assertet.
