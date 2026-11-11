# ADRs — AI Factory

Mitschrift der Entscheidungen, die **nur die Web-App** betreffen (Status · Context · Decision ·
Consequences). Regeln und BDDs leben in den Feature-Docs, nicht hier.

**Nummernraum und Ablage (User-Regel, 2026-10-25):** unsere Web-ADRs liegen **hier**, nicht im Core
und nirgends sonst — Einzige Ausnahme: eine ADR, die den **geteilten Core** betrifft, liegt im
globalen Raum [`docs/adr/`](../../../docs/adr/index.md). Nummern: `AF-ADR-<ddd>` (dreistellig, ab
001) hier vs. `ADR-<nnnn>` (vierstellig) global — in Text und Links immer mit Prefix, dann bleibt
auch ein „001" eindeutig. Der Nummernraum ist ein Experiment: kollidiert oder verwirrt er, werden
die Factory-ADRs in den globalen Raum umgezogen.

| ADR | Entscheidung | Status |
|-----|--------------|--------|
| [AF-ADR-001](001-factory-shares-core-disk-only.md) | Factory teilt den Core: Factory-Fähigkeiten entstehen möglichst im Core (Plugin profitiert), Factory nutzt nur die Disk-Familie, Core bleibt Eclipse-frei — gegen Jons „Core unangetastet"-Empfehlung, schreibt globale ADR-0046 fort | Accepted |
| [AF-ADR-002](002-origin-in-core-display-in-factory.md) | Herkunft und Team-Antwort: kein zweiter Monitor-Kanal — Inc-3f-Core-Bau entfernt, Turn-Ende = Rückkehr von `call()`, Team-Antwort = Ergebnis des Delegations-Werkzeugs (MVP ohne Core). Teile abgelöst durch [ADR-0078](../../../docs/adr/0078-origin-in-core-owner-monitor.md) | Accepted (2026-10-27), teilweise abgelöst |
| [AF-ADR-003](003-reference-ui-is-a-donor-not-a-source.md) | AntD-Umbau: die Referenz `ai-schulung` ist Geber von Mustern und Widgets, **nicht** Quelle des Zustands — Queue/Anker/Bestätigung (C-5, R-WP-37) bleiben ours, Zeiten bleiben live, Bootstrap verlässt die App | Accepted (2026-11-06) |
| [AF-ADR-004](004-stop-refusal-is-visible.md) | Eine abgelehnte Aktion bleibt sichtbar: Jons „409 auf Stop still verschlucken" (2026-11-10) ist verworfen — der `409` bleibt fachlich richtig, die Taste wird deaktiviert **und** die Ablehnung zeigt das Panel | Accepted (2026-11-11) |
