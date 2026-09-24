# Plan: R-TC-6/7/8/9 — Shell-Approval (Autonom-Definition, Call-Zeit-Evaluation, Clean Break, eigene Klasse)

## ⛔ STOP-AND-ASK (Paul 2026-09-08)
Bei Compile-Fehlern ohne Lösung, nicht-grün-bekommenden Tests, IST-Widersprüchen zu diesem
Plan oder Unklarheiten: **aktiv bei Jon nachfragen (askDev-Kanal)** — nie still workarounden,
nie SOLL ändern. SOLL = `docs/tool-confirmation.md` § SOLL (Fix, 2026-09-24, Paul freigegeben),
Zeilen 56–85. Diese Datei: **nur committen, Inhalt nicht anfassen** — Status-Flips ❌→✅
ausschließlich Jon.

## 0. Branch / Hygiene
- Arbeiten auf `fix/nextids-bug-report`. **Kein** Branch-Wechsel, **kein** Push.
- Commit **ALLE** geänderten Dateien pro grüner Iteration (Code + Docs + Prompts).
- `eclipseBuildProject` auf geänderten Projekten **vor** jedem JUnit-Lauf (stale bin/).
- Surefire (Maven) = ground truth für Core-Zahl; PDE-Suite als **Ganze** (1. Lauf braucht
  Workspace-Trust-Dialog im UI, danach nicht parallel nachstarten, sondern auf PID warten).
- `docs/tool-confirmation.md` wird in genau **einem** Commit mitgegeben (Status-Zeilen ❌→✅
  übernimmt Jon später selbst — nicht vormachen).

## 1. Kontext / Ziel
Shell-Tool-Confirmations-Logik heute: `AIChatView.applyShellCommandConfirmation()`
(Zeilen 438–475) setzt **einmalig pro Config-Laden/Agent-Wechsel** entweder einen Provider
mit **eingefrorenem** autonomous-Flag (Stale-Provider-Bug, BDD#4) oder null. Autonom-Check
prüft nur `AiPlanAgent` — Jon (`AiPoAgent`) fehlt (BDD#1). Die tote `"true"`-Auslesung an
:444 steht im Konflikt mit Clean-Break-Regel (R-TC-8).

Ziel: reine Policy in Core (SWT-frei), Call-Zeit-Evaluation über `Supplier<AiAgent>`,
eigene Klasse `ShellApprovalService` (Name vgl. `docs/user-question-tool-design.md:63`),
Clean Break für `"true"`.

## 2. Design-Entscheidungen (final)

### D1 — Core: `ShellConfirmationMode` (neu)
Paket `org.sterl.llmpeon.tool.tools` (neben `ShellTool`).
```java
public enum ShellConfirmationMode {
    ALWAYS, NOT_AUTONOMOUS, UNSET;
    public static ShellConfirmationMode of(String raw) {
        // trim + case-insensitiv; "always" → ALWAYS, "not-autonomous" → NOT_AUTONOMOUS;
        // ALLES andere (null, "", "false", "true", unbekannt) → UNSET
    }
}
```
„Empty means unset": `"true"` = UNSET (R-TC-8).

### D2 — Core: `ShellConfirmationPolicy` (neu, final)
```java
public final class ShellConfirmationPolicy {
    public enum Decision { PROMPT, AUTO_APPROVE }
    /** R-TC-7: Entscheidung ist reine Funktion der CALL-Zeit-Inputs. */
    public static Decision decide(ShellConfirmationMode mode, boolean autonomous) {
        return switch (mode) {
            case ALWAYS         -> Decision.PROMPT;
            case NOT_AUTONOMOUS -> autonomous ? Decision.AUTO_APPROVE : Decision.PROMPT;
            case UNSET          -> Decision.AUTO_APPROVE;
        };
    }
    /** R-TC-6: Autonom = aktiver Agent ist Jon (AiPoAgent) oder Peon-Plan (AiPlanAgent).
     *  Slaves erben — der Turn-Owner entscheidet, nicht der Slave. null → false (fail-closed). */
    public static boolean isAutonomous(AiAgent agent) {
        return agent instanceof AiPoAgent || agent instanceof AiPlanAgent;
    }
}
```
`AiPoAgent` und `AiPlanAgent` erben unabhängig von `AbstractAgent` — instanceof-Prüfung
gegen beide Typen ist korrekt (Core-interne Abhängigkeit, erlaubt).

### D3 — Plugin: `ShellApprovalService` (neu)
Paket `org.sterl.llmpeon.parts.shell` (neuer Package, vgl. doc `user-question-tool-design.md:63`:
stateless, liest Preferences, konfiguriert ShellTool-Provider, kein SWT).
**IST-verifizierte Typisierung (Jon-Befund 2):** `AskUserTool.QuestionPresenter` existiert
(AskUserTool.java:20–23) mit der Signatur `void show(String question, List<String> answers,
Consumer<String> onAnswer)` — **kein** `.getText()`, keine 4-Arg-`showQuestion`.
`AIChatView.showQuestion(String, List<String>, Consumer<String>)` ist direkt kompatibel,
`this::showQuestion` funktioniert als Method-Reference. Frage-Text exakt wie heute
(AIChatView:452–455), Antworten `List.of("Yes", "No")`, CANCEL-Default: Antwort-Initialwert
`"No"` (heute :451), `AskUserTool.CANCEL = "[canceled]"` (AskUserTool.java:18).

```java
public final class ShellApprovalService {
    public ShellApprovalService(ToolService sharedToolService,
                                AskUserTool.QuestionPresenter questionPresenter,
                                Supplier<AiAgent> activeAgent) { ... }
    /** Von AIChatView.applyConfig() gerufen — VOR dem LlmConfig-Gate, bei JEDER
     *  Preference-Änderung (idempotent, vgl. applyMcpConfig, D4). */
    public void applyConfiguration() {
        String raw = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID)
                     .get(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "");
        ShellConfirmationMode mode = ShellConfirmationMode.of(raw);
        sharedToolService.getTool(ShellTool.class).ifPresent(shellTool -> {
            if (mode == UNSET) { shellTool.setConfirmationProvider(null); return; }
            shellTool.setConfirmationProvider((command, dir) -> {
                if (ShellConfirmationPolicy.decide(mode,
                        ShellConfirmationPolicy.isAutonomous(activeAgent.get())) == AUTO_APPROVE)
                    return "Yes";                 // ShellTool: "Yes" → Original-Befehl läuft
                // UI-Verhalten exakt wie heute (AIChatView:450–466):
                var latch = new CountDownLatch(1);
                var answer = new AtomicReference<>("No");
                questionPresenter.show(
                    "Approve execution of:\n\n`" + command + "`\n in **" + dir + "**? \n\n"
                    + "or enter a new command to execute:",
                    List.of("Yes", "No"),
                    a -> { answer.set(a); latch.countDown(); });
                try { latch.await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (AskUserTool.CANCEL.equals(answer.get()))
                    throw new CancellationException("Canceled tool execution " + dir + " " + command);
                return answer.get();   // "No" → ShellTool antwortet "Shell command execution denied!"
            });
        });
    }
}
```
Provider wird bei jedem `applyConfiguration()` (idempotent, billig, wie MCP) (re-)gesetzt;
autonomous wird **pro Call** aus dem Supplier gelesen (R-TC-7 — kein `setActiveAgent`-Hook
nötig). Latch/Blocking bleibt im Provider-Lambda; `showQuestion` marshallt intern auf
UI-Thread — Verhalten **unverändert**.

### D4 — `AIChatView` (geändert)
- **Reaktivität (Jon-Befund 1, blockierend):** `shellApproval.applyConfiguration()` sitzt
  **VOR** dem `lastAppliedConfig`-Equals-Gate (AIChatView.java:421) — direkt nach
  `applyMcpConfig()` (:419) — und die Zeile `applyShellCommandConfirmation();` (:428)
  wird **gelöscht**, nicht ersetzt. Grund: nach D5 enthält `LlmConfig` die Shell-Pref
  nicht mehr → Shell-only-Änderung würde am Gate early-returnen und die Pref-Änderung
  würde erst nach Restart/anderer Config-Änderung wirken. Vorbild exakt `applyMcpConfig`
  (:417–419). Eintrag in `applyConfig()`:
  ```java
  // Shell-Confirmation is applied on every preference change, INDEPENDENTLY of the LlmConfig
  // gate below — LlmConfig no longer carries the shell pref (clean break), so a shell-only
  // change would early-return otherwise (same pattern as applyMcpConfig, R-MCP1).
  shellApproval.applyConfiguration();
  ```
- Methode `applyShellCommandConfirmation()` **Zeilen 438–475 löschen** (löst TODOs :442/:447
  = R-TC-9; enthält die tote `"true"`-Auslesung :444 = R-TC-8-Live-Fix).
- Neues Feld direkt nach `aiService`-Feld-Initialisierer (Zeile 88–95; Reihenfolge sicher):
  `private final ShellApprovalService shellApproval = new ShellApprovalService(
       aiService.getSharedToolService(), this::showQuestion, aiService::getActiveAgent);`
- Jetzt ungenutzte Imports **einzelner Verifikation** entfernen: `AiPlanAgent`, `ShellTool`,
  `InstanceScope`, `java.util.concurrent.CancellationException`, `java.util.concurrent.CountDownLatch`.
  **`AtomicReference` BEHALTEN** (monitorRef :97). Falls einer doch noch genutzt wird:
  import behalten und hier notieren.
- `onAgentChange()` :481 bleibt, ruft nur noch `aiService.setActiveAgent(mode)` —
  kein Provider-Refresh.

### D5 — R-TC-8 Clean-Break-Inventar (grep-verifiziert)
| Stelle | Aktion |
|---|---|
| `AIChatView.java:444` `"true"`-Check | fällt mit Methode weg (D4) |
| `AiConfigPreferenceView.java:73–79` Combo (`"false"/"always"/"not-autonomous"`) | **ANFASSEN VERBOTEN** — wäre `"false"` zu `""` geändert, matchen Legacy-Werte nicht mehr → Combo-Display-Regression. Policy ignoriert `"false"` als UNSET sowieso. |
| `LlmPreferenceInitializer.java:42` Default `""` | unverändert |
| **Dead-Field-Entfernung** (freigegeben von Paul, 2026-09-24): `LlmConfig.java:94` Feld `shellCommandConfirmationRequired` + `LlmConfigLoader.java:43` Setter-Call + Privat-Methode :74–76 + Assert in `LlmConfigLoaderTest.java:63` (nur die `isShellCommandConfirmationRequired`-Assert-Wegnahme; `store.put(...)` :49 bleibt) | Entfernen: null Consumer (grep-verifiziert), „one behaviour, one implementation". Bricht nichts außer dem eigenen Assert. |

### D6 — Homepage
Grep-verifiziert: keine Shell-Confirmation-Seite in `homepage/src` → **keine Homepage-Änderung**.
`docs/po-agent-jon.md:604–638` dokumentiert bereits die SOLL-Autonom-Regel (Da Mek autonom
als Jon-Slave) — konsistent, keine Änderung.

## 3. Betroffene Dateien
**Neu (Core):**
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/tools/ShellConfirmationMode.java`
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/tools/ShellConfirmationPolicy.java`
- `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/tool/tools/ShellConfirmationPolicyTest.java`

**Neu (Plugin):**
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/shell/ShellApprovalService.java`
- `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/ShellApprovalServiceTest.java`

**Geändert:**
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java` (D4)
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/ai/LlmConfig.java` (D5)
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/ai/LlmConfigLoader.java` (D5)
- `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/ai/LlmConfigLoaderTest.java` (D5)
- `docs/tool-confirmation.md` (Commit nur; Content von Jon)

**Unberührt (explizit):** `ShellTool.java`, `PeonAiService.java`, `AskUserTool.java`,
`UserQuestionResponseWidget.java`, `AiConfigPreferenceView.java`, `LlmPreferenceInitializer.java`,
`PeonConstants.java`, Homepage.

## 4. Rules & Constraints
- Core bleibt SWT-/Eclipse-frei (`ShellConfirmationPolicy` referenziert nur Core-Types).
- Log OR throw, never both; kein Logging in der Policy (reines Objekt).
- Test-Honesty: Tests müssen **ohne** das Feature rot sein (insb. Plugin-Szenario a/b:
  alter Code promptet bei Jon bzw. nutzt stale null-Provider).
- Keine Secrets in Logs/Exceptions (N/A hier, aber Frage-Texte = Kommandos, ok).
- `System.lineSeparator()` nur dort, wo Datei-/Pfadinhalt drin ist; reine Markdown-Frage-
  Texte behalten `"\n"` (bestehendes Verhalten beibehalten, nicht ändern).

## 5. Tests & BDD-Mapping
`docs/tool-confirmation.md` § SOLL hat **keine** UC-IDs, sondern die BDD-Liste unter
R-TC-6…9 — jede Test-Methode trägt `// R-TC-N: <BDD-Zeile>`-Komentar.

### Core `ShellConfirmationPolicyTest` (JUnit5 + AssertJ, GIVEN/WHEN/THEN)
- `mode_of_matrix` — R-TC-8: `of("true")==UNSET` (rot ohne Feature: heute wird „true" als
  bestätigt gewertet), `of("false")==UNSET`, `of("")==UNSET`, `of(null)==UNSET`,
  `of("ALWAYS")==ALWAYS`, `of(" Always ")==ALWAYS`, `of("not-autonomous")==NOT_AUTONOMOUS`.
- `decide_always_prompts_even_when_autonomous` — BDD#5 (R-TC-6).
- `decide_not_autonomous_autonomous_approves` — BDD#1 + BDD#2 (Jon- und Plan-Äquivalenz
  über `isAutonomous`-Ergebnis).
- `decide_not_autonomous_slave_prompts` — BDD#3 (R-TC-6: Slave-Instanz → PROMPT).
- `decide_call_time_flip` — R-TC-7: gleicher Mode, autonomous-Arg flipp true→false zwischen
  zwei `decide`-Calls → AUTO_APPROVE→PROMPT (reine Call-Zeit-Funktion).
- `isAutonomous_matrix` — R-TC-6: `new AiPoAgent(LlmConfig.newOllama("x").build(), new ToolService(false))`
  (Ctor :50) → true; `new AiPlanAgent(...)` (Ctor :21) → true; `AiDevAgent`/`ScaffoldAgent`/
  null → false. (R-TC-9 wird über den Compile-Proof abgedeckt: `AIChatView` referenziert
  `ShellApprovalService`.)

### Plugin `ShellApprovalServiceTest` (JUnit4, **keine** externen Assertion-Libs,
headless, Pattern `AbstractUnitTest`/`EclipseBuildToolTest`)
Setup: `ToolService toolService = new ToolService();` (registriert ShellTool automatisch,
Core ToolService:73); fake `QuestionPresenter` (ruft `onAnswer.accept("…")`, zählt Calls);
`Supplier<AiAgent>` als `AtomicReference<AiAgent>` (flipbar). Fixture: Preference auf
InstanceScope-Node `org.sterl.llmpeon` setzen **BEVOR** SUT-Build, in `@After`
zurücksetzen (Memory #12: kein cross-run-State).
- `notAutonomous_jon_active_noPrompt` — BDD#1 + ehrliches RED: Pref `"not-autonomous"`,
  active = Jon-Instanz → `shellRunCommand("echo ok")` → Presenter **nicht** aufgerufen,
  Befehl läuft. (Alte Logik: Jon zählt nicht autonom → Provider gesetzt → promptet.)
- `agentFlip_toSlave_prompts` — BDD#4 + R-TC-7: Pref `"not-autonomous"`, SUT gebaut mit
  Plan-aktiv, danach Supplier auf `AiDevAgent`-Instanz flippen → Call → Presenter
  **aufgerufen**, Antwort `"No"` → Ergebnis enthält `"Shell command execution denied!"`.
  (Alte Logik: Provider null beim Build → promptet nie.)
- `always_alwaysPrompts` — BDD#5: Pref `"always"`, active = Plan → Presenter aufgerufen.
- `unset_noProvider` — R-TC-8/BDD#7: Pref `""` bzw. `"true"` → `getTool(ShellTool.class)`
  .get().getConfirmationProvider? — falls kein Getter: indirekt über „keinen Prompt-Call
  auch bei `always`-Änderung auf `false`" prüfen; sonst Presenter-Nicht-Call.
  (Falls ShellTool keinen Provider-Getter hat: Test über Verhalten, nicht over Introspektion;
  sonst STOP-AND-ASK — ShellTool **nicht** ändern.)
- Kommandos nur `echo …` (harmlos). UI-Widget + CountDownLatch-Blocking = **manueller
  Smoke** (Paul): R-TC-4 „Ja → ausführbar", R-TC-5 „Nein/CANCEL → wird nicht ausgeführt,
  Agent sieht die Ablehnung".

## 6. Akzeptanz (manuell verifizierbar, Paul)
1. Pref „not-autonomous", als Jon ein Shell-Tool-Call (z. B. Da Mek delegiert) → **kein**
   Prompt.
2. Pref „not-autonomous", als Peon-Dev (oder anderer Slave) → Prompt.
3. Mid-Session Agent-Wechsel ohne Config-Reload → Prompt-Verhalten folgt dem neuen
   aktiven Agenten.
4. Pref „always" → Prompt auch unter Jon/Plan.
5. Pref „Not Required"/`"true"`/`""` → nie Prompt.

## 7. Inkrement & Abnahme
**Ein vertikales Inkrement** (alle Dateien kompilieren gemeinsam grün; jede Datei-Änderung
hält ihr Modul compilable). Reihenfolge:
1. Core: `ShellConfirmationMode`, `ShellConfirmationPolicy` + `ShellConfirmationPolicyTest`
   → `mvn test` (Surefire grün).
2. D5 (LlmConfig/Loader/Test) → Core grün.
3. Plugin: `ShellApprovalService` + `AIChatView`-Änderungen → `eclipseBuildProject` grün.
4. Plugin-Test `ShellApprovalServiceTest` → `eclipseBuildProject` → PDE-Suite ganz.
5. `docs/tool-confirmation.md` + alles commit (1–2 Commits, alle Dateien).

**IST-Evidenz-Anforderung** (Memory #28): Bei DONE-Claim pro Deliverable Datei + Status-Zeile
nennen (z. B. „ShellApprovalService.java erzeugt, AIChatView applyShellCommandConfirmation
weg (grep: 0 Treffer), Surefire X/Y grün").

## 8. Offene Fragen
Keine. D5-Dead-Field-Entfernung und Gate-Bypass (D4, Jon-Befund 1) sind freigegeben.

## 9. PO-Review (Da Dok, 2026-09-24) — R-TC-6/7/8/9, Commit 6a72e92, Branch fix/nextids-bug-report

### Verdict: **CONCERNS** (kein Rework — 2 Hygiene-Lücken + 1 Doku-Befund für Jon)

Plan↔Code, Docs↔Code (SOLL R-TC-6…9 + alle 8 BDDs), Docs↔Plan, Architektur: **alle grün**.
Evidenz (selbst verifiziert, nicht übernommen):

- **Builds:** `eclipseBuildProject` llmpeon-core / org.sterl.llmpeon / org.sterl.llmpeon.test → 0 Errors
  (nur pre-existing Null-Safety-Warnings).
- **Tests:** ShellConfirmationPolicyTest 6/6, LlmConfigLoaderTest 12/12 (Core),
  ShellApprovalServiceTest 5/5 (PDE/OSGi, Trust-Dialog blockierte nicht) — alle grün.
- **Clean-Break-Greps (workspace-wide, .java):** `applyShellCommandConfirmation` → 0 Treffer,
  `shellCommandConfirmationRequired` → 0 Treffer, `QuestionOrchestrator` → 0 Treffer.
- **Lint (lintDocsAndTests, scope docs + beide Test-Module):** 0 Befunde im SOLL-Scope
  (tool-confirmation.md trägt bewusst keine UC-IDs, Plan §5); die 44 gemeldeten
  UNBELEGT*/MANUELL-Findings liegen in docs-linter.md / compact-lock.md / java-debugger-tool.md
  = Bestand aus anderen Zyklen, nicht von diesem Commit verursacht.

### Geprüft & ok (Auszug)

- D1/D2 core verbatim Plan: `of()` trim+case-insensitiv, Alles-andere→UNSET; `decide` reine
  Call-Zeit-Funktion; `isAutonomous` = AiPoAgent|AiPlanAgent, null→false (fail-closed).
- D3 `ShellApprovalService`: Paket, stateless, kein SWT, Supplier pro Call; Frage-Text byte-gleich
  mit D3-Zitat (abgeleitet aus altem AIChatView:452-455), `"No"`-Default, CANCEL→
  CancellationException, `List.of("Yes","No")`, Latch-Semantik unverändert. ShellTool-Vertrag
  verifiziert (ShellTool.java:78-87: "Yes"→Original, "No"→denied, sonst→New-Command).
- D4: `shellApproval.applyConfiguration()` :427 **vor** LlmConfig-Gate :429, nach
  applyMcpConfig :422; Methode weg; Feld :95-98 nach aiService; onAgentChange :449 ohne
  Provider-Refresh; Imports: AiPlanAgent/ShellTool/CancellationException/CountDownLatch weg,
  AtomicReference behalten (monitorRef :100), **InstanceScope behalten — korrekt**, noch genutzt
  in :187/:209 (Plan-Alternativklausel greift).
- D5: LlmConfig ohne "Shell" (grep 0), LlmConfigLoader clean (ganze Datei), LoaderTest nur noch
  `store.put(...)` :49 (wie Plan) + 12/12 grün.
- R-TC-8 Combo **unangetastet** (AiConfigPreferenceView :73-79 wortgleich),
  LlmPreferenceInitializer :42 Default `""` unverändert.
- R-TC-9: AIChatView enthält nur noch import+Field+1 Call (grep "hell": 7 Zeilen, alle Wiring).
- BDD-Mapping: #1 `notAutonomous_jon_active_noPrompt` (RED-ehrlich), #2 core
  `decideNotAutonomousAutonomousApproves`+`isAutonomousMatrix` (Plan-§5-Zuordnung), #3
  `decideNotAutonomousSlavePrompts`+Flip-Test, #4 `agentFlip_toSlave_prompts` (RED-ehrlich),
  #5 `always_alwaysPrompts`, #6 `unset_noProvider("true")` (RED-ehrlich)+`modeOfMatrix`,
  #7 `unset_noProvider("")`, #8 `decideCallTimeFlip`+Flip. `// R-TC-N`-Kommentare in beiden
  Test-Dateien vorhanden.
- Architektur: core SWT-frei, eine Implementierung (keine zweite Policy), Kapselung (Service:
  public = Ctor + applyConfiguration), Richtung plugin→core, UI-Verhalten unverändert.

### Geplant-Zusatz-Test `nullActiveAgent_prompts` — vertretbar?

**Ja.** Pinnt den in D2-Javadoc dokumentierten Fail-closed-Vertrag
("null → false, a missing agent never skips the prompt"). Hinweis: wäre auch mit altem Code grün
(alt: null → kein AiPlanAgent → Provider → Prompt) → ist **Vertrags-Guard, kein
RED-Feature-Test** — als Contract-Pin korrekt gelabelt, kein Verstoss.

### CONCERNS (non-blocking, keine Rework-Pflicht)

1. **Unused Import:** `ShellConfirmationPolicyTest.java:14` —
   `import org.sterl.llmpeon.tool.tools.ShellConfirmationMode` ist redundant (gleiche Package),
   Build-Warning. Extraktion/Entfernung: Import streichen.
2. **Test-Fixture-Duplikation (Rule of Three):** `var model = LlmConfig.newOllama("x").build();`
   + `new ToolService(false)` 4× in `ShellApprovalServiceTest` (plus Core-Test). Einfach/lokal →
   Named Extraction: kleiner `static` Helper im Test (z. B. `model()` bzw. `autonomousAgent()`).

### Doku-Befund (separat, für Jon — nicht Rework gegen die Implementierung)

3. **ADR-0026 ist faktisch falsch** (`docs/adr/0026-extract-question-shell-approval.md`):
   behauptet mit Status "✅ Implemented (2026-08-09)" die Extraktion von
   `ShellApprovalService` **und** `QuestionOrchestrator`
   (`org.sterl.llmpeon.parts.question`). IST: `QuestionOrchestrator` existiert nirgends
   (grep+find 0), und die Shell-Logik saß bis zum 2026-09-24-IST-Befund noch in AIChatView —
   d. h. die ADR beschreibt Phantom-Komponente + Phantom-Zeitpunkt. Sie war im Plan nicht
   genannt und wird vom Increment auch nicht berührt. Empfehlung an Jon: ADR gegen die
   tatsächliche 2026-09-24-Umsetzung umschreiben (QuestionOrchestrator streichen,
   Dateiname/Datum korrigieren) — aktuell aktiv irreführend für künftige Agenten.

### Risikozile (CONCERNS)

- Most likely reason this breaks later: **ADR-0026** — ein künftiger Refaktor liest
  "ShellApprovalService → QuestionOrchestrator (via QuestionPresenter)" und operiert auf einer
  Architektur, die nie existierte (false belief, teuerste Fehlerklasse).
- Change, das das Risiko am meisten senkt: ADR-0026 von Jon gegen IST korrigieren; die zwei
  Hygiene-Punkte (1+2) sind Kosmetik.

### Status-Flip-Erinnerung (Jon/Paul)

`docs/tool-confirmation.md` R-TC-6…9 + BDD-Liste stehen noch auf ❌ — Flip ❌→✅ ist Jons
Aufgabe (Plan §0), wurde im Review bewusst nicht angefasst. Manueller Smoke (Plan §6,
Acceptanz 1-5) bleibt Paults Aufgabe.
