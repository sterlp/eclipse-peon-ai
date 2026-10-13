# Task R-TD-5 — Shell-onTool-Laufzeit (state: DONE 2026-10-01 20:14 — Commit bc975abd, gepusht;
Jon hat den stats-Declarations-Move :134→vor try genehmigt (20:13); ShellToolTest 22/0/0)

Branch `bug/grep-empty-payload-test`. SOLL: 3 terminale ShellTool-onTool-Zeilen (finished/timed out/stopped)
bekommen `stats.suffix()` VOR `StringUtil.charsSuffix(result)`; Return-Strings + Start-Zeile unverändert;
kein Test; ShellTool-Tests (Surefire) laufen lassen; Commit Code + `docs/tool-time-disclosure.md`
(Jons Edit liegt im Tree: ` M docs/tool-time-disclosure.md`), push.

## IST (verifiziert 2026-10-01)

- Zeilen wie angekündigt, kein Shift: Start :126, timed out :170-171, finished :186-187, stopped :200.
- **finished (:186-187) und timed out (:170-171): FIT** — `stats` im Scope (try-Block), Einbau trivial.
- **stopped (:200, `catch (InterruptedException e)` :195): NICHT FIT — exakt der im Task genannte
  Stop-Fall.** `var stats = CallStats.start();` ist :134 **innerhalb des try-Blocks** (try startet :125)
  → im catch **nicht im Scope** → Referenz = Compile-Fehler. Kein zweites `stats` in Method-Scope.
  Der Pfad hat heute keine Dauer (auch nicht im Return :197-199) — bekannt aus dem SOLL.

## Vorschlag an Jon (einziger Weg, SOLL am stopped-Pfad zu erfüllen)

`var stats = CallStats.start();` von :134 nach :124 (nach `List<String> lines = ...`, vor `try {`)
verschieben — 1 Zeile, kein Verhaltenstouch:
- Return-Strings bleiben unverändert (sie nutzen `stats` wie heute, nur Startpunkt ~ms früher).
- Dauer misst dann von vor der „Running"-Start-Zeile (arguably genauer: ganzes Tool-Call).
- Alle 3 Pfade (finished/timed out/stopped) + IOException-Catch hätten `stats` im Scope.
- Alternativen verworfen: neues `CallStats` im catch = sinnlose ~0s-Dauer; stopped ohne Dauer = SOLL-Bruch.

## Bei „ja" (nächste Schritte)

1. 3 onTool-Zeilen anpassen: `… " " + stats.suffix() + " " + StringUtil.charsSuffix(result)`
   (Reihenfolge: Dauer VOR chars, wie SOLL).
2. `mvn -o -pl org.sterl.llmpeon.core test -Dtest=ShellToolTest` (Surefire; keine OSGi-Abhängigkeit
   betroffen → kein JAR-Refresh/Plugin-Lauf nötig).
3. Commit: ShellTool.java + docs/tool-time-disclosure.md → push.
4. Report: Zeilen vorher/nachher, Testzahl, Commit-Hash, Push-Status.
