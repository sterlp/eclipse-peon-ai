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
- **Docs-ID-Marker am Test: aus dem Feature-Doc kopieren, nie erfinden** (hit 2026-10-17, codegraph
  inc-19/20: Da-Dok REJECTED — 12 `// UC-CG-*`-Marker in Tests, ohne dass die UC-IDs im Doc
  existierten; die Feature-Regeln heißen dort `R-CG-*`). R-X-n-Regeln tragen keine UC-X-n-
  Überschriften — die Marker-Form (pure ID-Zeile, kein Zusatztext) muss zur Doc-Form passen.
  Sobald ein Plan ID-Marker vorsieht, gehört `lintDocsAndTests` in das Inkrement-Gate:
  VERWAIST/UNBELEGT_ERLEDIGT blockiert, nicht erst der Review-Lauf.
  Invokation: `idPattern` ist **UC-only** (`UC-[A-Z]+-\d+[a-z]?`) — Regel-Marker `R-*` sind keine
  UC-IDs; ein Pattern, das auch `R-*` matcht, liefert eine Masse Schein-VERWAIST statt der kleinen
  Baseline aus UNBELEGT- und MANUELL-Findings. `docRoots` immer ALLE DREI Roots explizit setzen (`docs`,
  `org.sterl.llmpeon.core/docs`, `org.sterl.llmpeon/docs`) — fehlt einer, liefert der Linter
  Schein-VERWAIST für dessen Scope (hit 2026-10-23: 84 Schein-VERWAIST ohne `org.sterl.llmpeon/docs`).
  Melden: die echte `findings: N`-Zeile aus dem Output — nie die `Scanned: N doc file(s)`-Anzahl als
  „Findings" (hit 2026-10-24, R-OD-8 F3).
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

## aifactory (Spring-Boot-Reaktor) — Build-Fakten (inc-1, 2026-10-25)

- **`mvn -f aifactory/pom.xml …` braucht pnpm auf dem PATH** (factory-ui fährt pnpm-install/-build via
  exec-maven-plugin): `export PATH=~/.nvm/versions/node/v22.12.0/bin:$PATH`, sonst `factory-ui FAILURE:
  Cannot run program "pnpm"` vor jedem Backend-Modul.
- **Kein `-parameters` im aifactory-Build** (Parent ist `llmpeon-parent`, nicht
  `spring-boot-starter-parent`): `@PathVariable`/`@RequestParam` ohne expliziten Namen schlagen in
  Boot 4 fehl — der Fehler ist eine `IllegalArgumentException` und landet damit im 400-Handler, nicht
  als 500. Immer Namen explizit schreiben (`@PathVariable("id")`) oder dem aifactory-pom
  `<parameters>true</parameters>` geben.
- **`mvn clean`, wenn ein Test `NoClassDefFoundError: <SimpleTypeName>` (ohne Paket) meldet**: Eclipse
  JDT schreibt nach `target/classes` und hinterlässt bei Auflösungsfehlern Class-Files mit
  unqualifizierten Typ-Namen; der Maven-Incremental-Compile hält sie für aktuell.
- **Boot 4 behandelt `ResponseStatusException` vor dem `@ControllerAdvice`** → eigener Exception-Typ
  (z. B. `ProjectNotFoundException`) + `@ExceptionHandler`, sonst wird aus dem 404 ein 500/400.
- **UI-Test-Setup:** jsdom auf 26.1.0 gepinnt (jsdom 30 verlangt Node ≥22.22.2 → undici-Crash
  „reading 'bind'"); `vite.config.ts` importiert `defineConfig` aus `vitest/config`, sonst findet
  `tsc -b` das `test`-Feld nicht.
- **`gitea.sterl.co` löst auf dieser Maschine nicht auf** (DNS; github/maven-central gehen) —
  Clone-Beweise gegen das interne Gitea nur mit aktivem VPN, sonst gleichwertigen öffentlichen Repo
  benutzen und im Report ausweisen.

