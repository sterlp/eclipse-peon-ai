---
idPrefix: CC
---

# Compact (core) — ehrlicher Compact: Zähler, Input-Budget, Result, Logging

> **Status:** R-CC-1…6 **✅ done** (2026-09-23, Hotfix Paul, Surefire core 930, Mutations-Nachweis)
> · R-CIB-1…6 **❌ specified** (2026-09-24, Redesign mit Paul; Da-Dok-Review F1–F8 abgearbeitet)
> · R-CC-7 **🚧 in design** (Retry/Fehlerklassen, zusammen mit ApiRetry-Tabelle).
> **Komponente:** eigens core-Package `compact` ([ADR-0056](adr/0056-compact-component-and-render-modes.md)) —
> der Compact ist kein Konversations-Agent. **UI-Teil** (Compact-Button, Compact-Lock/Queue,
> working-Flag) liegt getrennt in [compact-lock.md](compact-lock.md) → künftig Plugin-Docs.
> Verwandt: [token-usage.md](token-usage.md) (Header ↑↓ = Kosten, ADR-0004, unverändert),
> [context-message-concept.md](context-message-concept.md), [header-state-leak.md](header-state-leak.md).

## Problem

Drei Themenkreise, ein Ziel — der Compact ist der Recovery-Pfad für übergroße History und muss
in jedem Fall liefern, ehrlich und nachvollziehbar:

1. **Zähler-Lügen (R-CC, ✅ gefixt 2026-09-23):** `totalTokenUsed` trug zwei Metriken (Provider-
   Kosten vs. Estimate), Compact-Skip fiel aufs Auto-Gate blind, Hint feuerte je Tool-Runde neu.
2. **Input-Budget (R-CIB):** der Compact-Input ist heute nur per-Message begrenzt
   (statischer 4000er Head-Cap, `AiCompressorAgent.java:70`) — kein Gesamt-Budget. Genau dann,
   wenn er gebraucht wird, kann sein Input das Context-Fenster des Compact-Modells sprengen
   (Evidenz 2026-09-24: 400 `exceed_context_size_error`). Umgekehrt kappt der Blanko-Cap die
   Substanz weg: bei einem 200k-Zeichen-Tool-Result bleibt ~1k Token für die Summary — der
   Compact komprimiert das weg, was er retten soll (Evidenz: 337461 Provider-Tokens vs.
   114512 Compact-Schätzung, 61 Messages).
3. **Keine Beobachtbarkeit (R-CIB-6):** kein Logger im Compact-Pfad, keine Größe/Stufen/
   Dropped-Mengen; History-JSONL wird nach dem Compact gelöscht — ein Compact-Problem ist
   nachträglich nicht rekonstruierbar.

---

## Teil 1 — Zähler & Result (R-CC)

### R-CC-8 — Compact/Hint nur ab einer echten History (Paul 2026-09-24, ⏳ Rückversicherung „> 3")

Der **Compact-Hint macht nur Sinn, wenn mehr als 3 Messages** in der Memory stehen — Tokens
allein reichen nicht (3 riesige Messages → der Compact hätte nichts zu retten). Konstante
`MIN_COMPACT_MESSAGES` (Wert: > 3) wird **an einer Stelle definiert und überall referenziert**:
Hint-Gate (`ToolService`), Auto-Compact-Gate (`AbstractAgent`) und der bestehende
SKIPPED_SMALL-Guard nutzen dieselbe Konstante — keine verstreuten Literale. Bestehender
SKIPPED_SMALL-Guard (`size() < 3` → Skip) bleibt unverändert im Verhalten, referenziert künftig
die Konstante.

- GIVEN Memory mit ≤ 3 Messages, Token-Schwelle überschritten WHEN das Hint-Gate prüft THEN
  kein Hint (Compact würde ohnehin skippen/scheitern).
- GIVEN Memory mit > 3 Messages, Token-Schwelle überschritten WHEN das Gate prüft THEN Hint
  scharf (R-CC-4-Dedup unverändert).

### R-CC-9 — Compact-Reinsert: keine Rekursion (Paul 2026-09-24)

Nach dem Compact legt die Compact-Komponente die Zusammenfassung + Marker **wieder ins Memory**
(`ThreadSafeMemory`-Add, Compact-Restore `AbstractAgent:320-326`). Diese **vom Compact
wiedereingefügten Messages** (a) werden über **Konstanten** erkennbar gemacht (wie `COMPACT_HINT`),
(b) dürfen **keinen erneuten Compact triggern** — weder Auto-Gate noch Hint feuern auf das
grade kompaktierte Ergebnis (R-CC-2-Reevaluate + R-CC-8-Messages-Gate decken das ab), und (c)
dürfen beim nächsten Compact nicht **rekursiv** wieder komprimiert werden, ohne dass neue
Messages dazukamen. So wird nie aus Versehen die frische Summary (oder eine echte User-Message)
beim Compact verworfen.

- GIVEN Compact erfolgreich, Memory = Summary + Marker WHEN das Auto-Gate/Hint erneut prüft THEN
  kein sofortiger Re-Compact ohne neue Messages.
- GIVEN die wiedereingefügten Summary-Messages WHEN ein Compact läuft THEN sie werden als
  State behandelt (nie als „echter User-Text"), erkennbar über die Konstante.

### R-CC-1 — Context-Counter misst Input, nie Kosten ✅

`totalTokenUsed` trägt ausschließlich Kontextgröße (Prompt-Input). Provider
`TokenUsage.inputTokenCount()` wird verwendet (Fallback: Estimate chars×2/7); `totalTokenCount()`
(Kosten) fließt **nie** in den Kontext-Zähler — Header ↑↓ bleibt Kosten (ADR-0004).

- GIVEN Response `TokenUsage(input=1000, output=5000, total=6000)` WHEN `addResult` THEN
  Zähler wächst um **1000**. *(Test: `ThreadSafeMemoryInputTokenCountTest#countsInputNotTotal`)*
- GIVEN Response ohne TokenUsage WHEN `addResult` THEN Estimate (chars×2/7). *(Test:
  `ThreadSafeMemoryInputTokenCountTest#fallsBackToEstimate`)*

### R-CC-2 — Reevaluate nur nach erfolgreichem Compact ✅

`reevaluateTokens()` + Static-Rebuild nach `compactSession` nur bei **COMPACTED**; bei Skip/
Failure bleibt der echte Zählerwert (Auto-Gate scharf, kein Death Spiral).

- GIVEN Zähler auf 260000 WHEN `compactSession` scheitert (leere Compressor-Response) THEN
  Zähler bleibt 260000. *(Test: `ToolServiceCompactResultTest#keepsRealCounterOnFailedCompact`)*

### R-CC-3 — Ehrliches Compact-Ergebnis ✅

`compact()` liefert `CompactResult` (**COMPACTED / SKIPPED_SMALL / FAILED_EMPTY**), kein Boolean.
`SKIPPED_SMALL` = zu klein (legitim, „not needed"-Zeile bleibt ehrlich); `FAILED_EMPTY` =
Compressor lieferte nichts → **Fehler**: `monitor.onProblem` beim Tool-Call, UI-Button und
stillen Auto-Compact — nie stiller Abbruch.

- GIVEN Memory mit 2 Messages WHEN `compact()` THEN `SKIPPED_SMALL`, kein onProblem. *(Test:
  `AbstractAgentCompactResultTest#skipsSmallContextHonestly`)*
- GIVEN Memory ≥ 3, Compressor liefert leer WHEN `compact()` THEN `FAILED_EMPTY` + `onProblem`
  mit Agentennamen. *(Test: `AbstractAgentCompactResultTest#emptyCompressorIsFailedNotEmptyNeeded`)*
- GIVEN Compact scheitert WHEN nächster Turn THEN Auto-Gate versucht erneut. *(Test:
  `AbstractAgentCompactResultTest#autoCompactRetriesAfterFailure`)*

### R-CC-4 — Compact-Hint nur einmal ✅

`addCompactHintIfNeeded` fügt den `COMPACT_HINT` nur hinzu, wenn er nicht bereits in der Memory
steht (`containsMessage`); nach erfolgreichem Compact (Memory geleert) wieder scharf; forced
Hint (Stuck-Pfad) unterliegt demselben Dedup.

- GIVEN Hint bereits in Memory WHEN erneut feuert THEN genau einmal. *(Test:
  `ToolServiceCompactHintTest#hintIsAddedOnce`)*
- GIVEN Compact erfolgreich, Schwelle wieder überschritten THEN Hint erscheint erneut. *(Test:
  `ToolServiceCompactHintTest#hintReappearsAfterSuccessfulCompact`)*

### R-CC-6 — Estimate als Estimate gekennzeichnet ✅

Wird der Zähler als Estimate (chars×2/7) bestimmt, zeigt die Anzeige `~N (estimate)` (PoDelegateTool-
Kontext, Roster). Provider-Werte ohne Tilde. *(Test: `ContextCounterDisplayTest#estimateIsDisclosed`)*

### R-CC-7 — Compact-Fehler sichtbar + begrenzter Retry 🚧 in design (Paul 2026-09-24)

Compact-Call kann fehlschlagen (Evidenz: 400 `exceed_context_size_error` am Compressor-Call —
Input übersteigt das Fenster des Compact-Modells). IST: Exception stirbt still oder als roher
Stack; LLM erfährt nichts, Header hängt ([header-state-leak.md](header-state-leak.md)).

- **Fehler ans LLM:** ehrliches Tool-Result „compact failed + Ursache" — nie still; bubbelt
  zum User. **`monitor.onProblem`** zusätzlich.
- **Retry 1× nach 20s — nur transient** (Rate-Limit, 5xx, Netzwerk); deterministisch tot
  (`exceed_context_size_error`, Invalid-Request) → sofort ehrlich fehlgeschlagen, kein Retry.
  Gleiche Fehlerklassen-Tabelle wie ApiRetry (ein Bestand, zwei Verbraucher).
- BDD (hart erst bei ❌): transient → 1 Retry nach 20s, dann onProblem + ehrliches Result;
  `exceed_context_size_error` → KEIN Retry, sofort onProblem + Ursache; Retry erfolgreich →
  normaler COMPACTED-Fluss.
- ❓ offen (nur Paul): Auto-Compact-Pfad darf bei deterministischem Fehler nicht je Turn
  endlos wiederversuchen (Max-1-Retry-pro-Fehlerklasse? Empfehlung: ja, sonst Compact-Spirale).

---

## Teil 2 — Compressor-Input-Budget (R-CIB)

### R-CIB-1 — Budget & Schätzung an einer Stelle

Budget = `autoCompactAfter` (Config, Tokens), Vergleichsbasis **ohne** Toleranz; die
**+5%-Toleranz gilt nur für den Auto-Compact-Hint** (Spielraum, damit das LLM noch Tools
aufrufen kann). **Schätzung an EINER Stelle:** `ChatMessageUtil.estimateTokens` (chars×2/7,
bewusst über-schätzend) ist der einzige Estimator — auch für den Compact-Input. Der alte
`join/4`-Ansatz ist verworfen (schätzte niedriger → Kürzung greift später als gedacht; zwei
Konventionen im selben Modul, F2). Werte als Estimate tragen die `~N (estimate)`-Markierung
(R-CC-6-Muster).

- GIVEN `autoCompactAfter ≤ 0`/unset WHEN der Compact-Input gebaut wird THEN **nie** kürzen
  (Budget „off", wie der Hint), nur Entry-Log. *(Test: `CompactStagerTest#zeroBudgetMeansNoCap`)*

### R-CIB-2 — Loop-Filter: exakt statt Substring ✅ gebaut

Exakte Duplikate via `Set` statt `indexOf`-Substring, O(n) (`LinkedHashSet`, keep-first) —
Zweck: LLM-Hänger (50× dasselbe File) kollabieren, ohne False-Positives. Duplikat-Hygiene wurde
an der Quelle gefixt („Compact-Result genau einmal" + R-ST4). Dedup läuft **vor** den Caps
(ungecappte Strings = exakter Vergleich; nach Caps würden verschiedene Messages kollabieren).

- GIVEN 50× identische Tool-Message WHEN der Filter läuft THEN genau einmal im Input.
- GIVEN zwei Messages mit identischem Kopf (bis Truncation), differierendem Inhalt THEN beide
  bleiben (kein Substring-False-Positive — Regressions-Gegenstück zum IST; Mutation: Set-Dedup
  zurück zu Substring → Test muss rot).

### R-CIB-3 — Entry-Debug-Log: Anfangswerte

**Sobald der Compact-Auftrag durch das Compact-Tool geht, genau EIN debug-Log** — vor jeder
Kürzung, mit den Anfangswerten: `agent, messageCount, estimatedInputTokens, budget,
thinkingEnabled` — damit Paul die Werte mit der UI (Token-Header) abgleichen kann. Bei
Budget „off" (≤ 0) ist das Entry-Log der einzige Log.

### R-CIB-4 — Stufen-Kürzung über Budget

Trigger: `estimate > autoCompactAfter`. Re-Estimate (`ChatMessageUtil.estimateTokens`) nach
jeder Stufe. Gilt **nur im Compacter** — der Live-Context an das LLM wird nicht verändert
(kein Live-Think-Stripping, bewusst verworfen). Logic = pure Funktion
`(Memory-Snapshot, Budget) → (Input-String, Disclosure, Stats)` im `compact`-Package
([ADR-0056](adr/0056-compact-component-and-render-modes.md)) — testbar ohne Agent-Mock.

1. **Stufe 1 — Think-Cap front + letzte echte User-Message:** Thinking wird mit **Cap 9000
   (Zeichen)** aufgenommen, Front-Cap mit `$`-Anker — **das Ende bleibt** (Konklusion steht am
   Ende, Kopf ist Prozessgeraste). Neuer Render-Modus in `ChatMessageUtil` (Options-Record,
   nicht wachsende Parameterliste; IST-Format `Think: `). Gleichzeitig: vom Textcontent der
   User-Messages nur die **letzte mit echtem User-Text** — frühere User-Messages sind nur
   „State" (gleiche Semantik wie die Chat-Darstellung). **Echter User-Text = letzter
   TextContent** der UserMessage — code-verifiziert (Da Thinka 2026-09-24): Turn-Context-Items
   werden **zuerst** angehängt, der echte User-Text **zuletzt** (`AbstractAgent.doCall:271-274`,
   `ThreadSafeMemory.add:73-77`, Compact-Restore `AbstractAgent:320-326`); Pauls Erst-These
   „erster TextContent" war am Code falsch herum und wurde korrigiert. Die Insert-Ordnung ist
   damit eine Regel — kein Raten mehr ([context-message-concept.md](context-message-concept.md)).
   **Ausgeschlossen von „letzte":** `COMPACT_HINT`, Queued-Marker, System-Nachrichten —
   Hint/Marker werden als **Konstanten** referenziert (nie verstreute String-Literale), damit
   der Ausschluss robust bleibt (Da-Dok F1: der Hint liegt real als UserMessage in der Memory).
   AI-Text voll.
2. **Re-Estimate** nach dem Join (`System.lineSeparator`) — estimate ≤ Budget → fertig.
3. **Stufe 2 — Tool-Results, Tool-Arguments, Thinking auf 6000:** immer noch drüber → Cap 6000
   (Zeichen) auf alle drei (Arguments gehören dazu, F3 — sonst wandern 200k-Write-Args ungecappt
   bis zur Endstufe). **Log warn.**
4. **Endstufe — dynamisches Per-Message-Cap:** immer noch drüber → Cap je Message =
   `capChars = restTokens × 7 / 2 / n` (Token→Zeichen mit dem ×2/7-Estimator, F5), n ≥ 1
   geclampt (`AiCompressorAgent.call` ist public und umgeht den `< 3`-Guard) → garantiert
   Terminierung (Summe ≤ n·cap → estimate ≤ Rest). Ersetzt fixe 3000-Stufe und „Text-only"-Stufe.
   **Log error.**

Konsequenz: unter Budget wird **gar nicht** gekürzt — auch ein einzelnes großes Tool-Result
nicht (es passt ja). Der statische 4000er-Blanko-Cap fällt weg.

### R-CIB-5 — Disclosure: einmal am Ende

Wurde gekürzt: der Compact-Input endet mit **`session truncated`** plus **einer Zeile** mit den
angewandten Caps („thinking capped 9000 (front), tool results 6000, per-message cap N,
duplicates collapsed M") — einmal am Input-Ende, **nicht** in jeder gekappten Message (User-
korrektur des heutigen IST). Nicht gekürzt → kein Hinweis. `compressor.md`-Prompt wird
erweitert, damit das Modell die Disclosure-Zeile versteht.

### R-CIB-6 — Compact-Logging: Log + Agent

**Eine Result-Zeile pro Compact-Versuch** — dieselben Zahlen an zwei Empfänger:

- **Log:** Stufe (info Stufe 1 / warn Stufe 2 / error Endstufe), estimate vor/nach,
  Dropped-Menge (Zeichen), Result-Größe, Modell, Dauer; bei Failure Ursache + Zahlen
  (Fehlerklassen kommen mit R-CC-7). „Log OR throw"-Regel unberührt (Result-Zeile ist kein
  Exception-Ersatz).
- **Zum Agenten:** die `CompactResult`-Zeile (Tool-Ergebnis an das LLM) trägt dieselben Zahlen
  („compressed 61 messages ~114k → input ~40k, result 2.3k, stage: tool results 6000") — das
  LLM versteht im nächsten Turn, was passiert ist. `CompactResult` wird dafür vom Enum zum
  **Record** mit den Zahlen erweitert (Da-Dok F9; alle `compact()`-Caller per Grep verifizieren).

---

## BDD (R-CIB)

```
GIVEN History unter Budget (estimate ≤ autoCompactAfter)
WHEN der Compressor baut seinen Input
THEN keine Message wird gekürzt oder gestrichen
AND der Input enthält alle Messages vollständig inkl. Thinking
AND kein "session truncated"-Hinweis
AND genau ein debug-Log mit Anfangswerten (agent, messageCount, estimate, budget)

GIVEN autoCompactAfter ≤ 0 oder unset
WHEN der Compact-Input gebaut wird
THEN wird nie gekürzt, egal wie groß der Estimate ist
AND es gibt nur das Entry-Log

GIVEN estimate > autoCompactAfter, Stufe 1 reicht (Re-Estimate ≤ Budget)
WHEN Stufe 1 läuft
THEN Thinking ist auf 9000 Zeichen gecappt, Front-Cap mit $-Anker — das Ende (Konklusion) bleibt
AND die letzte User-Message mit echtem User-Text (LETZTER TextContent) ist voll, frühere nur State
AND die letzte User-Message ist NICHT der COMPACT_HINT / Queued-Marker (Konstanten-Ausschluss)
AND AI-Text ist unverändert
AND der Input endet mit "session truncated" + Cap-Zeile (einmal, am Ende)
AND Log-Level = info

GIVEN estimate bleibt nach Stufe 1 über Budget
WHEN Stufe 2 läuft (Cap 6000 auf Tool-Results + Tool-Arguments + Thinking)
THEN estimate ist danach ≤ Budget oder die Endstufe greift
AND es gibt ein warn-Log mit Stufe + Zahlen

GIVEN estimate bleibt auch nach Stufe 2 über Budget
WHEN die Endstufe läuft
THEN jede Message ist auf restTokens × 7 / 2 / n Zeichen gecappt (n ≥ 1)
AND estimate ist danach ≤ Budget (garantiert — Mutation: Divisor/Konvertierung mutieren → rot)
AND es gibt ein error-Log mit Stufe + Zahlen

GIVEN eine einzige Message ist größer als das gesamte Budget
WHEN die Endstufe läuft
THEN diese Message wird auf das Per-Message-Cap gekappt und der Compact liefert

GIVEN Dedup allein bringt den Estimate unter Budget
WHEN der Input gebaut ist
THEN die Disclosure-Zeile nennt "duplicates collapsed: N" (kein stilles Entfernen)

GIVEN keine UserMessage mit echtem User-Text in der History
WHEN Stufe 1 läuft
THEN keine User-Message-Reduktion (Regel greift ins Leere, kein Fehler)

GIVEN ein Compact-Auftrag geht durch das Compact-Tool
WHEN der Compact startet
THEN genau EIN debug-Log mit den Anfangswerten wird geschrieben (vor jeder Kürzung)

GIVEN ein Compact-Versuch ist abgeschlossen (erfolgreich oder nicht)
WHEN das Ergebnis gebaut ist
THEN existiert genau eine Result-Zeile in Log UND im Tool-Ergebnis an den Agenten
```

## Umsetzung & Slicing (kleine vertikale Inkremente)

1. **Docs ✅ (dieses Doc):** Konsolidierung von compact-input-budget.md + compact-context-counter.md,
   F1–F8-Entscheidungen eingebacken; alte Docs aufgelöst.
2. **Core extrahieren:** core-Package `compact` — `CompactStager` (pure: Dedup + Stufen 1/2/
   Endstufe + Disclosure), `CompactEngine` (Stager + LLM-Call + Logging), `CompactResult`-Record.
   Grüne Unit-Tests ohne Agent-Mock; Mutation-Kandidaten: Endstufen-Terminierung + Dedup-Regression.
3. **Wiring:** `AbstractAgent.compact()` auf Engine umstellen, Hint/Trigger unverändert,
   `autoCompactAfter ≤ 0`-Guard, Entry-Debug-Log, Render-Modi in `ChatMessageUtil`
   (Options-Record) inkl. Fix des SystemMessage-Drops (ADR-0030-Landmine).
4. **R6 + UI:** Stats bis `CompactSessionTool`/Statuszeile, compressor.md-Prompt, Homepage-Update
   (user-visible → im selben Inkrement), Plugin-Tests.

## Offen

- **WARUM liefert der Compressor leeren Text?** (Think-only-Response? Cancel?) — R-CC-3-Log +
  Compact-Model-Config. Paul.
- **R-CC-7 Retry-Zählschutz** (s. o.) — nur Paul.
- **Workspace-Memory-Snapshot Vollkopien je `memoryAdd`** (ADR-0032-Hash-Key) — ❓
  [open-points.md](open-points.md).
- **AGENTS-`<agent>.md`-Kopien** im Slave-Memory: Code-Pfad liefert keine Mehrfach-Erzeugung;
  Evidenz fehlt.

## Abgrenzung

- **Live-Context:** bewusst nicht Teil dieser Story — kein Live-Think-Stripping; nur der
  Compact-Input wird gestuft gekürzt.
- **R-CC-7** (Fehlerklassen + Retry) bleibt eigenständig 🚧 und teilt sich die Fehlerklassen-
  Tabelle mit der ApiRetry-Triage — dieses Doc liefert die Zahlen/Disclosure-Infrastruktur (R-CIB-6).
- **Auto-Compact-Trigger-Logik:** `autoCompactAfter` wird nur als Budget gelesen, das
  Trigger-Verhalten ändert sich nicht.
- **UI** (Compact-Button, Lock/Queue, working-Flag): [compact-lock.md](compact-lock.md) —
  künftig Plugin-Docs.
- **Context-Pollution-Quellen** (Read-Tools ohne Cap, index.md pro Turn, Workspace-Memory ohne
  Cap): ❓ [open-points.md](open-points.md) — hier nicht behoben; der Compact symptom-behandelt
  nur seinen eigenen Input.