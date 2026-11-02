# AGENTS-DEV.md — implementing (the HOW)

Base: `AGENTS.md`. System prompt: `developer.md`.

## Skills by trigger

- Core change / plugin tests / Tycho build / dependency bump / test numbers → skill `peon-build-test`
- Writing a test / reporting an increment green / mutation check → skill `test-honesty`
- langchain4j / CompletableFuture / JSON extra body → skill `repo-api-traps`
- PDE / JDT / SWT / Preferences → skills `eclipse-dpe` / `eclipse-preferences`
- Release / Update-Site / gh-pages / deploy / Version bump → skill `peon-release`
- System-Prompts / Agent-Wiring / Docs-Übersicht → [docs/system-prompts.md](org.sterl.llmpeon.core/docs/system-prompts.md)


## Always-on rules (fail silently when violated — never trigger-gated)

- **Core changes are INVISIBLE to the plugin build.** After EVERY core change, BEFORE the Eclipse
  build/test run: `mvn -o -pl org.sterl.llmpeon,releng/llmpeon-target -am package -DskipTests`,
  then refresh + build `org.sterl.llmpeon` AND `org.sterl.llmpeon.test`. Full procedure +
  stale-artifact diagnostics: skill `peon-build-test`.
- **Sobald ein Plan ID-Marker vorsieht, gehört `lintDocsAndTests` in das Inkrement-Gate** —
  VERWAIST/UNBELEGT_ERLEDIGT blockieren das Inkrement, nicht erst den Review-Lauf (hit 2026-10-17:
  12 `// UC-CG-*`-Marker ohne die IDs im Doc liefen durch sechs grüne Gates). `R-*` sind keine
  UC-IDs → `idPattern` **UC-only** (`UC-[A-Z]+-\d+[a-z]?`); ein Pattern, das auch `R-*` matcht,
  liefert eine Masse Schein-VERWAIST statt der kleinen Baseline aus UNBELEGT- und MANUELL-Findings
  (`org.sterl.llmpeon.core/docs/docs-linter.md` R-DL-14). `docRoots` **gar nicht** übergeben
  (Auto-Discovery findet alle `docs`-Bäume, R-DL-26) oder alle — eine teilweise explizite Liste
  gewinnt und blendet den Rest still aus (hit 2026-10-23: 84 Schein-VERWAIST zu enger Scope).
  Melden: die echte `findings: N`-Zeile, nie `Scanned: N doc file(s)` (hit 2026-10-24, R-OD-8 F3).
- **Never widen scope silently.** Fixes outside the released plan — even correct ones, even
  one-liners — get reported to the PO first. TABU lists in a plan are binding.
- **Doc-Input, der als erledigt zitiert wird, gegen den COMMITTED Stand prüfen** (hit 2026-10-23,
  Story-2-Review G1: ein Plan las „compact.md ist bereits revidiert" aus dem Working Tree — die
  Revision war uncommitted, HEAD widersprach dem gebauten Code). Vor Abnahme/Bau: `git status`/
  `git log` gegen die zitierte Doc-Stelle; sonst wird der Doc-Input selbst zum Inkrement.
- **Docs und ADR-Renames ride mit dem Zyklus-Commit** (hit 2026-10-24, 2× R-OD-8/Plan-Tool-Review
  F1: Code committed, zugehörige Spec nur im Working Tree — frischer Checkout hat tote Javadoc-Pfade
  bzw. HEAD ohne Regel). Vor JEDEM Commit-Report: `git status --porcelain` auf modifizierte/neue
  `docs/**`, `AGENTS*.md` und ADR-Renames prüfen — fehlen sie in der Pfadliste, PO melden statt
  schweigend drüber zu committen.
- **Root-/Allowlist-Mengen an der Composition Root testen, nicht nur den Mechanismus am Seam** (hit
  2026-10-23, Story-3-Review F1: Core-Tests injizierten den User-Root per Setter — `roots.add(agentsSkillsDir)`
  in `PeonAiService` konnte ersatzlos verschwinden, ohne dass ein Test rot wurde). Ein PDE-Test auf den
  tatsächlichen Validator-Roots des realen Wirings (validate-only, keine I/O) ist die Mutations-Beweispflicht;
  `AgentService.get(name)` sieht dabei NUR geladene Custom-Agents, nicht `persistentAgents` — Scaffold/Jon/PO
  sind über `getAgents()` + Filter erreichbar.
- **Doc-Referenzen in Code/Javadoc tragen den Modul-Pfad** (`org.sterl.llmpeon.core/docs/x.md`,
  `org.sterl.llmpeon/docs/x.md`) — Root-`docs/`-Pfade sind nach der Restrukturierung (2026-10-22)
  tot für Feature-Docs; nur ADRs/Root-Docs bleiben dort. Referenzen gehören in Kommentare,
  nie in String-Literale von Tests (C2-Gate des Docs-Restrukturierungs-Zyklus).
- **Commit-Message trägt das Gate-Ergebnis** (Paul 2026-10-22): je Inkrement 1–2 Zeilen mit
  dem Ergebnis der **tatsächlich gefahrenen** Gates (z. B. `Core Surefire 1252/0/0/0 · Lint
  <n> Findings = Baseline`) — kein Surefire gefahren → Surefire-Zeile weglassen, nie angerechnet.
  Docs und ADRs tragen KEINE Test-/Mess-Zahlen — historische Werte sind stale und irreführen;
  Evidenz lebt im Commit und ggf. in den memory-Tools, nie in `docs/**`.
- Elegant, expressive modern Java (records, pattern matching, switch expressions, Lombok).
- Skill-Footer beantworten; falschen Skill im selben Turn fixen.


## Inc 3 (Zyklus 2026-11-02, Runde 2) — Chat scrollt in eigener Fläche

- **Feste Shell ohne View-Scroll clippt die anderen Seiten** (gemessen am Layout, nicht am Test):
  `#root { height: 100dvh; overflow: hidden }` nimmt Projektliste und Settings den einzigen Scroll.
  `overflow-y: auto` an `.view` (alle Views) + `overflow: hidden` an `.chat-workspace > .view` gibt
  jedem View seine eigene Fläche und dem Chat die innere; die Scroll-Regel gehört also **beiden**
  Sheetschichten, nicht nur dem Chat.
- **Scrollen ist in jsdom nur über das Blatt + einen Mock beweisbar**: `scrollHeight`/`clientHeight`
  sind 0 und `scrollTop`-Schreiben verpufft (kein Layout). Mock mit `Object.defineProperty`
  (`configurable: true`, eigener Getter/Setter für `scrollTop`), Geometrie 1000/300, dann
  `fireEvent.scroll(log)` für die Leserichtung. Proben: Guard `atBottom` raus ⇒ **nur**
  `readingUpIsNotRippedDown` rot; `area.scrollTop = area.scrollHeight` raus ⇒ **beide** Verhaltens-
  tests rot (die 24-px-Klausel am Ende macht den zweiten nicht leerläufig).
- **`declarations()` liest nur `styles.css`** — die Shell-Höhe steht in `index.css`. Neuer Zweig im
  Helfer: `baseDeclarations(selector)` (derselbe Helfer, zweite Sheet); `tokenValues` liest schon immer
  `index.css`, deshalb blieb den Tests der Unterschied bisher unsichtbar.
- **`.chat-log` ist `display: grid` und scrollt als Flex-Kind trotzdem** — `flex: 1; min-height: 0;
  overflow-y: auto` reicht, kein Umbau auf Block/Column. `min-height: 0` ist die Zeile, die den Scroll
  überhaupt möglich macht (Flex-Kind darf unter seine Inhaltshöhe) — als Sheet-Assertion gepinnt.
- **Die Warte-Leiste ist jetzt Block über dem Feld, nicht sticky** (Jon freigegeben, benannte
  Abweichung): der UC-WP-58-Test hielt `position: sticky` — genau die Wurzel von F9. Assertion auf
  Mono + Blink-Cursor **eingeengt** und im selben Test positiv ersetzt (kein `position:` im Block,
  `nextElementSibling` ist der Composer, `.chat-log` trägt `overflow-y: auto`). Doc-Wortlaut
  R-WP-31 („sticky am unteren Rand") auf „steht über der Eingabe" korrigiert.
- **Nach Inc 3: Backend 202/0/0** (unverändert — reines UI-Inkrement), **UI 121 Tests / 15 Dateien**
  (118/15), `tsc -b` 0, oxlint 0/0 (57 Dateien), Doc-Lint **0 Findings** bei UC **66/66**, 233
  Test-Ids. UC-WP-66 bleibt ❌ (flippt Jon).


## Inc 4 (Zyklus 2026-11-02, Runde 2) — Akzent links, ein hr, keine Rechtsbündigkeit

- **Die Doc-Zeile war der Defekt, nicht der Code** (F2): R-WP-29 forderte „rechtsbündig am Chat-Rand",
  `chat.html` setzt den Akzent beider Stimmen **links**. Korrigiert in `chat.md` (R-WP-29 + UC-WP-55)
  und `ui.md` (Look-Absatz: „Akzent beider Stimmen links"). Lehrregel: **Referenz vor der Regel** —
  stimmt die Regel nicht mit der Vorlage, ist die Regel die Fehlerquelle, solange die Vorlage gilt.
- **Der Farbakzent hat einen einzigen Besitzer der Breite**: `.chat-log .chat-line` setzt
  `border-left: 3px solid transparent`, die Stimme setzt nur `border-left-color`. Die
  `border-right: 3px solid var(--user)` der alten Nutzer-Zeile war deshalb doppelt gemalt (Kante +
  `padding-right` + `text-align: right`) — alles drei fiel, der Test prüft jetzt beide Seiten.
- **`hr` war nie zu viel**: `entriesOf` zeichnet die Trennlinie schon „nur beim Wechsel der Stimme"
  (`voice !== null && voice !== row.voice`) — kein Code-Fix für „genau ein hr" nötig. Probe
  (`&& voice !== row.voice` raus) ⇒ **genau** `roleChangeDrawsARule` rot; die Regel ist also bewiesen,
  nicht nur behauptet.
- **Negativ-Assertions brauchen eine Positiv-Seite im selben Test**: `not.toContain('border-right')`
  wäre durch „gar kein Akzent" grün geworden, deshalb prüft der umbenannte Test
  `userLineCarriesTheBlueAccentOnTheLeft` zusätzlich `border-left`, die 3px der Basisregel und den
  Token-Wert `#0969da`. Proben: `text-align: right` zurück ⇒ rot; `border-right: 3px solid` statt
  `border-left-color` ⇒ rot; 3px → 1px ⇒ rot; `background` an der user-Zeile ⇒
  `agentLineHasNoGrayBlock` rot; `text-align: center` an der Linien-Regel ⇒ ebenda rot.
- **Nach Inc 4: Backend 202/0/0** (unverändert — reines UI-/Doc-Inkrement), **UI 121 Tests / 15
  Dateien** (unverändert: ein Test umbenannt, Assertions ersetzt), `tsc -b` 0, oxlint 0/0 (57 Dateien),
  Doc-Lint **0 Findings** bei UC **66/66**, 233 Test-Ids. Marker bleiben bei Jon (R-WP-29/UC-WP-55 sind
  von ihm zu flippen).
