## aifactory — Bau-Fakten

Nur was **unabhängig vom Plan** gilt — kein Plan-/Inkrement-Bezug, kein Status, kein Datum. Was nur diesen
Zyklus angeht (Messungen, Proben, Fortschritt), gehört ins **Plan-File** `peon-plan/overview.md`,
Entscheidungen nach `docs/`. Bestehenden Eintrag ergänzen statt anhängen, Veraltetes löschen, < 100 Zeilen.

## Build

- **pnpm auf dem PATH**, sonst `factory-ui FAILURE: Cannot run program "pnpm"` vor jedem Backend-Modul:
  `export PATH=~/.nvm/versions/node/v22.12.0/bin:$PATH` + `COREPACK_ENABLE_PROJECT_SPEC=0` (corepack 0.33 in
  Node 22.12 kennt pnpm ≥ 11 nicht). **Aktiv: pnpm 10.28.0** (`lockfileVersion: '9.0'`); pnpm 12 ist kein
  Drop-in (`ERR_PNPM_PACKAGE_MANAGER_REMOVE_MODULES_DIR`). Das ungetrackte Root-`package.json` ist nicht uns.
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

## Backend-Tests

- **`/tmp` ≠ `java.io.tmpdir` auf macOS** (`/tmp → /private/tmp`, JVM-Temp unter `/var/folders/…/T`):
  Schreib-Pinning auf `/tmp/…` beweist das Gegenteil. `@TempDir` liegt **innerhalb** der TMP, ein daraus
  gebautes Home kann die zweite Schreibwurzel nie verlassen (`..` bleibt erlaubt) → Home nach `target/…`,
  „außerhalb" via `TmpRoots.systemTmp()`.
- **Pfad-Ausbruch-Guard: beide Seiten `toRealPath()`** — erst lexikalisch nach Normalisierung (Traversal ⇒
  400, nicht 404), dann real gegen real; eine Seite reicht auf macOS nicht (`/var → /private/var`).
  Symlink binnen der Wurzel 200, nach außerhalb 400.
- **Lange absolute Pfade erreichen den SSE-Draht nicht**: `SmartToolExecutor.reportProblem` kürzt auf 180
  Zeichen, `allowed roots […]` fällt weg (das Modell sieht sie weiter) → Draht-Verweigerung am Präfix
  `<tool>: Write denied:` prüfen, Wurzeln nur im nächsten Provider-Payload (`getCapturedMessages()`).
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
- `@Timeout` gilt je Testmethode, **nie** für Lifecycle-Methoden → dafür steht
  `junit.jupiter.execution.timeout.lifecycle.method.default=120s` in `junit-platform.properties`.
- Env-Startwerte über `System.setProperty` (`Environment` liest die Live-Map), im `@AfterEach` zwingend
  `System.clearProperty`. Handgeschriebener Test-Zustand endet im Test (`finally`) — ein vergiftetes
  `projects.json` zerlegt den Reset aller folgenden Klassen auf dem geteilten Home.

## Factory-UI

- **Doc-Lint-Baseline ist nicht 0** (❌-UCs kommender Inkremente): findings-frei = **keine neuen** Findings
  und die eigenen UCs von der Liste genommen.
- **jsdom misst weder Farbe noch Layout** (`getComputedStyle` = `canvastext`, `var()`/Nesting lösen nicht
  auf, `scrollHeight` = 0): Farb-/Maßverträge aus dem **Stylesheet-Text** lesen
  (`import.meta.glob('../../src/*.css', { query: '?raw' })`) und über Tokens schließen; eine Grenze wie
  `MAX_ROWS` wird als **Konstante** gepinnt, nie als Pixel. `?raw` auf `.css` liefert leer, solange
  `test.css` aus ist; `test.css.include` verlangt **RegExp** und muss beim Raw-Laden `\?|$` zulassen. Kein
  Selektorwortlaut in CSS-Prosa — die Helfer (`declarations`, `blockOf`, `tokenTable`) suchen per
  `indexOf(selector)`; `tokenTable` akzeptiert einen Selektor erst bei Whitespace + `{`.
- **AntD unter jsdom**: `matchMedia` **und** `ResizeObserver` sind load-bearing (jsdom 26.1.0: Property da,
  Wert `undefined`); `Select` über `<id>_list` (`id` je Choice Pflicht, `useId()` ist im Testbaum nicht
  eindeutig), Helfer `test/shared/choices.ts`. Dunkel ist **eine** Tabelle über `data-theme` am Document;
  `darkAlgorithm` leitet den Akzent ab („Token == Literal" ist im Dunkel nicht prüfbar — eigener Vergleich +
  `hell !== dunkel`), Tokens ohne Dunkel-Zeile (Fonts) fallen auf hell zurück.
- **Markdown/Shiki**: `remarkPlugins`/`components` als Modul-Konstante (stabile Props), `remark-breaks` ist
  Pflicht (v10 hat keine `breaks`-Option), rohes HTML bleibt Wortlaut, `white-space: normal` auf
  `.chat-log .chat-markdown` ist tragend; Markdown zerteilt Text in Spans ⇒ **Zeile** über `textContent`.
  `react-shiki` über `/web`, `engine="javascript"` (jsdom-tauglich); Sprachen außerhalb des Web-Sets rendern
  still Text (`shared/code.language.ts`), `shiki` ist bei pnpm strict nicht direkt importierbar.
- **Vitest** kennt kein `.as(…)` → Nachricht als **zweites** `expect`-Argument (`.as` ist AssertJ/Backend).
  **oxlint**: `react(only-export-components)` — eine Datei mit Komponenten-Export exportiert keine
  Funktionen/Hooks (auch im `test`-Baum nicht), Konstanten dürfen; `react(set-state-in-effect)` verboten
  (Projektwechsel remountet die Chat-Seite über `key={project.id}`).
- **Quellen-Scan in UI-Tests** nur über `import.meta.glob(…, { query: '?raw', eager: true })` —
  `node:fs`/`node:path`/`process` sind in `src`+`test` nicht typisiert.
- **Routing macht Navigation async** → `findBy*`. „Could not parse CSS stylesheet" bei kaltem vite-Cache ist
  Rauschen: erst `rm -rf node_modules/.vite` auf HEAD vergleichen.
- **Chat-Draht-Griffe nicht umbenennen**: `chat-line-jon` / `chat-live` / `chat-line-author` (Stimme
  user/agent = **Klasse**, Draht = `data-testid`). Verlaufszeilen nie mit `toContain` (Autor-Label hängt an
  der `li`) → `[data-testid="chat-line-user"] .chat-text` als exakte Liste. Trenner zählt allein
  `test/shared/log.parts.ts` → `separatorsOf(log)`; ein `<hr>` gehört in die `<li>`, nie dazwischen.
- **Modellierte Draht-Form braucht die erzeugende Core-Regel** (Datei:Zeile am Test), sonst prüft der Test
  eine Form, die der Core nie liefert (`docs/adr/003`).
- **jsdom bleibt auf 26.1.0 gepinnt**: jsdom 30 verlangt Node ≥ 22.22.2 → undici-Crash `reading 'bind'`.
- **`defineConfig` aus `vitest/config`** in `vite.config.ts` — sonst findet `tsc -b` das `test`-Feld nicht.

## Abweichungs-Register (UI)

Ein Code im Testkommentar verweist auf diese Stelle; die Begründung steht am Test selbst.

- `ANTD-A1` Nav-Rolle: `Menu` rendert `li[role=menuitem]`, Auswahl nur als `.ant-menu-item-selected`; das
  eingefrorene `A1` in `test/chat/chat.page.test.tsx` (Sperre `test/chat/**`) ist derselbe Fall, umbenennen
  verboten. — `ANTD-A4` Icons über `.anticon-*`, nicht `bi bi-*`.
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
  `diskEditFile` mit kurzem Anker. `*InsertLines` scheitern an einer Datei, die mit Leerzeile endet.
