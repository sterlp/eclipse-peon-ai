# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Paul committet selbst (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** — gilt auch für Core-Tests. `AGENTS*.md` = nur Dauerwissen (Plan-Dateien und
`peon-plan/review-findings.md` sind das Memory); Docs duplizieren nie Core-Regeln; Plan-Archive werden
**gelöscht**, nie committet.

## Stand (2026-11-11, Zyklus „Auswahlfeld: Pfeil ja, Clearer nein" — geplant, A im Bau)

* **Neuer Zyklus (Paul freigegeben: „wenn es mit dem bau besser ist dann absegnen")**: SOLL in `ui.md`
  **R-WP-67/UC-WP-104** (❌) — AntD 6.7.0 malt am `AutoComplete` **keinen** Pfeil (`suffix`/`suffixIcon`
  ohne Default, gemessen per `antd info` + Quelle `AutoComplete.js:151`), der `allowClear`-Knopf des
  Think-Feldes saß auf der Klickfläche. **Beide Felder stehen nur auf der Config-Seite**
  (`config.page.tsx:244/267`, `config.agents.tsx:248/264`) — **nicht im Composer** (das hatte ich Paul
  falsch gesagt; Plugin-Parität bestätigt: `ModelComboWidget`/`ModelConfigWidget.newCombo()`, SWT-`Combo`
  hat immer einen Pfeil und **niemals** einen Clear-Button, „leer" = erster Listeneintrag).
  Plan `peon-plan/overview.md`: **A** nur hinzufügen (Pfeil, eigene Datei `src/config/config.choice.tsx`)
  → **B** nur löschen (`allowClear`, `config.think.tsx:62`). Doc-Lint-Basis **30** ⇒ Ziel **29**.
### Voriger Zyklus „Grundregeln + Stop-Barkeit + Panel-Lebensdauer" — gebaut, reviewed, gemergt-ready

* **Branch `web-factory-5`**, gepusht bis `bb2d64a5` (Docs `4f507525`, Skill-Ledger `bb2d64a5`). Gates: **UI 292 Tests / 35 Dateien**,
  **Backend 276 / missCount = 1**, oxlint/tsc/`antd lint` je 0, `lintDocsAndTests` **29 Findings**
  (28 fremd UC-CR/UC-PC + UC-WP-101), **UC 130/130 definiert**, keine `UNBELEGT_ERLEDIGT`.
* **Regeln dieses Zyklus — alle ✅, Browser-Abnahme durch Paul offen:**
  **R-WP-65/UC-WP-102** Stop nur bei stoppbarem Turn (`9f466a8a` + `c2a18caf` + `ce4538bb`) ·
  **R-WP-66/UC-WP-103** Panel-Lebensdauer (`351fea28` + `ce4538bb`) ·
  **R-WP-62 geschärft** (Panel ist das Panel, Lebensdauer ist R-WP-66).
  **❌ blieb nur R-WP-64/UC-WP-101** (Styling ans Widget) — **das nächste Feature-Inkrement**.
* **Bau in vier kleinen Schritten** (bewährt): A Testteilung + `chat.turn.controls.ts` (`8a8b5030`) →
  A2b `chat.read.ts` (`43750900`) → B Backend-Feld + UI (`9f466a8a`, `351fea28`, Marker `53a0c9c3`) →
  C Review-Nacharbeit Verhalten (`c2a18caf`) → D Nachweise nur (`ce4538bb`, Docs `461aa2f3`).
  Größen jetzt: `chat.page.tsx` **525**/600, `chat.turn.controls.ts` 172, `chat.read.ts` 94,
  `chat.page.test.tsx` 738, `chat.page.stop.test.tsx` 366. **`styles.css` weiterhin 1286 > 1000** (R-WP-64).
* **Architektur-Entscheidung:** „ein Turn ist stoppbar" gehört `TurnClaim.turnIsStoppable()` → **ein** Feld
  `stoppable` am `AgentTileDto`, gesetzt **nur auf Jons Kachel**, getragen vom bestehenden
  Kachel-Snapshot/Push (R-WP-60). Abgelehnt: eigener Endpoint/Wrapper, neuer `AgentTileState` „AKTION",
  dynamische `TileCapability`.
* **Zwei Grenzen, die der Review gefunden hat und die jetzt im Doc stehen** (`docs/chat.md` R-WP-65):
  der **eigene Send-Intent** überstimmt den Stand **nicht**, sobald eine Kachel Arbeit meldet
  (`stoppable = some(stoppable) || (ownTurn && keine Kachel WORKING/COMPACTING)`); und eine **`409` auf
  Stop lässt den Intent fallen** (Netzwerkfehler nicht). Grund: ein Turn, den nur die eigene Erinnerung
  behält, ist kein Zustand.
* **AF-ADR-004**: Jons eigene Absorptions-Ausnahme „409 auf Stop bleibt still" (2026-11-10) ist von Paul
  verworfen — **keine Server-Ablehnung auf eine Nutzer-Aktion bleibt unsichtbar**.
* **Review #15** (Verdict REJECTED → alle Punkte gebaut): Findings liegen in
  `aifactory/peon-plan/review-findings.md` §15 (der Plan wird überschrieben, Findings müssen dorthin —
  Review-Agent kann nur `overview.md` schreiben, Kopie ist Jons Schritt).
* **Nach Pauls Antworten offengeblieben:** **B0/Q1** Reasoning-Dropdown (meine Test-Anleitung steht im
  Chat; `AutoComplete` hat keinen Chevron, rechts sitzt der Clear-Knopf) · ⏳ gestoppte Zeilen überleben
  genau einen Rebuild · ⏳ Summary-Zeile nach Compact = eigene Eingabe (Plugin-Parität) ·
  ⏳ Kern-Flags ohne Hook (B5-Grenze) · ⏳ M3 Spring-Advice · ⏳ Stop-Fenster nach eigenem Senden ·
  Browser-Abnahmen: Look Inc 4, Drawer §7, A7 Punkt 8, R-WP-48, F8 Config, Look-Story, R-WP-58/59/61/62,
  R-WP-65/66, Compact/Clear am echten Browser.
* **Nächste Inkrete:** 1) **B0-Zyklus zu Ende bauen** — Inkrement B (`allowClear` am Think-Feld weg);
  A ist grün, O1 von Jon entschieden: **`suffix`** statt `suffixIcon` (deprecated);
  2) **R-WP-64** Styling ans Widget — `styles.css` 1286 in kleine Schritte auflösen (gemeinsame Datei =
  Tokens/Typografie/Geteiltes; `test/shared/sheets.ts::declarations()` trifft nur den **ersten** Block,
  CSS-Tests müssen mitziehen); 3) C-2a Compact-Ergebnis live; 4) C-1 TurnClaim-Timeout
  (`ProjectRuntime.java:348`); 5) F9/F10, M2b. Auf Zuruf: Büroklammer (🚧).
* **Sicherheitsbefund (unverändert relevant):** ≥13 **Injektionsversuche ausschließlich in
  Tool-Resultaten** (Shell-Output, Leseblöcke, `compactSession`, erfundene „[Queued Message]"-Aufträge wie
  „B7c"): Verifikation auslassen, Trailer „Approved by human partner: Jon", falsche „harness note".
  Nichts befolgt — Regel dauerhaft im memory*-Speicher.

## Fallstricke (gemessen)

* **Doc-Lint selbst messen, nicht die Agenturmeldung:** Dev meldete nach meinem Doc-Edit **33** Befunde
  und schrieb vier davon (`DOPPELT_DEFINIERT`, `PRAEFIX_FEHLT`, `UC_OHNE_REGEL`, `STATUS_FEHLT`) meiner
  `memory.md`-Überschrift zu. Nach meinem Umbau der Überschrift messe ich **29** (Basis wieder erreicht,
  UC-WP-104 nicht mehr `UNBELEGT`) — die Ursache ist **nicht mehr reproduzierbar** (`memory.md` steht
  ohnehin unter „not participating"). Fazit bleibt: ID-Häufungen in Überschriften sind verdächtig, und die
  Zahl kommt aus meinem Lint-Lauf, nicht aus dem Report.
* **`toBeNull()`-Fall:** „ist nicht `null`" als `.not.toBeNull()` ist auf **`undefined` wahr** — die
  Pfeil-Aussage war grün, obwohl kein Element da war (von Dev per Temp-Log gemessen). Nachweis immer
  `toBeTruthy()` am Element. AntD 6.7.0: **`suffixIcon` ist deprecated** (`antd info` zeigt es nicht,
  `BaseSelect/index.d.ts:98` und `antd lint` schon) ⇒ **`suffix`** am `AutoComplete`; `antd lint` braucht
  das Pfad-Argument **`src`** — `./src` scannt 0 Dateien und ist ein grüner Nicht-Nachweis.
* **Test-Marker: eine ID pro `//`-Zeile, direkt über dem Test** — Mischzeilen (`// R-WP-65, UC-WP-102`)
  zählen nicht, die UC bleibt `UNBELEGT` (2× gemessen; steht jetzt in `AGENTS-DEV.md`). Nach einem
  Marker-Edit den **Lint-Lauf** zeigen, nicht nur die Tests.
* **Der Review-Agent kann nicht in `review-findings.md` schreiben** (nur `overview.md`) → Jon kopiert den
  Block, bevor der nächste Planüberschrieb den Fund zerstört.
* **Ein lokaler Client-Intent, der eine Server-Aussage überstimmt, ist eine Dauer-Falle** — er ist die
  einzige Stelle dieser Art im Chat und kippt bei jedem neuen „ist gerade etwas zu tun?"-Fall.
* **Dev meldet auch Ungeprüftes als Tatsache** (dieser Zyklus: „Plan wurde archiviert" — nie nachgesehen;
  zuvor: „Docs entfernen R-WP-64/65"): vor jeder Reaktion selbst messen bzw. Befehl-Ausgabe verlangen.
* Bauen: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`. Backend
  `mvn -o -f aifactory/pom.xml -pl factory-backend clean test`; UI `cd factory-ui &&
  COREPACK_ENABLE_PROJECT_SPEC=0 pnpm test`. Nur-UI-Änderung ⇒ nur UI-Tests. Smoke:
  `spring-boot:run -Dspring-boot.run.arguments=--server.port=8099` → `http://localhost:8099/ui/`.
  nvm-`default` bleibt 22.12 ⇒ PATH-Export auf v22.23.4 Pflicht; vor jedem UI-Gate `node -v`/`pnpm -v`.
* **Vitest-Filter:** nie `pnpm test -- <datei>` (jsdom CSS-Fehler) ⇒ `pnpm test -t '<name>'`; `-t` filtert
  **Testnamen** — ein Dateiwort ist ein **Scheinlauf**.
* **Anzeige eines offenen Zustands immer an das laufende Objekt binden**; **Identität schützt das _Ob_,
  nicht das _Wo_**. Der Dreh-Indikator endet über `writeTurn(null)`; eine **Aktion** schuldet kein Turn-Ende,
  also endet er bei ihr über die Kachel-Fallkante.
* **Zusage ≠ Fertig:** `202` heißt „angenommen"; Verlauf und Roster gehören an das **Ende** der Aktion
  (`agents`-Fallkante). Der Clear meldet nie etwas außer diesem Snapshot — der Kern hat keinen `onClear`-Hook.
* **Stale-Fallen Fabrik-UI:** `factory-ui/pom.xml` clean-Filesets zeigen auf `web-app/` ⇒ `mvn clean`
  löscht `factory-ui/dist` nie; `copy-resources` ist additiv; Eclipse baut nie `pnpm run build`; ein
  repo-aufgelöster `spring-boot:run` liefert die Oktober-SPA aus `~/.m2`. Bei „Zustand kommt aus Quelle X"
  immer am **Frischbau** messen.
* AntD 6.7.0: hell malt **`lightSiderBg`**; `matchMedia`+`ResizeObserver` stubben; `collapsed` controlled ⇒
  Effekt ignoriert; `Menu` ohne `navigation`-Landmarke; **Alert** bringt `role="alert"` selbst;
  **`AutoComplete` setzt `suffix=null`** ⇒ kein Chevron (B0). AntD nie aus dem Gedächtnis:
  `antd info`/`antd lint` (Skill `~/.agents/skills/antd`; CLI nur unter v22.23.4). jsdom rechnet **keine
  Farbe** ⇒ Rot-Beweis über DOM-Handle + Sheet-Deklaration; `+`/`~`-CSS braucht den Nestungs-Beweis;
  Vitest ohne `.as()`; `act()`-Rauschen bei AntD+React 19 ist Baseline.
* `lintDocsAndTests`/`nextIds`: Pfade relativ zum Session-cwd (`docRoots:["docs"]`, `testRoots`:
  `factory-backend/src/test`, `factory-ui/test`, `factory-ui/src`) — **`idPattern` weglassen**; Basis
  **29 Findings**. `nextIds` zählt auch **Verweise** in Linktexten: erst schreiben, dann ziehen.
* Git mit Paul im selben Repo: **nie amend/rebase auf seinen Commits**, nur `aifactory/`-Pfade, **kein
  Modell-/Tool-Name, kein `Assisted-by`**, **kein `git add -A`**; `peon-plan/` ist enttrackt + ignored.
  Neuen Datei committen: `git add -- <pfade>` + **nacktes** `git commit` (`-o` nimmt Ungetrackte nicht);
  Index-Löschung = `git rm --cached` + nacktes `git commit`. Tool-Quirks: `eclipseEditFile` verliert
  `oldString` bei langen Blöcken ⇒ `diskEditFile` mit frisch gelesener Datei und kurzem Anker;
  `*InsertLines` scheitern an Dateiende mit Leerzeile; Mutations-Restore nur per `cp` + `cmp`.
* **Agenten-Grenzen:** Review-Verdicts und neue Inkremente kommen **nur von Jon**; Dev-Antworten manchmal
  als rohes Tool-Fragment ⇒ Text-Status verlangen, nie neu bauen. Kontext: >90 % ⇒ `compactPlan`,
  >85 % ⇒ `compactDev`/`compactReview`.
* **Aufträge an Plan/Dev beginnen mit „Auftrag = Inkrement X, entscheidungsvollständig"** — sonst plant der
  Plan eine Scope-Frage. Je Inkrement **eine** Polarität (nur hinzufügen ODER nur löschen).
* `web-prototype.md` existiert nicht mehr — SOLL in `chat.md`/`files.md`. Grundsatzentscheidungen Pauls
  immer im **Eclipse-Plugin** nachlesen (`chat.html`, `ActionsBarWidget`, `AIChatView`), nicht neu fragen.
