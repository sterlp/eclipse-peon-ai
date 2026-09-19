# Task-State: Branch-Konsolidierung auf bugfix/user-context-selection (2026-09-19)

Order Paul: alle Branch-Änderungen konsolidieren, dann Sanity-Pass. **STATUS: STOP-AND-ASK bei Paul (Question 1 offen).**

## Inventar (erhoben 2026-09-19, `git fetch` frisch)

| Branch | vs current (73bb156) | Status |
|---|---|---|
| `bugfix/edit-tool-insert` | +4 (1dce54b, a3e8ce1, 729e7e7, 4788587) | **AUSGESCHLOSSEN** (Paul: superseded, Archiv) |
| `fix/compact-slot-model` | +7: efa22df (ADR-0034 COMPACT-slot-Routing), 947a49e, 0b69003, 3977a8d, 3ed1554, 1eb5f36, be608d3 | nur melden, Paul entscheidet |
| `main`, `core-cleanup-2026-09-11`, `fix/compressor-empty-compact-input` | 0 ahead | voll merged, nichts zu tun |
| `origin/release-2026-09-06` (nur remote, kein Local) | +25 Commits (a1d8d35, 27c09ad, 11f34f6, b3e0597, R5/R6, R-UI1, state-config …) | **siehe Befund unten** |

Stashes: **keiner** (nichts zu löschen).

## BEFUND (IST-Widerspruch zur Order) — release-2026-09-06 ist bereits voll enthalten

- Squash-Commit `45f2a0d2` "Release 2026 09 06 (#132)" ist auf dem aktuellen Branch;
  sein **Tree ist byte-identisch mit dem Release-Branch-Tip** `2e338e5` "clean"
  (verifiziert per Tree-Hash-Vergleich: beide `955c88a9`).
- Alle 3 benannten Commits sind inhaltlich im aktuellen Tree:
  - `a1d8d35` (Dropdown gelöscht): `parts/widget/dropdown/` existiert nicht auf current.
  - `27c09ad` (UTF-8-Pin): ISO-8859-1-Regressionstest in `EclipseWorkspaceWriteFileToolTest.java:111-125`.
  - `11f34f6` (PDE-Skip-Count): `EclipseRunTestTool.java:326` "Skipped: ".
  - Auch R5/R6 (copy/rename String + QualifiedPathValidator) und R-UI1 (ScrollToBottomCommand) sind da.
- Dry-run `git merge-tree --write-tree`: **15+ Konflikte** (add/add: docs/agent-ordering.md,
  docs/configuration.md; content: AGENTS.md, docs/index.md, docs/memory.md, docs/open-points.md,
  docs/resolved-points.md, docs/adr/index.md, DiskFileWriteTool.java, DiskFileWriteToolTest.java,
  EclipseWorkspaceWriteFileToolTest.java, PeonAiService.java, PeonAiServiceTest.java,
  BuildPoAgentComponent.java, MANIFEST.MF, pom.xml, 3× Prompts). Alle auflösbar als "ours"
  (current ist weiter) — aber 25 redundante History-Commits + Review-Noise auf dem zu reviewenden Branch.
- **Merge = null Content-Gewinn.** Empfehlung: als verifizierter No-Op dokumentieren, nicht mergen.

## Working Tree (vor jedem Merge dirty)

- Modified (Pauls Status-Flips nach Review): docs/disk-file-write-tool.md, docs/docs-linter.md,
  docs/eclipse-read-tools.md, docs/memory.md, docs/open-points.md
- Untracked: peon-plan/ (archivierter Plan overview-done-2026-09-19-11-29.md)

## Offene Fragen an Paul

1. Release-Merge: (a) No-Op, skip + dokumentieren (empfohlen) / (b) merge anyway (alle Konflikte "ours") / (c) anderes?
2. Dirty Docs: erst als eigener "docs: status flips"-Commit einpflegen? (Merge braucht cleanen Tree)
3. fix/compact-slot-model (+7): jetzt Teil des Zyklus oder später?
