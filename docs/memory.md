# Session-Stand — 2026-09-22 (Compact-Lock gebaut & reviewed, Smoke + Flip offen)

## Wo wir stehen

**Branch `story/compact-lock-2026-09-22`** (von `story/f2-debug-linter-2026-09-21`; Basis-Kette:
tool-evolution ← f2 ← compact-lock; Merge/Squash = Paul).

- ✅ **Compact-Lock gebaut:** I1 `18ae8eb` (CAS-Hülle `AbstractAgent.compact()`, Acquire vor
  R16-Guard, Release nur `if (acquired)`) · I2 `f059c68` (`call(null)`+Queue → `[Queued Message]:`-
  Payload, D5-`\nnull`-Fix) · I3 `d6b11b5` (Follow-up-Trigger in `handleDoneChatResponse` @Nullable
  `compactedAgent`, selbes UI-Runnable wie Unlock, Identity-Check Active Agent, D6 feuert bei jedem
  Abschluss) · Plan-Status `70ebd77` · Nacharbeiten `7e6afc5` (Javadoc selbsttragend + Companion-Pin
  `compactFailedReleasesWorkingFlag`) · Docs/Plan-Commits `b9ca08b` (4 f2-Docs + f2-Plan-Archiv +
  Da-Dok-Review-Sektion).
- Tests: Core Surefire 908/0, Plugin 261/0. Review **Da Dok: ACCEPTED** (3-Seiten, Zeilen-Evidenz im
  Plan-Datei-Ende). Meine Stichprobe ✅ (AbstractAgent.java:282/311-312, AIChatView.java:678-684).
- SOLL: `docs/compact-lock.md` (R-CT-1…4, UC-CT-1…6, **❌**), ADR-0052, chat-job-lifecycle.md
  Backlog → ❌. 🟡-Indicator + "!"-Messages (queued-user-messages.md Regel 8, 🚧) bewusst NICHT gebaut.

## Nächste Schritte

1. **Paul: Smoke 1–4** (Plan §9, UC-CT-3…6 manuell) — siehe Liste unten.
2. Danach: Flips UC-CT-1…6 ❌→✅ durch Jon (nach Eclipse-Restart + frischem `lintDocsAndTests` —
   die laufende Instanz lädt stale llmpeon-core, R-DL-22-Marker-Logik fehlt dort).
3. **Eclipse-Restart → Dogfood-Lint prüfen:** UC-JD-2…6 als MANUELL-Zeilen, UC-DL-99 weg (Altbestand
   f2-Zyklus) + dann UC-CT-Prüfung.
4. Da Mek wartet auf Ansage `planImplemented` (Plan-Archiv) — nach Flip.
5. Merge/Squash der Branch-Kette = Paul.
6. Geparkt: ApiRetry-Follow-up, Issue #142-ADR, build.properties-Warnung, DL-Test-ID-Sweep (~33).

## Smoke-Liste (UC-CT-3…6, manuell)

1. Send während Boss-Compact (autoCompactAfter klein, viele Messages) → Ack „queued", KEIN paralleler
   2. Job, Roster zeigt Agenten working.
2. Nach Compact-Ende: Follow-up-Turn verarbeitet Queue FIFO (eine Antwort).
3. Compact fehlschlagen (leere Compressor-Response erzwingen) → Queue trotzdem Follow-up.
4. Slave-Compact (Da Mek/Da Thinka Button) → nur Slave 🟢, Blatt-Regel Da Boss bleibt aus, kein
   Follow-up am Boss.

## Lektionen (Zyklus)

1. Eigene Dogfood-Tools in der laufenden Eclipse-Instanz testen STALE Code — Disk-Grep + Suite sind
   der Beweis (übernommen aus f2-Zyklus, weiter gültig).
2. Plan-Datei je Zyklus committen — f2-Plan war uncommitted, Archiv war verloren (in `b9ca08b`
   nachgeholt).