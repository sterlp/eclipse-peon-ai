---
idPrefix: WP
---
# Config — Seite und Datei der Factory

## Ziel

Die Factory liest Provider, Modelle und Keys aus **ihrer eigenen** Datei, nie aus dem Plugin-Bestand;
die globale Config-Seite zeigt sie maskiert und darf sie überschreiben — Default für alle, „überschreiben"
je Agent. Woher die Datei kommt (Git-Repo, Home-Aufbau, Sync): [Config-Repo](config-repo.md).

## Regeln

### R-WP-9 — Konfiguration: eigene Datei der Factory, nie der Plugin-Bestand ❌ (Pfad geändert 2026-10-28, R-CR-10; Rest gebaut)

Der Server liest Provider, Modelle und Keys aus **seiner eigenen** Config
(`~/.aifactory/config/config.properties`, Aufbau des Homes: [R-CR-10](config-repo.md); zusätzlich aus
Umgebungsvariablen als Startwert, wenn die Datei den Wert nicht trägt). Der Config-Bestand des Plugins
wird **nicht** gelesen und **nie** geschrieben — er liegt in Eclipse-Präferenzen und ist damit keine
Datei (Konflikt und Begründung: [Architektur](aifactory-architektur.md#konflikt-dokumentiert-2026-10-25)).
Überschreibungen von der Config-Seite landen in derselben Datei der Factory (R-WP-16).

Zwei Angaben kennt die Factory **nur selbst** und liest sie nie aus der Datei: das
**Konfigurationsverzeichnis** (Skills, Agents, Commands und der History-Ordner des Cores) ist immer
das Verzeichnis der Factory — sonst lägen Agent-Zustand und Agenten-Ordner im Bestand des Plugins
(`~/.peon`), was dieser Regel widerspricht — und **`diskToolsEnabled`** ist immer an
([R-WP-12](chat.md)). Wer die beiden Angaben trotzdem von Hand in die Datei schreibt, ändert damit nichts.

Die Datei enthält einen API-Key im Klartext: sie wird mit Eigentümer-Rechten (`600`) angelegt und ihr
Inhalt erscheint **nirgends** unmaskiert (R-WP-16) — weder in einer Antwort noch in einem Log.

#### UC-WP-20 — configReadFromFactoryOwnStore ✅
- GIVEN ein laufender Server WHEN Jon gebaut wird THEN stammen Provider/Modell/Keys aus der Config der
  Factory; der Plugin-Bestand bleibt byte-identisch.
- GIVEN eine fehlende oder unvollständige Config WHEN der Nutzer eine Nachricht sendet THEN sagt die
  App knapp, welche Angabe fehlt, statt mit einer Werkzeugfehler-Meldung zu enden.
- GIVEN ein Provider, der einen Key braucht (alle außer Ollama und LM Studio) WHEN kein Key gesetzt ist
  THEN lehnt die Factory **bevor** ein Modell gebaut wird, und nennt das fehlende Feld.

#### UC-WP-36 — configDirIsAlwaysTheFactoryHome ✅
- GIVEN die Config-Datei trägt von Hand eingetragen ein anderes Konfigurationsverzeichnis (z. B.
  `<plugin-home>/.peon` als absoluter Pfad — die Factory expandiert kein `~`) WHEN der Server die Config
  lädt THEN ist das Konfigurationsverzeichnis trotzdem das Verzeichnis der Factory, und es wird nichts
  aus einem anderen Verzeichnis gelesen oder geschrieben.

### R-WP-16 — Config-Seite ✅ (geschärft 2026-10-28 + 2026-11-02: Key-Entfernen, dynamische Felder gebaut)

Die globale Seite **Config** ([R-WP-1](ui.md)) zeigt **Provider, Modell, URL, Token-Fenster, Timeout,
Think senden** und den **Key-Status** aus der Config der Factory (R-WP-9) und darf diese Werte
**überschreiben** — die Überschreibung liegt in der Datei der Factory, der Bestand des Plugins wird
**nie** geschrieben und nie gelesen. Seit F8 dazu: **Think (Default)** (R-WP-49), das Modell als
**AutoComplete** mit Server-Liste (R-WP-50), **Max Tokens** und **Query-/Header-Params** (R-WP-51)
sowie je Agent **Extra-Body** und **Tools-Allowlist** (R-WP-52).

**Feldliste statt Wunschkonzert:** eine Temperatur existiert im geteilten Bestand **nur je Agent**, nicht
global — ein globales „Temp"-Feld gibt es nicht und wird im MVP nicht erfunden. Was es nicht gibt, ist
nicht ausgegraut, sondern nicht vorhanden. ⏳ Rückversicherung in [Open Points](open-points.md).

**Maskieren heißt: kein Zeichen nach draußen.** Geliefert wird nur, ob ein Key gesetzt ist und woher er
kommt (Datei oder Umgebungsvariable) — nie auch nur ein Zeichen des Keys, auch nicht in Fehlern, Logs
oder der Projektliste. **WEIL** die App keinen Login kennt ([R-WP-17](user.md)), wäre sonst jeder Key
für jeden Betrachter lesbar; ein Key-Auszug ist kein Anzeigefehler, sondern der Fehler.

**Geleert = gelöscht, nur Keys sind ausgenommen** (geschärft Paul 2026-10-28). Sichtbare Werte
(Provider, Modell, URL, Token-Fenster, Timeout, Think; je Agent auch Temperatur) stehen vorbefüllt im
Feld; leert der Nutzer eines und speichert, wird der Wert **aus der Datei gelöscht** — es gilt, was ohne
ihn gilt (beim Default: Umgebungsvariable oder nichts, „Empty means unset"; beim Agenten: der Default,
R-WP-23). Die Datei trägt danach nur, was wirklich gesetzt ist. Ein **Key** ist maskiert und nie
vorbefüllt, sein leeres Feld heißt deshalb **unverändert**; ein Key lässt sich über die Seite **setzen und — via „Remove key" — entfernen**
(geschärft Paul 2026-11-02: sonst war ein gesetzter Key über das UI nie wieder löschbar); eine
ENV-Quelle kann die Seite nur melden, nicht löschen. Key setzen und entfernen in einem Zug gibt es
  nicht: trägt eine Anfrage beides, lehnt die Factory sie als Ganzes ab (400) und speichert nichts;
  im UI hebt Tippen ins Key-Feld eine vorgemerkte Entfernung auf (2026-11-02, aus dem Bau). Der Fehler „turn needs an api key"
([UC-WP-20](#configreadfromfactoryownstore)) nennt die Settings-Seite als Ort der Lösung. Eine Datei, die neu geschrieben
wird, behält ihre Rechte `600` (R-WP-9). Das ist dasselbe Verhalten wie im Plugin — nur speichert die
Factory direkt in ihre Datei.

**Reihenfolge der Werte:** was in der Datei steht, gewinnt gegen die Umgebungsvariable — die Variable
ist der Startwert für den ersten Start, die Datei ist der vom Menschen gepflegte Stand. Woher ein
Wert kommt, zeigt die Seite.

**Dynamische Felder wie im Plugin (Paul 2026-11-02):** der Provider-Wechsel aktualisiert **sofort**
die abhängigen Felder — die **URL** wird auf den Default des gewählten Providers gesetzt (der Core trägt sie), das
**Modell-Feld wird geleert** (das Modell gilt pro Provider; ein übernommener Wert des alten Providers
wäre falsch) und **Think** folgt der Vorgabe des Providers: bei Providern ohne Think-Unterstützung
zeigt die Form das an. Ohne erneutes Speichern; die Form verhält sich wie der
Provider-Dialog im Eclipse-Plugin, nicht wie eine statische Form.

**WEIL** (User 2026-10-25): „der Rest kann aus der config kommen von den files — aber ich mag die im
UI später auch bearbeiten können". Eine eigene Datei statt Rückschreiben hält den Bestand des
Plugins unberührt, den die Factory weder liest noch schreibt (R-WP-9).

#### UC-WP-23 — settingsShowsMaskedConfig ✅
- GIVEN die Config-Seite WHEN sie geöffnet wird THEN stehen Provider, Modell, URL, Token-Fenster,
  Timeout und Think senden, dazu der Key-Status.
- GIVEN ein gesetzter Key WHEN die Seite geladen wird THEN enthält die Antwort die Info „gesetzt" und
  die Quelle, aber **kein** Zeichen des Keys — auch nicht maskiert mit Auszug, auch nicht in einem
  Fehlerfall.

#### UC-WP-24 — settingsOverrideNeverWritesSharedStore ✅
- GIVEN der Nutzer überschreibt ein Feld WHEN er speichert THEN liegt der Wert in der Datei der
  Factory, der Bestand des Plugins ist byte-identisch wie vorher, und die nächste Config-Auslieferung
  zeigt den neuen Wert.
- GIVEN ein gesetzter Key und ein leeres Key-Feld WHEN der Nutzer speichert THEN bleibt der Key
  unangetastet.
- GIVEN ein laufender Turn-Bestand WHEN der Wert gesetzt wurde THEN nutzt der **nächste** Turn den
  neuen Wert (Nachweis mit dem ersten echten Turn, [R-WP-4](chat.md)).

#### UC-WP-38 — configFileWinsOverEnvironment ✅
- GIVEN die Umgebungsvariable und die Datei tragen unterschiedliche Werte WHEN die Config geladen wird
  THEN gilt der Wert aus der Datei, und die Config-Seite nennt die Datei als Quelle.
- GIVEN die Datei trägt keinen Wert WHEN die Umgebungsvariable gesetzt ist THEN gilt der Wert aus der
  Umgebungsvariable und die Quelle wird als solche angezeigt.

#### UC-WP-46 — clearedFieldIsRemovedFromFile ✅
- GIVEN die Datei trägt beim Default `think: high` WHEN der Nutzer das Think-Feld leert und speichert
  THEN steht in der Datei kein `think` mehr und es wird kein Think-Parameter gesendet.
- GIVEN Compact mit Haken und `model: gpt-5`, `think: medium` WHEN der Nutzer das Modell-Feld leert und
  speichert THEN trägt Compacts Eintrag nur noch `think: medium`; das Modell erbt Compact vom Default.

#### UC-WP-62 — providerChangeUpdatesDependentFields ✅
- GIVEN die Config-Seite WHEN der Nutzer den Provider wechselt THEN aktualisieren sich Modell und URL
  sofort auf die Defaults des gewählten Providers (URL), geleert (Modell) bzw. auf dessen Vorgabe
  (Think) — ohne erneutes Speichern.

#### UC-WP-63 — keyCanBeRemovedAndStatusRefreshes ✅
- GIVEN ein gesetzter Key aus der Datei WHEN der Nutzer „Remove key" wählt und speichert THEN steht
  in der Datei kein Key mehr, und der Status zeigt „nicht gesetzt".
- GIVEN ein Key aus einer Umgebungsvariable WHEN die Seite geladen wird THEN meldet sie die Quelle
  ENV — entfernen über die Seite ist dann nicht möglich.

### R-WP-23 — Agenten auf der Config-Seite: Default für alle, „überschreiben" je Agent ✅ (Dev-Slot-Lücke: ⏳ in [Open Points](open-points.md))

Die Config-Seite listet die **sechs Slots, die die Factory fährt** — die Agenten Jon, Plan, Dev,
Review, Search und den Config-Slot **Compact** (kein Agent, gleiche Bedienung) unter dem Default. Je Agent gibt es genau eine
Checkbox **„überschreiben"** — reine Bedienhilfe; nur bei gesetztem Haken werden die
Config-Felder dieses Agenten eingeblendet. Der Haken ist **kein gespeicherter Wert**: er ist gesetzt,
sobald die Agent-Datei mindestens ein Config-Feld trägt (Modell, URL, Key, Think, Temperatur,
Extra-Body, Tools … — nicht id/name/description; der Provider ist kein Agent-Feld) ([Agent-Dateien](../../org.sterl.llmpeon.core/docs/builtin-agent-prompt-tracked.md), V2).

- Haken gesetzt: ein leeres Feld erbt den Default (Feld für Feld, wie R-DEF-1/2 im Plugin); Keys bleiben
  maskiert wie in R-WP-16.
- **Think folgt der Form des Basis-Providers** — die Form entscheidet über das **Angebot**, das Feld
  selbst ist aber **editierbar** (L4, Paul 2026-11-10: „ich kann nichts selber eingeben"; Plugin-Beleg:
  editierbares `Combo` mit `SWT.BORDER`, `ThinkValueSupport.java` — „editable combo", Wert verbatim):
  **Toggle** (Angebot leer / `true` / `false`) · **Values** (Angebot: die feste Liste des Providers) ·
  **FreeString** (Text) · **None** (weiterhin kein Feld, nur der Hinweis) — wie im Plugin gibt es für
  Toggle und Values **ein** editierbares Feld: Auswahl aus dem Angebot **oder** eigene Eingabe. Einen
  Provider je Agent gibt es nicht (oben), also entscheidet immer die Basis. Ein gespeicherter Wert
  außerhalb der Liste **bleibt verbatim** erhalten — das Feld bietet ihn als zusätzlichen Eintrag an,
  er ersetzt ihn nicht (ADR-0064); leer erbt den Default
  ([R-THINK-10](../../org.sterl.llmpeon.core/docs/per-agent-think.md)).
  Im **Dev-Slot** heißt das Feld **`Think (Default)`**, weil der Dev-Record im Core die Basis-Semantik
  für alle anderen Agenten trägt (R-THINK-10).
- Haken entfernt + Speichern: alle Config-Werte dieses Agenten werden **aus seiner Datei gelöscht** —
  der Agent ist wieder „Default". Der Prompt-Rumpf bleibt unberührt. Bewusst anders als R-DEF-11 im
  Plugin (dort bleibt ein versteckter Wert erhalten).

**WEIL** (Paul 2026-10-28): local first heißt meist ein oder zwei Modelle — der Default reicht für fast
alle Agenten. Die Seite zeigt nur die Agenten, die man wirklich anders will, statt aller Felder aller
Agenten; was nicht sichtbar ist, wirkt auch nicht.

#### UC-WP-44 — overrideCheckboxShowsAgentFields ✅
- GIVEN die Config-Seite und Dev ohne Config-Felder in seiner Datei WHEN sie geöffnet wird THEN ist
  Devs Haken „überschreiben" leer und keine Dev-Felder sind sichtbar; Dev nutzt den Default.
- GIVEN Dev WHEN der Nutzer den Haken setzt, nur das Modell `gpt-5` einträgt und speichert THEN trägt
  Devs Datei nur `model: gpt-5`, alle übrigen Werte erbt Dev vom Default, und beim nächsten Öffnen ist
  der Haken gesetzt.

#### UC-WP-45 — uncheckOverrideDeletesAgentValues ✅
- GIVEN Devs Datei trägt `model: gpt-5` und einen Prompt-Rumpf WHEN der Nutzer den Haken entfernt und
  speichert THEN trägt die Datei kein Config-Feld mehr, der Rumpf ist unverändert, Dev nutzt den Default
  und der Haken ist beim nächsten Öffnen leer.

#### UC-WP-67 — agentThinkFollowsTheProviderForm ✅ (L4 `c44a057c`: editierbar)
- GIVEN die Config-Seite mit Basis-Provider `OLLAMA` WHEN ein Slot überschreibt THEN ist Think ein
  **editierbares Feld** — Angebot leer / `true` / `false`, **Auswahl oder eigene Eingabe** (L4).
- GIVEN ein Basis-Provider mit fester Werte-Liste (z. B. `ANTHROPIC` → `adaptive`, `enabled`) WHEN der
  Slot trägt bereits `ultra` THEN zeigt die Auswahl `ultra` als zusätzlichen Eintrag, und Speichern
  schreibt `ultra` unverändert in die Datei (kein stiller Ersatz, ADR-0064).
- GIVEN ein Provider ohne Think-Eingang (`GOOGLE_GEMINI`, `MISTRAL`) WHEN ein Slot überschreibt THEN
  steht kein Think-Feld, nur der Hinweis; der gespeicherte Wert bleibt erhalten und wird mitgesendet.
- GIVEN der Dev-Slot WHEN seine Felder sichtbar THEN heißt das Think-Feld `Think (Default)`, weil Devs
  Record die Basis für alle anderen ist (R-THINK-10).

## F8 — Plugin-Parität der Config (2026-11-09, Nachtzyklus)

Ziel der Runde: die Config kennt, was das Eclipse-Plugin einstellt — ohne Eclipse-Eigenes (Copilot-
Login, Shell-Confirmation, MCP, Voice, Ping, debugMode/showRealtime bleiben draußen: im Web gibt es
keine Eclipse-Präferenzen, der Web-Chat streamt immer). Belege der Delta-Bestimmung:
`peon-plan/f8-survey.md`.

### R-WP-49 — Think (Default) auf der Default-Seite ✅ (gebaut+reviewed 2026-11-09)

Die Default-Seite zeigt zusätzlich **„Think (Default)"** — derselbe Wert wie das Dev-Slot-Feld
(R-WP-23): der Think-Wert, der je Request als Thinking/`reasoning_effort` geht. Es gilt **dieselbe
Form des Basis-Providers** wie je Agent — Toggle · Values · FreeString · None ([UC-WP-67](#uc-wp-67)),
und das Feld ist **editierbar** wie je Agent (L4 `c44a057c` — Auswahl oder eigene Eingabe,
Plugin-Combo),
ein gespeicherter Wert außerhalb der Liste bleibt verbatim (ADR-0064), leeren löscht ihn aus der Datei
(UC-WP-46). Beide Orte zeigen **einen** Wert: die Default-Seite liest und schreibt das Dev-Slot-Feld,
die Agent-Sektion bleibt wie sie ist — es gibt keinen zweiten Speicherplatz.

Unabhängig davon bleibt **„Think senden"** bestehen — es entscheidet, ob das **vorherige Thinking des
Modells** in den nächsten Request zurückgesendet wird, nicht mit welcher Stärke gedacht wird. Auch das
Plugin hat beide Angaben nebeneinander; das eine ersetzt das andere nicht, und ein leeres
Think-Feld ändert den Schalter nicht.

#### UC-WP-85 — defaultThinkFollowsTheProviderForm ✅ (L4 `c44a057c`: editierbar)
- GIVEN die Default-Seite mit einem Basis-Provider, dessen Think-Form `VALUES` ist WHEN sie geöffnet
  wird THEN ist „Think (Default)" ein **editierbares Feld** — Angebot: die Werte-Liste des Providers,
  **Auswahl oder eigene Eingabe** (L4).
- GIVEN die Datei trägt einen Think-Wert außerhalb der Liste WHEN die Seite lädt THEN bietet die
  Auswahl ihn als zusätzlichen Eintrag an, und Speichern schreibt ihn unverändert (ADR-0064).
- GIVEN ein Provider ohne Think-Eingang WHEN die Seite lädt THEN steht kein Think-Feld, nur der
  Hinweis; ein gespeicherter Wert bleibt erhalten und wird mitgesendet.
- GIVEN „Think (Default)" ist leer und „Think senden" steht auf an WHEN gespeichert wird THEN gilt
  beides unabhängig: in der Datei steht kein Think, der Schalter bleibt an.

### R-WP-50 — Modell-Feld = AutoComplete mit Server-Liste ✅ (gebaut+reviewed 2026-11-09)

Die Modell-Felder (Default-Seite und je Agent-Sektion) sind **AutoComplete** — Auswahl aus der
Modell-Liste des Providers **oder freie Eingabe** (Paul 2026-11-08; nicht jeder Provider hat eine
Liste). Die Liste liefert die Factory **serverseitig** über die Modell-Listen-Fähigkeit des Core
(`listAiModels`), geholt für die **effektive Verbindung**: Provider und URL aus dem laufenden Entwurf,
der Key nur serverseitig aus der Config — er reist nie zum Client (R-WP-16). Geholt wird beim Öffnen
bzw. Provider-Wechsel und über **Refresh**; ein fehlgeschlagener oder leerer Fetch ändert nichts —
der Freitext bleibt möglich, es gibt keinen Auto-Wechsel. **Kein Append:** die Eingabe wird nie als
Eintrag ergänzt; entspricht der Feldtext einem Eintrag (ohne Groß-/Kleinschreibung), gewinnt der
kanonische Eintrag des Servers. Ein gespeichertes Modell, das nicht in der Liste steht, bleibt
unangetastet stehen.

#### UC-WP-86 — modelFieldOffersListAndFreeText ✅
- GIVEN ein Provider mit Modell-Liste WHEN das Modell-Feld bedient wird THEN bietet es die Modelle der
  effektiven Verbindung an, und eine eigene Eingabe bleibt zulässig und speicherbar.
- GIVEN der Listen-Fetch scheitert oder liefert leer WHEN das Feld bedient wird THEN verhält es sich
  wie ein freies Textfeld — kein Fehlerblocker, kein Auto-Wechsel.
- GIVEN die Datei trägt ein Modell, das nicht in der Liste steht WHEN die Liste ankommt THEN bleibt
  der gespeicherte Wortlaut im Feld stehen.
- GIVEN der Nutzer tippt einen Listeneintrag ohne Groß-/Kleinschreibung exakt THEN wählt das Feld den
  kanonischen Eintrag des Servers, nicht den getippten Wortlaut.

### R-WP-51 — Advanced der Default-Seite: Max Tokens, Query- und Header-Params ✅ (gebaut+reviewed 2026-11-09)

Die Default-Seite bekommt **Max Tokens** (leer/gelöscht = Provider-Default), **Query-Params** und
**Header-Params** (je CSV `k=v,k2=v2`) — Angaben, die der Core auf jede Provider-Request anwendet
(`maxTokens`, `queryParams`, `headerParams`). Drei-Zustände wie überall ([R-WP-16](#r-wp-16)):
fehlend lässt die Datei in Ruhe, leer löscht den Wert, ein Wert setzt ihn. Zahlen reisen als Text und
werden serverseitig geparsed; ein unparsbarer Wert kommt als 400 mit Feldnamen zurück, und es wird
nichts gespeichert.

#### UC-WP-87 — advancedBaseFieldsSaveAndClear ✅
- GIVEN Max Tokens `4096` WHEN gespeichert und der nächste Turn läuft THEN trägt der Request das
  Output-Limit; WHEN das Feld geleert und gespeichert wird THEN steht in der Datei kein `maxTokens`
  mehr und es gilt der Provider-Default.
- GIVEN Query-Params `a=b,c=d` WHEN gespeichert THEN trägt jede Request-URL beide Parameter; geleert
  und gespeichert sind sie aus der Datei gelöscht.
- GIVEN Header-Params `k=v,k2=v2` WHEN gespeichert THEN trägt jede Provider-Request beide Header.
- GIVEN ein unparsbarer Wert (z. B. Max Tokens `abc`) WHEN gespeichert wird THEN antwortet die Factory
  mit 400 und dem Feldnamen, und die Datei ist unverändert.

### R-WP-52 — Je Agent: Extra-Body und Tools-Allowlist ✅ (gebaut+reviewed 2026-11-09)

Je Agent-Sektion (R-WP-23) kommen **Extra-Body** (JSON) und **Tools-Allowlist** (CSV, leer = alle
Tools) hinzu — der Core trägt beide Felder je Agent (`extraBody`, `tools`), und die Agent-Datei der
Factory schreibt sie ab jetzt. Das frühere „Extra-Body bewusst nicht" gilt hiermit als zurückgenommen
(Plugin-Parität, F8). Der Override-Haken folgt der bisherigen Regel: trägt die Datei Extra-Body oder
Tools, ist er gesetzt; Haken entfernt + Speichern löscht auch diese Werte (UC-WP-45). Der Extra-Body
folgt der Provider-Fähigkeit wie im Plugin: bei einem Provider ohne Extra-Body-Unterstützung steht
kein Feld, ein gespeicherter Wert bleibt erhalten und wird mitgesendet. Ein Extra-Body, der kein
JSON-Objekt ist, wird mit 400 und Feldnamen abgelehnt — nichts wird gespeichert (der Core würde ihn
sonst still verwerfen). Eine **Vorschau** zeigt zu
einer Allowlist, welche der dem Core bekannten Tools damit laufen.

#### UC-WP-88 — agentExtraBodyAndToolsSaveInAgentFile ✅
- GIVEN Devs Override mit Extra-Body `{"top_p": 0.9}` WHEN gespeichert und der nächste Dev-Turn läuft
  THEN trägt Devs Datei `extra_body` und der Request sendet ihn.
- GIVEN Devs Tools-Allowlist `disk_read, disk_write` WHEN gespeichert THEN läuft Devs nächster Turn mit
  genau diesen Tools; WHEN das Feld geleert und gespeichert wird THEN ist die Allowlist aus der Datei
  gelöscht und es laufen alle Tools.
- GIVEN der Override-Haken wird entfernt und gespeichert WHEN Devs Datei Extra-Body und Tools trug
  THEN sind auch diese beiden Werte aus der Datei gelöscht, der Prompt-Rumpf bleibt unberührt.
- GIVEN eine Allowlist WHEN die Vorschau angezeigt wird THEN nennt sie genau die Tools, die damit
  laufen — nicht mehr und nicht weniger.

