# Session-Stand (2026-09-06, Abend — nach Merge)

## Git-Stand (frisch, 2026-09-06 17:28)

- **`main` @ `fbfaaf4`** — 12 ahead of origin, **nicht gepusht**, Working Tree clean.
  Core **651/651** auf main nach Merge.
- **Gemerged (beide `--no-ff`, konfliktfrei außer po-delegation.txt):**
  - `state-config-2026-09-06` @ `f262648` → main (`27464f8`)
  - `toc-estimate-2026-09-06` @ `89a83d1` (+ Strays-Commit `d8cdb7d`) → main (`fbfaaf4`)
  - Merge-2-Konflikt nur `prompts/po-delegation.txt` — beide Seiten erhalten
    (Ownership-Satz + Datei-Hinweis), nichts verworfen.
- Strays committed (`d8cdb7d`): po.txt + ToolService.java (CONTEXT-LIMIT-Regel-Wording),
  streaming-display.md (toc-SOLL-Doku), po-delegation.txt + dev-build-loop.txt (Commit-Rules).
- Feature-Branches intakt gelassen, nicht gelöscht.
## Abgeschlossen ✅ (Zusammenfassung)

- Branch `sm-fixes-2026-09-06` — 4 Commits: Dropdown-Klassen gelöscht (`a1d8d35`) ·
  UTF-8-Write widerlegt + Regression-Guard (`27c09ad`) · PDE-Skip-Count (`11f34f6`) ·
  **Triage #16 gefixt** (`b3e0597`, Test-Barriere). Plugin 186/0 · Core 651/651.
  Merge → main = User-Entscheidung.

- **Zyklus `state-config-2026-09-06`** (8 Commits): inc-1 Housekeeping (Smoke-Fixes,
  Docs-Relocation zu Repo-Root, Prompt-Commit-Regeln) · inc-2 State-Umzug (History →
  `.metadata/.plugins/org.sterl.llmpeon/state/`, getStateLocation via Platform.getBundle) ·
  inc-3 One-Shot-Migration `~/.peon/state` (StateMigration, idempotent, log-only) ·
  inc-4 E5 absolute Pfade · inc-5 Copy-Tool (disk+eclipse, FileUtils.copy) · inc-6 Review-Fixes
  (Directory-Guard-Parität + Mutations-Killer). Review ✅ (Da Thinka, 3-Seiten), Docs geflippt ✅,
  Plan archiviert (overview-done-2026-09-06-16-17.md).
- **toc-Estimator:** ChatMessageUtil chars/3 → ×2/7 (beide Stellen), Floor ≤5→1 bleibt.
  WEIL: User will leichte Über-Schätzung (sah 45 vs 32 t/s).
- **Smoke-Test 2.7.1:** askUser R17 ✅, refreshRoster-Fix ✅ (war beim Merge #131 verloren,
  wieder eingesetzt), Sortierung AGENT_SECTIONS ✅, E5 entschieden (absolut).

## Offen für User (aktive Ansprache nächste Session)

1. **Smoke-Test des Merges:** erster Start migriert vorhandene `~/.peon/state`-Dateien
   automatisch in den Metadata-State — Ergebnis prüfen.
2. **Push-Entscheidung:** main ist 12 ahead of origin — pushen?
3. **5 kleine ❓-Punkte** (open-points.md) — Empfehlungen stehen bereit, „nimm deine
   Empfehlungen" genügt: UTF-8-Write (fixen, Zyklus 2, Read-Tool-Familie) · Glossar eager
   ((b) Turn-Context-Item Jon+Thinka+Mek, NICHT Static) · PDE-Skip-Count (kleiner Fix) ·
   Dropdown-Klassen (löschen) · buildWithDev-Compact (Compact statt Reset, ~50 %, nur neuer Plan).
4. **GO für Bug-Fix-Zyklus:** Triage #5–#16 (Tabelle unten) + ApiRetry-Verdacht (❓ in
   open-points.md: Null-Byte-IOException evtl. als Cancel klassifiziert, verwandt mit
   Memory #21) + Plugin-Hunt.
5. Ideen-Backlog (nicht geplant): Jon×Scaffold-Side-Quest · builtin-agent-prompt-override.md
   (🚧) · eclipse-java-move-type-tool.md (🚧, JDT-Refactoring) · Prompts als .peon-Config.

## Triage-Liste (offen — #1–#4, #9, #13 shipped)

| # | Fehler | Modul | Fix (Jon gewählt) |
|---|---|---|---|
| 5 | `ShellTool`: `join(timeout)` ≠ Sichtbarkeitsgarantie, plain `LinkedList` cross-thread | core | Thread-sichere Liste (CopyOnWrite) + Stress-Test |
| 6 | `findFirst`/`diskDeleteFile`: `Files.walk`-Stream nie geschlossen; Delete meldet „Deleted:" trotz still übersprungener Fehler | core | try-with-resources; Teilerfolg benennen („Deleted N of M, failed: …") |
| 7 | `AiModelParser`: Parse-Fehler → `printStackTrace` + leeres Catalog, Root Cause verloren | core | Root Cause loggen (warn), leere Liste bleibt |
| 8 | `ThinkResolver.toReasoning`: „True"/"False" rutschen durch, Off-Tokens verbatim an LM Studio | core | Case-insensitive Normalisierung; Off-Token→"off", sonst→"on" |
| 10 | `VoiceInputService.transcribe`: kein Timeout, `f.get()` unbounded | core | HttpRequest-Timeout + `f.get(30s)` → Timeout = Fehlermeldung |
| 11 | `searchComplete`: Limit-Cap **ohne** Disclosure (grepComplete hat sie) | core | „showing N of M" / Cap-Disclosure wie grepComplete |
| 12 | `FileLines.extract(0,0)` → RAW-Content ohne Zeilennummern, Javadoc sagt 0 → 1/last nummriert | core | 0 als 1/last → nummriert (disk+eclipse konsistent) |
| 14 | `AnthropicProvider.listAiModels`: hardcodet `api.anthropic.com`, ignoriert custom `baseUrl` (Proxy→401) | core | `baseUrl` aus Config nutzen |
| 15 | `VoiceInputService`: doppeltes `startRecording` leakt die alte Line | core | Alte Line vor neuem Start schließen |
| 16 | `ModelListCacheTest.concurrentGetOrFetch_sameIdentity_singleFlight` timing-flakig (Full-Run 1× rot, solo 5/5 grün) | core | ✅ **gefixt 2026-09-06** (`b3e0597`, Branch `sm-fixes-2026-09-06`): Test-Bug — zweite Barriere (waiterStarted-Latch + WAITING-Spin vor release) wie im Schwestertest; Produkt sauber (`putIfAbsent` atomar). 5× solo grün, Suite 651/651. |

**Ablauf pro Fehler (User-Vorgabe):** Rot-Test (Da Mek) → Jon prüft Rot-Test → Fix (von Jon
gewählt) → Grün → Commit. Inkremente klein bündeln (2–4 Fehler/Increment), Review via Da Thinka
am Ende. **Plugin-Hunt** (Da Mek) steht noch aus.

## Geparkt

- **Edit-Tools** → [open-points.md](open-points.md): Rename auf "Edit", gemeinsame Doku (4 Tools),
  `planEdit`-Count, Eclipse-Doku-Konflikt, `AiFileUpdate`-Nebenbefund, **Line-Ending-
  Normalisierung** (User-E2E-Spec 2026-09-05, ersetzt E3-Skip). Reihenfolge: Rename-Inkrement →
  Doku → Count-Fix → Line-Ending. E2E-Spec: `org.sterl.llmpeon.test/ai-e2e-test/file-edit-tools.txt`.
  ⚠️ Item 3 (Line-Ending) NICHT gebaut — dort rot im E2E = erwartet, keine Regression.
- Cleanup-Kandidaten (eigener Zyklus): `StreamingBridge.clock`-Feld redundant; `EclipseUtil.editInEditor`
  Dead Code (0 Referenzen); leerer `/org.sterl.llmpeon/docs/adr`-Restordner.

## Skips (dokumentierte Entscheidungen / bereits getrackt)

- Unbounded Query-Caches (`SearchQuery.CACHE`, `RegexUtils.GLOB_CACHE`) — ⏳ in open-points.md
- `ModelListCache` ohne Eviction — dokumentiert (ConfiguredChatModel-Javadoc)
- `SearchAgentTool` teilt parent `ApiRetry` — Design-Eigenheit
- `FileAgentHistoryStore` History-Wipe bei korrupter Zeile — dokumentiert
- `McpService` Connection-Wipe bei Fehler — dokumentiert

## Noch offen aus Release-Zyklus (2026-09-03/04)

- ⏳ unbegrenzte Query-Caches (siehe Skips)
- issues/fact-issues.md: Punkt 3 (CancellationException-Stacktrace als Error), Punkt 5 (Node-20-Deprecation)
- Untracked: `release-notes-2026-09-04.md`

## Docs-Lage (wichtig)

- docs/** = `/llmpeon-parent/docs/` (Repo-Root). Die 4 versehentlich unter
  `/org.sterl.llmpeon/docs/` gelandeten Docs sind nach Root verschoben, falscher Baum gelöscht
  (leerer adr-Restordner bleibt, siehe Cleanup).
- Neue Docs heute: peon-config-directory.md (✅) + ADR-0041 · configuration.md (Landkarte) ·
  file-copy-tool.md (✅ R1–R4) · eclipse-java-move-type-tool.md (🚧) · builtin-agent-prompt-override.md (🚧).
- open-points.md: ❓ ApiRetry/Null-Byte-IOException (NEU) · ❓ UTF-8-Write · ❓ Glossar eager ·
  ❓ PDE-Skip-Count · ❓ Dropdown-Klassen · ❓ buildWithDev-Compact · ⏳ Query-Caches · ⏳
  Streaming-Timing-Präzisierungen · ⏳ Edit-Tools geparkt.
