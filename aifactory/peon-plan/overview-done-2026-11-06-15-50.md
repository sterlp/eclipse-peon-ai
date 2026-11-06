# Plan (Delta): Inc 2-N1 „Antworten zeichnen" + Inc 2-N2 „DEV-Memory schneiden"

Basis `6d7a4bba` (Inc 2 Markdown). Review-Verdict **REJECTED — 1 BUG + 5 LOW** (Funde im Anhang, §12).
SOLL ist nachgezogen und damit Maßstab: `docs/chat.md` **R-WP-28** (242–273, inkl. „Nur Antworten sind
Markdown"), **R-WP-29 / UC-WP-55** (339–364, neu: `---` **in** einer Antwort, breite Tabelle, Bild),
`docs/ui.md` **R-WP-39 / UC-WP-71`; `docs/open-points.md` Punkt 2 (`16–18`) und Punkt 4 (`22–25`, C-6).
Pauls neue Anweisung (2026-11-06): **`AGENTS*.md` trägt nur, was jeder Build braucht** — episodische
Messungen und Proben gehören in die Plan-Dateien (`peon-plan/`), nicht in `AGENTS-DEV.md`.

Ausgangslage gemessen (Review): **163 Tests / 20 Dateien grün in 9,42 s**, `tsc -b` 0, oxlint 0/0.
`AGENTS-DEV.md`: **1025 Zeilen**.

## 0. Auftrag — zwei Inkremente, je eine Polarität, nicht mischen

- **Inc 2-N1** (Build): nur hinzufügen/korrigieren — Code + Tests. Vier SOLL-GIVENs der UC-WP-55, eine
  falsche Begründung, eine Messung; danach ein LOW-Kommentar. Kein Verhalten wird entfernt, keine
  Abhängigkeit, kein Backend, kein Draht.
- **Inc 2-N2** (Doku): nur löschen/auslagern in `AGENTS-DEV.md` → Ziel **200–280 Zeilen**; das episodische
  Memory zieht nach `peon-plan/dev-notes-antd.md` (neu). Doku-Arbeit, Suite bleibt unverändert.
- **Nicht berührt in beiden:** `chat.page.tsx`, `chat.live.ts`, `chat.pending.ts`, `docs/**` (Jons Dateien),
  Backend, Draht, `package.json`/Lockfile, `test/shared/setup.ts` (keine neuen Polyfills).
- Jede Mutation wird zurückgenommen; nach jedem Schritt ist die Suite grün (Reihenfolge in §3).

## 1. Delta-Entscheidungen

**N-D1 — `---` in einer Antwort ist Inhalt, gemalt von *unserer* ruhigen Linie.** Reine CSS-Regel
`.chat-log .chat-markdown hr` (kurz, linksbündig, `var(--border)`); kein JSX-Eingriff, react-markdown
liefert das `hr` bereits. Der Rollenwechsel-Trenner bleibt bis Inc 4 `hr.chat-rule` (`var(--tool-line)`,
volle Breite) — die beiden sind ab jetzt an **verschiedenen** Selektoren unterscheidbar.

**N-D2 — Der Trenner-Begriff steht an *einer* Stelle, nicht in jedem Test.** Neuer Test-Helper
`test/shared/log.parts.ts`:
```ts
/** Every `hr` of a log that is not content of an answer — one place decides what counts as a separator. */
export function separatorsOf(log: HTMLElement): HTMLElement[]
```
Definition: `hr` **außerhalb** von `.chat-markdown`. Grund (Review LOW-1b): ein modell-gemaltes `hr` darf
weder als unser Trenner durchgehen noch unseren Trenner verstecken. Der Beweis läuft über **den ganzen**
`.chat-log`; **Inc 4** (R-WP-29 GIVEN 2) fordert dieselbe Liste als `[]` — es gibt dann keinen zweiten,
schwächeren Selektor, der die künftige Zusicherung unterläuft.

**N-D3 — Die Tabelle scrollt in einem eigenen Kasten, nicht über `display:block`.** `components.table`
bekommt einen Wrapper `div.chat-markdown-table` (einziger JSX-Zusatz) — das ist eine **neue DOM-Ebene im
Antwortblock** und wird als Abweichung **`ANTD-A6`** im Register geführt (Nicht-AntD-Struktur, die ein Test
als Seam benutzt), nicht nur im Commit. Verworfen: `display:block` +
`overflow-x` auf `table` — es braucht `white-space: nowrap` auf den Zellen (sonst schrumpft die Tabelle
statt zu scrollen), was **jede** Tabelle jeder Antwort umzeichnet, und es hinterlässt kein Element, das
jsdom prüfen kann. Der Wrapper ist der Scroll-Container, den UC-WP-55 GIVEN 4 verlangt, und in jsdom sichtbar.

**N-D4 — Bilder sind Inhalt, aber nie breiter als die Antwort.** `img { max-width:100%; height:auto }`.
Damit ist die offene Frage aus Review LOW-2 („Modell-Bilder überhaupt zeigen?") **beantwortet**: R-WP-29 /
UC-WP-55 GIVEN 4 setzt ein Bild in der Antwort voraus. Der abgelehnte-Protokoll-Fall (`src=""`, Konsolen-
Warnung) bleibt **außerhalb** dieses Inkrements — eine Verhaltensänderung, kein Beweis (offener Punkt §11).

**N-D5 — Der BUG ist eine Textkorrektur; sein Beleg ist die Quelle, kein Test.** Gemessen im installierten
Stand 10.1.0: `react-markdown/lib/index.js:175-178` baut den Processor **pro Render** (`createProcessor`
`:262-275`, kein Cache); Identitäts-Memoisierung gibt es nur in `MarkdownHooks` (`:214-251`), das die App
nicht nutzt. Die Modul-Konstanten bleiben (stabile Props), die **Begründung** wird ersetzt. Keine Mutation
möglich — das wird im Commit und im Report so benannt.

**N-D6 — R-WP-28 ist dreiteilig, ab jetzt dreifach bewiesen.** Ein Test (`onlyAnswersAreMarkdown`) mit
Nutzer-, Fehler- und Werkzeug-Zeile; die Antwort daneben ist die positive Gegenseite (zeichnet Markdown).
Der bestehende Nutzer-Fall (`chat.log.test.tsx:87`) geht darin auf, `// UC-WP-73` bleibt daran.

**N-D7 — Die Suspicion wird gemessen, nicht behauptet, und nicht im Test.** Einweg-Probe mit
`performance.now()` über ~50 Rerenders einer ~100-Zeilen-Zaun-Antwort, danach gelöscht; Zahlen in
`peon-plan/dev-notes-antd.md`. Keine Zeit-Assertion in der Suite (sie soll < 30 s bleiben, jsdom-Zeit ist
kein Browser-Wert). Kein Cache, keine Optimierung in diesem Inkrement.

**N-D8 — Ausnahme von „nur Exception-Liste" (Inc 2-N2, von Jon bestätigt 2026-11-06).** Aus `601–714` bleiben **fünf** Fallstricke, die
keine Wiederholung der Regel in `AGENTS.md:6-8` sind, sondern lokale Fakten: `@TempDir` ist nie das
Fabrik-Home; Env-Startwerte über `System.setProperty` + `clearProperty` im `@AfterEach`; handgeschriebener
Test-Zustand endet im Test; klassenweites `@Timeout` wirkt je Methode, Lifecycle braucht
`junit-platform.properties` (120 s); der Guard scannt einen **Pfad**, keinen Namen. Messgeschichte
(118 s → 43 s, Obergrenzen-Formel, Proben) zieht um. Begründung: diese fünf sind nicht aus dem Code
ableitbar, und ihre Löschung kostet den nächsten Build einen roten Lauf.

**N-D9 — Zwei Ausnahmen von „181–213 ersatzlos löschen" und „nur `AGENTS-DEV.md`" (Inc 2-N2, von Jon bestätigt 2026-11-06).**
`181–213` enthält vier Fakten, die **nicht** markstream-spezifisch sind (react-shiki `/web` statt
Haupteinstieg + Preis; Markdown zerteilt Text in Token-Spans ⇒ `getByText` trifft nicht, Zeile über
`textContent` prüfen; Projektwechsel remountet über `key`, `set-state-in-effect`-Lint; „Could not parse CSS
stylesheet" ist bei kaltem vite-Cache vorbestehend). Gelöscht werden nur `186–195` (Paket/Pin-Wirrwarr) und
der „überholt"-Vorspann; der Rest wird auf vier Zeilen verdichtet und bleibt. Zweite Ausnahme:
`src/shared/code.language.ts:3` zitiert „AGENTS-DEV Inc 3b" — der Abschnitt fällt weg, die Zeile wird auf
die Aussage selbst umgestellt (einzige Nicht-`AGENTS-DEV`-Änderung in 2-N2).

## 2. Inc 2-N1 — AntD-Umbau, Markdown-Nacharbeit (Build)

Reihenfolge, nach jedem Schritt grün: **A (BUG) → B (`hr`) → C (Tabelle/Bild) → D (dreiteilig) →
E (Messung) → F (LOW-4-Kommentar)**.

### A — BUG: falsche Begründung der Modul-Konstanten

**Dateien:** `factory-ui/src/chat/chat.markdown.tsx:30-33`, `AGENTS-DEV.md` (Abschnitt „AntD-Umbau Inc 2",
Bullet „`remarkPlugins` und `components` als Modul-Konstante").

**Arbeit** — Kommentar ersetzen, Inhalt: react-markdown baut seinen Processor **bei jedem Render neu**
(`createProcessor`, kein Cache); die Identitäts-Memoisierung sitzt in `MarkdownHooks`, das die App nicht
benutzt; ein frisches Array pro Render kostet **nichts** zusätzlich; die Konstanten bleiben, weil sie
dieselben Props sind. Der Satz „reuses its processor for the identity of these plugins" und die
DEV-Formulierung „der Processor wird über die Identität der Plugins memoisiert" **dürfen nicht stehen
bleiben** (sie werden vom nächsten Inkrement-Leser als Erfahrung übernommen).

```tsx
/**
 * Module scope because those props then never change: react-markdown builds its processor on every
 * render (`createProcessor` in `react-markdown/lib/index.js`) — the identity-based memoisation lives in
 * `MarkdownHooks`, which this app does not use. A fresh array per render would cost nothing; the
 * constants stay because they are the same value on every render.
 */
```
**Mutation:** keine (N-D5). Beleg im Commit: Datei:Zeile der Quelle.

### B — UC-WP-55 GIVEN 3: die Linie einer Antwort

**Dateien:** `factory-ui/src/styles.css`, `factory-ui/test/chat/chat.markdown.test.tsx`,
`factory-ui/test/chat/chat.log.test.tsx`, `factory-ui/test/shared/log.parts.ts` (neu).

**Arbeit**
1. CSS-Regel (in `styles.css`, neben den Markdown-Regeln ab `:373`):
```css
/* A rule the model drew is the answer's content (R-WP-29): our own short, quiet line inside the answer
   block — the border grey, never the log's width, and never the separator between two voices (Inc 4
   removes that one). */
.chat-log .chat-markdown hr {
  border: 0;
  border-top: 1px solid var(--border);
  width: 3rem;
  margin: 0.55rem 0;
}
```
   **Trap (gemessen, AGENTS-DEV):** `declarations()` findet einen Selektor per `indexOf`, deshalb darf
   **kein Kommentar in `styles.css` einen Selektorwortlaut enthalten** — Formulierungen wie oben ohne
   geschweifte Klammer sind sicher, den Selektor selbst nie in Prosa wiederholen.
2. `test/shared/log.parts.ts` mit `separatorsOf(log)` (N-D2) und Kommentar, der auf Inc 4 verweist.
3. Test `anAnswersOwnRuleStaysInsideTheAnswer` (`// UC-WP-55`): `vorher\n\n---\n\nnachher` ⇒ genau ein
   `hr`, es liegt **in** `.chat-markdown`, trägt **nicht** `chat-rule`, `---` steht nicht im Text; Sheet:
   `var(--border)` + `width: 3rem`; Gegenprobe `.chat-log .chat-rule` trägt `var(--tool-line)`.
4. Test `theLogNeverCountsAnAnswersRuleAsItsSeparator` (`// UC-WP-55`): `[Nutzer, Antwort mit ---, Nutzer]`
   ⇒ `separatorsOf(log)` sind genau **zwei**, in der Reihenfolge der neuen Stimme (`agent`, `user`), die
   Antwort-Linie ist **nicht** darunter, und `hr.chat-rule` kommt **in** keiner Antwort vor.

**Mutationen:** (a) CSS-Block löschen ⇒ `anAnswersOwnRuleStaysInsideTheAnswer` rot. (b) `hr`-Override in
`chat.markdown.tsx` mit `className="chat-rule"` ⇒ `theLogNeverCountsAnAnswersRuleAsItsSeparator` rot (der
gefährliche Fall: Modell-Linie als unser Trenner). Beide zurücknehmen.

### C — UC-WP-55 GIVEN 4: breite Kost bleibt im Antwortblock

**Dateien:** `factory-ui/src/chat/chat.markdown.tsx`, `factory-ui/src/styles.css`,
`factory-ui/test/chat/chat.markdown.test.tsx`.

**Arbeit**
1. `components`-Map um `table` erweitern:
```tsx
/**
 * A GFM table of an answer is read inside its own scroll box (R-WP-29): the wrapper is what scrolls
 * horizontally, so a wide table never widens the log or the page. The table keeps its own layout.
 */
function TableBlock({ children }: { children?: ReactNode }) {
  return (
    <div className="chat-markdown-table">
      <table>{children}</table>
    </div>
  )
}
```
   `const COMPONENTS: Components = { pre: CodeFence, table: TableBlock }`.
2. CSS:
```css
/* The wrapper of a wide table is the one that scrolls — the log keeps its vertical scroll and the page
   its fixed frame (R-WP-29). */
.chat-log .chat-markdown-table {
  max-width: 100%;
  overflow-x: auto;
  margin: 0 0 0.35rem;
}

/* An image of an answer is content and stays inside the answer's width (R-WP-29). */
.chat-log .chat-markdown img {
  max-width: 100%;
  height: auto;
}
```
3. Test `wideContentStaysInsideTheAnswer` (`// UC-WP-55`, `// ANTD-A6`): Tabelle +
   `![x](https://example.test/x.png)` ⇒ `.chat-markdown-table > table` vorhanden, Wrapper trägt
   `overflow-x: auto`; `.chat-log` bleibt bei `overflow-y: auto` **ohne** `overflow-x`;
   `baseDeclarations('#root')` enthält weiter `overflow: hidden`; `img` vorhanden und
   `max-width: 100%`. Begründung im Test: jsdom misst keine Breite — bewiesen wird, **wer** scrollt, nicht
   wie weit (endgültige Optik: Pauls Smoke-Test).
4. **Register-Eintrag `ANTD-A6`** in `AGENTS-DEV.md` (Abschnitt „Abweichungs-Register (UI)"): „neue
   DOM-Ebene `div.chat-markdown-table` um jede Tabelle einer Antwort (Scroll-Kasten, R-WP-29); Test-Seam,
   nicht AntD" — plus Verweis im Testkommentar (Schritt 3). Die Aussage „Abweichung nur im Commit" ist
   damit erledigt.
5. **Struktur-Prüfung vor dem Test:** kein bestehender Test hängt an der alten Form. Gemessen per
   `grep -n "querySelector\|firstElementChild\|chat-markdown" test/chat/*.tsx` —
   Treffer sind ausschließlich **Nachfahren**-Abfragen (`th`, `tbody td` in `aTableAndStrikeThroughAreDrawnAndNotTyped`;
   `pre.shiki`, `[data-testid="chat-code"]`, `code` in `codeBlocksAreHighlighted`) und Assertions über die
   `<li>`-Kinder von `.chat-log` (`chat.log.test.tsx:25,72`, `chat.page.test.tsx:440,458,505,881,953,984`).
   Keine davon adressiert `table` als **direktes** Kind von `.chat-markdown` ⇒ beide Tests müssen nach
   Schritt 1/2 **unverändert** grün sein. Wird einer rot, ist das ein Fund und wird gemeldet, nicht der
   Test angepasst.

**Mutationen:** (a) `table`-Override entfernen (nackte `<table>`) ⇒ Test rot (kein Scroll-Kasten).
(b) `overflow-x: auto` zusätzlich auf `.chat-log` ⇒ Test rot (der Log würde der horizontale Scroller).

### D — R-WP-28 dreiteilig: nur Antworten sind Markdown

**Dateien:** `factory-ui/test/chat/chat.log.test.tsx`.

**Arbeit** — Test `anInputWithMarkdownCharactersStaysItsWord` (Zeilen 82–97) wird zu
`onlyAnswersAreMarkdown` (kein anderer Aufrufer, `grep` = 0), Marker `// UC-WP-73` bleibt, Kommentar nennt
die drei Zeilenarten. Ein Render mit vier Zeilen: Nutzer (`**fett** ~~weg~~`), Antwort, Fehler
(`role: 'error'`, `**model** ~~nicht~~`), Werkzeug (`appendLiveTool([], 'ls **fett**', 3)`). Je Zeile:
Zeichen stehen im `textContent`, `strong, del, br, table` sind `null`; die Antwort daneben trägt
`chat-markdown` (positive Gegenseite, damit der Test nicht nur die Abwesenheit pinnt).
Zusätzlich `// UC-WP-54` bleibt am Streaming-Test — hier **nicht** nötig.

**Mutationen:** (a) Fehler-Zweig in `chat.log.tsx:121-123` auf `ChatMarkdown` ⇒ Fehler-Assertion rot.
(b) `ChatTools` rendert `ChatMarkdown` statt der `span` ⇒ Werkzeug-Assertion rot.

### E — Suspicion messen (kein Test, kein Commit-Inhalt)

`chat.markdown.test.tsx` temporär um eine Probe erweitern (oder Wegwerf-Datei): Antwort mit ~100-Zeilen-
`ts`-Zaun, 50 Rerenders wie Token, `performance.now()` je Render; Median und p95 für (a) Prosa, (b) Zaun.
Probe **löschen**. Zahlen in `peon-plan/dev-notes-antd.md` unter „Messung: Kosten pro Token" mit dem
Hinweis: jsdom ist die Obergrenze, der Browser ist schneller; > 30 ms/Render im Zaun-Fall ist ein
Inc-4-Risiko für Jon, **kein** Grund, hier einen Cache zu bauen.

### F — Review LOW-4: Kommentar sagt mehr, als der Test belegt

`chat.markdown.test.tsx:142` — „one box for the fence alone … (R-WP-28)" ⇒ auf die belegte Aussage stellen:
ein Kasten für den **Zaun**, Inline-`code` bleibt außerhalb der hervorgehobenen Ansicht; dass Inline-Code
den globalen Chip der App trägt, regelt R-WP-28 nicht.

### Beweismatrix Inc 2-N1

| Fund | Test | UC | Mutation | erwartet rot |
|---|---|---|---|---|
| BUG | — (Quelle `react-markdown/lib/index.js:175-178`) | R-WP-28 | keine | — |
| LOW-1 G3 | `anAnswersOwnRuleStaysInsideTheAnswer` | UC-WP-55 G3 | `hr`-Regel löschen | derselbe |
| LOW-1 G3 | `theLogNeverCountsAnAnswersRuleAsItsSeparator` | UC-WP-55 G3 | `hr` mit `chat-rule` | derselbe |
| LOW-1 G4 | `wideContentStaysInsideTheAnswer` | UC-WP-55 G4 | Wrapper raus / `overflow-x` am Log | derselbe |
| LOW-3 | `onlyAnswersAreMarkdown` | UC-WP-73 + R-WP-28 | Fehler-/Werkzeug-Zeile durch `ChatMarkdown` | derselbe |
| Suspicion | — (Messung, §3 E) | — | keine | — |

**Erwartet: 163 → 166 Tests / 20 Dateien** (drei neue Tests, eine Umbenennung), `tsc -b` 0, oxlint 0/0,
Suite < 30 s, `lintDocsAndTests` ohne **neue** Findings (Baseline: 25).

## 3. Inc 2-N2 — `AGENTS-DEV.md` auf Build-Wissen schneiden (Doku)

Ziel: **200–280 Zeilen** (Ist 1025). Mechanik: erst `peon-plan/dev-notes-antd.md` **vollständig** schreiben
(Blöcke wörtlich), dann aus `AGENTS-DEV.md` entfernen; nie umgekehrt, nie umformulieren beim Verschieben.
Anker sind die **Überschriften** (Zeilennummern unten sind Stand vor 2-N1 und verschieben sich um ±3).

### 3.1 Bleibt (wörtlich)

| Abschnitt | Zeilen | warum |
|---|---|---|
| `## aifactory (Spring-Boot-Reaktor) — Build-Fakten` | 1–60 | jeder Backend-Build |
| `## Eclipse schreibt kaputte Klassen — Maven ist die Wahrheit` | 61–77 | jeder Java-Lauf |
| `## Toolchain corepack/pnpm (2026-11-05)` | 763–787 | `COREPACK_ENABLE_PROJECT_SPEC=0` ist Pflicht für **jeden** `pnpm`/`mvn`-Lauf |
| `## Abweichungs-Register (UI)` | 1004–1023 | einzige Stelle, auf die Testkommentare zeigen (`ANTD-A1…A6`, `INC7B-A1`, `aria-current`, eingefrorenes `A1`); **`ANTD-A6` kommt in 2-N1 hinzu** |

### 3.2 Wird verdichtet (Quelle → neuer Abschnitt „Factory-UI: Testkette und dauerhafte Fallstricke")

- Doc-Lint-Baseline ist nicht 0 Findings (91–94); Surefire-Teardown-Rauschen im Backend (95–97).
- Vitest kennt kein `.as(…)` — zweites Argument (101–103).
- Quellen-Scan per `import.meta.glob(… '?raw')`, **kein** `node:fs`/`fileURLToPath` in `src`/`test` (104–110).
- Catalog-Wert ≠ Feldwert; Label `.ant-select-content[title]`, Drahtwert nur im Body (`ANTD-A3`) (111–115).
- `react(only-export-components)`: Komponenten-Datei exportiert keine Hooks/Helfer; Konstanten erlaubt (129–132).
- Routing macht Navigation async ⇒ `findBy*` (133–135).
- jsdom beweist keine Farbe ⇒ Sheet-Text lesen; `?raw` auf `.css` nur mit `test.css.include` (RegExp + `\?|$`) (166–173).
- `hr` gehört in die `<li>`, nie zwischen zwei `<li>` (gilt bis Inc 4) (174–176).
- Draht-Griffe nicht umbenennen: `chat-line-jon`, `chat-live`, `chat-line-author` (177–179).
- `userLineTexts()`/`toContain`-Falle bei Verlaufszeilen (855–858).
- **Leitsatz „Modellierte Draht-Form braucht die erzeugende Core-Regel"** (862–865) — `docs/adr/003` verweist darauf.
- react-shiki `/web` statt Haupteinstieg + Preis; Markdown zerteilt Text in Token-Spans (Zeile über
  `textContent` prüfen, nicht `getByText`); Projektwechsel remountet über `key`; „Could not parse CSS
  stylesheet" ist bei kaltem vite-Cache vorbestehend (196–211, verdichtet).
- AntD unter jsdom: `matchMedia` **und** `ResizeObserver` sind load-bearing (jsdom 26.1.0), Select über
  `<id>_list` + `test/shared/choices.ts`, `darkAlgorithm` leitet den Akzent ab, Dunkel ist **eine** Tabelle
  via `data-theme`, Prosa in `index.css` darf keinen Test-Selektor nennen, Fonts fallen auf die helle
  Tabelle zurück, Bootstrap-Wache liegt in `test/shared/design.basis.ts` (896–970 verdichtet).
- react-markdown: `remarkPlugins`/`components` als Modul-Konstanten, **Processor wird pro Render gebaut**
  (korrigiert in 2-N1) (973–1002 verdichtet).

### 3.3 Wird verdichtet (Quelle → „Spring-Tests: Kontext-Disziplin", Regel steht in `AGENTS.md:6-8`)

Ein Kontext je Build, `FactorySpringTest` besitzt die eine `@DynamicPropertySource`, `missCount = 1`
(`-Dlogging.level.org.springframework.test.context.cache=DEBUG`); Ausnahmeliste
(`ChatRestartTest`, `DelegatedAnswerRestartTest`, `ProjectStatusTest`, beide Richtungen geprüft,
`SpringApplicationBuilder`-Boots erscheinen nie im Cache); `junit-platform.properties`
(`…lifecycle.method.default=120s`), klassenweites `@Timeout` wirkt je Methode; die fünf Fallstricke aus
N-D8. Quelle: 601–714.

### 3.4 Zieht nach `peon-plan/dev-notes-antd.md` (wörtlich, mit Überschrift)

80–98 (ohne 82–90), 99–117 (ohne 101–115), 118–137 (ohne 129–135), 138–163, 164–180 (ohne 166–179),
214–249, 250–286, 287–398, 399–455, 456–562, 563–602, 606–700 (ohne die in 3.3 verdichteten Fakten),
715–762, 788–895 (ohne 855–865), 896–1002 (Proben- und Abschluss-Bullets, inkl. „Nach Inc 2: 163/20").
Kopf der Datei: „Episodisches Memory des AntD-Umbaus — verschoben aus `aifactory/AGENTS-DEV.md` am
2026-11-06, weil `AGENTS*.md` nur trägt, was jeder Build braucht. Maßgeblich ist der Code; hier stehen die
Messungen und Proben je Inkrement."

**Die Datei steht für sich** (Jons Auflage): wer Inc 4 liest, braucht kein anderes Journal und nicht diesen
Plan. Deshalb beim Verschieben:
- jede Block-Überschrift mitnehmen (sie ist der Anker, auf den zitiert wird);
- Rückverweise auf Blöcke, die in `AGENTS-DEV.md` **bleiben** („s. oben …"), auf den neuen Ort auflösen
  (`aifactory/AGENTS-DEV.md`, Abschnitt …) — sonst zeigt der Satz ins Leere;
- keine Verweise auf `peon-plan/overview.md` (dieser Plan wird ersetzt), keine „siehe Inkrement N"-Kürzel
  ohne den Satz, der die Sache sagt.

### 3.5 Wird ersatzlos gelöscht

- 82–90 (Bootstrap-Paket, Build-Reihenfolge, `Nav.Link`/`aria-current`-Notiz — durch AntD/`ANTD-A1` überholt).
- 186–195 (markstream-Paket, Pin-Korrektur, „überholt"-Vorspann; die vier Fakten aus N-D9 bleiben verdichtet).
- Alle Abschluss-Bullets „Nach <Inc>: N Tests / Dateien" und alle „Proben <Inc>" — sie ziehen mit ihrem
  Block um, ein Abschluss-Bullet eines gelöschten Blocks wird **nicht** nach `AGENTS-DEV.md` übernommen.

### 3.6 Nachweis nach dem Schnitt

1. `wc -l aifactory/AGENTS-DEV.md` ⇒ 200–280.
2. Jeder im Code referenzierte Code existiert: `grep -rn "ANTD-A[1-6]\|INC7B-A1" factory-ui/src factory-ui/test`
   ⇒ alle Treffer stehen im Register (inkl. `ANTD-A6` aus 2-N1).
3. Kein hängender Verweis: `grep -rn "AGENTS-DEV" factory-ui/src` ⇒ nur die umgestellte Zeile in
   `code.language.ts` (nennt die Aussage selbst), und `docs/adr/003` findet den Leitsatz noch.
4. **Jeder zitierte Abschnittstitel existiert noch:** `grep -rn "Inc 3b\|AntD-Umbau Inc\|Leitsatz" --include=*.ts --include=*.tsx --include=*.java --include=*.md factory-ui/src factory-ui/test factory-backend/src docs`
   ⇒ jeder Treffer zeigt auf einen Abschnitt, der entweder in `AGENTS-DEV.md` **oder** in
   `peon-plan/dev-notes-antd.md` wörtlich als Überschrift steht (Abgleich Liste gegen `grep -n '^## '` beider
   Dateien). Die Treffer-Liste kommt in die Meldung (§8.5) — nicht nur „geprüft".
5. Jede Überschrift aus 3.4 steht in `peon-plan/dev-notes-antd.md`; `git diff` der entfernten Zeilen und
   `wc -l` der neuen Datei grob gegenprüfen (≈ 700 Zeilen umgezogen). Stichprobe „steht für sich": die Datei
   einmal von oben lesen, ohne `AGENTS-DEV.md` daneben — jeder Rückverweis muss auflösbar sein.
6. Suite unverändert grün (166/166), `tsc -b` 0, oxlint 0/0, `lintDocsAndTests` unverändert.

## 4. Betroffene Dateien

| Pfad | Inc | Änderung |
|---|---|---|
| `aifactory/factory-ui/src/chat/chat.markdown.tsx` | N1 | Kommentar (A), `TableBlock` + `table` in `COMPONENTS` (C) |
| `aifactory/factory-ui/src/styles.css` | N1 | zwei `hr`-Regeln (B), Wrapper + `img` (C) |
| `aifactory/factory-ui/test/shared/log.parts.ts` | N1 | **neu**, `separatorsOf(log)` |
| `aifactory/factory-ui/test/chat/chat.markdown.test.tsx` | N1 | zwei Tests neu (B, C), Kommentar (F) |
| `aifactory/factory-ui/test/chat/chat.log.test.tsx` | N1 | ein Test neu (B), einer umbenannt/erweitert (D) |
| `aifactory/AGENTS-DEV.md` | N1, N2 | N1: Bullet-Korrektur (A) + Register-Eintrag `ANTD-A6`; N2: Schnitt |
| `aifactory/peon-plan/dev-notes-antd.md` | N2 | **neu**, episodisches Memory |
| `aifactory/factory-ui/src/shared/code.language.ts` | N2 | Kommentar auf die Aussage selbst (N-D9) |

Unberührt: `chat.page.tsx`, `chat.live.ts`, `chat.pending.ts`, `chat.tools.tsx`, `chat.diff.tsx`,
`test/shared/setup.ts`, `vite.config.ts`, `package.json`, `pnpm-lock.yaml`, `docs/**`, `factory-backend/**`.

## 5. Regeln und Grenzen

- **Ein Pfad je Sache:** eine Markdown-Ansicht (`chat.markdown.tsx`), eine Hervorhebung (`shared/code.tsx`),
  eine Trenner-Definition (`test/shared/log.parts.ts`), ein Register (`AGENTS-DEV.md`).
- **Keine Farbe ohne Sheet-Beleg:** jsdom malt nicht — jede Farb-/Maß-Aussage über `declarations()` /
  `baseDeclarations()` aus `test/shared/sheets.ts`, nie über `getComputedStyle`.
- **Kein Selektorwortlaut in CSS-Prosa** (`indexOf`-Empfindlichkeit von `declarations`/`tokenValues`).
- **Keine Zeit-Assertion**, keine neue Abhängigkeit, kein Backend, kein Draht, `docs/**` nicht anfassen.
- **Marker:** die id wörtlich aus dem Doc (`UC-WP-55`, `UC-WP-73`); keine erfundene id (Core/UI-Regel).
- **Mutationen mit `cp` + Restore**, nie `git checkout` auf uncommittete Dateien.

## 6. BDD-Abnahme (messbares Ziel)

| GIVEN / WHEN / THEN | Test (Ebene) | UC |
|---|---|---|
| GIVEN eine Antwort `vorher\n\n---\n\nnachher` WHEN gezeichnet THEN ein `hr` **in** `.chat-markdown`, ohne `chat-rule`, kein `---` im Text; Regel: `var(--border)`, `width: 3rem` | `anAnswersOwnRuleStaysInsideTheAnswer` (unit, `chat.markdown.test.tsx`) | UC-WP-55 G3 |
| GIVEN zwei Rollenwechsel und eine Antwort mit `---` WHEN der Log zeichnet THEN `separatorsOf(log)` = genau die zwei Rollenwechsel-Regeln (`agent`, `user`), die Antwort-Linie nicht darunter, kein `hr.chat-rule` in einer Antwort | `theLogNeverCountsAnAnswersRuleAsItsSeparator` (unit, `chat.log.test.tsx`) | UC-WP-55 G3 |
| GIVEN breite Tabelle + Bild WHEN gezeichnet THEN Tabelle in `.chat-markdown-table` (`overflow-x: auto`), `img` `max-width: 100%`, `.chat-log` ohne `overflow-x`, `#root` `overflow: hidden` | `wideContentStaysInsideTheAnswer` (unit, `chat.markdown.test.tsx`) | UC-WP-55 G4 |
| GIVEN Nutzer-, Fehler- und Werkzeug-Zeile mit `**`/`~~` WHEN im Log THEN Wortlaut, kein `strong/del/br/table`; die Antwort daneben zeichnet | `onlyAnswersAreMarkdown` (unit, `chat.log.test.tsx`) | UC-WP-73 G3 + R-WP-28 |
| GIVEN der Kommentar an den Modul-Konstanten WHEN gelesen THEN nennt er „Processor pro Render", nicht Memoisierung | kein Test — Quellenbeleg `react-markdown/lib/index.js:175-178` | R-WP-28 |
| **Inc 2-N2** berührt keinen Use Case (Doku/Refactor) | Nachweis: Suite unverändert + Prüfungen §3.6 | — |

**Offen bleibt bewusst:** UC-WP-55 GIVEN 2 (keine Linie **zwischen** Zeilen) — Inc 4; Beweis läuft dann
über `separatorsOf(log) === []`. `R-WP-29` bleibt ❌ bis dahin, den Flip macht Jon.

## 7. Test-Strategie

- Drei neue Tests in zwei bestehenden Dateien ⇒ Dateizahl bleibt 20; keine neuen Test-Helfer-Polyfills.
- `test/shared/log.parts.ts` ist Produktionsnähe ohne Verhalten: reine DOM-Abfrage, von Inc 4
  weiterbenutzt — deshalb ausgelagert und nicht in einem Testfile versteckt.
- Beide Sheet-Assertions sind tragend (`width`, `overflow-x`) und werden mutiert; die DOM-Assertions
  benennen Verhalten (Kasten, `closest('.chat-markdown')`), nicht Klassen allein.
- Die Messung (E) ist ausdrücklich **kein** Test: keine Flakiness, kein Zeitbudget im CI.
- Nach dem Umbau prüfen, dass `chat.markdown.test.tsx:39` (`white-space: normal`) und
  `chat.log.test.tsx:25` (`hr.chat-rule`) unverändert grün sind — beide bleiben gültig.

## 8. Commit & Push

1. **Commit „Docs von Jon"** — nur falls `git status docs/` etwas zeigt: seine Dateien unverändert.
2. **Commit Inc 2-N1** — `aifactory/factory-ui/**` + `aifactory/AGENTS-DEV.md` (Bullet-Korrektur A **und**
   Register-Eintrag `ANTD-A6`); Message nennt den Docs-Commit, die vier Beweise (Antwort-`hr`,
   Trenner-Naht, Tabelle/Bild, dreiteiliger Wortlaut), `ANTD-A6` als Register-Abweichung, den Quellenbeleg
   des BUG und dass dieser mutationslos ist; **keinen Modellnamen**.
3. **Commit Inc 2-N2** — `aifactory/AGENTS-DEV.md`, `aifactory/peon-plan/dev-notes-antd.md`,
   `aifactory/factory-ui/src/shared/code.language.ts`; Message nennt Zeilenzahl vorher/nachher und den
   Prüflauf aus §3.6.
4. Push auf `web-factory-mvp-2`.
5. Meldung an Jon: Testzahl/Dateien/Dauer, je Mutation der getroffene Test, die Messwerte aus E,
   `AGENTS-DEV.md` alt/neu, die Struktur-Prüfung aus §2 C.5, die **Grep-Listen** aus §3.6.2/3.6.4
   (`ANTD-*`-Treffer und zitierte Abschnittstitel mit Fundort), und **jede** Abweichung von diesem Plan
   (namentlich: N-D8, N-D9, ob der LOW-2-Fall liegen blieb).

## 9. Ausblick (nach Inc 2-N1/N2)

- **Inc 3 Eingabe** (R-WP-41/UC-WP-74, R-WP-30/UC-WP-56): `autoSize`, Send nur bei leerem Entwurf, Queue
  bleibt (R-WP-10), Refocus; **3b** `!`-Prio-Nachricht (R-WP-42/UC-WP-75).
- **Inc 3c (klein)** (Jons Entscheidung, aus Review LOW-2): ein Bild mit abgelehntem Protokoll
  (`![x](data:…)` ⇒ `src=""` + React-Konsolenwarnung) zeigt **Alt-Text statt leerem `src`**; Beweis: genau
  ein `img` ohne `src`, `alt` bleibt, keine Konsolenwarnung. R-WP-29 verlangt das nicht — deshalb eigener
  Mini-Schritt nach Inc 3, nicht in 2-N1.
- **Inc 4 Timeline** (R-WP-40/UC-WP-72, R-WP-29/UC-WP-55 G2): Timeline am Anker, `at` additiv in
  `chat.live.ts`; **hier** fällt `hr.chat-rule` — Beweis: `separatorsOf(log)` über den ganzen `.chat-log`
  ist `[]`, die Antwort-Linie bleibt (`// UC-WP-55`, `// UC-WP-72`); die hr-Belege aus Inc 3a/`chat.page`
  (`roleChangeDrawsARule`, `roleChangeToAnErrorDrawsARule`, `chat.diff.test:111`) werden auf den neuen
  Selektor gezogen.
- **Inc 5a** (UC-WP-70 G1+2): Sider + Projektbaum + URL auf Projektname; `aria-current` kehrt zurück (Test).
- **Inc 5b** (UC-WP-70 G3): Files als Route `/chat/:projectName/files`, Einklappen, dockende Eingabe.

## 10. Offene Enden / Risiken

- **R1 (entschieden, Jon 2026-11-06):** Review LOW-2 (Bild mit abgelehntem Protokoll ⇒ `<img src="">` +
  React-Konsolenwarnung) wird **nach Inc 3** als eigener Mini-Schritt „Inc 3c (klein)" gebaut (siehe §9);
  hier nur indirekt berührt (Bilder **sind** Inhalt, §2 C). R-WP-29 verlangt den Alt-Text nicht.
- **R2:** Review LOW-5 (leere Kappe im Zaun bei Nicht-String-Kind) bleibt liegen — kein bekannter Auslöser;
  ein Fix wäre eine Verhaltensänderung (lautere Alternative), kein Beweis.
- **R3:** jsdom misst keine Breite; „Tabelle scrollt" ist als *Struktur* bewiesen (Wrapper + Sheet), die
  Optik bleibt Pauls Smoke-Test.
- **R4:** Nach dem AGENTS-DEV-Schnitt wächst die Versuchung, Episoden wieder dort abzulegen. Gegenmittel:
  die Kopfzeile von `dev-notes-antd.md` und der Register-Absatz „ein Code, eine Stelle".
- **R5:** `blockOf`/`tokenTable`-Formatierungsempfindlichkeit (Review LOW-10) bleibt bestehen; neue
  CSS-Kommentare halten sich an „kein Selektorwortlaut in Prosa".
- **R6:** Wird 2-N2 zusammen mit 2-N1 committet, ist der Diff nicht mehr lesbar — deshalb getrennt.

## 11. Anhang — Review-Funde (Inc 2, `6d7a4bba`) mit Status in diesem Plan

| Fund | Schwere | Status hier |
|---|---|---|
| Processor-Memoisierung als Grund genannt (Code + DEV-Journal) | BUG | §2 A — Textkorrektur, Quellenbeleg |
| LOW-1 `hr`/Tabelle/Bild ohne Blattregel und ohne Naht | LOW | §2 B + C; Inc-4-Naht über `separatorsOf` |
| LOW-2 Bild mit abgelehntem Protokoll loggt | LOW | entschieden: **Inc 3c (klein)** nach Inc 3 (§9, R1) |
| LOW-3 dreiteiliger Regelsatz, einteilig bewiesen | LOW | §2 D |
| LOW-4 Kommentar behauptet mehr als der Test | LOW | §2 F |
| LOW-5 stille Leerkappe im Zaun | LOW | offen, R2 |
| Suspicion Kosten pro Token | unbelegt | §2 E — gemessen, nicht behauptet |

Nicht Gegenstand (Review bestätigt erfüllt): XSS/Durchreichung, ein Renderer-Pfad, Zustand/Anker,
`final`-Entfall, Dependencies, Suite/Setup.
