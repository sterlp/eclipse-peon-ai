---
idPrefix: RP
---
# Read Picture Tool — `diskReadPicture` · `eclipseReadPicture`

**Technische Heimat:** core `org.sterl.llmpeon.tool` (Disk-Tool, geteilte Bild-Logik + Memory-Sequenz),
plugin `org.sterl.llmpeon` (`eclipse*`-Variante). Eine Feature-Doc für beide Familien (Parität wie
[read-tools.md](read-tools.md)). Architektur: [read-picture-tool-architektur.md](read-picture-tool-architektur.md).

**Status:** ✅ done (2026-11-03, uncommitted — Paul committet selbst; Paul-Smoke beider Tools ✅)

**Ziel:** Ein Agent kann sich ein Bild **anschauen** — Screenshot, UI-Test-Ergebnis, Diagramm —
indem er es per Tool lädt. Das Modell bekommt das Bild selbst, nicht nur eine Beschreibung.

## Business Rules

### R-RP-1 — Das Bild kommt als User-Message nach dem Tool-Result, ein Pfad für alle Provider ✅

**SOLL:** Ein erfolgreicher Aufruf von `diskReadPicture` / `eclipseReadPicture` legt im Agent-Memory
genau diese Sequenz an:

1. **Tool-Result (nur Text):** `📷 <Pfad der Familie, R-RP-7> (<Größe in KB>) — picture follows in the next user message`.
2. **Synthetische AI-Message** — wie das bestehende `ok`-Muster des Memory, damit auf ein
   Tool-Result nie direkt eine User-Message folgt.
3. **User-Message** mit getaggtem Kopf (Absender = Tool, Pfad) und dem Bild als Bild-Content.

Mehrere Tool-Results einer Runde bleiben lückenlos hinter der Tool-Call-Anfrage; die Bild-Sequenz
(2+3) wird **nach allen** Results der Runde angehängt, bei mehreren Bildern eine User-Message je Bild
in Aufruf-Reihenfolge.

**WEIL:** Bild-Content im Tool-Result nehmen nur Anthropic, Gemini und die OpenAI-Responses-API an;
OpenAI-Chat-Completions (auch LM Studio, Copilot), Ollama und Mistral lehnen ihn ab (geprüft
langchain4j 1.22.0 und `main` 1.24-SNAPSHOT — ein Update hilft nicht). Bild-Content in einer
User-Message verstehen alle. Eine Sequenz, die für jeden Provider gültig ist, vergiftet das Memory
auch bei einem Provider-/Modellwechsel nicht und bleibt für Persistenz und Compact eine
Standard-Message. Der Provider-Weg (Bild kanonisch im Tool-Result + Umformung je Provider beim
Senden, wie im Agent *pi*) wurde als zu groß für den Nutzen verworfen.

#### UC-RP-1 — Bild auf Ollama ansehen
GIVEN Dev läuft auf Ollama WHEN er `diskReadPicture("ui/login.png")` aufruft THEN steht im Memory
Tool-Result(Text) → AI-Message → User-Message(Kopf + Bild) AND der nächste Request ist gültig.

#### UC-RP-2 — Bild in einer gemischten Runde
GIVEN eine Runde mit `diskGrepFiles`, `diskReadPicture(a.png)`, `diskReadFile` WHEN ausgeführt THEN
folgen alle drei Tool-Results lückenlos auf die Tool-Call-Anfrage AND erst danach AI-Message →
User-Message(a.png).

### R-RP-2 — Formate und Größen-Cap 4 MB: ehrlich abgelehnt, nie verkleinert ✅

- **Formate:** `png`, `jpg`, `jpeg`, `gif`, `webp` — erkannt an der Dateiendung (case-insensitive),
  MIME-Typ daraus abgeleitet.
- **Cap:** Datei-Bytes **> 4 MB** → kein Bild, nur Text-Result
  `picture too large: <x.x> MB, max 4 MB — <Pfad>`. Genau 4 MB ist erlaubt.
- **Kein Resize/Downscale** — das Bild geht unverändert (base64) raus, mit Detail-Level **HIGH**
  (sonst verkleinert z. B. OpenAI/Copilot auf 512 px und ein Screenshot ist unlesbar).
- **WEIL:** Provider lehnen große Bilder ab (Anthropic ~5 MB je Bild); ein zu großes Bild im Memory
  würde jeden Folge-Request scheitern lassen. Ehrlich ablehnen ist billiger als ein vergiftetes Memory.

#### UC-RP-3 — Zu großes Bild
GIVEN `big.png` hat 7 MB WHEN `diskReadPicture` THEN Text-Result `picture too large: 7.0 MB, max 4 MB — <Pfad>`
AND kein Bild im Memory. GIVEN genau 4 MB THEN wird es geladen.

### R-RP-3 — Fehlerfälle: nur Text-Result, keine Bild-Sequenz ✅

Datei fehlt, Endung nicht unterstützt, zu groß (R-RP-2), Lesefehler → **ein** Text-Result mit klarem
Grund; **keine** AI-/User-Message wird angehängt. Nicht unterstützt:
`unsupported picture type: .<ext> (png, jpg, jpeg, gif, webp) — <Pfad>`. Pfad-Auflösung und -Fehler wie bei
`diskReadFile` bzw. `eclipseReadFile` (gleicher Scope, gleiche Meldungen).

#### UC-RP-4 — Falscher Typ / fehlende Datei
GIVEN `notes.txt` WHEN `diskReadPicture` THEN `unsupported picture type: .txt (png, jpg, jpeg, gif, webp) — <Pfad>`
AND keine Bild-Sequenz. GIVEN Datei fehlt THEN dieselbe Meldung wie `diskReadFile`, keine Bild-Sequenz.
(`eclipseReadPicture` löst nur den Pfad auf und delegiert an denselben Core-Weg — automatisiert wird
nur die Disk-Seite getestet, Paul 2026-11-03.)

### R-RP-4 — Compact entfernt das Bild, Platzhalter statt Bild ✅

Beim Compact wird jedes Bild durch den Text `[picture removed for compact: <Pfad>]` ersetzt; der
Compressor bekommt nie Bild-Bytes und kein Bild überlebt einen Compact. Ein Bild aus derselben Runde
wie ein `compactSession`-Aufruf wird erst **nach** dem Compact angehängt und bleibt also sichtbar.
Der Platzhalter bleibt auch erhalten, wenn der Compact stufenweise kürzen muss. Ein automatischer
Compact direkt nach der Bild-Runde entfernt das Bild ebenfalls — der Platzhalter sagt dem Agenten,
dass er es erneut laden kann. Die Kontext-Schätzung zählt
ein Bild pauschal mit **1.600 Tokens** (4.800 Zeichen bei chars/3), nicht mit 0 und nicht mit der
base64-Länge.

#### UC-RP-5 — Compact
GIVEN Memory mit Bild-User-Message WHEN Compact THEN Compressor-Input enthält
`[picture removed for compact: <Pfad>]` und kein Bild AND nach dem Compact ist kein Bild im Memory.

### R-RP-5 — History-Datei behält das Bild, Restore zeigt es wieder ✅

Die Bild-User-Message wird wie jede Message in die Agent-History angehängt (base64 inline, Codec
von langchain4j). Nach Neustart/Restore sieht der Agent das Bild wieder — bis zum nächsten Compact
(R-RP-4). Der Cap aus R-RP-2 begrenzt die Zeilengröße.

#### UC-RP-6 — Restore
GIVEN Agent-History mit Bild-User-Message WHEN Restore THEN steht die User-Message mit identischem
Bild-Content wieder im Memory.

### R-RP-6 — Beide Familien, gleiche Agenten wie die Read-Tools ✅

`diskReadPicture` hat jeder Agent, der `diskReadFile` hat; `eclipseReadPicture` jeder, der
`eclipseReadFile` hat (auch Search und Review) — lesend, kein Write-Validator. Gleiches Verhalten,
gleiche Meldungen, eine geteilte Implementierung im core.

#### UC-RP-7 — Tool-Zuordnung
GIVEN Agent hat `diskReadFile` / `eclipseReadFile` THEN hat er auch `diskReadPicture` /
`eclipseReadPicture`; GIVEN Agent ohne die Read-Familie THEN auch ohne das Picture-Tool.
(Automatisiert für die Disk-Familie; die Eclipse-Methode sitzt in derselben Klasse wie
`eclipseReadFile` und wird damit identisch zugeordnet.)

### R-RP-7 — Jede Familie nennt ihren eigenen Pfad-Dialekt ✅

`eclipseReadPicture` nennt **überall** den Workspace-Pfad (`/projekt/pfad`), `diskReadPicture` den
absoluten Disk-Pfad: im Tool-Result, im Bild-Kopf, im Compact-Platzhalter und in den Fehlermeldungen
(R-RP-2/3). **WEIL:** Eine Familie spricht einen Pfad-Dialekt
([eclipse-read-tools.md](../../org.sterl.llmpeon/docs/eclipse-read-tools.md)) — was ein Tool ausgibt,
muss der Agent unverändert in dieselbe Familie zurückgeben können (Paul, 2026-11-03).

#### UC-RP-8 — Eclipse-Pfad im Ergebnis
GIVEN `eclipseReadPicture("/llmpeon-parent/homepage/src/assets/install.png")` THEN Tool-Result
`📷 /llmpeon-parent/homepage/src/assets/install.png (36.1 KB) — …` AND Bild-Kopf und
Compact-Platzhalter nennen denselben Pfad. GIVEN dieselbe Datei ist 7 MB THEN nennt
`picture too large` ebenfalls den Workspace-Pfad. (Automatisiert im Core über den Anzeige-Pfad.)

## Abschalten für Modelle ohne Vision

Kein Automatismus: der User nimmt die Tools über die Tool Allowlist raus, z. B. `*, !*ReadPicture`
— Glob-Semantik siehe [agent-tool-config.md](agent-tool-config.md) R-ATC-11.

## Backlog / Out of Scope

- **Downscale großer Bilder** (Paul 2026-11-03, zurückgestellt): Bilder > Cap automatisch
  verkleinern statt ablehnen. Nicht trivial — AWT/ImageIO im Eclipse-Prozess (macOS, SWT) und
  OSGi-Import unbelegt, kein WebP in ImageIO, Alpha/CMYK/EXIF-Sonderfälle. Erster Schritt bei
  Wiederaufnahme: Plugin-Testlauf, der ImageIO real aufruft.
- Automatisches Ausblenden je Provider/Modell-Vision — bewusst nicht, Allowlist reicht.
- **Browser-Screenshot-Tool (Eclipse-only, Idee 2026-11-03, geparkt):** Agent öffnet URL im
  SWT-Browser und macht einen Screenshot. Nicht trivial — `Browser` hat keine Screenshot-API, nur
  `print`/`copyArea` (Risiko leeres/schwarzes Bild, v. a. Windows/Linux), sichtbare Shell nötig,
  Eclipse-interner Browser nur über interne API. Erster Schritt bei Wiederaufnahme: Spike-Plugin-Test
  (rote Testseite → Bild nicht einfarbig). Alternative: headless Chrome / Playwright-MCP.

Upstream-Wunsch: [langchain4j-feature-request-image-tool-result.md](langchain4j-feature-request-image-tool-result.md).
