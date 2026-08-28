# Verbesserungen aus opencode

**Status:** 📝 Studie / Vorschläge (2026-08-23) — Top Ideen aus dem Studium von
[opencode](https://github.com/anomalyco/opencode) (`/Users/sterlp/dev/workset/opencode`), fokussiert
auf **Usability** und **built-in AIs**. Jeder Punkt ist ein eigenständiger Kandidat für eine eigene
Story — ausformulieren erst bei Story-Start.

## Purpose

opencode löst zwei Dinge bemerkenswert gut, die Peon (Eclipse AI Harness) schwer hat:
**(1) First Success ohne Konfiguration** und **(2) Katalog statt Release-Zyklus** — neue Modelle
kommen ohne Code-Änderung. Diese Seite sammelt die Übernahme-Kandidaten.

> **Verworfen (2026-08-23):** „Freie LLMs keyless über Cloud-Gateway" — OpenRouter braucht doch
> einen Key für Completions (auch `:free`, live verifiziert), opencodes Sentinel-Trick funktioniert
> nur am eigenen Gateway. Details: [ADR-0033](adr/0033-ox-alpha-provider-slices.md) (Rejected).
> Ersatzidee falls „First Success" wieder aufgegriffen wird: lokale Ollama/LM-Studio-Erkennung.

---

## 1. Modell-Katalog statt Hardcoding (models.dev-Ansatz)

**opencode:** Ein remote JSON-Katalog (~100 Provider von models.dev), Disk-Cache mit TTL +
File-Lock, eingebetteter Build-Time-Snapshot für Offline (`packages/core/src/models-dev.ts`).
Neue Modelle = Catalog-Update, kein Release. Per-Provider-Quirks leben in separaten Custom-Loaders.

**Peon heute:** Modell-URLs teils hartkodiert (Mistral, Anthropic ignorieren Custom-URL beim
Listing), jede Modellliste per Provider-Code. Ein neues Modell erfordert nichts — aber eine neue
Provider-Quirk oder -URL schon Code + Release.

**Übernehmen würde ich:** Nur den Mechanismus, nicht die Abhängigkeit von models.dev — ein
optionales Peon-Katalog-JSON (gleiche Form wie `AiModel`), gecached im Plugin-Metadaten-Ordner,
Fallback auf die eingebaute `listAiModels()`-Logik. Der Katalog liefert Metadaten (Kosten,
Kontext-Limit, Reasoning-Flags), die heute fehlen — z. B. für Auto-Compact-Grenzen.
**Abhängigkeit:** baut sinnvoll erst nach dem Provider-Refactoring ([provider.md](provider.md)) auf.

---

## 2. Layered Activation & Onboarding (Zero-Config-Erkennung)

**opencode:** Ein Provider wird aktiv, wenn *irgendeines* davon zutrifft: Env-Var gesetzt,
Credential gespeichert, Config-Eintrag vorhanden (`provider.ts:1559–1602`). Env-Keys gewinnen als
Zero-Config-Pfad. Ohne Verbindung zeigt der Modell-Dialog „Popular Providers" mit Login direkt
inline (`dialog-provider.tsx`). Recent Models werden persistiert und als Default vorgeschlagen
(`state/model.json`, `defaultModel()`).

**Peon heute:** Kein First-Run-Erleben — nur Scaffold-/Jon-Tutorial-Nachrichten. Erkennung „nichts
konfiguriert" gibt es nicht; der User muss die Preference-Page finden. Zuletzt gewählte Modelle
pro Agent werden gehalten, aber nicht global als Recent vorgeschlagen.

**Übernehmen würde ich:**
1. **Env-Var-Erkennung** pro Provider (deklariert in der Provider-Klasse:
   `List<String> envVars()`) — `ANTHROPIC_API_KEY` gesetzt → Provider aktiv ohne Preference-Page.
2. **Onboarding-Hint in der Chat-View**: kein aktiver Provider erkannt → Inline-Hinweis mit
   direktem Sprung zur Preference-Page bzw. lokalem Vorschlag (Ollama/LM Studio auf localhost).
3. **Recent-Modelle global** persistieren und beim leeren Agent-Modell vorschlagen.

---

## 3. Sichere Credentials + deklarative Auth-Methoden

**opencode:** Credentials in `auth.json` mit Mode `0600`, drei Typen (`oauth`/`api`/
`wellknown`), CLI `opencode providers login` mit interaktivem Picker. Auth-Methoden sind
**deklarative Daten** (`{type: "oauth"|"api", label, prompts[]}` mit bedingten Prompts,
`provider/auth.ts`) — dieselbe Beschreibung treibt CLI und TUI.

**Peon heute:** API-Keys (und Copilot-OAuth-Token!) liegen im Klartext im
`ScopedPreferenceStore(InstanceScope)` (`LlmPreferenceInitializer.put(PREF_API_KEY, …)`) — lesbar
für jeden Code in der Workspace-Metadata. Copilot Device Flow ist handgebaut
(`CopilotDeviceFlowDialog`).

**Übernehmen würde ich:**
1. **`org.eclipse.equinox.security` Secure Store** für alle Keys/Tokens (höchster Wert, kleinstes
   Risiko) — Preference-Feld zeigt nur Masked-Werte, Actual-Lookup zur Laufzeit.
2. **Deklaratives Auth-Methoden-Schema** in der Provider-Klasse (`authMethods(): List<AuthMethod>`)
   → der Copilot-Device-Flow wird zum ersten generischen Fall statt Sonderlocke; spätere OAuth-
   Provider kosten fast nichts.

---

## 4. Permissions als Daten mit „Always Allow"-Lernen

**opencode:** Permission-Regeln sind Datensätze `{permission, pattern(wildcard),
action: allow|ask|deny}`, last-match-wins (`permission/index.ts`). „Deny" entfernt Tools komplett
aus der Modell-Sicht (nicht nur Runtime-Ablehnung — spart Token). Bestätigte Patterns werden in
der Session gelernt („always allow") und lösen andere Pending-Requests derselben Regel automatisch.

**Peon heute:** Globales Shell-Confirmation-Combo (3 Modi) auf der Preference-Page; Write-Validator
per Agent ([write-path-validator.md](write-path-validator.md)). Alles oder nichts — kein
Pattern-Matching, kein Lernen.

**Übernehmen würde ich:** Wildcard-Rule-Set pro Tool-Klasse (Shell, Disk-Write, Eclipse-Write) mit
allow/ask/deny + Session-„always allow". Der Write-Validator bleibt als fachliche Prüfung bestehen;
die Permission-Layer kommt als generische Schale darüber. Direkter Usability-Gewinn: weniger
Bestätigungs-Klicks bei gleichem Sicherheitsniveau, und `deny` spart Tokens, weil das Tool gar
erst advertised wird.

---

## Honorable Mentions (nicht Top 4, aber gemerkt)

* **Small Model Pattern** — dediziertes billiges Modell für Neben-Aufgaben (Titel, Summary).
  Peon hat bereits Compact/Search-Slots — konsolidieren statt erweitern.
* **`.well-known`-Auth-Delegation** — self-hosted Gateways definieren ihren eigenen Login.
  Interessant für Enterprise, weit weg vom Eclipse-Plugin-Kontext.
* **Offline-Snapshot des Modell-Katalogs** — nur relevant, wenn Punkt 1 umgesetzt wird.

## Relationship

* [Provider (AiProvider)](provider.md) — Punkte 1, 2 landen im geplanten Provider-Package
* [Advanced Configuration](advanced-configuration.md) / [caching.md](caching.md) — Extra-Body
  betrifft die Provider-Klassen
* [Write-Path Validator](write-path-validator.md) — bleibt fachliche Prüfung unter dem
  Permission-Layer aus Punkt 4
