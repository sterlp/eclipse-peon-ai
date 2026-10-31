# System Prompts — Übersicht

> Produkt-Code: gilt für alle Projekte (ABAB, C++, Java, …). Nichts Repo-Spezifisches.
> **Editing rights:** Nur Jon editiert die System-Prompts. Bei größeren Änderungen 4-Augen mit
> Plan / Review; der User ist an jeder Änderung beteiligt. Kein Diktat an andere Agenten.
> Quelle: `org.sterl.llmpeon.core/src/main/resources/org/sterl/llmpeon/prompts/` (13 Dateien).

## Kern-Agenten

| Prompt | Agent | Rolle | Wann / Warum |
|---|---|---|---|
| [`po.md`](../src/main/resources/org/sterl/llmpeon/prompts/po.md) | **Jon** | Business-Owner, Hüter der Docs | Immer wenn das WAS festgelegt wird: Stories, Regeln, BDD, Release-Freigabe. Einziger Ansprechpartner für den User. |
| [`po-delegation.md`](../src/main/resources/org/sterl/llmpeon/prompts/po-delegation.md) | **Jon** (Ergänzung) | Delegations-Playbook | Wird in `po.md` injiziert (`${plan}`/`${docs}`-Platzhalter). Operative Delegation an Plan/Dev/Review, Themenwechsel-Ritual. |
| [`doc-method.md`](../src/main/resources/org/sterl/llmpeon/prompts/doc-method.md) ❌ | **Jon** + **Paco** | gemeinsame Doc-Methode | Von beiden geladen: IST/SOLL/WEIL, GIVEN/WHEN/THEN, Symbole/Status-Marker, Konfliktcheck — ein Pflegeort, kein Drift ([R-PC-7](../../aifactory/docs/paco.md)). |
| [`paco.md`](../src/main/resources/org/sterl/llmpeon/prompts/paco.md) ❌ | **Paco** (nur Web) | fachlicher PO je Nutzer | Rolle & Identität: Grill me, Projektfragen, Stories/Proposals ([R-PC-7](../../aifactory/docs/paco.md)). |
| [`planner.md`](../src/main/resources/org/sterl/llmpeon/prompts/planner.md) | **Plan** | Plan-Mode, read-only | Vor jedem Build: Ziel → Architektur → Plan. Kein Schreibzugriff. |
| [`developer.md`](../src/main/resources/org/sterl/llmpeon/prompts/developer.md) | **Dev** | Dev-Mode, macht Änderungen | Implementierung nach Plan. Build+Tests, Test-Honesty, Skill-Footer. |
| [`review-agent.md`](../src/main/resources/org/sterl/llmpeon/prompts/review-agent.md) | **Review** | Review-Mode, read-only | Review jedes Inkrements gegen den Plan. Meldet Skill-Evolution-Outcome + Evidence. |

## Lifecycle- & System-Prompts

| Prompt | Agent | Rolle | Wann / Warum |
|---|---|---|---|
| [`default.md`](../src/main/resources/org/sterl/llmpeon/prompts/default.md) | alle | Fallback/System-Default | Basis-Prompt, wenn kein Agent-spezifischer Prompt gesetzt ist. |
| [`compressor.md`](../src/main/resources/org/sterl/llmpeon/prompts/compressor.md) | intern (Compressor-LLM) | Context-Compression | Wird vom `CompactService` benutzt, um die Chat-History zusammenzukürzen (R-CC-x, ADR-0055/0056). |
| [`dev-build-loop.md`](../src/main/resources/org/sterl/llmpeon/prompts/dev-build-loop.md) | Dev | Build-Loop-Verhalten | Steuert den iterativen Build/Verify-Zyklus (buildWithDev), inkl. Inkrement-Handling und planImplemented. |
| [`plan-write-loop.md`](../src/main/resources/org/sterl/llmpeon/prompts/plan-write-loop.md) | Plan | Plan-Schreib-Loop | Steuert den iterativen Schreib-Zyklus von `peon-plan/overview.md` (planWithPlanAgent). |

## Tutorial- & Hilfs-Agenten

| Prompt | Agent | Rolle | Wann / Warum |
|---|---|---|---|
| [`po-tutorial.md`](../src/main/resources/org/sterl/llmpeon/prompts/po-tutorial.md) | Jon | Tutorial-Mode | Geführt durch die PO-Methode (onboarding/onion), interaktiv. |
| [`scaffold-agent.md`](../src/main/resources/org/sterl/llmpeon/prompts/scaffold-agent.md) | Scaffold-Agent | Code-Generierung | Erstellt automatisch neue Skills/Prompts/Docs-Struktur (noch ausstehend für Jon-Skills). |
| [`scaffold-tutorial.md`](../src/main/resources/org/sterl/llmpeon/prompts/scaffold-tutorial.md) | Scaffold-Agent | Tutorial-Mode | Geführt durch das Scaffold-Feature (Struktur-Anleitung). |
| [`search-agent.md`](../src/main/resources/org/sterl/llmpeon/prompts/search-agent.md) | Search-Agent | Recherche | Wegwerf-Recherche-Agent für mehrschrittige Nachschlagen (rein lesend, kein Edit). |
