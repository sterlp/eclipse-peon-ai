# Offene Enden (2026-08-29)

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
  warn+ignore; Branch = `git branch --show-current` zu Build-Start. → nächster: 2b
  (Plugin-UI: Config-Seite, Dropdown+Refresh, Chat-UI-Ausblendung) und 2c (Cache-Hardcode-
  Entfernung, GPT/Claude-Beispiele, Cache-Abgleich, Custom-Agent-yml, Homepage).

- **Config-Umbau (User, 2026-08-28) — SOLL ❌ specified:** per-agent Model-Config (URL, Key,
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

- **Prompt Caching** ([caching.md](caching.md), 🚧): Design fest (2026-08-21, User): Caching als
  **per-agent JSON extra body** in Advanced Settings (auch Custom Agents) + GPT/Claude-Beispiele
  in der UI + gilt für jeden Agenten mit Modell-Slot; **Hardcode-Flatten: alles extra body**
  (Anthropic-Flags/OpenAI-`cache_control` werden zu UI-Beispielen); **Provider-Fähigkeits-Gate**:
  Boolean in `AiProvider`, UI-Input nur wo unterstützt; llama.cpp: kein Snippet = kein Slot
  (Compact-Agent); Abgleich über Usage-Cache-Tokens; **Provider-Umbau eigene Doku**
  ([provider.md](provider.md): `AiProvider`-Enum → eigene Komponente, je Provider eine Klasse,
  `supportsExtraBody()` je Provider-Klasse, verhaltenstreu). Dazu SOLL agent-spezifischer
  Config-Umbau (advanced-configuration.md). Umsetzung **nach der Issue-Runde**. Offene Fragen:
  Provider-Unterstützung (langchain4j `customParameters`), Merge-Semantik, Abgleich-Kanal.

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
