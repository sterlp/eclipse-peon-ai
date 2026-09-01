# Skill knowledge

Compact catalog of reusable patterns learned from implementation iterations. Each entry links to evidence or a focused pattern page and states **problem · root cause · proven response**.

## esbuild native binary hangs on macOS 26.6.2 (VitePress `docs:build` unusable)

- **Problem:** `npm run docs:build` in `homepage/` (VitePress → Vite → esbuild) hangs at startup. The esbuild native binary (arm64, valid Mach-O, ad-hoc signed, no quarantine) hangs on *any* invocation (`--version`, transform). Fresh reinstall and both installed node versions hang; a `config.mjs` workaround does not help.
- **Root cause:** Environment-level (macOS 26.6.2), not a code or content problem.
- **Proven response:** Treat the homepage `docs:build` gate as **deferred** — commit the homepage content with the note `homepage docs:build gate deferred (esbuild/macOS-26.6.2 env-blocker)` and do **not** modify the VitePress/Vite/esbuild dependency chain without explicit approval. Re-run the gate once the user's environment is fixed.
- **Evidence:** Cycle 2c (inc-18…inc-21, 2026-08-30) — all four homepage increments committed with the deferred-gate note; core (`mvn test`) and plugin (Maven package + Eclipse suite) green throughout.
