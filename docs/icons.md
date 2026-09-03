# UI-Icons — Rolle & Typografie (SOLL)

**Status:** ❌ specified (2026-08-30/31, Night-Cycle C) — SOLL-Definition für UI-Icons;
Umsetzung = eigener Build-Zyklus (UI-Widgets), NICHT in 2a/2b/2c.

## Zweck

Single Source of Truth für die Icons, die Peon in der UI verwendet: welche Rolle welches Icon
trägt, wo es angezeigt wird und wie es typografisch gerendert wird. LLM-Chat-Output (Markdown)
ist NICHT gebunden — dort dürfen Emoji frei verwendet werden.

## Rollen → Icons

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
| Da Sniffa (Search) | 🔍 | Chat-Header, Status-Leiste | ❌ offen (Q1) |
| Da Scribe | ✍️ | Chat-Header, Status-Leiste | ❌ offen (Q1) |
| Custom Agent | 📦 | Chat-Header, Status-Leiste | ❌ offen (Q1 + Q3) |

## Kandidaten-Pool

| Icon | Status | Anmerkung |
|---|---|---|
| 🗜 | zugewiesen | Compact |
| 🧩 | zugewiesen | SKILL |
| 🪄 | zugewiesen | Command |
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
| 🔍 | reserviert | Da Sniffa (Q1) |
| ✍️ | reserviert | Da Scribe (Q1) |
| ⇄ | in Benutzung | Cache-Reads im Token-Header (2c, R5 caching.md) |

## Typografie-Regeln

1. **Font-Size:** Emoji immer im Font-Size des umgebenden Texts (Chat-Header: Header-Font,
   Status-Leiste: Status-Font) — kein Skalieren, kein eigener Font.
2. **Plattform-Rendering:** macOS rendert Color Emoji, Windows Segoe UI Emoji — SWT übernimmt;
   kein manuelles Font-Setting für Emoji-Labels.
3. **Fallback:** rendert das Emoji nicht (Box/Question-Mark), wird das Text-Label OHNE Emoji
   angezeigt (kein Box-Rendering im UI).
4. **Position:** Icon VOR dem Text (🗜 Compact), ein Leerzeichen Abstand.
5. **Chat-Text (Markdown):** NICHT gebunden — LLM-Output darf Emoji frei verwenden; die
   Definition bindet nur UI-Widgets (Header, Status-Leiste, Buttons).

## Offene Fragen (User)

- **Q1:** Agenten-Personen mit Icons (🧙/📐/🔨/🔍/✍️/📦) oder nur Funktions-Rollen (🗜/🧩/🪄)?
- **Q2:** ✅/❌ als Status-Icons in der UI (z. B. Test-/Build-Status im Header) oder nur in Docs?
- **Q3:** 📦 = Custom Agent (Vorschlag) oder „Paket/Feature“?

## Umsetzung (SOLL)

- Eigener Build-Zyklus (UI-Widgets): Chat-Header + Status-Leiste tragen die Icons
  (`AiAgentStatusWidget`/`ActionsBarWidget`-Erweiterung).
- Kein Scope in 2c (Advanced-Config) — bewusste Trennung.
