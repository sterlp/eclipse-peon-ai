# Session-Stand — AI Factory

**Modus: nur Docs, kein Code** (User, 2026-10-25). Kein Plan, kein Build, kein Branch.

## Richtung (User)

Jon als **Web-Anwendung** (Spring Boot + SPA) statt Eclipse-Plugin.

* `llmpeon-core` wird **geteilt**: Factory-Fähigkeiten entstehen möglichst im Core, das Plugin
  profitiert; Duplikate in der Factory verboten → `docs/adr/001-…` (**AF-ADR-001**).
* Factory nutzt nur die **Disk-Tools**, kein `eclipse*`; Core bleibt Eclipse-frei.
* Web-App hat **nur Jon**, aber sein **Team bleibt voll** (Thinka/Mek/Dok/Sniffa) — PR-1.
* ADR-Ablage (User-Regel): **unsere Web-ADRs liegen bei uns** in `aifactory/docs/adr/` als
  `AF-ADR-001…` (dreistellig) — nicht im Core, nicht woanders. Nur eine ADR, die den **geteilten
  Core** betrifft, liegt global (`ADR-0001…`, vierstellig). Prefix immer mitschreiben, dann bleibt
  „001" eindeutig. Umnummerierung 0077 → AF-ADR-001 erfolgt, globale Index-Zeile entfernt.
  (Nummernraum = Experiment; Kollisionsfall → Rückzug in den globalen Raum.)

## Stand der Docs

* `docs/index.md` — Karte: Root-Register + Core-Docs verlinkt, Produktregeln PR-1…PR-4 (alle ❌).
* `docs/adr/index.md` + `001-factory-shares-core-disk-only.md`.
* `docs/open-points.md` — 2 ❓ („Workspace"-Begriff, Server-Config/Secrets).
* Root `docs/index.md` — „AI Factory docs"-Zeile im Abschnitt *Where the knowledge lives* ergänzt;
  ADR-Bereich dort auf 0001–0076 korrigiert (Stand war 0073, ADR-0074…0076 existieren).

## Nächste Schritte (wenn der User weitermacht)

1. ❓ „Workspace" in der Web-App klären → erstes Feature (Arbeitsordner je Session) schneiden.
2. Dann Architektur-Doc **vor** jeglichem Plan (`aifactory-web-architektur.md`), Mermaid-Sequenz:
   HTTP/SSE → Session → Core-Agent → disk-Tools.
3. Glossar erst mit dem ersten übergreifenden Begriff (z. B. Factory / Session / Arbeitsordner).

## Merksätze

* Factory-Duplikate von Core-Logik sind verboten; Grenze = „braucht das auch das Plugin?".
* Composition-Root-Gap aus ADR-0046 ist die erste Core-Baustelle, wenn's ans Wiring geht.
