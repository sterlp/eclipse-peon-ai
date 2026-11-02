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