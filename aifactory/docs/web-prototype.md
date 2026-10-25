---
idPrefix: WP
---
# Web-Prototype — Jon im Browser

> **Status:** 🚧 in design (2026-10-25) — Anforderungen des Users aufgeschrieben, drei Punkte offen
> (Tabelle am Ende). **Kein Code, kein Plan** bis die offenen Punkte entschieden sind.

## Ziel

Mit möglichst wenig Eigenbau den Kern zeigen: ein per Git geklontes Projekt, darauf **Jon mit seinem
Team**, Schreibzugriff gepinnt auf dieses Projekt. Agent-Logik, Tools und Prompts kommen
unverändert aus `llmpeon-core` ([AF-ADR-001](adr/001-factory-shares-core-disk-only.md)); die Factory
liefert nur Transport, Session und die zwei Views.

**Nicht-Ziel des Prototyps:** Authentifizierung, Multi-User-Verwaltung, Build-/Test-Runner,
Java-Navigation, Projektverwaltung über Git hinaus, Deployment-Härtung.

**Look:** Orientierung ist `stara-web` (Chat-Optik), aber mit deutlich kleineren linken/rechten
Rändern als dort.

## Regeln

### R-WP-1 — Views: Projects, Chat, Einstellungen ❌

Die App hat die Views **Projects** (Startseite), **Chat** und **Einstellungen** (R-WP-16). Alles
andere — Dateien, Agenten-Kacheln, weitere Funktionen — ist ein **Bereich** innerhalb eines dieser
Views, nie ein weiterer View.

#### UC-WP-1 — showsExactlyThreeViews ❌
- GIVEN die App WHEN der Nutzer navigiert THEN gibt es genau *Projects*, *Chat* und *Einstellungen*;
  der Datei-Bereich und die Team-Kacheln liegen im Chat-View.

### R-WP-22 — Die App wird unter `/ui/` ausgeliefert ❌

Die Factory ist ein Browser-Produkt: wer `http://<host>/ui/` öffnet, sieht die App. `index.html` und
die gebauten Assets (JS, CSS mit Hash im Namen) kommen als das an, was sie sind — `/api/**` bleibt
davon unberührt, und `/` führt nach `/ui/`. Eine Adresse, die niemand bedienen kann, ist ein Fehler
und keine Weiterleitung: kein Request darf in einer Schleife aus Weiterleitungen enden.

#### UC-WP-34 — indexHtmlServedAtUiRoot ❌
- GIVEN der laufende Server WHEN `GET /ui/` oder `GET /ui` THEN kommt `200` mit `text/html` und dem
  Inhalt der gebauten `index.html`; `GET /` leitet nach `/ui/` weiter.

#### UC-WP-35 — hashedAssetsServedAsThemselves ❌
- GIVEN die gebauten Assets liegen im Klassenpfad WHEN `GET /ui/assets/<name>.js` oder
  `/ui/assets/<name>.css` THEN kommt `200` mit dem Dateiinhalt und dem passenden `Content-Type`
  (`text/javascript` bzw. `text/css`) — nie JSON, nie `text/plain`, kein `500`.

### R-WP-2 — Ein Projekt kommt ausschließlich über eine Git-URL ✅

Kein Upload, keine Ordner-Auswahl, kein Import eines bestehenden Verzeichnisses: Projekt hinzufügen
heißt Git-URL eintragen und klonen.

#### UC-WP-2 — addProjectClonesBeforeListing ✅
- GIVEN die Projects-View WHEN der Nutzer eine Git-URL einträgt THEN wird geklont und das Projekt
  erscheint **erst nach erfolgreichem Clone** in der Liste.

#### UC-WP-3 — sameUrlReusesExistingClone ✅
- GIVEN eine bereits geklonte URL WHEN sie erneut eingetragen wird THEN wird das bestehende
  Verzeichnis weiterverwendet — kein zweiter Clone, kein Überschreiben, keine Fehlermeldung.

#### UC-WP-4 — failedCloneLeavesListUnchanged ✅
- GIVEN eine ungültige oder nicht erreichbare URL WHEN der Clone scheitert THEN bleibt die Liste
  unverändert und die Git-Fehlermeldung steht lesbar am Eingabefeld.

### R-WP-18 — Identität über URL, Kollision über Namen ✅

Projektnummer und Ordner dürfen sich nie in die Quere kommen: zwei verschiedene Repos, die denselben
Ordnernamen ergäben, sind ein **Konflikt**, keine zweite Zeile in der Liste. Ein Zielordner, der
bereits existiert und **kein** Git-Checkout ist, wird nie überschrieben oder gefüllt.

#### UC-WP-26 — sameNameDifferentUrlRejected ✅
- GIVEN ein registriertes Projekt `demo` aus `host/a/demo` WHEN `host/b/demo` eingetragen wird
  THEN scheitert das mit einer Fehlermeldung, die das bestehende Projekt nennt, und es entsteht
  weder ein zweiter Ordner noch ein zweiter Eintrag.

#### UC-WP-27 — existingNonGitDirectoryIsNeverOverwritten ✅
- GIVEN `<home>/projects/demo` existiert, ist aber kein Git-Checkout WHEN `…/demo.git` eingetragen
  wird THEN wird abgelehnt, der vorhandene Ordner bleibt unverändert und die Liste bleibt leer.

### R-WP-19 — Entfernen löscht Eintrag und Checkout, niemals etwas außerhalb ✅

Entfernen heißt: Registry-Eintrag weg **und** Checkout weg, mit Rückfrage im UI. Getilgt wird
ausschließlich ein Pfad, der unter `<home>/projects` liegt — ein aus der Registry-Datei gelesener
Pfad ist nie eine Löschfreigabe.

#### UC-WP-28 — removeDeletesEntryAndCheckout ✅
- GIVEN ein Projekt in der Liste WHEN der Nutzer es entfernt und die Rückfrage bestätigt THEN sind
  Eintrag und Checkout-Ordner weg; ohne Bestätigung bleibt beides unangetastet.

#### UC-WP-29 — removeNeverDeletesOutsideProjectDir ✅
- GIVEN die Registry-Datei trägt von Hand eingetragen einen Pfad außerhalb von `<home>/projects`
  (z. B. `/` oder einen absoluten Pfad) WHEN das Projekt entfernt wird THEN wird der Pfad
  abgelehnt bzw. nicht gelöscht und kein Byte außerhalb des Projektverzeichnisses verschwindet.

### R-WP-20 — Nur öffentliche Repos, keine Zugangsdaten in der URL ✅

v1 klont öffentliche HTTPS-Repos, **ausschließlich über `https://`**. Eingebettete Zugangsdaten
(`https://nutzer:token@host/…`) sind verboten — sie landen sonst ungefragt in der Registry und werden
jedem Betrachter der Projektliste zurückgegeben. Andere Schemes (`git@host:org/repo.git`, `ssh://`,
`file://`) kann v1 nicht: sie setzen einen Schlüssel oder eine Passphrase voraus, und die Factory
fragt nie nach ([R-WP-21](#r-wp-21--klonen-ist-nicht-interaktiv-und-zeitlich-begrenzt-)). Private
Repos brauchen später einen eigenen, bewussten Mechanismus.

#### UC-WP-30 — credentialUrlRejectedAndNothingStored ✅
- GIVEN eine URL mit Benutzer und Passwort/Token WHEN sie eingetragen wird THEN folgt eine klare
  Fehlermeldung ohne die Zugangsdaten zu wiederholen, und es entstehen weder Eintrag noch Ordner.

#### UC-WP-33 — nonHttpsUrlRejectedNothingStored ✅
- GIVEN eine URL ohne `https://` (z. B. `git@github.com:acme/demo.git`, `ssh://…`, `file:///tmp/demo`)
  WHEN sie eingetragen wird THEN lehnt die Factory mit dem Hinweis auf öffentliche HTTPS-Repos ab —
  ohne git aufzurufen, ohne Eintrag, ohne Ordner.

### R-WP-21 — Klonen ist nicht interaktiv und zeitlich begrenzt ✅

Die Factory läuft headless: kein Werkzeug darf auf eine Eingabe warten. Klonen endet deshalb nach
einer festen Frist mit Fehler, und git wird so aufgerufen, dass es gar nicht erst fragt.

#### UC-WP-31 — stalledCloneAbortsAndLeavesNothing ✅
- GIVEN eine URL, die den Verbindungsaufbau hält WHEN die Frist abläuft THEN wird der Prozess
  beendet, die Antwort ist ein Fehler und es bleiben weder Eintrag noch Ordner zurück.

#### UC-WP-32 — gitIsNeverAllowedToPrompt ✅
- GIVEN ein git-Kommando WHEN es ausgeführt wird THEN ist der Aufruf so konfiguriert, dass git
  niemals nach Benutzer, Passwort oder Host-Bestätigung fragt, sondern sofort fehlschlägt.


### R-WP-3 — Projekt öffnen setzt den Arbeitsordner ❌

Das angeklickte Projekt ist der Arbeitsordner des Jons und seines Teams — Ersatz für den
Workspace-/Projektbegriff des Eclipse-Plugins (siehe [Open Points](open-points.md)).

#### UC-WP-5 — openProjectBindsWorkDir ❌
- GIVEN ein Projekt in der Liste WHEN der Nutzer es öffnet THEN öffnet der Chat-View und alle
  Werkzeuge dieses Jons arbeiten auf dem Root dieses Projekts.

### R-WP-4 — Der Nutzer spricht immer mit Jon 🚧

Keine Agent-Auswahl, keine weiteren Built-in- oder Custom-Agenten
([PR-1](index.md)). Delegationen an Da Thinka/Da Mek/Da Dok/Da Sniffa sind als Tool-Zeilen im Chat
sichtbar — Parität zum Plugin.

#### UC-WP-6 — chatAlwaysGoesToJon ❌
- GIVEN der Chat-View WHEN der Nutzer eine Nachricht sendet THEN antwortet Jon; ein Agenten-Wechsel
  ist nirgends möglich.

### R-WP-5 — Antworten erscheinen laufend ❌

Der Turn streamt in den Chat, ohne dass der Nutzer aktualisieren muss. Nebenläufigkeit, Cancel und
Queue sind die des Cores — die Web-Schicht erfindet keinen zweiten Turn-Pfad.

#### UC-WP-7 — streamingUpdatesChatWithoutReload ❌
- GIVEN eine sendete Nachricht WHEN Jon antwortet THEN wächst die Antwort sichtbar und der
  Tool-Fortschritt erscheint als Zeile.

### R-WP-6 — Schreiben nur im Projekt und im temporären Verzeichnis ❌

Die Sicherheitsgrenze einer Web-App, die auf dem Rechner des Nutzers schreibt. Umsetzung mit dem
bestehenden `WriteValidator` + `QualifiedPathValidator` im Core — **kein neuer Mechanismus**, die
Factory übergibt nur die zwei Wurzeln.

#### UC-WP-8 — writeInsideProjectRootAllowed ❌
- GIVEN ein Projekt als Arbeitsordner WHEN Jon oder ein Team-Mitglied unter dem Projekt-Root
  schreibt THEN gelingt der Write wie im Plugin.

#### UC-WP-9 — writeOutsideBothRootsRejected ❌
- GIVEN derselbe Agent WHEN er außerhalb von Projekt-Root **und** temporärem Verzeichnis schreiben
  will THEN wird abgelehnt und die Ablehnung steht als Tool-Zeile im Chat.

#### UC-WP-10 — traversalDoesNotEscapePinning ❌
- GIVEN ein Pfad mit `..` oder ein absoluter Pfad, der nach der Normalisierung außerhalb beider
  Wurzeln liegt WHEN geschrieben werden soll THEN wird abgelehnt (Normalisierung **vor** dem Match).

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

### R-WP-8 — Der Projektstand ist im Chat-View einsehbar ❌

Lesender Blick auf die Dateien; geschrieben wird über diesen Bereich nie. Die zwei Wurzeln und ihre
Umschaltung sind in **R-WP-14** festgelegt.

### R-WP-9 — Konfiguration: eigene Datei der Factory, nie der Plugin-Bestand ❌

Der Server liest Provider, Modelle und Keys aus **seiner eigenen** Config
(`~/.aifactory/config.properties`, Keys zusätzlich aus Umgebungsvariablen). Der Config-Bestand des
Plugins wird **nicht** gelesen und **nie** geschrieben — er liegt in Eclipse-Präferenzen und ist damit
keine Datei (Konflikt und Begründung:
[Architektur](aifactory-architektur.md#konflikt-dokumentiert-2026-10-25)). Überschreibungen aus dem
Einstellungen-View landen in derselben Datei der Factory (R-WP-16).

#### UC-WP-20 — configReadFromFactoryOwnStore ❌
- GIVEN ein laufender Server WHEN Jon gebaut wird THEN stammen Provider/Modell/Keys aus der Config der
  Factory; der Plugin-Bestand bleibt byte-identisch.
- GIVEN eine fehlende oder unvollständige Config WHEN der Nutzer eine Nachricht sendet THEN sagt die
  App knapp, welche Angabe fehlt, statt mit einer Werkzeugfehler-Meldung zu enden.

### R-WP-10 — Ein Turn je Projekt, weitere Eingaben in der Queue 🚧

Eingaben während eines laufenden Turns gehen in die Queue des Cores; verschiedene Projekte dürfen
parallel laufen.

#### UC-WP-14 — secondInputDuringTurnQueues ❌
- GIVEN ein laufender Turn WHEN der Nutzer erneut sendet THEN wird die Nachricht gequeued und erst
  nach dem Turn verarbeitet — kein zweiter paralleler Turn auf derselben Memory.

### R-WP-11 — Projekte und ihr Chat überleben einen Neustart ❌

Projekt-Registry, Clone und **genau ein** Chat je Projekt sind persistent. Ein Projekt hat genau ein
Gespräch — es gibt keine Chat-Auswahl und kein zweites Gespräch neben dem einen. Nach dem Neustart
zeigt der Chat die **Texte von Nutzer und Jon** aus dem History-Bestand; Werkzeug- und Statuszeilen
sind live-only — die Factory führt dafür **keinen** zweiten Speicher. Nach dem Neustart
ist Jons Gespräch wieder da; **sein Team beginnt leer** (RAM-only-Parität zum Plugin, globales
ADR-0024) — ⏳ Rückversicherung in [Open Points](open-points.md).

#### UC-WP-15 — registryAndChatSurviveRestart ❌
- GIVEN ein Projekt mit geführtem Chat WHEN der Server neu startet THEN ist das Projekt in der Liste
  und der Chat enthält wieder dieselben Nachrichten (Wiederherstellung aus dem bestehenden
  History-Bestand des Cores, kein zweiter Speicher).

#### UC-WP-16 — oneChatPerProject ❌
- GIVEN ein offenes Projekt WHEN der Nutzer den Chat-View betritt THEN gibt es genau dieses eine
  Gespräch; es existiert keine Möglichkeit, ein zweites Gespräch zum selben Projekt zu beginnen.

### R-WP-12 — Alle Core-Werkzeuge, kein Eclipse-Werkzeug ❌

Jon und sein Team bekommen **alle** Core-Werkzeuge inklusive Shell (`disk*`, `web*`, `shell`,
`docs*`) — User-Entscheid 2026-10-25. `eclipse*`-Werkzeuge existieren nicht ([PR-2](index.md)).

#### UC-WP-17 — allCoreToolsNoEclipseTools ❌
- GIVEN ein Projekt als Arbeitsordner WHEN die Tool-Liste des Jons gebaut wird THEN sind die
  Core-Familien vollständig enthalten (auch Shell) und **kein** Werkzeug mit `eclipse`-Präfix.

### R-WP-13 — Temporäres Verzeichnis ist der System-TMP der Instanz ❌

Zweite Schreibwurzel ist `System.getProperty("java.io.tmpdir")` — **nicht** je Projekt ein
Unterordner. Bewusst so entschieden; Preis: zwei parallel laufende Projekte teilen sich den einen
TMP-Raum (Kollisions-Risiko bei gleichen Dateinamen, kein Schutz, keine Quarantäne).

#### UC-WP-18 — tmpWritesLandInSystemTempDir ❌
- GIVEN ein Agent schreibt in das temporäre Verzeichnis WHEN der Pfad aufgelöst wird THEN liegt die
  Datei direkt unterhalb des System-TMP dieser Instanz.

### R-WP-14 — Der Datei-Bereich zeigt genau zwei Wurzeln ❌

Standard ist der **Git-Checkout** des Projekts; umschaltbar dazu: das **temporäre Verzeichnis**
(R-WP-13). Beides lesend — geschrieben wird über diesen Bereich nie.

#### UC-WP-19 — fileAreaShowsCheckoutAndTmp ❌
- GIVEN der Chat-View WHEN der Datei-Bereich geöffnet wird THEN steht der Projekt-Checkout als
  Standard-Wurzel bereit und das temporäre Verzeichnis ist als zweite Wurzel erreichbar; beide
  Angebote sind lesend.

### R-WP-15 — Je Projekt genau ein Jon, ein Chat für alle ❌

Ein Projekt hat genau **eine** Jon-Instanz und genau **ein** Gespräch. Jeder Nutzer, der das Projekt
öffnet, sieht und schreibt in **denselben** Chat — Nachrichteneingaben laufen in die Queue *dieses*
Jons, nie in eine private Kopie. Andere Projekte laufen unabhängig und parallel.

#### UC-WP-21 — secondUserSeesSameProjectChat ❌
- GIVEN Nutzer A hat mit dem Projekt chatet WHEN Nutzer B dasselbe Projekt öffnet THEN sieht B
  dieselben Nachrichten und sendet in denselben Gesprächsverlauf.

#### UC-WP-22 — inputOnBusyProjectQueuesNotParallel ❌
- GIVEN im Projekt läuft ein Turn WHEN ein anderer Nutzer sendet THEN geht die Nachricht in die
  Queue genau dieses Jons — kein zweiter paralleler Turn auf derselben Memory.

### R-WP-16 — Dritter View: Einstellungen ❌

Die App hat **Projects · Chat · Einstellungen**. Der Einstellungen-View zeigt Provider, Modell,
Temp/Think und die Key-Anzeige (maskiert) aus dem bestehenden geteilten Bestand und darf Werte
**überschreiben** — die Überschreibung liegt im eigenen Verzeichnis der Factory
(`~/.aifactory/`), der geteilte Bestand wird **nie** geschrieben.

**WEIL** (User 2026-10-25): „der Rest kann aus der config kommen von den files — aber ich mag die im
UI später auch bearbeiten können". Eine Überschreibungsdatei statt Rückschreiben hält den Bestand des
Plugins unverändert, den die Factory nur liest (R-WP-9).

#### UC-WP-23 — settingsShowsMaskedConfig ❌
- GIVEN der Einstellungen-View WHEN er geöffnet wird THEN stehen Provider, Modell und Key; der Key
  ist maskiert und wird nie im Klartext ausgeliefert.

#### UC-WP-24 — settingsOverrideNeverWritesSharedStore ❌
- GIVEN der Nutzer überschreibt ein Feld WHEN er speichert THEN liegt der Wert im Verzeichnis der
  Factory, der geteilte Bestand ist byte-identisch wie vorher, und der nächste Turn nutzt den
  überschriebenen Wert.

### R-WP-17 — Nutzer ohne Login, aber mit Namen ❌

Der Prototyp authentifiziert nicht. Ein Nutzer gibt einmal einen **Namen** an (Browser-Speicher,
kein Passwort); der Name ist die Identität für alles Nutzerbezogene (Jon Light, Story-Autor). Ohne
Namen gibt es keinen Nutzer-bezogenen Bereich.

#### UC-WP-25 — userNameIsTheIdentity ❌
- GIVEN ein frischer Browser WHEN der Nutzer seinen Namen einträgt THEN ist er für diese der
  identische Nutzer (Jon Light, Stories); ein anderer Browser mit anderem Namen ist ein anderer
  Nutzer.

## Offene Punkte

Keine. Was hier offen war, ist seit 2026-10-25 Regel: Ablage von Registry und Clone (R-WP-5,
R-WP-18), öffentliche Repos ohne Zugangsdaten (R-WP-20), Entfernen inkl. Checkout mit Rückfrage
(R-WP-19).

**Erledigt:** Konfiguration — keine Config-UI, Quelle ist der bestehende Config-Bestand
(R-WP-9); *Keys/Modelle im UI pflegen* ist zurückgestellt und als ⏳ in
[Open Points](open-points.md) notiert. TMP-Direktwahl, Shell-Zusage und die zwei Datei-Wurzeln sind
seit 2026-10-25 entschieden (R-WP-12…R-WP-14).
