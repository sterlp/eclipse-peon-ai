---
idPrefix: CR
---
# Config-Repo — die Fabrik-Config lebt in git

> **Status:** ❌ specified (MVP, Paul 2026-10-28), nicht gebaut. Verschlüsselung (R-CR-1) ist das **erste
> Feature nach einem stabilen MVP**, Rotation (R-CR-3) 🚧. Vor der Planung: Architektur-Doc nachziehen
> (Config-Abschnitt kennt noch kein Config-Repo, Schreiben „nur nicht-leere Felder" ist überholt, R-WP-16).

## Ziel

Eine Fabrik startet auf einem **frischen Node ohne Platte** und ist trotzdem sofort vollständig: beim
Start holt sie ihre Config aus einem **Config-Git-Repo** (wie ein Spring Config Server, aktuell immer
`main`). Darin liegt alles, was die Fabrik ausmacht — Config, Agenten (`agents/`), Skills und die
Projektliste (`projects.yml`) —, also eine versionierte Config **ohne Datenbank**. Nicht im Repo liegt
die Chat-History der Agenten.

Änderungen über die Config-Seite lassen sich **nur testen** (Datei geändert, kein Commit) oder
**übernehmen** (Commit + Push).

## Regeln — MVP

### R-CR-10 — Aufbau des Fabrik-Homes: `config/` ist der Clone des Config-Repos ❌

```
~/.aifactory/
  config/                 ← Clone des Config-Repos (ohne URL: lokaler Ordner)
    config.properties     Default für alle Agenten + globale Einstellungen
    projects.yml          Projektliste (ersetzt projects.json)
    agents/<id>/AGENT.md  Agenten
    .agents/skills/       Skills (wie im Plugin der empfohlene Ort)
  projects/<name>         ← Clone je Projekt — nicht im Config-Repo
  state/<id>/             ← Chat-History je Projekt — nicht im Config-Repo
```

Committet wird **alles unter `config/`** und nichts anderes. `projects/` und `state/` liegen **neben** dem
Clone, nicht darin — es braucht kein `.gitignore`, und kein Projekt-Clone oder Chat kann versehentlich
im Config-Repo landen.

#### UC-CR-13 — commitContainsOnlyConfigDir ❌
- GIVEN eine Fabrik mit Config-Repo, einem Projekt und einem geführten Chat WHEN der Nutzer etwas
  übernimmt THEN enthält der Commit nur Dateien unter `config/` — keine aus `projects/` oder `state/`.

### R-CR-5 — Bootstrap nur über die Umgebung: Config-Repo-URL, optional Git-Token ❌

Was eine Fabrik zum Funktionieren braucht, kommt **ausschließlich aus Umgebungsvariablen** — den Zugang
zum Repo kann man nicht aus dem Repo holen. Pflicht ist die **Config-Repo-URL**. **Optional** ist ein
**Git-Token** — nötig nur, wenn die Umgebung nicht schon selbst Git-Zugang mitbringt (z. B. eine
SSH-Identity des Nodes, R-CR-7); ohne Token und ohne Zugang aus der Umgebung kann die Fabrik nur lesen,
was öffentlich ist. Beim Start klont die Fabrik `main` des Config-Repos. Das Token steht nie in einer
Datei auf dem Node, nie im Log, nie in der Oberfläche oder in einer Fehlermeldung. Fehlt die URL, läuft
die Fabrik rein lokal; das Config-Repo ist offline und „übernehmen" gesperrt.

#### UC-CR-6 — freshNodeBootstrapsFromConfigRepo ❌
- GIVEN ein frischer Node mit Config-Repo-URL und Token (oder eigenem Git-Zugang) in der Umgebung WHEN
  die Fabrik startet THEN hat sie `main` geklont und Config, Agenten, Skills und `projects.yml` sind
  geladen; keine Datei auf dem Node enthält das Token.
- GIVEN keine Config-Repo-URL WHEN die Fabrik startet THEN läuft sie lokal, die Config-Seite meldet das
  Config-Repo als offline, und „übernehmen" ist in Oberfläche und Backend gesperrt.

### R-CR-4 — Zwei Aktionen: „testen" schreibt die Datei, „übernehmen" committet und pusht ❌

Jede Änderung auf der Config-Seite wird zuerst **getestet**: sie landet in der lokalen Datei und wirkt ab
dem nächsten Turn, ohne Commit. **„übernehmen"** committet und pusht den lokalen Stand ins Config-Repo.

#### UC-CR-4 — testWritesFileCommitPushes ❌
- GIVEN ein Config-Repo WHEN der Nutzer ein Modell ändert und „testen" wählt THEN steht der neue Wert in
  der lokalen Datei, der nächste Turn nutzt ihn, und das Repo hat keinen neuen Commit.
- GIVEN derselbe Stand WHEN der Nutzer „übernehmen" wählt THEN entsteht ein Commit mit der Änderung und
  er ist gepusht.

### R-CR-2 — Keys liegen im MVP im Klartext; die Config-Seite warnt ❌

Im MVP gibt es keine Verschlüsselung: Keys stehen im Klartext in der Datei und gehen mit „übernehmen"
ins Config-Repo. Die Config-Seite zeigt deshalb einen **Alert**: Keys werden **unverschlüsselt**
committet, das Config-Repo muss **sicher** sein (privat, nur vertrauenswürdige Leser). **WEIL** lokal
betriebene Modelle meist gar keinen Key haben (Paul 2026-10-28); die Verschlüsselung (R-CR-1) kommt als
erstes Feature nach einem stabilen MVP.

#### UC-CR-5 — plaintextKeyCommitsWithWarning ❌
- GIVEN der Nutzer setzt einen Key WHEN er testet THEN wirkt der Key sofort und steht im Klartext in der
  lokalen Datei; es entsteht kein Commit.
- GIVEN ein gesetzter Key WHEN der Nutzer „übernehmen" wählt THEN entsteht Commit + Push mit dem Key im
  Klartext, und die Config-Seite zeigt den Alert „Keys unverschlüsselt — Repo muss sicher sein".

### R-CR-6 — Das Git-Token ist der Default für jedes Repo ❌

Das Token aus der Umgebung gilt für **jeden** Git-Zugriff der Fabrik — Config-Repo und Projekt-Repos.
**Genau dieses eine Token**, kein Zugang je Repo und kein Git-Zugang in der Config — wer mehrere Hosts
bedient, nimmt SSH (R-CR-7). Damit dürfen Projekt-Repos privat sein ([R-WP-20](project.md)).

#### UC-CR-7 — defaultTokenClonesPrivateProject ❌
- GIVEN ein privates HTTPS-Projekt-Repo und das Token in der Umgebung WHEN der Nutzer es hinzufügt THEN
  klont die Fabrik es mit dem Default-Token, und das Token steht weder in `projects.yml` noch in der
  Projektliste.

### R-CR-7 — SSH mit dem Schlüssel des Nodes, Public Key auf der Config-Seite ❌

Config-Repo und Projekt-Repos dürfen per SSH kommen (`git@host:…`, `ssh://…`); git nutzt den Schlüssel aus
`~/.ssh` des Nodes. Die Fabrik **erzeugt nie einen Private Key** — er kommt vom Betreiber. Die
Config-Seite zeigt die **Public Keys** aus `~/.ssh` (`*.pub`) zum Kopieren, damit man sie beim Git-Host
hinterlegen kann; fehlt einer, sagt sie das. Im MVP werden **alle Hosts angenommen**, ohne Rückfrage.
Ein Schlüssel mit Passphrase ist nicht nutzbar: git bricht sofort mit Meldung ab und wartet nie
([R-WP-21](project.md)).

#### UC-CR-8 — sshCloneWithNodeKey ❌
- GIVEN ein Node mit Schlüssel in `~/.ssh` und die URL `git@github.com:acme/oaf-config.git`, Host noch
  unbekannt WHEN die Fabrik startet THEN klont sie ohne Token und ohne Rückfrage.
- GIVEN ein Schlüssel mit Passphrase WHEN geklont wird THEN bricht git sofort mit Fehler ab, es bleibt
  weder Eintrag noch Ordner, und nichts wartet auf Eingabe.

#### UC-CR-9 — publicKeyShownNoPrivateKeyCreated ❌
- GIVEN `~/.ssh/id_ed25519.pub` WHEN die Config-Seite geöffnet wird THEN steht der Public Key kopierbar
  da; vom Private Key verlässt kein Zeichen den Server.
- GIVEN kein Schlüssel in `~/.ssh` WHEN die Config-Seite geöffnet wird THEN sagt sie „kein SSH-Key —
  vom Betreiber bereitstellen", und in `~/.ssh` ist danach keine neue Datei.

### R-CR-8 — Frischer Node: Projekte aus `projects.yml` neu klonen, Chats leer ❌

Die Chat-History liegt nicht im Config-Repo. Startet die Fabrik auf einem frischen Node, klont sie jedes
Projekt aus `projects.yml` neu; jeder Chat beginnt leer. Auf demselben Node mit bleibender Platte gilt
[R-WP-11](chat.md) unverändert. **WEIL** der Chat Arbeitsverlauf ist, keine Config — was zählt,
steht in Docs und Code des Projekts, also in git. Damit dort nichts verloren geht, pusht Dev jeden Commit
([R15 Jon](../../org.sterl.llmpeon.core/docs/po-agent-jon.md)).

#### UC-CR-10 — freshNodeReclonesProjectsWithEmptyChats ❌
- GIVEN ein frischer Node und ein Config-Repo, dessen `projects.yml` zwei Projekte führt WHEN die Fabrik
  startet THEN sind beide Projekte geklont und in der Liste, ihre Chats sind leer.
- GIVEN derselbe Node mit bleibender Platte WHEN die Fabrik neu startet THEN sind die Chats wieder da.

### R-CR-9 — Fremde Änderungen im Config-Repo: Rebase, nie Force-Push; Sync-Button ❌

Das Config-Repo kann sich seit dem Checkout geändert haben (Edit direkt im Repo, zweite Fabrik).
„übernehmen" holt deshalb erst `main` und setzt den eigenen Commit darauf (Rebase), dann Push. Bei einem
**Konflikt** wird nichts gepusht: die lokale Datei bleibt, wie der Nutzer sie getestet hat, und die
Config-Seite nennt Konflikt und Datei. **Nie Force-Push** — er würde fremde Änderungen still überschreiben.
Die Config-Seite hat einen **Sync-Button**, der den Stand von `main` holt und neu lädt (wirkt ab dem
nächsten Turn). Lokal getestete, nicht übernommene Änderungen **bleiben** und werden auf den neuen Stand
gesetzt (Rebase wie oben). Bei Konflikt ändert sich lokal nichts; die Seite nennt den Konflikt und bietet
„lokale Änderungen verwerfen und syncen" an — verworfen wird nie ohne diese Bestätigung.

#### UC-CR-11 — commitRebasesNeverForcePushes ❌
- GIVEN jemand hat im Repo ein anderes Projekt in `projects.yml` ergänzt WHEN der Nutzer ein Modell
  übernimmt THEN enthält das Repo danach beide Änderungen.
- GIVEN jemand hat dieselbe Zeile geändert WHEN der Nutzer übernimmt THEN wird nichts gepusht, die lokale
  Datei ist unverändert, und die Config-Seite nennt den Konflikt mit Datei.

#### UC-CR-12 — syncKeepsLocalTestChanges ❌
- GIVEN das Repo hat ein neues Modell für Dev und lokal ist nichts getestet WHEN der Nutzer „Sync" klickt
  THEN gilt das neue Modell ab dem nächsten Turn.
- GIVEN Dev lokal auf `gpt-5` getestet und im Repo ein neues Projekt WHEN Sync THEN gilt beides: Dev
  nutzt `gpt-5`, das neue Projekt ist da.
- GIVEN dieselbe Zeile lokal und im Repo geändert WHEN Sync THEN ändert sich lokal nichts, die Seite nennt
  den Konflikt und bietet „verwerfen und syncen" an; erst nach Bestätigung gilt der Repo-Stand.

## Regeln — erstes Feature nach dem MVP: Verschlüsselung

### R-CR-1 — Keys verschlüsselt mit einem Secret aus der Umgebung ❌ (nach dem MVP)

Keys und Tokens stehen in der Config als `{cipher}…`, alles andere im Klartext (lesbare Diffs, sichtbar
was geheim ist). Ver- und entschlüsselt wird mit **einem** Secret, das die Fabrik ausschließlich aus
einer Umgebungsvariable liest — nie von Platte, nie aus dem Repo; ein neuer Node braucht genau ein
Secret statt jedes Keys einzeln. Mit Secret liegen Keys **auch beim Testen** schon verschlüsselt in der
lokalen Datei. Empfehlung an den Betreiber: zufällig erzeugtes Secret (≥ 32 Zeichen), kein merkbares
Passwort — wer das Repo lesen kann, kann offline raten.

Fehlt das Secret, bricht der Start **nicht** ab: Warnung im Log, die Config-Seite zeigt den Alert aus
R-CR-2 und nennt zusätzlich die Umgebungsvariable; „übernehmen" bleibt erlaubt (Klartext, R-CR-2).

Beim Umstieg vom MVP: Keys, die schon im Klartext committet wurden, bleiben in der git-History lesbar —
das Secret schützt nur neue Commits; solche Keys beim Anbieter erneuern.

#### UC-CR-1 — committedKeyIsCipherOnly ❌
- GIVEN das Secret ist gesetzt und der Nutzer setzt auf der Config-Seite einen Key WHEN er testet oder
  „übernehmen" wählt THEN steht der Key in Datei und Commit nur als `{cipher}…`; kein Zeichen des
  Klartexts liegt im Repo.
- GIVEN ein `{cipher}`-Key im Repo und dasselbe Secret auf einem neuen Node WHEN die Fabrik startet THEN
  ist der Key entschlüsselt verfügbar und die Config-Seite zeigt ihn als „gesetzt" (maskiert, R-WP-16).

#### UC-CR-2 — missingSecretStartsWithAlert ❌
- GIVEN ein Node ohne Secret in der Umgebung WHEN die Fabrik startet THEN läuft sie, das Log trägt eine
  Warnung, und die Config-Seite zeigt den Alert mit dem Namen der Umgebungsvariable, dem Hinweis, dass
  Keys nicht verschlüsselt sind, und dass das Config-Repo sicher sein muss.

### R-CR-3 — Secret rotieren über die Config-Seite 🚧 (nach R-CR-1)

Eingabe altes Secret + neues Secret ⇒ die Fabrik entschlüsselt alle Keys mit dem alten und verschlüsselt
sie mit dem neuen. Nur wenn das für **alle** Keys gelingt: Commit mit **Reset der gesamten git-History**,
danach Hinweis an den Nutzer: neues Secret als Umgebungsvariable setzen und die Fabrik neu starten.
Schlägt ein Key fehl, bleibt alles unverändert. Offen: History-Reset — siehe [Offene Punkte](#offene-punkte) #2.

#### UC-CR-3 — rotateReencryptsAllOrNothing 🚧
- GIVEN altes und neues Secret, alle Keys mit dem alten lesbar WHEN der Nutzer rotiert THEN tragen alle
  Keys `{cipher}…` des neuen Secrets, das Repo hat einen neuen Stand, und der Nutzer bekommt den Hinweis
  „Secret als Umgebungsvariable setzen, Fabrik neu starten".
- GIVEN ein falsches altes Secret WHEN der Nutzer rotiert THEN ändert sich keine Datei und kein Commit
  entsteht; die Meldung nennt das falsche alte Secret.

## Offene Punkte

| # | Punkt | Stand |
|---|-------|-------|
| 2 | Rotation mit History-Reset: wer das Repo vorher geklont hat, behält die alte History — schützt nur gegen künftige Leser und kostet die Versionierung | ❓ |
