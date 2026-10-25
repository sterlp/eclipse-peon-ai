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
* **⏳ Chat-Nachrichten tragen im MVP keinen Autorennamen** — geteilter Chat je Projekt
  ([R-WP-16](web-prototype.md)), aber „wer hat das geschrieben?" ist offen gelassen. *Andersfall:*
  Autor gehört in die History-Zeile und damit in ein Core-Format.
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

## ❓ offen

* **❓ Authentifizierung** — der Prototyp kennt nur einen Nutzernamen (R-WP-17). Ab wann echte
  Anmeldung nötig wird (externer Zugriff?), ist eine Produktfrage, keine Prototyp-Frage.
* **❓ Story-Import** — wer holt `stories/*.md` in die Feature-Docs und wann? Bewusst außerhalb des
  Jon-Light-Features ([Jon Light, Offene Punkte](jon-light-stories.md)).
