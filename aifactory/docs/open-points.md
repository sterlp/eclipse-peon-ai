# Open Points — AI Factory

Status je Punkt: ❓ offen · ⏳ selbst entschieden (Rückversicherung steht aus) · 🔒 geklärt.
Durchgangslager: jeder Punkt wandert am Zyklusende in ein Feature-Doc oder entfällt.
Aufgeräumt 2026-11-10: die geschlossenen 🔒-Erzählberichte der Runden 3–6 sind entfallen — ihre
Entscheidungen stehen in den Feature-Docs, die gemessenen Fallstricke in [memory.md](memory.md).

## Runde 7 — Pauls Browser-Befunde zum API-Fehler-Stand (2026-11-09, „nach dem Release fixen")

Pauls Wortlaut, je Punkt seine Entscheidung (alle zur Look-Story, kein Teil des api-errors-Zyklus):

* **⏳ Überschriften im Main-Panel raus** — „die Überschriften kleben open, können raus im main panel —
  wir haben es ja im Top-Menü." Zielt auf die Seiten-Überschriften (Projekte/Config/…) — genau
  welche Flächen, am Browser abnehmen.
* **⏳ „Chats" wieder ins Top-Menü** — Damit kehrt der Runde-6-Beschluss („Chat muss weg", 🔒 in
  R-WP-43) **teilweise zurück**: Top-Menü wieder **Projekte · Chat · Settings**; der Sider bleibt,
  ist aber **nur bei Chats sichtbar**. Widerspricht R-WP-43 — Regel wird in der Look-Story
  umgeschrieben, nicht still.
* **⏳ Reasoning-Effort-Feld ist ein Select ohne eigene Eingabe** — „ich kann nichts selber eingeben"
  (F8, R-WP-51). Annahme: wie das Modell-Feld ein `AutoComplete` (Auswahl **oder** Eingabe) — am
  Plugin nachlesen, wie Paul es dort entschieden hat, bevor gebaut wird.
* **❓ Kacheln der Agenten oben im Chat sind weg (Paul: „most critical … damit wären wir defekt")** —
  die Kachel-Reihe über dem Chat fehlt in seinem Blick; entweder Regression (C-7-Kanal, Kacheln-Read
  nach `onOpen`) oder Look. Erst messen (Welcher Commit? Reproduktion), dann Regel — kein Blindfix.
* **⏳ Clear am Chat-Eingabe-Bereich fehlt (nur Icon)** — Clear/Compact der Kachel-Reihe
  ([R-WP-43](ui.md)) sind weg oder nicht sichtbar; „nur icon" heißt: Aktionen müssen als Icons an der
  Eingabe sitzen, keine Text-Buttons.
* **⏳ Compact am Chat-Eingabe-Bereich fehlt (nur Icon)** — dasselbe für Compact.
* **⏳ Send nur als Icon** — der Send-Knopf soll ein reines Icon sein, kein Text-Knopf.

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
