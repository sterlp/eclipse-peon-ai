# AntD-Umbau — episodisches Memory (verschoben aus `aifactory/AGENTS-DEV.md`, 2026-11-06)

`AGENTS*.md` trägt nur, was jeder Build braucht; hier stehen die Messungen, Proben und Abweichungen je
Inkrement. Maßgeblich ist der Code. Die Datei steht für sich: dauerhafte Regeln und das
Abweichungs-Register stehen in `aifactory/AGENTS-DEV.md` („Factory-UI: Testkette und dauerhafte
Fallstricke", „Spring-Tests: Kontext-Disziplin", „Toolchain corepack/pnpm", „Abweichungs-Register (UI)"),
und ein Block, dessen Befunde dorthin gezogen sind, sagt das an seiner Stelle.

## UI-Inkremente (Zyklus 2026-11-02, Inc 1a)

- **Doc-Lint-Baseline ist nicht 0 Findings:** Jons Docs-first Stand (uncommitted) definiert 18 UCs mit
  ❌, die erst spätere Inkremente abdecken ⇒ `lintDocsAndTests` meldet 18 UNBELEGT bei UC 63/63. Ein
  Inkrement ist findings-frei, wenn es **keine neuen** Findings erzeugt und seine eigenen UCs aus der
  Liste nimmt — nicht bei 0.
- **`[ERROR] Surefire is going to kill self fork JVM … 30 s after System.exit(0)`** im Backend-Lauf ist
  bestehendes Teardown-Rauschen: `mvn -o clean test` endet trotzdem mit `BUILD SUCCESS`, exit 0,
  `Tests run: 156, Failures: 0, Errors: 0`.

## UI-Inkremente (Zyklus 2026-11-02, Inc 1b)

> Dieser Block ist leer: die drei Befunde (Vitest kennt kein `.as(…)`, Quellen-Scan per `import.meta.glob`
> statt `node:fs`, Catalog-Wert ≠ Feldwert) sind dauerhafte Regeln und stehen verdichtet in
> `aifactory/AGENTS-DEV.md`, Abschnitt „Factory-UI: Testkette und dauerhafte Fallstricke".



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

> Dieser Block ist leer: seine vier Befunde (jsdom malt keine Farbe, `?raw` auf `.css`, `hr` in der Zeile,
> Draht-Griffe nicht umbenennen) sind dauerhafte Regeln und stehen verdichtet in `aifactory/AGENTS-DEV.md`,
> Abschnitt „Factory-UI: Testkette und dauerhafte Fallstricke".


## Inc 3b (Zyklus 2026-11-02) — Markdown-Streaming und Tool-Zeilen inline

> Der Block ist leer, weil er nichts Episodisches hinterlassen hat: `markstream-react` ist aus dem Projekt
> (Paket, Pin-Debatte und „überholt"-Vorspann sind gelöscht), die vier dauerhaften Fakten von dort stehen
> verdichtet in `aifactory/AGENTS-DEV.md`, Abschnitt „Factory-UI: Testkette und dauerhafte Fallstricke".



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
  UC-WP-49-Tests unbearbeitet (Regel aus Inc 3a, dauerhaft in `aifactory/AGENTS-DEV.md`: Griffe nicht
  umbenennen).
- **Die eingefrorene Knopf-Liste hat sich verschoben**: `oneChatPerProject` zählt die Kachel-Actions über
  `.chat-tile-actions button` (jetzt nur Clear/Compact) und die Tür über `.chat-tile-face[role="button"]`
  mit ihrem `aria-label` — wer einer Kachel eine weitere Affordanz gibt, zieht diese zwei Listen mit.
- **Hover und Reihe sind Sheet-Beweise, keine DOM-Beweise** (Fortsetzung Inc 3a, Blatt-Methode jetzt in
  `aifactory/AGENTS-DEV.md`): Positiv- *und*
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
  (Grammatik-Preis des `/web`-Baues — Regel in `aifactory/AGENTS-DEV.md`). `code.language.ts` bildet nur
  diese Liste ab, alles andere ⇒ `text`. Ein
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
  deshalb seit `INC7B-A1` `content.agents.thinkDefault` statt `content.agents.think`. Probe „Label immer `think`"
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
  (s. `aifactory/AGENTS-DEV.md`, „aifactory (Spring-Boot-Reaktor) — Build-Fakten": `/tmp` is NOT
  `java.io.tmpdir`). Und ein `@BeforeEach` der Unterklasse, der einen leeren Home
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
- **Core-Fakt Diff-Draht**: `AiMonitor.onFileUpdate` feuert bei `diskEditFile` / `diskReplaceLines` /
  `diskInsertLines` und bei `diskWriteFile` **und** `eclipseWriteFile` — Anlegen **und** Überschreiben
  (`oldContent = ""` beim Anlegen). Nur **Löschen, Umbenennen, Kopieren und Shell-Schreibvorgänge**
  liefern kein Event (nur Werkzeug-Zeile). `SseTurnMonitor` rechnet den Unified-Diff serverseitig
  (`SimpleDiff.unifiedDiff`), leerer Diff ⇒ kein Event, binärer Inhalt (NUL in den ersten 8 KB) ⇒
  ToolLine, mehr als 500 Changed-Zeilen ⇒ **partieller Diff** mit Ende-Marker
  (`… diff truncated — showing 500 of N changed lines`). Nie einen Diff-Algorithmus in die UI legen
  und nie `oldContent`/`newContent` über den Draht schicken. Vertrag:
  `org.sterl.llmpeon.core/docs/file-update-diff.md` (R-SD-1…3).

- **MockMvc ist ein zweiter Kontext**: Registry-Tests fahren `java.net.http.HttpClient` +
  `@LocalServerPort` (`RestTemplate` kann kein PATCH — `aifactory/AGENTS-DEV.md`, Build-Fakten). Die
  405-Methoden-Matrix bleibt
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


## C4 (Zyklus 2026-11-03, Runde 3) — Datei-Änderung als Live-Diff im Chat (Backend + UI)

- **Der SSE-Draht ist im reinen Unit-Test lesbar**: ein `SseEmitter`-Double überschreibt
  `send(SseEventBuilder)` und sammelt `event.build()` in ein `Set<SseEmitter.DataWithMediaType>`. Das erste
  Stück ist TEXT_PLAIN `"event:<name>\ndata:"`, danach folgt **genau ein** Stück mit
  `MediaType.APPLICATION_JSON` — der Diff liegt als String im JSON, Jackson escaped die Umbrüche, also bleibt
  es bei **einem** `data:`-Eintrag (kein Aufbrechen in mehrere Field-Values).
- **„genau ein Event, kein zweites daneben" braucht ein Marker-Event, keine Momentaufnahme**: der Versand ist
  asynchron, ein `sleep` gewinnt nur zufällig. Muster `SseTurnMonitorDiffTest`: Ereignis einspielen, dann
  `registry.publish(projectId, new ChatEvent.Done(1L))` und auf den `done` warten — die Verbindung ist FIFO,
  also ist alles davor die vollständige Liste. Ein leerer Diff, der nichts sendet, ist damit von einem
  kaputten Draht zu unterscheiden.
- **`eclipseWriteFile`/Edit mit relativem `aifactory/…`-Pfad legt unter dem Projekt an** (gemessen, C4-1):
  `aifactory/factory-backend/src/test/…` landete als `/aifactory-parent/aifactory/factory-backend/…`; Maven
  fand die Klasse nicht und brach mit `No tests matching pattern "SseTurnMonitorDiffTest"` (BUILD FAILURE,
  **trotz** kompilierter Quellen), `git status` zeigte ein `?? aifactory/aifactory/`. Neue Dateien nur mit
  Workspace-Pfaden `/factory-backend/…` bzw. `/factory-ui/…` anlegen, dann `git status` prüfen.
- **Den UI-Parser ohne Eingriff testen**: `chat.stream.ts`'s `toEvent` ist Modul-privat. Der Guard gegen ein
  `diff`-Event ohne `file`/`diff` wird über `openChatStream` + `fetch`-Double mit offener `ReadableStream`
  bewiesen (Chunk mit `event:diff\ndata:{…}`), die Behauptung lautet dann `live` bleibt leer — analog zum
  Stream-Harness von `chat.page.test.tsx`.
- **`closeStream`-Helfer muss doppelt-schließfest sein**: schließt ein Test den Stream selbst und der
  `afterEach` erneut, wirft `controller.close()` `TypeError` — und zwar **aus `act()`**, also als kryptischer
  Folgefehler eines grünen Tests. try/catch um den Close, nie um die Assertion.
- **`chat-diff` gehört an das `<li>`, nicht an die innere Fläche** (Plan-Skizze ist hier widerlegt): die
  flachen Selektoren `.chat-diff .chat-diff-file` und `.chat-diff .chat-diff-body` verlangen `chat-diff` als
  **Vater** von Datei-Label und `<pre>`. Die Fläche bleibt das geteilte `.chat-code`; Diffs bekommen **keinen**
  `.code-view`-Vertrag (kein Shiki im Diff, Regel aus Inc 6).
- **Farbe bleibt ein Sheet-Beweis** (Inc 3a gilt weiter, Regel in `aifactory/AGENTS-DEV.md`):
  `declarations()`-Regeln, jede Diff-Regel flach und
  ungruppiert. Zusätzlich: `getAllByTestId(/chat-(tool-line|diff)/)` trifft **auch** `chat-diff-line` — Anker
  `^chat-(tool-line|diff)$` setzen, sonst zählt die Box-Abgrenzung Zeilen statt Blöcke.
- **Ein Diff-Test braucht eine `---`-Zeile im Rumpf**: der Header-Zweig von `lineClassOf` ist
  `index <= 1 && (startsWith('--- ') || startsWith('+++ '))`. Eine Fixtur ohne `---`-Zeile außerhalb der Köpfe
  macht die Index-Bedingung zum **äquivalenten Mutanten** (Probelauf war grün). Fixtur `db/seed.sql` mit
  entfernter Kommentarzeile `--- eine alte Konstante` ⇒ die Zeile muss `diff-context` bleiben.
- **Proben C4** (je genau die erwarteten Tests rot, Restore per `cp` + `diff`): `index <= 1` raus →
  `aChangeIsABlockWithItsFileAndItsColouredLines`; `liveLinesAfterHistory` zurück auf nur `tool` →
  `aFileChangeIsLiveAndDoesNotOutliveTheReload`; `diff`-Zweig aus `entriesOf` → beide
  Diff-Komponententests + Page-Test; D6-Guard auf `false` → `aDiffEventWithoutItsFieldsIsDropped`;
  `display: block` aus der Zeilenregel → Block-Test; `livePreview`-Diff-Zweig raus →
  `theWaitingPreviewNamesTheFile`. Backend-Seite (C4-1): Guard-leerer-Diff raus →
  `anUnchangedFileShowsNothing`; Guard-Zusammenfassung als Diff → `aFileTooLargeForADiffIsAToolLine`;
  Override stillgelegt → alle vier (Default „Updated: …").
- **Nach C4: Backend 220/0/0** (216 + 4, C4-1, ein Kontext-Start), **UI 134 Tests / 17 Dateien** (129/16),
  `tsc` + oxlint 0/0 (60 Dateien), Doc-Lint **68/68 UCs, 258 Test-Ids, 0 Findings** (UC-WP-68 belegt).
  `R-WP-36`/`UC-WP-68` bleiben ❌ im Commit — flippt Jon.



## C-5 Inc 1 (Zyklus 2026-11-05) — Live-Anker je Turn eingefroren, Stream nach dem ersten Read

- **Der Live-Anker ist eine Turn-Größe, keine Event-Größe** (`chat.page.tsx`): `turnAnchor`-Ref friert
  `historyRows + pendingLines` beim ersten Live-Item ein und wird bei `done`, `error` und `setLive([])`
  zurückgesetzt. Die alte Zeilenzahl `messages.length + outgoing.length` pro Event zerlegt eine Antwort in
  mehrere `chat-live`-Blöcke, sobald der Nutzer **während** des Turns eine zweite Eingabe sendet (Test
  `aLiveAnswerKeepsItsAnchorWhenAnInputIsQueuedMidTurn` — auf HEAD rot wegen genau dieser Fragmentierung).
  Refs (`historyRows`, `pendingLines`) schreiben **nur in Handlern/Read-Callbacks** über `writeHistory`/
  `writePending`; ein Effekt-Ref hinkt einen Render nach, und ein Ref-Write im State-Updater ist bei
  doppeltem Aufruf (StrictMode/Compiler) eine Seite, die der Render nicht sieht.
- **Plan-Skizze `entriesOf(rows, live, confirmed)` ist widerlegt**: die Klammer
  `afterLines >= confirmed ? rows.length : afterLines` setzt die laufende Antwort **hinter** die eben
  getippte zweite Frage und widerspricht §7 Test 1 wie der eigenen Begründung in D-A. Richtig bleibt
  `Math.min(afterLines, rows.length)`; ein `confirmed`-Parameter hat damit keine unterscheidbare Wirkung und
  ist nicht gebaut. Probe (Platzierung ignoriert den Anker): `aLiveTraceStaysBetweenItsInputAndTheAnswer`,
  `toolLinesSurviveTheDoneRefresh`, `aLiveAnswerKeepsItsAnchorWhenAnInputIsQueuedMidTurn` rot.
- **`stubChat.deferNextHistoryRead()`** hält den nächsten `GET …/messages` offen (Promise, das der Test
  auflöst — kein `sleep`); der Stub liest `state.messages` erst beim Auflösen. So ist D-E beweisbar
  (`theStreamOpensAfterTheFirstHistoryRead`: `/events` = 0, während der Read offen ist; Mutation
  `connect()` sofort ⇒ genau der Test rot). **Reihenfolge im Mount-Effekt ist die Assertion**: `refresh()`
  vor `connect()`, sonst ist die Anker-Aussage des ersten Events eine Lüge.
- **„An update to ChatPage was not wrapped in act(...)" in `chat.page.test.tsx` ist vorbestehend**
  (gemessen 2026-11-05 gegen HEAD derselbe Stand, 3 Meldungen pro Lauf) — kein Anzeichen eines neuen Fehlers.

## C-5 Inc 2 (Zyklus 2026-11-05) — Readversion und liveMark: die laufende Antwort überlebt den Read

- **Ein Verlaufs-Read gibt nur auf, was er kennen kann** (`chat.page.tsx`, `chat.live.ts`): `refresh()`
  merkt sich **vor** der Anfrage `mark = liveItems.current.length`, und
  `liveLinesAfterHistory(items, mark)` behält `text`/`error` ab Index `mark`. Der Default
  (`mark = items.length`) erhält die alte Semantik für jeden Aufrufer ohne diese Kenntnis. Proben: `mark`
  nicht übergeben ⇒ genau `aLaterTurnKeepsStreamingWhileAnEarlierReadInFlight` rot; `mark = 0` (der Read gibt
  nichts auf) ⇒ sechs Tests rot (`streamingUpdatesChatWithoutReload`, `failedTurnIsALineInTheHistory`, …) —
  der Parameter ist kein toter.
- **Reads rasen, deshalb Nummer + zuletzt angewandte Nummer**: `readSeq` bei Ausgabe, `appliedRead` bei
  Anwendung, `read < appliedRead` ⇒ Antwort verwerfen. Verglichen wird mit der **angewandten** und nicht der
  höchsten vergebenen Nummer — sonst entwertet ein später ausgegebenes, noch unbeantwortetes Lesen das
  Ergebnis eines früheren. Probe: Guard raus ⇒ genau `anOlderHistoryReadNeverOverwritesANewerOne` rot.
  Alle Live-Schreibvorgänge laufen über `writeLive` (Ref + State zusammen), sonst stimmt der `mark`-Snapshot
  nicht mit dem, was auf dem Schirm ist (D-B).
- **R-WP-11 entscheidet, welcher Griff nach dem `done` die Antwort trägt**: sie steht dann als
  `chat-line-jon` aus dem Read und **nicht** mehr als `chat-live` (`toolLinesSurviveTheDoneRefresh` pinnt
  „nicht zweimal"). „Die Antwort verschwindet nicht" wird deshalb über die **Log-Form** und
  `agentLinesSaying(…)` behauptet, nie über einen `chat-live`-Griff; der Griff gehört dem Text, den der Read
  nicht kennen kann (`aLaterTurnKeepsStreamingWhileAnEarlierReadInFlight`: `user,user,jon,live,user`).
- **`stubChat` hält mehrere Reads gleichzeitig offen** — `deferNextHistoryRead(answersWith?)`: die Wartenden
  laufen der Reihe nach ab, und `answersWith` beantwortet genau diesen Read mit dem **Stand von der Ausgabe
  der Anfrage**, nicht mit dem Store beim Auflösen. Erst das macht „der ältere Read antwortet zuletzt mit dem
  älteren Gespräch" baubar — ohne sleep, ohne Fake-Timer.
- **Kein `// UC-WP-69` in Inc 2**: Doc-Lint bleibt bei **1 Finding (UC-WP-69 UNBELEGT)** — die Bestätigung
  über Position ist Inc 3. Ein Marker hier meldete Belegung, obwohl der `[Queued Message]`-Fall weiterhin
  doppelt steht. **Nach Inc 2: UI 139 Tests / 17 Dateien** (137/17), `tsc` + oxlint 0/0, Doc-Lint 69/69 UCs,
  265 Test-Ids, 1 Finding.

## C-5 Inc 3 (Zyklus 2026-11-05) — Bestätigung über Position: `chat.pending.ts`

- **Die vorläufige eigene Zeile wird über Position bestätigt, nie über Text** (`chat.pending.ts`,
  R-WP-37/UC-WP-69): `PendingLine{text, afterHistory}` — `afterHistory` = Verlaufszeilen beim Senden;
  bestätigt wird sie von der ersten noch ungebrauchten `role === 'user'`-Zeile ab
  `max(used, afterHistory)`. Zwei Zeiger sind tragend: `used` (ein Verlauf-Zeile bestätigt **genau eine**
  vorläufige — ohne ihn greift Zeile 2 twice, und `aLaterTurnKeepsStreamingWhileAnEarlierReadInFlight`
  fällt, weil die zweite Pending-Zeile zu früh verschwindet) und das **Anheben der Wasserlinie** der
  behaltenen Zeile auf `from` (Lese-Wiederholung bestätigt dann nicht dieselbe Zeile zweimal).
- **Mutationslehre: ein Filter-Mutant ist nicht die alte Semantik.** Die Textvergleich-Sonde als
  `filter(line => !history.some(textgleich))` gebaut, lässt `theSameTextTwiceIsConfirmedTwice` grün —
  ohne splice-artiges **Verbrauchen** der Bestigungszeile löscht ein einziger Texttreffer beide
  vorläufiglichen Zeilen. Sonden für „genau einmal"-Semantik müssen die Verbrauchsschleife reproduzieren
  ( Probe mit `withoutSent` + `splice`: genau die zwei Inc-3-Tests rot).
- **Nach Inc 3: UI 145 Tests / 18 Dateien** (139/17), `tsc` + oxlint 0/0 (62 Dateien), Doc-Lint **0
  Findings** bei UC 69/69, 270 Test-Ids (`// UC-WP-69` in `chat.page.test.tsx` + `chat.pending.test.ts`,
  `// UC-WP-57` am Zweimal-Fall). Marker `R-WP-37`/`UC-WP-69` bleiben ❌ im Commit — flippt Jon.

## C-5 Inc 4 (Zyklus 2026-11-05) — Bestätigung je Absender: Wasserlinie statt Verbrauchsschleife

- **`withoutConfirmed` ist jetzt zustandslos** (`chat.pending.ts`, R-WP-37 geschärft): Map
  Absender → Index der **letzten** Nutzerzeile im Read, bestätigt bei `last >= afterHistory`. Der
  `used`-Zeiger aus Inc 3 ist entfallen — er konnte den Bündel-Fall nicht (Core joinnt zwei gequeueete
  Eingaben zu **einer** Memory-Zeile, `queued-user-messages.md` §1). `origin` wird beim Senden über
  `senderName(user)` gestempelt (`chat.client.ts`, D-H): nur derselbe getrimmte Wert, den der Server als
  `origin` zurückgibt, bestätigt je; `null` ist eine Absenderklasse (UC-WP-48). `ChatLog` zeichnet
  `line.origin ?? content.chat.you`, die Prop `user` ist entfallen (D-I).
- **Plan-Widerspruch, über die Docs entschieden:** §10.6 listete `aLaterTurnKeepsStreamingWhileAnEarlier
  ReadInFlight` als „bleibt grün, darf nicht umformuliert werden" — die geschärfte R-WP-37 bestätigt aber
  gerade beide namenlosen vorläufigen Zeilen durch die eine Read-Zeile (der Fall ist §10.8.3: Send
  während eines Reads in flight, Zeile wurde vor dem Send geschrieben). Der Test ist **minimal**
  umformuliert: Shape-Assertion auf `[…, chat-line-jon, chat-live]`, Kern-Assertions (streamender Text
  überlebt den Read, genau ein `chat-live`, Antwort genau einmal) wörtlich erhalten, plus positive
  Ersetzung (Folge-Read bringt die Markerzeile → genau einmal, kein `chat-live`). Regel aus memory #4:
  betroffenen Kanal enger behaupten + positiven Ersatz im selben Test + benannte Abweichung.
- **Mutationsproben §10.7 sind Mindestmengen, keine exakten Mengen:** Absendervergleich raus macht
  zusätzlich `failedTurnLeavesNoPendingFrame` rot (namenlose vorläufige Zeile darf nicht durch eine
  `origin: 'Paul'`-Zeile bestätigt werden — der Test pinnt die Regel mit, ohne sie zu nennen);
  Rückbau auf 1:1 und die `<=`-Grenze rotieren ebenfalls über die erwartete Menge hinaus. Ein roter Test
  außerhalb der erwarteten Liste ist erst dann ein Fehlalarm, wenn seine Kette nicht aus der Mutanten-
  Semantik erklärbar ist.
- **Bündel-Fixtur zitiert die erzeugende Core-Regel** (Jons Leitsatz, dasselbe Inkrement; Regel jetzt in
  `aifactory/AGENTS-DEV.md`):
  `aBundledQueuedLineReplacesBothPendingLines` nennt `queued-user-messages.md §1` im Kommentar.
- **Nach Inc 4: UI 149 Tests / 18 Dateien** (145/18), `tsc` + oxlint 0/0 (62 Dateien), Doc-Lint **0
  Findings** bei UC 69/69, 274 Test-Ids. Marker `R-WP-37`/`UC-WP-69` bleiben ❌ — flippt Jon;
  `docs/chat.md`/`docs/user.md` (Jons Schärfungen) bleiben uncommitted bei ihm.

## AntD-Umbau Inc 1 (Zyklus 2026-11-06) — die Basis unter jsdom

- **Versionen gemessen, nicht geraten**: `antd` latest **6.7.0** (keine 7.x vorhanden, latest-5 = 5.29.3),
  `@ant-design/icons` **6.3.4** (`next: 4.0.0-rc.0` ist ein alter Pre-Release, keine neue Major). React
  19.3.1 läuft an v6 nativ — `@ant-design/v5-patch-for-react-19` bleibt unnötig.
- **jsdom 26.1.0 hat `matchMedia` nicht — die Property existiert, ihr Wert ist `undefined`** (gemessen
  2026-11-06, Inc 1-N1: `typeof window.matchMedia === 'undefined'`, obwohl der Name in
  `getOwnPropertyNames(window)` steht). Der Polyfill in `test/shared/setup.ts` ist deshalb **load-bearing**:
  ohne ihn 2 Tests rot mit `vi.spyOn() can only spy on a function`, und `shared/prefers.dark.ts` braucht
  `addEventListener` am zurückgegebenen Objekt. (Die frühere Aussage „nicht load-bearing" war auf dem Stand
  vor `app.root.test.tsx` gemessen und ist widerlegt.) Load-bearing ist auch **`ResizeObserver`**: ohne ihn
  15/16 Tests in `test/app/*` rot mit
  `ReferenceError: ResizeObserver is not defined` (`@rc-component/resize-observer`). Suite: 154 Tests /
  19 Dateien in ~8,5 s Wanduhr (vorher 149/18 in 4,5 s) — AntD kostet ~4 s, Grenze ~30 s hält.
- **Ein `Select` wird über `<id>_list` adressiert, nicht über `aria-controls`** (gemessen): die Liste hängt
  ab der ersten Öffnung im DOM (`getElementById('<id>_list').closest('.ant-select-dropdown')`), während
  `aria-expanded`/`aria-controls` mit dem Popup kommen und gehen. jsdom schließt **nie** (weder Escape noch
  Klick außerhalb), und ein `mouseDown` auf ein **offenes** Choice schließt es — Öffnen also nur, wenn die
  Liste fehlt. Helper: `test/shared/choices.ts`. Anzeige = `.ant-select-content[title]` und das ist das
  **Label**, nicht der Drahtwert (Inc 1b „Catalog-Wert ≠ Feldwert" gilt weiter — Regel in
  `aifactory/AGENTS-DEV.md`); der Drahtwert wird im
  PUT-Body von `theChoiceSendsItsWireValue` bewiesen (`test/config/config.page.test.tsx`, UC-WP-24).
- **`id` an einem Choice ist Test-Ökologie und Barrierefreiheit**: ohne `id` nutzt rc-select `useId()`, und
  deren Wert ist im Testbaum für mehrere Felder **gleich** (`test-id_list`) ⇒ `getElementById` trifft die
  Liste eines fremden Feldes. Deshalb tragen die Choices der Config-Seite `settings-provider` /
  `settings-send-thinking`.
- **`Menu mode="horizontal"`** rendert `li[role=menuitem]`, Auswahl nur als Klasse
  `.ant-menu-item-selected` — **kein** `aria-current`/`aria-selected` (`ANTD-A1`, s.
  `aifactory/AGENTS-DEV.md`, „Abweichungs-Register (UI)"). `Alert`-Prop heißt `title`
  (`message` deprecated), `role="alert"` sitzt am Div, `textContent` = title allein.
- **`Layout.Header` Maß und Kopffarbe kommen aus `var(--ant-layout-header-*)`** — gleiche Spezifität wie
  unsere `.app-header`, also über `theme.components.Layout` (bodyBg/headerBg/headerColor/headerPadding)
  lösen, nicht über CSS. `Card` füllt seinen Inhalt in `.ant-card-body`: eine eigene Flex-Regel gehört auf
  einen inneren Wrapper, nicht auf die Card-Klasse; „kein Hintergrund" (docs/ui.md) ist
  `Card.colorBgContainer: 'transparent'` als Token.
- **Positiver Ersatz für die `--bs-*`-/`.ui-card`-Belege** (der Blatt-Leser kann AntD nicht messen):
  `test/shared/theme.test.ts` (`// UC-WP-71`) prüft Token-Gleichheit gegen `index.css`
  (`tokenValues('--accent')` …), `darkAlgorithm`, `Card.colorBgContainer`, `Layout.*`, und ein Guard scannt
  die Quellen + package.json-Rohtext gegen react-bootstrap/`bi bi-`/`--bs-`. Seit Inc 1-N1 liegt der Guard in
  `test/shared/design.basis.ts`: Glob `'../../src/**/*.{ts,tsx,css}'` (`?raw` liefert **auch** für `.css`
  Text, weil `test.css.include` die eigenen Sheets durchlässt — die Pfad-Anzahl allein beweist nichts, nötig
  ist je `.css` ein `not.toHaveLength(0)`), Muster erfassen zusätzlich den CSS-Import ohne `from` und die
  `@import`-Form, und die Manifest-Prüfung liest `dependencies` **∪** `devDependencies` (Sonden: CSS-Import
  in `main.tsx`, `@import` in `styles.css`, `bootstrap` in devDeps ⇒ jeweils der Guard mit Pfad-Angabe).
- **oxlint `only-export-components` erwischt auch `main.tsx`**: eine Komponente, die nur das Theme um die
  App legt, gehört in eine eigene Datei (`app/app.root.tsx`), sonst bleibt eine Warnung im `lint`-Lauf.

## AntD-Umbau Inc 1-N1 (Zyklus 2026-11-06) — Beweise + eine Dunkel-Tabelle

- **`darkAlgorithm` leitet den Akzent ab** (gemessen): aus `colorPrimary: '#58a6ff'` wird im Token
  `#4e90dc`, im Light-Modus geht das Literal durch. „Token == Tabellen-Literal" ist im Dunkel **nicht**
  prüfbar — der Dunkel-Fall vergleicht gegen ein eigenes `factoryTheme(true)` und fordert zusätzlich
  `hell !== dunkel`; die Tabellen-Gleichheit bleibt in `theme.test.ts` (Kommentar in `app.root.test.tsx`,
  damit niemand die Behauptung „repariert").
- **Dunkel ist eine Tabelle, wirksam über `data-theme`**: `AppRoot` setzt
  `document.documentElement.dataset.theme` aus `usePrefersDark()`; `theme.ts` liest unverändert die
  Attribut-Tabelle, der Media-Block bleibt der No-JS-Fallback. Neu `tokenTable(selector)` in
  `test/shared/sheets.ts`: akzeptiert wird ein Selektor nur, wenn ihm **nur Whitespace + `{`** folgt —
  `indexOf(selector)` + „nächstes `{`" las die **helle** Tabelle, weil der neue `index.css`-Kopf den
  Selektorwortlaut in Prosa nennt; die helle Tabelle muss als gruppierte Regel angegeben werden (`:root,`
  allein ist nicht `{`-ständig). ⇒ Prosa in `index.css` darf nie den Selektorwortlaut enthalten, den ein
  Test per `indexOf` sucht (gilt ebenso für `blockOf`, Review LOW-10).
- **Fonts ohne Dunkel-Zeile**: `--sans`/`--mono` fehlen in der Attribut-Tabelle; `theme.ts` fällt auf die
  helle Tabelle zurück, und der Test fordert je gemapptem Token „Dunkelwert, sonst Hellwert" — sonst steht
  `fontFamily: undefined` still im Theme.
- **Proben Inc 1-N1** (je genau die genannten Tests rot, Restore per `cp` + `diff`): Optionswerte der Choice
  getauscht → `theChoiceSendsItsWireValue` **und** zwei Label-Leser; nur die Drahtseite in `patchOf` getauscht
  → genau die zwei Drahtwert-Assertions; CSS-Import in `main.tsx` / `@import` in `styles.css` / `bootstrap`
  in `devDependencies` → jeweils der Guard (nennt die Datei); `theme`-Prop aus `ConfigProvider` →
  `appRootWearsOurTokenColours`; `useEffect` raus → `appRootSetsTheDocumentTheme`; `--border` nur im
  Media-Block → `theDarkSheetIsOneTable` (Diff nennt das Feld); Font-Fallback raus →
  `darkModeCarriesTheDarkSheetAndTheDarkAlgorithm`. Lehre: „Optionswerte tauschen" ist **keine**
  Drahtwert-Mutation — sie zerstört auch das Label-Mapping.
- **Nach Inc 1-N1: UI 159 Tests / 20 Dateien** in ~8,6 s, `tsc -b` 0, oxlint 0/0 (69 Dateien). Kein Backend,
  keine neue Dependency, `src/chat/**` unverändert.

## AntD-Umbau Inc 2 (Zyklus 2026-11-06) — Markdown der Antworten: react-markdown + GFM + breaks

- **`markstream-react` raus, `react-markdown` 10.1.0 + `remark-gfm` 4.0.1 + `remark-breaks` 4.0.0 rein**
  (Lockfile gemessen). Das ist der in R-WP-28 vorgesehene Fallback: `breaks` fehlt bei markstream, sein
  eigenes CSS war nie eingebunden. `remark-breaks` ist Pflicht — react-markdown v10 hat **keine**
  `breaks`-Option.
- **`react-markdown` parst synchron im Render**: der `settle()`-Helper (`setTimeout 0`) der Markdown-Tests
  ist entfallen, und mit ihm das `final`-Prop von `ChatMarkdown` — der letzte Token parst den ganzen Text,
  ein Fertig-Flag hätte keine unterscheidbare Wirkung (Abweichung von der Plan-Skizze; einzige Aufruferin
  `chat.log.tsx`, dort ohne `final={false}`).
- **`remarkPlugins` und `components` als Modul-Konstante — aber nicht aus dem erwarteten Grund**
  (nachgemessen 10.1.0): `react-markdown` baut seinen Processor **bei jedem Render** neu
  (`lib/index.js:175-178` → `createProcessor` `:262-275`, kein Cache); Identitäts-Memoisierung gibt es nur
  in `MarkdownHooks` (`:214-251`), das die App nicht nutzt. Ein frisches Array pro Render kostet nichts —
  die Konstanten bleiben als stabile Props, nicht als Optimierung.
- **`pre` ist der einzige Haken der Code-Ansicht**: der Override reicht sein `code`-Kind an `CodeView`
  weiter (Sprache aus `language-*`, sonst `text`, die Umbruchzeile des Zaunendes abgeschnitten).
  Kein `code`-Override: der trennte Inline und Zaun nicht — deshalb pinnt der Test `chat-code` genau einmal
  und `closest('.code-view') === null` für Inline-Code.
- **Rohes HTML wird Wortlaut, nicht weggelassen** (gemessen mit `renderToStaticMarkup`):
  `und <b>fett</b> danach.` → `und &lt;b&gt;fett&lt;/b&gt; danach.`; ein `<img …>`-Block steht als Text da,
  der Folgeabsatz bleibt; `javascript:` in einem Markdown-Link-Ziel wird zu `href=""` (die eigene
  `urlTransform` von react-markdown). ⇒ kein `rehype-raw`, kein `skipHtml` nötig (UC-WP-54).
- **GFM-Form gemessen**: Tabelle → `table`/`th`/`tbody td`, `~~x~~` → `del`, Aufgabenliste →
  `input[type=checkbox][disabled]`, ein einfacher Umbruch → `br`, eine echte Leerzeile bleibt Absatzgrenze.
- **`white-space: normal` auf `.chat-log .chat-markdown` ist tragend**: `.chat-log .chat-line` setzt
  `pre-wrap`, ohne die Aufzeichnung zeichnet der erhaltene `\n` neben dem `br` eine zweite leere Zeile —
  dieselbe Fallgrube wie Shikis `.line`-Umbruch (Inc 5, Runde 2). Pin im Umbruch-Test.
- **Proben Inc 2** (je genau die genannten Tests rot, Restore per `cp` + `diff`): `remark-breaks` raus →
  `aSingleLineBreakStaysALineOnTheScreen`; `remark-gfm` raus → `aTableAndStrikeThroughAreDrawnAndNotTyped`;
  `pre`-Override leer → `codeBlocksAreHighlighted` + `streamingMarkdownStaysReadable`; `white-space: normal`
  aus `styles.css` → `aSingleLineBreakStaysALineOnTheScreen`.
- **Nach Inc 2: UI 163 Tests / 20 Dateien** in ~9,0 s, `tsc -b` 0, oxlint 0/0 (69 Dateien). Kein Backend,
  kein Draht, kein `pnpm build` (Pauls Server läuft). Bei Jon: `UC-WP-73` ❌→✅ und C-2b in
  `docs/open-points.md` (Reload ohne Markdown) — damit erledigt.

## AntD-Umbau Inc 2-N1 (Zyklus 2026-11-06) — Antworten zeichnen: Linie, Scroll-Kasten, Wortlaut

- **Eine Modell-Linie ist Inhalt, kein Trenner** (UC-WP-55 GIVEN 3): `---` **in** einer Antwort wird zum
  `hr` **in** `.chat-markdown` und ist ausschließlich CSS — `.chat-log .chat-markdown hr` (kurz,
  linksbündig, `var(--border)`). Kein JSX-Eingriff: react-markdown liefert das `hr` selbst. Der
  Rollenwechsel-Trenner bleibt bis Inc 4 `hr.chat-rule` (`var(--tool-line)`, volle Breite) — beide sind an
  verschiedenen Selektoren unterscheidbar.
- **Trenner-Definition an einer Stelle**: `test/shared/log.parts.ts` → `separatorsOf(log)` = jedes `hr`
  **außerhalb** von `.chat-markdown`. Der Beweis läuft über den **ganzen** `.chat-log`; Inc 4
  (UC-WP-55 GIVEN 2) fordert dieselbe Liste als `[]` — es gibt dann keinen zweiten, schwächeren Selektor,
  der die Zusicherung unterläuft. Ein modell-gemaltes `hr` darf weder als unser Trenner durchgehen noch
  unseren Trenner verstecken.
- **Breite Tabelle scrollt im eigenen Kasten** (UC-WP-55 GIVEN 4): `components.table` →
  `div.chat-markdown-table` (`overflow-x: auto`), das Bild endet mit der Antwort (`max-width: 100%`,
  `height: auto`), `.chat-log` bleibt bei `overflow-y` **ohne** `overflow-x`, `#root` bei `overflow:
  hidden`. Verworfen: `display: block` + `overflow-x` auf `table` — braucht `white-space: nowrap` auf den
  Zellen (sonst schrumpft die Tabelle), zeichnet jede Antwort-Tabelle um und hinterlässt kein
  jsdom-prüfbares Element. Die neue DOM-Ebene ist `ANTD-A6` im Register.
- **Nur Antworten sind Markdown, dreiteilig bewiesen** (R-WP-28): Nutzer-, History-Fehler-, Stream-Fehler-
  und Werkzeug-Zeile mit `**`/`~~` bleiben Wortlaut (`strong, del, br, table` sind `null`), die Antwort
  daneben zeichnet (`chat-markdown`) — der Test pinnt also auch die positive Gegenseite.
- **BUG korrigiert, mutationslos**: die Modul-Konstanten von `remarkPlugins`/`components` waren mit einer
  falschen Begründung versehen — `react-markdown` baut seinen Processor **pro Render**
  (`react-markdown/lib/index.js:175-178` → `createProcessor` `:262-275`, kein Cache); Identitäts-
  Memoisierung gibt es nur in `MarkdownHooks` (`:214-251`), das die App nicht nutzt. Beleg ist die Quelle,
  kein Test: eine Textkorrektur ist nicht mutierbar, das ist im Commit benannt.
- **Proben Inc 2-N1** (je genau der erwartete Test rot, Restore per `cp` + `diff`): `hr`-Block aus
  `styles.css` → `anAnswersOwnRuleStaysInsideTheAnswer`; `hr`-Override mit `className="chat-rule"` →
  `theLogNeverCountsAnAnswersRuleAsItsSeparator` (der gefährliche Fall: Modell-Linie als unser Trenner);
  `table`-Override raus → `wideContentStaysInsideTheAnswer`; `overflow-x: auto` zusätzlich am `.chat-log`
  → derselbe; History-Fehler durch `ChatMarkdown` → `onlyAnswersAreMarkdown`; Stream-Fehler durch
  `ChatMarkdown` → derselbe; `ChatTools` zeichnet `ChatMarkdown` → derselbe **plus**
  `aLiveTraceStaysBetweenItsInputAndTheAnswer` (der zählt die `.chat-tool-line`-Spanne — benannt, der Test
  ist nicht angepasst).
- **Mutationslehre**: die Behauptung „nie unsere Trenner-Klasse" gehört **nur** in den Log-Test —
  dieselbe Assertion zusätzlich im Markdown-Test macht eine Mutation an zwei Tests rot statt an einem.
- **Blatt-Lesen bestätigt**: `declarations()` sucht `indexOf(selector + ' {')`, deshalb überschattet
  `.chat-log .chat-markdown hr` den kürzeren Selektor nicht — und Prosa in `styles.css` darf nie
  Selektorwortlaut + `{` enthalten.
- **Struktur-Prüfung vor dem Umbau** (Jons Auflage): `grep` auf `querySelector|firstElementChild|chat-markdown`
  in `test/chat/*.tsx` ⇒ ausschließlich Nachfahren-Abfragen (`th`, `tbody td`, `pre.shiki`,
  `[data-testid="chat-code"]`, `code`) und `<li>`-Assertions über `.chat-log`; keine adressiert `table` als
  direktes Kind von `.chat-markdown` ⇒ `aTableAndStrikeThroughAreDrawnAndNotTyped` und
  `codeBlocksAreHighlighted` blieben nach dem Wrapper unverändert grün — kein Fund.
- **Nach Inc 2-N1: UI 166 Tests / 20 Dateien** in 8,25 s, `tsc -b` 0, oxlint 0/0 (70 Dateien). Doc-Lint 32
  Findings, alle fremd (paco 14, config-repo 14, chat 3 = UC-WP-72/74/75, ui 1 = UC-WP-70) — UC-WP-55/73
  nicht dabei, also kein neues Finding. Kein Backend, kein Draht, keine neue Dependency.

## Messung: Kosten pro Token (AntD-Umbau Inc 2-N1, 2026-11-06)

Probe in `chat.markdown.test.tsx` (nach dem Laufen **gelöscht**): eine Antwort wächst Zeile für Zeile,
50 Re-Renders wie beim Stream, `performance.now()` je Render. Wachsen ist Pflicht — identischer Text über
50 Renders misst nur den React-Bail-out (Compiler-Memo + stabile Props): gemessen 0,05 ms/Render, das wäre
als „Kosten pro Token" eine Lüge. Die Probe muss `*.test.tsx` heißen, sonst läuft sie nicht.

| Fall | Median | p95 | max |
|---|---|---|---|
| Prosa, 100 Zeilen (6689 Zeichen) | 4,82 ms | 7,78 ms | 8,32 ms |
| Zaun `ts`, 100 Zeilen (4389 Zeichen) | 14,02 ms | 18,90 ms | 147,70 ms (Erstrender lädt die Grammatik), letzter Render 18,90 ms |

jsdom ist die **Obergrenze**, der Browser ist schneller. Über 30 ms/Render im Zaun-Fall wäre ein Inc-4-
Risiko (Timeline zeichnet jede Antwort neu) — gemessen ist es die Hälfte, deshalb ist **kein** Cache und
keine Optimierung gebaut worden, und die Suite enthält keine Zeit-Assertion.

## Aus `AGENTS-DEV.md` ausgezogen (2026-11-07, Cap ~120 Zeilen)

Wissen, das nicht jeder Build braucht (Episodisches); der Wortlaut ist original:

Abschnitt **Backend-Tests**:

- **Lange absolute Pfade erreichen den SSE-Draht nicht**: `SmartToolExecutor.reportProblem` kürzt auf 180
  Zeichen, `allowed roots […]` fällt weg (das Modell sieht sie weiter) → Draht-Verweigerung am Präfix
  `<tool>: Write denied:` prüfen, Wurzeln nur im nächsten Provider-Payload (`getCapturedMessages()`).
- **AssertJ `assertThat(Optional).contains(x)` ist Gleichheit, nicht Substring** (`" /unbekannt"` fällt gegen
  `contains("/unbekannt")`). — **Prio-Block am Kopf behaupten**: `TaggedResult.PREFIX + TAG_QUEUED_USER_MESSAGE`;
  das nackte Tag-Wort steht auch im System-Prompt (`TaggedResult.INFO`) ⇒ als Substring von
  `getLastRequestBody()` ist es immer drin, die Behauptung liefe offen.
- `@Timeout` gilt je Testmethode, **nie** für Lifecycle-Methoden → dafür steht
  `junit.jupiter.execution.timeout.lifecycle.method.default=120s` in `junit-platform.properties`.

Abschnitt **Factory-UI**:

- **Ein offener Zustand wird an das laufende Objekt gebunden, nie an „Flag + letztes Element"** — ein
  Indikator sonst beim nächsten Turn auf dem alten Eintrag weiterblinkt (live-Items überleben den Turn). Und:
  eine Draht-Eigenschaft, die erst **nach** dem Zustandsende sichtbar wird (Zeit am geschlossenen Schritt),
  braucht **beide Zustände im selben Test** — sonst bleibt ihr Löscher grün. Und: **Identität schützt das
  _Ob_, nicht das _Wo_** — ein neuer Timeline-Eintrag braucht neben der Turn-Id den **Anker-Beweis** gegen
  die Antwortzeilen (Idiom `chat.page.test.tsx::toolLinesSurviveTheDoneRefresh`).

## Inkrement 5a-1: Sider statt Top-Navigation (2026-11-07)

R-WP-38 / UC-WP-70 G1+G3, R-WP-1, R-WP-35, R-WP-27. Rahmen (`app.shell.tsx`) besitzt die Projektliste
(`useProjectList`), `ProjectPage` stellt nur dar; `app.nav.tsx` + `app.nav.test.tsx` entfallen, neu
`app.sider.tsx` + `app.sider.test.tsx` (8) + `app.paths.test.ts` (1).

- **Messzahlen**: UI **201 Tests / 22 Dateien**, `tsc -b` 0, oxlint **0/0** (73 Dateien). Plan-Rechnung war
  200 — die eine Zeile mehr ist ein Befund, kein Fehler. Doc-Lint **28** Findings (Baseline 29): UC-WP-70
  verlässt die UNBELEGT-Liste, weil die Tests `// UC-WP-70` tragen — Arithmetik, **kein** neues Finding;
  R-WP-38/UC-WP-70 bleiben ❌ (G2 „Files" fehlt, das kommt erst in 5b), kein Doc-Flip.
- **`aria-current` am Menu-Eintrag ist laufzeitbewiesen** (vorher nur an der Quelle gelesen): Sonden M4
  (`aria-current` an allen Einträgen) und M5 (gar nicht gesetzt) machen `app.sider.test.tsx` rot. Beweis ist
  das Attribut **und** `.ant-menu-item-selected` ⇒ `ANTD-A9`.
- **12 Sonden, alle rot** und danach Restore per `diff` identisch: `breakpoint` weg, `collapsedWidth` weg,
  `selectedKeys={[]}`, `aria-current` überall / nirgends, Wurzeleintrag hinten, `onProjects` öffnet das erste
  Projekt, immer den Wurzeleintrag markieren, `onOpen` mit dem falschen Projekt, `AppSider` **in** den
  `Content` ziehen, `LastChatRoute` ohne `readLastProject`, `lightSiderBg: siderBg`. Treiber
  `/tmp/s5a_mutate.py` (python `assert count == 1` vor jeder Mutation, Backup `/tmp/s5a.<datei>`,
  Restore aus der **finalen** Kopie, `diff -q` danach).
- **AntD-Sider unter jsdom** (antd 6.7.0, `es/layout/Sider.js`): Defaults `width: 200` /
  `collapsedWidth: 80`; `dimensionMaxMap.lg = '991.98px'`; der Responsive-Effekt ruft den Handler **beim
  Mount** auf, ein `matches: true` in der `matchMedia`-Antwort genügt, kein `change`-Event. `collapsed`
  über `useControlledState` — **controlled ⇒ der Effekt setzt nichts mehr**. Trigger erscheint bei
  `collapsible || (below && zeroWidthTrigger)` und ist ein `span` ohne Rolle. `hasSider` meldet sich über
  Context (`siderHook.addSider`), ein eigener Wrapper um `Layout.Sider` verliert `.ant-layout-has-sider`
  **nicht** — der Content/Sider-Nestungstest verlässt sich darauf.
- **jsdom misst 0×0**: G1/G3-Beweis über DOM-Nestung (`main.contains(feld)`, `!sider.contains(feld)`,
  `:scope > .app-sider` unter `.ant-layout-has-sider`) + Klassen + Inline-`style.width`
  (`'0px'`/`'200px'`), nie über Layout-Maße.
- **oxlint `react(set-state-in-effect)`** beißt auch durch `useCallback(async …)`: `await listProjects()`
  dann `setState` im `try` ⇒ Warnung. `useProjectList` nutzt deshalb die Promise-Kette
  `.then(…).catch(…)` (Idiom der alten `project.page.tsx`) → 0/0.
- **`act()`-Warnung** („An update to ForwardRef … not wrapped in act") ist Vorbestand der Basis
  (antd 6 + React 19 + jsdom, auch an HEAD in `chat.composer.test.tsx`); `app.sider.test.tsx` erbt sie pro
  `render` (8 Instanzen). Kein neuer Defekt, nicht „reparieren".
- **Test-Doppelgänger `Host`** in `test/project/project.page.test.tsx` (besitzt die Liste, `onReload` setzt
  State) — damit bleibt `:264` ehrlich: `expect(reads).toBe(0)` statt `toBe(1)`, die Behauptung „kein
  Listen-Read durch ein Status-Ereignis" wäre mit eigenem GET des Blatts strukturell wahr.
- **Ein zusätzlicher GET `/api/projects`** pro `renderApp` (Rahmen-Mount). Alle Stubs tragen ihn;
  `stubAppServer(liste)` liest das Array bei jedem Aufruf ⇒ `aNewProjectAppearsInTheSider` schiebt in
  **dieselbe** Referenz.
- Bewusst offen: **G2 „Files"-Einheit** (5b), Id-Fallback der Route (5a-3), Namens-Segment (5a-2).

## Inkrement 5a-N1 (2026-11-07): Markierung als Funktion der Adresse bewiesen

Review-Verdict REJECTED, Grund nur im Test: drei Mutationen blieben grün (Auswahl aus **Klick-State**,
`activeKey` beim **Mount eingefroren**, Settings-`aria-current` beim Mount eingefroren).

- **Dauerhafte Regel daraus:** eine Markierung, die aus der Adresse gelesen wird, wird **nach einer
  Adressänderung behauptet, die nicht per Klick in dasselbe Element entstand**. Ein `unmount()` + Neu-Mount
  beweist die Regel **nicht** — er prüft zwei Mounts, nie eine Zustandsänderung. Derselbe Maßstab gilt für
  jedes „State kommt aus Quelle X": die platte Variante (`useState` ohne Pflege) ist zu leicht rot zu machen,
  getroffen werden muss **geseedeter State** und **beim Mount eingefroren**.
- **Bauort:** `app.routes.test.tsx` — die vier Weiterleitungs-Fälle (`/chat` mit und ohne letztes Projekt,
  verschwundenes Projekt, unbekannte Adresse) behaupten je die markierte Zeile gegen die Zieladresse;
  `app.shell.test.tsx::theSettingsEntryIsCurrentOnSettings` — **ein Mount, vier Adressen**
  (Einstellungen → Projekte-Liste → Chat über die **Listen-Zeile** → Einstellungen), `unmount()`-Weg ersetzt,
  nicht ergänzt (er bewies nichts zusätzliches).
- **Proben, je einzeln:** M1b (`selected = useState(activeKey)` + `setSelected(key)` im `onClick`) ⇒ **5 rot**;
  M1c (`const [frozenKey] = useState(navKeyOfPathname(pathname))`) ⇒ **dieselben 5**; M10
  (`const [settingsIsCurrent] = useState(pathname.startsWith(settingsPath))`) ⇒
  `theSettingsEntryIsCurrentOnSettings`. Die fünf: `chatOpensTheLastActiveProject`,
  `chatWithoutAProjectGoesToProjects`, `aProjectThatIsGoneLeadsToTheList`, `anUnknownAddressShowsTheApp`,
  `theSettingsEntryIsCurrentOnSettings`. **`app.sider.test.tsx` bleibt bei beiden Einfriermutationen grün**
  (Props ohne Adresswechsel) ⇒ Abnahmeort für „Mark = URL" ist der **Rahmen**, nie der Sider-Test allein.
- **LOW-2 (Decode):** `navKeyOfPathname` dekodiert das Segment, unzulässige Escape-Folge ⇒ Rückfall auf den
  Rohwert (kein Wurf); `chatPath` bleibt roh — zwei Antworten auf das Entkommen wären ein zweiter Eigner.
  Sonde „decode entfernt" ⇒ `anEscapedNameAddressOpensItsChatAndMarksItsEntry` +
  `theAddressNamesTheEntryThatShows` rot. Der Fall kommt **nur** aus einer hand-editierten `projects.json`
  (`SAFE_NAME` lehnt Leerzeichen ab) — Absicherung des Restrisikos aus 5a-2, kein Backend-Fall.
- **LOW-5 (Leiche):** `test/shared/theme.test.ts` bot sein synthetisches Beispiel unter
  `src/app/app.nav.tsx` an — Datei seit 5a-1 weg ⇒ `src/app/app.sider.tsx` (existiert, gleiche Form).
- **Zahlen:** UI **205 Tests / 22 Dateien**, `tsc -b` 0, oxlint **0/0** (73 Dateien), Doc-Lint **28**
  (identische Liste, kein neues Finding; R-WP-38/UC-WP-70 weiterhin ❌ bis 5b). Kein Backend, kein Core.



## Inkrement 5b-1 (2026-11-07): Einheit = Adresse, die Seite bleibt gemountet

`chat/:project/files` als **Kindroute, die nichts rendert**; „offen" ist eine Lesart der Adresse
(`navKeyOfPathname(pathname) === filesKeyOf(segment)`), `ProjectFiles` hat keinen `open`-State mehr, die
Fläche ist eine **innere** Komponente (`FilesArea`), die nur mit der Adresse gemountet ist. Sider: ein
flaches Einheiten-Item unter dem Projekt, das die Adresse nennt (Klasse `app-sider-unit`), genau eine Marke.

- **Zahlen:** UI **214 Tests / 22 Dateien**, `tsc -b` 0, oxlint **0/0** (73 Dateien), Doc-Lint **28**
  (unveränderte Liste, alle UC-CR-*/UC-PC-* fremd). Kein Backend, kein Core, kein `pnpm build`.
- **INC-5B-D1 (eine umgeschriebene Assertion, Behauptung identisch).** Vorher
  (`test/project/project.files.test.tsx::filesTabSitsOnThePanelTop`):
  `expect(declarations(".files-tab[data-open='true'][data-wide='true']")).toContain('right: var(--files-wide)')`
  — nachher: `expect(declarations(".files-panel[data-wide='true'] + .files-tab")).toContain('right: var(--files-wide)')`.
  Grund: die Breite ist der Zustand der **Fläche**; eine zweite Kopie am Tab war genau der zweite Eigner, den
  dieses Inkrement entfernt. Blatt-Regel accordingly: `.files-panel[data-wide='true'] + .files-tab` (Panel
  steht im DOM **vor** dem Tab — Geschwister in `.chat-workspace`, Spezifität 0,3,0 schlägt die nested
  `.files-tab[data-open]` mit 0,2,0).
- **INC-5B-D2:** der Tab-Klick ist jetzt eine Navigation (Push in beide Richtungen, Zurück schließt wieder).
  Die Datei-Tests erreichen das Panel unverändert über denselben Klick auf `files-open` — keine einzige
  Assertion dieser Datei hat sich deshalb geändert.
- **INC-5B-D3:** 5b-1 ist der eine bewusst gemischte Slice (Adresse/Key/Item/CSS dazu, `useState(open)` +
  `closeArea`-Maschine weg). Zwei Eigentümer von „offen" hätten R-WP-38 im selben Commit verletzt.
- **INC-5B-D4 (Testname, nicht Verhalten):** der Plan nennt `theUnitLeavesTheTreeWhenAnotherProjectIsNamed`
  mit „kein Eintrag ‚Files' überhaupt" für `activeKey = project:<anderes>`. Das ist mit demselben Plan
  unvereinbar: §4.3/§13.5 hängen die Einheit an **das Projekt, das die Adresse nennt** — und
  `theSiderEntryAndTheTabNameTheSameAddress` braucht den Eintrag genau dann, wenn die **Chat**-Adresse offen
  ist (sonst gibt es vom Chat aus keinen Sider-Griff auf die Einheit). Umgesetzt ist der Kern der Regel:
  Test `theUnitHangsUnderTheNamedProjectAlone` — bei `project:other` genau **ein** „Files", direkt hinter
  `other`, `demo` keins; und `noAddressMeansNoMark` (verschärft, nichts gelöscht) verlangt bei
  `activeKey = null` **kein** Einheiten-Item. Mutation „Einheit an jedes Project anhängen" macht beide rot.
- **Zwei Sider-Teststellen, die mitwachsen mussten:** `show()`-Doppel und das `view.rerender(<AppSider …/>)`
  in `theSiderShowsOneEntryPerProject` übergeben Props explizit ⇒ `onFiles` war an **beiden** Stellen fällig.
- **Draht Zählen, projektbezählt:** die Sider-Leiste öffnet den **Arbeitsstand**-Strom `/api/projects/events`,
  der Chat öffnet `/api/projects/<id>/events`. `chat.countOf('/events', 'GET')` zählt deshalb **beide** (im
  Rahmen: 2 statt 1). Behauptet wird projektbezählt: `countOf('/' + project().id + '/events', 'GET')` (und
  analog `/messages`). Sonst beweist der Test die Zahl der falschen Leitungen.
- **Mount statt Klick:** die Root-Ebene liest jetzt der Mount (`useEffect(() => readBranch('checkout',''),
  [readBranch])`), der Klick auf eine Wurzel bleibt `loadBranch` (setzt vorher `loading`). Geteilt in
  `readBranch` (nur lesen) und `loadBranch` (laden-markieren + lesen), damit der Mount kein synchrones
  `setState` im Effektrumpf tut — `react(set-state-in-effect)` ist hier verboten und `FilesLevel` zeigt für
  `undefined` schon den Ladehinweis. `readBranch` hängt in `loadBranch`s Abhängigkeiten **und** `remember`
  (oxlint `react-hooks(exhaustive-deps)` sonst mit Warning).
- **Mutationen, je einzeln, Restore per `cp` + `diff -q`:**
  - (a) **Kindroute → Schwesterroute** (`chat/:project/files` neben `chat/:project`, beide `<ChatRoute/>`) ⇒
    **Befund: bleibt grün** (90 Tests der drei betroffenen Dateien). React Router v6 rendert beide Male
    `RenderedRoute` derselben Stelle ⇒ dieselbe Instanz, kein neuer Mount, `/messages` bleibt bei 1 Read,
    `/events` bei 1 Strom, die Live-Zeile steht. Die Plan-Aussage „M1 ⇒ aRunningTurnSurvivesTheUnitClick
    rot" ist damit **nicht haltbar**: Mount-Identität ist hier Reconciliation, kein Routing-Beweis. Die
    Kindroute bleibt trotzdem die richtige Form (sie *sagt*, dass die Chat-Route gematcht bleibt, statt es
    der Trefferliste zu überlassen) — beweisbar ist sie durch diese Mutation nicht.
  - (a′) **freiwillige Gegenprobe, um die Blindheit des Tests auszuschließen:** `key={project.id}` →
    `key={pathname}` an `ChatPage` ⇒ `aRunningTurnSurvivesTheUnitClick` **rot** („Unable to find
    … `chat-live`"), 76 andere grün. Der Test sieht einen echten Remount also; er sieht nur den von (a)
    behaupteten nicht, weil es ihn nicht gibt.
  - (b) `chatFilesPath` gibt `chatPath` zurück ⇒ **3 rot**: `theUnitAddressIsItsOwnEntry`,
    `theTabOpensTheUnitAddressAndBack` („expected '/chat/demo' to be '/chat/demo/files'"),
    `theSiderEntryAndTheTabNameTheSameAddress`. Der Deep-Link-Test `theUnitAddressIsARouteOfTheChat` bleibt
    grün — er nennt die Adresse selbst, nicht den Erzeuger (so soll es sein).
  - (c) `FilesArea` **immer** gemountet (`{open && …}` → immer) ⇒ **6 rot**, darunter
    `closingThePanelForgetsThePositionInIt`, `fileAreaOpensOnCheckoutAndReadsItsWayDown`,
    `fileAreaShowsTheRejectionInsteadOfContent`, `theViewerCanAlwaysBeClosed` und die beiden
    Adress-Tests des Rahmens (das Panel steht dann auch bei geschlossener Adresse).
- **Verlauf, nicht Schönfärberei — Datei aus dem Gedächtnis geschrieben:** beim Umbau von
  `src/project/project.files.tsx` wurde die Datei einmal **vollständig überschrieben**, nachdem ihr Kopf
  (Importe, `FilesLevel`, `FileViewer`, `ROOTS`, `branchKey`) **nicht mehr gelesen**, sondern rekonstruiert
  war — Ergebnis: erfundene Helfer und Signaturen, die es so nie gab. Zurückgeholt mit
  `git checkout -- <datei>` (die Datei war **uncommitted**, aber ihr **Kopf** stimmte mit HEAD; verloren
  ging nur der eigene, laufende Edit). Afterwards in kleinen `diskEditFile`-Schritten auf dem realen Inhalt
  neu gebaut und per `git diff` **+ `git diff --ignore-all-space`** gegengeprüft (nur die geplanten Hübsche,
  kein verlorener Helfer). **Dauerhafte Regel:** eine Quelldatei wird **nie** aus dem Kontext neu
  geschrieben — entweder Zeile für Zeile editieren, oder erst komplett lesen und die gelesenen Zeilen im
  selben Zuge unverändert übernehmen. Vor jeder Teststufe: `git diff <datei>` gegen die erwarteten Hübsche.
  `cp`-Sicherungen nur von der **finalen** Fassung; `git checkout --` ist kein Rückweg für etwas, was
  Jons Hand schon geändert hat.

## Inkrement 5b-2 (Aufräumen, 2026-11-07) — hier eingefügt, vor dem 5b-1-N1-Block; zeitlich liegt er **danach**

- **Gemessen:** UI **216 Tests / 22 Dateien grün**, `npx tsc -b` 0, `npx oxlint` 0/0 bei 73 Dateien. Die
  Zahl steigt um genau zwei Tests (`aChatReadsTheProjectListOnce`, `aRefusedProjectListShowsTheList…`);
  LOW-3 verschiebt nur (`theFrameShowsTheProjectTree` → `test/app/app.shell.test.tsx`, Behauptungen
  wörtlich, `stubAppServer()` statt des lokalen `Host`-Doppels).
- **LOW-1:** `ChatRoute` fragt das Registry **nicht** mehr selbst — `projects` + `loaded` kommen aus
  `useAppContext()` (`useProjectList` meldet `loaded` in **beiden** Zweigen, auch nach Fehler). Der
  Deep-Link merkt das Projekt weiter über `writeLastProject` (Effekt an `[project]`, kein State).
- **Gate-Mutationen, beide genau einen Test treffend:** zweiten `listProjects()`-Ruf in `ChatRoute`
  zurückholen ⇒ `aChatReadsTheProjectListOnce` rot (3 Reads statt 1); `setLoaded(true)` nur im `then`
  ⇒ `aRefusedProjectListShowsTheListNotALoadingLine` rot. Ohne diese beiden Tests bliebe jede Mutation
  am Gate grün — deshalb sind sie Teil des Aufräum-Slices.
- **Zähler-Idiom:** Reads des Registry = `vi.mocked(fetch).mock.calls` mit `String(call[0]) === '/api/projects'`
  (`endsWith('/events')` trifft auch den Arbeitsstand-Draht, ein Zähler über Teilzeichenketten zählt dort doppelt).
- **Stub-Ansatz für eine abgelehnte Liste:** `stubAppServer()` zuerst, dann `fetch` um ein `vi.fn` wickeln, das
  nur `/api/projects` mit 500 ablehnt und alles andere an den Harness-Stub durchreicht — sonst hängt der
  offene `/events`-Strom des Rahmens als ungeschlossener Reader nach (der Harness kann nur seinen eigenen
  Stream schließen).
- **Die drei LOWs sind damit abgearbeitet:** LOW-1 (zweiter Read), LOW-3 (Test verschoben), LOW-4 (Prosa).

## Inkrement 5b-1-N1 (Nacharbeit auf Review `b4c202b0`, 2026-11-07)

Drei Punkte, **kein Verhalten** — zwei Nachweislücken und ein Kommentar mit widerlegter Mechanik.

- **Gemessen nachher:** UI **214 Tests / 22 Dateien grün** (keine neue Testdatei, kein neuer Test — die
  Assertion sitzt in einem bestehenden), `npx tsc -b` 0, `npx oxlint` 0/0 bei 73 Dateien,
  `AGENTS-DEV.md` **120 Zeilen** (Cap; gekürzt wurde das Abweichungs-Register **nicht**).
- **N1 (BUG-1):** `.files-panel[data-wide='true'] + .files-tab` (`styles.css:729`) greift nur bei
  unmittelbarer Vorgängerschaft; jsdom sieht weder die Kante noch die Reihenfolge. neu im Blatt-Test
  `filesTabSitsOnThePanelTop`: derselbe Test mountet jetzt (offen **und** breit über `openFileInTree`) und
  behauptet `data-wide='true'` am Panel plus `expect(tab.previousElementSibling).toBe(panel)`. **Warum hier
  und nicht in `theFilesTabOpensTheSlideOut`:** die Behauptung „der Tab reitet die breite Kante" steht in
  diesem Test — die Hälfte, die jsdom nicht sehen kann, gehört an denselben Ort, dann trifft eine
  Umsortierung genau einen Test. **Mutation** (`{open && <FilesArea …/>}` hinter den Tab gezogen):
  **`filesTabSitsOnThePanelTop` rot** — `expected <section class="view">… to be <aside class="files-panel">`;
  die anderen 12 der Datei bleiben grün (Restore per `cp` + `diff -q`).
- **N2 (BUG-2):** `app.routes.tsx` behauptete, eine **Schwesterroute** wäre „a new mount … `/messages`
  again … `/events` again" — **gemessen falsch** (React Router 7.18.5: kein zweiter `/messages`-Read).
  Kommentar sagt jetzt nur noch den wahren Grund (Unterpfad ⇒ Kindroute, rendert nichts, `open` ist eine
  Lesart des Pfads) + ein Verweis auf `docs/aifactory-architektur.md` („Rahmen, Adresse, Einheit"); die
  Kopf-Doku der Datei erzählt die Regel nicht mehr nach.
- **N3 (LOW):** ein Satz an `readBranch`, warum dort kein `loading`-Remember steht (einziger Aufrufer vor
  der Antwort ist der Mount, und `FilesLevel` zeigt für `undefined` schon den Ladehinweis; Klicklesungen
  gehen über `loadBranch`). **Nichts umgebaut.**
- **Restore-Falle, selbst passiert:** der `cp`-Rückweg für die Mutation war **vor** dem N3-Edit gesetzt ⇒ der
  Restore hat den gerade gesetzten Kommentar weggefressen (still, weil Comment-only). **Regel:** vor jeder
  Mutation die Sicherung auf der **dann** aktuellen Fassung neu setzen, und nach jedem Restore `git diff`
  gegen die erwarteten Hübsche — `diff -q` gegen die Sicherung beweist nur, dass die Sicherung alt genug war.
- **Umzug aus `AGENTS-DEV.md` hierher (Cap):** „Could not parse CSS stylesheet" bei **kaltem vite-Cache**
  ist Rauschen — erst `rm -rf node_modules/.vite`, dann auf HEAD vergleichen, bevor es als Befund zählt.

## Inkrement 1a (Top-Menü, 2026-11-07)

- **`Menu mode="horizontal" theme="dark"` läuft unter jsdom ohne Eingriff:** der bestehende `ResizeObserver`-Stub
  (`test/shared/setup.ts`) ruft nie zurück ⇒ kein Overflow, alle Einträge als `menuitem` erreichbar.
  **`disabledOverflow` war nicht nötig** (Plan R1) — also auch keine neue Zeile in `AGENTS-DEV.md`.
- **„Menü-Key = Adresse" ist am Chat-Eintrag nicht beweisbar:** `onNavigate(chatRoot)` statt `key` lässt
  `theTopChatEntryLeadsToTheLastChat` **grün** — `/chat` leitet auf das letzte Projekt um, die Zieladresse ist
  dieselbe. Gefangen haben die Mutation `theMvpPagesAreReachable`, `theUiSpeaksEnglish`,
  `theSettingsEntryIsCurrentOnSettings`. **Regel:** so eine Gleichung über die Einträge pinnen, deren Ziel
  sichtbar auseinanderfällt (`/projects`, `/settings`), nicht über den, in dem ein Redirect beide deckungsgleich macht.
- **Ein Band, zwei Leser:** `Layout.headerBg` **und** `Menu.darkItemBg` müssen denselben Token lesen
  (INC-L1-D1), sonst liegt AntDs Vorratston `#001529` auf unserem Band. Neuer Token ⇒ **drei** Stellen in
  `index.css` (hell, `[data-theme='dark']`, Media-Kopie), sonst spaltet `theDarkSheetIsOneTable`.
- **Zweites `<nav>` braucht eigenen Namen** (`content.nav.pages`), sonst werden alle
  `getByRole('navigation', { name: content.nav.views })`-Helfer mehrdeutig.
- **Doc-Lint-Baseline 28 → 29:** Jons Docs-Änderung bringt `UNBELEGT UC-WP-77` (Slice 1b) — kein eigener Befund.

## Inkrement 1b (Einheiten im Baum, 2026-11-07)

- **Zwei Marken-Beweise, zwei verschiedene Mutationen — beide nötig:** `chatKeyOf` im Deuter auf
  `projectKeyOf` zurückbauen ⇒ **9 Tests** rot (2× `app.paths`, 6× `app.routes`, 1× `app.shell`). Den
  Chat-Eintrag statt auf `onOpen` auf den Files-Griff legen ⇒ **genau 2** rot
  (`aRunningTurnSurvivesTheChatUnitClick` über die Adress-Assertion, `theChatUnitLeadsBackFromTheFileArea`).
  **Und die Lücke:** Chat-Eintrag **gar nicht** verdrahten ⇒ der Turn-Test bleibt **grün** (nichts passiert ⇒
  nichts stirbt), nur der Sider-Test fällt. Ein Turn-Test beweist also die **Adresse des Klicks**, nie den
  Griff selbst — deshalb beide Stellen testen.
- **Plan-Mutation „Chat-Eintrag navigiert auf `/chat`" ist in der Sider-Komponente nicht baubar** (sie kennt
  nur Callbacks, kein `navigate`); ohne zusätzlichen Props-Callback ist die Files-Variante die nächstechte.
  `/chat` wäre außerdem durch den Last-Project-Redirect deckungsgleich mit `/chat/<letztes>` (siehe 1a).
- **Einheiten sind DOM-Nachbarn, nicht nur Array-Einträge:** Reihenfolge Chat → Files über
  `unit.previousElementSibling` gepinnt (jsdom legt nichts aus, [[memory #15]]) — dieselbe Technik wie die
  `+`-Regel des Tabs in 5b-1-N1.
- **Doc-Lint wieder 28** (nur die fremden `UC-CR-*`/`UC-PC-*`): `UC-WP-76` und `UC-WP-77` sind jetzt belegt.

## Inkrement 1 N1 (Nachweise nachgezogen, 2026-11-07)

- **Ein Klick auf denselben Ort beweist nichts:** der Turn-Test startete auf `/chat/demo` und klickte die
  Chat-Einheit — derselbe Schlüssel, ein toter Eintrag ist ein No-op und bleibt grün. Start jetzt eine
  Adresse **tiefer** (`/chat/demo/files`) und die Fläche muss danach **weg** sein ⇒ Chat-Zweig in
  `app.sider.tsx` gestrichen = `aRunningTurnSurvivesTheChatUnitClick` rot (`/chat/demo/files` ≠
  `/chat/demo`) und `theChatUnitLeadsBackFromTheFileArea` rot. **Regel:** ein Klick-Test braucht eine
  Behauptung, die der Nicht-Klick nicht erfüllt (Adresswechsel oder entferntes Element).
- **AntD-Optik ist in jsdom nur über die Klassen der Basis beweisbar:** `theme="dark"` und
  `mode="horizontal"` waren nirgends gepinnt — `Menu.dark*`-Tokens zeigen nur, was die Basis malt, **wenn**
  sie dunkel zeichnet. Pin jetzt `ant-menu-dark` + `ant-menu-horizontal` an
  `theHeaderCarriesTheBrandThePagesAndTheName`; je eine Mutation ⇒ **genau dieser** Test rot.
- **Zwei Navigationen, eine Adresse ⇒ ein Render:** die Einigkeit von Kopf- und Sider-Marke ist nur im
  **selben** Render beweisbar (`theTopMarkAndTheTreeMarkAgreeInOneRender`) — zwei Mounts können je eine andere
  Adresse richtig haben. Gegenprobe: Seiten-Deuter auf das nackte `/chat` verengt ⇒ 4 rot (der neue darunter).
- **Toter Zweig ist ein Befund, kein Schmuck:** `activeKey === key` an der Projekt-Marke war seit D6
  unerreichbar (der Deuter antwortet nie `project:<seg>`; `AppSider` hat nur `app.shell.tsx` als Producer; kein
  Test setzt den Key) ⇒ **nur-löschend** entfernt. `projectKeyOf` bleibt Item-Key und Klick-Griff.
- **`Menu`-Baum (rc-menu 1.6.1) ist nur über Rollen beweisbar, nie über Klassen:** Gruppentitel =
  `div[role=menuitem]` **mit** `aria-expanded`+`aria-haspopup`, Kind = `li[role=menuitem]` **ohne** sie;
  Kinder einer geschlossenen Gruppe fehlen im DOM ganz. Das Einrück des Titels steht **inline**
  (`hooks/useDirectionStyle.js:24`) ⇒ eine eigene CSS-Regel für die Kinderstufe trifft keinen Knoten.
  Ein Icon im Kind gehört `aria-hidden`, sonst hängt sein `aria-label` am Accessible Name („folder Files").
- **Gesteuerte `openKeys` + responsive Basis = leere Keyliste:** überscheitet `Layout.Sider` seinen
  `breakpoint`, meldet `Menu` `onOpenChange([])` (`hooks/index.js:55-58`, Umschalten inline ↔ popup) ⇒ alle
  Gruppen zu, ohne Nutzeraktion. Eigene Ableitung nur bis `touched === null` tragen lassen, oder — S1b — den
  Sider unter dem Breakpoint durch einen `Drawer` ersetzen, dann gibt es keinen Umschalt-Punkt mehr.
- **`Layout.Sider` mit `collapsedWidth={0}`:** den `zeroWidthTrigger` gibt es in **beiden** Zuständen, und
  `onCollapse(value,'responsive')` feuert auch bei **gesteuertem** `collapsed` (`layout/Sider.js` ruft
  `handleSetCollapsed(mql.matches,'responsive')`) — gesteuertes Ein- und Ausklappen und automatisches
  Ansprechen vertragen sich; der alte Glaube „gesteuertes `collapsed` schaltet den Breakpoint ab" ist falsch.
- **`antd`-CLI ist der billige Vorgeschmack:** `npm i -D antd` liefert `antd info <Komponente>` (Props, Import,
  statische Methoden), `antd list`, `antd lint ./src` (Regeln A001–A099) und `antd migrate`. `antd usage` ist in
  6.4.1 weg. `antd env <pfad>` **schreibt** in das Projekt (main.tsx, vite.config.ts, …; Backup nach
  `.antd-backups/`) — für eine Demo-Seite also nur nutzen, wenn dieser Überschreib-Plan gewollt ist.
- **`Layout.Sider` ohne eigenen Griff:** `trigger={null}` nimmt **beide** Auslöser (Text-Pille
  `…-sider-trigger`, Null-Breiten-Auslöser `…-sider-zero-width-trigger`, Default-Glyphe `BarsOutlined`) —
  `layout/Sider.js`: `triggerDom = trigger !== null ? zeroWidthTrigger || <div …> : null`; die Klasse
  `…-has-trigger` gibt es nur mit eigenem Griff. Ein `getByText(/menu.?fold/)` sieht davon **nie** etwas:
  die Griffe sind `span`/`div` ohne Rolle, ihre Zeichen sind Icons.
- **`Menu` im gefalteten Sider verliert seine offenen Gruppen:** `menu.js:88` rechnet
  `inlineCollapsed ?? siderCollapsed`, die Basis schaltet also auf die Popup-Form und meldet beim Umschalten
  `onOpenChange([])` ⇒ alle Gruppen zu, ohne Klick. `inlineCollapsed={false}` hält die Inline-Form (bei
  `collapsedWidth={0}` ohnehin nichtsichtbar) und rettet die offenen Gruppen. **Weder `antd info Menu` noch
  `antd lint` sagen dazu etwas** — Maß ist `node_modules/antd/es/**`.
- **Ein Text-`Button` auf dem dunklen Band malt seine eigene Farbe:** `Layout.headerColor` gilt dem Text im
  Band, nicht der Button-Oberfläche — darum eine Zeile im eigenen Blatt (`color: var(--top-text)`),
  `:hover` nicht vergessen, sonst blinkt das Zeichen beim Draufzeigen dunkel.

## Inkrement 4 UI — Datei-Bereich als `Drawer` (2026-11-08)

- **`getContainer={false}` ist Inline, nicht „irgendwohin":** rc setzt `inline: getContainer === false`
  (`@rc-component/drawer/es/Drawer.js:102`), die Basis hängt `&-inline { position: absolute }` an den
  sonst `fixed; inset:0; pointer-events:none` stehenden Root
  (`antd/es/drawer/style/index.js:38-43, 63-65`). Der Root sucht sich den nächsten positionierten
  Vorfahren — hier `.chat-workspace` —, damit bleiben Kopfzeile und Baum frei, und Tab und Fläche teilen
  sich die Oberkante `top: 0`, ohne dass eine Kopfzeilenbreite kompensiert werden muss. Nebenbei: die
  eigenen Tokens (`--files-width`, `--files-wide`) bleiben auflösbar, und der Focus-Trap ist in dieser
  Kombination per Default **aus** (`antd/es/drawer/Drawer.js:122-125`).
- **`aria-modal` ist ein getyptes Prop, kein JSX-Trick nötig:** der Drawer nimmt
  `Pick<DialogHTMLAttributes, …>`; `pickAttrs(aria)` (`@rc-component/drawer/es/DrawerPopup.js:226`) legt
  aria-Sachen aufs `div[role=dialog]`, und die eigenen `restProps` werden **danach** gespreizt
  (`DrawerPanel.js`) — `aria-modal={false}` gewinnt also gegen das harte `"true"` der Basis.
- **Breite: `size` wird inline auf den Content-Wrapper geschrieben, die Klemme ist `max-width`.**
  `size` geht unverändert durch (`DrawerPopup.js:140-164, 229-236`), ein Blatt-`width` verliert gegen
  inline; `max-width` ist ein anderes Property und klemmt — muss aber die Basis-Regel
  `max-width: 100vw` auf demselben Element überstimmen, die unter `:where(.css-hash).ant-drawer …`
  (0,2,0) **nach** dem eigenen Blatt injiziert ⇒ drei eigene Klassen nötig. Die Übergangsregel lebt von
  `transition: all` genau dieses Wrappers (`style/index.js:78-86`), der Wechsel der Breite animiert mit.
- **Der Body der Basis scrollt:** `ant-drawer-body` = `flex:1; min-width:0; min-height:0; overflow:auto`
  (`style/index.js:192-197`). Wer den Inhalt selbst scrollen lassen will, muss ihm eine Höhe geben —
  sonst wächst der Inhalt und der Body trägt den Balken. Polster der Basis (`padding: paddingLG`) mit
  `classNames.body` + drei Klassen wegnehmen, nicht per `styles={{body:…}}` (dann wandert Aussehen aus
  dem Blatt).
- **Ohne Maske ist der Schirm bedienbar — und zwar wirklich:** Maske hätte `pointer-events:auto`
  (`style/index.js:67-76`), ohne sie bleibt der Root bei `pointer-events:none` während die Sektion `auto`
  bekommt (`:42, :124`); der Scroll-Lock ist an die Maske gehängt (`autoLock: mask && …`,
  rc `Drawer.js:123`), fällt also mit ihr weg. `push` braucht keine Konfiguration: `pushed` kommt aus dem
  Context einer Darüber-Ebene (`DrawerPopup.js:68-99`) und ist bei einer Ebene immer `false`.
- **Esc ist eine window-Abfrage, kein Fokus-Test:** `useEscKeyDown`
  (`@rc-component/portal/es/useEscKeyDown.js:17-31`) hängt am `window` und ist IME-geschützt —
  `fireEvent.keyDown(window, { key: 'Escape' })` schließt von jeder Fokusstelle, auch aus dem Chat-Feld.
- **`destroyOnHidden` baut in jsdom sofort ab** (rc `Drawer.js:77-79`, gemessen: Klick auf Zu ⇒ Knoten
  weg in 27 ms). Die Leave-Animation der **Hülle** läuft über `motionDeadline: 500`, darum müssen Tests,
  die „nichts bleibt zurück" behaupten, in `waitFor` — sonst gewinnen sie nur, weil die Animation noch
  läuft.
- **jsdom zahlt jede Rollen-Abfrage mit der Regelzahl des Dokuments — der teuerste Fund dieses
  Inkrements:** der Drawer injiziert beim Mount **+92 Regeln** (+18 KB; 983/38 Sheets statt 891/36), die erste
  `getAllByRole('button')` danach 494 ms statt 49 ms, und jede DOM-Änderung nimmt das Geld erneut —
  eine Datei von 7,22 s auf 48,6 s, ein Einzelfall über das 5-s-Timeout. Der Preis hängt an den Regeln,
  nicht am Mount (Mount/Unmount selbst: 15/27 ms) und nicht an `:has()` (ersetzt ⇒ keine Änderung).
  `test/shared/setup.ts` verschiebt `style[data-css-hash]` per `MutationObserver` aus dem Dokument;
  danach 216 Tests / 22 Dateien in 8,2 s. `StyleProvider container` aus `@ant-design/cssinjs` wäre der
  vorgesehene Weg, ist aber **nicht lösbar** (pnpm: `node_modules/@ant-design/` enthält nur `icons`, antd
  re-exportiert nichts davon) — er bräuchte einen eigenen devDependency.
- **`--ant-*`-Variablen sind nicht `:root`:** die Basis legt sie auf eine generierte Klasse
  (`.css-var-_r_0_`, `cssVar`-Modus), ein Blatt außerhalb dieser Klasse liest sie nicht — Bewegungen im
  Blatt bleiben darum Literal (hier `0.3s`).

## Inkrement 4 UI — N1 Delta: zwei rechte Overlays, ein Render-Pass (2026-11-09)

- **React wendet den eigenen State der View an, bevor die neue Adresse sie erreicht** (gemessen, Drawer +
  Insight in derselben Zeile): Klick auf eine Kachel, die den Insight öffnet, während die Adresse den Drawer
  hält, liefert einen Pass mit `insight` gesetzt **und** `filesOpen === true` — die Adresse kommt erst im
  nächsten Pass an. Die Freigabe-Regel „Kante besetzt ⇒ kein Insight" löscht in diesem Pass genau den
  Wunsch, der gerade entstanden ist. Lösung: Ask und Faktenlage in **einem** State-Objekt
  (`{ name, edgeAsked }`), Konsum im Pass der Ankunft; Sichtbarkeit zusätzlich über `shown` (solange die
  Adresse die Kante hält, `null`) ⇒ zwei Overlays in einem Frame sind nicht „verhindert", sondern unmöglich.
- **`flushSync(() => navigate(…))` dreht diese Reihenfolge nicht** — und `flushSync` ist in diesem Stand
  **nicht** aus `react` exportiert (react 19.3.1, gemessen über `node -e require('react')`; nur `react-dom`
  hat es), der Trick würde also einen DOM-Import in eine Seite ziehen. Beides verworfen.
- **Remount war hier die Fährte, nicht der Befund:** gleicher Heading-Knoten per `isSameNode` **und**
  `historyReads() === 1` scheiden „die Route hat die Seite neu gebaut" sauber aus, bevor an State-Designs
  gedreht wird.
- **`oxlint`-Grenzen, die die Form erzwungen haben:** `react(refs)` meldet Ref-Zugriff **im Render**
  (4 warnings) ⇒ kein Ref-Marker über die Pässe; `set-state-in-render` existiert als Regel **nicht**, ein
  guarded `setState` im Render bleibt also der erlaubte Weg (`set-state-in-effect` bleibt verboten).
- **`useLocation().key` ist der einzige History-Beweis für „geändert" oder „nur neu gemalt":** ein Push auf
  die gezeigte Adresse lässt `pathname` unverändert, der Key wechselt. Der Test-Harness trägt ihn als
  `data-entry` am `current-path`-Knoten.
- **Zwei Drähte enden auf `/agents`:** `/api/projects/<id>/agents` (Roster) und
  `/api/projects/<id>/agents/<name>` (Transcript eines Mitglieds) — ein `endsWith('/agents')`-Zweig frisst den
  zweiten, also Transcript-Zweig **per Regex vorher** (`/api/settings/agents` wird noch früher abgefangen).
  Roster ist jetzt Parameter von `stubAppServer(projects, roster = [DEV])`; der Default hat kein `TRANSCRIPT`,
  damit keine Kachel eine Tür bekommt, die kein Fall bestellt.



## Inkrement A7 — Werkzeug-Schritte im Restore (2026-11-09, R-WP-47 / UC-WP-82)

- **Ein restaurierter Schritt ist eine Zeile des Bestands, keine live-only Zeile** — das ist der ganze Umbau,
  und das ist der Satz, der acht UI-Pinnen bewegt hat: (a) ein **Clear** nimmt die Spur mit (vorher: sie
  blieb), (b) nach dem Read trägt **kein** Schritt ein `title` (UC-WP-72 G5 — der Bestand kennt keinen
  Moment; die Ankunfts-Uhr gilt nur, solange der Schritt live ist), (c) nach dem Reconnect bleibt die **Spur
  stehen**, der **Diff** nicht (UC-WP-68 bleibt). Die Kappung `MAX_TOOL_LINES` gilt für eine restaurierte
  Gruppe genauso — gleiches Widget, kein zweiter Pfad.
- **Misch-Gruppe: restaurierte Schritte führen, live-Items folgen** (gemessen). `entriesOf` schiebt erst die
  Rows bis zum Anker, dann den Live-Run; der Read liefert die Schritt-Zeile **vor** der live gesehenen
  Position. Anhängen (`[...previous.items, ...items]`) gab `think` vor `step` — verkehrt.
  `pushTrace` kehrt darum für eine restaurierte Gruppe um.
- **`liveLinesAfterHistory` lässt nur noch live eintreffende Schritte durch** (`index >= mark`): was vor dem
  Read da war, liefert der Read jetzt selbst. Der einzige Fall, in dem ein live gesehener Schritt einen Read
  überlebt, ist also ein Schritt **hinter** dem Mark — vorher war jeder Schritt ein Immer-Bleiber.
- **Backend-Messwerte, die der Plan offen ließ:** ein **gestoppter** Turn hinterlässt den Schritt **mit
  Auszug** (das Result ist vor dem Stopp schon im Bestand), nicht nur als Ankunft. Die Schreib-Ablehnung
  (`WritePinningTest`) und der `!`-Block (`ChatQueueTest`) stehen **im Auszug** des Schritts — der Kern legt
  gequeueuerte Texte aufs Werkzeug-Ergebnis (`ToolService`), also verbannen die Tests sie aus dem
  **Gespräch** (`filteredOn(role != "tool")`), nicht aus dem Verlauf.
- **Zahlen:** Backend 227 → **231** Tests (4 neue, keiner gelöscht), UI 218 → **221** (2 neu in
  `chat.log.test.tsx`, 1 neu in `chat.page.test.tsx`, `aReloadedLogInventsNoTimes` um einen restaurierten
  Schritt erweitert). `ProjectRuntime.java` 589 → **523**, `ConversationRestore.java` **153**,
  `chat.log.tsx` **310** — alle unter 600.
- **Kein zweites Maß, kein zweiter Speicher, kein neues CSS-Wort:** der Auszug kommt aus `AgentTranscript.cut`
  (jetzt paketprivat, geteiltes Maß mit dem Insight), non-Text-Payload ⇒ **leerer** Auszug (das `[no text]`
  gehört in die Insight-Zeile, nicht in den Chat).

