---
idPrefix: CIB
---

# Compressor-Input-Budget (R-CIB) — budgetierter Compact-Input

> **Status:** R-CIB-1…6 **✅ done** (2026-09-25, Da Dok Review ACCEPTED, 1001 Tests grün).
> **Einstiegs-Doc:** [compact.md](compact.md) (Zähler & Result, Präfix CC) ·
> **ADR:** [ADR-0056](adr/0056-compact-component-and-render-modes.md) ·
> **Architektur:** [compact-architektur.md](compact-architektur.md).
> **Komponente:** core-Package `compact` — Staging in `ContextTrimComponent` (pure), Token-Mathe
> zentral in `ChatMessageUtil`, LLM-Call + Logging in `CompactService`.

> **Warum ein eigenes Doc:** die Input-Budget-Regeln tragen den eigenen ID-Präfix `CIB` — der
> Docs-Linter verlangt ein Präfix je Feature-Doc ([docs-linter.md](docs-linter.md), R-DL-17);
> Fremd-Präfixe in einem teilnehmenden Doc sind `PRAEFIX_FREMD`. `compact.md` bleibt der
> Einstieg für alles Compact-core.

## Problem

Der Compact-Input war nur per-Message begrenzt (statischer 4000er Head-Cap,
`AiCompressorAgent.java:70`) — kein Gesamt-Budget. Genau dann, wenn er gebraucht wird, kann sein
Input das Context-Fenster des Compact-Modells sprengen (Evidenz 2026-09-24: 400
`exceed_context_size_error`). Umgekehrt kappt der Blanko-Cap die Substanz weg: bei einem
200k-Zeichen-Tool-Result bleibt ~1k Token für die Summary — der Compact komprimiert das weg, was
er retten soll (Evidenz: 337461 Provider-Tokens vs. 114512 Compact-Schätzung, 61 Messages).
Dazu: keine Beobachtbarkeit — kein Logger im Compact-Pfad, keine Größe/Stufen/Dropped-Mengen;
History-JSONL wird nach dem Compact gelöscht — ein Compact-Problem ist nachträglich nicht
rekonstruierbar.

## Regeln

### R-CIB-1 — Budget & Schätzung an einer Stelle ✅ done

Budget = `autoCompactAfter` (Config, Tokens), Vergleichsbasis **ohne** Toleranz; die
**+5%-Toleranz gilt nur für den Auto-Compact-Hint** (Spielraum, damit das LLM noch Tools
aufrufen kann). **Schätzung an EINER Stelle:** `ChatMessageUtil.estimateTokens` (chars×2/7,
bewusst über-schätzend) ist der einzige Estimator — auch für den Compact-Input. Der alte
`join/4`-Ansatz ist verworfen (schätzte niedriger → Kürzung greift später als gedacht; zwei
Konventionen im selben Modul, F2). Werte als Estimate tragen die `~N (estimate)`-Markierung
(Zähler-Muster, [compact.md](compact.md)).

- GIVEN `autoCompactAfter ≤ 0`/unset WHEN der Compact-Input gebaut wird THEN **nie** kürzen
  (Budget „off", wie der Hint), nur Entry-Log. *(Test: `CompactStagerTest#zeroBudgetMeansNoCap`)*
- **Gate-Seite (Q2-Verifikation 2026-09-24):** das Auto-Compact-Gate behandelt `≤ 0` ebenfalls
  als **off** (`getAutoCompactAfter() > 0`-Bedingung) — sonst feuert es bei ≤ 0 jeden Turn,
  während der Stager nie kürzt. IST-Mangel, bestehend, wurde mit dieser Story geschlossen
  (Hint-Gate macht ≤ 0 bereits sauber). *(Test: `compactWithZeroBudget_gateOff`)*

### R-CIB-2 — Loop-Filter: exakt statt Substring ✅ done

Exakte Duplikate via `Set` statt `indexOf`-Substring, O(n) (`LinkedHashSet`, keep-first) —
Zweck: LLM-Hänger (50× dasselbe File) kollabieren, ohne False-Positives. Duplikat-Hygiene wurde
an der Quelle gefixt („Compact-Result genau einmal" + R-ST4). Dedup läuft **vor** den Caps
(ungecappte Strings = exakter Vergleich; nach Caps würden verschiedene Messages kollabieren).

- GIVEN 50× identische Tool-Message WHEN der Filter läuft THEN genau einmal im Input.
- GIVEN zwei Messages mit identischem Kopf (bis Truncation), differierendem Inhalt THEN beide
  bleiben (kein Substring-False-Positive — Regressions-Gegenstück zum IST; Mutation: Set-Dedup
  zurück zu Substring → Test muss rot).

### R-CIB-3 — Entry-Debug-Log: Anfangswerte ✅ done

**Sobald der Compact-Auftrag durch das Compact-Tool geht, genau EIN debug-Log** — vor jeder
Kürzung, mit den Anfangswerten: `agent, messageCount, estimatedInputTokens, budget,
thinkingEnabled` — damit Paul die Werte mit der UI (Token-Header) abgleichen kann. Bei
Budget „off" (≤ 0) ist das Entry-Log der einzige *Compact*-Log. **Klarstellung (Nachbau-Review
2026-09-26, [compact.md](compact.md)):** seit dem monitor-freien Service schreiben die internen
Compressor-Chat-Events zusätzlich `log.debug`-Zeilen (log-only Monitor) — der Entry-Log bleibt
die EINE *Compact-Ereignis*-Zeile; die Compressor-Debug-Zeilen sind kein Verstoß gegen dieses BDD.

### R-CIB-4 — Stufen-Kürzung über Budget ✅ done

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

### R-CIB-5 — Disclosure: einmal am Ende ✅ done

Wurde gekürzt: der Compact-Input endet mit **`session truncated`** plus **einer Zeile** mit den
angewandten Caps („thinking capped 9000 (front), tool results 6000, per-message cap N,
duplicates collapsed M") — einmal am Input-Ende, **nicht** in jeder gekappten Message (User-
korrektur des heutigen IST). Nicht gekürzt → kein Hinweis. `compressor.md`-Prompt wird
erweitert, damit das Modell die Disclosure-Zeile versteht.

### R-CIB-6 — Compact-Logging: Log + Agent ✅ done

**Eine Result-Zeile pro Compact-Versuch** — dieselben Zahlen an zwei Empfänger:

- **Log:** Stufe (info Stufe 1 / warn Stufe 2 / error Endstufe), estimate vor/nach,
  Dropped-Menge (Zeichen), Result-Größe, Modell, Dauer; bei Failure Ursache + Zahlen
  (Fehlerklassen kommen mit der Compact-Retry-Story — [compact.md](compact.md)). „Log OR
  throw"-Regel unberührt (Result-Zeile ist kein Exception-Ersatz).
- **Zum Agenten:** die `CompactResult`-Zeile (Tool-Ergebnis an das LLM) trägt dieselben Zahlen
  („compressed 61 messages ~114k → input ~40k, result 2.3k, stage: tool results 6000") — das
  LLM versteht im nächsten Turn, was passiert ist. `CompactResult` wurde dafür vom Enum zum
  **Record** mit den Zahlen erweitert (Da-Dok F9; alle `compact()`-Caller per Grep verifiziert).

## BDD

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
