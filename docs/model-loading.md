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
Liste → **bleibt gesetzt** als Feld-Text (bewusst kein Auto-Switch auf ein Listen-Modell); die
Liste besteht **ausschließlich aus Server-Einträgen** — die getippte Eingabe wird nicht als
Eintrag aufgenommen (R-ML4).

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

## R-ML2 — Refresh & Verbindungs-Identität — ❌ neu entschieden (2026-09-28, Paul; ersetzt die Entscheidung vom 2026-09-12)

**Alt (2026-09-12, ✅ gebaut, user-verifiziert):** Refresh las den **gespeicherten** Stand — Apply
zuerst, dann Refresh mit der neuen Identität. **Warum umgestellt (WEIL):** Paul — „Es hilft uns
nichts, wenn ich die Eingabe mache und erst die Config-Seite verlassen muss, um das Modell zu
testen." Refresh-ohne-Apply verlangte erst Apply = Config-Seite war zum Testen nicht benutzbar
(widget-vs-store-Stale-Read, „Anfänger-Fehler"-Muster).

**Neu:** Ping & Reload lesen die **Live-Widget-Werte** (Provider/URL/Key/Think), Reload
persistiert nicht (Apply bleibt einziger Save-Pfad, Cancel verwirft). Die Regeln und BDDs liegen
jetzt im Widget-Doc: [model-config-widget.md](model-config-widget.md) (R-MCW-2/R-MCW-3, Präfix
`MCW`). Der gelieferte Umbau ist das `ModelConfigWidget`.

## R-ML3 — Model-Auswahl = natives SWT Combo auf beiden Pages — ✅ done (ui-config, `6b5c9ca`, 2026-09-12)

Das Model-Feld nutzt auf **Basic** und **Advanced** (geteilte Logik in `ModelComboWidget`) das
**native SWT-Combo** wie die übrigen Dropdowns — gleicher Dropdown-Button. Label und Combo
erscheinen exakt wie die übrigen Label/Feld-Paare derselben Page: Label in der Label-Spalte der
Page (gleiche Ausrichtung wie die Sibling-Labels), Combo in der Feld-Spalte. Der
**Refresh-Button** sitzt **unter** dem Combo (Placement/Style wie „Check Host and Port" beim
URL-Feld).

Verhalten unverändert (R-ML-Regeln + HP): fetch einmal pro Identität, Refresh holt neu, manuelle
Eingabe erlaubt, konfiguriertes Modell bleibt erhalten (Feld-Text, R-ML4) auch wenn es nicht in der
Liste steht, Single-Flight pro Identität + Secret-Masking (ADR-0040). Danach erst Design-Studie github-copilot-for-eclipse
(separater Schritt, advanced-configuration.md R-A3).

## R-ML4 — Liste = Server-Liste, Eingabe bleibt Feld-Text — ✅ done (ui-config Inc-5 `985b244`, User-Re-Smoke ✅ 2026-09-13; 2026-09-12 User-Entscheidung nach Smoke — ersetzt die 2b-Append-Regel)

Die Combo-Liste besteht **ausschließlich aus Server-Einträgen** — die getippte Eingabe wird
**nicht** als Eintrag aufgenommen (User 2026-09-12: „den Code entfernen, wo die aktuelle Auswahl
mit in die Liste aufgenommen wird"; ersetzt die 2b-Regel „unbekanntes Modell wird der Liste
angehängt"). Die Eingabe bleibt als **Feld-Text** erhalten und wird so gespeichert. Matcht der
Feld-Text einen Server-Eintrag (case-insensitive), selektiert das Combo den **Server-Eintrag**
(canonical — die getippte Variante verschwindet). **Ohne Server-Liste** (Fetch
fehlgeschlagen/leer) bleibt der Feld-Text verbatim, die Liste bleibt beim alten Cache-Stand.

- GIVEN die Server-Liste enthält `FOO`, WHEN der User `foo` getippt hat und der Fetch
  abgeschlossen ist, THEN enthält das Combo genau **einen** Eintrag (`FOO`) und zeigt/speichert
  `FOO` → `ModelComboWidgetTest.typedCaseVariantOfListedModelIsNotDuplicated`
- GIVEN das getippte Modell ist in keiner Server-Liste, WHEN der Fetch abgeschlossen ist,
  THEN enthält das Combo nur die Server-Einträge (kein Append) und der Feld-Text bleibt verbatim
  → `ModelComboWidgetTest.typedUnknownModelIsNotAddedToList` (neu)
- GIVEN Fetch fehlgeschlagen/leere Liste + getipptes Modell, WHEN Apply, THEN bleibt der
  Feld-Text verbatim (keine Kanonisierungsquelle) und die Liste bleibt der alte Cache-Stand
  → `ModelComboWidgetTest.refreshFailureKeepsTypedModelVerbatim` (angepasst: kein Listen-Append)