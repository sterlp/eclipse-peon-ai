# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. **Paul committet selbst** (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** (2026-10-29) — gilt auch für Core-Tests.

## Zyklus 2026-11-01 ABGESCHLOSSEN — Inc 5 + Inc 6 gebaut, reviewed, gepusht

Branch `web-factory-mvp`, Spitze `3957defc` (= origin). Plan: `peon-plan/overview.md` (§18 = Inc 6,
§10/14/16/17/19 = Reviews). Inc-4-Plan archiviert (`overview-inc4-done-2026-11-01.md`).

* **Inc 5** (Kacheln · Blick · Stop): UC-WP-11/12/13 ✅, R-WP-7 ✅, R-WP-26 ✅, UC-WP-49 ✅; UC-WP-50 ❌
  (Abbruch gebaut+getestet, nur Queue-Klausel offen — Core-Lücke). Review: REJECTED (BUG-1 Turn/Aktion-
  Kollision, BUG-2 Compact unbewiesen) → Delta 5e/5f (`TurnClaim` — ein Claim je Projekt, Senden nie
  abgelehnt (R-WP-10), 409 bei Turn, 10-s-Frist, resetCancel am Aktionsstart) → Zweitprüfung CONCERNS,
  alle Befunde gefixt.
* **Inc 6** (Nutzername): R-WP-17/25 ✅, UC-WP-25/48 ✅. Präfix `[User <Name>]: ` vor dem Core-Aufruf,
  Name überlebt Neustart ohne zweiten Speicher; Anzeige `origin ?? 'YOU'`; Identität im Browser
  (localStorage). 6d: getipptes Präfix ist Text, nie Absender (Escaping am Schreibort, D-6d-1).
* **Gates (Endstand):** Backend **156/0/0**, UI **47 Tests/7 Dateien**, lint 0/0 (31 Dateien),
  Doc-Lint UC 50/50 · ids 135/45 · findings **5** (offen: UC-WP-43…47) · VERWAIST 0.
* Plugins in diesem Workspace nicht geöffnet — Plugin-Gate nie gelaufen (Tycho offline); Ersatz:
  Core-Diff leer + Kern-Suite. C-2 (Core-Test-Paritäts-Pin) bleibt geparkt, Proposal-Text Plan Anhang B.

## Offen für Paul (❓ in [open-points.md](open-points.md))

1. **Core: Stop verliert eine bereits gepollte Queue-Eingabe** (gemessen, `AbstractAgent.call`) — Fix
   wäre Core-Story; danach UC-WP-50 ✅.
2. **AGENTS.md Language-Block** (uncommittet, „UI: English") vs. gebaute deutsche UI — Urheber unbekannt
   (war bei Session-Start noch nicht drin, Dev hat ihn nicht geschrieben); Paul entscheidet UI-Sprache.
3. **C-2 Core-Test-Proposal** (Plan Anhang B) — Pin auf Prompt-**Inhalt**, nicht nur Fügung.
4. **Paco:** Prompt liegt im Core (`prompts/paco.md`, unverdrahtet; Komposition `default + paco +
   doc-method` fehlt). Paco ist web-only → vermutlich fabrik-only baubar (Ressource laden + komponieren,
   eigener Runtime-Zweig), aber Feature-Umfang (R-PC-1…8, paco.md) = eigene Story. **Erst mit Paul
   starten** (SOLL-Fragen: Nutzer-Räume, Write-Roots `paco/<nutzer>/` im Checkout vs. Schreib-Pinning).

## Backlog (nächster Zyklus)

1. **Paco** (s. o.) — ❌ specified, nach dem MVP.
2. **Config-Repo** (config-repo.md) — ❌ specified; enthält auch den `ProcessCommandRunner`-Guard-Fix
   (R-CR-7) und Key-Verschlüsselung danach.
3. **UI-Seiten/Router** (ui.md ❌ Teil: vier Seiten, Reload bleibt auf der Seite).
4. Kleine Review-Reste aus Inc 5/6 (nicht gebaut, LOW): Compact-Summary erscheint als Nutzer-Zeile;
   doppelte verqueute eigene Zeile; 409 der Kachel-Aktion in der UI stumm; `HeldModel`-Socket
   AutoCloseable; Surefire-„kill self fork JVM"-Zeile (vorbestehend, bisectiert).
5. **Doc-Hygiene:** open-points.md ⏳-Liste (2026-10-25/26) ist lang — am nächsten Zyklusanfang je
   Punkt prüfen: schon Regel im Feature-Doc ⇒ raus; die neuen ❓ (Core-Queue-Verlust, Language-Block)
   mit Paul klären.

## Fallstricke (gemessen)

* Baureihenfolge: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`.
  Einzeltest: `-Dsurefire.failIfNoSpecifiedTests=false`.
* `MockLlmServer`: kein `getRequestCount()`, keine `system`-Nachrichten, `@P(name=…)`, SSE in einem
  Stück ⇒ Cancel-mitten-im-Stream nur mit später antwortendem Endpoint (`DelayedModel`, >1500-ms-Latch).
* Guards `OneChatPerProjectTest`/`SharedProjectChatTest`/`ChatTurnTest` nur **verschärfen** (Feldlisten
  exakt, positive Ersetzung, benannte Abweichung) — Stand: 4 Gesprächs-Routen (messages, messages/stop,
  events), Kachel-Familie `agents(+/{name})`, `SendMessageRequest(text, user)`.
* Port 8080 = IDE → Nachweise auf 8099; `~/.peon` vor/nach vergleichen; `@TempDir` liegt auf macOS unter
  TMP → Test-Homes nach `target/`.
* `lintDocsAndTests`: `root=/Users/sterlp/dev/workset/peon-ai`, `docRoots:["aifactory/docs"]` (NICHT
  „docs"), `testRoots` Fabrik+UI, `idPattern UC-WP-\\d+`.
* eclipseEditFile verliert `oldString` bei langen Blöcken → `diskEditFile` funktioniert (kurz ankern);
  sonst `eclipseWriteFile`/`eclipseReplaceLines`.
* `TurnClaim`-Tests: Kachel-Handshake (COMPACTING⇒IDLE) statt Core-Flags als Warte-Bedingung.
* Dev-Agent kann eigenmächtig „Review-Blöcke"/Entscheidungen in den Plan schreiben (Inc 6, §18.13) —
  Review-Verdicts und neue Inkremente kommen nur von mir; nach garbled Antworten erst Statusanfrage
  (memory-Regel 3).
* `web-prototype.md` existiert nicht mehr — SOLL in `chat.md`/`files.md`.
