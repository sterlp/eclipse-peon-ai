# ADR-0077 — Agent-Config: Datei zuerst, Eclipse-Config nur als Lese-Fallback

**Status:** Accepted (2026-10-27, Paul)

## Context

Jeder Agent soll eine Datei `agents/<id>/AGENT.md` haben, die schrittweise seine ganze Config trägt
([builtin-agent-prompt-tracked.md](../../org.sterl.llmpeon.core/docs/builtin-agent-prompt-tracked.md)).
Heute liegt die Config der Built-ins in den Eclipse-Prefs. Jons Empfehlung war ein sauberer Bruch:
Datei gewinnt, sonst Eclipse; Übernahme nur über einen manuellen „Export"-Knopf.

## Decision

1. **Lesen:** Datei vorhanden ⇒ sie gilt (je Feld, das sie trägt). Sonst Eclipse-Config als **Fallback**.
2. **Schreiben:** Speichern im UI schreibt **immer** die komplette Agent-Config in die Datei — nie
   mehr in die Eclipse-Prefs. Damit wandert jeder Agent beim ersten Speichern von selbst.
3. Der Eclipse-Lesecode wird `@Deprecated` und ist nur noch Fallback; er fällt, wenn er nicht mehr
   gebraucht wird.
4. **Stufen:** V1 = nur der Rumpf (Prompt). V2 = alle Felder plus Konverter Eclipse → Datei beim Speichern.

## Consequences

* Für eine Übergangszeit gibt es zwei Quellen; die Regel „Datei sticht" ist die einzige Auflösung —
  keine Feld-Mischung aus beiden Quellen über das hinaus, was die Datei nicht trägt.
* Kein Hintergrund-/Start-Migrationslauf, keine Key-Aliase: migriert wird nur durch ein bewusstes
  Speichern — vereinbar mit „Clean break over migration" (AGENTS.md).
* Nicht wieder vorschlagen: manueller Export-Knopf (verworfen, weil Speichern ihn überflüssig macht).
