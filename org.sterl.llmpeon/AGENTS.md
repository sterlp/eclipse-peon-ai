See parent: `/llmpeon-parent/AGENTS.md` (Global Rules, Thread Safety).
Module docs index: [docs/index.md](docs/index.md) — feature stories of this module (Eclipse plugin).

# Plugin Specifics

- **Error Handling**: Do not swallow exceptions. Return via dialog or `PeonConstants.status()`.
- **Test bundle = Fragment**: `org.sterl.llmpeon.test` is a `Fragment-Host: org.sterl.llmpeon` fragment — same classloader as the host. Test classes may mirror plugin packages (e.g. `org.sterl.llmpeon.parts.shared.EclipseUiUtilTest`) and legally call package-private host members.

