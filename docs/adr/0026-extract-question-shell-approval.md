# ADR-0026: Question-/Shell-Approval-Logik aus `AIChatView` extrahieren

## Status

**Teilweise umgesetzt, korrigiert 2026-09-24.** Der ursprüngliche Eintrag (2026-08-09)
behauptete fälschlich „✅ Implemented" inkl. eines `QuestionOrchestrator`, der **nie existiert
hat** (grep-verifiziert 2026-09-24, Da-Dok-Review R-TC-9). Tatsächlicher Stand:

- **`ShellApprovalService`** — ✅ umgesetzt erst **2026-09-24** (R-TC-9, Commit `6a72e92`,
  Zyklus R-TC-6…9), nicht 2026-08-09. Bis dahin saß die komplette Shell-Confirmation-Logik in
  `AIChatView.applyShellCommandConfirmation()`.
- **`QuestionOrchestrator`** — ❌ nie gebaut. `showQuestion()`/`hideQuestion()`/`cancelSilently()`
  leben weiterhin in `AIChatView` (Widget-Swap mit exclude/visible/layout direkt in der View).

## Context

`AIChatView` trug ~70 Zeilen verteilter Question-/Shell-Approval-Logik:
- `showQuestion()`/`hideQuestion()` manipulieren 4+ Widgets direkt (exclude/visible/layout)
- `applyShellCommandConfirmation()` las Preferences, erstellte CountDownLatch, rief
  `showQuestion()` auf — mit **eingefrorenem** autonomous-Flag (Stale-Provider-Bug, Befund 1 in
  [tool-confirmation.md](../tool-confirmation.md))
- Beide Pfade teilten sich Widget-Kenntnisse → doppelte Abhängigkeiten, schwer zu testen

## Decision

- **Shell:** Extraktion 2026-09-24 als `ShellApprovalService`
  (`org.sterl.llmpeon.parts.shell`) mit zwei Design-Regeln aus dem SOLL (R-TC-6/7, dort
  begründet): reine Policy-Entscheidung im **core** (`ShellConfirmationMode` +
  `ShellConfirmationPolicy`), Evaluation zur **Call-Zeit** über `Supplier<AiAgent>` statt
  eingefrorenem Flag. UI-Prompt-Verhalten (Frage-Text, „No"-Default, CANCEL →
  CancellationException) unverändert.
  *Nachtrag 2026-09-25 (Revision R-TC-6):* Autonomie gilt strikt nur für den `AiPoAgent` (Jon)
  und seine Sklaven unter Jon-Governance. `AiPlanAgent` standalone ist nicht autonom.
- **Question:** bewusst **nicht** extrahiert — der Widget-Swap ist UI-Verbundenheit pur; ein
  Orchestrator brächte nur einen Extra-Hop ohne zweite Nutzung. Kein geplanter Termin.

## Consequences

- Shell-Approval ist ohne SWT testbar (`ShellConfirmationPolicyTest` im core,
  `ShellApprovalServiceTest` headless im Plugin-Testmodul).
- Kein Provider-Refresh beim Agenten-Wechsel mehr nötig (Call-Zeit-Evaluation).
- `AIChatView` ~40 Zeilen leichter; die Question-Logik bleibt dort — ein künftiger
  Question-Refactor muss neu entschieden werden, auf **diesem** ADR allein nichts aufbauen.

## See Also

- [Ask User Tool Design](../user-question-tool-design.md)
- [Tool-Confirmation](../tool-confirmation.md) (R-TC-6…9, IST + SOLL + BDD)
