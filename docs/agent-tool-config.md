---
idPrefix: ATC
---

# Agent-Tool-Config — Tool-Sichtbarkeit als User-Config

> **Status:** ❌ specified (2026-10-13) ·
> **Fachdoc-Mechanismus:** [agent-tool-filter.md](agent-tool-filter.md) (R-TF-* bleibt Owner der
> Filter-Mechanik) · **verwandt:** [custom-agents-design.md](custom-agents-design.md) ·
> [advanced-configuration.md](advanced-configuration.md) · [scaffold-agent.md](scaffold-agent.md)
>
> **Ziel (Paul, 2026-10-13):** Der User kann pro Agent konfigurieren, welche Tools aktiv sind —
> wie bei Custom Agents. Ein Mechanismus für alle Agententypen: die **Tool Allowlist** (CSV) wird
> von `ToolPolicy` entschieden, egal ob sie aus dem Frontmatter (`tools:`) oder aus dem UI kommt.
> Hauptmotivation: selten genutzte Tools (z. B. die `debugJava*`-Debug-Tools) ausschließen, um
> Kontext zu sparen. ADR: [0065-tool-policy-negation](adr/0065-tool-policy-negation.md).

## Begriffe (→ [Glossar](glossary.md))

- **Tool Allowlist** — die CSV-Datenliste eines Agenten, die entscheidet, welche Tools aktiv sind.
  Quelle je Typ: Built-in = UI-Feld (Store `llm.agent.<id>.tools`, Dev = Basic-Seite); Custom =
  `tools:` im AGENT.md-Frontmatter. Matched via `ToolPolicy`.
- **Negation** — ein `!`-Präfix auf einem Allowlist-Token: entfernt nach der Allow-Bewertung alle
  durch das Präfix gematchten Tools wieder. Beispiel: `*, !debugJava*` = alles außer Debug.

## Business Rules

### R-ATC-1 ❌ — Eine Semantik für alle Agententypen (ToolPolicy als einziger Matcher)

- Die Tool-Entscheidung läuft für **jeden** Agententyp über dieselbe Logik: Built-in-Agenten werten
  ihre UI-Tool-Allowlist mit **exakt demselben** Matcher aus wie Custom Agents ihr `tools:`-Frontmatter
  — kein Sonderverhalten für Built-ins (Paul: „keine extra Logik für die Built-in-Agenten").
- **Prefix-Match bleibt:** `eclipse` aktiviert alle `eclipse*`-Tools, `*` alle Tools, ein exakter
  Name nur dieses Tool — unverändert zu Custom Agents heute (R-DEF: hier kein Inheritance-Bedarf).
- GIVEN Built-in-Agent mit `eclipse` WHEN Filter THEN nur Tools mit Präfix `eclipse` aktiv
- GIVEN Built-in-Agent mit leerem Feld WHEN Filter THEN alle Tools (absent = all, wie Custom)
- GIVEN Custom Agent mit `tools:` WHEN Filter THEN Verhalten unverändert zum heutigen Stand
  (Regression über die bestehende `ToolPolicyTest`-/`CustomAgentServiceTest`-Abdeckung)

### R-ATC-2 ❌ — Negation `!prefix` (allow-then-deny; nur Negationen = alles minus Ausschluss)

- Ein Token mit `!`-Präfix entfernt Tools: erst entscheidet die Allowlist (Prefix/`*`/exakt) wie
  heute, **danach** schneiden alle `!`-Tokens ihre Präfix-Matches aus der Allow-Menge heraus.
  `*, !debugJava*` = alles außer den Debug-Tools; `eclipse, !debugJava*` = alle Eclipse-Tools ohne
  Debug.
- **Ausschluss gewinnt** — ein Tool, das von einem `!`-Token gematcht wird, ist aus, auch wenn ein
  Allow-Token es auch matcht (Reihenfolge: erst allow, dann deny). Keine Misch-Semantik pro Token.
- **Nur Negationen = valide (Paul, 2026-10-13):** hat die Allowlist **kein** Allow-Token (nur
  `!`-Tokens), ist die Allow-Menge „alle Tools" (wie absent, R-ATC-3) und die Negationen ziehen
  ab — `!debugJava*` allein = alles außer Debug. Kein „ungültig", kein absent-Fallback; der
  Preview zeigt die aufgelöste Menge (R-ATC-8), damit der User sieht, was übrig bleibt.
- GIVEN Agent mit `*, !debugJava*` WHEN `debugJavaSetBreakpoint` THEN unsichtbar; WHEN `eclipseGrepFiles`
  THEN sichtbar
- GIVEN Agent mit `eclipse, !debugJava*` WHEN Filter THEN `eclipse*` außer `debugJava*`
- GIVEN Agent mit `!debugJava*` allein WHEN Filter THEN alle Tools außer `debugJava*` (inkl. MCP,
  R-ATC-6) — valide, kein Fehler
- GIVEN ToolPolicy-Test für Negation THEN echte Tool-Namen aus der Tool-Matrix (R-TF-4-Muster),
  keine erfundenen

### R-ATC-3 ❌ — Absent vs. leer (Glossar: „unset" gilt nicht hier)

- **Absent/Feld leer** = kein Filter, alle Tools aktiv (wie Custom Agents absent `tools:`).
  Das ist der Default und der Weg, den Filter komplett auszuschalten.
- **Explizit leer** gibt es in der CSV nicht: eine gespeicherte leere Liste/leere Tokens werden
  beim Laden zu absent normalisiert (Whitespace-only/`,`-only Tokens fliegen raus) — die
  „alle aus"-Situation ist bewusst NICHT vorgesehen: ein Agent ohne Tools ist sinnlos (LLM kann
  nicht agieren). Decision (Paul, 2026-10-13): keine „alle aus"-Semantik im Feature.
- GIVEN Agent-Config ohne Tool-Feld WHEN Filter THEN alle Tools aktiv
- GIVEN CSV mit nur Whitespace/Trennern WHEN geladen THEN = absent (kein „alles aus")
- GIVEN CSV mit `read` WHEN Filter THEN nur `read*`-Tools aktiv (Prefix-Regel R-ATC-1)

### R-ATC-4 ❌ — Config-Slots & Inheritance: KEINE (bewusst)

- **Dev (Basic-Seite):** Feld „Tools" auf der Basic-Page, Store-Key `llm.agent.dev.tools` — gilt
  **nur für den Dev-Agent**, **keine Vererbung** an die anderen Slots (Paul, 2026-10-13; bewusst
  anders als die Model-Config, R-DEF).
- **Advanced-Seite:** Feld je Agent-Slot (Plan/Compact/PO/…) im jeweiligen Agent-Bereich.
- **Custom Agents:** keine UI, Quelle bleibt das Frontmatter `tools:` — gleiche Semantik, kein
  Write-Back (Paul, 2026-10-13).
- **Scaffold-Doku:** [scaffold-agent.md](scaffold-agent.md) R8 wird erweitert: Scaffold kennt
  das `!`-Negation-Präfix und kann es beim Generieren von AGENT.md-Frontmatter setzen (WEIL: der
  Scaffold-Agent schreibt die `tools:`-Felder, die Negation könnte sonst nie entstehen).
- GIVEN Dev-Slot mit `*, !debugJava*` WHEN Plan/PO/Compact-Filter THEN unverändert alle Tools
  (keine Inheritance)
- GIVEN Plan-Slot mit eigenem Wert WHEN Filter THEN nur sein Wert zählt
- GIVEN Custom Agent WHEN UI THEN kein Feld (Phase 1), Frontmatter bleibt Quelle

### R-ATC-5 ❌ — Klassen-Filter bleibt UND-verknüpft (UI kann nur einschränken)

- Die Tool-Entscheidung ist die **Schnittmenge** aus Klassen-Filter (`getToolFilter()`:
  `isEditTool`-Split, Da-Dok-Shell-Whitelist, Sniffa-plan*-Ausschluss) und Name-Filter
  (Tool Allowlist). Der Klassen-Filter bleibt **immer** unberührt — Built-in-Schutz (R-TF-1/2)
  kann vom User nicht wegkonfiguriert werden.
- **Fazit für den User:** das UI-Feld kann nur **weiter einschränken**, niemals aufmachen.
  Ein Agent mit Built-in-Filter + leerer Allowlist verhält sich exakt wie heute.
- GIVEN Da Dok (Klassen-Filter ohne Write-Tools) WHEN Allowlist `*` THEN trotzdem keine
  Write-Tools (Schnittmenge)
- GIVEN Da Sniffa WHEN Allowlist `*` THEN weiterhin plan*-frei
- GIVEN Mek mit Allowlist `!debugJava*` WHEN Filter THEN Debug-Tools aus, Rest unverändert —
  der Klassen-Filter greift zusätzlich (Schnittmenge)

### R-ATC-6 ❌ — Name-Filter gilt für ALLE Tools (Built-in + MCP)

- Der Allowlist-Name-Filter (Tool Allowlist) wird auf **alle** Tool-Namen angewendet — die
  Built-in-Tool-Specs (`getToolFilter`-Seite) **und** die MCP-Tools (`getToolNameFilter`-Seite).
  Ein Filter, der nur MCP filtert, wäre die halbe Wahrheit („tool must never lie"); der
  Preview-Button (R-ATC-8) zeigt die aufgelöste Menge über beide Quellen.
- GIVEN Agent mit Allowlist `eclipse` WHEN toolSpecifications THEN alle Tools, die mit `eclipse`
  beginnen, sind aktiv — Built-in wie MCP (falls ein MCP-Tool `eclipse…` heißt)
- GIVEN MCP-Tool `mcp__docs__search_docs` WHEN Allowlist `mcp__docs` THEN aktiv, sonst unsichtbar
- GIVEN MCP-Tool-Name WHEN Filter THEN derselbe Matcher wie Built-in (R-ATC-1, ein Mechanismus)

### R-ATC-7 ❌ — Where to Store & Wiring

- Store-Keys: `llm.agent.<id>.tools` je Slot (`llm.agent.dev.tools` für Dev/Basic); kein Eintrag =
  absent (alle Tools). Der Wert wird als **roher String** gespeichert (User-Order), geparst beim
  Agent-Bau, Clean Break: Key entfernen = Filter weg, keine Migration.
- Agent-Instanzen werden bei Config-Änderung neu gebaut (IST, Reload-Mechanik) — der Filter hängt
  am Agenten, nicht am Service.
- GIVEN Agent-Slot `plan` mit Wert WHEN PeonAiService baut Agent THEN ToolPolicy prüft gegen den
  gespeicherten String
- GIVEN Key entfernt WHEN Reload THEN absent → alle Tools (Clean Break, kein stale State)

### R-ATC-8 ❌ — „Aktive Tools"-Preview (Button im UI)

- Neben dem Feld ein **Preview-Button**: zeigt die **aufgelöste, aktive Tool-Menge** des Agenten —
  Built-in-Specs UND MCP-Namen, bereits durch Klassen-Filter UND Allowlist geschneidet, als
  read-only Liste (Dialog). **WEIL:** ohne Vorschau rät der User im Dunkeln, was `eclipse` wirklich
  einschaltet — die Prefix-Semantik ist nicht selbsterklärend (auch die Semantik „nur Negationen =
  alles minus Ausschluss" sieht man hier).
- Der Preview konsumiert dieselbe Resolve-Methode, die auch der Filter benutzt (`isToolActive` /
  `isMcpToolActive` am Agenten) — keine zweite Filter-Logik (ein Mechanismus, R-ATC-1).
- GIVEN Agent-Slot WHEN Preview-Button THEN Dialog listet aktive Tools (alle Quellen,
  Schnittmenge), read-only
- GIVEN Allowlist `eclipse` WHEN Preview THEN Liste enthält `eclipse*`-Tools, keine `disk*`-,
  keine `debugJava*` (falls negiert)
- GIVEN Da Dok WHEN Preview THEN Liste zeigt die Klassen-Filter-Schnittmenge (Shell drin,
  Write-Tools draußen — auch bei `*`)

### R-ATC-9 ❌ — `!` allein: ehrlich melden, nicht still fressen

- Ein `!` ohne Präfix (`!` allein oder `!` mit nur Whitespace) ist ein **Konfigurationsfehler**:
  der Preview meldet ihn; der Filter behandelt ihn als no-op (kein Crash, kein stiller Ausschluss
  fremder Tools). Alle anderen Tokens sind gültig (auch Umlauts-Präfixe — Prefix-Match ist String).
- GIVEN Allowlist mit `!` allein WHEN Preview THEN Meldung „leere Negation"
- GIVEN Allowlist mit `!` allein WHEN Filter THEN no-op (kein Tool aus, kein Fehler)
- GIVEN Allowlist mit `!debugJava*` THEN gültig (Präfix `debugJava*`)

## Use-Case-Definitionen

#### UC-ATC-1 — Built-in-Agent mit Allowlist, klassen-filterfrei
GIVEN Dev-Agent mit `eclipse` WHEN `toolSpecifications` THEN nur `eclipse*`-Tools aktiv; `disk*`
und Shell sind unsichtbar.

#### UC-ATC-2 — Built-in-Agent mit Negation
GIVEN Dev-Agent mit `*, !debugJava*` WHEN `toolSpecifications` THEN alle Tools außer `debugJava*`.

#### UC-ATC-3 — Absent = alles (Default)
GIVEN Agent ohne Tool-Feld WHEN `toolSpecifications` THEN alle Tools (minus Klassen-Filter bei
Built-ins).

#### UC-ATC-4 — Da Dok mit `*` bleibt klassen-gefiltert
GIVEN Da Dok mit `*` WHEN `toolSpecifications` THEN Shell + Review-Tools sichtbar, Write-Tools
unsichtbar (R-TF-1-Schnittmenge).

#### UC-ATC-5 — Dev-Filter erbt nicht
GIVEN Dev-Slot `*, !debugJava*` WHEN Plan-Agent baut THEN alle Tools (keine Inheritance, R-ATC-4).

#### UC-ATC-6 — Custom Agent unverändert
GIVEN Custom Agent mit `tools: grep, read_` WHEN Filter THEN Verhalten identisch zum heutigen
IST (R-ATC-1-Regression).

#### UC-ATC-7 — Preview zeigt aufgelöste Menge
GIVEN Plan-Agent mit `eclipse, !debugJava*` WHEN Preview-Button THEN Dialog zeigt aktive Tools
(alle `eclipse*` ohne Debug), read-only.

## BDD-Test-Mapping

| Regel | Test |
|---|---|
| R-ATC-1 | `ToolPolicyTest` — Built-in-/Custom-Gleichheit (gleicher Matcher) |
| R-ATC-2 | `ToolPolicyTest` — Negation: allow-then-deny, `*, !debugJava*`, nur-Negationen-valide |
| R-ATC-3 | `ToolPolicyTest` — absent/leer-Normalisierung |
| R-ATC-4 | `AgentToolConfigTest` (core) — Slots unabhängig, Dev erbt nicht |
| R-ATC-5 | `PeonAiServiceTest` — Da-Dok/Sniffa-Schnittmenge bei `*` |
| R-ATC-6 | `AgentToolConfigTest` — MCP-Name-Filter über Allowlist |
| R-ATC-7 | `AgentToolConfigTest` — Store-Key parse/reload, Clean Break |
| R-ATC-8 | manuell (User-Smoke) — Dialog-Inhalt |
| R-ATC-9 | `ToolPolicyTest` — `!` allein = no-op + Preview-Meldung |

## Out of Scope

- **Custom-Agent-UI** (Phase 1: kein Feld, kein Write-Back) — Frontmatter bleibt Quelle; eigene
  Story, falls gewünscht.
- **Vererbung des Dev-Tool-Filters** an andere Slots — explizit abgelehnt (R-ATC-4).
- **„Alle aus"-Semantik** — bewusst nicht vorgesehen (R-ATC-3).
- MCP-Server-Config/Filter unverändert ([mcp.md](mcp.md)).
- Async-Agent-Interface (paralleler Umbau von Paul, siehe Chat) — kein Konflikt; der Filter ist
  Daten am Agenten, falls der Umbau die Agent-Konstruktion anfasst, plant Jon nach.
