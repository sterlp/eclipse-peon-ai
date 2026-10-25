# Session-Stand — AI Factory

## Richtung (User, 2026-10-25)

Jon als **Web-Anwendung** (Spring Boot + SPA) statt Eclipse-Plugin. `llmpeon-core` wird **geteilt**:
Factory-Fähigkeiten entstehen möglichst im Core, das Plugin profitiert; Factory nutzt nur die
**Disk-Tools**, kein `eclipse*`; Core bleibt Eclipse-frei. Web-App hat **nur Jon** — keine
Agent-Auswahl.

## Stand

* `docs/index.md` — Karte dieses Produkts: Links auf Root-Register + Core-Docs, Produktregeln
  PR-1…PR-4 (alle ❌).
* `docs/open-points.md` — 3 ❓ (Team-Delegation, „Workspace"-Begriff, Server-Config/Secrets).
* `docs/adr/0077-factory-shares-core-disk-only.md` + Index-Zeile im globalen ADR-Raum —
  User-Entscheid **gegen** meine „Core unangetastet"-Empfehlung.

## Nächste Schritte

1. ❓ Klären: behält Jon in der Web-App sein Team (Thinka/Mek/Dok/Sniffa)? → PR-1 schärfen.
2. Erstes Feature schneiden (Vorschlag: Agent-Boot + ein Chat-Turn über disk-Tools, vertikale
   Scheibe) — dafür braucht es Architektur-Doc **vor** dem Plan (`aifactory-web-architektur.md`).
3. Vor dem Bauen: Branch für den Zyklus anlegen (steht noch auf dem aktuellen Stand).

## Merksätze für die folgenden Zyklen

* Factory-Duplikate von Core-Logik sind verboten (ADR-0077); Grenze = „braucht das auch das Plugin?".
* Composition-Root-Gap aus ADR-0046 ist die erste Core-Baustelle, wenn's ans Wiring geht.
