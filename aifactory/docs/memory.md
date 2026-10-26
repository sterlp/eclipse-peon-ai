# Session-Stand — AI Factory

**Modus: NACHTZYKLUS / autonom** (User 2026-10-25, „keine Fragen mehr, bau den Prototyp"). `askUser`
verboten → Fragen als Chat-Nachricht; Lücken als ⏳/❓ in [open-points.md](open-points.md).
**Kein Agent schreibt in `docs/` — ich allein.** Branch `web-factory`.

## Stand 2026-10-25, 21:30 — Inc 2 fertig, abgenommen; Inc 3 ist der nächste Schritt

| Inc | Inhalt | Commit | grün |
|-----|--------|--------|------|
| 1 + 1-R + 1-C + 1-U | Registry, Clone, Projects-View, SPA-Auslieferung | `e88a161f` `5ee19173` `9711107e` `220622f8` | UC-WP-2,3,4,26–35 |
| 2a | `FileLlmConfigStore` im Core | `b4f57a61` | 1375 Core-Tests |
| 2b | Read-Pfad `config` + `GET /api/settings` + Einstellungen-Tab | `6d6aff20` | 55 Factory-Tests |
| 2c | Schreib-Pfad `PUT /api/settings` | `3df46084` | dito |
| 2d | Da-Dok-Auflagen (6 Punkte) | `50a35210` | 55 / 14 UI / lint 0 |

**Abgenommen:** UC-WP-23, 36, 37, 38 sind ✅ (Status im Doc geflippt, `lintDocsAndTests` davor:
18 Befunde, **0** `UNBELEGT_ERLEDIGT`). R-WP-9/12/16 bleiben ❌, weil UC-WP-20/17/24 noch offen sind —
UC-WP-24 hängt an GIVEN 3 („der nächste Turn sieht den neuen Wert") und das ist Inc 3.

Plan: `aifactory/peon-plan/overview.md` — Inc 2 abgeschlossen; `planImplemented` steht noch aus
(Inc-2-Abschnitt archivieren, `## 2d-E` bleibt als Log).

## Nächste Schritte

1. **Inc 3 planen** (Runtime + erster echter Turn + SSE + History) → schließt UC-WP-20, UC-WP-24/GIVEN 3,
   UC-WP-6/7/14/15/16. Vor dem Plan: `aifactory-architektur.md#runtime` und R-WP-4/R-WP-10/R-WP-11 lesen.
2. Danach Inc 4 (Schreib-Pinning + Datei-Bereich), Inc 5 (Kacheln + Slide-out), Inc 6 (Nutzername).
3. Doc-Schuld: kurzer Absatz zu `LlmConfigStore` in den Core-Docs (`provider.md` kennt das Interface nicht).
4. Meine Doc-Änderungen (dieses File, `web-prototype.md`, `open-points.md`, `aifactory-architektur.md`,
   `index.md`) fahren im nächsten Increment-Commit mit.

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
