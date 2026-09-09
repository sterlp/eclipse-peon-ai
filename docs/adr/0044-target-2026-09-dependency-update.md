# ADR-0044: Target Platform 2026-09 + Dependency Update (tycho, jakarta.annotation, langchain4j)

**Status:** Accepted (2026-09-09, cycle story/lib-update-2026-09-09)

## Context

Bump attempt from target 2026-03 → 2026-06/09 failed: simrel repos ship
`jakarta.annotation` only as **3.0.0** since 2026-06 (2.1.1 was last in 2026-03;
1.3.5 remains). The plugin MANIFEST demanded
`Import-Package: jakarta.annotation;version="[2.1.0,3.0.0)"` → matches nothing.
The actually used API (`@PostConstruct`-style in `AIChatView`/`EclipseUtil`/`IoUtils`/`JdtUtil`)
is stable across 2→3.

Facts (verified against p2 content.xml, 2026-09-09): 2026-03 = {1.3.5, 2.1.1},
2026-06 = {1.3.5, 3.0.0}, 2026-09 = {1.3.5, 3.0.0}. langchain4j latest = 1.20.0
(pom was 1.19.0, core compiles green at 1.19.0). tycho 5.0.3 → 5.0.4 (patch).

## Decision

* Target platform: **2026-09** (user decision 2026-09-09; 2026-06 was the interim bump).
* `Import-Package: jakarta.annotation` widened to **`[3.0.0,4.0.0)`** — precise against the
  target we ship (clean break, no Orbit-location workaround, no dual providers).
* langchain4j **1.19.0 → 1.20.0**; lib/ jars regenerate via the existing
  `maven-dependency-plugin copy-dependencies` (stripVersion → Bundle-ClassPath
  filenames stay stable); MANIFEST Bundle-ClassPath must be re-synced if the
  transitive set changes.
* All other pinned libs: bump to latest stable **within the same major line**
  (lombok 1.18.x, mockito 5.x, junit-bom 5.x, slf4j 2.0.x, maven plugins) —
  no major jumps this cycle; anything needing a major is reported, not done.
* tycho 5.0.4 stays (user's uncommitted bump).

## Consequences

* Building against < 2026-06 platforms no longer works (jakarta.annotation 3.0 required).
  Accepted: the `.target` file is the single platform definition.
* jakarta.inject stays `[2.0.0,3.0.0)` — still provided by 2026-09 (verified by resolve).
* If a future langchain4j release adds/removes transitive jars, the
  MANIFEST `Bundle-ClassPath` ↔ `lib/` sync must be checked (plan step, not folklore).
