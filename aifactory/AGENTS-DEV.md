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



## UI-Inkremente (Zyklus 2026-11-02, Inc 1a)

- **Bootstrap liegt als npm-Paket bei** (bootstrap 5.3.8, react-bootstrap 2.10.10, bootstrap-icons
  1.13.2 — laufen ohne Peer-Fehler gegen React 19). CSS ist in Vitest gestubbet (seit Inc 3a:
  `test.css.include` lässt nur die zwei eigenen Sheets durch, Bootstrap bleibt stub), also kein
  Bootstrap-Einfluss auf Tests. Im Produktions-Build landet
  Bootstrap **zuerst**, dann `index.css`-Tokens, dann `styles.css` (geprüft am dist-CSS:
  `.app-nav` bei Offset 309 k von 315 k) — deshalb gewinnt das eigene Theme ohne `!important`.
- **`Nav.Link as="button"`** rendert `<button class="nav-link">` und setzt bei `active` nur die Klasse,
  **nicht** `aria-current`; die Markierung muss die Komponente selbst setzen, sonst ist „welcher
  Eintrag zeigt" nicht testbar.
- **Doc-Lint-Baseline ist nicht 0 Findings:** Jons Docs-first Stand (uncommitted) definiert 18 UCs mit
  ❌, die erst spätere Inkremente abdecken ⇒ `lintDocsAndTests` meldet 18 UNBELEGT bei UC 63/63. Ein
  Inkrement ist findings-frei, wenn es **keine neuen** Findings erzeugt und seine eigenen UCs aus der
  Liste nimmt — nicht bei 0.
- **`[ERROR] Surefire is going to kill self fork JVM … 30 s after System.exit(0)`** im Backend-Lauf ist
  bestehendes Teardown-Rauschen: `mvn -o clean test` endet trotzdem mit `BUILD SUCCESS`, exit 0,
  `Tests run: 156, Failures: 0, Errors: 0`.

## UI-Inkremente (Zyklus 2026-11-02, Inc 1b)

- **Vitest kennt kein `.as(…)`** (gemessen 2026-11-02, Inc 1b): `expect(x).as('msg')` wirft
  `Invalid Chai property: as`. Die Assertion-Nachricht ist das **zweite Argument** — `expect(offenders, 'why')
  .toEqual([])`. `.as(…)` ist AssertJ und damit nur im Backend-Lauf gültig.
- **Quellen-Scan in UI-Tests ohne Node-Typen**: `tsconfig.app.json` setzt `types: ["vite/client", …]`, also sind
  `node:fs`, `node:path` und `process` in `src`+`test` **nicht** typisiert (`@types/node` liegt bei, gilt aber nur
  für `tsconfig.node.json` → `vite.config.ts`). Ein Scan über `src/**` läuft trotzdem durch Vitest **und** `tsc -b`,
  wenn er die Dateien als Text vom Bundler holt:
  `import.meta.glob('../../src/**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true })`.
  `import.meta.url` ist in Vitest **kein** `file:`-URL → `fileURLToPath(import.meta.url)` bringt
  `TypeError: The URL must be of scheme file`.
- **Catalog-Wert ≠ Feldwert** (UC-WP-53): ein `<select>` zeigt das Label aus `content.ts`, trägt aber den
  **Draht-Wert** als `value` (`sendThinking`: Label `Yes`/`No`, value `yes`/`no`). Ein Test, der `element.value`
  liest, darf den Catalog-Wert nicht erwarten — sonst fällt er, obwohl die Oberfläche richtig ist.


## UI-Inkremente (Zyklus 2026-11-02, Inc 1c — URL-Routing)

- **`addResolver` gehört nicht auf `ResourceHandlerRegistration`** (gemessen, Spring 7 / Boot 4):
  `resourceChain(true)` gibt eine **`ResourceChainRegistration`** zurück, und erst dort sitzt
  `addResolver(...)`. `resourceChain(true)` = `cacheResources(true)` → `CachingResourceResolver` steht
  **vor** dem eigenen Resolver; Erweiterungspunkt ist `PathResourceResolver.getResource(String, Resource)`,
  der delegiert **nie** an die Chain.
- **Der spezifischere Handler gewinnt, deshalb ist die `assets/`-Ausnahme im Fallback nicht tragend**:
  `/ui/assets/**` schlägt `/ui/**`, ein fehlendes Asset wird nie vom Fallback-Handler gesehen. Mutation
  (`isAppRoute` zu `return true`) macht **nur** den `.map`-Fall rot — die Extension-Regel ist die
  sichernde, die `assets/`-Regel ist Defense-in-depth.
- **oxlint `react(only-export-components)`**: eine Datei, die eine Komponente exportiert, darf keine
  Funktionen/Hooks exportieren (auch nicht im `test`-Baum). Ausgliederungen: Typ + Pfad-Helfer →
  `app/app.paths.ts`, Outlet-Context-Hook → `app/app.context.ts`, Test-Sonde → eigene `.tsx`.
  **Konstanten-Exporte sind erlaubt** (`BASENAME` bleibt).
- **Routing macht Navigation async**: ein Klick rendert die Zielseite erst nach der Datenlesung, drei
  Chat-Tests brauchen `findBy*` statt `getBy*` — `waitFor` mit Standard-1000 ms reicht, die Behauptung
  bleibt dieselbe.


## Inc 2 (Zyklus 2026-11-02) — Live-Status der Projektliste (Backend + UI)

- **Der Arbeits-Zustand kommt aus `ProjectRuntime`, nicht aus den Core-Flaggen**: `isWorking()` und
  `getQueuedMessageCount()` existieren nur **innerhalb** `call()` auf dem Turn-Thread — ein Delta, das beim
  Submit veröffentlicht wird, sähe eine eben eingetroffene, noch wartende Eingabe nicht (UC-WP-47 wird
  „working · 1 queued" nie). `ProjectRuntime` zählt deshalb seine angenommenen Submits (`outstanding`):
  working = „eins hat noch nicht geantwortet", queued = die dahinter. Publish immer zusammen mit der
  Zahlen-Änderung, nie woanders.
- **Ein Neustart wird in diesem Repo mit einem zweiten Kontext über demselben Home bewiesen**
  (`SpringApplicationBuilder(AifactoryApplication.class, FakeGitConfig.class)` auf `server.port=0`,
  Muster: `ChatRestartTest`) — nicht mit `@DirtiesContext`. Test-Reihenfolge dann mit
  `@TestMethodOrder(OrderAnnotation)`, weil der Neustart-Fall ein Projekt braucht, das vorher gearbeitet hat.
- **`@SpringBootTest` + zweite Kontexte**: kein Cleanup nötig — `try (ConfigurableApplicationContext …)`
  schließt den zweiten Kontext beim Verlassen des Blocks, der Port kommt aus
  `((WebServerApplicationContext) ctx).getWebServer().getPort()`. Der `@SpringBootTest`-Context des Laufs
  bleibt dabei unverändert geöffnet (zwei Server, beide auf `server.port=0`).
- **SSE für die Projektliste = `fetch` + `shared/sse.client.ts`** (bewusst nicht `EventSource`): der
  Parser liegt jetzt geteilt mit `chat.stream.ts` (CRLF, Chunk-Split, `:comment`). Ein UI-Test, dessen
  View einen Stream öffnet, muss `…/events` mit einer offenen `ReadableStream` beantworten und sie im
  `afterEach` schließen — sonst hängt der Reader der unmounted View (Muster: `project.page.test.tsx`).
- **Modulgrenze über D-6**: die Status-Typen, die `chat` aus `project` sichtbar macht
  (`ProjectWorkStates`, `ProjectWork`), liegen im **Wurzel-Paket** von `project`; `project` importiert
  nichts aus `chat`. `ComponentArchitectureTest` (Spring Modulith, `ApplicationModules.verify()`) läuft im
  Build mit und war grün.


## Inc 3a (Zyklus 2026-11-02) — Farb- und Oberflächen-Vertrag der Chat-Zeilen

- **jsdom beweist keine Farbe** (gemessen 2026-11-02): `getComputedStyle(...).borderLeftColor` ist
  `"canvastext"`, native CSS-Nesting und `var()` werden nicht ausgewertet (`textAlign` dagegen schon).
  Der Farbvertrag einer Regel wie R-WP-29 wird deshalb aus dem **Text des Stylesheets** gelesen
  (`import.meta.glob` über `../../src/*.css` mit `query: '?raw'`) und über die Tokens geschlossen:
  Klasse → Token → Wert aus `chat.html`.
- **`?raw` auf `.css` liefert einen leeren String, solange `test.css` aus ist.** `test.css.include`
  verlangt **RegExp**-Einträge (Strings lassen `vitest:css-disable` mit `re.test is not a function`
  abstürzen), und die id trägt beim Raw-Laden ihre Query — die Regex muss also `\?|$` am Ende zulassen.
- **Ein `<hr>` gehört in die Zeile, nicht zwischen die `<li>`** (HTML: ein `<ul>` darf nur `li` als
  Kinder). Die Zeile, die eine neue Stimme beginnt, trägt ihr `hr` selbst — die Liste bleibt eine Liste
  und die Trennlinie läuft trotzdem über die ganze Breite.
- **Die Draht-Handle der Chat-Zeilen nicht umbenennen**: `chat-line-jon` / `chat-live` /
  `chat-line-author` sind die Griffe von 20 bestehenden Tests. Die Stimme (user/agent) ist die **Klasse**,
  der Draht (jon/user) der `data-testid`.

## Inc 3b (Zyklus 2026-11-02) — Markdown-Streaming und Tool-Zeilen inline

- **`markstream-react` hat kein 1.x im npm-Registry** (gemessen 2026-11-02: 0.0.x → 0.1.0-beta →
  2.0.x). Der React-Einstieg von 2.0.23 ist der **Default-Export** (`MarkdownRender` existiert nicht als
  Export, auch nicht als Alias). Code-Block ersetzt man über `setCustomComponents(id, { code_block: Cmp })`
  in **Modul-Scope** + `customId={id}` an der Komponente — nicht über das `components`-Prop.
- **markstream liefert keine CSS-Klassen-Auflösung mit** (Klassen wie `font-semibold`,
  `whitespace-pre-wrap` bleiben ungestyled) und aktualisiert async (Timer/rAF) ⇒ "not wrapped in act" ist
  Bibliotheks-Rauschen, kein Fehler im Test. `vitest` stubbt Fremd-CSS ohnehin (`test.css.include` lässt
  nur die eigenen Sheets durch). Der Fallback `react-markdown` war nicht nötig.
- **Pin-Korrektur von Jon abgenommen (2026-11-02):** Plan D-8 „markstream-react 1.x pinnen" ist faktisch
  falsch — gepinnt sind **2.0.23** + **react-shiki 0.11.2**, Shiki bleibt über den Override wirksam.
- **`react-shiki`: `/web` statt Haupteinstieg** (gemessen am `pnpm build`): identischer Main-Chunk
  (Grammatiken sind Lazy-Chunks), aber 131 statt 316 Chunks und 6,7 MB statt 11,7 MB `dist`.
  `engine="javascript"` läuft in jsdom (kein WASM), unbekannte Sprache ⇒ stiller Plain-Text-Fallback.
  `shiki` ist bei pnpm strict **nicht** direkt importierbar (nur Transitiv-Dep).
- **`/web`-Preis:** Grammatiken außerhalb des Web-Sets (`go`, `rust`, `kotlin`, `diff`, `dockerfile`)
  rendern als Plain-Text; zurück auf den Voll-Bau ist **ein Importpfad** in `chat.markdown.tsx`.
- **Markdown zerteilt den Text** in Token-Spans: `getByText`/`findByText` trifft den Agenten-Satz nicht
  mehr. Abzufragen ist die **Zeile** (`chat-line-jon`) über ihre `textContent` — Helper
  `agentLinesSaying()` in `test/chat/chat.page.test.tsx`.
- **Projektwechsel remountet die Chat-Seite über `key={project.id}`** (`app.routes.tsx`, wie
  `ProjectFiles`): ein `setLive([])` im Effect-Body verstößt gegen oxlint `react(set-state-in-effect)`
  (React-Compiler-Lint) und ist mit Key überflüssig. Der Live-Anker ist eine **Zeilenzahl**, kein
  Textvergleich — deshalb überlebt er den `done`-Refresh.
- **"Could not parse CSS stylesheet" in jsdom ist vorbestehend**, wenn der vite-Cache kalt ist: es kommt
  aus dem nativen CSS-Nesting in `styles.css` (seit Inc 1a), die HEAD-Version zeigt dasselbe. Erst
  `rm -rf node_modules/.vite` auf HEAD vergleichen, bevor ein Inkrement beschuldigt wird.


## Inc 4 (Zyklus 2026-11-02) — Textarea, Warte-Leiste, Resend

- **jsdom kennt `requestSubmit`, meldet es aber als „Not implemented"**, wenn das Formular sich danach
  wirklich abgeben will: Ein Test-Double für `onSubmit` muss `event.preventDefault()` aufrufen, sonst
  steht die Meldung im Output, obwohl der Submit-Event gelaufen ist und der Test grün ist.
- **„Enter ist der Umbruch des Browsers" direkt bewiesen**: `fireEvent.keyDown(field, { key: 'Enter' })`
  gibt `false` zurück, sobald die Komponente `preventDefault()` ruft — das ist die Assertion, nicht ein
  Vergleich des Werts. Eine kontrollierte Textarea behält einen Mehrzeiler-Wert außerdem nur, wenn der
  Test ein echtes `useState` als Eigentümer hat (`<input type="text">` wirft den `\n` in jsdom weg —
  genau das ist der Unterschied, den der Fall zeigt).
- **Die Warte-Leiste hängt an `busy`** — demselben Flag wie der Stop-Knopf: ein Turn eines anderen Browsers
  liefert seine `partial`-Zeilen über denselben Stream, deshalb braucht „während ein Turn läuft" kein
  zweites Flag und kein zweites Protokoll (R-WP-31, UC-WP-22).
- **Der Steuerungs-Beweis aus Inc 5 (`oneChatPerProject`) friert die Knopf-Liste ein**: Wer dem Chat einen
  Knopf hinzufügt, muss diese Liste mitziehen (R-WP-30 nennt Resend) — die Liste ist der Ort, an dem ein
  vierter, gesprächswechselnder Knopf auffällt.

## Inc 5 (Zyklus 2026-11-02) — Kacheln als Reihe, Klick öffnet den Blick

- **`git checkout -- <datei>` ist kein Mutations-Restore**, solange die Datei uncommitted geändert ist: es
  holt die HEAD-Fassung und wirft den ganzen Inkrement-Stand weg (gemessen: die Neu-Schreib von
  `chat.tiles.tsx` war damit verloren). Mutationsproben mit `cp $file /tmp/x.orig` und zurück, nie mit git.
- **Ein `role="button"`-Container mit echten Knöpfen darin braucht im Keydown die
  `event.target === event.currentTarget`-Prüfung**: `stopPropagation` an den Actions trifft nur den
  Click-Zweig, ein Enter auf der fokussierten Action bubbert sonst an die Kachel und öffnet die Tür
  (Mutation: Guard raus ⇒ genau `clearDoesNotOpenTheInsight` rot).
- **Draht-Handle erhalten, auch wenn der Knopf fällt**: die Kachel-Tür trägt weiter
  `data-testid="tile-open-<name>"`, obwohl der eigene „Insight"-Knopf weg ist — dadurch blieben alle vier
  UC-WP-49-Tests unbearbeitet (Regel aus Inc 3a: Griffe nicht umbenennen).
- **Die eingefrorene Knopf-Liste hat sich verschoben**: `oneChatPerProject` zählt die Kachel-Actions über
  `.chat-tile-actions button` (jetzt nur Clear/Compact) und die Tür über `.chat-tile-face[role="button"]`
  mit ihrem `aria-label` — wer einer Kachel eine weitere Affordanz gibt, zieht diese zwei Listen mit.
- **Hover und Reihe sind Sheet-Beweise, keine DOM-Beweise** (Fortsetzung Inc 3a): Positiv- *und*
  Negativregel lesen — `declares('.chat-tile-open:hover')` wahr, `declares('.chat-tile-face:hover')`
  falsch, sonst ist „eine Kachel ohne Tür verspricht nichts" unbewiesen.

## Inc 6 (Zyklus 2026-11-02) — Datei-Bereich als Slide-out mit Baum und Highlighting

- **Ein rekursiver Baum merkt sich Aufgeklappt pro ZEILE, nicht pro Ebene**: `FilesLevel` darf
  `expanded.includes(branchKey(root, path))` gegen **seinen eigenen** Pfad prüfen — dann bleibt jede
  Kinderliste leer und `aria-expanded` steht für alle Zeiten auf `false` (gemessen: 8 von 12 Tests rot,
  symptomlos im DOM). Schlüssel ist immer `root|childPath` der Zeile.
- **react-shiki antwortet einen Tick später als der Klick** (gemessen): Die Box (`files-text`) existiert
  sofort, ihr `textContent` ist aber noch leer — `findByTestId(...).toHaveTextContent(...)` fällt als
  Fehlalarm. Abfragen mit `waitFor(() => expect(box.textContent).toContain(...))`.
- **Was `shiki/bundle/web` wirklich enthält** (gemessen an `shiki@4.6.0` →
  `node_modules/.pnpm/shiki@4.6.0/node_modules/shiki/dist/bundle-web.mjs`, Id-Liste per
  `grep -o '"id": "[a-z0-9-]*"'`): u. a. c/cpp/css/html/java/javascript/json/jsx/markdown/python/scss/
  shellscript/sql/tsx/typescript/xml/yaml — **kein** `toml`, **kein** `ini`, kein go/rust/kotlin/diff
  (Inc 3b). `code.language.ts` bildet nur diese Liste ab, alles andere ⇒ `text`. Ein
  `node -e "import('shiki/bundle/web')` funktioniert nicht: `shiki` ist bei pnpm strict nur Transitiv-Dep,
  und der Default-`node` auf dem PATH ist 18.
- **`.code-view` ist der gemeinsame Shiki-Hook** (Inc 6): `pre`/`.line` und der Dark-Switch hängen nicht
  mehr an `.chat-code`. Ein neuer Viewer braucht nur `CodeView`; die Box-Klasse bleibt beim Aufrufer
  (`.chat-code`, `.files-viewer-code`). Wer Shiki-Regeln an eine Box-Hängeklasse schreibt, verliert sie im
  anderen Aufrufer still.
- **Sheet-Beweise, zwei neue Formen**: (a) eine **verschachtelte** Regel ist nicht als
  `declarations('.files-level .files-level')` lesbar — sie steht als NESTING im Sheet, also Regex über den
  Sheet-Text; (b) „kein Overlay" beweist die **Abwesenheit** von `position:` in
  `declarations('.files-panel')`, während `.files-tab` `position: fixed` trägt (Mutation: `position: fixed`
  in den Panel-Block ⇒ genau `theChatStaysVisibleWhileTheViewerIsOpen` rot).
- **Der Slide-out braucht die Reihe im Router**: `ChatPage` gibt ein Fragment (`section.view`, `files`,
  `insight`); `.app-main` ist Spalten-Flex, ohne Wrapper `.chat-workspace { display: flex }` in
  `app.routes.tsx` **stapelt** das Panel unter dem Chat statt daneben — der Split ist eine
  Router-Entscheidung, keine Panel-Klasse.
- **Doc-Lint-Baseline nach Inc 6: 5 Findings** (UC-WP-44/45/46/62/63, alles config → Inc 7).
  Mutationsproben Inc 6, je genau ein Test rot: Tab-Icon fixiert → `theFilesTabOpensTheSlideOut`; Folding
  entfernt → 3 Tests; `languageOf`-Hook umgangen → `theViewerHighlightsTheFile`; Close-Reset entfernt →
  `closingThePanelForgetsThePositionInIt`; `position: fixed` am Panel → `theChatStaysVisibleWhileTheViewerIsOpen`.




## Inc 7a (Zyklus 2026-11-02) — Config: Dreizustands-Draht, Remove key, Provider-Defaults

- **`-Dtest='A+B'` läuft ohne Tests durch** (gemessen, surefire 3.6): `+` ist kein Separator, und
  zusammen mit `-Dsurefire.failIfNoSpecifiedTests=false` endet der Lauf mit `BUILD SUCCESS` und **null**
  Tests — ohne `Tests run:`-Zeile. Trenner ist das Komma, und die `Tests run:`-Zeile ist die Assertion,
  dass überhaupt etwas lief (sonst beweist ein grüner Subset-Lauf nichts).
- **`FileLlmConfigStore.put(key, blank)` löscht bereits** — die explizite `blank → remove`-Zeile in
  `FactoryConfigService.apply` ist deshalb ein **äquivalenter Mutant**: sie wegzulassen lässt alle Tests
  grün. UC-WP-46s Löschung ist über den Store bewiesen; die Zeile bleibt als Intention, nicht als Schutz.
- **Dreizustands-Draht erzwingt Text im Request**: `tokenWindow`, `timeoutSeconds`, `sendThinking` sind im
  Schreibmodell `String` (das dritte Zustandsmerkmal `""` muss darstellbar sein); Parsing und Bereichs-
  grenzen sitzen in `FactoryConfigService.validate` (`Integer.MAX_VALUE` für tokenWindow, weil der Core
  `parseInt` liest; Timeout `Long`). Nicht behaupten, Jackson konvertiert Zahlen — die Seite ist der einzige
  Client (Zahlen-Felder sind bewusst `type="text"` + `inputMode="numeric"`: ein `type="number"` meldet
  eigenen Müll als leer, und leer löscht jetzt den Wert).
- **`apiKey` und `removeApiKey` gleichzeitig abgelehnt** (400, `KEY_INTENT_HINT`): der Schreibreihenfolge
  nach würde ein einziger Body den Key setzen und sofort wieder löschen. UI-Seite: Tippen in das
  Key-Feld löscht die vorgemerkte Entfernung (beide Regeln sind gepinnt; vorher war keine davon bewiesen).
- **Provider-Defaults kommen aus dem Core, mit einem dritten Feld**: `ProviderDefaults(name, defaultUrl,
  thinkSupported)` — UC-WP-62 braucht die Think-Fähigkeit, und eine zweite Provider-Liste im UI verbietet
  D-10. `ThinkSupport` ist sealed; **nur `GOOGLE_GEMINI` und `MISTRAL`** antworten `ThinkSupport.None`
  (`Unknown` zählt als unterstützt). `defaultBaseUrl()` ist nur für OLLAMA/LM_STUDIO/GITHUB_MODELS/
  GITHUB_COPILOT nicht null — die GithubModels-URL trägt das Wort "models", also niemals
  `doesNotContain("model")` asserten.
- **Proben-Lehren**: (a) eine `perl -0pi`-Mutation erst mit `diff` gegen die Sicherung prüfen — ein
  nicht passender Anker beweist still nichts (drei Proben waren so); (b) bleibt eine Probe grün, kann es
  ein äquivalenter Mutant sein (siehe Store-`put`), nicht zwingend eine Testlücke; (c) "Reset nach dem
  Speichern" ist erst bewiesen, wenn ein **zweiter** Save folgt, und "Tippen hebt die Entfernung auf" erst
  mit einer Assertion auf den Body des darauffolgenden PUT.
- **Das Repo hat keine Prettier-Konfiguration** (`lint` = nur oxlint): `npx prettier` nach Default
  schreibt die ganze Datei um (Double Quotes, Semikolons). Entweder weglassen oder
  `--no-semi --single-quote --print-width 100` plus `git diff --stat`-Prüfung.
- **Nach Inc 7a: Backend 174** (config-Klassen 13→19 und 4→7), **UI 104 Tests / 13 Dateien**, Doc-Lint
  2 Findings (UC-WP-44/45 = Inc 7b).



## Inc 7b (Zyklus 2026-11-02) — Agent-Overrides in der Agent-Datei

- **`PromptYmlParser.parseYml` erfindet eine `name`-Zeile** (gemessen): fehlt der Name, setzt der Parser
  `name = defaultSkillName(Elternordner)` ins Frontmatter, und `SimplePromptFile.render()` schreibt das
  Live-Map zurück — wer nicht entfernt, schreibt ein Feld, das niemand gesetzt hat. Entfernen darf man sie
  nur, wenn der Wert dem Ordner gleicht (ein echter Name gehört weiter der Datei).
- **Der Kopf ist der Speicher, der Rumpf gehört dem Prompt**: `save()` schreibt `--- … ---` + Rumpf und legt
  weder Ordner an noch Rechte — `createDirectories` und POSIX `600` sind Sache des Aufrufers. Ein geschriebene
  Datei verliert den letzten Umbruch des Rumpfs (`render()` endet am Rumpf), deshalb prüft UC-WP-45 den Rumpf
  auf `contains`, nicht bytegleich.
- **Der Dev-Slot nimmt kein Modell, keine URL, keinen Key** (Q10-Familie): `LlmConfigLoader.agentRecord`
  nullt sie für `dev`, `devAgentConfig()` erzwingt das Basis-Modell. Das Overlay spiegelt das — die Datei
  trägt die Werte, `modelConfigFor("dev")` liefert sie nicht
  (`aDevOverrideReachesOnlyTheFieldsTheCoreReads`); think ist das einzige Feld, das der Dev-Slot wirklich trägt.
- **Overlay in `pin()`, nicht in `loadForTurn()`**: `load()`, `loadForTurn()` und `snapshot()` laufen alle
  über `pin()`, also wirkt die Agent-Datei an einer Stelle für alle drei; `pin()` setzt vorher `configDir`,
  das der Kern für `agents/<id>/AGENT.md` braucht (`LlmConfig.AGENT_DIRECTORY`, Dateiname „AGENT.md" ist im
  Kern ein Literal — keine öffentliche Konstante).
- **`Changes.clearing()` ist ein äquivalenter Mutant gegen „fünf leere Strings"** (Probe grün): `CONFIG_KEYS`
  sind genau die fünf Felder, beide Formen löschen dieselbe Menge. Beweisbar ist die Entscheidung im Service
  (`if (!overridden) → clearing()` statt „eingereichte Werte schreiben") — genau ein Test rot. Wie in 7a:
  erst prüfen, ob die Probe etwas ändert, dann schließen.
- **Slot-Liste und Slot-Tests trennen**: `AgentSlots.all()` ist eine Quelle (Q9), die Reihenfolge pinnt ein
  Test; die übrigen lesen über `JsonPath`-Filter `$[?(@.id == 'dev')]` und hängen nicht an der Reihenfolge.
- **Neue Route im UI = neuer Stub-Zweig, sonst crashen fremde Tests** (wie 7a): `ConfigPage` hängt
  `<Agents/>` unter dem Formular; der lokale Stub von `config.page.test.tsx` musste `/api/settings/agents`
  beantworten (sonst `slots.map is not a function`), und im Harness muss der Zweig **vor**
  `endsWith('/agents')` liegen — die Settings-Adresse endet genauso. Die angepinnte Leseliste von
  `settingsShowsMaskedConfig` wächst damit auf drei GETs.
- **Proben-Lehre, zweiter Fall**: ein Restore per `cp /tmp/x.orig Datei.java` nach einem früheren `cd` in
  einen anderen Ordner legt die Sicherung **dort** ab (gemessen: zwei Dateien im Repo-Root, die Quellen
  blieben mutiert und der Restore-`diff` verglich die Kopie). Restore nur mit absolutem Pfad, danach
  `git status` und ein voller Testlauf.
- **Nach Inc 7b: Backend 193** (174 + 19), **UI 110 Tests / 14 Dateien** (104/13), Doc-Lint **0 Findings**
  bei UC 63/63 (UC-WP-44/45 sind belegt, UC-WP-46 durch die Agent-Datei + UI).


## Inc 7b-Followup (Zyklus 2026-11-02) — Sperre, Doku-Kommentar, fehlende UI-Fälle

- **Der Overlay ist der einzige Leser der Agent-Datei** — die Factory bindet `agents/` **nie** in den Kern
  ein (`new AgentService(` gibt es nur im Plugin/Core-Tests). Kommentar in `AgentConfigFile` sagt jetzt
  genau das. Nebenbefund, festgehalten: dieselbe Dir ist die Custom-Agent-Dir des Kerns
  (`AgentService.reloadAgentConfig` → `new CustomAgent(...)`), ein Einhängen der Dir würde sechs
  Slot-„Agenten" erzeugen; heute wirkungslos, die sechs Slots sind über `AgentSlots` gepinnt (Q9).
- **Read-modify-write braucht eine Sperre, und der Beweis dafür ist die Lese-Seite**: `AgentConfigFile`
  hält ein `ReadWriteLock` pro Datei (`ConcurrentHashMap`-Key = normalisierter Pfad, also eine Sperre je
  Slot). `save()` = `Files.writeString` (trunkiert, nicht atomar) → ein Leser mitten im Schreiben sieht
  einen **leeren Kopf** und meldet „kein Override". Mutation (write-Lock raus) macht **beide** neuen Tests
  rot — auch `aSlotBeingWrittenAlwaysReadsAsAWholeHead`, obwohl der Leser sein Read-Lock behält: eine
  Lese-Sperre allein beweist nichts gegen einen nicht sperrenden Schreiber.
- **`AgentConfigFile.Changes` sind fünf gleich typisierte String-Positionen** (`model, url, apiKey, think,
  temperature`): `"high"` an der apiKey-Position kompiliert still und fällt erst in der Assertion
  („round 0 kept the think level"). Bei Datei-Tests immer gegen `CustomAgent`-Schlüssel gegenprüfen, nicht
  gegen das Gefühl.
- **Race-Test-Mechanik**: `CyclicBarrier(n)` + `Executors.newFixedThreadPool(n)` pro Runde, 24 Runden
  (eine Runde kann das Fenster by luck verfehlen), Ergebnis-Latch als `AtomicBoolean` (nur `set(true)`,
  nie `set(!…)` — sonst überschreibt die letzte Runde den Fund). `List<Future<?>> running =
  stream.map(t -> pool.submit(runnable))` kompiliert **nicht** (capture#1) → `var`. `try (ExecutorService)`
  ab Java 19 möglich, Repo fährt 21.
- **Der 400-Fall „Key + Remove key" ist im UI real erreichbar** — Reihenfolge zählt: erst ins Key-Feld
  tippen, **dann** „Remove key" ⇒ beide Intent reisen, der Server lehnt ab (der umgekehrte Weg wird durch
  die UI-Regel annulliert). Test also ohne Double: echter Klickpfad + `rejectWith`-Stub, assertions auf
  Meldung (`role="alert"`), auf den Body (`apiKey` **und** `removeApiKey`) und darauf, dass das Feld den
  Tipp behält. „Tippen hebt die Entfernung auf" ist erst bewiesen, wenn der **Body** des folgenden PUT kein
  `removeApiKey` trägt (Probe: Guard raus ⇒ genau dieser eine Test rot).
- **`trimBody` ist auf `ConfigPatch` typisiert** (gemeinsame Feldnamen beider Ebenen; `SettingsPatch` und
  `AgentPatch` extenden sie). Probe: `temperture:` in `patchOf` ⇒ `TS2561` bei `npx tsc -b` — `object` als
  Parametertyp war der Grund, warum der Tippfehler durchgelaufen wäre.
- **Nach dem Followup: Backend 195** (193 + 2), **UI 112 Tests / 14 Dateien** (110/14), Doc-Lint **0
  Findings**, UC 63/63, 218 Test-Ids. **Kein Marker geflippt** (UC-WP-44/45/46 bleiben ❌ — flippt Jon).
- **`eclipseInsertLines` kann Importe verdoppeln** (Insert nach Zeile 10 + anschließendes Replace der-
  selben Zeile ⇒ `import java.util.List;` zweimal): duplizierte Single-Type-Imports sind **legal**, javac
  meckert nicht — nach Insert+Replace-Combos die Import-Region lesen.


## Inc 1 (Zyklus 2026-11-02, Runde 2) — Fehlerzeile im Core-Memory

- **Ein Stop während eines laufenden Werkzeugs wirft nicht** (gemessen): `AbstractAgent.call` verlässt seine
  Schleife über `!monitor.isCanceled()` und **gibt die letzte Antwort zurück** — der
  `CancellationException`-Pfad (`TurnOutcome.CANCELED`, `ProjectRuntime`) entsteht nur mitten im
  Modell-Stream, und `AgentStopTest` dokumentiert schon, dass der nicht auf Abruf erzeugbar ist. Der
  entscheidende Mutant für „Stop ist kein Fehler" ist deshalb **nicht** der CANCELED-Zweig, sondern
  „Fehlerzeile am Turn-Ende" (`fail(...)` im Erfolgszweig) ⇒ genau `stopIsNoFailure` rot.
- **`eclipseInsertLines` zerlegt ein Bullet, wenn man nach seiner ersten Zeile einfügt** (gemessen): der Rest
  des Bullets wandert an das Dateiende. Ans Dateiende anhängen: `diskInsertLines` **mit**
  `afterLine=<letzte Zeile>` — ohne `afterLine` bricht der Aufruf mit
  `newContent required and can not be null` (korrigiert 2026-11-03, Runde 2 Inc 6; **nachgemessen Inc 7**:
  beide Insert-Tools brechen mit genau dieser Meldung, wenn `afterLine` die letzte Zeile einer Datei ist,
  die mit Leerzeile endet — anhängen dann über `eclipseEditFile` auf die letzte Inhaltszeile). Vorher prüfen,
  ob die Datei mit Umbruch endet.
- **Auf eine Live-Werkzeugzeile warten statt schlafen**: eigener `SseEmitter`-Double, `send(SseEventBuilder)`
  sammelt `event.build()` → `piece.getData()` (**nicht** `.data` — das Feld ist private); `contains("Running:")`
  unterscheidet ein laufendes Shell-Tool von einem Turn, der nie dort war. Ohne dieses Warten landete der Stop
  vor dem Tool und der Test wurde grün durch Nichtstun (0,5 s statt 3,7 s).
- **`ThreadSafeMemory.containsUserMessage(text)` ist der Idempotenz-Schutz** für „der Core hat die Nutzerzeile
  schon geschrieben" — `AbstractAgent.doCall` schreibt sie vor dem Modellaufruf (`:420-421`), `updateConfig`
  läuft vorher. Zwei gleiche Eingaben ergeben eine Nutzerzeile und zwei Fehlerzeilen; der Core verschmilzt
  zwei aufeinanderfolgende `UserMessage`s ohnehin (`ThreadSafeMemory.java:117-121`).
- **Reihenfolge Memory → Event**: ein Browser antwortet auf das `error`-Event mit einem History-Read. Steht die
  Zeile dann noch nicht im Memory, verschwindet sie sofort wieder — genau das war F7/F8. `ProjectRuntime.fail`
  hält beide Schritte an einer Stelle; `liveLinesAfterHistory` behält bewusst nur `tool`, damit die
  Live-Fehlerzeile beim Read aufgibt statt sich zu duplizieren.

- **Nach Inc 1: Backend 202** (195 + 7), **UI 115 Tests / 15 Dateien** (112/14), Doc-Lint **0 Findings** bei
  UC **64/64**, 227 Test-Ids. Marker UC-WP-64 bleibt ❌ (flippt Jon).


## Inc 2 (Zyklus 2026-11-02, Runde 2) — Warte-Rahmen an einem Browser-Flag

- **Der Rahmen ist nicht `busy`**: `busy` enthält auch ein WORKING-Tile eines fremden Turns (Stop-Knopf,
  Warte-Leiste) — der Warte-Rahmen einer Zeile gehört allein dem Turn, den **dieser Browser** gestartet hat
  (`runningTurn === projectId`, R-WP-31/UC-WP-65). Die Seite gibt das Flag nach unten durch; `Row.pending`
  einer History-Zeile ist immer `false`.
- **`stop()` löscht `runningTurn` selbst** (F10-Wurzel): das `done` nach einem akzeptierten Stop lässt den
  History-Read aus (D-16), ein Rahmen, der auf einen Read wartet, würde nie enden. Zeilen bleiben stehen.
- **`history().size() >= 2` ist seit R-WP-34 kein Beweis mehr für „beantwortet"**: ein fehlgeschlagener Turn
  schreibt Nutzer- **und** Fehlerzeile und erfüllt dieselbe Bedingung. `ChatUserMessageTest` wartet jetzt auf
  Jon-Zeilen (`awaitAnswers(projectId, n, …)`); Assertions unverändert.
- **Beweisgrenze dieser Härtung**: der Flake war lastabhängig und lief danach zweimal grün — reproduzierbar ist
  er nicht. Bewiesen ist nur die Tragfähigkeit der neuen Warte (Filter absichtlich auf `"error"` ⇒
  Deadline-Fehler nach 20 s). Ein grüner Lauf belegt also nicht, dass die alte Warte in Ordnung war.
- **Nach Inc 2: Backend 202** (unverändert — nur Warte-Bedingungen), **UI 118 Tests / 15 Dateien** (115/15),
  Doc-Lint **0 Findings** bei UC **65/65**, 230 Test-Ids. UC-WP-65 bleibt ❌ (flippt Jon).

- **Sicherungs-Reihenfolge ist eine Fallgrube** (gemessen, Inc 2): eine `cp`-Sicherung, die **vor** der
  Funktionsänderung entstand, macht beim Restore nach der Mutationsprobe die Funktionsänderung **mit**
  kaputt — der folgende Testlauf war grün, weil er den Rückbau gemessen hat. Sicherung erst vom finalen Stand
  (`<datei>.hard`), und nach jedem Restore `git status --short <datei>` und Diff gegen HEAD, bevor etwas als
  „grün" gilt.



## Inc 5 (Zyklus 2026-11-02, Runde 2) — Datei-Tab am Panel, Viewer-Zeilen einfach

- **Shikis Zeilen-Umbruch ist ein Textknoten, kein Box-Modell** (gemessen an `@shikijs/core@4.6.0`,
  `dist/index.mjs:898-906`): bei `structure === "classic"` liegt **zwischen** jeder
  `<span class="line">` ein Textknoten `"\n"` im `<code>`. In einem `<pre>` (`white-space: pre`) ist
  dieses `\n` der Zeilenumbruch — eine Regel `.code-view .line { display: block }` setzt die Box **nach**
  dem Umbruch, also entsteht pro Zeile eine **zweite, leere Zeile** (F6). Code-Viewer-Regeln deshalb nie
  an `.line` schreiben; die Zeilenhöhe steht auf `.code-view pre`.
- **Ein `position: fixed`-Tab mit Prozent-`top` kann nie bündig sein** (F5): er hängt am Viewport, das
  Panel an einer Flex-Zeile. Der Tab hängt an `.chat-workspace` (`position: absolute; top: 0`), dessen
  `position: relative` aus **Inc 3** kommt — Reihenfolge der Inkremente ist hier Abhängigkeit. Der
  geschlossene Tab sitzt damit am rechten Rand der **Chat-Fläche**, der offene an der Panelkante
  (`right: var(--files-width)` / `--files-wide`) und wandert mit der Breite mit.
- **`max-height: calc(100vh - …)` ist seit der festen Shell (Inc 3) falsch**: es schneidet das Panel
  unterhalb des Rahmens ab. Korrekte Form: `min-height: 0` + `overflow-y: auto` (Zeilen scrorren im Panel,
  die Höhe kommt aus der Zeile).
- **`perl -0pi` als Mutations-Probe ist eine Fallgrube** (gemessen, Inc 5): `{`/`}` in
  `s{…}{…}`-Delimits sprengen die One-Liner, die Fehlermeldung geht nach **stderr** — und ein
  nachgeschaltetes `| grep` verbirgt sie. Der Lauf war grün, weil die Mutation **nie** angewendet wurde.
  Proben jetzt mit `python3` + `assert s.count(old) == 1` (Abbruch bei verfehltem Anker) und **`diff`
  vor** dem Testlauf; erst dann zählt ein Grün.
- **Blatt-Beweis für zwei Zeilenhöhe in jsdom**: DOM-Form + Sheet-Vertrag zusammen — drei `.line`-Knoten,
  genau zwei `\n`-Textknoten dazwischen (`childNodes`-Filter), und `declares('.code-view .line') === false`.
  Layout misst jsdom nicht.
- **Proben Inc 5** (je genau die erwarteten Tests rot): `display: block` wieder rein →
  `codeLinesAreNotDoubled`; `position: fixed` am Tab → `filesTabSitsOnThePanelTop` +
  `theChatStaysVisibleWhileTheViewerIsOpen`; `top: 30%` → `filesTabSitsOnThePanelTop`; offener Tab
  `right: 0` → `filesTabSitsOnThePanelTop`; `max-height` am Panel → `filesTabSitsOnThePanelTop`;
  `position: relative` an `.chat-workspace` raus → `filesTabSitsOnThePanelTop` + `theLogIsTheScrollArea`.
- **Nach Inc 5: Backend 202** (unverändert — kein Backend-Code), **UI 123 Tests / 16 Dateien** (121/15),
  `tsc` + oxlint 0/0, Doc-Lint **66/66 UCs, 235 Test-Ids, 0 Findings**. Sheet-Helfer sind jetzt
  **eine** Datei: `test/shared/sheets.ts` exportiert zusätzlich `appSheet()` (Rohtext für verschachtelte
  Regeln); die lokale Kopie in `project.files.test.tsx` ist entfallen.


## Inc 6 (Zyklus 2026-11-02, Runde 2) — Think je Agent in der Form des Providers

- **Die Think-Form des Basis-Providers liegt jetzt auf dem Draht** (`ProviderDefaults` +
  `ProviderDefaultsDto`: `thinkForm`, `thinkValues` **neben** `thinkSupported`, additiv). Welche Form der
  Core je Provider antwortet (gemessen an `ThinkSupport`): `OPEN_AI`/`OPEN_AI_OFFICIAL`/`GITHUB_COPILOT`/
  `GITHUB_MODELS` → `Values` (ReasoningEffort, lowercase), `OLLAMA` → `Toggle`, `LM_STUDIO` →
  `FreeString`, `GOOGLE_GEMINI` + `MISTRAL` → `None`, `ANTHROPIC` → `Values(adaptive, enabled)`.
  **Kein Core-Provider antwortet `Unknown`** ⇒ `UNKNOWN` ist über HTTP nicht prüfbar — dafür steht
  `ThinkFormTest` (Mapping + „kein Provider heißt UNKNOWN").
- **Ein zweiter Provider-Read im Agent- Bereich wäre eine zweite Antwort auf eine Frage**: die Form kommt
  als Prop von `ConfigPage` (`<Agents think={thinkInputOf(chosen)} />`). Probe: Prop auf ein festes
  `UNKNOWN` gesetzt ⇒ **nur** `agentThinkFollowsTheProviderOfTheForm` rot — die Agents-Tests beweisen die
  Kopplung nicht, nur die Seite.
- **`NONE` ist ein Früh-Rückgabe-Zweig — jede Mutation dahinter ist ein äquivalenter Mutant** (gemessen):
  `form === 'VALUES' || … || form === 'NONE'` ließ alle 24 Config-Tests grün, weil der Hinweis-Zweig
  **vor** dem Select steht. Die Probe muss den Früh-Rückgabe-Test selbst treffen
  (`if (form === 'NONE' && props.value === '')`) ⇒ dann genau die zwei NONE-Verbraucher rot.
- **Das Dev-Label hat fünf Abnehmer**: `FIELDS` in `config.agents.test.tsx` liest die **Dev**-Zeile, trägt
  deshalb seit A4 `content.agents.thinkDefault` statt `content.agents.think`. Probe „Label immer `think`"
  macht fünf Tests rot (drei davon sind die positiven Begleiter) — das ist die Kostenseite eines Labels,
  das eine Semantik trägt (R-THINK-10).
- **Ein `<select>` braucht denselben Handler wie ein `<input>`**: `Field.onChange` und
  `edit(id, field)` sind auf `(value: string) => void` umgestellt (vorher
  `React.ChangeEvent<HTMLInputElement>`), sonst zwingt die Form zwei Handler-Paare.
- **`eclipseReplaceLines` ersetzt genau EINE Zeile** (zweimal gemessen, Inc 6): `newContent` über mehrere
  Zeilen lässt den alten Rest der Methode stehen — in `FactoryConfigService` und in
  `SettingsControllerTest` jeweils ein doppelter Methodenrumpf, den erst das Nachlesen zeigte. Nach
  jeder Replace die Region neu lesen, nie auf Erfolgsmeldung bauen.
- **Proben Inc 6** (je genau die erwarteten Tests rot): TOGGLE aus der Auswahl genommen →
  `thinkFieldFollowsTheProviderForm`; NONE-Früh-Rückgabe entschärft → `thinkFieldIsHiddenForNone` +
  Page-Test; gespeicherter Listen-Fremdwert weg → `storedThinkStaysVerbatim`; Dev-Label raus → 5 Tests;
  Prop fest auf UNKNOWN → nur Page-Test; `Values→FREE` im Mapping → `ThinkFormTest` +
  `FactoryConfigServiceTest` + `providerDefaultsCarryTheThinkForm`; `Toggle→UNKNOWN` → dieselben plus
  `noCoreProviderIsAnsweredWithTheUnknownForm`; „langer Think-Wert ⇒ high" im Datei-Schreiber → genau
  `thinkValueIsStoredVerbatim`.
- **Nach Inc 6: Backend 206** (202 + 4), **UI 128 Tests / 16 Dateien** (123/16), `tsc` + oxlint 0/0,
  Doc-Lint **67/67 UCs, 243 Test-Ids, 0 Findings**. UC-WP-67 bleibt ❌ (flippt Jon).


## Inc 7 (Zyklus 2026-11-02, Runde 2) — Config-Seite in der visuellen Sprache des Chats

- **`.ui-card` statt `.card` ist keine Geschmackssache**: Bootstrap 5 gibt `.card`
  `display: flex; flex-direction: column` und eine eigene Border-Farbe; gegen den Bootstrap-Layer hilft
  ohne `!important` nur ein eigener Name. Tokens der Chat-Cards (`.files-root-card`,
  `.chat-insight-line`): `1px solid var(--border)` + Radius + Padding, **kein** Hintergrund — die Fläche
  bleibt die Seite, die Kante nennt den Block.
- **`declarations()` liest eine gruppierte Regel nur am LETZTEN Selector** (der Helfer sucht
  `"<selector> {"`): aus `.settings input, … , .config-agents-key input {` ist nur
  `.config-agents-key input` lesbar. Ein Gruppenmitglied, das nicht Tail ist, über
  `appSheet().toContain('.config-agents-field input,')` pinnen — sonst beweist der Test die Teilmenge.
- **Neuer Helfer `block(selector)` in `test/shared/sheets.ts`**: `declarations()` endet vor der ersten
  verschachtelten Regel, ein Maß aus einem Nested-Block (das Padding von `.chat-composer textarea`) ist
  nur über den klammerbalancierten Block lesbar. Die Maß-Regex muss `(^|[^-\w])padding:` heißen — sonst
  trifft sie zuerst das `padding-top:` derselben Regel.
- **Layout ist in jsdom ein Sheet-Beweis**: „die Card ersetzt die Trennlinie" =
  `declarations('.config-agents-slot')` enthält kein `border-top` mehr; das Feldmaß wird gegen den Wert
  des Chat-Feldes verglichen statt gegen eine Zahl (ein fest codierter Wert überlebt jede Änderung der
  Chat-Eingabe nicht als Hinweis, sondern nur als Zufall).
- **Zwei `AGENTS-DEV.md` im Repo**: die Bau-Fakten dieses Zyklus stehen in
  `aifactory/AGENTS-DEV.md` (von Peon geladen als `/aifactory-parent/AGENTS-DEV.md`); die Wurzel-Datei
  (112 Zeilen) endet bei Inc 4 und ist nicht der aktuelle Stand.
- **Kein Doc-Delta durch Dev**: der Look-Absatz in `ui.md` gehört dem Docs-Besitzer, und keine
  bestehende Zeile lügt (`config.md` kennt keine Look-Aussage, `styles.css`-Kommentar „Deliberately no
  card" war der einzige Satz, der korrigiert werden musste). Jons Flip `UC-WP-67 ✅` in `config.md`
  (uncommitted) reist im Inc-7-Commit mit.
- **Proben Inc 7** (je genau `settingsUsesTheChatCard` rot): Form ohne `ui-card` · Slot ohne `ui-card` ·
  `.ui-card` ohne Padding · `.ui-card` ohne Rand · Feldmaß zurück auf `0.3rem 0.5rem` · Agent-Felder aus
  der geteilten Feldregel genommen · Slot-Trennlinie zurück · Key-Status ohne Mono.
- **Nach Inc 7: Backend 206/0/0** (unverändert — kein Backend-Code), **UI 129 Tests / 16 Dateien**
  (128/16), `tsc` + oxlint 0/0 (58 Dateien), Doc-Lint **67/67 UCs, 243 Test-Ids, 0 Findings**.


## Review-Followup (Zyklus 2026-11-03, Runde 2) — Fehlerpfad gepinnt, Warten zusammengelegt

- **Der Modellaufruf wirft, er liefert nicht null** (gemessen): `ApiRetry` startet mit `credit = 0` und
  `if (retryCount >= credit) throw` ⇒ der **erste** Fehler durchreicht ohne jede Backoff-Warte;
  `ToolLoopRequest.call` verpackt eine nachrichtlose Provider-Exception zu
  `RuntimeException("AI call to <url> failed: …")`. Ein `MockLlmServer`, das nach `start()` **gestoppt**
  wird, ergibt darum in < 1 s denselben RuntimeException-Zweig von `submit` wie ein defekter Provider —
  `ToolService:187` („AI call returned null") ist auf diesem Weg **nicht** erreichbar.
- **`MockLlmServer.isAlive()` ist nach `stop()` kein Beweis** (`getPort()` → NPE auf `server == null`,
  vom `catch (Exception)` in `isAlive` verschluckt ⇒ false aus dem falschen Grund). Beweis des
  „nie gefragt" ist `getRequestBodies().isEmpty()`, nicht eine Socket-Probe.
- **`teamIsWorking()` sieht Jon nicht** — die Iteration läuft über `jon.getTeam()`. Während Jons Tool
  läuft, wird `act(CLEAR)` also **allein** über `turnInFlight` abgelehnt: genau darum trägt
  `assertThatThrownBy(act)` die Regel „der Claim bleibt belegt" mit, entscheidend ist
  `assertThatCode(stop)` (Mutation `ranTurn = true` ⇒ beide rot).
- **Ein Submit, der vor dem Core scheitert, erreicht den Core nie**: ein stateful `configForTurn`-Supplier
  (erste Lese gibt die Config, zweite wirft) ist der einzige nötige Haken für einen **gequeuerten**,
  scheiternden Submit — kein zweiter Turn-Pfad, kein Core-Double, kein Core-Eingriff.
- **Historien-Warten ist jetzt eine Datei**: `ChatHistoryWaits.awaitAnswers(history, n, what)` und
  `awaitAnswer(history, answer)` über einem `Supplier<List<ChatMessageDto>>`; beides nennt **Jons Rolle**,
  nie eine Zeilenzahl (R-WP-34: ein Fehlturn schreibt zwei Zeilen und war damit als „beantwortet" lesbar).
  `SharedProjectChatTest` prüfte vorher **ohne Rollenprüfung** — benannte Verschärfung (getextet wird
  jeweils eine Modellantwort, alle Aufrufstellen bleiben grün). Die 15 Kopien von `awaitUntil` sind
  damit **nicht** consolidated — sie warten auf andere Dinge (`isWorking`, SSE, Queue).
- **Fundstelle „vier Lines-Zählungen ohne Warten in `ChatUserMessageTest`" ist an HEAD nicht messbar**:
  alle sieben Wartepunkte der Klasse warten seit Inc 2 auf Jon-Zeilen. Verbliebene
  `history().size()`-Wartungen stehen nur in `TurnFailureTest`/`ChatTurnFailureTest`, wo die **Zeilenzahl
  selbst die Regel ist** (R-WP-34) — keine Flake-Quelle.
- **`eclipseInsertLines` rechnet auf dem Stand des letzten Reads** (gemessen): nach einem Insert bei
  Zeile 140 landete ein zweiter Insert „afterLine=221" mitten in `stopIsNoFailure`. Entweder von unten
  nach oben einfügen oder nach **jedem** Insert neu lesen; vor dem Bauen immer `git diff` lesen.
- **Nach dem Review-Followup: Backend 208** (206 + 2), **UI 129 Tests / 16 Dateien** (unverändert — nur
  UC-Marker und ein Kommentar), `tsc` + oxlint 0/0, Doc-Lint **67/67 UCs, 250 Test-Ids, 0 Findings**.
  Proben, je genau die erwarteten Tests rot: `ranTurn = true` im RuntimeException-Zweig →
  `aQueuedSubmitThatFailsOwnsNothingOfTheRunningTurn`; `containsUserMessage`-Schutz aus `recordFailure` →
  `aTurnThatFailsInsideTheModelCallLeavesTheSamePair` + `thePayloadIsWrittenOnlyOnce`.


## Build-Geschwindigkeit / Test-Kontext (Zyklus 2026-11-03, F6/F7)

- **Die Regel steht als Zeile in `aifactory/AGENTS.md` und wird jetzt gemessen**: der Spring-Kontext
  startet genau einmal pro Build. Die Basisklasse `FactorySpringTest` (abstract) besitzt allein
  `@SpringBootTest(RANDOM_PORT)`, `@Import(FakeGitConfig.class)` und die **eine**
  `@DynamicPropertySource`; Unterklassen erben die statische Methode → gleiches `Method`-Objekt →
  derselbe Cache-Key. Eigene DPS / `@DirtiesContext` / `@MockitoBean` / `@TestPropertySource` /
  `@ActiveProfiles` / `@SpringBootTest(properties=…)` / Boot-Slices sind verboten —
  `SingleContextGuardTest` prüft das im Bytecode (ArchUnit ohne `@ArchTest`, gleiches Muster wie
  `ArchitectureGuardTest`), `assertOneSharedContext` in der Basis erwischt jeden zweiten Kontext zur Laufzeit.
- **Der Cache-Key ist die Kostenstelle, nicht die Boot-Zeit** (gemessen): 23 Klassen mit je eigener DPS =
  23 Klassen mit je eigener DPS = 23 Kontexte = 23 Server, **118 s → 43,1 s** Wanduhr bei gleichbleibend 216 Tests. Die 23 Boot-Zeilen
  summierten auf nur ~6 s — **hochgerechnet aus Boot-Zeilen fehlt die Hälfte** (gemessen ~21 s pro elf
  Kontexte: Context-Refresh, Web-Server, Modulith). Messen statt schätzen:
  `grep -cE "Started [A-Za-z]+ in [0-9.]+ seconds"` (die Zeile nennt die **Testklasse**, nie
  `AifactoryApplication`) und `missCount` mit
  `-Dlogging.level.org.springframework.test.context.cache=DEBUG`.
- **Ein Home für den ganzen Build (`target/test-home`), Reset je Testmethode** in der Reihenfolge
  `ProjectService.remove` → Home-Wipe → FakeGit-Reset. `remove` **vor** dem Wipe, weil
  `requireOwnCheckout` den Checkout braucht; ohne Drop bleibt der Runtime des vorigen Tests unter
  derselben Id hängen (Id = Hash der URL) — der Isolationsbeweis `FactorySpringTestResetTest` fällt dann
  laut, nicht still.
- **`@TempDir` ist nie das Fabrik-Home** (bleibt aber richtig für Ausbruch-Ziele): das geteilte Home muss
  **außerhalb** von `java.io.tmpdir` liegen, sonst kann `..` die zweite Schreibwurzel nie verlassen
  (s. oben „`/tmp` is NOT `java.io.tmpdir`"). Und ein `@BeforeEach` der Unterklasse, der einen leeren Home
  behauptet, während die Basis ihn gerade geleert hat, ist ein zweiter unsichtbarer Besitzer desselben
  Zustands → gestrichen (`AgentSettingsTest.emptyHome` → `talkToTheRunningServer`).
- **Feste Wartezeiten sind kein Beweis**: `chat/TurnGate` hält den Turn in einem **laufenden**
  Shell-Werkzeug (`while [ ! -f .turn-gate ]; do sleep 0.1; done`, selbst begrenzt ~45 s,
  Werkzeug-Timeout 60 s — beides **über** den Test-Deadlines, damit eine vergessene Freigabe rot wird und
  nicht „langsam"). Vorher standen ~40 s `sleep 3`/`sleep 6` in der Suite. Kommentare und Testtexte, die
  eine Pause behaupten, sind nach der Umstellung falsch und müssen mit (`SLEEP_TOOL` → `GATE_TOOL`,
  `call_sleep` → `call_gate`, „Schlaf drei Sekunden" → „hält das Werkzeug").
- **Zahlen, die Semantik sind, bleiben**: `DelayedModel(SUMMARY, 2_500)` in `AgentActionTest` liegt über
  dem 1500-ms-Prüfschritt des Kerns — das ist eine Aussage, keine Wartezeit.
- **Zustand vorheriger Methoden lesen ist ein latenter Fehler, keine Ausnahme**: `ProjectStatusTest`'s
  Neustart-Fall (UC-WP-47) verlangte früher „die Projekte der Tests vorher sind registriert"; heute legt er
  sein Projekt selbst an und hält es bis zum Neustart im Gate — **strenger**, und `@TestMethodOrder` ist
  entfallen.
- **Wer trotzdem eine zweite App bootet, gehört in die Ausnahmliste von `SingleContextGuardTest`**
  (`ChatRestartTest`, `DelegatedAnswerRestartTest`, `ProjectStatusTest`). Die Liste prüft in **beide**
  Richtungen: ein neuer Boot ist rot, ein überflüssiger Eintrag auch. `SpringApplicationBuilder`-Boots
  erscheinen **nie** im Context-Cache — kein Kontext der Kennzahl, aber ein zweiter Server.
- **Env-Startwerte über System-Properties, nicht über Dynamic Properties**: `Environment` liest
  `System.getProperties()` als Live-Map (`AbstractEnvironment.getSystemProperties()` liefert die echte
  Map, keinen Clone) → `System.setProperty` wirkt ohne zweiten Kontext. Pflicht danach ist `@AfterEach` +
  `System.clearProperty`: System-Properties gelten JVM-weit, ein Leak ist eine zweite Antwort auf
  `AIFACTORY_LLM_*` in einer fremden Klasse.
- **Handgeschriebener Test-Zustand muss im Test enden**: die vergiftete `projects.json` (UC-WP-29) wirft
  schon beim **Lesen** `IllegalStateException` (`StoredProject.toProject`); auf dem geteilten Home zerlegt
  das den Reset **aller** folgenden Klassen (Messprobe ohne `finally`: 10 Fehler in drei Klassen).
  Geräumt wird im Test, nicht mit Milde im Basis-Reset — sonst verschwindet jeder echte Remove-Fehler.
- **MockMvc ist ein zweiter Kontext**: Registry-Tests fahren `java.net.http.HttpClient` +
  `@LocalServerPort` (`RestTemplate` kann kein PATCH, s. oben). Die 405-Methoden-Matrix bleibt
  `ProjectFilesTest`; `jsonPath("$").isArray()` gibt es über HTTP nicht — die Array-Form am Body beweisen.


- **`@Timeout` auf der Klasse ist eine Methodengrenze, keine Containergrenze** (JUnit 6.0.3-Javadoc:
  „the same effect as applying it to all testable methods … **but not to its lifecycle methods**"). Probe:
  `@Timeout(1)` an einer Klasse, die 7,5 s brauchte ⇒ **grün**; dieselbe Grenze an einer einzelnen Methode
  (`ProjectStatusTest#afterARestartEveryProjectIsFree`) ⇒ `TimeoutException`. Also: klassenweite `@Timeout`
  wirkt **pro Testmethode**, und
  `@BeforeAll`/`@BeforeEach` sind **unbegrenzt** — der Home-Reset der Basis und der Context-Start sowieso.
  Eine Grenze dagegen ist `junit.jupiter.execution.timeout.lifecycle.method.default` — seit Inc 7 auf
  **120 s** gesetzt (eigener Bullet unten).
- **Obergrenzen-Formel statt Accumulation** (Ink 6): klassenweit = größte methodenweite Grenze + 30 s;
  methodenweit 60 s, wo die größte Await-Deadline ≤ 30 s; ohne methodenweite Grenze 60 s; Restart-Klassen
  90 s (zweite App ≈ 2,5 s); `ProjectStatusTest` 120 s (Deadline 90 s). **Deadlines selbst bleiben**
  (15–40 s Warten auf den Kern) — sie sind der Grund, warum Obergrenzen im grünen Lauf nichts kosten:
  42,8 s vor und nach der Umstellung. Eine Methode, deren Deadline gleich ihrer Obergrenze ist, ist ein
  offener Punkt, kein Erfolg: das war `ProjectStatusTest#aQueuedInputIsCounted` (90 s Deadline bei
  `@Timeout(90)`) — Inc 7 hat die Deadline auf 60 s gesenkt, die Grenze ist damit wieder ein Netz. Der Name
  `afterARestartEveryProjectIsFree` stand hier früher falsch: deren Wartepunkte sind 15/15/60 s bei Grenze 90 s.
- **Lifecycle-Methoden brauchen ein Konfig-Netz, kein `@Timeout`** (Inc 7):
  `factory-backend/src/test/resources/junit-platform.properties` (neu, erste Test-Ressource des Moduls) setzt
  `junit.jupiter.execution.timeout.lifecycle.method.default=120s` — es trifft `@BeforeAll`/`@BeforeEach`/
  `@AfterEach`/`@AfterAll` und damit den Context-Start (`SpringExtension.beforeAll`) so gut wie den
  Home-Reset der Basis. Name am Jar belegt: `org.junit.jupiter.engine.Constants` (JUnit 6.0.3). **Probe:**
  provisorische Klasse mit `@BeforeAll` + 5-s-Schlaf bei Netz **2 s** ⇒
  `Timeout holdsTheLifecycleMethod() timed out after 2 seconds`; Nebenbefund: der Schlaf wurde
  **unterbrochen** (`InterruptedException` als unterdrückte Exception) — die Grenze wirkt also auch im
  Default-`thread.mode`; ein Blocker, der Interrupts ignoriert, liefe dagegen weiter.
- **Der Guard scannt einen Pfad, keinen Namen** (Review S1): `SingleContextGuardTest` prüft alle Klassen
  mit `test-classes` im Source-URI (`Source.getUri()`), nicht die `*Test`-Endung — eine
  Helper-Oberklasse ohne Endung erbt Spring genauso, und eine eigene `@FactoryBootTest` mit
  `@SpringBootTest` ist derselbe zweite Kontext. Meta-Auflösung läuft über einen **Namens-Fixpunkt** der
  eigenen Annotationstypen (`JavaClass.isAnnotation()` + `getRawType().getSimpleName()`), denn
  ArchUnits `isMetaAnnotatedWith` müsste die Spring-Jars importieren. `JavaClass.getAnnotations()` sind
  **nur die direkt gesetzten** — das war die Lücke. Sonde: `ProbeHelper` (ohne `*Test`) mit Meta-`@SpringBootTest`
  ⇒ drei Regeln rot **und** `missCount = 2` (ohne die Sonde: grün bei zwei Kontexten).
  `imported` ist **statisch**: JUnit baut pro Testmethode eine Instanz, vier Imports des Test-Baums
  kosten mehr als alle vier Regeln (1,4 s → 0,4 s Guard).
- **Die Basis kann ein „kein Turn überlebt seine Methode" nur abprüfen, nie reparieren** (Review L1):
  `FactorySpringTest.resetSharedState` schlägt zuerst fehl, wenn `workStates.current()` noch ein
  arbeitendes Projekt nennt (Grace 10 s). Sie kann es nicht beenden — die `@AfterEach` der **Unterklasse**
  laufen **vor** der der Basis, also ist ihr Provider-Double längst gestoppt, und der Kern wartet dann
  seinen Earned-Patience-Backoff: `ApiRetry` ist **pro Turn** und hat `credit = 1` aus der Antwort vor dem
  Tool ⇒ erster Fehler wird wiederholt, **10 s** (`WAIT_STEP_MS`) lang, gemessen 10,0 s bis
  „chat turn of project … failed". Eine höhere Grace wäre ein Wettlauf gegen eine Kern-Konstante.
  Deshalb: Das Warten auf das Turn-Ende bleibt **in der Testmethode**, solange ihr Stub lebt
  (`ProjectStatusTest#afterARestartEveryProjectIsFree`), entfallen konnte es nur, wo die Methode ohnehin
  auf das Ergebnis wartet (`ChatUserMessageTest` — die Verlaufs-Zeile kommt nach der letzten Antwort).

