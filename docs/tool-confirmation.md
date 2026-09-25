# Tool-Confirmation — ein Bestätigungsmodell für riskante Tools

> **Status:** ⏳ geparkt (2026-09-19, Paul) — Bestätigungen selbst nicht gewünscht (immer aus);
> Bedarf entsteht erst mit CR-4 (Debugger) / CR-5 (Background-Shell). Confirmation bleibt Preference/Opt-in.
> Ursprungszuordnung (temporär): [tool-evolution.md](tool-evolution.md) — CR-6.

## Ziel

Heute bestätigt nur `shellRunCommand` über ein Widget. Riskante Aktionen anderer Tools (künftige
Terminal-Session, Java-Debugger, grundsätzlich auch Edits) brauchen dieselbe Schicht — **kategorisiert** und
mit **entscheids-cache**, damit autonome, lange Läufe nicht in Bestätigungs-Ermüdung kippen.

## Vorgeschlagenes Verhalten (Entwurf)

- **R-TC-1 🚧** Jedes riskante Tool trägt eine **Kategorie** (z. B. `terminal`, `file_write`, `debug`,
  `mcp_tool`, `safe_tool`). `safe_tool` wird immer automatisch bestätigt.
- **R-TC-2 🚧** Default bleibt **bestätigen**; Auto-Approve ist Opt-in (Preference), nie Default.
- **R-TC-3 🚧** Die User-Entscheidung wird mit Scope gecacht: **Once / Session / Global** — Session-Cache
  ist das empfohlene Minimum; Global nur via Preference.
- **R-TC-4 🚧** Sub-Agent-Läufe erben die Bestätigung der Parent-Session (kein zweiter Dialog pro Sklave).
- **R-TC-5 🚧** Bestätigungs-UI konsistent mit dem heutigen Shell-Widget (Yes/No/Freitext).

## BDD (Entwurf, hart erst bei ❌)

- GIVEN Kategorie `terminal` mit Session-Cache „Allow" WHEN Sklave denselben Befehlstyp ruft THEN kein zweiter Dialog.
- GIVEN Global-Cache nicht gesetzt WHEN neue Session startet THEN Default = nachfragen.
- GIVEN Tool ohne Kategorie WHEN Aufruf THEN wie `unknown` behandelt (nachfragen), nie still durchlassen.

## IST (2026-09-24, verifiziert Da Mek — Shell-Approval-Befund aus dem R-OD-5-Zyklus)

- Preference `PREF_SHELL_CONFIRMATION_ENABLED` kennt 3 Werte: `true`/`always` (semantisch
  identisch — immer bestätigen) und `not-autonomous` (nur bei nicht-autonomem aktiven Agenten).
  Default/leer = aus.
- **„Autonom" = `getActiveAgent() instanceof AiPlanAgent`** (`AIChatView.java:440`) — nur
  Peon-Plan. Jon (`AiPoAgent`), Peon-Dev, Peon-Review, Scaffold, Custom zählen **nicht**.
- Provider wird **nur** in `applyConfig()` gesetzt (`AIChatView.java:428`, Equals-Gate `:421`) —
  ein Agenten-Wechsel (`onAgentChange` → `setActiveAgent`, `PeonAiService.java:436-439`)
  aktualisiert ihn **nicht**. Eine einzige Shared-`ShellTool`-Instanz dient allen Agenten inkl.
  Jons Sklaven (`BuildPoAgentComponent.java:94/102/110`).
- Der Approval-Prompt blockiert im Provider-Callback den Agent-Thread (CountDownLatch,
  `AIChatView.java:449-466`) — UI bleibt bedienbar.

### Befund 1 — Stale-Provider (False Negative) ✅ (behoben durch R-TC-6/7)

Peon-Plan aktiv + `not-autonomous` → Provider `null`. Wechsel auf Peon-Dev → Dev führt
Shell-Befehle **ohne Approval** aus. False Negative = teuerste Fehlerklasse (AGENTS.md:
„a tool must never lie").

### Befund 2 — Jon zählt nicht als autonom ✅ (behoben durch R-TC-6)

`AiPoAgent` ist kein `AiPlanAgent` → unter `not-autonomous` prompten Jons Sklaven (Da Mek,
Shell via Shared-Tool) bei jedem Aufruf. SOLL (Paul 2026-09-24): **Jons Agenten brauchen im
autonomen Lauf kein Approval.** Fragment der geparkten Kategorisierung — hier als eigener
Mini-Fix behandelbar, ohne das volle R-TC-1…5-Modell zu öffnen.

### SOLL (Fix, 2026-09-24, Paul freigegeben)

- **R-TC-6 ✅ done (2026-09-24, Paul; Review Da Dok, Commits `6a72e92`/`43fe725`) — Autonom-Definition:** Unter `not-autonomous` gilt ein Lauf als autonom, wenn der
  **aktive Agent Jon (`AiPoAgent`) oder Peon-Plan (`AiPlanAgent`)** ist. Jons Sklaven (Da Mek,
  Da Thinka, Da Dok) erben das — der Lauf-Kontext (wer den Turn besitzt), nicht der Sklave,
  entscheidet.
- **R-TC-7 ✅ done (2026-09-24, Paul; Review Da Dok) — Evaluation zur Call-Zeit:** Der ConfirmationProvider wird konfiguriert gesetzt,
  entscheidet aber **pro Call** über die Autonomie (aktiver Agent zur Call-Zeit) — kein Stale-
  Zustand mehr, kein Refresh beim Agenten-Wechsel nötig. Alternative (a) „Refresh in
  `onAgentChange`" verworfen: sie fixt Befund 1, aber hält zwei Evaluationspunkte und das
  Stale-Risiko am Leben.
- **R-TC-8 ✅ done (2026-09-24, Paul; Review Da Dok) — Doppelung konsolidiert (Clean Break, AGENTS.md):** nur noch `always` (immer
  bestätigen) und `not-autonomous`; der Altwert `true` wird **ignoriert** (= unset, keine
  Bestätigung), nicht gemigtiert. Kein Alias.
- **R-TC-9 ✅ done (2026-09-24, Paul; Review Da Dok, ADR-0026 korrigiert) — Eigene Klasse:** die Shell-Confirmation-Logik verlässt `AIChatView` (löst die
  TODOs `AIChatView.java:444/447`): reine Policy-Entscheidung im **core** (Preference-Wert +
  „autonomous?"-Signal → Entscheidung, testbar ohne SWT), UI-Wiring (showQuestion-Callback)
  bleibt dünn im Plugin. One behaviour, one implementation.

BDD (alle ✅, getestet 2026-09-24 — core `ShellConfirmationPolicyTest` 6, plugin `ShellApprovalServiceTest` 5; Widget/Latch-Verhalten manuell):
- GIVEN `not-autonomous` + Jon aktiv WHEN Jons Sklave Shell ruft THEN kein Prompt, Befehl läuft.
- GIVEN `not-autonomous` + Peon-Plan aktiv WHEN Shell-Call THEN kein Prompt.
- GIVEN `not-autonomous` + Peon-Dev direkt aktiv WHEN Shell-Call THEN Prompt.
- GIVEN `not-autonomous`, Start mit Peon-Plan, Wechsel auf Peon-Dev WHEN Dev Shell-Call THEN
  Prompt (Befund 1 behoben — Call-Zeit-Evaluation).
- GIVEN `always` WHEN Shell-Call THEN Prompt — auch bei autonomen Agenten.
- GIVEN Pref-Wert `true` (Altwert) WHEN Load THEN behandelt wie unset — keine Bestätigung.
- GIVEN Pref leer/unset WHEN Shell-Call THEN keine Bestätigung.
- GIVEN `always` und eine Config-/Agenten-Änderung WÄHREND eines Laufs WHEN Shell-Call THEN
  die Entscheidung fällt anhand des aktiven Agenten zur Call-Zeit (kein Stale-Zustand).


## Offen (PO-Run)

- Minimal-Variante (nur Session-Cache für Shell/Terminal) vor vollem Category-Modell?
- Aufwand: **M-L**.


