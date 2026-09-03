## Versuch — selbstentwickelnde Projekt-Skills

Wir erproben den schlanken WikiSkill-Loop aus
[`skills/skill-evolution/SKILL.md`](../skills/skill-evolution/SKILL.md): Plan und Dev prüfen nach jeder
Iteration belegte, wiederverwendbare Erkenntnisse; der PO bewertet vor Freigabe zusätzlich, ob die
aktiven Skills Planqualität, Umsetzung oder Review messbar verbessert haben. Annahmen und verworfene
Änderungen bleiben in [`skills/wiki/skill-impact.md`](../skills/wiki/skill-impact.md), damit wir den
Ansatz nach mehreren Zyklen anhand konkreter Wirkung beibehalten, vereinfachen oder verwerfen können.


# Offene Enden (2026-08-30)
- **Reality-Check (PO, 2026-08-30):** Session-Kontext aus anderer Umgebung — in DIESERM Checkout existierten weder Night-Cycle-A-Tests noch 2c-Stand (Start @ `f600862`). **A ✅ (2026-08-30):** `MockLlmServer`-Extension (Anthropic-SSE `/v1/messages`, Ollama-NDJSON `/api/chat`, `rootUrl()`, additive-only) + `PerAgentConnectionE2ETest` (3 Provider × (Base-Erbe, Agent-URL) mit model/think/extraBody-on-the-Wire-Assertions + Config-Edit ohne Stale-Cache via echtem `withModelConfig→updateConfig→planAgentConfig`-Pfad). `ToolLoopRequestConnectionTest` + `AiServicePerAgentModelTest` gelöscht (subsumiert); `AiServicePerAgentThinkTest`/`ModelConnectionCacheTest`/`ModelListFetchTest` bleiben. 512 Core-Tests grün. Commits `6e34cb2`/`bd41c95`/`24a7910` + `8309799` (A-done, Archiv `overview-done-2026-08-30-18-58.md`). SWT-Fix ✅ (inc-17). B (2c) ✅ + C (Icons) ✅ — siehe Night-Cycle-Eintrag.
- **SMOKE-TEST-BUG (User, 2026-08-30) → ✅ GELÖST (inc-17 `60b494e`):** `SWTException: Invalid thread access` beim Model-List-Fetch — Fix: `prepareFetch()`/`FetchSnapshot` (UI-Thread-Capture, SWT-freies `static fetchList`), Test `AgentModelConfigFetchTest` (Plugin 122/0). Regel ✅ in [advanced-configuration.md](advanced-configuration.md). **User: Smoke-Test erneut fahren** (Modell-List-Fetch + Refresh bei allen Agents).
- **Night-Cycle (User, 2026-08-30) — ✅ A+B+C alle abgeschlossen (Stand 2026-09-01):**
  **(A) ✅** E2E-Stub-Tests (siehe A-Eintrag oben). **(B) ✅ 2c (2026-09-01):** LLM-Config-Abschluss auf `new-config`, Commits `a866983` (inc-18 Cache-Clean-Break + `ExtraBodyExamples` + Paste-Buttons + Homepage-Section) / `5908e58` (inc-19 `TokenStats` + Header `↑ ↓ ⇄`, WRITE nur Tooltip) / `0058e8c` (inc-20 Custom-Agent-Frontmatter `url`/`api_key`/`extra_body`, `PromptYmlParser` core, 0 Plugin-Änderungen) / `818d80a` (inc-21 Homepage-Rewrite) / `aa1b255` (Skills-Wiki esbuild-Pattern) / `911e5da` (inc-22 tote `baseAgentConfig` gelöscht) / `c5353dd` (2c-done, Archiv `overview-done-2026-09-01-15-18.md`). Core 503 grün (Eclipse-Zählung 524 = Parameterized-Artefakt), Plugin 122/0. Review OK (Plan-Agent) + PO-Abnahme. **PO-Entscheidungen zur Bestätigung (User):** (1) **Cache-Clean-Break** — Hardcode raus, kein Cache ohne User-Config, keine Migration; (2) **Claude-Direkt-API verliert natives Caching** (cache_control gehört in System-/Tool-Blöcke, nicht in Top-Level-Body) — bewusst akzeptiert, Homepage dokumentiert; (3) UI-Beispiele mit **Paste-in-Button** (ersetzen + Status-Label); (4) Snippet-Inhalte: GPT `{"prompt_cache_key": "llmpeon"}` (Azure), Claude `{"cache_control": {"type": "ephemeral"}}` (Gateway-Form) — **User vor Merge gegen sein Gateway verifizieren**. **⚠️ Deferred-Gate (User-Umfeld):** Homepage `npm run docs:build` — esbuild hängt auf macOS 26.6.2 (env-blocker, Dep-Chain unangetastet; Pattern im Skills-Wiki). Nach User-Fix: `npm run docs:build` in `homepage/` einmal durchlaufen. **(C) ✅** Icons-Docs-Seite [icons.md](icons.md) angelegt (NUR Docs/Text): 🗜/🧩/🪄 zugewiesen, Agenten-Personen + Q1–Q3 offen, ⇄ als in-Benutzung eingetragen.
- **R8 ✅ GPT-Default-Cache-Key (2026-09-01, inc-23 `2fa94d2`, Archiv `overview-done-2026-09-01-19-40.md`):** `OpenAiProvider` injiziert `prompt_cache_key = "peon-ai-<agent-id>"` bei `gpt-5*`-Modellen (case-insensitive, `Locale.ROOT`) als Provider-Entry; User-Wins via 2a-Merge (blank/`""` = unset → Default bleibt); Guard: keine ID → kein Key (regression-safe, `ExtraBodyRequestTest` bleibt grün). `AgentConfig.id` (additive, nie in ConnectionIdentity), Stamps in 4 `LlmConfig`-Factories + `customAgentConfig(agentId)`. 5 main + 3 test (1 neu: `OpenAiProviderCacheKeyTest`, 8 Tests; E2E +1: Default + User-wins on-the-wire). Core 512 grün (Eclipse 533 = Parameterized-Artefakt). Homepage-Liner (AGENTS.md-konform). Review OK (Plan-Agent) + PO-Abnahme. Docs: [caching.md](caching.md) R8 ✅, [index.md](index.md) ✅. Commit `5c0ff28` (r8-done). **NICHT gemerged** (Squash/Merge = User).
- **inc-24 ✅ Model-List-Fetch Fix + API-Key-Masking (2026-09-02, `0735154`, Archiv `overview-done-2026-09-02-08-16.md`):** Bug 3 (CancellationException): globales `pendingRequest`-Cancel-Slot aus `SharedHttpClient` entfernt, `cancelAndGet`→`getModels` (plain sendAsync + get(timeout)), 7 Provider-Renames; `ModelListCache` Single-Flight pro `ConnectionIdentity` (`inFlight` CHM, `putIfAbsent` + inline fetch, two-arg `finally` remove) — 4 parallele Page-Open-Fetches teilen jetzt 1 HTTP-Call. Bug 4 (Security): `ConnectionIdentity.toString()` maskiert (`apiKey=***`, `buildTimeBody=<len> chars`), `EffectiveConnection.toString()` maskiert `perRequestBody`; `equals`/`hashCode` unverändert. Core 518 grün, Plugin 122/0. Review OK (searchAgent 4/4 PASS) + PO-Abnahme. **User-Bug-Report (Smoke-Test 2026-09-02):** Bug 1 (2 horizontale Striche zwischen Selected-File + Skill-Liste — 1 raus bzw. repositionieren) + Bug 2 (Scrollen in Advanced Config komisch — erst mal ignorieren) = **defer**.
- **Smoke-Test-Follow-ups (User, 2026-09-02) — nacheinander abarbeiten:**
  **(1) ✅ Basic-Config: Model-Dropdown + Refresh (inc-25 `975e784`, Archiv `overview-done-2026-09-02-09-28.md`).** `ModelComboWidget` extrahiert (CCCombo + Refresh + Fetch/Apply/Stale-Guard, `FetchSnapshot`/`fetchList`/`baseSnapshot` SWT-free Seams); `AgentModelConfigSection` dünner (kein Behavioral-Change); `AiConfigPreferenceView` ersetzt `StringFieldEditor(PREF_MODEL)` durch `ModelComboWidget` + `performOk`. 4 neue Plugin-Tests (`ModelComboWidgetTest`, `Display.getDefault()` + Assume-Guard). Plugin 126/0. Review OK (searchAgent 4/4) + PO-Abnahme. SKILL: SWT-UI-Test-Pattern in `skills/eclipse-dpe/SKILL.md`.
  **(2) ✅ Example-Buttons (inc-26 `0d896a4`, Archiv `overview-done-2026-09-02-09-47.md`).** `Example`-Record + `description`, `LLAMA_CPP`-Beispiel (`chat_template_kwargs.enable_thinking`), 3 Buttons in 1 Zeile + Tooltips. Core 519/519, Plugin 126/126. Homepage aktualisiert.
  **(3) ✅ „Loading Static env info" — kein Bug, aber Agenten-Namen ergänzt (inc-27 `726f816`).** Die Meldung kommt einmal pro System-Prompt-Rebuild (erster Call, Clear, Compact, Config-Change) — korrekt. User sieht sie von verschiedenen Agenten (Dev/Plan/PO) und konnte nicht unterscheiden, welcher Agent triggert. Fix: `AbstractAgent.buildSystemPrompt()` → `"Loading 📋 " + label + " (" + getName() + ")"`. 1 Test-Assertion angepasst. Core 519/519.
  **(4) ⏳ Dropdown Look & Feel (Cleanup, unwichtigster Punkt).** Die Config-Dropdowns passen optisch nicht (Look & Feel). Vergleich: ChatView-Dropdowns vs. Config-Dropdowns — sind es andere Widget-Typen? Recherche: github-copilot-for-eclipse für UI-Referenz. Eher Cleanup als Bug.
  **Deferred (aus früherem Smoke-Test):** Bug 1 (2 horizontale Striche, 1 raus/repositionieren) + Bug 2 (Advanced-Config-Scrollen).
- **Backlog aus `issues/fact-issues.md` (User-Scratch, 2026-08-30 geprüft):** 4 konkrete Items (noch keine Story, Kandidaten für eigene Runden): (1) `eclipseReadFile` ignoriert `startLine`/`endLine` → Partial-Reads nur via diskReadFile (eigenes Tool-Fix); (2) `eclipseGrepFiles` mit Regex-Patterns schlägt oft fehl → plain strings/diskGrepFiles (eigenes Tool-Fix); (3) Abort-Pfad: `CancellationException`-Stacktrace wird als Error geloggt (`ToolLoopRequest.call`/`ApiRetry`, repro in `AbstractAgentTest.testAbortAddsMessageBeforeThrowing`) → Log-Level/Filter für erwartete Abborts; (4) GitHub-Actions veraltet: Node.js-20-Deprecation, `actions/setup-java@v4`→v5, `checkout`/`deploy-pages`-Bumps (Pipeline release/deploy).

- **Zyklus 2b ✅ (2026-08-30):** Plugin-UI per-agent Model-Config gebaut + Plan-Agent-Review OK + PO-Abnahme. 5 Inkremente: (1) `AgentModelConfig`-Record + `LlmConfig.modelConfigs` (alte per-agent-Felder raus, kein Temperature-Feld), (2) Persistenz-Domäne `LlmConfigStore/Keys/Loader/Saver` in core + `EclipseLlmConfigStore` + `PeonConstants`-Aliase (Sauberer-Rebuild, alte Keys ignoriert), (3) 4× `AgentModelConfigSection` (URL/Key/Modell-CCCombo+Refresh, Think-Widget je Base-Provider-`ThinkSupport`, JSON-ScrolledText nur bei `supportsExtraBody()`), (4) `ModelListCache` (pro `ConnectionIdentity`, Cache on success, Refresh-Fehler → alte Liste, configured-Model-Append ohne Auto-Switch), (5) Chat-UI-Räumung (`modelCombo`/`btnThink`, `PeonAiService.setModel`/`withThinkSupported`). Core 507/0 (Eclipse-Zählung; mvn 486), Plugin 121/0 grün. Commits auf `new-config`: 2a inc-1…8 + 2b (u. a. `10205dc`, `133dafc`, `c184aea`). Docs geflippt: advanced-configuration.md ✅ 2b (2c-Teile ❌), model-loading.md ✅, caching.md R2 ✅ + R1a-UI-✅ (Beispiele/Hardcode → 2c). **NICHT gemerged** (Squash/Merge = User). **E2E-Handverifikation (User, vor Merge):** Agent mit eigener URL → andere Liste; Refresh; Base-URL-Wechsel → keine leeren Dropdowns (Known-Issue gelöst); Chat ohne Modell-/Think-Widgets. **2c ✅ (2026-09-01)** — siehe Night-Cycle-Eintrag oben.

- **Prompt- & Git-Konventions-De-duplizierung (2026-08-29):** Git-SOLL jetzt nur in Root-
  `AGENTS.md` „Build cycles & git" (3 Zeilen, für alle Verbraucher: Peon, Nicht-Peon-Tools,
  User); operatives Git-Wissen in den Agent-Prompts — `po-delegation.txt` (PO bestimmt den
  Branch-Namen, übergibt an Da Mek; Branch-Check bei Zyklusbeginn), `developer.txt` /
  `dev-build-loop.txt` (Commit-Disziplin, vertical slices, compactSession, docs-Grenze; User
  editiert selbst). `AGENTS-DEV.md`-Git-Bullet gelöscht, `AGENTS-PO.md` vom User gelöscht.
  **Prompt-Docs-Policy (User, 2026-08-29):** kein Prompt-*Inhalt* in den Docs (Repo = SOT) —
  Docs halten nur das Wiring: welche Datei, wer lädt sie, wann, warum + Link. Kandidat: neue
  Story `docs/prompts.md` (12 Prompt-Dateien, Loader `PromptLoader`, `withDefault` hängt
  `default.txt` vor Custom Agents) — **✅ angelegt (2026-08-29)** + po-agent-jon.md umstrukturiert
  (Prompts-Sektion mit Wiring-Tabelle, Prompt-BDDs → Referenzen, R15 ✅ prompt-basiert).


- **Git-Build-Zyklen (User, 2026-08-28):** Build-Zyklen laufen auf einem dedizierten Branch;
  Da Mek committiert nach jeder grünen Iteration (kurze Summary, nur Iterations-Dateien);
  finaler Merge/Squash = User. Konvention: AGENTS.md „Build cycles & git“ + SOLL
  [po-agent-jon.md](po-agent-jon.md) R15 ❌ (po.txt-Erweiterung = Backlog). User hat den
  Branch angelegt + alles committed.
- **Zyklus 2a ✅ (2026-08-28):** Core-Model-Config-Fundament gebaut + PO-Review OK (483 Core-
  Tests grün, Root-Build + Plugin-Compile-Check SUCCESS). Branch `new-config`: Commits `inc-1`…
  `inc-8` + Abschluss (inkl. Docs-Sync) — **NICHT gemerged** (Squash/Merge = User). PO-
  Entscheidungen im Zyklus: User-Body gewinnt bei Key-Konflikt; invalides extraBody-JSON →
  warn+ignore; Branch = `git branch --show-current` zu Build-Start. → 2b ✅ (2026-08-30); nächster: 2c (Cache-Hardcode-
  Entfernung, GPT/Claude-Beispiele, Cache-Abgleich, Custom-Agent-yml, Homepage).

- **Config-Umbau (User, 2026-08-28) — SOLL ✅ komplett umgesetzt (2a/2b/2c, Stand 2026-09-01):** per-agent Model-Config (URL, Key,
  Modell, Think, JSON extra body); Connection-Cache nach Identität Provider+URL+Key
  [+Body nur Anthropic/Build-time] — [ADR-0034](adr/0034-connection-cache-by-identity.md);
  Modell-Listen einmalig pro Identität (Cache on success, Fehler → configured model);
  Modell-Dropdown + Think aus Chat-UI raus (Config-Seite = Single Source of Truth);
  JSON-Widget je Agent (Prefix-Speicherung); per-request Body nur OpenAI-Familie (verifiziert);
  Modell nicht in Liste → bleibt gesetzt (kein B2-Auto-Switch); Refresh-Button im
  Config-Dropdown (manueller Refetch, ❌). **Entschieden (2026-08-28): Option B** — Cache-
  Hardcodes bleiben im Provider-Refactor verhaltenstreu, fallen in Schritt 2 mit der
  JSON-Body-UI. Neu ❌: Core/UI-Trennung (Config-Domäne in core, Plugin = reine View),
  kompletter Config-Rebuild bei Update (keine Migrations-Kette), Custom Agents tragen die
  komplette Model-Config via AGENT.md-Frontmatter (yml) + Homepage-Doku. Reihenfolge:
  Provider-Refactor (provider.md R1–R5, Slice 1 ADR-0033) → Config-Umbau + Caching.
  **FERTIG (2026-08-28):** Provider-Refactor gebaut + PO-Review OK (454 Core-Tests grün,
  `mvn clean verify` 7 Module SUCCESS, Plugin-Compile-Check SUCCESS) — **NICHT committed**
  (Commit = User). AGENTS.md Compile-Check-Kommando korrigiert (`releng/llmpeon-target` in
  `-pl`). Nächster Schritt: Config-Umbau + Caching (Schritt 2). **Offen User-Frage:**
  „voice config“ = Base-Config (basic page) gemeint? (kein Voice-Feature in den Docs). **Follow-ups:** `free-provider-ox-alpha.md`
  (Slice 2) fehlt noch als Story; ADR-Index 0027-Duplikat renumerieren; Known-Issue-Bug
  (URL-Wechsel) wird strukturell kleiner, Fix-Regel im Config-Umbau.

- **Zyklus 2.6.3 (Standing-Orders-Umarbeitung) — Review + Fix-Kampagne abgeschlossen:**
  - Bauspezifikation + Review OK: issues 01 (NPE-Guard in `PeonAiService.get()`), 03+04
    (Static-Context-Re-Bake bei `updateConfig`/Reload-Callback), 05 (clear-Test), 06
    (Line-Separators + stray Quote) — Core 394/0, Plugin 91/91 grün. **Nicht committed** (Commit = User).
  - issue-02 (Slaven-Memory-Regression) **ABGELEHNT** — Halluzination: `AiPoAgent.setStaticContext`
    propgt das Memory an die Slaven; Repro-Test deterministisch grün, Datei gelöscht.
  - Docs ausgerichtet: [ADR-0031](adr/0031-static-context-env-plus-memory.md),
    context-architecture.md, ADR-0029-Superseded-Note, standing-orders-design.md → historisch.
- **Beobachten:** R2(a)-Rest-Race — nur relevant, falls der Live-Status nach Compact
  doch noch mal klebt (spät gelieferter Monitor-Callback, vgl. context-architecture.md R2).
- **BUG (User, 2026-08-21) → GELÖST (User-Code, von Da Mek reviewt):** ReloadConfigTool-Callback-Wrapper (PeonAiService) — der Reload-Pfad läuft NICHT durch updateConfig; ohne Re-Bake-Callback bekommen neue Custom Agents keinen Static-Context. ADR-0032 Rev: Memory nur noch dynamisch, Static = Env-only; Wrapper bleibt fürs Env-Re-Bake nötig. Suite 98/0, NICHT committed.

- **Prompt Caching** ([caching.md](caching.md), ✅ done 2c 2026-09-01): Design fest (2026-08-21, User): Caching als
  **per-agent JSON extra body** in Advanced Settings (auch Custom Agents) + GPT/Claude-Beispiele
  in der UI + gilt für jeden Agenten mit Modell-Slot; **Hardcode-Flatten: alles extra body**
  (Anthropic-Flags/OpenAI-`cache_control` werden zu UI-Beispielen); **Provider-Fähigkeits-Gate**:
  Boolean in `AiProvider`, UI-Input nur wo unterstützt; llama.cpp: kein Snippet = kein Slot
  (Compact-Agent); Abgleich über Usage-Cache-Tokens; **Provider-Umbau eigene Doku**
  ([provider.md](provider.md): `AiProvider`-Enum → eigene Komponente, je Provider eine Klasse,
  `supportsExtraBody()` je Provider-Klasse, verhaltenstreu). Dazu SOLL agent-spezifischer
  Config-Umbau (advanced-configuration.md). **✅ umgesetzt in 2c** (Clean-Break, UI-Beispiele,
  Usage-Abgleich `↑ ↓ ⇄`, Custom-Agent-Frontmatter, Homepage). Offene Fragen alle geklärt
  (→ caching.md „Open Questions“): Provider-Unterstützung verifiziert, Merge = User-gewinnt +
  Reserved-Strip, Abgleich = Token-Header.

# Geschlossen

- **Zyklus Agent-MD-Vorschläge + Jon-Skill (2026-08-27):** von User selbst finalisiert —
  `agents-md-proposal/` = 2 Optionen × 3 Dateien (`option-with-jon/` mit Skill-Pointer,
  Empfehlung / `option-without-jon/` methodenfrei), beide prompt-befreit, nur Projektfakten;
  README mit Challenge-Ergebnis (Da Thinka) + Deploy-Checkliste. Prompt-Vorschlag
  `po-delegation.txt` (Autonomie-/Night-Cycle-Schritt) **vom User angewendet**. Jon-Skill
  (agentskills.io-Format mit role references) vom User **rauskopiert** (→
  github.com/sterlp/ai-skill-codex) — Repo-Staging (`skills/jon/`, Root `jon-bringup*.md`)
  vom User gelöscht. Verbliebene Follow-ups (im README): Inc 3 (Phase-Dateien pro Sklave),
  Module-Guide-Konflikte (runTests vs. mvn), ADR-0027-Duplikat renumerieren,
  `non-peon-ai-agents-md/` deprecaten. Nichts committed.
- **Core-Fix-Kampagne (2026-08-16):** `ThreadSafeMemory`-Load-Pfad doppelte Division gestrichen
  (chars/9 → chars/3, konsistent mit `estimateTokens`); `ChatMessageUtil.toString()`-Workaround
  bleibt im Plugin ([ADR-0030](adr/0030-statictext-helper-frozen-chatmessageutil.md)).
- **Zyklus ADR-0029 (2026-08-16):** File-Context in der History gebaut + grün: po-agent-jon.md
  Marker ✅, EclipseFileContextItem + AgentsMdContextItem → Header-dedupKey, `itemsFor()` mit
  2 Items (R1+R2), Core-Delta `StandingOrdersBuilder.buildItems()` → `List<ContextItem>`,
  R2(a) Live-Status-Hide nach Replay in `doCompressContext`.

- **Idee (User, 2026-08-30, nach Zyklus 2b) → ✅ umgesetzt (2026-08-30/31):** Docs-Seite [icons.md](icons.md) angelegt (SOLL ❌ specified): 🗜 Compact / 🧩 SKILL / 🪄 Command zugewiesen, Agenten-Personen + Q1–Q3 offen, Typografie-Regeln; in [index.md](index.md) eingetragen. Umsetzung (UI-Widgets) = eigener Build-Zyklus, Backlog.
