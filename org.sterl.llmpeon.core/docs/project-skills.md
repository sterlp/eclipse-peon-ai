# Project Skills

**Ziel:** Skills projekt-lokal in `<Projekt>/.agents/skills` (Disk-Pfad) ablegen und gemeinsam mit
den geteilten User-Skills nutzen — Namenskollision gewinnt die engste Variante (R17). User-Roots
seit 2026-10-23: `~/.agents/skills` (neuer Default, APM-Kompatibilität, empfohlen) und
`~/.peon/skills` (deprecated, gelesen und schreibbar — Support, nicht empfohlen).
Technische Basis: [ADR-0042](../../docs/adr/0042-project-skill-slot.md) (ein Service, Slots),
[ADR-0043](../../docs/adr/0043-scaffold-write-validator.md) (Scaffold-Write-Scoping).

**Begriffe:** Config-Skills · Projekt-Skills · Skill-Slot — siehe [Glossar](../../docs/glossary.md).

## Business Rules

### Slot-Modell (Components mit disunkten Maps)

- **R2 ✅** Skill-Slot-Component (deep module): definiert sich über den **verantwortlichen Pfad**
  und kapselt das heutige Laden (Discover + Parse + atomarer Swap) + **eine eigene Map**. Der
  `SkillService` hält eine **Liste** dieser Components — mit R17 (2026-10-23) drei: User-Root
  `~/.agents/skills` (neuer Default) + `<configDir>/skills` (deprecated, Kompatibilität) auf der
  Config-Seite, Projekt-Slot (`<Projekt-Disk-Pfad>/.agents/skills`, dynamisch). Die effektive
  Skill-View ist die Komposition (siehe R4/R17); beliebig viele Slots möglich, ADR-0042.
  - GIVEN alle Components (Config-Roots + Projekt) WHEN der Service die effektive View berechnet
    THEN alle Maps sind vertreten, kein Name doppelt
- **R2a ✅** Projektwechsel ersetzt **ausschließlich** die Projekt-Component (neue Skills des
  ausgewählten Projekts ersetzen die des vorherigen); die Config-Component bleibt unberührt.
  - GIVEN Projekt A mit Skill `alpha` und Projekt B mit Skill `beta` WHEN Projekt B gewählt wird
    THEN die View zeigt `beta [project]`, `alpha` ist weg
  - GIVEN Projekt A hat `.agents/skills` nicht WHEN Projekt A gewählt THEN Projekt-Skills sind
    leer, Config-Skills unverändert
- **R2b ✅** Die Config-Component wird **nie** durch Projektwechsel oder Projekt-Skills verändert —
  maskierte Config-Skills kehren automatisch zurück, sobald kein Projekt-Skill sie mehr überdeckt.
  - GIVEN Config-Skill `deploy` und Projekt-Skill `deploy` (Override aktiv) WHEN das Projekt ohne
    Skills oder ein Projekt ohne `deploy`-Skill gewählt wird THEN `deploy` ist wieder sichtbar —
    als Config-Skill, unverändert
  - GIVEN kein Projekt gewählt WHEN View berechnet THEN genau die Config-Skills
- **R2c ✅** Eine Quelle für „aktuelles Projekt": der Projekt-Slot folgt `UserContext.currentProject`
  (Choke-Point `setProject`). **Pinned schlägt Selektion** — der Pin-Akt braucht keinen Slot-Wechsel
  (currentProject ist schon das gepinnte Projekt), Unpin mit Selektion triggert den Wechsel nach.
  - GIVEN Projekt A gepinnt WHEN User selektiert eine Ressource in Projekt B THEN der Projekt-Slot
    bleibt auf A, Turn-Context und Validator-Root zeigen ebenfalls A
  - GIVEN Projekt A gepinnt WHEN User entpinnt bei Selektion in B THEN Slot, Turn-Context und
    Validator-Root wechseln auf B
- **R2d ✅** Scaffold-Context: aktuelle Projekt-Info (bestehend, R2c-Quelle) **+ Liste der offenen
  Projekte mit Disk-Pfad** (neu) — sonst kann er Skills nicht für andere Projekte adressieren
  (Disk-Pfade nötig, weil seine Tools relativ nur gegen configDir auflösen). Format offen, Details
  im Plan (z.B. „Open projects: /path/to/x — x …").
- **R3 ✅** Ein Config-Reload (Preferences, ReloadConfigTool) refreshed **jede** Component;
  ein Projektwechsel refreshed **nur** die Projekt-Component.
  - GIVEN Projekt-Slot auf Projekt X gesetzt WHEN ReloadConfigTool läuft THEN beide Components
    werden neu geladen (Projekt-Slot bleibt auf X)

### Override & State

- **R4 ✅** Override **nur beim Lesen**: bei Namenskollision gewinnt die Projekt-Component in der
  effektiven View (Key = lowercase Name); die Config-Map behält ihren Eintrag (R2b).
  - GIVEN Config-Skill `code-review` und Projekt-Skill `code-review` WHEN View berechnet THEN
    geliefert wird die Projekt-Variante (Quelle `PROJECT`)
  - GIVEN nur Config-Skill `deploy` WHEN View berechnet THEN sichtbar mit Quelle `CONFIG`

### User-Roots (2026-10-23, Paul)

- **R17 ✅ done (2026-10-23, Nachtzyklus)** Der `SkillService` liest **drei** Roots mit fester Präzedenz — bei
  Namenskollision gewinnt die engste Variante: **Projekt > `~/.agents/skills` > `~/.peon/skills`**.
  `~/.agents/skills` ist der neue Default-User-Root — Kompatibilität mit dem Microsoft Agent
  Package Manager (APM), der Skills nach `.agents/skills` deployt (auch auf der Homepage zu
  dokumentieren). `~/.peon/skills` bleibt **deprecated gelesen** (Bestand bleibt sichtbar) und
  **schreibbar** (Support fällt nicht weg) — Empfehlung (Homepage-Warnung + Scaffold) ist aber
  `.agents`: User-Root oder Projekt. Beide User-Roots tragen die Quelle `CONFIG` — UI (R9/R13)
  bleibt unverändert; ein `.agents`-Skill, der einen `.peon`-Skill überdeckt, ist ein Config-internes
  Override. **Keine automatische Migration** (Clean Break) — aber auf User-Wunsch kann der
  **Scaffold die Migration ausführen**: er hat beide User-Roots als Validator-Roots (R10) und
  zieht Skills auf Bitte von `~/.peon/skills` nach `~/.agents/skills` um (User fragt im Chat).
  - GIVEN Skill `deploy` in allen drei Roots WHEN die View berechnet wird THEN gewinnt die
    Projekt-Variante
  - GIVEN Skill `deploy` in `~/.agents/skills` und `~/.peon/skills` WHEN die View berechnet wird
    THEN gewinnt die `.agents`-Variante
  - GIVEN Skill `deploy` nur in `~/.peon/skills` WHEN die View berechnet wird THEN sichtbar als
    `CONFIG`
  - GIVEN der User bittet den Scaffold, seine Skills umzuziehen WHEN der Scaffold die Dateien nach
    `~/.agents/skills` überführt THEN werden sie dort sichtbar und gewinnen künftig per Präzedenz
    über die `.peon`-Originale
  - GIVEN `~/.agents/skills` existiert nicht WHEN geladen wird THEN leer, kein Fehler (wie heute
    bei fehlendem Dir)
  - GIVEN `configDir`-Preference auf eigenes Verzeichnis gesetzt WHEN geladen wird THEN alle drei
    Roots gelten weiterhin (User-Root `~/.agents/skills` fix, `<configDir>/skills` folgt der
    Preference als Kompatibilitätspfad)

### State & Konsistenz

- **R1 ✅** Enabled-Toggles sind **name-keyed im SkillService** gespeichert und überleben jeden
  Refresh (User 2026-09-08); neue Namen starten aktiv.
  - GIVEN Skill `foo` ist deaktiviert WHEN ein Refresh (auch Projektwechsel) lädt THEN `foo` bleibt
    deaktiviert — auch wenn es ein Projekt-Skill-Override ist
  - GIVEN Skill `bar` wird erstmals gesehen THEN enabled = true
- **R5 ✅** Atomarer Swap: Refresh baut die Skill-Map neu und tauscht sie in einem Schritt —
  LLM-Calls sehen nie eine halbvolle/leere Map (kein `clear()` + befüllen).
  - GIVEN ein Refresh läuft während ein Agent skillList() ruft THEN die Antwort ist die alte oder
    die neue vollständige Map, nie eine Teilmenge
- **R5a ✅** Fehlgeschlagener Refresh lässt den bisherigen Stand unverändert (kein `clear()` vor
  dem Laden — heutiger Bug: IO-Fehler putzt alle Skills weg); der Fehler wird gemeldet.
  - GIVEN geladene Skills WHEN ein Refresh mit IO-Fehler scheitert THEN die bisherigen Skills
    bleiben sichtbar, der Fehler geht als Meldung raus (nicht als RuntimeException-Smash)
- **R6 ✅** Jeder Skill trägt seine Quelle (`CONFIG`/`PROJECT`) — Basis für UI-Differenzierung
  (R9) und LLM-Disclosure (R7).
- **R7 ✅** Skill-Listings **und** `skillRead`-Header geben die Quelle mit an (z.B.
  `code-review [project]` / `[config]`) — kein stiller Mix; Override-Situation ist erkennbar.
  - GIVEN ein Projekt-Skill überschreibt einen Config-Skill WHEN skillList THEN die Ausgabe zeigt
    Quelle und ggf. den Override
  - GIVEN ein Projekt-Skill WHEN skillRead THEN der `=== SKILL: name ===`-Header trägt die Quelle

### Scaffold (Placement)

- **R10 ✅** Scaffold darf in `.agents/skills` **aller offenen Projekte**, in den User-Root
  `~/.agents/skills` und weiterhin in `<configDir>/skills` schreiben (Validator-Roots dynamisch zur
  Call-Zeit — siehe ADR-0043). Kein allgemeines Projekt-Schreibrecht — nur die Skills-Dirs.
  **Empfehlung des Scaffolds ist immer `.agents`** (User-Root oder Projekt, je nach Skill-Inhalt) —
  `<configDir>/skills` (`~/.peon/skills`) bleibt schreibbarer Support-Bestand (deprecated, R17 —
  amendiert 2026-10-23).
  - GIVEN Projekte X (aktuell) und Y offen WHEN Scaffold schreibt `Y/.agents/skills/skill.md`
    THEN erlaubt; `Y/src/...` THEN rejected (Tool-Error + onProblem)
  - GIVEN Skill für Projekt Y angelegt während X aktuell THEN sichtbar erst, wenn Y currentProject
    ist (folgt aus R2a/R2c)
- **R11 ✅** Bei Anlage eines neuen Skills fragt Scaffold die Platzierung **im Chat** (Antworttext,
  kein askUser-Tool — er hat es nicht und braucht es nicht; User 2026-09-08) und wartet auf die
  Antwort, bevor er schreibt. Empfehlung hängt vom Skill-Inhalt ab (projekt-spezifisch → Projekt).
  - GIVEN Scaffold legt einen neuen Skill an WHEN Platzierung unklar THEN er stellt die Frage im
    Chat (mit Empfehlung) und schreibt erst nach der Antwort
  - GIVEN Antwort „Projekt" WHEN mehrere Projekte offen THEN Scaffold wählt das gemeinte Projekt
    aus der Liste (R2d) und schreibt in dessen `.agents/skills`
  - GIVEN Antwort „Projekt" WHEN Scaffold schreibt THEN Ziel ist `<Projekt>/.agents/skills`
  - GIVEN Antwort „Config" WHEN Scaffold schreibt THEN Ziel ist `~/.agents/skills` (Empfehlung;
    2026-10-23). `~/.peon/skills` bleibt schreibbar (Support-Bestand, R17), wird aber vom Scaffold
    nicht mehr empfohlen
- **R12 ✅** Scaffold-Write-Scoping hart über `WriteValidator` (dynamische Roots), nicht via
  Instanz-Merge mit den Shared-Tools (ADR-0043).

### UI

- **R9 ✅** Projekt-Skills sind im UI als solche erkennbar: Statuszeilen-Menü mit zwei Sektionen
  („Skills" = Config, „Project skills" mit Projekt-Namen als Header) + Zähler „N skills (M project)"
  in der Statuszeile; Slash-Autocomplete zeigt bei Projekt-Skills das Suffix ` [project]`.
  - GIVEN 3 Config- und 2 Projekt-Skills WHEN Statuszeile gerendert THEN Zähler „5 skills (2 project)"
  - GIVEN Projekt-Skill `review` WHEN Slash-Autocomplete THEN Eintrag `review [project]`
- **R13 ✅** Skill-Namen im Dialog/Output bleiben **ohne** Suffix — Quellen-Marker nur in Zähler,
  Menü-Header und Autocomplete (User 2026-09-08: „Menü-Sektionen + Zähler + Suffix reicht").

### Component-Refactor (Follow-up, 2026-09-09)

- **R14 ✅** Der Skill-Slot heißt `SkillComponent` (Komponenten-Architektur); die
  Component hat **immer einen Pfad** — kein `@Nullable` dir. „Kein Projekt" wird durch den
  **Instanz-Tausch im Service** ausgedrückt: der Service setzt eine leere Component ein. Die
  Config- und Projekt-Positionen bleiben als zwei Service-Felder erhalten; getauscht wird nur die
  Projekt-Component (kein Component-Cache pro Pfad — Reload ist billig, Staleness-Risiko unbezahlbar).
  *(2026-10-23, R17: die Config-Seite wird zur Liste — zwei User-Roots; die Felder-Struktur ist
  Plan-Detail des Umsetzungs-Inkrements.)*
  - GIVEN kein Projekt gewählt WHEN View berechnet THEN die eingesetzte Projekt-Component liefert
    leer, `path()` gibt nie null
  - GIVEN Projekt A THEN B THEN A WHEN gewechselt THEN je Wechsel eine frisch geladene
    Projekt-Component (kein wiederverwendeter Cache)
- **R15 ✅** Scaffold-Write → deterministischer Refresh: nach erfolgreichem
  Disk-Write des Scaffolds ruft der Code `skillService.refreshAll()` (nicht LLM-getrieben —
  kein Path-Sniffing, Scaffold schreibt ohnehin nur config-/skills-scoped). `ReloadConfigTool`
  bleibt als manueller Reload.
  - GIVEN Scaffold schreibt einen Skill WHEN der Write erfolgreich ist THEN die effektive View
    enthält ihn ohne LLM-Zutun
- **R16 ✅** Jon (AiPoAgent) bekommt den geteilten `SkillTool` in seine curated
  po-Tool-Liste (gleiche Instanz wie alle Sklaven — reine Wiring-Lücke, kein Filter).
  - GIVEN Jon WHEN seine Tools aufgebaut sind THEN `skillRead`/`skillList` sind verfügbar

## Out of Scope

- **Scaffold als Jon-Sub-Agent** (Jon delegiert Skill-Erstellung/-Edit an Scaffold) — User:
  „später", eigener Zyklus.
- **Skill-Learning-Loop** — eigene Story, siehe [skill-evolution-loop.md](skill-evolution-loop.md).

## BDD-Test-Mapping (Plan-Nachweis je Regel)

| Regel | Test (vorgeschlagen) |
|---|---|
| R2/R2a | `SkillServiceTest.twoComponents_projectSwitchReplacesProjectComponent` |
| R2b | `SkillServiceTest.configComponentNeverMutated_maskedSkillReturns` |
| R4 | `SkillServiceTest.projectSkillOverridesConfigSkillByLowercaseName` |
| R3 | `SkillServiceTest.reloadRefreshesEveryComponent` |
| R5 | `SkillServiceTest.swapIsAtomic_noPartialMapVisible` |
| R6/R7 | `SkillServiceTest.sourceMarkerAndListDisclosure` |
| R1 | `SkillServiceTest.enabledStateSurvivesRefreshAndFollowsName` |
| R5a | `SkillServiceTest.failedRefreshKeepsPreviousState` |
| R12 | `AiScaffoldAgentTest.writeValidatorAllowsConfigAndProjectSkillsOnly` |
| R14 | `SkillServiceTest.noProjectUsesEmptyComponent_pathNeverNull` · `SkillServiceTest.projectSwitchAlwaysFreshLoad_noCache` |
| R15 | `AiScaffoldAgentTest.successfulWriteTriggersSkillRefreshAll` |
| R16 | `PeonAiServiceTest.test_jon_gets_shared_skill_tool` (assertSame: geteilte Instanz) |
| R2a (Plugin) | `setProject_replacesProjectSlotOnly` (Name historisch, „Slot" = alte Begriffswelt) |
| R2c (Plugin) | `pinnedProject_keepsSkillComponent_untilSetProject` |
| R17 | `SkillServiceTest.threeRoots_precedenceProjectOverAgentsOverPeon` · `agentsRootWinsOverPeonRoot` · `peonOnlySkillVisibleAsConfig` · `agentsRootMissingLoadsEmpty` · `customConfigDirKeepsAllThreeRoots` · `AiScaffoldAgentTest.scaffoldWriteIntoUserAgentsRootWinsOverPeonOriginal` (Migration) · `AiScaffoldAgentTest.writeValidatorAllowsUserAgentsRoot` · `PeonAiServiceTest.scaffoldWriteValidator_acceptsHomeUserAgentsRoot` (Composition-Root-Wiring, Mutation `roots.add(agentsSkillsDir)`) · Prompt-Guard `AiScaffoldAgentTest.systemPromptRecommendsAgentsUserRoot` |

## Skill-relativer Lesepfad (SkillPromptFile) — ✅ done (2026-09-11, core-cleanup)

- GIVEN ein Pfad mit `../`-Segments (`../escape.txt`, `./sub/../../out.txt`), WHEN ein Skill seine
  Datei liest, THEN IAE „Path traversal not allowed" — der **Roh-Pfad** wird normalisiert
  (`\`→`/`, `.`/`..`-Auflösung, alle Plattformen) und gegen das Skill-Dir geprüft, BEVOR
  `../`-Segmente gestrippt werden (vormals Dead Guard). Ein führendes `/` zählt als skill-relativ
  (makeReltive-Kontrakt). → `SkillPromptFileTest.readRelativeFileRejectsPathTraversal`
- GIVEN skill-qualifizierter relativer Pfad, WHEN gelesen, THEN Datei wird gefunden (kein false
  „Path traversal") → `SkillPromptFileTest.bug_readRelativeFileWithSkillQualifiedPathAlwaysThrows`
- GIVEN parentloses Skill-Dir, WHEN gelesen, THEN IAE „File not found" statt NPE
  → `SkillPromptFileTest.bug_readRelativeFileWithParentlessSkillDirThrowsNpe`
