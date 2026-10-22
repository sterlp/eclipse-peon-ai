# ADR-0066 — Da Dok bekommt einen eigenen Model-Slot (REVIEW)

**Status:** Accepted (2026-10-13)

**Context:** `AiReviewAgent` (Da Dok / Peon-Review) wurde 1:1 aus der `AiPlanAgent`-Familie
abgeleitet und teilt daher den **PLAN**-Model-Slot (`review-agent.md` R1: „Config/Model-Slot =
PLAN"). Paul will Da Dok getrennt von Da Thinka konfigurieren können — eigenes Modell/Think für
Reviews (gerade mit Blick auf die Tool-Allowlist-Story, wo Da Dok eine eigene Tool-Menge bekommt).

**Decision:** Eigenes Slot-Präfix `llm.agent.review.*`, Aufnahme in `CORE_IDS`, eigene UI-Sektion
auf der Advanced-Seite. Fallback bei leerem Slot = **Base** (Muster [ADR-0036](0036-po-own-model-slot.md)),
nicht mehr Plan. Clean Break: kein Alias, keine Migration — User kopieren ggf. einmal die
PLAN-Werte in die REVIEW-Sektion.

**Consequences:**
- Da Dok und Da Thinka sind konfigurativ entkoppelt; PLAN-Änderungen berühren Da Dok nicht mehr.
- `CORE_IDS` wächst um REVIEW — alle Stellen, die CORE_IDS enumerieren (UI, Store-Keys,
  Default-Inheritance-Tests), müssen mitgezogen werden (Blast-Radius bewusst klein halten).
- Homepage-Doku (Config-Seiten) im selben Inkrement nachziehen (user-visible change).
