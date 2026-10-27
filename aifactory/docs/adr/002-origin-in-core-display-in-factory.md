# AF-ADR-002 — Herkunft je Chat-Zeile: der Core stempelt, die Fabrik entscheidet

**Status:** Accepted (2026-10-26, Inc 3f)

## Context

Der Chat soll **nur** Jon und die **finalen** Antworten seines Teams zeigen (R-WP-4, UC-WP-39/40). Der Core
gab Jons Monitor an jedes delegierte Mitglied weiter, damit war jede Mitglieder-Zeile live als Jons Zeile
unterwegs und die fertige Mitglieder-Antwort erreichte den Chat **gar nicht**. Naheliegende Auswege, alle
verworfen: in der Fabrik den Werkzeug-Text parsen (`statsLine`, `charsPrefix`) — ein zweiter, stiller
Vertrag auf Core-Formulierungen; eine zweite Chat-Datei — Verstoß gegen R-WP-11; „die letzte Antwort eines
Agenten ist die finale" — läßt Denk-Zwischenprodukte durch und unterscheidet zwei Delegationen nicht;
im Core filtern — ändert das Plugin (PR-3).

## Decision

1. **Der Core stellt die Tatsache, nicht die Anzeige.** Ein Monitor-Umschlag (`OriginMonitor`) stempelt die
   **Kern-Id** am Ort der Erzeugung (`SimpleMessage.origin`, `OnPartialAiResponse.origin`) — nur wenn noch
   keine Herkunft gesetzt ist, damit die innerste (besitzende) Umschreibung gewinnt. Anzeigenamen kennt
   nur die Fabrik (`AgentNames`), unbekannte Herkunft bleibt `null`; niemand rät einen Namen.
2. **Ableitungen werden mitgestempelt, aber nie ein Override verschluckt.** `onTool`/`onProblem`/
   `onFileUpdate` baut der Umschlag selbst (über das eigene `onChatResponse`); roh weiterreichen darf er nur
   an einen Delegate **ohne** eigenen Override (Reflection auf die deklarierende Klasse) und **nie** an einen
   verschachtelten Umschlag. Grund: `AIChatView` des Plugins überschreibt `onFileUpdate` (Diff-Ansicht).
3. **Die finale Team-Antwort ist eine eigene Tatsache** — der Text, den das Delegations-Werkzeug an Jon
   zurückgibt, gemeldet per `onDelegatedAnswer` und als langchain4j-`attributes`
   (`delegatedAgent`/`delegatedAnswer`) am Werkzeug-Ergebnis in **Jons** History abgelegt. Attributes gehen
   nicht ans Modell; der Rundlauf durch die History ist durch einen Kern-Test festgenagelt (kein zweiter
   Speicher).
4. **`done` kommt nur vom äußersten Agenten** (`onAgentCallCompleted`, vom Umschlag durchgelassen). Ein
   Mitglieds-Ende ist kein Turn-Ende.
5. **Vierter Draht-Name `delegated`** `{origin,text}` — die Team-Antwort braucht nach der Filterung einen
   eigenen Kanal; sie ist die einzige Live-Zeile, die nicht von Jon stammt.

## Consequences

* Der Core bleibt für das Plugin verhaltensgleich: die zwei neuen `AiMonitor`-Methoden sind leer, keine
  Signatur entfernt; `onChatResponse` bleibt die einzige abstrakte Methode (62 Implementierungen).
* Ein **fünfter** Ereignis-Typ ist billig: `ChatStreamRegistry.toSse` schaltet erschöpfend über das
  versiegelte `ChatEvent` — es kompiliert nicht mehr, statt den Zustell-Thread eines Zuschauers zu töten.
* Das **Live-Fragment** eines Turns trägt keinen Namen (live spricht nur Jon); der Name gehört zur
  dauerhaften Zeile ([web-prototype R-WP-4](../web-prototype.md)).
* Pro Agent gibt es **keinen** Mitschnitt und keinen zweiten Verlauf; die Kacheln (Inc 5) sind eine andere
  Ansicht auf dieselbe Memory, kein neuer Speicher.
* Wer die Herkunft „einfacher" aus dem Antworttext ableiten will, baut einen zweiten Vertrag auf
  Core-Formulierungen — dagegen steht diese ADR.
