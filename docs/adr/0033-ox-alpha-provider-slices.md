# ADR-0033: Ox-Alpha-Provider — Zwei-Slice-Plan (Refactoring vor neuem Provider)

**Status:** Accepted (backfill 2026-08-28 — bereits von provider.md / memory.md referenziert)

**Context:** Der neue Free-Provider „Ox Alpha" soll ohne Enum-Wachstum dazukommen;
`AiProvider` ist heute ein Enum mit je Konstante drei Verhaltensmethoden
(`buildModel`, `newRequestParameters`, `listAiModels`) plus statischen Helpers.

**Decision:** Zwei_slices: (1) **verhaltenstreu**es Refactoring auf „je Provider eine Klasse"
([provider.md](../provider.md) R1–R4, Golden-Tests bleiben grün); (2) Ox Alpha als **erste
neue Provider-Klasse** auf diesem Interface.

**Consequences:** Slice 1 **100% verhaltenstreu** (Cache-Hardcodes bleiben in den neuen
Klassen; sie fallen erst in Schritt 2 zusammen mit der JSON-Body-UI —
[caching.md](../caching.md) R1, Option B 2026-08-28). Slice 2 ist eigene
Story: `free-provider-ox-alpha.md` (noch **nicht** angelegt — Follow-up). Caching/Extra-Body
bleibt separates Feature ([caching.md](../caching.md), Fähigkeits-Gate provider.md R3).
Connection-Cache der Provider-Instanzen: [ADR-0034](0034-connection-cache-by-identity.md).
