---
idPrefix: BC
---

# Bounded Caches — RAM-Cache-Audit

> **Status:** ❌ specified (2026-10-17, Paul specified — Audit 2026-10-16, Nachtzyklus-5-
> Nachbereitung). Anlass: „dieser Cache, der sich selbst kürzt, sollte auch beim RegEx-Parser
> eingesetzt werden — überall wo wir cachen, sonst Memory-Leak". Verwandt:
> [temp-file-tool.md](temp-file-tool.md).

## Ziel

Ein geteilter, thread-sicherer `BoundedLruCache<K,V>` im core (`shared`) ersetzt die unbounded
statischen Caches. Ein Cache ohne Selbstkürzung ist ein Leak, das erst nach Tagen sichtbar wird.

## Audit (2026-10-16/17, codegraph + Grep über core)

| Cache | Key-Quelle | Gebunden? | Befund |
|---|---|---|---|
| `SearchQuery.CACHE` (shared/SearchQuery.java:10) | **LLM-kontrolliert** (`filter`-Param von Grep/Shell/Log) | ❌ unbounded | **echtes Leak** — jeder neue Filter-String landet ewig im Prozess-RAM |
| `RegexUtils.GLOB_CACHE` (shared/RegexUtils.java:11) | Agent-Configs, dynamische Validator-Globs | ❌ unbounded | praktisch klein, aber gleiche Form |
| `WebFetchTool.markdownCache` (WebFetchTool.java:43) | URLs | ✅ Cap 5, aber **FIFO statt LRU** (Insertion-Order-LinkedHashMap, kein accessOrder; get pflegt die Reihenfolge nicht) | Kapazität ok, Semantik-Label „LRU" ungenau |
| `ModelListCache` | ConnectionIdentity | ✅ endlich (Config-Key-Space) | keine Pflicht, wird nicht angefasst |
| `ThinkModelMapping.CACHE` | Provider-Enum | ✅ endlich | keine Pflicht, wird nicht angefasst |

## Business Rules

### R-BC-1 — Geteilter `BoundedLruCache<K,V>` ❌

GIVEN ein `BoundedLruCache` mit Cap N
WHEN N+1 verschiedene Keys geputt werden
THEN hält der Cache genau N Entries — der am längsten **nicht benutzte** wird evictet
     (echtes LRU: `get`/`put` eines bestehenden Keys frischt seine Reihenfolge auf)
→ `boundedLruEvictsLeastRecentlyUsed`, `getRefreshesRecency`

GIVEN parallele put/get aus mehreren Threads
THEN keine Korruption und keine Ausnahme (thread-safe; einfaches Synchronized-Muster, nicht
     lock-frei optimiert — Last ist niedrig, Korrektheit gewinnt)
→ `boundedLruIsThreadSafe`

Die API ist tief und klein: `get(K)`, `put(K,V)`, `computeIfAbsent(K, Function)`, `size()` —
keine Map-Sicht nach außen (kein Leak des Eviction-Verhaltens).

### R-BC-2 — `SearchQuery` adoptiert (Cap 128) ❌

GIVEN 129 verschiedene `filter`-Strings laufen durch `SearchQuery.of(...)`
WHEN danach `size()` geprüft
THEN hält der Cache **128** Entries — kein ungegrenztes Wachstum mehr
→ `searchQueryCacheBoundedAt128`

GIVEN derselbe Filter-String wiederholt sich
THEN das Verhalten ist unverändert (Compile-Ergebnis gecacht, regex/literal-Fallback identisch)
→ Bestandstests bleiben grün (keine neuen, das Verhalten deckt `SearchQueryTest` ab).

### R-BC-3 — `RegexUtils` adoptiert (Cap 128) ❌

GIVEN 129 verschiedene Globs laufen durch `globToPattern(...)`
THEN hält der Cache 128 Entries
→ `globCacheBoundedAt128`

### R-BC-4 — `WebFetchTool` adoptiert (Cap 5 bleibt, echtes LRU) ❌

GIVEN die handgerollte `LinkedHashMap` (Insertion-Order) wird durch `BoundedLruCache` ersetzt
THEN Cap 5 bleibt, Eviction ist **echtes LRU** (Recency-Refresh beim get), das synchronized-
     Drumherum entfällt (die Komponente ist selbst thread-safe)
→ `webFetchCacheUsesBoundedLru` (Cap- + Recency-Nachweis am Tool-Verhalten, nicht am Feld)

### R-BC-5 — Repo-Regel „Every RAM cache is bounded." ❌

`AGENTS.md` erhält die Regel: Ein RAM-Cache ohne Selbstkürzung ist ein Leak — jeder neue Cache
im Code nutzt `BoundedLruCache` (oder hat einen endlichen Key-Space und sagt es). Kein Test
dokumentation-only.

## Nicht angefasst

- `ModelListCache`, `ThinkModelMapping.CACHE` — endlicher Key-Space, bewusst keine Pflicht
  (Audit-Tabelle oben).
- Caps sind Konstanten (128/5), **keine** User-Config (Paul: keine Konfigurationsfläche).