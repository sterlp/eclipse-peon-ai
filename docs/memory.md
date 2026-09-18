# Session-Stand — 2026-09-17

## Wo wir stehen

**Zyklus läuft (Paul-Feedback-Session):** Edit-Guard + Lint-Polish + Read-Zeilennummern.
Docs geschrieben (alle ❌ specified):
- `disk-file-write-tool.md` — **Edit-Guard**: `oldString` Pflicht, `trim().length() >= 3`, up-front in `FileUtils.applyEdit` (alle 3 Oberflächen: diskEditFile, eclipseEditFile, eclipseUpdateOpenFile). IST-Notiz: heutiger Schutz nur zufällig via `countOccurrences` („Content is empty or null - cannot count!").
- `eclipse-workspace-write-file-tool.md` — eclipseEditFile Drift korrigiert (Replace-All), `eclipseUpdateOpenFile` neu dokumentiert (Da-Dok-Lücke C2).
- `docs-linter.md` — R-DL-19 (Scan-Quellen relativ zum Root, UC-DL-62), R-DL-20 (onTool compact mit Zahlen, UC-DL-63).
- `eclipse-read-tools.md` — R8 (Grep liefert Zeilennummern), R9 (Zeilennummern immer, supersedet R1c-Klausel „Ganzdatei ohne Zeilennummern"; R1c-Notiz eingetragen).

**Von Paul selbst gebaut (IST):** `FileUtils.applyEdit` Count-Guard gegen empty-oldString; onTool im DocsLinterTool. Self-Reference-Guard aus `bugfix/edit-tool-insert` ist NICHT mehr in FileUtils — bewusst ersetzt? (offen, nicht blockierend).

## Nächste Schritte

1. planWithPlanAgent: Inkrement (a) Edit-Guard explizit + Tests löschen (DiskEditLastLoopTest, EclipseEditLastLoopTest, EclipseUpdateOpenFileUITest; Growth-Tests behalten), (b) `validateChildRoots` → `QualifiedPathValidator` (NICHT FileUtils — Pfadvalidierung ≠ Edit-Semantik, mit Paul geklärt), (c) R-DL-19/20, (d) R8/R9.
2. Abnahme → buildWithDev → Da Dok. Branch: fragen (`bugfix/edit-tool-insert` liegt von main aus).
3. Offen an Paul: Not-Found-Fehler in `applyEdit` dumpet das GESAMTE File (Token-/OOM-Bombe) — cappen? (❓ noch nicht in open-points)
4. Merge `bugfix/user-context-selection` + User-Smoke (ALT vom 16.9.) = Paul-Entscheid; danach planImplemented für R-SEL-4-Plan.

## Offene Punkte

- ❓ applyEdit Not-Found-Dump cappen (Token-Bombe) — Paul fragen.
- ❓ Self-Ref-Guard entfernt — Absicht bestätigen (klein).
- Alt: ❓ Linter-idPattern-Verifikation · ⏳ User-Smoke Compact-Buttons · release-2026-09-06 Merge.

## Was nicht neu aufgemacht wird

Keine Tests auf Prompt-Inhalte (prompts.md R1) · kein Overlay-ToolService (ADR-0048) · Homepage `usage/selections.md` unangetastet · kein Async-Diff-Umzug.

## Lektionen

1. Edit-Tool-Triage-Reihenfolge: erst oldString null/empty/blank prüfen (billigster, häufigster Agentenfehler), dann Self-Reference, dann Race Conditions — die 2h-Stress-Test-Jagd hätte ein Input-Validierungs-Blick erspart.
2. UI-Harness-Tests gegen das falsche Tool (`eclipseUpdateOpenFile` statt `eclipseEditFile`) erzeugen Schein-Abdeckung + Save-Dialog-Side-Effects.
