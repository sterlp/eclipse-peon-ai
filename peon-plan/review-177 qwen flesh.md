# Review PR #179 — Model-Selector pro Agent (Issue #177)

> PO-Review 2026-11-14 (Jon) im Auftrag von Paul — **nur Review, keine Code-Änderung**.
> Detail-Befunde unten stammen vom Review-Agenten (fremder Blick, Diff gegen Feature-Docs).

## PO-Einordnung

- **Verdict: nicht mergen wie geliefert** — 1 blockierender Datenverlust-Pfad (P1), 4 P2, 3 LOW, kein P0.
- Der Fix trifft den Kern von #177: Routing gekapselt in `AgentModelSelection` (Dev → `llm.model`,
  PO/Plan/Review → `llm.agent.<id>.model`, Custom → `AGENT.md`), leer wird nie als `""` gespeichert,
  jede Save-Assertion prüft „andere Keys unverändert". BDD 1–10 haben Tests (BDD 10 nur Widget-Ebene,
  View-Wiring nur per Smoke).
- **Blocker:** eine getippte, noch nicht bestätigte Modell-Eingabe wird bei Config-Apply, Agent-Reload
  und Handoff still überschrieben — `show()` ohne Dirty-Check, `commitPending()` nur an einem Call-Site;
  der spätere FocusOut ist wegen der `shownModel`-Baseline ein No-op.
- **Zwei Punkte nur für Paul:** (1) **Scope** — Commit 1 ist ein Revert-of-Revert von #173, der PR landet
  die Chat-Model-List-Infra neu (22 Dateien, +1322/−37 für einen Bugfix); nur mergen, wenn #173 bewusst
  zurückgeholt wird. (2) **Doc-Hoheit** — der Fork-PR setzt R-ML5 selbst auf `✅ done (… UI-Smoke ✅)`,
  ✅ setzen wir nach eigener Abnahme.
- **Bestandsbefund:** `model-loading.md` hat kein `idPrefix` → R-ML/BDD-Marker sind nicht linter-tracked;
  `PeonAiService.getActiveModel()` (`:295`) beantwortet „effektives Modell" ein zweites Mal.


## Detail-Befunde des Review-Agenten (fremder Blick, ohne Bau-Session)

Review-only: Fork-Branch `Demmtop:fix/177-model-select-per-agent` ist NICHT checked out, Workspace =
`main` @ `0eae76b5` = PR-Basis → Diff "before"-Zustand ist auf der Platte verifizierbar. Reviewt:
`peon-plan/pr179.diff.md` (22 Dateien, +1322/−37, head `93f8235`). Keine Dateien geändert.

**Verdict: REJECTED** — 1 Blocker (verlorener User-Input), 4 MEDIUM, 3 LOW. Kein P0.
Feature-Docs: nur der PR enthält R-ML5 (`org.sterl.llmpeon/docs/model-loading.md`, Diff-Zeile 1224ff) —
Arbeitsbaum-Doc endet bei R-ML4. **Kein Plan** zu #179 → Review ohne Plan.

**Tests: nicht ausgeführt.** Grund: PR-Code liegt nicht im Workspace; der Suite-Lauf auf `main` würde
Vor-PR-Code prüfen und nichts beweisen. Zahlen aus dem PR-Body (Core + PDE grün, UI-Smoke ✅ 2026-11-13)
sind übernommen, nicht verifiziert. Linter: `lintDocs` über `org.sterl.llmpeon/docs` + `homepage/src` →
0 Findings, aber **`model-loading.md` participates nicht** (kein `idPrefix`) → R-ML5/BDD-Marker sind
nicht linter-tracked; Rückverfolgbarkeit Doc↔Test hier manuell geprüft.

## Review findings

### P1 — BUG (blockierend): offene Model-Eingabe wird still überschrieben, `commitPending` hängt an einem Call-Site

`ChatModelComboWidget.show()` (Diff-Zeile 1745ff → `ChatModelComboWidget.java:72-75`) setzt das Feld
bedingungslos auf `effectiveModel()` — kein Dirty-Check. Geflusht wird nur in `onAgentChange`
(`AIChatView.java:500`, Diff-Hunk @@ -475,7 +494,13). Alle übrigen Wechsel-/Refresh-Pfade, die die Doku
selbst als `refreshModelSelector`-Auslöser nennt, flusht nicht:
- `AIChatView.java:442` `refreshAgentUI()` → `refreshModelSelector()`; Reachability: `AIChatView.java:91`
  (`onAgentReload`) → `PeonAiService.java:197` `onAgentReload.run()` (Reload-Tool-Call eines Agenten).
- `AIChatView.java:481` `applyConfig()` → `refreshModelSelector()` (Pref-Listener `AIChatView.java:118-120`).
- `AIChatView.java:544` `onHandoff()` → `refreshModelSelector()`.
- Send-Pfad: kein Flush → Modell-Tipp + Enter sendet den Turn mit dem **alten** Modell; der neue Wert
  kommt erst durch den späteren FocusOut rein.

Trace (fokus-unabhängig, deterministisch): Base `qwen3`, PO aktiv, User tippt `mixtral` ins Feld
(kein FocusOut), schickt die Nachricht → `lockWhileWorking(true)` sperrt das Combo → Agent ruft das
Reload-Tool → `onAgentReload` → `refreshAgentUI` → `show()` schreibt `qwen3` ins Feld und
`shownModel = qwen3` → jeder spätere FocusOut trifft den Early-Return in `commit()`
(`ChatModelComboWidget.java:61`, `text.equalsIgnoreCase(shownModel)`) → `mixtral` ist verloren, ohne
Meldung, ohne Undo. Für die Klick-Pfade (Handoff, Agent-Combo) hängt es von der
FocusOut/Selection-Reihenfolge der Plattform ab — genau die Begründung, mit der der PR sein
`commitPending()` in `onAgentChange` rechtfertigt ("independent of the platform's FocusOut/Selection
order", Klass-Javadoc Diff-Zeile 1715ff).

Doc-Bezug: R-ML5-Regel „Ein FocusOut … speichert beim **vorherigen** Agenten: die View flusht die
offene Eingabe **vor dem Wechsel**" ist nur für den Klick ins Agent-Combo formuliert; dieselbe
Umsetzung-Zeile der Doku zählt „Config-Apply, Agentenwechsel, Agent-Reload, Handoff" als Refresh-Auslöser.
→ SOLL/IST-Konflikt bzw. Doc-Lücke (Checkliste 2): drei der vier genannten Pfade verlieren die Eingabe.

Fix-Richtung (Reparatur entscheidet): Flush **in** `refreshModelSelector()` kapseln statt an einer
Stelle — plus expliziter Flush vor `setActiveAgent` (Reihenfolge in `onAgentChange` bleibt nötig), oder
`show()` verwirft die Überschreibung, solange das Feld dirty ist. Send-Pfad mitflushen und Doc-Regel auf
alle Wechsel-Pfade + Send ausweiten.

### P2 — Doppelte Antwort auf „was ist das effektive Modell" (Checkliste 7)
`PeonAiService.getActiveModel()` (`PeonAiService.java:295-299`, Basis des „No model configured"-Gates
`AIChatView.java:568`) löst effektivModel selbst (`getAgentModelName()` else Base), während
`AgentModelSelection.of(...).effectiveModel()` (`AgentModelSelection.java`, Diff-Zeile 103ff) ab jetzt die
eine Routing-Regel besitzt. Werte stimmen heute überein, aber die Regel liegt zweimal — eine spätere
Änderung (z. B. Scaffold/Custom-Fallback) erwischt nur eine Stelle. `getActiveModel()` soll auf
`AgentModelSelection.of(getActiveAgent(), getConfig()).effectiveModel()` delegieren.

### P2 — `AiAgent.setAgentModelName` bedeutet je Implementierung etwas anderes
Persistiert nur in `CustomAgent` (`AGENT.md`), bei Dev/PO/Plan/Review/Scaffold reine Memory-Aktion
(`AiDevAgent.java:46`, `AiPoAgent.java:144`, `AiPlanAgent.java:56`, `AiReviewAgent.java:61`).
`AgentModelSelection.save` macht die Methode erstmals zum offiziellen Schreibweg (nur `AGENT_FILE`),
und `save` liefert für die anderen Targets `true/false` unabhängig davon — ein Aufrufer kann nicht
erkennen, dass ein Slot-Save über diese Methode nichts persistieren würde. Produktionsaufrufer der
Memory-Varianten nach dem PR: keiner (Referenzen nur `AiDevAgentTest.java:19`, `AiPlanAgentTest.java:69`,
`AiPoAgentTest.java:141`, `AiReviewAgentTest.java:107`, `AiScaffoldAgentTest.java:67`,
`CustomAgentServiceTest.java:381/404/422/439/455`) → die Tests pinnen einen Vertrag, der R-ML5
(„persist") widerspricht. Empfehlung: vier Memory-Overrides löschen (Referenzen mit) oder pro
Implementierung dokumentieren + AGENTS.md-Zeile „slot/base nur über `LlmConfigSaver`/`AgentModelSelection.save`".

### P2 — Doc sagt „höchstens ein Fetch pro Verbindung", Code tut das nur im Erfolgsfall; kein Test
`refreshModelSelector()` (`AIChatView.java:447-449`) feuelt bei jedem Config-Apply/Wechsel/Reload/Handoff
`fetchModels()`; `ModelListCache` cached **nur bei Erfolg** (`ModelListCache.java:16,61-63`) → bei
unerreichbarer Agent-Verbindung ein HTTP-Versuch + Job + `buildWithDefaults()` (Disk-I/O) pro Wechsel.
BDD 9 sagt ausdrücklich „(max. ein Fetch pro Verbindung, Cache)" — dafür existiert nirgends ein Test
(`AgentModelSelectionTest.modelListIsFetchedForTheAgentsOwnConnection` prüft nur die Connection-Identität,
Diff-Zeile 605ff). Entweder Doc-Satz auf „bei Erfolgs-Cache" präzisieren + BDD-9-Cache-Claim streichen,
oder negativer Test (2× Fetch derselben kaputten Verbindung → 1 Attempt).

### P2 — Redundante SWT-Tests (Overtesting, 1 Cluster)
`ChatModelComboWidgetTest` kopiert Basisklassen-Verhalten, ohne den neuen Commit-Vertrag zu prüfen:
- `refreshFailureKeepsPreviousList` (neu, Diff-Zeile ~871) ≈ `ModelComboWidgetTest.java:99-124`
  (gleiches GIVEN/WHEN/THEN, gleiche Assertions, kein `committed`-Assert).
- `typedCaseVariantOfListedModelIsCanonicalized` (neu, Diff-Zeile ~954) ≈
  `ModelComboWidgetTest.java:126-141` (Liste „FOO", getippt „foo", Server-ID gewinnt).
Die übrigen neuen Widget-Tests sind **nicht** redundant (sie asserten die `committed`-Callback-Liste:
`selectionCommitsShownText`, `focusOutCommitsTypedText`, `focusOutWithoutChangeDoesNotCommit`,
`clearedFieldCommitsEmpty`, `commitPendingFlushesTypedTextOnce`, `programmaticSetModelDoesNotCommit`,
`showDisplaysEffectiveModelAndTargetTooltip`, `tooltipStatesTheEffectPerTarget`) → behalten.
Remove: die zwei Kopien (je echter HTTP-Roundtrip + `sleep` im PDE-Lauf). Doku-Zeile „#173-Regeln" in der
Tests-Abschnotts-Zeile von `model-loading.md` entsprechend kürzen.

### LOW
- **Testlücke BDD 6 (Slot):** `clearingTheModelRemovesTheSlotKey` (Diff-Zeile ~519) asserts
  `containsKey(PO_KEY) == false`, aber nicht — wie die Custom-Variante (Diff-Zeile ~545ff mit
  `of(tester…).inherited()`/`effectiveModel()`) — dass der Selector danach `qwen3` zeigt. 1 Zeile.
- **Kanonisierung bleibt in der Anzeige stecken:** `applyModelList` (`ModelComboWidget.java:132`)
  selectet die Server-ID, der Store behält die getippte Variante; nach erneutem Öffnen zeigt das Feld
  wieder `foo`, dann wieder `FOO`. Kein Doc-Verstoß (R-ML4 regelt nur die Liste), aber Anzeige ≠ Store.
- **Beweislage Widget:** `ChatModelComboWidgetTest` extends `AbstractSwtUiTest` → headless Assume-Skip;
  die Commit-Verträge sind nur mit GUI bewiesen (Doku sagt das offen). Review kann die grünen Zahlen
  nicht reproduzieren.

### Zeilenangaben
Zeilenangaben für unveränderte `AIChatView`-Stellen (`:91`, `:118-120`, `:568`) sind
**main-Zeilen** = PR-Basis; im Head liegen sie wegen der Imports/Methods-Hunks ≈ +3 bis +35 höher.
Die Head-Nummern der neuen/geänderten Stellen (`:442`, `:447-449`, `:481`, `:500`, `:544`,
`ChatModelComboWidget.java:61/72-75`) sind aus den Diff-Hunk-Headern hochgerechnet.

### Pre-existing (nicht blockierend, nur berührt)
- `ModelComboWidget.java:126` — Stale-Guard ruft `snapshotProvider.get()` **im UI-Thread**
  (`buildWithDefaults()` inkl. `buildConfigDirs()` Disk-I/O). Für die Config-Seiten harmlos, durch den
  Chat jetzt bei jedem Agentenwechsel/Config-Apply.
- `ModelListCache` caching-only-on-success (`ModelListCache.java:16,61-63`).
- `applyModelList` kanonisiert nie den gespeicherten Wert (`ModelComboWidget.java:129-133`).

### PR-Body-Fragen — Empfehlung
- **Selector für Scaffold disabled:** annehmen. Doku-Tabelle („Selector disabled, zeigt Base"),
  `Target.NONE` → `selectable() == false` + `save()` liefert `false`, und
  `AgentModelSelectionTest.agentWithoutOwnModelIsNotSelectable` (Diff-Zeile ~586ff) asserts beides inkl.
  „Base unverändert".
- **Case-only-Änderung (`QWEN3` vs `qwen3`) wird nicht gespeichert:** annehmen — dokumentiert
  („echte Änderung … case-insensitive wie R-ML4") und getestet
  (`leavingTheFieldUnchangedCreatesNoOwnModel` prüft beide Richtungen). Folge: Store behält die
  Server-fremde Schreibweise (siehe LOW).

### Bewusst in Ordnung (gegen die Issue-Historie geprüft)
Doppel-Commit gibt es nicht: Selection + FocusOut + `commitPending()` sind über die `shownModel`-Baseline
idempotent. Fetch läuft im `Job`, Anwendung über `EclipseUtil.runInUiThread`; nur das Snapshot wird auf dem
UI-Thread genommen. `EclipseLlmConfigStore.put/remove` flusht pro Aufruf → Save ist persistent und feuert
den Pref-Listener. `toolAllowlistSlot()` → `modelSlot()` ist verlustfrei (nur Dev/Plan/PO/Review
überschreiben; einzige Nutzung `AbstractAgent.java:169`). Leer wird nie als `""` abgelegt
(`LlmConfigSaver.saveOrRemove`, Diff-Zeile ~391ff; BDD 7 mit `containsKey`). Jeder Save-Test asserts
„andere Ziele/Keys unverändert" → R-ML5-Kern „andere Agenten bleiben dran" ist ehrlich gedeckt. Kein
Secret im Fehler-Text (`AIChatView.onModelChange`, Diff-Zeile ~1381ff). #173-Reste: `saveBaseModel`
(aus `5dc254dd`) ist im PR-Head wieder weg, kein Caller übrig — Diff enthält die Datei nicht, also kein
Bruch.

### Skill/Instruction-Gap (nur Empfehlung)
`org.sterl.llmpeon/AGENTS.md` (UI-Patterns) hat keine Regel zu „commit-on-focusout-Widgets müssen vor
jedem UI-Refresh geflusht werden" — der Fehler genau dieser Klasse ist damit zum zweiten Mal im Projekt
(#173 → Revert, jetzt #179). Vorschlag: ein Eintrag „Editor-Widgets mit Deferred-Commit: Flush gehört in
den Refresh-Eintrittspunkt, nicht in einen einzelnen Listener".

**Most likely reason this breaks later:** ein Nutzer verliert ohne jede Meldung sein getipptes Modell
(Reload-Tool-Call, Config-Apply oder Handoff), und weil der spätere FocusOut dank `shownModel`-Baseline
ein No-op ist, gibt es kein Signal — der nächste Turn läuft still mit dem alten Modell.
**Change, der das Risiko am stärksten senkt:** Flush in `refreshModelSelector()` kapseln (ein
Eintrittspunkt statt vier) und die Doc-Regel auf alle Wechsel-Pfade + Send-Pfad ausweiten.
