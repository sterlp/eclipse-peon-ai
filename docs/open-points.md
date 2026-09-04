# Open Points

Status je Punkt: ❓ offen · ⏳ selbst entschieden (Rückversicherung mit User steht aus) · 🔒 geklärt.
Geklärte Punkte ohne Feature-Doc wandern nach [resolved-points.md](resolved-points.md).

## ❓ `eclipseWriteFile` schreibt immer UTF-8 — Charset-Asymmetrie

**Gefunden:** 2026-09-03, Dev-Agent während inc-1 (Test-Fixture).

**IST:** `IoUtils` **liest** mit `IFile#getCharset()` (`IoUtils.java:28-35`), **schreibt** aber
ohne Encoding-Berücksichtigung (`IoUtils.java:101-111`). Verifiziert: Datei mit
`encoding//…=ISO-8859-1` in `.settings/org.eclipse.core.resources.prefs`, danach
`eclipseWriteFile("äüß Ö")` → tatsächlich `C3 A4 C3 BC C3 9F 20 C3 96` (UTF-8) statt
`E4 FC DF 20 D6` (ISO-8859-1).

**Wirkung:** Ein Agent, der eine ISO-8859-1/Windows-1252-Datei liest, ändert und zurückschreibt,
**korrumpiert** sie stillschweigend — Lesen dekodiert korrekt, Schreiben kodiert falsch. Trifft
Legacy-Projekte (properties-Dateien, alte Java-Quellen).

**Frage an den User:** eigene kleine Story (Fix: `IoUtils` schreibt mit `file.getCharset()`,
Test mit ISO-Fixture) — oder bewusst akzeptieren und dokumentieren („Peon schreibt immer UTF-8")?

**PO-Empfehlung:** fixen, aber **nicht** in diesem Zyklus — Kandidat für Zyklus 2 zusammen mit
den Read-Tools ([eclipse-read-tools.md](eclipse-read-tools.md)), weil dieselbe Tool-Familie.

## ❓ Glossar eager laden?

[glossary.md](glossary.md) ist angelegt. **Frage (User, 2026-09-03):** automatisch in den
Kontext laden — und für welche Agenten? Optionen:
- **(a)** nur Jon (PO) — er formuliert das SOLL, dort entsteht der Begriff
- **(b)** Jon + Da Thinka + Da Mek — alle, die Docs oder Code schreiben
- **(c)** gar nicht eager, nur per Verweis aus `docs/index.md` (Status quo)

**PO-Empfehlung:** **(b)**, aber als `ContextItem` **im Turn-Context**, nicht im Static Context
— sonst bricht jede Glossar-Änderung den Prompt-Cache aller Agenten. Kosten: ~600 Token pro
Turn. Alternativ (a) + Da Mek liest bei Bedarf.

## ⏳ Unbegrenzte Query-Caches (PO-Entscheidung, Rückversicherung offen)

`SearchQuery.CACHE` und `RegexUtils.GLOB_CACHE` sind unbegrenzte `ConcurrentHashMap`s. Da
Queries von Agenten generiert werden, wächst der Cache theoretisch unbegrenzt.

**PO-Entscheidung (2026-09-03):** vorerst belassen — Einträge sind winzig (String + Pattern),
eine Session erzeugt Dutzende, nicht Millionen. Kein Blocker fürs Release.
**Bei Bedarf:** LRU mit fixer Obergrenze (z.B. 500). → Rückversicherung mit dem User steht aus.

## ❓ PDE-Runner meldet Skips nicht separat

**Gefunden:** 2026-09-03, Zyklus Test-Setup. `eclipseRunTests` gibt gelaufen/fehlgeschlagen
aus, aber keine Skip-Zahl. [test-setup.md](test-setup.md) R5 („0 skipped") ist damit nur
indirekt nachweisbar (Testzahl-Entwicklung, kein `@Ignore`/`Assume` neu).

**Frage an den User:** Skip-Zahl im Tool-Report ergänzen (kleiner Fix in `EclipseRunTestTool`)
— lohnt sich das, oder reicht die indirekte Kontrolle? **PO-Empfehlung:** kleiner Zusatz in
Zyklus 2, kostet fast nichts und macht R5 prüfbar.

## 🔒 `eclipseReadFile` kürzt lange Ausgaben — **widerlegt**

**Gefunden:** 2026-09-03 (Dev). **Geklärt:** 2026-09-03 (Plan-Agent, Pfadanalyse vom
Tool-Return bis in `ChatRequest.messages`). Es gibt **keine** stille Kürzung; das beobachtete
Verhalten war R1a/R1b (Bereich ignoriert → Ganzdatei) bzw. die UI-Anzeige. R1d in
[eclipse-read-tools.md](eclipse-read-tools.md) zurückgezogen, kein Zeilen-Cap.
→ nach [resolved-points.md](resolved-points.md) überführt.

## ❓ `buildWithDev` sollte Da Mek vorher compacten

**Beobachtung (User, 2026-09-03):** Wenn Jon über `buildWithDev` bauen lässt, sollte Da Mek
„möglichst leer" starten — die Plan-Datei ist die Übergabe, nicht sein Restkontext aus der
vorigen Runde.

**Vorschlag:** In den PO-Delegations-Tools vor dem Build automatisch `compactDev` auslösen,
**wenn** nennenswerter Kontext vorhanden ist (Schwelle nötig — z.B. > X % Fenster).
User: „da brauchen wir kein Test".

**Offene Design-Fragen:** (1) Compact oder harter Reset? Compact bewahrt gelernte
Projekt-Eigenheiten, Reset ist wirklich leer. (2) Ab welcher Schwelle? (3) Auch für
`planWithPlanAgent` (Da Thinka)? (4) Gilt das auch für Delta-Pläne nach einem Review — dort
ist der Restkontext ja gerade nützlich?

**PO-Empfehlung:** Compact statt Reset, Schwelle ~50 % Fenster, **nur** beim Start eines
neuen Plans (nicht bei Nacharbeit/Delta) — sonst verliert Da Mek genau das Wissen, das die
Nacharbeit billig macht. Eigene kleine Story nach dem Release.

## ❓ Deferred Smoke-Test-Kosmetik (User: „Kosmetik ist mir erstmal egal")

- **Bug 1:** zwei horizontale Striche zwischen Selected-File und Skill-Liste — einer raus bzw.
  repositionieren.
- **Bug 2:** Scrollverhalten in der Advanced Config wirkt komisch.
- **Punkt 4 (Dropdown Look & Feel):** Der Custom-Dropdown-Umbau wurde am 2026-09-03
  **descoped** (brach den Build). Die Klassen `DropdownItem`/`DropdownTheme`/`DropdownButton`/
  `DropdownPopup` liegen unbenutzt und kompilierbar im Plugin; kein Wiring in
  `ActionsBarWidget`/`ModelComboWidget`. Wiederaufnahme = eigene Story nach dem Release.

**Frage an den User:** nach dem Release angehen — oder die Dropdown-Klassen ersatzlos löschen?
