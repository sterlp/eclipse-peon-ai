# AGENTS-PO.md — Ergänzungen für Peon-PO (Jon)

> projekt-lokal, experimentell — **nur Ergänzungen** zum System-Prompt (`po.md`), keine
> Wiederholung dessen. Wird mit jedem Zyklus nachgeschärft (skill-evolution R6).

## Skill-Evolution-Loop (experimentell, docs/skill-evolution-loop.md)

Skills müssen ihren Token-Wert beweisen. Nach jedem Review-Zyklus (Da-Dok-Verdict liegt vor)
wendest du den Loop aus dem Skill `skill-evolution` an — genau **ein CRUD-Outcome pro Iteration**,
mit Evidence:

- **Create:** eine schwierige, gelöste Aufgabe wird zu einem neuen Skill, wenn die Lösung
  wiederverwendbar ist. Kurz halten (Prozedur + Anwendbarkeitsbedingung, keine Traces).
- **Update:** bewährte Skills verbessern — **kurz halten**. Wächst ein Skill, wird gestrafft
  statt erweitert. Gilt auch für diesen Text hier (R6: der Loop reift sich selbst).
- **Delete:** Skills, die mehr schaden als nutzen (falsch, veraltet, missbraucht, nie genutzt
  trotz Chance) — Löschung braucht dieselbe Evidence-Pflicht wie eine Änderung.
- **Read-Nutzung:** Agenten reporten nach skillRead (Footer), ob ein Skill hilfreich war —
  dieses Feedback ist dein Evolve-Input (aus den Replies der Sklaven sammeln).
- **Umsetzung:** Delegiere Create/Update/Delete an Da Mek (er hat beide Write-Familien und liest
  via skillRead). Scaffold bleibt künftigen Global-Skills (Config-Dir) vorbehalten.
- **Ledger:** je Outcome ein Eintrag in `.agents/skills/wiki/skill-impact.md` (accepted/rejected
  + Evidence, analog skill-evolution Gate-Phase).
- **Kein Outcome (no change)** ist ein legitimes Ergebnis — erzwingt keine Änderung.

## Review-Route (Da Dok)

- **Da Dok meldet im Review den Skill-Evolution-Outcome + Evidence** (Gate-Phase) — nimm seinen
  Befund als Evolve-Input in den Loop auf.
- Team-Order im Header: Da Boss · Da Thinka · Da Mek · Da Dok.

## Iteration-End-Summary (PO an User)

Ende jedes Zyklus: Management-Summary für den User. Enthält bei Skill-Änderungen einen kurzen
Abschnitt **„Skill-Evolution"** — je Zeile: Create/Update/Delete + Skill-Name + Begründung in
einer Zeile. Ohne Änderungen entfällt der Abschnitt.

# Architektur-Docs — Ergänzungen

- **Projektweite Konventionen:** `docs/component-architecture-java.md` (Layering, Suffixe,
  Konverter) — vierter Ort neben Fachdoc / Architektur-Doc / ADR.
- **Granularität:** ein Architektur-Doc je Feature-Doc (= je Service-Fassade).
  Triviales CRUD ohne eigene Struktur bekommt keins.
- **Anlegen zählt nur die angefasste Komponente:** Nachbarn werden verlinkt, nicht
  mitdokumentiert.
- **Drift:** weicht der Bau ab, Abweichung als ADR begründen.
- **ArchUnit:** mechanisch prüfbare Paket-/Layer-/Zyklusregeln zusätzlich absichern.

## Token-Disziplin

- **Pflicht: ≥ 1 Mermaid-Diagramm je Doc** (po.md). Sequenzdiagramm bei Interaktion über
  Komponenten-/Systemgrenzen; Klassendiagramm nur, wenn die Struktur nicht aus Fachdoc +
  Konventionen ableitbar ist.
- **Kein Code im Doc.**

## Template `docs/<feature>-architektur.md`

~~~
# {Feature} Architektur

> **Status:** 🚧 | ❌ | ✅   **Fachdoc:** [{feature}.md]({feature}.md)   **ADRs:** …

## Verantwortung & Abgrenzung
- **Zweck:** …
- **In-Scope / Out-of-Scope:** …

## Schnittstellen & Abhängigkeiten
| Richtung | Gegenüber | Wofür |
|---|---|---|
| nutzt | … | … |
| genutzt von | … | … |

Input: … | Output (API/Events): …

## Diagramme (Pflicht — Sequenz oder Klasse, je nach Fall)
```mermaid
sequenceDiagram
```

## Architektur-Check
- [ ] Keine Komponenten-Zyklen
- [ ] Klare Verantwortung, deckt die Regeln des Fachdocs ab
- [ ] Enkapsulation: Entity/Repository liegen in der besitzenden Komponente
- [ ] Single Responsibility, keine Duplizierung
- [ ] Composable / wiederverwendbar
~~~
