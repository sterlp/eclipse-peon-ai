package org.sterl.llmpeon.context;

import java.time.LocalDate;

import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.shared.LineSeparatorUtil;

public class StaticContextItem implements ContextItem {

    @Override
    public String render() {
        var ls = System.lineSeparator();
        return "Today: " + LocalDate.now()
        + " — APIs and libraries may have changed since your training cutoff. "
        + "Don't rely only on internal API knowledge — explore base classes and libs if possible with e.g. using "
        + EclipseCodeNavigationTool.GET_TYPE_SOURCE + " for java projects."
        + ls + "os name: " + System.getProperty("os.name")
        + ls + "os file.separator: '" + System.getProperty("file.separator") + "'"
        + ls + "os line.separator: " + LineSeparatorUtil.getDefaultLineSeparatorForLlm()
        + ls + "File access: prefer eclipse* over disk* tools. After disk* writes, call eclipseRefreshProject (refresh only) or eclipseBuildProject (refresh + build check) to sync Eclipse."
        + ls + "Outside the workspace, use Disk-tools if available; if not, ask the user to enable them. Never use shell/terminal for file I/O."
        + ls + "user language: " + System.getProperty("user.language")
            + " — respond in the user's language; if unsure or not otherwise declared, use this one.";
    }
    @Override
    public String label() {
        return "Static env info";
    }

}
