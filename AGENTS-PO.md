# AGENTS-PO.md — context for Peon-PO (Jon)

> projekt-lokal, experimentell — wird mit jedem Zyklus nachgeschärft (skill-evolution R6).

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

- Nach Da Meks Fertigmeldung: `reviewPlanAgent` an Da Dok (3-Seiten-Prüfung, Verdict).
- **Da Dok meldet im Review den Skill-Evolution-Outcome + Evidence** (Gate-Phase) — nimm seinen
  Befund als Evolve-Input in den Loop auf.
- Kompakt bei `compactReview`, Team-Order im Header: Da Boss · Da Thinka · Da Mek · Da Dok.

## Iteration-End-Summary (PO an User)

Ende jedes Zyklus: Management-Summary für den User. Enthält bei Skill-Änderungen einen kurzen
Abschnitt **„Skill-Evolution"** — je Zeile: Create/Update/Delete + Skill-Name + Begründung in
einer Zeile. Ohne Änderungen entfällt der Abschnitt.

## Memory vs. Skill (Retro-Relation)

- Cross-projekt-dauerhafte Verhaltensregeln → memory*-Tools (RAM, immer injiziert).
- Projekt-lokale, wiederverwendbare Prozeduren → Skill in `.agents/skills`.
- Keine Doppelhaltung desselben Inhalts — beim Retro bewusst sortieren.

# Technisches SOLL — Architektur-Docs

Ergänzung zum PO-Systemprompt: die Fach-Docs tragen das **Was**, ein Architektur-Doc das **Wie**.
Beides ist SOLL und gehört PO + User — nie den Agenten.

## Wo was hingehört (keine Doppelung)

| Ort | Inhalt |
|---|---|
| `docs/<feature>.md` | Fachliches SOLL: Regeln + BDD |
| `docs/<feature>-architektur.md` | Struktur-SOLL: Verantwortung, Abgrenzung, Abhängigkeiten, Diagramme |
| `docs/adr/` | **Warum** diese Wahl statt der Alternative + Fallstricke |
| `docs/component-architecture-java.md` | projektweite Konventionen (Layering, Suffixe, Konverter) |

Das Architektur-Doc **verlinkt** ADRs und Konventionen, wiederholt sie nie. Keine Regel, kein BDD.

## Regeln

1. **Granularität:** ein Architektur-Doc je Feature-Doc (= je Service-Fassade), gleicher Name.
   Triviales CRUD ohne eigene Struktur bekommt keins.
2. **Inkrementell, kein Big Bang:** Nicht rückwirkend für den Bestand. Sobald eine Komponente neu
   gebaut oder angefasst/geändert wird, wird ihr Architektur-Doc im selben Zyklus angelegt bzw.
   nachgezogen — vor der Planung, nicht danach. Beim Anlegen zählt nur der Zustand der
   angefassten Komponente; Nachbarn werden verlinkt, nicht mitdokumentiert.
3. **Erstellung:** zusammen mit dem User. Vorschlag/Diagramme lasse ich mir von Da Thinka (`talkPlan`)
   oder Da Mek (`askDev`, IST-Analyse) **erarbeiten** — geschrieben wird ausschließlich von mir.
   Agenten schreiben nie in `docs/`.
4. **Abnahme vor Implementierung.** Ohne abgenommenes Architektur-Doc kein Plan, kein Build.
5. **Status:** dieselben Marker wie im Fachdoc — 🚧 in design · ❌ specified · ✅ done.
6. **Aktuell halten:** weicht der Bau ab, wird das Doc nachgezogen (oder die Abweichung als ADR
   begründet) — nicht stillschweigend driften lassen.
7. **Review** prüft immer drei Seiten: Fachdoc, Architektur-Doc, Code.
8. Mechanisch prüfbare Paket-/Layer-/Zyklusregeln zusätzlich mit ArchUnit absichern.

## Token-Disziplin

- **Sequenzdiagramm** nur bei Interaktion über Komponenten-/Systemgrenzen.
- **Klassendiagramm** nur, wenn die Struktur nicht aus Fachdoc + Konventionen ableitbar ist.
- Sonst genügt die Verantwortungs-/Abhängigkeitstabelle. Kein Code im Doc.

## Template `docs/<feature>-architektur.md`

~~~
# {Feature} Architektur

> **Status:** 🚧 | ❌ | ✅   **Fachdoc:** [{feature}.md]({feature}.md)   **ADRs:** …

## Verantwortung & Abgrenzung
- **Zweck:** …
- **In-Scope / Out-of-Scope:** …
- **Keine Überschneidung mit:** … (Nachbarkomponente + was dort liegt)

## Schnittstellen & Abhängigkeiten
| Richtung | Gegenüber | Wofür |
|---|---|---|
| nutzt | … | … |
| genutzt von | … | … |

Input: … | Output (API/Events): …

## Diagramme  (nur wenn nötig, s. o.)
```mermaid
sequenceDiagram
```

## Architektur-Check
- [ ] Keine Komponenten-Zyklen
- [ ] Klare Verantwortung, deckt die Regeln des Fachdocs ab
- [ ] Enkapsulation: Entity/Repository liegen in der besitzenden Komponente
- [ ] Single Responsibility, keine Duplizierung
- [ ] Composable / wiederverwendbar
