# Session-Stand (2026-09-10, Eclipse frisch neu installiert — Zyklus `story/lib-update-2026-09-09` läuft)

## Aktiver Zyklus: Dependency-Update (Target 2026-09 + langchain4j 1.20.0)

- Plan: `peon-plan/overview.md` (4 Increments, abgenommen, freigegeben an Da Mek).
- ADR-0044 (`docs/adr/0044-target-2026-09-dependency-update.md`) = SOLL, im ADR-Index.
- **User-Entscheidung:** Target **2026-09**, Branch `story/lib-update-2026-09-09`, ALLE
  Lib-Updates mitnehmen (Major-Jumps nur reporten). Danach autonomer Bug-Hunt-Zyklus
  (Memory-Leak-Hunt + Triage #5–#15 + ApiRetry + AgentOrder-Edge), jeder Bug roter Test zuerst.

### Increments-Stand

1. **inc-1 Housekeeping** — committet `fe761f8`.
2. **inc-2 Target 2026-09 + jakarta.annotation [3.0.0,4.0.0)** — committet (2026-09-10).
   Gate grün: Maven-Resolve (`mvn -pl org.sterl.llmpeon,releng/llmpeon-target compile`),
   `eclipseBuildProject` beide Plugin-Projekte ohne Fehler, Plugin-OSGi-Suite **194/0**.
   Altes Blocker-Thema „IDE-Target rot" = erledigt (frische Eclipse-Installation löst 2026-09
   sauber — siehe resolved-points.md).
3. **inc-3 langchain4j 1.20.0 + beta-Module 1.20.0-beta30** — committet (2026-09-10).
   Gates grün: Core Surefire **699/0**, headless `mvn -pl org.sterl.llmpeon,releng/llmpeon-target package`
   BUILD SUCCESS, `eclipseBuildProject` beide Projekte warnings-only, Plugin-OSGi-Suite **194/0**.
   Änderungen: parent `langchain4j.version` 1.19.0→1.20.0 + neu `langchain4j.beta.version` 1.20.0-beta30
   (darauf `mvn -N install` — sonst stale parent-Pom in ~/.m2); core-pom beta-Module →
   `${langchain4j.beta.version}`; **McpService.java Compile-Fix**: `HttpMcpTransport` in beta30
   entfernt → HTTP + HTTP_SSE beide `StreamableHttpMcpTransport` (MCP-Spec-Nachfolger,
   text/event-stream nativ). lib/-Delta: −okhttp-sse, +kotlin-stdlib-jdk7, +langchain4j-reactive-streaming;
   slf4j-simple.jar war in lib/, aber aus MANIFEST/build.properties/.classpath fehlend
   (Pre-existing seit afbe7984) → alle drei auf 57 Jars gesynct. Whitelist: neu transitives
   `io.smallrye.reactive:mutiny-zero` — nur von `AiServiceStreamingEventPublisher` referenziert
   (unused in core) → kein `includeGroupIds`-Erweiterung nötig, kein Laufzeit-Risiko (an Jon
   berichtet). lib/ + sources/ sind gitignored (regenerierbar) → Commit trägt MANIFEST/
   build.properties/.classpath, Jars via `mvn -pl org.sterl.llmpeon clean process-resources`.
4. **inc-4 übrige Pins + slf4j-simple-Entdoppelung** — committet (2026-09-10).
   Gates grün: `mvn clean install` am Parent (alle 6 Module) BUILD SUCCESS, Core Surefire
   **699/0/0**, `eclipseBuildProject` beide Projekte warnings-only, Plugin-OSGi-Suite **194/0**.
   Bumped (gleiche Major-Line, Central-Check 2026-09-10): lombok 1.18.44→1.18.48,
   junit-bom 5.12.2→5.14.4, assertj 3.27.3→3.27.7, jimfs 1.3.0→1.3.2,
   slf4j-simple 2.0.18/2.0.17→**2.0.19 via neuer Parent-Property** `slf4j-simple.version`
   (Entdoppelung); Maven-Plugins: compiler 3.15.0→3.16.0, surefire 3.5.5→3.6.0,
   clean 3.3.2→3.5.0, dependency 3.6.1→3.11.0, resources 3.3.1→3.5.0.
   Bereits latest: mockito 5.23.0, flexmark 0.64.8. Neue Majors existieren
   (junit 6, assertj 4, slf4j 2.1, plugins 4.x) — Plan-Scope = gleiche Major-Line → nicht
   gemacht, hier reportet.

### Blocker

Keine. (Ehemaliger IDE-Target-Blocker durch frische Eclipse-Installation gelöst;
askDev-ConnectException war Dev-Modell-Endpoint, nicht Workspace.)

### Befunde (Bug-Hunt-Kandidaten)

- **PeonAiServiceTest löscht das Fixture `test_project/.agents/skills/test/SKILL.md`**
  (gefunden 2026-09-10, inc-2-Gate): die ADR-0042-Tests `setProject_replacesProjectSlotOnly`
  + `pinnedProject_keepsSkillComponent_untilSetProject` räumen in `finally` mit
  `deleteRecursively(fixtureSkillsDir.getParent())` = das GANZE `test_project/.agents`-Verzeichnis
  — inklusive dem getrackten Smoke-Fixture. Folge: nach JEDEM vollen Suite-Lauf taucht die
  Deletion im Working Tree auf (kam beiläufig in den ersten inc-2-Commit, per Amend behoben,
  `89531af`). Fix-Vorschlag: cleanup löscht nur die von den Tests angelegten Files
  (`fixture-skill.md`/`other-skill.md`). → Bug-Hunt-Triage. Bis dahin: Commits nur mit
  expliziten Datei-Listen, nie `git add -A` nach Suite-Läufen.

### Wichtige Fakten (verifiziert, nicht re-verifizieren)

- Simrel jakarta.annotation: 2026-03 = {1.3.5, 2.1.1}, 2026-06/09 = {1.3.5, 3.0.0} —
  `[2.1.0,3.0.0)` matchte nichts → Root Cause des User-Update-Fehlschlags.
- langchain4j latest 1.20.0 (pom war 1.19.0, core compiliert grün); beta-Module
  (mcp, open-ai-official) → 1.20.0-beta30.
- Echter Tycho-Resolve-Probe = `mvn -pl org.sterl.llmpeon,releng/llmpeon-target compile`
  (target-only `validate` ist No-op in Tycho 5). Vor Plugin-Compile: core `install -DskipTests`
  (MDEP-187).
- lib/-Fallen: `overWriteReleases=false` → immer `clean process-resources`;
  dependency:tree gegen `includeGroupIds`-Whitelist diffen.
- Gates: Core Surefire ~699, Plugin-OSGi ~194.
- Pins (Stand nach inc-4, 2026-09-10): langchain4j 1.20.0 + beta-Module 1.20.0-beta30,
  lombok 1.18.48, mockito 5.23.0 (latest), junit-bom 5.14.4, assertj 3.27.7,
  slf4j-simple 2.0.19 (Parent-Property `slf4j-simple.version`), jimfs 1.3.2,
  flexmark 0.64.8 (latest); Maven-Plugins: compiler 3.16.0, surefire 3.6.0, clean 3.5.0,
  dependency 3.11.0, resources 3.5.0. Tycho 5.0.4.

## Nach Session-Restart weitermachen mit

1. Zyklus komplett (inc-1…4 committet) → Review (Da Dok, 3-Seiten, Plan + ADR-0044), danach
   Merge/Squash-Entscheidung User.
3. Danach Bug-Hunt-Zyklus (siehe oben).

## Vorheriger Zyklus (abgeschlossen, Referenz)

- Story/133 gemerged (Squash `e55668b`), Branch stale. Release-Notes-Zeile: Skills
  `.agents/skills` + CRUD-Evolutions-Loop + Usefulness-Footer; Scaffold-Write refresht Skills;
  Jon liest Skills. Homepage-Update fürs Release = ❓ User prüft.
- Nächste Zyklen (Reihenfolge): 1. Memory-Leak-Hunt · 2. Bug-Fix Triage #5–#15 + ApiRetry +
  AgentOrder-Edge · 3. Loop-Bewährung. Geparkt: Query-Caches, Streaming-Präzisierungen,
  Edit-Tools, copy-tools-e2e `diskRenameResource`-Korrektur.
- open-points: ❓ Glossar eager · ❓ buildWithDev-Compact · M2 „slot"-Drift (kosmetisch).
