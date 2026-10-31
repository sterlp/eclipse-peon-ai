# ADR-0079 — Anzeigenamen ohne Ork-Namen (V1-Namensalignment vorgezogen)

## Status

Accepted (Paul, 2026-10-30)

## Context

Die V2-Tabelle ([builtin-agent-prompt-tracked.md](../../org.sterl.llmpeon.core/docs/builtin-agent-prompt-tracked.md))
sieht ids `jon/plan/dev/review/search` mit Anzeigenamen Jon/Plan/Dev/Review/Search vor — die
Ork-Namen (Da Thinka · Da Mek · Da Dok · Da Sniffa) sollten dort „in V2" entfallen. Der
Origin-Zyklus ([ADR-0078](0078-origin-in-core-owner-monitor.md)) stempelt `origin` = Core-Id an jede
Zeile; beide Consumer (Plugin-Header, Web-Fabrik) machen daraus die Absender-Anzeige. Die frühere
Empfehlung („Offene Entscheidung" in agenten-namen-im-chat.md) war: Ork-Namen, zur Konsistenz mit den
TOOL-Fortschrittszeilen.

## Decision

- Die Anzeigenamen werden **jetzt** (V1, vor V2) umgestellt: **Jon · Plan · Dev · Review**;
  Sub-Loop-Stempel **`Search`** und **`Compact`**. Die Ork-Namen entfallen vollständig (Prompts,
  Header, Fabrik) — gegen die frühere Empfehlung; die Konsistenz entsteht über die neuen Namen
  selbst, nicht über die Figuren.
- **Kein** V2-Scope: Config-Ids (`po`, `plan`, `dev`, `review`) und `llm.agent.*`-Keys bleiben
  unangetastet; ids und Datei-first-Config kommen mit V2.
- **History-Clean Break:** die History-Dateinamen sind heute `<getName()>-history.jsonl` und folgen
  damit dem Namen — mit der Umbenennung beginnen die Agent-Verläufe neu. Keine Migration;
  Release-Note: alte `Peon-*-history.jsonl` lassen sich bei Bedarf umbenennen
  (`Peon-Dev-history.jsonl` → `Dev-history.jsonl` usw.).

## Consequences

- `origin` trägt sofort die sprechenden Anzeigenamen — Plugin-Header und Web-Fabrik zeigen dasselbe,
  für Built-ins ohne Mapping-Schicht.
- Ein einmaliger State-Bruch (Agent-Verläufe) — bewusst, nach AGENTS.md „Clean break over migration".
- V2 muss beim id-Renaming (`jon` statt `po`) die History-Keys erneut anfassen — dieser Bruch wird
  dort nicht nochmal kaschiert.
