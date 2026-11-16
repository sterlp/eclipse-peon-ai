package org.sterl.llmpeon.context;

import java.time.LocalDate;

import org.sterl.llmpeon.agent.model.ContextInput;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.shared.LineSeparatorUtil;

public class StaticContextItem implements ContextItem {

    @Override
    public ContextInput render() {
        var ls = System.lineSeparator() + "- ";
        return new ContextInput("env", "Today: " + LocalDate.now()
        + " — APIs and libraries may have changed since your training cutoff. "
        + "Don't rely only on internal API knowledge — explore base classes and libs if possible with e.g. using "
        + EclipseCodeNavigationTool.GET_TYPE_SOURCE + " for java projects."
        + ls + "os name: " + System.getProperty("os.name")
        + ls + "os file.separator: '" + System.getProperty("file.separator") + "'"
        + ls + "os line.separator: " + LineSeparatorUtil.getDefaultLineSeparatorForLlm()
        + ls + "FILE ACCESS: workspace files -> eclipse* only; outside workspace -> disk* only. "
        + "After a disk* write, call eclipseRefreshProject or eclipseBuildProject to sync Eclipse. "
        + "Never use shell/terminal for file I/O."
        + ls + "user language: " + System.getProperty("user.language")
            + " — respond in the user's language; if unsure or not otherwise declared, use this one.");
    }

    @Override
    public String label() {
        return "Static env info";
    }

}
