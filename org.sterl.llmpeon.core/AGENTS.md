See parent: `/llmpeon-parent/AGENTS.md` (Global Rules).
Module docs index: [docs/index.md](docs/index.md) — feature stories of this module (`llmpeon-core`).
Alias for `org.sterl.llmpeon.core`.

# Core Specifics
- **Shared lib for two products** (Eclipse plugin + AI Factory web app). Before extending a core
  contract read [docs/ground-rules.md](docs/ground-rules.md) — extend only if clearly generic for all.
- Use Lombok for POJOs.
- Run tests with Maven Surefire (ground truth, see parent) — build/test recipes: skill `peon-build-test`.