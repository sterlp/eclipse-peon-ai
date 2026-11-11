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

### R-WP-58 — Reconnect mit Backoff: kein Dauerfeuer gegen einen toten Server ✅ (Paul 2026-11-10, Browser-Abnahme: „wir machen ständig requests auch wenn der server weg ist"; gebaut + reviewed 2026-11-10, Inkrement `c0555ce9` — 269/30 Tests, tsc/oxlint/antd-lint 0; Abnahme am Browser steht aus)

Jeder Ereignis-Strom der App (Gespräch, Blick, Projekt-Status) verbindet sich nach einem Ende
erneut — aber **nicht in konstantem Takt**: Nach jedem fehlgeschlagenen Verbindungsversuch wächst
die Wartezeit an (Start 500 ms, je Versuch verdoppelt, Deckel 10 s). Hat eine Verbindung **gestanden
und Ereignisse geliefert**, beginnt der nächste Reconnect wieder bei der Startwartezeit. Ein Server,
der nicht antwortet, erzeugt so keine Request-Flut; die Kette selbst hört nie auf — kehrt der Server
zurück, holt der nächste Versuch den Snapshot. Ein Ende ohne `retry` (Ablehnung, Verlassen der
Seite) verbindet weiterhin nicht erneut.

#### UC-WP-95 — theReconnectBacksOffInsteadOfHammering ✅ (gebaut + reviewed 2026-11-10, Inkrement `c0555ce9`)
- GIVEN der Server ist nicht erreichbar WHEN der Strom endet und der Reconnect läuft THEN wachsen
  die Wartezeiten an (500 ms → 1 s → 2 s → …, Deckel 10 s) — keine konstante Request-Folge.
- GIVEN eine Verbindung stand und hat Ereignisse geliefert WHEN sie danach endet THEN beginnt der
  Reconnect wieder bei der Startwartezeit.
- GIVEN ein Strom endet ohne `retry` WHEN der Reconnect-Entscheid fällt THEN wird nicht erneut
  verbunden (Bestand).

### R-WP-10 — Ein Turn je Projekt, weitere Eingaben in der Queue ✅

Eingaben während eines laufenden Turns gehen in die Queue des Cores; verschiedene Projekte dürfen
parallel laufen. **Bündelung, Reihenfolge und Ablieferung sind die des Cores** —
[`queued-user-messages.md`](../../org.sterl.llmpeon.core/docs/queued-user-messages.md) (R1, R2). Die
Fabrik hält **keinen** eigenen Queue-Speicher vor und mischt sich in die Reihenfolge nicht ein.

#### UC-WP-14 — secondInputDuringTurnQueues ✅
- GIVEN ein laufender Turn WHEN der Nutzer erneut sendet THEN wird die Nachricht gequeued und erst
  nach dem Turn verarbeitet — kein zweiter paralleler Turn auf derselben Memory.

### R-WP-42 — `!` am Anfang ist die Prio-Nachricht des Cores ✅ (Paul 2026-11-06)

Eine Eingabe, die **während eines laufenden Turns** mit `!` **beginnt**, ist die **Prio-Nachricht**;
Verhalten, Lane und Zustellung regelt der Core
([`queued-user-messages.md`](../../org.sterl.llmpeon.core/docs/queued-user-messages.md) R8, R11 — dort
nachlesen, hier nicht wiederholt). **Der Fabrik-Anteil ist nur:** die Eingabe **unparsed** an genau
denselben Core-Eingang zu geben, den auch das Plugin nutzt — statt sie als normalen Turn-Text
aufzunehmen — und die Zeile im Verlauf **so zu zeigen, wie sie getippt wurde** ([R-WP-37](chat.md):
die Fabrik parst und kürzt nichts). Läuft **kein** Turn, ist `!` schlicht Text (Core R8.1) — die
Fabrik kennt keinen eigenen Sonderfall. Ein `!` **ohne** Text danach ist konsumiert und löst nichts aus
(Core R8.6). Auch die Prio-Nachricht ist eine Nachricht an Jon: sie trägt den Absendernamen wie jede
andere Eingabe ([R-WP-25](user.md)).

#### UC-WP-75 — urgentMarkerIsHandedToTheCore ✅
- GIVEN im Projekt läuft ein Turn WHEN der Nutzer `! <Text>` sendet THEN nimmt der Core die Eingabe als
  Prio-Nachricht auf — **kein** zweiter Turn, kein zweiter Weg durch die Fabrik.
- GIVEN dieselbe Eingabe WHEN die Oberfläche zeichnet THEN steht sie **als vorläufige Zeile des
  Absenders** mit `!`, genau einmal ([R-WP-37](chat.md)). Eine dauerhafte Verlaufszeile ist sie bewusst
  **nicht**: der Core schreibt die Prio-Nachricht nie in die Memory, und die nächste bestätigte
  Nutzerzeile desselben Absenders löst sie ab.
- GIVEN es läuft **kein** Turn WHEN der Nutzer mit `!` sendet THEN ist `!` Teil der Nachricht.

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


### R-WP-37 — Die eigene Eingabe steht genau einmal im Verlauf ✅ (Paul 2026-11-03, C-5)

Solange der Verlauf die Eingabe nicht trägt, zeigt der Chat sie als **vorläufige Zeile**; sobald der
Verlaufs-Read sie liefert, ersetzt die Verlaufszeile die vorläufigen **an ihrer Stelle**. Die
Bestätigung erfolgt über die **Position** im Verlauf, nie über Textvergleich — ein gequeueter Marker
wäre sonst nie erkennbar. Bestätigt wird je **Absender**: eine Verlaufs-Nutzerzeile ersetzt die
vorläufigen Zeilen **desselben Absenders**, die vor ihr gesendet wurden (Wasserlinie je Absender);
Absender ist die Herkunft der Zeile — ohne Namen tragen alle Eingaben keinen Absender und bilden
**eine** Absenderklasse ([R-WP-25](user.md)). Der Bündel-Fall ist die Realität im Verlauf: sendet
derselbe Absender mehrfach innerhalb des Bündel-Fensters des Cores, liefert der Verlauf **eine**
gebündelte Zeile — sie ersetzt alle vorläufigen Zeilen dieses Absenders. Außerhalb des Fensters ist
dieselbe Eingabe zweimal **zwei** Zeilen. Der Marker bleibt sichtbar: er ist der Realitätsstand im
Verlauf, die Fabrik parst und kürzt nichts
([AF-ADR-002](adr/002-origin-in-core-display-in-factory.md)).

#### UC-WP-69 — ownInputStandsExactlyOnce ✅
- GIVEN ein Turn läuft und derselbe Absender sendet zweimal innerhalb des Bündel-Fensters WHEN der
  Verlauf die gebündelte Zeile liefert THEN verschwinden **beide** vorläufigen Zeilen und die
  gebündelte Zeile steht an ihrer Stelle — Verbatim inklusive Marker und Zeilenumbruch, Zeit des
  ersten Eintrags (R9 des Cores).
- GIVEN ein Turn läuft und Nutzer A sendet vor Nutzer B WHEN der Verlauf A-s Zeile zuerst liefert
  THEN ersetzt sie nur A-s vorläufige Zeile; B-s vorläufige bleibt sichtbar, bis B-s eigene
  Verlaufszeile kommt — eine fremde Zeile bestätigt nie eine fremde vorläufige.
- GIVEN derselbe Text zweimal außerhalb des Bündel-Fensters gesendet WHEN der Verlauf beide Zeilen
  trägt THEN stehen zwei Zeilen, beide bestätigt — keine Dublette der vorläufigen.
- GIVEN der Verlauf trägt die Eingabe als gequeuete Zeile mit Marker WHEN der Log gezeichnet wird
  THEN zeigt die Zeile den Wortlaut des Verlaufs inklusive Marker — kein Abgleich über den Text.

### R-WP-11 — Projekte und ihr Chat überleben einen Neustart auf demselben Node ✅

Projekt-Registry, Clone und **genau ein** Chat je Projekt sind persistent, solange die Platte des Nodes
bleibt. Auf einem **frischen Node** kommen die Projekte aus dem Config-Repo
([R-CR-8](config-repo.md)), die Chats beginnen leer. Ein Projekt hat genau ein
Gespräch — es gibt keine Chat-Auswahl und kein zweites Gespräch neben dem einen. Nach dem Neustart
zeigt der Chat die **Texte von Nutzer und Jon**, die **finalen Antworten des Teams** (Ergebnisse
der Delegations-Werkzeuge, R-WP-4) und die **Fehlerzeile eines gescheiterten Turns**
([R-WP-34](chat.md)) aus dem History-Bestand; **Jons eigene Werkzeug-Schritte** erscheinen
ebenfalls aus dem Bestand — als Timeline ([R-WP-47](chat.md)). Übrige Statuszeilen sind
live-only — die Factory führt dafür **keinen** zweiten Speicher. Nach dem Neustart
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
Die **Statusanzeige ist der eine Arbeits-Zeiger der Seite** — ihre Farben und Zustände regelt
[R-WP-45](chat.md); die Kachel-Reihe steht **ohne Abstand über dem Chat** (Paul 2026-11-08, Abnahme).
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

### R-WP-60 — Kachel-Stand kommt per Push, nie per Poll ✅ (Paul 2026-11-10, Browser-Abnahme: „es sieht so aus als würden wir die panels via einem poll versorgen => spring event und stream -> bei jeder änderung ein push an das UI"; Messung 2026-11-10: das UI pollt nicht — kein `setInterval` — aber zwei Zustandswechsel erzeugen **keinen** Snapshot: der Turn-Anfangs-Snapshot läuft vor dem Kern-Flag (Jons Kachel zeigt „arbeitet" erst mit der ersten Core-Zeile, einen Modell-Roundtrip später), und Delegations-Anfang/-Ende senden ihren Eintrag **ohne** Roster — die Kachel des Mitglieds leuchtet mit seiner ersten Zeile und erlischt erst am nächsten fremden Ereignis); gebaut + reviewed 2026-11-10, Inkrement `e68f1710` — Backend 275 Tests, missCount=1; Abnahme am Browser steht aus)

Jede Zustandsänderung der Kacheln, die die **Fabrik verursacht** oder **an einer ihrer Kern-Rückmeldungen
erkennt**, published sofort einen `agents`-Snapshot auf dem Draht — kein Polling, keine Wartezeit auf
die nächste Zeile. Gedeckt sind damit: Aktions-Claim und -Ende (Clear/Compact), Turn-Anfang **mit
wahrheitsgemäßem Claim-Stand** (der Fabrik-Claim `turnInFlight` ist Teil des Kachel-Answers, genau wie
beim Clear — der Kern setzt sein Flag erst im Modellaufruf, der Snapshot lügt deshalb nicht „idle"),
Delegations-Anfang und -Ende **mit Roster**, Turn-Ende, Compact-Ende und jede Core-Zeile. **Grenze,
bewusst:** `working`/`compacting` sind reine Kern-Flags ohne Listener — Änderungen, die die Fabrik an
keiner Rückmeldung sieht (z. B. Auto-Compact-Anlauf im Kern), erscheinen erst mit der nächsten
Rückmeldung; ein Kern-Hook dafür wäre ein Core-Inkrement (beide Verbraucher) und ist nicht Teil dieser
Regel. **Stop** ändert daran nichts: der Kern gibt das Flag erst zwischen Modellaufrufen frei — der
frische Read nach Stop bleibt der Mechanismus (gemessen, G3). Das UI liest den Roster nur an den
dokumentierten Anlässen (Mount, Verbindungsaufbau, Turn-Ende, Stop, Kachel-Aktion — [R-WP-57](chat.md),
[UC-WP-84](chat.md)) und hält sonst keinen Timer.

#### UC-WP-97 — tileStateIsPushedNeverPolled ✅ (gebaut + reviewed 2026-11-10, Inkrement `e68f1710`; GIVEN 3 als Messung belegt — kein `setInterval` in factory-ui/src)
- GIVEN ein Turn beginnt WHEN der Claim angenommen wird THEN zeigt Jons Kachel sofort „arbeitet" —
  auch auf einem zweiten Browser, ohne auf die erste Core-Zeile zu warten.
- GIVEN eine Delegation beginnt oder endet WHEN das Ereignis ankommt THEN geht derselbe
  Kachel-Snapshot mit — die Kachel des Mitglieds leuchtet und erlischt mit dem Ereignis, nicht erst
  mit seiner ersten beziehungsweise der nächsten fremden Zeile.
- GIVEN die App WHEN sie zeichnet THEN pollt sie nicht — der Kachel-Stand kommt ausschließlich per
  Push oder an den dokumentierten Read-Anlässen.

### R-WP-63 — Eine Chat-Aktion endet mit dem Neuaufbau des Gesprächs ❌ (Paul 2026-11-10, Browser-Abnahme: „wenn ich einen compact mache, wird das chat-windows nicht aktualisiert, sondern bleibt auf animation in progress"; Entscheidung Paul 2026-11-10: „du brauchst kein eigenes signal — alles ist bereits im core … wenn Jon ein compact macht / clear und rebuild des chats anhand der history von Jon, genau wie es im eclipse plugin passiert")

Eine Aktion, die Jons Gespräch verändert — **Compact** und **Clear** auf seiner Kachel — ist für den
Betrachter erst dann beendet, wenn sein Gespräch **aus dem Verlauf neu aufgebaut** ist. Vorbild ist das
Plugin: dort folgt auf das Compact-Ende der Neuaufbau aus dem Gedächtnis, und **Clear** leert das
Chat-Fenster; beide kennen keinen zweiten Zustand ([R-WP-11](chat.md)).

**Der Auslöser ist die vorhandene Kachel-Meldung, kein eigenes Signal** (Paul): die Oberfläche merkt sich
die angenommene Aktion, sieht Jons Kachel im Zustands-Bereich dieser Aktion (**Compact** `COMPACTING`,
**Clear** `WORKING`) und handelt auf dessen **Fallkante** im `agents`-Snapshot
([R-WP-60](chat.md)) — kein neues Ereignis, kein neues Feld, keine Server-Änderung. **Der Zeitpunkt ist
der Kern der Regel:** die Zusage der Aktion bedeutet „angenommen", nicht „fertig"; ein Verlauf-Lauf
unmittelbar danach läuft vor dem Compact und zeigt deshalb den alten Stand.

Der Neuaufbau schließt den offenen Live-Schritt (der Dreh-Indikator der 🗜-Zeile endet mit der Aktion,
[R-WP-45](chat.md)) und zeigt danach, was der Verlauf hergibt: nach einem erfolgreichen Compact die eine
wieder eingesetzte Zusammenfassungszeile des Kerns, nach **Clear** ein leeres Gespräch. Die 🗜-Zeilen des
Compacts stehen in keinem Gedächtnis — sie bleiben als Live-Spuren dieser Aktion sichtbar (darunter das
Ergebnis mit den Zahlen) und fallen mit dem nächsten Neuaufbau weg; ihr dauerhafter Auftritt ist
[Grenze offener Punkt C-2a](open-points.md), nicht Sache dieser Regel.

**Zwei Grenzen, bewusst.** (1) Ausgelöst wird nur durch eine **vom Nutzer an Jons Kachel angenommene**
Aktion: ein Compact, den der Kern selbst anstößt (Auto-Compact, Vorlauf-Compact mitten im Turn), löst
keinen Neuaufbau aus — der laufende Turn behält seine Live-Zeilen, und sein eigenes Ende
([R-WP-48](chat.md)) räumt wie bisher auf. Ein Aktions-Neuaufbau ersetzt kein Turn-Ende und liest nicht
zweimal. (2) Eine Aktion auf einer **Mitglieds**-Kachel verändert Jons Gespräch nicht; der Blick auf das
Mitglieds-Gespräch behält seine Live-Zeilen (Parität: neu aufgebaut wird nur das Gespräch, dessen
Aktion es verändert — [R-WP-26](chat.md)).

#### UC-WP-100 — chatRebuildsAfterItsActionFinishes
- GIVEN Jons Kachel WHEN **Compact** angenommen ist und die Kachel `COMPACTING` wieder verlässt THEN
  liest die Oberfläche den Verlauf neu und zeigt das Gespräch danach — die Zusammenfassungszeile des
  Kerns ist da, und kein Live-Schritt dreht mehr.
- GIVEN Jons Kachel WHEN **Clear** angenommen ist und die Kachel `WORKING` wieder verlässt THEN ist das
  Gespräch leer (Parität zum Plugin) — nicht der Lauf unmittelbar nach der Zusage entscheidet darüber.
- GIVEN ein laufender Turn WHEN sein Ende eintrifft THEN bleibt das der einzige Aufräum-Anlass dieses
  Turns; ein Aktions-Neuaufbau überschreibt die gezeigten Zeilen eines laufenden oder gestoppten Turns
  nicht.
- GIVEN ein Compact, den der Kern ohne Klick selbst anstößt WHEN er endet THEN bleibt der laufende Turn
  mit seinen Live-Zeilen unangetastet.

### R-WP-61 — Eine Problem-Meldung ist rot und trägt ein rotes Icon ✅ (Paul 2026-11-10, Browser-Abnahme: „tool indicatoren nicht da … status onProblem sollte sichtbar sein im UI mit dem rot und auch einem roten Icon"; Präzisierung Paul 2026-11-10: die Werkzeug-Schritte mit Dreh-Indikator sind gut so — nur der Fehlerfall muss sichtbar sein; gebaut + reviewed 2026-11-10, Inkrement `30ed0525` — Backend 276 Tests, missCount=1, UI 276; Abnahme am Browser steht aus, dort erscheint auch eine API-Retry-Meldung rot)

Läuft ein Werkzeug auf einen Fehler, meldet der Kern das als `PROBLEM` (z. B. ein abgelehnter oder
fehlgeschlagener Tool-Aufruf). Diese Meldung ist auf dem Draht ein **eigenes Ereignis** — der Server
tarnt sie nicht mehr als Werkzeug-Zeile ([R-WP-40](chat.md)) — und das UI zeichnet sie als **eigenen
Eintrag der Timeline**: **roter Text**, das Wort **„Problem"** (wie im Plugin) und ein **rotes Icon**
(AntD-Fehler-Indikator, Widget-vor-Eigenbau [R-WP-46](ui.md)). Wie ein Werkzeug-Schritt trägt sie den
Ursprungs-Stempel, wo einer steht ([R-WP-37](chat.md)), und ist ein Live-Eintrag
([R-WP-11](chat.md)). Im Restore gilt dasselbe wie für Werkzeug-Schritte ([R-WP-47](chat.md)), soweit
der Kern die Meldung in der Historie führt.

#### UC-WP-98 — problemLineIsRedWithIcon ✅ (gebaut + reviewed 2026-11-10, Inkrement `30ed0525` — T1 Backend, T3–T8 `test/chat/chat.problem.test.tsx`; Abnahme am Browser steht aus)
- GIVEN Jon arbeitet und ein Werkzeug läuft auf einen Fehler WHEN die Problem-Meldung ankommt THEN
  steht sie als eigener **roter** Timeline-Eintrag mit dem Wort „Problem" und einem **roten Icon** —
  nicht als graue Werkzeug-Zeile.
- GIVEN die Meldung trägt einen Ursprungs-Stempel WHEN sie gezeichnet wird THEN zeigt der Eintrag
  denselben Agent-Namen wie ein Werkzeug-Schritt.
- GIVEN ein Werkzeug läuft fehlerfrei WHEN seine Zeile ankommt THEN bleibt sie eine graue
  Werkzeug-Zeile — die Färbung gilt nur der Problem-Meldung.


### R-WP-45 — Status-Indikator der Kacheln: blau arbeitet, gelb kompaktiert, grün bereit ✅ (Paul 2026-11-08, Abnahme — „wir nutzen den Status-Indikator, keinen weiteren Status im Chat"; ersetzt den grauen Warte-Rahmen, [R-WP-31](chat.md); gebaut + reviewed 2026-11-08, Inkrement 4 — **Farb-/Maß-Abnahme am Browser steht aus**)

Der Status-Indikator je Kachel (heute ein schwarzer/grauer Punkt) zeigt den Zustand des Agenten
in **drei Farben**: **blau** = der Agent arbeitet, **gelb** = er kompaktiert, **bereit** = ein
**grüner Kreis, größer als bisher**. **Der arbeitende Zustand ist eine drehende Spin** — AntD `Spin`
mit `LoadingOutlined` (die Animation bringt AntD mit, keine eigene CSS-Rotation); in der Timeline
läuft sie als `size="small"` (Paul 2026-11-08, Abnahme). Er steht in der Kachel-Reihe **ohne Abstand
über dem Chat** und
ist damit der **eine** Arbeits-Zeiger — im Chat gibt es keinen zweiten
([R-WP-31](chat.md)); der Warte-Rahmen ist gelöscht. Die Farben gelten je Kachel — auch für
Team-Mitglieder, deren Arbeit sonst nur in der Delegations-Zeile sichtbar wäre (F3,
[open-points](open-points.md)). **Fehler = rot, wie im Plugin** (Paul 2026-11-08) — dargestellt als
AntD-Fehler-/Alert-Indikator, **wenn** AntD dafür ein fertiges Widget hat (Widget-vor-eigenbau,
[R-WP-46](ui.md)).

#### UC-WP-79 — tileStatusIndicatorColors ✅ (gebaut 2026-11-08, Inkrement 4 — **Farb-Abnahme am Browser steht aus**)
- GIVEN ein Agent arbeitet WHEN seine Kachel zeichnet THEN zeigt ihr Status-Indikator **Blau**.
- GIVEN ein Agent kompaktiert WHEN seine Kachel zeichnet THEN zeigt ihr Status-Indikator **Gelb**.
- GIVEN ein Agent ist bereit WHEN seine Kachel zeichnet THEN zeigt ihr Status-Indikator einen
  **grünen Kreis, größer als bisher** — und keine Kachel trägt eine Rahmen-Kontur
  ([R-WP-31](chat.md)).
- GIVEN ein Agent ist **gescheitert** WHEN seine Kachel zeichnet THEN zeigt ihr Status-Indikator
  **rot** (wie im Plugin) — als AntD-Fehler-/Alert-Indikator, sofern es dafür ein fertiges Widget gibt
  ([R-WP-46](ui.md)).

### R-WP-26 — Der Blick in den Agenten-Chat (Slide-out) ✅

Jede Kachel von **Plan, Dev und Review** öffnet rechts einen **schreibgeschützten** Blick in das
eigene Gespräch dieses Agenten (Slide-out). Search ist kein Agent (Sub-Loop von Jon,
[ADR-0079](adr/0079-display-names-without-ork-names.md)) und bekommt keinen Blick. Standard bleibt
Jons Chat; der Nutzer kann in den Chat eines Agenten wechseln und zurück. Interaktion läuft
weiterhin nur über Jon ([R-WP-4](chat.md)): kein Eingabefeld im Blick. **Stop** im UI stoppt den
laufenden Turn vollständig (wie bisher) — die Regel selbst und ihre Grenze (Queue-Klausel) stehen in
[UC-WP-50](chat.md); dieser Absatz behauptet kein Queue-Verhalten. Der Blick lebt seit C-7 **im selben Chat-Widget** und
ist **live** ([R-WP-53](#uc-wp-90)). Die Einsicht liest den RAM-Stand des Agenten im Core —
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

### R-WP-53 — Der Blick lebt im selben Chat-Widget (live) ✅ (C-7, Paul 2026-11-08: „Agenten-Blick = anderes Widget"; gebaut + reviewed 2026-11-09 — Review REJECTED, N1 `984d9e06` mit Kachel-Wechsel-Remount und Reconnect nach fehlgeschlagenem Re-Read; Abnahme am Browser steht aus)

Der Blick ([R-WP-26](#uc-wp-49)) zeigt das Gespräch des Mitglieds **im selben Widget** wie der
Haupt-Chat — dieselbe Log-Pipeline (Markdown mit Streaming [R-WP-28], Werkzeug-Zeilen als
Timeline-Einträge [R-WP-40]), nicht eine eigene Markup-Welt. Weiterhin **read-only**: kein Composer,
kein Senden, kein Stop im Blick — Interaktion läuft nur über Jon ([R-WP-4](chat.md), Stop am
Haupt-Chat, UC-WP-50). Gespeist wird der Blick aus **zwei Quellen, nur für diesen Agenten**: dem
RAM-Snapshot seines Gesprächs (R-WP-26) und einem **Live-Stream seiner Zeilen** — der Blick wächst,
während das Mitglied arbeitet, ohne Reload und ohne etwas anderes neu zu laden. Search bleibt ohne
Blick (R-WP-26); nach einem Neustart bleibt der Blick leer (R-WP-11).

#### UC-WP-89 — theAgentViewRendersInTheSharedChatWidget ✅
- GIVEN der Blick auf ein Mitglied ist offen WHEN er gezeichnet wird THEN nutzt er dieselbe
  Darstellung wie der Haupt-Chat — Markdown, Werkzeug-Zeilen als Timeline-Einträge — und trägt kein
  Eingabe- oder Bedienelement zum Schreiben.
- GIVEN ein Mitglied ohne Gesprächsstand WHEN der Blick geöffnet wird THEN zeigt er nur den
  Hinweis-Head (read-only), keine erfundenen Zeilen.
- GIVEN ein Neustart WHEN der Blick geöffnet wird THEN ist er leer (R-WP-11).

#### UC-WP-90 — theAgentViewGrowsWhileTheMemberWorks ✅
- GIVEN ein laufender Turn WHEN das Mitglied (Plan, Dev oder Review) arbeitet THEN wächst der offene
  Blick **live** — Zwischenschritte und Werkzeug-Zeilen erscheinen ohne Reload.
- GIVEN der Blick ist geschlossen WHEN das Mitglied arbeitet THEN bleibt der Jon-Chat unverändert bei
  seinen Delegations-Zeilen und der finalen Antwort (UC-WP-40).
- GIVEN der Nutzer öffnet den Blick WHEN er geöffnet wird THEN wird nur das Gespräch dieses Agenten
  geladen (Snapshot plus Stream) — Jons Verlauf und die Kacheln werden nicht neu geladen.

### R-WP-28 — Markdown der Antworten: GFM, Zeilenumbrüche, Code mit react-shiki ✅ (Paul 2026-11-02; Schärfung 2026-11-06)

Antworten rendern als Markdown mit **Streaming-Unterstützung**; Code-Blöcke mit **react-shiki**.
Der Renderer ist Mittel, kein Ziel — **er muss liefern**: **GFM** (Tabellen, Durchstreichen,
Aufgabenlisten) und **ein einzelner Zeilenumbruch im Text bleibt eine Zeile** auf dem Schirm
(Soft-Breaks, die zu einem Absatz laufen, sind der gemessene Defekt von `markstream-react`: eigenes
CSS nie eingebunden, keine `breaks`-Option). Halten die Zusagen im Prototypen nicht, gilt der
Fallback **`react-markdown` + `remark-gfm` + `remark-breaks`** (Referenz `ai-schulung`, wo Paul das
Rendering gut findet; `remark-breaks` ist nötig, weil `react-markdown` v10 keine `breaks`-Option hat).
**„Kein kaputtes Markup" heißt:** kein rohes HTML und kein Abbruch, während halbe Syntax über den
Draht kommt; **rohe Syntax-Zeichen (z. B. ein offenes `**`) während des Streams sind erlaubt** und
müssen nach dem letzten Token gerendert sein.
**Nur Antworten sind Markdown**: Nutzer-, Fehler- und Werkzeug-Zeilen bleiben Wortlaut im Text-Element
([AF-ADR-002](adr/002-origin-in-core-display-in-factory.md)). **Tool-Zeilen bleiben sichtbar** und
stehen **inline im Verlauf** an der Stelle, wo sie passieren — nicht als separate Liste hinter dem
Verlauf; kompakt (klein, grau, Tool-Zeilen gemerged wie in `chat.html`) und nach
[R-WP-40](chat.md) als Timeline-Einträge.

#### UC-WP-54 — streamingMarkdownWithInlineToolLines ✅
- GIVEN ein laufender Turn WHEN die Antwort streamt THEN wächst das Markdown sichtbar und bleibt
  während des Streams lesbar — **kein rohes HTML, kein Abbruch**; ein noch offenes Syntax-Zeichen
  steht solange als Text und ist nach dem letzten Token gerendert.
- GIVEN Tool-Aktivität WHEN Zeilen erscheinen THEN stehen sie im Verlauf an ihrer Stelle, kompakt,
  und bleiben nach dem Turn sichtbar.

#### UC-WP-73 — markdownKeepsLineBreaksAndGfm ✅
- GIVEN eine Antwort mit zwei durch **ein** Zeilenumbruch getrennten Sätzen WHEN sie gezeichnet wird
  THEN stehen die Sätze auf **zwei** Zeilen.
- GIVEN eine Antwort mit Tabelle und Durchstreichung WHEN sie gezeichnet wird THEN ist sie eine
  Tabelle und durchgestrichener Text — kein Quelltext.
- GIVEN eine Nutzer-Eingabe mit Markdown-Zeichen WHEN sie im Verlauf steht THEN erscheint sie als
  Wortlaut, nicht als Markup.

### R-WP-40 — Werkzeug- und Delegationsschritte stehen als Timeline im Verlauf ✅ (Paul 2026-11-06; Optik-Umbau AntD `Timeline` gebaut + reviewed 2026-11-07, Inkrement 3 auf `web-factory`; **Lade-Punkt = AntD `Spin`/`LoadingOutlined` small — 2026-11-08-Schärfung — gebaut + reviewed 2026-11-08, Inkrement 4**; Look-Abnahme am Browser steht aus)

Die Schritte eines Turns — Werkzeug, Think-Abschnitt, Delegation — werden als **AntD `Timeline`**
dargestellt: laufen mehrere Schritte **direkt hintereinander** (unterbrochen von einer LLM-Nachricht,
einem **Fehler** oder einem **Diff-Block** — jeder beendet die Gruppe und das Widget), bilden sie
**ein Widget an der Stelle des ersten
Schritts**; **der Punkt eines arbeitenden Schritts ist der loading-Dot**, erledigte Schritte tragen die
**Uhrzeit ihrer Ankunft als HTML-`title`** (Dots groß, Abstände zwischen den Items sehr klein — Paul
2026-11-07; **der Lade-Punkt des arbeitenden Schritts ist AntD `Spin` mit `LoadingOutlined`**
(`size="small"` — die Rotation kommt von AntD, keine eigene CSS-Animation) **und fällt maßlich etwas
größer aus als bisher** — Paul 2026-11-08, Abnahme; die **Geometrie der Items ist unseren Maßen unterworfen**: Minimum-Höhe und
Default-Abstände der Basis werden im Chat-Scope auf das sehr kleine Maß überschrieben — die Basis
kann das Maß nicht per Token drücken). **Der Think-Abschnitt ist ein Eintrag wie ein Werkzeug** (Paul 2026-11-07: „think zeigen
wir wie tool an" — wie im Plugin `chat.html`; die frühere Fassung „Jons Denken zeigt der Chat nicht"
ist damit zurückgenommen, Mitglieder-Denken bleibt unsichtbar, [R-WP-4](chat.md)). **Den Auftrag
erfindet die Fabrik nicht:** was der Core über die Leitung schickt, steht als **Wortlaut** in dem
Eintrag darüber — aus einem Text wird **nie** etwas herausgeparst, und ein Auftrag, den der Draht
nicht trägt, hat keinen eigenen Eintrag. Eine **Delegation ist ein Eintrag** in derselben Timeline:
während das Mitglied arbeitet, zeigt sein Eintrag „working", das Ergebnis erscheint unter ihm; der
Blick in den Gesprächsverlauf des Mitglieds bleibt davon unberührt ([R-WP-26](chat.md)). **Zeiten sind
live und lückenhaft:** Der Draht trägt je Schritt **nur den Augenblick des Eintreffens** (Browser-Uhr)
— eine **Dauer hat allein der Turn** (`done.millis`) und steht an seiner Antwort, **nie je Werkzeug**;
die Fabrik erfindet keine Zeit und **parst auch keine** aus dem Text des Cores. Wo keine Zeit bekannt
ist, trägt der Eintrag **keinen Titel**. Nach einem Reload zeigen die Zeilen, was der Memory enthält
([R-WP-11](chat.md): Dauer und `HH:mm` stehen dort, wo der Core sie selbst in den Text geschrieben
hat), und einen zweiten Speicher dafür gibt es nicht. **Was zeigbar ist, entscheidet der Draht — nichts
wird erfunden:** der Name des arbeitenden Agenten ist sein **Stempel** ([R-WP-37](chat.md): ohne Stempel
kein Name); **arbeitet** heißt am Bildschirm, dass der Eintrag der **letzte Schritt eines laufenden Turns**
ist — mit dem nächsten Schritt oder dem Ende der Antwort endet sein Indikator, denn je Werkzeug meldet der
Core kein eigenes Ende. **Die Delegation ist die Ausnahme:** Anfang und Ende ihres Auftrags stehen
**typisiert** am Draht, und ihr Eintrag trägt den **Namen des Mitglieds**, nicht den Jons. Ein
Diff ([R-WP-36](chat.md)) bleibt ein **eigener, voller Block** im Fluss — kein Timeline-Item.

#### UC-WP-72 — toolStepsAsTimelineEntries ✅
- GIVEN ein Turn ruft Werkzeuge WHEN die Zeilen erscheinen THEN stehen sie als Timeline-Einträge an
  ihrer Stelle im Verlauf, jeder mit dem Namen des arbeitenden Agenten; nennt der Draht zu einem
  Schritt keinen Agenten, nennt auch der Eintrag keinen.
- GIVEN ein Think-Abschnitt von **Jon** streamt WHEN er ankommt THEN erscheint er als Timeline-Item
  wie ein Werkzeug — ohne erfundenen Urheber (Namenzeile „Thinking"), mit dem Draht-Wortlaut und der
  Empfangs-Uhr. GIVEN das **Denken eines Mitglieds** WHEN es läuft THEN ist im Jon-Chat **nichts**
  sichtbar (R-WP-4 — Beleg im Backend, der Draht trägt kein Mitglieder-Denken).
- GIVEN ein Werkzeug läuft WHEN die Timeline zeichnet THEN trägt sein Eintrag einen laufenden
  Indikator; WHEN der nächste Schritt erscheint oder die Antwort des Turns endet THEN endet der
  Indikator und der Eintrag bleibt mit seiner Ankunftszeit stehen. GIVEN die Antwort des Turns endet WHEN deren Indikator endet THEN trägt **nur sie** die
  Turn-Dauer — kein Werkzeugeintrag zeigt eine eigene Dauer.
- GIVEN Jon delegiert an ein Mitglied WHEN die Delegation läuft THEN zeigt ihr Eintrag „working", und
  das Ergebnis erscheint unter diesem Eintrag — nicht am Ende des Verlaufs.
- GIVEN ein Reload nach dem Turn WHEN der Verlauf neu gezeichnet wird THEN stehen die Zeilen ohne
  erfundene Zeiten; ein Eintrag ohne Zeit hat keinen Titel.
- GIVEN ein Turn mit mehr Werkzeugzeilen als die Anzeige fasst WHEN die ältesten fallen THEN
  **behalten** die verbleibenden Einträge ihre Ankunftszeit.

### R-WP-36 — Datei-Änderungen erscheinen live als Diff im Chat ✅ (Paul 2026-11-03, C-4)

Jede **Inhalts-Änderung** (Anlegen **oder** Überschreiben) eines Agenten-Turns erscheint **im Chat als
Unified-Diff** (vorher/nachher, +/− gefärbt, mit Dateinamen) — wie im Eclipse-Plugin (`AIChatView` →
`SimpleDiff.unifiedDiff`). Der Umfang ist der der Core-Oberfläche: `onFileUpdate` feuert bei
`diskEditFile`/`diskReplaceLines`/`diskInsertLines` sowie bei `diskWriteFile` **und**
`eclipseWriteFile` — jeweils Anlegen **und** Überschreiben; nur **Löschen, Umbenennen, Kopieren und
Shell-Schreibvorgänge** erscheinen als Werkzeug-Zeile („Created file: …"), nicht als Diff
(Plugin-Parität). Über 500 Changed-Zeilen wird der Diff **partziell** und nennt am Ende die gekappte
Menge; binärer Inhalt bekommt gar kein Diff, sondern eine Werkzeug-Zeile mit „binary content".
Gebaut 2026-11-05; Vertrag und Regeln:
[File Update Diff](../../org.sterl.llmpeon.core/docs/file-update-diff.md) (R-SD-1…3).

Der Diff gilt für **Jon und sein Team** gleichermaßen: das Write-Event des Cores
(`AiMonitor.onFileUpdate`) trägt keinen Urheber — „sehen und mitlesen" heißt, der Nutzer sieht jede
Änderung am Projekt ([PR-3](index.md): der Core liefert das Ereignis, die Fabrik rendert — kein
Core-Eingriff). Der Diff ist **live-only** wie die Werkzeug-Zeilen ([R-WP-11](chat.md)): er gehört nicht
zum Verlaufs-Bestand und übersteht weder Reload noch Neustart; zum Nach-reviewen dient der Git-Stand im
Datei-Bereich ([Dateien](files.md)).

#### UC-WP-68 — fileChangesShowAsLiveDiff ✅
- GIVEN ein laufender Turn WHEN ein Agent eine Datei (bestehende oder neu angelegte) inhaltlich ändert
  THEN erscheint im Chat ein Diff-Block mit Dateinamen und den geänderten Zeilen (+/− gefärbt), live
  und ohne manuelles Aktualisieren ([R-WP-5](chat.md)).
- GIVEN ein Agent legt eine Datei **neu** an (oder überschreibt sie) WHEN der Turn läuft THEN erscheint
  ein Diff-Block — beim Anlegen aus reinen `+`-Zeilen, zusätzlich zur Werkzeug-Zeile (R-SD-1); per
  **Shell** geschriebene Dateien erscheinen weiterhin nur als Werkzeug-Zeile.
- GIVEN dieselbe Änderung WHEN ein anderer Nutzer dasselbe Projekt betrachtet THEN sieht er denselben
  Diff — ein Chat für alle ([R-WP-15](chat.md)).
- GIVEN der Turn ist vorbei WHEN der Verlauf nach Reload/Neustart gelesen wird THEN erscheint der
  Diff-Block **nicht** im wiederhergestellten Verlauf (live-only, R-WP-11); die geänderte Datei selbst
  ist im Datei-Bereich sichtbar.


### R-WP-29 — Farben und Fließtext der Chat-Zeilen ✅ (Paul 2026-11-07: die **Optik** folgt der Design-Übernahme — [R-WP-39](ui.md); die Fließtext-Grenzen unten bleiben gebaut. **Blasen: volle Breite, Höhe = Text** (Paul 2026-11-08) gebaut 2026-11-08, Inkrement 4 — **Abnahme am Browser steht aus**; **Farben nur als Indikator** — Paul 2026-11-10, Browser-Abnahme)

**Übernahme 2026-11-07:** die Verlaufs-Optik folgt dem Referenz-Design (`ai-schulung` `MessageList` —
Nutzer-**Bubble** rechts in Blau, Antworten links im Fluss); die frühere Fassung „Akzent links, keine
Bubbles" ist im Zuge der Design-Übernahme zurückgenommen ([R-WP-39](ui.md)). Was **weitergilt** und
grün bleibt: kein `<hr>` zwischen Zeilen, `---` innerhalb der Antwort, Tabelle scrollt im Antwortblock,
Bild bleibt in der Antwortbreite, nichts rechtsbündig. Die untenstehende BDD wird im Bau der
Übernahme auf die neue Optik umgeschrieben bzw. gelöscht (Design-Tests-Regel, [R-WP-39](ui.md)).

**Farben nur als Indikator** (Paul 2026-11-10, Browser-Abnahme: „meine nachricht ist jetzt ganz blau —
wir sollten nur die farben als indicatoren nutzen — panels sollten nicht blau sein, nur die rolle
trägt seine farbe"): keine Zeile der Konversation ist eine **Vollfläche in ihrer Rollen-Farbe** —
die Nutzer-Zeile ist dieselbe Karte wie eine Antwort (Oberfläche, Rand, normale Textfarbe; die
Antwort-Karte war schon richtig), die **Rolle trägt ihre Farbe am Namen** (`.chat-role` in der
Farbe ihrer Stimme). Damit ist die Referenz-Lesart „Nutzer-**Bubble** rechts in Blau" für die
Farbfläche zurückgenommen — Pauls Entscheidung gegen die Referenz; volle Breite, Höhe = Text und
„nichts rechtsbündig" bleiben unverändert. Abnahme am Browser; keine neuen Tests
(Design-Tests-Regel).

Die **eigene Eingabe steht rechts in ihrer Blase** (Referenz-Optik, oben); **keine Antwort** ist
rechtsbündig oder mittig zentriert — Antworten stehen links im Fluss. **Blasen nehmen die volle
verfügbare Breite ein, ihre Höhe ist nie größer als der Text** (Paul 2026-11-08, Abnahme: die Blasen
standen deutlich höher als ihr Inhalt); das Innenmaß bleibt eng am Text, die Maße halten sich an die
**AntD-Typografie** (body 14 px / Code 13 px — `ant-design.md`). Jons Antworten haben **keinen
grauen Hintergrund-Block** und keine eingestreuten Markierungen oder Cards im Fließtext. **Einen
Warte-Rahmen gibt es nicht** (Paul 2026-11-08: „einfach löschen" — zwei Indikatoren seien einer zu
viel; dass Jon arbeitet, zeigt der Status-Indikator der Kachel-Reihe, [R-WP-31](chat.md)). Sollte um
die vorläufige Blase **doch** eine Kennzeichung gewünscht sein, dann als AntD **`BorderBeam`**
(https://ant.design/components/border-beam, Paul 2026-11-08) — nie wieder ein eigener gestrichelter
Rahmen. Wo die Rolle wechselt,
**trennt keine Linie**: Blase und Karte trennen die Stimmen optisch — **kein `<hr>`** und
keine andere horizontale Linie **zwischen zwei Zeilen** (die Begründung „Linien treffen sich
seitlich" der Akzent-Fassung ist mit den Bubbles entfallen). Ein `---` **innerhalb einer Antwort** ist deren Inhalt und wird nicht
unterdrückt — es zeichnet als **unsere** kurze, ruhige Linie **im Antwortblock**, nie linienbreit über
den Verlauf; ein Beweis für „keine Trennlinie" prüft deshalb den **ganzen Verlauf** und nicht nur eine
eigene Klasse. **Breite Kost** verbreitert nie die Seite: eine **Tabelle** scrollt innerhalb des
Antwortblocks horizontal, ein **Bild** bleibt innerhalb der Antwortbreite.

#### UC-WP-55 — colorsFollowChatHtmlBaseline ✅
- GIVEN eine Nutzer- und eine Agent-Antwort WHEN beide im Verlauf stehen THEN steht die Eingabe des
  Nutzers **rechts in ihrer Blase**, die Antwort links im Fluss — keine Antwort ist rechtsbündig
  oder mittig zentriert.
- GIVEN dieselben beiden Zeilen WHEN zwischen ihnen die Rolle wechselt THEN verläuft **keine**
  horizontale Linie durch den Verlauf (kein `<hr>`), und keine Antwort ist mittig zentriert.
- GIVEN eine Antwort mit `---` WHEN sie gezeichnet wird THEN steht die Linie **innerhalb** der Antwort
  und nicht als Trennung über die Verlaufsbreite; **kein** Beweis für „keine Trennlinie" darf ein
  modell-gemalenes `hr` übersehen.
- GIVEN eine Antwort mit einer breiten Tabelle und einem Bild WHEN sie gezeichnet wird THEN scrollt die
  Tabelle **im Antwortblock**, die Seite bleibt ungeändert breit, und das Bild bleibt in der
  Antwortbreite.

### R-WP-41 — Die Eingabe wächst mit und findet ihren Fokus zurück ✅ (Paul 2026-11-06, Muster aus `ai-schulung`; Send-Knopf seit 2026-11-08 **nie deaktiviert**)

Das Eingabefeld ist eine **Auto-Grow-Textarea** (von einer Zeile bis ca. fünf, danach scrollt sie) und
steht **unten seiner Spalte** ([R-WP-35](chat.md)). **Senden kann man immer** (Paul 2026-11-08, BWT:
„Senden kann man ja immer"): der Send-Knopf ist **nie deaktiviert** — die frühere Fassung „bei leerer
Eingabe deaktiviert" ist zurückgenommen. Bei **leerer Eingabe** entscheidet der Zustand: **kein
laufender Turn** + Chat-Nachrichten vorhanden ⇒ **Resend** ([R-WP-30](chat.md)); laufender Turn ⇒
passiert nichts. **Während eines laufenden Turns bleibt
die Eingabe benutzbar und der Send-Knopf aktiv** — eine weitere Eingabe geht in die Queue
([R-WP-10](chat.md)); die Referenz-App sperrt ihr Feld im Stream, weil sie keine Queue kennt, und
genau deshalb folgen wir ihr hier **nicht** ([AF-ADR-003](adr/003-reference-ui-is-a-donor-not-a-source.md)).
Ist der Turn vorbei, **kehrt der Fokus in die Eingabe zurück** — der Nutzer tippt weiter, ohne zu
klicken. Tastatur bleibt, wie sie ist
([R-WP-30](chat.md)). Sichtbare Texte kommen aus dem String-Catalog ([R-WP-27](ui.md)).

#### UC-WP-74 — inputGrowsAndRefocuses ✅ (GIVEN 1 seit 2026-11-08 neu: Send nie deaktiviert)
- GIVEN eine leere Eingabe WHEN die App zeichnet THEN bleibt der Send-Knopf **aktiv**; WHEN Text
  entsteht THEN sendet er die Eingabe.
- GIVEN ein Turn läuft WHEN der Nutzer tippt und sendet THEN ist der Knopf **aktiv** und die Eingabe
  geht in die Queue ([UC-WP-14](chat.md)) — kein Sperren des Feldes, kein Deaktivieren im Turn.
- GIVEN ein Turn läuft WHEN der Nutzer bei **leerer** Eingabe Senden drückt THEN passiert nichts —
  keine Leerzeile, keine Queue-Eingabe.
- GIVEN ein langer Mehrzeiler WHEN der Nutzer tippt THEN wächst das Feld bis zu seiner Obergrenze
  und scrollt danach, statt die Chat-Fläche zu verdrängen.
- GIVEN ein Turn war läuft und ist jetzt vorbei WHEN die Eingabe wieder aktiv wird THEN steht der
  Fokus in ihr.

### R-WP-30 — Eingabe: Enter sendet, Shift+Enter macht die neue Zeile ✅ (Paul 2026-11-06, **bedeutet die Umkehrung** von Paul 2026-11-02)

Im Eingabefeld (Textarea) **sendet Enter**; **Shift+Enter** setzt eine neue Zeile — das ist die
Bewegung, die der Nutzer aus jedem Chat-Tool mitbringt (Paul 2026-11-06, nach Blick auf die
Referenz-App). **`Shift+Enter` UND `Ctrl/Cmd+Enter` setzen eine neue Zeile** (Paul 2026-11-06: am Mac
ist `Cmd` das `Ctrl`) — die neue Zeile hat damit zwei Wege, das Senden nur einen. Der
Send-Knopf bleibt die klickbare Alternative und ist **nie deaktiviert** ([R-WP-41](chat.md)).
**Mehrzeilige Eingaben bleiben möglich** — über Shift+Enter, und ein Text mit
eingebetteten Umbrüchen wird so gesendet, wie er dasteht. **Resend ist der Send-Knopf selbst**
(Paul 2026-11-08, BWT): drückt der Nutzer **Senden mit leerer Eingabe**, während **kein Turn läuft**,
aber Chat-Nachrichten existieren, geht die **letzte sichtbare Nutzer-Zeile erneut an das LLM** —
bestätigt oder noch ausstehend (Pending), damit genau Pauls Fall „nach einem Abbruch nochmal senden"
funktioniert; sonst „passiert einfach nichts". Sie läuft wie eine frische Eingabe in die Queue
([R-WP-10](chat.md)) und erscheint als neue Nutzer-Zeile. **Läuft ein Turn, tut der Knopf bei leerer
Eingabe nichts**, und es gibt **keinen eigenen „Resend"-Knopf** (2026-11-08: „der kommt aber ja eh
raus" — die frühere Knopf-Fassung von UC-WP-57 ist damit ersetzt).

#### UC-WP-56 — enterSendsShiftEnterNewlines ✅ (war `enterNewlinesCtrlEnterSends` ✅, Umkehrung Paul 2026-11-06)
- GIVEN das Eingabefeld mit Text WHEN der Nutzer Enter drückt THEN wird gesendet und das Feld ist
  danach leer; WHEN er **Shift+Enter** drückt THEN entsteht eine neue Zeile und es wird **nicht**
  gesendet. GIVEN leere Eingabe WHEN Enter THEN passiert nichts (die Eingabe bleibt, kein leerer
  Turn) — das Resend lebt allein am Send-Knopf ([R-WP-30](chat.md)).
- GIVEN ein Text mit zwei per Shift+Enter gesetzten Zeilen WHEN der Nutzer Enter drückt THEN kommen
  beide Zeilen **gemeinsam und in der Reihenfolge** in einem Turn an.

#### UC-WP-57 — resendLastMessageOnEmptySend ✅ (neu gefasst 2026-11-08, Paul BWT — der eigene Resend-Knopf entfällt, das Resend lebt am Send-Knopf; gebaut 2026-11-08, Runde-6-Delta + N1, Mutations-belegt — Look-/Verhaltens-Abnahme am Browser steht aus)
- GIVEN der Agent arbeitet **nicht** und der Chat hat eine Nutzer-Zeile WHEN der Nutzer **Senden mit
  leerer Eingabe** drückt THEN geht die **letzte sichtbare Nutzer-Zeile** erneut an das LLM —
  bestätigt oder noch ausstehend (Pending; der Stop-Fall: die gestoppte Eingabe ist sichtbar und
  wird wiederholt). Sie erscheint als neue Nutzer-Zeile und der Turn startet.
- GIVEN ein Turn läuft WHEN der Nutzer bei leerer Eingabe Senden drückt THEN passiert **nichts** —
  keine Leerzeile, keine Queue-Eingabe.
- GIVEN der Chat hat keine Nutzer-Zeile WHEN der Nutzer bei leerer Eingabe Senden drückt THEN
  passiert nichts.

### R-WP-47 — Der Restore zeigt Jons Werkzeug-Schritte ✅ (Paul 2026-11-08, Runde 5: „warum machen wir das nicht?" — A7; Annahme als ⏳ in [open-points](open-points.md) zurückversichert; gebaut + reviewed 2026-11-08 (A7 `bf3ce6ec`…`227db893`, N1 `9066a149`…`1bf336bd`), Look-/Verhaltens-Abnahme am Browser steht aus)

Jons **eigene** Werkzeug-Nutzung überlebt den Reload: Call und Result stehen bereits vollständig im
Core-History-Bestand (`<agent>-history.jsonl`) — die Fabrik hält dafür **keinen** zweiten Speicher
([R-WP-11](chat.md) bleibt, sie liest nur den Bestand). Der Restore bildet sie auf die
**Timeline** ([R-WP-40](chat.md)) ab: je Werkzeug ein Schritt mit **Werkzeugname** und einem
 **Auszug des Results** (so `AgentTranscript` es zeigt — gekürzt, kein Wortlaut-Heilen) — Delegationen
([R-WP-40](chat.md)) nennen ihr **Mitglied**; ihr Auszug ist die **Mitglieds-Antwort**, die als
eigene Zeile auf sie folgt. **Nicht** wiederhergestellt: der Live-Fortsetzungstext
und der Diff ([R-WP-36/UC-WP-68](chat.md)), Jons **Think** (kein Eintrag, R-WP-40) und
Mitglieder-**Innen**-Schritte (liegen in deren RAM-Memory, [R-WP-4](chat.md), ADR-0024). Die
Mitglieds-**Antwort** überlebt als Delegations-Ergebnis weiter (UC-WP-40 GIVEN 3 bleibt).

#### UC-WP-82 — restoredHistoryShowsToolSteps ✅ (gebaut + reviewed 2026-11-08, A7 + N1; Abnahme am Browser steht aus)
- GIVEN Jons History enthält Werkzeug-Calls und -Results (Disk, Shell, Web, Delegation) WHEN der
  Nutzer nach einem Reload denselben Chat öffnet THEN zeigt die Timeline je Werkzeug einen Schritt
  mit Name und Result-Auszug, Delegationen mit ihrem Mitglied — in der Reihenfolge des Bestands.
- GIVEN der Bestand enthält einen Tool-Call ohne Result (Turn lief noch) WHEN der Restore liest
  THEN zeigt der Schritt trotzdem seine Ankunft (Name, ohne Result-Auszug) — kein verschluckter
  Schritt.
- GIVEN der Bestand enthält nur eine User-Zeile WHEN der Restore liest THEN ändert sich nichts
  (keine leere Timeline, kein Platzhalter).

### R-WP-31 — Warte-Indikator während des Turns ✅ (Paul 2026-11-02; **Warte-Rahmen seit 2026-11-08 zurückgenommen** — Paul, Abnahme: „einfach löschen"; gebaut + reviewed 2026-11-08, Inkrement 4, UC-WP-65 ✅)

Während ein Turn läuft, zeigt der Chat eine kompakte **Warte-Leiste** („Waiting for AI…" wie im
Plugin: dunkel, Mono, blinkender Cursor) mit dem Live-Preview des Streams; sie steht **über der
Eingabe** ([R-WP-35](chat.md)).
Der **Warte-Zustand endet mit dem Turn** — `done`, `error` und ein **akzeptierter Stop** schließen ihn
ab; die bis dahin gezeigten Zeilen bleiben stehen ([UC-WP-50](chat.md)). **Der Warte-Rahmen ist
gelöscht** (Paul 2026-11-08, Abnahme: „der Rahmen ist komisch — zwei Indikatoren"): eine Zeile trägt
**nie** eine Rahmen-Kontur; dass Jon arbeitet, zeigt der **Status-Indikator** der Kachel-Reihe
([R-WP-7](chat.md), [R-WP-45](chat.md)) — **ohne Abstand über dem Chat**; im Chat selbst gibt es
**keinen** weiteren Arbeits-Status. Die **Warte-Leiste** gilt jedem Turn dieses Projekts, auch dem
eines anderen Nutzers ([UC-WP-22](chat.md)).

#### UC-WP-58 — waitingIndicatorShowsLiveState ✅
- GIVEN ein laufender Turn WHEN er noch nichts geliefert hat THEN zeigt die Warte-Leiste „Waiting for
  AI…"; WHEN der Stream liefert THEN wechselt sie auf das Live-Preview und endet mit dem Turn.

#### UC-WP-65 — waitingMarkEndsWithTheTurn ✅ (gebaut 2026-11-08, Inkrement 4: der Rahmen ist gelöscht, die Arbeit zeigt der Status-Indikator — [R-WP-7](chat.md)/[R-WP-45](chat.md))
- GIVEN ein laufender Turn WHEN die vorläufige Eingabezeile steht THEN trägt **keine** Zeile des
  Verlaufs eine Rahmen-Kontur — der arbeitende Zustand steht im Status-Indikator der Kachel-Reihe.
- GIVEN ein von diesem Browser gestarteter Turn WHEN `done` eintrifft THEN endet der Arbeits-Status,
  und die Zeilen des Turns bleiben sichtbar.
- GIVEN ein laufender Turn WHEN ein `error` eintrifft THEN endet der Arbeits-Status.
- GIVEN ein laufender Turn WHEN der Stop akzeptiert wird THEN endet der Arbeits-Status, und was der
  Nutzer bis dahin gesehen hat, bleibt stehen (kein Geschichts-Read, D-16).

### R-WP-32 — Kacheln als Reihe oben, Klick öffnet den Blick ✅ (Paul 2026-11-02)

Die Agenten-Kacheln ([R-WP-7](chat.md)) stehen **oben nebeneinander in einer Reihe** — der
Agenten-Name als Titel, Kontext, Status und Clear-/Compact darunter. Sie haben einen **Hover-Zustand**,
und ein **Klick auf die Kachel** öffnet den schreibgeschützten Blick ([R-WP-26](chat.md)) — nicht
erst ein separater Link.

#### UC-WP-59 — tilesRowLayoutClickOpensInsight ✅
- GIVEN der Chat-View WHEN er geöffnet wird THEN stehen die Kacheln als Reihe mit Name als Titel und
  den Actions darunter.
- GIVEN eine Kachel WHEN der Nutzer sie klickt THEN öffnet sich der Blick ([R-WP-26](chat.md)), mit
  sichtbarem Hover-Zustand.


### R-WP-34 — Ein gescheiterter Turn ist eine Verlaufszeile ✅ (Paul 2026-11-02, Runde 2)

Scheitert ein Turn, bleiben **beides** im Verlauf: der Text, den der Nutzer gesendet hat, und
**genau eine** Fehlerzeile. Gezeigt wird der **nackte Grund**; im Core-Memory trägt die Zeile den
Marker `[Turn failed]: <Grund>` (der Marker ist Speicher-Konvention, kein UI-Text). Die Fehlerzeile trägt **keinen** Autor, ist
**kein** Jon-Text und **kein** Werkzeug- oder Status-Zeile — sie ist deshalb gerade **nicht**
live-only, sondern Teil dessen, was der Verlauf nach Neustart und Reload zeigt (Abgrenzung zu
[R-WP-11](chat.md)). Sie liegt im **Core-Memory** des Projekts, wird über `GET …/messages` gelesen
und ist damit dem **Modell im nächsten Turn sichtbar** (Verwandtschaft zu R-AEM-1: das Modell weiß,
dass sein Turn gescheitert ist). Zwei Fehlschläge sind **zwei** Fehlerzeilen — keine Dedup, kein
Überschreiben. Ein **Stop** ist kein Fehler und erzeugt keine Fehlerzeile ([UC-WP-50](chat.md)).

#### UC-WP-64 — failedTurnIsAVisibleHistoryLine ✅
- GIVEN ein Turn scheitert **vor** dem Modellaufruf (die Config trägt kein Modell) WHEN der Verlauf
  gelesen wird THEN steht die gesendete Nachricht und danach **genau eine** Fehlerzeile — ohne
  Autors, ohne Jon-Name.
- GIVEN ein Turn scheitert **im** Modellaufruf WHEN der Verlauf gelesen wird THEN steht dieselbe
  Paarung aus Nachricht und Fehlerzeile.
- GIVEN ein Turn wird vom Nutzer gestoppt WHEN der Verlauf gelesen wird THEN steht **keine**
  Fehlerzeile.
- GIVEN ein gescheiterter Turn WHEN die Seite neu geladen wird THEN sind Nachricht und Fehlerzeile
  weiterhin im Verlauf (derselbe Bestand wie vor dem Reload).

### R-WP-48 — Je Turn genau ein Endereignis auf dem Draht ✅ (Verdacht 2026-11-06, **belegt 2026-11-08** — Runde-6-Diagnose: ein `Error` im Turn-Task schweigt vollständig, keine Log-Zeile, Kachel zurück auf IDLE, Warteschleife stand; Nachtzyklus 2026-11-08; gebaut + reviewed 2026-11-09 — Review REJECTED, N1 `b47126d0`/`76d3e653` mit Besitz-Regel des `error`-Ereignisses und Kachel-Lesefolge; Abnahme am Browser steht aus)

Jeder Turn, der einen Turn **eröffnet**, endet auf dem Draht mit **genau einem** Endereignis:
`done` (normal oder nach Stop) oder `error` — **auch** wenn im Turn-Task ein `Error` (oder sonst
ein `Throwable`) entkommt, der die `RuntimeException`-Zweige passiert: er zählt als gescheiterter
Turn ([R-WP-34](chat.md) — Verlaufszeile + `error`). Ein Submit, das nur **einreiht** (der Turn
läuft bereits), sendet **kein** `done` — und sein eigenes Scheitern (z. B. eine abgelehnte Config)
bleibt eine **Verlaufszeile ohne eigenes Endereignis**: das `error` gehört dem eröffneten Turn, ein
Fremdes dürfte die Live-Sicht des laufenden Turns nicht abreißen; die Zeile zeigt der nächste
Refresh nach dessen `done`. Einreihen selbst ist kein Ende — der besitzende Turn endet für beide.
Die Garantie liegt beim **finally-Pfad der Fabrik** (`ProjectRuntime`), kein Core-Eingriff.

Dazu die Browser-Seite: nach jedem (Re)Connect wird der Kachel-Stand **frisch gelesen**, sobald die
Verbindung steht ([UC-WP-84](chat.md)) — dieser frische Stand ist der maßgebliche; ein **veralteter**
Schnappschuss darf den Warte-Zustand („busy", [R-WP-31](chat.md)/[R-WP-45](chat.md)) nie dauerhaft
halten. Die erste Lesung beim Öffnen des View ([R-WP-57](chat.md)) ist ein **Live-Read, kein
veralteter Schnappschuss**; zeigt sie einen Stand, den der frische Read der stehenden Verbindung
danach korrigiert, steht dieser nur bis zu dessen Antwort. Und ein Read, der **vor** einem
`agents`-Endschnappschuss begann, überschreibt ihn nicht (der frischeste Stand gewinnt, wie beim
Verlauf über seine Lesefolge). Sonst bleibt ein Turn, der zwischen Kachel-Read und
Stream-Abonnement endete, für immer als „arbeitet" stehen.

#### UC-WP-83 — everyOpenedTurnEndsOnTheWireExactlyOnce ✅ (gebaut + reviewed 2026-11-09, Inkrement 1 + N1 `430343f2`/`b47126d0`)
- GIVEN ein Turn-Task, in dem ein `Error` entkommt (kein `RuntimeException`-Zweig) WHEN der Task
  durchläuft THEN **genau ein** `error`-Ereignis mit Verlaufszeile nach [R-WP-34](chat.md), danach
  der `agents`-Endschnappschuss und der Turn-Claim ist frei.
- GIVEN ein Turn scheitert mit `RuntimeException` (nicht Canceled) WHEN der Task durchläuft THEN
  **genau ein** `error` (Bestand, nicht neu).
- GIVEN ein Turn wird gestoppt WHEN der Task durchläuft THEN **genau ein** `done`, kein `error`
  (Bestand, [UC-WP-50](chat.md)).
- GIVEN ein einreiender Submit (Turn läuft bereits) WHEN der Task durchläuft THEN sendet **er**
  kein Endereignis; scheitert er (z. B. abgelehnte Config), stehen seine Verlaufszeilen im Verlauf,
  **ohne** eigenes `error` auf dem Draht — der besitzende Turn sendet seines.

#### UC-WP-84 — theWaitStateIsDerivedFromAFreshReadAfterConnect ✅ (gebaut + reviewed 2026-11-09, Inkrement 2 + N1 `e72c6c76`/`76d3e653` — Kachel-Lesefolge in `chat.board.ts`)
- GIVEN ein Turn endet **zwischen** Kachel-Read und Stream-Verbindung WHEN die Seite (neu) verbindet
  THEN wird der Kachel-Stand nach dem Verbinden frisch gelesen und die Kachel-Reihe zeigt **bereit** —
  kein Warte-Zustand aus dem alten Schnappschuss.
- GIVEN der Stream fällt während eines laufenden Turns weg WHEN der Reconnect durchläuft THEN wird
  der Kachel-Stand ebenfalls frisch gelesen (derselbe Beweis wie beim ersten Verbinden).
- GIVEN eine Kachel-Lesung beginnt, bevor der `agents`-Endschnappschuss eintrifft WHEN ihre Antwort
  **nach** dem Schnappschuss ankommt THEN überschreibt sie ihn **nicht** — der frischeste Stand
  gewinnt, die Kachel zeigt bereit.

### R-WP-57 — Die Kachel-Reihe liest ihren Roster, sobald der View steht — unabhängig vom Stream ✅ (Paul 2026-11-10, „nur das mal reparieren" — Defekt aus Runde 7: die Reihe war im Browser weg, [Open Points](open-points.md); gebaut + reviewed 2026-11-10, Inkrement N2 `4a607d11` + N3 `ccd62bd4`, Browser-Abnahme Paul 2026-11-10: „Agenten wieder da")

Beim Öffnen des Chat-Views wird der Roster (`GET …/agents`) gelesen, **sobald der View steht** — nicht
erst, wenn der Ereignis-Strom öffnet. Der frische Read nach jedem (Re)Connect ([R-WP-48](chat.md),
[UC-WP-84](chat.md)) bleibt unverändert und schützt den Zustand nach einem Turn-Ende; er ist **kein**
Ersatz für die erste Lesung. Der Verlauf hängt ohnehin nie am Stream — die Reihe darf es auch nicht:
öffnet der Strom nicht, bleibt der Chat funktionsfähig und die Reihe sichtbar. Das Backend antwortet
auf ein bestehendes Projekt **immer** mit der vollen Reihe (feste Team-Größe: Jon, Plan, Dev, Review,
Search; Live-Zustand je Kachel) — eine leere Liste heißt im UI „Read nie angekommen", nie „kein Team".

#### UC-WP-94 — theTileRowReadsItsRosterIndependentOfTheStream ✅ (gebaut + reviewed 2026-11-10, N2 `4a607d11` + N3 `ccd62bd4`; GIVEN 1 im bestehenden UC-WP-84-Test bewiesen, GIVEN 3 im Fehler-Test — Testzahl 268 unverändert)
- GIVEN der Chat-View wird geöffnet WHEN der Ereignis-Strom **nicht** öffnet (Antwort bleibt aus oder
  schlägt fehl) THEN steht die Kachel-Reihe trotzdem — der Roster-Read ist nicht vom Stream abhängig.
- GIVEN der Stream öffnet WHEN die Seite verbindet THEN wird der Kachel-Stand zusätzlich frisch
  gelesen (UC-WP-84, unverändert).
- GIVEN der Roster-Read scheitert WHEN die Reihe zeichnet THEN wird der Fehler als **Seiten-Fehler**
  gemeldet und der **zuletzt gelesene Stand** der Reihe bleibt stehen — kein Zurückfallen auf leer
  (kein stilles Schweigen); die nächste Lesung (done, Stop, Aktion) korrigiert ihn.



### R-WP-35 — Der Chat scrollt, die Eingabe steht unten ✅ (Runde 2, Inc 3; Scroll-Verhalten geschärft Paul 2026-11-08 — Schärfungen + FloatButton gebaut + reviewed 2026-11-08, Inkrement 4, UC-WP-80 ✅; die **Look-Anteile** — volle Bildschirmbreite, weißer Composer, Senden rechts — nehmen wir am Browser ab)

Der Chat ist eine **Fläche mit eigenem Scroll**: der Verlauf scrollt in seiner **eigenen** Fläche,
während die **Kachel-Reihe oben** und die **Eingabe unten** stehen bleiben — die Warte-Leiste
([R-WP-31](chat.md)) liegt direkt **über** der Eingabe. Neue Zeilen scrollen **nur** dann ans Ende,
wenn der Nutzer **unten** steht; wer oben nachliest, wird nicht nach unten gerissen. Die Fläche
nutzt bei behaltenem Rand die **volle Bildschirmbreite** (Paul 2026-11-08, Abnahme: der Chat nahm
nicht den ganzen Platz ein, die Ränder waren asymmetrisch) — der Scroll gehört dem Verlauf, nicht der
Seite. Die **Eingabe-Zeile** ist unten **weiß und ruhig über die volle Breite, der Senden-Knopf
rechts daneben** (Paul 2026-11-08, SOLL-Bild). **Schärfungen 2026-11-08 (❌):** nach der **eigenen
Eingabe** scrollt der Verlauf **immer** ans Ende — unabhängig von der Leseposition; **scrollt der
Nutzer zurück an das Ende, greift das Auto-Scrollen wieder**; und **solange er vom Ende entfernt ist,
steht ein schwebender Knopf** — Pfeil nach unten, „Scroll bottom" (AntD `FloatButton`, wie
`BackTop`, nur umgekehrt), der zum Ende springt und das Auto-Scrollen wieder bewaffnet.

#### UC-WP-66 — chatScrollsWithFixedComposer ✅
- GIVEN ein Chat mit langem Verlauf WHEN die Seite geöffnet wird THEN scrollt der Verlauf in seiner
  eigenen Fläche, und Kachel-Reihe wie Eingabe bleiben sichtbar.
- GIVEN der Nutzer steht am Ende des Verlaufs WHEN eine neue Zeile erscheint THEN scrollt der Verlauf
  ans Ende.
- GIVEN der Nutzer hat nach oben gelesen WHEN eine neue Zeile erscheint THEN bleibt der Verlauf an
  seiner Position stehen.

#### UC-WP-80 — scrollBottomButtonReArmsAutoScroll ✅ (gebaut + reviewed 2026-11-08, Inkrement 4)
- GIVEN der Nutzer ist vom Ende entfernt WHEN neue Zeilen erscheinen THEN bleibt der Verlauf an seiner
  Position und ein schwebender Knopf (Pfeil nach unten, „Scroll bottom", AntD `FloatButton`) ist
  sichtbar.
- GIVEN der Nutzer klickt den Knopf WHEN er springt THEN landet der Verlauf am Ende und das
  Auto-Scrollen greift wieder — neue Zeilen ziehen wieder ans Ende.
- GIVEN der Nutzer hat nach oben gelesen und scrollt selbst zurück ans Ende WHEN er dem Ende nahe ist
  THEN greift das Auto-Scrollen wieder.
- GIVEN der Nutzer sendet selbst WHEN der Turn läuft THEN scrollt der Verlauf immer ans Ende,
  unabhängig von der Leseposition.
