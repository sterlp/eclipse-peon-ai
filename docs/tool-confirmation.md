# Tool-Confirmation — ein Bestätigungsmodell für riskante Tools

> **Status:** 🚧 in design (Entwurf aus dem Tool-Evolutions-Run 2026-09-19, wartet auf PO-Freigabe).
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

## Offen (PO-Run)

- Minimal-Variante (nur Session-Cache für Shell/Terminal) vor vollem Category-Modell?
- Aufwand: **M-L**.
