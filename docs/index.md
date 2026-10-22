# Docs — design & dev spec (the HOW / system reference)

One story per feature, shared memory of the team. Technical decisions: [adr/index.md](adr/index.md).
Not published — user-facing docs live in `homepage/`.
Auto-loaded every turn: **this file** + [memory.md](memory.md). The module indexes below are **not**
auto-loaded — they carry the full entry text (trail, ADR links, Surefire numbers).

Status: ✅ done · 🚧 design/WIP · ❌ not done · ⏳ geparkt/historisch (superseded) · 📝 Studie. Backlog = every ❌/🚧 item.
Der vollständige Eintragstext je Feature (mit konkretem Status-Grund) steht im jeweiligen Modul-Index.

## Where the knowledge lives

* **[Core docs](../org.sterl.llmpeon.core/docs/index.md)** — `llmpeon-core` (plain Maven): agent loop,
  tool families (`disk*`/`web*`/`shell`/`docs*`), prompts, compact, provider mapping.
* **[Plugin docs](../org.sterl.llmpeon/docs/index.md)** — Eclipse/OSGi: SWT/JFace views & widgets,
  preference pages, header/roster, `eclipse*` adapters, OSGi tests.
* **[ADRs](adr/index.md)** (global 0001–0072) · [Glossary](glossary.md) · [Configuration](configuration.md)
* [Architecture rules](architecture.md) · [Open Points](open-points.md) · [Resolved](resolved-points.md)
  · [Open to Discuss](open-to-discuss.md)

## Core — `llmpeon-core`

Files: org.sterl.llmpeon.core/docs/

### agents
* Peon-PO (Jon) — ✅
* Agent-Specific AGENTS-<agent>.md — ✅
* AGENTS.md Support — ✅
* Agent-API-Retry (Mini) — ✅
* Built-in Agent Prompt Override — 🚧
* Plan & Dev Agent — ✅
* Custom Agents — ✅
* Standing Orders — ⏳
* Agent Ordering — ✅
* Persistent Agent History — ✅
* Queued User Messages — ✅
* Scaffold Agent — ✅
* Review Agent (Da Dok) — ✅
* Sklaven-Kontext — ✅
* Sub-agent tool timing — ✅
* Async Agent Tools — 🚧
* Per-Agent Think Support — ✅
* API-Fehler ins Agent-Memory — ✅
* System Prompts — Übersicht — ✅

### context & compact
* Context Architecture — ✅
* Context Message Konzept — ✅
* Compact — ✅
* Compact-Lock — ✅
* Tagged Results — ✅
* Session Token Usage — ✅

### tools
* Read Tools (Datei · Grep · Console) — Eclipse **und** Disk — ✅
* Search Agent Tool (Da Sniffa) — ✅
* Shell Tool — ✅
* Terminal-Session-Tool — ⏳
* Output-Trim-Service — ✅
* Tool-Output-Disclosure — ✅
* Tool-Time-Disclosure — ✅
* Tool-Result-Size im onTool — ✅
* Tool-Confirmation — ⏳
* Tool Descriptions Inventory — ✅
* Agent-Tool-Config — ✅
* Agent-Tool-Filter — ✅
* Disk File Write Tool — ✅
* File Copy Tool — ✅
* Write-Path Validator — ✅
* Web-Tools — ✅

### provider & konfiguration
* Provider (AiProvider) — ✅
* Prompt Caching — ✅
* `peon` Config Directory — ✅

### skills & prompts
* Project Skills — ✅
* Skill-Evolution-Loop — ✅
* Prompt Inventory & Wiring — ✅

### infra
* Docs-Linter — ✅
* MCP Servers — ✅
* Codegraph — Built-in-Integration — ✅
* Bounded Caches — ✅

## Plugin — `org.sterl.llmpeon`

Files: org.sterl.llmpeon/docs/

### chat view
* Streaming Response Display — ✅
* Chat Job Lifecycle — ✅
* Chat Markdown Links — ✅
* Agenten-Namen im Chat-Header — 🚧
* Interaction Design — ✅
* SWT Integrated Input Buttons — ✅
* Ask User Tool — ✅

### header & roster
* Agenten-Status im Header — ✅
* Agenten-Status Header — MVP-Neubau (Plan) — 🚧
* Header-State-Leak — ❌
* Icons — UI-Rollen & Tool-Output-Symbole — ✅

### konfiguration & modelle
* Advanced Configuration — ✅
* Default-Inheritance — ✅
* Model Config Widget — ✅
* Model Loading — ✅
* User-Context Selection — ✅
* Secure Credentials — 🚧

### eclipse*-tools & workspace
* Eclipse Read Tools — Projekt-Scope, Refresh, Console, Pfad-Dialekt — ✅
* Eclipse Workspace Write Tool — ✅
* Eclipse Java Move Type — 🚧
* Project-Problems-Tool — ✅
* Java-Debugger-Tool — ✅
* Workspace-Sicht — verschachtelte Projekte — ✅

### osgi-test-setup
* Test-Setup (OSGi) — ✅

## Repo-Docs & Notes

* [Architecture — Component Rules](architecture.md) - Service/Component/View/Widget layering, extraction rules, base
  packages. **✅ 2026-08-23:** PeonAiService = Fassade über SharedTools-/AgentContext-/BuildPoAgentComponent.

* [Maven Pipeline (CI)](pipeline.md) - CI headless-SWT-Verträglichkeit: `EclipseConsoleLogToolTest.after` crasht in
  CI (`SWTError: No more handles`) — neuer `ConsoleZoomHandler` in `org.eclipse.ui.console` 3.17.100 feuert
  `Display.getDefault()` auf `removeConsoles` (Target-Sprung 2026-03→2026-09). **✅ done (2026-09-10):** Fix =
  `xvfb-run -a mvn -B clean verify` in maven.yml; sequenziell validiert (B Guard grün → A ohne B grün); B-Guard
  bewusst NICHT zurückgebracht (maskiert Environment-Drift, AGENTS „guard that never fires is worse than none").

* [Docs als HTML veröffentlichen (Blume)](blume-publishing.md) - **🚧 Idee (2026-09-15, nicht geplant):** `docs/` als
  statische HTML-Site via [Blume](https://useblume.dev/docs). Vier Kollisionen mit dem Bestand (kein H1 im Content,
  striktes Frontmatter braucht `frontmatter.extend` für `idPrefix`, Mermaid/Callouts nur in `.mdx`, Tags nur als
  `search.tags`-Facette). Vorfrage Q-B4: `docs/` ist bewusst intern — veröffentlichen wir es überhaupt?

* [Verbesserungen aus opencode](verbesserungen.md) - **📝 Studie (2026-08-23).** Top 5 Übernahme-Kandidaten aus
  opencode: Ox-Alpha-Free-Provider, Modell-Katalog, Layered Activation/Onboarding, Secure Credentials + deklarative
  Auth, Permissions als Daten. Teil-Outcome: [Secure Credentials](../org.sterl.llmpeon/docs/secure-credentials.md).

* [Issue #142 — ASM-Konflikt](../issues/issue-142-asm-conflict.md) - **✅ gefixt (2026-10-06):** unser p2-Repo
  lieferte via `includeAllDependencies` Platform-Bundles (asm 9.10.1) mit und brickte Eclipse 2026-03; Flag entfernt,
  `jakarta.annotation` auf `[2.0.0,4.0.0)`, Target/Compatibility-Floor auf 2025-12. Repo liefert nur noch
  Feature+Plugin. Nutzer-Recovery: [Homepage Troubleshooting](https://peon-ai-4e.sterl.org/setup/troubleshooting) +
  `scripts/clear-stale-platform-bundles.ps1`.

* [Open Points](open-points.md) - zurückgestellte/offene Punkte mit Status ❓ offen · ⏳ selbst entschieden · 🔒
  geklärt.

* [Resolved Points](resolved-points.md) - geklärte Punkte ohne eigenes Feature-Doc (Punkt · Entscheidung ·
  Begründung · Datum). **Tool-Evolution-Run 2026-09-19:** CR-Verdicts nach PO-Run (abgelehnt/geparkt/angenommen +
  Decompiler-Begründung).

* [Open to Discuss](open-to-discuss.md) - ambiguous items not clear as bugs or features yet; reviewed end-of-cycle.

* [Open Ends / Memory](memory.md) - **Session-Zwischenstand only** (Ergebnis, offene User-Handlungen, Startpunkte
  der nächsten Session); nach jedem Plan-Zyklus aufräumen. Dauerhafte Lernings gehören nach `AGENTS.md` /
  `AGENTS-DEV.md` / `skills/` bzw. in die memory*-Tools, projektspezifische Fakten in die Feature-Docs und ADRs.

* [ADRs](adr/index.md) - technical decision records (the agent's long-term memory).

## Offene User-Entscheidungen

* Ehemals offen: Branch `release-2026-09-06` — die 3 Release-Commits (Dropdown-Deletion `a1d8d35` · UTF-8-Guard
  `27c09ad` · Skipped-Report `11f34f6`) sind seit 2026-10-13 vollständig in main, s.
  [resolved-points.md](resolved-points.md).

* **❓ Glossar eager** ((b) Turn-Context-Item für Jon+Thinka+Mek) und **❓ buildWithDev-Compact** (Compact statt
  Reset, ~50 %, nur neuer Plan) — Empfehlungen in [open-points.md](open-points.md).

* **❓ ApiRetry-Verdacht** (Null-Byte-IOException als Cancel klassifiziert?) — in [open-points.md](open-points.md),
  Investigation im Bug-Fix-Zyklus (Triage #5–#16 + Plugin-Hunt).
