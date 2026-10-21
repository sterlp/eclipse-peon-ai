---
name: peon-release
description: Release & Deploy-Prozedur — build, lokaler p2-Test, Tag-Release via release.yml, deploy-pages auf peon-ai-4e.sterl.org. Ersetzt github-eclipse-repo.md (ADR-0072).
---

# Release & Deploy — llmpeon

**Trigger:** Release bauen, Update-Site testen, Version bumpen, Deploy prüfen.

## Build

- Full: `mvn clean verify`
- Nur Update-Site: `mvn clean verify -pl releng/llmpeon-update-site -am`
- Output: `releng/llmpeon-update-site/target/repository/`
- Erwartet: `artifacts.jar`, `content.jar`, `p2.index`, `features/`, `plugins/`

## Lokal testen

- `cd releng/llmpeon-update-site/target/repository && python -m http.server 8080`
- Eclipse: `Help > Install New Software > Add > http://localhost:8080`
- Oder ZIP: `Help > Install New Software > Add > Archive`

## Release-Flow (Tag-Trigger)

1. Tag pushen: `x.y.z*` (z.B. `2.14.1`)
2. `release.yml` löst aus:
   - Baut die Update-Site (`mvn clean verify`)
   - **Setzt die Version automatisch aus dem Tag** (`tycho-versions-plugin:set-version` — kein manueller pom.xml-Edit nötig)
   - Upload-Step: `actions/upload-artifact` (`peon-ai-update-site`)
   - Deploy-Step: `actions/deploy-pages` — **ersetzt die Site komplett**, kein manuelles gh-pages-Cleanup nötig (der alte `find -maxdepth 1`-Fix aus `maven.yml` ist obsolet — maven.yml deployt nicht).
3. Deploy-Ziel: `https://peon-ai-4e.sterl.org/`

## Bekannte Fallen

- **p2-Root-Pfad verifizieren (2026-10-20):** `https://peon-ai-4e.sterl.org/p2.index` lieferte beim letzten Check 404 (Root ist die VitePress-Homepage). Vermutlich liegt die Update-Site unter einem Pfad (z.B. `/update/`). Nach dem nächsten Release-Smoke prüfen und den Pfad hier + in der Homepage-Installation eintragen. Pauls These zur alten URL: `https://sterlp.github.io/eclipse-peon-ai/` liefert 404, weil die Repo-Root keine `index.html` mehr hat — die Site-Struktur ist jetzt reine p2-Struktur.
- **p2-Content vor dem Push verifizieren:** `plugins/` + `features/` müssen gefüllt sein.
- **Issue-142-Guard** (`maven.yml`): Deploy schlägt fehl, wenn Tycho Bundles außerhalb `org.sterl.*` in den Build zieht — gewolltes Verhalten, nicht umgehen.
