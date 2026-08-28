# Hardcodiertes `"\n"` im System-Prompt-Rendering + stray Quote in `StaticContextItem`

Status: `✅ done (2026-08-21)` — `System.lineSeparator()` in `StaticContextItem.render()` + `WorkspaceMemoryTool.get()` (ADR-0014), stray `'` entfernt; dedupKey-Format `<pfad>:` + LS + ` content with line numbers:` (ADR-0031), Test auf `FileLines.format` umgestellt.

## Evidenz

- `org.sterl.llmpeon/src/org/sterl/llmpeon/context/StaticContextItem.java:16-20` —
  `render()` baut den Env-Block mit hartcodierten `"\n"` (5×), gegen die Projekt-Regel
  für Line-Separators (ADR-0014, Memory-Regel 7).
- `StaticContextItem.java:18` — zusätzlich ein **stray `'`** nach dem
  `LineSeparatorUtil.getDefaultLineSeparatorForLlm()`:
  `+ "\nos line.separator: " + ... + "'"` -> im Prompt sichtbar als
  `os line.separator: \n Unix/Linux (LF)'` (nachweisbar im aktuellen System-Prompt).
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/memory/WorkspaceMemoryTool.java:146,151` —
  `get()` rendert den Memory-Block ebenfalls mit `"\n\n"` bzw. `"\n"` — gleicher Block-Typ,
  gleicher Zielort (System-Prompt via `PeonAiService.java:191`).

## Problem

Konkreter Ablauf: Auf Windows (oder jedem OS mit anderem `file.separator`/Line-Separator)
enthält der System-Prompt Unix-`\n`, während der Rest der App `System.lineSeparator()`
nutzt; der LLM bekommt inkonsistente Format-Hinweise. Das stray `'` ist ein reiner
Typo-Artefakt, der in jedem System-Prompt jedes Agents landet und den
`os line.separator`-Wert visuell verfälscht (schließt ein Quote, das nie geöffnet wurde).

## Auswirkung + Schweregrad

**Code-Smell** (Regelverstoß ADR-0014) + **kleiner Bug** (stray `'`). Keine
Funktionsauswirkung, aber beide Strings sind in jedem System-Prompt präsent —
Fix-Vorschlag für die Review: `System.lineSeparator()` (bzw. `LineSeparatorUtil`) in beiden
Klassen verwenden und das `'` auf Zeile 18 entfernen.
