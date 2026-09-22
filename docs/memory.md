# Session-Stand — 2026-09-22 (TD ✅ · Compact-Smoke ok · Debugger-Bug-Hunt läuft)

## Debugger-Bug-Hunt (E2E 2026-09-22, test_project/issue.md — Triage nach Memory #22)

- **F1 Exception-BP tot (Paul: Blocker):** Da Mek exkludierte JDI/JDT (raw-JDI-Experiment feuert;
  `initializeBreakpoints` installiert Marker-BPs bei Target-Start). Verdacht: E2E-Setup/Phantom-BPs.
- **F2-A „4. Hit": JDI-Quirk bestätigt** (Suspend-Position = stiller Hit #1) — e2e-Doc 3.2 jetzt mit
  Zähl-Definition korrigiert. **F2-B „2. Hit": unklar** — Pauls Erinnerung: BP war sauber und griff;
  Re-Run 3.2/3b mit Clean-State entscheidet.
- **Bonus-Befund:** Request bleibt nach hitCount-Verbrauch enabled (Refire) — JDI-Spec-Verstoß,
  Ursache/Owner noch offen.
- **Ursachen-Fix-Story Paul:** `list_breakpoints` (R-JD-11/UC-JD-13, bewusst ohne Session —
  Phantom-BPs leben zwischen Sessions) + `hitCount:-1→0` Display-Clamp (R-JD-12/UC-JD-14). SOLL in
  java-debugger-tool.md ❌, e2e-Doc mit Clean-State-Pflicht (3b) + Zeile 23 + Zähl-Definition
  aktualisiert. Bau als Mini-Zyklus, danach Paul: Re-Run 3b/3.2 (1–2 Relaunches).
- Red-Test-Policy (Jon): Fix-Level = headless Stubs (DebugJson-Muster), JDI-Verhalten = Standalone-
  JUnit-Dokumentation, Live-Session manuell (ADR-0051).

# Session-Stand — 2026-09-22 (Compact-Lock ✅ reviewed · Tool-Time-Disclosure ✅ · Smoke offen)

## Wo wir stehen

**Branch `analysis/tool-evolution`** — f2 + compact-lock gemerged (fast-forward, Pauls Anweisung),
danach Tool-Time-Disclosure drüber. Story-Branches (f2, compact-lock) stehen noch (Aufräumen = Paul).
Kein Push; Merge auf main = Paul.

- ✅ **Compact-Lock** (CAS-Hülle, `[Queued Message]`-Payload, Follow-up-Trigger): Core 908/0,
  Plugin 261/0, Da Dok **ACCEPTED** (CONCERNS gefixt, `7e6afc5`/`b9ca08b`). Flips UC-CT-1…6
  **offen** — warten auf Pauls Smoke 1–4 (Liste unten), dann Flip + Lint.
- ✅ **Tool-Time-Disclosure (Option B)** komplett: `CallStats` im core-shared (Clock injizierbar,
  `b74bf1c`), PoDelegateTool migriert (Pin, byte-identisch), Shell/RunTest/Build-Suffix mit
  **gemessener** Dauer (auch im Timeout). Commits `b74bf1c`→`68ab57d`, Plan `6ea1b72`, Docs `081cccd`,
  Archiv `6e16118`. Core 916/0, Plugin 264/0. Da Dok REJECTED war doc-only (Heading-Depth, mein
  Fehler) — gefixt, TD-Scope im Lint clean. Abweichung bestätigt: `formatResults`/`timeoutReport`
  public static (OSGi-Cross-Bundle).
- Docs: `tool-time-disclosure.md` (R/UC-TD-1…4 ✅), ADR-0053 + Index (0052/0053 nachgetragen),
  docs-linter.md **Autor-Konvention** ergänzt (Regel `###`, UC `####` — Da-Dok-Fund).
- ⏳ **Eigener Lint läuft STALE** (Eclipse-Instanz lädt pre-F2-jar): zeigt VERWAIST UC-DL-99,
  UNBELEGT UC-JD-2…6 (statt MANUELL). Nach Pauls Eclipse-Restart erneut prüfen — dann ist der
  f2-Zyklus endgültig abgeschlossen.

## Nächste Schritte

1. **Paul: Smoke 1–4 (Compact-Lock)** — Send während Compact → Ack+kein 2. Job; Follow-up FIFO;
   fehlgeschlagener Compact → Queue trotzdem; Slave-Compact → Blatt-Regel.
2. Paul-Smoke Time-Disclosure mit drüber: Shell/RunTest/Build-Outputs enden mit `(Ns, HH:mm)`.
3. Danach: Flips UC-CT-1…6 (CT-3/4/5/6 mit „manuelle Verifikation"-Marker) + Da Mek archiviert
   Compact-Lock-Plan (wartet auf Ansage).
4. **Story 2 offen: Queued-Message-Zeit** („[Queued Message] (queued 14:32, 3min ago)") — Format-
   Empfehlung von Jon steht, **Pauls Bestätigung fehlt**; SOLL nach queued-user-messages.md, dann
   eigener Mini-Zyklus.
5. Eclipse-Restart → Dogfood-Lint (f2-Abschluss).
6. Merge/Squash = Paul. Geparkt: ApiRetry, Issue #142-ADR, build.properties-Warnung, DL-Sweep (~33),
   TD-Restkandidaten (open-points.md ❓), 🟡-Indicator, "!-Messages".

## Smoke-Liste (manuell, Compact-Lock CT-3…6)

1. Send während Boss-Compact → Ack „queued", kein 2. Job, Agent 🟢 working.
2. Nach Compact-Ende → Follow-up-Turn verarbeitet Queue FIFO.
3. Compact fehlschlagen → Queue trotzdem Follow-up.
4. Slave-Compact → nur Slave 🟢, Da Boss aus (Blatt-Regel), kein Follow-up am Boss.

## Lektionen (Zyklus)

1. Eigene Dogfood-Tools testen STALE Code — Disk-Grep + Suite sind der Beweis (gilt weiter; nach
   Neustart auflösen).
2. Plan-Datei je Zyklus committen (f2-Plan war verloren, `b9ca08b` nachgeholt) — jetzt Routine.
3. Feature-Doc direkt mit `### R-…`/`#### UC-…` anlegen — Heading-Depth-Regel (+1 Stufe) steht
   im Linter-Doc, Konvention jetzt auch als Autor-Notiz (docs-linter.md).