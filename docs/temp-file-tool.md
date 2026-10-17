# Temp-File-Tool (Da Sniffa für Dateien)

> **Status:** 🚧 Idee (2026-10-16, Paul — Nachtzyklus 5 Nachbereitung) · Verwandt:
> [output-trim-service.md](output-trim-service.md) (nutzt es später per Komposition) ·
> [web-tools.md](web-tools.md) (webGet-Vorläufer) · [peon-config-directory.md](peon-config-directory.md)

## Idee

Shell/Grep kürzen die Ausgabe (Output-Trim) — die **volle** Ausgabe muss für Diagnose erreichbar
bleiben, auch ohne disk-Tools (die nicht immer an sind). Pauls Vorschlag:

- **`TempFileTool` (Komposition):** erzeugt TMP-Files in einem `peon-temp`-Verzeichnis mit
  **token-freundlichen Namen** (z. B. `shell-1234.log` statt UUID) — das LLM erreicht das File mit
  einem Token, nicht erst einen 36-Zeichen-Namen.
- **Read nur über den Namen, nie über den Pfad** — der LLM kennt den Pfad gar nicht; das Tool
  löst den Namen intern auf. Kein Write-Validator-/Traversal-Thema.
- **Auto-Delete:** Dateien räumen sich selbst weg (TTL oder „beim nächsten Spawn"), sonst sammelt
  sich Müll. `webGet`-Vorläufer: wir speichern heute schon die letzten URLs im RAM — gleiche
  Selbstkürzungsidee (siehe [bounded-caches.md](bounded-caches.md)).

## Offen

- Auto-Delete-Strategie (TTL vs. Zyklus-Ende vs. N-Files-Cap).
- Verhältnis zu `webGet` (liefert auf Disk herunter — gleicher Namensraum?) und zu disk-Read-Tools.
- **Hard-Cap für Output-Trim** kommt mit diesem Feature: erst wenn gesichert ist, dass das LLM die
  volle Ausgabe erreichen kann, wird `maxChars` (Trim-Service) auf Cap geklemmt.