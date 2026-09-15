# Session-Stand (2026-09-15 — Runde-2-UI-Fix + R16-Schärfung ✅, Merge = User)

## Zyklus story/po-compact-2026-09-13 — ✅ KOMPLETT (Review bestanden, 12 Commits `76b4a06`→`26117a7`, nicht gemerged)

Surefire 742/0, Plugin-Suite 203/0, Working tree clean. **Merge/Squash = User.**

- **Runde-2-UI-Fix ✅ `9ed839b`:** Wrapper-Composite entfernt (flaches Roster — Label+Button direkte
  Kinder, Button wie der Hammer, CSS-weiß; Probe-Dump-Evidenz) + `refreshChat()` aus
  `doCompressAgent` (Slave-Summary streamt live UND persistiert; nur Jons eigener Compact rebuilt).
  `HeaderRosterStructureTest` = falsifizierbarer Guard; Probe-Test nach Evidence-Extraktion gelöscht
  (Regel: shell-öffnende SWT-Tests nie ins Repo). Struktur-Test-Charakterization: CSS-Klasse löst
  weiß auf (nicht Shell-Erbe) — bewusst nicht-weißer Shell-Background hält ihn falsifizierbar.
  User-Smoke Optik ✅ (2026-09-15: „optisch sauber").
- **R16-Schärfung ✅ `16e9e47` (User: „einverstanden build"):** Guard <2 → <3
  (`AbstractAgent.compact:274` — nach jedem Compact exakt 2 Messages → Re-Compact = Noop);
  `PoDelegateTool.compact` liest Boolean → `"Nothing to compact (N messages)"` statt „compacted."
  (Open-Point → resolved-points.md); CompactSessionTool unverändert; Surefire 738→742.
  Plan-Abweichung legitim: `CompactSessionToolTest` + 3. Message (Plan §4 Liste unvollständig —
  Coverage-Gap an Da Thinka zurückgemeldet: bei Guard-Änderungen alle Compact-Test-Seeds grep-inventarisieren).
  Mutation-Check ✅ (Guard <2 → exakt 3 neue Tests rot). Da Dok ACCEPTED.
- **Cleanup `779dce3`/`26117a7`:** R16 → ✅ geflippt, PoDelegateTool-Open-Point erledigt,
  User-Smoke-Row dedupliziert, Homepage „fewer than 3" korrigiert — **Da-Dok-Nebenbefund war
  arithmetisch falsch** (Guard skipt bei 0/1/2 = „fewer than 3", nicht „fewer than 2").
- **Ausstehender User-Smoke (open-points.md ⏳):** Re-Compact-Noop (2 Messages → `Nothing to compact`),
  Disabled-States, Tooltip — nach Merge.

- **Zyklus-Übriges (Runde 1–2):** R18 ✅ Compact ohne Cascade (Mutation-Proof) · Header-Compact-Buttons ✅
  · R19 ✅ Clear-Cascade + Homepage `usage/agents.md` · Runde-1-Fix `e46eab2` (wrap=false — SWT-Default
  in dieser Generation TRUE; center=true; compact_dark.svg) · Da Dok CONCERNS nur Kosmetik (`dcec8f9`) ·
  Docs geflippt `845e688`, Pläne archiviert.

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
