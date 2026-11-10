# Review-Findings — Web Factory

Ein Block pro_reviewter Delta (neueste zuerst). Der Plan (`peon-plan/overview.md`) verweist nur hierher —
er wird mit jedem Inkrement überschrieben und darf Findings nicht behalten (Memory-Regel 12).

## Review-Findings N2 (Commit `4a607d11`, Branch `web-factory-5`)

**Verdict: REJECTED** (2 BUG, 0 MEDIUM, 2 LOW). Reviewed: Plan `peon-plan/overview.md`, Docs
`docs/chat.md` (R-WP-57/UC-WP-94, R-WP-48/UC-WP-84 — Arbeitsbaum, Jons Hand), `docs/aifactory-architektur.md`,
`docs/open-points.md`/`memory.md` (Kontext). Diff: `git show 4a607d11 --stat` = 3 Code-/Testdateien + Plan,
keine Nebenänderung.

**Tests / Gates (selbst gefahren, `node v22.23.4`, `pnpm 10.28.0`):** `cd factory-ui &&
COREPACK_ENABLE_PROJECT_SPEC=0 pnpm test` → **268 Tests / 29 Dateien grün**; `npx tsc -b` Exit 0; `npx oxlint`
**0/0** (98 Dateien); `npx antd lint ./src` **0** (55 Dateien); `lintDocsAndTests`
(docRoots `docs`, testRoots `factory-ui/test`,`factory-ui/src`,`factory-backend/src/test`) → **28 findings**,
**kein `UNBELEGT UC-WP-94`** (Rest `UC-CR-1..14`, `UC-PC-1..14`). Kein Test hinzugefügt/gelöscht — bestätigt.

### BUG

**B1 — Der Mount-Read bricht R-WP-48 wörtlich; der Test schreibt den verbotenen Zustand als Beweis fest.**
`docs/chat.md:622-627` (R-WP-48, ✅ gebaut+reviewed): „der Warte-Zustand … wird **nie** aus einem Schnappschuss
abgeleitet, der **vor** dem Verbinden aufgenommen wurde". Genau das tut `factory-ui/src/chat/chat.page.tsx:401`
(`void refreshTiles()`), und `factory-ui/test/chat/chat.read.page.test.tsx:100-102` behauptet es als Beweis:
`countOf('/agents','GET') === 1` **und** `tile-state-Jon` = `WORKING`, während `/events` noch unbeantwortet ist.
Evidenz: laufender Test (grün) + Code-Trace `chat.page.tsx:401` → `chat.board.ts:30-39` → `chat.tiles.tsx:29-35`.
Zusätzlich: der neue Doc-Text R-WP-57 (`docs/chat.md:651-659`) beschreibt den Vor-Verbindungs-Stand **nicht** —
nur der Plan (§11 R11, Zeilen 268-271) tut das, und er verschiebt die Schärfung ausdrücklich auf Jon. Damit steht
der Ausschluss „keine Behaviour, die die Docs nicht beschreiben" offen. Der Widerspruch ist in den Docs selbst:
`docs/aifactory-architektur.md:180-182` sagt „Frisch gelesen wird **beim Mount**/Reconnect", R-WP-48 sagt
„nie vor dem Verbinden". Reparatur = **Jons** Doc-Entscheidung (R-WP-48 auf „der frische Read muss ihn
zurücknehmen" schärfen) — nicht vom Dev, nicht von mir. Verletzt Checkliste 2 (Docs↔Code) + 4 (Plan-Konflikt).
**Erledigt 2026-11-10 durch Jon:** R-WP-48 (`docs/chat.md:622-630`) neu gefasst — erste Lesung = Live-Read, kein
veralteter Schnappschuss; der frische Read der stehenden Verbindung bleibt maßgeblich.

**B2 — Die Fehlerpfade des geänderten Codes sind unbewiesen; einer davon lässt einen Vor-Verbindungs-`WORKING`
stehen.** `docs/chat.md:666-667` (UC-WP-94 GIVEN 3) verlangt „Reihe leer, Fehler als Seiten-Fehler". Kein Test
bindet diese Zeile: der Stub kann `GET …/agents` nicht ablehnen (`chat.page.stub.ts:150-163` antwortet immer 200;
Refuse-Helfer gibt es nur für `POST`, `:232-241`). Code-Trace für den neuen, **jetzt erreichbaren** Fall:
Mount-Read antwortet `WORKING` (Turn läuft anderswo) → `chat.board.ts:36-38` setzt die Kacheln; der frische Read
auf `onOpen` scheitert → `catch → onFailure` (`chat.page.tsx:179`, Banner) und **nichts** räumt die Kacheln ab,
kein erneuter Read bis `done`/Aktion/Reconnect → der Vor-Verbindungs-`WORKING` bleibt stehen. Das ist genau der
Zustand, den R-WP-48 (`docs/chat.md:626-627`) „für immer als arbeitet" nennt — neu durch den Mount-Read, und
ungetestet. Billigste Reparatur: `refuseRosterRead(status, message)` im Stub (~4 Zeilen) + eine Assertion in
einem **bestehenden** Fehler-Test (`chat.page.test.tsx`), Testzahl bleibt damit 268. Plan-Abweichung D28 ist
sauber gemeldet (kein Plan-Loch) — aber die Doc-Zeile ist damit unbelegt und der neue Stale-Fall ungesehen.
Mitzählend: GIVEN 1 nennt „Antwort bleibt aus **oder schlägt fehl**" (`:662-663`); gestellt ist nur „bleibt aus"
(`deferNextStreamOpen`), ein scheiternder Stream-Open ist im Stub ebenfalls nicht baubar — verhaltensgleich
(A16), aber ungeprüft. Verletzt Checkliste 2 + 6.
**Stand N3:** GIVEN 3 ist von Jon umgefasst („zuletzt gelesener Stand bleibt stehen") und wird in N3 gebaut;
der scheiternde **Stream-Open** bleibt unbaubar ungeprüft (gemeldet, nicht Gegenstand von N3).

### LOW

- **L1** `chat.page.tsx:395-400`: der dreizeilige Kommentar wiederholt die Regel aus R-WP-57; eine Zeile mit
  `(R-WP-57, UC-WP-94)` + dem einen Grund („a stream that never opens may not leave the row empty") genügt
  (Checkliste 5). Die 4 Zeilen Zuwachs sind der Grund, warum die Datei von 589 auf 593 rutscht (Schwelle 600
  noch gehalten).
- **L2** `chat.page.tsx:82-84` („read once the view stands and again on every (re)connect") doppelt die
  Lesefolge-Beschreibung aus `chat.board.ts:6-19`; die Klasse braucht nur den Verweis (`.{@link useTileBoard}`),
  nicht die Wiederholung.

### Pre-existing (nicht blockierend, vom Diff nur berührt)

- `chat.tiles.tsx:29-35` gibt bei `tiles.length === 0` `null` zurück — R-WP-57 (`docs/chat.md:657-659`) sagt
  aber, eine leere Liste heiße im UI „Read nie angekommen"; es gibt kein sichtbares Signal dafür. Alt, nicht
  geändert.
- Ein Roster-Read-Fehler setzt `error` (`chat.page.tsx:179`); gelöscht wird das Banner nur auf Sende-/Stop-Erfolg
  (`:446`, `:495`, `:521`), ein später erfolgreicher Read räumt es nicht weg. Alt, durch den Mount-Read nur
  häufiger erreichbar.

### Plan conflict (Checkliste 4)

`docs/chat.md:620-627` (R-WP-48 ✅, „nie … vor dem Verbinden") **vs** Plan §1, Zeilen 40-46 („UC-WP-84 GIVEN 1
verlangt nicht ‚kein Read vor dem Verbinden'") und §11 R11 (Zeilen 268-271, Schärfung „Jons Seite"). Der Plan
urteilt über den Code nach seiner eigenen Auslegung der Regel; der Doc-Text sagt das Gegenteil, und er wurde in
diesem Inkrement nicht angefasst (`git status`: `docs/**` unverändert in Jons Hand). Die Docs sind das Maß →
B1. Auflösung nur durch Jon (Satz in R-WP-48 schärfen oder R-WP-57 zurücknehmen); ohne diese Entscheidung ist
kein Verdikt über das Inkrement möglich. **Aufgelöst 2026-11-10 (Jon, Docs).**

### Plan ↔ Code (Checkliste 3) — vollständig

§3 exakt umgesetzt: `void refreshTiles()` vor `refresh()` (`chat.page.tsx:401`), `onOpen` unverändert
(`:379`), D-E-Halbsatz entfernt (`:396-397`), `:377` umgeschrieben, JavaDoc `:82-84` gepflegt — **nur** diese
Hunks im Diff, `chat.board.ts`/`chat.stream.ts`/`chat.tiles.tsx`/`content.ts`/Stub unberührt. §4 (a)-(d)
umgesetzt (Alte-Polaritäts-Assertion ersetzt, nicht gelockt), (e)/(f) grün ohne Änderung. §6 Schritt 2-5 und
§12-Zahlen deckungsgleich mit meinen Messungen. **M8 nicht selbst nachgefahren** (verlangt Quell-Änderung);
Kopplung ist per Trace belegt: ohne `:401` wäre (a) 0 ≠ 1, (b)/(d) 1 ≠ 2 — die drei genannten Fälle müssen rot
werden.

### Skill/Instruction gap (10) / Mutation (9)

Keine. `AGENTS.md:8-10` (600 Zeilen) eingehalten (593); Mutation M8 ist gefahren und protokolliert — ein
zusätzlicher Mutationslauf bringt hier nichts, weil B2 zeigt, dass ein **negativer** Test fehlt (der ist der
billigere, dauerhafte Beweis).

**Most likely reason this breaks later:** der Vor-Verbindungs-Stand wird ohne Test als Wahrheit akzeptiert —
die nächste Kachel-Änderung (Runde-7-Look) baut auf ihm auf, und ein einziger fehlgeschlagener
`GET …/agents` lässt „arbeitet" wieder stehen. **Change that most reduces that risk:** Jons Schärfung von
R-WP-48 (Transient erlaubt, Rücknahme durch den frischen Read verlangt) + ein Stub-`refuseRosterRead` mit einer
Assertion im bestehenden Fehler-Test.
