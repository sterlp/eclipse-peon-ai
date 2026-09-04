# Session-Stand — Bug-Hunt-Zyklus (2026-09-04)

**User-Anweisung:** Da Mek sucht systematisch (core ✅ durch, Plugin ⏳ ausstehend), bewährt jeden
Fehler mit rotem Test **vor** dem Fix; Jon triagt und wählt die Fixes; nur echte Fehler.
Dieses File ist die Arbeitsliste — bei jeder Änderung sofort aktualisieren (Compact-Schutz).

## Triage-Liste (Status: alle ⏸ auf User-Freigabe, außer #9 = Konflikt)

| # | Fehler | Modul | Fix (Jon gewählt) |
|---|---|---|---|
| 1 | `FileUtils.applyEdit` ersetzt **alle** Vorkommen, Tool-Beschreibung (disk+eclipse) sagt „first occurrence" — Tool lügt | core | **🔒 User 2026-09-04:** Replace-All bleibt (bewusst, spart Tool-Runden); Tool meldet **Anzahl der Ersetzungen**; 0 Matches = Tool-Fehler; Count auch bei null-Delete („deleted N"); Javadoc + Tool-Beschreibungen (disk+eclipse) korrigieren |
| 2 | `ShellTool`: dokumentiertes `tailLines=-1` (=all) wird auf Default 50 gemappt | core | **🔒 User 2026-09-04:** kein Param → Default **60**; >0 → letzte N; <=0 (0/-1) → alle; Hard-Cap 3000 disclosed. **Plus neues Feature `filter`** (Regex-first/Literal-Fallback, filtern→tail, Disclosure). SOLL in [shell-tool.md](shell-tool.md) (neu angelegt, index.md registriert) |
| 3 | `CustomAgent`: fehlendes `tools:`-Frontmatter sperrt **alle** Tools, Javadoc sagt „absent = all" | core | **🔒 User 2026-09-04:** `null`-Allowlist → allow-all-Predicate in `CustomAgent.getToolFilter()` **vor** read-only-Regel; `ToolPolicy.enables` bleibt strikt. SOLL-Ergänzung in custom-agents-design.md |
| 4 | `StreamingBridge.startedAt` resetet pro Call statt pro Turn (Javadoc + Lifecycle: 1 Bridge/Turn) — UI „working since Xs" springt bei jedem Tool-Call zurück | core | **🔒 SOLL bestätigt (Jon, UI-Consumer geprüft):** ein Turn = eine Startzeit; Reset-Zeile in `call()` entfernen, Field-Init deckt den Turn |
| 5 | `ShellTool`: `join(timeout)` ≠ Sichtbarkeitsgarantie, plain `LinkedList` cross-thread | core | Thread-sichere Liste (CopyOnWrite) + Stress-Test |
| 6 | `findFirst`/`diskDeleteFile`: `Files.walk`-Stream nie geschlossen; Delete meldet „Deleted:" trotz still übersprungener Fehler | core | try-with-resources; Teilerfolg benennen („Deleted N of M, failed: …") |
| 7 | `AiModelParser`: Parse-Fehler → `printStackTrace` + leeres Catalog, Root Cause verloren (Verhalten greift trotzdem via SOLL-Fallback) | core | Root Cause loggen (warn), leere Liste bleibt |
| 8 | `ThinkResolver.toReasoning`: „True"/"False" rutschen durch, Off-Tokens verbatim an LM Studio | core | Case-insensitive Normalisierung; Off-Token→"off", sonst→"on" |
| 9 | `AllowlistWriteValidator`: `docs/../../secret.txt` matcht `*/docs/*` — Path-Traversal umgeht Jons Write-Scope | core | **🔒 User 2026-09-04:** nur Wording — Pfad vor Glob-Match normalisieren (`..`/`.` auflösen), Doc-Satz → „normalized path", BDD R1 um Traversal-Fall |
| 10 | `VoiceInputService.transcribe`: kein Timeout, `f.get()` unbounded | core | HttpRequest-Timeout + `f.get(30s)` → Timeout = Fehlermeldung |
| 11 | `searchComplete`: Limit-Cap **ohne** Disclosure (grepComplete hat sie) — „every truncation named" verletzt | core | „showing N of M" / Cap-Disclosure wie grepComplete |
| 12 | `FileLines.extract(0,0)` → RAW-Content ohne Zeilennummern, Javadoc sagt 0 → 1/last nummriert | core | 0 als 1/last → nummriert (disk+eclipse konsistent) |
| 13 | **User-Bug:** „Show real time response"-Checkbox in erweiterter Config ohne Funktion — nur Tokens sichtbar, kein Text mehr | plugin | **🔒 User 2026-09-04: Default = on.** Diagnose (Jon): Default-Mismatch vom Config-Clean-Break — Preference-Default `true` (`LlmPreferenceInitializer:44`) vs. `LlmConfigLoader:40` `parseBoolean(null, false)`. Fix: Loader-Default → `true`. SOLL in [streaming-display.md](streaming-display.md) R17 (neu) |
| 14 | `AnthropicProvider.listAiModels`: hardcodet `api.anthropic.com`, ignoriert custom `baseUrl` (Proxy→401) | core | `baseUrl` aus Config nutzen |
| 15 | `VoiceInputService`: doppeltes `startRecording` leakt die alte Line | core | Alte Line vor neuem Start schließen |

## Skips (dokumentierte Entscheidungen / bereits getrackt)

- Unbounded Query-Caches (`SearchQuery.CACHE`, `RegexUtils.GLOB_CACHE`) — ⏳ in open-points.md
- `ModelListCache` ohne Eviction — „no eviction needed" dokumentiert (ConfiguredChatModel-Javadoc)
- `SearchAgentTool` teilt parent `ApiRetry` — Design-Eigenheit
- `FileAgentHistoryStore` History-Wipe bei korrupter Zeile — dokumentiert
- `McpService` Connection-Wipe bei Fehler — dokumentiert

## Scope dieses Zyklus (User 2026-09-04, User kurz weg)

**Jetzt bauen:** #1, #2 (+filter), #3, #4, #9, #13 — alle 🔒 entschieden.
**Später:** #5–#12, #14, #15 + Plugin-Hunt (bleiben auf der Liste oben).

**Zyklus-Setup (Jon, AGENTS.md-Standard):** Branch `bug-hunt-2026-09-04`, Auto-Commit pro grünem
Increment (`inc-N: <summary>` + Assisted-by-Trailer). Finaler Merge = User-Entscheidung.

## Entschieden (User 2026-09-04)

- **#9** = nur Wording: Pfad vor Glob-Match normalisieren, Doc-Satz → „normalized path", BDD R1 um Traversal-Fall.
- **#1** = Replace-All bleibt (bewusst); Tool meldet Anzahl der Ersetzungen; 0 Matches = Fehler; Count auch bei null-Delete; Javadoc + Tool-Beschreibungen (disk+eclipse) korrigieren.
- **#2** = tail: kein Param → 60, >0 → N, <=0 → all, Hard-Cap 3000 disclosed; **neu** `filter` (Regex-first/Literal-Fallback, filtern→tail, Disclosure) → SOLL in [shell-tool.md](shell-tool.md).
- **#3** = null-Allowlist → allow-all vor read-only-Regel (SOLL in custom-agents-design.md).
- **#4** = ein Turn = eine Startzeit (SOLL bestätigt, UI-Consumer geprüft).
- **#13** = Default `showRealtimeAiResponse` = on; Loader-Default → `true` (SOLL in streaming-display.md R17).

## Ablauf pro Fehler (User-Vorgabe)

Rot-Test (Da Mek) → Jon prüft Rot-Test → Fix (von Jon gewählt) → Grün → Commit.
Inkremente klein bündeln (2–4 Fehler/Increment), Review via Da Thinka am Ende.

## Übernommen aus Release-Zyklus (2026-09-03/04, noch offen für User)

- ⏳ unbegrenzte Query-Caches (siehe Skips)
- ❓ in open-points.md: Glossar eager laden · `buildWithDev` compactet Da Mek vorher ·
  `eclipseWriteFile` immer UTF-8 · PDE-Runner meldet Skips nicht separat · Smoke-Test-Kosmetik
- issues/fact-issues.md: Punkt 3 (CancellationException-Stacktrace als Error), Punkt 5 (Node-20-Deprecation)
- Descoped eigene Story: Custom-Dropdown-Umbau (resolved-points.md)
- Untracked: `release-notes-2026-09-04.md`; PoDelegateTool-Glättung uncommitted (Stand vom Nachlauf)
