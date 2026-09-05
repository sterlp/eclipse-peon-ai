# Session-Stand — Bug-Hunt + Streaming-Timing (2026-09-05)

## Shipped (in `2.7.0-fix`)

- **Bug-Hunt** (Branch `bug-hunt-2026-09-04`, Merge `db92e1b`, 2026-09-04): #1 applyEdit-Count,
  #2 ShellTool tail+filter, #3 CustomAgent null-Allowlist, #4 startedAt pro Turn, #9 Traversal
  (+follow-up `\`→`/`), #13 showRealtimeAiResponse Default on.
- **Streaming-Timing** (Branch `streaming-timing-2026-09-05`, Merge `86594a4` 2026-09-05, gepusht): R18–R21
  (Timer-Klasse, toc/s aus Token-Timer, "Started hh:mm", TOOL-Delta) + **R22** (kein toc/s am
  1. Token eines Calls, pro Call) + **R-PI1** (planImplemented-Kollision → Counter-Suffix,
  core `ArchiveName.firstFreeName`).
- Ground Truth beim Merge: Core (Surefire) **632/632** · Plugin (OSGi) **177/177**.
- **E5 offen (User-Entscheidung):** Disk-Tool Success-Message zeigt workingDir-Relative-Pfad
  (PO: kanonisch für Disk-Scope, by design) vs. User: BUG (KV-Cache-Poisoning).
  Optionen: (a) so lassen [PO-Empfehlung] · (b) absoluter Pfad.

## Geparkt

- **Edit-Tools** → [open-points.md](open-points.md): Rename auf "Edit", gemeinsame Doku (4 Tools),
  `planEdit`-Count, Eclipse-Doku-Konflikt, `AiFileUpdate`-Nebenbefund, **neu: Line-Ending-
  Normalisierung** (User-E2E-Spec 2026-09-05, ersetzt E3-Skip).
- **E2E-Abnahmetest nach Release:** `org.sterl.llmpeon.test/ai-e2e-test/file-edit-tools.txt`
  (User führt ihn aus; Item 3 = Line-Ending-Normalisierung ist **noch nicht gebaut** → wird
  bis zum Edit-Tools-Zyklus rot bleiben).
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
- Untracked: `release-notes-2026-09-04.md`; PoDelegateTool-Glättung uncommitted (Stand vom Nachlauf)
