
# Resolved Points

Geklärte Punkte, die in kein Feature-Doc passen. Offene Punkte: [open-points.md](open-points.md).

## SLF4J Multiple Providers — gelöst (Paul gemeldet 2026-10-17)

**Punkt:** `SLF4J(W): Class path contains multiple SLF4J providers` — `EclipseSlf4jProvider`
gewinnt, aber slf4j-simple (2.0.19) ist als zweiter Provider resolved (direkte Plugin-pom-
Dependency → `copy-dependencies` → `lib/` → Bundle-ClassPath → ServiceLoader sieht beide).
**Entscheidung:** slf4j-simple komplett raus (pom + MANIFEST + build.properties + Disk —
Nachzug 2026-10-23: auch die `.classpath`-Zeile, die beim ersten Sweep entfiel und den
PDE-Bau mit „missing required library `lib/slf4j-simple.jar`" blockierte; Paul: im core
soll er sein, das Plugin hat die eigene `EclipseSlf4jProvider`-Bridge) —
nur noch unser Provider; Fix-Nachweis strukturell (exakt eine
Provider-Registrierung im gebauten Bundle). **Pauls Annahme korrigiert:** es gibt kein
`Require-Bundle org.slf4j.api`-Range `[2.0.0,3.0.0[` (VOLL-Grep: 0 Treffer) — slf4j-api kommt
aus dem gebündelten `lib/`-Jar, Range-Schärfung entfällt. **Endstand 2026-10-23
([ADR-0073](adr/0073-slf4j-api-stays-bundled.md)):** OSGi-Resolve von slf4j-api negativ
bewiesen (Require-Bundle + ServiceLoader-Mediator → NOP-Logger für ALLES slf4j-Logging) —
slf4j-api bleibt gebündelt, eigene Story entfällt.

## Alias-Path — verworfen (Paul 2026-10-17)

**Punkt:** LLM setzt immer wieder den Eclipse-Project-Path als Disk-Path — Bedarf für ein
„alias path"-Feature (Tools akzeptieren beide Pfade)?
**Entscheidung:** Verworfen. Pfad-Dialekte bleiben familien-spezifisch (`disk*` ↔ `eclipse*`,
bewusste Trennung — Kollisionsargument `docs`/`tmp`-Projekte); nur ein Satz als Note in
[shell-tool.md](../org.sterl.llmpeon.core/docs/shell-tool.md) Notes. Der Befund soll durch klare Pfad-Angaben in der
Projekt-Info (und künftig R-CG-7) abgestellt werden.

| Punkt | Entscheidung | Begründung | Datum |
|---|---|---|---|
| `eclipseReadFile` kürzt lange Ausgaben still (Dev-Befund) | **Widerlegt** — kein Zeilen-Cap, R1d in [eclipse-read-tools.md](../org.sterl.llmpeon/docs/eclipse-read-tools.md) zurückgezogen | Pfadanalyse `FileLines` → `DefaultToolExecutor` → `SmartToolExecutor` → `ToolService.execute` → `ThreadSafeMemory.addResult` → `ToolLoopRequest.call` → `StreamingBridge`: nirgends wird gekappt. `ChatMessageUtil`-Limits (6000/90000/900000) dienen Logging/Dedup/Token-Schätzung; `SmartToolExecutor:60-62` kürzt nur Anzeigetext. Beobachtet wurde R1a/R1b. | 2026-09-03 |
| `SwtUtil.setExcluded-Extract` (Da-Dok G2) | 🔒 Gelöst: `SwtUtil.setVisible(Control, boolean)` (GridData-Vertrag im Javadoc), 3 Call-Sites migriert (`ModelConfigWidget.setThinkVisible`, `ExtraBodyWidget.setGateVisible`, `McpPreferenceView.applyTypeVisibility`); toter `labelStyle`-Zweig in `ModelConfigWidget` mit entfernt (ExtraBodyWidget bleibt — dort live) | Das 2-Zeilen-SWT-Idiom existierte 3× unter 3 Namen — „one behaviour, one implementation"; einziger Ort `SwtUtil` (Widget-Helfer). AIChatView (:709-729) bewusst Follow-up nach dem Async-Merge. | 2026-10-15 |
| Toter Code `eclipseFindImplementations` | 🔒 Gelöst: Banner + kompletter `/* */`-Block (`EclipseCodeNavigationTool.java:283-331`) gelöscht — `MAX_IMPL_RESULTS` existierte nur im Kommentar, Reaktivierung = Compile-Fehler | Irreführender Dead Code; `eclipseFindJavaType` deckt den Use Case; `JdtUtil`-Helper bleiben (aktiv genutzt) | 2026-10-15 |
| `McpConnectionService` apply-Duplikat | 🔒 Gelöst: gemeinsamer `private applyState(McpApplied)`-Block; `applyConfig` behält R-MCP1b-Short-Circuit, `toggle` behält Persist | Dritter Zustandsübergang hätte den Block drittens kopiert; Verhalten identisch, bestehende R-MCP1-Tests decken | 2026-10-15 |
| „Loading 📋 Static env info" erscheint häufig nach Tool-Calls — System-Prompt-Bug? | **Kein Bug.** Kein Rebuild pro Tool-Iteration, Prompt-Cache bricht nicht | `ToolService.executeLoop` ruft `buildSystemPrompt()` nie; Static-Context enthält nur `LocalDate.now()`, keinen Timestamp/Projekt/Zähler. Die Meldungen stammen von **verschiedenen Agenten** (je eigener Cache) und echten Invalidierungen (`clear`/`compact`/`setStaticContext`/`updateConfig`). Sichtbar erst seit inc-27 (Agentenname in der Meldung). Rest-Befunde (doppelter Aufruf `AbstractAgent:179`, fehlende `count == 1`-Assertion) → Zyklus 2a. | 2026-09-03 |
| Gewähltes Projekt im Static Context? Bekommen Da Mek/Da Thinka es? | **Wie gewollt** — Projekt ist Turn-Context, nicht System-Prompt; Sklaven bekommen es bewusst nicht | `AgentContextComponent:136-140` → `UserContext:20-44` (aktiver Agent). `initStaticContext` fügt nur `StaticContextItem` hinzu; `BuildPoAgentComponent:99-112` gibt den Sklaven Plan + agentenspezifische AGENTS.md + Workspace-Memory, kein Selected-Project-Block. Entspricht `issues/fact-issues.md` Punkt 0b. | 2026-09-03 |
| Custom-Dropdown-Umbau (Chat-Agent-Picker + Model-Dropdown) | **Descoped** — Klassen 2026-09-06 **ersatzlos gelöscht** (Dead Code, 0 Referenzen, −646 Zeilen; `DropdownItem`/`DropdownTheme`/`DropdownButton`/`DropdownPopup`) | Der halbfertige Umbau brach den Build (`DropdownButton:83`, nicht existentes `Control.setTabList(boolean)`) unmittelbar vor dem Release. Kosmetik vs. Release-Stabilität — User-Priorität: Release. Nach dem Descoping lagen die Klassen unbenutzt kompilierbar herum; User-Entscheidung 2026-09-06: löschen statt liegen lassen. Wiederaufnahme = eigene Story (Code aus Git-Historie). | 2026-09-03 (descoped), 2026-09-06 (gelöscht) |
| Backslash-Pfade in den File-Tools (`a\b.txt` vs `a/b.txt`) | **Keine Normalisierung in den Tools** — Windows versteht beide Separatoren nativ (Path + Eclipse `IPath`); POSIX: LLM-Vertrag ist `/`, Worst Case = ehrliches „file not found" (self-healing). Nur der Validator normalisiert unbedingt ([write-path-validator.md](../org.sterl.llmpeon.core/docs/write-path-validator.md)) | Normalisierung in den Tools würde wörtlich `a\b.txt` genannte Files auf POSIX unadressierbar machen — ein realer Verlust für eine hypothetische LLM-Verfehlung. Tool-Mismatch = lauter, ehrlicher Fehler (LLM retryt); Validator-Mismatch = stiller Bypass (Security) — verschiedene Failure-Modi rechtfertigen verschiedene Behandlung. | 2026-09-04 |
| `eclipseWriteFile` schreibt immer UTF-8 (Charset-Asymmetrie, Triage 2026-09-03) | **Widerlegt** — kein Bug. `IoUtils` schreibt seit v0.1.0 (#3) mit `IFile#getCharset()` (UTF-8-Fallback für neue Dateien); Regression-Test mit ISO-8859-1-Fixture läuft grün auf unmodifiziertem Code) | Triage-Befund war ein Fixture-Artefakt: Charset wurde per prefs-Datei auf der Disk gesetzt, **ohne Workspace-Refresh** — der Workspace sah weiterhin das UTF-8-Project-Default. Der Test bleibt als Regression-Guard. | 2026-09-06 |
| `diskReadFile` scheitert an workspace-qualifizierten Pfaden, wenn Projektname ≠ Disk-Ordnername | **Kein Code-Fix** — Empfehlung „Eclipse-Projektname = Disk-Ordnername", dokumentiert auf der Homepage. Keine Auto-Übersetzung `/project/path` → Disk in den `disk*`-Tools | Realfall 2026-09-16 (Kimi): `diskReadFile("/llmpeon-parent/docs/docs-linter.md")` bei Disk-Pfad `…/eclipse-peon-ai`. `FileUtils.resolve:78-88` nimmt einen absoluten Pfad nur, wenn er **existiert**, sonst `workingDir.resolve(...)` → hängt ihn an den Disk-Root (`…/eclipse-peon-ai/llmpeon-parent/docs/…`). Beides weg → `File not found: <x> also not in <workingDir>` (`DiskFileReadTool.java:54`). **Warum keine Übersetzung:** (1) echte Mehrdeutigkeit — heißt ein Projekt `docs` oder `tmp`, ist `/docs/x.md` gleichzeitig gültiger Disk- und Eclipse-Pfad, die Übersetzung müsste raten; (2) der `WriteValidator` arbeitet auf dem normalisierten Pfad, eine Schicht davor verschiebt die Sicherheitsgrenze; (3) es verwischt die Familien-Trennung, die `QualifiedPathValidator` bei Copy/Rename gerade hergestellt hat. Das LLM hat die Information ohnehin: `projectInfo` nennt in **jedem** Turn Projektname, Eclipse-Pfad und Disk-Pfad (`EclipseUtil.java:346-358`) — hier hat das Modell sie ignoriert, kein Werkzeugfehler. | 2026-09-16 |
| `PoDelegateTool.compact()` meldet trotz R16-Skip „compacted." (Da-Dok-Fund) | **Gefixt** — R16-Schärfung: Boolean-Return wird gelesen, Skip → `"Nothing to compact (N messages)"`; gemeinsam mit Guard < 2 → < 3 (Re-Compact = Noop), [po-agent-jon.md](../org.sterl.llmpeon.core/docs/po-agent-jon.md) R16 | Beides dieselbe Ehrlichkeits-Lücke am selben Choke-Point; User 2026-09-15: „einverstanden". Da-Dok-Review ACCEPTED. | 2026-09-15 |
| Schreibziel muss Regular-File sein | **Umgesetzt** — `requireRegularFile`-Guard im `DiskFileWriteTool` | Klare Fehlermeldung statt kryptischer Filesystem-Exception beim Schreiben auf ein Verzeichnis/Sonderfile; im IST verifiziert | 2026-10-23 |

| Glossar-Fragen | Entscheidung | Begründung | Datum |
|---|---|---|---|
| Glossar eager laden? (❓ 2026-09-03) | **(a) Nur Jon** — Glossar bleibt mein Kontext; ich trage das Wording in Orders/Pläne an Thinka/Mek/Dok weiter | Sklaven-Kontext ist Turn-scoped; ein Glossar-ContextItem für alle kostet Token pro Turn und driftet vom SOLL. Jon ist der Sprach-Hüter — Qualität entsteht in der Order, nicht im Masse-Load. | 2026-09-11 |
| Glossar „Slot" doppelt belegt (❓ 2026-09-10) | **„Slot" gestorben** — Model-Seite = **Model-Selection** (`AgentModelConfig`-Auswahl), Skill-Seite = **Skill-Component** (seit Story A im Code so). ADR-Dateinamen (0036/0042) bleiben historisch; Altbestand wird bei Berührung angeglichen (Glossar-Regel 4) | User 2026-09-11: „Slot macht für mich gar keinen Sinn — es ist eine model selection oder eine SkillComponent." Ein Begriff, zwei Bedeutungen verletzt das Glossar-Prinzip. | 2026-09-11 |

## Tool-Evolution-Run — CR-Verdicts (2026-09-19, Paul) · Zusammfassung nach Auflösung

Ausgangspunkt: Vergleich unseres Plugins gegen das externe Copilot-Eclipse-Plugin (Nacht-Zyklus 2026-09-19,
Plan `/github-copilot-for-eclipse/peon-plan/overview.md` — auf unseren Projekt-Peon-Plan verschoben).
Alle CR-Items im PO-Run entschieden; die ursprüngliche Sammelstelle (tool-evolution.md) und die externe
Mapping-Datei (feature-change-request-copilot.md) wurden aufgelöst. Verbleibende SOLL-Docs: ❌
[project-problems-tool.md](../org.sterl.llmpeon/docs/project-problems-tool.md), [java-debugger-tool.md](../org.sterl.llmpeon/docs/java-debugger-tool.md),
[tool-output-disclosure.md](../org.sterl.llmpeon.core/docs/tool-output-disclosure.md), [web-tools.md](../org.sterl.llmpeon.core/docs/web-tools.md) · ⏳ geparkt
[terminal-session-tool.md](../org.sterl.llmpeon.core/docs/terminal-session-tool.md) (Revisit mit async-agent-tools-proposal, Option C),
[tool-confirmation.md](../org.sterl.llmpeon.core/docs/tool-confirmation.md).

- **CR-1 (create_file), CR-2 (Whole-File-Regen), CR-7 (Change-Review-UI/Undo/WorkingSetBar) — abgelehnt.**
  Begründung (Paul): Agent-basiertes System + Git/PR-Workflow (V2 als eigener Service mit PRs) deckt
  Review/Undo; Whole-File deckt `diskWriteFile`/`eclipseWriteFile` ohnehin ab; Delta-Edit (Edit-Guard) ist
  besser; Extra-UI = Ballast (L). Revisit-Trigger: Nicht-Git-Workspaces werden realer Use-Case.
- **CR-3 — angenommen:** Datei- + Severity-Filter auf `eclipseReadProjectProblems`, projektweite Lesung bleibt unverändert.
- **CR-4 — angenommen (angepasst):** Debugger-Tool für Da Mek, lesend + ändernd, keine Confirmations, weil
  die Debug-Session User-Property ist (User startet/managt sie, Agent unterstützt).
- **CR-5 (persistente/Background-Terminal-Session) — geparkt, nicht verworfen:** Tools sind synchron;
  Background braucht Async-Tool-Infrastruktur (async-agent-tools-proposal Option C); Shell bleibt One-shot.
- **CR-6 (Tool-Confirmation) — geparkt:** Paul nutzt Bestätigungen nie (immer aus); Bedarf erst mit CR-4/CR-5.
- **CR-17/18/19 — angenommen:** Caps ehrlich (Search-Cap 1000→**500**); webFetch statt byte-Cap paginiert.
- **CR-20 — angenommen:** neues `webGet(url, path)` (eigenes Tool, isEditTool, kein Size-Limit).
- **CR-8–16 — Nein:** unsere Read/Search/Nav/Docs/Orchestration/Memory/Skills/Build/MCP-Familien = Vorsprung.
- **Decompiler Language-Server — kein Nutzen** (nativ gebündelt, nicht decompilierbar; Logik bewusst anders;
  IP-Gründe).

## Weitere geklärte Punkte (2026-09-24 aus open-points.md konsolidiert)

| Punkt | Entscheidung | Begründung | Datum |
|---|---|---|---|
| Debugger-Backlog F2: statische Felder + evaluate-Objektwerte | **Specified** — SOLL jetzt R-JD-9 (UC-JD-10/11) in [java-debugger-tool.md](../org.sterl.llmpeon/docs/java-debugger-tool.md), Bau im Debugger+Linter-Mini-Zyklus | Paul „Go" 2026-09-21 | 2026-09-21 |
| Debugger Exception-Suspend | **Specified** — R-JD-10 (UC-JD-12), `get_exception`-Action; Event-Abfrage verworfen (R-JD-3) | Paul „Go" 2026-09-21 | 2026-09-21 |
| Docs-Linter „manuell verifiziert"-Marker | **Specified** — R-DL-22 (UC-DL-65/66) in [docs-linter.md](../org.sterl.llmpeon.core/docs/docs-linter.md) | Paul „Go" 2026-09-21 | 2026-09-21 |
| Self-Reference-Guard aus `bugfix/edit-tool-insert` | **Bewusst NICHT übernommen** — Selbstwachstum ist Agenten-Absicht; Edit-Guard deckt die teure Klasse | Paul bestätigt 2026-09-19; Guard liegt fertig auf `bugfix/edit-tool-insert` falls wieder gebraucht | 2026-09-19 |
| SimpleDiff LCS-OOM bei großen Dateien | **Gelöst** — Guard `MAX_LCS_CELLS` + Common-Trim + `MAX_DIFF_LINES` (Nachfassung auf `fix/simple-diff`, in main) | Crash-Shape-Abdeckung | 2026-09-16/23 |
| User-Context-Selection-Regression | **Gelöst** — R-SEL-1…3; Setter-Reihenfolge nur per User-Smoke prüfbar | SOLL in [user-context.md](../org.sterl.llmpeon/docs/user-context.md) | 2026-09-16 |
| Docs-Linter liest Code-Block-Überschriften als Definitionen | **Gelöst** — R-DL-13 (Fenced Code Blocks übersprungen) | Dogfooding-Fund, Linter reportete False Positives | 2026-09-15 |
| Code-Block-Regel für Testquellen (VERWAIST-Fehlalarm) | **Entschieden** — R-DL-21 Option (b) in [docs-linter.md](../org.sterl.llmpeon.core/docs/docs-linter.md) | Realfall `DocsLinterToolTest:78` | 2026-09-21 |
| DL-Tests ohne ID-Kommentare (37 Altbestand) | **Bewusst offen als eigener Sweep** — ✅s sind legitim (Implementierung nachweislich getestet, nur Annotation fehlt); Empfehlung: eigener Mini-Zyklus, nicht im Feature-Zyklus | Linter meldet korrekt; zu groß für einen Feature-Abschluss | 2026-09-15 |
| `eclipseReplaceLines`/`diskReplaceLines` Insert-Korruption | **Gelöst** — Root-Cause = leerer/blanker oldString (`String.replace("", x)`); Edit-Guard min. 3 Non-WS, alle 3 Oberflächen, Mutation-Nachweise rot | Stress-Jagden fanden kein sporadisches Reprokt im Replace selbst | 2026-09-19 |
| Docs-Linter `idPattern` 0/0 bei workspace-Pfaden | **Entschieden** — Wurzel = Root-Handling, → R-DL-18 (Root-Fallback), gebaut im Mini-Zyklus `bugfix/linter-root-fallback` | Paul 2026-09-16 | 2026-09-16 |
| `nextIds` reserviert nicht / Neustart | **Gebaut** — Zustandslosigkeit bleibt (R-DL-16 + UC-DL-56/57), Ablauf in po.md; Mehrwert erst durch R-DL-17 | Paul bestätigt | 2026-09-15/16 |
| `applyEdit` not-found dumpet das gesamte File | **Bewusst so** — kein Bau; Dump spart den Read-Roundtrip im Fehlerfall | Paul 2026-09-19: gute Lösung (Kontext-Fenster um die ähnlichste Fundstelle) existiert nicht; Roundtrip-vs-Context-Bombe bleibt offen, bleibt stehen bis ein Realfall kommt | 2026-09-19 (aus open-points konsolidiert 2026-09-26) |
| Fallback-Hint („cannot be compacted") ohne Once-only-Guard — Spam je Tool-Iteration (Bug A) | **Gelöst** — Paul baute den Guard (`agent == null || !hasCompactTool` → Fallback), Ersteinbau war invertiert (`containsMessage(msg)` ohne `!` — Message kam nie an); Da Mek fixte die Negation + Test `fallbackHintIsAddedOnce` | `ToolService.java:220`, R-CC-15-Kontext (compact.md) | 2026-09-27 |
| Think: leeres Dropdown = unset, nichts senden (alle Provider) statt think:false | **Entschieden (Paul, Option A)** — Off-Tokens (`false`/`FALSE`/`none`/`no`/`off`, case-insensitive) = explizites off; Ollama-Items `""`/`true`/`false` | „Empty means unset"-Regel; Abschalten nur explizit — Issue #149 | 2026-09-27 |
| Think: `think_supported` fliegt komplett raus (auch Custom-Agent-Frontmatter) | **Entschieden (Paul)** — abgeleitet wird nur aus dem Think-Level; Legacy leskompatibel (on > off > support), migrate-on-write zu `think`; Basis-Checkbox „supports thinking" ebenfalls raus | Reporter-Verwirrung „supports vs. wants"; ein String, eine Kontrolle | 2026-09-27 |
| Legacy `think_supported: false` + `think_on_string: high` | **Jon (autonom)** — on-string gewinnt → `"high"` (konkreter Level = spezifischere User-Intent) | Thinka-Empfehlung akzeptiert | 2026-09-27 |
| LM Studio Off-Tokens | **Jon (autonom)** — alle Off-Tokens → `reasoning=off`, generic-on → `reasoning=on` (vorher: nur `"false"`, Rest verbatim) | Konsistenz mit Ollama-Regel; Verhaltensänderung dokumentiert | 2026-09-27 |
| Provider-Think-Audit-Befunde B1–B5 | **Jon (autonom)** — B1 Copilot `returnThinking(true)` gefixt (echter Bug, Parität zu OpenAI); B2/B3 toter Alt-Semantik-Code (`toOnOff`/`toBoolean`/`resolveOff`) gelöscht; B4/B5 Javadocs auf ADR-0059; B6 E2E-Legacy-`false` ergänzt | Da-Dok-Audit, CONCERNS; alles in Inc-4 | 2026-09-27 |
| API-Fehler ins Agent-Memory (Paul-Wunsch) | **Gebaut** (✅) — eigenes Feature-Doc [agent-error-memory.md](../org.sterl.llmpeon.core/docs/agent-error-memory.md); Rest: Paul-Smoke AiChatView-Wiring | Terminale Fehler nach erschöpftem ApiRetry (nie Cancel) landen als kompakte `e.getMessage()` (Dedup 1×) im Agent-Memory; Einfügepunkt AiChatView-catch — der Agent sieht den Abbruch und kann reagieren | 2026-09-30 |

## Aus open-points.md aufgeräumt (2026-10-14, Paul-Order)

| Punkt | Entscheidung | Begründung | Datum |
|---|---|---|---|
| R-ATC-4 Compact-Slot ohne Tools-Feld | **🔒 Paul:** Compact bekommt KEIN Tools-Feld — ein inertes Feld wäre Lüge | `CompactService` baut den Request ohne Tools, Da Scribe ruft nie Tools auf (`compactSession` ist Jons Delegate-Tool); Umsetzung inc4 `AgentModelConfigSection(..., !COMPACT)` + Test | 2026-10-14 |
| R-ATC-8 Preview = getippter Wert + Search-Preview-Pfad | **🔒 Paul:** Preview liest den Feldinhalt statt der gespeicherten Config; Search-Preview über `SearchAgentTool.resolveActiveTools` (Button enabled sobald `PeonAiService.current()` existiert) | Gemeinsamer Kompositions-Helper mit Runtime (`filterFor`), kein zweiter Matcher; Äquivalenz Preview↔Runtime bewiesen (`resolveActiveToolsMatchesRuntimeFilter`), inc5 | 2026-10-14 |
| Standalone-Peon-Review behält memory*/askUser | **🔒 Rückversichert:** wie alle Standalone-Agenten (Peon-Plan/-Dev, Custom), kein neuer Mechanismus | UC-TF-2 ([agent-tool-filter.md](../org.sterl.llmpeon.core/docs/agent-tool-filter.md)) auf RAM-Sklaven verengt; falls Paul Standalone auch gestrippt will: eigene Story | 2026-10-14 |
| R-SEL-4 Umsetzungsdetails | **🔒 alle drei bestätigt:** jede `setTextSelection` (auch leer) räumt den Typ; Rename `clazz`/`setClassFile` → `javaType`/`setJavaType`; Typ-Event berührt `currentProject` nicht | Kein Verhaltens-Widerspruch; UC-SEL-4-Tests bleiben | 2026-10-14 |
| CIB-Doc-Split (`compact-input-budget.md`) | **🔒 Paul:** Split okay statt Umnummerierung | Löst die 6× `PRAEFIX_FREMD` im Compact-Doc; IDs + ~35 Code-Kommentar-Referenzen unangetastet | 2026-10-14 |
| Unbegrenzte Query-Caches | **Überholt 2026-10-17 (Paul specified):** [bounded-caches.md](../org.sterl.llmpeon.core/docs/bounded-caches.md) — `SearchQuery.CACHE` + `RegexUtils.GLOB_CACHE` auf geteilten `BoundedLruCache` (Cap 128), `WebFetchTool` echtes LRU | 2026-10-14 Rückversicherung „Einträge winzig" galt, bis der Cache-Audit (Nachtzyklus 5) LLM-kontrollierte Keys als echtes Leak einstufte | 2026-10-14 → 2026-10-17 |
| Compressor-Dedup-Subtext-Edge / ThreadSafeMemory RAM-only | **Keine Aktion** — Dedup ist lossy (Compact akzeptiert), RAM-only nach Persist-IOException präexistierend | Keine Blocker; Revisit nur bei Beschwerden/Datenverlust-Meldungen | 2026-10-14 |

## handovers-and-plans-BETA-Block — verworfen (Jon 2026-10-22, Docs-Restrukturierung)

**Punkt:** `context-architecture.md` enthielt einen BETA-Block „`docs/handovers-and-plans.md` — sobald
stabil, als `EclipseFileContextItem`/`persistentContext`-Item integrieren". Das Doc existierte nie;
der Block wurde bei der Referenz-Hygiene (INC-3/4) entfernt.
**Entscheidung:** Konzept verworfen — Handover-/Plan-Doku lebt in `peon-plan/overview.md`
(archiviert pro Zyklus) + `docs/memory.md` (Session-Zwischenstand). Kein Context-Item nötig.
**Begründung:** Der Mechanismus, den der BETA-Block vorantreiben wollte, ist durch den
Plan-Archiv-Fluss (planImplemented) und memory.md seit 2026-10 realisiert.
