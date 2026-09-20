# Story A — Build-Plan: Tool-Output-Disclosure + Web-Tools

**Branch:** `analysis/tool-evolution` (läuft dort, nicht wechseln) · **Plan:** Da Thinka, 2026-09-20 · **Status:** ✅ alle 3 Inkremente gebaut (2026-09-20: inc 1 `857b72f`, inc 2 `435a9c7`, inc 3 folgt) — wartet auf PO-Review (Da Dok). Q1 gelöst — Paul: Variante (b), SOLL nachgetragen in `web-tools.md` R-WEB-8 + Architektur-Doc; Homepage-Deliverable in Inc 2. Der alte Vergleichs-Plan ist abgearbeitet (Verdicts: `docs/resolved-points.md`, „Tool-Evolution-Run").

> **STOP-AND-ASK (erste Regel für Da Mek):** Compile-Fehler ohne Lösung, nicht-grün-bekommende Tests, IST-Widerspruch zum Plan oder Unklarheit → **aktiv beim PO nachfragen (askDev-Kanal) — nie still workarounden, nie still SOLL ändern.** Je Inkrement IST-Evidenz liefern (§8) und nach jeder grünen Iteration committen (inkl. `docs/**`), Branch `analysis/tool-evolution` unverändert.

## 1. Kontext

PO-Run 2026-09-19 (Paul) hat angenommen: **CR-17/18/19** (Caps ehrlich machen; Search-Cap 1000→**500**; webFetch statt byte-Cap **paginiert**) und **CR-20** (neues `webGet(url, path)`). SOLL-Docs (lesen vor dem Bauen):

- `docs/tool-output-disclosure.md` — UC-OD-1…3 (idPrefix `OD`)
- `docs/web-tools.md` — UC-WEB-1…8 (idPrefix `WEB`)
- `docs/web-tools-architektur.md` — Klassen/Grenzen (Cache = schlichte LRU im Tool-Instanz-Feld, kein ADR nötig)

AGENTS.md-Prinzipien, die hier binden: „A tool must never lie about a limit" (Disclosure im Output), „One behaviour, one implementation" (geteilte Logik in core), „Empty means unset", „Clean break over migration", Thread-Safety (kein Single-Thread-Assumption), „Read the API contract in the source, never guess it".

**Slicing:** 3 vertikale Inkremente, je für sich grün, eine Polarität je Inkrement (1: nur-hinzufügen, 2: nur-hinzufügen, 3: Refactor/Erweiterung — bewusst NACH 2).

**Homepage:** genau **eine** Zeile in Inc 2 (AGENTS.md „visible changes"): die Doku des Disk-Tools-Toggles — `homepage/src/setup/custom-agents.md:176` (per Grep 2026-09-20 die einzige Toggle-Doku; „Disk tools" taucht sonst nur in `usage/docs-linter.md:11` auf — „Linter unabhängig vom Toggle" bleibt wahr, keine Änderung) — nennt jetzt auch `webGet` (gleiche Toggle-Bindung, R-WEB-8). Hinweis an Da-Dok, **kein** Scope: der „see Advanced Configuration"-Link auf dieser Zeile zeigt auf eine Seite ohne Disk-Tools-Sektion — Pre-existing Gap, nicht fixen.

## 2. Design-Entscheidungen (fix — keine Neuentscheidung durch Da Mek)

- **D1 Disclosure-Choke-Point:** Neue Überladung `AiReponseBuilder.searchComplete(List<String> results, int limit, String suffix)` (core, `…/tool/AiReponseBuilder.java:61`): wenn `limit > 0 && results.size() >= limit` → Disclosure-Zeile **„capped at {limit} — narrow your search"** (Wording aus `tool-output-disclosure.md`, Einstrich wie `grepComplete`). Bestehende 2-arg-`searchComplete` delegiert mit `limit = 0` (keine Disclosure). Trigger `>=` (nicht `>`): exakt am Limit ist schon gekappt.
- **D2 `eclipseSearchFiles`-Cap:** Konstante `private static final int MAX_LIMIT = 500` (kein neuer Parameter). `inLimit == 0 → 500`, Clamp `Math.max(1, Math.min(inLimit, 500))` **beibehalten** (negatives Limit → 1, existing Test `negativeLimitIsClamped`). `@P`-Description: „max results to return. Default 100, max 500."
- **D3 `diskSearchFiles`:** keine Logik-Änderung außer Call der 3-arg-`searchComplete` mit dem effektiven `limit` (0 = unlimited → keine Disclosure, UC-OD-3). `@P` bleibt („0 = unlimited. Default 50.") — jetzt ehrlich, weil der Return die Kappung nennt.
- **D4 `webGet` (neu, core):** eigene Klasse `org.sterl.llmpeon.tool.tools.WebGetTool extends AbstractTool`, `isEditTool() = true` (Plan/Review/SearchAgent-Filter greifen automatisch — keine extra Filter-Änderung). Guards **in dieser Reihenfolge**: `QualifiedPathValidator.requireQualifiedDisk("webGet", path)` (relative → IAE „must be fully qualified") → `validateWrite(path)` (WriteValidator des Request, wie `diskWriteFile`). **Überwriting** bestehender Datei (frischer Download = neuer Inhalt, wie `diskWriteFile`); bei Overwrite `monitor.onFileUpdate` (Display-Pfad = absolut, kein workingDir). **Kein Size-Limit** (R-WEB-4). HttpClient wie `WebFetchTool` (Redirect.ALWAYS, 10s connect, 30s request). Fehler → Exception, **nie** Teilergebnis/stiller 0-Byte-Erfolg.
- **D4a `webGet`-Registrierung:** **nur** `SharedToolsComponent` (plugin), **nicht** in `ToolService`-withDefaults (das bleibt WebFetch/Search/Shell/Compact) und **nicht** bei `AiScaffoldAgent` (configDir-scope passt nicht zu absoluten Download-Pfaden). **Gating (Q1 final, Paul 2026-09-20 — Variante (b)):** `webGet` hängt hinter `diskToolsEnabled` (default **OFF**) — in `SharedToolsComponent.updateActiveDiskTools`: on → `addTool(webGetTool)`, off → `removeTool(webGetTool)`, exakt wie die Disk-Tools (SOLL: `web-tools.md` R-WEB-8, `web-tools-architektur.md` Klassen-Tabelle).
- **D5 `webFetch`-Pagination:** LRU-Cache **5 URLs** als Instanz-Feld in `WebFetchTool` (Enkapsulation, keine globale Sichtbarkeit — Arch-Doc); `LinkedHashMap(accessOrder=true)` + `removeEldestEntry`, **jeder Zugriff in `synchronized(cache)`-Block** (Tool-Instanz wird von mehreren Agenten/Threading genutzt). Cache-Hit = **kein Refetch**, Fenster aus dem Snapshot (R-WEB-2); Fehlerpfad (HTTP ≥ 400) **niemals gecacht**.
- **D6 Fenster & Wording:** Signatur `webFetchAsMarkdown(url, startLine?, endLine?)` (optional, 1-based, `null/0` = Default wie Read-Tools). Fenster **hart max 500 Zeilen**: `shownEnd = min(endLine, start + 499)` (bei `start ≤ 0 → start = 1`, dann `shownEnd = min(endLine, 500)`). Output = **`FileLines.extract(markdown, start, shownEnd)`** → **nummerierte Zeilen** (konsistent mit Read-Familie `diskReadFile`/`eclipseReadFile`; „Zeilen-Splitting konsistent mit FileLines-Muster"). Disclosure-Zeile (exakt, R-WEB-1): **`lines X–Y of N — read on with startLine=Y+1`** — „read on"-Teil nur wenn `Y < N`. `start > N` → ehrliche Meldung `URL has N lines, requested start S` (FileLines-Semantik, web-taugliches Wording). Leeres Markdown → `Fetched <url>: empty content (0 lines)`.
- **D7 Fehlerpfad webFetch (R-WEB-3/R-OD-3):** HTTP ≥ 400 → Return = `Failed to fetch <url>. HTTP status <code>` + **Snippet = `FileLines.extract(markdown, 1, 10)`** (erste 10 Zeilen des Markdowns), **nie** voller Body. `onProblem` wie heute.
- **D8 Doc-Status-Flips:** Inc 1: `tool-output-disclosure.md` 🚧→❌ specified („Offen (PO-Run)"-Sektion aufgelöst) + `docs/index.md:74` „Hard-Cap" → „paginiert" korrigieren. Inc 3: UC-WEB-1…4 + R-WEB-1…3, R-OD-3, `index.md:74-75` + beide Doc-Status → **✅ done**; `docs/memory.md` Items 1+2 abhaken; `docs/open-points.md:10` `tool-output-disclosure` entfernen.

- **D9 Linter-Struktur (2026-09-20, lint-IST: 22 Befunde `UC_OHNE_REGEL`+`STATUS_FEHLT` in OD/WEB):** der Linter verlangt Regeln als `### R-<PREFIX>-<n> — <titel> <marker>`-Überschriften mit Status-Marker und jede UC als `#### UC-…` **unter ihrer Regel** mit eigenem Marker (Vorbilder: `docs/docs-linter.md`, `docs/user-context.md` — `❌` = offen, `✅`/`✅ done` = belegt). Bulleten-Regeln von OD/WEB → `###`-Überschriften, UCs unter ihrer Regel nesten, in `web-tools.md` R-WEB-8 **nach** R-WEB-7 sortieren — **Regel-/BDD-Inhalt bleibt wortgleich** (Format-Fix gemäß Linter-SOLL, keine SOLL-Änderung). Verteilung: Inc 1 → OD-Doc (OD-Befunde → 0), Inc 2 → WEB-Doc + webGet-Flip (OD+WEB → 0), Inc 3 → Rest-Flip (Regressions-Check 0).

## 3. Inc 1 (nur-hinzufügen) — Caps/Disclosures (UC-OD-1…3) — ✅ done (2026-09-20, inc-1): core 886/0/0, OSGi 226/0, lint OD 0 / WEB 16 (12 erwartet + 8 UNBELEGT, Tests folgen Inc 2/3)

**Änderungen:**
1. `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/AiReponseBuilder.java` — 3-arg-`searchComplete` (D1).
2. `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/tools/DiskFileReadTool.java:98` — `searchComplete(matches, limit, suffix)`.
3. `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/EclipseWorkspaceReadFileTool.java:126-132,160` — `MAX_LIMIT = 500` (D2), `@P`-Text, `searchComplete(list, limit, suffix)`.
4. Tests (s. §6, §7).
5. Docs: `docs/tool-output-disclosure.md` — **D9-Umbau** (Bulleten-Regeln → `### R-OD-n`-Überschriften mit Marker, UCs unter ihrer Regel genestet + Marker; Inhalt **wortgleich**) + Status 🚧→❌ specified + „Offen (PO-Run)"-Sektion löschen + **Flip: UC-OD-1…3, R-OD-1…2, R-OD-4 → ✅** (R-OD-3 bleibt ❌ — Inc 3); `docs/index.md:74` „Hard-Cap" → „paginiert" korrigieren; `org.sterl.llmpeon.test/ai-e2e-test/read-tools-e2e-test.md` — in Abschnitt 4 neue Zeile `4.8`: `eclipseSearchFiles("*.java", limit=100)` in Projekt mit >100 Matches (z. B. `llmpeon-core`) → genau 100 Treffer **und** „capped at 100 — narrow your search".

**Code-Sketch (D1, Kernstück):**
```java
public static String searchComplete(List<String> results, int limit, String suffix) {
    if (limit > 0 && results.size() >= limit)
        suffix = suffix == null ? "capped at " + limit + " — narrow your search"
                                 : suffix + System.lineSeparator() + "capped at " + limit + " — narrow your search";
    return searchComplete(results, suffix); // bestehende 2-arg-Methode rendert unverändert
}
public static String searchComplete(List<String> results, String suffix) {
    return searchComplete(results, 0, suffix); // ← bestehende Body-Logik in 2-arg, neue Delegierung hier
}
```
(Exakte Struktur frei wählbar, solange Wording/Trigger/Doppelt-Aufruf-Sicherheit stimmen.)

**Bestehende Tests, die rot werden (müssen adaptiert werden — Seed-Inventar per Grep in BEIDEN Modulen verifiziert, Memory-Regel #33):**
- core `DiskFileReadToolsTest#searchDiskFiles_limitRestrictsResults` (Zeilen 104–119): `split("\n").length`-Assertions für limit=1/2 brechen (Disclosure-Zeile + Leerzeile kommen dazu) → auf Zählung der **Treffer-Zeilen** umstellen (z. B. `result.lines().filter(l -> l.endsWith(".java")).count()`).
- plugin `EclipseSearchFilesToolTest#searchWorkspaceFiles_limitRestrictsResults` (Zeilen 95–105): `assertEquals(1, limited.split("\n").length)` bricht bei limit=1 → bestehende `resultLines(...)`-Helper nutzen (filtert `/`-Pfads).
- **Nicht betroffen** (verifiziert): alle anderen Seeds < 500 und ohne erreichten Limit (core: 2–3 Dateien limit=0; plugin: `limitIsGlobal` limit=3, `negativeLimitIsClamped` limit=-1 — beide via `resultLines`; Fixture+other < 500 bei limit=0 → keine Disclosure).

**Neue Tests:**
- core `DiskFileReadToolsTest`: `searchDiskFilesDisclosesCap` `// UC-OD-2` (80 Dateien `f00.java`…`, Default-Limit 50 → 50 Treffer-Zeilen + „capped at 50 — narrow your search") · `searchDiskFilesUnlimitedNoDisclosure` `// UC-OD-3` (30 Dateien, `limit=0` → 30 Zeilen, `doesNotContain("capped at")`).
- core `AiReponseBuilderTest`: 2 Unit-Tests für die 3-arg-Methode (Cap erreicht → Disclosure; unter Cap / limit=0 → keine) — ohne UC-ID (UC-Belege sitzen auf Tool-Ebene).
- plugin `EclipseSearchFilesToolTest`: `eclipseSearchFilesCapsWithDisclosure` `// UC-OD-1` — **600** Fixture-Dateien `cap/Cap<i>.java` per `IFile.create` (BDD-Getreue), `eclipseSearchFiles("Cap*.java", PeonTestFixture.PROJECT_NAME, 500)` → `resultLines`-Count == 500 + `contains("capped at 500 — narrow your search")`; `@Test(timeout = 60_000)`; **Cleanup:** `cap`-Folder in der Klasse-`@After` (neben Existing-Cleanup, VOR `super.after()`) löschen — Cascade-Schutz: liegen die 600 Dateien zurück, cappt `*.java` (limit 0→500) andere Tests und `foreignProjectsStayReachable` könnte `Other.java` verlieren.

**Gate Inc 1:** `mvn test` (llmpeon-core) grün → `eclipseBuildProject` `org.sterl.llmpeon` + `org.sterl.llmpeon.test` → `eclipseRunTests` `org.sterl.llmpeon.test` (ganze Suite, §7) grün → `lintDocsAndTests`: **OD 0 Befunde**; WEB weiterhin **12 Befunde** (`UC_OHNE_REGEL`+`STATUS_FEHLT` — D9-Umbau kommt in Inc 2) = erwartet.

## 4. Inc 2 (nur-hinzufügen) — `webGet` (UC-WEB-5…8) — ✅ done (2026-09-20, inc-2): core 891/0/0, OSGi 228/0, lint OD 0 · WEB: 0 high-priority-Befunde (4× UNBELEGT UC-WEB-1…4 = ❌-offene UCs, Tests in Inc 3). Q2 gelöst (Jon: R-WEB-Präfix-Rename), Ctor-Deviation ok (Jon: Registrierung nur bei Toggle-on, exakt wie Disk-Tools).

**Neu:** `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/tools/WebGetTool.java`
```java
public class WebGetTool extends AbstractTool {
    @Override public boolean isEditTool() { return true; }   // D4 — Sub-Agent-/Plan-/Review-Filter greift automatisch

    @Tool("Download a URL to a file on disk. Absolute path required; overwrites existing. Returns status, size and path — never the content.")
    public String webGet(@P(name = "url") String url,
                         @P(name = "path", description = "absolute disk path to write to") String path) {
        ArgsUtil.requireNonBlank(url, "url");
        ArgsUtil.requireNonBlank(path, "path");
        QualifiedPathValidator.requireQualifiedDisk("webGet", path); // UC-WEB-6: relative → IAE „must be fully qualified"
        validateWrite(path);                                          // UC-WEB-6: WriteValidator wie diskWriteFile
        HttpResponse<byte[]> resp = httpGet(url);                     // Redirects/Timeouts wie WebFetchTool
        if (resp.statusCode() >= 400)
            throw new IllegalArgumentException("webGet failed: HTTP " + resp.statusCode() + " from " + url);
        // Files.createDirectories(parent) + writeBytes (Byte-Array VOR Status-Check → nie Teilergebnis)
        // existierte vorher → monitor.onFileUpdate(absPath, old, new); onTool("Downloaded …")
        return n == 0
            ? "Downloaded " + abs + ": HTTP " + code + ", 0 bytes — server returned an empty body (from " + url + ")"  // UC-WEB-7
            : "Downloaded " + abs + ": HTTP " + code + ", " + n + " bytes (from " + url + ")";                          // UC-WEB-5
    }
}
```
Kein Inhalt im Return (R-WEB-4), keine neuen Dependencies.

**Registrierung (D4a):** `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/ai/component/SharedToolsComponent.java` — Feld `webGetTool` + `sharedToolService.addTool(webGetTool)` im Ctor (Zeile ~50–68) + Getter `webGetTool()` (Stil wie `diskGrepTool()`). **Gating (Q1 final, Variante (b)):** zusätzlich in `updateActiveDiskTools` — on → `addTool(webGetTool)`, off → `removeTool(webGetTool)`, exakt wie die Disk-Tools, **default OFF** (R-WEB-8). **Alle Registrierungs-/Tool-Listen-Stellen per Grep selbst verifiziert** (Memory #18): IST `ToolService` withDefaults (69–76, WebFetch/Search/Shell/Compact — bleibt unverändert), `AiScaffoldAgent:73` (bleibt unverändert), `SharedToolsComponent` (dort an), `BuildPoAgentComponent` (Jon — bewusst kein webGet, kuratiert; Slaves erhalten es über `sharedToolService`).

**Tests:**
- core **neu** `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/tool/WebGetToolTest.java` (JUnit5/AssertJ, `@TempDir`, lokaler `com.sun.net.httpserver.HttpServer`):
  - `webGetDownloadsAndReturnsMetadata` `// UC-WEB-5` (Datei auf Disk, Return enthält Status/Größe/Pfad, **enthält nicht** den Body)
  - `webGetEnforcesWriteGuards` `// UC-WEB-6` (WriteValidator wie `DiskFileWriteToolTest#docsRequest()` `withToolRequest`-Pattern: DOCS-Validator + absoluter Nicht-Docs-Pfad → IAE, Datei existiert nicht; + relativer Pfad → IAE „must be fully qualified")
  - `webGetHonestZeroBytes` `// UC-WEB-7` (Server liefert leeren 200 → „0 bytes — server returned an empty body", Datei existiert als leere Datei)
  - `webGetIsEditTool` `// UC-WEB-8` (`assertThat(new WebGetTool().isEditTool()).isTrue()`)
- plugin `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/SharedToolsComponentTest.java`:
  - Registrierung-Assertion (in `test_sharedTools_registersAllEclipseTools` oder neuer Test) + **Toggle-Test (Pflicht, R-WEB-8):** default OFF → `getTool(WebGetTool.class).isEmpty()` → `updateActiveDiskTools(config(true))` → present → `config(false)` → wieder empty. Kein UC-Kommentar — R-WEB-8 trägt im SOLL-Doc keinen UC; ein nicht definierter UC am Test würde `VERWAIST`.
  - `webGetFilteredFromSearchAgent` `// UC-WEB-8`: `sut.toolService().getExecutor("webGet")` + `SearchAgentTool.getFilter()` → `false` (Production-Wiring, wie existing Filter-Test Zeilen 62–77).

**Docs Inc 2:** `docs/tool-descriptions-inventory.md` — WebFetchTool-Sektion: neue Zeile `webGet` mit finaler Description (und Kopfzahl 42→43 `@Tool`-Methods). `docs/web-tools.md` — **D9-Umbau** (Bulleten-Regeln → `### R-WEB-n`-Überschriften mit Marker, UCs unter ihrer Regel genestet + Marker, R-WEB-8 **nach** R-WEB-7 sortieren; Inhalt **wortgleich**) + **Flip: UC-WEB-5…8, R-WEB-4…8 → ✅**; Doc-Status bleibt ❌ (UC-WEB-1…4/R-WEB-1…3 → Inc 3). **Homepage (Pflicht, „visible changes"):** `homepage/src/setup/custom-agents.md:176` (`disk`-Zeile) — `webGet` mit benennen, Entwurf: „The same toggle also enables **`webGet`** (download a URL to a disk path — status, size and path in the context, never the content)." (Wording final frei, muss `webGet` + gleiche Toggle-Bindung nennen.)

**Gate Inc 2:** wie Inc 1 (`mvn test` core → Build → OSGi-Suite → lint) + Homepage-Zeile im Commit (AGENTS.md „visible changes") — lint: **OD+WEB zusammen 0 Befunde** (❌-offene UCs sind **keine** Befunde).

## 5. Inc 3 (Refactor/Erweiterung, bewusst NACH Inc 2) — `webFetchAsMarkdown` paginiert (UC-WEB-1…4, R-OD-3) — ✅ done (2026-09-20, inc-3): core 897/0/0, OSGi 228/0, lint OD+WEB 0. Deviations (alle Plan-konform): `FetchResult`-Record statt zweitem Fetch im Fehlerpfad (ein HTTP-Call, nie gecacht), `@P(required=false)` wie Read-Familie, Inventory-Zeile 13 + memory.md-State-Zeilen synchronisiert.

```mermaid
sequenceDiagram
  participant M as Model
  participant W as WebFetchTool
  participant C as LRU-Cache (5, synchronized)
  participant H as HttpClient
  M->>W: webFetchAsMarkdown(url, startLine?, endLine?)
  alt Cache-Miss
    W->>H: GET (30s request / 10s connect, Redirects)
    H-->>W: status + body
    alt status >= 400
      W-->>M: "Failed to fetch … HTTP <code>" + Snippet (erste 10 MD-Zeilen) — NICHT gecacht
    else ok
      W->>W: HTML → Markdown (Flexmark, bestehend)
      W->>C: put(url, markdown) — 6. URL verdrängt älteste
    end
  else Cache-Hit
    C-->>W: markdown — KEIN zweiter HTTP-Call
  end
  W->>W: shownEnd = min(end, start+499); FileLines.extract(md, start, shownEnd)
  W-->>M: nummerierte Zeilen + "lines X–Y of N — read on with startLine=Y+1"
```

**Änderungen:**
1. `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/tools/WebFetchTool.java` — Signatur + Cache (D5/D6), Fehlerpfad (D7). Konstanten `CACHE_MAX_URLS = 5`, `MAX_WINDOW_LINES = 500`, `SNIPPET_LINES = 10`. `@Tool`-Description wird pagination-tauglich (Entwurf: „Fetch a URL as Markdown (cached). startLine/endLine (1-based, 0 = default) page the result; max 500 lines per call, disclosed. HTTP 4xx/5xx: status + snippet.") — final nach tool-descriptions-Pattern (10–25 Wörter, imperativ).
2. `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/shared/FileLines.java` — neuer `public static int countLines(String content)` (`split(FileUtils.dominantLineEnding(content), -1).length`) — Total fürs Disclosure, Logik an EINER Stelle.
3. **Löschen:** die beiden `@Disabled` Manual-Probes in `WebFetchToolTest` (Clean break; echte Tests ersetzen sie).
4. Tests (neu geschrieben) in `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/tool/WebFetchToolTest.java` — lokaler `com.sun.net.httpserver.HttpServer`, **per-URL Call-Counter** für Refetch-Belege:
   - `webFetchFirstCallReturnsFirstWindow` `// UC-WEB-1` (3000-Zeilen-Page → Zeilen 1–500 (nummeriert), Disclosure `lines 1–500 of 3000 — read on with startLine=501`, Zeile 501 NICHT enthalten) + Window-Clamp: Request `startLine=1, endLine=3000` → trotzdem max 500 + „read on"
   - `webFetchCacheHitPaginatesWithoutRefetch` `// UC-WEB-2` (2. Call `startLine=501` → 501–1000, **Counter == 1**)
   - `webFetchCacheEvictionRefetches` `// UC-WEB-3` (URLs 1–6, dann URL 1 erneut → Counter(URL1) == 2, Fenster wieder 1–500)
   - `webFetchHttpErrorReturnsStatusAndSnippet` `// UC-WEB-4` (HTTP 500, langer Body → Status im Return, Snippet vorhanden, **Body-Marker am Ende NICHT** enthalten)
   - `FileLinesTest` (existing, `…/shared/FileLinesTest.java`): `countLines`-Test.
   - Test-Timeouts explizit setzen (z. B. `@Timeout(30)` auf der Klasse).
5. Doc-Flips (D8/D9) — erst NACH grünem Gate: `web-tools.md` UC-WEB-1…4 + R-WEB-1…3 → ✅ + Doc-Status → **✅ done**; `tool-output-disclosure.md` R-OD-3 → ✅ + Doc-Status → **✅ done**; `docs/index.md:74-75` → ✅; `docs/memory.md` Items 1+2 abhaken; `docs/open-points.md:10` `tool-output-disclosure` entfernen.

**Nicht betroffen (explizit):** `ToolService` withDefaults (WebFetch-Instanz bleibt), `AiScaffoldAgent` (bekommt Pagination automatisch über dieselbe Klasse — gewollt), `ToolServiceTest:49-50` (nur Typ-Check, bleibt grün).

**Gate Inc 3:** wie Inc 1 + `lintDocsAndTests`: **0 Befunde** OD/WEB (Regressions-Check — war nach Inc 2 bereits 0).

## 6. BDD-Abdeckung (alle 11 UCs, linter-geprüft via `// UC-…`-Kommentare am Test)

| UC (Doc) | Scenario | Test (Methode) | Inc | Modul |
|---|---|---|---|---|
| UC-OD-1 | `eclipseSearchFilesCapsWithDisclosure` | `EclipseSearchFilesToolTest#eclipseSearchFilesCapsWithDisclosure` | 1 | plugin (JUnit4) |
| UC-OD-2 | `diskSearchFilesDisclosesCap` | `DiskFileReadToolsTest#searchDiskFilesDisclosesCap` | 1 | core |
| UC-OD-3 | `diskSearchFilesUnlimitedNoDisclosure` | `DiskFileReadToolsTest#searchDiskFilesUnlimitedNoDisclosure` | 1 | core |
| UC-WEB-5 | `webGetDownloadsAndReturnsMetadata` | `WebGetToolTest#webGetDownloadsAndReturnsMetadata` | 2 | core |
| UC-WEB-6 | `webGetEnforcesWriteGuards` | `WebGetToolTest#webGetEnforcesWriteGuards` | 2 | core |
| UC-WEB-7 | `webGetHonestZeroBytes` | `WebGetToolTest#webGetHonestZeroBytes` | 2 | core |
| UC-WEB-8 | `webGetIsEditToolFiltered` | `SharedToolsComponentTest#webGetFilteredFromSearchAgent` (+ `WebGetToolTest#webGetIsEditTool`) | 2 | plugin + core |
| UC-WEB-1 | `webFetchFirstCallReturnsFirstWindow` | `WebFetchToolTest#webFetchFirstCallReturnsFirstWindow` | 3 | core |
| UC-WEB-2 | `webFetchCacheHitPaginatesWithoutRefetch` | `WebFetchToolTest#webFetchCacheHitPaginatesWithoutRefetch` | 3 | core |
| UC-WEB-3 | `webFetchCacheEvictionRefetches` | `WebFetchToolTest#webFetchCacheEvictionRefetches` | 3 | core |
| UC-WEB-4 | `webFetchHttpErrorReturnsStatusAndSnippet` | `WebFetchToolTest#webFetchHttpErrorReturnsStatusAndSnippet` | 3 | core |

## 7. Test-Strategie & Gates

- **Ground truth = Maven Surefire** (core, `mvn test` in `llmpeon-core`), nicht `eclipseRunTests` (AGENTS.md). OSGi-Suite (`org.sterl.llmpeon.test`, JUnit4) nur für plugin-Teile (Inc 1: `EclipseSearchFilesToolTest`, Inc 2: `SharedToolsComponentTest`).
- **VOR jedem OSGi-Run** `eclipseBuildProject` über alle geänderten Projekte (stale bin/-Klassen = ClassNotFoundException, Memory #16).
- **OSGi-Run-Disziplin (Memory #13):** ERSTER Lauf braucht einmalig die manuelle Workspace-Trust-Bestätigung im UI-Dialog (ohne: Launch hängt, „0 tests ran") → **ganze Suite** starten (eine Bestätigung deckt den Lauf); bei Timeout NICHT parallel nachstarten (PDE-Instanzen kollidieren), sondern Paul informieren + auf die konkrete PID warten.
- **Kein persistierter State** in Tests (kein Eclipse-Prefs-/Disk-Abhängiges von früheren Runs); `@TempDir` + lokaler `HttpServer` (Port 0 = frei) in core.
- **Test-Honesty (AGENTS-DEV):** Cache-Tests belegen den **fehlenden** Refetch über den Call-Counter (nicht nur „Response ok"); Disclosure-Tests asserten die exakte Zeile UND die Trefferanzahl; „Test that would be green without the feature" = kein Test.
- `System.lineSeparator()` in allen Tool-Output-Strings (Memory #7) — Disclosure-Zeilen über `System.lineSeparator()` anhängen.
- `lintDocsAndTests` je Gate: Inc 1 → OD 0 / WEB 12 (D9-Umbau folgt in Inc 2) · Inc 2 → **0** (❌-offene UCs sind keine Befunde) · Inc 3 → 0 (Regression).

## 8. Evidenz & Abnahme je Inkrement (Memory #28 — IST, nicht Behauptung)

Je Inkrement liefert Da Mek: (1) Surefire/OSGi-Zahlen (Tests/Errors/Failures), (2) je UC: grüner Testname, (3) je Deliverable: file:line-Beleg (z. B. `AiReponseBuilder.searchComplete(…, int limit, …)` Zeile X; `WebGetTool` exists + `isEditTool` true; `EclipseWorkspaceReadFileTool` `MAX_LIMIT = 500`; `WebFetchTool` Cache-Feld + `MAX_WINDOW_LINES = 500`; `homepage/src/setup/custom-agents.md:176` nennt `webGet`), (4) lint-Report-Status, (5) Commit-Hash auf `analysis/tool-evolution`. PO/Da-Dok verifizieren gegen IST, bevor ein Inkrement „DONE" heißt.

## 9. Regeln & Constraints

- Naming/Logging: Log-OR-throw (nie beides); Tool-Fehler = ehrliche Exception-Meldungen ohne Stacktrace-Füllwasser; Secrets nie in Output (nicht relevant hier, aber gilt).
- Keine neuen Dependencies (Flexmark/HttpClient/JDK-`HttpServer` sind da); core bleibt plain-Maven.
- Read-only-Vorgaben: `eclipseReadFile`/`diskReadFile`/Grep-Tools/Fixtures **nicht** anfassen; `grepComplete` unverändert; `ToolService`-withDefaults unverändert.
- Inc-Polarität halten: Inc 1/2 fügen nur hinzu (keine Refactors), Inc 3 refactorisiert webFetch.
- Branch/Commit: `analysis/tool-evolution`, Commit je grüner Iteration **inkl.** Docs; kein Branch-Wechsel ohne Ansage.
- Vor Inc 1: Git-Zustand prüfen (Memory #23) — `docs/resolved-points.md`/`index.md`/`memory.md` der Auflösung sollen committed sein (`docs/memory.md` Schritt 1); falls nicht: erst committen, dann bauen.

## 10. Offene Fragen

- **Q1 — gelöst (Paul, 2026-09-20): Variante (b).** `webGet` hinter `diskToolsEnabled` (default OFF) in `SharedToolsComponent.updateActiveDiskTools` eingehängt; SOLL nachgetragen (`web-tools.md` R-WEB-8 + `web-tools-architektur.md` Klassen-Tabelle); Homepage-Toggle-Doku (`custom-agents.md:176`) = Inc-2-Deliverable (AGENTS.md „visible changes").
- **Q2 — gelöst (Jon, 2026-09-20): Variante (a).** `R-W-1…8` → `R-WEB-1…8` umbenannt (`web-tools.md`-Überschriften + 2 Referenzen in `web-tools-architektur.md` + Plan), Regel-/BDD-Inhalt wortgleich. DL-Befunde (33× UNBELEGT_ERLEDIGT UC-DL-5…39 + 1× VERWAIST UC-DL-99-Fixture) = bekannter offener Punkt „ID-Kommentare an den DL-Tests" (open-points.md), out-of-scope, kein Handlungsbedarf in dieser Story.
- **Offene Fragen: none.**

## 11. PO-Review Da Dok (2026-09-20) — Story A Inc 1–3 (bis `3b6de88`)

**Verdict: CONCERNS** — keine blockierenden Lücken. SOLL==IST auf allen 11 UCs; Inc 1/2/3 Plan-konform; Deviations sind die im Plan akzeptierten (FetchResult-Record, `@P(required=false)`, Ctor-Registrierung nur bei Toggle-on nach Jon).

**Verifiziert (IST, nicht Behauptung):**
- Live-Testläufe (Da Dok, diese Session, Eclipse-Runner — Surefire-Zahlen unverifizierbar ohne Maven-Shell): `WebFetchToolTest` 5/5, `WebGetToolTest` 5/5, `DiskFileReadToolsTest` 16/16 grün; `llmpeon-core` Build kompiliert (nur bestehende Null-Safety-Warnings). OSGi-Suite (`EclipseSearchFilesToolTest` 600-Datei-Test, `SharedToolsComponentTest`) nicht gelöst (braucht manuelle Workspace-Trust-Bestätigung) — Code-Read-Evidenz statt Test-Lauf.
- `lintDocsAndTests`: **OD 0 / WEB 0** Befunde (die 33 Befunde = UC-DL, bekannt out-of-scope, Plan Q2).
- Cap **500** (nicht 1000): `EclipseWorkspaceReadFileTool.java:36` `MAX_LIMIT = 500`, `inLimit==0 → 500`, Clamp erhalten (`:133-134`).
- Disclosure-Wording exakt: `AiReponseBuilder.searchComplete(List,int,String)` (`AiReponseBuilder.java:25-32`, Trigger `>=`, `System.lineSeparator()`); `webFetchAsMarkdown`: `lines X–Y of N — read on with startLine=Y+1` (`WebFetchTool.java:96-99`), „read on" nur bei `shownEnd < total`.
- LRU **5** + `synchronized(cache)` + kein Refetch bei Hit + Fehlerpfad nie gecacht: `WebFetchTool.java:42-49,108-137`; Call-Counter-Tests belegen fehlenden/zweiten Refetch (`WebFetchToolTest` UC-WEB-2 `calls==1`, UC-WEB-3 `calls==2`, UC-WEB-4 Retry `calls==2`).
- Snippet **10** Zeilen: `SNIPPET_LINES = 10`, `FileLines.extract(markdown, 1, 10)` (`WebFetchTool.java:78-79`).
- webGet: Guard-Reihenfolge `requireQualifiedDisk` → `validateWrite` (`WebGetTool.java:47-48`), Status-Check **vor** Write (nie Teilergebnis, `:58-66`), ehrliche 0-Byte-Meldung (`:71-75`), `isEditTool=true` (`:38-39`), **kein** Size-Limit.
- Toggle-Gate default OFF: `SharedToolsComponent.java:75-91` — `webGetTool` nur via `updateActiveDiskTools` (kein Ctor-`addTool`, Jon-Entscheidung); Toggle-Test `test_webGetFollowsDiskToolToggle` + `webGetFilteredFromSearchAgent` `// UC-WEB-8` (Production-Wiring) vorhanden.
- `countLines` = exakt derselbe Split wie `extract` (`FileLines.java:10-13` — `dominantLineEnding`, `-1`) → Total und Fenster konsistent.
- Inc-1-Seed-Tests korrekt adaptiert (nicht „Test falsch"): `DiskFileReadToolsTest.countHits` filtert Treffer-Zeilen (`:157-159`), `EclipseSearchFilesToolTest#searchWorkspaceFiles_limitRestrictsResults` nutzt `resultLines` + assertet Disclosure (`:105-108`); `negativeLimitIsClamped` unverändert grün-bleibend.
- Docs: beide Fachdocs ✅ done + R-/UC-Marker, `index.md:74-75` ✅, `open-points.md:10` „tool-output-disclosure" entfernt, Inventory `13a webGet` + 42→43 + WebFetch-Zeile 13 aktualisiert, Homepage `custom-agents.md:176` nennt `webGet` + Toggle-Bindung, E2E `read-tools-e2e-test.md:67` 4.8, Arch-Doc Klassen-Tabelle mit Gating.
- Commit-Hashes (`857b72f`, `435a9c7`, `3b6de88`) und Maven-Surefire-Zahlen **nicht unabhängig verifizierbar** (kein Git-/Maven-Zugriff bei Da Dok) — Code-Zustand auf dem Branch ist geprüft.

**Gaps (CONCERNS, nicht blockierend — für PO/Da Thinka):**
1. **`endLine=0`-Semantik: Doc↔Code-Drift.** `web-tools.md` R-WEB-1: „(`0` = Dateiende wie bei den Read-Tools)" — Code: `endLine=0` = **Fenster-Default** (min(start+499)), nicht Dateiende (`WebFetchTool.java:91-93`); `startLine=0` = Dokumentanfang. Weder Plan D6 noch eine UC pinnen `endLine=0`. Das Model liest aus der Doku ein „bis-Ende"-Lesen, kriegt aber ein 500-Fenster → SOLL≠IST in der Regel-Formulierung. D9-SOLL war „Inhalt wortgleich", also kein Da-Mek-Fehler — **PO-Entscheidung: Doku-Zeile korrigieren oder UC nachtragen** (Empfehlung: Doku-Zeile korrigieren, Code bleibt).
2. **Snippet-Länge ist mutation-taub.** `WebFetchToolTest#webFetchHttpErrorReturnsStatusAndSnippet` assertet `ERR1…ERR10` vorhanden + `ERR50-ENDBODY` abwesend — würde grün bleiben bei `SNIPPET_LINES = 11…49`. Mutation-Check-Empfehlung: `.doesNotContain("11: ERR11")` ergänzen (einziger Spot, der die 10-Zeilen-Kappe wirklich rot macht).
3. **Stale `R-W-*`-Referenzen nach Q2-Rename.** `WebGetToolTest.java:28` („R-W-4…8"), `:134` („R-W-7"), `SharedToolsComponent.java:45` („R-W-8"), `SharedToolsComponentTest.java:150` („R-W-8") — Doku heißt jetzt `R-WEB-*`. Nur Kommentare, kein Verhalten — aber genau die Verwechslungsquelle, die der Rename beseitigen sollte.
4. **Edge: `endLine < startLine`** (z. B. start=500, end=100): `FileLines.extract` tauscht die Bounds still, Disclosure-Label bleibt „lines 500–100 of N" → widersprüchlicher Output (AGENTS „never lie"). In Plan/Doku nicht definiert — seltener Modell-Fehler, nicht hart.

**Plan-coverage gaps (nicht Da-Mek-Fehler):**
- Plan Q2 (R-W→R-WEB-Rename) deckte nur Doku-Überschriften + Arch-Doc + Plan — **Code-Kommentare** waren nicht im Rename-Scope (→ Gap 3). Memory #18 (Grep alle Referenzstellen) hätte greifen sollen.
- `endLine=0`/`endLine<startLine`-Semantik nie im Plan gepinnt (→ Gaps 1+4).

**Skill-Evolution-Outcome (AGENTS-PO.md Gate-Phase):** **Kein CRUD-Outcome erzwungen.** Evidence: (a) Memory #33 (Seed-Inventar im Plan) hat funktioniert — beide rot-werdenden Tests im Plan korrekt vorab benannt und im Code adaptiert; (b) STOP-AND-ASK hat funktioniert — Q1/Q2 gelöst und dokumentiert, keine stillen SOLL-Änderungen gefunden; (c) einziger Evolve-Kandidat: Ledger-Eintrag „Rule-ID-Rename: Referenzen auch in Code-Kommentaren grep (Memory #18-Analog für Doku-IDs)" — Empfehlung an Jon/Da Mek, kein Skill-File selbst betroffen.
