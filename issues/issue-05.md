# `clear()`-Cache-Reset des System-Prompts ist ungetestet (bekanntes TODO)

Status: `✅ done (2026-08-21)` — `AbstractAgentTest.test_clear_resetsSystemPromptCache` guardet die `systemMessage = null`-Invalidation in `clear()` (Call 1 → Context ändern + clear → Call 2 assertet neuen Prompt); TODO in `AbstractAgent.java` damit abgedeckt.

## Evidenz

- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/AbstractAgent.java:305` —
  `this.systemMessage = null; // TODO test needed - AI forgot this reset case`.
- Einziger `clear()`-Test:
  `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/agent/AbstractAgentTest.java:193-212`
  (`clearDeletesOnlyThisAgentsPersistedHistory`, Methode ab Zeile 194) — assertet nur
  `memory` leer, `queuedMessageCount` 0, History-Datei gelöscht. **Nichts** prüft, dass nach
  `clear()` der System-Prompt neu gebaut wird (z. B. dass ein danach geänderter
  Static-Context/Base-Prompt im nächsten Call tatsächlich wirkt).
- Der Cache selbst ist neu relevant: seit der Branch trägt der System-Prompt Env+Memory
  (`PeonAiService.java:185-195`), d. h. ein vergessener Reset hätte jetzt sichtbare
  Auswirkung auf den Inhalt, nicht nur auf Performance.

## Problem

Konkreter Ablauf: Ein Refactoring entfernt/verschiebt die `systemMessage = null`-Zeile in
`clear()` -> alle bestehenden Tests laufen grün -> Agenten starten nach einem Clear mit dem
alten, gecachten System-Prompt (stale Env-Datum, stale Memory-Snapshot). Das TODO dokumentiert,
dass genau dieser Fall schon einmal vergessen wurde.

## Auswirkung + Schweregrad

**Risiko (Test-Lücke)** — kein aktiver Bug, aber die einzige Schutzlücke in der
Invalidations-Kette (`clear`/`compressContext`/`setStaticContext`). Fix-Vorschlag für die
Review: Test in `AbstractAgentTest`, der Static-Context setzt, Call 1 ausführt,
Static-Context ändert + `clear()` ruft und Call 2 auf den neuen System-Prompt-Content
assertet (StreamMock liefert die gesendeten Messages).
