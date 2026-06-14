# Plan: Issue #82 - Advanced Configuration Page & Skills Directory Resolution
https://github.com/sterlp/eclipse-peon-ai/issues/82
## Overview
Split AI Peon configuration into simple and advanced views, add per-agent model selection (search, plan, dev), and ensure skills/commands directories resolve to either `.claude` or `.llmpeon` at first launch.

## Task List

| # | Task | Status |
|---|------|--------|
| 01 | [Add preference constants](task-01-preference-constants.md) | ✅ Done |
| 02 | [First-launch directory resolution](task-02-directory-resolution.md) | ✅ Done |
| 03 | [Per-agent model fallback in LlmConfig](task-03-per-agent-models.md) | ⚠️ Partial - needs agent wiring (CRITICAL BUG) |
| 04 | [Create AiAdvancedPreferenceView](task-04-advanced-preference-view.md) | ✅ Done |
| 05 | [Register advanced preference page](task-05-plugin-xml-registration.md) | ✅ Done |
| 06 | [Unit tests for resolution and fallback](task-06-unit-tests.md) | ✅ Done (partial - per-agent model only; directory resolution requires Eclipse runtime integration test) |
| 07 | [Update design documentation](task-07-documentation.md) | ✅ Done |
| **NEW** | [Wire agents to use per-agent models](issue-fix-per-agent-models-and-docs.md#issue-1-per-agent-models-not-wired-to-agents) | 🔴 OPEN - CRITICAL |
| **NEW** | [Remove Design section from VitePress sidebar](issue-fix-per-agent-models-and-docs.md#issue-2-vitepress-sidebar-has-broken-design-section) | 🟡 OPEN |
| **NEW** | [Add user documentation for advanced config](issue-fix-per-agent-models-and-docs.md#issue-3-user-documentation-missing-for-per-agent-models) | ✅ Done (created `/doc/docs/setup/advanced-configuration.md`) |

## Critical Issues Discovered Post-Implementation

### Issue 1: Per-Agent Models Not Used by Agents (CRITICAL)
**Problem**: `LlmConfig` stores per-agent models with fallback getters, but **all agents receive the same shared `ConfiguredModel`** regardless of agent-specific settings.

**Impact**: Users can configure different models for search/plan/dev agents, but these settings are ignored - all agents use only the default model.

**Fix Required**: Modify `PeonAiService.java` to build separate `ConfiguredModel` instances per agent using the appropriate getter from `LlmConfig`.

### Issue 2: VitePress Sidebar Has Broken Design Section
**Problem**: VitePress config includes a "Design" sidebar section with links to `/design/*` paths. These resolve to `doc/docs/design/` which **does not exist** — design docs are in `doc/design/` (outside srcDir).

**Impact**: Users see broken links in documentation; design docs should not be in user-facing VitePress per AGENTS.md guidelines.

**Fix Required**: Remove the entire "Design" section from sidebar configuration.

### Issue 3: User Documentation Missing for Per-Agent Models
**Problem**: The `configuration.md` file does not document per-agent model configuration, temperature settings, debug mode, or query/header parameters.

**Impact**: Users cannot discover or understand how to use the advanced preference page features.

**Fix Required**: Create new documentation file at `/doc/docs/setup/advanced-configuration.md` explaining all advanced options and their impact.

## Design Decisions Summary

| Decision | Rationale |
|----------|-----------|
| Three new preference keys for per-agent models | Fine-grained model control without breaking existing setups |
| Model fallback chain: agent-specific → PREF_MODEL | Backward compatible; users with only default model continue working |
| One-time directory resolution at first launch | Deterministic behavior; avoids filesystem I/O on every config load |
| Fallback directory: `~/.llmpeon/skills` and `~/.llmpeon/commands` | Aligns with project identity (`llmpeon-core`, plugin ID `org.sterl.llmpeon`) |
