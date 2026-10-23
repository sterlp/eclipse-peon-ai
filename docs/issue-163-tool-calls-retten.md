# Issue #163 — Tool Calls retten (Ticket-Entwurf)

> Standalone-Entwurf, direkt ins GitHub-Ticket kopierbar. Keine internen Links.
> Recherche-Stand: 2026-10-23. Status: Backlog, low prio — Paul entscheidet, ob/wann gebaut wird.

## Symptom

Das Modell gibt einen Tool-Call manchmal als Fließtext aus, statt das strukturierte
Tool-Call-Protokoll zu benutzen. Der Text sieht aus wie XML-artige Function-Tags
(`function=…` mit Parametern), landet aber als normale Assistenten-Antwort im Chat.
Der Agent-Loop interpretiert ihn als finale Textantwort — der Tool-Call geht verloren,
der Turn endet, obwohl der Nutzer eigentlich eine Tool-Aktion erwartet hat.

Beobachtet vor allem bei schwächeren/lokalen Modellen und bei Parametern mit
Sonderzeichen (Unicode), die das Modell dazu verleiten, den Call als Text zu
„schreiben" statt zu strukturieren.

## Ursachenanalyse — langchain4j ist NICHT das Problem

Geprüft (2026-10-23) anhand des langchain4j-Quellcodes (Aggregator-Checkout):

- **OpenAI:** `OpenAiUtils` (Zeilen ~514–534) mapt ausschließlich die strukturierten
  Felder der Antwort (`toolCalls`) in `ToolExecutionRequest`-Objekte. Text und Tool-Calls
  werden getrennt geführt; nichts wird geparst oder zusammengeführt.
- **Anthropic:** `AnthropicMapper` (Zeilen ~392–406) analog — nur strukturierte
  `tool_use`-Blöcke werden gemappt.
- **Ollama:** `InternalOllamaHelper` (Zeilen ~92–100) analog — nur strukturierte Calls.

Ein Call, der als Fließtext ankommt, ist auf Provider-Seite **korrekt verarbeiteter
Text**: das Modell hat das Protokoll schlicht nicht benutzt. Ein Upstream-Issue bei
langchain4j ist daraus nicht ableitbar — das Rescuing wäre Peon-eigene Heuristik.

## IST in Peon

`ToolService` (llmpeon-core) entscheidet nach jedem LLM-Call:

- Hat die Antwort Tool-Execution-Requests → Tool-Loop läuft weiter.
- Sonst (nur Text) → der Text wird als finale Antwort behandelt, der Loop bricht ab.
  Ein Rescue-Pfad existiert heute nur für den Sonderfall „nur Thinking, kein Text und
  keine Tool-Calls" — nicht für „Tool-Call als Text".

## Lösungsvorschlag: Rescue-Heuristik im ToolService-Loop

Vor dem „nur Text"-Exit:

1. Den finalen Text auf `function=<Name>`-Muster prüfen — **nur wenn `<Name>` exakt
   einem registrierten Tool entspricht** (kein Pattern-Matching auf beliebige Namen,
   damit kein False-Positive-Risiko: Fließtext, der zufällig „function=" enthält,
   bleibt normale Antwort).
2. Treffer → aus dem Text einen `ToolExecutionRequest` bauen und den Call **regulär**
   über den bestehenden Tool-Pfad ausführen (gleiche Validierung, gleiche
   Ergebnis-Verarbeitung wie bei strukturierten Calls).
3. **Ein Rescue-Versuch je Turn** — kein Loop: erkennt die Heuristik erneut Text statt
   Call, endet der Turn mit der Textantwort (inklusiv einer ehrlichen Hinweis-Zeile,
   dass der Tool-Call nicht ausgeführt werden konnte).
4. UI-Disclosure: der gerettete Call erscheint im Chat wie jeder andere Tool-Call,
   als Ursprung „rescued from text" — der Nutzer sieht, dass hier heuristisch
   eingegriffen wurde.

Aufwand: klein–mittel (~60–100 Zeilen + Tests). Testbilder: registrierter Name im
Text → Rescue greift; unbekannter Name im Text → normale Textantwort; zweiter
Rescue-Versuch im selben Turn → wird nicht ausgeführt.

## Optional dahinter: System-Prompt-Stabilisierung

Zusätzliche Prompt-Zeile, die das Modell auffordert, Tool-Calls ausschließlich über
das Protokoll zu senden. Allein unzuverlässig (gerade die betroffenen Modelle halten
sich nicht dran) — als Ergänzung zur Heuristik denkbar, nicht als Ersatz.
