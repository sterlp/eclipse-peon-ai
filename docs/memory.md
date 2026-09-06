# Session-Stand (2026-09-06, Abend)

## Zyklus `state-config-2026-09-06` — ABGESCHLOSSEN ✅ (wartet auf Merge)

- Branch `state-config-2026-09-06`: 8 Commits (inc-1..6 + cycle-close `35f96cb`), Working Tree sauber.
- **Gebaut:** inc-1 Housekeeping (Smoke-Fixes, Docs-Relocation, Prompt-Commit-Regeln in
  po-delegation/dev-build-loop/AGENTS.md) · inc-2 State-Umzug (R2) · inc-3 Migration (R3,
  StateMigration) · inc-4 E5 (absolute Pfade) · inc-5 Copy-Tool (R1–R4, FileUtils.copy) ·
  inc-6 Review-Fixes (Directory-Guard-Parität + Mutations-Killer `serviceStartMigratesLegacyState`).
- **Core 651/651 · Plugin 185/185.** Homepage ✅ (peon-memory.md, custom-agents.md).
- **Review ✅** (Da Thinka, 3-Seiten + Mutations-Check): 1 echte Lücke (Directory-Quelle im
  eclipseCopyFile — meine Plan-Lücke, R1-Parität) → inc-6 gefixt; IST-Abweichungen ok
  (Platform.getBundle statt FrameworkUtil — Target-Platform-Limit; ReloadConfigToolTest 4-arg).
- **Docs geflippt:** peon-config-directory R1/R2/R3 ✅ · file-copy-tool R1/R2 ✅ · E5 ✅ ·
  index.md aktualisiert. Plan archiviert (overview-done-2026-09-06-16-17.md).
- **Nächster User-Schritt: Merge → main.** Smoke-Test des Builds (Migration prüfen: vorhandene
  `~/.peon/state`-Dateien sollten beim ersten Start in den Metadata-State wandern).

## Neu in den Docs heute

- **peon-config-directory.md** (✅ R1+R2+R3) + **ADR-0041** · **configuration.md** (Landkarte,
  im index) · **file-copy-tool.md** (✅ R1–R4) · **eclipse-java-move-type-tool.md** (🚧 Idee,
  Backlog) · **builtin-agent-prompt-override.md** (🚧 Idee) · **open-points.md:** ❓ ApiRetry
  (Null-Byte-IOException evtl. als Cancel klassifiziert — Smoke-Test-Beobachtung).

## Offen für User

1. **Merge `state-config-2026-09-06` → main** (nach eigenem Smoke-Test).
2. **5 kleine ❓-Punkte** (open-points.md): UTF-8-Write · Glossar eager · PDE-Skip-Count ·
   Dropdown-Klassen löschen · buildWithDev-Compact — „nimm deine Empfehlungen" genügt.
3. **GO für Bug-Fix-Zyklus:** Triage #5–#16 + ApiRetry-Verdacht + Plugin-Hunt.
4. Ideen (nicht geplant): Jon×Scaffold-Side-Quest · Built-in Prompt Override ·
   eclipseJavaMoveType (🚧).

## In Flight: Batch-Zyklus `state-config-2026-09-06` — BUILD FERTIG, REVIEW STEHT AUS

- Branch `state-config-2026-09-06` von main; 5 Commits (12940de→50899e5): inc-1 Housekeeping
  (Smoke-Fixes + Docs-Relocation + Prompt-Commit-Regeln) · inc-2 State-Umzug (R2: historyFile
  = stateDir-injiziert, getStateLocation im Plugin, LlmConfig.stateDirectory() headless) ·
  inc-3 Migration (R3: StateMigration, skip-if-exists, log-only) · inc-4 E5 (absolute Pfade,
  AiFileUpdate bleibt relativ) · inc-5 Copy-Tool (R1–R4, FileUtils.copy geteilt, Rename unangetastet).
- **Core 651/651 · Plugin 183/183.** Homepage ✅ je Inkrement (peon-memory.md, custom-agents.md).
- **Da Mek wartet auf planImplemented** — erst nach meinem Review (Schritt 4).
- **NÄCHSTER SCHRITT:** Review durch Da Thinka (talkPlan): Plan↔Code, Docs↔Code, Docs↔Plan;
  Feature-Docs: peon-config-directory.md (R1+R2+R3), disk-file-write-tool.md (E5), file-copy-tool.md
  (R1–R4). Danach mein OK → planImplemented → Retro → Status-Flips in den Docs.
- Da Mek design-note inc-5: Validierung lebt in FileUtils.copy (eine Implementierung) — OK von
  mir, gegen Plan-Spez geprüft.

## Neu in den Docs (2026-09-06)

- **peon-config-directory.md** (R1+R2+R3 ❌) + **ADR-0041** (amendiert): .peon config-only,
  History → workspace Metadata-State, One-Shot-Migration. **Konfigurations-Landkarte:**
  configuration.md (im index verlinkt). **file-copy-tool.md** R1–R4 (❌→jetzt gebaut,
  Status-Flip steht aus). **eclipse-java-move-type-tool.md** (🚧 Idee). **open-points.md:**
  ❓ ApiRetry/Cancel-Verdacht (Null-Byte-IOException als Cancel klassifiziert?).
- Jon×Scaffold-Side-Quest vermerkt (peon-config-directory.md).
- 4 Docs waren versehentlich unter /org.sterl.llmpeon/docs/ gelandet — nach Root verschoben,
  falscher Baum gelöscht (leerer adr-Rest räumt Da Mek weg).

## Erledigt heute (fürs Protokoll)

- Smoke-Test: askUser ✅, Prompt-Satz ✅, refreshRoster-Fix (zweimal — ging beim Merge #131
  verloren, wieder eingesetzt) ✅, Sortierung AGENT_SECTIONS ✅, E5 entschieden (absolut).
- Header-Roster + Sortierung wanderten in inc-1-Commit `12940de` auf dem Branch.

## Offen für User

- Merge `state-config-2026-09-06` → main (nach Review + planImplemented).
- 5 kleine ❓-Punkte (open-points.md) — Empfehlungen stehen.
- GO für Triage-Zyklus (#5–#16 + ApiRetry-Verdacht) — nächster Zyklus.

## Smoke-Test 2.7.1 — Ergebnisse (2026-09-06)

1. **askUser R17 ✅** — Question-Widget bei Jon funktioniert (User: „hat geklappt").
2. **Prompt-Satz** (`po-delegation.txt`, autonomer Modus: kein askUser, Frage in den Chat,
   Turn-Ende außer Queued Message inkl. Alter-Warnung) — von User finalisiert ✅.
3. **Header-Token-Count nach Clear ✅ gefixt (uncommitted):** `headerBar.refreshRoster()` in
   `onClear()` (AIChatView.java:186); Compact war bereits korrekt. **Entscheidung User:** Fix
   reist mit dem Merge `jon-askuser` → main, kein eigener Commit.
4. **Sortierung Advanced-Config ✅ gefixt (uncommitted):** AGENT_SECTIONS PO → Plan → Dev →
   Search → Compact (AiAdvancedPreferenceView.java:35) + Test + Doc-Zeile.
5. **E5 ✅ entschieden:** Disk-Tool Success-Messages = absoluter Pfad (Regel ❌ in
   disk-file-write-tool.md) — Bau steht aus (Bug-Fix-Zyklus).

## Neu in den Docs (2026-09-06)

- **peon-config-directory.md** (❌ R1) + **ADR-0041**: `.peon` = shared-config-only; Agent-History
  raus (IST-Verstoß, Totalschaden bei 2. Instanz). Umsetzung: erst wenn bekannte Bugs weg.
- **builtin-agent-prompt-override.md** (🚧 Idee): Built-in-Prompts als `.peon`-Config, Body
  supersedes Default — nicht geplant.
- Jon×Scaffold-Delegation (Skill-Anpassung nach Zyklus) = Side Quest, vermerkt in
  peon-config-directory.md.

## Nächste Schritte

1. Smoke-Test weiterfahren (User).
2. Danach: Merge-Entscheidung `jon-askuser` → main (Fixes reisen mit, User-WIP in
   AskUserTool/StatusLineWidget/UserContext uncommitted!).
3. Bug-Fix-Zyklus: Triage #5–#12/#14–#16 (GO-Entscheidung offen) + E5-Fix + ggf. Rest vom
   Nacht-Bug-Hunt (brach an Netzwerkfehler ab, nichts geändert).
4. Danach: `.peon`-State-Umsetzung (peon-config-directory.md R2, Zielort offen).

## Nächste Session = Smoke-Test (User testet den neuen Build)

User testet 2.7.1 + Jon-AskUser. Erwartbar:
- **askUser (R17):** Question-Widget erscheint jetzt **auch bei Jon** — matched/queue-safe Antwort,
  Stop = Cancel-Error. Slaven (Da Thinka/Da Mek) + Search-Agent: **kein** askUser (R9, unverändert).
- **Streaming:** toc/s aus Token-Phase, **kein Spike am 1. Token eines Calls** (R22), "Started hh:mm".
- **Edit-Tools E2E:** Spec in `org.sterl.llmpeon.test/ai-e2e-test/file-edit-tools.txt`
  (disk + eclipse, write counter, editLine + replaceLine).
  ⚠️ **Item 3 (Line-Ending-Normalisierung) ist NICHT gebaut** → dort rot = erwartet, kein
  Regression — SOLL steht im geparkten Edit-Tools-Punkt (open-points.md).
- **planImplemented:** Kollision → Counter-Suffix (`…-1.md`), nie "already exists" (R-PI1).

**Nach dem Smoke-Test — User-Entscheidungen offen:**
1. **E5:** Disk-Tool Success-Message — (a) workingDir-Relative-Pfad lassen [PO-Empfehlung] oder
   (b) absoluter Pfad? (User hatte "BUG/KV-Cache" gesagt.)
2. **Merge:** `jon-askuser-2026-09-05` → main.
3. **Triage-Liste (#5–#12, #14–#16):** GO für nächsten Bug-Fix-Zyklus (Fixes sind gewählt,
   siehe unten) + Plugin-Hunt.
4. **5 kleine ❓-Punkte** (open-points.md): UTF-8-Write · Glossar eager · PDE-Skip-Count ·
   Smoke-Test-Kosmetik/Dropdown-Klassen · `buildWithDev`-Compact — User kann "nimm deine
   Empfehlungen" sagen (PO: fixen · (b) Turn-Context · ja · löschen · Compact ~50 % nur neuer Plan).

## In Flight: Jon-AskUser (Branch `jon-askuser-2026-09-05`, von `main`)

**R17 (po-agent-jon.md ✅):** Jon bekommt `AskUserTool` in seine `poToolService` (dieselbe
Instanz aus dem shared Service, `BuildPoAgentComponent:78`; headless = kein askUser).
R13-Klärung: "never blocks" = Slave-Fragen-Eskalation, nicht direkte User-Entscheidungen.
**Commits:** `3b90fad` Code + Tool-Beschreibung (plain text only (no Markdown); Cancel-Note) ·
`2c9ccb6` Docs. **Ground Truth:** Core **632/632** · OSGi **179/179** (2 neue Tests: Membership
+ Slaven-Filter). **PO-Acceptance ✅.**
**⚠️ User-WIP vermischt:** Code-Commit trug User-WIP-Zeilen in `AIChatView.java` mit
(refreshChat-Javadoc + deduped `refreshStatusLine`); `StatusLineWidget.java` + `UserContext.java`
= User-WIP mid-change, uncommitted.

## Shipped (in `main`, 2026-09-04/05)

- **Bug-Hunt** (Merge `db92e1b`): #1 applyEdit-Count, #2 ShellTool tail+filter, #3 CustomAgent
  null-Allowlist, #4 startedAt pro Turn, #9 Traversal (+follow-up `\`→`/`), #13
  showRealtimeAiResponse Default on.
- **Streaming-Timing** (Merge `86594a4`, gepusht): R18–R21 (Timer-Klasse, toc/s aus Token-Timer,
  "Started hh:mm", TOOL-Delta) + **R22** (kein toc/s am 1. Token eines Calls, pro Call) +
  **R-PI1** (planImplemented-Kollision → Counter-Suffix, core `ArchiveName.firstFreeName`).

## Geparkt

- **Edit-Tools** → [open-points.md](open-points.md): Rename auf "Edit", gemeinsame Doku (4 Tools),
  `planEdit`-Count, Eclipse-Doku-Konflikt, `AiFileUpdate`-Nebenbefund, **Line-Ending-
  Normalisierung** (User-E2E-Spec 2026-09-05, ersetzt E3-Skip).
- Cleanup-Kandidaten (eigener Zyklus): `StreamingBridge.clock`-Feld redundant (assigned, nie
  gelesen); `EclipseUtil.editInEditor` Dead Code (0 Referenzen).

## Triage-Liste (offen — #1–#4, #9, #13 sind shipped)

| # | Fehler | Modul | Fix (Jon gewählt) |
|---|---|---|---|
| 5 | `ShellTool`: `join(timeout)` ≠ Sichtbarkeitsgarantie, plain `LinkedList` cross-thread | core | Thread-sichere Liste (CopyOnWrite) + Stress-Test |
| 6 | `findFirst`/`diskDeleteFile`: `Files.walk`-Stream nie geschlossen; Delete meldet „Deleted:" trotz still übersprungener Fehler | core | try-with-resources; Teilerfolg benennen („Deleted N of M, failed: …") |
| 7 | `AiModelParser`: Parse-Fehler → `printStackTrace` + leeres Catalog, Root Cause verloren | core | Root Cause loggen (warn), leere Liste bleibt |
| 8 | `ThinkResolver.toReasoning`: „True"/"False" rutschen durch, Off-Tokens verbatim an LM Studio | core | Case-insensitive Normalisierung; Off-Token→"off", sonst→"on" |
| 10 | `VoiceInputService.transcribe`: kein Timeout, `f.get()` unbounded | core | HttpRequest-Timeout + `f.get(30s)` → Timeout = Fehlermeldung |
| 11 | `searchComplete`: Limit-Cap **ohne** Disclosure (grepComplete hat sie) | core | „showing N of M" / Cap-Disclosure wie grepComplete |
| 12 | `FileLines.extract(0,0)` → RAW-Content ohne Zeilennummern, Javadoc sagt 0 → 1/last nummriert | core | 0 als 1/last → nummriert (disk+eclipse konsistent) |
| 14 | `AnthropicProvider.listAiModels`: hardcodet `api.anthropic.com`, ignoriert custom `baseUrl` (Proxy→401) | core | `baseUrl` aus Config nutzen |
| 15 | `VoiceInputService`: doppeltes `startRecording` leakt die alte Line | core | Alte Line vor neuem Start schließen |
| 16 | `ModelListCacheTest.concurrentGetOrFetch_sameIdentity_singleFlight` timing-flakig (Full-Run 1× rot, solo 5/5 grün) | core | offen — gefunden 2026-09-05 |

**Ablauf pro Fehler (User-Vorgabe):** Rot-Test (Da Mek) → Jon prüft Rot-Test → Fix (von Jon
gewählt) → Grün → Commit. Inkremente klein bündeln (2–4 Fehler/Increment), Review via Da Thinka
am Ende. **Plugin-Hunt** (Da Mek) steht noch aus.

## Skips (dokumentierte Entscheidungen / bereits getrackt)

- Unbounded Query-Caches (`SearchQuery.CACHE`, `RegexUtils.GLOB_CACHE`) — ⏳ in open-points.md
- `ModelListCache` ohne Eviction — „no eviction needed" dokumentiert (ConfiguredChatModel-Javadoc)
- `SearchAgentTool` teilt parent `ApiRetry` — Design-Eigenheit
- `FileAgentHistoryStore` History-Wipe bei korrupter Zeile — dokumentiert
- `McpService` Connection-Wipe bei Fehler — dokumentiert

## Übernommen aus Release-Zyklus (2026-09-03/04, noch offen für User)

- ⏳ unbegrenzte Query-Caches (siehe Skips)
- ❓ in open-points.md: Glossar eager laden · `buildWithDev` compactet Da Mek vorher ·
  `eclipseWriteFile` immer UTF-8 · PDE-Runner meldet Skips nicht separat · Smoke-Test-Kosmetik
- issues/fact-issues.md: Punkt 3 (CancellationException-Stacktrace als Error), Punkt 5 (Node-20-Deprecation)
- Untracked: `release-notes-2026-09-04.md`
