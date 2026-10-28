---
idPrefix: PC
---
# Paco — der fachliche PO je Nutzer

> **Status:** ❌ specified, nicht gebaut. Setzt das MVP voraus
> ([Index](index.md)); gebaut wird er **nach** dem MVP. Bis 2026-10-27 hieß das
> Feature „Jon Light", die Seite „Read-Jon".

## Ziel

Die Web-App ist multi-user — deshalb gibt es dort **zwei** POs mit klarer Rollenteilung (nur im Web;
das Plugin kennt nur Jon):

| | **Jon** — technischer PO | **Paco** — fachlicher PO |
|---|---|---|
| wie viele | einer je Projekt, ein Chat für alle | einer je Nutzer und Projekt — Chat und Gedächtnis privat, Vorschläge öffentlich |
| tut | baut mit seinem Team, klärt auch technische Fragen | analysiert das Projekt, schärft das SOLL, schreibt Vorschläge |
| Fokus | SOLL == IST herstellen | **Grill me:** Vision, Ziele, fachlicher Mehrwert, was genau gebaut werden soll — inkl. GIVEN/WHEN/THEN, die das SOLL härten |
| schreibt | `docs/` (und lässt Code bauen) | nur `paco/<nutzer>/` im Projekt (Stories, Proposals) + eigenes Gedächtnis (R-PC-8) |
| Team | Plan · Dev · Review · Search | nur **Search** |

Paco ist ein **gewöhnlicher Agent** (id `paco`, Name „Paco", mit description) — keine
Sonderkonfiguration: sein Modell erbt den Default wie jeder Agent und ist wie jeder Agent
überschreibbar ([Agent-Dateien](../../org.sterl.llmpeon.core/docs/builtin-agent-prompt-tracked.md)).
Seine Vorschläge werden später importiert (vom Projekt-Jon oder einem Menschen) — der Import ist
**nicht** Teil dieses Features.

## Regeln

### R-PC-1 — Paco ist ein eigener Agent je Nutzer ❌

Paco ist **nicht** der Projekt-Jon: eigenes Gespräch, keine Delegation an Plan/Dev/Review, kein
Zugriff auf Jons Chat oder Memory. Paco hat eine **eigene Seite je Projekt** ([R-WP-1](ui.md));
beide Gespräche bleiben getrennt.

#### UC-PC-1 — pacoHasOwnConversation ❌
- GIVEN ein Projekt mit Jon-Gespräch WHEN der Nutzer die Paco-Seite öffnet THEN ist das Gespräch leer
  und enthält keine Nachricht von Jon — und umgekehrt.

#### UC-PC-2 — onePacoPerUser ❌
- GIVEN zwei Nutzer am selben Projekt WHEN beide Paco öffnen THEN hat jeder sein eigenes Gespräch;
  keiner sieht die Fragen des anderen.

### R-PC-2 — Paco liest, sucht, und schreibt nur in die eigenen Vorschlags-Ordner ❌

Werkzeuge: alles Lesende der Core-Familien (`disk*`-Read/Grep/Search, `web*`, `docs*`-Read) und als
**einziges** Agenten-Werkzeug den Such-Agenten (`searchAgent`). Schreibend ist **ausschließlich**
`<projekt>/paco/<nutzer>/` des **eigenen** Nutzers erlaubt (plus das eigene Gedächtnis, R-PC-8) —
durchgesetzte Schreib-Liste, nicht höfliche Bitte. Solange kein Nutzer bekannt ist (kein Login), ist
`<nutzer>` = `user`.

#### UC-PC-3 — pacoWriteAllowedOnlyInOwnFolders ❌
- GIVEN Paco von Nutzer `paul` WHEN er in `<projekt>/paco/paul/stories/…` oder
  `<projekt>/paco/paul/proposals/…` schreibt THEN gelingt der Write.

#### UC-PC-4 — pacoWriteOutsideOwnFoldersRejected ❌
- GIVEN Paco von Nutzer `paul` WHEN er im Quellcode, in `docs/`, im temporären Verzeichnis oder im
  Ordner eines anderen Nutzers (`paco/anna/…`) schreiben will THEN wird abgelehnt und die Ablehnung
  steht als Tool-Zeile im Gespräch.

#### UC-PC-5 — pacoHasOnlyTheSearchAgent ❌
- GIVEN Paco WHEN seine Tool-Liste gebaut wird THEN ist `searchAgent` enthalten und **kein** anderes
  Delegations-Werkzeug (kein `talkPlan`, `planWithPlanAgent`, `askDev`, `buildWithDev`,
  `reviewPlanAgent`) — er kann nichts bauen lassen.

### R-PC-3 — Story oder Proposal: zwei Arten, eine Datei je Vorschlag ❌

Paco ordnet jeden Vorschlag genau einer Art zu:

| Art | wofür | Ablage | Inhalt |
|---|---|---|---|
| **Story** | neuer Wunsch aus Nutzersicht | `paco/<nutzer>/stories/<JJJJ-MM-TT>-<kurztitel>.md` | Ziel, GIVEN/WHEN/THEN, betroffene Bereiche |
| **Proposal** | Änderung an Bestehendem (Regel, Ablauf) | `paco/<nutzer>/proposals/<JJJJ-MM-TT>-<kurztitel>.md` | IST / SOLL / WEIL, betroffene Regeln |

Ein Vorschlag je Datei. Eine neue Datei überschreibt nie eine bestehende (Namens-Suffix bei
Kollision). Paco schreibt **fortlaufend** wie Jon: die Datei entsteht mit dem ersten geklärten Punkt,
jede weitere Entscheidung landet sofort darin — nichts ist entworfen, das nur im Chat steht.

#### UC-PC-6 — newProposalNeverOverwrites ❌
- GIVEN Paco WHEN eine **neue** Story angelegt wird THEN entsteht eine neue Datei unter
  `paco/<nutzer>/stories/` mit dem Schema oben; eine bestehende Datei gleichen Namens bleibt unverändert,
  die neue bekommt ein Suffix. (Weiterarbeiten an einer eigenen Datei regelt R-PC-6.)

#### UC-PC-7 — twoUsersNeverCollideOnFileNames ❌
- GIVEN zwei Nutzer legen gleichzeitig eine Story mit demselben Kurztitel an THEN liegen zwei
  Dateien in den zwei Nutzer-Ordnern — keine überschreibt die andere.

#### UC-PC-11 — changeToExistingRuleBecomesProposal ❌
- GIVEN Paco WHEN der Nutzer eine bestehende Regel ändern will THEN entsteht
  `paco/<nutzer>/proposals/<datum>-<titel>.md` mit IST/SOLL/WEIL — keine Datei unter `stories/`.

### R-PC-6 — An eigenen Vorschlägen weiterarbeiten, fremde nur lesen ❌

Die Paco-Seite listet die Stories und Proposals des Projekts. Öffnet der Nutzer einen **eigenen**
Vorschlag, liest Paco die Datei und überarbeitet **genau diese Datei** mit ihm — keine Kopie.
Vorschläge anderer Nutzer sind lesbar, aber nie änderbar. Im MVP gibt es keine Nutzer: alle
Vorschläge liegen unter `user/` und sind damit für jeden eigene; die Trennung greift mit Login.

#### UC-PC-12 — continueOwnProposalInPlace ❌
- GIVEN Nutzer `paul` hat `paco/paul/proposals/2026-10-27-login.md` WHEN er sie auf der Paco-Seite öffnet
  und eine Änderung bespricht THEN ändert Paco genau diese Datei — es entsteht keine Kopie.
- GIVEN dieselbe Liste zeigt `paco/anna/proposals/…` WHEN `paul` sie öffnet THEN kann er sie lesen, ein
  Schreibversuch wird abgelehnt (R-PC-2).

### R-PC-4 — Ein Vorschlag ist konfliktfrei oder benennt seinen Konflikt ❌

Eine Story oder ein Proposal darf einer bestehenden Regel, einem Glossar-Eintrag oder einem ADR nicht
widersprechen, ohne es zu sagen. Widerspricht er, trägt er einen Abschnitt **Konflikt**: beide
Positionen (Regel/ADR und Vorschlag) und wie der Konflikt aufgelöst werden soll.

#### UC-PC-8 — storyWithoutConflictHasNoConflictSection ❌
- GIVEN eine Story, die keiner bestehenden Regel widerspricht THEN enthält sie keinen
  Konflikt-Abschnitt und verweist auf die Regeln, die sie erfüllt.

#### UC-PC-9 — contradictingStoryNamesBothSides ❌
- GIVEN eine Story, die einer bestehenden Regel widerspricht THEN enthält sie den Abschnitt
  *Konflikt* mit Regel-Verweis, Gegenposition und vorgeschlagener Auflösung.

### R-PC-5 — Stories und Proposals sind Vorschläge, kein Bestand ❌

Was unter `paco/` liegt, ist **nicht** Teil der Feature-Docs und verändert keine Regel. Geschrieben
werden darf dort nur, was später importiert werden soll. Stories und Proposals sind **öffentlich**:
sie liegen im Projekt, damit Jon und jeder Nutzer sie sehen, und Jon committet sie mit dem nächsten
Projekt-Commit seines Zyklus mit.

#### UC-PC-10 — storyIsNotADocRule ❌
- GIVEN eine fertige Story WHEN Jon das Feature-Doc liest THEN gilt weiterhin nur das, was in den
  Feature-Docs steht; die Story ist Eingabe, nicht Entscheidung.

### R-PC-7 — Paco hat einen eigenen Prompt mit eigener Rolle und Identität ❌

Pacos Prompt ist **eigenständig** (`prompts/paco.md` im Core — ein Pflegeort für Plugin und Web, im
Plugin ist Paco nur nicht aktiv). Die **Doc-Methode**, die Jon und Paco teilen, steht in **einem**
gemeinsamen Fragment `prompts/doc-method.md`, das **beide** laden — so bleibt Paco 100 % kompatibel
zu Jon, ohne Kopie. Jons `po.md` und `po-delegation.md` lädt Paco nicht.

Aufteilung (gleiche Symbole bei beiden — ein neues Symbol kennt sofort jeder):

| `doc-method.md` (Jon + Paco) | `paco.md` (Rolle & Identität) | nur Jon (`po.md`/`po-delegation.md`) |
|---|---|---|
| skeptischer Berater, nimmt nichts an · eine Frage pro Nachricht mit Empfehlung · IST/SOLL/WEIL · GIVEN/WHEN/THEN härten das SOLL · Konfliktcheck gegen Regeln, Glossar, ADRs · fortlaufend schreiben · Feature-Doc-Format · Symbole/Status-Marker | Abschnitt **Charakter** ganz oben (Ton, Auftreten — wie in `po.md`) · „Du bist Paco" — **fachliches** Design **eines** Nutzers · **Grill me** auf Vision, Ziele, fachlichen Mehrwert · beantwortet Fragen zum Projekt aus Docs und Code · Ergebnis ist eine Story oder ein Proposal (R-PC-3) | fachliches **und technisches** Design · `docs/` schreiben · `nextIds`/ID-Vergabe · ADRs · Architektur-Docs · Team steuern (Plan/Dev/Review) · Bauen |

#### UC-PC-13 — pacoPromptIsOwnPrompt ❌
- GIVEN Paco ohne eigene Agent-Datei WHEN sein System-Prompt gebaut wird THEN stammt er aus
  `doc-method.md` + `paco.md` und enthält weder `po.md` noch `po-delegation.md`.
- GIVEN Jon WHEN sein System-Prompt gebaut wird THEN enthält er `doc-method.md` genau einmal.
- GIVEN Pacos Agent-Datei mit Rumpf WHEN sein System-Prompt gebaut wird THEN ersetzt der Rumpf den
  eingebauten Prompt wie bei jedem Agenten ([Agent-Dateien](../../org.sterl.llmpeon.core/docs/builtin-agent-prompt-tracked.md)).

### R-PC-8 — Paco hat ein Gedächtnis je Nutzer ❌

Paco ist stateful, je Nutzer und Projekt zwei Teile:

- **Chat-Verlauf** — wie bei Jon gespeichert, je Nutzer getrennt.
- **`memory.md`** — von Paco selbst gepflegt: letzter Fokus, offene Punkte, nächster Schritt; kompakt,
  Erledigtes fliegt raus. **Privat:** liegt unter `config/paco/<nutzer>/<projekt-id>/memory.md` (nicht
  im Projekt; einzige Schreib-Erlaubnis außerhalb von `paco/<nutzer>/` im Projekt), wird beim Start
  **automatisch geladen** (nur die des eigenen Nutzers) und von der Fabrik stündlich und beim
  Herunterfahren committet ([R-CR-11](config-repo.md)). Überlebt damit jedes Compact und jeden Neustart.

Beim Start meldet Paco kurz letzten Fokus und offene Punkte und schlägt vor, dort weiterzumachen.
Fremde `memory.md` lädt er nie automatisch.

#### UC-PC-14 — pacoRemembersOnlyOwnUser ❌
- GIVEN `paul` und `anna` haben je eine Paco-`memory.md` am selben Projekt WHEN `paul` Paco startet
  THEN enthält Pacos Kontext Pauls `memory.md` und nicht Annas.
- GIVEN ein Nutzer ohne Stand WHEN er Paco startet THEN fragt Paco, worauf ihr euch fokussiert.

## Offene Punkte

| # | Punkt | Stand / Vorschlag |
|---|-------|-------------------|
| 1 | Import-Mechanik (wer holt Vorschläge in die Docs?) | ⏳ bewusst außerhalb dieses Features |
| 3 | Story-Sprache und -Schema fest vorgeben oder frei? | ⏳ angenommen: Schema aus R-PC-3, Formulierungen frei |
