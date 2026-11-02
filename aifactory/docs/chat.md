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
([PR-1](index.md)). Eine Delegation an Plan/Dev/Review/Search erscheint als
Werkzeug-Zeilen im Chat, die das Mitglied nennen — Start und Abschluss, Parität zum Plugin.

**Herkunft je Zeile — der Core stempelt, der Consumer mappt ([ADR-0078](../../docs/adr/0078-origin-in-core-owner-monitor.md)).**
Jede Zeile trägt `origin` = Core-Id des sprechenden Agenten; die Fabrik zeigt den Namen —
**ids = Anzeigenamen seit [ADR-0079](../../docs/adr/0079-display-names-without-ork-names.md)**
(Jon · Plan · Dev · Review · Search), Jons Zeilen heißen „Jon" (er besitzt den Verlauf). Die **finale Antwort eines
Team-Mitglieds ist das Ergebnis des Delegations-Werkzeugs** in Jons Verlauf, ihr Urheber folgt aus dem
Werkzeug (`talkPlan`/`planWithPlanAgent` → Plan, `askDev`/`buildWithDev` → Dev, `reviewPlanAgent` →
Review, `searchAgent` → Search). Eine Zeile ohne Agenten-Bezug (`origin` null, außerhalb eines
Agent-Turns) trägt **keinen** Namen statt eines erfundenen (kein „System", kein „Peon"). Nutzer-Zeilen
tragen den Namen des Menschen, sobald es ihn gibt ([R-WP-25](user.md)). Das wachsende Live-Fragment
trägt die Core-Id des Agenten, der es erzeugt — das Web blendet Mitglieder-Interna aus (unten), das
Plugin zeigt sie als Überschrift
([agenten-namen-im-chat](../../org.sterl.llmpeon/docs/agenten-namen-im-chat.md)).

**Man sieht nur Jon und die finalen Antworten seines Teams** — nicht die Unterhaltung eines
Teammitglieds mit sich selbst: dessen **Denk- und Zwischenschritte** erscheinen im Jon-Chat **nie**,
weder live noch im Verlauf; sichtbar sind nur die Delegations-Zeilen und die finale Antwort. Es gibt
genau **einen** Gesprächsstrang. Die **Einsicht** in das eigene Gespräch eines Mitglieds (lesend,
außerhalb des Jon-Chats) regelt [R-WP-26](chat.md).

#### UC-WP-6 — chatAlwaysGoesToJon ✅
- GIVEN der Chat-View WHEN der Nutzer eine Nachricht sendet THEN antwortet Jon; ein Agenten-Wechsel
  ist nirgends möglich.

#### UC-WP-39 — everyLineCarriesItsAuthor ✅
- GIVEN eine Antwort von Jon oder von einem Team-Mitglied WHEN sie im Chat erscheint THEN nennt die
  Zeile den Agenten-Namen, der sie erzeugt hat; hat eine Zeile keinen Agenten-Bezug, trägt sie keinen
  Namen statt eines erfundenen.
- GIVEN ein Neustart WHEN der Verlauf wieder gelesen wird THEN trägt jede Zeile weiterhin ihre
  Herkunft — Jons Texte aus dem Besitzer des Verlaufs, Team-Antworten aus dem Delegations-Werkzeug;
  keine zweite Ablage.

#### UC-WP-40 — onlyFinalAnswersFromTheTeam ✅
- GIVEN Jon delegiert per `askDev` an Dev WHEN der Turn endet (`done`) THEN steht dessen **finale**
  Antwort (das Werkzeug-Ergebnis, wie es ist — inkl. der angehängten Statistik-Zeile) als Zeile „Dev"
  im Chat; war das Ergebnis leer, erscheint ebenfalls eine Zeile („returned no result").
- GIVEN dieselbe Delegation WHEN man den Jon-Chat betrachtet THEN sind die Zwischenschritte des
  Teammitglieds (Denken, eigene Werkzeug-Aufrufe, Teilantworten) **im Jon-Chat** nicht sichtbar —
  sichtbar sind nur die Delegations-Zeilen und die finale Antwort; es gibt genau einen Gesprächsstrang,
  keinen zweiten Verlauf und **in Jons Chat** keinen Mitschnitt je Agent. Das eigene Gespräch des
  Mitglieds ist nur über den schreibgeschützten Blick seiner Kachel einsehbar ([R-WP-26](chat.md)) —
  nie im Jon-Chat.
- GIVEN ein Reload nach einer Delegation WHEN der Chat neu aufgebaut wird THEN ist die finale Antwort
  des Team-Mitglieds weiterhin Teil des Verlaufs (Live-Zeilen ausgenommen, R-WP-11) — in Jons Verlauf,
  nicht in einer zweiten Ablage.
- GIVEN eine Delegation, deren Werkzeug-Aufruf fehlschlägt WHEN Jon das Fehler-Ergebnis bekommt THEN
  erscheint auch dieses Ergebnis wörtlich als Mitglieder-Zeile — kein Parsen, kein Unterdrücken; der
  Nutzer sieht, warum keine Antwort kam.

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

#### UC-WP-50 — stopCancelsTheWholeTurn ❌
- GIVEN ein laufender Turn WHEN der Nutzer **Stop** klickt THEN bricht die Fabrik den laufenden Turn
  ab — Jon und jede laufende Delegation enden („alles gestopt", Paul 2026-11-01); schon gezeigte
  Zeilen bleiben, es gibt keinen zweiten Speicher und keinen zweiten Turn-Pfad. Die Interaktion kehrt
  an Jon zurück; gequeuete Eingaben bleiben in der Queue.

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

### R-WP-7 — Eine Kachel je Agent mit Kontext, Status, Clear und Compact ✅

Kopf des Chat-Views: eine Kachel für jeden Agenten von Jons Team (Jon, Plan, Dev, Review,
Search) — Name, Kontextgröße, Statusanzeige, **Clear**- und **Compact**-Knopf. Kontextgröße ist
dieselbe Zahl wie im Plugin-Header: genutzte Tokens gegen das Auto-Compact-Budget des Agenten.
Die **Search**-Kachel zeigt diese Zahl nicht — Search ist ein Sub-Loop von Jon
([ADR-0079](adr/0079-display-names-without-ork-names.md)), hat keinen eigenen Kontext („—") und
kein Clear/Compact. Kacheln von Plan, Dev und Review öffnen den schreibgeschützten Blick in das
eigene Gespräch ([R-WP-26](chat.md)).

#### UC-WP-11 — teamTilesShowContextAndStatus ✅
- GIVEN der Chat-View WHEN er geöffnet wird THEN trägt jede Team-Kachel Name, Kontextzahl und
  Status; der Status wechselt bei Arbeit/Compact/Fehler ohne manuelles Aktualisieren. Die
  Search-Kachel trägt als Sub-Loop keinen Kontextwert.

#### UC-WP-12 — clearOnJonCascadesTeam ✅
- GIVEN ein laufendes Team WHEN **Clear** auf Jons Kachel geklickt wird THEN sind Jon und sein Team
  leer (Parität zum Plugin); **Clear** auf einer Team-Kachel leert nur diesen Agenten.

#### UC-WP-13 — compactAffectsOnlyThatAgent ✅
- GIVEN eine Kachel WHEN **Compact** geklickt wird THEN kompaktiert genau dieser Agent und die
  Kachel zeigt den Compact als eigenen Zustand.

### R-WP-26 — Der Blick in den Agenten-Chat (Slide-out) ✅

Jede Kachel von **Plan, Dev und Review** öffnet rechts einen **schreibgeschützten** Blick in das
eigene Gespräch dieses Agenten (Slide-out). Search ist kein Agent (Sub-Loop von Jon,
[ADR-0079](adr/0079-display-names-without-ork-names.md)) und bekommt keinen Blick. Standard bleibt
Jons Chat; der Nutzer kann in den Chat eines Agenten wechseln und zurück. Interaktion läuft
weiterhin nur über Jon ([R-WP-4](chat.md)): kein Eingabefeld im Blick. **Stop** im UI stoppt den
laufenden Turn vollständig (wie bisher) — die Regel selbst und ihre Grenze (Queue-Klausel) stehen in
[UC-WP-50](chat.md); dieser Absatz behauptet kein Queue-Verhalten. Die Einsicht liest den RAM-Stand des Agenten im Core —
**kein** zweiter Speicher; nach einem Neustart ist das Team leer ([R-WP-11](chat.md)), der Blick ist
leer. **WEIL** „warum hat Dev das gebaut?" sonst nur über die finale Antwort beantwortbar wäre
(Paul, 2026-11-01).

#### UC-WP-49 — agentChatIsReadOnlyInsight ✅
- GIVEN Jon delegiert WHEN der Nutzer die Kachel eines Mitglieds öffnet THEN zeigt der Blick das
  eigene Gespräch des Agenten (Zwischenschritte, eigene Werkzeug-Zeilen), schreibgeschützt; der
  Jon-Chat zeigt weiterhin nur die Delegations-Zeilen und die finale Antwort (UC-WP-40).
- GIVEN ein geöffneter Blick WHEN der Nutzer ihn betrachtet THEN gibt es kein Eingabe- oder
  Bedienelement zum Schreiben; Stop stoppt den laufenden Turn (Jon und Team) vollständig.
- GIVEN ein Neustart WHEN der Blick geöffnet wird THEN ist er leer (R-WP-11) — kein zweiter
  Speicher und kein Mitschnitt im Jon-Chat.

### R-WP-28 — Markdown-Streaming: markstream-react, Code mit react-shiki ❌ (Paul 2026-11-02)

Antworten rendern als Markdown mit **Streaming-Unterstützung** (`markstream-react`): unvollständiges
Markdown wird beim Streaming sauber gerendert, Code-Blöcke mit **react-shiki**; Fallback, falls
markstream im Prototypen nicht hält: `react-markdown`. **Tool-Zeilen bleiben sichtbar** und stehen
**inline im Verlauf** an der Stelle, wo sie passieren — nicht als separate Liste hinter dem Verlauf;
kompakt (klein, grau, Tool-Zeilen gemerged wie in `chat.html`).

#### UC-WP-54 — streamingMarkdownWithInlineToolLines ❌
- GIVEN ein laufender Turn WHEN die Antwort streamt THEN wächst das Markdown sichtbar und bleibt
  während des Streams lesbar — unvollständige Syntax-Bruchstücke erzeugen kein kaputtes Markup.
- GIVEN Tool-Aktivität WHEN Zeilen erscheinen THEN stehen sie im Verlauf an ihrer Stelle, kompakt,
  und bleiben nach dem Turn sichtbar.

### R-WP-29 — Farben und Fließtext der Chat-Zeilen ❌ (Paul 2026-11-02)

Die Zeile des Nutzers ist **blau** und **rechtsbündig** am Chat-Rand (wie `chat.html`: Label „You",
blauer Linien-Akzent), Jons und die Antworten des Teams sind **grün** (Label = Agent-Name). Jons
Antworten haben **keinen grauen Hintergrund-Block**, keine eingestreten Markierungen oder Cards im
Fließtext; wo die Rolle wechselt, trennt eine **einfache Trennlinie** (`<hr>`). Antworten und Dateien
sind **nicht mittig zentriert**.

#### UC-WP-55 — colorsFollowChatHtmlBaseline ❌
- GIVEN eine Nutzer- und eine Agent-Antwort WHEN beide im Verlauf stehen THEN trägt die Nutzer-Zeile
  den blauen Akzent rechtsbündig, die Agent-Zeile den grünen — ohne grauen Hintergrund-Block; der
  Rollenwechsel trennt per Trennlinie.

### R-WP-30 — Eingabe: Enter ist Zeilenumbruch, Cmd/Ctrl+Enter sendet ❌ (Paul 2026-11-02)

Im Eingabefeld (Textarea) macht **Enter eine neue Zeile**; **Cmd/Ctrl+Enter** sendet — so bleiben
Mehrzeiler möglich. Der Send-Knopf bleibt die klickbare Alternative. Zusätzlich kann der Nutzer die
**letzte gesendete Nachricht erneut senden**, ohne sie neu zu tippen — etwa nach einem Fehler
(„Resend"); sie läuft wie jede Eingabe in die Queue ([R-WP-10](chat.md)), wenn ein Turn läuft.

#### UC-WP-56 — enterNewlinesCtrlEnterSends ❌
- GIVEN das Eingabefeld WHEN der Nutzer Enter drückt THEN entsteht eine neue Zeile; WHEN er
  Cmd/Ctrl+Enter drückt THEN sendet die Eingabe.

#### UC-WP-57 — resendWithoutNewInput ❌
- GIVEN der Nutzer hat eine Nachricht gesendet WHEN er „Resend" wählt THEN geht dieselbe Nachricht
  erneut in die Queue (auch nach einem Fehler), ohne dass er sie neu tippt.

### R-WP-31 — Warte-Indikator während des Turns ❌ (Paul 2026-11-02)

Während ein Turn läuft, zeigt der Chat eine kompakte **Warte-Leiste** („Waiting for AI…" wie im
Plugin: sticky am unteren Rand, dunkel, Mono, blinkender Cursor) mit dem Live-Preview des Streams;
der Idle-Zustand endet mit dem Turn.

#### UC-WP-58 — waitingIndicatorShowsLiveState ❌
- GIVEN ein laufender Turn WHEN er noch nichts geliefert hat THEN zeigt die Warte-Leiste „Waiting for
  AI…"; WHEN der Stream liefert THEN wechselt sie auf das Live-Preview und endet mit dem Turn.

### R-WP-32 — Kacheln als Reihe oben, Klick öffnet den Blick ❌ (Paul 2026-11-02)

Die Agenten-Kacheln ([R-WP-7](chat.md)) stehen **oben nebeneinander in einer Reihe** — der
Agenten-Name als Titel, Kontext, Status und Clear-/Compact darunter. Sie haben einen **Hover-Zustand**,
und ein **Klick auf die Kachel** öffnet den schreibgeschützten Blick ([R-WP-26](chat.md)) — nicht
erst ein separater Link.

#### UC-WP-59 — tilesRowLayoutClickOpensInsight ❌
- GIVEN der Chat-View WHEN er geöffnet wird THEN stehen die Kacheln als Reihe mit Name als Titel und
  den Actions darunter.
- GIVEN eine Kachel WHEN der Nutzer sie klickt THEN öffnet sich der Blick ([R-WP-26](chat.md)), mit
  sichtbarem Hover-Zustand.
