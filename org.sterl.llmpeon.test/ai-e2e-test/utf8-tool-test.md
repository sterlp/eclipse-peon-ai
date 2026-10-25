# E2E: UTF-8 über beide Tool-Familien

Regeln: siehe [README.md](README.md) — Black Box, nur Fehler melden.

1. Schreibe `test-sonderzeichen.txt` mit dem Inhalt „ü ß ⚡".
2. Lies die Datei wieder und prüfe, dass die Zeichen korrekt zurückkommen.
3. Führe ein Update mit weiteren UTF-8-Zeichen aus — einmal als `edit line`, einmal als `replace`.
4. Lies die Datei wieder und prüfe das Ergebnis.

Zähle bei jedem Write mit (1, 2, 3 …), damit du beweisen kannst, dass du deinen eigenen Write gelesen
hast. Mach dasselbe mit den `eclipse*`- und den `disk*`-Tools — unabhängig und einmal im Kreuz-Test.
Nach einem `disk`-Write braucht Eclipse einen Refresh (`eclipseRefreshProject`), sonst siehst du die
Änderung nicht — Eclipse cached die Dateien.

Klappt alles: Datei löschen. Klappt etwas nicht: Datei stehen lassen, damit man es nachsehen kann,
und `utf8-tool-test-issues.md` anlegen (Form siehe [README.md](README.md)).
