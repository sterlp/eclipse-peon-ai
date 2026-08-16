# 0030: staticText()-Helper statt Core-Änderung an ChatMessageUtil

**Status:** Accepted · **Datum:** 2026-08-16 · **Zyklus:** ADR-0029

## Context

Während der ADR-0029-Implementierung fiel auf: `ChatMessageUtil.toString()` (Core, frozen)
**droppt SystemMessages** beim Rendering. `PeonAiService.setStaticContext()` baute den Static
Context über diese Methode → die OS/Date-Regeln waren **faktisch leer** im System-Prompt,
still, ohne Fehler oder Test. Seit der ContextItem-Refactoring unbeobachtet.

## Decision

Kein Core-Eingriff (frozen Modul): privater `staticText()`-Helper in
`PeonAiService.setStaticContext()` (Plugin-Layer, `PeonAiService.java`), der ContextItems
direkt per `render()` in einen Klartext-Block umwandelt. Javadoc dokumentiert das WARUM
(der Bug), damit der Helper nicht als Redundanz "weggeräumt" wird.

## Consequences

- OS/Date-Regeln sind wieder real im System-Prompt (vorher faktisch abwesend).
- Core bleibt unangetastet; die Landmine von `ChatMessageUtil.toString()` bleibt — jede neue
  Stelle, die SystemMessages über diese Methode rendert, ist silent-broken.
- Kandidat für spätere Core-Fix-Kampagne (nicht jetzt — nur aufräumen, was wir angefasst haben).
