# File Copy Tool (eclipse + disk)

## Goal

Ein `fileCopy` pro Tool-Familie (`diskCopyFile`, `eclipseCopyFile`) — Byte-genauer Copy einer
Datei. Damit kann ein LLM „Move" selbst bauen (copy + delete), ohne den Datei-Inhalt zu echoen —
das ist heute nicht möglich und teuer (Content läuft zweimal durch den Context).

**WEIL (User, 2026-09-06):** beim Smoke-Test vermisst — Rename existiert, Copy fehlt in beiden
Familien. Ein Verhalten, eine Implementierung, konsistent in beiden Familien (AGENTS.md-Konvention).

## Status

❌ specified — **E2E-Befunde (2026-09-06) aufgedeckt: SOLL erweitert um R5 + R6, Umsetzung im
nächsten Zyklus.** Ursprüngliche Umsetzung (R1–R4) war gebaut, aber: Erfolgsmeldung landete
nur im UI-Kanal (LLM sah langchain4j-`"Success"` statt `Copied <s> -> <t>`) und der
Ziel-Pfad von eclipseCopyFile/eclipseRenameResource wurde gegen die Workspace-Root statt das
Quellprojekt aufgelöst.

## Business Rules

### R1: Copy-Kern ✅
`fileCopy(sourcePath, targetPath)` kopiert die Datei Byte-genau.

- **GIVEN** existierende Datei `a.txt` **WHEN** `diskCopyFile("a.txt", "sub/b.txt")` **THEN**
  Kopie unter `sub/b.txt`, Original bleibt, Parent-Dirs werden angelegt (wie Rename)
- **GIVEN** Ziel existiert bereits **WHEN** Copy **THEN** Fehler (wie `diskRenameResource`:
  „Errors if target exists") — *❓ offen: overwrite-Modus nötig, oder hart ablehnen wie Rename?*
  **PO-Empfehlung:** hart ablehnen wie Rename, konsistent; Overwrite ist bewusst kein Default.
- **GIVEN** Quelle fehlt oder ist ein Verzeichnis **WHEN** Copy **THEN** Fehler mit klarer Meldung
  (kein rekursives Directory-Copy im MVP)

### R2: Beide Familien, ein Verhalten ✅
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

### R5: Qualified Paths Only 🔒 (User 2026-09-06, E2E-Befund 2)
Copy und Rename verlangen **voll qualifizierte Pfade** für **Quelle UND Ziel**:
- `diskCopyFile` / `diskRenameResource`: **absolute** Pfade (workingDir wird nicht angenommen)
- `eclipseCopyFile` / `eclipseRenameResource`: **`/project/path`** (projekt-qualifiziert)

Relativer Pfad → **Fehler**, der den Vertrag nennt (z.B. „Copy/Rename paths must be fully
qualified — disk: absolute, eclipse: /project/path"). **WEIL:** Copy/Rename sind die einzigen
Zwei-Pfad-Operationen; ein relativer Pfad ist nicht eindeutig (workingDir/Quellprojekt-Annahme
kann falsch sein → stiller Falsch-Ort bzw. IllegalArgumentException). Read/Write/Edit bleiben
bei ihrer bestehenden Domain-Basis-Resolution (Single-File-Ops = workingDir bzw.
resolveInEclipse) — der Regel-Split ist: Cross-File-Ops = qualifiziert, Single-File-Ops = Domain-Basis.

- **GIVEN** relativer Pfad (egal welche Familie, Quelle oder Ziel) **WHEN** Copy/Rename
  **THEN** Fehler, der den qualified-paths-Vertrag nennt — keine stillen Auflösungs-Annahmen
- **GIVEN** qualifizierte Pfade **WHEN** Copy/Rename **THEN** Verhalten wie R1–R4

### R6: Erfolgsmeldung ist LLM-sichtbar 🔒 (User 2026-09-06, E2E-Befund 1)
Copy-Tools geben `String` zurück (wie Edit/Delete-Tools derselben Familien) — niemals `void`.
Das Tool-Result (nicht nur der UI-Monitor-Kanal) lautet `Copied <resolved-source> -> <resolved-target>`
mit den **aufgelösten** Pfaden (Disk: absolut, Eclipse: `/project/path`).

- **GIVEN** erfolgreicher Copy **WHEN** das LLM das Tool-Result liest **THEN** es sieht
  `Copied <s> -> <t>` — nicht den generischen langchain4j-`"Success"` für void-Methoden
- **GIVEN** erfolgreicher Rename **WHEN** Result **THEN** Rename-Result nennt ebenfalls
  aufgelöste Quelle+Ziel (gleiche Sichtbarkeits-Regel)
