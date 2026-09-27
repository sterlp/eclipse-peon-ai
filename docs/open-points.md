# Open Points

Status je Punkt: ❓ offen · ⏳ selbst entschieden (Rückversicherung mit User steht aus) · 🔒 geklärt.
Geklärte Punkte ohne eigenes Feature-Doc: [resolved-points.md](resolved-points.md).

## ❓ test_project-Fixture: „minimal" SOLL vs. IST-Inhalt (2026-09-27, searchAgent-Freshness-Check)

`docs/test-setup.md:20-21` sagt SOLL = minimal (`.project`, `.classpath`, `src/`); liegt im
Fixture inzwischen: `pom.xml`, `Dockerfile`, `copyDirSrc/`, `data/`, `docs/`, `sub/`, `tmp/`,
`bin/`, `target/`, `peon-plan/`, `.agents/`. Teils legitimer Test-Output (Tests schreiben ins
Fixture, `test-setup.md:29-30`), aber `pom.xml`/`Dockerfile` sind kein Test-Output — Herkunft
unklar. README L3 „nicht als echtes Projekt benutzen" ist irreführend (das Fixture ist *bewusst*
echtes JDT-Projekt, `.project`-Nature ist der Punkt). Fragen an Paul: (a) `pom.xml`/`Dockerfile`
behalten (dann Doc-SOLL anpassen) oder aufräumen? (b) README-Zeile ergänzen: Zweck + Property
`peon.test.project` überschreibt den Pfad (`test-setup.md:52-53`)?

## ⏳ Standalone-Peon-Review behält memory*/askUser (2026-09-25, Jon-Entscheid aus dem Build)

Da-Dok-Stop-And-Ask: der Standalone-Peon-Review sieht WorkspaceMemoryTool (+ askUser im UI) —
die RAM-Sklaven-Stripping-Regel (noPrivilegedTools) greift nur für Sklaven. UC-TF-2
([agent-tool-filter.md](agent-tool-filter.md)) entsprechend auf den RAM-Sklaven verengt: Standalone
= wie alle Standalone-Agenten (Peon-Plan/-Dev, Custom), kein neuer Mechanismus. **Rückversicherung
Paul steht aus** — falls er Standalone-Review auch gestrippt haben will: eigener Mechanismus,
dann neue Story.

## ⏳ slf4j-simple.jar wird noch mitgebündelt (Paul-Notiz, 2026-09-25)

Verifiziert: `lib/slf4j-simple.jar` liegt weiter im Bundle (`MANIFEST.MF:96` Bundle-ClassPath,
`build.properties:65`, Plugin-`pom.xml:23-27` Dependency `${slf4j-simple.version} 2.0.19`) — trotz
eigenem `EclipseSlf4jProvider` (via `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`,
`Bundle-ClassPath: .` zuerst → unser Provider gewinnt den ServiceLoader-Scan). Vermutlich Rest aus
dem Zeit vor dem Eclipse-Provider. Bei nächster Berührung: Jar + Dependency raus, Build + Plugin-Lauf
testen (Test-Scope im core nutzt ohnehin logback statt slf4j-simple).

## Bug-Fix-Zyklus-Backlog (2026-09-24, priorisiert)

1. **R-CC-7 — Compact-Fehler sichtbar + begrenzter Retry** ([compact.md](compact.md)):
   Fehler ans LLM („compact failed" + Ursache) + onProblem; Retry 1× nach 20s nur transient,
   deterministische Fehler sofort ehrlich. Zusammen mit der ApiRetry-non-retryable-Klassifikation
   (eine Fehlerklassen-Tabelle, zwei Verbraucher). Evidenz: header-state-leak.md Fall 1+2.
2. **Header-State-Leak** ([header-state-leak.md](header-state-leak.md)): onProblem rendert den
   Header-State neu (🟢/Zähler/Working-Hint hängen nach Fehlerpfaden); IST-Messung vor der
   SOLL-Härtung offen (letzter gültiger Wert vs. aktiv falsch gesetzt).
3. **ApiRetry** (❓ eigener Abschnitt unten, Evidence 5×): non-retryable-Klassifikation +
   Mindest-Retry bei Connect-Level-Failures — in die gleiche Fehlerklassen-Tabelle.
4. ⏳ `ShellTool.confirmationProvider` non-volatile — pre-existing, harmlos, 1-Wort-Fix bei
   nächster Berührung (Da-Dok-Hinweis R-TC-Review).

## ❓ Context-Pollution-Quellen (2026-09-24, Evidenz aus dem Compact-Thema)

337461 Provider-Tokens vs. 114512 Compact-Schätzung — Pollution-Kandidaten im Jon/Agent-Mode
(IST-Analyse Da Mek): (a) `eclipseReadFile`/`diskReadFile` ohne Cap (`FileLines` 0/0 = ganze
Datei), (b) `docs/index.md` + `docs/memory.md` + AGENTS.md **pro Turn** in Jons System-Context
(`AgentContextComponent.java:130-140`) — index.md wächst ungebremst, (c) Workspace-Memory-Snapshot
ohne Cap. Frage an Paul: Caps an der Quelle (Read-Größenlimit, Kontext-Items begrenzen) oder
bewusst so lassen, weil der Compact-Input jetzt budgetiert ([compact.md](compact.md))? Verwandt:
Workspace-Memory-Vollkopien (unten).


## ❓ Workspace-Memory-Snapshot: Vollkopie je `memoryAdd` (2026-09-23)

Snapshot-Key ist der entries-Hash (ADR-0032) — jede Mutation erzeugt eine frische Vollkopie
aller Einträge, bis zum Compact. By design, aber die Kosten skalieren schlecht. Frage an Paul:
eigener Design-Punkt? (inkrementeller Snapshot / Dedup je Eintrag / Compact-frequenter).
Verwandt: [compact.md](compact.md) „Offen".

## ❓ Tool-Time-Disclosure Restkandidaten (2026-09-22, Option B — Paul-Scope)

`webFetchAsMarkdown` (Cache-Frische), `memoryAdd/Replace` (Datum-Bestätigung), `JavaDebugTool.continue`
(Dauer), searchAgent/compactSession/lint (Konsistenz) — je eigene Mini-Story. Read/Grep/Write-Familie
bleibt ohne Zeitinfo (stateless, Rauschen).

## ❓ Gelber Compact-Indikator (🟡) im Roster (2026-09-22, Paul)

„Besser als 🟢, aber grün ist auch vollkommen okay" — bewusst nicht gebaut. Umsetzung: zweiter
Anzeige-Zustand `compacting` + 🟡-Präfix in `AiAgentStatusWidget.text()`. Wiederaufnahme = Mini-Increment.
Kontext: [compact-lock.md](compact-lock.md).

## ❓ ApiRetry: Cancellation-/Retry-Klassifikation (Priorität hoch, Evidence 4×)

Befund-Klassen (derselbe Shape: Call bricht statt sichtbarem Retry):
1. `HttpTimeoutException`/`ConnectException` ohne Retry (2026-09-10).
2. SSE `Connection reset` mitten im Stream (2026-09-10).
3. „AI call canceled while waiting to retry" — Retry-Thread stirbt im Backoff (2026-09-02/11;
   **erneut 2026-09-24 doppelt live**: Da-Dok-Review + Da-Thinka-Plan, siehe
   [header-state-leak.md](header-state-leak.md)).
4. `IOException: header parser received no bytes` — Verdacht: Null-Byte-IOException als Cancel
   klassifiziert (2026-09-06).
5. **Neu 2026-09-20:** llama.cpp-Crash → 3× buildWithDev-Abbruch (`ConnectException`/
   `ClosedChannelException`/no-bytes). **Pauls Idee:** mindestens 1 Retry nach ~10s auch bei
   Connect-Level-Failures (Crash+Restart dauert meist Sekunden).

→ Lösungsweg: gemeinsame **Fehlerklassen-Tabelle** mit R-CC-7 (transient → Retry;
`exceed_context_size`/Invalid-Request → sofort ehrlich failen), dann Klassifikation
(Cancel-Misclassification?) in ApiRetry.

## ❓ Live-Status im Retry-Backoff-Fenster (2026-09-10)

`StreamingBridge.onError` versteckt die Statuszeile → 10s…5min Funkstille (User liest „hängt").
SOLL-Idee: „retrying in Xs" im Backoff-Fenster. Mini-Story, verwandt mit header-state-leak.

## ⏳ Jackson 2 → 3: beobachten (2026-09-10, User)

Migration erst bei voller Entfernbarkeit (openai-java pinnt Jackson 2; 5 eigene Dateien; Error-Path
ändert sich → ApiRetry-Klassifikation prüfen). Revisit-Trigger: langchain4j 1.21+/openai-Jackson-3.
Dann eigene Story mit ADR.

## ❓ buildWithDev sollte Da Mek vorher compacten (2026-09-03)

Compact statt Reset bei nennenswertem Kontext, Schwelle ~50 %, nur bei neuem Plan (nicht bei
Delta/Nacharbeit). User: „da brauchen wir kein Test". Eigene kleine Story.

## ⏳ Unbegrenzte Query-Caches (Rückversicherung offen)

`SearchQuery.CACHE` + `RegexUtils.GLOB_CACHE` unbegrenzt — belassen (Einträge winzig); bei Bedarf
LRU 500.

## ⏳ Streaming-Timing-Präzisierungen (2026-09-05, streaming-display.md R19)

Total-Timer läuft durch die Bridge-Lebenszeit (Anzeige nutzt `startedAt`); TOOL-Chunk zählt
`partialArguments()`-Delta.

## ⏳ Edit-Tools: Naming-Uniformität + gemeinsame Doku (geparkt 2026-09-05)

Rename auf „Edit" (`eclipseUpdateOpenFile`→`eclipseEditOpenFile`, `planUpdate`→`planEdit`);
gemeinsame `edit-tools.md`; `planUpdate` ohne Count-Disclosure (nachziehen); **Konflikt offen:**
eclipse-workspace-write-file-tool.md sagt noch „Errors if 0 or >1 matches" — widerspricht
Bug-Hunt #1 (Replace-All + Count), muss mitfixt werden. Line-Ending-Normalisierung: E2E-Spec
`file-edit-tools.txt`.

## ❓ Deferred Smoke-Test-Kosmetik (User: „Kosmetik ist mir erstmal egal")

Statuszeilen-Striche, Scrollverhalten Advanced Config.

## ⏳ Docs-SOLL-Hygiene-Sweep (2026-09-12)

Docs = reines SOLL, keine „war:"-Narrativen. Umgesetzt für advanced-configuration + model-loading;
Sweep über die übrigen Feature-Docs als eigener Aufwasch, Scope vom User bestätigen lassen.

## ⏳ Compressor-Dedup-Subtext-Edge / ThreadSafeMemory RAM-only (2026-09-13, Da-Dok Release-Scan)

Dedup kann Einzel-Nachricht als Teiltext unterschlagen (Compact ist lossy — akzeptiert);
nach Persist-IOException läuft die Session RAM-only weiter (präexistierendes Muster). Keine
Blocker; Revisit nur bei Beschwerden/Datenverlust-Meldungen.

## Compact ([compact.md](compact.md))

- ❓ Context-Noise zuerst raus (User-Idee, 2026-09-13): Context-Item-Messages vor dem Kürzen
  nehmen — „Light-Version" des Input-Budgets; die Input-Budget-Story (2026-09-24) deckt das
  Staging ab, Noise-Entfernung bleibt eigener Punkt.
- ⏳ **Input-Budget-Doc ausgegliedert** (2026-09-26, Jon): die sechs Lint-Befunde im Compact-Doc
  (6× `PRAEFIX_FREMD` für die Input-Budget-Regeln; die 6× `DOPPELT_DEFINIERT` waren durch das
  Löschen von compact-context-counter.md schon weg) mit einem **Doc-Split** gelöst statt
  Umnummerierung — [compact-input-budget.md](compact-input-budget.md) (Präfix `CIB`), IDs und
  ~35 Code-Kommentar-Referenzen unangetastet, `compact.md` bleibt Einstieg (Präfix `CC`).
  Rückversicherung Paul: Split okay, oder doch Umnummerierung unter `CC`?

## Neu (2026-09-14, story/po-compact-2026-09-13)

| Punkt | Status | Notiz |
|---|---|---|
| `homepage/src/setup/peon-po.md` listet das Team ohne Da Dok | ❓ offen | Korrektur im nächsten Homepage-Kontakt |
| User-Smoke Header-Compact-Buttons | ⏳ teils | Optik ✅; ausstehend: Re-Compact-Noop, Disabled-States, Tooltip — nach Merge |
| BDD-Test Compact-Hint-Fallback | ❓ Backlog | Regel gebaut (`29a341b`), Paul: „wann anders" |

## ⏳ R-SEL-4 Umsetzungsdetails (2026-09-16 — Rückversicherung steht aus)

Paul bestätigte „beides" (`IClassFile` + `IType`). Drei selbst abgeleitete Details (Plan §2.1/§6):
(1) jeder `setTextSelection` (auch leer) räumt den Typ — strikte Text/Typ-Alternation;
(2) Rename `clazz`/`setClassFile` → `javaType`/`setJavaType`; (3) Typ-Event berührt `currentProject`
nicht. Widerspruch = Verhalten zurückändern, Tests (UC-SEL-4) anpassen.

## ⏳ `eclipseBuildProject` Failure: build.properties-Warnung (seit #136)

„class folder 'resources/' not associated to any output library entry" bei grüner Kompilation.
Pre-existing (Da Mek 2026-09-21 verifiziert). Separater Mini-Fix-Kandidat.

## ⏳ CompactSessionTool kompaktiert agent.getMemory(), der Loop fährt req.getMemory() (Bug B, latent, 2026-09-27)

`CompactSessionTool.java:23-29` → `agent.compact(monitor)` (= `agent.getMemory()`), der Loop läuft
auf `req.getMemory()` (ToolService:200). Für Haupt-Agenten zufällig dasselbe Objekt
(`AbstractAgent.doCall` setzt `.memory(agent.memory)` — Konvention, keine Gewähr). Sobald ein
echter SearchAgent (agent != null + compactSession) mit auseinanderfallenden Objekten existiert:
Compact leert das **falsche** Memory → Summary landet unerreichbar, R-CC-4-Guard blockiert neue
Hints → Agent stuck. Optionen: (1) Tool kompaktiert `req.getMemory()` (berührt Compact-Ownership,
compact-architektur.md), oder (2) fail-fast im Loop (`agent != null && req.getMemory() !=
agent.getMemory()` → ehrlicher Fehler). Eigene kleine Story mit Doc-Anpassung, Revisit spätestens
beim echten Suchagenten.

## ⏳ compactSession trotz agent == null exponiert (Kleinigkeit, 2026-09-27)

Bei agent == null bleibt `compactSession` im Tool-Set sichtbar (Hint sagt „cannot be compacted",
das Tool wirft aber nur die ehrliche `IllegalStateException` beim Call). Konsistenter:
`toolSpecifications` lässt `compactSession` weg, wenn `agent == null` — spart Token und den
Fehlaufruf. Mit Bug-A-Fix zusammen behandelbar.

## ⏳ StreamingBridge/ApiRetry geteilt über toBuilder — parallele Nested-Calls racen (Da-Dok/Mek-Nebenbefund 2026-09-27, ADR-0058)

Der Nested-Request erbt den stateful `StreamingBridge` + `ApiRetry` des Parents — sequenziell
harmlos, zwei **parallele** Nested-Calls würden um latch/responseRef konkurrieren. Heute gibt es
keinen parallelen Sub-Call (alle Agent-Tools blockieren), Revisit mit
[async-agent-tools-proposal.md](async-agent-tools-proposal.md).

## ❓ Header: Jon-Context-Größe bleibt „0k estimate" (2026-09-27, Paul, Smoke)

IST (Da Dok): Roster-Refresh ist event-getrieben (`AIChatView:336-340` via `onTokenUsage`) — ein
Turn ohne echte Provider-Usage feuert das Event nicht → Jon bleibt auf „0k estimate" stehen, bis
ein anderes Event refreshed; andere Agenten (mit Provider-Usage) aktualisieren. SOLL-Frage an
Paul: Estimate-Pfad zusätzlich in den Roster-Refresh aufnehmen (Update auch ohne
Provider-Usage)? Verdacht-Update („rendern wir nicht alle Agenten") damit eingeschränkt: es
rendert alles, aber nur bei Event.

## ❓ Test-Fixture-Drift: `test_project` enthält pom.xml + Dockerfile (2026-09-27, Da-Mek-Smoke)

[test-setup.md](test-setup.md):20-21 sagt SOLL = „minimal" (.project, .classpath, src/), aber
`test_project` enthält auch `pom.xml` und `Dockerfile` (Test-Output bin/, target/, data/ sind laut
Doc legitim). README-Zeile „nicht als echtes Projekt benutzen" irreführend — es muss als echtes
Eclipse-Projekt ladbar sein, der Punkt ist nur, dass man nicht in ihm entwickelt. Entscheidung
offen: (a) Fixture behalten + Doc-SOLL anpassen, oder aufräumen; (b) README-Zeile korrigieren
(„Test-Fixture — ladbar, aber nicht hier developen; `peon.test.project` überschreibt den Pfad").
Da-Mek-Empfehlung: (a) behalten + Doc anpassen, (b) ja.

## ⏳ Eclipse-Installationsfehler User — Issue #142

Analyse korrigiert (2026-09-19): unser p2-Repo liefert asm 9.10.1 mit (includeAllDependencies).
Fix-Kandidaten + Follow-ups: [issue-142-asm-conflict.md](issue-142-asm-conflict.md). Nachzuholen
bei Pauls Zustimmung: Mindest-Eclipse-Version auf der Homepage nennen.
## ⏳ Think-BDD-Lücken (2026-09-27, Da-Dok-Provider-Think-Audit, Issue-#149-Zyklus)

Aus dem Audit registriert, bewusst nicht in Inc-4 (Scope-Dispositionen):
(a) Gemini buildModel-Thinking-Branch ungetestet (einziger Konsument der R-THINK-5-Ableitung);
(b) OpenAI-Familie-Off-Test fehlt `FALSE`/`No`/` Off `-Varianten; (c) GITHUB_COPILOT/GITHUB_MODELS
ohne Request-Param-Tests; (d) Anthropic konkret-Level-Pfad (Budget 8000) + unknown string ungetestet;
(e) LM Studio extra_body-reasoning-Override-Interaktion; (f) OPEN_AI_OFFICIAL reasoningSummary=DETAILED
nie asserted; (g) custom-agent×OpenAI-Familie nur Signatur-Smoke. Plus: `ThinkModelMapping.Entry.off`
wird geparst, aber ungenutzt (Inc-4-Scope war nur `resolveOff`+`find`); `AiAgent.isThinkEnabled()`
(deprecated Default) Kandidat fürs nächste Sterben-Inkrement.

## ⏳ User-Smoke Issue #149 (2026-09-27, Paul)

UI-Verifikation steht aus: Ollama-Dropdown (""/true/false) in der Advanced-Page, Basis-Checkbox
weg, `think:false` im Debug-Log bei `false` (der eigentliche Issue-Reporter-Fall), Custom-Agent
Legacy-Frontmatter (Write → nur `think`). Branch `story/issue-149-think` unveröffentlicht, Merge
nach Paul-Review.
