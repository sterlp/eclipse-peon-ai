# Session-Stand (2026-09-07)

## Git-Stand

- **`release-2026-09-06` @ `01c76ba`** (gepusht bis `4fc1008`, lokal 5 neue Commits:
  b011576 R6 · f14371a R5 · 402b922 + 7d224ec R-UI1/Review-Fix · 01c76ba cycle-close) —
  **Merge/Squash in main + Push = User-Entscheidung.** Main = origin/main `1f204aa` (User
  reviewed dort den Release; sein Squash-Merge des ersten Release-Stands stand noch aus —
  Stand bei Session-Start: "patch eingespielt" lief lokal).
- Alte lokale Branches: state-config-2026-09-06, toc-estimate-2026-09-06, sm-fixes-2026-09-06
  (→ renamed zu release-2026-09-06), alle intakt.
- **Core 678/0 · Plugin 191/0.** Plan archiviert: peon-plan/overview-done-2026-09-07-10-20.md.
- Nicht committet: test_project/issue.md + Test-Artefakte (renameRootDst_*, sub/) — bewusst.

## Zyklus `file-copy-e2e-fixes-2026-09-06` — ABGESCHLOSSEN ✅

- **Auslöser:** Copy-Tools-E2E (Test-Anleitung copy-tools-e2e-test.md von mir angelegt,
  User-Ausführung im isolierten Testprojekt → 4 Befunde in test_project/issue.md).
- **Behoben:** R6 (`Copied/Renamed <s> -> <t>` LLM-sichtbar, void→String, war langchain4j-`"Success"`)
  · R5 Qualified Paths Only (beide Familien, Copy+Rename, source+target; QualifiedPathValidator;
  eclipse-Ziel-Resolution-Fix — Workspace-Root-Fallback/1-Segment-Crash weg) · R-UI1
  (Question-Widget-Regression: finaler Scroll-to-bottom nach Widget-Completion, manuelle Verifikation).
- **Review:** Da Thinka 3-Seiten — 1 echter Fund (duplizierte Methoden in ChatMarkdownWidget,
  inc-3-ReplaceLines-Falle, gefixt 7d224ec) + Mutations-Nachweis R5 (`projectExists` →
  `eclipse_unknownProjectRejected` rot, sonst grün).
- Docs geflippt: file-copy-tool.md ✅ R1–R6 · user-question-tool-design.md R-UI1 ✅ · index.md.
- Test-Anleitung copy-tools-e2e-test.md: `diskRenameFile` → `diskRenameResource` korrigieren
  (Befund 3 des E2E) — **steht aus, kosmetisch**.

## User-Entscheidungen (Stand)

- R5 gilt **beide Familien, Copy + Rename** (User bestätigt). Begründung: stiller Falsch-Ort
  vs. lauter Fehler (Single-File-Ops bleiben Domain-Basis-Resolution).
- po.txt/dev-build-loop.txt Prompt-Änderungen (CONTEXT-LIMIT-Regel, Branch-Regeln inkl.
  main/master-Zeile + detached-HEAD) — User-Edits, committed.

## Nächste Schritte

1. **User:** Squash-Merge release-2026-09-06 → main nach Review + Push (beide Runden: der
   erste Release-Stand war evtl. schon gesquasht — Git-Verlauf prüfen, nicht doppelt).
2. **Bug-Fix-Zyklus:** Triage #5–#15 (Tabelle siehe unten) + ApiRetry-Verdacht (Null-Byte-
   IOException als Cancel klassifiziert? ❓ open-points.md, verwandt Memory #21) + Plugin-Hunt
   + **NEU: AgentOrder Auto-Create-Fehlschlag killt Agent-Reload** (Known Edge in
   agent-ordering.md — read-only Config-Dir → gar keine Agenten; robust = weiter ohne Ordering).
3. Offene ❓: Glossar eager ((b) Turn-Context-Item) · buildWithDev-Compact (Compact, ~50 %,
   nur neuer Plan) — Empfehlungen in open-points.md, „nimm deine Empfehlungen" genügt.
4. E2E-Erweiterungen möglich: Copy-Tools-Rerun auf dem gefixten Build (Anleitung liegt bereit),
   Edit-Tools-E2E (Line-Ending-Item 3 weiter ungeparkt).
5. Ideen-Backlog: Jon×Scaffold · builtin-agent-prompt-override (🚧) · eclipseJavaMoveType (🚧).

## Triage-Liste (offen — #1–#4, #9, #13, #16 shipped)

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

**Ablauf pro Fehler (User-Vorgabe):** Rot-Test (Da Mek) → Jon prüft Rot-Test → Fix → Grün →
Commit. Inkremente klein bündeln (2–4 Fehler/Increment), Review via Da Thinka am Ende.
Plugin-Hunt (Da Mek) steht noch aus.

## Geparkt / Wissenswert

- **Edit-Tools** (open-points.md): Rename auf "Edit", gemeinsame Doku, planEdit-Count,
  Line-Ending-Normalisierung (User-E2E-Spec, ersetzt E3-Skip). Reihenfolge: Rename → Doku →
  Count → Line-Ending. E2E-Spec: file-edit-tools.txt. ⚠️ Item 3 NICHT gebaut = rot im E2E ist
  erwartet.
- Cleanup-Kandidaten: `StreamingBridge.clock` redundant; `EclipseUtil.editInEditor` Dead Code;
  leerer /org.sterl.llmpeon/docs/adr-Restordner; test_project/issue.md nach Bug-Fix-Zyklus löschen.
- E2E-Tool-Namens-Falle: Tools heißen `diskRenameResource`/`eclipseRenameResource` (nicht
  *RenameFile) — in neuen Test-Anleitungen exakt prüfen.
- Docs-Lage: docs/** = /llmpeon-parent/docs/. Agent-Ordering neu dokumentiert
  (agent-ordering.md ✅ #128). Custom-Dropdown-Klassen gelöscht (2026-09-06).
- open-points.md: ❓ ApiRetry · ❓ Glossar eager · ❓ buildWithDev-Compact · ⏳ Query-Caches ·
  ⏳ Streaming-Präzisierungen · ⏳ Edit-Tools.
