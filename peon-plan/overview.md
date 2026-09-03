# Plan — Custom-Dropdown für Chat-Agent-Picker + Model-Dropdown (Option C, scoped)

## 1. Context

- **User-Befund:** „Die Config-Dropdowns passen optisch nicht, Look & Feel stimmt nicht."
- **Recherche** (`docs/dropdown-ui-comparison.md`): Preference-Pages sind bei uns und der
  Referenz (copilot-for-eclipse) identisch (stock Combos, null Styling). Der sichtbare
  Unterschied liegt in der **Chat-ActionBar**: die Referenz nutzt dort ein eigenes
  Dropdown-Widget (`DropdownButton`/`DropdownPopup`/`ItemController`), wir einen stock
  `Combo` (`ActionsBarWidget.agentCombo`, READ_ONLY, 120 px).
- **Ziel:** Agent-Picker im Chat-ActionBar **UND** Model-Dropdown in den Config-Pages
  (Basic + per-agent Sections) als thembares Custom-Dropdown auf Referenz-Niveau —
  die beiden Spots, die optisch nachweisen (User-Entscheidung: BOTH).
- **Branch:** `new-config` @ 726f816. Docs (`docs/`) bleiben PO-gepflegt — kein Dev-Write.

## 2. Option-Bewertung (Aufwand/Nutzen)

| Option | Aufwand | Nutzen | Urteil |
|---|---|---|---|
| A nichts tun + dokumentieren | minimal | 0 — Befund bleibt bestehen | ✗ |
| B E4-CSS auf stock Combos | mittel | unsicher: globaler Look-Change auf **alle** Combos; `CCombo`-Popups sind eigene Shell-Children → auf Mac meist nicht mitstylbar; Referenz-Look nicht erreichbar | ✗ |
| **C Custom-Dropdown-Widget: Agent-Picker + Model-Dropdown** | hoch (~1,9k Zeilen Referenz, aber Peon hat schon: E4-CSS-Registrierung, `EclipseUiUtil.resolveTheme()` + Theme-Listener, SWT-Test-Infra) | **hoch + bestimmt** — einzige Option, die den Referenz-L&F liefert; Grundlage für spätere reichere Picker (Icons/Gruppen/Action-Items) | ✓ **empfohlen** |
| D Theme-aware SWT-Farben ohne CSS | mittel | niedrig: native Combos ignorieren auf Mac größtenteils `setBackground`; Referenz-Look nicht erreichbar | ✗ |

**Empfehlung: C**, adaptiv neu gebaut statt 1:1-Port, auf **Agent-Picker +
Model-Dropdown** gesliced (Details §3). User-Entscheidung: der `CCombo`-Freitext-Vorteil
fällt bewusst weg (Auswahl genügt). Übrige Config-Dropdowns (Think, Provider, …) bleiben
stock — sie sind bereits auf Referenz-Niveau.

### Bewusst descoped (nicht Teil dieses Plans)
- Preference-Page-Dropdowns außer Model: Think/Provider/Shell-Confirmation/Voice/MCP — kein Touch.
- Hover-Shell-Mechanik der Referenz (`IDropdownItemHoverProvider`, 100 ms Polling).
- Gruppen-Header, Suffixe, Action-Items („Manage…"), E4-CSS-Engine-Integration (`IStylingEngine`).
- Linux-Overlay-Scrollbar-Sonderpfad der Referenz (User auf Mac; einfacher Pfad: Shell um
  vertikale Scrollbar-Breite verbreitern).
- Ungenutztes `EditableComboFieldEditor` — bleibt (Trennung: eigener Aufräumarbeit).

## 3. Design-Entscheidungen

1. **Neubau in Peon-Stil statt blindem 1:1-Port** (~700 Zeilen statt ~1,9k). Tricky-Methoden
   der Referenz (Monitor-Flip, Keyboard-Nav, Scrollbar-Kompensation, Rahmen-Paint) werden als
   **Logik** übernommen, nicht als ganzer Datei-Code. Referenz ist MIT (Microsoft) →
   **Copyright-Header („Copyright (c) Microsoft Corporation. Licensed under the MIT license.")
   in jedem adaptierten File behalten** (rechtliche Anforderung).
2. **Paket:** `org.sterl.llmpeon.parts.widget.dropdown` (Plugin-Modul, SWT — kein Core-Code,
   kein Core-Change; `AiAgent` bleibt in Core).
3. **Klassen (v1, minimal aber erweiterbar):**
   - `DropdownItem` — `record (String id, String label, Image icon)` (icon nullable, SWT-Image
     → gehört ins Plugin). Kein `enabled`/`suffix`/`onAction` in v1.
   - `DropdownTheme` — Theme-aware Farben: reine RGB-Konstanten (dark/light) + lazies
     SWT-`Color`-Cache pro Display, dispose-sicher. Theme-Quell: `EclipseUiUtil.resolveTheme()`.
     **Default-Palette = Referenz** (offene SOLL-Frage F2):
     dark: popupBg `#1E1F22`, focusBg `#184785` (24,71,133) · light: popupBg `#FFFFFF`, focusBg `#D4E2FF` (212,226,255).
   - `DropdownButton extends Composite` — painted (Hintergrund, Hover→focusBg, Label,
     optional Icon, Pfeil), `setItems(List<DropdownItem>)`, `setSelectedItemId(String)`,
     `getSelectedItemId()`, `setSelectionListener(Consumer<String>)`; öffnet per Klick/
     Space/Enter/↓; `setTabList(true)` + Focus-Rahmen-Paint; `computeSize` (natürliche Breite,
     ersetzt fixe 120 px). Pfeil: **gezeichneter GC-Triangel** (kein Asset, immer
     theme-korrekt) — SOLL-Frage F3.
   - `DropdownPopup` — borderless `Shell(SWT.NO_TRIM | SWT.ON_TOP)` + `ScrolledComposite`,
     eine Zeile pro Item (Icon+Label, fixe Zeilenhöhe), Hover/Focus-Highlight,
     Klick→auswählen, ↑/↓/Enter/Escape (Nav als **öffentliche Methoden** `focusNext()`/
     `focusPrev()`/`confirm()` → ohne KeyEvent-Simulation testbar), max. 15 sichtbare Zeilen,
     Positionierung unter Anchor, **Flip over bei unterem Bildschirmrand**, Clamp an Monitor,
     Schließen per `Shell.Deactivate` + Escape, Rahmen-Paint (drawRoundRectangle, arc 8,
     farbe aus `DropdownTheme`; **Fallback: 1 px border + eckig**, falls Mac-Corner falsch
     ausfallen — Inline-Entscheidung in inc-4).
4. **Wiring in `ActionsBarWidget`:** nur `agentCombo` wird intern zu `DropdownButton`.
   **Public API bleibt 1:1** (`setAgents`, `lockWhileWorking`, `updateModeUI`,
   `updateCompact`, Constructor) — `AIChatView` braucht keine Änderung.
   - Item-id = `AiAgent.getName()` (entspricht der bestehenden Selection-Preservation).
   - `lockWhileWorking(true)` → Button disable **+ geöffnetes Popup schließen**.
   - Theme-Wechsel: `EclipseUiUtil.addThemeChangeListener` → `DropdownTheme` neu auflösen +
     `redraw()` (Listener im Dispose aufräumen).
5. **Wiring in `ModelComboWidget`:** `CCombo` → `DropdownButton`, **Public API bleibt 1:1**
   (`setModel`/`getModel`/`fetchModels` + Constructor + `FetchSnapshot`-Static-Methoden) —
   Config-Pages (`AiConfigPreferenceView`, `AiAdvancedPreferenceView`) und
   `AgentModelConfigSection` brauchen keine Änderung.
   - Layout: `Model:`-Label + Button (`FILL`-gestreckt, Pfeil rechtsbündig) + Refresh-Button (bleibt).
   - Item-id = `AiModel.getId()`; selektiert = configured model.
   - **Empty-List:** Button zeigt leer, Klick öffnet kein Popup (0 Items → early return, wie Referenz) — kein Freitext-Fallback mehr.
   - **`getModel()`** liefert das `selectedItemId` (ohne Freitext ist Selection ≡ früherem Text).
   - Fetch/Cache/Stale-Guard (`ModelListCache`, `FetchSnapshot`, Job, configured-model-append in
     `applyModelList`) bleiben **logisch unverändert** — nur `applyModelList` schreibt statt
     `setItems`/`select` in den `DropdownButton` (`setItems` + `setSelectedItemId`).
   - Popup-Breite: naturbreit nach breitestem Modell-Id; **Inline-Entscheidung in inc-6**
     falls Ids zu breit wirken (dann Cap + Ellipsis).
6. **Kein CSS in v1** (nur SWT-Farben). CSS-Regeln in `css/dark-styles.css`/
   `default-styles.css` wären Follow-up (Extension-Point ist schon registriert —
   `plugin.xml` `org.eclipse.e4.ui.css.swt.theme`).
7. **Threading:** UI-Access nur auf UI-Thread (Agentenliste + Model-Liste kommen bereits via
   `runInUiThread`); der bestehende Model-Fetch-`Job` (Snapshot-capture vor dem Job) bleibt
   unverändert — keinen neuen Background-Pfad einführen.

## 4. Affected files

| Pfad | Change |
|---|---|
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/widget/dropdown/DropdownItem.java` | **neu** |
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/widget/dropdown/DropdownTheme.java` | **neu** |
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/widget/dropdown/DropdownButton.java` | **neu** |
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/widget/dropdown/DropdownPopup.java` | **neu** |
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/widget/ActionsBarWidget.java` | **geändert** (nur `agentCombo`-Teil: `buildAgentCombo`, `rebuildAgentItems`, `setAgents`, `lockWhileWorking`, `updateModeUI`) |
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java` | **unverändert** (API bleibt) |
| `/org.sterl.llmpeon.test/src/org/sterl/llmpeon/parts/widget/dropdown/*Test.java` | **neu** (5 Tests, §7) |
| `/org.sterl.llmpeon.test/src/org/sterl/llmpeon/parts/widget/ActionsBarWidgetTest.java` | **neu** |
| `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/config/widgets/ModelComboWidget.java` | **geändert** (CCombo → `DropdownButton`, Public API 1:1, §3.5) |
| `/org.sterl.llmpeon.test/src/org/sterl/llmpeon/parts/config/widgets/ModelComboWidgetTest.java` | **geändert** (CCombo-Assertions → DropdownButton-Assertions, S8–S10) |
| Config-Pages, `EditableComboFieldEditor`, Core-Modul | **unverändert** |

Referenz (read-only, `/Users/sterlp/dev/workset/copilot-for-eclipse/com.microsoft.copilot.eclipse.ui/src/com/microsoft/copilot/eclipse/ui/`): `swt/DropdownButton.java` (~288 Z.), `swt/DropdownPopup.java` (~764 Z.), `swt/ItemController.java` (~229 Z.), `swt/DropdownItem.java`, `swt/CssConstants.java`; CSS `css/dark.css`/`css/light.css` (nur als Farb-Referenz).

## 5. Regeln & Constraints

- SWT nur auf UI-Thread; Dispose-Listener für `Color`/`Shell`/`Image` (Peon-Pattern, vgl. `ActionsBarWidget`-Color-Dispose, `HeaderBarWidget.toolsMenu`).
- **Log OR throw, never both.** Keine geschluckten Exceptions.
- Plugin-Tests: JUnit 4, **keine** external-Assertion-Libs; SWT-Tests nach `ModelComboWidgetTest`-Pattern (`AbstractUnitTest`, `ui(() -> …)`, `Assume`-Skip ohne Workbench-Display, `WAIT_TIMEOUT_MS`/`SETTLE_MS` explizit).
- MIT-Header in adaptierten Files behalten (§3.1).
- `System.lineSeparator()` in Tool-Output-Strings — hier nicht relevant (kein Tool-Output).
- Kein Auto-Commit außer auf Branch `new-config`; Commit `inc-N: <summary>` + `Assisted-by: Peon AI (<Model>)`-Trailer.
- **Homepage-Check (inc-6):** Regel „visible changes → homepage im selben Increment". L&F-only, Verhalten identisch → voraussichtlich kein Update; inc-6 prüft, ob eine Homepage-Seite den Picker/Screenshot zeigt (`homepage/src/setup/agents.md` etc. — aktuell keine Bilder) und aktualisiert bei Bedarf.
- **Skill-Update (AGENTS.md):** falls während der Umsetzung neues SWT/Eclipse-Know-how fällt (z. B. borderless-Shell-Transparenz auf Mac, Theme-Listener-Leaks), als Hinweis in `/llmpeon-parent/skills/eclipse-dpe` ergänzen.

## 6. BDD-Akzeptanz

- **S1 Popup öffnen/auswählen:** GIVEN Chat-View geöffnet, WHEN Klick auf Agent-Picker, THEN Popup unter Anchor mit allen Agenten; WHEN Hover über Item, THEN focusBg-Highlight; WHEN Klick auf Item, THEN `onAgentChange` feuert, Popup schließt, Button zeigt neuen Label.
  → `DropdownPopupTest.selectsAndCloses`, `DropdownButtonTest.selectionListenerFires`
- **S2 Keyboard:** GIVEN Popup offen, WHEN ↑/↓/Enter/Escape, THEN Focus wandert / Auswahl erfolgt / Popup schließt.
  → `DropdownPopupTest.keyboardNavigation` (über `focusNext()/focusPrev()/confirm()`)
- **S3 Theming:** GIVEN dark Theme, THEN `DropdownTheme` liefert dark-Palette (RGB exakt); GIVEN light, THEN light; GIVEN Theme-Wechsel zur Laufzeit, THEN Button/Popup zeichnen neu.
  → `DropdownThemeTest.darkPalette`, `lightPalette`, `ActionsBarWidgetTest.themeChangeRedraws` (min. State-Test)
- **S4 Flip:** GIVEN Anchor nahe unterem Monitorrand, WHEN Popup öffnet, THEN Popup erscheint **über** dem Anchor.
  → `DropdownPopupTest.flipsAboveWhenNearBottom`
- **S5 Deactivate:** GIVEN Popup offen, WHEN Popup-Shell verliert den Focus, THEN Popup schließt.
  → `DropdownPopupTest.closesOnDeactivate`
- **S6 Regression Picker-Verhalten:** GIVEN laufende Konfiguration, WHEN `setAgents` (Reload), THEN Auswahl bleibt per Name; WHEN `updateModeUI` (Handoff), THEN Selection springt; WHEN `lockWhileWorking(true)`, THEN Picker disabled + offenes Popup zu.
  → `ActionsBarWidgetTest.preservesSelectionOnReload`, `updateModeUI`, `lockWhileWorking`
- **S7 Dispose-Safety:** GIVEN Popup offen, WHEN Chat-View/Parent disposed, THEN keine Exception, Popup-Shell + Colors disposed.
  → `DropdownButtonTest.disposesPopupOnDispose`
- **S8 Model-Picker Popup:** GIVEN Config-Page, WHEN Klick auf Model-Dropdown, THEN Popup mit Modell-Liste; WHEN Klick auf Modell, THEN Modell gesetzt.
  → `ModelComboWidgetTest` (S8)
- **S9 Fetch-Fehler:** GIVEN Model-Liste vorhanden, WHEN Fetch schlägt fehl, THEN Dropdown zeigt die **alte** Liste (kein Clear).
  → `ModelComboWidgetTest` (S9)
- **S10 Fetch-Erfolg:** GIVEN Model-Dropdown, WHEN Fetch erfolgreich, THEN Dropdown zeigt neue Liste + configured Modell selektiert.
  → `ModelComboWidgetTest` (S10)

## 7. Test-Strategie

Alle Tests in `org.sterl.llmpeon.test` (OSGi JUnit 4, Workbench-Display, `Assume`-Skip).
Keine Pixel-/Paint-Assertions — nur State, Callbacks, Layout-Größen, Positionen:

1. `dropdown/DropdownItemTest` — record-Immutabilität/Equality (rein, kein Display).
2. `dropdown/DropdownThemeTest` — exakte RGB-Werte dark/light (rein); Color-Cache: gleiche
   Instanz bei gleichem Theme, dispose-safe.
3. `dropdown/DropdownPopupTest` — S1/S2/S4/S5 + max-Height (z. B. 20 Items →
   ScrolledComposite-Client ≤ 15*RowHeight) + Scrollbar-Breiten-Kompensation (Shell-Breite >
   breites Label).
4. `dropdown/DropdownButtonTest` — S1 (Listener), `getSelectedItemId`, `computeSize` > 0,
   disable-State, S7 (Dispose mit offenem Popup).
5. `widget/ActionsBarWidgetTest` — S6 komplett (setAgents/updateModeUI/lockWhileWorking),
   S3-State, bestehende Public-API bleibt.

## 8. Inkremente (jedes grün, eigener `inc-N`-Commit)

- **inc-1 — Modell + Theme-Data:** `DropdownItem`, `DropdownTheme` (RGB + Color-Cache).
  Tests 1–2. (Kein sichtbarer UI-Change.) ✅ `DropdownItem`/`DropdownTheme` + `DropdownItemTest`(3)/`DropdownThemeTest`(4); Suite 133 grün.
- **inc-2 — `DropdownPopup` v1 (standalone-funktionsfähig):** Zeilen, Hover, Klick-Auswahl,
  Esc/Deactivate-Schließen, Position unter Anchor. Test 3 (Teil).
  ✅ `DropdownPopup` + `DropdownPopupTest`(2: selectsAndCloses, closesOnDeactivate); Theme-Farben +
  Rahmen-Paint + Selected-Row-Highlight vorgezogen (standalone-funktionsfähig); Keyboard-Nav/Flip/
  Clamp/Max-Height/Scrollbar bleiben inc-4. Suite 135 grün.
- **inc-3 — `DropdownButton` v1:** painted Button, Popup-Toggle, Selection-Callback,
  natürlicher Width, Dispose-Safety. Test 4.
- **inc-4 — Popup-Polish + Theming:** Keyboard-Nav, `DropdownTheme`-Farben anwenden,
  Rahmen-Paint (Rounded, Fallback eckig), max-Height + Scrolling, Monitor-Flip + Clamp,
  Scrollbar-Kompensation. Test 3 (vollständig), manuelle Mac-Verifikation (dark + light).
- **inc-5 — Wiring in `ActionsBarWidget`:** `Combo agentCombo` → `DropdownButton`;
  Theme-Listener + Cleanup; Public API unverändert. Test 5; `eclipseBuildProject` +
  OSGi-Suite grün; manuelle Verifikation im Workbench (Beide Themes).
- **inc-6 — Wiring in `ModelComboWidget`:** `CCombo` → `DropdownButton` (Design §3.5);
  Public API 1:1 (`setModel`/`getModel`/`fetchModels` + Constructor + `FetchSnapshot`);
  Refresh-Button bleibt; `applyModelList` → `setItems` + `setSelectedItemId`;
  Empty-List → Button leer, kein Popup; Config-Pages unverändert.
  `ModelComboWidgetTest` angepasst (CCombo → DropdownButton Assertions) + S8–S10.
  `eclipseBuildProject` + OSGi-Suite grün; manuelle Verifikation Config-Pages (beide Themes).
- **inc-7 — SOLL-abhängige Feinpolitur + Abschluss:** Icons/Check/Tooltip je nach F3–F5
  (falls „ja"), Homepage-Check, Skill-Update-Hinweis, finaler Build + Testlauf.

## 9. Offene SOLL-Fragen (User muss beantworten — Default steht in Klammern)

- **F1 (Scope-Bestätigung): BEANTWORTET (PO, 2026-09-02): BOTH** — Agent-Picker **und**
  Model-Dropdown (Basic + per-agent Sections) als Custom-Dropdown; übrige Config-Dropdowns
  (Think/Provider/…) bleiben stock.
- **F2 (Farben):** Referenz-Palette (dark `#1E1F22`/`#184785`, light `#FFF`/`#D4E2FF`)
  **oder** Peon-eigene (Chat-Fläche `#1E1E1E`/weiß)? (Default: Referenz)
- **F3 (Pfeil + Agent-Icons):** gezeichneter GC-Pfeil (Default) oder SVG-Asset wie die
  übrigen Chat-Buttons? Agent-Icons im Picker: ja/nein für v1? (Default: nein)
- **F4 (Markierung selektierten Items):** Check-Icon neben dem aktuellen Agenten?
  (Default: nein in v1)
- **F5 (Follow-ups): TEILS BEANTWORTET (PO, 2026-09-02):** Model-Picker ist **im Scope**
  (inc-6). CSS-Theme-Schicht + Hover-Shells bleiben Follow-ups (eigene Stories).
