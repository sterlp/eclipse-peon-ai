# Session-Stand — 2026-09-15 (Abend)

## Wo wir stehen

Branch `story/po-compact-2026-09-13` — **nie main, kein push** (Merge/Squash macht Paul).
**Core Surefire 861/0** · Plugin Tycho 212/8 (die 8 sind zyklusfremd, s.u.).

Vier Zyklen an diesem Tag, alle mit Review abgeschlossen:

| Zyklus | Ergebnis |
|---|---|
| Docs-Linter (Inc 1-7 + 2 Reparaturen) | `lintDocsAndTests` / `lintDocs` / `nextIds`, verdrahtet, Homepage, Prompt-Verträge |
| R-DL-13 (Inc 8 + 8a) | Fenced Code Blocks sind zitierter Text, keine Definitionen |
| Nachzyklus R-DL-7/8/14/15 | read-only + Fassaden-Split + raus aus dem Disk-Gate + Tool-Matrix-Tests |
| **R-DL-16 (`934ea7c`)** | `nextIds` zustandslos + Scan-Umfang/Lesequelle im Rückgabewert |
| **R-DL-17 (`ab71c53`)** | ein Präfix = ein Feature über n Dateien; `PRAEFIX_DOPPELT` gestrichen |

`docs/docs-linter.md` ist **✅ done, R-DL-1…17**. Offen dort nur noch Q2 (`suggestIds`, eigener Zyklus).
**Core Surefire 862/0.** Verbrannte IDs: `UC-DL-17`, `UC-DL-44`, `UC-DL-54`.

### R-DL-17 in einem Satz (2026-09-16, Pauls Einwand)

Paul fragte, ob `nextIds` Overkill ist — „hast du das Doc offen, kennst du die nächste Nummer".
Zurecht: `UC-DL-17` verlangte `PRAEFIX_DOPPELT` bei zwei Docs mit gleichem Präfix, womit ein Präfix
in genau einer Datei lebte und `nextIds` über eine Menge der Größe 1 aggregierte. Das widersprach
zugleich der Doc-Split-Regel aus `po.md` (Feature > 2 Seiten → mehrere Dateien). Entschieden:
**ein `idPrefix` spannt ein Feature auf, nicht eine Datei** — Befundtyp ersatzlos gestrichen,
Eindeutigkeit sichert allein `DOPPELT_DEFINIERT` (dateiübergreifend). Netto **weniger** Code:
ein Befundtyp, ein Erkennungsblock, ein Test weg. Review ACCEPTED.

**Pauls E2E am lebenden System (2026-09-16):** `nextIds` zweimal → stabil `R-DL-17`/`UC-DL-58`
(zustandslos, reserviert nicht) · `lintDocs` 54/54 Definitionen, 0 Befunde · `lintDocsAndTests`
34 erwartete Befunde (33 alte fehlende Testkommentare + absichtlicher `VERWAIST UC-DL-99`).
Scan-Umfang und Disk-Lesequelle werden offengelegt.

## Über Nacht allein bearbeitet (Paul war offline) — zwei Ergebnisse für den Morgen

### 1. Pauls Frage zu `nextIds` — beantwortet und gebaut

> „Wie stellen wir sicher, dass `nextIds` immer eine NEUE ID liefert? Sucht es nach einem
> Eclipse-Neustart die letzte ID in den Docs?"

**Ja — und der Neustart ist der harmlose Teil.** `DocsLinter.nextIds` hält nachweislich keinen
Zustand (kein Feld, kein Cache, kein Zähler; baut `prefixStats` bei jedem Aufruf neu aus dem frisch
gelesenen Doc-Baum). Ein persistenter Zähler wäre **schlechter** — er könnte still von den Docs
abweichen.

**Die echte Lücke liegt zwischen Ziehen und Speichern:** `nextIds` reserviert nichts. Gezogen ≠
vergeben. Nicht sofort gespeichert → nächster Aufruf liefert dieselbe Nummer → `DOPPELT_DEFINIERT`.

Entschieden, geschrieben, gebaut: **R-DL-16 + UC-DL-56/57** in `docs/docs-linter.md`, Arbeitsablauf
in `po.md` verankert, `nextIds` legt jetzt Lesequelle + Doc-Dateizahl (aus der Discovery) offen.
Review: **CONCERNS** (nicht blockierend — Plan hatte den Prompt-Pfad nicht als betroffen geführt),
abgenommen.

### 2. Install-Log des Users — **nicht unser Bug, kein Target-Rollback**

Kette: `jface` → `eclipse.swt;image.format=svg` → `swt.svg` → `jsvg` → `spifly 1.3.7` → dort
**uses-constraint violation**: `org.objectweb.asm` in **9.10.1 und 9.9.1 gleichzeitig**.
Drei unabhängige Belege, dass es nicht an uns liegt: wir pinnen `swt`/`jface`/`ui` gar nicht; unser
`Bundle-ClassPath` enthält **kein** asm/jsvg/spifly; `failing bundles.log` nennt nur die beiden
EPP-Bundles, `org.sterl.llmpeon` ist `[RESOLVED]`. Details in `open-points.md` (⏳).

## Für Paul zum Bestätigen (⏳ in `open-points.md`)

1. `nextIds`-Ablauf statt persistentem Zähler — passt das so?
2. Kein Target-Rollback auf 2026-03; dem betroffenen User frische Installation / `-clean` empfehlen.
   Einzige Folge-Aktion wäre: Mindest-Eclipse-Version auf der Homepage nennen.
3. `AGENTS-PO.md` ist weiterhin **uncommitted** (dein Edit, nicht angefasst).

## Offene Punkte (`open-points.md`)

- 🔒 **ID-Kommentare:** 11 UCs des Nachzyklus erledigt. Die ~37 älteren DL-Tests aus dem Erstzyklus
  fehlen weiter → eigener kleiner Zyklus.
- ❓ **Code-Block-Regel für Testquellen** — ID-Kommentar in Java-Textblock/Python-Docstring zählt
  heute als Beleg. Bewusst nicht gebaut (erfordert Sprach-Parsing = die Rateübung aus Q8).
- ❓ **`PeonAiServiceTest`: 8 rote Compact-/TurnContext-Tests** — zyklusfremd, eigener Bug-Zyklus.
- 🐞 **`eclipseReplaceLines` verhält sich sporadisch wie Insert** — nach jedem Line-Edit zurücklesen.
- ⏳ Ausstehender User-Smoke Compact-Zyklus · Branch `release-2026-09-06` Merge = User-Entscheid.

## Was nicht neu aufgemacht wird

- **Keine Tests auf Prompt-Inhalte** — `docs/prompts.md` R1: Prompt-Content ist Repo-SOT.
- **Keine Test-Infrastruktur in `homepage/`** — Green Gate ist `npm run docs:build`.
- **Q2 `suggestIds`** — eigener Zyklus.
- **Kein Overlay-/Child-`ToolService`** — verworfen, siehe ADR-0048.
- **Kein persistenter ID-Zähler / keine Registry-Datei** — R-DL-16, die Docs sind die Registry.

## Lektionen dieses Tages

1. **Format-/Sprachneutralität vor dem BDD prüfen.** Lösung war **Streichen**, nicht Erweitern.
2. **Ein Reporting-Tool muss den Scan-Umfang nennen**, aus der Discovery, nie aus den Treffern.
3. **Ein Feature ist fertig, wenn der vorgesehene Aufrufer es in Standard-Konfiguration aufrufen kann.**
4. **Streichen schlägt Absichern.**
5. **Ein Test auf eine strukturelle Unmöglichkeit ist eine Schranke, kein Beleg.**
6. **Prompt-/Doc-Änderungen der Agenten selbst nachlesen**, bevor man sie abnimmt.
7. **Verbindliche Agenten-Abläufe brauchen den Prompt-Pfad im Plan** — sonst ist die Regel
   dokumentiert, aber wirkungslos (Da Doks CONCERNS zu R-DL-16).

## Nächste Zyklen (Pauls Reihenfolge)

1. **Compact-Input-Budget Light + Context-Noise** — ❌ specified (`docs/compact-input-budget.md`),
   vor dem Bau nochmal gemeinsam.
2. **ApiRetry-Cancel-Bug** (`open-points.md`, 4 Evidence-Klassen).
3. ID-Kommentare an den ~37 Alt-DL-Tests nachtragen (kleiner Zyklus).
4. Danach: `PeonAiServiceTest`-Bugzyklus, Compact-Delay (~4-5s), R-A3 Copilot-Studie,
   Docs-Hygiene-Sweep, `suggestIds`.
