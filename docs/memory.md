# Session-Stand — 2026-09-30 (Cleanup + Push, Paul-Smoke ✅)

> Achtung: docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts
> im Chat, Jon schreibt. (Da Mek hatte 2026-09-27 memory.md überschrieben — nicht wiederholen.)

## AKTUELL 2026-09-30: Post-Smoke-Cleanup (Paul-Order)

Paul: „smoke war erfolgreich — docs saubermachen und alles pushen — alte pläne löschen — release
notes in english schreiben."

1. **Paul-Smoke ✅ 2026-09-30** für alle 5 Zyklen (R-DEF-9…11, R-THINK-11/12, Default-Inheritance,
   Model Config Widget, Issue #149) — in Docs markiert: index.md (4 Stellen), per-agent-think.md
   (R-THINK-4/5), model-config-widget.md, compact.md (R-CC-15), user-context.md,
   open-points.md (Issue-#149-Punkt 🔒).
2. **8 Plan-Archive gelöscht** (peon-plan/overview-done-2026-09-26…09-30) — peon-plan/ leer.
3. **CHANGELOG.md (Englisch) im Repo-Root angelegt** — Paul-Entscheidung: Repo-Root, nächste
   Version **2.12.4** (Letzter Tag: 2.12.3, 43 Commits seitdem: Think 14, Model-Config/Inheritance
   21, Housekeeping 8). Keep-a-Changelog-Stil; ältere Releases → GitHub Releases verlinkt.
4. **Push:** story/issue-149-think (war nur inc-6 `d77ea570` ahead auf origin) — Mek committet
   CHANGELOG.md + docs/** + peon-plan-Löschungen, dann `git push`. **Merge nach main = Paul**
   (nicht angeordnet, nicht machen). `release-2026-09-06` existiert nicht mehr (lokal+remote weg).
   `homepage/.vitepress/dist` ist NICHT tracked — kein stale-Dist-Problem. Keine Stashes.


## Grep-Tests 2026-09-30 (Branch `bug/grep-empty-payload-test`, NICHT gemerged)

- Paul-Verdacht: Grep „Leerfall" gibt zu viel zurück → Overflow. **NICHT bestätigt, gemessen:**
  0 Treffer = 68 Bytes (klein, ehrlich: no matches + regex search + Scope); 550 Treffer/110 .md
  = ~5 KB, exakt 100 Zeilen, disclosed (`showing 100 of 500` + `capped at 100 files`).
- 4 Regression-Guard-Tests von Da Mek: A `emptyMdGrepPayloadStaysSmallAndHonest` (EclipseGrepToolTest),
  B `grepCompleteEmptyResultLeaksNoFileContent` (AiReponseBuilderTest), C
  `manyMdMatchesAreCappedAndDisclosed`, D `grepCompleteManyHitsAreCappedAndDisclosed`.
  Commits `9c709187` + `ef140197`. Paul: Tests behalten ✅. Merge nach story/issue-149-think = Paul.
- Bekannte Lücke (kein Bug im Szenario): Cap = Zeilenzähler, kein Byte-Cap pro Zeile — Riesenzeilen
  können Output über 100 Zeilen hinauswachsen. Als Punkt zu open-points, Fix nur auf Paul-GO.
- Overflow real woanders: Agent-Retry nach „no matches" mit anderen Tools / Kontext-Akkumulation /
  Compact-Overflow (memory 29) → gehört in den Context-Overflow/ContentProvider-Zyklus.
- Paul meldete nächsten Testwunsch an („Sekunde") — abwarten.

## Replace/Indent-Tests 2026-09-30 (Fortsetzung, Branch `bug/grep-empty-payload-test`)

- 5 Guard-Tests (Commit `7edf7afc`): T1 diskEditFile / T2 eclipseEditFile (`'aaaa'`→`'    aaaa'`,
  newString ⊇ oldString — Single-Pass `String.replace`, kein Loop, kein Trim), T3/T4
  eclipse/diskReplaceLines (Delete-vor-Insert sitzt, FileLines.replaceLines:98), T5 planUpdate
  (kein Trim). Core 1055/0, Plugin 318/0. **Kein Tool-Bug — Paul bestätigt: LLM-Problem.**
- Paul-Entscheidungen 2026-09-30:
  1. Guards behalten ✅ (Familien teilen `FileUtils.applyEdit` / `FileLines.replaceLines`).
  2. **replaceLines-Fix gebaut (❌→Plan):** Out-of-Range-Zeilennummer → ehrlicher Fehler statt
     stiller Clamp auf letzte Zeile (stale Line-Number ersetzte still die falsche Zeile —
     widerspricht „tool must never lie"). Gemeinsame Logik `FileLines` → beide Familien.
  3. **applyEdit-Substring = RICHTIG, kein Fix, kein offener Punkt.** `'aaa'`→`'bbb'` auf
     eingerückter Zeile ist legitimer Substring-Fall; Zeilen-Anchoring = umgekehrter Fehler.
  4. **Tool-Description-Update (Paul-Entwurf, beide Familien identisch):** „Replace exact,
     whitespace-sensitive occurrences in a file. oldString must uniquely identify the target in
     its line context; if it matches multiple locations, all are replaced. Min 3 non-whitespace
     chars; reports count. newString=null deletes."
  5. **KEINE** neuen technischen Guards / Warnung bei newString ⊇ oldString (legitimes Muster).
- Pauls Feature-Idee (SOLL-Gespräch läuft): **Tool-Name am Diff im Chat sichtbar machen** —
  er sieht nur das Diff, nicht welches Tool es erzeugt hat (editFile vs replaceLines wäre
  sichtbar — genau die Indent-Verwechslung der LLM-Footgun).

## Overflow-Diagnose 2026-09-30 (Thinka 974k vs n_ctx 170240)

- Thinka starb mitten im Planen (replaceLines-Fix + Descriptions) mit 400 `exceed_context_size_error`;
  Status zeigte 23k. **clearPlan auf Thinka** — Compact wäre selbst ein 974k-Call → Overflow-Gefahr.
- Grep gemessen: liefert **Zeileninhalt**, Cap = 100 Zeilen **ohne Längenlimit pro Zeile** →
  minified .js = ganze Datei als „eine Zeile". Hauptverdacht für 974k (Thinka grep `replace` = 850
  Treffer workspace-weit). eclipseGrepFiles braucht workspace-qualifizierten Pfad (`/llmpeon-parent/docs`)
  — „docs" ohne Projekt = leerer Scope (Jon-Bedienfehler, kein Tool-Bug).
- Paul-Order: 1) Docs ❌ schreiben (erledigt), 2) aktuellen Plan finalisieren + bauen **✅ (Inc 1
  `e97ef512` replaceLines-Fix, Inc 2 `0be24315` Descriptions, `135d7ac0` Inventory-Sync — Branch
  erstmals gepusht)**, 3) Logging/Cap-Batch bauen — SOLL komplett freigegeben:
  **R-RS-1/2** (tool-result-size.md, `(N chars)`) · **R-OD-6** (Cap 2000, Paul ✅ nach Erklärung) ·
  **R-CC-17** (Biggest-Message-WARN, Compact-only — Paul 🔒: 400-Pfad NICHT instrumentieren) ·
  **R-AEM-1/2** (agent-error-memory.md NEU, ❌: terminale Fehler → e.getMessage() ins Memory,
  nie Cancel, Dedup 1×, Einfügepunkt AiChatView-catch). **✅ BATCH GEBAUT + REVIEWED 2026-09-30:**
  `0be9a4c5` (R-RS) · `d7cf6578` (R-OD-6) · `4f7cab05` (R-CC-17) · `53e54c5e` (R-AEM) · Delta
  `f77cbef0` (R-RS-Nachzug reloadConfig + webFetch Option A nach Da-Dok-REJECTED: 2 Sites verpasst;
  findImplementations = toter Code, open-points). Core 1085/0/0, Plugin 320/0/0. Status-Flips ✅
  erledigt, Lint 0. **Ausstehend: Paul-Smoke AiChatView-Wiring** (terminale Fehler → 1× in History,
  Abbruch → keine Insertion) · `planImplemented` (Archiv) · Commit offener docs/** + Push.

## Nächste Schritte

1. **Mek:** Commit (CHANGELOG.md, docs/**, peon-plan-Del) + Push → dann dieser Stand ok. **✅ erledigt 2026-09-30 (`37849c47`, pushed).**
2. **Merge nach main** = Pauls Entscheidung (Ansage offen).
3. **Danach (Paul-Order): Context-Overflow/ContentProvider-Bug-Zyklus** — Thinka 954k nach
   clearPlan (per-Request-Injektion, nicht Agent-Memory), Mek 431005 vs. n_ctx 170240, webFetch
   „Reading https://…" = 500k. Evidenz: open-points.md ❓ auto-compact-Eintrag. Logs nur mit
   searchAgent lesen (runtime-EclipseApplication/.metadata/.log, GROSS).
4. Backlog: AGENTS-Trim (wartet Paul-GO, inkl. toter „komponenten-architektur"-Skill-Link in
   AGENTS.md:43) · Scaffold-als-Jon-Delegat (SOLL-Gespräch offen) · TrimService-Story (⏳) ·
   R-CC-7 · Think-BDD-Lücken (a)–(g) · per-Agent-Provider-Override (❓) · Linter ☠️-Status (❓) ·
   ApiRetry (memory 21, 5× Evidence, +503 „Loading model") · Homepage advanced-configuration.md:53
   high/medium/low/minimal vs. Dropdown none…xhigh (präexistente Unschärfe).

## Gelöste Alt-Punkte (dieser Zyklus)

- Paul-Smoke 5 Zyklen ✅ (2026-09-30) — Push/Merge-Pflicht ging an Paul zurück, er will pushen.
- R-CC-15 Nested-Agent-Parent-Memory ✅ gesmoked.