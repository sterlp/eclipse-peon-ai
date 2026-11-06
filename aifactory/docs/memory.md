# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Paul committet selbst (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** (2026-10-29) — gilt auch für Core-Tests.

## Zyklus 2026-11-02 LÄUFT — UI-Nacharbeiten nach Pauls Smoke-Test

Branch `web-factory-mvp`, Spitze `cf8133ec` (= origin). **Runde 2 abgeschlossen + Review bestanden**
(2026-11-02): alle 7 Inkremente + Follow-up gebaut — Inc 1 `321cb3ea` (Turn-Fehler = Verlaufszeile,
R-WP-34/UC-WP-64) · Inc 2 `6027bbe4` (Warte-Rahmen endet, R-WP-31/UC-WP-65) · Inc 3 `72e97e6d`
(Chat scrollt, Eingabe fixed, R-WP-35/UC-WP-66) · Inc 4 `99560a9b` (Akzent links, R-WP-29/UC-WP-55) ·
Inc 5 `f069dd17` (Files-Tab bündig, Zeilenhöhe — Shiki-`\n`-Knoten, R-WP-33/UC-WP-60/61) · Inc 6
`7cc5b54f` (Think je Agent nach Provider-Form, R-WP-23/UC-WP-67) · Inc 7 `e682e7cc` (Config-Look,
Cards) · Review-Followup `cf8133ec` (UC-WP-64 GIVEN 2 gepinnt, `ranTurn` gepinnt, Historien-Warten
ein Helfer, Marker). Stand: Backend **208/0/0**, UI **129 Tests/16 Dateien**, oxlint 0/0,
UC **67/67**, Doc-Lint 0 Findings (250 Ids). Alle 10 Feedback-Punkte verarbeitet. Dev-Slot-Lücke ⏳.
Trailer-Regel: kein Modellname.

## Zyklus 2026-11-06 — Runde 4: AntD-Umbau (Paul freigegeben, autonom)

**Paul seit 12:05 wieder erreichbar** (Fragen wieder möglich). Backlog und Reihenfolge stehen in
[open-points.md](open-points.md) („Runde 3 — AntD-Umbau der Oberfläche").
**Inkrement 1 (AntD-Basis) ✅ abgenommen** — [R-WP-39/UC-WP-71](ui.md): AntD **6.7.0** gepinnt, Bootstrap
raus (Wache sieht CSS-Import, `.css`, `devDependencies`), **eine** Dunkel-Tabelle via `data-theme`,
`AppRoot` selbst gemountet bewiesen. **Inkrement 2 (Markdown) gebaut + nachgearbeitet** —
[R-WP-28/UC-WP-73](chat.md) ✅: `react-markdown@10.1` + `remark-gfm` + `remark-breaks`, `markstream` raus;
Review **REJECTED** (1 BUG: falsche Memoisierungs-Begründung · LOWs: modell-gemaltes `hr`, breite Tabelle
ohne Scroll, Wortlaut nur einteilig bewiesen) → **2-N1**closed (Antwort-`hr` als **unsere** kurze Linie im
Antwortblock, `div.chat-markdown-table` = Scroll-Kasten [`ANTD-A6`], `separatorsOf(log)` als **einzige**
Trenner-Definition für Inc 4, `onlyAnswersAreMarkdown` dreiteilig) · **2-N2**: `AGENTS-DEV.md`
1031 → **225** Zeilen, episodisches Memory nach [`peon-plan/dev-notes-antd.md`](../peon-plan/dev-notes-antd.md)
(915 Zeilen) — **Pauls Regel: `AGENTS*.md` ist kein Memory, Plan-Dateien sind das Memory.**
Commits Inc 1/2 auf `web-factory-mvp-2`: `cad7f0e6` · `77b33213` · `49f6a7b6` · `6d7a4bba` · `06a53021`
(2-N1) · `ed3dc535` (**Pauls** „CallStats listener", SHA gehalten) · `5ad3a69b` · `af977185` (Docs) ·
`f21f9f1a` (2-N2).

**Inkrement 3 (Eingabe) ✅ abgenommen (2026-11-06)** — [R-WP-41/UC-WP-74](chat.md) und
[R-WP-30/UC-WP-56](chat.md) ✅: `Input.TextArea autoSize` mit `MAX_ROWS = 5` (Höhe ist in jsdom nicht
behauptbar → Konstante + Form, Optik = Pauls Smoke-Test), **Enter sendet**, **Shift+Enter und
Ctrl/Cmd+Enter** setzen die neue Zeile, Send nur bei Text, Fokus bleibt beim Feld
(`typingElsewhere()`-Guard). Review **REJECTED** → alle Funde closed: Umbruch **am Caret** (mitten im Text
wie bei Auswahl, Mutationen M12/M13), AntD-Wache **fail-closed** (Bann = ganz `src/chat/`, Allowlist allein
`chat.composer.tsx`, M14 beweist eine neue Verlaufs-Datei), Seitentest schließt die Kette über echten
Ctrl+Enter, WHY-Kommentar an der doppelten Sende-Prüfung. Commits `8f6fe18c` · `8284d09d` · `6bad9074` ·
`0945f1a2` = Spitze = origin. Stand UI **171 Tests/20 Dateien ~8,7 s**, tsc/oxlint 0/0.
**`AGENTS-DEV.md` neu gefasst: 117 Zeilen, nur Zeitloses** (Paul: Plan-Status hat dort nichts zu suchen;
Core-`developer.md:55-60` von Paul nachgezogen — „episodic goes to the plan file"). Pauls Umzug hat den
alten Stand als **Dump in [`peon-plan/overview.md`](../peon-plan/overview.md) ab :447** abgelegt =
Doppelbestand; wartet auf sein Wort, dann raus. **Doc-Lint: 31 Findings** (PC 14, CR 14, UC-WP-70/72/75 —
UC-WP-56/74 sind raus). **Immer selbst messen**: Dev und Review melden 3, ihr Lint scopet nur `chat`/`ui`.

**Entschieden (2026-11-06):** AntD wird Design-Basis, **Bootstrap raus** ([R-WP-39](ui.md));
Navigation = **Projektbaum im Sider**, Klick aufs Projekt öffnet den Chat, **kein „Jon"-Label**
([R-WP-38](ui.md), ⏳ ob auch die Kachel umbenannt werden soll); **kein `hr`** zwischen Zeilen
([R-WP-29/UC-WP-55](chat.md) deshalb wieder ❌); Timeline für Werkzeuge/Delegationen
([R-WP-40/UC-WP-72](chat.md)); Markdown muss GFM + einfache Umbrüche können
([R-WP-28/UC-WP-73](chat.md), `markstream` hält nicht: CSS nie importiert, kein `breaks`);
Eingabe Auto-Grow/Refocus ([R-WP-41/UC-WP-74](chat.md)); **Zeiten bleiben live** (kein zweiter
Speicher, kein Text-Parsing). Grenze: [AF-ADR-003](adr/003-reference-ui-is-a-donor-not-a-source.md) —
die Referenz `ai-schulung` gibt Muster, **nie** unseren Zustand (C-5-Fix bleibt grün!).

**Konflikt entschieden (2026-11-06, Paul „A"):** Tastatur — **Enter sendet, Shift+Enter = neue Zeile**;
[R-WP-30/UC-WP-56](chat.md) umgeschrieben und auf ❌ gesetzt (Umbau in Inkrement 3), Merkposten gegen
Wiederaufmachen in [resolved-points.md](resolved-points.md). Paul zusätzlich: „es gibt **kein Lock vom
Input**" (bestätigt [R-WP-41](chat.md)). **`!` geklärt (2026-11-06, Paul: „sollte implementiert sein" → im Core nachgelesen):** die
Prio-Nachricht ist **Core-Bestand**
([`queued-user-messages.md`](../../org.sterl.llmpeon.core/docs/queued-user-messages.md) R1/R2/R8/R11:
`!` am Anfang **nur** im laufenden Turn → Urgent-Lane, dort gestrippt, kein Batching, beim nächsten
Tool-Call als **ein** `queued_user_message`-Block am Tool-Ergebnis; ohne laufenden Turn normaler Text).
**Die Fabrik reicht `!` noch nicht durch** (`ProjectRuntime.submit()` → Rohtext an `call()`) — SOLL als
[R-WP-42/UC-WP-75](chat.md) ❌, Bau in **Inkrement 3b**. **Tastatur nach Pauls Klarstellung:** Enter =
senden, **`Shift+Enter` UND `Ctrl/Cmd+Enter` = neue Zeile** ([R-WP-30/UC-WP-56](chat.md) ❌, Bau in
Inkrement 3). **Glossary angelegt** (2026-11-06, Paul):
[glossary.md](glossary.md) mit **Jon** / **Paco**, verlinkt aus [index.md](index.md) und
[product-vision.md](product-vision.md); Begriffe, die über ein Doc hinausreichen, stehen ab dort.

**Review zu Inc 1 = REJECTED** (4 Beweis-BUG: Wache sah den CSS-Import nicht · zwei Dunkel-Tabellen,
`theme.ts` las die ungenutzte `[data-theme='dark']`-Kopie · `sendThinking` nur im Label bewiesen ·
`AppRoot`/`ConfigProvider` von keinem Test gerendert) — alle vier geschlossen, je mit Mutation und
getroffener Datei (8 Sonden, keine blieb grün). **Merksatz für Reviews:** eine Mutation, die grün
bleibt, ist ein Befund — nicht der Test ist zu leise. `matchMedia`-Polyfill **bleibt** (gemessen:
jsdom 26.1.0 liefert die Property als `undefined`; Inc-1-Behauptung „nicht load-bearing" war falsch).

**Nächste Schritte:** **Inc 3b** `!` durchreichen ([R-WP-42/UC-WP-75](chat.md), Backend:
`ProjectRuntime.urgentTextOf` + Urgent-Lane, kein zweiter Turn) → Inc 4 Timeline + kein `hr`
([R-WP-40/UC-WP-72](chat.md), hebt F3 zum Teil) → Inc 5a Sider + URL (C-3, bringt `aria-current` mit Test
zurück) → 5b Files-Unit. Danach Rest-Backlog: C-7 (Agent-Blick read-only live), C-2a/C-1, F8/F10.
Stagehand ⏳ parked (Node ≥22.18 vs. Bau 22.12). **Nach Inc 3 gilt für die Wache:** neuer Datei im
Chat-Ordner mit AntD-Import ist rot — Inc 4 (Timeline) muss ihr CSS selbst malen.
([R-WP-41/UC-WP-74](chat.md)) — **wartet auf Pauls Tastatur-Entscheidung** (⏳ Enter-Konflikt unten) →
Inc 4 Timeline + kein `hr` (hebt F3 zum Teil) → Inc 5a Sider + URL (C-3, bringt `aria-current` mit
Test zurück) → 5b Files-Unit. Danach Rest-Backlog:
C-7 (Agent-Blick read-only live), C-2a/C-1, F8/F10. Stagehand ⏳ parked (Node ≥22.18 vs. Bau 22.12).

## Archivierte Stationen dieser Runde

**✅ F6/F7 abgeschlossen:** 23 → **1** Spring-Kontext, Wanduhr **118 → 42,1 s**, 216 Tests/0, null
Produktionscode. Commits `c47c4aa7`…`30c42754` auf `web-factory-mvp`. Regel + Messgröße in
[AGENTS.md](../AGENTS.md), Fakten in `AGENTS-DEV.md`; Guard `SingleContextGuardTest`, Lifecycle-Netz
120 s, TurnGate statt Schlaf, ein Home `target/test-home`.

**✅ C-4 Diffs gebaut + reviewt:** [R-WP-36/UC-WP-68](chat.md) ✅. Commits `32bb05fd`/`d7ca61b3`/`5e0956dd`.
Core-Erweiterung von Paul (`2cac0d6f` auf main): Anlegen = `+`-Diff, `SimpleDiff`-Cap 500 Changed-Zeilen
partziell statt Skip, Binary → Werkzeug-Zeile; Fabrik-Test `49571dc4` (Backend 222/0, missCount 1).
Entscheidung in [resolved-points.md](resolved-points.md).

**✅ C-5 Verlauf out of order — abgeschlossen (2026-11-05), R-WP-37/UC-WP-69 ✅:** Branch
`web-factory-mvp-2`, Inc 1 `195b149d` (Anker je Turn eingefroren, Stream nach erstem Read) · Inc 2
`a3da4b01` (Read-Sequenz + `mark`: veraltete Reads verworfen, nur Items `< mark` aufs Verlaufs-Target) ·
Inc 3 `8360d27e` (Bestätigung über Position statt Text) · **Review REJECTED** → Paul entschieden
(Fälle 1–6 einzeln per GIVEN/WHEN bestätigt) → **Inc 4 `08eefdde`** (Wasserlinie **je Absender**,
zustandslos; Bündel-Fall: eine Verlaufszeile bestätigt alle vor ihr gesendeten vorläufigen Zeilen
desselben Absenders; `senderName()` eine Normierungsstelle; ChatLog zeichnet `origin ?? you`, Prop
`user` entfällt) · Docs-Flip `702993b5`, Plan archiviert+gelöscht `d94784ad`. UI **149 Tests/18
Dateien**, tsc/oxlint 0/0, UC-WP 69/69 belegt. Geschärft: `user.md` R-WP-25 — „You" = Anzeige des
anonymen Nutzers, **kein Präfix an Jon**, namenlose Eingaben = eine Absenderklasse.

**Offen Runde 3 (Backlog in [open-points.md](open-points.md)):** F3 Kacheln · F5 Optik/Config ·
F8 Config-Umfang (reasoning_effort, Header) · F9 Clear-Layout · F10 Bootstrap docs-Laden ·
C-1 TurnClaim-Timeout · C-2 Compact-Anzeige · C-3 URL-Hash · C-7 Agent-Chat = Haupt-Widget
read-only (**nächster**, nach C-5). C-6 🔒 (hr raus/Linien in R-WP-29). Bild-Support-Anzeige ⏳.
LOW-2/LOW-3 + Projektwechsel-Ref-Reset aus dem C-5-Review ungeplant offen (klein, bei Gelegenheit).
Suspicion S1 (Clear/Compact verkürzt Verlauf → Wasserlinie hoch) und S2 (Read bei laufendem Turn,
heute 409) beobachten.

**pnpm-Fix (2026-11-05):** corepack 0.33 kann pnpm ≥11 nicht starten; läuft mit **pnpm 10.28.0**,
Root-`package.json`-Kollateral gelöscht, Lockfile drift-frei. Arbeitsregel (Paul): Nur-UI-Änderung ⇒
nur UI-Tests; Backend anfassen ⇒ Backend-Tests. Vor Fabrik-Build nach Core-Änderung: Kern
`clean test` → `install -DskipTests`. Trailer-Regel: kein Modellname. Commit nur `aifactory/`-Pfade
scopen (Core im selben Repo).

Runde 1/2-Befundlisten und die Design-Entscheidungen vom 2026-11-02 sind verarbeitet und leben in den
Feature-Docs (ui/chat/files/config.md) — hier gelöscht.

## Offen für Paul (❓ in [open-points.md](open-points.md))

1. Core-Queue-Verlust bei Stop (UC-WP-50) — Core-Story, nur per Proposal
2. C-2 Core-Test-Proposal (archivierter Inc-4-Plan, Anhang B)
3. Paco-Story (SOLL-Fragen: Nutzer-Räume, Write-Roots `paco/<nutzer>/`)

## Fallstricke (gemessen)

* Baureihenfolge: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`.
  Einzeltest: `-Dsurefire.failIfNoSpecifiedTests=false`.
* `MockLlmServer`: kein `getRequestCount()`, keine `system`-Nachrichten, SSE in einem Stück ⇒
  Cancel-mitten-im-Stream nur mit später antwortendem Endpoint (`DelayedModel`, >1500-ms-Latch).
* Guards `OneChatPerProjectTest`/`SharedProjectChatTest`/`ChatTurnTest` nur **verschärfen** (Feldlisten
  exakt, positive Ersetzung, benannte Abweichung) — Stand: 4 Gesprächs-Routen (messages, messages/stop,
  events), Kachel-Familie `agents(+/{name})`, `SendMessageRequest(text, user)`.
* Port 8080 = IDE → Nachweise auf 8099; `~/.peon` vor/nach vergleichen; `@TempDir` liegt auf macOS unter
  TMP → Test-Homes nach `target/`.
* `lintDocsAndTests`/`nextIds`: Pfade relativ zum Session-cwd (aifactory) — `docRoots:["docs"]` reicht
  (kein Präfix `aifactory/`, kein `root`-Parameter); `testRoots` ebenso relativ (Fabrik + UI:
  `factory-backend/src/test`, `factory-ui/test`, `factory-ui/src`). **`idPattern` weglassen** — mit
  `idPattern` lief der Lint auf 0/0-Definitionen (2026-11-05 gemessen); der Default matched korrekt.
* eclipseEditFile verliert `oldString` bei langen Blöcken → `diskEditFile` (kurz ankern); sonst
  `eclipseWriteFile`/`eclipseReplaceLines`.
* `TurnClaim`-Tests: Kachel-Handshake (COMPACTING⇒IDLE) statt Core-Flags als Warte-Bedingung.
* Dev-Agent kann eigenmächtig „Review-Blöcke"/Entscheidungen in den Plan schreiben — Review-Verdicts
  und neue Inkremente kommen nur von Jon; nach garbled Antworten erst Statusanfrage (memory-Regel 3).
* `web-prototype.md` existiert nicht mehr — SOLL in `chat.md`/`files.md`.
* Plan-Archive: **nicht committen, sondern löschen** — Pauls Regel (2026-11-02, Runde 2): sobald die
  Docs stehen, löscht Jon jedes Plan-Archiv selbst. Erledigt: 6 Plugin-/Core-Archive (Repo-Root
  `peon-plan/`, Zyklen 27.–31.10.) + eigenes Archiv `overview-done-2026-11-02-17-15.md`.
