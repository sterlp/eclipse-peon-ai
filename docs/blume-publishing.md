---
idPrefix: BLUME
---

# Docs als HTML veröffentlichen (Blume)

> **Status:** 🚧 Idee (2026-09-15, nicht geplant) — eigener Zyklus, **nicht** Teil des Docs-Linters.
> **Ziel:** Der `docs/`-Baum wird zusätzlich als lesbare HTML-Site veröffentlicht, ohne dass wir
> unser Doc-Konvent verbiegen.

## Warum hier festgehalten

Die Idee stammt vom User (2026-09-15, „nicht überbewerten, nur eine Idee"). Sie wird hier
festgehalten, weil **einzelne Entscheidungen im Docs-Linter-Zyklus sie billiger oder teurer machen**
— und weil die Kollisionen mit unserem Bestand real sind. Wer sie kennt, baut heute nichts, das
morgen umgebaut werden muss.

## Was Blume ist

[Blume](https://useblume.dev/docs) ist ein Markdown-first Doc-Framework auf Astro/Vite:
`.md`/`.mdx` in einen Ordner, `blume dev`, fertige statische HTML-Site mit Navigation, Suche,
SEO/OG-Images, `llms.txt`, JSON-API und MCP-Server. Navigation entsteht aus **Ordnerstruktur +
`meta.ts`**, nicht aus Tags.

## Kollisionen mit unserem Doc-Bestand (Stand 2026-09-15, ~66 Docs)

| # | Blume erwartet | Unser IST | Aufwand |
|---|---|---|---|
| 1 | **Kein H1 im Content** — `title` kommt aus dem Frontmatter, Content beginnt bei `##` | Jedes Doc beginnt mit `# Titel` | Bulk: `# X` → `title: X` |
| 2 | **Striktes Frontmatter** — *„Any key outside this reference fails the build"* | Bisher kein Frontmatter; `idPrefix` ist ein **eigener** Key | `frontmatter.extend` + Zod in `blume.config.ts` (Pflicht, nicht optional) |
| 3 | **Mermaid, Callouts, Math nur in `.mdx`** — in `.md` bleiben sie Rohtext | [AGENTS-PO.md](../AGENTS-PO.md) schreibt Mermaid-Sequenz-/Klassendiagramme für jede `{name}-architektur.md` vor | Bulk-Rename `.md` → `.mdx` (User: „im Bulk während wir veröffentlichen") |
| 4 | Tags gibt es als **`search.tags: [api]`** — eine **Such-Facette**, kein Navigationsmittel | `index.md` ist unsere Landkarte | Entscheidung offen (Q-B2) |

Weitere Fallstricke aus der Doku: eine Überschrift, die wörtlich auf `[toc]`/`[!toc]` endet, wird
als Marker interpretiert; `{#custom-id}` funktioniert nicht in `.mdx` (JSX-Konflikt) — dort
`[#custom-id]` nutzen; kein Inline-Math (`$…$`), nur Block-Math.

## Was wir dafür heute schon richtig machen

- **`idPrefix` steht im Frontmatter**, nicht als Blockquote im Text
  ([docs-linter.md](docs-linter.md) Q7) — Blume-Format ohne Extra-Migration.
- **Überschriften-Hierarchie** `## Business Rules` → `### R-…` → `#### UC-…` passt bereits exakt in
  Blumes Erwartung (Content ab `##`, `##`/`###` landen im TOC).

## Offene Punkte

| # | Frage | Status |
|---|---|---|
| Q-B1 | `frontmatter.extend`-Schema: nur `idPrefix` oder auch Status/Owner als Feld? | ❓ |
| Q-B2 | `search.tags` nutzen? Nützt erst mit Suche; solange `index.md` die Landkarte ist, gäbe es **zwei Wahrheiten** über die Einordnung eines Docs. | ❓ |
| Q-B3 | Alles auf `.mdx` oder nur die Architektur-Docs mit Mermaid? Gemischt = zwei Regeln im Kopf. | ❓ |
| Q-B4 | Veröffentlichen wir `docs/` überhaupt? Es ist bewusst **interne** Design-Spec — `homepage/` ist die Nutzer-Doku. Zielgruppe klären, bevor gebaut wird. | ❓ |

Q-B4 ist der eigentliche Vorfrage-Punkt: [index.md](index.md) sagt heute ausdrücklich
*„Not published — user-facing docs are in `homepage/`"*.
