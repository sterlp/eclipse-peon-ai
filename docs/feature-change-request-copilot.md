# Feature-Change-Requests — Copilot-Mapping (TEMPORÄR)

> ⚠️ **TEMPORÄR — einzige Datei mit externen (Copilot-)Bezügen. Wird nach dem PO-Run gelöscht** — die
> verweise sind nur für Paul hier gültig. Alle neutralen SOLL-Docs: siehe [tool-evolution.md](tool-evolution.md).
> Quell-Plan: `/github-copilot-for-eclipse/peon-plan/overview.md` (Da Thinka, 2026-09-19).

## Mapping je CR

| CR | Extern (Pfad im Projekt `github-copilot-for-eclipse`, Disk `copilot-for-eclipse`) | Unser Tool | Fazit |
|---|---|---|---|
| CR-1 create_file | `com.microsoft.copilot.eclipse.ui/src/.../chat/tools/CreateFileTool.java` | `diskWriteFile` (core `tool/tools/DiskFileWriteTool.java`), `eclipseWriteFile` (plugin `parts/tools/EclipseWorkspaceWriteFileTool.java`) | Funktional parity; UI-Vorteil (Undo/Bar) → CR-7. **Nein** |
| CR-2 insert_edit_into_file | `.../chat/tools/EditFileTool.java` („Smart Edit": ganze Datei regeneriert mit `// ...existing code...`) | `eclipseEditFile`/`eclipseReplaceLines`/`eclipseInsertLines`/`eclipseUpdateOpenFile`, `diskEditFile`/… (Edit-Guard in core `FileUtils.applyEdit`) | Regen-Mechanismus: Token-Kosten + Silent-Drift → **Nein**; Review-UI → CR-7 (**Teilweise**, M) |
| CR-3 get_errors | `.../chat/tools/GetErrorsTool.java` (Pfad-Array, SEVERITY_ERROR, DEPTH_ZERO) | `eclipseReadProjectProblems`, `eclipseBuildProject` (plugin `parts/tools/EclipseBuildTool.java`) | Per-File+Severity-Filter fehlt uns → **Ja** (S), Filter auf Bestandstool |
| CR-4 java_debugger | `.../chat/tools/JavaDebuggerToolAdapter.java` (15 Actions, AST-Eval 5s, needConfirmation=true, Nightly+JDT) | — (nichts) | Neue Fähigkeit; L + sicherheitskritisch → **Nein/Roadmap**, ggf. Lese-Subset |
| CR-5 run_in_terminal | `.../chat/tools/RunInTerminalToolAdapter.java` + inner `GetTerminalOutputTool` (L258; persistente Session, background+ID, 1000-Zeilen-Truncation, Terminal-View, SPI-Backends) | `shellRunCommand` (core `tool/tools/ShellTool.java`, One-shot 60s) | Persistenz+Background fehlt → **Ja** (FG zuerst, M) |
| CR-5b get_terminal_output | siehe CR-5 | — | Teil von CR-5 |
| CR-6 ConfirmationService | `.../chat/confirmation/ConfirmationService.java` (+ Handler, YOLO-Preference, Once/Session/Global-Cache, Subagent-Mapping, `InvokeToolConfirmationDialog`) | nur Shell-Widget (`AIChatView.java:437-474`) | Kategorien+Scope-Cache fehlen → **Ja** (M-L, minimal zuerst) |
| CR-7 Change-Review-UI | `CreateFileTool`/`EditFileTool` + `WorkingSetHandler.java`/`ChangedFile.java` + Compare-Editor (Original-Cache einmalig → Multi-Round-Undo; WorkingSetBar Keep/Undo/View-Diff) | alle Write/Edit-Tools (schreiben sofort, kein Review) | Größter Vertrauens-/UX-Hebel → **Ja** (L) |
| CR-8–16 | serverseitige Built-ins (File-Read/Search/Web, `run_subagent`) nativ — nicht einsehbar | unsere Read/Search/Nav/Docs/Orchestration/Memory/Skills/Build-Familien | **Unser Vorsprung** — Nein |
| CR-17 | (Vergleichspunkt: dort disclosed Truncation) | `eclipseSearchFiles` (`EclipseWorkspaceReadFileTool.java:120-160`, Cap 1000 still) | **Ja** (S) |
| CR-18 | | `diskSearchFiles` (`DiskFileReadTool.java:73-99`, Limit still) | **Ja** (S) |
| CR-19 | (Vergleichspunkt: `get_terminal_output` 1000 Zeilen disclosed) | `webFetchAsMarkdown` (`WebFetchTool.java:49-73`, kein Cap, Fehlerpfad = voller Body) | **Ja** (S) |

## Bewusst nicht übernommen (mit Warum)

- **Smart-Edit Whole-File-Regen:** Token-Kosten je Edit, Silent-Drift-Risiko — unser gezielter Edit-Guard ist
  präziser/effizienter. Nur die Review-UI (CR-7).
- **YOLO-Auto-Approve als Default:** bleibt Opt-in.
- **Serverseitige Architektur (2-Teil, nativ):** wir sind bewusst all-client; Fähigkeiten haben wir selbst.
- **Absolute lokale Pfade in create_file:** wir bleiben Workspace-scoped (Disk-Tools decken den Rest).
- **Editor-Kontext als Chat-Kontext statt Tool:** wir haben das (Selektion/ReferencedFiles) bereits; nichts zu holen.

## Offene Fragen → siehe [tool-evolution.md](tool-evolution.md) §Offene Fragen (7 Stück, je mit Lean).
