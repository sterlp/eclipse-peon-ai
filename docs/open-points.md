# Open Points

Status je Punkt: ❓ offen · ⏳ selbst entschieden (Rückversicherung mit User steht aus) · 🔒 geklärt.
Geklärte Punkte ohne eigenes Feature-Doc: [resolved-points.md](resolved-points.md).

## Bug-Fix-Zyklus-Backlog (2026-09-24, priorisiert)

1. **R-CC-7 — Compact-Fehler sichtbar + begrenzter Retry** ([compact-context-counter.md](compact-context-counter.md)):
   Fehler ans LLM („compact failed" + Ursache) + onProblem; Retry 1× nach 20s nur transient,
   deterministische Fehler sofort ehrlich. Zusammen mit der ApiRetry-non-retryable-Klassifikation
   (eine Fehlerklassen-Tabelle, zwei Verbraucher). Evidenz: header-state-leak.md Fall 1+2.
2. **Header-State-Leak** ([header-state-leak.md](header-state-leak.md)): onProblem rendert den
   Header-State neu (🟢/Zähler/Working-Hint hängen nach Fehlerpfaden); IST-Messung vor der
   SOLL-Härtung offen (letzter gültiger Wert vs. aktiv falsch gesetzt).
3. **ApiRetry** (❓ unten, Evidence-Sammlung): non-retryable-Klassifikation + Mindest-Retry bei
   Connect-Level-Failures (Paul-Idee 2026-09-20) — in die gleiche Fehlerklassen-Tabelle.
4. ⏳ `ShellTool.confirmationProvider` non-volatile — pre-existing, harmlos, 1-Wort-Fix bei
   nächster Berührung (Da-Dok-Hinweis R-TC-Review).

## ❓ Workspace-Memory-Snapshot: Vollkopie je `memoryAdd` (2026-09-23)

Snapshot-Key ist der entries-Hash (ADR-0032) — jede Mutation erzeugt eine frische Vollkopie
aller Einträge, bis zum Compact. By design, aber die Kosten skalieren schlecht. Frage an Paul:
eigener Design-Punkt? (inkrementeller Snapshot / Dedup je Eintrag / Compact-frequenter).
Verwandt: [compact-context-counter.md](compact-context-counter.md) „Offen".

## ❓ Tool-Time-Disclosure Restkandidaten (2026-09-22, Option B — Paul-Scope)

`webFetchAsMarkdown` (Cache-Frische), `memoryAdd/Replace` (Datum-Bestätigung), `JavaDebugTool.continue`
(Dauer), searchAgent/compactSession/lint (Konsistenz) — je eigene Mini-Story. Read/Grep/Write-Familie
bleibt ohne Zeitinfo (stateless, Rauschen).

## ❓ Gelber Compact-Indikator (🟡) im Roster (2026-09-22, Paul)

„Besser als 🟢, aber grün ist auch vollkommen okay" — bewusst nicht gebaut. Umsetzung: zweiter
Anzeige-Zustand `compacting` + 🟡-Präfix in `AiAgentStatusWidget.text()`. Wiederaufnahme = Mini-Increment.
Kontext: [compact-lock.md](compact-lock.md).

## 🚧 „!-Messages" — sofortiger History-Insert auch im ToolLoop (2026-09-22, Paul)

Regel 8 in [queued-user-messages.md](queued-user-messages.md); Insert-Punkt/Race offen. Eigene Story.

## ❓ Tool-Evolution PO-Run CR-Verdicts (2026-09-19)

Alle CR-Items entschieden — Verdicts + Begründungen: [resolved-points.md](resolved-points.md)
(„Tool-Evolution-Run"). Offen bleiben nur die dort gelisteten ❌-Stories (project-problems,
debugger, web-tools — teils inzwischen ✅) und die geparkten Docs (terminal-session, tool-confirmation).

## ❓ `applyEdit` Not-Found dumpet das gesamte File (2026-09-19 — bewusst so, Lösung offen)

**Paul: bewusst so** — der Dump spart den Read-Roundtrip im Fehlerfall. Offen: Roundtrip vs.
Context-Bombe bei großen Dateien; gute Lösung (Kontext-Fenster um die ähnlichste Fundstelle)
existiert nicht. Bleibt stehen, kein Bau.

## ❓ ApiRetry: Cancellation-/Retry-Klassifikation (Priorität hoch, Evidence 4×)

Befund-Klassen (derselbe Shape: Call bricht statt sichtbarem Retry):
1. `HttpTimeoutException`/`ConnectException` ohne Retry (2026-09-10).
2. SSE `Connection reset` mitten im Stream (2026-09-10).
3. „AI call canceled while waiting to retry" — Retry-Thread stirbt im Backoff (2026-09-02/11;
   **erneut 2026-09-24 doppelt live**: Da-Dok-Review + Da-Thinka-Plan, siehe
   [header-state-leak.md](header-state-leak.md)).
4. `IOException: header parser received no bytes` — Verdacht: Null-Byte-IOException als Cancel
   klassifiziert (2026-09-06).
5. **Neu 2026-09-20:** llama.cpp-Crash → 3× buildWithDev-Abbruch (`ConnectException`/
   `ClosedChannelException`/no-bytes). **Pauls Idee:** mindestens 1 Retry nach ~10s auch bei
   Connect-Level-Failures (Crash+Restart dauert meist Sekunden).

→ Lösungsweg: gemeinsame **Fehlerklassen-Tabelle** mit R-CC-7 (transient → Retry;
`exceed_context_size`/Invalid-Request → sofort ehrlich failen), dann Klassifikation
(Cancel-Misclassification?) in ApiRetry.

## ❓ Live-Status im Retry-Backoff-Fenster (2026-09-10)

`StreamingBridge.onError` versteckt die Statuszeile → 10s…5min Funkstille (User liest „hängt").
SOLL-Idee: „retrying in Xs" im Backoff-Fenster. Mini-Story, verwandt mit header-state-leak.

## ❓ Shell-Tool für Plan-/Review-Agent — Whitelist-Capability? (2026-09-10/13)

Use-Case belegt (Da Dok konnte im Release-Scan Commits nicht isolieren — kein Git). Optionen:
volles ShellTool / reduziert / Whitelist pro Agent (analog Write-Validator). Offen: Scope, Default-Set,
Read-only-Filter. Verwandt: ADR-0015, ADR-0022.

## ⏳ Jackson 2 → 3: beobachten (2026-09-10, User)

Migration erst bei voller Entfernbarkeit (openai-java pinnt Jackson 2; 5 eigene Dateien; Error-Path
ändert sich → ApiRetry-Klassifikation prüfen). Revisit-Trigger: langchain4j 1.21+/openai-Jackson-3.
Dann eigene Story mit ADR.

## ❓ buildWithDev sollte Da Mek vorher compacten (2026-09-03)

Compact statt Reset bei nennenswertem Kontext, Schwelle ~50 %, nur bei neuem Plan (nicht bei
Delta/Nacharbeit). User: „da brauchen wir kein Test". Eigene kleine Story.

## ⏳ Unbegrenzte Query-Caches (Rückversicherung offen)

`SearchQuery.CACHE` + `RegexUtils.GLOB_CACHE` unbegrenzt — belassen (Einträge winzig); bei Bedarf
LRU 500.

## ⏳ Streaming-Timing-Präzisierungen (2026-09-05, streaming-display.md R19)

Total-Timer läuft durch die Bridge-Lebenszeit (Anzeige nutzt `startedAt`); TOOL-Chunk zählt
`partialArguments()`-Delta.

## ⏳ Edit-Tools: Naming-Uniformität + gemeinsame Doku (geparkt 2026-09-05)

Rename auf „Edit" (`eclipseUpdateOpenFile`→`eclipseEditOpenFile`, `planUpdate`→`planEdit`);
gemeinsame `edit-tools.md`; `planUpdate` ohne Count-Disclosure (nachziehen); **Konflikt offen:**
eclipse-workspace-write-file-tool.md sagt noch „Errors if 0 or >1 matches" — widerspricht
Bug-Hunt #1 (Replace-All + Count), muss mitfixt werden. Line-Ending-Normalisierung: E2E-Spec
`file-edit-tools.txt`.

## ❓ Deferred Smoke-Test-Kosmetik (User: „Kosmetik ist mir erstmal egal")

Statuszeilen-Striche, Scrollverhalten Advanced Config. Dropdown-Umbau descoped (Klassen gelöscht
`a1d8d35`), Wiederaufnahme = eigene Story.

## ⏳ Docs-SOLL-Hygiene-Sweep (2026-09-12)

Docs = reines SOLL, keine „war:"-Narrativen. Umgesetzt für advanced-configuration + model-loading;
Sweep über die übrigen Feature-Docs als eigener Aufwasch, Scope vom User bestätigen lassen.

## ⏳ Compressor-Dedup-Subtext-Edge / ThreadSafeMemory RAM-only (2026-09-13, Da-Dok Release-Scan)

Dedup kann Einzel-Nachricht als Teiltext unterschlagen (Compact ist lossy — akzeptiert);
nach Persist-IOException läuft die Session RAM-only weiter (präexistierendes Muster). Keine
Blocker; Revisit nur bei Beschwerden/Datenverlust-Meldungen.

## Compact Input Budget ([compact-input-budget.md](compact-input-budget.md))

- ❓ Context-Noise zuerst raus (User-Idee, 2026-09-13): Context-Item-Messages vor dem Kürzen
  nehmen — zusammen mit der Light-Version ausarbeiten („#2 und #5 gehören zusammen").
- 🔒 R1–R5 SOLL festgelegt, Story ❌ specified. Reihenfolge: erst Compact-Slot-Bug, dann Budget-Light + Noise.

## Neu (2026-09-14, story/po-compact-2026-09-13)

| Punkt | Status | Notiz |
|---|---|---|
| `homepage/src/setup/peon-po.md` listet das Team ohne Da Dok | ❓ offen | Korrektur im nächsten Homepage-Kontakt |
| User-Smoke Header-Compact-Buttons | ⏳ teils | Optik ✅; ausstehend: Re-Compact-Noop, Disabled-States, Tooltip — nach Merge |
| BDD-Test Compact-Hint-Fallback | ❓ Backlog | Regel gebaut (`29a341b`), Paul: „wann anders" |

## ⏳ R-SEL-4 Umsetzungsdetails (2026-09-16 — Rückversicherung steht aus)

Paul bestätigte „beides" (`IClassFile` + `IType`). Drei selbst abgeleitete Details (Plan §2.1/§6):
(1) jeder `setTextSelection` (auch leer) räumt den Typ — strikte Text/Typ-Alternation;
(2) Rename `clazz`/`setClassFile` → `javaType`/`setJavaType`; (3) Typ-Event berührt `currentProject`
nicht. Widerspruch = Verhalten zurückändern, Tests (UC-SEL-4) anpassen.

## ⏳ `eclipseBuildProject` Failure: build.properties-Warnung (seit #136)

„class folder 'resources/' not associated to any output library entry" bei grüner Kompilation.
Pre-existing (Da Mek 2026-09-21 verifiziert). Separater Mini-Fix-Kandidat.

## ⏳ Eclipse-Installationsfehler User — Issue #142

Analyse korrigiert (2026-09-19): unser p2-Repo liefert asm 9.10.1 mit (includeAllDependencies).
Fix-Kandidaten + Follow-ups: [issue-142-asm-conflict.md](issue-142-asm-conflict.md). Nachzuholen
bei Pauls Zustimmung: Mindest-Eclipse-Version auf der Homepage nennen.
