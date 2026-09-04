# ADR-0039 — „extra body gewinnt" wird durch Streichen des typisierten Feldes umgesetzt

**Status:** Accepted (2026-09-03)

## Context

[advanced-configuration.md](../advanced-configuration.md) R-T3 verlangt: setzt der User
`temperature` **sowohl** im Temperature-Feld **als auch** im per-agent extra body, gewinnt der
Body — konsistent zur bestehenden Merge-Regel „User-Body gewinnt".

Die naheliegende Annahme wäre, dass sich das von allein ergibt: der Body wird ohnehin in den
Request gemerged, also überschreibt er das typisierte Feld. **Das ist falsch.**

Recherche in langchain4j (Planungsrunde 3b, in der Quelle verifiziert): `customParameters`
werden per `@JsonAnyGetter` **zusätzlich** zu den typisierten Feldern serialisiert
(`ChatCompletionRequest`, `AnthropicCreateMessageRequest`). Ein `temperature` im Body erzeugt
damit einen **doppelten JSON-Key** im Request. Weitere in derselben Recherche (Plan 2a, §2,
langchain4j 1.18.1) verifizierte Fakten: `customParameters` wird **shallow** geflattet (kein
Deep-Merge verschachtelter Objekte), Builder-Setter sind **REPLACE**, nicht MERGE, und
langchain4j hat **keinen** eigenen Reserved-Key-Schutz — jeder Schutz muss von uns kommen
(`ExtraBody.RESERVED_KEYS`). Welcher gewinnt, entscheidet der Parser der
Gegenseite — bei JSON ist das implementierungsabhängig und kein tragfähiger Vertrag. Der
Nutzer bekäme je nach Provider ein anderes Ergebnis, ohne dass irgendetwas im Log darauf
hindeutet.

## Decision

`ProviderRequestSupport.applyBase` setzt nicht mehr direkt `mc.getTemperature()`, sondern
`effectiveTemperature(mc)`: das typisierte Feld wird **aktiv auf `null` gesetzt**, wenn

1. der Provider `supportsExtraBody()` liefert **und**
2. der geparste extra body den Schlüssel `temperature` mit nicht-leerem Wert trägt.

Damit steht `temperature` **genau einmal** im Body, mit dem Wert des Users.

Das Provider-Gate ist Teil der Entscheidung, nicht Beiwerk: bei `ExtraBodyMode.NONE` (Ollama)
wird der Body verworfen — ohne das Gate fiele **beides** weg und der Agent liefe still ohne
Temperature.

`temperature` wird bewusst **nicht** in `ExtraBody.RESERVED_KEYS` aufgenommen (dort stehen nur
`model`/`messages`/`tools`): Reserved bedeutet „darf der User gar nicht setzen", hier soll er
es gerade dürfen und gewinnen.

## Consequences

- R-T3 hängt an einer expliziten, testbaren Regel statt an der Serialisierungs-Reihenfolge
  einer Fremdbibliothek. Bewiesen auf dem Draht durch
  `AgentTemperatureTest.bodyTemperatureWinsOnTheWire` (genau ein `temperature` im Body).
- `ExtraBody.parse` wird bei PER_REQUEST-Providern pro Request zweimal aufgerufen (einmal hier,
  einmal in `mergeCustomParameters`). Bei invalidem JSON gibt es dadurch zwei Warnungen.
  Akzeptiert — die Alternative wäre ein Cache für einen String-Parse.
- Die Regel gilt nur für `temperature`. Andere typisierte Felder (`maxTokens`, `think`) haben
  dasselbe Doppelkey-Problem, sind aber nicht Teil dieser Story. Wenn sie auftauchen, ist
  `effectiveTemperature` das Muster, das verallgemeinert wird.
- Regeln + BDD: [advanced-configuration.md](../advanced-configuration.md) R-T3.
