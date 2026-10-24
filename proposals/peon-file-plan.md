# Plan: `PeonFile` — generische Datei-Abstraktion mit Restrictions

**Status:** 🚧 design  
**Ziel:** Eine eigene, schlanke Komponente `PeonFile` im core, die CRUD + Move + Replace abdeckt, mit pluggable Restrictions und Testbarkeit über Jimfs. Eclipse bleibt nur ein Adapter im Plugin.

---

## 1. Ziel & Nutzen

- **Eclipse-Entkopplung:** `PlanTool` (und künftige File-Tools) arbeiten nur gegen `PeonFile`, nie gegen `IFile`/`IResource`.
- **Testbarkeit:** Core-Surefire testet die komplette Plan-/File-Logik mit Jimfs (`Jimfs.newFileSystem(Configuration.unix())`) — kein PDE, kein Eclipse nötig.
- **Generische Restrictions:** Schreibrechte (z. B. „Jon darf nur `docs/**`“, „Tool ist auf Projekt-Dir gepinnt“, „tmp ist beschreibbar“) sind ein eigener, wiederverwendbarer Mechanismus — nicht pro Tool hardcoded.
- **Zukunftssicher:** Headless-/CLI-Einsatz später möglich, ohne neuen Code.

**Nicht-Ziel:** Die bestehenden `disk*`/`eclipse*`-Tools werden in diesem Inkrement **nicht** migriert. Sie bleiben, bis ein konkreter Anlass besteht.

---

## 2. Paketstruktur

```
org.sterl.llmpeon.core/
└── src/main/java/org/sterl/llmpeon/file/
    ├── PeonFile.java              // Interface
    ├── PeonFileRestriction.java   // Interface für Schreib-Regeln
    ├── RestrictionViolation.java  // Exception
    ├── DiskPeonFile.java          // Default: java.nio.file.Path
    ├── InMemoryPeonFile.java      // Test: Map-basiert (oder Jimfs-backed)
    └── restrictions/
        ├── AllowlistRestriction.java   // Glob-Muster (z. B. docs/**)
        ├── ProjectPinRestriction.java  // nur innerhalb eines Root-Dirs
        └── TmpDirRestriction.java      // tmp/ zusätzlich erlaubt

org.sterl.llmpeon/  (Plugin)
└── src/org/sterl/llmpeon/parts/file/
    └── EclipsePeonFile.java       // Adapter: IFile/IFolder + refreshLocal
```

---

## 3. Interface `PeonFile`

```java
public interface PeonFile {
    boolean exists();
    String read();
    void write(String content);
    void move(PeonFile target);
    void delete();
    List<PeonFile> list();          // nur für Ordner, sonst List.of()
    boolean isFolder();

    /** Replace als Default: read → applyEdit → write. Eclipse überschreibt für History. */
    default void replace(String old, String new) {
        write(read().replace(old, new));
    }
}
```

**Design-Entscheidungen:**
- **Kein separates `PeonFolder`-Interface** — ein Flag `isFolder()` reicht für den Plan-/Config-Anwendungsfall. Ein klassisches Composite lohnt sich erst bei rekursiven Baum-Operationen.
- **`replace` als Default-Methode** ist bewusst simpel (read-modify-write). Der Eclipse-Adapter überschreibt sie, um Workspace-History/Undo zu erhalten.
- **Keine globbing/search-Methoden** im Interface — das bleibt in separaten Tools (`PeonGrepTool` etc., falls später benötigt).

---

## 4. Restrictions — generisches Konzept

### Interface

```java
public interface PeonFileRestriction {
    /** @return true wenn der Schreibzugriff erlaubt ist */
    boolean allowsWrite(PeonFile file);
    /** @return Fehlermeldung, wenn nicht erlaubt */
    String denialReason(PeonFile file);
}
```

### Implementierungen

| Restriction | Verhalten | Anwendungsfall |
|---|---|---|
| `AllowlistRestriction` | Glob-Muster (z. B. `docs/**`, `*.md`) | Jon darf nur Docs schreiben |
| `ProjectPinRestriction` | Nur innerhalb eines Root-Dirs (z. B. Projekt-Dir) | Tool ist auf ein Projekt gepinnt |
| `TmpDirRestriction` | `java.io.tmpdir` zusätzlich erlaubt | Tests/Temp-Dateien |
| `CompositeRestriction` | Kombiniert mehrere (AND/OR) | Kombinierte Regeln |

### Wiring

Restrictions werden **an der Composition Root** gesetzt, nicht im Tool selbst:

```java
// Beispiel: PlanTool ist auf Projekt-Dir gepinnt, tmp zusätzlich erlaubt
var restriction = new CompositeRestriction(
    new ProjectPinRestriction(projectDir),
    new TmpDirRestriction()
);
var planFile = new DiskPeonFile(path, restriction);
```

**Regel:** Restrictions prüfen **nur Schreibzugriffe** (`write`, `move`, `delete`, `replace`). Lesen ist immer erlaubt (oder optional auch restriktierbar, falls später benötigt).

---

## 5. Replace — zwei Pfade

### Default (Disk / InMemory)

```java
default void replace(String old, String new) {
    String content = read();
    // Validierung: old muss genau einmal vorkommen (oder Count-Check)
    long count = content.split(Pattern.quote(old), -1).length - 1;
    if (count != 1) throw new IllegalStateException("Expected 1 occurrence, found " + count);
    write(content.replace(old, new));
}
```

- **Count-Check** verhindert versehentliche Mehrfach-Ersetzungen (Konsistenz mit `FileUtils.applyEdit`).
- **Atomic Write:** `DiskPeonFile.write` nutzt `Files.write` mit `StandardOpenOption.TRUNCATE_EXISTING` — für die Plan-/Config-Nutzung reicht das; echte Atomic-Move-Semantik (Temp-File + `ATOMIC_MOVE`) nur falls benötigt.

### Eclipse-Override

```java
// EclipsePeonFile
@Override
public void replace(String old, String new) {
    String content = read();
    long count = content.split(Pattern.quote(old), -1).length - 1;
    if (count != 1) throw new IllegalStateException(...);
    // IFile.setContents mit KEEP_HISTORY → Workspace-Undo/History bleibt
    file.setContents(
        new ByteArrayInputStream(new.getBytes(UTF_8)),
        IResource.KEEP_HISTORY,
        monitor
    );
}
```

**Warum überschreiben?** Der Default-Pfad (read → write) würde über `IFile.setContents` laufen, aber ohne explizites `KEEP_HISTORY` verliert man die Workspace-History. Der Override macht das explizit.

---

## 6. Jimfs im Testing

**Abhängigkeit (core `pom.xml`):**

```xml
<dependency>
    <groupId>com.google.jimfs</groupId>
    <artifactId>jimfs</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

**Test-Setup:**

```java
@BeforeEach
void setUp() {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    Path root = fs.getPath("/test-project");
    Files.createDirectories(root);
    // DiskPeonFile funktioniert unverändert, weil es nur Path nutzt
    var file = new DiskPeonFile(root.resolve("peon-plan/overview.md"), restriction);
}
```

**Vorteil:** `DiskPeonFile` braucht keine Änderung — Jimfs liefert ein echtes `FileSystem`, und `java.nio.file.Path` funktioniert darauf transparent. Kein separater `JimfsPeonFile` nötig.

**Testabdeckung:**
- `PeonFileTest` (core): CRUD, move, replace (inkl. Count-Check), restrictions (allow/deny je Restriction-Typ).
- `PlanToolTest` (core, ersetzt PDE-Test): komplette Plan-Logik gegen Jimfs — `planRead`, `planSave`, `planUpdate`, `planImplemented`, `hasPlan`, Archiv-Kollisionen.
- `EclipsePeonFileTest` (Plugin, minimal): nur der Adapter-Teil (refreshLocal, History) — der Rest ist core-getestet.

---

## 7. Blast Radius — geänderte/neue Dateien

### Neu (core)

| Datei | Zweck |
|---|---|
| `org/sterl/llmpeon/file/PeonFile.java` | Interface |
| `org/sterl/llmpeon/file/PeonFileRestriction.java` | Restriction-Interface |
| `org/sterl/llmpeon/file/RestrictionViolation.java` | Exception |
| `org/sterl/llmpeon/file/DiskPeonFile.java` | Default-Impl (Path-basiert) |
| `org/sterl/llmpeon/file/InMemoryPeonFile.java` | Test-Impl (Map oder Jimfs) |
| `org/sterl/llmpeon/file/restrictions/AllowlistRestriction.java` | Glob-Regeln |
| `org/sterl/llmpeon/file/restrictions/ProjectPinRestriction.java` | Root-Pinning |
| `org/sterl/llmpeon/file/restrictions/TmpDirRestriction.java` | tmp erlauben |
| `org/sterl/llmpeon/file/restrictions/CompositeRestriction.java` | Kombination |
| `pom.xml` (core) | Jimfs-Dependency (test scope) |

### Neu (core tests)

| Datei | Zweck |
|---|---|
| `org/sterl/llmpeon/file/PeonFileTest.java` | CRUD/Move/Replace/Restrictions gegen Jimfs |
| `org/sterl/llmpeon/file/PlanToolTest.java` | Plan-Logik gegen Jimfs (ersetzt PDE-Variante) |

### Neu (Plugin)

| Datei | Zweck |
|---|---|
| `org/sterl/llmpeon/parts/file/EclipsePeonFile.java` | Adapter: IFile/IFolder + refreshLocal + History |
| `org/sterl/llmpeon/test/EclipsePeonFileTest.java` | Adapter-Test (minimal, PDE) |

### Geändert

| Datei | Änderung |
|---|---|
| `org/sterl/llmpeon/parts/tools/PlanTool.java` | Ersetzt `IFile`/`IoUtils` durch `PeonFile`; wandert ins core |
| `org/sterl/llmpeon/parts/ai/PeonAiService.java` | Wiring: `PlanTool` bekommt `EclipsePeonFile` (oder `DiskPeonFile` mit Restriction) |
| `org/sterl/llmpeon/docs/plan-tool.md` | Neu/angepasst: beschreibt `PeonFile`-Basis, Restrictions, Jimfs-Tests |
| `org/sterl/llmpeon.core/docs/index.md` | Neuer Eintrag für `file/`-Komponente |
| `docs/adr/` | Optional: neuer ADR „PeonFile — generische Datei-Abstraktion mit Restrictions“ |

### Unverändert (bewusst)

- `disk*`-Tools (`DiskFileReadTool`, `DiskFileWriteTool`, `DiskGrepTool`) — bleiben, bis separat migriert.
- `eclipse*`-Tools (`EclipseWorkspaceReadFileTool`, `EclipseWorkspaceWriteFileTool`, etc.) — bleiben.
- `SkillService`, `CommandService`, `AgentService` — keine Berührung.

---

## 8. Migrations-Reihenfolge

1. **Inkrement 1:** `PeonFile`-Interface + `DiskPeonFile` + `InMemoryPeonFile` + Restrictions im core. Tests gegen Jimfs. **Kein** PlanTool-Change.
2. **Inkrement 2:** `EclipsePeonFile` im Plugin + `PlanTool` auf `PeonFile` umstellen + aus Plugin ins core verschieben. PDE-Test schrumpft auf Adapter-Test.
3. **Inkrement 3 (optional):** Restrictions an bestehende Tools andocken (z. B. `AllowlistRestriction` für Jon), falls gewünscht.

---

## 9. Offene Fragen

- **Q1:** Soll `PeonFile` auch `copy()` bekommen, oder reicht `move` für den Anfang? *(Empfehlung: erst `move`, `copy` bei Bedarf.)*
Paul: ja
- **Q2:** Sollen Restrictions auch Lesen beschränken können, oder nur Schreiben? *(Empfehlung: erst nur Schreiben.)*
Paul: nein nur schreiben.
- **Q3:** Soll `EclipsePeonFile.replace` wirklich überschrieben werden, oder reicht der Default (read-modify-write)? *(Empfehlung: überschreiben, um `KEEP_HISTORY` zu erhalten.)*
Paul: ja überschrieben mit History!!!
- **Q4:** Soll es einen ADR geben, oder reicht das Feature-Doc `plan-tool.md`? *(Empfehlung: ADR, da es eine architektonische Entscheidung ist.)*
Paul: ist eine fachliche Anforderung alles in die docs -- wie immer nur entscheidungen aus fragen die selbst anders entschieden würden festhalten soweit in den docs nicht ersichtlich.


---

Weiterführende Ideen:

1. Dreistufige Permissions: allow / ask / deny statt nur erlaubt/verboten => auch als generisches Ask Interface bei uns etablieren. Wir haben schon was ähnliches für die Shell.

Vorschlag: PeonFileRestriction liefert nicht nur boolean allowsWrite(), sondern ein Decision (ALLOW, ASK, DENY). ASK bedeutet: UI fragt den User, bevor geschrieben wird.

Nutzen: Du bekommst eine Abstufung zwischen „immer erlauben“ (z. B. tmp) und „hart blockieren“ (z. B. fremde Home-Dirs). Gerade für den Plan-/Config-Bereich ist „frag nach, bevor außerhalb des Projekts geschrieben wird“ deutlich praktikabler als ein binäres Ja/Nein — und es ersetzt langfristig viele hardcoded WriteValidator-Sonderfälle. OpenCode löst genau das über permission-Regeln mit allow/ask/deny pro Tool und Pfad-Pattern.


2. Tool-Hooks (before/after) für Audit + Undo

Vorschlag: PeonFile-Operationen laufen durch einen Hook-Mechanismus (beforeWrite(file, content), afterWrite(file, result)), an dem sich Audit-Log, Undo-Snapshot oder spätere Sandbox-Checks anhängen lassen.

Nutzen: Du bekommst „wer hat wann welche Datei geändert“ gratis — wichtig für Multi-Agent-Szenarien (Jon/Dev/Review) und für ein späteres Undo/Rollback. OpenCode hat exakt dieses Muster (tool.execute.before/after um die eigentliche Ausführung). Für den Anfang reicht ein simpler In-Memory-Undo-Stack pro PeonFile-Instanz; ein vollständiges Audit-Log ist optional.

Paul: Vielleicht sollten wir Events feuern und einen EventService anbieten.

jede Komponente implementiert das Interface PeonEvent bei seinen eigenen Events.

Events liegen immer in "<component>.events" und enden immer auf Event
files.events.FileChangedEvent

statt hooks mit register -> damit wäre ein generisches Register abstract am Event Service Möglich.
