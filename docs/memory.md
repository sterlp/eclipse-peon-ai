# Session-Stand — 2026-09-28 (Widget-Zyklus gebaut + reviewed; #149 + Widget warten auf Paul-Smoke/Merge)

> Achtung: docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts
> im Chat, Jon schreibt. (Da Mek hatte 2026-09-27 memory.md überschrieben — nicht wiederholen.)

## Neu 2026-09-28: Model Config Widget + Think-Default — ✅ GEBAUT (wartet auf Paul-Smoke/Merge)

- **Zyklus komplett durchgebaut** (Jon gab Build-Freiagbe nach SOLL-Klärung; Branch `story/model-config-widget` auf Basis `story/issue-149-think`): Inc-0 Think-Default-Fallback (`c90debfb`, core Surefire **1040/0**) · Inc-1 ModelConfigWidget (`df4293fe`) · Inc-2 Basic-Page-Umbau (`a9691449`, OSGi Surefire **301/0** + 20 Headless-Skips = bestehendes „Workbench not created"-Muster; SWT-Tests laufen im PDE-Runner) · Inc-3 Homepage (`99c5aecb`) · Inc-4 Skill `eclipse-preferences` + dpe-Section verlagert (`5fd04c42`).
- **Da-Dok-Review:** REJECTED → 2 Blocker von Mek behoben: (1) Surefire-Ground-Truth nachgereicht (die 298/301 waren Eclipse-Runner-Zahlen — Memory-Regel 28 zutiefst bestätigt), (2) Docs-Flips PO-Aufgabe. Mutations-Nachweis applyThinkValue („nie stiller Ersatzwert"): red→green. Non-Blocking: waitUntil 3× dupliziert → Extract-Kandidat; index-basierte Child-Lookups spröde.
- **Status-Flips gemacht (Jon):** R-MCW-1…6 ✅, R-THINK-10 ✅, index.md, ADR-0060/0061 Accepted. Lint (Scope MCW|THINK): 17/17 UCs, 63 Test-IDs, 0 findings.
- **Think-Default (ADR-0061):** leerer Agent-Think erbt Base-Think (Dev-Slot); explizites off gewinnt; Custom erbt gleich. Basic-Think-Feld = Default-Editor (Label „Think (Default)").
- **Docs+Plan committed ✅** (Mek `9ac3d587` docs/** + Plan, `e4e705a7` Archiv, `planImplemented` ausgeführt → `peon-plan/overview-done-2026-09-28-14-09.md`). Merge-Reihenfolge: erst #149, dann Widget-Branch (Basis ist #149). User-Smoke: beide Branches gemeinsam (Think-Dropdown, think:false im Log, Widget-Felder 1–5 untereinander, Provider-Wechsel → Think-Form folgt, Reload ohne Apply, Cancel verwirft).
- Auffällig: altes Archiv-Artefakt `org.sterl.llmpeon/peon-plan/overview-done-2026-09-27-17-02.md` (#149-Zyklus, untracked, am falschen Ort) — Mek hat es bewusst außerhalb des Order-Scope gelassen; Aufräumen mit dem #149-Merge-Commit.
- **Zwei ⏳ aus dem Widget-Bau (open-points):** waitUntil-Extract in AbstractSwtUiTest; per-Agent-Provider-Override-Story (❓, bereits eingetragen).

## Branch — konsolidiert (Paul-Order 2026-09-28)
- **EINE Branch: `story/issue-149-think` @ `0bf93cb0`** — Widget-Branch wurde fast-forward gemerged (`f2059191..e4e705a7`, 24 Dateien +1698/−106, 0 Konflikte) und `story/model-config-widget` gelöscht (`git branch -d`). Archiv-Strunk aufgeräumt (`0bf93cb0`: `org.sterl.llmpeon/peon-plan/overview-done-2026-09-27-17-02.md` → `peon-plan/`).
- `release-2026-09-06` existiert weder lokal noch auf origin — nichts zu löschen. Remote-Reste (nicht Pauls Ziel?): `fix/nextids-bug-report`, `git-support`, `story/compact-input-budget`.
- **9 Commits ahead of `origin/story/issue-149-think`** — Push noch nicht gemacht (Jon-Entscheidung, default: nach Paul-Smoke).
- Merge → main + User-Smoke = Paul.

## Issue #149 — ✅ FERTIG (wartet auf Paul-Review)
- **Fix:** Boolean-off kollabierte in Persistenz zu unset (`ThinkValueSupport.booleanValue(false)`→`""`
  → Saver entfernt Key → Loader null → Ollama-Field weg). Neu: EIN Think-Level-String — leer=unset=
  nichts senden, Off-Tokens case-insensitive → Ollama `think:false`, editierbares Dropdown
  (`ThinkSupport.Toggle`), `think_supported` komplett raus (auch Custom-Frontmatter, legacy
  leskompatibel on>off>support, migrate-on-write), Basis-Checkbox raus. ADR-0059, R-THINK-1…9 ✅.
- **Provider-Think-Audit (Da Dok, Paul-Order):** 9 Provider geprüft — Interpretation überall
  korrekt; **B1 Copilot `returnThinking` gefixt** (echter Bug, Parität OpenAI); B2/B3 toter
  Alt-Semantik-Code gelöscht; B4/B5 Javadocs; B6 E2E-Legacy-`false`. Mutations-Nachweis
  toOllamaThink-Blank (3 Tests rot, incl. UC-THINK-1×2 + UC-THINK-7).
- **Paul-Smoke steht aus:** Ollama-Dropdown, Basis-Checkbox weg, `think:false` im Debug-Log,
  Legacy-Frontmatter-Write.

## Entscheidungen für Pauls Review (alle in resolved-points.md)
1. Leeres Think = unset (Option A) — Paul bestätigt.
2. `think_supported` komplett raus (auch Custom-Frontmatter, nur noch `think`) — Paul bestätigt.
3. Legacy on-string > think_supported:false (Jon autonom, Thinka-Empfehlung).
4. LM Studio: ALLE Off-Tokens → reasoning=off, generic-on → on (Jon autonom; **Verhaltensänderung**).
5. Rot-erst-Strategie: Inc-1 grüner Schutz + gepinnter IST-Test, Inc-2 echter Umschalt-Regressor.
6. B1–B6 Audit-Fixes im Inc-4 (Jon autonom).
7. Da-Dok-Review-Non-Blocking: Docs-Scope-Drift-Commits (index/memory/advanced-config in Inc-1) —
   künftig Doc-Dateien im Plan benennen; ROT-Evidenz künftig WIP-Commit (ab Inc-4 umgesetzt).

## Offen (open-points.md — neu)
- ⏳ Think-BDD-Lücken (a)–(g) + `Entry.off` ungenutzt + `AiAgent.isThinkEnabled` deprecated.
- ⏳ User-Smoke Issue #149. Vorherige offene Punkte unverändert (R-CC-16 ❌, Bug B, compactSession-
  Exposition, Header 0k, Fixture-Drift, Option A, Docs-Sweep, R-CC-7, Issue #142, Merge = Paul).

## Nächste Schritte
1. ~~Da Mek: committen + planImplemented~~ ✅ erledigt (`9ac3d587`/`e4e705a7`, Plan archiviert) — nur noch der untracked alte Archiv-Strunk im Plugin-Ordner offen.
2. Paul: gemeinsamer Smoke **beider Branches** — Merge-Reihenfolge: erst `story/issue-149-think`, dann `story/model-config-widget` (Basis ist #149). Smoke-Punkte oben („Neu 2026-09-28") + bei #149.
3. Autonom danach: R-CC-7 (next), Think-BDD-Lücken (a)–(g), per-Agent-Provider-Override-Story (❓ in open-points.md).

## Paul-Feedback 2026-09-27 (Config-UI)
- Widget-vs-Store-Stale-Reads in Preference-Pages: eclipse-dpe-Skill erweitert (Abschnitt
  „Preference pages — read widget state, not the store"). Asymmetrie Check-Host (liest Widget,
  AiConfigPreferenceView:121) vs. Reload-Models (liest Store, R-ML2-SOLL: erst Apply) —
  Mek-Analyse bestätigt R-ML2 als dokumentierte Entscheidung; SOLL-Änderung nur auf Paul-Wunsch.