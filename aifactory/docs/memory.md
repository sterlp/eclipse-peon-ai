# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Branch `web-factory`.
**Paul committet selbst — kein Commit/Push durch Agenten in dieser Session.** Entscheidungen stehen in
den Feature-Docs (siehe [Index](index.md)), nicht hier.

## Jetzt — Docs-Review gegen [Pauls Vision](../pauls-product-vision.md)

* ✅ V1–V6, V11 entschieden und geschrieben. ✅ `web-prototype.md` gesplittet (2026-10-28) in
  ui · project · chat · files · config · user — alle `idPrefix: WP`, IDs unverändert, Lint 74/74, 0 Befunde.
* Offen, je eine Frage an Paul: V7 TMP-Prompt-Zeile → V8 Entfernen löscht `state/<id>`? → V9 Produktname
  OAF vs AI Factory → V10 Inc-4-Review → V6-Folge (MVP: ein Paco-Gespräch für alle?) → V5-Rest
  (MCP, Codegraph, Skills geteilt). Details: [open-points.md](open-points.md).
* **Vor Planung Config:** `aifactory-architektur.md` Config-Abschnitt nachziehen (Config-Repo,
  geleert = gelöscht, Agenten-Checkbox).
* Core R15 (po-agent-jon.md) bleibt ❌, bis Paul die Prompt-Änderungen committet hat → dann ✅.
* Danach: Gesamtstand an Paul; er merged/released erst den Plugin-Fix.

## Code-Stand

| Inc | Inhalt | Stand |
|-----|--------|-------|
| 1 · 2a–d · 3a–e · 3-H | Registry/Clone, Auslieferung, Config-Datei + Seite, Chat (Queue, SSE, Verlauf) | ✅ reviewt |
| 3f | Herkunft + Team-Antwort | Core-Teil zurückgebaut ([AF-ADR-002](adr/002-origin-in-core-display-in-factory.md)), UC-WP-39/40 ❌ |
| 4a-2…4b-2 | Schreib-Pinning, Datei-Bereich | gebaut, nicht reviewt, Marker ❌ |
| 5 · 6 | Kacheln (UC-WP-11/12/13) · Nutzername (UC-WP-25) | nicht gebaut |

* **Fabrik-Code kompiliert nicht** (bewusst). Wiederaufnahme: `done`/`error` aus `jon.call()` in
  `ProjectRuntime`; 3f-Teile (`delegated`, `AgentNames`, Filter) auf Delegations-Tool-Result umbauen.
* Plan `aifactory/peon-plan/overview.md` = Inc 4 (Review fehlt) — vor neuem Plan archivieren.
* `ProcessCommandRunner.java:29-34` `BatchMode=yes` als Env-Var wirkungslos → Fix mit R-CR-7.
* Bug: Custom Agent mit Built-in-Namen überschreibt still (`AgentService.java:180-202`) → Agent-Datei-Feature.

## Fallstricke (gemessen)

* Baureihenfolge: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`
  (Eclipse-Lombok-Stümpfe in `target/classes`).
* `MockLlmServer`: kein `getRequestCount()`, fängt keine `system`-Nachrichten; `@P(name=…)` für Tool-Namen.
* Guards `OneChatPerProjectTest`/`SharedProjectChatTest` (DTO-Feldliste + Verbotliste) nicht anfassen.
* Port 8080 = IDE → Nachweise auf 8099; `~/.peon` vor/nach vergleichen.
* Lint immer mit `docRoots`/`testRoots` eingeschränkt.
