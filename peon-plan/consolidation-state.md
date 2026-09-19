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

## Pauls Entscheidungen (2026-09-19) + Ergebnis — ABGESCHLOSSEN

1. Release-Merge: **(a) No-Op, skip + dokumentieren** → umgesetzt, kein Merge. (Paul schließt den
   Punkt in den Docs selbst als 🔒.)
2. Dirty Docs: erst eigener Commit → **`4568b51` "docs: status flips + open-points/memory (PO)"**
   (5 Docs + peon-plan/**).
3. fix/compact-slot-model: **konsolidieren** → **Merge `0af001a`** (history-only).

### Merge fix/compact-slot-model — Details

- 4 Konflikte, alle auf die neuere HEAD-Seite aufgelöst (eindeutig ableitbar, kein SOLL-Wissen nötig):
  - `AiPoAgent.java`: theirs fügt `compact(AiMonitor)`-Override wieder ein — PR #141 (`8a2f62e`,
    2026-09-16, main) hat exakt diese Methode **bewusst entfernt** (Diff verifiziert;
    Per-Agent-Compact-Buttons = neuer Design-Pfad) → HEAD behalten.
  - `docs/index.md`: theirs-Seite leer (HEAD hat neuere Docs-Linter/Blume-Einträge) → HEAD.
  - `docs/memory.md`: Session-Stand 2026-09-19 (HEAD) vs 2026-09-13 (theirs, stale) → HEAD.
  - `docs/open-points.md`: theirs-Seite leer (HEAD hat Neu-2026-09-14 + R-SEL-4) → HEAD.
- Merge-Tree **byte-identisch mit Pre-Merge-HEAD** (`git diff --cached HEAD` leer) — Content der
  +7 Commits war bereits via Squash-PR #140 (`c808c42`) auf dem Branch (verifiziert:
  3-arg `callBlocking(ChatRequest, AgentConfig, AiMonitor)` in ConfiguredChatModel.java:44).
- **Gates (mvn -o -pl org.sterl.llmpeon,org.sterl.llmpeon.test -am verify):**
  **Core Surefire 884/0** (Baseline exakt) · **Plugin Tycho 223/0** (11 skipped, Baseline exakt) · BUILD SUCCESS.
- Commits: `4568b51` (Docs) → `0af001a` (Merge). Branch-Tip: `0af001a`.

### Rest

- `bugfix/edit-tool-insert` (+4): unangetastet (Archiv, Paul).
- Sanity-Pass: abgenommen (Da-Dok: CONCERNS → Aufräum-Inkrement `93d512d`, 2026-09-19).

## Aufräum-Inkrement `93d512d` (2026-09-19)

- Race-Fix `UserContext.addOneTimeOrders`: synchronizedSet + drain unter demselben Monitor.
  Roter Test zuerst: alte Code korruptierte die Set (NPE auf null-Element, 0.002s).
  Neue Tests: `oneTimeOrdersDeliveredInOrderExactlyOnce` (Characterization) +
  `oneTimeOrdersConcurrentAddAndDrainLoseNothingAndThrowNoCme` (swap-falsifizierbar).
- Test-Dedup: `FileLinesTest.existingBehaviourUnchanged` + `DiskFileWriteToolTest.diskEditFile_twoCharOldStringRejects` gelöscht.
- AGENTS-DEV.md: 162→162 Zeilen (net flat), 2 Lernings absorbiert.
- Skill `komponenten-architektur`: Description-Patch (~/.peon/skills, außerhalb Repo).
- Wiki-Pattern "Edit-tool root-cause triage" + 2 Ledger-Einträge.
- Gates: Core 882/0 (884−2 Dedup), Plugin 225/0 (223+2 new), BUILD SUCCESS.
- ACHTUNG: `git add -A` hat auch Pauls `docs/open-points.md`-🔒-Flip (release-2026-09-06) +
  Da-Doks `peon-plan/overview.md` (Sanity-Review) in den Commit genommen — Repo-Konvention
  (Docs mitnehmen), aber transparent gemeldet.
