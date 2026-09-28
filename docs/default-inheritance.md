---
idPrefix: DEF
---

# Default-Inheritance — der Dev-Slot als Default

> **Status:** ✅ done (2026-09-28, `story/issue-149-think`, Commits `19a67c17`…`a3320aa8`, Surefire core **1054/0**, OSGi Tycho **307/0** · Da Dok CONCERNS→Nacharbeiten `a3320aa8` · **Paul-Smoke steht aus** für R-DEF-4/5/7) · **Verwandt:** [advanced-configuration.md](advanced-configuration.md)
> (per-Agent-Slots, [ADR-0036](adr/0036-po-own-model-slot.md)) · [per-agent-think.md](per-agent-think.md)
> ([ADR-0061](adr/0061-think-default-base-think-fallback.md) — Think-Fallback als Vorbild) ·
> [model-config-widget.md](model-config-widget.md) (Basic-Seite)

## Ziel

Es gibt **keinen separaten Default-Slot — dev IST der Default** (Base). Paul hat 2026-09-28 bestätigt,
dass wir dabei bleiben („sollten wir reden" → Entscheidung: bleiben). Das Feature macht die
Vererbung konsequent und sichtbar:

1. **Alle NULL-Slots erben Base** — bisher nur PO (R: [ADR-0036](adr/0036-po-own-model-slot.md)).
2. **Die Advanced-Seite zeigt die Vererbung**: DEV-Sektion = gespiegelte Basic-Config,
   betitelt „Dev (Default)", ganz oben — damit klar ist, dass die anderen erben.
3. **Bug-Fix aus dem Widget-Zyklus-Umfeld (#125-Regression):** fehlende Base-URL crashte mit
   „baseUrl cannot be null or blank" (langchain4j), Advanced-Refresh starb am selben Bug.

## Regeln

### R-DEF-1 — Core-Slots erben das Base-Model ✅ done

#### UC-DEF-1 — Core-Slots erben das Base-Model

GIVEN ein Core-Slot (PLAN, COMPACT oder SEARCH) mit `model=null` WHEN die Config aufgelöst wird
(fachlich: `LlmConfig`-Agent-Record / effektive Connection) THEN der Slot nutzt das Base-Model
(`llm.model`), so wie PO es heute bereits tut ([ADR-0036](adr/0036-po-own-model-slot.md)).
Soll-Verhalten: vom IST abweichend — IST: PLAN/COMPACT/SEARCH bleiben `model=null`
(„provider default", `LlmConfig.java:218/226/234`).

### R-DEF-2 — Custom Agents erben das Base-Model ✅ done

#### UC-DEF-2 — Custom Agents erben das Base-Model

GIVEN ein Custom Agent mit leerem `model:` im Frontmatter WHEN das effektive Model aufgelöst wird
THEN der Agent erbt das Base-Model. Paul-Entscheidung 2026-09-28 („ja bitte"). Wer explizit „der
Provider entscheidet" will, lässt auch das Base-Model leer — dann greift der
„No model configured"-Guard (R-DEF-6).

### R-DEF-3 — Base-URL-Fallback (Bug #125-Regression) ✅ done

#### UC-DEF-3 — Base-URL-Fallback mit ehrlichem Fehler

GIVEN die Base-URL (`llm.url`) ist im Store nicht gesetzt (leer) WHEN eine Connection aufgelöst
wird THEN der Provider-Default greift, sofern der Provider einen hat (Ollama:
`http://localhost:11434` — Werte in `LlmConfig.newOllama`; LM Studio: `http://localhost:1234/v1` ⏳
Wert von Paul bestätigen lassen). Hat der Provider keinen Default (OpenAI-kompatibel mit fremder
URL), THEN ein **ehrlicher Fehler** mit klarem Hinweis („keine Base-URL konfiguriert —
Window > Preferences > Peon AI"), NIEMALS langchain4j-„baseUrl cannot be null or blank" durchreichen.

**Root Cause (IST, [ADR-0062](adr/0062-base-inheritance-dev-default.md)):** JFace
`ScopedPreferenceStore.setValue` entfernt den InstanceScope-Key, wenn der Wert == DefaultScope-Default
ist; der Runtime-Load liest nur den rohen InstanceScope (`LlmConfigLoader.java:30`,
`EclipseLlmConfigStore.java:21-23`). Basic-Seite öffnen + OK (Default-URL unverändert) → `llm.url`
verschwand → Runtime null. Der Advanced-Model-Refresh starb am selben Bug (Base-URL null im
`effectiveConnectionFor`-Fetch) — der Widget-Umbau (Inc-2) hat diese Klassen nicht angefasst.

**Write-Path-Fix (2026-09-28, Nachreview `a3320aa8`):** `performOk` beider Pages schreibt
Provider/URL/API-Key jetzt direkt via `EclipseLlmConfigStore.putOrRemove` (neu, geteilt) —
`s`chreibt den sichtbaren Wert explizit statt ihn still zu entfernen; damit ist der JFace-Trap für
URL/Key auf dem Schreibpfad geschlossen (lesseitig unverändert). Zugleich Fix des präexistierenden
NPE (`setValue(name, null)` bei leerem Feld → Partial Save): leer = Key entfernt, Save läuft
komplett durch (Test `performOkWithEmptyUrlAndApiKey_removesKeysAndSavesCompletely`, Mutation
„schreibt `""` statt remove" → 1 rot → revert).

### R-DEF-4 — Advanced-DEV-Sektion = komplette Default-Sicht ✅ done (Paul-Smoke steht aus)

#### UC-DEF-4 — Dev (Default)-Sektion

GIVEN die Advanced-Config-Seite ist offen WHEN die DEV-Sektion gerendert wird THEN sie trägt den
Titel **„Dev (Default)"** und zeigt **alle** Model-/Provider-Einstellungen — gespiegelte Basic-Felder
(Provider · URL · API-Key · Model+Refresh · Think) **plus Ping** (Thinka-F2, Verbindungssicht) und
DEV-Extras (Temperature, JSON extra body, R-DEF-7) — und schreibt **dieselben Keys wie Basic**
(`llm.providerType`, `llm.model`, `llm.agent.dev.*`). Grund: dev IST der Default.

**Override-Keys enden (Jon-Entscheidung aus Thinka-F1, Clean Break):** `llm.agent.dev.url` und
`llm.agent.dev.apiKey` werden **nie mehr gelesen** (Loader), beim nächsten Save geräumt. Andernfalls
entstünde ein unsichtbarer Override, der der UI widerspricht (UI zeigt `llm.url`, effektiv läuft
`llm.agent.dev.url`) — exakt die Lügen-Klasse, die wir nicht bauen. `llm.agent.dev.model` war schon
nie gelesen. Dev ist kein Override-Slot, sondern der Default ([ADR-0062](adr/0062-base-inheritance-dev-default.md)).

### R-DEF-5 — DEV ganz oben auf der Advanced-Seite ✅ done (Paul-Smoke steht aus)

#### UC-DEF-5 — Dev-Position oben


GIVEN die Advanced-Config-Seite listet die Agent-Sektionen WHEN die Sortierung gerendert wird THEN
DEV steht **ganz oben** — trotz logischer Sortierung — als sichtbares Signal „hier erben die
anderen von". Die übrigen Sektionen folgen in der bisherigen Reihenfolge.

### R-DEF-6 — „No model configured"-Guard bleibt ✅ done (kein Code-Change)

#### UC-DEF-6 — Guard unverändert ✅ (manuelle Verifikation 2026-09-28 — bestehender Guard, kein Code-Change)

GIVEN kein auflösbares Model (Base-Model leer UND Slot leer) WHEN ein Agent startet THEN der
bestehende Guard („No model configured — open Window > Preferences > Peon AI",
`AIChatView.java:542`) greift unverändert. Mit R-DEF-1/2 genügt ein Base-Model für alle Agenten.

### R-DEF-7 — DEV-Sektion = komplettes Model-/Provider-Setting ✅ done (Paul-Smoke steht aus)

#### UC-DEF-7 — DEV-Sektion komplett

Paul 2026-09-28: Die DEV-Sektion („Dev (Default)") enthält **alle** Model- & Provider-Einstellungen —
gespiegelte Basic-Felder (Provider · URL · API-Key · Model+Refresh · Think · Ping) **plus** Temperature und
JSON extra body. „Wenn nichts konfiguriert, nehmen wir den Default" — die Sektion ist der komplette
DEV-Slot, Basic ist die Verbindungssicht darauf. Basic-Scope selbst: R-DEF-8 (Temperature ja,
extra body nein).

### R-DEF-8 — Basic-Seite bekommt Temperature ✅ done

#### UC-DEF-8 — Temperature auf Basic

Paul 2026-09-28 (Antwort „3"): Die Basic-Page erhält ein **Temperature-Feld** (leer = unset — es wird
kein Parameter gesendet, Parse-Punkt `AgentTemperature` wie R-T1…R-T5 in
[advanced-configuration.md](advanced-configuration.md)); Feld-Position: nach Think (Bindung 6);
Label „Temperature (empty = unset):" — bewusst **nicht** „(Default)", weil Temperature NICHT an
andere Agenten vererbt (R-T, Thinka-F3: neben „Think (Default)" würde „(Default)" eine Vererbung
suggerieren, die es nicht gibt). JSON extra body bleibt Advanced-only (DEV-Sektion). Erweitert den
Widget-Scope aus [model-config-widget.md](model-config-widget.md) — dort als Scope-Update notiert.

## BDD-Abdeckung

| Regel | BDD-Typ | Test |
|---|---|---|
| R-DEF-1 | Automat (core) | Plan-Inkrement |
| R-DEF-2 | Automat (core) | Plan-Inkrement |
| R-DEF-3 | Automat (core) | Plan-Inkrement |
| R-DEF-8 | Automat (core/UI) | Plan-Inkrement |
| R-DEF-4/5 | Manuell (User-Smoke) | Paul |
| R-DEF-6 | Automat (bestehender Guard, unverändert) | — |

## IST-Notizen (für den Plan)

- Fallback-Punkte: `LlmConfig.poAgentConfig()` `:210` (Vorbild) vs. `planAgentConfig` `:218` /
  `compactAgentConfig` `:226` / `searchAgentConfig` `:234` / `customAgentConfig` `:247`;
  URL/Key-Fallback existiert bereits in `effectiveConnectionFor` → `agentBuilder` `:157-164`.
- Custom: `CustomAgent.java:166-168` (`firstOrDefault(MODEL, null)`), Auflösung via
  `LlmConfig.customAgentConfig`.
- Store-Keys: Base `llm.providerType|model|url|apiKey`; pro Agent `llm.agent.<id>.*`;
  `llm.agent.dev.model/url/apiKey` werden nie gelesen (Loader mappt DEV auf Base,
  `LlmConfigLoader.java:51`; url/apiKey ab R-DEF-4 Clean Break).
