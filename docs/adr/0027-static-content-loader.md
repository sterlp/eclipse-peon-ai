# ADR-0027: StaticContentLoader — effizientes Dateiladen mit Duplikat-Prüfung

**Status:** Akzeptiert · **Datum:** 2026-08-11 · **Betroffen:** Jon (Peon-PO), allgemein nutzbar

## Kontext

Jon braucht Auto-Load von `memory.md` + `docs/index.md` bei Session-Start und nach Compact. Statt Content bei jedem Turn neu zu laden (Token-Verteuerung), sollen Dateien nur einmal pro Session geladen werden — mit sichtbarer Token-Transparenz.

## Entscheidung

1. **`StaticContentMessage` als record** — kein ChatMessage-Interface. Langchain4j `ChatMessage` ist ein Interface; Extension会导致 Serialization-Probleme. Record hält nur Pfad, wird zu `UserMessage` expandiert vor Memory-Eintritt.

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
