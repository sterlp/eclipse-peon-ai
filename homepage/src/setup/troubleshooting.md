---
title: Troubleshooting
description: Fix Eclipse won't start or a blank AI Peon view after installing an old Peon AI version
---

# Troubleshooting

## Eclipse won't start — or the "AI Peon" view is blank — after installing an older Peon AI

Old Peon AI builds (before 2026-10) accidentally shipped parts of the **Eclipse platform itself**
in the update site. If you installed such a build on an Eclipse older than the platform it was
built for, two orphan bundles can stay behind and keep breaking the IDE even after Peon AI is
uninstalled:

- **Eclipse does not start.** The log shows a `Uses constraint violation` involving
  `org.objectweb.asm` versions `9.9.1` and `9.10.1`, ending with
  `Application "org.eclipse.ui.ide.workbench" could not be found in the registry`.
- **Eclipse starts, but the AI Peon view is blank.** Nothing is logged.

The culprits are leftover `org.objectweb.asm 9.10.1` and `jakarta.annotation-api 3.0.0` bundles.

### Quick fix

1. Close Eclipse completely (make sure no `eclipse.exe` / `javaw.exe` is running).
2. In your Eclipse `plugins` folder, delete these files (skip any that are absent):
   - `org.objectweb.asm_9.10.1.jar`
   - `org.objectweb.asm.tree_9.10.1.jar`
   - `org.objectweb.asm.tree.analysis_9.10.1.jar`
   - `org.objectweb.asm.util_9.10.1.jar`
   - `org.objectweb.asm.commons_9.10.1.jar`
   - `jakarta.annotation-api_3.0.0.jar`
3. Open `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info` and delete every
   line that starts with one of those bundle names followed by `,9.10.1,` or `,3.0.0,`.
4. Start Eclipse once with a clean cache:
   `eclipse.exe -clean -clearPersistedState`
5. Install the **current** Peon AI from the up-to-date update site. Under
   *Available Software Sites*, remove any old Peon AI site so it cannot be used again.

A ready-to-run PowerShell script does steps 2–3 for you (with backups): see
`docs/clear-stale-platform-bundles.ps1` in the repository.

```powershell
# preview, then apply (point -EclipseHome at the folder containing eclipse.exe)
powershell -ExecutionPolicy Bypass -File .\clear-stale-platform-bundles.ps1 -EclipseHome "C:\eclipse" -DryRun
powershell -ExecutionPolicy Bypass -File .\clear-stale-platform-bundles.ps1 -EclipseHome "C:\eclipse"
```

The current Peon AI build only ships the Peon AI plugin, so this cannot happen again.

## The view is named "AI Peon", not "AI Chat"

In *Window > Show View > Other…*, search for **Peon** or expand the **AI** category. The view's
label is **AI Peon**.
