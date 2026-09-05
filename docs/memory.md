# Session-Stand (2026-09-05)

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
