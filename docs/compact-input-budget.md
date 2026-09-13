# Compact Input Budget (AiCompressorAgent)

**Status:** ❌ specified (2026-09-13, Design-Abnahme User) — **Light-Version geplant**: Umsetzung
zusammen mit der Noise-Idee (❓ in open-points.md — Context-Items zuerst raus), Details vor dem
Bau gemeinsam verfeinern. R2 (exakter Loop-Filter) **IST bereits gebaut durch User**
(LinkedHashSet-Dedup, Cap 4000); R1/R3/R4/R5 unbaut.

**Vorgeschalteter Fix (eigener Zyklus, eigenes Release):** Compact-Slot-Bug — siehe unten,
Abgrenzung.

## Problem

Der Compact-Input ist heute nur per-Message begrenzt (`ChatMessageUtil.toString(msg, false, 3000)`,
Head-Truncation). Es gibt kein Gesamt-Budget — der Compact ist aber der Recovery-Pfad für eine
übergroße History: genau dann, wenn er gebraucht wird (Manual-Compact nach explodiertem Context),
kann sein Input selbst den Context sprengen und fehlschlagen.

Zweitens filtert der aktuelle Loop-Filter (`msg.indexOf(txt) < 0`) Duplikate per **Substring**-Match:
getrimmte identische Köpfe (z.B. zweimal dasselbe File gelesen, Inhalt differiert erst nach Zeichen
3000) lassen den **neueren** Read stumm verschwinden — die Summary basiert auf dem alten Stand,
ohne Disclosure. Und er ist O(n²).

## Ziele

1. **Compact liefert immer** — der Input passt in den Context des COMPACT-Modells.
2. **Kürzung nur auf Druck** — unter Budget bleibt alles vollständig; je weiter über Budget,
   desto aggressiver.
3. **Ehrlichkeit** — jede Kürzung/Streichung ist sichtbar (Marker), nie still.
4. **Loop-Filter präzise** — wiederholte identische Messages (LLM-Hänger) kollabieren, ohne
   False-Positives zu erzeugen.

## Regeln

### R1 — Budget & Estimation (festgelegt, User 2026-09-13)

Budget = `autoCompactAfter` (Config, Tokens) — **kein neues Config-Feld**. Token-Schätzung =
`join().length() / 4` über den fertigen Input-String (leicht konservativ).

**Toleranz: `+5%` über Budget ist akzeptiert** — z.B. `autoCompactAfter = 140k` eingestellt,
llama.cpp erlaubt ~150k → 140k + 5% passt. **User-sichtbar → Homepage muss die Toleranz
dokumentieren** (Teil dieser Story).

### R2 — Loop-Filter: exakt statt Substring (User 2026-09-13, **IST: gebaut** — `LinkedHashSet<String>`, keep-first)

Exakte Duplikate via `Set` statt `indexOf`-Substring, O(n). Begründung: Zweck ist das Filtern von
LLM-Hängern (50× dasselbe File), nicht Duplikat-Hygiene — die wurde an der Quelle gefixt
(„Compact-Result genau einmal", `1f2d0b0`/`ce3483d` + R-ST4).
Anmerkung: bei *exaktem* Match ist keep-first semantisch gleichwertig zu keep-last (gefilterte
Instanzen sind byte-identisch zum Behaltenen) — das frühere keep-last-Argument galt nur der
Substring-Variante. Per-Message-Cap im IST aktuell 4000 (statische Head-Truncation —
Tail-Verlust-Problem bleibt, siehe R3).

### R3 — Kürzung über Budget: Stufen (festgelegt, User 2026-09-13)

Trigger: `estimate > Budget + 5%` (R1). Nach jeder Stufe Re-Estimate (`join().length() / 4`):

1. **Sanft:** Think bleibt weg (immer, `ChatMessageUtil.toString(msg, false, …)` — bewusst:
   Think ist Derivat des Inhalts, für die Summary wertlos). Tool-Results bekommen einen Cap
   **zwischen 8000 (knapp drüber) und 4000 (weit drüber)**, proportional zur Überschreitung.
   User/AI-Messages bleiben unangetastet.
2. **Mittel:** immer noch drüber → **jede** Message auf **3000** kürzen.
3. **Endstufe:** immer noch drüber → R4 (älteste streichen).

Konsequenz: unter Budget wird **gar nicht** gekürzt — auch ein einzelnes großes Tool-Result nicht
(es passt ja). Der bisherige statische 4000er-Cap fällt als Blanko-Weg.

### R4 — Endstufe: Älteste streichen (festgelegt, User 2026-09-13)

Immer noch drüber (sehr alte History, Manual-Compact): von **vorne** (älteste) Messages **ganz
streichen**, nur `type:` + Marker, bis `estimate ≤ Budget + 5%` oder nur noch kurz drüber.
Der Compact muss immer liefern — Versagen ist keine Option (R-Ziel 1).

### R5 — Disclosure (festgelegt, User 2026-09-13)

Wurde gekürzt oder gestrichen, bekommt der Compact-Input am Ende den Hinweis
**`session truncated`** — der Compressor weiß damit, dass die Quelle unvollständig ist.

## BDD (Draft — Testnamen stehen noch nicht fest)

```
GIVEN History unter Budget (estimate ≤ Budget + 5%)
WHEN der Compressor baut seinen Input
THEN keine Message wird gekürzt oder gestrichen
AND der Input enthält alle Messages vollständig (modulo Think)

GIVEN 50× identische Tool-Message in der History (LLM-Hänger)
WHEN der Loop-Filter läuft
THEN der Input enthält die Message genau einmal

GIVEN zwei Messages, deren Kopf (bis Truncation) identisch ist, deren Inhalt differiert
WHEN der Loop-Filter läuft
THEN beide bleiben im Input (kein Substring-False-Positive — Regressions-Gegenstück zum IST)

GIVEN Input knapp über Budget + 5% (Stufe 1)
WHEN die sanfte Kürzung läuft
THEN Tool-Results sind auf 8000 gekürzt, User/AI unverändert
AND estimate ist danach ≤ Budget + 5% (oder Stufe 2 greift)

GIVEN Input weit über Budget (Stufe 1 reicht nicht)
WHEN Stufe 2 läuft
THEN jede Message ist auf 3000 gekürzt

GIVEN Input auch nach Stufe 2 noch über Budget + 5%
WHEN Stufe 3 läuft
THEN die ältesten Messages sind gestrichen (nur type: + Marker)
AND estimate ist danach ≤ Budget + 5% oder nur kurz drüber

GIVEN es wurde gekürzt oder gestrichen
WHEN der Input gebaut ist
THEN der Input endet mit dem Hinweis "session truncated"

GIVEN es wurde NICHT gekürzt
WHEN der Input gebaut ist
THEN enthält der Input keinen "session truncated"-Hinweis
```

## Abgrenzung

- **Vorgeschalteter Fix ✅ (2026-09-13, `efa22df`, Zyklus `fix/compact-slot-model`):** der
  Compact-Slot-Bug ist behoben — `AiCompressorAgent` routed den Call über
  `ConfiguredChatModel.modelFor(agent)` (ADR-0034); COMPACT-Slot-URL/Key/Modell gelten,
  leerer Slot → Base. Details in [advanced-configuration.md](advanced-configuration.md).
  R1 (Budget) baut damit auf dem COMPACT-Modell auf — das war vorher der Grund, den Fix
  vorzuziehen (Budget-Tuning aufs falsche Modell gerechnet).
- **Nicht Teil:** Auto-Compact-Trigger-Logik (`autoCompactAfter` wird nur als Budget gelesen,
  das Trigger-Verhalten ändert sich nicht).
