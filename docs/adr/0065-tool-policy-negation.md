# ADR-0065 — ToolPolicy als einziger Matcher für alle Agententypen + Negation

**Status:** Accepted (2026-10-13)

**Context:** Built-in-Agenten (Da Thinka/Da Mek/Da Dok/Da Sniffa…) bekommen ihre Tool-Sichtbarkeit
heute aus dem Klassen-Filter (`getToolFilter()`: `isEditTool`-Split, Whitelist-Exceptions) und dem
Name-Filter (nur MCP). Custom Agents haben `tools:` im AGENT.md-Frontmatter, entschieden über
`ToolPolicy.enables` (Prefix/`*`, absent = alle). Paul will Tool-Sichtbarkeit pro Agent als
User-Config im UI (Hauptmotivation: selten genutzte Tools wie `debugJava*` ausschließen) — ohne
extra Logik für Built-ins. Reine Prefix-Allowlist kann aber nicht ausschließen: „alles außer
Debug" hieße, alle anderen Tools aufzuzählen.

**Decision:**
1. **Ein Matcher:** Die Tool-Entscheidung läuft für alle Agententypen über `ToolPolicy` — die UI-
   Allowlist der Built-ins verhält sich exakt wie das Custom-Frontmatter `tools:`. Kein
   Sonderverhalten, keine zweite Parse-/Match-Logik.
2. **Negation:** `ToolPolicy` bekommt ein `!`-Präfix: erst Allow-Bewertung (Prefix/`*`/exakt,
   unverändert), **danach** schneiden alle `!`-Tokens ihre Präfix-Matches aus. Ausschluss gewinnt
   (`*, !debugJava*` = alles außer Debug). **Nur Negationen = valide** (Paul 2026-10-13): fehlt
   jedes Allow-Token, ist die Allow-Menge „alle Tools" und die Negationen ziehen ab —
   `!debugJava*` allein = alles außer Debug (kein ungültig-Fallback). Preview zeigt die
   aufgelöste Menge; `!` allein = no-op + Preview-Meldung.
3. **Schnittmenge mit dem Klassen-Filter:** der Klassen-Filter bleibt immer unberührt — das UI-Feld
   kann nur weiter einschränken, nie aufmachen (Built-in-Schutz R-TF-1/2 bleibt).
4. **Keine Inheritance:** Dev-Tool-Feld (Basic-Seite) gilt nur für Dev; kein Vererbungskonzept für
   Tools (bewusst anders als Model-Config).
5. **Kein Custom-UI, kein Write-Back** (Phase 1): Custom Agents bleiben Frontmatter-first.

**Consequences:**
- `ToolPolicy` wird der eine Choke-Point der Tool-Sichtbarkeit; Allow-then-Deny ist die
  dokumentierte Reihenfolge — keine Token-Mix-Semantik pro Eintrag.
- Allow-Semantik ist damit dreistufig: **kein Allow-Token = alle** → Allow-Tokens wählen vor →
  `!`-Tokens schneiden aus. Absent und „nur Negationen" unterscheiden sich nur darin, dass
  Negationen nach dem Alles-Start noch abziehen.
- Scaffold-Agent kennt die Negation (kann `!` ins Frontmatter schreiben), sonst könnte sie
  Custom-Agent-seitig nie entstehen.
- Name-Filter wird von „nur MCP" zu „alle Tools" erweitert — der Preview (R-ATC-8) konsumiert
  dieselbe Resolve-Methode (`isToolActive`/`isMcpToolActive`), keine zweite Logik.
- „Alle aus" ist bewusst keine Semantik (sinnloser Agent); absent = alle Tools bleibt der Default.
- Clean Break: Key weg = Filter weg, keine Migration.
