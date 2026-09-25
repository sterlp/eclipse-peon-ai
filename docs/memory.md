# Session-Stand — 2026-09-24 (Thema: Compact — Redesign ✅ in Docs, Plan beauftragt)

## Wo wir stehen

Branch `fix/nextids-bug-report` (alles ✅ vom Vormittag, HEAD `79a6951`, gepusht, nicht gemerged —
Merge = Paul). **Neues Thema läuft** (Agenten reset am 2026-09-24): **Compact-Redesign**.

## Aktuelles Thema: Compact

**SOLL komplett in `docs/compact.md` (konsolidiert 2026-09-24** — compact-input-budget.md +
compact-context-counter.md aufgelöst; alle Links repariert: index, open-points, ADR-0055,
header-state-leak):

- **R-CC-1…6 ✅** (Zähler/Result, Alt-Bestand), **R-CC-7 🚧** (Fehlerklassen + Retry, mit
  ApiRetry-Tabelle — separater Bug-Fix-Zyklus), **R-CIB-1…6 ❌ specified** (neuer Redesign).
- **Pauls Entscheidungen (2026-09-24):** Budget = autoCompactAfter ohne Toleranz (+5% nur Hint) ·
  Stufen: Think-Cap 9000 **front** (`$`-Anker) + nur letzte echte User-Message → Tool-Results+
  Args+Think 6000 (warn) → Endstufe `restTokens×7/2/n` (error, ersetzt fixe 3000-Stufe) ·
  Disclosure **einmal** am Input-Ende (nicht je Message) · Entry-Debug-Log EINMAL (Anfangswerte,
  UI-Abgleich) · Result-Zeile in Log UND ans Agenten · Schätzung EINMAL (×2/7) ·
  Hint/Marker als Konstanten, „echter User-Text" = erster TextContent der UserMessage ·
  **ADRs = Protokoll kein Gesetz**: falsche löschen, überholte mit Begründung behalten,
  nie „Frozen".
- **Da-Dok Review CONCERNS F1–F10 abgearbeitet** (siehe compact.md + ADR-0056); F4/F5/F6/F7/F10
  technisch von mir entschieden. Mutation-Checks: Endstufen-Terminierung + Dedup-Regression.
- **ADR-0056** (compact-Komponente + Render-Modi) ✅ geschrieben; **ADR-0030 → superseded**
  (bleibt bis Landmine-Fix, dann löschen — Task beim Zyklus-Abschluss!).
- **Da Thinka** empfiehlt (talkPlan): core-Package `compact` (CompactStager pure / CompactEngine /
  CompactResult-Record), öffentliche API `AiAgent.compact` bleibt. Slicing: 1 Docs ✅ →
  2 Core-Extraktion → 3 Wiring → 4 R6+UI+Homepage+compressor.md.

## Nächste Schritte

1. **planWithPlanAgent** → Plan für docs/compact.md R-CIB + R-CIB-6-Wiring (Inkremente 2–4),
   STOP-AND-ASK-Regel prominent, Feature-Doc-Pfad nennen, verify „erster TextContent =
   User-Text" am Code, Grep aller `compact()`-Caller.
2. Abnahme (ich) → buildWithDev auf gleichem Branch? **Branch-Frage an Paul** (neues Thema —
   fix/nextids-bug-report noch nicht gemerged; neuer Branch wie `story/compact-input-budget`?).
3. Nach Bau: ADR-0030 löschen (Landmine gefixt), adr/index-Zeile raus.

## Geparkt (unverändert)

R-TC-Smoke (5 Punkte) · Merge → main · Tool-Polish I1–I4 · Compact-Lock-Smoke 3+4 ·
Debugger-Re-Run · Plugin-Docs-Trennung (core vs. UI, „langsam", compact-lock.md wandert dort
hin) · Pollution-Quellen ❓ (open-points) · Übriges: docs/open-points.md.

## Smoke-Liste (manuell) — Compact-Lock CT-3…6: 1+2 ✅ Paul

3. Compact fehlschlagen → Queue trotzdem Follow-up.
4. Slave-Compact → nur Slave 🟢, Da Boss aus (Blatt-Regel), kein Follow-up am Boss.

## Lektionen (persistent → workspace-memory, hier nichts doppeln)