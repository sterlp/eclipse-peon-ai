# Built-in Agent Prompt Override

**Status: 🚧 Idee (User, 2026-09-06)** — nur festgehalten, nicht eingeplant, keine Rules/BDD.

## Idee

Die Prompts der Built-in-Agenten (Peon-PO/Plan/Dev/Scaffold) verlassen die Eclipse-Resources und
leben als Config im `.peon`-Verzeichnis. Semantik:

- Kein „Body" vorhanden → System-Prompt = eingebauter Default.
- Body vorhanden → **übersetzt/supersedes** den Built-in-Prompt (Customization ohne Code-Änderung).

**WEIL (User):** Hoffnung auf einen Improvement-Cycle — Nutzer teilen Prompt-Verbesserungen mit dem
Projekt zurück; der PO-Agent kann Prompt-Anpassungen ebenfalls beauftragen (Scaffold-Side-Quest).

## Offene Design-Fragen (später)

- Full-Replace vs. Append bei Override (Tendenz: Full-Replace, Default bleibt built-in).
- Datei-Layout (Body im bestehenden `agents/<name>/AGENT.md`? eigenes Layout für Built-ins?).
- Upgrade-/Staleness-Risiko: Repo-Prompt verbessert sich, User-Override bleibt alt stehen.
