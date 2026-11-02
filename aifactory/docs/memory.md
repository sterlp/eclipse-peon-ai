# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Paul committet selbst (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** (2026-10-29) — gilt auch für Core-Tests.

## Zyklus 2026-11-02 LÄUFT — UI-Nacharbeiten nach Pauls Smoke-Test

Branch `web-factory-mvp`, Spitze `c3123717` (= origin). Inc 5/6 + 7a/7b + Review-Followup abgeschlossen:
Backend **195/0/0**, UI **112 Tests/14 Dateien**, oxlint 0/0, UC **63/63** (Dev-Slot: Modell/URL/Key
gespeichert, wirken nicht — ⏳). Review 7b bestanden; Plan archiviert (`overview-done-2026-11-02-17-15.md`,
danach gelöscht — Pauls Regel: alte Pläne löschen, sobald die Docs stehen). Config-Docs
R-WP-16/23 + UC-WP-44/45/46/62/63 ✅.

**Pauls 10 Feedback-Punkte (2026-11-02, Runde 2) = Backlog dieses Zyklus:**

1. Alte Pläne: Jon löscht sie selbst, sobald die Docs stehen ✅ (6 Plugin-/Core-Archive + eigenes Archiv gelöscht)
2. Chat-Rand zu stark; nur ein hr war gemeint — und der Trenner ist rechts statt links
3. Gesendete Nachricht verschwindet nach Navigation; Fehler weder im Chat noch im UI sichtbar
4. Think-Level je Agent einstellbar wie in Eclipse
5. Files-Slide-out sitzt mittig am Pfeil — Deckel-Linie nicht bündig
6. Datei-Viewer: Zeilen wie mit doppeltem Enter — zu weit auseinander
7. Nach Key-Setzen verschwindet der Fehler im UI; der Chat soll den Fehler ebenfalls bekommen
8. Nach Jons Antwort verschwindet der schwarze „in-flight"-Rahmen um meine Nachricht nicht
9. Chat-Scroll fehlt; Input unten fixed wie in der Vorlage — Chat und Config kreativ überarbeiten
10. Nach Stop bleibt der schwarze gestrichelte Rahmen

**Runde 1 — 19 Smoke-Test-Befunde (2026-11-02): abgearbeitet (Details im gelöschten Archiv
`overview-done-2026-11-02-17-15.md`):**

1. Bootstrap rein; Config-UI Kraut und Rüben
2. Config: Provider-Wechsel ohne Live-Update wie Eclipse
3. API-Key muss gesetzt werden, obwohl Endpunkte in der Config stehen — klären
4. Statische Ränder L/R raus — Chat nutzt fast volle Breite
5. Input: Enter = Neue Zeile, Cmd/Ctrl+Enter = Senden
6. „Waiting for AI…"-Indikator wie Eclipse
7. Farben: AI grün, User blau, User am rechten Chat-Rand
8. Agent-Cards oben nebeneinander; Agent-Name als Titel, Rest darunter
9. Erneut senden ohne Neueingabe (z. B. nach Fehler)
10. „Chat"-Überschrift im Chat raus
11. Card-Klick = Funktion (Blick aufziehen), Hover fehlt
12. Jons Antwort: grauer Hintergrund raus, keine Marks/Cards, nur hr bei Rollenwechsel
13. Tool-Ausgaben kompakt; notfalls Portierung aus alter `chat.html`
14. Antworten/Dateien nicht mittig zentrieren
15. Dateien: linkes Slide-out, Files anklickbar, Anzeige unten/Split — Layout mit Paul prototypen
16. Linke Sidebar: verfügbare Projekte + Status (läuft gerade), Tabs „Chats"/„Files" darüber
17. UI Englisch + `content.ts` (Strings aus dem Code), i18n
18. Syntax-Highlighting: **react-shiki** (lib + Usage von Paul gegeben)
19. URL-stabil: Navigation = URL-Änderung (Aktion → URL → UI), kein useState

**Entscheidungen (2026-11-02, mit Paul):**
* Design: **Bootstrap + eigenes Theme** (Farben der alten `chat.html`); stara-web bleibt nur Referenz.
* Streaming: **markstream-react** + **react-shiki** für Code; Fallback react-markdown. Tool-Zeilen
  sichtbar, inline im Verlauf, kompakt.
* Navigation MVP: **Top-Menü (Projekte, Chat, Settings), keine Sidebar**; Chat = zuletzt aktives
  Projekt (⏳). Chats-Übersicht (Accordion, Paco-Chats +) = spätere Vision (❓).
* Files: **rechts als Slide-out** (bleibt), vertikaler „Files"-Tab am rechten Rand, `>`/`<`-Icons,
  Wurzeln Checkout/TMP als Cards, Baum-Navigation, kein Projektname im Panel, Viewer mit
  Syntax-Highlighting, nichts zentriert.
* Config: **dynamische abhängige Felder** (Provider-Wechsel → Modell/URL/Think, wie Eclipse),
  „Remove key"-Aktion, Key-Status frisch nach jedem Speichern, Fehler nennt die Settings-Seite (⏳).
* AGENTS.md „UI: English" von Paul bestätigt → Language-Block-❓ gelöst, Block committen.

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
