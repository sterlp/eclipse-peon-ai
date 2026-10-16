# Output-Trim-Service

> **Status:** 🚧 in design (2026-10-16, Nachtzyklus 5 — Jon-Bewertung, **WIP by Paul**) · **Anlass:**
> `feature-request-ShellTool.md` (us4g, 2026-10-13) + `long-shell.log` · Verwandt:
> [Shell Tool](shell-tool.md) · [Tool-Output-Disclosure](tool-output-disclosure.md) · ADR folgt bei Freigabe.

## Anlass

Ein Agent hat sich mit einem einzigen `shellRunCommand` (Pipeline `grep|awk` über `backend/src/test`)
eine minifizierte Datei mit 116 Zeilen / **3.217.746 Zeichen** in den Kontext geholt — Session unbrauchbar.
`long-shell.log` bestätigt das indirekt: Compact-Zeile mit ~1.020k geschätzten Tokens und Stage
`tool results 6000` (Trim-Leiter musste Tool-Results auf je 6000 chars kapppen, um überhaupt zu passen).
Ein Agent sollte Shell nur für Build/Test/git nutzen (Read/Grep existieren) — trotzdem ist die
Ausgabegrenze ein Bug, kein Erziehungsproblem.

## IST (am Code verifiziert, 2026-10-16)

- `ShellTool.MAX_OUTPUT_LENGTH = 3000` heißt „Length", begrenzt aber **Zeilen** (`tail()`), nicht Zeichen.
- Keine Obergrenze pro Zeile, keine für die Gesamtausgabe — 116 Zeilen passieren ungefiltert.
- `| head -30` in der Pipeline schützt nicht (nur Zeilenzahl).
- Reader puffert jede Zeile ungebunden in einer `LinkedList` (auch Heap-Risiko).

## Bewertung (Jon, Nachtzyklus 5)

1. **Request ist valide und code-bestätigt.** Der Fall ist keine Geschmacksfrage: ein Limit, das
   Zeichen verspricht und Zeilen zählt, verletzt direkt die AGENTS-Regel „A tool must never lie about
   a limit". Präzedenz: R-OD-6 hat genau diese Bug-Klasse (Zeilenlängen-Cap + Disclosure) für Grep
   gefixt — Shell ist der Nachzügler.
2. **Pauls TrimService-Gedanke: ja, gerechtfertigt** — aber als **kleine geteilte core-Komponente**
   (shared-Package, z. B. `OutputTrim`/`TrimmedResult`), nicht als „Service" mit Lifecycle. Das
   Trim/Tail-Konzept (Zeilen mit literal `\n` reassemblieren) wurde schon 2026-10-01 als
   „gehört in eine gekapselte Klasse, nicht repliziert" vermerkt (TrimService-Story, 2026-09-29).
   „One behaviour, one implementation": Shell-Char-Cap baut die Komponente, Grep (R-OD-6, Zeilen-Cap
   2000) und Read (Clamp) migrieren später je eigenes Inkrement — nicht alles im ersten Slice.
3. **SOLL 3 (volle Ausgabe als Temp-Datei)** ist das eigentlich wertvolle Detail: nichts geht verloren,
   der Kontext bleibt klein, der Agent kann per Read nachlesen. Shell schreibt nach tmpdir — kein
   Write-Validator-Konflikt, Pfad-Disclosure reicht.
4. **SOLL 5 (Ringpuffer)** folgt aus 2: Kopf+Ende puffern statt alles im Heap.
5. Werte (20.000 total / 500 je Zeile) sind vernünftige Defaults — aber siehe Frage Q2 (Grep nutzt
   2000 je Zeile; vereinheitlichen oder nicht?).

## SOLL-Draft (aus dem Request, 1:1 übernommen)

1. **Harte Zeichengrenze** für die Rückgabe (Vorschlag 20.000) zusätzlich zur Zeilengrenze; bei
   Überschreitung Kopf+Ende kürzen + Vermerk `… (truncated: <total> chars total, <shown> shown)`.
2. **Zeilen-Obergrenze** (Vorschlag 500 je Zeile), gekürzt mit `…(+N chars)`.
3. **Volle Ausgabe als Temp-Datei** bei Kürzung, Pfad im Ergebnis nennen.
4. **Konstante umbenennen**: `MAX_OUTPUT_LENGTH` → `MAX_OUTPUT_LINES` (der Name lügt).
5. **Reader begrenzen**: ab der Zeichengrenze nur Kopf+Ende puffern.
6. **Tool-Beschreibung schärfen**: „Nur für Build, Test und git. Dateien lesen/suchen → Read/Grep-Tools
   — Ausgabe wird ab N Zeichen gekürzt."
7. *(Optional)* Binäre/minifizierte Zeilen nur als Hinweis (`<line 42: 3.1 MB, likely minified/binary — omitted>`).

## BDD (Draft, aus dem Request)

- GIVEN ein Kommando gibt eine Zeile mit 3 Mio. Zeichen aus WHEN `shellRunCommand` läuft
  THEN Ergebnis ≤ 20.000 Zeichen + Kürzungsvermerk mit Gesamtlänge + Pfad zur vollständigen Ausgabe.
- GIVEN 100 Zeilen à 80 Zeichen WHEN `shellRunCommand` läuft THEN unverändert, ohne Vermerk.
- GIVEN der Timeout greift bei übergroßer Ausgabe WHEN die Teilausgabe gebaut wird THEN dieselben Grenzen.

## Offene Fragen (Paul, morgen)

- **Q1 Rollout-Scope:** Nur Shell im ersten Slice (Komponente entsteht neben Shell), oder sofort
  Grep (R-OD-6) + Read mit migrieren? Empfehlung: erst Shell, Migration je eigenes Inkrement.
- **Q2 Werte:** 20.000/500 für Shell — und Grep-Zeilen-Cap 2000 vereinheitlichen oder kontextabhängig lassen?
- **Q3 Namensfindung:** `OutputTrim` (shared-Komponente) vs. `TrimService` vs. `TrimmedResult` —
  Kernproblem, das die Komponente löst: ehrliche Cap-Disclosure + Zeilen-Reassembly an EINER Stelle.
- **Q4 Temp-Datei-Detail:** Pfad im Tool-Result reicht, oder zusätzlich Zeilenbereich-Hinweis
  („full output lines 1–116 in …")?