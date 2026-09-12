# Open Points

Status je Punkt: ❓ offen · ⏳ selbst entschieden (Rückversicherung mit User steht aus) · 🔒 geklärt.
## ❓ ApiRetry: Cancellation-Evidenz-Sammlung (Priorität: hoch, 3. Evidence 2026-09-11)

**Befund-Klassen (alle derselbe Shape: Call bricht, statt dass ApiRetry sichtbar retryt):**
1. **Netzwerk-Failures ohne Retry** (2026-09-10, warning-cleanup): `HttpTimeoutException` +
   `ConnectException` brachen den Call ohne sichtbaren Retry ab — im Gegensatz zu
   HTTP-Status-Fehlern.
2. **Connection reset mitten im SSE-Stream** (2026-09-10, mcp-fixes): `reviewPlanAgent` starb mit
   `ToolExecutionException: closed` ← SSE `Connection reset` (`IOException: chunked transfer
   encoding, state: READING_LENGTH` → `SocketException`).
3. **„AI call canceled while waiting to retry"** (2026-09-02 memory #21; erneut 2026-09-11:
   Da-Mek-Lauf (Branch-Konsolidierung) + Compact-Session starb mittendrin am selben String) —
   Retry-Thread stirbt im Backoff, Meldung endet wie gecancelt.
4. **`IOException: header parser received no bytes`** (2026-09-06): „attempt 1, retrying in 10s"
   gefolgt von Cancel — Verdacht: Null-Byte-IOException wird fälschlich als Cancel klassifiziert
   statt als retry-würdiger API-Fehler.

**Investigations-Fragen:** Retry-Klassifikation für empty-response/Network-Level (Connect/Timeout/
Reset) prüfen — Cancel-Misclassification? Kann ein API-Call den Backoff abbrechen ohne echtes
Cancel? Companion-Story: **Live-Status im Retry-Fenster** (unten).

## ❓ Live-Status im Retry-Backoff-Fenster (2026-09-10)

**IST:** Nach Connection-Abbruch versteckt `StreamingBridge.onError` (END-Chunk) die
Live-Statuszeile, PROBLEM-Nachrichten ebenso → während des ApiRetry-Backoffs (10s…5min):
Funkstille — keine Tokens, kein „working since", User liest es als „hängt".

**SOLL-Idee:** Statuszeile zeigt im Backoff-Fenster den Retry-Zustand („retrying in Xs").
Eigene Mini-Story, nicht Teil von [chat-job-lifecycle.md](chat-job-lifecycle.md).

## ❓ Shell-Tool für Plan-/Review-Agent — Whitelist-Capability? (2026-09-10, User)

Plan-/Review-Agent sollen ggf. `git`/`mvn`/`npm` nutzen — heute ohne Shell-Tool. Optionen: volles
ShellTool, reduziert, oder Whitelist-Capability im ShellTool selbst (analog Write-Validator):
Pattern-Liste per Agent konfigurierbar. **Status: nur Ticket** — „erst fertig werden, dann was
Neues." Offene Fragen: Whitelist pro Agent oder global? Default-Set? Read-only-Filter (push?)?
Verwandt: ADR-0015 (sandbox), ADR-0022 (Write-Path-Allowlist, Proposed).

## ⏳ Jackson 2 → 3: beobachten, Migration erst bei voller Entfernbarkeit (User 2026-09-10)

langchain4j 1.20.0 macht Jackson 3 **opt-in** (`langchain4j-jackson3`; Default bleibt Jackson 2).
Nicht heute migrierbar: (1) openai-java pinnt Jackson 2 (nicht unter unserer Kontrolle),
(2) eigene Core-Nutzung in 5 Dateien müsste mit, (3) Error-Path-Änderung
(`JsonReadException` statt Jackson-Exceptions) → ApiRetry-Klassifikation prüfen.
**Entscheidung:** Beobachten — Migration erst, wenn Jackson 2 vollständig entfernbar (auch aus
openai). Revisit-Trigger: langchain4j 1.21+ (Aggregator schon auf 1.21.0-beta31) oder openai-java
Jackson-3-Support. Dann eigene Story mit ADR (Major-Sprung, OSGi-Bundle-ClassPath, Error-Path).

## ❓ buildWithDev sollte Da Mek vorher compacten (2026-09-03)

Vor `buildWithDev` automatisch `compactDev` bei nennenswertem Kontext — die Plan-Datei ist die
Übergabe, nicht der Restkontext. User: „da brauchen wir kein Test". **PO-Empfehlung:** Compact
statt Reset, Schwelle ~50 % Fenster, nur beim Start eines neuen Plans (nicht bei Delta/Nacharbeit
— dort ist der Restkontext die Ersparnis). Eigene kleine Story.

## ⏳ Unbegrenzte Query-Caches (PO-Entscheidung, Rückversicherung offen)

`SearchQuery.CACHE` + `RegexUtils.GLOB_CACHE` sind unbegrenzte `ConcurrentHashMap`s.
**PO-Entscheidung (2026-09-03):** vorerst belassen — Einträge winzig, Session erzeugt Dutzende.
Bei Bedarf: LRU mit Obergrenze (z. B. 500). Rückversicherung mit User steht aus.

## ⏳ Streaming-Timing: Präzisierungen (2026-09-05, streaming-display.md R19)

1. **Total-Timer Stop:** Bridge kennt kein Turn-Ende — Total startet im Konstruktor, läuft durch
   die Bridge-Lebenszeit; Anzeige nutzt `startedAt` aus dem Chunk.
2. **TOOL-Chunk-Value:** R21 zählt gestreamten Text — für TOOL heißt das `partialArguments()`
   (Delta-Slice); der Tool-Name wird nicht gezählt.

## ⏳ Edit-Tools: Naming-Uniformität + gemeinsame Doku (geparkt 2026-09-05)

- Rename auf **"Edit"**: `eclipseUpdateOpenFile` → `eclipseEditOpenFile`, `planUpdate` →
  `planEdit` (Verb-Familie konsistent; "Update" kollidiert mit Write/overwrite).
- Gemeinsame Doku für die 4 Edit-Tools (`FileUtils.applyEdit` = Replace-All + Count + 0 = Fehler):
  neue `edit-tools.md` (PO-Empfehlung) vs kanonisch in disk-file-write-tool.md.
- `planUpdate`/`planEdit` meldet noch **keine Count** — nachziehen.
- **Konflikt offen:** eclipse-workspace-write-file-tool.md sagt noch „Errors if 0 or >1 matches"
  — widerspricht Bug-Hunt #1 (Replace-All + Count). Muss mitfixt werden.
- Nebenbefund: `planUpdate` überreicht dem Monitor Parameter statt Content — Editor-Diff falsch.
- **Line-Ending-Normalisierung (User, 2026-09-05, `file-edit-tools.txt`):** falsches Ending im
  oldString → Tool normalisiert **beide** Strings; anderes Ending im newString → wörtlich
  übernommen, kein Fehler. Ersetzt frühere E3-Entscheidung. E2E-Spec:
  `org.sterl.llmpeon.test/ai-e2e-test/file-edit-tools.txt`.

## ❓ Deferred Smoke-Test-Kosmetik (User: „Kosmetik ist mir erstmal egal")

- Zwei horizontale Striche zwischen Selected-File und Skill-Liste — einer raus.
- Scrollverhalten Advanced Config wirkt komisch.
- Dropdown-Umbau descoped (2026-09-03), Klassen gelöscht (`a1d8d35`, Git-Historie) —
  Wiederaufnahme = eigene Story.

- **⏳ 2026-09-12 — R-A2 SOLL-Präzisierung:** „Labels bündig links" konkretisiert als „exakt wie
  die übrigen Label/Feld-Paare derselben Page" (Label in der Page-Label-Spalte mit
  Sibling-Ausrichtung: Basic = LEFT/JFace, Advanced = END/addLabel). Abgeleitet aus der
  User-Beschwerde („Model-Label rechts aligned wo die anderen links sind"); Bestätigung im
  User-Smoke nach Inc-2. Will der User LEFT auf BEIDEN Pages → R-A3 (Label-Gestaltung).
- **⏳ 2026-09-12 — Docs-SOLL-Hygiene-Sweep:** User-Direktive: Docs = reines SOLL, keine
  implementierten Bug-/„war:"-Narrativen (der Plan trägt den Diff SOLL/IST). Umgesetzt für
  advanced-configuration.md + model-loading.md (UI-Zyklus). Offen: Sweep über die übrigen
  Feature-Docs (weitere „war:"-Blöcke, alte IST-Abschnitte) — als eigener Aufwasch, Scope vom
  User bestätigen lassen.