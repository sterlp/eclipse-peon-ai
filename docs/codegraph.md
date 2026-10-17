---
idPrefix: CG
---

# Codegraph — Built-in-Integration (MCP, Auto-Enable)

> **Status:** ✅ done (2026-10-17, Branch `story/night-5-2026-10-16`, Surefire core 1172/0 ·
> OSGi 376/0, Da-Dok-Review ACCEPTED nach Rework). **Architektur-Doc:**
> [codegraph-architektur.md](codegraph-architektur.md). **ADR:** [ADR-0068](adr/0068-codegraph-builtin-connect-path.md).

## Ziel

Codegraph (https://github.com/colbymchenry/codegraph) ist ein **lokaler Knowledge-Graph-Server**:
tree-sitter-Extraktion aller Code-Symbole, Call-Edges und Dependencies in eine SQLite-DB (FTS5,
kein BM25), 100 % lokal, betrieben als **MCP-Server über STDIO** (`codegraph serve --mcp`), mit
eigenem Background-Daemon pro Projekt, der den Index auto-synct (File-Watcher).

Peon macht daraus ein **Built-in-Feature**: aktives Projekt mit `.codegraph`-Index → Codegraph-MCP
verbindet sich **selbständig und automatisch** (kein Workaround-Eintrag wie das frühere
`codegraph.sh`), Tools stehen allen Agenten zur Verfügung, Projekt-Info weist auf Pfade und
Nutzung, Header-Icon zeigt den Zustand. **Vollständig unabhängig vom MCP-Toggle** (Paul):
MCP ist User-Config, Codegraph ist built-in — sonst bräuchten die Codegraph-Settings selbst einen
Workaround.

**Installation/Init = User-Aufgabe** (`codegraph` installieren, `codegraph init` im Projekt);
Peon hat **keinen** Init-Trigger. Ist es da, nutzen wir es automatisch — ist es nicht da, ist das
Feature einfach aus (kein Fehler-Spam, Tooltip weist darauf hin).

## Fakten (recherchiert 2026-10-17, Da Sniffa)

- MCP-STDIO-Server, Command `codegraph serve --mcp`; Workdir-Abhängigkeit: Server arbeitet im
  Projektverzeichnis (`.codegraph/` liegt dort). langchain4j **1.22.0-beta32** (im Classpath) hat
  `StdioMcpTransport.Builder.workingDirectory(Path)` — `codegraph.sh` (cd + exec) wird überflüssig.
- Default-Toolset: **ein** Tool `codegraph_explore` (Query → verbatim source grouped by file,
  call paths, blast radius). Weitere (`codegraph_node/search/callers/impact/files/status`) nur per
  Umgebungsvar `CODEGRAPH_MCP_TOOLS`.
- Server liefert Initialize-**Instructions** (`McpClient.instructions()` — serverseitige Agenten-
  Guidance, „single source of truth" beim Server) → Peon hardcodiert keinen Nutzungstext.
- Parameter `projectPath` erlaubt Abfrage fremder `.codegraph`-Projekte.
- „One live MCP writer per project": weitere `serve --mcp`-Instanzen proxyen auf den Daemon —
  mehrere Clients sind by design sicher; Peon-Disziplin = exakt **ein** eigener `serve --mcp`
  pro workDir.
- Index-Erkennung: `.codegraph/codegraph.db` (SQLite, WAL-Mode); das Verzeichnis allein ist ein
  Init-Transient und zählt nicht.
- Binary-Resolution deckt `which` ab (verifiziert 2026-10-17 auf Pauls Maschine,
  `/Users/sterlp/.local/bin/codegraph`): Suchort #1 = Install-Default `~/.local/bin/codegraph`,
  danach kompletter PATH-Scan. **Bewusst KEIN** rekursiver User-Home-Scan (teuer,
  false-positive-Anfällig); fehlende Install-Orte bei echtem Bedarf geordnet in
  `DefaultCodegraphProbe` ergänzen. Windows (`.exe`/`.cmd`-Shims) = Follow-up N1.



## Business Rules

### R-CG-1 — Built-in Auto-Connect (unabhängig vom MCP-Toggle) ✅

GIVEN Codegraph installiert + `codegraph init` im aktiven Projekt
WHEN das Projekt aktiv wird
THEN verbindet Peon automatisch einen STDIO-MCP-Server `codegraph serve --mcp`
     (Name `codegraph`, workDir = Disk-Path des Projekts) und die Tools stehen **allen** Agenten
     zur Verfügung.

GIVEN der MCP-Toggle (User-MCP-Server) ist aus
WHEN Codegraph-Projekt aktiv
THEN ist Codegraph trotzdem verbunden (Built-in ≠ User-MCP-Config).

GIVEN Binary fehlt oder kein `.codegraph`-Index
WHEN Projekt aktiv wird
THEN kein Verbindungsversuch, State OFF, kein wiederholter stiller Launch-Versuch.

### R-CG-2 — Probe (Erkennung) ✅

GIVEN das aktive Projekt hat ein `.codegraph/`-**Verzeichnis**, aber keine `codegraph.db`
WHEN Probe läuft
THEN gilt Codegraph als **nicht vorhanden** (Init-Transient).

GIVEN Binary ist nicht auffindbar (Install-Default + PATH)
WHEN Probe läuft
THEN State OFF; Probe ist rein Path-basiert (core, JUnit5-testbar, Eclipse-frei).

### R-CG-3 — Failure-Isolation ✅

GIVEN der Codegraph-Connect schlägt fehl (Launch/Timeout/Fehler)
WHEN der Apply-Pfad läuft
THEN trifft der Fehler **nur** Codegraph — User-MCP-Server bleiben verbunden;
     State = FEHLER, Tooltip nennt den Grund.

### R-CG-4 — Lifecycle: aktives Projekt, kein Churn ✅

GIVEN Projekt A (mit Codegraph) ist verbunden
WHEN Projekt B aktiv wird (Navigation; Pin ist nur Selection-Freeze, keine Voraussetzung)
THEN disconnect A, connect B mit workDir(B) — via bestehenden Apply-Compare, nur bei echter
     Änderung (kein Reconnect-Churn), Apply-Job koalesziert schnelle Projektwechsel.

GIVEN A→B→A-Flap
WHEN der letzte Apply A verbunden hat
THEN kein zusätzlicher Reconnect (Compare).

Invarianten: exakt **ein** Peon-`serve --mcp` pro workDir; der Daemon gehört Codegraph — Peon
startet/tötet ihn nie (Index-State, lebt über Peon hinweg). *(Umsetzungs-Klarstellung: Trigger
ist der Projektwechsel (`setProject` mit changed=true). Ein bewusster Pin-ON-Reprobe entfällt —
Pin-ON ohne Projektwechsel ist kein Zustandswechsel, der Apply-Compare würde früh zurückkehren;
Mutations-Nachweis für den Compare: `CodegraphConnectionServiceTest`, beide Compare-Richtungen
falsifiziert.)*

### R-CG-5 — Config: KEINE UI-Config in v1 ✅

GIVEN Codegraph-Installation + Index im aktiven Projekt
THEN verbindet sich das Built-in **selbständig** — v1 hat **keine** UI-Config
     (Checkbox/Command/Env-Felder sind **out of scope**, Paul 2026-10-17); „aus" macht man
     durch Entfernen (`codegraph uninit`/Deinstall).

*(Ursprünglich war eine Checkbox auf der MCP-Seite vorgesehen — Paul setzte sie für v1 ab:
wenn Codegraph nicht installiert ist, ist das Feature eh aus; wer es bewusst ausschalten will,
entfernt es. `CODEGRAPH_MCP_TOOLS` bleibt v1 ohne UI-Escape-Hatch — Follow-up falls Bedarf.)*

### R-CG-6 — Toolset: nur `codegraph_explore` ✅

WHEN Codegraph verbunden
THEN ist das Toolset die Codegraph-Default-Liste (nur `codegraph_explore`); die weiteren
     Codegraph-Tools bleiben deaktiviert. Die Sichteauswahl folgt dem bestehenden `ToolPolicy`
     (Default-Agenten: automatisch sichtbar; explizite Tools-CSV braucht ein `codegraph`-Token).

### R-CG-7 — Projekt-Info: Pfade + Nutzung (statt Pfad-Translation) ✅

GIVEN Codegraph ist verbunden
WHEN die Projekt-Info gebaut wird
THEN enthält sie: (a) Hinweis, dass `codegraph_explore` absolute **Disk-Pfade** liefert und für
     eclipse*-Tools `<diskPath>` → `/Projektname` zu ersetzen ist (Pfade außerhalb des Projekts
     werden **nicht** übersetzt — dort disk*-Tools mit Originalpfad); (b) Hinweis auf
     `codegraph_explore` inkl. kurzer Nutzungsinfo (ein Aufruf statt Such-Loop, verbatim source).
     Die serverseitigen Initialize-Instructions werden zusätzlich als Context injiziert
     (`McpClient.instructions()`).

GIVEN Codegraph aus/nicht vorhanden
WHEN Projekt-Info gebaut
THEN erscheint keine Codegraph-Zeile (keine Kontext-Blähung).

**KEIN** Tool-Output-Postprocessing (kein Umschreiben gelieferter Pfade — Tool-Honesty). Die
Homepage-Aussage (eclipse*-Tools übersetzen keine Pfade) bleibt unverändert gültig; Disk→Workspace
hier nur als Agent-Hinweis. *(Pauls ursprüngliche Idee „Ergebnisse in Eclipse-Pfade ersetzen" =
Bewertungs-Option S2, natives Wrapper-Tool `codegraphExplore` mit echter Übersetzung + Disclosure
— nur bei nachgewiesenem Praxis-Problem.)*

### R-CG-8 — Header-Icon ✅

GIVEN aktives Projekt mit Codegraph-Zustand
THEN zeigt der Header (neben dem Hammer) das Icon `icons/codegraph.svg` mit Zustand:
     **ON** (verbunden) · **OFF** (nicht vorhanden/Probe negativ) · **FEHLER** (Connect-Fehler),
     Tooltip mit Projektname + Grund (bei OFF/kein Index: „installieren + `codegraph init`").
     Kein Klick-Verhalten in v1. Zustandsupdates auf dem UI-Thread.

### R-CG-9 — Kein Init/Index-Trigger aus Peon ✅

WHEN Codegraph fehlt (Binary oder Index)
THEN startet Peon **keine** `codegraph install`/`init`/`index`-Aktion — User-Aufgabe,
     communicated via Tooltip + Homepage/README.

### R-CG-10 — Kein Telemetry-Override ✅

WHEN Peon den Codegraph-Server spawnt
THEN setzt Peon **kein** `CODEGRAPH_TELEMETRY` (kein Default-Env) — die Telemetry-Entscheidung
     des Users am Binary (persistiert in codegraphs Config) gilt unverändert.

### R-CG-11 — Dedup: Built-in verdrängt manuellen `codegraph`-Eintrag ✅

GIVEN ein manuell konfigurierter User-MCP-Server mit Name `codegraph` existiert (Workaround
     `codegraph.sh`) und das Built-in ist aktiv
WHEN Apply
THEN gewinnt das Built-in — kein doppelter `codegraph_explore` im Toolset. Homepage/README:
     alten Eintrag entfernen. Clean Break, keine Migration.

Umsetzung (Jon-Entscheid (a), 2026-10-17, ⏳-Rückversicherung in open-points.md): der Name
`codegraph` ist im User-MCP-Pfad **unbedingt** reserviert (Filter in
`McpConnectionService.connectServers`, Log-Zeile) — auch bei inaktivem Built-in. Randwirkung:
ein reiner Workaround-Eintrag funktioniert nicht mehr, sobald diese Version läuft; Paul kann
beim Review auf State-abhängigen Filter umstellen.

## Technik (WIE, ohne Architektur-Doc-Status)

- `McpServerConfig` bekommt Feld `workingDirectory` (**leer = unset**, „Empty means unset" —
  erbt das Prozess-Working-Dir wie heute); `McpService.buildStdioMcp` setzt es nur bei non-blank.
- Probe + Server-Factory im **core** (`org.sterl.llmpeon.mcp`): `CodegraphProbe` (Binary-Resolution
  + Index-Presence) und `CodegraphServerFactory` (Name/Command/Args/workDir → `McpServerConfig`)
  — rein Path-basiert, JUnit5-testbar.
- Plugin: `CodegraphConnectionService` = Lifecycle-Kleber (Projekt-Events → Probe-Job → State,
  isolierter Connect-Pfad über `ToolService`-MCP-Groups, kein SWT); `McpConnectionService`
  bleibt User-MCP-Apply-Pfad (Group `"user"`).
- Context: `AgentContextComponent` injiziert Codegraph-Info + Server-Instructions per Supplier
  (nur wenn verbunden).
- UI: `HeaderBarWidget` Indikator-Position vor dem Hammer; Icon-Asset in `ImageUtil`
  (`codegraph.svg` committed, `codegraph_dark.svg` nach Hammer-Muster).

## Offene Punkte (Follow-ups, nicht v1)

- UI-Config (Checkbox auf MCP-Seite, evtl. Command/Env-Felder) — Paul 2026-10-17 für v1
  out of scope, Revisit wenn ein echter Abschalt-Bedarf entsteht.
- S2: natives Wrapper-Tool `codegraphExplore` (projectPath vorbelegt, Pfade-Übersetzung mit
  Disclosure) — nur bei Praxis-Befund.
- `CODEGRAPH_MCP_TOOLS`-Env-Feld / weitere Tools aktivierbar — falls Bedarf.
- `workingDirectory` generisch im UI für User-MCP-Server exponieren — falls Bedarf.
- Windows-Binary-Resolution (`.exe`/`.cmd`-Shims) — Probe sucht v1 nur `codegraph`
  (macOS/Linux); eigene Story falls Bedarf.
