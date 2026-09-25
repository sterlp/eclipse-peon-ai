# Session-Stand — 2026-09-24 (Zyklus komplett: R-DL/R-ET/R-OD-5/R-TC/R3 ✅ auf `fix/nextids-bug-report`)

## Wo wir stehen

**Branch `fix/nextids-bug-report`** (von main `4a6abdd`), gepusht (HEAD `e3ff278` + Docs-Aufräum-Commits),
nicht gemerged — Merge = Pauls Entscheidung. Docs aufgeräumt 2026-09-24: open-points.md von 539
Zeilen stranguriert (🔒-Blöcke → resolved-points.md konsolidiert, Bug-Fix-Backlog priorisiert an
den Anfang), memory.md kompakt.

- **✅ R-OD-5** (eclipseBuildProject Cap 100 + Disclosure): `5d74aab`, Da-Dok ACCEPTED, Surefire 953.
- **✅ R-TC-6/7/8/9** (Shell-Approval: Autonom = Jon/Plan, Call-Zeit-Evaluation, `true` ignoriert,
  eigene Klasse `ShellApprovalService`): `6a72e92`+`43fe725`, Surefire 959, PDE 285. ADR-0026 gegen
  IST korrigiert (Phantom `QuestionOrchestrator`).
- **✅ R3** (Shell workingDirectory-Default = Projekt-Disk-Pfad, `cwd=`-Disclosure): `c06efcb`,
  Da-Dok ACCEPTED, Surefire 964, PDE 288. Smoke ✅ Paul (Pkt 1+2; „kein Projekt" nur noch Safety-Net,
  automatisiert gedeckt).

## Nächste Schritte

1. **Thema jetzt: Compact + State** (Paul) — R-CC-7 (Compact-Fehler ans LLM + Retry-1×-20s
   transient-only) + Header-State-Leak (onProblem re-rendert Header) + ApiRetry-Fehlerklassen-
   Tabelle. Evidenz zentral: docs/header-state-leak.md. → Plan mit Da Thinka, dann Build.
2. R-TC-Smoke (5 Punkte, tool-confirmation.md) — offen bei Paul.
3. Tool-Polish I1–I4 (`debugJava*`-Rename + R-JD-13 + QueuedAt-Regel 9 + Homepage) — unverändert.
4. Compact-Lock-Smoke 3+4; Debugger-Re-Run; Compressor-Empty-Root-Cause (Error-Log +
   Compact-Model-Config) — Paul.

### Zyklus-Historie (kompakt, Details in den Feature-Docs)

- **✅ R-DL-23/24/25** (nextIds, Pauls Bug-Report): Bindestrich-Präfixe, Rohvorkommen zählen
  (flach `OP-79`→`OP-80`), skipped-Docs namentlich. Commits `889b315`/`d477820`/`887d301`/
  `7933301`/`2235dba` (C1 Mutation-Pin). Befund 1 = Anwenderfehler (R-DL-18 deckt ab).
- **✅ R-ET Rename** `eclipseRunTests`→`eclipseRunJavaTests` (Java-only-Guard): Code `909ce05`,
  Docs `0188fd3`+`52ce47f`. Pauls Fragen beantwortet (Dauer via CallStats im Result, nicht
  onTool; Warnings → R-OD-5).

## Smoke-Liste (manuell) — Compact-Lock CT-3…6: 1+2 ✅ Paul

3. Compact fehlschlagen → Queue trotzdem Follow-up.
4. Slave-Compact → nur Slave 🟢, Da Boss aus (Blatt-Regel), kein Follow-up am Boss.

## Geparkt

`ShellTool.confirmationProvider` volatile (1-Wort-Fix bei Berührung) · Issue #142-ADR ·
DL-Sweep der 45 Alt-UNBELEGT · UC-DL-60/61 (R-DL-18 gebaut aber ❌ — Flip-Verdacht beim nächsten
DL-Kontakt prüfen) · Anthropic cache_read-Undercount. Übriges: docs/open-points.md.

## Lektionen (Zyklus)

1. **Bug-Report gegen Code prüfen, bevor Fix-Pläne entstehen:** Da Doks CONCERNS-C1 (ungepinnter
   Tie-Break `>=`) war exakt die Stelle, die Befund-3 rückholbar gemacht hätte — Mutations-Nachweis
   rot gemessen statt argumentiert, 5 Minuten Aufwand.
2. **Opt-in-Blindstelle bei Vergabe-Tools:** `nextIds` las nur Definitionen aus Opt-in-Docs —
   „free" war eine Lüge über Nicht-Lesbares. Regel-Prinzip: ein Eindeutigkeits-Tool darf im Zweifel
   nicht raten (R-DL-24 schreibt das jetzt fest).
