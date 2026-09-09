# Plan Story A: SkillComponent-Refactor (R14–R16) — llmpeon-parent, branch story/133

> **⚠️ STOP-AND-ASK (User 2026-09-08, prominent):** Da Mek: bei Compile-Fehlern ohne Lösung,
> nicht grün bekommbaren Tests, IST-Widersprüchen zu diesem Plan oder Unklarheiten SOFORT aktiv
> über den askDev-Kanal bei Jon nachfragen. **Nie still workarounden, nie das SOLL ändern.**
>
> **SOLL = docs/project-skills.md, Abschnitt "Component-Refactor (Follow-up, 2026-09-09)", R14–R16
> (verbatim im Prompt-Request).** Glossar: Begriff ist "Skill-Component"; "Skill-Slot" ist in neuem
> Code/Docs verboten.

## 0. Branch & Verifizierung (zuerst!)
- `git branch --show-current` — Regel 23: Zustand selbst prüfen. Erwartet: `story/133`
  (User-Branch, vgl. memory #23). Wenn anders/kein Branch → STOP-AND-ASK, nicht still umbenennen.
- Commit nach **jedem** grünen Increment: `inc-N: <summary>` + Trailer
  `Assisted-by: Peon AI (<ModelName>)`. Docs/**-Änderungen von Jon im selben Commit mitschieben.
- Maven Surefire = ground truth (core). Plugin-Tests: vor jedem Lauf `eclipseBuildProject` auf
  geänderten Projekten (stale bin/ → ClassNotFoundException); erster Lauf braucht einmalig
  Workspace-Trust (memory #13) — ganze Suite starten, nicht parallel nachstarten.

## 1. Kontext
Follow-up des Project-Skills-Features (R1–R13 ✅). drei Nachzieh-Regeln:
- **R14** `SkillSlot` → `SkillComponent`; eine Component hat **immer einen Pfad** (kein
  `@Nullable dir`); "kein Projekt" = Service tauscht auf eine **leere Component**-Instanz.
  Zwei Service-Felder (Config + Projekt) bleiben; nur die Projekt-Component wird getauscht;
  **kein Component-Cache pro Pfad** — frischer Load bei jedem Projektwechsel.
- **R15** nach erfolgreichem Scaffold-Disk-Write ruft Code deterministisch
  `skillService.refreshAll()` (nicht LLM-getrieben, kein Path-Sniffing). ReloadConfigTool
  bleibt manueller Reload.
- **R16** Jon (AiPoAgent) bekommt den **geteilten** SkillTool-Instanz in seine curated
  po-Tool-Liste (reine Wiring-Lücke).

## 2. IST — verifiziert (nicht neu recherchieren)
- `SkillSlot.java` (core, `org.sterl.llmpeon.skill`): `private volatile @Nullable Path dir` (:27),
  `setPath(@Nullable Path)` (:40–44, null → `Map.of()`), `path()` nullable, `refresh()`,
  `skills()`, `loadedSkillCount()`, private `load(@Nullable)` mit null-Branch.
- **Call-Sites verifiziert (Regel 18):** `new SkillSlot` nur in `SkillService` (2 Feld-Inits,
  :36–37); `SkillSlot.setPath` nur intern von `SkillService.refresh`/`setProjectSkillsDir`
  (keine externen Aufrufer). "Slot"-Wort in Testnamen: nur
  `PeonAiServiceTest.pinnedProject_keepsSkillSlot_untilSetProject` (+ docs-Tabelle).
- `SkillService`: Felder `configSlot/projectSlot`; `setProjectSkillsDir(@Nullable)` mutiert
  Projekt-Slot in place (:72–74); `refresh(@Nullable Path)` mit Guard
  `newPath == null && configSlot.path() == null → false`; `refreshAll()` refreshed beide;
  `effectiveView()` config-first + project-override. Javadoc erwähnt "slots".
- `AiScaffoldAgent` (core, `scaffold/`, **nicht** `agent/`): 1-arg Ctor
  `(ConfiguredChatModel)`, eigenes `ToolService(false)` mit eigenem `DiskFileWriteTool(configDir)`
  (privat), `addTool(SmartTool)`, `getWriteValidator()` = DynamicRootsWriteValidator (R12).
  **Kein Write-Hook heute.** Erzeugt in `PeonAiService` :146 (`scaffoldAgent = new
  AiScaffoldAgent(configuredModel)`), `skillService` existiert dort schon (:119).
- `DiskFileWriteTool` (core `tool/tools/`): @Tool-Methoden `diskWriteFile`, `diskDeleteFile`,
  `diskReplaceLines`, `diskEditFile`, … (alle mutierenden Methoden durchzählen!) — keine
  After-Write-Callback-Infrastruktur.
- `SharedToolsComponent` :40: `sharedToolService.addTool(new SkillTool(skillService))` —
  **immer** vorhanden (geteilte Instanz).
- `BuildPoAgentComponent.build()` (plugin `parts/ai/component/`): Jon's `poToolService` holt
  geteilte Tools per `sharedToolService.getTool(X.class).get()` ( garantiert vorhanden) bzw.
  `ifPresent` (AskUserTool, optional). **SkillTool fehlt.** AiPoAgent erhält poToolService im Ctor.
- `AbstractAgent`/`AiAgent` haben public `getToolService()`. `AgentService.getAgents()` existiert
  (PeonAiService :182 nutzt sie).
- Tests (Pin-Status): `SkillServiceTest` — zweiComponents_projectSwitchReplacesProjectComponent
  (:190), configComponentNeverMutated_maskedSkillReturns (:217, setProjectSkillsDir(null) :238),
  reloadRefreshesEveryComponent (:265), swapIsAtomic_noPartialMapVisible (:296),
  failedRefreshKeepsPreviousState (:419), writeSkill-Helper (flat `*.md` mit YML-Frontmatter).
  Plugin `PeonAiServiceTest`: test_has_read_skill_tool (:237, aktiver Agent),
  pinnedProject_keepsSkillSlot_untilSetProject (:1446). `BuildPoAgentComponentTest` existiert NICHT.

## 3. Design-Entscheidungen
1. **SkillComponent (R14-Kern):** Ctor **ohne** Pfad = leere Component
   (`dir = EMPTY_PATH`, `skills = Map.of()`, **kein Load → kein checked Exception im
   Feld-Initializer des Service**). Zweit-Ctor `(SkillSource, Path)` delegiert auf `setPath`
   (lädt sofort, IOException fliegt). `setPath(Path)` **non-null** (lädt neu), `path()` gibt
   **nie null** zurück. `EMPTY_PATH = Path.of("peon-empty-skills-placeholder")` — dokumentierter
   Platzhalter, nie ein existierendes Skills-Verzeichnis; `load()` liefert dafür leer
   (`Files.isDirectory` false). Javadoc-Kontrakt: "always has a path; the empty component's path
   is a deliberate placeholder, never a real skill dir". *(Jon kann das beim Review veto'en —
   BDD verlangt nur path() != null + leere View; Platzhalter ist die ehrlichste Form davon.)*
2. **Service drückt Abwesenheit aus (SOLL):** Felder `configComponent/projectComponent`,
   Initial `new SkillComponent(SkillSource.CONFIG/PROJECT)` (= leer).
   `setProjectSkillsDir(@Nullable)`: null → **neue leere Instanz**, sonst `new
   SkillComponent(PROJECT, dir)` — Instanz-Tausch, kein in-place setPath, kein Cache.
   `refresh(@Nullable Path)`: null → wenn Config-Component leer ist (path == EMPTY_PATH)
   No-op/false, sonst Tausch auf leere Instanz + true; nicht-null → wie heute setPath.
   `refreshAll()`/View/Komposition unverändert (nur umbenannte Felder).
   **Kein Cache pro Pfad** — bewusst, Staleness-Risiko > Ladekosten (SOLL-Wortlaut).
3. **R15:** `DiskFileWriteTool` bekommt `volatile @Nullable Runnable afterWrite` +
   `setAfterWrite(...)`, gefeuert am Ende **jeder** erfolgreichen mutierenden @Tool-Methode
   (nach dem Write, vor dem Return; bei Exception nie). `AiScaffoldAgent` bekommt 2-arg Ctor
   `(ConfiguredChatModel, @Nullable SkillService)`: non-null →
   `diskFileWriteTool.setAfterWrite(() -> { try { skillService.refreshAll(); } catch
   (IOException e) { throw new RuntimeException("Write succeeded but skill refresh failed: "
   + e.getMessage(), e); } })` — Write ist geglückt, Refresh-Fehler muss zum LLM durchschlagen
   (Tool-Honesty: kein stiller stale View). 1-arg Ctor bleibt (delegiert null) für bestehende
   Tests/Headless. Wiring: `PeonAiService` :146 → `new AiScaffoldAgent(configuredModel, skillService)`.
4. **R16:** In `BuildPoAgentComponent.build()`: `poToolService.addTool(sharedToolService
   .getTool(SkillTool.class).get())` — Style wie die garantiert vorhandenen Eclipse-Tools
   (SkillTool wird in SharedToolsComponent unconditionally geadded). Import `SkillTool` ergänzen.
5. **Test-Zugriff auf Components:** `SkillComponent projectComponent()` **package-private** in
   SkillService (Test liegt im selben Package) + Javadoc "visible for tests". Kein public API-Zuwachs.

## 4. Architecture / Datenfluss
SkillComponent (path + eigene Map, atomarer Swap) bleibt die Deep-Unit; SkillService komponiert
zwei Component-Instanzen. Abwesenheit lebt **ausschließlich im Service** (leere Instanz), nie in
einer null-Path-Component. Scaffold-Write-Pfad: LLM → DiskFileWriteTool.@Tool → Files.write ok →
afterWrite → SkillService.refreshAll() → nächste View sieht den Skill. Jon liest Skills über die
**geteilte** SkillTool-Instanz (gleiche Service-View wie alle Sklaven).

## 5. Betroffene Dateien (vollständig)
| Increment | Datei | Änderung |
|---|---|---|
| inc-1 | `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/skill/SkillSlot.java` → `SkillComponent.java` | Rename Klasse + Javadoc (Slot→Component-Worte) |
| inc-1 | `.../skill/SkillService.java` | Typ/Feld-Rename (configSlot/projectSlot → configComponent/projectComponent), Javadoc-Worte |
| inc-1 | `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/PeonAiServiceTest.java` | Testname `pinnedProject_keepsSkillSlot_untilSetProject` → `pinnedProject_keepsSkillComponent_untilSetProject` |
| inc-2 | `.../skill/SkillComponent.java` | non-null dir/setPath/path, EMPTY_PATH, 2 Ctors (s. D1) |
| inc-2 | `.../skill/SkillService.java` | Instanz-Tausch in setProjectSkillsDir/refresh, Guard ohne null-Path, `projectComponent()` package-private |
| inc-2 | `.../core/src/test/java/org/sterl/llmpeon/skill/SkillServiceTest.java` | 2 neue Tests + ggf. Anpassung falls L1–170 einen refresh(null)-Pin enthält (erst ganze Datei lesen!) |
| inc-3 | `.../tool/tools/DiskFileWriteTool.java` | afterWrite-Hook, feuern in allen mutierenden @Tool-Methoden |
| inc-3 | `.../scaffold/AiScaffoldAgent.java` | 2-arg Ctor + Verdrahtung |
| inc-3 | `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/ai/PeonAiService.java` | :146 2-arg Ctor |
| inc-3 | `.../core/src/test/java/org/sterl/llmpeon/scaffold/AiScaffoldAgentTest.java` | neuer Test |
| inc-4 | `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/ai/component/BuildPoAgentComponent.java` | SkillTool in poToolService |
| inc-4 | `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/PeonAiServiceTest.java` | neuer Test |

Nicht anfassen: SkillTool, SkillPromptFile, SkillSource, ReloadConfigTool (bleibt manueller
Reload), ADR-Docs (Jon).

## 6. Increments (jeweils grün + Commit)
### inc-1 — Mechanischer Rename SkillSlot → SkillComponent
- Ziel: Begriff/Typ wechseln, Verhalten 0 Änderung.
- Files: s. Tabelle inc-1. Eclipse Rename (alle Refs mitziehen — Grep "SkillSlot" danach: 0 in
  src/main+src/test; Surefire-Reports/target ignorieren).
- Tests: laufende Suite bleibt grün (reine Renames). Neuer Testname im Plugin-Test → Regeln unten.
- Commit: `inc-1: rename SkillSlot to SkillComponent (glossary R14)`

### inc-2 — R14 non-null Pfad-Contract + leere Component + kein Cache
- Ziel: `dir` nie null; "kein Projekt" = leere Component-Instanz; frischer Load je Wechsel.
- Verhalten (s. D1/D2): setProjectSkillsDir(null) → leere Instanz; refresh(null) No-op nur wenn
  Config bereits leer; path() nie null; jede setProjectSkillsDir(x≠null) = neue Instanz + Load.
- Tests (SkillServiceTest, GIVEN/WHEN/THEN-Kommentare, AssertJ):
  - `noProjectUsesEmptyComponent_pathNeverNull`: GIVEN frischer Service WHEN View berechnet THEN
    View = Config-Skills, `projectComponent().path()` != null, `projectComponent().skills()` leer;
    AND nach `setProjectSkillsDir(null)` erneut path() != null + leer.
    (Rot ohne Feature: Accessor fehlt → kompiliert nicht; alt: path() == null.)
  - `projectSwitchAlwaysFreshLoad_noCache`: GIVEN Projekt A mit Skill `alpha` WHEN A → B → (auf
    Disk zweites A-Skill ergänzen) → A THEN View enthält beide A-Skills; AND
    `projectComponent()` ist nach jedem Wechsel eine **neue Instanz** (isNotSameAs);
    AND A-Skill löschen + nochmals A → Skill weg (frischer Load, kein Cache).
    (Rot ohne Feature: selbe Instanz bei altem Code.)
  - Bestehende Tests (configComponentNeverMutated… :238 `setProjectSkillsDir(null)` etc.)
    müssen unverändert grün bleiben — View-Semantik identisch.
- Commit: `inc-2: R14 non-null path contract, empty-component swap, no cache`

### inc-3 — R15 deterministischer Skill-Refresh nach Scaffold-Write
- Ziel: nach erfolgreichem Scaffold-Disk-Write enthält die effektive View den Skill ohne
  LLM-Zutun (kein ReloadConfigTool-Call nötig).
- Verhalten: s. D3. Alle mutierenden @Tool-Methoden von DiskFileWriteTool feuern afterWrite.
- Test (AiScaffoldAgentTest, core, AssertJ):
  - `successfulWriteTriggersSkillRefreshAll`: GIVEN Scaffold mit SkillService + tmp configDir
    WHEN via `subject.getToolService().getTool(DiskFileWriteTool.class).orElseThrow()
    .diskWriteFile("skills/new-skill.md", <YML-Frontmatter wie SkillServiceTest.writeSkill>)`
    THEN `skillService.get("new-skill")` present — ohne jeden Reload-Call.
    (Rot ohne Feature: Write passiert, Service nie refreshed → get() empty.)
  - AND-step: `diskWriteFile` mit leerem Pfad → IllegalArgumentException, afterWrite **nicht**
    gefeuert (View unverändert).
- Plugin-Verdrahtung (PeonAiService 2-arg) läuft indirekt im Plugin-Suite-Mitlauf mit.
- Commit: `inc-3: R15 refresh skills after successful scaffold write`

### inc-4 — R16 Jon bekommt den geteilten SkillTool
- Ziel: Jons curated po-Tool-Liste enthält dieselbe SkillTool-Instanz wie die Sklaven.
- Verhalten: s. D4.
- Test (PeonAiServiceTest, OSGi JUnit 4, **kein AssertJ** — standard assertNotNull):
  - `test_jon_gets_shared_skill_tool`: GIVEN laufender aiService WHEN der AiPoAgent (Jon) aus
    `aiService.getAgents()` gefiltert wird THEN `jon.getToolService().getTool(SkillTool.class)`
    present und **same instance** wie `aiService.getToolService().getTool(SkillTool.class)`
    (assertSame — "geteilte Instanz" ist Teil des SOLL).
    (Rot ohne Wiring: getTool empty → assertNotNull fails.)
- Commit: `inc-4: R16 wire shared SkillTool into Jon's po tool list`

## 7. Regeln & Constraints
- **Docs (docs/**) gehören Jon:** Da Mek schreibt KEINE Docs. Test-Renames erzeugen Docs-Drift
  in der R-Mapping-Tabelle (project-skills.md, Zeile "R2c (Plugin)") — Jon sync't die Zeile
  (`pinnedProject_keepsSkillComponent_untilSetProject`); Da Mek committet Jon's Docs-Änderung mit.
  R16-Tabellenzeile darf bei `PeonAiServiceTest` bleiben (Variante war in der Tabelle genannt).
- Glossar: "Skill-Slot" nur noch in historischen Docs (ADR-0042); neuer Code/Tests: Skill-Component.
- Log OR throw, nie beides — im afterWrite: throw (Write-OK, Refresh-Fehler sichtbar, s. D3).
- Kein `@Nullable` mehr an Component-dir/setPath/path; `@Nullable` bleibt an
  SkillService-Methodenparametern (Service drückt Abwesenheit aus).
- Thread-Safety: `volatile` auf dir/skills/afterWrite beibehalten; Instanz-Tausch im Service ist
  atomar (ein reference write) — effectiveView-Leser sehen alt oder neu, nie halb (R5 bleibt).
- Test-Honesty: jeder neue Test ist ohne das jeweilige Feature rot (Accessor/Compile, fehlender
  Refresh, fehlendes Wiring) — keine Assertion-Massage.
- Surefire = ground truth für core-Zahlen.

## 8. Test-Strategie
- Core: `mvn -pl org.sterl.llmpeon.core test` (Surefire) nach inc-2/inc-3; SkillServiceTest
  zuerst GANZ lesen (L1–170 ungeprüft: evtl. refresh(null)-/slot-Javadocs-Pins).
- Plugin: vor jedem Lauf `eclipseBuildProject` auf `org.sterl.llmpeon` + `org.sterl.llmpeon.test`;
  ganze Suite; Workspace-Trust beim ersten Lauf (memory #13); Surefire-Reports unter
  org.sterl.llmpeon.test/target sind stale (memory #16) — nicht als Wahrheit lesen.
- Inc-1 ist refactor-safe: Suite grün, kein neuer Test nötig (Rename-Absicherung = Compiler).

## 9. Offene Fragen
- none — SOLL (R14–R16) ist verbatim übergeben; der einzige Deformationsspielraum (Pfad-Wert der
  leeren Component, EMPTY_PATH-Platzhalter) ist als Design-Entscheidung D1 dokumentiert und beim
  Review von Jon veto-bar.
