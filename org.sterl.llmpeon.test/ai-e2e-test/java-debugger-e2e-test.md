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

- Gewähltes Projekt: `test_project` (es gibt **nur** dieses Projekt — Referenzen auf andere
  Projekte sind immer ein Fehler).
- **Paul startet die Debug-Session** (Java-Launch im Debug-Mode, Breakpoint auf der mit
  `// <-- BREAKPOINT` markierten Zeile). Der Agent wartet danach.

---

## Phase 0 — Fixture (Agent-Prompt)

> Erstelle im Projekt `test_project` die Datei `src/org/sterl/fixture/DebugFix.java` mit exakt
> diesem Inhalt und notiere die Zeilennummern der `// <-- BREAKPOINT`- und der
> `// <-- BREAKPOINT-CATCH`-Zeile:
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
>         if (args.length > 0 && args[0].equals("catch")) {
>             caught();
>         }
>         System.out.println("done " + counter);
>     }
>
>     static int tick(int counter) {
>         return counter + 1;
>     }
>
>     static void caught() {
>         try {
>             throw new IllegalStateException("caught boom");
>         } catch (IllegalStateException e) {
>             System.out.println("caught: " + e.getMessage()); // <-- BREAKPOINT-CATCH
>         }
>     }
> }
> ~~~
>
> Wenn Paul die Datei gesehen hat und die Session läuft (Programm am Breakpoint suspended):
> weiter mit Phase 1. Sage ihm vorher: Launch mit Standard-Args für Phase 1–4, Programm-Arg
> `throw` erst für Phase 3b, Programm-Arg `catch` erst für Phase 3c.

## Phase 1 — Lesen (Agent-Prompt)

> Rufe nacheinander auf und prüfe je Aufruf die Erwartung:
>
> | # | Aufruf | Erwartet |
> |---|---|---|
> | 1.1 | `debugJavaGetState` | JSON: `vm.state` = suspended, Main-Thread mit `topFrame` (Methode `main`, Typ `DebugFix`, Zeile = Breakpoint-Zeile). |
> | 1.2 | `debugJavaGetStackTrace` | Frames, Frame 0 = `DebugFix.main`. |
> | 1.3 | `debugJavaGetVariables` mit `depth=2` | `locals` mit `counter` (int, 0–9) **und** ein separates `statics`-Feld mit dem statischen `p` (Point, Feldwerte x=1, y=2 bis Tiefe 2) — leeres `statics`-Array, wenn die Klasse keine statischen Felder hat (R-JD-9). |
>
> Erwartung an die Namen: du solltest die Tools anhand ihrer Namen/Beschreibungen **ohne
> Experimentieren** richtig bedienen können. Wenn du raten musstest: notieren (Abschlussfrage).

## Phase 2 — debugJavaSetVariable (Agent-Prompt)

> | # | Aufruf | Erwartet |
> |---|---|---|
> | 2.1 | `debugJavaSetVariable(name=counter, value=42)` | Response mit neuem Wert **42 als JSON-Zahl** (nicht String); danach `debugJavaGetVariables` bestätigt 42. Danach `debugJavaSetVariable(counter, 0)` — setzt zurück für Phase 3 und verifiziert den zweiten Write. |
> | 2.2 | `debugJavaSetVariable(name=p, value=0)` | **Ehrlicher Fehler** in der Art „primitives, String or null only (declared: …)" — kein stiller No-Op, kein Crash. |

## Phase 3 — Breakpoints (Agent-Prompt)

> | # | Aufruf | Erwartet |
> |---|---|---|
> | 3.1 | `debugJavaSetBreakpoint` auf die Breakpoint-Zeile, `condition="counter == 2"`, `hitCount=3` | Response mit Marker-ID + `installed`. |
> | 3.2 | `debugJavaContinue` | Läuft weiter … **und suspended wieder**, wenn der BP das **dritte Mal** erreicht ist (JDI: hitCount zählt zuerst, Condition wird danach geprüft). Im Suspend: `counter == 2`. **Zähl-Definition (JDI-Experiment Da Mek, 2026-09-22):** die Suspend-Position beim Setzen zählt als stiller Hit #1 — der BP wurde hier auf der bereits suspendierten Zeile gesetzt, also hält der 3. Hit am **4. Erreichen nach `debugJavaContinue`** (JDI-Semantik, kein Tool-Bug). Alternativ: BP vor dem Launch setzen, dann trifft der 3. Hit exakt das 3. Erreichen. |
> | 3.3 | `debugJavaRemoveBreakpoint(id)` | `{id, removed}`; ein erneuter `debugJavaContinue` hält dann **nicht** mehr an dieser Zeile. |
> | 3.4 | `debugJavaRemoveBreakpoint` mit unbekannter ID | **Ehrlicher Fehler**, kein stiller No-Op. |
>
> **Phase 3b — Exception-Breakpoint (Paul: Relaunch mit Programm-Arg `throw` nötig) — NACH Phase 4 ausführen!**
> (Begründung F6: nach dem Exception-Suspend beendet jeder Resume die VM — Phase 4 wäre danach nicht mehr möglich.)
> **Clean-State-Pflicht (2026-09-22, nach E2E-Befunden):** Paul leert vorher die Breakpoints-View
> (alle alten Marker weg), damit kein Phantom-BP die Phasen 3b/3.2 verfälscht — solange
> `debugJavaListBreakpoints` (R-JD-11) nicht gebaut ist, ist die View die einzige Sicht.
> 1. `debugJavaSetExceptionBreakpoint("java.lang.IllegalArgumentException")` → `debugJavaContinue` → Suspend,
>    sobald `IllegalArgumentException` geworfen wird.
> 2. `debugJavaGetException` im Suspend → **Ehrlicher Fehler** in der Art „no exception variable in top
>    frame" — dokumentierte Grenze (R-JD-10): am Throw-Site eines ungefangenen `throw` existiert
>    keine benannte Variable. Der positive Fall folgt in Phase 3c.
> 3. Danach Exception-Breakpoint wieder entfernen.
>
> **Phase 3c — `debugJavaGetException` positiv (Paul: Relaunch mit Programm-Arg `catch` nötig):**
> 1. `debugJavaSetBreakpoint` auf die `// <-- BREAKPOINT-CATCH`-Zeile (im `catch`-Block, der Agent setzt
>    ihn selbst — Paul startet nur den Relaunch).
> 2. `debugJavaContinue` → Suspend **im catch-Block**, `locals` enthält `e` (IllegalStateException).
> 3. `debugJavaGetException` → Typ (`IllegalStateException`) + Message (`caught boom`) +
>    Variablenname (`e`).
> 4. `debugJavaRemoveBreakpoint(id)`, Breakpoint-Catch-Zeile wieder freigeben.

## Phase 4 — Controls & Evaluate (Agent-Prompt)

> | # | Aufruf | Erwartet |
> |---|---|---|
> | 4.1 | `debugJavaStepOver` | `topFrame` rückt eine Zeile weiter, Zustand suspended. |
> | 4.2 | `debugJavaStepIn` | Erster `debugJavaStepIn` von der Loop-Zeile → `main`, Loop-Körper (JDI korrekt); **zweiter** `debugJavaStepIn` → `tick` (Fixture-Zeile 23). |
> | 4.3 | `debugJavaStepOut` | Zurück in `main`. |
> | 4.4 | `debugJavaStepOut` erneut am Top-Frame | Ehrliche Meldung in der Art „already at top frame — use debugJavaContinue", kein Crash. |
> | 4.5 | `debugJavaEvaluateExpression("counter + 1")` | JSON mit `counter + 1` als Wert und Typ. |
> | 4.6 | `debugJavaEvaluateExpression("p")` | Objekt-Ergebnis mit **Feldwerten bis Tiefe 2** (`x`, `y`) — keine bloße „(id=N)"-Referenz (R-JD-9). |
> | 4.7 | `debugJavaSuspend` (nach `debugJavaContinue`) | State-JSON mit suspended-Threads. |

## Phase 5 — User beendet die Session (Agent-Prompt)

> Frage Paul, die Session im Debug-UI zu **terminieren**. Wenn beendet:
>
> | # | Aufruf | Erwartet |
> |---|---|---|
> | 5.1 | `debugJavaGetState` | **Ehrlicher Fehler** „no active debug session — start debugging first". **Kein** Auto-Start, **kein** Auto-Reconnect, keine neue Launch. |
>
> Räume danach die Fixture-Datei weg (`toDelete`).

## Abschluss — Pflichtantwort im Chat (Agent-Prompt)

> Beantworte zum Schluss **explizit diese zwei Fragen** aus deiner Tester-Sicht (keine
> Quellcode-Zitate, deine Erfahrung beim Testen):
>
> 1. **Namen & Beschreibungen:** Waren die 15 Tool-Namen (`debugJavaGetState`, `debugJavaGetStackTrace`,
>    `debugJavaGetVariables`, `debugJavaEvaluateExpression`, `debugJavaSetVariable`, `debugJavaSetBreakpoint`,
>    `debugJavaSetExceptionBreakpoint`, `debugJavaRemoveBreakpoint`, `debugJavaListBreakpoints`, `debugJavaStepOver`, `debugJavaStepIn`, `debugJavaStepOut`,
>    `debugJavaContinue`, `debugJavaSuspend`, `debugJavaGetException`) und ihre Beschreibungen selbsterklärend? Konntest du
>    jede Aktion ohne Rateversuch richtig parametrisieren? Was fehlte oder war mehrdeutig?
> 2. **Verhalten:** Haben sich die Tools so verhalten, wie du es als LLM anhand von Name +
>    Beschreibung erwartet hättest? Nenne jede Abweichung (auch positive Überraschungen).
>
> Wenn alles wie erwartet: kurze Antwort „alle N Schritte wie erwartet" + die zwei Antworten.
> Keine issue.md ohne Fehler.

---

**SOLL-Quelle:** `docs/java-debugger-tool.md` (R-JD-1…10). Weicht diese Anleitung vom SOLL ab,
gilt das SOLL — und die Abweichung ist selbst ein Befund. Keine Code-Änderungen, keine
Ursachen-Vermutungen — du testest, du reparierst nicht.