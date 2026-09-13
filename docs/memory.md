# Session-Stand (2026-09-13 — Compact-Slot-Bug-Zyklus als nächstes)

## Release ui-config + UserContext — ✅ KOMPLETT, gemerged & gepusht

`main` = origin/main (0/0, Merge `3d38ef6` + User `bc24da9`). Suite 201/0. Compact-Konflikt
(`AiCompressorAgent.java:41-45`, Dedup-Fix doppelt) zugunsten Branch aufgelöst.
UserContext-Tests (12) + StandingOrdersBuilderTest-Fix: `a625167`/`13bb500`.

## Nächste Zyklen (User-Reihenfolge 2026-09-13)

1. **Compact-Slot-Bug** — ✅ FIXED (`efa22df`, Zyklus fix/compact-slot-model, Da-Dok-Review
   ACCEPTED, Surefire 728→730/0). COMPACT-Slot steuert den Call vollständig via
   `ConfiguredChatModel.modelFor(agent)`; leer → Base. Docs: advanced-configuration.md +
   compact-input-budget.md. **Merge/Release = User; User-Smoke: Compact mit fremder
   Slot-URL konfigurieren und beobachten.**
2. **Compact-Input-Budget Light + Context-Noise** — Story ❌ specified
   (docs/compact-input-budget.md), zusammen mit Noise-Idee (open-points.md ❓) ausarbeiten,
   vor dem Bau nochmal gemeinsam drüber.
3. **ApiRetry-Cancel-Bug** (memory #21) — Verifikation beim Bau (User).
4. Danach: Compact-Delay (~4-5s, Verdacht StreamingBridge-Poll/onCompleteResponse), R-A3
   Copilot-Studie, Docs-Hygiene-Sweep, Stale-Guard-Follow-up-Test.

**IST Compact-Input (User, gepusht):** LinkedHashSet-Dedup (exakt, O(n)) statt indexOf-Substring,
Cap 4000. Dokumentiert in docs/compact-input-budget.md R2. Homepage: +5%-Toleranz-Doku = Teil der
Budget-Story.

## Backlog / offene User-Entscheidungen

- Branch `release-2026-09-06` (3 Commits, von main) — Merge = User-Entscheid (index.md).
- Review-Agent bekommt read-only Git/Shell? (Use-Case: Diff-Isolation nach Hash — Umweg über
  Diff-File war Workaround, open-points.md).
