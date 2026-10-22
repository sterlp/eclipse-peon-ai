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
- **Never widen scope silently.** Fixes outside the released plan — even correct ones, even
  one-liners — get reported to the PO first. TABU lists in a plan are binding.
- Elegant, expressive modern Java (records, pattern matching, switch expressions, Lombok).
- Skill-Footer beantworten; falschen Skill im selben Turn fixen.
