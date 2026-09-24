# Shell Tool

Read-only diagnostic shell (`mvn`, `git`, `npm`, …) — **nicht** für File I/O (Read/Write-Tools
dafür). Output-Kontrolle (tail-Limit, Filter) macht das Tool selbst, nicht Shell-Pipes
(`| tail`, `| grep` im Command sind die Umwege, die bei Timeout den Buffer verlieren).

## Business Rules

### R1 — Tail-Limit (✅ done, 2026-09-04)

`tailLines` steuert, wie viele der **letzten** Zeilen zurückgegeben werden:

- **kein Parameter** → letzte **60** Zeilen (Default = Context-Schutz).
- **`tailLines > 0`** → letzte N Zeilen.
- **`tailLines <= 0`** (0 oder -1) → **alle** Zeilen (explizite Entscheidung des LLMs).
- **Hard-Cap 3000 Zeilen** — darüber wird gekürzt und als `... (N lines skipped)` disclosed
  (Repo-Vertrag: jede Kürzung wird benannt).

```
GIVEN ein Command mit 100 Zeilen Output
WHEN shellRunCommand ohne tailLines
THEN die letzten 60 Zeilen werden zurückgegeben
AND der Output benennt die Kürzung („40 lines skipped")

GIVEN ein Command mit 100 Zeilen Output
WHEN shellRunCommand mit tailLines=20
THEN die letzten 20 Zeilen werden zurückgegeben

GIVEN ein Command mit 100 Zeilen Output
WHEN shellRunCommand mit tailLines=-1 (oder 0)
THEN alle 100 Zeilen werden zurückgegeben

GIVEN ein Command mit 4000 Zeilen Output
WHEN shellRunCommand mit tailLines=-1
THEN 3000 Zeilen werden zurückgegeben
AND der Output benennt die Kürzung („1000 lines skipped")
```

**✅ fixed (Bug-Hunt #2, 2026-09-04):** `-1` wurde heute auf Default 50 gemappt (`ArgsUtil.getOrDefault`
mappt `<= 0` auf Default) — die innere Methode, die `<= 0 = all` korrekt umsetzt, ist unerreichbar.
Fix: `-1`/`0` als „all" durchreichen; Default wird 50 → 60.

### R2 — Filter-Parameter (✅ done, 2026-09-04 — neu)

Optionales `filter` (String) = „| grep" vom Tool selbst:

- **Regex-first mit Literal-Fallback** — dasselbe bewährte Muster wie die Grep-/Console-Tools
  ([ADR-0035](adr/0035-grep-regex-first-literal-fallback.md)); der **Suchmodus** (regex/literal)
  wird im Output disclosed.
- Filter trifft **pro Zeile** (Zeile enthält Match).
- **Pipeline: filtern → tail-Limit auf die gefilterten Zeilen** (wie `eclipseReadConsoleLog` R3:
  das Limit trifft das Gefilterte). Gilt für alle Pfade: Erfolg, Timeout, Fehler.
- **Disclosure (Repo-Vertrag):** aktiver Filter wird immer im Output benannt:
  `filter: <pattern> (showing N of M lines)` — ein stummer Filter wäre ein Falsch-Negativ.

```
GIVEN ein Command mit 100 Zeilen, 10 davon enthalten „ERROR"
WHEN shellRunCommand mit filter="ERROR"
THEN nur die 10 passenden Zeilen werden zurückgegeben (unter Beachtung des tail-Limits)
AND der Output benennt Filter + Suchmodus („filter: ERROR (regex, showing 10 of 100 lines)")

GIVEN ein Command mit 100 Zeilen, keine passt zum Filter
WHEN shellRunCommand mit filter="KEINMATCH"
THEN der Output meldet „0 of 100 lines" — kein stilles Leere

GIVEN ein Filter mit Regex-Metacharakttern, die nicht compilieren
WHEN shellRunCommand mit filter="[ungültig"
THEN Literal-Fallback: exakte Zeichenfolge wird gesucht
AND der Output benennt den Modus („literal")
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

- Hard-Cap 3000 bleibt auch bei `tailLines <= 0` — explizites „all" darf den Context nicht
  sprengen; die Kürzung ist disclosed. (User-Freigabe 2026-09-04: Default 60, <=0 = all, filter = Regex.)
- `commandUsesShellTail`-Hint (Timeout ohne Output wegen `| tail`-Buffering) bleibt; mit dem
  `filter`-Parameter verliert `| grep` im Command zusätzlich seinen Sinn — Hint kann erweitert werden.
