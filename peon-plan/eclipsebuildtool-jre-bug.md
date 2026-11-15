# Bug-Analyse: `EclipseBuildToolTest.buildReportShowsProjectErrors` rot — Fixture ohne JRE

**Status:** Analyse fertig, **noch kein Code geändert** (Paul: „kein Fix, nur erklären", danach „kannst du die bugs reparieren" → Stop vor dem Fix).
**Datum:** 2026-11-14 · **Branch:** `main` · Working Tree unverändert außer `docs/memory.md`, `pom.xml` (nicht von mir).

---

## 1. Fehlerbild

`org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/EclipseBuildToolTest.java:260`

```
java.lang.AssertionError: expected missingA error:
Project test_project problems:
The project was not built since its build path is incomplete. Cannot find the class file for java.lang.Object. Fix the build path then try building this project @ line -1 @ file /test_project
The type java.lang.Object cannot be resolved. It is indirectly referenced from required .class files @ line 1 @ file /test_project/src/org/sterl/fixture/Alpha.java
```

Der Seeder (`ErrorSeed.java` mit `missingA`/`missingB`, Zeile 252-253) liefert keine Marker → Assertion 260 **und** 261 rot.

## 2. Verifizierte Evidenz

| # | Behauptung | Beweis | Status |
|---|---|---|---|
| E1 | Fixture ist ein Java-Projekt mit `JRE_CONTAINER` **ohne** Execution Environment | `test_project/.classpath` (gelesen), `.project` hat `javabuilder` + `javanature` | ✅ verifiziert |
| E2 | Runtime-Workspace hat **kein Default-JRE** | `/Users/sterlp/junit-workspace/.metadata/.plugins/org.eclipse.core.runtime/.settings/org.eclipse.jdt.launching.prefs`: `PREF_VM_XML` mit `defaultVM=""`, zwei VMs gelistet | ✅ verifiziert |
| E3 | Launch-Workspace = `/Users/sterlp/junit-workspace` (persistent, nicht frisch) | Console-Log des PDE-Launches: `-data /Users/sterlp/eclipse-workspace/../junit-workspace` | ✅ verifiziert |
| E4 | Der Compiler hat die Seeder-Fehler **gesehen** | `javap`/`strings` auf `test_project/bin/org/sterl/fixture/ErrorSeed.class`: Error-Stub enthält `missingA cannot be resolved to a variable`, `missingB …` **und** `The type java.lang.Object cannot be resolved…` | ✅ verifiziert (Indiz dafür, dass JDT intern kompiliert hat; kein Beweis dafür, welche Marker JDT publiziert) |
| E5 | Das Tool liest ausschließlich Marker aus | `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/EclipseBuildTool.java:281-286` → `project.findMarkers(IMarker.PROBLEM, true, DEPTH_INFINITE)`, kein Build-State, kein `IJavaProject` | ✅ verifiziert |
| E6 | `libraryInfos.xml`/`.install.xml` erst **während** des Laufs entstanden (14:23:01), Build 14:23:02 | `ls -laT` auf den Metadaten | ⚠️ Indiz, nicht kausal bewiesen |

## 3. Ursache (Kausalkette)

1. `test_project/.classpath` bindet das JRE über den **Default-JRE**-Container (E1).
2. Im PDE-Runtime-Workspace ist kein Default-JRE gesetzt (E2) → `JavaRuntime.getDefaultVMInstall()` liefert `null` → `JRE_CONTAINER` löst auf **leere** Systembibliothek auf.
3. JDT meldet „Cannot find the class file for java.lang.Object" + „The project was not built since its build path is incomplete" und bricht den Build ab → **die Quelltext-Fehler des Seeders werden nie als Marker publiziert** (E4 vs. E2: intern gesehen, extern nicht gemeldet).
4. `eclipseBuildProject` gibt die zwei tatsächlich vorhandenen Marker ehrlich zurück (E5) — die Assertion erwartet Seeder-Fehler → rot.

**Kein Produkt-Bug:** das Tool hat nichts unterdrückt und nichts verschwiegen.
**`mvn clean install` hilft per Definition nicht:** der Zustand liegt im *Laufzeit*-Workspace (`junit-workspace`), nicht in Build-Artefakten.

## 4. Offene Frage — vor dem Fix zu beweisen

`JavaRuntime.initializeVMs()` (Quelle `org.eclipse.jdt.launching`, gelesen) setzt in Schritt 5 explizit „**use the first as the default**", wenn kein gültiges Default eingetragen ist. Dass die Persistenz trotzdem `defaultVM=""` zeigt, passt zu der Annahme, dass die VM-Liste zum Initialisierungszeitpunkt noch **leer** war und die zwei Einträge erst der asynchrone `DetectVMInstallationsJob` (am Ende von `initializeVMs()` gestartet) nachliefert → **Race zwischen VM-Detection und Test-Build**. Erklärt, warum der Test mal grün, mal rot ist.

**Noch nicht gemessen (decisive Probe):** Werte von
`JavaRuntime.getDefaultVMInstall()` und `JavaCore.getClasspathContainer(JRE_CONTAINER, javaProject).getClasspathEntries().length`
zum Zeitpunkt des Builds im Test-JVM. Erst danach Fix-Variante festlegen.

## 5. Korrektur meiner früheren Aussagen (Feedback eines anderen LLMs, geprüft)

- ❌ „`project.delete(false, true, …)` löscht die Fixture-Inhalte von der Festplatte" — **widerspricht dem IST**: `src/**` hatte nach dem Lauf unveränderte mtimes (10.11. 07:57) und `git status` war clean. Javadoc legt die Löschung nahe, empirisch ist sie nicht eingetreten. Nicht angefasst, separates Nachprüfen.
- ⚠️ „`ErrorSeed.class` beweist, dass die Fehlerrichtungen im Report stehen müssten" — der Stub beweist nur die *interne* Kompilierung (E4), nicht die Marker-Publikation.
- ✅ Rest (E1, E2, E5) direkt verifiziert; E6 bleibt Indiz.

## 6. Fix-Optionen (noch nicht freigegeben / nicht gebaut)

| | Idee | Folge |
|---|---|---|
| **A** (bevorzugt) | Harness stellt vor `importProject` ein Default-JRE sicher: `if (JavaRuntime.getDefaultVMInstall() == null) JavaRuntime.setDefaultVMInstall(<registrierte VM>, monitor)`. `saveVMConfiguration` ist in `setDefaultVMInstall` **nicht** enthalten → kein bleibender Workspace-Eingriff. | Test wird determinisch, egal wie die async VM-Detection läuft. |
| **B** | `assumeTrue("no default JRE", JavaRuntime.getDefaultVMInstall() != null)` | Überspringt still → Feature de facto ungetestet. Nur als Rückfall. |
| **C** (zusätzlich zu A) | Im Test zuerst ehrliche Umgebung-Diagnose: Report enthält „build path is incomplete" → *eigene* Assertion mit „Runtime-Workspace ohne Default-JRE, Seeder nie kompiliert" statt der_irreführenden_ „Tool zeigt Fehler nicht". | Failure bleibt ein Failure, aber mit richtiger Aussage. Schwächt die bestehende Assertion nicht ab. |

**Dependency-Check für A/C (erledigt):** `org.sterl.llmpeon.test` ist Fragment von `org.sterl.llmpeon`; dessen MANIFEST `Require-Bundle` enthält bereits `org.eclipse.jdt.launching` → **kein neues Bundle und kein Target-Platform-Change nötig**. API: `JavaRuntime.getDefaultVMInstall()`, `getVMInstallTypes()`, `IVMInstallType.getVMInstalls()/findVMInstall(String)`, `setDefaultVMInstall(IVMInstall, IProgressMonitor)` (alle public, kein `internal`-Import nötig — `StandardVMType.ID_STANDARD_VM_TYPE` wäre internal und wird deshalb vermieden).

**Reihenfolge-Zwang:** die VM muss gesetzt sein, **bevor** `AbstractIntegrationTest.importProjectIntoWorkspace()` das Fixture importiert/baut (`@BeforeClass` der Superklasse läuft vor der Subklasse → der Aufruf muss *in* diese Methode, nicht in eine neue `@BeforeClass`).

## 7. Nebenbefunde (bewusst getrennt, nicht Teil dieses Fixes)

- `AbstractIntegrationTest.java:81` `project.delete(false, true, …)` — Semantik unklar (siehe §5), potenziell destruktiv für die Fixture auf Disk.
- `buildReportShowsProjectErrors` räumt nur `ErrorSeed.java` weg; `bin/org/sterl/fixture/ErrorSeed.class` bleibt im Fixture liegen (git-ignored).
- `@Test(timeout = …)` verlagert den Body vom UI-Thread (memory #50) — hier nicht die Ursache, aber Relevant bei Hänge-Verdacht.

## 8. Nächste Schritte (wiederaufnehmen hier)

1. Probe in `buildReportShowsProjectErrors` (temporär): `getDefaultVMInstall()` + `getClasspathContainer(...).getClasspathEntries().length` in die Assertion-Messages → Lauf über `eclipseRunJavaTests` (**Paul-Regel: Tests über die Eclipse-Tools, nie per shell**; ganze Suite starten, einmalige Workspace-Trust-Bestätigung im UI-Dialog, memory #13; vorher `eclipseBuildProject` über die geänderten Projekte, memory #16).
2. Bei bestätigt `null`: Fix A + C umsetzen, Test erneut fahren.
3. Kern-Konvention beachten: Plugin-Tests brauchen `usePluginTest=true` (OSGi); `eclipseReadProjectProblems` auf `org.sterl.llmpeon.test` nach jedem Edit.
4. Kein ID-Marker am Test geändert (`// UC-OD-5, UC-OD-6` bleiben); falls das Inkrement IDs antastet → `lintDocsAndTests` ins Gate (AGENTS-DEV.md).
