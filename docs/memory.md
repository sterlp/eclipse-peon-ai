# Open Ends / Memory — Session-Zwischenstand

> Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> **Branch `web-factory-4`** (Paul, 2026-11-09). `web-factory-mvp` ist als #170 gemergt.
> Lokal ungepusht (Push 403, Git-Account `PaulNetze`): Merge main `7c270466` + Skill-Sync-Commits.
> Paul überträgt per `peon-plan/web-factory-4-skill-sync.patch` auf den anderen Rechner
> (dort erst `git merge origin/main`, dann `git am`).

## Skill-Sync 2026-11-09 (Paul-Auftrag, kein Plan-Zyklus)

- Ziel (Paul): jedes Projekt entwickelt eigene Skills unter `.agents/skills/` (CRUD je Zyklus),
  generische AGENTS*.md-Passagen wandern schrittweise in Skills. Grund-Loop läuft auch ohne Skill
  `skill-evolution` und ohne Wiki → steht jetzt in `po-delegation.md` (Retro), R6 ❌ in
  `core/docs/skill-evolution-loop.md` (Flip nach Pauls Bestätigung / erster Nacht-Retro).
- `doc-method.md`: neue Regel „Wechsel auf ✅ zieht den ganzen Regelblock mit" (aus Wiki-Muster
  Status flip, Teil 3 Disclosure bleibt im llmpeon-Wiki).
- Wiki `.agents/skills/wiki/` überall; Muster „Rename-Zyklen: Verbraucher inventarisieren" nach
  us4g/FORgE/MaPa. us4g committet+gepusht (`97008d6`, inkl. `AGENTS-PO.md`→`AGENTS-JON.md`).
- FORgE: direkt auf `main` + Push; Projekt-Kopie `skill-evolution` entfällt (nutzt den globalen).
  MaPa: direkt auf `master`, **Paul pusht** (Repo-Policy). us4g: auf `main` per Cherry-Pick + Push.
- Global `skill-evolution` (sterlp/ai-skill-codex via `~/apm.yml`): Paul kopiert die 2 Dateien aus
  `peon-plan/ai-skill-codex/skills/skill-evolution/` selbst ins Repo und synct.
- Lose Enden: (1) us4g hält noch eine Projekt-Kopie `skill-evolution` (identisch zum neuen
  globalen) — löschen, sobald der Feature-Branch `feature/stub-callback-endpoint-header` gemergt
  ist (sonst modify/delete-Konflikt). (2) ai-skill-codex enthält `skills/wiki/` mit llmpeon-Inhalt
  — Empfehlung an Paul: dort löschen.
- Working Tree llmpeon: `SkillService.java` (nur Zeilenumbruch) + `.factorypath` nicht von Dev —
  Pauls IDE, nicht committen.

## Nächster Schritt

- Story „Skills ohne Cache": `SkillComponent` parst beim Laden, `skillRead`/`skillList` liefern den
  Cache; neu geschriebene/geänderte Skills sieht ein Agent erst nach Projektwechsel/`refreshAll`.
  Paul will: list/read lesen immer von Platte (Nacht-Evolve). Rule in `core/docs/project-skills.md`
  mit Paul klären, dann Plan.

## Offen für Paul (je eine Frage / Bestätigung)

1. **⏳ R-PT-8:** Query auf geschlossenes Projekt meldet `onProblem` statt still „kein Plan" — ok?
2. **⏳ AGENTS-JON.md-Alias** (2026-10-30, open-points.md) — der Rename-Fall hat us4g getroffen
   (`AGENTS-PO.md` wirkte still nicht mehr), dort jetzt umbenannt.
3. **Homepage-Zeile zur Diff-Anzeige?** (optional)
4. ❓ Altbestand Reparatur-Zyklus: Edit-Tool-Falle (upstream melden?), manuelle Nachweise.

## Backlog (nicht gestartet)

- **Search-Agent als vollwertiger interner Agent** (R-LSR-7 🚧, search-agent-tool.md) ·
  **Rename-Sicherheit über stabile `id()`** (agenten-namen-im-chat.md 🚧).
- **P3:** SSE-Client-Kopien (5×) in Fabrik-Tests extrahieren — eigenes Gate.
- **P1-Variante:** Cancel-/Fehler-Pfad-Release ohne atomaren Queue-Blick — test-first (⏳).
- Async-Shell-Tool (Paul-Prio) → async Agents; Merge-Check „geteilte disk-Tool-Instanzen" vorher.
- Bug-Triage-Reste: totes MCP-Tool in Spec-Liste, `PoDelegateTool.dispatch`-Catch, ApiRetry-
  Fehlerklassen, Header-State-Leak, Auto-Compact bei Context-Overflow, `org.sterl.llmpeon.test`
  Surefire-Basis. Neu: Warnungs-Aufräum-Zyklus (5 `@SuppressWarnings`-Dateien) · „Kein `throw`
  ohne Message" · ApiRetry-Null-Byte-IOException-Verdacht (❓ open-points).
- Klein: `MutableClock` 7× → Fixture · `AbstractAgent.asyncCall` ohne Aufrufer · Lint-Baseline
  Core · historische `*-plan.md` löschen? · `StringUtil.oneLine`-Konsolidierung ·
  `effectiveUrl`-Schnitt · Ork-/Peon-Altbestand ~35 SOLL-Docs (Massen-Rename braucht Paul).

## Notizen

- Agentennamen: Jon (PO), Plan, Dev, Review — Ork-Namen (Da Mek/Da Dok/Da Thinka) nur noch als
  Synonym im Glossar, in neuem Text nie.
- System-Prompts generisch; Scaffold-Prompts stehen auf `Scaffold` (Jon editiert System-Prompts
  selbst, Übersicht: `core/docs/system-prompts.md`).
- Commit: EXPLIZITE Pfadliste; Pauls WIP nie (`AGENTS*.md` Root, `peon-plan/**`, `README.md`,
  `ai-e2e-test/**`, `bugs/**`, `homepage/**` nur auf Ansage).
- Lint: `docRoots`/`testRoots` weglassen = Auto-Discovery (R-DL-26 + AGENTS-DEV-Präzisierung
  2026-11-05 — teilweise Listen erzeugen Massen-Findings); `findings:`-Zeile melden, nie
  `Scanned:`; `idPattern` **mit** `[a-z]?`.
- JUnit `@Timeout`-Falle: Daemon-Probe-Muster statt `@Timeout` (memory 50).
- Eclipse-Plugin-Tests: `eclipseBuildProject` vor JUnit-Lauf; Einzelklassenlauf einer
  bestehenden Klasse ist trust-frei (~6 s).
