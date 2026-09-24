# Session-Stand — 2026-09-23 (Hotfix „Ehrlicher Compact" ✅ `d9786ba` · Tool-Polish-Mini-Zyklus noch offen)

## Wo wir stehen

**Branch `fix/simple-diff`** (von main `b42a3be`) — SimpleDiff-Fix läuft. Alt-Branch
`analysis/tool-evolution`: merged als `a4eeea6` (#144) + upstream gelöscht — Pauls
„Branch-Inhalt löschen" damit bereits erledigt, Working Tree clean.

- **✅ Hotfix „Ehrlicher Compact" (`d9786ba` + Archiv `edc2a42`):** R-CC-1..4 + R-CC-6 —
  Kontext-Zähler = `inputTokenCount()` statt Kosten (ADR-0055), Reevaluate nur bei COMPACTED
  (sticky `compactedThisTurn`), `CompactResult` statt stiller Lüge (onProblem ein Wortlaut, 4
  Stellen), Hint-Dedup, Estimate-Disclosure `~N (estimate)`. Surefire 930/0/0/0 (+10), Plugin
  279/0/0, Mutations-Nachweis ech gemessen. Docs: compact-context-counter.md (✅), ADR-0055.
  **R-CC-5 (Dedup-Truncation) gestrichen** — Analyse war falsch (UserMessage wird nie trunciert);
  echte Ursache der Aufsummierung = Workspace-Memory-Hash-Key (ADR-0032, by design) → ❓ in
  open-points.md.
- **Docs der Tool-Polish-Story (java-debugger Rename, ADR-0054 + Index, inventory,
  queued-user-messages) sind mit #144 in main gelandet** — der **Build (I1–I4) läuft trotzdem
  noch nicht** (Fachdoc: Rename + R-JD-13 weiter ❌, Queued-At Regel 9 ❌).

## Nächste Schritte

1. **Tool-Polish-Mini-Zyklus** (I1 Rename `debugJava*` + Referenzen · I2 R-JD-13 Breakpoints
   ohne Session · I3 QueuedAt-Regel 9 · I4 Homepage-Nachziehen) — Pauls 4 Entscheidungen vom
   2026-09-23 stehen im SOLL (❌), Plan war nicht angefangen, als der Hotfix kam.
2. Paul: Compact-Lock-Smoke 3+4 → Flips UC-CT-1…6 (UC-CT-3/5/6 stehen als UNBELEGT im Lint) →
   Compact-Lock-Plan archivieren.
3. Paul: Debugger-Re-Run 3b/3.2 mit Clean-State (F1/F2-B).
4. Paul: Compressor-Empty-Root-Cause — Error-Log (`log.warn „Empty compact message received"`)
   + Compact-Model-Config schicken.
5. Eclipse-Restart → Dogfood-Lint (stale UNBELEGT_ERLEDIGT-DL-Bestand + UC-PP/OD/TD/SEL/CT
   auflösen) — Linter-Report 2026-09-23: 74 findings, fast alle Alt-Bestand (docs-linter.md,
   java-debugger-tool.md, project-problems-tool.md etc.), KEINE vom CC-Hotfix.

## Smoke-Liste (manuell, Compact-Lock CT-3…6) — 1+2 ✅ Paul

3. Compact fehlschlagen → Queue trotzdem Follow-up.
4. Slave-Compact → nur Slave 🟢, Da Boss aus (Blatt-Regel), kein Follow-up am Boss.

## Geparkt

ApiRetry-Follow-up (Memory #21) · Issue #142-ADR · build.properties-Warnung · DL-Sweep ·
🟡-Indicator · „!-Messages" (Regel 8) · Refire-after-hitCount · Workspace-Memory-Vollkopie je
memoryAdd (❓ open-points.md, neu) · Anthropic cache_read-Undercount (ADR-0055 Pitfall).

## Lektionen (Zyklus)

1. **SOLL-Mechanismus am IST prüfen, bevor die Regel ins Doc geht:** R-CC-5 basierte auf meiner
   falschen Truncation-Analyse — Da Thinka fing es im Plan, Da Mek verifizierte es (UserMessage
   nie trunciert). Unbestätigte Befunde ersatzlos streichen, nicht „kein Bug"-nachhalten.
2. **RED-Test muss den letzten Schreiber pinnen:** `addResult` ist Replace-Semantik — ein Test
   hinter `executeLoop` muss die finale Usage/Re-Derive-Reihenfolge kennen (Cancel-Break als
   deterministisches Loop-Ende), sonst testet er die falsche Metrik.
3. **Sticky-Flags: Dokumentation ≠ Test** (Da-Dok-Risk-Line) — der 2×-compactSession-Pfad war
   implementiert, aber ungetestet; Delta-Test + echte Mutation (nicht Argumentation) schlossen
   die Lücke.