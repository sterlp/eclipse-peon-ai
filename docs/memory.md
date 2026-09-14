# Session-Stand (2026-09-13 — Compact-Cascade → R18/R19 + Header-Compact-Buttons)

## Neu spezifiziert (2026-09-13, User-Entscheid — wartet auf Bau-Freigabe)

- **R16 SOLL-Korrektur:** <2 Messages = **Skip** (kein Clear) — Code war richtig, Docs falsch. ✅ in po-agent-jon.md + index.md.
- **R18 ❌ specified (po-agent-jon.md):** Compact = nur der Agent selbst — impliziter Slave-Cascade in `AiPoAgent.compact()` entfernen; explizite Slave-Compact-Tools bleiben; Sklaven self-managen (Auto-Compact 0.7).
- **Header-Compact-Buttons ❌ specified (agenten-status-im-header.md, komplett auf MVP-Stand umgeschrieben):** Da Thinka/Da Mek/Da Dok je Icon-Button (kein Da Boss), Klick = `Job` wie `doCompressContext`, disabled bei working/in-flight, Statuszeile-Feedback inkl. „Nothing to compact" (R16-Skip). UI: `SwtUtil.createIconButton`, Widget von Label → Zeilen-Composites.
- **R19 ✅:** Clear-Cascade bleibt (IST bestätigt). **Homepage-Doku fehlt komplett** (keine Agenten-/Clear-Seite) — ❌ Teil des Inkrements.
- Pipeline: User-Frage war Anzeige-Lücke beim Jon-Compact (kein Fortschritt, „Hänger") — durch R18 obsolet für Jons Compact; Buttons geben Sklaven-Compact sichtbar zurück.
- Story-Split für den Bau: (1) R18 Cascade raus (core, mit Tests), (2) Header-Buttons (plugin UI), (3) Homepage. Order noch nicht freigegeben.

## Release ui-config + UserContext — ✅ KOMPLETT, gemerged & gepusht

`main` = origin/main (0/0, Merge `3d38ef6` + User `bc24da9`). Suite 201/0. Compact-Konflikt
(`AiCompressorAgent.java:41-45`, Dedup-Fix doppelt) zugunsten Branch aufgelöst.
UserContext-Tests (12) + StandingOrdersBuilderTest-Fix: `a625167`/`13bb500`.

## Nächste Zyklen (User-Reihenfolge 2026-09-13)

1. **Compact-Slot-Bug** — ✅ FIXED (`efa22df` + Wire-Tests `3977a8d`: COMPACT Think/extraBody
   + Custom-Agent-Frontmatter on-the-wire; Surefire 734/0), Da-Dok-Review ACCEPTED.
   COMPACT-Slot steuert den Call vollständig via `ConfiguredChatModel.modelFor(agent)`;
   leer → Base. Docs: advanced-configuration.md + compact-input-budget.md.
   **Merge/Release = User; User-Smoke: Compact mit fremder Slot-URL konfigurieren und beobachten.**
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
