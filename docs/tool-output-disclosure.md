---
idPrefix: OD
---

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

## BDD

#### UC-OD-1 — eclipseSearchFilesCapsWithDisclosure
- GIVEN Workspace mit 600 Match-Dateien WHEN `eclipseSearchFiles(query, limit=500)` THEN Return listet
  500 Treffer **und** nennt die Kappung („capped at 500 — narrow your search").

#### UC-OD-2 — diskSearchFilesDisclosesCap
- GIVEN Verzeichnis mit 80 Match-Dateien WHEN `diskSearchFiles(query)` (Default-Limit 50) THEN Return
  listet 50 Treffer **und** nennt die Kappung.

#### UC-OD-3 — diskSearchFilesUnlimitedNoDisclosure
- GIVEN Verzeichnis mit 30 Match-Dateien WHEN `diskSearchFiles(query, limit=0)` THEN alle 30 Treffer,
  **keine** Disclosure-Zeile (nichts wurde gekappt).


## Offen (PO-Run)

- Als ein gebündeltes S-Inkrement (Lean: ja) — alle drei teilen die Disclosure-Logik.
- Aufwand: **S** je Item.
