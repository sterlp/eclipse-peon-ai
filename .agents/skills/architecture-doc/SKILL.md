---
name: architecture-doc
description: Regeln und Template für Architektur-Docs (docs/<feature>-architektur.md) — Granularität je Feature-Doc/Service-Fassade, nur angefasste Komponente, ≥1 Mermaid-Diagramm, kein Code, Drift = ADR, ArchUnit. Aus AGENTS-PO.md migriert.
---

# Architektur-Docs — Regeln & Template (migrated from AGENTS-PO.md, 2026-10-20)

**Trigger:** Architektur-Doc schreiben oder prüfen.

## Regeln

- **Projektweite Konventionen:** Skill `component-architecture` (Layering, Suffixe,
  Konverter) — vierter Ort neben Fachdoc / Architektur-Doc / ADR.
- **Granularität:** ein Architektur-Doc je Feature-Doc (= je Service-Fassade).
  Triviales CRUD ohne eigene Struktur bekommt keins.
- **Anlegen zählt nur die angefasste Komponente:** Nachbarn werden verlinkt, nicht
  mitdokumentiert.
- **Drift:** weicht der Bau ab, Abweichung als ADR begründen.
- **ArchUnit:** mechanisch prüfbare Paket-/Layer-/Zyklusregeln zusätzlich absichern.
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
