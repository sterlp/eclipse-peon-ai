# Tagged Results — Tool-Results und System-Infos als getaggte Blöcke

> **Status:** 🚧 in design (2026-10-18, Paul) · **Code:** `agent/model/TaggedResult` + `ChatMessageUtil.extractMessages` (Pauls WIP, nicht auf main)
> **Verwandt:** [context-message-concept.md](context-message-concept.md) · [compact.md](compact.md) · [async-agents.md](async-agents.md)

## Warum

Der Agent bekommt Text aus drei Quellen in denselben Message-Kanal: User-Input, Harness-Infos
(Async-Ergebnisse, Fehler, Zustandswechsel) und Tool-Results. Ohne Markierung rät das Modell, wer
spricht — es hält eine Async-Antwort für einen User-Wunsch oder umgekehrt. Tags machen den
Absender **im Text** sichtbar, ohne die Message-Typen von langchain4j zu verbiegen.

Zweiter Nutzen: Ein Tag ist auch ein **Dedup-Schlüssel**. Wiederholte Reads derselben Datei, mehrfach
identische Tool-Ausgaben oder dasselbe Async-Ergebnis sollen im Compact-Input nur einmal stehen —
aber nur dann, und sichtbar.

## Format (Vorschlag)

```
//-- Start <head>
<body>
//-- End <head>
```

- `head` = Kurzbezeichnung/Kontext (z. B. `tool call: eclipseReadFile`, `async result: Da Thinka`).
- `body` = Nutzlast.
- `TaggedResult.INFO` beschreibt das Format selbst und gehört in die **System-Message** eines
  Agenten (oder in die Initialize-Instructions des jeweiligen Kontexts) — sonst erkennt das Modell
  die Tags nicht als Harness-Sprache.

## Bewertung: Set statt Liste? (Pauls Frage)

**Empfehlung: `extractMessages` gibt eine `List<String>` zurück; das Deduplizieren passiert beim
Consumer (Compact) und wird dort genannt.**

Begründung:

1. **Ein Set als Rückgabetyp ist ein hidden filter.** Zwei Nachrichten mit identischem Text
   verschwinden unsichtbar — der Compact-Input hat dann weniger Zeilen als der Verlauf, und niemand
   (Mensch wie Agent) erfährt, dass gefiltert wurde. Repo-Regel (AGENTS.md): jede Einschränkung
   wird in ihrem eigenen Output genannt.
2. **Dedup ist nur an EINER Stelle semantisch gewollt.** Im Compact ist Wiederholung Ballast; im
   normalen Render-Pfad (`toString`, Chat-Anzeige, Fehlerdiagnose) ist die **Anzahl** gleichlautender
   Messages Information (3× gelesen, 3× gescheitert).
3. **Falscher Dedup nach Kürzung.** Wird auf ein Per-Message-Cap gekürzt, können zwei
   *unterschiedliche* Inhalte auf denselben String fallen (gleicher Kopf, Rest abgeschnitten) und
   kollabieren still zu einem. Das ist Datenverlust, der wie ein Feature aussieht.
4. **Reihenfolge** ist bei `LinkedHashSet` erhalten — das ist kein Argument gegen die Liste, sondern
   zeigt, dass das Set ohnehin nur als Dedup-Werkzeug dient.

Konkrete Form: Consumer baut `new LinkedHashSet<>(list)`, zählt `before - after` und schreibt die
Zahl in die bestehende Compact-Disclosure („N duplicate message(s) collapsed"). Damit ist der Filter
sichtbar, testbar (Mutations-Nachweis: Duplikat entfernen → Zähler 0) und lokal.

## Offene Design-Fragen (❓ vor dem Bau klären)

1. **Sentinel-Robustheit:** `startsWith(PREFIX) && endsWith(SUFFIX)` ist fragil — ein Body kann auf
   `//-- End ` enden oder durch Trailing-Whitespace/Trim kippen. Prüfung auf `stripTrailing()` und
   auf den `head` im Suffix?
2. **Asymmetrie im Rendering:** `toString()` schreibt `SUFFIX` **ohne** `head` (`//-- End `), der
   Start trägt ihn. Symmetrisch machen (Debugbarkeit, Matching).
3. **Wer prüft den Tag?** Nur `messageTag` in `extractMessages` — gilt das auch für die Chat-Anzeige
   (`toString`)? Ein getaggtes Tool-Result im UI sollte nicht „USER:" davor bekommen.
4. **Injektionsort von `TaggedResult.INFO`:** pro Agent in die System-Message (static context) oder
   einmal in den Turn-Kontext ([ContextItem](context-message-concept.md))? Duplikat-Gefahr bei
   jedem Turn — der ContextItem-Dedup (`dedupKey`) existiert bereits, die System-Message wird je
   Turn neu gebaut.
5. **Doppel-Tagging:** Tool-Results, die Peon schon selbst markiert (🔍/📜/🗜-Symbole,
   [icons.md](icons.md)), bekommen zusätzlich den Block-Tag — gewollt (Symbol = Werkzeug, Tag =
   Absender) oder redundant?
