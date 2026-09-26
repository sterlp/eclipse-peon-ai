# Plan: Agent-Tool-Filter (R-TF-1…4, docs/agent-tool-filter.md)

SOLL: **docs/agent-tool-filter.md** (❌ specified 2026-09-25) · **docs/review-agent.md** R5 (präzisiert:
Shell für Diagnose/git, Code-Änderungen via Shell = Regelbruch) · **ADR-0048** (Ergänzung
2026-09-25: R-TF-3-Konstanten; „erst Filter, Fassade nur bei Ownership-Split").
**Keine Docs-Änderungen im Build** (Docs gehören Jon). UC-IDs `UC-TF-1…4` (nextIds frei gezogen).

## ⛔ STOP-AND-ASK (Paul 2026-09-08 — gilt für JEDES Inkrement)

Bei Compile-Fehlern ohne Lösung, nicht-grünen Tests, IST-Widersprüchen zum Plan oder Unklarheiten:
**aktiv bei Jon (askDev-Kanal) nachfragen** — nie still workarounden, nie das SOLL ändern.
Beispiel: eine Matrix-Methode aus dem Doc (z. B. `eclipseReadProjectProblems`) trägt am Code
einen anderen Namen als erwartet → STOP-AND-ASK, nicht still umbenennen.

---

## Verifizierte IST-Fakten (Grep/Read 2026-09-25)

| # | Fakt | Evidenz |
|---|---|---|
| 1 | `ShellTool` (core `tool/tools`): 2 `@Tool`-Methoden `readOperationSystemInformation` (:55-56, **ohne** `name=`), `shellRunCommand` (:67-68, **ohne** `name=`); `isEditTool()==true` (:42-43). | ✅ |
| 2 | `AiReviewAgent.getToolFilter()` (core, :73-76): `super.getToolFilter().and(t -> !t.getTool().isEditTool())` → filtert beides Shell-Methoden weg. `getWriteValidator() = DENY_ALL` (:78-81) — bleibt. | ✅ |
| 3 | Da-Dok-Slave: `BuildPoAgentComponent:102-106` — anonymes `AiReviewAgent`-Subobjekt, `super.getToolFilter().and(noPrivilegedTools)` (nur WorkspaceMemory+AskUser) → **erbt** die neue Whitelist automatisch; RAM-Slave UND Standalone-Peon-Review teilen die Klasse → beide (R-TF-1). | ✅ |
| 4 | `SearchAgentTool` (core, :25-28): Filter = Feld (`@Getter @Setter`), Default `!isEditTool && !(instanceof SearchAgentTool) && !(instanceof ShellTool)`. `setFilter` wird **einmal** aufgerufen: `SharedToolsComponent:52-54` (Plugin, `sa.getFilter().and(!(instanceof AskUserTool) && !(instanceof WorkspaceMemoryTool))`). | ✅ |
| 5 | `PlanTool` (Plugin `parts/tools`): 4 `@Tool`-Methoden `planRead/planSave/planUpdate/planImplemented` (alle **ohne** `name=`), `isEditTool()` **nicht** override → `false` (Befund R-TF-2: READ-markiert, schreibt aber real). Registrierung: `PeonAiService:130-131` (`sharedToolService.addTool(planTool)`). Core kann `PlanTool` **nicht** referenzieren (Plugin-Klasse) → Filter-Ausschluss muss plugin-seitig sein. | ✅ |
| 6 | Test-Muster `PeonAiServiceTest` (Plugin, JUnit 4, `assertDocsLinterOnly` :951-955 ist **lockert** (contains/absent), `activeToolNames(agent)` :939-945 nutzt `agent.isToolActive` + `getSpec().name()`) → R-TF-4-Matrix ohne bestehende Strict-Tighten. `SharedToolsComponentTest:65-80` hat das Filter-Test-Muster (`searchAgent.getFilter().test(executor)`). | ✅ |
| 7 | Bestehende Tests, die die Änderungen berühren: `docsLinterMatrixForDaDok` (UC-DL-51, PeonAiServiceTest:822 — locker, bleibt grün), `test_sharedTools_searchAgentFilter_excludesAskUserAndMemory` (locker, bleibt grün), `SearchAgentToolTest` (Core, Filter nicht angesprochen, bleibt grün), `CustomAgentServiceTest` (Custom Agents bleiben `!isEditTool` — Shell bleibt dort unsichtbar, unverändert), `AiReviewAgentTest` (Core, erhält neue Tests). **Kein** Test heute behauptet, Da Dok/Sniffa hätten/nicht-hätten Shell bzw. Plan. | ✅ |
| 8 | ADR-0048-Präzedenz: `SmartToolExecutor` existiert **pro `@Tool`-Methode** (ToolService:47, `getExecutor(name)`), Filter-Prädikat operiert pro Methode; `instanceof`-Prüfungen auf `getTool()` sind der etablierte Compile-Bindungs-Mechanismus (BuildPoAgentComponent:90-91, SharedToolsComponent:53-54). | ✅ |

## 1. Context

Zwei Sichtbarkeits-Fehlstellen im bestehenden Filter-Mechanismus: (1) Da Dok (`AiReviewAgent`)
verliert das ShellTool am `isEditTool`-Overlay, obwohl Review-Diagnose (git, Build/Test-Log)
Shell braucht — Release-Scan belegt (review-agent.md R5). (2) Da Sniffa (`SearchAgentTool`) sieht
alle vier `plan*`-Methoden, weil `PlanTool` `isEditTool()==false` meldet, aber real schreibt
(`peon-plan/overview.md`). Ziel: beides über die **bestehenden Filter** fixen (kein Klassen-Split,
keine neue Fassade — ADR-0048-Ergänzung), Tool-Namen als Konstanten (R-TF-3), Tool-Matrix als
Tests (R-TF-4).

## 2. Design-Entscheidungen

1. **R-TF-1 — Whitelist per `instanceof`, keine Namens-Strings im Filter:**
   ```java
   // AiReviewAgent (core)
   @Override
   protected Predicate<SmartToolExecutor> getToolFilter() {
       return super.getToolFilter().and(t -> !t.getTool().isEditTool() || t.getTool() instanceof ShellTool);
   }
   ```
   Begründung: Die Whitelist deckt die **ganze** ShellTool-Klasse (beide Methoden) → Typprüfung
   ist der engste, compile-gebundene Ausdruck (Präzedenz: `noPrivilegedTools` BuildPoAgentComponent:90,
   SharedToolsComponent:53). Namens-Konstanten wären hier nur eine schwächere Kopie des Typs.
2. **R-TF-3 — Konstanten genau am Scoping des Docs:** `ShellTool` bekommt
   ```java
   public static final String OPERATION_SYSTEM_INFORMATION = "readOperationSystemInformation";
   public static final String SHELL_RUN_COMMAND = "shellRunCommand";
   @Tool(name = OPERATION_SYSTEM_INFORMATION, value = "Read OS and environment info: …")
   @Tool(name = SHELL_RUN_COMMAND, value = "Run a shell command (mvn, npm, git). …")
   ```
   (eine Quelle für `@Tool(name=…)` + Test-Assertions; ADR-0048-Ergänzung). **Kein** Umbau anderer
   Tool-Klassen — PlanTool braucht keine Konstanten: Filter-Ausschluss ist `instanceof` (Typ, nicht
   String) und die Test-Assertions zählen PlanTool-Executors per Typ (s. 4.).
3. **R-TF-2 — Ausschluss im Plugin, nicht im Core:** `SharedToolsComponent`-Konstruktor (:52-54)
   erweitert den bestehenden `setFilter`-Chain:
   ```java
   sa.setFilter(sa.getFilter().and(e -> !(e.getTool() instanceof AskUserTool)
              && !(e.getTool() instanceof WorkspaceMemoryTool)
              && !(e.getTool() instanceof PlanTool)));
   ```
   Begründung: (a) `PlanTool` ist eine Plugin-Klasse — ein Core-Default-Filter könnte sie nicht
   typprüfend ausschließen (Abhängigkeitsrichtung core→plugin wäre falsch); (b) der Shared-Service
   ist der **einzige** Ort, wo PlanTool und SearchAgentTool zusammenleben (PeonAiService:130-131);
   (c) gleicher Präzedenz-Codeblock wie die zwei existierenden Ausschlüsse — kein neuer Mechanismus.
   Der Core-Default-Filter bleibt unverändert (Custom Agents, headless Services unverändert).
4. **R-TF-4 — Matrix-Tests typbasiert für die „niemals"-Mengen:** Presence-Assertions nutzen die
   Shell-Konstanten (R-TF-3) + die Doc-Methodennamen als Strings; Absence-Assertions gehen per
   `instanceof` auf `e.getTool()` (EclipseWorkspaceWriteFileTool, DiskFileWriteTool, JavaDebugTool,
   AskUserTool, WorkspaceMemoryTool, DocsIdTool) — robust gegen Umbenennungen, keine Literale in
   den „niemals"-Listen. plan*-Keinsehen per `PlanTool`-Typzählung (0 aktive PlanTool-Executors).

## 3. Architektur

```mermaid
componentDiagram
    direction LR
    subgraph core
        AiReviewAgent -->|getToolFilter: !isEditTool || instanceof ShellTool| ShellTool
        SearchAgentTool|>AbstractTool
        note right of SearchAgentTool : Core-Default-Filter\nUNVERÄNDERT
    end
    subgraph plugin
        SharedToolsComponent -->|setFilter-Chain +!(instanceof PlanTool)| SearchAgentTool
        PeonAiService -->|addTool| PlanTool
        BuildPoAgentComponent -->|reviewSlave erbt Whitelist| AiReviewAgent
    end
```

Kein neuer Mechanismus: beide Fixes sind Prädikat-Erweiterungen an bestehenden Filter-Stellen;
`ToolService`/`SmartToolExecutor`/`getToolNameFilter` bleiben unangetastet.

## 4. Inkremente (je für sich grün; Ground Truth: Surefire core + Plugin-PDE)

| # | Deliverables | Tests (konkrete Namen, UC-ID als Kommentar) | Polarität |
|---|---|---|---|
| **1 — Core: Da-Dok-Shell-Whitelist** (R-TF-1, R-TF-3) | `ShellTool`: 2 Namskonstanten + `@Tool(name=…)`; `AiReviewAgent.getToolFilter()`: `\|\| t.getTool() instanceof ShellTool`; `getWriteValidator()`/DENY_ALL **unverändert** | `AiReviewAgentTest` (Core, JUnit 5 + AssertJ, `ToolService(false)` + `addTool(new ShellTool())` + ein `DiskFileWriteTool` als Edit-Kontrolle): **`reviewAgentSeesBothShellMethods`** (`// UC-TF-1`), **`reviewAgentStillHidesOtherEditTools`** (`// UC-TF-2`: DiskFileWriteTool-Executors inaktiv, ShellTool-Executors aktiv) | nur-hinzufügen |
| **2 — Plugin: Sniffa plan*-frei + Tool-Matrix** (R-TF-2, R-TF-4) | `SharedToolsComponent`-Konstruktor: `setFilter`-Chain + `!(instanceof PlanTool)` (Import PlanTool) | `PeonAiServiceTest` (Plugin, JUnit 4, keine externen Assertion-Libs, Muster UC-DL-48…51, `assumeTrue(isWorkspaceAvailable())`): **`daDokMatrixKeepsShellAndWorkTools`** (`// UC-TF-1, // UC-TF-4`: für `aiService.getAgent(AiReviewAgent.NAME)` UND `jonDelegate().getReviewSlave()` — `activeToolNames` enthält `ShellTool.OPERATION_SYSTEM_INFORMATION`, `ShellTool.SHELL_RUN_COMMAND` (Konstanten!), `eclipseRunJavaTests`, `eclipseReadProjectProblems`, `eclipseBuildProject`, `lintDocs`, `lintDocsAndTests`; aktive PlanTool-Executors (Typzählung) == 4). **`daDokMatrixNeverPrivileged`** (`// UC-TF-2`: **RAM-Slave** — keine aktiven Executors von EclipseWorkspaceWriteFileTool / DiskFileWriteTool / JavaDebugTool / AskUserTool / WorkspaceMemoryTool / DocsIdTool, per `instanceof`; **Standalone-Peon-Review** — nie Write-Tools/JavaDebugTool/nextIds, aber memory* (4 Methoden) + askUser sichtbar. **Entscheidung Option A, Paul 2026-09-26:** UC-TF-2 gilt nur für den RAM-Sklaven (noPrivilegedTools = Sklaven-Regel); Standalone verhält sich wie alle Standalone-Agenten — SOLL von Jon in `docs/agent-tool-filter.md` nachgetragen (R-TF-1-BDDs, UC-TF-2, R-TF-4). askUser-Assertion für Standalone auf Presenter-Service (`newServiceWithPresenter`, askUser sonst nicht im Graph: empty = unset). **`searchAgentFilterHidesPlanTools`** (`// UC-TF-3`: `aiService.getSharedToolService()` — alle `PlanTool`-Executors (Typ) `filter.test(...) == false`, `eclipseGrepFiles`-Executor `== true`) | nur-hinzufügen |

**Bestands-Matrix-Tighten:** nicht nötig (Fakt 7: `assertDocsLinterOnly` & Co. sind lockert).

## 5. Regeln & Constraints

- **AGENTS.md:** ein Verhalten eine Implementierung (Filter = Standard-Mechanik, keine zweite
  Parallel-Whitelist); ein Tool lügt nicht über seine Limits; Surefire core + PDE-Plugin =
  Test-Ground-Truth.
- **Keine Docs-Änderungen im Build** (agent-tool-filter.md, review-agent.md, ADR-0048 gehören Jon).
- **UC-IDs** als Kommentare an den Tests (`// UC-TF-1` …) — genau die IDs aus §4, nichts anderes.
- **Namen:** Doc-Matrixnamen (`eclipseRunJavaTests` …) exakt verwenden; weicht ein Name am Code ab
  → STOP-AND-ASK (F8-Prinzip: API-Vertrag aus dem Source lesen, nie raten).
- **Plugin-Testlauf:** Vor JUnit-Lauf `eclipseBuildProject` (stale Bundle-Klassen); **erster**
  PDE-Lauf braucht die manuelle Workspace-Trust-Bestätigung (sonst Hang, „0 tests ran") — ganze
  Suite starten, nie parallel nachstarten.
- **Git:** dedizierter Zyklus-Branch (Name mit Paul abstimmen, vorschlag `story/agent-tool-filter`),
  Commit nach jeder grünen Iteration; finaler Merge = User-Entscheidung.
- **Scope-Hartgrenzen:** kein `getToolNameFilter` (MCP) Touch, keine PlanReadTool/Jon-Kuration,
  keine Naming-Uniformität (`planUpdate`→`planEdit` geparkt), keine neuen Fassaden, Custom-Agent-
  Filter (`CustomAgentService`) unverändert (Shell bleibt dort unsichtbar).

## 6. BDD → Tests (docs/agent-tool-filter.md, 1:1)

| UC | BDD (Doc) | Test |
|---|---|---|
| UC-TF-1 | Da Dok: `readOperationSystemInformation` + `shellRunCommand` sichtbar (R-TF-1) | `AiReviewAgentTest#reviewAgentSeesBothShellMethods` + `PeonAiServiceTest#daDokMatrixKeepsShellAndWorkTools` (R-TF-1 + R-TF-4) |
| UC-TF-2 | Da Dok **als RAM-Sklave**: Write-Tools, JavaDebugTool, AskUserTool, WorkspaceMemoryTool unsichtbar; Standalone behält memory*/askUser (Option A); DENY_ALL unverändert (R-TF-1) | `AiReviewAgentTest#reviewAgentStillHidesOtherEditTools` + `PeonAiServiceTest#daDokMatrixNeverPrivileged` (Validator: Bestandstest `reviewAgentReturnsDenyAllValidator` deckt) |
| UC-TF-3 | SearchAgent: keine plan*-Tools, READ-Tools unverändert (R-TF-2) | `PeonAiServiceTest#searchAgentFilterHidesPlanTools` |
| UC-TF-4 | Da-Dok-Matrix: Konstanten-Assertion, plan* (alle 4) gehalten, „nie"-Menge leer (R-TF-3/4) | `PeonAiServiceTest#daDokMatrixKeepsShellAndWorkTools` + `daDokMatrixNeverPrivileged` (R-TF-3 implizit: Assertion referenziert `ShellTool.OPERATION_SYSTEM_INFORMATION`/`SHELL_RUN_COMMAND`) |

R-TF-2-BDD „Da Dok/Da Thinka/Da Mek plan*-Sichtbarkeit unverändert" → Bestandstests
(UC-DL-50 + neue `daDokMatrixKeepsShellAndWorkTools` plan*-Teil) decken; kein neuer Test nötig.

## 7. Testinventar (per Grep verifiziert, Lektion R16)

Betroffen/überprüft: `PeonAiServiceTest` (UC-DL-48…52, `activeToolNames`, `assertDocsLinterOnly` —
locker, grün), `SharedToolsComponentTest` (`test_sharedTools_searchAgentFilter_excludesAskUserAndMemory`,
`webGetFilteredFromSearchAgent`, `javaDebugToolIsEditToolFilteredFromReadOnlyAgents` — alle
grün, PlanTool nicht im Test-Service), `SearchAgentToolTest` (Core, grün), `AiReviewAgentTest`
(erw. neue Tests, Bestand grün), `CustomAgentServiceTest` (Custom Agents `!isEditTool` — unverändert,
grün), `ShellApprovalServiceTest`/`PeonAiServiceTest`-Shell-Nutzung (rufen `shellRunCommand` direkt,
Filter-agnostisch, grün). Kein Test referenziert heute `readOperationSystemInformation`/`shellRunCommand`
als Tool-Name-Literal in einer Matrix (Grep: nur direkte Aufrufe) → `@Tool(name=…)`-Bindung ändert
keinen sichtbaren Namen (Default = Method-Name).

## 8. Offene Fragen

- keine blockierend.
- Branch-Name für den Zyklus: Vorschlag `story/agent-tool-filter` (Paul 2026-09-25-Regel: Branch-
  Namen abstimmen; ohne Gegenwort so starten).
- **Erledigt (Paul 2026-09-26):** STOP-AND-ASK zu `daDokMatrixNeverPrivileged` rot (Standalone sah
  memory*/askUser) → **Option A**: UC-TF-2 gilt nur für den RAM-Sklaven; Standalone behält
  memory*/askUser (IST stimmig, kein neuer Mechanismus). SOLL von Jon nachgetragen in
  `docs/agent-tool-filter.md` (R-TF-1-BDDs, UC-TF-2, R-TF-4-never-list auf RAM-Sklave gescoped).
- **inc-2 committed (9d1153f):** PDE 291/0/0 (PeonAiServiceTest 61/0/0, keine Skips); Core an HEAD
  966/966 (inc-1). **Ausgeklammert, Entscheidung ausstehend:** Working-Tree-Change an
  `AbstractAgent.java:257` (Queued-Marker `(queued HH:mm)` → `(HH:mm)`) — stammt aus dem gepoppten
  Stash des Compact-Zyklus, existiert in keiner Commits/Branch-Version, bricht 2 Core-Tests
  (`AbstractAgentTest`: `testQueuedMessagesChainedFifo` + 1 weiterer, erwarten `queued 14:32` pro
  Rule-9-Javadoc). Frage an Paul: revert (meine Empfehlung: Stash-Artefakt) oder beabsichtigt
  (dann: Tests + Rule-9-Format nachziehen, eigener Scope)?

---

## 10. Da-Dok-Review (2026-09-26, Story Agent-Tool-Filter, inc-1 `9e1366a` / inc-2 `9d1153f`+`acf675c`)

**Verdict: CONCERNS** — SOLL == IST für alle 4 Regeln; keine Code-Re-Arbeit. Befunde:

### Befunde (non-blocking)

1. **PRAEFIX_FEHLT ×8 in `docs/agent-tool-filter.md` (R-TF-1…4, UC-TF-1…4)** — der Doc trägt
   `idPrefix: TF` nur als fettes Markdown im Header-Zitat (Zeile 3), **nicht** als YAML-Front-Matter
   (`---\nidPrefix: TF\n---` wie `docs/docs-linter.md:1-3`). Der Docs-Linter erkennt den Präfix
   daher nicht und meldet alle 8 IDs als PRAEFIX_FEHLT → UC-/Rule-Tracking des Linters für diese
   Story-Doku ist faktisch inaktiv. **Owner: Jon (Doku), kein Story-Code** — Fix vor Status-Flip ✅.
   (Alle übrigen Lint-Befunde im Run: `docs-linter.md` UNBELEGT_ERLEDIGT ×32,
   `compact-context-counter.md` DOPPELT_DEFINIERT ×5, `compact.md` PRAEFIX_FREMD ×6,
   `compact-lock.md`/`java-debugger.md` — Bestand, andere Stories, nicht dieser Zyklus zurechenbar.)
2. **Working-Tree-Rotz (verifiziert, §8-Offene-Frage noch offen):** Die uncommitted
   Queued-Marker-Änderung an `AbstractAgent.java` ist weiterhin im Working Tree — Core-Lauf
   2026-09-26: 985 Tests, **2 rot** (`AbstractAgentTest#callNullInitialWithQueuedProcessesQueueAsPayload`
   + `#testQueuedMessagesChainedFifo`, erwarten `[Queued Message] (queued HH:mm)`, IST
   `(HH:mm)`). Story-eigener Code ist davon unberührt — aber der Branch ist so **nicht mergbar**,
   bis Paul entscheidet (Revert = Empfehlung im Plan).

### Verifikation (IST, nicht Dev-Claim)

- **Core (eclipseRunTests 2026-09-26):** `AiReviewAgentTest` 6/6 grün (inkl. neu
  `reviewAgentSeesBothShellMethods`, `reviewAgentStillHidesOtherEditTools`); Full-Core 983/985 grün
  (nur die 2 §8-Queued-Failures). Build core + Plugin: 0 Errors, nur Bestands-Warnings.
- **Plugin (PDE):** `PeonAiServiceTest` **61/61 grün** (inkl. neu
  `daDokMatrixKeepsShellAndWorkTools`, `daDokMatrixNeverPrivileged`, `searchAgentFilterHidesPlanTools`);
  `SharedToolsComponentTest` 8/8 grün (PlanTool-Änderung bricht keinen Bestandstest).
- **Plan↔Code:** alle Deliverables + Test-Namen 1:1 vorhanden. `ShellTool.OPERATION_SYSTEM_INFORMATION`
  /`SHELL_RUN_COMMAND` (ShellTool:28-29) sind Single Source für `@Tool(name=…)` (:58, :70);
  `AiReviewAgent.getToolFilter()` (AiReviewAgent:77) = exakt Plan-§2.1; `getWriteValidator()` DENY_ALL
  unverändert; `SharedToolsComponent:56-58` = exakt Plan-§2.3; `SearchAgentTool`-Core-Default-Filter
  (:26-28) unverändert; `BuildPoAgentComponent:102-106` Review-Slave erbt Whitelist + `noPrivilegedTools`.
- **Docs↔Code (inkl. Option A):** R-TF-1 beide BDD-Varianten getestet (Sklave: 6 privileged-Typen
  nie; Standalone: memory* (4 Methoden) + askUser via `newServiceWithPresenter` sichtbar) —
  Doc↔Test↔Code **konsistent** (Sonderfall 1 erledigt). R-TF-2: alle 4 PlanTool-Executors fallen
  durch den Filter, `eclipseGrepFiles` bleibt. R-TF-3: Konstanten-Assertions in beiden Modulen,
  keine Literale bei Shell-Namen; Work-Tool-Literale erlaubt (Doc: „Scoping R-TF-3"). R-TF-4:
  `nextIds` über `DocsIdTool`-Typzählung + `assertDocsLinterOnly` abgedeckt.
- **ADR-0048-Ergänzung 2026-09-25 + `docs-linter.md` Q5:** vorhanden und mit R-TF-3/agent-tool-filter.md
  konsistent (Sonderfall 4 OK).

### Plan-coverage-gap (Checkliste 4 — nicht Da-Mek-Re-Arbeit)

- R-TF-2-Dritten-BDD („Da Dok/Da Thinka/Da Mek plan*-Sichtbarkeit unverändert"): **kein direkter
  Test** für Da Thinka/Da Mek. Plan-§6 zitiert dafür UC-DL-50 — inakkurat, UC-DL-50
  (`docsLinterMatrixForDaThinka`) assertet nur den Docs-Linter, nicht plan*. Strukturell geschützt
  (geändertes Prädikat sitzt ausschließlich im `SearchAgentTool`-Filter, den diese Agenten nicht
  konsumieren) — SOLL bleibt erfüllt, aber eine einzeilige Plan-Agent-Matrix-Assertion (4 aktive
  PlanTool-Executors) wäre der ehrliche Beleg.

### Mutation-Check / Skill-Gaps

- Mutation-Check nicht nötig: Filter-Prädikate sind simpel, Tests asserten bidirektional
  (Presence + Absence).
- Keine Skill-/Instruction-Gaps.

**Most likely reason this breaks later:** der Story-Doc-Präfix wird nie erkannt, weil das
Front-Matter fehlt — beim Status-Flip ✅ führt der Linter alle 8 UCs als Rauschen und jede spätere
Änderung an R-TF-* bleibt ungetrackt. **Änderung, die das Risiko am stärksten senkt:**
`idPrefix: TF` als YAML-Front-Matter in `docs/agent-tool-filter.md` (Jon, 2 Zeilen) + parallel
die §8-Queued-Frage bei Paul abschließen, damit der Branch mergbar ist.

### Auflösung der CONCERNS (Da Mek, 2026-09-26)

- **Befund 1 (PRAEFIX_FEHLT):** erledigt in inc-3 (`6a96622`) — Jon hat `idPrefix: TF` als
  YAML-Front-Matter in `docs/agent-tool-filter.md` fixiert, im selben Commit.
- **CONCERNS 3 (R-TF-2 plan*-Coverage-Gap Da Thinka/Da Mek):** erledigt in inc-3 (`6a96622`) —
  `daDokMatrixKeepsShellAndWorkTools` assertet zusätzlich 4 aktive PlanTool-Executors für
  `AiPlanAgent` (Da Thinka, Filter `!isEditTool`) und `AiDevAgent` (Da Mek, Filter `p -> true`).
  PDE nach inc-3: 291/0/0 (PeonAiServiceTest 61/61).
- **Befund 2 (Queued-Marker / AbstractAgent.java):** OFFEN — wartet auf Pauls Entscheidung
  (Revert = Empfehlung, s. §8).
