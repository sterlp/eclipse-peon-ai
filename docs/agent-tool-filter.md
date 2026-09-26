# Agent-Tool-Filter — Tool-Sichtbarkeit je Agent

> **Status:** ❌ specified (2026-09-25, Paul) · **idPrefix:** TF ·
> **ADRs:** [ADR-0048](adr/0048-docs-linter-read-only-and-tool-split.md) (Korrektur 2026-09-25) ·
> **verwandt:** [review-agent.md](review-agent.md) · [custom-agents-design.md](custom-agents-design.md) · [search-agent-tool.md](search-agent-tool.md)

**Ziel:** Agenten bekommen ihre Tools über den bestehenden Filter-Mechanismus (`getToolFilter()` /
`getToolNameFilter()`), **nicht** durch Klassen-Splitting oder zusätzliche Fassaden.
`isEditTool()` bleibt die grobe interne Split-Regel (edit- vs. read-Agenten); Abweichungen je Agent
sind explizite Whitelist-Exceptions. Tool-Namen, die Filter oder Tests referenzieren, werden als
**statische String-Konstanten** an der Tool-Klasse gefasst — damit entfällt das „stille
Umbenennen"-Risiko (eine Quelle für Konstante und `@Tool(name=…)`).

## Business Rules

### R-TF-1 ❌ — Da Dok erhält das ShellTool (Whitelist-Exception)

- `AiReviewAgent.getToolFilter()` wird erweitert: `!isEditTool || t.getTool() instanceof ShellTool`
  — **beide** Shell-Methoden (`readOperationSystemInformation`, `shellRunCommand`) sind für Da Dok
  sichtbar (RAM-Sklave und Standalone-Peon-Review teilen dieselbe Agent-Klasse → beide). Typprüfung
  statt String-Vergleich (Präzedenz `noPrivilegedTools`, Compiler-Bindung); die Namen sind zusätzlich
  als Konstanten gefasst (R-TF-3), damit Tests ohne Literale asserten.
- **WEIL:** Review-Use-Case belegt (Release-Scan: Commits nicht isolierbar — kein Git; `git
  log/diff/show`, Build-/Test-Diagnose sind Da Doks Arbeit). [review-agent.md](review-agent.md) R5
  wird präzisiert: Shell erlaubt für Diagnose/git — **Code-Änderungen via Shell bleiben Regelbruch**
  (soft rule, wie bei Da Mek).
- GIVEN Da Dok WHEN toolSpecifications THEN `readOperationSystemInformation` + `shellRunCommand`
  sichtbar
- GIVEN Da Dok WHEN Filter THEN Write-Tools, JavaDebugTool, AskUserTool, WorkspaceMemoryTool
  bleiben unsichtbar (nur Shell ist die Ausnahme)
- GIVEN Da Dok WHEN WriteValidator THEN `DENY_ALL` unverändert (Shell-Whitelist ≠ Schreibrecht der
  Write-Tools)

### R-TF-2 ❌ — Da Sniffa (SearchAgent) sieht keine Plan-Tools

- Der `SearchAgentTool`-Filter schließt `PlanTool` aus. **🐞 Befund 2026-09-25:** Plan-Tools sind
  `isEditTool()==false` ("READ" markiert), schreiben aber real (`planSave`/`planUpdate` →
  `peon-plan/overview.md`) — der SearchAgent sah bisher alle vier plan*-Methoden.
- GIVEN SearchAgent WHEN toolSpecifications THEN keine plan*-Tools (planRead/planSave/planUpdate/
  planImplemented)
- GIVEN SearchAgent WHEN sonstige READ-Tools THEN unverändert sichtbar (read/grep/search…)
- GIVEN Da Dok / Da Thinka / Da Mek WHEN Filter THEN deren plan*-Sichtbarkeit bleibt unverändert

### R-TF-3 ❌ — Tool-Namen für Filter/Tests als statische Konstanten

- Jede Filter-/Whitelist-Stelle, die einen Tool-Namen als String prüft, nutzt eine
  `public static final String`-Konstante an der Tool-Klasse (`@Tool(name = KONSTANTE)`), kein
  doppeltes Literal. Scoping: nur dort, wo Namen referenziert werden — nicht pauschal alle
  Tool-Klassen umbauen.
- GIVEN ShellTool WHEN Da-Dok-Whitelist THEN Referenz = ShellTool-Konstante, kein Literal
- GIVEN Umbenennung der Shell-Methode WHEN Konstante mitgezogen THEN Filter bleibt konsistent
  (eine Quelle)
- GIVEN Methode wird umbenannt ohne die Konstante zu berühren THEN Compile-Fehler an der
  Filterstelle (Compiler-Bindung über die Konstante)

### R-TF-4 ❌ — Tool-Matrix je Agent als Test (Erweiterung)

- Ergänzt die bestehende Matrix (UC-DL-48…51): **Da Dok** hält `shellRunCommand`,
  `readOperationSystemInformation`, `eclipseRunJavaTests`, `eclipseReadProjectProblems`,
  `eclipseBuildProject`, `lintDocs`, `lintDocsAndTests`, plan* (alle 4 inkl. `planUpdate`); **nie**:
  Write-Tools, JavaDebugTool, AskUserTool, WorkspaceMemoryTool, `nextIds`. **SearchAgent** hält
  keine plan*-Tools.
- GIVEN PeonAiService WHEN Da-Dok-Toolnamen THEN obige Menge (Shell gegen die Konstanten aus
  R-TF-3; Namen ohne Konstante als Literal zulässig — Scoping R-TF-3)
- GIVEN PeonAiService WHEN SearchAgent-Toolnamen THEN plan*-frei

### Use-Case-Definitionen

#### UC-TF-1 — Da Dok sieht beide Shell-Methoden
GIVEN Da Dok (`AiReviewAgent`, RAM-Sklave wie Standalone) WHEN `toolSpecifications` THEN
`readOperationSystemInformation` + `shellRunCommand` enthalten.

#### UC-TF-2 — Da Dok versteckt andere Edit-Tools weiterhin
GIVEN Da Dok WHEN Filter greift THEN Write-Tools, JavaDebugTool, AskUserTool, WorkspaceMemoryTool
unsichtbar; WriteValidator bleibt `DENY_ALL`.

#### UC-TF-3 — SearchAgent ohne plan*-Tools
GIVEN Da Sniffa WHEN `toolSpecifications` THEN keine plan*-Methoden; sonstige READ-Tools
unverändert sichtbar.

#### UC-TF-4 — Da-Dok-Matrix behält die Review-Werkzeuge
GIVEN PeonAiService WHEN Da-Dok-Toolnamen THEN `eclipseRunJavaTests`, `eclipseReadProjectProblems`,
`eclipseBuildProject`, `lintDocs`, `lintDocsAndTests`, plan* (alle 4) enthalten — niemals `nextIds`.

## BDD-Test-Mapping

| Regel | Test |
|---|---|
| R-TF-1 | `PeonAiServiceTest` — Da-Dok-Matrix: Shell sichtbar, Write-Tools unsichtbar |
| R-TF-2 | `PeonAiServiceTest` — SearchAgent plan*-frei |
| R-TF-3 | Compile-Bindung + Konstanten-Referenz (implizit über R-TF-1/2) |
| R-TF-4 | dieselben Tests wie R-TF-1/2 |

## Out of Scope

- Jons kuratierter ToolService bleibt (PoDelegateTool, DocsIdTool, PlanReadTool-Fassade).
- MCP-Name-Filter unverändert.
- Naming-Uniformität (`planUpdate`→`planEdit` …) bleibt geparkt (open-points ⏳).
- Fassaden bleiben bestehen; neue Fälle entscheiden sich im Einzelfall: erst Filter
  (Standard-Mechanik), Fassade nur bei echtem Ownership-Split (ADR-0048 Korrektur 2026-09-25).
