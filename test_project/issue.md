# Issue: Java-Debugger-Tool E2E-Test

Session: `test_project` / `org.sterl.fixture.DebugFix`, Java 21 (HotSpot/Graal 21), 2026-09-22.

## Befund 1 (kritisch): Exception-Breakpoint feuert nicht (Phase 3b)

- `set_exception_breakpoint("java.lang.IllegalArgumentException")` → Antwort `installed: true` (ID 48853).
- Relaunch mit Programm-Arg `throw`; VM wirft ungefangene `IllegalArgumentException` in `DebugFix.main` (Zeile 14) — Console-Stacktrace bestätigt (`Exception in thread "main" ... boom`).
- **Kein Suspend** am Throw-Site. `continue` meldete nach 30 s: `continue: still running after 30000 ms — no breakpoint hit?`. VM lief bis zum Exit, Session war danach tot.
- Erwartet (SOLL): Suspend, sobald die IAE geworfen wird (uncaught, Default-Einstellung).
- Folge: `get_exception` im 3b-Szenario war nicht testbar. (Der positive Fall im catch-Block, Phase 3c, funktioniert korrekt.)

## Befund 2 (mittler): hitCount + Condition hält nicht am 3. Hit (Phase 3.2)

Szenario A (BP während Suspend installiert, `counter=2`):
- `set_breakpoint(Zeile 11, condition="counter == 2", hitCount=3)` → `installed: true` (ID 48849).
- `continue` → VM lief **drei** Durchläufe durch (counter 3, 4, 5) und hielt erst beim **4.** Erreichen der Zeile an (counter=6, Condition dort falsch).
- Erwartet: Suspend beim 3. Erreichen (JDI: hitCount zählt zuerst, Condition danach).

Szenario B (BP beim Launch aktiv, frische VM, counter=0):
- Derselbe BP (48849, nicht entfernt) war beim Relaunch aktiv → VM hielt erst beim **2.** Erreichen an (counter=1).
- Beide Beobachtungen weichen vom „3. Hit“ ab und sind untereinander inkonsistent.

Kontrollen (ohne Condition) waren korrekt:
- `hitCount=1` → Suspend beim 1. Erreichen (counter=7). ✓
- `hitCount=2` → Suspend beim 2. Erreichen (counter=9). ✓

Fazit: hitCount allein zählt korrekt; in Kombination mit einer Condition (hier: nie wahre Condition) weicht der Suspend-Punkt ab.

Folge: Phase 4 startete infolge von Szenario B bei counter=1 statt 0 (Schritt-Tests selbst ok, Erwartungswerte des Dokuments gelten nur bei counter=0).

## Kleinigkeiten (Kosmetik)

- Breakpoint-Antworten zeigen `hitCount: -1`, Tool-Beschreibung sagt `0 = every hit` — inkonsistent dargestellt (Verhalten selbst ok).

## Nicht-Befunde (zur Einordnung)

- `remove_breakpoint` funktioniert korrekt (zweimal verifiziert: nach Entfernen hielt `continue` nicht mehr an der Zeile; UI zeigte Marker weg). Die anfängliche „Breakpoint bleibt“-Beobachtung war der im UI gesondert gesetzte Start-Breakpoint des Users.
- SOLL-Quellen-Abweichung im Test-Dokument (kein Produktfehler): 4.2 erwartet `tick` auf „Zeile 20“, der Fixture-Körper steht auf Zeile 23; die Suspend-Position war korrekt `tick`, Zeile 23.
- SOLL-Quellen-Abweichung im Test-Dokument: 3.2 „Im Suspend: counter == 2“ geht davon aus, dass die anfängliche Suspend-Position als Erreichen #1 zählt; bei Zählen der Erreichungen nach `continue` wäre der 3. Hit counter==3.
