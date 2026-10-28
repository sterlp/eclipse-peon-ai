# Built-in Agent Prompt Override

**Status: 🚧 in design (Paul, 2026-09-06 / geschärft 2026-10-27)** — Richtung entschieden, noch keine
Rules/BDD. Gilt für **beide Produkte** (Plugin und Web/OAF) — eine Core-Fähigkeit nach GR-3.

## Ziel

Jeder Agent — Built-ins (Jon/PO, Plan, Dev, Review …) genauso wie Custom Agents und Paco — ist
**eine Datei** im Agenten-Ordner: `agents/<id>/AGENT.md`, Pseudo-yml-Kopf + Markdown-Rumpf, dasselbe
Format wie Custom Agents heute. Kein Sonderformat, keine Sonderwurst für Built-ins.

## Entschieden (Paul 2026-10-27)

- **Rumpf vorhanden → er ist der Prompt** und ersetzt den eingebauten vollständig (Full-Replace).
- **Rumpf leer / Datei fehlt → eingebauter Prompt.**
- **Ausbaustufen:**
  - **V1:** nur der Rumpf zählt — man kann so den Prompt eines Built-ins setzen. Die Config
    (Modell, Temperatur, Tools …) bleibt wo sie ist.
  - **V2:** die ganze Config eines Agenten kommt aus dem Kopf seiner Datei. **Datei sticht**, Eclipse-
    Prefs nur noch `@Deprecated`-Lese-Fallback; **Speichern schreibt immer komplett in die Datei**
    (Konverter Eclipse → Datei beim Speichern) — [ADR-0077](../../docs/adr/0077-agent-config-file-first-eclipse-fallback.md).
    Der Default gilt für alle, je Agent per „überschreiben" abweichend (Web: Checkbox im Config-UI,
    Abwählen löscht die Config-Werte aus der Datei — [R-WP-23](../../aifactory/docs/config.md)).
- **Web/OAF:** nur File-Config, der Agenten-Ordner ist Teil des git-sicherbaren Config-Verzeichnisses.
- **Kein Staleness-Schutz:** wer einen Built-in überschreibt, übernimmt ihn — Verbesserungen am
  eingebauten Prompt erreichen ihn nicht, es gibt keinen Hinweis und kein Merge. Vergleich über git.

## Agent-Datei: id · name · description · isTool (Paul 2026-10-27, Ziel V2)

Wie bei Skills: **id** = Verzeichnisname, **name** = Anzeige, **description** = Pflicht für jeden
Agenten (braucht das generische Delegate-Tool, um ihn anzubieten). **isTool** = der Agent ist für
andere Agenten aufrufbar und wandert damit ins generische Agent-Tool-System
([async-agents.md](async-agents.md), `AgentsTool`).

| id | name (Anzeige) | isTool | Bemerkung |
|---|---|---|---|
| `jon` | Jon | – | einziger Persona-Name; heute Config-ID `po` |
| `plan` | Plan | ✓ | im Plugin auch direkt im UI wählbar |
| `dev` | Dev | ✓ | im Plugin auch direkt im UI wählbar |
| `review` | Review | ✓ | im Plugin auch direkt im UI wählbar |
| `search` | Search | ✓ | heute `SearchAgentTool`, kein Agent |
| `scaffold` | Scaffold | – | heute ohne Config-Slot |
| `paco` | Paco | – | fachlicher PO; Prompt im Core, aktiv nur im Web ([aifactory/docs/paco.md](../../aifactory/docs/paco.md)) |

Die Team-Namen **Da Thinka · Da Mek · Da Dok · Da Sniffa entfallen in V2** — nur Jon behält seinen
Namen (Persönlichkeit). Folge: Prompts (`po.md`, Delegate-Tool-Beschreibungen) und Web-Chat-Absender
(R-WP-4) zeigen dann die Anzeigenamen.

## Abgrenzung

- `AGENTS-<agent>.md` im **Projekt** ([agent-specific-agentsmd.md](agent-specific-agentsmd.md)) bleibt
  eine **Ergänzung** je Projekt (Kontext in der History). `agents/<id>/AGENT.md` **ersetzt** den
  System-Prompt, global. Zwei verschiedene Dinge — nicht verwechseln.

## Offen

- **`compact`** ist heute ein Config-Slot, kein Agent — bekommt er eine Datei?
- **id `jon` statt `po`:** Config-Keys `llm.agent.po.*`, `AGENTS-PO.md`-Auflösung, History-Ordner —
  Da Mek prüft vor der Planung, was am alten Namen hängt.
- **Namenskollision heute:** ein Custom Agent mit dem Namen eines Built-ins überschreibt diesen in
  `AgentService.reloadAgentConfig` bzw. erscheint doppelt — kein Guard. Mit diesem Feature wird daraus
  die definierte Regel „Ordner = Built-in-ID ⇒ Override, kein neuer Agent".
- **V2 berührt** R-ATC-1…4 ([agent-tool-config.md](agent-tool-config.md)), R-DEF-* (Plugin
  default-inheritance.md), R-THINK-10 — beim Entwurf von V2 gegenprüfen.
