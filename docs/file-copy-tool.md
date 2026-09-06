# File Copy Tool (eclipse + disk)

## Goal

Ein `fileCopy` pro Tool-Familie (`diskCopyFile`, `eclipseCopyFile`) — Byte-genauer Copy einer
Datei. Damit kann ein LLM „Move" selbst bauen (copy + delete), ohne den Datei-Inhalt zu echoen —
das ist heute nicht möglich und teuer (Content läuft zweimal durch den Context).

**WEIL (User, 2026-09-06):** beim Smoke-Test vermisst — Rename existiert, Copy fehlt in beiden
Familien. Ein Verhalten, eine Implementierung, konsistent in beiden Familien (AGENTS.md-Konvention).

## Status

❌ specified — Backlog, eigener Zyklus (nicht im Release).

## Business Rules

### R1: Copy-Kern ❌
`fileCopy(sourcePath, targetPath)` kopiert die Datei Byte-genau. Same-Tool-Familie wie
`RenameResource` — gleiche Pfad-Resolution (workingDir-relativ bzw. workspace-relativ).

- **GIVEN** existierende Datei `a.txt` **WHEN** `diskCopyFile("a.txt", "sub/b.txt")` **THEN**
  Kopie unter `sub/b.txt`, Original bleibt, Parent-Dirs werden angelegt (wie Rename)
- **GIVEN** Ziel existiert bereits **WHEN** Copy **THEN** Fehler (wie `diskRenameResource`:
  „Errors if target exists") — *❓ offen: overwrite-Modus nötig, oder hart ablehnen wie Rename?*
  **PO-Empfehlung:** hart ablehnen wie Rename, konsistent; Overwrite ist bewusst kein Default.
- **GIVEN** Quelle fehlt oder ist ein Verzeichnis **WHEN** Copy **THEN** Fehler mit klarer Meldung
  (kein rekursives Directory-Copy im MVP)

### R2: Beide Familien, ein Verhalten ❌
`eclipseCopyFile` und `diskCopyFile` verhalten sich identisch (gleiche Semantik, gleiche
Meldungen) — keine stillen Abweichungen (False-Negative-Risiko, AGENTS.md).

- **GIVEN** dieselbe Copy-Operation in beiden Familien **WHEN** Ergebnis gemeldet wird **THEN**
  identische Meldung (`Copied <source> → <target>`), beide melden absolute/resolve-te Pfade wie
  die Familie-übliche Success-Message

### R3: Overwrite ablehnen 🔒 (User 2026-09-06)
Ziel existiert → Fehler, **kein** Overwrite-Flag — konsistent mit `RenameResource`, kein stiller
Datenverlust.

- **GIVEN** Ziel-Datei existiert bereits **WHEN** `fileCopy` **THEN** Fehler („target exists"),
  Original und Ziel unverändert

### R4: Rename bleibt eigenes Tool 🔒 (User 2026-09-06)
`RenameResource` wird **nicht** durch Copy+Delete ersetzt — Rename ist atomar (`Files.move`),
Copy+Delete als LLM-Folge hat eine Fehlerlücke (Copy ok, Delete fails → Duplikat) und ein
LLM-assemblierter Move echo-t Content. Rename = atomic Move, Copy+Delete = der versammelte Move,
wenn nötig.
