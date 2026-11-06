# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Paul committet selbst (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** — gilt auch für Core-Tests. `AGENTS*.md` = nur Dauerwissen (Plan-Dateien sind das
Memory); Docs duplizieren nie Core-Regeln; Plan-Archive werden **gelöscht**, nie committet.

## Zyklus 2026-11-06 — Runde 4: AntD-Umbau (autonom, Paul seit 12:05 wieder erreichbar)

Branch `web-factory-mvp-2`, Spitze `6249d59a` = origin. Backlog + Reihenfolge:
[open-points.md](open-points.md) („Runde 3 — AntD-Umbau"). Gebaut und abgenommen:

* **Inc 1 AntD-Basis ✅** — [R-WP-39/UC-WP-71](ui.md): AntD **6.7.0**, Bootstrap raus (Wache sieht
  CSS-Import, `.css`, `devDependencies`), **eine** Dunkel-Tabelle via `data-theme`, `AppRoot` selbst
  gemountet.
* **Inc 2 Markdown ✅** — [R-WP-28/UC-WP-73](chat.md): `react-markdown@10.1` + `remark-gfm` +
  `remark-breaks`, `div.chat-markdown-table` als Scroll-Kasten, `separatorsOf(log)` = einzige
  Trenner-Definition. **2-N2**: `AGENTS-DEV.md` 1031 → 225 Zeilen, episodisches Memory nach
  [`peon-plan/dev-notes-antd.md`](../peon-plan/dev-notes-antd.md).
* **Inc 3 Eingabe ✅** — [R-WP-41/UC-WP-74](chat.md), [R-WP-30/UC-WP-56](chat.md): `Input.TextArea
  autoSize` (`MAX_ROWS = 5`), Enter sendet, Shift+Enter **und** Ctrl/Cmd+Enter = Umbruch **am Caret**,
  Send nur bei Text, `typingElsewhere()`-Guard; AntD-Wache **fail-closed** (Bann = ganzes `src/chat/`,
  Allowlist allein `chat.composer.tsx`).
* **Inc 3b `!` ✅** — [R-WP-42/UC-WP-75](chat.md): `ProjectRuntime.urgentTextOf` + Urgent-Lane als
  **erste** Anweisung in `submit`, Bedingung `jon.isWorking()`, Blank-`!` konsumiert, Absendername trägt
  sie wie jede Eingabe.
* **Inc 4a kein `hr` ✅** — [R-WP-29/UC-WP-55](chat.md): `rule`/`<hr className="chat-rule">` und das
  zugehörige CSS sind weg; Antwort-`hr` (`---` in einer Antwort) bleibt. **Zwei echte Defekte, die der
  Review/Bau fand:** (1) der Blätter-Guard sah nur **ein** Deklarationsfeld → jetzt
  `sheets.ts::allDeclarations` über **alle** Blätter inkl. `@media`; (2) `.chat-line.pending` setzte
  `border-style: dashed` auf **vier** Seiten = horizontale Linien über/unter einer wartenden Zeile →
  `border-left-style` (`styles.css:493-496`). Guard verbietet jetzt **jede** `border*`-Deklaration ohne
  `left` an einem `.chat-line`-Selektor. Merksatz: **ein Verbot, das nur den ersten Selektor liest, ist
  kein Verbot.**

**Stand gemessen:** UI **173/20 Dateien ~8,7 s**, `tsc -b` 0, oxlint 0/0 · Backend **225/0**,
`missCount 1` · Doc-Lint (eigener Lauf `docRoots:["docs"]`, **ohne** `idPattern`) **30 Findings** = nur
Fremd-/Folgedocs (PC 14, CR 14, `UC-WP-72` = Inc 4b/4c, `UC-WP-70` = Inc 5a). Dev und Review melden
weniger — ihr Lint scopet enger: **immer selbst messen.**

**Nächste Schritte:** **Inc 4b** Timeline für Werkzeug-Schritte ([R-WP-40/UC-WP-72](chat.md):
Core-Stempel als Name, Ankunftszeit, Indikator am letzten Schritt des laufenden Turns, Cap behält Zeiten,
Reload erfindet keine Zeit) → **Inc 4c** Delegation als eigener Eintrag (typisiertes Anfang/Ende über die
Listener der Delegation, Wort „working", Mitglied nennt sich selbst) → **Inc 5a** Sider + URL
([R-WP-38/UC-WP-70](ui.md), C-3, `aria-current` zurück) → **5b** Files-Unit/Responsive. Danach
Rest-Backlog: C-7 (Agent-Blick read-only live), C-2a, C-1 TurnClaim-Timeout, F8/F10, Mini 3c
(`data:`-Bild ⇒ Alt statt leerem `src`). Stagehand ⏳ parked (Node ≥22.18 vs. Bau 22.12).

**Nach Inc 3 gilt für die Wache:** jede neue Datei im Chat-Ordner mit AntD-Import ist rot — die Timeline
malt ihr CSS selbst ([R-WP-39](ui.md) erlaubt genau das für Verlauf, Werkzeuge, Diffs, Think).

## Entscheidungen dieser Runde (Kurzfassung)

AntD wird Design-Basis, Bootstrap raus ([R-WP-39](ui.md)); Navigation = **Projektbaum im Sider**, Klick
aufs Projekt öffnet den Chat, **kein „Jon"-Label** ([R-WP-38](ui.md)); **kein `hr`** zwischen Zeilen
([R-WP-29](chat.md)); Timeline für Werkzeuge/Delegationen ([R-WP-40](chat.md)); Markdown mit GFM +
einfachen Umbrüchen ([R-WP-28](chat.md)); Eingabe Auto-Grow/Refocus ([R-WP-41](chat.md)); **Zeiten bleiben
live** (kein zweiter Speicher, kein Text-Parsing). Tastatur: Enter sendet, Shift+Enter **und**
Ctrl/Cmd+Enter = neue Zeile ([R-WP-30](chat.md), Merkposten in [resolved-points.md](resolved-points.md)).
`!` = Prio-Nachricht des Cores, Fabrik reicht sie nur durch
([`queued-user-messages.md`](../../org.sterl.llmpeon.core/docs/queued-user-messages.md) R8/R11). Grenze:
[AF-ADR-003](adr/003-reference-ui-is-a-donor-not-a-source.md) — die Referenz `ai-schulung` gibt Muster,
**nie** unseren Zustand (C-5-Fix bleibt grün). [glossary.md](glossary.md) definiert **Jon** und **Paco**.

## Archivierte Stationen

* **✅ F6/F7:** 23 → **1** Spring-Kontext, Wanduhr 118 → 42,1 s, null Produktionscode; Guard
  `SingleContextGuardTest`, Lifecycle-Netz 120 s, TurnGate statt Schlaf, ein Home `target/test-home`.
* **✅ C-4 Diffs** ([R-WP-36/UC-WP-68](chat.md)): Anlegen = `+`-Diff, `SimpleDiff`-Cap 500 partziell,
  Binary → Werkzeug-Zeile (Core-Erweiterung von Paul).
* **✅ C-5 Verlauf out of order** ([R-WP-37/UC-WP-69](chat.md)): Anker je Turn, Read-Sequenz + `mark`,
  Bestätigung über Position, **Wasserlinie je Absender** (zustandslos); `senderName()` eine
  Normierungsstelle. Geschärft: „You" = Anzeige des anonymen Nutzers, namenlose Eingaben = **eine**
  Absenderklasse.

## Offen für Paul (❓/⏳ in [open-points.md](open-points.md))

1. Core: abgebrochener Turn verliert eine bereits gepollte Queue-Eingabe (`UC-WP-50` bleibt ❌) —
   Core-Story, nur per Proposal.
2. C-2 Core-Test-Proposal; Paco-Story (Nutzer-Räume, Write-Roots `paco/<nutzer>/`).
3. ⏳-Annahmen zur Bestätigung: manueller Dunkel-Schalter, „Jon"-Label auch auf der Kachel,
   Refocus-Guard, **Inkrement-4-Auslegungen** („arbeitet" = letzter Schritt des laufenden Turns, Stempel
   ist praktisch immer „Jon", Think-Abschnitt bleibt unsichtbar), Dev-Slot-Wirkung, Stagehand parked.
4. C-1 TurnClaim-Timeout, C-7, F3/F5/F8/F9/F10, Bild-Support-Anzeige.

## Fallstricke (gemessen)

* Bauen: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`.
  Backend `mvn -o -f aifactory/pom.xml -pl factory-backend clean test`
  (`-Dsurefire.failIfNoSpecifiedTests=false`), UI `cd factory-ui &&
  COREPACK_ENABLE_PROJECT_SPEC=0 pnpm test` (pnpm **10.28.0**, Node 22.12). Nur-UI-Änderung ⇒ nur
  UI-Tests. Smoke: `install -DskipTests` → `-pl factory-backend spring-boot:run
  -Dspring-boot.run.arguments=--server.port=8099` → `http://localhost:8099/ui/` (Port 8080 = IDE).
* `MockLlmServer`: kein `getRequestCount()`, keine `system`-Nachrichten, SSE in einem Stück ⇒
  Cancel-mitten-im-Stream nur mit später antwortendem Endpoint. Guards
  `OneChatPerProjectTest`/`SharedProjectChatTest`/`ChatTurnTest` nur **verschärfen**.
* `lintDocsAndTests`/`nextIds`: Pfade relativ zum Session-cwd (`docRoots:["docs"]`,
  `testRoots`: `factory-backend/src/test`, `factory-ui/test`, `factory-ui/src`) — **`idPattern`
  weglassen** (sonst 0/0-Definitionen).
* jsdom 26.1.0: `matchMedia`/`ResizeObserver` nur polyfilliert, **kein Layout, keine Farbe** ⇒ Grenzen als
  exportierte Konstante pinnen, Blatt-Text über `?raw`; Selektorwortlaut nie in CSS-Prosa
  (`indexOf`-Falle); `declarations()` trifft nur den **ersten** Block ⇒ `allDeclarations` für
  Flächen-Verbote. Vitest ohne `.as()`; `expect(Optional).contains()` prüft Gleichheit.
* Git mit Paul im selben Repo: **nie amend/rebase auf seinen Commits** (SHA halten), nur
  `aifactory/`-Pfade committen, Commit nimmt den ganzen Index. Tool-Quirks: `eclipseEditFile` verliert
  `oldString` bei langen Blöcken → `diskEditFile` kurz ankern; „line required" → `diskReplaceLines`;
  `*InsertLines` scheitern an Dateiende mit Leerzeile.
* Dev-Antworten manchmal als rohes Tool-Fragment ⇒ **nie neu bauen**, Text-Status verlangen.
  Review-Verdicts und neue Inkremente kommen **nur von Jon**. Dev-Kontext >85 % → `compactDev`.
* `web-prototype.md` existiert nicht mehr — SOLL in `chat.md`/`files.md`.
