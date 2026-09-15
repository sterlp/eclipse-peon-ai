# Session-Stand (2026-09-15 — Header-Roster UI-Fix Runde 2: Fix implementiert, wartet User-Review/Smoke)

## Zyklus story/po-compact-2026-09-13 — ✅ fertig; UI-Fix Runde 2 implementiert (uncommitted)

Branch 8 Commits (`76b4a06`→`e46eab2`), Surefire 738/0. Merge = User. User-Smoke Runde 1:

**Befunde Runde 1 (User):** Wrap/Farben/„zu hoch" → Fix `e46eab2` (wrap=false explizit — SWT-Default
ist in dieser Generation TRUE, nicht false!; center=true; compact_dark.svg). ABER:

**Befunde Runde 2 (User-Diagnose, 2026-09-14) — ✅ implementiert (uncommitted, wartet User-Review/Smoke, 2026-09-15):**
1. **Grauer Kasten (Wrapper):** ✅ **Fix implementiert** — Row-Composite komplett entfernt,
   Label+Button direkte Kinder des Roster-Composites (RowLayout center=true, wrap=false,
   spacing=2), CSS-Klasse auf Label+Button wie beim Hammer. Original-Befund: Row-Composite
   (Label+Button) hatte grauen Hintergrund statt weiß/transparent, Button „zu groß".
2. **Chat-Refresh beim Slave-Compact:** ✅ **Fix implementiert** — `refreshChat()` aus
   `AIChatView.doCompressAgent` entfernt (Chat-Rebuild NUR bei Jons eigenem Compact).
   IST-Verifikation: Slave-Compact streamt live UND hängt die finale Summary persistent an
   (`AIChatView.doCompressAgent:516` → `AbstractAgent.compact:271` → `AiCompressorAgent.call:64`
   → `ConfiguredChatModel.callBlocking:45` immer `StreamingBridge` → live via `onStreamingChunk`
   = transient Live-Status; persistent via `AiCompressorAgent:67 onChatResponse` →
   `AIChatView.onChatResponse:289-301 appendMessage`). Vor dem Fix hat `refreshChat()` die
   angehängte Summary weggewischt.
- Vorheriger Fix-Versuch (`askDev`) brach ab („Da Mek AI call canceled") — ✅ verifiziert: Working
  Tree enthielt halbe Änderung (refreshChat-Fix + fertige Probe-Test-Datei); beides wurde
  geprüft und in diesen Fix übernommen.

**Runde-2-Änderungen (uncommitted, wartet User-Review/Smoke):**
- `AiAgentStatusWidget.java` (flat Struktur), `AIChatView.java` (refreshChat entfernt)
- NEU `HeaderRosterStructureTest.java` (falsifizierbarer Regression-Guard gegen Wrapper);
  `HeaderRosterPaintProbeTest.java` war nur Diagnose-Probe (Evidence extrahiert, dann gelöscht —
  kein toter Code ins Repo)
- Builds: `org.sterl.llmpeon` ✅, `org.sterl.llmpeon.test` ✅; Core Surefire 738/0 (2026-09-15)
- Plugin-Suite (OSGi, Eclipse-Runner): **203 Tests / 0 Failures** (2026-09-15, Trust-Dialog vom
  User bestätigt). Struktur-Test-Fix: Background-Assertion war falsch (Shell-Erbschaft
  angenommen) — IST: CSS-Klasse löst weiß auf (Probe-Dump: Button #FFFFFF wie Hammer); Test
  assertet jetzt CSS-Weiß gegen bewusst nicht-weißem Shell-Hintergrund (falsifizierbar).

## Zyklus-Übriges — ✅ KOMPLETT (Review bestanden, Mutation-Proof, nicht gemerged)

Branch 8 Commits, Working tree clean (vor Runde-2-Fix). Surefire 738/0.
- **R18 ✅** Compact ohne Cascade (Mutation-Proof: Cascade → rot AiPoAgentTest.java:201; Guard-Mutation → 7 Tests).
- **Header-Compact-Buttons ✅ gebaut** (Da Thinka/Da Mek/Da Dok, kein Da-Boss-Button; `AiAgentStatusModel.compactEnabled/compactResult`, `AIChatView.doCompressAgent`) — **User-Smoke offen (Runde 2).**
- **R19 ✅** Clear-Cascade + Homepage `usage/agents.md` (Build-Script `docs:build`, npm via nvm).
- Da Dok: CONCERNS nur Kosmetik (gefixt `dcec8f9`); Docs geflippt `845e688`, Plan archiviert `d5f32dd`.
- **Merge/Squash = User.** open-points.md: PoDelegateTool-„compacted."-Misreport ❓, homepage peon-po.md ohne Da Dok ❓, User-Smoke ❓.


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
