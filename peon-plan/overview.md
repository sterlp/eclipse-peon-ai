# Sanity-Pass: konsolidierter Branch `bugfix/user-context-selection` (Tip `0af001a`)

**Kein Build-Plan — Revisats.** Da Dok, 2026-09-19. Pauls 5 Fragen (a)–(e) nach der
Konsolidierung (Merge `0af001a` fix/compact-slot-model, `4568b51` Docs; vorheriger
Zyklus-Plan archiviert als `overview-done-2026-09-19-11-29.md`).
**Verdict: CONCERNS** — 1 echte Thread-Safety-Lücke (schmal), sonst kleinkarierte Punkte. Nichts repariert.

## a) Fehlerfrei?

**Merges — nichts halb-tot im Baum:**
- `0af001a` ist history-only: Merge-Tree byte-identisch mit Pre-Merge-HEAD (Claim
  `consolidation-state.md`, `git diff --cached HEAD` leer). Inhaltliche Spot-Checks
  bestätigen: `AiPoAgent` hat **keinen** `compact`-Override (PR #141-Entfernung intakt;
  beide Live-Call-Sites `AIChatView.java:523/:551` sind statisch `AiAgent` →
  `AbstractAgent.compact` `AbstractAgent.java:271`), `ConfiguredChatModel.java:44` hat die
  3-arg `callBlocking(ChatRequest, AgentConfig, AiMonitor)`, `PeonAiService` **keine**
  ungenutzten Imports. Release-2026-09-06 = verifizierter No-Op (Tree-Hash-Identität,
  skip + 🔒 dokumentiert).
- R8/R9/Edit-Guard am Tip intakt (Marker-Grep: Guard-Message `FileUtils`, R9-Kommentar
  `FileLines`, Disclosure-String `AiReponseBuilder` — je 1 Treffer, alle da).

**Befund 1 (einziger substanzieller, CONCERNS): `UserContext.addOneTimeOrders` ist ein
nichtsynchronisiertes `LinkedHashSet` im UI/Background-Kreuzfeuer.** Add:
`AIChatView.java:601` (`resolveOutgoingMessage`, **UI-Thread**, via Send/Mic/`onHandoff`).
Iterate + `clear()`: `UserContext.get()` ← `AgentContextComponent.turnContext()` ←
`PeonAiService.java:498` — läuft auf dem **Background-Job** (Agent-Turn). Fenster:
One-Time-Order per Handoff/Mic während aktiver Turn → `ConcurrentModificationException`
(Turn stirbt im Job) oder still verlorene Order. Regelmäßiger Send-Pfad ist sequenziell
(Add vor Job-Start) — deshalb schmal. AGENTS.md „No single-threaded assumptions" verletzt.
Fix-Klasse: `Collections.synchronizedSet` + iterate-into-copy, oder Concurrent-Queue mit
drain-to-list. **Zusätzlich (selbes Feld-Cluster, niedriger):** die Set-Methoden schreiben
mehrere Volatile-Felder nicht-atomar (`setSelectedResource`/`setJavaType`) → Background-
`get()` kann Zwischenstände rendern (degradiertes Context-Item, kein Crash; `lines()` ist
null-safed, `FileLines.format(textSelection.getText(), …)` nur gegen Empty-Swap).

**Befund 2 (low):** Tool-Config-Felder nicht volatile — `EclipseGrepTool.currentProject`
(`setCurrentProject` UI vs. Tool-Execution im Agent-Job), `DiskGrepTool.workingDir`.
Schlimmstenfalls kurz stale Scope/Dir.

**Edge-Cases der neueren Diffs (alle geprüft):**
- `FileLines.extract`/`format`: null → `""` ✓; CRLF via `dominantLineEnding` ✓; letzte
  Zeile ohne Newline ✓. **Kleinigkeit:** leere Datei → `format("")` rendert `"   1: \n"`
  (Nummer-Zeile für eine 0-zeilige Datei — ehrlichkeits-Randkante, prä-existierend im
  Range-Pfad, von R9 auf Ganzdatei ausgeweitet).
- `applyEdit`: leerer Content + gültiger Anker → IAE „not found" ✓ (Count-Guard), CRLF ✓,
  null/blank/2-char → Guard ✓.
- Grep-Cap: exakt 100 Treffer → alle, kein Disclosure (Boundary korrekt); **Kleinigkeit:**
  der File-Cap-Hinweis feuert bei `fileCount(hits) >= 100` — Korpus mit exakt 100
  Treffer-Dateien bekommt „capped at 100 files" ohne tatsächliche Kappung (beide Familien,
  shared Renderer). Negligible false-positive-Hint.
- Charset-Divergenz disk (`Files.readString` = UTF-8) vs. eclipse (`IFile.readString` =
  Workspace-Charset) ist **prä-existierend** (vor R8 gleicher Read-Pfad), nicht neu.

## b) Tests doppelt?

- **R9 — eine echte Doppel-Pin (Streich-Kandidat):** `FileLinesTest.existingBehaviourUnchanged`
  pinnt das Ganzdatei-Literal (`"   1: alpha\n…"`) **und** `FileLinesTest.extractWholeFileHasLineNumbers`
  dasselbe — gleiche Surface, gleiche Zeile. Streichen: die Ganzdatei-Zeile in
  `existingBehaviourUnchanged` (Swap/Klemmen/Hint-Pins dort bleiben). Alle übrigen R9-Pins
  sind per-Surface (FileLines-Unit / disk-Tool / eclipse-Tool / charset-e2e) → legitim
  („jede Surface pinnt ihr Format").
- **Guard — ein Streich-Kandidat:** `DiskFileWriteToolTest` pinnt null **und** twoChar mit
  derselben IAE-Message an derselben Surface; die trim-Varianten (blank/padded) sind
  Unit-Aufgabe von `FileUtilsTest`. `diskEditFile_twoCharOldStringRejects` streichen,
  `…_nullOldStringRejectsNamingRequirement` als Surface-Pin behalten.
- **R8 — nicht doppelt:** Mode-/Literal-/no-matches-Strings erscheinen in
  `AiReponseBuilderTest` + `DiskGrepToolTest` + `EclipseGrepToolTest` (3×) — aber
  per-Surface-Konvention (Plan §4.4-Kopfregel; Plugin-Tests können Core-Test-Helpers nicht
  teilen). Kein Streichen.
- **UC-DL-99 (VERWAIST, `DocsLinterToolTest:78`): legitimes Fixture, nicht
  Aufräum-Kandidat** — es ist der **einzige** Test, der das Rendering einer VERWAIST-
  Finding-Zeile (`VERWAIST UC-DL-99 file:2`) pinnt (`returnsEveryFindingWithoutTruncation`).
  Eigenes Lint-Rauschen ist der Preis dafür; Besserung ginge nur über eine Linter-
  Suppressions-Feature, nicht über Fixture-Löschung. Lint-Run des Sanity-Pass: 33 Befunde
  (32× UNBELEGT_ERLEDIGT UC-DL-* + diese 1× VERWAIST) — **alle vorbestehend, keine neuen**;
  UC-DL-62/63 definiert + BELEGT.

## c) Architektur?

- **Layering Grep: sauber.** Suche/Matching = core `SearchQuery.matchingLines`;
  Rendering/Caps/Disclosure = core `AiReponseBuilder.grepComplete`; `GrepHit`/`LineHit`
  in `org.sterl.llmpeon.shared` (richtig — beide Familien). Plugin-`EclipseGrepTool` hält
  nur Resource-Traverse, Scope-Präferenz, Derived-Filter, R7b-Refresh + Workspace-Pfade
  (Umwelt-Sorgen) — **keine eigene Suchlogik**. `DiskGrepTool` analog (Walk).
- **FileUtils vs. QualifiedPathValidator: sauber getrennt** — FileUtils = Content-Editing
  (Guard, Count, Line-Op), Validator = Pfad-Vertrag (R5), Eclipse-Projekt-Existenz als
  injizierter `Predicate` (Umwelt bleibt beim Caller). Deep Module, eine Vertrag-Message.
- **Note (kein Befund):** `AiReponseBuilder` liegt im Legacy-Paket `org.sterl.llmpeon.tool`
  (nicht `shared`) — AGENTS.md listet es ohnehin als Shared-Logik; Umzug = Big-Bang
  (verboten). Die `matchedFiles`-Cap-Check-Duplikation über beide Traversals ist die
  genehmigte O(1)-Abweichung (§7) und traval-formgebunden.
- **Instruction-Gap (Item 9):** `docs/architecture.md:5` referenziert „the skill
  `component-architecture`" — existiert unter diesem Namen nicht (tatsächlich:
  `komponenten-architektur`, dessen Description Spring-Boot-zentriert ist), und
  `docs/component-architecture-java.md` (in der Order genannt) existiert nicht.
  Referenz klären (Name in Doc korrigieren ODER Doc anlegen) — PO-Folgeaufgabe.

## d) Lektionen (noch nicht permanent)

1. **AGENTS-DEV / Memory #33 erweitern — Inventar bei Output-Format-Wechseln:** Memory #33
   deckt nur Schwellenwert-Seeds; dieser Zyklus zeigte dasselbe Muster für Format-Pins
   (13 statt 4 Roh-Read-Pins). Regel: bei Änderung einer geteilten Output-Formatierung
   (Read-Nummern, Grep-Zeilen) das Pin-Inventar per Grep des Output-Fragments über **alle**
   Test-Module erheben — nicht aus Plan/Doku übernehmen.
2. **AGENTS-DEV — Merge-Verifikation per Tree-Diff, nicht Commit-Zahl:** „history-only"
   Merge nur nachweislich wenn `git diff --cached HEAD` nach Konflikt-Auflösung leer /
   Tree-Hash identisch (hier: `0af001a`, Release-`45f2a0d2`). Verhindert half-tote
   Konsolidierungen + Review-Noise.
3. Nicht aufgenommen: `@P`-Rename → UC-DL-46 (wird vom Test-Pin selbst beim Gate gefangen —
   Doc-Zeile überflüssig).

## e) SKILLs (Gate-Phase, skill-evolution-Loop: genau ein Outcome)

**Outcome: `no change`** (kein Skill Create/Update/Delete). Begründung je Kandidat:
- **„Edit-Tool-Root-Cause-Triage"** (Pauls Vorschlag: erst oldString-Anker, dann
  Self-Ref, dann Race): Evidence vorhanden (2h-Stress-Jagd edit-tool-insert-Zyklus;
  Edit-Guard-Doc kodiert die Reihenfolge jetzt als Regel) — aber Projekt-lokal und
  prozedural kurz → passt als **Wiki-Pattern** (`skills/wiki/index.md`-Eintrag,
  „problem · root cause · proven response"), nicht als Skill (Prezedenz skill-impact.md:
  Projekt-lokal = AGENTS-DEV/wiki, keine neuen Skills). **Empfehlung:** Wiki-Eintrag als
  PO-Folgeaufgabe + Impact-Log-Zeile „no change (skill), wiki-proposal edit-triage".
- **`komponenten-architektur` [config]:** Description „für Spring-Boot-Backend-Projekte"
  trifft bei der Referenz aus AGENTS.md/docs/architecture.md (Eclipse/OSGi) nicht →
  „wrong/outdated" als Description. Ist aber ein **Config-Skill (quelle: config,
  projektübergreifend)** → Patch-Entscheidung liegt bei Paul (Description stack-neutral
  machen ODER Repo-Referenz korrigieren). Kein Selbst-Patch durch mich.
- **Merge-Tree-Verifikation:** zu einmalig/projekt-spezifisch für ein Skill → AGENTS-DEV
  (siehe d2).

**Most likely reason this breaks later:** One-Time-Order per Handoff/Mic während
aktiver Turn → CME oder verlorene Order im `LinkedHashSet` (Befund 1) — **Änderung mit
größtem Risiko-Rückgang:** `addOneTimeOrders` synchronisieren (synchronizedSet +
iterate-into-copy in `get()`), 5 Zeilen, kein API-Wechsel.
