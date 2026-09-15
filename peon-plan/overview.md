# R16-Schärfung: Compact-Guard `< 2` → `< 3` (Skip statt LLM-Call)

Zyklus `story/po-compact-2026-09-13` · Branch läuft, Working Tree clean · 1 Inkrement
SOLL-Quelle: `docs/po-agent-jon.md` R16 (❌ specified, 2026-09-15 geschärft) — nur diese Quelle.

## 0. STOP-AND-ASK (Da Mek)

Bei Compile-Fehlern ohne Lösung, nicht-grün-bekommenden Tests oder IST-Widersprüchen zum Plan:
**AKTIV bei Jon (askDev) nachfragen** — nie still workarounden, nie SOLL ändern.

## 1. Context

Ein Compact hinterlässt deterministisch **exakt 2 Messages** (Turn-Context-UserMessage
`Session compacted:` + Summary-AiMessage). Bei Guard `< 2` war ein Re-Compact direkt nach
jedem Compact ein echter LLM-Call auf diese 2 Messages — der Guard war strukturell tot
(User-Smoke 2026-09-15: „Compressing conversation 2 messages 124 tokens").
Ziel: Guard `< 3` → **jeder Re-Compact wird Noop**, und alle 4 Eintrittspfade (Action-Bar,
Sklaven-Header-Button, `compactSession`, `compactPlan/Review/Dev`) melden ehrlich, statt
fälschlich „compacted." zu berichten.

## 2. IST-Verifikation (bereits gemacht — Dev muss nichts re-erkunden)

| SOLL-Punkt | IST | Änderung? |
|---|---|---|
| 1. Guard in `AbstractAgent.compact()` | `AbstractAgent.java:272`: `if (memory.size() < 2) return false;` — einzige Guard-Quelle, **keine** `compact()`-Overrides im Workspace (Grep `public boolean compact`) | **1 Zeile** |
| 2. `PoDelegateTool.compactPlan/compactReview/compactDev` | Private Methode `compact(NamedAgent)` in `PoDelegateTool.java` (zwischen `compactDev()` und `getPlanSlave()`): **Boolean-Return wird verworfen**, meldet immer `"<uiName> compacted. Context: ..."` | **Ja** |
| 3. UI-Sklaven-Button | `AIChatView.doCompressAgent` (AIChatView.java:518–541) fängt `result = agent.compact(...)` (Zeile 529) und gibt `PeonConstants.status(AiAgentStatusModel.compactResult(result, slave.uiName()), ex)` (Zeile 539) zurück. `AiAgentStatusModel.compactResult(false, …)` → `"Nothing to compact"` (bereits getestet: `AiAgentStatusModelTest.compactResult_successVsSkip`). **→ Bereits gedeckt, kein Code-Ändern.** | **Nur stale Javadoc-Kommentar** (AIChatView.java:516–517: „R16 skip (< 2 messages)" → „< 3 messages") — 1 Kommentarzeile |
| 4. `CompactSessionTool` (Jon) | Liest bereits den Boolean; `false` → `"Not needed only " + agent.getMemory().size() + " message in context"` (CompactSessionTool.java, else-Branch). Kein Guard-Duplikat. **→ Keine Änderung**, nur Test-Verifikation (bestehende Tests decken den Skip-Zweig nicht neu — BDD-Szenario 1 reicht über `AbstractAgent.compact()`-Tests). | **Nein** |

**Akzeptierte Nuance (bewusst nicht gelöst):** `compact()` liefert `false` auch bei
Empty-Response des Compressors (da `log.warn`, Memory unverändert). In diesem seltenen Fall
meldet `PoDelegateTool` ebenfalls „Nothing to compact (N messages)" — ehrlich genug
(nichts wurde kompakt, N Messages vorhanden); Return-Vertrag von `compact()` wird **nicht**
geändert (kein Enum/Optional — Over-Engineering).

**Bewusst unverändert:** `AIChatView.doCompressContext` (Action-Bar) behält seinen
billigen UI-Short-Circuit `if (active.getMemory().size() < 3) return;` (AIChatView.java:493) —
SOLL bestätigt das explizit. Silent-Skip dort ist Bestand (keine Statuszeile), SOLL fordert nur
Feedback für den **Sklaven-Button**-Pfad.

## 3. Design-Entscheidungen

- **Eine Guard-Quelle:** `AbstractAgent.compact()` — alle 4 Eintrittspfade laufen durch sie.
  Kein doppeltes Guard-Logik-Duplikat in den Tools (Punkt 4 bestätigt: `CompactSessionTool`
  und (neu) `PoDelegateTool` lesen nur den Return).
- **N-Quelle in `PoDelegateTool`:** `slave.getMemory().size()` direkt nach dem `false`-Return.
  Einfachste Quelle, keine zusätzliche API auf `compact()`. Bei `false` ist das Memory
  garantiert unverändert (kein Clear), also ist N exakt der Zustand zum Skip-Zeitpunkt.
- **PoDelegateTool-Skip-Return:** nur den String returnen (kein `onTool`/`reportAction`) —
  SOLL definiert nur das Tool-Result an Jon; UI-Zier bleibt am Status-Pfad (Punkt 3).
  Erfolgspfad unverändert: `reportAction(agent, "compacted")` + `"<uiName> compacted. " + contextUsed(…)`.
- **Log OR throw:** unverändert — Guard-Paths sind `return false` (kein Log nötig, normaler
  Skip), Empty-Response bleibt `log.warn`.

**Neuer `PoDelegateTool.compact(NamedAgent)` (exakt):**
```java
private String compact(NamedAgent agent) {
    AiAgent slave = agent.agent();
    if (!slave.compact(monitor)) {
        return "Nothing to compact (" + slave.getMemory().size() + " messages)";
    }
    reportAction(agent, "compacted");
    return agent.uiName() + " compacted. " + contextUsed(slave);
}
```

## 4. Affected files (vollständige Liste — nichts anderes anfassen)

Core main:
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/AbstractAgent.java` — Zeile 272: `memory.size() < 2` → `memory.size() < 3`
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/poagent/tools/PoDelegateTool.java` — private `compact(NamedAgent)` wie oben

Plugin (Kommentar nur):
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java` — Javadoc von `doCompressAgent` (Zeile ~516–517): „R16 skip (< 2 messages)" → „R16 skip (< 3 messages)"

Core tests:
- `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/agent/AiPoAgentTest.java`
- `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/agent/AbstractAgentTest.java`
- `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/poagent/tools/PoDelegateToolTest.java`

Docs (vom User bereits aktualisiert — **einfach mitcommitten**, nicht editieren,
Status ❌→✅ **nicht** flippen — das ist PO-Aufgabe nach Review):
- `docs/po-agent-jon.md` (R16), `docs/agenten-status-im-header.md` (Regel 4 + BDD), `docs/open-points.md`

Unberührt: `CompactSessionTool.java`, `AiAgentStatusModel.java`, `AiChatView.doCompressContext`,
`AiDeveloperAgentTest.java` (alle in-Loop-Compact-Tests seeds 10 Messages → unbeeinflusst).

## 5. Tests — Reihenfolge red-first (GIVEN/WHEN/THEN → Test-Name)

**Schritt 1 — Red:** neue/geänderte Tests schreiben, gegen alten Code laufen lassen (müssen rot sein).

1. **`AiPoAgentTest.compact_skipsWithoutLlmCall_whenBelowThreeMessages`**
   (adaptiert aus `compact_skipsWithoutLlmCall_whenBelowTwoMessages` — umbenennen + anpassen)
   Parameterisiert `@ValueSource(ints = {0, 1, 2})`:
   GIVEN Jon mit N Messages · WHEN `compact(null)` · THEN `false`, Memory unverändert
   (`getCopy()` vor/nach identisch), `streamMock.getCallCount() == 0`.
   → Rot mit altem Guard (bei N=2 kompaktiert er heute).
2. **`AbstractAgentTest.compact_secondCallDirectlyAfterCompact_isNoop`** (NEU — die Mutationssicherung)
   GIVEN Agent mit 3 Messages + Stub-Compressor · WHEN 1. `compact()` → `true`, Memory = exakt 2 Messages
   (UserMessage „Session compacted:" + AiMessage Summary) · WHEN 2. `compact()` ·
   THEN `false`, Memory unverändert, **0 zusätzliche LLM-Calls** (`callCount == 1`).
   → Rot mit altem Guard (2. Compact feuert LLM-Call).
3. **`PoDelegateToolTest.compactDev_skipsWithHonestMessage_whenSlaveBelowThreeMessages`** (NEU)
   GIVEN Da Mek mit exakt 2 Messages · WHEN `tool.compactDev()` ·
   THEN Result verbatim `isEqualTo("Nothing to compact (2 messages)")`
   (also `doesNotContain("compacted.")` implizit), `streamMock.getCallCount() == 0`,
   Slave-Memory unverändert. → Rot (heute: „Da Mek compacted." + LLM-Call).

**Schritt 2 — Code ändern** (Sektion 4) → alles grün.

**Schritt 3 — Bestand anpassen** (grünerhaltend, sonst rot):
4. `AiPoAgentTest.compact_onlyCompactsJon_notSlaves`: Jon bekommt **3.** Message (z. B.
   `AiMessage.from("jon old reply 2")`); Sklaven bleiben bei 2 (werden nie kompaktiert —
   irrelevant). Javadoc-Kommentar „compact is a no-op below 2 messages" → „below 3".
5. `AbstractAgentTest.test_compactContext_clearsMemoryAndRestoresTurnContext`: 3. Message
   ergänzen; Kommentar „we need at least 2 messages" → „at least 3".
6. `AbstractAgentTest.test_call_rebuildsSystemMessageAfterClear`: nach `call("first")`
   Memory = 2 → **eine** Message ergänzen (→ 3), sonst fällt der Compressor-Call weg und
   `callCount` wird 2 statt 3.
7. `AbstractAgentTest.test_restoreTurnContext_skipsDuplicates`,
   `test_compressContext_noUserContextRestore`, `test_compressContext_skipsDuplicatesInTurnContext`:
   jeweils 3. Message ergänzen (alle seeden heute exakt 2 → Compact würde skippen, Summary
   bliebe weg).
8. `AbstractAgentTest.test_restoreTurnContext_keyedItem_reinjectedAfterCompact`: nach
   `call("hi")` Memory = 2 → 3. Message ergänzen. (Ohne Anpassung grün *aber* falsch —
   der Test würde den Re-Inject-Pfad nie üben: Test Honesty.)
9. **Erfolgs-Pfad absichern:** `PoDelegateToolTest.compactReview_compactsReviewSlaveMemory`
   um `assertThat(reply).contains("Da Dok compacted.")` ergänzen (SOLL: Erfolg weiterhin
   „compacted." — heute nur das Context-Pattern asserted).

**Mutation-Check (PO-Aufgabe nach grün, nicht persistenter Test):** Guard temporär auf
`< 2` zurück → `compact_secondCallDirectlyAfterCompact_isNoop` **muss rot** werden
(2. LLM-Call), ebenso `compact_skips…whenBelowThreeMessages` (N=2) und
`compactDev_skipsWithHonestMessage…`. Danach zurückstellen, grün lassen, committen.

## 6. Regeln & constraints

- Verbatim-Strings: `"Nothing to compact (N messages)"` (PoDelegateTool),
  `"Not needed only N message in context"` (CompactSessionTool, unverändert),
  UI: `"Nothing to compact"` (unverändert). Keine Abweichung vom SOLL-Wording.
- Kein `\n`-Hardcoding in neuen Tool-Outputs (hier: keine Multi-Liner, OK).
- Keine neue Log-Ausgabe im Skip-Pfad (kein Log or throw-Doppelpfad).
- Jedes Sub-Step kompiliert für sich (hier trivial: 1 Inkrement, 1 Commit pro grünem Step
  ist erlaubt, Mindestens End-Commit).
- **Commit:** grüne Iteration + `docs/**` (po-agent-jon.md, agenten-status-im-header.md,
  open-points.md) in **dieselben** Commits — nie nur Code-Dateien. Message z. B.
  `r16: compact guard <3 + honest slave-compact skip message`.
- **Nicht anfassen:** R16-Status-Flip (❌→✅) = PO-Aufgabe; `open-points.md`-Tabelle wird
  vom User/PO gepflegt — Dev committet sie wie vom User hinterlassen.

## 7. Verifikation / Abnahme

1. `mvn -pl llmpeon-core surefire:test` (Maven Surefire = ground truth): **grün, 0 failures**.
   Baseline 738 tests → erwartet 738 + 1 (Noop) + 1 (skip-Szenario, parameterisiert zählt
   Surefire als 3 Fälle: +2 netto je nach Zählweise; exakte Zahl aus dem Surefire-Report
   in die Abnahme-Evidenz aufnehmen — nicht raten).
2. Plugin-Compile-Check (Kommentar-Änderung in `AIChatView.java`):
   `eclipseBuildProject org.sterl.llmpeon` grün — **keine** OSGi-Plugin-Suite nötig
   (kein behavior-ändernder Plugin-Code).
3. Manuell-verifizierbar (bleibt User-Smoke, steht bereits in open-points.md):
   Sklaven-Header-Button bei < 3 Messages → Statuszeile `Nothing to compact`.
4. DONE-Claim-Evidenz (je Deliverable): geänderte Zeile in AbstractAgent.java (`< 3`),
   PoDelegateTool-Methode (Boolean-Return gelesen), Surefire-Zahlen, Commit-Hash inkl. docs.

## 8. Open questions

- Keine. SOLL ist vollständig determiniert; IST widerspricht nicht (Punkt 3 bereits
  gedeckt — als Evidenz im Review benennen, kein Stillstand).
