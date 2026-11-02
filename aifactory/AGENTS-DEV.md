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

