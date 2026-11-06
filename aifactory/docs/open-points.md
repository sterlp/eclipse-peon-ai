# Open Points — AI Factory

Status: ❓ offen · ⏳ selbst entschieden (Rückversicherung steht aus) · 🔒 geklärt.
Durchgangslager: jeder Punkt wandert am Zyklusende in ein Feature-Doc oder entfällt.
## Runde 3 — AntD-Umbau der Oberfläche (Paul 2026-11-06)

Entschieden: **AntD als neue Design-Basis**, Bootstrap verlässt die App
([R-WP-39](ui.md)), Navigation wird der **Projektbaum im Sider** ([R-WP-38](ui.md)), Zeiten bleiben
**live** ([AF-ADR-003](adr/003-reference-ui-is-a-donor-not-a-source.md)). Geber der Muster ist
`/Users/sterlp/dev/workset/ai-schulung/app/frontend` — **nie** ihr Zustandsmodell (AF-ADR-003).
Reihenfolge, je ein grünes Inkrement:

1. **AntD-Basis** — Provider, Theme-Tokens auf unsere Farben, Bootstrap raus (Hülle, Buttons,
   Karten, Formulare, Dialoge). Chat-Log bleibt unangetastet. Zieht **F5** (Optik Config) und
   **F9** (Clear-Layout) mit hoch.
2. **Markdown** — GFM + einfache Umbrüche als Zeilen ([R-WP-28/UC-WP-73](chat.md)); Fallback
   `react-markdown` + `remark-gfm`, weil `markstream` die Zusage nicht hält (CSS nie importiert,
   keine `breaks`-Option — gemessen 2026-11-06). Hebt C-2(b) (Reload ohne Markdown).
3. **Eingabe** — Auto-Grow, Send nur bei Text, Refocus nach dem Turn ([R-WP-41/UC-WP-74](chat.md)).
   Tastatur bleibt R-WP-30 (Enter = Umbruch) — siehe ⏳ unten.
4. **Timeline** — Werkzeuge und Delegationen als Einträge mit Agentenname, `loading` und
   Ankunftszeit; **Dauer nur je Turn** an der Antwort ([R-WP-40/UC-WP-72](chat.md)); kein `hr`
   zwischen Zeilen ([R-WP-29/UC-WP-55](chat.md), C-6). F3 (Agent-Arbeit unsichtbar) wird hier
   mitbehandelt: der Eintrag zeigt, wer arbeitet.
5. **Sider-Navigation + URL** — Projektbaum, Klick aufs Projekt öffnet seinen Chat, Einheiten
   Files (Paco später); URL auf den **klaren Projektnamen** (C-3, Namens-Kollision vorher prüfen).

* **⏳ Dunkel = eine Tabelle, App setzt `data-theme`** (2026-11-06, Review-Fund zu Inc 1,
  Rückversicherung ausstehend) — das Grundblatt schrieb Dunkel bisher zweimal (Media-Block für die
  Betriebssystem-Vorliebe, `[data-theme='dark']` für eine explizite Wahl, die niemand setzt); die
  Basis-Tokens lasen die ungenutzte Kopie → zwei Dunkellooks möglich, bei grüner Suite.
  [R-WP-39/UC-WP-71](ui.md) jetzt: die App setzt `data-theme` am Dokument auf die
  Betriebssystem-Vorliebe, die Tokens kommen aus **derselben** Tabelle, jedes Feld wird gegengeprüft.
  *Andersfall:* willst du einen **manuellen** Hell/Dunkel-Umschalter im Header, ist das eine neue Regel
  (Umschalter + Merkmal über Reload hinweg) — die Umsetzung bliebe kompatibel, nur die Queue der
  Entscheidung wäre eine andere.

* **⏳ „Jon muss weg" als Navigation LABEL verstanden** (2026-11-06, Paul per Zuruf, Rückversicherung
  ausstehend) — [R-WP-38](ui.md): es gibt **keinen „Jon"-Eintrag** und kein „Jon"-Label im Sider, ein
  Klick aufs Projekt öffnet dessen Chat. Die **Kachel** des Agenten behält ihren Namen
  ([R-WP-7](chat.md)) und die Docs nennen den Agenten weiter Jon. *Andersfall:* war gemeint, dass der
  Name auch auf der Kachel/ im Header verschwindet („Chat" statt „Jon"), ist das eine Umbenennung der
  Anzeige plus UC-Änderung in [chat.md](chat.md) — kein Umbau der Kachel-Regel selbst.
* **⏳ ⚠ Konflikt Tastatur:** Paul 2026-11-06 „die Referenz hat die korrekte Keyboard-Integration"
  (dort: **Enter sendet**, Shift+Enter = neue Zeile) gegen [R-WP-30](chat.md) (Paul 2026-11-02:
  **Enter = neue Zeile**, Cmd/Ctrl+Enter = senden). Ich habe R-WP-30 **nicht** angetastet und baue
  weiter danach. *Entscheidung nötig:* will Paul Enter = senden, ist das eine Bedeutungsänderung von
  R-WP-30 (plus `chat.composer`-Tests) — dann sage ich es dir vorher, weil Mehrzeile-Tippen damit
  seine Eingewöhnung verliert.
* **⏳ Screenshot-Werkzeug (Stagehand) parked** (2026-11-06) — `@browserbasehq/stagehand` v4 kann den
  Dev-Server öffnen und Screenshots/Layout-Messungen **ohne** LLM-Call (nur `page`-API); verlangt
  aber **Node ≥ 22.18**, der Bau fährt **22.12** (v23.4 ist vorhanden). Angenommen: kein neues
  Werkzeug, solange die F-Befunde unpräzise sind — Look-Abnahme bleibt Pauls Smoke-Test.
  *Andersfall:* eigenes Script unter Node 23.4, kein Vitest-/Maven-Eingriff.



## Runde 3 — Pauls Smoke-Test-Befunde (2026-11-03, Backlog)

Positiv bestätigt: **(1)** Chat-Restore geht. **(2)** Gemeinsam ein RAG-Chat gebaut
(`/Users/sterlp/dev/workset/ai-schulung`) — die Fabrik ist damit erstmals real produktiv gewesen.

Defekte/Fragen, je eine Story je Punkt erst nach Klärung (❓ = präzisieren, bevor geplant wird):

* **❓ F3 · Agent-Arbeit unsichtbar in den Status-Kacheln** — läuft ein Team-Mitglied, zeigt die Kachel
  oben keinen Fortschritt/Zustand. Welche Anzeige gewünscht ist (läuft-Status wie der Jon-Kachel?),
  klären, dann Regel + UC in [chat.md](chat.md).
* **❓ F5 · Optik im Detail** (u. a. Config-Seite) — Totalschaden-Einschätzung von Paul; Detail-Liste
  fehlt noch. Erst sammeln, dann eigenes Look-Inkrement.
* **❓ F8 · Config-Umfang fehlt** — `reasoning_effort` nicht einstellbar, Header-Einstellungen und
  weitere Plugin-Einstellungen fehlen. Gehört zu [config.md](config.md)/[R-WP-23](config.md) — Umfang
  mit dem Core-Bestand abgleichen, dann UCs ziehen. (Verwandt: ⏳ Dev-Slot oben.)
* **❓ F9 · „Clear" hinterlässt Tool-Zeilen mit zerstörtem Layout** — nach Clear bleiben Tool-Messages
  im Chat, Layout kaputt. Verhalten von Clear klären (was sollClear entfernen?) → Regel in
  [chat.md](chat.md).
* **❓ F10 · Bootstrap „docs laden einmal zu Beginn" greift nicht zuverlässig** — Fabrik-Jon fragte nach
  `docs/index.md`, statt es beim Start geladen zu haben. Bootstrap-/Prompt-Defekt der Fabrik-Agenten —
  prüfen, wo die Regel stehen muss (Agent-Prompt vs. Factory-Bootstrap).

## Runde 3 — Live-Befunde aus der Nutzung (2026-11-03, zweiter Schwung)

* **❓ C-1 · TurnClaim-Timeout** — `ERROR ProjectRuntime: turn ... never got the project back from a
  claimed action` — `TurnClaim.awaitNoAction` wirft nach 10 000 ms (`ProjectRuntime.java:348`).
  Aufgetreten beim Compact. Verwandt zur ⏳ TurnClaim-Lücke; Ursache im Code klären (Dev), bevor eine
  Regel steht.
* **❓ C-2 · Compact-Ergebnis im UI** — (a) live: nur „error" in der Agent-Karte, weder Ergebnis noch
  Fehlermeldung sichtbar; (b) nach Reload: Ergebnis da, aber **ohne Markdown** (nur Text). Regel in
  [chat.md](chat.md) fehlt: wie Compact live + im Verlauf erscheint.
* **❓ C-3 · URL mit Hash-Suffix** — `/ui/chat/spring-persistent-tasks-071d14a` statt
  `/ui/chat/spring-persistent-tasks`. Paul will den klaren Namen. Prüfen, ob die Registry-ID den Hash
  braucht (Namens-Kollision) — wenn Namen eindeutig sind, URL auf den Namen legen.
* **❓ C-7 · Agent-Chat ist ein anderes Widget** — der aufgeklappte Blick auf ein Team-Mitglied nutzt
  ein eigenes Chat-Widget: anderes Look&Feel, aktualisiert nicht während der Agent arbeitet, zieht
  alle Daten erneut (= Totalschaden). SOLL: dasselbe Chat-Widget wie der Haupt-Chat, an den
  Agent-Stream gehängt, **read-only** (keine Eingabe). Im Umfeld von [R-WP-26](chat.md).




## ⏳ selbst entschieden (2026-10-25/26) — bitte überfliegen

* **⏳ Dev-Modell/URL/Key werden gespeichert, aber vom Core nicht angewandt** (2026-11-02, aus dem
  7a/7b-Bau) — [R-WP-23](config.md) zeigt je Agent Modell, URL und Key; für den Slot `dev` liest der
  Core nur das Basismodell (`devAgentConfig()`) — gespeicherte Überschreibungen von Modell, URL und
  Key wirken nicht (der UI-Status `keySet` für Dev ist also nur Anzeige). Decision: Felder bleiben und
  speichern doc-konform (`agents/dev/AGENT.md`); kein UI-Hinweis, kein Core-Workaround. Dev-**Think**
  ist ausgenommen: es wirkt nicht nur, es ist im Core der Base-Default für alle anderen Agenten
  ([R-THINK-10](../../org.sterl.llmpeon.core/docs/per-agent-think.md)) — die Seite beschriftet das Feld
  im Dev-Slot deshalb `Think (Default)` ([R-WP-23/UC-WP-67](config.md)). *Andersfall:*
  sollen Devs Werte wirklich wirken, ist das eine Core-Story (Proposal mit WARUM) — dann UC in
  config.md nachziehen.
* **⏳ „Chat" öffnet das zuletzt aktive Projekt** (2026-11-02, meine Empfehlung, Rückversicherung
  ausstehend) — [R-WP-1](ui.md): Top-Menü „Chat" ohne aktives Projekt → Projekte; mit aktivem Projekt
  → dessen Chat. *Andersfall:* „Chat" führt immer auf die Projektliste (keine Abkürzung) oder es
  braucht eine Chat-Auswahl.
* **⏳ Provider-Wechsel setzt Modell/URL auf die Defaults des Providers** (2026-11-02) — wie der
  Provider-Dialog im Plugin ([R-WP-16/UC-WP-62](config.md)). *Andersfall:* Felder nur leeren statt
  Defaults einzutragen.
* **⏳ Die Fehlerzeile eines gescheiterten Turns liegt im Core-Memory** (2026-11-02, Runde 2, aus
  [R-WP-34/UC-WP-64](chat.md)) — die Fabrik schreibt bei einem Fehlschlag den Nutzer-Text und
  „`[Turn failed]: <Grund>`" in Jons Memory (`ThreadSafeMemory.add`, Core-API, kein Core-Eingriff):
  **kein** zweiter Speicher, und das Modell liest die Zeile im nächsten Turn (Verwandtschaft zu
  R-AEM-1). Eine in der Core-Queue wartende Eingabe ist bis zu ihrem Turnstart **nicht** persistiert —
  sie taucht im Verlauf erst mit diesem Turn auf. *Andersfall:* soll eine wartende Eingabe schon vor
  dem Turn reload-fest sichtbar sein, braucht es einen eigenen Speicher (widerspricht R-WP-11) oder
  einen Core-Mechanismus.

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

* **⏳ Bild-Support-Tools bleiben** (2026-11-02/03, Paul: „die tools sind von mir — drin lassen") — der
  Fremd-Commit `eeac8abb` „added picture support" ist Pauls Material auf `web-factory-mvp` (nur
  Plugin/Core-Pfade, `aifactory` unberührt). Die Fabrik kennt bisher keine Bild-Regel: Backend-Text-Parts
  erscheinen als `#picture: …`-Textzeile, Core-`ThreadSafeMemory` schreibt `AiMessage.from("ok")` je
  Follow-up. *Offen bis Pauls Smoke-Test:* wie Bild-Parts im Chat gezeigt werden sollen — dann als
  Regel + UC in chat.md, oder Verbatim bleibt gewollt und wird festgehalten.
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
