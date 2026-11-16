---
idPrefix: BAP
---
# Built-in Agent Prompt Override

**Status:** V1 (nur der Prompt) ❌ specified (Paul 2026-11-15); V2 (Config aus der Datei) 🚧. Gilt für
**beide Produkte** (Plugin und Web/OAF) — eine Core-Fähigkeit nach GR-3.
Technische Heimat: `org.sterl.llmpeon.core` — `org.sterl.llmpeon.agent` (Built-ins) und Agent-Service.

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

## Regeln V1 — der Rumpf ersetzt den eingebauten Prompt (Paul 2026-11-15)

Die Datei hat dasselbe Format wie ein Custom Agent: `agents/<id>/AGENT.md`, YAML-Kopf + Markdown-Rumpf, im
Konfigurationsverzeichnis (Plugin `~/.peon`, Fabrik ihr Home). Die `<id>` sind die bestehenden Slot-Ids
`po` (Jon), `plan`, `dev`, `review`, `search`; die Umbenennung `po` → `jon` gehört zu V2.

### R-BAP-1 — Ein Rumpf ersetzt den eingebauten Prompt vollständig ❌

Trägt `agents/<id>/AGENT.md` eines Built-ins einen nicht leeren Rumpf, ist dieser Rumpf der ganze
Prompt-Text des Agenten: Er ersetzt alle eingebauten Prompt-Dateien des Agenten — bei Jon die drei Teile
`po.md`, `doc-method.md`, `po-delegation.md`, bei den anderen ihre Prompt-Datei samt vorangestelltem
`default.md`, wo er davorsteht.
Was der Core zur Laufzeit erzeugt, bleibt: der Pfad-Scope aus dem Validator
([write-path-validator.md](write-path-validator.md) R6), der statische Kontext und `AGENTS-<agent>.md`.
Leerer Rumpf oder keine Datei ⇒ eingebauter Prompt. Eine Änderung wirkt nach dem nächsten Neuladen der
Agenten-Config, ohne Neustart. Es gibt keinen Staleness-Schutz (siehe oben).

#### UC-BAP-1 — bodyReplacesBuiltinPrompt ❌

```gherkin
Given "agents/dev/AGENT.md" trägt den Rumpf "Du bist ein Test-Dev."
When der System-Prompt von Dev gebaut wird
Then ist der Prompt-Text genau "Du bist ein Test-Dev."
  And kein Satz aus "developer.md" oder "default.md" ist enthalten
```

#### UC-BAP-2 — jonBodyReplacesAllThreeParts ❌

```gherkin
Given "agents/po/AGENT.md" trägt einen Rumpf
  And Jon schreibt nur in "docs"
When Jons System-Prompt gebaut wird
Then enthält er den Rumpf und den Pfad-Scope des Validators
  And keinen Satz aus "po.md", "doc-method.md" oder "po-delegation.md"
```

#### UC-BAP-3 — emptyBodyKeepsBuiltinPrompt ❌

```gherkin
Given "agents/review/AGENT.md" trägt nur einen Kopf und keinen Rumpf
When der System-Prompt von Review gebaut wird
Then ist es der eingebaute Prompt
```

### R-BAP-2 — Der Core liefert den eingebauten Prompt je Id ❌

Der Core stellt den eingebauten Prompt-Text jedes Built-ins je Id bereit — genau den Text, den R-BAP-1
ersetzt. **WEIL** die Fabrik ihn beim Überschreiben als Startwert zeigt ([Agenten](../../aifactory/docs/agents.md))
und ein zweiter Text nicht auseinanderlaufen darf.

#### UC-BAP-4 — builtinPromptIsReadable ❌

```gherkin
Given keine Datei "agents/po/AGENT.md"
When der eingebaute Prompt der Id "po" gelesen wird
Then ist er gleich dem Prompt-Text, den Jon ohne Datei nutzt
```

### R-BAP-3 — Ordner mit der Id eines Built-ins ist ein Override, kein neuer Agent ❌

Ein Agenten-Ordner, dessen Name eine Built-in-Id ist, erzeugt **keinen** Custom Agent: Er überschreibt nur
Prompt (V1) und Config des Built-ins. Der Agent erscheint genau einmal, mit seinem eingebauten Verhalten
(Tools, Handover, Schreibgrenze).

#### UC-BAP-5 — builtinIdFolderIsNoCustomAgent ❌

```gherkin
Given ein Ordner "agents/dev" mit Rumpf
When die Agenten geladen werden
Then gibt es genau einen Agenten "Dev"
  And er nutzt den Rumpf als Prompt und behält seine eingebauten Tools
```

Plugin: Zum Inkrement gehört ein Abschnitt „Override a built-in agent's prompt" auf der Homepage
(`homepage/src/setup/custom-agents.md`); eine eigene Oberfläche dafür bekommt das Plugin nicht.

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

**Vorgezogen (Paul 2026-10-30, V1 — [ADR-0079](../../docs/adr/0079-display-names-without-ork-names.md)):**
die Team-Namen **Da Thinka · Da Mek · Da Dok · Da Sniffa entfallen jetzt**, nicht erst in V2 — die
Anzeigenamen sind Jon · Plan · Dev · Review, die Sub-Loop-Stempel `Search`/`Compact`. Nur Jon behält
seinen Namen (Persönlichkeit). **Config-Ids und `llm.agent.*`-Keys bleiben bis V2 unverändert.**
Folge: Prompts (`po.md`, Delegate-Tool-Beschreibungen) und Web-Chat-Absender (R-WP-4) zeigen die
Anzeigenamen. **History-Dateinamen** (`<getName()>-history.jsonl`) folgen dem Namen — bewusster
Clean Break (ADR-0079), alte `Peon-*-history.jsonl` lassen sich bei Bedarf umbenennen.

## Abgrenzung

- `AGENTS-<agent>.md` im **Projekt** ([agent-specific-agentsmd.md](agent-specific-agentsmd.md)) bleibt
  eine **Ergänzung** je Projekt (Kontext in der History). `agents/<id>/AGENT.md` **ersetzt** den
  System-Prompt, global. Zwei verschiedene Dinge — nicht verwechseln.

## Offen

- **`compact`** ist heute ein Config-Slot, kein Agent — bekommt er eine Datei?
- **id `jon` statt `po`:** Config-Keys `llm.agent.po.*`, `AGENTS-JON.md`-Auflösung, History-Ordner —
  Da Mek prüft vor der Planung, was am alten Namen hängt.
- **V2 berührt** R-ATC-1…4 ([agent-tool-config.md](agent-tool-config.md)), R-DEF-* (Plugin
  default-inheritance.md), R-THINK-10 — beim Entwurf von V2 gegenprüfen.
