# Session-Stand — 2026-09-27 (Issue #149 Think-Dropdown ✅; Paul weg — Entscheidungsliste unten)

> Achtung: docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts
> im Chat, Jon schreibt. (Da Mek hatte 2026-09-27 memory.md überschrieben — nicht wiederholen.)

## Branch
- **`story/issue-149-think`** — 8 Commits: `88d01ed5` Inc-1 (core) · `a70d4897` Inc-2 (UI+Clean
  Break) · `b13e60da` Inc-3 (homepage) · `945115df` (WIP rot-Evidenz) + `4a7bbc84` (B1-Fix) ·
  `f4a80592` Inc-4 (Audit-Nacharbeiten + docs). Surefire core **1032/1032**, OSGi 294/294, Lint 0.
  Merge → main + User-Smoke = Paul.

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
1. planImplemented (Archiv) durch Da Mek — nach diesem Commit.
2. Paul: Review-Liste oben + Smoke → dann Merge. 3. Autonome Backlog-Kandidaten: R-CC-16 (next).