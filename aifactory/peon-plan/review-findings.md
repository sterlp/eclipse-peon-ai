# Review-Befunde — AntD-Umbau (Verdicht aus den Review-Runden)

Regel: Befunde stehen **hier**, nie nur in `overview.md` — der nächste Plan überschreibt die Plan-Datei.
Maßstab ist immer das Feature-Doc; „ein Doc erwähnt einen Fall nicht" ist nie eine Erlaubnis für den
Gegenteil-Fall. Review repariert nichts, Jon entscheidet.

## F8 (Review 2026-11-09, Scope `1ba90f55`..`844e0fb5`) — Verdict **REJECTED** → Delta N1

Selbst gemessen: Backend 257/0 (`missCount=1`), UI 243/27 grün, tsc/oxlint/antd-lint 0, Doc-Lint 28
(nur Fremd-Baseline UC-CR/UC-PC).

- **BUG 1 (Vorschau):** `ProjectToolPreview.java:42` baut die Basis ohne `addDelegateTool`,
  `ProjectRuntime.java:127` registriert `PoDelegateTool` auf demselben ToolService — ein `po`-Turn
  führt ein Tool aus, das die Vorschau nie nennt (UC-WP-88 GIVEN 4, config.md „nicht mehr und nicht
  weniger"). Test `AgentSettingsTest:340` prüft nur `contains(...)` und bliebe grün.
  **Jon: Code-Fix** — Vorschau-Basis + `addDelegateTool`, Doc-Zeile bleibt.
- **BUG 2 (zwei Think-Orte):** `config.agents.tsx:130-138` liest Slot-Werte nur beim Mount,
  `patchOf` sendet `think` immer, `config.page.tsx` lädt nach dem Default-Save nicht neu ⇒
  „Think (Default)" auf der Default-Seite ändern+speichern und dann die Dev-Karte speichern dreht den
  Wert still zurück (R-WP-49: beide Orte zeigen **einen** Wert). **Jon: Code-Fix** — nach erfolgreichem
  Default-Save die Agent-Werte neu lesen (geöffnete Karte zeigt dann den neuen Dev-Think).
- **Suspicion Compact+Extra-Body:** Doku R-WP-23 „Compact … gleiche Bedienung" vs. Inc 6 „compact ohne
  beides" (Plan D6 deckt nur die Tools). Core wendet `extraBody` für Compact an ⇒ **Jon: UI-Fix** —
  Compact bekommt das Extra-Body-Feld, ohne Tools/Vorschau (wie im Plugin: „Compact ohne Tools").
- **Instruktionslücke:** `SettingsSnapshot.toString()` gibt `queryParams` aus (A7 deckt nur Header/
  Extra-Body) — **Jon: toString ohne queryParams**, Log-Oberfläche wie die anderen.
- **Mutation:** Identitäts-/Stale-Guard in `config.model.tsx` ungetestet → **ein** Mutationsnachweis
  im Delta (Guard weg ⇒ Suite muss rot werden, Restore per `cp`+`diff`).
- LOW/Pre-existing: Details in `overview.md` §Review findings; nichts Sperrendes.

**N1 gebaut (2026-11-09):** BUG 1 — `ProjectToolPreview` registriert das Delegation-Werkzeug jetzt genau
wie `ProjectRuntime`, bevor geantwortet wird (Vorschau inklusive der 11 `poDelegate`-Namen; Test pinnt
`containsExactly` über alle 33 Namen). BUG 2 — `Agents` liest die Slot-Dateien nach jedem erfolgreichen
Default-Save neu (`reloadToken`; Test `savingTheDefaultThinkShowsTheNewValueInTheDevSlot`). Compact —
bekommt das Extra-Body-Feld, Tools-Feld und Vorschau bleiben aus
(`compactCarriesTheExtraBodyFieldWithoutTools`). `SettingsSnapshot.toString()` nennt nur noch
`queryParamsPresent`. Mutation — der Guard `config.model.tsx:45` überlebte die alte Suite; der neue Test
`astaleAnswerOfTheOldConnectionIsNotOffered` tötet ihn (getipptes `GPT-X` bleibt wortgleich), Restore per
`cp`+`diff` sauber. Grün: Backend 257/0 (`missCount=1`), UI 246/27, tsc/oxlint/antd-lint 0, Doc-Lint 28
(Fremd-Baseline unverändert), test IDs 394.

## Inkrement 5a — Sider + URL (Review 2026-11-07, Scope `c04f8ed1`..`87949a90`) — Verdict **REJECTED** → Delta

Grund nur im Test: die Markierung ist nicht als **Funktion der Adresse** bewiesen. Code korrekt.

**BUG-1 (Delta, gebaut).** `aria-current` / Auswahl wird nirgends **nach** einer Adressänderung geprüft, die
nicht per Klick entstand. Grün gebliebene Mutationen: M1b `useState(activeKey)` + `setSelected(key)`;
M1c `activeKey` beim Mount **eingefroren**; M10 Settings-`aria-current` beim Mount eingefroren. Ursache:
die drei Weiterleitungs-Tests (`app.routes.test.tsx:169-196`) behaupten nur URL + fehlende Chat-Region,
`theSettingsEntryIsCurrentOnSettings` prüft über `unmount()` + Neu-Mount. Regel: R-WP-1
(„Aktion → URL → UI-Change, kein `useState`-Zustand"), UC-WP-52, R-WP-38 („Sider-Markierung und URL einig").
**Risiko:** Sortierung/Ladezustand/Animation hebt die Auswahl auf den Klick — 204 Tests bleiben grün,
Back/Forward markiert ein Projekt, das die Adresse nicht nennt.

**Grün-gebliebene Mutationen sind der Normalbefund dieses Zyklus** (4b: 3 BUGs, 4c: 1 BUG, 5a: 1 BUG).
„Eine Mutation, die grün bleibt, ist ein Befund."

Rot gemachte Mutationen (Beweislage gut): `projectSegment`→`project.id` (9 Tests), `projectOfSegment`→
erster Treffer, Id-Zweig zurück, `aria-current` raus, `collapsedWidth` 0→80, `AppSider` in den `Content`,
Seiten-eigener Listen-Read (`reads === 0` misst die richtige Stelle), `await onReload()` gestrichen.

**LOW / zurückgestellt**
- **LOW-1** `app.routes.tsx:99` zweiter `listProjects()`-Fetch für den Deep-Link (Plan D1 erlaubt).
  Einfacher: über `useAppContext().projects` + `loaded`-Tor. → **5b** (5b fasst die Routen ohnehin an).
- **LOW-2** `chatPath` setzt den Namen unverschlüsselt ein, `navKeyOfPathname` vergleicht den **rohen**,
  `projectOfSegment` den **dekodierten** Wert → bei Namen mit Leerzeichen/`%` (nur hand-editierte
  `projects.json`) öffnet der Chat ohne Markierung. → **Delta** (Auflösung in `navKeyOfPathname` dekodieren).
- **LOW-3** `test/project/project.page.test.tsx:123-137` `theFrameShowsTheProjectTree` behauptet den
  **Rahmen**, Eigentümer wäre `test/app/`. → **5b** (Test verschieben, nicht duplizieren).
- **LOW-4** Doc-Prosa, die R-WP-38 nacherzählt, obwohl die ID verlinkt ist: `app.sider.tsx:36-46`,
  `project.list.ts:14-20`, `last.project.ts:1-9`. Das wertvolle WHY (`collapsed` nie controlled,
  `matchMedia` beim Mount) bleibt. → **5b** (kürzen, nicht löschen).
- **LOW-5** `test/shared/theme.test.ts:234` nutzt `src/app/app.nav.tsx` als synthetischen Pfad — Datei
  existiert seit 5a-1 nicht. → **Delta**.

**Plan-Lücken (Jons Seite, nicht Devs)**
1. §8-Mutationstabelle behauptete „Mark aus lokalem State ⇒ rot" — gilt nur für die platte Variante,
   nicht für geseedete/eingefrorene. Bei R-WP-1 muss die Mutation die **realistische** Form treffen.
2. C-3-Abschluss fehlte im Plan: `open-points.md` blieb ❓, `ui.md`-Ziel sagte „Top-Navigation",
   `memory.md` meldete UI.md-Änderungen als uncommittet. → von Jon korrigiert.
3. UC-WP-70 G3 erste Hälfte („Chat-Fläche nutzt die volle Breite") hatte keinen Abnahmeort — jsdom misst
   keine Breite. → Doc schärft auf „Sider auf Breite 0", Pixel kommen zur Look-Abnahme im Smoke-Test.

**Nicht melden (bewusst entschieden):** Polaritäts-Ausnahme 5a-1 (zwei Navigationen hätten R-WP-38 sofort
verletzt), Zahlen 201/205/204, `NavItem`-Typ = ANTD-A9, **G2 Files fehlt (5b)**, `act()`-Rauschen.

## Inkrement 5b-1 — Einheit = Adresse (Review 2026-11-07, Scope `680f2239`..`b4c202b0`) — Verdict **REJECTED** → Delta

Verhalten stimmt, **UC-WP-70 G2 ist vollständig belegt** (alle vier Teilaussagen ✅), R-WP-38 ✅,
R-WP-33 verhaltensseitig ✅ — aber zwei Nachweise/Kommentare tragen nicht.

**BUG-1 — die breite Tab-Kante hängt an ungepinnter DOM-Adjazenz.** `styles.css:729`
`.files-panel[data-wide='true'] + .files-tab` greift nur, wenn die Fläche unmittelbarer Vorgänger des Tabs
ist. **Gemessen:** Fläche im Fragment **hinter** den Tab gezogen ⇒ **214/214 grün**, der Tab sitzt im
breiten Zustand mitten auf dem Panel (verletzt R-WP-33 / UC-WP-60 „wandert mit der Panelbreite"). Der
Blatt-Test liest nur den **Selektor-Text**, nicht die Nestung. **Delta:** eine DOM-Assertion
`expect(tab.previousElementSibling).toBe(panel)` im offenen breiten Zustand. **Dauerhafte Regel**
(`AGENTS-DEV.md` + globales Memory): CSS, das über `+`/`~` liest, braucht einen Nestungs-Beweis —
jsdom sieht die Kante nicht.

**BUG-2 — Kommentar behauptet eine widerlegte Mechanik.** `app.routes.tsx:66-68`: „a sibling route would
be a new mount …". **Gemessen:** Schwesterroute eingebaut ⇒ grün, **kein** zweiter `/messages`-Read
(React Router 7.18.5). Die Kind-Route ist die **Grammatik**, nicht der Mount-Grund; bewiesen wird der
bleibende Mount über einen **erzwungenen** Remount (`key={pathname}` ⇒ `aRunningTurnSurvivesTheUnitClick`
rot — vom Review nachgefahren). Architektur-Doc ist von Jon korrigiert, der Kommentar zieht nach.

**LOW (bewusst nicht gebaut)**
- `project.files.tsx:261-283` zwei Eingänge in denselben Lesevorgang (`readBranch` Mount ohne
  `loading`-Remember, `loadBranch` Klick mit) — unsichtbar dank `FilesLevel`-Ladehinweis. Nur kommentiert.
- Der Marken-Nachweis sitzt verteilt (`app.paths.test.ts` + `app.routes.test.tsx`), nicht im Sider-Test —
  dort wird `activeKey` gesetzt, also kann er die Adresse nie beweisen. Für künftige Runden wissen.
- `main.tsx` `StrictMode`: der Mount-Effekt liest die Wurzel im Dev-Build zweimal (Test sieht das nie).

**Plan-Lücken (Jons Seite).** (1) §8/§12 erklärten die Panel/Tab-Reihenfolge für testfrei — die Doc
gewährt das nicht (BUG-1). (2) §13.3 nannte die Kind-Route als Mount-Grund (BUG-2).

**INC-5B-D4 — gelöst zugunsten der Doc.** Der Plan (§8 Zeile 3) verlangte „anderes Projekt ⇒ gar kein
Files-Eintrag", R-WP-38 verlangt den Eintrag **unter dem Projekt, das die Adresse nennt** (sonst gibt es bei
offener Chat-Adresse keinen Sider-Griff). Gebaut wie die Doc: genau eine Einheit direkt hinter dem genannten
Projekt, weg bei anderem Projekt und ohne Adresse. Review prüfte gegen die **Doc**, Mutation D (7 rot) belegt.

**Sonderprüfung `project.files.tsx`** (Dev hatte die Datei einmal aus dem Gedächtnis überschrieben): Diff
`680f2239`..`b4c202b0` enthält **nur** die geplanten Hunks; `ROOTS`/`branchKey`/`FilesLevel`/`FileViewer`/
`childPathOf` unverändert — kein erfundener Kopf. Regel: vor dem Vertrauen Diff gegen HEAD verlangen.

**Von 5a hierher erledigt:** Markierung über Adresswechsel bewiesen (M1b/M1c/M10 ⇒ 5/5/1 rot), `%20`-Adresse
markiert denselben Eintrag, Leichen-Pfad im Guard-Test ersetzt.

## Inkrement 4b/4c — Timeline (abgeschlossen, Befunde behoben)

- 4b: drei BUGs (Indikator an „Flag + letztes Element" statt Turn-Identität; Zeitstempel nur am offenen
  Schritt; `border-style: dashed` an vier Seiten). 4c: Delegations-Eintrag unbewiesen am Verlaufsende ⇒
  Positions-Beweis gegen die Antwortzeilen. Lehre: **Identität schützt das _Ob_, nicht das _Wo_**.
- Dauerhafte Regeln dazu stehen in `aifactory/AGENTS-DEV.md`, Episodisches in `dev-notes-antd.md`.

## Inkrement 1 — Look-Restart, 1a + 1b (Review 2026-11-07, Scope `42b08c02`..`d759ce7d`) — Verdict **REJECTED** → N1

Kein Verhaltensfehler: Rahmen und Baum tun, was R-WP-43 / UC-WP-76 / UC-WP-77 verlangen. **Zwei Nachweise
tragen nicht** — beide als grün gebliebene Mutation gemessen.

**BUG-1 (N1, gebaut).** `theme="dark"` und `mode="horizontal"` am Top-Menü waren nirgends gepinnt: A2
(`theHeaderCarriesTheBrandThePagesAndTheName`) zählte die Items, A6
(`theTopBandAndItsMenuComeFromOurTokens`) nur die Tokens — die `Menu.dark*`-Tokens malen also ins Leere,
ohne dass ein Test es merkt. **Gemessen:** `theme="dark"` weg ⇒ **1** rot, `mode="horizontal"` weg ⇒ **1**
rot, jeweils genau `theHeaderCarriesTheBrandThePagesAndTheName`. Gepinnt über die Klassen, die die Basis
selbst auf ihre Liste schreibt (`ant-menu-dark`, `ant-menu-horizontal`).

**BUG-2 (N1, gebaut).** `aRunningTurnSurvivesTheChatUnitClick` startete auf `/chat/demo` und klickte die
Chat-Einheit — **dieselbe Adresse**: ein toter Eintrag ist ein No-op, der Turn überlebt ihn. **Jetzt:** Start
eine Adresse tiefer (`/chat/demo/files`), Behauptungen Adresse wird `/chat/demo`, `files-panel` ist weg,
`/messages` und `/events` je 1×, Live-Zeile bleibt. **Gemessen:** Chat-Zweig in `app.sider.tsx` gestrichen ⇒
**2** rot — `aRunningTurnSurvivesTheChatUnitClick` über die Adresse (`/chat/demo/files` ≠ `/chat/demo`) und
`theChatUnitLeadsBackFromTheFileArea`. Eine geteilte Verdrahtung kann nicht nur einen Test treffen; der
Sider-Unit-Test bleibt der zweite Zeuge für denselben Griff.

**Regel aus diesem Befund (nach `AGENTS-DEV.md` übernommen):** ein Klick-Test braucht eine Behauptung, die der
**Nicht-Klick** nicht erfüllt (Adresswechsel oder entferntes Element). 1b hatte sie schon erkannt („ein
Turn-Test beweist die Adresse des Klicks, nie den Griff") — an diesem Test war sie nicht angewandt.

**LOW-1 (N1, gebaut, nur-löschend).** `activeKey === key` an der Projekt-Marke war unerreichbar: der Deuter
antwortet seit D6 nie `project:<seg>`, `AppSider` hat nur `app.shell.tsx` als Producer, und kein Test setzt
den Key. Entfernt; `projectKeyOf` bleibt Item-Key und Klick-Griff, das Projekt markiert also nie — die Einheit
unter ihm ist die Marke (UC-WP-77).

**LOW-N1-1 (gemeldet, nicht gebaut).** Jons Snippet an `theUnitsOfTheOpenProjectHangUnderIt` wäre die dritte
Stelle derselben Behauptung: `theUnitIsTheOnlyMarkOfTheAddress` (`app.sider.test.tsx:215-217`) prüft
Projekt-Eintrag unmarkiert + `aria-current` `null` bei **demselben** `activeKey`. Und die genannte Mutation
`entry(key, project.name, activeKey === siderRootKey)` ist **nicht** grün: `theFrameShowsTheProjectTree`
(`app.shell.test.tsx:56`) wird rot (`['page','page']` ≠ `['page',null]`), 223 bleiben grün. Nach Jon: „Beweis
fehlt mir nicht" — die Löschungs-Entscheidung ist also belegt; die Zeile wäre Redundanz (AGENTS.md:
Redundanz ist LOW). **Was offen bleibt, ist LOW-4:** die **Wurzel** (`activeKey = root`) ist im Sider-Unit-Test
überhaupt nicht auf Marken geprüft, gedeckt ist sie nur über die Shell-Tests.

**LOW-2 (bewusst nicht gebaut → Browser-Abnahme, Jon).** Der Menüblock sitzt über `background: transparent`
auf dem Band statt allein aus dem Token gemalt. Sieht der Browser das Band richtig, bleibt es; sonst Nachzug.

**LOW-3 (N1, gebaut).** „Kopf- und Sider-Markierung sind einig" war über zwei Mounts verteilt bewiesen.
Neu: `theTopMarkAndTheTreeMarkAgreeInOneRender` — Shell auf `/chat/demo`, **im selben Render** je genau eine
Marke in der jeweiligen Navigation, beide nennen „Chat". Gegenprobe (Seiten-Deuter auf das nackte `/chat`
verengt) ⇒ 4 rot, der neue darunter.

**LOW-4 (bewusst nicht gebaut → Browser-Abnahme, Jon).** B5 war im Unit-Test nur für „keine Adresse"
(`activeKey: null`) behauptet, nicht für die Wurzel `/projects`; die Wurzel ohne Einheiten steht weiterhin in
`theFrameShowsTheProjectTree` (Shell auf `/projects`).

**Zwei Suspicions → Browser-Abnahme (Jon).** Hier nur der Vermerk — der Wortlaut stand nicht im Review-Text
und wird nicht erfunden; bei Bedarf nachtragen.

**Plan-Lücken (Jons Seite, nicht Devs).** (1) §9 B4 schrieb das Szenario mit Start **auf** der Chat-Adresse —
damit war „kein Remount" nicht falsifizierbar (diese BUG-Art ist wiederkehrend: 4b, 4c, 5a, 5b-1, jetzt 1).
(2) §8 A2 + A6 zusammen ließen `theme`/`mode` unbewiesen: ein Look-Inkrement braucht die **Klassen der Basis**
als Pin, nicht nur die Token-Tabelle.

**Stand nach N1:** 224 Tests / 22 Dateien, `tsc -b` 0, oxlint 0/0. Die Browser-Abnahme des Rahmens steht aus.

## Inkrement 4 UI — S1 Sider-Baum (Dev-Bau 2026-11-08, Branch `web-factory-4`)

**Abweichungen vom Plan (Dev meldet, Jon zieht ❌→✅).**

1. **Chat-Kind ohne Icon.** `docs/ui.md` R-WP-44 verlangt das Ordner-Zeichen nur für Files; Paul: „die icons
   für pacos … brauchen wir nicht, … so wie die einträge im top-menu". Die Referenzbilder (`SOLL.png`,
   `side soll.png`) zeigen plain Kinder + Ordner an Files.
2. **`trigger` gesetzt** (`MenuUnfoldOutlined`/`MenuFoldOutlined`) statt Plan-„Default": R-WP-44 nennt die
   beiden Zeichen als **eigenen Griff**; die Basis malt ihr Text-Pill-Rechteck (`ant-layout-sider-trigger`).
3. **`app-sider-unit` und seine CSS-Regel sind weg.** Der Titel bekommt sein Einrück **inline**
   (`@rc-component/menu/hooks/useDirectionStyle.js:24`), die Kinder erben die Stufenmaß 24 px — die eigene
   Regel traf im DOM **0** Knoten, war also tot. Ein Einrück-Beweis wäre ein `.ant-*`-Pin auf die Basis ⇒ Look
   bleibt Browser-Abnahme.
4. **`openGroupsOf` öffnet zusätzlich die Gruppe der Adresse** (Plan nannte nur „< 4 alle, sonst erste"): eine
   Markierung hinter einer zugeklappten Gruppe beantwortete die URL, ohne etwas zu zeigen.
5. **`onOpenChange={setTouched}` erbt einen Basis-Befund:** überscheitet der Sider seinen Breakpoint, liefert
   die Basis eine **leere** Keyliste (`@rc-component/menu/hooks/index.js:55-58`) ⇒ alle Gruppen zu. Bewusst
   hingenommen, weil S1b (Drawer statt Sider unter `xl`, Pauls Vorgabe) den Umschalt-Punkt auflöst.
6. **Plan §5 `npm run check` existiert nicht** in `factory-ui/package.json` ⇒ `npm run lint` + `npx tsc -b` +
   `npx vitest run` + `npx antd lint ./src`.
7. **Plan §7 unterschlug Kollateralschäden:** `app.routes.test.tsx` (6 Stellen: Gruppentitel statt flachem
   Projekt-Eintrag, `/projects` markiert nichts mehr, Klick aufs Chat-Kind), `app.shell.test.tsx` (4 Stellen,
   Kopf-Nav statt Baum), `chat.page.test.tsx:711` (Settings liegt im Kopf, R-WP-43), `app.paths.test.ts`
   (2 Stellen). Alle Beweise bleiben in ihrer Aussage gleich, keiner ist gelöst.
8. **Der Warte-Rahmen bleibt stehen** (`styles.css` `.chat-line.pending`) — S2, nicht S1.

**Einmalige Messungen (Beweise stehen am Test, die Maßzahlen hier).**

- Submenu-Titel = `div[role=menuitem][aria-expanded][aria-haspopup=true]` im `li[role=none]`, Kind =
  `li[role=menuitem]` **ohne** diese Attribute; Kinder einer geschlossenen Gruppe sind gar nicht im DOM.
  ⇒ Differenz über `aria-expanded`, nie über Klassen der Basis.
- Ein Icon-`aria-label` färbt auf den Accessible Name durch („folder Files") ⇒ `aria-hidden` am Ordner-Zeichen.
- **Mutation aus dem Plan überlebt in jsdom:** „Gruppentitel an `onOpen`" als `label: <span onClick=…>` gemalt,
  bleibt grün — ein Klick auf den Titel-Knoten trifft ein **Kind** nicht. Die Mutation muss am Menü selbst
  ansetzen (`onOpenChange`/`onClick`).
- **Klick-Falle (AGENTS-DEV, hier wieder gemessen):** der Titel klappt **und** die Adresse bleibt — der
  Nicht-Klick erfüllt „Adresse unverändert" ohnehin. Der Beweis ist das **Attribut** (`aria-expanded`
  wechselt) plus „weder `onOpen` noch `onFiles`".

**Mutationen (Plan §7-S1, drei, alle getötet).**

- Gruppentitel an `onOpen` (über `onOpenChange`) ⇒ `groupTitleOnlyTogglesTheGroup` **und** der
  Collapse-Test rot.
- Chat-Zweig im `onClick` gestrichen ⇒ `chatUnitOpensTheChatAddress` **und** der Collapse-Test rot.
- `openKeys` auf „immer alle" ⇒ **genau** `groupsAreOpenUntilFourProjects` rot.
- Wie im Plan vorgesehen **nicht** gefahren: „Projekt zurück als flaches `entry`" — der Struktur-Test ist durch
  seinen Aufbau falsifizierbar (`aria-expanded` am Titel, `previousElementSibling` für „Files zuletzt").

**Stand nach S1:** 214 Tests / 22 Dateien grün, `tsc -b` 0, oxlint 0/0, `antd lint ./src` 0. Look (bündiger
Rand, 250 px, weiße Fläche, Einrück) ist **nicht** getestet — Paul am Browser. `docs/ui.md` und
`docs/memory.md` tragen Pauls laufende Edits und sind **nicht** committet. Offen: S1b Drawer unter `xl`.

## Inkrement 4 UI — S1b, Griff im Kopf (Dev-Bau 2026-11-08, Freigabe Jon nach Pauls Entscheidung)

**Übersteuerung des Plans (keine Dev-Entscheidung):** S1 baute den Griff noch am Baum (`trigger` =
`MenuFold/MenuUnfold`). Paul: das Menü braucht man nur kurz, der Chat soll die Fläche haben ⇒ **ein**
Text-Button im Kopf, `trigger={null}` am Baum. Pauls Drawer-Hinweis betraf den **Files**-Slide-out
(R-WP-33, eigener Slice nach S5), **nicht** den Sider — deshalb bleibt der Sider.

**Was zog:** `collapsed` gehört dem **Rahmen** (`app.shell.tsx`), nicht dem Baum — zwei fragen dasselbe
(Band-Button, Breakpoint-Hörer der Basis) und eine Antwort darf nicht zweimal wohnen. Neu
`src/app/app.sider.toggle.tsx`; Katalog-Name `content.nav.toggleSider(collapsed)` (Der Button malt Glyphen,
sein Name sagt, was der Druck tut — dieselbe Lesart wie `theme.toggle`). **Kein zh-Katalog in `factory-ui`**
(`content.ts` ist englisch, AGENTS.md: „UI: English") — Jons „EN + zh" gilt hier nicht.

**Befund, der S1-Befund 5 behebt statt nur hinzunehmen:** ein `Menu` im gefalteten `Layout.Sider` schaltet auf
die Popup-Form der Basis und meldet dabei `onOpenChange([])` ⇒ **alle Gruppen zu**, ohne jeden Klick. Mit dem
Griff im Kopf wird die Kante täglich überschritten, also `inlineCollapsed={false}` gesetzt
(`menu.js:88`: `inlineCollapsed ?? siderCollapsed`). Nachweis: neuer Shell-Test; Mutation ⇒ Chat-Kind fehlt im
DOM. Die `antd`-CLI kennt die Prop in `info Menu` nicht und `antd lint` schweigt — Maß ist der Quelltext.

**Look, nicht getestet (Paul am Browser):** ein Text-`Button` der Basis erbt die Bandfarbe **nicht**
(`Layout.headerColor` wirkt nur auf Text im Band) ⇒ `.app-sider-toggle` über `--top-text`, `:hover`
mitgenommen, damit das Zeichen beim Draufzeigen nicht dunkel blinkt.

**Beweis-Arbeit:** UC-WP-70 ist **umgezogen**, nicht gemehrt — der Sider-Unit-Test kann einen Kopf-Knopf nicht
sehen. Neu in `app.shell.test.tsx`: `theFrameFoldsTheTreeAndTheHandleOfTheBandReachesItAgain` (eng ⇒ gefaltet
ohne Klick; ein Knopf faltet auf und zu; die offene Auswahl führt auf eine Adresse, die vorher nicht galt).
Im Sider bleibt `theSiderWidthsItsTwoStatesAndCarriesNoHandleOfItsOwn` (0 px / 250 px, die Basis malt in
**beiden** Zuständen keinen eigenen Griff, `onCollapse(…, 'responsive')` geht an den Rahmen).
Helper `mediaAnswer`/`belowTheBreakpoint` wandern nach `test/shared/media.ts` (zwei Nutzer statt Kopie).

**Mutationen (drei, jede von genau dem geplanten Beweis getötet).** `onToggle` als Leerhandler ⇒ der neue
Shell-Test („the handle opens the tree") · `inlineCollapsed={false}` gestrichen ⇒ derselbe
(„Unable to find role=menuitem name Chat") · `trigger={null}` gestrichen ⇒ der Sider-Test (`handles()` findet
den Null-Breiten-Auslöser der Basis).

**Stand nach S1b:** 215 Tests / 22 Dateien grün, `tsc -b` 0, oxlint 0/0, `antd lint ./src` 0 (47 Dateien).

## Inkrement 4 UI — S2 Warte-Rahmen gelöscht (Dev-Bau 2026-11-08, Freigabe Jon)

**Gelöscht, nichts gebaut:** `.chat-log .chat-line.pending` samt Regelkommentar (`styles.css`),
`--frame-pending` aus **beiden** Tabellen (`index.css` hell + dunkel — Token danach nirgends), und der
`pending`-Pfad durch die Oberflächen-Schicht: `ChatLogProps`, `Row`, beide `map`-Äste der Zeilen, beide
className-Ableitungen (`chat.log.tsx`), `const pending` + `pending={pending}` (`chat.page.tsx`).
**`runningTurn` bleibt** — es speist `busy` (Stop-Knopf) und die Warte-**Leiste** (UC-WP-58), die stehen
darf. Kommentare, die den Rahmen behaupteten, sind mit dem Rahmen weg oder auf die Leiste umgestellt
(`chat.page.tsx` im Stop-Pfad: R-WP-31/UC-WP-50 statt UC-WP-65).

**Beweis-Arbeit (memory #4 — verengen statt löschen, Abwesenheit nur falsifizierbar über das Blatt):**
`pendingFrameExistsWhileTheTurnRuns` **gelöscht** — seine positive Hälfte *ist* die gelöschte Regel. Ihr
negativer Rest wohnt im einen verbleibenden Rahmen-Test
`stopLeavesNoPendingFrame` → **`noWaitingFrameOnAnyLineAtAnyTime`** (UC-WP-65): zuerst
`expect(declares('.chat-line.pending')).toBe(false)` (ohne diesen Anker wäre die DOM-Frage vakuös — eine
Klasse, die nichts malt, kann nicht gefunden werden), dann „keine Rahmen-Zeile" **während** des Turns,
**nach** dem Stop und **nach** `done`, dazu die lebenden Behauptungen: Warte-Leiste endet mit dem Stop und
die gezeigten Zeilen bleiben (`chat-live`, `chat-log`). `failedTurnLeavesNoPendingFrame` auf UC-WP-64
**verengt** (wartender Input behält seinen Text, genau eine Fehler-Zeile);
`aLiveFailureIsALineAndNotAWaitingFrame` → **`aLiveFailureIsALineOfTheHistory`** (UC-WP-64).
`framedLines()` bleibt als Ein-Zeiler des einen Tests, sein Kommentar nennt jetzt den Blatt-Anker.

**Mutation (eine, wie geplant):** `.chat-line.pending` zurück in `styles.css` (mit `var(--tool)`, weil der
Token weg ist — gemessen wird der Selektor) ⇒ **genau ein** Treffer:
`noWaitingFrameOnAnyLineAtAnyTime` — „the sheet states no waiting frame anymore: expected true to be
false". Regel wieder raus, Suite danach grün.

**Nennenswert:** (1) `failedTurnLeavesNoPendingFrame` behält seinen Namen (Plan §6 Zeile 8), obwohl der
Rahmen weg ist — sein ID-Kommentar heißt jetzt UC-WP-64; Vorschlag an Jon für eine spätere Runde:
`failedTurnKeepsTheWaitingInputAndOneErrorLine`. (2) Die `pending={false}`-Argumente sind mechanisch aus
**34** Anrufstellen geflogen (`chat.timeline` 30, `chat.diff` 3, `chat.log` 3) — Compiler-getrieben, kein
Beweis-Inventar. (3) Zeitsprung der Suite: **215 → 214** Tests (ein Test gelöscht, keiner neu).

**Stand nach S2:** 214 Tests / 22 Dateien grün, `tsc -b` 0, oxlint 0/0 (79 Dateien), `antd lint ./src` 0
(47 Dateien).

## Inkrement 4 UI — S3 Status-Indikator (Dev-Bau 2026-11-08, Freigabe Jon)

**Gebaut:** neu `src/chat/chat.status.tsx` — `WorkingIndicator` (AntD `Spin` + `LoadingOutlined`, Griff
`working-indicator`) und `AgentStatus` (Griff `agent-status`, **kein** eigenes `data-state` — der Wirt
`chat.tiles.tsx` trägt `data-state` seit je). Kachel: `AgentStatus` zieht in den `tile-state-<name>`-Span,
Wort bleibt; Clear/Compact sind `Button size="small"` (R-WP-43). Timeline: offener Eintrag bekommt
`loading` **und** das Widget als Punkt. Blatt: `.chat-tiles{margin:0}`, drei Widget-Klassen
(`.agent-status`, `.agent-status-idle` 0.75rem = 12 px, `.agent-status-error`),
`.working-indicator-question` auf dem **Indikator**-Knoten.

**Plan-Fund 1 — die „gestrichenen" Badge-Reste gab es nie:** §3-S3 wollte `Badge` im Titel,
`badgeColor`/`badgeStatus` und den Kommentar „kein quadratischer Icon-Würfel" streichen; `git log -S Badge`
auf `chat.tiles.tsx` ist **leer**, die Datei kannte nie ein Badge. Die zweite Anzeige, die R-WP-45 meint,
war der **8-px-Punkt des Blatts** (`.chat-tile-state::before` samt drei Zustandsvarianten) — genau den
beschreibt die Doc („heute ein schwarzer/grauer Punkt"). Gestrichen; „größer als bisher" heißt damit
12 px gegen 8 px, nicht gegen einen 6-px-Badge-Dot.

**Plan-Fund 2 — zwei veraltete Props:** `items.dot` der Timeline ist in v6 **veraltet** („Please use
`items.icon`", gemessen als Runtime-Warnung der Suite) — gebaut mit `icon`, dasselbe Ergebnis
(`useItems.js`: `mergedIcon = icon ?? dot`, `status: loading ? 'process' : 'finish'`). Und `Spin`-`size`
heißt `small|medium|large`; `size="default"` ist veraltet (`spin/index.js:86`) — die Signatur von §2.2
(`'small' | 'default'`) ist auf `'small' | 'medium' | 'large'` gestellt, dazu `tone: 'accent' | 'question'`.

**Warum die Farbe auf dem Indikator sitzt:** die Basis färbt `.ant-spin.ant-spin-section` mit `colorPrimary`
(0,2,0). Ein Farbanspruch des Wrappers verliert gegen diese Erklärung am Kind; eine Klasse **auf dem
Indikator-Knoten** (`classNames.indicator`, von der Basis nicht eingefärbt) gewinnt gegen jede Vererbung —
kein `.ant-*` im eigenen Blatt, kein hex in TSX.

**Konflikt mit der Basis-Wache, zugunsten der Docs gelöst:** `test/shared/theme.test.ts`
(`theBasisIsTheOnlyDesignLibraryOfTheApp`) verbietet Basis-Importe im ganzen `src/chat`-Ordner, Ausnahmen
waren `chat.composer.tsx` + `chat.timeline.tsx`. Beides in S3 ist von den Docs **verlangt**: R-WP-43
(`docs/ui.md:169` — „Aktionen … sind echte AntD-Elemente … auch an den Kacheln") und R-WP-45
(`docs/chat.md:230` — „AntD `Spin` mit `LoadingOutlined` … keine eigene CSS-Rotation"). Ausnahmeliste um
`chat.tiles.tsx` und `chat.status.tsx` erweitert, der Kommentar nennt jetzt die vier Regel-Geber. Der
Stimmen-Bann (`chat.log.tsx`, `chat.markdown.tsx`) ist unangetastet und beweist weiter. **Bitte:** den
Ausnahme-Satz in R-WP-39/UC-WP-71 nachzuziehen ist Jons Seite, nicht meine.

**Beweis-Arbeit:** Helper `workingEntries()` → **`workingMarks()`**, verankert am
`[data-testid=working-indicator]` im Log — AntDs eigener Lade-Punkt trägt keinen Griff, also ist der
Hersteller bewiesen. Zwei Identitäts-Assertions lasen `data-trace` **über das Mark-Element**; der Punkt sitzt
außerhalb unseres Inhalts-Knotens, deshalb fragen sie jetzt `data-working` am Eintrag (zwei Stellen in
`onlyOneEntryOfTheTurnWorksAtATime`). Drei Einzeiler in `chat.tiles.test.tsx` (WORKING/IDLE, COMPACTING,
ERROR), `// UC-WP-79` an den drei, `// UC-WP-81` an `teamTilesShowContextAndStatus` und an
`onlyOneEntryOfTheTurnWorksAtATime`. Kein neuer Test, keine neue Test-Datei.

**Mutationen (beide geplant, jede tötet wie gemeldet):** (1) `icon:` an `chat.timeline.tsx` gestrichen ⇒
**genau zwei** sterben (`onlyOneEntryOfTheTurnWorksAtATime`, `aThinkEntryWorksAndHandsTheDotToTheNextStep`).
Die übrigen drei der fünf können per Konstruktion nicht sterben — sie behaupten Abwesenheit; der
Hersteller-Beweis liegt an den zwei „genau eine Mark"-Behauptungen. (2) `AgentStatus` mit eigenem `Spin`
statt Delegation ⇒ **genau** `teamTilesShowContextAndStatus`.

**Einmalige Messung (Doppel-Spin), Ergebnis statt Test:** zweiten `WorkingIndicator` in die
Live-Antwortzeile von `chat.log.tsx` gebaut, Suite laufen lassen ⇒ **zwei** Tests sterben:
`aStepNeverWorksForATurnThatDidNotReportIt` („an open turn without a step of its own has no working step")
und `theIndicatorOfAStepEndsWithTheNextStepAndNotWithTheAnswer` („with the end of the answer no step works").
Besser als §3-S3 annahm: die neue Verankerung bemerkt eine zweite Arbeits-Mark **im Log**. Ungeschützt
bleibt bewusst „Kachel **und** Log gleichzeitig" — zwei Fragen (Agent arbeitet / dieser Schritt arbeitet),
die nie in einem Mount zusammenkommen. Messung zurückgebaut, kein Test angelegt.

**Zusatzaufgabe (genehmigt):** `failedTurnLeavesNoPendingFrame` →
`failedTurnKeepsTheWaitingInputAndOneErrorLine` (nur Name, Behauptungen unverändert).

**Look, nicht getestet (Paul am Browser):** Spin in Kachel und im Timeline-Punkt (`size="small"`), Gelb beim
Kompaktieren, rotes gefülltes Zeichen bei Fehler, 12-px-Kreis bei bereit, Kachelreihe ohne Abstand über dem
Chat, und die **Knopfmaße der Kachel**: unsere `button`-Regel im Blatt ist weg, sonst hätte sie der Basis
jede Erklärung weggenommen (R-WP-39 — ein Aussehen, ein Owner).

**Stand nach S3:** 214 Tests / 22 Dateien grün, `tsc -b` 0, oxlint 0/0 (79 Dateien), `antd lint ./src` 0


## Inkrement 4 UI — S4 Blasen, Fläche, Composer (Dev-Bau 2026-11-08, Freigabe Jon)

**Gebaut, nur Look, kein Test angefasst:** die drei Zeilen-Regeln des Verlaufs
(`.chat-log .chat-line.agent/.user/.error`) verlieren `max-width: min(85%, var(--content-max))` und ihre
Seite und stehen auf `justify-self: stretch`; die Selektoren selbst bleiben, sonst läse die bestehende
Look-Wache „Blase fehlt". Neuer Token `--chat-gutter: 1rem` (`index.css`, eine Tabelle — das Maß ist
modusunabhängig) an den Chat-Flächen; Composer weiß (`--surface`), zweispaltiges Grid
(`minmax(0,1fr) auto`, `align-items: end`), Senden rechts unten am Feld, Resend/Stop in der zweiten Zeile.

**Plan-Fund 1 — `.view` war schon erledigt:** §3-S4 verlangt `--chat-gutter` **auch** an `.view`, „sonst holt
deren `padding-inline` den Rand zurück". `.chat-workspace > .view` (`styles.css:693-699`) setzt seit Inc 3
`padding-inline: 0` für die Chat-Spalte — die Begründung gilt für die **anderen** Views (Projektliste,
Config), denen ihr Einzug bleiben muss. `.view` darum **unverändert**; sonst wäre diese App an jeder Seite
randlos.

**Plan-Fund 2 — „Markup unverändert" war nicht machbar:** der Composer wohnt nicht in `chat.page.tsx`,
sondern in `src/chat/chat.composer.tsx`, und das Senden hing in `.chat-composer-actions`. Ein Kind eines
Grid-Elements kann kein Feld des **Eltern**-Grids beziehen — §3-S4 verlangt gerade Spalte 2 in der Feldzeile.
Ein Knopf aus der Gruppe heraus direkt ans Form-Ende, `className="chat-send"` neu, Griffe und
`disabled`-Semantik unverändert, **kein Test geändert**: kein Beweis las die Gruppe, und die Nachbarschaft
`chat-waiting` → `chat-composer` (`chat.page.test.tsx:1711`) bleibt grün.

**Sechs Flächen statt vier:** §3-S4 nennt Head, Kacheln, Log, Composer. Warte-Leiste (`margin-inline`) und
die Fehler-Zeile der Seite (`padding-inline`) trugen dasselbe `--content-gutter`; blieben sie dort, stünden
sie weit eingerückt über einem randlosen Verlauf — genau die Asymmetrie, die A3 wegnimmt. Beide mitgezogen.

**Offen an Jon (bewusst nicht gebaut):** §3-S3 kündigt an „Der Composer folgt in S4" — R-WP-43
(`docs/ui.md:169`) zählt **Senden** zu den Aktionen, die echte AntD-Elemente sind. Jons S4-Freigabe sagt
„nur Look" und „Markup unverändert". Nicht gebaut; ein eigener Schritt (drei Knöpfe → `Button`, fürs Senden
`htmlType="submit"` — nicht `type`), wenn Jon ihn freigibt.

**Höhe = Text, im Blatt keine Ursache:** keine Regel gab einer Blase je ein Höhenmaß; der Log ist ein
**ein**-spaltiges Grid, jede Zeile gehört einem Eintrag, `align-items` steht dort nie. `padding` unverändert
(8/12 px), kein neues Polster, kein Pixel-Token — Pauls Eindruck „deutlich höher als der Inhalt" ist im Blatt
nicht belegt und wird am Browser abgenommen.

**Kein Test, keine Mutation** (Plan §7 für S4 so gesetzt). Nachgemessen an HEAD, dass nichts kippt:
`noAnswerLineIsCentredOrRightAligned` prüft nur `.chat-log .chat-line` und `.chat-line.agent` gegen
`justify-self:(end|center)` — `stretch` ist kein Verstoß, die `.user`-Zeile prüft sie nicht (F7);
`block('.chat-composer')` („kein `min-height`, kein `resize`") hält weiter; niemand pinnt das 85-%-Maß.

**Look, nicht getestet (Paul am Browser):** Blasen über die ganze Breite (Nutzer blau, Antwort Karte, Fehler
Zeile auf der Fläche), Verlauf randnah statt zentriert, weißer Composer über die Breite, Senden rechts unten am
Feld, Resend/Stop ruhig darunter, Warte-Leiste und Fehler-Zeile im selben Rand wie Verlauf und Feld.

**Stand nach S4:** 214 Tests / 22 Dateien grün (kein Test geändert), `tsc -b` 0, oxlint 0/0 (80 Dateien),
`antd lint ./src` 0 (48 Dateien), `vite build` grün.


## Inkrement 4 UI — S5 Scroll am Ende + Weg zurück (Dev-Bau 2026-11-08, Freigabe Jon)

**Gebaut:** `src/chat/chat.scroll.ts` (neu, 68 Z) — `useFollowBottom(logRef)` hält Lese-Position (Ref),
`following` (State für den Knopf), `onLogScroll`, `followNow`, `followIfFollowing` und `SCROLL_END_SLOP`;
`src/chat/chat.scroll.bottom.tsx` (neu, 28 Z) — AntD `FloatButton` + `ArrowDownOutlined`,
`tooltip`/`aria-label` aus dem Katalog (`content.chat.scrollBottom`); `send()` ruft `followNow()` **vor**
`writePending`, `resend()` erbt es. Zwei neue Tests in `chat.page.test.tsx` (UC-WP-80), beide UC-WP-66-Beweise
unangetastet grün. **Teilaufgabe Composer (R-WP-43):** drei Knöpfe → AntD `Button`, Senden mit
`htmlType="submit"` — Griffe, `disabled`-Semantik, Enter-Submit und der eingefrorene
Aufzählungs-Beweis (`chat.page.test.tsx:681`) unverändert grün.

**Plan-Fund 1 — der Sprung:** §2.4/§2.2 wollen `scrollTop = scrollHeight - clientHeight`. Die beiden
bestehenden UC-WP-66-Beweise pinnen `1_000` bei `scrollHeight = 1_000, clientHeight = 300` — die neue Formel
hätte beide umgeschrieben, Jon verlangte ausdrücklich „unangetastet grün". Gebaut wie bisher
(`scrollTop = scrollHeight`): ein Browser klemmt auf das Maximum ab, das Ziel ist dasselbe.

**Plan-Fund 2 — `LanguagePack`/zh:** §2.2 verlangt den Text „EN **und** zh, sonst TS-Fehler"; es gibt keinen
zh-Katalog und keinen `src/i18n`-Ordner — `src/shared/content.ts` ist EN-only (Jons Freigabe hatte das
berechtigterweise korrigiert).

**Abweichung 1 — der Auslöser bleibt auf der Seite:** der Hook besitzt Sprung, Lese-Position, Hörer und Slop;
die drei Zeilen `useEffect(() => followIfFollowing(), [messages, live, outgoing, …])` bleiben in
`chat.page.tsx`. Nur diese Seite weiß, welche Zustände neue Zeilen bedeuten. Hinein gezogen hätte es entweder
ein variables Dependency-Array (React-/Lint-Anti-Pattern) oder eine Revisionszahl gekostet — und eine Zahl
hätte das Wachstum des Textes **innerhalb** eines Live-Eintrags übersehen: Teil-Antworten ziehen heute mit,
weil jedes Event ein neues Array schreibt. Dafür eine Methode mehr im API (`followIfFollowing`).

**Abweichung 2 — Signatur des Hörers:** `onLogScroll: () => void` statt `UIEventHandler<HTMLElement>`.
`ChatLog` nimmt `onScroll?: () => void`; ein Handler mit Parameter ist darauf nicht assignable. Gelesen wird
die Fläche ohnehin am Ref, nicht am Event.

**Fund 3 — die Stelle des FloatButtons:** die Basis setzt `.ant-float-btn-individual` auf
`position: fixed` samt Einzug-Tokens (`node_modules/antd/es/float-button/style/button.js:44-51`) — an der
Fensterecke landet der Knopf **auf** der Eingabe-Zeile, genau auf „Senden" (Band ≈ 78 px, Knopf 40 px bei
24 px Einzug). Darum trägt er `classNames={{ root: 'chat-scroll-bottom-btn' }}` und hängt in unserer
null-hohen Box am Fuß des Verlaufs: Knopf und Aussehen bleiben die der Basis, die Stelle ist unsere
(R-WP-39). Kein `.ant-*` im eigenen Blatt, kein gesetztes Pixelmaß. Die `pure`-Variante der Basis ist kein
öffentliches Prop (nur `PurePanel`).

**Basis-Wache, wie in S3 zugunsten der Doc erweitert:** `test/shared/theme.test.ts` —
`basisOwnSurface` um `chat.scroll.bottom.tsx`, Kommentar nennt jetzt R-WP-35 („ein schwebender Knopf … AntD
`FloatButton`, wie `BackTop`, nur umgekehrt"). Der Stimmen-Bann (`chat.log`, `chat.markdown`) ist unangetastet.
**Bitte:** der Ausnahme-Satz in R-WP-39/UC-WP-71 bleibt Jons Seite.

**Mutationen (drei statt zwei, weil eine nicht einfach war):**

| Mutation | Getötete Tests |
|---|---|
| `followNow()` im `send`-Pfad gestrichen | **genau** `ownInputAlwaysShowsWhenSentFromTheTop` |
| Sichtbarkeit auf „nie" (`{false && …}`) | **beide** UC-WP-80-Tests — der zweite pinnt die Anwesenheit des Knopfes selbst |
| Klick mit nichts verbunden (`onClick` → noop) | **genau** `scrollBottomButtonAppearsAboveAndReArmsAutoScroll` |

Jons Vorgabe war „je genau ein Treffer"; die Sichtbarkeits-Mutation kann das nicht liefern, ohne einen der
beiden Tests zu schwächen — deshalb die dritte Mutation am Verdrahtungspunkt Knopf → Hook. `htmlType="submit"`
braucht keine eigene Mutation: ~30 bestehende Tests klicken `chat-send` und verlangen den Send, ein
`type="button"`-Rückfall ließe keinen davon grün werden.

**Look, nicht getestet (Paul am Browser):** schwebender Pfeil rechts über dem Verlauf (über der Warte-Leiste,
wenn sie steht), Composer-Knöpfe in den Maßen der Basis statt unserer `0.4rem/0.8rem`-Regel, Senden rechts unten
am Feld.

**Stand nach S5:** 216 Tests / 22 Dateien grün, `tsc -b` 0, oxlint 0/0 (82 Dateien), `antd lint ./src` 0
(50 Dateien), `vite build` grün. `chat.page.tsx` **588** Zeilen (vorher 594; der Plan nannte 602) — unter der
600er-Grenze, der Scroll-Anteil ist draußen.

---

## Inkrement 4 UI — N1 Review-Fixes (Dev-Bau 2026-11-08, Freigabe Jon · Stand `3627ccc5`)

### N1-1 BUG 1 — die Adresse öffnet ihre Gruppe immer

**Gebaut:** `src/app/app.sider.tsx` — neue interne Lesart `namedGroupOf(projects, activeKey)` (eine Antwort auf
„welche Gruppe nennt die Adresse?"), von `openGroupsOf` **und** vom Render benutzt. Effektive offenen Gruppen
= `touched ?? openGroupsOf(…)` **∪** Gruppe der Adresse; die Union begrenzt die **Voreinstellung**, nie die
Adresse-Gruppe (R-WP-44 „steht darüber hinaus immer offen", R-WP-1 „Markierung und URL einig" — sichtbar
einig). Kein neuer Zustand, keine Schleife: `touched` ändert sich einmal, der Rest ist Rechnung.

**Bewusst hingenommen (Kommentar an der Union, damit niemand es „repariert"):** den Titel der Gruppe anzuklicken,
die die Adresse nennt, klappt sie nicht zu — sie schnappt offen zurück. Das ist die Doc-Entscheidung „immer
offen" und kein zweiter Defekt. `touched`-Einträge verschwundener Projekte ignoriert das `Menu` (Kinder fehlen),
ebenfalls hingenommen.

**Test (der einzige neue des Deltas):** `test/app/app.sider.test.tsx
theAddressGroupOpensEvenAfterAToggle` (R-WP-44, R-WP-1, UC-WP-78). Der Wechsel der Adresse kommt per `rerender`
und **nicht** per Baum-Klick — genau das, was Projektliste, Browser-Zurück und Deep-Link dem Rahmen geben. Der
Zweig war vorher haltlos: `theSiderMarksTheEntryTheAddressNames` rendert durchgehend mit `touched === null`.

| Mutation | Getötete Tests |
|---|---|
| Union gestrichen (`openKeys={openKeys}`, nur `touched`) | **genau** `theAddressGroupOpensEvenAfterAToggle` (`test/app`: 1 failed / 5 passed von 6 Dateien) |

**Stand nach N1-1:** 217 Tests / 22 Dateien grün, `tsc -b` 0, oxlint 0/0 (82 Dateien), `antd lint ./src` 0
(50 Dateien).

### N1-2 BUG 2 (Kommentare) + LOW a/b/c

**BUG 2 — zwei Kommentare auf den gebauten Stand (kein Code-Wechsel):**
- `src/chat/chat.log.tsx` — „bubble on the right / card on the left" ist seit S4 falsch. Neu am Blatt
  (`styles.css:329-347`, beide Stimmen `justify-self: stretch`): beide füllen das Maß, die eigene Eingabe als
  blaue Blase (`--user`), eine Antwort als Karte auf `--surface` mit eigener Einfassung — **Farbe und
  Einfassung** unterscheiden, keine Seite. Der Satz „the log draws no line of its own between two rows"
  **bleibt** stehen (lebende Behauptung, gehalten von `test/shared/log.parts.ts`); der Delta-Plan ließ ihn
  fallen — gestrichen hätte er den Beweis der Trennlinien stillschweigend seiner Klammer beraubt.
- `src/chat/chat.timeline.tsx` (`DelegationEntry`) — „The basis' loading dot is the indicator" ist seit S3
  falsch. Neu: der Punkt des offenen Eintrags ist **unser** `WorkingIndicator`, den `timelineItems` als `icon`
  übergibt; der Lade-Glyphe der Basis wäre ein zweites Zeichen derselben Antwort (R-WP-46).

**LOW a — die drei Kachel-Einzeiler unterscheiden jetzt wirklich Zustände** (je **eine** Assertion, kein neuer
Test). Zwei DOM-Fallen bestimmen die Schreibweise, beide gemessen: `Spin` legt `classNames.indicator` auf das
**geklonte** Indikator-Element (`node_modules/antd/es/spin/Indicator/index.js:17-24`, Wurzel
`spin/index.js:97-98,122-131`) ⇒ Klasse sitzt **unter** dem TestId-Knoten, `toHaveClass` am Knoten wäre grün
trotz Defekt; und der `agent-status`-Wrapper trägt `aria-hidden` ⇒ keine Rollen-Query findet darin etwas,
`getByTestId`/`querySelector` sind der Weg. Klassen sind unsere, kein `.ant-*`.

| Mutation an `chat.status.tsx` (`AgentStatus`) | Getötete Tests |
|---|---|
| IDLE malt den Spin statt des Kreises | **genau** `teamTilesShowContextAndStatus` |
| COMPACTING verliert die Frage-Farbe (`tone` weg) | **genau** `compactShowsItsOwnState` |
| ERROR malt den Idle-Kreis statt des gefüllten Zeichens | **genau** `clearStaysOfferedAfterAFailure` |

(`test/chat`: je 1 failed / 117 passed von 118 — keine Nebenwirkung auf die Timeline-Beweise.)

**LOW b — `test/app/app.paths.test.ts`:** der beim S1-Umbau doppelt eingefügte Block (wörtlich dieselben
`expect`-Aufrufe wie zwei Zeilen darüber) ist ersatzlos raus; keine Behauptung fehlt, kein Name geändert.

**LOW c — `test/shared/sheets.ts::declares()`:** Semantik jetzt am Helper (Substring auf `selector` + genau
**ein** Leerzeichen + `{`; zwei Leerzeichen, Umbruch vor der Klammer und Gruppen-Selektoren werden nicht
gefunden, `index.css` wird nie gelesen — ein `false` kann grün bleiben, obwohl die Regel in anderer
Schreibweise steht), **Verhalten unverändert** wie im Delta vorgesehen. Empfehlung des Reviews
(whitespace-normalisierte Suche) ist damit **nicht** gebaut — wer sie will, ändert den Helper und meldet die
Aufrufer.

**Plan-Fund — die Aufrufer-Liste des Deltas:** N1.3(c) nennt `chat.page.test.tsx` (UC-WP-65) und
`chat.log.test.tsx`. Gemessen benutzt `chat.log.test.tsx` den Helper **nicht**; die Aufrufer der geteilten
Funktion sind `chat.page.test.tsx:1036,1763`, `test/shared/code.test.tsx:23`, `test/shared/theme.test.ts:47` —
diese Liste steht jetzt am Helper. **Daneben liegengeblieben (unangetastet, außerhalb des Deltas):**
`test/chat/chat.tiles.test.tsx:285` besitzt eine **eigene** Kopie von `declarations`/`declares` mit derselben
Schreibweisen-Grenze; wer die Suche normalisiert, muss **beide** erwischen.

**Stand nach N1-2:** 217 Tests / 22 Dateien grün, `tsc -b` 0, oxlint 0/0 (82 Dateien), `antd lint ./src` 0
(50 Dateien), `vite build` grün. Kommentare und Testverstärkungen, kein neues Verhalten, keine neue UC-ID —
R-WP-44/R-WP-1 zieht Jon selbst.

---

## Inkrement 6 — Nacharbeit Browser-Abnahme Inc 4 (Dev-Bau 2026-11-08, Branch `web-factory-4`)

Commits: `cb600bd0` docs · `91d7ecbb` 1 Blasen-Höhe · `f1de3b97` 2 Band-Spalt · `fddad38c` 3 Kopf-Menü ·
`df0b5691` 4 Resend am Send-Knopf · `7ce63a90` 5 Agent-Status-Maß · S6 Kante im Falz · S7 nichts getan.
Teststand je Schritt: 217 · 217 · 215 · **214** · 214 · 214 (22 Dateien). Je Commit `npx tsc -b` 0,
`npx oxlint` 0/0 (82 Dateien), `npx antd lint ./src` 0 (50 Dateien), `npx vitest run` grün.
**`npm run check` existiert nicht**, `npm run build` ist kaputt ⇒ `npx vite build`.

### Messprotokoll Sider (Schritt 6) — M1 jsdom, M2/M2b Chrome headless 1440×900

Ausgang: der Schmal-Fall war durch `theFrameFoldsTheTreeAndTheHandleOfTheBandReachesItAgain` belegt, der
**Breit-Fall nie**. Pauls Befund: „der Griff bewegt nichts Sichtbares".

- **M1 (jsdom, `matchMedia`-Stub antwortet `matches: false` ⇒ Breit-Fall; Wegwerfsonde
  `test/app/m1.probe.test.tsx`, vor dem Commit gelöscht).** Vor dem Klick `style.width` `250px`,
  `flex: 0 0 250px`. **Ein** Klick auf `sider-toggle` ⇒ Klassen `ant-layout-sider-collapsed` **und**
  `ant-layout-sider-zero-width`, inline `0px`, `flex: 0 0 0px`, `minWidth: 0`, Griffname
  „Show the project tree"; zweiter Klick zurück auf `250px`. ⇒ **Plan-Zweig „`width` bleibt 250px"
  widerlegt**, kein Verdrahtungsfehler in `app.shell.tsx`/`app.sider.tsx`.
- **M2 (Chrome headless, frisches `dist`, Stub-API).** Nach einem Klick: Sider-Box **1 px** breit,
  Baum-Box **0 px**, `elementFromPoint(120, 450)` = `chat-log`. ⇒ „der Baum bleibt sichtbar" **widerlegt**;
  `overflow: hidden` ist **nicht** nötig und wurde **nicht** gebaut. Was bleibt: `.app-sider` misst
  `overflow: visible` und `border-right: 1px rgb(229,231,235)` ⇒ **1-px-Sliver an der Brettkante** — die
  einzige wahre Ursache des Befunds.
- **M2b (gemessen, von Jon am 2026-11-08 **nicht** gebaut).** 1440 px gefaltet → Viewport 900 px → zurück
  1440 px: Sider wieder `250px`, `-collapsed` weg, Baum `249×140` sichtbar. Die Basis schreibt die
  Nutzer-Wahl also bei jedem Media-Wechsel um (`layout/Sider.js` `responsiveHandlerRef` →
  `onCollapse(mql.matches, 'responsive')`, läuft beim Mount **und** bei jedem `change`; `CollapseType` ist
  aus `antd` **nicht** exportiert, `trigger={null}` nimmt beide Griffe, `lg` = `991.98px`).
  **⏳ Annahme an Jon:** R-WP-44 verlangt nur „unter dem Breakpoint zu", nichts über die Rückkehr —
  das Verhalten ist offen, kein Defekt. Der erwogene Fix (ignoriere `'responsive'`-Schreibvorgänge,
  sobald der Griff betätigt wurde) verletzt genau die Auto-Zu-Regel und ist **zurückgenommen**
  (`src/app/app.sider.tsx` steht auf HEAD).
- **Nebenbefunde desselben Laufs** (bestätigen 1–5 im Browser): `headerBottom 64 == mainTop 64`; Blasen
  `user h=60` / `agent h=62` bei `w=1158`; Log `x 250, w 1190, h 574`; `.ant-spin-dot` 20×20 bei
  `font-size: 16px`; `.agent-status-idle` 16×16; Composer-Knöpfe `["Send","Stop"]`,
  `sendDisabled false`; Kacheln „working"/„ready".
- **M3 — nicht gegen einen echten Turn gemessen.** Belegt ist nur die Render-Seite (Stub-Roster
  `state: 'WORKING'` ⇒ „working" + 16-px-Drehzeichen). Die Draht-Seite bleibt aus dem Code belegt
  (`ProjectRuntime.java` `tileOf`/`agents`-Sendung, `AgentTiles.stateOf` Vorrang
  COMPACTING > WORKING > ERROR > IDLE) und aus den `agents`-Events eines früheren Diagnose-Laufs —
  **nicht** neu nachgemessen. Ein echter Turn braucht `mvn clean install` (Core + Fabrik) und einen Lauf
  auf 8099; von Jon offen.

### Schritt 6 — gebaut: nur die Kante im Falz

`src/styles.css` `.app-sider.ant-layout-sider-zero-width { border-right: 0 }` (direkt an der bestehenden
`.app-sider`-Regel). **Kein Test** — jsdom malt keine Kante; die Blatt-Erklärung sitzt am Block. Look-
Abnahme: gefalteter Baum ⇒ nichts als die Seite reicht bis an den Rand.

### Schritt 7 — Größenwacht: nichts getan (Plan-Bedingung „≤ 600")

`src/chat/chat.page.tsx` nach `df0b5691` (Schritt 4): **593** Zeilen (davor 588; der Plan nannte 589, die
Doku 602). ≤ 600 ⇒ kein Slice, kein `chat.page.send.ts`, keine `chat.page.events.ts`. Bei der nächsten
Änderung an der Datei ist die Zahl erneut zu messen.

### Werkzeug-Befund (Harness, nicht Repo)

Der eingeblendete Plan-Kontext dieser Sitzung meldete Fortschritt, den `git log`/`git status` widerlegten
(behauptete Commits, erfundene Testzahl, ein nicht existierender Test). Regel für die nächste Runde: vor
dem ersten Edit `git log --oneline -8` + `git status --porcelain` + gezieltes `grep` gegen die betroffene
Datei; ein Hash, der in `git log` fehlt, widerlegt den ganzen Block — keine Zeile daraus in Dateien oder
Commits übernehmen.

## Inkrement 4 UI — N1 Nacharbeit (Review REJECTED, Dev-Bau 2026-11-08)

Delta über `6084b449`: **BUG-1** (Wiederholung nach einem Stop) + LOW 1–3. Plan: Runde 6 — N1.

### BUG-1 — die Wiederholung fragte den Verlauf, die Doc verlangt den Schirm

**Ursache.** Der Stop liest den Verlauf nicht neu (`chat.page.tsx` `done`-Zweig, D-16, gepinnt durch
`stopKeepsTheShownLines`), die Wiederholung las aber nur `messages`. Nach Mount `[Q1]` → Q2 senden → Stop →
`done` stand auf dem Schirm Q1 + **Pending Q2**, im Verlauf nur Q1 ⇒ der leere Klick sandte Q1.
R-WP-30/UC-WP-57 GIVEN 1 sagen „letzte **sichtbare** Nutzer-Zeile … bestätigt oder noch ausstehend".

**Gebaut (D1–D3).** Neuer reiner Helfer `lastVisibleUserText(history, pending)` in `src/chat/chat.pending.ts`
(Ordnungsregel = **Platz im Log**: `chat.log.tsx` zeichnet Verlauf dann Pending ⇒ eine ausstehende Zeile
gewinnt; bewusst nicht die Draht-Chronologie — steht im Helfer-Kommentar). `chat.page.tsx` fragt sie in
`submit` mit `pendingLines.current` (ref, nicht State) und verliert `lastUserText`; Backend-Pfad des
Kommentars berichtigt: `factory-backend/src/main/java/**org/sterl/open/aifactory/chat/**/ProjectRuntime.java:519-541`
(`history()` parst den Absender, `submit:373` setzt das Präfix wieder ⇒ kein Doppelpräfix). Kein Read, keine
Dateilesung, kein Marker-Agriff.

**Rot vor grün (Protokoll).** Test `emptySendResendsTheStoppedUserMessage` zuerst geschrieben, Produktionscode
unverändert ⇒ **rot** wie erwartet: `chat.page.test.tsx:1661` — „the stopped input, never the older history
line: expected [stopped, stopped], received `[mach die zweiten Dateien fertig, wo liegen die Dateien?]`". Nach
dem Helferwechsel grün, 215/215.

| Mutation am Helfer | Getötete Tests |
|---|---|
| Pending-Zweig gestrichen (nur Verlauf) | **genau** `emptySendResendsTheStoppedUserMessage` (1 failed / 117 passed in `test/chat`) |
| Verlaufszweig gestrichen (nur Pending) | **genau** `emptySendResendsTheLastUserMessage` (1 failed / 117 passed) |

Beide Zweige sind also gebraucht; keiner ist Dekoration. Kein Unit-Test auf den Helfer selbst (beide Zweige
sind verhaltensseitig bewiesen, AGENTS: Redundanz ist LOW).

### LOW 1 — GIVEN-Nummern des UC-WP-74 (gemessen **7** Stellen, Review nannte 8)

`docs/chat.md:449-458`: 1 leer aktiv · 2 Turn läuft, tippen + senden ⇒ Queue · 3 Turn läuft, leerer Klick ⇒
nichts · 4 Auto-Grow · 5 Refocus. Gezogen: `chat.composer.tsx:9` 3→4, `:14,:55,:60,:88` 4→5,
`chat.composer.test.tsx:179` 3→4, `:26` 4→5. Richtig und unangetastet: `chat.composer.tsx:45`,
`chat.page.test.tsx:510`, `chat.composer.test.tsx:143` (je GIVEN 1). **Achte Stelle außerhalb des
Commit-Umfangs:** `docs/open-points.md:148` zitiert „UC-WP-74 GIVEN 4" für den Refocus ⇒ **Jons Seite**.
Messgröße grep, kein Test.

### LOW 2 — die Leer-Enter-Probe war blind, jetzt ist sie es nicht mehr

`enterSendsBothLinesInOneTurn` fragte Enter auf leerem Feld **nach** einem Send ohne `done`: dort steht
`runningTurn` ⇒ `busy` = true, der leere Weg wäre also auch **ohne** den Composer-Guard nichts gegangen. Jetzt
steht dieselbe Probe **vor** der ersten Eingabe (kein Turn läuft ⇒ nur der Guard kann der Grund sein) mit
`expect(fireEvent.keyDown(…)).toBe(false)` (kein Default) und `POST` 0; der hintere Rest bleibt, sein
Kommentar nennt jetzt den **laufenden Turn** als Hinderungsgrund (UC-WP-57 GIVEN 2). Kein neuer Test, keiner
gelöscht. Der Guard selbst bleibt zusätzlich in `chat.composer.test.tsx` `emptyEnterSendsNothing` bewiesen.

### LOW 3 — Sprachregel im Blatt

Übersetzt: `.chat-composer` (war `:622-626`), `.chat-waiting` (`:660-664`), `.chat-waiting-text` (`:680`) —
Selektoren und Werte unverändert, dabei „die beiden anderen Knöpfe" ⇒ **ein** Secondary-Control (Stop)
berichtigt. Der vom Review genannte **Sliver**-Kommentar war bereits Englisch, trifft also nicht.
**Zusätzlich gebaut (von mir in Runde 6 selbst geschrieben, `f1de3b97`):** der `.app-header`-Kommentar
(`:20`) war halb Deutsch ⇒ Englisch. **Bewusst nicht gebaut (Scope):** weitere deutsche Kommentare im Blatt
`:5`, `:212-213`, `:240`, `:953`, `:1029` — ältere Runden, kein Befund, nicht committet; `content.ts:204` ist
ein **Zitat** aus der deutschen Doku, keine Kommentar-Prosa. Akzeptanz- grep (`nicht|damit|weil` in
`src/styles.css`) ist leer.

**Stand nach dem Delta:** 215 Tests / 22 Dateien grün (214 + der neue Stop-Fall), `npx tsc -b` 0, `npx oxlint`
0/0 (82 Dateien), `npx antd lint ./src` 0 (50 Dateien), Eclipse-Bau `factory-ui` ohne Meldung. Browser-
Stichprobe (nach einem Abbruch leer „Send" ⇒ dieselbe Frage nochmal) steht bei Paul/Jon aus.

## Inkrement 4 UI — der Datei-Bereich wird der Drawer der Basis (Dev-Bau 2026-11-08, Branch `web-factory-4` · Stand `44298477`)

**Gebaut:** `ProjectFiles` (`src/project/project.files.tsx:225-266`) zeigt den Datei-Bereich als `Drawer`
der Basis — `getContainer={false}` (inline in `.chat-workspace`), `mask={false}`, `placement="right"`,
`size="var(--files-wide)"`, `zIndex={2}`, `destroyOnHidden`, `onClose={onToggle}`. Der eigene
`{open && …}`-Guard und `button.files-collapse` sind weg: Schließen ist der Kopf der Basis (X und Esc,
beide durch `onToggle` = Adresse). Blatt: drei Tab-Lagen und die Breiten-Klemme über `:has()`
(`src/styles.css:785-805`). Tests 216 / 22 Dateien (Esc-Fall neu).

### Abweichung 1 — Plan D6 vermisst eine Schicht, die es nicht gibt

D6 stützt sich auf „`.view` `z-index: 1` (`styles.css:732-739`)". Gemessen: `.view` (`:103`) und
`.chat-workspace > .view` (`:733`) führen **kein** `z-index`; der Wert 1 bei `:971` gehört zu
`.chat-insight`. Darum ist nichts ergänzt worden — der Drawer-Root (inline 2) liegt über der statischen
Zeile, der Tab (Blatt 3) über dem Drawer. Die gewünschte Reihenfolge ist da, ohne eine erfundene Schicht.

### Abweichung 2 — Scroll-Herr ist der Drawer, nicht die Fläche

§4 Schritt 2 schrieb der Fläche `overflow-y: auto` zu. Gemessen setzt die Basis auf ihren Body
`flex:1; min-height:0; overflow:auto` (`antd/es/drawer/style/index.js:192-197`); eine Fläche, die ihre
Höhe aus dem Inhalt holt, scrollt also **im Body** und die eigene Rolle wäre toter Code. Entscheid:
`min-height: 100%` an der Fläche (Oberfläche und Kante die ganze Höhe gemalt), `overflow-y` **weg**.
Sichtbar wie vorher: ein Scrollbalken über den ganzen Bereich.

### Abweichung 3 — drei Klassen statt zwei, weil die Basis später injiziert

D10 rechnete `.chat-workspace .files-drawer-body` (0,2,0) für das Polster. Gemessen verliert das: die
Basis schreibt ihr Polster über `:where(.css-hash).ant-drawer .ant-drawer-body` — ebenfalls (0,2,0), aber
**nach** unserem Blatt ⇒ Gleichstand ist Verlust. Getragen haben darum
`.chat-workspace .files-drawer .files-drawer-wrapper|…-body` (0,3,0); dasselbe gilt für die Wrapper-Klemme
gegen `max-width: 100vw` der Basis.

### Abweichung 4 — Bewegungsdauer bleibt ein Literal

Der Plan wollte die Tab-Dauer an die Kantenbewegung angleichen; `--ant-motion-duration-slow` ist im Blatt
**nicht lesbar** — die Basis legt ihre `--ant-*`-Variablen auf eine generierte Klasse (`.css-var-_r_0_`),
nicht auf `:root`. Darum `transition: right 0.3s ease-out` mit Kommentar; die Übergangsphase misst die
Browser-Abnahme.

### Benannte Abweichung 5 — der Nachbar-Nachweis ist raus (Plan D5, gegen Regel 15 `AGENTS-DEV.md`)

`previousElementSibling` und die `+`-Regel sind weg; die Lage liest der Tab über
`.chat-workspace:has(.files-panel[data-wide='true']) .files-tab`. Ein Geschwister-Beweis wäre nach dem
Umbau **zufällig** grün — Tab und Fläche sind seit dem Drawer keine Nachbarn mehr. `npx vite build` und
`grep -c ':has(' dist/assets/*.css` = 1 (beide Selektoren unverändert im Auslieferungsgut, kein
Transformer konfiguriert) ⇒ der vorentschiedene Fallback (nur eine Breite) war nicht nötig.

### BLOCKER behoben — jsdom kaskadiert die Basis in jede Rollen-Abfrage

Nicht im Plan und nicht in `src/`: `test/shared/setup.ts` räumt die Stil-Marken der Basis
(`style[data-css-hash]`) per `MutationObserver` aus dem Dokument. Maß: mit dem Drawer hält das Dokument
983 Regeln statt 891 (+18 KB, schon im geschlossenen Zustand), die **erste** `getAllByRole` einer Datei
läuft 494 ms statt 49 ms und wird nach jeder DOM-Änderung neu bezahlt ⇒ `project.files.test.tsx`
7,22 s (HEAD) → **48,6 s**, ein Fall über die 5 s des Test-Timeouts. Kein Test liest einen berechneten
Stil (jsdom antwortet `canvastext`), die Mechanik-Tests lesen unser Blatt als Text — die Stil-Marken
dürfen außerhalb des Dokuments stehen. Danach **216 Tests / 22 Dateien in 8,2 s** (ganze Suite).
Alternative „Timeout erhöhen" verworfen: sie behält die Verlangsamung, beseitigt sie nicht.

**Stand nach dem Delta:** 216 Tests / 22 Dateien grün, `npx tsc -b` 0, `npx oxlint` 0/0 (82 Dateien),
`npx antd lint ./src` 0 (50 Dateien), `npx vite build` 1,01 s. **Browser-Abnahme steht aus** (Plan §7:
Tab bündig oben · Fläche liegt über dem Rand, Chat sichtbar und tipbar · Tab reitet die Kante auch breit ·
schließen per X/Esc/Tab/Sider ohne Scroll-Sprung · Fokus zurück zum Tab · Inhalt fährt mit raus) — dieser
Build hat kein Browser-Werkzeug. Die Doku-Klammer „Portal `(document.body)`" hat Jon auf inline gezogen,
`aria-modal={false}` ist Jons Freigabe.

## Inkrement 4 UI — N1 Delta: die rechte Kante trägt genau ein Overlay (Dev-Bau 2026-11-09, Branch `web-factory-4` · Stand `43d18eea`)

**Protokoll-Lage, offengelegt:** Der Review-Block „Runde 6 (`df2297fd..43d18eea`)" stand **nicht mehr** in
`overview.md` — die Datei war um 23:43 mit dem N1-Plan überschrieben worden (`grep` fand den Titel nur noch in
der Übertragungs-Anweisung selbst, `overview.md:125`). Die Befunde unten sind darum **rekonstruiert aus §1/§2
des N1-Plans** (Jons Entscheidungstabelle), **nicht wörtlich**; eigene Verdikte sind keine erfunden.

### Jons Entscheidungen (Punkt für Punkt, aus dem Plan)

| Punkt | Entscheidung | Umsetzung |
|---|---|---|
| BUG-1 — Drawer (`zIndex={2}`) deckt `.chat-insight` (`z-index: 1`) zu und schluckt seine Klicks | **gegenseitiger Ausschluss** in beide Richtungen; kein zweiter „offen"-Besitzer, Insight bleibt ohne Adresspfad; Zahlen bleiben und bekommen **einen benannten Ort**; zwei neue Verhaltens-Tests | `src/chat/chat.edge.ts` (neu) + `closeFiles` in `app.routes.tsx`; Schicht-Liste über `.chat-workspace` |
| BUG-2 — Kommentar `app.routes.tsx:145-147` sagt „column … instead of covering it" | Satz auf die Overlay-Wirklichkeit umschreiben | erledigt (drei Zeilen, Adresse bleibt Besitzer) |
| `aria-modal={false}` | **bestätigt**, bleibt; kurz vermerken | bleibt; dieser Abschnitt ist der Vermerk |
| LOW-1 — globaler Style-Entferner `test/shared/setup.ts` | nachgemessen, **andere Lage als vermutet**, kein Fix | siehe §L1 unten |
| LOW-2/3/4 | mitgenommen | Test-Kommentar, Schicht-Liste, `.anticon`-Assertion weg |
| AGENTS-DEV | 2–3 Zeilen im Factory-UI-Block, Datei zum Commit freigegeben | drei Kurzregeln (siehe Commit 2) |
| Mutations-Check | nicht angefordert | — |

**L1 (LOW-1), Messwert statt Fix:** Der Entferner bewegt ausschließlich Blätter **mit** `data-css-hash`
(`setup.ts:60-61`) — das Marker-Attribut der Cssinjs-Cache der Basis. Unser Blatt trägt es nicht, und
Blatt-Mechanik wird per `?raw`-Text gelesen, nie über `getComputedStyle` ⇒ ein „Kaskade wäre still grün" ist
nicht möglich. Einzige echte Grenze: **kein Test prüft Basis-Regeln über berechnete Stilarten** — jetzt Regel
in `AGENTS-DEV.md`, `test/shared/setup.ts` bleibt unverändert.

### Der teure Befund: die Pass-Reihenfolge, nicht die Schicht

Der vom Plan vorgegebene guarded-Reset `if (filesOpen && insight !== null) setInsight(null)` frisst den Insight,
den der Leser **gerade** verlangt hat. Gemessen (temporäre Probe, vor Commit entfernt): React wendet den
eigenen State der View an, **bevor** die neue Adresse sie erreicht — im Pass, der den frisch gesetzten Namen
trägt, liest `filesOpen` noch `true`. Bestätigt: derselbe Heading-Knoten (`isSameNode`), `historyReads() === 1`
⇒ **kein** Remount, der Chat blieb gemountet (UC-WP-70 G2 unberührt); Fehlerbild war
`Unable to find an element by: [data-testid="insight-panel"]`.

**Verworfen, jeweils gemessen:** `flushSync(() => navigate(…))` — dreht den Pass nicht, und `flushSync` ist in
diesem Stand **nicht** aus `react` exportiert (react 19.3.1; nur `react-dom` hat es). Ref-Marker über die
Pässe — `oxlint`-Regel `react(refs)` verbietet Ref-Zugriff im Render (4 warnings). Plan-Fallback (Insight-State
in den Rahmen heben) — behält dieselbe Reihenfolge-Konkurrenz und hätte vier grüne Router-freie
`chat.insight.test.tsx`-Fälle umgebaut. **Getragen hat:** Ask und Faktenlage in **einem** State-Objekt
(`{ name, edgeAsked }`), consumiert in dem Pass, in dem die Kante ankommt; `shown` ist `null`, solange die
Adresse die Kante hält — beide Overlays in einem Frame sind damit **strukturell** unmöglich, nicht über eine
Zahl verhindert (`oxlint` meldet `set-state-in-render` nicht, `set-state-in-effect` bleibt verboten).

### Abweichung — `chat.page.tsx` war 634 Zeilen, nicht ~593

Die Plan-Prognose (593) traf nicht; AGENTS.md (`:10-12`) macht 600 zur harten Grenze, und §Schritt 6 erklärt
die Teilung für diesen Fall zum Teil des Deltas. Also: Regel und State nach `src/chat/chat.edge.ts` (61 Zeilen,
eine Zuständigkeit) — **597 Zeilen** danach, keine Verhaltensänderung, keine neue Prop.

### Abweichung — zwei Plan-Lokalitäten

LOW-4: die redundante `.anticon-close`-Assertion lebte in `theFilesTabOpensTheSlideOut`
(`test/project/project.files.test.tsx:355`), **nicht** in `theViewerCanAlwaysBeClosed` — dort entfernt,
`getByTestId('files-close')` bleibt als Existenz-Beweis. Und §6.3 („kein Push ins Leere") ist **verstärkt**:
die Adresse allein kann einen Push auf sich selbst nicht sehen, also trägt `test/shared/current.path.tsx`
zusätzlich `data-entry={key}` (Router-Key des Eintrags) — der Test behauptet jetzt Adresse **und**
History-Eintrag. Der Fall bleibt in der zweiten Hälfte von `openingTheInsightClosesTheFilesAddress` (kein
dritter Test ⇒ Stand bleibt 218).

**Stand nach dem Delta:** **218 Tests / 22 Dateien** grün, `npx tsc -b` 0, `npx oxlint` 0/0 (83 Dateien),
`npx antd lint ./src` 0 (51 Dateien), `npx vite build` ok, `grep -c ':has(' dist/assets/*.css` = 1,
`wc -l` = `chat.page.tsx` 597 · `chat.edge.ts` 61 · `project.files.tsx` 402 · `app.routes.tsx` 186.
Kommentar-Korrekturen dieses Deltas: `app.routes.tsx` (Zeile + JavaDoc-Ende), `chat.page.tsx` (Insight-Absatz),
`styles.css` (Insight-Kommentar, Tab-Kommentar), `project.files.tsx` (nannte ein `z-index: 1` beim Chat, das es
nicht gibt ⇒ Verweis auf die Liste). **Browser-Abnahme §7 (6 Punkte) steht weiterhin bei Jon** — dieser Build
hat kein Browser-Werkzeug; neu daran: Insight→Tab, Tab zu (kein Insight-Rückkehrer), `/files`→Kachel,
**Zurück/Weiter** über `/files`-Adressen (nie zwei Overlays), Tab über dem Insight erreichbar (3 > 1).


## R-WP-48 — je Turn genau ein Endereignis (Review 2026-11-09, Scope `430343f2` + `e72c6c76`) — Verdict **REJECTED** → N1

Grün gefahren: Backend 232, UI 224. Drei Befunde, alle von Review; Docs von Jon geschärft (R-WP-48/UC-WP-83/84), Delta-Plan folgt.

**B1 (Verhalten, Docs↔Code): UC-WP-83 GIVEN 4 war falsch gefasst/gebaut.** `fail()` (`ProjectRuntime.java:459-462`) publiziert `ChatEvent.Failed` **unbedingt** — auch für einen einreihenden Submit, dessen eigene Config-Lesung scheitert: ein `error` geht auf den Draht, während der besitzende Turn läuft; `chat.page.tsx:352-363` behandelt jedes `error` als Turn-Ende und reißt die Live-Sicht des laufenden Turns weg (für `RuntimeException` pre-existing, für `Error` neu durch den `Throwable`-Catch). Beleg: `aQueuedSubmitThatFailsOwnsNothingOfTheRunningTurn` (`TurnFailureTest.java:213-256`) wartet auf die `ROLE_ERROR`-Zeile, die nur `fail()` schreibt. **Jons Entscheidung (2026-11-09, autonom):** das `error`-Ereignis gehört dem eröffneten Turn — scheitert ein einreihender Submit, bleiben die Verlaufszeilen, aber **kein** Endereignis geht auf den Draht (Publish nur bei `ranTurn`); Doc entsprechend geschärft.

**B2 (fehlender Test): UC-WP-83 GIVEN 3 „genau ein `done`" ist auf dem Draht ungedeckt.** `stopIsNoFailure` prüft nur den Verlauf; D4s Begründung („gleicher Zweig") trägt für GIVEN 2, nicht für den CANCELED-Zweig (`:431-432`). Delta: Draht-Assertion in `stopIsNoFailure` nachtragen (benannte Abweichung von D4 — der Schluss war falsch, nicht die Regel).

**B3 (Race): der Roster-Read aus `onOpen` hat keine Lesefolge.** Eine `/agents`-Antwort (WORKING) kann nach dem `agents`-Endschnappschuss (IDLE) eintreffen und ihn überschreiben → Kachel für immer „arbeitet". Der Verlauf hat den Schutz (`readSeq`/`appliedRead`, `chat.page.tsx:171-173`), die Kacheln nicht; der Plan verwies denselben Grund als Verwerfungsgrund für den Doppel-Read. **Jons Entscheidung:** Lesefolge-Idiom auf die Kacheln legen (frischester Stand gewinnt); Doc-Satz in R-WP-48 nachgezogen, UC-WP-84 um GIVEN 3 ergänzt.

**Abweichung (Jons, nicht Devs):** `e72c6c76` committet `docs/chat.md` (+35/−2) mit fremden Flips (R-WP-47/UC-WP-82 ✅ aus der A7-Runde), weil Jon Dev die Datei ausdrücklich mitgab, während der Plan „kein `docs/**`" sagte. Dokumentiert, nicht rückgängig.

**Instruction gap:** `AGENTS-DEV.md` braucht die nvm-/PATH-Zeile für `pnpm`/Node ≥22 (hier fehlte `pnpm`, node war v18) — im Delta nachziehen.

**Hinweis:** Findings stehen zunächst in `overview.md` (nur Plan-Tools schreiben dort) — Übertrag hierher vor dem nächsten Plan-Schreibvorgang.

## API-Fehler (Review 2026-11-09, Scope `e6ca3573`..`b78e59d1`) — Verdict **CONCERNS** (kein BUG) → Delta N1

Selbst gemessen: Backend 274/0 (`missCount=1`, 47 s), UI 267/29 grün, tsc 0, oxlint 0/0, lintDocs nur
`UNBELEGT UC-WP-91` (so gewollt, M1/M2). M2 hier nachgemessen (`mvn clean test` erzeugt `server-api.ts`
neu, `git status` unverändert). Alle 5 Inkremente auf der benannten Variante, D9–D23 nicht überschritten.

- **S1 (Suspicion):** `ApiFormErrors` zeigt nur `ApiError.message` (= erste Violation). Antwort nennt zwei
  Felder, eines davon nicht umfasst ⇒ dessen Grund erscheint nirgends — gegen R-WP-56/R8. Heute
  unerreichbar (jede Server-Regel liegt auf einem umfassten Feld), aber R8 existiert genau dafür.
  **Mutation `every`→`some` (`api.error.tsx:57`) überlebt** — der eine Mutation-Nachweis dieser Serie,
  zusammen mit einem Zwei-Felder-Test. Delta empfohlen: Nachrichten der *nicht* umfassten Einträge zeigen.
- **L1:** `.api-field { margin-bottom: 0 }` verliert gegen das zur Laufzeit injizierte `.ant-form-item`
  (gleiche Spezifität, später) bzw. `.ant-form-inline .ant-form-item` (0,2,0) ⇒ doppelte Feldabstände.
  Fix wie D8: `.api-field.ant-form-item` (+ Inline-Scope). Nur am Browser messbar (R1).
- **L2:** Dedupe `ApiExceptionHandler:64-71` unerreichbar (kein DTO kann 2 Violations je Feld) ⇒
  einfacher: `getAllErrors()` 1:1.
- **Doc-Kleinigkeit:** `docs/api-errors.md:51` Beispiel-Property `jsonBody` — real heißt sie `extraBody`.
- **Doku-Stand (kein Code-Fehler):** `api-errors.md` R-WP-54/55/56 + UC-WP-91/92/93 noch ❌,
  `open-points.md` C-8 noch ❓ — ⏳/✅-Texte liegen in `peon-plan/api-error-notes.md`, Übernahme Jon.
- **Skill-Lücke:** `.agents/skills/api-types-and-error-widget/SKILL.md` (untracked) trägt die AntD-6-Fakten
  noch nicht. Nebenbefund: die episodische Edit-Nachlese-Regel in `AGENTS-DEV.md` gehört eher in die
  Plan-Datei als in die Agent-Memory.
- Architektur: ein Pfad je Verantwortung, `shared` ohne Component, ein Kontext (missCount=1), keine Datei
  >600 (`config.page.tsx` 437, `config.agents.tsx` 388). Pre-existing, nicht blockierend: der feste
  „gitUrl"-Text im Helper antwortet auch auf `/api/settings` (vorher schon so).
