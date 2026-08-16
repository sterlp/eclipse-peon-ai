# Architecture — Component Rules

**Status:** 🚧 in design · **Datum:** 2026-08-15

## Purpose

Common component architecture for all LlmPeon modules. Ensures consistent layering,
responsibility boundaries and testability. We follow the skill `component-architecture`
adapted for Eclipse/OSGi (no Spring, no Transactions).

## Base Packages

| Module | Base Package |
|--------|-------------|
| `org.sterl.llmpeon.core` | `org.sterl.llmpeon` |
| `org.sterl.llmpeon` (plugin) | `org.sterl.llmpeon.parts` |

## Type Categories

| Type | Suffix | Visibility | Role | Extract When |
|------|--------|-----------|------|--------------|
| **Service** | `*Service` | Public API | Orchestration, wiring, lifecycle | — |
| **Component** | `*Component` | Private (called by Service) | Low-level logic, single responsibility | >10 lines + unit-testable |
| **View** | `*View` | Public (Eclipse ViewRegistry) | Whole page, controller + UI composition | — |
| **Widget** | `*Widget` | Private (composed by View) | Single UI function (header, input, status) | — |
| **Initializer** | `*Initializer` | Internal | One-time setup/config | — |

## Golden Rule

> **Service = public API. Everything else = private.**

Services are called from the View or other Services. Components are called only by their
owning Service. Widgets are composed only by their View.

## Fixed Services

| Service | Module | Responsibility |
|---------|--------|---------------|
| `AgentService` | core | Agent lifecycle, registry, active agent |
| `ToolService` | core | Tool registry, executor management, `executeLoop` |
| `PeonAiService` | plugin | Eclipse facade — project, handoff, status, config |

Components are extracted from these Services when a method grows >10 lines and needs
independent testing. No pre-emptive splitting.

## View / Widget Layer

- **View** (`AIChatView`) = one page. Owns the send loop, agent switching, lifecycle.
- **Widget** = single UI concern (header bar, status line, input, chat rendering).
- A View composes Widgets; Widgets never talk to other Widgets directly.

## Package Layout (Plugin)

```
org.sterl.llmpeon.parts/
├── AIChatView.java          # The one View
├── PeonAiService.java       # The one Plugin Service (facade)
├── PeonConstants.java
├── agentsmd/                # AGENTS.md loading
├── config/                  # *Initializer classes
├── log/
├── model/
├── monitor/
├── shared/                  # Cross-cutting utilities (EclipseUtil, JdtUtil)
├── tools/                   # Eclipse-specific tools
└── widget/                  # UI components (composed by AIChatView)
```

## Rules

1. **No logic in View** beyond composition + event routing to Services.
2. **No Service calls another Service's Component** directly — go through the Service API.
3. **Extract to Component when:** method >10 lines AND you want a unit test for it.
4. **Components have no Service dependency** (no upward calls).
5. **One Component = one responsibility** — name it after what it does (`HandoffComponent`,
   `AgentContextComponent`, `ToolWiringComponent`).

## Error Handling (Exceptions) — ❌ specified (2026-08-16)

- **Niemals still swallowen:** ein `catch`, der ohne Log und ohne Rethrow einen
  Default-Wert zurückgibt (`catch (e) { return null; }`) ist ein Bug.
- **Log OR throw — nie beides** (Facade except, da die Exception den Kontext verlässt):
  - Exception wird **gehändelt** (Domain-Result wie `null`/skip ist valide) →
    **log mit Kontext** (Pfad/ID, das Problem) + Domain-Result zurückgeben.
  - Exception wird **delegiert** → **throwen**, nicht loggen.
- Log-Text trägt immer Kontext: ID/Pfad, das Problem, ggf. Workaround.

```
GIVEN eine Komponente fängt ein IOException beim Datei-Laden
AND "Datei fehlt → skip" (null) ist ein valides Domain-Result
WHEN die Exception auftritt
THEN wird mit Pfad und Cause geloggt
AND die Komponente gibt das Domain-Result zurück — nicht still
```

## BDD

```
GIVEN a Service method exceeds 10 lines of logic
AND the logic is unit-testable in isolation
WHEN we extract it
THEN it becomes a *Component in the same package
AND the Service delegates to it (one-liner)

GIVEN a new UI element for the chat view
WHEN it represents a single function (e.g. token counter)
THEN it is a *Widget in parts/widget/
AND it is composed by AIChatView

GIVEN a new service that needs public API access
WHEN multiple callers (View, other services) need it
THEN it is a *Service with public methods
AND its internals are extracted to *Components when they grow
```

## Constraints

- We only clean up what we touch — no big-bang refactoring.
- Existing names (`*View`, `*Widget`, `*Initializer`) are fixed and respected.
- Core module follows the same rules with base package `org.sterl.llmpeon`.
