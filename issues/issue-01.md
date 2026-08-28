# NPE in `PeonAiService.get()` wenn kein Projekt ausgewählt ist

Status: `✅ done (2026-08-21)` — Null-Guard in `PeonAiService.get()` (project lokal gecacht, Plan-Reference nur bei `project != null`); Repro-Test `test_turnContext_withoutProject_doesNotNpe` grün; Review OK.

## Evidenz

- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/PeonAiService.java:540` —
  `final var plan = getProject().getFile(PlanTool.OVERVIEW_FILE);` ohne Null-Check.
- `PeonAiService.java:328` — `getProject()` delegiert an `userContext.getCurrentProject()`,
  das initial `null` ist (`UserContext.java:23`, `volatile IProject currentProject`).
- `AIChatView.java:698` — `if (project != null) aiService.setProject(project);`:
  Projekt-Deselection/kein Projekt ist ein regulärer Zustand; Send-Pfad
  (`AIChatView.java:652` `aiService.call(...)`) hat keinen Projekt-Guard.
- Vergleich 2.6.2: der PO-Turn-Supplier prüfte explizit `project != null`
  (2.6.2 `AIChatView.java:229-233`). Das Plan-Reference-Item in `get()` ist Neucode der Branch.
- Gegencheck Handover-Punkt 3: `preloadPlanIfNeeded` (`PeonAiService.java:472-478`) ist
  **sicher** — `PlanTool.hasPlan()` (`PlanTool.java:146-150`) prüft
  `peonAiService.getProject() == null` zuerst. Der NPE-Pfad liegt nur in `get()`.

## Problem

Konkreter Ablauf: Kein Projekt ausgewählt (oder Deselection) -> User sendet eine Nachricht an
Dev/Plan/PO-Agent -> `doCall` ruft `renderTurnContext` -> `PeonAiService.get()` ->
`_handoffLine == null`-Zweig -> `getProject()` liefert `null` -> NPE auf `.getFile(...)`.
Der Turn schlägt mit NullPointerException fehl, statt einfach ohne Plan-Reference zu laufen.
Die benachbarten Items (`AgentsMdContextItem.itemsFor`, `EclipseFileContextItem.exists`,
`UserContext.get()`) behandeln `project == null` alle sauber — nur diese Zeile nicht.

## Auswirkung + Schweregrad

**Bug** — harter Turn-Abbruch (NPE) in einem regulären UI-Zustand (keine Projektselektion).
Fix-Vorschlag für die Review: `project == null` früh in `get()` abfangen oder
`getProject()` lokal cachen und prüfen.
