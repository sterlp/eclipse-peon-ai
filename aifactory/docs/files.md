---
idPrefix: WP
---
# Dateien — Schreibgrenze, TMP und Datei-Bereich

## Ziel

Agenten schreiben nur im Projekt-Checkout und im temporären Verzeichnis; der Nutzer sieht beides lesend
im Jon-Chat. Die Sicherheitsgrenze einer Web-App, die auf dem Rechner des Nutzers schreibt.

## Regeln

### R-WP-6 — Schreiben nur im Projekt und im temporären Verzeichnis ❌

Umsetzung mit dem bestehenden `WriteValidator` + `QualifiedPathValidator` im Core — **kein neuer
Mechanismus**, die Factory übergibt nur die zwei Wurzeln.

#### UC-WP-8 — writeInsideProjectRootAllowed ❌
- GIVEN ein Projekt als Arbeitsordner WHEN Jon oder ein Team-Mitglied unter dem Projekt-Root
  schreibt THEN gelingt der Write wie im Plugin.

#### UC-WP-9 — writeOutsideBothRootsRejected ❌
- GIVEN derselbe Agent WHEN er außerhalb von Projekt-Root **und** temporärem Verzeichnis schreiben
  will THEN wird abgelehnt und **Jons** Ablehnung steht als Tool-Zeile im Chat.
- GIVEN dasselbe Verlangen eines **Team-Mitglieds** WHEN es abgelehnt wird THEN erreicht die Ablehnung
  als Werkzeug-Ergebnis das Modell, die Datei entsteht nicht und im Chat erscheint **keine** Zeile —
  Mitglieder-Interna sind unsichtbar ([R-WP-4, UC-WP-40](chat.md)). Ein Mitglied hinterlässt also nie
  eine sichtbare Ablehnung; der sichtbare Fall ist immer Jons eigener Schreibversuch.

#### UC-WP-10 — traversalDoesNotEscapePinning ❌
- GIVEN ein Pfad mit `..` oder ein absoluter Pfad, der nach der Normalisierung außerhalb beider
  Wurzeln liegt WHEN geschrieben werden soll THEN wird abgelehnt (Normalisierung **vor** dem Match).

### R-WP-13 — Temporäres Verzeichnis ist der System-TMP der Instanz ❌

Zweite Schreibwurzel ist `System.getProperty("java.io.tmpdir")` — **nicht** je Projekt ein
Unterordner. Bewusst so entschieden; Preis: zwei parallel laufende Projekte teilen sich den einen
TMP-Raum (Kollisions-Risiko bei gleichen Dateinamen, kein Schutz, keine Quarantäne).

#### UC-WP-18 — tmpWritesLandInSystemTempDir ❌
- GIVEN ein Agent schreibt in das temporäre Verzeichnis WHEN der Pfad aufgelöst wird THEN liegt die
  Datei direkt unterhalb des System-TMP dieser Instanz.

### R-WP-8 — Der Projektstand ist im Chat-View einsehbar ❌

Lesender Blick auf die Dateien; geschrieben wird über diesen Bereich nie. Die zwei Wurzeln und ihre
Umschaltung sind in **R-WP-14** festgelegt.

### R-WP-14 — Der Datei-Bereich zeigt genau zwei Wurzeln ❌

Standard ist der **Git-Checkout** des Projekts; umschaltbar dazu: das **temporäre Verzeichnis**
(R-WP-13). Beides lesend — geschrieben wird über diesen Bereich nie.

#### UC-WP-19 — fileAreaShowsCheckoutAndTmp ❌
- GIVEN der Chat-View WHEN der Datei-Bereich geöffnet wird THEN steht der Projekt-Checkout als
  Standard-Wurzel bereit und das temporäre Verzeichnis ist als zweite Wurzel erreichbar; beide
  Angebote sind lesend.

#### UC-WP-42 — fileAreaNeverEscapesItsRoot ❌
- GIVEN der Datei-Bereich WHEN ein Pfad gelesen werden soll, der nach Normalisierung außerhalb der
  gewählten Wurzel liegt (`..`, absoluter Pfad, oder ein Symlink, der aus der Wurzel hinausführt)
  THEN antwortet der Server mit einem Fehler und liefert keinen Inhalt.
