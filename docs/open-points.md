## ⏳ Nachtzyklus 5 (2026-10-16) — Token-Schätzung chars×2/7 → chars/3
- **Punkt:** Estimator `ChatMessageUtil.charsToTokens` (chars×2/7 ≈ /3.5) unterschätzt systematisch
  (~110k statt ~120k gemessen). Paul-Idee: /3. Jon: **umgesetzt** — R1 in token-usage.md sagt
  ohnehin schon „chars / 3", der Code war die Abweichung; zu niedrige Schätzung ist für die
  Kontext-Anzeige (Compact-Button used/max) die gefährliche Richtung (Overflow-Risiko), zu hoch nur kosmetisch.
- **Rückversicherung:** Paul hat /3 vorgeschlagen („nicht so wichtig, mehr Info") — Umsetzung im
  Nachtzyklus autonom; widerspricht Paul, zurück auf ×2/7 (Änderung ist eine Konstante). **Umgesetzt
  `fbe2866f` + Zentralisierung `tokensToChars` `3199f732` (Open-Q1).**
# Open Points

Status je Punkt: ❓ offen · ⏳ selbst entschieden (Rückversicherung mit User steht aus) · 🔒 geklärt.
Geklärte Punkte ohne eigenes Feature-Doc: [resolved-points.md](resolved-points.md).
Aufgeräumt 2026-10-14 (Paul-Order): Gelöste → resolved-points.md, Wertloses gelöscht, Rest komprimiert.

## 2.14.0 (2026-10-19) — Jon-Entscheidungen zur Rückversicherung

- ⏳ **R-CC-19 (Zähler = Provider-Input + Schätzung der angehängten Messages):** von Jon entschieden
  (Overflow-Klasse, für die R-CC-18 allein nicht reicht). Folge: R-CC-6 ist präzisiert — „Provider-
  Wert ohne Tilde" gilt nur unverändert; nach `addResult` trägt der Zähler einen geschätzten Anteil
  und zeigt `~`. **Rückversicherung:** Paul bestätigt Semantik + Tilde, oder wir nehmen
  `updateTokenCounter` zurück (dann R-CC-18 wieder auf dem alten, blinden Zähler).
- ⏳ **Codegraph-Icon-Motiv:** Paul-Entscheid 2026-10-19 — Header zeigt die **Codegraph-Glyphe**
  (Lupe gehört zur Suche); `2a238ad4` per Datei-Restore zurückgenommen, Doku (R-CG-8) angeglichen.
- ⏳ **Codegraph-Windows-Fallback `cmd /c codegraph serve --mcp`:** von Jon entschieden und im
  Factory-Test gepinnt, aber **auf Windows ungetestet** (Maschine = macOS). Bei Pauls nächstem
  Windows-Lauf prüfen: startet der Server ohne absoluten Binary-Pfad?

## 2.14.1 (2026-10-19) — externes Review TOP 1–3: Jon-Entscheidungen

- ⏳ **R-CG-15 neu (Projektwechsel im Monorepo):** Befund **bestätigt** — `CodegraphConnectionService:106`
  kehrt bei gleichem `workDir` zurück, `State.project` bleibt A, `CodegraphContextItem` mappt weiter A.
  SOLL geschrieben: Verbindung bleibt, State-Refresh auf B. Nebenwirkung, die Paul kennen sollte:
  jeder Projektwechsel mit gemeinsamem Index erzeugt **eine neue Chat-Zeile** (Dedup-Key ändert sich);
  Alternative wäre ein Live-Projekt im Item (dann zwei Wahrheiten: State + Supplier).
- ⏳ **R-CG-14 Fix-Mechanik präzisiert:** der Reviewer schlug „Adopt und Disconnect über denselben
  Lock" vor — Jon: **nein**, ein Lock über `service.connect()` lässt Dispose auf einen 5–10-s-Spawn
  warten. Spezifiziert: Generation + `disposed`-Flag, Prüfung nach dem Connect, Lock nur um den
  kurzen Zustandswechsel. Ergänzt: eine stale Generation schreibt auch **kein** `setMcpEnabled`
  (der `finally` in `McpConnectionService.connectServers` würde sonst einen Toggle-OFF überschreiben).
- ✅ **TOP 3 (Compact `result:`-Marker):** kein neuer Befund — identisch mit R-CIB-4 Stufe 2
  (❌ specified 2026-10-19: `lastOccurrence` raus, das erste Vorkommen ist immer strukturell).

## 2.14.1 (2026-10-19) — Workspace-Nesting: Jon-Entscheidungen zur Rückversicherung

- ⏳ **Ergebnis-Limit schlägt Tiefe (R-WN-1):** Ist das Limit erreicht, wird ein schon gelieferter
  Duplikat-Eintrag nicht mehr auf die tiefere Sicht umgeschrieben (kein Volldurchlauf nach dem Cap);
  die Datei zählt weiterhin einmal, das Limit ist benannt. **Von Jon entschieden** (Performance gegen
  ein Rand-Kosmetikum: der Pfad zeigt dann den Parent, die Datei ist trotzdem nur einmal da).
  Alternative wäre ein Volldurchlauf nach dem Cap — bei jedem großen Monorepo-Search teuer.
- ⏳ **„Gleich tiefe Projekte → zuerst gefundener Pfad bleibt" bleibt ungetestet (Da-Dok F1):**
  der Fall ist nur bei **demselben** Ordner zweier Projekte beobachtbar, und Eclipse lehnt ein zweites
  Projekt am selben Ordner normalerweise ab (gleicher Disk-Pfad = gleiche Datei; anderer Disk-Pfad =
  andere Datei, kein Dedup-Fall). Die `!equals`-Zusicherung bleibt als Defensive stehen — sie ist im
  Test-Workspace nicht rot zu bekommen. **Rückversicherung:** Paul akzeptiert das, oder wir bauen den
  Guard ab (dann müsste irgendwo belegt sein, dass Eclipse gleiche Projekt-Ordner ausschließt).

## ❓ Doc-Aufräum-Slice: Belege, Messungen, Vorfälle raus aus den Feature-Docs (Paul 2026-10-19)

- **Paul-Entscheid:** Die Regel im PO-Prompt („Belege, Messungen und Gegenproben liegen in
  `peon-plan/`, nie in `${docs}`") **bleibt** — es trägt keinen Mehrwert im Fachdoc. Der Bestand ist
  entsprechend nicht-konform und wird aufgeräumt (kein Release-Blocker).
- **Was bleibt:** fachliches SOLL **und** fachliches IST je Regel — genau ein Statusmarker
  (🚧 / ❌ / ✅). Der Anker ist die **ID**, nie der Testname: 1 BDD → 1..n Tests, der Test trägt
  `// R-…` / `// UC-…`. **Was fliegt:** Commit-Hashes, Surefire-Zahlen, Messreihen,
  Vorfall-Erzählungen, Beleg-Tabellen **und Testnamen im Fließtext** (sie brechen bei jedem Rename).
- **Bekannte Kandidaten (beim Aufräumen per Grep vollständig inventarisieren, nicht nur diese):**
  `compact-lock.md` (Beleg-Tabelle), `codegraph.md` (Fakten-Bullet), `java-debugger-tool.md`,
  `compact.md` (Commit-Hashes in R-Zeilen), `tagged-results.md`, `bounded-caches.md`.
- **Ablage:** Belege nach `peon-plan/` (z. B. `peon-plan/belege/<feature>.md`), sonst ersatzlos —
  git hat die Historie.



## Context-Overflow / ContentProvider Bug-Zyklus (Paul-Order 2026-10-14 — nächster Zyklus)

Logs nur mit searchAgent (`.metadata/.log`, GROSS). Passende offene Punkte:

- ❓ **Auto-Compact bei Context-Overflow (2026-09-29):** Request überschreitet das Fenster
  (`exceed_context_size_error`, Da Mek 431005 vs. n_ctx 170240 nach aufgeblähtem Tool-Ergebnis)
  → Agent soll **selbst** compacten statt hart zu sterben; aktuell stirbt der Call (bekannter
  Dreiklang aus open-to-discuss.md: Hint-Dedup, ApiRetry auf totem Payload, stille Cancellation).
  Fall 2+3 (Thinka, 811k → 954k): Clear brachte NICHTS → Flut kommt aus der per-Request-Injektion
  (Static-Context/Workspace-Memory/Standing-Orders), nicht aus dem Agent-Memory; Wachstum ~143k
  ≈ ein Tool-Result — jeder fehlgeschlagene Versuch schiebt etwas nach. Stack:
  `PeonAiService.call:523` → `ToolService.executeLoop:159` → `SmartToolExecutor.run:40`.
- ❓ **Context-Pollution-Quellen (2026-09-24):** 337461 Provider-Tokens vs. 114512 Compact-Schätzung —
  Kandidaten: (a) `eclipseReadFile`/`diskReadFile` ohne Cap (`FileLines` 0/0 = ganze Datei),
  (b) `docs/index.md` + `docs/memory.md` + AGENTS.md **pro Turn** in Jons System-Context
  (`AgentContextComponent.java:130-140`, index.md wächst ungebremst), (c) Workspace-Memory-Snapshot
  ohne Cap. Frage: Caps an der Quelle oder bewusst offen, weil der Compact-Input budgetiert ist
  ([compact-input-budget.md](compact-input-budget.md))?
- ❓ **Workspace-Memory-Snapshot Vollkopie je `memoryAdd` (2026-09-23):** Snapshot-Key = entries-Hash
  (ADR-0032) — jede Mutation erzeugt eine frische Vollkopie aller Einträge bis zum Compact. By
  design, skaliert aber schlecht. Eigener Design-Punkt (inkrementeller Snapshot / Dedup /
  Compact-frequenter)?
- ⏳ **CompactSessionTool kompaktiert `agent.getMemory()`, Loop fährt `req.getMemory()` (Bug B,
  latent, 2026-09-27):** `CompactSessionTool.java:23-29` vs. `ToolService:200`; für Haupt-Agenten
  zufällig dasselbe Objekt (`AbstractAgent.doCall` setzt es per Konvention). Sobald ein echter
  SearchAgent mit compactSession existiert: Compact leert das **falsche** Memory → Summary
  unerreichbar, R-CC-4-Guard blockiert neue Hints → stuck. Optionen: (1) Tool kompaktiert
  `req.getMemory()` (berührt Compact-Ownership), (2) fail-fast im Loop (ehrlicher Fehler).
- ⏳ **compactSession trotz agent == null exponiert (2026-09-27):** Hint sagt „cannot be compacted",
  Tool wirft erst beim Call — `toolSpecifications` lässt es weg bei agent == null. Mit Bug B
  zusammen behandelbar.
- ❓ **Compact-Button ohne Monitor (2026-09-29):** User-getriggerter Compact übergibt scheinbar
  keinen Monitor — UI zeigt nichts während der Compressor läuft (Feedback-Loch). Verwandt:
  compact-lock / agenten-status-im-header.
- ❓ **Header: Jon-Context-Größe bleibt „0k estimate" (2026-09-27):** Roster-Refresh ist
  event-getrieben (`AIChatView:336-340` via onTokenUsage) — Turn ohne Provider-Usage feuert nicht.
  SOLL-Frage: Estimate-Pfad zusätzlich in den Roster-Refresh?
- ⏳ **StreamingBridge/ApiRetry geteilt über toBuilder (2026-09-27, ADR-0058):** parallele
  Nested-Calls würden um latch/responseRef racen; heute keinen, Revisit mit
  async-agent-tools-proposal.md.

## Tagged Results — Nachträge (Zyklus 2026-10-18)

- ❓ **R-*-Regeln haben kein Linter-Gate (2026-10-18):** `DocsLinter:241` nimmt `R-*`-Definitionen
  nicht in `defById` auf, `:254` überspringt sie im ✅-Check. Mit UC-only `idPattern` sind
  `// R-…`-Testmarker unsichtbar; mit einem Muster, das sie einschließt, werden sie `VERWAIST`.
  Folge: R-only-Docs (`tagged-results.md`, `compact-input-budget.md`, `compact.md`) haben **kein**
  maschinelles ✅-Gate — Da-Dok-Review ist die einzige Absicherung. Optionen: (a) R-Form als
  Definition zulassen (Überschriften `#### R-<F>-<n>`, wie bei UC), (b) solche Docs zusätzlich mit
  UC-Use-Cases ausstatten. Ohne Entscheidung bleibt der Linter blind für einen Großteil der Docs.
  *(Nebenbefund 2026-10-18: `codegraph.md` hat `idPrefix: CG` bereits, `mcp.md` keins — beides
  ändert an diesem Problem nichts, weil R-Definitionen im Linter grundsätzlich übersprungen werden;
  `idPrefix` allein ist also kein Fix.)*
  **Falle (2026-10-19, 2 Findings im eigenen Lauf):** eine Überschrift, die **mit einer Regel-ID
  beginnt** (`## R-CC-18 — Nachträge …` in dieser Datei), wird als Definition geparst →
  `PRAEFIX_FEHLT` bzw. `DOPPELT_DEFINIERT`. Punkt-Überschriften neutral formulieren (ID erst nach
  dem ersten Wort), sonst „definiert" man eine Regel in einer Notizdatei.
- ❓ **`AIChatView.applyTextSelection` — geändertes Verhalten bei disposed parent (Pauls WIP,
  2026-10-18, nicht committet):** das Refactoring (`if (parent == null || parent.isDisposed()) return;`)
  überspringt jetzt **auch** `uc.setTextSelection(ts)`; vorher lief die Selektion weiter, nur der
  Refresh unterblieb. Wenn das bewusst ist: kein Punkt. Wenn nicht: der frühe Return muss hinter
  `setTextSelection`.
- 🗑 **Untracked Session-Artefakte — ✅ erledigt (2026-10-19):** die Findings sind nach
  `open-points.md` (Codegraph-Konflikt) überführt, die Smoke-Datei und die alten Archive sind
  gelöscht; `peon-plan/` enthält nur noch den heutigen Zyklus-Plan (lokal, untracked).

## Nachträge zum Compact-Zyklus (R-CC-18, 2026-10-19)

- ❓ **Estimate vs. gesendetes Payload (2026-10-19, live gemessen):** `compactDev` meldete
  `~18772 (estimate)`, der nächste Delegate-Call ging mit **175555** Tokens raus und starb am
  `n_ctx` 175104 (2×). D3/`providerUsageStale` erklärt den **Zähler** (stale Provider-Wert
  überschreibt den re-derived Wert) — ob damit auch das Payload wieder zur Anzeige passt, ist
  **nicht** gemessen. Nach dem Merge reproduzieren: nach einem Compact den `memory=`/`estimate=`-
  Diagnose-Dreiklang gegen `n_prompt_tokens` des Fehlers stellen. Wenn die Divergenz bleibt, liegt
  sie außerhalb des Zählers (per-Turn-Injektion, Static, Sub-Loop) → zurück in den
  [Context-Overflow-Zyklus](#context-overflow--contentprovider-bug-zyklus-paul-order-2026-10-14--nächster-zyklus).
- ⏳ **Klassen-Zyklus `compact` ↔ `tool` über die `COMPACT_HINT`-Konstante (2026-10-19):** die neue
  Komponente sitzt in `compact` (richtig) und liest `ToolService.COMPACT_HINT`; `ContextTrimComponent`
  tat das schon vorher. Bewusst nicht mit-gefixt (D6, sonst hätte ein Lösch-Inkrement eine
  Konstante verschoben) — Cleanup-Kandidat: Konstante nach `CompactConstants`, dann ist
  `compact`→`tool` nur noch `tool.model` (Datenmodell). Kein Verhaltensrisiko, reine Struktur.

## Bug-Fix-Backlog

- ❓ **Fehlerklassen-Tabelle — R-CC-7 + ApiRetry, eine Tabelle zwei Verbraucher:** transient →
  Retry; `exceed_context_size`/Invalid-Request → sofort ehrlich failen. ApiRetry-Befunde
  (Evidence 5×, derselbe Shape: Call bricht statt sichtbarem Retry): Connect-Exceptions/SSE-Reset
  ohne Retry (2026-09-10), „AI call canceled while waiting to retry" — Retry-Thread stirbt im
  Backoff (**erneut 2026-09-24 doppelt live**), `IOException: header parser received no bytes`
  (Null-Byte als Cancel klassifiziert?), llama.cpp-Crash 3× buildWithDev-Abbruch (2026-09-20) —
  **Pauls Idee:** mindestens 1 Retry nach ~10s auch bei Connect-Level-Failures. R-CC-7-Seite:
  Compact-Fehler ans LLM + onProblem, Retry 1× nach 20s nur transient
  ([compact.md](compact.md), Evidenz header-state-leak.md Fall 1+2).
- ❓ **Header-State-Leak** ([header-state-leak.md](header-state-leak.md)): onProblem rendert den
  Header-State neu (🟢/Zähler/Working-Hint hängen nach Fehlerpfaden); IST-Messung vor der
  SOLL-Härtung offen (letzter gültiger Wert vs. aktiv falsch gesetzt).
- ❓ **Live-Status im Retry-Backoff-Fenster (2026-09-10):** `StreamingBridge.onError` versteckt die
  Statuszeile → 10s…5min Funkstille (User liest „hängt"); SOLL-Idee „retrying in Xs". Verwandt:
  Header-State-Leak.
- ⏳ `ShellTool.confirmationProvider` non-volatile — pre-existing, harmlos, 1-Wort-Fix bei
  nächster Berührung.

## ❓ Offene Design-Fragen (Paul)

- **Per-Agent Provider Override (2026-09-28):** nur der Base besitzt einen Provider
  (`EffectiveConnection.java:13`); Custom-Agent mit URL-Override läuft gegen Base-API-Semantik
  (bricht bei inkompatiblen APIs). Eigener Zyklus beschlossen, nach ModelConfigWidget. Offen bei
  Planung: Advanced-Feld, Frontmatter-Key `provider`, oder beides. Kontext:
  [model-config-widget.md](model-config-widget.md) R-MCW-5.
- **test_project-Fixture SOLL vs. IST (2026-09-27):** [test-setup.md](test-setup.md) sagt minimal,
  IST enthält u. a. `pom.xml`/`Dockerfile` (Herkunft unklar, kein Test-Output). Da-Mek-Empfehlung:
  behalten + Doc-SOLL anpassen, README-Zeile ergänzen (Zweck + `peon.test.project`-Override).
- **Gelber Compact-Indikator 🟡 (2026-09-22):** zweiter Anzeige-Zustand `compacting` +
  🟡-Präfix in `AiAgentStatusWidget.text()`; Mini-Increment. Kontext: compact-lock.md.
- **buildWithDev-Compact (2026-09-03):** Compact statt Reset bei nennenswertem Kontext (~50 %),
  nur bei neuem Plan. Eigene kleine Story, „da brauchen wir kein Test".
- **Tool-Time-Disclosure Restkandidaten (2026-09-22):** webFetch-Cache-Frische,
  memoryAdd/Replace-Datum, `JavaDebugTool.continue`, searchAgent/compactSession/lint — je
  Mini-Story; Read/Grep/Write bleibt ohne Zeitinfo (stateless, Rauschen).
- **Docs-Linter ☠️-Status (2026-09-29):** DocParser kennt nur ✅/❌/🚧 (`DocParser.java:209-216`)
  — ein `####`-UC-Heading mit ☠️ wirft STATUS_FEHLT (Fall: default-inheritance.md UC-DEF-4/5/7
  mussten zu Fließtext degradiert werden). Kandidat: ☠️ als Info-Status.
- **BDD-Test Compact-Hint-Fallback:** Regel gebaut (`29a341b`), Paul: „wann anders" — Backlog.

## ⏳ Mini-Cleanups / geparkt

- **TrimService-Story — 🔒 gelöst 2026-10-17 (OutputTrim Slice 1):** gemeinsame Klasse gebaut
  (`OutputTrim`, core/shared) — Shell + Grep migriert, Details/Commits:
  [output-trim-service.md](output-trim-service.md). Rest-Backlog unten.
- ❓ **Read-Tools-Rollout auf `OutputTrim` (2026-10-17):** `eclipseReadFile`/`diskReadFile` clampt
  Bereiche, aber nicht über die Trim-Komponente (Head/Tail-Disclosure) — Migration = eigenes
  Inkrement, kein Eile (Paul). Auch Kandidat: `LogExcerpt`/webFetch-Paging auf `OutputTrim`
  vereinheitlichen (vgl. TrimService-Bullet oben).
- ❓ **Pauls ShellTool-WIP uncommitted (2026-10-17, im Tree):** Parameter-Rename
  `workingDirectory` → `workDir` + `@P`-Text („disk path, default current project") + Error-Message
  mit Default-Ausgabe — Tool-Schema-Change über alle `shellRunCommand`-Aufrufer (Clean Break).
  Wir haben es NICHT committed/gebaut (2. Positional-Param-Rename in der Story). Paul committet
  die WIP-Änderungen selbst nach kurzem Review (2026-10-17); SOLL klären: Rename bestätigen +
  shell-tool.md R3/Docs nachziehen, oder zurückrollen.

- **Think-BDD-Lücken (a)–(g) (2026-09-27, Da-Dok-Provider-Think-Audit):** (a) Gemini
  buildModel-Thinking-Branch, (b) OpenAI-Off-Varianten `FALSE`/`No`/` Off `, (c)
  GITHUB_COPILOT/GITHUB_MODELS Request-Params, (d) Anthropic konkret-Level + unknown string,
  (e) LM Studio extra_body-Override, (f) OPEN_AI_OFFICIAL reasoningSummary=DETAILED, (g)
  custom-agent×OpenAI nur Signatur-Smoke. Plus: `ThinkModelMapping.Entry.off` geparst aber
  ungenutzt; `AiAgent.isThinkEnabled()` (deprecated Default) Sterben-Kandidat.
- ⏳ **slf4j-simple — 🔒 gelöst 2026-10-17 (`8e6bcb54`):** Jar + Dependency aus Plugin-pom/
  MANIFEST/build.properties entfernt — gebautes Bundle enthält exakt EINEN Provider
  (`EclipseSlf4jProvider`), Plugin-Suite 352/0 ohne SLF4J-Warnung. **→ resolved-points.md.**
  ⏳ Verbleibender Design-Punkt: slf4j-api ist **nicht OSGi-resolved**, sondern gebündelt
  (`lib/slf4j-api.jar`, aktuell 2.0.19 → 2.0.18 beim nächsten clean — Patch-Drift harmlos);
  Pauls Annahme eines `Require-Bundle`-Ranges `[2.0.0,3.0.0[` existiert im IST nicht. Falls
  Paul slf4j-api künftig von der Platform resolved will (Bundle in Target + Require-Bundle +
  lib-Jar weg): eigene Story, kein Bug-Anlass.
- **Jackson 2 → 3 beobachten (2026-09-10):** Migration erst bei voller Entfernbarkeit
  (openai-java pinnt Jackson 2; 5 eigene Dateien; Error-Path → ApiRetry-Klassifikation prüfen).
  Revisit-Trigger: langchain4j 1.21+ / openai-Jackson-3. Dann eigene Story mit ADR.
- **Edit-Tools Naming + gemeinsame Doku (geparkt 2026-09-05):** Rename auf „Edit"
  (`eclipseUpdateOpenFile`→`eclipseEditOpenFile`, `planUpdate`→`planEdit`), gemeinsame
  `edit-tools.md`, `planUpdate` Count-Disclosure nachziehen. Line-Ending-Normalisierung:
  E2E-Spec `file-edit-tools.txt`. *(Der frühere Doc-Drift „Errors if 0 or >1 matches" in
  eclipse-workspace-write-file-tool.md ist korrigiert.)*
- **Docs-SOLL-Hygiene-Sweep (2026-09-12):** Docs = reines SOLL, keine „war:"-Narrativen;
  umgesetzt für advanced-configuration + model-loading, Rest als eigener Aufwasch (Scope von
  Paul bestätigen lassen).
- **`eclipseBuildProject` build.properties-Warnung (seit #136):** „class folder 'resources/'
  not associated to any output library entry" bei grüner Kompilation; pre-existing (verifiziert
  2026-09-21), Mini-Fix-Kandidat.
- **`DiskFileWriteTool` 3× identischer File-Guard (Da-Mek-Kandidat 2026-10-15, geparkt):**
  `resolve()` + `!Files.isRegularFile` + IAE wortgleich dreimal (`diskReplaceLines`:124,
  `diskEditFile`:147, `diskInsertLines`:237) → `requireRegularFile(path, hint)`; exakte Meldungen
  1:1 (inkl. `Use searchDiskFiles`-Hint), `exists`-Varianten bewusst draußen. Core-Change →
  Jar-Ritual, nächster Core-Kontakt.
- **SwtUtil.setExcluded-Extract — 🔒 gelöst 2026-10-15 (`3dfa784d`, Hyg2):** → resolved-points.md.
- **Toter Code: `eclipseFindImplementations` — 🔒 gelöst 2026-10-15 (`b9a94195`, Hyg1):** → resolved-points.md.

## Sonstiges

- ⏳ **User-Smoke Header-Compact-Buttons:** Optik ✅; ausstehend: Re-Compact-Noop,
  Disabled-States, Tooltip — nach Merge.
- ⏳ **AGENTS.md-Ambiguität „Homepage-Doku: Dev-Inkrement vs. PO-Docs-Zyklus"** → Merge-Gate
  präzisieren (Da-Dok-Empfehlung, Plan §12).

## Night-Cycle 4 (2026-10-15) — Nachbefunde

- ⏳ **Cap-Test-Rest-Risiko (Da-Dok CONCERNS 2):** der Cap-Test hängt am Build-Erfolg aller 3
  Seed-CUs — liefern nur 2 von 3 CUs Marker (80 < 100), rotet er erneut in degradierten
  Recovery-States. Eskalationspfad = R6-Fallback (b) (direktes Marker-Seeding +
  `eclipseReadProjectProblems`, gleicher Cap-Pfad). Nur melden, wenn er erneut rotet — kein
  präventiver Umbau.
- ⏳ **Geteilte disk-Tool-Instanzen: mutable `AbstractTool.request` (Da-Dok CONCERNS 5):** Jons
  po-Registry teilt dieselben disk-Tool-Instanzen mit dem shared-Service; `request` wird pro Call
  gesetzt und im `finally` genullt (`SmartToolExecutor.run:37-58`). Single-Active-Agent: kein
  konkretes Race; bei Pauls **Async-Interface** (parallele Turns) würde ein fremder Request Jons
  Write-Validator überschreiben/leeren → unvalidierter Write (R3 umgangen). **Merge-Check-Item**
  für den Async-Zweig: Request-Reichweite (Parameter/ThreadLocal statt Instance-Feld) **oder** dünne
  pro-Registry-Wrapper-Instanz, die dasselbe Backing teilt, aber Request-State lokal hält —
  einstimmige Empfehlung aus dem externen Review (2026-10-16): nicht auf später schieben, sondern
  einen der beiden Wege VOR dem Async-Bau festlegen, da Jons `WriteValidator.DOCS` am geteilten
  Tool hängt.

## Externes Review agent-tool-config (2026-10-16) — geparkte Punkte

- ❓ **`PeonAiService.current()` statischer Last-Constructed-Handle:** Production-Call-Sites
  `AiConfigPreferenceView:177,180` + `AgentModelConfigSection:144,155`; Risiko (Javadoc benannt):
  bei mehreren Services zeigt das Preview u. U. die MCP-Tool-Menge eines anderen Views. Umbau-Option
  (externes Review): Service via IEclipseContext/OSGi-Service/`Supplier<PeonAiService>` in die
  Preference-Pages injizieren statt globaler Handle. Aufwand: 4 Call-Sites + Test-Fixture-Umbau
  (`clearCurrent()` in 5 Methoden). Jon: reeller Nutzen erst bei mehreren Views — es gibt heute
  genau einen aktiven Chat-View. Entscheidung offen (Paul).
- ⏳ **`ToolSelection` sealed interface (externes Review, Vorschlag):** `enables(List.of(), x)=false`
  vs. `nameFilter("")=alle` ist eine implizite Doppelbedeutung von „leer" (absent wird zwar im
  `parseAllowlist` zu null normalisiert, aber der Typ trägt die Unterscheidung nicht). Sealed
  `All`/`Filtered(allow, deny)` würde Sonderregel „nur Negationen" in eine testbare Resolve-Funktion
  ziehen. Gestaltungspunkt, kein Defekt — Revisit, falls ToolPolicy erneut angefasst wird.

## Codegraph-Bau (2026-10-17, autonome Entscheide Jon — Rückversicherung Paul)

- ⏳ **Reserved Name `codegraph` UNBEDINGT reserviert (Q1, Jon-Entscheid a):** der Name `codegraph`
  ist im User-MCP-Pfad immer reserviert (Filter in `McpConnectionService.connectServers`), auch
  wenn das Built-in inaktiv ist — manuelle Einträge werden gefiltert (Log-Zeile), Randwirkung: ein
  bestehender reiner Workaround-Eintrag funktioniert NICHT mehr, sobald diese Version läuft
  (Workaround stirbt komplett, unabhängig vom Built-in-State). Begründung: Clean Break, Name
  gehört dem Built-in (Homepage: „Eintrag löschen"), keine State-Kopplung User-Pfad ↔ Built-in.
  **Paul kann beim Review umwerfen** → dann State-abhängiger Filter (mehr Code, Kopplung).
- ⏳ **Icon-Visuals (Q2, Jon-Entscheid a):** ON = `codegraph.svg`, OFF = gleiches Icon
  disabled/gedimmt, ERROR = Platform-Icon `ImageUtil.STOP`; `codegraph_dark.svg` erzeugt Da Mek
  nach Hammer-Muster (kein neues Design-Asset von Paul nötig — Gegencheck nach Rückkehr möglich).


## Codegraph-Nachtzyklus 5 (2026-10-17) — geparkt

- ❓ **S3 (🗜 in PoDelegateTool/AgentsTool) nur halb machbar:** der AgentsTool-Teil liegt in
  Pauls untracked WIP (`agent/tools/` — TABU, 🚧 Async-Review offen). PoDelegateTool-Teil
  (🗜 auf den compact*-Delegates) wäre machbar, halber Rollout = Inkonsistenz zwischen beiden
  Delegate-Tools. Paul entscheidet: PoDelegate-Teil vorab bauen, oder S3 komplett nach
  AgentsTool-Merge. Kontext: [icons.md](icons.md).


## Codegraph-Toolset-Erweiterung (2026-10-17, Paul-Frage — ❓ Entscheidung ausstehend)

- ❓ **Mehr Codegraph-Tools registrieren (R-CG-6-Änderung, 2026-10-17):** Paul fragte nach dem
  Toolset (`node/search/callers/callees/impact/files/status` — per Default NICHT verbunden, nur
  `codegraph_explore`; Re-Enable serverseitig via `CODEGRAPH_MCP_TOOLS`). Jon-Befund: `explore`
  deckt callers/callees/impact/Source in einem Call ab; Spezialtools lohnen v. a. für billige
  Location-Lookups. **Jon-Empfehlung: `CODEGRAPH_MCP_TOOLS=explore,search,files`** (3 Schemas
  statt 7 — jedes Extra-Schema kostet Context in allen Agenten pro Turn), umgesetzt als kleiner
  Slice in `CodegraphServerFactory` (Env in der Config) + R-CG-6/R-CG-10-Text-Anpassung.
  **Nicht gebaut** — R-CG-6 ist Pauls SOLL (explore-only); Änderung erst nach Paul-Rückkehr.
  Kein rekursiver USER_HOME-Binary-Scan (Probe deckt `which` ab: `~/.local/bin` Suchort #1 +
  PATH-Scan, E2E-Beleg auf Pauls Maschine — Detail in codegraph.md Fakten).


## Codegraph-Smoke (2026-10-18, Paul) — Befunde

- 🔒 **Zwei `serve --mcp`-Prozesse — gelöst (2026-10-18, `fe583895`):** war ein **Bug**, kein Design:
  jede Registry baute ihren eigenen `McpService` (je Server ein Prozess). Fix nach
  [ADR-0069](adr/0069-shared-mcp-connection-per-server.md): der Connection-Service **besitzt** den
  `McpService` (`connect()` genau einmal, einziger Schließer), alle Registries adoptieren ihn
  (`ToolService.adoptMcpGroup`); `disconnectMcp` detacht nur noch. Gilt für Codegraph **und**
  User-MCP; der Leer-Pfad (Server-Liste wird leer) schließt ebenfalls, Status sagt jetzt ehrlich
  „MCP disconnected (no servers configured)". Gates: Core 1175/0, OSGi 386/0. Offen bleibt nur der
  `McpService.connect()`-Rollback im Core als testlos (siehe unten).
- ❓ **`codegraph` fehlt in Pauls Tool-Auswahl** (Agent-Tool-Config), obwohl verbunden
  (`Running MCP: codegraph_explore`). Kein struktureller MCP-Ausschluss (`ToolService.activeNames:111`,
  `getToolStatus:465-467` enthalten MCP) — situativ: die Preview (`PeonAiService.java:568`) zeigt die
  MCP-Menge des ToolService des jeweiligen Slot-Agenten × CSV-Filter; Codegraph ist nur auf
  shared + po verbunden. Offen: **welcher Agent/Slot** in Pauls UI, und ob die Soll-Aussage aus
  R-CG-6 („explizite CSV braucht ein `codegraph`-Token") dort überhaupt wählbar ist.
- ❓ **`McpService.connect()`-Rollback ist testlos** (Da-Dok-Review 2026-10-18): schlägt bei User-MCP
  Server #2 fehl, schließt der Owner den neuen Service nicht — die Leak-Freiheit hängt allein an
  `McpService.connect()` (`:87-93`, all-or-nothing: alle Clients werden im catch geschlossen). Kein
  Test pinnt das, und der Seam-Test kann es nicht (Fake überschreibt `connect()`). Testbar würde es
  erst mit einem fabrizierbaren `McpClient`/Transport — dann ein Core-Test „2. Server scheitert →
  Client 1 geschlossen, kein Handle offen". Aufwand vs. Wert: niedrig, aber bisher kein Fehlerfall
  beobachtet. Prio: Backlog.
- 🔒 **Chat-Feedback bei Connect/Disconnect — gelöst über das ContextItem (2026-10-18):** kein neuer
  Emit-Pfad; `CodegraphContextItem.label()` (= `Codegraph connected|off|failed — <Projekt>`) fährt
  den bestehenden `📋 Loading …`-Pfad (`AbstractAgent.renderTurnContext`). Siehe R-CG-7/R-CG-12.
  Nebenbefund: die alte `UserContext.codegraphLine` + `CodegraphConnectionService.projectInfoLine()`
  waren der zweite, verstreute Pfad — ersatzlos entfallen; die Tests dazu sind durch
  `CodegraphContextItemTest` + das Wiring in `AgentContextComponentTest:153/:200` abgelöst.
- 🔒 **Protokoll-Fallback ist kein Bug:** `Server does not support modern protocol, falling back to
  legacy initialize` + `protocolVersion 2024-11-05` = normaler Auto-Detect-Pfad
  ([ADR-0045](adr/0045-mcp-protocol-version-auto-detect.md), `DefaultMcpClient.java:291`,
  log.debug); codegraph 1.6.0 spricht nur legacy.
- 🔒 **Icon (2026-10-18 erledigt):** 32 ohne `viewBox` → auf 24×24 + `viewBox="0 0 32 32"`
  normalisiert; `codegraph_off.svg`/`codegraph_off_dark.svg` ergänzt (gedimmte Farbe statt
  `setEnabled(false)` — SWT graut Text, nicht das Image); R-CG-8 präzisiert.
