# E2E-Tests — so werden sie ausgeführt

Diese Dateien sind **Prompts für einen Agenten**, der die Tools wie ein Nutzer ausprobiert.
Ein Test = eine Datei. Kurze Prompts ohne eigene Regeln gelten zusätzlich unter diesen Regeln.

## Regeln (für alle Tests hier)

1. **Black Box.** Du testest das Verhalten der Tools, nicht den Code. Lies keinen Produktcode, um
   dir eine Erwartung zu bilden — die Erwartung steht im Test. Erst **wenn du einen Fehler
   siehst**, darfst du in den Code schauen, um deinen Befund zu schärfen (Datei + Zeile im
   Befund nennen). Die Erwartung änderst du dadurch nie.
2. **Nur Fehler werden gemeldet.** Kein Fehler = keine Datei, nur eine kurze Chat-Zeile
   („alles wie erwartet"). Ein Befund wird als `<testname>-issues.md` neben dem Test angelegt —
   eine Datei, ein Abschnitt pro Befund (Schritt, Aufruf, erwartet, tatsächlich wörtlich,
   reproduzierbar, Schwere).
3. **Weiche nie aus.** Liefert ein Tool ein unerwartetes Ergebnis, wechsle **nicht** still auf ein
   anderes Tool — genau das ist der Fehler, den wir suchen. Notiere ihn.
4. **Du testest, du reparierst nicht.** Keine Code-Änderung, keine Ursachen-Vermutung ohne Beleg.
5. **Rate nicht.** Ist eine Erwartung unklar formuliert, ist das selbst ein Befund.
6. **Räume auf** — nach der letzten Prüfung, nie vorher. Ausnahmen nennt der Test (z. B. Datei
   absichtlich stehen lassen, damit man den Fehler nachsehen kann).

## Die Tests

| Datei | Was |
|---|---|
| `compact-e2e.md` | Compact über zwei Agenten (Jon + Da Mek), State-Überleben, Sichtbarkeit |
| `copy-tools-e2e-test.md` | `diskCopyFile` ↔ `eclipseCopyFile` (Algorithmus, Pfadform, kein Overwrite) |
| `file-edit-tools.md` | Edit/Replace in beiden Tool-Familien, Zeilenenden, Mehrfach-Treffer |
| `java-debugger-e2e-test.md` | Debugger-Tools gegen eine Session, die Paul startet |
| `plan-chat-e2e-test.md` | Plan ohne Plan-Tools, direkt als Chat-Antwort |
| `plan-e2e-test.md` | Plan mit den Plan-Tools, bauen, archivieren |
| `plan-po-test.md` | Jon → Da Thinka planen → Da Mek bauen, selbst testen |
| `read-tools-e2e-test.md` | Lesen/Grep/Suche/Console |
| `utf8-tool-test.md` | Umlaute/UTF-8 über beide Tool-Familien |

**Grundsatz aus `AGENTS.md`:** ein Falsch-Negativ (Tool sagt „nichts da", obwohl es existiert) ist
der teuerste Fehler — der Agent handelt danach auf falschem Glauben. Melde es zuerst.
