# E2E-Test: Java-Debugger-Tool (User-managte Debug-Session)

**Für den Agenten, der diesen Test ausführt.** Du prüfst die Debugger-Tools **aus der Sicht eines
Nutzers**, nicht den Quellcode. Lies keinen Produktcode, um dir eine Erwartung zu bilden — die
Erwartung steht hier.

## Regeln

1. **Führe die Phasen der Reihe nach aus** und notiere zu jedem Aufruf: Ergebnis (gekürzt),
   erwartet ja/nein.
2. **Erstelle eine `issue.md` NUR, wenn du einen Fehler findest.** Kein Fehler = keine Datei,
   nur die Abschlussantwort im Chat (siehe letzte Phase).
3. **Die Debug-Session gehört dem User (Paul).** Du startest und beendest sie **nie selbst** und
   nutzt kein Launch-UI — die Session-Steuerung ist außerhalb deines Auftrags, genau das wird
   in Phase 5 geprüft.
4. **Rate nicht.** Ist eine Erwartung hier unklar formuliert, ist das selbst ein Befund.
5. Räume angelegte Dateien am Ende wieder weg — **nach** der letzten Prüfung, nie davor.

## Vorbedingungen (Paul)

- Gewähltes Projekt: `test_project`.
- **Paul startet die Debug-Session** (Java-Launch im Debug-Mode, Breakpoint auf der mit
  `// <-- BREAKPOINT` markierten Zeile). Der Agent wartet danach.

---

## Phase 0 — Fixture (Agent-Prompt)

> Erstelle im Projekt `test_project` die Datei `src/org/sterl/fixture/DebugFix.java` mit exakt
> diesem Inhalt und notiere die Zeilennummer der `// <-- BREAKPOINT`-Zeile:
>
> ~~~java
> package org.sterl.fixture;
>
> import java.awt.Point;
>
> public class DebugFix {
>     static Point p = new Point(1, 2);
>
>     public static void main(String[] args) {
>         int counter = 0;
>         while (counter < 10) {
>             counter = tick(counter); // <-- BREAKPOINT
>         }
>         if (args.length > 0 && args[0].equals("throw")) {
>             throw new IllegalArgumentException("boom"); // nur mit Programm-Arg "throw"
>         }
>         System.out.println("done " + counter);
>     }
>
>     static int tick(int counter) {
>         return counter + 1;
>     }
> }
> ~~~
>
> Wenn Paul die Datei gesehen hat und die Session läuft (Programm am Breakpoint suspended):
> weiter mit Phase 1. Sage ihm vorher: Launch mit Standard-Args für Phase 1–4, Programm-Arg
> `throw` erst für Phase 3b.

## Phase 1 — Lesen (Agent-Prompt)

> Rufe nacheinander auf und prüfe je Aufruf die Erwartung:
>
> | # | Aufruf | Erwartet |
> |---|---|---|
> | 1.1 | `get_state` | JSON: `vm.state` = suspended, Main-Thread mit `topFrame` (Methode `main`, Typ `DebugFix`, Zeile = Breakpoint-Zeile). |
> | 1.2 | `get_stack_trace` | Frames, Frame 0 = `DebugFix.main`. |
> | 1.3 | `get_variables` mit `depth=2` | `counter` (int, 0–9) in Pretty-JSON. **Statische Felder sind bewusst NICHT enthalten** („local variables of the frame" — statisches `p` fehlen ist **korrekt**, Erweiterung ist Backlog F2). |
>
> Erwartung an die Namen: du solltest die Tools anhand ihrer Namen/Beschreibungen **ohne
> Experimentieren** richtig bedienen können. Wenn du raten musstetest: notieren (Abschlussfrage).

## Phase 2 — set_variable (Agent-Prompt)

> | # | Aufruf | Erwartet |
> |---|---|---|
> | 2.1 | `set_variable(name=counter, value=42)` | Response mit neuem Wert **42 als JSON-Zahl** (nicht String); danach `get_variables` bestätigt 42. Danach `set_variable(counter, 0)` — setzt zurück für Phase 3 und verifiziert den zweiten Write. |
> | 2.2 | `set_variable(name=p, value=0)` | **Ehrlicher Fehler** in der Art „primitives, String or null only (declared: …)" — kein stiller No-Op, kein Crash. |

## Phase 3 — Breakpoints (Agent-Prompt)

> | # | Aufruf | Erwartet |
> |---|---|---|
> | 3.1 | `set_breakpoint` auf die Breakpoint-Zeile, `condition="counter == 2"`, `hitCount=3` | Response mit Marker-ID + `installed`. |
> | 3.2 | `continue` | Läuft weiter … **und suspended wieder**, wenn der BP das **dritte Mal** erreicht ist (JDI: hitCount zählt zuerst, Condition wird danach geprüft). Im Suspend: `counter == 2`. |
> | 3.3 | `remove_breakpoint(id)` | `{id, removed}`; ein erneuter `continue` hält dann **nicht** mehr an dieser Zeile. |
> | 3.4 | `remove_breakpoint` mit unbekannter ID | **Ehrlicher Fehler**, kein stiller No-Op. |
>
> **Phase 3b — Exception-Breakpoint (Paul: Relaunch mit Programm-Arg `throw` nötig) — NACH Phase 4 ausführen!**
> (Begründung F6: nach dem Exception-Suspend beendet jeder Resume die VM — Phase 4 wäre danach nicht mehr möglich.)
> `set_exception_breakpoint("java.lang.IllegalArgumentException")` → `continue` → Suspend,
> sobald `IllegalArgumentException` geworfen wird. Danach Breakpoint wieder entfernen.

## Phase 4 — Controls & Evaluate (Agent-Prompt)

> | # | Aufruf | Erwartet |
> |---|---|---|
> | 4.1 | `step_over` | `topFrame` rückt eine Zeile weiter, Zustand suspended. |
> | 4.2 | `step_in` | Erster `step_in` von der Loop-Zeile → `main`, Loop-Körper (JDI korrekt); **zweiter** `step_in` → `tick` (Zeile 20). |
> | 4.3 | `step_out` | Zurück in `main`. |
> | 4.4 | `step_out` erneut am Top-Frame | Ehrliche Meldung in der Art „already at top frame — use continue", kein Crash. |
> | 4.5 | `evaluate_expression("counter + 1")` | JSON mit `counter + 1` als Wert und Typ. |
> | 4.6 | `suspend` (nach `continue`) | State-JSON mit suspended-Threads. |

## Phase 5 — User beendet die Session (Agent-Prompt)

> Frage Paul, die Session im Debug-UI zu **terminieren**. Wenn beendet:
>
> | # | Aufruf | Erwartet |
> |---|---|---|
> | 5.1 | `get_state` | **Ehrlicher Fehler** „no active debug session — start debugging first". **Kein** Auto-Start, **kein** Auto-Reconnect, keine neue Launch. |
>
> Räume danach die Fixture-Datei weg (`toDelete`).

## Abschluss — Pflichtantwort im Chat (Agent-Prompt)

> Beantworte zum Schluss **explizit diese zwei Fragen** aus deiner Tester-Sicht (keine
> Quellcode-Zitate, deine Erfahrung beim Testen):
>
> 1. **Namen & Beschreibungen:** Waren die 13 Tool-Namen (`get_state`, `get_stack_trace`,
>    `get_variables`, `evaluate_expression`, `set_variable`, `set_breakpoint`,
>    `set_exception_breakpoint`, `remove_breakpoint`, `step_over`, `step_in`, `step_out`,
>    `continue`, `suspend`) und ihre Beschreibungen selbsterklärend? Konntest du jede Aktion
>    ohne Rateversuch richtig parametrisieren? Was fehlte oder war mehrdeutig?
> 2. **Verhalten:** Haben sich die Tools so verhalten, wie du es als LLM anhand von Name +
>    Beschreibung erwartet hättest? Nenne jede Abweichung (auch positive Überraschungen).
>
> Wenn alles wie erwartet: kurze Antwort „alle N Schritte wie erwartet" + die zwei Antworten.
> Keine issue.md ohne Fehler.

---

**SOLL-Quelle:** `docs/java-debugger-tool.md` (R-JD-1…5). Weicht diese Anleitung vom SOLL ab,
gilt das SOLL — und die Abweichung ist selbst ein Befund. Keine Code-Änderungen, keine
Ursachen-Vermutungen — du testest, du reparierst nicht.
