# Config-Dropdown-UI — IST (Peon) vs. SOLL (github-copilot-for-eclipse)

Arbeitsdokument für die PO-Entscheidung: wie unsere Config-Dropdowns aussehen, wie die
Referenz sie baut, und welche Optionen wir haben, unsere Dropdowns optisch anpassbarer zu machen.

> Hinweis: Das Referenz-Repo liegt **nicht** unter `/github-copilot-for-eclipse`, sondern unter
> `/Users/sterlp/dev/workset/copilot-for-eclipse` (Bundles `com.microsoft.copilot.eclipse.*`).

---

## 1. IST — wir (`org.sterl.llmpeon`)

### Widget-Übersicht

| Datei | Rolle | Dropdown-Typ | Styling |
|---|---|---|---|
| `src/org/sterl/llmpeon/parts/config/widgets/ModelComboWidget.java` | Shared "Model:"-Dropdown + Refresh-Button (Basic-Page + alle per-agent Sections) | `CCombo` (editierbar), `new CCombo(this, SWT.BORDER)` | **keins** |
| `src/org/sterl/llmpeon/parts/config/widgets/AgentModelConfigSection.java` | Per-agent Section (URL/Key/Model/Think/Extra-Body) auf der Advanced-Page | Model → delegiert an `ModelComboWidget`; Think → `CCombo` (editierbar, `SWT.BORDER`) | **keins** |
| `src/org/sterl/llmpeon/parts/config/AiConfigPreferenceView.java` | Basic-Page (`FieldEditorPreferencePage`, GRID) | Provider + Shell-Confirmation → JFace `ComboFieldEditor` (intern read-only SWT `Combo`); Model → `ModelComboWidget` | **keins** |
| `src/org/sterl/llmpeon/parts/config/AiAdvancedPreferenceView.java` | Advanced-Page (`FieldEditorPreferencePage`) | keine eigenen Combos — 4× `AgentModelConfigSection` in `TitledGroup` | **keins** |
| `src/org/sterl/llmpeon/parts/widget/ActionsBarWidget.java` | Chat-ActionBar (RowLayout) | Agent-Picker → `Combo`, `new Combo(this, SWT.READ_ONLY)`, feste Breite 120 px | **keins** am Combo (nur `setForeground` am Compact-Button für Token-Warnung) |
| `src/org/sterl/llmpeon/parts/config/McpPreferenceView.java` | MCP-Dialog (Server-Typ) | `Combo`, `SWT.READ_ONLY \| SWT.DROP_DOWN` | **keins** |
| `src/org/sterl/llmpeon/parts/config/EditableComboFieldEditor.java` | Custom JFace FieldEditor (Presets + Freitext) | `Combo`, `SWT.DROP_DOWN`, `minimumWidth = 150` | **keins** — **derzeit ungenutzt** (keine Aufrufstellen) |
| `src/org/sterl/llmpeon/parts/config/VoicePreferenceView.java` | Voice-Settings | JFace `ComboFieldEditor` | **keins** |

### Aufbau (kurz)

- **Zwei `CCombo`-Stellen** (beide editierbar, `SWT.BORDER`): `ModelComboWidget` (asynchrone
  Modellliste per `Job` + `ModelListCache`, `setItems(String[])` auf UI-Thread) und
  `AgentModelConfigSection` (statische Think-Presets aus dem SWT-freien `ThinkValueSupport`).
- **Read-only `Combo`-Stellen**: Agent-Picker im Chat, MCP-Typ-Picker.
- **JFace `ComboFieldEditor`**: Provider, Shell-Confirmation, Voice — alles aus statischen
  `String[][]`-Literalen.
- **Kein Styling/Theming irgendwo**: kein `setFont`/`setBackground`/`setForeground` an Combos,
  kein JFace-Theming, keine E4-CSS-Klassen, keine Custom-Renderer, keine Icons in den Items.
- **Shared Helper**: `ModelComboWidget` (Fetch/Apply-Lebenszyklus) + `ThinkValueSupport` (Mapping).
  Ansonsten baut jede Seite ihre Combos inline.

---

## 2. SOLL — Referenz (`copilot-for-eclipse`)

### Wichtige Erkenntnis zuerst

Die Referenz macht **zwei getrennte Dinge**:

1. **Preference-Pages: stock SWT `Combo` (`SWT.READ_ONLY`), null Styling** — exakt wie wir.
   Kein `CCombo` im ganzen Repo (0 Treffer).
2. **Chat-ActionBar: komplett eigenes Dropdown-Widget** (`DropdownButton` + `DropdownPopup` +
   `ItemController`), das **nicht** auf `Combo`/`CCombo` aufbaut — und das ist der Teil, der
   optisch auffällt.

### Preference-Pages (stock Combos)

| Datei (rel. zu `com.microsoft.copilot.eclipse.ui/src/com/microsoft/copilot/eclipse/ui/`) | Constructor |
|---|---|
| `preferences/CustomInstructionPreferencePage.java` | `new Combo(chatInstrContainer, SWT.DROP_DOWN \| SWT.READ_ONLY)` |
| `preferences/McpPreferencePage.java` | `new Combo(modeSelectorComposite, SWT.READ_ONLY)` |
| `preferences/CustomModesPreferencePage.java` (inner `CreateModeDialog`) | `new Combo(container, SWT.READ_ONLY \| SWT.DROP_DOWN)` |
| `dialogs/mcp/McpServerDetailDialog.java` | `new Combo(parent, SWT.READ_ONLY \| SWT.DROP_DOWN)` |
| `dialogs/jobs/ProjectSelectionDialog.java` | `new Combo(projectComposite, SWT.READ_ONLY \| SWT.DROP_DOWN)` |

- Inhalt: **nur Plain-Strings** (`setItems(String[])`), Mapping zurück per Index oder
  Text-Parsing. Keine Icons, keine Custom-Renderer, keine Cell-Editors.
- Styling: nur `GridData`-Layout + `setToolTipText`.
- Shared Helper: **kein** Combo-Helper — jede Seite baut inline. Dafür
  `PreferencePageUtils` (Links, `STANDARD_CONTENT_HEIGHT = 520`), `WrappableNoteLabel`
  (fette "Note:"-Präfixe pro Group).

### Custom-Dropdown-Familie (der optische Unterschied)

Paket `com/microsoft/copilot/eclipse/ui/swt/`:

| Datei | Rolle |
|---|---|
| `DropdownButton.java` | Custom-gepaintetes `Composite` (Icon + Label + Pfeil); öffnet Popup per Klick/Space/Enter/↓ |
| `DropdownPopup.java` | Borderless `Shell(SWT.NO_TRIM \| SWT.ON_TOP)` + `ScrolledComposite`; Item-Gruppen, Header, 1 px Separator, Keyboard-Navigation (↑/↓/Enter/Escape), max. 15 sichtbare Items, Monitor-aware Positionierung (flipped above anchor) |
| `ItemController.java` | Per-Row Focus/Hover-State-Maschine + gerundetes Focus-Paint (arc 6) |
| `DropdownItem.java` / `DropdownItemGroup.java` | Immutable Item-Modell (Builder): `id`, `label`, `selectedLabel`, `icon`, `suffix`, `tooltip`, `enabled`, `onAction` (Action-Item), `hoverProvider` (Side-Hover-Shell) |

**Drei Styling-Ebenen:**

1. **Explizite SWT-Farben**, theme-aware über `CssConstants` + `UiUtils.isDarkTheme()`:
   - Popup-Hintergrund: dark `(30,31,34)` / light weiß
   - Focus-Hintergrund: dark `(24,71,133)` / light `(212,226,255)`
   - Gerundeter Popup-Rahmen (arc 8) via `PaintListener` + `drawRoundRectangle`
2. **E4 CSS via `IStylingEngine`**: CSS-ids/classes per Widget-Daten
   (`org.eclipse.e4.ui.css.id` / `org.eclipse.e4.ui.css.CssClassName`),
   `stylingEngine.style(control)`; `ItemController` schaltet ids
   `popup-item-default` / `popup-item-selected` / `popup-item-focused` um.
3. **CSS-Dateien** `css/dark.css` & `css/light.css` (+ `macosx-*`, `windows-*`-Varianten):
   ```css
   #dropdown-popup, #dropdown-popup * { color: #D3D2D2; background-color: #1E1F22; }
   #popup-item-focused, #popup-item-focused * { color: #D3D2D2; background-color: #184785; }
   #popup-item-default Label.popup-action-text { color: #74A7DC; }
   .btn-primary { color: #FFFFFF; background-color: #3584F1; }
   ```
   CSS wird auch auf Dialog-Buttons angewendet (`btn-primary` via `setData(CSS_CLASS_NAME_KEY, ...)`).

**Inhalt des Custom-Dropdowns: reiche Objekte** — Icons, Suffixe, Tooltips, Enabled-Flags,
Action-Items (z. B. "Manage Models..."), Gruppen-Header, Hover-Shells (100 ms Polling gegen
Cursor-Transit-Lücken).

### Weitere Config-UI-Patterns der Referenz

- `FieldEditorPreferencePage(GRID)` + `Group`-Sections, geteiltes `GridLayout(1, true)`
- Feste Dialog-Höhen-Strategie (`STANDARD_CONTENT_HEIGHT = 520`, aufgestapelte Groups)
- "Note"-Pattern: jede Group endet mit `WrappableNoteLabel` (fett + auto-wrapping)
- Policy-Disabled-State: `createContents` short-circuits auf `WrappableIconLink`-Banner
- Cross-Page-Links via `PreferencePageUtils.createPreferenceLink`
- Management-UIs als `Table`/`TreeViewer` statt Combos (Modes, BYOK, MCP-Tools)
- Async-Binding: Hintergrund-`Job` in `INIT_JOB_FAMILY`, UI-Refresh per
  `SwtUtils.invokeOnDisplayThreadAsync`

---

## 3. Deltas — was ist anders?

| Aspekt | Wir (IST) | Referenz (SOLL) |
|---|---|---|
| Preference-Page-Combos | `CCombo` (editierbar) + JFace `ComboFieldEditor` | nur stock `Combo` `SWT.READ_ONLY`, kein `CCombo` |
| Styling Preference-Combos | keins | **auch keins** — hier sind wir auf demselben Stand |
| Chat-/ActionBar-Dropdown | stock `Combo` `SWT.READ_ONLY`, 120 px | **eigenes Widget** (`DropdownButton`/`DropdownPopup`): gerundeter Rahmen, Hover/Focus-Highlight, Icons, Suffixe, Gruppen, Keyboard-Navigation, CSS-Theming |
| Theming | nirgends | 3 Ebenen: SWT-Farben (dark/light), E4-CSS-Engine, CSS-Dateien |
| Item-Inhalt | Plain-Strings | Preference: Plain-Strings; Custom-Dropdown: reiche Objekte (Icon, Suffix, Tooltip, Action) |
| Shared-Helper | `ModelComboWidget` (Fetch-Lebenszyklus) — gut | kein Combo-Helper; dafür `PreferencePageUtils`/`WrappableNoteLabel` |
| Editierbarkeit | `CCombo` erlaubt Freitext (Model, Think) | `SWT.READ_ONLY` — Auswahl nur; Freitext gibt es nicht |
| Dialog-Buttons | stock | `btn-primary`-CSS-Klasse |

**Kernbefund:** Der optische Unterschied zwischen uns und der Referenz liegt **nicht** in den
Preference-Pages (beide stock) — sondern in der **Chat-ActionBar**, wo die Referenz ein
komplett eigenes, thembares Dropdown-Widget gebaut hat. Unser `CCombo`-Ansatz in den
Preference-Pages ist funktional (editierbar = Freitext für Model/Think) und unterscheidet sich
optisch nicht von der Referenz.

---

## 4. Empfehlung

Optionen, von günstig nach aufwendig:

### Option A — Agent-Picker auf `SWT.READ_ONLY`-Konsistenz (klein)
Unser Agent-Picker ist schon `Combo`/`READ_ONLY`. Kein Handlungsbedarf; nur dokumentieren,
dass Preference-Combos bewusst `CCombo` sind (Freitext-Feature).

### Option B — E4-CSS-Klassen auf bestehende Combos (mittel)
Die E4-CSS-Engine kann auch stock-`Combo`s stylen (z. B. `Combo`, `Combo Item` Selektor in
`css/dark.css`/`light.css` des Plugins). Geringer Aufwand, aber:
- wirkt auf **alle** Combos im Plugin (auch MCP-Dialog) — globaler Look-Change
- keine Icons/Suffixe/Gruppen möglich
- `CCombo`-Popup ist ein eigenes `Shell`-child — CSS-Erreichbarkeit ist platformabhängig
  (Mac: `CCombo`-Liste wird oft nicht mitgestylt)

### Option C — Custom-Dropdown-Widget à la Referenz (groß)
`DropdownButton`/`DropdownPopup`/`ItemController`-Familie portieren/neubauen und **zuerst**
für den Chat-Agent-Picker einsetzen (das ist der sichtbare Spot), optional später für
`ModelComboWidget`.
- Aufwand: hohes (Paint-Listener, Theme-Abfrage, Keyboard-Nav, Monitor-Positionierung,
  CSS-Dateien, Dark/Light-Tests)
- Nutzen: Icons/Gruppen/Suffixe/Action-Items möglich (z. B. Agent-Picker mit Agent-Icons,
  Model-Picker mit Provider-Gruppen + "Manage..."-Action)
- Voraussetzung: E4-CSS-Setup im Plugin (`IStylingEngine`, CSS-Dateien in
  `plugin.xml`/`e4_styling_engine`-Extension) — heute nicht vorhanden

### Option D — Zwischenstufe: Theme-aware SWT-Farben ohne CSS (mittel)
Nur Ebene 1 der Referenz: `UiUtils.isDarkTheme()`-artige Abfrage + `setBackground`/
`setForeground`/`PaintListener` auf unseren Combos. Kein CSS-Setup nötig, aber auch keine
Item-Reichweite (Icons/Suffixe).

### Meine Einschätzung
- Für **Preference-Pages**: nichts tun (wir sind auf Referenz-Niveau; `CCombo` ist ein
  bewusster Freitext-Vorteil).
- Für den **Chat-Agent-Picker**: Option C ist das, was die Referenz macht und was optisch
  den Unterschied macht — aber erst, wenn ein konkretes SOLL (Icons? Gruppen? Farben?) steht.
  Option B/D als billigerer erster Schritt, falls nur "dunkler Look" gewünscht ist.

---

## 5. Key Files

### Wir (`/org.sterl.llmpeon`)
| Pfad | Beschreibung |
|---|---|
| `src/org/sterl/llmpeon/parts/config/widgets/ModelComboWidget.java` | Shared Model-`CCombo` + Refresh, asynchrone Liste |
| `src/org/sterl/llmpeon/parts/config/widgets/AgentModelConfigSection.java` | Per-agent Section, Think-`CCombo` |
| `src/org/sterl/llmpeon/parts/config/AiConfigPreferenceView.java` | Basic-Page, JFace `ComboFieldEditor`s |
| `src/org/sterl/llmpeon/parts/config/AiAdvancedPreferenceView.java` | Advanced-Page, 4× Agent-Sections |
| `src/org/sterl/llmpeon/parts/widget/ActionsBarWidget.java` | Chat-ActionBar, Agent-`Combo` (READ_ONLY) |
| `src/org/sterl/llmpeon/parts/config/McpPreferenceView.java` | MCP-Dialog-Typ-`Combo` |
| `src/org/sterl/llmpeon/parts/config/EditableComboFieldEditor.java` | Ungenutzter editierbarer FieldEditor |

### Referenz (`/Users/sterlp/dev/workset/copilot-for-eclipse`, Bundle `com.microsoft.copilot.eclipse.ui`)
| Pfad (rel. `src/com/microsoft/copilot/eclipse/ui/`) | Beschreibung |
|---|---|
| `swt/DropdownButton.java` | Custom-gepainteter Dropdown-Button (Icon + Label + Pfeil) |
| `swt/DropdownPopup.java` | Borderless Popup-Shell, Gruppen, Separator, Keyboard-Nav |
| `swt/ItemController.java` | Focus/Hover-State + gerundetes Paint |
| `swt/DropdownItem.java` / `DropdownItemGroup.java` | Item-Modell (Builder) |
| `constants/CssConstants.java` (o. ä.) | Theme-aware SWT-Farben + CSS-Keys |
| `css/dark.css` / `css/light.css` | E4-CSS-Definitionen (`#dropdown-popup`, `#popup-item-focused`, `.btn-primary`) |
| `preferences/CustomInstructionPreferencePage.java` | Beispiel stock `Combo` READ_ONLY in Preference-Page |
| `preferences/McpPreferencePage.java` | Stock `Combo` + Table/Tree-Management-UI |
| `preferences/PreferencePageUtils.java` | Shared Page-Helfer (Links, Standard-Höhe) |
| `preferences/WrappableNoteLabel.java` | "Note:"-Label mit fettem Präfix |
| `chat/ActionBar.java` (o. ä.) | Nutzer des Custom-Dropdowns (Model-/Mode-Picker) |
