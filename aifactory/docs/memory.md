# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. **Paul committet selbst** (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** (2026-10-29) — gilt auch für Core-Tests.

## Jetzt — MVP-Zyklus auf `web-factory-mvp` (2026-11-01, autonom)

Paul freigegeben: „alles bekannte umsetzen, kleine Incremente, schönes UI, Jon im Web". Plan:
`peon-plan/overview.md` (Inc-4-Plan archiviert als `overview-inc4-done-2026-11-01.md`).

* **Inc-4-Review nachgeholt:** CONCERNS, kein Blocker — Befunde in Plan §10/Anhang A. C-1/C-3/C-4 +
  LOWs sind in **5a** abgeräumt; C-2 (Core-Test Paritäts-Pin) geparkt, Proposal-Text Plan Anhang B
  (**braucht Paul**).
* **Inc 5 komplett gebaut, reviewed, gepusht** (Spitze `dc83fabd`):
  **5b Kacheln** (UC-WP-11/12/13 ✅, R-WP-7 ✅), **5c Blick** (R-WP-26 ✅, UC-WP-49 ✅ — schreibgeschützt,
  nur Plan/Dev/Review, Caps 500 Zeilen/8 192 Zeichen), **5d Stop** (`POST …/messages/stop`,
  TurnOutcome CANCELED⇒done), **5e/5f Review-Reparatur** (TurnClaim: ein Claim je Projekt — Turn und
  Aktion treffen sich nie; Senden nie abgelehnt (R-WP-10), Aktion während Turn 409, Stop während
  wartender Aktion 409, 10-s-Frist gegen Claim-Leck, resetCancel am Aktionsstart).
  Gates: Backend **139/0/0**, UI **40/6**, lint 0/0, Doc-Lint UC 50/50 · ids 117/43 · findings 7
  (offen: UC-WP-43…48, 25) · VERWAIST 0.
* **Inc 5a** = Review-Härtung Datei-Bereich (Symlink-Listing, oxlint `chat ↛ project`, Wurzel-Parität).
* **Review-Spielstand:** Verdict REJECTED (BUG-1 Kollision, BUG-2 Compact unbewiesen) → Delta 5e/5f →
  Zweitprüfung CONCERNS (§16), 1/2/4 gefixt in 5f, alles zu. **Kein weiterer Review-Loop geplant.**

## Offen für Paul ( ❓ in [open-points.md](open-points.md))

1. **Core: Stop verliert bereits gepollte Queue-Eingabe** (`AbstractAgent.call` pollt, Cancel-Ausstieg,
   Drain leer) — UC-WP-50 bleibt ❌, Fabrik kann es nicht heben (UC-WP-50 partly gebaut: Abbruch selbst
   ✅ getestet, nur Queue-Klausel offen).
2. **AGENTS.md Language-Block** (uncommittet, „UI: English") widerspricht gebauter deutscher UI — Paul
   fragen; bis dahin UI deutsch, Kommentare englisch, Draht-Text `[kein Text]` deutsch.
3. **C-2 Core-Test-Proposal** (Plan Anhang B) + **Paco** braucht ggf. Core-Touch (Prompt-Komposition
   `default + paco + doc-method` existiert nicht; paco.md liegt im Core, unverdrahtet).

## Backlog dieses Zyklus

1. **Inc 6 — Nutzername** (R-WP-17/R-WP-25, UC-WP-25/48 ❌): Name im Browser-Speicher, `[User <Name>]:`
   an Jon, Chat zeigt Namen statt „YOU", Prompt-Zeile Fabrik-only. → als nächstes.
2. **Paco** — nach Inc 6: prüfen ob fabrik-only (Core-Ressource paco.md laden + komponieren, eigener
   Runtime-Zweig); Core-Touch ⇒ erst Proposal an Paul.
3. Danach: Config-Repo, UI-Seiten/Router (R-WP-1 „vier Seiten"), Shell-Bypass (A2, eigene Geschichte).

## Fallstricke (gemessen)

* Baureihenfolge: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`
  (Eclipse-Lombok-Stümpfe). Einzeltest: `-Dsurefire.failIfNoSpecifiedTests=false`.
* Surefire „kill self fork JVM"-Zeile am Ende von `clean verify` ist **vorbestehend** (Baseline-Log
  belegt) — kein Inc-5e/5f-Defekt.
* `MockLlmServer`: kein `getRequestCount()`, fängt keine `system`-Nachrichten; `@P(name=…)`; schreibt
  SSE-Antwort in einem Stück ⇒ Cancel-mitten-im-Stream nur mit später antwortendem Endpoint
  (`DelayedModel`, >1500-ms-Latch).
* Guards `OneChatPerProjectTest`/`SharedProjectChatTest` nur **verschärfen** (D-5b-1/D-5c-1/D-5d-1) —
  vier Gesprächs-Routen (messages, messages/stop, events), Kachel-Familie agents(+{name}).
* Port 8080 = IDE → Nachweise auf 8099; `~/.peon` vor/nach vergleichen; `@TempDir` liegt auf macOS unter
  TMP → Test-Homes nach `target/`.
* `lintDocsAndTests`: `root=/Users/sterlp/dev/workset/peon-ai`, `docRoots:["aifactory/docs"]` (nicht
  „docs" — das ist das Eltern-docs), `testRoots` Fabrik+UI, `idPattern UC-WP-\\d+`.
* eclipseEditFile verliert `oldString` bei langen Blöcken → `diskEditFile` (funktioniert) oder
  `eclipseReplaceLines`/`eclipseWriteFile`.
* `web-prototype.md` existiert nicht mehr (Restructure) — SOLL in `chat.md`/`files.md`.
* `TurnClaim`-Tests: Kachel-Handshake (COMPACTING⇒IDLE) statt Core-Flags als Warte-Bedingung.
