package org.sterl.llmpeon.context;

import java.time.LocalDate;

import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.shared.LineSeparatorUtil;

public class StaticContextItem implements ContextItem {

    @Override
    public String render() {
        return "Today: " + LocalDate.now()
        + " — APIs and libraries may have changed since your training cutoff. "
        + "Don't rely only on internal API knowledge — explore base classes and libs if possible with e.g. using "
        + EclipseCodeNavigationTool.GET_TYPE_SOURCE + " for java projects."
        + "\nos name: " + System.getProperty("os.name")
        + "\nos file.separator: '" + System.getProperty("file.separator") + "'"
        + "\nos line.separator: " + LineSeparatorUtil.getDefaultLineSeparatorForLlm() + "'"
        + "\nFile access: prefer eclipse* over disk* tools. After disk* writes, call eclipseRefreshProject (refresh only) or eclipseBuildProject (refresh + build check) to sync Eclipse."
        + "\nOutside the workspace, use Disk-tools if available; if not, ask the user to enable them. Never use shell/terminal for file I/O.";
    }
    @Override
    public String label() {
        return "Static env info";
    }

}
