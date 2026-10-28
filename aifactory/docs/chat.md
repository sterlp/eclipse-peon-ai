---
idPrefix: WP
---
# Jon-Chat — ein Jon, ein Gespräch je Projekt

## Ziel

Je Projekt spricht jeder Nutzer mit **demselben** Jon in **einem** Gespräch: Antworten streamen live,
Eingaben warten in der Queue, der Verlauf überlebt einen Neustart, das Team ist über Kacheln sichtbar.
Agent-Logik, Tools und Prompts kommen unverändert aus `llmpeon-core`
([AF-ADR-001](adr/001-factory-shares-core-disk-only.md)). Datei-Bereich: [Dateien](files.md) · Absender-Name: [Nutzer](user.md).

## Regeln

### R-WP-4 — Der Nutzer spricht immer mit Jon ✅

Keine Agent-Auswahl, keine weiteren Built-in- oder Custom-Agenten
([PR-1](index.md)). Eine Delegation an Da Thinka/Da Mek/Da Dok/Da Sniffa erscheint als
Werkzeug-Zeilen im Chat, die das Mitglied nennen — Start und Abschluss, Parität zum Plugin.

**Herkunft je Zeile — ohne Core-Änderung.** Jede Zeile trägt den Namen dessen, der sie erzeugt hat:
Jons Texte heißen „Jon" (er besitzt den Verlauf); die **finale Antwort eines Team-Mitglieds ist das
Ergebnis des Delegations-Werkzeugs** in Jons Verlauf, ihr Urheber folgt aus dem Werkzeug
(`talkPlan`/`planWithPlanAgent` → Plan, `askDev`/`buildWithDev` → Dev, `reviewPlanAgent` → Review,
`searchAgent` → Search); angezeigt wird der **Anzeigename** des Agenten aus dem Core (heute noch
Da Thinka/Da Mek/Da Dok/Da Sniffa, ab Agent-Dateien V2 Plan/Dev/Review/Search). Eine Zeile ohne
Agenten-Bezug trägt **keinen** Namen statt eines erfundenen (kein „System", kein „Peon"). Nutzer-Zeilen
tragen den Namen des Menschen, sobald es ihn gibt ([R-WP-25](user.md)). Das wachsende Fragment eines
laufenden Turns trägt keinen Namen, weil live nur Jon spricht und damit nichts zu verwechseln ist.
**Im Web ist das der Standard** — das Plugin bekommt dieselbe Darstellung später als
Experten-Einstellung ([agent-origin-proposal](../../proposals/agent-origin-proposal.md)).

**Man sieht nur Jon und die finalen Antworten seines Teams** — nicht die Unterhaltung eines
Teammitglieds mit sich selbst: dessen **Denk- und Zwischenschritte** erscheinen im Chat **nie**,
weder live noch im Verlauf; sichtbar sind nur die Delegations-Zeilen und die finale Antwort. Es gibt
genau **einen** Gesprächsstrang.

#### UC-WP-6 — chatAlwaysGoesToJon ✅
- GIVEN der Chat-View WHEN der Nutzer eine Nachricht sendet THEN antwortet Jon; ein Agenten-Wechsel
  ist nirgends möglich.

#### UC-WP-39 — everyLineCarriesItsAuthor ❌
- GIVEN eine Antwort von Jon oder von einem Team-Mitglied WHEN sie im Chat erscheint THEN nennt die
  Zeile den Agenten-Namen, der sie erzeugt hat; hat eine Zeile keinen Agenten-Bezug, trägt sie keinen
  Namen statt eines erfundenen.
- GIVEN ein Neustart WHEN der Verlauf wieder gelesen wird THEN trägt jede Zeile weiterhin ihre
  Herkunft — Jons Texte aus dem Besitzer des Verlaufs, Team-Antworten aus dem Delegations-Werkzeug;
  keine zweite Ablage.

#### UC-WP-40 — onlyFinalAnswersFromTheTeam ❌
- GIVEN Jon delegiert per `askDev` an Da Mek WHEN Da Mek fertig ist THEN steht dessen **finale**
  Antwort (das Werkzeug-Ergebnis) als Zeile „Da Mek" im Chat.
- GIVEN dieselbe Delegation WHEN man den Chat betrachtet THEN sind die Zwischenschritte des
  Teammitglieds (Denken, eigene Werkzeug-Aufrufe, Teilantworten) **nicht** sichtbar — sichtbar sind nur
  die Delegations-Zeilen und die finale Antwort, es gibt genau einen Gesprächsstrang, keinen zweiten
  Verlauf und keinen Mitschnitt je Agent.
- GIVEN ein Reload nach einer Delegation WHEN der Chat neu aufgebaut wird THEN ist die finale Antwort
  des Team-Mitglieds weiterhin Teil des Verlaufs (Live-Zeilen ausgenommen, R-WP-11) — in Jons Verlauf,
  nicht in einer zweiten Ablage.

### R-WP-5 — Antworten erscheinen laufend ✅

Der Turn streamt in den Chat, ohne dass der Nutzer aktualisieren muss. Nebenläufigkeit, Cancel und
Queue sind die des Cores — die Web-Schicht erfindet keinen zweiten Turn-Pfad.

#### UC-WP-7 — streamingUpdatesChatWithoutReload ✅
- GIVEN eine sendete Nachricht WHEN Jon antwortet THEN wächst die Antwort sichtbar und der
  Tool-Fortschritt erscheint als Zeile.

#### UC-WP-41 — slowReaderNeverStopsTheTurn ✅
- GIVEN ein Zuschauer, der seine Verbindung nicht mehr liest WHEN ein Turn läuft THEN wird der Turn
  dadurch nicht aufgehalten und alle anderen Zuschauer sehen die Zeilen weiter — ein Zuschauer ist
  keine Bremse für das Projekt.

### R-WP-10 — Ein Turn je Projekt, weitere Eingaben in der Queue ✅

Eingaben während eines laufenden Turns gehen in die Queue des Cores; verschiedene Projekte dürfen
parallel laufen.

#### UC-WP-14 — secondInputDuringTurnQueues ✅
- GIVEN ein laufender Turn WHEN der Nutzer erneut sendet THEN wird die Nachricht gequeued und erst
  nach dem Turn verarbeitet — kein zweiter paralleler Turn auf derselben Memory.

### R-WP-15 — Je Projekt genau ein Jon, ein Chat für alle ✅

Ein Projekt hat genau **eine** Jon-Instanz und genau **ein** Gespräch. Jeder Nutzer, der das Projekt
öffnet, sieht und schreibt in **denselben** Chat — Nachrichteneingaben laufen in die Queue *dieses*
Jons, nie in eine private Kopie. Andere Projekte laufen unabhängig und parallel.

#### UC-WP-21 — secondUserSeesSameProjectChat ✅
- GIVEN Nutzer A hat mit dem Projekt chatet WHEN Nutzer B dasselbe Projekt öffnet THEN sieht B
  dieselben Nachrichten und sendet in denselben Gesprächsverlauf.

#### UC-WP-22 — inputOnBusyProjectQueuesNotParallel ✅
- GIVEN im Projekt läuft ein Turn WHEN ein anderer Nutzer sendet THEN geht die Nachricht in die
  Queue genau dieses Jons — kein zweiter paralleler Turn auf derselben Memory.

### R-WP-11 — Projekte und ihr Chat überleben einen Neustart auf demselben Node ✅

Projekt-Registry, Clone und **genau ein** Chat je Projekt sind persistent, solange die Platte des Nodes
bleibt. Auf einem **frischen Node** kommen die Projekte aus dem Config-Repo
([R-CR-8](config-repo.md)), die Chats beginnen leer. Ein Projekt hat genau ein
Gespräch — es gibt keine Chat-Auswahl und kein zweites Gespräch neben dem einen. Nach dem Neustart
zeigt der Chat die **Texte von Nutzer und Jon** und die **finalen Antworten des Teams** (Ergebnisse
der Delegations-Werkzeuge, R-WP-4) aus dem History-Bestand; alle übrigen Werkzeug- und Statuszeilen
sind live-only — die Factory führt dafür **keinen** zweiten Speicher. Nach dem Neustart
ist Jons Gespräch wieder da; **sein Team beginnt leer** (RAM-only-Parität zum Plugin, globales
ADR-0024) — ⏳ Rückversicherung in [Open Points](open-points.md).

#### UC-WP-15 — registryAndChatSurviveRestart ✅
- GIVEN ein Projekt mit geführtem Chat WHEN der Server neu startet THEN ist das Projekt in der Liste
  und der Chat enthält wieder dieselben Nachrichten (Wiederherstellung aus dem bestehenden
  History-Bestand des Cores, kein zweiter Speicher).

#### UC-WP-16 — oneChatPerProject ✅
- GIVEN ein offenes Projekt WHEN der Nutzer den Chat-View betritt THEN gibt es genau dieses eine
  Gespräch; es existiert keine Möglichkeit, ein zweites Gespräch zum selben Projekt zu beginnen.

### R-WP-12 — Alle Core-Werkzeuge, kein Eclipse-Werkzeug ✅

Jon und sein Team bekommen **alle** Core-Werkzeuge inklusive Shell (`disk*`, `web*`, `shell`,
`docs*`) — User-Entscheid 2026-10-25. `eclipse*`-Werkzeuge existieren nicht ([PR-2](index.md)).

#### UC-WP-17 — allCoreToolsNoEclipseTools ✅
- GIVEN ein Projekt als Arbeitsordner WHEN die Tool-Liste des Jons gebaut wird THEN sind die
  Core-Familien vollständig enthalten (auch Shell) und **kein** Werkzeug mit `eclipse`-Präfix.

#### UC-WP-37 — diskToolsAreAlwaysOn ✅
- GIVEN eine Config-Datei ohne jede Angabe WHEN der Server die Config lädt THEN sind die
  Disk-Werkzeuge an — die Factory könnte sonst ihre eigentliche Arbeit nicht tun; eine von Hand
  eingetragene `false` ändert daran nichts.

### R-WP-7 — Eine Kachel je Agent mit Kontext, Status, Clear und Compact 🚧

Kopf des Chat-Views: eine Kachel für jeden Agenten von Jons Team (Jon, Da Thinka, Da Mek, Da Dok,
Da Sniffa) — Name, Kontextgröße, Statusanzeige, **Clear**- und **Compact**-Knopf. Kontextgröße ist
dieselbe Zahl wie im Plugin-Header: genutzte Tokens gegen das Auto-Compact-Budget des Agenten.

#### UC-WP-11 — teamTilesShowContextAndStatus ❌
- GIVEN der Chat-View WHEN er geöffnet wird THEN trägt jede Team-Kachel Name, Kontextzahl und
  Status; der Status wechselt bei Arbeit/Compact/Fehler ohne manuelles Aktualisieren.

#### UC-WP-12 — clearOnJonCascadesTeam ❌
- GIVEN ein laufendes Team WHEN **Clear** auf Jons Kachel geklickt wird THEN sind Jon und sein Team
  leer (Parität zum Plugin); **Clear** auf einer Team-Kachel leert nur diesen Agenten.

#### UC-WP-13 — compactAffectsOnlyThatAgent ❌
- GIVEN eine Kachel WHEN **Compact** geklickt wird THEN kompaktiert genau dieser Agent und die
  Kachel zeigt den Compact als eigenen Zustand.
