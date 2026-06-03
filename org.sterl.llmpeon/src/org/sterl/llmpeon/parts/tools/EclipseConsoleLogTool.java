package org.sterl.llmpeon.parts.tools;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.TextConsole;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseConsoleLogTool extends AbstractEclipseTool {

    @Tool("Eclipse: Read the content of the active console log - available e.g. after a test run to read the test logs.")
    public String readConsoleLog(
            @P(description = "Name of the console to read. If empty, reads the active console.", required = false, name = "consoleName")
            String consoleName) {

        var consoles = consoles();

        if (consoles.isEmpty()) {
            return "No message consoles available.";
        }

        var targetConsole = getConsole(consoleName, consoles);

        onTool("Reading console " + StringUtil.stripToEmpty(consoleName));
        if (targetConsole.isEmpty()) {
            return "Console not found. Available consoles:\n" + listAvailableConsoles();
        } else {
            String content = targetConsole.get().getDocument().get();
            return targetConsole.get().getName() + ":\n" + (StringUtil.hasValue(content) ? content : "empty");
        }
    }

    private Optional<TextConsole> getConsole(String consoleName, List<TextConsole> consoles) {
        if (StringUtil.hasValue(consoleName)) return consoles.stream()
                .filter(o -> o.getName().contains(consoleName) || o.getClass().toString().contains(consoleName))
                .findAny();
        
        Optional<TextConsole> result = Optional.empty();
        for (TextConsole textConsole : consoles) {
            result = Optional.of(textConsole);
            if (StringUtil.hasValue(textConsole.getDocument().get())) break;
        }
        return result;
    }
    
    @Tool("Eclipse: List all available consoles - e.g. for eclipse logs console etc.")
    public String listAvailableConsoles() {
        var consoles = consoles();

        onTool("List available consoles " + consoles.size());
        if (consoles.isEmpty()) {
            return "No consoles available.";
        }

        StringBuilder result = new StringBuilder();
        for (IConsole console : consoles) {
            if (console instanceof TextConsole) {
                result.append(StringUtil.hasValue(console.getName()) ? console.getName() : console.getClass()).append("\n");
            }
        }

        return result.toString().trim();
    }
    
    private List<TextConsole> consoles() {
        return Arrays.asList(ConsolePlugin.getDefault().getConsoleManager().getConsoles())
            .stream()
            .filter(o -> o instanceof TextConsole)
            .map(o -> (TextConsole)o)
            .toList();
    }
}