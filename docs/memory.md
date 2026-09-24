# Session-Stand — 2026-09-24 (nextIds-Bug-Fixes ✅ auf `fix/nextids-bug-report`)

## Wo wir stehen

**Branch `fix/nextids-bug-report`** (von main `4a6abdd`), **gepusht** auf origin (Stand `84629f36`,
danach 3 neue lokale Commits — erneut pushen). Merge = Pauls Entscheidung.

- **✅ R-OD-5 (eclipseBuildProject Cap 100 + Disclosure):** Commit `5d74aab` (core
  `AiReponseBuilder.buildMarkers` + `MAX_BUILD_MARKERS`, 1-arg `readProblems`-Overload entfernt),
  Da-Dok-Review **ACCEPTED**, Docs geflippt (`e68fb4c`), Surefire 953/0/0/0.
- **✅ R-TC-6/7/8/9 (Shell-Approval):** Commit `6a72e92` (core `ShellConfirmationMode`/`Policy`,
  plugin `ShellApprovalService`, AIChatView aufgeräumt, Dead-Field weg) + `43fe725` (Hygiene).
  Da-Dok-Verdict **CONCERNS (non-blocking)** → Hygiene + Phantom-ADR-0026 behoben (`1a93f48`):
  ADR-0026 hatte nie gebauten `QuestionOrchestrator` als „✅ Implemented" geführt — gegen IST
  korrigiert. Surefire 959/0/0/0, PDE-Suite 285/0/0. Docs geflippt (R-TC-6…9 + Befunde ✅).
- **Befund des Zyklus (User, offengelegt):** Agenten-Calls überlaufen Context + Header-State
  bleibt hängen (Fall 1: 172205 > 170240; Fall 2: 232440 > 170240, 8 Retries — Pauls
  Neubewertung: Anzeige-Leak, Compact-Positiv-Fall). Evidenz zentral:
  [header-state-leak.md](header-state-leak.md) — onProblem soll den Header re-rendern;
  Bug-Fix-Zyklus-Backlog.
- nextIds-Bug-Fixes (R-DL-23/24/25) ✅ weiter unten — unverändert, inkl. Da-Dok C1 Mutation-Pin.

## Nächste Schritte

1. Paul: **Manueller Smoke R-TC** (Plan §6, 5 Punkte): „not-autonomous"+Jon → kein Prompt (auch
   via Da Mek) · Peon-Dev direkt → Prompt · Mid-Session-Wechsel ohne Reload folgt neuem Agenten ·
   „always" → Prompt überall · `""`/`"true"` → nie.
2. Paul: Branch erneut pushen (3 neue Commits) + Merge → main — sein Call.
3. **Nächster Bug-Fix-Zyklus-Backlog:** Context-Overflow-Triage (open-to-discuss 2026-09-24:
   ApiRetry non-retryable, Counter-Lücke 81k↔172k, Review-Guard, Retry-Logging, stille
   Cancellation) · ApiRetry-Follow-up (Memory #21) · optional `ShellTool.confirmationProvider`
   volatile (pre-existing, harmlos, 1-Wort-Fix).
4. Tool-Polish-Mini-Zyklus (I1–I4) weiter offen: `debugJava*`-Rename + R-JD-13 + QueuedAt-Regel 9
   + Homepage (memory.md vom 2026-09-23, unverändert).
5. Paul: Compact-Lock-Smoke 3+4 → Flips UC-CT-3/5/6; Debugger-Re-Run 3b/3.2; Compressor-Empty-
   Root-Cause (Error-Log + Compact-Model-Config).

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

ApiRetry-Follow-up (Memory #21, erneut live aufgetreten — im Overflow-Triage-Backlog) ·
`ShellTool.confirmationProvider` volatile (pre-existing, harmlos, 1-Wort-Fix) · Issue #142-ADR ·
DL-Sweep der 45 Alt-UNBELEGT (UC-DL-1…55, Da-Dok-Out-of-Scope-Notiz) · UC-DL-60/61 (R-DL-18 ist
gebaut aber ❌ — Flip-Verdacht beim nächsten DL-Kontakt prüfen) · 🟡-Indicator · „!-Messages"
(Regel 8) · Workspace-Memory-Vollkopie je memoryAdd (❓ open-points.md) · Anthropic
cache_read-Undercount.

## Lektionen (Zyklus)

1. **Bug-Report gegen Code prüfen, bevor Fix-Pläne entstehen:** Da Doks CONCERNS-C1 (ungepinnter
   Tie-Break `>=`) war exakt die Stelle, die Befund-3 rückholbar gemacht hätte — Mutations-Nachweis
   rot gemessen statt argumentiert, 5 Minuten Aufwand.
2. **Opt-in-Blindstelle bei Vergabe-Tools:** `nextIds` las nur Definitionen aus Opt-in-Docs —
   „free" war eine Lüge über Nicht-Lesbares. Regel-Prinzip: ein Eindeutigkeits-Tool darf im Zweifel
   nicht raten (R-DL-24 schreibt das jetzt fest).
