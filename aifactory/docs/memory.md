# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Branch: Core-Repo auf `main`
(2.15.1), `web-factory` existiert nicht mehr. **Paul committet selbst.**
**Core-Änderungen NUR mit Pauls Approval, per Proposal mit WARUM** (2026-10-29).

## Jetzt — Origin im Core: GEBAUT, Review-Befunde im Nachzug (2026-10-30)

* **Gebaut & gepusht** (`web-factory-goon`, Inc 0–4): Core stempelt `origin` fill-if-null am
  Turn-Besitzer (`OwnerMonitor`, Emitter `call`/`compact`/`buildStaticMessages`), Loop-Zuordnung
  über `LoopOwner` (`LoopOwner.NONE = named(null, dev)`) — **keine** Tool-Call-Id-Buchführung
  (ADR-0078 Decision 6). Fabrik-Filter: Mitglieder-Zeilen fallen per gestempeltem `origin`
  (`isJon(origin)` + Null-Fallback), `delegated`-SSE-Event entfällt. Namen: Jon/Plan/Dev/Review,
  Sub-Loops `Search`/`Compact` ([ADR-0079](../../docs/adr/0079-display-names-without-ork-names.md)).
* **UC-WP-39/40 ✅** — `TeamInternalsTest`/`DelegatedAnswerTest`/`ProjectRuntimeTest` belegen es,
  [chat.md](chat.md) ist nachgezogen.
* **AF-ADR-002** teilweise abgelöst; aifactory-architektur.md nachgezogen.
* **Offen (Paul):** Browser-Proof Fabrik auf 8099 + Plugin-Harness (`test-chat.html`) — manuelle
  Nachweise; Details in [../../docs/memory.md](../../docs/memory.md).

## Code-Stand

| Inc | Inhalt | Stand |
|-----|--------|-------|
| 1 · 2a–d · 3a–e · 3-H · 4a-2…4b-2 | Registry/Clone, Auslieferung, Config, Chat, Schreib-Pinning, Datei-Bereich | gebaut; Inc 4 Review fehlt (geparkt) |
| 3f | Herkunft + Team-Antwort | gebaut (Origin-Reihe Inc 0–4, UC-WP-39/40 ✅) |
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
