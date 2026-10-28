---
idPrefix: WP
---
# Projekte — Git-Checkout als Projekt

## Ziel

Ein Projekt ist ein per Git geklonter Checkout unter `<home>/projects/<name>` — sicher hinzugefügt,
eindeutig, entfernbar, nie interaktiv; die Projektliste zeigt live, woran gearbeitet wird.
Woher Projekte auf einem frischen Node kommen: [Config-Repo R-CR-8](config-repo.md).

## Regeln

### R-WP-2 — Ein Projekt kommt ausschließlich über eine Git-URL ✅

Kein Upload, keine Ordner-Auswahl, kein Import eines bestehenden Verzeichnisses: Projekt hinzufügen
heißt Git-URL eintragen und klonen.

Ein **bereits ausgecheckter** Ordner von der Platte wird im MVP **nicht** adoptiert — Entscheidung
2026-10-26 („nicht wichtig, erstmal nur über den Checkout"): wer einen Arbeitsstand in der Factory
sehen will, trägt die URL ein und arbeitet in der Kopie. Eine spätere Adoption bräuchte eine Änderung
an dieser Regel **und** an R-WP-19 (Entfernen dürfte einen fremden Ordner nie löschen).

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

### R-WP-20 — Keine Zugangsdaten in der URL; Zugang kommt aus der Umgebung ❌ (geschärft 2026-10-28)

Die Factory klont über `https://` (öffentlich ohne Zugang, privat mit dem Default-Token,
[R-CR-6](config-repo.md)) und über SSH (`git@host:…`, `ssh://…`, Schlüssel des Nodes,
[R-CR-7](config-repo.md)). UC-CR-7/8 sind offen; der Rest ist gebaut. Eingebettete Zugangsdaten
(`https://nutzer:token@host/…`) sind verboten — sie landen sonst ungefragt in der Registry und werden
jedem Betrachter der Projektliste zurückgegeben. Andere Schemes (`file://`, `http://`) lehnt die
Factory ab; gefragt wird nie (R-WP-21).

#### UC-WP-30 — credentialUrlRejectedAndNothingStored ✅
- GIVEN eine URL mit Benutzer und Passwort/Token WHEN sie eingetragen wird THEN folgt eine klare
  Fehlermeldung ohne die Zugangsdaten zu wiederholen, und es entstehen weder Eintrag noch Ordner.

#### UC-WP-33 — unsupportedSchemeRejectedNothingStored ❌ (geschärft 2026-10-28: SSH ist erlaubt)
- GIVEN eine URL mit `file://` oder `http://` (z. B. `file:///tmp/demo`) WHEN sie eingetragen wird THEN
  lehnt die Factory mit dem Hinweis auf HTTPS oder SSH ab — ohne git aufzurufen, ohne Eintrag, ohne Ordner.
- GIVEN `git@github.com:acme/demo.git` oder `ssh://…` WHEN sie eingetragen wird THEN wird sie **nicht**
  abgelehnt, sondern geklont (UC-CR-8).

### R-WP-21 — Klonen ist nicht interaktiv und zeitlich begrenzt ✅

Die Factory läuft headless: kein Werkzeug darf auf eine Eingabe warten. Klonen endet deshalb nach
einer festen Frist mit Fehler, und git wird so aufgerufen, dass es gar nicht erst fragt.

#### UC-WP-31 — stalledCloneAbortsAndLeavesNothing ✅
- GIVEN eine URL, die den Verbindungsaufbau hält WHEN die Frist abläuft THEN wird der Prozess
  beendet, die Antwort ist ein Fehler und es bleiben weder Eintrag noch Ordner zurück.

#### UC-WP-32 — gitIsNeverAllowedToPrompt ✅
- GIVEN ein git-Kommando WHEN es ausgeführt wird THEN ist der Aufruf so konfiguriert, dass git
  niemals nach Benutzer, Passwort oder Host-Bestätigung fragt, sondern sofort fehlschlägt.

### R-WP-24 — Die Projektliste zeigt live, woran gearbeitet wird ❌

Jede Zeile der Projektliste trägt **arbeitet** (Jons Turn läuft, dazu die Zahl wartender Eingaben aus
der Queue, [R-WP-10](chat.md)) oder **frei**. Einen eigenen Zustand „wartet" gibt es nicht: Eingaben
warten nur, solange ein Turn läuft. Quelle ist der Laufzeit-Zustand im Speicher des Servers
(`ProjectRuntime`), verteilt als SSE an die Übersicht — kein eigener Speicher, nichts Persistiertes.
Nach einem Neustart ist jedes Projekt **frei**.

#### UC-WP-47 — projectListShowsLiveWorkState ❌
- GIVEN in Projekt A läuft ein Turn WHEN ein zweiter Nutzer die Projektliste öffnet THEN zeigt A
  „arbeitet"; nach dem Turn wechselt die Anzeige ohne Neuladen auf „frei".
- GIVEN in A läuft ein Turn und eine weitere Eingabe liegt in der Queue WHEN die Liste angezeigt wird THEN
  zeigt A „arbeitet · 1 wartend"; erst wenn auch diese Eingabe abgearbeitet ist, „frei".

### R-WP-3 — Projekt öffnen setzt den Arbeitsordner ✅

Das angeklickte Projekt ist der Arbeitsordner des Jons und seines Teams — Ersatz für den
Workspace-/Projektbegriff des Eclipse-Plugins (siehe [Open Points](open-points.md)).

#### UC-WP-5 — openProjectBindsWorkDir ✅
- GIVEN ein Projekt in der Liste WHEN der Nutzer es öffnet THEN öffnet der Chat-View und alle
  Werkzeuge dieses Jons arbeiten auf dem Root dieses Projekts.
