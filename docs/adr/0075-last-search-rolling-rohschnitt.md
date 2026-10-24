# ADR-0075: Last-Search-Rolling — Rohschnitt statt Paar-Grenzen

**Status:** Accepted (2026-10-24, Paul — gegen Jons Empfehlung)

**Context:** Der Hot-Block für Da Sniffa (`last-search-rolling`, Regeln in
`org.sterl.llmpeon.core/docs/search-agent-tool.md` R-LSR-1…6) braucht eine Trim-Strategie am
10.000-Zeichen-Cap. Zwei Varianten: ganze Paare droppen (Jons Empfehlung — fragmentfrei, da wir
das Paar-Format selbst schreiben und Delimiter kennen) vs. roher Char-Schnitt. Pauls
Randfall-Frage „ein Paar >10k" hat die Entscheidung gebracht: Jons 2-Stufen-Kappung (Antwort am
Kopf kappen, damit Paar-Grenzen immer auflösbar bleiben) vs. der simpel mechanische Schnitt.

**Decision:** Rohschnitt — der Block wird auf die letzten 10.000 Zeichen gekappt, Fragmente (z. B.
eine alte Antwort ohne ihren Frage-Header am Blockanfang) sind bewusst akzeptiert. Die Kappung
wird per Disclosure-Zeile `(older context dropped to fit cap)` genannt (R-LSR-4). Kein
Config-Schalter — der Block ist immer an (YAGNI).

**Consequences:** Implementierung ist ein substring — minimal. Der Blockanfang kann mitten in
einem alten Paar liegen; der Disclosure-Header erklärt das dem Modell. Künftige Vorschläge
„ganze Paare kappen" sind durch dieses ADR entschieden und werden nicht erneut aufgemacht.