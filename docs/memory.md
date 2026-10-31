# Open Ends / Memory — Session-Zwischenstand

> Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> Branch `web-factory-goon`, HEAD `0ca0b373` — **Mini-Zyklus 2026-10-31 komplett** (Inc 1–3 + Delta
> R-PT-5, Da-Dok-Review behandelt, Plan archiviert `overview-done-2026-10-31-10-36.md`).
> **Merge-Entscheidung (Squash-Default) liegt bei Paul.**

## Zyklus-Ergebnis (2026-10-31, Mini-Zyklus)

- **Inc 1** (`85422a43` + Warn-Assert `d38fce13`): AgentOrder-Fallback — matcht kein Agent irgendein
  Pattern → Default-Ordnung (Jon zuerst) + `log.warn`. R5 in `core/docs/agent-ordering.md`, Issue 2 🔒.
- **Inc 2** (`b7971400` + Restore-Test `0612e214`): `ActiveAgentPreference` (InstanceScope,
  `llm.activeAgent`) + `rememberActiveAgent` (einziger Schreiber, aus `setActiveAgent` UND
  `onHandoff`) + `restoreActiveAgent()` am Konstruktor-Ende; Restore automatisiert getestet.
  Doc `org.sterl.llmpeon/docs/active-agent-persistence.md`.
- **Inc 3** (`9c0e006f`): `NamedAgent` Clean-Break gelöscht (`uiName ≡ getName()`), core/plugin/
  aifactory, AgentsTool rein mechanisch, ADR-0025 unberührt. Ging durch Agent-Neustart
  (Serialization-Glitch) — Verifikations- statt Doppel-Bau.
- **Delta R-PT-5** (`d8cfdb6b`): Da-Dok-Review REJECTED mit BUG-1 (Restore-Preload hing am noch
  unbekannten Projekt → restaurierter Plan-Agent startete ohne planRef, Handoff fiel auf
  Memory-Fallback). Fix: `setProject` preloaded unbedingt (Guard-Reihenfolge Agent → planRef →
  hasPlan, kein Plan-I/O für Dev/Jon). Neue R-PT-5/UC-PT-5 in `plan-tool.md` ✅, R3-Zweitzeile in
  `active-agent-persistence.md`. Option (a) „Restore nach AIChatView" verworfen.
- **Gates final:** Core **1426/0/0/0** · Tycho **451/0/0/53** · Fabrik **107/0/0/0** ·
  lintDocsAndTests UC 49/49 definiert, UC-PT-5 belegt; 40 Findings vorbestehend in anderen Docs.
- **Skill `peon-build-test`** erweitert (Sektion „aifactory gate (separate reactor)"): aifactory ist
  eigener Reactor → nach Core-API-Änderung `mvn -o install -pl org.sterl.llmpeon.core` Pflicht und
  `clean` im aifactory-Run, sonst grün auf stale Klassen. Ledger-Zeile in skill-impact.md.
- Pauls eigener Commit `7d087322 cleanup` (AGENTS-Renames, docs/memory.md, AIChatView) wurde mit dem
  Push mitgenommen — **AGENTS*.md-Säuberung ist damit committed** (Punkt aus dem Reparatur-Zyklus).

## Proposal „Agent als Monitor-Quelle" — ⏳ GEPARKT (2026-10-31)

- v2 (Paul) von Da Dok ohne ADRs reviewt: **REJECTED** — 3 verifizierte Blocker (AsyncMonitor
  schluckt Streams — Hauptgewinn von keinem Inkrement geliefert; R7/R8 brechen origin-Vertrag
  Browser/History/authorTable; Inc-Reihenfolge nicht grün) + Doc-Konflikte (streaming-display R20,
  compact-lock UC-CT-14). Richter-Zusätze: `AiAgent.isTool()` existiert als Anker; echter Blocker
  für „Agents als Tools" ist die **Autorschaft** (authorTable per Tool-Name, generisches askAgent
  nicht attributierbar).
- **Alles festgehalten in `docs/agent-als-monitor-quelle.md` (⏳ geparkt, self-contained)** — inkl.
  Split-Empfehlung (① AsyncMonitor-Forward ② „Agenten als Tools"-Story mit Autorschafts-Kern
  ③ Quelle-vs-Stempel danach) und Q1–Q4. Paul überarbeitet das Proposal bei Wiederaufnahme selbst.
- **Übergabe für Pauls IDE-Wechsel zur Web Factory steht in `aifactory/docs/memory.md`**
  (uncommitted — „Paul committet selbst" laut Header dort).

- Da-Dok-Review (ohne ADRs) + Jons IST-Verifikation: **REJECTED** — Identität ist der Name
  (`AgentNames.java:51`), ~15 agentlose Emit-Stellen, R5↔R6-Widerspruch, Blast Radius 3× gemessen
  (36 Dateien/73 Monitor-Stellen/74 onChatResponse-Overrides/171 Overrides). Review-Dok:
  `peon-plan/cleaner-agent-pass-through-review.md` (inkl. Nachspann mit Pauls Entscheidung).
- **Pauls Entscheidungen („nur docs erstmal", kein Code):** ① Rolling-Block wechselt ins
  Search-Agent-Memory → **R-LSR-7 🚧** in `core/docs/search-agent-tool.md` (Search-Agent wird
  vollwertiger interner Agent, später Mitglied der AsyncAgentTool-Agentenliste — Entscheidung in
  `core/docs/async-agents.md`); ② stabile ID = Ordnername (Custom) / NAME-Konstante (Built-ins) →
  Sektion „Identität vs. Anzeigename" 🚧 in `org.sterl.llmpeon/docs/agenten-namen-im-chat.md`.
  Beide Stories im Backlog (❌/🚧).
- Jons Async-Antwort an Paul (Chat): Identität reist **pro Aufruf** (Stempel durch den Agent selbst,
  `AbstractAgent.call`/`ToolService.executeLoop`) — async-freundlich. Das „Durcheinander" liegt auf
  Monitor-Instanz-Ebene (geteilter `isCanceled`, single live-response-Slot, START/END-Pairing) und
  ist Teil der Async-Story, nicht des Identitäts-Mechanismus.
- Lint core+plugin: keine neuen Findings (UC-CT-3/5 UNBELEGT = bekannter manueller Smoke,
  UC-WP-* nur weil aifactory-Docs außerhalb der docRoots).

## Offen für Paul (je eine Frage / Bestätigung)

1. **Merge von `web-factory-goon`** (Squash-Default) — der Branch trägt jetzt Reparatur-Zyklus +
   Mini-Zyklus.
2. **Paul-Smoke `active-agent-persistence.md`** (4 Punkte): Neustart, `.prefs`-Flush, verschwundener
   Agent (R4), Handoff-Schreiben.
3. ❓: **Edit-Tool-Falle** (Tool-Defekt upstream melden oder Bedienungsregel?), manuelle Nachweise
   (Browser 8099, `test-chat.html`), 🔒-Reste ok?
4. Ork-/Peon-Altbestand ~35 SOLL-Docs (Inventar in open-points) — Massen-Rename braucht Paul
   (Glossar-Regel 6).
5. **Jon hat seine Design-Antwort zu Pauls AIChatView-Monitor-Label gegeben** (Name → Eclipse-Job-
   Label ist reine UI-Präsentation, kein zweiter Identity-Kanal neben OwnerMonitor/ADR-0078) — siehe
   Chat; optionaler Polish: `agent` statt `agentName`-String durchreichen.

## Backlog (nicht gestartet)

- **Aus Proposal-Review übernommen (Paul 2026-10-31, nur Docs bisher):** ① Search-Agent als echter
  interner Agent (R-LSR-7 🚧 — Rolling-Block ins Agent-Memory, Klärungen im Doc: Lebenszyklus/Scope,
  R3-„stateless", R-CC-15-Folge) · ② Rename-Sicherheit über stabile `id()` (🚧 in
  agenten-namen-im-chat.md).

- **P3:** SSE-Client-Kopien (5×) in Fabrik-Tests extrahieren — eigenes Gate.
- **P1-Variante:** Cancel-/Fehler-Pfad-Release ohne atomaren Queue-Blick — nächster Bug-Zyklus,
  test-first (⏳ open-points).
- Async-Shell-Tool (Paul-Prio) → async Agents; Merge-Check „geteilte disk-Tool-Instanzen" vorher.
- Bug-Triage-Reste: totes MCP-Tool in Spec-Liste, `PoDelegateTool.dispatch`-Catch, ApiRetry-
  Fehlerklassen, Header-State-Leak, Auto-Compact bei Context-Overflow, `org.sterl.llmpeon.test`
  Surefire-Basis.
- Neu aufgenommen (2026-10-29): „Kein `throw` ohne Message" · Warnungs-Aufräum-Zyklus (inkl. 5
  `@SuppressWarnings`-Dateien).
- Klein: `MutableClock` 7× → Fixture · `AbstractAgent.asyncCall` ohne Aufrufer · Lint-Baseline Core ·
  historische `*-plan.md` löschen? · `StringUtil.oneLine`-Konsolidierung · `effectiveUrl`-Schnitt.

## Notizen

- System-Prompts generisch; Scaffold-Prompts stehen auf `Scaffold` (Jon editiert System-Prompts
  selbst, Übersicht: `core/docs/system-prompts.md`).
- Commit: EXPLIZITE Pfadliste; Pauls WIP nie (`AGENTS*.md`, `peon-plan/**`, `README.md`,
  `ai-e2e-test/**`, `pauls-product-vision.md`, `bugs/**`).
- Lint: `docRoots` weglassen = Auto-Discovery (R-DL-26); `findings:`-Zeile melden, nie `Scanned:`.
  `lintDocsAndTests` mit `idPattern` **mit** `[a-z]?` — `UC-PT-2a` kollabierte sonst auf `UC-PT-2`
  (Da-Dok LOW-4, geklärt).
- Da-Dok-Empfehlungen immer am IST verifizieren — diesmal: BUG-1 bestätigt, seine Reco (a) aber
  verworfen (R2/Doc bricht); (b) nach Da-Thinka-Sparring mit Guard-Reihenfolge + Test-Fix.
