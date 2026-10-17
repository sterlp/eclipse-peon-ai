# Session-Stand — 2026-10-17 (Nachtzyklus 5: Codegraph gebaut — ZYKLUS KOMPLETT)

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Zyklus-Ergebnis (Branch `story/night-5-2026-10-16`, Pauls Nachtliste KOMPLETT abgearbeitet)

| Commit | Inhalt |
|---|---|
| `d8c7b8aa`/`fe5cc80e`/`966ae530`/`f6ed37c4` | **BoundedLruCache ✅** (Komponente + SearchQuery/RegexUtils Cap 128 + WebFetchTool echtes LRU + AGENTS-Regel), Da-Dok ACCEPTED |
| `84a01345` | **EclipseGrepTool IFile-Disclosure ✅** (R-OD-7/UC-OD-10, C-EXT-Muster) |
| `8e6bcb54` | **SLF4J-Fix ✅** — slf4j-simple raus (pom/MANIFEST/build.properties). IST-Korrektur: kein `Require-Bundle org.slf4j.api`-Range existiert — API kommt aus gebündeltem lib-Jar, Range-Schritt entfällt |
| `e63ad98d` | **Test-Reworks ✅** — `rosterIsFlatAndInheritsBackground` = Test-Bug (`IThemeEngine.DEFAULT_THEME_ID` keine registrierte Theme-ID; registriert: e4_classic/e4_dark/e4_default) → Split + assumeTrue; Build-Cap-Test → Core (Fake-IMarker infeasibel, `eclipseBuildProject` löscht zuerst alle PROBLEM-Marker) + Plugin-E2E |
| `79676f3d` | Skill eclipse-dpe Theme-Falle + Ledger |
| `a506635e`…`10fcb42e` | **Codegraph I1–I5** — workingDirectory (Empty=unset), instructionsOf, Probe/Factory, generische MCP-Groups im ToolService, `CodegraphConnectionService` (Job-Koaleszenz, Failure-Isolation), Reserved-Name-Filter, Context (R-CG-7), Header-Icon (R-CG-8) |
| `7b74b76a` | **I6a** Homepage `setup/codegraph` + Sidebar + README + `codegraph.sh` gelöscht (war getrackt) |
| `64e04737` | Da-Dok-Rework: 12 UC-CG-Marker → R-CG-Form + Mutations-Nachweis Churn-Compare |
| `6cf93ff5` | **I6b Docs** — codegraph.md ✅, ADR-0068 + Index, index.md-Zeilen |
| `bf29adbb` | Rest-Docs (open/resolved-points, tool-output-disclosure, codegraph-architektur.md) |

**Finale Baseline: Core Surefire 1172/0, OSGi 376/0** (E2E-Test lief echt — Codegraph-Binary + Index vorhanden). Plan archiviert: `overview-done-2026-10-17-18-57.md`.

## Offen für Paul (Rückversicherungen ⏳ / Entscheidungen ❓ — Details open-points.md)

1. ⏳ **Reserved-Name `codegraph` (Jon-Entscheid a):** im User-MCP-Pfad UNBEDINGT reserviert —
   auch bei inaktivem Built-in; Workaround-Eintrag funktioniert nicht mehr (Clean Break).
   Umwerfen möglich → dann State-abhängiger Filter.
2. ⏳ **Icon-Visuals Q2=(a):** ON=codegraph.svg, OFF=gedimmt, ERROR=ImageUtil.STOP;
   `codegraph_dark.svg` von Mek nach Hammer-Muster erzeugt — Gegencheck beim Smoke.
3. ❓ **S3 (🗜 in PoDelegateTool/AgentsTool)** nur halb machbar — AgentsTool-Teil in Pauls
   untracked WIP; PoDelegate-Teil wäre allein inkonsistent. Paul entscheidet.
4. Paul-Smoke Codegraph (Auto-Connect + Icon + Tool in Jon/Dev) + Merge → main = Paul.
5. Pauls WIP im Tree unverändert: ShellTool.java (M), feature-request, agent/model/, agent/tools/.

## Backlog (nächste Session, Paul-Prio laut Report 2026-10-17)

- **Async-Agents Top-3** (async-agents.md 🚧 — Paul-Entscheidung offen, daneben
  Mutable-`request`-Merge-Check-Item VOR dem Async-Bau, open-points).
- **TempFileTool** (Slice 2 Output-Trim 🚧 in design).
- Bug-Backlog: Fehlerklassen-Tabelle R-CC-7/ApiRetry (❓), Header-State-Leak (❓, IST-Messung
  offen), Context-Overflow-Zyklus (❓ Auto-Compact, Pollution, Memory-Snapshot).
- Lesestoff: Compact-Lock ❌ specified (gebaut? nein — baubar, Paul-Prio offen).

## Notizen

- **AGENTS-DEV.md neu (uncommitted — mit nächstem Commit rein):** Docs-ID-Marker aus dem
  Feature-Doc kopieren (R-X-n hat keine UC-X-n-Definitionen) + `lintDocsAndTests` ins
  Inkrement-Gate, sobald Marker geplant sind (12 VERWAIST liefen durch 6 grüne Gates).
  Ledger-Zeile skill-impact.md ergänzt (update accepted).
- memory-Tool-Eintrag gesetzt (gleicher Inhalt, cross-projekt).
- eclipseEditFile frisst weiterhin params (4× "oldString is required" heute) — Fallback
  eclipseReplaceLines/diskEditFile mit Read-back funktioniert.
- Skill-Evolution: Update-Outcome docs-linter-Gate-Empfehlung (s. o.).
