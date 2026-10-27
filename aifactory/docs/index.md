# AI Factory — Docs (Index)

**Ziel:** eine **Multi-User AI Factory** im Browser — sie baut gleichzeitig an mehreren Projekten, je
Projekt **ein** Jon mit seinem Team, und alle Nutzer eines Projekts sehen denselben Chat.
Agent-Logik kommt unverändert aus `llmpeon-core`, kein Eclipse/OSGi.

Dieser Index ist die Karte **dieses** Produkts: Links + Feature-Zeilen + Produktregeln.
Kein Protokoll, kein Code-Bestand.

Status: ✅ done · ◐ teilweise gebaut (Regeln mit ✅/❌ darunter) · 🚧 in design · ❌ specified (nicht
gebaut) · ⏳ geparkt · 📝 Studie.
Backlog = jedes ❌/🚧/◐.

## Wo das Wissen liegt

* **[Architektur](aifactory-architektur.md)** — das WIE: Komponenten, Turn-Besitz, Core-Abbildung,
  Invarianten. **Vor jedem Plan** zu lesen.
* **[Pauls Produktvision](../pauls-product-vision.md)** — Richtungsinput des Users (*für Agenten nur zu
  lesen*): Rollen und Rechte, Slide-outs je Agent, Config-Fenster (MCP, Agenten, System-Prompt),
  Docs als Website. **Kein SOLL** — was daraus Regel wird, entscheiden wir und es steht dann in einem
  Feature-Doc. Abweichungen davon stehen als ❓ in [Open Points](open-points.md).
* **[Peon-AI Root-Docs](../../docs/index.md)** — Register des `llmpeon-parent`-Repos: globale ADRs,
  Glossary, Architektur-Regeln, Open/Resolved Points.
* **[Core-Docs](../../org.sterl.llmpeon.core/docs/index.md)** — `llmpeon-core` (plain Maven,
  ohne Eclipse): Agenten · Context & Compact · Tool-Familien · Provider & Config · Skills & Prompts.

Nicht hier: alles Eclipse-/OSGi-spezifische (SWT-Views, Widgets, Preference-Pages, `eclipse*`-Tools).

## Produktregeln (SOLL)

* **PR-1 · Ein Agent, volles Team.** Pro Projekt genau ein Jon mit unverändertem Team (Da Thinka,
  Da Mek, Da Dok; Da Sniffa bleibt wie im Plugin das Such-Werkzeug). ✅
* **PR-2 · Nur Disk-Werkzeuge.** Die Factory betreibt die Core-Familien `disk*`, `web*`, `shell`,
  `docs*`; `eclipse*`-Tools und OSGi gibt es hier nicht. ✅ — [R-WP-12](web-prototype.md).
* **PR-3 · Geteilter Core.** Was beide Consumer brauchen, entsteht im Core und das Plugin profitiert;
  Web-only-Schichten (HTTP/SSE, Server-Session, SPA) bleiben in der Factory. Duplikate der
  Agent-Logik in der Factory sind verboten. ✅ — Begründung:
  [AF-ADR-001](adr/001-factory-shares-core-disk-only.md).
* **PR-4 · Core bleibt Eclipse-frei.** Keine `org.eclipse`-Importe im Core; der Factory-Port ist der
  Beweis, nicht nur die Absicht. ✅ — der Port läuft, `ArchitectureGuardTest` hält die Grenze;
  fortschreitend [ADR-0046](../../docs/adr/0046-core-portability-review.md).
* **PR-5 · Je Projekt ein Jon, ein Chat für alle.** Inputs aller Nutzer laufen in die Queue *eines*
  Agenten; verschiedene Projekte laufen parallel. ✅ — Details
  [Web-Prototype R-WP-15](web-prototype.md).
* **PR-6 · parallele Vorarbeit ohne Baurisiko.** Nutzer dürfen über **Jon Light** (read-only) Stories
  liefern, ohne den bauenden Jon zu stören. ❌ — [Jon Light & Stories](jon-light-stories.md).

## Features

* [Web-Prototype](web-prototype.md) — Projects · Chat · Einstellungen; Git-Clone als Projekt,
  Jon mit Team, Schreib-Pinning auf Projekt-Root + TMP, Kacheln je Agent. ◐ teilweise gebaut
  (Registry · Clone · Auslieferung · Einstellungen · Chat mit Queue, Verlauf, SSE ✅;
  Herkunft je Zeile + Team-Antwort ❌ seit 2026-10-27 ([AF-ADR-002](adr/002-origin-in-core-display-in-factory.md)),
  Schreib-Pinning, Datei-Bereich, Kacheln ❌). **Code nach Core-Aufräumen 2026-10-27 bewusst defekt.**
* [Jon Light & Stories](jon-light-stories.md) — read-only Jon je Nutzer, Stories in `stories/`,
  konfliktfrei oder mit benanntem Konflikt. ❌ specified (nach dem MVP)

## Noch nicht angelegt

[Glossary](glossary.md) — kommt mit dem ersten übergreifenden Begriff.
[ADRs](adr/index.md): unsere Web-ADRs liegen **hier** im eigenen Raum `AF-ADR-*`; nur eine ADR, die
den geteilten **Core** betrifft, liegt im globalen Raum
[`docs/adr/`](../../docs/adr/index.md). [Open Points](open-points.md) existiert.
