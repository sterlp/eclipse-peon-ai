# Session-Stand — AI Factory

**Modus: NACHTZYKLUS / autonom** (User 2026-10-25, „keine Fragen mehr, bau den Prototyp"). `askUser`
verboten → Fragen als Chat-Nachricht; Lücken als ⏳/❓ in [open-points.md](open-points.md).
**Kein Agent schreibt in `docs/` — ich allein.** Branch `web-factory`.

## Stand 2026-10-26, 09:45 — Inc 3 fertig, Review CONCERNS (kein Blocker), geflippt

| Inc | Inhalt | Commit |
|-----|--------|--------|
| 1 | Registry, Clone, Projects-View, SPA-Auslieferung | `e88a161f` … `220622f8` |
| 2a–2d | Config-Datei-Store im Core, `GET/PUT /api/settings`, Einstellungen-Tab | `b4f57a61` `6d6aff20` `3df46084` `50a35210` |
| 3a | Chat-Kanten: `POST` 202, SSE, Verlauf aus der Core-History, Jon + Team je Projekt | `88de42fb` |
| 3b | Chat-View: Verlauf, Composer, Live-Zeilen | `48636970` |
| 3c-F | **Core-Fix**: `AbstractAgent.call` gab im Wartepfad den Busy-Flag des laufenden Turns frei | `e3638dce` |
| 3c | Queue-Nachweis UC-WP-14/22 (`[Queued Message]`, kein Parallel-Turn) | `683748ae` |
| 3d | Neustart, ein Chat je Projekt, Chat für alle (`ProjectRemoved` → Runtime weg) | `565ec1b9` |
| 3e | Config pro Turn — nächster Turn nutzt den neuen Wert | `eacf02ac` |

Gates: Fabrik **80/0/0**, Core **1376/0/0**, UI **19 passed**, lint 0/0, Linter **12 Befunde, 0 VERWAIST**
(offen: UC-WP-8/9/10, 11/12/13, 18/19, 25, 39/40/41).

**Geflippt (nach Review):** UC-WP-5, 6, 7, 14, 15, 16, 17, 20, 21, 22, 24 sowie R-WP-3, 9, 10, 11, 12,
15, 16 und PR-1…PR-5. **Offen bleibt R-WP-4** (🚧 — Herkunft je Zeile UC-WP-39, nur finale
Team-Antworten UC-WP-40) und **R-WP-5** (UC-WP-41 „ein langsamer Zuschauer bremst den Turn nicht" neu).

## Nächste Schritte

1. **Inc 3-H (Härtung, klein):** Da-Dok-Befunde 1–4 + 9 — `ChatView.test.tsx` `getByRole` → `await
   findByRole` (2 Stellen, ~20 % Flakes), `@PreDestroy` auf den Turn-Executor + Emitter beim Shutdown
   schließen (der Surefire-30-s-Hang kam mit 3a, nicht „vorbestehend"), `ChatStreamRegistry.publish`
   ohne Emitter-I/O unter dem Projekt-Lock (UC-WP-41), leere Subscriber-/Lock-Maps aufräumen,
   Gleichzeitigkeitstest auf den Erst-Kontakt + Mutation `putIfAbsent` → `get`+`put`.
2. **Inc 3f:** Core liefert `origin` je Nachricht (Proposal `agent-origin-proposal.md`), Factory zeigt
   Jon + **nur** finale Team-Antworten (UC-WP-39/40). Vorher Doc-Entscheid: Delegations-Zeile sichtbar,
   Slave-Interna nie; Persistenz der finalen Antwort nach Reload ist ⏳ offen.
3. Danach Inc 4 (Schreib-Pinning + Datei-Bereich), Inc 5 (Kacheln + Slide-out), Inc 6 (Nutzername).
4. Doc-Schuld: `LlmConfigStore`-Absatz in den Core-Docs; `AGENTS-DEV.md` um den Eclipse-`target/classes`-
   Fallstrick ergänzen. Mein Doc-Stand (dieses File, `web-prototype.md`, `index.md`, `open-points.md`,
   `aifactory-architektur.md`) ist noch **uncommitted** und fährt im nächsten Increment-Commit mit.

## Was der User zuletzt geliefert hat

* **`aifactory/pauls-product-vision.md`** (neu, „AI read only", zur Diskussion) — Rollen Admin/User/Reader,
  Slide-out je Agent (nur lesbar), Datei-Slide-out links, Config-Fenster (MCP, Agenten, System-Prompt,
  Codegraph), Docs als Website, „man sieht wer antwortet", OAuth2 später. In [index.md](index.md)
  registriert; Abweichungen als ❓ in open-points (Autorennamen, Rollen, Kachel-Slide-out). Der User
  schreibt daran weiter und will, dass ich bei Fragen hineinlese.
* Sein SPA-Rezept (Blog) liegt als Beleg in `aifactory/peon-plan/ref-sterl-react-spring-boot.md`;
  wir liefern bewusst ohne Forward (R-WP-22).

## Zwei Dinge, die der User noch beantworten sollte (als Chat-Frage gestellt)

1. **Projekt, das „schon ausgecheckt" ist** — (a) nur Factory-Clone wiederverwenden (gebaut) oder (b) fremden
   Checkout von der Platte adoptieren? Bei (b) muss R-WP-2 weichen und Entfernen darf ihn nie löschen.
2. **Wer antwortet im Chat** — Vision sagt „man sieht wer antwortet"; mein ⏳ sagte „kein Autorenname".
   Empfehlung: Agent-Name pro Block ja, Menschenname erst mit Login.

**Beantwortet, nichts zu tun:** „jonkolo / Openclaw" — der User kennt den Namen nicht, Pfad existiert
nirgends; Punkt liegt in open-points, nichts gebaut.
