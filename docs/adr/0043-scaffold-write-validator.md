# ADR-0043 — Scaffold-Write-Scoping via WriteValidator statt Instanz-Merge

**Status:** Accepted (2026-09-08) · Umsetzung: ausstehend — Story [project-skills.md](../project-skills.md)

## Context

Der Scaffold-Agent schreibt via **eigene** `DiskFileWriteTool`-Instanzen (`workingDir=configDir`,
pro Call gepinnt) und erbt `WriteValidator.ALLOW_ALL` — die Config-Begrenzung ist nur workingDir
(weich: `FileUtils.resolve` lässt absolute Pfade und `..`-Escapes durch) + Prompt-Regel. SOLL:
Scaffold darf zusätzlich `<Projekt>/.agents/skills` beschreiben. Optionen: (a) Scaffold auf die
Shared-Write-Tools umziehen, (b) WriteValidator mit dynamischen Roots auf den eigenen Instanzen.

## Decision

**(b)** — Scaffold bekommt einen `getWriteValidator()`-Override (gleicher Mechanismus wie Jons
`WriteValidator.DOCS`): erlaubte Roots = `configDir` + `<offenes Projekt>/.agents/skills` für **alle
offenen Projekte** (dynamisch via Supplier zur Call-Zeit — keine statischen Globs). Die eigenen
Tool-Instanzen bleiben, **kein** Merge mit den Shared-Tools. WEIL: Skills können für ein nicht
aktuell gewähltes Projekt angelegt werden — mit nur einem Root müsste der User erst umschalten.
Eng gescoped: nur die Skills-Dirs der Projekte, kein allgemeines Projekt-Schreibrecht.

**WEIL kein Merge:** der Shared-Write-Tool-WorkingDir springt bei jedem Projektwechsel auf den
Projekt-Pfad — Config-Schreibzugriffe des Scaffold wären danach nur noch mit absoluten Pfaden
möglich. Eigene Instanzen sind zudem KV-Cache-freundlich und von `setProject` unberührt (der
Scaffold pinnt configDir ohnehin pro Call neu). Der Validator setzt am bestehenden Choke-Point
(`AbstractTool.validateWrite`) den harten Riegel, den workingDir heute nicht gibt.

**Rollen-Trennung:** workingDir = **nur Auflösungsbasis** für relative Pfade (keine Boundary —
`FileUtils.resolve` lässt absolute Pfade und `..`-Escapes durch); die harte Grenze liegt allein
im Validator. Die Prompt-Regel („nur Config-Dir/Projekt-Skills") bleibt als Self-Restriction-Hilfe
stehen — kein zweiter Enforcement-Mechanismus, dieselbe Logik an einem Ort.

## Consequences

- `WriteValidator`/`AllowlistWriteValidator` braucht dynamische Roots (Supplier) — kleine
  Erweiterung; Jons `DOCS`-Verhalten bleibt unverändert.
- Denials laufen durch den bestehenden AI-sichtbaren Tool-Fehler-Pfad (`onProblem`).
- Zwei Roots (configDir, Projekt-Skills-Dir) — Prompt-Regel und Validator müssen konsistent bleiben.
