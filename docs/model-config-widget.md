---
idPrefix: MCW
---

# Model Config Widget — Verbindungsfelder live testbar

> **Status:** ✅ done (2026-09-28, `story/model-config-widget` — Inc-0 `c90debfb` · Inc-1 `df4293fe` · Inc-2 `a9691449` · Inc-3 `99c5aecb` · Inc-4 `5fd04c42`; Surefire core 1040/0, OSGi 301/0 + 20 Headless-Skips (bestehendes „Workbench not created"-Muster); SWT-Tests laufen im PDE-Runner, Mutations-Nachweis applyThinkValue). User-Smoke steht aus.
> **Heimat der Reload-Fetch-Mechanik:** [model-loading.md](model-loading.md) (R-ML2, neu entschieden)
> · **Cache je Verbindungs-Identität:** [ADR-0034](adr/0034-connection-cache-by-identity.md)
> · **Warum:** [ADR-0060](adr/0060-model-config-widget-live-widget-reads.md)

**Umsetzungs-Pflichten desselben Inkrements (alle umgesetzt in Inc-3/Inc-4):** Homepage
`setup/advanced-configuration.md` + `configuration.md` wurden auf Live-Read + Fallback-Kette
umgeschrieben. Skill „Eclipse Preferences Development" (`eclipse-preferences`) von Da Mek
angelegt; die Quick-Section „Preference pages — read widget state, not the store" ist aus
`eclipse-dpe` entfernt (verlagert, nicht doppelt).

## WAS

`ModelConfigWidget` — die Verbindungsfelder der Basic-Page als **eine Feldgruppe, vertikal
untereinander in dieser Reihenfolge** (Paul, 2026-09-28):

1. Provider (Combo)
2. URL
3. API Key
4. Model-Combo + Reload-Button
5. Think-Dropdown (provider-abhängig, siehe R-MCW-6)

… plus Ping-Button. **Scope dieses Baus: nur die Basic-Config-Seite** — die Advanced-Page
(per Agent) rührt dieser Zyklus nicht (R-MCW-5, zurückgestellt).

**WEIL (Paul, 2026-09-28):** die Felder bilden die Verbindungs-Identität — ändert sich eines,
ist der Reload fällig. Und: „Es hilft uns nichts, wenn ich die Eingabe mache und erst die
Config-Seite verlassen muss, um das Modell zu testen." Heute liest Reload den Preference-Store
(R-ML2 alt: erst Apply) — das Widget liest **live**.

### R-MCW-1 — Feldset — ✅ done (2026-09-28, `df4293fe`)

Widget owns: Provider, URL, API Key, Think-Level, Model-Combo, Ping-Button, Reload-Button.
**Bewusst NICHT im Widget:** JSON extra body (kein Teil der Connection-Identity, eigene
UI-Beispiele/Paste-Buttons) — mehrere fokussierte Widgets kombinieren statt einem alles
fressenden Block (Paul, 2026-09-28).

#### UC-MCW-1 — Widget-Feldset
GIVEN das Widget wird auf einer Config-Page gebaut
WHEN der User es betrachtet
THEN es trägt genau Provider/URL/Key/Think/Model + Ping + Reload (extra body außerhalb)
→ `ModelConfigWidgetTest` (OSGi/PDE-Runner)

### R-MCW-2 — Ping & Reload lesen Live-Widget-Werte — ✅ done (2026-09-28, `df4293fe`)

Beide Buttons bauen ihre Verbindungs-Identity zur Klick-Zeit aus den **aktuellen Widget-Werten** —
nie aus dem Preference-Store. Apply-los geänderte Eingaben sind sofort testbar (Parität zum
heutigen „Check Host and Port"). Stale-Guard bleibt: Fetch-Ergebnisse, deren Identity sich
während des Fetchs ändert, werden verworfen.

#### UC-MCW-2 — Reload mit ungespeicherter Eingabe
GIVEN der User ändert URL/Key/Provider **ohne** Apply
WHEN er Reload (oder Ping) klickt
THEN der Fetch nutzt die **Widget-Identität**, nicht den Store-Stand
→ `ModelConfigWidgetTest.reloadUsesLiveWidgetValues`

### R-MCW-3 — Reload persistiert nicht — ✅ done (2026-09-28, `a9691449`)

Reload = **nur Fetch** (Test/Preview). Speichern ausschließlich über Apply/OK; Cancel verwirft
die Eingaben — der Store bleibt unangetastet. Die geholte Liste wird unter der
**Widget-Identität** gecacht (ADR-0034 unverändert); dass eine gecachte Identität nie
persistiert wurde, ist harmlos — der Cache hält nur Listen, keine Konfiguration.

#### UC-MCW-3 — Cancel nach Reload
GIVEN der User ändert die URL und klickt Reload (Liste kommt vom neuen Host)
WHEN er die Page ohne Apply verlässt (Cancel/Escape)
THEN der Store hält weiterhin die alte Connection — nichts wurde persistiert
→ `AiConfigPreferenceViewTest.reloadDoesNotPersist`

#### UC-MCW-4 — Store bleibt nach Reload unberührt
GIVEN Reload wurde unter neuer Widget-Identität erfolgreich ausgeführt
WHEN der Store danach gelesen wird
THEN die Connection-Felder enthalten die zuletzt gespeicherten (nicht die getippten) Werte
→ `AiConfigPreferenceViewTest.reloadDoesNotTouchStore`

### R-MCW-4 — Ping-Semantik — ✅ done (2026-09-28, `df4293fe`)

Ping = Host+Port-Erreichbarkeit (Semantik des heutigen „Check Host and Port", unverändert).
Die **API-Key-Validität** testet implizit der Reload (fehlender/falscher Key → sichtbarer
Fetch-Fehler). Kein dritter Button-Pfad.

#### UC-MCW-5 — Ping mit live Host
GIVEN der User tippt eine korrigierte URL ohne Apply
WHEN er Ping klickt
THEN der Check läuft gegen die Widget-URL (nicht den Store) und meldet wie heute Erreichbarkeit
→ `ModelConfigWidgetTest.pingUsesLiveWidgetUrl`

### R-MCW-5 — Scope: Basic-Page; Advanced-Reuse zurückgestellt — ✅ done (2026-09-28, `a9691449`)

Dieser Bau umbaut **nur die Basic-Page** (eine Widget-Instanz, Base-Verbindung — der
`providerEditor` wandert in das Widget). Die Wiederverwendung in der Advanced-Page
(`AgentModelConfigSection` je Agent-Slot) ist **bewusst zurückgestellt** — sie folgt in einem
eigenen Zyklus, sobald die Basic-Variante im Feld steht. Das Widget wird von Anfang an
Suppliers/Callbacks statt Store-Zugriff nehmen ([ADR-0005](adr/0005-widget-owns-state-view-routes.md)),
damit der Advanced-Reuse ohne Umbau möglich ist.

**Zurückgestellt (2026-09-28, Paul):** per-Agent-Provider-Override — heute besitzt nur der Base
einen Provider (`EffectiveConnection.java:13`, Entscheidung 2026-08-28); die Advanced-Page und
das Custom-Agent-Frontmatter kennen kein `provider`-Key. Ein Custom-Agent mit URL-Override läuft
somit immer gegen die Base-Provider-API-Semantik — bricht bei inkompatiblen APIs. Wenn und wie
der Override kommt (Advanced-Feld, Frontmatter-Key, oder beides) = eigene Story.

#### UC-MCW-6 — Widget ersetzt die Basic-Verbindungsfelder
GIVEN die Basic-Config-Page wird gebaut
WHEN die Felder Provider/URL/Key/Think/Model gerendert werden
THEN stehen sie im ModelConfigWidget in der Reihenfolge 1 Provider · 2 URL · 3 API-Key ·
4 Model+Refresh · 5 Think (vertikal untereinander), plus Ping; keine losen Field-Editors mehr
→ `AiConfigPreferenceViewTest.performOkPersistsWidgetValues`

### R-MCW-6 — Think-Feld ist provider-abhängig, LIVE beim Provider-Wechsel — ✅ done (2026-09-28, `df4293fe`)

Das Think-Feld (5) rendert die Form des aktuell gewählten Providers — gleiches Muster wie die
Advanced-Page (`provider.thinkSupport()`: Boolean→Toggle, Values→Combo-Liste, FreeString→Text,
None→kein Feld). **Unterschied zur Advanced-Page:** der Provider ist im selben Widget editierbar,
deshalb baut sich das Think-Feld **bei jedem Provider-Wechsel live neu** (keine
Construction-time-Freeze). Reload/Ping-Identity enthält Think nicht (R-THINK-Semantik: Think ist
Request-Level, nicht Connection-Identity).

Wert-Verhalten beim Provider-Wechsel (Paul bestätigt, 2026-09-28): der getippte/gewählte Wert wird
**verbatim übernommen, solange die neue Form freie Eingabe erlaubt** (Toggle/FreeString); bei
einer festen Options-Liste (Values/READ_ONLY) wird das Feld **geleert**, wenn der Wert nicht in
der Liste steht — nie ein stiller Ersatzwert.

**Das Basic-Think-Feld ist der Default-Editor** ([R-THINK-10](per-agent-think.md),
[ADR-0061](adr/0061-think-default-base-think-fallback.md)): sein Wert gilt für alle Agenten ohne
eigenen Think — Ausnahmen (z. B. Compact/Search) definieren die Advanced-Page. Label:
„Think (Default)".

#### UC-MCW-7 — Provider-Wechsel baut das Think-Feld neu
GIVEN der User wechselt im Widget den Provider (z. B. Ollama → OpenAI)
WHEN die neue Auswahl aktiv wird
THEN das Think-Feld rendert sofort die Optionen/Form des NEUEN Providers — ohne Page-Reopen
→ `ModelConfigWidgetTest.thinkFieldFollowsProviderChange` (Mutations-Nachweis: applyThinkValue red→green)

## Scope-Update (2026-09-28, pm — [default-inheritance.md](default-inheritance.md) R-DEF-8)

R-DEF-8 erweitert den Widget-Scope: die Basic-Seite bekommt **Temperature** als zusätzliches Feld
(leer = unset, `AgentTemperature`-Parse-Punkt, R-T-Semantik aus advanced-configuration.md). JSON
extra body bleibt bewusst draußen (Advanced-DEV-Sektion „Dev (Default)").
## Scope-Update (2026-09-29 — [default-inheritance.md](default-inheritance.md) R-DEF-9, [ADR-0063](adr/0063-default-config-single-owner-basic-page.md))

Rework „Default for all agents": das Widget ist der **komplette Default-Editor** auf der Basic-Seite
— jetzt inkl. **Extra body (JSON)** via `ExtraBodyWidget` (Bindung 7), Überschrift
„Default for all agents". Das provider-gate des Extra-Body-Feldes ist **live** (wie Think), und
versteckte gespeicherte Werte werden **nie still gelöscht** (R-DEF-11). Die Advanced-DEV-Sektion
entfällt (Advanced schreibt keine Base-Keys mehr).