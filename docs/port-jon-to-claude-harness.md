# Jon & Team in den Claude-Code-Harness portieren

> **Status:** 📝 Methodik (2026-10-29). Zweitgleis zum Eclipse-Peon: dieselbe docs-first-PO-Methode,
> aber ausgeführt im **Claude-Code-Harness** (CLI/IDE) statt im Peon-Eclipse-Plugin.
> Kanonische Methoden-Quelle bleibt die Jon-Skill
> (`https://github.com/sterlp/ai-skill-codex/tree/main/skills/jon`); dieses Doc beschreibt, **wie**
> die Peon-Prompts mechanisch auf Claude-Artefakte abgebildet werden.

## Ziel

Jon (PO) + sein Team sollen im Claude-Harness laufen, abrufbar über **`/jon`**. Der User wechselt mit
`/jon` in die PO-Rolle; Jon orchestriert Plan/Dev/Review über das `Agent`-Tool und schreibt selbst nur
in die Docs. Die Peon-**Engine** (eigene Tools) wird dabei bewusst nicht nachgebaut — nur die Methode
und die Rollen.

## Was ist Peon, was ist Claude — die Grenze

Peon liefert zwei Dinge, die der Port sauber trennen muss:

1. **Methode & Rollen** (portabel) — die Prompt-Dateien in
   `org.sterl.llmpeon.core/src/main/resources/org/sterl/llmpeon/prompts/`. Die werden portiert.
2. **Engine** (nicht portabel) — peon-native Tools: `plan*`, `talkPlan`/`buildWithDev`/`askDev`/
   `reviewPlanAgent`/`searchAgent`, `lintDocsAndTests`, `nextIds`, `compactSession`, `reloadConfig`,
   `handover`, Agent-Persistenz (`clear*`/`compact*`). Die haben im Claude-Harness kein 1:1-Pendant
   und werden gemappt oder manuell erledigt (siehe unten).

## Quell-Prompt → Claude-Artefakt

| Peon-Prompt | Claude-Artefakt | Komposition |
|---|---|---|
| `po.md` + `doc-method.md` | `~/.claude/skills/jon/SKILL.md` (`/jon`) | Composite: po.md-Regeln **+** volle doc-method.md |
| `default.md` + `planner.md` + `plan-write-loop.md` | `~/.claude/agents/plan.md` | default.md-Basis + Rolle + Plan-Write-Loop |
| `default.md` + `developer.md` + `dev-build-loop.md` | `~/.claude/agents/dev.md` | default.md-Basis + Rolle + Dev-Build-Loop |
| `default.md` + `review-agent.md` | `~/.claude/agents/review.md` | default.md-Basis + Rolle |
| `search-agent.md` | **Claude-Default** (`Explore`-Agent) | kein eigenes Agent-File — read-only Recherche deckt Claudes Explore ab |
| `default.md` + `scaffold-agent.md` | `~/.claude/agents/scaffold.md` | default.md-Basis + Rolle, Schreibziel auf `~/.claude/{agents,skills}` umgestellt |

**Komposition ist verbindlich:** jeder Agent trägt `default.md` als Basis; Jon ist immer das Composite
aus po.md **und** doc-method.md. Der Dev-Agent bekommt den `dev-build-loop.md`-Inhalt, der Plan-Agent
den `plan-write-loop.md`-Inhalt direkt mit eingebaut (im Peon werden sie dynamisch beim Bauen/Planen
angehängt — im Claude-Harness ist der Agent-Prompt statisch, darum fest als „Build-Loop (bei
buildWithDev)" bzw. „Plan-Write-Loop (bei planWithPlanAgent)"-Sektion verankert; die Sparring-Modi
`talkPlan`/`askDev` lassen die Loop-Sektion einfach aus).

**Namen:** im Claude-Harness kurz — `plan`, `dev`, `review` (+ `scaffold`). Die Peon-Namen (Da Thinka /
Da Mek / Da Dok) bleiben als Charakter in den Prompts erhalten, der `subagent_type` ist der Kurzname.

## Tool-Mapping (Jon ↔ Agent-Tool)

| Peon-Tool | Claude-Harness | Zweck |
|---|---|---|
| `talkPlan` | `Agent(subagent_type: "plan", …)` ohne Schreibauftrag | Architektur-Sparring, schreibt nichts |
| `planWithPlanAgent` | `Agent(subagent_type: "plan", …)` mit Plan-Pfad | Plan in die Plan-Datei schreiben/verfeinern |
| `askDev` | `Agent(subagent_type: "dev", …)` | Code-Frage oder kleiner Einzel-Fix |
| `buildWithDev` | `Agent(subagent_type: "dev", …)` mit Plan-Pfad | freigegebenes Inkrement bauen |
| `reviewPlanAgent` | `Agent(subagent_type: "review", …)` | unabhängiges QA-Review, frischer Context |
| `searchAgent` | `Agent(subagent_type: "Explore", …)` | read-only Recherche (Claude-Default) |
| Agent fortsetzen (`compact*`) | `SendMessage` an laufenden Agent | gleiches Thema weiterführen |
| Agent zurücksetzen (`clear*`) | frischer `Agent(…)`-Call | Themenwechsel = neuer Context |

## Claude-spezifische Prompt-Anpassungen

Beim Portieren **anpassen** (sonst referenziert der Prompt nicht existierende Peon-Mechanik):

- **`askUser` entfernen** — gibt es im Claude-Harness nicht; Jon fragt direkt im Chat.
- **`plan*`-Tools → Write/Edit auf die Plan-Datei** (Default-Ablage `peon-plan/`). Plan- und
  Review-Agent dürfen nur dort schreiben (per Prompt-Regel, nicht per Toolset erzwungen — Claude-
  Agents haben keine Pfad-Whitelist, nur Tool-Whitelist).
- **`SearchAgentTool` → Explore-Agent / direkte Read-Tools.**
- **`lintDocsAndTests` / `nextIds` → manuell:** IDs zieht Jon per Doc-Lesen (ziehen → sofort schreiben
  → speichern → nächste); Lint per Grep (jede Doc-ID hat einen Test-Marker, jeder Marker eine Doc-ID)
  vor jedem ❌ → ✅-Flip. Optionaler Ausbau: als Bash-Script/MCP nachrüsten.
- **`compactSession` / eigener `compact` → Claude-Auto-Compaction.** Disziplin bleibt: bei
  Themenwechsel/Context-Warnung zuerst `memory.md` + offene Punkte schreiben, dann weiter.
- **`reloadConfig`/`handover`** (Scaffold/Agent-Wiring) → entfällt; Claude lädt `~/.claude/agents/*.md`
  und `~/.claude/skills/*` beim nächsten Aufruf selbst.

## memory.md — immer in den Docs, blume-bewusst

Jons Gedächtnis ist **immer `<docs>/memory.md`** (die memory.md IN der Docs-Ablage), nie das Harness-
eigene Memory. Grund: sie wird mit git versioniert und mit den Docs committet — der User hat den
Session-Stand unter Versionskontrolle.

Die Docs-Ablage kann zusätzlich als **Blume-HTML-Site** gebaut und exportiert werden. Das ist im
mapa-manager-Projekt real aufgesetzt — **maßgeblich ist dessen echte Config, nicht Annahmen.**
Beispiel `docs-site/blume.config.ts` (Blume-Projekt liegt als Geschwister neben der Docs-Ablage):

```ts
export default defineConfig({
  title: "MaPa Docs",
  content: {
    root: "../docs",          // die Docs-Ablage
    exclude: ["memory.md"],   // memory.md wird NICHT publiziert
  },
  navigation: { tabs: [{ label: "Docs", path: "/", href: "/" }, { label: "ADR", path: "/adr" }] },
  frontmatter: {
    extend: { idPrefix: z.string().optional() },  // Custom-Key der Doc-Methode registriert
  },
});
```

Daraus die zwei Fakten, die den Port betreffen:

- **memory.md wird per `content.exclude: ["memory.md"]` vom Build ausgenommen** — genau der Mechanismus,
  den der Port braucht: memory.md liegt in `<docs>/`, wird git-versioniert, aber nie publiziert. Das ist
  die Default-Empfehlung, kein Workaround — memory.md ist Arbeitsstand, kein veröffentlichbarer Inhalt.
- **Der `idPrefix` der Doc-Methode ist ein registrierter Custom-Key** (`frontmatter.extend` + Zod).
  Blume-Frontmatter ist strikt: ein nicht registrierter Key bricht den Build (`BLUME_FRONTMATTER_INVALID`).
  Weitere Doc-Methode-Keys, die in publizierten Docs stehen, müssen hier ebenso registriert werden.

Weitere Blume-Konventionen für publizierte Docs (aus den Blume-Referenzdocs des Projekts, nicht erfunden):
der Seitentitel kommt aus dem Frontmatter `title:` (eigenen H1 im Content vermeiden), und Mermaid ist im
mapa-Setup über eine eigene Komponente gelöst (`docs-site/components.ts` registriert `MermaidMd`) — also
projekt-spezifisch, nicht als „nur in `.mdx`"-Pauschalregel annehmen.

## Nacht-Zyklus: Scaffold leitet Agenten/Skills ab

Analog zum **Skill-Evolution-Loop** aus [AGENTS-JON.md](../AGENTS-JON.md): nach Review-Zyklen fällt
Evidenz an (was fehlte, was zum Raten zwang — Da Dok meldet Skill/Instruction-Gaps, Checklisten-Punkt
10). Die wird in einem **Ledger** gesammelt (`.agents/skills/wiki/skill-impact.md` bzw. im Port eine
projektlokale Ledger-Datei).

Im Port übernimmt das der **`scaffold`-Agent** (Port von `scaffold-agent.md`), **im Nacht-Zyklus**
gestartet (Claude: `CronCreate` oder `/loop` zu Randzeiten): er liest das Ledger und leitet genau
**ein** CRUD-Outcome pro Lauf mit Evidenz ab — neuen Agent/Skill anlegen, bestehenden schärfen oder
löschen. „Kein Outcome" ist ein legitimes Ergebnis. Schreibziel des Scaffold im Claude-Harness:
`~/.claude/agents/*.md` und `~/.claude/skills/*/SKILL.md` (bzw. projektlokal `.claude/…`) — **nicht**
das Peon-`~/.peon/skills`.

## Entscheidung: die „Beta"-Agent-Prompts übernehmen?

Die experimentellen, projekt-lokalen Ergänzungen [AGENTS-JON.md](../AGENTS-JON.md) und
[AGENTS-DEV.md](../AGENTS-DEV.md) — **teils ja, teils nein:**

- **Nein** zum Einbacken der projekt-spezifischen Beta-Inhalte in die portablen Agent-Prompts:
  AGENTS-DEV.md ist voll von Eclipse/Peon-Spezifika (peon-build-test, Tycho, `lintDocsAndTests`-
  Invokation, `docRoots`, datierte Hit-Logs). In einem anderen Projekt ist das Rauschen oder schlicht
  falsch. **Der richtige Kanal dafür ist die `AGENTS.md`/`AGENTS-DEV.md` des Zielprojekts** — die lädt
  Claude ohnehin automatisch. So bleiben die globalen Agenten generisch, das Projekt-Wissen reist im
  Repo.
- **Ja** zu den **Methodik-Ideen** aus AGENTS-JON.md: der Skill-Evolution-Loop und der Nacht-Zyklus-
  Scaffold sind keine Peon-Spezifika, sondern übertragbare Praxis. Die werden übernommen (Scaffold-
  Agent + Nacht-Zyklus, siehe oben) — das **Konzept**, nicht der projekt-gebundene Wortlaut.

Kurz: Basis-Prompts (default/po/doc-method/planner/developer/review/loops/scaffold) portieren; Beta-
**Konzepte** übernehmen; Beta-**Projektinhalte** draußen lassen und der Ziel-`AGENTS.md` überlassen.

## Installationsorte

- **Global** `~/.claude/` — `/jon` + Team in jedem Projekt verfügbar (Rolle überall gleich, `<docs>`/
  `<plan>` projektspezifisch zur Laufzeit bestimmt). Empfohlen.
- **Projektlokal** `<projekt>/.claude/` — eingecheckt, teilbar, nur dort verfügbar. Für Projekte, die
  den Port versioniert mitliefern wollen.

## Port-Checkliste

1. `default.md`, `po.md`, `doc-method.md`, `planner.md`, `developer.md`, `review-agent.md`,
   `plan-write-loop.md`, `dev-build-loop.md`, `scaffold-agent.md` lesen.
2. `skills/jon/SKILL.md` = po.md + doc-method.md + Claude-Setup/Delegation-Header.
3. `agents/plan.md`, `agents/dev.md`, `agents/review.md`, `agents/scaffold.md` = default.md-Basis +
   Rolle (+ Loop beim Plan-/Dev-Agent). Such-Rolle = Explore-Default, kein File.
4. Claude-Anpassungen anwenden (askUser raus, plan*→Write/Edit, lint/nextIds→manuell, search→Explore).
5. memory.md auf `<docs>/memory.md` festlegen; Blume-Kompatibilität bzw. -Ausschluss klären.
6. Nacht-Zyklus (`CronCreate`/`/loop`) auf den Scaffold-Agent zeigen lassen; Ledger-Pfad festlegen.
7. Beta-Projektinhalte NICHT einbacken — der Ziel-`AGENTS.md` überlassen.
