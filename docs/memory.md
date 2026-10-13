# Session-Stand — 2026-10-13 (Merge + Docs-Cleanup ✅ · Agent-Tool-Config ❌ specified)

> Achtung: docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts
> im Chat, Jon schreibt. (Da Mek hatte 2026-09-27 memory.md überschrieben — nicht wiederholen.)

## AKTUELL 2026-10-13 (2): Neue Story specified — Agent-Tool-Config

- **Agent-Tool-Config ❌ specified** ([agent-tool-config.md](agent-tool-config.md), [ADR-0065](adr/0065-tool-policy-negation.md)): Tool Allowlist (CSV) pro Agent, ein Matcher (`ToolPolicy`) für Built-ins (UI) + Custom (Frontmatter), Negation `!` (allow-then-deny; **nur Negationen = alle minus Ausschluss**, Paul 2026-10-13), keine Inheritance (Dev-Filter bleibt Dev), Schnittmenge mit Klassen-Filter, Preview-Button, Scaffold R8 kennt `!`. Release-Notes komplett neu geschrieben (`release-notes-2026-10.md`) inkl. geplantem Da-Dok-Eigen-Slot (als „Geplant", noch nicht gebaut — Migration-Hinweis: PLAN-Werte einmal kopieren).
- **Paul baut parallel:** Async-Interface für Agenten — kein Konflikt (Filter = Daten am Agenten); falls die Agent-Konstruktion angefasst wird, plant Jon das UI-Feld nach dem Umbau.
- **Offene Design-Reste:** keine — SOLL interpretationsfrei (R-ATC-1…9). Custom-Agent-UI/Write-Back bewusst Out of Scope (Phase 1); „alle aus"-Semantik bewusst abgelehnt (R-ATC-3).
- **Nächster Schritt für diese Story:** planWithPlanAgent (Inkremente: 1 ToolPolicy-Negation core, 2 Store+Agent-Wiring, 3 UI-Felder Basic/Advanced, 4 Preview-Button, 5 Scaffold-Prompt R8) — erst nach Pauls Freigabe.

## AKTUELL 2026-10-13: Merge + Docs-Cleanup ✅

- **Merge main INTO `bug/grep-empty-payload-test`:** `d5c87548` (Eltern `0ba9bcc7` + `2968ede2`), keine Konflikte. main brachte: **Fix #142** (`includeAllDependencies` raus, jakarta.annotation Range, Target 2026-09→**2025-12**, Recovery-Guide + `clear-stale-platform-bundles.ps1`, Tags 2.12.4/2.12.5). **Nicht gepusht** — Push wartet auf den Docs-Commit.
- **Docs-Cleanup (Jon, ungepushed):** index.md-Duplikate beseitigt (Tool-Result-Size), „Offene User-Entscheidungen" → `release-2026-09-06` als erledigt vermerkt; open-points.md: test_project-Fixture konsolidiert, #142/API-Fehler nach resolved-points; resolved-points.md: +3 Zeilen. **Neu heute:** agent-tool-config.md + ADR-0065 + Glossar (Tool Allowlist, Negation) + Scaffold R8 + index.md-Zeile.
- **Offen aus dem letzten Stand (unverändert):** Paul-Smoke R-AEM + R-TD-5 · `planImplemented` (Archiv) · Merge branch→main = Paul · Docs-Commit + Push (wird jetzt nachgeholt).

## Shell-onTool-Laufzeit + Overflow-Batch (2026-09-30/10-01, Branch `bug/grep-empty-payload-test`)

- R-TD-5 ✅ `bc975abd` (Shell-onTool-Dauer, stopped-Pfad via stats-Move, Paul-Smoke offen).
- Overflow-Batch ✅: `0be9a4c5` R-RS · `d7cf6578` R-OD-6 · `4f7cab05` R-CC-17 · `53e54c5e` R-AEM · Delta `f77cbef0` (R-RS-Nachzug reloadConfig + webFetch; findImplementations = toter Code).
- Grep/Replace-Guards ✅ (`9c709187`, `ef140197`, `7edf7afc`), replaceLines-Fix `e97ef512`, Descriptions `0be24315`. Letzter Commit vor Merge: `bc975abd` (gepusht).

## Nächste Schritte

1. Da Mek: docs/** committen + pushen (Merge `d5c87548` + Cleanup + agent-tool-config-Docs).
2. **Paul-Smoke:** R-AEM (terminale Fehler → 1× History, Abbruch → keine Insertion) + R-TD-5 (`… (4s, 19:54) (1234 chars)` im Terminal-Output).
3. **Merge branch→main** = Pauls Entscheidung. Danach `planImplemented` für offene Pläne.
4. **Danach (Paul-Order): Context-Overflow/ContentProvider Bug-Zyklus** — Thinka 954k (per-Request-Injektion, nicht Agent-Memory), Mek 431005 vs. n_ctx 170240, webFetch 500k. Evidenz: open-points.md ❓ auto-compact. Logs nur mit searchAgent lesen (`.metadata/.log`, GROSS).
5. **Agent-Tool-Config-Bau:** Plan erstellen (5 Inkremente, s. o.) — nachdem Pauls Async-Interface-Umbau durch ist, sonst Migration doppelt.

## Backlog (Auswahl)

AGENTS-Trim (wartet Paul-GO, inkl. totem „komponenten-architektur"-Link AGENTS.md:43) · TrimService-Story (⏳, nächster Architecture-&-Bug-Sprint) · R-CC-7 + ApiRetry-Fehlerklassen-Tabelle (❓, Evidence 5×) · Header-State-Leak · Think-BDD-Lücken (a)–(g) · per-Agent-Provider-Override (❓) · Linter ☠️-Status (❓) · Homepage advanced-configuration.md:53 think-Werte-Mismatch · Homepage peon-po.md ohne Da Dok · Compact-Button ohne Monitor (❓) · Auto-Compact bei Overflow (❓) · Custom-Agent-UI mit Write-Back (Phase 2, agent-tool-config Out of Scope).
