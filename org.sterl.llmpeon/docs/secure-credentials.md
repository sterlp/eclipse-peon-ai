# Secure Credentials

**Status:** 🚧 planned (2026-08-26) — Herkunft: [verbesserungen.md](../../docs/verbesserungen.md) Punkt 3
(opencode-Studie). Zielbild vom User bestätigt: **sicher + verschlüsselt speichern, maskiert
anzeigen, aber Copy-Button** — der Key muss weiter kopierbar sein.

## Goal

API-Keys und OAuth-Tokens liegen heute **klartext** im `ScopedPreferenceStore(InstanceScope)`
(`LlmPreferenceInitializer.put(PREF_API_KEY, …)` — lesbar für jeden Code in der
Workspace-Metadata, und sie reisen mit jedem Preferences-Export mit). Sie wandern in den
**Eclipse Secure Store** (`org.eclipse.equinox.security`) — verschlüsselt auf Platte, maskiert in
der UI, und bleiben über einen **Copy-Button** weiterhin abholbar.

## Business Rules

### R1 — Verschlüsselte Speicherung ❌

Keys/Tokens werden ausschließlich im Secure Store gespeichert
(`SecurePreferencesFactory.getDefault().node("org.sterl.llmpeon")`, `encrypt=true`).
Das Preference-Store-Feld (`PREF_API_KEY` etc.) wird nicht mehr geschrieben; Bestandsfelder
leeren sich bei der Migration (R4).

* `keyLandsInSecureStoreNotPreferenceStore` — GIVEN User speichert einen API-Key WHEN danach
  gelesen THEN Secure Store liefert ihn, PreferenceStore enthält keinen Klartext.

### R2 — Maskierte Anzeige ❌

Das UI-Feld zeigt nie den vollen Key, sondern Masked-Wert (z. B. `sk-…abc1`) plus
„gespeichert"-Indikator, wenn ein Key im Secure Store liegt.

* `maskedDisplayNeverShowsFullKey` — GIVEN gespeicherter Key WHEN Preference-Seite gerendert THEN
  Feld zeigt nur Maske/Indikator, nicht den Klartext.

### R3 — Copy-Button ❌

Neben dem Key-Feld liegt ein **Copy-Button**: er liest den Klartext aus dem Secure Store und legt
ihn in die System-Zwischenablage (SWT `Clipboard`). Kein „Reveal"-Feld — die einzige Sichtbarkeit
des vollen Keys ist das Kopieren.

* `copyButtonPutsRealKeyOnClipboard` — GIVEN gespeicherter Key WHEN Copy-Button gedrückt THEN
  Zwischenablage enthält den echten Key.
* `copyWithoutKeyIsNoopWithHint` — GIVEN kein Key gespeichert WHEN Copy-Button gedrückt THEN keine
  Exception, kurzer Hinweis (Statuszeile/Meldung).

### R4 — Einmal-Migration ❌

Beim Start: existierende Klartext-Keys aus dem PreferenceStore in den Secure Store verschieben und
im alten Store entfernen. Abbruchfrei — fehlgeschlagene Migration lässt den Klartext unangetastet
(kein Datenverlust) und warnt.

* `migrationMovesPlaintextOnce` — GIVEN alter Plugin-State mit Klartext-Key WHEN Plugin startet
  THEN Key liegt im Secure Store, Pref-Feld ist geleert.
* `failedMigrationKeepsPlaintext` — GIVEN Secure Store nicht schreibbar WHEN Migration läuft THEN
  Klartext bleibt stehen, Warnung erscheint.

### R5 — OAuth-Token zieht um ❌

Der Copilot-OAuth-Token aus dem Device Flow (`CopilotDeviceFlowDialog`) landet ebenfalls im
Secure Store statt im PreferenceStore.

* `copilotTokenInSecureStore` — GIVEN erfolgreicher Device Flow WHEN Token gespeichert THEN nur im
  Secure Store, PrefStore leer.

### R6 — Config-Bau liest zur Laufzeit aus dem Secure Store ❌

`LlmConfig` bekommt den Key beim Bauen via eine Lookup-Stelle aus dem Secure Store — nicht mehr
aus dem PreferenceStore. Export/Import von Eclipse-Preferences enthält dadurch keine Keys mehr
(Feature, kein Bug).

* `configBuildReadsFromSecureStore` — GIVEN Key nur im Secure Store WHEN Agent-Config gebaut THEN
  Requests führen den korrekten Key.
* `preferenceExportContainsNoSecrets` — GIVEN Keys gespeichert WHEN Preferences exportiert THEN
  Exportdatei enthält keinen Key/Token.

## Randfälle / offene Punkte (Entscheider beim Bau)

- Headless/CI: Secure Store braucht ggf. `-eclipse.password` bzw. Default-PasswordProvider —
  für Tests einen Seed setzen (sonst Prompt/Timeout im Plugin-Test).
- Windows/macOS: Default-Provider genügt (kein OS-Keychain-Prompt) — OS-Keychain-Anbindung
  bewusst Backlog.

## Relationship

* [Advanced Configuration](advanced-configuration.md) — Key-/URL-Felder der Preference-Seiten;
  R2/R3 ändern deren Darstellung
* [Provider (AiProvider)](../../org.sterl.llmpeon.core/docs/provider.md) — Provider-Klassen bekommen den Key unverändert via
  `LlmConfig` injiziert (R6 hält die Schnittstelle stabil)
* [verbesserungen.md](../../docs/verbesserungen.md) Punkt 3 — deklarative Auth-Methoden (generischer Device
  Flow) bleibt Folge-Story, hier nur Token-Umzug (R5)
