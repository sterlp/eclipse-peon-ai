# Session-Stand — 2026-10-17 (Tag-Zyklus: Trim-Slice-1 fertig + Codegraph designt)

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Zyklus-Ergebnis (Branch `story/night-5-2026-10-16`)

| Commit | Inhalt |
|---|---|
| `f277446e` | **I1 ✅** — `OutputTrim` (core/shared) + 8 Tests, Gate 1136/0 |
| `22a91fed` | **I2 ✅** — ShellTool `tailLines`→`maxChars` (Default 10.000, kein Hard-Cap, Filter vor Trim) + Homepage, 1139/0. Plan-Lücke: 5. Rewrite `runOsCommand_filterWithMaxChars` (Jon-OK) |
| `dc4a5594` | **I3 ✅** — Grep-Migration (`AiReponseBuilder` D8, `MAX_GREP_LINE_CHARS` weg, Cap 500), OSGi 351/0. Genehmigte Abweichung: Pin 515 statt 501 (D3-Arithmetik) |
| `c97b5c19` | **toK-Rework ✅ (inc-7)** — `Locale.getDefault()`, k ohne Dezimalstellen (max 999k), M ab 1 Mio mit 2 Nachkommastellen, Edge 999.999.999→`1.000M` gepinnt; superseded `bc3b70b5`. 1144/0 |

- **Da-Dok-Review: CONCERNS (nur Docs-Cycle F1–F5)** — alle von Jon geschlossen (shell-tool.md
  R1/R2 neu, R-OD-6 2000→500, Inventory #12, SOLL-#6 als Verlagerung dokumentiert, WIP-Docs im
  Docs-Commit). Kein Code-Rework. Ledger-Zeile in skill-impact.md ergänzt (no change + Präzisierungskandidat).
- **Docs-Status:** output-trim-service.md ❌→✅, shell-tool.md R1/R2 ✅ (2026-10-17), index.md-Zeilen
  aktualisiert, open-points.md aufgeräumt (Trim-Bullet 🔒, 2 neue ❓: Read-Tools-Rollout, Pauls WIP).

## Pauls uncommitted WIP im Tree (NICHT von uns)

- **ShellTool.java:** Parameter-Rename `workingDirectory`→`workDir`, `@P`-Text, Error-Message —
  Tool-Schema-Change. Liegt als ❓ in open-points.md (SOLL klären). **Paul committet selbst nach
  kurzem Review (2026-10-17)** — nicht von uns anfassen.
- Untracked: `agent/model/`, `agent/tools/`, `long-shell.log`, `feature-request-ShellTool.md` — bleiben untracked.

## Paul-Orders 2026-10-17 (Autonomer Build, Paul nicht erreichbar)

> „Baue nur das wo du keine Fragen hast, alles auf diesem Branch" — Branch
> `story/night-5-2026-10-16` bleibt. Reihenfolge (Paul-Prio): **BoundedLruCache →
> EclipseGrepTool IFile-Disclosure → SLF4J-Fix → Test-Reworks (am Ende) → Codegraph-Bau
> (ohne UI-Config)**. Alias-Path = verworfen (resolved-points.md + shell-tool.md Note).

1. **BoundedLruCache** (bounded-caches.md — jetzt ❌): geteilter `BoundedLruCache<K,V>`
   (core/shared, thread-safe), adoptiert `SearchQuery.CACHE` + `RegexUtils.GLOB_CACHE`
   (Cap je 128), `WebFetchTool` bleibt Cap 5. AGENTS.md-Regel „Every RAM cache is bounded."
   Tests: LRU-Eviction je Cache, SearchQuery-Cap-Nachweis.
2. **EclipseGrepTool IFile-Disclosure** (Da-Dok-CONCERN, unblocked `af658e0f`): C-EXT-Muster
   (`effectiveExtension`) auf den IFile-Zweig, Pendant-Test analog
   `DiskGrepToolTest.fileTargetBeatsExtensionFilter`. Details: Peon-Plan-Archiv
   `overview-done-2026-10-16-13-16.md` §8.
3. **SLF4J-Fix:** Range `[2.0.0,3.0.0[` schärfen + slf4j-simple aus der Resolution raus
   (wer simple zieht, verifiziert Da Mek — Plugin-`pom.xml`, Target, MANIFEST; Kontext:
   `/llmpeon-update-site3` im Workspace). Bestehendes Verhalten: unser Provider gewinnt.
4. **Test-Reworks** (Da Mek, am Ende des Builds): `rosterIsFlatAndInheritsBackground`
   (unstable — bug in code or test?) + `buildReportCappedAt100WithDisclosure`
   (kurzer Timeout; Cap als eigenes Feature mit Fake-Problems statt echtem Projekt-Error;
   UC-OD-5 = zwei Tests).
5. **Codegraph-Bau** — `docs/codegraph.md` R-CG-1…11, **v1 OHNE UI-Config** (R-CG-5 geändert,
   Checkbox out of scope). Architektur-Doc vor Planung anlegen.

## Offene Schritte (vorherige Session — erledigt/überführt)

1. **Codegraph — ✅ designt, ❌ specified, NICHT umsetzen:** `docs/codegraph.md` (R-CG-1…11,
   Paul specified 2026-10-17, alle Fragen geklärt): Built-in Auto-Connect — **eigenes Enable,
   unabhängig vom MCP-Toggle**; Trigger = **aktives Projekt** (Pin = Selection-Freeze, keine
   Voraussetzung); Probe = `.codegraph/codegraph.db` + Binary (core, testbar); Failure-Isolation;
   Config = eine Checkbox auf MCP-Seite (Default an, sonst nichts); Toolset nur `codegraph_explore`;
   Projekt-Info-Hinweis (Disk-Pfade → eclipse*-Tools selbst übersetzen, KEIN Tool-Output-
   Postprocessing) + Server-Instructions via `McpClient.instructions()`; Icon `icons/codegraph.svg`
   (von Paul gelegt) ON/OFF/FEHLER + Tooltip; kein Init-Trigger, kein Telemetry-Override; Dedup:
   Built-in verdrängt manuellen `codegraph`-Eintrag (Homepage: alten Eintrag löschen).
   **Vor Planung: Architektur-Doc anlegen.** Fakten-Basis in codegraph.md (Da-Sniffa-Recherche:
   `StdioMcpTransport.workingDirectory` ist bereits in 1.22.0-beta32 im Classpath — codegraph.sh
   entfällt; codegraph = tree-sitter + SQLite-FTS5, **kein BM25**).
2. **Merge → main + Paul-Smoke** = Paul (Trim + toK: Token-Header `1,84M`-Format smokeen).
3. Backlog-Priorität laut Paul-Report (siehe Chat-Zusammenfassung 2026-10-17): ~~Codegraph-Fragen~~
   (erledigt) → Async-Agents Top-3 → BoundedLruCache → TempFileTool → S3/IFile-Disclosure.
4. **Test-Reworks für Da Mek am Ende des Zyklus** (Paul 2026-10-17, Queued): Paul hat TODOs im
   Code ergänzt — `EclipseBuildToolTest.rosterIsFlatAndInheritsBackground` („test unstable, rework,
   bug in code or in test?") und `buildReportCappedAt100WithDisclosure` („needs rework, far shorter
   timeout; only add a test that we see project errors / cap as own feature with fake problems /
   UC-OD-5 are two tests"). Mek macht beides am Schluss, danach erneute Zusammenfassung vom Stand.
5. **Alias-Path-Gedanke (Paul 2026-10-17, Queued, ❓ offen):** Beobachtung: das LLM setzt immer
   wieder den Eclipse-Project-Path als Disk-Path. Idee: „alias path"-Feature für alle Tools
   (Input BOTH workspace + disk path). Paul hofft, die Doc-Anpassung hilft und das Feature nicht
   nötig ist. **Zusammen mit Codegraph entschieden:** Projekt-Info-Hinweis (R-CG-7) ist der
   v1-Fix; Alias-Feature/S2-Wrapper nur bei Praxis-Befund. Details: open-points.md.
6. **SLF4J Multiple Providers (Paul 2026-10-17, Fix-Richtung geklärt):** unser
   `EclipseSlf4jProvider` gewinnt, aber slf4j-simple ist zusätzlich resolved — Version-Range
   `org.slf4j.api` schärfen + simple aus der Resolution raus (wer simple zieht, verifiziert
   Da Mek). Details: open-points.md (Bestands-Eintrag 2026-09-25 ergänzt).

## Notizen

- eclipseEditFile nahm oldString mehrfach nicht an (4× "oldString is required") — Fallback
  replaceLines + Read-back hat funktioniert. Beobachtung, kein dauerhaftes Lerning (möglicher
  Session-Glitch).
- Mek-Kontext nach I3 88 % → Compact vor nächstem großem Inkrement.
- Skill-Evolution: kein Outcome (Ledger-Zeile gesetzt).
- memory.md-Insert-Glitch 2026-10-17: eclipseInsertLines nach `afterLine` mitten in einem
  mehrzeiligen Item gesplittet — bei Sections mit mehrzeiligen Items lieber Whole-File-Write.