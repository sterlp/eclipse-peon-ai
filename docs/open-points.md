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
- **Context-Noise zuerst raus (2026-09-13):** Context-Item-Messages vor dem Kürzen nehmen —
  „Light-Version" des Input-Budgets; das Input-Budget deckt das Staging, Noise-Entfernung bleibt
  eigener Punkt.
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
  `edit-tools.md`, `planUpdate` Count-Disclosure nachziehen. **Konflikt mitfixen:**
  eclipse-workspace-write-file-tool.md sagt noch „Errors if 0 or >1 matches" (widert Replace-All
  + Count). Line-Ending-Normalisierung: E2E-Spec `file-edit-tools.txt`.
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

- ⏳ **Homepage `peon-po.md` listet das Team ohne Da Dok** — Korrektur im nächsten
  Homepage-Kontakt.
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
- ⏳ **EclipseGrepTool IFile-Disclosure (Da-Dok-CONCERN, 2026-10-16):** der IFile-Zweig (:68-69) wendet
  bei Datei-Target **keinen** Extension-Filter an, aber `grepComplete` (:82-83) / onTool (:88-89)
  disclosen die rohe Extension — dieselbe Text/Inhalt-Diskrepanz, die Inc-C-EXT auf der Disk-Seite
  gefixt hat (AGENTS „one behaviour, one implementation", Familien-Parität). Fix = C-EXT-Muster
  (effectiveExtension beim Datei-Target null), Details + Zeilen in peon-plan-§9 (Archiv). **UNBLOCKED
   2026-10-16:** Pauls WIP ist mit `af658e0f` gemerged (EclipseGrepTool inkl. 🔍-Präfix) — Umsetzung offen.


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
