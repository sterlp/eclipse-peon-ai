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

### R-WP-16 — Config-Seite ❌ (geschärft 2026-10-28: UC-WP-46 offen, Rest gebaut)

Die globale Seite **Config** ([R-WP-1](ui.md)) zeigt **Provider, Modell, URL, Token-Fenster, Timeout,
Think senden** und den **Key-Status** aus der Config der Factory (R-WP-9) und darf diese Werte
**überschreiben** — die Überschreibung liegt in der Datei der Factory, der Bestand des Plugins wird
**nie** geschrieben und nie gelesen.

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
vorbefüllt, sein leeres Feld heißt deshalb **unverändert**; ein Key lässt sich über die Seite nur setzen,
nicht löschen (Löschen = „überschreiben" abwählen oder Datei von Hand). Eine Datei, die neu geschrieben
wird, behält ihre Rechte `600` (R-WP-9). Das ist dasselbe Verhalten wie im Plugin — nur speichert die
Factory direkt in ihre Datei.

**Reihenfolge der Werte:** was in der Datei steht, gewinnt gegen die Umgebungsvariable — die Variable
ist der Startwert für den ersten Start, die Datei ist der vom Menschen gepflegte Stand. Woher ein
Wert kommt, zeigt die Seite.

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

#### UC-WP-46 — clearedFieldIsRemovedFromFile ❌
- GIVEN die Datei trägt beim Default `think: high` WHEN der Nutzer das Think-Feld leert und speichert
  THEN steht in der Datei kein `think` mehr und es wird kein Think-Parameter gesendet.
- GIVEN Compact mit Haken und `model: gpt-5`, `think: medium` WHEN der Nutzer das Modell-Feld leert und
  speichert THEN trägt Compacts Eintrag nur noch `think: medium`; das Modell erbt Compact vom Default.

### R-WP-23 — Agenten auf der Config-Seite: Default für alle, „überschreiben" je Agent ❌

Die Config-Seite listet **alle Agenten** (Jon, Plan, Dev, Review, Search, Paco, Custom Agents) und den
Config-Slot **Compact** (kein Agent, gleiche Bedienung) unter dem Default. Je Agent gibt es genau eine
Checkbox **„überschreiben"** — reine Bedienhilfe; nur bei gesetztem Haken werden die
Config-Felder dieses Agenten eingeblendet. Der Haken ist **kein gespeicherter Wert**: er ist gesetzt,
sobald die Agent-Datei mindestens ein Config-Feld trägt (Provider, Modell, URL, Key, Think, Temperatur …
— nicht id/name/description) ([Agent-Dateien](../../org.sterl.llmpeon.core/docs/builtin-agent-prompt-tracked.md), V2).

- Haken gesetzt: ein leeres Feld erbt den Default (Feld für Feld, wie R-DEF-1/2 im Plugin); Keys bleiben
  maskiert wie in R-WP-16.
- Haken entfernt + Speichern: alle Config-Werte dieses Agenten werden **aus seiner Datei gelöscht** —
  der Agent ist wieder „Default". Der Prompt-Rumpf bleibt unberührt. Bewusst anders als R-DEF-11 im
  Plugin (dort bleibt ein versteckter Wert erhalten).

**WEIL** (Paul 2026-10-28): local first heißt meist ein oder zwei Modelle — der Default reicht für fast
alle Agenten. Die Seite zeigt nur die Agenten, die man wirklich anders will, statt aller Felder aller
Agenten; was nicht sichtbar ist, wirkt auch nicht.

#### UC-WP-44 — overrideCheckboxShowsAgentFields ❌
- GIVEN die Config-Seite und Dev ohne Config-Felder in seiner Datei WHEN sie geöffnet wird THEN ist
  Devs Haken „überschreiben" leer und keine Dev-Felder sind sichtbar; Dev nutzt den Default.
- GIVEN Dev WHEN der Nutzer den Haken setzt, nur das Modell `gpt-5` einträgt und speichert THEN trägt
  Devs Datei nur `model: gpt-5`, alle übrigen Werte erbt Dev vom Default, und beim nächsten Öffnen ist
  der Haken gesetzt.

#### UC-WP-45 — uncheckOverrideDeletesAgentValues ❌
- GIVEN Devs Datei trägt `model: gpt-5` und einen Prompt-Rumpf WHEN der Nutzer den Haken entfernt und
  speichert THEN trägt die Datei kein Config-Feld mehr, der Rumpf ist unverändert, Dev nutzt den Default
  und der Haken ist beim nächsten Öffnen leer.
