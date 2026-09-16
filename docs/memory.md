# Session-Stand — 2026-09-16 (Abend)

## Wo wir stehen

**Branch `bugfix/user-context-selection` (von main), 3 Commits unpushed:**
- `2e51a16` — Inc 1: SimpleDiff-Guard (`MAX_LCS_CELLS=5_000_000`, Summary statt OOM, 4 Tests)
- `5ceb3aa` — Inc 2: Selection-Fix R-SEL-1…3 (roter Test zuerst, UserContext-Choke-Point,
  Snippet+Pfad statt Full-Content, `StandingOrdersBuilderTest` SOLL-befugt umgeschrieben)
- `c958d78` — Review-Fixes: distinctive Pfad-Assertion (`/test_project/pom.xml`), `setClassFile(null)`
  in `applyTextSelection` (stale-clazz/Job-Thread-Pfad geschlossen)

**Review: CONCERNS → abgenommen** (3 Gaps behandelt: Assertion geschärft, clazz-Fix, UC-SEL-3-BDD2
manuell). **Plugin 216/0 · Core Surefire 866/0 · Linter UC 3/3, 0 UC-SEL-Befunde.**
Docs: `user-context.md` ✅ done (R-SEL-1…3, UC-SEL-1/2/3); open-points: SimpleDiff + Selection 🔒,
neu ❓ Linter-idPattern-Verifikation. Mutation-Note: Setter-Reihenfolge in `applyTextSelection`
nicht headless-testbar → User-Smoke deckt ab.

## Crash-Aufklärung (SimpleDiff-OOM, für Rückfragen)

Kein Bedienfehler: `lcsDiff` allokiert `int[m+1][n+1]` (≈4·m·n Bytes) synchron auf dem Tool-Thread.
In-Memory-Zustand der open-points.md war auf ~7,3 Mio. Zeilen korrupt (21997 Insert-Duplikate —
Replace/Insert-Bug-Klasse, open-points „Neue Evidence"), Disk war IMMER sauber (332 Zeilen,
git-clean). 7,3M × 332 ≈ 9,7 GB > 4 GB Heap → OOM. Normal-Docs (300×300 ≈ 360 KB) sicher.
Docs-Reparatur via `eclipseWriteFile` (umgeht Diff-Pfad), danach In-Memory-Modell geheilt.

## Nächste Schritte

1. **Paul: User-Smoke am lebenden System** — README.md Zeilen 9-10 selektieren → Nachricht senden:
   Kontext muss Pfad + „Selected lines 9-10" + Snippet zeigen (nicht „not in a file", kein
   Volltext), Statuszeile zeigt die Selektion; Nicht-Text-Events (Outline/Explorer-Klick) dürfen
   sie nicht räumen. Deckt UC-SEL-3 BDD2 + Mutation (Setter-Reihenfolge).
2. **Paul: Merge/Squash** `bugfix/user-context-selection` → main.
3. Danach Da Mek: `planImplemented` (archiviert Plan) — erst nach Bestätigung.
4. Backlog-Reihenfolge Pauls: Compact-Input-Budget Light + Context-Noise (gemeinsam vor dem Bau),
   ApiRetry-Cancel-Bug, ID-Kommentare Alt-DL-Tests, Linter-idPattern-Verifikation (klein).

## Offene Punkte

- ❓ Linter `idPattern` 0/0-Verifikation (open-points.md) — klein.
- ❓ BDD-Test Compact-Hint-Fallback (Backlog, Paul: „wann anders").
- ⏳ User-Smoke Compact-Buttons (Teil-Smoke offen) · Branch `release-2026-09-06` Merge = User-Entscheid.
- 🐞 Replace/Insert-In-Memory-Korruption (Evidence 2026-09-16, open-points) — Tool-Bug-Zyklus.

## Was nicht neu aufgemacht wird

- Keine Tests auf Prompt-Inhalte (prompts.md R1) · kein Overlay-ToolService (ADR-0048) ·
  kein persistenter ID-Zähler (R-DL-16) · Homepage `usage/selections.md` bleibt unangetastet
  (SOLL-SOT für R-SEL-3) · kein Async-Diff-Umzug (descoped, Guard ist der Kern).

## Lektionen

1. **Nach OOM-Crash zuerst Disk-Truth prüfen** (Da Mek, Shell) — Eclipse-Reads können korrupt sein,
   Disk intakt. Reparatur über `eclipseWriteFile` (kein Diff-Pfad), nie über line-based Edits.
2. **Test-Inventar-Schwelle (Memory #33) gilt auch für Verhaltensänderungen:** R-SEL-3 änderte das
   Rendering — `StandingOrdersBuilderTest` ping das alte Verhalten fest und fehlte im Inventar
   (Da Mek hat gestoppt statt still umgeschrieben — STOP-AND-ASK funktioniert).
3. Review-Fixes als eigener Commit (`c958d78`) — Code-only, Docs bleiben beim PO.
