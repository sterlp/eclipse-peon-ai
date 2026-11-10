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

### R-WP-33 — Datei-Bereich als Slide-out rechts ✅ (Paul 2026-11-02; Tab-Bündigkeit geschärft Runde 2, Inc 5; **Panel als AntD `Drawer` ✅** — Paul 2026-11-08, Abnahme; gebaut + reviewed 2026-11-08, Runde 6 (Mechanik `df2297fd`, N1 `c622ae47`), Look-/Verhaltens-Abnahme am Browser steht aus)

Der Datei-Bereich ist ein **Slide-out am rechten Rand** mit einem **vertikalen „Files"-Tab** — so ist
sichtbar, dass hier aufklappbar ist. **Das Slide-out ist ein AntD `Drawer`** (von rechts — Widget-vor-
eigenbau, [R-WP-46](ui.md); Paul 2026-11-08: „wenn es um das Slide-out rechts geht, ist der Drawer das
richtige Element"): die eigene Slide-Mechanik weicht, der Drawer legt sich über den rechten Rand und der
Chat bleibt links daneben sichtbar; der **Tab sitzt weiter am Panel** — bündig an der Oberkante, im
geschlossenen Zustand am rechten Rand der Chat-Fläche. Auf- und Zuklappen steuern **Icons** (`<` / `>` —
welche Bibliothek die liefert, regelt [R-WP-39](ui.md)), Schließen ist immer möglich.
**Die rechte Kante trägt genau ein Overlay:** der Agent-Insight ([R-WP-26](chat.md)) und der
Datei-Drawer konkurrieren um dieselbe Kante — öffnet einer, schließt sich der andere (der Drawer
über die Adresse, der Insight ohne Spur; abgeleitet aus „der Drawer legt sich über den rechten
Rand", Paul 2026-11-08). Im Panel: **Baum-Navigation**
über die zwei Wurzeln ([R-WP-14](files.md)), die als **Cards** erscheinen (Checkout, TMP —
Paul-Vorschlag); der **Projektname wird im Panel nicht wiederholt** (er steht in der Kopfzeile des
Chats). Eine geöffnete Datei zeigt der Viewer mit **Syntax-Highlighting** (react-shiki),
**nicht mittig zentriert** — der Chat bleibt daneben sichtbar, der Viewer kann immer geschlossen
werden.

#### UC-WP-60 — filesPanelSlideOutWithTree ✅ (GIVEN 3 seit 2026-11-08, Runde 6)
- GIVEN der Chat-View WHEN der Nutzer den vertikalen „Files"-Tab nutzt THEN klappt das Slide-out auf
  (`>` / `<`), zeigt die zwei Wurzeln als Cards und eine Baum-Navigation; der Projektname steht im
  Panel nicht wiederholt.
- GIVEN der Chat-View **in beliebiger Fensterhöhe** WHEN der Tab offen oder geschlossen ist THEN sitzt
  er **bündig an der Oberkante** des Panels, an dem er hängt (geschlossen am rechten Rand der
  Chat-Fläche), und **wandert mit der Panelbreite** mit.
- GIVEN der Agent-Insight ist offen ([R-WP-26](chat.md)) WHEN der Nutzer den Datei-Bereich öffnet
  THEN schließt sich der Insight — und umgekehrt: GIVEN der Datei-Drawer ist offen WHEN der Nutzer
  einen Kachel-Insight öffnet THEN schließt sich der Drawer (Adresse zurück auf den Chat). Die
  rechte Kante zeigt nie zwei Overlays zugleich.

#### UC-WP-61 — fileViewerHighlightsSyntax ✅
- GIVEN eine geöffnete Datei WHEN sie angezeigt wird THEN zeigt der Viewer sie mit
  Syntax-Highlighting, nicht zentriert, mit klarer Navigation zurück; der Chat bleibt daneben
  sichtbar.
- GIVEN eine geöffnete Datei mit mehreren Zeilen WHEN sie angezeigt wird THEN steht **je Code-Zeile
  genau eine Zeilenhöhe** — kein zweiter, leerer Zeilenabstand zwischen den Zeilen.
