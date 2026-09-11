# ADR-0046 — Core-Portability-Review (Nordstern: Web-App-Port)

**Status:** Accepted (2026-09-11, Zyklus core-cleanup-2026-09-11)

## Context

Nordstern-Szenario (User): Jon mit den Disk-Tools als Web-App aus Eclipse portieren — alle
Eclipse-Tools fallen weg. Arch-Review im Cleanup-Zyklus (searchAgent-Befunde + IST-Verifikation,
Plan Inc-2). Fragen: was gehört in den core, ist die Architektur sauber abstrahiert, was müsste
beim Port portiert werden?

## Decision

1. **Core bleibt import-rein** — 0 `import org.eclipse` in core `src/main` (verifiziert nach Inc-2,
   heute erneut). Web-App-Port der Business-Logik ist damit grundsätzlich möglich.
2. **Kontrakte UI-agnostisch** ✓: `AiMonitor`, `ToolLoopRequest`, `ConfiguredChatModel`,
   `LlmConfigStore`; `QualifiedPathValidator` sauber injiziert.
3. **Moves Inc-2 (`b7f67e0`):** `SimpleDiff`, `WorkspaceGuideline` (Jackson-Record) →
   `shared`; `ThinkValueSupport` → `provider`; `AiAgentStatusModel` → `agent` — je 1 Import-Fix im
   Plugin, verhaltenstreu, je 1 Core-Test dort wo Tests existierten.
4. **Dokumentierte Moves, NICHT in diesem Zyklus:** `AskUserTool` → core (braucht
   Presenter-Interface) · `WorkspaceMemoryTool` → core (braucht Store-Interface,
   LlmConfigStore-Muster).
5. **`UiCommand`-Hierarchie (LiveStatus/Scroll/SetTheme etc.) BEHALTEN** — gehört zum WIP-MVP
   agenten-status-im-header (index.md). Kein Dead-Code-Lösch-Bitte. (memory.md C-Kategorie)
6. **`StringMatcher` bleibt vendored** (EPL, Header intakt — lassen).

## Consequences — Gaps beim Web-App-Port (Nordstern)

- **Composition-Root-Gap:** `PeonAiService` + `AgentContextComponent` + `BuildPoAgentComponent`
  sind Eclipse-typisiert (Plan-Flow nur `IFile`-basiert) — der eigentliche Port-Aufwand liegt hier,
  nicht in der Business-Logik.
- **Fehlende Web-App-Tools:** Build, Test-Runner, Java-Navigation, Console — Plugin-Domain; die
  Web-App braucht eigene Äquivalente oder verzichtet bewusst.
- **`StaticContextItem`** verweist auf eine Plugin-Klasse + hartcodierte eclipse-Guidance → vor dem
  Port konfigurierbar machen.
- **`peon.test.project` existiert nicht mehr** (ADR-0037-Historie) — tote Referenz, nicht wieder
  aufmachen.
- **Altschuld:** `SimpleDiff`/`WorkspaceGuideline` haben keine Tests (schon vor dem Move keine —
  kein Scope-Teil dieses Zyklus; nachholen, wenn sie sich verhaltenstreu ändern).
