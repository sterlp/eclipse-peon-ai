# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Paul committet selbst (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** (2026-10-29) — gilt auch für Core-Tests.

## Zyklus 2026-11-02 LÄUFT — UI-Nacharbeiten nach Pauls Smoke-Test

Branch `web-factory-mvp`, Spitze `cf8133ec` (= origin). **Runde 2 abgeschlossen + Review bestanden**
(2026-11-02): alle 7 Inkremente + Follow-up gebaut — Inc 1 `321cb3ea` (Turn-Fehler = Verlaufszeile,
R-WP-34/UC-WP-64) · Inc 2 `6027bbe4` (Warte-Rahmen endet, R-WP-31/UC-WP-65) · Inc 3 `72e97e6d`
(Chat scrollt, Eingabe fixed, R-WP-35/UC-WP-66) · Inc 4 `99560a9b` (Akzent links, R-WP-29/UC-WP-55) ·
Inc 5 `f069dd17` (Files-Tab bündig, Zeilenhöhe — Shiki-`\n`-Knoten, R-WP-33/UC-WP-60/61) · Inc 6
`7cc5b54f` (Think je Agent nach Provider-Form, R-WP-23/UC-WP-67) · Inc 7 `e682e7cc` (Config-Look,
Cards) · Review-Followup `cf8133ec` (UC-WP-64 GIVEN 2 gepinnt, `ranTurn` gepinnt, Historien-Warten
ein Helfer, Marker). Stand: Backend **208/0/0**, UI **129 Tests/16 Dateien**, oxlint 0/0,
UC **67/67**, Doc-Lint 0 Findings (250 Ids). Alle 10 Feedback-Punkte verarbeitet. Dev-Slot-Lücke ⏳.
Trailer-Regel: kein Modellname.

## Zyklus 2026-11-03 — Runde 3 LÄUFT

**✅ F6/F7 abgeschlossen (Review CONCERNS ohne Bug, Delta S1/L1 nachgebaut, Inc 9):** 23 → **1**
Spring-Kontext, Wanduhr **118 → 42,1 s**, 216 Tests/0, null Produktionscode. Commits `c47c4aa7`…`30c42754`
auf `web-factory-mvp`. Regel + Messgröße jetzt in [AGENTS.md](../AGENTS.md), Fakten in
`AGENTS-DEV.md` (Abschnitt „Build-Geschwindigkeit / Test-Kontext"); Guard `SingleContextGuardTest`
(härtet Meta-Annotationen + Nicht-`*Test`-Klassen), Lifecycle-Netz 120 s, TurnGate statt Schlaf,
ein Home `target/test-home`. F6/F7 aus open-points entfernt (Fakten leben in AGENTS-DEV).

**Offen Runde 3 (Backlog in [open-points.md](open-points.md)):** F3 Kacheln · F4/F6-C hr raus, Linien
wie Eclipse · F5 Optik/Config · F8 Config-Umfang (reasoning_effort, Header) · F9 Clear-Layout ·
F10 Bootstrap docs-Laden · C-1 TurnClaim-Timeout · C-2 Compact-Anzeige · C-3 URL-Hash ·
C-5 Verlauf out of order (Referenz: `/Users/sterlp/dev/workset/ai-schulung/app/frontend`) ·
C-7 Agent-Chat = Haupt-Widget read-only. **C-4 Diffs 🔒 entschieden (live only)** — als nächstes
Inkrement mit Regel+UC in chat.md. Bild-Support-Anzeige weiter ⏳.
**Nächster Schritt: C-4 bauen** (SseTurnMonitor → ChatEvent.Diff → Chat-Widget, kein Core-Eingriff).
Arbeitsregel (Paul): Nur-UI-Änderung ⇒ nur UI-Tests; Backend anfassen ⇒ Backend-Tests.
Trailer-Regel: kein Modellname. Commit nur `aifactory/`-Pfade scopen (Core im selben Repo).

Runde 1/2-Befundlisten und die Design-Entscheidungen vom 2026-11-02 sind verarbeitet und leben in den
Feature-Docs (ui/chat/files/config.md) — hier gelöscht.

## Offen für Paul (❓ in [open-points.md](open-points.md))

1. Core-Queue-Verlust bei Stop (UC-WP-50) — Core-Story, nur per Proposal
2. C-2 Core-Test-Proposal (archivierter Inc-4-Plan, Anhang B)
3. Paco-Story (SOLL-Fragen: Nutzer-Räume, Write-Roots `paco/<nutzer>/`)

## Fallstricke (gemessen)

* Baureihenfolge: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`.
  Einzeltest: `-Dsurefire.failIfNoSpecifiedTests=false`.
* `MockLlmServer`: kein `getRequestCount()`, keine `system`-Nachrichten, SSE in einem Stück ⇒
  Cancel-mitten-im-Stream nur mit später antwortendem Endpoint (`DelayedModel`, >1500-ms-Latch).
* Guards `OneChatPerProjectTest`/`SharedProjectChatTest`/`ChatTurnTest` nur **verschärfen** (Feldlisten
  exakt, positive Ersetzung, benannte Abweichung) — Stand: 4 Gesprächs-Routen (messages, messages/stop,
  events), Kachel-Familie `agents(+/{name})`, `SendMessageRequest(text, user)`.
* Port 8080 = IDE → Nachweise auf 8099; `~/.peon` vor/nach vergleichen; `@TempDir` liegt auf macOS unter
  TMP → Test-Homes nach `target/`.
* `lintDocsAndTests`/`nextIds`: Pfade relativ zum Session-cwd (aifactory) — `docRoots:["docs"]` reicht
  (kein Präfix `aifactory/`, kein `root`-Parameter); `testRoots` ebenso relativ (Fabrik + UI).
* eclipseEditFile verliert `oldString` bei langen Blöcken → `diskEditFile` (kurz ankern); sonst
  `eclipseWriteFile`/`eclipseReplaceLines`.
* `TurnClaim`-Tests: Kachel-Handshake (COMPACTING⇒IDLE) statt Core-Flags als Warte-Bedingung.
* Dev-Agent kann eigenmächtig „Review-Blöcke"/Entscheidungen in den Plan schreiben — Review-Verdicts
  und neue Inkremente kommen nur von Jon; nach garbled Antworten erst Statusanfrage (memory-Regel 3).
* `web-prototype.md` existiert nicht mehr — SOLL in `chat.md`/`files.md`.
* Plan-Archive: **nicht committen, sondern löschen** — Pauls Regel (2026-11-02, Runde 2): sobald die
  Docs stehen, löscht Jon jedes Plan-Archiv selbst. Erledigt: 6 Plugin-/Core-Archive (Repo-Root
  `peon-plan/`, Zyklen 27.–31.10.) + eigenes Archiv `overview-done-2026-11-02-17-15.md`.
