# Open Points — AI Factory

Status: ❓ offen · ⏳ selbst entschieden (Rückversicherung steht aus) · 🔒 geklärt.
Durchgangslager: jeder Punkt wandert am Zyklusende in ein Feature-Doc oder entfällt.

## Runde 6 — Browser-Abnahme Inc 4 (2026-11-08, abends, Screenshot `17.31.29`)

Gemessen (Dev, 2026-11-08): kein Stale-Build — Pauls Screenshot zeigt die **Inc-4-UI** (IDE-Lauf auf
8080, Bundle `target/classes/static/ui` 17:09:55, jünger als jede Quelle). Die Defekte sind echt im
Inc-4-Stand; Sider-Klappe, Blasen-Höhe, Band-Spalt, Top-Menü-„Chat", Resend und Agent-Status/Spinner
werden im Runde-6-Delta gebaut.

* **🔒 Chat liefert keine Antwort — Ursache: Pauls Core-Arbeitsstand kompilierte nicht** (2026-11-08,
  A/B-gemessen): `AsyncStreamingBridge.java` gelöscht, Referenzen standen noch in
  `ToolLoopRequest.java:21,22,54,67,195,199` ⇒ Eclipse baute Error-Stub-Klassen
  (`throw new Error("Unresolved compilation problem:")`); der `Error` entging allen `catch`-Zweigen
  (`AbstractAgent.call:314` fängt `Exception`, `ProjectRuntime:422` nur `RuntimeException`) und starb
  still im Future — kein `partial`, kein `done`, **keine Log-Zeile**, Provider nie kontaktiert.
  Bestätigt Pauls Beobachtung „Requests nie am LLM-Server angekommen". **Paul behoben** (Core-Commits,
  2026-11-08). Nebenfunde: ein gesunder Turn loggt in diesem Bereich ebenfalls nichts.
* **🔒 Stale-Fallen für künftige Läufe (nicht Ursache, aber reale Spreizfallen):** `factory-ui/pom.xml`
  clean-Filesets zeigen auf `web-app/` (existiert nicht) ⇒ `mvn clean` löscht `factory-ui/dist` nie,
  `copy-resources` ist additiv (drei Asset-Generationen in `target/classes/static/ui`); ein
  Eclipse-Bau führt `pnpm run build` **nie** aus (lifecycle-mapping `<ignore/>`); ein
  repo-aufgelöster `mvn spring-boot:run` liefert die **Oktober-SPA** aus `~/.m2`.
* **🔒 Top-Menü ohne „Chat"** (Paul 2026-11-08: „das muss weg — chat ist nur an der Seite") — in
  [R-WP-43/UC-WP-76](ui.md) nachgetragen.
* **🔒 Resend neu gefasst** (Paul 2026-11-08, zwei Nachrichten: erst „der kommt aber ja eh raus" zum
  Inaktiv-Defekt nach Reload, dann BWT) — es gibt **keinen eigenen Resend-Knopf** mehr; stattdessen
  sendet der **Send-Knopf mit leerer Eingabe** (kein laufender Turn, Chat-Nachrichten vorhanden) die
  **letzte Nutzer-Nachricht** erneut — genau der Fall „nach Abbruch sonst passiert nichts". Der
  Send-Knopf ist **nie deaktiviert** ([R-WP-41](chat.md) nachgezogen), leere Eingabe im laufenden
  Turn tut nichts. Steht in [R-WP-30/UC-WP-57](chat.md) ❌ (neu gefasst), gebaut im Runde-6-Delta.
* **⏳ Agent-Status „weiter defekt", Spinner fehlt** (Paul 2026-11-08, „es funktioniert wieder") —
  gegen [R-WP-45/UC-WP-79](chat.md) prüfen (blau arbeitet · gelb kompaktiert · grün bereit, Spin),
  Ursache suchen und im Runde-6-Delta reparieren.
* **⏳ Fabrik-Härtung „je Turn genau ein Endereignis"** — der Runde-3/4b-Verdacht (unten) ist damit
  **belegt**: ein `Error` im Turn-Task schweigt vollständig (kein `done`, kein `error`, keine
  Log-Zeile, Kachel zurück auf IDLE). Fix gehört der Fabrik (`ProjectRuntime` fängt `Throwable` bzw.
  wertet den Future aus), kein Core-Eingriff — Story nach dem Runde-6-Delta.
* **Nebenbefund (Messung):** `runningTurn` wird nur von `done`/`error`/Stop/Send-Fehler gelöscht,
  **nicht** vom Reconnect-Pfad (`chat.page.tsx:373-383`) — nach Send+Reload bliebt der Composer ohne
  Endereignis für immer im Warte-Zustand; übernimmt in die Härtungs-Story oben.
## Runde 5 — Browser-Abnahme läuft (2026-11-08)

* **🔒 Status-Indikator bei Fehler = rot** (2026-11-08, Paul: „genau rot wie im Plugin") — als
  AntD-Fehler-/Alert-Indikator, sofern es dort ein Widget gibt; Widget-vor-eigenbau gilt allgemein
  ([R-WP-46/UC-WP-81](ui.md)), ebenso „UI-Funktionen immer als wiederverwendbare Widgets bauen und
  nutzen". Steht in [R-WP-45/UC-WP-79](chat.md).
* **⏳ A7 · Tool-Results nach dem Restore wiederherstellen** (2026-11-08, Paul: „warum machen wir das
  nicht?") — die Results stehen in Jons History, der Verlauf zeigt sie nach Reload nicht. Vor dem Bau
  prüfen, ob der Core-History-Bestand sie überhaupt trägt; falls nicht, ist es eine **Core-Änderung**
  (nur mit Pauls Proposal-Approval).

* **🔒 A6 · Scroll entschieden (2026-11-08, Paul)** — Auto-Scroll nur am Ende (R-WP-35 ✅), nach eigener
  Eingabe immer, Zurückscrollen ans Ende bewaffnet neu, schwebender „Scroll bottom"-Knopf
  (AntD `FloatButton`, umgekehrtes `BackTop`) — [UC-WP-80](chat.md) ❌. Lade-Punkt der Timeline =
  AntD `Spin`/`LoadingOutlined` ([R-WP-40](chat.md)), Spin auch am Status-Indikator ([R-WP-45](chat.md)).

## Runde 4b — Look-Restart (Paul 2026-11-07, „docs schreiben, ich will ein Restart machen")

Auslöser: Pauls IST-Screenshots gegen SOLL-Bild (`SOLL.png`) + Mockup (`factory-ui-mockup.tsx`).
Entschieden: **Rahmen nach Mockup** — dunkles Top-Menü **und** Sider ([R-WP-43](ui.md) ❌ neu;
die Fassung „Sider statt Top-Menü" vom 2026-11-06 ist zurückgenommen, [R-WP-1](ui.md)). Aktionen
werden echte Buttons/Selects. Neu im SOLL, Rückprüfung offen:

* **⏳ Top-Menü-Einträge = Projekte · Chat · Settings** (2026-11-07, Jon, abgeleitet aus der ersten
  Version + Mockup) — [R-WP-43/UC-WP-76](ui.md). *Andersfall:* ohne „Chat" im Menü (nur Sider) oder
  ein vierter Eintrag.
* **⏳ Einheiten-Ordnung Chat · Paco · Files als letztes** (2026-11-07, aus dem Mockup-Kommentar
  „… die chats Jon / Paco / Files als letztes?") — [R-WP-43/UC-WP-77](ui.md). *Andersfall:* wie
  gebaut (Projekt-Klick öffnet den Chat, kein Chat-Eintrag im Sider).
* **🔒 Mehrere Chats je Agent = NICHT im MVP** (2026-11-08, Paul: „wir können keinen neuen Chat
  anlegen"; auch mit Paco zunächst ein Chat je Agent) — „Chat 59" + „+ Neuer Chat" im SOLL-Bild waren
  Layout, kein Feature; **PR-5 bleibt** ([R-WP-15](chat.md)). Die Sider-Gruppen ([R-WP-44](ui.md))
  sind so gebaut, dass mehrere Chats später einrutschen.
* **⏳ Sprechblasen + Zeit unter der Zeile** (2026-11-07, Paul: „nicht zwingend … mit der Zeit unten,
  aber wäre gut") — die Sprechblasen-Optik selbst ist Teil der Design-Übernahme ([R-WP-29](chat.md));
  offen ist nur die **Zeit unter der Zeile**. *Andersfall:* eigene Regel in [chat.md](chat.md), kein
  Nebenprodukt.
* **🔒 Modell-Auswahl + Kontextanzeige im Chat = später** (SOLL-Bild: Modell-Dropdown „gpt-6.1-sol",
  „Kontext ~914"; Paul 2026-11-08: „später") — im Chat kommt kein Modell-Umschalter (kollidierte ohnehin
  mit [R-WP-23](config.md): Modell gehört je Agent in die Config); **in der Config** ist das Modell-Feld
  MVP — Auswahl oder Eingabe, siehe F8 unten.
* **⏳ Chat-Optionen (Clear/Compact am Chat, nicht nur an den Kacheln)** (2026-11-07, Pauls Punkte 4/5) —
  die Kachel-Aktionen werden Buttons ([R-WP-43](ui.md)); **wo** die Chat-Optionen liegen (Dropdown im
  Chat-Kopf wie in der Referenz?), vor dem Inkrement klären. [R-WP-7](chat.md) wird angefasst.
* **🔒 Design-Tests-Regel (2026-11-07, Paul: „keine Tests anlegen, Tests die im Weg sind stabilisieren
  oder löschen")** — Design-Änderungen ändern **keine** UI-Tests; für Look gibt es keine neuen Tests;
  Abnahme am Browser. Steht in [R-WP-39](ui.md).
* **🔒 Bubbles = Teil der Übernahme** (2026-11-07, Jon, abgeleitet aus „übernimm das Design aus der
  ai-schulung") — der Verlauf folgt der Referenz-Optik (Nutzer-Bubble rechts blau, Antworten links als
  Karte), gebaut Inkrement 2 (`web-factory`, 2b), [R-WP-29](chat.md) ist geschärft.
* **🔒 Timeline-Optik = AntD `Timeline`** (2026-11-07, Paul, nach Look-Abnahme): Werkzeug-/Think-/Delegations-Schritte
  gruppieren sich zu **einem Widget je zusammenhängender Schritt-Folge** (LLM-Blase, Fehler und Diff-Block
  beenden die Gruppe), Dots groß, Abstände sehr klein (Steps-Geometrie im Chat-Scope überschrieben — die
  Basis drückt das Maß nicht per Token), Uhrzeit als HTML-`title`, arbeitender Schritt = loading-Dot,
  **Think zeigt wie ein Tool** ([R-WP-40](chat.md) umgeschrieben). *Nicht im Doc und nicht gebaut:* Kappung
  bei 20 Schritten mit Verdrängung — bewusst MVP-nachgelassen (Paul).
* **🔒 Sider-Baum entschieden (2026-11-08, Paul per Sider-Beispiel + `side soll.png`): Klapp-Gruppen
  je Projekt, Chats und Einheiten als Kinder, kein „Projects"-Eintrag, leerer Sider ohne Projekte,
  Einklap-Griff (`trigger`)** — steht in [R-WP-44/UC-WP-78](ui.md) ✅; gebaut + reviewed im
  Sider-Inkrement (Inc 4, 2026-11-08), Look-Abnahme am Browser steht aus. Der Anteil
  **Files-Slide-out** ist entschieden (2026-11-08, Paul: „der Drawer ist das richtige Element für das
  Slide-out rechts"): das Slide-out **bleibt**, als **AntD `Drawer`** von rechts — ❌-Anteil in
  [R-WP-33](files.md), eigener Slice nach Inc 4. „Top-Menü ist richtig" (Paul 2026-11-08):
  Projekte · Chat · Settings bleibt, [R-WP-43](ui.md) unverändert.

## Runde 3 — AntD-Umbau der Oberfläche (Paul 2026-11-06)

Entschieden: **AntD als neue Design-Basis**, Bootstrap verlässt die App
([R-WP-39](ui.md)), Navigation wird der **Projektbaum im Sider** ([R-WP-38](ui.md)), Zeiten bleiben
**live** ([AF-ADR-003](adr/003-reference-ui-is-a-donor-not-a-source.md)). Geber der Muster ist
`/Users/sterlp/dev/workset/ai-schulung/app/frontend` — **nie** ihr Zustandsmodell (AF-ADR-003).
Reihenfolge, je ein grünes Inkrement:

1. **AntD-Basis** — Provider, Theme-Tokens auf unsere Farben, Bootstrap raus (Hülle, Buttons,
   Karten, Formulare, Dialoge). Chat-Log bleibt unangetastet. Zieht **F5** (Optik Config) und
   **F9** (Clear-Layout) mit hoch.
2. **Markdown** — ✅ gebaut 2026-11-06: `react-markdown` + `remark-gfm` + `remark-breaks`
   ([R-WP-28/UC-WP-73](chat.md)); `markstream` hielt die Zusage nicht (CSS nie importiert, keine
   `breaks`-Option — gemessen). C-2(b) damit erledigt.
3. **Eingabe** — Auto-Grow, Send nur bei Text, Refocus nach dem Turn ([R-WP-41/UC-WP-74](chat.md));
   **Enter sendet, `Shift+Enter` und `Ctrl/Cmd+Enter` = neue Zeile** ([R-WP-30/UC-WP-56](chat.md),
   Paul 2026-11-06). **3b** schleift die Prio-Nachricht `!` durch ([R-WP-42/UC-WP-75](chat.md)).
4. **Timeline** — Werkzeuge und Delegationen als Einträge mit Agentenname, `loading` und
   Ankunftszeit; **Dauer nur je Turn** an der Antwort ([R-WP-40/UC-WP-72](chat.md)); kein `hr`
   zwischen Zeilen ([R-WP-29/UC-WP-55](chat.md), C-6). F3 (Agent-Arbeit unsichtbar) wird hier
   mitbehandelt: der Eintrag zeigt, wer arbeitet.
5. **Sider-Navigation + URL** — **5a gebaut 2026-11-07**: Projektbaum im Sider, Klick aufs Projekt öffnet
   seinen Chat, URL auf den **klaren Projektnamen** (C-3 damit erledigt, Entscheidung gegen die Id steht
   in [resolved-points.md](resolved-points.md)). Offen **5b**: die Einheit **Files** als Sider-Einheit und
   Adresse ([UC-WP-70 GIVEN 2](ui.md)) — erst danach der Flip von [R-WP-38/UC-WP-70](ui.md).

* **🔒 Dunkel (2026-11-06, abgelöst 2026-11-07 durch die Design-Übernahme, [R-WP-39](ui.md)):** die
  Fassung „Dunkel = eine Tabelle, OS setzt `data-theme`, Token-Parität gegen das Grundblatt" ist
  ersetzt — manueller **Hell/Dunkel-Umschalter** im Header (OS-Vorliebe = Startwert, Auswahl überlebt
  den Reload), Token-Paritäts-Tests gelöscht (Design-Tests-Regel, [R-WP-39](ui.md)).

* **⏳ Refocus nach dem Turn meidet das andere Feld** (2026-11-06, Ableitung zu
  [UC-WP-74 GIVEN 4](chat.md), Rückversicherung ausstehend) — der Fokus kehrt in die Eingabe zurück,
  **außer** der Nutzer tippt inzwischen in einem anderen Feld (Suche, Config). *Andersfall:* Fokus immer
  in die Eingabe = verlorene Eingabe an der anderen Stelle.

* **🔒 „Jon muss weg" war auf das LABEL beschränkt (2026-11-06, Paul per Zuruf; zurückgenommen
  2026-11-08)** — die neue Sider-Struktur zeigt die **Chats des Projekts als Einträge** (Jon, später
  Paco), [R-WP-44/UC-WP-78](ui.md); Wortlaut „Chat" oder „Jon" ist Paul egal („mir geht es nicht ums
  Wording, sondern das Design"). Die **Kachel** behält ihren Namen ([R-WP-7](chat.md)), die Docs
  nennen den Agenten weiter Jon.
* **⏳ Einheiten nur unter dem geöffneten Projekt** (2026-11-07, Schärfung zu
  [R-WP-38](ui.md) vor Inkrement 5b, Rückversicherung ausstehend) — der Sider zeigt „Files" **nur** unter
  dem Projekt, das die Adresse nennt. Abgeleitet: die Regel will sagen, **wo man ist**, und ein Projekt,
  das nicht offen ist, hat nichts aufzuklappend. *Andersfall:* Einheiten unter **jedem** Projekt = die
  doppelte Zeilenzahl im Sider und Adressen für Projekte, die niemand offen hat.
* **⏳ „Ein laufender Turn endet nicht am Klick auf die Einheit" steht jetzt ausdrücklich in
  [R-WP-38](ui.md)/[UC-WP-70 G2](ui.md)** (2026-11-07, abgeleitet aus [R-WP-33](files.md) „der Chat
  bleibt daneben sichtbar" und R-WP-15 „je Projekt ein Chat") — nötig wird das, weil der Datei-Bereich
  eine **Adresse** bekommt: würde die Adresse eine neue Seite rendern, würde der Chat neu mounten und der
  Stream neu öffnen. Angenommen: ein Adresswechsel **innerhalb** eines Projekts behält die Seite.
  *Andersfall:* „Klick auf Files darf den Turn abbrechen" wäre eine neue Regel und sichtbarer Datenverlust.
* **🔒 Berichtigt (2026-11-07, kein neuer Punkt):** [R-WP-33](files.md) nannte für die Tab-Icons noch
  `bootstrap-icons` — Rest vor Pauls AntD-Entscheidung ([R-WP-39](ui.md), 2026-11-06: Bootstrap raus).
  Die Regel nennt jetzt **keine** Bibliothek mehr; die Icon-Wahl regelt R-WP-39.
* **🔒 Tastatur entschieden (2026-11-06, Paul: „A")** — **Enter sendet, Shift+Enter und Ctrl/Cmd+Enter =
  neue Zeile**; gebaut und abgenommen in [R-WP-30/UC-WP-56](chat.md) ✅ (Inkrement 3). Der Konflikt mit
  Pauls Regel vom 02.11. ist damit gelöst, nicht offen; Merkposten gegen Wiederaufmachen steht in
  [resolved-points.md](resolved-points.md). Pauls Zusatz dazu (12:40): **„es gibt kein Lock vom Input"** —
  Eingabe ist auch im laufenden Turn benutzbar, sie geht in die Queue ([R-WP-41](chat.md) ✅).
* **🔒 `!` = Prio-Nachricht — Core-Regel, Fabrik reicht sie nur durch (2026-11-06, Paul)** — Regeln
  stehen im Core ([`queued-user-messages.md`](../../org.sterl.llmpeon.core/docs/queued-user-messages.md)
  R8, R11), hier nur der Fabrik-Anteil: [`R-WP-42/UC-WP-75`](chat.md) ✅ (Inkrement 3b).
* **⏳ Prio-Zeile ist nur eine vorläufige Zeile (2026-11-06, Jon nach Review; Paul: „mit deiner
  Empfehlung")** — [UC-WP-75 GIVEN 2](chat.md) versprach die `!` Zeile **im Verlauf**; der Core schreibt
  eine Prio-Nachricht nie in die Memory (Core R8.7), also trägt der Verlauf sie nie. Angenommen: die Zeile
  ist die **vorläufige** Zeile des Absenders (von der nächsten bestätigten Nutzerzeile geschluckt) und die
  Regel sagt das jetzt so. *Andersfall:* eine dauerhafte Prio-Spur (Inc 3d) braucht einen zweiten Speicher
  neben der Memory (widerspricht R-WP-11) — eigenes Inkrement, frühestens nach deinem Smoke-Test. Ebenso
  neu in der Regel: ein `!` ohne Text ist konsumiert (Core R8.6) und die Prio trägt den Absendernamen
  ([R-WP-25](user.md)) — beides war schon gebaut, aber nicht behauptet.
* **⏳ „Arbeitet" heißt „letzter Schritt eines laufenden Turns" (2026-11-06, Jon für Inkrement 4)** —
  [R-WP-40/UC-WP-72](chat.md) verlangt einen laufenden Indikator „solange er arbeitet"; der Core meldet je
  **Werkzeug** kein Ende (nur die Zeile bei Ankunft), also endet der Indikator am nächsten Schritt oder am
  Ende der Antwort. Der **Name** des Schritts ist der Core-Stempel auf der Zeile — ohne Stempel kein Name
  ([R-WP-37](chat.md)). Die **Delegation** ist die Ausnahme: Anfang und Ende kommen typisiert über den
  Factory-Listener der Delegation, ihr Eintrag nennt das **Mitglied**. Da die Fabrik Mitglieder-Zeilen
  filtert ([R-WP-4](chat.md)), ist der Stempel eines Werkzeug-Schritts praktisch immer **„Jon"** — ehrlich,
  aber laut; das Mitglied nennt erst der Delegations-Eintrag. **Ebenfalls angenommen:** Jons
  **Think-Abschnitt** wird kein Eintrag — der Chat zeigt sein Denken nicht, und R-WP-40 nannte ihn trotzdem
  als Schritt (Widerspruch **innerhalb** einer Regel, jetzt im Doc aufgelöst). *Andersfall:* eine echte
  je-Werkzeug-Dauer oder ein sichtbarer Think-Abschnitt bräuchten je eine eigene Regel (Dauer zusätzlich den
  Core) — beides wäre neu und nicht Nebenprodukt der Timeline. **Nachschärfung (2026-11-06, vor Inc 4c):**
  R-WP-40 forderte je Eintrag auch „und seinen Auftrag (`Dev: <Prompt>`)". Der Draht trägt den Prompt **nur im
  Wortlaut der Werkzeugzeile** (`"<Name> start:\n<Prompt>"`), der Delegations-Listener liefert ihn nicht — ihn
  aus dem Text zu lesen wäre genau das Parse-Verbot der Regel. Angenommen: der **Delegations-Eintrag nennt das
  Mitglied**, der **Auftrag steht unverändert in der Werkzeugzeile darüber**; die Klammer ist aus der Regel
  geworfen. *Andersfall:* ein Auftrag im Delegations-Eintrag verlangt eine **Core-Erweiterung** (Prompt im
  Listener) — Core-Änderung, nur mit deinem Proposal. **Und:** ein **Diff-Block** ist kein „nächster Schritt"
  (R-WP-40 zählt Werkzeug, Think und Delegation) — er lässt den Indikator am Werkzeugschritt, der vor ihm
  stand. *Andersfall:* Diff als Schrittende gelesen = eine Zeile Code und eine Assertion.
* **⏳ Screenshot-Werkzeug (Stagehand) parked** (2026-11-06) — `@browserbasehq/stagehand` v4 kann den
  Dev-Server öffnen und Screenshots/Layout-Messungen **ohne** LLM-Call (nur `page`-API); verlangt
  aber **Node ≥ 22.18**, der Bau fährt **22.12** (v23.4 ist vorhanden). Angenommen: kein neues
  Werkzeug, solange die F-Befunde unpräzise sind — Look-Abnahme bleibt Pauls Smoke-Test.
  *Andersfall:* eigenes Script unter Node 23.4, kein Vitest-/Maven-Eingriff.
* **❓ Ein Turn kann enden, ohne dass der Draht ein Endereignis sendet** (2026-11-06, Review-Fund zu
  Inkrement 4, **unbestätigt**) — `ProjectRuntime` publiziert `done` nur, wenn die Antwort nicht `null`
  ist; ein `Error`, der allen `catch`-Zweigen entkommt, und ein Submit, das der Core einreiht statt einen
  Turn zu öffnen, senden möglicherweise weder `done` noch `error`. Sichtbare Folge wäre eine Warteschleife
  und ein Indikator, der weiterblinkt. Nicht gemessen, deshalb keine Regel und kein Bug-Verdikt.
  **Empfehlung:** eigene, kleine Story nach der Timeline — Regel „je Turn genau ein Endereignis auf dem
  Draht" plus Backend-Test; die Fabrik repariert das allein (kein Core-Eingriff nötig, der `finally`-Pfad
  gehört ihr). *Andersfall:* ist der Pfad real und häufig, wäre das ein Look-Defekt beim nächsten
  Smoke-Test — melde mich dann mit dem Messwert.



## Runde 3 — Pauls Smoke-Test-Befunde (2026-11-03, Backlog)

Positiv bestätigt: **(1)** Chat-Restore geht. **(2)** Gemeinsam ein RAG-Chat gebaut
(`/Users/sterlp/dev/workset/ai-schulung`) — die Fabrik ist damit erstmals real produktiv gewesen.

Defekte/Fragen, je eine Story je Punkt erst nach Klärung (❓ = präzisieren, bevor geplant wird):

* **🔒 F3 · Agent-Arbeit unsichtbar in den Status-Kacheln** (2026-11-03) — entschieden 2026-11-08
  (Paul, Abnahme): der **Status-Indikator je Kachel** zeigt arbeitet/kompaktiert/bereit in
  Blau/Gelb/Grün ([R-WP-45/UC-WP-79](chat.md) ❌), die Reihe steht ohne Abstand über dem Chat; ob der
  Blick in ein Mitglied **live** läuft, bleibt C-7 (unten).
* **❓ F5 · Optik im Detail** (u. a. Config-Seite) — Totalschaden-Einschätzung von Paul; Detail-Liste
  fehlt noch. Erst sammeln, dann eigenes Look-Inkrement.
* **⏳ F8 · Config-Umfang: alles was das Plugin hat** (2026-11-02; geschärft Paul 2026-11-08) —
  `reasoning_effort` nicht einstellbar, Header-Einstellungen und weitere Plugin-Einstellungen fehlen;
  Umfang mit dem Core-Bestand abgleichen, dann UCs ziehen ([config.md](config.md), [R-WP-23](config.md)).
  Neu (Paul 2026-11-08): das **Modell-Feld ist Auswahl ODER Eingabe** (AntD `AutoComplete`-Stil — wie
  im Eclipse-Plugin, **keine Dropdown-Only-Liste**, nicht jeder Provider hat eine Modell-Liste) —
  MVP-Bestandteil der Config-Story.
* **❓ F9 · „Clear" hinterlässt Tool-Zeilen mit zerstörtem Layout** — nach Clear bleiben Tool-Messages
  im Chat, Layout kaputt. Verhalten von Clear klären (was sollClear entfernen?) → Regel in
  [chat.md](chat.md).
* **❓ F10 · Bootstrap „docs laden einmal zu Beginn" greift nicht zuverlässig** — Fabrik-Jon fragte nach
  `docs/index.md`, statt es beim Start geladen zu haben. Bootstrap-/Prompt-Defekt der Fabrik-Agenten —
  prüfen, wo die Regel stehen muss (Agent-Prompt vs. Factory-Bootstrap).

## Runde 3 — Live-Befunde aus der Nutzung (2026-11-03, zweiter Schwung)

* **❓ C-1 · TurnClaim-Timeout** — `ERROR ProjectRuntime: turn ... never got the project back from a
  claimed action` — `TurnClaim.awaitNoAction` wirft nach 10 000 ms (`ProjectRuntime.java:348`).
  Aufgetreten beim Compact. Verwandt zur ⏳ TurnClaim-Lücke; Ursache im Code klären (Dev), bevor eine
  Regel steht.
* **❓ C-2 · Compact-Ergebnis live** — in der Agent-Karte erscheint nur „error", weder Ergebnis noch
  Fehlermeldung sichtbar; Regel in [chat.md](chat.md) fehlt (wie Compact live erscheint).
  *(b) „nach Reload ohne Markdown" ist erledigt (2026-11-06, Inkrement 2): Markdown zeichnet jetzt
  überall dasselbe Renderer-Pfad, auch im Verlauf — [R-WP-28/UC-WP-73](chat.md).*
* **❓ C-7 · Agent-Chat ist ein anderes Widget** — der aufgeklappte Blick auf ein Team-Mitglied nutzt
  ein eigenes Chat-Widget: anderes Look&Feel, aktualisiert nicht während der Agent arbeitet, zieht
  alle Daten erneut (= Totalschaden). SOLL: dasselbe Chat-Widget wie der Haupt-Chat, an den
  Agent-Stream gehängt, **read-only** (keine Eingabe). Im Umfeld von [R-WP-26](chat.md).




## ⏳ selbst entschieden (2026-10-25/26) — bitte überfliegen

* **⏳ Dev-Modell/URL/Key werden gespeichert, aber vom Core nicht angewandt** (2026-11-02, aus dem
  7a/7b-Bau) — [R-WP-23](config.md) zeigt je Agent Modell, URL und Key; für den Slot `dev` liest der
  Core nur das Basismodell (`devAgentConfig()`) — gespeicherte Überschreibungen von Modell, URL und
  Key wirken nicht (der UI-Status `keySet` für Dev ist also nur Anzeige). Decision: Felder bleiben und
  speichern doc-konform (`agents/dev/AGENT.md`); kein UI-Hinweis, kein Core-Workaround. Dev-**Think**
  ist ausgenommen: es wirkt nicht nur, es ist im Core der Base-Default für alle anderen Agenten
  ([R-THINK-10](../../org.sterl.llmpeon.core/docs/per-agent-think.md)) — die Seite beschriftet das Feld
  im Dev-Slot deshalb `Think (Default)` ([R-WP-23/UC-WP-67](config.md)). *Andersfall:*
  sollen Devs Werte wirklich wirken, ist das eine Core-Story (Proposal mit WARUM) — dann UC in
  config.md nachziehen.
* **⏳ „Chat" öffnet das zuletzt aktive Projekt** (2026-11-02, meine Empfehlung, Rückversicherung
  ausstehend) — [R-WP-1](ui.md): Top-Menü „Chat" ohne aktives Projekt → Projekte; mit aktivem Projekt
  → dessen Chat. *Andersfall:* „Chat" führt immer auf die Projektliste (keine Abkürzung) oder es
  braucht eine Chat-Auswahl.
* **⏳ Provider-Wechsel setzt Modell/URL auf die Defaults des Providers** (2026-11-02) — wie der
  Provider-Dialog im Plugin ([R-WP-16/UC-WP-62](config.md)). *Andersfall:* Felder nur leeren statt
  Defaults einzutragen.
* **⏳ Die Fehlerzeile eines gescheiterten Turns liegt im Core-Memory** (2026-11-02, Runde 2, aus
  [R-WP-34/UC-WP-64](chat.md)) — die Fabrik schreibt bei einem Fehlschlag den Nutzer-Text und
  „`[Turn failed]: <Grund>`" in Jons Memory (`ThreadSafeMemory.add`, Core-API, kein Core-Eingriff):
  **kein** zweiter Speicher, und das Modell liest die Zeile im nächsten Turn (Verwandtschaft zu
  R-AEM-1). Eine in der Core-Queue wartende Eingabe ist bis zu ihrem Turnstart **nicht** persistiert —
  sie taucht im Verlauf erst mit diesem Turn auf. *Andersfall:* soll eine wartende Eingabe schon vor
  dem Turn reload-fest sichtbar sein, braucht es einen eigenen Speicher (widerspricht R-WP-11) oder
  einen Core-Mechanismus.

* **⏳ Fehler-Ergebnis einer Delegation erscheint wörtlich als Mitglieder-Zeile** (2026-10-30,
  Da-Dok-Befund B4) — [UC-WP-40](chat.md) hat den Fall jetzt ausdrücklich; abgeleitet aus „wie es ist"
  (AF-ADR-002): ein Java-Stacktrace im Web-Chat ist hässlich, aber ehrlich, und Parsen/Kürzen ist
  verboten. *Andersfall:* soll der Stacktrace nicht erscheinen, muss der Core Fehler-Ergebnisse
  kennzeichnen — ein neuer Core-Mechanismus, keine Fabrik-Heuristik.
* **⏳ UC-WP-9 klargestellt: die sichtbare Ablehnung ist immer Jons** (2026-10-26, Abnahmefrage A1 von
  Inc 4) — [UC-WP-9](files.md) trägt jetzt zwei GIVEN: Jons eigener Schreibversuch hinterlässt die
  Werkzeug-Zeile im Chat, die Ablehnung eines **Team-Mitglieds** endet beim Modell (Werkzeug-Ergebnis,
  keine Datei, **keine** Chat-Zeile), weil [R-WP-4/UC-WP-40](chat.md) Mitglieder-Interna unsichtbar
  hält. Klarstellung, keine Bedeutungsänderung. *Andersfall:* soll eine Mitglieds-Ablehnung doch im Chat
  erscheinen, ist das eine Ausnahme zu R-WP-4 und braucht eine eigene Regel.
* **⏳ Der Urheber-Name gehört zur dauerhaften Zeile, nicht zum Live-Fragment**
  ([UC-WP-39](chat.md), 2026-10-26 gebaut, 2026-10-27 mit dem Core-Rückbau wieder ❌) — die Live-Zeile eines laufenden Turns trägt **keinen** Namen, weil live nur Jon spricht
  und damit nichts zu verwechseln ist. *Andersfall:* soll auch das wachsende Live-Fragment ein Label
  tragen, ist das eine kleine UI-Zeile — dann sagt das Doc es ausdrücklich. **Ausnahme seit Inkrement 4:**
  die **Timeline-Schritte** nennen ihren Core-Stempel ([R-WP-40](chat.md)) — betroffen ist die Schritt-Zeile,
  nie das wachsende Antwort-Fragment.


* **⏳ Config-Quelle ist eine eigene Datei der Factory** (`~/.aifactory/config/config.properties`, Keys auch
  aus Env) — die Annahme "wir lesen den Bestand aus `~/.peon`" war **falsch**: der Plugin-Bestand liegt
  in Eclipse-Präferenzen, `~/.peon` enthält nur Skills/Agents/Commands. Decision:
  [R-WP-9](config.md), Begründung
  [Architektur → Konflikt](aifactory-architektur.md#konflikt-dokumentiert-2026-10-25).
  *Andersfall:* wenn der Plugin-Bestand doch gelesen werden soll, bräuchte es einen Core-Port des
  Config-Stores auf Dateien — plus die Frage, wessen Werte dann gewinnen.
* **⏳ Runtime-Zustand niemals im Checkout** — nur `stories/` und `proposals/` sind dort erlaubt
  ([Architektur-Invariante](aifactory-architektur.md#invarianten)); Grund: `git status` des Nutzers
  darf nicht vermüllt werden.
* **⏳ Doppelte URL: Doc gewinnt gegen Code** — Inc 1 baute `400 "already present"`,
  [R-WP-2/UC-WP-3](project.md) verlangt „kein zweiter Clone, keine Fehlermeldung". Decision:
  API antwortet `200` mit dem bestehenden Projekt, UI zeigt es nur an. *Andersfall:* willst du die
  Meldung als Feedback, wird UC-WP-3 umformuliert und der `400` bleibt — Aufwand dann: Doc + 3 Tests
  rückabwickeln.
* **⏳ „Bereits vorhanden" ist `200`, nicht `201`** — abgeleitet: es entsteht nichts Neues, also kein
  Created. Die Regel nennt bewusst keinen HTTP-Code.
* **⏳ Gleicher Ordnername + andere URL ⇒ abgelehnt**
  ([R-WP-18/UC-WP-26](project.md)) — abgeleitet aus dem Ziel `<home>/projects/<name>`: zwei Repos
  können sich nicht einen Ordner teilen. *Andersfall:* zweiter Name (`demo-2`) statt Fehler → Registry
  und Ordnername entkoppeln.
* **⏳ Vorhandener Nicht-Git-Ordner wird nie gefüllt oder überschrieben**
  ([R-WP-18/UC-WP-27](project.md)) — Ableitung: die Factory gehört dieser Ordner nicht.
* **⏳ v1 klonen wir nur über `https://`** — ssh-URLs (`git@host:org/repo.git`) tragen zwar kein
  Passwort, setzen aber Schlüssel oder Passphrase-Prompt voraus; beides verbietet
  [R-WP-21](project.md) (nie interaktiv). Deshalb abgelehnt statt halb funktionieren
  ([R-WP-20/UC-WP-33](project.md)). *Andersfall:* private Repos per ssh wären eine eigene Story
  mit Secret-Ablage und bekannten Hosts — dann diesen Satz wieder raus.
* **⏳ Die Registry-Datei ist keine Löschfreigabe**
  ([R-WP-19/UC-WP-29](project.md)) — entfernt wird nur unter `<home>/projects`; ein von Hand
  eingetragener absoluter Pfad (`/`, `/Users/…`) darf beim Entfernen nichts löschen.
* **⏳ `aifactory.home` / Env `AIFACTORY_HOME` ist Deployment, keine Nutzer-Einstellung** —
  [R-WP-5](chat.md) sagt „der Nutzer kann den Ort nicht ändern": gemeint ist kein Feld in den
  Einstellungen. Ohne den Override könnten Tests nicht in einem Temp-Home laufen.
* **⏳ Klonen synchron, Frist 300 s, ohne Fortschritt im UI** — während ein Klon läuft, blockiert
  „Hinzufügen" weitere Adds (Lesen der Liste nicht). Für große Repos später: Hintergrund-Klon +
  Status in der Liste.
* **⏳ Shell läuft ohne Rückfrage** — bewusst im MVP ([Architektur-Invariante Schreibgrenze](aifactory-architektur.md#invarianten)):
  das Schreib-Pinning fängt `disk*`-Schreibzugriffe ab, **nicht** was `shell` in einem Prozess tut.
  Akzeptiertes Risiko für einen Prototyp im internen Netz; vor externem Zugriff ist eine Zusage oder
  eine Sandbox nötig.
* **⏳ Auslieferung ist Teil des Inkrements, nicht des Deployments** — eine View, die im Browser 500
  liefert, gilt als nicht gebaut, auch wenn alle Unit-Tests grün sind. Deshalb
  [R-WP-22/UC-WP-34/UC-WP-35](ui.md) mit echten HTTP-Tests auf einem zufälligen Port.
  Anlass: seit einem Hand-Commit (`8c7dec24`, `@Controller` auf `SpaForwardController`) beantwortete
  dieser Mapping-Pfad **jeden** `/ui/**`-Request — Assets wurden nie ausgeliefert, `/ui/` endete in
  ~6 000 Weiterleitungen und einem `StackOverflowError` pro Aufruf. Die API war grün, die App tot.
* **⏳ Paco darf `docs/` lesen** und antworten, schreiben nur in die eigenen Vorschlags-Ordner
  ([Paco R-PC-2](paco.md)).
* **⏳ MVP ohne Plan-Werkzeuge, ohne memory\*-Werkzeuge, ohne MCP** — die drei sind Plugin-only bzw.
  ein Core-Port und werden eigene Stories; der MVP-Jon schreibt Pläne mit den Disk-Tools nach
  `peon-plan/overview.md`.
* **⏳ Nach einem Neustart ist nur Jon wieder da, sein Team beginnt leer** — abgeleitet aus
  „sonst wie beim Eclipse-Plugin" + globalem ADR-0024 (Sklaven sind RAM-only)
  ([R-WP-11](chat.md)). Team-Historie über den Restart wäre eine neue Regel
  und ein Core-Eingriff.
* **⏳ „Workspace" ist für die Factory das geklonte Projekt** — kein Eclipse-Workspace-Begriff,
  Arbeitsordner ist immer das Projekt-Root
  ([R-WP-3](project.md)/[R-WP-6](files.md)).
* **⏳ Key wird nicht auszugsweise angezeigt** ([R-WP-16](config.md)) — angenommen: „maskiert"
  heißt **kein Zeichen** nach draußen, gezeigt wird nur `gesetzt`/`fehlt` und die Quelle. Abgeleitet aus
  R-WP-20 (Zugangsdaten werden niemandem zurückgegeben) und dem fehlenden Login (R-WP-17). *Andersfall:*
  willst du die letzten 4 Zeichen zur Unterscheidung mehrerer Keys, ist das eine Satzänderung — ein
  Auszug bleibt ein Auszug.
* **⏳ Datei schlägt Umgebungsvariable** ([R-WP-16/UC-WP-38](config.md)) — die Variable ist der
  Startwert für den ersten Start, die Datei der gepflegte Stand (sonst könnte die UI nie etwas ändern).
  *Andersfall:* Deployment-Diktat („was der Operator setzt, gilt") ⇒ Reihenfolge umdrehen und das Feld
  im View sperren.
* **⏳ Key über die UI entfernen** (2026-11-02, Paul-Szenario: erster Request lieferte „turn needs an
  api key", ein gesetzter Key war im UI nicht erkennbar und nicht löschbar) — Decision: R-WP-16
  geschärft — „Remove key" schreibt den Key aus der Datei, ENV-Quelle wird nur gemeldet, der Status
  wird nach jedem Speichern frisch geliefert. *Andersfall:* soll das UI den Key nicht entfernen
  können, gilt wieder nur die Datei von Hand — dann aber mit sauberer Anzeige des Status.
* **⏳ kein globales Temperatur-Feld** ([R-WP-16](config.md)) — im geteilten Bestand existiert
  Temperatur **nur je Agent**; ein globales Feld wäre erfunden. Der View zeigt, was es gibt.
  *Andersfall:* „je Agent" mit ins MVP ⇒ eigener View-Bereich plus Core-Schreibpfad pro Agent.
* **⏳ Key-pflichtige Provider** ([UC-WP-20](config.md)) — alle außer Ollama und LM Studio
  brauchen einen Key; ohne Key lehnt die Factory, bevor ein Modell gebaut wird. Abgeleitet aus dem
  Bestand des Cores (nur diese beiden ersetzen den Key durch einen Platzhalter). *Andersfall:*
  GitHub-Provider brauchen in der Praxis auch einen Token — dann mit in die Liste.
* **⏳ Provider-Liste steht doppelt (UI + Server)** — der Server validiert gegen `AiProvider` (9 Namen),
  die Einstellungen-Auswahl im UI führt dieselben neun Namen als eigene Liste
  (`factory-ui/src/api/settings.ts`). Abgeleitet: die Alternative wäre, die Liste über die API
  auszuliefern — das würde [`SettingsDto`](config.md) um ein Feld verbreitern, obwohl R-WP-16 die
  Antwortfläche bewusst klein hält (acht Angaben). *Andersfall:* ein neuer Provider im Core ⇒ zwei
  Stellen anfassen; dann eigene Regel „ein Provider-Katalog je Instanz".
* **⏳ Entfernen lässt den Chat-Bestand liegen** — `Entfernen` löscht Eintrag und Checkout, wie
  [R-WP-19](project.md) es aufzählt, aber **nicht** `state/<id>`. Folge: wer dieselbe URL erneut
  anlegt, findet das alte Gespräch wieder (Identität ist die URL). Abgeleitet aus „genau ein Chat je
  Projekt" + [R-WP-11](chat.md); ein Verlauf, der mit dem Projekt sterben soll, wäre eine eigene
  Regel. *Andersfall:* „Entfernen heißt Tabula rasa" ⇒ `state/<id>` mitlöschen, dann aber mit Rückfrage.
* **⏳ Wiederhergestellte Verlaufszeilen tragen keine Zeit** — der Core-History-Bestand kennt keinen
  Zeitstempel; eine eigene Zeit-Datei wäre ein zweiter Speicher ([R-WP-11](chat.md)). Live-Zeilen
  zeigen die Zeit, der wiederhergestellte Verlauf nicht. *Andersfall:* Zeit im Core-History-Format =
  Core-Story.


## ❓ offen

* **⏳ Bild-Support-Tools bleiben** (2026-11-02/03, Paul: „die tools sind von mir — drin lassen") — der
  Fremd-Commit `eeac8abb` „added picture support" ist Pauls Material auf `web-factory-mvp` (nur
  Plugin/Core-Pfade, `aifactory` unberührt). Die Fabrik kennt bisher keine Bild-Regel: Backend-Text-Parts
  erscheinen als `#picture: …`-Textzeile, Core-`ThreadSafeMemory` schreibt `AiMessage.from("ok")` je
  Follow-up. *Offen bis Pauls Smoke-Test:* wie Bild-Parts im Chat gezeigt werden sollen — dann als
  Regel + UC in chat.md, oder Verbatim bleibt gewollt und wird festgehalten.
* **❓ Core: der Abbruch verliert eine bereits gepollte Queue-Eingabe** (2026-11-01, Inc 5d gemessen) —
  [UC-WP-50](chat.md) verspricht „gequeuete Eingaben bleiben in der Queue"; tatsächlich verliert
  `AbstractAgent.call` eine Eingabe, die der laufende Turn beim Stop schon gepollt hat (Poll, dann
  Cancel-Ausstieg, der Drain findet eine leere Queue). Nur noch wartende Eingaben legt
  `handleAbortAndDrain` als Memory-Zeile ab. Die Fabrik kann das nicht reparieren: sie setzt genau das
  Flag, das auch der Plugin-Stop setzt. UC-WP-50 bleibt deshalb ❌, und der Test benennt die Lücke.
  *Fix:* Core-Story — den zuletzt gepollten Eintrag beim Cancel zurücklegen (oder vor dem Verlassen
  drainen); danach UC-WP-50 ✅.
* **🔒 UI-Sprache = Englisch** (2026-11-02, Paul bestätigt; löst den „AGENTS.md-Language-Block"-
  Konflikt von 2026-11-01) — AGENTS.md („UI: English") gilt, [R-WP-27](ui.md) formalisiert: Strings
  im Catalog (`content.ts`/Locale), keine deutschen Draht-Werte. Umsetzung als eigene Story dieses
  Zyklus.
* **🔒 Chats-Übersicht links (Vision, Paul 2026-11-02; Entscheidung 2026-11-07):** jedes Projekt ein „Tree"/Accordion in der
  linken Navigation, mehrere Paco-Chats je Projekt (**+**), Überblick über alle Chats; Main-Chat
  immer Jon. Gehört zu [Paco](paco.md) — erst mit der Paco-Story, kein MVP-Mechanismus.
  **Paul 2026-11-07: „Später — erst nach dem MVP; aktuell gibt es nur ein Paco, er kann später mehr
  als einen Chat haben."** Das SOLL-Bild („Chat 59" + „+ Neuer Chat") ist damit die spätere Form,
  nicht der MVP — **PR-5 bleibt unverändert** (ein Jon, ein Gespräch je Projekt).

### Docs-Review gegen Pauls Produktvision (2026-10-27) — eine Frage nach der anderen

* **❓ V6-Folge:** Im MVP gibt es keine Nutzer (Paul): alle teilen `user/` und — offen — wohl auch ein
  Paco-Gespräch; „privat je Nutzer" (R-PC-1, UC-PC-2) gilt erst mit Login. R-PC-1 entsprechend schärfen.
* **❓ V5 Config-Umfang:** Docs = nur LLM-Felder in `config/config.properties`, MVP ohne MCP.
  Vision: MCP, Agenten-Config, Prompt-Override (Default sichtbar), Codegraph an/aus (Default an beim
  Checkout), Config-Verzeichnis git-sicherbar, Skills für alle Projekte geteilt.
  **Paul 2026-10-27:** jeder Agent (auch Jon, Team, Paco) ist eine Datei; Default wird konfiguriert,
  jeder Agent per Checkbox „überschreiben"; nur File-Config; Datei ohne Prompt ⇒ eingebauter Prompt
  (auch fürs Plugin). 🔒 V5a/b: `agents/<id>/AGENT.md` für jeden Agenten, Rumpf ersetzt, leer ⇒ eingebaut
  ([Core: builtin-agent-prompt-tracked.md](../../org.sterl.llmpeon.core/docs/builtin-agent-prompt-tracked.md)).
  🔒 Staleness: kein Hinweis. 🔒 V5c → [R-WP-23](config.md) (Checkbox blendet ein, Abwählen löscht).
  Offen V5-Rest: MCP, Codegraph an/aus, Skills geteilt.
* **❓ V7 TMP-Hinweis:** Vision: Agenten sollen `tmp/<projekt-name>` anlegen. R-WP-13 pinnt den
  System-TMP — passt, braucht aber eine Prompt-Zeile (Regel fehlt).
* **❓ V8 Entfernen:** Vision „Verzeichnis + Config weg". Heute bleibt `state/<id>` liegen (⏳ oben).
* **❓ V9 Produktname:** „Open AI Fabric (OAF)" vs. „AI Factory" in allen Docs/Code — umbenennen?
* **🚧 V11 Config-Repo (Paul 2026-10-28):** Fabrik startet auf frischem Node ohne Platte → beim Start
  Checkout eines Config-Git-Repos (`main`), Struktur wie `.peon` (Config, `agents/`, Skills,
  `projects.yml` statt `projects.json`); alles außer Chat-History wird committet. UI: „nur testen"
  (Datei, kein Commit) vs. „übernehmen" (commit + push). Kollidiert mit: R-WP-11 (Chat überlebt
  Neustart — auf neuem Node weg), R-WP-9/UC-WP-36 (Home = `~/.aifactory`, nie `~/.peon`), R-WP-16
  (Keys verlassen nie den Server), R-WP-20 (nur öffentliche Repos ohne Zugangsdaten — Config-Repo
  braucht Push-Rechte). 🔒 Keys → [config-repo.md](config-repo.md) R-CR-1 (verschlüsselt, ein Secret aus
  der Umgebung). Weitere offene Punkte stehen dort.
* **❓ Toter Guard (gefunden 2026-10-28):** `ProcessCommandRunner` setzt `BatchMode=yes` als
  Umgebungsvariable — ssh liest das nicht, wirkt nur als `GIT_SSH_COMMAND="ssh -o BatchMode=yes"`. Heute
  folgenlos (SSH-URLs abgelehnt), wird mit SSH ([R-CR-7](config-repo.md)) zum Hänger — Fix gehört in
  dessen Bau (UC-CR-8 Passphrase-Fall).
* **🔒 V10 Inc 4** (Schreib-Pinning, Datei-Bereich) — reviewed 2026-11-01 auf `web-factory-mvp`: CONCERNS,
  kein Blocker; Da-Mek-Pinning, R6 und alle Inc-4-Tests halten dem Origin-Umbau stand. Die vier CONCERNS
  (Symlink-Listing-Metadaten, Paritäts-Pin selbst-referentiell — Core-Test, `chat ↛ project` im UI
  unmechanisch, Schreib-/Lesewurzeln an zwei Stellen) werden in Inc 5 gehärtet; die Core-Test-Anmerkung
  braucht Pauls Freigabe.

* **❓ Rollen und Rechte** (Vision: Admin = Config/Keys, User = alles außer Config, Reader = nur lesen +
  „Read Jon") — der MVP hat keinen Login (R-WP-17) und kann deshalb nicht unterscheiden: jeder darf
  alles. Empfehlung: Rollen **nicht** im MVP, sondern als eigene Story mit OAuth2 (Vision sagt dasselbe);
  bis dahin ist der Betrieb ein vertrauenswürdiger Kreis (Prototyp kennt nur einen Nutzernamen, R-WP-17).
  *Andersfall:* schon im MVP ein Admin-Passwort = halbherzige Authentifizierung, die ich nicht bauen würde.
* **❓ Story-Import** — wer holt `stories/` und `proposals/` in die Feature-Docs und wann? Bewusst außerhalb des
  Paco-Features ([Paco, Offene Punkte](paco.md)).
