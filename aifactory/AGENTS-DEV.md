- Never test CSS you can use https://docs.stagehand.dev/v4/first-steps/introduction to screenshot it if needed
- UI elements are small, composable units with a single clear responsibility — like well-designed
  Java classes. Split when a unit has more than one reason to change, not by line count alone.
  Avoid both monoliths and premature fragmentation. (read component-architecture for more details)
- **Vorgeladene Felder sind keine Log-Inhalte**: Header-/Query-Params und Extra-Body reisen als Klartext
  ins Read-Modell (das Feld ist vorbefüllt), aber kein Log, kein 400-Hint und kein `toString()` darf sie
  wiederholen — ein Hint nennt nur das Feld; wie der Kern den Key behandelt der Kern `headerParams`
  (`LlmConfig` `@ToString(exclude=…)`), und `SettingsSnapshot` tut das Gleiche.

## Build

- **pnpm auf dem PATH**, sonst `factory-ui FAILURE: Cannot run program "pnpm"` vor jedem Backend-Modul:
  `export PATH=~/.nvm/versions/node/v22.23.4/bin:$PATH`; ein neuer nvm-Node bringt keine pnpm-Shims —
  einmal `corepack enable` (corepack 0.36: `COREPACK_ENABLE_PROJECT_SPEC=0` nicht mehr nötig). **Aktiv:
  pnpm 10.28.0** (`lockfileVersion: '9.0'`); pnpm 12 ist kein Drop-in (`ERR_PNPM_PACKAGE_MANAGER_REMOVE_MODULES_DIR`). Das ungetrackte Root-`package.json` ist nicht uns.
- **Jeder Maven-Lauf mit `clean`**: Eclipse (JDT+Lombok) lässt fehlerhafte Class-Files in `target/classes`
  zurück → `500 "Unresolved compilation problems"`, oder `NoClassDefFoundError: <SimpleTypeName>` ohne
  Paket. Eclipse meldet Lombok-Getter als „undefined" — Maven ist die Wahrheit.
- **Kern-Änderung**: erst `mvn -f org.sterl.llmpeon.core/pom.xml clean test`, dann
  `-o -pl … install -DskipTests` — sonst baut die Fabrik gegen das alte `~/.m2`-Artefakt. `spring-boot:run`
  **erst nach** `install`, denn die SPA kommt aus dem `factory-ui`-Jar (`classpath:/static/ui/**`).
- **Kein `-parameters`** im Build: `@PathVariable`/`@RequestParam` ohne Namen = `IllegalArgumentException`
  im 400-Handler, nicht 500 → Namen immer explizit.
- Einzeltest: `-Dsurefire.failIfNoSpecifiedTests=false`. `Surefire … kill self fork JVM` ~30 s nach
  `System.exit(0)` ist Teardown-Rauschen. Port **8080 ist die IDE** → Nachweise auf 8099; `gitea.sterl.co`
  braucht VPN; `~/.peon` vor/nach vergleichen.
- „Das war schon vorher so" ist eine Behauptung: erst **bisecten** (`git stash`, Baseline bauen, einspielen).

## Draht und Typen

- **`factory-ui/src/shared/server-api.ts` ist Build-Produkt** (`typescript-generator`, Phase
  `process-classes` in `factory-backend/pom.xml`): nie von Hand edited, aber committet (Eclipse führt `pnpm`
  nie aus). `mvn -o -pl factory-backend process-classes` erzeugt sie neu — ein Java-Feldumbruch bricht
  deshalb `npx tsc -b` statt still den Draht zu ändern.
- **Wire-Regeln sitzen als Bean-Validation-Constraints an den Request-DTOs** (`@Valid` am Endpoint,
  `config/Wire*`, `project/HttpsGitUrl`); die Services prüfen dieselbe Regel **nicht** nach — ein zweiter
  Wortlaut ist der nächste Wartungsfehler. Antwort: `ApiError.fieldErrors[{field,message}]`, `null` = „kein
  Feld". Constraints auf Record-Komponenten nur `@Target(FIELD)`: mit PARAMETER/METHOD meldet dieselbe
  Regel Feld, Accessor **und** Konstruktor (drei Antworten auf einen falschen Wert).

## Backend-Tests

- **`/tmp` ≠ `java.io.tmpdir` auf macOS** (`/tmp → /private/tmp`, JVM-Temp unter `/var/folders/…/T`):
  Schreib-Pinning auf `/tmp/…` beweist das Gegenteil. `@TempDir` liegt **innerhalb** der TMP, ein daraus
  gebautes Home kann die zweite Schreibwurzel nie verlassen (`..` bleibt erlaubt) → Home nach `target/…`,
  „außerhalb" via `TmpRoots.systemTmp()`.
- **Pfad-Ausbruch-Guard: beide Seiten `toRealPath()`** — erst lexikalisch nach Normalisierung (Traversal ⇒
  400, nicht 404), dann real gegen real; eine Seite reicht auf macOS nicht (`/var → /private/var`).
  Symlink binnen der Wurzel 200, nach außerhalb 400.
- **Boot 4**: `ResponseStatusException` läuft **vor** dem `@ControllerAdvice` → eigener Exception-Typ +
  `@ExceptionHandler`, sonst wird 404 zu 500/400. `TestRestTemplate` ist offline unbenutzbar
  (`NoClassDefFoundError` beim Context-Load) → echte HTTP-Tests mit `@SpringBootTest(RANDOM_PORT)` +
  `@LocalServerPort` + `RestTemplate` + `NoOpResponseErrorHandler`. Importe: `…boot.test.web.server`,
  `…boot.resttestclient`, `…boot.webmvc.test.autoconfigure`.
- **`RestTemplate` kann kein `PATCH`** (`ProtocolException`, erscheint als falscher Status) und folgt
  Redirects → beides über `java.net.http.HttpClient` (`Redirect.NEVER`, `method(m, noBody())`).

## Spring-Kontext-Disziplin (Regel in `AGENTS.md`, hier die Mechanik)

- `FactorySpringTest` besitzt allein `@SpringBootTest(RANDOM_PORT)`, `@Import(FakeGitConfig.class)` und die
  **eine** `@DynamicPropertySource`; Unterklassen erben dasselbe `Method`-Objekt → gleicher Cache-Key.
  Kennzahl `missCount = 1` (`-Dlogging.level.org.springframework.test.context.cache=DEBUG`).
- Die Ausnahmliste des `SingleContextGuardTest` prüft **beide** Richtungen (neuer Boot rot, überflüssiger
  Eintrag auch); `SpringApplicationBuilder`-Boots erscheinen nie im Cache. Der Guard scannt den **Pfad**
  (`test-classes` im Source-URI), nicht Namen, Meta-Annotationen über den Namens-Fixpunkt.
- Env-Startwerte über `System.setProperty` (`Environment` liest die Live-Map), im `@AfterEach` zwingend
  `System.clearProperty`. Handgeschriebener Test-Zustand endet im Test (`finally`) — ein vergiftetes
  `projects.json` zerlegt den Reset aller folgenden Klassen auf dem geteilten Home.

## Factory-UI

- **Vor jedem UI-Gate `node -v` (≥ 22) und `pnpm -v` prüfen**: ein frischer nvm-Node bringt keine pnpm-Shims →
  `corepack enable` auf genau diesem Node, PATH-Zeile siehe *Build*. Fehlt beides, bricht das Gate als
  Werkzeug-Crash (`Cannot run program "pnpm"`) vor der ersten Testaussage — das ist kein Testfehler.
  **`antd lint` ist kein `npx`-Lauf** (`npx antd` zieht das UI-Paket — kein Bin-Link →
  `could not determine executable to run`); der CLI liegt global nur unter **v22.23.4**, dort
  `antd lint ./src` fahren (auf dem nvm-Default 22.12 fehlt das `antd`-Binary).
- **Doc-Lint-Baseline ist nicht 0** (❌-UCs kommender Inkremente): findings-frei = **keine neuen** Findings
  und die eigenen UCs von der Liste genommen.
- **AntD-v6-Timeline (Inc 3)**: `Steps` erzwingt `min-height:48px` + `padding-bottom:12px` — Geometrie nur
  im Chat-Scope überschreiben (`.chat-log .chat-line.trace .ant-timeline-item`); `itemPaddingBottom` liest
  AntD **nur im Alternativ-Layout**, die `title`-Prop baut eine Zweitspalte. Beweise nur über
  `data-testid="chat-timeline"`/`data-trace`, nie `.ant-*`; `MAX_TOOL_LINES` kürzt nur Tool-Items.
- **jsdom misst weder Farbe noch Layout** (`getComputedStyle` = `canvastext`, `var()`/Nesting lösen nicht,
  `scrollHeight` = 0): **Look wird nicht als Sheet-Text gepinnt** — keine Farb-Literale in `test/`, eine
  Design-Änderung darf keinen Test anfassen. `test/shared/sheets.ts` bleibt für **Mechanik-Grenzen**
  (`overflow`, `min-height: 0`, „Selektor existiert nicht mehr", `min-height`/`resize` des Composer-Feldes,
  `MAX_ROWS` als Konstante): Blatt via `import.meta.glob('../../src/*.css', { query: '?raw' })`, Helfer
  `declarations`/`block`/`declares` suchen per `indexOf` — kein Selektorwortlaut in Prosa, kein Kommentar
  **im** Block, gruppierte Selektoren (`.a,\n.b {`) treffen sie nicht; **Zustands-CSS über `+`/`~` braucht die
  DOM-Nestung dazu** — die **Kanten-Lage** liegt seit dem Drawer aber in **Positionierung + `:has()`**
  (`.chat-workspace:has(...)`), `previousElementSibling` beweist sie nicht mehr.
- **AntD-Styles wandern im Test aus `document.head`** (`test/shared/setup.ts` ist die Messquelle): **kein Test
  prüft Basis-Regeln über berechnete Stilarten**, eigene Blatt-Mechanik bleibt `?raw`-Text. Schicht-Zahlen der
  Chat-Zeile stehen an **einer** Stelle (Liste über `.chat-workspace` in `styles.css`); eine neue rechte/obere
  Schicht trägt ihre Zahl dort ein, die Überlagerung selbst nimmt der Browser ab.
- **Farb-Modus ist eine Auswahl** (`src/shared/theme.mode.ts`, `localStorage['aifactory.theme']`, Startwert =
  Betriebssystem, keine Live-Folge); `main.tsx` schreibt `data-theme` **vor** dem Render ⇒ keine
  `prefers-color-scheme`-Doppeltabelle. `test/shared/app.harness.tsx` mountet darum `AppRoot` (hält Modus,
  Basis und `ThemeModeContext`, den `useThemeMode()` verlangt).
- **AntD unter jsdom**: `matchMedia` **und** `ResizeObserver` sind load-bearing (jsdom 26.1.0: Property da,
  Wert `undefined`); `Select` über `<id>_list` (`id` je Choice Pflicht, `useId()` ist im Testbaum nicht
  eindeutig), Helfer `test/shared/choices.ts`; `Layout.Sider` malt ohne `width` schon **200 px**, ein
  `'200px'`-Pin beweist nichts. Tokens ohne Dunkel-Zeile (Fonts) fallen auf hell zurück. `Spin` legt
  `classNames.indicator` auf das **geklonte** Indikator-Element (`spin/Indicator/index.js`) — die Klasse sitzt
  **unter** dem Test-Handle, `toHaveClass` am Handle wäre grün trotz Defekt; ein `aria-hidden`-Wrapper blendet
  Rollen-Queries aus ⇒ über `getByTestId` + `querySelector` beweisen, nie über `getByRole`.
- **Markdown/Shiki**: `remarkPlugins`/`components` als Modul-Konstante (stabile Props), `remark-breaks`
  Pflicht (v10 hat keine `breaks`-Option), rohes HTML bleibt Wortlaut; `white-space: normal` auf
  `.chat-log .chat-markdown` ist tragend und Markdown zerteilt Text in Spans ⇒ **Zeile** über
  `textContent`; `react-shiki` über `/web`, `engine="javascript"`, Fremdsprachen still (`code.language.ts`).
- **Vitest** kennt kein `.as(…)` → Nachricht als **zweites** `expect`-Argument (`.as` ist AssertJ/Backend).
  **oxlint**: `react(only-export-components)` — eine Datei mit Komponenten-Export exportiert keine
  Funktionen/Hooks (auch im `test`-Baum nicht), Konstanten dürfen; `react(set-state-in-effect)` verboten
  (Projektwechsel remountet die Chat-Seite über `key={project.id}`).
- **Vitest nie über den Dateipfad fahren** (`pnpm test -- test/chat/x.test.tsx` ⇒ jsdom
  `Could not parse CSS stylesheet` über die Dateigrenze, der Test läuft gar nicht): einzelne Fälle mit
  `pnpm test -t '<test-name>'` filtern (fährt die Suite), Gleiches für `npm test`.

- **AntD 6 `Form` ersetzt die native Hülle** (kein `component={false}`; das `<form>` kommt aus
  `@rc-component/form`, `Form.Item` ohne `Form` als Vorfahr loggt je Mount). Server-Fehler zeichnet
  `shared/api.error.tsx` (`ApiField` = `Form.Item` ohne `name`, `ApiFormErrors` = Alert ohne Feldbezug); die
  Submit-Kette des `Form` antwortet **einen Microtask später** als `onSubmit` — ein Test, der den Body
  synchron aus dem Call-Log liest, wird rot. `Form.Item`-Klassen: `className` an der Wurzel, fremde Props am
  `…-row`; der Status erreicht das `Input` nur als Klasse (kein `aria-invalid`).
- **Routing macht Navigation async** → `findBy*`. Quellen-Scan nur über `import.meta.glob` mit `?raw`
  (`node:fs`/`process` sind in `src`+`test` nicht typisiert). **Kein Mount-Beweis über
  Schwesterrouten:** gleiche Komponente an derselben Stelle behält React Router die Instanz — „bleibt
  gemountet" beweist nur ein erzwungener Remount (`key={pathname}`). **Ein Klick-Test braucht eine Behauptung,
  die der Nicht-Klick nicht erfüllt** (andere Adresse, entferntes Element): auf derselben Adresse ist ein
  toter Eintrag ein grüner No-op.
- **Zwei Drähte enden auf `/events`** — `/api/projects/events` (Arbeitsstand, Rahmen) und
  `/api/projects/<id>/events` (Chat): Ströme und Reads **projektbezählt** zählen (`/${project().id}/events`).
- **Chat-Draht-Griffe nicht umbenennen**: `chat-line-jon` / `chat-live` / `chat-line-author` (Stimme
  user/agent = **Klasse**, Draht = `data-testid`). Verlaufszeilen nie mit `toContain` (Autor-Label hängt an
  der `li`) → `[data-testid="chat-line-user"] .chat-text` als exakte Liste. Trenner zählt allein
  `test/shared/log.parts.ts` → `separatorsOf(log)`; ein `<hr>` gehört in die `<li>`, nie dazwischen.
- **Der Blick auf ein Mitglied hat keine eigene Zeichenschicht** (R-WP-53, `docs/chat.md`): `chat.insight.tsx`
  zeichnet ausschließlich über `ChatLog` (`testId="insight-log"`, `userAuthor={null}`, `openTurn={null}`) und
  subscribiert erst **nach** dem Snapshot-Read; eigenes Blick-Markup, eigene Blick-Wörter und ein Scroll-Button
  dort sind verboten. Sein Strom endet nie ein Turn — kein Indikator darf Arbeit behaupten.
- **Modellierte Draht-Form braucht die erzeugende Core-Regel** (Datei:Zeile am Test), sonst prüft der Test
  eine Form, die der Core nie liefert (`docs/adr/003`).
- **Modell-Feld ist eine Liste, kein Anhängen** (R-WP-50): `ModelField` zeigt ausschließlich Server-Ids als
  Optionen und kanonisiert case-insensiv auf den Servereintrag; ein getippter Fremdwert bleibt Freitext im
  Feld, wird aber nie Option. Kein Fetch beim Mount — `settingsShowsMaskedConfig` (drei GETs) ist der Guard.
- **Werkzeug-Vorschau**: `GET /api/settings/agents/{id}/tools?allowlist=` nennt die aktiven Namen zum
  getippten Wortlaut (leer = volle Liste; `compact`/unbekannt ⇒ 400). Eine Quelle ist
  `AbstractAgent.resolveActiveTools` — die UI filtert nichts selbst, der Kern deutet den Wortlaut (D9).
- **jsdom bleibt auf 26.1.0 gepinnt**: jsdom 30 verlangt Node ≥ 22.22.2 → undici-Crash `reading 'bind'`.
  `vite.config.ts` braucht `defineConfig` aus `vitest/config`, sonst findet `tsc -b` das `test`-Feld nicht.

## Abweichungs-Register (UI)

Ein Code im Testkommentar verweist auf diese Stelle; die Begründung steht am Test selbst.

- `ANTD-A1` Nav-Rolle: `Menu` rendert `li[role=menuitem]`, Auswahl nur als `.ant-menu-item-selected`; das
  eingefrorene `A1` in `test/chat/chat.page.test.tsx` (Sperre `test/chat/**`) ist derselbe Fall, umbenennen
  verboten. — `ANTD-A4` Icons über `.anticon-*`, nicht `bi bi-*`.
- `ANTD-A9` Markierung am `Menu`-Eintrag: die Basis setzt kein `aria-current`, reicht unbekannte Item-Props
  aber ans `li[role=menuitem]` durch — Beweis ist das Attribut **und** `.ant-menu-item-selected` (`NavItem`).
- `ANTD-A2` Popconfirm statt eigenem Lösch-Popup. — `ANTD-A3` Choice: Label an `.ant-select-content[title]`,
  Drahtwert nur im Body (`content.config.yes` ≠ `'true'`).
- `ANTD-A5` offenes Syntax-Zeichen (`**`) steht im Stream als Wortlaut; rohes HTML wird nie Markup.
- `ANTD-A6` `div.chat-markdown-table` = unser Scroll-Kasten je Tabellen-Antwort; bewiesen wird *wer*
  scrollt, nicht die Breite. — `INC7B-A1` Dev-Label „Think (Default)" statt `content.agents.think`.
- `ANTD-A7` Enter sendet; Shift+Enter und Ctrl/Cmd+Enter brechen um (Umkehrung R-WP-30; Spender: jeder Enter).
- `ANTD-A8` Auto-Grow-Feld der Basis: Höhe und `resize` stehen inline auf dem Feld; Fläche bleibt unser Blatt.

## Werkzeuge

- `eclipseEditFile`/`eclipseReplaceLines` mit **Teilzeile** als `oldString` haben schon Nachbarzeilen
  beschädigt → Region danach neu lesen. `eclipseEditFile` verliert `oldString` bei langen Blöcken →
  `diskEditFile` mit kurzem Anker. `*InsertLines` scheitern an einer Datei, die mit Leerzeile endet. **Löschen** heißt bei `*EditFile` `newString: ""` —
  ein `"\\n"` als Ersatz lässt die geleerte Zeile stehen (16× gemessen; Rückweg nur
  `git checkout -- <file>`, und das nur bei einer vorher unveränderten Datei).
- **Erfolgsmeldung eines `*EditFile` ist kein Beleg**: Echo und Fehlerdump beschreiben den **Vor**-Zustand,
  und schon einmal war ein früherer Edit derselben Datei still weg (gemessen 2026-11-09). Also nach jedem
  Edit an einer in derselben Sitzung bereits editierten Datei die Region mit `diskReadFile`/`git diff`
  gegenprüfen (Maven-Bau als zweite Wahrheit) — ein `not found` des Tools ist der gute Fall: er nennt den
  echten Inhalt.
- **Nie eine Quelldatei aus dem Kontext neu schreiben** (`*WriteFile` ohne Vorher-Lesen): der Kopf wird
  erfunden. Kleine `*EditFile`-Schritte oder erst komplett lesen; dann `git diff <file>` gegen die geplanten
  Änderungen — `git checkout -- <file>` ist kein Rückweg für fremde Handänderungen.
- **`content.ts`-Anker sind nie eindeutig**: `config` und `agents` tragen dieselben Feldwörter (`Model`, `URL`,
  `Think`) — ein `*EditFile`-Anker dort trifft beide Gruppen (gemessen: replaced 2), immer die Folgezeile mit
  in den Anker nehmen.
- **`eclipseWriteFile` mit Workspace-Pfad (`/proj/…`) legt eine **neue** Datei unter `<cwd>/proj/…` an**
  (Pfad-Dopplung, gemessen 2026-11-08: `aifactory/aifactory/factory-ui/...`); vorhandene Dateien finden dieselben
  Tools dagegen korrekt. Neue Dateien darum mit `diskWriteFile` und absolutem Pfad schreiben, danach
  `git status` — die duplizierten Leerverzeichnisse sind sonst der einzige Hinweis.
