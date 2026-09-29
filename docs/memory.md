# Session-Stand — 2026-09-29 (Widget-Rework R-DEF-9…11 im Bau; Mek nach Context-Overflow compactiert)

> Achtung: docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts
> im Chat, Jon schreibt. (Da Mek hatte 2026-09-27 memory.md überschrieben — nicht wiederholen.)

## AKTUELL 2026-09-29: Widget-Rework R-DEF-9…11 — Plan abgenommen, Build LÄUFT (Inc-Status unklar — MEST ERST STATUS ABFRAGEN)

- **SOLL steht (Paul-GO 2026-09-29):** Basic-Seite = „Default for all agents" (kompletter Slot inkl. Extra body JSON, Bindung 7); Advanced ohne DEV-Sektion + keine Base-Key-Writes (Single Owner, ADR-0063); Extra body/Think folgen Provider **live**, **versteckt ≠ löschen** (preservedBody/hiddenThink); Clean Break dev.url/apiKey bleibt. Supersedes R-DEF-4/5/7 (Advanced-DEV-Sektion 2026-09-28 — doppelt-Editor-Verlustrace: Dialog performt OK auf jede besuchte Seite, Advanced-Statle gewann; Extra-Body build-time-Gate + stille Löschung).
- **Docs umgeschrieben (Jon, 2026-09-29):** default-inheritance.md (R-DEF-9/10/11 ❌, R-DEF-4/5/7 ☠️, Rework-WARUM-Paragraph, BDD-Tabelle) · ADR-0063 + adr/index.md + ADR-0062-Note · index.md · advanced-configuration.md · model-config-widget.md. Uncommitted? → mit nächstem Mek-Commit mitreinichen lassen.
- **Plan:** peon-plan/overview.md (Delta-Plan, 4 Inkremente, von Da Thinka, von Jon ABGENOMMEN): Inc 1 Widget live-Gate + Preserve (UC-DEF-11) · Inc 2 Basic „Default for all agents" + extraBody-Persistenz (UC-DEF-9) · Inc 3 Advanced DEV raus + Base-Key-Writes raus (UC-DEF-10, Single-Owner-Beweis performOkWritesOnlySlotKeys) · Inc 4 Homepage. Test-Löschungen SOLL-bedingt benannt (performOkWritesBaseKeysForDev, devSectionCarriesFullFieldset, devSectionIsFirstAndTitledDefault). Build-Order an Mek erteilt (Surefire Ground Truth, eclipseBuildProject, Mutation-Nachweis applyGate(false), STOP-AND-ASK).
- **⚠️ Mek-Zwischenfall:** erster buildWithDev-Call starb mit „AI call canceled while waiting to retry" (bekannter ApiRetry-Bug, memory 21). Danach war Meks Context aufgebläht (431005 tokens vs. 170240 — verdächtigt: zu großes Tool-Ergebnis, letzter Call grep „MUTATION"); Paul hat Mek **compactiert** (99 msgs ~414k → input ~80k, result 9k). Mek ist laut Paul bereit — **JON MUSS ALS ERSTES: askDev an Mek = Inc-Status erfragen** (was von Inc 1 existiert? Commits? Tests?), dann Build weiterfahren mit derselben Build-Order.
- **Neue Bugs/Todos (open-points.md, ❓):** (1) Compact-Button übergibt keinen Monitor → UI-Feedback-Loch während Compress; (2) Auto-Compact bei `exceed_context_size_error` statt hartem Tod (gehört zum open-to-discuss-Dreiklang).
- **Kein Push/Merge** (Paul: „wir bleiben auf dem Branch").

## Vorherige Zyklen (veraltet-flugfähig)

- **Default-Inheritance 2026-09-28 ✅ (6 Story-Commits `19a67c17`…`a3320aa8` + Abschluss `4d52f45b`/`5beb28d7`/`ab92fa0f`):** R-DEF-1…3/6/8 gebaut (Vererbung alle NULL-Slots, URL-Fallback + ehrlicher Fehler, Temperature auf Basic, „Dev (Default)"-Sektion — die durch R-DEF-9/10 wieder entfernt wird), core 1054/0, OSGi 307/0. Branch-Konsolidierung: alles auf `story/issue-149-think`, story/model-config-widget gelöscht, release-2026-09-06 existiert nicht mehr.
- **Model Config Widget + Think-Default 2026-09-28 ✅** (R-MCW-1…6, R-THINK-10, ADR-0060/0061, Skill eclipse-preferences + setValue-traps-Update `5beb28d7`).
- **Issue #149 Think ✅** (R-THINK-1…9, ADR-0059) — Paul-Smoke für alle drei Zyklen steht gemeinsam aus; danach Push/Merge (Paul).


## Neu 2026-09-29: Widget-Rework R-DEF-9…11 — 🚧 freigegeben (Paul GO 2026-09-29)

- **Paul-Smoke-Befunde:** (1) Extra-Body-Feld provider-gated nur bei Page-Bau (Apply+Tab-Wechsel nötig) + **stille Löschung** gespeicherten extraBody/Think beim Advanced-OK mit geschlossenem Gate; (2) „speichert nicht immer" = **bewiesene Doppelt-Editor-Falle**: Dialog performt OK auf jede besuchte Seite (Basic zuerst, Advanced zuletzt), beide schrieben Base-Keys + Dev-Slot → Basic-Edits verloren; fehlender Provider-Key materialisiert OLLAMA-Default. Mek-IST-Analyse komplett (Schreibpfad-Tabelle, 6 Writer, Copilot-Login-Überschreibung).
- **Paul-Entscheidung (GO):** Ein Owner = Basic-Seite („Default for all agents", kompletter Slot inkl. Extra body JSON via ExtraBodyWidget, Bindung 7); Advanced ohne DEV-Sektion, keine Base-Key-Writes; Extra body/Think folgen Provider **live**, **versteckt ≠ löschen**; Clean Break dev.url/apiKey bleibt. Supersedes R-DEF-4/5/7.
- **Docs umgeschrieben (Jon):** default-inheritance.md R-DEF-9/10/11 ❌ + R-DEF-4/5/7 ☠️ superseded, BDD-Tabelle aktualisiert · [ADR-0063](adr/0063-default-config-single-owner-basic-page.md) Accepted + adr/index.md + ADR-0062-Note · index.md · advanced-configuration.md Update-Abschnitt · model-config-widget.md Scope-Update.
- **Nächste Schritte:** Plan (Da Thinka) → Abnahme → Build → Review → Flips; Homepage nachziehen lassen (Paul ausdrücklich bestellt). Kein Push/Merge (Paul: „wir bleiben auf dem Branch").
- **Paul-Notiz:** TrimService-Story für den nächsten Architecture-&-Bug-Sprint in open-points.md (⏳), inkl. `\n`-Konvention (memory-Regel 36).

## Neu 2026-09-28 (pm): Default-Inheritance — ✅ GEBAUT (wartet auf Paul-Smoke/Merge)

- **Story komplett (6 Commits `19a67c17`→`76df135d`→`39c83789`→`593fe634`→`3f909ace`→`a3320aa8`, Branch story/issue-149-think):** R-DEF-1 Core-Vererbung (resolveModel in po/plan/compact/search/custom, Wire-Test) · R-DEF-2 Custom erbt Base-Model · R-DEF-3 Base-URL→Provider-Default (Ollama :11434, LM Studio :1234/v1, GitHub-Defaults migriert), ehrlicher Fehler „No base URL configured"; Root Cause #125 (JFace entfernt Instance-Keys == Default, Runtime liest rohen InstanceScope) · R-DEF-8 Temperature auf Basic (leer=unset, Bindung 6, Label „Temperature (empty = unset):") · R-DEF-4/5/7 Advanced-„Dev (Default)"-Sektion ganz oben, kompletter Slot (Ping+Temperature+ExtraBody via ExtraBodyWidget-Extract), Save-Routing Base-Keys, `llm.agent.dev.url/apiKey/model` Clean-Break nie-gelesen · R-DEF-6 Guard unverändert (MANUELL-Marker).
- **Surefire:** core **1054/0**, OSGi Tycho **307/0** (+26 headless Skips; Display-Runner 307/307). Lint UC-DEF: 8/8, 0 findings (MANUELL-Info UC-DEF-6).
- **Da Dok CONCERNS → Nacharbeiten `a3320aa8`:** C1 Lügen-Kommentar performOk:108 korrigiert · C2+C3 NPE-Fix leeres API-Key/URL-Feld (Partial Save) → neu `EclipseLlmConfigStore.putOrRemove`, beide Pages schreiben Base-Keys jetzt direkt (JFace-Trap URL/Key auf Schreibpfad geschlossen, lesseitig unverändert), rot-erst-Test + Mutation · D6-Mutation agentRecord DEV-Zweig (rot→revert).
- **Docs:** default-inheritance.md (R-DEF-1…8 ✅), ADR-0062, index.md, advanced-configuration.md Update-Abschnitt (Dev-Override-Keys enden + R-ML2-Note), model-config-widget.md Scope-Update. Status-Flips von Jon.
- **Skill-Evolution (Da-Dok-Empfehlung, bei Mek bestellt):** eclipse-preferences SKILL um „ScopedPreferenceStore setValue traps" erweitern (Default-Equal-Removal + setValue(name,null)-NPE).
- **Paul-Smoke steht aus** (siehe Chat-Zusammenfassung 2026-09-28); danach Merge (Reihenfolge egal — alles auf EINEM Branch) + Push.
- **Achtung Backend:** 2× ConnectException beim Build-Start (LLM-Backend down) — Paul hat behoben; Retry klappte.

## Neu 2026-09-28: Model Config Widget + Think-Default — ✅ GEBAUT (wartet auf Paul-Smoke/Merge)

- **Zyklus komplett durchgebaut** (Jon gab Build-Freiagbe nach SOLL-Klärung; Branch `story/model-config-widget` auf Basis `story/issue-149-think`): Inc-0 Think-Default-Fallback (`c90debfb`, core Surefire **1040/0**) · Inc-1 ModelConfigWidget (`df4293fe`) · Inc-2 Basic-Page-Umbau (`a9691449`, OSGi Surefire **301/0** + 20 Headless-Skips = bestehendes „Workbench not created"-Muster; SWT-Tests laufen im PDE-Runner) · Inc-3 Homepage (`99c5aecb`) · Inc-4 Skill `eclipse-preferences` + dpe-Section verlagert (`5fd04c42`).
- **Da-Dok-Review:** REJECTED → 2 Blocker von Mek behoben: (1) Surefire-Ground-Truth nachgereicht (die 298/301 waren Eclipse-Runner-Zahlen — Memory-Regel 28 zutiefst bestätigt), (2) Docs-Flips PO-Aufgabe. Mutations-Nachweis applyThinkValue („nie stiller Ersatzwert"): red→green. Non-Blocking: waitUntil 3× dupliziert → Extract-Kandidat; index-basierte Child-Lookups spröde.
- **Status-Flips gemacht (Jon):** R-MCW-1…6 ✅, R-THINK-10 ✅, index.md, ADR-0060/0061 Accepted. Lint (Scope MCW|THINK): 17/17 UCs, 63 Test-IDs, 0 findings.
- **Think-Default (ADR-0061):** leerer Agent-Think erbt Base-Think (Dev-Slot); explizites off gewinnt; Custom erbt gleich. Basic-Think-Feld = Default-Editor (Label „Think (Default)").
- **Docs+Plan committed ✅** (Mek `9ac3d587` docs/** + Plan, `e4e705a7` Archiv, `planImplemented` ausgeführt → `peon-plan/overview-done-2026-09-28-14-09.md`). Merge-Reihenfolge: erst #149, dann Widget-Branch (Basis ist #149). User-Smoke: beide Branches gemeinsam (Think-Dropdown, think:false im Log, Widget-Felder 1–5 untereinander, Provider-Wechsel → Think-Form folgt, Reload ohne Apply, Cancel verwirft).
- Auffällig: altes Archiv-Artefakt `org.sterl.llmpeon/peon-plan/overview-done-2026-09-27-17-02.md` (#149-Zyklus, untracked, am falschen Ort) — Mek hat es bewusst außerhalb des Order-Scope gelassen; Aufräumen mit dem #149-Merge-Commit.
- **Zwei ⏳ aus dem Widget-Bau (open-points):** waitUntil-Extract in AbstractSwtUiTest; per-Agent-Provider-Override-Story (❓, bereits eingetragen).

## Branch — konsolidiert (Paul-Order 2026-09-28)
- **EINE Branch: `story/issue-149-think` @ `0bf93cb0`** — Widget-Branch wurde fast-forward gemerged (`f2059191..e4e705a7`, 24 Dateien +1698/−106, 0 Konflikte) und `story/model-config-widget` gelöscht (`git branch -d`). Archiv-Strunk aufgeräumt (`0bf93cb0`: `org.sterl.llmpeon/peon-plan/overview-done-2026-09-27-17-02.md` → `peon-plan/`).
- `release-2026-09-06` existiert weder lokal noch auf origin — nichts zu löschen. Remote-Reste (nicht Pauls Ziel?): `fix/nextids-bug-report`, `git-support`, `story/compact-input-budget`.
- **9 Commits ahead of `origin/story/issue-149-think`** — Push noch nicht gemacht (Jon-Entscheidung, default: nach Paul-Smoke).
- Merge → main + User-Smoke = Paul.

## Issue #149 — ✅ FERTIG (wartet auf Paul-Review)
- **Fix:** Boolean-off kollabierte in Persistenz zu unset (`ThinkValueSupport.booleanValue(false)`→`""`
  → Saver entfernt Key → Loader null → Ollama-Field weg). Neu: EIN Think-Level-String — leer=unset=
  nichts senden, Off-Tokens case-insensitive → Ollama `think:false`, editierbares Dropdown
  (`ThinkSupport.Toggle`), `think_supported` komplett raus (auch Custom-Frontmatter, legacy
  leskompatibel on>off>support, migrate-on-write), Basis-Checkbox raus. ADR-0059, R-THINK-1…9 ✅.
- **Provider-Think-Audit (Da Dok, Paul-Order):** 9 Provider geprüft — Interpretation überall
  korrekt; **B1 Copilot `returnThinking` gefixt** (echter Bug, Parität OpenAI); B2/B3 toter
  Alt-Semantik-Code gelöscht; B4/B5 Javadocs; B6 E2E-Legacy-`false`. Mutations-Nachweis
  toOllamaThink-Blank (3 Tests rot, incl. UC-THINK-1×2 + UC-THINK-7).
- **Paul-Smoke steht aus:** Ollama-Dropdown, Basis-Checkbox weg, `think:false` im Debug-Log,
  Legacy-Frontmatter-Write.

## Entscheidungen für Pauls Review (alle in resolved-points.md)
1. Leeres Think = unset (Option A) — Paul bestätigt.
2. `think_supported` komplett raus (auch Custom-Frontmatter, nur noch `think`) — Paul bestätigt.
3. Legacy on-string > think_supported:false (Jon autonom, Thinka-Empfehlung).
4. LM Studio: ALLE Off-Tokens → reasoning=off, generic-on → on (Jon autonom; **Verhaltensänderung**).
5. Rot-erst-Strategie: Inc-1 grüner Schutz + gepinnter IST-Test, Inc-2 echter Umschalt-Regressor.
6. B1–B6 Audit-Fixes im Inc-4 (Jon autonom).
7. Da-Dok-Review-Non-Blocking: Docs-Scope-Drift-Commits (index/memory/advanced-config in Inc-1) —
   künftig Doc-Dateien im Plan benennen; ROT-Evidenz künftig WIP-Commit (ab Inc-4 umgesetzt).

## Offen (open-points.md — neu)
- ⏳ Think-BDD-Lücken (a)–(g) + `Entry.off` ungenutzt + `AiAgent.isThinkEnabled` deprecated.
- ⏳ User-Smoke Issue #149. Vorherige offene Punkte unverändert (R-CC-16 ❌, Bug B, compactSession-
  Exposition, Header 0k, Fixture-Drift, Option A, Docs-Sweep, R-CC-7, Issue #142, Merge = Paul).

## Nächste Schritte
1. ~~Da Mek: committen + planImplemented~~ ✅ erledigt (`9ac3d587`/`e4e705a7`, Plan archiviert) — nur noch der untracked alte Archiv-Strunk im Plugin-Ordner offen.
2. Paul: gemeinsamer Smoke **beider Branches** — Merge-Reihenfolge: erst `story/issue-149-think`, dann `story/model-config-widget` (Basis ist #149). Smoke-Punkte oben („Neu 2026-09-28") + bei #149.
3. Autonom danach: R-CC-7 (next), Think-BDD-Lücken (a)–(g), per-Agent-Provider-Override-Story (❓ in open-points.md).

## Paul-Feedback 2026-09-27 (Config-UI)
- Widget-vs-Store-Stale-Reads in Preference-Pages: eclipse-dpe-Skill erweitert (Abschnitt
  „Preference pages — read widget state, not the store"). Asymmetrie Check-Host (liest Widget,
  AiConfigPreferenceView:121) vs. Reload-Models (liest Store, R-ML2-SOLL: erst Apply) —
  Mek-Analyse bestätigt R-ML2 als dokumentierte Entscheidung; SOLL-Änderung nur auf Paul-Wunsch.