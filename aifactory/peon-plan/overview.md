# Delta N3 — UC-WP-94 GIVEN 3: gescheiterter Roster-Read sagt warum, der zuletzt gelesene Stand bleibt stehen

**Status: GBAUT — Erstlauf auf dem Bestand grün (Stoppschild §5: kein Stopp), Bühnen-Beweis beider Hälften
gefahren, 268/29 grün, Lint 28 ohne `UC-WP-94` (§12). Ein Inkrement, nur `factory-ui`/Tests — kein
Produktionscode.**
Grundlage: Review von N2 (Commit `4a607d11`, Branch `web-factory-5`), Verdict REJECTED, Befunde B1/B2/L1/L2 —
Wortlaut in §13 dieses Plans (und nach Step 0 in `peon-plan/review-findings.md`). **Gebaut wird nur B2.**
B1 hat Jon in den Docs gelöst (`docs/chat.md:622-630` R-WP-48 neu gefasst; `:669-671` UC-WP-94 GIVEN 3 neu).
Regel/UC: `docs/chat.md` **R-WP-57 / UC-WP-94** — Status ❌ bleibt; **nur Jon** fragt danach neu ab / flippt.

## 0. Spielregeln (bindend, erster Schritt im Dev-Lauf)

- `git log --oneline -5` + `git status --porcelain`. Gearbeitet wird auf **`web-factory-5`** nach `4a607d11`.
- **Step 0 vor allem anderen:** §13 dieses Plans **unverändert** nach `peon-plan/review-findings.md` überführen
  (Datei neu anlegen, Inhalt = Review-Block, Titelzeile der Delta voran), dann in overview.md durch den Verweis
  `Review-Findings: peon-plan/review-findings.md` ersetzen. Grund: Memory-Regel 12 — Findings dürfen nicht nur
  in der Plan-Datei stehen, und der nächste Plan überschreibt sie. (Der Planner hat darauf keinen
  Schreibzugriff, siehe §15.) Commitbar: `aifactory/peon-plan/**`.
- Commitbar: `aifactory/factory-ui/**`, `aifactory/peon-plan/**`. **Niemals** `docs/**` (Jons Seite), nie
  `aifactory/pom.xml`, nie `main`, kein amend/rebase, kein Modell-/Tool-Name, kein `Assisted-by`-Trailer,
  **kein `git add -A`** — nur die Pfade aus §7.
- **Nur-UI-Änderung ⇒ nur UI-Tests.** Kein Maven. Kein Produktionscode, kein Behavior-Wechsel (§5 Stoppschild).
- UI-Gate (exakt): `cd factory-ui`, PATH-Export Node 22.12 (nvm `default`), **vor** dem Gate `node -v` (≥ 22) und
  `pnpm -v` belegen; `COREPACK_ENABLE_PROJECT_SPEC=0 pnpm test`; einzelne Fälle
  `pnpm test -t '<name>'` — **niemals** `pnpm test -- <datei>`; `npx tsc -b` 0; `npx oxlint` 0/0;
  `npx antd lint ./src` 0 findings.
- Seed/Rückweg nur über `cp`-Backup + `diff` (leer), **nie** `git checkout -- <datei>`; keine Datei aus dem
  Kontext neu schreiben (Regel 14) — Anker-Edits; `git diff` nur für §7.
- Keine neuen UI-Wörter, keine neuen IDs (UC-WP-94 ist gesetzt), keine neue `.ant-*`-Assertion, keine
  Farb-/Layout-Behauptungen (R1).
- **Jons Sparvorgabe:** **kein neuer Test, keine neue Test-Datei, keine neue Datei in `src/`** —
  Zahl bleibt **268 Tests / 29 Dateien**.

## 1. Befund → Entscheidung

| # | Befund (Review N2) | Entscheidung in N3 |
| --- | --- | --- |
| **B2** | Die Fehlerpfade des geänderten Codes sind unbewiesen: kein Test bindet UC-WP-94 GIVEN 3, weil der Stub `GET …/agents` nicht ablehnen kann (`chat.page.stub.ts` antwortet immer 200; Refuse-Helfer nur für `POST`). Ein scheiternder frischer Read nach `onOpen`/Reconnect ist damit ungesehen | **N3** — Stub-Helfer `refuseRosterRead(status, message)` (one-shot) + Beweis **im bestehenden Board-Test** `theReconnectReadsTheRosterFresh`; Produktionscode unverändert |
| B1 | (Doc-Widerspruch Mount-Read vs. R-WP-48) | **nicht gebaut** — von Jon in `docs/chat.md` gelöst (R-WP-48 `:622-630`: erste Lesung = Live-Read, frischer Read der stehenden Verbindung maßgeblich). B1-Szene `chat.read.page.test.tsx.aTurnEndingBeforeTheStreamStandsLeavesNoWaitingTile` bleibt **unangetastet** |
| L1/L2 | Kommentar-Duplikation `chat.page.tsx:395-400` und JavaDoc `:82-84`; Datei 589 → 593 (Schwelle 600) | **nicht gebaut** — N3 fasst `src/` nicht an. Vorgemerkt für ein reines Kommentar-Delta (Jons Entscheidung) |
| Pre-existing | Banner wird von einem erfolgreichen Read nicht gelöscht; leere Liste hat kein sichtbares Signal | **nicht gebaut**, **nicht behauptet** (§9: nach dem korrigierenden Read wird bewusst *nichts* ans Banner gefordert) |

**Warum dieser Wirt und kein neuer Test.** `theReconnectReadsTheRosterFresh`
(`test/chat/chat.read.page.test.tsx:111-128`) ist der bestehende Board-Test, in dem genau die geforderte Szene
schon steht: Mount-Read antwortet **WORKING** (`:112`, `:115` — der „zuletzt gelesene Stand"), und der frische
Read einer **stehenden Verbindung** (`onOpen` nach Reconnect, `chat.page.tsx:379`) ist der zweite Lesevorgang.
Es fehlt nur sein Scheitern. Alles andere (eigene Datei, zweiter Test) wäre ein 269. Test und gegen die
Sparvorgabe. **Verworfen:** Wirt `chat.page.test.tsx.stopOnAFinishedTurnIsSilent` (gleicher `catch`-Pfad, aber
der abgelehnte Read hinge am Stop statt an `onOpen` — Jons Szene nennt den frischen Read der Verbindung);
Wirt `clearOnATileNamesThatTileAndARefusalIsALine` (Reihe steht dort auf IDLE, „Stand bleibt" wäre nur schwach
beweisbar); neuer Helfer `acceptRosterRead()` (one-shot macht den Fehler alleinlesbar, kein zweiter Zustand).

**One-shot statt sticky.** `refuseRosterRead` lehnt **genau den nächsten** `GET …/agents` ab (Vorbild:
`chat.insight.test.tsx:141-143` `failTranscriptRead`, das ebenfalls abzählt). Nur so ist im selben Test die
zweite, *erfolgreiche* Lesung möglich — und die braucht der Doc-Halbsatz „die nächste Lesung … korrigiert ihn".

## 2. Was die Docs verlangen (maßgeblich, nicht nachgeschrieben)

- `docs/chat.md:669-671` (UC-WP-94 GIVEN 3, neu): scheiternder Roster-Read ⇒ **Seiten-Fehler** + **der zuletzt
  gelesene Stand der Reihe bleibt stehen** — kein Zurückfallen auf leer; die nächste Lesung (done, Stop, Aktion)
  korrigiert ihn.
- `docs/chat.md:622-630` (R-WP-48, neu gefasst): die erste Lesung ist ein Live-Read; der frische Read der
  stehenden Verbindung ist maßgeblich; ein Read vor einem `agents`-Schnappschuss überschreibt ihn nicht.

## 3. Architektur / Verantwortlichkeit (unverändert — das ist der Punkt)

- **`chat.board.ts`** entscheidet, *welcher Stand gewinnt* und **was ein Fehler mit dem Board tut**: `:30-37` —
  `claim` wird **vor** der Anfrage genommen, `setTiles` wird **nur im Erfolgszweig** gerufen, der
  `catch` ruft **ausschließlich** `onFailure`. Ein Fehler schreibt das Board also nie leer ⇒ der zuletzt
  gelesene Stand bleibt stehen. Genau das ist die neue GIVEN-3-Formulierung.
- **`chat.page.tsx`** entscheidet *wann* gelesen wird (`:401` Mount, `:379` `onOpen`, `:338` `done`,
  `:509` Stop, `:530` Aktion) und *was ein Fehler wird* (`:179 showBoardFailure` → `chat-error`-Banner).
- **Stub** ist Bühne, nicht Verhalten: er darf ablehnen, was der echte Server ablehnt (503 auf `GET …/agents`),
  aber keine neue Semantik erfinden.
- N3 führt **keine** Komponente, keinen Hook, keine Abstraktion ein. Datenfluss: `fetch → request()
  (nicht 2xx ⇒ ApiRequestError, `api.client.ts:12-14`) → refreshTiles().catch → onFailure(errorMessage)
  → setError → chat-error`. Board-Zweig: kein `setTiles`.

## 4. Der Bau (zwei Test-Dateien, Signatur-Ebene — kein Produktivcode)

### 4.1 `factory-ui/test/chat/chat.page.stub.ts` — Helfer `refuseRosterRead`

- `interface Chat` (bei `refuseActions` `:35-40`), eine Signatur + JavaDoc:

  ```ts
  /**
   * The next `GET …/agents` answers this instead of 200 — the roster read that fails while the board already
   * stands (UC-WP-94 GIVEN 3: said as a page failure, last read stand kept). Consumed by exactly that one read.
   */
  refuseRosterRead(status: number, message: string): void
  ```

- State neben `refusal`/`sendRefusal` (`:93-99`): `let rosterRefusal: { status: number; message: string } | undefined`
- **vor** der Held-Logik im GET-Zweig `:143-152` (Ablehnung gewinnt und verbraucht **keinen** gehaltenen Read):

  ```ts
        if (path.endsWith('/agents')) {
          if (rosterRefusal !== undefined) {
            const refused = rosterRefusal
            rosterRefusal = undefined
            return json(refused.status, { status: refused.status, error: 'unavailable', message: refused.message })
          }
          const [held, ...rest] = heldAgentReads
          …
  ```

- Helfer beim `refuseActions`-Eintrag (`:232-241`): `refuseRosterRead: (status, message) => { rosterRefusal = { status, message } },`

Körper ≈ 5 Zeilen (+ JavaDoc), exakt analog `refuseActions`; `json()` (`:263`) wird mitbenutzt. **Nichts** anderes
im Stub ändern (kein `refuseRosterRead` für `POST`, kein Umbau von `heldAgentReads`).

### 4.2 `factory-ui/test/chat/chat.read.page.test.tsx` — `theReconnectReadsTheRosterFresh` (`:111-128`)

Bestehender Kopf bleibt **wortgleich** (`:112-116`: WORKING-Roster, Mount+Flush, `tile-state-Jon` WORKING,
`rosterReads`), `setAgents([IDLE])` (`:120`) bleibt die Prämisse „der Turn endete ungesehen". Neu ist, dass die
**erste** frische Lesung danach ablehnt und die Korrektur an die **zweite** Lesung hängt:

```tsx
  // UC-WP-94 GIVEN 3: the fresh read of the standing connection fails. The page says why and the board keeps
  // the stand it read last — it never falls back to an empty row; the next reading is what corrects it
  chat.refuseRosterRead(503, ROSTER_FAILURE)
  await chat.closeStream()
  await waitFor(() => expect(chat.countOf('/events', 'GET')).toBe(2), { timeout: 1500 })
  expect(await screen.findByTestId('chat-error')).toHaveTextContent(ROSTER_FAILURE)
  expect(screen.getByTestId('chat-tiles'), 'no fall back to an empty row').toBeInTheDocument()
  expect(screen.getByTestId('tile-state-Jon'), 'the last read stand stays').toHaveTextContent(
    content.tiles.state.WORKING,
  )
  expect(chat.countOf('/agents', 'GET'), 'the refused read is the fresh read of the connection').toBe(
    rosterReads + 1,
  )

  // the positive replacement: the next reading lands, and the row moves with it (no second store, UC-WP-84)
  await chat.closeStream()
  await waitFor(() => expect(chat.countOf('/events', 'GET')).toBe(3), { timeout: 1500 })
  await waitFor(() =>
    expect(screen.getByTestId('tile-state-Jon')).toHaveTextContent(content.tiles.state.IDLE),
  )
  expect(chat.countOf('/agents', 'GET'), 'the next reading corrected the board').toBe(rosterReads + 2)
```

- `const ROSTER_FAILURE = 'the roster is not reachable'` **in diesem Test** (kein `content.ts`-Wort, kein Modul-Wort — der Wortlaut kommt aus dem Stub, nicht aus der UI).
- Die **beiden ursprünglichen Schluss-Assertions** (`waitFor(IDLE)` `:124-126`, `countOf === rosterReads + 1`
  `:127`) wandern wörtlich in den zweiten Block (Zähler `+ 2`, eigener Grund-Text); der erste Block erhält `+ 1`.
  UC-WP-84 GIVEN 2 bleibt damit **scharf** (eine Reconnect-Lesung bringt den frischen Stand auf die Reihe) —
  nichts wird gelockert oder gestrichen (Memory-Regel 4: eingrenzen **und** positiven Ersatz im selben Test).
- ID-Kommentare: `// UC-WP-84` bleibt, `// UC-WP-94` davor (Mehrzeilen-Muster existiert, z. B. `:488-489`).
  Testname bleibt (`theReconnectReadsTheRosterFresh` wird in Review/N1/N2-Texten referenziert; die ID-Zeilen
  machen die neue Abdeckung auffindbar).
- **Keine** Assertion an das Banner *nach* dem korrigierenden Read (es bleibt stehen — vorbestehendes Verhalten,
  nicht unsere Aussage), **keine** an `ChatWaiting`/Farben (R1).

## 5. Stoppschild (Jons „ganz wichtig") — Verhalten zuerst messen, dann entscheiden

Der erste Lauf des fertigen Tests **ist** die Messung gegen die neue GIVEN-3-Formulierung:

- **Erwartet GRÜN auf dem Bestand** (`chat.board.ts:30-37` schreibt im Fehlerfall kein `setTiles`;
  vorgeprüft, §3). Dann ist B2 geschlossen: Verhalten = Doc, Nachweis = Test.
- **STOPP und melden, wenn** der Test rot wird mit
  (a) fehlendem `tile-state-Jon` / fehlendem `chat-tiles` (Reihe fällt auf leer zurück),
  (b) fehlendem `chat-error` (Fehler wird verschluckt),
  (c) `IDLE` statt `WORKING` nach der abgelehnten Lesung, oder
  (d) `/events`- oder `/agents`-Zählern, die nicht aufgehen (Reconnect/Read folgt einer anderen Folge als hier
  beschrieben).
  In keinem dieser Fälle wird Produktionscode geändert, ein Test gelöst oder eine Erwartung „angepasst" —
  Report an Jon mit Fehlerwortlaut und Trace, Entscheidung bleibt bei ihm.

## 6. Reihenfolge und Rotation (rot → grün, ohne Produktionscode)

1. **Step 0** (§0): Findings nach `peon-plan/review-findings.md` überführen.
2. **Soll-Aussage messen:** §4 schreiben (Stub + Test).
   `pnpm test -t 'theReconnectReadsTheRosterFresh'` → **erwartet grün** (§5). Dieser grüne Erstlauf ist die
   Antwort auf „zeigt der Code die neue GIVEN-3-Form exakt?" und wird im Report protokolliert.
   (Eine Rot-vor-grün-Stufe an Produktionscode gibt es hier bewusst **nicht**: N3 ändert kein Verhalten —
   der negative Test gegen die neu gefasste Doc-Zeile ist der Beweis.)
3. **Bühnen-Beweis (ersetzt die Mutation, wie von Jon gesetzt):** `cp`-Backup der Testdatei; in einer
   **geseedeten** Variante den Aufruf `chat.refuseRosterRead(503, ROSTER_FAILURE)` **weglassen** — die frische
   Lesung antwortet dann 200 mit `IDLE`. Lauf → erwartet **rot, beide Hälften**:
   `tile-state-Jon` erwartet `WORKING`, erhalten `Idle`, **und** `Unable to find [data-testid="chat-error"]`.
   Zurück per `cp`, `diff` gegen das Backup **leer**, Backup löschen. Beweist: die Assertions beißen an der
   Ablehnung und nicht an der Fixture.
4. **Gates:** `COREPACK_ENABLE_PROJECT_SPEC=0 pnpm test` → **268 Tests / 29 Dateien** (Zahl unverändert),
   `npx tsc -b` Exit 0, `npx oxlint` 0/0, `npx antd lint ./src` 0 findings.
5. **Doc-/ID-Lint:** `lintDocsAndTests` mit `docRoots: ["docs"]`,
   `testRoots: ["factory-ui/test","factory-ui/src","factory-backend/src/test"]`, **ohne** `idPattern`.
   Erwartung: **28 findings**, `UC-WP-94` **nicht** auf der Liste (Rest `UC-CR-1..14`, `UC-PC-1..14`).
6. **Commit + Push** auf `web-factory-5`, nur §7-Pfade, Message ohne AI-Namen, mit UC-WP-94, z. B.:
   `UC-WP-94: a refused roster read is said and keeps the last read stand (UI test + stub)` —
   `peon-plan/overview.md` + `review-findings.md` im selben oder einem zweiten Commit.
7. **Jon** fragt danach neu ab (Doc-Flip ❌ → ✅ und Browser-Abnahme N2-A8 bleiben bei ihm).

## 7. Betroffene Dateien

- neu: `aifactory/peon-plan/review-findings.md` (Step 0, Inhalt = §13 unverändert)
- ändern: `factory-ui/test/chat/chat.page.stub.ts` (Signatur + State + GET-Zweig + Helfer),
  `factory-ui/test/chat/chat.read.page.test.tsx` (nur `theReconnectReadsTheRosterFresh` `:111-128`),
  `peon-plan/overview.md` (§12 Messergebnisse; Findings-Block durch Verweis ersetzt)
- **nicht** geändert: **alles** unter `factory-ui/src/**` (insb. `chat.board.ts`, `chat.page.tsx`, `content.ts`,
  `chat.tiles.tsx`), `chat.page.test.tsx`, `chat.read.page.test.tsx` im Übrigen (B1-Szene `:86-108`
  unangetastet), `app.harness.tsx`, `docs/**` (inkl. `memory.md`, `open-points.md`), `AGENTS*.md`,
  `.agents/**`, `factory-backend/**`, `aifactory/pom.xml`

## 8. Regeln & Grenzen

- Test-Änderungen nur per Anker-Edit; vor dem Testlauf `git diff` gegen §7 zeigen (Regel 14).
- Der Stub lehnt ab, was ein Server ablehnen kann (Status + `message`-Body wie `refuseActions`); er erfindet
  kein Protokoll, keine Zeit, keinen Retry.
- One-shot-Semantik ist Teil der Bühne und wird im JavaDoc genannt (Consumed by exactly that one read).
- `chat.read.page.test.tsx` wächst um ≈ 12 Zeilen (Bestand ≈ 535) — unter der 600er-Schwelle (AGENTS.md:8-10);
  kein zweiter Test, keine neue Datei.
- Timelines: die zwei `waitFor(..., { timeout: 1500 })` folgen dem bestehenden Muster (`:123`); keine
  `sleep`-Erfindung, keine neuen Timer (RECONNECT_MS = 500 bleibt).

## 9. Abnahmekriterien (jede Zeile ein benannter, laufbarer Test)

| # | GIVEN | WHEN | THEN | Nachweis |
| --- | --- | --- | --- | --- |
| **N3-A1** (UC-WP-94 GIVEN 3, Seiten-Fehler) | Reihe steht auf WORKING (Mount-Read), der frische Read der wiederstehenden Verbindung antwortet 503 mit `ROSTER_FAILURE` | Reconnect durchläuft | `chat-error` nennt **wörtlich** `ROSTER_FAILURE` | `chat.read.page.test.tsx.theReconnectReadsTheRosterFresh` (erweiterter Block 1) |
| **N3-A2** (UC-WP-94 GIVEN 3, Stand bleibt) | dito | View zeichnet | `chat-tiles` steht, `tile-state-Jon` weiter **WORKING** — kein Zurückfallen auf leer | derselbe Block (Assertionen 2 und 3) |
| **N3-A3** (keine Jagd nach dem Fehler) | dito | nach dem Fehler | genau **ein** Roster-Read mehr (`rosterReads + 1`): die abgelehnte Lesung *ist* der frische Read | derselbe Block (Zähler-Assertion) |
| **N3-A4** (Doc-Halbsatz „nächste Lesung korrigiert"; UC-WP-84 GIVEN 2 bleibt scharf) | Stand WORKING, die abgelehnte Lesung ist verbraucht | zweiter Reconnect liest frisch (Roster jetzt IDLE) | `tile-state-Jon` wechselt auf **IDLE**, Roster-Reads `rosterReads + 2` | derselbe Block (Schluss-Assertions, wörtlich aus `:124-127` übernommen) |
| **N3-A5** (Biss der Assertions) | seeded: `refuseRosterRead`-Aufruf entfernt (frische Lesung antwortet 200 IDLE) | Test laufen | **rot, beide Hälften** (`WORKING`≠`Idle`, `chat-error` fehlt) | §6 Schritt 3, Protokoll in §12 |
| **N3-A6** (Bestand) | Suite + Statische | Gates | **268** Tests / 29 Dateien grün (unverändert), `tsc -b` 0, `oxlint` 0/0, `antd lint` 0, `lintDocsAndTests` 28 ohne `UC-WP-94` | §6 Schritt 4-5 |
| **N3-A7** (B1-Szene unberührt) | — | Diff prüfen | `aTurnEndingBeforeTheStreamStandsLeavesNoWaitingTile` (`:86-108`) unverändert; kein `src/`-Hunk | `git diff` gegen §7 |
| **N3-A8** (Verhalten = Doc) | erster Lauf auf dem Bestand | — | **grün** wie §5 erwartet; andernfalls STOPP + Report | §6 Schritt 2 |
| N3-A9 | Pauls Fall im Browser (Server neu gestartet, Reihe steht, ein Read scheitert) | Chat offen | Banner + Reihe bleibt sichtbar | **Jon (R1)** — kein Test, nur Jon fragt danach neu ab |

## 10. Annahmen

- **A18** — Head nach `4a607d11` auf `web-factory-5` trägt keine weiteren Änderungen an
  `chat.read.page.test.tsx`/`chat.page.stub.ts` (Dev prüft per `git log`/`git status`).
- **A19** — `refuseRosterRead` wird nur von diesem einen Test benutzt; kein anderer Test ruft ihn auf, alle
  übrigen `/agents`-Antworten bleiben unverändert (grep vor dem Commit: eine Aufrufstelle).
- **A20** — der Reconnect-Lesepfad ist unverändert (`onEnd(true)` → `refresh()` + `setTimeout(connect, 500)`,
  `onOpen` → `refreshTiles()`), deshalb ist die abgelehnte Lesung deterministisch die des neuen Streams;
  zwischen `closeStream()` und `onOpen` liegt kein weiterer Roster-Read.
- **A21** — `request()` wirft bei 503 mit `apiError.message` (`api.client.ts:12-14`), `errorMessage` liefert den
  Server-Wortlaut (`:51-56`) — deshalb darf der Test den Stub-Wortlaut wörtlich erwarten.

## 11. Risiken

- **R14 (Zeit/Zwei-Reconnect-Test)** — der Wirt fährt jetzt zwei Reconnects (≈ 1 s im ungünstigsten Fall).
  Gegenmaßnahme: ausschließlich `waitFor` mit 1500 ms wie im Bestand, keine fixen Sleeps. Wird er flatterhaft,
  ist die **Alternative entschieden**: Korrektur über `chat-stop` (Read über `chat.page.tsx:509`) statt zweiter
  Reconnect — dann bleibt UC-WP-84 GIVEN 2 beim abgelehnten Read nur durch den Zähler belegt; in diesem Fall
  **vor** der Umstellung an Jon melden, nicht still umbauen.
- **R15 (Banner bleibt stehen)** — das Banner löscht erst Sende-/Stop-/Aktionserfolg (`chat.page.tsx:446/:495/:521`),
  ein erfolgreicher Read räumt es nicht (Review-Befund, vorbestehend). Deshalb **keine** N3-Assertion, die ein
  Verschwinden behauptet; R15 bleibt als bekannter Zustand stehen, bis Jon entscheidet.
- **R16 (einseitige Bühne)** — der Stub lehnt ab, der echte Server kann auch hängen; der hängende Fall ist
  durch `deferNextAgentsRead` bereits anderweitig abgedeckt (`aStaleRosterAnswerNeverOverwritesTheEndSnapshot`).
- **R1** bleibt: Look/Verhalten am Browser nimmt Jon ab (N3-A9).

## 12. Ergebnis (Dev, gebaut 2026-11-10)

- **Step 0 erledigt:** `peon-plan/review-findings.md` **neu angelegt** — Inhalt = der N2-Review-Block
  (Commit `4a607d11`) **unverändert**, eine Titelzeile vorangestellt; §13 dieses Plans ist durch den Verweis
  `Review-Findings: peon-plan/review-findings.md` ersetzt. **Werkzeug-Lücke gemeldet:** die Edit-Werkzeuge
  können keinen Zeilenbereich löschen (nur Ersetzen/Setzen), der 97-Zeilen-Block war darüber nicht entfernen —
  Löschung mit `sed -i '' '261,357d'` nach `cp`-Backup, `diff` gegen das Backup zeigte **nur** den gelöschten
  Block, die neue §13-Sektion kam per Anker-Edit vor §14. (Ein Range-Delete im Workspace-Editor wäre das, was
  der Dev hier brauchen würde.)
- **Erstlauf auf dem Bestand (N3-A8 / Stoppschild §5): GRÜN — kein Stopp, kein Produktionscode.** Der
  gescheiterte frische Read der wiederstehenden Verbindung zeigt genau die neue GIVEN-3-Form: Banner
  **wörtlich** `the roster is not reachable`, `chat-tiles` steht, `tile-state-Jon` bleibt `working`,
  `/agents` = `rosterReads + 1` (kein Jagd-Read); zweiter Reconnect → `ready`, `/agents` = `rosterReads + 2`
  (UC-WP-84 GIVEN 2 bleibt scharf). 3× wiederholt, jedes Mal grün (Test ≈ 0,2 s) — R14 (Zwei-Reconnect-Zeit)
  flattert nicht. Keine Abweichung gegen §5: weder (a) leere Reihe noch (b) verschluckter Fehler noch (c) `IDLE`
  statt `WORKING` noch (d) falsche Zähler.
- **Bühnen-Beweis statt Mutation (§6 Schritt 3, N3-A5):** Backup per `cp`, dann
  (1) `chat.refuseRosterRead(503, ROSTER_FAILURE)` entfernt → **rot**:
  `Unable to find [data-testid="chat-error"]` (`findByTestId`, `chat.read.page.test.tsx:137`) — die Hälfte
  „Fehler wird gesagt" beißt an der Ablehnung;
  (2) zusätzlich die Banner-Assertion entfernt, um die zweite Hälfte überhaupt zu erreichen → **rot**:
  `the last read stand stays` — *Expected `working`, Received `ready`* (`:138`) — die Hälfte „Stand bleibt"
  beißt ebenfalls an der Ablehnung, nicht an der Fixture. Zurück per `cp`, `diff` gegen das Backup **leer**,
  Backup gelöscht.
- **Zahlen:** `pnpm test` → **268 Tests / 29 Dateien** grün (unverändert, kein Test hinzu/weg),
  `npx tsc -b` Exit 0, `npx oxlint` **0/0** (98 Dateien), `npx antd lint ./src` **0 findings** (55 Dateien),
  `lintDocsAndTests` → **28 findings**, **`UC-WP-94` nicht auf der Liste** (Rest `UC-CR-1..14`, `UC-PC-1..14`;
  test IDs 446 → **447** — die neue ID-Zeile, keine neue ID-Erfindung).
- **Größen / Grenzen:** `chat.read.page.test.tsx` 539 → **557** Zeilen (+18, Schwelle 600 gehalten),
  `chat.page.stub.ts` 353 → 370. **Kein `src/`-Hunk** im Diff (N3-A7); B1-Szene
  `aTurnEndingBeforeTheStreamStandsLeavesNoWaitingTile` (`:87-118`) unangetastet; `refuseRosterRead` hat genau
  **eine** Aufrufstelle (A19) — `chat.read.page.test.tsx:132` — plus Signatur und Implementierung im Stub.
- **Diff / Commit:** `git diff --stat` = `factory-ui/test/chat/chat.page.stub.ts`,
  `factory-ui/test/chat/chat.read.page.test.tsx`, `peon-plan/overview.md`, neu `peon-plan/review-findings.md`
  — genau §7, `docs/**` unangetastet (Jons Hand, unstageändert gelassen).
  **Commit:** siehe Git-Log auf `web-factory-5` (`UC-WP-94: a refused roster read is said and keeps the last
  read stand …`); **Push auf `origin/web-factory-5`:** gefahren.
- **Offen bei Jon:** N3-A9 / N2-A8 (Browser-Abnahme), Doc-Flip ❌ → ✅, L1/L2 (Kommentar-Delta in `src/`),
  R15 (Banner räumt ein erfolgreicher Read nicht), der unbaubar bleibende **Stream-Open**-Fehler.

## 13. Review-Findings

Review-Findings: `peon-plan/review-findings.md` — N2 (Commit `4a607d11`, Branch `web-factory-5`, Verdict
REJECTED, B1/B2/L1/L2). Step 0 (2026-11-10) hat den Block **unverändert** dorthin überführt; dieser Plan hält
Findings nicht mehr, weil der nächste Plan ihn überschreibt (Memory-Regel 12). Gefallen sind B1 (Jons Docs) und
B2 (dieses Inkrement N3); L1/L2 und die vorbestehenden Punkte stehen unverändert dort.

## 14. N2-Kurzfassung (das Nötige zum Lesen; Einzelheiten: `git log -- peon-plan/overview.md`, Commit `4a607d11`)

Gebaut 2026-11-10: `void refreshTiles()` beim Mount (`chat.page.tsx:401`), `onOpen` unverändert; drei Tests
rot→grün (`aTurnEndingBeforeTheStreamStandsLeavesNoWaitingTile`, `aStaleRosterAnswerNeverOverwritesTheEndSnapshot`,
`clearOnATileNamesThatTileAndARefusalIsALine`), M8 gefahren (ohne die Zeile: dieselben drei rot, gleicher
Wortlaut), 268/29 grün, `tsc`/`oxlint`/`antd lint` 0, `lintDocsAndTests` 28 ohne `UC-WP-94`.
**Commit-Biologie bestätigt:** `e72c6c76` „R-WP-48: the roster is read once the stream stands, not beside the
mount (UC-WP-84)" hat den Mount-Read durch den `onOpen`-Read ersetzt; `76d3e653` (N1) ergänzte den Stale-Fall;
`742293c3` unbeteiligt. Offen bei Jon: Browser-Abnahme N2-A8, Doc-Flip ❌ → ✅, L1/L2 (Kommentar-Delta).

## 15. Was der Planner nicht konnte (notiert für Jon)

Kein Schreibzugriff auf andere Dateien als `peon-plan/overview.md`: **`peon-plan/review-findings.md` konnte ich
nicht anlegen.** Die N2-Findings stehen deshalb unverändert in §13 dieses Plans, und Step 0 (§0) überträgt sie
dorthin — bitte Jon oder der Dev (mit Schreibzugriff) vor dem nächsten Planüberschreiben.
**Erledigt 2026-11-10 (Dev, Step 0):** `peon-plan/review-findings.md` angelegt (Findings unverändert), §13 durch
den Verweis ersetzt.
