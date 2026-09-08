# ADR-0042 — Skill-Slots als Components mit disunkten Maps

**Status:** Accepted — Revision 2 (2026-09-08) · Umsetzung: ausstehend — Story [project-skills.md](../project-skills.md)

## Context

`SkillService` lädt heute genau ein Verzeichnis (`configDir/skills`) in eine `ConcurrentHashMap`
(Key = lowercase Name, last-wins, clear + befüllen). SOLL: projekt-lokale Skills in
`<Projekt-Disk-Pfad>/.agents/skills`; bei Projektwechsel werden die Projekt-Skills durch die des
neu gewählten Projekts **ersetzt**; die Config-Skills dürfen dabei **nie** überschrieben werden —
ein durch einen Projekt-Skill maskierter Config-Skill wird wieder sichtbar, wenn der Projekt-Skill
wegfällt.

## Decision

- **Slot-Component extrahieren** (Komponenten-Architektur, deep module): Die heutigen Funktionen
  (Discover + Parse + atomarer Swap) wandern in eine eigene Component, die sich über den **Pfad**
  definiert, für den sie verantwortlich ist — ihre Identität ist der Pfad. Der `SkillService` hält
  eine **Liste** dieser Components (heute genau zwei) und berechnet die effektive Skill-View als
  Komposition. **Theoretisch beliebig viele Skill-Verzeichnisse** möglich — ein neuer Slot ist nur
  eine weitere Component in der Liste (kein Service-Umbau).
- **Zwei disunkte Maps, nie gemergt im Speicher**: Config-Component (`configDir/skills`, statisch)
  und Projekt-Component (`<disk>/.agents/skills`, dynamisch) speichern getrennt. Der Override
  (R4: Projekt gewinnt bei Namenskollision) entsteht **nur beim Lesen** der effektiven View.
- **Projektwechsel ersetzt ausschließlich die Projekt-Component** — die Config-Component bleibt
  unberührt (kein Rescan, kein Verlust).
- **Atomarer Swap je Component**: neue Map vollständig bauen, dann Referenz tauschen — LLM-Calls
  sehen nie clear()+befüllen-Halbzustände.
- **Quelle am Skill**: `SkillPromptFile` bekommt `CONFIG`/`PROJECT`; **Enabled-State name-keyed im
  Service** (nicht an der Instanz) — Toggles überleben jeden Refresh/Wechsel, auch über einen
  Override hinweg (User 2026-09-08).

**Revision 2 (2026-09-08, User):** ursprünglich „eine gemergte Map, Config zuerst, Projekt
putzt zuletzt" — ersetzt durch disunkte Component-Maps mit Read-Zeit-Override. WEIL: das Config-Dir
bleibt von Projektwechseln unberührt, maskierte Config-Skills kehren automatisch zurück, und die
Liste ist erweiterbar (weitere Slots später ohne Umbau).

## Consequences

- Konsumenten lesen weiterhin EIN API (`getSkills()` = effektive View) — kein Zusatzwiring;
  `skillReadFile` via `skillDir` funktioniert auf Projekt-Disk-Pfaden.
- Config-Reload/ReloadConfigTool refreshed **jede** Component; Projektwechsel refreshed
  ausschließlich die Projekt-Component.
- Fehlendes `.agents/skills` bzw. kein gewähltes Projekt = leere Projekt-Component, kein Fehler.
- Heutiges Verhalten „Toggles weg bei Refresh" wird bewusst geändert.