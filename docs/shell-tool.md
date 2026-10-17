# Shell Tool

Read-only diagnostic shell (`mvn`, `git`, `npm`, …) — **nicht** für File I/O (Read/Write-Tools
dafür). Output-Kontrolle (Char-Cap, Filter) macht das Tool selbst, nicht Shell-Pipes
(`| tail`, `| grep` im Command sind die Umwege, die bei Timeout den Buffer verlieren).

**2026-10-17 (Output-Trim-Service Slice 1):** `tailLines` → `maxChars` (Clean Break), Kürzung
über die geteilte Komponente `OutputTrim` (core/shared) — Details:
[output-trim-service.md](output-trim-service.md).

## Business Rules

### R1 — Char-Cap via `maxChars` (✅ done, 2026-10-17; ersetzt tailLines/60/3000)

`maxChars` steuert, wie viele **Zeichen** des Outputs zurückgegeben werden — Kürzung macht
ausschließlich `OutputTrim` (Head 1/3 + Tail 2/3, Je-Zeile-Cap 500, Disclosure nennt total/shown):

- **kein Parameter oder `<= 0`** → Default **10.000** Zeichen (Context-Schutz).
- **`maxChars > 0`** → genau dieses Zeichen-Budget.
- **KEIN Hard-Cap** (Paul 2026-10-16) — ein explizites großes `maxChars` wird nicht heimlich
  gekappt; der Hard-Cap kommt mit dem [TempFileTool](temp-file-tool.md) (🚧, Slice 2).

```
GIVEN ein Command mit 100 Zeilen Output
WHEN shellRunCommand ohne maxChars
THEN max. 10.000 Zeichen werden zurückgegeben
AND Kürzung wird benannt („… (truncated: N chars total, M shown)")

GIVEN ein Command mit kleinem Output (< Cap)
WHEN shellRunCommand ohne maxChars
THEN der volle Output kommt unverändert (kein Disclosure-Noise)

GIVEN ein Command mit 100 Zeilen Output
WHEN shellRunCommand mit maxChars=20
THEN Head/Tail-Excerpt auf 20 Zeichen AND Disclosure nennt total/shown
→ maxCharsHeadTailExcerpt

GIVEN ein Command mit riesigem Output
WHEN shellRunCommand mit maxChars=500000
THEN der Output wird NICHT auf 10.000 zurückgestutzt — kein Hard-Cap
```

Tests: `maxCharsHeadTailExcerpt`, `runOsCommand_filterWithMaxChars`, `filterRunsBeforeTrim`
(ShellToolTest, core).

### R2 — Filter-Parameter (✅ done, 2026-09-04; Pipeline 2026-10-17 präzisiert)

Optionales `filter` (String) = „| grep" vom Tool selbst:

- **Regex-first mit Literal-Fallback** — dasselbe bewährte Muster wie die Grep-/Console-Tools
  ([ADR-0035](adr/0035-grep-regex-first-literal-fallback.md)); der **Suchmodus** (regex/literal)
  wird im Output disclosed.
- Filter trifft **pro Zeile** (Zeile enthält Match).
- **Pipeline: filtern → Char-Trim auf die gefilterten Zeilen** (Filter läuft VOR dem
  `OutputTrim`-Fenster, am Reader; `eclipseReadConsoleLog` R3 analog).
- **Disclosure (Repo-Vertrag):** aktiver Filter wird immer im Output benannt:
  `filter: <pattern> (showing N of M lines)` — ein stummer Filter wäre ein Falsch-Negativ.
  Filter- **und** Trim-Vermerk stehen in derselben Ausgabe, wenn beide greifen
  (`runOsCommand_filterWithMaxChars`).

```
GIVEN ein Command mit 100 Zeilen, 10 davon enthalten „ERROR"
WHEN shellRunCommand mit filter="ERROR"
THEN nur die 10 passenden Zeilen werden zurückgegeben (unter Beachtung des Char-Caps)
AND der Output benennt Filter + Suchmodus („filter: ERROR (regex, showing 10 of 100 lines)")

GIVEN ein Command mit 100 Zeilen, keine passt zum Filter
WHEN shellRunCommand mit filter="KEINMATCH"
THEN der Output meldet „0 of 100 lines" — kein stilles Leere

GIVEN ein Filter mit Regex-Metacharakttern, die nicht compilieren
WHEN shellRunCommand mit filter="[ungültig"
THEN Literal-Fallback: exakte Zeichenfolge wird gesucht
AND der Output benennt den Modus („literal")

GIVEN gefilterte Zeilen überschreiten zusammen maxChars
WHEN shellRunCommand mit filter + maxChars
THEN Filter-Disclosure UND Trim-Vermerk erscheinen beide („showing N of M" + „truncated:")
→ runOsCommand_filterWithMaxChars
```

### R3 — workingDirectory-Default (✅ done, 2026-09-24, Paul; Review Da Dok ACCEPTED, Commit `c06efcb`)

Fehlende `workingDirectory` darf **nicht** in den Process-CWD laufen (heute `ShellTool.java:74`:
`Path.of(".")` → `/Applications/Eclipse.app/.../MacOS/.` — der Prompt zeigte genau das). Stattdessen:

- Default = **Disk-Pfad des aktiven Projekts** (`aiService.getProject()`-Semantik — der Pin
  fließt ein: gepinntes Projekt bleibt auch bei Selektionswechsel).
- Call-Zeit-Evaluation (Supplier am `ShellTool`, Wiring im Plugin) — gleiches Muster wie
  R-TC-7: kein eingefrorenes Feld, Projektwechsel wirkt ohne Refresh.
- Kein Projekt gewählt → unverändert `.`; der **effektive** Pfad wird im Output genannt
  (`cwd=...`), nie still (Repo-Vertrag: kein stilles Umfeld).
- Explizit übergebene Pfade bleiben unverändert (inkl. ehrlicher Fehler bei ungültigem Pfad).

```
GIVEN Projekt „llmpeon-parent" gewählt (gepinnt) WHEN shellRunCommand ohne workingDirectory
THEN der Command läuft im Disk-Pfad des Projekts AND der Output nennt den effektiven Pfad

GIVEN Selektion wechselt auf ein anderes Projekt WHEN neuer Shell-Call ohne workingDirectory
THEN der neue Projekt-Disk-Pfad gilt (Call-Zeit, kein Reload)

GIVEN kein Projekt gewählt WHEN shellRunCommand ohne workingDirectory
THEN Process-CWD wie heute AND der Output nennt ihn („cwd=...")

GIVEN shellRunCommand mit explizitem workingDirectory
THEN verhält sich unverändert (Default greift nicht)
```

## Notes

- Das alte Zeilen-Modell (`tailLines`, Default 60, Hard-Cap 3000, `<=0 = all`) ist mit
  2026-10-17 ersatzlos entfernt — kein Alias, kein Migration-Pfad (Clean Break).
- `commandUsesShellTail`-Hint (Timeout ohne Output wegen `| tail`-Buffering) bleibt; mit dem
  `filter`-Parameter verliert `| grep` im Command zusätzlich seinen Sinn — Hint kann erweitert werden.
