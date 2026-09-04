# Session-Stand — Release-Stabilisierung **abgeschlossen** (2026-09-03/04)

**Branch `new-config`, NICHTS committet.** Dev hatte in allen Zyklen Commit-Verbot; der User
committet und released selbst.

## Ergebnis

| | Start | Jetzt |
|---|---|---|
| Plugin-Build | ❌ Compile-Fehler | ✅ |
| Core (Surefire = **Ground Truth**) | 519 | **566**, 0 rot, 0 skipped |
| Plugin (Surefire) | 68 gelaufen, 8 rot, 15 skipped | **176**, 0 rot, 4 skipped (nur SWT ohne Display) |
| `mvn clean verify` | headless rot | **SUCCESS** |
| Homepage-Build | — | ✅ |

**Backlog leer** — keine ❌ specified Story mehr offen.

Gebaut in 9 Zyklen: Test-Setup · 2a Read/Grep · 2b-1 Suche · 2b-2 Grep · 2b-3 Console +
Refresh-Ziel · 3a PO-Model-Slot · 3b Temperature pro Agent · Flake-Fix · R6b aus dem E2E-Test.
Details stehen dort, wo sie hingehören — in den Feature-Docs und ADRs, nicht hier:

- [eclipse-read-tools.md](eclipse-read-tools.md) — R1–R7 komplett ✅
- [test-setup.md](test-setup.md) — R1–R5 ✅
- [advanced-configuration.md](advanced-configuration.md) — R-PO1…R-PO4, R-T1…R-T5 ✅
- ADRs [0035](adr/0035-grep-regex-first-literal-fallback.md) ·
  [0036](adr/0036-po-own-model-slot.md) · [0037](adr/0037-dedicated-test-fixture-project.md) ·
  [0038](adr/0038-refresh-on-empty-search.md) · [0039](adr/0039-temperature-body-precedence.md);
  [0023](adr/0023-po-model-plan-slot.md) auf *Superseded* gesetzt.

## Offen für den User (vor dem Release)

1. **Zwei manuelle Prüfungen** — bewusst nicht automatisiert, weil ein Render-Test ohne Display
   skippen würde:
   - Temperature-Feld steht in **allen fünf** Agent-Sections zwischen Think und Extra body,
     auch bei Ollama (kein Provider-Gate).
   - Advanced-Seite zeigt fünf Sections in der Reihenfolge **PO, Dev, Plan, Search, Compact**;
     PO-Modell setzen → OK → Seite neu öffnen → Wert steht.
2. **Verhaltensänderung kommunizieren:** Search und Compact senden ohne eigenen
   Temperature-Wert **nichts** mehr (vorher implizit 0.3 / 0.2). Auf der Homepage dokumentiert.
3. Modell-List-Fetch + Refresh bei allen Agenten; Cache-Snippets gegen das eigene Gateway.
4. Dann **committen + releasen**.

## Nachlauf 2026-09-04 (nach dem Release-Zyklus)

- **Plan-Archiv aufgelöst:** alle 13 `peon-plan/overview-done-*.md` gesichtet, dauerhaft Wertvolles
  in die Docs überführt, Dateien gelöscht. Neu: [ADR-0040](adr/0040-model-list-single-flight-secret-masking.md)
  (Single-Flight + Secret-Masking); Ergänzungen in `test-setup.md` (Mock-Wire-Formate, bewusste
  Abdeckungs-Entscheidung), `caching.md` R3 (Herkunft der Snippets), ADR-0039 (langchain4j-Merge-
  Semantik), `AGENTS-DEV.md` (CancellationException-Trap, TABU-Regel-Ursprung).
- **ADR-0021 repariert** (Copy-Paste-Schaden im Context) und ergänzt: der 70-%-Trigger misst
  Füllstand, nicht Relevanz — Themenwechsel bleibt Jons manuelle Entscheidung (compact vs. reset).
- **Prompts überarbeitet** (`po.txt` / `po-delegation.txt`, Produkt-Assets, nicht Doc-Baum):
  index.md = Karte statt Protokoll · Glossar-Regel · Ownership entwirrt (adr/ = Jon allein,
  Feature-Docs = gemeinsam) · zwei Gedächtnisse ohne Dubletten · aktives Beraten + Konflikte immer
  gemeinsam lösen · Review prüft jetzt **drei** Seiten (Plan↔Code, Docs↔Code, Docs↔Plan) ·
  Delta-Plan braucht Abnahme · ❌→✅ erst nach Review (war fälschlich bei der Plan-Abnahme) ·
  Mutations-Check als Jons Ermessensentscheidung statt Dev-Dauerpflicht · Night-Cycle als
  Drei-Wege-Sortierung (klar → bauen · ableitbar → entscheiden + ⏳ · echt offen → skip + ❓),
  Präzisierungen ohne Bedeutungsänderung dürfen nachts direkt ins Feature-Doc.
- **`PoDelegateTool` geglättet** (Dev, nicht committet): einheitliches compact/reset-Beschreibungspaar,
  Compact liefert nur noch eine Quittung statt der ganzen Zusammenfassung, UI und Tool-Result teilen
  sich `dispatchStats(...)` (Kontext + Dauer + HH:mm), compact/reset melden knapp an die UI.
  `PoDelegateToolTest` 7/0.

## Nächste Session — Startpunkte

- **Eine Rückversicherung steht aus** (⏳ in [open-points.md](open-points.md)): unbegrenzte
  Query-Caches (`SearchQuery.CACHE`, `RegexUtils.GLOB_CACHE`) — vorerst bewusst belassen,
  LRU bei Bedarf.
- Weitere ❓ in [open-points.md](open-points.md): Glossar eager laden? · `buildWithDev` soll
  Da Mek vorher compacten · `eclipseWriteFile` schreibt immer UTF-8 (still korrumpierende
  Round-Trips) · PDE-Runner meldet Skips nicht separat · Smoke-Test-Kosmetik (2 Striche,
  Advanced-Config-Scrollen, Dropdown-L&F).
- Aus `issues/fact-issues.md`: Punkt 3 (CancellationException-Stacktrace als Error geloggt),
  Punkt 5 (GitHub-Actions Node-20-Deprecation).
- Descoped, eigene Story nach dem Release: Custom-Dropdown-Umbau
  (`DropdownButton`/`DropdownPopup`/… liegen unbenutzt + kompilierbar) →
  [resolved-points.md](resolved-points.md).

## Wo die Lernings dieser Session leben

`memory.md` ist **nur** Session-Zwischenstand. Dauerhaftes wurde verankert:

| Wohin | Was |
|---|---|
| [AGENTS.md](../AGENTS.md) | Tool-Ehrlichkeit / Falsch-Negativ · ein Verhalten eine Implementierung · Clean Break statt Migration · „leer = unset" · Surefire = Ground Truth · Verträge lesen statt raten · Report don't route around |
| [AGENTS-DEV.md](../AGENTS-DEV.md) | *Test honesty* (Vakuum-Test-Muster, Falsifizierbarkeit je Pfad, Charakterisierungstests vorab deklarieren, kein stilles Scope-Widening) · *Repo-specific API traps* |
| [skills/eclipse-dpe/SKILL.md](../skills/eclipse-dpe/SKILL.md) | Test-Fixture & unattended PDE-Launches · Workspace-Suche/Grep („warum ‚not found' lügt") · Console-API in PDE-Tests · SWT-UI-Tests |
| memory*-Tools (global) | 6 Einträge, projektübergreifend gültig |
