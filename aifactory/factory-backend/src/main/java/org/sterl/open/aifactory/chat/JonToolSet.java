package org.sterl.open.aifactory.chat;

import java.nio.file.Path;
import java.util.List;
import java.util.function.IntSupplier;

import org.sterl.llmpeon.docslinter.DocsIdTool;
import org.sterl.llmpeon.docslinter.DocsLinterTool;
import org.sterl.llmpeon.poagent.tools.PoDelegateTool;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.tool.DynamicRootsWriteValidator;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.ShellTool;
import org.sterl.llmpeon.tool.web.WebGetTool;
import org.sterl.open.aifactory.shared.TmpRoots;

/**
 * The tools Jon and his team get for one project, and the two roots they may write to (R-WP-6,
 * R-WP-12). One instance per project; the core owns everything it registers, so the factory adds no
 * tool of its own and no Eclipse tool can reach this set (ADR-0024, ADR-0026).
 *
 * <p>The whole set is built here and nowhere else: UC-WP-17 is a statement about this class, not about
 * the agent.</p>
 */
public final class JonToolSet {

    private final ToolService tools;
    private final DynamicRootsWriteValidator writeValidator;

    private JonToolSet(ToolService tools, DynamicRootsWriteValidator writeValidator) {
        this.tools = tools;
        this.writeValidator = writeValidator;
    }

    /**
     * @param projectRoot the project's checkout — the write root of Jon and Dev, and the working
     *                    directory of every path-based and shell tool
     */
    public static JonToolSet forProject(Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        ToolService tools = new ToolService(true);
        tools.addTool(new DiskFileReadTool(root));
        tools.addTool(new DiskFileWriteTool(root));
        tools.addTool(new DiskGrepTool(root));
        DocsLinterTool linter = new DocsLinterTool(root);
        tools.addTool(linter);
        tools.addTool(new DocsIdTool(linter));
        tools.addTool(new WebGetTool());
        // the supplier, not a fixed path: the shell reads its default at call time (R3)
        tools.getTool(ShellTool.class).ifPresent(shell -> shell.setDefaultWorkingDir(() -> root));
        return new JonToolSet(tools, new DynamicRootsWriteValidator(root, () -> List.of(TmpRoots.systemTmp())));
    }

    /**
     * The delegation tool Jon needs to reach his team. Registered before the agent is built, otherwise
     * the first turn cannot see it. No standing orders (D6): order texts are a story field of their own.
     */
    public void addDelegateTool(AiAgent plan, AiAgent review, AiAgent dev,
            IntSupplier autoCompactAfter) {
        tools.addTool(new PoDelegateTool(plan, review, dev, unused -> List.of(), autoCompactAfter));
    }

    public ToolService tools() {
        return tools;
    }

    /**
     * Project checkout plus system temp — the two roots of R-WP-13. Read by the core at validate time
     * and again at prompt time, so a root is never cached and the agent's prompt cannot age behind the
     * check. Handed to the agent through {@code getWriteValidator()} for Jon and Dev; Plan and
     * Review keep their core validators (Q5).
     */
    public WriteValidator writeValidator() {
        return writeValidator;
    }
}
