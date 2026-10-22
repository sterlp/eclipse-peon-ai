# Icons — UI-Rollen & Tool-Output-Symbole

**Status:** 🚧 in Umsetzung — UI-Icons ❌ specified (2026-08-30/31, Night-Cycle C); **Tool-Output-Symbole
✅ entschieden (2026-10-16, Paul) + Rollout gebaut (S1 `2209215e`, S2 `e216f00d`, Da-Dok ACCEPTED);
S3 (PoDelegate/AgentsTool) offen bis Pauls WIP-Merge.**

## Zweck

Single Source of Truth für die Icons/Symbole, die Peon verwendet: welche Rolle welches Icon trägt,
wo es angezeigt wird und wie es typografisch gerendert wird. LLM-Chat-Output (Markdown) ist NICHT
gebunden — dort dürfen Emoji frei verwendet werden.

## Tool-Output-Symbole (onTool — entschieden 2026-10-16, Paul)

Jedes onTool-Zeile beginnt mit dem Symbol seiner Operation — **eine Bedeutung, ein Symbol**, über
beide Tool-Familien (disk + eclipse, AGENTS „one behaviour, one implementation"). Symbol = erstes
Token, dann ein Leerzeichen, dann der bestehende Text.

| Symbol | Operation | LLM sichtbar in | Status |
|---|---|---|---|
| 🔍 | Search/Grep | `🔍 grep '…'` / `🔍 disk grep '…'` | ✅ gebaut (Paul, `2ff69adb` + WIP Eclipse-Seite) |
| 📜 | Read (Datei lesen) — **Scope: exakt `diskReadFile` + `eclipseReadFile`** (List/Console/WebFetch bewusst symbol-frei, Plan §3.6) | `📜 Reading …` | ✅ gebaut (S1 `2209215e`, rot-erst core+PDE) |
| 🗜 | Compact | Compact-Session (Result + onTool, COMPACTED/SKIPPED/FAILED) + Start-Zeile `🗜 Compressing conversation …` | ✅ gebaut (S2 `e216f00d`); **S3 offen:** PoDelegate/AgentsTool-Compact-Pfade erst nach Pauls WIP-Merge; präexistent: `ToolService:246` „🗜 Compact hint" (R-CC-Ära) |

**Regeln:**
- Ein Symbol nur für EINE Operation; Operationen ohne Symbol (z. B. Write) bleiben symbol-frei,
  bis Paul sie vergibt (kein stiller Wildwuchs — neue Symbole nur hier eintragen).
- Legende für den User: README.md (§ Symbole); die Homepage-Doku ist optional zu ergänzen, wenn
  weitere Symbole dazukommen.
- Da Sniffa hat 🔍 dafür freigegeben — die Agenten-Personen reservieren es nicht mehr.

## Rollen → Icons (UI)

### Funktions-Rollen (User-Vorschlag 2026-08-30)

| Rolle | Icon | Kontext (wo angezeigt) | Typografie-Regel |
|---|---|---|---|
| Compact | 🗜 | Chat-Header (Agent „Compact"), Status-Leiste | Emoji im Font-Size des umgebenden Texts, kein Skalieren |
| SKILL | 🧩 | Chat-Header (Skill-Ausführung), Status-Leiste | dito |
| Command | 🪄 | Chat-Header (Command-Ausführung), Status-Leiste | dito |

### Agenten-Personen (Vorschlag — offen, User-Q1)

| Rolle | Icon (Vorschlag) | Kontext | Status |
|---|---|---|---|
| Peon-PO (Jon) | 🧙 | Chat-Header, Status-Leiste | ❌ offen (Q1) |
| Peon-Plan (Da Thinka) | 📐 | Chat-Header, Status-Leiste | ❌ offen (Q1) |
| Peon-Dev (Da Mek) | 🔨 | Chat-Header, Status-Leiste | ❌ offen (Q1) |
| Da Sniffa (Search) | *(offen)* | Chat-Header, Status-Leiste | ❌ offen (Q1) — 🔍 an die Tool-Output-Symbole übergeben (2026-10-16, Paul) |
| Da Scribe | ✍️ | Chat-Header, Status-Leiste | ❌ offen (Q1) |
| Custom Agent | 📦 | Chat-Header, Status-Leiste | ❌ offen (Q1 + Q3) |

## Kandidaten-Pool

| Icon | Status | Anmerkung |
|---|---|---|
| 🗜 | zugewiesen | Compact (UI-Rolle + Tool-Output) |
| 🧩 | zugewiesen | SKILL |
| 🪄 | zugewiesen | Command |
| 🔍 | zugewiesen | Tool-Output „Search/Grep" (2026-10-16, Paul) |
| 📜 | zugewiesen | Tool-Output „Read" (2026-10-16, Paul; Wahl gegen 📝 — 📝 ist Write/Edit-vorbereitet) |
| 📦 | reserviert | Custom Agent (Q3) |
| 🔧 | frei | |
| 🛠 | frei | (Variante von 🔧) |
| ✅ | frei | Status-Icon? (Q2) |
| ❌ | frei | Status-Icon? (Q2) |
| 🎯 | frei | |
| ⚡ | frei | |
| 🧙 | reserviert | Peon-PO (Q1) |
| 📐 | reserviert | Peon-Plan (Q1) |
| 🔨 | reserviert | Peon-Dev (Q1) |
| ✍️ | reserviert | Da Scribe (Q1) |
| 📝 | frei | Write/Edit-Kandidat (bewusst frei, Paul 2026-10-16: Read = 📜) |
| ⇄ | in Benutzung | Cache-Reads im Token-Header (2c, R5 caching.md) |

## Typografie-Regeln

1. **Font-Size:** Emoji immer im Font-Size des umgebenden Texts (Chat-Header: Header-Font,
   Status-Leiste: Status-Font) — kein Skalieren, kein eigener Font.
2. **Plattform-Rendering:** macOS rendert Color Emoji, Windows Segoe UI Emoji — SWT übernimmt;
   kein manuelles Font-Setting für Emoji-Labels.
3. **Fallback:** rendert das Emoji nicht (Box/Question-Mark), wird das Text-Label OHNE Emoji
   angezeigt (kein Box-Rendering im UI).
4. **Position:** Icon VOR dem Text (🗜 Compact, 🔍 grep), ein Leerzeichen Abstand.
5. **Chat-Text (Markdown):** NICHT gebunden — LLM-Output darf Emoji frei verwenden; die
   Definition bindet nur UI-Widgets (Header, Status-Leiste, Buttons). Die Tool-Output-Symbole
   sind die Brücke: onTool-Zeilen tragen sie, der Chat rendert sie im Markdown.

## Offene Fragen (User)

- **Q1:** Agenten-Personen mit Icons (🧙/📐/🔨/✍️/📦) oder nur Funktions-Rollen (🗜/🧩/🪄)?
- **Q2:** ✅/❌ als Status-Icons in der UI (z. B. Test-/Build-Status im Header) oder nur in Docs?
- **Q3:** 📦 = Custom Agent (Vorschlag) oder „Paket/Feature“?

## Umsetzung (SOLL)

- Tool-Output-Symbole: **S1 (📜) + S2 (🗜) gebaut** (`2209215e`/`e216f00d`, Da-Dok ACCEPTED 2026-10-16,
  Gates Surefire 1126/0 + PDE 351/0); S3 = Follow-up nach Pauls WIP-Commit (PoDelegateTool/AgentsTool).
- UI-Icons (Header/Status-Leiste): eigener Build-Zyklus (UI-Widgets)
  (`AiAgentStatusWidget`/`ActionsBarWidget`-Erweiterung) — NICHT im Symbol-Rollout.
