# Session-Stand — 2026-09-24 (nextIds-Bug-Fixes ✅ auf `fix/nextids-bug-report`)

## Wo wir stehen

**Branch `fix/nextids-bug-report`** (von main `4a6abdd`), 11 Commits, **nicht gepusht** — Merge = Pauls
Entscheidung. `fix/simple-diff` ist als #146 (Squash `4a6abdd`) in main, lokal+remote gelöscht;
StaticContextItem-Java-Änderung von Paul ist committed. Alte Stashes: keine.

- **✅ nextIds-Bug-Fixes (R-DL-23/24/25, Pauls Bug-Report `nextIds-user-bug-report.md`):**
  R-DL-23 Bindestrich-Präfixe (`[A-Z]+(?:-[A-Z0-9]+)*`, last-dash-Split, inkl. DocParser/Lint-Pfad)
  · R-DL-24 Rohvorkommen zählen als belegt, gefundene Form wird fortgeschrieben (flach `OP-79` →
  `OP-80`, nie `R-OP-1`), Fundstelle im Vorschlag, „free" nur bei echtem Nichts · R-DL-25
  skipped-Docs namentlich im nextIds-Output.
  Commits: `889b315` (Docs-SOLL) · `d477820` (Inc 1) · `887d301` (Inc 2) · `7933301` (Inc 3) ·
  `2235dba` (Da-Dok C1: Flat-Tie-Break `>=` in `flatWins` mutations-gepinnt, rot gemessen).
  Surefire **949/0/0/0** (Ground Truth; Da Doks eclipseRunTests-Zahl 967 ist Artefakt).
  Da-Dok-Verdict: CONCERNS → C1 geschlossen. Docs geflippt (R-DL-23/24/25 + UC-DL-67…73 ✅),
  lintDocsAndTests: alle 45 UNBELEGT_ERLEDIGT = Alt-Bestand UC-DL-1…55, keine neuen.
- **✅ R-ET Rename `eclipseRunTests` → `eclipseRunJavaTests`** (Pauls Pre-Approval, Java-only-Guard
  `JavaCore.create`+`exists()` macht den Scope explizit): Code `909ce05` (1 Zeile, kein Reflector/
  Wiring), Docs-Follow-ups `0188fd3` (ADR-0053, Homepage-Toolset-Tabelle) + `52ce47f` (AGENTS.md, index.md,
  tool-time-disclosure.md) — alle ✅ committed. Gate: Core **949/0/0/0**,
  Plugin compile grün. Stale `lib/llmpeon-core.jar` (20.09, ohne `CallStats`) vorher per
  dokumentiertem Ritual re-copied — Vorbestand, nicht Rename-Folge.
- **Befund 1 des Reports** war kein Bug (Anwenderfehler) — R-DL-18 (Root-Fallback) deckte ihn
  2026-09-19 ab. Wunsch 4 (Disk-Pfad-Hinweis im Fehler) offengeblieben — kosmetisch.

## Nächste Schritte

1. ✅ R-ET Rename-Docs-Follow-ups erledigt: ADR-0053 ✅, Homepage-Toolset-Tabelle ✅, AGENTS.md ✅,
   tool-time-disclosure.md ✅ (Code `909ce05`, Docs `0188fd3` + index.md).
2. Paul (offen): `eclipseBuildProject`-Warnings sind aktuell **UNGECAppt** — `EclipseBuildTool.java:241-254`
   druckt alle ERROR+WARNING-Marker ohne Limit (Gegenteil der Annahme „Cap 100"). Vorschlag
   **Cap 100 + Disclosure** — wartet auf Pauls Entscheidung.
3. Paul (beantwortet, kein Change): Test-Dauer + Endzeit stehen im Tool-Result via
   `CallStats.suffix` ✅ — nicht in der `onTool`-Statuszeile (bewusst).
4. Paul: Branch pushen/mergen (`fix/nextids-bug-report` → main) — sein Call.
5. Optional (kosmetisch): `@P`-Description für `prefix` an `DocsIdTool` („e.g. ORD or O-TEST") —
   Fassaden-Änderung, als Follow-up notiert (Plan §10.3).
6. Tool-Polish-Mini-Zyklus (I1–I4) weiter offen: `debugJava*`-Rename + R-JD-13 + QueuedAt-Regel 9 +
   Homepage (memory.md vom 2026-09-23, unverändert).
7. Paul: Compact-Lock-Smoke 3+4 → Flips UC-CT-3/5/6; Debugger-Re-Run 3b/3.2; Compressor-Empty-
   Root-Cause (Error-Log + Compact-Model-Config).

## Smoke-Liste (manuell) — Compact-Lock CT-3…6: 1+2 ✅ Paul

3. Compact fehlschlagen → Queue trotzdem Follow-up.
4. Slave-Compact → nur Slave 🟢, Da Boss aus (Blatt-Regel), kein Follow-up am Boss.

## Geparkt

ApiRetry-Follow-up (Memory #21, erneut live aufgetreten) · Issue #142-ADR · DL-Sweep der 45
Alt-UNBELEGT (UC-DL-1…55, Da-Dok-Out-of-Scope-Notiz) · UC-DL-60/61 (R-DL-18 ist gebaut aber
❌ — Flip-Verdacht beim nächsten DL-Kontakt prüfen) · 🟡-Indicator · „!-Messages" (Regel 8) ·
Workspace-Memory-Vollkopie je memoryAdd (❓ open-points.md) · Anthropic cache_read-Undercount.

## Lektionen (Zyklus)

1. **Bug-Report gegen Code prüfen, bevor Fix-Pläne entstehen:** Da Doks CONCERNS-C1 (ungepinnter
   Tie-Break `>=`) war exakt die Stelle, die Befund-3 rückholbar gemacht hätte — Mutations-Nachweis
   rot gemessen statt argumentiert, 5 Minuten Aufwand.
2. **Opt-in-Blindstelle bei Vergabe-Tools:** `nextIds` las nur Definitionen aus Opt-in-Docs —
   „free" war eine Lüge über Nicht-Lesbares. Regel-Prinzip: ein Eindeutigkeits-Tool darf im Zweifel
   nicht raten (R-DL-24 schreibt das jetzt fest).
