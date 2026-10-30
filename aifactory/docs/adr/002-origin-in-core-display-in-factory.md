# AF-ADR-002 — Herkunft und Team-Antwort: kein zweiter Monitor-Kanal

**Status:** Accepted (2026-10-27, Paul) — ersetzt den Inc-3f-Bau vom 2026-10-26 vollständig.
Teile (Core-Weg, `origin`-Felder) sind seit [ADR-0078](../../../docs/adr/0078-origin-in-core-owner-monitor.md)
(2026-10-29) abgelöst.

## Context

Der Chat soll **nur** Jon und die **finalen** Antworten seines Teams zeigen, jede dauerhafte Zeile mit
ihrem Urheber (R-WP-4, UC-WP-39/40). Der Core reicht Jons Monitor ungefiltert an jedes delegierte
Mitglied weiter — Mitglieder-Events kommen also ohne Absender bei Jons Zuhörer an.

Inc 3f (2026-10-26) hat das im Core mit **zusätzlichen Kanälen** gelöst: `OriginMonitor` (Umschlag um
jeden Turn, Reflection-Weiche für Overrides), `onAgentCallCompleted` (zweites Turn-Ende neben
`onCallCompleted`), `onDelegatedAnswer` + `ToolResultAttributes` + `AbstractTool.resultAttributes`
(Team-Antwort als eigenes Event und als Attribut in der History). Das Plugin nutzte keinen davon.

## Decision

1. **Alles aus Inc 3f-1 fliegt aus dem Core** (`OriginMonitor`, `onAgentCallCompleted`,
   `onDelegatedAnswer`, `ToolResultAttributes`, `resultAttributes`). Das Feld `origin` an
   `SimpleMessage`/`OnPartialAiResponse` blieb zunächst ungenutzt — seit ADR-0078 stempelt der Core es.
2. **Turn-Ende = Rückkehr von `jon.call()`** — kein Monitor-Event. Die Fabrik ruft `call()` selbst.
3. **Herkunft (UC-WP-39) und Team-Antwort (UC-WP-40) braucht der MVP ohne Core** (Paul 2026-10-27):
   die finale Mitglieds-Antwort ist das Ergebnis des Delegations-Werkzeugs in Jons Verlauf, der
   Urheber folgt aus dem Werkzeugnamen (R-WP-4). Die Zeile zeigt das Ergebnis **wie es ist** (inkl.
   der angehängten Statistik-Zeile) — gekürzt wird nichts, denn das hieße Werkzeug-Text parsen.
   ~~Der Core-Weg „Monitor kennt den aktuellen Agenten" bleibt ein Plugin-Thema, keine
   MVP-Voraussetzung~~ — abgelöst: [ADR-0078](../../../docs/adr/0078-origin-in-core-owner-monitor.md)
   holt den Core-Weg für beide Consumer in den Core ([agent-origin-proposal](../../../proposals/agent-origin-proposal.md) §7a).
5. **Grenze der Werkzeug→Name-Tabelle:** sie kennt nur das feste Jon-Team. Kommen Custom Agents ins
   Web, trägt sie nicht mehr — dann ist `origin` aus dem Core (Proposal) der Weg, nicht eine längere Tabelle.
   → ADR-0078 setzt genau das um.
4. **Der Fabrik-Code darf bis dahin defekt sein** (Paul) — nur die Docs werden nachgezogen.

## Consequences

* Leitplanken für den Neuentwurf: **kein** zweiter Abschluss-Kanal, **kein** Delegations-Antwort-Event,
  **keine** Reflection im Wrapper, **keine** Attribut-Map je Tool-Instanz (Nebenläufigkeit).
* Weiterhin verworfen (nicht wieder vorschlagen): Werkzeug-Text parsen (`statsLine`) — zweiter, stiller
  Vertrag auf Core-Formulierungen; eine zweite Chat-Datei — Verstoß gegen R-WP-11; „die letzte Antwort
  eines Agenten ist die finale" — läßt Denk-Zwischenprodukte durch und unterscheidet zwei Delegationen nicht.
* Chat-Rebuild nach Compact folgt derselben Regel wie im Plugin
  ([R-CT-7](../../../org.sterl.llmpeon.core/docs/compact-lock.md)).
