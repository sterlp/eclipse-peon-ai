package org.sterl.llmpeon.parts.tools;

import java.util.List;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class SearchFilesTool {

    private final ToolContext context;

    public SearchFilesTool(ToolContext context) {
        this.context = context;
    }

    @Tool("Searches for files in the Eclipse workspace whose name contains the given query string. "
            + "Returns a list of matching files with name and meta data.")
    public String searchFiles(
            @P("Part of the file name to search for, e.g. 'Controller' or '.xml'") String query) {

        if (query == null || query.isBlank()) {
            return "Error: query must not be empty";
        }

        List<String> matches = context.searchFiles(query);

        System.err.println("Searched file " + query + " found " + matches.size() + " files ...");

        if (matches.isEmpty()) {
            return "No files found matching '" + query + "'";
        }

        var result = "Found " + matches.size() + " file(s):\n" + String.join("\n", matches);
        System.err.println(result);
        return result;
    }
}
