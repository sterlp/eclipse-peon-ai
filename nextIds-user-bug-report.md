# Bug-Report `nextIds` — aus der Praxis (FORgE, 2026-09-22/23)

> Aus Sicht des Anwenders (PO-Agent), der das Tool im Nachtzyklus OP-78 benutzen wollte.
> Alle Befunde reproduziert, Tool-Ausgaben wörtlich.

## Zusammenfassung

Die ID-Vergabe lief am Ende **von Hand per `eclipseGrepFiles`**. Genau das soll das Tool
verhindern — die Handvergabe erzeugt laut Systemprompt gleichzeitig `VERWAIST` und
`UNBELEGT_ERLEDIGT`.

Von drei gemeldeten Hürden bleiben nach Pauls Hinweis **zwei echte Bugs** (2 und 3); Befund 1 war
mein Anwenderfehler und ist unten als Lehre stehen geblieben.

| # | Befund | Schwere |
|---|---|---|
| 1 | ~~`root` akzeptiert keinen Workspace-Pfad~~ | **kein Bug — Anwenderfehler, s. u.** |
| 2 | `prefix` verbietet Bindestriche (`O-TEST`) | hoch — reale Doc-Konvention wird abgewiesen |
| 3 | Antwort erfindet ein Schema (`R-OP-1`), das im Projekt nicht existiert | mittel — führt zu falschen IDs, wenn man ihr folgt |

---

## Befund 1 — zurückgezogen: mein Fehler, nicht der des Tools

**IST (mein Fehlaufruf)**

```
nextIds(root="/forge-root", …)
→ Root is not a directory: /forge-root
  (also tried: /Users/sterlp/dev/projekte/utilitites/forge/forge-root)
```

`nextIds` ist ein **Disk-Tool** und will einen Disk-Pfad. Der stand die ganze Zeit in der
Projekt-Info der Session:

```
Project name: forge-root
Eclipse path: /forge-root
Disk path:    /Users/sterlp/dev/projekte/utilitites/forge
```

**Die eigentliche Stolperfalle:** Eclipse-Projektname ≠ Ordnername auf der Platte. Das Projekt
heißt `forge-root` (nach der Maven-`artifactId` im Root-`pom.xml`), der Ordner heißt `forge`.
Deshalb konnte auch der Fallback des Tools nicht greifen — er hängt den Projektnamen ans Working-Dir
an und landet bei `…/forge/forge-root`, das es nicht gibt. Bei gleichnamigem Ordner wäre es
zufällig gutgegangen und ich hätte den Unterschied nie bemerkt.

**Lehre (für mich, nicht für das Tool):** Bei jedem Disk-Tool den `Disk path` aus der Projekt-Info
nehmen, nie den Eclipse-Pfad und nie aus dem Projektnamen konstruieren.

---

## Befund 2 — `prefix` verbietet Bindestriche, echte Konventionen haben sie

**IST**

```
nextIds(prefix="O-TEST", docRoots=["docs"])
→ prefix must be uppercase letters only: O-TEST
```

**WEIL** — in FORgE/MaPa tragen Regeln bewusst einen **Bereichs**-Anteil:
`R-ORD-1`, `R-MALO-12`, `R-CUST-17`, `R-TS-10`, `R-CLR-13`, `R-UIERR-1`, `R-LM-10`,
`O-TEST-1`, `D-<BEREICH>-<Nr>`. Der Bereich ist der Teil, der die Nummernkreise überhaupt trennt —
er *ist* das Präfix. Das Tool akzeptiert damit exakt die Projekte nicht, für die es gedacht ist.

**SOLL** — `prefix` als `[A-Z][A-Z0-9-]*` zulassen (Bindestrich innen, nicht am Rand).
Falls das Trennzeichen intern eine Rolle spielt: am **letzten** Bindestrich vor der Nummer splitten,
nicht am ersten.

**Workaround, den ich fahren musste**

```
eclipseGrepFiles(query="O-TEST-", path="/forge-root/docs", extension=".md")
→ höchste vorhandene Nummer manuell ablesen, +1
```
Dasselbe für `ADR-0046`, `OP-78`, `OP-79`. Vier Handvergaben in einem Zyklus.

---

## Befund 3 — die Antwort erfindet ein Schema, das es im Projekt nicht gibt

**IST**

```
nextIds(root="<disk>", prefix="OP", docRoots=["docs"])
→ OP: free, would start: R-OP-1, UC-OP-1
```

In FORgE existieren **OP-70 … OP-79** (`docs/offene-punkte.md`, in `index.md` verlinkt).
Das Tool meldet den Nummernkreis als *frei* und schlägt `R-OP-1` vor.

Erklärung: Es hat `OP` als **Bereichs**namen gelesen und die Formen `R-<prefix>-N` /
`UC-<prefix>-N` daraus gebaut. Für FORgE ist `OP-` aber selbst schon der vollständige Präfix eines
Registers (offene Punkte), nicht ein Bereich innerhalb von `R-`/`UC-`.

**WEIL** — das ist der gefährlichste der drei Befunde: Befund 1 und 2 **scheitern laut**. Dieser
liefert eine plausibel aussehende Zahl, die schlicht falsch ist. Wer ihr folgt, vergibt `OP-1` ein
zweites Mal oder legt ein Paralleluniversum `R-OP-*` an. Ein Tool, dessen Zweck Eindeutigkeit ist,
darf im Zweifel nicht raten.

**SOLL**
- Wenn zum Präfix Treffer im Doc-Bestand existieren, **deren** Form fortschreiben — nicht eine
  Wunschform konstruieren.
- `free` nur melden, wenn wirklich **kein** Vorkommen des Präfixes in irgendeiner Schreibweise
  gefunden wurde; sonst zeigen, was gefunden wurde („OP: höchste gefunden OP-79 in
  docs/offene-punkte.md").
- Die Antwort sollte die **Fundstelle** der höchsten ID nennen. Dann ist sie nachprüfbar, statt
  geglaubt werden zu müssen.

---

## Nebenbeobachtung — „not participating" ist stumm

```
Scanned: 80 doc file(s): 20 linted, 60 not participating
```

60 von 80 Dateien zählen nicht mit, und man erfährt weder **welche** noch **warum**
(fehlender `idPrefix`-Opt-in, vermute ich). Bei einem Tool, das über Eindeutigkeit wacht, ist eine
stumme 75-%-Blindstelle ein Risiko: Genau in den 60 könnte die ID längst vergeben sein.

**SOLL** — auf Wunsch (oder immer, es sind ja nur Dateinamen) die nicht teilnehmenden Dateien
auflisten, mindestens aber den Grund einmal nennen.

---

## Was ich mir als Anwender wünschen würde, in dieser Reihenfolge

1. **Befund 3 zuerst** — lieber ein ehrlicher Fehler als eine falsche Nummer.
2. **Befund 2** — ohne Bindestriche ist das Tool in FORgE und MaPa unbenutzbar.
3. Fundstelle in der Antwort ausgeben (macht das Ergebnis prüfbar).
4. Optional, aus Befund 1 gelernt: Wenn der Fallback „Working-Dir + Projektname" scheitert, in der
   Fehlermeldung den bekannten `Disk path` des Projekts vorschlagen. Projektname ≠ Ordnername ist
   bei Maven-Multi-Modulen der Normalfall, nicht die Ausnahme.

## Reproduktion

Workspace `forge-root`, Disk `/Users/sterlp/dev/projekte/utilitites/forge`, `docs/` mit
`offene-punkte.md` (OP-70…OP-79) und `betrieb.md` (O-TEST-1), Stand 2026-09-23.
Alle vier Aufrufe oben sind wörtliche Tool-Ausgaben dieser Session.
