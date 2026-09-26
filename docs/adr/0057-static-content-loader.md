# ADR-0057 (vormals 0027 — Nummer war doppelt vergeben): StaticContentLoader — effizientes Dateiladen mit Duplikat-Prüfung

**Status:** Superseded by [ADR-0032](0032-workspace-memory-dynamic-turn-context.md) (statischer Snapshot entfernt, Memory dynamisch pro Turn) · **Datum:** 2026-08-11 · **Betroffen:** Jon (Peon-PO), allgemein nutzbar

## Kontext

Jon braucht Auto-Load von `memory.md` + `docs/index.md` bei Session-Start und nach Compact. Statt Content bei jedem Turn neu zu laden (Token-Verteuerung), sollen Dateien nur einmal pro Session geladen werden — mit sichtbarer Token-Transparenz.

## Entscheidung

1. **`StaticContentMessage` als record** — kein ChatMessage-Interface. Langchain4j `ChatMessage` ist ein Interface; eine Extension bringt Serialisierungsprobleme. Record hält nur Pfad, wird zu `UserMessage` expandiert vor Memory-Eintritt.

2. **`StaticContentLoader` (core)** — eigene Klasse, allgemein nutzbar. Methode `load(List<StaticContentMessage>, ThreadSafeMemory, AiMonitor, Function<String, Path> pathResolver)`.

3. **Duplikat-Prüfung via `memory.containsUserMessage()`** — existierende Methode prüft auf `"Static loaded file <path>:\n---"`. Found → skip. History ist Single Source of Truth.

4. **Header `Static loaded file <path>:\n---\n<content>`** — Pfad implizit in History, Changes kommen als normale Messages.

5. **Callback-Hook in `AiCompressorAgent`** — `Runnable onCompacted` im Konstruktor. Beide Compact-Pfade (Tool + UI) laufen durch `AiCompressorAgent.call()`. Zentral, keine UI-Abhängigkeit.

6. **PathResolver SPI** — `Function<String, Path>` im Loader. Core bleibt testbar (disk-Path), Plugin löst Eclipse-IFile.

## Konsequenzen

- **Pro:** Token-Effizienz (einmal laden), Duplikat-Skip, Testbarkeit (core headless), allgemein nutzbar
- **Con:** Callback null-Monitor im Compact-Handler (Inc-3-Refinement), kein Auto-Refresh bei Datei-Changes (bewusst)
- **Review:** Da Thinka: "Das ist bereits die beste Lösung."

## Verwandt

- [Jon (Peon-PO)](../po-agent-jon.md) — Auto-Load memory.md + docs/index.md
- [Queued User Messages](../queued-user-messages.md) — Compact-Abbruch + Queue-Drain
