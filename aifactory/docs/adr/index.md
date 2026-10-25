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
