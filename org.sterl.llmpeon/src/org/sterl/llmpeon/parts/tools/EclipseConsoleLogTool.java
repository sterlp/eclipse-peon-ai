package org.sterl.llmpeon.parts.tools;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.TextConsole;
import org.sterl.llmpeon.shared.LogExcerpt;
import org.sterl.llmpeon.shared.SearchQuery;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseConsoleLogTool extends AbstractEclipseTool {

    @Tool("Read Eclipse console output. consoleName targets a console; grep filters lines (regex, literal fallback); lines tails the filtered result.")
    public String eclipseReadConsoleLog(
            @P(description = "Name of the console to read. If empty, reads the active console.", required = false, name = "consoleName")
            String consoleName,
            @P(description = "Line count from the end of the log (like tail -n).", required = false, name = "lines")
            Integer lines,
            @P(description = "optional line filter — regex, falls back to literal", required = false, name = "grep")
            String grep) {

        var consoles = consoles();
        if (lines == null || lines <= 0) lines = 50;

        if (consoles.isEmpty()) {
            return "No message consoles available.";
        }

        var targetConsole = getConsole(consoleName, consoles);
        if (targetConsole.isEmpty()) {
            onTool("Reading console " + StringUtil.stripToEmpty(consoleName));
            return "Console not found. Available consoles:\n" + eclipseListAvailableConsoles();
        }

        var console = targetConsole.get();
        SearchQuery query = StringUtil.hasValue(grep) ? SearchQuery.of(grep) : null;
        LogExcerpt excerpt = LogExcerpt.of(console.getDocument().get(), lines, query);
        String message = "Reading console " + console.getName();
        if (query != null) message += " · grep '" + grep + "'";
        onTool(message + " · " + excerpt.shown() + " of " + excerpt.matching() + " lines");
        return excerpt.header(console.getName()) + "\n" + excerpt.text();
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
    
    @Tool("List open Eclipse consoles by name. Use to find names for reading console output.")
    public String eclipseListAvailableConsoles() {
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
