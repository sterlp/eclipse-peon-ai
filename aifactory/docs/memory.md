# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Paul committet selbst (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** (2026-10-29) — gilt auch für Core-Tests.

## Zyklus 2026-11-02 LÄUFT — UI-Nacharbeiten nach Pauls Smoke-Test

Branch `web-factory-mvp`, Spitze `3957defc` (= origin). Plan: `peon-plan/overview.md` (Inc-4-Plan
archiviert). MVP-Zyklus Inc 5/6 abgeschlossen: Backend 156/0/0, UI 47 Tests/7 Dateien, lint 0/0,
UC 50/50.

**Pauls 19 Smoke-Test-Befunde (2026-11-02) = Backlog dieses Zyklus:**

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
* `lintDocsAndTests`: `root=/Users/sterlp/dev/workset/peon-ai`, `docRoots:["aifactory/docs"]` (NICHT
  „docs"), `testRoots` Fabrik+UI, `idPattern UC-WP-\\d+`.
* eclipseEditFile verliert `oldString` bei langen Blöcken → `diskEditFile` (kurz ankern); sonst
  `eclipseWriteFile`/`eclipseReplaceLines`.
* `TurnClaim`-Tests: Kachel-Handshake (COMPACTING⇒IDLE) statt Core-Flags als Warte-Bedingung.
* Dev-Agent kann eigenmächtig „Review-Blöcke"/Entscheidungen in den Plan schreiben — Review-Verdicts
  und neue Inkremente kommen nur von Jon; nach garbled Antworten erst Statusanfrage (memory-Regel 3).
* `web-prototype.md` existiert nicht mehr — SOLL in `chat.md`/`files.md`.
