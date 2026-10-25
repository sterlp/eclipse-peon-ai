# E2E: Edit/Replace in beiden Tool-Familien

Regeln: siehe [README.md](README.md) — Black Box, nur Fehler melden.

Lege im Projekt-Root eine einfache Textdatei mit ~500 Tokens an und führe diese Edits darauf aus —
einmal mit den `eclipse*`-Tools, einmal mit den `disk*`-Tools (dazwischen die Datei löschen, am Ende
aufräumen). Teste je Familie **beide** Varianten: `edit line` und `replace`.

1. Ein exakter Treffer wird ersetzt.
2. Ein String, der 2–4× vorkommt, wird ersetzt — alle Treffer, nicht nur der erste.
3. `oldString`/`newString` mit dem falschen Zeilenende (`\r\n` bzw. `\n`, das in der Datei nicht
   vorkommt): beide Tools sollen das korrigieren und den Treffer trotzdem finden.
4. `newString` mit anderem Zeilenende als `oldString`: wird akzeptiert, weil `oldString` passt.
5. Ein `oldString`, das nicht in der Datei vorkommt: ehrlicher Fehler **plus Dateiinhalt**, damit man
   sieht warum — kein stiller No-Op.
6. Ein String, der mehrfach vorkommt, mit Erwartung „genau ein Treffer": entweder alle ersetzen und
   die Anzahl nennen, oder ehrlich ablehnen — nie still den ersten nehmen.

Zähle bei jedem Write mit (1, 2, 3 …), damit du beweisen kannst, dass du deinen eigenen Write gelesen
hast.

Melde am Ende, ob beide Familien gleich und wie erwartet reagiert haben. Bei Abweichungen:
`file-edit-tools-issues.md` anlegen (Form siehe [README.md](README.md)).
