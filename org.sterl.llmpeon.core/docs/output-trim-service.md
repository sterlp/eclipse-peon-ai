# Output-Trim-Service

> **Status:** ✅ done — Slice 1 (2026-10-17, `story/night-5-2026-10-16`: I1 `f277446e` · I2
> `22a91fed` · I3 `dc4a5594`; Surefire core 1144/0, OSGi 351/0, Da-Dok CONCERNS nur Docs-Cycle →
> geschlossen) · **Anlass:** `feature-request-ShellTool.md` (us4g, 2026-10-13) + `long-shell.log` ·
> Verwandt: [Shell Tool](shell-tool.md) · [Tool-Output-Disclosure](tool-output-disclosure.md)
> (R-OD-6) · [temp-file-tool.md](temp-file-tool.md) (Slice 2 — volle Ausgabe) ·
> [bounded-caches.md](bounded-caches.md)

## Anlass

Ein Agent hat sich mit einem einzigen `shellRunCommand` (Pipeline `grep|awk` über `backend/src/test`)
eine minifizierte Datei mit 116 Zeilen / **3.217.746 Zeichen** in den Kontext geholt — Session
unbrauchbar. `long-shell.log` bestätigt indirekt (~1.020k geschätzte Tokens, Stage `tool results 6000`).
Ein Agent sollte Shell nur für Build/Test/git nutzen (Read/Grep existieren) — trotzdem ist die
Ausgabegrenze ein Bug, kein Erziehungsproblem.

## SOLL (Slice 1 — Trim-Service, abgestimmt 2026-10-16, gebaut 2026-10-17)

1. **Geteilte Trim-Komponente im core** (shared, neben `FileLines`/`SearchQuery`) — eine Stelle für
   Cap + Kürzung + Disclosure; Shell und Grep nutzen sie per Komposition. Kein „Service" mit Lifecycle.
   **IST:** `OutputTrim` (Stadt-Name von Paul nicht vetoiert) mit `capLine` / `trim` /
   `Window` (Ringpuffer, nur Kopf+Ende im Heap) / `Result`.
2. **Harte Zeichengrenze als Default 10.000 chars** zusätzlich zur Zeilengrenze. Kürzung **Kopf +
   Ende** (Head 1/3, Tail 2/3 — Ende zuerst, Fehler stehen hinten in Build-Logs), Vermerk
   `… (truncated: <total> chars total, <shown> shown)`.
3. **`shellRunCommand`: Parameter-Switch `tailLines` → `maxChars`** (Clean Break, kein Alias).
   `maxChars` ist **Default, kein Hard-Cap** (Paul) — größer erlaubt; der echte Cap kommt mit
   [temp-file-tool.md](temp-file-tool.md), wenn das LLM die volle Ausgabe sicher erreichen kann.
   Risiko (bewusst): ohne Cap kann das Modell den Kontext wieder sprengen — Disclosure fängt es.
4. **Je-Zeile-Cap 500**, gekürzt mit `…(+N chars)` — der eigentliche Schutz gegen minifizierte Dateien.
5. **Grep migriert im selben Slice auf die Komponente** (Paul): dasselbe 10.000-Default-Cap für die
   Gesamt-Ausgabe; der R-OD-6-Zeilenlängen-Cap 2000 ist durch das Komponenten-Zeilen-Cap 500 ersetzt
   ([tool-output-disclosure.md](tool-output-disclosure.md) R-OD-6 nachgezogen). **Keine TMP-Datei in
   Slice 1** (Paul) — kürzen only.
6. **Konstante `MAX_OUTPUT_LENGTH`** — **realisiert als Verlagerung (D9):** gelöscht, die Grenze lebt
   in den `OutputTrim`-Konstanten (`DEFAULT_MAX_CHARS`, `MAX_LINE_CHARS`); kein `MAX_OUTPUT_CHARS`
   im Code (0 Referenzen — die SOLL-Formulierung „umbenennen" traf den Mechanismus nicht).
7. **Reader begrenzen**: ab der Zeichengrenze nur Kopf+Ende puffern (Ringpuffer), nicht alles im Heap.
8. **Tool-Beschreibung schärfen**: „Nur für Build, Test und git. Dateien lesen/suchen → Read/Grep-Tools
   — Ausgabe wird ab N Zeichen gekürzt."
9. Slice 2 = [temp-file-tool.md](temp-file-tool.md) (volle Ausgabe erreichbar machen) → danach
   Cap-Nachschärfung; Migration von Read (Clamp) bleibt eigenes Inkrement.

## BDD (Tests: `OutputTrimTest` 8 · `ShellToolTest` · `AiReponseBuilderTest` · `DiskGrepToolTest` · `EclipseGrepToolTest`)

- GIVEN ein Kommando gibt eine Zeile mit 3 Mio. Zeichen aus WHEN `shellRunCommand` läuft
  THEN Ergebnis ≤ 10.000 chars + Kürzungsvermerk mit Gesamtlänge. → `maxCharsHeadTailExcerpt`
- GIVEN 100 Zeilen à 80 Zeichen WHEN `shellRunCommand` läuft THEN unverändert, ohne Vermerk.
  → `maxCharsUnderCapNoDisclosure`
- GIVEN `maxChars=500` WHEN Ausgabe 2.000 chars THEN Kopf+Ende-Ausschnitt + Disclosure.
- GIVEN der Timeout greift bei übergroßer Ausgabe WHEN die Teilausgabe gebaut wird THEN dieselben Grenzen.
- GIVEN Grep liefert > 10.000 chars WHEN das Ergebnis gebaut wird THEN gleiche Kürzung + Disclosure.
  → `grepCompleteManyHitsAreCappedAndDisclosed`
- GIVEN eine minifizierte 500k-Zeile WHEN Trim THEN Zeile auf 500 gekürzt (`…(+N chars)`).
  → `grepClampsMinifiedLine` (Pin: `20014 chars total, 515 shown` — D3 zählt Kopf+Ende inkl. Marker)
- GIVEN Filter UND Trim greifen beide WHEN `shellRunCommand` läuft THEN beide Vermerke in einer
  Ausgabe (`showing N of M` + `truncated:`). → `runOsCommand_filterWithMaxChars`
- GIVEN Filter WHILE Trim THEN Filter läuft VOR dem Trim-Fenster. → `filterRunsBeforeTrim`

## Entscheidungen (Paul, 2026-10-16)

- Per-Line-Cap **500 überall** (Shell + Grep; ersetzt R-OD-6-Wert 2000).
- `maxChars` = Default, **kein** Hard-Cap (Cap kommt mit TempFileTool).
- Naming `OutputTrim` bestätigt (kein Veto).

## Offen (Slice 2 / später)

- Rollout Read-Tools (Clamp) auf die Komponente = eigenes Inkrement, kein Eile
  ([open-points.md](../../docs/open-points.md)).
