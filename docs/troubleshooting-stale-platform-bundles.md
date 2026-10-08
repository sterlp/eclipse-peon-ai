# Troubleshooting — stale platform bundles from pre-fix Peon AI update sites

> Applies to Peon AI update-site builds **before 2026-10** (the builds that were assembled with
> `includeAllDependencies=true`). Fixed builds ship only the Peon AI feature + plugin.
> See [issue-142-asm-conflict.md](issue-142-asm-conflict.md) for the analysis.

## Symptoms

Two different failure modes, both after installing an old Peon AI build on an Eclipse that is
**older** than the build's target platform:

1. **Eclipse does not start.** `.metadata/.log` (or `configuration/*.log`) shows a
   `Uses constraint violation`:

   ```
   Unable to resolve ... org.apache.aries.spifly.dynamic.bundle ... because it is exposed to
   package 'org.objectweb.asm' from resources org.objectweb.asm [9.9.1] and org.objectweb.asm [9.10.1]
   ...
   Application "org.eclipse.ui.ide.workbench" could not be found in the registry.
   ```

2. **Eclipse starts, but the "AI Peon" view is blank.** No error is logged;
   `AIChatView.createPartControl` is never invoked.

## Cause

Pre-fix update sites were built with `tycho-p2-repository-plugin` `includeAllDependencies=true`,
so the p2 repository contained the **entire** 2026-09 target platform. Installing that build on an
older Eclipse (e.g. 2026-03) copied platform bundles into the installation — notably:

| Bundle | Why it breaks |
|---|---|
| `org.objectweb.asm 9.10.1` | Sits next to the platform's `9.9.1`. `spifly` imports `org.objectweb.asm` from both → OSGi `uses` violation → `jsvg` → `org.eclipse.swt.svg` → workbench unresolved → Eclipse won't start. |
| `jakarta.annotation-api 3.0.0` | Sits next to the platform's `2.1.1`. `org.sterl.llmpeon` imports `jakarta.annotation [2.0.0,4.0.0)` → binds **3.0.0**, while `org.eclipse.e4.core.di` imports `[2,3)` → binds **2.1.1**. e4 matches `@PostConstruct` by annotation **Class identity** (`AnnotationProxy.isPresent` → `isAnnotationPresent(Class)`), so the two `PostConstruct` classes differ and `createPartControl` is never called → blank view. |

Both bundles remain as **orphans**: the Peon AI p2 profile no longer references them, but the files,
`bundles.info` and `artifacts.xml` still do. Uninstalling/reinstalling Peon AI does not remove them.

## Detect

In `<eclipse>/plugins` look for stray files such as:

```
org.objectweb.asm_9.10.1.jar
org.objectweb.asm.tree_9.10.1.jar
org.objectweb.asm.tree.analysis_9.10.1.jar
org.objectweb.asm.util_9.10.1.jar
jakarta.annotation-api_3.0.0.jar
```

and confirm they are also listed in
`<eclipse>/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info`.

## Fix — script (recommended)

[`clear-stale-platform-bundles.ps1`](clear-stale-platform-bundles.ps1) removes exactly these
orphans from `plugins/`, `bundles.info` and `artifacts.xml`, with backups.

```powershell
# 1. Close Eclipse completely (File > Exit; make sure no eclipse.exe / javaw.exe remains)
# 2. Preview (no changes):
powershell -ExecutionPolicy Bypass -File .\clear-stale-platform-bundles.ps1 -EclipseHome "E:\eclipse" -DryRun
# 3. Apply:
powershell -ExecutionPolicy Bypass -File .\clear-stale-platform-bundles.ps1 -EclipseHome "E:\eclipse"
# 4. Start Eclipse once with a clean OSGi cache / fresh model:
E:\eclipse\eclipse.exe -clean -clearPersistedState
```

## Fix — manual steps

1. **Close Eclipse** completely (verify no `eclipse.exe`/`javaw.exe` in Task Manager).
2. **Back up** `<eclipse>/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info`
   and `<eclipse>/artifacts.xml`.
3. **Delete** the stray JARs from `<eclipse>/plugins`
   (`org.objectweb.asm*_9.10.1.jar`, `jakarta.annotation-api_3.0.0.jar`, incl. `.source`).
4. In `bundles.info`, **delete every line** starting with
   `org.objectweb.asm,9.10.1,`, `org.objectweb.asm.tree,9.10.1,`,
   `org.objectweb.asm.tree.analysis,9.10.1,`, `org.objectweb.asm.util,9.10.1,`,
   `org.objectweb.asm.commons,9.10.1,` (and their `.source` counterparts),
   plus `jakarta.annotation-api,3.0.0,`.
5. In `artifacts.xml`, **delete the matching `<artifact … id='…' version='9.10.1'>…</artifact>`
   / `version='3.0.0'` blocks** (update the `<artifacts size='…'>` value if present).
6. Start Eclipse once with `-clean -clearPersistedState`.
7. In Eclipse, **remove the old Peon AI update site** (Window > Preferences > Install/Update >
   Available Software Sites) so it cannot be re-added, then install the current build.

## Prevent

* Do **not** re-add an old Peon AI update site. Old sites still serve the broken build.
* The current update site cannot leak platform bundles: the build uses the default
  `includeAllDependencies=false` and CI asserts the generated repository contains **only**
  `org.sterl.*` plugins.

## Verification

After the fix, a clean install has exactly one `org.objectweb.asm` family (the platform's, e.g.
`9.9.1`) and exactly one `jakarta.annotation` major line (e.g. `2.1.1`), and the bundle pool is
consistent — no `truly missing` entries. The AI Peon view then renders.
