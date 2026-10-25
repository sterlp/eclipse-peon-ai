---
idPrefix: JL
---
# Jon Light & Stories — mehrere POs, ein Projekt

> **Status:** ❌ specified (2026-10-25), nicht gebaut. Setzt den Web-Prototype voraus
> ([web-prototype.md](web-prototype.md)); gebaut wird er **nach** dem MVP.

## Ziel

Mehrere Menschen sollen zu einem Projekt **Vorarbeiten** leisten können, ohne sich in den einen
Projekt-Jon zu mischen: jeder Nutzer hat einen eigenen **Jon Light**, der das Projekt **liest** und
Fragen beantwortet, und der als einziges Ergebnis **Stories** in ein `stories`-Verzeichnis schreibt.
Diese Stories werden später importiert (vom Projekt-Jon oder von einem PO) — der Import ist **nicht**
Teil dieses Features.

**Warum das eigenen Docs braucht:** hier schreiben mehrere Nutzer parallel in dasselbe Projekt. Das
ist der einzige Ort der Factory, an dem zwei Menschen dasselbe Verzeichnis mit Inhalt füllen.

## Regeln

### R-JL-1 — Jon Light ist ein eigener Agent je Nutzer ❌

Jon Light ist **nicht** der Projekt-Jon: er hat ein eigenes Gespräch, kein Team, keine Delegation und
keinen Zugriff auf dessen Chat oder Memory. Der Nutzer wechselt im Chat-View zwischen
Projekt-Jon und Jon Light; beide Gespräche bleiben getrennt.

#### UC-JL-1 — jonLightHasOwnConversation ❌
- GIVEN ein Projekt mit Projekt-Jon-Gespräch WHEN der Nutzer zu Jon Light wechselt THEN ist das
  Gespräch leer und enthält keine Nachricht des Projekt-Jons — und umgekehrt.

#### UC-JL-2 — oneJonLightPerUser ❌
- GIVEN zwei Nutzer am selben Projekt WHEN beide Jon Light öffnen THEN hat jeder sein eigenes
  Gespräch; keiner sieht die Fragen des anderen.

### R-JL-2 — Jon Light liest, und schreibt nur ins Stories-Verzeichnis ❌

Werkzeuge: alles Lesende der Core-Familien (`disk*`-Read/Grep/Search, `web*`, `docs*`-Read).
Schreibend ist **ausschließlich** das Stories-Verzeichnis des Projekts erlaubt — durchgesetzte
Schreib-Liste, nicht höfliche Bitte.

#### UC-JL-3 — jonLightWriteAllowedOnlyInStories ❌
- GIVEN Jon Light WHEN er in `<projekt>/stories/…` schreibt THEN gelingt der Write.

#### UC-JL-4 — jonLightWriteOutsideStoriesRejected ❌
- GIVEN Jon Light WHEN er eine Datei im Quellcode, in `docs/` oder im temporären Verzeichnis
  schreiben will THEN wird abgelehnt und die Ablehnung steht als Tool-Zeile im Gespräch.

#### UC-JL-5 — jonLightHasNoTeam ❌
- GIVEN Jon Light WHEN die Tool-Liste gebaut wird THEN ist kein Delegations-Werkzeug enthalten
  (kein `talkPlan`, `askDev`, `buildWithDev`, `reviewPlanAgent`) — er kann nichts bauen lassen.

### R-JL-3 — Eine Story ist eine Datei in `stories/` ❌

`<projekt>/stories/<JJJJ-MM-TT>-<nutzername>-<kurztitel>.md`, eine Story je Datei, existsierende
Dateien werden **nie** überschrieben (Namens-Suffix bei Kollision). Inhalt: Ziel, Nutzer story
(GIVEN/WHEN/THEN), betroffene Bereiche, offener Punkt oder Konflikt.

#### UC-JL-6 — storyFileIsCreatedOnce ❌
- GIVEN Jon Light WHEN eine Story angelegt wird THEN entsteht eine neue Datei in `stories/` mit dem
  Schema oben; der Schreibversuch auf eine bereits existierende Story-Datei wird abgelehnt.

#### UC-JL-7 — twoUsersNeverCollideOnFileNames ❌
- GIVEN zwei Nutzer legen gleichzeitig eine Story mit demselben Kurztitel an THEN entstehen zwei
  Dateien mit eindeutigem Namen — keine überschreibt die andere.

### R-JL-4 — Eine Story ist konfliktfrei oder benennt ihren Konflikt ❌

Eine Story darf einer bestehenden Regel, einem Glossar-Eintrag oder einem ADR nicht widersprechen,
ohne es zu sagen. Widersprichst du, muss die Story einen Abschnitt **Konflikt** tragen: beide
Positionen (Regel/ADR und Story) und wie der Konflikt aufgelöst werden soll.

#### UC-JL-8 — storyWithoutConflictHasNoConflictSection ❌
- GIVEN eine Story, die keiner bestehenden Regel widerspricht THEN enthält sie keinen
  Konflikt-Abschnitt und verweist auf die Regeln, die sie erfüllt.

#### UC-JL-9 — contradictingStoryNamesBothSides ❌
- GIVEN eine Story, die einer bestehenden Regel widerspricht THEN enthält sie den Abschnitt
  *Konflikt* mit Regel-Verweis, Gegenposition und vorgeschlagener Auflösung.

### R-JL-5 — Stories sind Vorschläge, kein Bestand ❌

Was in `stories/` liegt, ist **nicht** Teil der Feature-Docs und verändert keine Regel. Geschrieben
werden darf dort nur, was später importiert werden muss.

#### UC-JL-10 — storyIsNotADocRule ❌
- GIVEN eine fertige Story WHEN der Projekt-Jon das Feature-Doc liest THEN gilt weiterhin nur das,
  was in den Feature-Docs steht; die Story ist Eingabe, nicht Entscheidung.

## Offene Punkte

| # | Punkt | Stand / Vorschlag |
|---|-------|-------------------|
| 1 | Import-Mechanik (wer holt Stories in die Docs?) | ⏳ bewusst außerhalb dieses Features |
| 2 | Darf Jon Light auch `docs/` **lesen** und daraus antworten? | ⏳ angenommen: ja, lesen ja, schreiben nein |
| 3 | Story-Sprache und -Schema fest vorgeben oder frei? | ⏳ angenommen: Schema aus R-JL-3, Formulierungen frei |
