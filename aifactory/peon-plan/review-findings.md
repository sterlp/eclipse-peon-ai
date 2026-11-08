# Review-Befunde — AntD-Umbau (Verdicht aus den Review-Runden)

Regel: Befunde stehen **hier**, nie nur in `overview.md` — der nächste Plan überschreibt die Plan-Datei.
Maßstab ist immer das Feature-Doc; „ein Doc erwähnt einen Fall nicht" ist nie eine Erlaubnis für den
Gegenteil-Fall. Review repariert nichts, Jon entscheidet.

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
