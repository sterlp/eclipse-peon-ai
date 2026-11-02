# Open Points — AI Factory

Status: ❓ offen · ⏳ selbst entschieden (Rückversicherung steht aus) · 🔒 geklärt.
Durchgangslager: jeder Punkt wandert am Zyklusende in ein Feature-Doc oder entfällt.

## ⏳ selbst entschieden (2026-10-25/26) — bitte überfliegen

* **⏳ Dev-Modell wird gespeichert, aber vom Core nicht angewandt** (2026-11-02, aus dem 7a-Bau) —
  [R-WP-23](config.md) zeigt je Agent ein Modell-Feld; für den Slot `dev` erzwingt der Core
  (`devAgentConfig()`) aber das Basismodell — die gespeicherte Überschreibung wirkt nicht. Decision:
  Feld bleibt und speichert doc-konform (`agents/dev/AGENT.md`); kein UI-Hinweis, kein
  Core-Workaround. *Andersfall:* soll Devs Modell wirklich wirken, ist das eine Core-Story (Proposal
  mit WARUM) — dann UC in config.md nachziehen.
* **⏳ „Chat" öffnet das zuletzt aktive Projekt** (2026-11-02, meine Empfehlung, Rückversicherung
  ausstehend) — [R-WP-1](ui.md): Top-Menü „Chat" ohne aktives Projekt → Projekte; mit aktivem Projekt
  → dessen Chat. *Andersfall:* „Chat" führt immer auf die Projektliste (keine Abkürzung) oder es
  braucht eine Chat-Auswahl.
* **⏳ Provider-Wechsel setzt Modell/URL auf die Defaults des Providers** (2026-11-02) — wie der
  Provider-Dialog im Plugin ([R-WP-16/UC-WP-62](config.md)). *Andersfall:* Felder nur leeren statt
  Defaults einzutragen.

* **⏳ Fehler-Ergebnis einer Delegation erscheint wörtlich als Mitglieder-Zeile** (2026-10-30,
  Da-Dok-Befund B4) — [UC-WP-40](chat.md) hat den Fall jetzt ausdrücklich; abgeleitet aus „wie es ist"
  (AF-ADR-002): ein Java-Stacktrace im Web-Chat ist hässlich, aber ehrlich, und Parsen/Kürzen ist
  verboten. *Andersfall:* soll der Stacktrace nicht erscheinen, muss der Core Fehler-Ergebnisse
  kennzeichnen — ein neuer Core-Mechanismus, keine Fabrik-Heuristik.
* **⏳ UC-WP-9 klargestellt: die sichtbare Ablehnung ist immer Jons** (2026-10-26, Abnahmefrage A1 von
  Inc 4) — [UC-WP-9](files.md) trägt jetzt zwei GIVEN: Jons eigener Schreibversuch hinterlässt die
  Werkzeug-Zeile im Chat, die Ablehnung eines **Team-Mitglieds** endet beim Modell (Werkzeug-Ergebnis,
  keine Datei, **keine** Chat-Zeile), weil [R-WP-4/UC-WP-40](chat.md) Mitglieder-Interna unsichtbar
  hält. Klarstellung, keine Bedeutungsänderung. *Andersfall:* soll eine Mitglieds-Ablehnung doch im Chat
  erscheinen, ist das eine Ausnahme zu R-WP-4 und braucht eine eigene Regel.
* **⏳ Der Urheber-Name gehört zur dauerhaften Zeile, nicht zum Live-Fragment**
  ([UC-WP-39](chat.md), 2026-10-26 gebaut, 2026-10-27 mit dem Core-Rückbau wieder ❌) — die Live-Zeile eines laufenden Turns trägt **keinen** Namen, weil live nur Jon spricht
  und damit nichts zu verwechseln ist. *Andersfall:* soll auch das wachsende Live-Fragment ein Label
  tragen, ist das eine kleine UI-Zeile — dann sagt das Doc es ausdrücklich.


* **⏳ Config-Quelle ist eine eigene Datei der Factory** (`~/.aifactory/config/config.properties`, Keys auch
  aus Env) — die Annahme "wir lesen den Bestand aus `~/.peon`" war **falsch**: der Plugin-Bestand liegt
  in Eclipse-Präferenzen, `~/.peon` enthält nur Skills/Agents/Commands. Decision:
  [R-WP-9](config.md), Begründung
  [Architektur → Konflikt](aifactory-architektur.md#konflikt-dokumentiert-2026-10-25).
  *Andersfall:* wenn der Plugin-Bestand doch gelesen werden soll, bräuchte es einen Core-Port des
  Config-Stores auf Dateien — plus die Frage, wessen Werte dann gewinnen.
* **⏳ Runtime-Zustand niemals im Checkout** — nur `stories/` und `proposals/` sind dort erlaubt
  ([Architektur-Invariante](aifactory-architektur.md#invarianten)); Grund: `git status` des Nutzers
  darf nicht vermüllt werden.
* **⏳ Doppelte URL: Doc gewinnt gegen Code** — Inc 1 baute `400 "already present"`,
  [R-WP-2/UC-WP-3](project.md) verlangt „kein zweiter Clone, keine Fehlermeldung". Decision:
  API antwortet `200` mit dem bestehenden Projekt, UI zeigt es nur an. *Andersfall:* willst du die
  Meldung als Feedback, wird UC-WP-3 umformuliert und der `400` bleibt — Aufwand dann: Doc + 3 Tests
  rückabwickeln.
* **⏳ „Bereits vorhanden" ist `200`, nicht `201`** — abgeleitet: es entsteht nichts Neues, also kein
  Created. Die Regel nennt bewusst keinen HTTP-Code.
* **⏳ Gleicher Ordnername + andere URL ⇒ abgelehnt**
  ([R-WP-18/UC-WP-26](project.md)) — abgeleitet aus dem Ziel `<home>/projects/<name>`: zwei Repos
  können sich nicht einen Ordner teilen. *Andersfall:* zweiter Name (`demo-2`) statt Fehler → Registry
  und Ordnername entkoppeln.
* **⏳ Vorhandener Nicht-Git-Ordner wird nie gefüllt oder überschrieben**
  ([R-WP-18/UC-WP-27](project.md)) — Ableitung: die Factory gehört dieser Ordner nicht.
* **⏳ v1 klonen wir nur über `https://`** — ssh-URLs (`git@host:org/repo.git`) tragen zwar kein
  Passwort, setzen aber Schlüssel oder Passphrase-Prompt voraus; beides verbietet
  [R-WP-21](project.md) (nie interaktiv). Deshalb abgelehnt statt halb funktionieren
  ([R-WP-20/UC-WP-33](project.md)). *Andersfall:* private Repos per ssh wären eine eigene Story
  mit Secret-Ablage und bekannten Hosts — dann diesen Satz wieder raus.
* **⏳ Die Registry-Datei ist keine Löschfreigabe**
  ([R-WP-19/UC-WP-29](project.md)) — entfernt wird nur unter `<home>/projects`; ein von Hand
  eingetragener absoluter Pfad (`/`, `/Users/…`) darf beim Entfernen nichts löschen.
* **⏳ `aifactory.home` / Env `AIFACTORY_HOME` ist Deployment, keine Nutzer-Einstellung** —
  [R-WP-5](chat.md) sagt „der Nutzer kann den Ort nicht ändern": gemeint ist kein Feld in den
  Einstellungen. Ohne den Override könnten Tests nicht in einem Temp-Home laufen.
* **⏳ Klonen synchron, Frist 300 s, ohne Fortschritt im UI** — während ein Klon läuft, blockiert
  „Hinzufügen" weitere Adds (Lesen der Liste nicht). Für große Repos später: Hintergrund-Klon +
  Status in der Liste.
* **⏳ Shell läuft ohne Rückfrage** — bewusst im MVP ([Architektur-Invariante Schreibgrenze](aifactory-architektur.md#invarianten)):
  das Schreib-Pinning fängt `disk*`-Schreibzugriffe ab, **nicht** was `shell` in einem Prozess tut.
  Akzeptiertes Risiko für einen Prototyp im internen Netz; vor externem Zugriff ist eine Zusage oder
  eine Sandbox nötig.
* **⏳ Auslieferung ist Teil des Inkrements, nicht des Deployments** — eine View, die im Browser 500
  liefert, gilt als nicht gebaut, auch wenn alle Unit-Tests grün sind. Deshalb
  [R-WP-22/UC-WP-34/UC-WP-35](ui.md) mit echten HTTP-Tests auf einem zufälligen Port.
  Anlass: seit einem Hand-Commit (`8c7dec24`, `@Controller` auf `SpaForwardController`) beantwortete
  dieser Mapping-Pfad **jeden** `/ui/**`-Request — Assets wurden nie ausgeliefert, `/ui/` endete in
  ~6 000 Weiterleitungen und einem `StackOverflowError` pro Aufruf. Die API war grün, die App tot.
* **⏳ Paco darf `docs/` lesen** und antworten, schreiben nur in die eigenen Vorschlags-Ordner
  ([Paco R-PC-2](paco.md)).
* **⏳ MVP ohne Plan-Werkzeuge, ohne memory\*-Werkzeuge, ohne MCP** — die drei sind Plugin-only bzw.
  ein Core-Port und werden eigene Stories; der MVP-Jon schreibt Pläne mit den Disk-Tools nach
  `peon-plan/overview.md`.
* **⏳ Nach einem Neustart ist nur Jon wieder da, sein Team beginnt leer** — abgeleitet aus
  „sonst wie beim Eclipse-Plugin" + globalem ADR-0024 (Sklaven sind RAM-only)
  ([R-WP-11](chat.md)). Team-Historie über den Restart wäre eine neue Regel
  und ein Core-Eingriff.
* **⏳ „Workspace" ist für die Factory das geklonte Projekt** — kein Eclipse-Workspace-Begriff,
  Arbeitsordner ist immer das Projekt-Root
  ([R-WP-3](project.md)/[R-WP-6](files.md)).
* **⏳ Key wird nicht auszugsweise angezeigt** ([R-WP-16](config.md)) — angenommen: „maskiert"
  heißt **kein Zeichen** nach draußen, gezeigt wird nur `gesetzt`/`fehlt` und die Quelle. Abgeleitet aus
  R-WP-20 (Zugangsdaten werden niemandem zurückgegeben) und dem fehlenden Login (R-WP-17). *Andersfall:*
  willst du die letzten 4 Zeichen zur Unterscheidung mehrerer Keys, ist das eine Satzänderung — ein
  Auszug bleibt ein Auszug.
* **⏳ Datei schlägt Umgebungsvariable** ([R-WP-16/UC-WP-38](config.md)) — die Variable ist der
  Startwert für den ersten Start, die Datei der gepflegte Stand (sonst könnte die UI nie etwas ändern).
  *Andersfall:* Deployment-Diktat („was der Operator setzt, gilt") ⇒ Reihenfolge umdrehen und das Feld
  im View sperren.
* **⏳ Key über die UI entfernen** (2026-11-02, Paul-Szenario: erster Request lieferte „turn needs an
  api key", ein gesetzter Key war im UI nicht erkennbar und nicht löschbar) — Decision: R-WP-16
  geschärft — „Remove key" schreibt den Key aus der Datei, ENV-Quelle wird nur gemeldet, der Status
  wird nach jedem Speichern frisch geliefert. *Andersfall:* soll das UI den Key nicht entfernen
  können, gilt wieder nur die Datei von Hand — dann aber mit sauberer Anzeige des Status.
* **⏳ kein globales Temperatur-Feld** ([R-WP-16](config.md)) — im geteilten Bestand existiert
  Temperatur **nur je Agent**; ein globales Feld wäre erfunden. Der View zeigt, was es gibt.
  *Andersfall:* „je Agent" mit ins MVP ⇒ eigener View-Bereich plus Core-Schreibpfad pro Agent.
* **⏳ Key-pflichtige Provider** ([UC-WP-20](config.md)) — alle außer Ollama und LM Studio
  brauchen einen Key; ohne Key lehnt die Factory, bevor ein Modell gebaut wird. Abgeleitet aus dem
  Bestand des Cores (nur diese beiden ersetzen den Key durch einen Platzhalter). *Andersfall:*
  GitHub-Provider brauchen in der Praxis auch einen Token — dann mit in die Liste.
* **⏳ Provider-Liste steht doppelt (UI + Server)** — der Server validiert gegen `AiProvider` (9 Namen),
  die Einstellungen-Auswahl im UI führt dieselben neun Namen als eigene Liste
  (`factory-ui/src/api/settings.ts`). Abgeleitet: die Alternative wäre, die Liste über die API
  auszuliefern — das würde [`SettingsDto`](config.md) um ein Feld verbreitern, obwohl R-WP-16 die
  Antwortfläche bewusst klein hält (acht Angaben). *Andersfall:* ein neuer Provider im Core ⇒ zwei
  Stellen anfassen; dann eigene Regel „ein Provider-Katalog je Instanz".
* **⏳ Entfernen lässt den Chat-Bestand liegen** — `Entfernen` löscht Eintrag und Checkout, wie
  [R-WP-19](project.md) es aufzählt, aber **nicht** `state/<id>`. Folge: wer dieselbe URL erneut
  anlegt, findet das alte Gespräch wieder (Identität ist die URL). Abgeleitet aus „genau ein Chat je
  Projekt" + [R-WP-11](chat.md); ein Verlauf, der mit dem Projekt sterben soll, wäre eine eigene
  Regel. *Andersfall:* „Entfernen heißt Tabula rasa" ⇒ `state/<id>` mitlöschen, dann aber mit Rückfrage.
* **⏳ Wiederhergestellte Verlaufszeilen tragen keine Zeit** — der Core-History-Bestand kennt keinen
  Zeitstempel; eine eigene Zeit-Datei wäre ein zweiter Speicher ([R-WP-11](chat.md)). Live-Zeilen
  zeigen die Zeit, der wiederhergestellte Verlauf nicht. *Andersfall:* Zeit im Core-History-Format =
  Core-Story.


## ❓ offen

* **❓ Core: der Abbruch verliert eine bereits gepollte Queue-Eingabe** (2026-11-01, Inc 5d gemessen) —
  [UC-WP-50](chat.md) verspricht „gequeuete Eingaben bleiben in der Queue"; tatsächlich verliert
  `AbstractAgent.call` eine Eingabe, die der laufende Turn beim Stop schon gepollt hat (Poll, dann
  Cancel-Ausstieg, der Drain findet eine leere Queue). Nur noch wartende Eingaben legt
  `handleAbortAndDrain` als Memory-Zeile ab. Die Fabrik kann das nicht reparieren: sie setzt genau das
  Flag, das auch der Plugin-Stop setzt. UC-WP-50 bleibt deshalb ❌, und der Test benennt die Lücke.
  *Fix:* Core-Story — den zuletzt gepollten Eintrag beim Cancel zurücklegen (oder vor dem Verlassen
  drainen); danach UC-WP-50 ✅.
* **🔒 UI-Sprache = Englisch** (2026-11-02, Paul bestätigt; löst den „AGENTS.md-Language-Block"-
  Konflikt von 2026-11-01) — AGENTS.md („UI: English") gilt, [R-WP-27](ui.md) formalisiert: Strings
  im Catalog (`content.ts`/Locale), keine deutschen Draht-Werte. Umsetzung als eigene Story dieses
  Zyklus.
* **❓ Chats-Übersicht links (Vision, Paul 2026-11-02):** jedes Projekt ein „Tree"/Accordion in der
  linken Navigation, mehrere Paco-Chats je Projekt (**+**), Überblick über alle Chats; Main-Chat
  immer Jon. Gehört zu [Paco](paco.md) — erst mit der Paco-Story, kein MVP-Mechanismus.

### Docs-Review gegen Pauls Produktvision (2026-10-27) — eine Frage nach der anderen

* **❓ V6-Folge:** Im MVP gibt es keine Nutzer (Paul): alle teilen `user/` und — offen — wohl auch ein
  Paco-Gespräch; „privat je Nutzer" (R-PC-1, UC-PC-2) gilt erst mit Login. R-PC-1 entsprechend schärfen.
* **❓ V5 Config-Umfang:** Docs = nur LLM-Felder in `config/config.properties`, MVP ohne MCP.
  Vision: MCP, Agenten-Config, Prompt-Override (Default sichtbar), Codegraph an/aus (Default an beim
  Checkout), Config-Verzeichnis git-sicherbar, Skills für alle Projekte geteilt.
  **Paul 2026-10-27:** jeder Agent (auch Jon, Team, Paco) ist eine Datei; Default wird konfiguriert,
  jeder Agent per Checkbox „überschreiben"; nur File-Config; Datei ohne Prompt ⇒ eingebauter Prompt
  (auch fürs Plugin). 🔒 V5a/b: `agents/<id>/AGENT.md` für jeden Agenten, Rumpf ersetzt, leer ⇒ eingebaut
  ([Core: builtin-agent-prompt-tracked.md](../../org.sterl.llmpeon.core/docs/builtin-agent-prompt-tracked.md)).
  🔒 Staleness: kein Hinweis. 🔒 V5c → [R-WP-23](config.md) (Checkbox blendet ein, Abwählen löscht).
  Offen V5-Rest: MCP, Codegraph an/aus, Skills geteilt.
* **❓ V7 TMP-Hinweis:** Vision: Agenten sollen `tmp/<projekt-name>` anlegen. R-WP-13 pinnt den
  System-TMP — passt, braucht aber eine Prompt-Zeile (Regel fehlt).
* **❓ V8 Entfernen:** Vision „Verzeichnis + Config weg". Heute bleibt `state/<id>` liegen (⏳ oben).
* **❓ V9 Produktname:** „Open AI Fabric (OAF)" vs. „AI Factory" in allen Docs/Code — umbenennen?
* **🚧 V11 Config-Repo (Paul 2026-10-28):** Fabrik startet auf frischem Node ohne Platte → beim Start
  Checkout eines Config-Git-Repos (`main`), Struktur wie `.peon` (Config, `agents/`, Skills,
  `projects.yml` statt `projects.json`); alles außer Chat-History wird committet. UI: „nur testen"
  (Datei, kein Commit) vs. „übernehmen" (commit + push). Kollidiert mit: R-WP-11 (Chat überlebt
  Neustart — auf neuem Node weg), R-WP-9/UC-WP-36 (Home = `~/.aifactory`, nie `~/.peon`), R-WP-16
  (Keys verlassen nie den Server), R-WP-20 (nur öffentliche Repos ohne Zugangsdaten — Config-Repo
  braucht Push-Rechte). 🔒 Keys → [config-repo.md](config-repo.md) R-CR-1 (verschlüsselt, ein Secret aus
  der Umgebung). Weitere offene Punkte stehen dort.
* **❓ Toter Guard (gefunden 2026-10-28):** `ProcessCommandRunner` setzt `BatchMode=yes` als
  Umgebungsvariable — ssh liest das nicht, wirkt nur als `GIT_SSH_COMMAND="ssh -o BatchMode=yes"`. Heute
  folgenlos (SSH-URLs abgelehnt), wird mit SSH ([R-CR-7](config-repo.md)) zum Hänger — Fix gehört in
  dessen Bau (UC-CR-8 Passphrase-Fall).
* **🔒 V10 Inc 4** (Schreib-Pinning, Datei-Bereich) — reviewed 2026-11-01 auf `web-factory-mvp`: CONCERNS,
  kein Blocker; Da-Mek-Pinning, R6 und alle Inc-4-Tests halten dem Origin-Umbau stand. Die vier CONCERNS
  (Symlink-Listing-Metadaten, Paritäts-Pin selbst-referentiell — Core-Test, `chat ↛ project` im UI
  unmechanisch, Schreib-/Lesewurzeln an zwei Stellen) werden in Inc 5 gehärtet; die Core-Test-Anmerkung
  braucht Pauls Freigabe.

* **❓ Rollen und Rechte** (Vision: Admin = Config/Keys, User = alles außer Config, Reader = nur lesen +
  „Read Jon") — der MVP hat keinen Login (R-WP-17) und kann deshalb nicht unterscheiden: jeder darf
  alles. Empfehlung: Rollen **nicht** im MVP, sondern als eigene Story mit OAuth2 (Vision sagt dasselbe);
  bis dahin ist der Betrieb ein vertrauenswürdiger Kreis (Prototyp kennt nur einen Nutzernamen, R-WP-17).
  *Andersfall:* schon im MVP ein Admin-Passwort = halbherzige Authentifizierung, die ich nicht bauen würde.
* **❓ Story-Import** — wer holt `stories/` und `proposals/` in die Feature-Docs und wann? Bewusst außerhalb des
  Paco-Features ([Paco, Offene Punkte](paco.md)).
