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
3. **inc-3 langchain4j 1.20.0 + beta-Module 1.20.0-beta30 — NÄCHSTES.**
   Ablauf: parent-pom `${langchain4j.version}` + neu `${langchain4j.beta.version}` →
   core-pom beta-Module → core `mvn clean install` (Surefire ~699) →
   Plugin `mvn clean process-resources` → lib/↔MANIFEST-Diff → dependency:tree-Whitelist-Diff →
   Plugin-Suite grün → Commit (inkl. lib/, sources/, MANIFEST).
4. inc-4 übrige Pins (gleiche Major-Line, Central-Check zuerst) + slf4j-simple-Entdoppelung
   (Parent-Property) — offen.

### Blocker

Keine. (Ehemaliger IDE-Target-Blocker durch frische Eclipse-Installation gelöst;
askDev-ConnectException war Dev-Modell-Endpoint, nicht Workspace.)

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
- Aktuelle Pins (vor inc-4): lombok 1.18.44, mockito 5.23.0, junit-bom 5.12.2, assertj 3.27.3,
  slf4j-simple 2.0.18 (core/test) + 2.0.17 (plugin), jimfs 1.3.0, flexmark 0.64.8;
  Maven-Plugins: compiler 3.15.0, surefire 3.5.5, clean 3.3.2, dependency 3.6.1, resources 3.3.1.

## Nach Session-Restart weitermachen mit

1. `git status` + `git log --oneline -3` — inc-3-Änderungen in Arbeit?
2. inc-3 → inc-4 → Review (Da Dok, 3-Seiten, Plan + ADR-0044).
3. Danach Bug-Hunt-Zyklus (siehe oben).

## Vorheriger Zyklus (abgeschlossen, Referenz)

- Story/133 gemerged (Squash `e55668b`), Branch stale. Release-Notes-Zeile: Skills
  `.agents/skills` + CRUD-Evolutions-Loop + Usefulness-Footer; Scaffold-Write refresht Skills;
  Jon liest Skills. Homepage-Update fürs Release = ❓ User prüft.
- Nächste Zyklen (Reihenfolge): 1. Memory-Leak-Hunt · 2. Bug-Fix Triage #5–#15 + ApiRetry +
  AgentOrder-Edge · 3. Loop-Bewährung. Geparkt: Query-Caches, Streaming-Präzisierungen,
  Edit-Tools, copy-tools-e2e `diskRenameResource`-Korrektur.
- open-points: ❓ Glossar eager · ❓ buildWithDev-Compact · M2 „slot"-Drift (kosmetisch).
