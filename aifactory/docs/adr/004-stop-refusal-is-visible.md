# AF-ADR-004 — Eine abgelehnte Aktion bleibt sichtbar (2026-11-11)

**Status:** Accepted (Paul 2026-11-11) · **Context:** Bug-Zyklus B7b/Q4, Fabrik-UI

## Context

2026-11-10 hatte Jon in `api-errors.md` eine **Absorptions-Ausnahme** beschlossen: antwortet der Server
auf einen **Stop** mit `409` („nichts mehr zu stoppen"), schluckt die Oberfläche die Antwort still —
Begründung: der Nutzer habe nichts Neues gewollt, die Meldung sei Rauschen, und das Plugin biete den
Knopf in dem Fall gar nicht erst an. Paul hat im Browser genau das als Fehler gemeldet.

## Decision

Paul entscheidet **gegen** diese Empfehlung (2026-11-11): „ja 409 vom server ist richtig — der Fehler war
nur, das generische Fehler-Widget sollte es anzeigen + der button sollte disabled sein im UI."

Die **fachliche** Aussage von 2026-11-10 bleibt unangetastet: eine laufende **Aktion** (Clear, Compact)
ist kein stoppbarer Turn, der Server lehnt weiter mit `409` ab. Verworfen ist nur ihre **Unsichtbarkeit**:
die Ablehnung zeigt das Error-Panel, und die Stop-Taste wird dort nicht mehr angeboten, wo es nichts zu
stoppen gibt.

## Consequences

* Zwei Wege, nicht einer: **verhindern** (Taste deaktiviert, Aussage im bestehenden Kachel-Snapshot) und
  **sichtbar machen** für die Zeitlücke zwischen dem Lesen des Stands und dem Klick. Beides ist nötig —
  die Deaktivierung allein kann die Renne nie schließen.
* `chat.client.ts` verliert seine „already over"-Ausnahme; der generische Anzeige-Pfad wird nicht
  verlängerbar um „außer hier".
* Dauerhafte Regel: **keine Server-Ablehnung auf eine Nutzer-Aktion bleibt unsichtbar.** Ein „das ist
  doch erwartbar" ist kein Grund zum Verschlucken — höchstens einer, die Aktion erst gar nicht
  anzubieten. (Regeln: [R-WP-65](../chat.md), [R-WP-66](../api-errors.md).)
* Der Deckel auf der alten Ausnahme muss bleiben: wer 2026-11-10 liest und „Rauschen vermeiden"
  wieder aufgreift, entscheidet erneut gegen Pauls Wort.
