# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Branch: Core-Repo auf `main`
(2.15.1), `web-factory` existiert nicht mehr. **Paul committet selbst.**
**Core-Änderungen NUR mit Pauls Approval, per Proposal mit WARUM** (2026-10-29).

## Jetzt — Core-Origin-Thema ([ADR-0078](../../docs/adr/0078-origin-in-core-owner-monitor.md))

* **Plan:** `peon-plan/overview.md` (Paul kopiert ihn bei IDE-Wechsel nach `llmpeon-parent/peon-plan`;
  Plan + memory leben dann dort). Alter Inc-4-Plan geparkt: `peon-plan/overview_parked.md` —
  **veraltet**: Diagramm §4.3.2 nennt `core/OriginMonitor(Jon)`, Testzahl 1386 (aktuell 1406), Branch
  web-factory → `main`; der Mitglieds-Test in 4a-3 muss nach dem Origin-Umbau **neu geschrieben**
  werden (`isJon(origin)` statt `null == Jon`).
* **D1–D6 entschieden** (Paul 2026-10-29, bestätigt durch externes Review): C = OwnerMonitor am
  Turn-Besitzer; D2 Fabrik filtert TOOL/PROBLEM per **Map nach Tool-Call-Id**; D3 kein `getAgent()` am
  `AiMonitor`; D4 `LoopOwner` als **eigenes Inkrement**; D5 Ork-Namen; D6 `onDelegatedAnswer` bleibt weg.
* **AF-ADR-002** teilweise abgelöst, chat.md (R-WP-4, UC-WP-39/40) + aifactory-architektur.md nachgezogen
  (2026-10-29): Mitglieder-Zeile erscheint bei `done`, inkl. Statistik-Zeile; leeres Ergebnis = Zeile;
  `delegated`-SSE-Event entfällt.
* **Achtung Fabrik:** baut gerade nur gegen stale Core-Jar (2.8.1 von 26.10.). Erstes Core-`install`
  bricht sie: `SseTurnMonitor.java:81,93`, `ProjectRuntime.java:20,169` — Inc 0 des Plans entgiftet.

## Code-Stand

| Inc | Inhalt | Stand |
|-----|--------|-------|
| 1 · 2a–d · 3a–e · 3-H · 4a-2…4b-2 | Registry/Clone, Auslieferung, Config, Chat, Schreib-Pinning, Datei-Bereich | gebaut; Inc 4 Review fehlt (geparkt) |
| 3f | Herkunft + Team-Antwort | neu geplant über ADR-0078 (Origin-Reihe Inc 4), UC-WP-39/40 ❌ |
| 5 · 6 | Kacheln (UC-WP-11/12/13) · Nutzername (UC-WP-25) | nicht gebaut |

* `ProcessCommandRunner.java:29-34` `BatchMode=yes` als Env-Var wirkungslos → Fix mit R-CR-7.
* Bug: Custom Agent mit Built-in-Namen überschreibt still (`AgentService.java:180-202`) → Agent-Datei-Feature.

## Fallstricke (gemessen)

* Baureihenfolge: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`
  (Eclipse-Lombok-Stümpfe in `target/classes`).
* `MockLlmServer`: kein `getRequestCount()`, fängt keine `system`-Nachrichten; `@P(name=…)` für Tool-Namen.
* Guards `OneChatPerProjectTest`/`SharedProjectChatTest` (DTO-Feldliste + Verbotliste) nicht anfassen.
* Port 8080 = IDE → Nachweise auf 8099; `~/.peon` vor/nach vergleichen.
* Lint immer mit `docRoots`/`testRoots` eingeschränkt; **Plan-Gate**: `lintDocsAndTests` je Inkrement,
  umgeschriebene Tests behalten ihre `// UC-…`-Anker (sonst UNBELEGT beim ✅-Flip).
* Core-Tests-Baseline: **1407** (Da-Dok-Messung 2026-10-30).
