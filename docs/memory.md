# Session-Stand — 2026-09-19

## Wo wir stehen

**Branch `bugfix/user-context-selection`, Zyklus „Edit-Guard + Lint-Polish + Read-Zeilennummern" KOMPLETT:**
- Step-0: Pauls Count-Guard + onTool + 5 SOLL-Docs committed
- `7800a56` Inc 1 Edit-Guard (oldString Pflicht, `trim().length() >= 3`, up-front in `FileUtils.applyEdit`, alle 3 Oberflächen; null→"" entfernt) — Core 875/0, Plugin 222/0
- `15e3eab` Inc 2 Lint-Polish (R-DL-19 `Sources:`-Zeile relativ, R-DL-20 onTool-Statuszeile, UC-DL-62/63 belegt) — Core 880/0 (Worktree-verifiziert)
- `e0538fa` Inc 3 R9 (Ganzdatei MIT Zeilennummern, supersedet R1c-Klausel) + R8 (Grep = Trefferzeilen `pfad:42: text` unpadded, MAX_GREP_LINES=100 + Disclosure, per-File-Counts weg, Clean Break) — Core 884/0, Plugin 223/0
- `fdaed04` AGENTS-DEV: `-pl test -am` zieht Host NICHT in Reaktor → `-pl org.sterl.llmpeon,org.sterl.llmpeon.test -am verify`
- `73bb156` Review-Fixes (staler Timeout-Wrapper umbenannt, unused `effectiveRoot` weg)
- **Review: CONCERNS → gelöst, abgenommen. Plan archiviert (`planImplemented`).** Docs ✅: Edit-Guard, R-DL-19/20, R8/R9. Lint: UC-DL-62/63 belegt, 0 neue Befunde (42 UNBELEGT_ERLEDIGT = vorbestehender Bestand).

**Da Mek IST gecompacted (21%).**

## Nächste Schritte

1. **Paul: Merge/Squash** `bugfix/user-context-selection` → main (enthält R-SEL-4 + R-DL-18 + diesen Zyklus — EIN Branch nach Pauls Wunsch). Alter `bugfix/edit-tool-insert` (Self-Ref-Historie) = Pauls Verwerf-Entscheid.
2. **Paul: User-Smokes** — R-SEL-4 (Java-Type-Selektion im Chat), R8/R9 live (erst nach Plugin-Install sichtbar), Edit-Guard (Probe-Edit mit kurzem oldString).
3. Backlog Pauls: Compact-Input-Budget Light + Context-Noise (gemeinsam vor dem Bau), ApiRetry-Cancel-Bug (heute 3 Connect/Stream-Abbrüche = frische Evidence für open-points #ApiRetry), Linter-idPattern-Verifikation.
4. Neue ❓ in open-points: applyEdit Not-Found-Dump cappen (GO von Paul offen) · Self-Ref-Guard-Verwerfung bestätigen.

## Offene Punkte

- ❓ applyEdit Not-Found-Dump cappen — Paul fragen.
- ❓ Self-Ref-Guard bewusst verworfen? — kurz bestätigen.
- ⏳ R-SEL-4 Umsetzungsdetails (3 Eigenentscheidungen) + User-Smoke — nach Merge.
- ❓ Linter-idPattern-Verifikation · ⏳ User-Smoke Compact-Buttons · ⏳ Jackson-2→3 · release-2026-09-06 Merge.

## Was nicht neu aufgemacht wird

Keine Tests auf Prompt-Inhalte · kein Overlay-ToolService (ADR-0048) · Homepage `usage/selections.md` SOT · R3 Console-Log unangetastet · kein Parameter fürs Grep-Zeilen-Cap (Konstante).

## Lektionen

1. Edit-Tool-Triage: erst oldString null/blank prüfen, dann Self-Reference, dann Race — 2h Stress-Jagd vs. Input-Validation-Blick.
2. Plan-Test-Inventare per Grep über BEIDE Module verifizieren (13 statt 4 Pins — Memory #33 erneut bestätigt, Dev hat korrekt gestoppt/gemeldet).
3. `-pl org.sterl.llmpeon.test -am` = stale-p2-Falle (jetzt in AGENTS-DEV.md, `fdaed04`).
4. Bei LLM-Abbruch im buildWithDev: State+Commits überleben — einfach fortsetzen lassen, Dev prüft IST per Git selbst.
