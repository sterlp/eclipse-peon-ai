# Docs — design & dev spec (the HOW / system reference)

One story per feature, shared memory of the team. Technical decisions: [adr/index.md](adr/index.md).
Not published — user-facing docs live in `homepage/`.
Auto-loaded every turn: **this file** + [memory.md](memory.md). The module indexes are **not**
auto-loaded — read the one for the module you work in; read both when a change crosses module
borders (eclipse*/disk*-Parität, Wiring, shared classes).

Status: ✅ done · 🚧 design/WIP · ❌ not done · ⏳ geparkt/historisch · 📝 Studie.
Backlog = every ❌/🚧 item. Vollständige Einträge (mit Status-Grund) stehen in den Modul-Indexen.

**[Product Vision](product-vision.md)** — was Peon AI (Eclipse-Plugin) ist und was nicht: Agenten-System in Eclipse, local first, kein Code-Completion-Tool.

## Where the knowledge lives

* **[Core docs](../org.sterl.llmpeon.core/docs/index.md)** — `llmpeon-core` (plain Maven):
  agents (PO/Plan/Dev/Review, History, Retry, Prompt-Override) · context & compact · tool
  families (`disk*`/`web*`/`shell`/`docs*`) · provider & config · skills & prompts ·
  infra (Docs-Linter, MCP, Codegraph, Caches).
* **[Plugin docs](../org.sterl.llmpeon/docs/index.md)** — Eclipse/OSGi: chat view & widgets ·
  header/roster · configuration & models · `eclipse*` tools & workspace · OSGi test setup.
* **[AI Factory docs](../aifactory/docs/index.md)** — Web-App (Spring Boot + SPA): Jon mit den
  Disk-Tools statt Eclipse-Plugin; eigener ADR-Raum `AF-ADR-*`. 🚧 Richtung, nichts gebaut.
* **[ADRs](adr/index.md)** (global 0001–0076) · [Glossary](glossary.md) · [Configuration](configuration.md)
* [Architecture rules](architecture.md) · [Open Points](open-points.md) · [Resolved](resolved-points.md)
  · [Open to Discuss](open-to-discuss.md)

## Repo-Docs & Notes

* [Architecture — Component Rules](architecture.md) — Service/Component/View/Widget layering,
  extraction rules, base packages. PeonAiService = Fassade (✅ 2026-08-23).
* [Maven Pipeline (CI)](pipeline.md) — ✅ headless-SWT-Verträglichkeit: CI läuft mit
  `xvfb-run -a` (Target-Sprung 2026-03→2026-09, Console-Crash); B-Guard bewusst nicht
  zurückgebracht (siehe Doc).
* [Docs als HTML veröffentlichen (Blume)](blume-publishing.md) — 🚧 Idee, nicht geplant; vier
  Kollisionen mit dem Bestand; Vorfrage Q-B4: veröffentlichen wir `docs/` überhaupt?
* [Verbesserungen aus opencode](verbesserungen.md) — 📝 Studie (2026-08-23): Top-5-Übernahme-
  Kandidaten; Teil-Outcome → [Secure Credentials](../org.sterl.llmpeon/docs/secure-credentials.md).
* [Issue #142 — ASM-Konflikt](../issues/issue-142-asm-conflict.md) — ✅ gefixt (2026-10-06):
  p2-Repo liefert nur Feature+Plugin; Nutzer-Recovery über Homepage-Troubleshooting.
* [Open Points](open-points.md) — zurückgestellte/offene Punkte (❓ offen · ⏳ selbst entschieden ·
  🔒 geklärt).
* [Resolved Points](resolved-points.md) — geklärte Punkte ohne eigenes Feature-Doc.
* [Open to Discuss](open-to-discuss.md) — ambiguous items; reviewed end-of-cycle.
* [Open Ends / Memory](memory.md) — Session-Zwischenstand only; nach jedem Plan-Zyklus aufräumen.
* [ADRs](adr/index.md) — technical decision records (the agent's long-term memory).

## Offene User-Entscheidungen

* **❓ Glossar eager** und **❓ buildWithDev-Compact** — Empfehlungen in [open-points.md](open-points.md).
* **❓ ApiRetry-Verdacht** (Null-Byte-IOException als Cancel klassifiziert?) — Investigation im
  Bug-Fix-Zyklus, Details in [open-points.md](open-points.md).
