# ADR-0063 — Default-Config hat einen Owner: die Basic-Seite

**Status:** Accepted (2026-09-29, Paul)
**Kontext:** [default-inheritance.md](../default-inheritance.md) Rework R-DEF-9…11 · supersedes die
Advanced-DEV-Sektion aus [ADR-0062](0062-base-inheritance-dev-default.md) (R-DEF-4/5/7) ·
Nachtrag zu [ADR-0060](0060-model-config-widget-live-widget-reads.md) (Live-Widget-Reads)

## Context

Nach dem Inc-4-Bau fand der Paul-Smoke: (1) Extra-Body-Feld provider-gated **einmalig beim
Page-Bau** (nur nach Apply + Tab-Wechsel sichtbar) und stille Löschung des gespeicherten
extraBody/Think beim Advanced-OK mit geschlossenem Gate. (2) **Doppelt-Editor-Falle** (bewiesen,
Da-Mek-IST-Analyse): der JFace-Preference-Dialog ruft `performOk` auf jede besuchte Seite — Basic
zuerst, Advanced zuletzt; beide schrieben alle Base-Keys + Dev-Slot aus ihrem Lade-Stand →
Basic-Edits verloren gegen Advanced-Stale; fehlender Provider-Key materialisierte den
DefaultScope-Default (OLLAMA). „Er speichert nicht immer, schwer zu reproduzieren" = genau dieser
Mechanismus (hängt an der Tab-Besuchs-Reihenfolge).

## Decision

1. **Ein Owner für die Default-Config: die Basic-Seite** („Default for all agents") — kompletter
   Slot inkl. Extra body (JSON) via `ExtraBodyWidget`.
2. **Advanced verliert die DEV-Sektion und schreibt keine Base-Keys mehr** — nur
   PO/PLAN/SEARCH/COMPACT-Overrides + Global-Settings. Dadurch erledigen sich Stale-Überschreibung,
   Default-Materialisierung und Copilot-Login-Überschreibung strukturell statt per Patch.
3. **Versteckt ≠ löschen:** Extra-Body- und Think-Felder folgen dem Provider live; nicht
   anwendbare gespeicherte Werte bleiben im Store erhalten (werden nur nicht gesendet). Kein Pfad
   löscht diese Keys mehr still.
4. Clean Break bleibt: `llm.agent.dev.url/apiKey/model` nie gelesen (dev ist Default-Slot, hat
   aber nur noch EIN Bedienpanel).

## Consequences

- Konfliktfreie Saves: jede Konfig-Höhe hat genau einen Editor (Basic = Default + Base-Keys,
  Advanced = pro-Agent-Overrides). „Speichert nicht immer" ist als Klasse tot.
- Der JFace-„performOk auf jede besuchte Seite"-Mechanismus bleibt plattformseitig — er ist
  harmlos, sobald sich die Pages keine Keys mehr teilen; Fallstrick für künftige Pages: ein
  Key, zwei Pages = Verlust-Race (in den eclipse-preferences-Skill gehört das schon).
- ExtraBodyWidget wird von Basic (und den Advanced-Sections) geteilt; das provider-gate ist
  live (Konstruktions-Gate entfällt im Widget-Kontext).
