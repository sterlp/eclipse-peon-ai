# Session-Stand — AI Factory

**Modus: NACHTZYKLUS / autonom** (User 2026-10-25, „keine Fragen mehr, bau den Prototyp"). `askUser`
verboten → Fragen als Chat-Nachricht; Lücken als ⏳/❓ in [open-points.md](open-points.md).
**Kein Agent schreibt in `docs/` — ich allein.** Branch `web-factory`.

## Stand 2026-10-25, 20:50 — Inc 2a grün, 2b noch nicht freigegeben

| Inc | Inhalt | Commit | grün |
|-----|--------|--------|------|
| 1 + 1-R + 1-C + 1-U | Registry, Clone, Projects-View, SPA-Auslieferung | `e88a161f` `5ee19173` `9711107e` `220622f8` | UC-WP-2,3,4,26–35 |
| **2a** | **`FileLlmConfigStore` im Core** (Datei-Store für die Factory, deterministische Bytes, atomar, `600`) | `b4f57a61` | 1375 Core-Tests, 13 neue |

Plan: `aifactory/peon-plan/overview.md` — oben der RELEASENTWURF Inc 2 (2a/2b/2c), darunter Inc-1-Archiv.

**Offen in Inc 2:** 2b = Factory-Read-Pfad (`config`-Komponente, `GET /api/settings`, Einstellungen-Tab,
UC-WP-23/36/37/38) · 2c = Schreib-Pfad (`PUT`, UC-WP-24) · dann Review durch Da Dok + meine Messung,
dann `lintDocsAndTests` und **erst dann** ❌→✅.

## Was der User zuletzt geliefert hat

* **`aifactory/pauls-product-vision.md`** (neu, „AI read only", zur Diskussion) — Rollen Admin/User/Reader,
  Slide-out je Agent (nur lesbar), Datei-Slide-out links, Config-Fenster (MCP, Agenten, System-Prompt,
  Codegraph), Docs als Website, „man sieht wer antwortet", OAuth2 später. In [index.md](index.md)
  registriert; Abweichungen als ❓ in open-points (Autorennamen, Rollen, Kachel-Slide-out). Der User
  schreibt daran weiter und will, dass ich bei Fragen hineinlese.
* Sein SPA-Rezept (Blog) liegt als Beleg in `aifactory/peon-plan/ref-sterl-react-spring-boot.md`;
  wir liefern bewusst ohne Forward (R-WP-22).

## Nächste Schritte

1. **2b freigeben** (Da Mek, nach `compactDev`), dann 2c.
2. Danach Review (Da Dok, drei Seiten) + meine Messung: Boot mit `AIFACTORY_HOME=/tmp/...`, `curl` der
   Settings, `ls -l` auf `600`, `~/.peon` vorher/nachher unverändert.
3. **Doc-Schuld:** ein kurzer Core-Doc-Absatz zum Datei-Store (in `provider.md` oder
   `peon-config-directory.md` steht `LlmConfigStore` bisher **gar nicht** — geprüft 2026-10-25).
4. Commit der Docs (`index.md`, `web-prototype.md`, `open-points.md`, diese Datei) fährt im 2b-Commit mit,
   wie geschrieben — `AGENTS-DEV.md`, `CHANGELOG.md`, `peon-plan/` bleiben draußen.
5. Danach Inc 3 (Runtime + erster Turn + SSE + History) — schließt UC-WP-20, UC-WP-24/3. GIVEN, UC-WP-6/7/14/15/16.

## Zwei Dinge, die der User noch beantworten sollte (als Chat-Frage gestellt)

1. **Projekt, das „schon ausgecheckt" ist** — (a) nur Factory-Clone wiederverwenden (gebaut) oder (b) fremden
   Checkout von der Platte adoptieren? Bei (b) muss R-WP-2 weichen und Entfernen darf ihn nie löschen.
2. **Wer antwortet im Chat** — Vision sagt „man sieht wer antwortet"; mein ⏳ sagte „kein Autorenname".
   Empfehlung: Agent-Name pro Block ja, Menschenname erst mit Login.

**Beantwortet, nichts zu tun:** „jonkolo / Openclaw" — der User kennt den Namen nicht, Pfad existiert
nirgends; Punkt liegt in open-points, nichts gebaut.
