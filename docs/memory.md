# Session-Stand — 2026-09-21 (Tool-Evolution Build-Zyklus)

## Wo wir stehen

**Branch `analysis/tool-evolution`** — Paul hat IDE neu installiert (2026-06, Target bleibt 2026-09).

- ✅ **Story A** (Disclosure + Web-Tools) — done 2026-09-20.
- ✅ **Story B** (Project-Problems-Filter) — done 2026-09-20 (`e84fcbc`, Archiv `overview-done-2026-09-20-14-02.md`).
- ✅ **Story C** (Java-Debugger) — **komplett 2026-09-21:** Plan → I1–I4 → Da-Dok-Review CONCERNS →
  Fixes (`ca2793a`, Mutation-Nachweis Session-Gate) → E2E-Smoke 2 Runden (F1/F7/F3 gefixt `2efaaf6`,
  F2 = Backlog) → UC-IDs gemappt (`8de750c`) → **Flips ✅** (R-JD-1…8, UC-JD-1…9; UC-JD-2…6 manuell
  verifiziert per ADR-0051). ADRs 0049/0050/0051 geschrieben. Lint: nur bekannte Befunde
  (34× UC-DL, 5× UC-JD-2…6 manuell, UC-DL-99).
- **Wartet:** Da Mek `planImplemented` (nach Commit meiner Docs-Flips) + Ledger-Eintrag Skill
  `eclipse-dpe` (Create, `2c1f2de`) + Management-Summary für Paul.

## Nächste Schritte

1. Da Mek: Docs committen (java-debugger-tool.md, index.md, ADRs 0049–0051, open-points, memory.md)
   + `planImplemented` + Skill-Impact-Ledger für `eclipse-dpe`.
2. Management-Summary mit Skill-Evolution-Abschnitt; Merge/Squash = Paul.
3. DocsLinter-Mini-Zyklus (idPattern-`@P`-Description + UC-DL-99 + „manuell verifiziert"-Marker-Idee).
4. ApiRetry-Follow-up + Issue #142 (Paul-Korrektur + Fix-Entscheid → ADR) — geparkt im Backlog.

## Bekannt & bewusst out-of-scope

- Lint: 34× `UNBELEGT_ERLEDIGT UC-DL` + `VERWAIST UC-DL-99` + 5× UC-JD-2…6 (manuell, ADR-0051).
- F2 (Debugger-Backlog), Exception-Event-Info, build.properties-Warnung — alle in open-points.md.

## Was nicht neu aufgemacht wird

CR-1/2/7 abgelehnt · CR-5/CR-6 geparkt · Copilot-Server nicht decompilieren · keine Confirmations für
Debugger · Target-Rollback auf 2026-06 nur IDE (Target bleibt 2026-09 — sonst bricht Story C).

## Lektionen (Zyklus)

1. API-Contract gegen die **tatsächlich aufgelöste** Target-Generation verifizieren (Plan-§3 ging 2×
   daneben: debug.core-Drift, getRootThreadGroups-vs-getThreads → diagnose.txt-Beweis).
2. Smoke vor Flips hat sich doppelt bewährt: F1/F7 wären sonst als ✅ geflippt worden.
3. Full-File-Rewrites von Docs: Frontmatter (`idPrefix`) nicht verlieren — PRAEFIX_FEHLT-Lauf hat's
   sofort gezeigt (Linter als Sicherheitsnetz, gut).
4. Flaky-Test stoppen statt iterieren (Hard-Stop half; ADR-0051 hält die Decision).
