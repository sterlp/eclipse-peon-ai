# Model Loading & Selection

The model dropdown shows the available models from the current LLM provider, with per-agent
model resolution. The list is fetched lazily and persists across agent switches — it is only
refetched when the provider config changes.

## SOLL (2026-08-28) — ✅ gebaut (Zyklus 2b, 2026-08-30)

Der Modell-Dropdown lebt in der Config-Seite (Basic-Page + pro Agent in der Advanced-Page,
geteilte Logik in `ModelComboWidget`) — [advanced-configuration.md](advanced-configuration.md),
Mechanik: [ADR-0034](adr/0034-connection-cache-by-identity.md). Die Liste gilt pro
**Verbindungs-Identität** (Provider+URL+Key): einmalig fetch, **Cache on success**,
Fetch-Fehler → konfiguriertes Modell bleibt gesetzt, kein Refetch beim Agentenwechsel;
**Refresh-Button unter dem Combo** (R-ML3) = manueller Refetch (Fehler → alter Cache bleibt).
Identitätswechsel der effektiven Verbindung → neuer Fetch. Konfiguriertes Modell nicht in der
Liste → **bleibt gesetzt** (bewusst kein Auto-Switch auf ein Listen-Modell); unbekanntes
Modell wird der Liste **angehängt** statt sie zu ersetzen.

```
GIVEN die Modell-Liste für eine Identität wurde erfolgreich geladen
WHEN ein Agent mit gleicher effektiver Identität aktiviert wird
THEN die gecachte Liste wird genutzt — kein Refetch

GIVEN der List-Fetch für eine Identität schlägt fehl (Netzwerk/leere Liste)
WHEN der Agent aktiviert wird
THEN das konfigurierte Modell bleibt gesetzt (kein Fehler, kein Auto-Switch)

GIVEN die gecachte Liste einer Identität
WHEN der User den Refresh-Button drückt
THEN die Liste wird neu geholt und ersetzt den Cache
AND bei Fetch-Fehler bleibt der alte Cache bestehen
```

## R-ML1 — Fetch-Identity ist live (2026-09-10) ✅ done (lib-update Zyklus, `fcb5339`)

Der Identity-Key für den Listen-Fetch (`ModelListCache.getOrFetch`) wird zur **Fetch-Zeit** aus
der aktuellen Konfiguration gebaut (live-Supplier) — nie aus einem Snapshot, der beim
Page-Aufbau gezogen wurde.

**WEIL:** ein beim Page-Aufbau gezogener Snapshot ließe Base-URL-Korrekturen erst nach
Page-Neuöffnung wirken.

- **BDD R-ML1a** GIVEN die Advanced-Page ist offen WHEN die Base-URL wird geändert und gespeichert
  THEN der nächste Listen-Fetch (Refresh-Button oder Dropdown-Open) nutzt die neue
  Effective-Connection-Identity — kein Page-Reopen nötig.
  Verifikation manuell (SWT-Präzedenz R-UI1/R-MCP3) + Code-Review des Live-Supplier-Wirings.
- **BDD R-ML1b** GIVEN ein Agent mit eigener URL WHEN die Agent-URL wird geändert THEN der Fetch
  nutzt die neue Agent-URL (Regression-Guard via Review).

Umsetzung (`fcb5339`): `AgentModelConfigSection.base` ist `Supplier<LlmConfig>`,
`prepareFetch()` baut die Identity zur Fetch-Zeit via
`base.get().effectiveConnectionFor(getRecord())`; `AiAdvancedPreferenceView` hält keinen stale
config-Field mehr (Supplier = `LlmPreferenceInitializer::buildWithDefaults`).
Think-Form/Extra-Body-Sichtbarkeit bleibt bewusst Konstruktions-Zeit (`base.get()` im Ctor).
Kein neuer Test (reines Wiring; Fetch-Logik von `AgentModelConfigFetchTest`/
`ModelComboWidgetTest` gedeckt) — Verifikation manuell wie R-UI1/R-MCP3.

## R-ML2 — Refresh & gespeicherte Verbindungs-Identität — ✅ dokumentiertes Verhalten (2026-09-12, User-Smoke beide Pages — kein Fix)

User-Entscheidung 2026-09-12: der Refresh-Button nutzt den **gespeicherten** Stand — Apply
(Speichern) übernimmt die korrigierte URL, danach greift Refresh mit der neuen Identität
(Fetch-Identity zur Fetch-Zeit, ADR-0034).

- GIVEN der User korrigiert die Base-URL **ohne** Apply und klickt Refresh, THEN die Liste kommt
  vom alten (gespeicherten) URL.
- GIVEN der User drückt Apply/OK, WHEN Refresh geklickt, THEN Fetch mit dem neuen URL aus dem Input.

Dokumentiert auf der Homepage (setup/advanced-configuration.md) und in configuration.md.
User-verifiziert (2026-09-12): nach Refresh ohne Apply bleibt die alte Modell-Liste vollständig
stehen, ein manuell eingetipptes Modell bleibt in der Auswahl.

## R-ML3 — Model-Auswahl = natives SWT Combo auf beiden Pages — ✅ done (ui-config, `6b5c9ca`, 2026-09-12)

Das Model-Feld nutzt auf **Basic** und **Advanced** (geteilte Logik in `ModelComboWidget`) das
**native SWT-Combo** wie die übrigen Dropdowns — gleicher Dropdown-Button. Label und Combo
erscheinen exakt wie die übrigen Label/Feld-Paare derselben Page: Label in der Label-Spalte der
Page (gleiche Ausrichtung wie die Sibling-Labels), Combo in der Feld-Spalte. Der
**Refresh-Button** sitzt **unter** dem Combo (Placement/Style wie „Check Host and Port" beim
URL-Feld).

Verhalten unverändert (R-ML-Regeln + HP): fetch einmal pro Identität, Refresh holt neu, manuelle
Eingabe erlaubt, konfiguriertes Modell bleibt selektiert auch wenn nicht in der Liste, Single-Flight
pro Identität + Secret-Masking (ADR-0040). Danach erst Design-Studie github-copilot-for-eclipse
(separater Schritt, advanced-configuration.md R-A3).