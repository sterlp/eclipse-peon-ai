# Maven Pipeline (CI) — headless SWT-Verträglichkeit

CI-Pipeline `.github/workflows/maven.yml`: ubuntu-latest, JDK 21 (temurin), `mvn -B clean verify`,
Trigger push/PR auf `main` (paths-ignore `docs/**`, `skills/**`). CI ist **headless** (kein X).

**Status: 🚧 in Umsetzung (2026-09-10, User-Go für sequenzielles B→A→B-Schema).**

## Bug (2026-09-10, User: „Pipeline kann nicht mehr releasen")

`EclipseConsoleLogToolTest.limitAppliesAfterGrep` failt in CI im **@After** (:24):
`SWTError: No more handles [gtk_init_check() failed]`.

**Ursache (verifiziert, nicht geraten — Bytecode aus beiden Target-JARs):**
- Auslöser = **Target-Sprung 2026-03 → 2026-09** (`89531af`, Zyklus-1 Lib-Update):
  `org.eclipse.ui.console` 3.16.0 → 3.17.100 bringt den **neuen `ConsoleZoomHandler`** mit,
  dessen Listener schon am `ConsoleManager`-Konstruktor hängt.
- `removeConsoles` auf einer `TextConsole` (unsere `PeonTestConsole extends TextConsole`)
  feuert den Listener → `Display.getDefault()` → headless CI ohne X → SWTError.
- Alt-Plattform (3.16.0) hatte den Handler nicht → Pipeline war vorher grün.
- Test-Code/Workflow/MANIFEST auf dem Branch **unangetastet**; lokal (PDE-Workbench mit
  Display) grün — deshalb nie lokal gesehen. CI-only-Bug.
- pom: tycho-surefire `useUIHarness=false`, `useUIThread=false` → headless OSGi-Booter.

## SOLL — sequenzielles Validierungs-Design (User 2026-09-10)

1. **R-PI1 (B) — Display-Guard im Test:** `EclipseConsoleLogToolTest.after()` fängt/kontrolliert
   den headless-Fall — Removal darf in headless CI nicht crashen. Guard feuert lokal nie
   (bewusst — B ist auch der **Validierungs-Vektor**: zeigt, dass genau dieser Pfad es war).
   Mechanics: `SWTError`-catch um `removeConsoles` ODER Display-Probe ohne Device-Creation —
   Da Mek verifiziert am SWT-Quellcode, welcher Probe sicher ist (STOP-AND-ASK bei Unklarheit).
2. **R-PI2 (A) — xvfb in der Pipeline:** NACH User-Bestätigung „B: Fehler weg in CI" wird B
   **deaktiviert** (Validierung, dass A ALLEINE genügt — B würde maskieren) und `maven.yml`
   läuft `xvfb-run -a mvn -B clean verify`. Kanonisch für SWT in CI: CI bekommt ein Display,
   volle Suite inkl. Display-Tests, kein toter Guard dauerhaft.
3. **R-PI3 — B zurück mit Kommentar:** Nach A-Bestätigung wird der B-Guard **wieder
   eingeführt mit Kommentar** (Defense-in-Depth: falls CI mal ohne Display läuft).
4. **R-PI4 — Validierung je Schritt:** Pipeline-Run muss grün sein; Ergebnis wird hier
   festgehalten (Datum + CI-Lauf).

## Validierungs-Log

| Schritt | Änderung | CI-Ergebnis | Datum |
|---|---|---|---|
| B (Guard) | `EclipseConsoleLogToolTest.after()` Display-Guard | ✅ grün (User-CI-Lauf) | 2026-09-10 |
| A ohne B (R-PI2) | `xvfb-run` in maven.yml, Guard deaktiviert | ⏳ offen | — |
| B wieder aktiv + A | Guard zurück mit Kommentar | ⏳ offen | — |

## Notes

- Revert des Target-Sprungs ist **keine** Option (Plattform-Verhalten, kein Fehler in
  `89531af`; der Sprung ist das eigentliche Lib-Update-SOLL).
- AGENTS „Log OR throw": Guard loggt (WARN/INFO) und wirft nicht — im @After darf nichts
  werfen, das die Suite rot färbt.
- Workflow-Namen: `maven.yml` heißt intern „build and deploy Peon AI" — Release-Gate = grüner
  Build auf main.