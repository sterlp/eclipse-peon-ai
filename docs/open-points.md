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

- **TrimService-Story (Paul 2026-09-29, nächster „Architecture & Bug Sprint"):** Head/Tail/Trim
  wird quer repliziert (ShellTool-Tail, Compact-Stufenkürzung, Tool-Output-Caps, LogExcerpt,
  webFetch-Paging) → gemeinsame Klasse `TrimmedResult`: eine Stelle kürzt + disclosed.
  Konvention: Zeilen zählen/zusammensetzen im Trim-Pfad mit **literal `\n`** statt
  `lineSeparator()` (plattformunabhängig), dokumentiert in `TrimmedResult.toString`.
  Grundlage: `ShellTool.java` · Verwandtes: `LogExcerpt`, `TextFileTypes`, Input-Budget,
  Tool-Output-Disclosure.
- **Think-BDD-Lücken (a)–(g) (2026-09-27, Da-Dok-Provider-Think-Audit):** (a) Gemini
  buildModel-Thinking-Branch, (b) OpenAI-Off-Varianten `FALSE`/`No`/` Off `, (c)
  GITHUB_COPILOT/GITHUB_MODELS Request-Params, (d) Anthropic konkret-Level + unknown string,
  (e) LM Studio extra_body-Override, (f) OPEN_AI_OFFICIAL reasoningSummary=DETAILED, (g)
  custom-agent×OpenAI nur Signatur-Smoke. Plus: `ThinkModelMapping.Entry.off` geparst aber
  ungenutzt; `AiAgent.isThinkEnabled()` (deprecated Default) Sterben-Kandidat.
- **slf4j-simple.jar wird mitgebündelt (2026-09-25):** trotz eigenem `EclipseSlf4jProvider`
  (`MANIFEST.MF:96`, `build.properties:65`, Plugin-`pom.xml:23-27`) — Jar + Dependency bei
  nächster Berührung raus, Build + Plugin-Lauf testen.
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
- **SwtUtil.setExcluded-Extract (2026-09-29, Da-Dok G2):** SWT-Idiom `exclude`+`setVisible` 3×
  dupliziert (2× R-DEF-9…11-Widgets + `McpPreferenceView`).
- **Dead Code: `ModelConfigWidget` 4-Arg-Konstruktor (2026-09-29):** `labelStyle` + SWT.END-Zweig
  caller-less seit R-DEF-10 — Cleanup beim nächsten Widget-Berührungs-Kontakt.
- **Toter Code: `eclipseFindImplementations` (2026-09-30):** `EclipseCodeNavigationTool.java:287-331`
  liegt in einem `/* … */`-Kommentarblock — Entfernung als eigene Dead-Code-Story.

## Sonstiges

- ⏳ **Homepage `peon-po.md` listet das Team ohne Da Dok** — Korrektur im nächsten
  Homepage-Kontakt.
- ⏳ **User-Smoke Header-Compact-Buttons:** Optik ✅; ausstehend: Re-Compact-Noop,
  Disabled-States, Tooltip — nach Merge.
- ⏳ **AGENTS.md-Ambiguität „Homepage-Doku: Dev-Inkrement vs. PO-Docs-Zyklus"** → Merge-Gate
  präzisieren (Da-Dok-Empfehlung, Plan §12).
