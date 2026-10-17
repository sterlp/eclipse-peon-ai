# Bounded Caches — RAM-Cache-Audit

> **Status:** 🚧 Idee (2026-10-16, Paul — Nachtzyklus 5 Nachbereitung) · Anlass: Cache-Audit im
> Gespräch über WebFetch („dieser Cache, der sich selbst kürzt, sollte auch beim RegEx-Parser
> eingesetzt werden — überall wo wir cachen, sonst Memory-Leak") · Verwandt:
> [temp-file-tool.md](temp-file-tool.md)

## Audit (2026-10-16, codegraph + Grep über core)

| Cache | Key-Quelle | Gebunden? | Befund |
|---|---|---|---|
| `SearchQuery.CACHE` (shared/SearchQuery.java:10) | **LLM-kontrolliert** (`filter`-Param von Grep/Shell/Log) | ❌ unbounded | **echtes Leak** — jeder neue Filter-String landet ewig im Prozess-RAM |
| `RegexUtils.GLOB_CACHE` (shared/RegexUtils.java:11) | Agent-Configs, dynamische Validator-Globs | ❌ unbounded | praktisch klein, aber gleiche Form |
| `WebFetchTool.markdownCache` | URLs | ✅ LRU 5 | per-Entry-Bytes unbeschränkt (5 ganze Seiten) — Hinweis |
| `ModelListCache` | ConnectionIdentity | ✅ | endlich durch Config-Key-Space |
| `ThinkModelMapping.CACHE` | Provider-Enum | ✅ | endlich |

## SOLL-Idee

- Geteilte **`BoundedLruCache<K,V>`** im core/shared (thread-safe — Muster: `WebFetchTool`-LRU),
  adoptiert von `SearchQuery` (Cap ~128, Queries wiederholen sich oft) + `RegexUtils` (~128),
  `WebFetchTool` (Cap 5 bleibt). `ModelListCache`/`ThinkModelMapping` optional (Key-Space endlich —
  keine Pflicht).
- **Regel-Kandidat für AGENTS.md:** „Every RAM cache is bounded." Ein Cache ohne Selbstkürzung ist
  ein Leak, das erst nach Tagen sichtbar wird.
- Tests: LRU-Eviction je adoptiertem Cache; SearchQuery-Cap-Nachweis (N+1 verschiedene Filter →
  Cache hält N).