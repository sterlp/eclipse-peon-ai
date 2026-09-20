# Tool-Output-Disclosure — Caps ehrlich machen

> **Status:** 🚧 in design (Entwurf aus dem Tool-Evolutions-Run 2026-09-19, wartet auf PO-Freigabe).
> Ursprungszuordnung (temporär): [tool-evolution.md](tool-evolution.md) — CR-17/18/19.

## Ziel

AGENTS.md: „A tool must never lie about a limit." Grep-Tools disclose ihre Caps im Return — drei weitere
Tools kappen still oder gar nicht. Nachziehen, gleiche Disclosure-Logik wie `grepComplete`.

## Regeln (Entwurf)

- **R-OD-1 ❌** `eclipseSearchFiles`: wird das Limit erreicht (Default 100, **Cap 500** — Paul 2026-09-19,
  war 1000), nennt der Return die Kappung („capped at N — narrow your search"). Heute: stille Kappung.
- **R-OD-2 ❌** `diskSearchFiles`: gleiche Regel — nur falls `limit > 0` und erreicht.
- **R-OD-3 ❌** `webFetchAsMarkdown`: statt byte-Cap → **paginiert** (Mini-Cache letzte 5 URLs,
  500-Zeilen-Fenster je Call, disclosed) — Details in [web-tools.md](web-tools.md); Fehlerpfad
  (HTTP ≥ 400) gibt Status + **Snippet** statt des vollständigen Body zurück.
- **R-OD-4 ❌** Disclosure-Zeilen folgen dem bestehenden `AiReponseBuilder`-Muster (eine Stelle, beide
  Such-Tools konsistent).

## BDD (Entwurf, hart erst bei ❌)

- GIVEN 1200 Treffer bei limit=1000 WHEN eclipseSearchFiles THEN Return nennt „capped at 1000".
- GIVEN limit=0 WHEN diskSearchFiles THEN unlimited, keine Disclosure.
- GIVEN 5-MB-HTML-Seite WHEN webFetchAsMarkdown THEN Result auf Cap gekürzt, Cap disclosed.
- GIVEN HTTP 500 WHEN webFetchAsMarkdown THEN Status + Snippet (kein voller Body im Kontext).

## Offen (PO-Run)

- Als ein gebündeltes S-Inkrement (Lean: ja) — alle drei teilen die Disclosure-Logik.
- Aufwand: **S** je Item.
