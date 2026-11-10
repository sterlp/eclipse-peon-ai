# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Paul committet selbst (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** — gilt auch für Core-Tests. `AGENTS*.md` = nur Dauerwissen (Plan-Dateien und
`peon-plan/review-findings.md` sind das Memory); Docs duplizieren nie Core-Regeln; Plan-Archive werden
**gelöscht**, nie committet.

## Stand (2026-11-10, Release)

* **Release durch PAUL selbst** („kein main merge das mache immer ich"): `web-factory-4` → main macht
  Paul — main hat `9fa23402` (Plan-Mode-Doku) voraus, Paul hat **main in den Branch gemergt** für sein
  Final-Review, danach merged er den Branch nach main. Stand `web-factory-4` = `de688a32` (vorn 73 /
  hinten 0). Inhalt der Spitze: **API-Fehler ✅** (R-WP-54/55/56 + UC-WP-92/93, UC-WP-91 gefaltet in
  R-WP-54, 5 Inkremente `e6ca3573`→`b78e59d1` + N1 `c47c9edc`/`d35f7f27`), **R18 Skill-Refresh**
  `88b81a8c` (Paul), **R6 Skill-Evolution-Loop ✅** `742293c3`. **Backend 274 / UI 268 Tests** grün,
  missCount=1, tsc/oxlint/antd-lint 0, Core-Suite 1486/0. Kein Core-Eingriff der Story.
* **BUG A geparkt:** ⏳ im Root-`docs/open-points.md` (uncommittet — Paul committet ihn selbst mit dem
  Release) — R18-Zusage „eigene Schreib-Änderung"
  Merge oder Rebase entscheidet Paul. Stand `web-factory-4` = `9663a2a9` (Docs-Commit), vorn 73 /
  hinten 1. Inhalt der Spitze: **API-Fehler ✅** (R-WP-54/55/56 + UC-WP-92/93, UC-WP-91 gefaltet in
  R-WP-54, 5 Inkremente `e6ca3573`→`b78e59d1` + N1 `c47c9edc`/`d35f7f27`), **R18 Skill-Refresh**
  `88b81a8c` (Paul), **R6 Skill-Evolution-Loop ✅** `742293c3`. **Backend 274 / UI 268 Tests** grün,
  missCount=1, tsc/oxlint/antd-lint 0, Core-Suite 1486/0. Kein Core-Eingriff der Story.

* **Ebenfalls drin:** F8 Config-Plugin-Parität ✅ (R-WP-49–52/UC-WP-85–88), C-7 Blick-live ✅
  (R-WP-53/UC-WP-89/90), R-WP-48 Endereignis ✅ — Details in den Docs.
* **BUG A geparkt:** ⏳ in `/llmpeon-parent/docs/open-points.md` — R18-Zusage „eigene Schreib-Änderung"
  gilt nicht innerhalb der 10-min-Ruhezeit (skillRead stale); Proper fix = Refresh ins Tool /
  Skill-Writes an Scaffold, späteres Tool-Extension-Inkrement.
* **Nächste Story: Runde-7-Look-Story** (Pauls Browser-Befunde, ⏳/❓ in [open-points.md](open-points.md))
  — **vorher die Kachel-Reihe ❓ messen** (most critical; Regression aus C-7, `chat.tiles.tsx` /
  Kacheln-Read nach `onOpen`). Danach: C-2a, C-1 TurnClaim-Timeout (`ProjectRuntime.java:348`),
  F9/F10, M2b (Paul-Entscheidung).
* **Offene Browser-Abnahmen:** Feldabstände N1-A9, ältere Look-Abnahmen (Inc-4 Look, Drawer §7,
  A7 Punkt 8, R-WP-48), F8 Config-Seite, C-7 Höhe/Follow im Slide-out.
* **⏳ M3** (Spring default error view): Messung sagt „Advice bleibt", Pauls Rückversicherung ausstehend.
* **Auf Zuruf:** Skill-Proposal `proposals/antd-skill-cli-rows-update.md` (4 CLI-Zeilen + Hinweis
  „installierte Version prüfen"); leeres Doppelverzeichnis `aifactory/aifactory/factory-ui/test/app`
  (+ `.DS_Store`) löschen.
* R18-Zusage „generell als done setzen" (Paul) ist geflippt (R6 ✅, `742293c3`); Docs-Commit siehe
  Release-Commit.

## Fallstricke (gemessen)

* Bauen: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`. Backend
  `mvn -o -f aifactory/pom.xml -pl factory-backend clean test`; UI `cd factory-ui &&
  COREPACK_ENABLE_PROJECT_SPEC=0 pnpm test` (pnpm 10.28.0, Node 22.12). Nur-UI-Änderung ⇒ nur UI-Tests.
  Smoke: `spring-boot:run -Dspring-boot.run.arguments=--server.port=8099` → `http://localhost:8099/ui/`.
  Node 22.23.4 installiert, **nvm-`default` bleibt 22.12** ⇒ PATH-Export Pflicht; vor jedem UI-Gate
  `node -v` ≥22 + `pnpm -v` prüfen.
* **Stale-Fallen Fabrik-UI:** `factory-ui/pom.xml` clean-Filesets zeigen auf `web-app/` (existiert
  nicht) ⇒ `mvn clean` löscht `factory-ui/dist` nie; `copy-resources` ist additiv (mehrere
  Asset-Generationen in `target/classes/static/ui`); ein Eclipse-Bau führt `pnpm run build` nie aus;
  ein repo-aufgelöster `mvn spring-boot:run` liefert die Oktober-SPA aus `~/.m2`. Bei „Zustand kommt
  aus Quelle X" immer am **Frischbau** messen.
* **Bei „Zustand kommt aus Quelle X" muss die Mutation geseedeten State UND Mount-Einfrieren treffen**;
  `unmount()` + Neu-Mount prüft zwei Mounts, nie eine Zustandsänderung. Anzeige eines offenen Zustands
  immer an das **laufende Objekt** binden, nie an „Flag + letztes Element"; **Identität schützt das _Ob_,
  nicht das _Wo_** (Idiom `chat.page.test.tsx::toolLinesSurviveTheDoneRefresh`).
* AntD 6.7.0: hell malt **`lightSiderBg`** (nicht `siderBg`); Responsive-Effekt ruft `matchMedia`
  **beim Mount** ⇒ gefiltert stubben (`includes('max-width')`); `collapsed` controlled ⇒ Effekt
  ignoriert; Zero-Width-Trigger ist ein **`span` ohne Rolle**; `Menu` liefert **keine** `navigation`-
  Landmarke ⇒ eigenes `<nav aria-label>`; `aria-current` am Eintrag = ANTD-A9. AntD nie aus dem
  Gedächtnis: `antd info/lint` vor Abschluss (Skill user-level `~/.agents/skills/antd`).
* `sheets.ts::declarations()` trifft nur den **ersten** Block ⇒ `allDeclarations`; Selektorwortlaut nie
  in CSS-Prosa; jsdom 26.1.0 ohne Layout/Farbe; Vitest ohne `.as()`; `act()`-Rauschen bei AntD+React 19
  ist Baseline; **CSS mit `+`/`~` braucht einen Nestungs-Beweis** (DOM-Assertion auf Adjazenz).
* Vitest nie `pnpm test -- <datei>` (jsdom CSS-Fehler) ⇒ `pnpm test -t '<name>'`.
* `MockLlmServer`: kein `getRequestCount()`, keine `system`-Nachrichten, SSE in einem Stück. Guards
  `OneChatPerProjectTest`/`SharedProjectChatTest`/`ChatTurnTest`/`TeamInternalsTest` nur **verschärfen**.
* `lintDocsAndTests`/`nextIds`: Pfade relativ zum Session-cwd (`docRoots:["docs"]`, `testRoots`:
  `factory-backend/src/test`, `factory-ui/test`, `factory-ui/src`) — **`idPattern` weglassen**.
* Git mit Paul im selben Repo: **nie amend/rebase auf seinen Commits**, nur `aifactory/`-Pfade,
  **kein Modell-/Tool-Name und kein `Assisted-by`-Trailer**, kein `git add -A` (fängt Plan-Archive und
  Pauls Core-Edits ein). Tool-Quirks: `eclipseEditFile` verliert `oldString` bei langen Blöcken ⇒
  `diskEditFile` kurz ankern; `*InsertLines` scheitern am Dateiende mit Leerzeile; Mutations-Restore
  nur per `cp` + `diff -q`, nie `git checkout`.
* Dev-Antworten kommen manchmal als rohes Tool-Fragment ⇒ **nie neu bauen**, Text-Status verlangen.
  Review-Verdicts und neue Inkremente kommen **nur von Jon**. Dev-Kontext >85 % → `compactDev`.
* Patches: Patch-Basis = **Branch-Commits, nicht main**; `git apply --3way` versteckt Konflikte ⇒ erst
  `--check -R`, dann ohne `--3way`.
* `web-prototype.md` existiert nicht mehr — SOLL in `chat.md`/`files.md`. Grundsatzentscheidungen
  Pauls immer im **Eclipse-Plugin** nachlesen (chat.html, Plugin-Dialoge), nicht neu fragen.
