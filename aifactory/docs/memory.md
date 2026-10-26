# Session-Stand — AI Factory

**Modus: NACHTZYKLUS / autonom** (User 2026-10-25, „keine Fragen mehr, bau den Prototyp"). `askUser`
verboten → Fragen als Chat-Nachricht; Lücken als ⏳/❓ in [open-points.md](open-points.md).
**Kein Agent schreibt in `docs/` — ich allein.** Branch `web-factory`.

## Stand 2026-10-26, 17:00 — Inc 3 **und** 3f komplett, reviewt, abgenommen

| Inc | Inhalt | Commit |
|-----|--------|--------|
| 1 | Registry, Clone, Projects-View, SPA-Auslieferung | `e88a161f`…`220622f8` |
| 2a–2d | Config-Datei-Store im Core, `GET/PUT /api/settings`, Einstellungen-Tab | `b4f57a61` `6d6aff20` `3df46084` `50a35210` |
| 3a–3e | Chat: drei Kanten, View, Queue, Neustart, Config je Turn | `88de42fb` `48636970` **`e3638dce`** `683748ae` `565ec1b9` `eacf02ac` |
| 3-H1/H2 | UI in Komponenten; Härtung (Outbox, `ContextClosedEvent`) | `eb642e3e` `ec13b2e4` |
| 3f-1 | **Core**: `origin` je Zeile, `OriginMonitor`, `onAgentCallCompleted`/`onDelegatedAnswer`, Team-Antwort als Attribut | `9ece7b89` |
| 3f-2 | Fabrik filtert Mitglieder-Interna, `done` nur vom äußersten Agenten, `AgentNames` | `f3d61f6f` |
| 3f-3 | `delegated`-Ereignis live + Verlauf mit Herkunft (auch nach Neustart) | `cab87925` |
| 3f-4 | UI: Urheber je Zeile, Team-Antwort als eigene dauerhafte Zeile | `ab1966ad` |
| 3f-5-H | erschöpfender SSE-`switch`, Draht-Namen festgenagelt, zweite Delegation belegt | `acab5b20` |

Gates: **Kern 1386/0/0**, **Fabrik 96/0/0**, **UI 22 Tests / 3 Dateien**, lint 0/0, build ✓,
`lintDocsAndTests` `UC 41/41; test IDs 67/32; findings 9`, **VERWAIST 0**.

**Offen (9 UNBELEGT):** UC-WP-8/9/10 (Schreib-Pinning, Inc 4) · UC-WP-18/19 (Datei-Bereich, Inc 4) ·
UC-WP-11/12/13 (Kacheln/Clear/Compact, Inc 5) · UC-WP-25 (Nutzername, Inc 6). Dazu zehn UC-JL
(Jon Light, eigener Plan).

## Was in 3f entschieden wurde (nicht wieder aufmachen)

* **Herkunft**: Core stempelt am Ort der Erzeugung (Kern-Id), Fabrik bildet auf Anzeigenamen ab
  (`AgentNames`, Jon = „Jon"), unbekannt ⇒ `null`, nie geraten. Feld heißt `origin`.
* **Attribut-Weg ist gemessen**: `delegatedAgent`/`delegatedAnswer` überleben
  `ChatMessageSerializer`→`Deserializer` (Test bleibt im Kern) ⇒ Team-Antwort liegt in **Jons** History,
  kein zweiter Speicher (R-WP-11). Der ⏳-Punkt in open-points ist damit erledigt, GIVEN 3 von UC-WP-40
  hängt daran.
* **Vierter/fünfter Draht-Name `delegated`** (`{origin,text}`); `done` **nur** aus
  `onAgentCallCompleted` (behebt F1: jedes Mitglieds-Ende sendete vorher ein `done`).
* **Live gilt nur Jon**: ein Mitglieds-Ende ist kein Turn-Ende; Mitglieder-Interna (Denken, Werkzeug,
  Teilantwort) sind live **und** im Verlauf unsichtbar.
* **Der `OriginMonitor` stempelt auch die abgeleiteten Callbacks** (`onTool`/`onProblem`/`onFileUpdate`),
  aber roh weiterreichen nur an einen Delegate **ohne** eigenen Override (`forwardsRaw`, Reflection) und
  nie an einen verschachtelten Umschlag — sonst färbt der äußere die Zeile des inneren um. Plugin-Parität
  (`AIChatView:395` überschreibt `onFileUpdate`).
* **Live-Fragment trägt keinen Namen** — die dauerhafte Zeile schon (in R-WP-4 klargestellt, ⏳ in
  open-points zur Rückversicherung).
* **Delegation = Werkzeug-Zeilen (Start + Abschluss) plus eine finale Antwort** — R-WP-4 sagt jetzt
  „Zeilen", nicht „eine Zeile" (vorher Widerspruch zu dem, was 3a zeigt).

## Fallstricke (gemessen, nicht neu ableiten)

* Baureihenfolge: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify` (sonst baut die
  Fabrik gegen das alte `~/.m2`; in `AGENTS-DEV.md` festgehalten).
* Eclipse schreibt Lombok-Stümpfe in `target/classes` ⇒ **immer** `clean`; Eclipse meldet Getter als
  „undefined", Maven ist die Wahrheit.
* `MockLlmServer`: kein `getRequestCount()`; `getCapturedMessages()` fängt nie `system`-Nachrichten;
  `stop()` fährt seinen Pool nicht herunter (offen, Kern).
* Fehlt in `toSse` ein Zweig, tötet die Auslieferung den Zustell-Thread des Zuschauers — ab 3f-5-H
  bricht stattdessen die Kompilierung (erschöpfender `switch`).
* `MockLlmServer`-Tool-Attrappen: `@P("…")` ist die **Beschreibung**, der Name braucht `@P(name=…)`
  (kein `-parameters`).
* Guards: `OneChatPerProjectTest`/`SharedProjectChatTest` prüfen die **erschöpfliche** DTO-Feldliste
  (`role,text,origin`) **und** eine Verbotliste (`chatId…author/owner`) — beides unangetastet lassen.
* Port 8080 gehört der IDE → Nachweise auf **8099**; `~/.peon` vor/nach vergleichen.

## Nächste Schritte

0. **Plan geteilt 2026-10-26** — Grund: `overview.md` war 3216 Zeilen, der Plan-Agent lief in die
   Context-Grenze (210638 Tokens, keine Kürzung). Inc 1–3f liegen in
   `peon-plan/overview-inc3f-done-2026-10-26.md` (+ `overview-done-2026-10-25-23-07.md`), `overview.md`
   trägt nur Inc 4 (704 Zeilen). **Regel für jeden Zyklus-Schluß: erledigten Teil archivieren, nicht anhängen.**
1. **Inc 4** — Plan §4.0…§4.11 **angenommen**, A1–A6 beantwortet (`ANSWERED 2026-10-26`). Reihenfolge
   4a-1 (nur Messung, kein Commit) → 4a-2 (Core R6 + Plugin-Compile-Gate) → 4a-3 (Fabrik pinnt Jon **und**
   Da Mek) → 4b-1 (`ProjectFilesController` + DTOs) → 4b-2 (UI-Slot + Vitest, **A5: Browser zusätzlich**).
2. **Inc 5** — Kacheln je Agent, Clear/Compact (UC-WP-11/12/13), Slide-out je Agent
   ([Produktvision](../pauls-product-vision.md)).
3. **Inc 6** — Nutzername (UC-WP-25) und damit der Menschenname auf `user`-Zeilen (R-WP-17).
4. Doc-Schuld: `LlmConfigStore`-Absatz in den Core-Docs (dort fehlt der Store ganz).
5. Uncommitted und bewußt so: `pauls-product-vision.md`, `AGENTS*.md`, `CHANGELOG.md`, `peon-plan/`.

## Zwei Dinge, die der User noch beantworten sollte (als Chat-Frage gestellt)

1. **Projekt, das „schon ausgecheckt" ist** — nur Factory-Clone (gebaut) oder fremden Checkout adoptieren?
   Bei Adoption muß R-WP-2 weichen und Entfernen darf ihn nie löschen.
2. **Wer antwortet im Chat** — beantwortet: Agent-Name ja (3f), Menschenname erst mit Inc 6.
