# ADR-0048 — Docs-Linter: echt read-only, und Split in Vergabe- vs. Prüf-Tool

**Status:** accepted (2026-09-15) · ergänzt [ADR-0047](0047-docs-linter-disk-only-single-root.md)
**Fachdoc:** [docs-linter.md](../docs-linter.md) (R-DL-7, R-DL-8, R-DL-14, R-DL-15)

## Context

Der Docs-Linter war gebaut und im Review angenommen. Beim Nachfassen zur Prompt-Verdrahtung kamen
**vier Defekte** ans Licht, die alle denselben Ursprung haben: Das Tool wurde gebaut, aber nie
daraufhin geprüft, **wer es tatsächlich in die Hand bekommt**.

| # | Befund | Fundstelle (IST vor dem Fix) |
|---|---|---|
| D1 | Jon (PO) hatte das Tool gar nicht — seine Tool-Liste ist hart kuratiert | `BuildPoAgentComponent.java:49-75` |
| D2 | 🐞 Registrierung nur bei `diskToolsEnabled == true`, Default ist `false` | `SharedToolsComponent.java:67-70`, `LlmConfig.java:92` |
| D3 | Kein einziger Test prüft, welcher Agent welche Tools sieht | — |
| D4 | `reportPath` schreibt, aber `isEditTool()` meldet `false`; kein Traversal-Test | `DocsLinterTool.java:40`, `130-149` |

**D2 ist der teuerste.** In der Standard-Konfiguration existierte der Linter für *niemanden* — auch
nicht für Da Dok, dessen Prompt (`review-agent.md`) ihn beim Review-Start verlangt. Ein Prompt, der
auf ein nicht vorhandenes Tool zeigt, lässt den Agenten halluzinieren oder blockieren, und niemand
sieht es. Das ist exakt das Fehlermuster, gegen das dieses Werkzeug gebaut wurde: *ein falscher
Pfad sieht aus wie ein sauberes Ergebnis.*

Zwei Fragen standen zur Entscheidung: (1) Wie kommt das Tool an die richtigen Agenten? (2) Darf es
überhaupt schreiben?

## Decision

### 1. `reportPath` ersatzlos streichen — Clean Break

Messung gegen das eigene Repo: Der Rückgabewert (`summary`) enthält **alle** Befunde einzeln mit
`datei:zeile`, ungekappt — 5.942 Zeichen, 149 Zeilen, 35 Befunde. Der Datei-Report enthielt
ausschließlich **Aufbereitung** (Überschriften, Tabellen, Gruppierung nach Datei, Methodennamen,
den Disk-Read-Hinweis), keine zusätzliche Information.

Der Nutzer ist ein Agent, kein Mensch am Terminal. Er bekommt den Rückgabewert direkt in seinen
Context — die Datei ist ein Umweg, der einen zweiten Read kostet und nichts liefert.

**Der eigentliche Gewinn ist die Abwesenheit.** Vier Probleme verschwinden, statt abgesichert
werden zu müssen:

- der Traversal-Angriffspfad auf `reportPath` (genau die Bug-Klasse, die
  [write-path-validator.md](../write-path-validator.md) real hatte — und für die es hier **keinen
  Test** gab),
- die `WriteValidator`-Kopplung je Agent (Da Dok/Da Thinka brauchten `DENY_ALL`, damit ein
  „read-only" Agent nicht doch schreibt),
- die Inkonsistenz `isEditTool() == false` bei einem schreibenden Tool,
- `DocsLinterWritePolicyTest` samt seiner vier Tests.

Braucht jemand den aufbereiteten Report als Arbeitsdatei (Migrationslauf mit 100+ Befunden),
schreibt **Jon** ihn nach `docs/` — er hat dort Schreibrecht, und dort gehört er hin.

### 2. Split in Fassaden entlang der Verantwortung

| Fassade | Methoden | Wer |
|---|---|---|
| `DocsIdTool` | `nextIds` | **nur Jon** |
| `DocsLinterTool` | `lintDocs`, `lintDocsAndTests` | Jon · Da Thinka · Da Mek · Da Dok |

Beide im Package `org.sterl.llmpeon.docslinter`, beide über **einer** Kern-Instanz — ein Feature,
zwei Sichten. Dass beide dasselbe `workingDir` teilen, ist keine Sorgfaltsfrage, sondern
strukturell: zwei Instanzen mit auseinanderlaufendem Root wären der stille Ausfall aus R-DL-6 eine
Ebene höher.

**Verworfen — eine dritte Fassade nur mit `lintDocs` für den Plan-Agenten.** `ToolService` lässt je
Methodenname genau einen Executor zu (`ToolService.java:47`); zwei Fassaden mit `lintDocs` im selben
Service kollidieren. Der Ausweg wäre ein Overlay-/Child-`ToolService` gewesen — eine neue
Architekturebene, nur um einem Agenten einen Call vorzuenthalten, den er ohnehin nicht braucht. Die
Trennung, die diesen Zyklus begründet, ist `nextIds`; Prüfen darf jeder. **Der einfachste Schnitt
gewinnt — verbessern kann man später, falls es je nötig wird.**

**Warum überhaupt trennen:** Die ID-Vergabe ist PO-Hoheit. Vergäbe Da Mek IDs selbst, entstünden sie
zum **Bauzeitpunkt**, während der PO später eine andere ins Doc schreibt — Ergebnis: gleichzeitig
`VERWAIST` (seine ID) und `UNBELEGT_ERLEDIGT` (die des Docs). Der Linter meldete dann Rauschen statt
Befund, also den Zustand, gegen den er existiert.

**Warum ein Split und nicht eine Prompt-Regel:** Ein Prompt-Satz („nie erfinden") ist eine Bitte, ein
fehlendes Tool ist eine Garantie. Die Regel stand bereits in `developer.md` — Verlass ist darauf
nicht.

**Warum ein Split und nicht ein Namens-Filter:** Technisch *könnte* ein Agent eine einzelne Methode
ausblenden — `getToolFilter()` ist ein `Predicate<SmartToolExecutor>` (`AbstractAgent.java:115-117`),
und `SmartToolExecutor` existiert pro `@Tool`-Methode. Das wäre die kleinere Codeänderung.

Trotzdem die Fassade, aus zwei Gründen:

1. **Präzedenz im eigenen Code.** Genau dieser Fall existiert bereits: `planSave` wird Jon nicht per
   Namensfilter entzogen, sondern über die Fassade `PlanReadTool`
   (`BuildPoAgentComponent.java:69-71`). Es gibt im gesamten Repo **keinen** Agenten, der eine
   einzelne Methode eines Multi-Method-Tools per Namen ausblendet. Zwei Mechanismen für dieselbe
   Aufgabe wären „one behaviour, two implementations".
2. **Ein Namens-Filter bricht still.** Er prüft ein String-Literal (`"nextIds"`) ohne
   Compiler-Bindung: Wird die Methode umbenannt, greift der Filter nicht mehr, und das Tool taucht
   klammheimlich bei einem Agenten auf, der es nicht haben soll. Kein Fehler, keine Meldung — exakt
   die False-Negative-Klasse, gegen die dieses Feature gebaut wird. Die Fassade ist
   compile-gebunden.

> **Korrektur 2026-09-15:** Eine frühere Fassung dieses ADR begründete den Split damit, der Filter
> wirke „tool-weit, nicht method-weit". **Das ist faktisch falsch** und wurde beim Planen widerlegt.
> Die Entscheidung bleibt, die Begründung ist ersetzt — eine falsche Begründung im ADR ist
> gefährlicher als keine, weil sie beim nächsten Anfassen als Fakt gelesen wird.
>
> **Ergänzung 2026-09-25 (Paul):** Mit der Regel „Tool-Namen, die Filter/Tests referenzieren,
> werden als statische String-Konstanten gefasst" ([agent-tool-filter.md](../agent-tool-filter.md),
> R-TF-3) entfällt Grund 2 („stilles Umbenennen") weitgehend — Konstante und `@Tool(name=…)` haben
> eine Quelle. Die Entscheidung (Fassade für `nextIds`) bleibt: Grund 1 (Präzedenz `PlanReadTool`)
> trägt weiter, und eine bestehende Fassade wird nicht umgebaut. Neue Fälle entscheiden sich im
> Einzelfall: erst Filter (Standard-Mechanik), Fassade nur bei echtem Ownership-Split.

### 3. Raus aus dem `diskToolsEnabled`-Gate

Beide Tools werden **immer** registriert. Das Disk-Gate schützt vor freiem
Dateisystem-Schreibzugriff; den hat der Linter nach Entscheidung 1 nicht mehr. Kein eigener
Schalter `docsLinterEnabled` — noch ein Config-Key für ein read-only-Tool wäre Overhead ohne
Schutzwirkung.

### 4. Tool-Matrix je Agent wird getestet

Die Zuordnung aus R-DL-14 ist ein Test, keine Konvention (D3). Ohne ihn ist D1 jederzeit
wiederholbar: Jons Tool-Liste ist hart kuratiert, und eine vergessene Zeile fällt niemandem auf.

## Consequences

**Positiv**

- Das Tool ist ohne Einschränkung read-only — keine Write-Policy, kein Traversal-Risiko, kein
  Validator-Setup je Agent.
- Die Kette der ID-Hoheit ist technisch erzwungen, nicht erbeten:
  Jon vergibt → Doc → Da Thinka plant → Da Mek kopiert → Da Dok prüft.
- Der Linter funktioniert in der Standard-Konfiguration. Die Prompt-Verträge zeigen nicht mehr ins
  Leere.
- Ein Test deckt künftig ab, welcher Agent welche Tools sieht — die Lücke, durch die D1 entstand.

**Negativ / Preis**

- Breaking Change an der Tool-Signatur: `reportPath` fällt weg, ohne Alias und ohne Migration
  (konsistent zur Projektregel *„Clean break over migration"*, [AGENTS.md](../../AGENTS.md)).
- Der aufbereitete Markdown-Report (Gruppierung nach Datei, Test-ID-Belege) entfällt. Falls er real
  vermisst wird, ist die Antwort ein Renderer **im Rückgabewert**, nicht die Rückkehr des
  Schreibpfads.
- Zwei Tools statt einem: minimal mehr Verdrahtung.

## Lesson (warum das überhaupt passierte)

Der Bau-Zyklus prüfte drei Seiten — Plan, Code, Docs. **Keine davon beantwortet die Frage, ob das
Tool beim vorgesehenen Nutzer ankommt.** Ein Feature ist nicht fertig, wenn es gebaut und getestet
ist, sondern wenn der vorgesehene Aufrufer es **in seiner Standard-Konfiguration** tatsächlich
aufrufen kann. Bei allem, was in einen Agenten-Prompt geschrieben wird, gehört dieser Nachweis zum
Increment — ein Prompt, der ein Tool nennt, das der Agent nicht hat, ist schlimmer als kein Prompt.
