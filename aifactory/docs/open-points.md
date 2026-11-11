# Open Points — AI Factory

Status je Punkt: ❓ offen · ⏳ selbst entschieden (Rückversicherung steht aus) · 🔒 geklärt.
Durchgangslager: jeder Punkt wandert am Zyklusende in ein Feature-Doc oder entfällt.
Aufgeräumt 2026-11-10: die geschlossenen 🔒-Erzählberichte der Runden 3–6 sind entfallen — ihre
Entscheidungen stehen in den Feature-Docs, die gemessenen Fallstricke in [memory.md](memory.md).

## Runde 9 — B7b: Compact-Ende im Chat (2026-11-10)

* **🔒 Was schließt die 🗜-Zeile nach einem Compact** — gemessen: die 🗜-Zeile **öffnet** in der
  Oberfläche einen offenen Turn (`chat.page.tsx:285-300`), geschlossen nur durch ein Turn-Ende (`:328`);
  eine **Aktion** schuldet per [R-WP-48](chat.md) keins ⇒ der Spin bleibt, und der Verlaufs-Lauf lief
  unmittelbar nach der Zusage (`:533-534`), also **vor** der Aktion ⇒ Inhalt stale. **Paul
  2026-11-10:** „du brauchst kein eigenes signal — alles ist bereits im core … wenn Jon ein compact macht
  / clear und rebuild des chats anhand der history von jon, genau wie es im eclipse plugin passiert."
  ⇒ Regel [R-WP-63/UC-WP-100](chat.md): Neuaufbau auf der **Fallkante des Kachel-Zustands**, kein
  neues Ereignis, kein neues Feld; Compact **und** Clear.
* **⏳ Kern-eigener Compact löst keinen Neuaufbau aus** (Bewusste Grenze in [R-WP-63](chat.md),
  abgeleitet: ein Auto-/Vorlauf-Compact passiert **mitten im Turn**, ein Neuaufbau würde die laufenden
  Live-Zeilen des Turns über die gezeigten Zeilen legen — verboten nach UC-WP-100 GIVEN 3 und D-16).
  *Andersfall:* Neuaufbau auch dort = Anker-Mechanik des Turns nachziehen ([R-WP-47](chat.md)).
* **❓ Stop während eines Clear** (gemessen 2026-11-10, Nebenbefund bei B7b) — **Clear** färbt die
  Kachel `WORKING` (`ProjectRuntime.java:161-162`), und `WORKING` macht den Chat „beschäftigt"
  (`chat.page.tsx:425-426`) ⇒ es stehen Chat-Warten **und** Stop; der Stop läuft in eine `409`
  ([TurnClaim.java:65-67]) und wird verschluckt (`:504-507`). Vorbild Plugin: Clear ist während eines
  Laufs gar nicht erst möglich (`ActionsBarWidget.java:161-168`). Entscheidung Paul: Aktions-`WORKING`
  ebenfalls kein Stop-Ziel (Parität zu `COMPACTING`, D-17) **oder** die Aktion wie im Plugin sperren.
  Antwort ⇒ Schärfung in [chat.md](chat.md).
* **⏳ Die Zeilen eines gestoppten Turns überleben genau EINEN Aktions-Neuaufbau** (im Bau entschieden
  2026-11-11, abgeleitet aus [R-WP-11](chat.md): Live-only endet mit der nächsten Lesung — dieselbe
  Grenze wie beim Diff; UC-WP-50 sagt „schon gezeigte Zeilen bleiben" nur über den **Stop** selbst, nicht
  über alle künftigen Lesungen). Festgehalten in [R-WP-63](chat.md) und UC-WP-100 GIVEN 5, gebaut mit
  Mutationsbeweis. *Andersfall:* dauerhafte Rettung = eigener Marker je Live-Zeile („diese Zeile ist nie
  ersetzbar") — widerspricht R-WP-11 und braucht einen zweiten Speicher.
* **⏳ Nach dem Compact steht die Context-Zusammenfassung als eigene Eingabe im Chat** — der Kern
  re-seedet genau eine Nutzer-Nachricht (`Session compacted:` + Summary, `AbstractAgent.java:601-605`)
  und **beide** Consumer malen sie als Nutzer-Zeile: Plugin über `ToSimpleMessage.java:24-29` (kein
  Filter auf den Marker), Fabrik über `ConversationRestore.java:57-61`. Parität ⇒ **lassen**.
  *Andersfall:* eigene neutrale Zeile = Darstellungsregel in [chat.md](chat.md), kein Core-Eingriff.


## Runde 7 — Pauls Browser-Befunde zum API-Fehler-Stand (2026-11-09, „nach dem Release fixen")

Pauls Wortlaut, je Punkt seine Entscheidung (alle zur Look-Story, kein Teil des api-errors-Zyklus):

* **❓ Kachel-Status wirkt verzögert (Paul 2026-11-10, Browser)** — **gebaut 2026-11-10** als
  [R-WP-60/UC-WP-97](chat.md) (Push statt Poll, `e68f1710`): jede Fabrik-seitige Zustandsänderung
  published einen Roster. **Abnahme am Browser steht aus**; bewusste Grenze bleibt: Kern-Flags ohne
  Rückmeldung (z. B. Auto-Compact-Anlauf) erscheinen erst mit der nächsten Zeile — ein Kern-Hook wäre
  ein Core-Inkrement.
* **❓ Reasoning-Effort-Feld im Browser (Bug-Liste B0, 2026-11-10)** — Paul: „Drop Down funktioniert
  reasonsing effort". Diagnose (Dev, antd-CLI-belegt): unser Think-`AutoComplete` hat **keinen Pfeil**
  (AutoComplete setzt `suffix=null` → kein Chevron) und rechts sitzt der **Clear**-Knopf — Klick dort
  löscht den Wert, ohne die Liste zu öffnen; im jsdom unsichtbar. Zweitverdacht: alter Bundle im
  Browser (Pre-L4-`Select` sichtbar). **Frage an Paul (DevTools, < 1 min):** (1) war die UI frisch
  gebaut/Server neu gestartet? (2) öffnet Klick in die **linke** Feldhälfte die Liste und Klick rechts
  löscht? (3) existiert nach dem Klick `.ant-select-dropdown` im DOM? Entscheidung danach: Affordance
  (Pfeil-Suffix am AutoComplete) bauen oder Bundle-Thema.
* **⏳ Clear am Chat-Eingabe-Bereich fehlt (nur Icon)** — **gebaut 2026-11-10** als Icon-Button-Widget
  ([R-WP-59/UC-WP-96](ui.md)); Abnahme am Browser steht aus.
* **⏳ Compact am Chat-Eingabe-Bereich fehlt (nur Icon)** — **gebaut 2026-11-10**, dito
  ([R-WP-59/UC-WP-96](ui.md)).
* **⏳ Send nur als Icon** — **gebaut 2026-11-10**, dito ([R-WP-59/UC-WP-96](ui.md)).

## Runde 8 — Nachwehen des Bug-Zyklus B6/B7a (2026-11-10)

* **❓ Ein Turn-Fehler wischt eine stehende Seiten-Ablehnung** (`chat.page.tsx:351`, gemessen im
  B7a-Review) — steht das Error-Panel (z. B. 409 auf ein Compact) und schlägt später ein **Turn**
  fehl, verschwindet das Panel. [R-WP-62](api-errors.md) sagt: das Panel schließt eine **neue
  Server-Interaktion** — ein Turn-Fehler ist aber keine Interaktion, die der Nutzer gerade gestartet
  hat. Beides denkbar: (a) Turn-Fehler lässt das Panel stehen (nur eigene Interaktion schließt),
  (b) jede Server-Nachricht schließt. **Frage an Paul**, dann Regel-Satz in
  [api-errors.md](api-errors.md).
* **❓ `factory-ui/src/styles.css` 1286 Zeilen** — über der 1000er-Grenze (`AGENTS.md`: „a file over
  1000 lines is a bug and blocks acceptance until split"); schon vor dem Bug-Zyklus 1273, der Zyklus
  hat nur drei flache Regeln ergänzt und einen Block entfernt. Entscheidung Paul: **Aufteilen als
  eigenes Inkrement** (Empfehlung: nach Familien — Shell · Chat · Formulare · Trace) **oder** die
  Grenze für Stylesheets schriftlich aussetzen (dann als Satz in `AGENTS.md`).

## Runde 7 — Look-Story-Nebenbefunde (2026-11-10)

* **❓ UI-Testbestand flackert unter Last** (bisectiert am L2-Stand: flackert auch auf der Baseline
  `7fa3f429`, je Lauf 1–7 rot — `findBy`-Frist 1 s, Einzelfälle bis 4,5 s, 29 Worker) — keine Erwartung
  entschärft, nichts umgeschrieben. Eigenes Mini-Inkrement „Wartefristen/Worker messen" später; langsame/
  flatternde Suite ist nach AGENTS.md ein Defekt, kein Zustand.

## Runde 6 — Rest (2026-11-08/09)

* **⏳ Agent-Status „weiter defekt", Spinner fehlt** (Paul 2026-11-08, „es funktioniert wieder") —
  gegen [R-WP-45/UC-WP-79](chat.md) geprüft und im Runde-6-Delta repariert (`Spin.dotSize = 16`,
  Idle-/Fehler-Kreis 1 rem); gegen einen **echten Turn** nicht gemessen (M3 offen) — Abnahme am
  Browser mit Paul.
* **⏳ Sider-Rückkehr nach Fenster-Resize (M2b, gemessen 2026-11-08, nicht gebaut)** — faltet der
  Nutzer den Sider per Griff und zieht das Fenster unter den Breakpoint und zurück, **öffnet AntD den
  Sider wieder** (der `'responsive'`-Schreibvorgang nimmt die Nutzer-Wahl zurück; Chrome-headless-
  Messung). Annahme wäre: nach der ersten Griff-Betätigung ignoriert die App `'responsive'`-**Auf**-
  Writes, das Auto-Zu unter dem Breakpoint ([R-WP-44](ui.md)) bleibt. Nicht gebaut — kein Doc-SOLL,
  kollisionsgefährdet mit der Auto-Zu-Regel; entscheidet Paul.

## Backlog — Smoke-Test-/Nutzungs-Befunde (2026-11-02/03)

* **❓ F5 · Optik im Detail** (u. a. Config-Seite) — Totalschaden-Einschätzung von Paul; Detail-Liste
  fehlt noch. Erst sammeln, dann eigenes Look-Inkrement.
* **❓ F9 · „Clear" hinterlässt Tool-Zeilen mit zerstörtem Layout** — nach Clear bleiben Tool-Messages
  im Chat, Layout kaputt. Verhalten von Clear klären (was soll Clear entfernen?) → Regel in
  [chat.md](chat.md).
* **❓ F10 · Bootstrap „docs laden einmal zu Beginn" greift nicht zuverlässig** — Fabrik-Jon fragte nach
  `docs/index.md`, statt es beim Start geladen zu haben. Bootstrap-/Prompt-Defekt der Fabrik-Agenten —
  prüfen, wo die Regel stehen muss (Agent-Prompt vs. Factory-Bootstrap).
* **❓ C-1 · TurnClaim-Timeout** — `ERROR ProjectRuntime: turn ... never got the project back from a
  claimed action` — `TurnClaim.awaitNoAction` wirft nach 10 000 ms (`ProjectRuntime.java:348`).
  Aufgetreten beim Compact. Ursache im Code klären (Dev), bevor eine Regel steht.
* **❓ C-2a · Compact-Ergebnis live** — in der Agent-Karte erscheint nur „error", weder Ergebnis noch
  Fehlermeldung sichtbar; Regel in [chat.md](chat.md) fehlt (wie Compact live erscheint).
  *(„nach Reload ohne Markdown" ist erledigt, 2026-11-06: [R-WP-28/UC-WP-73](chat.md).)*
* **⏳ F8-Rückversicherung an Paul:** (1) ist der Zuschnitt „alles was das Plugin hat, minus
  Eclipse-Eigenes" ok? (2) Annahmen aus dem Bau: Extra-Body ohne JSON-Objekt ⇒ 400 mit Feldnamen;
  `tools`-Wortlaut wird nie von der Fabrik interpretiert (nacktes `!` gespeichert, deutet der Core).
* **⏳ „Spring default error view" gemessen, Entscheidung am Advice** (2026-11-09, Pauls explizite
  Bitte „gerne mal messen"): ohne eigenen `@RestControllerAdvice` antwortet die nicht gemappte
  `/api/`-Kante mit Springs Body — `{"timestamp","status","error","path"}`, **ohne** `message`,
  **ohne** `fieldErrors`. Unsere Fehlerform (`ApiError` + `fieldErrors`) trägt nur der eigene
  Advice ⇒ **Empfehlung: Advice bleibt.** Pauls Rückversicherung steht aus.
* **⏳ A7-Rest: das bewusst nicht reparierte Race „Turn verankert während eines Reads"** (Klick-Flanke
  zwischen Read und Mount, kein beobachteter Befund) — falls Paul einen sieht, eigene Story; nur der
  wortgleiche Live-Fortsetzungstext/Diff bliebe Core-Änderung — nicht Teil von R-WP-47.

## Backlog — kleineres (aus früheren Runden)

* **⏳ Prio-Zeile ist nur eine vorläufige Zeile** (2026-11-06, gebaut als Annahme in
  [UC-WP-75](chat.md)) — die `!`-Zeile ist die vorläufige Zeile des Absenders (von der nächsten
  bestätigten Nutzerzeile geschluckt), weil der Core eine Prio-Nachricht nie in die Memory schreibt.
  *Andersfall:* dauerhafte Prio-Spur = zweiter Speicher ([R-WP-11](chat.md]) — eigenes Inkrement.
* **⏳ Screenshot-Werkzeug (Stagehand) parked** — kann Layout-Messungen ohne LLM-Call, verlangt aber
  Node ≥ 22.18, der Bau fährt 22.12. *Andersfall:* eigenes Script unter Node 23.4, kein
  Vitest-/Maven-Eingriff.
* **⏳ Bild-Support-Tools bleiben** (2026-11-02/03, Paul: „die tools sind von mir — drin lassen") —
  Pauls Fremd-Commit, nur Plugin/Core-Pfade. *Offen bis Pauls Smoke-Test:* wie Bild-Parts im Chat
  gezeigt werden sollen — dann als Regel + UC in chat.md, oder Verbatim bleibt gewollt.
* **❓ Core: der Abbruch verliert eine bereits gepollte Queue-Eingabe** (2026-11-01, Inc 5d gemessen) —
  [UC-WP-50](chat.md) bleibt deshalb ❌; Fix = Core-Story (zuletzt gepollten Eintrag beim Cancel
  zurücklegen), danach UC-WP-50 ✅.

## ⏳ selbst entschieden (2026-10-25/26) — bitte überfliegen

* **⏳ Dev-Modell/URL/Key werden gespeichert, aber vom Core nicht angewandt** (2026-11-02) —
  [R-WP-23](config.md): für den Slot `dev` liest der Core nur das Basismodell; Felder bleiben und
  speichern doc-konform. Dev-**Think** wirkt (Base-Default, [R-THINK-10](../../org.sterl.llmpeon.core/docs/per-agent-think.md)),
  Feld heißt deshalb `Think (Default)`. *Andersfall:* Core-Story (Proposal mit WARUM).
* **⏳ „Chat" öffnet das zuletzt aktive Projekt** (2026-11-02) — [R-WP-1](ui.md): ohne aktives Projekt
  → Projekte, sonst dessen Chat. *Andersfall:* „Chat" führt immer auf die Projektliste.
* **⏳ Provider-Wechsel setzt Modell/URL auf die Defaults des Providers** (2026-11-02) — wie der
  Provider-Dialog im Plugin ([R-WP-16/UC-WP-62](config.md)). *Andersfall:* Felder nur leeren.
* **⏳ Die Fehlerzeile eines gescheiterten Turns liegt im Core-Memory** (2026-11-02) —
  [R-WP-34/UC-WP-64](chat.md), kein zweiter Speicher. Eine in der Queue wartende Eingabe ist bis zum
  Turnstart nicht persistiert. *Andersfall:* eigener Speicher (widerspricht R-WP-11).
* **⏳ Fehler-Ergebnis einer Delegation erscheint wörtlich als Mitglieder-Zeile** (2026-10-30) —
  so gebaut („wie es ist", AF-ADR-002), Parsen/Kürzen verboten. *Andersfall:* neuer Core-Mechanismus.
* **⏳ UC-WP-9 klargestellt: die sichtbare Ablehnung ist immer Jons** (2026-10-26) —
  [UC-WP-9](files.md); Mitglieds-Ablehnung endet beim Modell ([R-WP-4](chat.md)). *Andersfall:*
  Ausnahme zu R-WP-4, eigene Regel.
* **⏳ Der Urheber-Name gehört zur dauerhaften Zeile, nicht zum Live-Fragment** ([UC-WP-39](chat.md)) —
  live nur Jon, kein Label nötig; **Ausnahme:** Timeline-Schritte nennen ihren Core-Stempel
  ([R-WP-40](chat.md)). *Andersfall:* Label am Live-Fragment = eine UI-Zeile, dann explizit ins Doc.

## ⏳ Config-/Projekt-Annahmen (Runde 1/2, 2026-10-25/26) — bitte überfliegen

* **⏳ Config-Quelle ist eine eigene Datei der Factory** (`~/.aifactory/config/config.properties`,
  Keys auch aus Env) — [R-WP-9](config.md), Begründung
  [Architektur → Konflikt](aifactory-architektur.md#konflikt-dokumentiert-2026-10-25). *Andersfall:*
  Plugin-Bestand lesen bräuchte einen Core-Port des Config-Stores.
* **⏳ Runtime-Zustand niemals im Checkout** — nur `stories/` und `proposals/` dort erlaubt
  ([Architektur-Invariante](aifactory-architektur.md#invarianten)).
* **⏳ Doppelte URL: Doc gewinnt gegen Code** — [R-WP-2/UC-WP-3](project.md): `200` mit dem
  bestehenden Projekt. *Andersfall:* `400` als Feedback ⇒ UC-WP-3 umformulieren.
* **⏳ „Bereits vorhanden" ist `200`, nicht `201`** — abgeleitet: es entsteht nichts Neues.
* **⏳ Gleicher Ordnername + andere URL ⇒ abgelehnt** ([R-WP-18/UC-WP-26](project.md)) —
  *Andersfall:* zweiter Name statt Fehler ⇒ Registry und Ordnername entkoppeln.
* **⏳ Vorhandener Nicht-Git-Ordner wird nie gefüllt oder überschrieben**
  ([R-WP-18/UC-WP-27](project.md)).
* **⏳ v1 klonen wir nur über `https://`** ([R-WP-20/UC-WP-33](project.md)) — ssh verlangt Schlüssel/
  Passphrase, beides verboten ([R-WP-21](project.md), nie interaktiv). *Andersfall:* private Repos
  per ssh = eigene Story mit Secret-Ablage.
* **⏳ Die Registry-Datei ist keine Löschfreigabe** ([R-WP-19/UC-WP-29](project.md)).
* **⏳ `aifactory.home` / Env `AIFACTORY_HOME` ist Deployment, keine Nutzer-Einstellung**
  ([R-WP-5](chat.md)) — kein Feld in den Einstellungen, aber nötig für Temp-Home-Tests.
* **⏳ Klonen synchron, Frist 300 s, ohne Fortschritt im UI** — große Repos später: Hintergrund-Klon +
  Status in der Liste.
* **⏳ Shell läuft ohne Rückfrage** — bewusst im MVP
  ([Architektur-Invariante](aifactory-architektur.md#invarianten)); vor externem Zugriff ist eine
  Zusage oder eine Sandbox nötig.
* **⏳ Auslieferung ist Teil des Inkrements, nicht des Deployments** —
  [R-WP-22/UC-WP-34/35](ui.md) mit echten HTTP-Tests.
* **⏳ Entfernen lässt den Chat-Bestand liegen** — `state/<id>` bleibt (Identität ist die URL);
  *Andersfall:* „Tabula rasa" ⇒ mitlöschen, mit Rückfrage.
* **⏳ Wiederhergestellte Verlaufszeilen tragen keine Zeit** — Core-History kennt keinen Zeitstempel;
  eigene Zeit-Datei = zweiter Speicher ([R-WP-11](chat.md)). *Andersfall:* Core-Story.
* **⏳ Key wird nicht auszugsweise angezeigt** ([R-WP-16](config.md)) — kein Zeichen nach draußen.
  *Andersfall:* letzte 4 Zeichen = Satzänderung.
* **⏳ Datei schlägt Umgebungsvariable** ([R-WP-16/UC-WP-38](config.md)) — Variable = Startwert,
  Datei = gepflegter Stand. *Andersfall:* Deployment-Diktat ⇒ Reihenfolge drehen, Feld sperren.
* **⏳ Key über die UI entfernen** (2026-11-02) — R-WP-16 geschärft: „Remove key" schreibt die Datei,
  ENV-Quelle wird nur gemeldet.
* **⏳ kein globales Temperatur-Feld** ([R-WP-16](config.md)) — Temperatur nur je Agent.
  *Andersfall:* eigener View-Bereich + Core-Schreibpfad.
* **⏳ Key-pflichtige Provider** ([UC-WP-20](config.md)) — alle außer Ollama und LM Studio.
  *Andersfall:* GitHub-Provider mit Token in die Liste.
* **⏳ Provider-Liste steht doppelt (UI + Server)** — Server validiert gegen `AiProvider` (9 Namen),
  UI führt dieselben (`factory-ui/src/api/settings.ts`). *Andersfall:* Liste über die API =
  breiteres `SettingsDto`; dann eigene Regel „ein Provider-Katalog je Instanz".
* **⏳ MVP ohne Plan-Werkzeuge, ohne memory\*-Werkzeuge, ohne MCP** — der MVP-Jon schreibt Pläne mit
  den Disk-Tools nach `peon-plan/overview.md`.
* **⏳ Nach einem Neustart ist nur Jon wieder da, sein Team beginnt leer** — [R-WP-11](chat.md) +
  globalem ADR-0024. *Andersfall:* Team-Historie über Restart = Core-Eingriff.
* **⏳ „Workspace" ist für die Factory das geklonte Projekt** — [R-WP-3](project.md)/[R-WP-6](files.md).
* **⏳ Paco darf `docs/` lesen** und antworten, schreiben nur in die eigenen Vorschlags-Ordner
  ([Paco R-PC-2](paco.md)).

## ❓ Vision-Diskussion (2026-10-27) — eine Frage nach der anderen

* **❓ V6-Folge:** Im MVP gibt es keine Nutzer (Paul): alle teilen `user/` und — offen — wohl auch ein
  Paco-Gespräch; „privat je Nutzer" (R-PC-1, UC-PC-2) gilt erst mit Login. R-PC-1 entsprechend schärfen.
* **❓ V5-Rest (Config-Umfang):** MCP, Codegraph an/aus (Default an beim Checkout), Config-Verzeichnis
  git-sicherbar, Skills für alle Projekte geteilt. 🔒 V5a/b/c sind in [R-WP-23](config.md).
* **❓ V7 TMP-Hinweis:** Agenten sollen `tmp/<projekt-name>` anlegen ([R-WP-13](files.md) pinnt den
  System-TMP) — braucht eine Prompt-Zeile (Regel fehlt).
* **❓ V8 Entfernen:** Vision „Verzeichnis + Config weg". Heute bleibt `state/<id>` liegen (⏳ oben).
* **❓ V9 Produktname:** „Open AI Fabric (OAF)" vs. „AI Factory" in allen Docs/Code — umbenennen?
* **🚧 V11 Config-Repo (Paul 2026-10-28):** Checkout eines Config-Git-Repos beim Start; Details und
  Kollisionen in [config-repo.md](config-repo.md).
* **❓ Toter Guard (2026-10-28):** `ProcessCommandRunner` setzt `BatchMode=yes` als
  Umgebungsvariable — ssh liest das nicht (nur via `GIT_SSH_COMMAND`). Heute folgenlos (SSH-URLs
  abgelehnt), wird mit SSH ([R-CR-7](config-repo.md)) zum Hänger — Fix gehört in dessen Bau.
* **❓ Rollen und Rechte** (Vision: Admin/User/Reader) — der MVP hat keinen Login ([R-WP-17](user.md)),
  jeder darf alles. Empfehlung: Rollen **nicht** im MVP, eigene Story mit OAuth2; bis dahin
  vertrauenswürdiger Kreis.
* **❓ Story-Import** — wer holt `stories/` und `proposals/` in die Feature-Docs und wann?
  ([Paco, Offene Punkte](paco.md)).
