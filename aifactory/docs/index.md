# AI Factory — Docs (Index)

**Ziel:** Jon (Peon-PO) als **Web-Anwendung** — Spring Boot Backend + SPA im Browser statt
Eclipse-Plugin. Agent-Logik wird nicht neu erfunden, sie kommt aus `llmpeon-core`.

Dieser Index ist die Karte **dieses** Produkts: Links + Feature-Zeilen + Produktregeln.
Kein Protokoll, kein Code-Bestand.

Status: ✅ done · 🚧 in design · ❌ specified (nicht gebaut) · ⏳ geparkt · 📝 Studie.
Backlog = jedes ❌/🚧.

## Wo das Wissen liegt

* **[Peon-AI Root-Docs](../../docs/index.md)** — Register des `llmpeon-parent`-Repos:
  ADRs (globaler Nummernraum), Glossary, Architektur-Regeln, Open/Resolved Points.
* **[Core-Docs](../../org.sterl.llmpeon.core/docs/index.md)** — `llmpeon-core` (plain Maven,
  ohne Eclipse): Agenten (PO/Plan/Dev/Review) · Context & Compact · Tool-Familien
  (`disk*`/`web*`/`shell`/`docs*`) · Provider & Config · Skills & Prompts · Infra.

Nicht hier: alles Eclipse-/OSGi-spezifische (SWT-Views, Widgets, Preference-Pages, `eclipse*`-Tools).

## Produktregeln (SOLL)

* **PR-1 · Ein Agent.** Die Web-App kennt genau einen Agenten: Jon. Keine Agent-Auswahl, keine
  weiteren Built-in- oder Custom-Agenten. ❌
  (Offen: ob Jons Team — Da Thinka/Da Mek/Da Dok, Da Sniffa — mitkommt → [Open Points](open-points.md).)
* **PR-2 · Nur Disk-Werkzeuge.** Die Factory betreibt die Disk-Familie (`disk*`, `web*`, `shell`,
  `docs*`); `eclipse*`-Tools und OSGi gibt es hier nicht. ❌
* **PR-3 · Geteilter Core.** Was beide Consumer brauchen, entsteht im Core und das Plugin profitiert;
  Web-only-Schichten (HTTP/SSE, Server-Session, SPA) bleiben in der Factory. Duplikate der
  Agent-Logik in der Factory sind verboten. ❌ — Begründung:
  [ADR-0077](../../docs/adr/0077-factory-shares-core-disk-only.md).
* **PR-4 · Core bleibt Eclipse-frei.** Keine `org.eclipse`-Importe im Core; der Factory-Port ist der
  Beweis, nicht nur die Absicht. ❌ — fortschreitend [ADR-0046](../../docs/adr/0046-core-portability-review.md).

## Features

_noch keine — kein Feature ist in diesem Docs-Baum spezifiziert._

## Noch nicht angelegt

[Glossary](glossary.md) · [ADRs](adr/index.md) — eigene Factory-ADRs gibt es nicht, der Nummernraum
bleibt global in [`docs/adr/`](../../docs/adr/index.md). Glossar kommt mit dem ersten übergreifenden
Begriff. [Open Points](open-points.md) existiert.
