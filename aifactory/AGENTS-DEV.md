## aifactory (Spring-Boot-Reaktor) — Build-Fakten (inc-1, 2026-10-25)

- **`mvn -f aifactory/pom.xml …` braucht pnpm auf dem PATH** (factory-ui fährt pnpm-install/-build via
  exec-maven-plugin): `export PATH=~/.nvm/versions/node/v22.12.0/bin:$PATH`, sonst `factory-ui FAILURE:
  Cannot run program "pnpm"` vor jedem Backend-Modul.
- **Kein `-parameters` im aifactory-Build** (Parent ist `llmpeon-parent`, nicht
  `spring-boot-starter-parent`): `@PathVariable`/`@RequestParam` ohne expliziten Namen schlagen in
  Boot 4 fehl — der Fehler ist eine `IllegalArgumentException` und landet damit im 400-Handler, nicht
  als 500. Immer Namen explizit schreiben (`@PathVariable("id")`) oder dem aifactory-pom
  `<parameters>true</parameters>` geben.
- **`mvn clean`, wenn ein Test `NoClassDefFoundError: <SimpleTypeName>` (ohne Paket) meldet**: Eclipse
  JDT schreibt nach `target/classes` und hinterlässt bei Auflösungsfehlern Class-Files mit
  unqualifizierten Typ-Namen; der Maven-Incremental-Compile hält sie für aktuell.
- **Boot 4 behandelt `ResponseStatusException` vor dem `@ControllerAdvice`** → eigener Exception-Typ
  (z. B. `ProjectNotFoundException`) + `@ExceptionHandler`, sonst wird aus dem 404 ein 500/400.
- **UI-Test-Setup:** jsdom auf 26.1.0 gepinnt (jsdom 30 verlangt Node ≥22.22.2 → undici-Crash
  „reading 'bind'"); `vite.config.ts` importiert `defineConfig` aus `vitest/config`, sonst findet
  `tsc -b` das `test`-Feld nicht.
- **`gitea.sterl.co` löst auf dieser Maschine nicht auf** (DNS; github/maven-central gehen) —
  Clone-Beweise gegen das interne Gitea nur mit aktivem VPN, sonst gleichwertigen öffentlichen Repo
  benutzen und im Report ausweisen.
- **`spring-boot:run` erst nach `install`:** `mvn -f aifactory/pom.xml -pl factory-backend
  spring-boot:run` braucht `org.sterl.open-ai-factory:factory-ui` im `~/.m2` — der Jar ist die
  Classpath-Quelle für `classpath:/static/ui/**`, ohne ihn fehlt die SPA im laufenden Server. Erst
  `mvn -f aifactory/pom.xml install -DskipTests`, dann `spring-boot:run`.
- **Boot 4.1: `TestRestTemplate` ist offline nicht benutzbar** — es liegt in
  `spring-boot-resttestclient`, dessen `@AutoConfigureTestRestTemplate`
  `org.springframework.boot:spring-boot-restclient` (`RestTemplateBuilder`) braucht; das deklariert das
  Artefakt **nicht** und liegt nicht in `~/.m2` → `NoClassDefFoundError` beim Context-Load
  (`Tests run: n, Errors: n`, nicht blau). Echte HTTP-Tests deshalb mit
- **`/tmp` is NOT `java.io.tmpdir` on macOS** (measured 2026-10-26, inc-4a-3): `/tmp → /private/tmp`,
  while the JVM's temp root is `/var/folders/<…>/T`. A write-pinning proof aimed at `/tmp/…` proves the
  opposite of what it claims (it gets DENIED, being outside both factory roots). Same trap inverts for
  test dirs: `@TempDir` lives *inside* `java.io.tmpdir`, so a project home built on it can never escape
  the second write root (`..` stays allowed). Use `target/…` for a home, `TmpRoots.systemTmp()` (or a
  `resolveSibling`) for outside, and never `@TempDir` for either.
- **Long absolute paths never reach the SSE wire** (measured 2026-10-26, inc-4a-3):
  `SmartToolExecutor.reportProblem` truncates the monitor copy at 180 chars + `…`, so a denial line with
  an 88-char root loses its `allowed roots […]` list on the wire while the model still gets the full
  text. Assert wire denials at the stable prefix (`<tool>: Write denied:`) and roots/paths only in the
  next provider payload (`MockLlmServer.getCapturedMessages()` → `ToolExecutionResultMessage.text()`).

  `@SpringBootTest(RANDOM_PORT)` + `@LocalServerPort` + `org.springframework.web.client.RestTemplate`
  und `NoOpResponseErrorHandler` (sonst versteckt eine Exception die Status-Assertion), und
  Redirects mit `java.net.http.HttpClient` (`Redirect.NEVER`) prüfen — ein folgender Client macht den
  Redirect unsichtbar. Boot-4-Importe: `LocalServerPort` = `org.springframework.boot.test.web.server`,
  `TestRestTemplate` = `org.springframework.boot.resttestclient`,
  `@AutoConfigureMockMvc` = `org.springframework.boot.webmvc.test.autoconfigure`.
- **`RestTemplate` kann kein `PATCH` senden** (gemessen 2026-10-26, inc-4b-1): der underlying
  `HttpURLConnection` wirft `ProtocolException: Invalid HTTP method: PATCH`, und die Exception versteckt sich
  hinter `NoOpResponseErrorHandler` als unerwarteter Status. Methoden-Matrix-Tests (405 auf
  `PUT`/`POST`/`PATCH`/`DELETE`) deshalb über `java.net.http.HttpClient` (`HttpRequest.newBuilder().method(m,
  noBody())`) — der beherrscht alle vier.
- **Lese-Guard gegen Pfad-Ausbruch: beide Seiten `toRealPath()`** (inc-4b-1, Mutation belegt): erst
  lexikalische Containschaft **nach** Normalisierung (davon ⇒ Traversal wird 400, nicht 404, auch wenn das Ziel
  nicht existiert), dann `candidate.toRealPath()` gegen `root.toRealPath()`. Nur eine Seite aufzulösen ist auf
  macOS falsch (`/var → /private/var`): der TMP-Test fällt mit NPE/404, obwohl jede Datei da ist. Ein Symlink
  **innerhalb** der Wurzel **nach außerhalb** muß 400 werden, einer **binnen** der Wurzel 200 — sonst ist der
  Guard ein Fehlalarm-Generator und der Test nur aufs Scheitern geprüft.

## Eclipse schreibt kaputte Klassen — Maven ist die Wahrheit

* Nach `eclipseBuildProject`/`eclipseEditFile` kann `target/classes` **Lombok-blankierte Fehler-Stümpfe**
  enthalten. Ein Maven-Lauf **ohne** `clean` hält sie für aktuell; der laufende Server antwortet dann
  `500 "Unresolved compilation problems"` (gemessen: `JonToolSet`, `model.getConfig`). Deshalb **jeder**
  Maven-Lauf mit `clean`, besonders `verify` und `spring-boot:run`.
* Eclipse meldet Lombok-Getter (`LlmConfig`, `ConfiguredChatModel`) trotzdem als „undefined" — nicht
  danach gehen, Maven ist die Wahrheit.
* Nach einer Änderung im Kern (`org.sterl.llmpeon.core`) **zuerst**
  `mvn -f org.sterl.llmpeon.core/pom.xml clean test`, dann `mvn -o -pl org.sterl.llmpeon.core install
  -DskipTests` — sonst baut die Fabrik gegen das alte `~/.m2`-Artefakt.
* „Das war schon vorher so" ist eine Behauptung: erst **bisecten** (`git stash`, Baseline bauen, wieder
  einspielen), dann melden. Ein gemessener Hinweis (z. B. ein 30-s-Hang) wird mit Ursache belegt, nicht
  als „vorbestehend" abgehakt.
* `eclipseEditFile`/`eclipseReplaceLines` mit einer Teilzeile als `oldString` haben schon Nachbarzeilen
  beschädigt — nach jedem Edit die Region neu lesen.

## Factory-UI: Testkette und dauerhafte Fallstricke

Episodisches (Messungen, Proben, je-Inkrement-Befunde) steht in `aifactory/peon-plan/dev-notes-antd.md` —
nicht hier. Alles in diesem Abschnitt gilt jedem UI-Build.

- **Doc-Lint-Baseline ist nicht 0 Findings:** die Docs definieren UCs mit ❌, die erst spätere Inkremente
  abdecken. Ein Inkrement ist findings-frei, wenn es **keine neuen** Findings erzeugt und seine eigenen UCs
  aus der Liste nimmt — nicht bei 0.
- **`[ERROR] Surefire is going to kill self fork JVM … 30 s after System.exit(0)`** im Backend-Lauf ist
  bestehendes Teardown-Rauschen: `mvn -o clean test` endet trotzdem mit `BUILD SUCCESS`, exit 0.
- **Vitest kennt kein `.as(…)`**: `expect(x).as('msg')` wirft `Invalid Chai property: as`. Die
  Assertion-Nachricht ist das **zweite Argument** — `expect(offenders, 'why').toEqual([])`. `.as(…)` ist
  AssertJ und gilt nur im Backend-Lauf.
- **Quellen-Scan in UI-Tests ohne Node-Typen**: `tsconfig.app.json` setzt `types: ["vite/client", …]`,
  `node:fs`/`node:path`/`process` sind in `src`+`test` nicht typisiert. Der Scan läuft durch Vitest **und**
  `tsc -b` über `import.meta.glob('../../src/**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true })`
  — `fileURLToPath(import.meta.url)` bringt `TypeError: The URL must be of scheme file`.
- **Catalog-Wert ≠ Feldwert** (`ANTD-A3`): ein Choice zeigt das Label und reist mit dem **Draht-Wert** ab.
  Label liest man an `.ant-select-content[title]`, den Drahtwert nur im Request-Body — ein Test, der das
  Feld liest, darf nie den Catalog-Wert erwarten.
- **oxlint `react(only-export-components)`**: eine Datei, die eine Komponente exportiert, darf keine
  Funktionen/Hooks exportieren (auch nicht im `test`-Baum) — Ausgliederung in eigene Dateien.
  **Konstanten-Exporte sind erlaubt.**
- **Routing macht Navigation async**: ein Klick rendert die Zielseite erst nach der Datenlesung — `findBy*`
  statt `getBy*`, `waitFor` mit Standard-1000 ms reicht.
- **jsdom beweist keine Farbe**: `getComputedStyle(…).borderLeftColor` ist `"canvastext"`, `var()` und
  natives Nesting werden nicht ausgewertet. Farb- und Maßverträge werden aus dem **Text des Stylesheets**
  gelesen (`import.meta.glob` über `../../src/*.css` mit `query: '?raw'`) und über die Tokens geschlossen.
- **`?raw` auf `.css` liefert einen leeren String, solange `test.css` aus ist.** `test.css.include`
  verlangt **RegExp**-Einträge (Strings lassen `vitest:css-disable` abstürzen), und die id trägt beim
  Raw-Laden ihre Query — die Regex muss `\?|$` am Ende zulassen.
- **Kein Selektorwortlaut in CSS-Prosa** (`styles.css` wie `index.css`): die Blatt-Helfer
  (`declarations`, `blockOf`, `tokenTable`) suchen per `indexOf(selector)` — ein Selektorwortlaut in einem
  Kommentar oder Prosa-Satz liest die falsche Regel. `tokenTable` akzeptiert einen Selektor erst, wenn ihm
  nur Whitespace + `{` folgt; die helle Tabelle ist als gruppierte Regel anzugeben.
- **Ein `<hr>` gehört in die `<li>`, nie zwischen zwei `<li>`** (ein `<ul>` darf nur `li` als Kinder) —
  die Zeile, die eine neue Stimme beginnt, trägt ihr `hr` selbst. Gilt bis Inc 4.
- **Die Draht-Griffe der Chat-Zeilen nicht umbenennen**: `chat-line-jon` / `chat-live` /
  `chat-line-author` sind die Griffe von Dutzenden Tests. Die Stimme (user/agent) ist die **Klasse**, der
  Draht (jon/user) der `data-testid`.
- **Verlaufszeilen nie über `toContain` vergleichen**: der `li`-`textContent` hängt den Autor-Label an, und
  eine Nutzereingabe ist **Substring** der Marker-Zeile — abzufragen ist
  `[data-testid="chat-line-user"] .chat-text` als exakte Liste.
- **Modellierte Draht-Form braucht die erzeugende Core-Regel:** bildet ein UI-Test eine Verlaufs- oder
  Draht-Form nach, steht an der Testdatei die Core-Regel mit Datei:Zeile, die diese Form erzeugt — sonst
  prüft der Test eine Form, die der Core so nie liefert (`docs/adr/003` verweist darauf).
- **`react-shiki` über `/web`** statt des Haupteinstiegs: identischer Main-Chunk, deutlich weniger Chunks
  und `dist`-Größe; `engine="javascript"` läuft in jsdom, unbekannte Sprache ⇒ stiller Plain-Text. Preis:
  Grammatiken außerhalb des Web-Sets (`go`, `rust`, `kotlin`, `diff`, `dockerfile`, `toml`, `ini`) rendern
  als Text — `shared/code.language.ts` bildet genau diese Liste ab, alles andere ⇒ `text`; zurück auf den
  Voll-Bau ist ein Importpfad. `shiki` ist bei pnpm strict nicht direkt importierbar (Transitiv-Dep).
- **Markdown zerteilt den Text** in Token-Spans: `getByText`/`findByText` trifft einen Agenten-Satz nicht —
  die **Zeile** über ihre `textContent` abfragen.
- **Projektwechsel remountet die Chat-Seite über `key={project.id}`**: ein `setLive([])` im Effect-Body ist
  damit überflüssig und verstößt gegen oxlint `react(set-state-in-effect)`.
- **„Could not parse CSS stylesheet" in jsdom ist vorbestehend**, wenn der vite-Cache kalt ist (natives
  CSS-Nesting in `styles.css`): erst `rm -rf node_modules/.vite` auf HEAD vergleichen, bevor ein Inkrement
  beschuldigt wird.
- **AntD unter jsdom**: `matchMedia` **und** `ResizeObserver` sind load-bearing (jsdom 26.1.0 — die Property
  existiert, ihr Wert ist `undefined`); ein `Select` wird über `<id>_list` adressiert, nicht über
  `aria-controls` (Helper `test/shared/choices.ts`, und `id` je Choice ist Pflicht — `useId()`-Werte sind im
  Testbaum nicht eindeutig). Die Bootstrap-Wache liegt in `test/shared/design.basis.ts`.
- **Dunkel ist eine Tabelle**, wirksam über `data-theme` am Document-Element; `darkAlgorithm` leitet den
  Akzent ab („Token == Tabellen-Literal" ist im Dunkel nicht prüfbar — eigener Vergleich plus
  `hell !== dunkel`), und Tokens ohne Dunkel-Zeile (Fonts) fallen auf die helle Tabelle zurück.
- **`react-markdown`**: `remarkPlugins` und `components` sind Modul-Konstanten als **stabile Props** — der
  Processor wird pro Render gebaut (`react-markdown/lib/index.js`), Identitäts-Memoisierung steckt in
  `MarkdownHooks`, das die App nicht nutzt. `remark-breaks` ist Pflicht (v10 hat keine `breaks`-Option),
  rohes HTML bleibt Wortlaut, `white-space: normal` auf `.chat-log .chat-markdown` ist tragend.
- **Trenner nur an einer Stelle zählen**: `test/shared/log.parts.ts` → `separatorsOf(log)` ist die einzige
  Definition (jedes `hr` außerhalb von `.chat-markdown`); Inc 4 beweist darüber `[]`.

## Spring-Tests: Kontext-Disziplin

Die harte Regel steht in `aifactory/AGENTS.md` (Kontext höchstens einmal je Build, keine Restarts). Hier
stehen nur die Mechanik und die Fallstricke, die nicht aus dem Code ableitbar sind.

- **Ein Kontext je Build:** `FactorySpringTest` (abstract) besitzt allein `@SpringBootTest(RANDOM_PORT)`,
  `@Import(FakeGitConfig.class)` und die **eine** `@DynamicPropertySource`; Unterklassen erben die statische
  Methode → gleiches `Method`-Objekt → derselbe Cache-Key. Kennzahl: `missCount = 1` mit
  `-Dlogging.level.org.springframework.test.context.cache=DEBUG`.
- **Ausnahmliste von `SingleContextGuardTest`** (`ChatRestartTest`, `DelegatedAnswerRestartTest`,
  `ProjectStatusTest`) — sie prüft in **beide** Richtungen: ein neuer Boot ist rot, ein überflüssiger
  Eintrag auch. `SpringApplicationBuilder`-Boots erscheinen **nie** im Context-Cache.
- **Der Guard scannt einen Pfad, keinen Namen:** alle Klassen mit `test-classes` im Source-URI, nicht die
  `*Test`-Endung; Meta-Auflösung über den Namens-Fixpunkt der eigenen Annotationstypen, denn
  `JavaClass.getAnnotations()` sind nur die direkt gesetzten.
- **Klassenweites `@Timeout` wirkt je Testmethode, nie auf Lifecycle-Methoden** — die Grenze für
  `@BeforeAll`/`@BeforeEach`/`@AfterEach`/`@AfterAll` (und damit den Context-Start) setzt
  `factory-backend/src/test/resources/junit-platform.properties` mit
  `junit.jupiter.execution.timeout.lifecycle.method.default=120s`.
- **`@TempDir` ist nie das Fabrik-Home** (es liegt innerhalb `java.io.tmpdir`, siehe Build-Fakten) — das
  geteilte Home muss außerhalb liegen, sonst kann `..` die zweite Schreibwurzel nie verlassen.
- **Env-Startwerte über `System.setProperty`, nicht über Dynamic Properties** (`Environment` liest die
  Live-Map) — Pflicht danach: `@AfterEach` + `System.clearProperty`, ein Leak ist eine zweite Antwort auf
  `AIFACTORY_LLM_*` in einer fremden Klasse.
- **Handgeschriebener Test-Zustand muss im Test enden** (`finally`): ein vergiftetes `projects.json` wirft
  schon beim Lesen und zerlegt auf dem geteilten Home den Reset aller folgenden Klassen.

## Toolchain corepack/pnpm (2026-11-05) — warum `pnpm` im ganzen Repo tot war

- **Ursache ist corepacks Bin-Map, kein partieller Download**: corepack **0.33.0** (in Node 22.12.0
  gebündelt) kennt für pnpm nur die Range `>=6.0.0` und schreibt die Bin-Map beim Prepare fest in
  `~/.cache/node/corepack/v1/pnpm/<v>/.corepack` → `pnpm: ./bin/pnpm.cjs`. **pnpm ≥ 11 liefert
  `bin/pnpm.mjs`** (Wrapper um das Native-Binary, Manifest-`bin` zeigt auf Shell-Platzhalter) →
  `MODULE_NOT_FOUND`. Bewiesen: Cache-Ordner gelöscht + `corepack prepare pnpm@12.16.1 --activate` gegen
  das Registry-Netz schreibt exakt dieselbe falsche Map. Auslöser war `corepack use pnpm@12.16.1`, das
  zusätzlich ein **ungetracktes Root-`package.json`** (`packageManager: pnpm@12.16.1+sha512…`) erzeugt hat —
  corepack läuft walk-up, das Feld gilt damit auch für `factory-ui` — und `lastKnownGood.json` auf 12.16.1
  stellte.
- **Ein corepack-Upgrade allein hilft nicht**: die Map des **aktiven** corepack gewinnt (der Shim
  `~/.nvm/versions/node/v22.12.0/bin/pnpm` → `lib/node_modules/corepack`). corepack 0.36 hat die Range
  `>=11.0.0 → ./bin/pnpm.mjs`, verlangt aber Node `^22.22.2 || ^24.15.0 || >=26` (hier 22.12.0; der
  `node` außerhalb von nvm ist 18.12.1 aus `/usr/local/bin`).
- **pnpm 12 ist kein Drop-in**: der erste Install will `node_modules/.pnpm` vollständig entfernen (neue
  Modules-Dir) und scheitert auf macOS mit `ERR_PNPM_PACKAGE_MANAGER_REMOVE_MODULES_DIR … Directory not
  empty (os error 66)`; `node_modules` bleibt halbvoll zurück und muss mit 10.28.0 neu installiert werden.
  Lauffähig ist pnpm 12 nur **am corepack vorbei** (`npm i -g pnpm@12.16.1 --prefix <dir>` →
  Native-Binary, Node 22.12 genügt).
- **Zustand nach der Reparatur**: `lastKnownGood` zurück auf **pnpm 10.28.0** (liegt im Cache, passt zum
  `lockfileVersion: '9.0'`), Root-`package.json` unangetastet — Entscheidung beim Docs-Besitzer. Solange es
  bleibt, ist `pnpm` in `factory-ui` **nur** mit `COREPACK_ENABLE_PROJECT_SPEC=0` erreichbar (ignoriert das
  `packageManager`-Feld); dieselbe Variable braucht jeder `mvn`-Lauf, dessen exec-maven-plugin `pnpm` aufruft.

## Abweichungs-Register (UI)

Codes je Zyklus präfixiert; ein Code in einem Testkommentar verweist auf diese eine Stelle.

- `ANTD-A1` — **Nav-Rolle**: `Menu mode="horizontal"` rendert `li[role=menuitem]` (kein `a`/`button`) und
  zeigt die Auswahl nur als Klasse `.ant-menu-item-selected`.
- `ANTD-A2` — **Popconfirm** statt des eigenen Lösch-Popups in der Projektliste.
- `ANTD-A3` — **Choice: Label im Feld, Drahtwert im Body** (`content.config.yes` ≠ `'true'`).
- `ANTD-A4` — **Icons über `.anticon-*`** statt `bi bi-*` (Test-Selektoren).
- `ANTD-A5` — **Markdown-Streaming**: ein noch offenes Syntax-Zeichen (`**`) steht während des Streams als
  **Wortlaut** und ist erst mit dem letzten Token gerendert; rohes HTML wird nie Markup der Seite (es bleibt
  als Text stehen). Bewiesen in `test/chat/chat.markdown.test.tsx` (`UC-WP-54`, `UC-WP-73`) — die alte
  Zusage „der Renderer schließt offene Syntax" ist mit dem Renderer entfallen.
- `ANTD-A6` — **Scroll-Kasten um jede Tabelle einer Antwort**: `div.chat-markdown-table` ist eine zusätzliche
  DOM-Ebene im Antwortblock (R-WP-29: eine breite Tabelle scrollt **im** Antwortblock, nie die Seite breit) —
  von uns, nicht von der Basis; ein Test benutzt sie als Seam, weil jsdom keine Breite misst (bewiesen wird,
  *wer* scrollt). Belegt in `test/chat/chat.markdown.test.tsx` (`UC-WP-55`).
- `INC7B-A1` — **Dev-Label „Think (Default)"** statt `content.agents.think` (Inc 7b, fünf Abnehmer).
- **`aria-current` — Übergang bis Inc 5a**: die Navigation trägt es seit dem AntD-Umbau nicht mehr; Auswahl
  wird über `.ant-menu-item-selected` bewiesen. Inc 5a stellt es mit der URL-Auswahl wieder her und pinnt es
  mit Test (`// UC-WP-70`).
- **Eingefroren: `A1` in `test/chat/chat.page.test.tsx`** — gehört der Inc-7-Sperrregel im alten Chat-
  Namensraum und bleibt unumbenannt (Sperre `test/chat/**`); gemeint ist dieselbe Nav-Rollen-Abweichung wie
  `ANTD-A1`, gehoben mit Inc 4 (Timeline).
