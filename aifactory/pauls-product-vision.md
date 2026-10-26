Pauls Ideen/Vision zu dem Projekt Jon Web zur Diskussion soweit keine bessere Idee gelten diese.  AI read only

*Open AI Fabric* (alias Web UI, OAF, Jon@Web)

# Ground Rules

- Ziel: Eclipse UI durch ein Web-UI ersetzen. Aber nur "Jon-Mode", keine anderen agenten in der ersten Version
- Fachbereich und Entwicklung Arbeitet mit einem Tool, dewegen im Web.
- Wir nutzen das core projekt -- das sollte unabhängig von Eclipse Plugin und diesem Web UI existieren
- Bugs kleine Änderungen können direkt vom Fachbereich ohne Entwickler gemacht werden


- Cooles UI mit React
- Backend Spring
- Skills sind von allen projekten geteilt
- Es ist eine "Fabrik" -- will ich mehr als eine, installiere ich mehrere instanzen davon
- OAuth2 kommt, aber später
- Etwas anders als im Eclipse Plugin sieht man nur die finale antwort der slaves im Chat Verlauf und natürlich alle nachrichten von Jon -- sozusagen die Tool Results der Agent Calls. Zudem sieht man auch wer antwortet
    - siehe auch: agent-origin-proposal.md für das Eclipse Plugin
- Keine DB wir arbeiten in der einchen ersten Version mit files -- damit könnten wir das config verzeichnis welches wohl auch in git sichern
- wir brauchen ein state verzeichnis für Jons chat
- Jedes Projekt hat ein Verzeichnis. Die write File Tools sind an dieses und an das system tmp verzeihnis gelockt beim schreiben. Info an die agenten die sollen sich ggf. ein tmp/<projekt-name> anlegen 

# Gruppen Rollen und Rechte
- Admin  - darf alles insebsondere die Config, API keys
- Reader - Darf nur lesen und mit "Read Jon" sprechen
- User   - Darf alles außer die Config (API Key, MCP usw.)

# Gobe architektur der Web-Seite

## Projekte Page
- Liste der projekte
- neue GIT projekte einfügen
- Jedes Projekt hat "einen Jon + Team"
- Projekte löschen entfernen das Verzeichniss auf dem Filesystem und die config das wars -- dann kann man es auch wieder einfügen
- In der Übersicht sollte man sehen ob gerade am Projekt gearbeitet wird

## Jon Chat Page -- das wichtigste Projekt Fenster

Hier passiert die Arbeit im Projekt.

- Chat fenster wo man mit Jon sprechen kann
- chat liegt auf dem server sprich ich kann zwischen den Projekten wechseln wenn Jon arbeitet
- Im Chat habe ich oben kacheln für jeden agenten
    - Jeder Agent hat ein compact und ein clear icon
    - Jeder Agent zeigt oben seinen status (arbeit, idle, compacting) und context used an
    - Wenn man das "panel" anklickt vom agenten kommt rechts der Chat Slide out
- Ich kann mit Jon sprechen
- left seide menü slide out um die files zu sehen
- righ light out wenn ich auf einen "slave" klicke, dann sehe ich seinen chat kann aber nicht mit ihm chaten
- chatten geht nur mit jon
- Ein Jon Chat für alle User "statefull" wenn man so will -- alle User sehen den "gleichen" chat
- Jeder User hat ein "read only Jon" für fragen zum Projekt, dieser kann proposals / stories schreiben, sonst nichts -- dieses Chat Fenster gehört aber dem User -- kein anderer User sieht das
- Der Chat im UI representiert den State of dem Server -- man kann zwischen den Projekten springen. Wir streamen aber ins UI den chat wenn es connected ist. Jon ist peristent wie im Eclpse Plugin
- Ein User Chat trägt später auch seine namen -- der Agent sieht "[User Paul]: <user input>" im UI sieht man oben statt YOU, Paul -- solange unbekannt einfach "YOU" wie jetzt auch -- damit kennt Jon den User der mit ihm spricht
   - mini system prompt nötig? "Chat message begins with `[User xxx]:` where `xxx` is the login user."

## Jon Read Page
- Wie Jon aber read ony
- Man kann ein Proposal/Story erstellen -- letztendlich eine md datei im `proposals` bzw. `stories` Verzeichnis
- Read only Jon ist eigentlich als Doku Agent gedacht, wenn man fragen hat -- (Open: sollte ein schwächere AI vielleicht nutzen?)

## Project Docs

- Vielleicht mit Blume? https://github.com/haydenbleasel/blume
- die docs diekt als website rendern und in der UI erreichbar machen
- Damit kann der User die MD files direkt anschauen vom Projekt
- Aber der "read only Jon" wird denke ich wichtiger sein als das

## Config Fenster
- MCP settings
- Agenten Konfiguration
- Alles wird von allen agenten geteilt und Projekten
- System Prompt von Jon und die Agenten "Überschreiben" checkbox default man sieht den Build in Prompt
- Codegraph an/aus -- by default sollte das aber für das projekt immer dann aktiviert wenden wenn wir es auschecken
