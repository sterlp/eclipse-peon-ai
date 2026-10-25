# Open Points — AI Factory

Status: ❓ offen · ⏳ selbst entschieden (Rückversicherung steht aus) · 🔒 geklärt.
Durchgangslager: jeder Punkt wandert am Zyklusende in ein Feature-Doc oder entfällt.

## ⏳ im Nachtzyklus entschieden (2026-10-25) — bitte überfliegen

* **⏳ Config-Quelle ist eine eigene Datei der Factory** (`~/.aifactory/config.properties`, Keys auch
  aus Env) — die Annahme "wir lesen den Bestand aus `~/.peon`" war **falsch**: der Plugin-Bestand liegt
  in Eclipse-Präferenzen, `~/.peon` enthält nur Skills/Agents/Commands. Decision:
  [Web-Prototype R-WP-9](web-prototype.md), Begründung
  [Architektur → Konflikt](aifactory-architektur.md#konflikt-dokumentiert-2026-10-25).
  *Andersfall:* wenn der Plugin-Bestand doch gelesen werden soll, bräuchte es einen Core-Port des
  Config-Stores auf Dateien — plus die Frage, wessen Werte dann gewinnen.
* **⏳ Factory-Home = `~/.aifactory`** — `projects.json` (Registry), `projects/<name>` (Clones),
  `state/<project>/` (Agent-History), `config.properties`. Würde der User die Daten im Workspace oder
  in einer DB haben wollen, sind Registry und History-Pfade neu zu setzen.
* **⏳ Runtime-Zustand niemals im Checkout** — nur `stories/` ist dort erlaubt
  ([Architektur-Invariante](aifactory-architektur.md#invarianten)); Grund: `git status` des Nutzers
  darf nicht vermüllt werden.
* **⏳ Doppelte URL: Doc gewinnt gegen Code** — Inc 1 baute `400 "already present"`,
  [R-WP-2/UC-WP-3](web-prototype.md) verlangt „kein zweiter Clone, keine Fehlermeldung". Decision:
  API antwortet `200` mit dem bestehenden Projekt, UI zeigt es nur an. *Andersfall:* willst du die
  Meldung als Feedback, wird UC-WP-3 umformuliert und der `400` bleibt — Aufwand dann: Doc + 3 Tests
  rückabwickeln.
* **⏳ „Bereits vorhanden" ist `200`, nicht `201`** — abgeleitet: es entsteht nichts Neues, also kein
  Created. Die Regel nennt bewusst keinen HTTP-Code.
* **⏳ Gleicher Ordnername + andere URL ⇒ abgelehnt**
  ([R-WP-18/UC-WP-26](web-prototype.md)) — abgeleitet aus dem Ziel `<home>/projects/<name>`: zwei Repos
  können sich nicht einen Ordner teilen. *Andersfall:* zweiter Name (`demo-2`) statt Fehler → Registry
  und Ordnername entkoppeln.
* **⏳ Vorhandener Nicht-Git-Ordner wird nie gefüllt oder überschrieben**
  ([R-WP-18/UC-WP-27](web-prototype.md)) — Ableitung: die Factory gehört dieser Ordner nicht.
* **⏳ v1 klonen wir nur über `https://`** — ssh-URLs (`git@host:org/repo.git`) tragen zwar kein
  Passwort, setzen aber Schlüssel oder Passphrase-Prompt voraus; beides verbietet
  [R-WP-21](web-prototype.md) (nie interaktiv). Deshalb abgelehnt statt halb funktionieren
  ([R-WP-20/UC-WP-33](web-prototype.md)). *Andersfall:* private Repos per ssh wären eine eigene Story
  mit Secret-Ablage und bekannten Hosts — dann diesen Satz wieder raus.
* **⏳ Die Registry-Datei ist keine Löschfreigabe**
  ([R-WP-19/UC-WP-29](web-prototype.md)) — entfernt wird nur unter `<home>/projects`; ein von Hand
  eingetragener absoluter Pfad (`/`, `/Users/…`) darf beim Entfernen nichts löschen.
* **⏳ `aifactory.home` / Env `AIFACTORY_HOME` ist Deployment, keine Nutzer-Einstellung** —
  [R-WP-5](web-prototype.md) sagt „der Nutzer kann den Ort nicht ändern": gemeint ist kein Feld in den
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
  [R-WP-22/UC-WP-34/UC-WP-35](web-prototype.md) mit echten HTTP-Tests auf einem zufälligen Port.
  Anlass: seit einem Hand-Commit (`8c7dec24`, `@Controller` auf `SpaForwardController`) beantwortete
  dieser Mapping-Pfad **jeden** `/ui/**`-Request — Assets wurden nie ausgeliefert, `/ui/` endete in
  ~6 000 Weiterleitungen und einem `StackOverflowError` pro Aufruf. Die API war grün, die App tot.
* **⏳ Kein SPA-Routing im MVP** — die Views sind Tabs, es gibt keine Adressen pro View. `/ui/` liefert
  `index.html`, Assets laufen über den Resource-Handler; eine Weiterleitungs-„History-Fallback"-
  Maschine braucht es erst, wenn der Router kommt. *Andersfall:* mit Router wird eine Regel für
  unbekannte Pfade nötig (die dann **nie** vom Asset-Pfad geschluckt werden darf).
* **⏳ Jon Light darf `docs/` lesen** und antworten, schreiben nur `stories/*`
  ([Jon Light R-JL-2](jon-light-stories.md)).
* **⏳ MVP ohne Plan-Werkzeuge, ohne memory\*-Werkzeuge, ohne MCP** — die drei sind Plugin-only bzw.
  ein Core-Port und werden eigene Stories; der MVP-Jon schreibt Pläne mit den Disk-Tools nach
  `peon-plan/overview.md`.
* **⏳ Nach einem Neustart ist nur Jon wieder da, sein Team beginnt leer** — abgeleitet aus
  „sonst wie beim Eclipse-Plugin" + globalem ADR-0024 (Sklaven sind RAM-only)
  ([Web-Prototype R-WP-11](web-prototype.md)). Team-Historie über den Restart wäre eine neue Regel
  und ein Core-Eingriff.
* **⏳ „Workspace" ist für die Factory das geklonte Projekt** — kein Eclipse-Workspace-Begriff,
  Arbeitsordner ist immer das Projekt-Root
  ([Web-Prototype R-WP-3/R-WP-6](web-prototype.md)).
* **⏳ Key wird nicht auszugsweise angezeigt** ([R-WP-16](web-prototype.md)) — angenommen: „maskiert"
  heißt **kein Zeichen** nach draußen, gezeigt wird nur `gesetzt`/`fehlt` und die Quelle. Abgeleitet aus
  R-WP-20 (Zugangsdaten werden niemandem zurückgegeben) und dem fehlenden Login (R-WP-17). *Andersfall:*
  willst du die letzten 4 Zeichen zur Unterscheidung mehrerer Keys, ist das eine Satzänderung — ein
  Auszug bleibt ein Auszug.
* **⏳ Datei schlägt Umgebungsvariable** ([R-WP-16/UC-WP-38](web-prototype.md)) — die Variable ist der
  Startwert für den ersten Start, die Datei der gepflegte Stand (sonst könnte die UI nie etwas ändern).
  *Andersfall:* Deployment-Diktat („was der Operator setzt, gilt") ⇒ Reihenfolge umdrehen und das Feld
  im View sperren.
* **⏳ Leeres Feld löscht nichts, Key löschen geht im MVP nicht** ([R-WP-16](web-prototype.md)) —
  abgeleitet: ein leeres Formularfeld ist kein Löschbefehl, und ein versehentlich gelöschter Key wäre
  für alle Nutzer weg. *Andersfall:* eigener „Key löschen"-Knopf mit Rückfrage.
* **⏳ kein globales Temperatur-Feld** ([R-WP-16](web-prototype.md)) — im geteilten Bestand existiert
  Temperatur **nur je Agent**; ein globales Feld wäre erfunden. Der View zeigt, was es gibt.
  *Andersfall:* „je Agent" mit ins MVP ⇒ eigener View-Bereich plus Core-Schreibpfad pro Agent.
* **⏳ Key-pflichtige Provider** ([UC-WP-20](web-prototype.md)) — alle außer Ollama und LM Studio
  brauchen einen Key; ohne Key lehnt die Factory, bevor ein Modell gebaut wird. Abgeleitet aus dem
  Bestand des Cores (nur diese beiden ersetzen den Key durch einen Platzhalter). *Andersfall:*
  GitHub-Provider brauchen in der Praxis auch einen Token — dann mit in die Liste.

## ❓ offen

* **❓ Wer antwortet im Chat — Name ja oder nein?** (2026-10-25) — [Pauls Produktvision](../pauls-product-vision.md)
  will: im Verlauf sind **Jons Nachrichten** und die **finalen Antworten der Team-Mitglieder** zu sehen,
  und man sieht **wer** antwortet. Das widerspricht der bisherigen Annahme „Nachrichten tragen im MVP
  keinen Autorennamen". Empfehlung: **Agent-Name pro Block ist MVP** (der Monitor des Cores trägt ihn
  schon, ohne ihn sind Jon und sein Team im geteilten Chat nicht zu unterscheiden), der **Name des
  Menschen** bleibt offen bis zum Login (R-WP-17). *Andersfall:* ohne Agent-Namen braucht der Chat eine
  andere Markierung, um Team-Antworten zu erkennen.
* **❓ Rollen und Rechte** (Vision: Admin = Config/Keys, User = alles außer Config, Reader = nur lesen +
  „Read Jon") — der MVP hat keinen Login (R-WP-17) und kann deshalb nicht unterscheiden: jeder darf
  alles. Empfehlung: Rollen **nicht** im MVP, sondern als eigene Story mit OAuth2 (Vision sagt dasselbe);
  bis dahin ist der Betrieb ein vertrauenswürdiger Kreis. *Andersfall:* schon im MVP ein Admin-Passwort
  = halbherzige Authentifizierung, die ich nicht bauen würde.
* **❓ Kachel → Chat-Slide-out je Team-Mitglied** (Vision: Klick auf eine Agenten-Kachel öffnet rechts
  den Chat dieses Agenten, **nur lesbar**) — [R-WP-7](web-prototype.md) kennt die Kachel mit Kontext,
  Status, Clear, Compact, aber keinen Slide-out. Empfehlung: Inkrement 5 baut die Kacheln wie spezifiziert,
  der Slide-out folgt direkt danach als eigenes Inkrement (er braucht die History-Zugriffe je Agent).
* **❓ Projekt, das schon ausgecheckt ist** (2026-10-25, vom User angestoßen) — „1 Projekt = 1 Git-URL,
  das kann auch schon ausgecheckt sein". Gemeint sein kann (a) *von der Factory* geklont → wiederverwenden
  (ist gebaut: UC-WP-3) oder (b) ein **Checkout, der schon woanders auf der Platte liegt** (z. B. seine
  eigene Arbeitskopie) → dann bräuchte es einen Weg, diesen Ordner anzugeben, und
  [R-WP-2](web-prototype.md) („kein Import eines bestehenden Verzeichnisses") müsste weichen.
  **Nichts daran ist entschieden; Inc 2 wird dadurch nicht blockiert.** Folge bei (b): Identität bleibt
  die URL, und `Entfernen` dürfte einen fremden Checkout **niemals** löschen (R-WP-19 gilt nur für
  Clone der Factory).
* **❓ „jonkolo / Openclaw" (2026-10-25 17:11)** — eine hereingekommene Nachricht nannte
  `/Users/sterlp/sideProjects/jonkolo` („Openclaw JS, fast alles da, nur die Tools fehlen"); der Pfad
  existiert nicht und der User kennt den Namen nicht. Quelle der Nachricht ungeklärt → **Punkt liegt,
  nichts wurde gebaut.** Naheliegender Kandidat, falls doch gemeint:
  `/Users/sterlp/dev/workset/opencode` (OpenCode-Monorepo, JS/TS, ~30 Pakete).
* **❓ Authentifizierung** — der Prototyp kennt nur einen Nutzernamen (R-WP-17). Ab wann echte
  Anmeldung nötig wird (externer Zugriff?), ist eine Produktfrage, keine Prototyp-Frage.
* **❓ Story-Import** — wer holt `stories/*.md` in die Feature-Docs und wann? Bewusst außerhalb des
  Jon-Light-Features ([Jon Light, Offene Punkte](jon-light-stories.md)).
